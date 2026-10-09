package com.pml.catalog.security;

import com.pml.catalog.testing.CatalogWiring;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.impl.EventLifecycleServiceImpl;
import com.pml.catalog.service.impl.TicketTierServiceImpl;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.dto.authorization.AuthorizationResult;
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
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Who can see and add ticket tiers, and read an event's status history, against a MongoDB replica
 * set with the real derived queries. Draft events and hidden tiers belong to their organization; the
 * public sees published events' visible tiers only.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("Ticket tiers and event history are visible only to those entitled to them")
class CatalogTierVisibilityTest {

    private static final String OWNER_ORG = "org-owner";
    private static final String OTHER_ORG = "org-other";
    private static final String PUBLISHED = "event-published";
    private static final String DRAFT = "event-draft";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketTierRepository tiers;
    private static EventRepository events;

    private TicketTierServiceImpl tierService;
    private EventLifecycleServiceImpl lifecycle;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_tier_visibility"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        tiers = factory.getRepository(TicketTierRepository.class);
        events = factory.getRepository(EventRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), TicketTier.class).block();
        template.remove(new Query(), Event.class).block();
        events.save(event(PUBLISHED, true, EventStatus.PUBLISHED)).block();
        events.save(event(DRAFT, false, EventStatus.DRAFT)).block();
        tiers.save(tier("tier-public", PUBLISHED, false)).block();
        tiers.save(tier("tier-hidden", PUBLISHED, true)).block();
        tiers.save(tier("tier-draft", DRAFT, false)).block();

        IdentityServiceClient identity = Mockito.mock(IdentityServiceClient.class);
        when(identity.checkEventAccess(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(AuthorizationResult.authorizedAsOwner(OWNER_ORG)));
        tierService = new TicketTierServiceImpl(tiers, Clock.fixed(Instant.parse("2026-09-18T09:00:00Z"), ZoneOffset.UTC),
                events, new EventWriteGuard(events, identity), template, CatalogWiring.tierFactory(),
                CatalogWiring.mirror(template, Clock.fixed(Instant.parse("2026-09-18T09:00:00Z"), ZoneOffset.UTC)));
        lifecycle = new EventLifecycleServiceImpl(events, new ReactiveMongoRepositoryFactory(template)
                .getRepository(ApprovalTimelineRepository.class));
    }

    @Test
    @DisplayName("The public sees a published event's visible tiers, and nothing of hidden tiers or drafts")
    void thePublicSeesOnlyPublishedVisibleTiers() {
        TenantScope anonymous = TenantScope.denyAll(null);

        assertThat(as(anonymous, tierService.findVisibleToCaller("tier-public")).block()).isNotNull();
        assertThat(as(anonymous, tierService.findVisibleToCaller("tier-hidden")).block()).isNull();
        assertThat(as(anonymous, tierService.findVisibleToCaller("tier-draft")).block()).isNull();
        assertThat(ids(as(anonymous, tierService.findForCaller(PUBLISHED, false)))).containsExactly("tier-public");
        assertThat(ids(as(anonymous, tierService.findForCaller(PUBLISHED, true)))).as("hidden tiers need ownership").isEmpty();
        assertThat(ids(as(anonymous, tierService.findForCaller(DRAFT, false)))).isEmpty();
        assertThat(ids(as(anonymous, tierService.findAvailableForPurchase(DRAFT)))).isEmpty();
        assertThat(ids(as(anonymous, tierService.findAvailableForPurchase(PUBLISHED)))).containsExactly("tier-public");
    }

    @Test
    @DisplayName("Another organization sees no more than the public")
    void anotherOrganizationSeesOnlyWhatThePublicSees() {
        TenantScope outsider = TenantScope.of("user-outsider", Set.of(OTHER_ORG));

        assertThat(as(outsider, tierService.findVisibleToCaller("tier-hidden")).block()).isNull();
        assertThat(as(outsider, tierService.findVisibleToCaller("tier-draft")).block()).isNull();
        assertThat(ids(as(outsider, tierService.findForCaller(PUBLISHED, true)))).isEmpty();
        assertThat(ids(as(outsider, tierService.findForCaller(DRAFT, false)))).isEmpty();
    }

    @Test
    @DisplayName("The owning organization sees its hidden tiers and its drafts")
    void theOwnerSeesEverything() {
        TenantScope owner = TenantScope.of("user-owner", Set.of(OWNER_ORG));

        assertThat(as(owner, tierService.findVisibleToCaller("tier-hidden")).block()).isNotNull();
        assertThat(as(owner, tierService.findVisibleToCaller("tier-draft")).block()).isNotNull();
        assertThat(ids(as(owner, tierService.findForCaller(PUBLISHED, true)))).containsExactlyInAnyOrder("tier-public", "tier-hidden");
        assertThat(ids(as(owner, tierService.findForCaller(DRAFT, false)))).containsExactly("tier-draft");
    }

    @Test
    @DisplayName("Adding a tier to another organization's event is refused as an unknown event, and writes nothing")
    void addingATierToSomeoneElsesEventIsRefused() {
        TenantScope outsider = TenantScope.of("user-outsider", Set.of(OTHER_ORG));
        assertThat(events.findById(PUBLISHED).block()).as("reachable without the guard").isNotNull();

        assertThatThrownBy(() -> as(outsider, tierService.createTier(PUBLISHED, newTier("VIP"))).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refused -> assertThat(refused.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN));
        assertThat(tiers.findByEventIdAndCode(PUBLISHED, "VIP").block()).isNull();
    }

    @Test
    @DisplayName("The owning organization adds a tier, which takes the event's organization")
    void theOwnerAddsATier() {
        TenantScope owner = TenantScope.of("user-owner", Set.of(OWNER_ORG));

        TicketTier created = as(owner, tierService.createTier(PUBLISHED, newTier("VIP"))).block();

        assertThat(created.getOrganizationId()).isEqualTo(OWNER_ORG);
    }

    @Test
    @DisplayName("An event's status history is refused to another organization and served to its own")
    void eventHistoryIsScoped() {
        TenantScope outsider = TenantScope.of("user-outsider", Set.of(OTHER_ORG));
        TenantScope owner = TenantScope.of("user-owner", Set.of(OWNER_ORG));

        assertThatThrownBy(() -> as(outsider, lifecycle.getEventLifecycle(DRAFT)).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refused -> assertThat(refused.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN));
        assertThatThrownBy(() -> as(outsider, lifecycle.getAllowedStatusTransitions(DRAFT)).block())
                .isInstanceOf(DomainRefusal.class);
        assertThat(as(owner, lifecycle.getEventLifecycle(DRAFT)).block()).isNotNull();
        assertThat(as(owner, lifecycle.getAllowedStatusTransitions(DRAFT)).block()).isNotEmpty();
    }

    private static Event event(String id, boolean published, EventStatus status) {
        Event event = Event.builder().id(id).title("Event " + id).organizationId(OWNER_ORG).status(status).build();
        event.setPublished(published);
        event.setActive(true);
        return event;
    }

    private static TicketTier tier(String id, String eventId, boolean hidden) {
        TicketTier tier = new TicketTier();
        tier.setId(id);
        tier.setEventId(eventId);
        tier.setOrganizationId(OWNER_ORG);
        tier.setCode(id.toUpperCase());
        tier.setName(id);
        tier.setPrice(new BigDecimal("100.00"));
        tier.setQuantity(10);
        tier.setAvailableQuantity(10);
        tier.setActive(true);
        tier.setHidden(hidden);
        return tier;
    }

    private static CreateTicketTierInput newTier(String code) {
        return new CreateTicketTierInput(code, "VIP", null, new BigDecimal("300.00"), "ZMW", 5, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static List<String> ids(Flux<TicketTier> found) {
        return found.map(TicketTier::getId).collectList().block();
    }

    private static <T> Mono<T> as(TenantScope scope, Mono<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(scope)));
    }

    private static <T> Flux<T> as(TenantScope scope, Flux<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(scope)));
    }

    private static JwtAuthenticationToken auth(TenantScope scope) {
        String subject = scope.subject() != null ? scope.subject() : "anonymous";
        return new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg", "none").subject(subject).build(), List.of());
    }
}
