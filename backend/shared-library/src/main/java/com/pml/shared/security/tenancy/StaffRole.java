package com.pml.shared.security.tenancy;

import com.pml.shared.security.Authorities;
import com.pml.shared.security.PlatformRole;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The roles that make someone platform staff: the people who work on the platform itself rather
 * than inside one organizer's organization.
 *
 * <p>Administrators and super administrators are staff who may read across every organizer. A
 * finance officer is staff too, and works across organizers on payouts, refunds and the buyers
 * behind them; whether a given service also treats finance as tenant-wide for its own collections
 * is that service's configuration (booking does, identity and catalog do not). Keeping the three
 * in one named set lets the platform answer "is this person staff, and which kind" in one place
 * and record the answer the same way for all three, whatever a service has configured.
 *
 * <p>{@link #covers} is the rank rule for approvals: a super administrator may do anything an
 * administrator may, and an administrator may do anything a finance officer may. It is a pure
 * comparison of role names and reads no request, so it can sit inside any rule that has to be
 * unit-testable.
 */
public enum StaffRole {

    ADMIN(PlatformRole.ADMIN),
    SUPER_ADMIN(PlatformRole.SUPER_ADMIN),
    FINANCE(PlatformRole.FINANCE);

    private final PlatformRole platformRole;

    StaffRole(PlatformRole platformRole) {
        this.platformRole = platformRole;
    }

    /** The Spring authority name, for example {@code ROLE_FINANCE}. */
    public String authority() {
        return platformRole.authority();
    }

    /** The platform role this staff role is. */
    public PlatformRole platformRole() {
        return platformRole;
    }

    /** Every staff role: the set a staff-aware check uses unless it names a narrower one. */
    public static Set<StaffRole> all() {
        return EnumSet.allOf(StaffRole.class);
    }

    /**
     * Whether the held authorities satisfy {@code required}.
     *
     * <p>A bare role name such as {@code FINANCE} is not an authority and never matches; only the
     * {@code ROLE_}-prefixed form does. A super administrator covers every role, an administrator
     * covers finance, and any other required role must be held exactly.
     *
     * @param required a role name without the {@code ROLE_} prefix, as the action declares it
     */
    public static boolean covers(Collection<String> held, String required) {
        if (held == null || required == null) {
            return false;
        }
        if (held.contains(SUPER_ADMIN.authority())) {
            return true;
        }
        if (SUPER_ADMIN.name().equals(required)) {
            return false;
        }
        if (FINANCE.name().equals(required)) {
            return held.contains(FINANCE.authority()) || held.contains(ADMIN.authority());
        }
        return held.contains(Authorities.role(required));
    }
}
