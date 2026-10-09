package com.pml.booking.migration;

import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.migration.CollectionRenameMigration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Moves booking's collections onto their {@link BookingCollections} names.
 *
 * <h2>This is the service where an unmigrated database is worst</h2>
 * Booking holds tickets, payments, escrow and the journal. Reading an empty {@code tickets}
 * collection does not error — it reports that nobody has bought anything, that every escrow
 * balance is zero, and that the ledger balances perfectly because it contains nothing. Every one
 * of those is a plausible-looking number.
 *
 * <h2>Ordering against the other booking migrations</h2>
 * This runs <b>first</b> in {@code DataMigrationRunner}. The conformance migrations rewrite
 * fields inside these collections, and a field rewrite that runs before the collection move
 * operates on an empty destination and reports a clean zero — which is what a successful run
 * also reports.
 *
 * <h2>Two collections are deliberately absent</h2>
 * {@code escrow_accounts} stays where it is: it belongs to the orphaned {@code EscrowAccount},
 * whose shape differs from the per-event {@code EventEscrowAccount} that already owns
 * {@code booking_escrow_accounts}. Renaming it would merge two schemas into the collection that
 * holds the money. {@code booking_purchase_escalations} is left where it is rather than moved
 * onto a guessed name.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingCollectionRenameMigrationService {

    /** Legacy collection name → its {@link BookingCollections} name. */
    private static final Map<String, String> RENAMES = CollectionRenameMigration.table(
            "tickets", BookingCollections.TICKETS,
            "payment_intents", BookingCollections.PAYMENT_INTENTS,
            "payment_attempts", BookingCollections.PAYMENT_ATTEMPTS,
            "escrow_transactions", BookingCollections.ESCROW_TRANSACTIONS,
            "platform_accounts", BookingCollections.PLATFORM_ACCOUNTS,
            "chart_of_accounts", BookingCollections.CHART_OF_ACCOUNTS,
            "journal_entries", BookingCollections.JOURNAL_ENTRIES,
            "commission_records", BookingCollections.COMMISSION_RECORDS,
            "bank_accounts", BookingCollections.BANK_ACCOUNTS,
            "refund_requests", BookingCollections.REFUND_REQUESTS,
            "chargebacks", BookingCollections.CHARGEBACKS,
            "reconciliation_runs", BookingCollections.RECONCILIATION_RUNS,
            "promo_codes", BookingCollections.PROMO_CODES);

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<CollectionRenameMigration.Result> migrate() {
        return new CollectionRenameMigration(mongoTemplateProvider.getObject()).migrate(RENAMES);
    }
}
