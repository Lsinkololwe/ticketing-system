package com.pml.booking.workflow.purchase;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Start;
import com.pml.shared.constants.ReservationStatus;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Every HELD reservation has a workflow owning its timers.
 *
 * <p>At boot, each HELD reservation is started in adopt mode under {@code USE_EXISTING}: one that
 * already has its execution is untouched, and one created before checkout ran on workflows gets an
 * execution that expires it, polls its payment and escalates it exactly as a new one would. Every
 * pod runs this; the conflict policy makes the concurrent starts one.
 */
@Slf4j
@Component
public class PurchaseAdoptionRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofMinutes(2);

    private final TicketReservationRepository reservations;
    private final TemporalGateway temporal;

    public PurchaseAdoptionRunner(TicketReservationRepository reservations, TemporalGateway temporal) {
        this.reservations = reservations;
        this.temporal = temporal;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long adopted = reservations.findByStatus(ReservationStatus.HELD)
                    .concatMap(reservation -> temporal.call(() -> {
                                PurchaseWorkflow workflow = temporal.newWorkflow(PurchaseWorkflow.class,
                                        WorkflowIds.purchase(reservation.getId()), TaskQueues.CHECKOUT,
                                        WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("Purchase", reservation.getId()).eventId(reservation.getEventId()).build());
                                return WorkflowClient.start(workflow::run, new Start(reservation.getId(), true));
                            })
                            .onErrorResume(error -> {
                                log.warn("Reservation {} could not be adopted: {}", reservation.getId(), error.getMessage());
                                return Mono.empty();
                            }))
                    .count()
                    .block(BUDGET);
            log.info("Checkout adoption: {} HELD reservation(s) have a workflow", adopted);
        } catch (RuntimeException error) {
            log.error("Checkout adoption did not complete; HELD reservations without a workflow keep their seats "
                    + "until the next boot: {}", error.getMessage());
        }
    }
}
