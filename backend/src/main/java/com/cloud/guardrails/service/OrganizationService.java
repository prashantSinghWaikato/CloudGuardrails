package com.cloud.guardrails.service;

import com.cloud.guardrails.entity.Organization;

public interface OrganizationService {
    Organization getById(Long id);
}