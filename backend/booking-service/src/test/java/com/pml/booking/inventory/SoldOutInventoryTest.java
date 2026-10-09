package com.pml.booking.inventory;

import com.pml.shared.security.InternalServiceWebClients;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.dto.InventoryReservationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * What the reservation path receives when a tier is sold out.
 *
 * <h2>Why this test exists</h2>
 * Catalog owns ticket inventory and booking reserves against it over
 * {@code POST /api/internal/inventory/tiers/{id}/reserve}. When the tier cannot satisfy the
 * request catalog answers <b>HTTP 409</b> with an {@code InventoryReservationResult} whose
 * {@code success} is false — a deliberate, well-formed refusal.
 *
 * <p>{@code CatalogServiceClient} handles 4xx by consuming that body, logging it, and returning
 * {@code Mono.empty()}. The caller, {@code ReservationServiceImpl.takeInventory}, acts on the
 * refusal inside {@code .flatMap(result -> …)} — and {@code flatMap} does not run on an empty
 * source. So the question this test settles is not a matter of reading the code: it is whether the
 * sold-out refusal reaches the caller at all, or is swallowed and the reservation allowed to
 * proceed against inventory nobody reserved.
 *
 * <p>Asserted against a real WireMock server rather than reasoned about, because the answer turns
 * on WebClient's {@code onStatus} contract — whether an empty Mono from the handler suppresses the
 * error and resumes the normal body path — and that is exactly the kind of framework detail this
 * corpus has been wrong about before.</p>
 */
@Tag("L5")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001 · a sold-out tier must refuse, not vanish")
class SoldOutInventoryTest {

    private static WireMockServer catalog;
    private static CatalogServiceClient client;

    @BeforeAll
    static void startCatalog() {
        catalog = new WireMockServer(options().dynamicPort());
        catalog.start();
        client = new CatalogServiceClient(
                InternalServiceWebClients.unauthenticated(WebClient.builder()),
                "http://localhost:" + catalog.port());
    }

    @AfterAll
    static void stopCatalog() {
        if (catalog != null) {
            catalog.stop();
        }
    }

    @Test
    @DisplayName("catalog's 409 sold-out refusal reaches the caller as success=false")
    void soldOutRefusalReachesTheCaller() {
        // Exactly what InternalInventoryController returns when a tier cannot satisfy the
        // request: 409, with a well-formed failure body. Nothing about this is malformed or
        // exceptional — it is the ordinary sold-out answer on a busy on-sale.
        catalog.stubFor(post(urlPathMatching("/api/internal/inventory/tiers/.*/reserve"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":false,"reservedQuantity":0,"availableQuantity":0,
                                 "errorMessage":"Insufficient inventory","tierId":"tier-1"}
                                """)));

        StepVerifier.create(client.reserveInventory("tier-1", 2, "res-1"))
                // The assertion that matters. An empty Mono here is not a benign "nothing to
                // report" — the caller acts on the refusal inside flatMap, which never runs on an
                // empty source, so the reservation completes as though the seats were held.
                .assertNext(result -> {
                    if (result == null || result.success()) {
                        throw new AssertionError(
                                "a sold-out tier must not report success: " + result);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a 5xx does raise, so the circuit breaker still sees real outages")
    void anOutageStillRaises() {
        catalog.resetAll();
        catalog.stubFor(post(urlPathMatching("/api/internal/inventory/tiers/.*/reserve"))
                .willReturn(aResponse().withStatus(503)));

        // The other half of the distinction, and it has to be asserted separately. A fix that
        // simply stopped raising would pass the sold-out test above while quietly disabling the
        // breaker — catalog could be down and every reservation would proceed against inventory
        // nobody confirmed, which is the overselling this client's fallback exists to prevent.
        StepVerifier.create(client.reserveInventory("tier-1", 2, "res-1"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("a successful reservation still comes through, so this is not passing by refusing everything")
    void theHappyPathStillWorks() {
        catalog.resetAll();
        catalog.stubFor(post(urlPathMatching("/api/internal/inventory/tiers/.*/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"success":true,"reservedQuantity":2,"availableQuantity":48,
                                 "errorMessage":null,"tierId":"tier-1"}
                                """)));

        StepVerifier.create(client.reserveInventory("tier-1", 2, "res-1"))
                .assertNext(result -> {
                    if (!result.success()) {
                        throw new AssertionError("the happy path must still reserve: " + result);
                    }
                })
                .verifyComplete();
    }
}
