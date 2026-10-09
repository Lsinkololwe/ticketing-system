package com.pml.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.Optional;

/**
 * Spring Data auditing for every service: who wrote a document, and when by the platform clock.
 *
 * <h2>Why the clock is not optional</h2>
 * {@code @CreatedDate} and {@code @LastModifiedDate} are populated by Spring Data, not by
 * application code — so however carefully services avoid inline {@code now()} calls, audit
 * fields would still be stamped from the wall clock unless the auditing infrastructure is
 * pointed at the same bean. A frozen-clock test would drive a reservation across its ten-minute
 * boundary correctly, and the document it wrote would carry a {@code createdAt} from real time.
 *
 * <h2>Who</h2>
 * {@code @CreatedBy} and {@code @LastModifiedBy} carry the JWT subject — the Keycloak user id —
 * falling back to {@code preferred_username}, and {@code system} for work no user started: a
 * workflow activity, a migration, a consumer.
 *
 * <h2>Conditional on Spring Data being present</h2>
 * {@code spring-boot-starter-data-mongodb-reactive} is {@code provided} in this library, and
 * {@code api-gateway} does not use it; the {@link Clock} bean itself comes from
 * {@link PlatformClockAutoConfiguration}, so the gateway gets a clock without Spring Data.
 */
@AutoConfiguration(after = PlatformClockAutoConfiguration.class, afterName = {
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration"})
@ConditionalOnClass(DateTimeProvider.class)
public class PlatformAuditingAutoConfiguration {

    static final String SYSTEM = "system";

    @Bean
    @ConditionalOnMissingBean(name = "auditingDateTimeProvider")
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ReactiveMongoTemplate.class, ReactiveSecurityContextHolder.class})
    @EnableReactiveMongoAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
    static class ReactiveMongoAuditing {

        @Bean
        @ConditionalOnMissingBean
        public ReactiveAuditorAware<String> auditorAware() {
            return () -> ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .filter(Authentication::isAuthenticated)
                    .map(PlatformAuditingAutoConfiguration::auditorOf)
                    .defaultIfEmpty(SYSTEM);
        }
    }

    static String auditorOf(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = com.pml.shared.security.AccountIdentity.userIdOf(jwt);
            if (userId != null) {
                return userId;
            }
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        String name = authentication.getName();
        return name != null && !name.isBlank() ? name : SYSTEM;
    }
}
