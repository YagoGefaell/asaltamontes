package io.github.yagogefaell.asaltamontes.infrastructure.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

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

            if (header != null && header.startsWith("Bearer ")) {
                String jwt = header.substring(7);

                try {
                    if (jwtUtil.isTokenValid(jwt)) {
                        Claims claims = jwtUtil.extractAllClaims(jwt);

                        if (!jwtUtil.isExpired(claims)) {
                            Long id = jwtUtil.extractId(claims);
                            var userDetails = userService.loadUserById(id);

                            var authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                            accessor.setUser(authToken);
                        }
                    }
                } catch (Exception e) {
                    throw new InvalidTokenException(jwt);
                }
            }
        }
        return message;
    }
}
