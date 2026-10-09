package com.pml.booking.migration;

import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.constants.Money;
import com.pml.shared.migration.DocumentBackfill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Brings booking-service's stored documents up to the persisted shape the code expects.
 *
 * <h2>What a live database is missing without this</h2>
 * Each entry below closes a gap that a code change opened and cannot close by itself:
 *
 * <ul>
 *   <li><b>{@code _class}</b> — {@code @TypeAlias} changed what new writes store; existing
 *       documents keep the fully-qualified class name and stop deserialising the day that class
 *       moves package.</li>
 *   <li><b>{@code version}</b> — a document with {@code @Version} and no {@code version} field
 *       is treated as new, so optimistic locking guards nothing until something writes it.</li>
 *   <li><b>{@code currency}</b> — {@code @Builder.Default} runs when an object is built, not when
 *       one is read, so amounts written earlier deserialise with a null currency.</li>
 * </ul>
 *
 * <p>Runs after the collection renames: these filters name the {@code BookingCollections} names, and before the
 * rename the documents are still under their old names where nothing here would match them.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingRegistryConformanceMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<DocumentBackfill.Result> migrate() {
        return new DocumentBackfill(mongoTemplateProvider.getObject())
                // _class → the alias each document now declares
                .rewriteClassTo(BookingCollections.BANK_ACCOUNTS,
                        "com.pml.booking.domain.model.BankAccount", "bank_accounts")
                .rewriteClassTo("booking_purchase_escalations",
                        "com.pml.booking.domain.model.PurchaseEscalation", "booking_purchase_escalations")
                .rewriteClassTo(BookingCollections.CHARGEBACKS,
                        "com.pml.booking.domain.model.ChargebackRecord", "chargebacks")
                .rewriteClassTo(BookingCollections.CHART_OF_ACCOUNTS,
                        "com.pml.booking.domain.model.ChartOfAccountsEntry", "chart_of_accounts")
                .rewriteClassTo(BookingCollections.CHECKIN_CONFLICTS,
                        "com.pml.booking.domain.model.CheckInConflict", "checkin_conflicts")
                .rewriteClassTo(BookingCollections.CHECKINS,
                        "com.pml.booking.domain.model.CheckIn", "checkins")
                .rewriteClassTo(BookingCollections.COMMISSION_RECORDS,
                        "com.pml.booking.domain.model.CommissionRecord", "commission_records")
                .rewriteClassTo(BookingCollections.ESCROW_ACCOUNTS,
                        "com.pml.booking.domain.model.EventEscrowAccount", "escrow_accounts")
                .rewriteClassTo(BookingCollections.ESCROW_TRANSACTIONS,
                        "com.pml.booking.domain.model.StandaloneEscrowTransaction", "escrow_transactions")
                .rewriteClassTo(BookingCollections.JOURNAL_ENTRIES,
                        "com.pml.booking.domain.model.JournalEntry", "journal_entries")
                .rewriteClassTo(BookingCollections.PAYMENT_ATTEMPTS,
                        "com.pml.booking.domain.model.PaymentAttempt", "payment_attempts")
                .rewriteClassTo(BookingCollections.PAYMENT_INTENTS,
                        "com.pml.booking.domain.model.PaymentIntent", "payment_intents")
                .rewriteClassTo(BookingCollections.PAYOUT_REQUESTS,
                        "com.pml.booking.domain.model.PayoutRequest", "payout_requests")
                .rewriteClassTo(BookingCollections.PLATFORM_ACCOUNTS,
                        "com.pml.booking.domain.model.PlatformAccount", "platform_accounts")
                .rewriteClassTo(BookingCollections.PROMO_CODES,
                        "com.pml.booking.domain.model.PromoCode", "promo_codes")
                .rewriteClassTo(BookingCollections.RECONCILIATION_RUNS,
                        "com.pml.booking.domain.model.ReconciliationRun", "reconciliation_runs")
                .rewriteClassTo(BookingCollections.REFUND_REQUESTS,
                        "com.pml.booking.domain.model.RefundRequest", "refund_requests")
                .rewriteClassTo(BookingCollections.RESERVATIONS,
                        "com.pml.booking.domain.model.TicketReservation", "reservations")
                .rewriteClassTo(BookingCollections.TICKETS,
                        "com.pml.booking.domain.model.Ticket", "tickets")

                // optimistic locking needs a version to start from
                .setWhereMissing(BookingCollections.BANK_ACCOUNTS, "version", 0L)
                .setWhereMissing(BookingCollections.CHARGEBACKS, "version", 0L)
                .setWhereMissing(BookingCollections.CHART_OF_ACCOUNTS, "version", 0L)
                .setWhereMissing(BookingCollections.COMMISSION_RECORDS, "version", 0L)
                .setWhereMissing(BookingCollections.ESCROW_ACCOUNTS, "version", 0L)
                .setWhereMissing(BookingCollections.ESCROW_TRANSACTIONS, "version", 0L)
                .setWhereMissing(BookingCollections.JOURNAL_ENTRIES, "version", 0L)
                .setWhereMissing(BookingCollections.PAYMENT_ATTEMPTS, "version", 0L)
                .setWhereMissing(BookingCollections.PAYMENT_INTENTS, "version", 0L)
                .setWhereMissing(BookingCollections.PAYOUT_REQUESTS, "version", 0L)
                .setWhereMissing(BookingCollections.PLATFORM_ACCOUNTS, "version", 0L)
                .setWhereMissing(BookingCollections.PROMO_CODES, "version", 0L)
                .setWhereMissing(BookingCollections.RECONCILIATION_RUNS, "version", 0L)
                .setWhereMissing(BookingCollections.REFUND_REQUESTS, "version", 0L)
                .setWhereMissing(BookingCollections.RESERVATIONS, "version", 0L)
                .setWhereMissing(BookingCollections.TICKETS, "version", 0L)

                // Every monetary field has a currency sibling
                .setWhereMissing("booking_purchase_escalations", "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.BANK_ACCOUNTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.CHARGEBACKS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.CHART_OF_ACCOUNTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.COMMISSION_RECORDS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.ESCROW_ACCOUNTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.ESCROW_TRANSACTIONS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.JOURNAL_ENTRIES, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.PAYMENT_ATTEMPTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.PAYMENT_INTENTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.PAYOUT_REQUESTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.PLATFORM_ACCOUNTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.PROMO_CODES, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.RECONCILIATION_RUNS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.REFUND_REQUESTS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.RESERVATIONS, "currency", Money.DEFAULT_CURRENCY)
                .setWhereMissing(BookingCollections.TICKETS, "currency", Money.DEFAULT_CURRENCY)
                .run();
    }
}
