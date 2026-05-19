package com.pml.booking.scheduler;

import com.pml.booking.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reservation Expiration Scheduler
 *
 * <p>Business Intent: Automatically expires reservations that have passed their TTL.
 * Runs every 30 seconds to ensure timely inventory release.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A04:2021 - Insecure Design: Uses blocking with timeout to ensure completion</li>
 *   <li>A09:2021 - Security Logging: Logs all expiration events for audit trail</li>
 * </ul>
 *
 * <p><b>CRITICAL</b>: Uses blocking pattern with timeout to ensure scheduler
 * tracks task completion. Fire-and-forget subscribe() would leave tasks running
 * indefinitely and could cause overlapping executions.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final ReservationService reservationService;

    /**
     * Maximum time allowed for a single expiration run.
     * Should be less than the scheduled interval to prevent overlap.
     */
    private static final Duration EXPIRATION_TIMEOUT = Duration.ofSeconds(25);

    /**
     * Expire reservations every 30 seconds.
     *
     * <p>Fixed rate ensures consistent execution regardless of task duration.
     * Uses blocking with timeout to ensure task completion before next run.</p>
     *
     * <p><b>NOTE</b>: Uses blockLast() instead of subscribe() to ensure:
     * <ol>
     *   <li>Scheduler knows when task completes</li>
     *   <li>No overlapping executions</li>
     *   <li>Proper error propagation for monitoring</li>
     * </ol>
     * </p>
     */
    @Scheduled(fixedRate = 30000)
    public void expireReservations() {
        log.debug("Running scheduled reservation expiration task");

        try {
            reservationService.expireReservations()
                    .timeout(EXPIRATION_TIMEOUT)
                    .doOnError(error -> log.error("Error during reservation expiration: {}", error.getMessage()))
                    .doOnSuccess(v -> log.debug("Reservation expiration task completed successfully"))
                    .block(EXPIRATION_TIMEOUT);
        } catch (Exception e) {
            // Log but don't rethrow - scheduler should continue running
            log.error("Reservation expiration task failed: {}. Will retry on next schedule.", e.getMessage());
        }
    }
}
