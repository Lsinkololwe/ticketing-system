package com.pml.catalog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.EventDiscovery;
import com.pml.catalog.service.EventRanking;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.EventDiscoveryFilterInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.security.ServiceSecurity;
import com.pml.shared.security.publicop.PublicGraphQlFilter;
import com.pml.shared.security.publicop.PublicOperationPolicy;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-CAT-004-R13 · a visitor with no token, against a real MongoDB and a real Redis.
 *
 * <p>Two halves. The service layer, seeded with an event in every state, answers the anonymous scope with the
 * published one and nothing else. The security chain, with the catalog's own allowlist, admits the buyer's reads
 * and refuses everything else, rate limited.
 */
@Tag("L2")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R13 · anonymous browsing returns only published events and admits only the allowlist")
class AnonymousDiscoveryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TenantScope ANONYMOUS = TenantScope.denyAll(null);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRepository events;
    private static EventDiscovery discovery;
    private static EventRanking ranking;
    private static EventServiceImpl service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_anonymous_discovery");
        events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
        discovery = new EventDiscovery(template, CLOCK, 1000);
        ranking = new EventRanking(template, CLOCK);
        service = new EventServiceImpl(events, CLOCK, new com.pml.shared.event.Outbox(template, "catalog_outbox", CLOCK),
                org.springframework.transaction.reactive.TransactionalOperator.create(
                        new org.springframework.data.mongodb.ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                CatalogWiring.venues(template, CLOCK), CatalogWiring.tierFactory(), CatalogWiring.tiers(template),
                CatalogWiring.mirror(template, CLOCK), CatalogWiring.categories(template));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static Event save(String id, EventStatus status, boolean published, boolean active, boolean deleted) {
        Instant starts = NOW.plusSeconds(10 * 86_400L);
        return template.save(Event.builder().id(id).title("Event " + id).organizationId("org-1").organizerId("user-1")
                .organizerName("Kabwe Collective").organizerEmail("private@organizer.example").organizerPhone("+260970000000")
                .rejectionReason(status == EventStatus.REJECTED ? "capacity unverified" : null)
                .status(status).published(published).isActive(active).isDeleted(deleted).soldTickets(5).totalCapacity(500)
                .eventDateTime(starts).endDateTime(starts.plusSeconds(4 * 3600)).createdAt(NOW).build()).block();
    }

    @BeforeEach
    void seedOneOfEveryState() {
        template.remove(new Query(), Event.class).block();
        save("published", EventStatus.PUBLISHED, true, true, false);
        save("draft", EventStatus.DRAFT, false, true, false);
        save("pending", EventStatus.PENDING_APPROVAL, false, true, false);
        save("changes", EventStatus.CHANGES_REQUESTED, false, true, false);
        save("approved", EventStatus.APPROVED, false, true, false);
        save("rejected", EventStatus.REJECTED, false, true, false);
        save("cancelled", EventStatus.CANCELLED, false, true, false);
        save("completed", EventStatus.COMPLETED, true, true, false);
        save("deleted", EventStatus.PUBLISHED, true, false, true);
        // a status that says published while the flag says it is not, and the reverse: both are non-public
        save("flag-off", EventStatus.PUBLISHED, false, true, false);
        save("status-off", EventStatus.APPROVED, true, true, false);
    }

    private static <T> T anonymously(Mono<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(ANONYMOUS))).block();
    }

    private static EventDiscoveryFilterInput everything() {
        return new EventDiscoveryFilterInput(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("the service layer, with no principal")
    class ServiceLayer {

        @Test
        @DisplayName("every state is really in the collection, so the filters below mean something")
        void fixtureIsReal() {
            assertThat(template.findAll(Event.class).collectList().block()).hasSize(11);
        }

        @Test
        @DisplayName("discoverEvents returns the published event and no draft, pending, approved, rejected, cancelled or deleted one")
        void discover() {
            var page = anonymously(discovery.find(everything(), 50, null));
            assertThat(page.events()).extracting(Event::getId).containsExactly("published");
        }

        @Test
        @DisplayName("trendingEvents returns the published event only")
        void trending() {
            assertThat(anonymously(ranking.trending(10).collectList())).extracting(Event::getId).containsExactly("published");
        }

        @Test
        @DisplayName("event(id) answers the published event, and null for every other state")
        void single() {
            assertThat(anonymously(service.findVisibleById("published"))).isNotNull();
            for (String id : List.of("draft", "pending", "changes", "approved", "rejected", "cancelled", "completed", "deleted", "flag-off", "status-off", "no-such-event")) {
                assertThat(anonymously(service.findVisibleById(id))).as(id).isNull();
            }
        }

        @Test
        @DisplayName("a signed-in caller still reads a completed event the public no longer sees")
        void signedInKeepsTheBroaderView() {
            assertThat(anonymously(service.findVisibleById("completed"))).isNull();
            TenantScope customer = TenantScope.of("user-9", java.util.Set.of());
            assertThat(service.findVisibleById("completed")
                    .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(customer))).block()).isNotNull();
        }

        @Test
        @DisplayName("the contact data is on the document, which is why the schema gates it with @auth")
        void contactDataExistsOnTheDocument() {
            Event stored = events.findById("published").block();
            assertThat(stored.getOrganizerEmail()).isEqualTo("private@organizer.example");
        }
    }

    @Nested
    @DisplayName("the security chain, with the catalog's allowlist and a real Redis")
    class Chain {

        private static final int LIMIT = 4;
        private static AnnotationConfigApplicationContext context;
        private static WebTestClient web;

        @Configuration
        @EnableWebFlux
        @EnableWebFluxSecurity
        static class Wiring {

            @Bean
            ServiceSecurity serviceSecurity() {
                return new ServiceSecurity("http://localhost:1/realms/none", "", "myticketzm-catalog-service", "", List.of());
            }

            @Bean
            SecurityWebFilterChain chain(ServerHttpSecurity http, ServiceSecurity security) {
                return security.securityWebFilterChain(http);
            }

            @Bean
            PublicGraphQlFilter filter() {
                LettuceConnectionFactory factory = new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(RedisNode.host(), RedisNode.port()));
                factory.afterPropertiesSet();
                ReactiveStringRedisTemplate redis = new ReactiveStringRedisTemplate(factory);
                redis.execute(new DefaultRedisScript<>("return 1", Long.class), List.of("warm"), List.of()).blockLast();
                PublicOperationPolicy policy = PublicOperationPolicy.of("catalog-it-" + UUID.randomUUID(),
                                PublicDiscoveryConfig.PUBLIC_ROOT_FIELDS)
                        .withEntities(PublicDiscoveryConfig.PUBLIC_ENTITY_FIELDS).withLimit(LIMIT, Duration.ofMinutes(1));
                return new PublicGraphQlFilter(policy, redis, JSON, null,
                        com.pml.shared.security.publicop.TrustedProxies.parse("10.0.0.0/8"));
            }

            @Bean
            org.springframework.web.server.WebFilter routerPeer() {
                return new com.pml.shared.testing.RouterPeerFilter();
            }

            @Bean
            RouterFunction<ServerResponse> graphql() {
                return RouterFunctions.route().POST("/graphql", request ->
                        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue("{\"data\":{\"trendingEvents\":[]}}")).build();
            }
        }

        @BeforeAll
        static void start() {
            context = new AnnotationConfigApplicationContext(Wiring.class);
            web = WebTestClient.bindToApplicationContext(context).build();
        }

        @AfterAll
        static void stop() {
            context.close();
        }

        private WebTestClient.ResponseSpec post(String query, String variables, String address) {
            String body = "{\"query\":" + JSON.valueToTree(query) + (variables == null ? "" : ",\"variables\":" + variables) + "}";
            return web.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", address)
                    .bodyValue(body).exchange();
        }

        @Test
        @DisplayName("trending, discovery, one event and the filter lists are served without a token")
        void buyerReads() {
            post("{ trendingEvents(first: 3) { id } }", null, "198.51.100.10").expectStatus().isOk();
            post("query($f: EventDiscoveryFilterInput!) { discoverEvents(filter: $f) { edges { node { id } } } }", "{\"f\":{}}", "198.51.100.10").expectStatus().isOk();
            post("query($id: ID!) { event(id: $id) { id title } }", "{\"id\":\"e1\"}", "198.51.100.10").expectStatus().isOk();
            post("{ categories { id } provinces { id } cities { id } citiesWithEvents { id } }", null, "198.51.100.10").expectStatus().isOk();
            post("query($t: ReferenceType!) { referenceData(type: $t) { code name metadata } }", "{\"t\":\"COUNTRY\"}", "198.51.100.12").expectStatus().isOk();
            post("query($t: ReferenceType!, $p: String!) { referenceDataByParent(type: $t, parentCode: $p) { code } }", "{\"t\":\"MUSIC_GENRE\",\"p\":\"MUSIC\"}", "198.51.100.12").expectStatus().isOk();
        }

        @Test
        @DisplayName("introspection, a mutation, a mixed selection, a personal query, an admin query and a batch are 401")
        void everythingElse() {
            for (String query : new String[] {
                    "{ __schema { types { name } } }",
                    "mutation { unlockTierWithAccessCode(eventId: \"1\", accessCode: \"x\") { id } }",
                    "{ trendingEvents { id } myEvents { totalElements } }",
                    "{ recommendedEvents { reason } }",
                    "{ pendingApprovalEvents { totalElements } }",
                    "{ referenceTypes { type } }",
                    "{ referenceItem(type: BANK, code: \"X\") { code } }",
                    "{ referenceDataAll(type: BANK) { totalElements } }",
                    "{ _service { sdl } }" }) {
                post(query, null, "198.51.100.11").expectStatus().isUnauthorized();
            }
            web.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("[{\"query\":\"{ trendingEvents { id } }\"},{\"query\":\"{ trendingEvents { id } }\"}]").exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("past the limit a client address gets 429 with Retry-After; another address is unaffected")
        void rateLimit() {
            String caller = "203.0.113." + (int) (Math.random() * 200 + 20);
            for (int i = 0; i < LIMIT; i++) {
                post("{ trendingEvents { id } }", null, caller).expectStatus().isOk();
            }
            post("{ trendingEvents { id } }", null, caller).expectStatus().isEqualTo(429).expectHeader().valueEquals("Retry-After", "60");
            post("{ trendingEvents { id } }", null, "203.0.113.251").expectStatus().isOk();
        }

        @Test
        @DisplayName("a token the service cannot validate is refused, never treated as anonymous")
        void badTokenIsNotAnonymous() {
            web.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer garbage")
                    .bodyValue(Map.of("query", "{ trendingEvents { id } }")).exchange().expectStatus().isUnauthorized();
        }
    }
}
