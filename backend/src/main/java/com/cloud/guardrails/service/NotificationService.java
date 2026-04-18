package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.NotificationResponse;
import com.cloud.guardrails.entity.Notification;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.User;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.NotificationRepository;
import com.cloud.guardrails.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RealtimeMessagingService realtimeMessagingService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(boolean unreadOnly) {
        User user = getCurrentUser();
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId())
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return notifications.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByUserIdAndReadFalse(getCurrentUser().getId());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        User user = getCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        notification.setRead(true);
        notificationRepository.save(notification);
        NotificationResponse response = toResponse(notification);
        realtimeMessagingService.sendNotification(user, response);
        return response;
    }

    @Transactional
    public void markAllRead() {
        User user = getCurrentUser();
        List<Notification> notifications = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        notifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void notifyViolationCreated(Violation violation) {
        createForOrganizationUsers(
                violation.getOrganization(),
                "VIOLATION_CREATED",
                "New violation detected",
                "%s on %s requires attention".formatted(
                        violation.getRule() != null ? violation.getRule().getRuleName() : "Violation",
                        violation.getResourceId()
                ),
                violation.getSeverity(),
                violation.getResourceId(),
                violation,
                null
        );
    }

    @Transactional
    public void notifyAccountAdded(com.cloud.guardrails.entity.CloudAccount account) {
        createForOrganizationUsers(
                account.getOrganization(),
                "ACCOUNT_ADDED",
                "Cloud account added",
                "%s (%s) was added for %s in %s".formatted(
                        account.getName(),
                        account.getAccountId(),
                        account.getProvider(),
                        account.getRegion()
                ),
                "INFO",
                account.getName(),
                null,
                null
        );
    }

    @Transactional
    public void notifyViolationFixed(Violation violation, Remediation remediation) {
        createForOrganizationUsers(
                violation.getOrganization(),
                "VIOLATION_FIXED",
                "Violation fixed",
                "%s on %s has been verified as fixed".formatted(
                        violation.getRule() != null ? violation.getRule().getRuleName() : "Violation",
                        violation.getResourceId()
                ),
                violation.getSeverity(),
                violation.getResourceId(),
                violation,
                remediation
        );
    }

    @Transactional
    public void notifyRemediationFailed(Remediation remediation, String message) {
        Violation violation = remediation.getViolation();
        createForOrganizationUsers(
                violation.getOrganization(),
                "REMEDIATION_FAILED",
                "Remediation failed",
                message,
                violation.getSeverity(),
                violation.getResourceId(),
                violation,
                remediation
        );
    }

    @Transactional
    public void notifyVerificationFailed(Remediation remediation, String message) {
        Violation violation = remediation.getViolation();
        createForOrganizationUsers(
                violation.getOrganization(),
                "VERIFICATION_FAILED",
                "Verification failed",
                message,
                violation.getSeverity(),
                violation.getResourceId(),
                violation,
                remediation
        );
    }

    private void createForOrganizationUsers(Organization organization,
                                            String type,
                                            String title,
                                            String message,
                                            String severity,
                                            String resourceId,
                                            Violation violation,
                                            Remediation remediation) {
        if (organization == null) {
            return;
        }

        List<User> users = userRepository.findByOrganizationId(organization.getId());
        for (User user : users) {
            Notification notification = notificationRepository.save(Notification.builder()
                    .type(type)
                    .title(title)
                    .message(message)
                    .severity(severity)
                    .resourceId(resourceId)
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .organization(organization)
                    .user(user)
                    .violation(violation)
                    .remediation(remediation)
                    .build());

            realtimeMessagingService.sendNotification(user, notification);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .severity(notification.getSeverity())
                .resourceId(notification.getResourceId())
                .read(notification.getRead())
                .createdAt(notification.getCreatedAt())
                .violationId(notification.getViolation() != null ? notification.getViolation().getId() : null)
                .remediationId(notification.getRemediation() != null ? notification.getRemediation().getId() : null)
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
