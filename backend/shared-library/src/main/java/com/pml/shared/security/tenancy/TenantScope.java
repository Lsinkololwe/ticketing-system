package com.pml.shared.security.tenancy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The organizations one caller may reach, resolved once per request.
 *
 * <h2>What this is for</h2>
 * OWASP A01:2021 — Broken Access Control, in its <b>CWE-639</b> form: an
 * authorization decision made on a role alone, against a resource named by a
 * caller-supplied id. {@code hasAnyRole('ORGANIZER')} answers "may this account
 * edit ticket tiers", which is not the question. The question is "may this
 * account edit <em>this</em> ticket tier", and no role can answer it.
 *
 * <h2>A set, not an id</h2>
 * A user may belong to several organizations. Modelling tenancy as one id forces
 * a choice at resolution time, and the existing {@code ActorOrganizationResolver}
 * shows what that choice becomes in practice — {@code organizations.get(0)}, with
 * a warning log and a javadoc admitting it is wrong. Carrying the whole set means
 * the query filters on {@code organizationId IN (…)} and no caller is silently
 * pinned to whichever membership sorted first.
 *
 * <h2>Empty means deny, and that is deliberate</h2>
 * {@link #denyAll} exists so a failure to establish tenancy has somewhere safe to
 * land. Every method here treats an empty set as permitting nothing. The one way
 * to widen access is {@link #platformAdmin()}, which is set from a realm role and
 * never from anything the caller supplies.
 *
 * @param subject         the authenticated principal, for logs only — never a filter
 * @param organizationIds active memberships; empty permits nothing
 * @param platformAdmin   true only for a platform-wide role, which bypasses the filter
 */
public record TenantScope(String subject, Set<String> organizationIds, boolean platformAdmin) {

    public TenantScope {
        organizationIds = organizationIds == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(organizationIds));
    }

    /** A scope that permits nothing. The safe landing place for any failure to resolve. */
    public static TenantScope denyAll(String subject) {
        return new TenantScope(subject, Set.of(), false);
    }

    public static TenantScope of(String subject, Collection<String> organizationIds) {
        return new TenantScope(subject, Set.copyOf(new LinkedHashSet<>(organizationIds)), false);
    }

    public static TenantScope platformAdministrator(String subject, Collection<String> organizationIds) {
        return new TenantScope(subject, Set.copyOf(new LinkedHashSet<>(organizationIds)), true);
    }

    /**
     * Whether this caller may reach a resource owned by {@code organizationId}.
     *
     * <p>A null or blank owner returns {@code false}. An unowned row is not public
     * data — it is a row whose ownership was never set, and guessing either way on
     * a security path is how a boundary acquires a hole nobody wrote down.
     */
    public boolean permits(String organizationId) {
        if (platformAdmin) {
            return true;
        }
        if (organizationId == null || organizationId.isBlank()) {
            return false;
        }
        return organizationIds.contains(organizationId);
    }

    /** True when this scope can reach nothing at all — no memberships and not an administrator. */
    public boolean permitsNothing() {
        return !platformAdmin && organizationIds.isEmpty();
    }

    @Override
    public String toString() {
        // No organization ids. This lands in logs and exception messages, and the
        // membership list of the caller is not something either needs to carry.
        return "TenantScope[subject=%s, organizations=%d, platformAdmin=%s]"
                .formatted(subject, organizationIds.size(), platformAdmin);
    }
}
