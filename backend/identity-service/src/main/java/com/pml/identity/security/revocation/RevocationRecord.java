package com.pml.identity.security.revocation;

import com.pml.identity.persistence.IdentityCollections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.pml.shared.security.revocation.RevocationType;

import java.time.Instant;

/**
 * The durable record of a revoked token, session or user.
 *
 * <p>MongoDB holds the system of record for revocations and Redis caches it, so losing the cache
 * costs a slower lookup rather than a missed revocation.</p>
 *
 * <h2>Expiry</h2>
 * <p>{@code expiresAt} carries a MongoDB TTL index — {@code expireAfter = "0s"} deletes the
 * document once that instant passes. Reads additionally filter on {@code expiresAt} because the
 * TTL monitor sweeps roughly once a minute and may leave an expired document briefly
 * readable.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = IdentityCollections.TOKEN_REVOCATIONS)
@TypeAlias("token_revocations")
public class RevocationRecord {

    /** {@code TYPE:value} — see {@link RevocationType#documentId(String)}. */
    @Id
    private String id;

    private RevocationType type;

    /** The raw claim value: the {@code jti}, {@code sid} or {@code sub}. */
    private String value;

    /** Free text for the audit trail, e.g. {@code "logout"}, {@code "admin-revoke"}. */
    private String reason;

    /** Who performed the revocation — a user id, an admin id, or {@code "system"}. */
    private String revokedBy;

    private Instant revokedAt;

    /**
     * When every token this revocation could match has expired. Set to at least the
     * access-token lifespan past {@code revokedAt}.
     */
    private Instant expiresAt;

    /** True when this record still covers tokens that could be presented now. */
    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
