package com.cloud.guardrails.controller;

import com.cloud.guardrails.service.AwsPushIngestionService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/aws/events")
@RequiredArgsConstructor
public class InternalAwsIngestionController {

    private final AwsPushIngestionService awsPushIngestionService;

    @PostMapping
    public ResponseEntity<Void> ingest(
            @RequestHeader("X-Guardrails-Ingestion-Secret") String secret,
            @RequestBody JsonNode payload
    ) throws Exception {
        awsPushIngestionService.ingest(payload, secret);
        return ResponseEntity.accepted().build();
    }
}
