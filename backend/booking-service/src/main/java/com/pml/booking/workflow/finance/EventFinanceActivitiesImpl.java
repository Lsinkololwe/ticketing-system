package com.pml.booking.workflow.finance;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.service.CatalogLifecycleService;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Event-finance activities over the escrow and commission services.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.FINANCE)
public class EventFinanceActivitiesImpl implements EventFinanceActivities {

    private static final Duration AWAIT = Duration.ofSeconds(50);

    private final CatalogLifecycleService catalogFacts;
    private final EscrowService escrows;
    private final CommissionService commissions;

    public EventFinanceActivitiesImpl(CatalogLifecycleService catalogFacts, EscrowService escrows, CommissionService commissions) {
        this.catalogFacts = catalogFacts;
        this.escrows = escrows;
        this.commissions = commissions;
    }

    @Override
    public void openEscrow(String eventId, String organizationId, long startsAtMillis) {
        await(catalogFacts.onEventPublished(eventId, organizationId, Instant.ofEpochMilli(startsAtMillis)).thenReturn(Boolean.TRUE));
    }

    @Override
    public void openRescheduleWindow(String eventId, long previousStartsAtMillis, long newStartsAtMillis) {
        await(catalogFacts.onEventRescheduled(eventId, Instant.ofEpochMilli(previousStartsAtMillis),
                Instant.ofEpochMilli(newStartsAtMillis)).thenReturn(Boolean.TRUE));
    }

    /** Starting the hold again finds the escrow already holding and answers the stored {@code holdUntil}. */
    @Override
    public long startHold(String eventId, long completedAtMillis) {
        return await(escrow(eventId).flatMap(escrow -> escrow.getStatus() != EscrowStatus.ACTIVE
                ? Mono.just(escrow)
                : escrows.lockEscrow(eventId)
                        .flatMap(held -> escrows.updateExpectedLockDate(held.getId(), Instant.ofEpochMilli(completedAtMillis))))
                .map(escrow -> escrow.getHoldUntil().toEpochMilli()));
    }

    @Override
    public long rescheduleHold(String eventId, long newStartsAtMillis) {
        return await(escrow(eventId)
                .flatMap(escrow -> escrows.updateExpectedLockDate(escrow.getId(), Instant.ofEpochMilli(newStartsAtMillis)))
                .map(escrow -> escrow.getHoldUntil().toEpochMilli()));
    }

    @Override
    public int openDisputes(String eventId) {
        return await(escrow(eventId).map(EventEscrowAccount::getOpenDisputeCount));
    }

    @Override
    public void makePayoutEligible(String eventId) {
        await(escrow(eventId).flatMap(escrow -> escrow.getStatus() == EscrowStatus.HOLD
                ? escrows.markPayoutEligible(eventId)
                : Mono.just(escrow)).thenReturn(Boolean.TRUE));
    }

    @Override
    public long recogniseCommission(String eventId) {
        return await(commissions.markEventCommissionsEarned(eventId).defaultIfEmpty(0L));
    }

    private Mono<EventEscrowAccount> escrow(String eventId) {
        return escrows.findByEventId(eventId)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.ESCROW_ACCOUNT_UNKNOWN, "no escrow account for event " + eventId)));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
