package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.*;
import com.cloud.guardrails.service.ExecutiveReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ExecutiveReportService executiveReportService;

    @PostMapping("/executive-summary/generate")
    public ExecutiveReportResponse generateNow(@RequestBody(required = false) ExecutiveReportGenerationRequest request) {
        return executiveReportService.generateNow(request != null ? request : new ExecutiveReportGenerationRequest());
    }

    @GetMapping("/executive-summary/schedule")
    public ExecutiveReportScheduleResponse getSchedule() {
        return executiveReportService.getSchedule();
    }

    @PutMapping("/executive-summary/schedule")
    public ExecutiveReportScheduleResponse upsertSchedule(@RequestBody ExecutiveReportScheduleRequest request) {
        return executiveReportService.upsertSchedule(request);
    }

    @GetMapping("/executive-summary/runs")
    public List<ExecutiveReportResponse> getRecentRuns() {
        return executiveReportService.getRecentRuns();
    }

    @PostMapping("/executive-summary/run-scheduled-now")
    public ExecutiveReportResponse runScheduledNow() {
        return executiveReportService.runScheduledNow();
    }
}
