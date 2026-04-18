package com.cloud.guardrails.repository;

import com.cloud.guardrails.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByCloudAccount_IdAndExternalEventId(Long cloudAccountId, String externalEventId);
}
