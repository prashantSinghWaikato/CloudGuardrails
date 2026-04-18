package com.cloud.guardrails.service;

import com.cloud.guardrails.aws.AwsAccountValidationService;
import com.cloud.guardrails.aws.AwsIngestionService;
import com.cloud.guardrails.dto.AccountActivationRequest;
import com.cloud.guardrails.dto.AccountRequest;
import com.cloud.guardrails.dto.AccountResponse;
import com.cloud.guardrails.dto.AccountValidationResponse;
import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.exception.ConflictException;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.CloudAccountRepository;
import com.cloud.guardrails.repository.OrganizationRepository;
import com.cloud.guardrails.repository.UserRepository;
import com.cloud.guardrails.security.CredentialCryptoService;
import com.cloud.guardrails.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CloudAccountService {

    private final CloudAccountRepository repository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final AwsAccountValidationService awsAccountValidationService;
    private final AwsIngestionService awsIngestionService;
    private final NotificationService notificationService;

    // ✅ CREATE ACCOUNT
    public AccountResponse create(AccountRequest request) {

        Long orgId = UserContext.getOrgId();

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        validateNameAndAccountUniqueness(request, orgId, null);
        validateExternalAccount(request);

        CloudAccount account = CloudAccount.builder()
                .name(normalizeName(request.getName()))
                .accountId(request.getAccountId())
                .provider(request.getProvider())
                .region(request.getRegion())
                .accessKey(credentialCryptoService.encrypt(request.getAccessKey()))
                .secretKey(credentialCryptoService.encrypt(request.getSecretKey()))
                .monitoringEnabled(false)
                .activationStatus("PENDING")
                .activationMethod(null)
                .organization(org)
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(account);
        grantAccountToOrganizationUsers(org, account);
        notificationService.notifyAccountAdded(account);

        return map(account);
    }

    // ✅ LIST ACCOUNTS
    public List<AccountResponse> getAll() {

        Long orgId = UserContext.getOrgId();

        return repository.findByOrganizationId(orgId)
                .stream()
                .map(this::map)
                .toList();
    }

    private AccountResponse map(CloudAccount acc) {
        return AccountResponse.builder()
                .id(acc.getId())
                .name(acc.getName())
                .accountId(acc.getAccountId())
                .provider(acc.getProvider())
                .region(acc.getRegion())
                .monitoringEnabled(Boolean.TRUE.equals(acc.getMonitoringEnabled()))
                .activationStatus(acc.getActivationStatus())
                .activationMethod(acc.getActivationMethod())
                .roleArn(acc.getRoleArn())
                .lastActivatedAt(acc.getLastActivatedAt() != null ? acc.getLastActivatedAt().toString() : null)
                .lastSyncAt(acc.getLastSyncAt() != null ? acc.getLastSyncAt().toString() : null)
                .lastSyncStatus(acc.getLastSyncStatus())
                .lastSyncMessage(acc.getLastSyncMessage())
                .build();
    }

    public AccountResponse update(Long id, AccountRequest request) {

        Long orgId = UserContext.getOrgId();

        CloudAccount account = repository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        validateNameAndAccountUniqueness(request, orgId, id);
        validateExternalAccount(request);

        account.setName(normalizeName(request.getName()));
        account.setAccountId(request.getAccountId());
        account.setProvider(request.getProvider());
        account.setRegion(request.getRegion());
        account.setAccessKey(credentialCryptoService.encrypt(request.getAccessKey()));
        account.setSecretKey(credentialCryptoService.encrypt(request.getSecretKey()));
        account.setMonitoringEnabled(false);
        account.setActivationStatus("PENDING");
        account.setActivationMethod(null);
        account.setRoleArn(null);
        account.setExternalId(null);
        account.setLastActivatedAt(null);
        account.setLastSyncAt(null);
        account.setLastSyncStatus(null);
        account.setLastSyncMessage(null);

        repository.save(account);

        return map(account);
    }

    public void delete(Long id) {

        Long orgId = UserContext.getOrgId();

        CloudAccount account = repository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        revokeAccountFromOrganizationUsers(account);
        repository.delete(account);
    }

    public CloudAccount getById(Long id) {
        Long orgId = UserContext.getOrgId();

        return repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    public CloudAccount getByIdForOrganization(Long id, Long orgId) {
        return repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    public CloudAccount getAwsAccountByExternalAccountId(String accountId) {
        List<CloudAccount> matches = repository.findByAccountIdAndProviderIgnoreCase(accountId, "AWS");

        if (matches.isEmpty()) {
            throw new NotFoundException("AWS account not found");
        }

        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple AWS accounts matched the supplied AWS account ID");
        }

        return matches.get(0);
    }

    public AccountValidationResponse validate(AccountRequest request) {
        if ("AWS".equalsIgnoreCase(request.getProvider())) {
            return awsAccountValidationService.validateAccount(
                    request.getAccountId(),
                    request.getAccessKey(),
                    request.getSecretKey(),
                    request.getRegion()
            );
        }

        throw new IllegalArgumentException("Unsupported provider: " + request.getProvider());
    }

    public AccountResponse activate(Long id, AccountActivationRequest request) {
        Long orgId = UserContext.getOrgId();

        CloudAccount account = repository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        AccountValidationResponse validation = awsAccountValidationService.validateActivation(
                account,
                request.getRoleArn(),
                request.getExternalId()
        );

        account.setRoleArn(request.getRoleArn().trim());
        account.setExternalId(credentialCryptoService.encrypt(request.getExternalId()));
        account.setMonitoringEnabled(true);
        account.setActivationStatus("ACTIVE");
        account.setActivationMethod("ASSUME_ROLE");
        account.setLastActivatedAt(LocalDateTime.now());
        account.setLastSyncStatus("READY");
        account.setLastSyncMessage(validation.getMessage());

        repository.save(account);

        return map(account);
    }

    public AccountResponse scan(Long id) {
        Long orgId = UserContext.getOrgId();

        CloudAccount account = repository
                .findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (!Boolean.TRUE.equals(account.getMonitoringEnabled())) {
            throw new IllegalArgumentException("Activate monitoring before running a manual scan");
        }

        awsIngestionService.ingest(account);
        return map(repository.findById(account.getId()).orElse(account));
    }

    private void validateNameAndAccountUniqueness(AccountRequest request, Long orgId, Long currentId) {
        String normalizedName = normalizeName(request.getName());

        if (normalizedName == null || normalizedName.isBlank()) {
            throw new IllegalArgumentException("Account name is required");
        }

        boolean nameExists = currentId == null
                ? repository.existsByOrganizationIdAndNameIgnoreCase(orgId, normalizedName)
                : repository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(orgId, normalizedName, currentId);

        if (nameExists) {
            throw new ConflictException("Account name already exists");
        }

        if (request.getAccountId() == null || request.getAccountId().isBlank()) {
            throw new IllegalArgumentException("Account ID is required");
        }

        boolean accountExists = currentId == null
                ? repository.existsByOrganizationIdAndAccountId(orgId, request.getAccountId())
                : repository.existsByOrganizationIdAndAccountIdAndIdNot(orgId, request.getAccountId(), currentId);

        if (accountExists) {
            throw new ConflictException("AWS account already exists for this organization");
        }
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private void grantAccountToOrganizationUsers(Organization organization, CloudAccount account) {
        List<com.cloud.guardrails.entity.User> users = userRepository.findByOrganizationId(organization.getId());

        for (com.cloud.guardrails.entity.User user : users) {
            List<CloudAccount> accounts = user.getCloudAccounts() != null
                    ? new ArrayList<>(user.getCloudAccounts())
                    : new ArrayList<>();

            boolean alreadyPresent = accounts.stream().anyMatch(existing -> existing.getId().equals(account.getId()));
            if (!alreadyPresent) {
                accounts.add(account);
                user.setCloudAccounts(accounts);
                userRepository.save(user);
            }
        }
    }

    private void revokeAccountFromOrganizationUsers(CloudAccount account) {
        List<com.cloud.guardrails.entity.User> users = userRepository.findByOrganizationId(account.getOrganization().getId());

        for (com.cloud.guardrails.entity.User user : users) {
            List<CloudAccount> accounts = user.getCloudAccounts() != null
                    ? new ArrayList<>(user.getCloudAccounts())
                    : new ArrayList<>();

            boolean removed = accounts.removeIf(existing -> existing.getId().equals(account.getId()));
            if (removed) {
                user.setCloudAccounts(accounts);
                userRepository.save(user);
            }
        }
    }

    private void validateExternalAccount(AccountRequest request) {
        if ("AWS".equalsIgnoreCase(request.getProvider())) {
            awsAccountValidationService.validateAccount(
                    request.getAccountId(),
                    request.getAccessKey(),
                    request.getSecretKey(),
                    request.getRegion()
            );
        }
    }
}
