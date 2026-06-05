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
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            // ═══════════════════════════════════════════════════════════════════
            // TRANSPORT SECURITY
            // ═══════════════════════════════════════════════════════════════════

            // HSTS: Force HTTPS for 1 year, include subdomains, allow preload list
            // This prevents protocol downgrade attacks and cookie hijacking
            headers.add("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains; preload");

            // ═══════════════════════════════════════════════════════════════════
            // CONTENT SECURITY
            // ═══════════════════════════════════════════════════════════════════

            // Prevent MIME type sniffing (stops browsers from guessing content type)
            headers.add("X-Content-Type-Options", "nosniff");

            // Prevent page from being embedded in iframe (clickjacking protection)
            // SAMEORIGIN allows embedding within same domain (for admin dashboards)
            headers.add("X-Frame-Options", "SAMEORIGIN");

            // Legacy XSS filter (deprecated but still useful for older browsers)
            headers.add("X-XSS-Protection", "1; mode=block");

            // Content Security Policy: Restricts where resources can be loaded from
            // - default-src 'self': Only allow resources from same origin
            // - script-src 'self': Only same-origin scripts (no inline)
            // - style-src 'self' 'unsafe-inline': Allow inline styles (needed for some UIs)
            // - img-src 'self' data: https:: Allow images from same origin, data URIs, HTTPS
            // - font-src 'self': Only same-origin fonts
            // - connect-src 'self': Only same-origin XHR/WebSocket
            // - frame-ancestors 'self': Only allow framing from same origin
            // - form-action 'self': Only allow form submissions to same origin
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

            // Control referrer header: Only send origin for cross-origin requests
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

            // Disable browser features that could leak data or enable fingerprinting
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

            // Prevent caching of API responses (they may contain sensitive data)
            // Individual routes can override this for cacheable responses
            if (!headers.containsKey(HttpHeaders.CACHE_CONTROL)) {
                headers.add(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
                headers.add(HttpHeaders.PRAGMA, "no-cache");
                headers.add(HttpHeaders.EXPIRES, "0");
            }

            // ═══════════════════════════════════════════════════════════════════
            // SERVER INFORMATION HIDING
            // ═══════════════════════════════════════════════════════════════════

            // Remove server version information (prevents version-specific attacks)
            headers.remove("Server");
            headers.remove("X-Powered-By");
        }));
    }

    @Override
    public int getOrder() {
        // Run after route filters to modify response headers
        return 100;
    }
}
