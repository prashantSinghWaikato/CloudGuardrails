package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.Remediation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RemediationRepository extends JpaRepository<Remediation, Long> {

    // ✅ ORG SAFE EXISTENCE CHECK
    boolean existsByViolationIdAndViolation_Organization_Id(
            Long violationId,
            Long organizationId
    );

    // ✅ ORG SAFE FETCH
    Optional<Remediation> findTopByViolationIdAndViolation_Organization_IdOrderByCreatedAtDescIdDesc(
            Long violationId,
            Long organizationId
    );

    List<Remediation> findByViolation_Organization_IdAndViolation_CloudAccount_IdIn(
            Long orgId,
            List<Long> accountIds
    );
}
