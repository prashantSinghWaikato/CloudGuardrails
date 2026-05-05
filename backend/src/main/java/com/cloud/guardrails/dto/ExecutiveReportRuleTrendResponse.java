package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExecutiveReportRuleTrendResponse {
    String ruleName;
    long count;
}
