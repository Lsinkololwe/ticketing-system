package com.pml.identity.authorization;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.shared.security.Permission;

import java.time.Instant;
import java.util.Set;

/**
 * Everything one decision needs, as plain values: what the caller's platform roles grant, the
 * event grant and organization membership found for them, the organization and the instant. The
 * decider reads nothing else, so a decision can be reproduced from a context alone.
 *
 * <p>{@code grant}, {@code member} and {@code organization} are exactly what was found, not what
 * counts: the decider is the one place that decides whether a grant is valid, a member active, and
 * a grant the issuing organization's.
 *
 * @param platformGrants   permissions the caller's platform roles grant; empty when platform roles
 *                         are not being consulted
 * @param eventScoped      whether the decision is about one event, so a grant may apply
 * @param organizationId   the organization named by the caller, or {@code null}
 * @param grant            the caller's grant on the event, whatever its status, or {@code null}
 * @param member           the caller's membership in the organization, whatever its status, or {@code null}
 * @param organization     the organization, or {@code null} when it was not found
 * @param now              the instant grants are checked against
 */
public record DecisionContext(
        Set<Permission> platformGrants,
        boolean eventScoped,
        String organizationId,
        EventAccessGrant grant,
        OrganizationMember member,
        Organization organization,
        Instant now) {

    public DecisionContext {
        platformGrants = platformGrants == null ? Set.of() : Set.copyOf(platformGrants);
    }

    /** A context in which only platform roles were consulted: enough to answer a platform allow. */
    public static DecisionContext platformOnly(Set<Permission> platformGrants, boolean eventScoped,
                                               String organizationId, Instant now) {
        return new DecisionContext(platformGrants, eventScoped, organizationId, null, null, null, now);
    }
}
