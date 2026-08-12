package com.pml.shared.security.revocation;

import com.pml.shared.security.TokenBlacklistConstants;

/**
 * The three identifiers a JWT can be revoked by.
 *
 * <p>Each constant builds its own Redis cache key from {@link TokenBlacklistConstants}, which
 * is the single key layout shared by the API Gateway, the backend services and the frontend
 * applications.</p>
 */
public enum RevocationType {

    /** {@code jti} — a single access token. */
    TOKEN {
        @Override
        public String cacheKey(String value) {
            return TokenBlacklistConstants.blacklistKey(value);
        }
    },

    /** {@code sid} — every token minted for one Keycloak SSO session. */
    SESSION {
        @Override
        public String cacheKey(String value) {
            return TokenBlacklistConstants.sessionKey(value);
        }
    },

    /** {@code sub} — every token held by one user ("logout everywhere", ban, compromise). */
    USER {
        @Override
        public String cacheKey(String value) {
            return TokenBlacklistConstants.userRevocationKey(value);
        }
    };

    /** The Redis key this revocation is cached under. */
    public abstract String cacheKey(String value);

    /**
     * The MongoDB {@code _id} for this revocation.
     *
     * <p>Deriving the id from (type, value) makes {@code save} an idempotent upsert: revoking
     * the same token twice overwrites rather than duplicating, and the durable lookup is a
     * primary-key read rather than a query.</p>
     */
    public String documentId(String value) {
        return name() + ":" + value;
    }
}
