package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.authorization.Decision;
import com.pml.identity.authorization.DecisionContextLoader;
import com.pml.identity.authorization.DecisionStep;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.identity.service.impl.PermissionResolutionServiceImpl;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.Permission;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
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

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.pml.shared.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The resolution order against a real MongoDB replica set with the real repositories: every row of
 * the decision table asserts the outcome and the step that decided it, the set a person holds is
 * the set the checks would answer one by one, and the access-control cases an attacker would try
 * (another organization's grant, an expired grant, a tampered stored code, a removed member) are
 * refused at the right step.
 */
@Tag("L2")
@Tag("ET-PLT-013")
@Tag("ET-ORG-003")
@DisplayName("Permission decisions report their deciding step, and the effective set equals the checks")
class PermissionDecisionTableTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final String ORG = "org-a";
    private static final String OTHER_ORG = "org-b";
    private static final String EVENT = "event-1";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static UserRepository users;
    private static OrganizationRepository organizations;
    private static OrganizationMemberRepository members;
    private static EventAccessGrantRepository grants;

    private PermissionResolutionService resolution;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_decision_table"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        users = factory.getRepository(UserRepository.class);
        organizations = factory.getRepository(OrganizationRepository.class);
        members = factory.getRepository(OrganizationMemberRepository.class);
        grants = factory.getRepository(EventAccessGrantRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        for (Class<?> type : List.of(User.class, Organization.class, OrganizationMember.class, EventAccessGrant.class)) {
            template.remove(new Query(), type).block();
        }
        organization(ORG, OrganizationStatus.ACTIVE);
        organization(OTHER_ORG, OrganizationStatus.ACTIVE);
        var clock = TestClock.frozenAt(NOW);
        resolution = new PermissionResolutionServiceImpl(
                new DecisionContextLoader(users, members, organizations, grants, clock), members, grants, clock);
    }

    // ---- decision table: outcome and deciding step ----

    @Test
    @DisplayName("A platform ADMIN is allowed everywhere at the platform step, FINANCE only its set, and neither needs membership")
    void platformRoleDecidesFirst() {
        user("staff-admin", UserType.ADMIN);
        user("staff-finance", UserType.FINANCE);

        assertStep(org("staff-admin", ORGANIZATION_EDIT), DecisionStep.PLATFORM_ROLE, true);
        assertStep(org("staff-finance", FINANCIAL_VIEW), DecisionStep.PLATFORM_ROLE, true);
        assertStep(org("staff-finance", EVENT_EDIT), DecisionStep.NOT_A_MEMBER, false);
        assertStep(org("staff-admin", PLATFORM_CONFIGURE), DecisionStep.NOT_A_MEMBER, false);
    }

    @Test
    @DisplayName("ORGANIZER and CUSTOMER roles grant nothing at the platform step")
    void organizerAndCustomerGrantNothing() {
        user("organizer", UserType.ORGANIZER);
        user("customer", UserType.CUSTOMER);

        assertStep(org("organizer", EVENT_VIEW), DecisionStep.NOT_A_MEMBER, false);
        assertStep(org("customer", EVENT_VIEW), DecisionStep.NOT_A_MEMBER, false);
    }

    @Test
    @DisplayName("Members are decided by role, custom addition or denial, and a non-member or removed member is refused")
    void membershipSteps() {
        member("owner", OrganizationRole.OWNER, MemberStatus.ACTIVE);
        member("contributor", OrganizationRole.CONTRIBUTOR, MemberStatus.ACTIVE);
        member("removed", OrganizationRole.MANAGER, MemberStatus.REMOVED);
        OrganizationMember custom = member("custom", OrganizationRole.CONTRIBUTOR, MemberStatus.ACTIVE);
        custom.setCustomPermissions(Set.of("analytics:view"));
        members.save(custom).block();
        OrganizationMember denied = member("denied", OrganizationRole.MANAGER, MemberStatus.ACTIVE);
        denied.setDeniedPermissions(Set.of("event:publish"));
        members.save(denied).block();

        assertStep(org("owner", ORGANIZATION_BILLING), DecisionStep.MEMBER_ROLE, true);
        assertStep(org("custom", ANALYTICS_VIEW), DecisionStep.MEMBER_CUSTOM, true);
        assertStep(org("denied", EVENT_PUBLISH), DecisionStep.MEMBER_DENIED, false);
        assertStep(org("contributor", EVENT_EDIT), DecisionStep.DEFAULT, false);
        assertStep(org("removed", EVENT_VIEW), DecisionStep.NOT_A_MEMBER, false);
        assertStep(org("stranger", EVENT_VIEW), DecisionStep.NOT_A_MEMBER, false);
    }

    @Test
    @DisplayName("A grant is exhaustive: it narrows an admin on its event, adds for a stranger, and a member denial does not apply on it")
    void grantIsTheWholeAnswer() {
        member("admin", OrganizationRole.ADMIN, MemberStatus.ACTIVE);
        grant("admin", EVENT, ORG, EventRole.VIEWER, Set.of(), AccessGrantStatus.ACTIVE, null);
        grant("steward", EVENT, ORG, EventRole.CHECK_IN, Set.of("analytics:view"), AccessGrantStatus.ACTIVE, null);
        OrganizationMember refunds = member("refunds-denied", OrganizationRole.MANAGER, MemberStatus.ACTIVE);
        refunds.setDeniedPermissions(Set.of("ticket:refund"));
        members.save(refunds).block();
        grant("refunds-denied", EVENT, ORG, EventRole.EVENT_ADMIN, Set.of(), AccessGrantStatus.ACTIVE, null);

        assertStep(event("admin", EVENT_EDIT), DecisionStep.GRANT_EXHAUSTIVE, false);
        assertStep(event("admin", EVENT_VIEW), DecisionStep.GRANT_ROLE, true);
        assertStep(event("steward", TICKET_SCAN), DecisionStep.GRANT_ROLE, true);
        assertStep(event("steward", ANALYTICS_VIEW), DecisionStep.GRANT_CUSTOM, true);
        assertStep(event("steward", EVENT_EDIT), DecisionStep.GRANT_EXHAUSTIVE, false);
        assertStep(event("refunds-denied", TICKET_REFUND), DecisionStep.GRANT_ROLE, true);
        assertThat(resolution.hasEventPermission("admin", "event-other", ORG, EVENT_EDIT).block())
                .as("the narrowing grant does not reach the admin's other events").isTrue();
    }

    @Test
    @DisplayName("An expired or revoked grant is absent, at the exact expiry boundary and a second either side")
    void grantExpiryBoundary() {
        member("steward", OrganizationRole.MANAGER, MemberStatus.ACTIVE);
        grant("steward", "event-expiring", ORG, EventRole.VIEWER, Set.of(), AccessGrantStatus.ACTIVE, NOW);
        grant("steward", "event-lapsed", ORG, EventRole.VIEWER, Set.of(), AccessGrantStatus.ACTIVE, NOW.minusSeconds(1));
        grant("steward", "event-future", ORG, EventRole.VIEWER, Set.of(), AccessGrantStatus.ACTIVE, NOW.plusSeconds(1));
        grant("steward", "event-revoked", ORG, EventRole.VIEWER, Set.of(), AccessGrantStatus.REVOKED, null);

        assertStep(eventOf("steward", "event-expiring", EVENT_EDIT), DecisionStep.GRANT_EXHAUSTIVE, false);
        assertStep(eventOf("steward", "event-future", EVENT_EDIT), DecisionStep.GRANT_EXHAUSTIVE, false);
        assertStep(eventOf("steward", "event-lapsed", EVENT_EDIT), DecisionStep.MEMBER_ROLE, true);
        assertStep(eventOf("steward", "event-revoked", EVENT_EDIT), DecisionStep.MEMBER_ROLE, true);
    }

    @Test
    @DisplayName("A grant issued by another organization authorizes nothing on this organization's event")
    void foreignGrantIsIgnored() {
        grant("outsider", EVENT, OTHER_ORG, EventRole.EVENT_OWNER, Set.of(), AccessGrantStatus.ACTIVE, null);

        assertStep(event("outsider", EVENT_EDIT), DecisionStep.NOT_A_MEMBER, false);
        assertThat(resolution.hasEventPermission("outsider", EVENT, OTHER_ORG, EVENT_EDIT).block()).isTrue();
    }

    @Test
    @DisplayName("Without a named organization only a grant can answer; otherwise there is nothing to decide with")
    void noOrganizationNamed() {
        grant("steward", EVENT, ORG, EventRole.CHECK_IN, Set.of(), AccessGrantStatus.ACTIVE, null);

        assertThat(resolution.decideEvent("steward", EVENT, null, TICKET_SCAN, false).block().step())
                .isEqualTo(DecisionStep.GRANT_ROLE);
        assertThat(resolution.decideEvent("nobody", EVENT, null, TICKET_SCAN, false).block().step())
                .isEqualTo(DecisionStep.NO_ORGANIZATION);
    }

    @Test
    @DisplayName("The organization's status refuses what a member's role allows, and platform staff are not held to it")
    void organizationStatusGate() {
        organization("org-suspended", OrganizationStatus.SUSPENDED);
        member("owner", "org-suspended", OrganizationRole.OWNER);
        user("staff-admin", UserType.ADMIN);

        Decision refused = resolution.decideOrganization("owner", "org-suspended", EVENT_PUBLISH, true).block();
        assertThat(refused.step()).isEqualTo(DecisionStep.ORGANIZATION_STATUS);
        assertThat(refused.organizationStatus()).isEqualTo("SUSPENDED");
        assertThat(resolution.decideOrganization("owner", "org-suspended", EVENT_VIEW, true).block().allowed())
                .as("a read is not gated by status").isTrue();
        assertThat(resolution.decideOrganization("staff-admin", "org-suspended", EVENT_PUBLISH, true).block().step())
                .isEqualTo(DecisionStep.PLATFORM_ROLE);
    }

    @Test
    @DisplayName("Resolution against a real database stays under the 20 ms p99 that would justify a cache")
    void decisionLatencyIsMeasured() {
        organization("org-latency", OrganizationStatus.APPROVED);
        member("latency-owner", "org-latency", OrganizationRole.OWNER);
        grant("latency-guest", "event-latency", "org-latency", EventRole.VIEWER, Set.of(), AccessGrantStatus.ACTIVE, null);
        for (int warm = 0; warm < 100; warm++) {
            resolution.decideOrganization("latency-owner", "org-latency", EVENT_VIEW, true).block();
        }

        int samples = 1000;
        long[] nanos = new long[samples];
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            if (i % 2 == 0) {
                resolution.decideOrganization("latency-owner", "org-latency", EVENT_EDIT, true).block();
            } else {
                resolution.decideEvent("latency-guest", "event-latency", null, EVENT_VIEW, false).block();
            }
            nanos[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(nanos);
        double p50 = nanos[samples / 2] / 1e6;
        double p99 = nanos[(int) (samples * 0.99) - 1] / 1e6;
        System.out.printf("ET-PLT-013 decision latency over %d decisions: p50=%.2f ms p99=%.2f ms%n", samples, p50, p99);

        assertThat(p99).as("p99 resolution latency, ms; a cache is justified only above 20").isLessThan(20.0);
    }

    @Test
    @DisplayName("A suspended organization freezes the grants it issued: the guest can read but not act")
    void suspendedOrganizationFreezesGrants() {
        organization("org-frozen", OrganizationStatus.SUSPENDED);
        grant("guest", "event-frozen", "org-frozen", EventRole.EVENT_ADMIN, Set.of(), AccessGrantStatus.ACTIVE, null);

        Decision refused = resolution.decideEvent("guest", "event-frozen", null, EVENT_EDIT, false).block();
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.step()).isEqualTo(DecisionStep.ORGANIZATION_STATUS);
        assertThat(resolution.decideEvent("guest", "event-frozen", null, EVENT_VIEW, false).block().allowed())
                .as("a read is not gated by status").isTrue();
    }

    @Test
    @DisplayName("A member of an organization that cannot be found is refused as an unknown organization")
    void unknownOrganization() {
        member("ghost", "org-missing", OrganizationRole.OWNER);

        assertThat(resolution.decideOrganization("ghost", "org-missing", EVENT_VIEW, true).block().step())
                .isEqualTo(DecisionStep.ORGANIZATION_UNKNOWN);
    }

    @Test
    @DisplayName("A stored code the catalogue does not know grants nothing and denies nothing")
    void tamperedStoredCodes() {
        OrganizationMember tampered = member("tampered", OrganizationRole.CONTRIBUTOR, MemberStatus.ACTIVE);
        tampered.setCustomPermissions(Set.of("platform:*", "EVENT_EDIT", "event:edit "));
        tampered.setDeniedPermissions(Set.of("event:*", "TICKET_SCAN"));
        members.save(tampered).block();

        assertStep(org("tampered", EVENT_EDIT), DecisionStep.DEFAULT, false);
        assertStep(org("tampered", PLATFORM_CONFIGURE), DecisionStep.DEFAULT, false);
        assertStep(org("tampered", TICKET_SCAN), DecisionStep.MEMBER_ROLE, true);
    }

    @Test
    @DisplayName("An unknown permission is refused, and a caller with no identity is a stranger")
    void unknownPermissionAndAnonymous() {
        assertThat(resolution.decideOrganization("anyone", ORG, null, true).block().step())
                .isEqualTo(DecisionStep.PERMISSION_UNKNOWN);
        assertThat(resolution.decideOrganization(null, ORG, EVENT_VIEW, true).block().step())
                .isEqualTo(DecisionStep.NOT_A_MEMBER);
        assertThat(resolution.decideOrganization("anyone", null, EVENT_VIEW, true).block().step())
                .isEqualTo(DecisionStep.NOT_A_MEMBER);
    }

    // ---- owner decisions D-54 and D-55 ----

    @Test
    @DisplayName("Bank accounts are managed by owners and admins whatever the payout switch says; managers and marketers cannot")
    void bankAccountsFollowTheirOwnPermission() {
        member("owner", OrganizationRole.OWNER, MemberStatus.ACTIVE);
        member("admin", OrganizationRole.ADMIN, MemberStatus.ACTIVE);
        member("manager", OrganizationRole.MANAGER, MemberStatus.ACTIVE);
        member("marketer", OrganizationRole.MARKETER, MemberStatus.ACTIVE);

        // The organization's default has the payout switch off, and an admin still manages accounts.
        assertStep(org("owner", BANK_MANAGE), DecisionStep.MEMBER_ROLE, true);
        assertStep(org("admin", BANK_MANAGE), DecisionStep.MEMBER_ROLE, true);
        assertStep(org("admin", PAYOUT_REQUEST), DecisionStep.DEFAULT, false);
        assertStep(org("manager", BANK_MANAGE), DecisionStep.DEFAULT, false);
        assertStep(org("marketer", BANK_MANAGE), DecisionStep.DEFAULT, false);
    }

    @Test
    @DisplayName("Bank accounts cannot be managed before the organization is approved")
    void bankAccountsNeedAnApprovedOrganization() {
        organization("org-pending", OrganizationStatus.PENDING_REVIEW);
        member("owner", "org-pending", OrganizationRole.OWNER);

        Decision decision = resolution.decideOrganization("owner", "org-pending", BANK_MANAGE, true).block();

        assertThat(decision.step()).isEqualTo(DecisionStep.ORGANIZATION_STATUS);
    }

    @Test
    @DisplayName("Promotions belong to marketers, managers, admins and owners; the profile to every active member")
    void promotionsAndProfile() {
        for (OrganizationRole role : OrganizationRole.values()) {
            member("u-" + role.name(), role, MemberStatus.ACTIVE);
        }

        assertThat(org("u-CONTRIBUTOR", PROMOTION_MANAGE).allowed()).isFalse();
        for (OrganizationRole role : List.of(OrganizationRole.MARKETER, OrganizationRole.MANAGER,
                OrganizationRole.ADMIN, OrganizationRole.OWNER)) {
            assertThat(org("u-" + role.name(), PROMOTION_MANAGE).allowed()).as(role.name()).isTrue();
        }
        for (OrganizationRole role : OrganizationRole.values()) {
            assertThat(org("u-" + role.name(), ORGANIZATION_VIEW).allowed()).as(role.name()).isTrue();
        }
    }

    // ---- the effective set is the checks, one by one ----

    @Test
    @DisplayName("For every kind of caller, the effective set equals the permissions the checks allow")
    void effectiveSetEqualsChecks() {
        user("staff-finance", UserType.FINANCE);
        member("staff-finance", OrganizationRole.OWNER, MemberStatus.ACTIVE);
        member("manager", OrganizationRole.MANAGER, MemberStatus.ACTIVE);
        member("contributor", OrganizationRole.CONTRIBUTOR, MemberStatus.ACTIVE);
        OrganizationMember restricted = member("restricted", OrganizationRole.ADMIN, MemberStatus.ACTIVE);
        restricted.setDeniedPermissions(Set.of("team:remove"));
        members.save(restricted).block();
        grant("manager", EVENT, ORG, EventRole.CHECK_IN, Set.of("analytics:view"), AccessGrantStatus.ACTIVE, null);
        grant("steward", EVENT, ORG, EventRole.EDITOR, Set.of(), AccessGrantStatus.ACTIVE, null);
        grant("foreign", EVENT, OTHER_ORG, EventRole.EVENT_ADMIN, Set.of(), AccessGrantStatus.ACTIVE, null);
        organization("org-suspended", OrganizationStatus.SUSPENDED);
        member("owner", "org-suspended", OrganizationRole.OWNER);

        record Case(String user, String organization, String event) { }
        for (Case c : List.of(
                new Case("staff-finance", ORG, null), new Case("staff-finance", ORG, EVENT),
                new Case("manager", ORG, null), new Case("manager", ORG, EVENT),
                new Case("contributor", ORG, null), new Case("restricted", ORG, null),
                new Case("steward", ORG, EVENT), new Case("steward", null, EVENT),
                new Case("foreign", ORG, EVENT), new Case("owner", "org-suspended", null),
                new Case("nobody", ORG, null))) {
            Set<Permission> effective = resolution.getEffectivePermissions(c.user(), c.organization(), c.event())
                    .block().permissions();
            EnumSet<Permission> byChecks = EnumSet.noneOf(Permission.class);
            for (Permission permission : Permission.values()) {
                Decision decision = c.event() == null
                        ? resolution.decideOrganization(c.user(), c.organization(), permission, true).block()
                        : resolution.decideEvent(c.user(), c.event(), c.organization(), permission, true).block();
                if (decision.allowed()) {
                    byChecks.add(permission);
                }
            }
            assertThat(effective).as("%s in %s/%s", c.user(), c.organization(), c.event())
                    .containsExactlyInAnyOrderElementsOf(byChecks);
        }
    }

    @Test
    @DisplayName("Platform staff who are also members hold the union of both, not only the platform set")
    void platformStaffWhoAreMembersHoldBoth() {
        user("staff-finance", UserType.FINANCE);
        member("staff-finance", OrganizationRole.OWNER, MemberStatus.ACTIVE);

        var effective = resolution.getEffectivePermissions("staff-finance", ORG, null).block();

        assertThat(effective.permissions()).contains(PAYOUT_APPROVE, TEAM_INVITE, ORGANIZATION_BILLING);
        assertThat(effective.organizationRole()).isEqualTo(OrganizationRole.OWNER);
        assertThat(effective.source().name()).isEqualTo("PLATFORM");
    }

    // ---- delegation: only what you hold, in either direction ----

    @Test
    @DisplayName("Granting or denying is bounded by what the actor holds, a denial included")
    void delegationIsBoundedInBothDirections() {
        member("admin", OrganizationRole.ADMIN, MemberStatus.ACTIVE);

        resolution.requireDelegable("admin", ORG, Set.of("event:publish"), Permission.Scope.ORGANIZATION).block();
        assertThatThrownBy(() -> resolution.requireDelegable("admin", ORG, Set.of("organization:billing"),
                Permission.Scope.ORGANIZATION).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refused -> assertThat(refused.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED));
        assertThatThrownBy(() -> resolution.requireDelegable("admin", ORG, Set.of("payout:approve"),
                Permission.Scope.ORGANIZATION).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refused -> assertThat(refused.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED));
        assertThatThrownBy(() -> resolution.requireDelegable("admin", ORG, Set.of("event:*"),
                Permission.Scope.ORGANIZATION).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refused -> assertThat(refused.errorCode()).isEqualTo(ErrorCode.PERMISSION_UNKNOWN));
    }

    // ---- fixtures ----

    private Decision org(String userId, Permission permission) {
        return resolution.decideOrganization(userId, ORG, permission, true).block();
    }

    private Decision event(String userId, Permission permission) {
        return eventOf(userId, EVENT, permission);
    }

    private Decision eventOf(String userId, String eventId, Permission permission) {
        return resolution.decideEvent(userId, eventId, ORG, permission, true).block();
    }

    private static void assertStep(Decision decision, DecisionStep step, boolean allowed) {
        assertThat(decision.step()).as("deciding step").isEqualTo(step);
        assertThat(decision.allowed()).as("allowed").isEqualTo(allowed);
    }

    private static void organization(String id, OrganizationStatus status) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setSettings(new OrganizationSettings());
        organization.setStatus(status);
        organizations.save(organization).block();
    }

    private static OrganizationMember member(String userId, OrganizationRole role, MemberStatus status) {
        return member(userId, ORG, role, status);
    }

    private static void member(String userId, String organizationId, OrganizationRole role) {
        member(userId, organizationId, role, MemberStatus.ACTIVE);
    }

    private static OrganizationMember member(String userId, String organizationId, OrganizationRole role,
                                             MemberStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setUserId(userId);
        member.setOrganizationId(organizationId);
        member.setRole(role);
        member.setStatus(status);
        return members.save(member).block();
    }

    private static void user(String userId, UserType role) {
        User user = new User();
        user.setId(userId);
        user.setRoles(EnumSet.of(role));
        users.save(user).block();
    }

    private static void grant(String userId, String eventId, String organizationId, EventRole role,
                              Set<String> custom, AccessGrantStatus status, Instant expiresAt) {
        EventAccessGrant grant = new EventAccessGrant();
        grant.setUserId(userId);
        grant.setEventId(eventId);
        grant.setOrganizationId(organizationId);
        grant.setEventRole(role);
        grant.setCustomPermissions(custom);
        grant.setStatus(status);
        grant.setExpiresAt(expiresAt);
        grants.save(grant).block();
    }
}
