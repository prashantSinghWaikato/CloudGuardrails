package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.CloudAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CloudAccountRepository extends JpaRepository<CloudAccount, Long> {

    List<CloudAccount> findByOrganizationId(Long orgId);

    Optional<CloudAccount> findByIdAndOrganizationId(Long id, Long orgId);

    List<CloudAccount> findByAccountIdAndProviderIgnoreCase(String accountId, String provider);

    boolean existsByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(Long organizationId, String name, Long id);

    boolean existsByOrganizationIdAndAccountId(Long organizationId, String accountId);

    boolean existsByOrganizationIdAndAccountIdAndIdNot(Long organizationId, String accountId, Long id);
}
