package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExecutiveReportAccountRiskResponse {
    String accountId;
    String accountName;
    long openFindings;
    long criticalFindings;
    long findingsCreated;
}
