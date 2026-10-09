package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.service.AccountLifecycleRules;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R10 · a person's own deletion request: the grace period and who may ask")
class AccountLifecycleRulesTest {

    @Test
    void graceIsThirtyDays() {
        assertThat(AccountLifecycleRules.scheduledFor(Instant.parse("2026-10-04T08:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-03T08:00:00Z"));
    }

    @Test
    void onlyALiveAccountMayAsk() {
        AccountLifecycleRules.requireCanRequestDeletion(AccountState.ACTIVE);
        for (AccountState state : new AccountState[]{AccountState.SUSPENDED, AccountState.DELETED, AccountState.MERGED, AccountState.PROVISIONING}) {
            assertThatThrownBy(() -> AccountLifecycleRules.requireCanRequestDeletion(state))
                    .isInstanceOfSatisfying(DomainRefusal.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE));
        }
    }

    @Test
    void dueOnlyOnceTheGracePeriodHasRunOut() {
        Instant scheduled = Instant.parse("2026-11-03T08:00:00Z");
        assertThat(AccountLifecycleRules.due(scheduled, scheduled.minusSeconds(1))).isFalse();
        assertThat(AccountLifecycleRules.due(scheduled, scheduled)).isTrue();
        assertThat(AccountLifecycleRules.due(null, scheduled)).isFalse();
    }
}
