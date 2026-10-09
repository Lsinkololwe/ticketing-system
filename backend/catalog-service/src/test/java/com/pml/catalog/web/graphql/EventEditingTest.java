package com.pml.catalog.web.graphql;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.security.EventWriteGuard;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.UpdateEventInput;
import com.pml.catalog.web.graphql.mutation.EventMutationResolver;
import com.pml.catalog.workflow.approval.EventApprovalProcess;
import com.pml.catalog.workflow.lifecycle.EventLifecycleProcess;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.event.Outbox;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Editing an event through the resolver, against a MongoDB replica set: every field the schema
 * offers is stored, and the lifecycle decides what a change may do. A material change — when,
 * where, by whom, how many — sends an approved event back for review and is refused on a published
 * one; anything else leaves the status alone.
 */
@Tag("L2")
@Tag("ET-CAT-001")
@Tag("ET-PLT-007")
@DisplayName("An event edit is stored whole, and a material change goes back for review")
class EventEditingTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-11-14T16:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-14T23:00:00Z");
    private static final String EVENT = "65a1b2c3d4e5f60718293a4c";
    private static final String ORGANIZER = "user-organizer-1";
    private static final String ORGANIZATION = EventAuthoringTest.ORGANIZATION;
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRepository events;

    private EventAuthoringTest.StubIdentity identity;
    private EventMutationResolver resolver;
    private EventLifecycleProcess lifecycle;
    private com.pml.catalog.workflow.schedule.EventPublishScheduleProcess schedules;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_event_editing");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        // The service's own collection validators, applied as it applies them at startup: every write
        // below must also be one the production database accepts.
        new com.pml.catalog.config.MongoSchemaValidationConfig(template,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.pml.shared.config.MongoSchemaValidationProperties()).run(null);
        events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
        for (String collection : List.of(CatalogCollections.EVENTS, CatalogCollections.LOCATIONS, CatalogCollections.REFERENCE_DATA)) {
            template.collectionExists(collection)
                    .flatMap(exists -> exists ? Mono.empty() : template.createCollection(collection).then())
                    .block();
        }
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void wire() {
        for (String collection : List.of(CatalogCollections.EVENTS, CatalogCollections.LOCATIONS, CatalogCollections.REFERENCE_DATA)) {
            template.remove(new Query(), collection).block();
        }
        template.insertAll(List.of(
                geography(ReferenceType.COUNTRY, "ZM", "Zambia", null, null, Map.of("dialCode", "+260", "iso3", "ZMB")),
                geography(ReferenceType.PROVINCE, "CB", "Copperbelt", ReferenceType.COUNTRY, "ZM", Map.of()),
                geography(ReferenceType.CITY, "KITWE", "Kitwe", ReferenceType.PROVINCE, "CB",
                        Map.of("latitude", -12.8024, "longitude", 28.2132)))).collectList().block();
        identity = new EventAuthoringTest.StubIdentity();
        EventServiceImpl service = new EventServiceImpl(events, CLOCK, new Outbox(template, "catalog_outbox", CLOCK),
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                CatalogWiring.venues(template, CLOCK), CatalogWiring.tierFactory(), CatalogWiring.tiers(template),
                CatalogWiring.mirror(template, CLOCK), CatalogWiring.categories(template));
        lifecycle = Mockito.mock(EventLifecycleProcess.class);
        schedules = Mockito.mock(com.pml.catalog.workflow.schedule.EventPublishScheduleProcess.class);
        Mockito.when(schedules.schedule(Mockito.anyString(), Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(schedules.move(Mockito.anyString(), Mockito.any())).thenReturn(Mono.empty());
        Mockito.when(schedules.cancel(Mockito.anyString())).thenReturn(Mono.empty());
        resolver = new EventMutationResolver(service, CLOCK, identity, new EventWriteGuard(events, identity),
                lifecycle, Mockito.mock(EventApprovalProcess.class), schedules);
    }

    private static void seed(EventStatus status) {
        template.remove(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(EVENT)), Event.class).block();
        template.save(Event.builder()
                .id(EVENT)
                .title("Lusaka Jazz Night")
                .description("Live jazz")
                .organizationId(ORGANIZATION)
                .organizerId(ORGANIZER)
                .status(status)
                .published(status == EventStatus.PUBLISHED)
                .isActive(true)
                .eventDateTime(STARTS)
                .endDateTime(ENDS)
                .totalCapacity(150)
                .categoryId("MUSIC")
                .createdAt(NOW.minusSeconds(7 * 86_400))
                .locationId("6a85a3dad85e861c848e2e00")
                .approvedAt(status == EventStatus.APPROVED ? NOW.minusSeconds(86_400) : null)
                .approvedBy(status == EventStatus.APPROVED ? "reviewer-1" : null)
                .build()).block();
    }

    private Event edit(Map<String, Object> payload, String... roles) {
        UpdateEventInput input = new DefaultInputObjectMapper().mapToJavaObject(payload, UpdateEventInput.class);
        return resolver.updateEvent(EVENT, input)
                .contextWrite(EventAuthoringTest.signedIn(ORGANIZER, roles))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.of(ORGANIZER, Set.of(ORGANIZATION)))))
                .block();
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static ErrorCode refusal(Runnable call) {
        try {
            call.run();
        } catch (DomainRefusal refused) {
            return refused.errorCode();
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    @DisplayName("every field the edit form can send is stored")
    void storesEveryField() {
        seed(EventStatus.DRAFT);

        edit(payload(
                "title", "Lusaka Jazz Night — Extended",
                "thumbnailImageUrl", "https://cdn.example.com/thumb.jpg",
                "galleryImages", List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"),
                "isVirtual", true,
                "virtualEventUrl", "https://stream.example.com/jazz",
                "virtualEventPlatform", "YouTube",
                "refundPolicy", "PARTIAL_REFUND",
                "cancellationPolicy", "Refunds until 48 hours before.",
                "termsAndConditions", "Over 18s only.",
                "enableWaitlist", true,
                "waitlistCapacity", 40));

        Event stored = events.findById(EVENT).block();
        assertThat(stored.getTitle()).isEqualTo("Lusaka Jazz Night — Extended");
        assertThat(stored.getThumbnailImageUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
        assertThat(stored.getGalleryImages()).hasSize(2);
        assertThat(stored.isVirtual()).isTrue();
        assertThat(stored.getVirtualEventUrl()).isEqualTo("https://stream.example.com/jazz");
        assertThat(stored.getVirtualEventPlatform()).isEqualTo("YouTube");
        assertThat(stored.getRefundPolicy()).isEqualTo("PARTIAL_REFUND");
        assertThat(stored.getCancellationPolicy()).isEqualTo("Refunds until 48 hours before.");
        assertThat(stored.getTermsAndConditions()).isEqualTo("Over 18s only.");
        assertThat(stored.isWaitlistEnabled()).isTrue();
        assertThat(stored.getWaitlistCapacity()).isEqualTo(40);
        assertThat(stored.getUpdatedBy()).isEqualTo(ORGANIZER);
    }

    @Test
    @DisplayName("a new venue is resolved against the reference data's cities")
    void movesVenue() {
        seed(EventStatus.DRAFT);

        edit(payload("location", payload("name", "Nkana Stadium", "address", "Freedom Ave", "city", "Kitwe", "country", "Zambia")));

        Event stored = events.findById(EVENT).block();
        assertThat(stored.getCityId()).isEqualTo("KITWE");
        assertThat(stored.getLocationName()).isEqualTo("Nkana Stadium");
    }

    @Nested
    @DisplayName("an approved event")
    class Approved {

        static Stream<Map<String, Object>> materialEdits() {
            return Stream.of(
                    payload("eventDateTime", STARTS.plusSeconds(3600)),
                    payload("endDateTime", ENDS.plusSeconds(3600)),
                    payload("totalCapacity", 200),
                    payload("location", payload("name", "Nkana Stadium", "address", "Freedom Ave",
                            "city", "Kitwe", "country", "Zambia")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("materialEdits")
        @DisplayName("each material change sends it back to draft and clears the approval")
        void materialChangeReturnsToDraft(Map<String, Object> change) {
            seed(EventStatus.APPROVED);

            edit(change);

            Event stored = events.findById(EVENT).block();
            assertThat(stored.getStatus()).isEqualTo(EventStatus.DRAFT);
            assertThat(stored.getApprovedAt()).isNull();
            assertThat(stored.getApprovedBy()).isNull();
        }

        @Test
        @DisplayName("a non-material change leaves it approved")
        void nonMaterialKeepsApproval() {
            seed(EventStatus.APPROVED);

            edit(payload("description", "Now with a guest trumpeter", "bannerImageUrl", "https://cdn.example.com/new.jpg"));

            Event stored = events.findById(EVENT).block();
            assertThat(stored.getStatus()).isEqualTo(EventStatus.APPROVED);
            assertThat(stored.getApprovedBy()).isEqualTo("reviewer-1");
        }
    }

    @Nested
    @DisplayName("a published event")
    class Published {

        @Test
        @DisplayName("a material change is refused with its status, and nothing changes")
        void materialChangeRefused() {
            seed(EventStatus.PUBLISHED);

            DomainRefusal refused = null;
            try {
                edit(payload("eventDateTime", STARTS.plusSeconds(3600)));
            } catch (DomainRefusal e) {
                refused = e;
            }

            assertThat(refused).isNotNull();
            assertThat(refused.errorCode()).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
            assertThat(refused.details()).containsEntry("currentStatus", "PUBLISHED");
            assertThat(events.findById(EVENT).block().getEventDateTime()).isEqualTo(STARTS);
        }

        @Test
        @DisplayName("a non-material change is accepted")
        void nonMaterialAccepted() {
            seed(EventStatus.PUBLISHED);

            edit(payload("termsAndConditions", "Bring ID."));

            assertThat(events.findById(EVENT).block().getTermsAndConditions()).isEqualTo("Bring ID.");
        }
    }

    @Test
    @DisplayName("an event under review keeps what the reviewer is looking at")
    void underReviewRefusesMaterialChange() {
        seed(EventStatus.PENDING_APPROVAL);

        assertThat(refusal(() -> edit(payload("totalCapacity", 999)))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
    }

    @Test
    @DisplayName("editing a rejected event is a revision and returns it to draft")
    void rejectedBecomesDraft() {
        seed(EventStatus.REJECTED);

        edit(payload("description", "Addressed the reviewer's comments"));

        assertThat(events.findById(EVENT).block().getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"COMPLETED", "CANCELLED"})
    @DisplayName("a finished event cannot be edited")
    void finishedEventsAreClosed(EventStatus status) {
        seed(status);

        assertThat(refusal(() -> edit(payload("title", "Rewritten history")))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        @DisplayName("an organizer cannot make their own event featured")
        void organizerCannotFeature() {
            seed(EventStatus.DRAFT);

            assertThat(refusal(() -> edit(payload("featured", true)))).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
            assertThat(events.findById(EVENT).block().isFeatured()).isFalse();
        }

        @Test
        @DisplayName("a platform administrator can")
        void adminCanFeature() {
            seed(EventStatus.DRAFT);

            edit(payload("featured", true), "ROLE_ADMIN");

            assertThat(events.findById(EVENT).block().isFeatured()).isTrue();
        }

        @Test
        @DisplayName("another organization's event looks like no event at all")
        void otherTenant() {
            seed(EventStatus.DRAFT);
            UpdateEventInput input = new DefaultInputObjectMapper().mapToJavaObject(payload("title", "Hijacked"), UpdateEventInput.class);

            assertThatThrownBy(() -> resolver.updateEvent(EVENT, input)
                    .contextWrite(EventAuthoringTest.signedIn("user-intruder"))
                    .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.of("user-intruder", Set.of("org-rival")))))
                    .block())
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN));
            assertThat(events.findById(EVENT).block().getTitle()).isEqualTo("Lusaka Jazz Night");
        }

        @Test
        @DisplayName("a member without event:edit is refused")
        void withoutPermission() {
            seed(EventStatus.DRAFT);
            identity.authorized = false;

            assertThatThrownBy(() -> edit(payload("title", "Nope")))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("a gallery link that is not http(s) is refused")
    void galleryLinksAreWebLinks() {
        seed(EventStatus.DRAFT);

        try {
            edit(payload("galleryImages", List.of("https://cdn.example.com/ok.jpg", "data:text/html,<script>alert(1)</script>")));
            throw new AssertionError("expected a refusal");
        } catch (ValidationRefusal refused) {
            assertThat(refused.violations()).extracting(FieldViolation::path).contains("galleryImages[1]");
        }
        assertThat(events.findById(EVENT).block().getGalleryImages()).isNull();
    }

    private static ReferenceData geography(ReferenceType type, String code, String name, ReferenceType parentType,
                                           String parentCode, Map<String, Object> metadata) {
        return ReferenceData.builder().type(type).code(code).name(name).parentType(parentType)
                .parentCode(parentCode).isActive(true).isSystem(true).metadata(new HashMap<>(metadata)).build();
    }

    // ── ET-CAT-004 ───────────────────────────────────────────────────────────────────────────────

    private Event as(String subject, TenantScope scope, String[] roles, java.util.function.Supplier<Mono<Event>> call) {
        return call.get()
                .contextWrite(EventAuthoringTest.signedIn(subject, roles))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    @Nested
    @DisplayName("the event page content")
    class PageContent {

        @Test
        @DisplayName("every content field the editor sends is stored, and editing it leaves an approved event approved")
        void storedWithoutReview() {
            seed(EventStatus.APPROVED);

            edit(payload(
                    "tagline", "Smooth and loud",
                    "ageRestriction", "16+",
                    "doorsOpenAt", STARTS.minusSeconds(1800),
                    "faqs", List.of(payload("question", "Can I bring a child?", "answer", "From 16.")),
                    "runningOrder", List.of(payload("time", "16:30", "title", "Doors")),
                    "gettingThere", "Minibus from Town",
                    "parkingInfo", "Free",
                    "bagPolicy", "No bottles",
                    "checkoutSettings", payload("maxTicketsPerOrder", 5, "collectHolderNames", false, "extraQuestion", "Allergies?"),
                    "bannerAltText", "Band on stage"));

            Event stored = events.findById(EVENT).block();
            assertThat(stored.getStatus()).as("content is not a material change").isEqualTo(EventStatus.APPROVED);
            assertThat(stored.getTagline()).isEqualTo("Smooth and loud");
            assertThat(stored.getAgeRestriction()).isEqualTo("16+");
            assertThat(stored.getDoorsOpenAt()).isEqualTo(STARTS.minusSeconds(1800));
            assertThat(stored.getFaqs()).hasSize(1);
            assertThat(stored.getRunningOrder()).hasSize(1);
            assertThat(stored.getGettingThere()).isEqualTo("Minibus from Town");
            assertThat(stored.getParkingInfo()).isEqualTo("Free");
            assertThat(stored.getBagPolicy()).isEqualTo("No bottles");
            assertThat(stored.getCheckoutSettings().maxTicketsPerOrder()).isEqualTo(5);
            assertThat(stored.getBannerAltText()).isEqualTo("Band on stage");
        }

        @Test
        @DisplayName("a field not sent is not touched; an empty list clears")
        void partialUpdate() {
            seed(EventStatus.DRAFT);
            edit(payload("tagline", "First", "faqs", List.of(payload("question", "Q?", "answer", "A."))));

            edit(payload("parkingInfo", "Free"));
            assertThat(events.findById(EVENT).block().getTagline()).isEqualTo("First");
            assertThat(events.findById(EVENT).block().getFaqs()).hasSize(1);

            edit(payload("faqs", List.of()));
            assertThat(events.findById(EVENT).block().getFaqs()).isEmpty();
        }

        @Test
        @DisplayName("a go-live time that has passed is refused when set, and doors after the start are refused")
        void refusals() {
            seed(EventStatus.DRAFT);

            try {
                edit(payload("publishAt", NOW.minusSeconds(60), "doorsOpenAt", STARTS.plusSeconds(60)));
                throw new AssertionError("expected a refusal");
            } catch (ValidationRefusal refused) {
                assertThat(refused.violations()).extracting(FieldViolation::path)
                        .containsExactlyInAnyOrder("publishAt", "doorsOpenAt");
            }
        }
    }

    @Nested
    @DisplayName("scheduled publication")
    class Scheduling {

        private Event publish(String... roles) {
            return as(ORGANIZER, TenantScope.of(ORGANIZER, Set.of(ORGANIZATION)), roles, () -> resolver.publishEvent(EVENT));
        }

        private void seedApproved(Instant publishAt) {
            seed(EventStatus.APPROVED);
            events.findById(EVENT).block();
            Event event = events.findById(EVENT).block();
            event.setPublishAt(publishAt);
            events.save(event).block();
        }

        @Test
        @DisplayName("publishing an approved event with a go-live time ahead schedules it and publishes nothing")
        void schedules() {
            Instant at = NOW.plusSeconds(3 * 86_400);
            seedApproved(at);

            Event result = publish();

            assertThat(result.getStatus()).isEqualTo(EventStatus.APPROVED);
            assertThat(result.isPublishScheduled()).isTrue();
            Event stored = events.findById(EVENT).block();
            assertThat(stored.isPublishScheduled()).isTrue();
            assertThat(stored.isPublished()).isFalse();
            Mockito.verify(schedules).schedule(EVENT, at);
            Mockito.verifyNoInteractions(lifecycle);
        }

        @Test
        @DisplayName("with no go-live time, or one already past, publishing publishes now")
        void publishesNow() {
            Mockito.when(lifecycle.publish(Mockito.eq(EVENT), Mockito.anyString())).thenReturn(Mono.just(Event.builder().id(EVENT).build()));
            seedApproved(null);

            publish();
            Mockito.verify(lifecycle).publish(EVENT, ORGANIZER);
            Mockito.verifyNoInteractions(schedules);

            seedApproved(NOW.minusSeconds(60));
            publish();
            Mockito.verify(lifecycle, Mockito.times(2)).publish(EVENT, ORGANIZER);
            Mockito.verify(schedules, Mockito.never()).schedule(Mockito.anyString(), Mockito.any());
        }

        @Test
        @DisplayName("an event that is not approved is not scheduled, whatever its go-live time")
        void onlyApproved() {
            Mockito.when(lifecycle.publish(Mockito.eq(EVENT), Mockito.anyString())).thenReturn(Mono.just(Event.builder().id(EVENT).build()));
            seed(EventStatus.DRAFT);
            Event event = events.findById(EVENT).block();
            event.setPublishAt(NOW.plusSeconds(86_400));
            events.save(event).block();

            publish();

            Mockito.verify(lifecycle).publish(EVENT, ORGANIZER);
            assertThat(events.findById(EVENT).block().isPublishScheduled()).isFalse();
        }

        @Test
        @DisplayName("scheduling twice is the same schedule")
        void idempotent() {
            seedApproved(NOW.plusSeconds(86_400));

            publish();
            publish();

            assertThat(events.findById(EVENT).block().isPublishScheduled()).isTrue();
        }

        @Test
        @DisplayName("moving the go-live time of a scheduled event moves the wait")
        void editMovesTheWait() {
            seedApproved(NOW.plusSeconds(86_400));
            publish();
            Instant later = NOW.plusSeconds(5 * 86_400);

            edit(payload("publishAt", later));

            assertThat(events.findById(EVENT).block().getPublishAt()).isEqualTo(later);
            Mockito.verify(schedules).move(EVENT, later);
        }

        @Test
        @DisplayName("an edit that sends the event back for review ends the schedule")
        void materialEditEndsIt() {
            seedApproved(NOW.plusSeconds(86_400));
            publish();

            edit(payload("totalCapacity", 200));

            Event stored = events.findById(EVENT).block();
            assertThat(stored.getStatus()).isEqualTo(EventStatus.DRAFT);
            assertThat(stored.isPublishScheduled()).isFalse();
            Mockito.verify(schedules).cancel(EVENT);
        }

        @Test
        @DisplayName("cancelScheduledPublish clears the time and stops the wait; an unscheduled event is returned unchanged")
        void cancelSchedule() {
            seedApproved(NOW.plusSeconds(86_400));
            publish();

            Event cleared = as(ORGANIZER, TenantScope.of(ORGANIZER, Set.of(ORGANIZATION)), new String[0],
                    () -> resolver.cancelScheduledPublish(EVENT));

            assertThat(cleared.isPublishScheduled()).isFalse();
            assertThat(cleared.getPublishAt()).isNull();
            assertThat(events.findById(EVENT).block().getPublishAt()).isNull();
            Mockito.verify(schedules).cancel(EVENT);
            assertThat(as(ORGANIZER, TenantScope.of(ORGANIZER, Set.of(ORGANIZATION)), new String[0],
                    () -> resolver.cancelScheduledPublish(EVENT)).getStatus()).isEqualTo(EventStatus.APPROVED);
        }

        @Test
        @DisplayName("another organization cannot schedule or unschedule it")
        void tenantScoped() {
            seedApproved(NOW.plusSeconds(86_400));
            TenantScope rival = TenantScope.of("intruder", Set.of("org-rival"));

            assertThat(refusal(() -> as("intruder", rival, new String[0], () -> resolver.publishEvent(EVENT))))
                    .isEqualTo(ErrorCode.EVENT_UNKNOWN);
            assertThat(refusal(() -> as("intruder", rival, new String[0], () -> resolver.cancelScheduledPublish(EVENT))))
                    .isEqualTo(ErrorCode.EVENT_UNKNOWN);
            assertThat(events.findById(EVENT).block().isPublishScheduled()).isFalse();
            Mockito.verifyNoInteractions(schedules);
        }
    }

    @Nested
    @DisplayName("a platform administrator cancelling an event")
    class AdminCancel {

        private EventCancellationInputDtoHolder input() {
            return new EventCancellationInputDtoHolder();
        }

        /** The mutation's input as DGS binds it. */
        final class EventCancellationInputDtoHolder {
            final com.pml.catalog.web.graphql.dto.EventCancellationInputDto dto =
                    new DefaultInputObjectMapper().mapToJavaObject(payload("reason", "Venue lost its licence"),
                            com.pml.catalog.web.graphql.dto.EventCancellationInputDto.class);
        }

        @Test
        @DisplayName("cancels another organization's event, as the organizer could, with the administrator as the actor")
        void adminCancels() {
            seed(EventStatus.PUBLISHED);
            Mockito.when(lifecycle.cancel(Mockito.eq(EVENT), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Mono.just(Event.builder().id(EVENT).status(EventStatus.CANCELLED).soldTickets(7).build()));

            var response = resolver.cancelEvent(EVENT, input().dto)
                    .contextWrite(EventAuthoringTest.signedIn("admin-1", "ROLE_ADMIN"))
                    .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.platformAdministrator("admin-1", Set.of()))))
                    .block();

            assertThat(response.getTicketsAffected()).isEqualTo(7);
            Mockito.verify(lifecycle).cancel(EVENT, "admin-1", "Venue lost its licence");
        }

        @Test
        @DisplayName("an organizer of another organization still cannot")
        void strangerCannot() {
            seed(EventStatus.PUBLISHED);

            assertThat(refusal(() -> resolver.cancelEvent(EVENT, input().dto)
                    .contextWrite(EventAuthoringTest.signedIn("intruder", "ROLE_ORGANIZER"))
                    .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.of("intruder", Set.of("org-rival")))))
                    .block())).isEqualTo(ErrorCode.EVENT_UNKNOWN);
            Mockito.verifyNoInteractions(lifecycle);
        }
    }
}
