package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.RemediationResponse;
import com.cloud.guardrails.entity.Remediation;
import com.cloud.guardrails.service.ResponseMapper;
import com.cloud.guardrails.service.RemediationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/remediations")
@RequiredArgsConstructor
public class RemediationController {

    private final RemediationService service;
    private final ResponseMapper responseMapper;

    // ✅ ORG SAFE
    @GetMapping
    public List<RemediationResponse> getAll() {
        return service.getAll()
                .stream()
                .map(responseMapper::toRemediationResponse)
                .toList();
    }

    // ✅ ORG SAFE
    @GetMapping("/{id}")
    public RemediationResponse getById(@PathVariable Long id) {
        return responseMapper.toRemediationResponse(service.getById(id));
    }

    // ✅ FIXED — CONSISTENT RESPONSE
    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id) {

        Remediation remediation = service.getById(id);

        service.approveAndExecute(remediation);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Remediation approved and executed");
        response.put("id", remediation.getId());

        return response;
    }

    @PostMapping("/{id}/retry")
    public RemediationResponse retry(@PathVariable Long id) {
        return responseMapper.toRemediationResponse(service.retry(service.getById(id)));
    }

    @PostMapping("/{id}/reverify")
    public RemediationResponse reverify(@PathVariable Long id) {
        return responseMapper.toRemediationResponse(service.reverify(service.getById(id)));
    }
}
