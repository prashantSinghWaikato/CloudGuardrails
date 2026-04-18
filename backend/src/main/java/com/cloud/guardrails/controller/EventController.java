package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.EventDTO;
import com.cloud.guardrails.service.EventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventProducer eventProducer;

    public EventController(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @PostMapping
    public void sendEvent(@RequestBody EventDTO event) {
        try {
            eventProducer.sendEvent(event);
            log.debug("Event sent: {}", event);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }
}
