package com.pml.identity.security.revocation;

import reactor.core.publisher.Mono;

/**
 * Ends every live session of an account, with the tokens minted for them. The narrow face of
 * {@link AccessRevocationService} for the flows after which the person is expected to sign in
 * again straight away: the sessions that exist now are revoked, never the whole subject, so the
 * fresh sign-in is not refused.
 */
@FunctionalInterface
public interface AccessCutter {

    /**
     * @param accountId the account whose sessions end
     * @param trigger   why, recorded on the revocation and its audit row
     * @param actorId   who caused it, or null for the platform itself
     */
    Mono<Void> cutSessions(String accountId, RevocationTrigger trigger, String actorId);
}
