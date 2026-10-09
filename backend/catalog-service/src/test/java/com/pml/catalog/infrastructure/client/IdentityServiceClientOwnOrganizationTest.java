package com.pml.catalog.infrastructure.client;

import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.security.InternalServiceWebClients;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identity refuses an authorization question that names no organization ("Organization ID is
 * required"), which made every event save and picture upload fail. A question about "my own
 * organization" is completed from the caller's memberships before it is asked.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002 · an authorization check that names no organization is sent for the caller's own")
class IdentityServiceClientOwnOrganizationTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ownerOrganizationIsFilledIn() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/authorization/user-organizations", exchange -> {
            byte[] json = ("{\"organizations\":[{\"organizationId\":\"org-team\",\"role\":\"MANAGER\",\"isActive\":true},"
                    + "{\"organizationId\":\"org-own\",\"role\":\"OWNER\",\"isActive\":true}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.createContext("/api/internal/authorization/check", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] json = "{\"authorized\":true,\"organizationId\":\"org-own\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();

        IdentityServiceClient client = new IdentityServiceClient(
                InternalServiceWebClients.unauthenticated(WebClient.builder()), "http://127.0.0.1:" + server.getAddress().getPort());

        var result = client.checkAuthorization(AuthorizationRequest.builder().userId("u1").requiredPermission("event:create").build()).block();

        assertThat(result.isAuthorized()).isTrue();
        assertThat(body.get()).contains("\"organizationId\":\"org-own\"");
    }
}
