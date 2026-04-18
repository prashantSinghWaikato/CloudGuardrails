package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.ViolationDetailResponse;
import com.cloud.guardrails.dto.ViolationResponse;
import com.cloud.guardrails.entity.Violation;
import com.cloud.guardrails.service.ResponseMapper;
import com.cloud.guardrails.service.RemediationService;
import com.cloud.guardrails.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;
    private final RemediationService remediationService;
    private final ResponseMapper responseMapper;

    // 🔥 FIXED — ORG SAFE
    @GetMapping
    public Page<ViolationResponse> getAll(Pageable pageable) {

        Page<Violation> page = violationService.getViolations(pageable);

        return page.map(responseMapper::toViolationResponse);
    }

    @GetMapping("/{id}")
    public ViolationDetailResponse getById(@PathVariable Long id) {
        Violation violation = violationService.getById(id);
        return responseMapper.toViolationDetailResponse(
                violation,
                remediationService.findForViolation(violation)
        );
    }

    // 🔥 FIXED — ORG SAFE FILTER
    @GetMapping("/filter")
    public Page<ViolationResponse> filter(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            Pageable pageable) {

        Page<Violation> page =
                violationService.filter(severity, status, pageable);

        return page.map(responseMapper::toViolationResponse);
    }

    @PutMapping("/{id}/status")
    public ViolationResponse updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        return responseMapper.toViolationResponse(violationService.updateStatus(id, status));
    }

    // 🔥 FIXED — ORG SAFE SEARCH
    @GetMapping("/search")
    public Page<ViolationResponse> search(
            @RequestParam String query,
            Pageable pageable) {

        Page<Violation> page =
                violationService.search(query, pageable);

        return page.map(responseMapper::toViolationResponse);
    }

    @GetMapping("/count")
    public long count(@RequestParam String status) {
        return violationService.count(status);
    }

    @GetMapping("/recent")
    public List<ViolationResponse> getRecentViolations() {

        return violationService.getRecent()
                .stream()
                .map(responseMapper::toViolationResponse)
                .toList();
    }

    @PostMapping("/{id}/remediate")
    public ResponseEntity<?> triggerRemediation(@PathVariable Long id) {

        Violation violation = violationService.getById(id);

        var remediation = remediationService.createRemediation(
                violation,
                violation.getRule()
        );

        if (remediation == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Remediation already exists for this violation");
        }

        return ResponseEntity.ok("Remediation triggered successfully");
    }
}
