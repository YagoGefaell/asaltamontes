package io.github.yagogefaell.asaltamontes.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import io.github.yagogefaell.asaltamontes.infrastructure.security.WebSocketJwtInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtInterceptor jwtInterceptor;
    private final CorsProperties corsProperties;
    private final StompErrorHandler stompErrorHandler;

    public WebSocketConfig(WebSocketJwtInterceptor jwtInterceptor, CorsProperties corsProperties, StompErrorHandler stompErrorHandler) {
        this.jwtInterceptor = jwtInterceptor;
        this.corsProperties = corsProperties;
        this.stompErrorHandler = stompErrorHandler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-asaltamontes")
                .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(stompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtInterceptor);
    }

}
