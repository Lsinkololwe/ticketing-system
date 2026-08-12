package com.pml.shared.security.revocation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Neither the cache nor the durable store could say whether the token is revoked, and the
 * requested operation is too sensitive to proceed without knowing.
 *
 * <p>This is the fail-closed path, and it is deliberately loud. 503 with {@code Retry-After}
 * tells the client this is a transient infrastructure condition rather than a permission
 * problem, so the organizer sees "try again shortly" instead of "access denied" and support
 * does not spend an afternoon chasing a phantom authorization bug.</p>
 *
 * <p>Reaching this state at all should page someone — see
 * {@code identity_revocation_unavailable_total}.</p>
 */
public class RevocationUnavailableException extends ResponseStatusException {

    private final transient String operation;

    public RevocationUnavailableException(String operation) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "This action is temporarily unavailable because the session-revocation service "
                        + "cannot be reached. Please try again in a moment.");
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}
