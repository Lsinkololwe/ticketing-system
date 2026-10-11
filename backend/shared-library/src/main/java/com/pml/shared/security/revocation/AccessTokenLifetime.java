package com.pml.shared.security.revocation;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

/**
 * The expiry stage of the token pipeline: signature, then lifetime, then revocation.
 *
 * <p>The decoder already rejects an expired token, but only with its own timestamp tolerance and
 * only where a decoder is configured. This stage restates the rule at the point of use against
 * the platform clock, with the same {@link #TOLERANCE} the decoder uses,
 * so an expired token is refused without costing a revocation lookup and the same rule holds in
 * the gateway, in each service and in the per-operation guard.</p>
 */
public final class AccessTokenLifetime {

    /**
     * How far past {@code exp} a token is still honoured, for clock drift between the issuer and
     * this process. The decoder uses the same value, so the stage here never admits what the
     * decoder would refuse.
     */
    public static final Duration TOLERANCE = Duration.ofSeconds(30);

    private AccessTokenLifetime() {
    }

    /**
     * True when the token's {@code exp} lies before {@code now} by more than the tolerance.
     * A token with no {@code exp} is not judged here: the decoder refuses one at the door
     * ({@code MultiIssuerJwtResolver} requires the claim), so this stage only ever sees tokens
     * whose lifetime is stated.
     */
    public static boolean isExpired(Jwt jwt, Instant now, Duration tolerance) {
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt != null && expiresAt.plus(tolerance).isBefore(now);
    }

    /** True when {@code nbf} is still in the future beyond the tolerance. */
    public static boolean isNotYetValid(Jwt jwt, Instant now, Duration tolerance) {
        Instant notBefore = jwt.getNotBefore();
        return notBefore != null && notBefore.minus(tolerance).isAfter(now);
    }
}
