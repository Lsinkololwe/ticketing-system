package com.pml.shared.security.tenancy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The hop from a service's outbox to identity's audit trail, against a stand-in identity: what is
 * sent, what each answer does to the row, and that nothing but the facts ever leaves.
 * OWASP A04:2021 · A09:2021.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@Tag("OWASP")
@DisplayName("a row is delivered to identity as its facts with its record id and time, and each answer settles or keeps it")
class PlatformWideAuditForwarderTest {

    private static final String COLLECTION = "test_forwarder_outbox";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");
    private static final Instant HAPPENED = Instant.parse("2026-10-13T08:15:00Z");

    private static WireMockServer identity;
    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger forwarderLog = (Logger) LoggerFactory.getLogger(PlatformWideAuditForwarder.class);
    private PlatformWideAuditOutbox outbox;
    private PlatformWideAuditForwarder forwarder;
    private SimpleMeterRegistry meters;

    @BeforeAll
    static void start() {
        identity = new WireMockServer(options().dynamicPort());
        identity.start();
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client,
                "forwarder_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
    }

    @AfterAll
    static void stop() {
        identity.stop();
        client.close();
    }

    @BeforeEach
    void wire() {
        identity.resetAll();
        template.remove(new Query(), COLLECTION).block();
        meters = new SimpleMeterRegistry();
        var clock = TestClock.frozenAt(NOW);
        outbox = new PlatformWideAuditOutbox(template, COLLECTION, clock);
        var metrics = new PlatformWideAuditMetrics("booking-service", outbox, meters, clock);
        forwarder = new PlatformWideAuditForwarder(outbox, WebClient.builder().baseUrl(identity.baseUrl()).build(), metrics);
        logged.start();
        forwarderLog.addAppender(logged);
    }

    @AfterEach
    void detach() {
        forwarderLog.detachAppender(logged);
    }

    private String reach() {
        return outbox.append(new PlatformWideAuditRecord("finance-1", "ROLE_FINANCE", "booking-service",
                PlatformWideAccess.Reason.RESERVATION_SUPPORT_READ, "reservation", HAPPENED)).block();
    }

    private double count(String name) {
        var counter = meters.find(name).tag("service", "booking-service").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("the body is the record id, when it happened and the five facts, and the row becomes DELIVERED")
    void deliversTheFacts() {
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(202)));
        String id = reach();

        assertThat(forwarder.forward(id).block()).isEqualTo(PlatformWideAuditForwarder.Outcome.DELIVERED);

        identity.verify(1, postRequestedFor(urlPathEqualTo(PlatformWideAuditForwarder.PATH))
                .withRequestBody(equalToJson("""
                        {"recordId":"%s","occurredAt":"2026-10-13T08:15:00Z","actorSub":"finance-1",
                         "role":"ROLE_FINANCE","service":"booking-service",
                         "reason":"RESERVATION_SUPPORT_READ","operation":"reservation"}""".formatted(id), false, false)));
        assertThat(identity.getAllServeEvents().get(0).getRequest().getBodyAsString()).doesNotContain("Bearer", "token");
        assertThat(outbox.find(id).block().state()).isEqualTo(PlatformWideAuditOutbox.DELIVERED);
        assertThat(count("platform.wide.audit.delivered")).isEqualTo(1);
    }

    @Test
    @DisplayName("the body has exactly seven keys, so nothing else can leave the service")
    void bodyHasNoOtherKeys() throws Exception {
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(202)));
        forwarder.forward(reach()).block();

        var body = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(identity.getAllServeEvents().get(0).getRequest().getBodyAsString());
        var keys = new java.util.TreeSet<String>();
        body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("actorSub", "occurredAt", "operation", "reason", "recordId", "role", "service");
    }

    @Test
    @DisplayName("identity being unavailable leaves the row PENDING, notes the attempt and fails so the caller retries")
    void identityDownKeepsTheRow() {
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(503)));
        String id = reach();

        assertThatThrownBy(() -> forwarder.forward(id).block()).isInstanceOf(WebClientResponseException.class);

        var row = outbox.find(id).block();
        assertThat(row.state()).isEqualTo(PlatformWideAuditOutbox.PENDING);
        assertThat(row.attempts()).isEqualTo(1);
        assertThat(count("platform.wide.audit.delivered")).isZero();
    }

    @Test
    @DisplayName("a slow identity is a failed attempt, not a hang: the row waits and the attempt is noted")
    void identityHangKeepsTheRow() {
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH))
                .willReturn(aResponse().withStatus(202).withFixedDelay(
                        (int) PlatformWideAuditForwarder.ATTEMPT_TIMEOUT.toMillis() + 2_000)));
        String id = reach();

        assertThatThrownBy(() -> forwarder.forward(id).block()).isInstanceOf(RuntimeException.class);

        assertThat(outbox.find(id).block().state()).isEqualTo(PlatformWideAuditOutbox.PENDING);
        assertThat(outbox.find(id).block().attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("a refusal as malformed or too old is final: the row is REFUSED, kept, logged as an error and counted")
    void refusalIsFinal() {
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(400)));
        String id = reach();

        assertThat(forwarder.forward(id).block()).isEqualTo(PlatformWideAuditForwarder.Outcome.REFUSED);

        assertThat(outbox.find(id).block().state()).isEqualTo(PlatformWideAuditOutbox.REFUSED);
        assertThat(count("platform.wide.audit.delivery.failed")).isEqualTo(1);
        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("REFUSED") && m.contains(id) && m.contains("finance-1"));
    }

    @Test
    @DisplayName("authorization failures and server errors are not final: a misconfigured token is retried until fixed")
    void authFailuresAreRetried() {
        String id = reach();
        for (int status : new int[]{401, 403, 404, 429, 500, 502}) {
            identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(status)));
            assertThatThrownBy(() -> forwarder.forward(id).block()).as("status " + status)
                    .isInstanceOf(WebClientResponseException.class);
        }
        assertThat(outbox.find(id).block().state()).isEqualTo(PlatformWideAuditOutbox.PENDING);
    }

    @Test
    @DisplayName("a row already delivered is not sent again, and an unknown row sends nothing")
    void settledRowsAreNotResent() {
        identity.stubFor(post(urlPathEqualTo(PlatformWideAuditForwarder.PATH)).willReturn(aResponse().withStatus(202)));
        String id = reach();
        forwarder.forward(id).block();

        assertThat(forwarder.forward(id).block()).isEqualTo(PlatformWideAuditForwarder.Outcome.NOTHING_TO_SEND);
        assertThat(forwarder.forward(UUID.randomUUID().toString()).block())
                .isEqualTo(PlatformWideAuditForwarder.Outcome.NOTHING_TO_SEND);

        identity.verify(1, postRequestedFor(urlPathEqualTo(PlatformWideAuditForwarder.PATH)));
    }
}
