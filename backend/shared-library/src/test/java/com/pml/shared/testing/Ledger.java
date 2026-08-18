package com.pml.shared.testing;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asserts the corpus-wide property: <strong>no balance is written except as a double-entry pair.</strong>
 *
 * <p>Over {@code booking_journal_lines} (ET-FIN-001 §4), for every {@code journalEntryId}:
 *
 * <ul>
 *   <li>at least two lines — a single-line entry is not double entry</li>
 *   <li>{@code sum(DEBIT.amount) == sum(CREDIT.amount)}, compared exactly</li>
 *   <li>every {@code amount} strictly positive — direction carries the sign</li>
 * </ul>
 *
 * <h2>Exactly, not within a tolerance</h2>
 * A tolerance is how a ledger drifts. A ngwee per entry over a season is kwacha, and there
 * is no point at which anyone notices, because every individual entry looked fine. Money is
 * {@code BigDecimal} at scale 2 with {@code HALF_UP} applied once (ET-PLT-002 R4), so equality
 * is the correct comparison — and {@code compareTo} rather than {@code equals}, since
 * {@code 10.00} and {@code 10.0} are the same amount and different {@code BigDecimal}s.
 */
public final class Ledger {

    private static final String LINES = "booking_journal_lines";

    private Ledger() {
    }

    /** Short form, as written in the specs' acceptance boxes. */
    public static void assertBalanced() {
        assertBalanced(Harness.template());
    }

    public static void assertBalanced(ReactiveMongoTemplate template) {
        List<Document> lines = template.find(new Query(), Document.class, LINES).collectList().block();
        if (lines == null || lines.isEmpty()) {
            return; // an empty ledger is trivially balanced
        }

        Map<String, List<Document>> byEntry = new LinkedHashMap<>();
        for (Document line : lines) {
            String entryId = line.getString("journalEntryId");
            if (entryId == null) {
                throw new AssertionError(
                        "a %s row has no journalEntryId — it belongs to no entry and can never be balanced"
                                .formatted(LINES));
            }
            byEntry.computeIfAbsent(entryId, k -> new ArrayList<>()).add(line);
        }

        List<String> failures = new ArrayList<>();
        byEntry.forEach((entryId, entryLines) -> {
            if (entryLines.size() < 2) {
                failures.add("  entry %s has %d line(s); double entry needs at least 2"
                        .formatted(entryId, entryLines.size()));
                return;
            }

            BigDecimal debits = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            for (Document line : entryLines) {
                BigDecimal amount = amountOf(line);
                if (amount.signum() <= 0) {
                    failures.add("  entry %s has a non-positive amount %s; direction carries the sign"
                            .formatted(entryId, amount));
                    return;
                }
                String direction = line.getString("direction");
                switch (direction == null ? "" : direction) {
                    case "DEBIT" -> debits = debits.add(amount);
                    case "CREDIT" -> credits = credits.add(amount);
                    default -> failures.add("  entry %s has direction '%s'; expected DEBIT or CREDIT"
                            .formatted(entryId, direction));
                }
            }

            if (debits.compareTo(credits) != 0) {
                failures.add("  entry %s is unbalanced: debits %s, credits %s (difference %s)"
                        .formatted(entryId, debits, credits, debits.subtract(credits)));
            }
        });

        if (!failures.isEmpty()) {
            throw new AssertionError(
                    "the ledger is not balanced across %d entr%s:%n%s".formatted(
                            failures.size(), failures.size() == 1 ? "y" : "ies",
                            String.join(System.lineSeparator(), failures)));
        }
    }

    private static BigDecimal amountOf(Document line) {
        Object raw = line.get("amount");
        if (raw == null) {
            throw new AssertionError("a %s row has no amount".formatted(LINES));
        }
        // Decimal128 is what ET-PLT-002 R4 stores; the others appear only when a
        // test seeds by hand, and are worth accepting so a seeding slip reads as
        // a seeding slip rather than a ledger failure.
        if (raw instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        if (raw instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (raw instanceof Number number) {
            throw new AssertionError("""
                    a %s row stores amount as %s (%s). Money is Decimal128 — a binary \
                    floating-point amount cannot represent K0.10 and must never reach the ledger \
                    (ET-PLT-002 R4)."""
                    .formatted(LINES, raw.getClass().getSimpleName(), number));
        }
        throw new AssertionError("a %s row stores amount as %s".formatted(LINES, raw.getClass()));
    }
}
