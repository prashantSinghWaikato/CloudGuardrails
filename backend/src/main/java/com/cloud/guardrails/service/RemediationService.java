package com.cloud.guardrails.service;

import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.RemediationExecution;
import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.exception.ForbiddenException;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.RemediationRepository;
import com.cloud.guardrails.repository.ViolationRepository;
import com.cloud.guardrails.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationService {

    private final RemediationRepository remediationRepository;
    private final ViolationRepository violationRepository;
    private final RemediationExecutor remediationExecutor;
    private final RemediationVerifier remediationVerifier;
    private final ResponseMapper responseMapper;
    private final NotificationService notificationService;
    private final RealtimeMessagingService realtimeMessagingService;
    private final RemediationHistoryService remediationHistoryService;

    // ================= CREATE =================

    @Transactional
    public Remediation createRemediation(Violation violation, Rule rule) {

        Long orgId = violation.getOrganization().getId();

        var existing = remediationRepository
                .findByViolationIdAndViolation_Organization_Id(
                        violation.getId(),
                        orgId
                );

        if (existing.isPresent()) {
            return existing.get();
        }

        if (rule == null) {
            throw new NotFoundException("Rule missing for violation");
        }

        String action = rule.getRemediationAction() != null
                ? rule.getRemediationAction()
                : "AUTO_FIX";

        Remediation remediation = Remediation.builder()
                .violation(violation)
                .action(action)
                .status(Boolean.TRUE.equals(rule.getAutoRemediation()) ? "EXECUTING" : "PENDING")
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .targetAccountId(violation.getCloudAccount() != null ? violation.getCloudAccount().getAccountId() : null)
                .targetResourceId(violation.getResourceId())
                .lastTriggeredBy(Boolean.TRUE.equals(rule.getAutoRemediation()) ? "SYSTEM" : null)
                .lastTriggerSource(Boolean.TRUE.equals(rule.getAutoRemediation()) ? "AUTO_RULE" : null)
                .build();

        remediation = remediationRepository.save(remediation);

        realtimeMessagingService.sendRemediation(remediation);

        if (Boolean.TRUE.equals(rule.getAutoRemediation())) {
            execute(remediation, "AUTO_RULE", "SYSTEM");
        }

        return remediation;
    }

    // ================= EXECUTE =================

    @Transactional
    public void execute(Remediation remediation, String triggerSource, String actorEmail) {

        populateAuditTargets(remediation);
        String resolvedActor = resolveActor(actorEmail);
        String resolvedTriggerSource = normalizeTriggerSource(triggerSource);
        remediation.setLastTriggeredBy(resolvedActor);
        remediation.setLastTriggerSource(resolvedTriggerSource);
        RemediationExecution execution = null;

        try {
            Map<String, Object> response = remediation.getResponse() != null
                    ? new HashMap<>(remediation.getResponse())
                    : new HashMap<>();

            RemediationVerifier.VerificationResult preVerification = shouldPreVerifyBeforeExecution(remediation)
                    ? remediationVerifier.verify(remediation)
                    : null;
            if (preVerification != null && preVerification.verificationAttempted() && preVerification.verified()) {
                execution = remediationHistoryService.start(
                        remediation,
                        resolvedTriggerSource,
                        resolvedActor,
                        currentAttemptNumber(remediation)
                );
                response.put("status", "already_compliant");
                response.put("action", normalizeAction(remediation.getAction()));
                response.put("resourceId", remediation.getTargetResourceId());
                response.put("timestamp", LocalDateTime.now().toString());
                response.put("message", "Resource already compliant; remediation execution skipped");
                response.put("verification", preVerification.details());

                remediation.setResponse(response);
                remediation.setStatus("VERIFIED");
                updateVerificationFields(remediation, preVerification);
                markViolationFixed(remediation.getViolation());
                notificationService.notifyViolationFixed(remediation.getViolation(), remediation);
                remediationHistoryService.complete(
                        execution,
                        remediation.getStatus(),
                        remediation.getVerificationStatus(),
                        remediation.getVerificationMessage(),
                        response
                );
                remediationRepository.save(remediation);
                realtimeMessagingService.sendRemediation(remediation);
                return;
            }

            remediation.setStatus("EXECUTING");
            remediation.setAttemptCount(nextAttemptNumber(remediation));
            remediationRepository.save(remediation);
            execution = remediationHistoryService.start(
                    remediation,
                    resolvedTriggerSource,
                    resolvedActor,
                    remediation.getAttemptCount()
            );

            RemediationExecutor.ExecutionResult result = remediationExecutor.execute(remediation);
            remediation.setStatus(result.status());
            remediation.setExecutedAt(LocalDateTime.now());
            response = new HashMap<>(result.response());

            if ("EXECUTED".equals(result.status())) {
                applyVerificationOutcome(remediation, response);
            } else {
                keepViolationOpen(remediation.getViolation());
            }

            remediation.setResponse(response);
            remediationHistoryService.complete(
                    execution,
                    remediation.getStatus(),
                    remediation.getVerificationStatus(),
                    remediation.getVerificationMessage(),
                    response
            );

        } catch (Exception e) {

            remediation.setStatus("FAILED");

            Map<String, Object> response = new HashMap<>();
            response.put("status", "failed");
            response.put("error", e.getMessage());

            remediation.setResponse(response);
            remediation.setVerificationStatus("ERROR");
            remediation.setVerificationMessage(e.getMessage());
            keepViolationOpen(remediation.getViolation());
            notificationService.notifyRemediationFailed(remediation, e.getMessage());

            if (execution == null) {
                execution = remediationHistoryService.start(
                        remediation,
                        resolvedTriggerSource,
                        resolvedActor,
                        currentAttemptNumber(remediation)
                );
            }
            remediationHistoryService.complete(
                    execution,
                    remediation.getStatus(),
                    remediation.getVerificationStatus(),
                    remediation.getVerificationMessage(),
                    response
            );
        }

        remediationRepository.save(remediation);

        realtimeMessagingService.sendRemediation(remediation);
    }

    @Transactional(readOnly = true)
    public Remediation findForViolation(Violation violation) {
        if (violation == null || violation.getOrganization() == null) {
            return null;
        }

        return remediationRepository
                .findByViolationIdAndViolation_Organization_Id(violation.getId(), violation.getOrganization().getId())
                .orElse(null);
    }

    @Transactional
    public Remediation retry(Remediation remediation) {
        if ("EXECUTING".equalsIgnoreCase(remediation.getStatus())) {
            throw new IllegalStateException("Remediation is already executing");
        }

        if ("VERIFIED".equalsIgnoreCase(remediation.getStatus())) {
            return remediation;
        }

        remediation.setStatus("PENDING");
        remediationRepository.save(remediation);
        execute(remediation, "USER_RETRY", UserContext.getEmail());
        return remediation;
    }

    @Transactional
    public Remediation reverify(Remediation remediation) {
        Map<String, Object> response = remediation.getResponse() != null
                ? new HashMap<>(remediation.getResponse())
                : new HashMap<>();
        populateAuditTargets(remediation);

        String actor = resolveActor(UserContext.getEmail());
        RemediationExecution execution = remediationHistoryService.start(
                remediation,
                "USER_REVERIFY",
                actor,
                currentAttemptNumber(remediation)
        );

        RemediationVerifier.VerificationResult verification = remediationVerifier.verify(remediation);
        applyVerificationOutcome(remediation, response, verification);
        remediation.setResponse(response);
        remediationRepository.save(remediation);
        remediationHistoryService.complete(
                execution,
                remediation.getStatus(),
                remediation.getVerificationStatus(),
                remediation.getVerificationMessage(),
                response
        );
        realtimeMessagingService.sendRemediation(remediation);
        return remediation;
    }

    private void applyVerificationOutcome(Remediation remediation, Map<String, Object> response) {
        applyVerificationOutcome(remediation, response, remediationVerifier.verify(remediation));
    }

    private void applyVerificationOutcome(Remediation remediation,
                                          Map<String, Object> response,
                                          RemediationVerifier.VerificationResult verification) {
        response.put("verification", verification.details());
        updateVerificationFields(remediation, verification);

        if (verification.verified()) {
            remediation.setStatus("VERIFIED");
            markViolationFixed(remediation.getViolation());
            notificationService.notifyViolationFixed(remediation.getViolation(), remediation);
        } else if (verification.verificationAttempted()) {
            remediation.setStatus("VERIFICATION_FAILED");
            keepViolationOpen(remediation.getViolation());
            notificationService.notifyVerificationFailed(remediation,
                    verification.details().get("message") != null
                            ? String.valueOf(verification.details().get("message"))
                            : "Remediation execution completed but verification failed");
        } else {
            keepViolationOpen(remediation.getViolation());
        }
    }

    private void markViolationFixed(Violation violation) {
        if (violation == null) {
            return;
        }

        violation.setStatus("FIXED");
        violation.setUpdatedAt(LocalDateTime.now());
        violationRepository.save(violation);
        realtimeMessagingService.sendViolation(violation);
    }

    private void keepViolationOpen(Violation violation) {
        if (violation == null) {
            return;
        }

        violation.setStatus("OPEN");
        violation.setUpdatedAt(LocalDateTime.now());
        violationRepository.save(violation);
        realtimeMessagingService.sendViolation(violation);
    }

    // ================= APPROVAL =================

    @Transactional
    public void approveAndExecute(Remediation remediation) {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (!remediation.getViolation().getOrganization().getId().equals(orgId) ||
                !accountIds.contains(remediation.getViolation().getCloudAccount().getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        remediation.setStatus("APPROVED");
        remediationRepository.save(remediation);

        execute(remediation, "USER_APPROVAL", UserContext.getEmail());
    }

    // ================= API METHODS =================

    public List<Remediation> getAll() {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (orgId == null || accountIds == null || accountIds.isEmpty()) {
            throw new ForbiddenException("Unauthorized");
        }

        return remediationRepository
                .findByViolation_Organization_IdAndViolation_CloudAccount_IdIn(
                        orgId,
                        accountIds
                );
    }

    public Remediation getById(Long id) {

        Remediation r = remediationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Remediation not found"));

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (!r.getViolation().getOrganization().getId().equals(orgId) ||
                !accountIds.contains(r.getViolation().getCloudAccount().getId())) {
            throw new ForbiddenException("Unauthorized");
        }

        return r;
    }

    private void populateAuditTargets(Remediation remediation) {
        if (remediation.getViolation() == null) {
            return;
        }

        remediation.setTargetResourceId(remediation.getViolation().getResourceId());

        if (remediation.getViolation().getCloudAccount() != null) {
            remediation.setTargetAccountId(remediation.getViolation().getCloudAccount().getAccountId());
        }
    }

    private void updateVerificationFields(Remediation remediation,
                                          RemediationVerifier.VerificationResult verification) {
        remediation.setLastVerifiedAt(LocalDateTime.now());
        remediation.setVerificationStatus(String.valueOf(
                verification.details().getOrDefault("verificationStatus", "UNKNOWN")));
        remediation.setVerificationMessage(String.valueOf(
                verification.details().getOrDefault("message", "Verification completed")));
    }

    private String resolveActor(String actorEmail) {
        return actorEmail != null && !actorEmail.isBlank() ? actorEmail : "SYSTEM";
    }

    private String normalizeTriggerSource(String triggerSource) {
        if (triggerSource == null || triggerSource.isBlank()) {
            return "SYSTEM";
        }

        return triggerSource.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "DEFAULT_ACTION";
        }

        return action.trim().toUpperCase(Locale.ROOT);
    }

    private boolean shouldPreVerifyBeforeExecution(Remediation remediation) {
        Integer attemptCount = remediation.getAttemptCount();
        boolean hasPreviousAttempt = attemptCount != null && attemptCount > 0;
        boolean violationAlreadyFixed = remediation.getViolation() != null
                && "FIXED".equalsIgnoreCase(remediation.getViolation().getStatus());
        boolean alreadyVerified = "VERIFIED".equalsIgnoreCase(remediation.getStatus());

        return hasPreviousAttempt || violationAlreadyFixed || alreadyVerified;
    }

    private int nextAttemptNumber(Remediation remediation) {
        return currentAttemptNumber(remediation) + 1;
    }

    private int currentAttemptNumber(Remediation remediation) {
        return remediation.getAttemptCount() != null ? remediation.getAttemptCount() : 0;
    }
}
