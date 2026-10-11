package com.pml.catalog.web.graphql;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.security.EventWriteGuard;
import com.pml.catalog.service.EventService;
import com.pml.catalog.service.TicketTierFactory;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.CreateEventInput;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.mutation.EventMutationResolver;
import com.pml.catalog.web.rest.InternalEventController;
import com.pml.catalog.workflow.approval.EventApprovalProcess;
import com.pml.catalog.workflow.lifecycle.EventLifecycleProcess;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.PlatformRefusalTranslator;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.event.Outbox;
import com.pml.shared.security.InternalServiceWebClients;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Creating an event from exactly what the organizer app sends, end to end against a MongoDB replica
 * set: the payload is bound by DGS's own input mapper, then goes through the resolver, the permission
 * check, the venue lookup in the reference data, and one transaction that writes the venue, the event
 * and its tiers.
 */
@Tag("L2")
@Tag("ET-CAT-001")
@Tag("ET-CAT-002")
@Tag("ET-CAT-003")
@Tag("ET-PLT-007")
@DisplayName("An event is created with everything the organizer entered, or not at all")
class EventAuthoringTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-11-14T16:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-14T23:00:00Z");
    private static final String ORGANIZER = "user-organizer-1";
    static final String ORGANIZATION = "65a1b2c3d4e5f60718293a4b";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRepository events;
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private StubIdentity identity;
    private EventService service;
    private EventMutationResolver resolver;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_event_authoring");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        // The service's own collection validators, applied as it applies them at startup: every write
        // below must also be one the production database accepts.
        new com.pml.catalog.config.MongoSchemaValidationConfig(template,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.pml.shared.config.MongoSchemaValidationProperties()).run(null);
        events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
        // Collections exist up front: a multi-document transaction may not create one.
        for (String collection : List.of(CatalogCollections.EVENTS, CatalogCollections.TICKET_TIERS,
                CatalogCollections.LOCATIONS, CatalogCollections.REFERENCE_DATA)) {
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
    void freshCatalogue() {
        for (String collection : List.of(CatalogCollections.EVENTS, CatalogCollections.TICKET_TIERS,
                CatalogCollections.LOCATIONS, CatalogCollections.REFERENCE_DATA)) {
            template.remove(new Query(), collection).block();
        }
        seedGeography();
        identity = new StubIdentity();
        service = serviceWith(CatalogWiring.tierFactory());
        resolver = resolverFor(service);
    }

    // ── the organizer's payload, as the New event page builds it ───────────────────────────────

    private static Map<String, Object> newEventPayload() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", "Lusaka Jazz Night");
        input.put("description", "An evening of live jazz.");
        input.put("categoryId", "MUSIC");
        input.put("eventDateTime", STARTS);
        input.put("endDateTime", ENDS);
        input.put("totalCapacity", 150);
        input.put("ticketTiers", List.of(
                tier("GENERAL", "General", "150.00", 100, 0),
                tier("VIP", "VIP", "500.00", 50, 1)));
        input.put("isVirtual", false);
        input.put("virtualEventUrl", null);
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("name", "Mulungushi Conference Centre");
        location.put("address", "Great East Road");
        location.put("city", "lusaka");
        location.put("country", "Zambia");
        input.put("location", location);
        input.put("bannerImageUrl", "https://cdn.example.com/jazz.jpg");
        return input;
    }

    private static Map<String, Object> tier(String code, String name, String price, int quantity, int sortOrder) {
        Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("code", code);
        tier.put("name", name);
        tier.put("description", null);
        tier.put("price", new BigDecimal(price));
        tier.put("currency", "ZMW");
        tier.put("quantity", quantity);
        tier.put("sortOrder", sortOrder);
        return tier;
    }

    /** What DGS does to the arguments before the resolver sees them. */
    private static CreateEventInput bind(Map<String, Object> payload) {
        return new DefaultInputObjectMapper().mapToJavaObject(payload, CreateEventInput.class);
    }

    private Event create(Map<String, Object> payload) {
        return resolver.createEvent(bind(payload))
                .contextWrite(signedIn(ORGANIZER))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.of(ORGANIZER, Set.of(ORGANIZATION)))))
                .block();
    }

    private List<FieldViolation> violationsOf(Map<String, Object> payload) {
        try {
            create(payload);
        } catch (ValidationRefusal refused) {
            return refused.violations();
        }
        throw new AssertionError("expected the input to be refused");
    }

    @Nested
    @DisplayName("the organizer app's payload")
    class ThePayload {

        @Test
        @DisplayName("stores every field the page sends: venue, tiers, banner, format")
        void storesEverything() {
            Event created = create(newEventPayload());

            Event stored = events.findById(created.getId()).block();
            assertThat(stored.getStatus().name()).isEqualTo("DRAFT");
            assertThat(stored.getOrganizationId()).isEqualTo(ORGANIZATION);
            assertThat(stored.getOrganizerId()).isEqualTo(ORGANIZER);
            assertThat(stored.getOrganizerName()).as("denormalized from identity at creation").isEqualTo("Test Organization");
            assertThat(stored.getBannerImageUrl()).isEqualTo("https://cdn.example.com/jazz.jpg");
            assertThat(stored.isVirtual()).isFalse();
            assertThat(stored.getLocationName()).isEqualTo("Mulungushi Conference Centre");
            assertThat(stored.getLocationAddress()).isEqualTo("Great East Road");
            assertThat(stored.getCityId()).isEqualTo("LUSAKA");
            assertThat(stored.getCityName()).as("the reference data's spelling, not the typed one").isEqualTo("Lusaka");
            assertThat(stored.getTotalCapacity()).as("the sum of the tiers").isEqualTo(150);
            assertThat(stored.getLowestTicketPrice()).isEqualByComparingTo("150.00");

            List<TicketTier> tiers = template.find(Query.query(Criteria.where("eventId").is(created.getId())),
                    TicketTier.class).collectList().block();
            assertThat(tiers).extracting(TicketTier::getCode).containsExactlyInAnyOrder("GENERAL", "VIP");
            assertThat(tiers).allSatisfy(tier -> {
                assertThat(tier.getOrganizationId()).isEqualTo(ORGANIZATION);
                assertThat(tier.getCurrency()).isEqualTo("ZMW");
                assertThat(tier.getSalesEndAt()).as("sales close when the event starts").isEqualTo(STARTS);
                assertThat(tier.getMaxPerOrder()).as("the platform default").isEqualTo(10);
            });
            assertThat(stored.getTicketCategories()).extracting(Event.EventTicketCategory::getTierId)
                    .containsExactlyInAnyOrderElementsOf(tiers.stream().map(TicketTier::getId).toList());
        }

        @Test
        @DisplayName("records the venue once, under the reference data's city, attributed to the organization")
        void recordsTheVenue() {
            Event created = create(newEventPayload());

            Location venue = template.findById(created.getLocationId(), Location.class).block();
            assertThat(venue.getCityId()).isEqualTo("LUSAKA");
            assertThat(venue.getProvinceName()).isEqualTo("Lusaka");
            assertThat(venue.getCountry()).isEqualTo("Zambia");
            assertThat(venue.getOrganizationId()).isEqualTo(ORGANIZATION);
            assertThat(venue.getCreatedById()).isEqualTo(ORGANIZER);
            assertThat(venue.getLatitude()).as("the city's coordinates when none were given").isEqualTo(-15.3875);
        }

        @Test
        @DisplayName("a second event at the same venue, typed in another case, reuses it")
        void reusesAVenue() {
            Event first = create(newEventPayload());
            Map<String, Object> again = newEventPayload();
            ((Map<String, Object>) again.get("location")).put("name", "MULUNGUSHI conference centre");

            Event second = create(again);

            assertThat(second.getLocationId()).isEqualTo(first.getLocationId());
            Persistence.assertExactly(template, CatalogCollections.LOCATIONS, 1);
        }

        @Test
        @DisplayName("booking can price a ticket for the new event by the tier id the buyer selects")
        void bookingCanPriceIt() {
            Event created = create(newEventPayload());
            TicketTier vip = template.findOne(Query.query(Criteria.where("eventId").is(created.getId())
                    .and("code").is("VIP")), TicketTier.class).block();

            EventSummaryDto summary = new InternalEventController(service).getEventById(created.getId()).block().getBody();

            assertThat(summary.getTicketCategories())
                    .filteredOn(tier -> vip.getId().equals(tier.getId()))
                    .singleElement()
                    .satisfies(tier -> assertThat(tier.getPrice()).isEqualByComparingTo("500.00"));
        }
    }

    @Nested
    @DisplayName("the venue's city")
    class TheCity {

        @Test
        @DisplayName("a city that is not in the reference data is refused, and nothing is written")
        void unknownCity() {
            Map<String, Object> payload = newEventPayload();
            ((Map<String, Object>) payload.get("location")).put("city", "Atlantis");

            assertThatThrownBy(() -> create(payload))
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode()).isEqualTo(ErrorCode.LOCATION_UNKNOWN));
            nothingWritten();
        }

        @Test
        @DisplayName("a city an administrator deactivated is refused")
        void deactivatedCity() {
            Map<String, Object> payload = newEventPayload();
            ((Map<String, Object>) payload.get("location")).put("city", "Old Town");

            assertThatThrownBy(() -> create(payload))
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode()).isEqualTo(ErrorCode.LOCATION_UNKNOWN));
        }

        @Test
        @DisplayName("a real city in the wrong country is refused")
        void wrongCountry() {
            Map<String, Object> payload = newEventPayload();
            ((Map<String, Object>) payload.get("location")).put("country", "Malawi");

            assertThatThrownBy(() -> create(payload))
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode()).isEqualTo(ErrorCode.LOCATION_UNKNOWN));
        }

        @Test
        @DisplayName("a venue name that looks like a pattern is matched as text, not as a regular expression")
        void venueNameIsNotAPattern() {
            create(newEventPayload());
            Map<String, Object> payload = newEventPayload();
            ((Map<String, Object>) payload.get("location")).put("name", ".*");

            Event second = create(payload);

            assertThat(second.getLocationName()).isEqualTo(".*");
            Persistence.assertExactly(template, CatalogCollections.LOCATIONS, 2);
        }
    }

    @Nested
    @DisplayName("an inconsistent event is refused before anything is written")
    class Consistency {

        @Test
        @DisplayName("a banner link that is not http(s) is refused, so no client renders a script link")
        void scriptLinkRefused() {
            Map<String, Object> payload = newEventPayload();
            payload.put("bannerImageUrl", "javascript:alert(document.cookie)");

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("bannerImageUrl");
            nothingWritten();
        }

        @Test
        @DisplayName("a link carrying credentials is refused")
        void credentialsInLinkRefused() {
            Map<String, Object> payload = newEventPayload();
            payload.put("bannerImageUrl", "https://admin:secret@cdn.example.com/jazz.jpg");

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("bannerImageUrl");
        }

        @Test
        @DisplayName("a virtual event needs its link, an in-person one its venue")
        void formatNeedsItsPlace() {
            Map<String, Object> virtual = newEventPayload();
            virtual.put("isVirtual", true);
            virtual.put("location", null);
            Map<String, Object> inPerson = newEventPayload();
            inPerson.put("location", null);

            assertThat(violationsOf(virtual)).extracting(FieldViolation::path).contains("virtualEventUrl");
            assertThat(violationsOf(inPerson)).extracting(FieldViolation::path).contains("location");
        }

        @Test
        @DisplayName("an event cannot end before it starts, or start in the past")
        void datesMakeSense() {
            Map<String, Object> backwards = newEventPayload();
            backwards.put("endDateTime", STARTS.minusSeconds(60));
            Map<String, Object> past = newEventPayload();
            past.put("eventDateTime", NOW.minusSeconds(3600));
            past.put("endDateTime", NOW.plusSeconds(3600));

            assertThat(violationsOf(backwards)).extracting(FieldViolation::path).contains("endDateTime");
            assertThat(violationsOf(past)).extracting(FieldViolation::path).contains("eventDateTime");
        }

        @Test
        @DisplayName("the capacity is the sum of the tiers; a different figure is refused")
        void capacityIsTheTierSum() {
            Map<String, Object> payload = newEventPayload();
            payload.put("totalCapacity", 999);

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("totalCapacity");
        }

        @Test
        @DisplayName("two tiers of one event cannot share a code")
        void tierCodesAreUnique() {
            Map<String, Object> payload = newEventPayload();
            payload.put("ticketTiers", List.of(tier("GENERAL", "A", "10.00", 5, 0), tier("general", "B", "20.00", 5, 1)));

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("ticketTiers[1].code");
        }

        @Test
        @DisplayName("a tier priced in another currency is refused")
        void tierCurrency() {
            Map<String, Object> payload = newEventPayload();
            Map<String, Object> dollars = tier("USD", "Dollars", "10.00", 5, 0);
            dollars.put("currency", "USD");
            payload.put("ticketTiers", List.of(dollars));
            payload.put("totalCapacity", 5);

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("ticketTiers[0].currency");
        }

        @Test
        @DisplayName("a free event cannot carry a paid tier")
        void freeMeansFree() {
            Map<String, Object> payload = newEventPayload();
            payload.put("isFreeEvent", true);

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("isFreeEvent");
        }

        @Test
        @DisplayName("a category that is not an active reference-data category is refused")
        void categoryComesFromReferenceData() {
            Map<String, Object> unknown = newEventPayload();
            unknown.put("categoryId", "CONCERT");
            Map<String, Object> retired = newEventPayload();
            retired.put("categoryId", "RETIRED");

            assertThat(violationsOf(unknown)).extracting(FieldViolation::path).contains("categoryId");
            assertThat(violationsOf(retired)).extracting(FieldViolation::path).contains("categoryId");
        }

        @Test
        @DisplayName("a refund policy outside the stored list is refused rather than failing the database write")
        void refundPolicyIsAKnownValue() {
            Map<String, Object> payload = newEventPayload();
            payload.put("refundPolicy", "whatever the organizer feels like");

            assertThat(violationsOf(payload)).extracting(FieldViolation::path).contains("refundPolicy");
        }

        @Test
        @DisplayName("every platform refund policy code (FLEXIBLE, MODERATE, STRICT, NO_REFUNDS) is accepted")
        void platformRefundPolicyCodesAreAccepted() {
            assertThat(com.pml.catalog.service.EventDetails.REFUND_POLICIES)
                    .contains("FLEXIBLE", "MODERATE", "STRICT", "NO_REFUNDS");
        }

        @Test
        @DisplayName("input validation bounds sizes and refuses negative prices")
        void inputValidation() {
            Map<String, Object> payload = newEventPayload();
            payload.put("title", "x".repeat(201));
            Map<String, Object> negative = tier("NEG", "Negative", "-5.00", 5, 0);
            payload.put("ticketTiers", List.of(negative));

            List<String> paths = VALIDATOR.validate(bind(payload)).stream()
                    .map(violation -> violation.getPropertyPath().toString()).toList();

            assertThat(paths).contains("title", "ticketTiers[0].price");
        }
    }

    @Nested
    @DisplayName("who may create")
    class WhoMayCreate {

        @Test
        @DisplayName("a caller without event:create is refused, and nothing is written")
        void withoutPermission() {
            identity.authorized = false;

            assertThatThrownBy(() -> create(newEventPayload())).isInstanceOf(AccessDeniedException.class);
            nothingWritten();
        }

        @Test
        @DisplayName("a caller with no identity is refused")
        void anonymous() {
            assertThatThrownBy(() -> resolver.createEvent(bind(newEventPayload())).block())
                    .as("the client receives what the platform translator makes of it")
                    .satisfies(refused -> assertThat(new PlatformRefusalTranslator().translate(refused))
                            .hasValueSatisfying(translated ->
                                    assertThat(translated.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_AUTHENTICATED)));
            nothingWritten();
        }

        @Test
        @DisplayName("the event belongs to the organization identity-service names, whatever the payload says")
        void organizationComesFromIdentity() {
            Map<String, Object> payload = newEventPayload();
            payload.put("additionalInfo", Map.of("organizationId", "org-someone-else"));

            Event created = create(payload);

            assertThat(created.getOrganizationId()).isEqualTo(ORGANIZATION);
            assertThat(identity.lastRequest.getUserId()).isEqualTo(ORGANIZER);
        }
    }

    @Test
    @DisplayName("the organizer's name is denormalized from identity onto the event at creation")
    void organizerNameIsDenormalizedAtCreation() {
        Event created = create(newEventPayload());

        assertThat(events.findById(created.getId()).block().getOrganizerName()).isEqualTo("Test Organization");
    }

    @Test
    @DisplayName("an unreachable identity-service does not block event creation: the organizer name falls back to Unknown")
    void creationSurvivesAnIdentityOutageWhenNamingTheOrganizer() {
        identity.organizationNameFails = true;

        Event created = create(newEventPayload());

        Event stored = events.findById(created.getId()).block();
        assertThat(stored).as("the event is written despite the failed name lookup").isNotNull();
        assertThat(stored.getOrganizerName()).as("display data, not a precondition").isEqualTo("Unknown");
    }

    @Test
    @DisplayName("the event's lowest price and tier list follow its tiers when one is repriced or deleted")
    void mirrorFollowsTierWrites() {
        Event created = create(newEventPayload());
        com.pml.catalog.service.impl.TicketTierServiceImpl tiers = new com.pml.catalog.service.impl.TicketTierServiceImpl(
                CatalogWiring.tiers(template), CLOCK, events, new EventWriteGuard(events, identity), template,
                CatalogWiring.tierFactory(), CatalogWiring.mirror(template, CLOCK));
        TicketTier general = template.findOne(Query.query(Criteria.where("eventId").is(created.getId())
                .and("code").is("GENERAL")), TicketTier.class).block();

        tiers.deleteTier(general.getId())
                .contextWrite(signedIn(ORGANIZER))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.of(ORGANIZER, Set.of(ORGANIZATION)))))
                .block();

        Event after = events.findById(created.getId()).block();
        assertThat(after.getLowestTicketPrice()).isEqualByComparingTo("500.00");
        assertThat(after.getTicketCategories()).extracting(Event.EventTicketCategory::getCode).containsExactly("VIP");
        assertThat(after.getTotalCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("a venue is shared: another organization naming the same hall reuses it")
    void venuesAreShared() {
        Event first = create(newEventPayload());
        identity.organization = "65a1b2c3d4e5f60718293a99";

        Event second = create(newEventPayload());

        assertThat(second.getOrganizationId()).isEqualTo("65a1b2c3d4e5f60718293a99");
        assertThat(second.getLocationId()).isEqualTo(first.getLocationId());
    }

    @Test
    @DisplayName("a failure after the venue and event are written rolls all of it back")
    void allOrNothing() {
        TicketTierFactory failsOnTheSecondTier = new TicketTierFactory(10) {
            private int built;

            @Override
            public TicketTier build(Event event, CreateTicketTierInput input, int sortOrder, Instant now) {
                if (++built == 2) {
                    throw new IllegalStateException("simulated failure writing the second tier");
                }
                return super.build(event, input, sortOrder, now);
            }
        };
        resolver = resolverFor(serviceWith(failsOnTheSecondTier));

        assertThatThrownBy(() -> create(newEventPayload())).hasMessageContaining("second tier");
        nothingWritten();
    }

    @Nested
    @DisplayName("what the organizer says about the event (ET-CAT-004)")
    class PageContent {

        private Map<String, Object> withContent() {
            Map<String, Object> input = newEventPayload();
            input.put("tagline", "  One night, two floors  ");
            input.put("ageRestriction", "18+");
            input.put("doorsOpenAt", STARTS.minusSeconds(3600));
            input.put("publishAt", NOW.plusSeconds(86_400));
            input.put("faqs", List.of(Map.of("question", "Is there parking?", "answer", "Yes, behind the hall.")));
            input.put("runningOrder", List.of(Map.of("time", "17:00", "title", "Doors"), Map.of("time", "18:30", "title", "Headliner")));
            input.put("gettingThere", "Bus 12 from Kamwala");
            input.put("parkingInfo", "K20 a car");
            input.put("bagPolicy", "Small bags only");
            input.put("checkoutSettings", Map.of("maxTicketsPerOrder", 4, "collectHolderNames", true, "extraQuestion", "Dietary needs?"));
            input.put("bannerAltText", "A saxophonist on stage");
            input.put("galleryImages", List.of("https://cdn.example.com/1.jpg"));
            return input;
        }

        @Test
        @DisplayName("is stored whole, trimmed, under validators that accept it")
        void stored() {
            Event created = create(withContent());

            Event stored = events.findById(created.getId()).block();
            assertThat(stored.getTagline()).isEqualTo("One night, two floors");
            assertThat(stored.getAgeRestriction()).isEqualTo("18+");
            assertThat(stored.getDoorsOpenAt()).isEqualTo(STARTS.minusSeconds(3600));
            assertThat(stored.getPublishAt()).isEqualTo(NOW.plusSeconds(86_400));
            assertThat(stored.isPublishScheduled()).as("a draft is not scheduled; publishing it is").isFalse();
            assertThat(stored.getFaqs()).singleElement().satisfies(faq -> {
                assertThat(faq.question()).isEqualTo("Is there parking?");
                assertThat(faq.answer()).isEqualTo("Yes, behind the hall.");
            });
            assertThat(stored.getRunningOrder()).extracting(item -> item.time() + " " + item.title())
                    .containsExactly("17:00 Doors", "18:30 Headliner");
            assertThat(stored.getGettingThere()).isEqualTo("Bus 12 from Kamwala");
            assertThat(stored.getParkingInfo()).isEqualTo("K20 a car");
            assertThat(stored.getBagPolicy()).isEqualTo("Small bags only");
            assertThat(stored.getCheckoutSettings().maxTicketsPerOrder()).isEqualTo(4);
            assertThat(stored.getCheckoutSettings().collectHolderNames()).isTrue();
            assertThat(stored.getCheckoutSettings().extraQuestion()).isEqualTo("Dietary needs?");
            assertThat(stored.getBannerAltText()).isEqualTo("A saxophonist on stage");
            assertThat(stored.getGalleryImages()).containsExactly("https://cdn.example.com/1.jpg");
            assertThat(stored.getGrossSales()).isEqualByComparingTo("0");
            assertThat(stored.getCommissionAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("an event with none of it is unchanged: every field reads empty")
        void absent() {
            Event stored = events.findById(create(newEventPayload()).getId()).block();

            assertThat(stored.getTagline()).isNull();
            assertThat(stored.getFaqs()).isNull();
            assertThat(stored.getCheckoutSettings()).isNull();
        }

        @Test
        @DisplayName("refuses an age restriction outside the five, doors after the start, and a go-live time that is past or after the start")
        void refusals() {
            Map<String, Object> bad = withContent();
            bad.put("ageRestriction", "adults only");
            bad.put("doorsOpenAt", STARTS.plusSeconds(60));
            bad.put("publishAt", NOW.minusSeconds(60));

            assertThat(violationsOf(bad)).extracting(FieldViolation::path)
                    .containsExactlyInAnyOrder("ageRestriction", "doorsOpenAt", "publishAt");

            Map<String, Object> late = withContent();
            late.put("publishAt", STARTS);
            assertThat(violationsOf(late)).extracting(FieldViolation::path).containsExactly("publishAt");
        }

        @Test
        @DisplayName("refuses a running-order time that is not HH:mm and a FAQ with no answer, at the input path")
        void refusesMalformedLists() {
            Map<String, Object> bad = withContent();
            bad.put("runningOrder", List.of(Map.of("time", "7pm", "title", "Doors")));
            bad.put("faqs", List.of(Map.of("question", "Q?", "answer", " ")));

            assertThat(violationOf(bad)).isNotEmpty();
        }

        private List<jakarta.validation.ConstraintViolation<CreateEventInput>> violationOf(Map<String, Object> payload) {
            return List.copyOf(VALIDATOR.validate(bind(payload)));
        }

        @Test
        @DisplayName("a duplicate starts with no schedule, no cancellation and zero totals, but keeps the content")
        void duplicateResets() {
            Event original = create(withContent());
            original = events.findById(original.getId()).block();
            original.setPublishScheduled(true);
            original.setCancellationReason("rain");
            original.setGrossSales(new BigDecimal("900.00"));
            original.setCommissionAmount(new BigDecimal("45.00"));
            events.save(original).block();
            Event source = events.findById(original.getId()).block();

            Event copy = service.duplicateEvent(source, "Jazz Night 2", ORGANIZER, ORGANIZATION).block();

            Event stored = events.findById(copy.getId()).block();
            assertThat(stored.getTagline()).isEqualTo("One night, two floors");
            assertThat(stored.getFaqs()).hasSize(1);
            assertThat(stored.getPublishAt()).isNull();
            assertThat(stored.isPublishScheduled()).isFalse();
            assertThat(stored.getCancellationReason()).isNull();
            assertThat(stored.getGrossSales()).isEqualByComparingTo("0");
            assertThat(stored.getCommissionAmount()).isEqualByComparingTo("0");
        }
    }

    // ── harness ─────────────────────────────────────────────────────────────────────────────────

    private static void nothingWritten() {
        Persistence.assertNothingPersisted(template, CatalogCollections.EVENTS);
        Persistence.assertNothingPersisted(template, CatalogCollections.TICKET_TIERS);
        Persistence.assertNothingPersisted(template, CatalogCollections.LOCATIONS);
    }

    private static void seedGeography() {
        List<ReferenceData> rows = new ArrayList<>();
        rows.add(row(ReferenceType.COUNTRY, "ZM", "Zambia", null, null, true, Map.of("dialCode", "+260", "iso3", "ZMB")));
        rows.add(row(ReferenceType.COUNTRY, "MW", "Malawi", null, null, true, Map.of("dialCode", "+265", "iso3", "MWI")));
        rows.add(row(ReferenceType.PROVINCE, "LUS", "Lusaka", ReferenceType.COUNTRY, "ZM", true, Map.of()));
        rows.add(row(ReferenceType.CITY, "LUSAKA", "Lusaka", ReferenceType.PROVINCE, "LUS", true,
                Map.of("latitude", -15.3875, "longitude", 28.3228)));
        rows.add(row(ReferenceType.EVENT_CATEGORY, "MUSIC", "Music", null, null, true, Map.of()));
        rows.add(row(ReferenceType.EVENT_CATEGORY, "RETIRED", "Retired", null, null, false, Map.of()));
        rows.add(row(ReferenceType.CITY, "OLD_TOWN", "Old Town", ReferenceType.PROVINCE, "LUS", false,
                Map.of("latitude", -15.0, "longitude", 28.0)));
        template.insertAll(rows).collectList().block();
    }

    private static ReferenceData row(ReferenceType type, String code, String name, ReferenceType parentType,
                                     String parentCode, boolean active, Map<String, Object> metadata) {
        return ReferenceData.builder()
                .type(type).code(code).name(name).parentType(parentType).parentCode(parentCode)
                .isActive(active).isSystem(true).metadata(new HashMap<>(metadata)).build();
    }

    private EventService serviceWith(TicketTierFactory factory) {
        return new EventServiceImpl(events, CLOCK, new Outbox(template, "catalog_outbox", CLOCK),
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                CatalogWiring.venues(template, CLOCK), factory, CatalogWiring.tiers(template),
                CatalogWiring.mirror(template, CLOCK), CatalogWiring.categories(template), identity);
    }

    private EventMutationResolver resolverFor(EventService eventService) {
        return new EventMutationResolver(eventService, CLOCK, identity, new EventWriteGuard(events, identity),
                Mockito.mock(EventLifecycleProcess.class), Mockito.mock(EventApprovalProcess.class),
                Mockito.mock(com.pml.catalog.workflow.schedule.EventPublishScheduleProcess.class));
    }

    static Context signedIn(String subject, String... roles) {
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("test").header("alg", "none").subject(subject).claim("scope", "openid").build(),
                java.util.Arrays.stream(roles).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList()));
    }

    /** identity-service, answering for the organization this test's organizer belongs to. */
    static final class StubIdentity extends IdentityServiceClient {
        boolean authorized = true;
        boolean organizationNameFails;
        String organization = ORGANIZATION;
        AuthorizationRequest lastRequest;

        StubIdentity() {
            super(InternalServiceWebClients.unauthenticated(WebClient.builder()), "http://identity.invalid");
        }

        @Override
        public Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request) {
            lastRequest = request;
            return Mono.just(authorized
                    ? AuthorizationResult.authorizedAsMember(organization, "OWNER")
                    : AuthorizationResult.denied("requires event:create"));
        }

        @Override
        public Mono<AuthorizationResult> checkEventAccess(String userId, String eventId, String organizationId,
                                                          String permission) {
            return Mono.just(authorized
                    ? AuthorizationResult.authorizedAsMember(ORGANIZATION, "OWNER")
                    : AuthorizationResult.denied("requires " + permission));
        }

        @Override
        public Mono<String> getOrganizationName(String organizationId) {
            return organizationNameFails
                    ? Mono.error(new IllegalStateException("identity-service unreachable"))
                    : Mono.just("Test Organization");
        }
    }
}
