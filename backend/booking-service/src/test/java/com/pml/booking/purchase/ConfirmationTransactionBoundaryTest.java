package com.pml.booking.purchase;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.ReservationTransitions;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.exception.ReservationExpiredException;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.dto.InventoryOperationResult;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.impl.PurchaseServiceImpl;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Confirmation commits tickets and envelopes together, and a retry after a
 * failure finishes the job exactly once.
 *
 * <h2>The shape under test</h2>
 * The catalog commit runs before the transaction and is idempotent per reservation. The transaction
 * then claims the reservation, writes one ticket per seat, records the financials and stages one
 * {@code booking.TicketPurchased} per ticket. A failure inside it leaves the reservation {@code HELD}
 * with no tickets and no envelope, which is the state the recovery path retries from.
 *
 * <p>Escrow, commission, accounting and the catalog are stubs; the claim, the tickets, the outbox and
 * the transaction run against a real replica set.</p>
 */
@Tag("L2")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001-R7/R8 · confirmation commits tickets and envelopes together, and retries cleanly")
class ConfirmationTransactionBoundaryTest {

    private static final String RESERVATION = "res-boundary-probe";
    private static final String TIER = "tier-boundary-probe";
    private static final Instant NOW = Instant.parse("2026-09-01T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketReservationRepository reservations;
    private static TicketRepository tickets;
    private static Clock clock;

    private final AtomicBoolean ledgerDown = new AtomicBoolean(false);
    private final AtomicInteger catalogCommits = new AtomicInteger();
    private PurchaseServiceImpl purchases;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_confirmation_boundary"));
        ReactiveMongoRepositoryFactory repositories = new ReactiveMongoRepositoryFactory(template);
        reservations = repositories.getRepository(TicketReservationRepository.class);
        tickets = repositories.getRepository(TicketRepository.class);
        clock = TestClock.frozenAt(NOW);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void wire() {
        template.remove(new Query(), TicketReservation.class).block();
        template.remove(new Query(), Ticket.class).block();
        template.remove(new Query(), Document.class, "booking_outbox").block();
        ledgerDown.set(false);
        catalogCommits.set(0);

        CommissionService commission = Mockito.mock(CommissionService.class);
        when(commission.getCommissionRate()).thenReturn(new BigDecimal("0.05"));
        when(commission.calculateCommission(any())).thenReturn(new BigDecimal("22.50"));
        when(commission.calculateNetAmount(any())).thenReturn(new BigDecimal("427.50"));
        when(commission.createPendingCommission(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        EscrowService escrow = Mockito.mock(EscrowService.class, invocation -> Mono.empty());

        AccountingService accounting = Mockito.mock(AccountingService.class, invocation ->
                ledgerDown.get() && invocation.getMethod().getName().equals("recordTicketSale")
                        ? Mono.error(new IllegalStateException("ledger unavailable"))
                        : Mono.empty());

        CatalogServiceClient catalog = Mockito.mock(CatalogServiceClient.class);
        when(catalog.commitInventoryToSold(anyString(), anyInt(), anyString())).thenAnswer(invocation -> {
            catalogCommits.incrementAndGet();
            return Mono.just(new InventoryOperationResult(true, "COMMIT", TIER, null));
        });

        purchases = new PurchaseServiceImpl(
                reservations,
                clock,
                tickets,
                new ReservationTransitions(template, clock),
                escrow,
                commission,
                accounting,
                catalog,
                new Outbox(template, "booking_outbox", clock),
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())));
    }

    private static void seedHeld(Instant expiresAt) {
        template.save(TicketReservation.builder()
                .id(RESERVATION)
                .eventId("event-boundary-probe")
                .userId("user-boundary-probe")
                .organizerId("user-organizer")
                .organizationId("org-boundary-probe")
                .items(List.of(TicketReservation.ReservationItem.builder()
                        .ticketTierId(TIER)
                        .tierName("General admission")
                        .quantity(2)
                        .unitPrice(new BigDecimal("450.00"))
                        .subtotal(new BigDecimal("900.00"))
                        .build()))
                .status(ReservationStatus.HELD)
                .totalAmount(new BigDecimal("900.00"))
                .subtotal(new BigDecimal("900.00"))
                .discountAmount(BigDecimal.ZERO)
                .expiresAt(expiresAt)
                .build()).block();
    }

    private static ReservationStatus status() {
        return template.findById(RESERVATION, TicketReservation.class).block().getStatus();
    }

    @Test
    @DisplayName("a failure inside the transaction leaves the hold, no tickets and no envelope")
    void aFailedConfirmationLeavesNothingBehind() {
        seedHeld(NOW.plusSeconds(600));
        ledgerDown.set(true);

        assertThatThrownBy(() -> purchases.confirm(RESERVATION, "intent-probe", "deposit-probe").block())
                .isInstanceOf(RuntimeException.class);

        assertThat(status()).as("the claim rolled back with the tickets").isEqualTo(ReservationStatus.HELD);
        Persistence.assertNothingPersisted(template, "booking_tickets");
        Persistence.assertNothingPersisted(template, "booking_outbox");
        assertThat(catalogCommits).as("the catalog commit ran once, before the transaction").hasValue(1);
    }

    @Test
    @DisplayName("the retry after a failure confirms, with one envelope per ticket")
    void theRetryConfirmsWithOneEnvelopePerTicket() {
        seedHeld(NOW.plusSeconds(600));
        ledgerDown.set(true);
        assertThatThrownBy(() -> purchases.confirm(RESERVATION, "intent-probe", "deposit-probe").block())
                .isInstanceOf(RuntimeException.class);

        ledgerDown.set(false);
        List<Ticket> issued = purchases.confirm(RESERVATION, "intent-probe", "deposit-probe").block();

        assertThat(status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(issued).hasSize(2);
        List<Document> staged = template.find(new Query(), Document.class, "booking_outbox").collectList().block();
        assertThat(staged).hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.getString("eventType")).isEqualTo(EventType.BOOKING_TICKET_PURCHASED.wireName());
                    Document payload = row.get("payload", Document.class);
                    assertThat(payload.getString("tierId")).isEqualTo(TIER);
                    assertThat(payload.getString("ownerId")).isEqualTo("user-boundary-probe");
                });
        assertThat(staged).extracting(row -> row.get("payload", Document.class).getString("ticketId"))
                .containsExactlyInAnyOrderElementsOf(issued.stream().map(Ticket::getId).toList());
        assertThat(catalogCommits)
                .as("the retry commits again; the catalog records it against the reservation, so it is a no-op there")
                .hasValue(2);
    }

    @Test
    @DisplayName("confirming a confirmed reservation returns its tickets and stages nothing more")
    void aSecondConfirmationIsANoOp() {
        seedHeld(NOW.plusSeconds(600));

        List<Ticket> first = purchases.confirm(RESERVATION, "intent-probe", "deposit-probe").block();
        List<Ticket> second = purchases.confirm(RESERVATION, "intent-probe", "deposit-probe").block();

        assertThat(second).extracting(Ticket::getId)
                .containsExactlyInAnyOrderElementsOf(first.stream().map(Ticket::getId).toList());
        Persistence.assertExactly(template, "booking_outbox", 2);
        Persistence.assertExactly(template, "booking_tickets", 2);
        assertThat(catalogCommits).hasValue(1);
    }

    @Test
    @DisplayName("an expired hold is refused before the catalog is asked to sell anything")
    void anExpiredHoldNeverReachesTheCatalog() {
        seedHeld(NOW.minusSeconds(1));

        assertThatThrownBy(() -> purchases.confirm(RESERVATION, "intent-probe", "deposit-probe").block())
                .isInstanceOf(ReservationExpiredException.class);

        assertThat(catalogCommits).hasValue(0);
        Persistence.assertNothingPersisted(template, "booking_tickets");
    }
}
