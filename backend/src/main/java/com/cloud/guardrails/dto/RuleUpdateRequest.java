package com.cloud.guardrails.dto;

import lombok.Data;

@Data
public class RuleUpdateRequest {
    private String description;
    private String severity;
    private Boolean enabled;
    private Boolean autoRemediation;
    private String remediationAction;
}
