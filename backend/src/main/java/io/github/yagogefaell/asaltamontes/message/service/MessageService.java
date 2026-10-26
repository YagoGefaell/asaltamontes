package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.ChatPageDto;
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
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final MediaReferenceService mediaReferences;
    private final int maxGroupSize;

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_HISTORY_PAGE = 100;
    public static final int MAX_CHAT_PAGE = 100;
    private static final int PREVIEW_LENGTH = 30;

    public MessageService(MessageRepository messageRepository, 
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserService userService,
                          ApplicationEventPublisher eventPublisher,
                          MediaReferenceService mediaReferences,
                          @Value("${asaltamontes.chat.max-group-size:100}") int maxGroupSize) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.mediaReferences = mediaReferences;
        this.maxGroupSize = maxGroupSize;
    }

    /**
     * Saves a message from a participant and broadcasts it after commit (ChatMessageBroadcaster).
     * Counters and the conversation preview are updated with single statements, so concurrent
     * messages never lose an update.
     */
    @Transactional
    public MessageDto processIncomingMessage(UUID conversationPublicId, Long senderId, String content) {
        String text = validateContent(content);

        // Unknown and foreign chats look the same (404), so nobody can probe which chats exist
        Long conversationId = conversationPublicId == null ? null
                : conversationRepository.findIdByPublicId(conversationPublicId).orElse(null);
        if (conversationId == null || !participantRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new NotFoundException("Chat no encontrado");
        }

        UserAccount sender = userService.loadUserById(senderId);

        Message message = new Message();
        message.setConversation(conversationRepository.getReferenceById(conversationId));
        message.setSender(sender);
        message.setContent(text);
        // Microsecond precision, as stored: the message cursor carries sent_at and PostgreSQL rounds nanoseconds
        message.setSentAt(OpaqueCursor.truncate(LocalDateTime.now()));
        Message savedMessage = messageRepository.save(message);

        String preview = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH).trim() + "..." : text;
        conversationRepository.updateLastMessage(conversationId, preview, savedMessage.getSentAt(), sender);
        participantRepository.incrementUnreadExceptSender(conversationId, senderId);

        MessageDto dto = new MessageDto(
                savedMessage.getPublicId(),
                conversationPublicId,
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
            .map(conv -> new ChatResponseDto(conv.getPublicId()));
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
        return new ChatResponseDto(newConv.getPublicId());
    }

    @Transactional
    public ChatResponseDto createGroupConversation(String currentUsername, String groupName, String groupImage,
                                                   UUID groupMediaId, List<String> users) {
        UserAccount creator = (UserAccount) userService.loadUserByUsername(currentUsername);
        // The creator administers the new group, so she may set its image; the image must be hers
        MediaReferenceService.Linked image = mediaReferences.link(groupMediaId, groupImage, MediaPurpose.GROUP_IMAGE,
                creator.getId(), null);

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

        Conversation newConv = createGroupConversation(creator, members, groupName, image);
        return new ChatResponseDto(newConv.getPublicId());
    }

    /**
     * A page of the history (T10): the {@code limit} messages before {@code before} (or the latest ones),
     * oldest first. Reading no longer marks the chat as read: that is chat.read or POST /read.
     */
    @Transactional(readOnly = true)
    public MessagePageDto getConversationHistory(UUID conversationPublicId, Long userId, String beforeToken, int limit) {
        Long conversationId = participantConversationId(conversationPublicId, userId);
        Long before = messageCursor(beforeToken, conversationId);

        int pageSize = Math.clamp(limit, 1, MAX_HISTORY_PAGE);
        // One extra row tells whether there is a previous page
        Limit fetch = Limit.of(pageSize + 1);
        List<MessageDto> newestFirst = before == null
                ? messageRepository.findLatest(conversationId, fetch)
                : messageRepository.findBefore(conversationId, before, fetch);

        boolean hasMore = newestFirst.size() > pageSize;
        List<MessageDto> page = new ArrayList<>(newestFirst.subList(0, Math.min(pageSize, newestFirst.size())));
        Collections.reverse(page);
        String nextBefore = hasMore ? page.get(0).cursor() : null;

        return new MessagePageDto(page, hasMore, nextBefore);
    }

    /**
     * The {@code limit} messages after {@code after}, oldest first (T11: what arrived while the WebSocket was down).
     * {@code hasMore} means there are newer ones: ask again with after = cursor of the last item.
     */
    @Transactional(readOnly = true)
    public MessagePageDto getMessagesAfter(UUID conversationPublicId, Long userId, String afterToken, int limit) {
        Long conversationId = participantConversationId(conversationPublicId, userId);
        Long after = messageCursor(afterToken, conversationId);
        if (after == null) {
            throw new BadRequestException("Cursor no válido");
        }

        int pageSize = Math.clamp(limit, 1, MAX_HISTORY_PAGE);
        List<MessageDto> oldestFirst = messageRepository.findAfter(conversationId, after, Limit.of(pageSize + 1));

        boolean hasMore = oldestFirst.size() > pageSize;
        List<MessageDto> page = List.copyOf(oldestFirst.subList(0, Math.min(pageSize, oldestFirst.size())));
        return new MessagePageDto(page, hasMore, null);
    }

    @Transactional
    public void markConversationAsRead(UUID conversationPublicId, Long userId) {
        Long conversationId = conversationPublicId == null ? null
                : conversationRepository.findIdByPublicId(conversationPublicId).orElse(null);
        if (conversationId == null || participantRepository.resetUnread(conversationId, userId) == 0) {
            throw new NotFoundException("Chat no encontrado");
        }
    }

    /** Internal id of a chat the user takes part in; unknown and foreign chats are the same 404 (no probing). */
    private Long participantConversationId(UUID conversationPublicId, Long userId) {
        Long conversationId = conversationRepository.findIdByPublicId(conversationPublicId).orElse(null);
        if (conversationId == null || !participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new NotFoundException("Chat no encontrado");
        }
        return conversationId;
    }

    /**
     * Internal id of the message a cursor points to (null when there is no cursor). The token must be well formed
     * and name a message of this very chat with that exact sent_at, so it cannot point into other chats: 400.
     */
    private Long messageCursor(String token, Long conversationId) {
        OpaqueCursor cursor = OpaqueCursor.decode(token, OpaqueCursor.MESSAGE);
        if (cursor == null) {
            return null;
        }
        if (cursor.order() == null) {
            throw new BadRequestException("Cursor no válido");
        }
        return messageRepository.findIdByPublicIdAndSentAt(cursor.publicId(), conversationId, cursor.order())
                .orElseThrow(() -> new BadRequestException("Cursor no válido"));
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

    private Conversation createGroupConversation(UserAccount creator, List<UserAccount> members, String groupName,
                                                 MediaReferenceService.Linked image) {
        Conversation conv = new Conversation();
        conv.setType(ChatType.GROUP);
        conv.setGroupName((groupName == null || groupName.isBlank()) ? "Nuevo grupo" : groupName.trim());
        conv.setGroupImage(image.url());
        conv.setGroupMediaId(image.mediaId());
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

    /** A page of the chat list (T10b): the {@code limit} chats after the cursor, most recent first. */
    @Transactional(readOnly = true)
    public ChatPageDto getUserConversations(String username, String beforeToken, int limit) {
        OpaqueCursor cursor = OpaqueCursor.decode(beforeToken, OpaqueCursor.CHAT);
        // The cursor chat has to be one of hers. Its order value is only a keyset position in her own list
        // (it may differ from the chat's current last_message_at if a message arrived meanwhile), so it is not
        // checked against the row: the queries below only ever return her chats.
        Long beforeId = cursor == null ? null
                : conversationRepository.findIdByPublicIdAndParticipant(cursor.publicId(), username)
                        .orElseThrow(() -> new BadRequestException("Cursor no válido"));
        LocalDateTime beforeAt = cursor == null ? null : cursor.order();

        int pageSize = Math.clamp(limit, 1, MAX_CHAT_PAGE);
        // One extra row tells whether there is another page
        Limit fetch = Limit.of(pageSize + 1);
        List<ChatSummaryDto> rows;
        if (beforeId == null) {
            rows = conversationRepository.findChatsForUser(username, fetch);
        } else if (beforeAt != null) {
            rows = conversationRepository.findChatsForUserBefore(username, beforeAt, beforeId, fetch);
        } else {
            rows = conversationRepository.findChatsWithoutMessagesForUserBefore(username, beforeId, fetch);
        }

        boolean hasMore = rows.size() > pageSize;
        List<ChatSummaryDto> page = List.copyOf(rows.subList(0, Math.min(pageSize, rows.size())));
        ChatSummaryDto last = hasMore ? page.get(page.size() - 1) : null;

        return new ChatPageDto(
            page,
            hasMore,
            last != null ? OpaqueCursor.encode(OpaqueCursor.CHAT, last.lastMessageAt(), last.conversationId()) : null,
            participantRepository.countUnreadChats(username)
        );
    }
}