package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.EventDTO;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final EventRepository eventRepository;
    private final ViolationService violationService;
    private final OrganizationService organizationService;
    private final CloudAccountService cloudAccountService;

    public void ingest(EventDTO dto) {
        Organization organization = organizationService.getById(dto.getOrganizationId());

        if (dto.getCloudAccountId() == null) {
            throw new IllegalArgumentException("Cloud account ID is required");
        }

        CloudAccount cloudAccount = cloudAccountService
                .getByIdForOrganization(dto.getCloudAccountId(), organization.getId());

        if (dto.getExternalEventId() != null
                && !dto.getExternalEventId().isBlank()
                && eventRepository.existsByCloudAccount_IdAndExternalEventId(
                cloudAccount.getId(),
                dto.getExternalEventId())
        ) {
            return;
        }

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
        violationService.evaluate(savedEvent);
    }
}
