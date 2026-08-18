# ET-TKT-003 · Validation, check-in and the offline gate — tasks

> **Spec** [`specs/ticketing/003-validation-and-checkin/spec.md`](../ticketing/003-validation-and-checkin/spec.md) · **Wave 5** · `blocked_by:` ET-PLT-005, ET-TKT-002, ET-ORG-003, ET-CAT-001
> **Screens** — check-in **reporting** only, in `Org Admin` (route `(dashboard)/events/[id]/check-in` already exists). The **scanner** is not one of the three web apps.
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-003 -DfailIfNoTests=true`

## ⚠️ Blocking decision — the scanner client has no codebase

The spec's §5 **T3** targets `frontend/mobile/.../scanner/`. **That directory does not exist** —
`frontend/` contains only `web`. The design authority's Coverage map agrees it is out of scope for
the three web apps: *"validation & check-in is a scanner-app / gate-steward surface, offline-first
— a distinct mobile scanner UI, not part of the three web apps designed here."*

So the scanner is specified, required by R2/R3, and has nowhere to live.

**Every backend task below (BE-1…BE-9) and the org-admin reporting surface are unblocked and
should proceed regardless.** Only FE-S (the scanner client) waits on this call:

| Option | Consequence |
|---|---|
| **A · New Expo app at `frontend/mobile`** *(matches the spec's own file path, and `CLAUDE.md` already describes an Expo 54 mobile app)* | Full offline-first scanner. New app, new toolchain, new e2e harness — the largest single frontend addition in the corpus |
| **B · Scanner as a route in `apps/ticketing`** | Reuses the existing PWA and its harness; offline signature verification and a local scan queue are achievable, camera access and background sync are weaker |
| **C · Defer the scanner; ship backend + reporting now** | Everything except FE-S lands; gates run on manual override (BE-7) until the scanner exists — workable for a first event, and BE-7 exists precisely for this |

**There is no design screen for any of these options**, so whichever is chosen needs a design pass
before UI work. Until the choice is made, treat FE-S as `deferred` and do not invent a screen.

## R0 · Reconcile

`CheckIn`, `CheckInConflict`, `CheckInService`, `CheckInServiceImpl`, `CheckInConflictRepository`,
`ValidationMethod`, `CheckInConflictType/Status` and `CheckInBackfillMigrationService` all exist
(untracked). 21 references to `ET-TKT-003`.

Decisive checks:
- Is the accept a **single conditional update**, or read-then-write? Read-then-write lets two
  scanners admit the same ticket.
- Is the refusal explainer on the **refusal path only**, or does the happy path pay for it?
- Does offline scan upload resolve conflicts by **earliest wins**, or by upload order?

## A · Backend

### BE-1 · The check-in document, its unique index and the online conditional update
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** **50 parallel scans yield one acceptance** under real contention.
- Two stewards scanning the same ticket at two gates simultaneously is normal, not exotic.

### BE-2 · The refusal explainer, on the refusal path only
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** each refusal carries its declared detail; **the happy path is one round trip**.
- The happy path runs thousands of times an hour at a gate with one bar of signal. Explaining a
  refusal can afford a second query; admitting a valid ticket cannot.

### BE-3 · Local signature verification in the scanner client
- **Spec** R2 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes · **⚠️ gated on the decision above**
- **Acceptance** **a forged code makes no network call**; verification is constant-time.
- The backend half — the signer and the per-event key ([`ET-TKT-002`](ET-TKT-002.md) BE-4/BE-6) —
  is buildable now and should be. Only the client half waits.

### BE-4 · `uploadScans` — idempotent, ordered, per-scan resolution
- **Spec** R3 · **§5** T4 · **depends** BE-2 · **parallel-safe** no
- **Acceptance** a **re-uploaded batch changes nothing**; **500 scans within budget**.
- Per-scan resolution, not per-batch: one bad scan in 500 must not reject the other 499.

### BE-5 · Conflict detection, the earliest-wins rule and the conflict document
- **Spec** R4 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** **two offline scans in either upload order yield the same winner.**
- Earliest **scan time** wins, not earliest upload. Two gates offline; whichever steward's phone
  reconnects first must not change who actually got in.

### BE-6 · Grant checks at key issue and at every scan and upload
- **Spec** R5 · **§5** T6 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** a **mid-batch revocation refuses the remainder; earlier scans stand.**
- A steward dismissed mid-event stops scanning immediately; the people they already admitted are
  still inside and their check-ins are real.

### BE-7 · Manual override, its reason, its metric and its alert ratio
- **Spec** R6 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** manual validations are **distinguishable in every report**; the ratio alerts.
- A rising manual ratio means the scanner is failing or someone is waving people through. Both
  need to be visible, and the two are indistinguishable without the metric.
- Under **Option C** this becomes the primary check-in path.

### BE-8 · The check-in summary aggregation and its polling contract
- **Spec** R7 · **§5** T8 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** **`$match` first** (**D-13**); a late upload is reflected within one poll.

### BE-9 · The subgraph half; the refund-after-validation test
- **Spec** R8 · **§5** T9 · **depends** BE-7 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** a **validated ticket refunds and both facts survive** — the person attended *and*
  was refunded. Neither erases the other, and the ledger and check-in report must both say so.

## B · Contract

### GQL-1 · 3 queries, 4 mutations
- **depends** BE-9 · **parallel-safe** no
- Scanner key provisioning is `@tag(name: "internal")` ([`ET-TKT-002`](ET-TKT-002.md) BE-6).

## C · Frontend

### Organizer reporting · `Org Admin` — **unblocked, build this now**

> **Infographics gate before the summary.**
> **Kernel:** *"How many people are in the room, and is the gate working?"*
> Attendance is the focal number. Manual-override ratio is the second story and must not compete
> with it — rank, do not tile.

### FE-1 · Live check-in summary
- **depends** GQL-1, BE-8, F0-2 · **parallel-safe** no
- Route `(dashboard)/events/[id]/check-in`. Checked in vs sold, as a proportion. Poll on a
  visibility-aware interval (**D-12**); a late upload appears within one poll.
- **testids** `checkin-total`, `checkin-sold`, `checkin-rate`, `checkin-last-updated`

### FE-2 · Manual override visibility
- **depends** BE-7 · **parallel-safe** yes
- Manual validations counted separately and always distinguishable (BE-7). The ratio is shown; an
  alerting ratio is stated, not merely coloured.
- **testids** `checkin-manual-count`, `checkin-manual-ratio`, `checkin-manual-alert`

### FE-3 · Conflicts
- **depends** BE-5 · **parallel-safe** yes
- Duplicate-scan conflicts with **which scan won and why** (earliest scan time). An unexplained
  conflict list is a list nobody acts on.
- **testids** `conflict-row`, `conflict-winner`, `conflict-reason`

### FE-4 · Steward access management
- **depends** BE-6, [`ET-ORG-003`](ET-ORG-003.md) FE-1 · **parallel-safe** yes
- Grant and revoke `ticket:scan` for this event. Revocation states that it takes effect on the
  steward's next scan **and that earlier scans stand**.
- **testids** `steward-row`, `steward-grant`, `steward-revoke`

### FE-S · Scanner client — **⚠️ deferred pending the decision above**

Specified but not scheduled. When the option is chosen, the requirements are already fixed by the
spec and are the hard part of this slice:

- Camera scan → **local signature verification, no network** (BE-3).
- Offline scan queue, persisted across app restarts; upload on reconnect (BE-4).
- Per-event key provisioning, scoped to the grant, stored securely and wiped on revocation.
- Refusal reasons legible at arm's length in daylight, on a cheap handset.
- Manual override with a reason (BE-7).
- **Designed for a gate: one bar of signal, low battery, a queue of people, and a steward who has
  not been trained.**

## D · Tests

### TS-1 · Contention *(L3)* — 50 parallel scans → one acceptance; unique index live via MCP.

### TS-2 · Refusal explainer *(L2)*
Each refusal carries its detail; **the happy path is one round trip** — assert the query count,
not the response.

### TS-3 · Signing *(L1)*
Forged code fails locally with **no network call**; constant-time; verification needs no database.

### TS-4 · Upload *(L3)*
Re-upload is a no-op; 500 scans within budget; per-scan resolution — one bad scan does not reject
the batch.

### TS-5 · Conflicts *(L3 — the ordering property)*
Two offline scans uploaded in **either order** → the **same** winner, by earliest scan time.

### TS-6 · Grants *(L3)*
Mid-batch revocation refuses the remainder; **earlier scans stand**; key issue requires the grant.

### TS-7 · Manual override *(L3)*
Distinguishable in **every** report; the ratio alerts at threshold.

### TS-8 · Summary *(L3)*
`$match` first; a late upload is reflected within one poll.

### TS-9 · Refund after validation *(L3)*
A validated ticket refunds; **both facts survive**; ledger balanced; the check-in report still
counts the attendance.

### TS-10 · e2e *(L5, org-admin — needs F0-2)*
Summary polling, manual ratio, conflicts with winners, steward grant/revoke. Loading, empty (an
event before doors open **is** the empty state), error, populated.

### TS-11 · Scanner *(deferred with FE-S)*
Offline scan → queue → reconnect → upload → conflict resolution, on a real device profile.

## E · Gate

- [ ] **Scanner decision recorded (A / B / C) with its rationale**
- [ ] R0 recorded; read-then-write accept or upload-order conflict resolution classified `contradicted`
- [ ] 50 parallel scans → one acceptance
- [ ] Happy path is one round trip; refusals carry their detail
- [ ] Forged codes fail locally with no network call
- [ ] Re-uploaded batches change nothing; 500 scans within budget
- [ ] Either upload order yields the same winner, by earliest scan time
- [ ] Mid-batch revocation refuses the remainder; earlier scans stand
- [ ] Manual overrides distinguishable everywhere; ratio alerts
- [ ] Validated tickets can still be refunded; both facts survive
- [ ] Org-admin reporting built and e2e-covered (**not** blocked by the scanner decision)
- [ ] **Infographics gate passed**; attendance is the single focal number
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-TKT-003 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented` (or `implemented` with FE-S explicitly `deferred`, recorded in `spec.yaml`)
