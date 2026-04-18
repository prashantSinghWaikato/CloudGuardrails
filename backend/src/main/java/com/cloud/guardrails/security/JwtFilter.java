package com.cloud.guardrails.security;

import com.cloud.guardrails.entity.User;
import com.cloud.guardrails.repository.UserRepository;
import com.cloud.guardrails.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);

            Claims claims = jwtService.extractClaims(token);

            String email = claims.getSubject();
            User user = userRepository.findWithCloudAccountsByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            Long orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;

            List<Long> accountIds = user.getCloudAccounts().stream()
                    .map(com.cloud.guardrails.entity.CloudAccount::getId)
                    .toList();

            // ✅ Set context
            UserContext.setEmail(email);
            UserContext.setOrgId(orgId);
            UserContext.setAccountIds(accountIds);

            // ✅ Set Spring Security auth (FIXES 403)
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of()
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.writeInvalidToken(request, response, "Invalid or expired token");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // ✅ VERY IMPORTANT
            UserContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/auth");
    }
}
