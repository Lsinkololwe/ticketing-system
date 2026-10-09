package com.pml.booking.config;

import com.pml.booking.persistence.BookingCollections;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.util.Map;

/**
 * MongoDB Schema Validation Configuration for Booking Service.
 * <p>
 * Applies JSON Schema validation to the following collections:
 * <ul>
 *     <li><b>tickets</b>: Ensures ticket documents have required fields,
 *         valid enums, proper organizationId for tenant isolation</li>
 *     <li><b>payments</b>: Validates payment documents (when schema is added)</li>
 *     <li><b>escrows</b>: Validates escrow documents (when schema is added)</li>
 * </ul>
 * </p>
 * <p>
 * Schema validation enforces:
 * <ul>
 *     <li>OWASP A01:2021 - Broken Access Control: organizationId required</li>
 *     <li>OWASP A03:2021 - Injection: Email/phone pattern validation</li>
 *     <li>OWASP A04:2021 - Insecure Design: Price non-negativity, status enums</li>
 *     <li>Data integrity: Required fields, string length limits, ObjectId patterns</li>
 * </ul>
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class MongoSchemaValidationConfig extends com.pml.shared.config.MongoSchemaValidationConfig {

    public MongoSchemaValidationConfig(
            ReactiveMongoTemplate mongoTemplate,
            ResourceLoader resourceLoader,
            com.pml.shared.config.MongoSchemaValidationProperties properties) {
        super(mongoTemplate, resourceLoader, properties);
    }

    /**
     * Defines schema mappings for Booking Service collections.
     * <p>
     * Schema files are located at:
     * {@code src/main/resources/mongodb/schemas/}
     * </p>
     * <p>
     * OWASP Compliance:
     * <ul>
     *     <li>A01:2021 - Broken Access Control: organizationId required for tenant isolation</li>
     *     <li>A03:2021 - Injection: Email/phone pattern validation</li>
     *     <li>A04:2021 - Insecure Design: Price non-negativity, status enums</li>
     * </ul>
     * </p>
     *
     * @return Map of collection names to schema file paths
     */
    @Override
    protected Map<String, String> getSchemaDefinitions() {
        Map<String, String> schemas = newSchemaMap();

        // =========================================================================
        // CORE TICKETING COLLECTIONS
        // =========================================================================
        schemas.put(BookingCollections.TICKETS, "tickets-schema.json");
        schemas.put(BookingCollections.RESERVATIONS, "booking-reservations-schema.json");
        schemas.put(BookingCollections.BOOKINGS, "bookings-schema.json");
        schemas.put(BookingCollections.TICKET_TRANSFERS, "ticket-transfers-schema.json");
        schemas.put(BookingCollections.RECOVERY_PROPOSALS, "recovery-proposals-schema.json");

        // =========================================================================
        // PAYMENT COLLECTIONS
        // =========================================================================
        schemas.put(BookingCollections.PAYMENT_INTENTS, "payment-intents-schema.json");
        schemas.put(BookingCollections.PAYMENT_ATTEMPTS, "payment-attempts-schema.json");
        schemas.put(BookingCollections.CHARGEBACKS, "chargebacks-schema.json");
        schemas.put(BookingCollections.REFUND_REQUESTS, "refund-requests-schema.json");

        // =========================================================================
        // ESCROW & FINANCIAL COLLECTIONS
        // =========================================================================
        schemas.put(BookingCollections.ESCROW_TRANSACTIONS, "escrow-transactions-schema.json");
        schemas.put(BookingCollections.ESCROW_ACCOUNTS, "booking-escrow-accounts-schema.json");
        schemas.put(BookingCollections.PAYOUT_REQUESTS, "booking-payout-requests-schema.json");
        schemas.put(BookingCollections.COMMISSION_RECORDS, "commission-records-schema.json");

        // =========================================================================
        // ACCOUNTING COLLECTIONS
        // =========================================================================
        schemas.put(BookingCollections.JOURNAL_ENTRIES, "journal-entries-schema.json");
        schemas.put(BookingCollections.CHART_OF_ACCOUNTS, "chart-of-accounts-schema.json");
        schemas.put(BookingCollections.PLATFORM_ACCOUNTS, "platform-accounts-schema.json");
        schemas.put(BookingCollections.RECONCILIATION_RUNS, "reconciliation-runs-schema.json");
        schemas.put(BookingCollections.BANK_ACCOUNTS, "bank-accounts-schema.json");

        // =========================================================================
        // PROMOTIONAL COLLECTIONS
        // =========================================================================
        schemas.put(BookingCollections.PROMO_CODES, "promo-codes-schema.json");

        log.info("Booking Service: Configured {} collection schemas for validation", schemas.size());
        return schemas;
    }
}
