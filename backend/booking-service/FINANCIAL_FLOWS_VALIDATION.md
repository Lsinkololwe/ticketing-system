# PML Event Ticketing Platform - Financial Flows Validation Guide

## Purpose

This document provides detailed step-by-step flows for every financial scenario in the platform. Each flow includes:
- **Trigger**: What initiates the flow
- **Steps**: Each step with expected data changes
- **Journal Entries**: Exact accounting entries created
- **Validations**: Checkpoints to verify correctness
- **Sample Data**: Concrete examples for testing

Use this document to:
1. Validate implementation correctness
2. Create integration tests
3. Debug financial discrepancies
4. Train new developers

---

## Table of Contents

1. [Flow 1: Ticket Purchase (Happy Path)](#flow-1-ticket-purchase-happy-path)
2. [Flow 2: Gateway Settlement (T+1)](#flow-2-gateway-settlement-t1)
3. [Flow 3: Organizer Payout Request](#flow-3-organizer-payout-request)
4. [Flow 4: Payout Disbursement](#flow-4-payout-disbursement)
5. [Flow 5: Customer Refund (Before Event)](#flow-5-customer-refund-before-event)
6. [Flow 6: Chargeback - Full Escrow Recovery](#flow-6-chargeback---full-escrow-recovery)
7. [Flow 7: Chargeback - Partial Escrow + Future Payouts](#flow-7-chargeback---partial-escrow--future-payouts)
8. [Flow 8: Chargeback - Write-off (Bad Debt)](#flow-8-chargeback---write-off-bad-debt)
9. [Flow 9: Event Completion - Commission Recognition](#flow-9-event-completion---commission-recognition)
10. [Flow 10: Event Cancellation](#flow-10-event-cancellation)
11. [Flow 11: Gateway Reconciliation](#flow-11-gateway-reconciliation)
12. [Flow 12: Bank Reconciliation](#flow-12-bank-reconciliation)
13. [Flow 13: Escrow Reconciliation](#flow-13-escrow-reconciliation)

---

## Flow 1: Ticket Purchase (Happy Path)

### Scenario
Customer purchases a K500 VIP ticket for "Lusaka Jazz Night" event.

### Pre-conditions
- Event exists: `EVT001` - "Lusaka Jazz Night"
- Organizer: `ORG001` - "Lusaka Events Ltd"
- Platform commission rate: 10%
- Gateway fee rate: 2.5%

### Trigger
```
POST /graphql
mutation {
  purchaseTicket(input: {
    eventId: "EVT001"
    ticketTypeId: "TKT-VIP"
    quantity: 1
    paymentMethod: MTN_MOBILE_MONEY
    phoneNumber: "+260971234567"
  })
}
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: Payment Intent Created                                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: BookingService.initiatePayment()                                              │
│ Collection: payment_intents                                                            │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "PI-2024-001",                                                                │
│   "eventId": "EVT001",                                                                 │
│   "organizerId": "ORG001",                                                             │
│   "customerId": "CUST001",                                                             │
│   "amount": 500.00,                         // Gross ticket price                      │
│   "currency": "ZMW",                                                                   │
│   "status": "PENDING",                                                                 │
│   "paymentMethod": "MTN_MOBILE_MONEY",                                                 │
│   "phoneNumber": "+260971234567",                                                      │
│   "breakdown": {                                                                       │
│     "ticketPrice": 500.00,                                                             │
│     "platformCommission": 50.00,            // 10% of 500                              │
│     "organizerAmount": 450.00,              // 500 - 50                                │
│     "gatewayFee": 12.50                     // 2.5% of 500 (estimated)                 │
│   },                                                                                   │
│   "createdAt": "2024-03-15T10:30:00Z",                                                 │
│   "expiresAt": "2024-03-15T10:45:00Z"       // 15 min expiry                           │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ platformCommission + organizerAmount = ticketPrice                                   │
│ ✓ status = "PENDING"                                                                   │
│ ✓ No journal entries yet (payment not confirmed)                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: PawaPay Payment Request Sent                                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: PaymentGatewayService.initiatePayment()                                       │
│ External API: POST https://api.pawapay.io/deposits                                     │
│                                                                                         │
│ Request Sent:                                                                           │
│ {                                                                                       │
│   "depositId": "PI-2024-001",               // Our reference                           │
│   "amount": "500",                                                                      │
│   "currency": "ZMW",                                                                   │
│   "correspondent": "MTN_MOMO_ZMB",                                                     │
│   "payer": {                                                                           │
│     "type": "MSISDN",                                                                  │
│     "address": { "value": "260971234567" }                                             │
│   },                                                                                   │
│   "statementDescription": "PML Tickets - Lusaka Jazz Night"                            │
│ }                                                                                       │
│                                                                                         │
│ Response Received:                                                                      │
│ {                                                                                       │
│   "depositId": "PI-2024-001",                                                          │
│   "status": "ACCEPTED",                     // Gateway accepted request                │
│   "created": "2024-03-15T10:30:05Z"                                                    │
│ }                                                                                       │
│                                                                                         │
│ Document Updated (payment_intents):                                                    │
│ {                                                                                       │
│   "status": "PROCESSING",                   // Changed from PENDING                    │
│   "providerStatus": "ACCEPTED",                                                        │
│   "providerTransactionId": "PP-TXN-123456", // PawaPay's ID                            │
│   "updatedAt": "2024-03-15T10:30:05Z"                                                  │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ status changed to "PROCESSING"                                                       │
│ ✓ providerTransactionId populated                                                      │
│ ✓ Customer receives USSD prompt on phone                                               │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Customer Approves on Phone (USSD)                                               │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Customer Action: Enters PIN on MTN MoMo prompt                                         │
│ Money Movement: K500 moves from Customer's MoMo → PawaPay Collection Account           │
│                                                                                         │
│ PHYSICAL MONEY LOCATION NOW:                                                            │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Customer's MTN MoMo:     K10,000 → K9,500 (K500 deducted)                           ││
│ │ PawaPay Collection:      K50,000 → K50,500 (K500 received)                          ││
│ │ Our Bank Account:        K100,000 (unchanged - not yet settled)                     ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: PawaPay Webhook Received (Payment Completed)                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Endpoint: POST /api/webhooks/pawapay/deposits                                          │
│ Service: PaymentWebhookController.handleDepositCallback()                              │
│                                                                                         │
│ Webhook Payload:                                                                        │
│ {                                                                                       │
│   "depositId": "PI-2024-001",                                                          │
│   "status": "COMPLETED",                                                               │
│   "amount": "500",                                                                      │
│   "currency": "ZMW",                                                                   │
│   "correspondent": "MTN_MOMO_ZMB",                                                     │
│   "created": "2024-03-15T10:30:05Z",                                                   │
│   "receivedByRecipient": "2024-03-15T10:31:15Z"                                        │
│ }                                                                                       │
│                                                                                         │
│ Document Updated (payment_intents):                                                    │
│ {                                                                                       │
│   "status": "SUCCEEDED",                    // Payment confirmed!                      │
│   "providerStatus": "COMPLETED",                                                       │
│   "confirmedAt": "2024-03-15T10:31:15Z",                                               │
│   "updatedAt": "2024-03-15T10:31:15Z"                                                  │
│ }                                                                                       │
│                                                                                         │
│ Event Published: PaymentCompletedEvent                                                 │
│ {                                                                                       │
│   "paymentIntentId": "PI-2024-001",                                                    │
│   "eventId": "EVT001",                                                                 │
│   "organizerId": "ORG001",                                                             │
│   "amount": 500.00,                                                                    │
│   "completedAt": "2024-03-15T10:31:15Z"                                                │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ status = "SUCCEEDED"                                                                 │
│ ✓ PaymentCompletedEvent published                                                      │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 5: Ticket Created                                                                  │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Listener: PaymentEventListener.onPaymentCompleted()                                    │
│ Service: TicketService.createTicket()                                                  │
│ Collection: tickets                                                                    │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "TKT-2024-001",                                                               │
│   "eventId": "EVT001",                                                                 │
│   "ticketTypeId": "TKT-VIP",                                                           │
│   "ticketTypeName": "VIP Section",                                                     │
│   "customerId": "CUST001",                                                             │
│   "paymentIntentId": "PI-2024-001",                                                    │
│   "price": 500.00,                                                                      │
│   "status": "PURCHASED",                                                               │
│   "qrCode": "TKT-2024-001-ABCD1234",        // For venue scanning                      │
│   "purchasedAt": "2024-03-15T10:31:15Z",                                               │
│   "validFrom": "2024-04-01T18:00:00Z",      // Event start time                        │
│   "validUntil": "2024-04-02T02:00:00Z"      // Event end time                          │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ Ticket created with PURCHASED status                                                 │
│ ✓ QR code generated                                                                    │
│ ✓ Linked to PaymentIntent                                                              │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 6: Commission Record Created                                                       │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: CommissionService.createCommission()                                          │
│ Collection: commissions                                                                │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "COM-2024-001",                                                               │
│   "eventId": "EVT001",                                                                 │
│   "organizerId": "ORG001",                                                             │
│   "ticketId": "TKT-2024-001",                                                          │
│   "paymentIntentId": "PI-2024-001",                                                    │
│   "grossSaleAmount": 500.00,                                                           │
│   "commissionRate": 0.10,                   // 10%                                     │
│   "commissionAmount": 50.00,                // 500 * 0.10                              │
│   "status": "PENDING",                      // Not yet earned                          │
│   "calculatedAt": "2024-03-15T10:31:16Z"                                               │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ Commission status = "PENDING" (not earned until event completes)                     │
│ ✓ commissionAmount = grossSaleAmount * commissionRate                                  │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 7: Event Escrow Account Updated                                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: EscrowService.creditEscrow()                                                  │
│ Collection: event_escrow_accounts                                                      │
│                                                                                         │
│ If First Sale (Account Created):                                                        │
│ {                                                                                       │
│   "_id": "ESC-EVT001",                                                                 │
│   "eventId": "EVT001",                                                                 │
│   "organizerId": "ORG001",                                                             │
│   "accountCode": "2010-EVT00001",           // Virtual account code                    │
│   "balance": 450.00,                        // 500 - 50 (commission)                   │
│   "currency": "ZMW",                                                                   │
│   "status": "ACTIVE",                                                                  │
│   "createdAt": "2024-03-15T10:31:16Z"                                                  │
│ }                                                                                       │
│                                                                                         │
│ If Existing Account (Balance Updated):                                                  │
│ {                                                                                       │
│   "balance": 45450.00,                      // Previous 45000 + 450                    │
│   "updatedAt": "2024-03-15T10:31:16Z"                                                  │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ Escrow balance = ticketPrice - commission                                            │
│ ✓ Account linked to correct event and organizer                                        │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 8: Journal Entry Created (THE CORE ACCOUNTING RECORD)                              │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: AccountingService.recordTicketSale()                                          │
│ Collection: journal_entries                                                            │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "JE-2024-001",                                                                │
│   "entryNumber": "JE-2024-03-00001",        // Sequential numbering                    │
│   "entryDate": "2024-03-15",                                                           │
│   "description": "Ticket sale - Lusaka Jazz Night - TKT-2024-001",                     │
│   "type": "STANDARD",                                                                  │
│   "status": "POSTED",                                                                  │
│   "referenceType": "PAYMENT_INTENT",                                                   │
│   "referenceId": "PI-2024-001",                                                        │
│   "lines": [                                                                           │
│     {                                                                                   │
│       "accountCode": "1021",                // Gateway Settlement Receivable           │
│       "accountName": "Gateway Settlement Receivable",                                  │
│       "debit": 500.00,                      // Full amount owed by gateway             │
│       "credit": 0.00,                                                                  │
│       "description": "MoMo collection - PI-2024-001",                                  │
│       "referenceType": "PAYMENT",                                                      │
│       "referenceId": "PI-2024-001"                                                     │
│     },                                                                                  │
│     {                                                                                   │
│       "accountCode": "2010-EVT00001",       // Event Escrow (per-event)                │
│       "accountName": "Event Escrow - Lusaka Jazz Night",                               │
│       "debit": 0.00,                                                                   │
│       "credit": 450.00,                     // Net to organizer                        │
│       "description": "Organizer escrow - TKT-2024-001",                                │
│       "referenceType": "TICKET",                                                       │
│       "referenceId": "TKT-2024-001"                                                    │
│     },                                                                                  │
│     {                                                                                   │
│       "accountCode": "2031",                // Deferred Commission Revenue             │
│       "accountName": "Deferred Commission Revenue",                                    │
│       "debit": 0.00,                                                                   │
│       "credit": 50.00,                      // Commission (deferred until event)       │
│       "description": "Platform commission - pending event completion",                 │
│       "referenceType": "COMMISSION",                                                   │
│       "referenceId": "COM-2024-001"                                                    │
│     }                                                                                   │
│   ],                                                                                    │
│   "totalDebits": 500.00,                                                               │
│   "totalCredits": 500.00,                                                              │
│   "createdAt": "2024-03-15T10:31:16Z",                                                 │
│   "createdBy": "SYSTEM"                                                                │
│ }                                                                                       │
│                                                                                         │
│ ═══════════════════════════════════════════════════════════════════════════════════════│
│                                                                                         │
│ JOURNAL ENTRY VISUALIZATION:                                                            │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00001 | 2024-03-15 | Ticket Sale - Lusaka Jazz Night                    ││
│ ├──────────────────┬─────────────────────────────────────┬──────────┬─────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit    │ Credit          ││
│ ├──────────────────┼─────────────────────────────────────┼──────────┼─────────────────┤│
│ │ 1021             │ Gateway Settlement Receivable       │ K500.00  │                 ││
│ │ 2010-EVT00001    │ Event Escrow - Lusaka Jazz Night    │          │ K450.00         ││
│ │ 2031             │ Deferred Commission Revenue         │          │ K50.00          ││
│ ├──────────────────┴─────────────────────────────────────┼──────────┼─────────────────┤│
│ │                                              TOTALS    │ K500.00  │ K500.00     ✓   ││
│ └────────────────────────────────────────────────────────┴──────────┴─────────────────┘│
│                                                                                         │
│ VALIDATION CHECKPOINTS:                                                                 │
│ ✓ totalDebits == totalCredits (CRITICAL - must balance)                                │
│ ✓ Debit 1021 = full payment amount (gateway owes us this)                              │
│ ✓ Credit 2010-XXX = payment - commission (organizer's share)                           │
│ ✓ Credit 2031 = commission (DEFERRED, not 4010 revenue yet)                            │
│ ✓ Commission goes to 2031 DEFERRED, not 4010 EARNED                                    │
│ ✓ All lines have referenceType and referenceId for audit trail                         │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Final State After Ticket Purchase

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ FINAL STATE SUMMARY                                                                     │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ DOCUMENTS CREATED/UPDATED:                                                              │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Collection           │ Document ID      │ Key Fields                                ││
│ ├──────────────────────┼──────────────────┼───────────────────────────────────────────┤│
│ │ payment_intents      │ PI-2024-001      │ status: SUCCEEDED                         ││
│ │ tickets              │ TKT-2024-001     │ status: PURCHASED                         ││
│ │ commissions          │ COM-2024-001     │ status: PENDING, amount: K50              ││
│ │ event_escrow_accounts│ ESC-EVT001       │ balance: K450 (added)                     ││
│ │ journal_entries      │ JE-2024-001      │ status: POSTED, balanced: true            ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ACCOUNT BALANCES CHANGED:                                                               │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Account              │ Before           │ After            │ Change                 ││
│ ├──────────────────────┼──────────────────┼──────────────────┼────────────────────────┤│
│ │ 1021 Gateway Recv.   │ K12,000.00       │ K12,500.00       │ +K500.00 (DR)          ││
│ │ 2010-EVT00001 Escrow │ K45,000.00       │ K45,450.00       │ +K450.00 (CR)          ││
│ │ 2031 Deferred Comm.  │ K5,000.00        │ K5,050.00        │ +K50.00 (CR)           ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ PHYSICAL MONEY LOCATION:                                                                │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ PawaPay Collection Account: +K500 (will settle T+1)                                 ││
│ │ Our Bank Account: Unchanged (waiting for settlement)                                 ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 2: Gateway Settlement (T+1)

### Scenario
PawaPay settles previous day's collections (K12,500 gross, K312.50 in fees).

### Pre-conditions
- Previous day collections: K12,500
- Gateway fee: 2.5%
- Net settlement: K12,187.50 (K12,500 - K312.50)

### Trigger
```
Bank notification: K12,187.50 received from PawaPay
Settlement file received via SFTP/API
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: Settlement File Received                                                        │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: ReconciliationService.processSettlementFile()                                 │
│                                                                                         │
│ Settlement File Contents (CSV/JSON):                                                    │
│ {                                                                                       │
│   "settlementId": "SETTLE-2024-03-15",                                                 │
│   "settlementDate": "2024-03-15",                                                      │
│   "transactionDate": "2024-03-14",          // Transactions from previous day          │
│   "grossAmount": 12500.00,                                                             │
│   "feeAmount": 312.50,                      // 2.5% of gross                           │
│   "netAmount": 12187.50,                    // What we actually receive                │
│   "transactionCount": 25,                                                              │
│   "transactions": [                                                                    │
│     { "depositId": "PI-2024-001", "amount": 500.00, "status": "COMPLETED" },           │
│     { "depositId": "PI-2024-002", "amount": 250.00, "status": "COMPLETED" },           │
│     // ... 23 more transactions                                                        │
│   ]                                                                                     │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Reconciliation Run Created                                                      │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Collection: reconciliation_runs                                                        │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "RECON-2024-001",                                                             │
│   "type": "GATEWAY",                                                                   │
│   "reconciliationDate": "2024-03-15",                                                  │
│   "status": "IN_PROGRESS",                                                             │
│   "expectedTotal": 12500.00,                // From settlement file                    │
│   "actualTotal": 0.00,                      // To be calculated                        │
│   "matchedCount": 0,                                                                   │
│   "unmatchedCount": 0,                                                                 │
│   "items": [],                              // To be populated                         │
│   "createdAt": "2024-03-15T03:00:00Z"       // Scheduled at 3 AM                       │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Transaction Matching (Gap 2 Implementation)                                     │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: ReconciliationService.processGatewayMatching()                                │
│                                                                                         │
│ For Each Settlement Transaction:                                                        │
│ 1. Find PaymentIntent by providerTransactionId                                         │
│ 2. Compare amounts                                                                      │
│ 3. Mark as MATCHED, AMOUNT_MISMATCH, or MISSING_INTERNAL                               │
│                                                                                         │
│ Matching Results:                                                                       │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ External ID    │ External Amt │ Internal ID    │ Internal Amt │ Status              ││
│ ├────────────────┼──────────────┼────────────────┼──────────────┼─────────────────────┤│
│ │ PI-2024-001    │ K500.00      │ PI-2024-001    │ K500.00      │ MATCHED             ││
│ │ PI-2024-002    │ K250.00      │ PI-2024-002    │ K250.00      │ MATCHED             ││
│ │ PI-2024-003    │ K100.00      │ PI-2024-003    │ K99.50       │ AMOUNT_MISMATCH     ││
│ │ PI-2024-025    │ K75.00       │ -              │ -            │ MISSING_INTERNAL    ││
│ │ -              │ -            │ PI-2024-099    │ K150.00      │ UNMATCHED_INTERNAL  ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ Document Updated (reconciliation_runs):                                                │
│ {                                                                                       │
│   "status": "REQUIRES_REVIEW",              // Due to discrepancies                    │
│   "actualTotal": 12425.00,                  // Sum of matched internal amounts         │
│   "variance": 75.00,                        // expectedTotal - actualTotal             │
│   "matchedCount": 22,                                                                  │
│   "unmatchedCount": 3,                                                                 │
│   "items": [/* 25 ReconciliationItem objects */]                                       │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ Each settlement transaction matched against internal records                          │
│ ✓ Discrepancies flagged for review                                                     │
│ ✓ MISSING_INTERNAL items need webhook investigation                                    │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: Settlement Journal Entry Created                                                │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: AccountingService.recordGatewaySettlement()                                   │
│ Collection: journal_entries                                                            │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "JE-2024-002",                                                                │
│   "entryNumber": "JE-2024-03-00002",                                                   │
│   "entryDate": "2024-03-15",                                                           │
│   "description": "PawaPay settlement - SETTLE-2024-03-15",                             │
│   "type": "STANDARD",                                                                  │
│   "status": "POSTED",                                                                  │
│   "lines": [                                                                           │
│     {                                                                                   │
│       "accountCode": "1011",                // Operating Bank Account                  │
│       "accountName": "Primary Operating Bank Account",                                 │
│       "debit": 12187.50,                    // NET amount received                     │
│       "credit": 0.00,                                                                  │
│       "description": "Settlement received from PawaPay"                                │
│     },                                                                                  │
│     {                                                                                   │
│       "accountCode": "5010",                // Gateway Fee Expense                     │
│       "accountName": "Payment Gateway Fees",                                           │
│       "debit": 312.50,                      // Fee deducted by gateway                 │
│       "credit": 0.00,                                                                  │
│       "description": "PawaPay processing fees - 2.5%"                                  │
│     },                                                                                  │
│     {                                                                                   │
│       "accountCode": "1021",                // Gateway Settlement Receivable           │
│       "accountName": "Gateway Settlement Receivable",                                  │
│       "debit": 0.00,                                                                   │
│       "credit": 12500.00,                   // GROSS amount cleared                    │
│       "description": "Settlement of March 14 collections"                              │
│     }                                                                                   │
│   ],                                                                                    │
│   "totalDebits": 12500.00,                                                             │
│   "totalCredits": 12500.00                                                             │
│ }                                                                                       │
│                                                                                         │
│ JOURNAL ENTRY VISUALIZATION:                                                            │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00002 | 2024-03-15 | PawaPay Settlement                                 ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 1011             │ Operating Bank Account              │ K12,187.50│                ││
│ │ 5010             │ Payment Gateway Fees                │ K312.50   │                ││
│ │ 1021             │ Gateway Settlement Receivable       │           │ K12,500.00     ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K12,500.00│ K12,500.00  ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ VALIDATION CHECKPOINTS:                                                                 │
│ ✓ Debit 1011 = NET settlement (what we actually received in bank)                      │
│ ✓ Debit 5010 = Gateway fees (expense recognition)                                      │
│ ✓ Credit 1021 = GROSS amount (clears the receivable)                                   │
│ ✓ Net + Fees = Gross                                                                   │
│ ✓ 1021 balance should decrease toward zero after settlement                            │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Physical Money Movement

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ PHYSICAL MONEY MOVEMENT                                                                 │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ BEFORE SETTLEMENT:                                                                      │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ PawaPay Collection Account:      K50,500 (includes our K12,500)                     ││
│ │ Our Operating Bank Account:      K100,000                                           ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ SETTLEMENT TRANSFER:                                                                    │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ PawaPay → Stanbic Bank Transfer                                                     ││
│ │ Amount: K12,187.50 (K12,500 - K312.50 fee)                                          ││
│ │ Fee retained by PawaPay: K312.50                                                    ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ AFTER SETTLEMENT:                                                                       │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ PawaPay Collection Account:      K38,000 (K50,500 - K12,500)                        ││
│ │ Our Operating Bank Account:      K112,187.50 (K100,000 + K12,187.50)                ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 3: Organizer Payout Request

### Scenario
Organizer "Lusaka Events Ltd" requests K30,000 payout from "Lusaka Jazz Night" escrow.

### Pre-conditions
- Event: EVT001 - "Lusaka Jazz Night" (Event completed)
- Escrow balance: K45,000
- Commission status: EARNED (event completed)
- Organizer verified: Yes

### Trigger
```
POST /graphql
mutation {
  requestPayout(input: {
    eventId: "EVT001"
    amount: 30000.00
    payoutMethod: MTN_MOBILE_MONEY
    phoneNumber: "+260977654321"
  })
}
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: Payout Request Created                                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: PayoutService.requestPayout()                                                 │
│ Collection: payout_requests                                                            │
│                                                                                         │
│ Validations Performed:                                                                  │
│ ✓ Event exists and belongs to organizer                                                │
│ ✓ Event status = COMPLETED or PAST                                                     │
│ ✓ Escrow balance >= requested amount                                                   │
│ ✓ Organizer verified and not blocked                                                   │
│ ✓ No pending payout request for same event                                             │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "PAY-2024-001",                                                               │
│   "eventId": "EVT001",                                                                 │
│   "organizerId": "ORG001",                                                             │
│   "requestedAmount": 30000.00,                                                         │
│   "payoutFee": 50.00,                       // K50 flat fee for payout                 │
│   "netPayoutAmount": 29950.00,              // 30000 - 50                              │
│   "currency": "ZMW",                                                                   │
│   "payoutMethod": "MTN_MOBILE_MONEY",                                                  │
│   "recipientPhone": "+260977654321",                                                   │
│   "status": "PENDING",                      // Awaiting approval                       │
│   "requestedAt": "2024-04-05T14:30:00Z",                                               │
│   "notes": ""                                                                          │
│ }                                                                                       │
│                                                                                         │
│ VALIDATION CHECKPOINT:                                                                  │
│ ✓ status = "PENDING" (not auto-approved)                                               │
│ ✓ netPayoutAmount = requestedAmount - payoutFee                                        │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Finance Approval                                                                │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Trigger: Finance team reviews and approves payout                                      │
│                                                                                         │
│ mutation {                                                                              │
│   approvePayoutRequest(id: "PAY-2024-001", notes: "Approved - Event completed")        │
│ }                                                                                       │
│                                                                                         │
│ Service: PayoutService.approvePayout()                                                 │
│                                                                                         │
│ Document Updated (payout_requests):                                                    │
│ {                                                                                       │
│   "status": "APPROVED",                                                                │
│   "approvedBy": "ADMIN-001",                                                           │
│   "approvedAt": "2024-04-05T15:00:00Z",                                                │
│   "notes": "Approved - Event completed"                                                │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Escrow to Payable Journal Entry                                                 │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: AccountingService.recordPayoutApproval()                                      │
│                                                                                         │
│ Journal Entry:                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00050 | 2024-04-05 | Payout Approved - Lusaka Events Ltd                ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 2010-EVT00001    │ Event Escrow - Lusaka Jazz Night    │ K30,000.00│                ││
│ │ 2021             │ Organizer Payouts Payable           │           │ K29,950.00     ││
│ │ 4020             │ Payout Processing Fee Revenue       │           │ K50.00         ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K30,000.00│ K30,000.00  ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ VALIDATION CHECKPOINTS:                                                                 │
│ ✓ Debit 2010-XXX reduces escrow liability (we owe less to escrow)                      │
│ ✓ Credit 2021 creates payable (we now owe organizer directly)                          │
│ ✓ Credit 4020 recognizes payout fee as revenue                                         │
│ ✓ Escrow account balance decreased by K30,000                                          │
│                                                                                         │
│ Escrow Account Updated:                                                                 │
│ {                                                                                       │
│   "balance": 15000.00                       // Was 45000, now 45000 - 30000            │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 4: Payout Disbursement

### Scenario
Approved payout PAY-2024-001 is disbursed to organizer via MTN Mobile Money.

### Trigger
```
Scheduled job or manual trigger: PayoutService.processPendingPayouts()
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: PawaPay Payout Request                                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: PayoutGatewayService.sendPayout()                                             │
│ External API: POST https://api.pawapay.io/payouts                                      │
│                                                                                         │
│ Request:                                                                                │
│ {                                                                                       │
│   "payoutId": "PAY-2024-001",                                                          │
│   "amount": "29950",                        // Net payout amount                       │
│   "currency": "ZMW",                                                                   │
│   "correspondent": "MTN_MOMO_ZMB",                                                     │
│   "recipient": {                                                                        │
│     "type": "MSISDN",                                                                  │
│     "address": { "value": "260977654321" }                                             │
│   },                                                                                   │
│   "statementDescription": "PML Payout - Lusaka Jazz Night"                             │
│ }                                                                                       │
│                                                                                         │
│ Document Updated (payout_requests):                                                    │
│ {                                                                                       │
│   "status": "PROCESSING",                                                              │
│   "providerTransactionId": "PP-PAYOUT-789",                                            │
│   "processingStartedAt": "2024-04-05T16:00:00Z"                                        │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Payout Completion Webhook                                                       │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Endpoint: POST /api/webhooks/pawapay/payouts                                           │
│                                                                                         │
│ Webhook Payload:                                                                        │
│ {                                                                                       │
│   "payoutId": "PAY-2024-001",                                                          │
│   "status": "COMPLETED",                                                               │
│   "amount": "29950",                                                                   │
│   "completedAt": "2024-04-05T16:01:30Z"                                                │
│ }                                                                                       │
│                                                                                         │
│ Document Updated (payout_requests):                                                    │
│ {                                                                                       │
│   "status": "COMPLETED",                                                               │
│   "completedAt": "2024-04-05T16:01:30Z"                                                │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Disbursement Journal Entry                                                      │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Journal Entry:                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00051 | 2024-04-05 | Payout Disbursed - PAY-2024-001                    ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 2021             │ Organizer Payouts Payable           │ K29,950.00│                ││
│ │ 1011             │ Operating Bank Account              │           │ K29,950.00     ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K29,950.00│ K29,950.00  ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ VALIDATION CHECKPOINTS:                                                                 │
│ ✓ Debit 2021 clears the payable (we no longer owe)                                     │
│ ✓ Credit 1011 reduces bank balance (money left our account)                            │
│ ✓ 2021 balance should be K0 for this payout                                            │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Physical Money Movement

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ PHYSICAL MONEY MOVEMENT                                                                 │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Our Operating Bank → PawaPay → Organizer's MTN MoMo                                    │
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Our Operating Bank Account:    K112,187.50 → K82,237.50 (-K29,950)                  ││
│ │ Organizer's MTN MoMo:          K5,000.00 → K34,950.00 (+K29,950)                    ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 6: Chargeback - Full Escrow Recovery

### Scenario
Customer files chargeback for K500 ticket. Organizer has K45,000 in escrow - full recovery from escrow.

### Pre-conditions
- Original ticket: TKT-2024-001, K500
- Chargeback amount: K500 + K25 fee = K525
- Event escrow balance: K45,000
- Organizer: ORG001

### Trigger
```
PawaPay webhook: Chargeback notification received
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: Chargeback Record Created                                                       │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: ChargebackService.processChargeback()                                         │
│ Collection: chargeback_records                                                         │
│                                                                                         │
│ Document Created:                                                                       │
│ {                                                                                       │
│   "_id": "CB-2024-001",                                                                │
│   "paymentIntentId": "PI-2024-001",                                                    │
│   "ticketId": "TKT-2024-001",                                                          │
│   "eventId": "EVT001",                                                                 │
│   "organizerId": "ORG001",                                                             │
│   "originalAmount": 500.00,                                                            │
│   "chargebackFee": 25.00,                   // Gateway charges this fee                │
│   "totalChargebackAmount": 525.00,          // Amount to recover                       │
│   "reason": "FRAUD",                                                                   │
│   "status": "RECEIVED",                                                                │
│   "recoveryStatus": "PENDING",                                                         │
│   "recoveries": [],                                                                    │
│   "receivedAt": "2024-04-10T09:00:00Z"                                                 │
│ }                                                                                       │
│                                                                                         │
│ Event Published: ChargebackReceivedEvent                                               │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Chargeback Journal Entry (Initial Recording)                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ This entry records:                                                                     │
│ 1. Gateway takes back the money (Credit 1011 or 1021)                                  │
│ 2. We recognize the chargeback fee expense (Debit 5020)                                │
│ 3. We create a receivable from organizer (Debit 1023)                                  │
│                                                                                         │
│ Journal Entry:                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00100 | 2024-04-10 | Chargeback Received - CB-2024-001                  ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 1023             │ Chargeback Recovery Receivable      │ K525.00   │                ││
│ │ 5020             │ Chargeback Expense                  │ K25.00    │                ││
│ │ 1011             │ Operating Bank Account              │           │ K525.00        ││
│ │ 5020             │ Chargeback Expense (reversal)       │           │ K25.00         ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K550.00   │ K550.00     ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ Note: The fee is recorded separately - it's an expense to the platform                 │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Recovery Waterfall - Attempt Escrow Recovery                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: ChargebackService.startRecovery() → attemptRecoveryFromEscrow()               │
│                                                                                         │
│ Check: Escrow balance K45,000 >= Recovery amount K525 ✓                                │
│ Result: FULL RECOVERY FROM ESCROW                                                       │
│                                                                                         │
│ Chargeback Record Updated:                                                              │
│ {                                                                                       │
│   "recoveryStatus": "FULLY_RECOVERED",                                                 │
│   "recoveries": [                                                                      │
│     {                                                                                   │
│       "source": "ORGANIZER_ESCROW",                                                    │
│       "sourceId": "ESC-EVT001",                                                        │
│       "amount": 525.00,                                                                │
│       "recoveredAt": "2024-04-10T09:00:05Z"                                            │
│     }                                                                                   │
│   ],                                                                                    │
│   "totalRecovered": 525.00,                                                            │
│   "unrecoveredAmount": 0.00                                                            │
│ }                                                                                       │
│                                                                                         │
│ Escrow Account Updated:                                                                 │
│ {                                                                                       │
│   "balance": 44475.00                       // Was 45000, now 45000 - 525              │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: Recovery Journal Entry                                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Journal Entry:                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00101 | 2024-04-10 | Chargeback Recovery from Escrow - CB-2024-001      ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 2010-EVT00001    │ Event Escrow - Lusaka Jazz Night    │ K525.00   │                ││
│ │ 1023             │ Chargeback Recovery Receivable      │           │ K525.00        ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K525.00   │ K525.00     ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ VALIDATION CHECKPOINTS:                                                                 │
│ ✓ Debit 2010-XXX reduces escrow liability (organizer's funds used for recovery)        │
│ ✓ Credit 1023 clears the receivable (recovery complete)                                │
│ ✓ 1023 balance = K0 after full recovery                                                │
│ ✓ Platform protected - no loss incurred                                                │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 7: Chargeback - Partial Escrow + Future Payouts

### Scenario
Chargeback for K525, but escrow only has K200. Organizer has K1,000 pending payout. Recovery from both sources.

### Pre-conditions
- Chargeback amount: K525
- Event escrow balance: K200 (insufficient)
- Organizer's pending payout: K1,000 (PAY-2024-002)

### Recovery Waterfall

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ RECOVERY WATERFALL                                                                      │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Step 1: ORGANIZER_ESCROW                                                            ││
│ │ ════════════════════════                                                            ││
│ │                                                                                     ││
│ │ Escrow Balance: K200                                                                ││
│ │ Recovery Needed: K525                                                               ││
│ │ Recovered: K200 (all available)                                                     ││
│ │ Remaining: K325                                                                     ││
│ │                                                                                     ││
│ │ Escrow Balance After: K0                                                            ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                       │                                                 │
│                                       ▼                                                 │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Step 2: ORGANIZER_FUTURE (Gap 1 Implementation)                                     ││
│ │ ═══════════════════════════════════════════════                                     ││
│ │                                                                                     ││
│ │ Query: Find organizer's pending payouts                                             ││
│ │   - PAY-2024-002: K1,000 (status: APPROVED)                                         ││
│ │                                                                                     ││
│ │ Recovery Needed: K325                                                               ││
│ │ Available: K1,000                                                                   ││
│ │ Recovered: K325                                                                     ││
│ │ Remaining: K0 (FULLY RECOVERED!)                                                    ││
│ │                                                                                     ││
│ │ Payout Updated:                                                                     ││
│ │ {                                                                                   ││
│ │   "netPayoutAmount": 675.00,             // Was 1000, now 1000 - 325                ││
│ │   "notes": "[2024-04-10] CHARGEBACK RECOVERY: K325.00 for CB-2024-002"              ││
│ │ }                                                                                   ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ FINAL STATUS: FULLY_RECOVERED                                                           │
│ Total Recovered: K525 (K200 from escrow + K325 from pending payout)                    │
│ Platform Loss: K0                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Journal Entries for Partial Recovery

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ JOURNAL ENTRY 1: Recovery from Escrow (K200)                                            │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00110 | 2024-04-10 | Chargeback Recovery (Escrow) - CB-2024-002         ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 2010-EVT00002    │ Event Escrow - Rock Festival        │ K200.00   │                ││
│ │ 1023             │ Chargeback Recovery Receivable      │           │ K200.00        ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K200.00   │ K200.00     ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ JOURNAL ENTRY 2: Recovery from Pending Payout (K325)                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00111 | 2024-04-10 | Chargeback Recovery (Payout) - CB-2024-002         ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 2021             │ Organizer Payouts Payable           │ K325.00   │                ││
│ │ 1023             │ Chargeback Recovery Receivable      │           │ K325.00        ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K325.00   │ K325.00     ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ Note: This reduces the payable to the organizer. They will receive K325 less.          │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 8: Chargeback - Write-off (Bad Debt)

### Scenario
Chargeback for K525, escrow empty, no pending payouts, platform reserve insufficient. Write-off required.

### Pre-conditions
- Chargeback amount: K525
- Event escrow balance: K0
- Organizer's pending payouts: K0
- Platform reserve balance: K100

### Recovery Waterfall

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ RECOVERY WATERFALL - WRITE-OFF SCENARIO                                                 │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Step 1: ORGANIZER_ESCROW                                                            ││
│ │ ════════════════════════                                                            ││
│ │ Escrow Balance: K0                                                                  ││
│ │ Recovered: K0                                                                       ││
│ │ Remaining: K525                                                                     ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                       │                                                 │
│                                       ▼                                                 │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Step 2: ORGANIZER_FUTURE                                                            ││
│ │ ════════════════════════                                                            ││
│ │ Pending Payouts: K0                                                                 ││
│ │ Recovered: K0                                                                       ││
│ │ Remaining: K525                                                                     ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                       │                                                 │
│                                       ▼                                                 │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Step 3: PLATFORM_RESERVE                                                            ││
│ │ ════════════════════════                                                            ││
│ │ Reserve Balance: K100                                                               ││
│ │ Recovered: K100 (all available)                                                     ││
│ │ Remaining: K425                                                                     ││
│ │                                                                                     ││
│ │ Reserve Balance After: K0                                                           ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                       │                                                 │
│                                       ▼                                                 │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Step 4: WRITE_OFF (Bad Debt)                                                        ││
│ │ ════════════════════════════                                                        ││
│ │ Amount Written Off: K425                                                            ││
│ │                                                                                     ││
│ │ THIS IS A PLATFORM LOSS!                                                            ││
│ │ Recorded as expense in account 5040 (Bad Debt Expense)                              ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ FINAL STATUS: PARTIALLY_RECOVERED                                                       │
│ Total Recovered: K100 (from reserve only)                                              │
│ Written Off: K425                                                                       │
│ Platform Loss: K425                                                                     │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Write-off Journal Entry

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ JOURNAL ENTRY: Write-off (Bad Debt)                                                     │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00125 | 2024-04-10 | Chargeback Write-off - CB-2024-003                 ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 5040             │ Bad Debt Expense                    │ K425.00   │                ││
│ │ 1023             │ Chargeback Recovery Receivable      │           │ K425.00        ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K425.00   │ K425.00     ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ IMPACT:                                                                                 │
│ - Bad Debt Expense increases (reduces net income)                                       │
│ - Chargeback Receivable cleared (nothing more to collect)                              │
│ - Platform's profitability impacted                                                     │
│                                                                                         │
│ ALERT: This triggers a financial alert for management review                           │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 9: Event Completion - Commission Recognition

### Scenario
Event "Lusaka Jazz Night" completes successfully. All deferred commissions become earned revenue.

### Pre-conditions
- Event: EVT001 status changed to COMPLETED
- Total deferred commission: K5,000 (from 100 ticket sales)

### Trigger
```
Event status change: SCHEDULED → COMPLETED
or
Scheduled job: CommissionService.recognizeDueCommissions()
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: Identify Pending Commissions for Event                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Query: commissions.find({ eventId: "EVT001", status: "PENDING" })                      │
│                                                                                         │
│ Results:                                                                                │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Commission ID    │ Ticket ID        │ Commission Amount │ Status                    ││
│ ├──────────────────┼──────────────────┼───────────────────┼───────────────────────────┤│
│ │ COM-2024-001     │ TKT-2024-001     │ K50.00            │ PENDING                   ││
│ │ COM-2024-002     │ TKT-2024-002     │ K25.00            │ PENDING                   ││
│ │ COM-2024-003     │ TKT-2024-003     │ K100.00           │ PENDING                   ││
│ │ ... (97 more)    │ ...              │ ...               │ PENDING                   ││
│ ├──────────────────┴──────────────────┼───────────────────┼───────────────────────────┤│
│ │                             TOTAL   │ K5,000.00         │                           ││
│ └─────────────────────────────────────┴───────────────────┴───────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Update Commission Status                                                        │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Service: CommissionService.recognizeCommission()                                       │
│                                                                                         │
│ For each commission:                                                                    │
│ {                                                                                       │
│   "status": "EARNED",                       // Changed from PENDING                    │
│   "earnedAt": "2024-04-02T02:00:00Z"        // Event end time                          │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: Commission Recognition Journal Entry                                            │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Journal Entry:                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-04-00001 | 2024-04-02 | Commission Recognition - EVT001 Lusaka Jazz Night  ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 2031             │ Deferred Commission Revenue         │ K5,000.00 │                ││
│ │ 4010             │ Commission Revenue                  │           │ K5,000.00      ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K5,000.00 │ K5,000.00   ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ VALIDATION CHECKPOINTS:                                                                 │
│ ✓ Debit 2031 reduces deferred liability (no longer "owed" as future service)           │
│ ✓ Credit 4010 recognizes earned revenue (impacts income statement)                     │
│ ✓ NO CASH MOVEMENT - this is pure accounting reclassification                          │
│ ✓ Total recognized = sum of all commission amounts for this event                      │
│                                                                                         │
│ Business Impact:                                                                        │
│ - Platform can now report this as EARNED revenue                                        │
│ - Shows on income statement as Commission Revenue                                       │
│ - Increases profitability metrics                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 11: Gateway Reconciliation

### Scenario
Daily gateway reconciliation at 3 AM CAT to match PawaPay settlement against internal records.

### Trigger
```
Scheduled: ReconciliationScheduler.runGatewayReconciliation() at 3:00 AM CAT
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ GATEWAY RECONCILIATION PROCESS                                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ INPUT:                                                                                  │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Gateway Settlement File (External Source - PawaPay)                                 ││
│ │ ─────────────────────────────────────────────────────                               ││
│ │ Transaction ID  │ Amount    │ Status    │ Timestamp                                 ││
│ │ PI-2024-001     │ K500.00   │ COMPLETED │ 2024-03-14T10:31:15Z                      ││
│ │ PI-2024-002     │ K250.00   │ COMPLETED │ 2024-03-14T11:45:20Z                      ││
│ │ PI-2024-003     │ K100.00   │ COMPLETED │ 2024-03-14T14:22:33Z                      ││
│ │ PI-2024-004     │ K75.00    │ COMPLETED │ 2024-03-14T16:55:10Z                      ││
│ │ ... (21 more)                                                                       ││
│ │ TOTAL: K12,500.00 (25 transactions)                                                 ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Internal Payment Records (MongoDB - payment_intents)                                ││
│ │ ──────────────────────────────────────────────────────                              ││
│ │ Query: { status: "SUCCEEDED", processedAt: { $gte: "2024-03-14", $lt: "2024-03-15" ││
│ │ ────────────────────────────────────────────────────────────────────────────────    ││
│ │ Transaction ID  │ Amount    │ Status    │ processedAt                               ││
│ │ PI-2024-001     │ K500.00   │ SUCCEEDED │ 2024-03-14T10:31:15Z                      ││
│ │ PI-2024-002     │ K250.00   │ SUCCEEDED │ 2024-03-14T11:45:20Z                      ││
│ │ PI-2024-003     │ K99.50    │ SUCCEEDED │ 2024-03-14T14:22:33Z    ← MISMATCH!       ││
│ │ PI-2024-005     │ K300.00   │ SUCCEEDED │ 2024-03-14T18:00:00Z    ← NOT IN GATEWAY! ││
│ │ ... (20 more)                                                                       ││
│ │ TOTAL: K12,425.00 (24 transactions)                                                 ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ MATCHING ALGORITHM (processGatewayMatching)                                             │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ For each external transaction:                                                          │
│   1. Find internal record by providerTransactionId                                     │
│   2. If not found → MISSING_INTERNAL                                                   │
│   3. If found, compare amounts (with K0.01 tolerance)                                  │
│      - Difference ≤ K0.01 → MATCHED                                                    │
│      - Difference > K0.01 → AMOUNT_MISMATCH                                            │
│                                                                                         │
│ For each internal transaction not matched:                                              │
│   → UNMATCHED_INTERNAL                                                                 │
│                                                                                         │
│ RESULTS:                                                                                │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Transaction     │ External    │ Internal    │ Variance   │ Status                   ││
│ ├─────────────────┼─────────────┼─────────────┼────────────┼──────────────────────────┤│
│ │ PI-2024-001     │ K500.00     │ K500.00     │ K0.00      │ MATCHED                  ││
│ │ PI-2024-002     │ K250.00     │ K250.00     │ K0.00      │ MATCHED                  ││
│ │ PI-2024-003     │ K100.00     │ K99.50      │ K0.50      │ AMOUNT_MISMATCH ⚠️       ││
│ │ PI-2024-004     │ K75.00      │ -           │ K75.00     │ MISSING_INTERNAL ❌       ││
│ │ PI-2024-005     │ -           │ K300.00     │ K300.00    │ UNMATCHED_INTERNAL ❌     ││
│ │ ... (20 MATCHED)│ ...         │ ...         │ K0.00      │ MATCHED                  ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ SUMMARY:                                                                                │
│   Matched: 22                                                                           │
│   Amount Mismatch: 1 (K0.50 variance)                                                  │
│   Missing Internal: 1 (K75.00) - Webhook may have failed                               │
│   Unmatched Internal: 1 (K300.00) - Our record, gateway doesn't have it               │
│   Total Variance: K375.50                                                              │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ RECONCILIATION RUN RECORD                                                               │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Document Created/Updated (reconciliation_runs):                                        │
│ {                                                                                       │
│   "_id": "RECON-2024-03-15-GW",                                                        │
│   "type": "GATEWAY",                                                                   │
│   "reconciliationDate": "2024-03-15",                                                  │
│   "status": "REQUIRES_REVIEW",              // Due to discrepancies                    │
│   "expectedTotal": 12500.00,                // From gateway settlement                 │
│   "actualTotal": 12425.00,                  // From our records                        │
│   "variance": 75.00,                        // Difference                              │
│   "matchedCount": 22,                                                                  │
│   "unmatchedCount": 3,                                                                 │
│   "items": [                                                                           │
│     {                                                                                   │
│       "externalId": "PI-2024-003",                                                     │
│       "internalId": "PI-2024-003",                                                     │
│       "externalAmount": 100.00,                                                        │
│       "internalAmount": 99.50,                                                         │
│       "status": "AMOUNT_MISMATCH",                                                     │
│       "notes": "Variance: K0.50"                                                       │
│     },                                                                                  │
│     {                                                                                   │
│       "externalId": "PI-2024-004",                                                     │
│       "externalAmount": 75.00,                                                         │
│       "status": "MISSING_INTERNAL",                                                    │
│       "notes": "No internal PaymentIntent - possible webhook failure"                  │
│     },                                                                                  │
│     {                                                                                   │
│       "internalId": "PI-2024-005",                                                     │
│       "internalAmount": 300.00,                                                        │
│       "status": "UNMATCHED_INTERNAL",                                                  │
│       "notes": "Internal payment not in gateway settlement"                            │
│     }                                                                                   │
│   ],                                                                                    │
│   "createdAt": "2024-03-15T01:00:00Z",                                                 │
│   "completedAt": "2024-03-15T01:00:45Z"                                                │
│ }                                                                                       │
│                                                                                         │
│ ALERTS TRIGGERED (Gap 6):                                                               │
│ - Email sent to finance@pml.com: "Gateway Reconciliation - 3 discrepancies found"     │
│ - Slack notification to #finance channel                                               │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Investigation Actions for Discrepancies

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ DISCREPANCY INVESTIGATION GUIDE                                                         │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ AMOUNT_MISMATCH (PI-2024-003: K0.50 difference)                                        │
│ ───────────────────────────────────────────────                                         │
│ Possible Causes:                                                                        │
│ 1. Rounding error in fee calculation                                                   │
│ 2. Currency conversion variance                                                        │
│ 3. Manual adjustment at gateway                                                        │
│                                                                                         │
│ Investigation Steps:                                                                    │
│ 1. Check PaymentIntent for original amount vs. what was recorded                       │
│ 2. Review webhook payload for this transaction                                         │
│ 3. Contact PawaPay support if unexplained                                              │
│                                                                                         │
│ Resolution:                                                                             │
│ - If K0.50 is a genuine variance, create adjustment entry                              │
│ - Mark item as RESOLVED with explanation                                               │
│                                                                                         │
│ ─────────────────────────────────────────────────────────────────────────────────────── │
│                                                                                         │
│ MISSING_INTERNAL (PI-2024-004: K75.00 missing from our system)                         │
│ ──────────────────────────────────────────────────────────────                          │
│ Possible Causes:                                                                        │
│ 1. Webhook failed to deliver                                                           │
│ 2. Our system was down during webhook                                                  │
│ 3. Webhook processed but database write failed                                         │
│                                                                                         │
│ Investigation Steps:                                                                    │
│ 1. Check webhook logs for PI-2024-004                                                  │
│ 2. Request transaction details from PawaPay API                                        │
│ 3. Manually create PaymentIntent if transaction is valid                               │
│                                                                                         │
│ Resolution:                                                                             │
│ - Create PaymentIntent manually from gateway data                                      │
│ - Process ticket creation workflow                                                     │
│ - Mark item as RESOLVED                                                                │
│                                                                                         │
│ ─────────────────────────────────────────────────────────────────────────────────────── │
│                                                                                         │
│ UNMATCHED_INTERNAL (PI-2024-005: K300.00 in our system but not at gateway)             │
│ ─────────────────────────────────────────────────────────────────────────                │
│ Possible Causes:                                                                        │
│ 1. Transaction failed at gateway but we recorded success                               │
│ 2. Transaction was refunded/reversed at gateway                                        │
│ 3. Our system created duplicate record                                                 │
│ 4. Timing issue - transaction will appear in tomorrow's settlement                     │
│                                                                                         │
│ Investigation Steps:                                                                    │
│ 1. Check PaymentIntent providerStatus - was it actually COMPLETED?                     │
│ 2. Query PawaPay API for this transaction                                              │
│ 3. Check if this was processed after settlement cutoff time                            │
│                                                                                         │
│ Resolution:                                                                             │
│ - If timing issue: Wait for next day's settlement                                      │
│ - If false positive: Reverse the PaymentIntent and ticket                              │
│ - Mark item as RESOLVED with explanation                                               │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 13: Escrow Reconciliation

### Scenario
Daily escrow reconciliation to verify virtual escrow balances match the actual escrow bank account.

### Trigger
```
Scheduled: ReconciliationScheduler.runEscrowReconciliation() at 4:00 AM CAT
```

### Step-by-Step Flow

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ESCROW RECONCILIATION PROCESS                                                           │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ GOAL: Verify that SUM(all event escrow virtual balances) = Escrow Bank Account balance │
│                                                                                         │
│ INPUT:                                                                                  │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Virtual Escrow Accounts (event_escrow_accounts)                                     ││
│ │ ─────────────────────────────────────────────────                                   ││
│ │ Account Code     │ Event              │ Organizer    │ Balance                      ││
│ │ 2010-EVT00001    │ Lusaka Jazz Night  │ ORG001       │ K45,000.00                   ││
│ │ 2010-EVT00002    │ Rock Festival      │ ORG001       │ K120,000.00                  ││
│ │ 2010-EVT00003    │ Comedy Show        │ ORG002       │ K8,500.00                    ││
│ │ 2010-EVT00004    │ Food Fair          │ ORG003       │ K33,000.00                   ││
│ │ ... (496 more)   │ ...                │ ...          │ ...                          ││
│ │ ────────────────────────────────────────────────────────────────────────────────    ││
│ │                                       │ VIRTUAL TOTAL│ K850,000.00                  ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ Escrow Bank Account (1012) - From Bank Statement                                    ││
│ │ ─────────────────────────────────────────────────                                   ││
│ │ Stanbic Bank Trust Account ending X02                                               ││
│ │ Statement Date: 2024-03-15                                                          ││
│ │ Closing Balance: K850,250.00                                                        ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ VARIANCE: K250.00 (Bank has K250 more than virtual accounts show)                      │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ VARIANCE INVESTIGATION                                                                  │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Possible Causes for K250 Variance:                                                      │
│                                                                                         │
│ 1. BANK INTEREST EARNED                                                                 │
│    - Escrow trust account may earn interest                                            │
│    - K250 could be monthly interest credit                                             │
│    - Action: Credit to 4099 (Other Income) or distribute to escrow accounts           │
│                                                                                         │
│ 2. UNRECORDED TRANSFER                                                                  │
│    - Settlement transfer recorded at bank but not in our system                        │
│    - Action: Investigate pending settlements, record missing journal entry             │
│                                                                                         │
│ 3. TIMING DIFFERENCE                                                                    │
│    - Transaction in transit at statement cutoff                                        │
│    - Action: Usually resolves next day, mark as timing difference                      │
│                                                                                         │
│ 4. MANUAL BANK ADJUSTMENT                                                               │
│    - Bank made correction or fee reversal                                              │
│    - Action: Obtain bank advice, record appropriate entry                              │
│                                                                                         │
│ RESOLUTION: Create adjustment entry for K250 interest income                           │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ADJUSTMENT JOURNAL ENTRY (Gap 8)                                                        │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Journal Entry:                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ JE-2024-03-00200 | 2024-03-15 | Escrow Reconciliation Adjustment                   ││
│ ├──────────────────┬─────────────────────────────────────┬───────────┬────────────────┤│
│ │ Account Code     │ Account Name                        │ Debit     │ Credit         ││
│ ├──────────────────┼─────────────────────────────────────┼───────────┼────────────────┤│
│ │ 1012             │ Escrow Bank Account                 │ K250.00   │                ││
│ │ 4099             │ Reconciliation Variance Income      │           │ K250.00        ││
│ ├──────────────────┴─────────────────────────────────────┼───────────┼────────────────┤│
│ │                                             TOTALS     │ K250.00   │ K250.00     ✓  ││
│ └────────────────────────────────────────────────────────┴───────────┴────────────────┘│
│                                                                                         │
│ Note: For unfavorable variance (bank < virtual), would be:                             │
│   DR 5099 Reconciliation Variance Expense                                              │
│   CR 1012 Escrow Bank Account                                                          │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ RECONCILIATION RUN RECORD                                                               │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ Document Created (reconciliation_runs):                                                │
│ {                                                                                       │
│   "_id": "RECON-2024-03-15-ESC",                                                       │
│   "type": "ESCROW",                                                                    │
│   "reconciliationDate": "2024-03-15",                                                  │
│   "status": "COMPLETED",                    // After adjustment                        │
│   "expectedTotal": 850000.00,               // Sum of virtual accounts                 │
│   "actualTotal": 850250.00,                 // Bank statement balance                  │
│   "variance": 250.00,                                                                  │
│   "varianceExplanation": "Bank interest income",                                       │
│   "adjustmentEntryId": "JE-2024-03-00200",                                             │
│   "createdAt": "2024-03-15T02:00:00Z",                                                 │
│   "completedAt": "2024-03-15T02:05:00Z"                                                │
│ }                                                                                       │
│                                                                                         │
│ POST-RECONCILIATION BALANCE CHECK:                                                      │
│ Virtual Total:    K850,000.00                                                          │
│ Bank Balance:     K850,250.00                                                          │
│ Difference:       K0.00 (after adjustment recorded in 4099)                            │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Validation Checklist Summary

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ FINANCIAL FLOW VALIDATION CHECKLIST                                                     │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ [ ] Flow 1: Ticket Purchase                                                             │
│     [ ] PaymentIntent created with correct breakdown                                   │
│     [ ] Ticket created after payment confirmed                                         │
│     [ ] Commission created with PENDING status                                         │
│     [ ] Escrow account credited with net amount                                        │
│     [ ] Journal entry balanced (DR 1021 = CR 2010-XXX + CR 2031)                       │
│                                                                                         │
│ [ ] Flow 2: Gateway Settlement                                                          │
│     [ ] Settlement file parsed correctly                                               │
│     [ ] Transactions matched against internal records                                  │
│     [ ] Discrepancies flagged appropriately                                            │
│     [ ] Journal entry: DR 1011 + DR 5010 = CR 1021                                     │
│     [ ] 1021 balance reduced after settlement                                          │
│                                                                                         │
│ [ ] Flow 3-4: Payout Request & Disbursement                                             │
│     [ ] Payout request created with correct fee calculation                            │
│     [ ] Approval moves funds from escrow to payable                                    │
│     [ ] Disbursement clears payable, reduces bank balance                              │
│     [ ] Escrow balance correctly reduced                                               │
│                                                                                         │
│ [ ] Flow 5: Customer Refund                                                             │
│     [ ] Refund reduces escrow                                                          │
│     [ ] Commission reversed (if applicable)                                            │
│     [ ] Refund payable created and cleared on disbursement                             │
│                                                                                         │
│ [ ] Flow 6-8: Chargeback Recovery                                                       │
│     [ ] Recovery waterfall executes in correct order                                   │
│     [ ] Escrow recovery creates correct journal entries                                │
│     [ ] Future payout recovery reduces pending payout amounts                          │
│     [ ] Platform reserve used when escrow/payouts exhausted                            │
│     [ ] Write-off creates bad debt expense entry                                       │
│                                                                                         │
│ [ ] Flow 9: Commission Recognition                                                      │
│     [ ] Deferred commission moves to earned on event completion                        │
│     [ ] Journal entry: DR 2031, CR 4010                                                │
│     [ ] Commission status changed to EARNED                                            │
│                                                                                         │
│ [ ] Flow 11-13: Reconciliation                                                          │
│     [ ] Gateway reconciliation matches transactions correctly                          │
│     [ ] Bank reconciliation handles timing differences                                 │
│     [ ] Escrow reconciliation verifies virtual = physical                              │
│     [ ] Adjustment entries created for valid variances                                 │
│     [ ] Alerts triggered for significant discrepancies                                 │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Test Data for Validation

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ SAMPLE TEST DATA FOR VALIDATION                                                         │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ EVENT:                                                                                  │
│ {                                                                                       │
│   "id": "EVT-TEST-001",                                                                │
│   "title": "Test Jazz Night",                                                          │
│   "organizerId": "ORG-TEST-001"                                                        │
│ }                                                                                       │
│                                                                                         │
│ TICKET PURCHASE:                                                                        │
│ {                                                                                       │
│   "ticketPrice": 500.00,                                                               │
│   "commissionRate": 0.10,                                                              │
│   "expectedCommission": 50.00,                                                         │
│   "expectedEscrow": 450.00                                                             │
│ }                                                                                       │
│                                                                                         │
│ GATEWAY SETTLEMENT:                                                                     │
│ {                                                                                       │
│   "grossAmount": 500.00,                                                               │
│   "feeRate": 0.025,                                                                    │
│   "expectedFee": 12.50,                                                                │
│   "expectedNetSettlement": 487.50                                                      │
│ }                                                                                       │
│                                                                                         │
│ PAYOUT:                                                                                 │
│ {                                                                                       │
│   "requestedAmount": 450.00,                                                           │
│   "payoutFee": 50.00,                                                                  │
│   "expectedNetPayout": 400.00                                                          │
│ }                                                                                       │
│                                                                                         │
│ CHARGEBACK:                                                                             │
│ {                                                                                       │
│   "originalAmount": 500.00,                                                            │
│   "chargebackFee": 25.00,                                                              │
│   "totalRecoveryNeeded": 525.00                                                        │
│ }                                                                                       │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-03-15 | System | Initial creation |
| 1.1 | 2024-04-10 | System | Added Gap 1 (Future Payouts Recovery) flow |
| 1.2 | 2024-04-19 | System | Added complete reconciliation flows |

