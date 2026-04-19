package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.AccountScanRun;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.repository.AccountScanRunRepository;
import com.cloud.guardrails.repository.CloudAccountRepository;
import com.cloud.guardrails.repository.EventRepository;
import com.cloud.guardrails.service.ViolationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.User;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketEncryptionRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyStatusRequest;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Type;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsIngestionService {

    private final EventRepository eventRepository;
    private final ViolationService violationService;
    private final ObjectMapper objectMapper;
    private final AwsClientFactory awsClientFactory;
    private final CloudAccountRepository cloudAccountRepository;
    private final AccountScanRunRepository accountScanRunRepository;

    public ScanSummary ingest(CloudAccount account) {
        validateAccount(account);
        AccountScanRun scanRun = startScanRun(account);
        ScanSummary summary = new ScanSummary();

        try (CloudTrailClient client = awsClientFactory.createCloudTrailClient(account)) {

            client.lookupEvents().events().forEach(e -> {
                try {
                    summary.eventsSeen++;
                    String externalEventId = resolveExternalEventId(e);

                    if (eventRepository.existsByCloudAccount_IdAndExternalEventId(account.getId(), externalEventId)) {
                        summary.duplicatesSkipped++;
                        return;
                    }

                    Event savedEvent = eventRepository.saveAndFlush(buildEvent(account, e));
                    summary.eventsIngested++;
                    summary.violationsCreated += violationService.evaluate(savedEvent);
                } catch (DataIntegrityViolationException duplicate) {
                    summary.duplicatesSkipped++;
                    log.debug("Skipping duplicate CloudTrail event {} for account {}",
                            e.eventId(),
                            account.getId());
                } catch (Exception ex) {
                    log.error("Failed to ingest CloudTrail event for account {}", account.getId(), ex);
                }
            });

            summary.postureFindingsCreated += runCurrentStateChecks(account);
            String message = buildSuccessMessage(summary);
            finishScanRun(scanRun, "SUCCESS", message, summary);
            markSync(account, "SUCCESS", message);
            return summary;
        } catch (Exception ex) {
            finishScanRun(scanRun, "FAILED", ex.getMessage(), summary);
            markSync(account, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private int runCurrentStateChecks(CloudAccount account) {
        int findings = 0;

        try (Ec2Client ec2Client = awsClientFactory.createEc2Client(account)) {
            findings += scanSecurityGroups(account, ec2Client);
            findings += scanInstancesWithoutTags(account, ec2Client);
        } catch (Exception ex) {
            log.error("EC2 posture checks failed for account {}", account.getId(), ex);
        }

        try (S3Client s3Client = awsClientFactory.createS3Client(account)) {
            findings += scanS3Buckets(account, s3Client);
        } catch (Exception ex) {
            log.error("S3 posture checks failed for account {}", account.getId(), ex);
        }

        try (IamClient iamClient = awsClientFactory.createIamClient(account)) {
            findings += scanIamUsers(account, iamClient);
        } catch (Exception ex) {
            log.error("IAM posture checks failed for account {}", account.getId(), ex);
        }

        return findings;
    }

    private int scanSecurityGroups(CloudAccount account, Ec2Client ec2Client) {
        int findings = 0;

        for (SecurityGroup group : ec2Client.describeSecurityGroupsPaginator(DescribeSecurityGroupsRequest.builder().build())
                .securityGroups()) {
            for (IpPermission permission : group.ipPermissions()) {
                for (String cidr : extractWorldCidrs(permission)) {
                    Integer port = permission.fromPort();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("port", port);
                    payload.put("fromPort", permission.fromPort());
                    payload.put("toPort", permission.toPort());
                    payload.put("cidr", cidr);
                    payload.put("protocol", permission.ipProtocol());
                    payload.put("sourceIp", "current-state-scan");
                    payload.put("scanMode", "STATE_CHECK");

                    findings += saveSyntheticEvent(
                            account,
                            "AuthorizeSecurityGroupIngress",
                            group.groupId(),
                            "posture:sg:%s:%s:%s:%s".formatted(
                                    group.groupId(),
                                    cidr,
                                    permission.fromPort(),
                                    permission.toPort()
                            ),
                            payload
                    );
                }
            }
        }

        return findings;
    }

    private int scanInstancesWithoutTags(CloudAccount account, Ec2Client ec2Client) {
        int findings = 0;

        for (Instance instance : ec2Client.describeInstancesPaginator(DescribeInstancesRequest.builder().build())
                .reservations()
                .stream()
                .flatMap(reservation -> reservation.instances().stream())
                .toList()) {
            if (instance.tags() != null && !instance.tags().isEmpty()) {
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("instanceType", instance.instanceTypeAsString());
            payload.put("hasTag", "false");
            payload.put("sourceIp", "current-state-scan");
            payload.put("scanMode", "STATE_CHECK");

            findings += saveSyntheticEvent(
                    account,
                    "RunInstances",
                    instance.instanceId(),
                    "posture:ec2:%s:no-tags".formatted(instance.instanceId()),
                    payload
            );
        }

        return findings;
    }

    private int scanS3Buckets(CloudAccount account, S3Client s3Client) {
        int findings = 0;

        for (Bucket bucket : s3Client.listBuckets().buckets()) {
            String bucketName = bucket.name();
            findings += scanS3PublicAccessBlock(account, s3Client, bucketName);
            findings += scanS3BucketAcl(account, s3Client, bucketName);
            findings += scanS3BucketPolicy(account, s3Client, bucketName);
            findings += scanS3BucketEncryption(account, s3Client, bucketName);
        }

        return findings;
    }

    private int scanS3PublicAccessBlock(CloudAccount account, S3Client s3Client, String bucketName) {
        try {
            var response = s3Client.getPublicAccessBlock(GetPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .build());

            var config = response.publicAccessBlockConfiguration();
            boolean enabled = config != null
                    && Boolean.TRUE.equals(config.blockPublicAcls())
                    && Boolean.TRUE.equals(config.ignorePublicAcls())
                    && Boolean.TRUE.equals(config.blockPublicPolicy())
                    && Boolean.TRUE.equals(config.restrictPublicBuckets());

            if (enabled) {
                return 0;
            }

            return saveSyntheticEvent(
                    account,
                    "PutBucketPublicAccessBlock",
                    bucketName,
                    "posture:s3:public-access-block:%s:disabled".formatted(bucketName),
                    buildPublicAccessBlockPayload(bucketName, config)
            );
        } catch (S3Exception ex) {
            String errorCode = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : null;
            if ("NoSuchPublicAccessBlockConfiguration".equals(errorCode)) {
                return saveSyntheticEvent(
                        account,
                        "PutBucketPublicAccessBlock",
                        bucketName,
                        "posture:s3:public-access-block:%s:missing".formatted(bucketName),
                        Map.of(
                                "bucketName", bucketName,
                                "publicAccessBlockEnabled", "false",
                                "blockPublicAcls", "false",
                                "ignorePublicAcls", "false",
                                "blockPublicPolicy", "false",
                                "restrictPublicBuckets", "false",
                                "sourceIp", "current-state-scan",
                                "scanMode", "STATE_CHECK"
                        )
                );
            }

            log.debug("Skipping S3 public access block posture check for bucket {}: {}", bucketName, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
            return 0;
        }
    }

    private int scanS3BucketAcl(CloudAccount account, S3Client s3Client, String bucketName) {
        try {
            var acl = s3Client.getBucketAcl(GetBucketAclRequest.builder().bucket(bucketName).build());
            boolean publicRead = acl.grants().stream().anyMatch(grant ->
                    grant.grantee() != null
                            && (Type.GROUP.equals(grant.grantee().type()) || "Group".equalsIgnoreCase(grant.grantee().typeAsString()))
                            && grant.grantee().uri() != null
                            && grant.grantee().uri().contains("AllUsers")
                            && List.of(Permission.READ, Permission.FULL_CONTROL).contains(grant.permission())
            );
            boolean publicWrite = acl.grants().stream().anyMatch(grant ->
                    grant.grantee() != null
                            && (Type.GROUP.equals(grant.grantee().type()) || "Group".equalsIgnoreCase(grant.grantee().typeAsString()))
                            && grant.grantee().uri() != null
                            && grant.grantee().uri().contains("AllUsers")
                            && List.of(Permission.WRITE, Permission.FULL_CONTROL).contains(grant.permission())
            );

            int findings = 0;
            if (publicRead) {
                findings += saveSyntheticEvent(
                        account,
                        "PutBucketAcl",
                        bucketName,
                        "posture:s3:acl:%s:public-read".formatted(bucketName),
                        Map.of(
                                "bucketName", bucketName,
                                "acl", "public-read",
                                "sourceIp", "current-state-scan",
                                "scanMode", "STATE_CHECK"
                        )
                );
            }
            if (publicWrite) {
                findings += saveSyntheticEvent(
                        account,
                        "PutBucketAcl",
                        bucketName,
                        "posture:s3:acl:%s:public-write".formatted(bucketName),
                        Map.of(
                                "bucketName", bucketName,
                                "acl", "public-write",
                                "sourceIp", "current-state-scan",
                                "scanMode", "STATE_CHECK"
                        )
                );
            }

            return findings;
        } catch (S3Exception ex) {
            log.debug("Skipping S3 ACL posture check for bucket {}: {}", bucketName, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
            return 0;
        }
    }

    private int scanS3BucketPolicy(CloudAccount account, S3Client s3Client, String bucketName) {
        try {
            var status = s3Client.getBucketPolicyStatus(GetBucketPolicyStatusRequest.builder()
                    .bucket(bucketName)
                    .build());

            if (status.policyStatus() == null || !Boolean.TRUE.equals(status.policyStatus().isPublic())) {
                return 0;
            }

            return saveSyntheticEvent(
                    account,
                    "PutBucketPolicy",
                    bucketName,
                    "posture:s3:policy:%s:public".formatted(bucketName),
                    Map.of(
                            "bucketName", bucketName,
                            "principal", "*",
                            "sourceIp", "current-state-scan",
                            "scanMode", "STATE_CHECK"
                    )
            );
        } catch (S3Exception ex) {
            log.debug("Skipping S3 policy posture check for bucket {}: {}", bucketName, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
            return 0;
        }
    }

    private int scanS3BucketEncryption(CloudAccount account, S3Client s3Client, String bucketName) {
        try {
            s3Client.getBucketEncryption(GetBucketEncryptionRequest.builder().bucket(bucketName).build());
            return 0;
        } catch (S3Exception ex) {
            String errorCode = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : null;
            if ("ServerSideEncryptionConfigurationNotFoundError".equals(errorCode)) {
                return saveSyntheticEvent(
                        account,
                        "PutBucketEncryption",
                        bucketName,
                        "posture:s3:encryption:%s:disabled".formatted(bucketName),
                        Map.of(
                                "bucketName", bucketName,
                                "encryption", "false",
                                "sourceIp", "current-state-scan",
                                "scanMode", "STATE_CHECK"
                        )
                );
            }
            log.debug("Skipping S3 encryption posture check for bucket {}: {}", bucketName, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage());
            return 0;
        }
    }

    private Map<String, Object> buildPublicAccessBlockPayload(String bucketName,
                                                              software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bucketName", bucketName);
        payload.put("publicAccessBlockEnabled", config != null
                && Boolean.TRUE.equals(config.blockPublicAcls())
                && Boolean.TRUE.equals(config.ignorePublicAcls())
                && Boolean.TRUE.equals(config.blockPublicPolicy())
                && Boolean.TRUE.equals(config.restrictPublicBuckets()) ? "true" : "false");
        payload.put("blockPublicAcls", config != null && Boolean.TRUE.equals(config.blockPublicAcls()) ? "true" : "false");
        payload.put("ignorePublicAcls", config != null && Boolean.TRUE.equals(config.ignorePublicAcls()) ? "true" : "false");
        payload.put("blockPublicPolicy", config != null && Boolean.TRUE.equals(config.blockPublicPolicy()) ? "true" : "false");
        payload.put("restrictPublicBuckets", config != null && Boolean.TRUE.equals(config.restrictPublicBuckets()) ? "true" : "false");
        payload.put("sourceIp", "current-state-scan");
        payload.put("scanMode", "STATE_CHECK");
        return payload;
    }

    private int scanIamUsers(CloudAccount account, IamClient iamClient) {
        int findings = 0;

        for (User user : iamClient.listUsersPaginator().users()) {
            for (AttachedPolicy policy : iamClient.listAttachedUserPoliciesPaginator(builder -> builder.userName(user.userName()))
                    .attachedPolicies()) {
                if (!"AdministratorAccess".equals(policy.policyName())) {
                    continue;
                }

                findings += saveSyntheticEvent(
                        account,
                        "AttachUserPolicy",
                        user.userName(),
                        "posture:iam:user:%s:AdministratorAccess".formatted(user.userName()),
                        Map.of(
                                "user", user.userName(),
                                "userName", user.userName(),
                                "policy", "AdministratorAccess",
                                "sourceIp", "current-state-scan",
                                "scanMode", "STATE_CHECK"
                        )
                );
            }
        }

        return findings;
    }

    private List<String> extractWorldCidrs(IpPermission permission) {
        List<String> cidrs = permission.ipRanges().stream()
                .map(range -> range.cidrIp())
                .filter(cidr -> cidr != null && cidr.equals("0.0.0.0/0"))
                .toList();

        if (!cidrs.isEmpty()) {
            return cidrs;
        }

        return permission.ipv6Ranges().stream()
                .map(range -> range.cidrIpv6())
                .filter(cidr -> cidr != null && cidr.equals("::/0"))
                .toList();
    }

    private int saveSyntheticEvent(CloudAccount account,
                                   String eventType,
                                   String resourceId,
                                   String externalEventId,
                                   Map<String, Object> payload) {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType(eventType)
                .resourceId(resourceId)
                .externalEventId(externalEventId + ":" + System.currentTimeMillis())
                .payload(payload)
                .organization(account.getOrganization())
                .cloudAccount(account)
                .timestamp(LocalDateTime.now())
                .build());

        return violationService.evaluate(event);
    }

    private AccountScanRun startScanRun(CloudAccount account) {
        return accountScanRunRepository.save(AccountScanRun.builder()
                .cloudAccount(account)
                .organization(account.getOrganization())
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .message("Scan started")
                .build());
    }

    private void finishScanRun(AccountScanRun scanRun, String status, String message, ScanSummary summary) {
        scanRun.setCompletedAt(LocalDateTime.now());
        scanRun.setStatus(status);
        scanRun.setMessage(truncateMessage(message));
        scanRun.setEventsSeen(summary.eventsSeen);
        scanRun.setEventsIngested(summary.eventsIngested);
        scanRun.setDuplicatesSkipped(summary.duplicatesSkipped);
        scanRun.setViolationsCreated(summary.violationsCreated);
        scanRun.setPostureFindingsCreated(summary.postureFindingsCreated);
        accountScanRunRepository.save(scanRun);
    }

    private String buildSuccessMessage(ScanSummary summary) {
        return "Scan completed: %d seen, %d ingested, %d duplicates skipped, %d event violations, %d posture findings"
                .formatted(
                        summary.eventsSeen,
                        summary.eventsIngested,
                        summary.duplicatesSkipped,
                        summary.violationsCreated,
                        summary.postureFindingsCreated
                );
    }

    private Event buildEvent(CloudAccount account,
                             software.amazon.awssdk.services.cloudtrail.model.Event event) {
        return Event.builder()
                .eventType(event.eventName())
                .resourceId(resolveResourceId(event))
                .externalEventId(resolveExternalEventId(event))
                .payload(extractPayload(event))
                .organization(account.getOrganization())
                .cloudAccount(account)
                .timestamp(resolveTimestamp(event))
                .build();
    }

    private Map<String, Object> extractPayload(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        String cloudTrailEvent = event.cloudTrailEvent();

        if (cloudTrailEvent == null || cloudTrailEvent.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> rawPayload = objectMapper.readValue(cloudTrailEvent, new TypeReference<>() {
            });
            return normalizePayload(event, rawPayload);
        } catch (Exception ex) {
            log.warn("Failed to parse CloudTrail payload for event {}", event.eventId(), ex);
            return Map.of("rawEvent", cloudTrailEvent);
        }
    }

    private String resolveResourceId(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        if (event.resources() != null && !event.resources().isEmpty()) {
            return event.resources().stream()
                    .map(resource -> resource.resourceName() != null
                            ? resource.resourceName()
                            : resource.resourceType())
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(event.eventId());
        }

        return event.eventId();
    }

    private String resolveExternalEventId(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        if (event.eventId() != null && !event.eventId().isBlank()) {
            return event.eventId();
        }

        return "%s:%s:%s".formatted(
                event.eventName(),
                resolveTimestamp(event),
                resolveResourceId(event)
        );
    }

    private LocalDateTime resolveTimestamp(software.amazon.awssdk.services.cloudtrail.model.Event event) {
        if (event.eventTime() != null) {
            return LocalDateTime.ofInstant(event.eventTime(), ZoneId.systemDefault());
        }

        return LocalDateTime.now();
    }

    private void validateAccount(CloudAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Cloud account is required");
        }

        if (isBlank(account.getAccessKey()) || isBlank(account.getSecretKey()) || isBlank(account.getRegion())) {
            throw new IllegalArgumentException(
                    "Cloud account is missing AWS credentials or region: " + account.getId()
            );
        }

        if (!Boolean.TRUE.equals(account.getMonitoringEnabled())) {
            throw new IllegalArgumentException("Cloud account monitoring is not activated: " + account.getId());
        }

        if (account.getOrganization() == null) {
            throw new IllegalArgumentException("Cloud account organization is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void markSync(CloudAccount account, String status, String message) {
        account.setLastSyncAt(LocalDateTime.now());
        account.setLastSyncStatus(status);
        account.setLastSyncMessage(truncateMessage(message));
        cloudAccountRepository.save(account);
    }

    private String truncateMessage(String message) {
        return message != null && message.length() > 1024
                ? message.substring(0, 1024)
                : message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizePayload(software.amazon.awssdk.services.cloudtrail.model.Event event,
                                                 Map<String, Object> rawPayload) {
        Map<String, Object> payload = new LinkedHashMap<>(rawPayload);

        payload.putIfAbsent("eventName", event.eventName());
        payload.putIfAbsent("sourceIp", readString(rawPayload, List.of("sourceIPAddress")));

        switch (event.eventName()) {
            case "AuthorizeSecurityGroupIngress" -> normalizeSecurityGroupIngress(payload, rawPayload);
            case "PutBucketAcl" -> normalizeBucketAcl(payload, rawPayload);
            case "PutBucketPolicy" -> normalizeBucketPolicy(payload, rawPayload);
            case "PutBucketEncryption" -> normalizeBucketEncryption(payload, rawPayload);
            case "PutBucketPublicAccessBlock" -> normalizePublicAccessBlock(payload, rawPayload);
            case "AttachUserPolicy" -> normalizeAttachUserPolicy(payload, rawPayload);
            case "ConsoleLogin" -> normalizeConsoleLogin(payload, rawPayload);
            case "RunInstances" -> normalizeRunInstances(payload, rawPayload);
            default -> {
                // Leave raw CloudTrail payload as-is for other event types.
            }
        }

        return payload;
    }

    private void normalizeSecurityGroupIngress(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("port", readInteger(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.fromPort",
                "requestParameters.ipPermissions.items.0.toPort"
        )));
        payload.putIfAbsent("fromPort", readInteger(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.fromPort"
        )));
        payload.putIfAbsent("toPort", readInteger(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.toPort"
        )));
        payload.putIfAbsent("cidr", readString(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.ipRanges.items.0.cidrIp"
        )));
        payload.putIfAbsent("protocol", readString(rawPayload, List.of(
                "requestParameters.ipPermissions.items.0.ipProtocol"
        )));
    }

    private void normalizeBucketAcl(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("acl", readString(rawPayload, List.of("requestParameters.x-amz-acl")));
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));
    }

    private void normalizeBucketPolicy(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));
        String policyDocument = readString(rawPayload, List.of("requestParameters.bucketPolicy"));
        if (policyDocument == null || policyDocument.isBlank()) {
            return;
        }

        try {
            Map<String, Object> policy = objectMapper.readValue(policyDocument, new TypeReference<>() {
            });
            payload.putIfAbsent("policyDocument", policy);
            Object principal = readValue(policy, "Statement.0.Principal");
            if (principal instanceof String principalText) {
                payload.putIfAbsent("principal", principalText);
            } else if (principal instanceof Map<?, ?> principalMap && principalMap.containsKey("AWS")) {
                payload.putIfAbsent("principal", String.valueOf(principalMap.get("AWS")));
            }
        } catch (Exception ex) {
            payload.putIfAbsent("policyDocument", policyDocument);
        }
    }

    private void normalizeBucketEncryption(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));
        String encryptionEnabled = readString(rawPayload, List.of(
                "requestParameters.serverSideEncryptionConfiguration.rules.0.applyServerSideEncryptionByDefault.sseAlgorithm"
        ));
        payload.putIfAbsent("encryption", encryptionEnabled == null || encryptionEnabled.isBlank() ? "false" : "true");
    }

    private void normalizePublicAccessBlock(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("bucketName", readString(rawPayload, List.of("requestParameters.bucketName")));

        String blockPublicAcls = readString(rawPayload, List.of(
                "requestParameters.PublicAccessBlockConfiguration.BlockPublicAcls"
        ));
        String ignorePublicAcls = readString(rawPayload, List.of(
                "requestParameters.PublicAccessBlockConfiguration.IgnorePublicAcls"
        ));
        String blockPublicPolicy = readString(rawPayload, List.of(
                "requestParameters.PublicAccessBlockConfiguration.BlockPublicPolicy"
        ));
        String restrictPublicBuckets = readString(rawPayload, List.of(
                "requestParameters.PublicAccessBlockConfiguration.RestrictPublicBuckets"
        ));

        payload.putIfAbsent("blockPublicAcls", normalizeBooleanString(blockPublicAcls));
        payload.putIfAbsent("ignorePublicAcls", normalizeBooleanString(ignorePublicAcls));
        payload.putIfAbsent("blockPublicPolicy", normalizeBooleanString(blockPublicPolicy));
        payload.putIfAbsent("restrictPublicBuckets", normalizeBooleanString(restrictPublicBuckets));

        boolean enabled = "true".equalsIgnoreCase(normalizeBooleanString(blockPublicAcls))
                && "true".equalsIgnoreCase(normalizeBooleanString(ignorePublicAcls))
                && "true".equalsIgnoreCase(normalizeBooleanString(blockPublicPolicy))
                && "true".equalsIgnoreCase(normalizeBooleanString(restrictPublicBuckets));

        payload.putIfAbsent("publicAccessBlockEnabled", enabled ? "true" : "false");
    }

    private void normalizeAttachUserPolicy(Map<String, Object> payload, Map<String, Object> rawPayload) {
        String policyArn = readString(rawPayload, List.of("requestParameters.policyArn"));
        if (policyArn != null && !policyArn.isBlank()) {
            int index = policyArn.lastIndexOf('/');
            payload.putIfAbsent("policy", index >= 0 ? policyArn.substring(index + 1) : policyArn);
        }
    }

    private void normalizeConsoleLogin(Map<String, Object> payload, Map<String, Object> rawPayload) {
        String arn = readString(rawPayload, List.of("userIdentity.arn"));
        if (arn != null && arn.contains(":root")) {
            payload.putIfAbsent("user", "root");
            return;
        }

        String userName = readString(rawPayload, List.of("userIdentity.userName"));
        if (userName != null && !userName.isBlank()) {
            payload.putIfAbsent("user", userName);
        }
    }

    private void normalizeRunInstances(Map<String, Object> payload, Map<String, Object> rawPayload) {
        payload.putIfAbsent("instanceType", readString(rawPayload, List.of(
                "requestParameters.instanceType"
        )));

        Object tagItems = readValue(rawPayload, "requestParameters.tagSpecificationSet.items.0.tags");
        boolean hasTags = tagItems instanceof List<?> list && !list.isEmpty();
        payload.putIfAbsent("hasTag", hasTags ? "true" : "false");
    }

    private String readString(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Integer readInteger(Map<String, Object> payload, List<String> keys) {
        for (String key : keys) {
            Object value = readValue(payload, key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    // Keep checking fallbacks.
                }
            }
        }
        return null;
    }

    private String normalizeBooleanString(String value) {
        if (value == null || value.isBlank()) {
            return "false";
        }

        return String.valueOf(Boolean.parseBoolean(value));
    }

    @SuppressWarnings("unchecked")
    private Object readValue(Map<String, Object> payload, String path) {
        Object current = payload;

        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
                continue;
            }

            if (current instanceof List<?> list) {
                int index;
                try {
                    index = Integer.parseInt(part);
                } catch (NumberFormatException ex) {
                    return null;
                }

                if (index < 0 || index >= list.size()) {
                    return null;
                }

                current = list.get(index);
                continue;
            }

            return null;
        }

        return current;
    }

    public static class ScanSummary {
        private int eventsSeen;
        private int eventsIngested;
        private int duplicatesSkipped;
        private int violationsCreated;
        private int postureFindingsCreated;

        public int getEventsSeen() {
            return eventsSeen;
        }

        public int getEventsIngested() {
            return eventsIngested;
        }

        public int getDuplicatesSkipped() {
            return duplicatesSkipped;
        }

        public int getViolationsCreated() {
            return violationsCreated;
        }

        public int getPostureFindingsCreated() {
            return postureFindingsCreated;
        }
    }
}
