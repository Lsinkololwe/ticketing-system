package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.User;
import com.pml.shared.error.ErrorCode;

import java.util.Optional;

/** What stops a known account from being ensured: the refusal codes of CONTRACT 4.3. */
public final class AccountGuard {

    private AccountGuard() {
    }

    /** Empty when the account may proceed (PROVISIONING or ACTIVE and not being merged). */
    public static Optional<ErrorCode> refusal(User account) {
        return refusal(AccountStates.of(account), account.getPendingKind());
    }

    public static Optional<ErrorCode> refusal(AccountState state, PendingKind pendingKind) {
        if (pendingKind == PendingKind.MERGING) {
            return Optional.of(ErrorCode.ACCOUNT_MERGING);
        }
        return switch (state) {
            case SUSPENDED -> Optional.of(ErrorCode.ACCOUNT_SUSPENDED);
            case MERGED, DELETED -> Optional.of(ErrorCode.ACCOUNT_NOT_ACTIVE);
            case PROVISIONING, ACTIVE -> Optional.empty();
        };
    }

    /**
     * Empty only for an account that is ACTIVE with nothing pending. This is the request-time rule: unlike
     * {@link #refusal(User)}, which lets a PROVISIONING account carry on being ensured, a profile is
     * shown only once the account is fully active.
     */
    public static Optional<ErrorCode> requestRefusal(User account) {
        Optional<ErrorCode> refusal = refusal(account);
        if (refusal.isPresent()) {
            return refusal;
        }
        return AccountStates.of(account) == AccountState.ACTIVE ? Optional.empty() : Optional.of(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    public static boolean isActive(User account) {
        return AccountStates.of(account) == AccountState.ACTIVE && account.getPendingKind() != PendingKind.MERGING;
    }
}
