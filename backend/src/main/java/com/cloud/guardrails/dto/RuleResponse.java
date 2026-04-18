package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuleResponse {
    private Long id;
    private String ruleName;
    private String description;
    private String severity;
    private Boolean enabled;
    private Boolean autoRemediation;
    private String remediationAction;
}
