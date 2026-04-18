package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViolationResponse {
    private Long id;
    private String ruleName;
    private String resourceId;
    private String severity;
    private String status;
    private String accountId;
}
