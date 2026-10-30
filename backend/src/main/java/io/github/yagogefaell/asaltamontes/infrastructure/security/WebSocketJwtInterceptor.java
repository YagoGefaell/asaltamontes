package io.github.yagogefaell.asaltamontes.infrastructure.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import io.jsonwebtoken.Claims;

@Component
public class WebSocketJwtInterceptor implements ChannelInterceptor {
    
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public WebSocketJwtInterceptor(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {

            String header = accessor.getFirstNativeHeader("Authorization");

            // No anonymous sessions: the client gets an ERROR frame and the connection is closed
            if (header == null || !header.startsWith("Bearer ")) {
                throw new InvalidTokenException("Token no proporcionado");
            }

            String jwt = header.substring(7);

            try {
                Claims claims = jwtUtil.extractAllClaims(jwt);

                if (jwtUtil.isExpired(claims) || !jwtUtil.isType(claims, JwtUtil.ACCESS_TYPE)) {
                    throw new InvalidTokenException("Token inválido");
                }

                Long id = jwtUtil.extractId(claims);
                var userDetails = userService.loadUserById(id);
                if (userDetails.isSuspended()) {
                    throw new InvalidTokenException("Token inválido");
                }

                // The principal's name is the user id: messages are routed by id (/user/{id}/queue/...),
                // so they keep arriving after a username change
                var authToken = new UsernamePasswordAuthenticationToken(
                        String.valueOf(userDetails.getId()),
                        null,
                        userDetails.getAuthorities()
                );

                accessor.setUser(authToken);
            } catch (Exception e) {
                throw new InvalidTokenException("Token inválido");
            }
        } else if (accessor != null && (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand()))) {

            if (accessor.getUser() == null) {
                throw new InvalidTokenException("No autenticado");
            }
            // An open connection does not outlive her suspension (T23c2): the principal's name is her id
            try {
                if (userService.loadUserById(Long.parseLong(accessor.getUser().getName())).isSuspended()) {
                    throw new InvalidTokenException("Token inválido");
                }
            } catch (NumberFormatException e) {
                throw new InvalidTokenException("No autenticado");
            }

            // Only the user's own queues can be subscribed to (/user/queue/messages, /user/queue/errors)
            String destination = accessor.getDestination();
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                    && (destination == null || !destination.startsWith("/user/queue/"))) {
                throw new ForbiddenException("Suscripción no permitida");
            }
        }
        return message;
    }
}
