package com.cloud.guardrails.dto;

import lombok.Data;

@Data
public class AccountActivationRequest {
    private String roleArn;
    private String externalId;
}
