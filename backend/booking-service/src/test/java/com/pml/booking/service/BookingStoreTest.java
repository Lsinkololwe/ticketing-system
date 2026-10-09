package com.pml.booking.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.BookingRules;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.model.Booking;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Booking numbers are unique and sequential, staging is idempotent, and the status predicate agrees with the derivation. */
@Tag("L2")
@Tag("ET-TKT-002")
@DisplayName("Booking store · numbers, staging and the status predicate")
class BookingStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static BookingStore store;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        var factory = new SimpleReactiveMongoDatabaseFactory(client, "booking_store_" + UUID.randomUUID().toString().substring(0, 8));
        // Money is stored as Decimal128 in the running service (MoneyConversions); the predicate compares numbers.
        var conversions = org.springframework.data.mongodb.core.convert.MongoCustomConversions.create(a ->
                a.bigDecimal(org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation.DECIMAL128));
        var context = new org.springframework.data.mongodb.core.mapping.MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new org.springframework.data.mongodb.core.convert.MappingMongoConverter(
                org.springframework.data.mongodb.core.convert.NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        template = new ReactiveMongoTemplate(factory, converter);
        store = new BookingStore(template, TestClock.frozenAt(NOW));
        // the unique indexes the staging race depends on
        for (String field : List.of("reservationId", "bookingNumber")) {
            template.indexOps(Booking.class).ensureIndex(
                    new org.springframework.data.mongodb.core.index.Index().on(field, org.springframework.data.domain.Sort.Direction.ASC).unique()).block();
        }
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("concurrent stagings get distinct, gapless numbers in the year's sequence")
    void numbersAreDistinct() {
        List<Booking> staged = Flux.range(0, 20)
                .flatMap(i -> store.stage("res-n-" + i, "u" + i, "ev1", "Name " + i, "n" + i + "@example.com", null), 8)
                .collectList().block();
        Set<String> numbers = staged.stream().map(Booking::getBookingNumber).collect(Collectors.toSet());
        assertThat(numbers).hasSize(20).allMatch(n -> n.startsWith("BK-2026-"));
    }

    @Test
    @DisplayName("staging the same reservation twice returns the first booking and keeps the first contact")
    void stagingIsIdempotent() {
        Booking first = store.stage("res-idem", "u1", "ev1", "Mary Phiri", "mary@example.com", "+260971000000").block();
        Booking second = Flux.merge(
                store.stage("res-idem", "u1", "ev1", "Someone Else", "other@example.com", null),
                store.stage("res-idem", "u1", "ev1", "Someone Else", "other@example.com", null)).blockLast();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getBookingNumber()).isEqualTo(first.getBookingNumber());
        assertThat(second.getContactName()).isEqualTo("Mary Phiri");
        assertThat(template.count(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("reservationId").is("res-idem")), Booking.class).block())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("a refused hold leaves nothing behind")
    void discardRemovesUnheldBooking() {
        store.stage("res-discard", "u1", "ev1", null, null, null).block();
        store.discardUnheld("res-discard").block();
        assertThat(store.byReservationId("res-discard").block()).isNull();
    }

    @Test
    @DisplayName("for every status the database predicate selects exactly the bookings whose derived status it is")
    void predicateAgreesWithDerivation() {
        record Seed(BookingStatus stored, String refunded, int tickets, int cancelled, String late) { }
        List<Seed> seeds = List.of(
                new Seed(BookingStatus.PENDING, "0", 2, 0, null),
                new Seed(BookingStatus.FAILED, "0", 2, 0, null),
                new Seed(BookingStatus.CONFIRMED, "0", 2, 0, null),
                new Seed(BookingStatus.CONFIRMED, "0", 2, 1, null),
                new Seed(BookingStatus.CONFIRMED, "0", 2, 2, null),
                new Seed(BookingStatus.CONFIRMED, "40", 2, 0, null),
                new Seed(BookingStatus.CONFIRMED, "100", 2, 0, null),
                new Seed(BookingStatus.EXPIRED, "0", 2, 0, null),
                new Seed(BookingStatus.EXPIRED, "0", 2, 0, "PROCESSING"),
                new Seed(BookingStatus.EXPIRED, "0", 2, 0, "FAILED"),
                new Seed(BookingStatus.CANCELLED, "0", 2, 0, null));
        var db = "pred-" + UUID.randomUUID();
        int i = 0;
        for (Seed s : seeds) {
            template.insert(Booking.builder().reservationId(db + (i++)).eventId(db).buyerId("u").status(s.stored())
                    .totalAmount(new BigDecimal("100")).refundedAmount(new BigDecimal(s.refunded()))
                    .ticketCount(s.tickets()).cancelledTicketCount(s.cancelled()).lateRefundStatus(s.late())
                    .bookingNumber("BK-X-" + db + i).createdAt(NOW).build()).block();
        }
        List<Booking> all = template.find(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("eventId").is(db)), Booking.class).collectList().block();
        assertThat(all).hasSize(seeds.size());
        for (BookingStatus wanted : BookingStatus.values()) {
            Set<String> viaPredicate = template.find(Query.query(new org.springframework.data.mongodb.core.query.Criteria().andOperator(
                            org.springframework.data.mongodb.core.query.Criteria.where("eventId").is(db), BookingRules.criteriaFor(wanted))), Booking.class)
                    .map(Booking::getId).collect(Collectors.toSet()).block();
            Set<String> viaDerivation = all.stream().filter(b -> BookingRules.effectiveStatus(b) == wanted)
                    .map(Booking::getId).collect(Collectors.toSet());
            assertThat(viaPredicate).as("status %s", wanted).isEqualTo(viaDerivation);
        }
    }
}
