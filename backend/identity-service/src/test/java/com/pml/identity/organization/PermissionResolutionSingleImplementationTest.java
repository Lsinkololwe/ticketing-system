package com.pml.identity.organization;

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
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.impl.AuthorizationServiceImpl;
import com.pml.identity.service.impl.PermissionResolutionServiceImpl;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.UserType;
import com.pml.shared.dto.authorization.AuthorizationResult;
import com.pml.shared.security.Permission;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The permission resolution and the cross-service authorization checks give the same answer
 * because the second one only translates the first. The table runs both over the same stored
 * state; the source lint keeps the translation from growing a decision of its own.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Permission resolution has one implementation; the authorization checks delegate to it")
class PermissionResolutionSingleImplementationTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final String USER = "user-1";
    private static final String ORG = "org-1";
    private static final String EVENT = "event-1";

    private static final Path RESOLUTION = Path.of(
            "src/main/java/com/pml/identity/service/impl/PermissionResolutionServiceImpl.java");
    private static final Path AUTHORIZATION = Path.of(
            "src/main/java/com/pml/identity/service/impl/AuthorizationServiceImpl.java");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static final List<Permission> PERMISSIONS = List.of(
            Permission.EVENT_VIEW, Permission.EVENT_EDIT, Permission.EVENT_PUBLISH, Permission.PAYOUT_REQUEST,
            Permission.FINANCIAL_VIEW, Permission.TEAM_INVITE, Permission.TICKET_SCAN);

    private UserRepository users;
    private OrganizationMemberRepository members;
    private OrganizationRepository organizationRepository;
    private EventAccessGrantRepository grants;
    private OrganizationService organizationService;
    private PermissionResolutionServiceImpl resolution;
    private AuthorizationServiceImpl authorization;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        members = mock(OrganizationMemberRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        grants = mock(EventAccessGrantRepository.class);
        organizationService = mock(OrganizationService.class);
        when(users.findById(any(String.class))).thenReturn(Mono.empty());
        resolution = new PermissionResolutionServiceImpl(users, members, organizationRepository, grants,
                TestClock.frozenAt(NOW));
        authorization = new AuthorizationServiceImpl(resolution, mock(OrganizationMemberService.class), organizationService);
    }

    @Test
    @DisplayName("Organization-level checks agree for every role, status and permission, and for a non-member")
    void organizationChecksAgree() {
        List<String> disagreements = new ArrayList<>();
        List<OrganizationRole> roles = new ArrayList<>(List.of(OrganizationRole.values()));
        roles.add(null);
        for (OrganizationRole role : roles) {
            for (OrganizationStatus status : List.of(OrganizationStatus.DRAFT, OrganizationStatus.PENDING_REVIEW,
                    OrganizationStatus.ACTIVE, OrganizationStatus.SUSPENDED)) {
                arrange(role, status, null);
                for (Permission permission : PERMISSIONS) {
                    boolean viaResolution = resolution.hasOrganizationPermission(USER, ORG, permission).block();
                    boolean viaAuthorization = authorization.checkEventPermission(USER, ORG, permission.code())
                            .block().isAuthorized();
                    if (viaResolution != viaAuthorization) {
                        disagreements.add(role + "/" + status + "/" + permission.code()
                                + " resolution=" + viaResolution + " authorization=" + viaAuthorization);
                    }
                }
            }
        }
        assertThat(disagreements).isEmpty();
    }

    @Test
    @DisplayName("Event checks agree for every grant shape, role and permission, including a foreign or expired grant")
    void eventChecksAgree() {
        List<String> disagreements = new ArrayList<>();
        List<EventAccessGrant> shapes = new ArrayList<>();
        shapes.add(null);
        shapes.add(grant(EventRole.VIEWER, ORG, AccessGrantStatus.ACTIVE, null));
        shapes.add(grant(EventRole.CHECK_IN, ORG, AccessGrantStatus.ACTIVE, null));
        shapes.add(grant(EventRole.EVENT_ADMIN, ORG, AccessGrantStatus.ACTIVE, null));
        shapes.add(grant(EventRole.EVENT_ADMIN, "org-elsewhere", AccessGrantStatus.ACTIVE, null));
        shapes.add(grant(EventRole.EVENT_ADMIN, ORG, AccessGrantStatus.REVOKED, null));
        shapes.add(grant(EventRole.EVENT_ADMIN, ORG, AccessGrantStatus.ACTIVE, NOW.minusSeconds(60)));
        List<OrganizationRole> roles = new ArrayList<>(List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER,
                OrganizationRole.CONTRIBUTOR));
        roles.add(null);
        for (EventAccessGrant shape : shapes) {
            for (OrganizationRole role : roles) {
                for (OrganizationStatus status : List.of(OrganizationStatus.PENDING_REVIEW, OrganizationStatus.ACTIVE)) {
                    arrange(role, status, shape);
                    for (Permission permission : PERMISSIONS) {
                        boolean viaResolution = resolution.hasEventPermission(USER, EVENT, ORG, permission).block();
                        boolean viaAuthorization = authorization.checkEventAccess(USER, EVENT, ORG, permission.code())
                                .block().isAuthorized();
                        if (viaResolution != viaAuthorization) {
                            disagreements.add((shape == null ? "no grant" : shape.getEventRole() + "@"
                                    + shape.getOrganizationId() + "/" + shape.getStatus() + "/" + shape.getExpiresAt())
                                    + " " + role + "/" + status + "/" + permission.code()
                                    + " resolution=" + viaResolution + " authorization=" + viaAuthorization);
                        }
                    }
                }
            }
        }
        assertThat(disagreements).isEmpty();
    }

    @Test
    @DisplayName("The organization's status refuses publishing and payouts through both entry points")
    void statusGateAppliesToBoth() {
        arrange(OrganizationRole.OWNER, OrganizationStatus.PENDING_REVIEW, null);

        assertThat(resolution.hasOrganizationPermission(USER, ORG, Permission.PAYOUT_REQUEST).block()).isFalse();
        assertThat(resolution.hasOrganizationPermission(USER, ORG, Permission.EVENT_PUBLISH).block()).isFalse();
        assertThat(resolution.hasOrganizationPermission(USER, ORG, Permission.EVENT_EDIT).block()).isTrue();
        AuthorizationResult refused = authorization.checkEventPermission(USER, ORG, "payout:request").block();
        assertThat(refused.isAuthorized()).isFalse();
        assertThat(refused.getReason()).isEqualTo("Organization status PENDING_REVIEW does not permit payout:request");
    }

    @Test
    @DisplayName("A member of an organization that cannot be found holds nothing, through both entry points")
    void unknownOrganizationHoldsNothing() {
        arrange(OrganizationRole.OWNER, OrganizationStatus.ACTIVE, null);
        when(organizationRepository.findById(ORG)).thenReturn(Mono.empty());
        when(organizationService.findById(ORG)).thenReturn(Mono.empty());

        assertThat(resolution.hasOrganizationPermission(USER, ORG, Permission.EVENT_EDIT).block()).isFalse();
        AuthorizationResult refused = authorization.checkEventPermission(USER, ORG, "event:edit").block();
        assertThat(refused.isAuthorized()).isFalse();
        assertThat(refused.getReason()).isEqualTo("Organization not found");
    }

    @Test
    @DisplayName("The authorization checks do not apply platform roles, so they never allow more than membership does")
    void platformRolesDoNotWidenTheAuthorizationChecks() {
        arrange(null, OrganizationStatus.ACTIVE, null);
        User admin = new User();
        admin.setId(USER);
        admin.setRoles(EnumSet.of(UserType.ADMIN));
        when(users.findById(USER)).thenReturn(Mono.just(admin));

        assertThat(resolution.hasOrganizationPermission(USER, ORG, Permission.ORGANIZATION_EDIT).block())
                .as("platform administrators pass the resolution").isTrue();
        AuthorizationResult result = authorization.checkEventPermission(USER, ORG, "organization:edit").block();
        assertThat(result.isAuthorized()).as("and are still just non-members here").isFalse();
        assertThat(result.getReason()).isEqualTo("User is not a member of the organization");
    }

    @Test
    @DisplayName("Refusal texts and response fields callers read are unchanged")
    void responseShapeIsPreserved() {
        arrange(OrganizationRole.CONTRIBUTOR, OrganizationStatus.ACTIVE, null);

        AuthorizationResult insufficient = authorization.checkEventPermission(USER, ORG, "team:invite").block();
        assertThat(insufficient.isAuthorized()).isFalse();
        assertThat(insufficient.getGrantingRole()).isEqualTo("CONTRIBUTOR");
        assertThat(insufficient.getReason())
                .isEqualTo("Insufficient permissions. Required: team:invite, Current role: CONTRIBUTOR");

        AuthorizationResult allowed = authorization.checkEventPermission(USER, ORG, "event:view").block();
        assertThat(allowed.isAuthorized()).isTrue();
        assertThat(allowed.getAuthorizationSource()).isEqualTo("ORGANIZATION_MEMBER");
        assertThat(allowed.getOrganizationId()).isEqualTo(ORG);

        assertThat(authorization.checkEventPermission(USER, ORG, "EVENT_EDIT").block().getReason())
                .isEqualTo("Unknown permission EVENT_EDIT");
        assertThat(authorization.checkEventAccess(USER, EVENT, null, "event:view").block().getReason())
                .isEqualTo("No event access grant and organization ID not provided");

        arrange(OrganizationRole.OWNER, OrganizationStatus.ACTIVE, grant(EventRole.VIEWER, ORG, AccessGrantStatus.ACTIVE, null));
        AuthorizationResult viaGrant = authorization.checkEventAccess(USER, EVENT, ORG, "event:view").block();
        assertThat(viaGrant.getAuthorizationSource()).isEqualTo("EVENT_ACCESS_GRANT");
        assertThat(viaGrant.getGrantingRole()).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("The authorization service holds no decision logic: it delegates to the resolution service")
    void adapterOnlyDelegates() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(RESOLUTION) && Files.isRegularFile(AUTHORIZATION),
                "identity sources not present");
        String resolutionSource = COMMENTS.matcher(Files.readString(RESOLUTION)).replaceAll("");
        String adapterSource = COMMENTS.matcher(Files.readString(AUTHORIZATION)).replaceAll("");

        List<String> mappingCalls = List.of("grantedByPlatformRoles(", ".hasPermission(", ".permissions(",
                ".canPerform(", ".isValid(", "OrganizationRole", "EventRole");
        for (String call : mappingCalls) {
            assertThat(resolutionSource).as("the single implementation owns " + call).contains(call);
            assertThat(adapterSource).as("the adapter must not repeat " + call).doesNotContain(call);
        }
        assertThat(adapterSource)
                .contains("PermissionResolutionService resolution")
                .contains("resolution.decideOrganization(")
                .contains("resolution.decideEvent(")
                .doesNotContain("EventAccessService")
                .doesNotContain("AccessGrantStatus");
    }

    /** Stores the state both entry points read: the member (or none), the organization and the grant (or none). */
    private void arrange(OrganizationRole role, OrganizationStatus status, EventAccessGrant grant) {
        Organization organization = new Organization();
        organization.setId(ORG);
        organization.setStatus(status);
        organization.setSettings(new OrganizationSettings());
        when(organizationRepository.findById(ORG)).thenReturn(Mono.just(organization));
        when(organizationService.findById(ORG)).thenReturn(Mono.just(organization));

        if (role == null) {
            when(members.findByUserIdAndOrganizationId(USER, ORG)).thenReturn(Mono.empty());
        } else {
            OrganizationMember member = new OrganizationMember();
            member.setUserId(USER);
            member.setOrganizationId(ORG);
            member.setRole(role);
            member.setStatus(MemberStatus.ACTIVE);
            when(members.findByUserIdAndOrganizationId(USER, ORG)).thenReturn(Mono.just(member));
        }
        when(grants.findByUserIdAndEventId(USER, EVENT))
                .thenReturn(grant == null ? Mono.empty() : Mono.just(grant));
    }

    private static EventAccessGrant grant(EventRole role, String organizationId, AccessGrantStatus status, Instant expiresAt) {
        EventAccessGrant grant = new EventAccessGrant();
        grant.setUserId(USER);
        grant.setEventId(EVENT);
        grant.setOrganizationId(organizationId);
        grant.setEventRole(role);
        grant.setStatus(status);
        grant.setExpiresAt(expiresAt);
        return grant;
    }
}
