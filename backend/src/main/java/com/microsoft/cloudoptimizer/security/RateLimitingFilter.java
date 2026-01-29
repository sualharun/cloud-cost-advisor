package com.microsoft.cloudoptimizer.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter to prevent API abuse.
 *
 * RATE LIMITING STRATEGY:
 * - Per-tenant limiting: 100 requests per minute
 * - Per-IP limiting for unauthenticated: 10 requests per minute
 * - Sliding window algorithm
 *
 * ENTERPRISE CONSIDERATIONS:
 * For production, consider using:
 * - Azure API Management for centralized rate limiting
 * - Redis-based distributed rate limiting
 * - Tiered limits based on subscription level
 */
@Component
@Order(1)
@Slf4j
public class RateLimitingFilter implements Filter {

    @Value("${rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${rate-limit.unauthenticated-requests-per-minute:10}")
    private int unauthenticatedRequestsPerMinute;

    // Simple in-memory rate limiting (use Redis for production)
    private final Map<String, RateLimitWindow> rateLimits = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip rate limiting for health checks
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/health")) {
            chain.doFilter(request, response);
            return;
        }

        String rateLimitKey = getRateLimitKey(httpRequest);
        int limit = isAuthenticated(httpRequest) ? requestsPerMinute : unauthenticatedRequestsPerMinute;

        RateLimitWindow window = rateLimits.computeIfAbsent(rateLimitKey, k -> new RateLimitWindow());

        if (!window.tryAcquire(limit)) {
            log.warn("Rate limit exceeded for key: {}", rateLimitKey);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Rate limit exceeded\", \"retryAfter\": 60}");
            return;
        }

        // Add rate limit headers
        httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - window.getCount())));
        httpResponse.setHeader("X-RateLimit-Reset", String.valueOf(window.getResetTime()));

        chain.doFilter(request, response);
    }

    private String getRateLimitKey(HttpServletRequest request) {
        // Use tenant ID if authenticated, otherwise IP
        String tenantId = TenantContext.getCurrentTenantIdOrNull();
        if (tenantId != null) {
            return "tenant:" + tenantId;
        }

        // Fall back to IP
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        return TenantContext.getCurrentTenantIdOrNull() != null;
    }

    /**
     * Simple sliding window rate limit tracker.
     */
    private static class RateLimitWindow {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();
        private static final long WINDOW_SIZE_MS = 60_000; // 1 minute

        synchronized boolean tryAcquire(int limit) {
            long now = System.currentTimeMillis();

            // Reset window if expired
            if (now - windowStart > WINDOW_SIZE_MS) {
                windowStart = now;
                count.set(0);
            }

            // Check if under limit
            if (count.get() >= limit) {
                return false;
            }

            count.incrementAndGet();
            return true;
        }

        int getCount() {
            return count.get();
        }

        long getResetTime() {
            return (windowStart + WINDOW_SIZE_MS) / 1000;
        }
    }
}
