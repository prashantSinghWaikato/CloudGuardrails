package com.cloud.guardrails.dto;

import lombok.Data;

@Data
public class AccountRequest {
    private String name;
    private String accountId;
    private String provider;
    private String region;
    private String accessKey;
    private String secretKey;
}
