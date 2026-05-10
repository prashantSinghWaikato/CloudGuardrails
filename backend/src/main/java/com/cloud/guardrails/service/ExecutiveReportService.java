package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.*;
import com.cloud.guardrails.entity.*;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.*;
import com.cloud.guardrails.security.UserContext;
import com.cloud.guardrails.util.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutiveReportService {

    private static final String REPORT_TYPE = "EXECUTIVE_WEEKLY";

    private final OrganizationRepository organizationRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final ReportRunRepository reportRunRepository;
    private final ViolationRepository violationRepository;
    private final RemediationRepository remediationRepository;
    private final CloudAccountRepository cloudAccountRepository;
    private final AccountScanRunRepository accountScanRunRepository;
    private final GeminiExecutiveSummaryService geminiExecutiveSummaryService;
    private final ReportEmailService reportEmailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExecutiveReportResponse generateNow(ExecutiveReportGenerationRequest request) {
        Long orgId = UserContext.getOrgId();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        LocalDateTime to = parseDateTimeOrDefault(request.getTo(), TimeUtils.utcNow());
        LocalDateTime from = parseDateTimeOrDefault(request.getFrom(), to.minusDays(7));
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Report start must be before end");
        }

        return runReport(organization, null, from, to, "MANUAL", UserContext.getEmail(), List.of());
    }

    @Transactional(readOnly = true)
    public ExecutiveReportScheduleResponse getSchedule() {
        Long orgId = UserContext.getOrgId();

        return reportDefinitionRepository.findByOrganizationIdAndReportType(orgId, REPORT_TYPE)
                .map(this::toScheduleResponse)
                .orElse(ExecutiveReportScheduleResponse.builder()
                        .reportType(REPORT_TYPE)
                        .name("Weekly Executive Summary")
                        .enabled(false)
                        .dayOfWeek(DayOfWeek.MONDAY.getValue())
                        .scheduledTime("09:00")
                        .timeZone("UTC")
                        .recipients(List.of())
                        .nextRunAt(null)
                        .lastRunAt(null)
                        .build());
    }

    @Transactional
    public ExecutiveReportScheduleResponse upsertSchedule(ExecutiveReportScheduleRequest request) {
        Long orgId = UserContext.getOrgId();
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        ReportDefinition definition = reportDefinitionRepository
                .findByOrganizationIdAndReportType(orgId, REPORT_TYPE)
                .orElseGet(() -> ReportDefinition.builder()
                        .organization(organization)
                        .reportType(REPORT_TYPE)
                        .name("Weekly Executive Summary")
                        .frequency("WEEKLY")
                        .createdBy(UserContext.getEmail())
                        .createdAt(TimeUtils.utcNow())
                        .build());

        definition.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        definition.setDayOfWeek(validateDayOfWeek(request.getDayOfWeek()));
        definition.setScheduledTime(parseScheduledTime(request.getScheduledTime()));
        definition.setTimeZone(normalizeTimeZone(request.getTimeZone()));
        definition.setRecipientEmails(normalizeRecipients(request.getRecipients()));
        definition.setUpdatedAt(TimeUtils.utcNow());

        return toScheduleResponse(reportDefinitionRepository.save(definition));
    }

    @Transactional(readOnly = true)
    public List<ExecutiveReportResponse> getRecentRuns() {
        Long orgId = UserContext.getOrgId();
        return reportRunRepository.findTop10ByOrganizationIdAndReportTypeOrderByCreatedAtDesc(orgId, REPORT_TYPE)
                .stream()
                .map(this::toReportResponse)
                .toList();
    }

    @Transactional
    public ExecutiveReportResponse runScheduledNow() {
        Long orgId = UserContext.getOrgId();
        ReportDefinition definition = reportDefinitionRepository.findByOrganizationIdAndReportType(orgId, REPORT_TYPE)
                .orElseThrow(() -> new NotFoundException("Report schedule not found"));

        LocalDateTime periodEnd = TimeUtils.utcNow();
        LocalDateTime periodStart = periodEnd.minusDays(7);

        return runReport(
                definition.getOrganization(),
                definition,
                periodStart,
                periodEnd,
                "SCHEDULED",
                UserContext.getEmail(),
                definition.getRecipientEmails() == null ? List.of() : definition.getRecipientEmails()
        );
    }

    @Transactional
    public void executeDueSchedules() {
        List<ReportDefinition> definitions = reportDefinitionRepository.findByEnabledTrueAndReportType(REPORT_TYPE);
        for (ReportDefinition definition : definitions) {
            try {
                executeIfDue(definition);
            } catch (Exception ex) {
                log.warn("Scheduled report execution failed for definition {}", definition.getId(), ex);
            }
        }
    }

    private void executeIfDue(ReportDefinition definition) {
        ZoneId zoneId = ZoneId.of(normalizeTimeZone(definition.getTimeZone()));
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime scheduledOccurrence = now
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(definition.getDayOfWeek())))
                .with(definition.getScheduledTime())
                .withSecond(0)
                .withNano(0);

        if (scheduledOccurrence.isAfter(now)) {
            scheduledOccurrence = scheduledOccurrence.minusWeeks(1);
        }

        LocalDateTime periodEnd = scheduledOccurrence.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime periodStart = periodEnd.minusDays(7);

        if (reportRunRepository.existsByReportDefinitionIdAndPeriodStartAndPeriodEnd(definition.getId(), periodStart, periodEnd)) {
            return;
        }

        runReport(
                definition.getOrganization(),
                definition,
                periodStart,
                periodEnd,
                "SCHEDULED",
                definition.getCreatedBy(),
                definition.getRecipientEmails()
        );
    }

    private ExecutiveReportResponse runReport(Organization organization,
                                              ReportDefinition definition,
                                              LocalDateTime from,
                                              LocalDateTime to,
                                              String triggerType,
                                              String requestedBy,
                                              List<String> recipients) {
        ReportRun run = ReportRun.builder()
                .organization(organization)
                .reportDefinition(definition)
                .reportType(REPORT_TYPE)
                .reportName(definition != null ? definition.getName() : "Executive Summary")
                .triggerType(triggerType)
                .requestedBy(requestedBy)
                .periodStart(from)
                .periodEnd(to)
                .status("GENERATING")
                .emailStatus((recipients == null || recipients.isEmpty()) ? "SKIPPED" : "PENDING")
                .emailRecipients(recipients == null ? List.of() : recipients)
                .createdAt(TimeUtils.utcNow())
                .updatedAt(TimeUtils.utcNow())
                .build();

        run = reportRunRepository.save(run);

        try {
            ExecutiveReportMetricsResponse metrics = buildMetrics(organization.getId(), from, to);
            GeminiExecutiveSummaryService.SummaryResult summaryResult =
                    geminiExecutiveSummaryService.generate(from, to, metrics);

            run.setSummaryText(summaryResult.summaryText());
            run.setAiProvider(summaryResult.provider());
            run.setSummaryData(objectMapper.convertValue(metrics, Map.class));
            run.setStatus("SUCCESS");
            run.setUpdatedAt(TimeUtils.utcNow());

            if (definition != null) {
                definition.setLastRunAt(TimeUtils.utcNow());
                definition.setUpdatedAt(TimeUtils.utcNow());
                reportDefinitionRepository.save(definition);
            }

            if (recipients != null && !recipients.isEmpty()) {
                ReportEmailService.DeliveryResult delivery = reportEmailService.sendExecutiveSummary(run, recipients);
                run.setEmailStatus(delivery.status());
                if ("SENT".equals(delivery.status())) {
                    run.setEmailedAt(TimeUtils.utcNow());
                } else if (delivery.errorMessage() != null) {
                    run.setErrorMessage(delivery.errorMessage());
                }
            }
        } catch (Exception ex) {
            run.setStatus("FAILED");
            run.setErrorMessage(ex.getMessage());
            run.setUpdatedAt(TimeUtils.utcNow());
        }

        return toReportResponse(reportRunRepository.save(run));
    }

    private ExecutiveReportMetricsResponse buildMetrics(Long orgId, LocalDateTime from, LocalDateTime to) {
        List<Violation> violations = violationRepository.findByOrganizationId(orgId);
        List<Remediation> remediations = remediationRepository.findByViolation_Organization_Id(orgId);
        List<CloudAccount> accounts = cloudAccountRepository.findByOrganizationId(orgId);
        List<AccountScanRun> scanRuns = accountScanRunRepository
                .findByOrganizationIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(orgId, from, to);
        Duration reportingWindow = Duration.between(from, to);
        LocalDateTime previousFrom = from.minus(reportingWindow);
        LocalDateTime previousTo = from;

        List<Violation> findingsInPeriod = violations.stream()
                .filter(v -> withinRange(v.getCreatedAt(), from, to))
                .toList();
        List<Violation> previousFindings = violations.stream()
                .filter(v -> withinRange(v.getCreatedAt(), previousFrom, previousTo))
                .toList();

        long totalFindings = findingsInPeriod.size();
        long openFindings = violations.stream().filter(v -> "OPEN".equalsIgnoreCase(v.getStatus())).count();
        long criticalFindings = violations.stream()
                .filter(v -> "OPEN".equalsIgnoreCase(v.getStatus()))
                .filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity()))
                .count();
        long closedFindings = violations.stream()
                .filter(v -> "FIXED".equalsIgnoreCase(v.getStatus()))
                .filter(v -> withinRange(v.getUpdatedAt(), from, to))
                .count();
        long previousTotalFindings = previousFindings.size();
        long previousOpenFindings = violations.stream()
                .filter(v -> "OPEN".equalsIgnoreCase(v.getStatus()))
                .filter(v -> v.getCreatedAt() != null && v.getCreatedAt().isBefore(from))
                .count();
        long previousCriticalFindings = violations.stream()
                .filter(v -> "OPEN".equalsIgnoreCase(v.getStatus()))
                .filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity()))
                .filter(v -> v.getCreatedAt() != null && v.getCreatedAt().isBefore(from))
                .count();
        long previousClosedFindings = violations.stream()
                .filter(v -> "FIXED".equalsIgnoreCase(v.getStatus()))
                .filter(v -> withinRange(v.getUpdatedAt(), previousFrom, previousTo))
                .count();

        long remediationSuccessCount = remediations.stream()
                .filter(r -> "PASSED".equalsIgnoreCase(r.getVerificationStatus()))
                .filter(r -> withinRange(r.getLastVerifiedAt(), from, to))
                .count();
        long remediationFailureCount = remediations.stream()
                .filter(r -> "FAILED".equalsIgnoreCase(r.getVerificationStatus())
                        || "ERROR".equalsIgnoreCase(r.getVerificationStatus())
                        || "FAILED".equalsIgnoreCase(r.getStatus()))
                .filter(r -> withinRange(firstNonNull(r.getLastVerifiedAt(), r.getExecutedAt(), r.getCreatedAt()), from, to))
                .count();

        Map<Long, Long> createdByAccount = findingsInPeriod.stream()
                .filter(v -> v.getCloudAccount() != null)
                .collect(Collectors.groupingBy(v -> v.getCloudAccount().getId(), Collectors.counting()));

        List<ExecutiveReportAccountRiskResponse> topAccounts = accounts.stream()
                .map(account -> ExecutiveReportAccountRiskResponse.builder()
                        .accountId(account.getAccountId())
                        .accountName(account.getName())
                        .openFindings(violations.stream()
                                .filter(v -> v.getCloudAccount() != null && account.getId().equals(v.getCloudAccount().getId()))
                                .filter(v -> "OPEN".equalsIgnoreCase(v.getStatus()))
                                .count())
                        .criticalFindings(violations.stream()
                                .filter(v -> v.getCloudAccount() != null && account.getId().equals(v.getCloudAccount().getId()))
                                .filter(v -> "OPEN".equalsIgnoreCase(v.getStatus()))
                                .filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity()))
                                .count())
                        .findingsCreated(createdByAccount.getOrDefault(account.getId(), 0L))
                        .build())
                .sorted(Comparator.comparingLong(ExecutiveReportAccountRiskResponse::getCriticalFindings).reversed()
                        .thenComparingLong(ExecutiveReportAccountRiskResponse::getOpenFindings).reversed()
                        .thenComparingLong(ExecutiveReportAccountRiskResponse::getFindingsCreated).reversed())
                .limit(3)
                .toList();

        List<ExecutiveReportRuleTrendResponse> topRules = findingsInPeriod.stream()
                .collect(Collectors.groupingBy(v -> v.getRule() != null ? v.getRule().getRuleName() : "Unknown", Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> ExecutiveReportRuleTrendResponse.builder()
                        .ruleName(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();

        int accountsScanned = (int) scanRuns.stream()
                .filter(run -> "SUCCESS".equalsIgnoreCase(run.getStatus()))
                .map(run -> run.getCloudAccount().getId())
                .distinct()
                .count();

        LocalDateTime staleThreshold = to.minusDays(7);
        List<CloudAccount> staleAccounts = accounts.stream()
                .filter(account -> account.getLastSyncAt() == null || account.getLastSyncAt().isBefore(staleThreshold))
                .toList();

        List<String> coverageNotes = new ArrayList<>();
        coverageNotes.add(accountsScanned + " accounts completed a scan during the reporting period.");
        if (!staleAccounts.isEmpty()) {
            coverageNotes.add(staleAccounts.size() + " accounts have stale or missing scan coverage.");
        }
        if (topRules.isEmpty()) {
            coverageNotes.add("No findings were generated during this period.");
        }

        return ExecutiveReportMetricsResponse.builder()
                .totalFindings(totalFindings)
                .openFindings(openFindings)
                .criticalFindings(criticalFindings)
                .closedFindings(closedFindings)
                .previousTotalFindings(previousTotalFindings)
                .previousOpenFindings(previousOpenFindings)
                .previousCriticalFindings(previousCriticalFindings)
                .previousClosedFindings(previousClosedFindings)
                .remediationSuccessCount(remediationSuccessCount)
                .remediationFailureCount(remediationFailureCount)
                .accountsScanned(accountsScanned)
                .staleAccounts(staleAccounts.size())
                .topAccounts(topAccounts)
                .topRules(topRules)
                .coverageNotes(coverageNotes)
                .build();
    }

    private ExecutiveReportScheduleResponse toScheduleResponse(ReportDefinition definition) {
        return ExecutiveReportScheduleResponse.builder()
                .id(definition.getId())
                .reportType(definition.getReportType())
                .name(definition.getName())
                .enabled(Boolean.TRUE.equals(definition.getEnabled()))
                .dayOfWeek(definition.getDayOfWeek())
                .scheduledTime(definition.getScheduledTime() != null ? definition.getScheduledTime().toString() : null)
                .timeZone(definition.getTimeZone())
                .recipients(definition.getRecipientEmails() == null ? List.of() : definition.getRecipientEmails())
                .nextRunAt(TimeUtils.formatUtc(computeNextRun(definition)))
                .lastRunAt(TimeUtils.formatUtc(definition.getLastRunAt()))
                .build();
    }

    private ExecutiveReportResponse toReportResponse(ReportRun run) {
        ExecutiveReportMetricsResponse metrics = run.getSummaryData() == null
                ? ExecutiveReportMetricsResponse.builder()
                .topAccounts(List.of())
                .topRules(List.of())
                .coverageNotes(List.of())
                .build()
                : objectMapper.convertValue(run.getSummaryData(), ExecutiveReportMetricsResponse.class);

        return ExecutiveReportResponse.builder()
                .id(run.getId())
                .definitionId(run.getReportDefinition() != null ? run.getReportDefinition().getId() : null)
                .reportType(run.getReportType())
                .reportName(run.getReportName())
                .triggerType(run.getTriggerType())
                .requestedBy(run.getRequestedBy())
                .periodStart(TimeUtils.formatUtc(run.getPeriodStart()))
                .periodEnd(TimeUtils.formatUtc(run.getPeriodEnd()))
                .status(run.getStatus())
                .aiProvider(run.getAiProvider())
                .emailStatus(run.getEmailStatus())
                .emailedAt(TimeUtils.formatUtc(run.getEmailedAt()))
                .createdAt(TimeUtils.formatUtc(run.getCreatedAt()))
                .summaryText(run.getSummaryText())
                .metrics(metrics)
                .recipients(run.getEmailRecipients() == null ? List.of() : run.getEmailRecipients())
                .errorMessage(run.getErrorMessage())
                .build();
    }

    private LocalDateTime computeNextRun(ReportDefinition definition) {
        if (definition.getScheduledTime() == null || definition.getDayOfWeek() == null) {
            return null;
        }

        ZoneId zoneId = ZoneId.of(normalizeTimeZone(definition.getTimeZone()));
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = now
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(definition.getDayOfWeek())))
                .with(definition.getScheduledTime())
                .withSecond(0)
                .withNano(0);

        if (!next.isAfter(now)) {
            next = next.plusWeeks(1);
        }

        return next.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }

        return recipients.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Integer validateDayOfWeek(Integer dayOfWeek) {
        if (dayOfWeek == null) {
            return DayOfWeek.MONDAY.getValue();
        }
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Day of week must be between 1 and 7");
        }
        return dayOfWeek;
    }

    private LocalTime parseScheduledTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.of(9, 0);
        }
        return LocalTime.parse(value.trim());
    }

    private String normalizeTimeZone(String value) {
        if (value == null || value.isBlank()) {
            return "UTC";
        }
        ZoneId.of(value.trim());
        return value.trim();
    }

    private LocalDateTime parseDateTimeOrDefault(String value, LocalDateTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private boolean withinRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        return value != null && !value.isBefore(from) && value.isBefore(to);
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
