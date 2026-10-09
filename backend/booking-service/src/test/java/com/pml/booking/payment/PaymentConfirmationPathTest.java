package com.pml.booking.payment;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PaymentIntent.PaymentProvider;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.exception.ReservationExpiredException;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.MobileMoneyRequest;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.infrastructure.gateway.model.PaymentResultStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PurchaseEscalationService;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.service.impl.PaymentAttemptRecorderImpl;
import com.pml.booking.service.impl.PaymentServiceImpl;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A verified deposit becomes exactly one confirmation.
 *
 * <h2>The chain under test</h2>
 * The provider receives the intent's stored {@code depositId}; a callback naming that id is verified
 * against the provider's status API; the answer is applied once by compare-and-set with its
 * {@code booking.Payment*} envelope in the same transaction; and only the writer that made the
 * transition drives confirmation or release. The provider and the purchase step are stubs — the
 * compare-and-set, the transaction and the outbox run against a real replica set.
 */
@Tag("L2")
@Tag("ET-PAY-002")
@DisplayName("ET-PAY-002-R3/R4/R7 · a deposit is verified, applied once, and confirmed once")
class PaymentConfirmationPathTest {

    private static final String DEPOSIT = "5f0a8e44-8d6b-4c61-9a57-8d3f2b1e9c10";
    private static final String INTENT = "intent-probe";
    private static final String RESERVATION = "res-probe";
    private static final Instant NOW = Instant.parse("2026-09-01T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static PaymentIntentRepository intents;
    private static PaymentAttemptRepository paymentAttempts;
    private static Outbox outbox;
    private static TransactionalOperator transaction;
    private static Clock clock;

    private MobileMoneyGateway gateway;
    private PurchaseService purchases;
    private PurchaseEscalationService escalations;
    private PaymentOutcomeService outcomes;
    private PaymentServiceImpl payments;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_payment_path"));
        intents = new ReactiveMongoRepositoryFactory(template).getRepository(PaymentIntentRepository.class);
        paymentAttempts = new ReactiveMongoRepositoryFactory(template).getRepository(PaymentAttemptRepository.class);
        clock = TestClock.frozenAt(NOW);
        outbox = new Outbox(template, "booking_outbox", clock);
        transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void wire() {
        template.remove(new Query(), PaymentIntent.class).block();
        template.remove(new Query(), PaymentAttempt.class).block();
        template.remove(new Query(), Document.class, "booking_outbox").block();

        gateway = Mockito.mock(MobileMoneyGateway.class);
        MobileMoneyGatewayFactory gateways = Mockito.mock(MobileMoneyGatewayFactory.class);
        when(gateways.getGatewayForPhone(anyString())).thenReturn(Mono.just(gateway));

        purchases = Mockito.mock(PurchaseService.class);
        when(purchases.confirm(anyString(), anyString(), any())).thenReturn(Mono.just(List.of()));
        when(purchases.release(anyString(), any(), any())).thenReturn(Mono.just(new TicketReservation()));

        escalations = Mockito.mock(PurchaseEscalationService.class);
        when(escalations.raise(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        outcomes = new PaymentOutcomeService(intents, template, transaction, outbox, gateways,
                purchases, escalations, clock);
        PaymentAttemptRecorder recorder = new PaymentAttemptRecorderImpl(paymentAttempts, clock);
        payments = new PaymentServiceImpl(intents, template, gateways, outcomes, recorder, clock);
    }

    private static void seed(PaymentStatus status) {
        template.save(PaymentIntent.builder()
                .id(INTENT)
                .idempotencyKey("user-probe_" + RESERVATION)
                .transactionRef("TXN-20260901-PROBE")
                .depositId(DEPOSIT)
                .reservationId(RESERVATION)
                .eventId("event-probe")
                .userId("user-probe")
                .amount(new BigDecimal("450.00"))
                .currency("ZMW")
                .provider(PaymentProvider.MTN_MOMO_ZMB)
                .phoneNumber("+260971234567")
                .status(status)
                .expiresAt(NOW.plusSeconds(900))
                .build()).block();
    }

    private static PaymentResult providerSays(PaymentResultStatus status) {
        return PaymentResult.builder()
                .correlationId(DEPOSIT)
                .providerTransactionId(DEPOSIT)
                .status(status)
                .isPending(status == PaymentResultStatus.PENDING)
                .providerId("pawapay")
                .timestamp(NOW)
                .build();
    }

    /** What the adapter builds for a timeout or a 5xx: a failure with no provider reference. */
    private static PaymentResult transportFailure() {
        return PaymentResult.failed(DEPOSIT, "HTTP_503", "status API unavailable", true, "pawapay", NOW);
    }

    private static PaymentIntent intent() {
        return template.findById(INTENT, PaymentIntent.class).block();
    }

    private static PaymentAttempt paymentAttempt() {
        return paymentAttempts.findByDepositId(DEPOSIT).block();
    }

    private static List<Document> outboxRows() {
        return template.find(new Query(), Document.class, "booking_outbox").collectList().block();
    }

    @Test
    @DisplayName("a verified success settles the intent, stages PaymentCompleted and confirms once")
    void aVerifiedSuccessConfirmsOnce() {
        seed(PaymentStatus.PROCESSING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.SUCCESS)));

        outcomes.verifyAndApply(DEPOSIT).block();

        assertThat(intent().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outboxRows()).singleElement()
                .extracting(row -> row.getString("eventType"))
                .isEqualTo(EventType.BOOKING_PAYMENT_COMPLETED.wireName());
        verify(purchases, times(1)).confirm(RESERVATION, INTENT, DEPOSIT);
        verify(purchases, never()).release(anyString(), any(), any());
    }

    @Test
    @DisplayName("a replayed callback finds the intent settled and changes nothing")
    void aReplayedCallbackChangesNothing() {
        seed(PaymentStatus.PROCESSING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.SUCCESS)));

        outcomes.verifyAndApply(DEPOSIT).block();
        outcomes.verifyAndApply(DEPOSIT).block();

        assertThat(outboxRows()).hasSize(1);
        verify(purchases, times(1)).confirm(anyString(), anyString(), any());
        verify(gateway, times(1)).checkPaymentStatus(DEPOSIT);
    }

    @Test
    @DisplayName("ten simultaneous callbacks for one deposit produce one transition and one confirmation")
    void simultaneousCallbacksConfirmOnce() {
        seed(PaymentStatus.PROCESSING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.SUCCESS)));

        List<PaymentIntent> settled = Flux.range(0, 10)
                .flatMap(i -> outcomes.verifyAndApply(DEPOSIT).subscribeOn(Schedulers.boundedElastic()), 10)
                .collectList()
                .block();

        assertThat(settled).hasSize(10).allMatch(i -> i.getStatus() == PaymentStatus.SUCCEEDED);
        assertThat(outboxRows()).as("one transition, one envelope").hasSize(1);
        verify(purchases, times(1)).confirm(RESERVATION, INTENT, DEPOSIT);
    }

    @Test
    @DisplayName("a callback claiming success that the provider contradicts issues nothing")
    void aForgedSuccessCallbackIssuesNothing() {
        seed(PaymentStatus.PROCESSING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.FAILED)));

        // The callback's claim is never read: the webhook only prompts this verification.
        outcomes.verifyAndApply(DEPOSIT).block();

        assertThat(intent().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(purchases, never()).confirm(anyString(), anyString(), any());
        verify(purchases, times(1)).release(eq(RESERVATION), eq(ReservationStateMachine.Action.RELEASE), any());
        assertThat(outboxRows()).singleElement()
                .extracting(row -> row.getString("eventType"))
                .isEqualTo(EventType.BOOKING_PAYMENT_FAILED.wireName());
    }

    @Test
    @DisplayName("a pending answer records the poll and changes nothing else")
    void aPendingAnswerChangesNothing() {
        seed(PaymentStatus.PROCESSING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.PENDING)));

        PaymentIntent after = outcomes.verifyAndApply(DEPOSIT).block();

        assertThat(after.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(after.getPollAttempts()).isEqualTo(1);
        Persistence.assertNothingPersisted(template, "booking_outbox");
        Mockito.verifyNoInteractions(purchases);
    }

    @Test
    @DisplayName("an unavailable status API is no answer, so the payment is not failed")
    void anUnavailableStatusApiIsNotAFailure() {
        seed(PaymentStatus.PROCESSING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(transportFailure()));

        outcomes.verifyAndApply(DEPOSIT).block();

        assertThat(intent().getStatus())
                .as("ET-PAY-001 R2 · no code path infers FAILED from an exception")
                .isEqualTo(PaymentStatus.PROCESSING);
        Persistence.assertNothingPersisted(template, "booking_outbox");
        Mockito.verifyNoInteractions(purchases);
    }

    @Test
    @DisplayName("money that arrives after the hold ended is escalated, not lost")
    void aLateSuccessIsEscalated() {
        seed(PaymentStatus.EXPIRED);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.SUCCESS)));
        when(purchases.confirm(anyString(), anyString(), any()))
                .thenReturn(Mono.error(new ReservationExpiredException(RESERVATION)));

        outcomes.verifyAndApply(DEPOSIT).block();

        assertThat(intent().getStatus())
                .as("the provider's answer outranks a local expiry")
                .isEqualTo(PaymentStatus.SUCCEEDED);
        verify(escalations, times(1)).raise(eq(RESERVATION), any(), any(), eq(INTENT),
                eq(PurchaseEscalation.Reason.PAID_AFTER_EXPIRY), any(), any(), any());
        verify(purchases, times(1)).release(eq(RESERVATION), eq(ReservationStateMachine.Action.FAIL), any());
    }

    @Test
    @DisplayName("a callback naming no known deposit is reported, not swallowed")
    void anUnknownDepositIsReported() {
        assertThatThrownBy(() -> outcomes.verifyAndApply("deposit-nobody-issued").block())
                .isInstanceOf(PaymentOutcomeService.UnknownDeposit.class);
    }

    @Test
    @DisplayName("submission sends the stored deposit id and marks the intent PROCESSING")
    void submissionUsesTheStoredDepositId() {
        seed(PaymentStatus.PENDING);
        when(gateway.initiatePayment(any())).thenReturn(Mono.just(providerSays(PaymentResultStatus.PENDING)));

        PaymentIntent after = payments.initiatePayment(INTENT).block();

        ArgumentCaptor<MobileMoneyRequest> sent = ArgumentCaptor.forClass(MobileMoneyRequest.class);
        verify(gateway).initiatePayment(sent.capture());
        assertThat(sent.getValue().correlationId())
                .as("ET-PAY-002 R4 · the provider receives the id a callback will name")
                .isEqualTo(DEPOSIT);
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(after.getProviderTransactionId()).isEqualTo(DEPOSIT);

        assertThat(paymentAttempt())
                .as("ET-PAY-001 · the attempt is recorded and links back to its intent")
                .isNotNull()
                .satisfies(attempt -> {
                    assertThat(attempt.getPaymentIntentId()).isEqualTo(INTENT);
                    assertThat(attempt.getStatus().name()).isEqualTo("PENDING_APPROVAL");
                });
    }

    @Test
    @DisplayName("a submission that times out leaves the intent PENDING and releases nothing")
    void aTimedOutSubmissionStaysPending() {
        seed(PaymentStatus.PENDING);
        when(gateway.initiatePayment(any())).thenReturn(Mono.just(transportFailure()));

        PaymentIntent after = payments.initiatePayment(INTENT).block();

        assertThat(after.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(after.getDepositId()).isEqualTo(DEPOSIT);
        Mockito.verifyNoInteractions(purchases);

        assertThat(paymentAttempt())
                .as("ET-PAY-001 · the row exists — a crash mid-call still leaves evidence — but an "
                        + "unanswered call does not fabricate a decline")
                .isNotNull()
                .satisfies(attempt -> assertThat(attempt.getStatus().name()).isEqualTo("CREATED"));
    }

    @Test
    @DisplayName("a callback that lands before the submission response is not overwritten by it")
    void aCallbackAheadOfTheSubmissionResponseStands() {
        seed(PaymentStatus.PENDING);
        when(gateway.checkPaymentStatus(DEPOSIT)).thenReturn(Mono.just(providerSays(PaymentResultStatus.SUCCESS)));
        when(gateway.initiatePayment(any())).thenAnswer(invocation -> outcomes.verifyAndApply(DEPOSIT)
                .thenReturn(providerSays(PaymentResultStatus.PENDING)));

        payments.initiatePayment(INTENT).block();

        assertThat(intent().getStatus())
                .as("ET-PAY-002 R4 · the accepted-response write is a compare-and-set from PENDING")
                .isEqualTo(PaymentStatus.SUCCEEDED);
        verify(purchases, times(1)).confirm(RESERVATION, INTENT, DEPOSIT);
    }
    @Test
    @DisplayName("ET-TKT-001 R8 · recovery confirms a paid reservation that is still HELD")
    void resumeConfirmsAPaidHeldReservation() {
        seed(PaymentStatus.SUCCEEDED);
        holdReservation(com.pml.shared.constants.ReservationStatus.HELD);

        outcomes.resume(RESERVATION).block();

        verify(purchases, times(1)).confirm(eq(RESERVATION), eq(INTENT), any());
    }

    @Test
    @DisplayName("ET-TKT-001 R8 · recovery releases a HELD reservation whose payment failed")
    void resumeReleasesAFailedHeldReservation() {
        seed(PaymentStatus.FAILED);
        holdReservation(com.pml.shared.constants.ReservationStatus.HELD);

        outcomes.resume(RESERVATION).block();

        verify(purchases, times(1)).release(eq(RESERVATION), eq(ReservationStateMachine.Action.RELEASE), any());
        verify(purchases, never()).confirm(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("ET-TKT-001 R8 · recovery leaves a reservation that already moved alone")
    void resumeLeavesAMovedReservationAlone() {
        seed(PaymentStatus.SUCCEEDED);
        holdReservation(com.pml.shared.constants.ReservationStatus.CONFIRMED);

        outcomes.resume(RESERVATION).block();

        verify(purchases, never()).confirm(anyString(), anyString(), any());
        verify(purchases, never()).release(anyString(), any(), any());
    }

    private static void holdReservation(com.pml.shared.constants.ReservationStatus status) {
        template.remove(new Query(), TicketReservation.class).block();
        template.save(TicketReservation.builder()
                .id(RESERVATION)
                .eventId("event-probe")
                .userId("user-probe")
                .status(status)
                .expiresAt(NOW.plusSeconds(600))
                .build()).block();
    }
}
