package com.pml.identity.auth.web;

import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.security.SecurityConfig;
import com.pml.shared.testing.TestClock;
import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The development-only endpoint that shows captured sign-in codes: the contact travels in the body,
 * only a caller holding the internal write scope on the loopback interface and not behind a proxy
 * is answered, and the endpoint does not exist unless capture is switched on.
 */
@Tag("L3")
@Tag("ET-IDN-001")
@DisplayName("the captured-codes endpoint is body-only, loopback-only, scope-gated and absent without capture")
class DevCapturedMessagesControllerTest {

    private static final String AUDIENCE = "myticketzm-identity-service";
    private static final String PATH = "/api/internal/auth/dev/captured/search";
    private static final String PHONE = "+260971234567";

    private static StubIssuer realm;
    private static WebTestClient client;
    private static volatile InetSocketAddress remote;

    @BeforeAll
    static void start() {
        realm = StubIssuer.start("myticketzm");
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "issuerUri", realm.issuer());
        ReflectionTestUtils.setField(config, "trustedIssuersCsv", "");
        ReflectionTestUtils.setField(config, "keycloakClientId", AUDIENCE);
        ReflectionTestUtils.setField(config, "expectedAudiencesCsv", AUDIENCE);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of());

        CapturedMessages captured = new CapturedMessages();
        CapturingProvider provider = new CapturingProvider(captured, TestClock.frozenAt(Instant.parse("2026-10-04T08:00:00Z")));
        provider.send(DeliveryChannel.WHATSAPP, PHONE, "482913").block();
        provider.send(DeliveryChannel.EMAIL, "someone@example.com", "100200").block();

        client = WebTestClient.bindToController(new DevCapturedMessagesController(captured))
                .webFilter(new WebFilterChainProxy(
                        config.securityWebFilterChain(ServerHttpSecurity.http(), new org.springframework.mock.env.MockEnvironment())))
                .webFilter((exchange, chain) -> chain.filter(exchange.mutate().request(
                        new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public InetSocketAddress getRemoteAddress() {
                                return remote;
                            }
                        }).build()))
                .configureClient().build();
    }

    @AfterAll
    static void stop() {
        realm.close();
    }

    private static WebTestClient.ResponseSpec call(String scope, InetSocketAddress from, Map<String, String> headers, Object body) {
        remote = from;
        return client.post().uri(PATH).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    if (scope != null) {
                        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + realm.scopedToken(AUDIENCE, scope));
                    }
                    headers.forEach(h::set);
                })
                .bodyValue(body).exchange();
    }

    private static InetSocketAddress loopback() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 50000);
    }

    private static InetSocketAddress elsewhere() throws Exception {
        return new InetSocketAddress(InetAddress.getByName("10.20.30.40"), 50000);
    }

    @Test
    @DisplayName("a loopback caller with the write scope gets the codes for the recipient named in the body")
    void answersLoopbackWithScope() {
        call("internal-write", loopback(), Map.of(), Map.of("recipient", PHONE, "limit", 5))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].code").isEqualTo("482913")
                .jsonPath("$[0].channel").isEqualTo("WHATSAPP");
    }

    @Test
    @DisplayName("the contact is never read from the URL: a recipient in the query string filters nothing")
    void queryStringRecipientIsIgnored() {
        remote = loopback();
        client.post().uri(PATH + "?recipient=" + PHONE.replace("+", "%2B")).contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + realm.scopedToken(AUDIENCE, "internal-write")))
                .bodyValue(Map.of()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("the old GET with the recipient in the URL is gone")
    void getIsNotServed() {
        remote = loopback();
        client.get().uri("/api/internal/auth/dev/captured?recipient=" + PHONE.replace("+", "%2B"))
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + realm.scopedToken(AUDIENCE, "internal-read")))
                .exchange().expectStatus().is4xxClientError();
    }

    @Test
    @DisplayName("a caller that is not on the loopback interface is refused")
    void refusesRemoteCaller() throws Exception {
        call("internal-write", elsewhere(), Map.of(), Map.of()).expectStatus().isForbidden();
    }

    @Test
    @DisplayName("a request carrying X-Forwarded-For, Forwarded or X-Real-IP is refused even from loopback")
    void refusesForwardedRequests() {
        for (Map<String, String> headers : List.of(Map.of("X-Forwarded-For", "203.0.113.9"),
                Map.of("Forwarded", "for=203.0.113.9"), Map.of("X-Real-IP", "203.0.113.9"))) {
            call("internal-write", loopback(), headers, Map.of()).expectStatus().isForbidden();
        }
    }

    @Test
    @DisplayName("the write scope is required: the read scope, another scope and no token are refused")
    void requiresScope() {
        call("internal-read", loopback(), Map.of(), Map.of()).expectStatus().isForbidden();
        call("profile email", loopback(), Map.of(), Map.of()).expectStatus().isForbidden();
        call(null, loopback(), Map.of(), Map.of()).expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("a limit above the page-size maximum is refused, not reduced")
    void oversizedLimitIsRefused() {
        call("internal-write", loopback(), Map.of(), Map.of("limit", 5000)).expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("the endpoint exists only when capture is switched on")
    void absentWithoutCapture() {
        ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
                .withBean(CapturedMessages.class)
                .withUserConfiguration(DevCapturedMessagesController.class);

        runner.run(context -> assertThat(context).doesNotHaveBean(DevCapturedMessagesController.class));
        runner.withPropertyValues("identity.delivery.capture.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DevCapturedMessagesController.class));
        runner.withPropertyValues("identity.delivery.capture.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevCapturedMessagesController.class));
    }
}
