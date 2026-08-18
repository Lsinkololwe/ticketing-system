package com.pml.booking.migration;

import com.pml.shared.migration.MigrationLedger;
import com.pml.shared.migration.MigrationRunner;
import com.pml.shared.persistence.MoneyFieldMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Booking's data migrations, in dependency order.
 *
 * <p>The mechanics — the ledger, the claim, fail-fast, skip-if-applied — are
 * {@link MigrationRunner}'s. This class is only the list and the reasons for its
 * order.
 *
 * <h2>The ledger collection name is load-bearing</h2>
 * {@code booking_migrations} already holds rows recording every migration applied
 * to every environment. Renaming it, or changing how a row is identified, makes
 * every one of those look un-applied and re-runs the lot. It is a constant here
 * and it stays one.
 *
 * @see MigrationRunner
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner extends MigrationRunner {

    /** Fixed by the rows already in production. Do not rename. */
    static final String LEDGER_COLLECTION = "booking_migrations";

    private final ReactiveMongoTemplate mongoTemplate;

    /** The platform clock (ET-PLT-001 R3), handed to the ledger so its audit rows are testable. */
    private final Clock clock;

    private final EscrowDocumentConformanceMigrationService escrowDocuments;
    private final EscrowStatusConformanceMigrationService escrowStatuses;
    private final EscrowTransactionMigrationService escrowTransactions;
    private final PayoutConformanceMigrationService payouts;
    private final ReservationConformanceMigrationService reservations;
    private final PaymentSubjectMigrationService paymentSubjects;
    private final TicketStatusConformanceMigrationService ticketStatuses;
    private final CheckInBackfillMigrationService checkIns;
    private final StatusSemanticMigrationService statusSemantics;
    private final MoneyFieldMigrationService moneyFields;

    @Value("${booking.migrations.enabled:true}")
    private boolean enabled;

    @Value("${booking.migrations.fail-fast:true}")
    private boolean failFast;

    @Override
    protected MigrationLedger ledger() {
        return new MigrationLedger(mongoTemplate, LEDGER_COLLECTION, clock);
    }

    @Override
    protected boolean isEnabled() {
        return enabled;
    }

    @Override
    protected boolean isFailFast() {
        return failFast;
    }

    @Override
    protected String serviceName() {
        return "booking-service";
    }

    @Override
    protected Map<String, Supplier<Mono<?>>> steps() {
        Map<String, Supplier<Mono<?>>> steps = new LinkedHashMap<>();

        // Moves event_escrow_accounts → booking_escrow_accounts. Must precede
        // anything that rewrites fields in the destination.
        steps.put("escrow-document-conformance", escrowDocuments::migrate);

        // Rewrites statuses inside booking_escrow_accounts — so, after the move.
        steps.put("escrow-status-conformance", escrowStatuses::migrate);

        // Lifts embedded transactions out into their own collection; reads the
        // escrow accounts, so after both of the above.
        steps.put("escrow-transaction-extraction", escrowTransactions::migrateAllTransactions);

        steps.put("payout-conformance", payouts::migrate);

        // Moves ticket_reservations → booking_reservations, rewrites the four
        // old statuses to ET-TKT-001's five, renames convertedAt → confirmedAt.
        steps.put("reservation-conformance", reservations::migrate);

        // Re-points payment intents and attempts off ticketId onto
        // reservationId. After the reservation move, so the collection it
        // conceptually refers to exists.
        steps.put("payment-subject", paymentSubjects::migrate);

        // Rewrites the old eleven ticket statuses onto ET-TKT-002 R7's seven.
        // Must precede the check-in backfill, which selects tickets by status
        // and would find nothing at all once VALIDATED is the only admitted
        // state and half the collection still says USED.
        steps.put("ticket-status-conformance", ticketStatuses::migrate);

        // Derives CheckIn documents from already-validated tickets.
        steps.put("check-in-backfill", checkIns::migrate);

        // Converts money fields still held as strings into Decimal128. After
        // every collection move above, because it names collections explicitly
        // and would convert nothing in a collection that has not arrived yet —
        // reporting a clean zero while the money stayed as strings.
        //
        // It lives in shared-library beside the converters it completes, but its
        // collection list is entirely booking's, and every service shares one
        // MongoDB database — so running it from here covers all of them.
        steps.put("money-field-decimal128", moneyFields::migrate);

        // Last on purpose: it stamps statusSemantic from each document's status,
        // so every status rewrite above must have happened first. Run earlier,
        // it would stamp semantics derived from values that are about to change.
        steps.put("status-semantic", statusSemantics::migrateAll);

        return steps;
    }
}
