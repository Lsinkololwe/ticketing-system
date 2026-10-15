package com.pml.shared.security;

import com.pml.shared.security.tenancy.StaffRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single platform-role model answers exactly as the three rule sets it replaced did, for every
 * combination of roles a person can hold. The reference functions below are those earlier rules,
 * written out in full so the comparison does not depend on the model it checks.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("The platform-role model agrees with the rules it replaced on every role combination")
class PlatformRoleEquivalenceTest {

    private static List<Set<PlatformRole>> everyCombination() {
        PlatformRole[] all = PlatformRole.values();
        List<Set<PlatformRole>> combinations = new ArrayList<>();
        for (int mask = 0; mask < (1 << all.length); mask++) {
            EnumSet<PlatformRole> set = EnumSet.noneOf(PlatformRole.class);
            for (int bit = 0; bit < all.length; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    set.add(all[bit]);
                }
            }
            combinations.add(set);
        }
        return combinations;
    }

    private static Set<String> authorities(Set<PlatformRole> roles) {
        return roles.stream().map(r -> "ROLE_" + r.name()).collect(Collectors.toSet());
    }

    private static Set<Permission> referenceGrants(Set<String> names) {
        EnumSet<Permission> granted = EnumSet.noneOf(Permission.class);
        if (names.contains("SUPER_ADMIN")) {
            granted.addAll(EnumSet.allOf(Permission.class));
        } else if (names.contains("ADMIN")) {
            granted.addAll(PlatformGrantExpectations.admin());
        }
        if (names.contains("FINANCE")) {
            granted.addAll(PlatformGrantExpectations.FINANCE);
        }
        return granted;
    }

    private static boolean referenceDirective(String required, Set<String> a) {
        if (a.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }
        return switch (required) {
            case "CUSTOMER" -> a.contains("ROLE_CUSTOMER") || a.contains("ROLE_ORGANIZER") || a.contains("ROLE_ADMIN");
            case "ORGANIZER" -> a.contains("ROLE_ORGANIZER") || a.contains("ROLE_ADMIN");
            case "ADMIN" -> a.contains("ROLE_ADMIN");
            case "SUPER_ADMIN" -> false;
            case "FINANCE" -> a.contains("ROLE_FINANCE") || a.contains("ROLE_ADMIN");
            default -> throw new IllegalArgumentException(required);
        };
    }

    private static boolean referenceCovers(Set<String> held, String required) {
        if (held.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }
        if ("SUPER_ADMIN".equals(required)) {
            return false;
        }
        if ("FINANCE".equals(required)) {
            return held.contains("ROLE_FINANCE") || held.contains("ROLE_ADMIN");
        }
        return held.contains("ROLE_" + required);
    }

    @Test
    @DisplayName("Granted permissions are the same for every role combination, by bare name and by authority")
    void grantsAgree() {
        for (Set<PlatformRole> roles : everyCombination()) {
            Set<String> names = roles.stream().map(Enum::name).collect(Collectors.toSet());
            assertThat(Permission.grantedByPlatformRoles(names)).as("names %s", names)
                    .isEqualTo(referenceGrants(names));
            assertThat(Permission.grantedByPlatformRoles(authorities(roles))).as("authorities %s", names)
                    .isEqualTo(referenceGrants(names));
        }
    }

    @Test
    @DisplayName("The schema directive's role gate admits exactly who it used to")
    void directiveGateAgrees() {
        for (Set<PlatformRole> roles : everyCombination()) {
            Set<String> a = authorities(roles);
            for (String required : List.of("CUSTOMER", "ORGANIZER", "ADMIN", "SUPER_ADMIN", "FINANCE")) {
                PlatformRole target = PlatformRole.valueOf(required);
                boolean actual = PlatformRole.heldByAuthority(a).contains(PlatformRole.SUPER_ADMIN)
                        || PlatformRole.anySatisfies(PlatformRole.heldByAuthority(a), target)
                        && target != PlatformRole.SUPER_ADMIN;
                assertThat(actual).as("%s required, holding %s", required, a)
                        .isEqualTo(referenceDirective(required, a));
            }
        }
    }

    @Test
    @DisplayName("The staff rank rule covers exactly what it used to")
    void staffRankAgrees() {
        for (Set<PlatformRole> roles : everyCombination()) {
            Set<String> a = authorities(roles);
            for (String required : List.of("SUPER_ADMIN", "ADMIN", "FINANCE", "ORGANIZER")) {
                assertThat(StaffRole.covers(a, required)).as("%s required, holding %s", required, a)
                        .isEqualTo(referenceCovers(a, required));
            }
        }
    }

    @Test
    @DisplayName("Only an exact upper-case ROLE_ authority counts at a gate; a bare or lower-case name does not")
    void gateReadsExactAuthorities() {
        assertThat(PlatformRole.heldByAuthority(Set.of("ADMIN", "role_admin", "ROLE_admin", "ROLE_ADMIN ")))
                .isEmpty();
        assertThat(PlatformRole.heldByAuthority(Set.of("ROLE_ADMIN"))).containsExactly(PlatformRole.ADMIN);
    }

    @Test
    @DisplayName("A name that is not a platform role grants nothing and is ignored")
    void unknownNamesAreIgnored() {
        assertThat(Permission.grantedByPlatformRoles(List.of("ROLE_ROOT", "", "  "))).isEmpty();
        assertThat(PlatformRole.fromName(null)).isEmpty();
        assertThat(PlatformRole.fromName("")).isEmpty();
    }
}
