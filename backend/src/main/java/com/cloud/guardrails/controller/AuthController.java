package com.cloud.guardrails.controller;

import com.cloud.guardrails.dto.SignupRequest;
import com.cloud.guardrails.entity.Organization;
import com.cloud.guardrails.entity.User;
import com.cloud.guardrails.exception.ConflictException;
import com.cloud.guardrails.exception.NotFoundException;
import com.cloud.guardrails.exception.UnauthorizedException;
import com.cloud.guardrails.repository.OrganizationRepository;
import com.cloud.guardrails.repository.UserRepository;
import com.cloud.guardrails.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.cloud.guardrails.dto.LoginRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ================= LOGIN =================

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return jwtService.generateToken(user);
    }

    // ================= SIGNUP =================

    @PostMapping("/signup")
    public String signup(@RequestBody SignupRequest request) {

        // ✅ check existing user
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("User already exists");
        }

        // ✅ create organization
        Organization org = Organization.builder()
                .createdAt(LocalDateTime.now())
                .name(request.getOrganizationName())
                .build();

        org = organizationRepository.save(org);

        // ✅ create user
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .role("ADMIN")
                .password(passwordEncoder.encode(request.getPassword()))
                .organization(org)
                .cloudAccounts(List.of())
                .build();

        user = userRepository.save(user);

        // 🔥 return token (auto login)
        return jwtService.generateToken(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Unauthorized");
        }

        String token = authHeader.substring(7);

        String email = jwtService.extractClaims(token).getSubject();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return Map.of(
                "name", user.getName(),
                "email", user.getEmail(),
                "organization", user.getOrganization() != null
                        ? user.getOrganization().getName()
                        : "N/A",
                "accounts", user.getCloudAccounts() != null
                        ? user.getCloudAccounts()
                          .stream()
                          .map(a -> a.getAccountId())
                          .toList()
                        : List.of()
        );
    }
}
