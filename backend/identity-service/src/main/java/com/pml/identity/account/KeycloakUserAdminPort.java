package com.pml.identity.account;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * The id-keyed Keycloak operations the account services use beyond {@link KeycloakAccountPort}:
 * ending sessions, changing realm roles, enabling or disabling, creating platform staff, and reading
 * a user of either realm for the sync. Every method addresses the user by Keycloak id; nothing looks
 * a user up by email or phone.
 *
 * <p>A {@code realm} of {@code null} means the buyer realm. Platform staff live in their own realm.</p>
 */
public interface KeycloakUserAdminPort {

    /** A Keycloak user as the sync needs it. Email and names are the staff's own, never a buyer's contact. */
    record KeycloakUserView(String id, String username, String email, String firstName, String lastName,
                            boolean enabled, boolean emailVerified, Set<String> realmRoles) {
        @Override
        public String toString() {
            return "KeycloakUserView[" + id + ", " + username + "]";
        }
    }

    /** Ends every SSO session of the user; a user Keycloak no longer holds has none. */
    Mono<Void> endSessions(String realm, String keycloakUserId);

    /** One of a person's live Keycloak sessions. */
    record SessionView(String id, String ipAddress, java.time.Instant startedAt, java.time.Instant lastAccessAt,
                       java.util.List<String> clients) {
    }

    /** The user's live sessions; none by default, so a port that cannot list them reports an empty list. */
    default reactor.core.publisher.Flux<SessionView> sessions(String realm, String keycloakUserId) {
        return reactor.core.publisher.Flux.empty();
    }

    /** Ends one SSO session by its id (the token's {@code sid}); a session already gone is already ended. */
    Mono<Void> endSession(String realm, String sessionId);

    /** Grants a realm role, raising when Keycloak refuses or is unreachable. */
    Mono<Void> grantRealmRole(String realm, String keycloakUserId, String roleName);

    /** Revokes a realm role; a user or role Keycloak no longer holds is already revoked. */
    Mono<Void> revokeRealmRole(String realm, String keycloakUserId, String roleName);

    /** Enables or disables the user, sending the full representation. */
    Mono<Void> setEnabled(String realm, String keycloakUserId, boolean enabled);

    /** Reads a user of {@code realm}; empty when Keycloak holds no user with that id. */
    Mono<KeycloakUserView> readUser(String realm, String keycloakUserId);

    /** The first value of a user attribute, empty when the user or the attribute is absent. */
    Mono<Optional<String>> readAttribute(String realm, String keycloakUserId, String name);

    /**
     * Creates a platform staff user in the staff realm: enabled, with UPDATE_PASSWORD and
     * CONFIGURE_TOTP required at first sign-in. A 409 reads the user back by username.
     *
     * @return the Keycloak user id
     */
    Mono<String> createStaffUser(String username, String email, String firstName, String lastName,
                                 String temporaryPassword);

    default Mono<Void> endSessions(String keycloakUserId) {
        return endSessions(null, keycloakUserId);
    }

    default Mono<Void> grantRealmRole(String keycloakUserId, String roleName) {
        return grantRealmRole(null, keycloakUserId, roleName);
    }

    default Mono<Void> revokeRealmRole(String keycloakUserId, String roleName) {
        return revokeRealmRole(null, keycloakUserId, roleName);
    }
}
