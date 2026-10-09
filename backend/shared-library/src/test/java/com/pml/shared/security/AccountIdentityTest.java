package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · the application user id is the accountId claim, falling back to sub")
class AccountIdentityTest {

    private static Jwt jwt(Map<String, Object> extra) {
        Map<String, Object> claims = new HashMap<>(extra);
        claims.putIfAbsent("sub", "kc-user-id");
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }

    @Test
    @DisplayName("accountId wins over sub")
    void accountIdWins() {
        assertThat(AccountIdentity.userIdOf(jwt(Map.of("accountId", "acc-1")))).isEqualTo("acc-1");
    }

    @Test
    @DisplayName("staff and legacy tokens without the claim use sub")
    void fallsBackToSub() {
        assertThat(AccountIdentity.userIdOf(jwt(Map.of()))).isEqualTo("kc-user-id");
    }

    @Test
    @DisplayName("a blank or non-string accountId claim is ignored")
    void blankClaimIgnored() {
        assertThat(AccountIdentity.userIdOf(jwt(Map.of("accountId", "  ")))).isEqualTo("kc-user-id");
        assertThat(AccountIdentity.userIdOf(jwt(Map.of("accountId", 42)))).isEqualTo("kc-user-id");
    }

    @Test
    @DisplayName("no claim and no sub is null, never an empty id")
    void nothingIsNull() {
        Jwt noSub = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("x", "y"));
        assertThat(AccountIdentity.userIdOf(noSub)).isNull();
        assertThat(AccountIdentity.userIdOf(null)).isNull();
    }

    @Test
    @DisplayName("the Keycloak converter names the authentication by account id, and sub is untouched for revocation")
    void converterNamesByAccountId() {
        Jwt token = jwt(Map.of("accountId", "acc-9", "preferred_username", "acc-9"));
        AbstractAuthenticationToken auth = KeycloakJwtAuthenticationConverter.reactiveConverter().convert(token).block();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("acc-9");
        assertThat(((Jwt) auth.getPrincipal()).getSubject()).isEqualTo("kc-user-id");

        Jwt staff = jwt(Map.of("preferred_username", "alice"));
        assertThat(KeycloakJwtAuthenticationConverter.reactiveConverter().convert(staff).block().getName())
                .isEqualTo("kc-user-id");
    }

    @Test
    @DisplayName("SecurityContextUtils.getCurrentUserId resolves the accountId claim")
    void currentUserIdUsesClaim() {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt(Map.of("accountId", "acc-3")), java.util.List.of());
        StepVerifier.create(SecurityContextUtils.getCurrentUserId()
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                reactor.core.publisher.Mono.just(new SecurityContextImpl(auth)))))
                .expectNext("acc-3").verifyComplete();

        JwtAuthenticationToken legacy = new JwtAuthenticationToken(jwt(Map.of()), java.util.List.of());
        StepVerifier.create(SecurityContextUtils.getCurrentUserId()
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                reactor.core.publisher.Mono.just(new SecurityContextImpl(legacy)))))
                .expectNext("kc-user-id").verifyComplete();
    }
}
