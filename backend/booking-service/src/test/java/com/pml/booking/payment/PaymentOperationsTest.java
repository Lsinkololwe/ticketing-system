package com.pml.booking.payment;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.service.PaymentOperations;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PaymentRiskService;
import com.pml.booking.web.graphql.dto.PaymentAttemptFilterInput;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.TestClock;
import com.pml.shared.testing.MongoReplicaSet;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The operator's payment tools: search and filters, what is stuck, re-driving a collection by asking the
 * provider, and the risk summary. Nothing here decides an outcome the provider did not give.
 */
@Tag("L2")
@Tag("ET-ADM-003")
@DisplayName("ET-ADM-003 · payment attempts: filters, stuck list, resume and bulk retry never invent an outcome; risk summary adds up")
class PaymentOperationsTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private PaymentOutcomeService outcomes;
    private PaymentOperations operations;
    private PaymentRiskService risk;
    private String event;
    private String buyer;

    @BeforeAll
    static void connect() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_payment_ops");
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void setUp() {
        event = "ev-" + UUID.randomUUID();
        buyer = "buyer-" + UUID.randomUUID();
        outcomes = Mockito.mock(PaymentOutcomeService.class);
        when(outcomes.verifyAndApply(anyString())).thenReturn(Mono.empty());
        when(outcomes.resume(anyString())).thenReturn(Mono.empty());
        var clock = TestClock.frozenAt(NOW);
        risk = new PaymentRiskService(template, clock);
        operations = new PaymentOperations(template, outcomes, risk, clock);
    }

    private PaymentAttempt attempt(PaymentAttemptType type, PaymentAttemptStatus status, String amount, Duration age, String failureCode) {
        return template.insert(PaymentAttempt.builder().depositId("dep-" + UUID.randomUUID()).attemptNumber("PA-" + UUID.randomUUID())
                .attemptType(type).status(status).amount(new BigDecimal(amount)).eventId(event).buyerId(buyer).reservationId("res-" + UUID.randomUUID())
                .provider("pawapay").payerPhone("+260971000" + (int) (Math.random() * 900 + 100)).failureCode(failureCode)
                .createdAt(NOW.minus(age)).updatedAt(NOW.minus(age)).build()).block();
    }

    private PaymentAttemptFilterInput filter(PaymentAttemptStatus status, PaymentAttemptType type, String ref, Integer stuck) {
        return new PaymentAttemptFilterInput(status == null ? null : List.of(status), type, null, event, null, null, null, null, null, null, null, null, ref, stuck);
    }

    // ---- lists ---------------------------------------------------------------------------------

    @Test
    @DisplayName("search narrows by status, type (rows from before the type existed count as collections), reference and amount")
    void search() {
        PaymentAttempt processing = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.PROCESSING, "100.00", Duration.ofHours(2), null);
        PaymentAttempt completed = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.COMPLETED, "500.00", Duration.ofHours(3), null);
        PaymentAttempt refund = attempt(PaymentAttemptType.REFUND, PaymentAttemptStatus.PROCESSING, "40.00", Duration.ofHours(2), null);
        PaymentAttempt legacy = attempt(null, PaymentAttemptStatus.CREATED, "20.00", Duration.ofHours(4), null);

        assertThat(ids(operations.search(filter(PaymentAttemptStatus.PROCESSING, null, null, null), null))).containsExactlyInAnyOrder(processing.getId(), refund.getId());
        assertThat(ids(operations.search(filter(null, PaymentAttemptType.COLLECT, null, null), null)))
                .containsExactlyInAnyOrder(processing.getId(), completed.getId(), legacy.getId());
        assertThat(ids(operations.search(filter(null, PaymentAttemptType.REFUND, null, null), null))).containsExactly(refund.getId());
        assertThat(ids(operations.search(filter(null, null, completed.getDepositId(), null), null))).containsExactly(completed.getId());
        assertThat(ids(operations.search(new PaymentAttemptFilterInput(null, null, null, event, null, null, null, null, null, null,
                new BigDecimal("50.00"), new BigDecimal("200.00"), null, null), null))).containsExactly(processing.getId());
        assertThat(ids(operations.search(filter(null, null, null, 180), null)))
                .as("collections in flight for three hours or more").containsExactly(legacy.getId());
    }

    @Test
    @DisplayName("search refuses an inverted range, an unknown risk level and a non-positive stuck window")
    void searchValidation() {
        assertThat(refusal(operations.search(new PaymentAttemptFilterInput(null, null, null, event, null, null, null, "SEVERE", null, null, null, null, null, null), null)).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(operations.search(new PaymentAttemptFilterInput(null, null, null, event, null, null, null, null, null, null,
                new BigDecimal("9"), new BigDecimal("1"), null, null), null)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(operations.search(filter(null, null, null, 0), null)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("stuck lists only collections still in flight past the window: never refunds, payouts, finished or fulfilled ones")
    void stuck() {
        PaymentAttempt old = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofMinutes(45), null);
        PaymentAttempt fresh = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofMinutes(5), null);
        PaymentAttempt refund = attempt(PaymentAttemptType.REFUND, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofHours(2), null);
        PaymentAttempt done = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.COMPLETED, "10.00", Duration.ofHours(2), null);
        PaymentAttempt fulfilled = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.CONFIRMED, "10.00", Duration.ofHours(2), null);
        fulfilled.setFulfilled(true);
        template.save(fulfilled).block();
        PaymentAttempt unfulfilled = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.CONFIRMED, "10.00", Duration.ofHours(2), null);

        List<String> stuck = ids(operations.stuck(30, null));

        assertThat(stuck).contains(old.getId(), unfulfilled.getId()).doesNotContain(fresh.getId(), refund.getId(), done.getId(), fulfilled.getId());
        assertThat(refusal(operations.stuck(0, null)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(operations.stuck(60 * 24 * 31, null)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    // ---- recovery ------------------------------------------------------------------------------

    @Test
    @DisplayName("resume asks the provider and applies its answer; it changes nothing itself, and refuses what is already complete or not a collection")
    void resume() {
        PaymentAttempt stuck = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofHours(1), null);

        PaymentAttempt after = operations.resume(stuck.getDepositId()).block();

        verify(outcomes).verifyAndApply(stuck.getDepositId());
        verify(outcomes).resume(stuck.getReservationId());
        assertThat(after.getStatus()).as("the provider said nothing new, so nothing changed").isEqualTo(PaymentAttemptStatus.PROCESSING);

        PaymentAttempt complete = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.COMPLETED, "10.00", Duration.ofHours(1), null);
        PaymentAttempt refund = attempt(PaymentAttemptType.REFUND, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofHours(1), null);
        assertThat(refusal(operations.resume(complete.getDepositId())).errorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        assertThat(refusal(operations.resume(refund.getDepositId())).errorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        assertThat(refusal(operations.resume("dep-never")).errorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        verify(outcomes, never()).verifyAndApply(complete.getDepositId());
        verify(outcomes, never()).verifyAndApply(refund.getDepositId());
    }

    @Test
    @DisplayName("bulk retry asks again only where the provider has not answered; a decline is skipped, a missing payment is named, duplicates are one")
    void bulkRetry() {
        PaymentAttempt stuck = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofHours(1), null);
        PaymentAttempt unreachable = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.FAILED, "10.00", Duration.ofHours(1), "PROVIDER_TIMEOUT");
        PaymentAttempt declined = attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.FAILED, "10.00", Duration.ofHours(1), "INSUFFICIENT_FUNDS");
        PaymentAttempt refund = attempt(PaymentAttemptType.REFUND, PaymentAttemptStatus.PROCESSING, "10.00", Duration.ofHours(1), null);

        List<PaymentOperations.Outcome> results = operations.retryMany(Arrays.asList(stuck.getDepositId(), stuck.getDepositId(),
                unreachable.getDepositId(), declined.getDepositId(), refund.getDepositId(), "dep-never")).block();

        assertThat(results).extracting(PaymentOperations.Outcome::result)
                .containsExactly("RETRIED", "RETRIED", "SKIPPED", "SKIPPED", "NOT_FOUND");
        verify(outcomes, times(1)).verifyAndApply(stuck.getDepositId());
        verify(outcomes, times(1)).verifyAndApply(unreachable.getDepositId());
        verify(outcomes, never()).verifyAndApply(declined.getDepositId());

        // idempotent: asking again re-asks the provider and applies whatever it says; it never doubles an outcome
        List<PaymentOperations.Outcome> again = operations.retryMany(List.of(stuck.getDepositId())).block();
        assertThat(again).extracting(PaymentOperations.Outcome::result).containsExactly("RETRIED");
        assertThat(template.findById(stuck.getId(), PaymentAttempt.class).block().getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
    }

    @Test
    @DisplayName("bulk retry takes between one and fifty payments")
    void bulkLimits() {
        assertThat(refusal(operations.retryMany(List.of())).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(operations.retryMany(null)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        List<String> fiftyOne = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "dep-" + i).toList();
        assertThat(refusal(operations.retryMany(fiftyOne)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    // ---- risk ----------------------------------------------------------------------------------

    @Test
    @DisplayName("the risk summary counts each collection in the window once, at one level, and the levels add up to the total")
    void riskSummary() {
        attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.COMPLETED, "50.00", Duration.ofHours(1), null);
        attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.CONFIRMED, "50000.00", Duration.ofHours(2), null);
        attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.FAILED, "50.00", Duration.ofHours(3), "DECLINED");
        attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.COMPLETED, "50.00", Duration.ofHours(30), null);
        attempt(PaymentAttemptType.REFUND, PaymentAttemptStatus.PROCESSING, "50.00", Duration.ofHours(1), null);

        long inWindow = template.count(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("eventId").is(event)
                        .and("createdAt").gte(NOW.minus(Duration.ofHours(24))).and("attemptType").is(PaymentAttemptType.COLLECT)), PaymentAttempt.class).block();
        PaymentRiskService.Summary summary = risk.summary(24).block();
        PaymentRiskService.Summary again = risk.summary(24).block();

        assertThat(inWindow).isEqualTo(3);
        assertThat(summary.low() + summary.medium() + summary.high()).isEqualTo(summary.evaluated());
        assertThat(summary.flagged()).isEqualTo(summary.medium() + summary.high());
        assertThat(summary.evaluated()).isGreaterThanOrEqualTo(inWindow);
        assertThat(again.evaluated()).as("assessing is stored, so asking again does not change the answer").isEqualTo(summary.evaluated());
        assertThat(again.high()).isEqualTo(summary.high());
        assertThat(summary.amountAtRisk().signum()).isGreaterThanOrEqualTo(0);
    }

    private static List<String> ids(Mono<com.pml.booking.service.Pages.Slice<PaymentAttempt>> page) {
        return page.block().data().stream().map(PaymentAttempt::getId).toList();
    }
}
