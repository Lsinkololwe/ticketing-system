# Accounting Service Method Naming Convention Analysis

**Date**: April 20, 2026
**Purpose**: Refactor method names to clearly indicate which accounts are debited and credited

---

## FINAL DESIGN: Layered Architecture

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PUBLIC API LAYER                                     │
│                   (Business-focused method names)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  recordTicketSale()                                                          │
│  recordRefund()                                                              │
│  recordRefundDisbursement()                                                  │
│  recordPayout()                                                              │
│  recordPayoutDisbursement()                                                  │
│  recordCommissionEarned()                                                    │
│  recordCommissionClawback()                                                  │
│  recordChargebackReceived()                                                  │
│  recordChargeback()                                                          │
│  recordGatewaySettlement()                                                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ calls
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ATOMIC DEBIT/CREDIT LAYER                               │
│              (Explicit account operations - internal use)                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  DEBIT METHODS (Increase assets/expenses, Decrease liabilities)             │
│  ─────────────────────────────────────────────────────────────              │
│  debitGatewayReceivable(amount, description)       → Asset IN               │
│  debitOperatingBank(amount, description)           → Asset IN               │
│  debitChargebackReceivable(amount, description)    → Asset IN               │
│  debitEventEscrow(eventId, amount, description)    → Liability OUT          │
│  debitDeferredCommission(amount, description)      → Liability OUT          │
│  debitRefundsPayable(amount, description)          → Liability OUT          │
│  debitPayoutsPayable(amount, description)          → Liability OUT          │
│  debitPlatformReserve(amount, description)         → Equity OUT             │
│  debitCommissionRevenue(amount, description)       → Revenue OUT            │
│  debitGatewayFeesExpense(amount, description)      → Expense IN             │
│  debitChargebackFeesExpense(amount, description)   → Expense IN             │
│  debitBadDebtExpense(amount, description)          → Expense IN             │
│                                                                              │
│  CREDIT METHODS (Decrease assets/expenses, Increase liabilities)            │
│  ──────────────────────────────────────────────────────────────             │
│  creditGatewayReceivable(amount, description)      → Asset OUT              │
│  creditOperatingBank(amount, description)          → Asset OUT              │
│  creditChargebackReceivable(amount, description)   → Asset OUT              │
│  creditEventEscrow(eventId, amount, description)   → Liability IN           │
│  creditDeferredCommission(amount, description)     → Liability IN           │
│  creditRefundsPayable(amount, description)         → Liability IN           │
│  creditPayoutsPayable(amount, description)         → Liability IN           │
│  creditGatewayFeesPayable(amount, description)     → Liability IN           │
│  creditCommissionRevenue(amount, description)      → Revenue IN             │
│  creditFeeRevenue(amount, description)             → Revenue IN             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ creates
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        JOURNAL LINE LAYER                                    │
│                    (JournalLine model objects)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  JournalLine.debit(accountCode, accountName, amount, description)            │
│  JournalLine.credit(accountCode, accountName, amount, description)           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Benefits of This Design

1. **Public API remains intuitive**: Callers use business-focused names
2. **Internal clarity**: Implementation shows exactly which accounts are affected
3. **Single Responsibility**: Each debit/credit method handles ONE account
4. **Reusability**: Atomic methods can be reused across different business operations
5. **Testability**: Easy to unit test individual debit/credit operations
6. **Audit Trail**: Clear code flow showing what's debited and credited

### Example: recordTicketSale() Implementation

```java
@Transactional
public Mono<JournalEntry> recordTicketSale(
        String paymentIntentId, String ticketId, String eventId,
        BigDecimal grossAmount, BigDecimal netAmount,
        BigDecimal commissionAmount, BigDecimal gatewayFeeAmount, String currency) {

    List<JournalLine> lines = new ArrayList<>();
    String description = "Ticket sale: " + ticketId;

    // DEBITS - Money coming IN to assets
    lines.add(debitGatewayReceivable(grossAmount, description));

    // CREDITS - Creating liabilities
    lines.add(creditEventEscrow(eventId, netAmount, description));
    lines.add(creditDeferredCommission(commissionAmount, description));
    if (hasValue(gatewayFeeAmount)) {
        lines.add(creditGatewayFeesPayable(gatewayFeeAmount, description));
    }

    return journalService.createAndPostEntry(...);
}

// ATOMIC DEBIT/CREDIT METHODS
private JournalLine debitGatewayReceivable(BigDecimal amount, String description) {
    return JournalLine.debit(GATEWAY_RECEIVABLE, "Gateway Settlement Receivable", amount, description);
}

private JournalLine creditEventEscrow(String eventId, BigDecimal amount, String description) {
    return JournalLine.credit(getEscrowAccountCode(eventId), "Event Escrow", amount, description);
}

private JournalLine creditDeferredCommission(BigDecimal amount, String description) {
    return JournalLine.credit(DEFERRED_COMMISSION, "Deferred Commission Revenue", amount, description);
}

private JournalLine creditGatewayFeesPayable(BigDecimal amount, String description) {
    return JournalLine.credit(GATEWAY_FEES_PAYABLE, "Gateway Fees Payable", amount, description);
}
```

---

## PREVIOUS ANALYSIS (for reference)

## Design Philosophy

### Naming Convention Pattern

For double-entry accounting methods, names should clearly indicate:
1. **What is being DEBITED** (money IN for assets/expenses, money OUT for liabilities/equity/revenue)
2. **What is being CREDITED** (money OUT for assets/expenses, money IN for liabilities/equity/revenue)

**Pattern**: `debit{SourceAccount}Credit{DestinationAccount}` or `{BusinessOperation}_debit{Account}Credit{Account}`

### Quick Reference: DEBIT vs CREDIT

| Account Type | Normal Balance | DEBIT means... | CREDIT means... |
|--------------|----------------|----------------|-----------------|
| **Assets** (1xxx) | DEBIT | Increase (IN) | Decrease (OUT) |
| **Expenses** (5xxx) | DEBIT | Increase (IN) | Decrease (OUT) |
| **Liabilities** (2xxx) | CREDIT | Decrease (OUT) | Increase (IN) |
| **Equity** (3xxx) | CREDIT | Decrease (OUT) | Increase (IN) |
| **Revenue** (4xxx) | CREDIT | Decrease (OUT) | Increase (IN) |

---

## Current vs Proposed Method Names

### 1. Ticket Sale Recording

**Current**: `recordTicketSale()`

**Journal Entry**:
```
DR Gateway Receivable (1021)       K100.00   [IN - gateway owes us]
   CR Event Escrow (2010-XXX)             K88.00   [IN - we owe organizer]
   CR Deferred Commission (2031)          K10.00   [IN - pending commission]
   CR Gateway Fees Payable (2024)         K 2.00   [IN - we owe gateway]
```

**Proposed**: `debitGatewayReceivable_creditEscrowCommissionFees()`

**Rationale**: The primary debit is Gateway Receivable; credits are distributed to multiple liability accounts. The name shows the main asset being recorded.

---

### 2. Refund Recording (Liability Creation)

**Current**: `recordRefund()`

**Journal Entry**:
```
DR Event Escrow (2010-XXX)         K88.00   [OUT - organizer's money taken]
DR Deferred Commission (2031)      K10.00   [OUT - commission returned]
   CR Customer Refunds Payable (2022)     K98.00   [IN - we owe customer]
```

**Proposed**: `debitEscrowCommission_creditRefundsPayable()`

**Rationale**: We're taking from escrow/commission and creating a new liability to the customer.

---

### 3. Refund Disbursement (Cash Out)

**Current**: `recordRefundDisbursement()`

**Journal Entry**:
```
DR Customer Refunds Payable (2022) K100.00  [OUT - liability cleared]
   CR Operating Bank (1011)               K100.00  [OUT - money leaves bank]
```

**Proposed**: `debitRefundsPayable_creditBank()`

**Rationale**: Clear and direct - we're clearing the liability by paying from bank.

---

### 4. Payout Recording (Liability Creation)

**Current**: `recordPayout()`

**Journal Entry**:
```
DR Event Escrow (2010-XXX)         K88.00   [OUT - escrow reduced]
   CR Organizer Payouts Payable (2021)    K85.00   [IN - we owe organizer]
   CR Fee Revenue (4020)                  K 3.00   [IN - fee earned]
```

**Proposed**: `debitEscrow_creditPayoutsPayableAndFeeRevenue()`

**Rationale**: We're moving money from escrow to create a payout liability and recognizing fee revenue.

---

### 5. Payout Disbursement (Cash Out)

**Current**: `recordPayoutDisbursement()`

**Journal Entry**:
```
DR Organizer Payouts Payable (2021) K85.00  [OUT - liability cleared]
   CR Operating Bank (1011)                K85.00  [OUT - money leaves bank]
```

**Proposed**: `debitPayoutsPayable_creditBank()`

**Rationale**: Clear and direct - we're clearing the liability by paying from bank.

---

### 6. Commission Earned (Revenue Recognition)

**Current**: `recordCommissionEarned()`

**Journal Entry**:
```
DR Deferred Commission (2031)      K10.00   [OUT - deferred reduced]
   CR Commission Revenue (4010)           K10.00   [IN - revenue recognized]
```

**Proposed**: `debitDeferredCommission_creditCommissionRevenue()`

**Rationale**: Clear transition from deferred (liability) to earned (revenue).

---

### 7. Commission Clawback (Reversal)

**Current**: `recordCommissionClawback()`

**Journal Entry (from deferred)**:
```
DR Deferred Commission (2031)      K10.00   [OUT - deferred reduced]
   CR Chargeback Receivable (1023)        K10.00   [OUT - receivable cleared]
```

**Journal Entry (from earned)**:
```
DR Commission Revenue (4010)       K10.00   [OUT - revenue reversed]
   CR Chargeback Receivable (1023)        K10.00   [OUT - receivable cleared]
```

**Proposed**: `debitCommission_creditChargebackReceivable()` (with `wasEarned` param)

**Rationale**: The source varies based on `wasEarned` flag, but the pattern is the same.

---

### 8. Chargeback Received (Gateway Takes Money)

**Current**: `recordChargebackReceived()`

**Journal Entry**:
```
DR Chargeback Recovery Receivable (1023) K500.00  [IN - we need to recover]
DR Chargeback Fees Expense (5030)         K25.00  [IN - direct expense]
   CR Operating Bank (1011)                      K525.00  [OUT - money taken by gateway]
```

**Proposed**: `debitChargebackReceivableAndFees_creditBank()`

**Rationale**: Shows that we're recording a receivable (what we need to recover) and an expense (fee cost), while bank decreases.

---

### 9. Chargeback Recovery (From Various Sources)

**Current**: `recordChargeback()`

**Journal Entry (from Escrow)**:
```
DR Event Escrow (2010-XXX)         K500.00  [OUT - organizer's money taken]
   CR Chargeback Receivable (1023)        K500.00  [OUT - receivable cleared]
```

**Journal Entry (from Reserve)**:
```
DR Platform Reserve (3020)         K500.00  [OUT - reserve used]
   CR Chargeback Receivable (1023)        K500.00  [OUT - receivable cleared]
```

**Journal Entry (Write-off)**:
```
DR Bad Debt Expense (5040)         K500.00  [IN - loss recognized]
   CR Chargeback Receivable (1023)        K500.00  [OUT - receivable written off]
```

**Proposed**: `debitRecoverySource_creditChargebackReceivable()` (with `fundSource` param)

**Rationale**: The debit source varies based on the recovery waterfall stage, but always credits the chargeback receivable.

---

### 10. Gateway Settlement (Funds Received)

**Current**: `recordGatewaySettlement()`

**Journal Entry**:
```
DR Operating Bank (1011)           K9,800   [IN - money received]
DR Gateway Fees Expense (5010)       K200   [IN - fee cost]
   CR Gateway Receivable (1021)          K10,000  [OUT - receivable cleared]
```

**Proposed**: `debitBankAndGatewayFees_creditGatewayReceivable()`

**Rationale**: Shows bank increase and fee expense, while clearing the gateway receivable.

---

## Summary: Proposed Method Renaming

| # | Current Name | Proposed Name |
|---|--------------|---------------|
| 1 | `recordTicketSale` | `debitGatewayReceivable_creditEscrowCommissionFees` |
| 2 | `recordRefund` | `debitEscrowCommission_creditRefundsPayable` |
| 3 | `recordRefundDisbursement` | `debitRefundsPayable_creditBank` |
| 4 | `recordPayout` | `debitEscrow_creditPayoutsPayableFeeRevenue` |
| 5 | `recordPayoutDisbursement` | `debitPayoutsPayable_creditBank` |
| 6 | `recordCommissionEarned` | `debitDeferredCommission_creditCommissionRevenue` |
| 7 | `recordCommissionClawback` | `debitCommission_creditChargebackReceivable` |
| 8 | `recordChargebackReceived` | `debitChargebackReceivableFees_creditBank` |
| 9 | `recordChargeback` | `debitRecoverySource_creditChargebackReceivable` |
| 10 | `recordGatewaySettlement` | `debitBankGatewayFees_creditGatewayReceivable` |

---

## Alternative Naming Convention (Hybrid)

If the full debit/credit names are too verbose, consider a hybrid approach that keeps the business context but adds the primary debit/credit pair:

| # | Current Name | Alternative Hybrid Name |
|---|--------------|------------------------|
| 1 | `recordTicketSale` | `ticketSale_debitGatewayReceivable` |
| 2 | `recordRefund` | `refund_debitEscrowCreditRefundsPayable` |
| 3 | `recordRefundDisbursement` | `refundDisbursement_debitRefundsPayableCreditBank` |
| 4 | `recordPayout` | `payout_debitEscrowCreditPayoutsPayable` |
| 5 | `recordPayoutDisbursement` | `payoutDisbursement_debitPayoutsPayableCreditBank` |
| 6 | `recordCommissionEarned` | `commissionEarned_debitDeferredCreditRevenue` |
| 7 | `recordCommissionClawback` | `commissionClawback_debitCommissionCreditReceivable` |
| 8 | `recordChargebackReceived` | `chargebackReceived_debitReceivableCreditBank` |
| 9 | `recordChargeback` | `chargebackRecovery_debitSourceCreditReceivable` |
| 10 | `recordGatewaySettlement` | `gatewaySettlement_debitBankCreditReceivable` |

---

## Implementation Impact

### Files to Modify

1. **Interface**: `AccountingService.java` - Rename method signatures
2. **Implementation**: `AccountingServiceImpl.java` - Rename implementations
3. **Callers**: All services that call these methods:
   - `PaymentServiceImpl.java` - Ticket sales
   - `RefundServiceImpl.java` - Refunds
   - `PayoutServiceImpl.java` - Payouts
   - `ChargebackServiceImpl.java` - Chargebacks
   - `ReconciliationMutationResolver.java` - Gateway settlement

### Migration Strategy

1. **Phase 1**: Add new methods with new names, deprecate old ones
2. **Phase 2**: Update all callers to use new method names
3. **Phase 3**: Remove deprecated methods

---

## Visual Account Flow Reference

```
TICKET SALE FLOW:
═══════════════════════════════════════════════════════════════════════════════
Customer pays K100 for ticket (10% commission, K2 gateway fee)

 Gateway Receivable (1021)    Event Escrow (2010-XXX)    Deferred Commission (2031)
 ┌──────────────────────┐     ┌──────────────────────┐   ┌──────────────────────┐
 │ IN(DR)  │ OUT(CR)    │     │ IN(CR)   │ OUT(DR)   │   │ IN(CR)   │ OUT(DR)   │
 │ K100 ✓  │            │     │ K88 ✓    │           │   │ K10 ✓    │           │
 │ Gateway │            │     │ We owe   │           │   │ Pending  │           │
 │ owes us │            │     │ organizer│           │   │ commis.  │           │
 └──────────────────────┘     └──────────────────────┘   └──────────────────────┘

 Gateway Fees Payable (2024)
 ┌──────────────────────┐
 │ IN(CR)   │ OUT(DR)   │
 │ K2 ✓     │           │
 │ We owe   │           │
 │ gateway  │           │
 └──────────────────────┘

Method: debitGatewayReceivable_creditEscrowCommissionFees()


REFUND FLOW (Two-Step):
═══════════════════════════════════════════════════════════════════════════════
Step 1: Create refund liability (when approved)

 Event Escrow (2010-XXX)      Deferred Commission        Refunds Payable (2022)
 ┌──────────────────────┐     ┌──────────────────────┐   ┌──────────────────────┐
 │ IN(CR)   │ OUT(DR)   │     │ IN(CR)   │ OUT(DR)   │   │ IN(CR)   │ OUT(DR)   │
 │          │ K88 ✓     │     │          │ K10 ✓     │   │ K98 ✓    │           │
 │          │ Taken     │     │          │ Returned  │   │ We owe   │           │
 │          │ back      │     │          │           │   │ customer │           │
 └──────────────────────┘     └──────────────────────┘   └──────────────────────┘

Method: debitEscrowCommission_creditRefundsPayable()

───────────────────────────────────────────────────────────────────────────────
Step 2: Disburse to customer (when gateway sends money)

 Refunds Payable (2022)       Operating Bank (1011)
 ┌──────────────────────┐     ┌──────────────────────┐
 │ IN(CR)   │ OUT(DR)   │     │ IN(DR)   │ OUT(CR)   │
 │          │ K98 ✓     │     │          │ K98 ✓     │
 │          │ Cleared   │     │          │ Money     │
 │          │           │     │          │ sent      │
 └──────────────────────┘     └──────────────────────┘

Method: debitRefundsPayable_creditBank()


PAYOUT FLOW (Two-Step):
═══════════════════════════════════════════════════════════════════════════════
Step 1: Create payout liability (when approved)

 Event Escrow (2010-XXX)      Payouts Payable (2021)     Fee Revenue (4020)
 ┌──────────────────────┐     ┌──────────────────────┐   ┌──────────────────────┐
 │ IN(CR)   │ OUT(DR)   │     │ IN(CR)   │ OUT(DR)   │   │ IN(CR)   │ OUT(DR)   │
 │          │ K88 ✓     │     │ K85 ✓    │           │   │ K3 ✓     │           │
 │          │ Released  │     │ We owe   │           │   │ Fee      │           │
 │          │           │     │ organizer│           │   │ earned   │           │
 └──────────────────────┘     └──────────────────────┘   └──────────────────────┘

Method: debitEscrow_creditPayoutsPayableFeeRevenue()

───────────────────────────────────────────────────────────────────────────────
Step 2: Disburse to organizer (when bank sends money)

 Payouts Payable (2021)       Operating Bank (1011)
 ┌──────────────────────┐     ┌──────────────────────┐
 │ IN(CR)   │ OUT(DR)   │     │ IN(DR)   │ OUT(CR)   │
 │          │ K85 ✓     │     │          │ K85 ✓     │
 │          │ Cleared   │     │          │ Money     │
 │          │           │     │          │ sent      │
 └──────────────────────┘     └──────────────────────┘

Method: debitPayoutsPayable_creditBank()


CHARGEBACK FLOW (Two-Step):
═══════════════════════════════════════════════════════════════════════════════
Step 1: Chargeback received (gateway takes money from our bank)

 Chargeback Receivable (1023)  Chargeback Fees (5030)    Operating Bank (1011)
 ┌──────────────────────┐      ┌──────────────────────┐  ┌──────────────────────┐
 │ IN(DR)   │ OUT(CR)   │      │ IN(DR)   │ OUT(CR)   │  │ IN(DR)   │ OUT(CR)   │
 │ K500 ✓   │           │      │ K25 ✓    │           │  │          │ K525 ✓   │
 │ We need  │           │      │ Penalty  │           │  │          │ Taken by │
 │ to recover           │      │ cost     │           │  │          │ gateway  │
 └──────────────────────┘      └──────────────────────┘  └──────────────────────┘

Method: debitChargebackReceivableFees_creditBank()

───────────────────────────────────────────────────────────────────────────────
Step 2: Recovery (from escrow, reserve, or write-off)

Example: Recovery from Escrow

 Event Escrow (2010-XXX)      Chargeback Receivable (1023)
 ┌──────────────────────┐     ┌──────────────────────┐
 │ IN(CR)   │ OUT(DR)   │     │ IN(DR)   │ OUT(CR)   │
 │          │ K500 ✓    │     │          │ K500 ✓    │
 │          │ Taken for │     │          │ Cleared   │
 │          │ chargeback│     │          │           │
 └──────────────────────┘     └──────────────────────┘

Method: debitRecoverySource_creditChargebackReceivable(source=ORGANIZER_ESCROW)


GATEWAY SETTLEMENT FLOW:
═══════════════════════════════════════════════════════════════════════════════
Gateway settles K10,000 with K200 fees (we receive K9,800)

 Operating Bank (1011)        Gateway Fees Expense       Gateway Receivable (1021)
 ┌──────────────────────┐     ┌──────────────────────┐   ┌──────────────────────┐
 │ IN(DR)   │ OUT(CR)   │     │ IN(DR)   │ OUT(CR)   │   │ IN(DR)   │ OUT(CR)   │
 │ K9,800 ✓ │           │     │ K200 ✓   │           │   │          │ K10,000 ✓│
 │ Received │           │     │ Fee      │           │   │          │ Settled  │
 │          │           │     │ cost     │           │   │          │          │
 └──────────────────────┘     └──────────────────────┘   └──────────────────────┘

Method: debitBankGatewayFees_creditGatewayReceivable()
```

---

## IMPLEMENTATION COMPLETE (April 20, 2026)

The layered architecture has been fully implemented in `AccountingServiceImpl.java`.

### Atomic Debit Methods (13 total)

| Method | Account | Type | Effect |
|--------|---------|------|--------|
| `debitGatewayReceivable()` | 1021 | ASSET | Increase (IN) |
| `debitOperatingBank()` | 1011 | ASSET | Increase (IN) |
| `debitChargebackReceivable()` | 1023 | ASSET | Increase (IN) |
| `debitEventEscrow()` | 2010-XXX | LIABILITY | Decrease (OUT) |
| `debitDeferredCommission()` | 2031 | LIABILITY | Decrease (OUT) |
| `debitRefundsPayable()` | 2022 | LIABILITY | Decrease (OUT) |
| `debitPayoutsPayable()` | 2021 | LIABILITY | Decrease (OUT) |
| `debitPlatformReserve()` | 3020 | EQUITY | Decrease (OUT) |
| `debitCommissionRevenue()` | 4010 | REVENUE | Decrease (OUT) |
| `debitGatewayFeesExpense()` | 5010 | EXPENSE | Increase (IN) |
| `debitChargebackFeesExpense()` | 5030 | EXPENSE | Increase (IN) |
| `debitBadDebtExpense()` | 5040 | EXPENSE | Increase (IN) |

### Atomic Credit Methods (10 total)

| Method | Account | Type | Effect |
|--------|---------|------|--------|
| `creditGatewayReceivable()` | 1021 | ASSET | Decrease (OUT) |
| `creditOperatingBank()` | 1011 | ASSET | Decrease (OUT) |
| `creditChargebackReceivable()` | 1023 | ASSET | Decrease (OUT) |
| `creditEventEscrow()` | 2010-XXX | LIABILITY | Increase (IN) |
| `creditDeferredCommission()` | 2031 | LIABILITY | Increase (IN) |
| `creditRefundsPayable()` | 2022 | LIABILITY | Increase (IN) |
| `creditPayoutsPayable()` | 2021 | LIABILITY | Increase (IN) |
| `creditGatewayFeesPayable()` | 2024 | LIABILITY | Increase (IN) |
| `creditCommissionRevenue()` | 4010 | REVENUE | Increase (IN) |
| `creditFeeRevenue()` | 4020 | REVENUE | Increase (IN) |

### Build Verification

```
[INFO] BUILD SUCCESS
[INFO] Compiling 347 source files
[INFO] Total time:  4.600 s
```

---

## Recommendation

Use the **Hybrid Naming Convention** that keeps business context while clearly showing the primary debit/credit flow. This maintains code readability while providing clarity on what accounts are affected.

The hybrid names are:
- Short enough to read easily
- Include business context (ticketSale, refund, payout, etc.)
- Clearly show the main debit and credit accounts

This approach balances maintainability with the need for clear accounting semantics.
