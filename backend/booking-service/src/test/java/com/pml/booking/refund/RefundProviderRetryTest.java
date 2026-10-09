package com.pml.booking.refund;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.config.PaymentProperties;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.EscrowTransaction;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.service.impl.PaymentAttemptRecorderImpl;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.exception.ProviderUnavailableException;
import com.pml.booking.infrastructure.client.PawaPayClient;
import com.pml.booking.infrastructure.client.PawaPayClient.FailureReason;
import com.pml.booking.infrastructure.client.PawaPayClient.RefundResponse;
import com.pml.booking.repository.CommissionRecordRepository;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.security.TenantAccessGuard;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.impl.CommissionServiceImpl;
import com.pml.booking.service.impl.EscrowServiceImpl;
import com.pml.booking.service.impl.RefundServiceImpl;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A refund sent to PawaPay more than once — because PawaPay was unreachable, or because the process
 * died after sending it — goes out under one PawaPay refund id and takes the escrow debit once.
 *
 * <p>Runs the real refund, escrow and commission services against a MongoDB replica set, with only
 * the PawaPay HTTP client replaced, so the database effects are the ones production produces.
 */
@Tag("L2")
@Tag("ET-FIN-004")
@DisplayName("A retried refund reuses its PawaPay id and debits the escrow once")
class RefundProviderRetryTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final String REFUND = "refund-retry-1";
    private static final String TICKET = "ticket-retry-1";
    private static final String EVENT = "event-retry-1";
    private static final String DEPOSIT = "deposit-retry-1";
    private static final BigDecimal PRICE = new BigDecimal("200.00");
    private static final BigDecimal RATE = new BigDecimal("0.10");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static RefundRequestRepository refunds;
    private static TicketRepository tickets;
    private static EventEscrowAccountRepository escrows;
    private static CommissionRecordRepository commissions;
    private static PaymentAttemptRepository paymentAttempts;
    private static TransactionalOperator transaction;
    private static Clock clock;

    private PawaPayClient pawaPay;
    private RefundServiceImpl service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_refund_retry"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        refunds = factory.getRepository(RefundRequestRepository.class);
        tickets = factory.getRepository(TicketRepository.class);
        escrows = factory.getRepository(EventEscrowAccountRepository.class);
        commissions = factory.getRepository(CommissionRecordRepository.class);
        paymentAttempts = factory.getRepository(PaymentAttemptRepository.class);
        transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()));
        clock = TestClock.frozenAt(NOW);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), RefundRequest.class).block();
        template.remove(new Query(), Ticket.class).block();
        template.remove(new Query(), EventEscrowAccount.class).block();
        template.remove(new Query(), CommissionRecord.class).block();
        template.remove(new Query(), PaymentAttempt.class).block();

        tickets.save(Ticket.builder()
                .id(TICKET)
                .ticketNumber("TKT-RETRY-1")
                .eventId(EVENT)
                .price(PRICE)
                .paymentInfo(Ticket.PaymentInfo.builder().transactionId(DEPOSIT).build())
                .build()).block();
        escrows.save(EventEscrowAccount.builder()
                .id("escrow-retry-1")
                .eventId(EVENT)
                .currentBalance(new BigDecimal("1000.00"))
                .transactions(new ArrayList<>())
                .build()).block();
        commissions.save(CommissionRecord.createPending(TICKET, EVENT, "organizer-1", "org-1", PRICE, RATE, NOW)).block();
        refunds.save(RefundRequest.builder()
                .id(REFUND)
                .ticketId(TICKET)
                .ticketNumber("TKT-RETRY-1")
                .eventId(EVENT)
                .refundAmount(PRICE)
                .currency("ZMW")
                .status(RefundRequestStatus.APPROVED)
                .build()).block();

        pawaPay = Mockito.mock(PawaPayClient.class);
        EscrowServiceImpl escrowService = new EscrowServiceImpl(escrows, clock, Mockito.mock(TenantAccessGuard.class));
        CommissionServiceImpl commissionService = new CommissionServiceImpl(commissions, Mockito.mock(AccountingService.class), clock);
        service = new RefundServiceImpl(refunds, clock, tickets, pawaPay, commissionService, escrowService,
                Mockito.mock(AccountingService.class), new PaymentProperties(), transaction, template,
                new PaymentAttemptRecorderImpl(paymentAttempts, clock));
    }

    @Test
    @DisplayName("PawaPay unreachable, then reachable: the second send carries the first send's refund id")
    void anUnreachableProviderIsRetriedWithTheSameId() {
        when(pawaPay.initiateRefund(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.just(unreachable()))
                .thenAnswer(call -> Mono.just(new RefundResponse(call.getArgument(0), "ACCEPTED", NOW, null)));

        assertThatThrownBy(() -> service.processRefund(REFUND).block())
                .isInstanceOf(ProviderUnavailableException.class);
        RefundRequest afterRetry = service.processRefund(REFUND).block();

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(pawaPay, times(2)).initiateRefund(ids.capture(), anyString(), any(), any(), any());
        assertThat(ids.getAllValues()).hasSize(2).doesNotContainNull();
        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
        assertThat(afterRetry.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
        assertThat(afterRetry.getPawaPayRefundId()).isEqualTo(ids.getValue());
        assertThat(afterRetry.getPawaPayDepositId()).isEqualTo(DEPOSIT);
        assertThat(refundDebits()).as("one escrow debit for the refund").hasSize(1);
        assertThat(escrows.findByEventId(EVENT).block().getCurrentBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00").subtract(PRICE.subtract(PRICE.multiply(RATE))));

        List<PaymentAttempt> rows = paymentAttempts.findAll().collectList().block();
        assertThat(rows).as("one attempt row for the one refund id").hasSize(1);
        PaymentAttempt row = rows.get(0);
        assertThat(row.getAttemptType()).isEqualTo(PaymentAttemptType.REFUND);
        assertThat(row.getProviderReference()).isEqualTo(ids.getValue());
        assertThat(row.getRefundRequestId()).isEqualTo(REFUND);
        assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
    }

    @Test
    @DisplayName("A send whose answer was lost is sent again under the same id, which PawaPay reports as a duplicate")
    void aLostAnswerIsResentUnderTheSameId() {
        when(pawaPay.initiateRefund(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.error(new ProviderUnavailableException("connection reset after the request was sent")))
                .thenAnswer(call -> Mono.just(new RefundResponse(call.getArgument(0), "DUPLICATE_IGNORED", NOW, null)));

        assertThatThrownBy(() -> service.processRefund(REFUND).block()).isInstanceOf(ProviderUnavailableException.class);
        RefundRequest afterRetry = service.processRefund(REFUND).block();

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(pawaPay, times(2)).initiateRefund(ids.capture(), anyString(), any(), any(), any());
        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
        assertThat(afterRetry.getStatus()).as("a duplicate means PawaPay already has the refund")
                .isEqualTo(RefundRequestStatus.PROCESSING);
        assertThat(refundDebits()).hasSize(1);
    }

    @Test
    @DisplayName("Concurrent processing of one refund assigns a single PawaPay id and one escrow debit")
    void concurrentProcessingAssignsOneId() {
        when(pawaPay.initiateRefund(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(call -> Mono.just(new RefundResponse(call.getArgument(0), "ACCEPTED", NOW, null)));

        Mono.zipDelayError(
                        service.processRefund(REFUND).onErrorResume(error -> Mono.empty()).then(Mono.just(1)),
                        service.processRefund(REFUND).onErrorResume(error -> Mono.empty()).then(Mono.just(2)))
                .block();

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(pawaPay, Mockito.atLeastOnce()).initiateRefund(ids.capture(), anyString(), any(), any(), any());
        assertThat(ids.getAllValues().stream().distinct()).as("every send uses the same refund id").hasSize(1);
        assertThat(refundDebits()).as("one escrow debit however many sends").hasSize(1);
    }

    @Test
    @DisplayName("A refusal from PawaPay fails the refund and records its reason")
    void aRefusalFailsTheRefund() {
        when(pawaPay.initiateRefund(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(call -> Mono.just(new RefundResponse(call.getArgument(0), "REJECTED", NOW,
                        new FailureReason("DEPOSIT_NOT_FOUND", "The original deposit was not found"))));

        RefundRequest refused = service.processRefund(REFUND).block();

        assertThat(refused.getStatus()).isEqualTo(RefundRequestStatus.FAILED);
        assertThat(refused.getRejectionReason()).isEqualTo("The original deposit was not found");
        PaymentAttempt row = paymentAttempts.findByProviderReference(refused.getPawaPayRefundId()).block();
        assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.REJECTED);
        assertThat(row.getFailureCode()).isEqualTo("DEPOSIT_NOT_FOUND");
    }

    private static RefundResponse unreachable() {
        return new RefundResponse(null, "REJECTED", NOW,
                new FailureReason("CIRCUIT_BREAKER_OPEN", "Refund service temporarily unavailable"));
    }

    private static List<EscrowTransaction> refundDebits() {
        return escrows.findByEventId(EVENT).block().getTransactions().stream()
                .filter(txn -> REFUND.equals(txn.getRefundRequestId()))
                .toList();
    }
}
