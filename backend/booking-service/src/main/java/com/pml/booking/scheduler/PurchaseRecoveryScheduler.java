package com.pml.booking.scheduler;

import com.pml.booking.service.PurchaseRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Runs the R8 recovery sweep.
 *
 * <h2>Why this is separate from the expiry sweep</h2>
 * They answer different questions and must not share a schedule's fate. The
 * expiry sweep asks "has this hold run out of time?" and its worst failure is
 * inventory sitting idle a little longer. The recovery sweep asks "did somebody
 * pay for this?" and its worst failure is a buyer who has been charged and holds
 * nothing. If one of them throws, the other has to keep running.
 *
 * <h2>Why it starts late</h2>
 * The delay is deliberate. On a restart after a crash, in-flight payment
 * callbacks are still arriving through the normal path; sweeping immediately
 * would race them and reach reservations that were about to resolve themselves.
 * Waiting a minute lets the ordinary path finish first, so what remains is
 * genuinely stuck.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRecoveryScheduler {

    private final PurchaseRecoveryService recoveryService;

    private static final Duration SWEEP_TIMEOUT = Duration.ofMinutes(2);

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void recoverStuckPurchases() {
        try {
            recoveryService.recoverStuckPurchases()
                    .timeout(SWEEP_TIMEOUT)
                    .block(SWEEP_TIMEOUT);
        } catch (Exception e) {
            // Swallowed so the schedule survives. A recovery sweep that dies
            // permanently is worse than one that fails a tick: the reservations
            // it would have resolved involve money, and nothing else looks at
            // them.
            log.error("Recovery sweep failed: {}. Retrying next tick.", e.getMessage());
        }
    }
}
