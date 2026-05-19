package com.pml.booking.service;

import com.pml.booking.domain.model.JournalEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Accounting Service Interface - Central Hub for Financial Operations
 *
 * <p>This is the main entry point for all financial operations in the platform.
 * It orchestrates the creation of proper journal entries for each business
 * transaction, ensuring double-entry compliance.</p>
 *
 * <h2>Design Philosophy</h2>
 * <p>Business services (PaymentService, RefundService, PayoutService) should
 * call AccountingService methods rather than directly creating journal entries.
 * This ensures consistent accounting treatment and proper audit trails.</p>
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>Ticket Sale</b>: Gateway receivable → Commission + Escrow</li>
 *   <li><b>Refund</b>: Reversal of original sale entries</li>
 *   <li><b>Payout</b>: Escrow → Bank (organizer payment)</li>
 *   <li><b>Commission Earned</b>: Deferred revenue → Commission revenue</li>
 *   <li><b>Chargeback</b>: Complex multi-entry based on recovery source</li>
 * </ul>
 *
 * <h2>Account Mappings</h2>
 * <pre>
 * Ticket Sale (Customer pays $100, commission 10%, fee $2):
 *   DR Gateway Receivable (1021)    $100.00
 *      CR Event Escrow (2011-XXX)          $88.00  (net to organizer)
 *      CR Deferred Commission (2031)       $10.00  (pending commission)
 *      CR Gateway Fee Payable (2024)       $ 2.00  (gateway processing fee)
 *
 * Commission Earned (after event + hold period):
 *   DR Deferred Commission (2031)   $10.00
 *      CR Commission Revenue (4010)        $10.00
 *
 * Organizer Payout:
 *   DR Event Escrow (2011-XXX)      $88.00
 *      CR Bank Account (1011)              $88.00
 *
 * Chargeback (from escrow):
 *   DR Event Escrow (2011-XXX)      $100.00
 *      CR Chargeback Recovery Rec (1023)   $100.00
 * </pre>
 *
 * @see com.pml.booking.service.JournalService
 * @see com.pml.booking.service.ChartOfAccountsService
 * @since 1.0.0
 */
public interface AccountingService {

    // ========================================================================
    // TICKET SALE OPERATIONS
    // ========================================================================

    /**
     * Records the accounting entries for a ticket sale.
     *
     * <p>Creates a balanced journal entry:</p>
     * <ul>
     *   <li>DR Gateway Settlement Receivable (gross amount)</li>
     *   <li>CR Event Escrow (net amount to organizer)</li>
     *   <li>CR Deferred Commission Revenue (commission amount)</li>
     *   <li>CR Gateway Fee Payable (if applicable)</li>
     * </ul>
     *
     * @param paymentIntentId   Payment intent ID (correlation)
     * @param ticketId          Ticket ID
     * @param eventId           Event ID (for escrow account lookup)
     * @param grossAmount       Total amount charged to customer
     * @param netAmount         Amount credited to escrow (after commission/fees)
     * @param commissionAmount  Platform commission amount
     * @param gatewayFeeAmount  Gateway processing fee
     * @param currency          Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordTicketSale(
            String paymentIntentId,
            String ticketId,
            String eventId,
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal commissionAmount,
            BigDecimal gatewayFeeAmount,
            String currency
    );

    // ========================================================================
    // REFUND OPERATIONS
    // ========================================================================

    /**
     * Records the accounting entries for a refund.
     *
     * <p>Creates a reversal of the original ticket sale entries:</p>
     * <ul>
     *   <li>DR Event Escrow (net amount)</li>
     *   <li>DR Deferred Commission (commission amount)</li>
     *   <li>CR Customer Refunds Payable (gross amount)</li>
     * </ul>
     *
     * @param refundRequestId      Refund request ID (correlation)
     * @param originalPaymentId    Original payment intent ID
     * @param ticketId             Ticket ID
     * @param eventId              Event ID
     * @param refundAmount         Amount being refunded
     * @param commissionClawback   Commission amount being clawed back
     * @param currency             Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordRefund(
            String refundRequestId,
            String originalPaymentId,
            String ticketId,
            String eventId,
            BigDecimal refundAmount,
            BigDecimal commissionClawback,
            String currency
    );

    // ========================================================================
    // PAYOUT OPERATIONS
    // ========================================================================

    /**
     * Records the accounting entries for an organizer payout.
     *
     * <p>Creates a journal entry:</p>
     * <ul>
     *   <li>DR Event Escrow (payout amount)</li>
     *   <li>CR Organizer Payouts Payable (payout amount)</li>
     * </ul>
     *
     * <p>When payout is actually disbursed:</p>
     * <ul>
     *   <li>DR Organizer Payouts Payable</li>
     *   <li>CR Bank Account</li>
     * </ul>
     *
     * @param payoutRequestId Payout request ID (correlation)
     * @param eventId         Event ID
     * @param organizerId     Organizer ID
     * @param payoutAmount    Amount being paid out
     * @param payoutFee       Payout processing fee (if any)
     * @param currency        Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordPayout(
            String payoutRequestId,
            String eventId,
            String organizerId,
            BigDecimal payoutAmount,
            BigDecimal payoutFee,
            String currency
    );

    /**
     * Records the actual disbursement of a payout.
     *
     * <p>Called when funds leave the platform bank account:</p>
     * <ul>
     *   <li>DR Organizer Payouts Payable</li>
     *   <li>CR Bank Account</li>
     * </ul>
     *
     * @param payoutRequestId    Payout request ID
     * @param disbursementAmount Amount disbursed
     * @param bankReference      Bank transaction reference
     * @param currency           Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordPayoutDisbursement(
            String payoutRequestId,
            BigDecimal disbursementAmount,
            String bankReference,
            String currency
    );

    // ========================================================================
    // COMMISSION OPERATIONS
    // ========================================================================

    /**
     * Records commission being earned (revenue recognition).
     *
     * <p>Called after event completion and hold period:</p>
     * <ul>
     *   <li>DR Deferred Commission Revenue (2031)</li>
     *   <li>CR Commission Revenue (4010)</li>
     * </ul>
     *
     * @param commissionRecordId Commission record ID (correlation)
     * @param eventId            Event ID
     * @param amount             Commission amount
     * @param currency           Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordCommissionEarned(
            String commissionRecordId,
            String eventId,
            BigDecimal amount,
            String currency
    );

    /**
     * Records commission clawback (reversal due to refund/chargeback).
     *
     * <p>Reverses previously recorded commission:</p>
     * <ul>
     *   <li>DR Commission Revenue or Deferred Commission</li>
     *   <li>CR Commission Clawback</li>
     * </ul>
     *
     * @param commissionRecordId Commission record ID
     * @param referenceId        Refund or chargeback ID
     * @param amount             Clawback amount
     * @param wasEarned          Whether commission was already earned
     * @param currency           Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordCommissionClawback(
            String commissionRecordId,
            String referenceId,
            BigDecimal amount,
            boolean wasEarned,
            String currency
    );

    // ========================================================================
    // REFUND DISBURSEMENT
    // ========================================================================

    /**
     * Records the actual disbursement of a refund to the customer.
     *
     * <p><b>Business Context:</b></p>
     * <p>When a refund is approved, we first record a liability (Refunds Payable).
     * When we actually send the money to the customer via gateway, we need to
     * clear that liability and record the money leaving our bank.</p>
     *
     * <p><b>Accounting Flow (IN/OUT perspective):</b></p>
     * <pre>
     * When refund is SENT to customer:
     *
     *   REFUNDS PAYABLE (Liability)         BANK ACCOUNT (Asset)
     *   ┌─────────────────────┐             ┌─────────────────────┐
     *   │ IN (Credit)  │ OUT  │             │ IN        │ OUT     │
     *   │ We owe       │(Debit)│             │(Debit)    │(Credit) │
     *   │ customer     │ Paid │             │           │ Money   │
     *   │              │ ✓    │             │           │ out ✓   │
     *   └─────────────────────┘             └─────────────────────┘
     *        K100                                 K100
     *        (cleared)                            (decreased)
     *
     * Journal Entry:
     *   DR Customer Refunds Payable (2022)  K100.00  [OUT - liability cleared]
     *      CR Operating Bank Account (1011)         K100.00  [OUT - money leaves]
     * </pre>
     *
     * @param refundRequestId   Refund request ID (correlation)
     * @param refundAmount      Amount sent to customer
     * @param gatewayReference  Gateway transaction reference
     * @param currency          Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordRefundDisbursement(
            String refundRequestId,
            BigDecimal refundAmount,
            String gatewayReference,
            String currency
    );

    // ========================================================================
    // CHARGEBACK OPERATIONS
    // ========================================================================

    /**
     * Records the RECEIPT of a chargeback from the payment gateway.
     *
     * <p><b>Business Context:</b></p>
     * <p>When a customer disputes a charge, the payment gateway immediately
     * takes the money back from our bank account. This is the FIRST step
     * in the chargeback process - recording that money has left.</p>
     *
     * <p><b>Accounting Flow (IN/OUT perspective):</b></p>
     * <pre>
     * When gateway TAKES money back:
     *
     *   CHARGEBACK RECEIVABLE (Asset)       BANK ACCOUNT (Asset)
     *   ┌─────────────────────┐             ┌─────────────────────┐
     *   │ IN        │ OUT     │             │ IN        │ OUT     │
     *   │(Debit)    │(Credit) │             │(Debit)    │(Credit) │
     *   │ We need   │         │             │           │ Money   │
     *   │ to recover│         │             │           │ taken ✓ │
     *   │ this ✓    │         │             │           │         │
     *   └─────────────────────┘             └─────────────────────┘
     *        K500                                 K500
     *        (we must recover)                    (decreased)
     *
     *   CHARGEBACK FEES (Expense)
     *   ┌─────────────────────┐
     *   │ IN        │ OUT     │
     *   │(Debit)    │(Credit) │
     *   │ Cost to   │         │
     *   │ us ✓      │         │
     *   └─────────────────────┘
     *        K25
     *        (gateway penalty)
     *
     * Journal Entry:
     *   DR Chargeback Recovery Receivable (1023)  K500.00  [IN - we need to recover]
     *   DR Chargeback Fees Expense (5030)          K25.00  [IN - cost to us]
     *      CR Operating Bank Account (1011)               K525.00  [OUT - money taken]
     * </pre>
     *
     * <p><b>Next Step:</b> Call {@link #recordChargeback} for each recovery
     * attempt (escrow, reserve, write-off) to clear the receivable.</p>
     *
     * @param chargebackId      Chargeback record ID (correlation)
     * @param eventId           Event ID
     * @param ticketId          Ticket ID
     * @param chargebackAmount  Amount taken by gateway (original sale)
     * @param chargebackFee     Gateway chargeback penalty fee
     * @param gatewayReference  Gateway chargeback reference
     * @param currency          Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordChargebackReceived(
            String chargebackId,
            String eventId,
            String ticketId,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String gatewayReference,
            String currency
    );

    /**
     * Records accounting entries for a chargeback RECOVERY.
     *
     * <p><b>Business Context:</b></p>
     * <p>After a chargeback is received ({@link #recordChargebackReceived}), we have
     * a Chargeback Recovery Receivable (asset). We must now clear this receivable
     * by recovering funds from available sources in priority order (waterfall).</p>
     *
     * <p><b>Recovery Waterfall (in order):</b></p>
     * <ol>
     *   <li><b>ORGANIZER_ESCROW</b>: Money in event escrow (organizer's funds)</li>
     *   <li><b>ORGANIZER_FUTURE</b>: Pending payouts from organizer's other events</li>
     *   <li><b>PLATFORM_RESERVE</b>: Platform's safety buffer</li>
     *   <li><b>WRITE_OFF</b>: Record as bad debt (unrecoverable loss)</li>
     * </ol>
     *
     * <p><b>Accounting Flow (IN/OUT perspective) - Example from Escrow:</b></p>
     * <pre>
     * Recovering K500 from organizer's escrow:
     *
     *   EVENT ESCROW (Liability)            CHARGEBACK RECEIVABLE (Asset)
     *   ┌─────────────────────┐             ┌─────────────────────┐
     *   │ IN (Credit)  │ OUT  │             │ IN        │ OUT     │
     *   │ Money we owe │(Debit)│             │(Debit)    │(Credit) │
     *   │ organizer    │ Taken│             │ Recovery  │ Cleared │
     *   │              │ for  │             │ needed    │ ✓       │
     *   │              │ CB ✓ │             │           │         │
     *   └─────────────────────┘             └─────────────────────┘
     *        K500                                 K500
     *        (reduced)                            (cleared)
     *
     * Journal Entry (ORGANIZER_ESCROW):
     *   DR Event Escrow (2010-XXX)              K500.00  [OUT - organizer's money taken]
     *      CR Chargeback Recovery Rec (1023)           K500.00  [OUT - receivable cleared]
     *
     * Journal Entry (PLATFORM_RESERVE):
     *   DR Platform Reserve (3020)             K500.00  [OUT - reserve used]
     *      CR Chargeback Recovery Rec (1023)           K500.00  [OUT - receivable cleared]
     *
     * Journal Entry (WRITE_OFF):
     *   DR Bad Debt Expense (5040)             K500.00  [IN - loss recognized]
     *      CR Chargeback Recovery Rec (1023)           K500.00  [OUT - receivable cleared]
     * </pre>
     *
     * @param chargebackId     Chargeback record ID (correlation)
     * @param eventId          Event ID
     * @param ticketId         Ticket ID
     * @param recoveryAmount   Amount being recovered in this step
     * @param chargebackFee    Gateway chargeback fee (only on first call, zero after)
     * @param fundSource       Source: ORGANIZER_ESCROW, ORGANIZER_FUTURE, PLATFORM_RESERVE, WRITE_OFF
     * @param currency         Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordChargeback(
            String chargebackId,
            String eventId,
            String ticketId,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String fundSource,
            String currency
    );

    // ========================================================================
    // GATEWAY SETTLEMENT
    // ========================================================================

    /**
     * Records gateway settlement (funds received in bank).
     *
     * <p>When payment gateway settles funds to our bank:</p>
     * <ul>
     *   <li>DR Bank Account (net settlement amount)</li>
     *   <li>DR Gateway Fees Expense (fees deducted)</li>
     *   <li>CR Gateway Settlement Receivable (gross amount)</li>
     * </ul>
     *
     * @param settlementId     Gateway settlement batch ID
     * @param grossAmount      Gross settlement amount
     * @param feeAmount        Fees deducted by gateway
     * @param netAmount        Net amount received
     * @param settlementDate   Date of settlement
     * @param bankReference    Bank transaction reference
     * @param currency         Currency code
     * @return The posted journal entry
     */
    Mono<JournalEntry> recordGatewaySettlement(
            String settlementId,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            LocalDateTime settlementDate,
            String bankReference,
            String currency
    );

    // ========================================================================
    // BALANCE QUERIES
    // ========================================================================

    /**
     * Gets the current balance for a specific account.
     *
     * <p>Calculates balance from all posted journal entries:
     * Balance = SUM(debits) - SUM(credits) for normal debit accounts
     * Balance = SUM(credits) - SUM(debits) for normal credit accounts</p>
     *
     * @param accountCode The account code
     * @return Current balance
     */
    Mono<BigDecimal> getAccountBalance(String accountCode);

    /**
     * Gets the balance for an account as of a specific date.
     *
     * @param accountCode The account code
     * @param asOfDate    The date to calculate balance up to
     * @return Balance as of the specified date
     */
    Mono<BigDecimal> getAccountBalanceAsOf(String accountCode, LocalDate asOfDate);

    /**
     * Gets a trial balance report.
     *
     * <p>Returns all accounts with their debit/credit balances.
     * For a healthy system: SUM(debit balances) = SUM(credit balances)</p>
     *
     * @param asOfDate Date to calculate balances (null for current)
     * @return Trial balance as list of account balances
     */
    Flux<AccountBalance> getTrialBalance(LocalDate asOfDate);

    /**
     * Represents an account balance in the trial balance.
     */
    record AccountBalance(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal debitBalance,
            BigDecimal creditBalance,
            BigDecimal netBalance
    ) {}

    // ========================================================================
    // RECONCILIATION SUPPORT
    // ========================================================================

    /**
     * Gets the total gateway receivable balance.
     *
     * <p>This should match expected unsettled gateway amounts.</p>
     *
     * @return Gateway receivable balance
     */
    Mono<BigDecimal> getGatewayReceivableBalance();

    /**
     * Gets the total escrow liability balance.
     *
     * <p>This should match the sum of all event escrow accounts.</p>
     *
     * @return Total escrow liability
     */
    Mono<BigDecimal> getTotalEscrowLiability();

    /**
     * Gets the total deferred commission balance.
     *
     * <p>This represents commissions not yet earned (pending events).</p>
     *
     * @return Deferred commission balance
     */
    Mono<BigDecimal> getDeferredCommissionBalance();
}
