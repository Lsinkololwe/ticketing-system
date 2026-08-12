package com.pml.shared.security.revocation;

import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * The second, independent opinion behind the Redis cache.
 *
 * <h2>Independence requirement</h2>
 * <p>The fallback exists so that a Redis outage does not take revocation offline, which holds
 * only while its failure mode is uncorrelated with Redis. Expressing it as an SPI lets each
 * service bind it to a store that satisfies that:</p>
 *
 * <ul>
 *   <li><b>identity-service</b> — MongoDB, which it already owns and which holds the system of
 *       record for revocations.</li>
 *   <li><b>booking, catalog and any future service</b> — the identity-service internal API: a
 *       different process over a different protocol, reached without holding a second copy of
 *       the data.</li>
 * </ul>
 *
 * <p>Implementations are side-effect free on the read path and propagate errors, so that
 * {@link CachedRevocationCheck} can distinguish "not revoked" from "could not tell".</p>
 */
public interface DurableRevocationStore {

    /**
     * @param identifiers the token's identifiers, never empty
     * @return {@link RevocationDecision#REVOKED} if any identifier has a live revocation,
     *         {@link RevocationDecision#ACTIVE} if none does; an error signal if the store
     *         cannot answer
     */
    Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers);

    /** Liveness probe for the health endpoint. Errors mean unavailable. */
    Mono<Void> ping();

    /** Short name for logs, metrics and health details, e.g. {@code "mongodb"}. */
    String name();
}
