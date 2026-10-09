package com.pml.shared.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Builds {@link EventEnvelope}s that satisfy the {@link EventType} registry, and refuses ones that do not.
 *
 * <h2>Refusing at the publisher, not at the consumer</h2>
 * A malformed envelope is cheap to reject here and expensive everywhere else. Once it is on the
 * topic it has been delivered to every subscriber, each of which fails in its own way at its own
 * time — one dead-letters it, one throws a {@code NullPointerException} reading a missing key,
 * one silently treats the absent field as a default. The publisher is the only place where the
 * problem has a single owner and a stack trace pointing at the code that caused it.
 *
 * <h2>What is checked</h2>
 * <ul>
 *   <li>every identifier the registry lists for the wire name is present and non-blank</li>
 *   <li>no key beyond those it lists — an extra key is a payload growing by accident, and
 *       payloads are where personal data leaks into every subscriber's dead-letter queue</li>
 *   <li>the {@code sourceService} matches the prefix of the wire name, so a service cannot
 *       publish another's event and make the origin unattributable</li>
 * </ul>
 */
public final class EventEnvelopes {

    private EventEnvelopes() {
    }

    /**
     * @param causationId the {@code eventId} that caused this one; null at the head of a chain
     */
    public static EventEnvelope of(EventType type,
                                   Instant occurredAt,
                                   String correlationId,
                                   String causationId,
                                   Map<String, Object> payload) {

        Map<String, Object> checked = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        List<String> problems = new ArrayList<>();

        Set<String> missing = new TreeSet<>(type.requiredKeys());
        missing.removeAll(checked.keySet());
        if (!missing.isEmpty()) {
            problems.add("missing " + missing);
        }

        Set<String> unexpected = new TreeSet<>(checked.keySet());
        unexpected.removeAll(type.requiredKeys());
        if (!unexpected.isEmpty()) {
            problems.add("unexpected " + unexpected);
        }

        // A key that is present but blank is worse than one that is absent: the absent one fails
        // here, the blank one reaches a consumer that looks up "" and finds nothing.
        Set<String> blank = new TreeSet<>();
        checked.forEach((key, value) -> {
            if (value == null || (value instanceof CharSequence text && text.toString().isBlank())) {
                blank.add(key);
            }
        });
        if (!blank.isEmpty()) {
            problems.add("blank " + blank);
        }

        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    type.wireName() + " payload does not match ET-PLT-003 §4: "
                            + String.join("; ", problems));
        }

        // Money is a decimal string, never a float. Enforced in the value rather than by
        // configuring the serialiser, because the envelope crosses a service boundary and the
        // consumer's Jackson is not ours to configure. Left as a BigDecimal it is written as a
        // JSON number, and a consumer reading into Map<String,Object> gets a Double — where
        // 150.10 is 150.09999999999999 and a receipt stops agreeing with the ledger.
        checked.replaceAll((key, value) ->
                value instanceof java.math.BigDecimal amount ? amount.toPlainString() : value);

        return new EventEnvelope(
                UUID.randomUUID().toString(),
                type.wireName(),
                1,
                occurredAt,
                correlationId,
                causationId,
                type.sourceService(),
                checked);
    }

    /** Head of a chain — nothing caused this one. */
    public static EventEnvelope of(EventType type, Instant occurredAt,
                                   String correlationId, Map<String, Object> payload) {
        return of(type, occurredAt, correlationId, null, payload);
    }

    /**
     * An envelope caused by another, inheriting its correlation.
     *
     * <p>Threading these by hand is where a chain gets broken: a publisher that starts a fresh
     * correlation mid-flow leaves the second half of a purchase unattached to the first, and the
     * gap is only visible when someone is trying to reconstruct an incident.</p>
     */
    public static EventEnvelope causedBy(EventEnvelope cause, EventType type,
                                         Instant occurredAt, Map<String, Object> payload) {
        return of(type, occurredAt, cause.correlationId(), cause.eventId(), payload);
    }
}
