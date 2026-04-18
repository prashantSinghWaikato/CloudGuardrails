package com.cloud.guardrails.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic}")
    private String eventTopicName;

    @Bean
    public NewTopic eventTopic() {
        return new NewTopic(eventTopicName, 1, (short) 1);
    }
}
