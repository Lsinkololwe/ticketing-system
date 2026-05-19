package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.service.ChartOfAccountsService;
import com.pml.booking.web.graphql.dto.ChartOfAccountsMutationResponse;
import com.pml.booking.web.graphql.dto.CreateChartOfAccountsInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Chart of Accounts Operations.
 *
 * <p>Provides mutations for managing the Chart of Accounts, which is the
 * foundation of the double-entry bookkeeping system.</p>
 *
 * <h2>Account Structure</h2>
 * <ul>
 *   <li>1000-1999: ASSETS (Debit normal balance)</li>
 *   <li>2000-2999: LIABILITIES (Credit normal balance)</li>
 *   <li>3000-3999: EQUITY (Credit normal balance)</li>
 *   <li>4000-4999: REVENUE (Credit normal balance)</li>
 *   <li>5000-5999: EXPENSES (Debit normal balance)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ChartOfAccountsMutationResolver {

    private final ChartOfAccountsService chartOfAccountsService;

    /**
     * Create a new Chart of Accounts entry.
     * Schema: createChartOfAccountsEntry(input: CreateChartOfAccountsInput!): ChartOfAccountsMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChartOfAccountsMutationResponse> createChartOfAccountsEntry(
            @InputArgument CreateChartOfAccountsInput input
    ) {
        log.info("GraphQL mutation: createChartOfAccountsEntry({})", input.accountCode());

        return chartOfAccountsService.createAccount(
                        input.accountCode(),
                        input.accountName(),
                        input.accountType(),
                        input.subType(),
                        input.parentAccountCode(),
                        input.currency() != null ? input.currency() : "ZMW",
                        input.description()
                )
                .map(entry -> ChartOfAccountsMutationResponse.success(
                        "Account " + input.accountCode() + " created successfully", entry))
                .onErrorResume(e -> {
                    log.error("Failed to create account {}: {}", input.accountCode(), e.getMessage());
                    return Mono.just(ChartOfAccountsMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Update a Chart of Accounts entry.
     * Schema: updateChartOfAccountsEntry(id: ID!, input: CreateChartOfAccountsInput!): ChartOfAccountsMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChartOfAccountsMutationResponse> updateChartOfAccountsEntry(
            @InputArgument String id,
            @InputArgument CreateChartOfAccountsInput input
    ) {
        log.info("GraphQL mutation: updateChartOfAccountsEntry(id={}, code={})", id, input.accountCode());

        return chartOfAccountsService.updateAccount(
                        id,  // accountCode
                        input.accountName(),
                        input.description()
                )
                .map(entry -> ChartOfAccountsMutationResponse.success(
                        "Account " + entry.getAccountCode() + " updated successfully", entry))
                .onErrorResume(e -> {
                    log.error("Failed to update account {}: {}", id, e.getMessage());
                    return Mono.just(ChartOfAccountsMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Deactivate a Chart of Accounts entry.
     * Schema: deactivateChartOfAccountsEntry(id: ID!): ChartOfAccountsMutationResponse!
     *
     * <p>Note: Accounts with posted journal entries cannot be deactivated.</p>
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChartOfAccountsMutationResponse> deactivateChartOfAccountsEntry(@InputArgument String id) {
        log.info("GraphQL mutation: deactivateChartOfAccountsEntry({})", id);

        return chartOfAccountsService.deactivateAccount(id)
                .map(entry -> ChartOfAccountsMutationResponse.success(
                        "Account " + entry.getAccountCode() + " deactivated successfully", entry))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate account {}: {}", id, e.getMessage());
                    return Mono.just(ChartOfAccountsMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Seed the standard Chart of Accounts.
     * Schema: seedChartOfAccounts: Boolean!
     *
     * <p>Creates the standard account structure for the platform:</p>
     * <pre>
     * 1000 - ASSETS
     * ├── 1011 - Primary Operating Bank Account
     * ├── 1012 - Escrow Bank Account
     * ├── 1021 - Gateway Settlement Receivable
     * ├── 1022 - Commission Receivable (pending)
     * ├── 1023 - Chargeback Recovery Receivable
     * 2000 - LIABILITIES
     * ├── 2010 - Event Escrow (dynamic: 2011-XXXX)
     * ├── 2021 - Organizer Payouts Payable
     * ├── 2022 - Customer Refunds Payable
     * ├── 2023 - Tax Withholding Payable
     * ├── 2031 - Deferred Commission Revenue
     * 3000 - EQUITY
     * ├── 3010 - Retained Earnings
     * 4000 - REVENUE
     * ├── 4010 - Commission Revenue
     * ├── 4020 - Payout Processing Fee Revenue
     * 5000 - EXPENSES
     * ├── 5010 - Payment Gateway Fees
     * ├── 5020 - Chargeback Losses
     * ├── 5040 - Bad Debt Expense
     * </pre>
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Boolean> seedChartOfAccounts() {
        log.info("GraphQL mutation: seedChartOfAccounts");

        return chartOfAccountsService.seedStandardAccounts()
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("Failed to seed chart of accounts: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}
