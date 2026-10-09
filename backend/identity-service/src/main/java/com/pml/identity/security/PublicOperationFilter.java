package com.pml.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.security.publicop.PublicGraphQlFilter;
import com.pml.shared.security.publicop.PublicOperationPolicy;
import com.pml.shared.security.publicop.TrustedProxies;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Identity's use of the platform's public-operation filter (ET-ADM-002-R10): a signed-out caller reaches exactly
 * {@link #PUBLIC_FIELDS} and the organization badge the router resolves for the buyer's event page, and nothing else. The rules, the limits and the rate limiter are
 * {@link PublicGraphQlFilter}'s; only the allowlist is identity's. Adding a field is a spec change.
 */
@Component
public class PublicOperationFilter extends PublicGraphQlFilter {

    public static final Set<String> PUBLIC_FIELDS = Set.of("publicPlatformRules");

    /**
     * What the router may ask identity for about an organization on a signed-out event page: the verified badge.
     * The selection is checked to be this leaf field only, so no other organization field is reachable.
     */
    public static final java.util.Map<String, Set<String>> PUBLIC_ENTITY_FIELDS =
            java.util.Map.of("Organization", Set.of("verified"));

    private static PublicOperationPolicy identityPolicy() {
        return PublicOperationPolicy.of("identity", PUBLIC_FIELDS).withEntities(PUBLIC_ENTITY_FIELDS);
    }

    @Autowired
    public PublicOperationFilter(ReactiveStringRedisTemplate redis, ObjectMapper json,
                                 ObjectProvider<MeterRegistry> meters,
                                 @org.springframework.beans.factory.annotation.Value("${platform.public-graphql.trusted-proxies:}")
                                 String trustedProxies) {
        super(identityPolicy(), redis, json, meters.getIfAvailable(), TrustedProxies.parse(trustedProxies));
    }

    public PublicOperationFilter(ReactiveStringRedisTemplate redis, ObjectMapper json) {
        super(identityPolicy(), redis, json, null);
    }

    public PublicOperationFilter(ReactiveStringRedisTemplate redis, ObjectMapper json, TrustedProxies trustedProxies) {
        super(identityPolicy(), redis, json, null, trustedProxies);
    }
}
