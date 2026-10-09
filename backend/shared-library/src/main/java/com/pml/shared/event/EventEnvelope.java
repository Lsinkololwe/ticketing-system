package com.pml.shared.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The shape of every cross-service message.
 *
 * <h2>Why an envelope rather than the payload alone</h2>
 * A consumer that receives only a domain object can answer "what happened", and nothing else.
 * Each field here exists because some consumer or operator has to answer a different question:
 *
 * <ul>
 *   <li>{@code eventId} — <b>the deduplication key</b>. Azure Service Bus delivers at least
 *       once, so every consumer will eventually see the same message twice. Without a stable id
 *       minted by the publisher, "have I seen this?" has no answer and a redelivered
 *       {@code PaymentCompleted} issues a second ticket.</li>
 *   <li>{@code correlationId} — constant across one user-initiated chain, so a purchase that
 *       fanned out across three services can be reassembled from three services' logs.</li>
 *   <li>{@code causationId} — the {@code eventId} that caused this one. Correlation says which
 *       request; causation says which <em>step</em>, which is what turns a flat list into the
 *       order things actually happened.</li>
 *   <li>{@code schemaVersion} — bumped only on a breaking change, so a consumer can refuse a
 *       shape it does not understand instead of silently reading a field that moved.</li>
 * </ul>
 *
 * <h2>Payload holds identifiers, never personal data</h2>
 * Identifiers and scalars only. A name or a phone number in a payload is
 * replicated into every subscriber's dead-letter queue and every log line that dumps a message,
 * and the obligation to erase it on request then extends to all of them. An id costs one lookup and
 * keeps the personal data in the service that owns it.
 *
 * @param eventId       UUID minted by the publisher; the deduplication key
 * @param eventType     the {@link EventType} wire name, e.g. {@code booking.TicketPurchased}
 * @param schemaVersion starts at 1
 * @param occurredAt    from the platform {@code Clock}, never the wall clock
 * @param correlationId constant across one user-initiated chain
 * @param causationId   the {@code eventId} that caused this one; null at the head of a chain
 * @param sourceService {@code catalog}, {@code booking} or {@code identity}
 * @param payload       identifiers and scalars only
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String sourceService,
        Map<String, Object> payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId is the deduplication key and cannot be null");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sourceService, "sourceService");

        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion starts at 1, got " + schemaVersion);
        }
        // Copied, not referenced. A publisher that keeps mutating the map it passed in would
        // otherwise change a message that has already been staged for delivery.
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
