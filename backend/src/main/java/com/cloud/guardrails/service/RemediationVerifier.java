package com.cloud.guardrails.service;

import com.cloud.guardrails.aws.AwsIamRemediationService;
import com.cloud.guardrails.aws.AwsSecurityGroupRemediationService;
import com.cloud.guardrails.aws.AwsS3RemediationService;
import com.cloud.guardrails.entity.Remediation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemediationVerifier {

    private final AwsSecurityGroupRemediationService awsSecurityGroupRemediationService;
    private final AwsS3RemediationService awsS3RemediationService;
    private final AwsIamRemediationService awsIamRemediationService;

    public VerificationResult verify(Remediation remediation) {
        String action = normalizeAction(remediation.getAction());

        try {
            return switch (action) {
                case "REVOKE_SECURITY_GROUP_RULE" -> result(
                        awsSecurityGroupRemediationService.isIngressRevoked(remediation),
                        "Security group ingress verification completed"
                );
                case "BLOCK_PUBLIC_S3_ACCESS" -> result(
                        awsS3RemediationService.isPublicAccessBlocked(remediation),
                        "S3 public access verification completed"
                );
                case "DISABLE_ACCESS_KEY" -> result(
                        awsIamRemediationService.isAccessKeyDisabled(remediation),
                        "IAM access key verification completed"
                );
                default -> skipped("No verifier configured for remediation action");
            };
        } catch (Exception ex) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("verified", false);
            details.put("verificationStatus", "ERROR");
            details.put("message", "Verification failed");
            details.put("error", ex.getMessage());
            return new VerificationResult(false, false, details);
        }
    }

    private VerificationResult result(boolean verified, String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("verified", verified);
        details.put("verificationStatus", verified ? "PASSED" : "FAILED");
        details.put("message", message);
        return new VerificationResult(true, verified, details);
    }

    private VerificationResult skipped(String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("verified", false);
        details.put("verificationStatus", "SKIPPED");
        details.put("message", message);
        return new VerificationResult(false, false, details);
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "DEFAULT_ACTION";
        }

        return action.trim().toUpperCase(Locale.ROOT);
    }

    public record VerificationResult(boolean verificationAttempted, boolean verified, Map<String, Object> details) {
    }
}
