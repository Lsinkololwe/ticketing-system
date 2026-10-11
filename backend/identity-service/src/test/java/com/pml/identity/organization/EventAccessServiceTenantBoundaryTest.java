package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.service.impl.EventAccessServiceImpl;
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
 * ET-PLT-007 Phase 6 · {@code EventAccessServiceImpl.update}/{@code .revoke} each used to re-fetch
 * their grant with a bare {@code accessGrantRepository.findById(id)} — no filter of any kind. The
 * resolver's {@code reads.grantForCaller(accessId)} already proves ownership before either is
 * called, so this is defense in depth: a future caller that skips that read is refused here too.
 *
 * <p>Flat {@code @Test} methods, not {@code @Nested} groups: see F-055 — this project's Surefire
 * setup never executes a {@code @Test} inside a {@code @Nested} class.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("F-001 · an event access grant's update and revoke are reachable only by the organization that owns it, or a platform administrator")
class EventAccessServiceTenantBoundaryTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String GRANT_ID = "grant-kabwe-scanner";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventAccessGrantRepository grants;
    private static EventAccessServiceImpl service;

    private static final TenantScope OUTSIDER = TenantScope.of("user-outsider", Set.of(OTHER_ORG));
    private static final TenantScope ADMIN = TenantScope.platformAdministrator("user-admin", Set.of());

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_event_access_boundary"));
        grants = new ReactiveMongoRepositoryFactory(template).getRepository(EventAccessGrantRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC);
        service = new EventAccessServiceImpl(grants, clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneGrantOwnedByKabwe() {
        template.remove(new Query(), EventAccessGrant.class).block();
        EventAccessGrant grant = EventAccessGrant.builder()
                .id(GRANT_ID)
                .userId("user-scanner-1")
                .eventId("event-kabwe-jazz-night")
                .organizationId(OWNER_ORG)
                .eventRole(EventRole.CHECK_IN)
                .grantedById("user-owner")
                .status(AccessGrantStatus.ACTIVE)
                .build();
        grants.save(grant).block();
    }

    private static <T> Mono<T> as(TenantScope scope, Mono<T> operation) {
        return operation.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)));
    }

    @Test
    @DisplayName("an outsider's update is refused, and the grant is unchanged")
    void outsiderCannotUpdate() {
        assertThat(grants.findById(GRANT_ID).block()).as("reachable by unscoped id").isNotNull();

        assertThatThrownBy(() -> as(OUTSIDER, service.update(GRANT_ID, EventRole.EVENT_ADMIN, null, null)).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.ACCESS_GRANT_UNKNOWN));

        assertThat(grants.findById(GRANT_ID).block().getEventRole()).isEqualTo(EventRole.CHECK_IN);
    }

    @Test
    @DisplayName("an outsider's revoke is refused, and the grant survives active")
    void outsiderCannotRevoke() {
        assertThatThrownBy(() -> as(OUTSIDER, service.revoke(GRANT_ID, "no longer needed", "user-outsider")).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.ACCESS_GRANT_UNKNOWN));

        assertThat(grants.findById(GRANT_ID).block().getStatus()).isEqualTo(AccessGrantStatus.ACTIVE);
    }

    @Test
    @DisplayName("a platform administrator's revoke succeeds across organizations, proving the guard is not merely vacuous")
    void administratorCanRevokeAnyOrganizationsGrant() {
        EventAccessGrant revoked = as(ADMIN, service.revoke(GRANT_ID, "event cancelled", "user-admin")).block();
        assertThat(revoked.getStatus()).isEqualTo(AccessGrantStatus.REVOKED);
    }
}
