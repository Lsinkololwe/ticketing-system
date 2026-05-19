# PML Event Ticketing Platform - Financial Architecture Documentation

## Table of Contents
1. [Overview](#overview)
2. [Virtual vs Real Accounts](#virtual-vs-real-accounts)
3. [Where Money Actually Sits](#where-money-actually-sits)
4. [Chart of Accounts - Detailed Commentary](#chart-of-accounts---detailed-commentary)
5. [Account Categories Explained](#account-categories-explained)
6. [Money Flow Examples](#money-flow-examples)
7. [Reconciliation Architecture](#reconciliation-architecture)
8. [Key Concepts](#key-concepts)

---

## Overview

The PML Event Ticketing Platform uses a **double-entry bookkeeping system** to track all financial transactions. This system consists of:

1. **Chart of Accounts** - A hierarchical list of accounts used to categorize transactions
2. **Journal Entries** - Records of every financial transaction with balanced debits and credits
3. **Ledgers** - The aggregated view of all transactions per account

### The Critical Distinction

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                         │
│   VIRTUAL ACCOUNTING SYSTEM              vs           REAL BANK ACCOUNTS                │
│   ──────────────────────────                         ─────────────────────              │
│                                                                                         │
│   ┌─────────────────────────────┐                   ┌─────────────────────────────┐    │
│   │  MongoDB Collections        │                   │  Actual Bank Accounts       │    │
│   │                             │                   │                             │    │
│   │  • chart_of_accounts        │                   │  • Stanbic Bank ZMW         │    │
│   │  • journal_entries          │     TRACKS        │    (Operating Account)      │    │
│   │  • event_escrow_accounts    │ ◀──────────────▶  │                             │    │
│   │  • commissions              │    REAL MONEY     │  • Stanbic Bank ZMW         │    │
│   │  • payout_requests          │                   │    (Escrow Trust Account)   │    │
│   │                             │                   │                             │    │
│   │  These are RECORDS that     │                   │  These hold ACTUAL CASH     │    │
│   │  track where money is       │                   │                             │    │
│   └─────────────────────────────┘                   └─────────────────────────────┘    │
│                                                                                         │
│   The Chart of Accounts is a VIRTUAL LEDGER that provides detailed tracking            │
│   of money that physically sits in only 2-3 real bank accounts.                        │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Virtual vs Real Accounts

### What is a "Virtual Account"?

A virtual account is an **internal accounting record** that exists only in our database. It allows us to:
- Track money at a granular level (per event, per organizer)
- Maintain detailed audit trails
- Calculate balances without moving actual money between bank accounts

### What is a "Real Account"?

A real account corresponds to an **actual bank account** at a financial institution where physical (electronic) money resides.

### Classification Table

| Account Code | Account Name | Type | Where Money Actually Is |
|--------------|--------------|------|-------------------------|
| **1011** | Primary Operating Bank Account | **REAL** | Stanbic Bank - Operating Account |
| **1012** | Escrow Bank Account | **REAL** | Stanbic Bank - Trust/Escrow Account |
| **1021** | Gateway Settlement Receivable | VIRTUAL | Money is at PawaPay (not yet settled) |
| **1022** | Commission Receivable | VIRTUAL | Money is in bank, tracked separately |
| **1023** | Chargeback Recovery Receivable | VIRTUAL | Represents amount to recover |
| **2010-XXXX** | Event Escrow (per event) | VIRTUAL | Money is in Escrow Bank Account (1012) |
| **2021** | Organizer Payouts Payable | VIRTUAL | Money is in Operating Account (1011) |
| **2022** | Customer Refunds Payable | VIRTUAL | Money is in Operating Account (1011) |
| **2031** | Deferred Commission Revenue | VIRTUAL | Money is in Operating Account (1011) |
| **3020** | Platform Reserve | VIRTUAL | Money is in Operating Account (1011) |
| **4010** | Commission Revenue | VIRTUAL | Accounting entry only (P&L) |
| **5010** | Payment Gateway Fees | VIRTUAL | Accounting entry only (P&L) |

---

## Where Money Actually Sits

### Physical Money Location Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           PHYSICAL LOCATION OF FUNDS                                     │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐ │
│  │                              PAYMENT GATEWAY (PawaPay)                             │ │
│  │                                                                                    │ │
│  │   When customer pays K100:                                                        │ │
│  │   • Money moves: Customer's MoMo Wallet → PawaPay Collection Account              │ │
│  │   • PawaPay holds this money until T+1 settlement                                 │ │
│  │   • We record: DR 1021 Gateway Receivable (we're OWED this money)                 │ │
│  │                                                                                    │ │
│  │   Settlement balance: K12,500 (not yet in our bank)                               │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                            │                                                            │
│                            │ T+1 Settlement (minus 2.5% fees)                          │
│                            ▼                                                            │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐ │
│  │                         STANBIC BANK - OPERATING ACCOUNT                          │ │
│  │                         Account #: 0040-XXXX-XXXX-01                              │ │
│  │                                                                                    │ │
│  │   This account holds:                                                             │ │
│  │   ┌─────────────────────────────────────────────────────────────────────────────┐│ │
│  │   │ Virtual Allocation          │ Amount      │ Actual Location                 ││ │
│  │   ├─────────────────────────────┼─────────────┼─────────────────────────────────┤│ │
│  │   │ Platform Commission (earned)│ K50,000     │ HERE - available for withdrawal ││ │
│  │   │ Platform Reserve            │ K25,000     │ HERE - reserved for chargebacks ││ │
│  │   │ Pending Payouts             │ K15,000     │ HERE - waiting to be disbursed  ││ │
│  │   │ Pending Refunds             │ K2,000      │ HERE - waiting to be refunded   ││ │
│  │   ├─────────────────────────────┼─────────────┼─────────────────────────────────┤│ │
│  │   │ TOTAL in this account       │ K92,000     │ Single bank balance             ││ │
│  │   └─────────────────────────────────────────────────────────────────────────────┘│ │
│  │                                                                                    │ │
│  │   NOTE: The bank sees ONE balance (K92,000). Our virtual accounts break this      │ │
│  │   down into logical categories for internal tracking.                             │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐ │
│  │                         STANBIC BANK - ESCROW TRUST ACCOUNT                       │ │
│  │                         Account #: 0040-XXXX-XXXX-02                              │ │
│  │                                                                                    │ │
│  │   This account holds ONLY organizer escrow funds (legally segregated):            │ │
│  │   ┌─────────────────────────────────────────────────────────────────────────────┐│ │
│  │   │ Virtual Escrow Account      │ Amount      │ Actual Location                 ││ │
│  │   ├─────────────────────────────┼─────────────┼─────────────────────────────────┤│ │
│  │   │ 2010-EVT001 Jazz Night      │ K45,000     │ HERE - in this bank account     ││ │
│  │   │ 2010-EVT002 Rock Festival   │ K120,000    │ HERE - in this bank account     ││ │
│  │   │ 2010-EVT003 Comedy Show     │ K8,500      │ HERE - in this bank account     ││ │
│  │   │ 2010-EVT004 Food Fair       │ K33,000     │ HERE - in this bank account     ││ │
│  │   │ ... (500+ events)           │ ...         │ HERE - in this bank account     ││ │
│  │   ├─────────────────────────────┼─────────────┼─────────────────────────────────┤│ │
│  │   │ TOTAL in escrow account     │ K850,000    │ Single bank balance             ││ │
│  │   └─────────────────────────────────────────────────────────────────────────────┘│ │
│  │                                                                                    │ │
│  │   NOTE: The bank sees ONE balance (K850,000). Our system tracks per-event        │ │
│  │   balances virtually. This protects organizer funds from platform operations.     │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Why Use Virtual Accounts?

1. **Granular Tracking**: Track money per event, per organizer, per transaction
2. **No Bank Fees**: Moving money between real accounts incurs fees; virtual moves are free
3. **Speed**: Virtual transfers are instant; bank transfers take time
4. **Audit Trail**: Every virtual movement creates a journal entry
5. **Regulatory Compliance**: Escrow funds can be in a single trust account but tracked per beneficiary

---

## Chart of Accounts - Detailed Commentary

### Account Numbering Convention

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              ACCOUNT NUMBERING SYSTEM                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│   1000-1999 : ASSETS            Resources the company OWNS or is OWED                   │
│   2000-2999 : LIABILITIES       Amounts the company OWES to others                      │
│   3000-3999 : EQUITY            Owner's stake (capital + retained earnings)             │
│   4000-4999 : REVENUE           Income from operations                                  │
│   5000-5999 : EXPENSES          Costs incurred to generate revenue                      │
│                                                                                         │
│   Sub-account pattern: XXXX-YYYYYYYY                                                    │
│   Example: 2010-EVT00123 = Event Escrow for event ID EVT00123                          │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 1000 - ASSETS (What We Own or Are Owed)

**Normal Balance: DEBIT** (Assets increase with debits, decrease with credits)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 1000 - ASSETS                                                                           │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 1011 - Primary Operating Bank Account                                               ││
│ │ ═══════════════════════════════════════                                             ││
│ │                                                                                     ││
│ │ Type: REAL ACCOUNT                                                                  ││
│ │ Location: Stanbic Bank Zambia - Account ending in X01                               ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Receives settlement funds from PawaPay (after T+1)                              ││
│ │   • Disburses payouts to organizers                                                 ││
│ │   • Disburses refunds to customers                                                  ││
│ │   • Pays operational expenses                                                       ││
│ │                                                                                     ││
│ │ Contains (virtually allocated):                                                     ││
│ │   • Platform's earned revenue                                                       ││
│ │   • Funds waiting to be paid out                                                    ││
│ │   • Reserve funds                                                                   ││
│ │                                                                                     ││
│ │ Journal Entry Examples:                                                             ││
│ │   DR 1011  K12,187.50  ← Settlement received from PawaPay                           ││
│ │   CR 1011  K8,955.00   ← Payout sent to organizer                                   ││
│ │                                                                                     ││
│ │ Reconciliation: Monthly bank statement reconciliation required                      ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 1012 - Escrow Bank Account                                                          ││
│ │ ════════════════════════════                                                        ││
│ │                                                                                     ││
│ │ Type: REAL ACCOUNT                                                                  ││
│ │ Location: Stanbic Bank Zambia - Trust Account ending in X02                         ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Holds organizer funds in a segregated trust account                             ││
│ │   • Legally protected from platform's operational risks                             ││
│ │   • Required for regulatory compliance (holding customer funds)                     ││
│ │                                                                                     ││
│ │ Legal Requirement:                                                                  ││
│ │   In Zambia, platforms holding funds on behalf of third parties must               ││
│ │   maintain these in a separate trust account. This protects organizers             ││
│ │   if the platform faces financial difficulties.                                     ││
│ │                                                                                     ││
│ │ NOTE: While this is a REAL account, the individual event escrow accounts           ││
│ │ (2010-XXXX) are VIRTUAL sub-ledgers tracking each organizer's share.               ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 1021 - Gateway Settlement Receivable                                                ││
│ │ ════════════════════════════════════                                                ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: At PawaPay (not yet in our bank)                                    ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Tracks money collected by PawaPay that they OWE us                              ││
│ │   • Represents T+1 settlement timing difference                                     ││
│ │                                                                                     ││
│ │ Lifecycle:                                                                          ││
│ │   1. Customer pays K100 via MoMo                                                    ││
│ │   2. PawaPay collects the money                                                     ││
│ │   3. We record: DR 1021 K100 (we're owed this)                                      ││
│ │   4. Next day, PawaPay settles K97.50 (minus 2.5% fee)                              ││
│ │   5. We record: CR 1021 K100 (receivable cleared)                                   ││
│ │                 DR 1011 K97.50 (cash received)                                      ││
│ │                 DR 5010 K2.50 (gateway fee expense)                                 ││
│ │                                                                                     ││
│ │ Balance Meaning:                                                                    ││
│ │   • K50,000 balance = PawaPay owes us K50,000 from recent transactions              ││
│ │   • Should clear to near-zero after each settlement                                 ││
│ │   • Large balance after settlement = PROBLEM (reconciliation needed)                ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 1022 - Commission Receivable                                                        ││
│ │ ════════════════════════════                                                        ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: In Operating Account (1011) but not yet earned                      ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Tracks commission that is pending (not yet earned)                              ││
│ │   • Used in the two-stage commission recognition model                              ││
│ │                                                                                     ││
│ │ Two-Stage Commission Model:                                                         ││
│ │   Stage 1 (Ticket Sale): Commission is DEFERRED (2031)                              ││
│ │   Stage 2 (Event Completion): Commission is EARNED (4010)                           ││
│ │                                                                                     ││
│ │ Why Two Stages?                                                                     ││
│ │   • If event is cancelled, commission must be returned                              ││
│ │   • Revenue recognition principle: earn when service delivered                      ││
│ │   • The "service" is completed when the event happens                               ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 1023 - Chargeback Recovery Receivable                                               ││
│ │ ═════════════════════════════════════                                               ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Represents amount to be recovered (may be in escrow)                ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Tracks amounts we expect to recover from organizers due to chargebacks          ││
│ │   • Created when chargeback occurs, cleared when recovered                          ││
│ │   • Written off to 5040 (Bad Debt) if unrecoverable                                 ││
│ │                                                                                     ││
│ │ Lifecycle:                                                                          ││
│ │   1. Chargeback of K525 received                                                    ││
│ │   2. DR 1023 K525 (we're owed this from organizer)                                  ││
│ │   3. Recovery from escrow: CR 1023 K525, DR 2010-XXX K525                           ││
│ │   OR                                                                                ││
│ │   3. Write-off: CR 1023 K525, DR 5040 K525 (bad debt)                               ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2000 - LIABILITIES (What We Owe)

**Normal Balance: CREDIT** (Liabilities increase with credits, decrease with debits)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 2000 - LIABILITIES                                                                      │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2010 - Event Escrow (Master Account)                                                ││
│ │ ════════════════════════════════════                                                ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Parent/Control Account)                                      ││
│ │ Money Location: Escrow Bank Account (1012)                                          ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Parent account for all per-event escrow sub-accounts                            ││
│ │   • Balance should equal sum of all 2010-XXXX accounts                              ││
│ │   • Balance should also equal the Escrow Bank Account (1012) balance                ││
│ │                                                                                     ││
│ │ Sub-Account Structure:                                                              ││
│ │   2010-EVT001  Jazz Night Escrow         K45,000                                    ││
│ │   2010-EVT002  Rock Festival Escrow      K120,000                                   ││
│ │   2010-EVT003  Comedy Show Escrow        K8,500                                     ││
│ │   ─────────────────────────────────────────────────                                 ││
│ │   2010 TOTAL                             K173,500                                   ││
│ │                                                                                     ││
│ │ Why Sub-Accounts?                                                                   ││
│ │   • Each organizer can see ONLY their event's escrow balance                        ││
│ │   • Prevents one organizer's chargeback from affecting another                      ││
│ │   • Enables per-event financial reporting                                           ││
│ │   • Supports partial payouts (multiple payout requests per event)                   ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2010-XXXXX - Per-Event Escrow Accounts                                              ││
│ │ ═════════════════════════════════════                                               ││
│ │                                                                                     ││
│ │ Type: VIRTUAL SUB-ACCOUNT                                                           ││
│ │ Money Location: Escrow Bank Account (1012)                                          ││
│ │                                                                                     ││
│ │ Created: Automatically when first ticket sold for an event                          ││
│ │ Account Code: 2010-{first 8 chars of eventId}                                       ││
│ │                                                                                     ││
│ │ Lifecycle:                                                                          ││
│ │   1. Event created - no escrow account yet                                          ││
│ │   2. First ticket sold - account created, balance = net ticket price                ││
│ │   3. More tickets sold - balance increases                                          ││
│ │   4. Chargeback - balance decreases (recovery)                                      ││
│ │   5. Event completes - organizer can request payout                                 ││
│ │   6. Payout approved - balance decreases                                            ││
│ │   7. Final payout - balance = K0, account inactive                                  ││
│ │                                                                                     ││
│ │ Example: 2010-EVT00123                                                              ││
│ │   Event: "Lusaka Jazz Night 2024"                                                   ││
│ │   Organizer: Lusaka Events Ltd                                                      ││
│ │   Balance: K45,000                                                                  ││
│ │   Transactions:                                                                     ││
│ │     + K90   TKT-001  Ticket sale (net after 10% commission)                         ││
│ │     + K90   TKT-002  Ticket sale                                                    ││
│ │     + K450  TKT-003  VIP ticket sale                                                ││
│ │     - K525  CB-001   Chargeback recovery                                            ││
│ │     ... (thousands of transactions)                                                 ││
│ │     ──────────────────────────────────────                                          ││
│ │     = K45,000 current balance                                                       ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2021 - Organizer Payouts Payable                                                    ││
│ │ ════════════════════════════════                                                    ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Operating Bank Account (1011)                                       ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Tracks payouts that have been APPROVED but not yet SENT                         ││
│ │   • Intermediate state between escrow and actual disbursement                       ││
│ │                                                                                     ││
│ │ Why This Account Exists:                                                            ││
│ │   1. Organizer requests payout                                                      ││
│ │   2. Finance team reviews and approves                                              ││
│ │   3. At approval: DR 2010-XXX, CR 2021 (moved from escrow to payable)               ││
│ │   4. Processing may take hours/days (batch processing)                              ││
│ │   5. When actually sent: DR 2021, CR 1011 (paid from bank)                          ││
│ │                                                                                     ││
│ │ Balance Meaning:                                                                    ││
│ │   • K50,000 balance = We owe organizers K50,000 in approved payouts                 ││
│ │   • Should be relatively low (payouts processed quickly)                            ││
│ │   • High balance = payout processing backlog                                        ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2022 - Customer Refunds Payable                                                     ││
│ │ ═══════════════════════════════                                                     ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Operating Bank Account (1011)                                       ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Tracks refunds approved but not yet sent to customers                           ││
│ │   • Similar lifecycle to 2021 but for refunds                                       ││
│ │                                                                                     ││
│ │ Lifecycle:                                                                          ││
│ │   1. Customer requests refund                                                       ││
│ │   2. Approved: DR 2010-XXX (escrow), DR 2031 (commission), CR 2022 (payable)        ││
│ │   3. Sent to customer: DR 2022, CR 1011                                             ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2023 - Tax Withholding Payable                                                      ││
│ │ ══════════════════════════════                                                      ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Operating Bank Account (1011)                                       ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Tracks taxes withheld from organizer payouts                                    ││
│ │   • Zambia may require withholding tax on payments to individuals/companies         ││
│ │   • Accumulated here until remitted to ZRA (Zambia Revenue Authority)               ││
│ │                                                                                     ││
│ │ Example (if 15% withholding applies):                                               ││
│ │   Payout K10,000 gross                                                              ││
│ │   Withholding K1,500 (15%)                                                          ││
│ │   Net to organizer K8,500                                                           ││
│ │   DR 2021 K10,000, CR 1011 K8,500, CR 2023 K1,500                                   ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2024 - Gateway Fees Payable                                                         ││
│ │ ═══════════════════════════                                                         ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: N/A (fees deducted at settlement)                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Optional account for accruing gateway fees before settlement                    ││
│ │   • In practice, PawaPay deducts fees at settlement time                            ││
│ │   • May be used for fee estimation or accrual accounting                            ││
│ │                                                                                     ││
│ │ Note: Often unused as fees are recognized directly to 5010 at settlement.           ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 2031 - Deferred Commission Revenue                                                  ││
│ │ ══════════════════════════════════                                                  ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Operating Bank Account (1011)                                       ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Holds commission that is collected but NOT YET EARNED                           ││
│ │   • Critical for proper revenue recognition                                         ││
│ │                                                                                     ││
│ │ Accounting Principle (ASC 606 / IFRS 15):                                           ││
│ │   Revenue is recognized when the performance obligation is satisfied.               ││
│ │   For event ticketing:                                                              ││
│ │   • Obligation: Enable customer to attend the event                                 ││
│ │   • Satisfied: When the event occurs                                                ││
│ │   • Therefore: Commission is EARNED only after event completion                     ││
│ │                                                                                     ││
│ │ Lifecycle:                                                                          ││
│ │   1. Ticket sold for K100 (K10 commission at 10%)                                   ││
│ │   2. At sale: CR 2031 K10 (commission is DEFERRED)                                  ││
│ │   3. Event occurs successfully                                                      ││
│ │   4. After event: DR 2031 K10, CR 4010 K10 (commission is EARNED)                   ││
│ │                                                                                     ││
│ │ What if Event is Cancelled?                                                         ││
│ │   • Commission is never earned                                                      ││
│ │   • DR 2031 K10, CR 2022 K10 (move to refund payable)                               ││
│ │   • Organizer doesn't owe commission on cancelled events                            ││
│ │                                                                                     ││
│ │ Balance Meaning:                                                                    ││
│ │   • K100,000 balance = K100,000 in commission waiting for events to complete        ││
│ │   • Will convert to revenue (4010) as events occur                                  ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 3000 - EQUITY (Owner's Stake)

**Normal Balance: CREDIT** (Equity increases with credits, decreases with debits)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 3000 - EQUITY                                                                           │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 3010 - Retained Earnings                                                            ││
│ │ ════════════════════════                                                            ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Operating Bank Account (1011)                                       ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Accumulated profits that have not been distributed to owners                    ││
│ │   • Calculated: Revenue - Expenses = Net Income → Retained Earnings                 ││
│ │                                                                                     ││
│ │ Updated:                                                                            ││
│ │   • At period close (monthly/yearly)                                                ││
│ │   • Revenue accounts (4XXX) and Expense accounts (5XXX) are closed here             ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 3020 - Platform Reserve                                                             ││
│ │ ═══════════════════════                                                             ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT                                                               ││
│ │ Money Location: Operating Bank Account (1011)                                       ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Safety buffer for chargeback losses                                             ││
│ │   • Used as THIRD source in chargeback recovery waterfall                           ││
│ │   • Should be replenished from profits                                              ││
│ │                                                                                     ││
│ │ Target Balance:                                                                     ││
│ │   • Typically 2-5% of monthly transaction volume                                    ││
│ │   • Example: K1M monthly volume → K20,000-50,000 reserve                            ││
│ │                                                                                     ││
│ │ When Used:                                                                          ││
│ │   • Chargeback occurs                                                               ││
│ │   • Escrow insufficient                                                             ││
│ │   • Future payouts insufficient                                                     ││
│ │   • Platform reserve is tapped: DR 3020, CR 1011                                    ││
│ │                                                                                     ││
│ │ Alert:                                                                              ││
│ │   • If reserve drops below threshold, finance team is alerted                       ││
│ │   • Reserve should be replenished from retained earnings                            ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 4000 - REVENUE (Income)

**Normal Balance: CREDIT** (Revenue increases with credits, decreases with debits)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 4000 - REVENUE                                                                          │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 4010 - Commission Revenue                                                           ││
│ │ ═════════════════════════                                                           ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - this is a P&L account, not a balance sheet account            ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records EARNED commission revenue                                               ││
│ │   • Primary revenue stream for the platform                                         ││
│ │                                                                                     ││
│ │ When Recognized:                                                                    ││
│ │   • After event completes (Two-Stage Model)                                         ││
│ │   • DR 2031 (Deferred Commission) → CR 4010 (Commission Revenue)                    ││
│ │                                                                                     ││
│ │ Commission Rate:                                                                    ││
│ │   • Standard: 10% of ticket price                                                   ││
│ │   • May vary by organizer tier, event type, volume discounts                        ││
│ │                                                                                     ││
│ │ Example:                                                                            ││
│ │   Event "Jazz Night" completes with K100,000 in ticket sales                        ││
│ │   Commission earned: K10,000 (10%)                                                  ││
│ │   Entry: DR 2031 K10,000, CR 4010 K10,000                                           ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 4020 - Payout Processing Fee Revenue                                                ││
│ │ ════════════════════════════════════                                                ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records fee charged for processing payouts to organizers                        ││
│ │   • Covers: Mobile money fees, bank transfer fees, processing overhead              ││
│ │                                                                                     ││
│ │ Fee Structure:                                                                      ││
│ │   • Typically 0.5% of payout amount                                                 ││
│ │   • Or fixed fee (e.g., K10 per payout)                                             ││
│ │                                                                                     ││
│ │ When Recognized:                                                                    ││
│ │   • When payout is approved (not when disbursed)                                    ││
│ │   • DR 2010-XXX (Escrow), CR 2021 (Payable) + CR 4020 (Fee Revenue)                 ││
│ │                                                                                     ││
│ │ Example:                                                                            ││
│ │   Organizer requests K10,000 payout                                                 ││
│ │   Processing fee: K50 (0.5%)                                                        ││
│ │   Net to organizer: K9,950                                                          ││
│ │   Entry: DR 2010 K10,000, CR 2021 K9,950, CR 4020 K50                               ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 4099 - Reconciliation Variance Income                                               ││
│ │ ═════════════════════════════════════                                               ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records unexpected GAINS from reconciliation                                    ││
│ │   • Rare - indicates we received more than expected                                 ││
│ │                                                                                     ││
│ │ Examples:                                                                           ││
│ │   • Gateway overpaid settlement (rare)                                              ││
│ │   • Currency conversion worked in our favor                                         ││
│ │   • Bank interest credited                                                          ││
│ │                                                                                     ││
│ │ Entry:                                                                              ││
│ │   DR 1011 K50 (extra cash), CR 4099 K50 (variance income)                           ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5000 - EXPENSES (Costs)

**Normal Balance: DEBIT** (Expenses increase with debits, decrease with credits)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ 5000 - EXPENSES                                                                         │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 5010 - Payment Gateway Fees                                                         ││
│ │ ═══════════════════════════                                                         ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records fees paid to PawaPay for processing transactions                        ││
│ │   • Typically 2-3% of transaction amount                                            ││
│ │                                                                                     ││
│ │ When Recognized:                                                                    ││
│ │   • At settlement time (when fees are deducted)                                     ││
│ │   • DR 5010 (fee expense), DR 1011 (net cash), CR 1021 (clear receivable)           ││
│ │                                                                                     ││
│ │ Fee Structure (PawaPay Zambia):                                                     ││
│ │   MTN MoMo: 2.5%                                                                    ││
│ │   Airtel Money: 2.5%                                                                ││
│ │   Zamtel Kwacha: 2.5%                                                               ││
│ │                                                                                     ││
│ │ Example:                                                                            ││
│ │   Settlement of K10,000 in transactions                                             ││
│ │   Gateway fee: K250 (2.5%)                                                          ││
│ │   Net received: K9,750                                                              ││
│ │   Entry: DR 5010 K250, DR 1011 K9,750, CR 1021 K10,000                              ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 5020 - Chargeback Losses                                                            ││
│ │ ════════════════════════                                                            ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records the principal amount of chargebacks                                     ││
│ │   • Separate from chargeback fees (5030)                                            ││
│ │                                                                                     ││
│ │ When Recognized:                                                                    ││
│ │   • When chargeback is confirmed and loss is realized                               ││
│ │   • After recovery attempts (may be partially recovered)                            ││
│ │                                                                                     ││
│ │ Note:                                                                               ││
│ │   • If fully recovered from escrow → no entry to 5020                               ││
│ │   • Only unrecovered amounts become losses                                          ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 5030 - Chargeback Fees                                                              ││
│ │ ══════════════════════                                                              ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records the FEE charged by gateway per chargeback                               ││
│ │   • This is in addition to the chargeback amount                                    ││
│ │                                                                                     ││
│ │ Typical Fee:                                                                        ││
│ │   • K25-50 per chargeback (fixed)                                                   ││
│ │   • Charged regardless of chargeback outcome                                        ││
│ │                                                                                     ││
│ │ Recovery:                                                                           ││
│ │   • Platform typically recovers this from organizer                                 ││
│ │   • Included in chargeback recovery waterfall                                       ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 5040 - Bad Debt Expense                                                             ││
│ │ ═══════════════════════                                                             ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records amounts that are UNRECOVERABLE and written off                          ││
│ │   • Last step in chargeback recovery waterfall                                      ││
│ │                                                                                     ││
│ │ When Used:                                                                          ││
│ │   • Escrow: K0 (insufficient)                                                       ││
│ │   • Future Payouts: K0 (none pending)                                               ││
│ │   • Platform Reserve: K0 (depleted)                                                 ││
│ │   • Remaining amount → Bad Debt                                                     ││
│ │                                                                                     ││
│ │ Entry:                                                                              ││
│ │   DR 5040 K425 (bad debt), CR 1023 K425 (write off receivable)                      ││
│ │                                                                                     ││
│ │ Impact:                                                                             ││
│ │   • Direct hit to bottom line (reduces profit)                                      ││
│ │   • Should be minimized through proper escrow management                            ││
│ │   • High bad debt = need to adjust organizer policies                               ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐│
│ │ 5099 - Reconciliation Variance Expense                                              ││
│ │ ══════════════════════════════════════                                              ││
│ │                                                                                     ││
│ │ Type: VIRTUAL ACCOUNT (Profit & Loss)                                               ││
│ │ Money Location: N/A - P&L account                                                   ││
│ │                                                                                     ││
│ │ Purpose:                                                                            ││
│ │   • Records unexpected LOSSES from reconciliation                                   ││
│ │   • Counterpart to 4099 (variance income)                                           ││
│ │                                                                                     ││
│ │ Examples:                                                                           ││
│ │   • Gateway underpaid settlement                                                    ││
│ │   • Bank fees we didn't account for                                                 ││
│ │   • Currency conversion loss                                                        ││
│ │   • Rounding differences (small amounts)                                            ││
│ │                                                                                     ││
│ │ Entry:                                                                              ││
│ │   DR 5099 K15 (variance loss), CR 1021 K15 (reduce receivable)                      ││
│ │                                                                                     ││
│ │ Monitoring:                                                                         ││
│ │   • Should be near zero if systems are working correctly                            ││
│ │   • Regular variance = need to investigate root cause                               ││
│ └─────────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Money Flow Examples

### Example 1: Complete Ticket Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE TICKET LIFECYCLE - K100 TICKET                              │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│  T+0: TICKET PURCHASE                                                                   │
│  ════════════════════                                                                   │
│                                                                                         │
│  Customer pays K100 via MTN MoMo                                                        │
│                                                                                         │
│  PHYSICAL MONEY MOVEMENT:                                                               │
│  ┌──────────────────┐         ┌──────────────────┐                                     │
│  │ Customer's MoMo  │──K100──▶│ PawaPay Account  │                                     │
│  │    Wallet        │         │ (Collection)     │                                     │
│  └──────────────────┘         └──────────────────┘                                     │
│                                                                                         │
│  VIRTUAL ACCOUNTING:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ Journal Entry JE-001                                                           │    │
│  │ DR 1021 Gateway Settlement Receivable    K100.00  ← We're owed by PawaPay      │    │
│  │ CR 2010-EVT001 Event Escrow                K90.00  ← Owe organizer (90%)       │    │
│  │ CR 2031 Deferred Commission Revenue        K10.00  ← Commission (10%)          │    │
│  └────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                         │
│  T+1: GATEWAY SETTLEMENT                                                                │
│  ═══════════════════════                                                                │
│                                                                                         │
│  PawaPay settles to our bank (minus 2.5% fee)                                          │
│                                                                                         │
│  PHYSICAL MONEY MOVEMENT:                                                               │
│  ┌──────────────────┐         ┌──────────────────┐                                     │
│  │ PawaPay Account  │──K97.50▶│ Stanbic Bank     │                                     │
│  │                  │         │ Operating (1011) │                                     │
│  └──────────────────┘         └──────────────────┘                                     │
│                                                                                         │
│  VIRTUAL ACCOUNTING:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ Journal Entry JE-002                                                           │    │
│  │ DR 1011 Primary Operating Bank Account    K97.50  ← Cash received              │    │
│  │ DR 5010 Payment Gateway Fees               K2.50  ← Fee expense                │    │
│  │ CR 1021 Gateway Settlement Receivable    K100.00  ← Clear receivable           │    │
│  └────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                         │
│  T+7: EVENT COMPLETES - COMMISSION EARNED                                               │
│  ════════════════════════════════════════                                               │
│                                                                                         │
│  PHYSICAL MONEY MOVEMENT: NONE (money already in bank)                                  │
│                                                                                         │
│  VIRTUAL ACCOUNTING:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ Journal Entry JE-003                                                           │    │
│  │ DR 2031 Deferred Commission Revenue       K10.00  ← Release deferred           │    │
│  │ CR 4010 Commission Revenue                K10.00  ← Recognize as earned        │    │
│  └────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                         │
│  T+8: ORGANIZER PAYOUT APPROVED                                                         │
│  ══════════════════════════════                                                         │
│                                                                                         │
│  Organizer requests K90 payout (0.5% fee = K0.45)                                      │
│                                                                                         │
│  PHYSICAL MONEY MOVEMENT: NONE YET                                                      │
│                                                                                         │
│  VIRTUAL ACCOUNTING:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ Journal Entry JE-004                                                           │    │
│  │ DR 2010-EVT001 Event Escrow               K90.00  ← Release from escrow        │    │
│  │ CR 2021 Organizer Payouts Payable         K89.55  ← Net amount payable         │    │
│  │ CR 4020 Payout Processing Fee Revenue      K0.45  ← Fee revenue                │    │
│  └────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                         │
│  T+8: PAYOUT DISBURSED                                                                  │
│  ═════════════════════                                                                  │
│                                                                                         │
│  PHYSICAL MONEY MOVEMENT:                                                               │
│  ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐        │
│  │ Stanbic Bank     │──K89.55▶│ PawaPay Payout   │──K89.55▶│ Organizer's MoMo │        │
│  │ Operating (1011) │         │                  │         │ Wallet           │        │
│  └──────────────────┘         └──────────────────┘         └──────────────────┘        │
│                                                                                         │
│  VIRTUAL ACCOUNTING:                                                                    │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ Journal Entry JE-005                                                           │    │
│  │ DR 2021 Organizer Payouts Payable         K89.55  ← Clear payable              │    │
│  │ CR 1011 Primary Operating Bank Account    K89.55  ← Cash out                   │    │
│  └────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                         │
│  FINAL STATE:                                                                           │
│  ┌────────────────────────────────────────────────────────────────────────────────┐    │
│  │ Account                           │ Balance Change │ Final Balance             │    │
│  ├───────────────────────────────────┼────────────────┼───────────────────────────┤    │
│  │ 1011 Operating Bank               │ +K97.50-K89.55 │ +K7.95 (net)              │    │
│  │ 1021 Gateway Receivable           │ +K100-K100     │ K0.00                     │    │
│  │ 2010-EVT001 Escrow                │ +K90-K90       │ K0.00                     │    │
│  │ 2021 Payouts Payable              │ +K89.55-K89.55 │ K0.00                     │    │
│  │ 2031 Deferred Commission          │ +K10-K10       │ K0.00                     │    │
│  │ 4010 Commission Revenue           │ +K10           │ K10.00 (earned)           │    │
│  │ 4020 Payout Fee Revenue           │ +K0.45         │ K0.45 (earned)            │    │
│  │ 5010 Gateway Fees                 │ +K2.50         │ K2.50 (expense)           │    │
│  ├───────────────────────────────────┼────────────────┼───────────────────────────┤    │
│  │ NET PROFIT from this ticket       │                │ K10.45 - K2.50 = K7.95    │    │
│  └────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                         │
│  Where did the K100 go?                                                                 │
│  • K89.55 → Organizer (via MoMo payout)                                                │
│  • K2.50  → PawaPay (gateway fee, deducted from settlement)                            │
│  • K7.95  → Platform profit (K10 commission + K0.45 payout fee - K2.50 gateway fee)    │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Reconciliation Architecture

### Three-Way Reconciliation Model

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           RECONCILIATION ARCHITECTURE                                    │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                          1. GATEWAY RECONCILIATION                               │   │
│  │                          ─────────────────────────                               │   │
│  │                                                                                  │   │
│  │  Purpose: Verify gateway transactions match our records                          │   │
│  │                                                                                  │   │
│  │  ┌────────────────────┐         ┌────────────────────┐                          │   │
│  │  │ PawaPay Settlement │         │ Our PaymentIntent  │                          │   │
│  │  │ File (CSV/JSON)    │◀──────▶│ Records (MongoDB)  │                          │   │
│  │  │                    │ COMPARE │                    │                          │   │
│  │  │ TXN-001: K500      │         │ PI-001: K500       │                          │   │
│  │  │ TXN-002: K250      │         │ PI-002: K250       │                          │   │
│  │  │ TXN-003: K100      │         │ PI-003: K100       │                          │   │
│  │  └────────────────────┘         └────────────────────┘                          │   │
│  │                                                                                  │   │
│  │  Discrepancy Types:                                                              │   │
│  │  • MATCHED          - Both sides agree                                           │   │
│  │  • AMOUNT_MISMATCH  - Same TXN, different amounts (investigate fees)             │   │
│  │  • UNMATCHED_EXTERNAL - Gateway has it, we don't (webhook failure!)              │   │
│  │  • UNMATCHED_INTERNAL - We have it, gateway doesn't (our bug)                    │   │
│  │                                                                                  │   │
│  │  Schedule: Daily at 3:00 AM CAT (after 2 AM settlement)                          │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                          2. BANK RECONCILIATION                                  │   │
│  │                          ──────────────────────                                  │   │
│  │                                                                                  │   │
│  │  Purpose: Verify bank statement matches our journal entries                      │   │
│  │                                                                                  │   │
│  │  ┌────────────────────┐         ┌────────────────────┐                          │   │
│  │  │ Bank Statement     │         │ Our JournalEntry   │                          │   │
│  │  │ (From Stanbic)     │◀──────▶│ Records (MongoDB)  │                          │   │
│  │  │                    │ COMPARE │                    │                          │   │
│  │  │ CR K97.50 Settlement         │ JE-002: DR 1011    │                          │   │
│  │  │ DR K89.55 Payout   │         │ JE-005: CR 1011    │                          │   │
│  │  │ CR K50.00 Interest │         │ ???                │ ← Missing entry!         │   │
│  │  └────────────────────┘         └────────────────────┘                          │   │
│  │                                                                                  │   │
│  │  Common Discrepancies:                                                           │   │
│  │  • Bank fees we didn't record                                                    │   │
│  │  • Interest income not recorded                                                  │   │
│  │  • Timing differences (T+1 settlements)                                          │   │
│  │                                                                                  │   │
│  │  Schedule: Weekly or Monthly                                                     │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│  │                          3. ESCROW RECONCILIATION                                │   │
│  │                          ────────────────────────                                │   │
│  │                                                                                  │   │
│  │  Purpose: Verify escrow account balances match transaction history               │   │
│  │                                                                                  │   │
│  │  ┌────────────────────┐         ┌────────────────────┐                          │   │
│  │  │ EventEscrowAccount │         │ Sum of EscrowTxns  │                          │   │
│  │  │ balance field      │◀──────▶│ for this account   │                          │   │
│  │  │                    │ COMPARE │                    │                          │   │
│  │  │ EVT-001: K45,000   │         │ +K90 -K525 +K90... │                          │   │
│  │  │                    │         │ = K45,000 ✓        │                          │   │
│  │  └────────────────────┘         └────────────────────┘                          │   │
│  │                                                                                  │   │
│  │  Also verifies:                                                                  │   │
│  │  • Sum of all escrow accounts = Escrow Bank Account balance (1012)              │   │
│  │  • No negative balances (shouldn't happen!)                                      │   │
│  │                                                                                  │   │
│  │  Schedule: Daily at 4:00 AM CAT                                                  │   │
│  └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Concepts

### The Accounting Equation

```
ASSETS = LIABILITIES + EQUITY

For PML Ticketing:
Bank Accounts + Receivables = Escrow Owed + Payouts Owed + Commission Owed + Retained Earnings
```

### Double-Entry Bookkeeping Rules

| Account Type | Increases With | Decreases With | Normal Balance |
|--------------|----------------|----------------|----------------|
| Assets (1XXX) | DEBIT | CREDIT | DEBIT |
| Liabilities (2XXX) | CREDIT | DEBIT | CREDIT |
| Equity (3XXX) | CREDIT | DEBIT | CREDIT |
| Revenue (4XXX) | CREDIT | DEBIT | CREDIT |
| Expenses (5XXX) | DEBIT | CREDIT | DEBIT |

### Virtual Account Best Practices

1. **Always reconcile** virtual accounts to real bank accounts
2. **Document** the mapping between virtual and real accounts
3. **Monitor** for discrepancies daily
4. **Audit** quarterly to ensure integrity
5. **Automate** reconciliation to catch issues early

### Real Account Requirements

1. **Segregation**: Keep escrow funds in separate trust account
2. **Access Control**: Limit who can authorize bank transfers
3. **Dual Approval**: Require two approvers for large payouts
4. **Bank Reconciliation**: Monthly at minimum, weekly preferred
5. **Audit Trail**: Every bank transaction must have a journal entry

---

## Glossary

| Term | Definition |
|------|------------|
| **Virtual Account** | An internal accounting record that tracks money logically but doesn't represent a separate bank account |
| **Real Account** | An actual bank account at a financial institution |
| **Escrow** | Funds held on behalf of a third party (organizers) until conditions are met |
| **Deferred Revenue** | Income received but not yet earned |
| **Settlement** | The transfer of funds from payment gateway to merchant bank account |
| **Receivable** | Money owed TO us |
| **Payable** | Money owed BY us |
| **Journal Entry** | A record of a financial transaction with balanced debits and credits |
| **Reconciliation** | The process of comparing two sets of records to ensure they match |
| **Write-Off** | Recording an amount as unrecoverable/lost |

---

## Accounting Service Touchpoints

This section documents all the code touchpoints that trigger accounting entries (debits/credits) in the system.

### Understanding IN/OUT (Debit/Credit)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           IN/OUT = DEBIT/CREDIT                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   Every account is like a bucket that can RECEIVE (IN) or GIVE (OUT).          │
│                                                                                 │
│   The accounting direction depends on the ACCOUNT TYPE:                         │
│                                                                                 │
│   ASSETS (1xxx) & EXPENSES (5xxx):                                              │
│   ┌─────────────────────────────────────┐                                       │
│   │ IN (Debit)           │ OUT (Credit) │  ← Normal Balance: DEBIT              │
│   │ Money/Value received │ Money/Value  │                                       │
│   │ (INCREASES balance)  │ given away   │                                       │
│   │                      │ (DECREASES)  │                                       │
│   └─────────────────────────────────────┘                                       │
│                                                                                 │
│   LIABILITIES (2xxx), EQUITY (3xxx), REVENUE (4xxx):                            │
│   ┌─────────────────────────────────────┐                                       │
│   │ OUT (Debit)          │ IN (Credit)  │  ← Normal Balance: CREDIT             │
│   │ Obligation fulfilled │ Obligation   │                                       │
│   │ (DECREASES balance)  │ created/     │                                       │
│   │                      │ revenue      │                                       │
│   │                      │ (INCREASES)  │                                       │
│   └─────────────────────────────────────┘                                       │
│                                                                                 │
│   GOLDEN RULE: Every journal entry must balance (Total Debits = Total Credits)  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### AccountingService Methods Summary

| Method | Trigger | IN (Debit) | OUT (Credit) |
|--------|---------|------------|--------------|
| `recordTicketSale` | PaymentEventListener | Gateway Receivable (1021) | Escrow (2010-XXX), Deferred Commission (2031), Gateway Fees Payable (2024) |
| `recordRefund` | RefundServiceImpl | Escrow (2010-XXX), Deferred Commission (2031) | Refunds Payable (2022) |
| `recordRefundDisbursement` | RefundServiceImpl | Refunds Payable (2022) | Bank Account (1011) |
| `recordPayout` | PayoutRequestServiceImpl | Escrow (2010-XXX) | Payouts Payable (2021), Fee Revenue (4020) |
| `recordPayoutDisbursement` | PayoutRequestServiceImpl | Payouts Payable (2021) | Bank Account (1011) |
| `recordCommissionEarned` | CommissionServiceImpl | Deferred Commission (2031) | Commission Revenue (4010) |
| `recordCommissionClawback` | CommissionServiceImpl | Commission/Deferred | Chargeback Receivable (1023) |
| `recordChargebackReceived` | ChargebackServiceImpl | Chargeback Receivable (1023), Chargeback Fees (5030) | Bank Account (1011) |
| `recordChargeback` | ChargebackServiceImpl | Escrow/Reserve/Bad Debt | Chargeback Receivable (1023) |
| `recordGatewaySettlement` | ReconciliationService | Bank Account (1011), Gateway Fees (5010) | Gateway Receivable (1021) |

### Complete Transaction Flows with IN/OUT

#### 1. Ticket Sale Flow (K100 ticket, 10% commission, K2 gateway fee)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        TICKET SALE: Customer pays K100                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Triggered by: PaymentEventListener.onPaymentCompleted()                         │
│  Calls: accountingService.recordTicketSale()                                     │
│                                                                                 │
│  Accounts Affected:                                                              │
│                                                                                 │
│  Gateway Receivable (1021) - ASSET                                               │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │ K100.00 ✓            │              │  ← Gateway owes us this money          │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Event Escrow (2010-XXX) - LIABILITY                                             │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │                      │ K88.00 ✓     │  ← We owe organizer this amount        │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Deferred Commission (2031) - LIABILITY                                          │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │                      │ K10.00 ✓     │  ← Our commission (not yet earned)     │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Gateway Fees Payable (2024) - LIABILITY                                         │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │                      │ K2.00 ✓      │  ← Gateway fee we'll pay at settlement │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Journal Entry:                                                                  │
│    DR Gateway Receivable (1021)       K100.00                                    │
│       CR Event Escrow (2010-XXX)              K88.00                             │
│       CR Deferred Commission (2031)           K10.00                             │
│       CR Gateway Fees Payable (2024)          K 2.00                             │
│                                            ─────────                             │
│    Total Debits: K100.00 = Total Credits: K100.00 ✓                              │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 2. Refund Flow (K100 refund)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        REFUND: K100 returned to customer                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  STEP 1: Record Refund (creates liability)                                       │
│  Triggered by: RefundServiceImpl.handleRefundCallback() on COMPLETED             │
│  Calls: accountingService.recordRefund()                                         │
│                                                                                 │
│  Event Escrow (2010-XXX) - LIABILITY                                             │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │ K88.00 ✓             │              │  ← Organizer's share reduced           │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Deferred Commission (2031) - LIABILITY                                          │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │ K10.00 ✓             │              │  ← Commission clawed back              │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Customer Refunds Payable (2022) - LIABILITY                                     │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │                      │ K100.00 ✓    │  ← We owe customer this money          │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  ─────────────────────────────────────────────────────────────────────────────  │
│                                                                                 │
│  STEP 2: Record Disbursement (clears liability)                                  │
│  Triggered by: RefundServiceImpl.handleRefundCallback() immediately after Step 1 │
│  Calls: accountingService.recordRefundDisbursement()                             │
│                                                                                 │
│  Customer Refunds Payable (2022) - LIABILITY                                     │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │ K100.00 ✓            │              │  ← Liability cleared (paid off)        │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Operating Bank Account (1011) - ASSET                                           │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │                      │ K100.00 ✓    │  ← Money left our bank                 │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 3. Chargeback Flow (K500 + K25 fee)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    CHARGEBACK: Gateway takes K525 back                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  STEP 1: Receive Chargeback (gateway takes money)                                │
│  Triggered by: ChargebackServiceImpl.receiveChargeback()                         │
│  Calls: accountingService.recordChargebackReceived()                             │
│                                                                                 │
│  Chargeback Recovery Receivable (1023) - ASSET                                   │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │ K500.00 ✓            │              │  ← We need to recover this             │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Chargeback Fees Expense (5030) - EXPENSE                                        │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │ K25.00 ✓             │              │  ← Gateway penalty (direct cost)       │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Operating Bank Account (1011) - ASSET                                           │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │                      │ K525.00 ✓    │  ← Gateway took this from bank         │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  ─────────────────────────────────────────────────────────────────────────────  │
│                                                                                 │
│  STEP 2: Recovery from Escrow (K300 available)                                   │
│  Triggered by: ChargebackServiceImpl.startRecovery() → attemptRecoveryFromEscrow │
│  Calls: accountingService.recordChargeback(source="ORGANIZER_ESCROW")            │
│                                                                                 │
│  Event Escrow (2010-XXX) - LIABILITY                                             │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │ K300.00 ✓            │              │  ← Organizer's escrow reduced          │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Chargeback Recovery Receivable (1023) - ASSET                                   │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │                      │ K300.00 ✓    │  ← Receivable partially cleared        │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  ─────────────────────────────────────────────────────────────────────────────  │
│                                                                                 │
│  STEP 3: Recovery from Future Payouts (K150 remaining)                           │
│  Triggered by: ChargebackServiceImpl.attemptRecoveryFromFuturePayouts()          │
│  Calls: accountingService.recordChargeback(source="ORGANIZER_FUTURE")            │
│                                                                                 │
│  Organizer Payouts Payable (2021) - LIABILITY                                    │
│  ┌─────────────────────────────────────┐                                        │
│  │ OUT (Debit)          │ IN (Credit)  │                                        │
│  │ K150.00 ✓            │              │  ← Pending payout reduced              │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  Chargeback Recovery Receivable (1023) - ASSET                                   │
│  ┌─────────────────────────────────────┐                                        │
│  │ IN (Debit)           │ OUT (Credit) │                                        │
│  │                      │ K150.00 ✓    │  ← Receivable fully cleared            │
│  └─────────────────────────────────────┘                                        │
│                                                                                 │
│  If recovery still short, continues to PLATFORM_RESERVE then WRITE_OFF          │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Code Touchpoints Reference

| File | Line | Method | AccountingService Call |
|------|------|--------|------------------------|
| `PaymentEventListener.java` | ~80 | `onPaymentCompleted()` | `recordTicketSale()` |
| `RefundServiceImpl.java` | ~207 | `handleRefundCallback()` | `recordRefund()`, `recordRefundDisbursement()` |
| `PayoutRequestServiceImpl.java` | ~261 | `approvePayout()` | `recordPayout()` |
| `PayoutRequestServiceImpl.java` | ~270 | `disbursePayout()` | `recordPayoutDisbursement()` |
| `CommissionServiceImpl.java` | ~109 | `earnCommission()` | `recordCommissionEarned()` |
| `CommissionServiceImpl.java` | ~179 | `clawbackCommission()` | `recordCommissionClawback()` |
| `ChargebackServiceImpl.java` | ~118 | `receiveChargeback()` | `recordChargebackReceived()` |
| `ChargebackServiceImpl.java` | ~383+ | `startRecovery()` | `recordChargeback()` |

### Account Code Quick Reference

```
1xxx - ASSETS (Normal Balance: DEBIT = IN increases)
├── 1011 - Operating Bank Account (REAL)
├── 1012 - Escrow Bank Account (REAL)
├── 1021 - Gateway Settlement Receivable
├── 1022 - Commission Receivable
└── 1023 - Chargeback Recovery Receivable

2xxx - LIABILITIES (Normal Balance: CREDIT = IN increases)
├── 2010-XXX - Event Escrow Accounts (per event)
├── 2021 - Organizer Payouts Payable
├── 2022 - Customer Refunds Payable
├── 2024 - Gateway Fees Payable
└── 2031 - Deferred Commission Revenue

3xxx - EQUITY (Normal Balance: CREDIT = IN increases)
├── 3010 - Retained Earnings
└── 3020 - Platform Reserve

4xxx - REVENUE (Normal Balance: CREDIT = IN increases)
├── 4010 - Commission Revenue
├── 4020 - Fee Revenue
└── 4099 - Other Income

5xxx - EXPENSES (Normal Balance: DEBIT = IN increases)
├── 5010 - Payment Gateway Fees
├── 5020 - Chargeback Loss
├── 5030 - Chargeback Fees
├── 5040 - Bad Debt Expense
└── 5099 - Other Expense
```

---

*Document Version: 2.0*
*Last Updated: 2026-04-20*
*Author: PML Platform Engineering Team*
