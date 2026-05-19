# Accounting Touchpoints Analysis

## Complete List of Financial Operations

This document catalogs **every touchpoint** in the booking-service that results in accounting journal entries (debits/credits), categorized by whether they are **USER-initiated** or **SYSTEM-initiated**.

---

## Summary Table

| # | Touchpoint | Trigger Type | Initiator | Method Called | Business Event |
|---|------------|--------------|-----------|---------------|----------------|
| 1 | Ticket Sale | SYSTEM | Payment Webhook | `recordTicketSale()` | Customer purchases ticket |
| 2 | Gateway Settlement | USER | Admin GraphQL | `recordGatewaySettlement()` | Gateway settles to bank |
| 3 | Chargeback Received | SYSTEM | Gateway Webhook | `recordChargebackReceived()` | Gateway notifies chargeback |
| 4 | Chargeback Recovery - Escrow | SYSTEM | Recovery Waterfall | `recordChargeback(ORGANIZER_ESCROW)` | Recover from event escrow |
| 5 | Chargeback Recovery - Future | SYSTEM | Recovery Waterfall | `recordChargeback(ORGANIZER_FUTURE)` | Recover from pending payouts |
| 6 | Chargeback Recovery - Reserve | SYSTEM | Recovery Waterfall | `recordChargeback(PLATFORM_RESERVE)` | Recover from platform reserve |
| 7 | Chargeback Write-Off | SYSTEM | Recovery Waterfall | `recordChargeback(WRITE_OFF)` | Bad debt write-off |
| 8 | Payout Recording | USER | Organizer Request | `recordPayout()` | Organizer requests payout |
| 9 | Payout Disbursement | USER/SYSTEM | Admin/Auto Approval | `recordPayoutDisbursement()` | Payout sent to bank |
| 10 | Commission Earned | SYSTEM | Event Completion | `recordCommissionEarned()` | Commission becomes revenue |
| 11 | Commission Clawback | SYSTEM | Refund/Chargeback | `recordCommissionClawback()` | Commission reversed |
| 12 | Refund Recording | USER | Customer Request | `recordRefund()` | Customer requests refund |
| 13 | Refund Disbursement | SYSTEM | Gateway Callback | `recordRefundDisbursement()` | Refund sent to customer |

---

## Detailed Touchpoint Analysis

### 1. TICKET SALE (SYSTEM-INITIATED)

**Trigger**: Payment gateway webhook (PawaPay) confirms successful payment
**File**: `PaymentEventListener.java:80`
**Event**: `PaymentCompletedEvent`

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: TICKET SALE                                                         ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    SYSTEM (Payment webhook from PawaPay)                            ║
║ User Action:  Customer submits payment via mobile money                         ║
║ System Flow:  PawaPay → Webhook → PaymentEventListener → AccountingService      ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Customer Phone ──K500──► PawaPay Gateway ──► Our System Records

Journal Entry (recordTicketSale):
  DR Gateway Receivable (1021)     K500    [IN: Gateway owes us money]
     CR Event Escrow (2010-EVT)           K425    [IN: Organizer's pending funds]
     CR Deferred Commission (2031)        K75     [IN: Our pending revenue]
```

**Code Path**:
```java
PaymentEventListener.onPaymentCompleted(PaymentCompletedEvent event)
  └── accountingService.recordTicketSale(paymentIntentId, ticketId, eventId,
          grossAmount, netAmount, commissionAmount, gatewayFee, currency)
```

---

### 2. GATEWAY SETTLEMENT (USER-INITIATED)

**Trigger**: Admin records settlement via GraphQL mutation
**File**: `ReconciliationMutationResolver.java:248`
**Security**: `@PreAuthorize("hasRole('ADMIN')")`

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: GATEWAY SETTLEMENT                                                  ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    USER (Admin via GraphQL mutation)                                ║
║ User Action:  Admin records PawaPay's settlement to bank account               ║
║ System Flow:  Admin Dashboard → GraphQL → ReconciliationMutationResolver       ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  PawaPay ──K9,800──► Our Bank Account (minus K200 gateway fees)

Journal Entry (recordGatewaySettlement):
  DR Operating Bank (1011)         K9,800  [IN: Net money in bank]
  DR Gateway Fees Expense (5010)     K200  [IN: Fee cost recorded]
     CR Gateway Receivable (1021)        K10,000 [OUT: Receivable cleared]
```

**GraphQL Mutation**:
```graphql
mutation {
  recordGatewaySettlement(input: {
    settlementId: "SETTLE-001"
    grossAmount: 10000.00
    feeAmount: 200.00
    netAmount: 9800.00
    settlementDate: "2024-01-15T10:00:00Z"
    bankReference: "BANK-REF-123"
    currency: "ZMW"
  }) {
    success
    message
  }
}
```

---

### 3. CHARGEBACK RECEIVED (SYSTEM-INITIATED)

**Trigger**: PawaPay webhook notifies chargeback filed
**File**: `ChargebackServiceImpl.java:136`
**Called By**: `receiveChargeback()` method

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: CHARGEBACK RECEIVED                                                 ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    SYSTEM (Gateway webhook)                                         ║
║ User Action:  Customer files chargeback with their bank/mobile money provider  ║
║ System Flow:  Bank → PawaPay → Webhook → ChargebackServiceImpl                 ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Our Bank Account ──K525──► TAKEN by gateway (K500 refund + K25 fee)

Journal Entry (recordChargebackReceived):
  DR Chargeback Receivable (1023)  K500   [IN: We need to recover this]
  DR Chargeback Fees Expense (5030) K25   [IN: Direct expense]
     CR Operating Bank (1011)            K525   [OUT: Money already taken]
```

**Code Path**:
```java
ChargebackServiceImpl.receiveChargeback(...)
  └── accountingService.recordChargebackReceived(chargebackId, eventId,
          ticketId, chargebackAmount, chargebackFee, gatewayReference, currency)
```

---

### 4-7. CHARGEBACK RECOVERY WATERFALL (SYSTEM-INITIATED)

**Trigger**: Automatic recovery process after chargeback acceptance
**File**: `ChargebackServiceImpl.java:410, 540, 558`
**Recovery Order**: ESCROW → FUTURE → RESERVE → WRITE_OFF

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: CHARGEBACK RECOVERY WATERFALL                                       ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    SYSTEM (Automatic waterfall process)                             ║
║ Trigger:      Chargeback status changed to ACCEPTED                            ║
║ System Flow:  ChargebackServiceImpl.startRecovery() → waterfall methods        ║
╚════════════════════════════════════════════════════════════════════════════════╝
```

#### 4. Recovery from ORGANIZER_ESCROW
```
Money Source: Event's escrow balance (organizer's pending funds)

Journal Entry (recordChargeback - ORGANIZER_ESCROW):
  DR Event Escrow (2010-EVT)       K500   [OUT: Reduce organizer balance]
     CR Chargeback Receivable (1023)     K500   [OUT: Receivable cleared]
```

#### 5. Recovery from ORGANIZER_FUTURE
```
Money Source: Organizer's pending payout requests (not yet disbursed)

No journal entry - just reduces payout amounts with audit notes:
  PayoutRequest.netPayoutAmount reduced by K325
  Notes: "[2024-01-15] CHARGEBACK RECOVERY: K325.00 deducted..."
```

#### 6. Recovery from PLATFORM_RESERVE
```
Money Source: Platform's reserve fund

Journal Entry (recordChargeback - PLATFORM_RESERVE):
  DR Platform Reserve (2041)      K200   [OUT: Reserve reduced]
     CR Chargeback Receivable (1023)     K200   [OUT: Receivable cleared]
```

#### 7. WRITE_OFF (Bad Debt)
```
Money Source: None - recorded as loss

Journal Entry (recordChargeback - WRITE_OFF):
  DR Bad Debt Expense (5040)      K100   [IN: Loss recorded]
     CR Chargeback Receivable (1023)     K100   [OUT: Receivable written off]
```

---

### 8. PAYOUT RECORDING (USER-INITIATED)

**Trigger**: Organizer requests payout via dashboard
**File**: `PayoutRequestServiceImpl.java:261`
**Called When**: Admin approves and completes payout request

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: PAYOUT RECORDING                                                    ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    USER (Organizer requests → Admin approves → Admin completes)     ║
║ User Actions: 1) Organizer clicks "Request Payout"                             ║
║               2) Admin reviews and clicks "Approve"                            ║
║               3) Admin clicks "Complete" after bank transfer                   ║
║ System Flow:  Dashboard → GraphQL → PayoutRequestServiceImpl.complete()        ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Event Escrow Balance ──K10,000──► Payout Payable Account

Journal Entry (recordPayout):
  DR Event Escrow (2010-EVT)       K10,000 [OUT: Organizer's escrow reduced]
     CR Payouts Payable (2023)           K9,700  [IN: We owe organizer]
     CR Fee Revenue (4020)                 K300  [IN: Platform payout fee]
```

**Code Path**:
```java
PayoutRequestServiceImpl.complete(id, bankReference, completedBy)
  └── accountingService.recordPayout(requestId, eventId, organizerId,
          netPayoutAmount, totalFee, currency)
```

---

### 9. PAYOUT DISBURSEMENT (USER-INITIATED)

**Trigger**: Immediately follows payout recording
**File**: `PayoutRequestServiceImpl.java:270`
**Called When**: As part of `complete()` flow

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: PAYOUT DISBURSEMENT                                                 ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    USER (Admin completing the payout)                               ║
║ User Action:  Admin has transferred money and records bank reference           ║
║ System Flow:  PayoutRequestServiceImpl.complete() → recordPayoutDisbursement() ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Our Bank Account ──K9,700──► Organizer's Bank Account

Journal Entry (recordPayoutDisbursement):
  DR Payouts Payable (2023)        K9,700 [OUT: Liability cleared]
     CR Operating Bank (1011)            K9,700 [OUT: Money left our bank]
```

---

### 10. COMMISSION EARNED (SYSTEM-INITIATED)

**Trigger**: Event completion / post-event batch process
**File**: `CommissionServiceImpl.java:109`
**Called When**: Event ends and commission recognition runs

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: COMMISSION EARNED                                                   ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    SYSTEM (Scheduled job or event completion trigger)               ║
║ Trigger:      Event ends successfully → Commission becomes earned              ║
║ System Flow:  Scheduler → CommissionServiceImpl.markCommissionEarned()         ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Deferred Commission ──► Commission Revenue (no cash movement - recognition)

Journal Entry (recordCommissionEarned):
  DR Deferred Commission (2031)    K75    [OUT: Liability cleared]
     CR Commission Revenue (4010)        K75    [IN: Revenue recognized]
```

**Code Path**:
```java
CommissionServiceImpl.markCommissionEarned(ticketId)
  └── accountingService.recordCommissionEarned(commissionId, eventId,
          amount, currency)
```

---

### 11. COMMISSION CLAWBACK (SYSTEM-INITIATED)

**Trigger**: Refund or chargeback requires commission reversal
**File**: `CommissionServiceImpl.java:179`
**Called When**: Refund approved or chargeback accepted

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: COMMISSION CLAWBACK                                                 ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    SYSTEM (Triggered by refund/chargeback processing)               ║
║ Trigger:      Customer refund approved OR chargeback accepted                  ║
║ System Flow:  RefundService/ChargebackService → CommissionService.clawback()   ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  If PENDING:  Deferred Commission reduced (no revenue impact)
  If EARNED:   Commission Revenue reversed (reduces recognized revenue)

Journal Entry (recordCommissionClawback - if was EARNED):
  DR Commission Revenue (4010)     K75    [OUT: Revenue reversed]
     CR Deferred Commission (2031)       K75    [IN: Or direct reduction]

Journal Entry (recordCommissionClawback - if was PENDING):
  DR Deferred Commission (2031)    K75    [Direct reduction - no revenue impact]
     CR Deferred Commission (2031)       K75    [Self-clearing for pending]
```

**Code Path**:
```java
CommissionServiceImpl.clawbackEarnedCommission(ticketId, refundRequestId, reason)
  └── accountingService.recordCommissionClawback(commissionId, refundRequestId,
          amount, wasEarned, currency)
```

---

### 12. REFUND RECORDING (USER-INITIATED)

**Trigger**: Customer requests refund, admin approves, gateway confirms
**File**: `RefundServiceImpl.java:222`
**Called When**: Gateway confirms refund completed

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: REFUND RECORDING                                                    ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    USER (Customer requests → Admin approves → Gateway executes)     ║
║ User Actions: 1) Customer clicks "Request Refund"                              ║
║               2) Admin reviews and clicks "Approve"                            ║
║               3) Gateway processes and sends callback                          ║
║ System Flow:  Dashboard → GraphQL → RefundService → Gateway → Callback         ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Event Escrow + Deferred Commission ──► Refunds Payable

Journal Entry (recordRefund):
  DR Event Escrow (2010-EVT)       K425   [OUT: Net portion from organizer]
  DR Deferred Commission (2031)     K75   [OUT: Commission clawed back]
     CR Refunds Payable (2022)           K500   [IN: We now owe customer]
```

**Code Path**:
```java
RefundServiceImpl.handlePawaPayCallback(status="COMPLETED", ...)
  └── accountingService.recordRefund(refundId, originalPaymentId, ticketId,
          eventId, refundAmount, commissionClawback, currency)
```

---

### 13. REFUND DISBURSEMENT (SYSTEM-INITIATED)

**Trigger**: Gateway callback confirms refund sent to customer
**File**: `RefundServiceImpl.java:232`
**Called When**: Immediately after refund recording

```
╔════════════════════════════════════════════════════════════════════════════════╗
║ TOUCHPOINT: REFUND DISBURSEMENT                                                 ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Initiator:    SYSTEM (Gateway callback confirms disbursement)                  ║
║ Trigger:      PawaPay confirms refund sent to customer's mobile money          ║
║ System Flow:  Gateway → Webhook → RefundServiceImpl.handlePawaPayCallback()    ║
╚════════════════════════════════════════════════════════════════════════════════╝

Money Flow:
  Our Bank Account ──K500──► Customer's Mobile Money

Journal Entry (recordRefundDisbursement):
  DR Refunds Payable (2022)        K500   [OUT: Liability cleared]
     CR Operating Bank (1011)            K500   [OUT: Money left our bank]
```

---

## Touchpoint Categories

### USER-INITIATED (Require Human Action)

| Touchpoint | Who | Action | Entry Point |
|------------|-----|--------|-------------|
| Gateway Settlement | Admin | Records bank deposit | GraphQL `recordGatewaySettlement` |
| Payout Recording | Admin | Approves & completes payout | GraphQL `completePayout` |
| Payout Disbursement | Admin | Records bank transfer | GraphQL `completePayout` |
| Refund Recording | Customer/Admin | Request/Approve refund | GraphQL `createRefundRequest` |

### SYSTEM-INITIATED (Automated by Webhooks/Schedulers)

| Touchpoint | Trigger | Source |
|------------|---------|--------|
| Ticket Sale | Payment webhook | PawaPay `onPaymentCompleted` |
| Chargeback Received | Chargeback webhook | PawaPay `onChargebackReceived` |
| Chargeback Recovery (4 types) | Recovery waterfall | `ChargebackServiceImpl.startRecovery()` |
| Commission Earned | Event completion | Scheduler / Event trigger |
| Commission Clawback | Refund/Chargeback | `CommissionServiceImpl` |
| Refund Disbursement | Gateway callback | PawaPay `onRefundCompleted` |

---

## Visual Flow: User vs System Touchpoints

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        USER-INITIATED TOUCHPOINTS                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────┐    ┌───────────────┐    ┌───────────────┐               │
│  │   CUSTOMER    │    │   ORGANIZER   │    │    ADMIN      │               │
│  └───────┬───────┘    └───────┬───────┘    └───────┬───────┘               │
│          │                    │                    │                        │
│          │ Request            │ Request            │ Record Settlement      │
│          │ Refund             │ Payout             │ Complete Payout        │
│          ▼                    ▼                    ▼                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     GraphQL API Layer                                │   │
│  │                    @PreAuthorize secured                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                   AccountingService                                  │   │
│  │  recordRefund(), recordPayout(), recordGatewaySettlement()          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                       SYSTEM-INITIATED TOUCHPOINTS                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌───────────────┐    ┌───────────────┐    ┌───────────────┐               │
│  │   PAWAPAY     │    │  SCHEDULER    │    │  EVENT BUS    │               │
│  │   WEBHOOKS    │    │   (CRON)      │    │  (MODULITH)   │               │
│  └───────┬───────┘    └───────┬───────┘    └───────┬───────┘               │
│          │                    │                    │                        │
│          │ Payment OK         │ Daily batch        │ PaymentCompletedEvent  │
│          │ Chargeback         │ Commission earn    │ ChargebackAcceptedEvent│
│          │ Refund OK          │                    │                        │
│          ▼                    ▼                    ▼                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                  Event Listeners / Service Impls                     │   │
│  │  PaymentEventListener, ChargebackServiceImpl, CommissionServiceImpl  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                   AccountingService                                  │   │
│  │  recordTicketSale(), recordChargebackReceived(), recordChargeback() │   │
│  │  recordCommissionEarned(), recordCommissionClawback()               │   │
│  │  recordRefundDisbursement()                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Audit Trail Requirements

Each touchpoint creates an audit trail in the `JournalEntry`:

```java
JournalEntry {
    id: "JE-2024-001234"
    type: TICKET_SALE | REFUND | CHARGEBACK | PAYOUT | SETTLEMENT | COMMISSION
    correlationId: "TKT-abc123"    // Links to business entity
    description: "Ticket sale for EVT-001"
    createdAt: "2024-01-15T10:30:00Z"
    createdBy: "system" | "user-uuid"    // USER vs SYSTEM indicator
    lines: [
        { accountCode: "1021", description: "...", debitAmount: 500, creditAmount: null },
        { accountCode: "2010-EVT001", description: "...", debitAmount: null, creditAmount: 425 },
        { accountCode: "2031", description: "...", debitAmount: null, creditAmount: 75 }
    ]
}
```

---

## Security Considerations

| Touchpoint | Authentication | Authorization |
|------------|---------------|---------------|
| Gateway Settlement | JWT + ADMIN role | `@PreAuthorize("hasRole('ADMIN')")` |
| Payout Recording | JWT + ADMIN role | `@PreAuthorize("hasRole('ADMIN')")` |
| Refund Recording | JWT + (ADMIN or owner) | Ownership check in service |
| Webhooks | API Key / Signature | `X-PawaPay-Signature` validation |
| Schedulers | Internal | No auth (runs as system) |
| Event Listeners | Internal | No auth (triggered by events) |

---

*Generated: 2024-01-15*
*Version: 1.0*
