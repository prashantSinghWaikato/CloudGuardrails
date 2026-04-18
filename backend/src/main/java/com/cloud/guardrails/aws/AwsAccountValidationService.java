package com.cloud.guardrails.aws;

import com.cloud.guardrails.dto.AccountValidationResponse;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.exception.ExternalValidationException;
import com.cloud.guardrails.security.CredentialCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

@Service
@RequiredArgsConstructor
public class AwsAccountValidationService {

    private final AwsClientFactory awsClientFactory;
    private final CredentialCryptoService credentialCryptoService;

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

    public AccountValidationResponse validateActivation(CloudAccount account,
                                                        String roleArn,
                                                        String externalId) {
        validateActivationFields(account, roleArn);

        CloudAccount candidate = CloudAccount.builder()
                .id(account.getId())
                .name(account.getName())
                .accountId(account.getAccountId())
                .provider(account.getProvider())
                .region(account.getRegion())
                .organization(account.getOrganization())
                .accessKey(account.getAccessKey())
                .secretKey(account.getSecretKey())
                .roleArn(roleArn)
                .externalId(credentialCryptoService.encrypt(externalId))
                .build();

        try (StsClient stsClient = awsClientFactory.createStsClient(candidate)) {
            GetCallerIdentityResponse identity = stsClient.getCallerIdentity();

            if (!account.getAccountId().equals(identity.account())) {
                throw new IllegalArgumentException("Activated role resolved to a different AWS account");
            }

            return AccountValidationResponse.builder()
                    .valid(true)
                    .provider("AWS")
                    .accountId(identity.account())
                    .arn(identity.arn())
                    .userId(identity.userId())
                    .message("AWS monitoring activation validated successfully")
                    .build();
        } catch (AwsServiceException | SdkClientException ex) {
            throw new ExternalValidationException("Unable to validate AWS activation role", ex);
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

    private void validateActivationFields(CloudAccount account, String roleArn) {
        if (account == null) {
            throw new IllegalArgumentException("Cloud account is required");
        }
        if (isBlank(account.getAccessKey()) || isBlank(account.getSecretKey()) || isBlank(account.getRegion())) {
            throw new IllegalArgumentException("Stored AWS credentials are required before activation");
        }
        if (isBlank(roleArn)) {
            throw new IllegalArgumentException("AWS role ARN is required for activation");
        }
    }
}
