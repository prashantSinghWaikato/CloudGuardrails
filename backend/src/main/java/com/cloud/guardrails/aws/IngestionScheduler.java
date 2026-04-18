package com.cloud.guardrails.aws;

import com.cloud.guardrails.entity.CloudAccount;
import com.cloud.guardrails.repository.CloudAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ingestion.polling", name = "enabled", havingValue = "true")
public class IngestionScheduler {

    private final AwsIngestionService awsIngestionService;
    private final CloudAccountRepository accountRepository;

    @Scheduled(fixedRateString = "${ingestion.polling.fixed-rate-ms:3600000}")
    public void run() {
        List<CloudAccount> accounts = accountRepository.findByProviderIgnoreCaseAndMonitoringEnabledTrue("AWS");

        for (CloudAccount acc : accounts) {
            try {
                awsIngestionService.ingest(acc);
            } catch (Exception ex) {
                // Continue polling other accounts even if one account fails activation or sync.
            }
        }
    }
}
