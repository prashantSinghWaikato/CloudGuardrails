package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<Rule, Long> {
    Rule findByRuleNameIgnoreCase(String ruleName);
}
