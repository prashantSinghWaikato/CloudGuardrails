package com.cloud.guardrails.aws;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;

public class AwsClientFactory {

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
}
