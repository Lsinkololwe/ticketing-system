package com.pml.shared.security;

import java.util.Locale;
import java.util.Set;

/**
 * The authority strings Spring Security sees, written once. A role reaches a service as
 * {@code ROLE_<NAME>} and an internal caller as a {@code SCOPE_internal-*} scope or one of the
 * service roles, so any rule that names one takes it from here rather than spelling it.
 */
public final class Authorities {

    public static final String ROLE_PREFIX = "ROLE_";

    public static final String ROLE_SUPER_ADMIN = ROLE_PREFIX + "SUPER_ADMIN";
    public static final String ROLE_ADMIN = ROLE_PREFIX + "ADMIN";
    public static final String ROLE_FINANCE = ROLE_PREFIX + "FINANCE";

    public static final String SCOPE_INTERNAL_READ = "SCOPE_internal-read";
    public static final String SCOPE_INTERNAL_WRITE = "SCOPE_internal-write";
    public static final String ROLE_INTERNAL = ROLE_PREFIX + "INTERNAL";
    public static final String ROLE_INTERNAL_SERVICE = ROLE_PREFIX + "INTERNAL_SERVICE";
    public static final String ROLE_SYSTEM = ROLE_PREFIX + "SYSTEM";

    /** What may call an {@code internal-read} endpoint: the read scope or a service identity. */
    public static final String[] INTERNAL_READ = {SCOPE_INTERNAL_READ, ROLE_INTERNAL_SERVICE, ROLE_SYSTEM};

    /** What may call an {@code internal-write} endpoint: the write scope or a service identity. */
    public static final String[] INTERNAL_WRITE = {SCOPE_INTERNAL_WRITE, ROLE_INTERNAL_SERVICE, ROLE_SYSTEM};

    /** Authorities that mark a service-to-service caller, in any of its forms. */
    public static final Set<String> INTERNAL_CALLER = Set.of(
            ROLE_INTERNAL, ROLE_INTERNAL_SERVICE, SCOPE_INTERNAL_READ, SCOPE_INTERNAL_WRITE);

    private Authorities() {
    }

    /** The authority for a role name, whatever its case: {@code finance} becomes {@code ROLE_FINANCE}. */
    public static String role(String name) {
        return ROLE_PREFIX + name.trim().toUpperCase(Locale.ROOT);
    }

    /** The role name behind an authority or a bare name, upper-cased and without the {@code ROLE_} prefix. */
    public static String roleName(String authorityOrName) {
        String upper = authorityOrName.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith(ROLE_PREFIX) ? upper.substring(ROLE_PREFIX.length()) : upper;
    }
}
