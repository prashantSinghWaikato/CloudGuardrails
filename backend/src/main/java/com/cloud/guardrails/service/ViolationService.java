package com.cloud.guardrails.service;

import com.cloud.guardrails.config.RuleConfig;
import com.cloud.guardrails.dto.ViolationResponse;
import com.cloud.guardrails.engine.ConditionEvaluator;
import com.cloud.guardrails.entity.Event;
import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.RuleRepository;
import com.cloud.guardrails.repository.ViolationRepository;
import com.cloud.guardrails.security.UserContext;
import com.cloud.guardrails.util.TimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@Slf4j
public class ViolationService {

    private final RuleConfig ruleConfig;
    private final ViolationRepository violationRepository;
    private final RuleRepository ruleRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;
    private final RemediationService remediationService;
    private final ResponseMapper responseMapper;
    private final NotificationService notificationService;
    private final RealtimeMessagingService realtimeMessagingService;

    public ViolationService(RuleConfig ruleConfig,
                            ViolationRepository violationRepository,
                            RuleRepository ruleRepository,
                            ConditionEvaluator conditionEvaluator,
                            ObjectMapper objectMapper,
                            RemediationService remediationService,
                            ResponseMapper responseMapper,
                            NotificationService notificationService,
                            RealtimeMessagingService realtimeMessagingService) {
        this.ruleConfig = ruleConfig;
        this.violationRepository = violationRepository;
        this.ruleRepository = ruleRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.objectMapper = objectMapper;
        this.remediationService = remediationService;
        this.responseMapper = responseMapper;
        this.notificationService = notificationService;
        this.realtimeMessagingService = realtimeMessagingService;
    }

    // ================= EVENT PROCESSING =================

    public int evaluate(Event event) {

        if (event == null || event.getOrganization() == null || event.getCloudAccount() == null) {
            log.warn("Skipping event: missing organization or cloud account");
            return 0;
        }

        int created = 0;
        for (RuleConfig.RuleDefinition ruleDef : ruleConfig.getList()) {
            if (isMatchingRule(ruleDef, event)) {
                created += processRule(ruleDef, event);
            }
        }

        return created;
    }

    private boolean isMatchingRule(RuleConfig.RuleDefinition ruleDef, Event event) {
        String ruleEventType = ruleDef.getEventType();

        if (ruleEventType == null || ruleEventType.isBlank() || "*".equals(ruleEventType.trim())) {
            return true;
        }

        return ruleEventType.equalsIgnoreCase(event.getEventType());
    }

    @Transactional
    private int processRule(RuleConfig.RuleDefinition ruleDef, Event event) {

        JsonNode payloadNode = parsePayload(event);
        if (payloadNode == null) return 0;

        if (!conditionEvaluator.evaluate(ruleDef.getCondition(), payloadNode)) return 0;

        Rule dbRule = fetchRule(ruleDef.getName());
        if (dbRule == null || !Boolean.TRUE.equals(dbRule.getEnabled())) return 0;

        Long orgId = event.getOrganization().getId();

        boolean exists = violationRepository
                .existsByRuleIdAndResourceIdAndStatusAndOrganizationIdAndCloudAccount_Id(
                        dbRule.getId(),
                        event.getResourceId(),
                        "OPEN",
                        orgId,
                        event.getCloudAccount().getId()
                );

        if (exists) return 0;

        Violation savedViolation = violationRepository.save(buildViolation(event, dbRule));

        remediationService.createRemediation(savedViolation, dbRule);

        realtimeMessagingService.sendViolation(savedViolation);
        notificationService.notifyViolationCreated(savedViolation);
        return 1;
    }

    private JsonNode parsePayload(Event event) {
        try {
            return objectMapper.valueToTree(event.getPayload());
        } catch (Exception e) {
            log.error("Error parsing payload for event {}", event.getId(), e);
            return null;
        }
    }

    private Rule fetchRule(String ruleName) {
        return ruleRepository.findByRuleNameIgnoreCase(ruleName);
    }

    private Violation buildViolation(Event event, Rule rule) {

        return Violation.builder()
                .event(event)
                .rule(rule)
                .organization(event.getOrganization())
                .cloudAccount(event.getCloudAccount())
                .resourceId(event.getResourceId())
                .severity(rule.getSeverity())
                .status("OPEN")
                .createdAt(TimeUtils.utcNow())
                .updatedAt(TimeUtils.utcNow())
                .build();
    }

    // ================= API METHODS =================

    public Page<Violation> getViolations(Pageable pageable) {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (accountIds == null || accountIds.isEmpty()) {
            return Page.empty();
        }

        return violationRepository
                .findByOrganizationIdAndCloudAccount_IdIn(
                        orgId, accountIds, pageable);
    }

    public Violation getById(Long id) {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        return violationRepository
                .findByIdAndOrganizationIdAndCloudAccount_IdIn(
                        id, orgId, accountIds
                )
                .orElseThrow(() -> new NotFoundException("Violation not found"));
    }

    public Page<Violation> filter(String severity, String status, Pageable pageable) {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (accountIds == null || accountIds.isEmpty()) {
            return Page.empty();
        }

        if (severity != null && status != null) {
            return violationRepository
                    .findBySeverityAndStatusAndOrganizationIdAndCloudAccount_IdIn(
                            severity, status, orgId, accountIds, pageable);
        }

        if (severity != null) {
            return violationRepository
                    .findBySeverityAndOrganizationIdAndCloudAccount_IdIn(
                            severity, orgId, accountIds, pageable);
        }

        if (status != null) {
            return violationRepository
                    .findByStatusAndOrganizationIdAndCloudAccount_IdIn(
                            status, orgId, accountIds, pageable);
        }

        return violationRepository
                .findByOrganizationIdAndCloudAccount_IdIn(
                        orgId, accountIds, pageable);
    }

    public Page<Violation> search(String query, Pageable pageable) {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (accountIds == null || accountIds.isEmpty()) {
            return Page.empty();
        }

        Page<Violation> byResource =
                violationRepository
                        .findByOrganizationIdAndCloudAccount_IdInAndResourceIdContainingIgnoreCase(
                                orgId, accountIds, query, pageable);

        Page<Violation> byRule =
                violationRepository
                        .findByOrganizationIdAndCloudAccount_IdInAndRule_RuleNameContainingIgnoreCase(
                                orgId, accountIds, query, pageable);

        List<Violation> combined = new java.util.ArrayList<>();
        combined.addAll(byResource.getContent());
        combined.addAll(byRule.getContent());

        return new org.springframework.data.domain.PageImpl<>(
                combined,
                pageable,
                combined.size()
        );
    }

    public Violation updateStatus(Long id, String status) {

        Violation v = getById(id);

        v.setStatus(status);
        v.setUpdatedAt(TimeUtils.utcNow());

        return violationRepository.save(v);
    }

    public long count(String status) {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        return violationRepository
                .countByStatusAndOrganizationIdAndCloudAccount_IdIn(
                        status, orgId, accountIds);
    }

    public List<Violation> getRecent() {

        Long orgId = UserContext.getOrgId();
        List<Long> accountIds = UserContext.getAccountIds();

        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }

        return violationRepository
                .findTop5ByOrganizationIdAndCloudAccount_IdInOrderByCreatedAtDesc(
                        orgId, accountIds);
    }
}
