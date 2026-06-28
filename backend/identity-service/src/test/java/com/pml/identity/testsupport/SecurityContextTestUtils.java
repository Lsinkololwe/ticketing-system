package com.pml.identity.testsupport;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.util.context.Context;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * Test support for the reactive security context.
 *
 * <p>Resolvers and services extract the authenticated user via
 * {@link com.pml.shared.security.SecurityContextUtils}, which reads from
 * {@link ReactiveSecurityContextHolder}. Unit tests therefore inject identity by
 * writing a {@link JwtAuthenticationToken} into the Reactor context with
 * {@link reactor.core.publisher.Mono#contextWrite(Context)} /
 * {@link reactor.core.publisher.Flux#contextWrite(Context)}.</p>
 *
 * <pre>
 * StepVerifier.create(
 *     resolver.applyToBeOrganizer(input)
 *         .contextWrite(SecurityContextTestUtils.withUser(USER_ID)))
 *     .assertNext(...)
 *     .verifyComplete();
 * </pre>
 */
public final class SecurityContextTestUtils {

    private SecurityContextTestUtils() {
    }

    /** Build a minimal JWT carrying the given subject (user id). */
    public static Jwt jwt(String userId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId)
                .claim("email", userId + "@example.com")
                .build();
    }

    /**
     * Reactor context that authenticates the given user with the supplied roles.
     *
     * @param userId JWT subject claim
     * @param roles  role names without the {@code ROLE_} prefix (e.g. "ORGANIZER")
     */
    public static Context withUser(String userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt(userId), authorities);
        return ReactiveSecurityContextHolder.withAuthentication(authentication);
    }
}
