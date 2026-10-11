package com.pml.identity.workflow.mirror;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A team member holds the {@code ORGANIZER} realm role while they belong to an organization: the
 * coarse gate of the console and of the organizer operations reads it, and an invited member would
 * otherwise never have it. The owner's role is granted at approval and is not touched here. Flat
 * methods: F-055.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("The membership mirror grants and releases the organizer realm role for team members")
class GroupMirrorOrganizerRoleTest {

    private OrganizationMemberRepository members;
    private KeycloakService keycloak;
    private GroupMirrorActivitiesImpl mirror;

    @BeforeEach
    void setUp() {
        members = Mockito.mock(OrganizationMemberRepository.class);
        OrganizationRepository organizations = Mockito.mock(OrganizationRepository.class);
        keycloak = Mockito.mock(KeycloakService.class);
        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setSlug("showstop");
        when(organizations.findById("org-1")).thenReturn(Mono.just(organization));
        when(members.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(keycloak.grantRealmRole(anyString(), anyString())).thenReturn(Mono.empty());
        when(keycloak.revokeRealmRole(anyString(), anyString())).thenReturn(Mono.empty());
        when(keycloak.joinOrganizationGroup(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(keycloak.leaveOrganizationGroup(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        mirror = new GroupMirrorActivitiesImpl(members, organizations, keycloak);
    }

    private OrganizationMember pending(String userId, OrganizationRole role, MemberStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setId("m-" + userId);
        member.setUserId(userId);
        member.setOrganizationId("org-1");
        member.setRole(role);
        member.setStatus(status);
        member.setMirrorPending(true);
        when(members.findByMirrorPendingTrue()).thenReturn(Flux.just(member));
        return member;
    }

    @Test
    @DisplayName("an active administrator is granted the role and joins their group")
    void activeMemberIsGranted() {
        OrganizationMember member = pending("user-admin", OrganizationRole.ADMIN, MemberStatus.ACTIVE);

        assertThat(mirror.repairPending()).isEqualTo(1);

        verify(keycloak).grantRealmRole("user-admin", "ORGANIZER");
        verify(keycloak).joinOrganizationGroup("user-admin", "showstop", "admins");
        assertThat(member.isMirrorPending()).isFalse();
    }

    @Test
    @DisplayName("an owner row is not granted the role here: approval grants it")
    void ownerIsNotGrantedHere() {
        pending("user-owner", OrganizationRole.OWNER, MemberStatus.ACTIVE);

        mirror.repairPending();

        verify(keycloak, never()).grantRealmRole(anyString(), anyString());
        verify(keycloak).joinOrganizationGroup("user-owner", "showstop", "owners");
    }

    @Test
    @DisplayName("a removed member who belongs nowhere else loses the role")
    void removedMemberLosesTheRole() {
        pending("user-manager", OrganizationRole.MANAGER, MemberStatus.REMOVED);
        when(members.findByUserIdAndStatus("user-manager", MemberStatus.ACTIVE)).thenReturn(Flux.empty());

        mirror.repairPending();

        verify(keycloak).leaveOrganizationGroup("user-manager", "showstop", "managers");
        verify(keycloak).revokeRealmRole("user-manager", "ORGANIZER");
    }

    @Test
    @DisplayName("a removed member who still belongs to another organization keeps the role")
    void removedMemberWhoStillBelongsKeepsTheRole() {
        pending("user-manager", OrganizationRole.MANAGER, MemberStatus.REMOVED);
        when(members.findByUserIdAndStatus("user-manager", MemberStatus.ACTIVE))
                .thenReturn(Flux.just(new OrganizationMember()));

        mirror.repairPending();

        verify(keycloak, never()).revokeRealmRole(anyString(), anyString());
    }

    @Test
    @DisplayName("a failed role grant leaves the membership pending for the next pass")
    void failedGrantStaysPending() {
        OrganizationMember member = pending("user-admin", OrganizationRole.ADMIN, MemberStatus.ACTIVE);
        when(keycloak.grantRealmRole("user-admin", "ORGANIZER")).thenReturn(Mono.error(new IllegalStateException("down")));

        assertThat(mirror.repairPending()).isZero();

        assertThat(member.isMirrorPending()).isTrue();
    }
}
