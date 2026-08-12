package com.pml.shared.security.revocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One (type, value) pair a token can be revoked by — a {@code jti}, {@code sid} or {@code sub}.
 *
 * @param type  which claim this value came from
 * @param value the raw claim value
 */
public record RevocationIdentifier(RevocationType type, String value) {

    public RevocationIdentifier {
        if (type == null) {
            throw new IllegalArgumentException("Revocation type is required");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Revocation value is required");
        }
    }

    /** The Redis key this identifier is cached under. */
    public String cacheKey() {
        return type.cacheKey(value);
    }

    /** The stable storage id for this identifier, usable as a document primary key. */
    public String storageId() {
        return type.documentId(value);
    }

    /**
     * Builds the identifier list for a token, skipping claims the token does not carry.
     *
     * <p>Used by every caller so that all three identifiers are always considered together.</p>
     */
    public static List<RevocationIdentifier> from(String jti, String sid, String sub) {
        List<RevocationIdentifier> identifiers = new ArrayList<>(3);
        addIfPresent(identifiers, RevocationType.TOKEN, jti);
        addIfPresent(identifiers, RevocationType.SESSION, sid);
        addIfPresent(identifiers, RevocationType.USER, sub);
        return identifiers;
    }

    private static void addIfPresent(List<RevocationIdentifier> target,
                                     RevocationType type, String value) {
        if (value != null && !value.isBlank()) {
            target.add(new RevocationIdentifier(type, value));
        }
    }

    /** Masked form for logs — never log a raw session or token identifier. */
    public String masked() {
        return type + ":" + mask(value);
    }

    static String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
