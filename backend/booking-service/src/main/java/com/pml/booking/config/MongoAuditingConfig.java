package com.pml.booking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * MongoDB Auditing Configuration
 *
 * Business Intent: Automatically tracks who created and modified documents.
 * Integrates with Keycloak security context to extract the authenticated user.
 *
 * Auditing Fields Populated:
 * - @CreatedBy: User ID from JWT subject claim
 * - @LastModifiedBy: User ID from JWT subject claim
 * - @CreatedDate: Timestamp when document was created
 * - @LastModifiedDate: Timestamp when document was last modified
 *
 * This enables complete audit trails for compliance, debugging, and
 * understanding data lineage in the ticketing system.
 */
@Slf4j
@Configuration
@EnableReactiveMongoAuditing
public class MongoAuditingConfig {

    /**
     * Provides the current auditor (user) from the reactive security context.
     *
     * Extraction Strategy:
     * 1. Get security context from ReactiveSecurityContextHolder
     * 2. Extract Authentication object
     * 3. If principal is a JWT, extract the subject claim (user ID)
     * 4. Fallback to principal name if not JWT
     * 5. Default to "system" for unauthenticated operations
     */
    @Bean
    public ReactiveAuditorAware<String> auditorAware() {
        return () -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(this::extractUserId)
                .defaultIfEmpty("system")
                .doOnNext(auditor -> log.debug("Audit user: {}", auditor));
    }

    /**
     * Extract user ID from authentication principal.
     * Supports JWT (Keycloak) and fallback to principal name.
     */
    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            // Extract from Keycloak JWT subject claim
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }

            // Fallback to preferred_username claim
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        // Fallback to principal name
        String name = authentication.getName();
        return name != null && !name.isBlank() ? name : "system";
    }
}
