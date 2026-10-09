package com.pml.shared.security.revocation;

import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** On a resolver class, the annotation makes every mutation fail closed and leaves queries alone. */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("A sensitive resolver refuses its mutations when revocation cannot be checked")
class FailClosedOnRevocationClassTest {

    @FailClosedOnRevocation
    public static class PayoutResolver {
        @DgsMutation
        public Mono<String> approvePayout() {
            return Mono.just("approved");
        }

        @DgsQuery
        public Mono<String> payout() {
            return Mono.just("read");
        }
    }

    private static PayoutResolver proxied(RevocationDecision decision) {
        RevocationProperties properties = new RevocationProperties();
        RevocationMetrics metrics = new RevocationMetrics(new SimpleMeterRegistry(), Clock.systemUTC());
        RevocationCheck check = (jti, sid, sub) -> Mono.just(decision);
        AspectJProxyFactory factory = new AspectJProxyFactory(new PayoutResolver());
        factory.setProxyTargetClass(true);
        factory.addAspect(new RevocationGuardAspect(new SensitiveOperationGuard(check, properties, metrics)));
        return factory.getProxy();
    }

    private static Context signedIn() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    @DisplayName("store unreachable: the mutation is refused, the query still answers")
    void mutationFailsClosedQueryDoesNot() {
        PayoutResolver resolver = proxied(RevocationDecision.UNKNOWN);

        assertThatThrownBy(() -> resolver.approvePayout().contextWrite(signedIn()).block())
                .isInstanceOf(RevocationUnavailableException.class);
        assertThat(resolver.payout().contextWrite(signedIn()).block()).isEqualTo("read");
    }

    @Test
    @DisplayName("token revoked: the mutation is refused as revoked")
    void revokedIsRefused() {
        assertThatThrownBy(() -> proxied(RevocationDecision.REVOKED).approvePayout().contextWrite(signedIn()).block())
                .isInstanceOf(TokenRevokedException.class);
    }

    @Test
    @DisplayName("token active: the mutation runs")
    void activeRuns() {
        assertThat(proxied(RevocationDecision.ACTIVE).approvePayout().contextWrite(signedIn()).block())
                .isEqualTo("approved");
    }
}
