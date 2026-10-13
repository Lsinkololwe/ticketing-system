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
import com.pml.booking.web.graphql.dto.BulkPayoutOperationResponse;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.IdempotencyKeyReusedRefusal;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * ET-PLT-007-R6 · {@code approvePayoutRequest}, {@code retryPayoutRequest} and
 * {@code bulkRetryFailedPayouts} against the real {@link IdempotencyGuard}: a retry with the same key must
 * not decide or re-drive the workflow a second time, and a reused key with a different body must be refused.
 * The workflow is replaced by a stand-in that, like the real one, refuses a retry once the payout has left
 * the failed state.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("approvePayout, retryPayout and bulkRetryPayouts replay an identical retry and refuse a reused key with a different body")
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
                mock(PayoutRecoveryService.class),
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

    /**
     * A stand-in for the payout workflow: a payout in {@code retryable} is failed and takes one retry,
     * after which it is processing and every further retry is refused by state, exactly as the workflow's
     * update validator does. {@code executed} records each retry that actually went through.
     */
    private PayoutRequestMutationResolver resolverWithFailedPayouts(Set<String> retryable, List<String> executed) {
        Set<String> failed = Collections.synchronizedSet(new HashSet<>(retryable));
        PayoutProcess process = mock(PayoutProcess.class);
        when(process.retry(anyString(), anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            if (!failed.remove(id)) {
                return Mono.error(new TranslatedRefusal(ErrorCode.PAYOUT_STATE_INVALID, "payout " + id + " is not failed"));
            }
            executed.add(id);
            return Mono.just(payoutRequest(id));
        });
        return new PayoutRequestMutationResolver(mock(PayoutRequestService.class), mock(TenantReads.class),
                mock(PayoutRecoveryService.class),
                mock(com.pml.booking.service.EscrowService.class), mock(com.pml.booking.security.PayoutAccess.class), process,
                guard, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("retryPayoutRequest: a second press with a different key is refused by state and uses no second attempt")
    void retryWithADifferentKeyIsRefusedByState() {
        List<String> executed = new ArrayList<>();
        PayoutRequestMutationResolver resolver = resolverWithFailedPayouts(Set.of("payout-1"), executed);

        BookingFixture.asAdmin("admin-1", resolver.retryPayoutRequest("payout-1", "payout-retry-a-" + System.nanoTime()), "ROLE_ADMIN").block();

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.retryPayoutRequest("payout-1", "payout-retry-b-" + System.nanoTime()), "ROLE_ADMIN").block())
                .isInstanceOf(TranslatedRefusal.class);
        assertThat(executed).hasSize(1);
    }

    @Test
    @DisplayName("bulkRetryFailedPayouts: the same key and the same ids in another order is one execution and the same answer")
    void bulkReplayInAnotherOrderRunsOnce() {
        String key = "payout-bulk-replay-" + System.nanoTime();
        List<String> executed = new ArrayList<>();
        PayoutRequestMutationResolver resolver = resolverWithFailedPayouts(Set.of("payout-1", "payout-2", "payout-3"), executed);

        BulkPayoutOperationResponse first = BookingFixture.asAdmin("admin-1",
                resolver.bulkRetryFailedPayouts(List.of("payout-1", "payout-2", "payout-3"), key), "ROLE_ADMIN").block();
        BulkPayoutOperationResponse second = BookingFixture.asAdmin("admin-1",
                resolver.bulkRetryFailedPayouts(List.of("payout-3", "payout-1", "payout-2", "payout-1"), key), "ROLE_ADMIN").block();

        assertThat(executed).as("each payout is re-driven once").containsExactlyInAnyOrder("payout-1", "payout-2", "payout-3");
        assertThat(first.getProcessedCount()).isEqualTo(3);
        assertThat(second.getProcessedCount()).isEqualTo(3);
        assertThat(second.getFailedPayoutIds()).isEmpty();
    }

    @Test
    @DisplayName("bulkRetryFailedPayouts: the same key for a different set of payouts is refused as key reuse")
    void bulkReusedKeyForADifferentSetIsRefused() {
        String key = "payout-bulk-mismatch-" + System.nanoTime();
        List<String> executed = new ArrayList<>();
        PayoutRequestMutationResolver resolver = resolverWithFailedPayouts(Set.of("payout-1", "payout-2"), executed);

        BookingFixture.asAdmin("admin-1", resolver.bulkRetryFailedPayouts(List.of("payout-1"), key), "ROLE_ADMIN").block();

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.bulkRetryFailedPayouts(List.of("payout-1", "payout-2"), key), "ROLE_ADMIN").block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(executed).containsExactly("payout-1");
    }

    @Test
    @DisplayName("bulkRetryFailedPayouts: a payout that cannot be retried is skipped and reported, the others still go")
    void bulkSkipsAnUnretryablePayout() {
        List<String> executed = new ArrayList<>();
        PayoutRequestMutationResolver resolver = resolverWithFailedPayouts(Set.of("payout-1", "payout-3"), executed);

        BulkPayoutOperationResponse response = BookingFixture.asAdmin("admin-1",
                resolver.bulkRetryFailedPayouts(List.of("payout-1", "payout-2", "payout-3"), "payout-bulk-skip-" + System.nanoTime()),
                "ROLE_ADMIN").block();

        assertThat(executed).containsExactly("payout-1", "payout-3");
        assertThat(response.getProcessedCount()).isEqualTo(2);
        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getFailedPayoutIds()).containsExactly("payout-2");
    }

    @Test
    @DisplayName("bulkRetryFailedPayouts: an empty list is refused and nothing is executed")
    void bulkEmptyListIsRefused() {
        List<String> executed = new ArrayList<>();
        PayoutRequestMutationResolver resolver = resolverWithFailedPayouts(Set.of("payout-1"), executed);

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.bulkRetryFailedPayouts(List.of(), "payout-bulk-empty-" + System.nanoTime()), "ROLE_ADMIN").block())
                .isInstanceOf(ValidationRefusal.class);
        assertThat(executed).isEmpty();
    }

    @Test
    @DisplayName("bulkRetryFailedPayouts: an oversized list is refused, never trimmed, and nothing is executed")
    void bulkOversizedListIsRefused() {
        List<String> executed = new ArrayList<>();
        Set<String> all = new HashSet<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i <= PayoutRequestMutationResolver.MAX_BULK_PAYOUTS; i++) {
            ids.add("payout-" + i);
            all.add("payout-" + i);
        }
        PayoutRequestMutationResolver resolver = resolverWithFailedPayouts(all, executed);

        assertThatThrownBy(() -> BookingFixture.asAdmin("admin-1",
                        resolver.bulkRetryFailedPayouts(ids, "payout-bulk-large-" + System.nanoTime()), "ROLE_ADMIN").block())
                .isInstanceOf(ValidationRefusal.class);
        assertThat(executed).isEmpty();
    }
}
