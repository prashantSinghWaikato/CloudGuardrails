package com.cloud.guardrails.config;

import com.cloud.guardrails.entity.Rule;
import com.cloud.guardrails.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RuleInitializer implements CommandLineRunner {

    private final RuleConfig ruleConfig;
    private final RuleRepository ruleRepository;

    @Override
    public void run(String... args) {

        for (RuleConfig.RuleDefinition ruleDef : ruleConfig.getList()) {

            String ruleName = ruleDef.getName().trim();
            Rule existingRule = ruleRepository.findByRuleNameIgnoreCase(ruleName);

            if (existingRule == null) {

                Rule rule = Rule.builder()
                        .ruleName(ruleName)
                        .description(ruleDef.getMessage())
                        .severity(ruleDef.getSeverity())
                        .enabled(true)
                        .autoRemediation(
                                ruleDef.getAutoRemediation() != null ? ruleDef.getAutoRemediation() : true
                        )
                        .remediationAction(
                                defaultRemediationAction(ruleDef)
                        )
                        .build();

                ruleRepository.save(rule);

            } else {

                existingRule.setDescription(ruleDef.getMessage());
                existingRule.setSeverity(ruleDef.getSeverity());
                existingRule.setEnabled(true);
                existingRule.setAutoRemediation(
                        ruleDef.getAutoRemediation() != null ? ruleDef.getAutoRemediation() : existingRule.getAutoRemediation()
                );
                existingRule.setRemediationAction(defaultRemediationAction(ruleDef));

                ruleRepository.save(existingRule);
            }
        }
    }

    private String defaultRemediationAction(RuleConfig.RuleDefinition ruleDef) {
        if (ruleDef.getRemediationAction() != null && !ruleDef.getRemediationAction().isBlank()) {
            return ruleDef.getRemediationAction();
        }

        String ruleName = ruleDef.getName() != null ? ruleDef.getName().toLowerCase() : "";

        if (ruleName.contains("security group") || ruleName.contains("ssh open") || ruleName.contains("rdp open")) {
            return "REVOKE_SECURITY_GROUP_RULE";
        }

        if (ruleName.contains("access key")) {
            return "DISABLE_ACCESS_KEY";
        }

        if (ruleName.contains("s3")) {
            return "BLOCK_PUBLIC_S3_ACCESS";
        }

        if (ruleName.contains("tag")) {
            return "TAG_RESOURCE";
        }

        return "DEFAULT_ACTION";
    }
}
