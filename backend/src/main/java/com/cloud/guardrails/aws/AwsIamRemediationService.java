package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AccessKeyMetadata;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.StatusType;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwsIamRemediationService {

    private final AwsClientFactory awsClientFactory;

    public void disableAccessKey(Remediation remediation) {
        AccessKeyTarget target = resolveAccessKeyTarget(remediation);
        CloudAccount account = remediation.getViolation().getCloudAccount();

        try (IamClient iamClient = awsClientFactory.createIamClient(account)) {
            iamClient.updateAccessKey(UpdateAccessKeyRequest.builder()
                    .userName(target.userName())
                    .accessKeyId(target.accessKeyId())
                    .status(StatusType.INACTIVE)
                    .build());
        }
    }

    public boolean isAccessKeyDisabled(Remediation remediation) {
        AccessKeyTarget target = resolveAccessKeyTarget(remediation);
        Violation violation = remediation.getViolation();
        if (violation == null || violation.getCloudAccount() == null) {
            throw new IllegalArgumentException("Cloud account is required for remediation");
        }

        CloudAccount account = violation.getCloudAccount();

        try (IamClient iamClient = awsClientFactory.createIamClient(account)) {
            return iamClient.listAccessKeys(ListAccessKeysRequest.builder()
                            .userName(target.userName())
                            .build())
                    .accessKeyMetadata()
                    .stream()
                    .filter(metadata -> target.accessKeyId().equals(metadata.accessKeyId()))
                    .map(AccessKeyMetadata::status)
                    .anyMatch(status -> StatusType.INACTIVE.equals(status));
        }
    }

    private AccessKeyTarget resolveAccessKeyTarget(Remediation remediation) {
        Violation violation = remediation.getViolation();
        if (violation == null || violation.getCloudAccount() == null) {
            throw new IllegalArgumentException("Cloud account is required for remediation");
        }

        Event event = violation.getEvent();
        Map<String, Object> payload = event != null ? event.getPayload() : Map.of();

        String userName = readString(payload, List.of("user", "userName", "requestParameters.userName"));
        String accessKeyId = readString(payload, List.of(
                "accessKeyId",
                "requestParameters.accessKeyId",
                "responseElements.accessKey.accessKeyId"
        ));

        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("IAM username is required for access key remediation");
        }
        if (accessKeyId == null || accessKeyId.isBlank()) {
            throw new IllegalArgumentException("IAM access key ID is required for remediation");
        }

        return new AccessKeyTarget(userName, accessKeyId);
    }

    private String readString(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value != null) {
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object readValue(Map<String, Object> payload, String path) {
        Object current = payload;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private record AccessKeyTarget(String userName, String accessKeyId) {
    }
}
