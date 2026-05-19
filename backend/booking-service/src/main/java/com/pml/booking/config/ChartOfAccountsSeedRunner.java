package com.pml.booking.config;

import com.pml.booking.service.ChartOfAccountsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Application Runner for seeding the Chart of Accounts on startup.
 *
 * <p>Automatically seeds the standard Chart of Accounts when the application
 * starts. This ensures the financial engine has the required accounts to
 * function properly.</p>
 *
 * <h2>Seeded Accounts</h2>
 * <pre>
 * 1000 - ASSETS
 * ├── 1011 - Primary Operating Bank Account
 * ├── 1012 - Escrow Bank Account
 * ├── 1021 - Gateway Settlement Receivable
 * ├── 1022 - Commission Receivable (pending)
 * ├── 1023 - Chargeback Recovery Receivable
 * 2000 - LIABILITIES
 * ├── 2010 - Event Escrow (parent for dynamic accounts)
 * ├── 2021 - Organizer Payouts Payable
 * ├── 2022 - Customer Refunds Payable
 * ├── 2023 - Tax Withholding Payable
 * ├── 2024 - Gateway Fees Payable
 * ├── 2031 - Deferred Commission Revenue
 * 3000 - EQUITY
 * ├── 3010 - Retained Earnings
 * ├── 3020 - Platform Reserve
 * 4000 - REVENUE
 * ├── 4010 - Commission Revenue
 * ├── 4020 - Payout Processing Fee Revenue
 * 5000 - EXPENSES
 * ├── 5010 - Payment Gateway Fees
 * ├── 5020 - Chargeback Losses
 * ├── 5030 - Chargeback Fees
 * ├── 5040 - Bad Debt Expense
 * </pre>
 *
 * <h2>Configuration</h2>
 * <p>Controlled by property: <code>pml.financial.seed-on-startup</code></p>
 * <ul>
 *   <li>true (default): Seed accounts on startup</li>
 *   <li>false: Skip seeding (for tests or manual control)</li>
 * </ul>
 *
 * @see ChartOfAccountsService#seedStandardAccounts()
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // Run early in the startup sequence
@ConditionalOnProperty(
        name = "pml.financial.seed-on-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class ChartOfAccountsSeedRunner implements ApplicationRunner {

    private final ChartOfAccountsService chartOfAccountsService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Financial Engine Initialization ===");
        log.info("Checking Chart of Accounts...");

        chartOfAccountsService.seedStandardAccounts()
                .doOnSuccess(seeded -> {
                    if (seeded) {
                        log.info("Chart of Accounts seeded successfully");
                    } else {
                        log.info("Chart of Accounts already initialized");
                    }
                })
                .doOnError(error -> log.error("Failed to seed Chart of Accounts: {}", error.getMessage()))
                .block(); // Block on startup to ensure accounts exist before processing

        log.info("=== Financial Engine Ready ===");
    }
}
