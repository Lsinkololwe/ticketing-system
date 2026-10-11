package com.pml.booking.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.web.graphql.dto.CreateRefundRequestInput;
import com.pml.booking.web.graphql.mutation.RefundRequestMutationResolver;
import com.pml.booking.workflow.refund.RefundProcess;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.IdempotencyKeyReusedRefusal;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
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
 * ET-PLT-007-R6 · {@code createUserRefundRequest}, {@code createAdminRefundRequest} and
 * {@code approveRefundRequest} against the real {@link IdempotencyGuard}: a retry with the same
 * key must not raise or decide a refund request a second time, and a reused key with a different
 * body must be refused rather than replayed.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("requestRefund and approveRefund replay an identical retry and refuse a reused key with a different body")
class RequestRefundIdempotencyTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static IdempotencyGuard guard;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_refund_idempotency"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test_refund",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(redis, ledger, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    private RefundRequest refundRequest(String id) {
        RefundRequest request = new RefundRequest();
        request.setId(id);
        request.setStatus(RefundRequestStatus.PENDING);
        request.setRefundAmount(BigDecimal.valueOf(20));
        return request;
    }

    private RefundRequestMutationResolver resolverCountingRequests(AtomicInteger requestCount) {
        RefundProcess process = mock(RefundProcess.class);
        when(process.request(anyString(), anyString(), anyString())).thenAnswer(call -> {
            int n = requestCount.incrementAndGet();
            return Mono.just(refundRequest("refund-" + n));
        });
        when(process.requestAsAdmin(anyString(), anyString(), anyString(), anyBoolean(), any())).thenAnswer(call -> {
            int n = requestCount.incrementAndGet();
            return Mono.just(refundRequest("admin-refund-" + n));
        });
        when(process.approve(anyString(), anyString(), any())).thenAnswer(call -> {
            int n = requestCount.incrementAndGet();
            return Mono.just(refundRequest("approved-" + n));
        });
        return new RefundRequestMutationResolver(process, guard, new ObjectMapper().findAndRegisterModules());
    }

    private CreateRefundRequestInput input(String key) {
        return new CreateRefundRequestInput("tkt-1", "I cannot attend", null, null, key);
    }

    @Test
    @DisplayName("createUserRefundRequest: the same key and the same body replays, the workflow is never asked twice")
    void buyerReplayDoesNotRaiseTwice() {
        String key = "refund-replay-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        RefundRequestMutationResolver resolver = resolverCountingRequests(requests);

        RefundRequest first = BookingFixture.asCustomer("buyer-1", resolver.createUserRefundRequest(input(key))).block();
        RefundRequest second = BookingFixture.asCustomer("buyer-1", resolver.createUserRefundRequest(input(key))).block();

        assertThat(requests.get()).as("the refund workflow is started exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("createUserRefundRequest: the same key with a different ticket is refused, and the workflow is never asked for the mismatch")
    void buyerReusedKeyWithADifferentTicketIsRefused() {
        String key = "refund-mismatch-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        RefundRequestMutationResolver resolver = resolverCountingRequests(requests);

        BookingFixture.asCustomer("buyer-1", resolver.createUserRefundRequest(input(key))).block();

        assertThatThrownBy(() -> BookingFixture.asCustomer("buyer-1",
                        resolver.createUserRefundRequest(new CreateRefundRequestInput("tkt-2", "I cannot attend", null, null, key)))
                .block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createAdminRefundRequest: the same key and the same body replays, the workflow is never asked twice")
    void adminReplayDoesNotRaiseTwice() {
        String key = "refund-admin-replay-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        RefundRequestMutationResolver resolver = resolverCountingRequests(requests);

        RefundRequest first = BookingFixture.asAdmin("admin-1",
                resolver.createAdminRefundRequest("tkt-1", "goodwill", false, new BigDecimal("20.00"), key), "ROLE_ADMIN").block();
        RefundRequest second = BookingFixture.asAdmin("admin-1",
                resolver.createAdminRefundRequest("tkt-1", "goodwill", false, new BigDecimal("20.00"), key), "ROLE_ADMIN").block();

        assertThat(requests.get()).as("the refund workflow is started exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("createAdminRefundRequest: the same key with a different amount is refused, and the workflow is never asked for the mismatch")
    void adminReusedKeyWithADifferentAmountIsRefused() {
        String key = "refund-admin-mismatch-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        RefundRequestMutationResolver resolver = resolverCountingRequests(requests);

        BookingFixture.asAdmin("admin-1",
                resolver.createAdminRefundRequest("tkt-1", "goodwill", false, new BigDecimal("20.00"), key), "ROLE_ADMIN").block();

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.createAdminRefundRequest("tkt-1", "goodwill", false, new BigDecimal("30.00"), key), "ROLE_ADMIN")
                .block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("approveRefundRequest: the same key and the same body replays, the workflow is never asked twice")
    void approveReplayDoesNotDecideTwice() {
        String key = "refund-approve-replay-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        RefundRequestMutationResolver resolver = resolverCountingRequests(requests);

        RefundRequest first = BookingFixture.asAdmin("admin-1",
                resolver.approveRefundRequest("refund-1", "ok", key), "ROLE_ADMIN").block();
        RefundRequest second = BookingFixture.asAdmin("admin-1",
                resolver.approveRefundRequest("refund-1", "ok", key), "ROLE_ADMIN").block();

        assertThat(requests.get()).as("the workflow is asked to decide exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("approveRefundRequest: the same key with different review comments is refused, and the workflow is never asked for the mismatch")
    void approveReusedKeyWithDifferentCommentsIsRefused() {
        String key = "refund-approve-mismatch-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        RefundRequestMutationResolver resolver = resolverCountingRequests(requests);

        BookingFixture.asAdmin("admin-1", resolver.approveRefundRequest("refund-1", "ok", key), "ROLE_ADMIN").block();

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.approveRefundRequest("refund-1", "changed my mind", key), "ROLE_ADMIN")
                .block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(requests.get()).isEqualTo(1);
    }
}
