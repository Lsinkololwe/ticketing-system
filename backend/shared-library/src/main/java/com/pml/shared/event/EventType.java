package com.pml.shared.event;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The closed set of cross-service wire names — <b>25 names, no others.</b>
 *
 * <h2>An enum because the registry is closed</h2>
 * No other cross-service message exists, and a {@code String} cannot say that. A typo
 * in a published name is not a compile error and not a runtime error either: the message goes to
 * the topic, matches no subscription filter, and is discarded. Nothing fails — a consumer simply
 * never hears about a payment. Naming them here turns that into a name that does not exist.
 *
 * <h2>The required payload keys travel with the name</h2>
 * Each constant carries the identifiers its payload must hold, so
 * {@link EventEnvelopes#of} can refuse an envelope that is missing one. A consumer reading
 * {@code payload.get("tierId")} and getting null cannot tell a publisher bug from an optional
 * field, and it is usually neither — it is a key that was renamed on one side.
 */
public enum EventType {

    // ---- catalog ---------------------------------------------------------------
    CATALOG_EVENT_PUBLISHED("catalog.EventPublished", "eventId", "organizationId", "startsAt"),
    CATALOG_TICKET_TIER_PUBLISHED("catalog.TicketTierPublished",
            "tierId", "eventId", "capacity", "price", "currency", "salesStartAt", "salesEndAt"),
    CATALOG_TICKET_TIER_CAPACITY_CHANGED("catalog.TicketTierCapacityChanged",
            "tierId", "eventId", "newCapacity", "previousCapacity"),
    CATALOG_EVENT_RESCHEDULED("catalog.EventRescheduled", "eventId", "previousStartsAt", "newStartsAt"),
    CATALOG_EVENT_CANCELLED("catalog.EventCancelled", "eventId", "reason"),
    CATALOG_EVENT_COMPLETED("catalog.EventCompleted", "eventId", "completedAt"),

    // ---- booking ---------------------------------------------------------------
    BOOKING_TICKET_PURCHASED("booking.TicketPurchased",
            "ticketId", "eventId", "tierId", "ownerId", "quantity"),
    BOOKING_TICKET_TRANSFERRED("booking.TicketTransferred", "ticketId", "fromUserId", "toUserId"),
    BOOKING_TICKET_VALIDATED("booking.TicketValidated", "ticketId", "eventId", "validatedBy"),
    BOOKING_PAYMENT_COMPLETED("booking.PaymentCompleted",
            "paymentIntentId", "reservationId", "userId", "amount", "currency"),
    BOOKING_PAYMENT_FAILED("booking.PaymentFailed",
            "paymentIntentId", "reservationId", "userId", "failureCode"),
    BOOKING_REFUND_COMPLETED("booking.RefundCompleted",
            "refundRequestId", "ticketId", "userId", "amount", "currency"),
    BOOKING_PAYOUT_COMPLETED("booking.PayoutCompleted",
            "payoutRequestId", "organizationId", "netAmount", "currency"),

    // ---- identity --------------------------------------------------------------
    IDENTITY_ORGANIZATION_APPROVED("identity.OrganizationApproved",
            "organizationId", "ownerId", "slug"),
    IDENTITY_ORGANIZATION_SUSPENDED("identity.OrganizationSuspended", "organizationId", "reason"),
    IDENTITY_MEMBER_ROLE_CHANGED("identity.MemberRoleChanged",
            "organizationId", "userId", "previousRole", "newRole"),
    IDENTITY_MEMBER_REMOVED("identity.MemberRemoved", "organizationId", "userId"),
    IDENTITY_EVENT_ACCESS_GRANTED("identity.EventAccessGranted", "userId", "eventId", "eventRole"),
    IDENTITY_EVENT_ACCESS_REVOKED("identity.EventAccessRevoked", "userId", "eventId"),
    IDENTITY_ACCOUNT_ACTIVATED("identity.AccountActivated", "userId"),
    IDENTITY_ACCOUNT_SUSPENDED("identity.AccountSuspended", "userId"),
    IDENTITY_ACCOUNT_DELETED("identity.AccountDeleted", "userId"),
    IDENTITY_CONTACT_ADDED("identity.ContactAdded", "userId", "channel", "changeId"),
    IDENTITY_CONTACT_CHANGED("identity.ContactChanged", "userId", "channel", "changeId"),
    IDENTITY_CONTACT_REMOVED("identity.ContactRemoved", "userId", "channel", "changeId");

    private static final Map<String, EventType> BY_WIRE_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EventType::wireName, type -> type));

    private final String wireName;
    private final Set<String> requiredKeys;

    EventType(String wireName, String... requiredKeys) {
        this.wireName = wireName;
        this.requiredKeys = Set.of(requiredKeys);
    }

    /**
     * The payload key whose value orders this event, or empty when it is commutative.
     *
     * <p>Service Bus delivers unordered across a topic, so where order
     * matters it is bought with a session id — and only for that key. A session id shared more
     * widely than the aggregate that needs it serialises messages that had no reason to wait,
     * which turns a topic into a queue of one.</p>
     */
    public java.util.Optional<String> sessionKey() {
        return java.util.Optional.ofNullable(SESSION_KEYS.get(this));
    }

    /** True when this event type is ordered by session, so its subscriptions must be session-enabled. */
    public boolean isOrdered() {
        return sessionKey().isPresent();
    }

    private static final Map<EventType, String> SESSION_KEYS = Map.ofEntries(
            Map.entry(CATALOG_TICKET_TIER_PUBLISHED, "tierId"),
            Map.entry(CATALOG_TICKET_TIER_CAPACITY_CHANGED, "tierId"),
            Map.entry(BOOKING_TICKET_TRANSFERRED, "ticketId"),
            Map.entry(IDENTITY_ORGANIZATION_APPROVED, "organizationId"),
            Map.entry(IDENTITY_ORGANIZATION_SUSPENDED, "organizationId"),
            Map.entry(IDENTITY_MEMBER_ROLE_CHANGED, "organizationId"),
            Map.entry(IDENTITY_MEMBER_REMOVED, "organizationId"),
            Map.entry(IDENTITY_EVENT_ACCESS_GRANTED, "eventId"),
            Map.entry(IDENTITY_EVENT_ACCESS_REVOKED, "eventId"));

    public String wireName() {
        return wireName;
    }

    public Set<String> requiredKeys() {
        return requiredKeys;
    }

    /** The service that publishes it — the prefix of the wire name. */
    public String sourceService() {
        return wireName.substring(0, wireName.indexOf('.'));
    }

    /**
     * Resolves a wire name off the bus.
     *
     * <p>Empty rather than throwing: a message carrying an unknown {@code eventType} is one this
     * build has never heard of, which happens legitimately during a rolling deploy. The consumer
     * decides whether to skip it or dead-letter it; that is not a decision this lookup can make
     * on its behalf.</p>
     */
    public static Optional<EventType> ofWireName(String wireName) {
        return Optional.ofNullable(BY_WIRE_NAME.get(wireName));
    }
}
