package com.pml.booking.payment;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.config.BookingIndexInitializer;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.service.PaymentAttemptRecorder.CallOutcome;
import com.pml.booking.service.PaymentAttemptRecorder.ProviderCall;
import com.pml.booking.service.impl.PaymentAttemptRecorderImpl;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payment-attempt recorder against a MongoDB replica set carrying every index the collection has
 * in a running service — both the registry's and the ones the model's annotations create — so a row
 * the unique indexes would refuse is refused here too.
 */
@Tag("L2")
@Tag("ET-PAY-001")
@DisplayName("Every provider call is one payment-attempt row, written before the call and settled by its answer")
class ProviderCallRecordTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static PaymentAttemptRepository attempts;
    private PaymentAttemptRecorderImpl recorder;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_provider_calls"));
        attempts = new ReactiveMongoRepositoryFactory(template).getRepository(PaymentAttemptRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void freshCollectionWithProductionIndexes() {
        template.dropCollection(PaymentAttempt.class).block();
        template.createCollection(PaymentAttempt.class).block();
        MongoMappingContext mapping = (MongoMappingContext) template.getConverter().getMappingContext();
        Flux.fromIterable(new MongoPersistentEntityIndexResolver(mapping).resolveIndexFor(PaymentAttempt.class))
                .concatMap(index -> template.indexOps(PaymentAttempt.class).ensureIndex(index))
                .blockLast();
        new IndexEnsurer(template).ensure(BookingIndexInitializer.specifications().stream()
                .filter(spec -> spec.collection().equals(template.getCollectionName(PaymentAttempt.class)))
                .toList()).block();
        recorder = new PaymentAttemptRecorderImpl(attempts, TestClock.frozenAt(NOW));
    }

    @Test
    @DisplayName("Two checkouts for different payments each get their own row under the unique indexes")
    void twoCollectionsBothRecord() {
        recorder.beforeCollect(intent("intent-a", UUID.randomUUID().toString())).block();
        recorder.beforeCollect(intent("intent-b", UUID.randomUUID().toString())).block();

        List<PaymentAttempt> rows = attempts.findAll().collectList().block();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(PaymentAttempt::getAttemptNumber).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(rows).extracting(PaymentAttempt::getAttemptType).containsOnly(PaymentAttemptType.COLLECT);
    }

    @Test
    @DisplayName("A refund, a payout and a verification deposit are each one typed row linked to what they pay")
    void eachKindIsRecorded() {
        String refundRef = UUID.randomUUID().toString();
        String payoutRef = UUID.randomUUID().toString();
        String depositRef = UUID.randomUUID().toString();

        recorder.beforeCall(new ProviderCall(PaymentAttemptType.REFUND, refundRef, new BigDecimal("150.00"), "ZMW",
                "event-1", "org-1", "refund-1", null, null)).block();
        recorder.beforeCall(new ProviderCall(PaymentAttemptType.PAYOUT, payoutRef, new BigDecimal("900.00"), "ZMW",
                "event-1", "org-1", null, "payout-1", "bank-1")).block();
        recorder.beforeCall(new ProviderCall(PaymentAttemptType.VERIFICATION, depositRef, new BigDecimal("0.53"), "ZMW",
                null, "org-1", null, null, "bank-1")).block();

        PaymentAttempt refund = attempts.findByProviderReference(refundRef).block();
        PaymentAttempt payout = attempts.findByProviderReference(payoutRef).block();
        PaymentAttempt deposit = attempts.findByProviderReference(depositRef).block();
        assertThat(refund.getAttemptType()).isEqualTo(PaymentAttemptType.REFUND);
        assertThat(refund.getRefundRequestId()).isEqualTo("refund-1");
        assertThat(payout.getPayoutRequestId()).isEqualTo("payout-1");
        assertThat(payout.getBankAccountId()).isEqualTo("bank-1");
        assertThat(deposit.getAttemptType()).isEqualTo(PaymentAttemptType.VERIFICATION);
        assertThat(List.of(refund, payout, deposit)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.CREATED);
            assertThat(row.getExpiresAt()).as("only a collection has an approval window").isNull();
        });
    }

    @Test
    @DisplayName("Recording the same call twice leaves one row")
    void aRetriedCallIsOneRow() {
        String ref = UUID.randomUUID().toString();
        ProviderCall call = new ProviderCall(PaymentAttemptType.PAYOUT, ref, new BigDecimal("900.00"), "ZMW",
                "event-1", "org-1", null, "payout-1", "bank-1");

        PaymentAttempt first = recorder.beforeCall(call).block();
        PaymentAttempt second = recorder.beforeCall(call).block();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(attempts.count().block()).isEqualTo(1);
    }

    @Test
    @DisplayName("Accepted moves the row to PROCESSING, refused to REJECTED, and no answer leaves it CREATED")
    void answersSettleTheRow() {
        String accepted = UUID.randomUUID().toString();
        String refused = UUID.randomUUID().toString();
        String silent = UUID.randomUUID().toString();
        for (String ref : List.of(accepted, refused, silent)) {
            recorder.beforeCall(new ProviderCall(PaymentAttemptType.REFUND, ref, BigDecimal.TEN, "ZMW",
                    "event-1", "org-1", "refund-" + ref, null, null)).block();
        }

        recorder.afterCall(accepted, CallOutcome.ACCEPTED, null, null).block();
        recorder.afterCall(refused, CallOutcome.REFUSED, "INVALID_AMOUNT", "Amount below the provider minimum").block();
        recorder.afterCall(silent, CallOutcome.NO_ANSWER, null, null).block();
        recorder.afterCall(refused, CallOutcome.ACCEPTED, null, null).block();

        assertThat(attempts.findByProviderReference(accepted).block().getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        PaymentAttempt rejected = attempts.findByProviderReference(refused).block();
        assertThat(rejected.getStatus()).as("a later answer does not overwrite the first").isEqualTo(PaymentAttemptStatus.REJECTED);
        assertThat(rejected.getFailureCode()).isEqualTo("INVALID_AMOUNT");
        assertThat(attempts.findByProviderReference(silent).block().getStatus()).isEqualTo(PaymentAttemptStatus.CREATED);
    }

    private static PaymentIntent intent(String id, String depositId) {
        return PaymentIntent.builder()
                .id(id)
                .depositId(depositId)
                .reservationId("reservation-" + id)
                .eventId("event-1")
                .userId("buyer-1")
                .amount(new BigDecimal("200.00"))
                .currency("ZMW")
                .phoneNumber("+260970000001")
                .build();
    }
}
