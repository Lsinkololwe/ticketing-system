package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.pml.shared.security.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The permission catalogue is closed, its codes parse one way, and platform roles grant exactly
 * their sets. Pure values.
 */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("The permission catalogue is closed and platform roles grant exactly their sets")
class PermissionCatalogueTest {

    @Test
    @DisplayName("Thirty permissions, every code unique")
    void thirtyUniqueCodes() {
        Set<String> codes = Arrays.stream(Permission.values()).map(Permission::code).collect(Collectors.toSet());
        assertThat(Permission.values()).hasSize(30);
        assertThat(codes).hasSize(30);
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("Every code is lower-case module:action with exactly one colon, and reads back to itself")
    void codesParseOneWay(Permission permission) {
        assertThat(permission.code()).matches("^[a-z_]+:[a-z_]+$");
        assertThat(permission.code().chars().filter(c -> c == ':').count()).isEqualTo(1);
        assertThat(Permission.fromCode(permission.code())).contains(permission);
        assertThat(permission.code()).startsWith(permission.module() + ":");
    }

    @Test
    @DisplayName("A name outside the catalogue is not a permission, including the old upper-case names")
    void unknownNamesAreNotPermissions() {
        for (String name : List.of("EVENT_EDIT", "event.edit", "event:*", "*", "", "event:access:grant", "Event:Edit")) {
            assertThat(Permission.fromCode(name)).as(name).isEmpty();
        }
        assertThat(Permission.fromCode(null)).isEmpty();
    }

    @Test
    @DisplayName("SUPER_ADMIN holds everything; ADMIN everything but platform configuration")
    void administratorSets() {
        assertThat(Permission.grantedByPlatformRoles(List.of("SUPER_ADMIN"))).containsExactlyInAnyOrder(Permission.values());
        assertThat(Permission.grantedByPlatformRoles(List.of("ADMIN")))
                .containsExactlyInAnyOrderElementsOf(EnumSet.complementOf(EnumSet.of(PLATFORM_CONFIGURE)));
        assertThat(Permission.grantedByPlatformRoles(List.of("ADMIN", "SUPER_ADMIN")))
                .as("holding both is the larger set, whatever the order").contains(PLATFORM_CONFIGURE);
    }

    @Test
    @DisplayName("FINANCE is its own money-and-audit set, not a slice of ADMIN")
    void financeSet() {
        assertThat(Permission.grantedByPlatformRoles(List.of("FINANCE"))).containsExactlyInAnyOrder(
                FINANCIAL_VIEW, PAYOUT_APPROVE, TICKET_REFUND, TRANSACTION_RECOVER, AUDIT_VIEW,
                ORGANIZATION_VIEW, EVENT_VIEW);
        assertThat(Permission.grantedByPlatformRoles(List.of("FINANCE")))
                .doesNotContain(ORGANIZATION_SUSPEND, EVENT_EDIT, ATTENDEE_VIEW);
    }

    @Test
    @DisplayName("Organizers, customers and finance leads hold nothing through their platform role")
    void rolesWithoutAPlatformSet() {
        assertThat(Permission.grantedByPlatformRoles(List.of("ORGANIZER", "CUSTOMER", "FINANCE_LEAD", "SCANNER"))).isEmpty();
        assertThat(Permission.grantedByPlatformRoles(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Spring authorities and realm roles give the same answer, without regard to case")
    void authorityPrefixAndCaseAreIgnored() {
        assertThat(Permission.grantedByPlatformRoles(List.of("ROLE_FINANCE")))
                .isEqualTo(Permission.grantedByPlatformRoles(List.of("finance")));
        assertThat(Permission.grantedByPlatformRoles(java.util.Arrays.asList("ROLE_ADMIN", null))).contains(ORGANIZATION_SUSPEND);
    }

    @Test
    @DisplayName("The sets handed out cannot be changed by the caller")
    void grantedSetsAreUnmodifiable() {
        Set<Permission> granted = Permission.grantedByPlatformRoles(List.of("FINANCE"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> granted.add(PLATFORM_CONFIGURE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
