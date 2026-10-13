package com.pml.identity.auth.challenge;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Durable copy of contact locks, so that losing Redis (a flush, a failover, a restart) does not
 * release a contact that was locked for guessing codes. It carries the contact key and the
 * challenge id only, never the contact itself.
 */
public interface LockMirror {

    /**
     * Writes down that a contact is locked until {@code lockedUntil}. {@code challengeId} is the
     * challenge whose wrong guesses caused the lock, so a later check that only knows the
     * challenge id can still find the lock.
     */
    Mono<Void> record(String contactKey, String challengeId, Instant lockedUntil, Instant at);

    /** Locks still in force at {@code now}, as (contactKey, lockedUntil). */
    Flux<ActiveLock> active(Instant now);

    /** The lock still in force for this contact at {@code now}, if any. Empty when there is none. */
    Mono<ActiveLock> activeForContact(String contactKey, Instant now);

    /** The lock still in force for the contact this challenge was issued to, if any. */
    Mono<ActiveLock> activeForChallenge(String challengeId, Instant now);

    record ActiveLock(String contactKey, Instant lockedUntil) {
    }
}
