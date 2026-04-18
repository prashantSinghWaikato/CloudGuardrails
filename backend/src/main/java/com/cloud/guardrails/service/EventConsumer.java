package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.EventDTO;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class EventConsumer {

    private final EventRepository eventRepository;
    private final ViolationService violationService;
    private final ObjectMapper objectMapper;
    private final OrganizationService organizationService;
    private final CloudAccountService cloudAccountService;
    @Value("${app.kafka.topic}")
    private String eventTopicName;

    public EventConsumer(EventRepository eventRepository,
                         ViolationService violationService,
                         ObjectMapper objectMapper,
                         OrganizationService organizationService,
                         CloudAccountService cloudAccountService) {
        this.eventRepository = eventRepository;
        this.violationService = violationService;
        this.objectMapper = objectMapper;
        this.organizationService = organizationService;
        this.cloudAccountService = cloudAccountService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${KAFKA_GROUP_ID:guardrails-group}"
    )
    public void consume(String message) {

        try {
            EventDTO dto = objectMapper.readValue(message, EventDTO.class);

            log.info("Received event: {}", dto);

            // ✅ Validate organization
            Organization organization = organizationService.getById(dto.getOrganizationId());
            if (organization == null) {
                log.error("Invalid organization ID: {}", dto.getOrganizationId());
                return;
            }

            if (dto.getCloudAccountId() == null) {
                log.error("Missing cloud account ID for event: org={}, resource={}",
                        dto.getOrganizationId(), dto.getResourceId());
                return;
            }

            // ✅ Validate cloud account within the same organization without request-scoped auth state
            CloudAccount cloudAccount = cloudAccountService
                    .getByIdForOrganization(dto.getCloudAccountId(), organization.getId());

            if (dto.getExternalEventId() != null
                    && !dto.getExternalEventId().isBlank()
                    && eventRepository.existsByCloudAccount_IdAndExternalEventId(
                            cloudAccount.getId(),
                            dto.getExternalEventId())
            ) {
                log.debug("Skipping duplicate event {} for account {}",
                        dto.getExternalEventId(), cloudAccount.getId());
                return;
            }

            // ✅ Build event
            Event event = Event.builder()
                    .eventType(dto.getEventType())
                    .resourceId(dto.getResourceId())
                    .externalEventId(dto.getExternalEventId())
                    .payload(dto.getPayload())
                    .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now())
                    .organization(organization)
                    .cloudAccount(cloudAccount)
                    .build();

            Event savedEvent = eventRepository.save(event);

            // ✅ Trigger rule evaluation
            violationService.evaluate(savedEvent);

            log.info("Event processed successfully: id={}, org={}, account={}",
                    savedEvent.getId(),
                    organization.getId(),
                    cloudAccount != null ? cloudAccount.getId() : "N/A"
            );

        } catch (Exception e) {
            log.error("Error processing Kafka event on topic {}: {}", eventTopicName, message, e);
        }
    }
}
