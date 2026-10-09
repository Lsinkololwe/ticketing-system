package com.pml.booking.workflow.chargeback;

import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.constants.PlatformTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004-R8 · which chargeback decisions each status admits, and where its deadline falls")
class ChargebackRulesTest {

    @Test
    @DisplayName("review starts from RECEIVED; accept and dispute from RECEIVED or UNDER_REVIEW; an outcome only for DISPUTED")
    void decisionsByStatus() {
        for (ChargebackStatus status : ChargebackStatus.values()) {
            assertThat(ChargebackRules.canReview(status)).isEqualTo(status == ChargebackStatus.RECEIVED);
            assertThat(ChargebackRules.canDecide(status))
                    .isEqualTo(status == ChargebackStatus.RECEIVED || status == ChargebackStatus.UNDER_REVIEW);
            assertThat(ChargebackRules.awaitsOutcome(status)).isEqualTo(status == ChargebackStatus.DISPUTED);
        }
    }

    @Test
    @DisplayName("the response deadline is the start of its day in Lusaka, and an absent one is zero")
    void deadlineIsStartOfDayInLusaka() {
        ChargebackRecord record = ChargebackRecord.builder().id("r").chargebackId("c").eventId("e")
                .status(ChargebackStatus.RECEIVED).responseDeadline(LocalDate.of(2026, 10, 1)).build();

        assertThat(ChargebackActivitiesImpl.view(record).responseDeadlineMillis())
                .isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay(PlatformTime.ZONE).toInstant().toEpochMilli());
        assertThat(ChargebackActivitiesImpl.view(record.toBuilder().responseDeadline(null).build()).responseDeadlineMillis())
                .isZero();
    }
}
