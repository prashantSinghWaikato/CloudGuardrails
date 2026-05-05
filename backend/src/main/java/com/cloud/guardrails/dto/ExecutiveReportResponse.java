package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExecutiveReportResponse {
    Long id;
    Long definitionId;
    String reportType;
    String reportName;
    String triggerType;
    String requestedBy;
    String periodStart;
    String periodEnd;
    String status;
    String aiProvider;
    String emailStatus;
    String emailedAt;
    String createdAt;
    String summaryText;
    ExecutiveReportMetricsResponse metrics;
    List<String> recipients;
    String errorMessage;
}
