package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExecutiveReportMetricsResponse {
    long totalFindings;
    long openFindings;
    long criticalFindings;
    long closedFindings;
    long remediationSuccessCount;
    long remediationFailureCount;
    int accountsScanned;
    int staleAccounts;
    List<ExecutiveReportAccountRiskResponse> topAccounts;
    List<ExecutiveReportRuleTrendResponse> topRules;
    List<String> coverageNotes;
}
