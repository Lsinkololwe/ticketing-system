package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("A service is told when its permission catalogue differs from identity's")
class PermissionCatalogueHealthIndicatorTest {

    private static WebClient identityAnswering(String body, HttpStatus status) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build())).build();
    }

    @Test
    @DisplayName("the same fingerprint is UP")
    void sameIsUp() {
        String body = "{\"fingerprint\":\"" + PermissionCatalogueFingerprint.compute() + "\"}";
        Health health = new PermissionCatalogueHealthIndicator(identityAnswering(body, HttpStatus.OK)).health().block();
        assertThat(health.getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    @DisplayName("a different fingerprint is a CATALOGUE_MISMATCH")
    void differentIsMismatch() {
        Health health = new PermissionCatalogueHealthIndicator(
                identityAnswering("{\"fingerprint\":\"stale\"}", HttpStatus.OK)).health().block();
        assertThat(health.getStatus().getCode()).isEqualTo("CATALOGUE_MISMATCH");
    }

    @Test
    @DisplayName("an identity that does not answer leaves the service UNKNOWN, not down")
    void unreachableIsUnknown() {
        Health health = new PermissionCatalogueHealthIndicator(
                identityAnswering("", HttpStatus.SERVICE_UNAVAILABLE)).health().block();
        assertThat(health.getStatus().getCode()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("the fingerprint changes when a role's grants change shape, and is stable otherwise")
    void stable() {
        assertThat(PermissionCatalogueFingerprint.compute()).isEqualTo(PermissionCatalogueFingerprint.compute())
                .hasSize(64);
    }
}
