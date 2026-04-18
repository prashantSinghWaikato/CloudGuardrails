package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RemediationResponse {
    private Long id;
    private String action;
    private String status;
    private Integer attemptCount;
    private LocalDateTime executedAt;
    private LocalDateTime lastVerifiedAt;
    private String ruleName;
    private String resourceId;
    private String targetAccountId;
    private String targetResourceId;
    private String lastTriggeredBy;
    private String lastTriggerSource;
    private String verificationStatus;
    private String verificationMessage;
    private Map<String, Object> response;
    private List<RemediationExecutionResponse> history;
}
