package com.cloud.guardrails.aws;

import com.cloud.guardrails.dto.AccountValidationResponse;
import com.cloud.guardrails.exception.ExternalValidationException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

@Service
public class AwsAccountValidationService {

    public AccountValidationResponse validateAccount(String accountId,
                                                     String accessKey,
                                                     String secretKey,
                                                     String region) {

        validateRequiredFields(accountId, accessKey, secretKey, region);

        try (StsClient stsClient = AwsClientFactory.createStsClient(accessKey, secretKey, region)) {
            GetCallerIdentityResponse identity = stsClient.getCallerIdentity();

            if (!accountId.equals(identity.account())) {
                throw new IllegalArgumentException("AWS account ID does not match the supplied credentials");
            }

            return AccountValidationResponse.builder()
                    .valid(true)
                    .provider("AWS")
                    .accountId(identity.account())
                    .arn(identity.arn())
                    .userId(identity.userId())
                    .message("AWS account validated successfully")
                    .build();
        } catch (AwsServiceException | SdkClientException ex) {
            throw new ExternalValidationException("Unable to validate AWS account credentials", ex);
        }
    }

    private void validateRequiredFields(String accountId,
                                        String accessKey,
                                        String secretKey,
                                        String region) {
        if (isBlank(accountId) || isBlank(accessKey) || isBlank(secretKey) || isBlank(region)) {
            throw new IllegalArgumentException("AWS accountId, region, accessKey, and secretKey are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
