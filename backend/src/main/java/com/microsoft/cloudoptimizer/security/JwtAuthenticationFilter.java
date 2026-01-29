package com.microsoft.cloudoptimizer.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT authentication filter for API requests.
 *
 * TOKEN STRUCTURE:
 * {
 *   "sub": "user-id",
 *   "tid": "tenant-id",
 *   "roles": ["ROLE_USER", "ROLE_ADMIN"],
 *   "exp": 1234567890
 * }
 *
 * AZURE AD INTEGRATION:
 * In production, this filter validates Azure AD tokens.
 * The JWT is obtained by the browser extension via Azure AD popup flow.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret:default-secret-key-for-development-only-32chars}")
    private String jwtSecret;

    @Value("${jwt.issuer:cloud-optimizer}")
    private String jwtIssuer;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null) {
                Claims claims = validateToken(token);

                if (claims != null) {
                    String userId = claims.getSubject();
                    String tenantId = claims.get("tid", String.class);
                    String rolesString = claims.get("roles", String.class);
                    String[] roles = rolesString != null ? rolesString.split(",") : new String[0];

                    // Set tenant context
                    TenantContext.setCurrentTenantId(tenantId);
                    TenantContext.setCurrentUser(userId);
                    TenantContext.setCurrentRoles(roles);

                    // Set Spring Security context
                    List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Authenticated user: {} for tenant: {}", userId, tenantId);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Clear tenant context after request
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Claims validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(jwtIssuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip authentication for health checks and docs
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health");
    }
}
