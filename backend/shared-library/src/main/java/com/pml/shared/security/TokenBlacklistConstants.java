package com.pml.shared.security;

/**
 * Shared Redis Key Constants for Token/Session Blacklisting
 *
 * These constants are shared across ALL services (frontend and backend).
 * CRITICAL: These values MUST match the frontend implementation in:
 * frontend/web/apps/organization-admin/src/lib/auth/token-blacklist.ts
 *
 * <h2>Redis Key Structure</h2>
 * <pre>
 * Token Blacklist:   pml:blacklist:{jti}   → JSON metadata (TTL: 1 hour)
 * Session Blacklist: pml:session:{sid}     → "revoked" (TTL: token lifetime)
 * User Revocation:   pml:revoked:{sub}     → timestamp (TTL: 30 minutes)
 * </pre>
 *
 * <h2>Usage</h2>
 * <ul>
 *   <li>API Gateway: Primary blacklist check before forwarding requests</li>
 *   <li>Microservices: Defense-in-depth check before processing</li>
 *   <li>Frontend: Blacklist tokens on logout</li>
 * </ul>
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html">OWASP JWT Cheat Sheet</a>
 */
public final class TokenBlacklistConstants {

    private TokenBlacklistConstants() {
        // Prevent instantiation
    }

    // =========================================================================
    // TOKEN BLACKLIST (JTI-based)
    // =========================================================================

    /**
     * Redis key prefix for blacklisted tokens.
     * Key format: pml:blacklist:{jti}
     */
    public static final String BLACKLIST_PREFIX = "pml:blacklist:";

    /**
     * TTL for blacklisted tokens in seconds (1 hour).
     * After this time, Redis automatically deletes the entry.
     */
    public static final long BLACKLIST_TTL_SECONDS = 60 * 60; // 1 hour

    // =========================================================================
    // SESSION BLACKLIST (SID-based, Keycloak sessions)
    // =========================================================================

    /**
     * Redis key prefix for blacklisted sessions.
     * Key format: pml:session:{sid}
     */
    public static final String SESSION_PREFIX = "pml:session:";

    // =========================================================================
    // USER REVOCATION (SUB-based, "logout everywhere")
    // =========================================================================

    /**
     * Redis key prefix for user revocation entries.
     * Key format: pml:revoked:{sub}
     */
    public static final String USER_REVOCATION_PREFIX = "pml:revoked:";

    /**
     * TTL for user revocation entries in seconds (30 minutes).
     * Longer than typical access token lifetime to catch all tokens.
     */
    public static final long USER_REVOCATION_TTL_SECONDS = 30 * 60; // 30 minutes

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Build the full Redis key for a token JTI.
     *
     * @param jti The JWT ID claim from the access token
     * @return Full Redis key (e.g., "pml:blacklist:abc123")
     */
    public static String blacklistKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }

    /**
     * Build the full Redis key for a session.
     *
     * @param sid The session ID (Keycloak session)
     * @return Full Redis key (e.g., "pml:session:xyz789")
     */
    public static String sessionKey(String sid) {
        return SESSION_PREFIX + sid;
    }

    /**
     * Build the full Redis key for a user revocation entry.
     *
     * @param sub The subject (user ID) from the JWT
     * @return Full Redis key (e.g., "pml:revoked:user123")
     */
    public static String userRevocationKey(String sub) {
        return USER_REVOCATION_PREFIX + sub;
    }
}
