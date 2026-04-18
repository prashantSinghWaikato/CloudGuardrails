package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwsS3RemediationService {

    private final AwsClientFactory awsClientFactory;

    public void blockPublicAccess(Remediation remediation) {
        String bucketName = resolveBucketName(remediation);
        CloudAccount account = remediation.getViolation().getCloudAccount();

        try (S3Client s3Client = awsClientFactory.createS3Client(account)) {
            s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                            .blockPublicAcls(true)
                            .ignorePublicAcls(true)
                            .blockPublicPolicy(true)
                            .restrictPublicBuckets(true)
                            .build())
                    .build());
        }
    }

    public boolean isPublicAccessBlocked(Remediation remediation) {
        String bucketName = resolveBucketName(remediation);
        Violation violation = remediation.getViolation();
        if (violation == null || violation.getCloudAccount() == null) {
            throw new IllegalArgumentException("Cloud account is required for remediation");
        }

        CloudAccount account = violation.getCloudAccount();

        try (S3Client s3Client = awsClientFactory.createS3Client(account)) {
            var config = s3Client.getPublicAccessBlock(GetPublicAccessBlockRequest.builder()
                            .bucket(bucketName)
                            .build())
                    .publicAccessBlockConfiguration();

            return config != null
                    && Boolean.TRUE.equals(config.blockPublicAcls())
                    && Boolean.TRUE.equals(config.ignorePublicAcls())
                    && Boolean.TRUE.equals(config.blockPublicPolicy())
                    && Boolean.TRUE.equals(config.restrictPublicBuckets());
        }
    }

    private String resolveBucketName(Remediation remediation) {
        Violation violation = remediation.getViolation();
        if (violation == null || violation.getCloudAccount() == null) {
            throw new IllegalArgumentException("Cloud account is required for remediation");
        }

        Event event = violation.getEvent();
        Map<String, Object> payload = event != null ? event.getPayload() : Map.of();

        String bucketName = firstNonBlank(
                violation.getResourceId(),
                readString(payload, List.of("bucketName", "requestParameters.bucketName"))
        );

        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("S3 bucket name is required for remediation");
        }

        return bucketName;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
