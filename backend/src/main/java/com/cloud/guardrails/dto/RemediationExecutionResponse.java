package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RemediationExecutionResponse {
    private Long id;
    private String action;
    private String triggerSource;
    private String triggeredBy;
    private Integer attemptNumber;
    private String targetAccountId;
    private String targetResourceId;
    private String status;
    private String verificationStatus;
    private String verificationMessage;
    private String startedAt;
    private String completedAt;
    private Map<String, Object> response;
}
