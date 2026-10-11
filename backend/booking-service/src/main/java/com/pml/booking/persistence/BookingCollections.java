package com.pml.booking.persistence;

/**
 * The collection names this service owns — a closed set; a collection not named here does not
 * belong to booking.
 *
 * <h2>Why the names are constants and not literals</h2>
 * A collection name appears in far more places than its {@code @Document}: aggregation
 * pipelines pass it to {@code mongoTemplate.aggregate}, the index initialiser and the schema
 * validator key off it, and migrations name it directly. A literal in each of those is a
 * rename waiting to go wrong, and it goes wrong <em>silently</em> — an aggregation against a
 * collection that no longer exists does not fail, it returns zero rows. A dashboard showing
 * K0.00 and a count of 0 is indistinguishable from a quiet week.
 *
 * <h2>Ownership</h2>
 * Only this service's collections are named here. The {@code booking_} prefix is the only
 * place write-ownership is recorded, so a constant for another service's collection in this
 * file would be a second writer appearing without anyone deciding on one.
 */
public final class BookingCollections {

    private BookingCollections() {
    }

    /** The authoritative counters — available, reserved, sold · versioned */
    public static final String TIER_INVENTORY = "booking_tier_inventory";

    /** The inventory hold and its TTL · versioned */
    public static final String RESERVATIONS = "booking_reservations";

    /** The issued ticket, its owner, its QR identity */
    public static final String TICKETS = "booking_tickets";

    /** One intent per purchase attempt, keyed by idempotency key · versioned */
    public static final String PAYMENT_INTENTS = "booking_payment_intents";

    /** One row per provider call, with the provider reference */
    public static final String PAYMENT_ATTEMPTS = "booking_payment_attempts";

    /** Every provider callback, for replay defence */
    public static final String WEBHOOK_RECEIPTS = "booking_webhook_receipts";

    /** One per event · versioned */
    public static final String ESCROW_ACCOUNTS = "booking_escrow_accounts";

    /** The movements into and out of an escrow account */
    public static final String ESCROW_TRANSACTIONS = "booking_escrow_transactions";

    /** Singleton revenue and fee accounts · versioned */
    public static final String PLATFORM_ACCOUNTS = "booking_platform_accounts";

    /** The account tree */
    public static final String CHART_OF_ACCOUNTS = "booking_chart_of_accounts";

    /** The double-entry header */
    public static final String JOURNAL_ENTRIES = "booking_journal_entries";

    /** The debit and credit lines */
    public static final String JOURNAL_LINES = "booking_journal_lines";

    /** Pending and recognised commission */
    public static final String COMMISSION_RECORDS = "booking_commission_records";

    /** Organizer payout destinations */
    public static final String BANK_ACCOUNTS = "booking_bank_accounts";

    /** The payout lifecycle · versioned */
    public static final String PAYOUT_REQUESTS = "booking_payout_requests";

    /** Refund lifecycle and fees */
    public static final String REFUND_REQUESTS = "booking_refund_requests";

    /** Provider-initiated reversals */
    public static final String CHARGEBACKS = "booking_chargebacks";

    /** One per reconciliation execution */
    public static final String RECONCILIATION_RUNS = "booking_reconciliation_runs";

    /** Per-transaction match state */
    public static final String RECONCILIATION_ITEMS = "booking_reconciliation_items";

    /** Code, budget, redemption counter · versioned */
    public static final String PROMO_CODES = "booking_promo_codes";

    /** One row per admitted ticket, with the gate and the scanner */
    public static final String CHECKINS = "booking_checkins";

    /** Duplicate and offline-collision scans, for adjudication */
    public static final String CHECKIN_CONFLICTS = "booking_checkin_conflicts";

    /** Transfer and resale lifecycle between users · versioned */
    public static final String TICKET_TRANSFERS = "booking_ticket_transfers";

    /** Proposed dual-control recovery actions on stuck money */
    public static final String RECOVERY_PROPOSALS = "booking_recovery_proposals";

    /** Precomputed finance and sales statistics */
    public static final String STATISTICS_ROLLUPS = "booking_statistics_rollups";

    /** This service's document-version backfills */
    public static final String MIGRATION_RUNS = "booking_migration_runs";

    /** One durable booking per reservation: number, contact, items, lifecycle (the reservation itself is TTL-removed) */
    public static final String BOOKINGS = "booking_bookings";

    /** Monotonic counters behind human-facing numbers (booking_bookings numbers) */
    public static final String BOOKING_COUNTERS = "booking_counters";

    /** Audit of organizer messages to ticket holders (who, what, how many) */
    public static final String HOLDER_MESSAGES = "booking_holder_messages";

    /** Moves between the platform's own accounts, one per idempotency key */
    public static final String PLATFORM_TRANSFERS = "booking_platform_transfers";

    /** Staged cross-service events, written in the business transaction */
    public static final String OUTBOX = "booking_outbox";

    /** The durable half of IdempotencyGuard: one row per {scope, key}, authoritative over Redis */
    public static final String IDEMPOTENCY_LEDGER = "booking_idempotency_ledger";

}
