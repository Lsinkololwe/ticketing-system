package com.pml.shared.security.tenancy;

import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Where a caller's active organization memberships come from.
 *
 * <p>The port exists because {@code shared-library} is a leaf — it may not reach
 * a service's {@code IdentityServiceClient}, and each service already has one.
 * Every implementation answers the same question and is held to the same rule
 * below.
 *
 * <h2>The contract on failure</h2>
 * An implementation that cannot reach identity-service <b>must signal an error</b>,
 * not return an empty set. The two are not interchangeable:
 *
 * <ul>
 *   <li>an empty set is a fact — this account belongs to no organization, so it
 *       reaches nothing, and every refusal is final;</li>
 *   <li>a failed lookup is the <em>absence</em> of that fact, and answering it
 *       with "no memberships" tells an organizer their own event does not exist
 *       whenever identity-service is briefly unwell.</li>
 * </ul>
 *
 * Both deny — this never fails open — but only the error is retryable, and only
 * the error can be reported honestly to the caller. The existing
 * {@code IdentityServiceClient.getUserOrganizations} swallows its failures into
 * {@code new UserOrganizationsResponse(List.of())}, which is precisely the
 * conflation this contract forbids; an implementation wrapping it has to undo
 * that before it can satisfy this interface.
 */
@FunctionalInterface
public interface TenantMemberships {

    /**
     * The ids of organizations where {@code subject} is an <b>active</b> member.
     *
     * @return the memberships, possibly empty; or an error if the lookup failed.
     *         Never an empty set standing in for a failure.
     */
    Mono<Set<String>> activeOrganizationIdsOf(String subject);
}
