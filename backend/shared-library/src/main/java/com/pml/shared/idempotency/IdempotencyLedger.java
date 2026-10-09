package com.pml.shared.idempotency;

import reactor.core.publisher.Mono;

/**
 * The durable authority behind {@link IdempotencyGuard}.
 *
 * <p>Redis answers quickly and forgets; this is what remembers. A claim is a single insert guarded
 * by a unique constraint, so when two callers present one key at the same instant the database,
 * not application code, decides which one proceeds. Losing Redis therefore can never cause a
 * second application of an operation.</p>
 */
public interface IdempotencyLedger {

    /** The recorded state of one scoped key. {@code response} is null until the operation completes. */
    record Entry(String fingerprint, boolean completed, String response) {
    }

    /** Whether the caller now owns the key, and the entry that decides everything else. */
    record Claim(boolean won, Entry entry) {
    }

    /** Inserts a pending entry, or returns the one that already exists. */
    Mono<Claim> claim(String scope, String key, String fingerprint);

    /** Reads the current entry, empty if the key is unknown or has expired. */
    Mono<Entry> find(String scope, String key);

    /** Records the operation's serialised response against a claimed key. */
    Mono<Void> complete(String scope, String key, String response);

    /** Withdraws a claim whose operation failed, so a retry is free to apply. */
    Mono<Void> release(String scope, String key);
}
