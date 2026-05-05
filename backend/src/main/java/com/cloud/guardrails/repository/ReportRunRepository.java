package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.ReportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {

    List<ReportRun> findTop10ByOrganizationIdAndReportTypeOrderByCreatedAtDesc(Long organizationId, String reportType);

    boolean existsByReportDefinitionIdAndPeriodStartAndPeriodEnd(Long reportDefinitionId,
                                                                 LocalDateTime periodStart,
                                                                 LocalDateTime periodEnd);

    List<ReportRun> findTop1ByReportDefinitionIdOrderByCreatedAtDesc(Long reportDefinitionId);
}
