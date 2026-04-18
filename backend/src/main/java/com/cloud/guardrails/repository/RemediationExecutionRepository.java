package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.RemediationExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemediationExecutionRepository extends JpaRepository<RemediationExecution, Long> {
    List<RemediationExecution> findByRemediationIdOrderByStartedAtDesc(Long remediationId);
}
