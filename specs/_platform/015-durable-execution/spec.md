# ET-PLT-015 · Durable execution — Temporal as the process layer

> **Conformance** · ROADMAP D-21 · CONVENTIONS §0, §9, §10 · amends ET-PLT-001 R1, ET-PLT-006 §2, ET-PLT-012 §4

## 1. Capability

Every business process on this platform that spans more than one step, one service, one provider
round trip or one moment in time — a purchase waiting on a mobile-money answer, a payout that must
settle or reverse, an escrow held until seven days after an event, an organizer approval that grants
roles in Keycloak — needs three things a request handler cannot give it: **state that survives a
crash**, **timers that fire even when no pod is running the code that set them**, and **compensation
that runs exactly when a later step fails**.

This spec admits Temporal to provide them. A process is a **workflow**: deterministic code whose
every step is recorded in the Temporal server's history, replayed after a crash, and resumed where
it stopped. The work a process does — writing MongoDB, staging an outbox envelope, calling PawaPay
or Keycloak — is an **activity**: ordinary Spring code, retried by the server, and idempotent by
construction.

MongoDB stays the system of record. Service Bus stays the broadcast layer for the ET-PLT-003 facts.
Temporal replaces the hand-rolled sagas, `@Scheduled` sweeps, Redis sweep locks and in-memory event
chains that today try to be a process engine and cannot promise what one promises.

## 2. Design decisions

**Workflows decide, activities do.** Workflow code holds sequence, timers, retries and
compensation. It never touches MongoDB, Redis, Service Bus, a provider or the wall clock, because
it is replayed: anything non-deterministic read during the first run reads differently on replay
and the history no longer matches. Every such call is an activity.

**MongoDB stays the system of record; a workflow's status is a projection.** Each activity that
changes business state runs one reactive transaction that advances the document's status by
compare-and-set and stages any outbox envelope. GraphQL reads the document, never workflow state.
A retried activity finds the status already moved and returns what is stored.

**Temporal first, MongoDB second.** A mutation starts or updates the workflow; the first write
happens in an activity. No resolver saves a document and then starts a workflow, because that is
the dual write the outbox exists to remove, reintroduced between two stores.

**Service Bus carries facts, Temporal carries processes.** A cross-service fact still leaves
through the outbox and a topic. The receiving consumer turns it into a start or signal and does
nothing else. Temporal cannot replace topic fan-out without every publisher holding a recipient
list.

**Workflow ids are business ids.** `payout/{escrowAccountId}`, `event-finance/{eventId}`. A
deterministic id makes a start idempotent, lets a webhook or consumer address the execution without
a lookup table, and makes the Web UI a recovery console keyed by what operators already know.

**One namespace per environment, task queues per concern.** Services share a namespace so a
booking workflow can address another service's execution by id; they are separated by task queue
so checkout never queues behind back-office work and a provider rate limit is enforced across
every pod.

**The SDK is blocking, so it is confined.** Client calls go through one adapter per service under
`infrastructure/temporal`, on `boundedElastic`. Activity methods are synchronous by contract and
run on the worker's own executor, never on a Netty event loop, so an activity may await a reactive
chain. BlockHound still draws the line at runtime.

**Payloads carry ids, never personal data.** Workflow ids, inputs and history are stored in plain
text on the Temporal server. A phone number or a name in a workflow input is personal data in a
store ET-PLT-008 does not govern. Activities load details by id.

**Rejected alternatives**

- *Keep hand-rolled sagas.* The Phase 0 findings (F-030) are what a persisted state machine without an engine looks like after a year: sweeps nobody fires, listeners that never run, recovery services that report and do not recover.
- *Temporal signals as the cross-service bus.* Point-to-point by construction; a new consumer becomes a change to every publisher.
- *A namespace per service with Nexus.* Worth its setup only when services have separate owners and blast radii.
- *Starting a workflow from a transaction.* A gRPC round trip inside a MongoDB transaction holds locks for the network's latency, and a rolled-back transaction cannot un-start a workflow.
- *A payload codec at launch.* Encryption with a Key Vault key is the second line of defence; ids-only payloads are the first, and they are checkable in a unit test.

## 3. Requirements

### ET-PLT-015-R1 · Every task queue is declared once, and each service polls only its own

THE SYSTEM SHALL run workers only on the task queues §4 registers, and each service's configuration
SHALL declare exactly the queues §4 assigns it.

**Acceptance**
- [ ] Every `spring.temporal.workers[].task-queue` in a service's `application.yml` is a §4 row owned by that service, and every §4 row owned by the service is declared
- [ ] Task queue names are constants in the service's `infrastructure/temporal/TaskQueues`, and no `@WorkflowImpl` or `@ActivityImpl` names a queue by literal
- [ ] The namespace and frontend address come from `TEMPORAL_NAMESPACE` and `TEMPORAL_ADDRESS`, with development defaults only

### ET-PLT-015-R2 · A workflow id is derived from the business id, never generated

THE SYSTEM SHALL start every workflow under an id built from the business identifier §4 names for
its type, with the conflict policy §4 declares.

**Acceptance**
- [ ] Each workflow type has one static id function in `infrastructure/temporal/WorkflowIds`, and no call site concatenates an id
- [ ] Starting the same business process twice reaches one execution, or is refused where §4 declares `FAIL`

### ET-PLT-015-R3 · Workflow code is deterministic

THE SYSTEM SHALL keep every workflow implementation free of I/O, wall-clock time, randomness and
thread use, obtaining time, ids and sleeps only through `io.temporal.workflow.Workflow`.

**Acceptance**
- [ ] No class under `workflow/` implementing a `@WorkflowInterface` references a repository, `ReactiveMongoTemplate`, `StreamBridge`, `WebClient`, `Clock`, `Instant.now`, `UUID.randomUUID`, `Thread` or `Mono`/`Flux`
- [ ] A replay test runs each workflow's recorded history against the current implementation

### ET-PLT-015-R4 · An activity is idempotent, and writes by compare-and-set with its outbox rows

THE SYSTEM SHALL implement every state-changing activity as one reactive transaction that advances
a status by compare-and-set and stages any envelope through `Outbox`, and a second execution of the
same activity SHALL change nothing.

**Acceptance**
- [ ] No `@ActivityImpl` class references `StreamBridge`
- [ ] Each state-changing activity has a test that runs it twice and asserts one transition and one envelope
- [ ] No Temporal client call and no provider call occurs inside a MongoDB transaction

### ET-PLT-015-R5 · The blocking SDK is confined to adapters and activity threads

THE SYSTEM SHALL call `WorkflowClient` only from `infrastructure/temporal` adapters that return
`Mono`, scheduled on `boundedElastic`, and SHALL permit `.block()` in production code on an activity
thread only.

**Acceptance**
- [ ] `BlockingCallLintTest` admits `@ActivityImpl` classes and nothing else new
- [ ] No resolver, controller or consumer references `WorkflowClient` or a workflow stub directly
- [ ] BlockHound stays installed and green

### ET-PLT-015-R6 · Facts and callbacks become starts or signals, carrying their originating id

WHEN a Service Bus consumer or a provider webhook reaches a process, THE SYSTEM SHALL translate it
into one signal-with-start or signal carrying the envelope's `eventId` or the provider's reference,
and SHALL checkpoint only after Temporal accepts it.

**Acceptance**
- [ ] A consumer that reaches a workflow does no business work of its own
- [ ] A signal delivered twice with the same originating id changes workflow state once

### ET-PLT-015-R7 · Workflow payloads carry identifiers, never personal data

THE SYSTEM SHALL pass workflow inputs, update and signal arguments, and results as records of ids,
amounts, codes and timestamps.

**Acceptance**
- [ ] No record used as a workflow or activity argument declares a component named for a phone number, email, name, address or document number

### ET-PLT-015-R8 · Every workflow is tested end to end with time skipped, and against a real server

THE SYSTEM SHALL test each workflow type at layer 3 on `TestWorkflowEnvironment` with stubbed
activities, including every timer and every compensation, and SHALL prove at layer 2 that a
service's worker completes a run against the Temporal development server.

**Acceptance**
- [ ] Each §4 workflow type has a layer 3 test class tagged with this spec's id or its owning spec's
- [ ] `TemporalDevServer` is a layer 2 marker and a round-trip test against it is green
- [ ] Every timer in §4 is exercised under time skipping, not by waiting

## 4. Model

### Namespace and connection

| Setting | Development | Production |
|---|---|---|
| Service | the Temporal CLI development server, `dev_temporal` in `docker-resources` | a self-hosted Temporal service the platform operates (ROADMAP D-28) |
| Namespace | `ticketing` | `ticketing` in each environment's own service |
| Frontend | `127.0.0.1:7233` | the service's frontend `host:7233`, TLS when `TEMPORAL_TLS_ENABLED` |
| Persistence | the development server's SQLite file | PostgreSQL `temporal` and `temporal_visibility`, reached only by the Temporal service |
| Retention | development server default | P30D, a namespace setting |

**Staging** runs its own self-hosted service, set up first; a purchase, a payout and a refund run
through it before production's is set up (ROADMAP D-33; `docs/operations/TEMPORAL_SELF_HOSTED.md`).
The development server is never used beyond a developer's machine. Every Schedule runner creates a
missing Schedule and moves an existing one to the code's cadence, action and overlap policy, keeping
an operator's pause (ROADMAP D-34).

### Task queue registry

| Task queue | Service | Registers | Why it is separate |
|---|---|---|---|
| `booking-checkout` | booking | PurchaseWorkflow, LatePaymentRefundWorkflow, checkout activities | checkout latency must not queue behind back-office work |
| `booking-provider` | booking | PawaPay collect, status, refund and payout activities | one server-enforced rate limit across every pod |
| `booking-finance` | booking | EventFinance, CancellationRefunds, Payout, BankVerification, Refund, Chargeback workflows and activities | a mass refund must not starve checkout |
| `booking-recon` | booking | ReconciliationWorkflow and its read-heavy activities | ET-FIN-005 R7: reconciliation does not contend with writes |
| `catalog-lifecycle` | catalog | EventApproval, EventLifecycle workflows and activities | owned by catalog |
| `identity-onboarding` | identity | OrganizerOnboarding, OwnershipTransfer, UserSync workflows and activities; the GroupMirrorRepair Schedule's runs | Keycloak's admin API is the bottleneck |
| `identity-notify` | identity | Notification, Reminder workflows and send activities | messaging providers fail independently of onboarding |
| `identity-account` | identity | AccountEnsure workflow and its activities (AccountMerge, ContactChange join in later waves) | the buyer's checkout waits on AccountEnsure; Keycloak admin latency from a bulk backfill must not queue ahead of it |

**Queue `identity-account`** (identity; registers `AccountEnsureWorkflow` now, `AccountMergeWorkflow` and `ContactChangeWorkflow` as they land; separate from `identity-onboarding` because the buyer's checkout waits on `AccountEnsureWorkflow`, and Keycloak admin latency from a bulk backfill must not queue ahead of it). The row above was added in the change that added the queue to `application.yml` and `TaskQueues.java` (ET-IDN-004 BE-3), as `TemporalRegistryLintTest` requires.

**Search attributes carry no personal data (D-46, 2026-10-04).** `BusinessId` is an opaque id (account id, ticket id, `contactKey`); a lint fails any workflow id or search attribute containing `@` or `+<digits>`, and workflow payloads and activity inputs carry ids and proof ids, never a contact value, name or code.

Seats cross the service boundary through catalog's idempotent internal inventory API, called from
booking activities; there is no cross-service task queue.

### Workflow registry

| Workflow | Queue | Id | Conflict policy | Owning spec | Status |
|---|---|---|---|---|---|
| `PurchaseWorkflow` | `booking-checkout` | `purchase/{reservationId}`, the id a name-based UUID of buyer and idempotency key | USE_EXISTING | ET-TKT-001 | implemented |
| `LatePaymentRefundWorkflow` | `booking-checkout` | `late-refund/{reservationId}` — a reservation has one payment | USE_EXISTING, REJECT_DUPLICATE reuse (a finished refund is never rerun); started by `PurchaseWorkflow`'s `startLateRefund` activity when a verified payment's reservation is over without tickets; polls the provider 30 s doubling to 10 min, escalates to ET-ADM-003 only on refusal, failure or 72 h of silence | ET-TKT-001 | implemented — in code (this change set); trials pending |
| `PayoutWorkflow` | `booking-finance` | `payout/{escrowAccountId}` | FAIL — one open request per escrow account (ET-FIN-003 R2) | ET-FIN-003 | implemented |
| `BankVerificationWorkflow` | `booking-finance` | `bank-verification/{bankAccountId}` | USE_EXISTING | ET-FIN-003 | implemented |
| `EventFinanceWorkflow` | `booking-finance` | `event-finance/{eventId}` | USE_EXISTING | ET-FIN-001 | implemented |
| `CancellationRefundsWorkflow` | `booking-finance` | `cancellation-refunds/{eventId}` | USE_EXISTING | ET-FIN-004 | implemented |
| `RefundWorkflow` | `booking-finance` | `refund/{ticketId}` — one refund in flight per ticket (ET-FIN-004 R4) | USE_EXISTING; a cancellation's child start is refused while the ticket's own refund is open | ET-FIN-004 | implemented |
| `ChargebackWorkflow` | `booking-finance` | `chargeback/{providerChargebackId}` | USE_EXISTING | ET-FIN-004 | implemented |
| `ReconciliationWorkflow` | `booking-recon` | `recon/{type}/scheduled`, suffixed by the Schedule with its fire time | Schedule overlap policy SKIP; run timeout PT30M | ET-FIN-005 | implemented |
| `EventApprovalWorkflow` | `catalog-lifecycle` | `event-approval/{eventId}` | USE_EXISTING | ET-ADM-001 | implemented |
| `EventLifecycleWorkflow` | `catalog-lifecycle` | `event/{eventId}` | USE_EXISTING | ET-CAT-001 | implemented |
| `EventPublishScheduleWorkflow` | `catalog-lifecycle` | `event-publish/{eventId}` | signal-with-start, USE_EXISTING; signals `reschedule` and `cancel`; one timer to `publishAt`, then publishes through `EventLifecycleProcess` | ET-CAT-004 | implemented |
| `OrganizerOnboardingWorkflow` | `identity-onboarding` | `org-onboarding/{organizationId}` | USE_EXISTING | ET-ORG-001 | implemented |
| `OwnershipTransferWorkflow` | `identity-onboarding` | `ownership/{transferId}` | USE_EXISTING | ET-ORG-002 | implemented |
| `UserSyncWorkflow` | `identity-onboarding` | `user-sync/{keycloakUserId}` | USE_EXISTING; continues as new after 1,000 changes or when suggested | ET-IDN-002 | implemented |
| `TicketExpiryWorkflow` | `booking-finance` | `ticket-expiry/{eventId}` | USE_EXISTING — started by the catalog-events consumer on `catalog.EventCompleted`; sleeps 24 h, then expires unscanned tickets in batches | ET-TKT-002 | **planned** |
| `TicketTransferWorkflow` | `booking-checkout` | `ticket-transfer/{transferId}` | USE_EXISTING; `claim` and `cancel` updates; timer to `expiresAt` | ET-TKT-004 | **planned** |
| `WebhookOrphanWorkflow` | `booking-checkout` | `webhook-orphan/{providerEventId}` | USE_EXISTING; re-match every PT5M, escalate at PT1H | ET-PAY-002 | **planned** |
| `BookingRollupWorkflow` | `booking-recon` | `rollup/booking/{granularity}/scheduled`, suffixed by the Schedule | Schedules `booking-rollup-hourly` and `booking-rollup-nightly`, overlap SKIP | ET-ADM-004 | **planned** |
| `CatalogRollupWorkflow` | `catalog-lifecycle` | `rollup/catalog/{granularity}/scheduled`, suffixed by the Schedule | Schedules `catalog-rollup-hourly` and `catalog-rollup-nightly`, overlap SKIP | ET-ADM-004 | **planned** |
| `IdentityRollupWorkflow` | `identity-onboarding` | `rollup/identity/{granularity}/scheduled`, suffixed by the Schedule | Schedules `identity-rollup-hourly` and `identity-rollup-nightly`, overlap SKIP | ET-ADM-004 | **planned** |
| `DevicePruningWorkflow` | `identity-notify` | `device-pruning/scheduled`, suffixed by the Schedule | Schedule `identity-device-pruning` daily, overlap SKIP | ET-NTF-001 | **planned** |
| `MassSendWorkflow` | `identity-notify` | `mass-send/{massSendId}` | USE_EXISTING; a batch at the configured rate, then a timer; `pause` and `resume` updates | ET-NTF-002 | **planned** |
| `OrganizerDigestWorkflow` | `identity-notify` | `organizer-digest/scheduled`, suffixed by the Schedule | Schedule `organizer-digest` hourly, overlap SKIP | ET-NTF-002 | **planned** |
| `BookingMigrationWorkflow` | `booking-recon` | `migration/{collection}/{version}` | USE_EXISTING; a batch per activity, continued as new from a cursor, off-peak | ET-PLT-010 | **planned** |
| `CatalogMigrationWorkflow` | `catalog-lifecycle` | `migration/{collection}/{version}` | USE_EXISTING; a batch per activity, continued as new from a cursor, off-peak | ET-PLT-010 | **planned** |
| `IdentityMigrationWorkflow` | `identity-onboarding` | `migration/{collection}/{version}` | USE_EXISTING; a batch per activity, continued as new from a cursor, off-peak | ET-PLT-010 | **planned** |
| `ErasureWorkflow` | `identity-onboarding` | `erasure/{userId}` (the opaque account id, never a contact) | USE_EXISTING — one open request per user; timers to T−7d and `scheduledFor`; `cancel` and `expedite` updates | ET-PLT-008 | **planned** |
| `DataExportWorkflow` | `identity-onboarding` | `data-export/{exportId}` | USE_EXISTING | ET-PLT-008 | **planned** |
| `AuditMaintenanceWorkflow` | `identity-onboarding` | `audit/{type}/scheduled`, suffixed by the Schedule with its fire time | Schedules `audit-verify` daily and `audit-purge` weekly, overlap SKIP | ET-PLT-009 | **planned** |
| `QueueAdmissionWorkflow` | `booking-checkout` | `queue-admission/{eventId}` | USE_EXISTING — one per high-demand sales window; admits `admitRate` passes a minute by timer | ET-PLT-011 | **planned** |
| `ReminderWorkflow` | `identity-notify` | `reminder/{reminderId}` | USE_EXISTING | ET-NTF-002 | implemented |
| `NotificationWorkflow` | `identity-notify` | `notify/{deduplicationKey}` | USE_EXISTING | ET-NTF-001 | implemented |
| `AccountEnsureWorkflow` | `identity-account` | `account-ensure/{contactKey}` — the HMAC key, never the contact (D-46) | Update-with-Start, USE_EXISTING; update `ensure`; activities claimContact, createKeycloakUser, applyAttributesAndRoles, activateAccount, stageOutbox, all repeatable; completes forward, never deletes | ET-IDN-004 | implemented |
| `AccountMergeWorkflow` | `identity-account` | `account-merge/{mergedAccountId}` | USE_EXISTING; `cancel` before MERGED | ET-IDN-004 | **planned** |
| `ContactChangeWorkflow` | `identity-account` | `contact-change/{accountId}` — the opaque account id, never a contact | FAIL on a running id (`CONTACT_CHANGE_IN_PROGRESS`), ALLOW_DUPLICATE reuse — one open change per account. One workflow for ADD, CHANGE, REMOVE and PRIMARY. Input: ids, the contact key (keyed hash), a masked display value; never a contact or a code. CHANGE waits for updates `authorise` (code to the current primary accepted) and `acceptNew(proofId)` (code to the new contact accepted), signals `failedAttempt` (five abort with `OTP_ATTEMPTS_EXHAUSTED`), `codeResent(target, challengeId)` (a resent code has a new challenge id) and `cancel`, and a PT48H timer (`OTP_EXPIRED`). Activities `begin` (marker `CHANGING`), `claimContact` (new contact under the unique index BEFORE any release), `syncKeycloak` (full representation), `commit` (release, primary, flags, account event, outbox in ONE transaction), `endSessions`, `clearMarker`, `notifyContacts` (best effort); all repeatable, retried without limit; a refusal clears the marker and changes nothing. `Workflow.getVersion` marker `contact-change-steps` | ET-IDN-004 | implemented |
| `TicketDeliveryWorkflow` | `identity-notify` | `ticket-delivery/{ticketId}` | USE_EXISTING; WhatsApp then email with retries; gate fallback (ticket code plus ID) if neither confirms | ET-NTF-001 | **planned** |

**Status column.** `implemented` means a `@WorkflowInterface` of that name exists under `backend/*/src/main`
and has a layer 3 test. **`planned`** means the row is declared (queue, id and policy reserved) but no
class of that name exists in the tree yet; the owning spec's task list tracks it. Checked by searching
`backend/*/src/main` for each class name, 2026-10-03: 17 registry types (including `LatePaymentRefundWorkflow`, added in this change set) plus the dynamic
`GroupMirrorRepair` type are implemented; the other rows are planned (16 at that date, plus the five identity-account and ticket-delivery rows added 2026-10-04). The
`*MigrationRunner` classes in catalog and identity are boot runners, not the planned `*MigrationWorkflow` types.

Recurring jobs are Temporal Schedules, each starting a workflow on its service's queue with overlap
policy SKIP: booking's four reconciliation schedules (`ReconciliationWorkflow`, ET-FIN-005) and
identity's `identity-group-mirror-repair` schedule every minute, which starts the dynamic workflow type
`GroupMirrorRepair` on `identity-onboarding` to run the Keycloak group-mirror repair activity
(ET-ORG-002). The dynamic type accepts no other name. Identity has no user-reconciliation Schedule (removed 2026-10-10, ET-IDN-002 R4); its `audit-verify` and
`audit-purge` Schedules start `AuditMaintenanceWorkflow` (ET-PLT-009), `identity-device-pruning`
starts `DevicePruningWorkflow` (ET-NTF-001) and `organizer-digest` starts `OrganizerDigestWorkflow`
(ET-NTF-002). Booking's reconciliation Schedules include `recon-provider` (ET-FIN-005, ET-PAY-002),
and each service runs its own analytics rollup Schedules (ET-ADM-004).

A registry row may precede its implementation: the lint requires every workflow interface in the
tree to be a row, and every queue a service polls to be a row, so a planned workflow is declared
on a queue that already exists.

### Configuration

```yaml
spring:
  temporal:
    connection:
      target: ${TEMPORAL_ADDRESS:127.0.0.1:7233}
      # prod profile: enable-https: ${TEMPORAL_TLS_ENABLED:false}
    namespace: ${TEMPORAL_NAMESPACE:ticketing}
    workers-auto-discovery:
      packages:
        - com.pml.{service}.workflow
    workers:
      - task-queue: {queue}        # one entry per §4 row the service owns
```

### Environment variables

| Variable | Used by | Notes |
|---|---|---|
| `TEMPORAL_ADDRESS` | all three services | frontend `host:port` |
| `TEMPORAL_NAMESPACE` | all three services | `ticketing` in development |
| `TEMPORAL_TLS_ENABLED` | all three services, `prod` profile | `true` when the self-hosted frontend terminates TLS |
| `TEMPORAL_ACTIVITY_EXECUTORS` | all three services | activity slots per worker |

### Build

`temporal.version` 1.38.0 through `io.temporal:temporal-bom` (ET-PLT-012 §4). Services depend on
`temporal-spring-boot-starter`, excluding `temporal-testing`, which they declare at test scope.

### Test harness

| Layer | Construct | Proves |
|---|---|---|
| 3 | `TestWorkflowExtension` / `TestWorkflowEnvironment`, time skipping, activities stubbed | sequence, timers, compensation |
| 2 | `TemporalDevServer` (`temporalio/temporal`, `server start-dev`) | a worker registers and completes a run over gRPC |

## 5. Tasks

- [ ] **T1 · Admit Temporal to the build** — parent property and BOM import, upper-bound pins, service starters; `BuildTopologyTest` names the BOM
- [ ] **T2 · Development server** — `dev_temporal` under the `temporal` profile in `docker-resources`; `TemporalDevServer` fixture
- [ ] **T3 · Lint amendments** — `@ActivityImpl` in `BlockingCallLintTest`; layer markers in `TestLayerLintTest`
- [ ] **T4 · Per-service adapter** — `infrastructure/temporal` with `TaskQueues`, `WorkflowIds` and the gateway
- [ ] **T5 · Registry and determinism lints** — task queues against §4, workflow implementations against R3, payload records against R7
- [ ] **T6 · Round trip** — a layer 2 test completes a workflow against the development server

## 5a. Production readiness

Honest status as of 2026-10-03:

- **Implemented in code.** The worker wiring, queue constants, workflow ids, the 17 implemented workflow
  types and the dynamic `GroupMirrorRepair` type, the Schedule runners, and the lints and layer 3 replay tests
  (`TemporalRegistryLintTest`, `WorkflowDeterminismLintTest`, `*Workflow*Test`, run by `.github/workflows/temporal.yml`).
- **Not yet proven against a real, operated cluster.** The self-hosted staging service of D-28 / D-33 is
  configured in `docker-resources/temporal/self-hosted/` (verified to start locally with namespace `ticketing`, 30-day retention and
  the search attributes) but **no staging environment has been provisioned and none of the D-33 trials have
  run** (purchase, payout, refund, late-payment refund, crash and rolling-deploy drills, schedule overlap).
  `docs/operations/TEMPORAL_SELF_HOSTED.md` holds the trial script and exit criteria.
- **Not done:** the load test at the D-16 on-sale rate, Worker Versioning rollout, dashboards and alerts, and
  any production environment.
- Passing unit, replay and lint gates is not evidence that the service works under load or failure; do not
  report this spec as production-verified until the staging trial is signed off.

## 6. Out of scope

- **A payload codec.** R7 is the first line; a Key Vault-keyed codec is added when a payload needs more than ids.
- **Search attributes in workflow code.** The names are registered by the infrastructure init job (`docker-resources/temporal/self-hosted/search-attributes.conf`: `BusinessId`, `TenantId`, `OrganizationId`, `EventId`, `BusinessStatus`, `ProcessKind`, all Keyword); workflows set them as the ET-ADM-003 recovery queue gains a source that reads them.
- **Nexus and a namespace per service.**
- **Production cluster sizing and Worker Versioning rollout.** An operations runbook, not a code contract.
