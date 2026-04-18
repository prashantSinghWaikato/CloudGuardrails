package com.cloud.guardrails.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class WebSocketUserInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String email = accessor.getSessionAttributes() != null
                    ? (String) accessor.getSessionAttributes().get(WebSocketAuthHandshakeInterceptor.SESSION_EMAIL_ATTRIBUTE)
                    : null;

            if (email != null && !email.isBlank()) {
                Principal principal = () -> email;
                accessor.setUser(principal);
            }
        }

        return message;
    }
}
