package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.EventDTO;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.exception.UnauthorizedException;
import com.cloud.guardrails.util.TimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwsPushIngestionService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final CloudAccountService cloudAccountService;
    private final EventIngestionService eventIngestionService;
    private final ObjectMapper objectMapper;

    @Value("${ingestion.aws.shared-secret}")
    private String sharedSecret;

    public void ingest(JsonNode requestBody, String providedSecret) throws JsonProcessingException {
        validateSecret(providedSecret);

        JsonNode detail = extractDetailNode(requestBody);
        String awsAccountId = resolveAwsAccountId(requestBody, detail);

        if (awsAccountId == null || awsAccountId.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve AWS account ID from event payload");
        }

        CloudAccount cloudAccount = cloudAccountService.getAwsAccountByExternalAccountId(awsAccountId);

        EventDTO event = new EventDTO();
        event.setExternalEventId(resolveExternalEventId(requestBody, detail));
        event.setEventType(resolveEventType(requestBody, detail));
        event.setResourceId(resolveResourceId(detail));
        event.setOrganizationId(cloudAccount.getOrganization().getId());
        event.setCloudAccountId(cloudAccount.getId());
        event.setTimestamp(resolveTimestamp(requestBody, detail));
        event.setPayload(buildNormalizedPayload(requestBody, detail));

        if (event.getEventType() == null || event.getEventType().isBlank()) {
            throw new IllegalArgumentException("Unable to resolve AWS event type from event payload");
        }

        eventIngestionService.ingest(event);
    }

    private void validateSecret(String providedSecret) {
        if (providedSecret == null || !providedSecret.equals(sharedSecret)) {
            throw new UnauthorizedException("Invalid ingestion secret");
        }
    }

    private JsonNode extractDetailNode(JsonNode requestBody) {
        JsonNode detail = requestBody.path("detail");
        if (!detail.isMissingNode() && detail.isObject()) {
            return detail;
        }

        return requestBody;
    }

    private String resolveAwsAccountId(JsonNode root, JsonNode detail) {
        return firstNonBlank(
                text(root, "account"),
                text(detail, "recipientAccountId"),
                text(detail.path("userIdentity"), "accountId")
        );
    }

    private String resolveExternalEventId(JsonNode root, JsonNode detail) {
        return firstNonBlank(
                text(detail, "eventID"),
                text(detail, "eventId"),
                text(root, "id")
        );
    }

    private String resolveEventType(JsonNode root, JsonNode detail) {
        return firstNonBlank(
                text(detail, "eventName"),
                text(root, "detail-type"),
                text(root, "eventName")
        );
    }

    private String resolveResourceId(JsonNode detail) {
        String securityGroupId = text(detail.at("/requestParameters/groupId"));
        if (securityGroupId != null) {
            return securityGroupId;
        }

        String bucketName = text(detail.at("/requestParameters/bucketName"));
        if (bucketName != null) {
            return bucketName;
        }

        String userName = text(detail.at("/requestParameters/userName"));
        if (userName != null) {
            return userName;
        }

        JsonNode resources = detail.path("resources");
        if (resources.isArray()) {
            for (JsonNode resource : resources) {
                String value = firstNonBlank(
                        text(resource, "ARN"),
                        text(resource, "arn"),
                        text(resource, "resourceName"),
                        text(resource, "resourceType")
                );
                if (value != null) {
                    return value;
                }
            }
        }

        return firstNonBlank(text(detail, "eventID"), text(detail, "eventId"), "unknown-resource");
    }

    private LocalDateTime resolveTimestamp(JsonNode root, JsonNode detail) {
        String timestamp = firstNonBlank(
                text(root, "time"),
                text(detail, "eventTime")
        );

        if (timestamp == null) {
            return null;
        }

        try {
            return TimeUtils.fromInstantUtc(Instant.parse(timestamp));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildNormalizedPayload(JsonNode root, JsonNode detail) {
        LinkedHashMap<String, Object> payload = objectMapper.convertValue(detail, MAP_TYPE);

        payload.putIfAbsent("sourceIp", text(detail, "sourceIPAddress"));
        payload.putIfAbsent("user", resolveUser(detail));
        payload.putIfAbsent("userName", text(detail.at("/requestParameters/userName")));
        payload.putIfAbsent("policy", resolvePolicy(detail));
        payload.putIfAbsent("accessKeyId", resolveAccessKeyId(detail));
        payload.putIfAbsent("bucketName", text(detail.at("/requestParameters/bucketName")));
        payload.putIfAbsent("port", resolvePort(detail));
        payload.putIfAbsent("cidr", resolveCidr(detail));
        payload.putIfAbsent("instanceType", text(detail.at("/requestParameters/instanceType")));
        payload.putIfAbsent("hasTag", resolveHasTag(detail));
        payload.putIfAbsent("acl", resolveAcl(detail));
        payload.putIfAbsent("principal", resolvePrincipal(detail));
        payload.putIfAbsent("encryption", resolveEncryption(detail));
        payload.put("rawEventBridgeEnvelope", objectMapper.convertValue(root, MAP_TYPE));

        payload.entrySet().removeIf(entry -> entry.getValue() == null);
        return payload;
    }

    private String resolveUser(JsonNode detail) {
        String type = text(detail.at("/userIdentity/type"));
        if ("Root".equalsIgnoreCase(type)) {
            return "root";
        }

        return firstNonBlank(
                text(detail.at("/requestParameters/userName")),
                text(detail.at("/userIdentity/userName")),
                text(detail.at("/userIdentity/arn"))
        );
    }

    private String resolvePolicy(JsonNode detail) {
        String policyArn = text(detail.at("/requestParameters/policyArn"));
        if (policyArn == null) {
            return null;
        }

        int lastSlash = policyArn.lastIndexOf('/');
        return lastSlash >= 0 ? policyArn.substring(lastSlash + 1) : policyArn;
    }

    private String resolveAccessKeyId(JsonNode detail) {
        return firstNonBlank(
                text(detail.at("/requestParameters/accessKeyId")),
                text(detail.at("/responseElements/accessKey/accessKeyId"))
        );
    }

    private Integer resolvePort(JsonNode detail) {
        JsonNode portNode = detail.at("/requestParameters/ipPermissions/items/0/fromPort");
        if (portNode.isNumber()) {
            return portNode.asInt();
        }

        portNode = detail.at("/requestParameters/fromPort");
        if (portNode.isNumber()) {
            return portNode.asInt();
        }

        return null;
    }

    private String resolveCidr(JsonNode detail) {
        return firstNonBlank(
                text(detail.at("/requestParameters/ipPermissions/items/0/ipRanges/items/0/cidrIp")),
                text(detail.at("/requestParameters/cidrIp")),
                text(detail.at("/responseElements/securityGroupRuleSet/items/0/cidrIpv4"))
        );
    }

    private String resolveHasTag(JsonNode detail) {
        JsonNode items = detail.at("/requestParameters/tagSpecificationSet/items");
        if (items.isArray()) {
            return items.isEmpty() ? "false" : "true";
        }

        return null;
    }

    private String resolveAcl(JsonNode detail) {
        return firstNonBlank(
                text(detail.at("/requestParameters/x-amz-acl")),
                text(detail.at("/requestParameters/acl")),
                text(detail.at("/requestParameters/accessControlPolicy/cannedAccessControlList"))
        );
    }

    private String resolvePrincipal(JsonNode detail) {
        String policy = firstNonBlank(
                text(detail.at("/requestParameters/policy")),
                text(detail.at("/requestParameters/bucketPolicy"))
        );

        if (policy == null) {
            return null;
        }

        try {
            JsonNode policyNode = objectMapper.readTree(policy);
            JsonNode principalNode = policyNode.at("/Statement/0/Principal");
            if (principalNode.isTextual()) {
                return principalNode.asText();
            }

            JsonNode awsPrincipal = principalNode.path("AWS");
            if (awsPrincipal.isTextual()) {
                return awsPrincipal.asText();
            }
        } catch (Exception ignored) {
            if (policy.contains("\"Principal\":\"*\"") || policy.contains("\"AWS\":\"*\"")) {
                return "*";
            }
        }

        return null;
    }

    private String resolveEncryption(JsonNode detail) {
        JsonNode encryptionConfig = detail.at("/requestParameters/serverSideEncryptionConfiguration");
        if (!encryptionConfig.isMissingNode() && !encryptionConfig.isNull()) {
            return "true";
        }

        return null;
    }

    private String text(JsonNode node, String fieldName) {
        return text(node.path(fieldName));
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }
}
