package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ViolationRepository extends JpaRepository<Violation, Long> {

    // ================= BASE (ORG + ACCOUNT) =================

    Page<Violation> findByOrganizationIdAndCloudAccount_IdIn(
            Long orgId,
            List<Long> accountIds,
            Pageable pageable
    );

    // ================= FILTER =================

    Page<Violation> findBySeverityAndOrganizationIdAndCloudAccount_IdIn(
            String severity,
            Long orgId,
            List<Long> accountIds,
            Pageable pageable
    );

    Page<Violation> findByStatusAndOrganizationIdAndCloudAccount_IdIn(
            String status,
            Long orgId,
            List<Long> accountIds,
            Pageable pageable
    );

    Page<Violation> findBySeverityAndStatusAndOrganizationIdAndCloudAccount_IdIn(
            String severity,
            String status,
            Long orgId,
            List<Long> accountIds,
            Pageable pageable
    );

    // ================= SEARCH =================

    Page<Violation> findByOrganizationIdAndCloudAccount_IdInAndResourceIdContainingIgnoreCase(
            Long orgId,
            List<Long> accountIds,
            String resource,
            Pageable pageable
    );

    Page<Violation> findByOrganizationIdAndCloudAccount_IdInAndRule_RuleNameContainingIgnoreCase(
            Long orgId,
            List<Long> accountIds,
            String ruleName,
            Pageable pageable
    );

    // ================= COUNT =================

    long countByStatusAndOrganizationIdAndCloudAccount_IdIn(
            String status,
            Long orgId,
            List<Long> accountIds
    );

    // ================= RECENT =================

    List<Violation> findTop5ByOrganizationIdAndCloudAccount_IdInOrderByCreatedAtDesc(
            Long orgId,
            List<Long> accountIds
    );

    // ================= DUPLICATE CHECK =================

    boolean existsByRuleIdAndResourceIdAndStatusAndOrganizationIdAndCloudAccount_Id(
            Long ruleId,
            String resourceId,
            String status,
            Long organizationId,
            Long cloudAccountId
    );

    Optional<Violation> findByIdAndOrganizationIdAndCloudAccount_IdIn(
            Long id,
            Long orgId,
            List<Long> accountIds
    );
}