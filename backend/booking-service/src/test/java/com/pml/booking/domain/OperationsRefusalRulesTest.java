package com.pml.booking.domain;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.HolderMessage.Status;
import com.pml.booking.service.ChargebackRecoveryOps;
import com.pml.booking.service.PlatformTransferService;
import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.workflow.refund.RefundRules;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Small rules found by testing the money paths, kept as plain tests so they cannot regress quietly. */
@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("Operations rules · open-refund conflict, delivery counting, recovery references, platform transfer checks")
class OperationsRefusalRulesTest {

    @Test
    @DisplayName("asking again for the same sum is the open request; a different sum is a different ask; no sum asks for none")
    void openRefundConflict() {
        assertThat(RefundRules.asksForDifferentAmount(new BigDecimal("20.00"), new BigDecimal("20"))).isFalse();
        assertThat(RefundRules.asksForDifferentAmount(new BigDecimal("20.00"), new BigDecimal("25.00"))).isTrue();
        assertThat(RefundRules.asksForDifferentAmount(null, new BigDecimal("25.00"))).isFalse();
        assertThat(RefundRules.asksForDifferentAmount(new BigDecimal("20.00"), null)).isFalse();
        assertThat(RefundRules.openRefundMessage(new BigDecimal("25.00"))).contains("25.00").contains("withdraw it");
    }

    @Test
    @DisplayName("delivery is what identity counted, capped at the batch; a repeat of a sent batch counts as sent; the status follows the totals")
    void delivery() {
        assertThat(HolderMessageRules.delivered("QUEUED", 150, 200)).isEqualTo(150);
        assertThat(HolderMessageRules.delivered("QUEUED", 500, 200)).as("never more than was asked").isEqualTo(200);
        assertThat(HolderMessageRules.delivered("QUEUED", -3, 200)).isZero();
        assertThat(HolderMessageRules.delivered("DUPLICATE", 0, 200)).isEqualTo(200);
        assertThat(HolderMessageRules.delivered("NO_VERIFIED_CONTACT", 0, 200)).isZero();
        assertThat(HolderMessageRules.statusOf(0, 10)).isEqualTo(Status.FAILED);
        assertThat(HolderMessageRules.statusOf(4, 10)).isEqualTo(Status.PARTIAL);
        assertThat(HolderMessageRules.statusOf(10, 10)).isEqualTo(Status.SENT);
    }

    @Test
    @DisplayName("a recovery is well formed with a recoverable source, a positive amount of whole ngwee and a reference; the reference is remembered")
    void recoveryChecks() {
        assertThat(ChargebackRecoveryOps.checkRecording(BigDecimal.TEN, ChargebackFundSource.ORGANIZER_ESCROW, "R-1")).isNull();
        assertThat(ChargebackRecoveryOps.checkRecording(BigDecimal.TEN, ChargebackFundSource.WRITE_OFF, "R-1").errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(ChargebackRecoveryOps.checkRecording(BigDecimal.TEN, null, "R-1")).isNotNull();
        assertThat(ChargebackRecoveryOps.checkRecording(BigDecimal.ZERO, ChargebackFundSource.PLATFORM_RESERVE, "R-1")).isNotNull();
        assertThat(ChargebackRecoveryOps.checkRecording(new BigDecimal("1.001"), ChargebackFundSource.PLATFORM_RESERVE, "R-1")).isNotNull();
        assertThat(ChargebackRecoveryOps.checkRecording(BigDecimal.TEN, ChargebackFundSource.PLATFORM_RESERVE, " ")).isNotNull();

        ChargebackRecord record = ChargebackRecord.builder().internalNotes("Accepted: ok\n" + ChargebackRecoveryOps.marker(" R-1 ") + " 10 from X by y").build();
        assertThat(ChargebackRecoveryOps.alreadyRecorded(record, "R-1")).isTrue();
        assertThat(ChargebackRecoveryOps.alreadyRecorded(record, "R-2")).isFalse();
        assertThat(ChargebackRecoveryOps.alreadyRecorded(ChargebackRecord.builder().build(), "R-1")).isFalse();
    }

    @Test
    @DisplayName("platform money moves between operating and reserve only, in whole ngwee, with a reason; above the limit a second person is needed")
    void platformTransferChecks() {
        String reason = "month end reserve top up";
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, BigDecimal.TEN, reason)).isNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.RESERVE, PlatformAccountType.OPERATING, new BigDecimal("0.01"), reason)).isNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.OPERATING, BigDecimal.TEN, reason)).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.TAX_HOLDING, PlatformAccountType.OPERATING, BigDecimal.TEN, reason)).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, null, BigDecimal.TEN, reason)).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, new BigDecimal("1.005"), reason)).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, BigDecimal.TEN, "short")).isNotNull();
        var service = new PlatformTransferService(null, null, null, null, java.time.Clock.systemUTC(), new BigDecimal("1000.00"));
        assertThat(service.needsSecondPerson(new BigDecimal("1000.00"))).isFalse();
        assertThat(service.needsSecondPerson(new BigDecimal("1000.01"))).isTrue();
    }
}
