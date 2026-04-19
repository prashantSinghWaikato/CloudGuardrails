package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.AccountScanRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountScanRunRepository extends JpaRepository<AccountScanRun, Long> {

    List<AccountScanRun> findTop10ByCloudAccount_IdOrderByStartedAtDesc(Long cloudAccountId);
}
