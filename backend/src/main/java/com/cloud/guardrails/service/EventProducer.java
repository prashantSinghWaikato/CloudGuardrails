package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.EventDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    @Value("${app.kafka.topic}")
    private String eventTopicName;

    public void sendEvent(EventDTO event) throws JsonProcessingException {
        kafkaTemplate.send(eventTopicName, objectMapper.writeValueAsString(event));
    }
}
