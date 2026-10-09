package com.pml.shared.security.tenancy;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** identity-service's membership answer, as booking and catalog read it. */
@Tag("L5")
@Tag("ET-PLT-007")
@DisplayName("Tenancy is read from identity-service, and an outage is not an empty answer")
class RemoteTenantMembershipsTest {

    private static WireMockServer identity;
    private RemoteTenantMemberships memberships;

    @BeforeAll
    static void start() {
        identity = new WireMockServer(options().dynamicPort());
        identity.start();
    }

    @AfterAll
    static void stop() {
        identity.stop();
    }

    @BeforeEach
    void client() {
        identity.resetAll();
        memberships = new RemoteTenantMemberships(WebClient.builder().baseUrl(identity.baseUrl()).build());
    }

    @Test
    @DisplayName("only active memberships scope the caller")
    void activeMembershipsOnly() {
        identity.stubFor(get(urlPathEqualTo(RemoteTenantMemberships.PATH))
                .withQueryParam("userId", equalTo("user-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"organizations":[
                          {"organizationId":"org-a","organizationName":"A","role":"OWNER","isOwner":true,"isActive":true},
                          {"organizationId":"org-b","organizationName":"B","role":"MEMBER","isOwner":false,"isActive":false}
                        ]}""")));

        assertThat(memberships.activeOrganizationIdsOf("user-1").block()).containsExactly("org-a");
    }

    @Test
    @DisplayName("a failed lookup fails — it never becomes \"belongs to nothing\"")
    void outagePropagates() {
        identity.stubFor(get(urlPathEqualTo(RemoteTenantMemberships.PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> memberships.activeOrganizationIdsOf("user-1").block())
                .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    @DisplayName("no principal permits nothing, without asking")
    void blankSubjectAsksNothing() {
        assertThat(memberships.activeOrganizationIdsOf(" ").block()).isEmpty();
        assertThat(identity.getAllServeEvents()).isEmpty();
    }
}
