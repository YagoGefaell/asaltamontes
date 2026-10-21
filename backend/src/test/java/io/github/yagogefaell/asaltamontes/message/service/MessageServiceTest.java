package io.github.yagogefaell.asaltamontes.message.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private UserService userService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageService messageService;

    @Test
    void processIncomingMessage_rejectsSenderWhoIsNotParticipant() {
        when(participantRepository.findByConversationIdAndUserUsername(42L, "intruder"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.processIncomingMessage(42L, "intruder", "hola"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any(Message.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void processIncomingMessage_savesAndBroadcastsWhenSenderIsParticipant() {
        Conversation conversation = new Conversation();
        conversation.setId(42L);
        UserAccount sender = user(1L, "ana");
        UserAccount recipient = user(2L, "bea");
        ConversationParticipant senderParticipant = participant(conversation, sender);
        ConversationParticipant recipientParticipant = participant(conversation, recipient);

        when(participantRepository.findByConversationIdAndUserUsername(42L, "ana"))
                .thenReturn(Optional.of(senderParticipant));
        when(userService.loadUserByUsername("ana")).thenReturn(sender);
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.findByConversationId(42L))
                .thenReturn(List.of(senderParticipant, recipientParticipant));

        messageService.processIncomingMessage(42L, "ana", "hola");

        verify(messageRepository).save(any(Message.class));
        verify(messagingTemplate).convertAndSendToUser(eq("bea"), eq("/queue/messages"), any(MessageDto.class));
    }

    private static UserAccount user(Long id, String username) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static ConversationParticipant participant(Conversation conversation, UserAccount user) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUser(user);
        return participant;
    }
}
