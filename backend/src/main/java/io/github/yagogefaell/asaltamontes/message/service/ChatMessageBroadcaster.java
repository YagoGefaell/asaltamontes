package io.github.yagogefaell.asaltamontes.message.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.yagogefaell.asaltamontes.message.dto.ConversationRemovedDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDeletedDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;

/**
 * Sends saved messages to every participant over WebSocket, only after the transaction that saved them
 * commits: a rolled-back message is never delivered.
 */
@Component
public class ChatMessageBroadcaster {

    private final ConversationParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessageBroadcaster(ConversationParticipantRepository participantRepository,
                                  SimpMessagingTemplate messagingTemplate) {
        this.participantRepository = participantRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        // WebSocket sessions are identified by user id (WebSocketJwtInterceptor), not by username
        for (Long userId : participantRepository.findUserIdsByConversationPublicId(event.message().conversationId())) {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", event.message());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(MessageDeletedEvent event) {
        MessageDeletedDto dto = new MessageDeletedDto(event.conversationId(), event.messageId());
        for (Long userId : participantRepository.findUserIdsByConversationPublicId(event.conversationId())) {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/message-deletions", dto);
        }
    }

    /** The chat disappears from the list of the expelled (or the one who left, in her other sessions). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversationRemoved(ConversationRemovedEvent event) {
        messagingTemplate.convertAndSendToUser(String.valueOf(event.userId()), "/queue/conversations",
                new ConversationRemovedDto(event.conversationId(), event.reason()));
    }
}
