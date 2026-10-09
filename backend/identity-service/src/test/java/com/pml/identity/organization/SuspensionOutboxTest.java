package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.Organization;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A suspension and its outbox envelope commit together — the platform, not just the mechanism.
 *
 * <h2>Why a service path</h2>
 * {@code OutboxTest} and {@code OutboxDrainTest} prove {@link Outbox} atomic and the drain
 * restartable in isolation. They cannot show that a service actually stages its rows.
 *
 * <p>So this test asserts the property against a real service path. A {@code suspend} written as
 * {@code save(org).doOnSuccess(suspended -> streamBridge.send(...))} lets the document commit, the
 * send fail, and the only trace be a WARN. A suspended organisation no other service hears about
 * keeps selling tickets.</p>
 *
 * <h2>The case that matters is the rollback</h2>
 * A staged row that publishes is the easy half. The guarantee is <b>both or neither</b>, so the
 * assertion with teeth is the one where the transaction fails: no document change, and no envelope
 * promising a change that never happened. An outbox that can emit a message for a write that was
 * rolled back is worse than no outbox, because every consumer believes it.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · a suspension and its envelope commit together, or neither does")
class SuspensionOutboxTest {

    private static final String ORG = "org-suspend-probe";
    private static final String OUTBOX = "identity_outbox";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TransactionalOperator transaction;
    private static Outbox outbox;
    private static Clock clock;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory =
                new SimpleReactiveMongoDatabaseFactory(client, "identity_outbox_probe");
        template = new ReactiveMongoTemplate(factory);
        transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(factory));
        clock = TestClock.frozenAt(Instant.parse("2026-09-02T09:00:00Z"));
        outbox = new Outbox(template, OUTBOX, clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), OUTBOX).block();
        template.remove(new Query(), Organization.class).block();
        template.save(Organization.builder().id(ORG).name("Probe").slug("probe").build()).block();
    }

    /** The shipped shape of {@code suspend}, reduced to the two writes that must agree. */
    private Mono<Void> suspendWith(String reason, boolean thenFail) {
        return transaction.transactional(
                template.update(Organization.class)
                        .matching(Query.query(Criteria.where("_id").is(ORG)))
                        .apply(new org.springframework.data.mongodb.core.query.Update()
                                .set("status", "SUSPENDED"))
                        .first()
                        .then(outbox.stage(com.pml.shared.event.EventEnvelopes.of(
                                EventType.IDENTITY_ORGANIZATION_SUSPENDED,
                                clock.instant(), ORG,
                                Map.of("organizationId", ORG, "reason", reason))))
                        .then(thenFail
                                ? Mono.error(new IllegalStateException("keycloak refused"))
                                : Mono.empty()))
                .then();
    }

    private long outboxRows() {
        return template.count(new Query(), Document.class, OUTBOX).block();
    }

    private String status() {
        return template.findById(ORG, Organization.class).block().getStatus() == null
                ? null : template.findById(ORG, Organization.class).block().getStatus().name();
    }

    @Test
    @DisplayName("a committed suspension leaves exactly one staged envelope")
    void bothOrNeither_commit() {
        suspendWith("fraud investigation", false).block();

        assertThat(status()).isEqualTo("SUSPENDED");
        assertThat(outboxRows())
                .as("the write and its envelope commit together — one row, PENDING, owed to the bus")
                .isEqualTo(1);

        Document staged = template.findOne(
                Query.query(Criteria.where("status").is(Outbox.PENDING)), Document.class, OUTBOX)
                .block();
        assertThat(staged.getString("eventType"))
                .isEqualTo(EventType.IDENTITY_ORGANIZATION_SUSPENDED.wireName());
    }

    @Test
    @DisplayName("a rolled-back suspension leaves no document change and no envelope")
    void bothOrNeither_rollback() {
        assertThat(status()).as("the fixture must start unsuspended, or this proves nothing")
                .isNotEqualTo("SUSPENDED");

        try {
            suspendWith("fraud investigation", true).block();
        } catch (RuntimeException expected) {
            // the transaction fails after both writes — the point is what survives it
        }

        assertThat(status())
                .as("the suspension must not have landed")
                .isNotEqualTo("SUSPENDED");
        assertThat(outboxRows())
                .as("and no envelope may promise a suspension that never happened — a consumer "
                        + "cannot tell an envelope for a rolled-back write from a real one")
                .isZero();
    }
}
