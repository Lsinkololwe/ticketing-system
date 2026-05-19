package com.pml.catalog.config;

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
 */
@Slf4j
@Configuration
@EnableReactiveMongoAuditing
public class MongoAuditingConfig {

    @Bean
    public ReactiveAuditorAware<String> auditorAware() {
        return () -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(this::extractUserId)
                .defaultIfEmpty("system")
                .doOnNext(auditor -> log.debug("Audit user: {}", auditor));
    }

    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        String name = authentication.getName();
        return name != null && !name.isBlank() ? name : "system";
    }
}
