package com.cloud.guardrails.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;
    private final WebSocketUserInterceptor webSocketUserInterceptor;
    @Value("${app.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginPatterns;

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
                .setAllowedOriginPatterns(parseAllowedOrigins())
                .addInterceptors(webSocketAuthHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketUserInterceptor);
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }
}
