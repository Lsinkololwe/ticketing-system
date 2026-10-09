package com.pml.identity.organization;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.impl.AuthorizationServiceImpl;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.dto.authorization.AuthorizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The check catalog and booking call before an event write or a payout: catalogue codes only,
 * grants honoured only on their own organization's events, and the owner's switches applied.
 */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("The cross-service permission check takes catalogue codes and applies grants and switches")
class CrossServicePermissionCheckTest {

    private static final String ORG = "org-1";

    private OrganizationMemberService members;
    private OrganizationService organizations;
    private EventAccessService grants;
    private AuthorizationServiceImpl authorization;
    private Organization organization;

    @BeforeEach
    void setUp() {
        members = mock(OrganizationMemberService.class);
        organizations = mock(OrganizationService.class);
        grants = mock(EventAccessService.class);
        authorization = new AuthorizationServiceImpl(members, organizations, grants);

        organization = new Organization();
        organization.setId(ORG);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setSettings(new OrganizationSettings());
        when(organizations.findById(ORG)).thenReturn(Mono.just(organization));
        when(grants.findByUserAndEvent("user-1", "event-1")).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("An old upper-case name is refused as unknown, even for the owner")
    void oldNamesAreUnknown() {
        memberIs(OrganizationRole.OWNER);

        AuthorizationResult result = authorization.checkEventPermission("user-1", ORG, "EVENT_EDIT").block();

        assertThat(result.isAuthorized()).isFalse();
        assertThat(result.getReason()).contains("Unknown permission");
        assertThat(authorization.checkEventPermission("user-1", ORG, "event:edit").block().isAuthorized()).isTrue();
    }

    @Test
    @DisplayName("A manager passes financial:view only once the owner's switch is on")
    void managerSwitchApplies() {
        memberIs(OrganizationRole.MANAGER);

        assertThat(authorization.checkEventPermission("user-1", ORG, "financial:view").block().isAuthorized()).isFalse();
        organization.getSettings().setManagersCanViewFinancials(true);
        assertThat(authorization.checkEventPermission("user-1", ORG, "financial:view").block().isAuthorized()).isTrue();
    }

    @Test
    @DisplayName("The organization's status still gates publishing and payouts for a member who holds the permission")
    void lifecycleStillGates() {
        memberIs(OrganizationRole.OWNER);
        organization.setStatus(OrganizationStatus.PENDING_REVIEW);

        assertThat(authorization.checkEventPermission("user-1", ORG, "payout:request").block().isAuthorized()).isFalse();
        assertThat(authorization.checkEventPermission("user-1", ORG, "event:edit").block().isAuthorized())
                .as("drafting continues during review").isTrue();
    }

    @Test
    @DisplayName("A grant on an event of another organization authorizes nothing there")
    void foreignGrantIsIgnored() {
        EventAccessGrant foreign = new EventAccessGrant();
        foreign.setOrganizationId("org-elsewhere");
        foreign.setEventRole(EventRole.EVENT_OWNER);
        foreign.setStatus(AccessGrantStatus.ACTIVE);
        when(grants.findByUserAndEvent("user-1", "event-1")).thenReturn(Mono.just(foreign));
        when(members.findByUserAndOrganization("user-1", ORG)).thenReturn(Mono.empty());

        assertThat(authorization.checkEventAccess("user-1", "event-1", ORG, "event:delete").block().isAuthorized()).isFalse();

        foreign.setOrganizationId(ORG);
        assertThat(authorization.checkEventAccess("user-1", "event-1", ORG, "event:delete").block().isAuthorized()).isTrue();
    }

    @Test
    @DisplayName("A grant on the event decides alone: a narrower grant refuses what the member's role would allow")
    void grantIsExhaustive() {
        memberIs(OrganizationRole.ADMIN);
        EventAccessGrant viewer = new EventAccessGrant();
        viewer.setOrganizationId(ORG);
        viewer.setEventRole(EventRole.VIEWER);
        viewer.setStatus(AccessGrantStatus.ACTIVE);
        when(grants.findByUserAndEvent("user-1", "event-1")).thenReturn(Mono.just(viewer));

        assertThat(authorization.checkEventAccess("user-1", "event-1", ORG, "event:edit").block().isAuthorized()).isFalse();
    }

    @Test
    @DisplayName("Gate staff hold ticket:scan through a check-in grant alone; a viewer grant is refused naming its role")
    void gateStaffScanThroughTheirGrant() {
        when(members.findByUserAndOrganization("user-1", ORG)).thenReturn(Mono.empty());
        EventAccessGrant checkIn = new EventAccessGrant();
        checkIn.setOrganizationId(ORG);
        checkIn.setEventRole(EventRole.CHECK_IN);
        checkIn.setStatus(AccessGrantStatus.ACTIVE);
        when(grants.findByUserAndEvent("user-1", "event-1")).thenReturn(Mono.just(checkIn));

        assertThat(authorization.checkAuthorization(scanRequest()).block().isAuthorized()).isTrue();

        checkIn.setEventRole(EventRole.VIEWER);
        AuthorizationResult refused = authorization.checkAuthorization(scanRequest()).block();
        assertThat(refused.isAuthorized()).isFalse();
        assertThat(refused.getGrantingRole()).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("Someone with neither a grant nor a membership is refused without a role, as an outsider")
    void outsiderIsRefusedWithoutARole() {
        when(members.findByUserAndOrganization("user-1", ORG)).thenReturn(Mono.empty());

        AuthorizationResult refused = authorization.checkAuthorization(scanRequest()).block();

        assertThat(refused.isAuthorized()).isFalse();
        assertThat(refused.getGrantingRole()).isNull();
    }

    private static com.pml.shared.dto.authorization.AuthorizationRequest scanRequest() {
        return com.pml.shared.dto.authorization.AuthorizationRequest.builder()
                .userId("user-1").eventId("event-1").organizationId(ORG).requiredPermission("ticket:scan").build();
    }

    private void memberIs(OrganizationRole role) {
        OrganizationMember member = new OrganizationMember();
        member.setUserId("user-1");
        member.setOrganizationId(ORG);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        when(members.findByUserAndOrganization("user-1", ORG)).thenReturn(Mono.just(member));
    }
}
