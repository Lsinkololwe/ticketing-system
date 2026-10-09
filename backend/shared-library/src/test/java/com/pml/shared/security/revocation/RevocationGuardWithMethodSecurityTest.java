package com.pml.shared.security.revocation;

import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aspect inside the proxy chain it runs in for real: reactive method security
 * ({@code @PreAuthorize}) and {@code @FailClosedOnRevocation} on the same DGS mutation, the shape of
 * every sensitive resolver. The earlier unit tests built the proxy by hand with the aspect alone, so
 * the "Required to bind 2 arguments" failure that only appears next to Spring Security's interceptors
 * was never exercised.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("RevocationGuardAspect works beside @PreAuthorize on a DGS mutation under reactive method security")
class RevocationGuardWithMethodSecurityTest {

    static final AtomicReference<RevocationDecision> DECISION = new AtomicReference<>(RevocationDecision.ACTIVE);

    public static class Resolver {
        @DgsMutation
        @PreAuthorize("hasRole('ADMIN')")
        @FailClosedOnRevocation("test.approve")
        public Mono<String> approve(@InputArgument String id, @InputArgument Double rate) {
            return Mono.just("approved " + id + " " + rate);
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableReactiveMethodSecurity
    static class Config {
        @Bean
        Resolver resolver() {
            return new Resolver();
        }

        @Bean
        RevocationGuardAspect aspect() {
            RevocationCheck check = (jti, sid, sub) -> Mono.just(DECISION.get());
            return new RevocationGuardAspect(new SensitiveOperationGuard(check, new RevocationProperties(),
                    new RevocationMetrics(new SimpleMeterRegistry(), Clock.systemUTC())));
        }
    }

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);

    @AfterEach
    void close() {
        DECISION.set(RevocationDecision.ACTIVE);
        context.close();
    }

    private static Context signedIn(String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("u-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }

    private Resolver resolver() {
        return context.getBean(Resolver.class);
    }

    @Test
    @DisplayName("an admin with an active token reaches the mutation, arguments intact")
    void activeRuns() {
        assertThat(resolver().approve("o-1", 7.5).contextWrite(signedIn("ROLE_ADMIN")).block())
                .isEqualTo("approved o-1 7.5");
    }

    @Test
    @DisplayName("a revoked token is refused as revoked")
    void revokedRefused() {
        DECISION.set(RevocationDecision.REVOKED);
        assertThatThrownBy(() -> resolver().approve("o-1", null).contextWrite(signedIn("ROLE_ADMIN")).block())
                .isInstanceOf(TokenRevokedException.class);
    }

    @Test
    @DisplayName("an unknown revocation state fails closed")
    void unknownFailsClosed() {
        DECISION.set(RevocationDecision.UNKNOWN);
        assertThatThrownBy(() -> resolver().approve("o-1", null).contextWrite(signedIn("ROLE_ADMIN")).block())
                .isInstanceOf(RevocationUnavailableException.class);
    }

    @Test
    @DisplayName("authorization failure surfaces as access denied, not masked by the revocation check")
    void authorizationFirst() {
        DECISION.set(RevocationDecision.UNKNOWN);
        assertThatThrownBy(() -> resolver().approve("o-1", null).contextWrite(signedIn("ROLE_USER")).block())
                .isInstanceOf(AccessDeniedException.class);
    }
}
