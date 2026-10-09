package com.pml.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resolves the application user id (the value stored as {@code User._id}) from a token.
 *
 * <p>Keycloak ignores a client supplied id on user creation, so for buyer accounts the token
 * {@code sub} is the Keycloak user id and NOT the account id. Keycloak therefore emits an
 * {@code accountId} claim (mapper on the user attribute of the same name). Staff and legacy
 * users have no such attribute, and for them {@code sub} is the user id.</p>
 *
 * <p>Revocation (jti, sid and the per-user kill switch) deliberately keeps using {@code sub}
 * and {@code sid}: those identify Keycloak sessions, not application accounts.</p>
 */
public final class AccountIdentity {

    public static final String CLAIM = "accountId";

    private AccountIdentity() {
    }

    /** The account id claim if present and non-blank, else {@code sub}, else {@code null}. */
    public static String userIdOf(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object claim = jwt.getClaims().get(CLAIM);
        if (claim instanceof String s && !s.isBlank()) {
            return s;
        }
        String subject = jwt.getSubject();
        return subject != null && !subject.isBlank() ? subject : null;
    }
}
