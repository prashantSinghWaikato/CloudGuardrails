package com.cloud.guardrails.service;

import com.cloud.guardrails.dto.RuleResponse;
import com.cloud.guardrails.dto.RuleUpdateRequest;
import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;

    public List<RuleResponse> getAll() {
        return ruleRepository.findAll().stream()
                .map(this::map)
                .toList();
    }

    public RuleResponse getById(Long id) {
        return map(getRule(id));
    }

    public RuleResponse update(Long id, RuleUpdateRequest request) {
        Rule rule = getRule(id);

        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getSeverity() != null) {
            rule.setSeverity(request.getSeverity());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        if (request.getAutoRemediation() != null) {
            rule.setAutoRemediation(request.getAutoRemediation());
        }
        if (request.getRemediationAction() != null) {
            rule.setRemediationAction(request.getRemediationAction());
        }

        return map(ruleRepository.save(rule));
    }

    public RuleResponse setEnabled(Long id, boolean enabled) {
        Rule rule = getRule(id);
        rule.setEnabled(enabled);
        return map(ruleRepository.save(rule));
    }

    private Rule getRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rule not found"));
    }

    private RuleResponse map(Rule rule) {
        return RuleResponse.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .description(rule.getDescription())
                .severity(rule.getSeverity())
                .enabled(rule.getEnabled())
                .autoRemediation(rule.getAutoRemediation())
                .remediationAction(rule.getRemediationAction())
                .build();
    }
}
