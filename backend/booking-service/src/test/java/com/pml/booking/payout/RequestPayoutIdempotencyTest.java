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
import com.pml.booking.it.BookingFixture;
import com.pml.booking.security.TenantReads;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.booking.web.graphql.mutation.PayoutRequestMutationResolver;
import com.pml.booking.workflow.payout.PayoutProcess;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.dto.authorization.AuthorizationResult;
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
 * ET-PLT-007-R6 · {@code createPayoutRequest} against the real {@link IdempotencyGuard}: the
 * mutation used to answer a reused key with a bare lookup that never checked the body matched
 * (F-053-adjacent gap); it now replays only an identical retry and refuses a mismatch.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("createPayoutRequest replays an identical retry and refuses a reused key with a different body")
class RequestPayoutIdempotencyTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static IdempotencyGuard guard;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_payout_idempotency"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test_payout",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(redis, ledger, new ObjectMapper().findAndRegisterModules(), Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    private PayoutRequestMutationResolver resolverCountingRequests(AtomicInteger requestCount) {
        PayoutProcess process = mock(PayoutProcess.class);
        when(process.request(any(), anyString(), anyString())).thenAnswer(call -> {
            int n = requestCount.incrementAndGet();
            PayoutRequest request = new PayoutRequest();
            request.setId("payout-" + n);
            request.setStatus(PayoutRequestStatus.PENDING);
            request.setRequestedAmount(BigDecimal.valueOf(500));
            return Mono.just(request);
        });

        IdentityServiceClient identity = mock(IdentityServiceClient.class);
        when(identity.checkAuthorization(any())).thenReturn(Mono.just(AuthorizationResult.builder().authorized(true).build()));
        com.pml.booking.service.EscrowService escrows = mock(com.pml.booking.service.EscrowService.class);
        com.pml.booking.domain.model.EventEscrowAccount escrow = new com.pml.booking.domain.model.EventEscrowAccount();
        escrow.setId("escrow-1");
        escrow.setEventId("event-1");
        escrow.setOrganizationId("org-1");
        when(escrows.findById("escrow-1")).thenReturn(Mono.just(escrow));

        return new PayoutRequestMutationResolver(mock(PayoutRequestService.class), mock(TenantReads.class),
                mock(PayoutRecoveryService.class), identity, escrows, new com.pml.booking.security.PayoutAccess(identity), process, guard, new ObjectMapper().findAndRegisterModules());
    }

    private CreatePayoutRequestInput input(String key) {
        return new CreatePayoutRequestInput("org-1", "event-1", "escrow-1", "bank-1", new BigDecimal("500.00"), "ZMW",
                null, null, null, key);
    }

    @Test
    @DisplayName("the same key and the same body: one workflow start, the same payout request handed back twice")
    void replayDoesNotStartTheWorkflowTwice() {
        String key = "payout-replay-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        PayoutRequestMutationResolver resolver = resolverCountingRequests(requests);

        PayoutRequest first = BookingFixture.asOrganizer("org-user-1", "org-1", resolver.createPayoutRequest(input(key))).block();
        PayoutRequest second = BookingFixture.asOrganizer("org-user-1", "org-1", resolver.createPayoutRequest(input(key))).block();

        assertThat(requests.get()).as("the payout workflow is started exactly once").isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("the same key with a different amount is refused, and the workflow is never started for the mismatch")
    void reusedKeyWithADifferentAmountIsRefused() {
        String key = "payout-mismatch-" + System.nanoTime();
        AtomicInteger requests = new AtomicInteger();
        PayoutRequestMutationResolver resolver = resolverCountingRequests(requests);

        BookingFixture.asOrganizer("org-user-1", "org-1", resolver.createPayoutRequest(input(key))).block();

        CreatePayoutRequestInput different = new CreatePayoutRequestInput("org-1", "event-1", "escrow-1", "bank-1",
                new BigDecimal("900.00"), "ZMW", null, null, null, key);
        assertThatThrownBy(() -> BookingFixture.asOrganizer("org-user-1", "org-1", resolver.createPayoutRequest(different)).block())
                .isInstanceOf(IdempotencyKeyReusedRefusal.class);
        assertThat(requests.get()).isEqualTo(1);
    }
}
