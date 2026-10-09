package com.pml.identity.account;

import com.pml.shared.constants.UserType;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * The id-keyed Keycloak admin operations the account workflow activities use (CONTRACT 12).
 * Nothing else writes Keycloak users. Implementations send a FULL user representation on every
 * update and treat a 409 on create as "read it back by username".
 */
public interface KeycloakAccountPort {

    /** What identity-service needs to know about a Keycloak user. */
    record KeycloakAccount(String keycloakUserId, String username, boolean enabled, boolean emailVerified) {
    }

    /** The attributes written to the user. {@code email} is null for an account with no email contact. */
    record AccountAttributes(String accountId, String email, boolean emailVerified, String displayName, String locale) {
    }

    /** Create the user with {@code username = accountId}; returns the Keycloak user id. Idempotent. */
    Mono<String> createUser(String accountId, boolean enabled, UserType role);

    /** Set attributes and realm roles on an existing user. Idempotent. */
    Mono<Void> applyAttributesAndRoles(String keycloakUserId, AccountAttributes attributes, Set<UserType> roles);

    /**
     * Sets the user's email and {@code emailVerified} from the account's email contact, or clears
     * both when the account has none. Sends the full representation; the username is never touched.
     */
    Mono<Void> setEmail(String keycloakUserId, String email, boolean emailVerified);

    /** Empty when no user has that username. */
    Mono<KeycloakAccount> findByUsername(String username);

    /** Enables or disables the user, sending the full representation. */
    Mono<Void> setEnabled(String keycloakUserId, boolean enabled);
}
