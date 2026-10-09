package com.pml.identity.infrastructure.keycloak;

/**
 * A Keycloak admin write that did not land.
 *
 * <p>Deliberately a plain runtime exception rather than an {@code IllegalStateException}: an
 * activity treats the latter as a refused business rule and does not retry it, while a group or
 * role write that failed is exactly the transient fault a retry budget exists for.
 */
public class KeycloakWriteFailed extends RuntimeException {

    public KeycloakWriteFailed(String message) {
        super(message);
    }
}
