package com.pml.catalog.security;

import org.springframework.web.reactive.function.client.WebClient;
import com.pml.shared.security.InternalServiceWebClients;
import com.pml.shared.security.Permission;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.repository.EventRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each lock refuses on its own, with the other one wide open.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>The claim being tested</h2>
 * Not "the write path refuses when it should" — that would pass with either lock present and
 * would be exactly the test that lets somebody delete one of them. Each case below <b>disables
 * one lock and proves the other still holds</b>:
 *
 * <ul>
 *   <li>identity-service is stubbed to authorise <em>everything</em>, and a caller from another
 *       organization is still refused. That refusal is the repository filter and nothing else.</li>
 *   <li>the caller genuinely belongs to the owning organization, so the filter returns the row,
 *       and identity-service refuses. That refusal is the permission check and nothing else.</li>
 * </ul>
 *
 * <p>Delete either lock and exactly one of those two fails, naming which. A test that only
 * exercised both together would stay green through the removal of either one.
 *
 * <h2>Against a replica set, with a real repository</h2>
 * The filter is {@code findByIdAndOrganizationIdIn}, a derived Mongo query. Only identity-service
 * is stubbed, because it is the thing being switched on and off; the boundary itself runs against
 * the real driver.
 */
@Tag("L2")
@Tag("ET-PLT-007")
    // The class also iterates ids across two organizations and asserts the response is
    // identical for a non-existent id and for another tenant's id. That property belongs to
    // the error contract as much as to the tenant boundary — one decides who may read, the
    // other that the refusal discloses nothing — so the class carries both tags.
@Tag("ET-PLT-005")
@DisplayName("D-20 · the filter holds without the check, and the check holds without the filter")
class EventWriteBothLocksTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String THE_EVENT = "event-owned-by-kabwe";
    private static final String NEVER_ISSUED = "event-that-was-never-issued";
    private static final String CALLER = "user-marketing-contractor";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRepository events;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_both_locks"));
        events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneEvent() {
        template.remove(new Query(), Event.class).block();
        Event event = Event.builder()
                .id(THE_EVENT)
                .title("Kabwe Jazz Nights")
                .organizationId(OWNER_ORG)
                .organizerId("user-" + OWNER_ORG)
                .status(EventStatus.DRAFT)
                .published(false)
                .isActive(true)
                .totalCapacity(4_000)
                .eventDateTime(Instant.parse("2026-12-01T18:00:00Z"))
                .build();
        template.save(event).block();
    }

    @Nested
    @DisplayName("with the permission check answering yes to everything")
    class OnlyTheFilterIsLoadBearing {

        @Test
        @DisplayName("ET-PLT-007 · another organization is refused, and the filter is what refused")
        void theFilterRefusesAlone() {
            assertThat(events.findById(THE_EVENT).block())
                    .as("the event must exist, or this passes against an empty collection")
                    .isNotNull();

            assertThatThrownBy(() -> write(OTHER_ORG, alwaysAuthorised(), Permission.EVENT_EDIT))
                    .as("""
                        identity-service said yes. If this write succeeds, the repository filter \
                        is gone and every organizer can edit every event on the platform.""")
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode())
                            .isEqualTo(ErrorCode.EVENT_UNKNOWN));
        }

        @Test
        @DisplayName("ET-PLT-007 · the refusal is indistinguishable from an id that was never issued")
        void crossTenantLooksLikeNotFound() {
            DomainRefusal onTheirs = refusalFor(OTHER_ORG, THE_EVENT);
            DomainRefusal onNothing = refusalFor(OTHER_ORG, NEVER_ISSUED);

            assertThat(onTheirs.errorCode()).isEqualTo(onNothing.errorCode());
            assertThat(onTheirs.details()).isEqualTo(onNothing.details()).isEmpty();
        }

        @Test
        @DisplayName("ET-PLT-007 · the owning organization still gets through")
        void theOwnerIsNotBlocked() {
            // Closing a boundary that also closes the front door is not a fix.
            assertThat(write(OWNER_ORG, alwaysAuthorised(), Permission.EVENT_EDIT).getId())
                    .isEqualTo(THE_EVENT);
        }
    }

    @Nested
    @DisplayName("with the caller genuinely inside the owning organization")
    class OnlyTheCheckIsLoadBearing {

        @Test
        @DisplayName("ET-PLT-007 · a member without the permission is refused, and the check is what refused")
        void theCheckRefusesAlone() {
            // The MARKETER case, which is the whole business argument for two locks: they belong
            // to the organization, so the filter returns the row and has no further opinion. Only
            // the permission check knows that marketing may run promotions and may not edit events.
            assertThatThrownBy(() ->
                    write(OWNER_ORG, alwaysRefused("MARKETER may not edit events"), Permission.EVENT_EDIT))
                    .as("""
                        the filter passed — this caller really is in the owning organization. If \
                        this write succeeds, the permission check is gone and every member of an \
                        organization has owner-level power over all of its events.""")
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("MARKETER may not edit events");
        }

        @Test
        @DisplayName("ET-PLT-007 · a permission refusal may say why, unlike a cross-tenant one")
        void thePermissionRefusalIsAllowedToBeSpecific() {
            // Deliberately not disguised. By this point the caller has proved membership of the
            // owning organization, so the event's existence is not news and TenantBoundary has
            // nothing left to conceal. Telling them which permission they lack is what lets them
            // ask the right colleague rather than file a bug about a vanished event.
            assertThatThrownBy(() ->
                    write(OWNER_ORG, alwaysRefused("requires event:publish"), Permission.EVENT_PUBLISH))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("event:publish");
        }
    }

    @Test
    @DisplayName("ET-PLT-007 · a platform administrator is subject to the permission check too")
    void administratorsAreNotExempt() {
        // The filter lets an administrator past by design. If the check were skipped for them as
        // well, the two locks would collapse into none for exactly the accounts that can do the
        // most damage.
        assertThatThrownBy(() -> writeAs(
                TenantScope.platformAdministrator("ops", Set.of()),
                alwaysRefused("suspended administrator"),
                THE_EVENT, Permission.EVENT_DELETE))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── harness ─────────────────────────────────────────────────────────────

    private static Event write(String callerOrganization, IdentityServiceClient identity, Permission permission) {
        return writeAs(TenantScope.of(CALLER, Set.of(callerOrganization)), identity, THE_EVENT, permission);
    }

    private static Event writeAs(
            TenantScope scope, IdentityServiceClient identity, String eventId, Permission permission) {

        return new EventWriteGuard(events, identity)
                .forWrite(eventId, permission)
                .contextWrite(AUTHENTICATED)
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    private static DomainRefusal refusalFor(String callerOrganization, String eventId) {
        try {
            writeAs(TenantScope.of(CALLER, Set.of(callerOrganization)),
                    alwaysAuthorised(), eventId, Permission.EVENT_EDIT);
        } catch (DomainRefusal refused) {
            return refused;
        }
        throw new AssertionError("expected a refusal for event " + eventId);
    }

    /** A JWT carrying the subject {@code EventWriteGuard} reads for the permission call. */
    private static final Context AUTHENTICATED = ReactiveSecurityContextHolder.withAuthentication(
            new JwtAuthenticationToken(Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject(CALLER)
                    .claim("scope", "openid")
                    .build(), List.of()));

    /** identity-service with the door held open — leaves only the repository filter standing. */
    private static IdentityServiceClient alwaysAuthorised() {
        return new StubIdentity(true, null);
    }

    private static IdentityServiceClient alwaysRefused(String reason) {
        return new StubIdentity(false, reason);
    }

    /**
     * The only stubbed collaborator. Subclassed rather than mocked so the guard runs its real
     * code path — {@code isAuthorized()}, the error it raises and the message it carries are all
     * production behaviour.
     */
    private static final class StubIdentity extends IdentityServiceClient {
        private final boolean authorized;
        private final String reason;

        private StubIdentity(boolean authorized, String reason) {
            super(InternalServiceWebClients.unauthenticated(WebClient.builder()), "http://identity.invalid");
            this.authorized = authorized;
            this.reason = reason;
        }

        @Override
        public Mono<AuthorizationResult> checkEventAccess(
                String userId, String eventId, String organizationId, String permission) {
            return Mono.just(AuthorizationResult.builder()
                    .authorized(authorized)
                    .reason(reason)
                    .build());
        }
    }
}
