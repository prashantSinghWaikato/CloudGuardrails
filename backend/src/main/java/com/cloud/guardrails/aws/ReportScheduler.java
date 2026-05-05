package com.cloud.guardrails.aws;

import com.cloud.guardrails.service.ExecutiveReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportScheduler {

    private final ExecutiveReportService executiveReportService;

    @Scheduled(fixedDelayString = "${app.reports.scheduler.fixed-delay-ms:600000}")
    public void runDueReports() {
        executiveReportService.executeDueSchedules();
    }
}
