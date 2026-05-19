# Financial Education Guide: How Money Flows in a Ticketing System

## A Complete Lesson for Understanding Ticketing Platform Finance

**Written for:** Anyone who wants to understand how money moves in ticketing platforms
**Difficulty:** Beginner (explained like teaching a student)

---

## Table of Contents

### Part 1: Fundamentals
1. [Lesson 1: The Basic Concept - What Happens When Someone Buys a Ticket](#lesson-1-the-basic-concept)
2. [Lesson 2: Real Accounts vs Virtual Accounts](#lesson-2-real-accounts-vs-virtual-accounts)
3. [Lesson 3: Who Gets a Piece of the Ticket Price](#lesson-3-who-gets-a-piece-of-the-ticket-price)
4. [Lesson 4: The Complete Money Journey - Step by Step](#lesson-4-the-complete-money-journey)
5. [Lesson 5: Why We Need Virtual Accounts](#lesson-5-why-we-need-virtual-accounts)

### Part 2: Settlement & Account Types
6. [Lesson 6: The Settlement Process - When Money Actually Arrives](#lesson-6-the-settlement-process)
7. [Lesson 7: Understanding "Escrow" - Not All Virtual Accounts Are Escrow](#lesson-7-understanding-escrow)
8. [Lesson 8: Two-Stage Commission Model - Pending vs Earned](#lesson-8-two-stage-commission-model)

### Part 3: Money Movements
9. [Lesson 9: Refunds - Money Going Backwards](#lesson-9-refunds)
10. [Lesson 10: Refund Processing Fees - Who Pays?](#lesson-10-refund-processing-fees)
11. [Lesson 11: Payouts - Giving Organizers Their Money](#lesson-11-payouts)
12. [Lesson 12: Event Cancellation - The Full Financial Impact](#lesson-12-event-cancellation)

### Part 4: Double-Entry Bookkeeping
13. [Lesson 13: What is Double-Entry Bookkeeping?](#lesson-13-double-entry-bookkeeping-basics)
14. [Lesson 14: Debit vs Credit - The Confusing Part Explained](#lesson-14-debit-vs-credit)
15. [Lesson 15: Every Transaction in Your System - Complete Journal Entries](#lesson-15-complete-journal-entries)

### Part 5: Summary
16. [Lesson 16: Your System's Complete Account Structure](#lesson-16-account-structure)
17. [Lesson 17: Putting It All Together](#lesson-17-putting-it-all-together)

---

## Lesson 1: The Basic Concept

### What Happens When Someone Buys a Ticket?

Imagine you're selling lemonade for a friend. Your friend makes the lemonade, but you help sell it. For your help, you keep a small part of each sale.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        THE LEMONADE EXAMPLE                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Customer pays K10 for lemonade                                                │
│                                                                                  │
│   ┌─────────────┐          ┌─────────────┐          ┌─────────────┐            │
│   │  Customer   │────K10───│    YOU      │────K9────│   Friend    │            │
│   │  (Buyer)    │          │  (Platform) │          │ (Organizer) │            │
│   └─────────────┘          └─────────────┘          └─────────────┘            │
│                                   │                                             │
│                                   └──── K1 (Your fee for helping)               │
│                                                                                  │
│   The K10 was split:                                                            │
│   • K9 goes to your friend (the lemonade maker)                                 │
│   • K1 stays with you (your commission for selling)                             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**In your ticketing system:**
- **Customer** = Ticket Buyer (person attending the event)
- **You** = Your Platform (PML Tickets)
- **Friend** = Event Organizer (person hosting the event)

---

## Lesson 2: Real Accounts vs Virtual Accounts

### Understanding the Difference

Think of it like this:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    REAL ACCOUNTS vs VIRTUAL ACCOUNTS                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   REAL BANK ACCOUNT                      VIRTUAL ACCOUNT                        │
│   ══════════════════                     ═══════════════                        │
│                                                                                  │
│   ┌─────────────────────┐               ┌─────────────────────┐                │
│   │  🏦 ZANACO BANK     │               │  📊 YOUR DATABASE   │                │
│   │                     │               │                     │                │
│   │  Account: ****1234  │               │  "Escrow for        │                │
│   │  Balance: K50,000   │               │   Music Festival"   │                │
│   │                     │               │  Balance: K28,500   │                │
│   │  This is ACTUAL     │               │                     │                │
│   │  money in a bank    │               │  This is a NUMBER   │                │
│   │                     │               │  in your database   │                │
│   │  You can go to ATM  │               │                     │                │
│   │  and withdraw it    │               │  It REPRESENTS      │                │
│   │                     │               │  money, but isn't   │                │
│   │                     │               │  a real bank account│                │
│   └─────────────────────┘               └─────────────────────┘                │
│                                                                                  │
│                                                                                  │
│   KEY INSIGHT:                                                                  │
│   ════════════                                                                  │
│   Virtual accounts are like LABELS or FOLDERS that help you track              │
│   "who owns what portion" of the REAL money sitting in your bank.              │
│                                                                                  │
│   It's like having one big jar of money, but with sticky notes                 │
│   saying "K28,500 belongs to Music Festival organizer"                          │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Simple Analogy: The Shared Piggy Bank

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      THE SHARED PIGGY BANK ANALOGY                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Imagine 3 kids sharing ONE piggy bank, but they each track their own money:  │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │              🐷 ONE REAL PIGGY BANK                                     │   │
│   │              Total inside: K100                                         │   │
│   │                                                                         │   │
│   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                    │   │
│   │   │ 📝 Alice's  │  │ 📝 Bob's    │  │ 📝 Carol's  │                    │   │
│   │   │   Notebook  │  │   Notebook  │  │   Notebook  │                    │   │
│   │   │             │  │             │  │             │                    │   │
│   │   │ "I have K40"│  │ "I have K35"│  │ "I have K25"│                    │   │
│   │   └─────────────┘  └─────────────┘  └─────────────┘                    │   │
│   │                                                                         │   │
│   │   These notebooks are like VIRTUAL ACCOUNTS                             │   │
│   │   The piggy bank is like the REAL BANK ACCOUNT                          │   │
│   │                                                                         │   │
│   │   K40 + K35 + K25 = K100 ✓ (The math must always match!)               │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   IN YOUR SYSTEM:                                                               │
│   • Piggy Bank = Your company's REAL bank account at Zanaco/Stanbic            │
│   • Alice's Notebook = Virtual Escrow Account for Event 1                       │
│   • Bob's Notebook = Virtual Escrow Account for Event 2                         │
│   • Carol's Notebook = Platform Revenue Account (your commission)               │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Your Real vs Virtual Accounts

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    YOUR SYSTEM'S ACCOUNTS                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   REAL BANK ACCOUNTS (Physical money held at actual banks)                      │
│   ══════════════════════════════════════════════════════                        │
│                                                                                  │
│   1. YOUR COMPANY'S MAIN BANK ACCOUNT                                           │
│      ┌─────────────────────────────────────────────────────────────────────┐   │
│      │ Bank: Stanbic Bank Zambia                                           │   │
│      │ Account Name: PML Tickets Ltd                                       │   │
│      │ Account Number: 9020001234567                                       │   │
│      │ Type: Business Current Account                                       │   │
│      │                                                                      │   │
│      │ This is where MTN/Airtel/Zamtel send money when customers pay       │   │
│      │ ALL ticket sale money lands here first                              │   │
│      └─────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   2. ORGANIZER'S BANK ACCOUNTS (external - not yours)                           │
│      ┌─────────────────────────────────────────────────────────────────────┐   │
│      │ These are accounts belonging to EVENT ORGANIZERS                    │   │
│      │ You store their details in your system for payouts                  │   │
│      │                                                                      │   │
│      │ Example:                                                             │   │
│      │ • John's Events Ltd - Zanaco ****4567                               │   │
│      │ • Music Productions Ltd - FNB ****8901                              │   │
│      │                                                                      │   │
│      │ When you do a payout, you transfer FROM your bank TO their bank     │   │
│      └─────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   VIRTUAL ACCOUNTS (Numbers in your database - tracking only)                   │
│   ═════════════════════════════════════════════════════════                     │
│                                                                                  │
│   1. PLATFORM REVENUE ACCOUNT (Your commission tracking)                        │
│      ┌─────────────────────────────────────────────────────────────────────┐   │
│      │ Collection: platform_accounts                                        │   │
│      │ Type: REVENUE                                                        │   │
│      │ Balance: K150,000                                                    │   │
│      │                                                                      │   │
│      │ This tracks how much of the money in your bank account              │   │
│      │ is YOUR PROFIT (commission you earned)                              │   │
│      └─────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   2. EVENT ESCROW ACCOUNTS (One per event)                                      │
│      ┌─────────────────────────────────────────────────────────────────────┐   │
│      │ Collection: escrow_accounts                                          │   │
│      │                                                                      │   │
│      │ Event: "Music Festival 2026"                                        │   │
│      │ Organizer: John's Events Ltd                                        │   │
│      │ Balance: K28,500                                                    │   │
│      │                                                                      │   │
│      │ This tracks how much of the money in your bank account              │   │
│      │ BELONGS TO this organizer for THIS event                            │   │
│      └─────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│      ┌─────────────────────────────────────────────────────────────────────┐   │
│      │ Event: "Comedy Night"                                               │   │
│      │ Organizer: Laugh Factory Ltd                                        │   │
│      │ Balance: K12,000                                                    │   │
│      │                                                                      │   │
│      │ Different event, different organizer, separate tracking             │   │
│      └─────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   THE GOLDEN RULE:                                                              │
│   ════════════════                                                              │
│   Sum of all virtual accounts = Money in real bank account                     │
│                                                                                  │
│   Pending Commission + Earned Revenue + Event Escrows    = Bank Balance        │
│   K5,000            + K145,000       + K28,500 + K12,000 = K190,500            │
│                                                                                  │
│   If this doesn't match, something is WRONG!                                    │
│                                                                                  │
│                                                                                  │
│   IMPORTANT: TWO-STAGE COMMISSION MODEL                                         │
│   ════════════════════════════════════                                          │
│   • PENDING COMMISSION: Money you WILL earn (but haven't yet!)                 │
│   • EARNED REVENUE: Money that IS yours (event completed + 7 days)             │
│                                                                                  │
│   Why? So refunds before events are simple - just cancel the pending           │
│   commission. No need to "take back" earned money!                              │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 3: Who Gets a Piece of the Ticket Price

### The Ticket Price Breakdown

When a customer pays K500 for a VIP ticket, that K500 doesn't all go to one place. It gets divided.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     THE K500 TICKET - WHO GETS WHAT?                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Customer pays: K500                                                           │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   K500                                                                  │   │
│   │   ████████████████████████████████████████████████████████████████████ │   │
│   │   │                                                              │    │ │   │
│   │   │              ORGANIZER'S SHARE                               │PLAT│ │   │
│   │   │                   K475 (95%)                                 │FORM│ │   │
│   │   │                                                              │ K25│ │   │
│   │   │                                                              │(5%)│ │   │
│   │   │                                                              │    │ │   │
│   │   ████████████████████████████████████████████████████████████████████ │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   BREAKDOWN:                                                                    │
│   ══════════                                                                    │
│                                                                                  │
│   ┌────────────────────┬──────────┬────────────────────────────────────────┐   │
│   │ WHO                │ AMOUNT   │ WHY                                    │   │
│   ├────────────────────┼──────────┼────────────────────────────────────────┤   │
│   │ Event Organizer    │ K475     │ They created the event, booked venue,  │   │
│   │                    │ (95%)    │ hired performers, took the risk        │   │
│   ├────────────────────┼──────────┼────────────────────────────────────────┤   │
│   │ Platform (You)     │ K25      │ You provided the technology, payment   │   │
│   │                    │ (5%)     │ processing, customer support, app      │   │
│   └────────────────────┴──────────┴────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   BUT WAIT! There's more complexity when payment providers are involved...      │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### The Full Picture with Payment Provider Fees

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                  THE FULL K500 BREAKDOWN (WITH PAYMENT FEES)                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Customer pays K500 via MTN Mobile Money                                       │
│                                                                                  │
│   OPTION A: Platform absorbs payment fees (most common)                         │
│   ═══════════════════════════════════════════════════                           │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   K500 from customer                                                    │   │
│   │      │                                                                  │   │
│   │      ├──── K10 ────► MTN Mobile Money (2% transaction fee)             │   │
│   │      │               (They keep this for processing the payment)        │   │
│   │      │                                                                  │   │
│   │      └──── K490 ────► Lands in YOUR bank account                       │   │
│   │                          │                                              │   │
│   │                          ├──── K475 ──► Organizer's Escrow              │   │
│   │                          │                                              │   │
│   │                          └──── K15 ───► PENDING Commission              │   │
│   │                                         (K25 commission - K10 fee)      │   │
│   │                                         NOT earned yet! Stays pending   │   │
│   │                                         until event + 7 days            │   │
│   │                                                                         │   │
│   │   Your profit reduced because you paid the mobile money fee!            │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   OPTION B: Customer pays payment fees (less common)                            │
│   ═══════════════════════════════════════════════════                           │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Customer sees: K500 ticket + K10 processing fee = K510 total          │   │
│   │                                                                         │   │
│   │   K510 from customer                                                    │   │
│   │      │                                                                  │   │
│   │      ├──── K10 ────► MTN Mobile Money (fee)                            │   │
│   │      │                                                                  │   │
│   │      └──── K500 ───► Lands in YOUR bank account                        │   │
│   │                          │                                              │   │
│   │                          ├──── K475 ──► Organizer's Escrow              │   │
│   │                          │                                              │   │
│   │                          └──── K25 ───► PENDING Commission (full 5%)   │   │
│   │                                         (Becomes EARNED after event+7d) │   │
│   │                                                                         │   │
│   │   Your full commission preserved, but customer pays more                │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   OPTION C: Organizer pays fees (rare)                                          │
│   ═══════════════════════════════════                                            │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   K500 from customer                                                    │   │
│   │      │                                                                  │   │
│   │      ├──── K10 ────► MTN Mobile Money (fee)                            │   │
│   │      │                                                                  │   │
│   │      └──── K490 ───► Lands in YOUR bank account                        │   │
│   │                          │                                              │   │
│   │                          ├──── K465 ──► Organizer's Escrow              │   │
│   │                          │              (K475 - K10 fee)                │   │
│   │                          │                                              │   │
│   │                          └──── K25 ───► PENDING Commission (full 5%)   │   │
│   │                                                                         │   │
│   │   Organizer receives less, but knows upfront                           │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   RECOMMENDATION FOR YOUR SYSTEM:                                               │
│   Use OPTION A (Platform absorbs fees) because:                                 │
│   • Simpler customer experience (no surprise fees)                              │
│   • More competitive with other platforms                                       │
│   • Organizer gets predictable amount                                           │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 4: The Complete Money Journey - Step by Step

### Following the Money from Customer to Organizer

Let's follow K500 through the ENTIRE journey:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    THE COMPLETE MONEY JOURNEY                                    │
│                    (Following K500 step by step)                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   DAY 1, 10:00 AM - CUSTOMER BUYS TICKET                                        │
│   ══════════════════════════════════════                                        │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 1: Customer clicks "Buy Ticket" in your app                     │   │
│   │                                                                         │   │
│   │   ┌─────────────┐     "I want VIP ticket"     ┌─────────────┐          │   │
│   │   │  Customer   │ ──────────────────────────► │  Your App   │          │   │
│   │   │  (Buyer)    │                             │  (Mobile)   │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   At this point: No money has moved yet!                               │   │
│   │   You've only reserved the ticket for 10 minutes                       │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 2: Customer redirected to MTN Mobile Money                      │   │
│   │                                                                         │   │
│   │   ┌─────────────┐                             ┌─────────────┐          │   │
│   │   │  Your App   │ ──── "Pay K500 to PML" ───► │  MTN MoMo   │          │   │
│   │   │             │                             │    App      │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   Still no money moved - customer just sees payment request            │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 3: Customer enters PIN and confirms payment                     │   │
│   │                                                                         │   │
│   │   ┌─────────────┐     "Enter PIN: ****"       ┌─────────────┐          │   │
│   │   │  Customer   │ ──────────────────────────► │  MTN MoMo   │          │   │
│   │   │             │                             │             │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   NOW THE MONEY MOVES!                                                 │   │
│   │                                                                         │   │
│   │   ┌─────────────────────────────────────────────────────────────────┐  │   │
│   │   │                                                                 │  │   │
│   │   │   Customer's MTN Wallet                                         │  │   │
│   │   │   Before: K1,200                                                │  │   │
│   │   │   After:  K700  (K500 deducted)                                │  │   │
│   │   │                                                                 │  │   │
│   │   └─────────────────────────────────────────────────────────────────┘  │   │
│   │                                                                         │   │
│   │   Where did the K500 go?                                               │   │
│   │   → It's now sitting in MTN's holding account                          │   │
│   │   → MTN will send it to you later (this is called "settlement")        │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 4: MTN sends confirmation to your system (webhook)              │   │
│   │                                                                         │   │
│   │   ┌─────────────┐    "Payment SUCCESS!"       ┌─────────────┐          │   │
│   │   │  MTN MoMo   │ ──────────────────────────► │ Your Server │          │   │
│   │   │             │                             │(Booking Svc)│          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   Your system now knows payment was successful                         │   │
│   │   But money is still with MTN, not in your bank yet!                   │   │
│   │                                                                         │   │
│   │   YOUR SYSTEM NOW DOES:                                                │   │
│   │   1. Mark ticket as PURCHASED                                          │   │
│   │   2. Credit virtual escrow: +K475                                      │   │
│   │   3. Credit platform revenue: +K25                                     │   │
│   │   4. Generate QR code                                                  │   │
│   │   5. Send confirmation to customer                                     │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   DAY 2, 6:00 AM - SETTLEMENT (Money arrives in your bank)                     │
│   ═════════════════════════════════════════════════════════                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 5: MTN settles funds to your bank (T+1)                         │   │
│   │                                                                         │   │
│   │   ┌─────────────┐                             ┌─────────────┐          │   │
│   │   │ MTN Holding │ ──── Bank Transfer ───────► │ Your Bank   │          │   │
│   │   │   Account   │      K490 (K500-K10 fee)    │  Account    │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   MTN keeps K10 as their transaction fee                               │   │
│   │   You receive K490 in your actual bank account                         │   │
│   │                                                                         │   │
│   │   Your Bank Account:                                                    │   │
│   │   Before: K100,000                                                     │   │
│   │   After:  K100,490 (+K490)                                             │   │
│   │                                                                         │   │
│   │   NOTE: This K490 matches your virtual accounts:                       │   │
│   │   • Escrow: K475                                                       │   │
│   │   • Platform Revenue: K15 (K25 commission - K10 MTN fee absorbed)      │   │
│   │   • Total: K490 ✓                                                      │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   EVENT DATE + 7 DAYS - PAYOUT TO ORGANIZER                                    │
│   ═══════════════════════════════════════════                                    │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 6: Organizer requests payout                                    │   │
│   │                                                                         │   │
│   │   Conditions met:                                                       │   │
│   │   ✓ Event has ended                                                    │   │
│   │   ✓ 7-day hold period passed                                           │   │
│   │   ✓ No disputes pending                                                │   │
│   │   ✓ Bank account verified                                              │   │
│   │                                                                         │   │
│   │   Finance team approves payout request                                  │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 7: Bank transfer to organizer                                   │   │
│   │                                                                         │   │
│   │   ┌─────────────┐                             ┌─────────────┐          │   │
│   │   │ Your Bank   │ ──── Bank Transfer ───────► │ Organizer's │          │   │
│   │   │  Account    │        K475                 │    Bank     │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   Your Bank Account:                                                    │   │
│   │   Before: K100,490                                                     │   │
│   │   After:  K100,015 (-K475)                                             │   │
│   │                                                                         │   │
│   │   Virtual Accounts Updated:                                             │   │
│   │   • Escrow: K475 → K0 (closed)                                         │   │
│   │   • Platform Revenue: K15 (unchanged - this is your profit)            │   │
│   │                                                                         │   │
│   │   Organizer's Bank Account:                                            │   │
│   │   Before: K5,000                                                       │   │
│   │   After:  K5,475 (+K475)                                               │   │
│   │                                                                         │   │
│   │   JOURNEY COMPLETE! 🎉                                                 │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 5: Why We Need Virtual Accounts

### The Problem Without Virtual Accounts

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    WHY VIRTUAL ACCOUNTS ARE ESSENTIAL                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   SCENARIO: You have 3 events, 100 tickets sold total                          │
│                                                                                  │
│   WITHOUT VIRTUAL ACCOUNTS:                                                     │
│   ═══════════════════════════                                                    │
│                                                                                  │
│   Your Bank Balance: K50,000                                                    │
│                                                                                  │
│   Questions you CANNOT answer:                                                  │
│   • How much belongs to Music Festival organizer?  🤷                           │
│   • How much belongs to Comedy Night organizer?    🤷                           │
│   • How much is your profit?                       🤷                           │
│   • If Music Festival is cancelled, how much to refund? 🤷                      │
│   • Can Comedy Night organizer request K30,000 payout?  🤷                      │
│                                                                                  │
│   It's just one big number with no breakdown!                                   │
│                                                                                  │
│                                                                                  │
│   WITH VIRTUAL ACCOUNTS:                                                        │
│   ══════════════════════                                                         │
│                                                                                  │
│   Your Bank Balance: K50,000                                                    │
│                                                                                  │
│   Broken down by virtual accounts:                                              │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Platform Revenue Account:        K2,500  (your earned commission)       │   │
│   │ Music Festival Escrow:           K28,500 (organizer 1's money)          │   │
│   │ Comedy Night Escrow:             K12,000 (organizer 2's money)          │   │
│   │ Concert 2026 Escrow:             K7,000  (organizer 3's money)          │   │
│   │ ─────────────────────────────────────────────────────────────────────── │   │
│   │ TOTAL:                           K50,000 ✓ (matches bank!)              │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   Now you CAN answer:                                                           │
│   • Music Festival organizer has K28,500 ✓                                      │
│   • Comedy Night organizer has K12,000 ✓                                        │
│   • Your profit is K2,500 ✓                                                     │
│   • If Music Festival cancelled: refund K28,500 + clawback commission ✓         │
│   • Comedy Night can request up to K12,000 (not K30,000) ✓                      │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Virtual Account Rules

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      VIRTUAL ACCOUNT RULES                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   RULE 1: DOUBLE-ENTRY BOOKKEEPING                                              │
│   ══════════════════════════════════                                             │
│                                                                                  │
│   Every transaction must have TWO sides:                                        │
│                                                                                  │
│   When ticket is sold (K500):                                                   │
│   ┌────────────────────────────────────────────────────────────────────────┐    │
│   │ DEBIT (Money coming in)         │ CREDIT (Where it goes)              │    │
│   │ ────────────────────────────────│─────────────────────────────────────│    │
│   │ Bank Account: +K490             │ Escrow Account: +K475               │    │
│   │ (Money received from MTN)       │ Platform Revenue: +K15              │    │
│   │                                 │                                     │    │
│   │ Total In: K490                  │ Total Allocated: K490 ✓            │    │
│   └────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│   When payout is made (K475):                                                   │
│   ┌────────────────────────────────────────────────────────────────────────┐    │
│   │ DEBIT (Where it's taken from)   │ CREDIT (Where it goes)              │    │
│   │ ────────────────────────────────│─────────────────────────────────────│    │
│   │ Escrow Account: -K475           │ Organizer Bank: +K475               │    │
│   │ (Reduce escrow balance)         │ (Money transferred out)             │    │
│   │                                 │                                     │    │
│   │ Total Out: K475                 │ Total Received: K475 ✓             │    │
│   └────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│                                                                                  │
│   RULE 2: VIRTUAL ACCOUNTS CAN NEVER GO NEGATIVE                                │
│   ═══════════════════════════════════════════════                                │
│                                                                                  │
│   If Escrow has K10,000, you cannot pay out K15,000                             │
│   System must reject: "Insufficient balance"                                    │
│                                                                                  │
│                                                                                  │
│   RULE 3: SUM OF VIRTUAL ACCOUNTS = REAL BANK BALANCE                          │
│   ═════════════════════════════════════════════════════                          │
│                                                                                  │
│   This is your RECONCILIATION check                                             │
│   Run this daily to ensure no money is "lost"                                   │
│                                                                                  │
│   If they don't match, you have a BUG or FRAUD!                                 │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 6: The Settlement Process

### Understanding T+1, T+2 Settlement

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    SETTLEMENT: WHEN MONEY ACTUALLY ARRIVES                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   When a customer pays via MTN/Airtel/Zamtel, you don't get the money          │
│   instantly. There's a delay called "settlement period".                        │
│                                                                                  │
│   COMMON SETTLEMENT PERIODS:                                                    │
│   ══════════════════════════                                                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Provider          │ Settlement Period │ What it means                   │   │
│   │───────────────────│───────────────────│─────────────────────────────────│   │
│   │ MTN Mobile Money  │ T+1               │ Money arrives next business day │   │
│   │ Airtel Money      │ T+1 to T+2        │ 1-2 business days               │   │
│   │ Zamtel Kwacha     │ T+1 to T+2        │ 1-2 business days               │   │
│   │ Card Payments     │ T+2 to T+3        │ 2-3 business days               │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   T = Transaction day                                                           │
│   T+1 = One day after transaction                                               │
│                                                                                  │
│                                                                                  │
│   EXAMPLE TIMELINE:                                                             │
│   ══════════════════                                                             │
│                                                                                  │
│   Monday 10:00 AM:  Customer buys ticket (K500)                                 │
│                     → Money leaves customer's wallet                            │
│                     → Your system records the sale                              │
│                     → Virtual accounts updated                                  │
│                     → BUT your bank account unchanged!                          │
│                                                                                  │
│   Tuesday 6:00 AM:  Settlement happens (T+1)                                    │
│                     → MTN sends K490 to your bank                               │
│                     → Your bank account increases by K490                       │
│                                                                                  │
│                                                                                  │
│   THE IMPLICATION:                                                              │
│   ═════════════════                                                              │
│                                                                                  │
│   There's a period where:                                                       │
│   • Your virtual accounts show: K475 escrow + K15 revenue = K490                │
│   • Your real bank account shows: K0 (not yet received)                         │
│                                                                                  │
│   This is NORMAL! The money is "in transit"                                     │
│                                                                                  │
│   For accounting purposes:                                                      │
│   • Virtual accounts = "Money OWED to us"                                       │
│   • Bank account = "Money we HAVE"                                              │
│                                                                                  │
│   Once settled:                                                                 │
│   • Virtual accounts = K490                                                     │
│   • Bank account = K490                                                         │
│   • Everything matches! ✓                                                       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Settlement Reconciliation

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    DAILY RECONCILIATION PROCESS                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Every day, you should verify money matches between:                           │
│   1. Payment provider reports (MTN/Airtel/Zamtel)                               │
│   2. Your bank statement                                                        │
│   3. Your virtual account totals                                                │
│                                                                                  │
│   EXAMPLE RECONCILIATION:                                                       │
│   ════════════════════════                                                       │
│                                                                                  │
│   Date: March 1, 2026                                                           │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ SOURCE                          │ AMOUNT                               │   │
│   │─────────────────────────────────│──────────────────────────────────────│   │
│   │ MTN Settlement Report           │ K45,000 deposited to your bank       │   │
│   │ Airtel Settlement Report        │ K12,000 deposited to your bank       │   │
│   │ Bank Statement (today)          │ +K57,000 incoming                    │   │
│   │─────────────────────────────────│──────────────────────────────────────│   │
│   │ Total from providers            │ K57,000                              │   │
│   │ Total in bank statement         │ K57,000                              │   │
│   │ MATCH? ✓                        │                                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   IF THEY DON'T MATCH:                                                          │
│   • Check for failed transactions                                               │
│   • Check for chargebacks/refunds                                               │
│   • Contact payment provider                                                    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 7: Refunds - Money Going Backwards

### When a Refund Happens

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    REFUND: MONEY FLOWS IN REVERSE                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   SCENARIO: Customer bought K500 VIP ticket, requests refund                   │
│                                                                                  │
│   Original Sale recorded:                                                       │
│   • Escrow: +K475                                                               │
│   • Platform Revenue: +K25                                                      │
│   • Customer paid: K500                                                         │
│                                                                                  │
│   NOW: FULL REFUND (100%)                                                       │
│   ═══════════════════════                                                        │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 1: Determine refund amount                                      │   │
│   │                                                                         │   │
│   │   Customer paid K500, they should get K500 back                         │   │
│   │                                                                         │   │
│   │   Where does this K500 come from?                                       │   │
│   │   • K475 from Escrow (organizer's share)                               │   │
│   │   • K25 from Commission (our share)                                     │   │
│   │                                                                         │   │
│   │   ⭐ TWO-STAGE COMMISSION MODEL MAKES THIS SIMPLE! ⭐                   │   │
│   │                                                                         │   │
│   │   IF refund is BEFORE event (commission status = PENDING):             │   │
│   │   → Just CANCEL the pending commission. It was never "earned"!         │   │
│   │   → No complicated "clawback" needed                                   │   │
│   │                                                                         │   │
│   │   IF refund is AFTER event (rare dispute, commission = EARNED):        │   │
│   │   → Must CLAWBACK from earned revenue (more complex)                   │   │
│   │   → This is rare because events have already happened                  │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 2: Update virtual accounts (TWO-STAGE MODEL)                   │   │
│   │                                                                         │   │
│   │   Escrow Account:                                                       │   │
│   │   Before: K28,500                                                       │   │
│   │   Change: -K475 (debit for refund)                                     │   │
│   │   After:  K28,025                                                       │   │
│   │                                                                         │   │
│   │   IF COMMISSION WAS PENDING (before event - common case):              │   │
│   │   ┌───────────────────────────────────────────────────────────────┐    │   │
│   │   │ Pending Commission Account:                                   │    │   │
│   │   │ Before: K2,500                                                │    │   │
│   │   │ Change: -K25 (CANCEL pending - simple debit!)                │    │   │
│   │   │ After:  K2,475                                                │    │   │
│   │   │                                                               │    │   │
│   │   │ Ticket.commissionStatus: PENDING → CANCELLED                 │    │   │
│   │   │ ✅ Simple! Money was never "earned" so no clawback needed!  │    │   │
│   │   └───────────────────────────────────────────────────────────────┘    │   │
│   │                                                                         │   │
│   │   IF COMMISSION WAS EARNED (after event - rare dispute):              │   │
│   │   ┌───────────────────────────────────────────────────────────────┐    │   │
│   │   │ Earned Revenue Account:                                       │    │   │
│   │   │ Before: K145,000                                              │    │   │
│   │   │ Change: -K25 (CLAWBACK from earned)                          │    │   │
│   │   │ After:  K144,975                                              │    │   │
│   │   │                                                               │    │   │
│   │   │ Ticket.commissionStatus: EARNED → CLAWED_BACK                │    │   │
│   │   │ ⚠️ More complex - affects already-recognized revenue         │    │   │
│   │   └───────────────────────────────────────────────────────────────┘    │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 3: Send money back to customer                                  │   │
│   │                                                                         │   │
│   │   Option A: Refund via same payment method                             │   │
│   │   ┌─────────────┐                             ┌─────────────┐          │   │
│   │   │ Your Bank   │ ────── K500 refund ───────► │ Customer's  │          │   │
│   │   │  Account    │   (via MTN API)             │ MTN Wallet  │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   │   Your Bank Account:                                                    │   │
│   │   Before: K100,000                                                     │   │
│   │   After:  K99,500 (-K500)                                              │   │
│   │                                                                         │   │
│   │   Customer's Wallet:                                                   │   │
│   │   Before: K700                                                         │   │
│   │   After:  K1,200 (+K500)                                               │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   FINANCIAL INTEGRITY CHECK:                                                    │
│   ═══════════════════════════                                                    │
│                                                                                  │
│   Escrow Debit + Commission Clawback = Refund Amount                           │
│   K475 + K25 = K500 ✓                                                           │
│                                                                                  │
│   This ensures money is properly accounted for!                                 │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Partial Refund (Time-Based Policy)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    PARTIAL REFUND EXAMPLE                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Your refund policy (from USER_STORIES.md):                                    │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Days before event │ Refund %  │ Example (K500 ticket)                  │   │
│   │───────────────────│───────────│────────────────────────────────────────│   │
│   │ 14+ days          │ 100%      │ K500 refund                            │   │
│   │ 7-13 days         │ 75%       │ K375 refund                            │   │
│   │ 1-6 days          │ 50%       │ K250 refund                            │   │
│   │ < 24 hours        │ 0%        │ No refund                              │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   EXAMPLE: 75% REFUND (8 days before event)                                     │
│   ══════════════════════════════════════════                                     │
│                                                                                  │
│   Original K500 ticket:                                                         │
│   • Escrow: K475                                                                │
│   • Platform Revenue: K25                                                       │
│                                                                                  │
│   75% Refund = K375 to customer                                                 │
│                                                                                  │
│   How to calculate the sources:                                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Total refund: K500 × 75% = K375                                      │   │
│   │                                                                         │   │
│   │   Commission portion of refund: K25 × 75% = K18.75                     │   │
│   │   Escrow portion of refund: K475 × 75% = K356.25                       │   │
│   │                                                                         │   │
│   │   Check: K18.75 + K356.25 = K375 ✓                                     │   │
│   │                                                                         │   │
│   │   What happens to the other 25%?                                        │   │
│   │   • K6.25 stays in Platform Revenue (retained commission)              │   │
│   │   • K118.75 stays in Escrow (retained by organizer)                    │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   After 75% Refund:                                                             │
│   • Customer receives: K375                                                     │
│   • Escrow: K475 - K356.25 = K118.75 (organizer keeps)                         │
│   • Platform Revenue: K25 - K18.75 = K6.25 (platform keeps)                    │
│                                                                                  │
│   Everyone keeps their proportional 25%!                                        │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 8: Payouts - Giving Organizers Their Money

### The Payout Process

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    PAYOUT: FROM ESCROW TO ORGANIZER'S BANK                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   TIMELINE:                                                                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Tickets     Tickets      Event       7-Day        Payout              │   │
│   │   On Sale     Selling      Happens     Hold         Eligible            │   │
│   │      │           │            │          │             │                │   │
│   │      ▼           ▼            ▼          ▼             ▼                │   │
│   │   ───┬───────────┬────────────┬──────────┬─────────────┬────────────►  │   │
│   │   Feb 1      Feb 1-28      Mar 1     Mar 1-8        Mar 8              │   │
│   │                                                                         │   │
│   │   Escrow      Escrow         Escrow     Escrow        Escrow           │   │
│   │   Created     Filling        Locked     Locked        Unlocked         │   │
│   │   K0          K0→K28,500     K28,500    K28,500       K28,500          │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   WHY THE 7-DAY HOLD?                                                           │
│   ═══════════════════                                                            │
│                                                                                  │
│   This protects everyone:                                                       │
│                                                                                  │
│   1. CHARGEBACKS: Customer might dispute charge with their bank                │
│      → If you already paid organizer, YOU lose the money                        │
│      → Hold period lets you handle disputes first                               │
│                                                                                  │
│   2. COMPLAINTS: Event might not deliver what was promised                      │
│      → "Event was terrible, I want refund!"                                     │
│      → Hold period gives time to investigate                                    │
│                                                                                  │
│   3. FRAUD: Someone might use stolen payment method                             │
│      → Real owner reports fraud after event                                     │
│      → Hold period protects against fraudulent organizers                       │
│                                                                                  │
│   4. NO-SHOWS: Venue might cancel, organizer disappears                         │
│      → You still have money to refund customers                                 │
│                                                                                  │
│                                                                                  │
│   PAYOUT PROCESS:                                                               │
│   ════════════════                                                               │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 1: Organizer requests payout in dashboard                       │   │
│   │                                                                         │   │
│   │   ┌─────────────────────────────────────────────────────────────────┐  │   │
│   │   │ "Music Festival 2026"                                           │  │   │
│   │   │ Status: COMPLETED                                               │  │   │
│   │   │ Escrow Balance: K28,500                                         │  │   │
│   │   │ Payout Eligible: Yes ✓                                          │  │   │
│   │   │                                                                 │  │   │
│   │   │ Select Bank Account: Zanaco ****4567                            │  │   │
│   │   │                                                                 │  │   │
│   │   │ [Request Payout K28,500]                                        │  │   │
│   │   └─────────────────────────────────────────────────────────────────┘  │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 2: Finance team reviews and approves                            │   │
│   │                                                                         │   │
│   │   Finance Dashboard:                                                    │   │
│   │   ┌─────────────────────────────────────────────────────────────────┐  │   │
│   │   │ Payout Request #PR-00123                                        │  │   │
│   │   │ ─────────────────────────────────────────────────────────────── │  │   │
│   │   │ Organizer: John's Events Ltd                                    │  │   │
│   │   │ Event: Music Festival 2026                                      │  │   │
│   │   │ Amount: K28,500                                                 │  │   │
│   │   │ Bank: Zanaco ****4567 (Verified ✓)                             │  │   │
│   │   │                                                                 │  │   │
│   │   │ Checks:                                                         │  │   │
│   │   │ ✓ Event completed                                               │  │   │
│   │   │ ✓ Hold period passed (8 days)                                   │  │   │
│   │   │ ✓ No pending disputes                                           │  │   │
│   │   │ ✓ Bank account verified                                         │  │   │
│   │   │                                                                 │  │   │
│   │   │ [Approve]  [Reject]                                             │  │   │
│   │   └─────────────────────────────────────────────────────────────────┘  │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 3: Bank transfer executed                                       │   │
│   │                                                                         │   │
│   │   Option A: Manual Transfer                                             │   │
│   │   • Finance person logs into bank portal                               │   │
│   │   • Creates transfer to Zanaco ****4567                                │   │
│   │   • Amount: K28,500                                                    │   │
│   │   • Reference: PAYOUT-PR00123                                          │   │
│   │                                                                         │   │
│   │   Option B: Automated via API                                          │   │
│   │   • System calls bank API or payment provider payout API               │   │
│   │   • Transfer initiated automatically                                    │   │
│   │                                                                         │   │
│   │   Either way:                                                           │   │
│   │   ┌─────────────┐                             ┌─────────────┐          │   │
│   │   │ Your Bank   │ ────── K28,500 ───────────► │ Organizer's │          │   │
│   │   │  (Stanbic)  │                             │   (Zanaco)  │          │   │
│   │   └─────────────┘                             └─────────────┘          │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   STEP 4: Update records                                               │   │
│   │                                                                         │   │
│   │   Escrow Account:                                                       │   │
│   │   Before: K28,500                                                       │   │
│   │   After:  K0 (CLOSED)                                                  │   │
│   │                                                                         │   │
│   │   Your Bank Account:                                                    │   │
│   │   Before: K100,000                                                     │   │
│   │   After:  K71,500 (-K28,500)                                           │   │
│   │                                                                         │   │
│   │   Platform Revenue:                                                     │   │
│   │   Unchanged - your K1,500 commission stays (from 60 tickets × K25)     │   │
│   │                                                                         │   │
│   │   Send notification to organizer: "Payout of K28,500 sent!"            │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 9: Your System's Account Structure

### The Complete Picture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    YOUR COMPLETE ACCOUNT STRUCTURE                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│                                                                                  │
│   ╔═══════════════════════════════════════════════════════════════════════════╗ │
│   ║                         REAL BANK ACCOUNTS                                 ║ │
│   ║                   (Physical money in actual banks)                         ║ │
│   ╠═══════════════════════════════════════════════════════════════════════════╣ │
│   ║                                                                            ║ │
│   ║   YOUR COMPANY'S BANK ACCOUNT                                              ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ 🏦 Stanbic Bank Zambia                                             │  ║ │
│   ║   │ Account: PML Tickets Ltd                                           │  ║ │
│   ║   │ Number: 9020001234567                                              │  ║ │
│   ║   │ Balance: K190,500                                                  │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ This is WHERE THE MONEY ACTUALLY SITS                              │  ║ │
│   ║   │ • Receives: Settlements from MTN/Airtel/Zamtel                     │  ║ │
│   ║   │ • Sends: Payouts to organizers, Refunds to customers               │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                  │
│                                      │                                          │
│                                      │ The K190,500 in your bank is            │
│                                      │ TRACKED by these virtual accounts:      │
│                                      ▼                                          │
│                                                                                  │
│   ╔═══════════════════════════════════════════════════════════════════════════╗ │
│   ║                        VIRTUAL ACCOUNTS                                    ║ │
│   ║              (Numbers in your database - tracking only)                    ║ │
│   ╠═══════════════════════════════════════════════════════════════════════════╣ │
│   ║                                                                            ║ │
│   ║   PLATFORM ACCOUNTS (TWO-STAGE COMMISSION MODEL)                           ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ 📊 PENDING COMMISSION ACCOUNT                                      │  ║ │
│   ║   │ MongoDB Collection: platform_accounts (type: PENDING)              │  ║ │
│   ║   │ Balance: K2,500                                                    │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ This is commission you WILL earn (but NOT YET!)                    │  ║ │
│   ║   │ • Credits: 5% of each ticket at purchase time                      │  ║ │
│   ║   │ • Debits: Cancel on refunds (simple!) OR move to earned            │  ║ │
│   ║   │ • When: Event completes + 7 days → moves to EARNED                 │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ 📊 EARNED REVENUE ACCOUNT                                          │  ║ │
│   ║   │ MongoDB Collection: platform_accounts (type: EARNED)               │  ║ │
│   ║   │ Balance: K7,500                                                    │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ This is YOUR ACTUAL PROFIT (commission you've EARNED!)             │  ║ │
│   ║   │ • Credits: From pending when event completes + 7 days              │  ║ │
│   ║   │ • Debits: Rare clawback on disputes after event                    │  ║ │
│   ║   │ • Can withdraw this to your operating account                      │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ║   EVENT ESCROW ACCOUNTS (one per event)                                    ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ 📊 MongoDB Collection: escrow_accounts                             │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ Event: "Music Festival 2026"                                       │  ║ │
│   ║   │ Organizer: John's Events Ltd                                       │  ║ │
│   ║   │ Balance: K95,000                                                   │  ║ │
│   ║   │ Status: ACTIVE (event not yet happened)                            │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ This money BELONGS TO John, but held until payout                  │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ Event: "Comedy Night"                                              │  ║ │
│   ║   │ Organizer: Laugh Factory Ltd                                       │  ║ │
│   ║   │ Balance: K45,000                                                   │  ║ │
│   ║   │ Status: PAYOUT_ELIGIBLE (event ended, hold passed)                 │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ Event: "Concert 2026"                                              │  ║ │
│   ║   │ Organizer: Music Productions Ltd                                   │  ║ │
│   ║   │ Balance: K40,500                                                   │  ║ │
│   ║   │ Status: LOCKED (event ended, in 7-day hold)                        │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                  │
│                                                                                  │
│   VERIFICATION (The Golden Rule):                                               │
│   ═══════════════════════════════                                                │
│                                                                                  │
│   Pending + Earned + All Escrows                          = Bank Balance        │
│   K2,500 + K7,500 + K95,000 + K45,000 + K40,500          = K190,500 ✓          │
│                                                                                  │
│   If this doesn't match, investigate immediately!                               │
│                                                                                  │
│                                                                                  │
│   ╔═══════════════════════════════════════════════════════════════════════════╗ │
│   ║                    EXTERNAL BANK ACCOUNTS                                  ║ │
│   ║            (Not your money - reference for payouts)                        ║ │
│   ╠═══════════════════════════════════════════════════════════════════════════╣ │
│   ║                                                                            ║ │
│   ║   ORGANIZER BANK ACCOUNTS                                                  ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ 📊 MongoDB Collection: bank_accounts                               │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ Owner: John's Events Ltd (org-001)                                 │  ║ │
│   ║   │ Bank: Zanaco                                                       │  ║ │
│   ║   │ Account: ****4567                                                  │  ║ │
│   ║   │ Verified: Yes ✓                                                    │  ║ │
│   ║   │                                                                    │  ║ │
│   ║   │ You DON'T hold money here - you TRANSFER to here                  │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ Owner: Laugh Factory Ltd (org-002)                                 │  ║ │
│   ║   │ Bank: FNB                                                          │  ║ │
│   ║   │ Account: ****8901                                                  │  ║ │
│   ║   │ Verified: Yes ✓                                                    │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 10: Putting It All Together

### Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    THE COMPLETE MONEY FLOW                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │                         PHASE 1: SALE                                   │   │
│   │                                                                         │   │
│   │   ┌──────────┐          ┌──────────┐          ┌──────────┐             │   │
│   │   │ Customer │──K500───►│ MTN MoMo │──K490───►│Your Bank │             │   │
│   │   │  Wallet  │          │          │          │ Account  │             │   │
│   │   └──────────┘          └──────────┘          └──────────┘             │   │
│   │                              │                      │                   │   │
│   │                              │                      │                   │   │
│   │                         K10 (fee)                   │                   │   │
│   │                         stays with MTN              │                   │   │
│   │                                                     │                   │   │
│   │                                              ┌──────┴──────┐            │   │
│   │                                              │             │            │   │
│   │                                              ▼             ▼            │   │
│   │                                        ┌──────────┐  ┌──────────┐       │   │
│   │                                        │ Escrow   │  │ PENDING  │       │   │
│   │                                        │ (Virtual)│  │Commission│       │   │
│   │                                        │  +K475   │  │  +K15    │       │   │
│   │                                        └──────────┘  │(not yet  │       │   │
│   │                                                      │ earned!) │       │   │
│   │                                                      └──────────┘       │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │                         PHASE 2: HOLD                                   │   │
│   │                                                                         │   │
│   │   Money sits in your bank, tracked by virtual accounts                 │   │
│   │                                                                         │   │
│   │   ┌──────────────────────────────────────────────────────────────────┐ │   │
│   │   │                                                                  │ │   │
│   │   │   YOUR BANK: K190,500 total                                     │ │   │
│   │   │                                                                  │ │   │
│   │   │   Broken down as:                                                │ │   │
│   │   │   ┌────────────┐ ┌────────────┐ ┌────────────┐                   │ │   │
│   │   │   │Event 1     │ │Event 2     │ │Event 3     │                   │ │   │
│   │   │   │Escrow      │ │Escrow      │ │Escrow      │                   │ │   │
│   │   │   │K95,000     │ │K45,000     │ │K40,500     │                   │ │   │
│   │   │   └────────────┘ └────────────┘ └────────────┘                   │ │   │
│   │   │                                                                  │ │   │
│   │   │   ┌────────────┐ ┌────────────┐                                  │ │   │
│   │   │   │Pending     │ │Earned      │  TWO-STAGE COMMISSION:          │ │   │
│   │   │   │Commission  │ │Revenue     │  Pending = not yet earned       │ │   │
│   │   │   │K2,500      │ │K7,500      │  Earned = yours to keep!        │ │   │
│   │   │   └────────────┘ └────────────┘                                  │ │   │
│   │   │                                                                  │ │   │
│   │   │   Total: K95,000 + K45,000 + K40,500 + K2,500 + K7,500 = K190,500 ✓│ │  │
│   │   │                                                                  │ │   │
│   │   └──────────────────────────────────────────────────────────────────┘ │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │                         PHASE 3: PAYOUT                                 │   │
│   │                                                                         │   │
│   │   After event + 7 days, organizer requests payout                      │   │
│   │                                                                         │   │
│   │   ┌──────────┐          ┌──────────┐          ┌──────────┐             │   │
│   │   │ Escrow   │──K45,000─►│Your Bank │──K45,000─►│Organizer │             │   │
│   │   │ (Virtual)│  (debit) │ Account  │ (transfer)│  Bank    │             │   │
│   │   │  -K45,000│          │ -K45,000 │          │ +K45,000 │             │   │
│   │   └──────────┘          └──────────┘          └──────────┘             │   │
│   │                                                                         │   │
│   │   After payout:                                                         │   │
│   │   • That escrow account: CLOSED (K0)                                   │   │
│   │   • Your bank: K190,500 - K45,000 = K145,500                           │   │
│   │   • Organizer's bank: +K45,000                                         │   │
│   │                                                                         │   │
│   │   Remaining virtual accounts:                                           │   │
│   │   K95,000 + K40,500 + K10,000 = K145,500 ✓ (matches bank)              │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │                    OPTIONAL: REFUND (if needed)                         │   │
│   │                                                                         │   │
│   │   ┌──────────┐          ┌──────────┐          ┌──────────┐             │   │
│   │   │ Escrow   │──K475───►│Your Bank │──K500───►│ Customer │             │   │
│   │   │ (Virtual)│  (debit) │ Account  │ (refund) │  Wallet  │             │   │
│   │   └──────────┘          └──────────┘          └──────────┘             │   │
│   │        │                                                                │   │
│   │   ┌────┴────┐                                                           │   │
│   │   │ PENDING │──K25───► If PENDING: Just cancel it (simple!)            │   │
│   │   │Commission│         If EARNED: Clawback (rare, after event)         │   │
│   │   └─────────┘                                                           │   │
│   │                                                                         │   │
│   │   K475 + K25 = K500 refund to customer ✓                               │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Summary: Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         KEY TAKEAWAYS                                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   1. REAL vs VIRTUAL ACCOUNTS                                                   │
│      • Real = Actual bank account where money sits                              │
│      • Virtual = Database records tracking who owns what portion                │
│      • Virtual accounts are like "sticky notes" on a shared jar                 │
│                                                                                  │
│   2. WHO GETS WHAT FROM A K500 TICKET                                           │
│      • Customer pays: K500                                                      │
│      • MTN keeps: ~K10 (payment processing fee)                                 │
│      • Platform (you) keeps: K15-25 (5% commission minus fees)                 │
│      • Organizer receives: K475 (after event + hold period)                     │
│                                                                                  │
│   3. MONEY FLOW TIMELINE                                                        │
│      • Day 0: Customer pays via mobile money                                    │
│      • Day 1-2: Settlement (money arrives in your bank)                         │
│      • Event day: Event happens                                                  │
│      • Event +7 days: Payout becomes eligible                                   │
│      • After approval: Transfer to organizer's bank                             │
│                                                                                  │
│   4. THE GOLDEN RULE                                                            │
│      Sum of all virtual accounts = Real bank balance                            │
│      If they don't match, something is wrong!                                   │
│                                                                                  │
│   5. ACCOUNTS YOU NEED (TWO-STAGE COMMISSION MODEL)                             │
│      • 1 Real bank account (your company's)                                     │
│      • 1 Virtual PENDING commission account (not yet earned)                   │
│      • 1 Virtual EARNED revenue account (yours to keep!)                        │
│      • 1 Virtual escrow account PER EVENT                                       │
│      • References to organizers' bank accounts (for payouts)                    │
│                                                                                  │
│   6. REFUNDS = MONEY GOING BACKWARDS (SIMPLIFIED!)                              │
│      • Debit from escrow (organizer's portion)                                  │
│      • If PENDING: Cancel commission (simple debit - no clawback!)             │
│      • If EARNED: Clawback from revenue (rare - after event)                   │
│      • Credit to customer (refund)                                              │
│                                                                                  │
│   7. TWO-STAGE COMMISSION = SIMPLER REFUNDS                                     │
│      • At purchase: Commission goes to PENDING (not earned yet)                │
│      • After event + 7 days: Commission moves to EARNED                        │
│      • Refund before event: Just cancel pending (no clawback!)                 │
│      • Why? Simpler accounting, proper revenue recognition!                                              │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Questions to Test Your Understanding

1. If your bank has K100,000 and you have 3 events with escrows of K30,000, K25,000, and K35,000, how much is your platform revenue?

2. A customer pays K1,000 for a ticket. MTN takes 2% fee. Your commission is 5%. How much goes to escrow?

3. Why can't you pay an organizer immediately after their event ends?

4. If a customer requests a 75% refund on a K400 ticket, how much comes from escrow and how much from platform revenue?

5. What's the difference between settlement and payout?

---

**Answers:**
1. K100,000 - K30,000 - K25,000 - K35,000 = K10,000 platform revenue (pending + earned)
2. K1,000 × 95% = K950 to escrow (organizer's share). You keep K50 commission minus MTN's K20 fee = K30
3. 7-day hold protects against chargebacks, disputes, and fraud
4. Refund = K400 × 75% = K300. From escrow: K380 × 75% = K285. From pending commission: K20 × 75% = K15
5. Settlement = payment provider sends money TO you. Payout = you send money TO organizer.

---

# PART 2: ADVANCED LESSONS

---

## Lesson 7: Understanding "Escrow" - Not All Virtual Accounts Are Escrow {#lesson-7-understanding-escrow}

### What Does "Escrow" Actually Mean?

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    WHAT IS ESCROW? (Simple Explanation)                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ESCROW = Money held by a THIRD PARTY until certain conditions are met        │
│                                                                                  │
│   Real-World Example:                                                           │
│   ══════════════════                                                             │
│                                                                                  │
│   You want to buy a house from a seller.                                        │
│                                                                                  │
│   ┌──────────┐      ┌──────────────┐      ┌──────────┐                         │
│   │   YOU    │─────►│  LAWYER      │─────►│  SELLER  │                         │
│   │  (Buyer) │      │ (Escrow      │      │          │                         │
│   │          │      │  Agent)      │      │          │                         │
│   └──────────┘      └──────────────┘      └──────────┘                         │
│        │                  │                    ▲                                │
│        │                  │                    │                                │
│        └── K500,000 ─────►│                    │                                │
│           "Hold this      │                    │                                │
│            until house    │                    │                                │
│            inspection     │                    │                                │
│            passes"        │                    │                                │
│                           │                    │                                │
│                           └── K500,000 ────────┘                                │
│                              "Inspection passed,                                │
│                               release to seller"                                │
│                                                                                  │
│   The lawyer holds the money IN ESCROW until conditions are met.               │
│   Neither buyer nor seller can touch it until then.                             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### In Your Ticketing System

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    ESCROW IN YOUR SYSTEM                                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ┌──────────┐      ┌──────────────┐      ┌──────────┐                         │
│   │  TICKET  │─────►│  YOUR        │─────►│  EVENT   │                         │
│   │  BUYER   │      │  PLATFORM    │      │ ORGANIZER│                         │
│   │          │      │ (Escrow      │      │          │                         │
│   └──────────┘      │  Holder)     │      └──────────┘                         │
│        │            └──────────────┘           ▲                                │
│        │                  │                    │                                │
│        └── K475 ─────────►│                    │                                │
│           "Hold this      │                    │                                │
│            until event    │                    │                                │
│            happens +      │                    │                                │
│            7 days"        │                    │                                │
│                           │                    │                                │
│                           └── K475 ────────────┘                                │
│                              "Event completed,                                  │
│                               hold period passed,                               │
│                               release to organizer"                             │
│                                                                                  │
│   YOU are the escrow holder! You hold organizer's money until                  │
│   conditions are met (event happens + 7 day hold period).                       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### NOT All Virtual Accounts Are Escrow!

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    ESCROW vs NON-ESCROW ACCOUNTS                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ╔═══════════════════════════════════════════════════════════════════════════╗ │
│   ║                    ESCROW ACCOUNTS (Holding for others)                   ║ │
│   ╠═══════════════════════════════════════════════════════════════════════════╣ │
│   ║                                                                            ║ │
│   ║   EVENT ESCROW ACCOUNTS                                                    ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ ✓ This IS escrow                                                   │  ║ │
│   ║   │ ✓ Money belongs to ORGANIZER                                       │  ║ │
│   ║   │ ✓ You are holding it FOR them                                      │  ║ │
│   ║   │ ✓ Released when conditions met (event + 7 days)                    │  ║ │
│   ║   │ ✓ You have FIDUCIARY duty (legal obligation to protect it)        │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                  │
│   ╔═══════════════════════════════════════════════════════════════════════════╗ │
│   ║                 NON-ESCROW ACCOUNTS (Your own money)                      ║ │
│   ╠═══════════════════════════════════════════════════════════════════════════╣ │
│   ║                                                                            ║ │
│   ║   PENDING COMMISSION ACCOUNT                                               ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ ✗ This is NOT escrow                                               │  ║ │
│   ║   │ ✓ Money WILL BE yours (when earned)                                │  ║ │
│   ║   │ ✓ You're tracking it as "pending" for accounting purposes         │  ║ │
│   ║   │ ✓ It's a REVENUE RECOGNITION account                               │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ║   EARNED REVENUE ACCOUNT                                                   ║ │
│   ║   ┌────────────────────────────────────────────────────────────────────┐  ║ │
│   ║   │ ✗ This is NOT escrow                                               │  ║ │
│   ║   │ ✓ Money IS yours                                                   │  ║ │
│   ║   │ ✓ You can spend it, withdraw it, whatever you want                 │  ║ │
│   ║   │ ✓ It's your PROFIT                                                 │  ║ │
│   ║   └────────────────────────────────────────────────────────────────────┘  ║ │
│   ║                                                                            ║ │
│   ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                  │
│   KEY DIFFERENCE:                                                               │
│   ═══════════════                                                                │
│   • ESCROW = Holding someone else's money (legal obligation!)                  │
│   • NON-ESCROW = Your own money (just tracking it)                             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Why This Matters Legally

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    LEGAL IMPLICATIONS OF ESCROW                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ⚠️  IMPORTANT: Escrow money is NOT yours!                                    │
│                                                                                  │
│   If your company goes bankrupt:                                                │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ ESCROW FUNDS (Event Escrows)                                            │   │
│   │ • CANNOT be used to pay your company's debts                            │   │
│   │ • Must be returned to organizers                                        │   │
│   │ • Protected by law (in most jurisdictions)                              │   │
│   │ • That's why they're called "escrow" - they're segregated              │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ YOUR REVENUE (Pending + Earned)                                         │   │
│   │ • CAN be used to pay your company's debts                               │   │
│   │ • This is your company's money                                          │   │
│   │ • Creditors can claim this                                              │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   BEST PRACTICE:                                                                │
│   Keep escrow funds in a SEPARATE bank account from operating funds!           │
│   (Though for simplicity, we track them virtually in one bank account)         │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 8: Two-Stage Commission Model - Pending vs Earned {#lesson-8-two-stage-commission-model}

### Why Two Stages?

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    THE PROBLEM WITH ONE-STAGE COMMISSION                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   WRONG WAY (One-Stage):                                                        │
│   ══════════════════════                                                         │
│                                                                                  │
│   Customer buys ticket → Commission immediately goes to "Revenue"               │
│                                                                                  │
│   Day 1:  Ticket sold for K500                                                  │
│           Commission K25 → REVENUE (yours!)                                     │
│                                                                                  │
│   Day 15: Customer requests refund (event is next month)                        │
│           Need to give back K500                                                │
│           Including the K25 commission!                                         │
│                                                                                  │
│   PROBLEM: You already counted K25 as revenue!                                  │
│   Now you have to "CLAW BACK" money you already "earned"                       │
│                                                                                  │
│   This creates:                                                                 │
│   • Complicated accounting                                                      │
│   • Negative entries in revenue                                                 │
│   • Auditors asking questions                                                   │
│   • Tax complications                                                           │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### The Two-Stage Solution

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TWO-STAGE COMMISSION MODEL                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   STAGE 1: PENDING (Not yet earned)                                             │
│   ═══════════════════════════════════                                            │
│                                                                                  │
│   When ticket is purchased:                                                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Commission K25 → PENDING COMMISSION ACCOUNT                             │   │
│   │                                                                         │   │
│   │ This money:                                                             │   │
│   │ • Is NOT recognized as revenue yet                                      │   │
│   │ • Could still be cancelled (if refund)                                  │   │
│   │ • Is a LIABILITY (you might have to give it back)                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   STAGE 2: EARNED (Now it's yours!)                                             │
│   ════════════════════════════════════                                           │
│                                                                                  │
│   When event completes + 7 days pass:                                           │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Commission K25: PENDING → EARNED REVENUE ACCOUNT                        │   │
│   │                                                                         │   │
│   │ This money:                                                             │   │
│   │ • IS recognized as revenue                                              │   │
│   │ • You EARNED it (event happened, you fulfilled your service)           │   │
│   │ • It's an ASSET (yours to keep)                                        │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Commission Status Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    COMMISSION STATUS LIFECYCLE                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│                         ┌─────────────┐                                         │
│                         │   PENDING   │                                         │
│                         │             │                                         │
│                         │ Commission  │                                         │
│                         │ calculated, │                                         │
│                         │ not earned  │                                         │
│                         └──────┬──────┘                                         │
│                                │                                                 │
│            ┌───────────────────┼───────────────────┐                            │
│            │                   │                   │                            │
│            ▼                   ▼                   ▼                            │
│   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                      │
│   │  CANCELLED  │     │   EARNED    │     │ CLAWED_BACK │                      │
│   │             │     │             │     │             │                      │
│   │ Refund      │     │ Event       │     │ Rare:       │                      │
│   │ BEFORE      │     │ completed   │     │ Dispute     │                      │
│   │ event       │     │ + 7 days    │     │ AFTER       │                      │
│   │             │     │             │     │ earned      │                      │
│   └─────────────┘     └─────────────┘     └─────────────┘                      │
│         │                   │                   │                               │
│         │                   │                   │                               │
│         ▼                   ▼                   ▼                               │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ CANCELLED: Simple debit from Pending (no clawback needed!)              │   │
│   │ EARNED: Move from Pending to Earned (now it's revenue)                  │   │
│   │ CLAWED_BACK: Debit from Earned (complex - affects recognized revenue)  │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   Most refunds are CANCELLED (before event) - which is simple!                 │
│   Very few are CLAWED_BACK (after event) - the complex case                    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### When Does Commission Move from Pending to Earned?

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    COMMISSION EARNING TRIGGER                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   TIMELINE:                                                                     │
│                                                                                  │
│   Ticket    Many more     Event        7-Day         Commission                 │
│   Sold      tickets sold  Happens      Hold          EARNED                     │
│     │           │            │           │              │                       │
│     ▼           ▼            ▼           ▼              ▼                       │
│   ──┬───────────┬────────────┬───────────┬──────────────┬──────────►           │
│   Jan 1      Jan 1-28     Feb 15     Feb 15-22       Feb 22                    │
│                                                                                  │
│   Status:    Status:      Status:     Status:        Status:                   │
│   PENDING    PENDING      PENDING     PENDING        EARNED                     │
│                                                                                  │
│                                                                                  │
│   AUTOMATED PROCESS (runs daily at 2 AM):                                       │
│   ════════════════════════════════════════                                       │
│                                                                                  │
│   1. Find all events where:                                                     │
│      • status = COMPLETED                                                       │
│      • eventDate + 7 days <= today                                              │
│                                                                                  │
│   2. For each qualifying event, find all tickets where:                         │
│      • commissionStatus = PENDING                                               │
│                                                                                  │
│   3. For each ticket:                                                           │
│      • Debit Pending Commission Account                                         │
│      • Credit Earned Revenue Account                                            │
│      • Update ticket.commissionStatus = EARNED                                  │
│      • Set ticket.commissionEarnedAt = now()                                    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 9: Refunds - Money Going Backwards (Updated) {#lesson-9-refunds}

This lesson was covered earlier but here's the updated version with two-stage commission:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    REFUND WITH TWO-STAGE COMMISSION                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   SCENARIO A: Refund BEFORE event (Commission is PENDING)                       │
│   ════════════════════════════════════════════════════════                       │
│                                                                                  │
│   This is the COMMON case (90%+ of refunds)                                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Original Sale:                                                        │   │
│   │   • Escrow: +K475                                                       │   │
│   │   • Pending Commission: +K25                                            │   │
│   │   • Ticket.commissionStatus = PENDING                                   │   │
│   │                                                                         │   │
│   │   Full Refund:                                                          │   │
│   │   • Escrow: -K475 (debit)                                               │   │
│   │   • Pending Commission: -K25 (CANCEL - simple debit!)                   │   │
│   │   • Ticket.commissionStatus = CANCELLED                                 │   │
│   │   • Customer gets: K500                                                 │   │
│   │                                                                         │   │
│   │   ✅ SIMPLE! We just cancelled something that was never earned.        │   │
│   │      No "clawback" needed. No negative revenue entries.                 │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   SCENARIO B: Refund AFTER event (Commission is EARNED) - RARE                  │
│   ═══════════════════════════════════════════════════════════════               │
│                                                                                  │
│   This happens in dispute cases (maybe 1% of refunds)                           │
│   Example: Customer claims event was fraudulent                                 │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Original Sale (commission already earned):                            │   │
│   │   • Escrow: K0 (already paid out to organizer!)                        │   │
│   │   • Earned Revenue: includes K25 from this ticket                       │   │
│   │   • Ticket.commissionStatus = EARNED                                    │   │
│   │                                                                         │   │
│   │   Dispute Refund:                                                       │   │
│   │   • Organizer must return K475 (separate process)                       │   │
│   │   • Earned Revenue: -K25 (CLAWBACK - complex!)                         │   │
│   │   • Ticket.commissionStatus = CLAWED_BACK                               │   │
│   │   • Customer gets: K500                                                 │   │
│   │                                                                         │   │
│   │   ⚠️ COMPLEX! We have to "take back" already-earned revenue.           │   │
│   │      Creates negative entries. Affects financial reports.               │   │
│   │      This is why 7-day hold exists - to minimize this case!            │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 10: Refund Processing Fees - Who Pays? {#lesson-10-refund-processing-fees}

### The Hidden Cost of Refunds

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    THE REFUND FEE PROBLEM                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ORIGINAL PURCHASE (K500 ticket):                                              │
│   ═══════════════════════════════                                                │
│                                                                                  │
│   Customer pays K500 via MTN MoMo                                               │
│   MTN keeps ~K10 (2% fee)                                                       │
│   You receive K490 in your bank                                                 │
│                                                                                  │
│   You allocate:                                                                 │
│   • K475 to Escrow (organizer's share)                                         │
│   • K15 to Pending Commission (your share after fee)                           │
│                                                                                  │
│                                                                                  │
│   NOW: CUSTOMER REQUESTS FULL REFUND                                            │
│   ════════════════════════════════════                                           │
│                                                                                  │
│   Customer expects K500 back!                                                   │
│                                                                                  │
│   But wait... you only have K490 (MTN kept K10)                                │
│                                                                                  │
│   AND... to send K500 back to customer, MTN charges ANOTHER fee!               │
│   Refund fee: ~K10 (another 2%)                                                │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   TOTAL FEES ON THIS REFUND:                                            │   │
│   │                                                                         │   │
│   │   Original fee:     K10 (paid by customer, absorbed by platform)        │   │
│   │   Refund fee:       K10 (who pays this?)                                │   │
│   │   ─────────────────────────────────────                                 │   │
│   │   Total:            K20 in fees!                                        │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   THE QUESTION: Who absorbs these K20 in fees?                                 │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### The Three Options

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    WHO PAYS REFUND FEES?                                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   OPTION A: PLATFORM ABSORBS ALL FEES (Most Customer-Friendly)                  │
│   ═══════════════════════════════════════════════════════════                    │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Customer paid:          K500                                          │   │
│   │   Customer receives:      K500 (full refund!)                           │   │
│   │                                                                         │   │
│   │   Platform pays:                                                        │   │
│   │   • K10 original fee (already paid)                                     │   │
│   │   • K10 refund fee (new)                                                │   │
│   │   ─────────────────────                                                 │   │
│   │   Total platform loss: K20                                              │   │
│   │                                                                         │   │
│   │   Plus: K15 commission cancelled (was pending, not lost)                │   │
│   │                                                                         │   │
│   │   ACTUAL CASH FLOW:                                                     │   │
│   │   Escrow contributes:     K475                                          │   │
│   │   Platform contributes:   K25 (commission + K10 extra!)                 │   │
│   │   ─────────────────────                                                 │   │
│   │   Total sent to customer: K500                                          │   │
│   │                                                                         │   │
│   │   ⚠️ Platform LOSES K10 on every refund!                               │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   OPTION B: CUSTOMER ABSORBS FEES (Most Platform-Friendly)                      │
│   ═════════════════════════════════════════════════════════                      │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Customer paid:          K500                                          │   │
│   │   Customer receives:      K480 (K500 - K10 original - K10 refund)       │   │
│   │                                                                         │   │
│   │   Platform pays:          K0 (no loss!)                                 │   │
│   │                                                                         │   │
│   │   ACTUAL CASH FLOW:                                                     │   │
│   │   Escrow contributes:     K475                                          │   │
│   │   Platform contributes:   K15 (just the pending commission)             │   │
│   │   MTN takes:              K10 (refund fee)                              │   │
│   │   ─────────────────────                                                 │   │
│   │   Customer receives:      K480                                          │   │
│   │                                                                         │   │
│   │   ⚠️ Customers may complain "I paid K500, I should get K500!"          │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   OPTION C: SPLIT THE FEES (Compromise)                                         │
│   ══════════════════════════════════════                                         │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Customer paid:          K500                                          │   │
│   │   Customer receives:      K490 (loses K10 refund fee only)              │   │
│   │                                                                         │   │
│   │   Platform pays:          K10 (original fee only)                       │   │
│   │                                                                         │   │
│   │   "We'll refund the ticket price, but processing fees are non-refundable"
│   │                                                                         │   │
│   │   This is MOST COMMON in the industry!                                  │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Recommendation for Your System

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    RECOMMENDED APPROACH                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   USE OPTION C (Industry Standard) with CLEAR DISCLOSURE:                       │
│                                                                                  │
│   At checkout, show:                                                            │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │   Ticket Price:         K500.00                                         │   │
│   │   Processing Fee:       K10.00 (non-refundable)                         │   │
│   │   ─────────────────────────────                                         │   │
│   │   Total:                K510.00                                         │   │
│   │                                                                         │   │
│   │   [✓] I understand processing fees are non-refundable                  │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   On refund:                                                                    │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │   Original Purchase:    K510.00                                         │   │
│   │   Processing Fee:       -K10.00 (non-refundable)                        │   │
│   │   ─────────────────────────────                                         │   │
│   │   Refund Amount:        K500.00                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   FINANCIAL IMPACT:                                                             │
│   • Platform keeps the K10 original fee (covers refund processing)             │
│   • Customer gets back the ticket price (K500)                                 │
│   • Organizer's K475 goes back to customer                                     │
│   • Platform's K15 pending commission is cancelled                             │
│   • Net platform position: K10 - K10 = K0 (break even on fees!)               │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 11: Payouts - Giving Organizers Their Money {#lesson-11-payouts}

(This was covered in earlier lessons - the key update is that commission moves from PENDING to EARNED at the same time payouts become eligible)

---

## Lesson 12: Event Cancellation - The Full Financial Impact {#lesson-12-event-cancellation}

### What Happens When an Event is Cancelled?

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    EVENT CANCELLATION - COMPLETE FINANCIAL IMPACT                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   SCENARIO: Music Festival cancelled                                            │
│   Tickets sold: 200 tickets × K500 = K100,000 total                            │
│                                                                                  │
│   CURRENT STATE (before cancellation):                                          │
│   ══════════════════════════════════════                                         │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Event Escrow Account:           K95,000 (200 × K475)                    │   │
│   │ Pending Commission Account:     K5,000  (200 × K25)                     │   │
│   │                                                                         │   │
│   │ Commission Status: ALL tickets are PENDING (event hasn't happened)     │   │
│   │                                                                         │   │
│   │ Note: We received K98,000 after MTN fees (200 × K490)                  │   │
│   │       We're holding K100,000 in virtual accounts                        │   │
│   │       The K2,000 difference? MTN already took it!                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   CANCELLATION PROCESS:                                                         │
│   ═════════════════════                                                          │
│                                                                                  │
│   FOR EACH OF THE 200 TICKETS:                                                  │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ 1. Debit Escrow:                    -K475                               │   │
│   │ 2. Cancel Pending Commission:       -K25 (status → CANCELLED)           │   │
│   │ 3. Initiate Refund to Customer:     K500                                │   │
│   │                                                                         │   │
│   │ But wait... refund processing fee!                                      │   │
│   │ 4. MTN refund fee:                  -K10                                │   │
│   │                                                                         │   │
│   │ Customer receives:                  K500 (or K490 if fee passed on)     │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   TOTAL FINANCIAL IMPACT:                                                       │
│   ════════════════════════                                                       │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Money we received from MTN:        K98,000                            │   │
│   │   Money we must refund (200 × K500): K100,000                           │   │
│   │   ────────────────────────────────────────                              │   │
│   │   Shortfall:                         K2,000 (original fees)             │   │
│   │                                                                         │   │
│   │   Plus refund fees (200 × K10):      K2,000                             │   │
│   │   ────────────────────────────────────────                              │   │
│   │   TOTAL PLATFORM LOSS:               K4,000                             │   │
│   │                                                                         │   │
│   │   WHO CAUSED THIS? The organizer who cancelled!                         │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Who Should Bear the Cancellation Cost?

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    CANCELLATION FEE RESPONSIBILITY                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   THE FAIRNESS QUESTION:                                                        │
│   ══════════════════════                                                         │
│                                                                                  │
│   The organizer cancelled the event, causing all these fees.                    │
│   Should the platform absorb K4,000 in losses?                                  │
│   Or should the organizer pay for the mess they created?                        │
│                                                                                  │
│                                                                                  │
│   OPTION 1: Platform absorbs all fees                                           │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ • Platform loses K4,000                                                 │   │
│   │ • Organizer loses nothing (their escrow goes to refunds)                │   │
│   │ • Customer gets full refund                                             │   │
│   │                                                                         │   │
│   │ Problem: Organizers have no incentive to avoid cancellation!           │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   OPTION 2: Organizer pays cancellation penalty (RECOMMENDED)                   │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │ Terms of Service include:                                               │   │
│   │ "If organizer cancels event, platform may deduct processing fees        │   │
│   │  from organizer's escrow balance before refunding customers."           │   │
│   │                                                                         │   │
│   │ Calculation:                                                            │   │
│   │ Total escrow:              K95,000                                      │   │
│   │ Processing fee penalty:    -K4,000 (200 tickets × K20)                  │   │
│   │ ─────────────────────────────────                                       │   │
│   │ Available for refunds:     K91,000                                      │   │
│   │                                                                         │   │
│   │ Per customer refund:       K455 (K91,000 ÷ 200)                         │   │
│   │                                                                         │   │
│   │ OR: Organizer pays K4,000 separately, customers get full K500          │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   OPTION 3: Customer absorbs refund fee only                                    │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ • Customer receives K490 (K500 - K10 refund fee)                        │   │
│   │ • Platform absorbs K10 original fee per ticket                          │   │
│   │ • Total platform loss: K2,000                                           │   │
│   │ • Organizer's escrow fully used for refunds                             │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   BEST PRACTICE:                                                                │
│   ═══════════════                                                                │
│   Most platforms use OPTION 2 with a cap:                                       │
│   • First 10% of tickets: Platform absorbs fees (goodwill)                     │
│   • Above 10%: Organizer penalty applies                                        │
│   • OR: Flat cancellation penalty (e.g., 5% of total sales)                    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

# PART 3: DOUBLE-ENTRY BOOKKEEPING

---

## Lesson 13: What is Double-Entry Bookkeeping? {#lesson-13-double-entry-bookkeeping-basics}

### The Fundamental Rule

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    DOUBLE-ENTRY BOOKKEEPING BASICS                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   THE GOLDEN RULE:                                                              │
│   ════════════════                                                               │
│                                                                                  │
│   For EVERY transaction:                                                        │
│                                                                                  │
│           DEBITS = CREDITS                                                      │
│                                                                                  │
│   Every time money moves, TWO things happen:                                    │
│   1. One account is DEBITED                                                     │
│   2. Another account is CREDITED                                                │
│                                                                                  │
│   The amounts MUST be equal!                                                    │
│                                                                                  │
│                                                                                  │
│   WHY?                                                                          │
│   ═════                                                                          │
│   Because money doesn't appear or disappear - it just MOVES!                   │
│                                                                                  │
│   If you give me K100:                                                          │
│   • Your wallet: -K100 (you have less)                                         │
│   • My wallet: +K100 (I have more)                                             │
│   • Total in the world: unchanged!                                             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Simple Example

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    SIMPLE EXAMPLE: BUYING LUNCH                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   You buy lunch for K50                                                         │
│                                                                                  │
│   SINGLE-ENTRY (Bad):                                                           │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Cash: -K50                                                              │   │
│   │                                                                         │   │
│   │ That's it. No record of WHY you spent K50.                             │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   DOUBLE-ENTRY (Good):                                                          │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account          │  Debit   │  Credit  │                               │   │
│   │──────────────────│──────────│──────────│                               │   │
│   │ Food Expense     │  K50     │          │  (You got lunch - expense ↑)  │   │
│   │ Cash             │          │  K50     │  (You spent cash - asset ↓)   │   │
│   │──────────────────│──────────│──────────│                               │   │
│   │ TOTAL            │  K50     │  K50     │  BALANCED! ✓                  │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   Now you know:                                                                 │
│   • K50 was spent (Cash credit)                                                │
│   • It was spent on food (Food Expense debit)                                  │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 14: Debit vs Credit - The Confusing Part Explained {#lesson-14-debit-vs-credit}

### Why Debit and Credit Are Confusing

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    THE CONFUSION EXPLAINED                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   YOU THINK:                                                                    │
│   ══════════                                                                     │
│   "When I put money IN my bank, they say CREDIT"                               │
│   "When I take money OUT, they say DEBIT"                                      │
│                                                                                  │
│   "So CREDIT = money IN, DEBIT = money OUT... right?"                          │
│                                                                                  │
│   WRONG! (sort of)                                                              │
│                                                                                  │
│   When the bank says "we credited your account", they're speaking              │
│   from THEIR perspective, not yours!                                            │
│                                                                                  │
│   FROM THE BANK'S VIEW:                                                         │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Your deposit = They OWE you more money                                  │   │
│   │              = Their liability INCREASED                                │   │
│   │              = They CREDIT their liability account                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   FROM YOUR VIEW:                                                               │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Your deposit = You HAVE more money                                      │   │
│   │              = Your asset INCREASED                                     │   │
│   │              = You DEBIT your bank account (asset)                      │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   FORGET what the bank says. Focus on the accounting rules.                     │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### The Real Rules

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    DEBIT AND CREDIT RULES                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ╔═══════════════════════════════════════════════════════════════════════════╗ │
│   ║                    ACCOUNT TYPES AND RULES                                 ║ │
│   ╠═══════════════════════════════════════════════════════════════════════════╣ │
│   ║                                                                            ║ │
│   ║   Account Type     │  DEBIT means    │  CREDIT means                      ║ │
│   ║   ─────────────────│─────────────────│─────────────────                   ║ │
│   ║   ASSETS           │  INCREASE ↑     │  DECREASE ↓                        ║ │
│   ║   (things you own) │                 │                                     ║ │
│   ║   ─────────────────│─────────────────│─────────────────                   ║ │
│   ║   LIABILITIES      │  DECREASE ↓     │  INCREASE ↑                        ║ │
│   ║   (things you owe) │                 │                                     ║ │
│   ║   ─────────────────│─────────────────│─────────────────                   ║ │
│   ║   REVENUE          │  DECREASE ↓     │  INCREASE ↑                        ║ │
│   ║   (money earned)   │                 │                                     ║ │
│   ║   ─────────────────│─────────────────│─────────────────                   ║ │
│   ║   EXPENSES         │  INCREASE ↑     │  DECREASE ↓                        ║ │
│   ║   (money spent)    │                 │                                     ║ │
│   ║   ─────────────────│─────────────────│─────────────────                   ║ │
│   ║   EQUITY           │  DECREASE ↓     │  INCREASE ↑                        ║ │
│   ║   (owner's share)  │                 │                                     ║ │
│   ║                                                                            ║ │
│   ╚═══════════════════════════════════════════════════════════════════════════╝ │
│                                                                                  │
│                                                                                  │
│   MEMORY TRICK (DEALER):                                                        │
│   ═══════════════════════                                                        │
│                                                                                  │
│   Debit increases:   D - Dividends                                              │
│                      E - Expenses                                               │
│                      A - Assets                                                 │
│                                                                                  │
│   Credit increases:  L - Liabilities                                            │
│                      E - Equity                                                 │
│                      R - Revenue                                                │
│                                                                                  │
│                      D E A - L E R                                              │
│                      ─────   ─────                                              │
│                      DEBIT   CREDIT                                             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Your System's Account Types

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    YOUR SYSTEM'S ACCOUNTS CLASSIFIED                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ASSETS (Debit to increase):                                                   │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ • Bank Account (real money you have)                                    │   │
│   │ • Accounts Receivable (if any - money owed TO you)                     │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   LIABILITIES (Credit to increase):                                             │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ • Event Escrow Accounts (money you owe TO organizers)                   │   │
│   │ • Pending Commission (might have to return on refund - liability!)     │   │
│   │ • Customer Refunds Payable (if processing)                              │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   REVENUE (Credit to increase):                                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ • Earned Commission Revenue (money you've EARNED)                       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   EXPENSES (Debit to increase):                                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ • Payment Processing Fees (MTN/Airtel fees you absorb)                  │   │
│   │ • Refund Processing Fees (fees on refunds)                              │   │
│   │ • Commission Clawback Expense (rare - when earned commission refunded) │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   ⚠️ IMPORTANT INSIGHT:                                                        │
│   ══════════════════════                                                         │
│   PENDING COMMISSION is a LIABILITY, not revenue!                              │
│   Why? Because you might have to give it back (if refund before event).        │
│   Only EARNED COMMISSION is revenue.                                           │
│                                                                                  │
│   This is another reason the two-stage model makes sense!                       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 15: Every Transaction in Your System - Complete Journal Entries {#lesson-15-complete-journal-entries}

### Transaction 1: Customer Buys a Ticket (K500)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION 1: TICKET PURCHASE                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   EVENT: Customer pays K500 for ticket via MTN (2% fee = K10)                  │
│                                                                                  │
│   JOURNAL ENTRY:                                                                │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Bank Account (Asset)           │  K490    │          │ Money received   │   │
│   │ Payment Processing Fee (Exp)   │  K10     │          │ MTN's cut        │   │
│   │ Event Escrow (Liability)       │          │  K475    │ Owed to organizer│   │
│   │ Pending Commission (Liability) │          │  K25     │ Might refund     │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K500    │  K500    │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   BREAKDOWN:                                                                    │
│   • We received K490 (asset ↑ = debit)                                         │
│   • We expensed K10 in fees (expense ↑ = debit)                                │
│   • We now owe organizer K475 (liability ↑ = credit)                           │
│   • We have K25 pending commission (liability ↑ = credit)                      │
│                                                                                  │
│   WHY is Pending Commission a LIABILITY?                                        │
│   Because we might have to return it! It's not truly ours yet.                 │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Transaction 2: Event Completes + 7 Days (Commission Earned)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION 2: COMMISSION EARNED                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   EVENT: Event happened, 7 days passed, commission now earned                   │
│                                                                                  │
│   JOURNAL ENTRY:                                                                │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Pending Commission (Liability) │  K25     │          │ No longer owed   │   │
│   │ Commission Revenue (Revenue)   │          │  K25     │ NOW it's earned! │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K25     │  K25     │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   BREAKDOWN:                                                                    │
│   • Pending liability decreases (debit - we don't owe it back anymore)         │
│   • Revenue increases (credit - we earned it!)                                  │
│                                                                                  │
│   This entry moves commission from LIABILITY to REVENUE.                        │
│   Now it shows up on your income statement as earned income!                   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Transaction 3: Organizer Payout (K475)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION 3: ORGANIZER PAYOUT                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   EVENT: Organizer requests payout, we transfer K475 to their bank             │
│                                                                                  │
│   JOURNAL ENTRY:                                                                │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Event Escrow (Liability)       │  K475    │          │ No longer owed   │   │
│   │ Bank Account (Asset)           │          │  K475    │ Money sent out   │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K475    │  K475    │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   BREAKDOWN:                                                                    │
│   • Escrow liability decreases (debit - we paid what we owed)                  │
│   • Bank account decreases (credit - money left the bank)                       │
│                                                                                  │
│   After this entry:                                                             │
│   • Escrow account: K0 (closed)                                                │
│   • Organizer got their K475                                                   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Transaction 4: Refund BEFORE Event (Commission PENDING)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION 4: REFUND (PENDING COMMISSION)                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   EVENT: Customer requests full refund before event                             │
│          Refund fee: K10 (customer absorbs)                                     │
│                                                                                  │
│   JOURNAL ENTRY:                                                                │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Event Escrow (Liability)       │  K475    │          │ No longer owed   │   │
│   │ Pending Commission (Liability) │  K25     │          │ Cancelled        │   │
│   │ Bank Account (Asset)           │          │  K490    │ Money refunded   │   │
│   │ Payment Processing Fee (Exp)   │          │  K10     │ Fee REVERSED*    │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K500    │  K500    │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   * If customer absorbs refund fee, we send K490 and reverse the K10 expense  │
│     because effectively the customer paid that fee for nothing.                │
│                                                                                  │
│   ALTERNATIVE (if platform absorbs refund fee):                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Event Escrow (Liability)       │  K475    │          │ No longer owed   │   │
│   │ Pending Commission (Liability) │  K25     │          │ Cancelled        │   │
│   │ Refund Processing Fee (Exp)    │  K10     │          │ Platform pays    │   │
│   │ Bank Account (Asset)           │          │  K510    │ Full refund sent │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K510    │  K510    │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   NOTE: No revenue account is touched! Commission was PENDING (liability).    │
│   We just reduced the liability. Simple!                                        │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Transaction 5: Refund AFTER Event (Commission EARNED) - Rare

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION 5: REFUND (EARNED COMMISSION) - DISPUTE           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   EVENT: Dispute after event - must refund even though commission earned        │
│          Organizer has already been paid out!                                   │
│                                                                                  │
│   This is COMPLEX. Let's break it down:                                         │
│                                                                                  │
│   STEP 1: Record the refund to customer                                         │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Commission Clawback (Expense)  │  K25     │          │ Lost revenue     │   │
│   │ Refund Processing Fee (Exp)    │  K10     │          │ Platform pays    │   │
│   │ Accounts Receivable (Asset)    │  K475    │          │ Organizer owes us│   │
│   │ Bank Account (Asset)           │          │  K510    │ Refund sent      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K510    │  K510    │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   STEP 2: When organizer returns the K475                                       │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Bank Account (Asset)           │  K475    │          │ Organizer repaid │   │
│   │ Accounts Receivable (Asset)    │          │  K475    │ No longer owed   │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K475    │  K475    │ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   NOTE: Commission Clawback is an EXPENSE that reduces your profits.           │
│   This is the "complex case" that the two-stage model minimizes!               │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Transaction 6: Event Cancellation (Mass Refund)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTION 6: EVENT CANCELLATION                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   EVENT: Organizer cancels, 200 tickets must be refunded                        │
│          Platform absorbs refund fees (K10 × 200 = K2,000)                      │
│                                                                                  │
│   JOURNAL ENTRY (aggregate):                                                    │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Account                        │  Debit   │  Credit  │ Explanation      │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ Event Escrow (Liability)       │  K95,000 │          │ Cancel escrow    │   │
│   │ Pending Commission (Liability) │  K5,000  │          │ Cancel all comm  │   │
│   │ Refund Processing Fee (Exp)    │  K2,000  │          │ Platform absorbs │   │
│   │ Bank Account (Asset)           │          │  K102,000│ Refunds sent     │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K102,000│  K102,000│ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   WAIT! We only have K98,000 in the bank for this event!                       │
│   (K100,000 received - K2,000 original fees MTN kept)                          │
│                                                                                  │
│   The extra K4,000 comes from platform's operating funds.                       │
│   This is why cancellations are EXPENSIVE for the platform!                    │
│                                                                                  │
│   BETTER APPROACH (customer absorbs refund fee):                                │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Event Escrow (Liability)       │  K95,000 │          │                  │   │
│   │ Pending Commission (Liability) │  K5,000  │          │                  │   │
│   │ Bank Account (Asset)           │          │  K98,000 │ K490 × 200       │   │
│   │ Payment Proc Fee (Exp) REVERSE │          │  K2,000  │ Fees "returned"  │   │
│   │────────────────────────────────│──────────│──────────│──────────────────│   │
│   │ TOTAL                          │  K100,000│  K100,000│ BALANCED ✓       │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Summary: All Journal Entry Types

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    SUMMARY OF ALL JOURNAL ENTRIES                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   TRANSACTION             │ DEBITS                    │ CREDITS                 │
│   ────────────────────────│───────────────────────────│─────────────────────────│
│   Ticket Purchase         │ Bank (Asset)              │ Escrow (Liability)      │
│                           │ Processing Fee (Expense)  │ Pending Comm (Liab)     │
│   ────────────────────────│───────────────────────────│─────────────────────────│
│   Commission Earned       │ Pending Comm (Liability)  │ Commission Rev (Revenue)│
│   ────────────────────────│───────────────────────────│─────────────────────────│
│   Organizer Payout        │ Escrow (Liability)        │ Bank (Asset)            │
│   ────────────────────────│───────────────────────────│─────────────────────────│
│   Refund (Pending Comm)   │ Escrow (Liability)        │ Bank (Asset)            │
│                           │ Pending Comm (Liability)  │                         │
│   ────────────────────────│───────────────────────────│─────────────────────────│
│   Refund (Earned Comm)    │ Clawback Exp (Expense)    │ Bank (Asset)            │
│                           │ A/R from Organizer        │                         │
│   ────────────────────────│───────────────────────────│─────────────────────────│
│   Event Cancellation      │ Escrow (Liability)        │ Bank (Asset)            │
│                           │ Pending Comm (Liability)  │                         │
│                           │ Refund Fee (Expense)      │                         │
│   ────────────────────────│───────────────────────────│─────────────────────────│
│                                                                                  │
│   GOLDEN RULE: DEBITS ALWAYS = CREDITS                                          │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Lesson 16: Settlement - When Money Actually Arrives {#lesson-16-settlement-process}

### Understanding Settlement

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    WHAT IS SETTLEMENT?                                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Settlement = When the payment provider transfers collected money to your bank │
│                                                                                  │
│   TIMELINE:                                                                     │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                         │   │
│   │   Day 0 (10:00 AM)        Day 1 (EOD)         Day 2 (Morning)          │   │
│   │        │                      │                     │                   │   │
│   │        ▼                      ▼                     ▼                   │   │
│   │   ┌─────────┐            ┌─────────┐          ┌─────────┐              │   │
│   │   │Customer │            │MTN Batch│          │ Money   │              │   │
│   │   │Pays     │            │Process  │          │ Arrives │              │   │
│   │   │K500     │            │         │          │ In Your │              │   │
│   │   │via MTN  │            │         │          │ Bank    │              │   │
│   │   └─────────┘            └─────────┘          └─────────┘              │   │
│   │                                                                         │   │
│   │   Customer's    MTN collects     MTN settles    You have               │   │
│   │   wallet       all payments      to your        K490 in                │   │
│   │   debited      for the day       bank           bank                   │   │
│   │   -K500        and processes                                            │   │
│   │                                                                         │   │
│   │   THIS IS T+1 SETTLEMENT (Transaction + 1 day)                         │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Settlement Terms

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    SETTLEMENT TERMINOLOGY                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   T+0 (Same Day):                                                               │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Money arrives in your bank on the SAME DAY as the transaction           │   │
│   │ • Very rare for mobile money                                            │   │
│   │ • Usually only for premium/enterprise accounts                          │   │
│   │ • Higher fees                                                            │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   T+1 (Next Day):                                                               │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Money arrives in your bank the NEXT BUSINESS DAY                         │   │
│   │ • Most common for MTN/Airtel in Zambia                                  │   │
│   │ • Standard settlement window                                             │   │
│   │ • Transactions before cutoff (e.g., 5 PM) settle next day               │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   T+2 (Two Days):                                                               │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Money arrives TWO business days after transaction                        │   │
│   │ • Common for card payments                                               │   │
│   │ • Weekends/holidays extend this                                          │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│   T+7 or Longer:                                                                │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Some aggregators/payment providers hold funds longer                     │   │
│   │ • New merchant accounts                                                  │   │
│   │ • High-risk merchants                                                    │   │
│   │ • International payments                                                 │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   IMPORTANT:                                                                    │
│   ══════════                                                                     │
│   "T" means the transaction date, NOT calendar date.                            │
│                                                                                  │
│   If transaction is Friday 6 PM (after cutoff):                                 │
│   • T = Monday (next business day is considered T)                              │
│   • T+1 = Tuesday                                                               │
│   • You wait 4 calendar days (Fri → Tue)!                                      │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Your System's Settlement Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    YOUR SETTLEMENT FLOW                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   WHEN TICKET IS PURCHASED (IMMEDIATELY):                                       │
│   ════════════════════════════════════════                                       │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ Your System Records:                                                    │   │
│   │                                                                         │   │
│   │ PaymentIntent {                                                         │   │
│   │   status: "SUCCEEDED",                                                  │   │
│   │   amount: K500,                                                         │   │
│   │   settlementStatus: "PENDING",  // Money not in bank yet!              │   │
│   │   expectedSettlementDate: "2026-03-03",  // T+1                        │   │
│   │ }                                                                       │   │
│   │                                                                         │   │
│   │ Ticket {                                                                │   │
│   │   status: "PURCHASED",  // Ticket is valid!                            │   │
│   │ }                                                                       │   │
│   │                                                                         │   │
│   │ Escrow {                                                                │   │
│   │   balance: K475,                                                        │   │
│   │   settlementStatus: "PENDING",                                          │   │
│   │ }                                                                       │   │
│   │                                                                         │   │
│   │ The VIRTUAL accounts are updated immediately.                           │   │
│   │ The REAL bank account gets money later (T+1).                          │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│                                                                                  │
│   WHEN SETTLEMENT HAPPENS (T+1):                                                │
│   ══════════════════════════════                                                 │
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │ 1. MTN sends settlement file/report                                     │   │
│   │    "Yesterday's transactions: K50,000 total, settling to account XXX"  │   │
│   │                                                                         │   │
│   │ 2. Money arrives in your bank account                                   │   │
│   │    Bank balance: +K49,000 (after MTN's 2% fee)                          │   │
│   │                                                                         │   │
│   │ 3. Your system reconciles:                                              │   │
│   │    - Match settlement report to PaymentIntents                          │   │
│   │    - Update settlementStatus: "PENDING" → "SETTLED"                    │   │
│   │    - Flag any discrepancies                                             │   │
│   │                                                                         │   │
│   │ 4. Journal Entry:                                                       │   │
│   │    Dr: Bank Account       K49,000                                       │   │
│   │    Dr: Processing Fee     K1,000                                        │   │
│   │    Cr: Settlement Pending K50,000                                       │   │
│   │                                                                         │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Settlement by Provider (Zambia)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    SETTLEMENT WINDOWS BY PROVIDER                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   Provider         │ Settlement │ Cutoff Time │ Notes                          │
│   ─────────────────│────────────│─────────────│─────────────────────────────── │
│   MTN MoMo         │ T+1        │ 5:00 PM     │ Most common in Zambia          │
│   Airtel Money     │ T+1        │ 5:00 PM     │ Similar to MTN                 │
│   Zamtel Kwacha    │ T+1 to T+2 │ 4:00 PM     │ May vary                       │
│   Paychangu        │ T+1        │ 6:00 PM     │ Aggregator (combines above)    │
│   Visa/Mastercard  │ T+2        │ Midnight    │ Card payments take longer      │
│                                                                                  │
│   WEEKEND EXAMPLE:                                                              │
│   ═════════════════                                                              │
│                                                                                  │
│   Friday 3:00 PM:   Customer pays K500                                         │
│   Friday 5:00 PM:   Cutoff - included in Friday batch                          │
│   Saturday:         Bank closed, no settlement                                  │
│   Sunday:           Bank closed, no settlement                                  │
│   Monday 10:00 AM:  MTN settles to your bank                                   │
│                                                                                  │
│   Total wait: ~3 calendar days (but only T+1 business day)                     │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Quiz: Test Your Understanding

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    QUIZ QUESTIONS                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   1. What is the difference between an escrow account and your earned           │
│      revenue account?                                                           │
│                                                                                  │
│   2. If a customer pays K500 for a ticket and requests a refund before          │
│      the event, what happens to the K25 commission?                             │
│      a) It's clawed back from revenue                                           │
│      b) It's cancelled from pending (no clawback)                               │
│      c) The organizer pays it                                                   │
│                                                                                  │
│   3. Why is Pending Commission a LIABILITY, not Revenue?                        │
│                                                                                  │
│   4. A customer pays via MTN at 6 PM Friday. With T+1 settlement,              │
│      when does the money arrive in your bank?                                   │
│                                                                                  │
│   5. Write the journal entry for a K500 ticket purchase                        │
│      (K10 MTN fee, K475 escrow, K25 pending commission)                        │
│                                                                                  │
│   6. Event is cancelled with 100 tickets sold (K500 each).                     │
│      Who should pay the refund processing fees and why?                         │
│                                                                                  │
│   7. What's the journal entry when commission moves from PENDING to EARNED?    │
│                                                                                  │
│   8. In double-entry bookkeeping, what does "Debit" mean for:                  │
│      a) An Asset account                                                        │
│      b) A Liability account                                                     │
│      c) A Revenue account                                                       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘

ANSWERS:

1. Escrow = Money you're holding FOR someone else (organizer) - it's their money.
   Earned Revenue = Your money - commission you've earned.

2. b) It's cancelled from pending (no clawback). Commission was never "earned"
   because the event hadn't happened yet.

3. Because you might have to give it back! If customer refunds before event,
   that "pending" commission must be returned. Only when it becomes EARNED
   is it truly your revenue.

4. Monday! Friday 6 PM is after cutoff, so transaction goes in Monday's batch.
   T+1 from Monday = Tuesday. (Wait: actually if 5 PM cutoff, 6 PM would be
   next batch which would settle Monday, so money arrives Monday or Tuesday
   depending on exact cutoff rules.)

5. Dr: Bank Account             K490
   Dr: Processing Fee Expense   K10
   Cr: Event Escrow (Liability) K475
   Cr: Pending Commission (Liab) K25

6. Ideally the organizer (they caused the cancellation). Platform should include
   this in Terms of Service: "Organizer cancellation may result in processing
   fee deduction from escrow."

7. Dr: Pending Commission (Liability)  K25  (decrease liability)
   Cr: Commission Revenue              K25  (increase revenue)

8. a) Asset: Debit = INCREASE (you have MORE)
   b) Liability: Debit = DECREASE (you owe LESS)
   c) Revenue: Debit = DECREASE (you earned LESS - rare, for corrections)
