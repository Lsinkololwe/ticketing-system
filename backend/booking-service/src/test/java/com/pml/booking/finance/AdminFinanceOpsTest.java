package com.pml.booking.finance;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.CommissionRecord.CommissionStatus;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.AdminFinanceReads;
import com.pml.booking.service.ChargebackRecoveryOps;
import com.pml.booking.service.ChargebackRecoveryOps.Action;
import com.pml.booking.service.ChargebackService;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chargeback recovery, commission and settlement lists: money recorded once, amounts bounded, lists filtered and totalled. */
@Tag("L2")
@Tag("ET-ADM-003")
@DisplayName("ET-ADM-003 · chargeback recovery is recorded once and bounded; commission and settlement lists filter and total correctly")
class AdminFinanceOpsTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ChargebackRecoveryOps recovery;
    private static ChargebackService chargebacks;
    private static AccountingService accounting;
    private static AdminFinanceReads reads;

    @BeforeAll
    static void connect() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_admin_finance");
        chargebacks = Mockito.mock(ChargebackService.class);
        accounting = Mockito.mock(AccountingService.class);
        recovery = new ChargebackRecoveryOps(template, chargebacks, accounting,
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())));
        reads = new AdminFinanceReads(template);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void stubs() {
        Mockito.reset(chargebacks, accounting);
        when(accounting.recordChargeback(anyString(), anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenAnswer(call -> Mono.just(JournalEntry.builder().id("je-" + UUID.randomUUID()).build()));
    }

    private ChargebackRecord lost(String amount) {
        return template.insert(ChargebackRecord.builder().chargebackId("CB-" + UUID.randomUUID()).eventId("ev-1").ticketId("t-1")
                .originalAmount(new BigDecimal(amount)).chargebackAmount(new BigDecimal(amount)).chargebackFee(BigDecimal.ZERO)
                .currency("ZMW").status(ChargebackStatus.LOST).recoveryStatus(RecoveryStatus.IN_PROGRESS)
                .recoveredAmount(BigDecimal.ZERO).writtenOffAmount(BigDecimal.ZERO).fundSources(new ArrayList<>())
                .receivedAt(Instant.now()).build()).block();
    }

    private ChargebackRecord record(ChargebackRecord cb, String amount, ChargebackFundSource source, String reference) {
        return recovery.apply(cb.getId(), Action.RECORD_RECOVERY, amount == null ? null : new BigDecimal(amount), source, reference, "fin-1").block();
    }

    // ---- chargeback recovery -------------------------------------------------------------------

    @Test
    @DisplayName("a recovery is posted to the ledger and added to the recovered amount, with who recorded it")
    void recordsRecovery() {
        ChargebackRecord cb = lost("100.00");

        ChargebackRecord after = record(cb, "40.00", ChargebackFundSource.ORGANIZER_ESCROW, "ESC-1");

        assertThat(after.getRecoveredAmount()).isEqualByComparingTo("40.00");
        assertThat(after.getUnrecoveredAmount()).isEqualByComparingTo("60.00");
        assertThat(after.getRecoveryStatus()).isEqualTo(RecoveryStatus.IN_PROGRESS);
        assertThat(after.getRecoveredBy()).isEqualTo("fin-1");
        assertThat(after.getRecoveryJournalEntryId()).startsWith("je-");
        assertThat(after.getFundSources()).containsExactly(ChargebackFundSource.ORGANIZER_ESCROW);
    }

    @Test
    @DisplayName("the same reference submitted twice is one recovery: no second ledger entry, no double count")
    void repeatedReferenceIsOnce() {
        ChargebackRecord cb = lost("100.00");
        record(cb, "40.00", ChargebackFundSource.ORGANIZER_ESCROW, "ESC-1");
        ChargebackRecord again = record(cb, "40.00", ChargebackFundSource.ORGANIZER_ESCROW, "ESC-1");

        assertThat(again.getRecoveredAmount()).isEqualByComparingTo("40.00");
        verify(accounting, times(1)).recordChargeback(anyString(), anyString(), anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("more than is unrecovered, a source that is not recoverable, a bad amount, a blank reference and a chargeback not lost are refused")
    void refusals() {
        ChargebackRecord cb = lost("100.00");
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, new BigDecimal("100.01"), ChargebackFundSource.PLATFORM_RESERVE, "R1", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.CHARGEBACK_STATE_INVALID);
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, BigDecimal.TEN, ChargebackFundSource.WRITE_OFF, "R2", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, null, ChargebackFundSource.PLATFORM_RESERVE, "R3", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, new BigDecimal("-1"), ChargebackFundSource.PLATFORM_RESERVE, "R3", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, new BigDecimal("1.005"), ChargebackFundSource.PLATFORM_RESERVE, "R3", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, BigDecimal.TEN, ChargebackFundSource.PLATFORM_RESERVE, " ", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        ChargebackRecord received = template.insert(ChargebackRecord.builder().chargebackId("CB-" + UUID.randomUUID()).chargebackAmount(BigDecimal.TEN)
                .status(ChargebackStatus.RECEIVED).recoveryStatus(RecoveryStatus.NOT_STARTED).recoveredAmount(BigDecimal.ZERO).fundSources(new ArrayList<>()).build()).block();
        assertThat(refusal(recovery.apply(received.getId(), Action.START_RECOVERY, null, null, null, "fin-1")).errorCode()).isEqualTo(ErrorCode.CHARGEBACK_STATE_INVALID);
        assertThat(refusal(recovery.apply("cb-never", Action.START_RECOVERY, null, null, null, "fin-1")).errorCode()).isEqualTo(ErrorCode.CHARGEBACK_STATE_INVALID);
        verify(accounting, Mockito.never()).recordChargeback(anyString(), anyString(), anyString(), any(), any(), anyString(), anyString());
        assertThat(template.findById(cb.getId(), ChargebackRecord.class).block().getRecoveredAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("recovering the whole amount closes it as RECOVERED, and nothing more can be recorded")
    void fullRecovery() {
        ChargebackRecord cb = lost("100.00");
        record(cb, "60.00", ChargebackFundSource.ORGANIZER_ESCROW, "ESC-1");
        ChargebackRecord done = record(cb, "40.00", ChargebackFundSource.PLATFORM_RESERVE, "RES-1");

        assertThat(done.getRecoveryStatus()).isEqualTo(RecoveryStatus.RECOVERED);
        assertThat(done.getFundSources()).containsExactlyInAnyOrder(ChargebackFundSource.ORGANIZER_ESCROW, ChargebackFundSource.PLATFORM_RESERVE);
        assertThat(refusal(recovery.apply(cb.getId(), Action.RECORD_RECOVERY, BigDecimal.ONE, ChargebackFundSource.PLATFORM_RESERVE, "X", "fin-1")).errorCode())
                .isEqualTo(ErrorCode.CHARGEBACK_STATE_INVALID);
    }

    @Test
    @DisplayName("two operators recording different recoveries at once cannot together recover more than was lost")
    void simultaneousRecoveriesAreBounded() {
        for (int round = 0; round < 5; round++) {
            ChargebackRecord cb = lost("100.00");
            Flux.just("A", "B")
                    .flatMap(ref -> recovery.apply(cb.getId(), Action.RECORD_RECOVERY, new BigDecimal("60.00"), ChargebackFundSource.ORGANIZER_ESCROW, ref, "fin-" + ref)
                            .<Object>map(r -> r).onErrorResume(e -> Mono.just(e)))
                    .collectList().block();

            ChargebackRecord stored = template.findById(cb.getId(), ChargebackRecord.class).block();
            assertThat(stored.getRecoveredAmount()).as("round %d", round).isLessThanOrEqualTo(new BigDecimal("100.00"));
        }
    }

    @Test
    @DisplayName("starting recovery starts it once; asking again leaves it as it is")
    void startRecovery() {
        ChargebackRecord cb = template.insert(ChargebackRecord.builder().chargebackId("CB-" + UUID.randomUUID()).chargebackAmount(BigDecimal.TEN)
                .status(ChargebackStatus.LOST).recoveryStatus(RecoveryStatus.NOT_STARTED).recoveredAmount(BigDecimal.ZERO).fundSources(new ArrayList<>()).build()).block();
        when(chargebacks.startRecovery(cb.getId())).thenReturn(Mono.just(cb));

        recovery.apply(cb.getId(), Action.START_RECOVERY, null, null, null, "fin-1").block();
        verify(chargebacks, times(1)).startRecovery(cb.getId());

        ChargebackRecord inProgress = lost("10.00");
        recovery.apply(inProgress.getId(), Action.START_RECOVERY, null, null, null, "fin-1").block();
        verify(chargebacks, times(1)).startRecovery(anyString());
    }

    // ---- commission and settlements ------------------------------------------------------------

    @Test
    @DisplayName("commissionRecords filters by status, event and organization and totals each status")
    void commissions() {
        String event = "ev-" + UUID.randomUUID();
        String organization = "org-" + UUID.randomUUID();
        commission(event, organization, CommissionStatus.PENDING, "5.00");
        commission(event, organization, CommissionStatus.PENDING, "7.50");
        commission(event, organization, CommissionStatus.EARNED, "10.00");
        commission(event, organization, CommissionStatus.CANCELLED, "2.00");
        commission("ev-other", "org-other", CommissionStatus.EARNED, "99.00");

        var page = reads.commissions(new AdminFinanceReads.CommissionFilter(null, event, organization, null, null, null), null).block();

        assertThat(page.data()).hasSize(4);
        assertThat(page.totals().pending()).isEqualByComparingTo("12.50");
        assertThat(page.totals().earned()).isEqualByComparingTo("10.00");
        assertThat(page.totals().cancelled()).isEqualByComparingTo("2.00");
        assertThat(page.totals().clawedBack()).isEqualByComparingTo("0");
        assertThat(reads.commissions(new AdminFinanceReads.CommissionFilter(CommissionStatus.PENDING, event, null, null, null, null), null).block().data()).hasSize(2);
        assertThat(refusal(reads.commissions(new AdminFinanceReads.CommissionFilter(null, event, null, null, Instant.now(), Instant.now().minusSeconds(10)), null)).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("gatewaySettlements lists settlement entries only, with gross, fee and net read from the ledger lines")
    void settlements() {
        String id = "SETTLE-" + UUID.randomUUID();
        template.insert(JournalEntry.builder().entryNumber("JE-2026-10-9" + (int) (Math.random() * 9000 + 1000)).correlationId(id).entryDate(java.time.LocalDate.of(2026, 10, 4))
                .type(JournalEntryType.STANDARD).status(JournalEntryStatus.POSTED).postedAt(Instant.parse("2026-10-04T12:00:00Z")).currency("ZMW")
                .metadata(Map.of("transactionType", "GATEWAY_SETTLEMENT", "settlementDate", "2026-10-04T00:00:00Z", "bankReference", "BANK-77"))
                .lines(List.of(JournalLine.debit("1011", "Bank", new BigDecimal("97.00"), "net"), JournalLine.debit("5010", "Fees", new BigDecimal("3.00"), "fee"),
                        JournalLine.credit("1021", "Receivable", new BigDecimal("100.00"), "gross"))).build()).block();
        template.insert(JournalEntry.builder().entryNumber("JE-2026-10-8" + (int) (Math.random() * 9000 + 1000)).correlationId("not-a-settlement")
                .type(JournalEntryType.STANDARD).status(JournalEntryStatus.POSTED).metadata(Map.of("transactionType", "REFUND")).lines(List.of()).build()).block();

        var page = reads.settlements(new AdminFinanceReads.SettlementFilter(null, null, id), null).block();

        assertThat(page.data()).hasSize(1);
        var settlement = page.data().get(0);
        assertThat(settlement.settlementId()).isEqualTo(id);
        assertThat(settlement.grossAmount()).isEqualByComparingTo("100.00");
        assertThat(settlement.feeAmount()).isEqualByComparingTo("3.00");
        assertThat(settlement.netAmount()).isEqualByComparingTo("97.00");
        assertThat(settlement.bankReference()).isEqualTo("BANK-77");
        assertThat(settlement.netAmount().add(settlement.feeAmount())).isEqualByComparingTo(settlement.grossAmount());
        assertThat(reads.settlements(null, null).block().data()).extracting(AdminFinanceReads.GatewaySettlement::settlementId).doesNotContain("not-a-settlement");
    }

    private static void commission(String event, String organization, CommissionStatus status, String amount) {
        template.insert(CommissionRecord.builder().ticketId("t-" + UUID.randomUUID()).eventId(event).organizationId(organization)
                .amount(new BigDecimal(amount)).ticketPrice(new BigDecimal(amount).multiply(BigDecimal.TEN)).status(status).createdAt(Instant.now()).build()).block();
    }
}
