package com.pml.identity.service;

import com.pml.identity.domain.model.User;
import reactor.core.publisher.Mono;

/**
 * Brings accounts in step with what Keycloak holds, without ever deciding who a person is.
 *
 * <ul>
 *   <li>Keycloak is read by id, in the realm the event came from; the event itself carries no profile
 *       data, so out-of-order events converge on the latest state.</li>
 *   <li>Roles come from the user's <em>realm roles</em> only. User attributes such as {@code roles} or
 *       {@code accountType} are user-editable in some flows and are never trusted.</li>
 *   <li>A Keycloak user with no account is adopted or created only for platform staff (the staff
 *       realm). A buyer's account is born by proving a contact, so a buyer-realm user nobody
 *       recognises is recorded as an orphan and left alone.</li>
 *   <li>Contacts are never written here, and nothing is hard-deleted: a Keycloak delete marks the
 *       account DELETED and releases its contacts.</li>
 * </ul>
 */
public interface UserSyncService {

    /**
     * Syncs one Keycloak user.
     *
     * @param realm the realm of the user; null or blank means the buyer realm
     * @return the account, or empty when Keycloak holds no such user or none is to be created
     */
    Mono<User> syncUser(String realm, String keycloakUserId);

    /** A Keycloak delete: the account is marked DELETED and its contacts released; an unknown user is ignored. */
    Mono<Void> markDeleted(String realm, String keycloakUserId);

    /** Stamps {@code lastLoginAt}; an unknown user is ignored. */
    Mono<User> updateLastLogin(String realm, String keycloakUserId);

    /** Records that a change could not be applied, so the failure is data and not only a log line. */
    Mono<Void> recordFailure(String realm, String keycloakUserId, String kind, String reason);
}
