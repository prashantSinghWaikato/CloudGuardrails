package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.entity.Violation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}