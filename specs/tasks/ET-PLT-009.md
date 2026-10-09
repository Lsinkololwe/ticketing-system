# ET-PLT-009 · The audit trail — tasks

> **Spec** [`specs/_platform/009-audit-trail/spec.md`](../_platform/009-audit-trail/spec.md) · **Wave 7** · `blocked_by:` ET-PLT-001, 007, 008, ET-ADM-005
> **Screen** `Admin - Transactions & System.dc.html` *(Audit logs)*
> **Routes** `apps/admin/src/app/(dashboard)/system/audit`
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-PLT-009 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

Specs across the corpus say *"writes an audit row"* as an acceptance condition —
[`ET-ORG-003`](ET-ORG-003.md) BE-7's step-1 allows, [`ET-FIN-001`](ET-FIN-001.md) BE-8's manual
adjustments, [`ET-ADM-002`](ET-ADM-002.md) BE-2's config changes,
[`ET-ADM-003`](ET-ADM-003.md) BE-6's provider-raw access,
[`ET-PLT-013`](ET-PLT-013.md) BE-7's permission changes. **This spec is where those promises are
made good**, and R1's cross-reference test is what proves none was forgotten.

## R0 · Reconcile

```bash
grep -rn 'audit' specs/*/*/spec.md | grep -i 'writes an audit\|audited\|audit row' | wc -l
grep -rn 'AuditLog\|@Audited' backend --include='*.java' | grep -v /src/test/
```

Enumerate **every** "writes an audit row" acceptance box across the corpus in R0. That list is the
registry BE-1 must contain. Any promise without a registry entry is a promise the platform breaks
silently.

## A · Backend

### BE-1 · The registry, the `@Audited` annotation and the cross-reference test
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** the **annotated set equals the registry**; **every *writes an audit row* elsewhere
  in the corpus resolves** to a registry action.
- The second half is the load-bearing test, and it is unusual: it asserts against the **spec
  corpus**, not the code. It is what makes a cross-cutting promise checkable.

### BE-2 · The row, its five mandatory components and the `Clock`
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** every registry action produces a **complete** row — actor, action, subject, time,
  outcome. A row missing one is a row that cannot answer a question.
- Time from the injected `Clock` ([`ET-PLT-001`](ET-PLT-001.md) BE-3), so audit rows are testable.

### BE-3 · The per-subject hash chain and its verification
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- **Acceptance** **a directly modified row is detected**; **chains write in parallel**.
- Per subject, not global — a single global chain serialises every audit write in the platform,
  which at on-sale is a bottleneck on the hottest path. Per-subject chains write concurrently and
  still detect tampering within a subject.
- Test tampering by editing a row **directly in MongoDB**, bypassing the service.

### BE-4 · Redaction driven by the PII inventory
- **Spec** R4 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **a bank-account change records no full number**; **statuses and amounts are
  recorded in full**.
- Driven by [`ET-PLT-008`](ET-PLT-008.md) BE-1's inventory, so redaction cannot drift from the PII
  definition. The asymmetry matters: the audit log is a financial record, so amounts must be
  complete; it is not a copy of the personal data.

### BE-5 · Read auditing, with the recursion stopped at one level
- **Spec** R5 · **§5** T5 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **one read yields exactly one read row**; **an actor cannot filter out their own
  actions**.
- Auditing a read of the audit log is right, and auditing that audit is an infinite regress. One
  level, deliberately.
- The filter rule closes the obvious hole: an administrator who can exclude themselves from the view
  has an unaudited surface.

### BE-6 · Class-based retention, the purge and the checkpoint
- **Spec** R6 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **the chain verifies from a checkpoint after a purge.**
- Purging the head of a chain would break verification for everything after it; the checkpoint is
  what lets old rows go while the remaining chain stays provable.

### BE-7 · Write-failure isolation, the gap marker and the alert
- **Spec** R7 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **a forced failure on a payout approval completes the payout and alerts**, leaving
  a gap marker.
- The judgement call, and the spec makes it explicitly: an audit write must not fail a business
  operation. Blocking a payout because the audit store hiccuped is worse than a recorded gap — but
  the gap must be **visible**, not silent, which is what the marker and the alert are for.

### BE-8 · The subgraph half, including `myAuditTrail`
- **Spec** R5 · **§5** T8 · **depends** BE-5 · **parallel-safe** **no — shared identity SDL**
- **Acceptance** **a plain `ADMIN` sees no financial action**; a user sees **their own** trail.
- Reading the audit log is itself privileged, and finance actions are a narrower class than general
  administration.

## B · Contract

### GQL-1 · 4 queries, 1 mutation
- **depends** BE-8 · **parallel-safe** no

## C · Frontend — `Admin - Transactions & System.dc.html`

### FE-1 · Audit log browser
- **depends** GQL-1 · **parallel-safe** no
- Actor, action, subject, time, outcome. Filter by actor, action class, subject, range.
- **No edit or delete affordance anywhere** — same discipline as the ledger
  ([`ET-FIN-001`](ET-FIN-001.md) FE-3) and the config history ([`ET-ADM-002`](ET-ADM-002.md) FE-3).
- **The filter must not let an actor exclude themselves** (BE-5); if the UI offers an actor filter,
  it cannot offer "not me".
- Wide table scrolls in its own container.
- **testids** `audit-table`, `audit-row`, `audit-actor`, `audit-action`, `audit-subject`, `audit-outcome`, `audit-filter-actor`

### FE-2 · Chain verification
- **depends** BE-3 · **parallel-safe** yes
- Verify a subject's chain and show the result. A **failed verification is an alarm state naming
  the row**, not a red badge — it means someone edited the database directly.
- **testids** `audit-verify`, `audit-verify-result`, `audit-tamper-alarm`

### FE-3 · Gap markers
- **depends** BE-7 · **parallel-safe** yes
- Gaps rendered **in the timeline**, where they happened, not in a separate diagnostics page. A gap
  hidden elsewhere is a gap nobody sees.
- **testids** `audit-gap-marker`, `audit-gap-reason`

### FE-4 · Redaction is visible as redaction
- **depends** BE-4 · **parallel-safe** yes
- A redacted field reads as *redacted*, not as empty. Empty implies nothing was there.
- Amounts and statuses in full; account numbers masked.
- **testids** `audit-redacted-field`

### FE-5 · `myAuditTrail`
- **depends** BE-8 · **parallel-safe** yes
- In the user's own settings, across all three apps: what happened on my account, and who did it.
- **testids** `my-audit-row`

## D · Tests

### TS-1 · Registry *(L1 + corpus cross-reference)*
Annotated set equals registry. **Every *writes an audit row* acceptance box in the corpus resolves
to a registry action** — this test reads the specs.

### TS-2 · Completeness *(L3)* — every registry action yields a complete five-component row.

### TS-3 · Chain *(L3)*
A row edited **directly in MongoDB** is detected. Chains for different subjects write in parallel
without contention.

### TS-4 · Redaction *(L3)*
Bank-account change records no full number; amounts and statuses in full; redaction driven by the
ET-PLT-008 inventory, not a local list.

### TS-5 · Read auditing *(L3)* — one read, one row; an actor cannot filter out their own actions.

### TS-6 · Retention *(L3, frozen clock)* — purge by class; the chain verifies from the checkpoint after.

### TS-7 · Isolation *(L3 — the judgement call, tested)*
Force an audit-write failure during a payout approval: **the payout completes**, a gap marker is
written, an alert fires.

### TS-8 · Authorization *(L3)* — plain `ADMIN` sees no financial action; a user sees only their own trail.

### TS-9 · e2e *(L5, admin)*
Browser with filters; verification success and tamper alarm; gap markers in the timeline; redacted
fields legible as redacted; `myAuditTrail`. **No edit or delete affordance.** Loading, empty, error,
populated.

## E · Gate

- [ ] R0 enumerated every "writes an audit row" promise across the corpus
- [ ] Registry equals annotations; **every corpus promise resolves to a registry action**
- [ ] Every action produces a complete five-component row, timed from the injected `Clock`
- [ ] A directly-modified row is detected; per-subject chains write in parallel
- [ ] Redaction driven by the ET-PLT-008 inventory; amounts and statuses recorded in full
- [ ] One read yields one row; an actor cannot filter out their own actions
- [ ] Chain verifies from a checkpoint after a purge
- [ ] **An audit-write failure does not fail the business operation**, and leaves a visible gap plus an alert
- [ ] Plain `ADMIN` sees no financial action; users see their own trail
- [ ] No edit or delete affordance in the UI; gaps shown in the timeline; redaction reads as redaction
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-PLT-009 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
