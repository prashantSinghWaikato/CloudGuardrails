package com.cloud.guardrails.integration;

import com.cloud.guardrails.aws.AwsAccountValidationService;
import com.cloud.guardrails.aws.AwsIamRemediationService;
import com.cloud.guardrails.aws.AwsS3RemediationService;
import com.cloud.guardrails.aws.AwsSecurityGroupRemediationService;
import com.cloud.guardrails.dto.AccountValidationResponse;
import com.cloud.guardrails.dto.SignupRequest;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Notification;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.entity.User;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.repository.CloudAccountRepository;
import com.cloud.guardrails.repository.EventRepository;
import com.cloud.guardrails.repository.NotificationRepository;
import com.cloud.guardrails.repository.OrganizationRepository;
import com.cloud.guardrails.repository.RemediationRepository;
import com.cloud.guardrails.repository.RuleRepository;
import com.cloud.guardrails.repository.UserRepository;
import com.cloud.guardrails.repository.ViolationRepository;
import com.cloud.guardrails.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAndAccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CloudAccountRepository cloudAccountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private RemediationRepository remediationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AwsAccountValidationService awsAccountValidationService;

    @MockBean
    private AwsSecurityGroupRemediationService awsSecurityGroupRemediationService;

    @MockBean
    private AwsS3RemediationService awsS3RemediationService;

    @MockBean
    private AwsIamRemediationService awsIamRemediationService;

    @Test
    void accountsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void validateCreateAccountAndManageRulesWithJwt() throws Exception {
        when(awsAccountValidationService.validateAccount(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(true)
                        .provider("AWS")
                        .accountId("123456789012")
                        .arn("arn:aws:iam::123456789012:user/test")
                        .userId("AIDTEST")
                        .message("AWS account validated successfully")
                        .build());

        String token = signupAndGetToken();

        String accountPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Primary Prod",
                "accountId", "123456789012",
                "provider", "AWS",
                "region", "us-east-1",
                "accessKey", "AKIATEST",
                "secretKey", "secret"
        ));

        mockMvc.perform(post("/accounts/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.accountId").value("123456789012"));

        mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Primary Prod"))
                .andExpect(jsonPath("$.accountId").value("123456789012"))
                .andExpect(jsonPath("$.provider").value("AWS"));

        org.junit.jupiter.api.Assertions.assertTrue(
                notificationRepository.findAll().stream()
                        .anyMatch(notification ->
                                "ACCOUNT_ADDED".equals(notification.getType())
                                        && "Primary Prod".equals(notification.getResourceId()))
        );

        mockMvc.perform(get("/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Primary Prod"))
                .andExpect(jsonPath("$[0].accountId").value("123456789012"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0]").value("123456789012"));

        User signedUpUser = userRepository.findAll().stream().findFirst().orElseThrow();
        Organization org = signedUpUser.getOrganization();
        CloudAccount account = cloudAccountRepository.findByOrganizationId(org.getId()).stream().findFirst().orElseThrow();
        Rule rule = ruleRepository.findAll().stream().findFirst().orElseThrow();

        Long ruleId = rule.getId();

        mockMvc.perform(get("/rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));

        mockMvc.perform(patch("/rules/{id}/enabled", ruleId)
                        .header("Authorization", "Bearer " + token)
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/accounts/{id}", account.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(0));
    }

    @Test
    void accountQueriesUseCurrentOrganizationFromDatabaseNotTokenClaim() throws Exception {
        when(awsAccountValidationService.validateAccount(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(true)
                        .provider("AWS")
                        .accountId("123456789012")
                        .arn("arn:aws:iam::123456789012:user/test")
                        .userId("AIDTEST")
                        .message("AWS account validated successfully")
                        .build());

        String token = signupAndGetToken();

        String accountPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Original Org Account",
                "accountId", "123456789012",
                "provider", "AWS",
                "region", "us-east-1",
                "accessKey", "AKIATEST",
                "secretKey", "secret"
        ));

        mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountPayload))
                .andExpect(status().isOk());

        User user = userRepository.findAll().stream().findFirst().orElseThrow();
        Organization newOrganization = organizationRepository.save(Organization.builder()
                .name("Moved Org")
                .createdAt(LocalDateTime.now())
                .build());
        user.setOrganization(newOrganization);
        userRepository.save(user);

        mockMvc.perform(get("/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void violationDetailsAndRemediationActionsReturnRichPayloads() throws Exception {
        when(awsSecurityGroupRemediationService.isIngressRevoked(any())).thenReturn(true);

        SignupRequest request = new SignupRequest();
        request.setName("Details User");
        request.setEmail("details-" + UUID.randomUUID() + "@example.com");
        request.setPassword("password123");
        request.setOrganizationName("Details Org");

        String token = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        Organization organization = user.getOrganization();

        CloudAccount account = cloudAccountRepository.save(CloudAccount.builder()
                .name("Details Account")
                .accountId("123456789012")
                .provider("AWS")
                .region("us-east-1")
                .organization(organization)
                .createdAt(LocalDateTime.now())
                .accessKey("enc:v1:test")
                .secretKey("enc:v1:test")
                .build());

        user.setCloudAccounts(new java.util.ArrayList<>(java.util.List.of(account)));
        userRepository.save(user);
        token = jwtService.generateToken(user);

        Rule rule = ruleRepository.findByRuleNameIgnoreCase("SSH Open to Internet");
        rule.setEnabled(true);
        rule.setAutoRemediation(false);
        rule.setRemediationAction("REVOKE_SECURITY_GROUP_RULE");
        ruleRepository.save(rule);

        Event event = eventRepository.save(Event.builder()
                .eventType("AuthorizeSecurityGroupIngress")
                .resourceId("sg-123456")
                .externalEventId("evt-123")
                .payload(Map.of("port", 22, "cidr", "0.0.0.0/0"))
                .timestamp(LocalDateTime.now())
                .organization(organization)
                .cloudAccount(account)
                .build());

        Violation violation = violationRepository.save(Violation.builder()
                .event(event)
                .rule(rule)
                .organization(organization)
                .cloudAccount(account)
                .resourceId("sg-123456")
                .severity("CRITICAL")
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        Remediation remediation = remediationRepository.save(Remediation.builder()
                .violation(violation)
                .action("REVOKE_SECURITY_GROUP_RULE")
                .status("VERIFICATION_FAILED")
                .attemptCount(1)
                .createdAt(LocalDateTime.now())
                .response(Map.of(
                        "status", "success",
                        "verification", Map.of(
                                "verificationStatus", "FAILED",
                                "verified", false
                        )
                ))
                .build());

        mockMvc.perform(get("/violations/{id}", violation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(violation.getId()))
                .andExpect(jsonPath("$.organizationName").value("Details Org"))
                .andExpect(jsonPath("$.accountId").value("123456789012"))
                .andExpect(jsonPath("$.eventType").value("AuthorizeSecurityGroupIngress"))
                .andExpect(jsonPath("$.payload.port").value(22))
                .andExpect(jsonPath("$.remediation.id").value(remediation.getId()))
                .andExpect(jsonPath("$.remediation.status").value("VERIFICATION_FAILED"));

        mockMvc.perform(post("/remediations/{id}/reverify", remediation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(remediation.getId()))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.response.verification.verificationStatus").value("PASSED"));

        remediation.setStatus("FAILED");
        remediation.setResponse(Map.of("status", "failed", "error", "retry me"));
        remediationRepository.save(remediation);

        mockMvc.perform(post("/remediations/{id}/retry", remediation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(remediation.getId()))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.response.verification.verificationStatus").value("PASSED"));
    }

    @Test
    void notificationsEndpointsReturnUnreadCountsAndMarkRead() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setName("Notify User");
        request.setEmail("notify-" + UUID.randomUUID() + "@example.com");
        request.setPassword("password123");
        request.setOrganizationName("Notify Org");

        String token = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        Notification notification = notificationRepository.save(Notification.builder()
                .type("VIOLATION_CREATED")
                .title("New violation detected")
                .message("SSH open to internet requires attention")
                .severity("CRITICAL")
                .resourceId("sg-123")
                .read(false)
                .createdAt(LocalDateTime.now())
                .organization(user.getOrganization())
                .user(user)
                .build());

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("VIOLATION_CREATED"))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(put("/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId()))
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(put("/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"));
    }

    @Test
    void accountNameMustBeUniqueWithinOrganizationAndAccountIdCannotRepeat() throws Exception {
        when(awsAccountValidationService.validateAccount(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(true)
                        .provider("AWS")
                        .accountId("123456789012")
                        .arn("arn:aws:iam::123456789012:user/test")
                        .userId("AIDTEST")
                        .message("AWS account validated successfully")
                        .build());

        String token = signupAndGetToken();

        String firstPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Primary Prod",
                "accountId", "123456789012",
                "provider", "AWS",
                "region", "us-east-1",
                "accessKey", "AKIATEST",
                "secretKey", "secret"
        ));

        mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isOk());

        String duplicateNamePayload = objectMapper.writeValueAsString(Map.of(
                "name", "Primary Prod",
                "accountId", "210987654321",
                "provider", "AWS",
                "region", "us-east-1",
                "accessKey", "AKIATEST2",
                "secretKey", "secret"
        ));

        mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateNamePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account name already exists"));

        String duplicateAccountPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Secondary Prod",
                "accountId", "123456789012",
                "provider", "AWS",
                "region", "us-east-1",
                "accessKey", "AKIATEST3",
                "secretKey", "secret"
        ));

        mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateAccountPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("AWS account already exists for this organization"));
    }

    private String signupAndGetToken() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setName("Test User");
        request.setEmail("user-" + UUID.randomUUID() + "@example.com");
        request.setPassword("password123");
        request.setOrganizationName("Test Org");

        return mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
