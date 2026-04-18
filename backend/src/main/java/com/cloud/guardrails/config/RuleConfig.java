package com.cloud.guardrails.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "rules")
@Data
public class RuleConfig {

    private List<RuleDefinition> list;

    @Data
    public static class RuleDefinition {
        private String name;
        private String eventType;
        private String condition;
        private String severity;
        private String message;
        private Boolean autoRemediation;
        private String remediationAction;
    }
}
