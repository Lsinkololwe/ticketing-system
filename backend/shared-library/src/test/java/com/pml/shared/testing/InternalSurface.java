package com.pml.shared.testing;

import com.pml.shared.testing.jwt.StubIssuer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Discovers every {@code /api/internal/**} endpoint a service declares and runs each one through the
 * service's real security chain.
 *
 * <p>Endpoints are found by reading the controllers themselves, so a path added tomorrow is covered
 * the day it is written. Each is then called three ways with genuinely signed tokens: with none
 * (401), with the most privileged <em>user</em> token there is, an administrator holding no internal
 * scope (403), and with the scope that is meant to reach it (200). A single smoke test of the
 * prefix would pass while one path sat in front of an earlier {@code permitAll} rule.</p>
 */
public final class InternalSurface {

    public record Endpoint(HttpMethod method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private InternalSurface() {
    }

    /** Every internal endpoint declared in the controllers under {@code sources}. */
    public static List<Endpoint> discover(Path sources, Path javaRoot) {
        List<Endpoint> endpoints = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Class<?> type = load(javaRoot.relativize(file));
                RequestMapping base = type.getAnnotation(RequestMapping.class);
                if (base == null) {
                    continue;
                }
                for (String prefix : base.value().length == 0 ? new String[]{""} : base.value()) {
                    for (Method method : type.getDeclaredMethods()) {
                        collect(prefix, method, endpoints);
                    }
                }
            }
        } catch (IOException unreadable) {
            throw new AssertionError("controller sources could not be read: " + sources, unreadable);
        }
        return endpoints.stream().filter(e -> e.path().startsWith("/api/internal")).toList();
    }

    private static void collect(String prefix, Method method, List<Endpoint> out) {
        add(prefix, HttpMethod.GET, paths(method.getAnnotation(GetMapping.class) == null ? null : method.getAnnotation(GetMapping.class).value()), method.isAnnotationPresent(GetMapping.class), out);
        add(prefix, HttpMethod.POST, paths(method.getAnnotation(PostMapping.class) == null ? null : method.getAnnotation(PostMapping.class).value()), method.isAnnotationPresent(PostMapping.class), out);
        add(prefix, HttpMethod.PUT, paths(method.getAnnotation(PutMapping.class) == null ? null : method.getAnnotation(PutMapping.class).value()), method.isAnnotationPresent(PutMapping.class), out);
        add(prefix, HttpMethod.DELETE, paths(method.getAnnotation(DeleteMapping.class) == null ? null : method.getAnnotation(DeleteMapping.class).value()), method.isAnnotationPresent(DeleteMapping.class), out);
        add(prefix, HttpMethod.PATCH, paths(method.getAnnotation(PatchMapping.class) == null ? null : method.getAnnotation(PatchMapping.class).value()), method.isAnnotationPresent(PatchMapping.class), out);
    }

    private static String[] paths(String[] declared) {
        return declared == null || declared.length == 0 ? new String[]{""} : declared;
    }

    private static void add(String prefix, HttpMethod verb, String[] suffixes, boolean present, List<Endpoint> out) {
        if (!present) {
            return;
        }
        for (String suffix : suffixes) {
            out.add(new Endpoint(verb, (prefix + suffix).replaceAll("\\{[^}/]+}", "x")));
        }
    }

    /** A client over {@code chain} that answers 200 to anything the chain lets through. */
    public static WebTestClient clientBehind(SecurityWebFilterChain chain) {
        RouterFunctions.Builder routes = RouterFunctions.route();
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH}) {
            routes.route(RequestPredicates.method(method).and(RequestPredicates.path("/**")),
                    request -> ServerResponse.ok().bodyValue("reached"));
        }
        return WebTestClient.bindToRouterFunction(routes.build())
                .webFilter(new WebFilterChainProxy(chain))
                .configureClient().build();
    }

    public static ServerHttpSecurity http() {
        return ServerHttpSecurity.http();
    }

    /**
     * @param audience the audience the service requires
     * @param scopeFor the scope that is meant to reach each endpoint
     * @return one line per violated expectation; empty when the surface is sound
     */
    public static List<String> violations(WebTestClient client, StubIssuer realm, String audience,
                                          List<Endpoint> endpoints, Function<Endpoint, String> scopeFor) {
        List<String> violations = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            expect(violations, endpoint, "no token", 401, call(client, endpoint, null));
            expect(violations, endpoint, "administrator user token without an internal scope", 403,
                    call(client, endpoint, realm.validToken(audience, "ADMIN", "SUPER_ADMIN", "FINANCE", "CUSTOMER")));
            expect(violations, endpoint, "user token carrying only profile scopes", 403,
                    call(client, endpoint, realm.scopedToken(audience, "openid profile email phone")));
            expect(violations, endpoint, "the internal scope meant for it", 200,
                    call(client, endpoint, realm.scopedToken(audience, scopeFor.apply(endpoint))));
            String wrongScope = "internal-read".equals(scopeFor.apply(endpoint)) ? "internal-write" : "internal-read";
            expect(violations, endpoint, "the internal scope meant for a different method", 403,
                    call(client, endpoint, realm.scopedToken(audience, wrongScope)));
        }
        return violations;
    }

    private static int call(WebTestClient client, Endpoint endpoint, String token) {
        return client.method(endpoint.method()).uri(endpoint.path())
                .headers(h -> {
                    if (token != null) {
                        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .exchange().returnResult(Void.class).getStatus().value();
    }

    private static void expect(List<String> violations, Endpoint endpoint, String caller, int expected, int actual) {
        if (actual != expected) {
            violations.add("%s with %s: expected %d, got %d".formatted(endpoint, caller, expected, actual));
        }
    }

    private static Class<?> load(Path relativeSource) {
        String name = relativeSource.toString().replace('/', '.').replaceAll("\\.java$", "");
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError missing) {
            throw new AssertionError("controller class not loadable: " + name, missing);
        }
    }
}
