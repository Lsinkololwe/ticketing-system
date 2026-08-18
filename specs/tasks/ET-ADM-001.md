# ET-ADM-001 · The approvals workbench — tasks

> **Spec** [`specs/admin/001-approvals-workbench/spec.md`](../admin/001-approvals-workbench/spec.md) · **Wave 6** · `blocked_by:` ET-PLT-004, 005, ET-ORG-001, ET-ORG-003, ET-CAT-001, ET-NTF-002
> **Screen** `Admin - Approvals Workbench.dc.html` (+ `Admin - Docs - Approvals & Config.dc.html`) — **read both first**
> **Routes** `apps/admin/src/app/(dashboard)/approvals/{page,organizers,events,documents}`
> **Verify** `mvn -q -f backend test -Dgroups=ET-ADM-001 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

This spec **owns no decision.** Approving an organizer belongs to [`ET-ORG-001`](ET-ORG-001.md);
approving an event to [`ET-CAT-001`](ET-CAT-001.md). What this owns is the **workbench**: queues,
claims, SLA, escalation and bulk — the machinery that makes reviewing at volume possible without
two reviewers deciding the same thing twice.

## R0 · Reconcile

Existing: `PendingApprovalStatsService` in booking-service and admin routes
`approvals/{organizers,events,documents}`.

The one thing to check hardest: **is any approval precondition implemented twice?** R4 says the
checks are **shared with the decision mutations**. A workbench that re-implements "is this
organization eligible for approval" will drift from the owning spec, and the drift shows up as a
queue offering an action the mutation refuses.

## A · Backend

### BE-1 · The two queue queries, their ordering and their indexes
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** `explain()` reports `IXSCAN`; **breached items float**.

### BE-2 · Claims — the unique index, the TTL, the expiry sweep
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** two parallel claims yield **one holder**; **an expired claim keeps its queue
  position**.
- The claim prevents two reviewers working the same application. Its expiry must not punish the
  applicant by sending them to the back of the queue because a reviewer went to lunch.

### BE-3 · The SLA clock, its pause, and the three escalation levels
- **Spec** R3 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **one escalation per level**; **the clock pauses in `CHANGES_REQUESTED`**.
- The pause is the fair part: while the platform is waiting on the applicant, the platform's clock
  is not running. Without it every changes-requested application breaches and the SLA becomes noise.

### BE-4 · Precondition checks, **shared** with the decision mutations
- **Spec** R4 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** each unmet precondition **refuses by name**; **there is no second
  implementation** — assert by source scan, the same discipline as
  [`ET-ORG-003`](ET-ORG-003.md) BE-5.

### BE-5 · The decision surface, the timeline and the atomic claim release
- **Spec** R5 · **§5** T5 · **depends** BE-2, BE-4 · **parallel-safe** no
- **Acceptance** **deciding another reviewer's claim refuses**; **every change appends a timeline
  row**. The decision and the claim release are one transaction.

### BE-6 · Bulk approval, its six rules and its per-id outcomes
- **Spec** R6 · **§5** T6 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** **no bulk rejection exists anywhere**; a re-submission **approves nothing twice**.
- Bulk approve, never bulk reject. Approving in bulk is an efficiency on items that already meet
  their preconditions; rejecting in bulk denies people without anyone having looked, and a
  rejection carries a reason that cannot be batched.
- Per-id outcomes, not a batch result — the reviewer must see which three of forty failed and why.

### BE-7 · Metrics — throughput, ageing, per-reviewer load, and the pre-breach alert
- **Spec** R7 · **§5** T7 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** `$match` first (**D-13**); **the alert fires before the SLA, not after**. An alert
  that fires on breach reports history; the point is to prevent it.

### BE-8 · The subgraph halves; every field `ADMIN` and `@tag(name: "admin")`
- **Spec** R1–R7 · **§5** T8 · **depends** BE-6 · **parallel-safe** **no — two SDL files**
- **Acceptance** the public contract exposes **no workbench field**.

## B · Contract

### GQL-1 · 6 queries, 3 mutations across identity and catalog
- **depends** BE-8 · **parallel-safe** no *(two subgraphs — sequence)*

## C · Frontend — `Admin - Approvals Workbench.dc.html`

> **Infographics gate before the queue header.**
> **Kernel:** *"What needs deciding now, and what is about to breach?"*
> Ageing is the focal dimension. A queue sorted by arrival with no breach signal is a list, not a
> workbench.

### FE-1 · Queue with SLA state
- **depends** GQL-1 · **parallel-safe** no
- Table shape, columns, grouping and empty state **from the screen** — do not invent them.
- **Breached items float** (BE-1); pre-breach is visually distinct from breached, and from healthy.
  Severity by colour, identity by label — never colour alone.
- **testids** `approval-queue`, `approval-queue-row`, `approval-sla-state`, `approval-age`, `approval-queue-empty`

### FE-2 · Claim and release
- **depends** BE-2 · **parallel-safe** no
- Claiming shows the holder and the TTL. An item claimed by someone else is **visibly theirs**, and
  its decision actions are absent rather than present-and-refusing.
- Expiry returns the item **in place** — the UI must not appear to reorder it.
- **testids** `approval-claim`, `approval-claimed-by`, `approval-claim-ttl`, `approval-release`

### FE-3 · Decision surface
- **depends** BE-5, [`ET-ORG-001`](ET-ORG-001.md) FE-8, [`ET-CAT-001`](ET-CAT-001.md) FE-5 · **parallel-safe** no
- **"Approve" / "Reject" / "Request Changes"** — exact verbs, never vaguer.
- Unmet preconditions are shown **by name, before** the reviewer commits (BE-4). Discovering the
  block on submit wastes the review.
- Reject and request-changes are distinct actions with distinct consequences
  ([`ET-ORG-001`](ET-ORG-001.md) BE-5) — the UI must not blur them into "decline".
- **testids** `approval-approve`, `approval-reject`, `approval-request-changes`, `approval-precondition-<name>`

### FE-4 · Timeline
- **depends** BE-5 · **parallel-safe** yes
- Every change, with actor and time. Append-only, and **no edit affordance** — the same discipline
  as the ledger ([`ET-FIN-001`](ET-FIN-001.md) FE-3).
- **testids** `approval-timeline`, `approval-timeline-row`

### FE-5 · Bulk approve
- **depends** BE-6 · **parallel-safe** yes
- Selection, preconditions checked **before** the action, **per-id outcomes** after — three of
  forty failed, and why.
- **No bulk reject anywhere in the UI.** Not disabled, not hidden — absent.
- **testids** `bulk-select`, `bulk-approve`, `bulk-result-row`, `bulk-result-failed`

### FE-6 · Workbench metrics header
- **depends** BE-7 · **parallel-safe** yes
- Pending count, oldest item age, breaching-soon count, per-reviewer load.
- One focal figure — **breaching soon** — because it is the only one that requires action right
  now. The rest are supporting. Do not tile four equal stat cards; that is the failure the
  infographics gate exists to catch.
- **testids** `workbench-pending`, `workbench-oldest`, `workbench-breaching-soon`, `workbench-reviewer-load`

## D · Tests

### TS-1 · Queues *(L3)* — `IXSCAN`; breached items float; ordering stable under insertion.

### TS-2 · Claims *(L3)*
Two parallel claims → one holder (index live via MCP); expiry keeps queue position; TTL sweep
idempotent.

### TS-3 · SLA *(L3, frozen clock)*
One escalation per level, asserted by running the sweeper repeatedly; the clock **pauses** in
`CHANGES_REQUESTED` and resumes on resubmission.

### TS-4 · Preconditions *(L1 + source scan)*
Each refuses by name; **no second implementation exists**.

### TS-5 · Decisions *(L3)*
Deciding another's claim refuses; decision and claim release are atomic; every change appends a
timeline row.

### TS-6 · Bulk *(L3)*
**No bulk rejection exists** — assert the mutation is absent from the composed schema.
Re-submission approves nothing twice. A mixed batch returns per-id outcomes.

### TS-7 · Metrics *(L3)* — `$match` first; the alert fires **before** the SLA.

### TS-8 · Contract *(L4)* — public contract exposes no workbench field.

### TS-9 · e2e *(L5, admin)*
- Queue: loading, **empty** (a cleared queue is a success state and should look like one), error,
  populated.
- Claim → decide → timeline. Another reviewer's claim shows no decision actions.
- Precondition blocks shown before commit.
- Bulk approve with mixed outcomes; **no bulk reject control exists anywhere**.
- Breach and pre-breach visually distinct, and distinguishable without colour.
- Compliance: teal, Inter, tokens, `data-brand="admin"`.

## E · Gate

- [ ] R0 recorded; any duplicated precondition check classified `contradicted`
- [ ] Breached items float; queues `IXSCAN`
- [ ] Two parallel claims yield one holder; expiry preserves queue position
- [ ] One escalation per level; SLA clock pauses in `CHANGES_REQUESTED`
- [ ] Preconditions shared with the owning specs — **one implementation**
- [ ] Deciding another reviewer's claim refuses; decision and release are atomic
- [ ] **No bulk rejection exists in the schema or the UI**
- [ ] Bulk returns per-id outcomes
- [ ] The pre-breach alert fires before the SLA
- [ ] Both `.dc.html` screens read; table shape and empty state match
- [ ] **Infographics gate passed**; breaching-soon is the single focal figure
- [ ] Approve / Reject / Request Changes use exactly those words
- [ ] `mvn -q -f backend verify -Dgroups=ET-ADM-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
