package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.AccountStatus;
import com.pml.identity.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · the lifecycle state, the legacy status and the active flag move together (CONTRACT 2)")
class AccountStatesTest {

    private static User legacy(AccountStatus status) {
        User user = new User();
        user.setAccountStatus(status);
        return user;
    }

    @Test
    @DisplayName("legacy statuses map as the contract says")
    void legacyMapping() {
        assertThat(AccountStates.of(legacy(AccountStatus.ACTIVE))).isEqualTo(AccountState.ACTIVE);
        assertThat(AccountStates.of(legacy(AccountStatus.PENDING_DELETION))).isEqualTo(AccountState.ACTIVE);
        assertThat(AccountStates.of(legacy(AccountStatus.INACTIVE))).isEqualTo(AccountState.SUSPENDED);
        assertThat(AccountStates.of(legacy(AccountStatus.LOCKED))).isEqualTo(AccountState.SUSPENDED);
        assertThat(AccountStates.of(legacy(AccountStatus.SUSPENDED))).isEqualTo(AccountState.SUSPENDED);
        assertThat(AccountStates.of(legacy(AccountStatus.PENDING_VERIFICATION))).isEqualTo(AccountState.PROVISIONING);
        assertThat(AccountStates.of(new User())).as("no status at all is an old, active account").isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("the new status field wins over the legacy one")
    void statusWins() {
        User user = legacy(AccountStatus.ACTIVE);
        user.setStatus(AccountState.SUSPENDED);
        assertThat(AccountStates.of(user)).isEqualTo(AccountState.SUSPENDED);
    }

    @Test
    @DisplayName("apply sets status, legacy status and active together, for every state")
    void applyKeepsThreeFieldsInStep() {
        for (AccountState state : AccountState.values()) {
            User user = new User();
            AccountStates.apply(user, state);
            assertThat(user.getStatus()).isEqualTo(state);
            assertThat(user.getAccountStatus()).isEqualTo(AccountStates.legacyStatus(state));
            assertThat(user.isActive()).as("active flag for %s", state).isEqualTo(state == AccountState.ACTIVE);
            assertThat(AccountStates.of(user)).isEqualTo(state);
        }
    }
}
