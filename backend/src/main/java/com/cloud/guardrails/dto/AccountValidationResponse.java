package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountValidationResponse {
    private boolean valid;
    private String provider;
    private String accountId;
    private String arn;
    private String userId;
    private String message;
}
