package com.pml.booking.infrastructure.temporal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015-R2 · booking workflow ids are derived from business ids, never generated")
class WorkflowIdsTest {

    @Test
    @DisplayName("each id is its §4 prefix and the business identifier, and the same input gives the same id")
    void idsAreDeterministic() {
        assertThat(WorkflowIds.payout("escrow-1")).isEqualTo("payout/escrow-1").isEqualTo(WorkflowIds.payout("escrow-1"));
        assertThat(WorkflowIds.purchase("res-1")).isEqualTo("purchase/res-1");
        assertThat(WorkflowIds.bankVerification("bank-1")).isEqualTo("bank-verification/bank-1");
        assertThat(WorkflowIds.eventFinance("event-1")).isEqualTo("event-finance/event-1");
        assertThat(WorkflowIds.reconciliation("PROVIDER", "2026-09-13")).isEqualTo("recon/PROVIDER/2026-09-13");
    }

    @Test
    @DisplayName("a missing business identifier is refused rather than producing a shared id")
    void blankIdentifiersAreRefused() {
        assertThatThrownBy(() -> WorkflowIds.payout(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowIds.refund(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
