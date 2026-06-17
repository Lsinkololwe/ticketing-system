package com.pml.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * OWASP Security Headers Filter.
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A02:2021 - Cryptographic Failures: HSTS enforcement</li>
 *   <li>A05:2021 - Security Misconfiguration: Security headers</li>
 * </ul>
 *
 * <h2>Headers Added</h2>
 * <pre>
 * Strict-Transport-Security    → Forces HTTPS for 1 year
 * X-Content-Type-Options       → Prevents MIME sniffing
 * X-Frame-Options              → Prevents clickjacking
 * X-XSS-Protection             → Legacy XSS protection
 * Content-Security-Policy      → Restricts resource loading
 * Referrer-Policy              → Controls referrer information
 * Permissions-Policy           → Restricts browser features
 * Cache-Control                → Prevents caching of sensitive data
 * </pre>
 *
 * <h2>Filter Order: 100</h2>
 * Runs AFTER route filters to modify the response headers.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();

        // Use beforeCommit() to modify headers BEFORE they are sent to the client.
        // This prevents UnsupportedOperationException on ReadOnlyHttpHeaders.
        response.beforeCommit(() -> {
            try {
                HttpHeaders headers = response.getHeaders();

                // ═══════════════════════════════════════════════════════════════════
                // TRANSPORT SECURITY
                // ═══════════════════════════════════════════════════════════════════

                // HSTS: Force HTTPS for 1 year, include subdomains, allow preload list
                headers.add("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains; preload");

                // ═══════════════════════════════════════════════════════════════════
                // CONTENT SECURITY
                // ═══════════════════════════════════════════════════════════════════

                // Prevent MIME type sniffing
                headers.add("X-Content-Type-Options", "nosniff");

                // Prevent clickjacking
                headers.add("X-Frame-Options", "SAMEORIGIN");

                // Legacy XSS filter
                headers.add("X-XSS-Protection", "1; mode=block");

                // Content Security Policy
                headers.add("Content-Security-Policy",
                        "default-src 'self'; " +
                        "script-src 'self'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self'; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'self'; " +
                        "form-action 'self'; " +
                        "base-uri 'self'; " +
                        "object-src 'none'");

                // ═══════════════════════════════════════════════════════════════════
                // PRIVACY & TRACKING
                // ═══════════════════════════════════════════════════════════════════

                headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

                headers.add("Permissions-Policy",
                        "accelerometer=(), " +
                        "camera=(), " +
                        "geolocation=(), " +
                        "gyroscope=(), " +
                        "magnetometer=(), " +
                        "microphone=(), " +
                        "payment=(), " +
                        "usb=()");

                // ═══════════════════════════════════════════════════════════════════
                // CACHE CONTROL (for API responses)
                // ═══════════════════════════════════════════════════════════════════

                if (!headers.containsKey(HttpHeaders.CACHE_CONTROL)) {
                    headers.add(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
                    headers.add(HttpHeaders.PRAGMA, "no-cache");
                    headers.add(HttpHeaders.EXPIRES, "0");
                }

                // ═══════════════════════════════════════════════════════════════════
                // SERVER INFORMATION HIDING
                // ═══════════════════════════════════════════════════════════════════

                headers.remove("Server");
                headers.remove("X-Powered-By");

            } catch (UnsupportedOperationException ignored) {
                // Headers already committed (read-only), skip modification
            }

            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run after route filters to modify response headers
        return 100;
    }
}
