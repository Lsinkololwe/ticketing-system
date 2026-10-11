package com.pml.booking.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.model.PlatformTransfer;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.service.AdminFinanceReads;
import com.pml.booking.service.ChargebackRecoveryOps;
import com.pml.booking.service.DualControlService;
import com.pml.booking.service.PaymentOperations;
import com.pml.booking.service.PaymentRiskService;
import com.pml.booking.service.PlatformTransferService;
import com.pml.booking.web.graphql.dto.PlatformTransferInput;
import com.pml.booking.web.graphql.query.AdminOpsResolver;
import com.pml.booking.web.graphql.query.AdminOpsResolver.PlatformTransferResult;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.IdempotencyKeyReusedRefusal;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code transferBetweenPlatformAccounts} against the real {@link IdempotencyGuard}: a real Mongo
 * ledger and a real Redis fast path, with the transfer itself replaced by a counter so the one
 * thing under test is that a retry cannot move the platform's money a second time and a reused
 * key cannot be answered with a transfer of a different sum.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@DisplayName("transferBetweenPlatformAccounts replays an identical retry and refuses a reused key with a different amount")
class PlatformTransferIdempotencyTest {

    private static MongoClient client;
    private static IdempotencyGuard guard;
    private static ObjectMapper mapper;

    private final AtomicInteger ledgerMovements = new AtomicInteger();
    private AdminOpsResolver resolver;

    @BeforeAll
    static void connect() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        ReactiveMongoTemplate mongo = BookingFixture.template(client, "booking_platform_transfer_idempotency");
        mapper = new ObjectMapper().findAndRegisterModules();
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "booking_idempotency_ledger_test_transfer",
                TestClock.frozenAt(Instant.now()), Duration.ofHours(24));
        guard = new IdempotencyGuard(BookingFixture.redis(), ledger, mapper, Duration.ofSeconds(5));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void wire() {
        PlatformTransferService transfers = Mockito.mock(PlatformTransferService.class);
        when(transfers.needsSecondPerson(any())).thenReturn(false);
        when(transfers.execute(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            ledgerMovements.incrementAndGet();
            return Mono.just(PlatformTransfer.builder()
                    .id("transfer-" + UUID.randomUUID())
                    .fromAccount(call.getArgument(0))
                    .toAccount(call.getArgument(1))
                    .amount(call.getArgument(2))
                    .reason(call.getArgument(3))
                    .idempotencyKey(call.getArgument(4))
                    .executedBy(call.getArgument(5))
                    .build());
        });
        resolver = new AdminOpsResolver(Mockito.mock(AdminFinanceReads.class), Mockito.mock(PaymentOperations.class),
                Mockito.mock(PaymentRiskService.class), Mockito.mock(DualControlService.class), transfers,
                Mockito.mock(ChargebackRecoveryOps.class), Clock.systemUTC(), guard, mapper);
    }

    private static PlatformTransferInput input(String key, String amount) {
        return new PlatformTransferInput(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE,
                new BigDecimal(amount), "month end reserve top up", key);
    }

    private PlatformTransferResult transfer(PlatformTransferInput input) {
        return BookingFixture.as("fin-1", TenantScope.platformAdministrator("fin-1", Set.of()),
                resolver.transferBetweenPlatformAccounts(input), "ROLE_FINANCE").block();
    }

    @Test
    @DisplayName("the same key and the same body: one ledger movement, the same transfer handed back twice")
    void replayMovesMoneyOnce() {
        String key = "transfer-replay-" + System.nanoTime();

        PlatformTransferResult first = transfer(input(key, "250.00"));
        PlatformTransferResult second = transfer(input(key, "250.00"));

        assertThat(ledgerMovements.get()).as("the platform's money moves exactly once").isEqualTo(1);
        assertThat(first.executed()).isTrue();
        assertThat(second.transfer().getId()).isEqualTo(first.transfer().getId());
        assertThat(second.transfer().getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("the same key with a different amount: IDEMPOTENCY_KEY_REUSED, and no second movement")
    void reusedKeyWithADifferentAmountIsRefused() {
        String key = "transfer-mismatch-" + System.nanoTime();
        transfer(input(key, "250.00"));

        assertThatThrownBy(() -> transfer(input(key, "251.00")))
                .isInstanceOf(IdempotencyKeyReusedRefusal.class)
                .satisfies(refused -> assertThat(((IdempotencyKeyReusedRefusal) refused).errorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(ledgerMovements.get()).as("the mismatched retry never reaches the ledger").isEqualTo(1);
    }

    @Test
    @DisplayName("five simultaneous submissions of one key: one movement and all five get the same transfer")
    void parallelSubmissionsMoveMoneyOnce() {
        String key = "transfer-parallel-" + System.nanoTime();

        Concurrency.Outcome<PlatformTransferResult> outcome = Concurrency.inParallel(5,
                caller -> transfer(input(key, "250.00")));

        assertThat(outcome.failures()).as("no caller is refused or errors").isEmpty();
        assertThat(outcome.successes()).hasSize(5);
        assertThat(ledgerMovements.get()).as("the platform's money moves exactly once").isEqualTo(1);
        assertThat(outcome.successes()).extracting(result -> result.transfer().getId())
                .containsOnly(outcome.successes().get(0).transfer().getId());
    }
}
