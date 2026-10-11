package com.pml.booking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.dto.InventoryReservationResult;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.service.impl.ReservationServiceImpl;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.web.graphql.dto.TicketSelectionInput;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.IdempotencyKeyReusedRefusal;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ET-PLT-007-R6 · {@code reserveTickets} against the real {@link IdempotencyGuard}: a real Mongo
 * ledger and a real Redis fast path, with everything else about the reservation mocked so the
 * one thing under test is the guard's wiring, not the reservation flow it wraps.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@DisplayName("reserveTickets replays an identical retry and refuses a reused key with a different body")
class ReserveTicketsIdempotencyTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static IdempotencyGuard guard;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_reserve_idempotency"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(redis, ledger, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    private ReservationServiceImpl serviceCountingOperations(AtomicInteger operationCount,
                                                              TicketReservationRepository reservations) {
        CatalogServiceClient catalog = mock(CatalogServiceClient.class);
        EventSummaryDto event = EventSummaryDto.builder()
                .id("evt-1")
                .organizerId("org-user-1")
                .organizationId("org-1")
                .ticketCategories(List.of(EventSummaryDto.TicketCategoryDto.builder()
                        .code("GA").name("General").price(BigDecimal.valueOf(50))
                        .capacity(100).sold(0).active(true).build()))
                .build();
        when(catalog.getEventById("evt-1")).thenReturn(Mono.just(event));
        when(catalog.reserveInventory(anyString(), anyInt(), anyString())).thenAnswer(call -> {
            operationCount.incrementAndGet();
            return Mono.just(new InventoryReservationResult(true, null, "GA"));
        });

        when(reservations.findByUserIdAndStatus(anyString(), any())).thenReturn(Flux.empty());
        when(reservations.save(any(TicketReservation.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        return new ReservationServiceImpl(reservations, TestClock.frozenAt(Instant.now()), catalog,
                mock(PurchaseService.class), guard, new ObjectMapper().findAndRegisterModules());
    }

    private ReserveTicketsInput input(String key, int quantity) {
        return new ReserveTicketsInput("evt-1", List.of(new TicketSelectionInput("GA", quantity)),
                null, key, null, null, null);
    }

    @Test
    @DisplayName("the same key and the same body: one inventory call, the same reservation handed back twice")
    void replayDoesNotTakeInventoryTwice() {
        String key = "reserve-replay-" + System.nanoTime();
        AtomicInteger operations = new AtomicInteger();
        TicketReservationRepository reservations = mock(TicketReservationRepository.class);
        ReservationServiceImpl service = serviceCountingOperations(operations, reservations);

        TicketReservation first = service.createReservation("buyer-1", input(key, 2), "res-a").block();
        TicketReservation second = service.createReservation("buyer-1", input(key, 2), "res-b").block();

        assertThat(operations.get()).as("inventory is taken exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("the same key with a different body: refused, and inventory is never touched for the second attempt")
    void reusedKeyWithADifferentBodyIsRefused() {
        String key = "reserve-mismatch-" + System.nanoTime();
        AtomicInteger operations = new AtomicInteger();
        TicketReservationRepository reservations = mock(TicketReservationRepository.class);
        ReservationServiceImpl service = serviceCountingOperations(operations, reservations);

        service.createReservation("buyer-1", input(key, 2), "res-a").block();

        assertThatThrownBy(() -> service.createReservation("buyer-1", input(key, 3), "res-b").block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(operations.get()).as("the mismatched retry never reaches inventory").isEqualTo(1);
    }

    @Test
    @DisplayName("five simultaneous submissions of one key: inventory is taken once and all five get the same reservation")
    void parallelSubmissionsTakeInventoryOnce() {
        String key = "reserve-parallel-" + System.nanoTime();
        AtomicInteger operations = new AtomicInteger();
        TicketReservationRepository reservations = mock(TicketReservationRepository.class);
        ReservationServiceImpl service = serviceCountingOperations(operations, reservations);

        Concurrency.Outcome<TicketReservation> outcome = Concurrency.inParallel(5,
                caller -> service.createReservation("buyer-1", input(key, 2), "res-" + caller).block());

        assertThat(outcome.failures()).as("no caller is refused or errors").isEmpty();
        assertThat(outcome.successes()).hasSize(5);
        assertThat(operations.get()).as("inventory is taken exactly once").isEqualTo(1);
        assertThat(outcome.successes()).extracting(TicketReservation::getId).containsOnly(outcome.successes().get(0).getId());
    }
}
