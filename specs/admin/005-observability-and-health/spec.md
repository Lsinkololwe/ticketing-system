# ET-ADM-005 · Observability — metrics, tracing, SLOs, health and alerting

> **Conformance** · PDI Phase 9 monitoring and alerting · MISSING_FEATURES Part 4 system health

## 1. Capability

Across this corpus, sixty-odd acceptance criteria end with the words *is a metric and
alerts*. Dead-letter depth. Unmatched reconciliation items. The lazy-repair rate. The
manual-validation ratio. Payment failure rate per provider. Amount at risk in the recovery
queue. Every one of those was written by a spec that knew something needed watching and
left the watching to somebody else.

This spec is that somebody. It declares the metric registry — every metric the platform
emits, its type, its labels and its owner — the four SLOs the platform commits to, the
alert rules and where they route, the correlation identifier that ties a support question to
a request, and the health surface that answers *is the platform working* without requiring
somebody to log into three dashboards.

The rule that shapes it is that **an alert must be actionable and it must page somebody who
can act**. A dashboard nobody opens is not monitoring. An alert with no runbook is a
notification. And a page at three in the morning for something that could have waited until
nine is how a team learns to ignore pages.

## 2. Design decisions

**One metric registry, declared here, emitted everywhere.** Every metric a spec asks for
appears in §4 with its name, type, labels and owner. A metric emitted but not registered is
a metric nobody knows about; a metric registered but not emitted is a dashboard that
silently shows nothing.

**Four SLOs, and they are about the user, not the machine.** Ticket purchase success rate,
purchase latency, gate scan latency and payout timeliness. CPU and memory are not SLOs —
they are inputs to an investigation that starts with one of these four getting worse.

**Alerts are split by urgency and route differently.** `PAGE` wakes somebody: money is
moving wrongly or the platform is down. `TICKET` creates work for the next working day.
`INFO` goes to a channel. Every alert declares its class, and §4's table is the whole set.

**Every alert names its runbook.** An alert with no runbook is a notification somebody
acknowledges and forgets. The runbook link is a required field on the alert definition, and
an alert added without one fails the build.

**A correlation id spans the request, the logs, the events and the errors.** Generated at
the gateway, propagated through headers, carried in every `EventEnvelope`
([ET-PLT-003](../../_platform/003-event-contract/) §4), returned in every error's
`extensions` ([ET-PLT-005](../../_platform/005-error-contract/) §4), and stamped on every
log line. A support conversation becomes one query.

**Tracing is sampled, and money paths are sampled at 100%.** A trace per request at on-sale
volume is a storage bill nobody sanctioned. Ten percent of ordinary traffic, everything on
the purchase, payout and refund paths, and everything that errors. The paths that matter are
the paths that get traced.

**Health is layered and honest.** Liveness says the process is running. Readiness says it
can serve. A composite health endpoint aggregates dependencies with per-dependency status.
Readiness reports unready when a **required** dependency is down, and degraded — still
serving — when an optional one is. Marking everything required means one slow provider takes
the platform out of rotation.

**Business alerts sit alongside technical ones.** *No tickets sold in the last hour during a
published on-sale* is more useful than any CPU graph, because it catches the failures that
leave every technical indicator green.

**Rejected alternatives**

- *Metrics emitted ad hoc per service.* Nobody knows what exists, and half the dashboards are empty.
- *CPU and memory as SLOs.* They are inputs to an investigation, not commitments to a user.
- *One alert severity.* Everything pages, and then nothing does.
- *Alerts without runbooks.* Acknowledged and forgotten.
- *100% trace sampling.* A storage bill nobody sanctioned, for data nobody reads.
- *A single health endpoint returning up or down.* One slow provider takes the platform out of rotation.
- *Technical monitoring only.* Every indicator green while nothing has sold for an hour.

## 3. Requirements

### ET-ADM-005-R1 · Every metric is registered before it is emitted

THE SYSTEM SHALL emit only metrics declared in §4, each with its declared type and labels.

**Acceptance**
- [ ] Every metric in §4 is emitted by the service named as its owner
- [ ] Every metric emitted by any service appears in §4 — a test enumerates the registry endpoint and compares
- [ ] Names follow `{domain}_{subject}_{unit}` and types are `counter`, `gauge`, `histogram` or `summary`
- [ ] Label cardinality is bounded — no metric is labelled by user id, ticket id or any unbounded value
- [ ] Every metric an earlier spec's acceptance criteria requires appears here, and §4 names which spec asked for it
- [ ] Adding a metric changes this spec's §4 in the same commit

### ET-ADM-005-R2 · Four SLOs, measured and reported

THE SYSTEM SHALL define the four §4 SLOs, measure them continuously and report attainment.

**Acceptance**
- [ ] Purchase success rate ≥ 99.0% of attempts that reach a terminal state, excluding user-declined payments
- [ ] Purchase latency p95 ≤ 3 s from `reserveTickets` to the payment intent being submitted
- [ ] Gate scan latency p95 ≤ 1 s for an online validation
- [ ] Payout timeliness: 95% of approved payouts settle within 24 hours
- [ ] Each is computed over a rolling 30-day window and reported with its error budget consumed
- [ ] Burning more than 50% of an error budget in a week raises a `TICKET` alert; more than 90% raises a `PAGE`
- [ ] A test asserts each SLO's computation against a seeded fixture

### ET-ADM-005-R3 · Alerts are classed, routed and every one has a runbook

THE SYSTEM SHALL classify every alert and SHALL route it by class, and no alert SHALL exist
without a runbook.

**Acceptance**
- [ ] Every alert in §4 declares `PAGE`, `TICKET` or `INFO`
- [ ] `PAGE` routes to on-call; `TICKET` creates work; `INFO` posts to a channel
- [ ] Every alert definition carries a runbook URL; a definition without one fails the build
- [ ] Every alert declares a for-duration, so a transient blip does not fire
- [ ] Alerts are deduplicated and grouped, so one incident produces one page
- [ ] Every `PAGE` alert has been fired in a drill at least once before it is relied upon
- [ ] A test asserts every alert definition has a runbook and a for-duration

### ET-ADM-005-R4 · One correlation id spans everything

THE SYSTEM SHALL generate a correlation id per external request and SHALL propagate it
through every log, event, error and downstream call.

**Acceptance**
- [ ] The gateway generates a correlation id when the request carries none, and preserves one it does
- [ ] It propagates as a header to every service and on to every provider call
- [ ] It is a component of every `EventEnvelope` ([ET-PLT-003](../../_platform/003-event-contract/) §4)
- [ ] It appears in `extensions.correlationId` on every GraphQL error ([ET-PLT-005](../../_platform/005-error-contract/) §4)
- [ ] Every log line carries it in structured form, not concatenated into the message
- [ ] A test follows one purchase and asserts the same id appears in the gateway log, all three services' logs, the bus envelope and the error response
- [ ] `causationId` chains a derived event to the one that caused it

### ET-ADM-005-R5 · Tracing is sampled, with money paths at full rate

THE SYSTEM SHALL trace requests with a sampling policy that captures every money path.

**Acceptance**
- [ ] Baseline sampling is `observability.trace.sample-rate` (10%)
- [ ] Reservation, payment, refund and payout paths sample at 100%
- [ ] Every errored request is sampled regardless of the baseline
- [ ] Spans cover the gateway, the router, each service, MongoDB, Redis and every provider call
- [ ] Provider spans carry the provider name and the outcome, and **no** account identifier or raw message
- [ ] Trace retention is configured and bounded
- [ ] A test asserts a purchase produces a complete trace across all three services

### ET-ADM-005-R6 · Health is layered and distinguishes required from optional

THE SYSTEM SHALL expose liveness, readiness and a composite health surface, and readiness
SHALL depend only on required dependencies.

**Acceptance**
- [ ] `/actuator/health/liveness` reports process health only and never checks a dependency
- [ ] `/actuator/health/readiness` reports unready when a **required** dependency is down
- [ ] Required dependencies per service are declared in §4; everything else is optional
- [ ] An optional dependency being down yields `DEGRADED` — the service still serves
- [ ] `systemHealth` aggregates every service with per-dependency status and last-checked time
- [ ] A degraded service is visibly degraded rather than silently reduced
- [ ] A test stops each optional dependency in turn and asserts the service stays ready

### ET-ADM-005-R7 · Business alerts catch what technical alerts miss

THE SYSTEM SHALL alert on business conditions that indicate failure while technical
indicators are healthy.

**Acceptance**
- [ ] No ticket sold in `observability.business.no-sales-window` (PT1H) while at least one event has an open sales window raises a `PAGE`
- [ ] Payment success rate below 90% over 15 minutes raises a `PAGE`
- [ ] Reservation expiry rate above 40% over an hour raises a `TICKET` — buyers are abandoning
- [ ] Zero check-ins 30 minutes into an event with sold tickets raises a `TICKET`
- [ ] Recovery-queue amount at risk above its threshold raises a `PAGE` ([ET-ADM-003](../003-transaction-recovery/) R8)
- [ ] Trial balance non-zero raises a `PAGE` immediately ([ET-FIN-005](../../finance/005-reconciliation/) R1)
- [ ] Each has a runbook naming the first three things to check

### ET-ADM-005-R8 · The health surface is queryable by an operator

THE SYSTEM SHALL expose system health and open alerts through the graph.

**Acceptance**
- [ ] `systemHealth` returns overall status, per-service status and per-dependency detail
- [ ] `transactionHealth` returns pending, failed and stuck counts, success rate and average processing time
- [ ] `systemAlerts(severity, unacknowledgedOnly)` lists open alerts
- [ ] `acknowledgeAlert(id, note)` records who acknowledged it and when
- [ ] Every field requires `ADMIN` and carries `@tag(name: "admin")`
- [ ] Acknowledging does not resolve — an alert resolves when its condition clears
- [ ] Health queries do not themselves depend on the components they report on

## 4. Model

### Metric registry

**Platform** — every service.

| Metric | Type | Labels | Asked for by |
|---|---|---|---|
| `http_server_requests_seconds` | histogram | service, method, uri, status | ET-PLT-001 |
| `graphql_operation_seconds` | histogram | service, operation, type | ET-PLT-004 |
| `graphql_errors_total` | counter | service, errorCode, errorType | ET-PLT-005 |
| `mongodb_operation_seconds` | histogram | service, collection, operation | ET-PLT-002 |
| `blocking_call_detected_total` | counter | service, class | ET-PLT-001 |

**Eventing**

| Metric | Type | Labels | Asked for by |
|---|---|---|---|
| `event_published_total` | counter | topic, eventType | ET-PLT-003 |
| `event_consumed_total` | counter | subscription, eventType, outcome | ET-PLT-003 |
| `event_deadletter_depth` | gauge | topic, subscription | ET-PLT-003 R6 |
| `event_consumer_lag_seconds` | gauge | subscription | ET-PLT-003 |
| `outbox_pending_total` | gauge | service | ET-PLT-003 R1 |

**Identity and organization**

| Metric | Type | Labels | Asked for by |
|---|---|---|---|
| `otp_requested_total` | counter | channel, outcome | ET-IDN-001 |
| `otp_verification_total` | counter | outcome | ET-IDN-001 |
| `user_lazy_repair_total` | counter | — | ET-IDN-002 R3 |
| `user_reconciliation_repairs_total` | counter | kind | ET-IDN-002 R4 |
| `keycloak_mirror_pending` | gauge | — | ET-ORG-002 R8 |
| `permission_resolution_seconds` | histogram | step | ET-ORG-003 R7 |
| `permission_unknown_total` | counter | permission | ET-ORG-003 R6 |

**Catalogue and ticketing**

| Metric | Type | Labels | Asked for by |
|---|---|---|---|
| `reservation_created_total` | counter | outcome | ET-TKT-001 |
| `reservation_expired_total` | counter | — | ET-TKT-001 R4 |
| `inventory_conflict_total` | counter | tierId-bucketed | ET-CAT-002 R2 |
| `ticket_issued_total` | counter | — | ET-TKT-002 |
| `ticket_validation_total` | counter | method, outcome | ET-TKT-003 |
| `checkin_manual_ratio` | gauge | eventId-bucketed | ET-TKT-003 R6 |
| `checkin_conflict_total` | counter | — | ET-TKT-003 R4 |

**Payment and finance**

| Metric | Type | Labels | Asked for by |
|---|---|---|---|
| `payment_attempt_total` | counter | provider, outcome | ET-PAY-001 |
| `payment_pending_over_limit` | gauge | — | ET-PAY-001 R5 |
| `payment_unmapped_status_total` | counter | provider | ET-PAY-001 R3 |
| `webhook_received_total` | counter | provider, outcome | ET-PAY-002 |
| `webhook_orphan_open` | gauge | — | ET-PAY-002 R5 |
| `webhook_signature_invalid_total` | counter | provider | ET-PAY-002 R1 |
| `ledger_trial_balance_delta` | gauge | — | ET-FIN-005 R1 |
| `commission_clawback_total` | counter | — | ET-FIN-002 R6 |
| `payout_settled_seconds` | histogram | — | SLO 4 |
| `reconciliation_open_items` | gauge | itemClass | ET-FIN-005 R6 |
| `reconciliation_oldest_item_age_seconds` | gauge | — | ET-FIN-005 R6 |
| `recovery_amount_at_risk` | gauge | source | ET-ADM-003 R8 |

**Notification and approvals**

| Metric | Type | Labels | Asked for by |
|---|---|---|---|
| `notification_sent_total` | counter | channel, category, outcome | ET-NTF-001 |
| `notification_suppressed_total` | counter | reason | ET-NTF-002 R4 |
| `approval_queue_depth` | gauge | queue | ET-ADM-001 R1 |
| `approval_oldest_age_seconds` | gauge | queue | ET-ADM-001 R7 |
| `approval_sla_breach_total` | counter | queue, level | ET-ADM-001 R3 |

Forty metrics. Nothing else is emitted.

### SLOs

| # | SLO | Target | Window | Measured from |
|---|---|---|---|---|
| 1 | purchase success rate | ≥ 99.0% | 30 d rolling | terminal reservations, excluding user declines |
| 2 | purchase latency p95 | ≤ 3 s | 30 d rolling | `reserveTickets` → intent submitted |
| 3 | gate scan latency p95 | ≤ 1 s | 30 d rolling | online `validateTicket` |
| 4 | payout timeliness | 95% ≤ 24 h | 30 d rolling | approval → settled |

Error-budget burn above 50% in a week is a `TICKET`; above 90% is a `PAGE`.

### Alert rules

| Alert | Condition | For | Class |
|---|---|---|---|
| `TrialBalanceNonZero` | `ledger_trial_balance_delta ≠ 0` | 1 m | **PAGE** |
| `NoSalesDuringOnSale` | zero tickets sold, open sales window | 1 h | **PAGE** |
| `PaymentSuccessRateLow` | success rate < 90% | 15 m | **PAGE** |
| `RecoveryRiskHigh` | `recovery_amount_at_risk` > K50,000 | 5 m | **PAGE** |
| `PayoutUnconfirmed` | any `PAYOUT_UNCONFIRMED` item | 0 m | **PAGE** |
| `ServiceUnready` | readiness failing | 2 m | **PAGE** |
| `ErrorBudget90` | any SLO budget > 90% burned | 0 m | **PAGE** |
| `DeadLetterDepth` | `event_deadletter_depth > 0` | 15 m | TICKET |
| `ReconciliationBacklog` | `reconciliation_open_items > 10` | 1 h | TICKET |
| `ReservationExpiryHigh` | expiry rate > 40% | 1 h | TICKET |
| `ApprovalSlaBreach` | any level-2 escalation | 0 m | TICKET |
| `KeycloakMirrorPending` | `keycloak_mirror_pending > 0` | 30 m | TICKET |
| `LazyRepairRateHigh` | `user_lazy_repair_total` rising | 1 h | TICKET |
| `ManualCheckinRatioHigh` | `checkin_manual_ratio > 0.1` | 15 m | TICKET |
| `WebhookSignatureFailures` | any invalid signature | 5 m | TICKET |
| `UnmappedProviderStatus` | `payment_unmapped_status_total` rising | 1 h | TICKET |
| `ErrorBudget50` | any SLO budget > 50% burned | 0 m | TICKET |
| `NotificationFailureRate` | channel failure rate > 20% | 30 m | INFO |
| `ConsumerLag` | `event_consumer_lag_seconds > 300` | 10 m | INFO |

Nineteen alerts. Each carries a runbook URL; a definition without one fails the build.

### Required versus optional dependencies

| Service | Required — readiness fails | Optional — degraded |
|---|---|---|
| catalog | MongoDB | Redis, Service Bus |
| booking | MongoDB | Redis, Service Bus, PawaPay |
| identity | MongoDB, Keycloak | Redis, Service Bus, WhatsApp, SMS, SMTP |
| gateway | Apollo Router | Redis |

MongoDB is booking's only required datastore: the outbox lives there too, staged in the
same transaction as the write it describes ([ET-PLT-003](../../_platform/003-event-contract/) R1),
so there is no second store whose loss could break the durability guarantee. PawaPay is optional
because a payment provider outage degrades purchasing without taking the service out of
rotation — buyers can still browse, check in and manage tickets.

### Correlation

```
gateway            X-Correlation-Id: generated or preserved
   ↓ header
service            MDC + structured log field
   ↓ EventEnvelope.correlationId              (ET-PLT-003 §4)
consumer           same id, new causationId
   ↓ extensions.correlationId                 (ET-PLT-005 §4)
client error       the same id the user can quote to support
```

### Tracing

| Path | Sample rate |
|---|---|
| baseline | 10% |
| reservation, payment, refund, payout | **100%** |
| any errored request | **100%** |

Provider spans carry the provider name and outcome. They carry **no** MSISDN, account
number or raw message.

### GraphQL

Subgraph `identity` — health is platform state.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `systemHealth` | query | `ADMIN` | `SystemHealth!` `@tag(name: "admin")` |
| `transactionHealth` | query | `ADMIN` | `TransactionHealth!` `@tag(name: "admin")` |
| `sloAttainment(window)` | query | `ADMIN` | `[SloAttainment!]!` `@tag(name: "admin")` |
| `systemAlerts(severity, unacknowledgedOnly)` | query | `ADMIN` | `[SystemAlert!]!` `@tag(name: "admin")` |
| `acknowledgeAlert(id, note)` | mutation | `ADMIN` | `SystemAlert!` `@tag(name: "admin")` |

`SystemHealth` carries `overallStatus`, `uptime`, per-service `ServiceHealth` and
per-dependency status with `lastCheckedAt`. `ServiceStatus` is `HEALTHY`, `DEGRADED`,
`UNHEALTHY`, `UNKNOWN`. `AlertSeverity` is `INFO`, `WARNING`, `ERROR`, `CRITICAL`, mapping
to the three routing classes.

Acknowledging records the actor and the note; it does not resolve. An alert resolves when
its condition clears.

### Configuration

| Property | Value |
|---|---|
| `observability.trace.sample-rate` | 0.10 |
| `observability.trace.money-path-rate` | 1.00 |
| `observability.trace.retention` | `P14D` |
| `observability.metrics.export-interval` | `PT15S` |
| `observability.business.no-sales-window` | `PT1H` |
| `observability.health.check-interval` | `PT30S` |
| `observability.log.level` | `INFO`, structured JSON |

Prometheus scrapes `/actuator/prometheus`; Grafana dashboards and Alertmanager routing live
in `../docker-resources/prometheus/` and `../docker-resources/grafana/`.

### Error codes

None introduced.

## 5. Tasks

- [ ] **T1 · Emit the forty registered metrics across the three services**
  - requirements: R1
  - files: `backend/*/src/main/java/com/pml/*/infrastructure/metrics/`
  - verify: the registry endpoint's metric set equals §4; no unbounded label
  - parallel-safe: yes — one service per agent
  - depends: —

- [ ] **T2 · Correlation id: generation, propagation, MDC, envelope, error extensions**
  - requirements: R4
  - files: `backend/api-gateway/.../filter/`, `backend/shared-library/.../observability/`
  - verify: one purchase shows one id across the gateway, three services, the bus and the error
  - parallel-safe: no — every service
  - depends: —

- [ ] **T3 · Tracing with the sampling policy and the provider-span redaction**
  - requirements: R5
  - files: `backend/*/src/main/resources/application.yml`, `.../config/TracingConfig.java`
  - verify: a purchase produces a complete cross-service trace; no MSISDN in any span
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · Layered health with required-versus-optional dependencies**
  - requirements: R6
  - files: `backend/*/src/main/java/com/pml/*/config/HealthConfig.java`
  - verify: each optional dependency stopped in turn leaves the service ready
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T5 · The four SLOs, their computation and error-budget tracking**
  - requirements: R2
  - files: `../docker-resources/prometheus/rules/`, `backend/.../SloService.java`
  - verify: each SLO matches a seeded fixture; budget burn fires at 50% and 90%
  - parallel-safe: yes
  - depends: T1

- [ ] **T6 · The nineteen alert rules, their runbooks and their routing**
  - requirements: R3, R7
  - files: `../docker-resources/prometheus/rules/`, Alertmanager routing
  - verify: a definition without a runbook fails the build; each `PAGE` fires in a drill
  - parallel-safe: no — one rule file
  - depends: T5

- [ ] **T7 · The business alerts and their runbooks**
  - requirements: R7
  - files: `../docker-resources/prometheus/rules/business.yml`
  - verify: a simulated hour of zero sales during an open window pages
  - parallel-safe: yes
  - depends: T6

- [ ] **T8 · The health and alert graph surface**
  - requirements: R8
  - files: `backend/identity-service/.../web/graphql/query/HealthQueryResolver.java`
  - verify: health queries do not depend on the components they report on
  - parallel-safe: no — shared SDL across identity's specs
  - depends: T4

- [ ] **T9 · Grafana dashboards per audience**
  - requirements: R2, R7
  - files: `../docker-resources/grafana/dashboards/`
  - verify: each SLO, each business alert condition and the recovery queue are visible
  - parallel-safe: yes
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| Business dashboards for organizers and finance | [ET-ADM-004](../004-analytics-and-statistics/) |
| The recovery queue whose risk figure this alerts on | [ET-ADM-003](../003-transaction-recovery/) |
| The trial balance whose delta this alerts on | [ET-FIN-005](../../finance/005-reconciliation/) |
| The dead-letter queue itself | [ET-PLT-003](../../_platform/003-event-contract/) |
| The audit trail, which is a record and not a metric | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Prometheus, Grafana and Alertmanager deployment | `../docker-resources/` |
| On-call rotation and incident process | operations, not a spec in this corpus |

Deliberately never in scope: **CPU and memory as SLOs** (inputs to an investigation, not
commitments to a user), **alerts without runbooks** (acknowledged and forgotten), and
**100% trace sampling** (a storage bill nobody sanctioned, for data nobody reads).
