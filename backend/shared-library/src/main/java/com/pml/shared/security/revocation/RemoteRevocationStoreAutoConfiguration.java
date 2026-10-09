package com.pml.shared.security.revocation;

import com.pml.shared.security.InternalServiceClientAutoConfiguration;
import com.pml.shared.security.InternalServiceWebClients;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * The durable revocation store for services that do not own it: identity's records, read over
 * identity's internal API with this service's own client-credentials token.
 *
 * <p>Registered before {@link RevocationAutoConfiguration}, whose check needs a durable store, and
 * only where a service names identity with {@code pml.security.revocation.identity-url}. identity
 * defines its own MongoDB-backed store and never sets that key.
 */
@AutoConfiguration(after = InternalServiceClientAutoConfiguration.class, before = RevocationAutoConfiguration.class)
@ConditionalOnProperty(prefix = "pml.security.revocation", name = {"enabled", "identity-url"})
@EnableConfigurationProperties(RevocationProperties.class)
public class RemoteRevocationStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(InternalServiceWebClients.class)
    @ConditionalOnMissingBean(DurableRevocationStore.class)
    public HttpDurableRevocationStore httpDurableRevocationStore(InternalServiceWebClients clients,
                                                                 RevocationProperties properties) {
        return new HttpDurableRevocationStore(clients.to(properties.getIdentityUrl()));
    }
}
