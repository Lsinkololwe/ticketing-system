package com.pml.identity.account;

import reactor.core.publisher.Mono;

/**
 * Opens a platform staff account. The one way a staff person is created from the console: the
 * implementation runs it as a durable process, so a restart half way through finishes it or removes
 * it, and never leaves a person who exists in the login system and not in the platform's records.
 */
public interface StaffAccountCreator {

    /**
     * Creates the account, or returns the one an earlier identical request is still creating.
     *
     * <p>Who may call is decided by the caller of this method (see {@link StaffRolePolicy}); the
     * implementation repeats the super-administrator check before it starts anything. Refused with
     * {@code STAFF_CREATION_IN_PROGRESS} when the account is not finished within the wait; asking again
     * returns the same account.</p>
     *
     * <p>The answer carries the one-time temporary password only when this call is the one whose password
     * was applied to the new person. A request repeated after the account was finished, or one that met
     * another request's execution, returns the account without a password: it is shown once, and a lost
     * one is replaced with a reset.</p>
     */
    Mono<StaffAccountPassword> create(StaffAccountRequest request);
}
