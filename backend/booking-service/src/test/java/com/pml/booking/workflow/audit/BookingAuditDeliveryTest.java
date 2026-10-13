package com.pml.booking.workflow.audit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAuditForwarder;
import com.pml.shared.security.tenancy.PlatformWideAuditMetrics;
import com.pml.shared.security.tenancy.PlatformWideAuditOutbox;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;
import com.pml.shared.security.tenancy.TemporalPlatformWideAuditDelivery;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TemporalDevServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * booking's own bindings of the shared audit delivery: the classes it registers run on its audit queue,
 * against a real Temporal server, and carry a row of its outbox to identity.
 * OWASP A04:2021 · A09:2021.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@Tag("OWASP")
@DisplayName("booking's audit delivery runs on booking's audit queue and carries a row of its outbox to identity")
class BookingAuditDeliveryTest {

    private static WireMockServer identity;
    private static MongoClient mongo;

    @BeforeAll
    static void start() {
        identity = new WireMockServer(options().dynamicPort());
        identity.start();
        mongo = MongoClients.create(MongoReplicaSet.connectionString());
    }

    @AfterAll
    static void stop() {
        identity.stop();
        mongo.close();
    }

    @Test
    @DisplayName("the workflow and its activity are registered on the audit queue, by constant")
    void registeredOnTheAuditQueue() {
        assertThat(BookingAuditDeliveryWorkflowImpl.class.getAnnotation(WorkflowImpl.class).taskQueues())
                .containsExactly(TaskQueues.AUDIT);
        assertThat(BookingAuditDeliveryActivitiesImpl.class.getAnnotation(ActivityImpl.class).taskQueues())
                .containsExactly(TaskQueues.AUDIT);
        assertThat(TaskQueues.AUDIT).isEqualTo("booking-audit");
    }

    @Test
    @DisplayName("a row of the outbox is started on the audit queue by the service's own classes and delivered once")
    void deliversARow() {
        identity.stubFor(post(urlPathEqualTo("/api/internal/audit/platform-wide")).willReturn(aResponse().withStatus(202)));
        var template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(mongo,
                "booking_audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)));
        var outbox = new PlatformWideAuditOutbox(template, BookingCollections.PLATFORM_WIDE_AUDIT_OUTBOX, Clock.systemUTC());
        var metrics = new PlatformWideAuditMetrics("booking-service", outbox, new SimpleMeterRegistry(), Clock.systemUTC());
        var forwarder = new PlatformWideAuditForwarder(outbox, WebClient.builder().baseUrl(identity.baseUrl()).build(), metrics);
        var client = WorkflowClient.newInstance(WorkflowServiceStubs.newServiceStubs(
                        WorkflowServiceStubsOptions.newBuilder().setTarget(TemporalDevServer.target()).build()),
                WorkflowClientOptions.newBuilder().setNamespace(TemporalDevServer.NAMESPACE).build());
        var workers = WorkerFactory.newInstance(client);
        var worker = workers.newWorker(TaskQueues.AUDIT);
        worker.registerWorkflowImplementationTypes(BookingAuditDeliveryWorkflowImpl.class);
        worker.registerActivitiesImplementations(new BookingAuditDeliveryActivitiesImpl(forwarder));
        workers.start();
        try {
            String id = outbox.append(new PlatformWideAuditRecord("admin-1", "ROLE_ADMIN", "booking-service",
                    PlatformWideAccess.Reason.CALLER_SCOPED_QUERY, null, Instant.now().minusSeconds(30))).block();

            new TemporalPlatformWideAuditDelivery(new TemporalGateway(client), TaskQueues.AUDIT).start(id);

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(outbox.find(id).block().state()).isEqualTo(PlatformWideAuditOutbox.DELIVERED));
            assertThat(identity.getAllServeEvents()).hasSize(1);
        } finally {
            workers.shutdownNow();
        }
    }
}
