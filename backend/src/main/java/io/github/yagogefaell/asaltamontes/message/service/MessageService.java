package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.ChatResponseDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessagePageDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxGroupSize;

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_HISTORY_PAGE = 100;
    private static final int PREVIEW_LENGTH = 30;

    public MessageService(MessageRepository messageRepository, 
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserService userService,
                          ApplicationEventPublisher eventPublisher,
                          @Value("${asaltamontes.chat.max-group-size:100}") int maxGroupSize) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.maxGroupSize = maxGroupSize;
    }

    /**
     * Saves a message from a participant and broadcasts it after commit (ChatMessageBroadcaster).
     * Counters and the conversation preview are updated with single statements, so concurrent
     * messages never lose an update.
     */
    @Transactional
    public MessageDto processIncomingMessage(Long conversationId, Long senderId, String content) {
        String text = validateContent(content);

        if (conversationId == null || !participantRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new ForbiddenException("No tienes permiso para escribir en este chat");
        }

        UserAccount sender = userService.loadUserById(senderId);

        Message message = new Message();
        message.setConversation(conversationRepository.getReferenceById(conversationId));
        message.setSender(sender);
        message.setContent(text);
        message.setSentAt(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);

        String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH).trim() + "..." : text;
        conversationRepository.updateLastMessage(conversationId, preview, savedMessage.getSentAt(), sender);
        participantRepository.incrementUnreadExceptSender(conversationId, senderId);

        MessageDto dto = new MessageDto(
                savedMessage.getId(),
                conversationId,
                sender.getUsername(),
                savedMessage.getContent(),
                savedMessage.getSentAt()
        );

        eventPublisher.publishEvent(new MessageSentEvent(dto));
        return dto;
    }

    private static String validateContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BadRequestException("El mensaje no puede estar vacío");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("El mensaje no puede superar los " + MAX_CONTENT_LENGTH + " caracteres");
        }
        return text;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ChatResponseDto> getPrivateConversation(String currentUsername, String recipientUsername) {
        return conversationRepository.findPrivateConversation(currentUsername, recipientUsername)
            .map(conv -> new ChatResponseDto(conv.getId()));
    }

    @Transactional
    public ChatResponseDto createOrGetPrivateConversation(String currentUsername, String recipientUsername) {
        if (currentUsername.equalsIgnoreCase(recipientUsername)) {
            throw new BadRequestException("No puedes abrir un chat contigo misma");
        }

        Optional<ChatResponseDto> existingChat = getPrivateConversation(currentUsername, recipientUsername);

        if (existingChat.isPresent()) {
            return existingChat.get();
        }

        UserAccount me = (UserAccount) userService.loadUserByUsername(currentUsername);
        UserAccount other = (UserAccount) userService.loadUserByUsername(recipientUsername);

        Conversation newConv = createPrivateConversation(me, other);
        return new ChatResponseDto(newConv.getId());
    }

    @Transactional
    public ChatResponseDto createGroupConversation(String currentUsername, String groupName, String groupImage, List<String> users) {
        UserAccount creator = (UserAccount) userService.loadUserByUsername(currentUsername);

        if (users == null || users.isEmpty()) {
            throw new BadRequestException("Debes añadir al menos un usuario al grupo");
        }

        Set<String> normalizedUsernames = new LinkedHashSet<>();
        for (String username : users) {
            if (username == null || username.isBlank()) {
                continue;
            }
            if (!username.equalsIgnoreCase(currentUsername)) {
                normalizedUsernames.add(username.trim());
            }
        }

        if (normalizedUsernames.isEmpty()) {
            throw new BadRequestException("Debes añadir usuarios válidos distintos de tu cuenta");
        }

        // The creator is also a participant
        if (normalizedUsernames.size() + 1 > maxGroupSize) {
            throw new BadRequestException("Un grupo puede tener como máximo " + maxGroupSize + " participantes");
        }

        List<UserAccount> members = normalizedUsernames.stream()
                .map(username -> (UserAccount) userService.loadUserByUsername(username))
                .toList();

        Conversation newConv = createGroupConversation(creator, members, groupName, groupImage);
        return new ChatResponseDto(newConv.getId());
    }

    /**
     * A page of the history (T10): the {@code limit} messages before {@code before} (or the latest ones),
     * oldest first. Reading no longer marks the chat as read: that is chat.read or POST /read.
     */
    @Transactional(readOnly = true)
    public MessagePageDto getConversationHistory(Long conversationId, Long userId, Long before, int limit) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("No tienes permiso para acceder a este chat");
        }

        int pageSize = Math.clamp(limit, 1, MAX_HISTORY_PAGE);
        // One extra row tells whether there is a previous page
        Limit fetch = Limit.of(pageSize + 1);
        List<MessageDto> newestFirst = before == null
                ? messageRepository.findLatest(conversationId, fetch)
                : messageRepository.findBefore(conversationId, before, fetch);

        boolean hasMore = newestFirst.size() > pageSize;
        List<MessageDto> page = new ArrayList<>(newestFirst.subList(0, Math.min(pageSize, newestFirst.size())));
        Collections.reverse(page);
        Long nextBefore = hasMore ? page.get(0).id() : null;

        return new MessagePageDto(page, hasMore, nextBefore);
    }

    @Transactional
    public void markConversationAsRead(Long conversationId, Long userId) {
        if (participantRepository.resetUnread(conversationId, userId) == 0) {
            throw new ForbiddenException("No tienes permiso para acceder a este chat");
        }
    }

    private Conversation createPrivateConversation(UserAccount user1, UserAccount user2) {
        Conversation conv = new Conversation();
        conv.setType(ChatType.INDIVIDUAL);
        conv.setCreatedAt(LocalDateTime.now());
        Conversation savedConv = conversationRepository.save(conv);

        ConversationParticipant p1 = new ConversationParticipant();
        p1.setConversation(savedConv);
        p1.setUser(user1);

        ConversationParticipant p2 = new ConversationParticipant();
        p2.setConversation(savedConv);
        p2.setUser(user2);

        participantRepository.saveAll(List.of(p1, p2));

        return savedConv;
    }

    private Conversation createGroupConversation(UserAccount creator, List<UserAccount> members, String groupName, String groupImage) {
        Conversation conv = new Conversation();
        conv.setType(ChatType.GROUP);
        conv.setGroupName((groupName == null || groupName.isBlank()) ? "Nuevo grupo" : groupName.trim());
        conv.setGroupImage((groupImage == null || groupImage.isBlank()) ? null : groupImage.trim());
        conv.setCreatedAt(LocalDateTime.now());
        Conversation savedConv = conversationRepository.save(conv);

        Set<Long> addedUsers = new LinkedHashSet<>();
        addedUsers.add(creator.getId());

        ConversationParticipant creatorParticipant = new ConversationParticipant();
        creatorParticipant.setConversation(savedConv);
        creatorParticipant.setUser(creator);

        List<ConversationParticipant> participants = new java.util.ArrayList<>();
        participants.add(creatorParticipant);

        for (UserAccount member : members) {
            if (member == null || addedUsers.contains(member.getId())) {
                continue;
            }

            ConversationParticipant participant = new ConversationParticipant();
            participant.setConversation(savedConv);
            participant.setUser(member);
            participants.add(participant);
            addedUsers.add(member.getId());
        }

        participantRepository.saveAll(participants);
        return savedConv;
    }

    public List<ChatSummaryDto> getUserConversations(String username) {
        return conversationRepository.findActiveChatsForUser(username);
    }
}