package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.EventDTO;
import com.cloud.guardrails.service.EventIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping
    public void sendEvent(@RequestBody EventDTO event) {
        eventIngestionService.ingest(event);
        log.debug("Event processed directly: {}", event);
    }
}
