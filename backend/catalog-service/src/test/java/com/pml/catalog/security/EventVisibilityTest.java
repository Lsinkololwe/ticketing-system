package com.pml.catalog.security;

import com.pml.catalog.testing.CatalogWiring;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code event(id)} is a PUBLIC query, so it must answer with a public event.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>Why the public lookup filters</h2>
 * Catalog's public surface filters every list query to published, active events —
 * {@code findByPublishedTrueAndIsActiveTrue} and its siblings — because a draft is nobody's
 * business until it is published. The schema puts {@code event(id: ID!)} in that same PUBLIC
 * block with no {@code @auth} directive and no {@code @PreAuthorize}, so the filter has to live in
 * the query that takes the caller-supplied key.
 *
 * <p>Without it, an id would be enough to read a rival's unannounced draft with its capacity and
 * pricing, a rejected event with its {@code rejectionReason}, or a soft-deleted event together with
 * {@code deletedBy} and {@code deletionReason} — with no account, role or token.
 *
 * <h2>Against a replica set, because the fix is a query</h2>
 * {@code findByIdAndPublishedTrueAndIsActiveTrue} is a derived Mongo query and
 * {@code isActive} is a Lombok-prefixed boolean field — precisely the kind of property
 * mapping that resolves differently against a mock than against the driver. Every case
 * runs the real finder.
 *
 * <h2>Each case proves the filter, not the fixture</h2>
 * An assertion that a draft is invisible passes just as happily when the draft was never
 * written. Every negative case below first asserts the row <em>is</em> present and
 * reachable by unscoped id.
 */
@Tag("L2")
@Tag("ET-PLT-007")
    // The class also iterates ids across two organizations and asserts the response is
    // identical for a non-existent id and for another tenant's id. That property belongs to
    // the error contract as much as to the tenant boundary — one decides who may read, the
    // other that the refusal discloses nothing — so the class carries both tags.
@Tag("ET-PLT-005")
@DisplayName("F-007 · a single event is visible only when published, or to the organization that owns it")
class EventVisibilityTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String NEVER_ISSUED = "event-that-was-never-issued";

    private static final String PUBLISHED = "event-published";
    private static final String DRAFT = "event-draft";
    private static final String REJECTED = "event-rejected";
    private static final String SOFT_DELETED = "event-soft-deleted";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRepository events;
    private static EventServiceImpl service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_event_visibility"));
        events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC);
        service = new EventServiceImpl(
                events,
                clock,
                new com.pml.shared.event.Outbox(template, "catalog_outbox", clock),
                org.springframework.transaction.reactive.TransactionalOperator.create(
                        new org.springframework.data.mongodb.ReactiveMongoTransactionManager(
                                template.getMongoDatabaseFactory())),
                CatalogWiring.venues(template, clock), CatalogWiring.tierFactory(), CatalogWiring.tiers(template),
                CatalogWiring.mirror(template, clock), CatalogWiring.categories(template));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneOfEachVisibility() {
        template.remove(new Query(), Event.class).block();

        save(PUBLISHED, EventStatus.PUBLISHED, true, true);
        save(DRAFT, EventStatus.DRAFT, false, true);
        save(REJECTED, EventStatus.REJECTED, false, true);
        // deleteEventWithReason sets both flags; isActive is what the discovery
        // surface has always keyed on, so that is what the filter checks.
        save(SOFT_DELETED, EventStatus.PUBLISHED, true, false);
    }

    private static void save(String id, EventStatus status, boolean published, boolean active) {
        Event event = Event.builder()
                .id(id)
                .title("Kabwe Jazz Nights")
                .organizationId(OWNER_ORG)
                .organizerId("user-" + OWNER_ORG)
                .status(status)
                .published(published)
                .isActive(active)
                .isDeleted(!active)
                .rejectionReason(status == EventStatus.REJECTED ? "Venue capacity unverified" : null)
                .totalCapacity(4_000)
                .eventDateTime(Instant.parse("2026-12-01T18:00:00Z"))
                .build();
        template.save(event).block();
    }

    /**
     * A signed-in caller belonging to no organization — a plain customer.
     *
     * <p>A signed-in caller, not an anonymous one: all three subgraphs carry
     * {@code .pathMatchers("/graphql/**").authenticated()}, so nothing here is reachable without
     * a token however loudly the schema's PUBLIC header says otherwise. The scope below is
     * {@code denyAll(null)} because a customer has no memberships, and the entry price for
     * holding one of these tokens is registering a phone number.
     */
    @Nested
    @DisplayName("a signed-in caller with no memberships")
    class Customer {

        @Test
        @DisplayName("ET-PLT-007 · reads a published event, which is the point of the discovery query")
        void seesPublished() {
            assertThat(visibleTo(TenantScope.denyAll(null), PUBLISHED))
                    .as("closing F-007 must not close the public catalogue")
                    .isNotNull();
        }

        @Test
        @DisplayName("ET-PLT-007 · cannot read an unpublished draft")
        void cannotSeeDraft() {
            assertThat(events.findById(DRAFT).block())
                    .as("the draft must exist, or this test passes against an empty collection")
                    .isNotNull();

            assertThat(visibleTo(TenantScope.denyAll(null), DRAFT)).isNull();
        }

        @Test
        @DisplayName("ET-PLT-007 · cannot read a rejected event, nor therefore its rejectionReason")
        void cannotSeeRejected() {
            assertThat(events.findById(REJECTED).block().getRejectionReason())
                    .as("the reason must be on the document for its concealment to mean anything")
                    .isEqualTo("Venue capacity unverified");

            assertThat(visibleTo(TenantScope.denyAll(null), REJECTED)).isNull();
        }

        @Test
        @DisplayName("ET-PLT-007 · cannot read a soft-deleted event")
        void cannotSeeSoftDeleted() {
            assertThat(events.findById(SOFT_DELETED).block()).isNotNull();

            assertThat(visibleTo(TenantScope.denyAll(null), SOFT_DELETED))
                    .as("published once is not published still — deletion has to withdraw it")
                    .isNull();
        }

        @Test
        @DisplayName("ET-PLT-007 · a routine miss is not recorded as a security incident")
        void missIsNotAnIncident() {
            // A customer opening a stale link to an unpublished event is the ordinary
            // traffic of the discovery surface. Labelling each one
            // securityIncident=true is how an incident log gets muted, and a muted log
            // takes the real cross-tenant reaches down with it.
            List<ILoggingEvent> recorded = whileCapturingTenantGuardLogs(
                    () -> visibleTo(TenantScope.denyAll(null), DRAFT));

            assertThat(recorded)
                    .as("ordinary discovery misses must not reach the incident log")
                    .noneSatisfy(entry -> assertThat(entry.getFormattedMessage())
                            .contains("securityIncident=true"));
        }

        @Test
        @DisplayName("ET-PLT-007 · an authenticated caller reaching for somebody else's draft still is")
        void authenticatedReachIsStillAnIncident() {
            // The other half, and the reason the case above is a filter on `subject`
            // rather than a blanket removal of the guard: silence for a caller with no
            // subject is only affordable while a probe from a member is still recorded.
            List<ILoggingEvent> recorded = whileCapturingTenantGuardLogs(
                    () -> visibleTo(TenantScope.of("u", Set.of(OTHER_ORG)), DRAFT));

            assertThat(recorded)
                    .as("a member of another organization reaching a real id is worth seeing")
                    .anySatisfy(entry -> assertThat(entry.getFormattedMessage())
                            .contains("securityIncident=true"));
        }

        @Test
        @DisplayName("ET-PLT-007 · a hidden event and an id that was never issued answer identically")
        void hiddenAndUnknownAreIndistinguishable() {
            // The anti-enumeration property. If a draft answered differently from an invented
            // id, the query would sort real ids from invented ones for anyone with a list.
            assertThat(visibleTo(TenantScope.denyAll(null), DRAFT))
                    .isEqualTo(visibleTo(TenantScope.denyAll(null), NEVER_ISSUED))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("an authenticated organizer")
    class Organizer {

        @Test
        @DisplayName("ET-PLT-007 · reads their own organization's draft")
        void ownerSeesOwnDraft() {
            Event seen = visibleTo(TenantScope.of("u", Set.of(OWNER_ORG)), DRAFT);

            assertThat(seen)
                    .as("an organizer must still be able to open the event they are writing")
                    .isNotNull();
            assertThat(seen.getStatus()).isEqualTo(EventStatus.DRAFT);
        }

        @Test
        @DisplayName("ET-PLT-007 · cannot read another organization's draft")
        void nonOwnerIsRefused() {
            assertThat(events.findById(DRAFT).block()).isNotNull();

            assertThat(visibleTo(TenantScope.of("u", Set.of(OTHER_ORG)), DRAFT))
                    .as("a role says whether an account may read events, never which ones")
                    .isNull();
        }

        @Test
        @DisplayName("ET-PLT-007 · still reads another organization's published event")
        void nonOwnerStillSeesPublished() {
            // The ordering the implementation depends on: public visibility is decided
            // before tenancy, so scoping the miss does not scope the catalogue.
            assertThat(visibleTo(TenantScope.of("u", Set.of(OTHER_ORG)), PUBLISHED)).isNotNull();
        }
    }

    @Test
    @DisplayName("ET-PLT-007 · a platform administrator reads a draft in any organization")
    void administratorSeesEverything() {
        assertThat(visibleTo(TenantScope.platformAdministrator("ops", Set.of()), DRAFT))
                .as("moderation cannot work through a filter that hides what is to be moderated")
                .isNotNull();
    }

    /** Runs {@code action}, returning everything {@link TenantGuard} logged while it ran. */
    private static List<ILoggingEvent> whileCapturingTenantGuardLogs(Runnable action) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TenantGuard.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level original = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
            appender.stop();
        }
        return List.copyOf(appender.list);
    }

    /** What {@code event(id)} would return for this caller. */
    private static Event visibleTo(TenantScope scope, String id) {
        return service.findVisibleById(id)
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }
}
