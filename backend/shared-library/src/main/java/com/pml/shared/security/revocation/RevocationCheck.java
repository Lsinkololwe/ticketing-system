package com.pml.shared.security.revocation;

import reactor.core.publisher.Mono;

/**
 * Resolves whether a presented token has been revoked.
 *
 * <p>This is the only revocation abstraction application code should depend on. Whether the
 * answer comes from a Redis cache, a database, a remote service, or some combination is a
 * deployment concern — see {@link CachedRevocationCheck} for the standard composition.</p>
 *
 * <p>Implementations report an infrastructure failure as {@link RevocationDecision#UNKNOWN}
 * rather than {@link RevocationDecision#ACTIVE}, so that callers can distinguish a verified
 * token from an unverifiable one and apply their own policy.</p>
 */
@FunctionalInterface
public interface RevocationCheck {

    /**
     * @param jti the {@code jti} claim, or null
     * @param sid the {@code sid} claim, or null
     * @param sub the {@code sub} claim, or null
     * @return the decision — never an error signal
     */
    Mono<RevocationDecision> check(String jti, String sid, String sub);
}
