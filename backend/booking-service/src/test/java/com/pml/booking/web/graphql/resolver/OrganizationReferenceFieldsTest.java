package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A money record answers its organization as a reference only; the name is identity's. Flat methods: F-055. */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Escrow accounts and payouts reference their organization")
class OrganizationReferenceFieldsTest {

    private final OrganizationReferenceFields fields = new OrganizationReferenceFields();

    private static DgsDataFetchingEnvironment sourced(Object source) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(source);
        return dfe;
    }

    @Test
    @DisplayName("an escrow account answers the Organization entity reference and nothing else")
    void escrowAccountReference() {
        EventEscrowAccount account = new EventEscrowAccount();
        account.setOrganizationId("org-1");
        assertThat(fields.ofEscrowAccount(sourced(account)))
                .containsOnly(Map.entry("__typename", "Organization"), Map.entry("id", "org-1"));
    }

    @Test
    @DisplayName("a payout request answers the reference for its organization")
    void payoutReference() {
        PayoutRequest payout = new PayoutRequest();
        payout.setOrganizationId("org-2");
        assertThat(fields.ofPayoutRequest(sourced(payout))).containsEntry("id", "org-2");
    }

    @Test
    @DisplayName("a record with no organization id answers null rather than a reference to nothing")
    void missingOrganizationIsNull() {
        assertThat(fields.ofEscrowAccount(sourced(new EventEscrowAccount()))).isNull();
        PayoutRequest blank = new PayoutRequest();
        blank.setOrganizationId("  ");
        assertThat(fields.ofPayoutRequest(sourced(blank))).isNull();
        assertThat(OrganizationReferenceFields.reference(null)).isNull();
    }
}
