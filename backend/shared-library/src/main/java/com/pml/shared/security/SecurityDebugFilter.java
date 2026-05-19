package com.pml.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Base64;

/**
 * Security Debug Filter for logging Authorization headers and JWT tokens.
 *
 * <p>This filter logs incoming requests with their Authorization headers
 * to help diagnose authentication and authorization issues.</p>
 *
 * <h2>What it logs:</h2>
 * <ul>
 *   <li>Request method and path</li>
 *   <li>Authorization header presence</li>
 *   <li>JWT token claims (decoded header and payload)</li>
 *   <li>Token type (Bearer, Basic, etc.)</li>
 * </ul>
 *
 * <p><b>WARNING:</b> This filter should only be enabled in development/debugging.
 * It logs sensitive information that should not be exposed in production.</p>
 *
 * @author PML Ticketing System
 */
public class SecurityDebugFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityDebugFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // Log the request
        log.info("╔══════════════════════════════════════════════════════════════════════");
        log.info("║ SECURITY DEBUG - Incoming Request");
        log.info("╠══════════════════════════════════════════════════════════════════════");
        log.info("║ Method: {} | Path: {}", method, path);

        // Get Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("║ Authorization Header: MISSING (no token provided)");
            log.info("╚══════════════════════════════════════════════════════════════════════");
        } else if (authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            log.info("║ Authorization Header: Bearer token present");
            log.info("║ Token Length: {} characters", token.length());

            // Decode and log JWT claims (header and payload only, not signature)
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String header = new String(Base64.getUrlDecoder().decode(parts[0]));
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

                    log.info("╠══════════════════════════════════════════════════════════════════════");
                    log.info("║ JWT Header: {}", header);
                    log.info("╠══════════════════════════════════════════════════════════════════════");
                    log.info("║ JWT Payload: {}", payload);
                    log.info("╠══════════════════════════════════════════════════════════════════════");

                    // Extract key claims for easier debugging
                    extractAndLogClaims(payload);
                } else {
                    log.warn("║ Token format: Invalid JWT structure (expected 3 parts, got {})", parts.length);
                }
            } catch (Exception e) {
                log.error("║ Error decoding JWT: {}", e.getMessage());
            }
            log.info("╚══════════════════════════════════════════════════════════════════════");
        } else {
            log.info("║ Authorization Header: {} (not a Bearer token)",
                    authHeader.substring(0, Math.min(20, authHeader.length())) + "...");
            log.info("╚══════════════════════════════════════════════════════════════════════");
        }

        return chain.filter(exchange);
    }

    private void extractAndLogClaims(String payload) {
        try {
            // Simple JSON parsing for key claims
            // In production, use a proper JSON parser
            if (payload.contains("\"sub\"")) {
                String sub = extractClaim(payload, "sub");
                log.info("║ Subject (sub): {}", sub);
            }
            if (payload.contains("\"preferred_username\"")) {
                String username = extractClaim(payload, "preferred_username");
                log.info("║ Username: {}", username);
            }
            if (payload.contains("\"email\"")) {
                String email = extractClaim(payload, "email");
                log.info("║ Email: {}", email);
            }
            if (payload.contains("\"realm_access\"")) {
                int start = payload.indexOf("\"realm_access\"");
                int end = payload.indexOf("}", start) + 1;
                if (start != -1 && end > start) {
                    String realmAccess = payload.substring(start, Math.min(end + 20, payload.length()));
                    log.info("║ Realm Access: {}", realmAccess);
                }
            }
            if (payload.contains("\"resource_access\"")) {
                int start = payload.indexOf("\"resource_access\"");
                int end = payload.indexOf("}}", start) + 2;
                if (start != -1 && end > start) {
                    String resourceAccess = payload.substring(start, Math.min(end + 20, payload.length()));
                    log.info("║ Resource Access: {}", resourceAccess);
                }
            }
            if (payload.contains("\"scope\"")) {
                String scope = extractClaim(payload, "scope");
                log.info("║ Scopes: {}", scope);
            }
            if (payload.contains("\"exp\"")) {
                String exp = extractClaim(payload, "exp");
                log.info("║ Expires: {} (epoch)", exp);
            }
            if (payload.contains("\"iss\"")) {
                String iss = extractClaim(payload, "iss");
                log.info("║ Issuer: {}", iss);
            }
            if (payload.contains("\"aud\"")) {
                String aud = extractClaim(payload, "aud");
                log.info("║ Audience: {}", aud);
            }
        } catch (Exception e) {
            log.debug("║ Could not extract claims: {}", e.getMessage());
        }
    }

    private String extractClaim(String json, String claim) {
        String searchKey = "\"" + claim + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return "N/A";

        start += searchKey.length();
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length()) return "N/A";

        char firstChar = json.charAt(start);
        if (firstChar == '"') {
            // String value
            int end = json.indexOf("\"", start + 1);
            return end > start ? json.substring(start + 1, end) : "N/A";
        } else if (firstChar == '[' || firstChar == '{') {
            // Array or object - return a snippet
            int depth = 1;
            int end = start + 1;
            char open = firstChar;
            char close = firstChar == '[' ? ']' : '}';
            while (end < json.length() && depth > 0) {
                char c = json.charAt(end);
                if (c == open) depth++;
                else if (c == close) depth--;
                end++;
            }
            return json.substring(start, Math.min(end, start + 100));
        } else {
            // Number or boolean
            int end = start;
            while (end < json.length() && !",}]".contains(String.valueOf(json.charAt(end)))) {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }
}
