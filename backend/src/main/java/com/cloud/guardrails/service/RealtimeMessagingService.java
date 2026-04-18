package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.NotificationResponse;
import com.cloud.guardrails.dto.RemediationResponse;
import com.cloud.guardrails.dto.ViolationResponse;
import com.cloud.guardrails.entity.Notification;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.User;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RealtimeMessagingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ResponseMapper responseMapper;

    public void sendViolation(Violation violation) {
        if (violation == null || violation.getOrganization() == null || violation.getCloudAccount() == null) {
            return;
        }

        ViolationResponse response = responseMapper.toViolationResponse(violation);
        for (User user : findEligibleUsers(violation.getOrganization().getId(), violation.getCloudAccount().getId())) {
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/violations", response);
        }
    }

    public void sendRemediation(Remediation remediation) {
        if (remediation == null
                || remediation.getViolation() == null
                || remediation.getViolation().getOrganization() == null
                || remediation.getViolation().getCloudAccount() == null) {
            return;
        }

        RemediationResponse response = responseMapper.toRemediationResponse(remediation);
        Long orgId = remediation.getViolation().getOrganization().getId();
        Long cloudAccountId = remediation.getViolation().getCloudAccount().getId();

        for (User user : findEligibleUsers(orgId, cloudAccountId)) {
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/remediations", response);
        }
    }

    public void sendNotification(User user, NotificationResponse response) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank() || response == null) {
            return;
        }

        messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications", response);
    }

    public void sendNotification(User user, Notification notification) {
        if (notification == null) {
            return;
        }

        sendNotification(user, responseMapper.toNotificationResponse(notification));
    }

    private List<User> findEligibleUsers(Long orgId, Long cloudAccountId) {
        return userRepository.findWithCloudAccountsByOrganizationId(orgId).stream()
                .filter(user -> user.getCloudAccounts() != null
                        && user.getCloudAccounts().stream().anyMatch(account -> account.getId().equals(cloudAccountId)))
                .toList();
    }
}
