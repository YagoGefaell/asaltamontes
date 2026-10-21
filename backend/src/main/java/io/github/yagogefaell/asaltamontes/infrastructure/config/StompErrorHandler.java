package io.github.yagogefaell.asaltamontes.infrastructure.config;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;

/**
 * STOMP ERROR frames with the real reason ("Token inválido", "Suscripción no permitida") instead of
 * Spring's generic "Failed to send message to ExecutorSubscribableChannel", so the client can react
 * (e.g. refresh the token and reconnect). Other errors keep the default behaviour.
 */
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    @Nullable
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, @NonNull Throwable ex) {
        Throwable cause = ex;
        while (cause != null && !(cause instanceof InvalidTokenException) && !(cause instanceof ForbiddenException)) {
            cause = cause.getCause();
        }
        if (cause == null) {
            return super.handleClientMessageProcessingError(clientMessage, ex);
        }

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(cause.getMessage());
        accessor.setLeaveMutable(true);
        // The reason goes in the "message" header, like Spring's own ERROR frames; no body
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
