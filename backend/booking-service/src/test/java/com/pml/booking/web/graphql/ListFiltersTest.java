package com.pml.booking.web.graphql;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.security.TenantReads;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.PayoutRecoveryService;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.service.PlatformSummaryService;
import com.pml.booking.service.TicketSearch;
import com.pml.booking.service.TicketService;
import com.pml.booking.service.TicketStatsService;
import com.pml.booking.web.graphql.dto.EscrowAccountFilterInput;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PayoutRequestFilterInput;
import com.pml.booking.web.graphql.dto.TicketFilterInput;
import com.pml.booking.web.graphql.dto.TicketOffsetPage;
import com.pml.booking.web.graphql.query.EscrowAccountQueryResolver;
import com.pml.booking.web.graphql.query.PayoutRequestQueryResolver;
import com.pml.booking.web.graphql.query.TicketQueryResolver;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.persistence.MoneyConversions;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The administrator and organizer list filters, against a MongoDB replica set: every field the
 * schema offers narrows the list, and none of them widens what the caller may see.
 */
@Tag("L2")
@Tag("ET-TKT-001")
@Tag("ET-FIN-003")
@DisplayName("List filters apply every field they offer, and never widen the caller's view")
class ListFiltersTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String ORGANIZER_A = "organizer-a";
    private static final String ORGANIZER_B = "organizer-b";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketQueryResolver tickets;
    private static PayoutRequestQueryResolver payouts;
    private static EscrowAccountQueryResolver escrow;

    @BeforeAll
    static void seed() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = platformTemplate();
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();

        template.insertAll(List.of(
                ticket("TKT-0001", ORGANIZER_A, TicketStatus.ISSUED, "Chanda Mwila", "chanda@example.com", "Lusaka Jazz Night", 1),
                ticket("TKT-0002", ORGANIZER_A, TicketStatus.VALIDATED, "Mutale Banda", "mutale@example.com", "Lusaka Jazz Night", 2),
                ticket("TKT-0003", ORGANIZER_A, TicketStatus.REFUNDED, "Chanda Mwila", "chanda@example.com", "Kitwe Rock", 3),
                ticket("TKT-0004", ORGANIZER_B, TicketStatus.ISSUED, "Chanda Mwila", "chanda@example.com", "Ndola Summit", 4),
                ticket("TKT-.*05", ORGANIZER_B, TicketStatus.ISSUED, "Regex Person", "regex@example.com", "Ndola Summit", 5)))
                .collectList().block();
        PayoutRequestRepository payoutRepository = repositories().getRepository(PayoutRequestRepository.class);
        template.insertAll(List.of(
                payout("escrow-1", PayoutMethod.MOBILE_MONEY), payout("escrow-1", PayoutMethod.BANK_TRANSFER),
                payout("escrow-2", PayoutMethod.MOBILE_MONEY))).collectList().block();
        EventEscrowAccountRepository escrowRepository = repositories().getRepository(EventEscrowAccountRepository.class);
        template.insertAll(List.of(account("event-1", "250.00"), account("event-2", "0.00"))).collectList().block();

        tickets = new TicketQueryResolver(Mockito.mock(TicketService.class), Mockito.mock(TenantReads.class),
                Mockito.mock(TicketStatsService.class), new TicketSearch(template));

        PayoutRequestService payoutService = Mockito.mock(PayoutRequestService.class);
        when(payoutService.findAll()).thenAnswer(call -> payoutRepository.findAll());
        payouts = new PayoutRequestQueryResolver(payoutService, Mockito.mock(TenantReads.class),
                Clock.fixed(NOW, ZoneOffset.UTC), Mockito.mock(PayoutRecoveryService.class));

        EscrowService escrowService = Mockito.mock(EscrowService.class);
        when(escrowService.findAll()).thenAnswer(call -> escrowRepository.findAll());
        escrow = new EscrowAccountQueryResolver(escrowService, Mockito.mock(PlatformSummaryService.class));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static ReactiveMongoTemplate platformTemplate() {
        MongoCustomConversions conversions = new MoneyConversions().mongoCustomConversions();
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_list_filters"), converter);
    }

    private static ReactiveMongoRepositoryFactory repositories() {
        return new ReactiveMongoRepositoryFactory(template);
    }

    private static Ticket ticket(String number, String organizer, TicketStatus status, String buyer, String email,
                                 String event, int daysAgo) {
        return Ticket.builder().ticketNumber(number).organizerId(organizer).eventId("event-" + event.hashCode())
                .eventTitle(event).status(status).buyerId("buyer-" + email).buyerName(buyer).buyerEmail(email)
                .ticketCategoryCode("GENERAL").purchaseDate(NOW.minusSeconds(daysAgo * 86_400L)).build();
    }

    private static PayoutRequest payout(String escrowAccount, PayoutMethod method) {
        return PayoutRequest.builder().escrowAccountId(escrowAccount).organizerId(ORGANIZER_A).eventId("event-1")
                .payoutMethod(method).status(PayoutRequestStatus.PENDING).requestedAt(NOW.minusSeconds(3600)).build();
    }

    private static EventEscrowAccount account(String eventId, String balance) {
        return EventEscrowAccount.builder().eventId(eventId).organizerId(ORGANIZER_A).currency("ZMW")
                .currentBalance(new BigDecimal(balance)).build();
    }

    private static <T> T bind(Class<T> type, Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return new DefaultInputObjectMapper().mapToJavaObject(map, type);
    }

    private static List<String> numbers(TicketOffsetPage page) {
        return page.data().stream().map(Ticket::getTicketNumber).toList();
    }

    @Nested
    @DisplayName("tickets")
    class Tickets {

        @Test
        @DisplayName("an organizer's list holds only that organizer's tickets, whatever the filter names")
        void organizerScope() {
            TicketFilterInput otherOrganizer = bind(TicketFilterInput.class, "organizerId", ORGANIZER_B);

            assertThat(numbers(tickets.ticketsByOrganizer(ORGANIZER_A, null, null).block()))
                    .containsExactlyInAnyOrder("TKT-0001", "TKT-0002", "TKT-0003");
            assertThat(numbers(tickets.ticketsByOrganizer(ORGANIZER_A, otherOrganizer, null).block())).isEmpty();
        }

        @Test
        @DisplayName("statuses, organizer and search all narrow an administrator's search")
        void searchFields() {
            TicketFilterInput filter = bind(TicketFilterInput.class,
                    "statuses", List.of(TicketStatus.ISSUED, TicketStatus.VALIDATED), "searchQuery", "chanda@");

            assertThat(numbers(tickets.searchTickets(filter, null).block())).containsExactlyInAnyOrder("TKT-0001", "TKT-0004");
            assertThat(numbers(tickets.searchTickets(bind(TicketFilterInput.class, "organizerId", ORGANIZER_B), null).block()))
                    .containsExactlyInAnyOrder("TKT-0004", "TKT-.*05");
        }

        @Test
        @DisplayName("the search text is matched literally, never as a pattern")
        void searchIsLiteral() {
            assertThat(numbers(tickets.searchTickets(bind(TicketFilterInput.class, "searchQuery", "TKT-.*"), null).block()))
                    .containsExactly("TKT-.*05");
        }

        @Test
        @DisplayName("a search shorter than three characters is refused")
        void shortSearch() {
            try {
                tickets.searchTickets(bind(TicketFilterInput.class, "searchQuery", "ch"), null).block();
                throw new AssertionError("expected a refusal");
            } catch (ValidationRefusal refused) {
                assertThat(refused.violations()).extracting(FieldViolation::path).containsExactly("filter.searchQuery");
            }
        }

        @Test
        @DisplayName("the page is counted and cut by the database")
        void paging() {
            TicketOffsetPage page = tickets.searchTickets(bind(TicketFilterInput.class),
                    OffsetPaginationInput.of(1, 2)).block();

            assertThat(page.data()).hasSize(2);
            assertThat(page.pagination().totalCount()).isEqualTo(5);
            assertThat(page.pagination().hasNextPage()).isTrue();
        }
    }

    @Test
    @DisplayName("payout requests filter by escrow account and payout method")
    void payoutFilters() {
        PayoutRequestFilterInput filter = bind(PayoutRequestFilterInput.class,
                "escrowAccountId", "escrow-1", "payoutMethod", PayoutMethod.MOBILE_MONEY);

        List<PayoutRequest> found = payouts.payoutRequests(filter, null).block().data();

        assertThat(found).singleElement().satisfies(payout -> {
            assertThat(payout.getEscrowAccountId()).isEqualTo("escrow-1");
            assertThat(payout.getPayoutMethod()).isEqualTo(PayoutMethod.MOBILE_MONEY);
        });
    }

    @Test
    @DisplayName("escrow accounts filter by whether they hold money")
    void escrowHasBalance() {
        assertThat(escrow.escrowAccounts(bind(EscrowAccountFilterInput.class, "hasBalance", true), null).block().data())
                .extracting(EventEscrowAccount::getEventId).containsExactly("event-1");
        assertThat(escrow.escrowAccounts(bind(EscrowAccountFilterInput.class, "hasBalance", false), null).block().data())
                .extracting(EventEscrowAccount::getEventId).containsExactly("event-2");
    }
}
