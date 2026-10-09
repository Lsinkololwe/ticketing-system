package com.pml.booking.migration;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.shared.referencedata.StatusSemanticResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;

/**
 * Stamps {@code statusSemantic} onto payouts written before the field existed.
 *
 * <h2>Why this is needed</h2>
 * Queries and branches now read {@code statusSemantic} rather than enumerating
 * status codes, because the code set is administrator-owned and no longer
 * enumerable. Every payout stored before this field existed has it null, so
 * without a backfill those rows match no semantic query at all — "every payout
 * still in flight" would silently omit the entire history, which for a finance
 * report is worse than failing outright.
 *
 * <h2>Idempotency</h2>
 * Only rows with a null semantic are touched, so re-running is a no-op. It does
 * not overwrite an existing value: a row already stamped carries what was true
 * when it was written, and re-deriving it from today's reference data would
 * rewrite history if an administrator has since re-classified a status.
 *
 * <h2>Unresolvable rows are reported, not guessed</h2>
 * A status code with no reference-data row cannot be classified. Those are
 * counted and logged rather than defaulted — a payout stamped with a plausible
 * but wrong semantic is invisible, while one left null is findable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusSemanticMigrationService {

    private static final String PAYOUT_TYPE = "PAYOUT_STATUS";
    private static final String TICKET_TYPE = "TICKET_STATUS";
    private static final String ESCROW_TYPE = "ESCROW_STATUS";

    private final PayoutRequestRepository payoutRequestRepository;
    private final TicketRepository ticketRepository;
    private final EventEscrowAccountRepository escrowAccountRepository;
    private final StatusSemanticResolver semanticResolver;

    /**
     * @param stamped      rows given a semantic by this run
     * @param alreadySet   rows that already had one
     * @param unresolvable rows whose status code matches no reference data
     */
    public record Result(long stamped, long alreadySet, long unresolvable) {
        public long examined() {
            return stamped + alreadySet + unresolvable;
        }

        Result plus(Result other) {
            return new Result(
                    stamped + other.stamped,
                    alreadySet + other.alreadySet,
                    unresolvable + other.unresolvable);
        }

        static Result accumulate(Result acc, Outcome outcome) {
            return switch (outcome) {
                case STAMPED -> new Result(acc.stamped + 1, acc.alreadySet, acc.unresolvable);
                case ALREADY_SET -> new Result(acc.stamped, acc.alreadySet + 1, acc.unresolvable);
                case UNRESOLVABLE -> new Result(acc.stamped, acc.alreadySet, acc.unresolvable + 1);
            };
        }
    }

    /** Backfill every collection that carries a workflow status. */
    public Mono<Result> migrateAll() {
        return migrate()
                .flatMap(payouts -> migrateTickets().map(payouts::plus))
                .flatMap(soFar -> migrateEscrowAccounts().map(soFar::plus));
    }

    /**
     * Backfill per-event escrow accounts.
     *
     * <p>These matter most of the three. "How much money is the platform still
     * holding" is answered by summing balances of escrows that have not paid out,
     * and a hard-coded status list gets that sum wrong the day an administrator
     * adds a status — under-reporting a liability, silently.
     */
    public Mono<Result> migrateEscrowAccounts() {
        return escrowAccountRepository.findAll()
                .concatMap(this::migrateEscrowAccount)
                .reduce(new Result(0, 0, 0), Result::accumulate)
                .doOnSuccess(r -> log.info(
                        "Escrow status semantic backfill: {} stamped, {} already set, {} unresolvable (of {})",
                        r.stamped(), r.alreadySet(), r.unresolvable(), r.examined()));
    }

    private Mono<Outcome> migrateEscrowAccount(EventEscrowAccount account) {
        if (account.getStatusSemantic() != null) {
            return Mono.just(Outcome.ALREADY_SET);
        }
        if (account.getStatus() == null) {
            return Mono.just(Outcome.UNRESOLVABLE);
        }
        return semanticResolver.resolve(ESCROW_TYPE, account.getStatus().name())
                .flatMap(semantic -> {
                    account.setStatusSemantic(semantic);
                    return escrowAccountRepository.save(account).thenReturn(Outcome.STAMPED);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("Escrow account {} has status {} with no reference data — left unstamped",
                            account.getEventId(), account.getStatus());
                    return Outcome.UNRESOLVABLE;
                }));
    }

    /**
     * Backfill tickets.
     *
     * <p>Separated from payouts because the collections differ by orders of
     * magnitude — an operator running this against a season's ticket sales
     * wants to know which half is taking the time.
     */
    public Mono<Result> migrateTickets() {
        return ticketRepository.findAll()
                .concatMap(this::migrateTicket)
                .reduce(new Result(0, 0, 0), Result::accumulate)
                .doOnSuccess(r -> log.info(
                        "Ticket status semantic backfill: {} stamped, {} already set, {} unresolvable (of {})",
                        r.stamped(), r.alreadySet(), r.unresolvable(), r.examined()));
    }

    private Mono<Outcome> migrateTicket(Ticket ticket) {
        if (ticket.getStatusSemantic() != null) {
            return Mono.just(Outcome.ALREADY_SET);
        }
        if (ticket.getStatus() == null) {
            return Mono.just(Outcome.UNRESOLVABLE);
        }
        return semanticResolver.resolve(TICKET_TYPE, ticket.getStatus().name())
                .flatMap(semantic -> {
                    ticket.setStatusSemantic(semantic);
                    return ticketRepository.save(ticket).thenReturn(Outcome.STAMPED);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("Ticket {} has status {} with no reference data — left unstamped",
                            ticket.getTicketNumber(), ticket.getStatus());
                    return Outcome.UNRESOLVABLE;
                }));
    }

    /** Backfill payouts only. */
    public Mono<Result> migrate() {
        return payoutRequestRepository.findAll()
                // Sequential: a backfill competes with live traffic for the same
                // connection pool, and finishing later beats slowing down a
                // payout someone is waiting on.
                .concatMap(this::migrateOne)
                .reduce(new Result(0, 0, 0), Result::accumulate)
                .doOnSuccess(r -> log.info(
                        "Payout status semantic backfill: {} stamped, {} already set, {} unresolvable (of {})",
                        r.stamped(), r.alreadySet(), r.unresolvable(), r.examined()));
    }

    private enum Outcome { STAMPED, ALREADY_SET, UNRESOLVABLE }

    private Mono<Outcome> migrateOne(PayoutRequest payout) {
        if (payout.getStatusSemantic() != null) {
            return Mono.just(Outcome.ALREADY_SET);
        }
        if (payout.getStatus() == null) {
            return Mono.just(Outcome.UNRESOLVABLE);
        }

        return semanticResolver.resolve(PAYOUT_TYPE, payout.getStatus().name())
                .flatMap(semantic -> {
                    payout.setStatusSemantic(semantic);
                    return payoutRequestRepository.save(payout).thenReturn(Outcome.STAMPED);
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("Payout {} has status {} with no reference data — left unstamped",
                            payout.getRequestId(), payout.getStatus());
                    return Outcome.UNRESOLVABLE;
                }));
    }
}
