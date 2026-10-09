package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · which account states may be ensured, and with which refusal code (CONTRACT 4.3)")
class AccountGuardTest {

    @Test
    @DisplayName("PROVISIONING and ACTIVE accounts proceed")
    void provisioningAndActiveProceed() {
        assertThat(AccountGuard.refusal(AccountState.PROVISIONING, null)).isEmpty();
        assertThat(AccountGuard.refusal(AccountState.ACTIVE, null)).isEmpty();
        assertThat(AccountGuard.refusal(AccountState.ACTIVE, PendingKind.CHANGING)).isEmpty();
    }

    @Test
    @DisplayName("a suspended account is refused with ACCOUNT_SUSPENDED")
    void suspended() {
        assertThat(AccountGuard.refusal(AccountState.SUSPENDED, null)).contains(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("merged and deleted accounts are not active")
    void mergedAndDeleted() {
        assertThat(AccountGuard.refusal(AccountState.MERGED, null)).contains(ErrorCode.ACCOUNT_NOT_ACTIVE);
        assertThat(AccountGuard.refusal(AccountState.DELETED, null)).contains(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("a merge in progress wins over every state")
    void mergingWins() {
        for (AccountState state : AccountState.values()) {
            assertThat(AccountGuard.refusal(state, PendingKind.MERGING))
                    .as("state %s while MERGING", state).contains(ErrorCode.ACCOUNT_MERGING);
        }
    }
}
