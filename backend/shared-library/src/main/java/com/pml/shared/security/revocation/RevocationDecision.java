package com.pml.shared.security.revocation;

/**
 * The outcome of a revocation check.
 *
 * <p>The three states are distinct because "the token is fine" and "no store could answer" call
 * for different handling. Read-only endpoints may proceed on {@link #UNKNOWN}; operations
 * annotated {@link FailClosedOnRevocation} refuse it.</p>
 */
public enum RevocationDecision {

    /** A revocation record was found. The request must be rejected. */
    REVOKED,

    /** Every store answered, and none of them holds a revocation for this token. */
    ACTIVE,

    /**
     * Neither the cache nor the durable store could answer, or the token carries no identifier
     * to check. Revocation is unenforceable for this request and the caller applies its policy.
     */
    UNKNOWN;

    /** True when the caller may proceed under a fail-closed policy. */
    public boolean isDefinitivelyActive() {
        return this == ACTIVE;
    }
}
