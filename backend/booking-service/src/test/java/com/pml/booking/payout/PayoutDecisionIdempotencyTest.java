package com.pml.booking.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.security.TenantReads;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.mutation.PayoutRequestMutationResolver;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.constants.PayoutRequestStatus;
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
 * ET-PLT-007-R6 · {@code approvePayoutRequest} and {@code retryPayoutRequest} against the real
 * {@link IdempotencyGuard}: a retry with the same key must not decide or re-drive the workflow a
 * second time, and a reused key with a different body must be refused.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("approvePayout and retryPayout replay an identical retry and refuse a reused key with a different body")
class PayoutDecisionIdempotencyTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static IdempotencyGuard guard;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_payout_decision_idempotency"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test_payout_decision",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(redis, ledger, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    private PayoutRequest payoutRequest(String id) {
        PayoutRequest request = new PayoutRequest();
        request.setId(id);
        request.setStatus(PayoutRequestStatus.APPROVED);
        return request;
    }

    private PayoutRequestMutationResolver resolverCountingCommands(AtomicInteger commandCount) {
        PayoutProcess process = mock(PayoutProcess.class);
        when(process.approve(anyString(), anyString(), any())).thenAnswer(call -> {
            int n = commandCount.incrementAndGet();
            return Mono.just(payoutRequest("approved-" + n));
        });
        when(process.retry(anyString(), anyString())).thenAnswer(call -> {
            int n = commandCount.incrementAndGet();
            return Mono.just(payoutRequest("retried-" + n));
        });
        return new PayoutRequestMutationResolver(mock(PayoutRequestService.class), mock(TenantReads.class),
                mock(PayoutRecoveryService.class), mock(IdentityServiceClient.class),
                mock(com.pml.booking.service.EscrowService.class), mock(com.pml.booking.security.PayoutAccess.class), process,
                guard, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("approvePayoutRequest: the same key and the same body replays, the workflow is never asked to decide twice")
    void approveReplayDoesNotDecideTwice() {
        String key = "payout-approve-replay-" + System.nanoTime();
        AtomicInteger commands = new AtomicInteger();
        PayoutRequestMutationResolver resolver = resolverCountingCommands(commands);

        PayoutRequest first = BookingFixture.asAdmin("admin-1",
                resolver.approvePayoutRequest("payout-1", "ok", key), "ROLE_ADMIN").block();
        PayoutRequest second = BookingFixture.asAdmin("admin-1",
                resolver.approvePayoutRequest("payout-1", "ok", key), "ROLE_ADMIN").block();

        assertThat(commands.get()).as("the workflow is asked to decide exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("approvePayoutRequest: the same key with different notes is refused, and the workflow is never asked for the mismatch")
    void approveReusedKeyWithDifferentNotesIsRefused() {
        String key = "payout-approve-mismatch-" + System.nanoTime();
        AtomicInteger commands = new AtomicInteger();
        PayoutRequestMutationResolver resolver = resolverCountingCommands(commands);

        BookingFixture.asAdmin("admin-1", resolver.approvePayoutRequest("payout-1", "ok", key), "ROLE_ADMIN").block();

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.approvePayoutRequest("payout-1", "changed my mind", key), "ROLE_ADMIN")
                .block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(commands.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryPayoutRequest: the same key and the same body replays, the workflow is never re-driven twice")
    void retryReplayDoesNotRedriveTwice() {
        String key = "payout-retry-replay-" + System.nanoTime();
        AtomicInteger commands = new AtomicInteger();
        PayoutRequestMutationResolver resolver = resolverCountingCommands(commands);

        PayoutRequest first = BookingFixture.asAdmin("admin-1",
                resolver.retryPayoutRequest("payout-1", key), "ROLE_ADMIN").block();
        PayoutRequest second = BookingFixture.asAdmin("admin-1",
                resolver.retryPayoutRequest("payout-1", key), "ROLE_ADMIN").block();

        assertThat(commands.get()).as("the payout is re-driven exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("retryPayoutRequest: the same key for a different payout is refused, and the workflow is never re-driven for the mismatch")
    void retryReusedKeyForADifferentPayoutIsRefused() {
        String key = "payout-retry-mismatch-" + System.nanoTime();
        AtomicInteger commands = new AtomicInteger();
        PayoutRequestMutationResolver resolver = resolverCountingCommands(commands);

        BookingFixture.asAdmin("admin-1", resolver.retryPayoutRequest("payout-1", key), "ROLE_ADMIN").block();

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.retryPayoutRequest("payout-2", key), "ROLE_ADMIN")
                .block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(commands.get()).isEqualTo(1);
    }
}
