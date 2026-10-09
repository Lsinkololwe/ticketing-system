package com.pml.keycloak.identity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Authenticated JSON client for the identity-service internal API.
 *
 * <ul>
 *   <li>Client-credentials token, cached until shortly before expiry, refreshed once on a 401.</li>
 *   <li>Fail closed: with any credential missing {@link #fromEnvironment} returns {@code null} and
 *       callers must refuse to authenticate; there is no unauthenticated mode.</li>
 *   <li>Short timeouts, no response bodies and no request payloads in logs.</li>
 * </ul>
 */
public class IdentityHttp {

    private static final Logger LOG = Logger.getLogger(IdentityHttp.class);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TOKEN_SKEW = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record Settings(String baseUrl, String tokenUrl, String clientId, String clientSecret,
                           Duration connectTimeout, Duration requestTimeout) {
        public Settings(String baseUrl, String tokenUrl, String clientId, String clientSecret) {
            this(baseUrl, tokenUrl, clientId, clientSecret, CONNECT_TIMEOUT, REQUEST_TIMEOUT);
        }
    }

    /** Result of a call: status and decoded body (null when empty). */
    public record Reply<T>(int status, T body) {}

    private final Settings settings;
    private final Clock clock;
    private final HttpClient http;

    private final Object tokenLock = new Object();
    private String token;
    private Instant tokenExpiry = Instant.MIN;

    public IdentityHttp(Settings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Names of the missing variables, empty when complete. Values are never reported. */
    public static List<String> missing(Map<String, String> env) {
        List<String> missing = new ArrayList<>();
        for (String name : List.of("IDENTITY_BASE_URL", "IDENTITY_CLIENT_ID", "IDENTITY_CLIENT_SECRET",
                "KEYCLOAK_TOKEN_URL")) {
            String v = env.get(name);
            if (v == null || v.isBlank()) {
                missing.add(name);
            }
        }
        return missing;
    }

    /** @return a client, or {@code null} (fail closed) when any variable is missing. */
    public static IdentityHttp fromEnvironment(Map<String, String> env, Clock clock) {
        if (!missing(env).isEmpty()) {
            return null;
        }
        String base = env.get("IDENTITY_BASE_URL").trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new IdentityHttp(new Settings(base, env.get("KEYCLOAK_TOKEN_URL").trim(),
                env.get("IDENTITY_CLIENT_ID").trim(), env.get("IDENTITY_CLIENT_SECRET")), clock);
    }

    public <T> Reply<T> post(String path, Object body, Class<T> type, Map<String, String> headers) {
        return send("POST", path, body, type, headers);
    }

    public <T> Reply<T> post(String path, Object body, Class<T> type) {
        return send("POST", path, body, type, Map.of());
    }

    public <T> Reply<T> get(String path, Class<T> type) {
        return send("GET", path, null, type, Map.of());
    }

    private <T> Reply<T> send(String method, String path, Object body, Class<T> type,
                              Map<String, String> headers) {
        for (int attempt = 0; ; attempt++) {
            String bearer = bearer();
            HttpResponse<byte[]> response = execute(method, path, body, bearer, headers);
            if (response.statusCode() == 401 && attempt == 0) {
                invalidate(bearer);
                continue;
            }
            return decode(response, type);
        }
    }

    private HttpResponse<byte[]> execute(String method, String path, Object body, String bearer,
                                         Map<String, String> headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
                    .timeout(settings.requestTimeout())
                    .header("Authorization", "Bearer " + bearer)
                    .header("Accept", "application/json, application/problem+json");
            headers.forEach(b::header);
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body)));
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new IdentityUnavailableException("identity-service timed out on " + method + " " + path);
        } catch (IOException e) {
            throw new IdentityUnavailableException("identity-service unreachable on " + method + " " + path
                    + " (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdentityUnavailableException("interrupted calling identity-service");
        }
    }

    private <T> Reply<T> decode(HttpResponse<byte[]> response, Class<T> type) {
        int status = response.statusCode();
        byte[] raw = response.body();
        if (status >= 200 && status < 300) {
            try {
                T value = (raw == null || raw.length == 0 || type == Void.class)
                        ? null : MAPPER.readValue(raw, type);
                return new Reply<>(status, value);
            } catch (IOException e) {
                throw new IdentityUnavailableException("identity-service sent an unreadable " + status + " body");
            }
        }
        Dto.Problem problem = null;
        try {
            if (raw != null && raw.length > 0) {
                problem = MAPPER.readValue(raw, Dto.Problem.class);
            }
        } catch (IOException ignored) {
            // not a problem document; status alone is reported
        }
        throw new IdentityApiException(status, problem);
    }

    // ---- token --------------------------------------------------------------------------------

    private String bearer() {
        synchronized (tokenLock) {
            if (token != null && clock.instant().isBefore(tokenExpiry.minus(TOKEN_SKEW))) {
                return token;
            }
            fetchToken();
            return token;
        }
    }

    private void invalidate(String used) {
        synchronized (tokenLock) {
            if (used.equals(token)) {
                token = null;
                tokenExpiry = Instant.MIN;
            }
        }
    }

    private void fetchToken() {
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(settings.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(settings.clientSecret(), StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.tokenUrl()))
                    .timeout(settings.requestTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                LOG.warnf("Token endpoint answered %d for the identity client", response.statusCode());
                throw new IdentityUnavailableException("token endpoint answered " + response.statusCode());
            }
            Map<?, ?> json = MAPPER.readValue(response.body(), Map.class);
            Object access = json.get("access_token");
            if (!(access instanceof String s) || s.isBlank()) {
                throw new IdentityUnavailableException("token response without access_token");
            }
            Object ttl = json.get("expires_in");
            long seconds = ttl instanceof Number n ? n.longValue() : 60;
            token = s;
            tokenExpiry = clock.instant().plusSeconds(seconds);
        } catch (HttpTimeoutException e) {
            throw new IdentityUnavailableException("token endpoint timed out");
        } catch (IOException e) {
            throw new IdentityUnavailableException("token endpoint unreachable (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdentityUnavailableException("interrupted fetching token");
        }
    }
}
