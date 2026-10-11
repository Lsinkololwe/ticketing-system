package com.pml.shared.security;

import org.springframework.core.env.Environment;

/**
 * Whether a service is running somewhere its JWT audience check must not be silently off.
 *
 * <p>Local development and the test suite both run against realms that may not have every
 * client's {@code oidc-audience-mapper} in place (a developer's own Keycloak, a stub issuer with
 * no mapper concept at all), so they are exempt. Anywhere else — a named deployment profile, or
 * no profile at all, which is the shape of a misconfigured deployment rather than a recognised
 * one — the check must be on, and {@link PlatformResourceServer#jwt} refuses to start when it
 * is not.</p>
 */
public final class AudienceRequirement {

    private AudienceRequirement() {
    }

    public static boolean outsideLocalOrTest(Environment environment) {
        return !environment.matchesProfiles("local", "test");
    }
}
