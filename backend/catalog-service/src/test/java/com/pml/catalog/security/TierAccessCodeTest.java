package com.pml.catalog.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.TierAccessService;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A hidden tier opens with its access code, and nothing a buyer can observe tells a wrong code from a
 * tier that does not exist. Real MongoDB for the tiers, real Redis for the guess counter.
 */
@Tag("L2")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R6 · an access code opens a hidden tier, a wrong one reveals nothing, and guessing is rationed")
class TierAccessCodeTest {

    private static final String ORG = "65a1b2c3d4e5f60718293a4b";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketTierRepository tiers;
    private static EventRepository events;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;

    private TierAccessService access;
    private String eventId;
    private String user;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_tier_access"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        tiers = factory.getRepository(TicketTierRepository.class);
        events = factory.getRepository(EventRepository.class);
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), TicketTier.class).block();
        template.remove(new Query(), Event.class).block();
        // A fresh user and event per test, so the counter in the shared Redis starts at zero.
        user = "user-" + UUID.randomUUID();
        eventId = events.save(event(true)).block().getId();
        tiers.save(tier(eventId, "GA", false, true, null)).block();
        tiers.save(tier(eventId, "BACKSTAGE", true, true, "Backstage-24")).block();
        tiers.save(tier(eventId, "OLD", true, false, "OLD-CODE")).block();
        access = new TierAccessService(tiers, events, redis);
    }

    private static Event event(boolean published) {
        return Event.builder().organizationId(ORG).organizerId("organizer").title("Jazz")
                .status(published ? EventStatus.PUBLISHED : EventStatus.DRAFT).published(published).isActive(true)
                .eventDateTime(Instant.parse("2026-12-01T18:00:00Z")).endDateTime(Instant.parse("2026-12-01T23:00:00Z"))
                .totalCapacity(10).createdAt(Instant.parse("2026-10-01T10:00:00Z")).build();
    }

    private static TicketTier tier(String eventId, String code, boolean hidden, boolean active, String accessCode) {
        return TicketTier.builder().eventId(eventId).organizationId(ORG).code(code).name(code)
                .price(new BigDecimal("100.00")).quantity(10).availableQuantity(10).isActive(active).isHidden(hidden)
                .accessCode(accessCode).build();
    }

    private DomainRefusal refused(String code) {
        return refused(eventId, code);
    }

    private DomainRefusal refused(String event, String code) {
        try {
            access.unlock(event, code, user).block();
        } catch (DomainRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    @DisplayName("the right code, in either case, returns the hidden tier")
    void rightCode() {
        assertThat(access.unlock(eventId, "backstage-24", user).block().getCode()).isEqualTo("BACKSTAGE");
        assertThat(access.unlock(eventId, "  BACKSTAGE-24 ", user).block().getCode()).isEqualTo("BACKSTAGE");
    }

    @Test
    @DisplayName("a wrong code, an unknown event, a draft event and a deactivated tier all answer TIER_UNKNOWN alike")
    void indistinguishable() {
        String draft = events.save(event(false)).block().getId();
        tiers.save(tier(draft, "SECRET", true, true, "DRAFT-CODE")).block();

        DomainRefusal wrong = refused("nope");
        DomainRefusal unknownEvent = refused("65a1b2c3d4e5f60718293aff", "Backstage-24");
        DomainRefusal draftEvent = refused(draft, "DRAFT-CODE");
        DomainRefusal deactivated = refused("OLD-CODE");

        assertThat(List.of(wrong, unknownEvent, draftEvent, deactivated))
                .allSatisfy(refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN));
        assertThat(List.of(unknownEvent, draftEvent, deactivated)).extracting(Throwable::getMessage)
                .containsOnly(wrong.getMessage());
    }

    @Test
    @DisplayName("a code never opens a visible tier, and the visible tier's own code is no key")
    void visibleTiersAreNotUnlockable() {
        tiers.save(tier(eventId, "PUBLIC_CODED", false, true, "PUB-CODE")).block();

        assertThat(refused("PUB-CODE").errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN);
    }

    @Test
    @DisplayName("a blank or overlong code is a validation error, and does not count as a guess")
    void validation() {
        assertThat(refused(" ").errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refused("x".repeat(65)).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        for (int i = 0; i < 5; i++) {
            assertThat(refused("wrong-" + i).errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN);
        }
    }

    @Test
    @DisplayName("five wrong codes are answered, the sixth is rationed, and even the right code waits")
    void rationing() {
        for (int i = 0; i < 5; i++) {
            assertThat(refused("wrong-" + i).errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN);
        }

        DomainRefusal sixth = refused("wrong-6");
        DomainRefusal rightButLate = refused("Backstage-24");

        assertThat(sixth.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(sixth.details()).containsKey("retryAfterSeconds");
        assertThat((Long) sixth.details().get("retryAfterSeconds")).isBetween(1L, 900L);
        assertThat(rightButLate.errorCode()).as("a limit that a lucky guess escapes is not a limit")
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("the limit is per buyer and per event: another buyer, and another event, are unaffected")
    void perBuyerPerEvent() {
        for (int i = 0; i < 6; i++) {
            refused("wrong-" + i);
        }
        String otherEvent = events.save(event(true)).block().getId();
        tiers.save(tier(otherEvent, "VIP", true, true, "VIP-1")).block();

        assertThat(access.unlock(otherEvent, "VIP-1", user).block().getCode()).as("same buyer, other event").isEqualTo("VIP");
        assertThat(access.unlock(eventId, "Backstage-24", "someone-else-" + UUID.randomUUID()).block().getCode())
                .as("same event, other buyer").isEqualTo("BACKSTAGE");
    }

    @Test
    @DisplayName("a correct code clears the count, so the buyer starts again")
    void successClears() {
        for (int i = 0; i < 4; i++) {
            refused("wrong-" + i);
        }
        assertThat(access.unlock(eventId, "Backstage-24", user).block()).isNotNull();

        for (int i = 0; i < 5; i++) {
            assertThat(refused("again-" + i).errorCode()).isEqualTo(ErrorCode.TIER_UNKNOWN);
        }
    }

    @Test
    @DisplayName("the count expires: a key with no TTL would ration a buyer for ever")
    void countHasATtl() {
        refused("wrong");

        Long seconds = redis.getExpire(TierAccessService.KEY_PREFIX + user + ":" + eventId)
                .map(java.time.Duration::toSeconds).block();

        assertThat(seconds).isBetween(1L, 900L);
    }

    @Test
    @DisplayName("parallel guesses cannot slip past the limit")
    void parallelGuesses() {
        List<ErrorCode> outcomes = Flux.range(0, 20)
                .flatMap(i -> access.unlock(eventId, "guess-" + i, user)
                        .thenReturn(ErrorCode.INTERNAL_ERROR)
                        .onErrorResume(DomainRefusal.class, refusal -> Mono.just(refusal.errorCode())), 20)
                .collectList().block();

        assertThat(outcomes).filteredOn(code -> code == ErrorCode.TIER_UNKNOWN).hasSize(5);
        assertThat(outcomes).filteredOn(code -> code == ErrorCode.RATE_LIMIT_EXCEEDED).hasSize(15);
    }

    @Test
    @DisplayName("booking's check: the right code opens exactly that tier, and a wrong code, a visible tier and an unknown tier do not")
    void opens() {
        TicketTier backstage = tiers.findByEventIdAndCode(eventId, "BACKSTAGE").block();
        TicketTier ga = tiers.findByEventIdAndCode(eventId, "GA").block();

        assertThat(access.opens(backstage.getId(), "backstage-24").block()).isTrue();
        assertThat(access.opens(backstage.getId(), "nope").block()).isFalse();
        assertThat(access.opens(backstage.getId(), " ").block()).isFalse();
        assertThat(access.opens(ga.getId(), "anything").block()).isFalse();
        assertThat(access.opens("65a1b2c3d4e5f60718293aff", "backstage-24").block()).isFalse();
    }
}
