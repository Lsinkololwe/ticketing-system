package com.pml.booking.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.service.BookingStore;
import com.pml.booking.service.PaymentService;
import com.pml.booking.service.ReservationService;
import com.pml.booking.web.graphql.dto.PayReservationInput;
import com.pml.booking.web.graphql.dto.PaymentInitiationResponse;
import com.pml.booking.workflow.purchase.PurchaseProcess;
import com.pml.booking.workflow.purchase.PurchaseWorkflow;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.PaymentView;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.IdempotencyKeyReusedRefusal;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * ET-PLT-007-R6 · {@code payReservation} against the real {@link IdempotencyGuard}: a prompt sent
 * twice with the same key must not send the buyer's handset a second one.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@DisplayName("payReservation replays an identical retry and refuses a reused key with a different body")
class PayReservationIdempotencyTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static IdempotencyGuard guard;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_pay_idempotency"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test_pay",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(redis, ledger, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    private PurchaseProcess serviceCountingPrompts(AtomicInteger promptCount) {
        TicketReservation reservation = new TicketReservation();
        reservation.setId("res-1");
        reservation.setUserId("buyer-1");
        reservation.setStatus(ReservationStatus.HELD);
        reservation.setExpiresAt(Instant.now().plusSeconds(600));
        reservation.setTotalAmount(BigDecimal.valueOf(100));
        reservation.setCurrency("ZMW");

        ReservationService reservations = mock(ReservationService.class);
        when(reservations.findById("res-1")).thenReturn(Mono.just(reservation));

        PaymentService payments = mock(PaymentService.class);
        when(payments.createPaymentIntent(eq("res-1"), any(), eq("buyer-1"), any(), any(), any()))
                .thenAnswer(call -> {
                    promptCount.incrementAndGet();
                    com.pml.booking.domain.model.PaymentIntent intent = new com.pml.booking.domain.model.PaymentIntent();
                    intent.setId("pi-1");
                    intent.setTransactionRef("TXN-1");
                    intent.setStatus(PaymentStatus.PENDING);
                    return Mono.just(intent);
                });

        PurchaseWorkflow workflow = mock(PurchaseWorkflow.class);
        when(workflow.pay(any())).thenReturn(new PaymentView("pi-1", "TXN-1", PaymentStatus.PENDING));
        TemporalGateway temporal = mock(TemporalGateway.class);
        when(temporal.existingWorkflow(eq(PurchaseWorkflow.class), any())).thenReturn(workflow);
        when(temporal.call(any())).thenAnswer(call -> {
            java.util.concurrent.Callable<?> blocking = call.getArgument(0);
            return Mono.fromCallable(blocking);
        });

        return new PurchaseProcess(temporal, reservations, payments, TestClock.frozenAt(Instant.now()),
                mock(BookingStore.class), guard, new ObjectMapper().findAndRegisterModules());
    }

    private PayReservationInput input(String key) {
        return new PayReservationInput("res-1", "0977000000", key);
    }

    @Test
    @DisplayName("the same key and the same body: one prompt, the same payment handed back twice")
    void replayDoesNotPromptTwice() {
        String key = "pay-replay-" + System.nanoTime();
        AtomicInteger prompts = new AtomicInteger();
        PurchaseProcess process = serviceCountingPrompts(prompts);

        PaymentInitiationResponse first = process.pay("buyer-1", input(key)).block();
        PaymentInitiationResponse second = process.pay("buyer-1", input(key)).block();

        assertThat(prompts.get()).as("the handset is prompted exactly once").isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("the same key with a different phone number: refused, and the original number is never re-prompted")
    void reusedKeyWithADifferentPhoneIsRefused() {
        String key = "pay-mismatch-" + System.nanoTime();
        AtomicInteger prompts = new AtomicInteger();
        PurchaseProcess process = serviceCountingPrompts(prompts);

        process.pay("buyer-1", input(key)).block();

        assertThatThrownBy(() -> process.pay("buyer-1",
                        new PayReservationInput("res-1", "0966000000", key)).block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(prompts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("five simultaneous submissions of one key: the handset is prompted once and all five get the same payment")
    void parallelSubmissionsPromptOnce() {
        String key = "pay-parallel-" + System.nanoTime();
        AtomicInteger prompts = new AtomicInteger();
        PurchaseProcess process = serviceCountingPrompts(prompts);

        Concurrency.Outcome<PaymentInitiationResponse> outcome = Concurrency.inParallel(5,
                caller -> process.pay("buyer-1", input(key)).block());

        assertThat(outcome.failures()).as("no caller is refused or errors").isEmpty();
        assertThat(outcome.successes()).hasSize(5);
        assertThat(prompts.get()).as("the handset is prompted exactly once").isEqualTo(1);
        assertThat(outcome.successes()).containsOnly(outcome.successes().get(0));
    }
}
