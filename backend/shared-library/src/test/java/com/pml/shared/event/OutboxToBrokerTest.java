package com.pml.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RabbitNode;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox end to end on real infrastructure: a row staged in MongoDB, the drain, the transport, and
 * a RabbitMQ whose queue the message is read back from.
 *
 * <p>{@code OutboxDrainTest} proves the drain's own promises against a sink standing in for the bus.
 * This proves the join: that a staged envelope really leaves MongoDB for the broker, that the row is
 * marked sent only after the broker confirmed it, and that a broker that cannot be reached leaves the
 * row owed rather than lost.</p>
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · a staged envelope reaches a real broker, and an unreachable broker leaves it owed")
class OutboxToBrokerTest {

    private static final String OUTBOX = "broker_outbox";

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static TestClock clock;
    private static Outbox outbox;
    private static CachingConnectionFactory connections;
    private static RabbitTemplate rabbit;
    private static Jackson2JsonMessageConverter converter;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "broker_harness"));
        clock = TestClock.frozenAt(Instant.parse("2026-10-14T10:00:00Z"));
        outbox = new Outbox(mongo, OUTBOX, clock);

        connections = new CachingConnectionFactory(RabbitNode.host(), RabbitNode.port());
        connections.setUsername(RabbitNode.USERNAME);
        connections.setPassword(RabbitNode.PASSWORD);
        connections.setVirtualHost(RabbitNode.newVirtualHost());
        connections.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);

        converter = new Jackson2JsonMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        rabbit = new RabbitTemplate(connections);
        rabbit.setMessageConverter(converter);

        RabbitAdmin admin = new RabbitAdmin(connections);
        for (Declarable declarable : new EventTopologyAutoConfiguration().platformEventTopology().getDeclarables()) {
            if (declarable instanceof org.springframework.amqp.core.Exchange exchange) {
                admin.declareExchange(exchange);
            } else if (declarable instanceof org.springframework.amqp.core.Queue queue) {
                admin.declareQueue(queue);
            } else if (declarable instanceof org.springframework.amqp.core.Binding binding) {
                admin.declareBinding(binding);
            }
        }
    }

    @AfterAll
    static void disconnect() {
        connections.destroy();
        client.close();
    }

    @BeforeEach
    void reset() {
        mongo.remove(new Query(), OUTBOX).block();
        while (rabbit.receive("booking-events.catalog-sub", 100) != null) {
            // start from an empty queue
        }
    }

    private static EventEnvelope ticketPurchased(String ticketId) {
        return EventEnvelopes.of(EventType.BOOKING_TICKET_PURCHASED, clock.instant(), "correlation-1",
                Map.of("ticketId", ticketId, "eventId", "event-1", "tierId", "tier-1", "ownerId", "user-1",
                        "quantity", 1));
    }

    private long rowsWith(String status) {
        return mongo.count(Query.query(Criteria.where("status").is(status)), Document.class, OUTBOX).block();
    }

    @Test
    @DisplayName("a staged envelope is confirmed by the broker, read from the subscription's queue, and the row is SENT")
    void stagedEnvelopeReachesTheBroker() {
        outbox.stage(ticketPurchased("TKT-BROKER-1")).block();
        OutboxDrain drain = new OutboxDrain(outbox, new EventBridge(
                new AmqpEventTransport(rabbit, converter, Duration.ofSeconds(10)), EventTopology.BOOKING_EVENTS));

        Long published = drain.publishPending().block(Duration.ofSeconds(20));

        assertThat(published).isEqualTo(1L);
        assertThat(rowsWith(Outbox.SENT)).isEqualTo(1);
        Message received = rabbit.receive("booking-events.catalog-sub", 5000);
        assertThat(received).as("catalog subscribes to booking.TicketPurchased").isNotNull();
        EventEnvelope body = (EventEnvelope) converter.fromMessage(received, org.springframework.core.ParameterizedTypeReference
                .forType(EventEnvelope.class));
        assertThat(body.payload()).containsEntry("ticketId", "TKT-BROKER-1");
        assertThat(body.eventType()).isEqualTo("booking.TicketPurchased");
    }

    @Test
    @DisplayName("an unreachable broker leaves the row PENDING, never SENT")
    void unreachableBrokerLeavesTheRowOwed() {
        outbox.stage(ticketPurchased("TKT-BROKER-2")).block();

        CachingConnectionFactory nowhere = new CachingConnectionFactory("localhost", 1);
        nowhere.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        RabbitTemplate unreachable = new RabbitTemplate(nowhere);
        unreachable.setMessageConverter(converter);
        OutboxDrain drain = new OutboxDrain(outbox, new EventBridge(
                new AmqpEventTransport(unreachable, converter, Duration.ofSeconds(2)), EventTopology.BOOKING_EVENTS));

        Long published = drain.publishPending().block(Duration.ofSeconds(30));

        assertThat(published).isZero();
        assertThat(rowsWith(Outbox.SENT)).as("a message the broker never took is not marked sent").isZero();
        assertThat(rowsWith(Outbox.PENDING)).as("it is still owed").isEqualTo(1);
        nowhere.destroy();
    }
}
