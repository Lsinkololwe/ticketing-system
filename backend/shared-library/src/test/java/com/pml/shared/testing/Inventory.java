package com.pml.shared.testing;

import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * Asserts the corpus-wide property: <strong>an event can never be sold beyond its capacity.</strong>
 *
 * <p>The invariant over {@code booking_tier_inventory} (ET-CAT-002 §4):
 *
 * <pre>availableQuantity + reservedQuantity + soldQuantity == capacity</pre>
 *
 * <p>Every movement is a transfer between the three counters, never a creation or a
 * destruction: reserve moves available→reserved, confirm moves reserved→sold, release moves
 * reserved→available. So the sum is constant, and any drift means a hold leaked or a
 * counter was written by something other than the conditional atomic update.
 *
 * <h2>Assert it throughout, not at the end</h2>
 * ET-CAT-002 T3 and ET-TKT-001 R1 both require conservation after <em>every</em> attempt,
 * successful or refused. An end-state check passes on an implementation that dips negative
 * in the middle — which under the 5,000-reservations-per-minute peak this platform is sized
 * for (D-16) is exactly when it will.
 */
public final class Inventory {

    private static final String COLLECTION = "booking_tier_inventory";

    private Inventory() {
    }

    /** Short form, as written in the specs' acceptance boxes. */
    public static void assertConserved(String tierId) {
        assertConserved(Harness.template(), tierId);
    }

    public static void assertConserved(ReactiveMongoTemplate template, String tierId) {
        Document row = template.findOne(
                Query.query(Criteria.where("_id").is(tierId)), Document.class, COLLECTION).block();

        if (row == null) {
            throw new AssertionError(
                    "no %s row for tier %s — conservation cannot be asserted over a tier that does not exist"
                            .formatted(COLLECTION, tierId));
        }
        check(row);
    }

    /** Conservation across every tier — the sweep used after a contention run. */
    public static void assertAllConserved() {
        assertAllConserved(Harness.template());
    }

    public static void assertAllConserved(ReactiveMongoTemplate template) {
        List<Document> rows = template.findAll(Document.class, COLLECTION).collectList().block();
        if (rows != null) {
            rows.forEach(Inventory::check);
        }
    }

    private static void check(Document row) {
        int capacity = intOf(row, "capacity");
        int available = intOf(row, "availableQuantity");
        int reserved = intOf(row, "reservedQuantity");
        int sold = intOf(row, "soldQuantity");
        int total = available + reserved + sold;

        if (total != capacity) {
            throw new AssertionError("""
                    inventory not conserved for tier %s:
                      available %d + reserved %d + sold %d = %d, but capacity is %d (drift %+d)
                    Counters move between each other and are never created or destroyed, so \
                    drift means a hold leaked or something wrote a counter outside the \
                    conditional atomic update."""
                    .formatted(row.get("_id"), available, reserved, sold, total, capacity, total - capacity));
        }
        if (available < 0 || reserved < 0 || sold < 0) {
            throw new AssertionError(
                    "negative counter for tier %s: available %d, reserved %d, sold %d"
                            .formatted(row.get("_id"), available, reserved, sold));
        }
    }

    private static int intOf(Document row, String field) {
        Object value = row.get(field);
        if (value == null) {
            throw new AssertionError("%s.%s is absent — the document does not match ET-CAT-002 §4"
                    .formatted(COLLECTION, field));
        }
        return ((Number) value).intValue();
    }
}
