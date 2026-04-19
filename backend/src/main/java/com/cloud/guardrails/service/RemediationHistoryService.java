package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.RemediationExecutionResponse;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.entity.RemediationExecution;
import com.cloud.guardrails.repository.RemediationExecutionRepository;
import com.cloud.guardrails.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemediationHistoryService {

    private final RemediationExecutionRepository remediationExecutionRepository;

    @Transactional
    public RemediationExecution start(Remediation remediation,
                                      String triggerSource,
                                      String triggeredBy,
                                      Integer attemptNumber) {
        return remediationExecutionRepository.save(RemediationExecution.builder()
                .remediation(remediation)
                .action(remediation.getAction())
                .triggerSource(triggerSource)
                .triggeredBy(triggeredBy)
                .attemptNumber(attemptNumber)
                .targetAccountId(remediation.getTargetAccountId())
                .targetResourceId(remediation.getTargetResourceId())
                .status(remediation.getStatus())
                .startedAt(TimeUtils.utcNow())
                .build());
    }

    @Transactional
    public RemediationExecution complete(RemediationExecution execution,
                                         String status,
                                         String verificationStatus,
                                         String verificationMessage,
                                         Map<String, Object> response) {
        execution.setStatus(status);
        execution.setVerificationStatus(verificationStatus);
        execution.setVerificationMessage(verificationMessage);
        execution.setCompletedAt(TimeUtils.utcNow());
        execution.setResponse(response);
        return remediationExecutionRepository.save(execution);
    }

    @Transactional(readOnly = true)
    public List<RemediationExecutionResponse> getHistory(Long remediationId) {
        return remediationExecutionRepository.findByRemediationIdOrderByStartedAtDesc(remediationId).stream()
                .map(this::toResponse)
                .toList();
    }

    private RemediationExecutionResponse toResponse(RemediationExecution execution) {
        return RemediationExecutionResponse.builder()
                .id(execution.getId())
                .action(execution.getAction())
                .triggerSource(execution.getTriggerSource())
                .triggeredBy(execution.getTriggeredBy())
                .attemptNumber(execution.getAttemptNumber())
                .targetAccountId(execution.getTargetAccountId())
                .targetResourceId(execution.getTargetResourceId())
                .status(execution.getStatus())
                .verificationStatus(execution.getVerificationStatus())
                .verificationMessage(execution.getVerificationMessage())
                .startedAt(TimeUtils.formatUtc(execution.getStartedAt()))
                .completedAt(TimeUtils.formatUtc(execution.getCompletedAt()))
                .response(execution.getResponse())
                .build();
    }
}
