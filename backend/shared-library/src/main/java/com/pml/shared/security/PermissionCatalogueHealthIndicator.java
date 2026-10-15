package com.pml.shared.security;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Reports whether this service was built with the same permission catalogue as identity, which is
 * the service that decides organization and event access. A service on an older revision would deny
 * or admit on rules identity no longer holds, and nothing else would show it.
 *
 * <p>The answer is read when health is asked for and kept for a minute; nothing polls identity.
 * An unreachable identity is reported as a detail and does not mark this service down, since a
 * mismatch is a deployment fault and not a runtime outage.</p>
 */
public class PermissionCatalogueHealthIndicator implements ReactiveHealthIndicator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final com.pml.shared.security.authorization.IdentityAuthorizationClient identity;
    private final String local;
    private final Clock clock;
    private volatile Mono<Health> cached;
    private volatile long cachedAt;

    public PermissionCatalogueHealthIndicator(WebClient identity) {
        this(identity, Clock.systemUTC());
    }

    public PermissionCatalogueHealthIndicator(WebClient identity, Clock clock) {
        this.identity = new com.pml.shared.security.authorization.IdentityAuthorizationClient(identity);
        this.clock = clock;
        this.local = PermissionCatalogueFingerprint.compute();
    }

    @Override
    public Mono<Health> health() {
        long now = clock.millis();
        if (cached == null || now - cachedAt > 60_000) {
            cached = probe().cache();
            cachedAt = now;
        }
        return cached;
    }

    private Mono<Health> probe() {
        return identity.catalogueFingerprint()
                .timeout(PROBE_TIMEOUT)
                .map(remote -> {
                    boolean same = local.equals(remote);
                    Health.Builder builder = same ? Health.up() : Health.status("CATALOGUE_MISMATCH");
                    return builder.withDetail("local", local).withDetail("identity", String.valueOf(remote)).build();
                })
                .onErrorResume(error -> Mono.just(Health.unknown()
                        .withDetail("local", local)
                        .withDetail("reason", "identity did not answer: " + error.getClass().getSimpleName())
                        .build()));
    }
}
