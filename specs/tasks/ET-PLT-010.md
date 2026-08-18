# ET-PLT-010 · Schema evolution — event versions, GraphQL deprecation, document migration — tasks

> **Spec** [`specs/_platform/010-schema-evolution/spec.md`](../_platform/010-schema-evolution/spec.md) · **Wave 7** · `blocked_by:` ET-PLT-002, 003, 004, 005
> **Screens** — **none.** This spec's product is CI checks, upcasters and migration jobs. Building a screen for it is a defect.
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-010 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

Three schemas evolve independently — the event envelope, the GraphQL contract, and the documents —
and each breaks differently. This spec makes each kind of change **classified by CI** rather than
argued about in review.

## R0 · Reconcile

```bash
ls .github/workflows/
grep -rn 'schemaVersion\|@Deprecated\|upcast' backend --include='*.java' | grep -v /src/test/
```

Classify. The decisive question: **is any of this enforced, or is it convention?** A deprecation
policy nobody's build checks is a comment. Every row that is convention-only is `partially-satisfied`
at best, and this slice's job is to make it mechanical.

## A · Backend

### BE-1 · The compatibility table as a CI check
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Files** `.github/workflows/` — **the classification runs as a workflow step, not a repo script**,
  so it cannot be skipped locally and forgotten.
- **Acceptance** one change of **each kind** is classified correctly; **a `FORBIDDEN` change fails
  CI**. Write the forbidden change, watch CI fail, revert it.

### BE-2 · The upcaster interface, registry and dispatch
- **Spec** R2 · **§5** T2 · **depends** R0 · **parallel-safe** no
- **Acceptance** v1 and v2 of a changed event are handled **identically**; **chains compose** —
  v1→v2→v3 through two upcasters yields the same result as a direct v1→v3.

### BE-3 · Consumer acceptance of N and N−1, dead-lettering the rest
- **Spec** R2, R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** an **older and a newer** version both dead-letter, and **neither throws**.
- Both directions matter. A consumer that throws on an unknown version blocks its subscription;
  one that silently drops loses the message. Dead-lettering with a reason keeps it recoverable
  through [`ET-ADM-003`](ET-ADM-003.md) BE-7.

### BE-4 · Version-labelled publication metrics and the retirement gate
- **Spec** R3 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a version with **no publications for 30 days is retirable**; a retired one
  dead-letters.
- Retirement gated on observed traffic rather than on a calendar. "Nobody publishes v1 any more" is
  a measurement, and treating it as an assumption is how a straggler service breaks on deploy day.

### BE-5 · GraphOS usage reporting and the removal gate
- **Spec** R4 · **§5** T5 · **depends** R0 · **parallel-safe** no
- **Acceptance** **removing a still-used field fails CI.**
- The same principle as BE-4 on the GraphQL side: a field's deprecation window ends when clients
  stop calling it, and GraphOS knows that. Deprecating on a schedule instead breaks a mobile client
  that has not shipped an update.

### BE-6 · `schemaVersion` on every document and lazy migration
- **Spec** R5 · **§5** T6 · **depends** R0 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** documents at **three versions all read as current**; **two concurrent readers
  migrate once**.
- Lazy migration means a read of an old document upgrades it in place. Two readers hitting the same
  stale document must not both write — that is a lost update on a document nobody is thinking about.

### BE-7 · The eager migration job — batched, resumable, off-peak
- **Spec** R6 · **§5** T7 · **depends** BE-6 · **parallel-safe** no
- **Acceptance** **a restart re-migrates nothing**; **reservation latency is unaffected during a
  full run**.
- The same contention discipline as [`ET-FIN-005`](ET-FIN-005.md) BE-8 and
  [`ET-ADM-004`](ET-ADM-004.md) BE-8: a full-collection sweep must never compete with on-sale.

### BE-8 · The coordinated-type CI check
- **Spec** R7 · **§5** T8 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **a removed error-code constant fails without a plan reference.**
- Error codes, event names and status enums appear in the backend, the composed schema and the
  frontend at once. Removing one in a single place is the change that compiles and then fails at
  runtime in the client.

## B · Contract

BE-1, BE-5 and BE-8 **are** the contract work. The output is CI gates, not schema.

## C · Frontend

**None.** Two obligations inherited by the frontend, both already covered elsewhere:

- Codegen freshness in CI ([`ET-PLT-004`](ET-PLT-004.md) BE-8) — `npm run codegen && git diff --exit-code`,
  with **both caches cleared first**, or the check passes on stale output.
- A deprecated field still in use blocks its own removal (BE-5), so the frontend's migration off it
  is what unblocks the backend.

Building a "schema versions" screen is a defect. Nobody uses it, and GraphOS already shows this.

## D · Tests

### TS-1 · Classification *(L4, CI)*
One change of each kind classified correctly. **A `FORBIDDEN` change fails CI** — write it, watch it
fail, revert.

### TS-2 · Upcasting *(L1)*
v1 and v2 handled identically; chains compose; an upcaster is pure and testable without a broker.

### TS-3 · Consumer tolerance *(L3)*
Older and newer versions both dead-letter with a reason; **neither throws**; the subscription keeps
moving.

### TS-4 · Retirement *(L3)*
A version unpublished for 30 days is retirable; a retired version dead-letters rather than being
silently accepted.

### TS-5 · GraphQL removal gate *(L4, CI)*
Removing a still-used field **fails**; removing an unused one passes.

### TS-6 · Document migration *(L3)*
Three versions all read as current. **Two concurrent readers migrate once** — run them in parallel
and assert a single write.

### TS-7 · Eager job *(L3)*
Restart re-migrates nothing. Run it **concurrently with 200-against-50** and assert reservation
latency and inventory conservation.

### TS-8 · Coordinated types *(L4, CI)*
Removing an error-code constant fails without a plan reference.

## E · Gate

- [ ] R0 recorded; every convention-only rule classified honestly as unenforced
- [ ] Classification runs as a **CI workflow step**, not a local script
- [ ] A `FORBIDDEN` change was written and **seen to fail CI**
- [ ] Upcasters compose; v1 and v2 handled identically
- [ ] Unknown versions dead-letter with a reason and never throw
- [ ] Retirement gated on **observed** traffic, not a calendar
- [ ] Removing a still-used GraphQL field fails CI
- [ ] Three document versions read as current; concurrent readers migrate once
- [ ] Eager migration is resumable and does not disturb reservation latency
- [ ] Removing a coordinated constant fails without a plan reference
- [ ] **No screen was built for this spec**
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-010 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
