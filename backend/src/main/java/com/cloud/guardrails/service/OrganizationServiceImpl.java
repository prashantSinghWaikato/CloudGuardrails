package com.cloud.guardrails.service;

import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Override
    public Organization getById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Organization not found with id: " + id));
    }
}
