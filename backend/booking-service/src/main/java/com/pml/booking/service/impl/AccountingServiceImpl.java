package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.enums.BalanceDirection;
import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.repository.ChartOfAccountsRepository;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.ChartOfAccountsService;
import com.pml.booking.service.JournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accounting Service Implementation - Central Hub for Financial Operations
 *
 * <p>This is the primary entry point for all financial operations. Business
 * services should call AccountingService methods rather than directly creating
 * journal entries, ensuring consistent double-entry compliance.</p>
 *
 * <h2>Chart of Accounts - Key Codes</h2>
 *
 * <h3>ASSETS (Normal Balance: DEBIT - increases with IN/receiving)</h3>
 * <ul>
 *   <li><b>1011</b>: Operating Bank Account (real money in our bank)</li>
 *   <li><b>1021</b>: Gateway Settlement Receivable (money gateway owes us)</li>
 *   <li><b>1023</b>: Chargeback Recovery Receivable (money we need to recover)</li>
 * </ul>
 *
 * <h3>LIABILITIES (Normal Balance: CREDIT - increases with IN/receiving)</h3>
 * <ul>
 *   <li><b>2010-XXX</b>: Event Escrow Accounts (money we owe organizers)</li>
 *   <li><b>2021</b>: Organizer Payouts Payable (approved but not sent)</li>
 *   <li><b>2022</b>: Customer Refunds Payable (approved but not sent)</li>
 *   <li><b>2024</b>: Gateway Fees Payable (fees we owe gateway)</li>
 *   <li><b>2031</b>: Deferred Commission Revenue (commission not yet earned)</li>
 * </ul>
 *
 * <h3>EQUITY (Normal Balance: CREDIT)</h3>
 * <ul>
 *   <li><b>3020</b>: Platform Reserve (safety buffer for chargebacks)</li>
 * </ul>
 *
 * <h3>REVENUE (Normal Balance: CREDIT - increases with IN/receiving)</h3>
 * <ul>
 *   <li><b>4010</b>: Commission Revenue (earned commission)</li>
 *   <li><b>4020</b>: Fee Revenue (payout processing fees)</li>
 * </ul>
 *
 * <h3>EXPENSES (Normal Balance: DEBIT - increases with IN/receiving)</h3>
 * <ul>
 *   <li><b>5010</b>: Payment Gateway Fees (gateway processing costs)</li>
 *   <li><b>5020</b>: Chargeback Loss (chargeback amounts)</li>
 *   <li><b>5030</b>: Chargeback Fees (gateway chargeback penalty fees)</li>
 *   <li><b>5040</b>: Bad Debt Expense (unrecoverable amounts written off)</li>
 * </ul>
 *
 * @see AccountingService
 * @see JournalService
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingServiceImpl implements AccountingService {

    private final JournalService journalService;
    private final ChartOfAccountsService chartOfAccountsService;
    private final ChartOfAccountsRepository accountsRepository;
    private final JournalEntryRepository journalRepository;

    // ========================================================================
    // CHART OF ACCOUNTS - Account Code Constants
    // ========================================================================
    //
    // DEBIT/CREDIT QUICK REFERENCE:
    // - Assets (1xxx) & Expenses (5xxx): DEBIT = increase (IN), CREDIT = decrease (OUT)
    // - Liabilities (2xxx), Equity (3xxx), Revenue (4xxx): CREDIT = increase (IN), DEBIT = decrease (OUT)
    //
    // ========================================================================

    // ASSETS (Normal Balance: DEBIT)
    private static final String OPERATING_BANK = "1011";           // Real money in our bank
    private static final String GATEWAY_RECEIVABLE = "1021";       // Money gateway owes us
    private static final String CHARGEBACK_RECEIVABLE = "1023";    // Money we need to recover

    // LIABILITIES (Normal Balance: CREDIT)
    private static final String ESCROW_PARENT = "2010";            // Parent code for escrow accounts
    private static final String ORGANIZER_PAYOUTS_PAYABLE = "2021"; // Payouts approved, not sent
    private static final String CUSTOMER_REFUNDS_PAYABLE = "2022"; // Refunds approved, not sent
    private static final String GATEWAY_FEES_PAYABLE = "2024";     // Gateway fees we owe
    private static final String DEFERRED_COMMISSION = "2031";      // Commission not yet earned

    // EQUITY (Normal Balance: CREDIT)
    private static final String PLATFORM_RESERVE = "3020";         // Safety buffer for chargebacks

    // REVENUE (Normal Balance: CREDIT)
    private static final String COMMISSION_REVENUE = "4010";       // Earned commission
    private static final String FEE_REVENUE = "4020";              // Payout processing fees

    // EXPENSES (Normal Balance: DEBIT)
    private static final String GATEWAY_FEES_EXPENSE = "5010";     // Gateway processing costs
    private static final String CHARGEBACK_LOSS = "5020";          // Chargeback amounts (unused alias)
    private static final String CHARGEBACK_FEES_EXPENSE = "5030";  // Gateway chargeback penalty
    private static final String BAD_DEBT_EXPENSE = "5040";         // Unrecoverable write-offs

    // ========================================================================
    // TICKET SALE OPERATIONS
    // ========================================================================

    /**
     * Records the accounting entries for a ticket sale.
     *
     * <h3>Business Flow</h3>
     * <p>Customer pays K100 for a ticket. The money is split:</p>
     * <ul>
     *   <li>K88 → Organizer's escrow (they'll receive this after event)</li>
     *   <li>K10 → Platform commission (deferred until event completes)</li>
     *   <li>K2 → Gateway fees (we owe this to PawaPay)</li>
     * </ul>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ TICKET SALE: Customer pays K100 (10% commission, K2 gateway fee)       │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Money IN / Obligations reduced):                             │
     * │   ─────────────────────────────────────────                            │
     * │   DR Gateway Receivable (1021)      K100.00  [IN - gateway owes us]    │
     * │                                                                        │
     * │   CREDITS (Obligations created / Money OUT):                           │
     * │   ──────────────────────────────────────────                           │
     * │      CR Event Escrow (2010-XXX)           K88.00  [IN - we owe org.]   │
     * │      CR Deferred Commission (2031)        K10.00  [IN - pending comm.] │
     * │      CR Gateway Fees Payable (2024)       K 2.00  [IN - we owe gateway]│
     * │                                                                        │
     * │   Balance: K100.00 = K88.00 + K10.00 + K2.00 ✓                        │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
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
    @Override
    @Transactional
    public Mono<JournalEntry> recordTicketSale(
            String paymentIntentId,
            String ticketId,
            String eventId,
            BigDecimal grossAmount,
            BigDecimal netAmount,
            BigDecimal commissionAmount,
            BigDecimal gatewayFeeAmount,
            String currency
    ) {
        log.info("Recording ticket sale: payment={}, ticket={}, gross={}",
                paymentIntentId, ticketId, grossAmount);

        String description = "Ticket sale: " + ticketId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // ========================================================================
        // DEBIT: Gateway Receivable (ASSET)
        // Gateway will settle this amount to us (T+1)
        // Asset increases = DEBIT = Money IN
        // ========================================================================
        lines.add(debitGatewayReceivable(grossAmount, description));

        // ========================================================================
        // CREDIT: Event Escrow (LIABILITY)
        // We now owe this amount to the organizer
        // Liability increases = CREDIT = Money IN (to liability)
        // ========================================================================
        lines.add(creditEventEscrow(eventId, netAmount, description));

        // ========================================================================
        // CREDIT: Deferred Commission (LIABILITY)
        // Commission is deferred until event completes + hold period
        // Liability increases = CREDIT
        // ========================================================================
        if (hasValue(commissionAmount)) {
            lines.add(creditDeferredCommission(commissionAmount, "Commission: " + ticketId));
        }

        // ========================================================================
        // CREDIT: Gateway Fees Payable (LIABILITY)
        // We owe gateway their processing fee
        // Liability increases = CREDIT
        // ========================================================================
        if (hasValue(gatewayFeeAmount)) {
            lines.add(creditGatewayFeesPayable(gatewayFeeAmount, "Gateway fee: " + ticketId));
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("ticketId", ticketId);
        metadata.put("eventId", eventId);
        metadata.put("transactionType", "TICKET_SALE");

        return journalService.createAndPostEntry(
                paymentIntentId,
                LocalDateTime.now(),
                description,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // REFUND OPERATIONS
    // ========================================================================

    /**
     * Records the accounting entries for a refund (Step 1: Create liability).
     *
     * <h3>Business Flow</h3>
     * <p>Customer requests refund for K98 ticket before event. We reverse:</p>
     * <ul>
     *   <li>K88 from organizer's escrow (they never earned this)</li>
     *   <li>K10 from deferred commission (we never earned this)</li>
     *   <li>Create K98 liability to pay customer</li>
     * </ul>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ REFUND CREATION: K98 refund (K88 escrow + K10 commission)              │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Reversing liabilities):                                      │
     * │   ───────────────────────────────                                      │
     * │   DR Event Escrow (2010-XXX)        K88.00  [OUT - org.'s money taken] │
     * │   DR Deferred Commission (2031)     K10.00  [OUT - commission returned]│
     * │                                                                        │
     * │   CREDITS (Creating new liability):                                    │
     * │   ─────────────────────────────────                                    │
     * │      CR Customer Refunds Payable (2022)   K98.00  [IN - we owe cust.]  │
     * │                                                                        │
     * │   Balance: K88.00 + K10.00 = K98.00 ✓                                 │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * <h3>Next Step</h3>
     * <p>When gateway sends money to customer, call {@link #recordRefundDisbursement}
     * to clear the Refunds Payable liability.</p>
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
    @Override
    @Transactional
    public Mono<JournalEntry> recordRefund(
            String refundRequestId,
            String originalPaymentId,
            String ticketId,
            String eventId,
            BigDecimal refundAmount,
            BigDecimal commissionClawback,
            String currency
    ) {
        log.info("Recording refund: request={}, ticket={}, amount={}",
                refundRequestId, ticketId, refundAmount);

        String description = "Refund: " + ticketId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // Calculate escrow portion (refund minus commission)
        BigDecimal escrowDebit = refundAmount.subtract(commissionClawback);

        // ========================================================================
        // DEBIT: Event Escrow (LIABILITY)
        // Taking back what we owed the organizer
        // Liability decreases = DEBIT = Money OUT (from organizer's perspective)
        // ========================================================================
        lines.add(debitEventEscrow(eventId, escrowDebit, description));

        // ========================================================================
        // DEBIT: Deferred Commission (LIABILITY)
        // Returning commission that was never earned
        // Liability decreases = DEBIT = Money OUT
        // ========================================================================
        if (hasValue(commissionClawback)) {
            lines.add(debitDeferredCommission(commissionClawback, "Commission clawback: " + ticketId));
        }

        // ========================================================================
        // CREDIT: Customer Refunds Payable (LIABILITY)
        // Creating obligation to pay customer
        // Liability increases = CREDIT = Money IN (we now owe customer)
        // ========================================================================
        lines.add(creditRefundsPayable(refundAmount, "Refund payable: " + ticketId));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("ticketId", ticketId);
        metadata.put("eventId", eventId);
        metadata.put("originalPaymentId", originalPaymentId);
        metadata.put("transactionType", "REFUND");

        return journalService.createAndPostEntry(
                refundRequestId,
                LocalDateTime.now(),
                description,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // PAYOUT OPERATIONS
    // ========================================================================

    /**
     * Records the accounting entries for a payout approval (Step 1: Create liability).
     *
     * <h3>Business Flow</h3>
     * <p>Organizer requests K88 payout with K3 fee. We:</p>
     * <ul>
     *   <li>Release K88 from escrow (organizer's funds)</li>
     *   <li>Create K85 payout liability (what we'll send them)</li>
     *   <li>Recognize K3 fee revenue (our processing fee)</li>
     * </ul>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ PAYOUT APPROVAL: K88 escrow → K85 payout + K3 fee                      │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Releasing escrow):                                           │
     * │   ──────────────────────────                                           │
     * │   DR Event Escrow (2010-XXX)        K88.00  [OUT - escrow released]    │
     * │                                                                        │
     * │   CREDITS (Creating obligation + earning fee):                         │
     * │   ────────────────────────────────────────────                         │
     * │      CR Organizer Payouts Payable (2021)  K85.00  [IN - we owe org.]   │
     * │      CR Fee Revenue (4020)                K 3.00  [IN - fee earned]    │
     * │                                                                        │
     * │   Balance: K88.00 = K85.00 + K3.00 ✓                                  │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * <h3>Next Step</h3>
     * <p>When bank sends money to organizer, call {@link #recordPayoutDisbursement}
     * to clear the Payouts Payable liability.</p>
     *
     * @param payoutRequestId Payout request ID (correlation)
     * @param eventId         Event ID
     * @param organizerId     Organizer ID
     * @param payoutAmount    Net amount to be paid to organizer
     * @param payoutFee       Payout processing fee (if any)
     * @param currency        Currency code
     * @return The posted journal entry
     */
    @Override
    @Transactional
    public Mono<JournalEntry> recordPayout(
            String payoutRequestId,
            String eventId,
            String organizerId,
            BigDecimal payoutAmount,
            BigDecimal payoutFee,
            String currency
    ) {
        log.info("Recording payout: request={}, event={}, amount={}",
                payoutRequestId, eventId, payoutAmount);

        String description = "Payout: " + payoutRequestId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // Calculate total escrow debit (net payout + fee)
        BigDecimal totalDebit = payoutAmount.add(payoutFee != null ? payoutFee : BigDecimal.ZERO);

        // ========================================================================
        // DEBIT: Event Escrow (LIABILITY)
        // Releasing funds from organizer's escrow (full amount including fee)
        // Liability decreases = DEBIT = Money OUT
        // ========================================================================
        lines.add(debitEventEscrow(eventId, totalDebit, description));

        // ========================================================================
        // CREDIT: Organizer Payouts Payable (LIABILITY)
        // Creating obligation to pay organizer (net of fee)
        // Liability increases = CREDIT = Money IN (we now owe organizer)
        // ========================================================================
        lines.add(creditPayoutsPayable(payoutAmount, description));

        // ========================================================================
        // CREDIT: Fee Revenue (REVENUE)
        // Recognizing payout processing fee as revenue
        // Revenue increases = CREDIT = Money IN
        // ========================================================================
        if (hasValue(payoutFee)) {
            lines.add(creditFeeRevenue(payoutFee, "Payout fee: " + payoutRequestId));
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("eventId", eventId);
        metadata.put("organizerId", organizerId);
        metadata.put("transactionType", "PAYOUT");

        return journalService.createAndPostEntry(
                payoutRequestId,
                LocalDateTime.now(),
                "Organizer payout: " + payoutRequestId,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    /**
     * Records the actual disbursement of a payout (Step 2: Clear liability).
     *
     * <h3>Business Flow</h3>
     * <p>Bank sends K85 to organizer's account. We clear our obligation.</p>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ PAYOUT DISBURSEMENT: K85 sent to organizer                             │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Clearing liability):                                         │
     * │   ────────────────────────────                                         │
     * │   DR Organizer Payouts Payable (2021)  K85.00  [OUT - liability cleared│
     * │                                                                        │
     * │   CREDITS (Money leaving bank):                                        │
     * │   ─────────────────────────────                                        │
     * │      CR Operating Bank (1011)          K85.00  [OUT - money sent]      │
     * │                                                                        │
     * │   Result: Liability cleared, bank balance reduced                      │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param payoutRequestId    Payout request ID
     * @param disbursementAmount Amount disbursed
     * @param bankReference      Bank transaction reference
     * @param currency           Currency code
     * @return The posted journal entry
     */
    @Override
    @Transactional
    public Mono<JournalEntry> recordPayoutDisbursement(
            String payoutRequestId,
            BigDecimal disbursementAmount,
            String bankReference,
            String currency
    ) {
        log.info("Recording payout disbursement: request={}, amount={}",
                payoutRequestId, disbursementAmount);

        String description = "Disbursement: " + payoutRequestId;

        // ========================================================================
        // DEBIT: Organizer Payouts Payable (LIABILITY)
        // Clearing our obligation to pay organizer
        // Liability decreases = DEBIT = Money OUT (we no longer owe this)
        //
        // CREDIT: Operating Bank (ASSET)
        // Money physically leaving our bank account
        // Asset decreases = CREDIT = Money OUT
        // ========================================================================
        List<JournalLine> lines = List.of(
                debitPayoutsPayable(disbursementAmount, description),
                creditOperatingBank(disbursementAmount, description)
        );

        Map<String, String> metadata = new HashMap<>();
        metadata.put("bankReference", bankReference);
        metadata.put("transactionType", "DISBURSEMENT");

        return journalService.createAndPostEntry(
                payoutRequestId + "-DISB",
                LocalDateTime.now(),
                "Payout disbursement: " + payoutRequestId,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // REFUND DISBURSEMENT
    // ========================================================================

    /**
     * Records the actual disbursement of a refund to the customer (Step 2: Clear liability).
     *
     * <h3>Business Flow</h3>
     * <p>Gateway sends K98 back to customer's mobile money account. We clear our obligation.</p>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ REFUND DISBURSEMENT: K98 sent to customer                              │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Clearing liability):                                         │
     * │   ────────────────────────────                                         │
     * │   DR Customer Refunds Payable (2022)  K98.00  [OUT - liability cleared]│
     * │                                                                        │
     * │   CREDITS (Money leaving bank):                                        │
     * │   ─────────────────────────────                                        │
     * │      CR Operating Bank (1011)         K98.00  [OUT - money sent]       │
     * │                                                                        │
     * │   Result: Liability cleared, bank balance reduced                      │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param refundRequestId   Refund request ID
     * @param refundAmount      Amount sent to customer
     * @param gatewayReference  Gateway transaction reference
     * @param currency          Currency code
     * @return The posted journal entry
     */
    @Override
    @Transactional
    public Mono<JournalEntry> recordRefundDisbursement(
            String refundRequestId,
            BigDecimal refundAmount,
            String gatewayReference,
            String currency
    ) {
        log.info("Recording refund disbursement: request={}, amount={}, ref={}",
                refundRequestId, refundAmount, gatewayReference);

        String description = "Refund disbursed: " + refundRequestId;

        // ========================================================================
        // DEBIT: Customer Refunds Payable (LIABILITY)
        // Clearing our obligation to pay customer
        // Liability decreases = DEBIT = Money OUT (we no longer owe this)
        //
        // CREDIT: Operating Bank (ASSET)
        // Money physically leaving our bank via gateway
        // Asset decreases = CREDIT = Money OUT
        // ========================================================================
        List<JournalLine> lines = List.of(
                debitRefundsPayable(refundAmount, description),
                creditOperatingBank(refundAmount, description)
        );

        Map<String, String> metadata = new HashMap<>();
        metadata.put("gatewayReference", gatewayReference);
        metadata.put("transactionType", "REFUND_DISBURSEMENT");

        return journalService.createAndPostEntry(
                refundRequestId + "-DISB",
                LocalDateTime.now(),
                "Refund disbursement: " + refundRequestId,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // COMMISSION OPERATIONS
    // ========================================================================

    /**
     * Records commission being earned (revenue recognition).
     *
     * <h3>Business Flow</h3>
     * <p>Event completed + hold period passed. Commission moves from deferred to earned.</p>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ COMMISSION EARNED: K10 deferred → K10 revenue                          │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Releasing deferred):                                         │
     * │   ────────────────────────────                                         │
     * │   DR Deferred Commission (2031)    K10.00  [OUT - deferred reduced]    │
     * │                                                                        │
     * │   CREDITS (Recognizing revenue):                                       │
     * │   ──────────────────────────────                                       │
     * │      CR Commission Revenue (4010)  K10.00  [IN - revenue recognized]   │
     * │                                                                        │
     * │   Result: Commission now officially earned as revenue                  │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param commissionRecordId Commission record ID (correlation)
     * @param eventId            Event ID
     * @param amount             Commission amount
     * @param currency           Currency code
     * @return The posted journal entry
     */
    @Override
    @Transactional
    public Mono<JournalEntry> recordCommissionEarned(
            String commissionRecordId,
            String eventId,
            BigDecimal amount,
            String currency
    ) {
        log.info("Recording commission earned: commission={}, event={}, amount={}",
                commissionRecordId, eventId, amount);

        String description = "Commission earned: " + commissionRecordId;

        // ========================================================================
        // DEBIT: Deferred Commission (LIABILITY)
        // Moving commission from deferred (pending) state
        // Liability decreases = DEBIT = Money OUT
        //
        // CREDIT: Commission Revenue (REVENUE)
        // Recognizing commission as earned revenue
        // Revenue increases = CREDIT = Money IN
        // ========================================================================
        List<JournalLine> lines = List.of(
                debitDeferredCommission(amount, description),
                creditCommissionRevenue(amount, description)
        );

        Map<String, String> metadata = new HashMap<>();
        metadata.put("eventId", eventId);
        metadata.put("transactionType", "COMMISSION_EARNED");

        return journalService.createAndPostEntry(
                commissionRecordId,
                LocalDateTime.now(),
                "Commission earned for event: " + eventId,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    /**
     * Records commission clawback (reversal due to refund/chargeback).
     *
     * <h3>Business Flow</h3>
     * <p>Commission needs to be reversed due to chargeback. Source depends on timing:</p>
     * <ul>
     *   <li><b>wasEarned=false</b>: Commission still in Deferred (event not completed)</li>
     *   <li><b>wasEarned=true</b>: Commission already in Revenue (event completed)</li>
     * </ul>
     *
     * <h3>Accounting Entry (from Deferred)</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ COMMISSION CLAWBACK (before event): K10 clawback                       │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DR Deferred Commission (2031)      K10.00  [OUT - deferred reduced]  │
     * │      CR Chargeback Receivable (1023) K10.00  [OUT - receivable cleared]│
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * <h3>Accounting Entry (from Revenue)</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ COMMISSION CLAWBACK (after event): K10 clawback                        │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DR Commission Revenue (4010)       K10.00  [OUT - revenue reversed]  │
     * │      CR Chargeback Receivable (1023) K10.00  [OUT - receivable cleared]│
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param commissionRecordId Commission record ID
     * @param referenceId        Refund or chargeback ID
     * @param amount             Clawback amount
     * @param wasEarned          Whether commission was already earned
     * @param currency           Currency code
     * @return The posted journal entry
     */
    @Override
    @Transactional
    public Mono<JournalEntry> recordCommissionClawback(
            String commissionRecordId,
            String referenceId,
            BigDecimal amount,
            boolean wasEarned,
            String currency
    ) {
        log.info("Recording commission clawback: commission={}, wasEarned={}, amount={}",
                commissionRecordId, wasEarned, amount);

        String description = "Commission clawback: " + commissionRecordId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // ========================================================================
        // DEBIT: Commission Source (varies based on wasEarned)
        // - wasEarned=false: Debit Deferred Commission (LIABILITY) - reduces deferred
        // - wasEarned=true: Debit Commission Revenue (REVENUE) - reverses revenue
        // ========================================================================
        if (wasEarned) {
            // Commission was already earned - reverse from revenue
            lines.add(debitCommissionRevenue(amount, description));
        } else {
            // Commission still deferred - reduce the deferred amount
            lines.add(debitDeferredCommission(amount, description));
        }

        // ========================================================================
        // CREDIT: Chargeback Receivable (ASSET)
        // This credits (reduces) the chargeback receivable
        // Part of the chargeback recovery waterfall
        // Asset decreases = CREDIT = Money OUT
        // ========================================================================
        lines.add(creditChargebackReceivable(amount, description));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("referenceId", referenceId);
        metadata.put("wasEarned", String.valueOf(wasEarned));
        metadata.put("transactionType", "COMMISSION_CLAWBACK");

        return journalService.createAndPostEntry(
                commissionRecordId + "-CLAWBACK",
                LocalDateTime.now(),
                "Commission clawback: " + commissionRecordId,
                JournalEntryType.ADJUSTMENT,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // CHARGEBACK OPERATIONS
    // ========================================================================
    //
    // CHARGEBACK LIFECYCLE (Two-Step Process):
    //
    // STEP 1: recordChargebackReceived() - Gateway takes money from our bank
    //    - Creates Chargeback Recovery Receivable (we need to recover this)
    //    - Records Chargeback Fee Expense (gateway penalty - platform absorbs)
    //    - Credits (reduces) our bank account
    //
    // STEP 2: recordChargeback() - We recover funds (may be called multiple times)
    //    - Called for EACH source in the recovery waterfall
    //    - Clears the Chargeback Recovery Receivable as we recover
    //    - Sources in order: ESCROW → FUTURE_PAYOUTS → RESERVE → WRITE_OFF
    //
    // ========================================================================

    /**
     * Records the RECEIPT of a chargeback from the payment gateway (Step 1).
     *
     * <h3>Business Flow</h3>
     * <p>Gateway notifies us: "Customer disputed K500 charge, plus K25 penalty fee."
     * Gateway immediately debits K525 from our bank account.</p>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ CHARGEBACK RECEIVED: Gateway takes K525 (K500 sale + K25 fee)          │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Creating receivable + recording expense):                    │
     * │   ──────────────────────────────────────────────────                   │
     * │   DR Chargeback Receivable (1023)     K500.00  [IN - we need to recover│
     * │   DR Chargeback Fees Expense (5030)    K25.00  [IN - penalty cost]     │
     * │                                                                        │
     * │   CREDITS (Money leaving bank):                                        │
     * │   ─────────────────────────────                                        │
     * │      CR Operating Bank (1011)         K525.00  [OUT - gateway took it] │
     * │                                                                        │
     * │   Balance: K500 + K25 = K525 ✓                                        │
     * │   Note: The K25 fee is platform expense, NOT recoverable from organizer│
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * <h3>Next Step</h3>
     * <p>Call {@link #recordChargeback} for each recovery step (escrow, reserve, write-off)
     * to clear the K500 Chargeback Receivable.</p>
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
    @Override
    @Transactional
    public Mono<JournalEntry> recordChargebackReceived(
            String chargebackId,
            String eventId,
            String ticketId,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String gatewayReference,
            String currency
    ) {
        log.info("Recording chargeback RECEIVED: id={}, amount={}, fee={}, ref={}",
                chargebackId, chargebackAmount, chargebackFee, gatewayReference);

        String description = "Chargeback received: " + chargebackId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // ========================================================================
        // DEBIT: Chargeback Recovery Receivable (ASSET)
        // Creating a receivable for the amount we need to recover from organizer
        // Asset increases = DEBIT = Money IN (we're owed this)
        // ========================================================================
        lines.add(debitChargebackReceivable(chargebackAmount, description));

        // ========================================================================
        // DEBIT: Chargeback Fees Expense (EXPENSE)
        // Gateway penalty fee - this is a direct cost to the platform
        // NOT recoverable from organizer (we absorb this loss)
        // Expense increases = DEBIT = Money IN (cost incurred)
        // ========================================================================
        if (hasValue(chargebackFee)) {
            lines.add(debitChargebackFeesExpense(chargebackFee, "Chargeback fee: " + chargebackId));
        }

        // ========================================================================
        // CREDIT: Operating Bank (ASSET)
        // Gateway has already taken this money from our bank
        // Asset decreases = CREDIT = Money OUT
        // ========================================================================
        BigDecimal totalDeducted = chargebackAmount.add(
                chargebackFee != null ? chargebackFee : BigDecimal.ZERO
        );
        lines.add(creditOperatingBank(totalDeducted, "Chargeback debit: " + chargebackId));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("ticketId", ticketId);
        metadata.put("eventId", eventId);
        metadata.put("gatewayReference", gatewayReference);
        metadata.put("transactionType", "CHARGEBACK_RECEIVED");

        return journalService.createAndPostEntry(
                chargebackId + "-RCVD",
                LocalDateTime.now(),
                description,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    /**
     * Records a chargeback RECOVERY step (Step 2) - clearing receivable from a source.
     *
     * <h3>Business Flow</h3>
     * <p>After chargeback received, we recover K500 using the waterfall:</p>
     * <ol>
     *   <li><b>ORGANIZER_ESCROW</b>: Take from organizer's event escrow first</li>
     *   <li><b>ORGANIZER_FUTURE</b>: Deduct from organizer's pending payouts</li>
     *   <li><b>PLATFORM_RESERVE</b>: Use platform's safety buffer</li>
     *   <li><b>WRITE_OFF</b>: Record as bad debt (unrecoverable loss)</li>
     * </ol>
     *
     * <h3>Accounting Entry (from ORGANIZER_ESCROW)</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ CHARGEBACK RECOVERY: K500 from organizer's escrow                      │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DR Event Escrow (2010-XXX)          K500.00  [OUT - org's money taken]│
     * │      CR Chargeback Receivable (1023)  K500.00  [OUT - receivable cleared│
     * │                                                                        │
     * │   Result: Organizer bears the chargeback cost (from their escrow)      │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * <h3>Accounting Entry (from PLATFORM_RESERVE)</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ CHARGEBACK RECOVERY: K300 from platform reserve (escrow was short)     │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DR Platform Reserve (3020)          K300.00  [OUT - reserve used]    │
     * │      CR Chargeback Receivable (1023)  K300.00  [OUT - receivable cleared│
     * │                                                                        │
     * │   Result: Platform absorbs this portion from safety buffer             │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * <h3>Accounting Entry (WRITE_OFF)</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ CHARGEBACK WRITE-OFF: K100 unrecoverable (reserve also insufficient)   │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DR Bad Debt Expense (5040)          K100.00  [IN - loss recognized]  │
     * │      CR Chargeback Receivable (1023)  K100.00  [OUT - written off]     │
     * │                                                                        │
     * │   Result: Unrecoverable loss recorded as expense                       │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
     *
     * @param chargebackId     Chargeback record ID (correlation)
     * @param eventId          Event ID
     * @param ticketId         Ticket ID
     * @param recoveryAmount   Amount being recovered in this step
     * @param chargebackFee    IGNORED - fees handled in recordChargebackReceived()
     * @param fundSource       Source: ORGANIZER_ESCROW, ORGANIZER_FUTURE, PLATFORM_RESERVE, WRITE_OFF
     * @param currency         Currency code
     * @return The posted journal entry
     */
    @Override
    @Transactional
    public Mono<JournalEntry> recordChargeback(
            String chargebackId,
            String eventId,
            String ticketId,
            BigDecimal recoveryAmount,
            BigDecimal chargebackFee,  // IGNORED - fee handled in recordChargebackReceived
            String fundSource,
            String currency
    ) {
        log.info("Recording chargeback RECOVERY: id={}, event={}, amount={}, source={}",
                chargebackId, eventId, recoveryAmount, fundSource);

        String description = "Chargeback recovery: " + chargebackId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // ========================================================================
        // DEBIT: Recovery Source (varies based on fundSource)
        // Each source type has different accounting implications:
        //
        // ORGANIZER_ESCROW: Debit Event Escrow (LIABILITY)
        //   - Reduces what we owe organizer
        //   - Organizer bears the chargeback cost
        //
        // ORGANIZER_FUTURE: Debit Payouts Payable (LIABILITY)
        //   - Reduces pending payouts to organizer
        //   - Recovery from organizer's future earnings
        //
        // PLATFORM_RESERVE: Debit Platform Reserve (EQUITY)
        //   - Uses platform's safety buffer
        //   - Platform absorbs this cost
        //
        // WRITE_OFF: Debit Bad Debt Expense (EXPENSE)
        //   - Records unrecoverable loss
        //   - Last resort when all sources exhausted
        // ========================================================================
        switch (fundSource) {
            case "ORGANIZER_ESCROW":
                lines.add(debitEventEscrow(eventId, recoveryAmount, description));
                break;
            case "ORGANIZER_FUTURE":
                lines.add(debitPayoutsPayable(recoveryAmount, description));
                break;
            case "PLATFORM_RESERVE":
                lines.add(debitPlatformReserve(recoveryAmount, description));
                break;
            case "WRITE_OFF":
                lines.add(debitBadDebtExpense(recoveryAmount, description));
                break;
            default:
                log.warn("Unknown fund source '{}', defaulting to escrow", fundSource);
                lines.add(debitEventEscrow(eventId, recoveryAmount, description));
        }

        // ========================================================================
        // CREDIT: Chargeback Recovery Receivable (ASSET)
        // Clearing the receivable as we recover funds
        // Asset decreases = CREDIT = Money OUT (receivable cleared)
        // ========================================================================
        lines.add(creditChargebackReceivable(recoveryAmount, description));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("ticketId", ticketId);
        metadata.put("eventId", eventId);
        metadata.put("fundSource", fundSource);
        metadata.put("transactionType", "CHARGEBACK_RECOVERY");

        // Use unique correlation ID per recovery step
        String correlationId = chargebackId + "-" + fundSource.substring(0, Math.min(4, fundSource.length()));

        return journalService.createAndPostEntry(
                correlationId,
                LocalDateTime.now(),
                "Chargeback recovery from " + fundSource + ": " + chargebackId,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // GATEWAY SETTLEMENT
    // ========================================================================

    /**
     * Records gateway settlement (funds received in bank - typically T+1).
     *
     * <h3>Business Flow</h3>
     * <p>PawaPay settles daily collections. Example: K10,000 gross, K200 fees, K9,800 net.</p>
     * <ul>
     *   <li>We receive K9,800 in our bank account (net of fees)</li>
     *   <li>We recognize K200 as gateway fees expense</li>
     *   <li>We clear K10,000 from Gateway Receivable</li>
     * </ul>
     *
     * <h3>Accounting Entry</h3>
     * <pre>
     * ┌────────────────────────────────────────────────────────────────────────┐
     * │ GATEWAY SETTLEMENT: K10,000 settled (K9,800 net after K200 fees)       │
     * ├────────────────────────────────────────────────────────────────────────┤
     * │                                                                        │
     * │   DEBITS (Money in + expense):                                         │
     * │   ────────────────────────────                                         │
     * │   DR Operating Bank (1011)          K9,800.00  [IN - money received]   │
     * │   DR Gateway Fees Expense (5010)      K200.00  [IN - fee cost]         │
     * │                                                                        │
     * │   CREDITS (Clearing receivable):                                       │
     * │   ──────────────────────────────                                       │
     * │      CR Gateway Receivable (1021)  K10,000.00  [OUT - receivable clear]│
     * │                                                                        │
     * │   Balance: K9,800 + K200 = K10,000 ✓                                  │
     * │   Result: Bank increased, receivable cleared, fees expensed            │
     * └────────────────────────────────────────────────────────────────────────┘
     * </pre>
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
    @Override
    @Transactional
    public Mono<JournalEntry> recordGatewaySettlement(
            String settlementId,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            LocalDateTime settlementDate,
            String bankReference,
            String currency
    ) {
        log.info("Recording gateway settlement: id={}, gross={}, net={}",
                settlementId, grossAmount, netAmount);

        String description = "Settlement: " + settlementId;
        List<JournalLine> lines = new java.util.ArrayList<>();

        // ========================================================================
        // DEBIT: Operating Bank (ASSET)
        // Money arriving in our bank account (net of fees)
        // Asset increases = DEBIT = Money IN
        // ========================================================================
        lines.add(debitOperatingBank(netAmount, description));

        // ========================================================================
        // DEBIT: Gateway Fees Expense (EXPENSE)
        // Recognizing the fees deducted by gateway
        // Expense increases = DEBIT = Money IN (cost incurred)
        // ========================================================================
        if (hasValue(feeAmount)) {
            lines.add(debitGatewayFeesExpense(feeAmount, "Settlement fees: " + settlementId));
        }

        // ========================================================================
        // CREDIT: Gateway Receivable (ASSET)
        // Clearing the receivable - gateway has paid what they owed us
        // Asset decreases = CREDIT = Money OUT (no longer owed)
        // ========================================================================
        lines.add(creditGatewayReceivable(grossAmount, description));

        Map<String, String> metadata = new HashMap<>();
        metadata.put("bankReference", bankReference);
        metadata.put("settlementDate", settlementDate.toString());
        metadata.put("transactionType", "GATEWAY_SETTLEMENT");

        return journalService.createAndPostEntry(
                settlementId,
                settlementDate,
                "Gateway settlement: " + settlementId,
                JournalEntryType.STANDARD,
                lines,
                "SYSTEM",
                metadata
        );
    }

    // ========================================================================
    // BALANCE QUERIES
    // ========================================================================

    @Override
    public Mono<BigDecimal> getAccountBalance(String accountCode) {
        return getAccountBalanceAsOf(accountCode, LocalDate.now());
    }

    @Override
    public Mono<BigDecimal> getAccountBalanceAsOf(String accountCode, LocalDate asOfDate) {
        return accountsRepository.findByAccountCode(accountCode)
                .flatMap(account -> {
                    BalanceDirection normalBalance = account.getNormalBalance();

                    return journalRepository.findByAccountCode(accountCode)
                            .filter(entry -> entry.getStatus() == com.pml.booking.domain.enums.JournalEntryStatus.POSTED)
                            .filter(entry -> !entry.getEntryDate().isAfter(asOfDate))
                            .flatMapIterable(JournalEntry::getLines)
                            .filter(line -> line.getAccountCode().equals(accountCode))
                            .reduce(BigDecimal.ZERO, (balance, line) -> {
                                BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
                                BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;

                                if (normalBalance == BalanceDirection.DEBIT) {
                                    return balance.add(debit).subtract(credit);
                                } else {
                                    return balance.add(credit).subtract(debit);
                                }
                            });
                })
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Flux<AccountBalance> getTrialBalance(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        return accountsRepository.findByIsActiveTrue()
                .flatMap(account -> getAccountBalanceAsOf(account.getAccountCode(), effectiveDate)
                        .map(balance -> {
                            BalanceDirection normalBalance = account.getNormalBalance();
                            BigDecimal debitBalance = BigDecimal.ZERO;
                            BigDecimal creditBalance = BigDecimal.ZERO;

                            if (normalBalance == BalanceDirection.DEBIT) {
                                if (balance.compareTo(BigDecimal.ZERO) >= 0) {
                                    debitBalance = balance;
                                } else {
                                    creditBalance = balance.abs();
                                }
                            } else {
                                if (balance.compareTo(BigDecimal.ZERO) >= 0) {
                                    creditBalance = balance;
                                } else {
                                    debitBalance = balance.abs();
                                }
                            }

                            return new AccountBalance(
                                    account.getAccountCode(),
                                    account.getAccountName(),
                                    account.getAccountType().name(),
                                    debitBalance,
                                    creditBalance,
                                    balance
                            );
                        }))
                .filter(ab -> ab.netBalance().compareTo(BigDecimal.ZERO) != 0);
    }

    // ========================================================================
    // RECONCILIATION SUPPORT
    // ========================================================================

    @Override
    public Mono<BigDecimal> getGatewayReceivableBalance() {
        return getAccountBalance(GATEWAY_RECEIVABLE);
    }

    /**
     * Gets the total escrow liability (sum of all event escrow accounts).
     *
     * <p><b>Account Structure:</b></p>
     * <ul>
     *   <li>Parent Account: 2010 (ESCROW_PAYABLE)</li>
     *   <li>Child Accounts: 2010-{eventId} per event</li>
     *   <li>Normal Balance: CREDIT (liability - money owed to organizers)</li>
     * </ul>
     *
     * <p><b>IN/OUT Flow:</b></p>
     * <ul>
     *   <li><b>IN (Credit)</b>: Ticket sales add to escrow</li>
     *   <li><b>OUT (Debit)</b>: Payouts/refunds/chargebacks reduce escrow</li>
     * </ul>
     *
     * @return Sum of all 2010-XXX account balances
     */
    @Override
    public Mono<BigDecimal> getTotalEscrowLiability() {
        return accountsRepository.findByParentAccountCode(ESCROW_PARENT)
                .flatMap(account -> getAccountBalance(account.getAccountCode()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<BigDecimal> getDeferredCommissionBalance() {
        return getAccountBalance(DEFERRED_COMMISSION);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Gets the escrow account code for an event.
     *
     * <p><b>Account Structure:</b></p>
     * <ul>
     *   <li>Format: 2010-{eventId} (first 8 chars of event ID)</li>
     *   <li>Parent Account: 2010 (ESCROW_PAYABLE per AccountSubType)</li>
     *   <li>Normal Balance: CREDIT (liability - money we owe to organizers)</li>
     * </ul>
     *
     * <p><b>Debit/Credit Flow:</b></p>
     * <ul>
     *   <li><b>IN (Credit)</b>: Ticket sales increase escrow (money received for organizer)</li>
     *   <li><b>OUT (Debit)</b>: Payouts/refunds/chargebacks decrease escrow</li>
     * </ul>
     *
     * @param eventId The event ID
     * @return The escrow account code (e.g., "2010-abc12345")
     */
    private String getEscrowAccountCode(String eventId) {
        String shortEventId = eventId.length() > 8 ? eventId.substring(0, 8) : eventId;
        return "2010-" + shortEventId;
    }

    // ========================================================================
    // ATOMIC DEBIT METHODS
    // ========================================================================
    // These methods create individual JournalLine objects for debiting accounts.
    // Use inside record...() methods for clear, explicit accounting.
    //
    // DEBIT = Increase for Assets (1xxx) and Expenses (5xxx) → Money IN
    // DEBIT = Decrease for Liabilities (2xxx), Equity (3xxx), Revenue (4xxx) → Money OUT
    // ========================================================================

    /**
     * DEBIT Gateway Receivable (1021) - ASSET.
     * <p>Use when: Gateway owes us money (ticket sale)</p>
     * <p>Effect: Increases our receivable (money IN to asset)</p>
     */
    private JournalLine debitGatewayReceivable(BigDecimal amount, String description) {
        return JournalLine.debit(GATEWAY_RECEIVABLE, "Gateway Settlement Receivable", amount, description);
    }

    /**
     * DEBIT Operating Bank (1011) - ASSET.
     * <p>Use when: Money arrives in our bank (settlement)</p>
     * <p>Effect: Increases bank balance (money IN to asset)</p>
     */
    private JournalLine debitOperatingBank(BigDecimal amount, String description) {
        return JournalLine.debit(OPERATING_BANK, "Operating Bank Account", amount, description);
    }

    /**
     * DEBIT Chargeback Recovery Receivable (1023) - ASSET.
     * <p>Use when: Gateway takes money back and we need to recover</p>
     * <p>Effect: Increases receivable (we need to recover this money)</p>
     */
    private JournalLine debitChargebackReceivable(BigDecimal amount, String description) {
        return JournalLine.debit(CHARGEBACK_RECEIVABLE, "Chargeback Recovery Receivable", amount, description);
    }

    /**
     * DEBIT Event Escrow (2010-XXX) - LIABILITY.
     * <p>Use when: Taking money from organizer's escrow (payout, refund, chargeback)</p>
     * <p>Effect: Decreases liability (money OUT, we owe organizer less)</p>
     */
    private JournalLine debitEventEscrow(String eventId, BigDecimal amount, String description) {
        return JournalLine.debit(getEscrowAccountCode(eventId), "Event Escrow", amount, description);
    }

    /**
     * DEBIT Deferred Commission (2031) - LIABILITY.
     * <p>Use when: Commission is earned or clawed back</p>
     * <p>Effect: Decreases deferred (pending) commission</p>
     */
    private JournalLine debitDeferredCommission(BigDecimal amount, String description) {
        return JournalLine.debit(DEFERRED_COMMISSION, "Deferred Commission Revenue", amount, description);
    }

    /**
     * DEBIT Customer Refunds Payable (2022) - LIABILITY.
     * <p>Use when: Refund is disbursed to customer</p>
     * <p>Effect: Decreases liability (we no longer owe customer)</p>
     */
    private JournalLine debitRefundsPayable(BigDecimal amount, String description) {
        return JournalLine.debit(CUSTOMER_REFUNDS_PAYABLE, "Customer Refunds Payable", amount, description);
    }

    /**
     * DEBIT Organizer Payouts Payable (2021) - LIABILITY.
     * <p>Use when: Payout is disbursed to organizer</p>
     * <p>Effect: Decreases liability (we no longer owe organizer)</p>
     */
    private JournalLine debitPayoutsPayable(BigDecimal amount, String description) {
        return JournalLine.debit(ORGANIZER_PAYOUTS_PAYABLE, "Organizer Payouts Payable", amount, description);
    }

    /**
     * DEBIT Platform Reserve (3020) - EQUITY.
     * <p>Use when: Using reserve for chargeback recovery</p>
     * <p>Effect: Decreases equity (safety buffer used)</p>
     */
    private JournalLine debitPlatformReserve(BigDecimal amount, String description) {
        return JournalLine.debit(PLATFORM_RESERVE, "Platform Reserve", amount, description);
    }

    /**
     * DEBIT Commission Revenue (4010) - REVENUE.
     * <p>Use when: Clawing back earned commission (chargeback after event)</p>
     * <p>Effect: Decreases revenue (reversal)</p>
     */
    private JournalLine debitCommissionRevenue(BigDecimal amount, String description) {
        return JournalLine.debit(COMMISSION_REVENUE, "Commission Revenue", amount, description);
    }

    /**
     * DEBIT Gateway Fees Expense (5010) - EXPENSE.
     * <p>Use when: Recognizing gateway processing fees</p>
     * <p>Effect: Increases expense (cost to platform)</p>
     */
    private JournalLine debitGatewayFeesExpense(BigDecimal amount, String description) {
        return JournalLine.debit(GATEWAY_FEES_EXPENSE, "Payment Gateway Fees", amount, description);
    }

    /**
     * DEBIT Chargeback Fees Expense (5030) - EXPENSE.
     * <p>Use when: Gateway charges penalty for chargeback</p>
     * <p>Effect: Increases expense (penalty cost to platform)</p>
     */
    private JournalLine debitChargebackFeesExpense(BigDecimal amount, String description) {
        return JournalLine.debit(CHARGEBACK_FEES_EXPENSE, "Chargeback Fees Expense", amount, description);
    }

    /**
     * DEBIT Bad Debt Expense (5040) - EXPENSE.
     * <p>Use when: Writing off unrecoverable chargeback</p>
     * <p>Effect: Increases expense (loss recognized)</p>
     */
    private JournalLine debitBadDebtExpense(BigDecimal amount, String description) {
        return JournalLine.debit(BAD_DEBT_EXPENSE, "Bad Debt Expense", amount, description);
    }

    // ========================================================================
    // ATOMIC CREDIT METHODS
    // ========================================================================
    // These methods create individual JournalLine objects for crediting accounts.
    // Use inside record...() methods for clear, explicit accounting.
    //
    // CREDIT = Decrease for Assets (1xxx) and Expenses (5xxx) → Money OUT
    // CREDIT = Increase for Liabilities (2xxx), Equity (3xxx), Revenue (4xxx) → Money IN
    // ========================================================================

    /**
     * CREDIT Gateway Receivable (1021) - ASSET.
     * <p>Use when: Gateway settles (pays us what they owed)</p>
     * <p>Effect: Decreases receivable (money OUT, no longer owed)</p>
     */
    private JournalLine creditGatewayReceivable(BigDecimal amount, String description) {
        return JournalLine.credit(GATEWAY_RECEIVABLE, "Gateway Settlement Receivable", amount, description);
    }

    /**
     * CREDIT Operating Bank (1011) - ASSET.
     * <p>Use when: Money leaves our bank (payout, refund, chargeback)</p>
     * <p>Effect: Decreases bank balance (money OUT of asset)</p>
     */
    private JournalLine creditOperatingBank(BigDecimal amount, String description) {
        return JournalLine.credit(OPERATING_BANK, "Operating Bank Account", amount, description);
    }

    /**
     * CREDIT Chargeback Recovery Receivable (1023) - ASSET.
     * <p>Use when: We recover chargeback funds from a source</p>
     * <p>Effect: Decreases receivable (cleared as we recover)</p>
     */
    private JournalLine creditChargebackReceivable(BigDecimal amount, String description) {
        return JournalLine.credit(CHARGEBACK_RECEIVABLE, "Chargeback Recovery Receivable", amount, description);
    }

    /**
     * CREDIT Event Escrow (2010-XXX) - LIABILITY.
     * <p>Use when: Adding money to organizer's escrow (ticket sale)</p>
     * <p>Effect: Increases liability (we owe organizer more)</p>
     */
    private JournalLine creditEventEscrow(String eventId, BigDecimal amount, String description) {
        return JournalLine.credit(getEscrowAccountCode(eventId), "Event Escrow", amount, description);
    }

    /**
     * CREDIT Deferred Commission (2031) - LIABILITY.
     * <p>Use when: Recording commission at ticket sale (before earned)</p>
     * <p>Effect: Increases deferred commission</p>
     */
    private JournalLine creditDeferredCommission(BigDecimal amount, String description) {
        return JournalLine.credit(DEFERRED_COMMISSION, "Deferred Commission Revenue", amount, description);
    }

    /**
     * CREDIT Customer Refunds Payable (2022) - LIABILITY.
     * <p>Use when: Approving a refund (creating obligation to pay customer)</p>
     * <p>Effect: Increases liability (we owe customer)</p>
     */
    private JournalLine creditRefundsPayable(BigDecimal amount, String description) {
        return JournalLine.credit(CUSTOMER_REFUNDS_PAYABLE, "Customer Refunds Payable", amount, description);
    }

    /**
     * CREDIT Organizer Payouts Payable (2021) - LIABILITY.
     * <p>Use when: Approving a payout (creating obligation to pay organizer)</p>
     * <p>Effect: Increases liability (we owe organizer)</p>
     */
    private JournalLine creditPayoutsPayable(BigDecimal amount, String description) {
        return JournalLine.credit(ORGANIZER_PAYOUTS_PAYABLE, "Organizer Payouts Payable", amount, description);
    }

    /**
     * CREDIT Gateway Fees Payable (2024) - LIABILITY.
     * <p>Use when: Recording gateway fees at ticket sale</p>
     * <p>Effect: Increases liability (we owe gateway)</p>
     */
    private JournalLine creditGatewayFeesPayable(BigDecimal amount, String description) {
        return JournalLine.credit(GATEWAY_FEES_PAYABLE, "Gateway Fees Payable", amount, description);
    }

    /**
     * CREDIT Commission Revenue (4010) - REVENUE.
     * <p>Use when: Commission becomes earned (after event + hold period)</p>
     * <p>Effect: Increases revenue (money IN)</p>
     */
    private JournalLine creditCommissionRevenue(BigDecimal amount, String description) {
        return JournalLine.credit(COMMISSION_REVENUE, "Commission Revenue", amount, description);
    }

    /**
     * CREDIT Fee Revenue (4020) - REVENUE.
     * <p>Use when: Charging payout processing fee to organizer</p>
     * <p>Effect: Increases revenue (fee earned)</p>
     */
    private JournalLine creditFeeRevenue(BigDecimal amount, String description) {
        return JournalLine.credit(FEE_REVENUE, "Payout Processing Fee Revenue", amount, description);
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Checks if an amount has a positive value (not null and greater than zero).
     */
    private boolean hasValue(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
