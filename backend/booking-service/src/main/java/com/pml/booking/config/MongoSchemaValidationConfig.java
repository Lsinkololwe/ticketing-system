package com.pml.booking.config;

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
        schemas.put("tickets", "tickets-schema.json");
        schemas.put("ticket_reservations", "ticket-reservations-schema.json");

        // =========================================================================
        // PAYMENT COLLECTIONS
        // =========================================================================
        schemas.put("payment_intents", "payment-intents-schema.json");
        schemas.put("payment_attempts", "payment-attempts-schema.json");
        schemas.put("chargebacks", "chargebacks-schema.json");
        schemas.put("refund_requests", "refund-requests-schema.json");

        // =========================================================================
        // ESCROW & FINANCIAL COLLECTIONS
        // =========================================================================
        schemas.put("escrow_accounts", "escrow-accounts-schema.json");
        schemas.put("escrow_transactions", "escrow-transactions-schema.json");
        schemas.put("event_escrow_accounts", "event-escrow-accounts-schema.json");
        schemas.put("payout_requests", "payout-requests-schema.json");
        schemas.put("commission_records", "commission-records-schema.json");

        // =========================================================================
        // ACCOUNTING COLLECTIONS
        // =========================================================================
        schemas.put("journal_entries", "journal-entries-schema.json");
        schemas.put("chart_of_accounts", "chart-of-accounts-schema.json");
        schemas.put("platform_accounts", "platform-accounts-schema.json");
        schemas.put("reconciliation_runs", "reconciliation-runs-schema.json");
        schemas.put("bank_accounts", "bank-accounts-schema.json");

        // =========================================================================
        // PROMOTIONAL COLLECTIONS
        // =========================================================================
        schemas.put("promo_codes", "promo-codes-schema.json");

        log.info("Booking Service: Configured {} collection schemas for validation", schemas.size());
        return schemas;
    }
}
