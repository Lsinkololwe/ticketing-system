package com.pml.shared.security.tenancy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * Installs the per-request tenant scope in every service that says how to find a caller's
 * memberships.
 *
 * <p>A service opts in by defining a {@link TenantMemberships} bean. Without the filter every
 * tenant-scoped lookup fails loudly rather than silently permitting anything — see
 * {@code CurrentTenantScope#get()}: a missing boundary should break the service, not the boundary.
 */
@AutoConfiguration
@ConditionalOnClass(ReactiveSecurityContextHolder.class)
@EnableConfigurationProperties(TenancyProperties.class)
public class TenantScopeAutoConfiguration {

    @Bean
    @ConditionalOnBean(TenantMemberships.class)
    @ConditionalOnMissingBean
    public TenantScopeWebFilter tenantScopeWebFilter(TenantMemberships memberships, TenancyProperties properties) {
        return new TenantScopeWebFilter(memberships, properties.platformWideAuthorities());
    }
}
