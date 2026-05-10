package com.cloud.guardrails.service;

import com.cloud.guardrails.entity.ReportRun;
import com.cloud.guardrails.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportEmailService {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.reports.email.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.reports.email.from:}")
    private String fromAddress;

    @Value("${app.reports.email.from-name:Cloud Guardrails}")
    private String fromName;

    public DeliveryResult sendExecutiveSummary(ReportRun run, List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return new DeliveryResult("SKIPPED", "No recipients configured");
        }

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            return new DeliveryResult("SKIPPED", "Brevo API key is not configured");
        }

        if (fromAddress != null && !fromAddress.isBlank()) {
            return sendViaBrevo(run, recipients);
        }

        return new DeliveryResult("SKIPPED", "Report sender email is not configured");
    }

    private DeliveryResult sendViaBrevo(ReportRun run, List<String> recipients) {
        RestClient restClient = restClientBuilder.baseUrl("https://api.brevo.com").build();

        Map<String, Object> payload = Map.of(
                "sender", Map.of(
                        "name", fromName,
                        "email", fromAddress
                ),
                "to", recipients.stream()
                        .map(email -> Map.of("email", email))
                        .toList(),
                "subject", "Cloud Guardrails Weekly Executive Summary",
                "textContent", buildBody(run)
        );

        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return new DeliveryResult("SENT", null);
        } catch (RestClientResponseException ex) {
            String errorBody = ex.getResponseBodyAsString();
            return new DeliveryResult("FAILED", errorBody == null || errorBody.isBlank() ? ex.getMessage() : errorBody);
        } catch (Exception ex) {
            return new DeliveryResult("FAILED", ex.getMessage());
        }
    }

    private String buildBody(ReportRun run) {
        return """
                Cloud Guardrails Executive Summary

                Period:
                %s to %s

                %s

                View the full report in Cloud Guardrails.
                Generated at: %s
                """.formatted(
                TimeUtils.formatUtc(run.getPeriodStart()),
                TimeUtils.formatUtc(run.getPeriodEnd()),
                run.getSummaryText() != null ? run.getSummaryText() : "No summary available.",
                TimeUtils.formatUtc(run.getCreatedAt())
        );
    }

    public record DeliveryResult(String status, String errorMessage) {
    }
}
