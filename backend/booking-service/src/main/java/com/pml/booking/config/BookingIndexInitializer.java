package com.pml.booking.config;

import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.persistence.IndexSpec;
import com.pml.booking.persistence.BookingCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Creates booking-service's indexes.
 *
 * <h2>What the annotations do not give you</h2>
 * {@code @Indexed} produces one single-field index per annotated field and nothing else. Every
 * compound index below, every TTL, and the sparse-unique pairs are outside what it can express
 * — so without this class the platform has none of them, including
 * {@code booking_tier_inventory}'s unique {@code tierId} — the index the reservation decrement targets — and {@code booking_webhook_receipts}'s unique {@code providerEventId}, which is the platform's replay defence.
 *
 * <p>A unique index is a <b>constraint, not an optimisation: each one is a race the
 * application cannot win by checking first</b>. A missing unique index does not make
 * anything slower. It permits the duplicate, quietly, under exactly the concurrency that makes
 * it matter.</p>
 *
 * <h2>One list, checked against a live database</h2>
 * {@link #specifications()} is the complete index set, one entry per index, so it can be compared
 * mechanically — {@code BookingIndexRegistryTest} asserts every entry exists on a live database
 * with the uniqueness, sparseness and expiry declared here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingIndexInitializer {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * {@code booking.reservation.ttl-minutes}, mirrored here.
     *
     * <p>Constants rather than injected properties because {@link #specifications()} is static —
     * {@code BookingIndexRegistryTest} asserts the registry against a live database without
     * standing up a Spring context. If the property moves, this pair moves with it, and
     * {@code ReservationTtlOrderingTest} fails if the ordering it guarantees is ever inverted.
     */
    static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    /**
     * How long a released hold stays readable after its expiry.
     *
     * <p>An hour is how long the platform is willing to keep a released hold readable for
     * support and reconciliation before MongoDB removes it. Any value comfortably above the
     * purchase workflow's seat grace would prevent the race — this one also leaves the row
     * queryable while somebody is still on the phone about it.
     */
    static final Duration RESERVATION_TTL_GRACE = Duration.ofHours(1);

    /** Every index booking-service owns. */
    public static List<IndexSpec> specifications() {
        return List.of(
                // **hot** — the reservation decrement targets this and only this
                IndexSpec.on(BookingCollections.TIER_INVENTORY, "idx_tierId")
                        .asc("tierId")
                        .unique()
                        .build(),

                // remaining capacity across an event
                IndexSpec.on(BookingCollections.TIER_INVENTORY, "idx_eventId")
                        .asc("eventId")
                        .build(),

                // The TTL fires AFTER the workflow releases the hold, and the grace is the whole point.
                //
                // `Duration.ZERO` would tell MongoDB to delete the document the moment
                // `expiresAt` passes — the same instant the purchase workflow's expiry timer
                // claims it. That release is what returns the seats: it moves the hold to EXPIRED
                // and credits the tier's counters back. A row deleted before it is claimed is never released, so
                // the inventory it held is gone from the tier for good. That is not an oversell,
                // it is the opposite and it is worse for being invisible — a hot tier quietly
                // shrinks, one abandoned checkout at a time, and nothing errors.
                //
                // With the grace, the TTL is a backstop rather than a competitor: by the
                // time it runs, the workflow released the row an hour ago, and TTL removes a
                // document that is already terminal: ttl + grace = PT1H10M.
                IndexSpec.on(BookingCollections.RESERVATIONS, "idx_expiresAt_ttl")
                        .asc("expiresAt")
                        .expireAfter(RESERVATION_TTL.plus(RESERVATION_TTL_GRACE))
                        .build(),

                // **hot** — outstanding holds per tier
                // One live hold per buyer per tier, enforced at the database rather than by a check.
                //
                // `items.ticketTierId` sits inside an array, so this is multikey: a reservation
                // spanning two tiers contributes one entry per tier, each separately unique
                // against the buyer. A buyer may hold tier A and tier B at once, and not tier A
                // twice.
                //
                // The partial filter is what makes it usable rather than punitive. Without
                // `status = HELD`, a buyer could never reserve the same tier again *ever* —
                // last month's released reservation would still occupy the key. Scoping
                // uniqueness to live holds is the difference between a constraint and a
                // permanent ban.
                IndexSpec.on(BookingCollections.RESERVATIONS, "uniq_reservation_user_tier_held")
                        .asc("userId")
                        .asc("items.ticketTierId")
                        .unique()
                        .partial(new org.bson.Document("status", "HELD"))
                        .build(),

                // Boot adoption and the recovery queue read HELD reservations by expiry.
                IndexSpec.on(BookingCollections.RESERVATIONS, "idx_reservation_status_expires")
                        .asc("status")
                        .asc("expiresAt")
                        .build(),

                IndexSpec.on(BookingCollections.RESERVATIONS, "idx_tierId_status")
                        .asc("tierId")
                        .asc("status")
                        .build(),

                // "my pending purchase"
                IndexSpec.on(BookingCollections.RESERVATIONS, "idx_userId_status")
                        .asc("userId")
                        .asc("status")
                        .build(),

                // **hot** — check-in and sales counts
                IndexSpec.on(BookingCollections.TICKETS, "idx_eventId_status")
                        .asc("eventId")
                        .asc("status")
                        .build(),

                // "my tickets"
                IndexSpec.on(BookingCollections.TICKETS, "idx_ownerId_status")
                        .asc("ownerId")
                        .asc("status")
                        .build(),

                // the scanned identity
                // Unique among the tickets that carry one: a document without the field is indexed as null, and a
                // plain unique index would then admit exactly one such ticket in the whole collection.
                IndexSpec.on(BookingCollections.TICKETS, "idx_ticketReference")
                        .asc("ticketReference")
                        .unique()
                        .partialWhereTypeIs("ticketReference", "string")
                        .build(),

                // the retry guard that Redis alone cannot give
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "idx_idempotencyKey")
                        .asc("idempotencyKey")
                        .unique()
                        .build(),

                // stuck-transaction triage: intents by status, oldest first
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "idx_status_createdAt")
                        .asc("status")
                        .asc("createdAt")
                        .build(),

                // The platform reference a callback correlates on. Partial on
                // string, so an intent stored with a null reference never collides with another.
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "idx_depositId")
                        .asc("depositId")
                        .unique()
                        .partialWhereTypeIs("depositId", "string")
                        .build(),

                // Webhook correlation — unique among the attempts the provider has acknowledged.
                //
                // Partial, not sparse: an attempt that has not yet received a provider reference
                // stores the field as null rather than omitting it, and sparse does not exclude
                // a stored null. Under `unique` the second unacknowledged attempt would collide
                // with the first, so a payment could not be attempted twice until the first one
                // came back — a failure that appears only under the retry load it is meant to
                // survive.
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "idx_providerReference")
                        .asc("providerReference")
                        .unique()
                        .partialWhereTypeIs("providerReference", "string")
                        .build(),

                // What makes paymentAttempts(intentId) an index scan rather than a
                // collection scan.
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "idx_paymentIntentId")
                        .asc("paymentIntentId")
                        .build(),

                // replay defence
                IndexSpec.on(BookingCollections.WEBHOOK_RECEIPTS, "idx_providerEventId")
                        .asc("providerEventId")
                        .unique()
                        .build(),

                // bounded growth
                IndexSpec.on(BookingCollections.WEBHOOK_RECEIPTS, "idx_receivedAt_ttl")
                        .asc("receivedAt")
                        .expireAfter(Duration.ofDays(90))
                        .build(),

                // one escrow per event
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "idx_eventId")
                        .asc("eventId")
                        .unique()
                        .build(),

                // the statement
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "idx_escrowAccountId_createdAt")
                        .asc("escrowAccountId")
                        .desc("createdAt")
                        .build(),

                // trace a movement back to its cause
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "idx_referenceType_referenceId")
                        .asc("referenceType")
                        .asc("referenceId")
                        .build(),

                // entry → lines
                IndexSpec.on(BookingCollections.JOURNAL_LINES, "idx_journalEntryId")
                        .asc("journalEntryId")
                        .build(),

                // **hot** — trial balance
                IndexSpec.on(BookingCollections.JOURNAL_LINES, "idx_accountCode_postedAt")
                        .asc("accountCode")
                        .asc("postedAt")
                        .build(),

                // recognition at event completion
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "idx_eventId_status")
                        .asc("eventId")
                        .asc("status")
                        .build(),

                // the payout queue
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "idx_organizationId_status")
                        .asc("organizationId")
                        .asc("status")
                        .build(),

                // the finance workbench
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "idx_status_requestedAt")
                        .asc("status")
                        .asc("requestedAt")
                        .build(),

                // One payout per idempotency key. Partial rather than sparse, and on
                // this collection the distinction decides whether payouts work at all:
                // the key is optional, a request without one is stored as
                // `idempotencyKey: null`, and sparse indexes a stored null. The first
                // keyless payout would take the key `null` and every later one would
                // collide with it — unrelated organizations blocking each other from
                // being paid. A $type test excludes the absent field and the stored
                // null together.
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "idx_idempotencyKey")
                        .asc("idempotencyKey")
                        .unique()
                        .partialWhereTypeIs("idempotencyKey", "string")
                        .build(),

                // Offline upload replay guard. Partial, not sparse: an online scan
                // carries no scan id and stores `scanId: null`, so a sparse unique
                // index would let the first scan at a gate take the key `null` and
                // reject every scan after it — the guard breaking exactly the path
                // it does not protect.
                IndexSpec.on(BookingCollections.CHECKINS, "idx_scanId")
                        .asc("scanId")
                        .unique()
                        .partialWhereTypeIs("scanId", "string")
                        .build(),

                // the default destination
                IndexSpec.on(BookingCollections.BANK_ACCOUNTS, "idx_organizationId_isDefault")
                        .asc("organizationId")
                        .asc("isDefault")
                        .build(),

                // redemption lookup
                IndexSpec.on(BookingCollections.PROMO_CODES, "idx_code")
                        .asc("code")
                        .unique()
                        .build(),

                // **hot** — the drain's claim, every poll
                IndexSpec.on(BookingCollections.OUTBOX, "idx_status_stagedAt")
                        .asc("status")
                        .asc("stagedAt")
                        .build(),

                // ── Lookups and constraints once declared as annotations on the model classes ──
                // Names are the ones the annotations produced, so a database that already has
                // them reports each as present rather than building it again.
                // booking_bank_accounts
                IndexSpec.on(BookingCollections.BANK_ACCOUNTS, "accountNumber").asc("accountNumber").build(),
                IndexSpec.on(BookingCollections.BANK_ACCOUNTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.BANK_ACCOUNTS, "organizer_default_idx").asc("organizerId").asc("isDefault").build(),
                IndexSpec.on(BookingCollections.BANK_ACCOUNTS, "organizerId").asc("organizerId").build(),

                // booking_chargebacks
                IndexSpec.on(BookingCollections.CHARGEBACKS, "chargebackId").asc("chargebackId").unique().build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "customerId").asc("customerId").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "journalEntryId").asc("journalEntryId").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "organizer_status_idx").asc("organizerId").asc("status").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "originalTransactionId").asc("originalTransactionId").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "reason").asc("reason").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "receivedAt").asc("receivedAt").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "recoveryStatus").asc("recoveryStatus").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "status_received_idx").asc("status").desc("receivedAt").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.CHARGEBACKS, "ticketId").asc("ticketId").build(),

                // booking_chart_of_accounts
                IndexSpec.on(BookingCollections.CHART_OF_ACCOUNTS, "accountCode").asc("accountCode").unique().build(),
                IndexSpec.on(BookingCollections.CHART_OF_ACCOUNTS, "type_active_idx").asc("accountType").asc("isActive").build(),
                IndexSpec.on(BookingCollections.CHART_OF_ACCOUNTS, "accountType").asc("accountType").build(),
                IndexSpec.on(BookingCollections.CHART_OF_ACCOUNTS, "isActive").asc("isActive").build(),
                IndexSpec.on(BookingCollections.CHART_OF_ACCOUNTS, "parent_idx").asc("parentAccountCode").build(),
                IndexSpec.on(BookingCollections.CHART_OF_ACCOUNTS, "subtype_active_idx").asc("subType").asc("isActive").build(),

                // booking_checkin_conflicts
                IndexSpec.on(BookingCollections.CHECKIN_CONFLICTS, "conflict_event_detected_idx").asc("eventId").desc("detectedAt").build(),
                IndexSpec.on(BookingCollections.CHECKIN_CONFLICTS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.CHECKIN_CONFLICTS, "organizerId").asc("organizerId").build(),

                // booking_checkins
                IndexSpec.on(BookingCollections.CHECKINS, "checkin_event_recorded_idx").asc("eventId").desc("recordedAt").build(),
                IndexSpec.on(BookingCollections.CHECKINS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.CHECKINS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.CHECKINS, "ticketId").asc("ticketId").unique().build(),

                // booking_commission_records
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "organizer_status_idx").asc("organizerId").asc("status").build(),
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.COMMISSION_RECORDS, "ticketId").asc("ticketId").unique().build(),

                // booking_escrow_accounts
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "accountNumber").asc("accountNumber").unique().build(),
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "idx_escrow_status_organizer").asc("status").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.ESCROW_ACCOUNTS, "statusSemantic").asc("statusSemantic").build(),

                // booking_escrow_transactions
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "category").asc("category").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "chargebackId").asc("chargebackId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "escrow_category_idx").asc("escrowAccountId").asc("category").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "escrow_timestamp_idx").asc("escrowAccountId").desc("timestamp").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "escrow_type_idx").asc("escrowAccountId").asc("type").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "escrowAccountId").asc("escrowAccountId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "journalEntryId").asc("journalEntryId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "paymentIntentId").asc("paymentIntentId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "payoutRequestId").asc("payoutRequestId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "refundRequestId").asc("refundRequestId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "ticketId").asc("ticketId").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "timestamp").asc("timestamp").build(),
                IndexSpec.on(BookingCollections.ESCROW_TRANSACTIONS, "type").asc("type").build(),

                // booking_journal_entries
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "correlationId").asc("correlationId").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "date_status_idx").asc("entryDate").asc("status").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "entryDate").asc("entryDate").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "entryNumber").asc("entryNumber").unique().build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "account_code_idx").asc("lines.accountCode").asc("status").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "reversalOfEntryId").asc("reversalOfEntryId").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "reversedByEntryId").asc("reversedByEntryId").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "type_status_idx").asc("type").asc("status").build(),
                IndexSpec.on(BookingCollections.JOURNAL_ENTRIES, "type").asc("type").build(),

                // booking_payment_attempts
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "attemptNumber").asc("attemptNumber").unique().build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "buyer_created_idx").asc("buyerId").desc("createdAt").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "buyerId").asc("buyerId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "correlationId").asc("correlationId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "depositId").asc("depositId").unique().build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "event_status_idx").asc("eventId").asc("status").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "expiresAt").asc("expiresAt").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "providerTransactionId").asc("providerTransactionId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "reservation_status_idx").asc("reservationId").asc("status").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "reservationId").asc("reservationId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "status_created_idx").asc("status").asc("createdAt").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "status_expires_idx").asc("status").asc("expiresAt").build(),
                IndexSpec.on(BookingCollections.PAYMENT_ATTEMPTS, "status").asc("status").build(),

                // booking_payment_intents
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "providerTransactionId").asc("providerTransactionId").build(),
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "reservationId").asc("reservationId").unique().build(),
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "transactionRef").asc("transactionRef").unique().build(),
                IndexSpec.on(BookingCollections.PAYMENT_INTENTS, "userId").asc("userId").build(),

                // booking_payout_requests
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "escrowAccountId").asc("escrowAccountId").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "event_status_idx").asc("eventId").asc("status").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "organizer_status_idx").asc("organizerId").asc("status").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "paymentReference").asc("paymentReference").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "requestId").asc("requestId").unique().build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "idx_payout_status_organizer").asc("status").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "idx_payout_status").asc("status").build(),
                IndexSpec.on(BookingCollections.PAYOUT_REQUESTS, "statusSemantic").asc("statusSemantic").build(),

                // booking_platform_accounts
                IndexSpec.on(BookingCollections.PLATFORM_ACCOUNTS, "accountType").asc("accountType").unique().build(),

                // booking_promo_codes
                IndexSpec.on(BookingCollections.PROMO_CODES, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.PROMO_CODES, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.PROMO_CODES, "organizerId").asc("organizerId").build(),

                // booking_purchase_escalations
                IndexSpec.on("booking_purchase_escalations", "eventId").asc("eventId").build(),
                IndexSpec.on("booking_purchase_escalations", "reason").asc("reason").build(),
                IndexSpec.on("booking_purchase_escalations", "reservationId").asc("reservationId").unique().build(),
                IndexSpec.on("booking_purchase_escalations", "resolved").asc("resolved").build(),

                // booking_reconciliation_runs
                IndexSpec.on(BookingCollections.RECONCILIATION_RUNS, "reconciliationDate").asc("reconciliationDate").build(),
                IndexSpec.on(BookingCollections.RECONCILIATION_RUNS, "runNumber").asc("runNumber").unique().build(),
                IndexSpec.on(BookingCollections.RECONCILIATION_RUNS, "status_date_idx").asc("status").desc("reconciliationDate").build(),
                IndexSpec.on(BookingCollections.RECONCILIATION_RUNS, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.RECONCILIATION_RUNS, "type_date_idx").asc("type").desc("reconciliationDate").build(),
                IndexSpec.on(BookingCollections.RECONCILIATION_RUNS, "type").asc("type").build(),

                // booking_refund_requests
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "buyerId").asc("buyerId").build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "pawaPayRefundId").asc("pawaPayRefundId").build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "requestId").asc("requestId").unique().build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "ticketId").asc("ticketId").build(),
                IndexSpec.on(BookingCollections.REFUND_REQUESTS, "ticketNumber").asc("ticketNumber").build(),

                // booking_reservations
                IndexSpec.on(BookingCollections.RESERVATIONS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.RESERVATIONS, "idempotencyKey").asc("idempotencyKey").build(),
                IndexSpec.on(BookingCollections.RESERVATIONS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.RESERVATIONS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.RESERVATIONS, "paymentIntentId").asc("paymentIntentId").build(),
                IndexSpec.on(BookingCollections.RESERVATIONS, "status").asc("status").build(),
                IndexSpec.on(BookingCollections.RESERVATIONS, "userId").asc("userId").build(),

                // booking_tickets
                IndexSpec.on(BookingCollections.TICKETS, "buyerId").asc("buyerId").build(),
                IndexSpec.on(BookingCollections.TICKETS, "eventId").asc("eventId").build(),
                IndexSpec.on(BookingCollections.TICKETS, "organizationId").asc("organizationId").build(),
                IndexSpec.on(BookingCollections.TICKETS, "organizerId").asc("organizerId").build(),
                IndexSpec.on(BookingCollections.TICKETS, "reservationId").asc("reservationId").build(),
                IndexSpec.on(BookingCollections.TICKETS, "idx_ticket_status").asc("status").build(),
                IndexSpec.on(BookingCollections.TICKETS, "statusSemantic").asc("statusSemantic").build(),
                IndexSpec.on(BookingCollections.TICKETS, "ticketNumber").asc("ticketNumber").unique().build(),
                IndexSpec.on(BookingCollections.TICKETS, "ticketTierId").asc("ticketTierId").build(),
                IndexSpec.on(BookingCollections.TICKETS, "idx_bookingId").asc("bookingId").build(),

                // booking_ticket_transfers: the open offer per ticket, each side's list, and the expiry sweep
                IndexSpec.on(BookingCollections.TICKET_TRANSFERS, "idx_transfer_ticket_status")
                        .asc("ticketId").asc("status").build(),
                IndexSpec.on(BookingCollections.TICKET_TRANSFERS, "idx_transfer_from_created")
                        .asc("fromUserId").desc("createdAt").build(),
                IndexSpec.on(BookingCollections.TICKET_TRANSFERS, "idx_transfer_to_created")
                        .asc("toUserId").desc("createdAt").build(),
                IndexSpec.on(BookingCollections.TICKET_TRANSFERS, "idx_transfer_event_status")
                        .asc("eventId").asc("status").build(),
                // At most one open offer per ticket, in the database rather than in a check
                IndexSpec.on(BookingCollections.TICKET_TRANSFERS, "uniq_transfer_open_per_ticket")
                        .asc("ticketId").unique().partial(new org.bson.Document("status", "PENDING")).build(),

                // booking_bookings: one per reservation, one per number; the lists organizers and buyers page through
                IndexSpec.on(BookingCollections.BOOKINGS, "uniq_booking_reservation").asc("reservationId").unique().build(),
                IndexSpec.on(BookingCollections.BOOKINGS, "uniq_booking_number").asc("bookingNumber").unique().build(),
                IndexSpec.on(BookingCollections.BOOKINGS, "idx_booking_org_created")
                        .asc("organizationId").desc("createdAt").build(),
                IndexSpec.on(BookingCollections.BOOKINGS, "idx_booking_buyer_created")
                        .asc("buyerId").desc("createdAt").build(),
                IndexSpec.on(BookingCollections.BOOKINGS, "idx_booking_event_status")
                        .asc("eventId").asc("status").build(),

                // booking_platform_transfers: one move per idempotency key, in the database rather than in a check
                IndexSpec.on(BookingCollections.PLATFORM_TRANSFERS, "uniq_platform_transfer_key")
                        .asc("idempotencyKey").unique().build(),
                IndexSpec.on(BookingCollections.PLATFORM_TRANSFERS, "idx_platform_transfer_created")
                        .desc("createdAt").build(),

                // booking_recovery_proposals: what can still be confirmed, soonest expiry first; a maker's own list
                IndexSpec.on(BookingCollections.RECOVERY_PROPOSALS, "idx_proposal_status_expiry")
                        .asc("status").asc("expiresAt").build(),
                IndexSpec.on(BookingCollections.RECOVERY_PROPOSALS, "idx_proposal_maker_proposed")
                        .asc("proposedById").desc("proposedAt").build(),

                // booking_holder_messages: an event's message history, newest first
                IndexSpec.on(BookingCollections.HOLDER_MESSAGES, "idx_holder_message_event_created")
                        .asc("eventId").desc("createdAt").build());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexEnsurer.Report report = new IndexEnsurer(mongoTemplate)
                .ensure(specifications())
                // Boot-time work on the main thread, where blocking is allowed: nothing
                // may serve traffic against a collection whose constraints are absent.
                .block();

        if (report != null && !report.isClean()) {
            log.error("booking-service index registry is not satisfied: {}", report);
        }
    }
}
