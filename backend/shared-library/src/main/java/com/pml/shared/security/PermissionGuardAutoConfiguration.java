package com.pml.shared.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Installs {@link RequiresPermissionAspect} and the {@link PermissionGuards} expression bean in every service. */
@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class PermissionGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequiresPermissionAspect requiresPermissionAspect(
            org.springframework.beans.factory.ObjectProvider<com.pml.shared.security.tenancy.PlatformWideAuditSink> sink,
            org.springframework.beans.factory.ObjectProvider<java.time.Clock> clock,
            @org.springframework.beans.factory.annotation.Value("${spring.application.name:unknown}") String service) {
        return new RequiresPermissionAspect(sink.getIfAvailable(), service,
                clock.getIfAvailable(java.time.Clock::systemUTC));
    }

    /** The bean {@code @PreAuthorize} expressions reach as {@code @permissionGuards}. */
    @Bean(name = "permissionGuards")
    @ConditionalOnMissingBean(name = "permissionGuards")
    public PermissionGuards permissionGuards() {
        return new PermissionGuards();
    }

    /** In every service that calls identity: compares the compiled catalogue with identity's. */
    @Bean(name = "permissionCatalogueHealthIndicator")
    @ConditionalOnProperty("services.identity.url")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.ReactiveHealthIndicator")
    @ConditionalOnMissingBean(name = "permissionCatalogueHealthIndicator")
    public PermissionCatalogueHealthIndicator permissionCatalogueHealthIndicator(
            InternalServiceWebClients clients,
            @org.springframework.beans.factory.annotation.Value("${services.identity.url}") String identityUrl) {
        return new PermissionCatalogueHealthIndicator(clients.to(identityUrl));
    }

    /** In every service that calls identity: the one client for its authorization endpoints. */
    @Bean
    @ConditionalOnProperty("services.identity.url")
    @ConditionalOnMissingBean
    public com.pml.shared.security.authorization.IdentityAuthorizationClient identityAuthorizationClient(
            InternalServiceWebClients clients,
            @org.springframework.beans.factory.annotation.Value("${services.identity.url}") String identityUrl) {
        return new com.pml.shared.security.authorization.IdentityAuthorizationClient(clients.to(identityUrl));
    }
}
