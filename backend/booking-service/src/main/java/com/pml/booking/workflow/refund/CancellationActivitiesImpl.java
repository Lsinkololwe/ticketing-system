package com.pml.booking.workflow.refund;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.service.EscrowService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflow.Batch;
import com.pml.shared.constants.TicketStatus;
import io.temporal.spring.boot.ActivityImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * The cancellation's cursor over live tickets, and the escrow's close.
 */
@Slf4j
@Component
@ActivityImpl(taskQueues = TaskQueues.FINANCE)
public class CancellationActivitiesImpl implements CancellationRefundsWorkflow.Activities {

    private static final Duration AWAIT = Duration.ofSeconds(50);
    private static final List<TicketStatus> LIVE = List.of(TicketStatus.ISSUED, TicketStatus.VALIDATED);

    private final ReactiveMongoTemplate template;
    private final EscrowService escrows;

    public CancellationActivitiesImpl(ReactiveMongoTemplate template, EscrowService escrows) {
        this.template = template;
        this.escrows = escrows;
    }

    @Override
    public Batch nextBatch(String eventId, String afterTicketId, int limit) {
        Criteria live = Criteria.where("eventId").is(eventId).and("status").in(LIVE);
        if (afterTicketId != null) {
            live = live.and("_id").gt(afterTicketId);
        }
        List<String> ids = await(template.find(Query.query(live).with(Sort.by(Sort.Direction.ASC, "_id")).limit(limit), Ticket.class)
                .map(Ticket::getId)
                .collectList());
        return new Batch(ids, ids.isEmpty() ? afterTicketId : ids.get(ids.size() - 1));
    }

    /**
     * The escrow closes at zero. A residual is not closed over: it is logged as the shortfall or
     * surplus finance has to book, and the answer says the escrow stayed open.
     */
    @Override
    public boolean closeEscrow(String eventId) {
        return await(escrows.findByEventId(eventId)
                .flatMap(escrow -> {
                    if (escrow.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0) {
                        log.error("JOURNAL_UNBALANCED: event {} was cancelled and refunded but its escrow holds {} {}",
                                eventId, escrow.getCurrentBalance(), escrow.getCurrency());
                        return Mono.just(false);
                    }
                    return escrows.cancelEscrow(eventId).thenReturn(true);
                })
                .defaultIfEmpty(false));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
