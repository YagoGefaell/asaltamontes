package io.github.yagogefaell.asaltamontes.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
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
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService mediaReferences;

    private static final UUID CHAT = UUID.fromString("0192f3a4-7c00-7000-8000-000000000001");

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, conversationRepository, participantRepository,
                userService, eventPublisher, mediaReferences, 100);
    }

    @Test
    void processIncomingMessage_rejectsSenderWhoIsNotParticipant() {
        when(conversationRepository.findIdByPublicId(CHAT)).thenReturn(Optional.of(42L));
        when(participantRepository.existsByConversationIdAndUserId(42L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.processIncomingMessage(CHAT, 7L, "hola"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any(Message.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void processIncomingMessage_rejectsBlankOrTooLongContentBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> messageService.processIncomingMessage(CHAT, 1L, "   "))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> messageService.processIncomingMessage(CHAT, 1L, "x".repeat(2001)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(participantRepository, messageRepository, eventPublisher);
    }

    @Test
    void processIncomingMessage_rejectsAnUnknownChatLikeAForeignOne() {
        when(conversationRepository.findIdByPublicId(CHAT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.processIncomingMessage(CHAT, 7L, "hola"))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void processIncomingMessage_savesTrimmedContentUpdatesCountersAndPublishesTheEvent() {
        UserAccount ana = new UserAccount();
        ana.setId(1L);
        ana.setUsername("ana");
        when(conversationRepository.findIdByPublicId(CHAT)).thenReturn(Optional.of(42L));
        when(participantRepository.existsByConversationIdAndUserId(42L, 1L)).thenReturn(true);
        when(userService.loadUserById(1L)).thenReturn(ana);
        when(conversationRepository.getReferenceById(42L)).thenReturn(new Conversation());
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        messageService.processIncomingMessage(CHAT, 1L, "  hola  ");

        verify(participantRepository).incrementUnreadExceptSender(42L, 1L);
        verify(conversationRepository).updateLastMessage(any(), any(), any(), any());
        ArgumentCaptor<MessageSentEvent> event = ArgumentCaptor.forClass(MessageSentEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().message().content()).isEqualTo("hola");
        assertThat(event.getValue().message().senderUsername()).isEqualTo("ana");
    }
}
