package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountResponse {
    private Long id;
    private String name;
    private String accountId;
    private String provider;
    private String region;
    private Boolean monitoringEnabled;
    private String activationStatus;
    private String activationMethod;
    private String roleArn;
    private String lastActivatedAt;
    private String lastSyncAt;
    private String lastSyncStatus;
    private String lastSyncMessage;
}
