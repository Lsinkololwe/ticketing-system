package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
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
import com.pml.identity.service.impl.PermissionResolutionServiceImpl;
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

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.pml.shared.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Permission decisions against a MongoDB replica set with the real repositories: roles, the
 * owner's switches read from the stored organization, event grants, platform roles, and the rule
 * that nobody hands out a permission they do not hold.
 */
@Tag("L2")
@Tag("ET-ORG-003")
@DisplayName("Permission decisions follow roles, the owner's switches, event grants and platform roles")
class PermissionResolutionTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final String ORG = "org-1";
    private static final String OTHER_ORG = "org-2";
    private static final String EVENT = "event-1";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static UserRepository users;
    private static OrganizationRepository organizations;
    private static OrganizationMemberRepository members;
    private static EventAccessGrantRepository grants;

    private PermissionResolutionServiceImpl resolution;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_permission_resolution"));
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
        Organization organization = new Organization();
        organization.setId(ORG);
        organization.setSettings(new OrganizationSettings());
        organizations.save(organization).block();

        for (OrganizationRole role : OrganizationRole.values()) {
            member("user-" + role.name().toLowerCase(), role, MemberStatus.ACTIVE);
        }
        resolution = new PermissionResolutionServiceImpl(users, members, organizations, grants, TestClock.frozenAt(NOW));
    }

    @Test
    @DisplayName("Owners and admins grant event access; managers do not")
    void ownersAndAdminsGrantEventAccess() {
        assertThat(holds("user-owner", EVENT_ACCESS_GRANT)).isTrue();
        assertThat(holds("user-admin", EVENT_ACCESS_GRANT)).isTrue();
        assertThat(holds("user-manager", EVENT_ACCESS_GRANT)).isFalse();
    }

    @Test
    @DisplayName("A manager's financial view follows the switch stored on the organization, read on every decision")
    void managersFinancialViewFollowsTheStoredSwitch() {
        assertThat(holds("user-manager", FINANCIAL_VIEW)).isFalse();

        switchOn(settings -> settings.setManagersCanViewFinancials(true));

        assertThat(holds("user-manager", FINANCIAL_VIEW)).isTrue();
        assertThat(holds("user-marketer", FINANCIAL_VIEW)).isFalse();
    }

    @Test
    @DisplayName("An admin's payout request follows the switch; the owner may always request")
    void adminsPayoutRequestFollowsTheStoredSwitch() {
        assertThat(holds("user-admin", PAYOUT_REQUEST)).isFalse();
        assertThat(holds("user-owner", PAYOUT_REQUEST)).isTrue();

        switchOn(settings -> settings.setAdminsCanRequestPayouts(true));

        assertThat(holds("user-admin", PAYOUT_REQUEST)).isTrue();
    }

    @Test
    @DisplayName("A refused check says which permission was missing, as ACTOR_NOT_PERMITTED")
    void refusalNamesThePermission() {
        assertThatThrownBy(() -> resolution.requireOrganizationPermission("user-contributor", ORG, TEAM_INVITE).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refused -> {
                    assertThat(refused.errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
                    assertThat(refused.getMessage()).contains("team:invite");
                });
        resolution.requireOrganizationPermission("user-admin", ORG, TEAM_INVITE).block();
    }

    @Test
    @DisplayName("Suspended members, members of another organization and strangers hold nothing")
    void onlyActiveMembersHoldAnything() {
        member("user-suspended", OrganizationRole.OWNER, MemberStatus.SUSPENDED);

        assertThat(holds("user-suspended", EVENT_VIEW)).isFalse();
        assertThat(resolution.hasOrganizationPermission("user-owner", OTHER_ORG, EVENT_VIEW).block()).isFalse();
        assertThat(holds("user-nobody", EVENT_VIEW)).isFalse();
    }

    @Test
    @DisplayName("Custom permissions add to the role and denied ones remove from it, a denial winning")
    void customAndDeniedPermissions() {
        OrganizationMember contributor = members.findByUserIdAndOrganizationId("user-contributor", ORG).block();
        contributor.setCustomPermissions(Set.of("analytics:view", "event:delete"));
        contributor.setDeniedPermissions(Set.of("event:delete", "ticket:scan"));
        members.save(contributor).block();

        assertThat(holds("user-contributor", ANALYTICS_VIEW)).isTrue();
        assertThat(holds("user-contributor", EVENT_DELETE)).as("denied beats custom").isFalse();
        assertThat(holds("user-contributor", TICKET_SCAN)).as("denied beats the role").isFalse();
    }

    @Test
    @DisplayName("Platform roles decide before membership: ADMIN everywhere, FINANCE only its money set")
    void platformRolesDecideFirst() {
        user("user-platform-admin", UserType.ADMIN);
        user("user-finance", UserType.FINANCE);

        assertThat(holds("user-platform-admin", ORGANIZATION_EDIT)).isTrue();
        assertThat(holds("user-platform-admin", PLATFORM_CONFIGURE)).isFalse();
        assertThat(holds("user-finance", FINANCIAL_VIEW)).isTrue();
        assertThat(holds("user-finance", EVENT_EDIT)).isFalse();
    }

    @Test
    @DisplayName("An event grant is the whole answer on its event, and counts only for the organization that issued it")
    void eventGrantsAreExhaustiveAndScoped() {
        grant("user-manager", EVENT, ORG, EventRole.VIEWER);

        assertThat(resolution.hasEventPermission("user-manager", EVENT, ORG, EVENT_EDIT).block())
                .as("the viewer grant narrows the manager on this event").isFalse();
        assertThat(resolution.hasEventPermission("user-manager", "event-other", ORG, EVENT_EDIT).block())
                .as("elsewhere the manager role applies").isTrue();

        grant("user-stranger", "event-foreign", OTHER_ORG, EventRole.EVENT_ADMIN);
        assertThat(resolution.hasEventPermission("user-stranger", "event-foreign", "org-3", EVENT_EDIT).block())
                .as("a grant naming another organization's event authorizes nothing there").isFalse();
        assertThat(resolution.hasEventPermission("user-stranger", "event-foreign", OTHER_ORG, EVENT_EDIT).block()).isTrue();
    }

    @Test
    @DisplayName("Nobody hands out a permission they lack, a platform permission, or a name the catalogue does not have")
    void delegationIsBoundedByWhatTheGranterHolds() {
        resolution.requireDelegable("user-admin", ORG, Set.of("analytics:view", "team:view"), Permission.Scope.ORGANIZATION).block();

        assertRefused(() -> resolution.requireDelegable("user-admin", ORG, Set.of("payout:request"), Permission.Scope.ORGANIZATION),
                ErrorCode.ACTOR_NOT_PERMITTED, "payout:request");
        assertRefused(() -> resolution.requireDelegable("user-owner", ORG, Set.of("payout:approve"), Permission.Scope.ORGANIZATION),
                ErrorCode.ACTOR_NOT_PERMITTED, "payout:approve");
        assertRefused(() -> resolution.requireDelegable("user-owner", ORG, Set.of("team:invite"), Permission.Scope.EVENT),
                ErrorCode.ACTOR_NOT_PERMITTED, "team:invite");
        assertRefused(() -> resolution.requireDelegable("user-owner", ORG, Set.of("EVENT_EDIT"), Permission.Scope.ORGANIZATION),
                ErrorCode.PERMISSION_UNKNOWN, "EVENT_EDIT");
    }

    @Test
    @DisplayName("Effective permissions report which step decided")
    void effectivePermissionsNameTheirSource() {
        grant("user-marketer", EVENT, ORG, EventRole.CHECK_IN);

        var viaGrant = resolution.getEffectivePermissions("user-marketer", ORG, EVENT).block();
        var viaRole = resolution.getEffectivePermissions("user-marketer", ORG, null).block();
        var nothing = resolution.getEffectivePermissions("user-nobody", ORG, null).block();

        assertThat(viaGrant.source()).isEqualTo("EVENT");
        assertThat(viaGrant.permissions()).containsExactlyInAnyOrderElementsOf(EventRole.CHECK_IN.permissions());
        assertThat(viaRole.source()).isEqualTo("ORGANIZATION");
        assertThat(viaRole.permissions()).contains(PROMOTION_MANAGE);
        assertThat(nothing.source()).isEqualTo("NONE");
        assertThat(nothing.permissions()).isEmpty();
    }

    private boolean holds(String userId, Permission permission) {
        return resolution.hasOrganizationPermission(userId, ORG, permission).block();
    }

    private void switchOn(java.util.function.Consumer<OrganizationSettings> change) {
        Organization organization = organizations.findById(ORG).block();
        change.accept(organization.getSettings());
        organizations.save(organization).block();
    }

    private static void member(String userId, OrganizationRole role, MemberStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setUserId(userId);
        member.setOrganizationId(ORG);
        member.setRole(role);
        member.setStatus(status);
        members.save(member).block();
    }

    private static void user(String userId, UserType role) {
        User user = new User();
        user.setId(userId);
        user.setRoles(EnumSet.of(role));
        users.save(user).block();
    }

    private static void grant(String userId, String eventId, String organizationId, EventRole role) {
        EventAccessGrant grant = new EventAccessGrant();
        grant.setUserId(userId);
        grant.setEventId(eventId);
        grant.setOrganizationId(organizationId);
        grant.setEventRole(role);
        grant.setStatus(AccessGrantStatus.ACTIVE);
        grants.save(grant).block();
    }

    private static void assertRefused(java.util.function.Supplier<reactor.core.publisher.Mono<Void>> call, ErrorCode code, String mentioned) {
        assertThatThrownBy(() -> call.get().block())
                .isInstanceOfSatisfying(DomainRefusal.class, refused -> {
                    assertThat(refused.errorCode()).isEqualTo(code);
                    assertThat(refused.getMessage()).contains(mentioned);
                });
    }
}
