package com.pml.shared.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;

/**
 * Stores money as BSON Decimal128 rather than as a string.
 *
 * <h2>Why this is not a style preference</h2>
 * Spring Data MongoDB serialises {@link BigDecimal} to a BSON <em>String</em> by
 * default. MongoDB's {@code $sum} ignores non-numeric values silently, so an
 * aggregation over a money field returns {@code 0} — not an error, not a
 * warning, just a confident zero. A revenue dashboard reading zero looks like a
 * quiet week rather than a defect, which is the worst way for a money bug to
 * present.
 *
 * <p>The codebase had already found this and worked around it per-query with
 * {@code $convert} (see {@code OrganizerDashboardServiceImpl.asDecimal}). A
 * workaround that every future aggregation must remember is a defect waiting on
 * the first author who does not — and two repositories already sum {@code $amount}
 * raw. Storing the right type removes the obligation instead of documenting it.
 *
 * <h2>Reading tolerates both, deliberately</h2>
 * Only the WRITE side changes. Reads still accept a string, because Spring's
 * default {@code String → BigDecimal} converter stays registered. That is what
 * makes the rollout safe: documents written before the migration keep loading
 * through the application while the backfill runs. Aggregations are the part
 * that cannot tolerate the mix, which is why the migration is not optional.
 *
 * @see MoneyFieldMigrationService
 */
@Configuration
public class MoneyConversions {

    /**
     * <h3>Why this is a representation setting and not a custom converter</h3>
     * The obvious implementation — registering a {@code BigDecimal → Decimal128}
     * {@code @WritingConverter} — compiles, registers, reviews as correct, and
     * does nothing. Spring Data 4.5 resolves {@code BigDecimal} through its own
     * {@code BigDecimalRepresentation}, which defaults to {@code STRING} for
     * backwards compatibility and takes precedence over a custom converter for
     * the same type. The write silently stays a string.
     *
     * <p>That failure mode is worth naming, because the symptom is identical to
     * having changed nothing at all.
     */
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adapter ->
                adapter.bigDecimal(MongoCustomConversions.BigDecimalRepresentation.DECIMAL128));
    }
}
