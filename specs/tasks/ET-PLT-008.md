# ET-PLT-008 · PII inventory, erasure and the retention schedule — tasks

> **Spec** [`specs/_platform/008-data-protection/spec.md`](../_platform/008-data-protection/spec.md) · **Wave 7** · `blocked_by:` ET-PLT-002, 007, ET-IDN-002, ET-FIN-001, ET-NTF-001
> **Screens** `Ticketing - Profile & Registration.dc.html` *(erasure request, export, consent)* · `Admin - Users & Organizations.dc.html` *(obligations, certificate)*
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-008 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

> **Corpus note.** This spec has **four broken links** (**P4**) — it writes
> `../001-notification-transport/` when [`ET-NTF-001`](ET-NTF-001.md) lives under `notification/`.
> Fix them in this slice; they sit inside acceptance boxes.

The tension this spec resolves: a person may demand erasure, and the platform may be legally
obliged to keep a financial record of what they bought. Both are true, so erasure is **anonymisation
plus a certificate stating what was retained and why** — not deletion.

## R0 · Reconcile

```bash
grep -rn 'phone\|email\|nrc\|firstName\|lastName\|msisdn' backend --include='*.java' | grep -v /src/test/ | wc -l
```

Build the **PII inventory as data** in R0 — every field, its collection, its retention class. That
inventory is the input to BE-1 and the thing every later task is driven by. Without it, erasure is a
best-effort sweep that misses a collection nobody remembered.

Check also: does any **event payload** carry personal data? [`ET-PLT-003`](ET-PLT-003.md) BE-8
forbids it, and this spec depends on that holding.

## A · Backend

### BE-1 · The `@Pii` annotation, the inventory and the build check
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** **every annotated field has an inventory row and vice versa**; **an unannotated
  addition fails the build**.
- The build check is what keeps this true. Without it the inventory is accurate on the day it is
  written and wrong a sprint later, and nobody finds out until an erasure misses a field.

### BE-2 · The subject token, the anonymiser and the irreversibility test
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** **the original is unrecoverable from document, index and log.** All three.
- Anonymisation that leaves the original in an index, or in a log line written at the time, has not
  anonymised anything. The index is the one people forget.

### BE-3 · The request, the grace period, cancellation and the notifications
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **a pending-deletion account still works**; **cancellation restores at any point**
  in the 30-day window.
- The account stays usable during the grace period because the commonest erasure request is made in
  anger and regretted. A dead account for 30 days makes cancellation pointless.

### BE-4 · The obligation checks and the deferral path
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **each obligation defers and re-attempts on closure.**
- An open dispute, an unsettled payout, a financial record inside its statutory window — each
  defers erasure rather than blocking it forever. Deferral with automatic re-attempt is what stops
  a request being quietly dropped.

### BE-5 · The inventory-driven erasure job across all three services
- **Spec** R2, R4 · **§5** T5 · **depends** BE-4 · **parallel-safe** **no — spans three services**
- **Acceptance** **the trial balance is unchanged**; **a full purchase history still resolves**.
- Erasure must not move a single kwacha. The ledger keeps its rows; the person becomes a token.
  Assert `Ledger.assertBalanced()` before and after, and compare the trial balance exactly.

### BE-6 · TTLs for short-lived PII, each tied to an inventory row
- **Spec** R5 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** each TTL is **configured, fires, and has an inventory row stating its window.**
- Confirm the TTL indexes exist live via **MongoDB MCP** — a TTL declared in an annotation but never
  created grows the collection forever, and nothing fails until it does.

### BE-7 · Asynchronous export with a presigned link
- **Spec** R6 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **no third-party PII in the export**; generation **does not contend with
  purchases**.
- A buyer's export must not include the organizer's bank details or another attendee's name.
  Presigned link per **D-11**, and off-peak so a full export never competes with on-sale.

### BE-8 · Consent records with provenance, and the suppression rule
- **Spec** R7 · **§5** T8 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **a marketing send with no consent record is suppressed.**
- Suppressed, not defaulted to allowed. Absence of a record is absence of consent — this is what
  [`ET-NTF-001`](ET-NTF-001.md) BE-3's optional categories check against.

### BE-9 · The erasure certificate and the subgraph half
- **Spec** R3, R4 · **§5** T9 · **depends** BE-5 · **parallel-safe** **no — shared identity SDL**
- **Acceptance** the certificate states **what was erased and what was retained and why**.
- That "and why" is the whole document. "We kept some records" is not an answer to a data-subject
  request; "we retained transaction records under statutory obligation X until date Y" is.

## B · Contract

### GQL-1 · 5 queries, 6 mutations
- **depends** BE-9 · **parallel-safe** no

## C · Frontend

### Data subject · `Ticketing - Profile & Registration.dc.html`

### FE-1 · Request erasure
- **depends** GQL-1, F0-1 · **parallel-safe** no
- Confirm, and state plainly: **30 days to change your mind; your account keeps working until then**;
  and **what will be retained and why** (BE-9's categories, before the request, not after).
- **testids** `erasure-request`, `erasure-grace-notice`, `erasure-retention-notice`, `erasure-confirm`

### FE-2 · Pending state and cancellation
- **depends** BE-3 · **parallel-safe** yes
- Countdown; cancel available throughout; the account visibly still works.
- **testids** `erasure-pending`, `erasure-countdown`, `erasure-cancel`

### FE-3 · Data export
- **depends** BE-7 · **parallel-safe** yes
- Request → asynchronous → presigned link with an expiry. Say it takes time; do not spin.
- **testids** `export-request`, `export-pending`, `export-download`, `export-expiry`

### FE-4 · Consent
- **depends** BE-8 · **parallel-safe** yes
- Marketing consent with **when and how it was given** (provenance). Withdrawal is immediate.
- Sits beside [`ET-NTF-001`](ET-NTF-001.md) FE-1's preferences — consent is the legal basis,
  preference is the channel choice, and the UI must not merge them into one toggle.
- **testids** `consent-row`, `consent-provenance`, `consent-withdraw`

### Admin · `Admin - Users & Organizations.dc.html`

### FE-5 · Erasure queue and obligations
- **depends** BE-4 · **parallel-safe** yes
- Pending requests, deferred ones **with the obligation naming itself**, completed ones with their
  certificate.
- A deferred request must never look abandoned — show what it is waiting on and when it re-attempts.
- **testids** `erasure-queue-row`, `erasure-deferred-reason`, `erasure-retry-at`, `erasure-certificate-link`

### FE-6 · No PII in the erasure UI itself
- **parallel-safe** yes
- The queue shows tokens and dates, not names and phone numbers. A screen built to erase personal
  data must not display it.
- **Acceptance** an e2e asserts no MSISDN-shaped string renders on the admin queue.

## D · Tests

### TS-1 · Inventory *(L4 — build check)*
Annotated set equals the inventory, both directions; **an unannotated PII field fails the build**.

### TS-2 · Anonymisation *(L3)*
Original unrecoverable from **document, index and log** — three assertions, and the index one by
querying it directly.

### TS-3 · Grace period *(L3, frozen clock)*
Account works throughout; cancellation restores at day 1, day 15 and day 29; day 31 executes.

### TS-4 · Obligations *(L3)*
Each obligation defers; closing it triggers re-attempt; nothing is dropped.

### TS-5 · Erasure *(L3 — the money-safety test)*
**Trial balance identical before and after**; `Ledger.assertBalanced()`; a full purchase history
still resolves with tokenised subjects.

### TS-6 · TTLs *(L3)*
Each configured TTL fires; each has an inventory row; **indexes confirmed live via MCP**.

### TS-7 · Export *(L3)*
No third-party PII; generation off-peak and non-contending — run it concurrently with 200-against-50
and assert reservation latency.

### TS-8 · Consent *(L3)* — no record → marketing suppressed; provenance recorded.

### TS-9 · e2e *(L5)*
Request → pending → cancel; request → pending → complete → certificate. Export. Consent withdrawal.
Admin queue with deferred reasons. **No PII rendered in the erasure UI.** Loading, empty, error,
populated.

## E · Gate

- [ ] **P4: the four broken `../001-notification-transport/` links fixed**
- [ ] R0 PII inventory built as data before any code
- [ ] Annotated fields and inventory rows equal; an unannotated addition fails the build
- [ ] Original unrecoverable from document, index **and** log
- [ ] Account works during the 30-day grace; cancellation restores at any point
- [ ] Every obligation defers and re-attempts on closure
- [ ] **Trial balance unchanged by erasure**; purchase history still resolves
- [ ] Every TTL configured, firing, inventoried — and confirmed live
- [ ] Export carries no third-party PII and does not contend with purchases
- [ ] No consent record → marketing suppressed
- [ ] Certificate states what was retained **and why**
- [ ] No PII renders in the erasure UI
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-008 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
