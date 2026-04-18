package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.RuleResponse;
import com.cloud.guardrails.dto.RuleUpdateRequest;
import com.cloud.guardrails.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @GetMapping
    public List<RuleResponse> getAll() {
        return ruleService.getAll();
    }

    @GetMapping("/{id}")
    public RuleResponse getById(@PathVariable Long id) {
        return ruleService.getById(id);
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @RequestBody RuleUpdateRequest request) {
        return ruleService.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public RuleResponse setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return ruleService.setEnabled(id, enabled);
    }
}
