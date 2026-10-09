package com.pml.identity.service;

import com.pml.identity.domain.enums.AccountState;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Pure rules for a person's own account-deletion request. */
public final class AccountLifecycleRules {

    /** How long a deletion request stays cancellable. */
    public static final Duration DELETION_GRACE = Duration.ofDays(30);

    private AccountLifecycleRules() {
    }

    public static Instant scheduledFor(Instant requestedAt) {
        return requestedAt.plus(DELETION_GRACE);
    }

    /** Only a live account can ask for its own deletion. */
    public static void requireCanRequestDeletion(AccountState state) {
        if (state != AccountState.ACTIVE) {
            throw new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "account is " + state,
                    Map.of());
        }
    }

    /**
     * Whether a recorded request has run out its grace period. The execution step (the
     * {@code AccountService#delete} soft delete) is allowed only from this point.
     */
    public static boolean due(Instant scheduledFor, Instant now) {
        return scheduledFor != null && !scheduledFor.isAfter(now);
    }
}
