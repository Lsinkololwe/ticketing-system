package com.pml.identity.account;

import reactor.core.publisher.Mono;

/**
 * Finds or creates the account that owns a contact a buyer has just proved, so that buyer can be signed
 * in. A returning buyer gets their existing account; a first-time buyer gets a new one. Implemented by the
 * Temporal facade; the {@code /accounts/ensure} endpoint depends on this interface so tests can
 * substitute a fake.
 */
public interface AccountEnsurer {

    /** Idempotent for one proof: calling again never starts a second workflow. */
    Mono<EnsureResult> ensure(EnsureCommand command);
}
