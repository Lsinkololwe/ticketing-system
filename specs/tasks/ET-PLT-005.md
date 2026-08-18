# ET-PLT-005 · Error contract — tasks

> **Spec** [`specs/_platform/005-error-contract/spec.md`](../_platform/005-error-contract/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-001, ET-PLT-004
> **Screens** — no screen of its own, but **every** screen's error state is defined here. Pairs with **Track F0-6**.
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-005` · `mvn -q -f backend verify -Dgroups=ET-PLT-005`

A **closed** registry: §4 groups codes into `platform`, `identity`, `organization`, `catalog`,
`ticketing`, `payment`, `finance`, `notification_and_admin`. Across the corpus **92 distinct
codes** are declared by other specs, and every one must land in this registry. A spec that raises
a code not in §4 is wrong, not extending.

## R0 · Reconcile *(do this first)*

```bash
grep -rn 'GraphQLError\|@DgsExceptionHandler\|ErrorType\.' backend --include='*.java' | grep -v /src/test/
grep -rn 'errorCode' backend --include='*.java' | grep -v /src/test/
```

`booking-service` and `identity-service` both have `exception/GlobalExceptionHandler`. Classify
each existing error path: does its code appear in §4, does it set `retryable`, and does it leak
an exception class name or stack frame? Every leak is `contradicted`.

## A · Backend

### BE-1 · `DomainRefusal`, `ErrorCode` enum and `GraphQlErrors` in `shared-library`
- **Spec** R1, R2 · **§5** T1 · **depends** R0 · **parallel-safe** no *(every service imports it)*
- **Acceptance** a test asserts the enum equals the §4 registry **row for row** — not "contains",
  equals. An enum with an extra code is a code nobody documented.

### BE-2 · The family fallback and catch-all handlers
- **Spec** R2, R4 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** an NPE thrown from a resolver leaks **no class name, message or frame**.
- The catch-all is the difference between an internal error and a free tour of the internals.

### BE-3 · A refusal type and a `@DgsExceptionHandler` per registry row, per service
- **Spec** R1, R2, R3 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** every `errorCode` names a §4 row; every `DomainRefusal` subtype has a handler;
  **every handler sets `extensions.retryable`**.
- `retryable` is not decoration — it is the field the client uses to decide whether to offer a
  retry button. Omitting it means the UI guesses, and it will guess wrong on a payment.

### BE-4 · Bean Validation on every input; the `fields` extension
- **Spec** R5 · **§5** T4 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a malformed input yields **one** error carrying the offending field list, and
  **writes nothing**.
- [ROADMAP §Cross-cutting](../ROADMAP.md): *a refused operation persists nothing* — required on
  every refusal test in the corpus, via [`ET-PLT-006`](ET-PLT-006.md)'s
  `Persistence.assertNothingPersisted`.

### BE-5 · Tenant-boundary responses return `*_UNKNOWN`
- **Spec** R6 · **§5** T5 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a cross-tenant id and a genuinely non-existent id produce **identical**
  responses — same code, same message, same timing shape.
- `ACTOR_NOT_PERMITTED` on a cross-tenant id confirms the id exists. That is an enumeration
  oracle, and it is why this is a security requirement rather than a niceness one.

### BE-6 · Map lock contention and duplicate keys; bound the server-side retry
- **Spec** R7 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a lock conflict → `RESOURCE_CONFLICT`, `retryable: true`; an idempotency-key
  duplicate → `IDEMPOTENCY_KEY_REUSED`, **not** retryable.
- Those two must not be confused: retrying a reused idempotency key is exactly the double-charge
  the key exists to prevent.

### BE-7 · RFC 9457 problem documents on the REST surface
- **Spec** R2 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- The REST surface is real: presigned-URL uploads (D-11) and the internal OTP API.
- **Acceptance** a REST refusal carries the **same** `errorCode` as its GraphQL equivalent. One
  vocabulary, two transports.

## B · Contract

Covered by BE-3 — `extensions.errorCode` and `extensions.retryable` are part of the wire contract
and must appear in the composed schema's error shape.

## C · Frontend — this is **Track F0-6**, and it is a real slice

### FE-1 · `extensions.errorCode` → rendered state, for every registry row
- **Spec** R1, R2 · **depends** BE-3, [`ET-PLT-004`](ET-PLT-004.md) BE-8 · **parallel-safe** no
- **Files** `frontend/web/libs/shared/src/` (barrel-exported)
- A single mapping from code → user-facing copy, shared by all three apps. Copy rules from the
  design authority: sentence case, no exclamation points in admin surfaces, no emoji, statuses
  humanised.
- **Acceptance** every code in §4 maps to a rendered state. An unmapped code must fall back to a
  generic message **and log**, never render the raw code to a user.

### FE-2 · `retryable` drives whether a retry is offered
- **Spec** R7 · **depends** FE-1 · **parallel-safe** yes
- **Acceptance** `RESOURCE_CONFLICT` offers retry; `IDEMPOTENCY_KEY_REUSED` and
  `ACTOR_NOT_PERMITTED` do not. The button's existence is data-driven, not per-screen judgement.

### FE-3 · `TOKEN_REVOKED` signs the user out rather than retrying
- **Spec** R2 · **depends** FE-1, [`ET-IDN-003`](ET-IDN-003.md) *(Wave 1 — the handler ships now, the code arrives then)*
- **Acceptance** a revoked token clears local session state and routes to login. Retrying a
  revoked token is an infinite loop against a fail-closed check.

### FE-4 · Field-level validation display from the `fields` extension
- **Spec** R5 · **depends** BE-4, FE-1 · **parallel-safe** yes
- **Acceptance** the offending field list renders **on the fields**, not as a banner. Per
  [ui-ux-pro-max](../../.claude/skills/ui-ux-pro-max/SKILL.md) `error-feedback`: the message goes
  next to the problem.

### FE-5 · Error components in the design system
- **depends** FE-1 · **parallel-safe** yes
- `Toast` (`variant` ∈ `success|error|warning|info`) for transient; `EmptyState`
  (`size` ∈ `sm|md|lg`) for a failed load; inline text for field errors. Closed prop sets — a
  prop outside the set is a break, not an extension.
- Tokens only. `--color-money` is **semantic** and must never be used for a status chip that is
  not about money.
- **Acceptance** compliance suite passes: no hex, no `px`, three fonts, closed props.

## D · Tests

### TS-1 · Registry closure *(L1)*
- Enum equals §4 row for row.
- **Corpus-wide**: every code any `spec.yaml` declares under `errors:` exists in §4. 92 codes
  today; this test keeps that at 92-of-92 as specs land.

### TS-2 · Handlers *(L2)*
- Per service: every `DomainRefusal` subtype has a `@DgsExceptionHandler`; every handler sets
  `retryable`; an NPE leaks nothing.

### TS-3 · Refusal semantics *(L3, Testcontainers)*
- `Persistence.assertNothingPersisted` on **every** refusal path.
- Cross-tenant id and unknown id produce identical responses.
- Lock conflict → retryable `RESOURCE_CONFLICT`; duplicate key → non-retryable
  `IDEMPOTENCY_KEY_REUSED`.

### TS-4 · REST parity *(L2)*
- A REST refusal and its GraphQL equivalent carry the same `errorCode`.

### TS-5 · Frontend *(L5, Playwright)*
- By `data-testid`: a code renders its mapped copy; a retryable code shows retry and a
  non-retryable one does not; `TOKEN_REVOKED` lands on login; field errors render on fields.
- **Apollo-driven surfaces cannot be mocked through Microcks** — it 500s on queries containing
  fragments. Use the **F0-4** Testcontainers subgraph fixture for these.

## E · Gate

- [ ] R0 recorded; every leaking error path classified `contradicted`
- [ ] `ErrorCode` equals §4 row for row; all 92 corpus-declared codes present
- [ ] Every handler sets `retryable`; an NPE leaks no class, message or frame
- [ ] Every refusal persists nothing
- [ ] Cross-tenant and unknown ids are indistinguishable
- [ ] REST and GraphQL share one code vocabulary
- [ ] Every §4 code maps to a rendered frontend state; unmapped codes log rather than leak
- [ ] `retryable` drives the retry affordance; `TOKEN_REVOKED` signs out
- [ ] Compliance suite green (tokens, fonts, closed props)
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-005 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
