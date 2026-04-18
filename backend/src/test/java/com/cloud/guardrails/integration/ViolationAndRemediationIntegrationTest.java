package com.cloud.guardrails.integration;

import com.cloud.guardrails.aws.AwsIamRemediationService;
import com.cloud.guardrails.aws.AwsSecurityGroupRemediationService;
import com.cloud.guardrails.aws.AwsS3RemediationService;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Notification;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.RemediationExecution;
import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.repository.CloudAccountRepository;
import com.cloud.guardrails.repository.EventRepository;
import com.cloud.guardrails.repository.NotificationRepository;
import com.cloud.guardrails.repository.OrganizationRepository;
import com.cloud.guardrails.repository.RemediationRepository;
import com.cloud.guardrails.repository.RemediationExecutionRepository;
import com.cloud.guardrails.repository.RuleRepository;
import com.cloud.guardrails.repository.UserRepository;
import com.cloud.guardrails.repository.ViolationRepository;
import com.cloud.guardrails.security.UserContext;
import com.cloud.guardrails.service.EventConsumer;
import com.cloud.guardrails.service.RemediationService;
import com.cloud.guardrails.service.ViolationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ViolationAndRemediationIntegrationTest {

    @MockBean
    private AwsSecurityGroupRemediationService awsSecurityGroupRemediationService;

    @MockBean
    private AwsS3RemediationService awsS3RemediationService;

    @MockBean
    private AwsIamRemediationService awsIamRemediationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CloudAccountRepository cloudAccountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RemediationRepository remediationRepository;

    @Autowired
    private RemediationExecutionRepository remediationExecutionRepository;

    @Autowired
    private ViolationService violationService;

    @Autowired
    private RemediationService remediationService;

    @Autowired
    private EventConsumer eventConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUpVerificationDefaults() {
        when(awsSecurityGroupRemediationService.isIngressRevoked(any())).thenReturn(true);
        when(awsS3RemediationService.isPublicAccessBlocked(any())).thenReturn(true);
        when(awsIamRemediationService.isAccessKeyDisabled(any())).thenReturn(true);
    }

    @Test
    void duplicateExternalEventIsRejectedAtDatabaseLevel() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org One")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org One Account")
                .accountId("123456789012")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Event first = buildEvent(organization, account, "evt-1", "sg-123");
        Event second = buildEvent(organization, account, "evt-1", "sg-456");

        eventRepository.saveAndFlush(first);

        assertThrows(DataIntegrityViolationException.class,
                () -> eventRepository.saveAndFlush(second));
    }

    @Test
    void matchingEventCreatesViolationAndKnownRemediationExecutes() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Two")
                .createdAt(LocalDateTime.now())
                .build());

        userRepository.save(com.cloud.guardrails.entity.User.builder()
                .name("Org Two User")
                .email("org-two@example.com")
                .password("encoded")
                .role("ADMIN")
                .organization(organization)
                .cloudAccounts(new java.util.ArrayList<>())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Two Account")
                .accountId("210987654321")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("SSH Open to Internet");
        rule.setEnabled(true);
        rule.setAutoRemediation(true);
        rule.setRemediationAction("REVOKE_SECURITY_GROUP_RULE");
        ruleRepository.save(rule);

        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType("AuthorizeSecurityGroupIngress")
                .resourceId("sg-open-ssh")
                .externalEventId("evt-ssh-open")
                .payload(Map.of("port", 22, "cidr", "0.0.0.0/0"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        violationService.evaluate(event);

        Violation violation = violationRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals("FIXED", violation.getStatus());
        assertEquals("sg-open-ssh", violation.getResourceId());

        Remediation remediation = remediationRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals("VERIFIED", remediation.getStatus());
        assertNotNull(remediation.getExecutedAt());
        assertEquals("210987654321", remediation.getTargetAccountId());
        assertEquals("sg-open-ssh", remediation.getTargetResourceId());
        assertEquals("SYSTEM", remediation.getLastTriggeredBy());
        assertEquals("AUTO_RULE", remediation.getLastTriggerSource());
        assertEquals("PASSED", remediation.getVerificationStatus());
        assertNotNull(remediation.getLastVerifiedAt());
        assertEquals("success", remediation.getResponse().get("status"));
        assertEquals("REVOKE_SECURITY_GROUP_RULE", remediation.getResponse().get("action"));
        assertEquals("PASSED", ((Map<?, ?>) remediation.getResponse().get("verification")).get("verificationStatus"));
        verify(awsSecurityGroupRemediationService).revokeIngress(remediation);

        java.util.List<String> notificationTypes = notificationRepository.findAll().stream()
                .map(Notification::getType)
                .toList();
        org.junit.jupiter.api.Assertions.assertTrue(notificationTypes.contains("VIOLATION_CREATED"));
        org.junit.jupiter.api.Assertions.assertTrue(notificationTypes.contains("VIOLATION_FIXED"));

        java.util.List<RemediationExecution> history =
                remediationExecutionRepository.findByRemediationIdOrderByStartedAtDesc(remediation.getId());
        assertEquals(1, history.size());
        assertEquals("AUTO_RULE", history.get(0).getTriggerSource());
        assertEquals("SYSTEM", history.get(0).getTriggeredBy());
        assertEquals("VERIFIED", history.get(0).getStatus());
        assertEquals("PASSED", history.get(0).getVerificationStatus());
    }

    @Test
    void disabledRuleDoesNotCreateViolation() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Three")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Three Account")
                .accountId("999999999999")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("IAM User Created");
        rule.setEnabled(false);
        ruleRepository.save(rule);

        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType("CreateUser")
                .resourceId("iam-user-no-violation")
                .externalEventId("evt-disabled-rule")
                .payload(Map.of("user", "demo-user"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        violationService.evaluate(event);

        assertEquals(0, violationRepository.count());
    }

    @Test
    void consumerProcessesEventWithoutUserContextWhenAccountBelongsToOrganization() throws Exception {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Four")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Four Account")
                .accountId("555555555555")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        UserContext.clear();

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "CreateUser",
                "resourceId", "iam-user-from-consumer",
                "organizationId", organization.getId(),
                "cloudAccountId", account.getId(),
                "payload", Map.of("user", "demo-user")
        ));

        eventConsumer.consume(message);

        assertEquals(1, eventRepository.count());
        assertEquals(1, violationRepository.count());
    }

    @Test
    void consumerRejectsEventWithoutCloudAccountId() throws Exception {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Five")
                .createdAt(LocalDateTime.now())
                .build());

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "CreateUser",
                "resourceId", "missing-account",
                "organizationId", organization.getId(),
                "payload", Map.of("user", "demo-user")
        ));

        eventConsumer.consume(message);

        assertEquals(0, eventRepository.count());
        assertEquals(0, violationRepository.count());
    }

    @Test
    void consumerSkipsDuplicateExternalEventIdForSameCloudAccount() throws Exception {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Six")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Six Account")
                .accountId("777777777777")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        String message = objectMapper.writeValueAsString(Map.of(
                "externalEventId", "evt-duplicate",
                "eventType", "CreateUser",
                "resourceId", "dup-user",
                "organizationId", organization.getId(),
                "cloudAccountId", account.getId(),
                "payload", Map.of("user", "demo-user")
        ));

        eventConsumer.consume(message);
        eventConsumer.consume(message);

        assertEquals(1, eventRepository.count());
        assertEquals(1, violationRepository.count());
    }

    @Test
    void s3PublicAccessRemediationExecutesAgainstAwsService() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Seven")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Seven Account")
                .accountId("888888888888")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("S3 Public Read Access");
        rule.setEnabled(true);
        rule.setAutoRemediation(true);
        rule.setRemediationAction("BLOCK_PUBLIC_S3_ACCESS");
        ruleRepository.save(rule);

        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType("PutBucketAcl")
                .resourceId("demo-bucket")
                .externalEventId("evt-s3-public")
                .payload(Map.of("acl", "public-read", "bucketName", "demo-bucket"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        violationService.evaluate(event);

        Remediation remediation = remediationRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals("VERIFIED", remediation.getStatus());
        assertEquals("BLOCK_PUBLIC_S3_ACCESS", remediation.getResponse().get("action"));
        verify(awsS3RemediationService).blockPublicAccess(remediation);
    }

    @Test
    void accessKeyRemediationExecutesAgainstAwsService() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Eight")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Eight Account")
                .accountId("999999999998")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("Access Key Created");
        rule.setEnabled(true);
        rule.setAutoRemediation(true);
        rule.setRemediationAction("DISABLE_ACCESS_KEY");
        ruleRepository.save(rule);

        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType("CreateAccessKey")
                .resourceId("demo-user")
                .externalEventId("evt-access-key")
                .payload(Map.of(
                        "user", "demo-user",
                        "accessKeyId", "AKIATESTKEY123456789"
                ))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        violationService.evaluate(event);

        Remediation remediation = remediationRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals("VERIFIED", remediation.getStatus());
        assertEquals("DISABLE_ACCESS_KEY", remediation.getResponse().get("action"));
        verify(awsIamRemediationService).disableAccessKey(remediation);
    }

    @Test
    void failedVerificationKeepsViolationOpen() {
        when(awsSecurityGroupRemediationService.isIngressRevoked(any())).thenReturn(false);

        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Nine")
                .createdAt(LocalDateTime.now())
                .build());

        userRepository.save(com.cloud.guardrails.entity.User.builder()
                .name("Org Nine User")
                .email("org-nine@example.com")
                .password("encoded")
                .role("ADMIN")
                .organization(organization)
                .cloudAccounts(new java.util.ArrayList<>())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Nine Account")
                .accountId("999999999997")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("SSH Open to Internet");
        rule.setEnabled(true);
        rule.setAutoRemediation(true);
        rule.setRemediationAction("REVOKE_SECURITY_GROUP_RULE");
        ruleRepository.save(rule);

        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType("AuthorizeSecurityGroupIngress")
                .resourceId("sg-still-open")
                .externalEventId("evt-verification-failed")
                .payload(Map.of("port", 22, "cidr", "0.0.0.0/0"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        violationService.evaluate(event);

        Violation violation = violationRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals("OPEN", violation.getStatus());

        Remediation remediation = remediationRepository.findAll().stream().findFirst().orElseThrow();
        assertEquals("VERIFICATION_FAILED", remediation.getStatus());
        assertEquals("FAILED", ((Map<?, ?>) remediation.getResponse().get("verification")).get("verificationStatus"));
        org.junit.jupiter.api.Assertions.assertTrue(notificationRepository.findAll().stream()
                .map(Notification::getType)
                .anyMatch("VERIFICATION_FAILED"::equals));
    }

    @Test
    void retrySkipsAwsCallWhenResourceIsAlreadyVerified() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Org Ten")
                .createdAt(LocalDateTime.now())
                .build());

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Org Ten Account")
                .accountId("999999999996")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("SSH Open to Internet");
        rule.setEnabled(true);
        rule.setAutoRemediation(false);
        rule.setRemediationAction("REVOKE_SECURITY_GROUP_RULE");
        ruleRepository.save(rule);

        Event event = eventRepository.saveAndFlush(Event.builder()
                .eventType("AuthorizeSecurityGroupIngress")
                .resourceId("sg-already-fixed")
                .externalEventId("evt-retry-fixed")
                .payload(Map.of("port", 22, "cidr", "0.0.0.0/0"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        Violation violation = violationRepository.saveAndFlush(Violation.builder()
                .event(event)
                .rule(rule)
                .organization(organization)
                .cloudAccount(account)
                .resourceId("sg-already-fixed")
                .severity(rule.getSeverity())
                .status("FIXED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Remediation remediation = remediationRepository.saveAndFlush(Remediation.builder()
                .violation(violation)
                .action("REVOKE_SECURITY_GROUP_RULE")
                .status("FAILED")
                .attemptCount(1)
                .createdAt(LocalDateTime.now())
                .targetAccountId(account.getAccountId())
                .targetResourceId("sg-already-fixed")
                .build());

        UserContext.setEmail("reviewer@example.com");
        try {
            remediationService.retry(remediation);
        } finally {
            UserContext.clear();
        }

        Remediation updated = remediationRepository.findById(remediation.getId()).orElseThrow();
        assertEquals("VERIFIED", updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertEquals("reviewer@example.com", updated.getLastTriggeredBy());
        assertEquals("USER_RETRY", updated.getLastTriggerSource());
        assertEquals("PASSED", updated.getVerificationStatus());
        assertEquals("already_compliant", updated.getResponse().get("status"));
        verify(awsSecurityGroupRemediationService, never()).revokeIngress(any());

        java.util.List<RemediationExecution> history =
                remediationExecutionRepository.findByRemediationIdOrderByStartedAtDesc(updated.getId());
        assertEquals(1, history.size());
        assertEquals("USER_RETRY", history.get(0).getTriggerSource());
        assertEquals("reviewer@example.com", history.get(0).getTriggeredBy());
        assertEquals("VERIFIED", history.get(0).getStatus());
        assertEquals("PASSED", history.get(0).getVerificationStatus());
        assertEquals("already_compliant", history.get(0).getResponse().get("status"));
    }

    private Event buildEvent(Organization organization,
                             CloudAccount account,
                             String externalEventId,
                             String resourceId) {
        return Event.builder()
                .eventType("AuthorizeSecurityGroupIngress")
                .resourceId(resourceId)
                .externalEventId(externalEventId)
                .payload(Map.of("port", 22, "cidr", "0.0.0.0/0"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build();
    }
}
