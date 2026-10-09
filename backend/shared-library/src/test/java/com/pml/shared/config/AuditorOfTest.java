package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("The auditor recorded on a document")
class AuditorOfTest {

    private static Jwt jwt(String subject, String username) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(60));
        if (subject != null) {
            builder.subject(subject);
        }
        if (username != null) {
            builder.claim("preferred_username", username);
        }
        return builder.build();
    }

    @Test
    @DisplayName("the JWT subject, which is the Keycloak user id")
    void subject() {
        assertThat(PlatformAuditingAutoConfiguration.auditorOf(
                new JwtAuthenticationToken(jwt("user-42", "chanda"), List.of()))).isEqualTo("user-42");
    }

    @Test
    @DisplayName("a token without a subject falls back to its username, then to the principal's name")
    void fallbacks() {
        assertThat(PlatformAuditingAutoConfiguration.auditorOf(
                new JwtAuthenticationToken(jwt(null, "chanda"), List.of()))).isEqualTo("chanda");
        assertThat(PlatformAuditingAutoConfiguration.auditorOf(
                new TestingAuthenticationToken("svc-account", "n/a"))).isEqualTo("svc-account");
    }
}
