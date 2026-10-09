package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.AccountStatus;
import com.pml.identity.domain.model.User;

/**
 * The one place the new lifecycle {@link AccountState} and the legacy {@link AccountStatus} plus
 * {@code active} flag are kept in step (CONTRACT 2).
 *
 * <p>Three fields describe one fact, and different parts of the platform read different ones. A
 * transition that sets one and not the others yields an account that is suspended on its profile
 * page and active in every list, so every write goes through {@link #apply}.</p>
 */
public final class AccountStates {

    private AccountStates() {
    }

    /** The state of an account, reading the legacy status for a document written before the backfill. */
    public static AccountState of(User user) {
        if (user.getStatus() != null) {
            return user.getStatus();
        }
        AccountStatus legacy = user.getAccountStatus();
        if (legacy == null) {
            return AccountState.ACTIVE;
        }
        return switch (legacy) {
            case ACTIVE, PENDING_DELETION -> AccountState.ACTIVE;
            case INACTIVE, LOCKED, SUSPENDED -> AccountState.SUSPENDED;
            case PENDING_VERIFICATION -> AccountState.PROVISIONING;
        };
    }

    public static AccountStatus legacyStatus(AccountState state) {
        return switch (state) {
            case PROVISIONING -> AccountStatus.PENDING_VERIFICATION;
            case ACTIVE -> AccountStatus.ACTIVE;
            case SUSPENDED -> AccountStatus.SUSPENDED;
            case MERGED, DELETED -> AccountStatus.INACTIVE;
        };
    }

    /** Sets {@code status}, the legacy {@code accountStatus} and {@code active} together. */
    public static void apply(User user, AccountState state) {
        user.setStatus(state);
        user.setAccountStatus(legacyStatus(state));
        user.setActive(state == AccountState.ACTIVE);
    }
}
