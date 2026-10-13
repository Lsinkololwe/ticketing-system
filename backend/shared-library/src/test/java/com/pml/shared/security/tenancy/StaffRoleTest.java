package com.pml.shared.security.tenancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The approval rank: who may do what an action's required role names. */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007 · a super administrator covers every role, an administrator covers finance, nobody covers upward")
class StaffRoleTest {

    @Test
    @DisplayName("the staff set is exactly administrator, super administrator and finance")
    void theSetIsClosed() {
        assertThat(StaffRole.all()).containsExactlyInAnyOrder(StaffRole.ADMIN, StaffRole.SUPER_ADMIN, StaffRole.FINANCE);
        assertThat(StaffRole.all()).extracting(StaffRole::authority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_FINANCE");
    }

    @Test
    @DisplayName("rank: super covers all, admin covers finance and admin, finance covers finance only")
    void rank() {
        assertThat(StaffRole.covers(List.of("ROLE_SUPER_ADMIN"), "SUPER_ADMIN")).isTrue();
        assertThat(StaffRole.covers(List.of("ROLE_SUPER_ADMIN"), "FINANCE")).isTrue();
        assertThat(StaffRole.covers(List.of("ROLE_SUPER_ADMIN"), "ADMIN")).isTrue();
        assertThat(StaffRole.covers(List.of("ROLE_ADMIN"), "FINANCE")).isTrue();
        assertThat(StaffRole.covers(List.of("ROLE_ADMIN"), "ADMIN")).isTrue();
        assertThat(StaffRole.covers(List.of("ROLE_ADMIN"), "SUPER_ADMIN")).isFalse();
        assertThat(StaffRole.covers(List.of("ROLE_FINANCE"), "FINANCE")).isTrue();
        assertThat(StaffRole.covers(List.of("ROLE_FINANCE"), "ADMIN")).isFalse();
        assertThat(StaffRole.covers(List.of("ROLE_FINANCE"), "SUPER_ADMIN")).isFalse();
    }

    @Test
    @DisplayName("people who are not staff cover nothing, and a bare role name is not an authority")
    void nonStaff() {
        assertThat(StaffRole.covers(Set.of("ROLE_ORGANIZER", "ROLE_CUSTOMER"), "FINANCE")).isFalse();
        assertThat(StaffRole.covers(Set.of(), "FINANCE")).isFalse();
        assertThat(StaffRole.covers(Set.of("FINANCE"), "FINANCE")).isFalse();
        assertThat(StaffRole.covers(null, "FINANCE")).isFalse();
        assertThat(StaffRole.covers(Set.of("ROLE_FINANCE"), null)).isFalse();
    }
}
