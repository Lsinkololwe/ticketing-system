package com.pml.catalog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.security.publicop.PublicGraphQlFilter;
import com.pml.shared.security.publicop.PublicOperationPolicy;
import com.pml.shared.security.publicop.TrustedProxies;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.util.Map;
import java.util.Set;

/**
 * What a signed-out buyer may ask the catalog (ET-CAT-001 anonymous browsing): the discovery feed, the trending
 * list, one published event, and the reference data the buyer's filters, phone field and checkout need (per type, see {@code ReferenceAccess}). Every other operation, and every
 * field of an event that is not the public page of a published event, still needs a token.
 *
 * <p>The router's {@code _entities} is admitted only for the catalog's own public organizer counts
 * ({@code Organization.publishedEventCount}, {@code completedEventCount}), which the event page shows beside the
 * verified badge. Adding a root field here is a spec change.
 */
@Configuration
public class PublicDiscoveryConfig {

    /** The root query fields a caller without a token may select. */
    public static final Set<String> PUBLIC_ROOT_FIELDS = Set.of(
            "discoverEvents", "trendingEvents", "event", "categories", "provinces", "cities", "citiesWithEvents",
            // Admitted at the gateway; ReferenceAccess then limits a signed-out caller to the public types.
            "referenceData", "referenceDataByParent");

    /** Federated entity fields resolvable without a token. */
    public static final Map<String, Set<String>> PUBLIC_ENTITY_FIELDS = Map.of(
            "Organization", Set.of("publishedEventCount", "completedEventCount"));

    @Bean
    public PublicOperationPolicy catalogPublicOperationPolicy() {
        return PublicOperationPolicy.of("catalog", PUBLIC_ROOT_FIELDS).withEntities(PUBLIC_ENTITY_FIELDS);
    }

    @Bean
    public PublicGraphQlFilter catalogPublicGraphQlFilter(PublicOperationPolicy policy, ReactiveStringRedisTemplate redis,
                                                          ObjectMapper json, ObjectProvider<MeterRegistry> meters,
                                                          @Value("${platform.public-graphql.trusted-proxies:}") String trustedProxies) {
        // Only the router/gateway in front of us may say who the client is; see TrustedProxies.
        return new PublicGraphQlFilter(policy, redis, json, meters.getIfAvailable(), TrustedProxies.parse(trustedProxies));
    }
}
