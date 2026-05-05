package com.cloud.guardrails.service;

import com.cloud.guardrails.entity.ReportRun;
import com.cloud.guardrails.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.reports.email.from:}")
    private String fromAddress;

    public DeliveryResult sendExecutiveSummary(ReportRun run, List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return new DeliveryResult("SKIPPED", "No recipients configured");
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return new DeliveryResult("SKIPPED", "Mail sender is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject("Cloud Guardrails Weekly Executive Summary");
        message.setText(buildBody(run));

        try {
            sender.send(message);
            return new DeliveryResult("SENT", null);
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
                """.formatted(
                TimeUtils.formatUtc(run.getPeriodStart()),
                TimeUtils.formatUtc(run.getPeriodEnd()),
                run.getSummaryText() != null ? run.getSummaryText() : "No summary available."
        );
    }

    public record DeliveryResult(String status, String errorMessage) {
    }
}
