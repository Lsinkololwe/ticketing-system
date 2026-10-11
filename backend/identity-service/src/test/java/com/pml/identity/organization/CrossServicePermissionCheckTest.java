package com.pml.identity.organization;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.impl.AuthorizationServiceImpl;
import com.pml.identity.service.impl.PermissionResolutionServiceImpl;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;

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

    private OrganizationMemberRepository memberRepository;
    private OrganizationRepository organizationRepository;
    private EventAccessGrantRepository grantRepository;
    private OrganizationService organizations;
    private AuthorizationServiceImpl authorization;
    private Organization organization;

    @BeforeEach
    void setUp() {
        memberRepository = mock(OrganizationMemberRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        grantRepository = mock(EventAccessGrantRepository.class);
        organizations = mock(OrganizationService.class);
        PermissionResolutionServiceImpl resolution = new PermissionResolutionServiceImpl(
                mock(UserRepository.class), memberRepository, organizationRepository, grantRepository,
                TestClock.frozenAt(Instant.parse("2026-09-18T10:00:00Z")));
        authorization = new AuthorizationServiceImpl(resolution, mock(OrganizationMemberService.class), organizations);

        organization = new Organization();
        organization.setId(ORG);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setSettings(new OrganizationSettings());
        when(organizations.findById(ORG)).thenReturn(Mono.just(organization));
        when(organizationRepository.findById(ORG)).thenReturn(Mono.just(organization));
        when(grantRepository.findByUserIdAndEventId("user-1", "event-1")).thenReturn(Mono.empty());
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
        when(grantRepository.findByUserIdAndEventId("user-1", "event-1")).thenReturn(Mono.just(foreign));
        when(memberRepository.findByUserIdAndOrganizationId("user-1", ORG)).thenReturn(Mono.empty());

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
        when(grantRepository.findByUserIdAndEventId("user-1", "event-1")).thenReturn(Mono.just(viewer));

        assertThat(authorization.checkEventAccess("user-1", "event-1", ORG, "event:edit").block().isAuthorized()).isFalse();
    }

    @Test
    @DisplayName("Gate staff hold ticket:scan through a check-in grant alone; a viewer grant is refused naming its role")
    void gateStaffScanThroughTheirGrant() {
        when(memberRepository.findByUserIdAndOrganizationId("user-1", ORG)).thenReturn(Mono.empty());
        EventAccessGrant checkIn = new EventAccessGrant();
        checkIn.setOrganizationId(ORG);
        checkIn.setEventRole(EventRole.CHECK_IN);
        checkIn.setStatus(AccessGrantStatus.ACTIVE);
        when(grantRepository.findByUserIdAndEventId("user-1", "event-1")).thenReturn(Mono.just(checkIn));

        assertThat(authorization.checkAuthorization(scanRequest()).block().isAuthorized()).isTrue();

        checkIn.setEventRole(EventRole.VIEWER);
        AuthorizationResult refused = authorization.checkAuthorization(scanRequest()).block();
        assertThat(refused.isAuthorized()).isFalse();
        assertThat(refused.getGrantingRole()).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("Someone with neither a grant nor a membership is refused without a role, as an outsider")
    void outsiderIsRefusedWithoutARole() {
        when(memberRepository.findByUserIdAndOrganizationId("user-1", ORG)).thenReturn(Mono.empty());

        AuthorizationResult refused = authorization.checkAuthorization(scanRequest()).block();

        assertThat(refused.isAuthorized()).isFalse();
        assertThat(refused.getGrantingRole()).isNull();
    }

    @Test
    @DisplayName("The organization name lookup returns the name, and nothing for a blank or unknown id")
    void organizationNameLookup() {
        organization.setName("Showstop Live Events");
        when(organizations.findById("org-unknown")).thenReturn(Mono.empty());

        assertThat(authorization.getOrganizationName(ORG).block()).isEqualTo("Showstop Live Events");
        assertThat(authorization.getOrganizationName("org-unknown").block()).isNull();
        assertThat(authorization.getOrganizationName("  ").block()).as("blank is not looked up at all").isNull();
        assertThat(authorization.getOrganizationName(null).block()).isNull();
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
        when(memberRepository.findByUserIdAndOrganizationId("user-1", ORG)).thenReturn(Mono.just(member));
    }
}
