package com.cloud.guardrails.service;

import com.cloud.guardrails.aws.AwsIamRemediationService;
import com.cloud.guardrails.aws.AwsSecurityGroupRemediationService;
import com.cloud.guardrails.aws.AwsS3RemediationService;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemediationExecutor {

    private final AwsSecurityGroupRemediationService awsSecurityGroupRemediationService;
    private final AwsS3RemediationService awsS3RemediationService;
    private final AwsIamRemediationService awsIamRemediationService;

    public ExecutionResult execute(Remediation remediation) {
        Violation violation = remediation.getViolation();
        String action = normalizeAction(remediation.getAction());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", action);
        response.put("resourceId", violation != null ? violation.getResourceId() : null);
        response.put("timestamp", LocalDateTime.now().toString());

        return switch (action) {
            case "BLOCK_PUBLIC_S3_ACCESS" -> blockPublicS3Access(remediation, response);
            case "REVOKE_SECURITY_GROUP_RULE" -> revokeSecurityGroupRule(remediation, response);
            case "DISABLE_ACCESS_KEY" -> disableAccessKey(remediation, response);
            case "TAG_RESOURCE" -> success(response, "Simulated remediation tagging prepared");
            case "DEFAULT_ACTION", "AUTO_FIX" -> manual(response,
                    "No provider-specific executor configured; manual remediation required");
            default -> manual(response,
                    "Unsupported remediation action; manual remediation required");
        };
    }

    private ExecutionResult blockPublicS3Access(Remediation remediation, Map<String, Object> response) {
        awsS3RemediationService.blockPublicAccess(remediation);
        return success(response, "S3 public access blocked in AWS");
    }

    private ExecutionResult success(Map<String, Object> response, String message) {
        response.put("status", "success");
        response.put("message", message);
        return new ExecutionResult("EXECUTED", response);
    }

    private ExecutionResult revokeSecurityGroupRule(Remediation remediation, Map<String, Object> response) {
        awsSecurityGroupRemediationService.revokeIngress(remediation);
        return success(response, "Security group ingress revoked in AWS");
    }

    private ExecutionResult disableAccessKey(Remediation remediation, Map<String, Object> response) {
        awsIamRemediationService.disableAccessKey(remediation);
        return success(response, "IAM access key disabled in AWS");
    }

    private ExecutionResult manual(Map<String, Object> response, String message) {
        response.put("status", "manual_required");
        response.put("message", message);
        return new ExecutionResult("MANUAL_ACTION_REQUIRED", response);
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "DEFAULT_ACTION";
        }

        return action.trim().toUpperCase(Locale.ROOT);
    }

    public record ExecutionResult(String status, Map<String, Object> response) {
    }
}
