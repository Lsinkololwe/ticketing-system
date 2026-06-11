# Event Ticketing Platform - Complete Architecture Redesign V3

## World-Class Production Design with Financial Best Practices

**Version:** 3.1 (Two-Stage Commission Model)
**Date:** March 2026
**Stack:** Spring Boot 3.x, Spring WebFlux, Spring Modulith, MongoDB Replica Set, Azure Service Bus
**Microservices:** Catalog Service, Booking Service, Identity Service

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Industry Best Practices & Financial Model](#2-industry-best-practices--financial-model)
3. [Account Types & Fund Flow](#3-account-types--fund-flow)
4. [Complete User Journey](#4-complete-user-journey)
5. [Event Lifecycle Management](#5-event-lifecycle-management)
6. [Updated Architecture Overview](#6-updated-architecture-overview)
7. [MongoDB Collections (Aligned with Requirements)](#7-mongodb-collections-aligned-with-requirements)
8. [Payment Integration & Consistency](#8-payment-integration--consistency)
9. [Event Rescheduling & Cancellation](#9-event-rescheduling--cancellation)
10. [Payout & Settlement Rules](#10-payout--settlement-rules)
11. [Transaction Tracking & Terminology](#11-transaction-tracking--terminology)
12. [Implementation Phases](#12-implementation-phases)

---

## 1. Executive Summary

### What This Document Covers

This architecture redesign is based on your **actual USER_STORIES.md** requirements, covering:

| User Type | Key Capabilities |
|-----------|-----------------|
| **Ticket Buyer** | Browse events, purchase tickets, receive QR codes, request refunds, transfer tickets |
| **Event Organizer** | Create events, configure ticket categories, track sales, request payouts |
| **Admin** | Approve events, approve organizers, process refunds |
| **Scanner** | Validate tickets at event entry |
| **Finance Team** | Process payouts, view financial reports |

### What Was Removed (Not in Requirements)

| Feature | Status | Reason |
|---------|--------|--------|
| Seat selection/assignment | ❌ Removed | Not in USER_STORIES.md |
| Row/Section mapping | ❌ Removed | Not in USER_STORIES.md |
| Venue seating charts | ❌ Removed | Not in USER_STORIES.md |

### Your Three Microservices (Unchanged)

| Service | Port | Responsibility |
|---------|------|----------------|
| **Catalog Service** | 8081 | Events, Locations, Categories, Approval Workflow |
| **Booking Service** | 8082 | Tickets, Payments, QR Codes, Escrow |
| **Identity Service** | 8083 | Users, Organizers, Bank Accounts, Payouts, Notifications |

---

## 2. Industry Best Practices & Financial Model

### 2.1 How Ticketing Platforms Make Profit

Based on analysis of Eventbrite, Ticketmaster, and regional platforms:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    REVENUE STREAMS FOR TICKETING PLATFORMS                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  1. TRANSACTION FEES (Primary Revenue)                                          │
│  ═══════════════════════════════════                                            │
│  • Commission on each ticket sale (typically 3-10%)                             │
│  • Your model: 5% commission (from USER_STORIES.md)                             │
│                                                                                  │
│  Example: K300 ticket                                                            │
│  ├── Platform commission (5%): K15                                              │
│  └── Organizer receives: K285                                                   │
│                                                                                  │
│  2. PAYMENT PROCESSING FEES (Pass-through or absorbed)                          │
│  ═════════════════════════════════════════════════════                          │
│  • Mobile Money fees: 1-3% per transaction                                      │
│  • Options:                                                                      │
│    a) Pass to buyer (K300 + K9 fees = K309 total)                              │
│    b) Deduct from organizer (K300 - K15 comm - K9 fees = K276)                 │
│    c) Platform absorbs (reduces margin)                                         │
│                                                                                  │
│  3. PREMIUM FEATURES (Optional)                                                  │
│  ══════════════════════════════                                                  │
│  • Featured event placement                                                      │
│  • Priority support for organizers                                              │
│  • Advanced analytics                                                            │
│  • Marketing tools                                                               │
│                                                                                  │
│  4. INTEREST ON ESCROW (Secondary)                                              │
│  ═════════════════════════════════                                              │
│  • Funds held in escrow earn interest                                           │
│  • Especially significant for events months away                                │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Commission Structure Best Practices

```javascript
// Recommended tiered commission structure
const CommissionTiers = {
  STANDARD: {
    rate: 0.05,        // 5%
    description: "Standard events"
  },
  CHARITY: {
    rate: 0.02,        // 2% (reduced for charity)
    description: "Registered charity events"
  },
  HIGH_VOLUME: {
    rate: 0.03,        // 3% (volume discount)
    minTickets: 1000,
    description: "Events with 1000+ tickets"
  },
  PREMIUM_ORGANIZER: {
    rate: 0.04,        // 4% (loyalty discount)
    minEvents: 10,
    description: "Organizers with 10+ successful events"
  }
};
```

### 2.3 When Does the Organizer Get Their Money?

**Industry Standard: Escrow + Hold Period**

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         PAYOUT TIMELINE (Best Practice)                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  TICKET SALE                     EVENT DATE                    PAYOUT           │
│      │                               │                            │             │
│      ▼                               ▼                            ▼             │
│  ────┬───────────────────────────────┬────────────────────────────┬──────────   │
│      │                               │                            │             │
│      │       ESCROW PERIOD           │      HOLD PERIOD           │             │
│      │   (Funds held in escrow)      │   (7 days after event)     │             │
│      │                               │                            │             │
│      │  ┌─────────────────────────┐  │  ┌──────────────────────┐  │             │
│      │  │ • Funds accumulate      │  │  │ • Refund window      │  │             │
│      │  │ • Available for refunds │  │  │ • Dispute resolution │  │             │
│      │  │ • No payouts allowed    │  │  │ • No-show complaints │  │             │
│      │  └─────────────────────────┘  │  └──────────────────────┘  │             │
│      │                               │                            │             │
│                                                                                  │
│  WHY 7-DAY HOLD AFTER EVENT?                                                    │
│  ═══════════════════════════                                                    │
│  1. Customer complaints (event quality issues)                                   │
│  2. Chargeback window from payment providers                                     │
│  3. Fraud detection period                                                       │
│  4. Regulatory compliance in some jurisdictions                                  │
│                                                                                  │
│  PAYOUT OPTIONS:                                                                 │
│  ═══════════════                                                                │
│  a) AUTOMATIC: Payout 7 days after event (default)                              │
│  b) MANUAL: Organizer requests payout, finance team approves                    │
│  c) PARTIAL: Organizer can request partial early payout (with fee)              │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Account Types & Fund Flow

### 3.1 Required Account Types

Your system needs these account types (based on USER_STORIES.md US-FIN-001, US-FIN-002):

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           ACCOUNT HIERARCHY                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                      PLATFORM-LEVEL ACCOUNTS                               │  │
│  │  (Owned by your company - ONE instance of each)                           │  │
│  ├───────────────────────────────────────────────────────────────────────────┤  │
│  │                                                                            │  │
│  │  ┌─────────────────────────┐    ┌─────────────────────────┐               │  │
│  │  │  PENDING COMMISSION     │    │  EARNED REVENUE         │               │  │
│  │  │  ACCOUNT (Virtual)      │    │  ACCOUNT (Virtual)      │               │  │
│  │  ├─────────────────────────┤    ├─────────────────────────┤               │  │
│  │  │ Purpose:                │    │ Purpose:                │               │  │
│  │  │ • Hold commission until │    │ • Confirmed platform    │               │  │
│  │  │   event is completed    │    │   income (yours!)       │               │  │
│  │  │ • NOT yet earned!       │    │ • Event completed +     │               │  │
│  │  │                         │    │   7-day hold passed     │               │  │
│  │  │ Funded by:              │    │                         │               │  │
│  │  │ • 5% of each ticket     │    │ Funded by:              │               │  │
│  │  │   at purchase time      │    │ • Transfer from Pending │               │  │
│  │  │                         │    │   when commission earns │               │  │
│  │  │ On refund:              │    │                         │               │  │
│  │  │ • Simply cancelled      │    │ Linked to:              │               │  │
│  │  │   (no clawback needed!) │    │ • Company bank account  │               │  │
│  │  └─────────────────────────┘    └─────────────────────────┘               │  │
│  │                                                                            │  │
│  │  ┌─────────────────────────┐                                               │  │
│  │  │  PLATFORM OPERATIONS    │    TWO-STAGE COMMISSION MODEL:               │  │
│  │  │  ACCOUNT                │    ═══════════════════════════               │  │
│  │  ├─────────────────────────┤    Purchase → PENDING (not earned)           │  │
│  │  │ Purpose:                │    Event Complete + 7 days → EARNED          │  │
│  │  │ • Operating expenses    │    Refund before event → CANCELLED           │  │
│  │  │ • Payment provider fees │                                               │  │
│  │  │ • Refund reserves       │    WHY? Simpler refund handling!             │  │
│  │  │                         │    No "clawback" needed for pending          │  │
│  │  │ Funded by:              │    commission - just cancel it.              │  │
│  │  │ • Manual transfer from  │                                               │  │
│  │  │   earned revenue        │                                               │  │
│  │  └─────────────────────────┘                                               │  │
│  │                                                                            │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                      EVENT-LEVEL ACCOUNTS                                  │  │
│  │  (Created per event - ONE per published event)                            │  │
│  ├───────────────────────────────────────────────────────────────────────────┤  │
│  │                                                                            │  │
│  │  ┌─────────────────────────┐    ┌─────────────────────────┐               │  │
│  │  │  EVENT ESCROW ACCOUNT   │    │  EVENT ESCROW ACCOUNT   │               │  │
│  │  │  (Event: Music Festival)│    │  (Event: Comedy Night)  │               │  │
│  │  ├─────────────────────────┤    ├─────────────────────────┤               │  │
│  │  │ eventId: evt-001        │    │ eventId: evt-002        │               │  │
│  │  │ organizerId: org-123    │    │ organizerId: org-456    │               │  │
│  │  │ status: ACTIVE          │    │ status: PAYOUT_ELIGIBLE │               │  │
│  │  │ balance: K28,500        │    │ balance: K12,000        │               │  │
│  │  │ holdUntil: event+7days  │    │ payoutReady: true       │               │  │
│  │  └─────────────────────────┘    └─────────────────────────┘               │  │
│  │                                                                            │  │
│  │  Why per-event escrow?                                                     │  │
│  │  • Isolate funds for each event                                           │  │
│  │  • Easy tracking and reconciliation                                       │  │
│  │  • Support multiple organizers                                            │  │
│  │  • Clean handling of cancellations                                        │  │
│  │                                                                            │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                      EXTERNAL ACCOUNTS (References)                        │  │
│  │  (Linked bank accounts for payouts - stored in identity-service)          │  │
│  ├───────────────────────────────────────────────────────────────────────────┤  │
│  │                                                                            │  │
│  │  ┌─────────────────────────┐    ┌─────────────────────────┐               │  │
│  │  │  ORGANIZER BANK ACCOUNT │    │  PLATFORM BANK ACCOUNT  │               │  │
│  │  │  (External)             │    │  (External)             │               │  │
│  │  ├─────────────────────────┤    ├─────────────────────────┤               │  │
│  │  │ userId: org-123         │    │ type: PLATFORM_MAIN     │               │  │
│  │  │ bankName: Zanaco        │    │ bankName: Stanbic       │               │  │
│  │  │ accountNumber: ****1234 │    │ accountNumber: ****9999 │               │  │
│  │  │ verified: true          │    │ verified: true          │               │  │
│  │  └─────────────────────────┘    └─────────────────────────┘               │  │
│  │                                                                            │  │
│  │  Note: We don't HOLD money in these - we TRANSFER to them                 │  │
│  │                                                                            │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Fund Flow: From Buyer to Organizer

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              FUND FLOW DIAGRAM                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  STEP 1: TICKET PURCHASE (K300)                                                 │
│  ══════════════════════════════                                                 │
│                                                                                  │
│  Buyer's Phone ───────────────────────────────────────────────────────────────  │
│       │                                                                          │
│       │ MTN MoMo: K300                                                          │
│       ▼                                                                          │
│  ┌─────────────────────┐                                                        │
│  │ PAYMENT PROVIDER    │  (MTN/Airtel/Zamtel collects K300)                     │
│  │ Collection Account  │                                                        │
│  └─────────┬───────────┘                                                        │
│            │                                                                     │
│            │ Settlement (T+1 or T+2)                                            │
│            ▼                                                                     │
│  ┌─────────────────────┐                                                        │
│  │ PLATFORM SETTLEMENT │  (Payment provider settles to your bank)               │
│  │ Bank Account        │                                                        │
│  └─────────┬───────────┘                                                        │
│            │                                                                     │
│            │ Internal allocation (immediate in system)                          │
│            │                                                                     │
│      ┌─────┴─────┐                                                              │
│      │           │                                                               │
│      ▼           ▼                                                               │
│  ┌────────┐  ┌────────┐                                                         │
│  │ K15    │  │ K285   │                                                         │
│  │ (5%)   │  │ (95%)  │                                                         │
│  └───┬────┘  └───┬────┘                                                         │
│      │           │                                                               │
│      ▼           ▼                                                               │
│  ┌─────────────────────┐  ┌─────────────────────┐                               │
│  │ PENDING COMMISSION  │  │ EVENT ESCROW        │                               │
│  │ ACCOUNT             │  │ ACCOUNT             │                               │
│  │ +K15 (NOT earned!)  │  │ +K285               │                               │
│  └─────────┬───────────┘  └─────────────────────┘                               │
│            │                      │                                              │
│            │                      │ (After event + 7 days)                       │
│            │                      ▼                                              │
│            │               ┌─────────────────────┐                               │
│            │               │ PAYOUT REQUEST      │                               │
│            │               │ (Finance approves)  │                               │
│            │               └─────────┬───────────┘                               │
│            │                         │                                           │
│            │ (After event + 7 days)  │                                           │
│            ▼                         ▼                                           │
│  ┌─────────────────────┐  ┌─────────────────────┐                               │
│  │ EARNED REVENUE      │  │ ORGANIZER BANK      │                               │
│  │ ACCOUNT             │  │ ACCOUNT             │                               │
│  │ +K15 (NOW earned!)  │  │ (Zanaco: ****1234)  │                               │
│  │                     │  │ +K285               │                               │
│  └─────────────────────┘  └─────────────────────┘                               │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────────┐│
│  │ TWO-STAGE COMMISSION MODEL:                                                 ││
│  │ • At purchase: K15 goes to PENDING (not revenue yet!)                       ││
│  │ • After event + 7 days: K15 moves from PENDING → EARNED (now it's revenue!) ││
│  │ • On refund (before event): K15 simply cancelled from PENDING (no clawback!)││
│  └─────────────────────────────────────────────────────────────────────────────┘│
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 How to Link Real Bank Accounts

```javascript
// Collection: bank_accounts (in identity-service - ALREADY EXISTS)
// Your existing BankAccount.java model is correct!

// Key fields for bank account linking:
{
  "_id": "ba_uuid",
  "userId": "org-123",              // The organizer who owns this
  "bankName": "Zanaco",             // Bank name
  "bankCode": "ZNC",                // Bank code for transfers
  "accountNumber": "1234567890",    // Full account number (encrypted at rest)
  "accountHolderName": "John Doe",  // Must match bank records
  "branchCode": "001",              // Branch code
  "isPrimary": true,                // Default for payouts
  "isVerified": true,               // Has passed verification
  "verifiedAt": ISODate("..."),

  // Verification process:
  // 1. Organizer adds bank account
  // 2. System sends micro-deposit (K0.50)
  // 3. Organizer confirms amount received
  // 4. Account marked as verified
}
```

---

## 4. Complete User Journey

### 4.1 User Registration to First Ticket Purchase

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE USER JOURNEY: TICKET BUYER                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  PHASE 1: REGISTRATION                                                          │
│  ════════════════════                                                           │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 1. Download App │───▶│ 2. Enter Phone  │───▶│ 3. Verify OTP   │             │
│  │    (Mobile)     │    │    Number       │    │    (SMS)        │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│  ┌─────────────────┐    ┌─────────────────┐           │                        │
│  │ 5. Ready to     │◀───│ 4. Complete     │◀──────────┘                        │
│  │    Browse       │    │    Profile      │                                    │
│  └─────────────────┘    │    (Name,Email) │                                    │
│                         └─────────────────┘                                     │
│                                                                                  │
│  Service: Identity Service                                                       │
│  Events: USER_REGISTERED → USER_PROFILE_COMPLETED                               │
│                                                                                  │
│  PHASE 2: EVENT DISCOVERY                                                       │
│  ════════════════════════                                                       │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 6. Browse       │───▶│ 7. Filter by    │───▶│ 8. View Event   │             │
│  │    Events       │    │    Category/    │    │    Details      │             │
│  │                 │    │    Date/City    │    │                 │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│  Service: Catalog Service                              │                        │
│  Query: GET /events?status=PUBLISHED&category=...      │                        │
│                                                        │                        │
│  PHASE 3: TICKET SELECTION & PURCHASE                  │                        │
│  ════════════════════════════════════                  │                        │
│                                                        ▼                        │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 9. Select       │───▶│ 10. Choose      │───▶│ 11. Enter       │             │
│  │    Ticket       │    │     Quantity    │    │     Payment     │             │
│  │    Category     │    │     (1-10)      │    │     Method      │             │
│  │    (VIP/General)│    │                 │    │     (MTN/Airtel)│             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│                    ATOMIC OPERATION BEGINS             │                        │
│                    ════════════════════════            │                        │
│                                                        ▼                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ 12. BOOKING SERVICE TRANSACTION (MongoDB ACID)                          │   │
│  │     a) Check idempotencyKey (prevent duplicate)                         │   │
│  │     b) Reserve tickets (atomic findAndModify)                           │   │
│  │     c) Create PaymentIntent (PENDING)                                   │   │
│  │     d) Decrement available inventory                                    │   │
│  │     e) Publish TICKET_RESERVED event                                    │   │
│  │     f) Return payment URL/USSD code                                     │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                        │                        │
│                                                        ▼                        │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 13. User Opens  │───▶│ 14. User Enters │───▶│ 15. Payment     │             │
│  │     Payment URL │    │     PIN         │    │     Processed   │             │
│  │     or Dials    │    │                 │    │                 │             │
│  │     USSD        │    │                 │    │                 │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│                                                        │ Webhook callback       │
│                                                        ▼                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ 16. PAYMENT OUTCOME PROCESSING (Booking Service)                        │   │
│  │     a) Verify webhook signature                                         │   │
│  │     b) Check idempotency (processed_webhooks)                           │   │
│  │     c) If SUCCESS:                                                       │   │
│  │        - Update ticket status to PURCHASED                              │   │
│  │        - Credit escrow (K285)                                           │   │
│  │        - Credit PENDING commission (K15) ← NOT earned yet!              │   │
│  │        - Set ticket.commissionStatus = PENDING                          │   │
│  │        - Generate QR code                                               │   │
│  │        - Publish PAYMENT_COMPLETED event                                │   │
│  │     d) If FAILED:                                                        │   │
│  │        - Release reservation                                            │   │
│  │        - Restore inventory                                              │   │
│  │        - Publish PAYMENT_FAILED event                                   │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                        │                        │
│                                                        ▼                        │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 17. Receive SMS │───▶│ 18. View Ticket │───▶│ 19. Attend      │             │
│  │     Confirmation│    │     in App      │    │     Event       │             │
│  │     + Email     │    │     (QR Code)   │    │     (Scan QR)   │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Organizer Journey: Event Creation to Payout

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE USER JOURNEY: EVENT ORGANIZER                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  PHASE 1: ORGANIZER ONBOARDING                                                  │
│  ═════════════════════════════                                                  │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 1. Register as  │───▶│ 2. Apply to     │───▶│ 3. Submit       │             │
│  │    Regular User │    │    become       │    │    Business     │             │
│  │                 │    │    Organizer    │    │    Documents    │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│  ┌─────────────────┐    ┌─────────────────┐           │                        │
│  │ 5. Add Bank     │◀───│ 4. Admin        │◀──────────┘                        │
│  │    Account      │    │    Approves     │     (US-ADM-004)                   │
│  │    (for payouts)│    │    Application  │                                    │
│  └─────────────────┘    └─────────────────┘                                     │
│                                                                                  │
│  Service: Identity Service                                                       │
│  Status: User.role = ORGANIZER, Organization.status = APPROVED             │
│                                                                                  │
│  PHASE 2: EVENT CREATION                                                        │
│  ═══════════════════════                                                        │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 6. Create       │───▶│ 7. Add Event    │───▶│ 8. Configure    │             │
│  │    Draft Event  │    │    Details      │    │    Ticket       │             │
│  │    (US-ORG-001) │    │    (Title, Date,│    │    Categories   │             │
│  │                 │    │     Location)   │    │    (US-ORG-002) │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│  Event Status: DRAFT                                   │                        │
│  Service: Catalog Service                              │                        │
│                                                        │                        │
│  Ticket Categories Example:                            │                        │
│  ┌─────────────────────────────────────────────────────┼─────────────────────┐ │
│  │ Code      │ Name           │ Price  │ Quantity │ Benefits               │ │ │
│  │───────────│────────────────│────────│──────────│────────────────────────│ │ │
│  │ EARLY     │ Early Bird     │ K200   │ 100      │ First access           │ │ │
│  │ GENERAL   │ General        │ K300   │ 400      │ Standard admission     │ │ │
│  │ VIP       │ VIP            │ K500   │ 50       │ Front row, free drinks │ │ │
│  │ VVIP      │ VVIP           │ K1000  │ 20       │ Backstage access       │ │ │
│  └─────────────────────────────────────────────────────┴─────────────────────┘ │
│                                                        │                        │
│  PHASE 3: APPROVAL WORKFLOW                            │                        │
│  ══════════════════════════                            │                        │
│                                                        ▼                        │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 9. Submit for   │───▶│ 10. Admin       │───▶│ 11. Approved OR │             │
│  │    Approval     │    │     Reviews     │    │     Rejected    │             │
│  │    (US-ORG-003) │    │     (US-ADM-001)│    │     (US-ADM-002)│             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│  Event Status: DRAFT → PENDING_APPROVAL → APPROVED/REJECTED                    │
│                                                        │                        │
│                              If REJECTED ──────────────┼───▶ Revise and        │
│                                                        │     Resubmit          │
│                              If APPROVED               │                        │
│                                                        ▼                        │
│  PHASE 4: PUBLISHING & SALES                                                    │
│  ═══════════════════════════                                                    │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 12. Publish     │───▶│ 13. Escrow      │───▶│ 14. Event Goes  │             │
│  │     Event       │    │     Account     │    │     Live        │             │
│  │     (US-ORG-004)│    │     Created     │    │     (Ticket     │             │
│  │                 │    │     (Automatic) │    │      Sales      │             │
│  └─────────────────┘    └─────────────────┘    │      Begin)     │             │
│                                                └─────────────────┘             │
│  Event Status: APPROVED → PUBLISHED                                             │
│  Escrow Status: CREATED → ACTIVE (on first sale)                               │
│                                                        │                        │
│  ┌─────────────────┐                                   │                        │
│  │ 15. View Sales  │◀──────────────────────────────────┘                        │
│  │     Dashboard   │    (Real-time updates)                                     │
│  │     (US-ORG-006)│                                                            │
│  └─────────────────┘                                                            │
│                                                                                  │
│  PHASE 5: EVENT COMPLETION & PAYOUT                                             │
│  ══════════════════════════════════                                             │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 16. Event       │───▶│ 17. 7-Day Hold  │───▶│ 18. Request     │             │
│  │     Happens     │    │     Period      │    │     Payout      │             │
│  │                 │    │     (Escrow     │    │     (US-ORG-007)│             │
│  │                 │    │      LOCKED)    │    │                 │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                        │                        │
│  Event Status: PUBLISHED → COMPLETED                   │                        │
│  Escrow Status: ACTIVE → LOCKED → PAYOUT_ELIGIBLE      │                        │
│                                                        ▼                        │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐             │
│  │ 19. Finance     │───▶│ 20. Bank        │───▶│ 21. Funds in    │             │
│  │     Approves    │    │     Transfer    │    │     Bank        │             │
│  │     (US-FIN-001)│    │     Initiated   │    │     Account     │             │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘             │
│                                                                                  │
│  Escrow Status: PAYOUT_ELIGIBLE → PROCESSING → CLOSED                          │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Event Lifecycle Management

### 5.1 Event State Machine (From USER_STORIES.md)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         EVENT STATE MACHINE                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│       ┌──────────┐                                                               │
│       │  DRAFT   │ ◄──────────────────────────────────┐                         │
│       └────┬─────┘                                    │                         │
│            │ submit() (US-ORG-003)                    │ revise()                │
│            ▼                                          │                         │
│  ┌─────────────────────┐                              │                         │
│  │  PENDING_APPROVAL   │──────────────────────────────┤                         │
│  └─────────┬───────────┘                              │                         │
│            │                                          │                         │
│     ┌──────┴──────┐                            ┌──────┴──────┐                  │
│     │ approve()   │                            │  reject()   │                  │
│     │ (US-ADM-001)│                            │ (US-ADM-002)│                  │
│     ▼             │                            ▼             │                  │
│ ┌──────────┐      │                      ┌──────────┐        │                  │
│ │ APPROVED │      │                      │ REJECTED │────────┘                  │
│ └────┬─────┘      │                      └──────────┘                           │
│      │ publish()  │                                                              │
│      │(US-ORG-004)│                                                              │
│      ▼            │                                                              │
│ ┌──────────┐      │                                                              │
│ │PUBLISHED │◄─────┘                                                              │
│ └────┬─────┘                                                                     │
│      │                                                                           │
│      ├───────── eventDate passes ─────────► ┌───────────┐                       │
│      │                                      │ COMPLETED │                       │
│      │                                      └───────────┘                       │
│      │                                                                           │
│      ├───────── cancel() (US-ORG-005) ────► ┌───────────┐                       │
│      │                                      │ CANCELLED │                       │
│      │                                      └───────────┘                       │
│      │                                                                           │
│      └───────── reschedule() ─────────────► ┌───────────────┐                   │
│                                             │ RESCHEDULED   │                   │
│                                             │ (stays        │                   │
│                                             │  PUBLISHED    │                   │
│                                             │  with new     │                   │
│                                             │  date)        │                   │
│                                             └───────────────┘                   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Updated Architecture Overview

### 6.1 Architecture Aligned with Your Microservices

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    EVENT TICKETING SYSTEM - ARCHITECTURE V3                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌───────────────┐     ┌───────────────────────────────────────────────────┐   │
│  │  Mobile App   │────▶│                  API GATEWAY                       │   │
│  │  (Expo/RN)    │     │  • Rate Limiting                                   │   │
│  └───────────────┘     │  • JWT Validation (Keycloak)                       │   │
│                        │  • Request Routing                                  │   │
│  ┌───────────────┐     │                                                     │   │
│  │  Web App      │────▶│                                                     │   │
│  │  (Next.js)    │     └───────────────────────┬───────────────────────────┘   │
│  └───────────────┘                             │                                │
│                                                │                                │
│  ┌───────────────┐                             │                                │
│  │  Admin Panel  │─────────────────────────────┤                                │
│  │  (Next.js)    │                             │                                │
│  └───────────────┘                             │                                │
│                                                ▼                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                         YOUR 3 MICROSERVICES                              │  │
│  ├──────────────────────────────────────────────────────────────────────────┤  │
│  │                                                                           │  │
│  │  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐│  │
│  │  │   CATALOG SERVICE   │  │   BOOKING SERVICE   │  │  IDENTITY SERVICE   ││  │
│  │  │      (Port 8081)    │  │     (Port 8082)     │  │    (Port 8083)      ││  │
│  │  ├─────────────────────┤  ├─────────────────────┤  ├─────────────────────┤│  │
│  │  │ Modules:            │  │ Modules:            │  │ Modules:            ││  │
│  │  │ • Event Management  │  │ • Ticket Booking    │  │ • User Management   ││  │
│  │  │ • Location/Venue    │  │ • Payment Processing│  │ • Organizer Profiles││  │
│  │  │ • Category          │  │ • QR Code Generation│  │ • Bank Accounts     ││  │
│  │  │ • Approval Workflow │  │ • Escrow Management │  │ • Payout Processing ││  │
│  │  │                     │  │ • Refund Processing │  │ • Notifications     ││  │
│  │  ├─────────────────────┤  ├─────────────────────┤  ├─────────────────────┤│  │
│  │  │ Collections:        │  │ Collections:        │  │ Collections:        ││  │
│  │  │ • events            │  │ • tickets           │  │ • users             ││  │
│  │  │ • locations         │  │ • payment_intents   │  │ • organizer_profiles││  │
│  │  │ • event_categories  │  │ • escrow_accounts   │  │ • bank_accounts     ││  │
│  │  │ • cities/provinces  │  │ • financial_txns    │  │ • payout_requests   ││  │
│  │  │ • event_publications│  │ • refund_requests   │  │ • notifications     ││  │
│  │  │                     │  │ • processed_webhooks│  │ • event_publications││  │
│  │  │                     │  │ • event_publications│  │                     ││  │
│  │  └─────────────────────┘  └─────────────────────┘  └─────────────────────┘│  │
│  │                                                                           │  │
│  │                     SPRING MODULITH EVENT FLOW                            │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │  │ Internal Events: @ApplicationModuleListener                         │ │  │
│  │  │ • Within each service, events handled synchronously in transaction  │ │  │
│  │  │ • Guaranteed delivery via event_publications collection             │ │  │
│  │  └─────────────────────────────────────────────────────────────────────┘ │  │
│  │                                    │                                      │  │
│  │                                    ▼                                      │  │
│  │  ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │  │ External Events: Azure Service Bus                                  │ │  │
│  │  │ • Cross-service communication                                       │ │  │
│  │  │ • Topics: payment-events, ticket-events, user-events               │ │  │
│  │  │ • Each service subscribes to relevant topics                        │ │  │
│  │  └─────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                           │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                         AZURE SERVICE BUS                                 │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │  │
│  │  │ payment-events  │  │ ticket-events   │  │ user-events     │          │  │
│  │  │ (Topic)         │  │ (Topic)         │  │ (Topic)         │          │  │
│  │  │                 │  │                 │  │                 │          │  │
│  │  │ Subscribers:    │  │ Subscribers:    │  │ Subscribers:    │          │  │
│  │  │ • catalog-svc   │  │ • catalog-svc   │  │ • catalog-svc   │          │  │
│  │  │ • identity-svc  │  │ • identity-svc  │  │ • booking-svc   │          │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘          │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                     EXTERNAL PAYMENT PROVIDERS                            │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐          │  │
│  │  │   MTN MoMo      │  │  Airtel Money   │  │ Zamtel Kwacha   │          │  │
│  │  │                 │  │                 │  │                 │          │  │
│  │  │  Webhook ───────┼──┼─────────────────┼──┼───────▶ Booking │          │  │
│  │  │  Callback       │  │                 │  │         Service │          │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘          │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                      MONGODB REPLICA SET                                  │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐                         │  │
│  │  │  mongo1    │  │  mongo2    │  │  mongo3    │                         │  │
│  │  │ (PRIMARY)  │  │(SECONDARY) │  │(SECONDARY) │                         │  │
│  │  └────────────┘  └────────────┘  └────────────┘                         │  │
│  │                                                                           │  │
│  │  Databases:                                                               │  │
│  │  • ticketing_catalog (Catalog Service)                                   │  │
│  │  • ticketing_booking (Booking Service)                                   │  │
│  │  • ticketing_identity (Identity Service)                                 │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. MongoDB Collections (Aligned with Requirements)

### 7.1 Catalog Service Collections

```javascript
// ═══════════════════════════════════════════════════════════════════════════════
// CATALOG SERVICE DATABASE: ticketing_catalog
// ═══════════════════════════════════════════════════════════════════════════════

// Collection: events (ALREADY EXISTS - Your Event.java model is correct!)
// No changes needed - your model already has:
// - ticketCategories (embedded array) - correct for your requirements
// - NO seats/rows/sections - correct (not in requirements)
// - availableTickets, soldTickets - correct
// - status (EventStatus enum) - correct

// Collection: event_publications (Spring Modulith)
// Auto-created by Spring Modulith for guaranteed event delivery
{
  "_id": ObjectId("..."),
  "event": { /* serialized event */ },
  "listenerId": "com.pml.catalog.listeners.InventoryListener.onTicketPurchased",
  "publicationDate": ISODate("..."),
  "completionDate": null  // null until processed
}

// Indexes for event_publications
db.event_publications.createIndex({ "completionDate": 1 })
db.event_publications.createIndex({ "publicationDate": 1 })
```

### 7.2 Booking Service Collections

```javascript
// ═══════════════════════════════════════════════════════════════════════════════
// BOOKING SERVICE DATABASE: ticketing_booking
// ═══════════════════════════════════════════════════════════════════════════════

// Collection: tickets (UPDATE YOUR Ticket.java model)
// Changes needed:
// - Add @Version for optimistic locking
// - Add reservation fields for double-booking prevention
{
  "_id": "tkt-uuid",
  "ticketNumber": "TKT-ABCD1234",
  "eventId": "evt-uuid",
  "buyerId": "usr-uuid",

  // Event denormalized data (already in your model)
  "eventTitle": "Music Festival 2026",
  "eventDate": "2026-03-15T18:00:00Z",
  "eventLocationName": "Lusaka National Stadium",

  // Ticket category (already in your model)
  "ticketCategoryCode": "VIP",
  "ticketCategoryName": "VIP Access",
  "price": NumberDecimal("500.00"),
  "currency": "ZMW",

  // Status (use your existing TicketStatus enum)
  "status": "PENDING_PAYMENT",  // PENDING_PAYMENT | PURCHASED | VALIDATED | USED | CANCELLED | REFUNDED

  // Reservation tracking (ADD THIS)
  "reservation": {
    "reservedAt": ISODate("..."),
    "expiresAt": ISODate("..."),      // 10 minutes from reservation
    "sessionId": "session-uuid"
  },

  // Payment tracking (already in your model)
  "paymentInfo": {
    "paymentId": "pi-uuid",
    "transactionId": "TXN-xxx",
    "amount": NumberDecimal("500.00"),
    "status": "COMPLETED"
  },

  // Commission (UPDATE YOUR MODEL - Two-Stage Commission)
  "commissionRate": NumberDecimal("0.05"),
  "commissionAmount": NumberDecimal("25.00"),
  "netAmount": NumberDecimal("475.00"),
  "commissionStatus": "PENDING",  // PENDING | EARNED | CANCELLED | CLAWED_BACK
  "commissionEarnedAt": null,     // Set when status changes to EARNED

  // QR Code
  "qrCode": "data:image/png;base64,...",

  // Optimistic Locking (ADD THIS)
  "version": NumberLong(0),

  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}

// Collection: payment_intents (NEW - for payment processing)
{
  "_id": "pi-uuid",

  // Idempotency
  "idempotencyKey": "user123_evt456_1709123456789",
  "transactionRef": "TXN-20260228-ABCD1234",
  "providerTransactionId": "MTN-XYZ789",

  // References
  "ticketId": "tkt-uuid",
  "eventId": "evt-uuid",
  "userId": "usr-uuid",

  // Amount
  "amount": NumberDecimal("500.00"),
  "currency": "ZMW",

  // Provider
  "provider": "MTN_MOMO",  // MTN_MOMO | AIRTEL_MONEY | ZAMTEL_KWACHA
  "phoneNumber": "+260971234567",

  // Status
  "status": "PENDING",  // PENDING | PROCESSING | SUCCEEDED | FAILED | EXPIRED
  "failureReason": null,

  // Tracking
  "webhookAttempts": 0,
  "pollAttempts": 0,
  "lastPolledAt": null,

  // Timing
  "createdAt": ISODate("..."),
  "expiresAt": ISODate("..."),
  "processedAt": null,

  // Optimistic Locking
  "version": NumberLong(0)
}

// Indexes
db.payment_intents.createIndex({ "idempotencyKey": 1 }, { unique: true })
db.payment_intents.createIndex({ "transactionRef": 1 }, { unique: true })
db.payment_intents.createIndex({ "status": 1, "createdAt": 1 })  // For polling

// Collection: escrow_accounts (UPDATE YOUR EscrowAccount.java model)
// Changes needed:
// - Add eventId (per-event escrow)
// - Add organizerId
// - Add status enum
// - Add version for optimistic locking
{
  "_id": "esc-uuid",

  // Per-event escrow
  "eventId": "evt-uuid",
  "organizerId": "org-uuid",
  "accountNumber": "ESC-EVT001-2026",

  // Balances
  "currentBalance": NumberDecimal("28500.00"),
  "totalDeposits": NumberDecimal("30000.00"),
  "totalWithdrawals": NumberDecimal("1500.00"),  // Refunds

  // Status
  "status": "ACTIVE",  // CREATED | ACTIVE | LOCKED | PAYOUT_ELIGIBLE | PROCESSING | CLOSED
  "lockUntil": ISODate("..."),  // Event date + 7 days

  // Ledger (double-entry)
  "transactions": [
    {
      "id": "txn-uuid",
      "type": "CREDIT",
      "category": "TICKET_SALE",
      "amount": NumberDecimal("475.00"),
      "ticketId": "tkt-uuid",
      "paymentIntentId": "pi-uuid",
      "balanceAfter": NumberDecimal("28500.00"),
      "timestamp": ISODate("...")
    }
  ],

  // Optimistic Locking
  "version": NumberLong(0),

  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}

// Indexes
db.escrow_accounts.createIndex({ "eventId": 1 }, { unique: true })
db.escrow_accounts.createIndex({ "organizerId": 1 })
db.escrow_accounts.createIndex({ "status": 1 })

// Collection: processed_webhooks (idempotency for webhooks)
{
  "_id": "webhook_mtn_abc123",
  "provider": "MTN_MOMO",
  "transactionRef": "TXN-20260228-ABCD1234",
  "processedAt": ISODate("...")
}

// TTL Index - auto-delete after 30 days
db.processed_webhooks.createIndex({ "processedAt": 1 }, { expireAfterSeconds: 2592000 })

// Collection: event_publications (Spring Modulith)
// Same structure as catalog service
```

### 7.3 Identity Service Collections

```javascript
// ═══════════════════════════════════════════════════════════════════════════════
// IDENTITY SERVICE DATABASE: ticketing_identity
// ═══════════════════════════════════════════════════════════════════════════════

// Collection: users (ALREADY EXISTS - Your User.java model)
// No changes needed

// Collection: organizer_profiles (ALREADY EXISTS - Your Organization.java model)
// No changes needed

// Collection: bank_accounts (ALREADY EXISTS - Your BankAccount.java model is correct!)
// This is how you link real bank accounts for payouts

// Collection: payout_requests (ALREADY EXISTS - Your PayoutRequest.java model)
// No changes needed

// Collection: platform_accounts (NEW - Two-Stage Commission Model)
// You need TWO platform accounts: PENDING and EARNED

// Account 1: Pending Commission (NOT yet earned)
{
  "_id": "platform-pending-commission",
  "accountType": "PENDING_COMMISSION",
  "accountName": "Pending Commission (Not Yet Earned)",
  "currentBalance": NumberDecimal("5000.00"),   // Commission from active events
  "totalCredits": NumberDecimal("8000.00"),     // All commission credited
  "totalEarned": NumberDecimal("2500.00"),      // Moved to earned
  "totalCancelled": NumberDecimal("500.00"),    // Cancelled on refunds (before event)

  // Ledger
  "transactions": [
    {
      "id": "txn-uuid",
      "type": "CREDIT",
      "category": "COMMISSION_PENDING",
      "amount": NumberDecimal("25.00"),
      "ticketId": "tkt-uuid",
      "eventId": "evt-uuid",
      "balanceAfter": NumberDecimal("5000.00"),
      "timestamp": ISODate("...")
    },
    {
      "id": "txn-uuid2",
      "type": "DEBIT",
      "category": "COMMISSION_EARNED",     // Moved to earned account
      "amount": NumberDecimal("25.00"),
      "ticketId": "tkt-uuid",
      "eventId": "evt-uuid",
      "balanceAfter": NumberDecimal("4975.00"),
      "timestamp": ISODate("...")
    },
    {
      "id": "txn-uuid3",
      "type": "DEBIT",
      "category": "COMMISSION_CANCELLED",  // Refund before event - simply cancel
      "amount": NumberDecimal("25.00"),
      "ticketId": "tkt-uuid",
      "eventId": "evt-uuid",
      "refundId": "ref-uuid",
      "balanceAfter": NumberDecimal("4950.00"),
      "timestamp": ISODate("...")
    }
  ],

  "version": NumberLong(0),
  "updatedAt": ISODate("...")
}

// Account 2: Earned Revenue (THIS is real revenue!)
{
  "_id": "platform-earned-revenue",
  "accountType": "EARNED_REVENUE",
  "accountName": "Earned Platform Revenue",
  "currentBalance": NumberDecimal("150000.00"),
  "totalEarned": NumberDecimal("155000.00"),
  "totalClawedBack": NumberDecimal("5000.00"),  // Rare: refund AFTER event (disputes)

  // Link to company bank account (can withdraw from here)
  "linkedBankAccountId": "ba-platform-main",

  // Ledger
  "transactions": [
    {
      "id": "txn-uuid",
      "type": "CREDIT",
      "category": "COMMISSION_EARNED",   // From pending when event completes
      "amount": NumberDecimal("25.00"),
      "ticketId": "tkt-uuid",
      "eventId": "evt-uuid",
      "balanceAfter": NumberDecimal("150000.00"),
      "timestamp": ISODate("...")
    }
  ],

  "version": NumberLong(0),
  "updatedAt": ISODate("...")
}

// COMMISSION STATUS LIFECYCLE:
// ════════════════════════════
// 1. Ticket purchased → Credit PENDING account (commissionStatus = PENDING)
// 2. Event completes + 7 days → Move from PENDING to EARNED (commissionStatus = EARNED)
// 3. Refund before event → Cancel from PENDING (commissionStatus = CANCELLED) - NO clawback!
// 4. Refund after event (rare dispute) → Clawback from EARNED (commissionStatus = CLAWED_BACK)

// Collection: processed_events (consumer idempotency)
{
  "_id": "PaymentSucceeded_pi_123456",
  "eventType": "PaymentSucceededEvent",
  "processedAt": ISODate("...")
}

// TTL Index
db.processed_events.createIndex({ "processedAt": 1 }, { expireAfterSeconds: 604800 })
```

---

## 8. Payment Integration & Consistency

(Same as ARCHITECTURE_REDESIGN_V2.md - the payment flow design remains correct)

---

## 9. Event Rescheduling & Cancellation

### 9.1 Event Rescheduling (Best Practice)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      EVENT RESCHEDULING FLOW                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  SCENARIO: Event date changes from March 15 to April 5                          │
│                                                                                  │
│  STEP 1: ORGANIZER REQUESTS RESCHEDULE                                          │
│  ═══════════════════════════════════════                                        │
│  • New date must be in the future                                               │
│  • Reason required                                                               │
│                                                                                  │
│  STEP 2: SYSTEM PROCESSES RESCHEDULE                                            │
│  ════════════════════════════════════                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ CATALOG SERVICE                                                         │    │
│  │ 1. Update event date                                                    │    │
│  │ 2. Update escrow lockUntil (newDate + 7 days)                          │    │
│  │ 3. Publish EVENT_RESCHEDULED event                                      │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                    │                                            │
│                                    ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ IDENTITY SERVICE (via Azure Service Bus)                               │    │
│  │ 1. Send notification to ALL ticket holders                              │    │
│  │    - SMS: "Event rescheduled to April 5"                                │    │
│  │    - Email: Detailed info + refund option                               │    │
│  │    - Push: App notification                                             │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  STEP 3: TICKET HOLDERS CAN CHOOSE                                              │
│  ═════════════════════════════════                                              │
│  • Keep ticket (valid for new date)                                             │
│  • Request full refund (special policy for reschedule)                          │
│                                                                                  │
│  REFUND POLICY ON RESCHEDULE:                                                   │
│  • 100% refund available regardless of normal policy                            │
│  • Refund window: 7 days from reschedule notification                           │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Event Cancellation (From USER_STORIES.md US-ORG-005)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      EVENT CANCELLATION FLOW                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  TRIGGER: Organizer cancels event (US-ORG-005)                                  │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ CATALOG SERVICE                                                         │    │
│  │ 1. Update event status to CANCELLED                                     │    │
│  │ 2. Set cancellation reason                                               │    │
│  │ 3. Publish EVENT_CANCELLED event                                        │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                    │                                            │
│                                    ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ BOOKING SERVICE (via Azure Service Bus)                                 │    │
│  │ 1. Find all tickets for this event (PURCHASED, VALIDATED)               │    │
│  │ 2. FOR EACH ticket:                                                      │    │
│  │    a) Mark ticket as REFUND_PENDING                                     │    │
│  │    b) Create automatic RefundRequest                                    │    │
│  │    c) Calculate refund: 100% (full refund on cancellation)              │    │
│  │    d) Publish REFUND_INITIATED event                                    │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                    │                                            │
│                                    ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ FINANCIAL PROCESSING (Booking Service) - TWO-STAGE COMMISSION           │    │
│  │ For each ticket:                                                         │    │
│  │ 1. Debit escrow: K475 (net amount)                                       │    │
│  │ 2. Check ticket.commissionStatus:                                        │    │
│  │    IF PENDING (event hasn't completed):                                  │    │
│  │       - Cancel pending commission: K25 (simple debit from pending)       │    │
│  │       - Set ticket.commissionStatus = CANCELLED                          │    │
│  │       - NO clawback needed! Commission was never "earned"               │    │
│  │    IF EARNED (rare - dispute after event):                               │    │
│  │       - Clawback from earned revenue: K25                                │    │
│  │       - Set ticket.commissionStatus = CLAWED_BACK                        │    │
│  │ 3. Initiate refund: K500 (full amount to buyer)                         │    │
│  │ 4. Call payment provider refund API                                      │    │
│  │                                                                          │    │
│  │ Financial integrity: escrow_debit + commission_cancel = buyer_refund    │    │
│  │                      K475 + K25 = K500 ✓                                │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                    │                                            │
│                                    ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ IDENTITY SERVICE (Notifications)                                        │    │
│  │ Send to each ticket holder:                                              │    │
│  │ • SMS: "Event cancelled. Full refund processing."                        │    │
│  │ • Email: Detailed info + refund timeline                                │    │
│  │ • Push: App notification                                                 │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  FINAL STATE:                                                                    │
│  • Event: CANCELLED                                                             │
│  • Escrow: CLOSED (balance = 0)                                                 │
│  • All tickets: REFUNDED                                                        │
│  • Platform revenue: Reduced by total commission                                │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Payout & Settlement Rules

### 10.1 Payout Eligibility Rules

```javascript
const PayoutEligibilityRules = {
  // Rule 1: Event must be completed
  eventCompleted: (event) => event.status === 'COMPLETED',

  // Rule 2: Hold period must have passed
  holdPeriodPassed: (event) => {
    const holdDays = 7;
    const releaseDate = addDays(event.endDateTime, holdDays);
    return new Date() >= releaseDate;
  },

  // Rule 3: No pending disputes
  noPendingDisputes: (escrow) => escrow.pendingDisputeCount === 0,

  // Rule 4: Minimum balance check
  hasBalance: (escrow) => escrow.currentBalance > 0,

  // Combined check
  isPayoutEligible: (event, escrow) => {
    return PayoutEligibilityRules.eventCompleted(event)
        && PayoutEligibilityRules.holdPeriodPassed(event)
        && PayoutEligibilityRules.noPendingDisputes(escrow)
        && PayoutEligibilityRules.hasBalance(escrow);
  }
};
```

### 10.2 Payout Flow (From USER_STORIES.md US-ORG-007, US-FIN-001)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           PAYOUT FLOW                                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  STEP 1: ORGANIZER REQUESTS PAYOUT (US-ORG-007)                                 │
│  ════════════════════════════════════════════                                    │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ PRE-CONDITIONS:                                                         │    │
│  │ • Event status = COMPLETED                                              │    │
│  │ • Current date >= Event date + 7 days                                   │    │
│  │ • Escrow status = PAYOUT_ELIGIBLE                                       │    │
│  │ • Bank account verified = true                                          │    │
│  │ • Escrow balance > 0                                                    │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  Organizer submits request in app:                                              │
│  • Select event                                                                  │
│  • Select bank account (from verified accounts)                                 │
│  • Confirm amount (escrow balance)                                              │
│                                                                                  │
│  STEP 2: FINANCE TEAM REVIEW (US-FIN-001)                                       │
│  ═════════════════════════════════════════                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ FINANCE DASHBOARD:                                                      │    │
│  │ • View pending payout requests                                          │    │
│  │ • Verify organizer identity                                             │    │
│  │ • Verify bank account details                                           │    │
│  │ • Check for any flags/disputes                                          │    │
│  │ • Approve or Reject with reason                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  STEP 3: BANK TRANSFER INITIATION                                               │
│  ════════════════════════════════                                                │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ BOOKING SERVICE:                                                        │    │
│  │ 1. Debit escrow account (full balance)                                  │    │
│  │ 2. Record financial transaction                                         │    │
│  │ 3. Initiate bank transfer via payment provider                          │    │
│  │    (This could be manual or automated depending on your bank setup)     │    │
│  │ 4. Set PayoutRequest status = PROCESSING                                │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  STEP 4: BANK CONFIRMATION                                                      │
│  ═════════════════════════                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ ON SUCCESS:                                                             │    │
│  │ • PayoutRequest status = COMPLETED                                      │    │
│  │ • Escrow status = CLOSED                                                │    │
│  │ • Notify organizer (SMS + Email + Push)                                │    │
│  │                                                                          │    │
│  │ ON FAILURE (US-FIN-003):                                                │    │
│  │ • PayoutRequest status = FAILED                                         │    │
│  │ • Restore escrow balance (compensation)                                 │    │
│  │ • Alert finance team                                                     │    │
│  │ • Retry up to 3 times                                                   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 10.3 Commission Earning Process (Two-Stage Model)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    COMMISSION EARNING PROCESS                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  WHEN DOES COMMISSION BECOME "EARNED"?                                          │
│  ════════════════════════════════════                                            │
│                                                                                  │
│  Same conditions as payout eligibility:                                         │
│  • Event status = COMPLETED                                                      │
│  • Current date >= Event date + 7 days (hold period passed)                     │
│                                                                                  │
│  AUTOMATED BATCH JOB (runs daily):                                              │
│  ═══════════════════════════════════                                             │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │ @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily                         │    │
│  │ public void processCommissionEarning() {                                │    │
│  │                                                                          │    │
│  │   // 1. Find all events that completed + 7 days ago                     │    │
│  │   List<Event> eligibleEvents = findEventsWithHoldPeriodPassed();        │    │
│  │                                                                          │    │
│  │   for (Event event : eligibleEvents) {                                  │    │
│  │     // 2. Find all tickets with commissionStatus = PENDING              │    │
│  │     List<Ticket> tickets = findPendingCommissionTickets(event.id);      │    │
│  │                                                                          │    │
│  │     for (Ticket ticket : tickets) {                                     │    │
│  │       // 3. Move commission from PENDING → EARNED                       │    │
│  │       debitPendingCommissionAccount(ticket.commissionAmount);           │    │
│  │       creditEarnedRevenueAccount(ticket.commissionAmount);              │    │
│  │                                                                          │    │
│  │       // 4. Update ticket                                               │    │
│  │       ticket.commissionStatus = EARNED;                                 │    │
│  │       ticket.commissionEarnedAt = Instant.now();                        │    │
│  │       ticketRepository.save(ticket);                                    │    │
│  │     }                                                                   │    │
│  │   }                                                                     │    │
│  │ }                                                                       │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  VISUAL FLOW:                                                                    │
│  ═══════════                                                                     │
│                                                                                  │
│  TICKET PURCHASE                    EVENT + 7 DAYS                              │
│       │                                  │                                       │
│       ▼                                  ▼                                       │
│  ┌─────────────────┐              ┌─────────────────┐                           │
│  │ Pending         │   Batch Job  │ Earned          │                           │
│  │ Commission      │ ───────────► │ Revenue         │                           │
│  │ Account         │              │ Account         │                           │
│  │ +K25            │              │ +K25            │                           │
│  │ (NOT yours yet) │              │ (NOW it's yours)│                           │
│  └─────────────────┘              └─────────────────┘                           │
│                                                                                  │
│  WHY THIS MATTERS:                                                              │
│  ═════════════════                                                              │
│  • Accounting compliance: Revenue recognized when earned                        │
│  • Simpler refunds: No "clawback" needed for pending commission                │
│  • Clear audit trail: Know exactly when commission was earned                   │
│  • Financial reporting: Separate pending vs earned revenue                      │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Transaction Tracking & Terminology

### 11.1 Financial Transaction Types

```javascript
const TransactionTypes = {
  // Credits (money in)
  TICKET_SALE: "Credit to escrow when ticket is purchased",
  COMMISSION_PENDING: "Credit to PENDING commission (5% of ticket price) - NOT earned yet!",

  // Commission lifecycle (Two-Stage Model)
  COMMISSION_EARNED: "Move from pending to earned (event completed + 7 days)",
  COMMISSION_CANCELLED: "Cancel pending commission (refund before event) - NO clawback needed",
  COMMISSION_CLAWBACK: "Debit from EARNED revenue (rare: refund after event)",

  // Debits (money out)
  REFUND_ESCROW: "Debit from escrow for refund",
  PAYOUT: "Debit from escrow for organizer payout",

  // Adjustments
  ADJUSTMENT_CREDIT: "Manual credit adjustment",
  ADJUSTMENT_DEBIT: "Manual debit adjustment"
};

// Commission Status Enum (add to Ticket model)
const CommissionStatus = {
  PENDING: "Commission held - event not yet completed",
  EARNED: "Commission earned - event completed + 7 days passed",
  CANCELLED: "Commission cancelled - refund issued before event",
  CLAWED_BACK: "Commission clawed back - refund after event (dispute)"
};

const TransactionCategories = {
  PURCHASE: "Related to ticket purchase",
  REFUND: "Related to refund processing",
  PAYOUT: "Related to organizer payout",
  ADJUSTMENT: "Manual adjustment"
};
```

### 11.2 Industry Standard Terminology

| Term | Definition | Your System |
|------|------------|-------------|
| **Gross Amount** | Total ticket price paid by buyer | K500 |
| **Net Amount** | Amount after platform commission | K475 (95%) |
| **Commission** | Platform fee deducted from sale | K25 (5%) |
| **Escrow** | Funds held until payout eligibility | Event-level escrow account |
| **Hold Period** | Time between event end and payout eligibility | 7 days |
| **Settlement** | Transfer of funds from payment provider to your bank | T+1 or T+2 |
| **Payout** | Transfer of funds from escrow to organizer bank | After hold period |
| **Clawback** | Reversal of commission on refund | 100% of commission returned |
| **Chargeback** | Buyer disputes charge with bank/provider | Handle via dispute process |
| **Reconciliation** | Matching internal records with bank statements | Daily automated |

### 11.3 Transaction ID Formats

```javascript
const TransactionIdFormats = {
  // Ticket purchase transaction
  ticketPurchase: "TXN-PURCHASE-{date}-{sequence}",
  // Example: TXN-PURCHASE-20260228-000001

  // Commission transaction
  commission: "TXN-COMM-{date}-{sequence}",
  // Example: TXN-COMM-20260228-000001

  // Refund transaction
  refund: "TXN-REFUND-{date}-{sequence}",
  // Example: TXN-REFUND-20260228-000001

  // Payout transaction
  payout: "TXN-PAYOUT-{date}-{sequence}",
  // Example: TXN-PAYOUT-20260228-000001

  // Payment provider reference
  providerRef: "{provider}-{date}-{uuid8}",
  // Example: MTN-20260228-ABCD1234
};
```

---

## 12. Settlement Process Implementation

### 12.1 Settlement Terminology

| Term | Definition | Your Implementation |
|------|------------|---------------------|
| **T+0** | Same-day settlement | Rare, premium accounts only |
| **T+1** | Next business day | MTN, Airtel, Zamtel standard |
| **T+2** | Two business days | Card payments |
| **Cutoff** | Time after which transactions go to next batch | Typically 5 PM |
| **Settlement File** | Report from provider listing settled transactions | Reconcile daily |

### 12.2 Settlement Status Tracking

```java
// Add to PaymentIntent.java
public enum SettlementStatus {
    PENDING,           // Payment confirmed but not yet settled to bank
    SETTLED,           // Money received in bank account
    FAILED,            // Settlement failed (rare)
    RECONCILED         // Matched with bank statement
}

@Document(collection = "payment_intents")
public class PaymentIntent {
    // ... existing fields ...

    private SettlementStatus settlementStatus;
    private Instant expectedSettlementDate;  // Calculate based on T+1/T+2
    private Instant actualSettlementDate;    // When money arrived
    private String settlementBatchId;        // Provider's batch reference
}
```

### 12.3 Settlement Reconciliation Job

```java
@Component
public class SettlementReconciliationJob {

    @Scheduled(cron = "0 0 10 * * MON-FRI")  // 10 AM on business days
    public void reconcileSettlements() {
        // 1. Fetch yesterday's settlement report from each provider
        List<SettlementReport> reports = fetchSettlementReports();

        // 2. For each transaction in report
        for (SettlementReport report : reports) {
            for (SettlementLine line : report.getLines()) {
                // 3. Find matching PaymentIntent
                PaymentIntent pi = paymentIntentRepository
                    .findByProviderTransactionId(line.getTransactionId());

                if (pi != null && pi.getSettlementStatus() == PENDING) {
                    // 4. Update settlement status
                    pi.setSettlementStatus(SETTLED);
                    pi.setActualSettlementDate(line.getSettlementDate());
                    pi.setSettlementBatchId(report.getBatchId());
                    paymentIntentRepository.save(pi);
                } else {
                    // 5. Flag discrepancy for investigation
                    createReconciliationAlert(line, "No matching pending payment");
                }
            }
        }

        // 6. Check for payments that should have settled but didn't
        List<PaymentIntent> overdue = paymentIntentRepository
            .findBySettlementStatusAndExpectedSettlementDateBefore(
                PENDING,
                Instant.now().minus(2, ChronoUnit.DAYS)
            );
        for (PaymentIntent pi : overdue) {
            createReconciliationAlert(pi, "Settlement overdue");
        }
    }
}
```

---

## 13. Account Classification (Escrow vs Non-Escrow)

### 13.1 Account Type Definitions

```java
public enum VirtualAccountType {
    // ESCROW ACCOUNTS - Money held FOR others (Liability)
    EVENT_ESCROW,           // Organizer's ticket revenue (per event)

    // NON-ESCROW ACCOUNTS - Platform's own money tracking
    PENDING_COMMISSION,     // Commission not yet earned (Liability*)
    EARNED_REVENUE,         // Commission earned (Equity/Revenue)
    OPERATING_RESERVE,      // Funds for operations (Asset tracking)

    // * PENDING_COMMISSION is Liability because we might return it on refund
}
```

### 13.2 Account Model Implementation

```java
@Document(collection = "virtual_accounts")
public class VirtualAccount {
    @Id
    private String id;

    private VirtualAccountType type;
    private String accountCode;           // "ESC-EVT001-2026" or "PLAT-PENDING"
    private String accountName;

    // For escrow accounts only
    private String eventId;               // null for platform accounts
    private String organizerId;           // null for platform accounts

    private BigDecimal balance;
    private String currency;              // "ZMW"

    // Accounting classification
    private AccountingClassification accountingType;  // ASSET, LIABILITY, REVENUE, EXPENSE

    // Ledger
    @Field("ledger")
    private List<LedgerEntry> ledgerEntries;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}

public enum AccountingClassification {
    ASSET,      // Debit to increase
    LIABILITY,  // Credit to increase
    REVENUE,    // Credit to increase
    EXPENSE     // Debit to increase
}
```

### 13.3 Singleton Platform Accounts

```java
@Service
public class PlatformAccountService {

    private static final String PENDING_COMMISSION_ID = "PLAT-PENDING-COMMISSION";
    private static final String EARNED_REVENUE_ID = "PLAT-EARNED-REVENUE";

    @PostConstruct
    public void ensurePlatformAccountsExist() {
        // Create singleton platform accounts if they don't exist
        createIfNotExists(PENDING_COMMISSION_ID, VirtualAccountType.PENDING_COMMISSION,
            AccountingClassification.LIABILITY, "Pending Commission (Not Yet Earned)");

        createIfNotExists(EARNED_REVENUE_ID, VirtualAccountType.EARNED_REVENUE,
            AccountingClassification.REVENUE, "Earned Platform Revenue");
    }

    public VirtualAccount getPendingCommissionAccount() {
        return virtualAccountRepository.findById(PENDING_COMMISSION_ID)
            .orElseThrow(() -> new IllegalStateException("Platform account not initialized"));
    }

    public VirtualAccount getEarnedRevenueAccount() {
        return virtualAccountRepository.findById(EARNED_REVENUE_ID)
            .orElseThrow(() -> new IllegalStateException("Platform account not initialized"));
    }
}
```

---

## 14. Refund Processing Fee Handling

### 14.1 Refund Fee Policy Configuration

```java
@ConfigurationProperties(prefix = "ticketing.refund")
public class RefundFeePolicy {

    // Who absorbs the refund processing fee?
    private RefundFeeBearer feeBearer = RefundFeeBearer.CUSTOMER;

    // For event cancellation by organizer
    private CancellationFeePolicy cancellationPolicy = CancellationFeePolicy.ORGANIZER_PAYS;

    // Minimum refund amount (to avoid tiny refunds)
    private BigDecimal minimumRefundAmount = new BigDecimal("10.00");

    public enum RefundFeeBearer {
        PLATFORM,   // Platform absorbs all fees (most customer-friendly)
        CUSTOMER,   // Customer absorbs refund fee (industry standard)
        ORGANIZER   // Organizer absorbs fees (when they cause cancellation)
    }

    public enum CancellationFeePolicy {
        PLATFORM_ABSORBS,     // Platform pays all fees on cancellation
        ORGANIZER_PAYS,       // Deduct fees from organizer's escrow
        SHARED                // Split between platform and organizer
    }
}
```

### 14.2 Refund Calculation Service

```java
@Service
public class RefundCalculationService {

    @Autowired
    private RefundFeePolicy policy;

    @Autowired
    private PaymentProviderService paymentProviderService;

    public RefundCalculation calculateRefund(Ticket ticket, BigDecimal refundPercentage) {
        BigDecimal ticketPrice = ticket.getPrice();
        BigDecimal refundAmount = ticketPrice.multiply(refundPercentage);

        // Get refund fee from payment provider
        BigDecimal refundFee = paymentProviderService
            .getRefundFee(ticket.getPaymentInfo().getProvider(), refundAmount);

        BigDecimal escrowPortion = ticket.getNetAmount().multiply(refundPercentage);
        BigDecimal commissionPortion = ticket.getCommissionAmount().multiply(refundPercentage);

        BigDecimal amountToCustomer;
        BigDecimal platformCost;
        BigDecimal escrowDeduction;

        switch (policy.getFeeBearer()) {
            case CUSTOMER:
                // Customer absorbs fee - they get less back
                amountToCustomer = refundAmount.subtract(refundFee);
                platformCost = BigDecimal.ZERO;
                escrowDeduction = escrowPortion;
                break;

            case PLATFORM:
                // Platform absorbs fee - customer gets full refund
                amountToCustomer = refundAmount;
                platformCost = refundFee;
                escrowDeduction = escrowPortion;
                break;

            case ORGANIZER:
                // Organizer absorbs fee (cancellation scenario)
                amountToCustomer = refundAmount;
                platformCost = BigDecimal.ZERO;
                escrowDeduction = escrowPortion.add(refundFee);
                break;

            default:
                throw new IllegalStateException("Unknown fee bearer");
        }

        return RefundCalculation.builder()
            .originalTicketPrice(ticketPrice)
            .refundPercentage(refundPercentage)
            .refundFee(refundFee)
            .amountToCustomer(amountToCustomer)
            .escrowDeduction(escrowDeduction)
            .commissionToCancel(commissionPortion)
            .platformCost(platformCost)
            .build();
    }
}
```

### 14.3 Event Cancellation Fee Handling

```java
@Service
public class EventCancellationService {

    public CancellationSummary processEventCancellation(String eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        List<Ticket> tickets = ticketRepository.findByEventIdAndStatusIn(
            eventId, List.of(TicketStatus.PURCHASED, TicketStatus.VALIDATED)
        );

        BigDecimal totalRefunds = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        int ticketCount = tickets.size();

        for (Ticket ticket : tickets) {
            RefundCalculation calc = refundCalculationService
                .calculateRefund(ticket, BigDecimal.ONE);  // 100% refund

            totalRefunds = totalRefunds.add(calc.getAmountToCustomer());
            totalFees = totalFees.add(calc.getRefundFee());
        }

        // Determine who pays the fees
        VirtualAccount escrow = escrowRepository.findByEventId(eventId);
        BigDecimal availableInEscrow = escrow.getBalance();

        // If organizer pays fees, check if escrow can cover it
        BigDecimal totalNeeded = totalRefunds;
        if (policy.getCancellationPolicy() == ORGANIZER_PAYS) {
            // Fees come from escrow (organizer's share)
            // If escrow can't cover, platform absorbs remainder
            if (availableInEscrow.compareTo(totalNeeded) < 0) {
                // Shortfall - platform must cover
                BigDecimal shortfall = totalNeeded.subtract(availableInEscrow);
                // Log alert for finance team
                alertService.createFinanceAlert(
                    "Cancellation shortfall: " + shortfall + " for event " + eventId
                );
            }
        }

        return CancellationSummary.builder()
            .eventId(eventId)
            .ticketCount(ticketCount)
            .totalRefunds(totalRefunds)
            .totalFees(totalFees)
            .escrowBalance(availableInEscrow)
            .build();
    }
}
```

---

## 15. Double-Entry Bookkeeping Implementation

### 15.1 Ledger Entry Model

```java
@Document
public class LedgerEntry {
    private String id;
    private Instant timestamp;
    private String transactionId;         // Links related debits/credits
    private String description;

    private String accountId;
    private String accountName;
    private AccountingClassification accountType;

    private EntryType entryType;          // DEBIT or CREDIT
    private BigDecimal amount;
    private BigDecimal balanceAfter;

    // Reference to source transaction
    private String referenceType;         // TICKET, REFUND, PAYOUT, etc.
    private String referenceId;           // ticket ID, refund ID, etc.

    private String createdBy;             // "SYSTEM" or user ID
}

public enum EntryType {
    DEBIT,
    CREDIT
}
```

### 15.2 Journal Entry Service

```java
@Service
public class JournalEntryService {

    @Autowired
    private VirtualAccountRepository accountRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Creates a balanced journal entry with multiple debits and credits.
     * Validates that total debits = total credits.
     */
    @Transactional
    public String createJournalEntry(JournalEntryRequest request) {
        // Validate balance
        BigDecimal totalDebits = request.getEntries().stream()
            .filter(e -> e.getType() == DEBIT)
            .map(JournalLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = request.getEntries().stream()
            .filter(e -> e.getType() == CREDIT)
            .map(JournalLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new UnbalancedJournalEntryException(
                "Debits (" + totalDebits + ") must equal Credits (" + totalCredits + ")"
            );
        }

        String transactionId = UUID.randomUUID().toString();

        for (JournalLine line : request.getEntries()) {
            VirtualAccount account = accountRepository.findById(line.getAccountId())
                .orElseThrow();

            // Calculate new balance based on account type and entry type
            BigDecimal newBalance = calculateNewBalance(
                account.getBalance(),
                account.getAccountingType(),
                line.getType(),
                line.getAmount()
            );

            // Create ledger entry
            LedgerEntry entry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .transactionId(transactionId)
                .description(request.getDescription())
                .accountId(account.getId())
                .accountName(account.getAccountName())
                .accountType(account.getAccountingType())
                .entryType(line.getType())
                .amount(line.getAmount())
                .balanceAfter(newBalance)
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .createdBy("SYSTEM")
                .build();

            // Atomic update with optimistic locking
            Query query = Query.query(Criteria.where("_id").is(account.getId())
                .and("version").is(account.getVersion()));
            Update update = new Update()
                .set("balance", newBalance)
                .push("ledgerEntries", entry)
                .inc("version", 1)
                .set("updatedAt", Instant.now());

            UpdateResult result = mongoTemplate.updateFirst(query, update, VirtualAccount.class);
            if (result.getModifiedCount() == 0) {
                throw new OptimisticLockingFailureException(
                    "Account " + account.getId() + " was modified concurrently"
                );
            }
        }

        return transactionId;
    }

    private BigDecimal calculateNewBalance(
            BigDecimal currentBalance,
            AccountingClassification accountType,
            EntryType entryType,
            BigDecimal amount) {

        // DEALER rule: Debit increases Dividends/Expenses/Assets
        //              Credit increases Liabilities/Equity/Revenue

        boolean increase = switch (accountType) {
            case ASSET, EXPENSE -> entryType == DEBIT;
            case LIABILITY, REVENUE -> entryType == CREDIT;
        };

        return increase
            ? currentBalance.add(amount)
            : currentBalance.subtract(amount);
    }
}
```

### 15.3 Pre-Built Journal Entry Templates

```java
@Service
public class FinancialTransactionService {

    @Autowired
    private JournalEntryService journalService;

    /**
     * TICKET PURCHASE
     * Dr: Bank Account (Asset)              +K490
     * Dr: Payment Processing Fee (Expense)  +K10
     * Cr: Event Escrow (Liability)          +K475
     * Cr: Pending Commission (Liability)    +K25
     */
    public void recordTicketPurchase(Ticket ticket, PaymentIntent payment) {
        JournalEntryRequest request = JournalEntryRequest.builder()
            .description("Ticket purchase: " + ticket.getTicketNumber())
            .referenceType("TICKET")
            .referenceId(ticket.getId())
            .entries(List.of(
                JournalLine.debit("BANK-MAIN", payment.getNetAmount()),
                JournalLine.debit("EXP-PROCESSING-FEE", payment.getProcessingFee()),
                JournalLine.credit("ESC-" + ticket.getEventId(), ticket.getNetAmount()),
                JournalLine.credit("PLAT-PENDING-COMMISSION", ticket.getCommissionAmount())
            ))
            .build();

        journalService.createJournalEntry(request);
    }

    /**
     * COMMISSION EARNED (batch job)
     * Dr: Pending Commission (Liability)    -K25
     * Cr: Commission Revenue (Revenue)      +K25
     */
    public void recordCommissionEarned(Ticket ticket) {
        JournalEntryRequest request = JournalEntryRequest.builder()
            .description("Commission earned: " + ticket.getTicketNumber())
            .referenceType("COMMISSION_EARNING")
            .referenceId(ticket.getId())
            .entries(List.of(
                JournalLine.debit("PLAT-PENDING-COMMISSION", ticket.getCommissionAmount()),
                JournalLine.credit("PLAT-EARNED-REVENUE", ticket.getCommissionAmount())
            ))
            .build();

        journalService.createJournalEntry(request);
    }

    /**
     * REFUND (Pending Commission)
     * Dr: Event Escrow (Liability)          -K475
     * Dr: Pending Commission (Liability)    -K25
     * Cr: Bank Account (Asset)              -K500
     */
    public void recordRefundWithPendingCommission(
            Ticket ticket,
            RefundCalculation calc) {

        JournalEntryRequest request = JournalEntryRequest.builder()
            .description("Refund (pending commission): " + ticket.getTicketNumber())
            .referenceType("REFUND")
            .referenceId(ticket.getId())
            .entries(List.of(
                JournalLine.debit("ESC-" + ticket.getEventId(), calc.getEscrowDeduction()),
                JournalLine.debit("PLAT-PENDING-COMMISSION", calc.getCommissionToCancel()),
                JournalLine.credit("BANK-MAIN", calc.getAmountToCustomer())
            ))
            .build();

        journalService.createJournalEntry(request);
    }

    /**
     * REFUND (Earned Commission - Dispute)
     * Dr: Commission Clawback (Expense)     +K25
     * Dr: Accounts Receivable (Asset)       +K475
     * Cr: Bank Account (Asset)              -K500
     */
    public void recordRefundWithEarnedCommission(
            Ticket ticket,
            RefundCalculation calc) {

        JournalEntryRequest request = JournalEntryRequest.builder()
            .description("Refund (earned commission clawback): " + ticket.getTicketNumber())
            .referenceType("REFUND_DISPUTE")
            .referenceId(ticket.getId())
            .entries(List.of(
                JournalLine.debit("EXP-COMMISSION-CLAWBACK", calc.getCommissionToCancel()),
                JournalLine.debit("AR-ORGANIZER-" + ticket.getOrganizerId(), calc.getEscrowDeduction()),
                JournalLine.credit("BANK-MAIN", calc.getAmountToCustomer())
            ))
            .build();

        journalService.createJournalEntry(request);
    }

    /**
     * ORGANIZER PAYOUT
     * Dr: Event Escrow (Liability)          -K28,500
     * Cr: Bank Account (Asset)              -K28,500
     */
    public void recordOrganizerPayout(PayoutRequest payout, VirtualAccount escrow) {
        JournalEntryRequest request = JournalEntryRequest.builder()
            .description("Organizer payout: " + payout.getId())
            .referenceType("PAYOUT")
            .referenceId(payout.getId())
            .entries(List.of(
                JournalLine.debit(escrow.getId(), payout.getAmount()),
                JournalLine.credit("BANK-MAIN", payout.getAmount())
            ))
            .build();

        journalService.createJournalEntry(request);
    }
}
```

### 15.4 Financial Reconciliation Query

```java
@Repository
public interface VirtualAccountRepository extends MongoRepository<VirtualAccount, String> {

    // Sum all virtual account balances
    @Aggregation(pipeline = {
        "{ $group: { _id: null, totalBalance: { $sum: '$balance' } } }"
    })
    BigDecimal sumAllBalances();

    // Group by account type
    @Aggregation(pipeline = {
        "{ $group: { _id: '$accountingType', totalBalance: { $sum: '$balance' } } }"
    })
    List<BalanceByType> sumBalancesByType();
}

@Service
public class ReconciliationService {

    /**
     * THE GOLDEN RULE: Sum of all virtual accounts = Bank balance
     */
    public ReconciliationResult reconcile(BigDecimal actualBankBalance) {
        BigDecimal virtualTotal = virtualAccountRepository.sumAllBalances();

        BigDecimal difference = actualBankBalance.subtract(virtualTotal);

        return ReconciliationResult.builder()
            .actualBankBalance(actualBankBalance)
            .virtualAccountTotal(virtualTotal)
            .difference(difference)
            .isBalanced(difference.compareTo(BigDecimal.ZERO) == 0)
            .balancesByType(virtualAccountRepository.sumBalancesByType())
            .build();
    }
}
```

---

## 16. Implementation Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Set up MongoDB Replica Set (use existing docker/mongodb-replica-set/)
- [ ] Add Spring Modulith dependencies to all 3 services
- [ ] Configure Azure Service Bus
- [ ] Update Ticket.java with @Version, commissionStatus, commissionEarnedAt
- [ ] Create PaymentIntent model with settlementStatus
- [ ] Update EscrowAccount.java with per-event fields

### Phase 2: Virtual Account System (Week 2-3)
- [ ] Create VirtualAccount model with AccountingClassification
- [ ] Create LedgerEntry model for double-entry
- [ ] Implement JournalEntryService with balance validation
- [ ] Create singleton platform accounts (PENDING_COMMISSION, EARNED_REVENUE)
- [ ] Implement VirtualAccountRepository with balance queries

### Phase 3: Double-Booking Prevention (Week 3-4)
- [ ] Implement atomic reservation in Booking Service
- [ ] Add reservation cleanup job
- [ ] Implement idempotency checking

### Phase 4: Payment Integration (Week 4-5)
- [ ] Implement payment provider abstraction
- [ ] Implement MTN MoMo, Airtel Money, Zamtel Kwacha providers
- [ ] Implement webhook handler with signature verification
- [ ] Implement polling fallback
- [ ] Add settlement tracking to PaymentIntent

### Phase 5: Financial Transaction Recording (Week 5-6)
- [ ] Implement FinancialTransactionService with journal templates
- [ ] recordTicketPurchase() - creates escrow + pending commission entries
- [ ] recordCommissionEarned() - moves pending to earned
- [ ] recordRefundWithPendingCommission() - simple cancel
- [ ] recordRefundWithEarnedCommission() - clawback (rare)
- [ ] recordOrganizerPayout() - escrow to bank transfer

### Phase 6: Settlement & Reconciliation (Week 6-7)
- [ ] Implement SettlementReconciliationJob (daily at 10 AM)
- [ ] Integrate settlement file parsing from providers
- [ ] Implement ReconciliationService (Golden Rule check)
- [ ] Create finance dashboard alerts for discrepancies

### Phase 7: Refund Processing (Week 7-8)
- [ ] Implement RefundFeePolicy configuration
- [ ] Implement RefundCalculationService
- [ ] Implement EventCancellationService with fee handling
- [ ] Handle organizer-caused vs platform-caused refunds

### Phase 8: Commission Earning Automation (Week 8)
- [ ] Implement daily batch job for commission earning
- [ ] Find events where eventDate + 7 days <= today
- [ ] Process all PENDING tickets to EARNED status
- [ ] Create journal entries for each earning

### Phase 9: Testing & Hardening (Week 9-10)
- [ ] Unit tests for journal entry balance validation
- [ ] Integration tests for full purchase→refund flow
- [ ] Test settlement reconciliation with mock data
- [ ] Load testing for concurrent bookings
- [ ] Chaos testing for failure scenarios

---

## Summary

This architecture provides:

1. **Aligned with Requirements**: Based on your USER_STORIES.md, no extra features
2. **Double-Booking Prevention**: Atomic MongoDB operations with @Version
3. **Payment Consistency**: Idempotency + Webhook + Polling + Settlement Tracking
4. **Two-Stage Commission Model**: PENDING → EARNED (simplifies refunds!)
5. **Clear Account Classification**: Escrow (others' money) vs Revenue (your money)
6. **Double-Entry Bookkeeping**: Every transaction balanced (Debits = Credits)
7. **Settlement Reconciliation**: Daily job to match provider reports with bank
8. **Refund Fee Handling**: Configurable policy (customer/platform/organizer pays)
9. **Event Cancellation**: Proper fee allocation and mass refund processing
10. **Bank Account Linking**: Already in your BankAccount.java model
11. **Financial Reporting**: Ledger entries with full audit trail

### Key Models to Create/Update

| Model | Service | Changes Needed |
|-------|---------|----------------|
| `Ticket.java` | Booking | Add commissionStatus, commissionEarnedAt |
| `PaymentIntent.java` | Booking | Add settlementStatus, expectedSettlementDate |
| `VirtualAccount.java` | Booking | NEW - for all virtual account tracking |
| `LedgerEntry.java` | Booking | NEW - for double-entry bookkeeping |
| `RefundFeePolicy.java` | Booking | NEW - configuration for fee handling |

### Key Services to Implement

| Service | Purpose |
|---------|---------|
| `JournalEntryService` | Creates balanced double-entry journal entries |
| `FinancialTransactionService` | Pre-built journal templates for each transaction type |
| `RefundCalculationService` | Calculates refund amounts with fee handling |
| `SettlementReconciliationJob` | Daily reconciliation with payment providers |
| `ReconciliationService` | Verifies Golden Rule (virtual = bank balance) |
| `PlatformAccountService` | Manages singleton platform accounts |

### The Golden Rule

```
Sum of all Virtual Account Balances = Actual Bank Account Balance

Pending Commission + Earned Revenue + All Event Escrows = Bank Balance
```

If this doesn't match, investigate immediately!
