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
import java.util.LinkedHashMap;
import java.util.List;
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
            Map<String, Object> rawPayload = objectMapper.readValue(cloudTrailEvent, new TypeReference<>() {
            });
            return normalizePayload(event, rawPayload);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizePayload(software.amazon.awssdk.services.cloudtrail.model.Event event,
                                                 Map<String, Object> rawPayload) {
        Map<String, Object> payload = new LinkedHashMap<>(rawPayload);

        payload.putIfAbsent("eventName", event.eventName());
        payload.putIfAbsent("sourceIp", readString(rawPayload, List.of("sourceIPAddress")));

        switch (event.eventName()) {
            case "AuthorizeSecurityGroupIngress" -> normalizeSecurityGroupIngress(payload, rawPayload);
            case "PutBucketAcl" -> normalizeBucketAcl(payload, rawPayload);
            case "PutBucketPolicy" -> normalizeBucketPolicy(payload, rawPayload);
            case "PutBucketEncryption" -> normalizeBucketEncryption(payload, rawPayload);
            case "AttachUserPolicy" -> normalizeAttachUserPolicy(payload, rawPayload);
            case "ConsoleLogin" -> normalizeConsoleLogin(payload, rawPayload);
            case "RunInstances" -> normalizeRunInstances(payload, rawPayload);
            default -> {
                // Leave raw CloudTrail payload as-is for other event types.
            }
        }

        return payload;
    }

    private void normalizeSecurityGroupIngress(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("port", readInteger(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.fromPort",
                "requestParameters.ipPermissions.items.0.toPort"
        )));
        payload.putIfAbsent("fromPort", readInteger(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.fromPort"
        )));
        payload.putIfAbsent("toPort", readInteger(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.toPort"
        )));
        payload.putIfAbsent("cidr", readString(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.ipRanges.items.0.cidrIp"
        )));
        payload.putIfAbsent("protocol", readString(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.ipProtocol"
        )));
    }

    private void normalizeBucketAcl(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("acl", readString(rawPayload, List.of("requestParameters.x-amz-acl")));
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));
    }

    private void normalizeBucketPolicy(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));
        String policyDocument = readString(rawPayload, List.of("requestParameters.bucketPolicy"));
        if (policyDocument == null || policyDocument.isBlank()) {
            return;
        }

        try {
            Map<String, Object> policy = objectMapper.readValue(policyDocument, new TypeReference<>() {
            });
            payload.putIfAbsent("policyDocument", policy);
            Object principal = readValue(policy, "Statement.0.Principal");
            if (principal instanceof String principalText) {
                payload.putIfAbsent("principal", principalText);
            } else if (principal instanceof Map<?, ?> principalMap && principalMap.containsKey("AWS")) {
                payload.putIfAbsent("principal", String.valueOf(principalMap.get("AWS")));
            }
        } catch (Exception ex) {
            payload.putIfAbsent("policyDocument", policyDocument);
        }
    }

    private void normalizeBucketEncryption(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));
        String encryptionEnabled = readString(rawPayload, List.of(
                "requestParameters.serverSideEncryptionConfiguration.rules.0.applyServerSideEncryptionByDefault.sseAlgorithm"
        ));
        payload.putIfAbsent("encryption", encryptionEnabled == null || encryptionEnabled.isBlank() ? "false" : "true");
    }

    private void normalizeAttachUserPolicy(Map<String, Object> payload, Map<String, Object> rawPayload) {
        String policyArn = readString(rawPayload, List.of("requestParameters.policyArn"));
        if (policyArn != null && !policyArn.isBlank()) {
            int index = policyArn.lastIndexOf('/');
            payload.putIfAbsent("policy", index >= 0 ? policyArn.substring(index + 1) : policyArn);
        }
    }

    private void normalizeConsoleLogin(Map<String, Object> payload, Map<String, Object> rawPayload) {
        String arn = readString(rawPayload, List.of("userIdentity.arn"));
        if (arn != null && arn.contains(":root")) {
            payload.putIfAbsent("user", "root");
            return;
        }

        String userName = readString(rawPayload, List.of("userIdentity.userName"));
        if (userName != null && !userName.isBlank()) {
            payload.putIfAbsent("user", userName);
        }
    }

    private void normalizeRunInstances(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("instanceType", readString(rawPayload, List.of(
                "requestParameters.instanceType"
        )));

        Object tagItems = readValue(rawPayload, "requestParameters.tagSpecificationSet.items.0.tags");
        boolean hasTags = tagItems instanceof List<?> list && !list.isEmpty();
        payload.putIfAbsent("hasTag", hasTags ? "true" : "false");
    }

    private String readString(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    // Keep checking fallbacks.
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object readValue(Map<String, Object> payload, String path) {
        Object current = payload;

        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
                continue;
            }

            if (current instanceof List<?> list) {
                int index;
                try {
                    index = Integer.parseInt(part);
                } catch (NumberFormatException ex) {
                    return null;
                }

                if (index < 0 || index >= list.size()) {
                    return null;
                }

                current = list.get(index);
                continue;
            }

            return null;
        }

        return current;
    }
}
