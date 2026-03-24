package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.ChatResponse;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummary;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.PrivateMessage;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserService userService;

    public MessageService(MessageRepository messageRepository, 
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserService userService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
    }

    @Transactional
    public Message saveMessage(PrivateMessage dto) {
        UserAccount sender = (UserAccount) userService.loadUserByUsername(dto.senderUsername());
        UserAccount receiver = (UserAccount) userService.loadUserByUsername(dto.receiverUsername());

        Conversation conversation = conversationRepository
                .findPrivateConversation(sender.getUsername(), receiver.getUsername())
                .orElseGet(() -> createPrivateConversation(sender, receiver));

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(dto.content());
        message.setSentAt(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(savedMessage.getSentAt());
        conversationRepository.save(conversation);

        ConversationParticipant receiverParticipant = participantRepository
                .findByConversationIdAndUserUsername(conversation.getId(), receiver.getUsername())
                .orElseThrow();
        receiverParticipant.setUnreadCount(receiverParticipant.getUnreadCount() + 1);
        participantRepository.save(receiverParticipant);

        return savedMessage;
    }

    @Transactional
    public ChatResponse getOrCreatePrivateConversation(String currentUsername, String recipientUsername) {
        return conversationRepository.findPrivateConversation(currentUsername, recipientUsername)
            .map(conv -> new ChatResponse(conv.getId(), recipientUsername))
            .orElseGet(() -> {
                UserAccount me = (UserAccount) userService.loadUserByUsername(currentUsername);
                UserAccount other = (UserAccount) userService.loadUserByUsername(recipientUsername);

                Conversation newConv = createPrivateConversation(me, other);
                return new ChatResponse(newConv.getId(), recipientUsername);
            });
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

    private Conversation createPrivateConversation(UserAccount user1, UserAccount user2) {
        Conversation conv = new Conversation();
        conv.setGroup(false);
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

    public List<ChatSummary> getUserConversations(String username) {
        return conversationRepository.findActiveChatsForUser(username);
    }
}