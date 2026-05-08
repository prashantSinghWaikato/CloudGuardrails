package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.ExecutiveReportMetricsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiExecutiveSummaryService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.model:gemini-2.5-flash}")
    private String model;

    public SummaryResult generate(LocalDateTime from, LocalDateTime to, ExecutiveReportMetricsResponse metrics) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback(from, to, metrics, "Gemini API key is not configured");
        }

        try {
            RestClient restClient = restClientBuilder
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .build();

            Map<String, Object> payload = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of(
                                    "text", buildPrompt(from, to, metrics)
                            ))
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.3,
                            "maxOutputTokens", 700
                    )
            );

            String raw = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String summary = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            if (summary.isBlank()) {
                return fallback(from, to, metrics, "Gemini returned an empty summary");
            }

            return new SummaryResult("GEMINI", summary.trim(), null);
        } catch (Exception ex) {
            log.warn("Gemini summary generation failed", ex);
            return fallback(from, to, metrics, ex.getMessage());
        }
    }

    private SummaryResult fallback(LocalDateTime from,
                                   LocalDateTime to,
                                   ExecutiveReportMetricsResponse metrics,
                                   String reason) {
        StringBuilder summary = new StringBuilder();
        summary.append("Executive Overview\n");
        summary.append("From ")
                .append(PERIOD_FORMAT.format(from))
                .append(" to ")
                .append(PERIOD_FORMAT.format(to))
                .append(", Cloud Guardrails detected ")
                .append(metrics.getTotalFindings())
                .append(" findings. ")
                .append(metrics.getOpenFindings())
                .append(" remain open, including ")
                .append(metrics.getCriticalFindings())
                .append(" critical issues. ")
                .append(metrics.getClosedFindings())
                .append(" findings were closed during the period.");

        if (metrics.getPreviousTotalFindings() > 0 || metrics.getPreviousClosedFindings() > 0) {
            summary.append(" Compared with the prior period, findings moved from ")
                    .append(metrics.getPreviousTotalFindings())
                    .append(" to ")
                    .append(metrics.getTotalFindings())
                    .append(", while closed findings moved from ")
                    .append(metrics.getPreviousClosedFindings())
                    .append(" to ")
                    .append(metrics.getClosedFindings())
                    .append(".");
        }
        summary.append("\n\n");

        summary.append("Key Risks\n");
        if (!metrics.getTopAccounts().isEmpty()) {
            var account = metrics.getTopAccounts().get(0);
            summary.append("- Highest-risk account: ")
                    .append(account.getAccountName())
                    .append(" (")
                    .append(account.getAccountId())
                    .append(") currently carries ")
                    .append(account.getOpenFindings())
                    .append(" open findings and ")
                    .append(account.getCriticalFindings())
                    .append(" critical issues.\n");
        }

        if (!metrics.getTopRules().isEmpty()) {
            summary.append("- Repeated control gaps this period: ");
            summary.append(
                    metrics.getTopRules().stream()
                            .limit(3)
                            .map(rule -> rule.getRuleName() + " (" + rule.getCount() + ")")
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("none")
            );
            summary.append(".\n");
        }

        if (!metrics.getCoverageNotes().isEmpty()) {
            summary.append("- Coverage note: ")
                    .append(metrics.getCoverageNotes().get(0))
                    .append('\n');
        }

        summary.append("\nRecommended Actions\n");
        if (!metrics.getTopAccounts().isEmpty()) {
            summary.append("- Prioritize review of ")
                    .append(metrics.getTopAccounts().get(0).getAccountName())
                    .append(" because it currently concentrates the most operational risk.\n");
        }
        if (!metrics.getTopRules().isEmpty()) {
            summary.append("- Address the recurring gap around ")
                    .append(metrics.getTopRules().get(0).getRuleName())
                    .append(" to reduce repeat findings.\n");
        }
        if (metrics.getStaleAccounts() > 0) {
            summary.append("- Restore scan coverage for stale accounts before the next reporting cycle.\n");
        } else {
            summary.append("- Maintain current scan coverage across all onboarded accounts.\n");
        }

        if (reason != null && !reason.isBlank()) {
            summary.append("\nSystem Note\n");
            summary.append("- AI narrative fallback used: ").append(reason);
        }

        return new SummaryResult("TEMPLATE", summary.toString().trim(), reason);
    }

    private String buildPrompt(LocalDateTime from, LocalDateTime to, ExecutiveReportMetricsResponse metrics) throws Exception {
        Map<String, Object> promptPayload = new LinkedHashMap<>();
        promptPayload.put("periodStart", PERIOD_FORMAT.format(from));
        promptPayload.put("periodEnd", PERIOD_FORMAT.format(to));
        promptPayload.put("metrics", metrics);

        return """
                You are writing a concise enterprise cloud security weekly executive summary.
                Use only the facts provided. Do not invent numbers, causes, or systems.
                Keep the tone professional and operational, not marketing.
                Use exactly these headings and plain text only:
                Executive Overview
                Key Risks
                Recommended Actions
                Under Executive Overview: write one short paragraph with the most important changes and week-over-week signal.
                Under Key Risks: write 2 to 3 bullet points.
                Under Recommended Actions: write 2 to 3 bullet points.
                Mention exact counts where relevant. Do not wrap the response in quotes. Do not use markdown bold.
                Keep it under 260 words.

                Facts:
                %s
                """.formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(promptPayload));
    }

    public record SummaryResult(String provider, String summaryText, String fallbackReason) {
    }
}
