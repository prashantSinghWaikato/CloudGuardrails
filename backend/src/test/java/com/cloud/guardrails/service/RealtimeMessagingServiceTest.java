package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.NotificationResponse;
import com.cloud.guardrails.dto.RemediationResponse;
import com.cloud.guardrails.dto.ViolationResponse;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.entity.User;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMessagingServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResponseMapper responseMapper;

    @InjectMocks
    private RealtimeMessagingService realtimeMessagingService;

    @Test
    void sendsViolationOnlyToUsersWithMatchingAccountAccess() {
        Organization organization = Organization.builder().id(10L).name("Org").build();
        CloudAccount account = CloudAccount.builder().id(20L).accountId("123456789012").organization(organization).build();
        Rule rule = Rule.builder().id(30L).ruleName("SSH Open").build();
        Violation violation = Violation.builder()
                .id(40L)
                .organization(organization)
                .cloudAccount(account)
                .rule(rule)
                .resourceId("sg-123")
                .severity("CRITICAL")
                .status("OPEN")
                .build();

        User eligible = User.builder()
                .email("eligible@example.com")
                .organization(organization)
                .cloudAccounts(List.of(account))
                .build();

        User ineligible = User.builder()
                .email("blocked@example.com")
                .organization(organization)
                .cloudAccounts(List.of(CloudAccount.builder().id(99L).accountId("999").build()))
                .build();

        ViolationResponse response = ViolationResponse.builder()
                .id(40L)
                .ruleName("SSH Open")
                .resourceId("sg-123")
                .severity("CRITICAL")
                .status("OPEN")
                .accountId("123456789012")
                .build();

        when(userRepository.findWithCloudAccountsByOrganizationId(10L)).thenReturn(List.of(eligible, ineligible));
        when(responseMapper.toViolationResponse(violation)).thenReturn(response);

        realtimeMessagingService.sendViolation(violation);

        verify(messagingTemplate).convertAndSendToUser("eligible@example.com", "/queue/violations", response);
        verify(messagingTemplate, never()).convertAndSendToUser(eq("blocked@example.com"), eq("/queue/violations"), any());
    }

    @Test
    void sendsNotificationOnlyToTargetUser() {
        User user = User.builder().email("notify@example.com").build();
        NotificationResponse response = NotificationResponse.builder()
                .id(1L)
                .type("ACCOUNT_ADDED")
                .title("Cloud account added")
                .message("Primary Prod added")
                .read(false)
                .build();

        realtimeMessagingService.sendNotification(user, response);

        verify(messagingTemplate).convertAndSendToUser("notify@example.com", "/queue/notifications", response);
    }

    @Test
    void sendsRemediationOnlyToUsersWithMatchingAccountAccess() {
        Organization organization = Organization.builder().id(10L).name("Org").build();
        CloudAccount account = CloudAccount.builder().id(20L).accountId("123456789012").organization(organization).build();
        Violation violation = Violation.builder()
                .id(40L)
                .organization(organization)
                .cloudAccount(account)
                .resourceId("sg-123")
                .build();
        Remediation remediation = Remediation.builder()
                .id(50L)
                .violation(violation)
                .action("REVOKE_SECURITY_GROUP_RULE")
                .status("VERIFIED")
                .build();

        User eligible = User.builder()
                .email("eligible@example.com")
                .organization(organization)
                .cloudAccounts(List.of(account))
                .build();

        RemediationResponse response = RemediationResponse.builder()
                .id(50L)
                .action("REVOKE_SECURITY_GROUP_RULE")
                .status("VERIFIED")
                .build();

        when(userRepository.findWithCloudAccountsByOrganizationId(10L)).thenReturn(List.of(eligible));
        when(responseMapper.toRemediationResponse(remediation)).thenReturn(response);

        realtimeMessagingService.sendRemediation(remediation);

        verify(messagingTemplate).convertAndSendToUser("eligible@example.com", "/queue/remediations", response);
    }
}
