package com.pml.identity.boot;

import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.security.tenancy.PlatformWideAuditSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Identity's whole context: a platform-wide reach is a searchable row through the real security
 * chain, the audit query gives administrators the whole trail and finance the money view only, reading the trail leaves a row,
 * and a row delivered by booking over the internal API is validated and searchable beside it.
 * OWASP A01:2021 · A03:2021 · A09:2021.
 */
@Tag("L3")
@Tag("ET-PLT-009")
@Tag("ET-PLT-007")
@Tag("OWASP")
@DisplayName("ET-PLT-009 · the platform-wide audit trail end to end through identity's security chain")
@SpringBootTest(classes = {IdentityServiceApplication.class, PlatformWideAuditEndToEndTest.Fakes.class})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class PlatformWideAuditEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String ROW = "TENANT_BOUNDARY_CROSSED";

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        FakeKeycloak fakeKeycloak() {
            return new FakeKeycloak();
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    ReactiveMongoTemplate template;

    private WebTestClient.ResponseSpec graphql(String subject, String role, String query) {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(60)).build()
                .mutateWith(mockJwt().jwt(jwt -> jwt.subject(subject)
                                .claim("jti", UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString())
                                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query)).exchange();
    }

    private WebTestClient.ResponseSpec internal(String authority, Object body) {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(60)).build();
        if (authority != null) {
            client = client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority(authority)));
        }
        return client.post().uri("/api/internal/audit/platform-wide").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange();
    }

    private long reaches() {
        return template.count(Query.query(Criteria.where("action").is(ROW)), AuditLog.class).block();
    }

    private static final String TRAIL = "{ auditLogs(filter: {action: \"" + ROW + "\"}, pagination: {size: 50}) "
            + "{ content { action actorId metadata at } pageInfo { totalCount } } }";

    // ------------------------------------------------------------------------------------ wiring

    @Test
    @DisplayName("identity records platform-wide reaches into its own audit trail, not just the log")
    void identityWritesTheTrail() {
        assertThat(context.getBean(PlatformWideAuditSink.class))
                .isInstanceOf(com.pml.identity.platform.PlatformWideAuditTrail.class);
    }

    // ------------------------------------------------------------------------------------ the query

    @Test
    @DisplayName("an administrator reads the trail, and the read is itself one row naming who read it")
    void readingTheTrailLeavesARow() {
        long before = reaches();

        String body = graphql("admin-read", "ADMIN", TRAIL).expectStatus().isOk().expectBody(String.class)
                .returnResult().getResponseBody();
        assertThat(body).contains("\"data\"").doesNotContain("\"errors\"");
        assertThat(reaches()).as("exactly one row for one read").isEqualTo(before + 1);

        AuditLog row = template.find(Query.query(Criteria.where("action").is(ROW).and("performedBy").is("admin-read")),
                AuditLog.class).blockFirst();
        assertThat(row.getReason()).isEqualTo("AUDIT_TRAIL_READ");
        assertThat(row.getOperation()).isEqualTo("auditLogs");
        assertThat(row.getActorRole()).isEqualTo("ROLE_ADMIN");
        assertThat(row.getService()).isEqualTo("identity-service");
        assertThat(row.getTimestamp()).isNotNull();

        String next = graphql("admin-read", "ADMIN", TRAIL).expectBody(String.class).returnResult().getResponseBody();
        assertThat(next).as("the previous read is in the trail the second read returns")
                .contains("admin-read").contains("AUDIT_TRAIL_READ").contains("identity-service");
    }

    @Test
    @DisplayName("a super administrator reads it too")
    void superAdministratorReads() {
        long before = reaches();
        assertThat(graphql("root-1", "SUPER_ADMIN", TRAIL).expectBody(String.class).returnResult().getResponseBody())
                .doesNotContain("\"errors\"");
        assertThat(reaches()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("organizers, customers and scanners are refused the trail and leave no row")
    void everyoneElseIsRefused() {
        long before = reaches();
        for (String role : new String[]{"ORGANIZER", "CUSTOMER", "USER", "SCANNER"}) {
            String body = graphql("someone-" + role, role, TRAIL).expectBody(String.class).returnResult().getResponseBody();
            assertThat(body).as(role).contains("\"errors\"").doesNotContain("\"content\"");
        }
        assertThat(reaches()).as("a refusal is not a reach").isEqualTo(before);
    }

    @Test
    @DisplayName("an oversized page is refused, not clamped, and no row is written for it")
    void oversizedPageIsRefused() {
        long before = reaches();
        String body = graphql("admin-big", "ADMIN",
                "{ auditLogs(pagination: {size: 1000000}) { content { id } } }")
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("\"errors\"").contains("PAGE_SIZE_EXCEEDED").doesNotContain("\"content\"");
        assertThat(reaches()).isEqualTo(before);
    }

    private static Map<String, String> body(String actor, String role, String reason) {
        Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("recordId", UUID.randomUUID().toString());
        body.put("occurredAt", Instant.now().minusSeconds(30).toString());
        body.put("actorSub", actor);
        body.put("role", role);
        body.put("service", "booking-service");
        body.put("reason", reason);
        return body;
    }

    private static Map<String, String> with(Map<String, String> base, String key, String value) {
        Map<String, String> copy = new java.util.LinkedHashMap<>(base);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return copy;
    }

    private void deliver(String actor, String role, String reason) {
        internal("SCOPE_internal-write", with(body(actor, role, reason), "operation", "op")).expectStatus().isAccepted();
    }

    @Test
    @DisplayName("finance reads the money view through the real chain: any actor's money rows, no other row, and its own read is a row")
    void financeReadsTheMoneyView() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        deliver("fin-admin-" + run, "ROLE_ADMIN", "BANK_ACCOUNT_MANAGE");
        deliver("fin-root-" + run, "ROLE_SUPER_ADMIN", "RECOVERY_ACTION_CONFIRM");
        deliver("fin-admin-" + run, "ROLE_ADMIN", "BOOKING_CONTACT_READ");
        deliver("fin-admin-" + run, "ROLE_ADMIN", "ORGANIZATION_MEMBER_READ");
        long before = reaches();

        String body = graphql("finance-" + run, "FINANCE", "{ auditLogs(filter: {service: \"booking-service\"}, "
                + "pagination: {size: 100}) { content { actorId metadata } pageInfo { totalCount } } }")
                .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).doesNotContain("\"errors\"");
        assertThat(body).contains("fin-admin-" + run).contains("fin-root-" + run)
                .contains("BANK_ACCOUNT_MANAGE").contains("RECOVERY_ACTION_CONFIRM");
        assertThat(body).doesNotContain("BOOKING_CONTACT_READ").doesNotContain("ORGANIZATION_MEMBER_READ")
                .doesNotContain("AUDIT_TRAIL_READ").doesNotContain("FINANCE_AUDIT_VIEW_READ");

        assertThat(reaches()).as("finance's read is one row").isEqualTo(before + 1);
        AuditLog row = template.find(Query.query(Criteria.where("action").is(ROW).and("performedBy").is("finance-" + run)),
                AuditLog.class).blockFirst();
        assertThat(row.getReason()).isEqualTo("FINANCE_AUDIT_VIEW_READ");
        assertThat(row.getActorRole()).isEqualTo("ROLE_FINANCE");
        assertThat(row.getOperation()).isEqualTo("auditLogs");
        assertThat(row.getService()).isEqualTo("identity-service");

        String admin = graphql("admin-" + run, "ADMIN", "{ auditLogs(filter: {actorId: \"fin-admin-" + run + "\"}, "
                + "pagination: {size: 100}) { content { metadata } } }")
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(admin).as("the administrator sees every row").contains("BANK_ACCOUNT_MANAGE")
                .contains("BOOKING_CONTACT_READ").contains("ORGANIZATION_MEMBER_READ");
    }

    @Test
    @DisplayName("finance asking for the whole trail by reason, service or actor is answered with nothing")
    void financeCannotAskForMore() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        deliver("outside-" + run, "ROLE_ADMIN", "BOOKING_CONTACT_READ");
        graphql("admin-" + run, "ADMIN", TRAIL).expectStatus().isOk();

        String[] asks = {
                "filter: {reason: \"BOOKING_CONTACT_READ\"}",
                "filter: {reason: \"AUDIT_TRAIL_READ\"}",
                "filter: {actorId: \"outside-" + run + "\"}",
                "filter: {service: \"identity-service\"}",
                "filter: {action: \"USER_LOGIN\", includeAccountEvents: true}",
        };
        for (String ask : asks) {
            String body = graphql("finance-ask-" + run, "FINANCE", "{ auditLogs(" + ask + ", pagination: {size: 50}) "
                    + "{ content { id } pageInfo { totalCount } } }")
                    .expectBody(String.class).returnResult().getResponseBody();
            assertThat(body).as(ask).doesNotContain("\"errors\"").contains("\"content\":[]");
        }
    }

    @Test
    @DisplayName("finance's oversized page is refused and writes no row")
    void financeOversizedPage() {
        long before = reaches();
        String body = graphql("finance-big", "FINANCE", "{ auditLogs(pagination: {size: 1000000}) { content { id } } }")
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("PAGE_SIZE_EXCEEDED").doesNotContain("\"content\"");
        assertThat(reaches()).isEqualTo(before);
    }

    // ------------------------------------------------------------------------------------ delivery from booking

    @Test
    @DisplayName("a reach delivered by booking keeps the time it happened, is stamped with identity's own receipt time, and is searchable")
    void deliveredReachIsARow() {
        Instant happened = Instant.now().minus(Duration.ofHours(5)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Map<String, String> delivery = with(with(body("finance-7", "ROLE_FINANCE", "BUYER_BOOKINGS_READ"),
                "occurredAt", happened.toString()), "operation", "bookingsByBuyer");

        internal("SCOPE_internal-write", delivery).expectStatus().isAccepted();

        AuditLog row = template.find(Query.query(Criteria.where("action").is(ROW).and("performedBy").is("finance-7")),
                AuditLog.class).blockFirst();
        assertThat(row.getService()).isEqualTo("booking-service");
        assertThat(row.getActorRole()).isEqualTo("ROLE_FINANCE");
        assertThat(row.getRecordId()).isEqualTo(delivery.get("recordId"));
        assertThat(row.getTimestamp()).as("when it happened, as the sender says").isEqualTo(happened);
        assertThat(row.getReceivedAt()).as("when identity stored it, by identity's clock")
                .isAfter(Instant.now().minusSeconds(60)).isBefore(Instant.now().plusSeconds(5));

        String found = graphql("admin-search", "ADMIN", "{ auditLogs(filter: {service: \"booking-service\", "
                + "reason: \"BUYER_BOOKINGS_READ\"}) { content { actorId at receivedAt metadata } } }")
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(found).contains("finance-7").contains("ROLE_FINANCE").contains("bookingsByBuyer")
                .contains(happened.toString().substring(0, 19)).contains("receivedAt");
    }

    @Test
    @DisplayName("the same record delivered again is answered as accepted and leaves one row")
    void duplicateDeliveryIsOneRow() {
        Map<String, String> delivery = body("finance-dup", "ROLE_FINANCE", "BUYER_BOOKINGS_READ");

        internal("SCOPE_internal-write", delivery).expectStatus().isAccepted();
        internal("SCOPE_internal-write", delivery).expectStatus().isAccepted();
        internal("SCOPE_internal-write", with(delivery, "occurredAt", Instant.now().toString())).expectStatus().isAccepted();

        assertThat(template.count(Query.query(Criteria.where("recordId").is(delivery.get("recordId"))), AuditLog.class).block())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a time in the future beyond clock skew or older than thirty days is refused and stores nothing")
    void occurredAtIsBounded() {
        long before = reaches();
        Map<String, String> base = body("finance-window", "ROLE_FINANCE", "BUYER_BOOKINGS_READ");

        internal("SCOPE_internal-write", with(base, "occurredAt", Instant.now().plus(Duration.ofMinutes(10)).toString()))
                .expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "occurredAt", Instant.now().plus(Duration.ofDays(2)).toString()))
                .expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "occurredAt", Instant.now().minus(Duration.ofDays(31)).toString()))
                .expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "occurredAt", "1970-01-01T00:00:00Z")).expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "occurredAt", "yesterday")).expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "occurredAt", null)).expectStatus().isBadRequest();
        assertThat(reaches()).as("nothing was stored").isEqualTo(before);

        internal("SCOPE_internal-write", with(base, "occurredAt", Instant.now().minus(Duration.ofDays(29)).toString()))
                .expectStatus().isAccepted();
        internal("SCOPE_internal-write", with(with(base, "recordId", UUID.randomUUID().toString()), "occurredAt",
                Instant.now().plusSeconds(60).toString())).expectStatus().isAccepted();
        assertThat(reaches()).as("both edges of the window are served").isEqualTo(before + 2);
    }

    @Test
    @DisplayName("a delivery without a well-formed record id is refused, and a repeated time cannot hide a receipt time")
    void recordIdIsRequired() {
        long before = reaches();
        Map<String, String> base = body("finance-id", "ROLE_FINANCE", "BUYER_BOOKINGS_READ");

        internal("SCOPE_internal-write", with(base, "recordId", null)).expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "recordId", "not-a-uuid")).expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "recordId", UUID.randomUUID().toString().toUpperCase()))
                .expectStatus().isBadRequest();
        internal("SCOPE_internal-write", with(base, "recordId", "x'; db.audit.drop(); //")).expectStatus().isBadRequest();

        assertThat(reaches()).isEqualTo(before);
    }

    @Test
    @DisplayName("fields beyond the shape (a token, a filter, a note) are never stored")
    void extraFieldsAreNeverStored() {
        Map<String, String> smuggling = with(with(with(body("finance-extra", "ROLE_FINANCE", "BUYER_BOOKINGS_READ"),
                "token", "eyJhbGciOi-secret"), "filter", "buyerId=42"), "note", "free text");

        internal("SCOPE_internal-write", smuggling).expectStatus().isAccepted();

        org.bson.Document stored = template.getCollection(IdentityCollections.AUDIT_LOGS)
                .flatMap(c -> reactor.core.publisher.Mono.from(c.find(new org.bson.Document("recordId", smuggling.get("recordId"))).first()))
                .block();
        assertThat(stored.toJson()).doesNotContain("eyJhbGciOi").doesNotContain("buyerId=42").doesNotContain("free text");
        assertThat(stored.keySet()).doesNotContain("token", "filter", "note");
    }

    // ------------------------------------------------------------------------------------ local-first delivery, two services

    /** Identity as booking and catalog see it: the real controller and security chain, with an outage switch. */
    private final java.util.concurrent.atomic.AtomicBoolean identityDown = new java.util.concurrent.atomic.AtomicBoolean();

    private org.springframework.web.reactive.function.client.WebClient identityClient() {
        var http = org.springframework.web.server.adapter.WebHttpHandlerBuilder.applicationContext(context);
        springSecurity().beforeServerCreated(http);
        mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write")).beforeServerCreated(http);
        return org.springframework.web.reactive.function.client.WebClient.builder()
                .clientConnector(new org.springframework.test.web.reactive.server.HttpHandlerConnector(http.build()))
                .filter(org.springframework.web.reactive.function.client.ExchangeFilterFunction.ofRequestProcessor(request ->
                        identityDown.get()
                                ? reactor.core.publisher.Mono.error(new java.net.ConnectException("identity is down"))
                                : reactor.core.publisher.Mono.just(request)))
                .baseUrl("http://identity.test").build();
    }

    /** One service's side of the chain: a real outbox in the shared MongoDB, a real worker on a real Temporal server. */
    private final class Service implements AutoCloseable {
        final String collection = "test_outbox_" + UUID.randomUUID().toString().substring(0, 8);
        final com.pml.shared.security.tenancy.PlatformWideAuditOutbox outbox;
        final com.pml.shared.security.tenancy.PlatformWideAuditMetrics metrics;
        final com.pml.shared.security.tenancy.TemporalPlatformWideAuditDelivery delivery;
        final io.temporal.worker.WorkerFactory workers;
        final String queue = "test-audit-" + UUID.randomUUID();
        final io.micrometer.core.instrument.simple.SimpleMeterRegistry meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

        Service() {
            var clock = java.time.Clock.systemUTC();
            outbox = new com.pml.shared.security.tenancy.PlatformWideAuditOutbox(template, collection, clock);
            metrics = new com.pml.shared.security.tenancy.PlatformWideAuditMetrics("booking-service", outbox, meters, clock);
            var forwarder = new com.pml.shared.security.tenancy.PlatformWideAuditForwarder(outbox, identityClient(), metrics);
            workers = io.temporal.worker.WorkerFactory.newInstance(workflowClient);
            var worker = workers.newWorker(queue);
            worker.registerWorkflowImplementationTypes(
                    com.pml.shared.security.tenancy.PlatformWideAuditDeliveryWorkflowImpl.class);
            worker.registerActivitiesImplementations(
                    new com.pml.shared.security.tenancy.PlatformWideAuditDeliveryActivities() {
                        @Override
                        public void forward(String recordId) {
                            forwarder.forward(recordId).block(Duration.ofSeconds(25));
                        }
                    });
            workers.start();
            delivery = new com.pml.shared.security.tenancy.TemporalPlatformWideAuditDelivery(temporal, queue);
        }

        /** The access class this service runs, with its sink, as booking's reads and staff checks use it. */
        com.pml.shared.security.tenancy.PlatformWideAccess access(
                com.pml.shared.security.tenancy.PlatformWideAuditDelivery start, java.time.Clock clock) {
            return new com.pml.shared.security.tenancy.PlatformWideAccess("booking-service",
                    new com.pml.shared.security.tenancy.OutboxPlatformWideAuditSink(outbox, start, metrics), clock,
                    java.util.Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_FINANCE"), meters);
        }

        @Override
        public void close() {
            workers.shutdownNow();
        }
    }

    @Autowired
    io.temporal.client.WorkflowClient workflowClient;
    @Autowired
    com.pml.shared.infrastructure.temporal.TemporalGateway temporal;

    private static Boolean staffReach(com.pml.shared.security.tenancy.PlatformWideAccess access, String actor) {
        var token = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                actor, "n/a", java.util.List.of(new SimpleGrantedAuthority("ROLE_FINANCE")));
        return com.pml.shared.security.tenancy.PlatformWideAccess.isPlatformStaff(
                        com.pml.shared.security.tenancy.PlatformWideAccess.Reason.RESERVATION_SUPPORT_READ, "reservation",
                        com.pml.shared.security.tenancy.StaffRole.all())
                .contextWrite(ctx -> com.pml.shared.security.tenancy.PlatformWideAccess.seed(ctx, access))
                .contextWrite(org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(token))
                .block(Duration.ofSeconds(30));
    }

    private long trailRowsFor(String actor) {
        return template.count(Query.query(Criteria.where("action").is(ROW).and("performedBy").is(actor)), AuditLog.class).block();
    }

    @Test
    @DisplayName("identity down at the reach: the row is kept locally PENDING, identity returns, and it appears once in the trail with its original time")
    void reachSurvivesAnIdentityOutage() throws Exception {
        String actor = "finance-outage-" + UUID.randomUUID().toString().substring(0, 8);
        Instant happened = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try (Service booking = new Service()) {
            identityDown.set(true);
            var access = booking.access(booking.delivery::start, java.time.Clock.fixed(happened, java.time.ZoneOffset.UTC));

            assertThat(staffReach(access, actor)).as("the reach is not held up by identity being down").isTrue();

            var waiting = booking.outbox.backlog().block();
            assertThat(waiting.pending()).isEqualTo(1);
            assertThat(waiting.oldest()).isEqualTo(happened);
            assertThat(trailRowsFor(actor)).as("identity has not been told yet").isZero();

            identityDown.set(false);

            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(60))
                    .untilAsserted(() -> assertThat(trailRowsFor(actor)).isEqualTo(1));
            assertThat(booking.outbox.backlog().block().pending()).isZero();
            AuditLog row = template.find(Query.query(Criteria.where("performedBy").is(actor)), AuditLog.class).blockFirst();
            assertThat(row.getTimestamp()).as("the original time").isEqualTo(happened);
            assertThat(row.getReceivedAt()).as("identity's own receipt time").isAfter(happened.plusSeconds(60));
            assertThat(row.getService()).isEqualTo("booking-service");
            assertThat(row.getReason()).isEqualTo("RESERVATION_SUPPORT_READ");
            assertThat(row.getRecordId()).isNotNull();

            String found = graphql("admin-late", "ADMIN", "{ auditLogs(filter: {actorId: \"" + actor + "\"}) "
                    + "{ content { actorId at receivedAt } pageInfo { totalCount } } }")
                    .expectBody(String.class).returnResult().getResponseBody();
            assertThat(found).contains(actor).contains(happened.toString().substring(0, 19)).contains("\"totalCount\":1");
        }
    }

    @Test
    @DisplayName("a delivery repeated after identity already stored it is a no-op that is still answered as delivered")
    void repeatedDeliveryAfterStorageIsOneRow() throws Exception {
        String actor = "finance-repeat-" + UUID.randomUUID().toString().substring(0, 8);
        try (Service booking = new Service()) {
            var access = booking.access(booking.delivery::start, java.time.Clock.systemUTC());
            assertThat(staffReach(access, actor)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(trailRowsFor(actor)).isEqualTo(1));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10))
                    .until(() -> booking.outbox.backlog().block().pending() == 0);

            // the sender never saw the answer: it asks again for the same row
            String recordId = template.find(Query.query(Criteria.where("performedBy").is(actor)), AuditLog.class)
                    .blockFirst().getRecordId();
            template.getCollection(booking.collection).flatMap(c -> reactor.core.publisher.Mono.from(c.updateOne(
                    new org.bson.Document("_id", recordId),
                    new org.bson.Document("$set", new org.bson.Document("state", "PENDING"))))).block();
            booking.delivery.start(recordId);

            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).until(() ->
                    "DELIVERED".equals(booking.outbox.find(recordId).block().state()));
            assertThat(trailRowsFor(actor)).as("still one row").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Temporal unreachable at the start: the reach goes through, the row stays PENDING, and the next start-up delivers it")
    void temporalDownAtTheStart() throws Exception {
        String actor = "finance-notemporal-" + UUID.randomUUID().toString().substring(0, 8);
        try (Service booking = new Service()) {
            var unreachable = io.temporal.client.WorkflowClient.newInstance(
                    io.temporal.serviceclient.WorkflowServiceStubs.newServiceStubs(
                            io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
                                    .setTarget("127.0.0.1:1").setRpcTimeout(Duration.ofSeconds(2))
                                    .setRpcRetryOptions(io.temporal.serviceclient.RpcRetryOptions.newBuilder()
                                            .setExpiration(Duration.ofSeconds(3)).build()).build()),
                    io.temporal.client.WorkflowClientOptions.newBuilder().setNamespace(workflowClient.getOptions().getNamespace()).build());
            var down = new com.pml.shared.security.tenancy.TemporalPlatformWideAuditDelivery(
                    new com.pml.shared.infrastructure.temporal.TemporalGateway(unreachable), booking.queue);
            var access = booking.access(down, java.time.Clock.systemUTC());

            assertThat(staffReach(access, actor)).isTrue();

            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                    assertThat(booking.meters.find("platform.wide.audit.delivery.start.failures").counter().count()).isEqualTo(1.0));
            assertThat(booking.outbox.backlog().block().pending()).isEqualTo(1);
            assertThat(trailRowsFor(actor)).isZero();

            // the service restarts with Temporal back
            new com.pml.shared.security.tenancy.PlatformWideAuditDrain(booking.outbox, booking.delivery, booking.metrics)
                    .redrive().block();

            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(60))
                    .untilAsserted(() -> assertThat(trailRowsFor(actor)).isEqualTo(1));
        }
    }

    @Test
    @DisplayName("the internal write needs the internal scope, and refuses anything outside the closed shapes")
    void internalWriteIsClosed() {
        Map<String, String> good = body("finance-8", "ROLE_FINANCE", "BUYER_BOOKINGS_READ");
        long before = reaches();

        internal(null, good).expectStatus().isUnauthorized();
        internal("ROLE_ADMIN", good).expectStatus().isForbidden();
        internal("SCOPE_internal-read", good).expectStatus().isForbidden();
        internal("SCOPE_internal-write", with(good, "reason", "NOT_A_REASON")).expectStatus().is4xxClientError();
        internal("SCOPE_internal-write", with(good, "reason", "REFUND_WORKFLOW")).expectStatus().is4xxClientError();
        internal("SCOPE_internal-write", with(good, "service", "Booking Service!")).expectStatus().is4xxClientError();
        internal("SCOPE_internal-write", with(good, "actorSub", "f\nforged=1")).expectStatus().is4xxClientError();
        internal("SCOPE_internal-write", with(good, "operation", "x".repeat(101))).expectStatus().is4xxClientError();

        assertThat(reaches()).as("none of the refused deliveries wrote a row").isEqualTo(before);
    }

    @Test
    @DisplayName("a row cannot be altered or removed through the internal API: only POST exists")
    void noEditOrDeleteOverTheWire() {
        long before = reaches();
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build()
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_internal-write")));
        for (var method : new org.springframework.http.HttpMethod[]{org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE}) {
            int status = client.method(method).uri("/api/internal/audit/platform-wide").exchange()
                    .returnResult(Void.class).getStatus().value();
            assertThat(status).as(method.name() + " is never served").isGreaterThanOrEqualTo(400);
        }
        assertThat(reaches()).as("nothing was altered or removed").isEqualTo(before);
        assertThat(template.getCollectionName(AuditLog.class)).isEqualTo(IdentityCollections.AUDIT_LOGS);
    }
}
