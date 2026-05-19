package com.pml.booking.event.listener;

import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Financial Job Listener
 *
 * Scheduled jobs for financial operations:
 * 1. Process expired payments (mark as expired, release ticket reservations)
 * 2. Process hold period expirations (LOCKED → PAYOUT_ELIGIBLE)
 * 3. Reconciliation jobs (future)
 *
 * These jobs run on a schedule to ensure financial consistency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialJobListener {

    private final PaymentService paymentService;
    private final EscrowService escrowService;
    private final CommissionService commissionService;

    /**
     * Process expired payments.
     * Runs every 5 minutes.
     *
     * Marks PENDING/PROCESSING payments as EXPIRED if past their expiry time.
     * This releases the ticket reservation for other buyers.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void processExpiredPayments() {
        log.info("Starting expired payments job");

        paymentService.processExpiredPayments()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Expired payments job completed: {} payments processed", count);
                    }
                })
                .doOnError(error -> log.error("Expired payments job failed", error))
                .onErrorResume(error -> Mono.just(0L))
                .subscribe();
    }

    /**
     * Process hold period expirations.
     * Runs every hour.
     *
     * Transitions escrow accounts from LOCKED to PAYOUT_ELIGIBLE
     * after the 7-day hold period passes.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void processHoldPeriodExpirations() {
        log.info("Starting hold period expiration job");

        escrowService.processHoldPeriodExpirations()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Hold period job completed: {} escrows now payout-eligible", count);
                    }
                })
                .doOnError(error -> log.error("Hold period job failed", error))
                .onErrorResume(error -> Mono.just(0L))
                .subscribe();
    }

    /**
     * Daily financial summary.
     * Runs once per day at midnight.
     *
     * Logs platform financial metrics for monitoring.
     */
    @Scheduled(cron = "0 0 0 * * *") // Midnight daily
    public void generateDailyFinancialSummary() {
        log.info("Generating daily financial summary");

        commissionService.getTotalPlatformEarnedCommission()
                .doOnSuccess(total -> log.info("Daily Summary - Total Earned Commission: {}", total))
                .doOnError(error -> log.error("Failed to generate financial summary", error))
                .subscribe();
    }
}
