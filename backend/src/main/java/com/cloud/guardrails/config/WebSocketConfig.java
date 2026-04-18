package com.cloud.guardrails.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;
    private final WebSocketUserInterceptor webSocketUserInterceptor;

    public WebSocketConfig(WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor,
                           WebSocketUserInterceptor webSocketUserInterceptor) {
        this.webSocketAuthHandshakeInterceptor = webSocketAuthHandshakeInterceptor;
        this.webSocketUserInterceptor = webSocketUserInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue"); // subscribe
        config.setApplicationDestinationPrefixes("/app"); // send
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(webSocketAuthHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketUserInterceptor);
    }
}
