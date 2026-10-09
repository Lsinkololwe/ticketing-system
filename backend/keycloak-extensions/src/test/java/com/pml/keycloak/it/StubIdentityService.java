package com.pml.keycloak.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** HTTP stub of the identity-service internal API (CONTRACT section 4). Test double, not a mock. */
final class StubIdentityService implements AutoCloseable {

    static final String GOOD_CODE = "123456";
    private static final ObjectMapper JSON = new ObjectMapper();

    final HttpServer server;
    final String expectedAzp;
    /** contact value -> accountId used by ensure. */
    final Map<String, String> accounts = new ConcurrentHashMap<>();
    /** handle -> accountId (single use). */
    final Map<String, String> handles = new ConcurrentHashMap<>();
    final Map<String, String> challenges = new ConcurrentHashMap<>();
    final Map<String, String> proofs = new ConcurrentHashMap<>();
    final List<JsonNode> syncEvents = new CopyOnWriteArrayList<>();
    final List<String> calls = new CopyOnWriteArrayList<>();
    final List<String> unauthenticated = new CopyOnWriteArrayList<>();
    volatile boolean ensureSetsHandle = false;
    volatile String ensureIssueHandleSeen = "";

    StubIdentityService(String expectedAzp) throws IOException {
        this.expectedAzp = expectedAzp;
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        server.createContext("/api/internal/auth/challenges/verify", ex -> handle(ex, this::verify));
        server.createContext("/api/internal/auth/challenges", ex -> handle(ex, this::challenge));
        server.createContext("/api/internal/auth/accounts/ensure", ex -> handle(ex, this::ensure));
        server.createContext("/api/internal/auth/handles/redeem", ex -> handle(ex, this::redeem));
        server.createContext("/api/internal/keycloak/sync/event", ex -> handle(ex, b -> {
            syncEvents.add(b);
            return new Reply(202, "{}");
        }));
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    record Reply(int status, String body) {}

    private interface Handler {
        Reply apply(JsonNode body);
    }

    private void handle(HttpExchange ex, Handler handler) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            calls.add(path);
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (!authorised(auth)) {
                unauthenticated.add(path);
                send(ex, new Reply(401, "{\"errorCode\":\"UNAUTHORIZED\"}"));
                return;
            }
            byte[] raw = ex.getRequestBody().readAllBytes();
            JsonNode body = raw.length == 0 ? JSON.createObjectNode() : JSON.readTree(raw);
            send(ex, handler.apply(body));
        } catch (RuntimeException e) {
            send(ex, new Reply(500, "{}"));
        }
    }

    /** The bearer must be a real Keycloak-issued JWT of the plugin's client. */
    private boolean authorised(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        try {
            String[] parts = header.substring(7).split("\\.");
            JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            return expectedAzp.equals(claims.path("azp").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private static void send(HttpExchange ex, Reply r) throws IOException {
        byte[] out = r.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", r.status() >= 400 ? "application/problem+json" : "application/json");
        ex.sendResponseHeaders(r.status(), out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private Reply challenge(JsonNode b) {
        String contact = b.path("contact").path("value").asText();
        if (contact.startsWith("locked")) {
            return new Reply(423, "{\"errorCode\":\"OTP_LOCKED\",\"lockedUntil\":\"2030-01-01T10:30:00Z\"}");
        }
        String id = UUID.randomUUID().toString();
        challenges.put(id, contact);
        return new Reply(202, "{\"challengeId\":\"" + id + "\",\"contactType\":\"EMAIL\",\"maskedContact\":\"j***@example.com\","
                + "\"channel\":\"EMAIL\",\"expiresInSeconds\":300,\"resendAfterSeconds\":60}");
    }

    private Reply verify(JsonNode b) {
        String contact = challenges.get(b.path("challengeId").asText());
        if (contact == null) {
            return new Reply(410, "{\"errorCode\":\"OTP_EXPIRED\"}");
        }
        if (!GOOD_CODE.equals(b.path("code").asText())) {
            return new Reply(400, "{\"errorCode\":\"OTP_INVALID\",\"attemptsRemaining\":4}");
        }
        String proof = UUID.randomUUID().toString();
        proofs.put(proof, contact);
        return new Reply(200, "{\"proof\":\"" + proof + "\",\"contactType\":\"EMAIL\",\"maskedContact\":\"j***@example.com\",\"expiresInSeconds\":120}");
    }

    private Reply ensure(JsonNode b) {
        ensureIssueHandleSeen = b.path("issueHandle").asText();
        String contact = proofs.get(b.path("proof").asText());
        if (contact == null) {
            return new Reply(400, "{\"errorCode\":\"PROOF_INVALID\"}");
        }
        String account = accounts.get(contact);
        if (account == null) {
            return new Reply(200, "{\"accountId\":\"" + UUID.randomUUID() + "\",\"status\":\"ACTIVE\",\"isNew\":true}");
        }
        return new Reply(200, "{\"accountId\":\"" + account + "\",\"status\":\"ACTIVE\",\"isNew\":false}");
    }

    private Reply redeem(JsonNode b) {
        String account = handles.remove(b.path("handle").asText());
        if (account == null) {
            return new Reply(400, "{\"errorCode\":\"LOGIN_HANDLE_INVALID\"}");
        }
        return new Reply(200, "{\"accountId\":\"" + account + "\"}");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static List<String> noCalls() {
        return Collections.emptyList();
    }
}
