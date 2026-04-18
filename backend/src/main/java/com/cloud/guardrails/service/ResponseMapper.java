package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.RemediationResponse;
import com.cloud.guardrails.dto.NotificationResponse;
import com.cloud.guardrails.dto.ViolationDetailResponse;
import com.cloud.guardrails.dto.ViolationResponse;
import com.cloud.guardrails.entity.Notification;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResponseMapper {

    private final RemediationHistoryService remediationHistoryService;

    public ViolationResponse toViolationResponse(Violation violation) {
        return ViolationResponse.builder()
                .id(violation.getId())
                .ruleName(violation.getRule() != null ? violation.getRule().getRuleName() : "Unknown")
                .resourceId(violation.getResourceId())
                .severity(violation.getSeverity())
                .status(violation.getStatus())
                .accountId(violation.getCloudAccount() != null
                        ? violation.getCloudAccount().getAccountId()
                        : "N/A")
                .build();
    }

    public RemediationResponse toRemediationResponse(Remediation remediation) {
        String ruleName = "Unknown";
        if (remediation.getViolation() != null && remediation.getViolation().getRule() != null) {
            ruleName = remediation.getViolation().getRule().getRuleName();
        }

        String resourceId = "N/A";
        if (remediation.getViolation() != null) {
            resourceId = remediation.getViolation().getResourceId();
        }

        return RemediationResponse.builder()
                .id(remediation.getId())
                .action(remediation.getAction())
                .status(remediation.getStatus())
                .attemptCount(remediation.getAttemptCount())
                .executedAt(remediation.getExecutedAt())
                .lastVerifiedAt(remediation.getLastVerifiedAt())
                .ruleName(ruleName)
                .resourceId(resourceId)
                .targetAccountId(remediation.getTargetAccountId())
                .targetResourceId(remediation.getTargetResourceId())
                .lastTriggeredBy(remediation.getLastTriggeredBy())
                .lastTriggerSource(remediation.getLastTriggerSource())
                .verificationStatus(remediation.getVerificationStatus())
                .verificationMessage(remediation.getVerificationMessage())
                .response(remediation.getResponse())
                .history(remediationHistoryService.getHistory(remediation.getId()))
                .build();
    }

    public ViolationDetailResponse toViolationDetailResponse(Violation violation, Remediation remediation) {
        return ViolationDetailResponse.builder()
                .id(violation.getId())
                .ruleName(violation.getRule() != null ? violation.getRule().getRuleName() : "Unknown")
                .severity(violation.getSeverity())
                .status(violation.getStatus())
                .resourceId(violation.getResourceId())
                .accountId(violation.getCloudAccount() != null ? violation.getCloudAccount().getAccountId() : "N/A")
                .provider(violation.getCloudAccount() != null ? violation.getCloudAccount().getProvider() : null)
                .region(violation.getCloudAccount() != null ? violation.getCloudAccount().getRegion() : null)
                .organizationName(violation.getOrganization() != null ? violation.getOrganization().getName() : null)
                .createdAt(violation.getCreatedAt())
                .updatedAt(violation.getUpdatedAt())
                .eventType(violation.getEvent() != null ? violation.getEvent().getEventType() : null)
                .externalEventId(violation.getEvent() != null ? violation.getEvent().getExternalEventId() : null)
                .eventTimestamp(violation.getEvent() != null ? violation.getEvent().getTimestamp() : null)
                .payload(violation.getEvent() != null ? violation.getEvent().getPayload() : null)
                .remediation(remediation != null ? toRemediationResponse(remediation) : null)
                .build();
    }

    public NotificationResponse toNotificationResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .severity(notification.getSeverity())
                .resourceId(notification.getResourceId())
                .read(notification.getRead())
                .createdAt(notification.getCreatedAt())
                .violationId(notification.getViolation() != null ? notification.getViolation().getId() : null)
                .remediationId(notification.getRemediation() != null ? notification.getRemediation().getId() : null)
                .build();
    }
}
