package com.pml.booking.reservation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.persistence.BookingCollections;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.service.impl.ReservationServiceImpl;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.web.graphql.dto.TicketSelectionInput;
import com.pml.shared.security.InternalServiceWebClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * A buyer selects a tier by the id catalog gave it, and booking prices the reservation from the
 * tier list catalog serves for the event. Catalog is a WireMock server answering with the event
 * exactly as catalog's internal endpoint shapes it; reservations are written to a replica set.
 */
@Tag("L5")
@Tag("ET-TKT-001")
@Tag("ET-CAT-002")
@DisplayName("A reservation is priced from the tier the buyer selected, by its catalog id")
class ReservationByCatalogTierTest {

    private static final String EVENT = "evt-jazz";
    private static final String VIP_TIER = "6a85a3dad85e861c848e2f01";
    private static final String GENERAL_TIER = "6a85a3dad85e861c848e2f02";
    private static final String OTHER_EVENTS_TIER = "6a85a3dad85e861c848e2f99";

    private static WireMockServer catalog;
    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketReservationRepository reservations;

    private ReservationServiceImpl service;

    @BeforeAll
    static void start() {
        catalog = new WireMockServer(options().dynamicPort());
        catalog.start();
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_reservation_by_tier"));
        reservations = new ReactiveMongoRepositoryFactory(template).getRepository(TicketReservationRepository.class);
    }

    @AfterAll
    static void stop() {
        catalog.stop();
        client.close();
    }

    @BeforeEach
    void wire() {
        catalog.resetAll();
        template.remove(new Query(), BookingCollections.RESERVATIONS).block();
        catalog.stubFor(get(urlEqualTo("/api/internal/events/" + EVENT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id":"%s","title":"Lusaka Jazz Night","organizerId":"user-1","organizationId":"org-1",
                         "status":"PUBLISHED","totalCapacity":150,"ticketsSold":0,
                         "ticketCategories":[
                           {"id":"%s","code":"VIP","name":"VIP","price":500.00,"capacity":50,"sold":0,"active":true},
                           {"id":"%s","code":"GENERAL","name":"General","price":150.00,"capacity":100,"sold":0,"active":true}
                         ]}
                        """.formatted(EVENT, VIP_TIER, GENERAL_TIER))));
        catalog.stubFor(post(urlPathMatching("/api/internal/inventory/tiers/.*/reserve")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"errorMessage\":null,\"tierId\":\"t\"}")));
        catalog.stubFor(post(urlPathMatching("/api/internal/inventory/tiers/.*/release")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"operation\":\"RELEASE\",\"tierId\":\"t\",\"errorMessage\":null}")));
        service = new ReservationServiceImpl(reservations, TestClock.frozenAt(Instant.parse("2026-10-01T09:00:00Z")),
                new CatalogServiceClient(InternalServiceWebClients.unauthenticated(WebClient.builder()),
                        "http://localhost:" + catalog.port()),
                mock(PurchaseService.class),
                com.pml.shared.testing.IdempotencyPassthrough.guard(), new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private TicketReservation reserve(String tierId, int quantity, String key) {
        return service.createReservation("buyer-1",
                new ReserveTicketsInput(EVENT, List.of(new TicketSelectionInput(tierId, quantity)), null, key, null, null, null),
                "res-" + key).block();
    }

    @Test
    @DisplayName("the tier id the buyer selected prices the reservation, and the reservation is stored")
    void pricedByTierId() {
        TicketReservation held = reserve(VIP_TIER, 2, "k1");

        assertThat(held.getItems().get(0).getUnitPrice()).isEqualByComparingTo("500.00");
        assertThat(held.getTotalAmount()).isEqualByComparingTo("1000.00");
        Persistence.assertExactly(template, BookingCollections.RESERVATIONS, 1);
    }

    @Test
    @DisplayName("a tier of another event cannot be bought at this event, and its hold is given back")
    void anotherEventsTierIsRefused() {
        assertThatThrownBy(() -> reserve(OTHER_EVENTS_TIER, 1, "k2")).hasMessageContaining("TIER_UNKNOWN");

        Persistence.assertNothingPersisted(template, BookingCollections.RESERVATIONS);
        catalog.verify(postRequestedFor(urlEqualTo("/api/internal/inventory/tiers/" + OTHER_EVENTS_TIER + "/release")));
    }

    @Test
    @DisplayName("an event whose tiers catalog never mirrored sells nothing, rather than guessing a price")
    void noMirroredTiers() {
        catalog.stubFor(get(urlEqualTo("/api/internal/events/" + EVENT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + EVENT + "\",\"organizationId\":\"org-1\",\"ticketCategories\":null}")));

        assertThatThrownBy(() -> reserve(VIP_TIER, 1, "k3")).hasMessageContaining("TIER_UNKNOWN");
        Persistence.assertNothingPersisted(template, BookingCollections.RESERVATIONS);
    }
}
