package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ViolationRepository extends JpaRepository<Violation, Long> {

    List<Violation> findByOrganizationId(Long orgId);

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

    @Query("""
            select v
            from Violation v
            where v.organization.id = :orgId
              and v.cloudAccount.id in :accountIds
              and (
                    lower(v.resourceId) like lower(concat('%', :query, '%'))
                    or lower(v.rule.ruleName) like lower(concat('%', :query, '%'))
                  )
            """)
    Page<Violation> searchByResourceOrRule(@Param("orgId") Long orgId,
                                           @Param("accountIds") List<Long> accountIds,
                                           @Param("query") String query,
                                           Pageable pageable);

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
