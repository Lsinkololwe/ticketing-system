package com.pml.identity.flowb.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A controllable HTTP hop placed between Keycloak and the Identity Service.
 *
 * <p>Flow B crosses a process boundary that is normally invisible to tests: the
 * {@code user-sync} SPI listener inside Keycloak calls
 * {@code POST /api/internal/keycloak/sync/user-data} on the Identity Service. This proxy
 * <em>is</em> that wire, which lets a test cut exactly one component and observe what the
 * rest of the system does — without mocking anything inside either process.</p>
 *
 * <p>The proxy also solves a bootstrapping problem: Keycloak needs
 * {@code IDENTITY_SERVICE_URL} at container-start time, but the Spring Boot test server
 * port is only known after the context refreshes. The proxy binds first, on a port that is
 * exposed to the container network, and is re-pointed at the app once its port is known.</p>
 *
 * <p>Not thread-confined: {@link #setMode(Mode)} takes effect for every subsequent request.</p>
 */
public final class FaultInjectingProxy implements AutoCloseable {

    /** How the proxy should behave for the next request. */
    public enum Mode {
        /** Forward verbatim to the target. */
        PASS_THROUGH,
        /** Answer 503 without contacting the target — models "Identity Service is down". */
        FAIL_503,
        /** Answer 500 without contacting the target — models "Identity Service errored". */
        FAIL_500,
        /** Accept the request and never answer — models a hung dependency. */
        HANG,
        /** Close the socket without a response — models a connection reset. */
        RESET
    }

    /** Headers that must not be copied through a proxy hop. */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "host", "connection", "content-length", "transfer-encoding",
            "keep-alive", "upgrade", "expect");

    private final HttpServer server;
    private final HttpClient client;
    private final int port;
    private final List<String> seenPaths = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger received = new AtomicInteger();
    private final AtomicInteger forwarded = new AtomicInteger();

    private volatile String target;
    private volatile Mode mode = Mode.PASS_THROUGH;
    private volatile Duration hangFor = Duration.ofSeconds(45);

    private FaultInjectingProxy(HttpServer server) {
        this.server = server;
        this.port = server.getAddress().getPort();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Binds on an ephemeral loopback+wildcard port and starts serving. */
    public static FaultInjectingProxy start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            FaultInjectingProxy proxy = new FaultInjectingProxy(server);
            server.createContext("/", proxy::handle);
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();
            return proxy;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the Flow B sync proxy", e);
        }
    }

    public int getPort() {
        return port;
    }

    /** @param target absolute base URL, e.g. {@code http://localhost:53211} */
    public void setTarget(String target) {
        this.target = target;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public void setHangFor(Duration hangFor) {
        this.hangFor = hangFor;
    }

    /** Total requests that arrived at the proxy, whatever the mode. */
    public int requestsReceived() {
        return received.get();
    }

    /** Requests actually relayed to the Identity Service. */
    public int requestsForwarded() {
        return forwarded.get();
    }

    public List<String> seenPaths() {
        synchronized (seenPaths) {
            return List.copyOf(seenPaths);
        }
    }

    /** Resets counters and returns to {@link Mode#PASS_THROUGH}. */
    public void reset() {
        received.set(0);
        forwarded.set(0);
        seenPaths.clear();
        mode = Mode.PASS_THROUGH;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------------
    // handler
    // ------------------------------------------------------------------

    private void handle(HttpExchange exchange) {
        received.incrementAndGet();
        seenPaths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        try (exchange) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Mode current = mode;
            String base = target;

            if (current == Mode.RESET) {
                // Closing the exchange without sendResponseHeaders drops the connection.
                return;
            }
            if (current == Mode.HANG) {
                sleepQuietly(hangFor);
                respond(exchange, 504, "{\"error\":\"proxy hang\"}");
                return;
            }
            if (current == Mode.FAIL_503 || base == null) {
                respond(exchange, 503, "{\"error\":\"identity-service unavailable\"}");
                return;
            }
            if (current == Mode.FAIL_500) {
                respond(exchange, 500, "{\"error\":\"identity-service failure\"}");
                return;
            }

            forwarded.incrementAndGet();
            relay(exchange, base, body);
        } catch (IOException e) {
            // The far side is a container; a broken pipe here is not a test failure signal.
            respondQuietly(exchange, 502, "{\"error\":\"proxy relay failed\"}");
        }
    }

    private void relay(HttpExchange exchange, String base, byte[] body) throws IOException {
        URI uri = URI.create(base + exchange.getRequestURI().toString());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .method(exchange.getRequestMethod(),
                        body.length == 0
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(body));

        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(v -> builder.header(name, v));
            }
        });

        try {
            HttpResponse<byte[]> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            response.headers().map().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                    values.forEach(v -> exchange.getResponseHeaders().add(name, v));
                }
            });
            byte[] out = response.body();
            exchange.sendResponseHeaders(response.statusCode(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            respond(exchange, 502, "{\"error\":\"proxy interrupted\"}");
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] out = body.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private void respondQuietly(HttpExchange exchange, int status, String body) {
        try {
            respond(exchange, status, body);
        } catch (IOException ignored) {
            // exchange already closed
        }
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Consumes and discards a stream; kept for symmetry with future streaming relays. */
    @SuppressWarnings("unused")
    private static void drain(InputStream in) throws IOException {
        in.readAllBytes();
    }
}
