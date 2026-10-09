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

## R0 findings *(2026-08-19)*

| Item | Expected | Measured | Class |
|---|---|---|---|
| `ErrorCode` registry in `shared-library` | 93 rows | **absent** | `absent` |
| `@DgsExceptionHandler` | one per registry row | **0, anywhere** | `absent` |
| Catch-all for `Throwable` | present | **absent** | `absent` |
| GraphQL error path | typed, with `extensions` | **none — DGS's default** | `absent` |
| booking's `GlobalExceptionHandler` | GraphQL | **REST only** (`@ExceptionHandler`/`ResponseEntity`) | `contradicted` |
| Codes raised that are §4 rows | all | **2 of 16** in booking; 5 more elsewhere are outside §4 | `contradicted` |
| `ex.getMessage()` copied into the response body | never | **on every handler** | `contradicted` |

**R0 was wrong here, and the correction matters.** The first pass grepped for
`@DgsExceptionHandler`, found none, and concluded the GraphQL surface had no error handling. It
has two mechanisms per service, and one of them is dead.

| Bean | Type | Live? |
|---|---|---|
| `web/graphql/exception/GraphQLExceptionResolver` | Spring GraphQL `DataFetcherExceptionResolverAdapter` | **yes — handles everything** |
| `graphql/exception/MongoValidationExceptionHandler` | graphql-java `DataFetcherExceptionHandler` | **no — unreachable** |

`DgsSpringGraphQLAutoConfiguration.graphQlSource` collects every `DataFetcherExceptionResolver`
bean in order and *appends* the `DataFetcherExceptionHandler` bean last, wrapped in
`ExceptionHandlerResolverAdapter`. Spring GraphQL walks that chain and stops at the first resolver
returning non-null — and `GraphQLExceptionResolver` ends in a catch-all that returns
`INTERNAL_ERROR` for anything unmatched. **It never returns null, so nothing after it ever runs.**

The consequence is not academic. `MongoValidationExceptionHandler` is the only code that treats
`TenantIsolationException` as `PERMISSION_DENIED` with `securityIncident: true` — a cross-tenant
access attempt. That path has never executed. Those violations are currently swallowed by the
catch-all and reported to the caller as a generic internal error, and to the log as one line
without the security marker. A dead handler is worse than a missing one, because the file exists,
reads correctly, and is cited as evidence the case is covered.

**This also constrains how the new handler is installed.** The autoconfiguration declares
`dataFetcherExceptionHandler()` `@ConditionalOnMissingBean` and injects it into `graphQlSource`
*by type*. Adding a second `DataFetcherExceptionHandler` bean does not layer — the context fails
to start with `NoUniqueBeanDefinitionException`. So BE-3 removes both existing mechanisms rather
than adding a third beside them.

**All three services carry the same leak on the live path.** `buildError` copies
`ex.getMessage()` into the GraphQL response — so the CWE-209 finding R0 attributed to booking's
REST handler is on every service's GraphQL surface too. No handler anywhere sets `retryable`, and
every one spells the key `code` rather than `errorCode`.

**Fourteen of booking's sixteen codes are outside §4.** `TICKET_NOT_FOUND`, `ACCESS_DENIED`,
`INVALID_STATE`, `PAYMENT_FAILED` and ten others. Only `INTERNAL_ERROR` and
`TICKET_ALREADY_VALIDATED` are registry rows. This is §4's own argument made concrete — forty
spellings of *not found*, and a frontend `switch` nobody can complete.

**Every handler copies `ex.getMessage()` into the response.** That is the CWE-209 leak, present
on every path, and it is why R0 classifies this as `contradicted` rather than `partial`.

## A · Backend

- [x] **BE-1 · `DomainRefusal`, `ErrorCode` and `GraphQlErrors`** — *8 tests, 2 mutations verified.*
  - `ErrorCode` holds **93 rows generated from §4**, not transcribed. `ErrorCodeRegistryTest`
    parses the table and asserts the enum **equals** it in both directions: an extra code is a
    refusal nobody documented, a missing one is a client branch that never fires. Proven by adding
    `SOMETHING_NOBODY_SPECIFIED`, which failed by name.
  - `retryable` comes from the registry rather than the call site, so the same code cannot be
    retryable in one service and not another — which would make the client's decision depend on
    which service answered.
  - **`GraphQlErrors.forDefect` takes a `Throwable` and ignores it entirely.** No message, no class
    name, no frame, no cause. The parameter exists so call sites read naturally and so a later
    change cannot quietly start including it. Every defect produces **byte-identical** extensions,
    because two internal failures distinguishable from outside is a side channel an attacker can
    probe. Proven by adding a `debug` key, which failed two assertions.
  - The exchange is *log it instead*, not *lose it*: a `correlationId` is always carried, so
    support can find everything the caller was not told.
  - A refusal's `details` **cannot overwrite** the contract keys. A call site that could set
    `retryable: true` would make the client retry something the registry says fails every time.

- [x] **BE-2 · the catch-all, wired as the one exit every failure takes** — *5 tests, 2 mutations.*
  - `PlatformDataFetcherExceptionHandler` implements graphql-java's
    `DataFetcherExceptionHandler`. One handler rather than one per exception type, because a
    per-type handler covers the types somebody remembered — and the failure this exists to
    prevent is the one nobody anticipated, which is also the one carrying the most revealing
    message. The default path is the safe path; refusals are the special case.
  - **The unwrapping is the load-bearing part.** Every refusal on this platform is raised inside
    a `Mono`, so it arrives wrapped in a `CompletionException`. A handler checking
    `instanceof DomainRefusal` on the outer throwable classifies all of them as defects — safe,
    and useless: every sold-out tier would report `INTERNAL_ERROR`. That would disable the entire
    error contract **while every leak test still passed**. Mutation-verified.
  - Refusals log at INFO, defects at ERROR. A sold-out tier is the platform working; logging
    refusals as errors fills a dashboard with noise until nobody reads it.

**A bug this found in itself.** `getPath()` and `getSourceLocation()` dereference the
`DataFetchingEnvironment` without a null check, so the first draft threw from inside the
exception handler — the worst available failure mode, since the original exception is lost and
what happens next is outside this contract entirely. Guarding the error builder was not enough:
the same call sat in the **log statements**, which run first. Both are now guarded and the path
degrades to `<no path>`.

- [x] **BE-3 · registry codes wired into all three services** — *13 tests, 2 mutations verified.*
  - **The refusal path is installed by replacing, not adding.** DGS collects every
    `DataFetcherExceptionResolver` bean and consults them *before* the
    `DataFetcherExceptionHandler`, stopping at the first non-null answer. Each service had a
    resolver ending in a catch-all, so it answered everything and the platform handler would
    never have run. A second `DataFetcherExceptionHandler` does not layer either — DGS injects
    it by type, so the context simply fails to start. Both are now asserted per service by
    `assertNoCompetingErrorBeans`, because a shadowed contract looks completely healthy from
    outside: errors still return, queries still fail, and every "a bad request produces an
    error" test still passes. What silently disappears is the codes, `retryable`, and the
    message boundary.
  - `ErrorContractAutoConfiguration` is `@AutoConfiguration(beforeName = …DgsSpringGraphQLAutoConfiguration)`.
    Ordering is not cosmetic: both that bean and DGS's default are `@ConditionalOnMissingBean`,
    so registering second means backing off and running on DGS's default handler.
  - **`RefusalTranslator` rather than converting the exceptions in place.** Half the exceptions
    that matter belong to Spring, Spring Security or the database driver and cannot be made to
    extend `DomainRefusal`. The services' own could have been, and were not, because their
    messages are assembled from data — `"Ticket not found: %s (%s)"`,
    `"%s with %s '%s' already exists"`. Converting the class without rewriting the message
    carries the leak forward under a new name and looks like progress. A translator forces every
    message to be written fresh at one reviewable site per service.
  - **Coverage is scanned, not listed.** `RefusalCoverage` walks each service's exception package
    reflectively, so a class added tomorrow is covered today. The failure it prevents is quiet:
    an unmapped exception falls through to the defect path, so an ordinary business refusal
    reaches the caller as `INTERNAL_ERROR` and the on-call as an ERROR with a stack trace. The
    service works and the dashboard lies.
  - **`IllegalArgumentException` and `IllegalStateException` are deliberately not translated.**
    On this platform they come from asserted invariants, so calling them `BAD_REQUEST` blames the
    caller for a server bug — they retry, edit correct input and give up, while the log records a
    handled refusal and no alert fires. `illegalArgumentIsNotAClientError` pins this.
  - Translator order is `@Order`ed with the platform last, so a service can be more specific
    about an exception both understand. That is what BE-6 needs: a duplicate key on the
    idempotency index is a **non-retryable** `IDEMPOTENCY_KEY_REUSED`, while any other duplicate
    key is a retryable `RESOURCE_CONFLICT`. Retrying the first is the double-charge the key
    prevents.
  - A translator that throws is caught and treated as not recognising the exception. Losing the
    original failure to a bug in the code describing it is the same worst case the null-guards in
    BE-2 exist to prevent.

**A leak found in BE-2 while wiring this.** `DomainRefusal`'s own javadoc states that the message
never crosses the boundary — and BE-2's handler passed `refusal.getMessage()` straight to
`GraphQLError.message`. The doc was right and the handler was wrong, which is the more dangerous
ordering: the guarantee was documented, reviewable, and false. The client now receives
`GraphQlErrors.refusalMessage(code)` — the code name, which it already has in
`extensions.errorCode` and which is provably free of anything the throw site knew.
`developerMessageStaysServerSide` uses a refusal carrying an email address and a tenant id, and
fails when the message is restored. Mutation-verified.

The identity mappings are where this pays: `DuplicateResourceException` carries `resourceType`,
`fieldName` and `fieldValue` and formats `"User with email someone@example.com already exists"`.
Returning any part of that answers *is this person registered?* for anyone who asks, on a
registration form, without a session. The refusal carries `RESOURCE_CONFLICT` and nothing else.

- [x] **BE-5 · the tenant boundary** — *mechanism done and tested (5 tests, 1 mutation).*
  *Re-measured 2026-09-19:* the boundary the R6 finding below said was missing now exists and
  answers through this mechanism — `TenantGuard` (shared-library), booking's `CallerScope`,
  `TenantReads` and `EventGateAccess`, and identity's member queries all call
  `TenantBoundary.refuse`. The reads not yet behind it are ratcheted by `TenantBoundaryLintTest`
  (catalog 19) and belong to ET-PLT-007's tenant scoping, not to the error contract.
  - `TenantBoundary.refuse(unknownCode, what)` answers a cross-tenant reach with **the same
    `*_UNKNOWN` code an unissued id produces** — same code, same message, no details.
    `crossTenantMatchesNotFoundExactly` compares the two responses key by key with only the
    correlation id removed, because any difference at all is the signal: a caller holding
    candidate ids diffs the answers and learns which are real, needing access to none of them.
  - **It rejects `ACTOR_NOT_PERMITTED` at the call site.** That code reads as the more precise
    and more helpful answer, and passing it reopens the oracle in one line. Any code not ending
    `_UNKNOWN` throws, so the mistake fails where it is made rather than quietly on the wire.
  - No details map, ever. A detail naming the resource, the owning tenant or the permission
    required restores exactly what withholding the code prevented. Mutation-verified: adding
    `{"reason": "cross-tenant"}` fails two assertions.

- [x] **BE-6 · lock contention and idempotency, told apart** — *7 tests, 1 mutation verified.*
  - A lost lock race is a retryable `RESOURCE_CONFLICT`; a reused idempotency key is
    `IDEMPOTENCY_KEY_REUSED` and **never** retryable. Both are "a unique constraint said no" and
    they call for opposite advice, and because the client decides from `extensions.retryable`
    alone, confusing them does not produce an error anyone sees — **it produces a second
    payment.** Mutation-verified: weakening the code to `RESOURCE_CONFLICT` fails four tests.
  - The distinction is made in `BookingRefusalTranslator`, not the platform, because only the
    service knows which of its unique indexes guards a key. The index name is read from the
    driver's message — brittle in principle, and the alternative is calling every duplicate key
    retryable, which on a payment path means advising a client to repeat a charge the database
    just refused. The names are §4 registry rows, so `BookingIndexRegistryTest` fails a rename
    before it can turn this into a silent fallback.
  - An **unrecognised** duplicate key deliberately falls back to retryable `RESOURCE_CONFLICT`.
    Guessing "idempotency" for an unknown index would tell a caller a key was reused when they
    never sent one.
  - `serviceWinsOverPlatform` asserts the chain order directly. Without it the two retryability
    tests still pass in isolation while the wire is wrong.

**A production bug the container test found.** `ErrorContractRealityTest` raises a duplicate key
from a real unique index rather than constructing the driver's message by hand, and the
classification came back `INTERNAL_ERROR` with `retryable: true`. The cause: a reactive write
surfaces `com.mongodb.MongoWriteException` **untranslated**, not Spring's
`DuplicateKeyException`, so every unit test for BE-6 had been proving that the parser matched a
string the test author wrote.

On a payment path that combination is the worst available. The client is told an internal error
occurred and that retrying may help, so it retries — which is exactly the double charge the
idempotency key exists to prevent, reached through the error contract itself. `DuplicateKeys` now
recognises the Spring type, `MongoWriteException` and `MongoBulkWriteException`, matching on
error code **11000** rather than on the exception type, because `MongoWriteException` covers every
write failure and calling a schema-validation rejection a uniqueness conflict would offer a retry
that fails identically forever.

**Two live data-integrity bugs found while doing this, both fixed.** BE-6 needed to know which
indexes guard idempotency, which meant reading them — and two were declared
`@Indexed(unique = true, sparse = true)` on optional fields. Sparse skips a document only where
the field is **absent**, and Spring Data writes a null field as `field: null`, which is present.
Both indexes therefore index every null under the single key `null`.

| Field | Consequence before the fix |
|---|---|
| `booking_checkins.scanId` | Online scans carry no scan id. The **first scan at a gate takes the key `null` and every scan after it is rejected as a duplicate** — the guard breaking the one path it does not protect. |
| `booking_payout_requests.idempotencyKey` | The first keyless payout takes `null`; every later one collides. Unrelated organizations block each other from being paid. |

Both are now `unique` + `partial $type: string`, declared in `BookingIndexInitializer` against new
ET-PLT-002 §4 rows, with the model annotations removed so the registry is the single authority.
The javadoc on each field stated the wrong belief in as many words — *"sparse so the historical
rows … do not collide"*, *"Sparse because online scans do not carry one"* — so the reasoning was
recorded, reviewed and wrong, which is how it survived. Both now say why partial is required.

**The lint that stops this recurring.** `IndexAuthorityLintTest` bans `unique + sparse` outright
— all seven occurrences were wrong, so there was no baseline worth preserving — and ratchets the
per-service count of index annotations, which create indexes outside the §4 registry where no test
checks them. Removing annotations without lowering the budget also fails, because leaving it high
re-opens exactly the room just closed. Both rules mutation-verified.

A **seventh** instance surfaced while writing that lint: `identity_users.username`. Better Auth
creates the user document on first OIDC login without a username, so nulls there are the normal
state of every account between signup and sync — the second concurrent signup would have been
rejected as a duplicate. Also fixed to partial.

These are the **fifth, sixth and seventh** instances of this bug class in the codebase; see
[`specs/FINDINGS.md`](../FINDINGS.md) F-002 for why they keep appearing.

## R6 finding · there is no tenant boundary to make non-disclosing *(2026-08-19)*

BE-5 assumes a check exists that refuses a cross-tenant reach, and that its answer is too
informative. Neither half holds. A census of every `@DgsQuery`, `@DgsMutation` and
`@DgsEntityFetcher` taking an id and returning organization-scoped data found **~37 entry points
with no tenant filter and no ownership comparison** — they do not refuse informatively, they
return the record.

| | |
|---|---|
| `organizationId` claim in the JWT | **none** — keycloak-extensions never mints one |
| Tenant context holder | **none** |
| `findByIdAndOrganizationId`-shaped finders | **zero, in all three services** |
| `@auth` directive tenant dimension | **none** — role only |
| Unscoped id-taking entry points | **~37** (catalog ~16, identity ~17, booking ~5) |

**The sharpest holes are writes, not reads.** The six `TicketTierMutationResolver` mutations and
`updateEventAccessibility` are `@PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")` and then call
straight through to `tierService.updateTier(tierId, …)` / `deleteTier(tierId)`, whose
implementations do a bare `findById` with no organization comparison. **Any account holding the
`ORGANIZER` realm role can reprice or delete any other organization's ticket tiers by id.**

Three further observations from the same census:

1. **`TenantAccessGuard`, `OrganizationSecurityService.isMemberOfOrganization` and
   `TenantValidationService` all exist, fail closed, and are called by nothing.** That is the
   orphan pattern again, and here it is load-bearing: the components read as evidence the boundary
   is enforced.
2. **Four `@PreAuthorize` expressions name beans that do not exist** —
   `@payoutSecurityService` (×3) and `@bankAccountSecurityService`. SpEL resolution failure throws,
   so these fail closed; the effect is organizers locked out of their own payouts and bank
   accounts rather than a leak. Dead either way.
3. **`ActorOrganizationResolver.resolve` returns `organizations.get(0)`** for a user in more than
   one organization, with a warning log. Its own javadoc records this as known-wrong.

**Why this is not fixed here.** Closing it means deciding where tenant identity comes from — a
Keycloak claim, a membership lookup per request, or a resolved context — and that decision belongs
to the identity and organization specs, not to the error contract. Retrofitting 37 endpoints under
ET-PLT-005 would also invent authorization semantics no spec has stated. What ET-PLT-005 owes is
the shape of the answer once a check refuses, and that is now built, enforced and tested, so every
check added later is non-disclosing by construction.

**Recorded as a blocking finding** — see `specs/FINDINGS.md`.

- [x] **BE-4 · Bean Validation, and the `fields` extension** — *9 tests, 2 mutations verified.*

  **R0 for this task: validation was entirely decorative.** 203 constraint annotations on model
  classes, 11 more on input types, `spring-boot-starter-validation` on every service's classpath —
  and **zero `@Valid` on any resolver and no `ValidatingMongoEventListener`**, so not one of them
  ever executed. Nothing looked wrong: the annotations read as protection, and the collection
  `$jsonSchema` rules caught the worst of it at write time with a driver error naming the
  collection and the rejected value.

  - `ValidationRefusal` is **one error carrying the whole field list**, per §4's rejection of
    per-constraint codes. One error and not one per violation: an array whose length depends on how
    wrong the input was means the client rendering "the first error" marks a different field each
    time, and the one rendering all of them repeats a banner.
  - **`{ path, constraint }` — the constraint name, never the message.** §4 says this outright and
    the reason is disclosure: a message template interpolates what it rejected, so a `@Pattern` on
    a phone number renders the number and a custom validator renders the code it just checked
    against stored data. The test uses messages containing `hunter2` and an email address and
    asserts neither reaches the wire. A name is also the only form a client can translate.
  - The field list is **sorted**. Bean Validation returns a `Set`, so unordered the same bad request
    answers differently on each call and the errors under a user's form reshuffle between attempts.
  - `@Validated` on all **37** mutation resolvers, `@Valid` on **54** input arguments. Verified from
    the DGS sources first — `DgsSchemaProvider` calls `AopUtils.getTargetClass(instance)` when
    discovering handlers, so the proxy `@Validated` introduces does not hide the resolvers.
  - **The lint found three arguments the rollout missed**: `List<ValidateTicketInput>`,
    `List<InviteMemberInput>`, `List<EventAccessGrantInput>`. Bulk mutations, and the ones where a
    field list matters most. Element cascading and the `items[1].name` path shape were **probed
    against a real validator** rather than assumed, and are now pinned by a test — without the index
    a form of twenty invitations is told "a name is blank" and cannot mark which.

  Two rules, deliberately different in strength. `@Validated`/`@Valid` is **banned outright** when
  missing: it is mechanical and there is no reason to opt out. Input classes with **no** constraints
  are **ratcheted** (booking 22, catalog 24, identity 14), because choosing the right constraint per
  field is domain work, and a ban would invite a lint-satisfying `@NotNull` on everything — worse
  than nothing, because it looks like validation.

- [x] **BE-7 · RFC 9457 on the REST surface** — *3 parity tests, 1 mutation verified.*
  - `PlatformProblemDetailAdvice` replaces booking's per-exception advice, which built
    `ResponseEntity<ErrorResponse>` and copied `ex.getMessage()` in **28 places**. Twelve other REST
    controllers across the three services had no advice at all and fell through to Spring's
    defaults.
  - **Parity is structural, not promised.** The advice resolves through *the same*
    `RefusalTranslator` chain as the GraphQL handler, so a code can only diverge if a translator is
    registered for one transport and not the other. `RestGraphQlParity` asserts it anyway over
    **every exception class each service declares** — the same reflective scan the coverage test
    uses, because checking a handful proves the ones somebody thought of.
  - **The status is derived from the registry's classification, never chosen per handler.** Mapping
    by hand is how the same refusal becomes 400 in one controller and 409 in another until the code
    in the body stops agreeing with the status in the header. `UNAVAILABLE` is 503 rather than 500
    so that intermediaries retry it, matching `retryable` on those rows.
  - `UNKNOWN` is listed explicitly rather than swept into a `default`, so adding a family to
    `ErrorClassification` fails this build instead of silently becoming a 500.

**A duplication removed while writing BE-7.** The advice and the GraphQL handler each needed
`unwrap` and the translator walk, and I wrote the second copy before noticing that two copies of the
logic deciding *whether something is a refusal* is exactly the drift the parity test is meant to
catch — and the parity test would have been comparing one implementation against a copy of itself.
Both now call `RefusalResolution`. There is one place to be wrong, and both transports are wrong
together, which is far easier to notice than one of them being quietly right.

**All seven backend tasks and all five frontend tasks are done.** The GraphQL and REST surfaces both answer in
registry codes with `retryable`, no exception message crosses either boundary, and validation
failures carry the field list. The one gap that remains is not in this spec: the tenant boundary
has no checks to shape (F-001).

### Remaining backend tasks

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

## C · Frontend — Track F0-6 *(done, 2026-08-19)*

- [x] **FE-1 · `extensions.errorCode` → rendered state, for every registry row** — *5 tests.*
  - The mapping is by **classification**, not by code. 93 bespoke UI branches would be a table
    nobody can keep complete, and the first unmapped code would render blank — the exact failure a
    closed registry exists to prevent. A code appears by name only where the product needs
    something its classification does not give it, and that list is three entries long.
  - `errorContract.test.ts` parses §4 and asserts the count **exactly**, not `> 50`. A regex that
    silently stops matching half the table still passes a loose check, and then "every code renders"
    means "every code the regex happened to find renders".
  - An unrecognised code renders a sentence **and logs**, deduplicated by code — a failing list view
    fires the same refusal on every row, and two hundred identical console lines get muted, which
    is the same as not logging at all.

- [x] **FE-2 · `retryable` drives whether a retry is offered** — *4 unit + 4 component tests,
  2 mutations verified.*
  - `ErrorState` honours `onRetry` **only** when the server said retrying could work. Passing a
    handler is permission to show the button, not an instruction.
  - Tested by rendering, not by asserting `action === 'none'`. The two come apart the moment a
    screen decides to be helpful: rendering the button whenever a handler is passed reads as
    correct, is the natural way to write the component, and produces a retry button on a declined
    payment. That mutation fails three tests.

- [x] **FE-3 · `TOKEN_REVOKED` signs the user out rather than retrying** — *4 tests.*
  - **The Apollo error link was reading `extensions.code`.** The contract key is `errorCode`;
    `code` is graphql-java's generic key that this platform never populates, so the branch returned
    `undefined` for every refusal and the link never ended a session. Nothing looked broken — there
    is no symptom until a revoked token is quietly accepted.
  - The decision now lives in `sessionAction`, which takes **the whole error** rather than a code
    string, so reading the extensions is part of what is under test. A helper taking a bare string
    cannot express that guarantee, because the caller would already have made the choice that
    matters.
  - Revoked is separated from unauthenticated: both route to sign-in, only one also clears local
    storage. Re-presenting a revoked token fails forever, so leaving it behind leaves the user
    apparently signed in until something asks the server.

- [x] **FE-4 · Field-level validation display from the `fields` extension** — *6 tests.*
  - `fieldErrors` maps `path → message` keyed on the **constraint name**, so a rule added to a new
    input renders without touching the mapping. The index survives (`invitations[3].email`),
    because without it a form of twenty invitations is told "a name is blank" and cannot mark which.
  - One message per field even when two constraints fail: "wrong length" is a consequence of being
    empty and disappears when it is fixed.

- [x] **FE-5 · Error components in the design system** — *compliance suite green.*
  - `ErrorState` and `FieldError`, barrel-exported, tokens only, `data-testid` selectors,
    `role="alert"`. The correlation id renders in `--font-mono` — it is shown precisely because
    everything else is deliberately withheld, and without it "something went wrong" is the end of
    the conversation.

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

- [x] R0 recorded; every leaking error path classified — table below
- [x] `ErrorCode` equals §4 row for row; all corpus-declared codes present
  - `ErrorCodeRegistryTest` parses §4 out of `spec.md` itself and compares row for row, rather than
    retyping it into a fixture. A third copy of the registry would be a third thing to drift.
- [x] Every handler sets `retryable`; an NPE leaks no class, message or frame
  - Now proven **through a real GraphQL execution**, which is what R4's box asks for and what
    nothing did before — see below.
- [x] Every refusal persists nothing — *scoped as §3 states it: "asserted per refusal in the
      owning spec's tests (ET-PLT-006)".* What this spec owns is the platform half — a refusal
      raised inside the transaction rolls back every write in it — and that is proven:
      `ErrorContractRealityTest` on a real driver error, `TransactionRealityTest` on a replica set
      and its absence on a standalone `mongod`.
  - `ErrorContractRealityTest` asserts it on a real driver error for the idempotency path. "Every
    refusal" is a per-refusal obligation discharged in each owning spec's own tests
    ([ET-PLT-006](ET-PLT-006.md)), not here, and most of those specs are unopened.
- [x] Cross-tenant and unknown ids are indistinguishable
  - Proven in five layer-2 Testcontainers tests, each asserting the code, the **rendered message**
    and the details are identical between another tenant's id and one never issued. They existed,
    tagged only to the spec that decides *who may read*; they are now tagged to this one too, which
    decides that the refusal discloses nothing.
- [x] REST and GraphQL share one code vocabulary
  - `RestGraphQlParity` ranges reflectively over **every exception class each service declares** and
    compares code, classification and `retryable` across both transports. A mapping added to one
    transport only fails by name.
- [x] Every §4 code maps to a rendered frontend state; unmapped codes log rather than leak —
      **2026-09-19.** `libs/shared/src/lib/errors.ts` resolves each code through its §4
      classification, with a short list of codes that differ; `errorContract.test.ts` parses §4 out
      of `spec.md`, asserts every code renders a non-empty message that never shows the code, and
      that an unknown one still renders and logs once. Its row count now comes from
      `ErrorCode.java` — the hard-coded 93 had gone stale at 98 and failed for that reason alone.
- [x] `retryable` drives the retry affordance; `TOKEN_REVOKED` signs out — the button is decided
      by `retryable` at render level (`ErrorState.test.tsx`: a reused idempotency key shows no
      retry even when a handler is passed). New `client.session.test.ts` drives the **real** shared
      Apollo client — the one all three apps build — against a stubbed server: `TOKEN_REVOKED`
      drops the stored credential, runs the app's sign-out once and is not retried;
      `ACTOR_NOT_AUTHENTICATED` and a gateway 401 sign out without discarding storage; an ordinary
      refusal leaves the session alone. Mutation-verified: removing `clearSessionState()` fails it.
- [x] Compliance suite green (tokens, fonts, closed props) — `npm run test:compliance`, 15 tests:
      no raw hex (0, frozen), raw `px` ratcheted per app, three fonts only, `data-brand` per app,
      and — new — **closed prop sets**, with the scanner shown red on a fixture. 38 literal values
      sit outside the sets today (admin 8, org-admin 19, ticketing 11 — mostly `Button
      color="gray"`/`"teal"`), frozen as ratchets like `px`: the replacements are design decisions
      and the design files are unreadable until `/design-login`. `test:compliance` itself pointed
      at an Nx target that does not exist; it now runs the suite.
- [x] `mvn -f backend verify -Dgroups=ET-PLT-005 -DfailIfNoTests=false` green — **100 tests**
  - The flag reads `false` because `true` **cannot pass, for any spec**. See [F-022](../FINDINGS.md);
    the guarantee it was meant to give now lives in `SpecTagCoverageTest`.
- [x] Spec `status:` → `implemented` — **2026-09-19**, once ET-PLT-004 closed and the three frontend rows ran. The note below records why it waited.
  - `SpecStatusLintTest` refused it on two counts and both are right. **First:** `blocked_by` names
    ET-PLT-001 and ET-PLT-004, and both are still `in-progress` — a spec cannot claim to be built
    on foundations that are not. **Second:** four gate boxes are unticked, three of them frontend
    and one `[~]`, and "implemented" means the gate is met, not mostly met.
  - So R1–R7 are done to `implemented` standard and the *status* stays `in-progress`, which is what
    is actually true. It advances when ET-PLT-001 and ET-PLT-004 close and the frontend slice runs.
  - Worth stating plainly: this spec was worked ahead of its blockers by choice, and the corpus
    caught it without being asked. That is the machinery working.

## F · R0 · Classification

| § | Requirement | Classification | What was actually found |
|---|---|---|---|
| R1 | Every refusal is a `DomainRefusal` carrying a registry code | partially-satisfied | The registry and handler are complete. But refusal types are **translated, not inherited** — see the deviation below. 110 sites still throw raw `IllegalArgumentException`/`IllegalStateException`. |
| R2 | Every refusal reaches the client with the same `extensions` shape | **contradicted → fixed** | The handler was correct and **nothing proved it ran**. Every test drove it directly. |
| R3 | Retryability is stated and is correct | already-satisfied | `ErrorCode` carries `retryable`; the registry test enforces §4 row by row. |
| R4 | Unhandled failures leak nothing | partially-satisfied → fixed | The handler leaks nothing. The *integration* probe R4 names did not exist, and `graphiql` shipped on in prod — [F-021](../FINDINGS.md). |
| R5 | Validation is one code carrying the field list | partially-satisfied | Shape is right and asserted. Coverage of input classes is a ratchet, not complete. |
| R6 | Existence is not disclosed across a tenant boundary | already-satisfied | Proven in five layer-2 tests — they were simply not tagged to this spec. |
| R7 | Lock contention is a retryable refusal | already-satisfied | Including the subtlety: booking's translator outranks the platform's, so a reused idempotency key does **not** become a retryable `RESOURCE_CONFLICT`. |

### The finding that matters most

R2's contract was **proven in isolation and unproven in place.** Every error-contract test in the
corpus hand-built a `DataFetcherExceptionHandlerParameters` and called
`PlatformDataFetcherExceptionHandler` itself. Not one executed a GraphQL query.

That gap is not cosmetic here. graphql-java installs its own handler unless given one, and that
default puts **the exception's message** into the response. So a mis-wiring does not announce
itself — the service starts, errors still come back, and the platform quietly answers with exactly
the leak R4 exists to prevent. `ResolverFailureContractTest` now executes a real query and, when
the handler is removed, fails on the assertion naming that leak: the probe's fake connection
string, credential and all, arrives at the client.

`ErrorContractAutoConfiguration`'s own javadoc named **`ErrorContractWiringTest`** as the guard for
its two invariants — "per service, because nothing about the resulting behaviour looks broken from
the outside". That class did not exist. It does now, and both invariants held; nothing had been
enforcing them.

### Deviation recorded, not corrected

R1 asks that every refusal type "extends `DomainRefusal`, is named as a fact, and ends in no
`Exception` suffix". The platform does the opposite on purpose: services declare ordinary
`*Exception` classes and a per-service `RefusalTranslator` maps each to a registry code.

Not changed, for the reason F-013 records. Inheritance would put a shared-library type in every
service's domain signatures, and the translator design already delivers what R1 is *for* — one
closed vocabulary, `retryable` on every error, a service unable to invent a code. The 110 raw
`IllegalArgumentException` sites are the real remaining gap and are mostly constructor invariants
("Account code is required") rather than reachable business refusals; separating the two is a
per-spec judgement, not a mechanical sweep.

### Evidence

- `ResolverFailureContractTest` (5) — a real execution; mutation-verified by removing the handler,
  which fails 4 of 5 including the leak assertion by name.
- `ErrorContractWiringTest` (3) — the test the auto-configuration cited and nobody wrote.
  Mutation-verified three ways: drop the DGS ordering, unregister the auto-config, add a rogue
  resolver. Each fails its own assertion and no other.
- `DebugSurfaceLintTest` (2) — F-021. Its own first version was **wrong and green**; the mutation
  is what said so.
- `SpecTagCoverageTest` (2) — F-022, mutation-verified in both directions.
- `verify -Dgroups=ET-PLT-005`: **100 tests**, BUILD SUCCESS. Full suite **BUILD SUCCESS**.
