package com.pml.booking.web.graphql.resolver;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Money records point at the event so a screen can show its current name beside the recorded one. */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Escrow accounts and payouts reference their event for its current name")
class EventReferenceFieldsTest {

    private static DgsDataFetchingEnvironment source(Object source) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(source);
        return dfe;
    }

    @Test
    @DisplayName("an escrow account and a payout answer an Event reference by id")
    void referencesByEventId() {
        EventEscrowAccount escrow = new EventEscrowAccount();
        escrow.setEventId("6aca0c803ff088c1f746c8fa");
        PayoutRequest payout = new PayoutRequest();
        payout.setEventId("6aca0c803ff088c1f746c8fa");
        EventReferenceFields fields = new EventReferenceFields();

        assertThat(fields.ofEscrowAccount(source(escrow)))
                .isEqualTo(Map.of("__typename", "Event", "id", "6aca0c803ff088c1f746c8fa"));
        assertThat(fields.ofPayoutRequest(source(payout)))
                .isEqualTo(Map.of("__typename", "Event", "id", "6aca0c803ff088c1f746c8fa"));
    }

    @Test
    @DisplayName("no event id, no reference")
    void noEventNoReference() {
        assertThat(EventReferenceFields.reference(null)).isNull();
        assertThat(EventReferenceFields.reference(" ")).isNull();
    }
}
