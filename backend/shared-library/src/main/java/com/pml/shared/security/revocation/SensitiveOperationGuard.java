package com.pml.shared.security.revocation;

import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Enforces a fail-closed revocation check before a sensitive operation runs.
 *
 * <h2>Per-operation policy</h2>
 * <p>The strict policy is opted into per operation rather than applied globally, so that an
 * unreachable revocation store degrades read paths but blocks the operations that grant or
 * transfer money-adjacent capability.</p>
 *
 * <p>The tier is declared at the operation with {@link FailClosedOnRevocation} rather than by
 * request path, because every GraphQL mutation arrives as {@code POST /graphql}. REST endpoints
 * use the same annotation, so both transports share one policy vocabulary.</p>
 *
 * <p>Depends only on {@link RevocationCheck}, so services backed by MongoDB and services backed
 * by the identity-service API enforce identical semantics.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class SensitiveOperationGuard {

    private final RevocationCheck revocationCheck;
    private final RevocationProperties properties;
    private final RevocationMetrics metrics;

    /**
     * Completes normally when the caller's token is definitively active.
     *
     * @param operation label for logs and the {@code operation} tag on
     *                  {@code identity_revocation_denials_total}
     * @return an empty {@link Mono}, or an error signal carrying {@link TokenRevokedException}
     *         or {@link RevocationUnavailableException}
     */
    public Mono<Void> assertNotRevoked(String operation) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        return SecurityContextUtils.getJwt()
                .flatMap(jwt -> revocationCheck
                        .check(jwt.getId(), jwt.getClaimAsString("sid"), jwt.getSubject())
                        .flatMap(decision -> apply(decision, operation, jwt.getSubject())))
                // No JWT in context. Method security governs whether the operation may run
                // anonymously; where it may, there is no token to resolve.
                .then();
    }

    private Mono<Void> apply(RevocationDecision decision, String operation, String subject) {
        return switch (decision) {
            case ACTIVE -> Mono.empty();

            case REVOKED -> {
                metrics.denied(operation, decision);
                log.warn("[Revocation] Refused {} for {} — the presented token is revoked",
                        operation, RevocationIdentifier.mask(subject));
                yield Mono.error(new TokenRevokedException());
            }

            case UNKNOWN -> {
                metrics.denied(operation, decision);
                log.error("[Revocation] Refused {} for {} — revocation state is unknown "
                                + "(fail-closed): neither the cache nor the durable store could "
                                + "answer.",
                        operation, RevocationIdentifier.mask(subject));
                yield Mono.error(new RevocationUnavailableException(operation));
            }
        };
    }
}
