package com.pml.catalog.security;

import com.pml.catalog.testing.CatalogWiring;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ET-PLT-007 Phase 6 · {@code setEventFeatured}, {@code sendPublishReminder} and
 * {@code deleteEventWithReason} each used to re-fetch their event with a bare
 * {@code eventRepository.findById(id)} — no filter of any kind. {@code setEventFeatured} and
 * {@code sendPublishReminder} are reached only from an ADMIN-only mutation, so an admin acting
 * across organizations is correct and expected; what this proves is that these methods now read
 * the caller's real {@link CurrentTenantScope} rather than skip the check outright, so a future
 * caller that is not admin-gated is refused here too, instead of silently reaching across tenants.
 *
 * <p>Flat {@code @Test} methods rather than {@code @Nested} groups: see F-055. This project's
 * Surefire setup never executes a single {@code @Nested} test method — confirmed with a minimal,
 * dependency-free probe class independent of anything in this file — so grouping these into
 * Outsider/Administrator nested classes would silently never run them.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("F-001 · an event's admin operations are reachable only by the organization that owns it, or a platform administrator")
class EventAdminOperationsTenantBoundaryTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String EVENT_ID = "event-kabwe-jazz-night";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRepository events;
    private static EventServiceImpl service;

    private static final TenantScope OUTSIDER = TenantScope.of("user-outsider", Set.of(OTHER_ORG));
    private static final TenantScope ADMIN = TenantScope.platformAdministrator("user-admin", Set.of());

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_event_admin_boundary"));
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
                CatalogWiring.mirror(template, clock), CatalogWiring.categories(template),
                org.mockito.Mockito.mock(com.pml.catalog.infrastructure.client.IdentityServiceClient.class));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneEventOwnedByKabwe() {
        template.remove(new Query(), Event.class).block();
        Event event = Event.builder()
                .id(EVENT_ID)
                .title("Kabwe Jazz Nights")
                .organizationId(OWNER_ORG)
                .organizerId("user-" + OWNER_ORG)
                .status(EventStatus.APPROVED)
                .published(false)
                .featured(false)
                .isActive(true)
                .totalCapacity(4_000)
                .eventDateTime(Instant.parse("2026-12-01T18:00:00Z"))
                .build();
        template.save(event).block();
    }

    private static <T> Mono<T> as(TenantScope scope, Mono<T> operation) {
        return operation.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)));
    }

    @Test
    @DisplayName("an outsider's setEventFeatured is refused, and the event is not featured")
    void outsiderCannotFeature() {
        assertThat(events.findById(EVENT_ID).block()).as("reachable by unscoped id").isNotNull();

        assertThatThrownBy(() -> as(OUTSIDER, service.setEventFeatured(EVENT_ID, true)).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN));

        assertThat(events.findById(EVENT_ID).block().isFeatured()).isFalse();
    }

    @Test
    @DisplayName("an outsider's sendPublishReminder is refused")
    void outsiderCannotSendReminder() {
        assertThatThrownBy(() -> as(OUTSIDER, service.sendPublishReminder(EVENT_ID, "admin-1")).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN));
    }

    @Test
    @DisplayName("an outsider's deleteEventWithReason is refused, and the event survives")
    void outsiderCannotDelete() {
        assertThatThrownBy(() -> as(OUTSIDER, service.deleteEventWithReason(EVENT_ID, "user-outsider", "cleanup")).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN));

        assertThat(events.findById(EVENT_ID).block()).as("survives the refused delete").isNotNull();
    }

    @Test
    @DisplayName("a platform administrator's setEventFeatured succeeds across organizations, proving the guard is not merely vacuous")
    void administratorCanFeatureAnyOrganizationsEvent() {
        Event featured = as(ADMIN, service.setEventFeatured(EVENT_ID, true)).block();
        assertThat(featured.isFeatured()).isTrue();
    }
}
