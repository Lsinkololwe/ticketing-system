# ET-TKT-004 · Ticket transfer and controlled resale — tasks

> **Spec** [`specs/ticketing/004-transfer-and-resale/spec.md`](../ticketing/004-transfer-and-resale/spec.md) · **Wave 5** · `blocked_by:` ET-PLT-005, 007, ET-TKT-002, 003, ET-PAY-001, ET-FIN-001, 002
> **Screen** `Ticketing - My Tickets & Transfer.dc.html` — **read it first**
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-004 -DfailIfNoTests=false`

> **Corpus note.** This spec has **four broken cross-area links** (precondition **P4**): it writes
> `../002-commission/`, `../003-payouts-and-settlement/` ×2 and `../001-escrow-and-ledger/` as
> siblings when those live under `finance/`. Fix them in this slice — they sit inside acceptance
> boxes that route the implementer to the owning spec.

A ticket is a bearer credential ([`ET-TKT-002`](ET-TKT-002.md)), so transferring one is a
**re-issue**, not a change of a name field. Resale is capped at face value — the platform is not
building a secondary market.

## R0 · Reconcile

One reference to `ET-TKT-004` exists. Effectively greenfield. Verify before assuming:
- Does `Ticket` support a `TRANSFER_PENDING` state?
- Does anything already allow a price above face value? Off by default is the requirement.

## A · Backend

### BE-1 · The transfer document, its token, its TTL and `TRANSFER_PENDING`
- **Spec** R2, R3 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** a pending ticket is **not scannable, not refundable, not re-transferable**.
- All three. A ticket in flight between two people must not be usable by either, or a sender
  transfers, walks in, and the recipient is turned away at the gate.

### BE-2 · `initiateTransfer` and its five refusals
- **Spec** R5 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a **non-owner receives `TICKET_UNKNOWN`**, not a permission error
  ([`ET-PLT-005`](ET-PLT-005.md) R6). Anything else confirms the ticket exists to someone who does
  not hold it.

### BE-3 · `claimTransfer` — ownership plus re-issue, in one transaction
- **Spec** R1 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- **Acceptance** **the sender's QR fails immediately** after; two parallel claims yield **one**
  change.
- Ownership and re-issue in the same transaction. Transferring without rotating the QR means the
  sender keeps a working screenshot — which is the entire attack.

### BE-4 · Expiry and cancellation return the ticket **without** re-issuing
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **the sender's QR still works after a return**; both paths idempotent.
- The asymmetry is deliberate: a claim rotates, a return does not. Rotating on return would
  invalidate the sender's own ticket for a transfer that never happened.

### BE-5 · The cutoff, its boundary tests and the cancelled-event rule
- **Spec** R4 · **§5** T5 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** refusal **either side of the cutoff**; a **cancelled event returns every pending
  transfer** — so refunds go to the people who actually hold the tickets.

### BE-6 · The resale flag, the face-value cap and the listing lifecycle
- **Spec** R6 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **resale is off by default**; **no path permits a price above face value.**
- Both are policy decisions with legal weight in-market. Assert the cap as a source-level
  prohibition as well as behaviourally — a validation that lives only in a service method is one
  new call site away from being bypassed.

### BE-7 · Resale settlement — `2040`, the entries and the payment path
- **Spec** R7 · **§5** T7 · **depends** BE-6 · **parallel-safe** no
- **Acceptance** the entries balance; **a failed payment leaves the listing live**; **the original
  commission is untouched**.
- The first sale's commission was earned and recognised on its own schedule
  ([`ET-FIN-002`](../finance/002-commission/)). A resale is a new transaction with its own
  commission; reaching back into the first one would reverse revenue that was correctly booked.
- Seller proceeds credit `2040`, payable through [`ET-FIN-003`](ET-FIN-003.md) against `2040`
  rather than an event escrow.

### BE-8 · The chain, its query and the organizer's view
- **Spec** R8 · **§5** T8 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** three transfers read correctly **from either end**; **a buyer sees only their
  own** segment.
- The organizer needs the chain for fraud investigation; a buyer must not learn who held the
  ticket before them.

### BE-9 · The subgraph half; the narrow preview type
- **Spec** R2 · **§5** T9 · **depends** BE-7 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** **`TransferPreview` exposes exactly six fields**; static composition green.
- Reachable by anyone holding a claim link, exactly as [`ET-ORG-002`](ET-ORG-002.md) BE-4's
  invitation preview.

## B · Contract

### GQL-1 · 4 queries, 6 mutations
- **depends** BE-9 · **parallel-safe** no

## C · Frontend — `Ticketing - My Tickets & Transfer.dc.html`

### FE-1 · Initiate transfer
- **depends** GQL-1, F0-1 · **parallel-safe** no
- Recipient (phone or email), and a clear statement of what happens: **the ticket leaves you when
  they claim it, and your QR stops working then**.
- Cutoff shown; past it the action is absent with the reason.
- **testids** `transfer-initiate`, `transfer-recipient`, `transfer-cutoff-notice`, `transfer-confirm`

### FE-2 · Pending transfer state
- **depends** FE-1 · **parallel-safe** no
- **The sender's ticket must visibly show it is in flight**: not scannable, not refundable, not
  re-transferable (BE-1). Expiry countdown; cancel available until claimed.
- Showing a normal QR on a pending ticket is the defect that gets someone turned away at a gate.
- **testids** `transfer-pending-badge`, `transfer-expiry`, `transfer-cancel`, `ticket-qr-suppressed`

### FE-3 · Claim — a **public** route
- **depends** GQL-1 · **parallel-safe** yes
- Reached by link, often with no session; sign-in first, then claim. Shows only the six preview
  fields.
- States: valid, expired, cancelled, already-claimed, event-cancelled. All five designed.
- **testids** `transfer-preview`, `transfer-claim`, `transfer-state-<state>`

### FE-4 · Return states
- **depends** BE-4 · **parallel-safe** yes
- On expiry or cancellation the ticket returns and **the original QR works again** — say so, or
  the sender assumes it is dead and requests a re-issue they do not need.
- **testids** `transfer-returned-notice`

### FE-5 · Resale listing *(only where the organizer enabled it)*
- **depends** BE-6, GQL-1 · **parallel-safe** yes
- Off by default (BE-6) — absent, not disabled, where not enabled.
- **The price input is capped at face value and says so before the seller types a higher number.**
  A cap enforced only on submit reads as arbitrary.
- **testids** `resale-list`, `resale-price`, `resale-facevalue-cap`, `resale-listing-status`

### FE-6 · Resale purchase
- **depends** BE-7 · **parallel-safe** yes
- Reuses the checkout path ([`ET-TKT-001`](ET-TKT-001.md) / [`ET-PAY-001`](ET-PAY-001.md)),
  including the idempotency key. A failed payment **leaves the listing live** — say so rather than
  implying the ticket is gone.
- **testids** `resale-buy`, `resale-payment-failed`

### FE-7 · Transfer history
- **depends** BE-8 · **parallel-safe** yes
- The buyer sees **only their own** segment. No prior holders — that is the organizer's view, not
  the buyer's.
- **testids** `transfer-history-row`

## D · Tests

### TS-1 · Pending state *(L3)*
Pending is **not scannable, not refundable, not re-transferable** — three assertions.

### TS-2 · Refusals *(L3)* — non-owner → `TICKET_UNKNOWN`; all five refusals distinct.

### TS-3 · Claim *(L3)*
Sender's QR fails **immediately** after claim; two parallel claims → one change; ownership and
re-issue in one transaction (fail the re-issue and observe ownership unchanged).

### TS-4 · Return *(L3, frozen clock)*
Expiry and cancellation return the ticket; **the sender's QR still works**; both paths idempotent.

### TS-5 · Cutoff *(L1)* — refusal either side; cancelled event returns every pending transfer.

### TS-6 · Resale cap *(L1 + L3)*
Off by default; **no path** permits above face value — source-level **and** behavioural, including
an update-after-listing path.

### TS-7 · Resale settlement *(L3)*
Entries balance; failed payment leaves the listing live; **the original commission is untouched**;
`2040` credited; `Ledger.assertBalanced()`.

### TS-8 · Chain *(L3)*
Three transfers read from either end; a buyer's query returns only their segment.

### TS-9 · Contract *(L4)* — `TransferPreview` has exactly six fields in the composed schema.

### TS-10 · e2e *(L5, ticketing — needs F0-1, F0-4)*
- Initiate → pending (QR suppressed) → claim → sender's QR dead, recipient's live.
- Expiry → return → sender's QR alive again.
- Claim link in all five states, including no session.
- Resale: cap enforced before submit; failed payment leaves the listing live.
- Loading, empty, error, populated.

## E · Gate

- [ ] **P4: the four broken finance links in this spec fixed**
- [ ] R0 recorded
- [ ] Pending tickets are not scannable, refundable or re-transferable
- [ ] Non-owner initiation returns `TICKET_UNKNOWN`
- [ ] Claim rotates the QR in the same transaction as ownership
- [ ] Return does **not** rotate; the sender's QR still works
- [ ] Cutoff enforced both sides; cancellation returns every pending transfer
- [ ] Resale off by default; face value cap unbypassable, asserted at source and behaviour
- [ ] Resale settlement balances; original commission untouched; `2040` credited
- [ ] Buyer sees only their own chain segment
- [ ] `TransferPreview` exposes exactly six fields
- [ ] Pending tickets never render a usable QR
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-004 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
