package com.pml.shared.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inventory moves by one conditional update, never read-compare-write.
 *
 * <h2>The shape this bans, and why it is worth a lint</h2>
 * No code path reads {@code availableQuantity} into Java, compares it, and writes it back.
 * Under contention that sequence loses writes —
 * two buyers read 1, both write 0, both are told yes, and a real person arrives at a gate with a
 * valid ticket and no seat.
 *
 * <p>{@code InventoryServiceImpl} does the job as a single {@code findAndModify} with an
 * {@code $expr} guard and {@code $inc}, which is the correct form. A second implementation
 * beside it that reads, compares and writes differs on the one property that decides whether a
 * venue oversells — and even with zero callers it is a trap: the next person needing to adjust a
 * count finds two methods, no indication which is safe, and the shorter one reads more
 * naturally.</p>
 *
 * <h2>A ban, not a ratchet</h2>
 * There is no legitimate instance to preserve, and the correct path is unaffected — {@code $inc}
 * does not read the value into Java at all.
 */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R6 · inventory is never read-compare-written")
class InventoryWriteShapeLintTest {

    private static final List<String> SERVICES = List.of(
            "catalog-service", "booking-service", "identity-service");

    /** The inventory counters that must only move by conditional update. */
    private static final List<String> INVENTORY_FIELDS = List.of(
            "AvailableQuantity", "SoldQuantity", "ReservedQuantity");

    /**
     * A setter fed by arithmetic on the getter of the same field.
     *
     * <p>Matches {@code setAvailableQuantity(x.getAvailableQuantity() - n)} and the {@code +}
     * form. Deliberately narrow: this is the sequence that loses writes, and a looser pattern
     * would flag every assignment of a count and be switched off within a week.</p>
     */
    private static Pattern lostUpdateShape(String field) {
        return Pattern.compile("set" + field + "\\s*\\([^)]*get" + field + "\\s*\\(\\)\\s*[-+]");
    }

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    @Test
    @DisplayName("no service reads an inventory count into Java, adjusts it, and writes it back")
    void noReadCompareWriteOnInventory() throws IOException {
        List<String> offenders = new ArrayList<>();
        int examined = 0;

        for (String service : SERVICES) {
            Path root = backendRoot().resolve(service).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                for (Path file : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                    examined++;
                    // Comments stripped — this corpus has three times had a lint report a
                    // documented-but-unenforced rule as compliant because the prose named the
                    // very shape being searched for.
                    String source = Files.readString(file)
                            .replaceAll("(?s)/\\*.*?\\*/", "")
                            .replaceAll("(?m)//.*$", "");
                    for (String field : INVENTORY_FIELDS) {
                        Matcher hit = lostUpdateShape(field).matcher(source);
                        while (hit.find()) {
                            offenders.add(backendRoot().relativize(file) + " → "
                                    + hit.group().replaceAll("\\s+", " "));
                        }
                    }
                }
            }
        }

        assertThat(examined)
                .as("a scan that reads nothing passes forever")
                .isGreaterThan(200);
        assertThat(offenders)
                .as("two buyers both read 1, both write 0, and both are told yes. Move the count "
                        + "with a single conditional update — findAndModify with the availability "
                        + "check in the filter and $inc in the update, as InventoryServiceImpl does")
                .isEmpty();
    }

    @Test
    @DisplayName("the pattern recognises the code that was removed, and clears the one that stayed")
    void thePatternRecognisesWhatItBans() {
        // Verbatim from TicketTierServiceImpl before 2026-09-02, and the shape InventoryServiceImpl
        // uses instead. Without this the assertion above cannot tell a clean tree from a broken
        // regex — and a broken regex is the one failure a lint cannot report about itself.
        String removed = "tier.setAvailableQuantity(tier.getAvailableQuantity() - quantity);";
        String kept = "Update update = new Update().inc(\"availableQuantity\", -quantity);";

        assertThat(lostUpdateShape("AvailableQuantity").matcher(removed).find())
                .as("the lost-update shape must be caught")
                .isTrue();
        assertThat(lostUpdateShape("AvailableQuantity").matcher(kept).find())
                .as("the atomic form must not be flagged, or the lint is unusable")
                .isFalse();
    }
}
