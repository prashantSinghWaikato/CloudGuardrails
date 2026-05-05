package com.cloud.guardrails.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExecutiveReportScheduleResponse {
    Long id;
    String reportType;
    String name;
    boolean enabled;
    Integer dayOfWeek;
    String scheduledTime;
    String timeZone;
    List<String> recipients;
    String nextRunAt;
    String lastRunAt;
}
