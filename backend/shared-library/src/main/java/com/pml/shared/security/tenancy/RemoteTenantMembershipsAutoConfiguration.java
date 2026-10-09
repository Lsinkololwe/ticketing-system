package com.pml.shared.security.tenancy;

import com.pml.shared.security.InternalServiceClientAutoConfiguration;
import com.pml.shared.security.InternalServiceWebClients;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Resolves tenancy from identity-service in any service that names it with
 * {@code platform.tenancy.identity-url}. Identity itself defines its own {@link TenantMemberships}.
 */
@AutoConfiguration(after = InternalServiceClientAutoConfiguration.class, before = TenantScopeAutoConfiguration.class)
@ConditionalOnProperty(prefix = "platform.tenancy", name = "identity-url")
@EnableConfigurationProperties(TenancyProperties.class)
public class RemoteTenantMembershipsAutoConfiguration {

    @Bean
    @ConditionalOnBean(InternalServiceWebClients.class)
    @ConditionalOnMissingBean(TenantMemberships.class)
    public RemoteTenantMemberships remoteTenantMemberships(InternalServiceWebClients clients,
                                                           TenancyProperties properties) {
        return new RemoteTenantMemberships(clients.to(properties.identityUrl()));
    }
}
