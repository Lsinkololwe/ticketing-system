package com.pml.shared.security.publicop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.security.ServiceSecurity;
import com.pml.shared.testing.RedisNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared filter inside the shared resource-server chain, over a real Redis: what a tokenless caller can reach
 * is decided by the allowlist and nothing about the endpoint behind it.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007-R9 · through the real security chain a tokenless caller reaches only the allowlist, rate limited")
class PublicGraphQlSecurityTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int LIMIT = 5;
    private static AnnotationConfigApplicationContext context;
    private static WebTestClient client;

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class Wiring {

        @Bean
        ServiceSecurity serviceSecurity() {
            return new ServiceSecurity("http://localhost:1/realms/none", "", "svc", "", List.of(), false);
        }

        @Bean
        SecurityWebFilterChain chain(ServerHttpSecurity http, ServiceSecurity security) {
            return security.securityWebFilterChain(http);
        }

        @Bean
        PublicGraphQlFilter publicFilter() {
            LettuceConnectionFactory factory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration(RedisNode.host(), RedisNode.port()));
            factory.afterPropertiesSet();
            ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
            // First use of the driver loads classes and resources; do that here, off the reactive threads.
            template.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>("return 1", Long.class),
                    List.of("warm"), List.of()).blockLast();
            return new PublicGraphQlFilter(
                    PublicOperationPolicy.of("it-" + UUID.randomUUID(), Set.of("events")).withLimit(LIMIT, java.time.Duration.ofMinutes(1)),
                    template, JSON, null, TrustedProxies.parse("10.0.0.0/8"));
        }

        @Bean
        org.springframework.web.server.WebFilter routerPeer() {
            return new com.pml.shared.testing.RouterPeerFilter();
        }

        /** Whatever reaches the endpoint answers 200, so a 401 can only have come from the chain. */
        @Bean
        RouterFunction<ServerResponse> graphql() {
            return RouterFunctions.route().POST("/graphql", request ->
                    ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue("{\"data\":{\"events\":[]}}")).build();
        }
    }

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Wiring.class);
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    private static WebTestClient.ResponseSpec post(String query, String forwardedFor) {
        return client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor)
                .bodyValue(Map.of("query", query)).exchange();
    }

    @Test
    @DisplayName("the allowlisted query is served; introspection, a mutation, a mixed selection, entities and a batch are 401")
    void allowlistOnly() {
        post("{ events { id } }", "198.51.100.1").expectStatus().isOk();
        for (String refused : new String[] {
                "{ __schema { types { name } } }",
                "mutation { events }",
                "{ events { id } secrets { id } }",
                "{ _service { sdl } }",
                "{ _entities(representations: []) { __typename } }" }) {
            post(refused, "198.51.100.2").expectStatus().isUnauthorized();
        }
        client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"query\":\"{ events { id } }\"}]").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("a request that carries a token is not waved through by the filter")
    void tokenIsNotPublic() {
        client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer not-a-jwt").bodyValue(Map.of("query", "{ events { id } }"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("the request after the limit is 429 with Retry-After; another address is unaffected")
    void rateLimited() {
        String caller = "192.0.2." + (int) (Math.random() * 200 + 10);
        for (int i = 0; i < LIMIT; i++) {
            post("{ events { id } }", "1.1.1.1, " + caller).expectStatus().isOk();
        }
        post("{ events { id } }", "9.9.9.9, " + caller).expectStatus().isEqualTo(429).expectHeader().valueEquals("Retry-After", "60");
        post("{ events { id } }", "1.1.1.1, 192.0.2.250").expectStatus().isOk();
    }

    @Test
    @DisplayName("Redis unreachable: the public query is still served")
    void failsOpen() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        dead.afterPropertiesSet();
        PublicGraphQlFilter filter = new PublicGraphQlFilter(PublicOperationPolicy.of("down", Set.of("events")),
                new ReactiveStringRedisTemplate(dead), JSON, null);
        WebTestClient direct = WebTestClient.bindToRouterFunction(
                RouterFunctions.route().POST("/graphql", r -> ServerResponse.ok().bodyValue("{}")).build())
                .webFilter(filter).build();
        direct.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "{ events { id } }")).exchange().expectStatus().isOk();
        assertThat(filter.policy().service()).isEqualTo("down");
    }
}
