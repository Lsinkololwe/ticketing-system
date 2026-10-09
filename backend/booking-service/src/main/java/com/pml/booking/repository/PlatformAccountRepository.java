package com.pml.booking.repository;

import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.model.PlatformAccount;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Reactive Repository for Platform Accounts
 *
 * Provides reactive access to the platform_accounts collection in MongoDB.
 * Platform accounts represent the company's own financial accounts, separate
 * from organizer escrow accounts.
 *
 * <h2>Account Type Singleton Pattern</h2>
 * <p>Each {@link PlatformAccountType} should have exactly ONE instance.
 * The service layer enforces this via getOrCreateAccount() pattern:</p>
 *
 * <pre>
 * public Mono<PlatformAccount> getOrCreateOperating() {
 *     return repository.findByAccountType(PlatformAccountType.OPERATING)
 *         .switchIfEmpty(Mono.defer(() ->
 *             repository.save(PlatformAccount.createOperating())
 *         ));
 * }
 * </pre>
 *
 * <h2>Usage Patterns</h2>
 * <ul>
 *   <li><b>Credit commission</b>: Find OPERATING account, call credit()</li>
 *   <li><b>Cover chargeback</b>: Find RESERVE account, call debit()</li>
 *   <li><b>Withhold tax</b>: Find TAX_HOLDING account, call credit()</li>
 * </ul>
 *
 * <h2>Balance Monitoring</h2>
 * <p>Scheduled jobs should monitor account balances and trigger alerts
 * when thresholds are breached:</p>
 * <pre>
 * repository.findByAccountType(PlatformAccountType.RESERVE)
 *     .filter(PlatformAccount::isBelowMinimumThreshold)
 *     .flatMap(account -> alertService.sendLowBalanceAlert(account));
 * </pre>
 *
 * @see PlatformAccount
 * @see PlatformAccountType
 * @since 1.0.0
 */
@Repository
public interface PlatformAccountRepository extends ReactiveMongoRepository<PlatformAccount, String> {

    // ========================================================================
    // ACCOUNT TYPE LOOKUPS (Primary Access Pattern)
    // ========================================================================

    /**
     * Find platform account by type.
     *
     * <p>This is the primary lookup method. Each account type should have
     * exactly one instance.</p>
     *
     * @param accountType The platform account type (OPERATING, RESERVE, TAX_HOLDING)
     * @return Mono containing the account if found
     */
    Mono<PlatformAccount> findByAccountType(PlatformAccountType accountType);

    // ========================================================================
    // ACTIVE ACCOUNT QUERIES
    // ========================================================================

    /**
     * Find all active platform accounts.
     *
     * @return Flux of active accounts
     */
    Flux<PlatformAccount> findByIsActiveTrue();

    // ========================================================================
    // BALANCE QUERIES (Specific Account Types)
    // ========================================================================

    /**
     * Get the operating account balance.
     *
     * <p>Convenience method for dashboard/reporting.</p>
     *
     * @return Mono<BigDecimal> operating account balance
     */
    default Mono<BigDecimal> getOperatingBalance() {
        return findByAccountType(PlatformAccountType.OPERATING)
                .map(PlatformAccount::getBalance)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Get the reserve account balance.
     *
     * @return Mono<BigDecimal> reserve account balance
     */
    default Mono<BigDecimal> getReserveBalance() {
        return findByAccountType(PlatformAccountType.RESERVE)
                .map(PlatformAccount::getBalance)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Get the tax holding account balance.
     *
     * @return Mono<BigDecimal> tax holding account balance
     */
    default Mono<BigDecimal> getTaxHoldingBalance() {
        return findByAccountType(PlatformAccountType.TAX_HOLDING)
                .map(PlatformAccount::getBalance)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Get total balance across all platform accounts.
     *
     * @return Mono<BigDecimal> total of all platform account balances
     */
    default Mono<BigDecimal> getTotalPlatformBalance() {
        return findByIsActiveTrue()
                .map(PlatformAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
