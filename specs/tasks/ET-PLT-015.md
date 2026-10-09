# ET-PLT-015 · Durable execution — tasks

> **Spec** [`specs/_platform/015-durable-execution/spec.md`](../_platform/015-durable-execution/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001, ET-PLT-003, ET-PLT-012
> **Status** `approved` — cleared to build
> **Screen** none — a platform construct with no UI
> **Verify** `mvn -q -f backend/shared-library test -Dgroups=ET-PLT-015 -DfailIfNoTests=false` · `mvn -q -f backend/booking-service test -Dgroups=ET-PLT-015 -DfailIfNoTests=false`

Temporal becomes the platform's process layer: every multi-step, cross-service or timed process is a
workflow whose history survives a crash, whose timers fire with no pod running the code that set
them, and whose writes are idempotent activities that stage their outbox rows in the same transaction.
MongoDB stays the system of record and Service Bus stays the broadcast layer (ROADMAP D-21).

## R0 · Reconcile

Before this spec the platform's processes were `@Scheduled` sweeps under Redis locks, in-process
listener chains, and recovery services that reported without recovering ([F-030](../FINDINGS.md),
[F-031](../FINDINGS.md)). Record each one against the §4 workflow registry as `replaced`,
`kept-by-decision`, or `open`.

## A · Backend

- **BE-1 · Admit Temporal to the build.** `temporal.version` and `temporal-bom` in the parent;
  `temporal-spring-boot-starter` in each service with `temporal-testing` at test scope; upper-bound
  pins recorded in ET-PLT-012 §4.
- **BE-2 · Development server.** `dev_temporal` under the `temporal` profile in `docker-resources`;
  `TemporalDevServer` Testcontainers fixture in the shared test-jar.
- **BE-3 · Adapters.** `infrastructure/temporal/{TaskQueues, WorkflowIds, TemporalGateway}` in each
  service; the worker configuration declares exactly the §4 queues.
- **BE-4 · Lints.** `TemporalRegistryLintTest` (queues, workflow types, client confinement),
  `WorkflowDeterminismLintTest` (determinism, no direct sends from activities, ids-only payloads),
  `@ActivityImpl` admitted by `BlockingCallLintTest`, layer markers in `TestLayerLintTest`.
- **BE-5 · Workflows.** Each §4 row implemented by its owning spec's slice, with a layer-3 test.

## E · Gate

- [x] Temporal admitted through `temporal-bom`; enforcer green — `BuildTopologyTest.allFiveBomsAreImported`; `mvn -DskipTests install` passes `requireUpperBoundDeps` with the ET-PLT-012 §4 pins
- [x] Development server and fixture — `TemporalRoundTripTest` completes a workflow against `temporalio/temporal` started by Testcontainers
- [x] Each service polls exactly its §4 queues, named by constants — `TemporalRegistryLintTest.queuesMatchTheRegistry`, `noLiteralQueues`
- [x] Every workflow interface in the tree is a registry row on its own service's queue — `TemporalRegistryLintTest.workflowTypesAreRegistered`
- [x] `WorkflowClient` is confined to adapters and workflow packages — `TemporalRegistryLintTest.clientIsConfined`
- [x] Workflow code is free of I/O, clock, randomness and threads; activities never send; payloads carry ids — `WorkflowDeterminismLintTest` (patterns proven against synthetic offenders)
- [x] `USE_EXISTING` and `FAIL` behave as §4 relies on, including Update-with-Start — `TemporalRoundTripTest.useExistingReachesTheSameRun`, `failRefusesASecondStart`, `updateWithStartReachesOneExecution`
- [x] A refusal keeps its `ErrorCode` from validator to client — the payout, purchase, refund and chargeback layer-3 tests assert codes through a workflow client ([VERIFICATION §1.9](../VERIFICATION.md))
- [x] A replay test runs each workflow's recorded history against the current implementation — every layer-3 workflow test class in booking (8), catalog (2) and identity (7) replays a completed run with `WorkflowReplayer`; the first one caught `PurchaseWorkflow` reading its own workflow id ([F-031](../FINDINGS.md))
- [x] Every workflow type in the tree has a layer-3 test class — booking: Payout, BankVerification, Purchase, EventFinance, Refund, CancellationRefunds, Chargeback, Reconciliation; catalog: EventLifecycle, EventApproval; identity: OrganizerOnboarding, OwnershipTransfer, UserSync, UserBackfill, Reminder, Notification, GroupMirrorRepair. §4 rows owned by ET-PLT-008, -009, -010, -011, ET-TKT-002, -004, ET-PAY-002, ET-ADM-004 and ET-NTF-001, -002 are declared ahead of their implementation, on queues that already exist
- [x] `mvn -q -f backend/shared-library test -Dgroups=ET-PLT-015 -DfailIfNoTests=false` green — full shared-library suite 371/371 on 2026-09-13
- [x] Spec `status:` → `implemented` — 2026-09-19, when its last blocker, ET-PLT-003, closed
