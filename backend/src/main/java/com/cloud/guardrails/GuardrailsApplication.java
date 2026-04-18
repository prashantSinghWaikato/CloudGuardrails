package com.cloud.guardrails;

import com.cloud.guardrails.config.RuleConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RuleConfig.class)
@EnableScheduling
public class GuardrailsApplication {

	public static void main(String[] args) {
		SpringApplication.run(GuardrailsApplication.class, args);
	}

}
