package com.pml.identity.platform;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.config.MongoSchemaValidationConfig;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.service.AdminReadService;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.SortDirection;
import com.pml.identity.web.graphql.dto.platform.AuditLogEntry;
import com.pml.identity.web.graphql.dto.platform.AuditLogFilterInput;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.graphql.PageSize;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.PlatformWideAuditRecord;
import com.pml.shared.security.tenancy.StaffRole;
import com.pml.shared.testing.MongoReplicaSet;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.SchemaParser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A platform-wide reach becomes a searchable, validated, insert-only row in the audit trail, against
 * a real MongoDB with the production validators applied. OWASP A01:2021 · A09:2021.
 */
@Tag("L2")
@Tag("ET-PLT-009")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-009 · every platform-wide reach is a queryable audit row that cannot be altered through any interface")
class PlatformWideAuditTrailTest {

    private static final Instant NOW = Instant.parse("2026-10-13T08:30:00Z");
    /** Identity's own clock at receipt: later than the time the reach happened, as a late delivery would be. */
    private static final Instant RECEIVED = NOW.plusSeconds(90);
    private static final Set<String> AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static AdminReadService reads;
    private static final SimpleMeterRegistry METERS = new SimpleMeterRegistry();
    private static PlatformWideAccess access;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_platform_wide_audit"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();
        new com.pml.shared.persistence.IndexEnsurer(template).ensure(
                com.pml.identity.config.IdentityIndexInitializer.startupSpecifications().stream()
                        .filter(spec -> spec.collection().equals(IdentityCollections.AUDIT_LOGS)).toList()).block();
        reads = new AdminReadService(template);
        access = new PlatformWideAccess("booking-service", new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC), AUTHORITIES, METERS);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void empty() {
        template.remove(new Query(), IdentityCollections.AUDIT_LOGS).block();
    }

    private static <T> Mono<T> as(String subject, String role, Mono<T> call) {
        return call.contextWrite(ctx -> PlatformWideAccess.seed(ctx, access))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(
                        subject, "n/a", List.of(new SimpleGrantedAuthority(role)))));
    }

    private static AuditLogFilterInput filter(String actor, String service, String reason, Instant from, Instant to) {
        return new AuditLogFilterInput(from, to, "TENANT_BOUNDARY_CROSSED", actor, null, null, null, null, service, reason);
    }

    @Test
    @DisplayName("a finance officer's use leaves one row with actor, role, service, reason, operation and time")
    void aReachIsARow() {
        as("finance-1", "ROLE_FINANCE", PlatformWideAccess.isPlatformStaff(
                PlatformWideAccess.Reason.RESERVATION_SUPPORT_READ, "reservation", StaffRole.all())).block();

        List<AuditLogEntry> rows = reads.auditLogs(filter(null, null, null, null, null), null).block().content();
        assertThat(rows).hasSize(1);
        AuditLogEntry row = rows.get(0);
        assertThat(row.source()).isEqualTo("AUDIT");
        assertThat(row.action()).isEqualTo("TENANT_BOUNDARY_CROSSED");
        assertThat(row.actorId()).isEqualTo("finance-1");
        assertThat(row.status()).isEqualTo("SUCCESS");
        assertThat(row.at()).isEqualTo(NOW);
        assertThat(row.metadata()).containsEntry("actorRole", "ROLE_FINANCE")
                .containsEntry("service", "booking-service")
                .containsEntry("reason", "RESERVATION_SUPPORT_READ")
                .containsEntry("operation", "reservation");

        AuditLog stored = template.findAll(AuditLog.class).blockFirst();
        assertThat(stored.getActorRole()).isEqualTo("ROLE_FINANCE");
        assertThat(stored.getUserId()).as("no subject: nobody's account is the object of a reach").isNull();
        assertThat(stored.getErrorMessage()).isNull();
        assertThat(stored.getClientIp()).as("no network detail is copied from the request").isNull();
    }

    @Test
    @DisplayName("an administrator's strict reach is a row too, and a refused organizer leaves none")
    void strictReachAndRefusal() {
        as("admin-1", "ROLE_ADMIN", PlatformWideAccess.platformWide(PlatformWideAccess.Reason.AUDIT_TRAIL_READ, "auditLogs")).block();
        assertThatThrownBy(() -> as("organizer-1", "ROLE_ORGANIZER",
                PlatformWideAccess.platformWide(PlatformWideAccess.Reason.AUDIT_TRAIL_READ, "auditLogs")).block())
                .isInstanceOf(DomainRefusal.class);
        assertThat(as("customer-1", "ROLE_CUSTOMER", PlatformWideAccess.isPlatformStaff(
                PlatformWideAccess.Reason.BUYER_BOOKINGS_READ)).block()).isFalse();

        assertThat(reads.auditLogs(filter(null, null, null, null, null), null).block().content())
                .extracting(AuditLogEntry::actorId).containsExactly("admin-1");
    }

    @Test
    @DisplayName("rows are found by date range, actor, service and reason, newest first")
    void searchable() {
        as("finance-1", "ROLE_FINANCE", PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ)).block();
        as("admin-1", "ROLE_ADMIN", PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.PLATFORM_SALES_ANALYTICS_READ)).block();
        // a later row from another service, written through the same sink with its own clock
        PlatformWideAccess catalog = new PlatformWideAccess("catalog-service", new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC)),
                Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC), AUTHORITIES);
        PlatformWideAccess.platformWide(PlatformWideAccess.Reason.EVENT_ACCESS_GRANTS_READ, "eventAccessGrants")
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, catalog))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(
                        "root-1", "n/a", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))))
                .block();

        assertThat(reads.auditLogs(filter(null, null, null, null, null), null).block().content())
                .extracting(AuditLogEntry::actorId).hasSize(3)
                .first().as("newest first").isEqualTo("root-1");
        assertThat(reads.auditLogs(filter("finance-1", null, null, null, null), null).block().content()).hasSize(1);
        assertThat(reads.auditLogs(filter(null, "catalog-service", null, null, null), null).block().content())
                .extracting(AuditLogEntry::actorId).containsExactly("root-1");
        assertThat(reads.auditLogs(filter(null, null, "BUYER_BOOKINGS_READ", null, null), null).block().content())
                .extracting(AuditLogEntry::actorId).containsExactly("finance-1");
        assertThat(reads.auditLogs(filter(null, null, null, NOW.plusSeconds(1800), NOW.plusSeconds(7200)), null).block().content())
                .extracting(AuditLogEntry::actorId).containsExactly("root-1");
        assertThat(reads.auditLogs(filter(null, "booking-service", "EVENT_ACCESS_GRANTS_READ", null, null), null).block().content())
                .as("filters are conjunctive").isEmpty();
    }

    @Test
    @DisplayName("the production validator accepts the row and refuses an unknown action or an oversized field")
    void validatorAcceptsAndRefuses() {
        as("finance-1", "ROLE_FINANCE", PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.BUYER_BOOKINGS_READ)).block();
        assertThat(template.count(new Query(), IdentityCollections.AUDIT_LOGS).block()).isEqualTo(1);

        assertThat(rawInsertFailure(new Document("action", "PLATFORM_WIDE_ACCESS").append("status", "SUCCESS")
                .append("timestamp", new java.util.Date()))).as("an action outside the registry").isNotNull();
        assertThat(rawInsertFailure(new Document("action", "TENANT_BOUNDARY_CROSSED").append("status", "SUCCESS")
                .append("timestamp", new java.util.Date()).append("reason", "R".repeat(101))))
                .as("a reason is a short fixed name, not free text").isNotNull();
        assertThat(rawInsertFailure(new Document("action", "TENANT_BOUNDARY_CROSSED").append("status", "SUCCESS")
                .append("timestamp", new java.util.Date()).append("service", 7)))
                .as("a field of the wrong type").isNotNull();
    }

    @Test
    @DisplayName("a store that refuses the row does not block the reach, and the loss is counted and logged")
    void storeRefusalIsCounted() {
        // an authority no real realm role would ever be, long enough to break the stored shape
        String longRole = "ROLE_" + "X".repeat(90);
        PlatformWideAccess odd = new PlatformWideAccess("booking-service", new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC), Set.of(longRole), METERS);

        Boolean wide = PlatformWideAccess.isPlatformWide(PlatformWideAccess.Reason.OWNERSHIP_TRANSFER_READ)
                .contextWrite(ctx -> PlatformWideAccess.seed(ctx, odd))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new UsernamePasswordAuthenticationToken(
                        "admin-9", "n/a", List.of(new SimpleGrantedAuthority(longRole)))))
                .block();

        assertThat(wide).isTrue();
        assertThat(template.count(new Query(), IdentityCollections.AUDIT_LOGS).block()).isZero();
        assertThat(METERS.get("platform.wide.audit.failures").tag("reason", "OWNERSHIP_TRANSFER_READ").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("one page of the trail is bounded: an oversized page is refused, never clamped")
    void oversizedPageIsRefused() {
        assertThatThrownBy(() -> new OffsetPaginationInput(0, 1_000_000, "timestamp", SortDirection.DESC))
                .isInstanceOf(PageSize.PageSizeExceeded.class);
        assertThatThrownBy(() -> new OffsetPaginationInput(0, PageSize.MAX + 1, "timestamp", SortDirection.DESC))
                .as("one over the ceiling").isInstanceOf(PageSize.PageSizeExceeded.class);
        assertThat(new OffsetPaginationInput(0, PageSize.MAX, "timestamp", SortDirection.DESC).getLimit())
                .as("the ceiling itself is served").isEqualTo(PageSize.MAX);
    }

    // ---- delivered late, delivered twice ------------------------------------------------------------

    private static PlatformWideAuditRecord delivered(Instant happened) {
        return new PlatformWideAuditRecord("finance-5", "ROLE_FINANCE", "booking-service",
                PlatformWideAccess.Reason.BUYER_BOOKINGS_READ, "bookingsByBuyer", happened);
    }

    @Test
    @DisplayName("a reach identity makes itself carries a record id and the time identity stored it, beside when it happened")
    void ownReachCarriesRecordIdAndReceiptTime() {
        as("admin-1", "ROLE_ADMIN", PlatformWideAccess.platformWide(PlatformWideAccess.Reason.AUDIT_TRAIL_READ, "auditLogs")).block();

        AuditLog stored = template.findAll(AuditLog.class).blockFirst();
        assertThat(stored.getRecordId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(stored.getTimestamp()).isEqualTo(NOW);
        assertThat(stored.getReceivedAt()).isEqualTo(RECEIVED);
        AuditLogEntry entry = reads.auditLogs(filter(null, null, null, null, null), null).block().content().get(0);
        assertThat(entry.at()).isEqualTo(NOW);
        assertThat(entry.receivedAt()).isEqualTo(RECEIVED);
    }

    @Test
    @DisplayName("a delivered reach keeps the time it happened, stores its own receipt time, and both are read back")
    void deliveredReachKeepsBothTimes() {
        PlatformWideAuditTrail trail = new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC));
        String recordId = java.util.UUID.randomUUID().toString();

        Boolean stored = trail.receive(recordId, delivered(NOW.minusSeconds(3600))).block();

        assertThat(stored).isTrue();
        AuditLogEntry entry = reads.auditLogs(filter("finance-5", null, null, null, null), null).block().content().get(0);
        assertThat(entry.at()).as("when it happened").isEqualTo(NOW.minusSeconds(3600));
        assertThat(entry.receivedAt()).as("when identity stored it").isEqualTo(RECEIVED);
        assertThat(entry.metadata()).containsEntry("service", "booking-service").containsEntry("reason", "BUYER_BOOKINGS_READ");
    }

    @Test
    @DisplayName("the same record delivered twice is one row, and both deliveries are answered as stored")
    void duplicateDeliveryIsOneRow() {
        PlatformWideAuditTrail trail = new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC));
        String recordId = java.util.UUID.randomUUID().toString();

        Boolean first = trail.receive(recordId, delivered(NOW)).block();
        Boolean second = trail.receive(recordId, delivered(NOW.plusSeconds(5))).block();

        assertThat(first).isTrue();
        assertThat(second).as("a repeat is still a success for the sender").isFalse();
        List<AuditLog> rows = template.find(new Query(), AuditLog.class).collectList().block();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTimestamp()).as("the first delivery's facts stand").isEqualTo(NOW);
    }

    @Test
    @DisplayName("eight deliveries of one record racing each other leave one row")
    void racingDuplicatesLeaveOneRow() {
        PlatformWideAuditTrail trail = new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC));
        String recordId = java.util.UUID.randomUUID().toString();

        List<Boolean> answers = reactor.core.publisher.Flux.range(0, 8)
                .flatMap(i -> trail.receive(recordId, delivered(NOW)).subscribeOn(reactor.core.scheduler.Schedulers.parallel()), 8)
                .collectList().block();

        assertThat(answers).hasSize(8).containsOnlyOnce(true);
        assertThat(template.count(new Query(), IdentityCollections.AUDIT_LOGS).block()).isEqualTo(1);
    }

    @Test
    @DisplayName("distinct records with the same facts are distinct rows: two reaches are two events")
    void sameFactsDifferentRecordsAreTwoRows() {
        PlatformWideAuditTrail trail = new PlatformWideAuditTrail(template, Clock.fixed(RECEIVED, ZoneOffset.UTC));

        trail.receive(java.util.UUID.randomUUID().toString(), delivered(NOW)).block();
        trail.receive(java.util.UUID.randomUUID().toString(), delivered(NOW)).block();

        assertThat(template.count(new Query(), IdentityCollections.AUDIT_LOGS).block()).isEqualTo(2);
    }

    @Test
    @DisplayName("the database refuses a record id that is not a UUID")
    void validatorRefusesAMalformedRecordId() {
        assertThat(rawInsertFailure(new Document("action", "TENANT_BOUNDARY_CROSSED").append("status", "SUCCESS")
                .append("timestamp", new java.util.Date()).append("recordId", "not-a-uuid"))).isNotNull();
    }

    // ---- append only ----------------------------------------------------------------------------------

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern WRITES_BESIDES_INSERT = Pattern.compile(
            "\\b(updateFirst|updateMulti|upsert|findAndModify|findAndReplace|findAndRemove|replaceOne|deleteOne|deleteMany"
                    + "|remove|save|saveAll|delete|deleteAll|deleteById|dropCollection)\\s*\\(");

    @Test
    @DisplayName("no code path updates or removes an audit row: only inserts reach the collection")
    void noCodeChangesOrRemovesARow() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("AuditLog.java") || name.equals("IdentityIndexInitializer.java")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    boolean namesTheTrail = line.contains("AuditLog.class") || line.contains("IdentityCollections.AUDIT_LOGS");
                    if (namesTheTrail && WRITES_BESIDES_INSERT.matcher(line).find()) {
                        offenders.add(name + ":" + (i + 1));
                    }
                }
            }
        }
        assertThat(offenders)
                .as("the audit trail is read and appended to; nothing in the service alters or removes a row")
                .isEmpty();
    }

    @Test
    @DisplayName("no Spring Data repository exists for the audit trail, so nothing can save or delete it by id")
    void noRepositoryForTheTrail() throws IOException {
        List<String> repositories = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Pattern.compile("Repository<\\s*AuditLog\\s*,").matcher(Files.readString(file)).find()) {
                    repositories.add(file.getFileName().toString());
                }
            }
        }
        assertThat(repositories).isEmpty();
    }

    @Test
    @DisplayName("no GraphQL mutation reads, edits or removes audit rows")
    void noMutationTouchesTheTrail() throws IOException {
        var registry = new SchemaParser().parse(Files.readString(Path.of("src/main/resources/graphql/schema.graphqls")));
        ObjectTypeDefinition mutation = registry.getType("Mutation", ObjectTypeDefinition.class).orElseThrow();
        List<String> touching = mutation.getFieldDefinitions().stream()
                .filter(PlatformWideAuditTrailTest::mentionsTheTrail)
                .map(FieldDefinition::getName)
                .toList();
        assertThat(touching).isEmpty();
    }

    private static boolean mentionsTheTrail(FieldDefinition field) {
        String signature = field.getName() + " " + field.getType() + " " + field.getInputValueDefinitions();
        return signature.toLowerCase().contains("auditlog") || field.getName().toLowerCase().contains("audit");
    }

    private static Throwable rawInsertFailure(Document document) {
        return template.insert(document, IdentityCollections.AUDIT_LOGS).then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just).block();
    }
}
