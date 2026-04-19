package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.AccountRequest;
import com.cloud.guardrails.dto.AccountResponse;
import com.cloud.guardrails.dto.AccountActivationRequest;
import com.cloud.guardrails.dto.AccountScanRunResponse;
import com.cloud.guardrails.dto.AccountValidationResponse;
import com.cloud.guardrails.service.CloudAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class CloudAccountController {

    private final CloudAccountService service;

    // ✅ CREATE
    @PostMapping
    public AccountResponse create(@RequestBody AccountRequest request) {
        return service.create(request);
    }

    @PostMapping("/validate")
    public AccountValidationResponse validate(@RequestBody AccountRequest request) {
        return service.validate(request);
    }

    @PostMapping("/{id}/activate")
    public AccountResponse activate(@PathVariable Long id, @RequestBody AccountActivationRequest request) {
        return service.activate(id, request);
    }

    @PostMapping("/{id}/scan")
    public AccountResponse scan(@PathVariable Long id) {
        return service.scan(id);
    }

    @GetMapping("/{id}/scans")
    public List<AccountScanRunResponse> getScanHistory(@PathVariable Long id) {
        return service.getScanHistory(id);
    }

    // ✅ LIST
    @GetMapping
    public List<AccountResponse> getAll() {
        return service.getAll();
    }

    @PutMapping("/{id}")
    public AccountResponse update(
            @PathVariable Long id,
            @RequestBody AccountRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
