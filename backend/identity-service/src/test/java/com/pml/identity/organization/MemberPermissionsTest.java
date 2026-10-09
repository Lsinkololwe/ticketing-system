package com.pml.identity.organization;

import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.pml.shared.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/** What one member may do: role under the organization's settings, plus custom, minus denied. Pure values. */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("A member's permissions are the role's under the settings, plus custom, minus denied")
class MemberPermissionsTest {

    @Test
    @DisplayName("A denial beats both the role and a custom grant")
    void denialWins() {
        OrganizationMember member = member(OrganizationRole.ADMIN, Set.of("payout:request"), Set.of("event:delete", "payout:request"));

        assertThat(member.hasPermission(EVENT_DELETE, null)).isFalse();
        assertThat(member.hasPermission(PAYOUT_REQUEST, null)).isFalse();
        assertThat(member.hasPermission(TEAM_INVITE, null)).isTrue();
    }

    @Test
    @DisplayName("Custom codes add to the role; old names and unknown codes add nothing")
    void customCodesAdd() {
        OrganizationMember member = member(OrganizationRole.CONTRIBUTOR, Set.of("analytics:view", "PROMOTION_MANAGE", "event:fly"), Set.of());

        assertThat(member.permissions(null)).contains(ANALYTICS_VIEW).doesNotContain(PROMOTION_MANAGE);
    }

    @Test
    @DisplayName("The owner's switch reaches the member through the settings passed in")
    void switchesReachTheMember() {
        OrganizationSettings on = new OrganizationSettings();
        on.setManagersCanViewFinancials(true);
        OrganizationMember manager = member(OrganizationRole.MANAGER, Set.of(), Set.of());

        assertThat(manager.hasPermission(FINANCIAL_VIEW, new OrganizationSettings())).isFalse();
        assertThat(manager.hasPermission(FINANCIAL_VIEW, on)).isTrue();
    }

    @Test
    @DisplayName("Changing one member's set never changes the role's shared set")
    void memberSetsAreCopies() {
        OrganizationMember member = member(OrganizationRole.CONTRIBUTOR, Set.of("analytics:view"), Set.of());
        member.permissions(null).add(ORGANIZATION_DELETE);

        assertThat(OrganizationRole.CONTRIBUTOR.permissions()).doesNotContain(ANALYTICS_VIEW, ORGANIZATION_DELETE);
    }

    private static OrganizationMember member(OrganizationRole role, Set<String> custom, Set<String> denied) {
        OrganizationMember member = new OrganizationMember();
        member.setRole(role);
        member.setCustomPermissions(custom);
        member.setDeniedPermissions(denied);
        return member;
    }
}
