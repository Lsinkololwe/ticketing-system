package com.pml.booking.payout;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.EscrowTransaction;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.ChargebackRecordRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.PayoutSettlementService;
import com.pml.booking.workflow.payout.PayoutWorkflow.Decision;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.booking.workflow.payout.PayoutWorkflow.View;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The MongoDB half of a payout against a real replica set.
 *
 * <p>The journal is a stub that answers with an entry id; what is asserted here is the escrow
 * document, the request's status, the outbox and how many times each entry was asked for. The
 * workflow's sequence over these same steps is {@code PayoutWorkflowTest}.
 */
@Tag("L2")
@Tag("ET-FIN-003")
@DisplayName("ET-FIN-003-R6 · settlement writes debit once, reverse exactly, and complete with one envelope")
class PayoutSettlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    private static final String ESCROW = "escrow-settle";
    private static final String BANK = "bank-settle";
    private static final String REQUEST = "5b1c9f1e-0d1a-4bb8-9c2e-7f5a3e9d2a11";
    private static final String ORGANIZER = "organizer-settle";
    private static final String FINANCE = "finance-settle";
    private static final BigDecimal BALANCE = new BigDecimal("500.00");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ChargebackRecordRepository chargebacks;
    private static Outbox outbox;
    private static TransactionalOperator transaction;
    private static Clock clock;

    private AccountingService accounting;
    private PayoutSettlementService settlement;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_payout_settlement"));
        chargebacks = new ReactiveMongoRepositoryFactory(template).getRepository(ChargebackRecordRepository.class);
        clock = TestClock.frozenAt(NOW);
        outbox = new Outbox(template, "booking_outbox", clock);
        transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), PayoutRequest.class).block();
        template.remove(new Query(), EventEscrowAccount.class).block();
        template.remove(new Query(), BankAccount.class).block();
        template.remove(new Query(), ChargebackRecord.class).block();
        template.remove(new Query(), Document.class, "booking_outbox").block();

        EventEscrowAccount escrow = EventEscrowAccount.create("event-settle", ORGANIZER, NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(30)));
        escrow.setId(ESCROW);
        escrow.setOrganizationId("organization-settle");
        escrow.setStatus(EscrowStatus.HOLD);
        escrow.setHoldUntil(NOW.minus(Duration.ofDays(1)));
        escrow.setCurrentBalance(BALANCE);
        escrow.setTotalCredited(BALANCE);
        template.save(escrow).block();
        template.save(bank(true)).block();

        accounting = Mockito.mock(AccountingService.class);
        when(accounting.recordPayout(anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> Mono.just(entry(call.getArgument(0))));
        when(accounting.recordPayoutReversal(anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> Mono.just(entry(call.getArgument(0))));
        when(accounting.recordPayoutDisbursement(anyString(), any(), any(), any()))
                .thenAnswer(call -> Mono.just(entry(call.getArgument(0))));

        settlement = new PayoutSettlementService(template, transaction, outbox, accounting, chargebacks,
                new BigDecimal("10.00"), clock);
    }

    @Test
    @DisplayName("R2, R5 · a request is for the full balance, and stores only the masked account number")
    void aRequestIsForTheFullBalance() {
        View view = settlement.createRequest(submit(BALANCE)).block();

        PayoutRequest stored = request();
        assertThat(view.status()).isEqualTo(PayoutRequestStatus.PENDING);
        assertThat(stored.getAccountNumber()).isEqualTo("****4567");
        assertThat(stored.getSettledAmount()).isEqualByComparingTo(stored.getRequestedAmount());
        assertThat(stored.getOrganizationId()).isEqualTo("organization-settle");
    }

    @Test
    @DisplayName("R4 · an unverified account is refused, and nothing is saved")
    void anUnverifiedAccountIsRefused() {
        template.remove(new Query(), BankAccount.class).block();
        template.save(bank(false)).block();

        assertRefused(() -> settlement.createRequest(submit(BALANCE)).block(), ErrorCode.BANK_ACCOUNT_NOT_VERIFIED);
        assertThat(template.count(new Query(), PayoutRequest.class).block()).isZero();
    }

    @Test
    @DisplayName("R2 · a partial amount is refused")
    void aPartialAmountIsRefused() {
        assertRefused(() -> settlement.createRequest(submit(new BigDecimal("100.00"))).block(), ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("R3 · the requester cannot approve their own request")
    void theRequesterCannotApprove() {
        settlement.createRequest(submit(BALANCE)).block();

        assertRefused(() -> settlement.approve(new Decision(REQUEST, ORGANIZER, null)).block(), ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(request().getStatus()).isEqualTo(PayoutRequestStatus.PENDING);
    }

    @Test
    @DisplayName("R1 · a dispute opened after the request makes approval refuse")
    void anOpenDisputeRefusesApproval() {
        settlement.createRequest(submit(BALANCE)).block();
        template.save(ChargebackRecord.builder().id("chargeback-settle").eventId("event-settle")
                .status(ChargebackStatus.RECEIVED).build()).block();

        assertRefused(() -> settlement.approve(new Decision(REQUEST, FINANCE, null)).block(), ErrorCode.PAYOUT_WINDOW_NOT_OPEN);
        assertThat(request().getStatus()).isEqualTo(PayoutRequestStatus.PENDING);
    }

    @Test
    @DisplayName("R6 · beginning settlement twice under one payout id debits the escrow once")
    void beginningTwiceDebitsOnce() {
        approved();

        settlement.beginSettlement(REQUEST, "pp-1").block();
        View second = settlement.beginSettlement(REQUEST, "pp-1").block();

        EventEscrowAccount escrow = escrow();
        assertThat(second.status()).isEqualTo(PayoutRequestStatus.PROCESSING);
        assertThat(second.attempts()).isEqualTo(1);
        assertThat(escrow.getCurrentBalance()).isEqualByComparingTo("0.00");
        assertThat(payoutRows(escrow, "PAYOUT")).hasSize(1);
        verify(accounting, times(1)).recordPayout(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("R6 · a failed transfer restores the escrow exactly, once, and reopens it for payout")
    void aFailureRestoresTheEscrowExactly() {
        approved();
        settlement.beginSettlement(REQUEST, "pp-1").block();

        settlement.failSettlement(REQUEST, "INSUFFICIENT_BALANCE", "provider float empty").block();
        View again = settlement.failSettlement(REQUEST, "INSUFFICIENT_BALANCE", "provider float empty").block();

        EventEscrowAccount escrow = escrow();
        assertThat(again.status()).isEqualTo(PayoutRequestStatus.FAILED);
        assertThat(escrow.getCurrentBalance()).isEqualByComparingTo(BALANCE);
        assertThat(escrow.getTotalDebited()).isEqualByComparingTo("0.00");
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.PAYOUT_ELIGIBLE);
        assertThat(payoutRows(escrow, "PAYOUT_REVERSAL")).hasSize(1);
        verify(accounting, times(1)).recordPayoutReversal(anyString(), any(), any(), any(), any(), any());
        assertThat(request().getFailureCategory()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("R7 · a retry after failure debits again under a new payout id")
    void aRetryDebitsAgain() {
        approved();
        settlement.beginSettlement(REQUEST, "pp-1").block();
        settlement.failSettlement(REQUEST, "INSUFFICIENT_BALANCE", "provider float empty").block();

        View retried = settlement.beginSettlement(REQUEST, "pp-2").block();

        assertThat(retried.attempts()).isEqualTo(2);
        assertThat(escrow().getCurrentBalance()).isEqualByComparingTo("0.00");
        assertThat(request().getPawaPayPayoutId()).isEqualTo("pp-2");
        verify(accounting, times(2)).recordPayout(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("R8 · completion stages exactly one booking.PayoutCompleted, however often it is applied")
    void completionStagesOneEnvelope() {
        approved();
        settlement.beginSettlement(REQUEST, "pp-1").block();

        settlement.completeSettlement(REQUEST, "prov-9").block();
        View again = settlement.completeSettlement(REQUEST, "prov-9").block();

        assertThat(again.status()).isEqualTo(PayoutRequestStatus.COMPLETED);
        List<Document> rows = template.find(new Query(), Document.class, "booking_outbox").collectList().block();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).toJson()).contains("booking.PayoutCompleted");
        verify(accounting, times(1)).recordPayoutDisbursement(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a completed payout cannot be failed afterwards")
    void aCompletedPayoutCannotBeFailed() {
        approved();
        settlement.beginSettlement(REQUEST, "pp-1").block();
        settlement.completeSettlement(REQUEST, "prov-9").block();

        assertRefused(() -> settlement.failSettlement(REQUEST, "LATE", "late failure").block(), ErrorCode.PAYOUT_STATE_INVALID);
        assertThat(escrow().getCurrentBalance()).isEqualByComparingTo("0.00");
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private void approved() {
        settlement.createRequest(submit(BALANCE)).block();
        settlement.approve(new Decision(REQUEST, FINANCE, "reviewed")).block();
    }

    private static Submit submit(BigDecimal amount) {
        return new Submit(REQUEST, ORGANIZER, "event-settle", ESCROW, BANK, amount, "ZMW", PayoutMethod.MOBILE_MONEY,
                null, null, "idem-settle", ORGANIZER);
    }

    private static BankAccount bank(boolean verified) {
        return BankAccount.builder()
                .id(BANK)
                .organizerId(ORGANIZER)
                .accountHolderName("Account Holder")
                .bankName("MTN Mobile Money")
                .accountNumber("260971234567")
                .isVerified(verified)
                .verificationStatus(verified ? BankAccount.VerificationStatus.VERIFIED : BankAccount.VerificationStatus.PENDING)
                .build();
    }

    private static JournalEntry entry(String correlationId) {
        return JournalEntry.builder().id("journal-" + correlationId).build();
    }

    private static PayoutRequest request() {
        return template.findById(REQUEST, PayoutRequest.class).block();
    }

    private static EventEscrowAccount escrow() {
        return template.findById(ESCROW, EventEscrowAccount.class).block();
    }

    private static List<EscrowTransaction> payoutRows(EventEscrowAccount escrow, String category) {
        return escrow.getTransactions().stream()
                .filter(row -> category.equals(row.getCategory()) && REQUEST.equals(row.getPayoutRequestId()))
                .toList();
    }

    private static void assertRefused(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainRefusal.class)
                .satisfies(error -> assertThat(((DomainRefusal) error).errorCode()).isEqualTo(code));
    }
}
