package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.ChatResponse;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummary;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(MessageRepository messageRepository, 
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserService userService,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public MessageDto processIncomingMessage(Long conversationId, String senderUsername, String content) {
        Message savedMessage = saveMessage(conversationId, senderUsername, content);

        MessageDto dto = new MessageDto(
                savedMessage.getId(),
                savedMessage.getConversation().getId(),
                savedMessage.getSender().getUsername(),
                savedMessage.getContent(),
                savedMessage.getSentAt()
        );

        sendMessageToConversation(dto);
        return dto;
    }

    @Transactional
    public Message saveMessage(Long conversationId, String senderUsername, String content) {
        UserAccount sender = (UserAccount) userService.loadUserByUsername(senderUsername);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("La conversación no existe"));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setSentAt(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(savedMessage.getSentAt());
        conversation.setLastMessage(savedMessage.getContent().length() > 30 ? savedMessage.getContent().substring(0, 30).trim() + "..." : savedMessage.getContent());
        conversation.setLastSender(savedMessage.getSender());
        conversationRepository.save(conversation);

        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        
        for (ConversationParticipant participant : participants) {
            if (!participant.getUser().getUsername().equals(senderUsername)) {
                participant.setUnreadCount(participant.getUnreadCount() + 1);
                participantRepository.save(participant);
            }
        }

        return savedMessage;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ChatResponse> getPrivateConversation(String currentUsername, String recipientUsername) {
        return conversationRepository.findPrivateConversation(currentUsername, recipientUsername)
            .map(conv -> new ChatResponse(conv.getId()));
    }

    @Transactional
    public ChatResponse createOrGetPrivateConversation(String currentUsername, String recipientUsername) {
        Optional<ChatResponse> existingChat = getPrivateConversation(currentUsername, recipientUsername);

        if (existingChat.isPresent()) {
            return existingChat.get();
        }

        UserAccount me = (UserAccount) userService.loadUserByUsername(currentUsername);
        UserAccount other = (UserAccount) userService.loadUserByUsername(recipientUsername);

        Conversation newConv = createPrivateConversation(me, other);
        return new ChatResponse(newConv.getId());
    }

    @Transactional
    public ChatResponse createGroupConversation(String currentUsername, String groupName, String groupImage, List<String> users) {
        UserAccount creator = (UserAccount) userService.loadUserByUsername(currentUsername);

        if (users == null || users.isEmpty()) {
            throw new RuntimeException("Debes añadir al menos un usuario al grupo");
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
            throw new RuntimeException("Debes añadir usuarios válidos distintos de tu cuenta");
        }

        List<UserAccount> members = normalizedUsernames.stream()
                .map(username -> (UserAccount) userService.loadUserByUsername(username))
                .toList();

        Conversation newConv = createGroupConversation(creator, members, groupName, groupImage);
        return new ChatResponse(newConv.getId());
    }

    @Transactional
    public List<MessageDto> getConversationHistory(Long conversationId, String username) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("No tienes permiso para acceder a este chat"));

        if (participant.getUnreadCount() > 0) {
            participant.setUnreadCount(0);
            participantRepository.save(participant);
        }

        return messageRepository.findMessagesByConversationId(conversationId);
    }

    @Transactional
    public void markConversationAsRead(Long conversationId, String username) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserUsername(conversationId, username)
                .orElseThrow(() -> new RuntimeException("No tienes permiso para acceder a este chat"));

        if (participant.getUnreadCount() > 0) {
            participant.setUnreadCount(0);
            participantRepository.save(participant);
        }
    }

    private void sendMessageToConversation(MessageDto message) {
        List<ConversationParticipant> participants = participantRepository.findByConversationId(message.conversationId());

        for (ConversationParticipant participant : participants) {
            messagingTemplate.convertAndSendToUser(
                    participant.getUser().getUsername(),
                    "/queue/messages",
                    message
            );
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

    public List<ChatSummary> getUserConversations(String username) {
        return conversationRepository.findActiveChatsForUser(username);
    }
}