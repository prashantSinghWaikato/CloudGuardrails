package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String message;
    private String severity;
    private String resourceId;
    private Boolean read;
    private String createdAt;
    private Long violationId;
    private Long remediationId;
}
