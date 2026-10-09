package com.pml.booking.security;

import com.pml.shared.security.ServiceSecurity;
import com.pml.shared.testing.InternalSurface;
import com.pml.shared.testing.InternalSurface.Endpoint;
import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every internal endpoint of the booking service is closed to callers without an internal scope.
 *
 * <p>The endpoints are discovered from the controllers, so one added later is covered without
 * touching this test; each is run through the service's real security chain with signed tokens.</p>
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("every booking internal endpoint answers 401, 403 and 200 as its scope dictates")
class InternalSurfaceTest {

    private static final String AUDIENCE = "booking-service";

    private static StubIssuer realm;
    private static WebTestClient client;

    @BeforeAll
    static void start() {
        realm = StubIssuer.start("myticketzm");
        ServiceSecurity security = new ServiceSecurity(realm.issuer(), "", AUDIENCE, AUDIENCE, List.of());
        client = InternalSurface.clientBehind(security.securityWebFilterChain(InternalSurface.http()));
    }

    @AfterAll
    static void stop() {
        realm.close();
    }

    @Test
    @DisplayName("no internal path is reachable without the right token")
    void everyInternalPathIsScopeGated() {
        List<Endpoint> endpoints = InternalSurface.discover(
                Path.of("src/main/java/com/pml/booking/web"), Path.of("src/main/java"));
        assertThat(endpoints).as("the controllers must expose an internal surface for this test to mean anything").isNotEmpty();

        List<String> violations = InternalSurface.violations(client, realm, AUDIENCE, endpoints,
                endpoint -> endpoint.method() == HttpMethod.GET ? "internal-read" : "internal-write");

        assertThat(violations).isEmpty();
    }
}
