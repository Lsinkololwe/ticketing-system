package com.pml.booking.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.repository.ChargebackRecordRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.PayoutSettlementService;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.event.Outbox;
import com.pml.shared.persistence.MoneyConversions;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.auditing.ReactiveIsNewAwareAuditingHandler;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.ReactiveAuditingEntityCallback;
import org.springframework.data.mapping.callback.ReactiveEntityCallbacks;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The round-trip test builds its values from the validators themselves, so it cannot notice a value
 * production writes that a validator forbids. This one writes what production writes: the same
 * factories and the real settlement service, with the platform's real id shapes (a UUID for a
 * reservation and a payout, the platform actor {@code system} in the audit fields, the masked number
 * on a payout), into collections whose validators were applied exactly as startup applies them.
 *
 * <p>Each case below is a write that was refused live and was invisible to the suite, because the
 * suite's template has no validator.
 */
@Tag("L2")
@Tag("ET-PLT-010")
@DisplayName("Production-shaped writes are accepted by the real validators")
class BookingProductionShapedWritesTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String ORGANIZATION = "6aca0ad2551d7fbe889c9614";
    private static final String EVENT = "6aca0c803ff088c1f746c8fa";
    private static final String TIER = "6aca0c803ff088c1f746c8fb";
    private static final String ESCROW = "6aca49fb67559f78fd15ef35";
    private static final String BANK = "6aca5f8c6b135f799630fc51";
    private static final String USER = "02f19926-508d-4413-ba75-88e2ffdb6191";
    private static final String ORGANIZER = "bac8944c-84de-44c4-9256-4a24adc6d49f";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static Clock clock;

    @BeforeAll
    static void applyValidators() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "booking_production_shapes");
        MongoCustomConversions conversions = new MoneyConversions().mongoCustomConversions();
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        template = new ReactiveMongoTemplate(factory, converter);
        // Auditing is on in the service (createdAt, createdBy and updatedBy are filled on save); without it a
        // document reaches the validator without the fields production always sets.
        ReactiveIsNewAwareAuditingHandler auditing = new ReactiveIsNewAwareAuditingHandler(PersistentEntities.of(context));
        auditing.setDateTimeProvider(() -> Optional.<java.time.temporal.TemporalAccessor>of(NOW));
        auditing.setAuditorAware(() -> Mono.just("system"));
        template.setEntityCallbacks(ReactiveEntityCallbacks.create(new ReactiveAuditingEntityCallback(() -> auditing)));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();
        clock = TestClock.frozenAt(NOW);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("a reservation is named by a UUID, as checkout names it")
    void reservationNamedByAUuid() {
        TicketReservation reservation = TicketReservation.builder()
                .id(UUID.nameUUIDFromBytes("purchase:user:key".getBytes()).toString())
                .eventId(EVENT)
                .userId(USER)
                .organizerId(ORGANIZER)
                .organizationId(ORGANIZATION)
                .items(List.of(TicketReservation.ReservationItem.builder()
                        .ticketTierId(TIER).tierName("General").quantity(1)
                        .unitPrice(new BigDecimal("150")).subtotal(new BigDecimal("150")).build()))
                .status(com.pml.shared.constants.ReservationStatus.HELD)
                .expiresAt(NOW.plus(Duration.ofMinutes(10)))
                .createdAt(NOW)
                .updatedAt(NOW)
                .idempotencyKey("0932ab18-7ff0-4ad7-b4ae-adf0105863fd")
                .totalAmount(new BigDecimal("150.00"))
                .subtotal(new BigDecimal("150"))
                .discountAmount(BigDecimal.ZERO)
                .currency("ZMW")
                .build();

        assertThat(template.save(reservation).block()).isNotNull();
    }

    @Test
    @DisplayName("an escrow is opened from the event envelope, which names an organization and no person")
    void escrowOpenedByOrganization() {
        EventEscrowAccount escrow = EventEscrowAccount.create(EVENT, null, NOW.plus(Duration.ofDays(70)), NOW);
        escrow.setOrganizationId(ORGANIZATION);

        assertThat(template.save(escrow).block()).isNotNull();
    }

    @Test
    @DisplayName("a ticket is issued with the platform actor in its audit fields and no copied event details")
    void ticketAsIssued() {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TKT-E6B04047")
                .eventId(EVENT)
                .reservationId(UUID.randomUUID().toString())
                .bookingId(UUID.randomUUID().toString())
                .bookingNumber("BK-2026-00000006")
                .ticketTierId(TIER)
                .ticketCategoryCode(TIER)
                .ticketCategoryName("General")
                .buyerId(USER)
                .organizerId(ORGANIZER)
                .organizationId(ORGANIZATION)
                .price(new BigDecimal("150"))
                .currency("ZMW")
                .status(TicketStatus.ISSUED)
                .quantity(1)
                .commissionRate(new BigDecimal("0.05"))
                .commissionAmount(new BigDecimal("7.50"))
                .netAmount(new BigDecimal("142.50"))
                .qrCode(UUID.randomUUID().toString())
                .paymentReference("6aca4a2a67559f78fd15ef36")
                .purchaseDate(NOW)
                .isActive(true)
                .createdAt(NOW)
                .createdBy("system")
                .updatedBy("system")
                .build();

        assertThat(template.save(ticket).block()).isNotNull();
    }

    @Test
    @DisplayName("a payout request made through the settlement service, as the workflow makes it")
    void payoutRequestThroughTheService() {
        EventEscrowAccount escrow = EventEscrowAccount.create(EVENT, null, NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(30)));
        escrow.setId(ESCROW);
        escrow.setOrganizationId(ORGANIZATION);
        escrow.setStatus(EscrowStatus.HOLD);
        escrow.setHoldUntil(NOW.minus(Duration.ofDays(1)));
        escrow.setCurrentBalance(new BigDecimal("142.50"));
        escrow.setTotalCredited(new BigDecimal("142.50"));
        template.save(escrow).block();
        template.save(BankAccount.builder()
                .id(BANK)
                .organizerId(USER)
                .organizationId(ORGANIZATION)
                .accountHolderName("Showstop Live Events")
                .bankName("Zanaco")
                .accountNumber("260961234567")
                .accountType("CURRENT")
                .currency("ZMW")
                .status("ACTIVE")
                .isVerified(true)
                .verificationStatus(BankAccount.VerificationStatus.VERIFIED)
                .createdAt(NOW)
                .build()).block();

        AccountingService accounting = Mockito.mock(AccountingService.class);
        PayoutSettlementService settlement = new PayoutSettlementService(template,
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                new Outbox(template, "booking_outbox", clock),
                accounting,
                new ReactiveMongoRepositoryFactory(template).getRepository(ChargebackRecordRepository.class),
                new BigDecimal("10.00"), clock);
        String request = UUID.randomUUID().toString();

        settlement.createRequest(new Submit(request, "client-supplied-and-ignored", ORGANIZATION, EVENT, ESCROW, BANK,
                new BigDecimal("142.50"), "ZMW", PayoutMethod.BANK_TRANSFER, null, null, UUID.randomUUID().toString(), USER)).block();

        PayoutRequest saved = template.findById(request, PayoutRequest.class).block();
        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PayoutRequestStatus.PENDING);
        assertThat(saved.getAccountNumber()).isEqualTo("****4567");
        assertThat(saved.getOrganizerId()).isEqualTo(USER);
    }
}
