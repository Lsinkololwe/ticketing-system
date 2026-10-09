# ET-PLT-001 · Runtime baseline — tasks

> **Spec** [`specs/_platform/001-runtime-baseline/spec.md`](../_platform/001-runtime-baseline/spec.md) · **Wave 0** · `blocked_by:` ET-PLT-012
> **Screens** — none.
> **Verify** `mvn -q -f backend -DskipTests clean install` · `mvn -q -f backend test -Dgroups=ET-PLT-001`

The reactive contract, the injected `Clock`, module boundaries and the gateway. Two facts from
[CONVENTIONS.md §1](../CONVENTIONS.md) set the size of this slice: **five `.block()` calls in
production code** and **150 files still on `LocalDateTime`**.

## R0 · Reconcile *(do this first)*

Classify §3 R1–R7 against the tree. Produce, as data rather than prose:

```bash
grep -rn '\.block()\|\.blockFirst()\|\.blockLast()\|\.toFuture()\.get()' backend --include='*.java' | grep -v /src/test/
grep -rln 'LocalDateTime\|LocalDate\.now\|ZonedDateTime\|System\.currentTimeMillis' backend --include='*.java' | grep -v /src/test/
grep -rn '\.subscribe(' backend --include='*.java' | grep -v /src/test/
```

The counts you get are the real size of BE-2 and BE-3. If they differ materially from five and
150, say so — CONVENTIONS.md is then stale and the number in the PR body is the true one.

## A · Backend

### BE-1 · Confirm ET-PLT-012 carries this spec's dependency baseline
- **Spec** R2 · **§5** T1 · **depends** R0 · **parallel-safe** no *(every pom)*
- **Acceptance** `mvn -q -f backend -DskipTests clean install` succeeds and every §4 baseline
  coordinate resolves from a managed version.

### BE-2 · Purge the servlet stack; add the BlockHound test agent
- **Spec** R1 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes *(one service per agent)*
- **Files** `backend/*/pom.xml`, `backend/*/src/test/resources/`
- **Acceptance** `mvn dependency:tree | grep -E 'starter-web|tomcat'` is empty; production source
  contains no `.block()`, `.blockFirst()`, `.blockLast()`, `.toFuture().get()` or fire-and-forget
  `.subscribe()`.
- Genuinely blocking third-party SDKs go on `Schedulers.boundedElastic()`, **wrapped once, in
  `infrastructure/`**. Containing the blocking to one adapter class is the point; a
  `boundedElastic` call scattered through a service layer has not fixed anything.
- BlockHound is what turns R1 from a grep into a runtime guarantee — it fails the test when a
  blocking call reaches an event-loop thread, including one inside a library you did not write.

### BE-3 · `PlatformConfig` with the `Clock` bean; remove every inline `now()`
- **Spec** R3 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes *(one service per agent)*
- **Files** `backend/*/src/main/java/com/pml/*/config/PlatformConfig.java`
- **Acceptance** production source contains no inline `Instant.now()`, `LocalDateTime.now()`,
  `LocalDate.now()`, `ZonedDateTime.now()` or `System.currentTimeMillis()`.
- Every timestamp comes from the injected `Clock`. This is what makes [`ET-PLT-006`](ET-PLT-006.md)'s
  frozen-clock tests possible — a reservation cannot be tested at 9:59 and 10:01 if the code
  reads the wall clock.

### BE-4 · Declare each module's boundary in `package-info.java`
- **Spec** R4 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** every top-level package in §4 has a header naming what it exposes, what it may
  depend on and what is internal; **no module imports another's `repository` or `service.impl`**.

### BE-5 · Reduce `shared-library` to contracts
- **Spec** R5 · **§5** T5 · **depends** BE-1 · **parallel-safe** no *(every service imports it)*
- **Acceptance** `shared-library` declares no `@Document`, `@Repository`, `@Service` or
  `@DgsComponent`.
- It holds DTOs, the error contract, the event envelope and security converters. A `@Service` in
  a shared library is a service two other services now silently share.

### BE-6 · Externalise every credential and endpoint; fail fast on absence
- **Spec** R6 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a grep for secrets across committed `application*.yml` is empty; a service
  started with a required variable unset **names the variable** and exits.
- Failing fast with the variable's name beats starting with a default and failing at 3am against
  the wrong Keycloak.

### BE-7 · Gateway — WebFlux namespace, rate limiter, circuit breaker, header pass-through
- **Spec** R7 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Files** `backend/api-gateway/src/main/resources/application.yml`, `.../config/`
- Config lives under `spring.cloud.gateway.server.webflux.*` — **not** `spring.cloud.gateway.*`.
  The generic starter is deprecated; use `spring-cloud-starter-gateway-server-webflux`.
- **Acceptance** a forged JWT sent **directly** to `:8082/graphql` is rejected. The gateway is
  not the security boundary — each service validates independently, or a service reachable
  inside the network trusts anything.

### BE-8 · Per-service `ReactiveJwtDecoder` — signature, issuer, audience, expiry
- **Spec** R7 · **§5** T8 · **depends** BE-6 · **parallel-safe** yes
- **Acceptance** `mvn -q -f backend test -Dgroups=ET-PLT-001 -DfailIfNoTests=false`.
- All four checks, not three. An issuer-only check accepts a token minted by the right realm for
  the wrong audience.

## B · Contract

None directly. `subgraph: null` in `spec.yaml`.

## C · Frontend

None. **Track F0** runs in parallel.

## D · Tests

### TS-1 · Reactivity and clock guards
- **Spec** R1, R3 · **depends** BE-3 · **parallel-safe** yes
- **L1** — `Clock` injection: a service given a fixed `Clock` writes the fixed instant.
- **L3 (Testcontainers)** — BlockHound installed; a request path that touches Mongo, Redis and
  the JWKS endpoint completes with no blocking-call detection.
- **Lint-as-test** — the greps from R0 become assertions that fail the build, not a checklist.
  [ROADMAP §Cross-cutting](../ROADMAP.md) lists "no blocking call ever runs on an event-loop
  thread" and "every timestamp comes from the injected `Clock`" as properties asserted *by this
  spec and by lint*. Both halves are required.

### TS-2 · Boundary and gateway tests
- **Spec** R4, R7 · **depends** BE-8 · **parallel-safe** yes
- **L1** — module-boundary assertion: no import of another module's `repository`/`service.impl`.
- **L3** — Keycloak container; four token-rejection tests (bad signature, wrong issuer, wrong
  audience, expired) against **each** service directly, bypassing the gateway.
- **L3** — startup test: required env var unset → the process exits naming it.

Tag every class `@Tag("ET-PLT-001")`; `@DisplayName` names the requirement.

## E · Gate

- [x] R0 table recorded, with the **actual** `.block()` and `LocalDateTime` counts — Appendix below
- [x] Zero blocking calls in production source; BlockHound green on a real request path
  - **`KNOWN_OFFENDERS` is empty.** Every blocking call left in production source is at boot or in
    a `@Scheduled` method, and R1 now exempts both **by name and for one reason**: neither can
    reach a Netty worker.
  - Two were removed outright on 2026-09-02 — `PaymentEventListener` and `ChargebackEventListener`
    were `@TransactionalEventListener(AFTER_COMMIT)`, which runs on the committing thread, a Netty
    worker on a request path. Both now subscribe, and what made that safe was building the durable
    net that was missing: `ChargebackRecoveryService` ([F-028](../FINDINGS.md)).
  - Two were **ruled exempt** rather than converted. R1's justification is "one stalled worker
    stalls every concurrent request it is carrying" — a statement about event loops that does not
    reach a scheduler thread. The cost a blocking scheduled task *does* carry is pool starvation,
    and that is fixed by sizing the pool (booking 10, identity 4), not by removing the block.
    Converting them would satisfy the letter and lose something real: `.block()` is what stops the
    reservation expiry sweep overlapping itself.
  - **BlockHound is unchanged, which is what keeps the exemption honest.** It fires only on an
    event-loop thread, so it draws exactly this line at runtime rather than by pattern — a
    `@Scheduled` method that somehow lands on a worker still fails.
- [x] Zero inline `now()`; every timestamp from the injected `Clock` — **0 remain**, down from
      474. `InlineNowLintTest`'s budget is now `0` in **all six modules**, so the ratchet has
      become a ban and any reintroduction fails the build.
  - Completed 2026-09-02 in four passes: shared-library 7→0, api-gateway 4→0, catalog 22→0,
    identity 88→0, booking 136→0, keycloak-extensions 8→0.
  - **Not a search-and-replace.** Three shapes needed different answers, and picking the wrong one
    would have satisfied the lint while leaving the defect:
    - *Spring beans* (40 files) take the injected `Clock` — mechanical.
    - *Domain models* take the `Instant` as a parameter, because a `@Document` cannot hold a bean.
      This is the bulk of booking and catalog: 78 methods in booking alone.
    - *Elapsed measurements* take neither. They moved to `System.nanoTime()` — injecting a `Clock`
      would have passed the lint and kept the bug, since an injected wall clock is no more
      monotonic than a static one. `ElapsedTimeLintTest` now bans the shape outright.
  - **`LocalDate.now()` was a third case again**, and a live one: it reads the **JVM default zone**.
    Every container here runs UTC, so "today" resolved to the previous day for the first two hours
    of each Lusaka morning — a nightly reconciliation started at 01:00 reconciled the wrong day and
    the right one was never reconciled at all. All 16 sites now use `PlatformTime.dateAt`, which
    names `Africa/Lusaka`.
  - **Defects found while migrating**, none of which the lint was looking for:
    - see F-023 in [FINDINGS](../FINDINGS.md): — the storefront advertises the early-bird price and checkout charges
      the full one. Open; the pricing half belongs to ET-TKT-001.
    - `recordReminderSent` and `markForRetry` each read the wall clock **four times in one method**,
      so the timestamps they wrote could not agree on when "now" was and the interval they encoded
      was never quite the configured one. One reading each now.
    - `EventSummaryDto.isActive()` — a bean getter on a **cross-service wire payload**, so Jackson
      serialised a field decided by the *sender's* clock at serialisation time, with nothing in the
      payload saying when. Zero callers; removed.
    - `@Builder.Default` timestamps on `TimelineEvent`, `SyncResponse`, `PaymentResult` and
      `PayoutResult` stamped the moment an object was **built** rather than when the thing happened
      — close enough to be plausible, never right, and untestable. All four now set by the caller.
- [x] No servlet stack on any classpath — enforced at dependency resolution by the parent POM's
      `bannedDependencies` rule (web, tomcat, jetty, undertow), so it fails the build rather than
      a test that could be skipped
- [x] `shared-library` holds no `@Document`/`@Repository`/`@Service`/`@DgsComponent` — C3
- [x] A forged JWT is refused by each service directly, not only at the gateway — E3, and also
      wrong-issuer, wrong-audience, expired and absent, at `/graphql` and a REST path
- [x] `mvn -f backend clean install` green — **73 tests**, 67 in shared-library plus 2 in each service
- [x] Spec `status:` → `implemented`

---

## Appendix · R0 findings and the implementation breakdown *(2026-08-18)*

### What R0 actually found

| Item | CONVENTIONS claim | Measured | Class |
|---|---|---|---|
| `.block()` in production | 5 | **13** (see correction) | 6 legitimate, 6 debt, 1 fixed |
| Files on `LocalDateTime`/`ZonedDateTime` | ~150 | **179** | `contradicted` |
| Inline `now()` **call sites** | — | **477** (re-measured; see A4) | `contradicted` |
| `.subscribe()` | — | **30** | needs triage |
| Servlet stack on any classpath | — | **0** | `already-satisfied`, now enforcer-guarded by ET-PLT-012 BE-8 |
| `PlatformConfig` | — | **absent** | `absent` |
| `Clock` beans | — | 2, both `revocationClock` | `partially-satisfied` |
| `@Service` in `shared-library` | — | **≥1** (`TenantValidationService`) | `contradicted` (BE-5) |

### The `.block()` finding is not what the spec assumes

R1 bans `.block()` and gives the reason: *"Blocks a Netty worker."* Six of the seven do not.

| Site | Construct | Verdict |
|---|---|---|
| `ReferenceDataSeeder` | `implements ApplicationRunner` | **legitimate** — main thread, boot only |
| `ChartOfAccountsSeedRunner` | `implements ApplicationRunner` | **legitimate** |
| `MigrationRunner` | `@EventListener(ApplicationReadyEvent.class)` | **legitimate** |
| `PlatformConfigurationInitializer` | boot initializer | **legitimate** — confirm the construct |
| `ReservationIndexInitializer` | boot initializer | **legitimate** — confirm the construct |
| `ReferenceDataSeeder` (2nd site) | same runner | **legitimate** |
| **`TenantValidationService:118`** | `@Service`, blocks **inside** `Mono.fromCallable`, once per document | **latent hazard** — see correction below |

`ApplicationRunner` is Spring Boot's native construct for boot-time work and runs on the main
thread, so blocking there stalls nothing. Deleting those six would trade a correct construct for
a worse one and lose the startup ordering that seeders depend on.

**Correction (2026-08-18, from BlockHound).** `TenantValidationService` was first classified as
a live stall. It is not. `validateTenantContext` returns a *callable* `Mono` doing pure reflection
with no I/O, and Reactor evaluates a callable source directly on `block()` rather than parking — so
no thread is ever suspended. BlockHound establishes this; a grep cannot.

It still has to be rewritten in B2, because the shape is one refactor away from a live stall: the
moment that method does any I/O its `Mono` stops being callable, `block()` starts parking, and every
request through the batch path stalls a Netty worker — a change nothing about which would look
dangerous in review. Classified **latent hazard**, not `contradicted`.

A second thing BlockHound surfaced: **Reactor guards `block()` on a non-blocking thread itself**,
raising `IllegalStateException` before any park occurs. So the two mechanisms cover different
ground — Reactor catches the explicit call, BlockHound catches what it cannot see, several frames
down inside a driver or a parser. Both are wanted.

**Approved rule (2026-08-18), replacing a flat ban:** no `.block()` except in a class implementing
`ApplicationRunner`/`CommandLineRunner`, or in an `@EventListener(ApplicationReadyEvent.class)`
method. Enforced by lint, and by BlockHound at runtime — which only ever fires on an event-loop
thread, so it already draws this exact line. **Approved and recorded in ET-PLT-001 §3 R1.**

### Breakdown

**A · The clock — do first, it unlocks ET-PLT-002, ET-PLT-006 T2 and every frozen-time test**
- **A1** `PlatformClockConfiguration` in `shared-library`: `@Bean @ConditionalOnMissingBean Clock` → `systemUTC()`
- **A2** Collapse the two `revocationClock` beans onto it, keeping `@ConditionalOnMissingBean` so a test can override
- **A3** `DateTimeProvider` bound to the platform `Clock`, so `@CreatedDate`/`@LastModifiedDate` are frozen in tests
- [x] **A4** Migrate the inline `now()` sites — **complete 2026-09-02: 477 → 0, all six modules.**
  - `InlineNowLintTest`'s budget is `0` everywhere, so the ratchet has become a ban.
  - The note below about splitting the 230 `LocalDateTime.now()` sites to ET-PLT-002 is now
    historical: that type migration landed, and by 2026-09-02 only **7** `LocalDateTime.now()` sites
    remained platform-wide. What was left was `Instant.now()`, which is this spec's own work.
  - **Three shapes, three different answers**, and picking the wrong one satisfies the lint while
    keeping the defect: Spring beans take the injected `Clock`; domain models take the `Instant` as
    a parameter, because a `@Document` cannot hold a bean; **elapsed measurements take neither** and
    moved to `System.nanoTime()`, since an injected wall clock is no more monotonic than a static
    one. `ElapsedTimeLintTest` now bans that shape outright and found four live instances — two on
    the payment path.
  - `LocalDate.now()` was a fourth shape and a live defect: it reads the **JVM default zone**, and
    every container here runs UTC, so "today" resolved to the previous day for the first two hours
    of each Lusaka morning. A nightly reconciliation started at 01:00 reconciled the wrong day and
    the right one was never reconciled at all. All 16 sites now use `PlatformTime.dateAt`.
  - Findings that fell out of the migration rather than out of the lint: [F-023](../FINDINGS.md)
    (the storefront advertises the early-bird price, checkout charges the full one),
    `EventSummaryDto.isActive()` serialising a sender-clock decision onto a cross-service payload,
    and four `@Builder.Default` timestamps stamping when an object was *built* rather than when the
    thing happened.

  *Superseded detail from 2026-08-18 follows.*
  - **Re-measured, because B4 taught the lesson.** True count is **477**, not 473, and the breakdown decides the work: `Instant.now()` 209 · `LocalDateTime.now()` **230** · `LocalDate.now()` 18 · `System.currentTimeMillis()` 19 · `System.nanoTime()` 1. Only **5** clock-aware call sites existed.
  - **Split by owner, deliberately.** The 230 `LocalDateTime.now()` sites are **ET-PLT-002 R5's**, not this spec's: the correct fix is a *type* migration to `Instant`, and "fixing" them as `LocalDateTime.now(clock)` would satisfy the lint while cementing exactly the type ET-PLT-002 removes — a fix that has to be undone is worse than a visible defect.
  - **`System.nanoTime()` is exempt, with reason.** It is a monotonic counter for elapsed duration with no relation to wall time; replacing it with a `Clock` read would break the measurement it exists for. One site (`RevocationCacheTrust`) uses it as a *signal token*, not a timestamp at all.
  - **`InlineNowLintTest` is a per-module ratchet**, calibrated to measured truth: shared-library 7 · catalog 108 · booking 229 · identity 111 · api-gateway 4 · **keycloak-extensions 8** — the last of which my by-module count had missed entirely. Budgets may only fall: a migration that does not lower its budget **fails the build**, because reclaimed headroom silently permits new violations.
  - **Migrated (3):** `MigrationLedger` STARTED_AT/FINISHED_AT now come from the injected `Clock`, threaded through `IdentityMigrationRunner` and `DataMigrationRunner`. Proven by the ratchet refusing to pass until shared-library's budget dropped 10 → 7.
  - **Remaining: 474**, burned down per module by the spec that owns each kind. `keycloak-extensions` is a Spring-free Keycloak SPI JAR (ET-PLT-012 BE-4), so its 8 cannot inject a Spring `Clock` and need a separate decision.

**B · Reactivity**
- [x] **B1** BlockHound in test scope + install hook *(the runtime half of R1)* — *done 2026-08-18. Version managed in the parent; `-XX:+AllowRedefinitionToAddDeleteMethods` added to surefire (JDK 13+ refuses the redefinition otherwise, and instrumentation fails silently). `PlatformBlockHoundIntegration` registered via ServiceLoader with **zero allowances** — each one is a hole in the guarantee. Five tests: instrumentation self-check, blocking on `parallel()` refused, blocking on `boundedElastic()` allowed, and the two findings above.*
- [x] **B2** Fix `TenantValidationService` — *done 2026-08-18. Now `Flux.defer(...).concatMap(...).then(...)`. `concatMap` not `flatMap`: a tenant violation must name the first offending document every time, not whichever lost the race. `Flux.defer` so a null argument still arrives as `onError` rather than throwing at assembly. Two tests: it composes and blocks nothing, and it fails fast in order.*
- [x] **B3** Triage the 30 `.subscribe()` — **complete 2026-09-02.** *Triage notes from 2026-08-18 follow.*
  - The eight real-debt sites are converted to `subscribe(onNext, onError)`: `ReconciliationScheduler`
    ×4, `FireAndForgetLintTest`'s booking budget falls 13 → 5. A `doOnError` above a bare
    `subscribe()` logs the failure but does not consume it — the error still reaches Reactor's
    global hook, which is not this platform's error path.
  - The remaining seven are the legitimate ones the triage identified and they stay: five in
    `AzureServiceBusConfig` (establishing a long-lived bus consumer *is* subscribing), one boot
    validator, one cache warmer. Rewriting these to avoid the word would make them worse.
  - **16 were bare `.subscribe()`** — the only shape where a failure provably has nowhere to go. `.subscribe(onNext, onError)` at least hands it somewhere, so bare is the line the lint draws.
  - **Fixed (1, high severity): `PawaPayWebhookController`.** Inside `onErrorResume` it cleared the webhook deduplication marker fire-and-forget, then returned HTTP 200 immediately. Three failures compounded: the response returned **before** the clear ran, a failed clear went nowhere, and PawaPay was told "success" and stopped retrying. A marker left set then **suppresses the genuine retry as a duplicate** — the callback is never processed and money is in flight with nobody looking for it. Directly against ET-PAY-002 R2. Now composed: the clear is awaited, its own failure is logged, and the 200 still returns deliberately so the provider does not retry excessively.
  - **Legitimate, not defects (7):** `AzureServiceBusConfig` ×5 (bus consumer wiring), `MongoSchemaValidationConfig` (boot validators), `RevocationCacheWarmer` (startup warm). Establishing a long-lived consumer *is* subscribing; rewriting these to avoid the word would make them worse.
  - **Real debt, owned elsewhere (8):** `ReconciliationScheduler` ×4 → **ET-FIN-005**; `FinancialJobListener` ×3 and `ChargebackEventListener` ×1 → **ET-PLT-003** (after-commit work moves to the outbox drain). Scheduled and after-commit work whose errors currently vanish.
  - **`FireAndForgetLintTest`** freezes the count per module (booking 13 after the fix, shared 1, identity 1, rest 0) and may only fall. The remaining sites are *inside* the budget rather than pattern-exempted, because "wiring or leak?" is a judgement a regex should not be trusted to make.
- [x] **B4** Lint test for the narrowed `.block()` rule — *done 2026-08-18. `BlockingCallLintTest` scans every module's production source from the reactor root.*
  - **⚠️ R0 was wrong, and the lint proved it.** The reconciliation grep matched `.block()` with **literal empty parentheses**, so it missed every `.block(TIMEOUT)` call. The true count is **13, not 7**. Six previously-unknown violations surfaced:
    - **High (4)** — `PaymentEventListener` ×2, `ChargebackEventListener` ×2. `@TransactionalEventListener(AFTER_COMMIT)` runs **synchronously on the committing thread**; on a request path that is a Netty worker, so these are live stalls, not latent. Owner: **ET-PLT-003** (after-commit work moves to the outbox drain).
    - **Medium (2)** — `ReservationExpirationScheduler`, `PurchaseRecoveryScheduler`. `@Scheduled` runs on a `TaskScheduler` pool that defaults to **one thread**, so blocking there delays every other job — including the expiry sweep inventory conservation depends on. Owners: **ET-TKT-001** B5, **ET-ADM-003**.
  - **Introduced as a ratchet, not a weakened rule.** The four offending files are frozen in `KNOWN_OFFENDERS`; any *new* violation fails immediately, a fixed offender must be removed from the list (a stale entry silently re-permits a cleaned file), and a second test caps the list so it can only shrink.
  - **Coarse by design:** the allowance is per file, not per method. Method-level regex scoping would be more precise and far more brittle, and would still miss a blocking call inside a driver. The guarantee is the pair — this lint plus BlockHound at runtime.

**C · Boundaries**
- [x] **C1** `package-info.java` per top-level package — **complete, 2026-09-02.**
  - Was 42 of 62 across five modules; api-gateway had **none**. Now **61 of 61**: shared-library
    13/13, catalog 15/15, booking 14/14, identity 15/15, api-gateway 4/4.
  - Each of the 19 written declares the four things the house style asks for — what the package
    exposes, what it may depend on, what is internal, and its **R5 allowlist status with the
    reason**. The last is the point: the debt sits at the code rather than only in a spec, so the
    next reader of `com.pml.catalog.error` learns there that the registry is shared and the mapping
    deliberately is not.
  - `ModuleBoundaryLintTest`'s frozen counts are now equal, so this has stopped being a ratchet and
    become a floor: a new package without one is a regression immediately, not eventually. Worth
    noting the *totals* moved too (catalog 13→15, api-gateway 5→4) as packages came and went —
    which is the argument for freezing both numbers rather than a percentage, since a percentage
    holds steady while a package quietly disappears.
- [x] **C2** Boundary test — *done 2026-08-18, and it locked in three properties that were **already clean**, which is exactly when to assert them: a boundary costs nothing to protect while it holds and a refactor to recover afterwards.*
  - **0** cross-module imports of another module's `repository` or `service.impl`
  - **0** imports from `shared-library` into any service — it is a genuine leaf
  - **0** service-to-service imports at all: catalog, booking and identity talk over the bus and the graph, never by compiling against each other. A service compiled against another is a distributed monolith, and the reactor eventually reports it as a cycle.
- [x] **C3** Strip stereotypes from `shared-library` — *done 2026-08-18. Three `@Service`s removed: `TenantValidationService`, `MoneyFieldMigrationService`, `StatusSemanticResolver`. The latter two have live consumers across three services, so they are now contributed by `SharedPersistenceSupportAutoConfiguration` — an explicit offer the application can override, rather than a bean acquired implicitly through `@ComponentScan("com.pml.shared")`. Guarded by `SharedLibraryBoundaryTest`, a source scan, because a stereotype in a library is invisible from inside the library.*
  - **Found by the lint, missed by hand:** a nested `@DgsComponent` in `AuthDirectiveAutoConfiguration` — the manual inventory grep was anchored to column 0 and this one is indented. Exempted **by exact path** with its justification: `graphql/auth.graphqls` is on R5's allowlist and this class is the wiring that makes the directive work. Any other `@DgsComponent` still fails.
  - **Debt recorded, not silently carried:** R5's acceptance is an allowlist, and neither `MoneyFieldMigrationService` nor `StatusSemanticResolver` is on it. They belong in booking (**ET-PLT-002** T5) and the reference engine (**ET-PLT-014**) respectively. Relocating them means rewriting consumers in three services, which is not this slice's job.
  - **Orphan flagged:** `TenantValidationService` has **zero production consumers** — only its own test. It is reflection-based and superseded by [`ET-PLT-007`](ET-PLT-007.md) BE-4, which moves every tenant check into the repository filter. Recommend deletion in that slice rather than here.

**D · Configuration** — `@ConfigurationProperties` + `@Validated` + JSR-303, the native fail-fast
- [x] **D1/D3** Fail-fast on missing credentials — *done 2026-08-18, and this one was a live security defect, not a style point.*
  - **All three services shipped working fallback credentials in their base `application.yml`:** `admin-password: ${KEYCLOAK_ADMIN_PASSWORD:admin}`, and `client-secret: ${*_SERVICE_SECRET:*-service-secret}` in identity, catalog and booking.
  - **Why it was live:** the base file is inherited by every profile and `application-prod.yml` overrode none of them. A missing environment variable in production did **not** fail — the service started against Keycloak using a credential readable in git, silently. R6 exists to make a missing variable fail fast; this would not have failed at all.
  - **Fixed the profile-correct way:** the defaults are gone from the base files (so startup fails naming the variable), and the development values moved to `application-local.yml`, where a value in a file called `local` is visibly a development value.
  - **Nuance recorded in the test:** Spring *does* name the missing variable, but in the **cause chain** — the outermost frame says only "Unexpected exception during bean creation". R6's "names the variable" is true of the stack trace, not of its first line.
- [x] **D2** Secrets lint over committed config — *done. `ExternalisedConfigLintTest` flags any credential-named key carrying a **non-empty** default in a base `application.yml`.*
  - **`${VAR:}` is deliberately not a violation** — an empty default means "no password", a legitimate configuration, not a secret in git. Banning defaults outright would flag every URL and realm name and the lint would be switched off inside a week.
  - **No real hardcoded secrets found:** the `SAS_KEY_VALUE` connection strings are Service Bus *emulator* placeholders (`UseDevelopmentEmulator=true`), and prod deliberately sets `connection-string: ""` to force token credentials.


**Alignment with `docker-resources`** — *done 2026-08-18, requested mid-slice, and it found a live misconfiguration.*
- [x] **Every service's `client_credentials` client id was wrong.** Services declared `identity-service` / `catalog-service` / `booking-service` / `api-gateway`; the realm provisions `myticketzm-identity-service` and siblings. **No service could obtain a token.** The mismatch is invisible from either repository alone — each looks reasonable, only the pair is wrong. All 7 sites aligned and made env-driven.
- [x] **My own D change corrected.** I had put development secret defaults in `application-local.yml`. Wrong twice: the placeholder values (`identity-service-secret`) never matched the provisioned realm, and the *real* values would have been credentials in git — the exact defect D2 forbids. The local profiles now carry no secret at all, only a pointer to `docker-resources/env/ticketing.env` and `docker-resources/env/keycloak.env`, which are gitignored and are the same variables the realm import substitutes.
- [x] **`DockerResourcesAlignmentTest`** keeps the two repositories in step: client ids must exist in the realm, realm names must match (allowing the deliberate `-admin` second realm), and **no secret value from the env files may appear in committed config**. It compares identifiers only — never reads or echoes a secret. Skips cleanly when the sibling repo is not checked out, since CI may see only this repository. Proven to fail: reverting one client id to its old value produced `'catalog-service' is not a client in the realm`.

**E · Gateway and JWT** — *done 2026-08-18.*
- [x] **E1** Gateway namespace — *already correct, now asserted.* Config sits under
  `spring.cloud.gateway.server.webflux.*` and the pom uses
  `spring-cloud-starter-gateway-server-webflux`. `GatewayConfigurationLintTest` fails if a key is
  placed directly under `spring.cloud.gateway` or the deprecated starter reappears. This is worth
  a lint precisely because a misplaced key is **not an error** — the app starts clean with no
  routes, no rate limiter and no circuit breaker, which reads as a routing bug for an afternoon.
- [x] **E2** All four checks, on one path — *and the fourth was not being applied.*
  - **The defect.** `keycloak.expected-audiences` was read into a field by all three services and
    the gateway, then used **only** on the `issuers.size() > 1` branch. On the other branch —
    `ReactiveJwtDecoders.fromIssuerLocation` — the setting was accepted, stored and silently
    ignored. A resource server checking signature, issuer and expiry but not audience accepts any
    token the realm minted for **any** client: the mobile app's token opens the internal admin
    API, and every log line looks normal.
  - **The branch was also mislabelled.** Its comment read "OFF by default" in all four files while
    `keycloak.trusted-issuers` **defaults to the admin realm** — so the path nobody thought was
    live was the only one running, and the documented fallback never executed. A security control
    that applies only in one configuration is not a control.
  - **Collapsed to one path.** `PlatformResourceServer` is now the single entry point; every
    service and the gateway route through `MultiIssuerJwtResolver`, one issuer or several, and
    `decoderFor` is the only place a decoder is built.
  - **JWKS is now lazy.** `fromIssuerLocation` fetches OIDC discovery *during bean creation*, so
    every service failed to start while Keycloak was briefly unreachable — a boot-time coupling
    with no security benefit, since `iss` is pinned either way. Asserted: building a decoder makes
    zero HTTP calls.
- [x] **E3** Four rejections, at each service directly — *`JwtValidationContractTest` in catalog,
  booking and identity, each driving **its own** `SecurityConfig`'s filter chain.*
  - Probed at **`/graphql`** as well as an authenticated REST path. BE-7's acceptance names
    `/graphql` specifically, and it carries essentially all traffic — a service that authenticated
    everything except its GraphQL endpoint would be open in practice while looking locked down.
  - **The valid-token case is asserted too.** Without it, a decoder pointed at a JWKS URL that
    404s rejects all five inputs and looks like a perfect result.
  - **`StubIssuer` rather than a Keycloak container.** A real Keycloak can produce none of the four
    on demand: it will not sign with a key it does not hold, will not mint a token that expired ten
    minutes ago, and needs a second client with an audience mapper before it emits a wrong
    audience. The container would be ceremony around the same hand-assembled JWT, plus a minute of
    pull-and-boot per module. **What it would add — confidence in Keycloak's real token shape —
    this does not provide**, and that is recorded in the class rather than glossed.

**Every assertion in E was watched failing.** Five mutations:
| Mutation | Result |
|---|---|
| Audience validator returns null | 2 shared + all 3 service tests fail |
| `createDefaultWithIssuer` → no-op | expiry assertion fails |
| Resolver returns the first manager for any `iss` | **passed — the assertion was vacuous** |
| Resolver fallback **and** `createDefault()` | issuer assertion fails |
| Forgery key published in JWKS | signature assertion fails |

The third is the one worth keeping. The wrong-issuer case used a token from a second realm, which
carries *that realm's signature* — so it was being refused by the signature check and the `iss`
claim was never reached. `StubIssuer.claimingIssuer` now mints a token signed by the **trusted**
realm's published key that claims another issuer, leaving `iss` as the only thing that can refuse
it. That it then took mutating **both** layers to fail is the finding: issuer is defended twice,
by the resolver and by the validator.

**Open, and not closed by this slice: audience validation is off in every environment.**
`KEYCLOAK_EXPECTED_AUDIENCES` is blank, so the fourth check does not run in production. Turning it
on needs **both halves**, and the other half is in `docker-resources`: the realm has **no
`oidc-audience-mapper` on any client**, so Keycloak emits `"aud": "account"` and enabling this
alone would reject every token the platform issues. Not defaulted to a service's client id for
that reason — a default that breaks all authentication is worse than a logged gap. Each service
now logs the gap at WARN on every startup, and
`PlatformResourceServerTest.audienceIsNotEnforcedWhenUnconfigured` pins what it costs, so the
warning describes a measured consequence rather than a theory.

Order: **A1–A3 → B1 → B2/C3 → B4 → A4 → C1/C2 → D → E.** A4 is the bulk and is safest once the
lint that proves it is in place.
