package com.pml.shared.security.revocation;

/**
 * The Redis keys a revocation is cached under.
 *
 * <p>The gateway's session blacklist reads the same keys, so the formats are a contract between
 * processes: changing one here without the gateway leaves a revoked token accepted at the edge.
 *
 * <pre>
 * token   pml:blacklist:{jti}
 * session pml:session:{sid}
 * user    pml:revoked:{sub}
 * </pre>
 */
public final class RevocationKeys {

    public static final String TOKEN_PREFIX = "pml:blacklist:";
    public static final String SESSION_PREFIX = "pml:session:";
    public static final String USER_PREFIX = "pml:revoked:";

    private RevocationKeys() {
    }

    public static String token(String jti) {
        return TOKEN_PREFIX + jti;
    }

    public static String session(String sid) {
        return SESSION_PREFIX + sid;
    }

    public static String user(String sub) {
        return USER_PREFIX + sub;
    }
}
