package com.pml.shared.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service-to-service calls carry the caller's own client-credentials token.
 *
 * <p>{@code /api/internal/**} refuses a request without one, so a client built without the token
 * filter fails only in a deployed environment — every unit test mocks the client away. This drives
 * a real exchange: WireMock plays both the token endpoint and the called service.
 */
@Tag("L5")
@Tag("ET-PLT-007")
@DisplayName("Internal calls authenticate as the calling service")
class InternalServiceWebClientsTest {

    private static WireMockServer server;

    @BeforeAll
    static void start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @BeforeEach
    void stubs() {
        server.resetAll();
        server.stubFor(post(urlEqualTo("/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"svc-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
        server.stubFor(get(urlEqualTo("/api/internal/ping")).willReturn(aResponse().withBody("pong")));
    }

    private ReactiveClientRegistrationRepository registrations() {
        return new InMemoryReactiveClientRegistrationRepository(ClientRegistration
                .withRegistrationId("booking-service")
                .clientId("myticketzm-booking-service")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(server.baseUrl() + "/token")
                .build());
    }

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration.class,
                    InternalServiceClientAutoConfiguration.class))
            .withPropertyValues("spring.application.name=booking-service");

    @Test
    @DisplayName("with this service's registration, every call carries its bearer token")
    void callsCarryTheServiceToken() {
        runner.withBean(ReactiveClientRegistrationRepository.class, this::registrations).run(context -> {
            WebClient identity = context.getBean(InternalServiceWebClients.class).to(server.baseUrl());

            assertThat(identity.get().uri("/api/internal/ping").retrieve().bodyToMono(String.class).block())
                    .isEqualTo("pong");
            server.verify(getRequestedFor(urlEqualTo("/api/internal/ping"))
                    .withHeader("Authorization", equalTo("Bearer svc-token")));
        });
    }

    @Test
    @DisplayName("the token is fetched once and reused until it expires")
    void tokenIsCached() {
        runner.withBean(ReactiveClientRegistrationRepository.class, this::registrations).run(context -> {
            WebClient identity = context.getBean(InternalServiceWebClients.class).to(server.baseUrl());
            identity.get().uri("/api/internal/ping").retrieve().bodyToMono(String.class).block();
            identity.get().uri("/api/internal/ping").retrieve().bodyToMono(String.class).block();

            server.verify(1, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/token")));
        });
    }

    @Test
    @DisplayName("with no registration the call goes out bare — the called service refuses it, visibly")
    void noRegistrationSendsNoToken() {
        runner.run(context -> {
            WebClient identity = context.getBean(InternalServiceWebClients.class).to(server.baseUrl());
            identity.get().uri("/api/internal/ping").retrieve().bodyToMono(String.class).block();

            server.verify(getRequestedFor(urlEqualTo("/api/internal/ping")).withHeader("Authorization", absent()));
        });
    }
}
