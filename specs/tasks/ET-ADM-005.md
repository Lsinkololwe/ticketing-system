# ET-ADM-005 · Observability — metrics, tracing, SLOs, health and alerting — tasks

> **Spec** [`specs/admin/005-observability-and-health/spec.md`](../admin/005-observability-and-health/spec.md) · **Wave 6** · `blocked_by:` ET-PLT-001, 003, 005, ET-FIN-005, ET-ADM-003
> **Screen** `Admin - Observability & Health.dc.html` — **read it first**
> **Routes** `apps/admin/src/app/(dashboard)/system/observability`
> **Verify** `mvn -q -f backend test -Dgroups=ET-ADM-005 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

**Forty metrics, four SLOs, nineteen alert rules.** Most of the artefacts live in the sibling
`docker-resources/` repo — Prometheus rules, Alertmanager routing, Grafana dashboards. A task
that creates any of those inside `ticketing-system/` is wrong.

> **Corpus note.** This spec has a broken cross-area link (**P4**): it writes `../005-reconciliation/`
> for [`ET-FIN-005`](ET-FIN-005.md), which lives under `finance/`. Fix it in this slice.

## R0 · Reconcile

```bash
grep -rn 'MeterRegistry\|Counter\.\|Timer\.\|@Timed' backend --include='*.java' | grep -v /src/test/
grep -rn 'correlationId\|MDC\|traceId' backend --include='*.java' | grep -v /src/test/
ls ../docker-resources/prometheus ../docker-resources/grafana 2>/dev/null
```

Classify. The two questions that matter most:
- Is there a **correlation id** that survives the gateway → service → bus → error path, or does each
  hop invent its own?
- Does any metric carry an **unbounded label** — a user id, an event id, a ticket reference? That is
  a cardinality explosion that takes Prometheus down, and it is `contradicted`.

## A · Backend

### BE-1 · Emit the forty registered metrics across the three services
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** the registry endpoint's metric set **equals** §4 — not contains, equals; **no
  unbounded label**.
- Equality both ways: a metric in §4 that nothing emits is a dashboard panel that stays empty and
  an alert that never fires.

### BE-2 · Correlation id — generation, propagation, MDC, envelope, error extensions
- **Spec** R4 · **§5** T2 · **depends** R0 · **parallel-safe** **no — every service**
- **Acceptance** **one purchase shows one id across the gateway, three services, the bus and the
  error response.**
- This is the single most useful thing in the spec. Without it, diagnosing a failed purchase means
  correlating five log streams by timestamp; with it, it is one grep. It must reach the **error
  extensions** too, so a user reporting a failure can quote the id.

### BE-3 · Tracing with the sampling policy and provider-span redaction
- **Spec** R5 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a purchase produces a **complete cross-service trace**; **no MSISDN in any span**.
- Spans are the easiest accidental PII leak in the platform — the phone number is a payment
  parameter, and it lands in a span attribute unless redaction is explicit. Feeds
  [`ET-PLT-008`](ET-PLT-008.md).

### BE-4 · Layered health with required-versus-optional dependencies
- **Spec** R6 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **each optional dependency stopped in turn leaves the service ready.**
- Mongo is required; a messaging provider is not. A readiness probe that fails when WhatsApp is down
  takes the platform out of the load balancer for a notification outage — the exact inversion
  [`ET-NTF-001`](ET-NTF-001.md) BE-4 forbids at the service layer.

### BE-5 · The four SLOs, their computation and error-budget tracking
- **Spec** R2 · **§5** T5 · **depends** BE-1 · **parallel-safe** yes
- **Files** `../docker-resources/prometheus/rules/` *(sibling repo)*, `backend/.../SloService.java`
- **Acceptance** each SLO matches a seeded fixture; **budget burn fires at 50% and 90%**.
- Burn-rate alerting rather than threshold alerting: it fires while there is budget left to protect.

### BE-6 · The nineteen alert rules, their runbooks and their routing
- **Spec** R3, R7 · **§5** T6 · **depends** BE-5 · **parallel-safe** no *(one rule file)*
- **Acceptance** **a definition without a runbook fails the build**; **each `PAGE` fires in a
  drill**.
- A page at 3am with no runbook is a page that wakes someone who then has to reverse-engineer the
  alert. Making the runbook a build requirement is what keeps them written.
- Drill them. An alert that has never fired is an alert that may not be routed.

### BE-7 · The business alerts and their runbooks
- **Spec** R7 · **§5** T7 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** **a simulated hour of zero sales during an open sales window pages.**
- The most valuable alert in the platform, and the one no infrastructure monitor produces: every
  service is green, every probe passes, and nobody can buy a ticket. Silence during an on-sale is
  the symptom.

### BE-8 · The health and alert graph surface
- **Spec** R8 · **§5** T8 · **depends** BE-4 · **parallel-safe** **no — shared identity SDL**
- **Acceptance** **health queries do not depend on the components they report on.**
- A health endpoint that reads Mongo to report that Mongo is down reports nothing when it matters.

### BE-9 · Grafana dashboards per audience
- **Spec** R2, R7 · **§5** T9 · **depends** BE-6 · **parallel-safe** yes
- **Files** `../docker-resources/grafana/dashboards/` *(sibling repo)*
- **Acceptance** each SLO, each business alert condition and the recovery queue are visible.

## B · Contract

### GQL-1 · 4 queries, 1 mutation
- **depends** BE-8 · **parallel-safe** no
- Every field `ADMIN` and `@tag`ged; the public contract exposes no health internals.

## C · Frontend — `Admin - Observability & Health.dc.html`

> **Infographics gate.**
> **Kernel:** *"Is anything wrong right now, and how much error budget is left?"*
> The focal element is **current state**, not a wall of charts. Forty metrics is exactly the input
> that becomes forty equal tiles and communicates nothing.
> Severity by colour, identity by label — never colour alone. Categorical hues cap at five.

### FE-1 · System health — the focal element
- **depends** GQL-1 · **parallel-safe** no
- One dominant state: healthy, degraded, or down — with **which component and why**. Required and
  optional dependencies are visually distinct (BE-4), because "WhatsApp is down" and "MongoDB is
  down" are not the same news.
- **testids** `system-health`, `component-health-row`, `component-required-badge`, `component-degraded-reason`

### FE-2 · SLOs and error budget
- **depends** BE-5 · **parallel-safe** yes
- Four SLOs, each with attainment and **budget remaining**. Budget is the actionable number — a
  99.4% figure means nothing without knowing how much of the month's allowance it consumed.
- If a burn chart is built: zero baseline, single y-axis, direct labels, the takeaway annotated on
  the chart next to the evidence.
- **testids** `slo-row`, `slo-attainment`, `slo-budget-remaining`, `slo-burn-rate`

### FE-3 · Active alerts
- **depends** BE-6 · **parallel-safe** yes
- Severity, component, age, **and a link to the runbook** — the runbook is the point of the alert
  reaching a human.
- Business alerts (BE-7) are not buried among infrastructure ones; zero sales during an open window
  is a first-class row.
- **testids** `alert-row`, `alert-severity`, `alert-runbook-link`, `alert-age`, `alert-business`

### FE-4 · Correlation-id lookup
- **depends** BE-2 · **parallel-safe** yes
- Paste an id, see the request across gateway, services and bus. This is the screen an operator
  actually uses when a user reports a failure and quotes the id from their error message.
- **testids** `correlation-lookup`, `correlation-timeline`, `correlation-span-row`

### FE-5 · Dead-letter and recovery depth
- **depends** [`ET-PLT-003`](ET-PLT-003.md) BE-6, [`ET-ADM-003`](ET-ADM-003.md) BE-9 · **parallel-safe** yes
- Depth by topic, oldest age, linked to the recovery queue. Do not rebuild the queue here — link to
  it.
- **testids** `deadletter-depth`, `deadletter-oldest`, `recovery-queue-link`

### FE-6 · No PII on this screen
- **depends** BE-3 · **parallel-safe** yes
- Traces, spans and correlation timelines carry ids, never phone numbers or names.
- **Acceptance** an e2e asserts no MSISDN-shaped string renders anywhere on the surface.

## D · Tests

### TS-1 · Metrics *(L3)* — the emitted set **equals** §4; no unbounded label, asserted per metric.

### TS-2 · Correlation *(L3)*
One purchase, one id, across gateway → three services → bus → error extensions. Assert on the id,
end to end.

### TS-3 · Tracing *(L3)* — complete cross-service trace; **no MSISDN in any span attribute**.

### TS-4 · Health *(L3)*
Each optional dependency stopped in turn leaves the service **ready**; a required one does not.
Health queries do not read the component they report on.

### TS-5 · SLOs *(L3)* — each matches a seeded fixture; burn fires at 50% and 90%.

### TS-6 · Alerts *(L4 + drill)*
A rule without a runbook **fails the build**. Every `PAGE` fires in a drill and routes.

### TS-7 · Business alerts *(L3)* — a simulated hour of zero sales in an open window pages.

### TS-8 · e2e *(L5, admin)*
Health with required/optional distinction; SLO budgets; alerts with runbook links; correlation
lookup; dead-letter depth. **No PII rendered.** Loading, empty (all-green **is** a designed
success state), error, populated.

## E · Gate

- [ ] **P4: the broken `../005-reconciliation/` link in this spec fixed**
- [ ] R0 recorded; any unbounded metric label classified `contradicted`
- [ ] Emitted metric set equals §4 exactly; no unbounded labels
- [ ] One correlation id across gateway, three services, bus and error extensions
- [ ] Complete cross-service trace; no MSISDN in any span
- [ ] Every optional dependency can be down with the service still ready
- [ ] Health queries do not depend on what they report on
- [ ] Four SLOs computed; budget burn at 50% and 90%
- [ ] No alert rule without a runbook; every `PAGE` drilled
- [ ] Zero-sales-during-open-window pages
- [ ] Prometheus, Alertmanager and Grafana artefacts live in `../docker-resources/`, not this repo
- [ ] **Infographics gate passed**; current state is the focal element, not forty tiles
- [ ] No PII renders on the observability surface
- [ ] `mvn -q -f backend verify -Dgroups=ET-ADM-005 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented` — **Wave 7 does not open until all of Wave 6 is**
