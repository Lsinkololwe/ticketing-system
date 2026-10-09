package com.pml.booking.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Which account codes the ledger opens for itself: an event's escrow account, and nothing else. */
@Tag("L1")
@Tag("ET-FIN-001")
@DisplayName("ET-FIN-001 · only an event's escrow account is opened on first posting")
class ChartOfAccountsEventEscrowTest {

    @Test
    @DisplayName("2010- and an event's short id is an event escrow account; every other code is left to be refused if unknown")
    void eventEscrowCodes() {
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("2010-abc12345")).isTrue();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("2010-ev-1")).isTrue();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("2010-")).isFalse();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("2010")).isFalse();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("1011")).isFalse();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("4010-abc")).isFalse();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("2010-abc def")).as("no spaces").isFalse();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode("2010-../x")).as("no path-like codes").isFalse();
        assertThat(ChartOfAccountsServiceImpl.isEventEscrowCode(null)).isFalse();
    }
}
