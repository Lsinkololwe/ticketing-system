package com.pml.shared.security.tenancy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.config.MongoSchemaValidationConfig;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The local-first sink through the real access path: the reach is a stored row before anything
 * else happens, the administrator is never held up by delivery, and a failure at any step leaves
 * the reach standing with an error line and a counter. OWASP A04:2021 · A09:2021.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@Tag("OWASP")
@DisplayName("a platform-wide reach is stored locally first, delivery never delays the administrator, and failures never block")
class OutboxPlatformWideAuditSinkTest {

    private static final String COLLECTION = "test_sink_outbox";
    private static final String REFUSING = "test_sink_outbox_refusing";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger accessLog = (Logger) LoggerFactory.getLogger(LogOnlyPlatformWideAuditSink.LOGGER_NAME);
    private final Logger sinkLog = (Logger) LoggerFactory.getLogger(OutboxPlatformWideAuditSink.class);
    private final List<String> started = new CopyOnWriteArrayList<>();
    private SimpleMeterRegistry meters;
    private PlatformWideAuditOutbox outbox;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client,
                "sink_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)));
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties()) {
            @Override
            protected Map<String, String> getSchemaDefinitions() {
                return Map.of(COLLECTION, "platform-wide-audit-outbox-schema.json");
            }
        }.applySchemaValidation();
        // a collection whose validator refuses every document: the local write fails for real
        Mono.from(template.getMongoDatabase().block().createCollection(REFUSING,
                new com.mongodb.client.model.CreateCollectionOptions().validationOptions(
                        new com.mongodb.client.model.ValidationOptions().validator(
                                new Document("$jsonSchema", new Document("bsonType", "object")
                                        .append("required", List.of("neverPresent"))))))).block();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void wire() {
        template.remove(new Query(), COLLECTION).block();
        meters = new SimpleMeterRegistry();
        logged.start();
        accessLog.addAppender(logged);
        sinkLog.addAppender(logged);
    }

    @AfterEach
    void detach() {
        accessLog.detachAppender(logged);
        sinkLog.detachAppender(logged);
    }

    private PlatformWideAccess accessThrough(String collection, PlatformWideAuditDelivery delivery) {
        var clock = TestClock.frozenAt(NOW);
        outbox = new PlatformWideAuditOutbox(template, collection, clock);
        var metrics = new PlatformWideAuditMetrics("booking-service", outbox, meters, clock);
        return new PlatformWideAccess("booking-service", new OutboxPlatformWideAuditSink(outbox, delivery, metrics),
                clock, PlatformWideAccess.DEFAULT_AUTHORITIES, meters);
    }

    private static <T> Mono<T> asFinance(Mono<T> call, PlatformWideAccess access) {
        var auth = new UsernamePasswordAuthenticationToken("finance-1", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_FINANCE")));
        return call.contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private static Mono<Boolean> reachCall() {
        return PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ,
                "bookingsByBuyer", StaffRole.all());
    }

    private Mono<Boolean> staffReach(PlatformWideAccess access) {
        return asFinance(reachCall(), access);
    }

    @Test
    @DisplayName("a reach is a PENDING row with its facts and time, a delivery is started for it, and the log line is kept")
    void localFirst() {
        PlatformWideAccess access = accessThrough(COLLECTION, started::add);

        assertThat(staffReach(access).block()).isTrue();

        var rows = template.find(new Query(), Document.class, COLLECTION).collectList().block();
        assertThat(rows).hasSize(1);
        Document row = rows.get(0);
        assertThat(row.getString("actorSub")).isEqualTo("finance-1");
        assertThat(row.getString("role")).isEqualTo("ROLE_FINANCE");
        assertThat(row.getString("reason")).isEqualTo("BUYER_BOOKINGS_READ");
        assertThat(row.getString("operation")).isEqualTo("bookingsByBuyer");
        assertThat(row.getDate("occurredAt").toInstant()).isEqualTo(NOW);
        assertThat(row.getString("state")).isEqualTo(PlatformWideAuditOutbox.PENDING);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(started).containsExactly(row.getString("_id")));
        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("platform-wide access") && m.contains("finance-1"));
    }

    @Test
    @DisplayName("a reach is one row however many times the same request asks")
    void oneRowPerDistinctReach() {
        PlatformWideAccess access = accessThrough(COLLECTION, started::add);

        asFinance(reachCall().then(reachCall()).then(reachCall()), access).block();

        assertThat(template.count(new Query(), COLLECTION).block()).isEqualTo(1);
    }

    @Test
    @DisplayName("a delivery that is slow to start does not slow the reach")
    void slowStartDoesNotDelay() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        PlatformWideAccess access = accessThrough(COLLECTION, recordId -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        long begun = System.nanoTime();
        Boolean staff = staffReach(access).block(Duration.ofSeconds(5));
        long tookMillis = (System.nanoTime() - begun) / 1_000_000;
        release.countDown();

        assertThat(staff).isTrue();
        assertThat(tookMillis).as("the reach returned without waiting for the start").isLessThan(2_000);
        assertThat(template.count(new Query(), COLLECTION).block()).isEqualTo(1);
    }

    @Test
    @DisplayName("Temporal being unreachable leaves the row PENDING, counts the failed start and still lets the reach through")
    void startFailureKeepsTheRow() {
        PlatformWideAccess access = accessThrough(COLLECTION, recordId -> {
            throw new IllegalStateException("temporal unreachable");
        });

        assertThat(staffReach(access).block()).isTrue();

        assertThat(outbox.backlog().block().pending()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(meters.find("platform.wide.audit.delivery.start.failures").tag("service", "booking-service")
                        .counter()).isNotNull().extracting(c -> c.count()).isEqualTo(1.0));
        assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains("could not be started") && m.contains("re-driven"));
    }

    @Test
    @DisplayName("even when the local write itself fails the administrator proceeds, with an error line and a counter")
    void outboxWriteFailureDoesNotBlock() {
        PlatformWideAccess access = accessThrough(REFUSING, started::add);

        Boolean staff = staffReach(access).block(Duration.ofSeconds(10));

        assertThat(staff).as("the reach proceeded").isTrue();
        assertThat(started).as("nothing to deliver: no row exists").isEmpty();
        assertThat(meters.find("platform.wide.audit.failures").tag("service", "booking-service")
                .tag("reason", "BUYER_BOOKINGS_READ").counter().count()).isEqualTo(1.0);
        assertThat(logged.list).anyMatch(event -> event.getLevel() == Level.ERROR
                && event.getFormattedMessage().contains("NOT recorded") && event.getFormattedMessage().contains("finance-1"));
        assertThat(Set.copyOf(logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList()))
                .anyMatch(m -> m.contains("platform-wide access: actor=finance-1"));
    }
}
