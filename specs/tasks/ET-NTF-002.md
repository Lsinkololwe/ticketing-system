# ET-NTF-002 · Lifecycle triggers — which fact produces which message — tasks

> **Spec** [`specs/notification/002-lifecycle-triggers/spec.md`](../notification/002-lifecycle-triggers/spec.md) · **Wave 5** · `blocked_by:` ET-PLT-003, ET-NTF-001, ET-CAT-001, ET-TKT-002, ET-FIN-003, ET-FIN-004, ET-ORG-002
> **Screens** — **none.** Same Coverage-map ruling as [`ET-NTF-001`](ET-NTF-001.md). The only surface is the `triggerRegistry` query, which is an admin read for operators, not a screen of its own.
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-NTF-002 -DfailIfNoTests=true`

Seven blockers — the most of any spec in the corpus — because it listens to facts produced by
nearly every other one. **The registry is data, not code**: which fact produces which message, to
whom, on which channel, expressed as a table that can be read and tested.

## R0 · Reconcile

Effectively greenfield. Classify what already sends messages ad-hoc:

```bash
grep -rn 'notificationService\.send\|messagingService\.send' backend --include='*.java' | grep -v /src/test/
```

Any direct `send` from a business service is `contradicted` by R1 — it bypasses the registry, the
rate cap, coalescing and recipient resolution, and it is invisible to the operator reading
`triggerRegistry`.

## A · Backend

### BE-1 · The trigger registry as data, and the listeners that drive it
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** the set of templates sent **equals** the registry; **no service calls `send`
  directly**.
- Equality, both directions: a registry row nobody sends is a promise the platform breaks; a
  message nobody registered is one no operator can explain when a user asks why they got it.

### BE-2 · Purchase coalescing on `reservationId`
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a **10-ticket purchase yields one notification.** One document per seat
  ([`ET-TKT-002`](ET-TKT-002.md) BE-1) means ten issuance facts for one human decision. Ten
  WhatsApp messages for one purchase is how a platform gets muted.

### BE-3 · The mass-send job — rated, batched, resumable
- **Spec** R3 · **§5** T3 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** **10,000 holders, killed midway, resumed, exactly 10,000 messages.** Not 10,001,
  not 9,999.
- Same resumability requirement as [`ET-FIN-004`](ET-FIN-004.md) BE-5's mass refund, and for the
  same reason — a cancellation hits both at once, on the same event, at the same moment.

### BE-4 · The hourly cap and its suppression record
- **Spec** R4 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **15 optional and 5 transactional yield 10 and 5.**
- The cap applies to optional messages only. Suppressing a payout confirmation because a user
  browsed a lot of events would be absurd. Suppressions are **recorded**, so "why didn't I get
  it?" has an answer.

### BE-5 · Reminder rows, their lifecycle consumers and the dispatch sweep
- **Spec** R5 · **§5** T5 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** reminders **track a reschedule in both directions**; a past-due creation **skips**.
- Both directions: an event moved later pushes the reminder later; moved earlier pulls it in. A
  reminder for a date that already passed must not fire at all.

### BE-6 · The organizer digest and its empty-digest suppression
- **Spec** R6 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **500 sales yield one digest**; **no activity yields none.**
- A daily "you sold nothing" email trains the organizer to ignore the channel that will later
  carry "your payout failed".

### BE-7 · Recipient resolution at send time
- **Spec** R7 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a **transfer between trigger and send reaches the new owner**; **no payload PII is
  read**.
- Resolve **at send**, not at trigger. A ticket transferred in the interval must notify whoever
  holds it now.
- The second clause enforces [`ET-PLT-003`](ET-PLT-003.md) BE-8: envelopes carry ids, not people.
  The notifier looks the recipient up — which is also what makes send-time resolution possible at
  all.

### BE-8 · The subgraph half and the `triggerRegistry` query
- **Spec** R1 · **§5** T8 · **depends** BE-6 · **parallel-safe** **no — shared identity SDL**
- The registry is queryable so an operator can answer "what would this action send, and to whom?"
  without reading Java.

## B · Contract

### GQL-1 · 4 queries, 2 mutations
- **depends** BE-8 · **parallel-safe** no
- `ADMIN`-scoped and `@tag`ged.

## C · Frontend

**No screen.** The Coverage map is explicit and §6 agrees.

One narrow surface only, and it is a read:

### FE-1 · Trigger registry viewer *(inside `Admin - Transactions & System`)*
- **depends** GQL-1 · **parallel-safe** yes
- A table: fact → template → recipient → channel. Read-only. It exists so an operator answering
  *"why did this user get this message?"* has somewhere to look.
- Reuses the existing system-page table shape — this does not warrant a new layout.
- **testids** `trigger-registry-table`, `trigger-row`

**Do not build a template editor, a campaign composer or a notification dashboard.** None is in
scope, and each would need a design pass that does not exist.

## D · Tests

### TS-1 · Registry equality *(L1 — the load-bearing test)*
The set of templates sent **equals** the registry, both directions. **No service calls `send`
directly** — assert by source scan so a future direct call fails the build.

### TS-2 · Coalescing *(L3)* — a 10-ticket purchase → one notification.

### TS-3 · Mass send *(L3)*
10,000 holders, killed midway, resumed → **exactly** 10,000. Run it against the rate limiter, not
around it.

### TS-4 · Cap *(L3)* — 15 optional + 5 transactional → 10 + 5; suppressions recorded and queryable.

### TS-5 · Reminders *(L3, frozen clock)*
Reschedule later and earlier both move the reminder; past-due creation skips; the dispatch sweep is
idempotent.

### TS-6 · Digest *(L3)* — 500 sales → one digest; no activity → none.

### TS-7 · Recipient resolution *(L3 — the subtle one)*
Transfer the ticket **between trigger and send**; the **new** owner is notified. Assert **no payload
PII is read** by instrumenting the envelope accessor.

### TS-8 · e2e *(L5, admin)*
Trigger registry viewer: loading, empty, error, populated. That is the whole frontend surface.

## E · Gate

- [ ] R0 recorded; every direct `send` from a business service classified `contradicted`
- [ ] Registry equals sends, both directions
- [ ] No service calls `send` directly, enforced by source scan
- [ ] A 10-ticket purchase yields one notification
- [ ] 10,000-holder mass send resumes to exactly 10,000
- [ ] Hourly cap applies to optional only; suppressions recorded
- [ ] Reminders track reschedules in both directions; past-due skips
- [ ] 500 sales → one digest; no activity → no digest
- [ ] Recipients resolved **at send**; a mid-flight transfer reaches the new owner
- [ ] No PII read from any event payload
- [ ] **Only the registry viewer was built** — no template editor, no campaign composer
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-NTF-002 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented` — **Wave 6 does not open until all of Wave 5 is**
