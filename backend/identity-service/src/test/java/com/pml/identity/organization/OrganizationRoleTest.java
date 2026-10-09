package com.pml.identity.organization;

import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.shared.security.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static com.pml.shared.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The organization role table: each role's exact permission set, the inheritance shape, and the
 * two permissions that depend on the owner's switches. Pure values, no Spring and no database.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("Organization roles carry exactly their permissions, and the owner's switches add the two conditional ones")
class OrganizationRoleTest {

    private static final Set<Permission> CONTRIBUTOR_SET = EnumSet.of(
            EVENT_VIEW, ATTENDEE_VIEW, TICKET_SCAN, ORGANIZATION_VIEW, TEAM_VIEW);

    private static final Set<Permission> MARKETER_SET = union(CONTRIBUTOR_SET, ANALYTICS_VIEW, PROMOTION_MANAGE);

    private static final Set<Permission> MANAGER_SET = union(CONTRIBUTOR_SET,
            EVENT_CREATE, EVENT_EDIT, EVENT_PUBLISH, ANALYTICS_VIEW, PROMOTION_MANAGE);

    private static final Set<Permission> ADMIN_SET = union(union(MANAGER_SET, MARKETER_SET.toArray(Permission[]::new)),
            EVENT_DELETE, EVENT_CANCEL, TICKET_REFUND, TEAM_INVITE, TEAM_REMOVE, TEAM_ROLE, EVENT_ACCESS_GRANT,
            ORGANIZATION_EDIT, BANK_MANAGE, FINANCIAL_VIEW);

    private static final Set<Permission> OWNER_SET = union(ADMIN_SET,
            ORGANIZATION_BILLING, ORGANIZATION_TRANSFER, ORGANIZATION_DELETE, PAYOUT_REQUEST);

    @Test
    @DisplayName("With both switches off, every role carries exactly its table row")
    void eachRoleCarriesItsRow() {
        OrganizationSettings off = new OrganizationSettings();

        assertThat(OrganizationRole.CONTRIBUTOR.permissions(off)).containsExactlyInAnyOrderElementsOf(CONTRIBUTOR_SET);
        assertThat(OrganizationRole.MARKETER.permissions(off)).containsExactlyInAnyOrderElementsOf(MARKETER_SET);
        assertThat(OrganizationRole.MANAGER.permissions(off)).containsExactlyInAnyOrderElementsOf(MANAGER_SET);
        assertThat(OrganizationRole.ADMIN.permissions(off)).containsExactlyInAnyOrderElementsOf(ADMIN_SET);
        assertThat(OrganizationRole.OWNER.permissions(off)).containsExactlyInAnyOrderElementsOf(OWNER_SET);
    }

    @Test
    @DisplayName("Managers see financial figures only when the owner switches it on")
    void managersFinancialViewFollowsTheSwitch() {
        OrganizationSettings on = new OrganizationSettings();
        on.setManagersCanViewFinancials(true);

        assertThat(OrganizationRole.MANAGER.grants(FINANCIAL_VIEW, new OrganizationSettings())).isFalse();
        assertThat(OrganizationRole.MANAGER.grants(FINANCIAL_VIEW, null)).as("no settings means every switch off").isFalse();
        assertThat(OrganizationRole.MANAGER.grants(FINANCIAL_VIEW, on)).isTrue();
        assertThat(OrganizationRole.MARKETER.grants(FINANCIAL_VIEW, on)).as("the switch is the manager's only").isFalse();
        assertThat(OrganizationRole.MANAGER.grants(PAYOUT_REQUEST, on)).as("and it adds nothing else").isFalse();
    }

    @Test
    @DisplayName("Admins request payouts only when the owner switches it on; the owner always may")
    void adminsPayoutRequestFollowsTheSwitch() {
        OrganizationSettings on = new OrganizationSettings();
        on.setAdminsCanRequestPayouts(true);

        assertThat(OrganizationRole.ADMIN.grants(PAYOUT_REQUEST, new OrganizationSettings())).isFalse();
        assertThat(OrganizationRole.ADMIN.grants(PAYOUT_REQUEST, on)).isTrue();
        assertThat(OrganizationRole.MANAGER.grants(PAYOUT_REQUEST, on)).as("the switch is the admin's only").isFalse();
        assertThat(OrganizationRole.OWNER.grants(PAYOUT_REQUEST, new OrganizationSettings())).isTrue();
    }

    @Test
    @DisplayName("Owners and admins grant event access; nobody below them does")
    void eventAccessGrantBelongsToOwnersAndAdmins() {
        for (OrganizationRole role : OrganizationRole.values()) {
            boolean expected = role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN;
            assertThat(role.grants(EVENT_ACCESS_GRANT, null)).as("%s granting event access", role).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("No organization role carries a platform permission, whatever the switches")
    void noRoleCarriesAPlatformPermission() {
        OrganizationSettings on = new OrganizationSettings();
        on.setManagersCanViewFinancials(true);
        on.setAdminsCanRequestPayouts(true);
        for (OrganizationRole role : OrganizationRole.values()) {
            assertThat(role.permissions(on)).noneMatch(permission -> permission.scope() == Permission.Scope.PLATFORM);
        }
    }

    @Test
    @DisplayName("Marketer and manager are siblings: neither includes the other's event authoring")
    void marketerAndManagerAreIncomparable() {
        assertThat(OrganizationRole.MANAGER.parents()).containsExactly(OrganizationRole.CONTRIBUTOR);
        assertThat(OrganizationRole.MARKETER.parents()).containsExactly(OrganizationRole.CONTRIBUTOR);
        assertThat(OrganizationRole.ADMIN.parents()).containsExactlyInAnyOrder(OrganizationRole.MANAGER, OrganizationRole.MARKETER);

        assertThat(OrganizationRole.MARKETER.includes(OrganizationRole.MANAGER)).isFalse();
        assertThat(OrganizationRole.MARKETER.permissions()).doesNotContain(EVENT_CREATE, EVENT_EDIT, EVENT_PUBLISH);
    }

    @Test
    @DisplayName("The chain holds from owner down to contributor")
    void theChainHolds() {
        assertThat(OrganizationRole.OWNER.includes(OrganizationRole.ADMIN)).isTrue();
        assertThat(OrganizationRole.ADMIN.includes(OrganizationRole.MANAGER)).isTrue();
        assertThat(OrganizationRole.ADMIN.includes(OrganizationRole.MARKETER)).isTrue();
        assertThat(OrganizationRole.MANAGER.includes(OrganizationRole.CONTRIBUTOR)).isTrue();
        assertThat(OrganizationRole.CONTRIBUTOR.includes(OrganizationRole.MANAGER)).isFalse();
        assertThat(OrganizationRole.ADMIN.includes(OrganizationRole.OWNER)).isFalse();
    }

    @Test
    @DisplayName("Only the owner transfers, deletes, or manages billing")
    void ownerOnlyPermissions() {
        for (OrganizationRole role : OrganizationRole.values()) {
            boolean owner = role == OrganizationRole.OWNER;
            for (Permission ownerOnly : EnumSet.of(ORGANIZATION_TRANSFER, ORGANIZATION_DELETE, ORGANIZATION_BILLING)) {
                assertThat(role.grants(ownerOnly, null)).as("%s holding %s", role, ownerOnly).isEqualTo(owner);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(OrganizationRole.class)
    @DisplayName("A role's shared set cannot be changed in place, with or without a switch")
    void theSetsCannotBeMutated(OrganizationRole role) {
        // The closure is shared by every member holding the role; adding one member's custom
        // permission to it in place would hand that permission to all of them.
        OrganizationSettings on = new OrganizationSettings();
        on.setManagersCanViewFinancials(true);
        on.setAdminsCanRequestPayouts(true);
        assertThatThrownBy(() -> role.permissions().add(ORGANIZATION_DELETE)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> role.permissions(on).add(ORGANIZATION_DELETE)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Set<Permission> union(Set<Permission> base, Permission... more) {
        EnumSet<Permission> all = EnumSet.copyOf(base);
        all.addAll(Set.of(more));
        return all;
    }
}
