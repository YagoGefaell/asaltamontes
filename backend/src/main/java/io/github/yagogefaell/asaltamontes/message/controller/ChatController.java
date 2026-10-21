package io.github.yagogefaell.asaltamontes.message.controller;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.message.dto.ChatErrorDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatReadEventDto;
import io.github.yagogefaell.asaltamontes.message.dto.SendMessageCommandDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ChatController {

    private final MessageService messageService;

    public ChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send")
    public void processMessage(@Payload SendMessageCommandDto command, Principal principal) {
        messageService.processIncomingMessage(
                command.conversationId(),
                userId(principal),
                command.content()
        );
    }

    @MessageMapping("/chat.read")
    public void processReadReceipt(@Payload ChatReadEventDto message, Principal principal) {
        messageService.markConversationAsRead(message.conversationId(), userId(principal));
    }

    /** The WebSocket principal's name is the user id (see WebSocketJwtInterceptor). */
    private static Long userId(Principal principal) {
        return Long.valueOf(principal.getName());
    }

    /** Errors in the handlers above go back to the sender's session instead of being lost. */
    @MessageExceptionHandler({ BadRequestException.class, ForbiddenException.class, NotFoundException.class, ConflictException.class })
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorDto handleDomainError(RuntimeException ex) {
        return new ChatErrorDto(ex.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorDto handleUnexpectedError(Exception ex) {
        log.error("Unexpected error handling a chat message", ex);
        return new ChatErrorDto("No se ha podido procesar el mensaje");
    }
}
