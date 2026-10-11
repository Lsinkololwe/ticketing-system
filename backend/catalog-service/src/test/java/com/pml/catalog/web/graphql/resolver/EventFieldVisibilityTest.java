package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.EventCategoryService;
import com.pml.catalog.service.MediaService;
import com.pml.catalog.service.OrganizerProfileService;
import com.pml.catalog.service.TicketTierService;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who reads what, on the fields whose answer depends on the caller: the hidden tiers of an event, a
 * tier's access code, an event's sales totals, and an asset's moderation trail. Field resolvers
 * only — the queries that reach them are tested where they live.
 */
@Tag("L1")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R1/R6/R9/R12 · fields that depend on the caller answer only the owning organization and administrators")
class EventFieldVisibilityTest {

    private static final String OWNER = "65a1b2c3d4e5f60718293a4b";
    private static final String RIVAL = "65a1b2c3d4e5f60718293a4c";

    private static final TenantScope OWNER_SCOPE = TenantScope.of("owner", Set.of(OWNER));
    private static final TenantScope RIVAL_SCOPE = TenantScope.of("rival", Set.of(RIVAL));
    private static final TenantScope ANONYMOUS = TenantScope.denyAll(null);
    private static final TenantScope ADMIN = TenantScope.platformAdministrator("ops", Set.of());

    private TicketTierService tierService;
    private EventComputedFieldResolver computed;
    private TicketTierFieldResolver tierFields;
    private EventContentFieldResolver content;
    private MediaFieldResolver mediaFields;

    private final Event event = Event.builder().id("e1").organizationId(OWNER).organizerId("o")
            .grossSales(new BigDecimal("900.00")).commissionAmount(new BigDecimal("45.00")).build();
    private final TicketTier open = TicketTier.builder().id("t-open").eventId("e1").organizationId(OWNER).build();
    private final TicketTier hidden = TicketTier.builder().id("t-hidden").eventId("e1").organizationId(OWNER)
            .isHidden(true).accessCode("BACKSTAGE").build();

    @BeforeEach
    void wire() {
        tierService = Mockito.mock(TicketTierService.class);
        Mockito.when(tierService.findByEventId("e1", true)).thenReturn(Flux.just(open, hidden));
        Mockito.when(tierService.findByEventId("e1", false)).thenReturn(Flux.just(open));
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
        computed = new EventComputedFieldResolver(Mockito.mock(EventCategoryService.class), tierService, clock);
        tierFields = new TicketTierFieldResolver();
        content = new EventContentFieldResolver(Mockito.mock(OrganizerProfileService.class),
                Mockito.mock(com.pml.catalog.infrastructure.client.IdentityServiceClient.class),
                Mockito.mock(org.springframework.data.mongodb.core.ReactiveMongoTemplate.class));
        mediaFields = new MediaFieldResolver(Mockito.mock(MediaService.class));
    }

    private static DgsDataFetchingEnvironment source(Object source) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(source);
        return dfe;
    }

    private static <T> T under(TenantScope scope, Mono<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope))).block();
    }

    private static Context signedIn(String... roles) {
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").subject("u").claim("scope", "openid").build(),
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    // ── Event.ticketTiers ────────────────────────────────────────────────────

    @Test
    @DisplayName("the owning organization and an administrator see hidden tiers on the event")
    void ownersSeeHiddenTiers() {
        assertThat(under(OWNER_SCOPE, computed.ticketTiers(source(event)))).extracting(TicketTier::getId)
                .containsExactly("t-open", "t-hidden");
        assertThat(under(ADMIN, computed.ticketTiers(source(event)))).hasSize(2);
    }

    @Test
    @DisplayName("everyone else sees the public tiers only: a rival organization, a buyer, an anonymous caller")
    void othersDoNot() {
        assertThat(under(RIVAL_SCOPE, computed.ticketTiers(source(event)))).extracting(TicketTier::getId).containsExactly("t-open");
        assertThat(under(ANONYMOUS, computed.ticketTiers(source(event)))).extracting(TicketTier::getId).containsExactly("t-open");
    }

    @Test
    @DisplayName("with no tenant scope at all, the answer is the public one, not an error and not the hidden tiers")
    void noScopeFailsClosed() {
        List<TicketTier> seen = computed.ticketTiers(source(event)).block();

        assertThat(seen).extracting(TicketTier::getId).containsExactly("t-open");
    }

    // ── TicketTier.accessCode ────────────────────────────────────────────────

    @Test
    @DisplayName("the access code is read back by the owning organization and administrators, and by nobody else")
    void accessCode() {
        assertThat(under(OWNER_SCOPE, tierFields.accessCode(source(hidden)))).isEqualTo("BACKSTAGE");
        assertThat(under(ADMIN, tierFields.accessCode(source(hidden)))).isEqualTo("BACKSTAGE");
        assertThat(under(RIVAL_SCOPE, tierFields.accessCode(source(hidden)))).isNull();
        assertThat(under(ANONYMOUS, tierFields.accessCode(source(hidden)))).isNull();
        assertThat(tierFields.accessCode(source(hidden)).block()).as("no scope").isNull();
        assertThat(under(OWNER_SCOPE, tierFields.accessCode(source(open)))).as("a tier with no code").isNull();
    }

    // ── Event sales totals ───────────────────────────────────────────────────

    @Test
    @DisplayName("gross sales, commission and net are the owner's and the administrators'")
    void salesTotals() {
        assertThat(under(OWNER_SCOPE, content.grossSales(source(event)))).isEqualByComparingTo("900.00");
        assertThat(under(OWNER_SCOPE, content.commissionAmount(source(event)))).isEqualByComparingTo("45.00");
        assertThat(under(OWNER_SCOPE, content.netSales(source(event)))).isEqualByComparingTo("855.00");
        assertThat(under(ADMIN, content.netSales(source(event)))).isEqualByComparingTo("855.00");
    }

    @Test
    @DisplayName("a stranger reads null for each of them, including an anonymous caller and a missing scope")
    void salesTotalsHidden() {
        for (TenantScope scope : List.of(RIVAL_SCOPE, ANONYMOUS)) {
            assertThat(under(scope, content.grossSales(source(event)))).isNull();
            assertThat(under(scope, content.commissionAmount(source(event)))).isNull();
            assertThat(under(scope, content.netSales(source(event)))).isNull();
        }
        assertThat(content.grossSales(source(event)).block()).isNull();
    }

    @Test
    @DisplayName("an event with no totals yet reads zero to its owner, not null")
    void zeroNotNull() {
        Event fresh = Event.builder().id("e2").organizationId(OWNER).grossSales(null).commissionAmount(null).build();

        assertThat(under(OWNER_SCOPE, content.grossSales(source(fresh)))).isEqualByComparingTo("0");
        assertThat(under(OWNER_SCOPE, content.netSales(source(fresh)))).isEqualByComparingTo("0");
    }

    // ── Event.organization, Organization counts ──────────────────────────────

    @Test
    @DisplayName("the organization is a federation reference identity resolves, and an event with none has none")
    void organizationReference() {
        Map<String, Object> reference = content.organization(source(event));

        assertThat(reference).containsEntry("__typename", "Organization").containsEntry("id", OWNER);
        assertThat(content.organization(source(Event.builder().id("x").build()))).isNull();
        assertThat(content.organization(source(Event.builder().id("x").organizationId(" ").build()))).isNull();
    }

    @Test
    @DisplayName("the organization's counts are asked of the key the router sent")
    void organizationCounts() {
        OrganizerProfileService profiles = Mockito.mock(OrganizerProfileService.class);
        Mockito.when(profiles.publishedEventCount(OWNER)).thenReturn(Mono.just(4));
        Mockito.when(profiles.completedEventCount(OWNER)).thenReturn(Mono.just(9));
        EventContentFieldResolver resolver = new EventContentFieldResolver(profiles,
                Mockito.mock(com.pml.catalog.infrastructure.client.IdentityServiceClient.class),
                Mockito.mock(org.springframework.data.mongodb.core.ReactiveMongoTemplate.class));
        Map<String, Object> representation = new HashMap<>(Map.of("__typename", "Organization", "id", OWNER));

        assertThat(resolver.publishedEventCount(source(representation)).block()).isEqualTo(4);
        assertThat(resolver.completedEventCount(source(representation)).block()).isEqualTo(9);
    }

    // ── MediaAsset.moderationLog ─────────────────────────────────────────────

    @Test
    @DisplayName("the moderation trail is an administrator's; an organizer sees the status and the reason, not who")
    void moderationLog() {
        MediaAsset asset = MediaAsset.builder().id("m1")
                .moderationLog(List.of(new MediaAsset.ModerationEntry("REMOVE", "admin-1", "copyright", Instant.EPOCH)))
                .build();

        assertThat(mediaFields.moderationLog(source(asset)).contextWrite(signedIn("ROLE_ADMIN")).block()).hasSize(1);
        assertThat(mediaFields.moderationLog(source(asset)).contextWrite(signedIn("ROLE_SUPER_ADMIN")).block()).hasSize(1);
        assertThat(mediaFields.moderationLog(source(asset)).contextWrite(signedIn("ROLE_ORGANIZER")).block()).isNull();
        assertThat(mediaFields.moderationLog(source(asset)).block()).as("not signed in").isNull();
        assertThat(mediaFields.moderationLog(source(MediaAsset.builder().id("m2").moderationLog(null).build()))
                .contextWrite(signedIn("ROLE_ADMIN")).block()).isNull();
    }
}
