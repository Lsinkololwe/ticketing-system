package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * What a Keycloak token's claims become as Spring authorities, and that a hostile or broken
 * token cannot turn the conversion into an exception.
 *
 * <p>The converter runs on every authenticated request before any access decision. An exception
 * from it is a 500 for a caller who sent a malformed claim, and the shape of the failure is
 * attacker-chosen, so the malformed cases are pinned one claim at a time: a failure then names
 * the one shape that broke.</p>
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Keycloak claims become authorities, and malformed claims never throw")
class KeycloakGrantedAuthoritiesConverterTest {

    private static Jwt tokenWith(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("u-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static List<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("realm_access.roles become ROLE_ authorities")
    void realmRolesBecomeRoleAuthorities() {
        Jwt jwt = tokenWith(Map.of("realm_access", Map.of("roles", List.of("ADMIN", "FINANCE"))));

        assertThat(names(new KeycloakGrantedAuthoritiesConverter().convert(jwt)))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_FINANCE");
    }

    @Test
    @DisplayName("Keycloak's built-in roles are not turned into authorities")
    void builtInRolesAreSkipped() {
        Jwt jwt = tokenWith(Map.of("realm_access", Map.of("roles",
                List.of("offline_access", "uma_authorization", "default-roles-myticketzm", "CUSTOMER"))));

        assertThat(names(new KeycloakGrantedAuthoritiesConverter().convert(jwt)))
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("resource_access.<client>.roles become ROLE_ authorities for the configured client only")
    void clientRolesBecomeRoleAuthoritiesForTheConfiguredClient() {
        Jwt jwt = tokenWith(Map.of("resource_access", Map.of(
                "web", Map.of("roles", List.of("ORGANIZER")),
                "other", Map.of("roles", List.of("SUPER_ADMIN")))));

        assertThat(names(new KeycloakGrantedAuthoritiesConverter("web").convert(jwt)))
                .as("a role held on a different client must not be granted")
                .containsExactly("ROLE_ORGANIZER");
    }

    @Test
    @DisplayName("without a configured client id, client roles are not read at all")
    void clientRolesAreIgnoredWithoutAClientId() {
        Jwt jwt = tokenWith(Map.of("resource_access", Map.of("web", Map.of("roles", List.of("ADMIN")))));

        assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("a space-separated scope claim becomes one SCOPE_ authority per scope")
    void scopeClaimBecomesScopeAuthorities() {
        Jwt jwt = tokenWith(Map.of("scope", "a b"));

        assertThat(names(new KeycloakGrantedAuthoritiesConverter().convert(jwt)))
                .containsExactlyInAnyOrder("SCOPE_a", "SCOPE_b");
    }

    @Test
    @DisplayName("realm roles, client roles and scopes are combined")
    void allThreeSourcesAreCombined() {
        Jwt jwt = tokenWith(Map.of(
                "scope", "internal-read",
                "realm_access", Map.of("roles", List.of("ADMIN")),
                "resource_access", Map.of("web", Map.of("roles", List.of("ORGANIZER")))));

        assertThat(names(new KeycloakGrantedAuthoritiesConverter("web").convert(jwt)))
                .containsExactlyInAnyOrder("SCOPE_internal-read", "ROLE_ADMIN", "ROLE_ORGANIZER");
    }

    @Test
    @DisplayName("absent claims yield no authorities")
    void absentClaimsYieldNothing() {
        Jwt jwt = tokenWith(Map.of("preferred_username", "someone"));

        assertThat(new KeycloakGrantedAuthoritiesConverter("web").convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("a blank scope claim yields no authorities")
    void blankScopeYieldsNothing() {
        Jwt jwt = tokenWith(Map.of("scope", "   "));

        assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("realm_access without a roles entry yields no authorities")
    void realmAccessWithoutRolesYieldsNothing() {
        Jwt jwt = tokenWith(Map.of("realm_access", Map.of("something", "else")));

        assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("realm_access.roles that is not a list does not throw and grants nothing")
    void rolesThatIsNotAListDoesNotThrow() {
        Jwt jwt = tokenWith(Map.of("realm_access", Map.of("roles", "ADMIN")));

        assertThatCode(() -> {
            assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a realm_access claim that is not an object does not throw and grants nothing")
    void realmAccessThatIsNotAnObjectDoesNotThrow() {
        Jwt jwt = tokenWith(Map.of("realm_access", "ADMIN"));

        assertThatCode(() -> {
            assertThat(new KeycloakGrantedAuthoritiesConverter().convert(jwt)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a role list holding a non-string does not throw and never grants a role that was not a string")
    void nonStringRoleElementDoesNotThrow() {
        Jwt jwt = tokenWith(Map.of("realm_access", Map.of("roles", List.of(42, "CUSTOMER"))));

        assertThatCode(() -> {
            assertThat(names(new KeycloakGrantedAuthoritiesConverter().convert(jwt)))
                    .doesNotContain("ROLE_ADMIN");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a client entry that is not an object does not throw and grants nothing")
    void clientEntryThatIsNotAnObjectDoesNotThrow() {
        Jwt jwt = tokenWith(Map.of("resource_access", Map.of("web", "ADMIN")));

        assertThatCode(() -> {
            assertThat(new KeycloakGrantedAuthoritiesConverter("web").convert(jwt)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a resource_access claim that is not an object does not throw and grants nothing")
    void resourceAccessThatIsNotAnObjectDoesNotThrow() {
        Jwt jwt = tokenWith(Map.of("resource_access", List.of("ADMIN")));

        assertThatCode(() -> {
            assertThat(new KeycloakGrantedAuthoritiesConverter("web").convert(jwt)).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a scope claim that is a list rather than a string does not throw")
    void scopeThatIsAListDoesNotThrow() {
        Jwt jwt = tokenWith(Map.of("scope", List.of("a", "b")));

        assertThatCode(() -> new KeycloakGrantedAuthoritiesConverter().convert(jwt))
                .doesNotThrowAnyException();
    }
}
