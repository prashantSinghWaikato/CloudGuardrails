package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ViolationDetailResponse {
    private Long id;
    private String ruleName;
    private String severity;
    private String status;
    private String resourceId;
    private String accountId;
    private String provider;
    private String region;
    private String organizationName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String eventType;
    private String externalEventId;
    private LocalDateTime eventTimestamp;
    private Map<String, Object> payload;
    private RemediationResponse remediation;
}
