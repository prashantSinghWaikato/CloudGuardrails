package com.cloud.guardrails.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class EventDTO {
    private String externalEventId;
    private String eventType;
    private String resourceId;
    private Long organizationId;
    private Long cloudAccountId;
    private LocalDateTime timestamp;
    private Map<String, Object> payload;
}
