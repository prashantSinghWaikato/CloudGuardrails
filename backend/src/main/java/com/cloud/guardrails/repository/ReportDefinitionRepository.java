package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    Optional<ReportDefinition> findByOrganizationIdAndReportType(Long organizationId, String reportType);

    List<ReportDefinition> findByEnabledTrueAndReportType(String reportType);
}
