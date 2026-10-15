package com.pml.shared.event;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who hears which event: every exchange, every subscription and the wire names it binds.
 *
 * <h2>One registry, declared by every service</h2>
 * The topology is code, not broker configuration, so it is versioned and reviewed with the events it
 * routes. {@link EventTopologyAutoConfiguration} declares all of it on startup in every service.
 * Declaring is idempotent, which is what lets a consumer start before its publisher and a publisher
 * start before its consumer without either losing a message: the queue exists as soon as either
 * side has connected.
 *
 * <h2>What a subscription is</h2>
 * A subscription is a durable queue named {@code <exchange>.<group>}, bound to the publisher's topic
 * exchange once per wire name it consumes. A wire name no subscription binds is accepted by the
 * exchange and dropped, which is correct for an event nobody consumes yet. Adding a consumer means
 * adding its row here.
 *
 * <h2>Ordering</h2>
 * A subscription that carries an {@linkplain EventType#isOrdered() ordered} wire name has a single
 * active consumer: one consumer is handed messages at a time, in the order they were published, so a
 * capacity change cannot overtake the creation of the tier it changes. A subscription of commutative
 * events is consumed concurrently.
 *
 * <h2>Failure</h2>
 * A message a consumer rejects, or that waits past its time to live, is dead-lettered to
 * {@code <queue>.dlq} through the exchange's dead-letter exchange. The dead-letter queue is bounded, so
 * a subscription nobody consumes cannot grow without limit.
 */
public final class EventTopology {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String BOOKING_EVENTS = "booking-events";
    public static final String IDENTITY_EVENTS = "identity-events";

    /** How long a message may wait in a subscription before it is dead-lettered. */
    public static final long MESSAGE_TTL_MILLIS = 60 * 60 * 1000L;

    /** The most messages a dead-letter queue holds; the oldest is dropped beyond it. */
    public static final int DEAD_LETTER_LIMIT = 10_000;

    /**
     * @param exchange   the publisher's topic exchange
     * @param group      the consuming service's subscription name
     * @param eventTypes the event types it consumes
     */
    public record Subscription(String exchange, String group, Set<EventType> eventTypes) {

        public String queue() {
            return queueOf(exchange, group);
        }

        public String deadLetterQueue() {
            return queue() + ".dlq";
        }

        public String deadLetterExchange() {
            return EventTopology.deadLetterExchange(exchange);
        }

        public boolean ordered() {
            return eventTypes.stream().anyMatch(EventType::isOrdered);
        }

        public Set<String> wireNames() {
            return eventTypes.stream().map(EventType::wireName).collect(Collectors.toUnmodifiableSet());
        }
    }

    public static final List<Subscription> SUBSCRIPTIONS = List.of(
            new Subscription(CATALOG_EVENTS, "booking-sub", Set.of(
                    EventType.CATALOG_EVENT_CANCELLED, EventType.CATALOG_EVENT_COMPLETED,
                    EventType.CATALOG_EVENT_PUBLISHED, EventType.CATALOG_EVENT_RESCHEDULED,
                    EventType.CATALOG_TICKET_TIER_CAPACITY_CHANGED, EventType.CATALOG_TICKET_TIER_PUBLISHED)),
            new Subscription(CATALOG_EVENTS, "identity-sub", Set.of(
                    EventType.CATALOG_EVENT_CANCELLED, EventType.CATALOG_EVENT_COMPLETED,
                    EventType.CATALOG_EVENT_PUBLISHED, EventType.CATALOG_EVENT_RESCHEDULED)),
            new Subscription(BOOKING_EVENTS, "catalog-sub", Set.of(
                    EventType.BOOKING_REFUND_COMPLETED, EventType.BOOKING_TICKET_PURCHASED,
                    EventType.BOOKING_TICKET_VALIDATED)),
            new Subscription(BOOKING_EVENTS, "identity-sub", Set.of(
                    EventType.BOOKING_PAYMENT_COMPLETED, EventType.BOOKING_PAYMENT_FAILED,
                    EventType.BOOKING_PAYOUT_COMPLETED, EventType.BOOKING_REFUND_COMPLETED,
                    EventType.BOOKING_TICKET_PURCHASED, EventType.BOOKING_TICKET_TRANSFERRED)),
            new Subscription(IDENTITY_EVENTS, "booking-sub", Set.of(
                    EventType.IDENTITY_EVENT_ACCESS_GRANTED, EventType.IDENTITY_EVENT_ACCESS_REVOKED,
                    EventType.IDENTITY_MEMBER_REMOVED, EventType.IDENTITY_MEMBER_ROLE_CHANGED,
                    EventType.IDENTITY_ORGANIZATION_APPROVED, EventType.IDENTITY_ORGANIZATION_SUSPENDED)),
            new Subscription(IDENTITY_EVENTS, "catalog-sub", Set.of(
                    EventType.IDENTITY_MEMBER_REMOVED, EventType.IDENTITY_MEMBER_ROLE_CHANGED,
                    EventType.IDENTITY_ORGANIZATION_APPROVED, EventType.IDENTITY_ORGANIZATION_SUSPENDED)));

    /** The exchanges, one per publishing service. */
    public static final List<String> EXCHANGES = List.of(CATALOG_EVENTS, BOOKING_EVENTS, IDENTITY_EVENTS);

    private EventTopology() {
    }

    public static String deadLetterExchange(String exchange) {
        return exchange + ".dlx";
    }

    /** The queue name of one subscription, as a listener names it. */
    public static String queueOf(String exchange, String group) {
        return exchange + "." + group;
    }
}
