package com.pml.keycloak.identity;

/** Transport failure, timeout, missing credentials or a failed token fetch. Fail closed. */
public class IdentityUnavailableException extends RuntimeException {
    public IdentityUnavailableException(String message) {
        super(message);
    }

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
