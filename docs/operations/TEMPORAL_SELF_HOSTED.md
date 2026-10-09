# Temporal, self-hosted — setup, staging trial and runbook

**Decisions:** ROADMAP D-21 (Temporal), D-28 (self-hosted), D-33 (staging first, then production), D-34
(Schedules follow the code). **Normative source:**
[`specs/_platform/015-durable-execution/spec.md`](../../specs/_platform/015-durable-execution/spec.md) §4.
**Configuration:** [`docker-resources/temporal/self-hosted/`](../../docker-resources/temporal/self-hosted/README.md) (compose, server config, search attributes).

> ### STATUS: NOT YET PROVISIONED
> | | |
> |---|---|
> | Staging Temporal service | **not provisioned** |
> | Production Temporal service | **not provisioned** |
> | D-33 trial (section 6) | **not run**; every box below is unticked on purpose |
> | What exists | `docker-resources/temporal/self-hosted/` was brought up on a developer machine with Docker: schema created, namespace `ticketing` with 720 h (30 d) retention, six search attributes registered, 512 shards confirmed in the database. That proves the configuration starts. It proves nothing about load, failure behaviour, backups, TLS or the services' workflows on this server. |
> | Unit, replay and lint gates | green in CI (`.github/workflows/temporal.yml`); they are not staging evidence |
>
> Do not describe Temporal as "set up" or "production-ready" until section 6 is signed off.

The platform runs its own Temporal service. Workflow history lives in the platform's PostgreSQL and
payloads carry ids only (ET-PLT-015 R7).

| Environment | Temporal | Store |
|---|---|---|
| Development | `docker compose --profile temporal up -d dev_temporal` in `docker-resources`; gRPC `127.0.0.1:7233`, UI `http://localhost:8233`, namespace `ticketing` | one SQLite file; one process; developer machine only |
| Staging | `docker-resources/temporal/self-hosted` on one host, one replica of each service | PostgreSQL `temporal`, `temporal_visibility` (own instance) |
| Production | the same images and config, 2+ replicas per service, managed PostgreSQL | its own PostgreSQL, never shared with staging |

---

## 1 · What a production service is made of

Four services: **frontend** (gRPC, the platform connects here), **history** (workflow state, timers),
**matching** (task queues), **worker** (Temporal's internal workflows). `docker-resources/temporal/self-hosted/docker-compose.yml`
runs each as its own container so one can restart or scale alone. Scale history and matching first.
Membership is kept in the database, so replicas need no seed list.

## 2 · Decisions (cannot change later, or must be recorded)

| Decision | Value | Why |
|---|---|---|
| `numHistoryShards` | **512**, staging and production | Immutable after the first start. The D-16 peak (5,000 reservations a minute, about 83 a second) is far below what 512 shards carry; headroom for several times that. Same value in staging so its behaviour predicts production. |
| Persistence | PostgreSQL 16, databases `temporal` and `temporal_visibility`, plugin `postgres12` | D-28 |
| Retention | 30 days (`ticketing` namespace) | ET-PLT-015 §4 |
| Server version | `temporalio/server:1.31.3`, admin-tools 1.31.3, UI 2.55.0 | pinned in `docker-resources/temporal/self-hosted/.env.example`; upgrade one minor at a time |
| Namespace | `ticketing`, one per environment | ET-PLT-015 §2 |
| Search attributes | `BusinessId`, `TenantId`, `OrganizationId`, `EventId`, `BusinessStatus`, `ProcessKind` (Keyword) | `docker-resources/temporal/self-hosted/search-attributes.conf` |

Environment-specific values to fill in at provisioning (placeholders; nothing below exists yet):

| Item | Staging | Production |
|---|---|---|
| Frontend address (`TEMPORAL_ADDRESS`) | `ENV-SPECIFIC: <staging-temporal-frontend-host>:7233` | `ENV-SPECIFIC: <prod-temporal-frontend-host>:7233` |
| PostgreSQL host (`POSTGRES_SEEDS`) | `ENV-SPECIFIC: <staging-pg-host>` | `ENV-SPECIFIC: <prod-pg-host>` |
| UI URL (behind SSO) | `ENV-SPECIFIC: <staging-temporal-ui-host>` | `ENV-SPECIFIC: <prod-temporal-ui-host>` |
| Database password source | `ENV-SPECIFIC: secret store reference` | `ENV-SPECIFIC: secret store reference` |
| TLS certificates | `ENV-SPECIFIC` | `ENV-SPECIFIC` |
| Recorded shard count | `______` (expected 512) | `______` (expected 512) |

## 3 · Provision (per environment)

1. PostgreSQL 16 with a `temporal` user; TLS to the database; only the Temporal services can reach it
   (ET-PLT-002 §4: no application service has these credentials). Apply the tuning notes in
   `docker-resources/temporal/self-hosted/README.md`.
2. On the Temporal host: `cd docker-resources/temporal/self-hosted && cp .env.example .env`, set `POSTGRES_SEEDS`,
   `POSTGRES_PASSWORD` (from the secret store), keep `NUM_HISTORY_SHARDS=512`. Staging can keep the bundled
   `postgres` container only for a throwaway trial; the trial of record uses the intended PostgreSQL.
3. `docker compose up -d`. The jobs `temporal-schema` (databases and schemas), `temporal-config` and
   `temporal-init` (namespace with retention, search attributes) run and exit 0.
4. Turn on TLS on the frontend, put the UI behind OIDC SSO (Keycloak), expose metrics (port 9090 of each
   service) to Prometheus and scrape the services' SDK metrics.
5. Checks:

```sh
temporal operator cluster health --address $TEMPORAL_ADDRESS
temporal operator namespace describe --address $TEMPORAL_ADDRESS --namespace ticketing   # retention 720h0m0s
temporal operator search-attribute list --address $TEMPORAL_ADDRESS --namespace ticketing
# shard count, from the database (must equal the recorded value):
psql -h $POSTGRES_SEEDS -U temporal -d temporal -tAc 'select count(*) from shards'
```

- [ ] Both databases are in the platform's backup schedule; one restore rehearsed into a scratch stack.
- [ ] Shard count recorded in section 2.

## 4 · Point the services at it

The services run the `prod` profile (`application-prod.yml`):

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `TEMPORAL_ADDRESS` | the frontend `host:7233` |
| `TEMPORAL_NAMESPACE` | `ticketing` |
| `TEMPORAL_TLS_ENABLED` | `true` once the frontend serves TLS |

- [ ] All three services start; each log shows its workers polling their task queues.
- [ ] `temporal task-queue describe` shows pollers on `booking-checkout`, `booking-provider`,
      `booking-finance`, `booking-recon`, `catalog-lifecycle`, `identity-onboarding`, `identity-notify`.
- [ ] Schedules exist with the code's timing: `booking-recon-escrow`, `booking-recon-escrow-journal`,
      `booking-recon-alerts`, `booking-recon-weekly-summary`, `identity-user-reconciliation`,
      `identity-group-mirror-repair`.

## 5 · Staging trial script (D-33)

Run against the staging service with PawaPay's sandbox. For every drill, open the workflow in the Web UI
and check the history reads as the business step, then record the workflow id, result and who ran it.

| # | Drill | Steps | Pass when | Done |
|---|---|---|---|---|
| 1 | Purchase | reserve, pay in sandbox, receive ticket | `purchase/{reservationId}` completes; one ticket; one journal entry | [ ] |
| 2 | Abandoned purchase | reserve, never pay | seats return after the grace period | [ ] |
| 3 | Payout | complete an event, shorten the hold, request and approve a payout | `payout/{escrowAccountId}` completes; journal shows the settlement; second request while open is refused | [ ] |
| 4 | Refund | buyer requests a refund; it waits; an approver approves | `refund/{ticketId}` completes once; the 2-day escalation fires if left | [ ] |
| 5 | Late-payment refund | pay after the 5-minute grace has released the seats (D-22) | money is refunded, no ticket exists, no double refund | [ ] |
| 6 | Cancellation | cancel an event with sold tickets | every ticket refunded with no person | [ ] |
| 7 | Bank verification / chargeback | add an account; a test chargeback two days from deadline | one `5050` entry; finance lead alerted 24 h before | [ ] |
| 8 | Worker crash mid-flow | `docker stop`/kill a booking pod while a purchase waits for payment, then pay | another pod finishes it; nothing lost or doubled | [ ] |
| 9 | Rolling deploy mid-flow | redeploy booking while a refund waits for approval, then approve | approval completes the same execution; no non-determinism failure | [ ] |
| 10 | Server restart | restart `temporal-history` (then `temporal-frontend`) during a waiting refund | the refund still completes; clients reconnect | [ ] |
| 11 | Schedule overlap | pause the recon worker so a run exceeds its interval, resume | overlap policy SKIP: no concurrent runs; next fire runs normally; an operator's pause is kept after redeploy (D-34) | [ ] |
| 12 | Backup/restore | restore both databases into a scratch stack | a running workflow from the backup resumes | [ ] |
| 13 | Load | 5,000 reservations a minute for 10 minutes (D-16) | schedule-to-start p99 on `booking-checkout` under 1 s; persistence p99 under 50 ms; no task failures | [ ] |

Signed off by engineering `______` and finance `______`, date `______`.

## 6 · Exit criteria for production

All must hold, with evidence linked here:

- [ ] Section 5 drills 1 to 13 pass on the staging service of record (not the local compose).
- [ ] Zero unexplained workflow task failures during the trial; no non-determinism error from a deploy.
- [ ] Dashboards and the alerts in section 8 exist and fired correctly in a test.
- [ ] Backups taken and one restore rehearsed; RPO/RTO written down.
- [ ] TLS on the frontend and to PostgreSQL; UI behind SSO; database credentials from the secret store.
- [ ] Shard count in the database equals the recorded value.
- [ ] The runbook (section 7) walked through by someone other than its author.
- [ ] Production then repeats sections 3 and 4 with its own databases; one real K5 purchase and refund.
      Staging and production never share a Temporal service or database.

## 7 · Runbook basics

Tools: Web UI (`:8233`) and `temporal` CLI with `--address $TEMPORAL_ADDRESS --namespace ticketing`.

**Find a workflow.** By id (`purchase/{reservationId}`) or by search attribute:
`temporal workflow list --query 'ProcessKind="PURCHASE" AND BusinessId="<id>"'`. MongoDB is the record;
the workflow explains why a document is where it is.

**Stuck workflow (not progressing).** `temporal workflow describe -w <id>` and `show -w <id>`. Look at
pending activities (retrying with what error?) and pending timers. A workflow waiting for a signal or a
timer is not stuck. If an activity fails repeatedly, fix the cause (provider down, bad data); the retry
picks up on its own. Do not restart pods hoping to unstick it; pods are stateless.

**Failed-activity retries.** Activities retry with their configured policy (the service's `*Rules`
class). A non-retryable refusal (`Refusals`) fails the workflow step by design and surfaces the `ErrorCode`.
To force an immediate retry of a backed-off activity: `temporal activity ...` (pause/unpause/reset) or
signal the workflow if it exposes one. Escalations go to ET-ADM-003.

**Workflow task failure / replay failure (non-determinism).** Symptom: workflow task failures with
"nondeterministic" in the UI after a deploy. Cause: a running workflow's command sequence changed (timer,
activity, child, signal handler added/removed/reordered). Action: roll back the worker deploy
(below); affected workflows resume on their own. Then reintroduce the change behind `Workflow.getVersion`
(DURABLE_EXECUTION section 5) with a recorded-history replay test. Never reset a workflow to hide it
without finding the cause.

**Queue backlog.** Alert on schedule-to-start latency or backlog age. `temporal task-queue describe
--task-queue <q>` shows pollers and backlog. No pollers: the service for that queue is down; restart it.
Pollers present but backlog grows: scale that service's pods or raise `TEMPORAL_ACTIVITY_EXECUTORS`; check
whether a downstream (PawaPay, Keycloak) is rate-limiting, since `booking-provider` is deliberately a
single rate-limited lane.

**Rollback of a bad workflow deploy.** Redeploy the previous image of the service. Because histories are
replayed, the previous code resumes every execution that has not yet passed the bad change; executions
that already ran the new code's commands may fail replay on the old code. Check section 7 "replay failure"
and, if needed, roll forward with a `getVersion`-guarded fix instead.

**Terminate / cancel / reset.** Prefer cancel (runs compensation), then terminate (no cleanup):
`temporal workflow cancel -w <id>`; `temporal workflow terminate -w <id> --reason "<ticket>"`.
Reset to an earlier event only with engineering lead approval and after confirming every activity after
that point is idempotent: `temporal workflow reset -w <id> --event-id <n> --reason "<ticket>"`.
Terminating a `purchase/*`, `payout/*` or `refund/*` execution can leave money in an intermediate
state: check the document and ledger in MongoDB and the provider first. Record every manual action.

**Server problems.** PostgreSQL unavailable: frontend and history return errors, workers keep retrying;
no data is lost; recover the database. A single Temporal service down: restart it; others keep serving.
The UI can cancel and signal, so it stays behind SSO.

**Upgrades.** Release notes first; run `temporal-schema` (update-schema) for both databases before the new
server version starts, one minor version at a time; staging before production.

## 8 · Monitoring and alerts

Sources: Temporal server metrics (port 9090 on each service), SDK metrics from the three services,
PostgreSQL metrics.

| Metric | Alert |
|---|---|
| `temporal_workflow_task_schedule_to_start_latency` by task queue (SDK) | p99 above 1 s for 5 min on `booking-checkout`; above 30 s on the others |
| task-queue backlog age / `temporal_activity_schedule_to_start_latency` | backlog age above 1 min on `booking-checkout`, above 10 min elsewhere |
| `workflow_task_execution_failed` / `workflow_failed` (SDK) | any non-determinism failure pages; sustained failure rate above 1% |
| `activity_execution_failed` by activity | rising rate on provider activities |
| poller count per task queue | zero pollers for 2 min |
| server `service_errors`, `service_latency` (frontend, history) | p99 above 1 s; error rate above 1% |
| `persistence_latency` / `persistence_errors` | p99 above 100 ms; any sustained errors |
| history shard lock latency / `shard_closed` | repeated shard ownership churn |
| PostgreSQL: connections, replication lag, disk, WAL, table bloat | above 80% of limit / 80% disk |
| Schedules | a schedule not fired in twice its interval; unexpectedly paused |
| Running workflow counts for `refund/*`, `payout/*` past their expected age | open longer than the business timer allows |
| Frontend certificate expiry | under 14 days |

Dashboards: one for workflow health by task queue, one for server and persistence, one for PostgreSQL.
Alerts are not configured yet.

## 9 · Running it

- **Upgrades:** section 7.
- **Backups:** the two databases are the whole of Temporal's state; restore them together.
- **Watch:** section 8; a non-determinism error means a deploy changed a running workflow (see CLAUDE.md
  and DURABLE_EXECUTION section 5).
