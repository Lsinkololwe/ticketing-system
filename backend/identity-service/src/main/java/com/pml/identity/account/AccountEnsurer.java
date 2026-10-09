package com.pml.identity.account;

import reactor.core.publisher.Mono;

/**
 * Finds or creates the account that owns a verified contact (CONTRACT 12). Implemented by the
 * Temporal facade; the {@code /accounts/ensure} endpoint depends on this interface so tests can
 * substitute a fake.
 */
public interface AccountEnsurer {

    /** Idempotent for one proof: calling again never starts a second workflow. */
    Mono<EnsureResult> ensure(EnsureCommand command);
}
