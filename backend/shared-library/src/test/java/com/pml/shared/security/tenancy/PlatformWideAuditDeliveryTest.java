package com.pml.shared.security.tenancy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TemporalDevServer;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Delivery end to end against a real Temporal server, a real MongoDB and a stand-in identity:
 * a reach made while identity is down is delivered once when it returns, a start that Temporal
 * refused is picked up by the next start-up, and starting twice delivers once.
 * OWASP A04:2021 · A09:2021.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@Tag("ET-PLT-015")
@Tag("OWASP")
@DisplayName("a reach made while identity is down is delivered exactly once when it returns, and survives Temporal being down at the start")
class PlatformWideAuditDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

    private static WireMockServer identity;
    private static MongoClient mongo;
    private static ReactiveMongoTemplate template;
    private static WorkflowClient workflowClient;
    private static WorkerFactory workers;
    private static String queue;
    private static PlatformWideAuditOutbox outbox;
    private static PlatformWideAuditMetrics metrics;
    private static SimpleMeterRegistry meters;
    private static TemporalPlatformWideAuditDelivery delivery;

    @BeforeAll
    static void start() {
        identity = new WireMockServer(options().dynamicPort());
        identity.start();
        mongo = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(mongo,
                "delivery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        var clock = TestClock.frozenAt(NOW);
        outbox = new PlatformWideAuditOutbox(template, "test_delivery_outbox", clock);
        meters = new SimpleMeterRegistry();
        metrics = new PlatformWideAuditMetrics("booking-service", outbox, meters, clock);

        WorkflowServiceStubs stubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(TemporalDevServer.target()).build());
        workflowClient = WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder().setNamespace(TemporalDevServer.NAMESPACE).build());
        queue = "test-audit-" + UUID.randomUUID();
        var forwarder = new PlatformWideAuditForwarder(outbox, WebClient.builder().baseUrl(identity.baseUrl()).build(), metrics);
        workers = WorkerFactory.newInstance(workflowClient);
        var worker = workers.newWorker(queue);
        worker.registerWorkflowImplementationTypes(PlatformWideAuditDeliveryWorkflowImpl.class);
        worker.registerActivitiesImplementations(new PlatformWideAuditDeliveryActivities() {
            @Override
            public void forward(String recordId) {
                forwarder.forward(recordId).block(Duration.ofSeconds(25));
            }
        });
        workers.start();
        delivery = new TemporalPlatformWideAuditDelivery(new TemporalGateway(workflowClient), queue);
    }

    @AfterAll
    static void stop() {
        workers.shutdownNow();
        identity.stop();
        mongo.close();
    }

    @BeforeEach
    void reset() {
        identity.resetAll();
        template.remove(new Query(), "test_delivery_outbox").block();
    }

    private String reach() {
        return outbox.append(new PlatformWideAuditRecord("finance-1", "ROLE_FINANCE", "booking-service",
                PlatformWideAccess.Reason.RESERVATION_SUPPORT_READ, "reservation", NOW.minusSeconds(600))).block();
    }

    private void identityAnswers(int status) {
        identity.resetMappings();
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(status)));
    }

    private long accepted() {
        return identity.getAllServeEvents().stream().filter(e -> e.getResponse().getStatus() == 202).count();
    }

    private String state(String id) {
        return outbox.find(id).block().state();
    }

    @Test
    @DisplayName("identity down at the moment of the reach: the row waits PENDING, and is delivered once when identity returns")
    void deliveredOnceAfterTheOutage() {
        identityAnswers(503);
        String id = reach();

        delivery.start(id);

        await().atMost(Duration.ofSeconds(20)).until(() -> identity.getAllServeEvents().size() >= 1);
        assertThat(state(id)).as("still waiting while identity is down").isEqualTo(PlatformWideAuditOutbox.PENDING);
        assertThat(outbox.backlog().block().pending()).isEqualTo(1);

        identityAnswers(202);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(state(id)).isEqualTo(PlatformWideAuditOutbox.DELIVERED));
        assertThat(accepted()).as("identity was told exactly once").isEqualTo(1);
        assertThat(identity.findAll(postRequestedFor(urlPathEqualTo(PlatformWideAuditForwarder.PATH))).get(0)
                .getBodyAsString()).contains(id).contains(NOW.minusSeconds(600).toString());
    }

    @Test
    @DisplayName("starting the same delivery twice, and again after it finished, tells identity once")
    void startingTwiceDeliversOnce() {
        identityAnswers(202);
        String id = reach();

        delivery.start(id);
        delivery.start(id);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(state(id)).isEqualTo(PlatformWideAuditOutbox.DELIVERED));
        delivery.start(id);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(accepted()).isEqualTo(1));
    }

    @Test
    @DisplayName("Temporal unreachable at the start: the row stays PENDING, and the next start-up starts it and it is delivered")
    void temporalDownAtStartIsRedrivenAtNextStart() {
        identityAnswers(202);
        String id = reach();
        WorkflowServiceStubs unreachable = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget("127.0.0.1:1").setRpcTimeout(Duration.ofSeconds(2))
                .setRpcRetryOptions(RpcRetryOptions.newBuilder().setExpiration(Duration.ofSeconds(3)).build()).build());
        var downClient = WorkflowClient.newInstance(unreachable,
                WorkflowClientOptions.newBuilder().setNamespace(TemporalDevServer.NAMESPACE).build());
        var unreachableDelivery = new TemporalPlatformWideAuditDelivery(new TemporalGateway(downClient), queue);
        var sink = new OutboxPlatformWideAuditSink(outbox, unreachableDelivery, metrics);

        sink.record(new PlatformWideAuditRecord("finance-2", "ROLE_FINANCE", "booking-service",
                PlatformWideAccess.Reason.BUYER_BOOKINGS_READ, null, NOW)).block();

        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                assertThat(meters.find("platform.wide.audit.delivery.start.failures").counter().count()).isEqualTo(1.0));
        assertThat(outbox.backlog().block().pending()).as("both rows still wait").isEqualTo(2);
        assertThat(identity.getAllServeEvents()).as("identity was told nothing").isEmpty();
        assertThatThrownBy(() -> unreachableDelivery.start(id)).isInstanceOf(RuntimeException.class);

        // the service restarts, Temporal is back: the one-time pass starts what is waiting
        new PlatformWideAuditDrain(outbox, delivery, metrics).redrive().block();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(outbox.backlog().block().pending()).isZero());
        assertThat(accepted()).isEqualTo(2);
    }

    @Test
    @DisplayName("a delivery identity refuses for good completes with the row REFUSED and does not retry")
    void refusalCompletes() {
        identityAnswers(400);
        String id = reach();

        delivery.start(id);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(state(id)).isEqualTo(PlatformWideAuditOutbox.REFUSED));
        await().during(Duration.ofSeconds(7)).atMost(Duration.ofSeconds(14)).untilAsserted(() ->
                assertThat(identity.getAllServeEvents()).hasSize(1));
    }
}
