package com.pml.identity.auth.challenge;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Durable copy of contact locks, so a Redis loss does not release a locked contact
 * (ET-IDN-001-R3). Carries the contact key only - never the contact.
 */
public interface LockMirror {

    Mono<Void> record(String contactKey, Instant lockedUntil, Instant at);

    /** Locks still in force at {@code now}, as (contactKey, lockedUntil). */
    Flux<ActiveLock> active(Instant now);

    record ActiveLock(String contactKey, Instant lockedUntil) {
    }
}
