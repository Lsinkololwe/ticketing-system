package com.pml.identity.authorization;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * What a person may hand to, or take from, someone else: only permissions they hold themselves,
 * only catalogue permissions, and none of a broader scope than the place they are being set. A
 * denial is held to the same rule as a grant, so nobody can strip a permission they could not use.
 */
public final class DelegationPolicy {

    private DelegationPolicy() {
    }

    /**
     * Parses {@code codes} into catalogue permissions, refusing an unknown code and any code whose
     * scope is broader than {@code target}. Scope order is platform, organization, event: a later
     * scope is narrower.
     */
    public static Set<Permission> requested(Collection<String> codes, Permission.Scope target) {
        EnumSet<Permission> requested = EnumSet.noneOf(Permission.class);
        if (codes == null) {
            return requested;
        }
        for (String code : codes) {
            Permission permission = Permission.fromCode(code).orElseThrow(() ->
                    new TranslatedRefusal(ErrorCode.PERMISSION_UNKNOWN, "no permission is named " + code));
            if (permission.scope().ordinal() < target.ordinal()) {
                throw new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                        permission.code() + " cannot be granted at " + target.name().toLowerCase() + " level");
            }
            requested.add(permission);
        }
        return requested;
    }

    /** The refusal for the requested permissions the actor does not hold, if there are any. */
    public static Optional<TranslatedRefusal> missing(Set<Permission> requested, Set<Permission> held) {
        EnumSet<Permission> missing = EnumSet.noneOf(Permission.class);
        missing.addAll(requested);
        missing.removeAll(held);
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                "cannot grant permissions you do not hold: " + Permission.codes(missing)));
    }
}
