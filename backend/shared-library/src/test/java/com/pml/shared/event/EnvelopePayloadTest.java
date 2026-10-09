package com.pml.shared.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No personal data leaves the platform in an event payload.
 *
 * <h2>Why the payload is where this has to be stopped</h2>
 * A message is not delivered once. It is copied to every subscription on the topic, retained by
 * each until acknowledged, written to a dead-letter queue when a consumer fails, and printed in
 * full by any log line that dumps a message. One email address in one payload is therefore an
 * email address in three services' queues, their dead-letter queues, and their logs.
 *
 * <p>An erasure guarantee would then have to reach all of it — and a dead-letter queue is not
 * somewhere a deletion request can reach. Keeping identifiers in the payload costs each consumer
 * one lookup against the service that owns the data, and keeps erasure a single-service problem.
 *
 * <h2>Asserted against the registry, not against a sample</h2>
 * Every key of every registered event is checked, so a new row carrying {@code ownerEmail} fails here
 * rather than in review.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R3 · event payloads carry identifiers, never personal data")
class EnvelopePayloadTest {

    /**
     * Fragments that name a person rather than identify a record.
     *
     * <p>{@code name} is deliberately included even though it also appears in innocuous
     * compounds: the cost of an exception for a real one is a line in this list, and the cost
     * of omitting it is a payload carrying {@code organizerName} that nobody notices.</p>
     */
    private static final List<String> PERSONAL = List.of(
            "email", "phone", "msisdn", "firstname", "lastname", "fullname", "surname",
            "name", "address", "dob", "birth", "nrc", "passport", "nationalid",
            "account", "card", "pan", "iban", "latitude", "longitude", "ip");

    /**
     * Keys that contain a listed fragment and are not personal data.
     *
     * <p>Each is here because it is an identifier or a code, not because the rule is
     * inconvenient. An addition to this set is a decision that should be visible in a diff.</p>
     */
    private static final Set<String> ALLOWED = Set.of();

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("carries no key that names a person")
    void payloadKeysAreIdentifiers(EventType type) {
        List<String> suspect = new ArrayList<>();

        for (String key : type.requiredKeys()) {
            if (ALLOWED.contains(key)) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            PERSONAL.stream()
                    .filter(lower::contains)
                    .findFirst()
                    .ifPresent(fragment -> suspect.add(key + " (matches '" + fragment + "')"));
        }

        assertThat(suspect)
                .as("""
                    %s would carry this into every subscription on its topic, every dead-letter \
                    queue, and every log line that dumps a message — and ET-PLT-008's erasure \
                    obligation cannot reach a dead-letter queue. Send the identifier and let the \
                    consumer look it up.""", type.wireName())
                .isEmpty();
    }

    @Test
    @DisplayName("the identifiers that are sent are ids, not values")
    void identifiersLookLikeIdentifiers() {
        // A positive statement of the same rule: event payloads are ids, quantities, money,
        // instants and enum names. Nothing in them is free text a person typed.
        List<String> allKeys = java.util.Arrays.stream(EventType.values())
                .flatMap(type -> type.requiredKeys().stream())
                .distinct()
                .sorted()
                .toList();

        List<String> freeText = allKeys.stream()
                .filter(key -> key.toLowerCase(Locale.ROOT).contains("description")
                        || key.toLowerCase(Locale.ROOT).contains("comment")
                        || key.toLowerCase(Locale.ROOT).contains("note")
                        || key.toLowerCase(Locale.ROOT).contains("message"))
                .toList();

        assertThat(freeText)
                .as("free text is where personal data arrives without anyone choosing to send it")
                .isEmpty();

        assertThat(allKeys)
                .as("the registry should be small and all identifiers — a payload that grows is "
                        + "a payload nobody is reviewing")
                .hasSizeLessThan(40);
    }

    @Test
    @DisplayName("the allowlist is empty, and any entry has to be argued for")
    void allowlistStaysHonest() {
        // Recorded rather than left implicit: nothing currently needs an exception. An entry
        // added later should be visible in a diff, with a reason beside it.
        assertThat(ALLOWED)
                .as("every §4 payload key passes the rule on its own merits")
                .isEmpty();
    }
}
