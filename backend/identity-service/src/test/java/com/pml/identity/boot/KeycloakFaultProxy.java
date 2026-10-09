package com.pml.identity.boot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * A small reverse proxy in front of the real Keycloak that can answer HTTP 503 on demand, so the admin client
 * meets a genuine 5xx instead of a refused connection. {@link #failBefore} answers 503 without forwarding
 * (Keycloak never saw the request); {@link #failAfter} forwards the request and then throws the answer
 * away (the write happened, the caller was told it failed - the crash-after-the-write case).
 */
final class KeycloakFaultProxy implements AutoCloseable {

    private static final Set<String> HOP_BY_HOP = Set.of("host", "connection", "content-length", "transfer-encoding", "keep-alive",
            "upgrade", "expect");

    private final HttpServer server;
    private final String target;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger failBefore = new AtomicInteger();
    private final AtomicInteger failAfter = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private volatile Predicate<String> matches = request -> true;
    private final AtomicInteger forwarded = new AtomicInteger();

    KeycloakFaultProxy(int port, String target) throws IOException {
        this.target = target;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    /** The next {@code count} requests matching {@code "METHOD path"} get a 503 and never reach Keycloak. */
    void failBefore(int count, Predicate<String> requests) {
        this.matches = requests;
        failBefore.set(count);
    }

    /** The next {@code count} matching requests are forwarded, executed by Keycloak, and answered 503. */
    void failAfter(int count, Predicate<String> requests) {
        this.matches = requests;
        failAfter.set(count);
    }

    int failures() {
        return failed.get();
    }

    int forwarded() {
        return forwarded.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String signature = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        byte[] body = exchange.getRequestBody().readAllBytes();
        boolean candidate = matches.test(signature);
        if (candidate && failBefore.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            failed.incrementAndGet();
            respond(exchange, 503, new byte[0]);
            return;
        }
        boolean dropAnswer = candidate && failAfter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target + exchange.getRequestURI()))
                    .method(exchange.getRequestMethod(), body.length == 0
                            ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase())) {
                    values.forEach(value -> request.header(name, value));
                }
            });
            HttpResponse<byte[]> answer = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            forwarded.incrementAndGet();
            if (dropAnswer) {
                failed.incrementAndGet();
                respond(exchange, 503, new byte[0]);
                return;
            }
            answer.headers().map().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase()) && !name.startsWith(":")) {
                    values.forEach(value -> exchange.getResponseHeaders().add(name, value));
                }
            });
            respond(exchange, answer.statusCode(), answer.body());
        } catch (Exception unreachable) {
            respond(exchange, 502, new byte[0]);
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
