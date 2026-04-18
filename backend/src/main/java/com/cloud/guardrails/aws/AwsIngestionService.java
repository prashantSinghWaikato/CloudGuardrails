package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.repository.EventRepository;
import com.cloud.guardrails.service.ViolationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsIngestionService {

    private final EventRepository eventRepository;
    private final ViolationService violationService;
    private final ObjectMapper objectMapper;
    private final AwsClientFactory awsClientFactory;
    private final com.cloud.guardrails.repository.CloudAccountRepository cloudAccountRepository;

    public void ingest(CloudAccount account) {
        validateAccount(account);

        try (CloudTrailClient client = awsClientFactory.createCloudTrailClient(account)) {

            client.lookupEvents().events().forEach(e -> {
                try {
                    String externalEventId = resolveExternalEventId(e);

                    if (eventRepository.existsByCloudAccount_IdAndExternalEventId(account.getId(), externalEventId)) {
                        return;
                    }

                    Event savedEvent = eventRepository.saveAndFlush(buildEvent(account, e));
                    violationService.evaluate(savedEvent);
                } catch (DataIntegrityViolationException duplicate) {
                    log.debug("Skipping duplicate CloudTrail event {} for account {}",
                            e.eventId(),
                            account.getId());
                } catch (Exception ex) {
                    log.error("Failed to ingest CloudTrail event for account {}", account.getId(), ex);
                }
            });

            markSync(account, "SUCCESS", "CloudTrail polling completed successfully");
        } catch (Exception ex) {
            markSync(account, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private Event buildEvent(CloudAccount account,
                             software.amazon.awssdk.services.cloudtrail.model.Event event) {
        return Event.builder()
                .eventType(event.eventName())
                .resourceId(resolveResourceId(event))
                .externalEventId(resolveExternalEventId(event))
                .payload(extractPayload(event))
                .organization(account.getOrganization())
                .cloudAccount(account)
                .timestamp(resolveTimestamp(event))
                .build();
    }

    private Map<String, Object> extractPayload(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        String cloudTrailEvent = event.cloudTrailEvent();

        if (cloudTrailEvent == null || cloudTrailEvent.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(cloudTrailEvent, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse CloudTrail payload for event {}", event.eventId(), ex);
            return Map.of("rawEvent", cloudTrailEvent);
        }
    }

    private String resolveResourceId(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        if (event.resources() != null && !event.resources().isEmpty()) {
            return event.resources().stream()
                    .map(resource -> resource.resourceName() != null
                            ? resource.resourceName()
                            : resource.resourceType())
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(event.eventId());
        }

        return event.eventId();
    }

    private String resolveExternalEventId(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        if (event.eventId() != null && !event.eventId().isBlank()) {
            return event.eventId();
        }

        return "%s:%s:%s".formatted(
                event.eventName(),
                resolveTimestamp(event),
                resolveResourceId(event)
        );
    }

    private LocalDateTime resolveTimestamp(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        if (event.eventTime() != null) {
            return LocalDateTime.ofInstant(event.eventTime(), ZoneId.systemDefault());
        }

        return LocalDateTime.now();
    }

    private void validateAccount(CloudAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Cloud account is required");
        }

        if (isBlank(account.getAccessKey()) || isBlank(account.getSecretKey()) || isBlank(account.getRegion())) {
            throw new IllegalArgumentException(
                    "Cloud account is missing AWS credentials or region: " + account.getId()
            );
        }

        if (!Boolean.TRUE.equals(account.getMonitoringEnabled())) {
            throw new IllegalArgumentException("Cloud account monitoring is not activated: " + account.getId());
        }

        if (account.getOrganization() == null) {
            throw new IllegalArgumentException("Cloud account organization is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void markSync(CloudAccount account, String status, String message) {
        account.setLastSyncAt(LocalDateTime.now());
        account.setLastSyncStatus(status);
        account.setLastSyncMessage(message != null && message.length() > 1024
                ? message.substring(0, 1024)
                : message);
        cloudAccountRepository.save(account);
    }
}
