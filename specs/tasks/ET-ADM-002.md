# ET-ADM-002 · Platform configuration, feature flags and versioned settings — tasks

> **Spec** [`specs/admin/002-platform-configuration/spec.md`](../admin/002-platform-configuration/spec.md) · **Wave 6** · `blocked_by:` ET-PLT-005, ET-ORG-003, ET-FIN-002
> **Screen** `Admin - Platform Configuration.dc.html` — **read it first**
> **Routes** `apps/admin/src/app/(dashboard)/system/configuration`
> **Verify** `mvn -q -f backend/identity-service test -Dgroups=ET-ADM-002 -DfailIfNoTests=false`

Configuration that changes commission rates, hold periods and TTLs — which is to say,
configuration that can silently rewrite what people were charged. **R6 is the requirement that
matters: changes are prospective only.**

## R0 · Reconcile

Classify. The decisive questions:
- Is configuration **append-only versioned**, or updated in place? In-place is `contradicted` —
  you cannot answer "what was the commission rate on 3 March?" from an overwritten row.
- Does any config change apply **retroactively** to existing entities?
- Is there a **percentage rollout** on any flag? R5 says there is not.

## A · Backend

### BE-1 · The registry, its types and bounds, and the seeder
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** an unknown key **refuses**; **each bound refuses its violation**; seeding is
  idempotent.
- A closed registry with bounds, like the error registry. An arbitrary key-value store is a place
  to put a 500% commission rate.

### BE-2 · Append-only versioned storage, with the reason and the audit row
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **no row is ever updated**; **the value as at a past instant is recoverable**.
- That second clause is what makes a historical commission dispute answerable.

### BE-3 · Caching with cross-instance eviction
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** cold and warm answers **identical**; **nothing polls**; **no hot-path query in the
  warm case**.
- Configuration is read on the reservation path. A database read per reservation at 5,000/minute
  (**D-16**) is a self-inflicted bottleneck.

### BE-4 · The resolution order and the organization override
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** an override **replaces rather than merges**; **the source is always returned**.
- Returning the source is what makes "why is this organization on 3%?" answerable without reading
  code.

### BE-5 · The commission-override interaction in [`ET-FIN-002`](ET-FIN-002.md)'s resolver
- **Spec** R4 · **§5** T5 · **depends** BE-4 · **parallel-safe** **no — it amends ET-FIN-002**
- **Acceptance** **a 3% override beats a 2% charity tier and a 5% standard, asserted both ways.**
- This is the subtle one. [`ET-FIN-002`](ET-FIN-002.md) BE-1's rule is *lowest applicable rate
  wins*; an explicit override is not "applicable rate" — it **replaces** resolution. So it wins
  even when it is **higher**. Both directions must be tested, because implementing it as another
  band silently discards every override above the tier rate.

### BE-6 · Feature flags, their audiences and the kill-switch list
- **Spec** R5 · **§5** T6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **an unknown flag evaluates off**; **no percentage rollout exists**.
- Fail-off: a typo in a flag name disables a feature rather than enabling one nobody meant to ship.
  No percentage rollout because a buyer who can reserve and a buyer who cannot, for no reason they
  can see, is not an experiment — it is a bug report.

### BE-7 · The four prospective-only tests
- **Spec** R6 · **§5** T7 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** a **rate**, **hold**, **TTL** and **policy** change each leave **prior entities
  untouched**.
- The single most important behaviour in this spec. A commission rate change that reaches back
  into existing tickets alters what organizers were told they would be paid, retroactively, with
  no audit trail beyond the config row.

### BE-8 · The subgraph half; `SUPER_ADMIN` writes, `ADMIN` reads
- **Spec** R7 · **§5** T8 · **depends** BE-6 · **parallel-safe** **no — shared identity SDL**
- **Acceptance** an `ADMIN` **reads and cannot write**; the public contract exposes nothing.

## B · Contract

### GQL-1 · 5 queries, 4 mutations
- **depends** BE-8 · **parallel-safe** no

## C · Frontend — `Admin - Platform Configuration.dc.html`

### FE-1 · Configuration browser
- **depends** GQL-1 · **parallel-safe** no
- Grouped by domain. Each key shows current value, **bounds**, type, source (default vs override),
  and who last changed it and when.
- Bounds are shown **before** editing (BE-1), not discovered by a rejection.
- **testids** `config-group`, `config-row`, `config-value`, `config-bounds`, `config-source`, `config-last-changed`

### FE-2 · Change with reason and prospectivity notice
- **depends** BE-2, BE-7 · **parallel-safe** no
- Reason is **mandatory**. The confirmation states plainly: **this applies to future
  <entities> only; existing ones keep their current value.**
- For a commission-rate change, name what is unaffected — tickets already sold. Ambiguity here is
  what produces a panicked rollback.
- `SUPER_ADMIN` only; an `ADMIN` sees values with **no edit affordance**, not a disabled button.
- **testids** `config-edit`, `config-reason`, `config-prospective-notice`, `config-confirm`, `config-readonly-badge`

### FE-3 · Version history
- **depends** BE-2 · **parallel-safe** yes
- Every version with value, reason, actor, timestamp. **Value as at a past instant** is queryable
  from the UI — that is the feature that settles disputes.
- Append-only; **no edit or delete affordance**.
- **testids** `config-history-row`, `config-history-asof`, `config-history-reason`

### FE-4 · Feature flags
- **depends** BE-6 · **parallel-safe** yes
- On/off and audience. **Kill switches marked distinctly** — they are the controls someone reaches
  for during an incident and must be findable in ten seconds.
- **No percentage-rollout control**, because there is no percentage rollout (BE-6).
- **testids** `flag-row`, `flag-toggle`, `flag-audience`, `flag-killswitch`

### FE-5 · Organization overrides
- **depends** BE-4, BE-5 · **parallel-safe** yes
- Per-organization overrides listed separately from defaults. For commission, state that an
  override **replaces** resolution — **including when it is higher than the tier rate** (BE-5).
  A reviewer who assumes "lowest wins" will set an override that does not do what they expect.
- **testids** `override-row`, `override-organization`, `override-value`, `override-replaces-notice`

## D · Tests

### TS-1 · Registry *(L1/L3)* — unknown key refuses; every bound refuses its violation; seeding idempotent.

### TS-2 · Versioning *(L3)*
**No row ever updated** (assert by reflection over the repository); value as at a past instant
recoverable; every change carries a reason and an audit row.

### TS-3 · Cache *(L3)*
Cold equals warm; **nothing polls**; **no hot-path query in the warm case** — assert the query
count on a reservation, not the latency.

### TS-4 · Resolution *(L1)* — override replaces rather than merges; source always returned.

### TS-5 · Commission override *(L1 — the both-ways test)*
3% override beats a 2% charity tier **and** a 5% standard. Both assertions, in one test class, so
the "lowest wins" mistake cannot pass.

### TS-6 · Flags *(L1/L3)* — unknown evaluates off; **no percentage-rollout code path exists**.

### TS-7 · Prospectivity *(L3 — the four tests that matter)*
Rate, hold, TTL and policy changes each leave prior entities untouched. Seed entities before the
change and assert them unchanged after.

### TS-8 · Authorization *(L3)* — `ADMIN` reads, cannot write; `SUPER_ADMIN` can; public exposes nothing.

### TS-9 · e2e *(L5, admin)*
Browser with bounds shown; change with mandatory reason and prospectivity notice; history with
as-at query; flags with kill switches marked; overrides with the replaces-notice. `ADMIN` sees no
edit affordance. Loading, empty, error, populated.

## E · Gate

- [ ] R0 recorded; in-place updates or retroactive application classified `contradicted`
- [ ] Closed registry with bounds; unknown keys refuse
- [ ] Storage append-only; past values recoverable; reasons mandatory
- [ ] Warm cache issues no hot-path query; nothing polls
- [ ] Overrides replace rather than merge; source always returned
- [ ] **3% override beats both a 2% and a 5% tier — asserted both ways**
- [ ] Unknown flags evaluate **off**; no percentage rollout exists
- [ ] All four prospectivity tests green
- [ ] `ADMIN` reads and cannot write; `SUPER_ADMIN` writes
- [ ] Prospectivity is stated in the UI **before** confirmation
- [ ] Kill switches visually distinct
- [ ] `mvn -q -f backend/identity-service test -Dgroups=ET-ADM-002 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
