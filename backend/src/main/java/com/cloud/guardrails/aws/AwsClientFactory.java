package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.security.CredentialCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AwsClientFactory {

    private final CredentialCryptoService credentialCryptoService;

    public CloudTrailClient createCloudTrailClient(CloudAccount account) {
        return CloudTrailClient.builder()
                .region(resolveRegion(account.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(account))
                .build();
    }

    public StsClient createStsClient(CloudAccount account) {
        return StsClient.builder()
                .region(resolveRegion(account.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(account))
                .build();
    }

    public Ec2Client createEc2Client(CloudAccount account) {
        return Ec2Client.builder()
                .region(resolveRegion(account.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(account))
                .build();
    }

    public S3Client createS3Client(CloudAccount account) {
        return S3Client.builder()
                .region(resolveRegion(account.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(account))
                .build();
    }

    public IamClient createIamClient(CloudAccount account) {
        return IamClient.builder()
                .region(resolveRegion(account.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(account))
                .build();
    }

    public static CloudTrailClient create(String accessKey, String secretKey, String region) {

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        return CloudTrailClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();
    }

    public static StsClient createStsClient(String accessKey, String secretKey, String region) {

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        return StsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();
    }

    public static Ec2Client createEc2Client(String accessKey, String secretKey, String region) {

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        return Ec2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();
    }

    public static S3Client createS3Client(String accessKey, String secretKey, String region) {

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();
    }

    public static IamClient createIamClient(String accessKey, String secretKey, String region) {

        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);

        return IamClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider resolveCredentialsProvider(
            CloudAccount account
    ) {
        String accessKey = credentialCryptoService.decrypt(account.getAccessKey());
        String secretKey = credentialCryptoService.decrypt(account.getSecretKey());
        var baseProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        if (account.getRoleArn() == null || account.getRoleArn().isBlank()) {
            return baseProvider;
        }

        StsClient stsClient = StsClient.builder()
                .region(resolveRegion(account.getRegion()))
                .credentialsProvider(baseProvider)
                .build();

        AssumeRoleRequest.Builder assumeRoleRequest = AssumeRoleRequest.builder()
                .roleArn(account.getRoleArn())
                .roleSessionName("guardrails-" + UUID.randomUUID());

        if (account.getExternalId() != null && !account.getExternalId().isBlank()) {
            assumeRoleRequest.externalId(
                    credentialCryptoService.decrypt(account.getExternalId())
            );
        }

        return StsAssumeRoleCredentialsProvider.builder()
                .stsClient(stsClient)
                .refreshRequest(assumeRoleRequest.build())
                .build();
    }

    private Region resolveRegion(String region) {
        return Region.of(region);
    }
}
