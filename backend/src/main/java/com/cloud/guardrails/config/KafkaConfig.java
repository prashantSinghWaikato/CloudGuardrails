package com.cloud.guardrails.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic eventTopic() {
        return new NewTopic("events-topic", 1, (short) 1);
    }
}
