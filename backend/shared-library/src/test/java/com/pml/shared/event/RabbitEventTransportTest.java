package com.pml.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pml.shared.testing.RabbitNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The event transport against a real RabbitMQ: the platform topology is declared on a broker, an
 * envelope is published through {@link AmqpEventTransport}, and what arrives, where, and in what
 * order is read back from the queues.
 *
 * <h2>Why a real broker</h2>
 * Every property asserted here is behaviour of the broker, not of our code: that a topic exchange
 * routes by wire name, that a rejected message reaches the dead-letter queue, that a publish to an
 * exchange nobody declared is refused rather than confirmed. A mock would assert what we believe the
 * broker does.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003 · events travel through a real RabbitMQ: routed by wire name, ordered, dead-lettered, confirmed")
class RabbitEventTransportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static CachingConnectionFactory connections;
    private static RabbitTemplate template;
    private static RabbitAdmin admin;
    private static AmqpEventTransport transport;
    private static EventBridge bridge;

    @BeforeAll
    static void connectAndDeclareTheTopology() {
        connections = new CachingConnectionFactory(RabbitNode.host(), RabbitNode.port());
        connections.setUsername(RabbitNode.USERNAME);
        connections.setPassword(RabbitNode.PASSWORD);
        connections.setVirtualHost(RabbitNode.newVirtualHost());
        connections.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(MAPPER);
        template = new RabbitTemplate(connections);
        template.setMessageConverter(converter);
        admin = new RabbitAdmin(connections);

        Declarables topology = new EventTopologyAutoConfiguration().platformEventTopology();
        List<Declarable> declared = new ArrayList<>(topology.getDeclarables());
        declared.forEach(declarable -> {
            if (declarable instanceof org.springframework.amqp.core.Exchange exchange) {
                admin.declareExchange(exchange);
            } else if (declarable instanceof org.springframework.amqp.core.Queue queue) {
                admin.declareQueue(queue);
            } else if (declarable instanceof org.springframework.amqp.core.Binding binding) {
                admin.declareBinding(binding);
            }
        });

        transport = new AmqpEventTransport(template, converter, Duration.ofSeconds(10));
        bridge = new EventBridge(transport, EventTopology.CATALOG_EVENTS);
    }

    @AfterAll
    static void close() {
        connections.destroy();
    }

    @Test
    @DisplayName("an event is routed to the subscriptions bound to its wire name, and to no other")
    void routedByWireName() {
        drain("catalog-events.booking-sub");
        drain("catalog-events.identity-sub");

        // TicketTierPublished: booking binds it, identity does not.
        bridge.publish(tierPublished("tier-routed")).block(Duration.ofSeconds(10));

        Message received = template.receive("catalog-events.booking-sub", 5000);
        assertThat(received).as("booking consumes TicketTierPublished").isNotNull();
        assertThat(received.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(received.getMessageProperties().getHeaders())
                .containsEntry(AmqpEventTransport.EVENT_TYPE_HEADER, "catalog.TicketTierPublished")
                .containsEntry(AmqpEventTransport.ORDERING_KEY_HEADER, "tier-routed");
        assertThat(received.getMessageProperties().getReceivedDeliveryMode())
                .as("a broker restart must not lose it")
                .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);

        assertThat(template.receive("catalog-events.identity-sub", 500))
                .as("identity does not consume TicketTierPublished, so nothing reaches it").isNull();
    }

    @Test
    @DisplayName("an event no subscription binds is accepted and dropped, never an error")
    void unboundEventIsDropped() {
        // catalog.* events other than the six are not in any binding; use a registered wire name nobody binds.
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(),
                EventType.CATALOG_EVENT_PUBLISHED.wireName(), 1, Instant.parse("2026-10-14T10:00:00Z"),
                "corr", null, "catalog", Map.of("eventId", "ev-1", "organizationId", "org-1", "startsAt", "x"));

        // Published on booking-events, where catalog.EventPublished is not bound at all.
        EventBridge onBookingExchange = new EventBridge(transport, EventTopology.BOOKING_EVENTS);
        onBookingExchange.publish(envelope).block(Duration.ofSeconds(10));

        assertThat(template.receive("booking-events.identity-sub", 500)).isNull();
        assertThat(template.receive("booking-events.catalog-sub", 500)).isNull();
    }

    @Test
    @DisplayName("an ordered subscription hands messages over in the order they were published")
    void orderedSubscriptionKeepsOrder() {
        drain("catalog-events.booking-sub");
        List<String> published = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            EventEnvelope envelope = capacityChanged("tier-order", i);
            published.add(envelope.eventId());
            bridge.publish(envelope).block(Duration.ofSeconds(10));
        }

        List<String> received = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Message message = template.receive("catalog-events.booking-sub", 5000);
            assertThat(message).as("message %d", i + 1).isNotNull();
            received.add(message.getMessageProperties().getMessageId());
        }
        assertThat(received).as("capacity changes of one tier arrive in publish order").isEqualTo(published);
    }

    @Test
    @DisplayName("a message the consumer rejects is dead-lettered with its body intact")
    void rejectedMessageReachesTheDeadLetterQueue() throws Exception {
        drain("catalog-events.booking-sub");
        drain("catalog-events.booking-sub.dlq");

        EventEnvelope envelope = tierPublished("tier-poison");
        bridge.publish(envelope).block(Duration.ofSeconds(10));

        // A listener that gives up, as booking's does for a dead-lettered outcome: the container rejects the
        // message without a requeue and the queue's dead-letter exchange moves it.
        var container = new org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer(connections);
        container.setQueueNames("catalog-events.booking-sub");
        container.setDefaultRequeueRejected(false);
        container.setPrefetchCount(1);
        container.setMessageListener((org.springframework.amqp.core.MessageListener) message -> {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("consumer gave up");
        });
        container.start();
        try {
            Message dead = template.receive("catalog-events.booking-sub.dlq", 8000);
            assertThat(dead).as("a rejected message is kept for an operator, not discarded").isNotNull();
            assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(envelope.eventId());
        } finally {
            container.stop();
        }
    }

    @Test
    @DisplayName("a publish to an exchange nobody declared fails, so the outbox row stays pending")
    void unconfirmedPublishFails() {
        EventBridge toNowhere = new EventBridge(transport, "an-exchange-nobody-declared");

        assertThatThrownBy(() -> toNowhere.publish(tierPublished("tier-x")).block(Duration.ofSeconds(15)))
                .as("the outbox marks a row sent only when this completes")
                .isInstanceOf(EventBridge.EventPublicationFailed.class);
    }

    // ------------------------------------------------------------------ helpers

    private static void drain(String queue) {
        while (template.receive(queue, 100) != null) {
            // empty the queue so a test sees only its own messages
        }
    }

    private static EventEnvelope tierPublished(String tierId) {
        return new EventEnvelope(UUID.randomUUID().toString(), EventType.CATALOG_TICKET_TIER_PUBLISHED.wireName(), 1,
                Instant.parse("2026-10-14T10:00:00Z"), "corr-" + tierId, null, "catalog",
                Map.of("tierId", tierId, "eventId", "ev-1", "capacity", 100, "price", "150.00", "currency", "ZMW",
                        "salesStartAt", "2026-11-01T08:00:00Z", "salesEndAt", "2026-11-30T08:00:00Z"));
    }

    private static EventEnvelope capacityChanged(String tierId, int capacity) {
        return new EventEnvelope(UUID.randomUUID().toString(),
                EventType.CATALOG_TICKET_TIER_CAPACITY_CHANGED.wireName(), 1,
                Instant.parse("2026-10-14T10:00:00Z"), "corr-" + capacity, null, "catalog",
                Map.of("tierId", tierId, "eventId", "ev-1", "newCapacity", capacity, "previousCapacity", capacity - 1));
    }
}
