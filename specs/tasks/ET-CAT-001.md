# ET-CAT-001 · Event lifecycle — tasks

> **Spec** [`specs/catalog/001-event-lifecycle/spec.md`](../catalog/001-event-lifecycle/spec.md) · **Wave 2** · `blocked_by:` ET-PLT-002, 003, 005, 007, ET-ORG-001, ET-ORG-003
> **Screens** `Org Admin - Event Editor.dc.html`, `Org Admin - Create Event Wizard.dc.html` *(author)* · `Admin - Approvals Workbench.dc.html` + `Admin - Events.dc.html` *(reviewer)* · `Ticketing - Discover & Checkout.dc.html` + `Ticketing - Event Detail (Full).dc.html` *(buyer)*
> **Routes** org-admin `(dashboard)/events{,/new,/[id]}` · admin `(dashboard)/approvals/events`, `events{,/calendar}` · ticketing `events/[id]`, `/`
> **Verify** `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-001 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

7 queries, 10 mutations, three audiences. The state machine here is what makes an event visible,
sellable, refundable or over — and V3 §5 and §9 are authoritative on it.

## R0 · Reconcile

```bash
grep -rn 'EventStatus\|ET-CAT-001' backend/catalog-service --include='*.java'
grep -rn 'hasRole\|Permission' backend/catalog-service --include='*.java' | grep -v /src/test/
```

Every permission comparison found is a `contradicted` row deleted by
[`ET-ORG-003`](ET-ORG-003.md) BE-5 — this spec **must** gate through the internal permission API
instead. Also classify: does a status literal appear in any mutation? Is there a completion sweep
at all?

## A · Backend

### BE-1 · Document, `EventStatus`, `EventTransitions`, the 99-pair test
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** **14 legal rows**, 85 refusals, **no status literal in any mutation**.

### BE-2 · `MATERIAL_FIELDS` and the re-approval rule
- **Spec** R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** each material field **individually** returns an `APPROVED` event to `DRAFT`;
  **adding a new field fails a test until it is classified**.
- That second clause is the durable part. Without it, the next field added to the event silently
  defaults to immaterial, and an organizer edits the venue of an approved event without review.

### BE-3 · Create and publish gates over the permission API; the `EVENT_OWNER` grant
- **Spec** R2 · **§5** T3 · **depends** BE-1 · **parallel-safe** no *(depends on [`ET-ORG-003`](ET-ORG-003.md)'s endpoint)*
- **Acceptance** **catalog contains no role comparison**; the creator holds `EVENT_OWNER`.
- Staged access comes from [`ET-ORG-001`](ET-ORG-001.md) BE-7's capability matrix: a
  `PENDING_REVIEW` organization may create a **draft** but not publish.

### BE-4 · Publish, unpublish, and the zero-sold check
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** an event with **one sold ticket cannot be unpublished**; an `APPROVED` event is
  **invisible publicly**.
- Unpublishing under a sold ticket would strand a buyer holding a ticket to an event that no
  longer exists. Approved-but-unpublished must not leak: approval is an internal fact.

### BE-5 · Reschedule — dates, refund window, escrow clock, window extension
- **Spec** R5 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** **tickets stay valid**; a second reschedule **extends rather than replaces** the
  refund window.
- Replacing the window on a second reschedule shortens the buyer's rights each time the organizer
  changes their mind. V3 §9 is authoritative.

### BE-6 · The completion sweep, its lock, its idempotence
- **Spec** R6 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** frozen-clock completion at `endsAt + interval`; a second instance **moves nothing
  twice**.
- Completion is what triggers commission **recognition** ([`ET-FIN-002`](ET-FIN-002.md), **D-04**),
  so a double-fire double-recognises revenue.

### BE-7 · Cancellation and the arithmetic assertion
- **Spec** R7 · **§5** T7 · **depends** BE-4 · **parallel-safe** **no — spans two services**
- **Acceptance** `escrowDebit + commissionCancelled + clawedBack == totalRefunded`, to **K0.01**.
- This is the money-conservation identity for a cancelled event. Assert it as arithmetic on real
  ledger rows, not as a code review.

### BE-8 · Consumers for the two identity events; the denormalised counter
- **Spec** R2, R8 · **§5** T8 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** **suspension unpublishes**; the counter is **display-only and never gates**.
- `catalog`'s sold counter is a mirror. The authority is `booking_tier_inventory`
  ([`ET-CAT-002`](ET-CAT-002.md)). A gate on a mirrored counter oversells the moment the mirror
  lags — which is exactly at on-sale.

### BE-9 · Discovery — connections, the index, cache, `@auth` on every field
- **Spec** R8 · **§5** T9 · **depends** BE-4 · **parallel-safe** **no — shared catalog SDL with CAT-002 and CAT-003**
- **Acceptance** `explain()` reports `IXSCAN`; static composition green.

## B · Contract

### GQL-1 · 7 queries, 10 mutations, three audiences
- **depends** BE-9 · **parallel-safe** no
- `@tag` every admin element; the **public** contract must expose the buyer's projection only —
  no review reason, no internal status vocabulary.
- `compose-supergraph.sh --static` → `npm run codegen` → commit; **restart the local router**.

## C · Frontend

### Author · `Org Admin - Create Event Wizard.dc.html` and `Event Editor.dc.html`

### FE-1 · Create-event wizard
- **depends** GQL-1, F0-2 · **parallel-safe** no
- Route `(dashboard)/events/new`. Step order comes from the screen, not from the form model.
- Venue picker binds to [`ET-CAT-003`](ET-CAT-003.md); tiers to [`ET-CAT-002`](ET-CAT-002.md).
- **testids** `event-wizard-step-<n>`, `event-title`, `event-dates`, `event-venue`, `event-wizard-submit`

### FE-2 · Event editor with the material-field warning
- **depends** FE-1 · **parallel-safe** no
- Route `events/[id]`. Editing a **material** field on an `APPROVED` event returns it to `DRAFT`
  and requires re-approval — **warn before saving, naming the field**. Discovering this after the
  fact is how an organizer loses a publish slot the day before on-sale.
- **testids** `event-editor`, `event-material-warning`, `event-save`

### FE-3 · Lifecycle actions and the state banner
- **depends** FE-2 · **parallel-safe** yes
- Submit for approval, publish, unpublish, reschedule, cancel — each visible only when the state
  machine and the capability matrix both allow it, and each explaining itself when it does not.
- Statuses humanised: `PUBLISHED` → "Live" (**F0-7**).
- Unpublish is absent once a ticket is sold (BE-4).
- **testids** `event-status-banner`, `event-publish`, `event-unpublish`, `event-reschedule`, `event-cancel`

### FE-4 · Reschedule and cancel dialogs — consequences stated
- **depends** FE-3 · **parallel-safe** yes
- Reschedule: new dates, and **what happens to the refund window** (extended, not replaced).
- Cancel: irreversible, refunds every ticket, reverses commission. State the amount.
- Both confirm before firing. These are the two most consequential actions an organizer has.
- **testids** `reschedule-dialog`, `reschedule-confirm`, `cancel-dialog`, `cancel-confirm`, `cancel-refund-total`

### Reviewer · `Admin - Approvals Workbench.dc.html`, `Admin - Events.dc.html`

### FE-5 · Event approval queue
- **depends** GQL-1 · **parallel-safe** yes
- Route `(dashboard)/approvals/events`. "Approve" / "Reject" / "Request Changes" — exact verbs.
- **testids** `event-approval-row`, `event-approve`, `event-reject`, `event-request-changes`

### FE-6 · Admin events list and calendar
- **depends** GQL-1 · **parallel-safe** yes
- Routes `events`, `events/calendar`. Bento stat row above; table below; both from the screen.
- **testids** `admin-events-table`, `admin-events-calendar`

### Buyer · `Ticketing - Discover & Checkout.dc.html`, `Event Detail (Full).dc.html`

### FE-7 · Discovery list
- **depends** GQL-1 · **parallel-safe** yes
- Route `/`. Only `PUBLISHED` events. Cursor pagination against BE-9's connection — never an
  offset walk of the catalogue.
- Images lazy with reserved space; `srcset`; the list must not reflow as they land.
- `data-brand="ticketing"`, iris accent, **Space Grotesk headings only** (body stays Inter).
- **testids** `event-card`, `event-list`, `event-list-empty`, `event-list-load-more`

### FE-8 · Event detail
- **depends** FE-7 · **parallel-safe** yes
- Route `events/[id]`. Tiers, prices, sales window state, sold-out state, venue.
- Currency `K 125,430` — Kwacha symbol, space, tabular Fira Code. **Never `ZMW`, never `$`.**
- A cancelled or rescheduled event says so **at the top**, with the new date or the refund
  position — not as a footnote under the buy button.
- **testids** `event-detail`, `tier-row`, `tier-price`, `tier-soldout`, `event-reschedule-notice`

## D · Tests

### TS-1 · State machine *(L1)* — all 99 pairs: 14 allow, 85 refuse; no status literal.

### TS-2 · Material fields *(L1)*
Each material field individually demotes an `APPROVED` event; **an unclassified new field fails
the test**.

### TS-3 · Authorization *(L3)*
Catalog holds no role comparison; creator gets `EVENT_OWNER`; a `PENDING_REVIEW` organization can
draft but not publish.

### TS-4 · Publish rules *(L3)*
One sold ticket blocks unpublish; `APPROVED` is invisible on the public contract — assert against
the **composed public contract**, not the resolver.

### TS-5 · Reschedule *(L3, frozen clock)*
Tickets stay valid; a second reschedule extends the window; the escrow clock moves.

### TS-6 · Completion *(L3)*
Completes at `endsAt + interval`; a second sweeper instance moves nothing twice.

### TS-7 · Cancellation arithmetic *(L3 — spans catalog and booking)*
`escrowDebit + commissionCancelled + clawedBack == totalRefunded` to K0.01. Plus
`Ledger.assertBalanced()`.

### TS-8 · Counter safety *(L3)*
The denormalised counter never gates a sale — assert by desynchronising it deliberately and
confirming a sale still respects `booking_tier_inventory`.

### TS-9 · Discovery *(L3)*
`IXSCAN` on every discovery filter combination; connection pagination is stable under insertion.

### TS-10 · e2e *(L5)*
- **Author (org-admin, needs F0-2)**: wizard → submit → material-field warning → publish →
  reschedule → cancel, each with its confirm.
- **Reviewer (admin)**: queue loading/empty/error/populated; the three verbs.
- **Buyer (ticketing, needs F0-1)**: discovery empty and populated; detail; sold-out; cancelled
  notice. Apollo-driven → use the **F0-4** container fixture.
- Compliance on all three: correct `data-brand`, Space Grotesk headings **only** in ticketing.

## E · Gate

- [ ] R0 recorded; every catalog role comparison classified `contradicted`
- [ ] 14 legal transitions of 99 pairs; no status literal
- [ ] Material-field classification enforced; a new unclassified field fails a test
- [ ] Catalog contains no role comparison; gates go through the permission API
- [ ] Sold ticket blocks unpublish; `APPROVED` invisible on the public contract
- [ ] Reschedule keeps tickets valid and **extends** the refund window
- [ ] Completion sweep idempotent under two instances
- [ ] Cancellation arithmetic balances to K0.01; ledger balanced
- [ ] Denormalised counter never gates a sale, proven by deliberate desync
- [ ] All five `.dc.html` screens read; layouts match
- [ ] Author, reviewer and buyer e2e green; compliance green on three apps
- [ ] `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
