package com.pml.booking.workflow.finance;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Cancelled;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Completed;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Published;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Rescheduled;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Start;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventType;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.BatchRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Catalog's facts become signals to the event's finance workflow, and nothing else.
 *
 * <p>The Service Bus consumer checkpoints only after this completes, so a signal Temporal did not
 * accept is redelivered. Each signal carries its envelope id, and the workflow ignores one it has
 * already seen.
 */
@Slf4j
@Service
public class EventFinanceProcess {

    private final TemporalGateway temporal;

    public EventFinanceProcess(TemporalGateway temporal) {
        this.temporal = temporal;
    }

    public Mono<Void> deliver(EventEnvelope envelope) {
        Object fact = factOf(envelope);
        if (fact == null) {
            return Mono.empty();
        }
        String eventId = text(envelope, "eventId");
        return temporal.run(() -> {
            WorkflowClient client = temporal.client();
            EventFinanceWorkflow workflow = temporal.newWorkflow(EventFinanceWorkflow.class, WorkflowIds.eventFinance(eventId),
                    TaskQueues.FINANCE, WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("EventFinance", eventId).eventId(eventId).build());
            BatchRequest request = client.newSignalWithStartRequest();
            request.add(workflow::run, new Start(eventId));
            switch (fact) {
                case Published published -> request.add(workflow::published, published);
                case Completed completed -> request.add(workflow::completed, completed);
                case Cancelled cancelled -> request.add(workflow::cancelled, cancelled);
                case Rescheduled rescheduled -> request.add(workflow::rescheduled, rescheduled);
                default -> throw new IllegalStateException("no signal for " + fact.getClass().getSimpleName());
            }
            client.signalWithStart(request);
        });
    }

    /** A chargeback closed; the event's finance workflow re-checks eligibility now. */
    public Mono<Void> disputeClosed(String eventId, String chargebackId) {
        return temporal.run(() -> temporal.existingWorkflow(EventFinanceWorkflow.class, WorkflowIds.eventFinance(eventId))
                        .disputeClosed(chargebackId))
                .onErrorResume(WorkflowNotFoundException.class, closed -> Mono.empty());
    }

    /**
     * The signal a catalog envelope becomes, or null for a wire name booking does not
     * consume.
     */
    public static Object factOf(EventEnvelope envelope) {
        EventType type = EventType.ofWireName(envelope.eventType()).orElse(null);
        if (type == null) {
            log.warn("Unknown wire name {} on catalog-events — acknowledging", envelope.eventType());
            return null;
        }
        return switch (type) {
            case CATALOG_EVENT_PUBLISHED -> new Published(envelope.eventId(), text(envelope, "organizationId"),
                    millis(envelope, "startsAt"));
            case CATALOG_EVENT_COMPLETED -> new Completed(envelope.eventId(), millis(envelope, "completedAt"));
            case CATALOG_EVENT_CANCELLED -> new Cancelled(envelope.eventId(), text(envelope, "reason"));
            case CATALOG_EVENT_RESCHEDULED -> new Rescheduled(envelope.eventId(), millis(envelope, "previousStartsAt"),
                    millis(envelope, "newStartsAt"));
            default -> null;
        };
    }

    private static String text(EventEnvelope envelope, String key) {
        Object value = envelope.payload().get(key);
        return value == null ? null : value.toString();
    }

    private static long millis(EventEnvelope envelope, String key) {
        String value = text(envelope, key);
        return value == null ? 0L : Instant.parse(value).toEpochMilli();
    }
}
