package com.cloud.guardrails.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExecutiveReportScheduleRequest {
    private Boolean enabled;
    private Integer dayOfWeek;
    private String scheduledTime;
    private String timeZone;
    private List<String> recipients;
}
