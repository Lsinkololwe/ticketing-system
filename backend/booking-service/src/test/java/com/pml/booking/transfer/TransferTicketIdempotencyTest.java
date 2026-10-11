package com.pml.booking.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput.TransferChannel;
import com.pml.booking.web.graphql.mutation.TicketTransferMutationResolver;
import com.pml.booking.workflow.transfer.TicketTransferProcess;
import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.IdempotencyKeyReusedRefusal;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
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
 * ET-PLT-007-R6 · {@code initiateTicketTransfer} against the real {@link IdempotencyGuard}: a
 * retry with the same key must not raise a second transfer offer, and a reused key with a
 * different body must be refused rather than replayed.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("initiateTicketTransfer replays an identical retry and refuses a reused key with a different body")
class TransferTicketIdempotencyTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static IdempotencyGuard guard;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_transfer_idempotency"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test_transfer",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(redis, ledger, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    private TicketTransferMutationResolver resolverCountingOffers(AtomicInteger offerCount) {
        TicketTransferProcess process = mock(TicketTransferProcess.class);
        when(process.initiate(any(), anyString())).thenAnswer(call -> {
            int n = offerCount.incrementAndGet();
            TicketTransfer transfer = new TicketTransfer();
            transfer.setId("transfer-" + n);
            transfer.setStatus(TicketTransferStatus.PENDING);
            return Mono.just(transfer);
        });
        return new TicketTransferMutationResolver(process, guard, new ObjectMapper().findAndRegisterModules());
    }

    private InitiateTicketTransferInput input(String key) {
        return new InitiateTicketTransferInput("tkt-1", TransferChannel.WHATSAPP, "+260971234567", "enjoy", key);
    }

    @Test
    @DisplayName("the same key and the same body: one offer raised, the same transfer handed back twice")
    void replayDoesNotRaiseASecondOffer() {
        String key = "transfer-replay-" + System.nanoTime();
        AtomicInteger offers = new AtomicInteger();
        TicketTransferMutationResolver resolver = resolverCountingOffers(offers);

        TicketTransfer first = BookingFixture.asCustomer("holder-1", resolver.initiateTicketTransfer(input(key))).block();
        TicketTransfer second = BookingFixture.asCustomer("holder-1", resolver.initiateTicketTransfer(input(key))).block();

        assertThat(offers.get()).as("the transfer is raised exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("the same key with a different recipient is refused, and no second offer is ever raised")
    void reusedKeyWithADifferentRecipientIsRefused() {
        String key = "transfer-mismatch-" + System.nanoTime();
        AtomicInteger offers = new AtomicInteger();
        TicketTransferMutationResolver resolver = resolverCountingOffers(offers);

        BookingFixture.asCustomer("holder-1", resolver.initiateTicketTransfer(input(key))).block();

        InitiateTicketTransferInput different = new InitiateTicketTransferInput("tkt-1", TransferChannel.WHATSAPP, "+260969999999", "enjoy", key);
        assertThatThrownBy(() -> BookingFixture.asCustomer("holder-1", resolver.initiateTicketTransfer(different)).block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(offers.get()).isEqualTo(1);
    }
}
