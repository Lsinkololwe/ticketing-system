package com.pml.shared.security.revocation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The presented token has been revoked — logout, ban, admin revoke or compromise response.
 *
 * <p>401 rather than 403: the credential itself is no longer valid, so the client should
 * re-authenticate rather than conclude it lacks permission.</p>
 */
public class TokenRevokedException extends ResponseStatusException {

    private final transient RevocationType matchedType;

    public TokenRevokedException(RevocationType matchedType) {
        super(HttpStatus.UNAUTHORIZED, "Your session has been revoked. Please sign in again.");
        this.matchedType = matchedType;
    }

    public TokenRevokedException() {
        this(null);
    }

    public RevocationType getMatchedType() {
        return matchedType;
    }
}
