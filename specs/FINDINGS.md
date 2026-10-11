# Findings that block or cross specs

Things discovered while implementing that no single spec owns, and that would be lost if
recorded only in the task file of whichever spec happened to surface them.

Each entry states what was **verified** as distinct from what was **inferred**, because the
difference decides whether the next person re-checks or acts.

---

## Open items index

*Added 2026-10-03 (see [F-042](#f-042--documentation-realignment)). Compiled by reading F-001 to F-041 in
full; severity is derived from each finding's own text, not re-assessed. Status is only as good as the
later finding that last touched the item: "Partially fixed" means a later entry closed part of it and no
entry records the rest. No item here was re-verified against the code on 2026-10-03.*

**How to use this index.** It is a map into the history, not a replacement for it. Find the row, open the
entry in "Where recorded" for the evidence, and check the *latest* finding named there, because entries are
append-only and an item is often closed several findings after it was raised. When you close or open
something, edit its row here in the same change and add the dated note to the finding itself. Closed items
are not listed except the first block, which records items a reader of F-038 or F-036 would otherwise still
think are open.

| Id | Title | Severity | Area / spec | Status | Where recorded |
|---|---|---|---|---|---|
| OI-01 | Refund policy is a flat 24-hour percentage; an event marked no-refund can still be refunded. Policies are platform configuration (D-25..D-30 line, F-040) but ET-FIN-004 R1's schedule is unbuilt; catalog still accepts the old values | High | finance / ET-FIN-004, ET-ADM-002 | Open | F-038 Open, F-040 |
| OI-02 | Identity by-id resolvers with no visible guard | High (OWASP A01) | identity | Partially fixed: F-037 covered about 37 organizer entry points; F-048 audited all of them and closed five more. Left open: event-grant ownership of `eventId`, `_entities` for User, token-only transfer read, public organization status fields | F-029 "Still open", F-037 |
| OI-03 | Admin-only approval fields on `Event` (`approvedBy`, `rejectedBy`, ...) are tagged but not guarded at the resolver; any signed-in caller can read them | Med | catalog / ET-CAT-001, ET-ADM-001 | Open | F-037 "Found along the way" |
| OI-04 | Identity encryption key once fell back to a literal in git; rotation is an operator action wherever a service ran without the variable | Med | identity / ops | Open (operator action; not recorded as done) | F-038 Fixed |
| OI-05 | Self-hosted Temporal staging trial (purchase, payout, refund; crash and deploy drills) not run; the service is not stood up | Med | platform / ET-PLT-015, D-28, D-33 | Open | F-033, F-034 (as Cloud, superseded), F-037, ROADMAP D-33 |
| OI-06 | Temporal runtime proof gaps: boot wiring of dynamic workflows and Schedule clients proven only in the test harness; Schedule runners (incl. group-mirror update path) not run against a server; ET-ORG-001 kill points not proven by killing a worker | Med | platform / ET-PLT-015 | Open | F-031, F-032, F-034 |
| OI-07 | Webhook evidence: `booking_webhook_receipts` not written (ET-PAY-002 R2); orphaned callbacks logged and answered 200 but not retained for re-matching (R5) | Med | payment / ET-PAY-002 | Open (F-037 added provider-call records, not webhook receipts) | F-030 Known limits |
| OI-08 | Failed refund does not reverse an `EARNED` to `CLAWED_BACK` commission clawback (needs a compensating journal entry) | Med | finance / ET-FIN-002, ET-FIN-004 | Open | F-036 Known limits |
| OI-09 | `DeadLetter.UNSUPPORTED_SCHEMA_VERSION` is a registry entry; no consumer compares `schemaVersion` against N-1 | Med | platform / ET-PLT-003 | Open | F-035 |
| OI-10 | Outbox wiring: `ConsumerGuard` and `ConsumerDispatch` orphaned; 14 consumed rows in the spec against 2 implemented; twelve publishers sent directly | Med | platform / ET-PLT-003 | Partially fixed: F-030 to F-032 moved booking, catalog and identity publication onto outboxes and workflows; no entry re-measures the rest | F-027 "What remains", F-030, F-032 |
| OI-11 | Admin platform summary aggregates `financial_transactions`, which nothing writes, so its figures are zero | Med | admin / ET-ADM-004 | Open (needs a finance decision; `booking_journal_entries` is the unverified candidate) | F-037 "Found along the way" |
| OI-12 | Only 14-20% of reachable service methods execute under test | Med | all / ET-PLT-006, VERIFICATION | Open | F-038 Open |
| OI-13 | Keycloak group mirror: reverse direction (group member with no MongoDB row is removed) and the pending-count metric/alert are not built | Med | organization / ET-ORG-002 R8 | Partially fixed: forward repair built (F-018), re-platformed as a Temporal Schedule (F-031) | F-018 |
| OI-14 | Four collections have no validator (`identity_audit_logs`, `identity_payout_config_audit_logs`, `identity_token_revocations`, `platform_configuration`) | Low | platform / ET-PLT-002 | Open (`platform_configuration` has since moved to `catalog_platform_configuration`, F-040; not re-checked) | F-035 |
| OI-15 | Legacy `catalog_categories`, `catalog_cities`, `catalog_provinces` remain in code beside the reference-data engine | Low | catalog / ET-CAT-003, ET-PLT-014 | Open | F-039 Open |
| OI-16 | 26 `@Document` fields nothing writes and 38 nothing reads | Low | data model | Open (needs a model review with migrations) | F-038 Open |
| OI-17 | Catalog export link points at `/api/exports`, which nothing serves | Low | admin / catalog | Open (known and left, F-040) | F-038, F-040 |
| OI-18 | `catalog_approval_notifications` has a validator and no `@Document` model (dead validator or missing model) | Low | catalog | Open, not investigated | F-035 |
| OI-19 | ET-ADM-001 R4: the admin queue does not display `approvalBlockers`; the field exists | Low | admin / ET-ADM-001 | Partially fixed (backend done; screen awaits the design pass) | F-036, F-037 |
| OI-20 | ET-PAY-001 §4: six `paymentAttemptsBy*` operations against one `paymentAttempts(intentId)` not reconciled | Low | payment / ET-PAY-001 | Open | F-036 Known limits |
| OI-21 | F-008: sign-in GraphQL surface (`login`, `register`, `requestPhoneOtp`, ...) unreachable behind `authenticated()`; vestigial or needs a per-operation exception | Low (availability, dead surface) | identity / ET-IDN-001 | Open, needs a ruling | F-008 |
| OI-22 | D-19 amendment work (collapse 60 pagination twins, 17 de-suffixes, 30 pairings needing a human decision) | Med | platform / ET-PLT-004 | Ruled 2026-09-01; completion not recorded in FINDINGS | F-004 |
| OI-23 | Parity-test known lists may only shrink: category presentation fields, device metadata, four `SendNotificationInput` options, `ReportExport.errorMessage`, `AuthPayload.tokenType`, deprecated `tags` | Low | catalog / identity | Closed 2026-10-06 — the deprecated elements were removed (F-045) | F-039 Open, F-040, F-045 |
| OI-24 | Finance escalation: no active `FINANCE_LEAD` only logs a warning; a retried escalation can resend channel copy and emails | Low | finance / D-32 | Open | F-034 Known limits |
| OI-25 | Frontend design debt: 684 raw `px` values frozen as per-app ratchets (admin 119, org-admin 520, ticketing 45) and 38 literal prop values outside the closed sets (mostly `Button color="gray"\|"teal"`) | Low | frontend / design system | Open (ratcheted; replacements are design decisions needing `/design-login`) | F-041, IMPLEMENTATION_PLAN Track F0 |
| OI-26 | Live `IXSCAN` confirmation: catalog must be booted once against the dev database so new indexes and `index-registry-conformance-5` run | Low | catalog / ET-CAT-003 | Open | F-039 Open |
| OI-27 | Minor: `openDisputeCount` has no layer-2 test; search attributes and payload codec deferred; Temporal Flow Atlas artifact predates `UserBackfillWorkflow`; Nx 23.1 inferred e2e targets do not run (worked around) | Low | platform / finance / tooling | Open | F-031, F-032, F-041 |

**Recorded as open, since closed (do not re-raise):** F-038's High item (`CreateEventInput` dropping 13
fields incl. `ticketTiers` and `location`, `UpdateEventInput` 14, notification-preferences input 9) and its
filter items (`discoverEvents` ignoring its filter; ticket, payout, escrow and admin event filters dropping
12 fields) were **fixed the next day in [F-039](#f-039--the-serious-bugs-f-038-found-event-authoring-notification-settings-discovery-and-list-filters)**.
F-002 (index authority) closed in F-037. F-036's refund/payout attempt rows, refund-id reuse and
ET-NTF-002 approval triggers closed in F-037. F-034 and F-033's Temporal Cloud items are superseded, see
OI-05.

**Counts (27 rows).** Severity: High 2, Med 12, Low 13. Status: Open 22, Partially fixed 4 (OI-02,
OI-10, OI-13, OI-19), ruled with completion unrecorded 1 (OI-22).

---

## F-001 · Organization-scoped data has no tenant boundary

**Found** 2026-08-19, while implementing [`ET-PLT-005`](tasks/ET-PLT-005.md) BE-5.
**Severity** OWASP A01 — Broken Access Control. **Status** **closed 2026-09-18** — the read paths
the 2026-09-01 ratchet left went through `TenantGuard` in [F-037](#f-037--the-open-items-after-the-temporal-move-closed-refunds-provider-evidence-tenancy-permissions-approvals-indexes).
**Owning specs** identity / organization authorization. Not ET-PLT-005.

> ### Closed on 2026-09-01 — the decision, the writes, and a ratchet
>
> **The decision this entry said had to be made first.** Tenant identity comes from a
> **membership lookup resolved once per request**, not a Keycloak claim. `TenantScopeWebFilter`
> puts a `cache()`d `Mono<TenantScope>` in the Reactor context, so the lookup costs at most one
> call to identity-service per request and nothing at all on a request that never touches a
> tenant-owned resource. A claim was rejected: it needs a realm change in the sibling repository,
> and it stays stale for the token's lifetime, so a revoked membership keeps its access.
>
> **`TenantScope` carries a set, not an id.** That is what retires
> `ActorOrganizationResolver.resolve`'s `organizations.get(0)`: a consultant in two organizations
> reaches both, deterministically, and the filter becomes `organizationId IN (…)`.
>
> **The filter is in the query.** `findByIdAndOrganizationIdIn` on `TicketTierRepository` and
> `EventRepository`; `TenantGuard.locate` takes the scoped lookup *as an argument*, so the only
> way to call it is to have written the filter. A row belonging to someone else does not come
> back, and "not yours" is the same empty `Mono` as "no such id" — which is what keeps the error
> code from being an enumeration oracle.
>
> **The six write paths this entry named are shut**, in the service rather than the resolver, so
> the next caller of `updateTier` inherits the guard instead of having to remember it.
>
> **The refusal is silent and the log is not.** Response: `TIER_UNKNOWN` / `EVENT_UNKNOWN`, no
> details, message rendered from the code alone. On the refusal path only, an existence probe
> decides whether this was a cross-tenant reach and logs `securityIncident=true` — the marker the
> dead `MongoValidationExceptionHandler` was the only code ever to set. Identical bytes on the
> wire, different line in the log.
>
> **A trap found on the way.** `IdentityServiceClient.getUserOrganizations` ends in
> `onErrorResume(e -> Mono.just(new UserOrganizationsResponse(List.of())))` — every failure
> becomes "belongs to no organization". Fine for a list screen; for an authorization decision it
> means a five-second blip in identity-service tells every organizer on the platform that their
> own events do not exist, denying correctly for a reason nothing can report and no client can
> retry. `getUserOrganizationsOrFail` was added and `TenantMemberships` forbids the conflation in
> its contract. A platform administrator survives the outage; nobody else does, and that is
> deliberate.
>
> **Reads, 2026-09-01 — the caller-scoped operations §4 asks for are now built.**
> `myPayoutRequests` (ET-FIN-003) and `myEscrowAccounts` (ET-FIN-001) exist in booking, scoped
> through `CallerScope`. Their `organizationId` argument **selects among the caller's own
> organizations and cannot widen beyond them** — the distinction between it and
> `payoutRequestsByOrganizer(organizerId)`, where the argument decides whose data comes back, is
> the whole of this finding. `CallerScopedReadTest` proves it on a replica set: a two-organization
> member sees both and only both, a selector naming another tenant refuses indistinguishably from
> one naming nothing, and a caller with no memberships is refused rather than shown an empty page
> that would read as "you have no payouts". Mutation-verified — trusting the selector fails it.
> `ImplicitSubjectOperationTest` stops any future pass mapping a `my*` requirement onto a
> `*By<Principal>(id)` field in §4.
>
> ### D-20, 2026-09-01 — the event write path, and what the ratchet does not measure
>
> All seven catalog event mutations now pass through `EventWriteGuard.forWrite(id, permission)`.
> They were never *unchecked* — each already called `checkEventAccess` before writing — but they
> loaded the event by bare id first and compared afterwards, which is the shape that makes
> forgetting invisible. The guard puts the tenant in the query and keeps the permission check.
>
> **The ruling that made this two locks rather than one.** The filter answers "does this belong
> to an organization you are in"; the check answers "may you do this to it", resolved through
> D-10's order. They are not the same question, and this platform ships five organization roles
> in which a MARKETER cannot edit events and a CONTRIBUTOR is view-only, plus event-level roles
> that override the organization role for a single event. Replacing the check with the filter —
> the tempting optimisation, since it removes a cross-service round trip — would give every team
> member owner-level power over every event the organization runs. D-20 records that, and the
> availability cost accepted with it.
>
> **What the conversion revealed about this entry's own ratchet.** Converting all seven mutations
> moved `TenantBoundaryLintTest`'s catalog number **up by one, not down by seven**. The census
> counts `eventRepository.findById` and the mutations called `eventService.findById` — one layer
> above what it measures. The number is a proxy for a boundary, and it measures the layer where
> the query is written rather than the layer where a caller-supplied id arrives.
> `EventWriteGuardLintTest` measures the entry points, which is the half that moves when a
> mutation is converted. Neither number is wrong; one of them was being read as answering a
> question it was never asked.
>
> **What is *not* closed.** Every pre-existing read path. `TenantBoundaryLintTest` freezes the census at
> **catalog 25 · identity 16 · booking 64** unscoped `findById` call sites on tenant-owned
> documents — a different unit from this entry's ~37 entry points, and measured rather than
> estimated. The budgets may only fall, with one exception taken on 2026-09-01: catalog rose
> 24 → 25 because every `TenantGuard` built needs an unscoped probe of its own, and that one
> closed [F-007](#f-007--the-one-public-query-that-took-an-id-did-not-filter-on-visibility).
> A rise is only ever justified by a new `TenantGuard.locate` on the same commit, and
> `GUARDED_PATHS` in the same lint is what holds that claim to account afterwards. A service
> scanning to zero fails as a broken census rather than passing as compliance, and comments
> stopped being counted on that commit.
>
> **Proof.** 30 tests, all tagged `ET-PLT-007`. `TicketTierTenantBoundaryTest` runs the real
> derived query against the Testcontainers replica set, and every refusal case first asserts the
> row *is* reachable unscoped — so it proves the guard, not the fixture. Mutation-verified:
> restoring the bare `findById` fails 5 of its 8 tests, and adding one unscoped call site fails
> the lint.


### What was verified

A census of every `@DgsQuery`, `@DgsMutation` and `@DgsEntityFetcher` across catalog, booking and
identity that takes an id and returns organization-scoped data:

| | |
|---|---|
| `organizationId` (or equivalent) claim in the JWT | **none** — `keycloak-extensions` mints no such claim |
| Tenant context holder | **none** |
| Repository finders shaped `findByIdAndOrganizationId` | **zero, all three services** |
| `@auth` directive tenant dimension | **none** — it compares roles only |
| Entry points taking an id, returning org-scoped data, with no tenant filter and no ownership comparison | **~37** (catalog ~16, identity ~17, booking ~5) |

Tenant identity, where it is established at all, is obtained three different ways: a round trip to
identity-service (`IdentityServiceClient.checkAuthorization`), `ActorOrganizationResolver`, or read
off the document already loaded. There are four distinct correct patterns in use and no single one
is authoritative.

### The sharpest holes are writes

The six mutations in `TicketTierMutationResolver` and `EventAccessibilityMutationResolver.updateEventAccessibility`
carry `@PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")` and then call straight through to
`tierService.updateTier(tierId, …)` / `deleteTier(tierId)`. Those implementations do a bare
`findById` with no organization comparison.

**Any account holding the `ORGANIZER` realm role can reprice or delete any other organization's
ticket tiers by id.** Read exposure is worse in breadth — KYC verification documents, ownership
transfers, invitation tokens, event access grants — but a write is what makes this urgent.

### Three things that make it look solved when it is not

1. **`TenantAccessGuard`, `OrganizationSecurityService.isMemberOfOrganization` and
   `TenantValidationService` all exist, are correct, fail closed, and are called by nothing.**
   `shared-library`'s own `package-info.java` says so for the third: *"consumed by nothing in
   production."* This is the orphan pattern, and here it is load-bearing — the components read as
   evidence the boundary is enforced.
2. **Four `@PreAuthorize` expressions name beans that do not exist** — `@payoutSecurityService`
   (three sites) and `@bankAccountSecurityService`. *Inferred, not executed:* SpEL resolution
   failure throws, so these fail closed, and the effect is organizers locked out of their own
   payouts and bank accounts rather than a leak. Dead either way.
3. **`ActorOrganizationResolver.resolve` returns `organizations.get(0)`** when a user belongs to
   more than one organization, with a warning log. Its own javadoc records this as known-wrong.

### What was done, and what deliberately was not

`TenantBoundary.refuse(unknownCode, what)` is built and tested: once a check decides to refuse, the
answer is the same `*_UNKNOWN` code an unissued id produces — same code, same message, no details —
so a caller cannot distinguish "not yours" from "does not exist". It rejects `ACTOR_NOT_PERMITTED`
at the call site, since that reads as the more helpful answer and reopens the oracle in one line.

**The checks themselves were not added.** Closing this means deciding where tenant identity comes
from — a Keycloak claim, a membership lookup per request, or a resolved request context — and that
is an identity/organization design decision no spec has yet stated. Retrofitting 37 endpoints under
an error-contract task would mean inventing authorization semantics and burying them where nobody
would look for them.

### Suggested order when this is picked up

1. Decide the source of tenant identity. A claim is cheapest per request and requires a Keycloak
   mapper plus a story for the multi-organization user that `ActorOrganizationResolver` currently
   guesses at.
2. Add tenant-scoped finders so the filter is in the query rather than in a comparison after the
   fact. A `findByIdAndOrganizationId` returning empty is naturally indistinguishable from
   not-found; a load-then-compare has to remember to call `TenantBoundary`.
3. Convert the write paths first — the tier mutations and `updateEventAccessibility`.
4. Add a lint asserting no id-taking resolver returning org-scoped data lacks a tenant filter, so
   the count only goes down.

---

## F-002 · The index registry is not the only index authority

**Found** 2026-08-19, while implementing [`ET-PLT-005`](tasks/ET-PLT-005.md) BE-6.
**Severity** data integrity — two live bugs traced to it, both on money or admission paths.
**Status** **closed 2026-09-18** — every index is in the registry and `auto-index-creation` is off
([F-037](#f-037--the-open-items-after-the-temporal-move-closed-refunds-provider-evidence-tenancy-permissions-approvals-indexes)).

### What was verified

All three services set `spring.data.mongodb.auto-index-creation: true` in `application.yml`, with
no profile override anywhere. Every `@Indexed` and `@CompoundIndex` annotation on a model
therefore creates a real index at startup — outside the ET-PLT-002 §4 registry, and outside the
`*IndexRegistryTest` that asserts declarations match §4.

| service | model annotations | §4 declarations |
|---|---|---|
| booking | **141** | 26 |
| identity | **52** | 18 |
| catalog | **41** | 7 |

So roughly **183 of 234 indexes are created by an authority no test checks.** The registry tests
pass, and they are checking the smaller half.

*One contradiction worth noting:* `catalog-service/.../ReferenceDataSeeder.java:67` states
"catalog-service does not enable auto-index-creation". Its own `application.yml:52` sets it to
`true`. The comment is wrong, and it is the kind of wrong that stops the next person checking.

### Why it produced bugs rather than just duplication

An annotation is where a developer writes an index without consulting §4, and `unique + sparse`
reads as the obvious way to say "unique when present". It is not. Sparse skips a document only
where the field is **absent**; Spring Data writes a null field as `field: null`, which is present,
so a sparse unique index puts every null under the single key `null` and the second such document
collides.

Two instances were found in this pass, both fixed to `unique` + `partial $type`:

- **`booking_checkins.scanId`** — online scans carry no scan id, so the first scan at a gate would
  take `null` and **every subsequent online check-in at that event would be rejected as a
  duplicate**.
- **`booking_payout_requests.idempotencyKey`** — the first keyless payout takes `null`; every
  later one collides, so unrelated organizations block each other from being paid.

A third was found immediately after, while adding the lint below: **`identity_users.username`**.
Better Auth creates the user document on first OIDC login *without* a username — it arrives later
from the Keycloak sync — so nulls there are not an edge case but the normal state of every account
between signup and sync. The second concurrent signup would have been rejected as a duplicate.

These are the **fifth, sixth and seventh** instances. Four earlier ones (`identity_users.idx_email`,
`identity_users.idx_phoneNumber`, `booking_payment_attempts.idx_providerReference`,
`booking_checkins.scanId`'s sibling) were fixed when only one of them had actually fired. The
pattern recurs because nothing prevents it: `IndexSpec.sparse()` now carries a corrected javadoc,
but an annotation never goes near `IndexSpec`.

### Suggested fix

1. **Turn `auto-index-creation` off** and move every surviving annotation index into the registry.
   This is the change that makes the registry true rather than aspirational. It needs care: an
   index the annotations create today and the registry does not declare would disappear, so the
   move has to be a census, not a switch flip.
2. ~~Add a lint~~ **Done** — `IndexAuthorityLintTest` ratchets the annotation count per service
   (booking 150, identity 56, catalog 42) so it can only fall, and fails if annotations are removed
   without lowering the budget. A lint was worth more than the migration here: the migration is
   one-time, the pressure to add an annotation is continuous.
3. ~~Ban `unique + sparse`~~ **Done** — banned outright rather than ratcheted, because all seven
   occurrences were wrong and there was no legitimate baseline to preserve. Mutation-verified.

---

## F-003 · The contract advertises operations nothing implements, in both directions

**Found** 2026-08-19, while implementing [`ET-PLT-004`](tasks/ET-PLT-004.md) BE-5 and BE-8.
**Severity** contract integrity — each instance is a screen or a client call that fails at runtime.
**Status** **closed 2026-09-01** — the count is zero and a lint holds it there.

### Schema → no resolver

A census matching every `Mutation` field against the service's Java resolvers:

| service | mutations declared | with no resolver method |
|---|---|---|
| catalog | 41 | **0** |
| identity | 117 | **~25** |
| booking | 80 | **4** |

Identity's include `updateMyProfile`, `setupTwoFactor`, `verifyTwoFactor`, `disableTwoFactor`,
`socialAuth`, `linkSocialAccount`, `unlinkSocialAccount`, `requestAccountDeletion`,
`cancelAccountDeletion`, `suspendUser`, `unsuspendUser`. Booking's are `uploadScans`,
`createPlatformAccount`, `creditPlatformAccount`, `debitPlatformAccount`.

These compose, publish, and generate TypeScript types. A client has every reason to believe they
work.

### Client → no schema field

The reverse, found by validating every `gql` document against the composed supergraph — a check
that did not previously exist, because **`graphql-codegen` exits 0 on a document selecting a field
the schema does not have.** It emits types for whatever it can resolve and moves on, so the
generated types agree with the document and both disagree with the server.

Eleven documents fail, on seven distinct causes: `verifyOrganizationDocuments`,
`bulkApproveDocuments`, `bulkRejectDocuments`, `organizationStatistics`, `myPermissions`,
`BusinessAddress.street`, and the whole `TicketOffsetPage` shape used by org-admin check-in.

### Also verified while measuring this

**`createUser` declared `UserMutationResponse!` and its resolver returned `Mono<User>`.** A client
selecting `success` on it received nothing. That one is fixed by BE-5, which removed the wrapper —
but it is worth recording that a resolver and its schema field had disagreed about the return type
without anything failing.

### What was done

`documentValidity.test.ts` validates every `gql` document against the composed supergraph and
holds the eleven known-broken ones in a frozen `KNOWN_BROKEN` list. A twelfth fails the build; a
fixed one also fails the build, so the list cannot rot into permission.

**The schema-side gap is not ratcheted yet.** Removing ~29 advertised mutations is a product
decision — some are unbuilt features others are waiting on — and inventing implementations for
them under a federation-contract task would be worse than leaving them visible. The census command
is in this entry so the next person can re-run it rather than rediscover it.

---

### Closed on 2026-09-01 — twenty-one removed, five built

The schema-side half of this finding was **26 root fields declared with no resolver**: catalog 0,
identity 21, booking 5. They composed into the supergraph, generated client types, and appeared in
`FRONTEND_GRAPHQL_CONTRACT.md` as operations a frontend could call. Calling one returned an error.

**The dangerous subset was booking's five.** `createPlatformAccount`, `creditPlatformAccount`,
`debitPlatformAccount`, `accountBalance` and `trialBalance` are ledger operations, and they
carried neither `@auth` nor `@PreAuthorize` — they sat in the contract's *endpoint-floor-only*
bucket. Harmless while nothing answered them, and unguarded money movement the moment somebody
bound a resolver, because nothing in the schema shows a guard is missing until you look for it.
Identity's 21 had the same shape on `suspendUser`, `unsuspendUser` and `disableTwoFactor`.

**Twenty-one were removed.** No spec named them, and **no frontend document called any of the
26** — so ET-PLT-010's deprecation window, which exists to stop working behaviour being withdrawn
from clients, had nothing to protect: there was no behaviour and there were no callers. Eight
now-unreferenced types went with them. The removals were the 2FA cluster, the social-auth cluster,
the superseded permission-check surface, four statistics queries ET-ADM-004 does not name, and the
erasure pair ET-PLT-008 will build properly.

**Five were built**, because a spec names each:

| Operation | Spec | Note |
|---|---|---|
| `trialBalance(asOf)` | ET-FIN-001, ET-FIN-005 | POSTED entries only; every account appears; net runs in each account's normal direction |
| `myEffectivePermissions(organizationId, eventId)` | ET-ORG-003 | Through `PermissionResolutionService` — D-10's five-step order, not the JWT's realm roles |
| `updateMyProfile(input)` | ET-IDN-002 | Replaces `updateProfile(input: JSON!)`, an unvalidated map on a profile write |
| `suspendUser(id, reason)` | ET-IDN-002 | |
| `unsuspendUser(id)` | added to ET-IDN-002 §4 | A suspension with no documented way back is a lock-out |

**The `updateProfile` case is worth keeping.** DGS binds by method name, so a resolver called
`updateProfile` answered a *second* SDL field of that name while §4's `updateMyProfile` sat
declared and unbound beside it. The schema advertised the operation the specs asked for and served
a different one next to it — and the one it served took `JSON!`, an untyped map on a mutation that
writes a user's own record.

**Held at zero** by `FrontendContractLintTest.nothingIsAdvertisedWithoutBeingImplemented`, which
parses both sides. The cheapest way to add an operation is to write the SDL line and mean to come
back to it.

**Evidence.** `TrialBalanceTest` (5 cases, replica set, mutation-verified twice: counting DRAFT
and REVERSED entries fails four cases; a uniform `debit - credit` net fails the direction case),
`AccountSuspensionTest` (4 cases, replica set), `UnimplementedOperationLintTest` (3 shape checks
with comments stripped).

---

## F-004 · Half the graph the specs name does not exist, and a fifth exists under another name

**Found** 2026-08-31, during the [reconciliation pass](RECONCILIATION.md).
**Severity** contract integrity — it decides whether a slice builds, renames, or leaves alone.
**Status** **ruled 2026-09-01** — see [`ROADMAP.md` D-19](ROADMAP.md). Waves 2–6 are unblocked;
the work the ruling creates is below.

> ### The ruling, and what it turns 61 contradictions into
>
> The two halves were separate questions and were answered separately.
>
> **Pagination — the twins collapse.** 60 `*OffsetPagination`/`*CursorPagination` pairs become one
> field each: catalog 34, booking 19, identity 7. A further 17 lone variants lose the suffix. The
> subgraph surface goes from **542 root fields to 482**, and no client has to choose between two
> ways of asking the same question.
>
> **The survivor is not a fresh decision — §4 already names it, per operation.** The corpus splits
> cleanly and along the right line: `*Connection!` with `(first, after)` for the seven public and
> personal feeds (`events`, `searchEvents`, `eventsByCategory`, `eventsByCity`, `myTickets`,
> `myNotifications`, `resaleListings`), `*Page!` with `(page)` for the thirty-three admin and
> organizer tables (`auditLogs`, `deadLetters`, `recoveryQueue`, `journalEntries`, every approval
> queue). Infinite scroll gets cursors, tables that need page numbers and totals get offsets.
> Where §4 declares neither, the operation is not paginated and both variants go.
>
> **Naming — the schema wins.** `createPayoutRequest`, `deleteTicketTier`, `approveRefundRequest`,
> `submitOrganizationForReview` stand as shipped; §4 is amended to them. `<verb><Noun>Request` is a
> consistently applied convention, and renaming 36 working operations to satisfy a document would
> be precedence exercised for its own sake.
>
> ### What the amendment work actually is, and a caution about this table
>
> | | Count | Kind of work |
> |---|---|---|
> | Twin pairs to collapse | **60** | Mechanical — delete one field, keep the shape §4 names |
> | Lone variants to de-suffix | **17** | Mechanical |
> | §4 names with one obvious shipped counterpart | **17** | Spec edit, low risk |
> | §4 names needing someone to decide which operation they mean | **30** | **Not mechanical** |
>
> **The last row is the honest part.** The pairings were found by matching significant word stems,
> which produces leads, not answers. Several first candidates are plainly wrong —
> `requestPayout → approvePayoutRequest` (it is `createPayoutRequest`),
> `requestRefund → approveRefundRequest` (it is `createUserRefundRequest`),
> `myOrganization → hasOrganizationPermission` (it is `myOwnedOrganization`). Others are ambiguous
> in a way no heuristic settles: `chargebacks` has four filtered variants and none is the unfiltered
> list; `myPermissions` has `allPermissions`, `currentUserPermissions`, `myEffectivePermissions` and
> `permissions`, of which two have no resolver at all.
>
> Treat that column as a worklist with candidates attached, never as a rename map. Applying it
> unread would silently repoint specs at operations that do something else — which is the same
> class of mistake as the census that counted a file's existence as proof it ran.
**Owning specs** ET-PLT-004 (federation contract). Not any one capability spec.

### What was verified

Every `graphql.queries` and `graphql.mutations` entry across all 41 `spec.yaml` files, matched
against the three subgraph SDLs and against the `@DgsQuery`/`@DgsMutation` bindings behind them.
The resolver census reports **zero orphan resolvers** in all three subgraphs, which is what makes
the reverse direction trustworthy.

| | Operations | Share |
|---|---|---|
| In the SDL with a resolver bound | 87 | 30% |
| `contradicted` — in the SDL with no resolver, or built under a different name | 61 | 21% |
| `absent` | 143 | 49% |
| **§4 operations across the corpus** | **291** | |

### The 61 are one decision, not 61

**137 of the 542 root fields across the three subgraphs (25%) are `*OffsetPagination` /
`*CursorPagination` twins** — catalog 76 of 155, booking 47, identity 14. Where a spec's §4 names
one paginated operation, the schema carries two to four suffixed variants.

| §4 name | What the schema carries |
|---|---|
| `provinces` (ET-CAT-003) | `provincesOffsetPagination`, `provincesCursorPagination`, `provincesByCountry{Offset,Cursor}Pagination` |
| `requestPayout`, `approvePayout` (ET-FIN-003) | `createPayoutRequest`, `approvePayoutRequest` |
| `myTickets` (ET-TKT-002) | `ticketsByBuyer{Offset,Cursor}Pagination`, `searchTickets{Offset,Cursor}Pagination` |
| `deleteTier` (ET-CAT-002) | `deleteTicketTier` |

Two conventions, both predating the corpus and both applied everywhere: pagination strategy
encoded in the field name, and `<verb><Noun>Request` for lifecycle mutations.

### Why this is recorded here rather than in a spec

It cannot be settled inside any one spec. Either the specs win
([README §Precedence](README.md#precedence)) and ~137 schema fields are renamed under
ET-PLT-010's deprecation window — breaking every client document — or §4 is amended across 41
specs to the shipped convention. Both are defensible; neither is a per-slice call.

Left undecided, each of the 61 is a slice that will rebuild something that already works, or
leave in place something its own spec contradicts. That is the failure
[`IMPLEMENTATION_PLAN.md` §3](IMPLEMENTATION_PLAN.md) says R0 exists to prevent.

### Smaller, and exactly bounded: the resolver-less fields

F-003's schema-side census, re-measured: **26** fields are declared with no resolver — catalog
**0**, booking **5** (`accountBalance`, `trialBalance`, `createPlatformAccount`,
`creditPlatformAccount`, `debitPlatformAccount`), identity **21**. `uploadScans`, named in F-003,
now resolves.

Ten of identity's 21 are the permission surface — `myEffectivePermissions`, `allPermissions`,
`hasOrganizationPermission`, `hasEventPermission`, `myOrganizationRole`, `myEventRole` and
others. The platform advertises a permission API that returns nothing, and per
[F-001](#f-001--organization-scoped-data-has-no-tenant-boundary) there is no tenant boundary
behind it either.

---

## F-005 · Two registries the corpus calls closed have one row each outside them

**Found** 2026-08-31, during the [reconciliation pass](RECONCILIATION.md).
**Severity** low individually; the missing check is the point.
**Status** **closed 2026-09-18** by [F-035](#f-035--the-collection-validators-f-031-flagged-and-what-a-parity-lint-found-beyond-them) —
see that finding for detail: the collections row was already resolved (`identity_role_permission_changes`
is a row of both ET-PLT-002 §4 and ET-PLT-013 §4, and `CollectionRegistryLintTest` already reads every
module); the error-code row is resolved by adding `DeadLetter.UNSUPPORTED_SCHEMA_VERSION` rather than an
`ErrorCode`, because ET-PLT-010 §4 was rewritten since this finding to say explicitly that no `ErrorCode`
should exist for it.

Both registries are very nearly exact, which is why the exceptions are worth recording rather
than quietly fixing — a closed registry with one silent escape hatch is not closed.

| Registry | Authority | Rows | Divergence |
|---|---|---|---|
| Error codes | `shared-library/.../error/ErrorCode.java` | 93 | ~~`UNSUPPORTED_SCHEMA_VERSION` is declared by ET-PLT-010 §4 and **absent from the enum**.~~ ET-PLT-010 §4 now says explicitly this is not an `ErrorCode`; it is `DeadLetter.UNSUPPORTED_SCHEMA_VERSION` (F-035) |
| Collections | ET-PLT-002 §4 | 66 | ~~The corpus declares **67**. `identity_role_permission_changes` is named by ET-PLT-013 §4 alone and **is not a registry row**.~~ Now a row of both §4 tables (F-035 verified, did not add) |

`ErrorCodeRegistryTest` parses ET-PLT-005 §4 and asserts the enum equals it in both directions —
so it will not catch a code a *different* spec declares. `*IndexRegistryTest` compares the
registry against the code, never against the rest of the corpus. In both cases the check exists,
is good, and is scoped to one document, so a name introduced in a second document passes.

This is [F-002](#f-002--the-index-registry-is-not-the-only-index-authority) one level up: there,
annotations create indexes outside the registry; here, a spec names a collection outside it. The
fix is the same shape — a lint that reads the whole corpus, not one file.

---

## F-006 · The frontend contract claims to be generated, and nothing generates it

**Found** 2026-09-01, while collapsing the pagination twins under [`ROADMAP.md` D-19](ROADMAP.md).
**Severity** contract integrity — a client reads it and calls an operation the server does not have.
**Status** **closed 2026-09-01** — the generator exists and the build fails when the document drifts. Measured, not estimated.

### What was verified

`docs/FRONTEND_GRAPHQL_CONTRACT.md` opens with:

> *Generated from the subgraph schemas. Do not edit by hand — it is rewritten whenever a
> `.graphqls` file changes.*

There is no generator. `grep -rl FRONTEND_GRAPHQL_CONTRACT` over every `.mjs`, `.js`, `.ts`,
`.sh`, `.json` and `.java` in the repository returns nothing — no script, no npm task, no Maven
plugin, no CI step. The file has only ever been written by hand, and the sentence forbidding
hand-editing is the reason nobody has.

### The drift it hides

Every root field on each subgraph, set-compared against the operations the document lists:

| Subgraph | Kind | In schema | In doc | Doc lists, does not exist | Exists, doc omits |
|---|---|---|---|---|---|
| catalog | Query | 110 | 110 | 0 | 0 |
| catalog | Mutation | 45 | 45 | 0 | 0 |
| booking | Query | 125 | 125 | 0 | 0 |
| booking | Mutation | 80 | 80 | 0 | 0 |
| **identity** | Query | 62 | 64 | **11** | **9** |
| **identity** | Mutation | 95 | 97 | **8** | **6** |

Catalog and booking are exact only because the D-19 collapse pass corrected booking's rows by
hand on 2026-09-01. Identity is what the document looks like when nobody has touched it: **19
operations advertised that no subgraph implements, and 15 implemented operations it never
mentions** — including all seven this pass collapsed, which were absent under both their old and
new names.

### Why this is worth a finding rather than a fix

Correcting the 34 identity rows by hand would restore the same condition that produced them: a
document that is true on the day someone edits it and drifts from the next schema change onward.
It also cannot be done honestly without inventing the required role for each operation, which is
the column a client actually depends on.

The fix is the generator the header already promises. It has one input — the three
`schema.graphqls` files — and the role column comes from the `@auth` and `@tag` directives already
on every field, so it is derivable rather than authored. Until it exists, the header's claim
should be removed: a document that says it is generated is trusted differently from one that
admits it is maintained by hand.

**This is the same shape as [F-002](#f-002--the-index-registry-is-not-the-only-index-authority)
and [F-005](#f-005--two-registries-the-corpus-calls-closed-have-one-row-each-outside-them)** —
an authority declared, and nothing enforcing it. The pattern is now three for three, which
suggests checking the claim before trusting any "generated, do not edit" header in this
repository.

### Closed on 2026-09-01 — the generator, and what it found on the way

`FrontendContractLintTest` is the generator. It parses the `Query` and `Mutation` blocks of the
three subgraph SDLs, takes the role column from each field's `@auth(requires: …)` directive — the
one that `AuthDirective` actually enforces at runtime — and renders the document. Ordinary runs
re-derive it and compare; `-Dcontract.write=true` rewrites it. There is one implementation of the
parse and it is the same one that checks, so the two cannot disagree.

**The drift was the smaller half of the problem.** The committed document listed `status`, `type`
and `verified` as identity *queries*, repeatedly — once per object type carrying a field of that
name. Whatever produced it scanned field declarations without restricting them to the root
blocks. Its headline of **557 operations** was wrong by 51: the real figure is **506**, and the
"PUBLIC by omission" bucket falls from 441 to 372. The `ADMIN` count of 83 was right, which is
how a number like 557 survives — the part anyone would spot-check was correct.

Two guards, because the count alone is not enough:

- The generator asserts it parsed **more than 300** operations. One that produces nothing
  regenerates a blank document and then agrees with it forever.
- A second test asserts `status`, `type`, `verified`, `id` and `createdAt` never appear as
  operation names. They are common object fields, so this is the specific failure a future parser
  is most likely to reintroduce, and a count-only check would not notice it.

**Verified against an independent parse**, which disagreed by 11 and was wrong on all 11: a
line-oriented scan counted five words out of `# SECURITY:` and `# Note:` comment prose as
operations. That is the same class of mistake as the original, arrived at independently, which is
the argument for the parse living in one place rather than being re-derived per reader.

---

## F-007 · The one public query that took an id did not filter on visibility

**Found** 2026-09-01, while converting the read paths [F-001](#f-001--organization-scoped-data-has-no-tenant-boundary)
ratcheted. **Severity** OWASP A01 — Broken Access Control (CWE-639). **Corrected 2026-09-01, same day**:
first written as *unauthenticated*; it is not. See "What the reach actually was" below.
**Status** **closed 2026-09-01**. **Owning spec** [`ET-PLT-007`](tasks/ET-PLT-007.md).

### What was verified

Catalog's schema groups its public queries under a header that says so, and every list query in
that group ends the same way in the repository:

```
findByPublishedTrueAndIsActiveTrueOrderByIdAsc
findByCategoryIdAndPublishedTrueAndIsActiveTrue
findByFeaturedTrueAndPublishedTrueAndIsActiveTrue
findByEventDateTimeBetweenAndPublishedTrueAndIsActiveTrue          … and eight more
```

`event(id: ID!): Event` sits in that same block, annotated `# Get single event by ID - PUBLIC`.
It carried no `@auth` directive and its resolver no `@PreAuthorize`, and it resolved to
`EventServiceImpl.findById` — `eventRepository.findById(id)`, no filter of any kind.

So the one query in the group that takes a caller-supplied key was the one that applied no
visibility rule. Any caller holding an id read:

| | what it exposes |
|---|---|
| a `DRAFT` event | a competitor's unannounced line-up, capacity and tier pricing |
| a `REJECTED` event | `rejectionReason` — which the schema `@tag`s organizer/admin precisely because it is not public |
| a soft-deleted event | `deletedAt`, `deletedBy`, `deletionReason` |

### What the reach actually was

This entry first said "no account at all". That was wrong, and the correction is worth stating
plainly because it changes the severity class. **All three subgraphs carry
`.pathMatchers("/graphql/**").authenticated()`** — catalog's says so in a comment right above the
line: *"require authentication so JWT is parsed and available to resolvers"*. No GraphQL operation
on any subgraph is reachable without a token, the schema's PUBLIC annotations notwithstanding.

The reach was **any authenticated caller**: the lowest-privilege `CUSTOMER` token, which anyone
obtains by registering a phone number. That is horizontal privilege escalation with a self-service
entry price, not anonymous disclosure — a materially smaller blast radius and still the thing
CWE-639 names. A customer account is not a credential the platform issues selectively.

Event ids are Mongo `ObjectId`s and not guessable at scale, so this is disclosure to a signed-in
caller who *has* an id — a shared draft preview link, a stale bookmark, a URL in an email thread,
or a subsequently-cancelled event — rather than a bulk enumeration.

**The mistake worth learning from**: the SDL says `# Get single event by ID - PUBLIC` and groups
the field under a header reading `EVENT DISCOVERY QUERIES (PUBLIC - For all consumers)`. Both are
schema documentation and neither is enforcement; the enforcement is one line in a
`SecurityConfig` in a different language, and it contradicts them. Reading the schema's own word
for the audience is what produced the overstatement — the same class of error as trusting the
"generated" header in [F-006](#f-006--the-frontend-contract-claims-to-be-generated-and-nothing-generates-it).

**`@auth` is enforced.** This was checked rather than assumed: `AuthDirective` is a real
`SchemaDirectiveWiring` registered by `AuthDirectiveAutoConfiguration`, the directive is declared
in `shared-library`'s `auth.graphqls`, and all three services load `classpath*:graphql/**/*.graphql*`
so the jar's declaration is on the schema. The 103 `@auth(requires: …)` usages in catalog do
what they say. `event(id)` simply had none.

### The fix

`EventService.findVisibleById`, wired into both `event(id)` and the `Event` federation entity
fetcher — `_entities` resolves a caller-supplied key exactly as a root query does, so closing one
and not the other would have left the same read open through the router.

It tries the public finder first and consults tenancy only on a miss. That ordering is not an
optimisation detail: a published event, the overwhelmingly common case, is answered by one
indexed lookup without resolving tenancy at all, so the public path gains no membership call.

Three decisions inside it are worth keeping:

- **Empty, not a refusal.** `event(id)` is nullable and has always answered `null` for an unknown
  id. Refusing would reintroduce the distinction `TenantBoundary` exists to erase.
- **`TenantGuard` all the same.** It still classifies the miss and still logs a confirmed
  cross-tenant reach as `securityIncident=true`, which a bare `switchIfEmpty` could not.
- **An anonymous miss is not an incident.** The guard is skipped when the scope has no subject.
  The first draft did not do this, and the test run showed every anonymous miss arriving in the
  incident log — somebody opening a stale link to an unpublished event, labelled a security
  incident. An incident log that fires on ordinary public traffic is one that gets muted, and it
  takes the real reaches with it. An *authenticated* caller with no memberships still goes
  through the guard.

### Evidence

`EventVisibilityTest` — 11 cases on a real replica set, because the fix is a derived query over a
Lombok-prefixed boolean (`isActive`) and a mock would assert the mock. Every negative case first
asserts the row is present and reachable unscoped. Mutation-verified twice: restoring `findById`
fails 6 cases; removing the anonymous-subject filter fails exactly the incident-log case.

### What this says about the other 104

F-001's ratchet counts unscoped `findById` calls on tenant-owned documents. It did not and could
not have found this one, because the count is a proxy for a boundary and says nothing about
whether the boundary was ever *supposed* to be tenancy. Here the missing rule was
publication state, and the tell was not the `findById` — it was that thirteen sibling queries
filtered and one did not.

The census also stopped counting comments on this commit. Documenting a fix means writing the
vulnerable call in prose, and a lint that charges a service for explaining itself is one that
teaches people not to explain.

---

## F-008 · The sign-in surface sits behind the sign-in requirement

**Found** 2026-09-01, while writing [F-006](#f-006--the-frontend-contract-claims-to-be-generated-and-nothing-generates-it)'s
generator — the role column forced the question of what "PUBLIC" means on this platform.
**Severity** availability / dead surface, not a vulnerability. **Status** open, needs a ruling.
**Owning spec** [`ET-IDN-001`](tasks/ET-IDN-001.md) with [`ET-PLT-007`](tasks/ET-PLT-007.md).

### What was verified

All three subgraphs carry `.pathMatchers("/graphql/**").authenticated()`. Catalog's is annotated
*"require authentication so JWT is parsed and available to resolvers"*, which is a sound reason —
but it makes every GraphQL operation on every subgraph require a token, and the schema's `PUBLIC`
comments describe an audience the server does not implement.

Identity serves these over GraphQL, each with a **working resolver**:

| Operation | Resolver |
|---|---|
| `login`, `register`, `refreshToken`, `validateToken` | `AuthenticationMutationResolver` |
| `requestPhoneOtp`, `verifyPhoneOtp` | `PhoneOtpMutationResolver` |
| `resetPassword` | `UserMutationResolver` |

A signed-out caller cannot reach any of them. There is no `/api/auth/**` controller either — the
`SecurityConfig` permits that path and nothing serves it.

### The likely explanation, and why it still needs deciding

Per `CLAUDE.md`, the real login path is Keycloak's own browser flow with the custom Phone OTP
authenticator; Keycloak issues the tokens, and OTP reaches identity over
`/api/internal/otp/*` REST, which is separately secured. On that reading these seven are
**vestigial** — a GraphQL auth surface from before Keycloak owned the flow, still compiling, still
composed into the supergraph, still in every generated client type.

That is a ruling, not a repair: either they are removed, or the endpoint gains a per-operation
exception and they become real. Leaving them is the expensive option — they appear in
`FRONTEND_GRAPHQL_CONTRACT.md`, in codegen output, and in any reasonable reading of the schema as
the way a client signs a user in.

### Why the same scan produced no vulnerability

Worth stating, because the list of unguarded-looking operations reads alarmingly. **76 of the 506
operations carry neither `@auth` nor `@PreAuthorize`**, among them `suspendUser`,
`unsuspendUser`, `disableTwoFactor`, `platformStatistics` and `usersCountByRole`. Every one of
those was checked: **none has a resolver**. They are part of identity's 21 declared-but-unbound
fields under [F-003](#f-003--the-contract-advertises-operations-nothing-implements-in-both-directions),
so they answer nothing today — and would be unguarded admin operations the moment somebody bound
one. The rest of the bucket is catalog's genuine discovery surface plus `me`, which is correctly
scoped to the caller.

`FrontendContractLintTest.graphQlEndpointRequiresAToken` now pins the floor those 76 stand on.
Relaxing any service's rule to `permitAll` — plausible while chasing an anonymous-browsing bug,
since the schema does call those queries PUBLIC — would publish all of them at once, and before
this test nothing in the build would have noticed.

---

## F-009 · The reservation TTL raced the sweep that returns the seats

**Found** 2026-09-01, verifying [`ET-TKT-001`](tasks/ET-TKT-001.md) R4.
**Severity** correctness — silent, permanent inventory loss. Not a security finding.
**Status** **closed 2026-09-01**. **Owning spec** ET-TKT-001.

### What was verified

`booking_reservations` carried its TTL index as `expireAfter(Duration.ZERO)`. On a date field
that tells MongoDB to delete the document **the instant `expiresAt` passes**.

The expiry sweep is what returns the seats: every 30 seconds it finds holds past `expiresAt`,
moves them to `EXPIRED`, and credits the tier's counters back. A document the TTL monitor removes
first is never claimed — so the inventory that hold was carrying is never returned.

ET-TKT-001 R4 states the ordering explicitly, and §4 states the number:

> The TTL index on `expiresAt` is configured to fire **after** the sweep window —
> `expireAfterSeconds` corresponds to `ttl + booking.reservation.ttl-grace` (PT1H)

`booking.reservation.ttl-grace` did not exist in `application.yml` at all.

### Why it would not have been found by watching

It is a race, not a certainty: MongoDB's TTL monitor wakes about once a minute and the sweep runs
every thirty seconds, so most expired holds are swept first. The ones that are not lose their
seats **silently** — no error, no failed operation, nothing in a log. A tier's available count
drifts down by a few seats a day and eventually reports sold out with seats unsold. The natural
first hypothesis for that symptom is an oversell bug, which is the opposite of what is happening.

### The fix

`expireAfter(RESERVATION_TTL.plus(RESERVATION_TTL_GRACE))` — PT1H10M, with `ttl-grace: PT1H` added
to the configuration and the reason recorded there. The TTL becomes a floor sweeper rather than a
competitor: by the time it runs, the sweep released the row an hour earlier and TTL removes a
document already terminal.

### Evidence

`ReservationTtlOrderingTest` — three cases on a replica set. R4 asks for an **inverted-config
test**, so the check is written as a rule applied to two specs: a zero expiry must be rejected and
the registry's own spec must be accepted. Without both directions the rule could pass while
comparing nothing. A third case asserts the live index on the collection carries the expiry, since
MongoDB will not silently alter an existing TTL in place and a registry is a list of intentions.

---

## F-010 · The one reservation read that did not check who was asking

**Found** 2026-09-01, verifying ET-TKT-001 against OWASP A01.
**Severity** OWASP A01 — Broken Access Control (CWE-639), any authenticated caller.
**Status** **closed 2026-09-01**. **Owning spec** ET-TKT-001 with [`ET-PLT-007`](tasks/ET-PLT-007.md).

### What was verified

`reservation(id: ID!)` carried `@PreAuthorize("isAuthenticated()")` and returned
`reservationService.findById(id)` with no comparison against the caller.

A reservation carries the buyer's `userId`, the event, the tiers and quantities they chose, any
promo code, and the exact money — `unitPrice`, `subtotal`, `discountAmount`, `totalAmount`. All of
it was readable by any signed-in account holding an id, and on this platform a signed-in account
costs a phone number to obtain.

**Both neighbouring operations already scoped**, which is what makes this the same shape as
[F-007](#f-007--the-one-public-query-that-took-an-id-did-not-filter-on-visibility):

| | |
|---|---|
| `cancelReservation` | matches the caller against `userId` — its own comment explains that otherwise a buyer could release somebody else's hold seconds before an on-sale |
| `myActiveReservations` | compares the argument to the token's subject in its `@PreAuthorize` |
| `reservation(id)` | took the id and answered |

Three operations over one collection, two of which filter. **That is now twice — catalog's
`event(id)` and booking's `reservation(id)` — that the defect was found by comparing an operation
against its siblings rather than against its spec.** It is the most productive reading order
found so far: where a group of operations shares a collection, the one that does not filter is
worth opening first.

### The fix, and why it is not `TenantScope`

Subject-scoped. A reservation belongs to a **person**, not an organization, so the tenancy
mechanism is the wrong instrument — a customer belongs to no organization and every one of them
would be refused. The comparison is against the token's subject, with `ROLE_ADMIN`,
`ROLE_FINANCE` and `ROLE_SUPER_ADMIN` reading across buyers because refunds, chargebacks and
disputes all begin with somebody looking at a reservation that is not theirs.

`ROLE_ORGANIZER` is deliberately **not** in that set, and a test pins it: an organizer reads the
reservations for their own event through `reservationsByEvent`, which checks the event. This path
knows nothing about events, so admitting the role would open every buyer's reservation to every
organizer.

It answers empty rather than refusing — the field is nullable and has always answered `null` for
an unknown id, so a foreign reservation and an invented one stay indistinguishable.

### Evidence

`ReservationVisibilityTest` (5 cases, replica set, each negative first proving the row exists
unscoped) and `ReservationScopeLintTest` (4 shape checks, comments stripped — but **not** string
literals, since the role names it checks are string literals). Mutation-verified: removing the
filter fails the lint naming exactly what leaks.

---

## F-011 · Two more by-id reads that answer without checking, one of them holding half a credential

**Found** 2026-09-01, applying to ET-ORG-002 the reading order that produced F-007 and F-010.
**Severity** OWASP A01 — Broken Access Control (CWE-639), any authenticated caller.
**Status** **closed 2026-09-01.** **Owning spec** [`ET-ORG-002`](tasks/ET-ORG-002.md).

### The reading order, now three for three

Where several operations read one collection, open the one that does **not** filter. It has found
a defect every time it has been applied:

| | |
|---|---|
| catalog `event(id)` | [F-007](#f-007--the-one-public-query-that-took-an-id-did-not-filter-on-visibility) — a dozen sibling list queries all ended `PublishedTrueAndIsActiveTrue`; the by-id read did not |
| booking `reservation(id)` | [F-010](#f-010--the-one-reservation-read-that-did-not-check-who-was-asking) — `cancelReservation` and `myActiveReservations` both scoped; the by-id read did not |
| identity, below | `organizationMembers` (the list) calls `requireCurrentUserId`; the two by-id reads do not |

### What was verified

Both carry `@PreAuthorize("isAuthenticated()")` and pass the caller-supplied id straight to a
finder.

**`organizationMember(organizationId, userId)`** → `memberService.findByUserAndOrganization(...)`.
Any authenticated caller reads any organization's membership row for any user: their role in that
organization, and by enumeration its team composition. The paged sibling
`organizationMembers(organizationId, …)` resolves the caller first; this one does not.

**`ownershipTransfer(id)`** → `transferService.findById(id)`. This is the more serious of the two.
`OwnershipTransferRequest` exposes **`transferToken: String!`** in the SDL, and ET-ORG-002 §1
describes ownership transfer as moving "control of a business's money to another person … a
two-party handshake with an expiring token, confirmed by the recipient".

**The token is one of two factors, not the whole credential.** `acceptOwnershipTransfer(token,
confirmationCode)` requires both, so reading the token does not by itself complete a takeover —
which is the difference between this being urgent and being an emergency. It is still the bearer
half of the handshake, readable by anyone signed in who holds a transfer id, and the design
intends it to travel only to the named recipient.

### The fix — two different rules, and a schema decision

The two reads look alike and are not, which is why a single "add a guard" pass would have got one
of them wrong.

**`organizationMember` is tenant-scoped.** Both arguments come from the client, and the question
is not *is this row yours* — a caller may legitimately read a colleague's membership — but *are
you in the organization it names*. That is `TenantScope.permits(organizationId)`, with the
argument acting as a selector over memberships the token established. A subject comparison, the
instrument F-010 used, would have refused every colleague and looked like a working fix.

**`ownershipTransfer` is party-scoped.** The parties are the current owner and the named
recipient. Neither need share an organization with the other by the time it completes, and the
recipient may belong to none at all, so tenancy is the wrong instrument here. Platform
administrators read it because a stalled transfer is a support case. `ROLE_ORGANIZER` is excluded
— every organizer holds it — and so is `ROLE_FINANCE`, for a different reason: a transfer is a
control change, not a money movement.

**`transferToken` left the output type.** This is the part a guard could not have fixed. Scoping
the read narrows who can select the field; it does not change the fact that a bearer credential
was a selectable field on a type. Nothing reads it back — acceptance and decline take it as an
*argument*, and the server looks the transfer up by it — so `ConfirmOwnershipTransferInput` keeps
it and the output type does not. No client read it, so [ET-PLT-010](tasks/ET-PLT-010.md)'s
deprecation window had no working behaviour to protect.

### Evidence

`OrganizationReadScopeTest` — 9 cases on a replica set, each negative first proving the row is
reachable unscoped. `OrganizationReadScopeLintTest` — 4 shape checks, comments stripped, one of
which holds the *schema* decision rather than code: re-adding `transferToken` to the type would
otherwise look like restoring a missing field. Mutation-verified in both directions — unscoping
the membership read and re-adding the token each fail exactly the assertion that names them.

---

## F-012 · An invitation link disclosed the invitee's contact details, and the owner index was never built

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R2 and R4.
**Severity** OWASP A01 (information disclosure) and a missing database constraint.
**Status** **closed 2026-09-01**. **Owning specs** ET-ORG-002 with [`ET-PLT-002`](tasks/ET-PLT-002.md).

### R4 · `invitationByToken` answered with the whole invitation

§4 declares `invitationByToken(token) PUBLIC InvitationPreview` and spells out why:

> `InvitationPreview` is a deliberately narrow type — `organizationName`, `organizationLogoUrl`,
> `proposedRole`, `inviterDisplayName`, `expiresAt` — because the token is a bearer credential and
> anything it exposes is exposed to whoever the link was forwarded to.

**The type did not exist.** The query returned `TeamInvitation`, which carries `email`,
`phoneNumber`, `inviteeName`, `invitedById`, `message` and `invitationToken`.

An invitation token arrives by email or WhatsApp and is then forwarded, screenshotted and pasted
into group chats — that is the normal life of an invitation link, not an attack. Everything this
query answered was therefore answered to whoever the link reached rather than to the person it was
addressed to: **a named individual's email address and phone number, disclosed by holding a link
somebody forwarded them.**

The five-field type now exists and the query returns it. `TeamInvitation` is unchanged — an
organizer listing their own pending invitations legitimately needs the invitee's email, and that
surface is authenticated and scoped. Narrowing one query is not the same as stripping the document,
and a test pins both halves.

### R2 · the one-owner constraint was a comment, not an index

§4 declares `identity_organization_members {organizationId} PARTIAL UNIQUE where role=OWNER`, and
R2 states the race it exists for. The collection carried `{userId, organizationId}` unique and
`{organizationId, role, status}` compound. **Neither prevents a second owner.**

Two ownership transfers confirmed at the same moment each read one owner, each write a second, and
the organization ends with two people who can each remove the other, move the bank account and
take the payouts. No check-first wins that: the check and the write are separate operations and
the window is the gap between them. ET-PLT-002 §4 opens by saying uniqueness rows are
"**constraints, not optimisations: each one is a race the application cannot win by checking
first**", and this is the clearest example of it in the corpus.

Two `identity_ownership_transfers` rows §4 declares were also absent — the unique `transferToken`,
without which one acceptance link can resolve to two handshakes.

### The registries disagreed, which is why nothing caught it

Adding the three indexes made `IdentityIndexRegistryTest` fail: it compares the code's
declarations against **ET-PLT-002 §4's central index registry**, and that table did not carry
these rows even though **ET-ORG-002 §4 did**. Two specs, two index registries, three rows in one
and not the other — and the lint enforcing set equality was quietly holding the code to the
smaller of the two.

That is [F-002](#f-002--the-index-registry-is-not-the-only-index-authority)'s shape from the other
direction: not an index outside the registry, but a **requirement outside it**. The three rows are
now in ET-PLT-002's table, so all three authorities agree.

### Evidence

`InvitationPreviewTest` — 4 cases at layer 4, mutation-verified twice: pointing the query back at
`TeamInvitation` fails the return-type case, and adding a sixth field fails both the exact-five
case and the withheld-fields case, naming `email`. `IdentityIndexRegistryTest` asserts all three
new rows against a live database.

---

## F-013 · The role hierarchy was eleven hand-maintained lists and a fall-through chain

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R1.
**Severity** correctness and maintainability; two spec deviations recorded, not silently corrected.
**Status** **closed 2026-09-18** — structure on 2026-09-01; the catalogue, the settings switches and
the event-access bug in [F-037](#f-037--the-open-items-after-the-temporal-move-closed-refunds-provider-evidence-tenancy-permissions-approvals-indexes).

### What R1 asks, and what was there

R1 requires the effective permission set to be a transitive closure computed in **one** method, and
that **no capability check compare a role by name**.

`OrganizationRole` carried eleven `canX()` methods, every one of them a name comparison —
`this == OWNER || this == ADMIN || this == MANAGER` — plus a twelfth list in `permissions()`. Twelve
lists that must agree with each other and with §4. Adding a sixth role means editing all twelve,
and forgetting one is a silent grant or a silent denial with nothing to compare against.

**`permissions()` was a `switch` with fall-through**, which makes the roles a total order:
`OWNER → ADMIN → MANAGER → MARKETER → CONTRIBUTOR`. §4 says the opposite in as many words:

> `MARKETER` and `MANAGER` share rank 2 and are **not** comparable: a marketer is not a lesser
> manager, and a role comparison that assumes a total order gets this wrong.

A chain cannot express §4's shape at all, because **ADMIN inherits from both rank-2 roles**. Written
as fall-through, one of MANAGER or MARKETER sits beneath the other and whichever loses contributes
nothing of its own.

`AuthorizationServiceImpl` compounded it with `role.isAtLeast(requiredRole)` — the total-order
framing in the name. It is now `includes`, subset containment, which has a correct answer for an
incomparable pair where `>=` does not.

### What the restructure surfaced

`OrganizationMember.effectivePermissions()` called `role.permissions()` and then `addAll`-ed the
member's custom permissions **into the returned set**. Harmless while the method built a fresh
`HashSet` per call; a live bug the moment the closure is computed once and shared, since one
member's custom grant would reach every member holding that role for the life of the process. The
caller now copies, and a test asserts the closure is unmodifiable so the mistake fails loudly
rather than spreading.

### Two deviations recorded rather than fixed

**The vocabulary is not §4's.** §4 and [D-17](ROADMAP.md) both specify `module:action` —
`event:create`, `team:invite`. The platform runs on `SCREAMING_CASE`, and **38 call sites across
all three services** compare against those strings, including catalog's `EventWriteGuard` and
booking's payout mutations. Migrating is a cross-service change with a stored role→permission
mapping behind it; changing the enum alone would leave new strings in one place and old strings at
38 others, breaking every permission check while looking correct.

**Two permissions §4 marks opt-in are unconditional.** §4 makes `FINANCIAL_VIEW` for MANAGER and
`PAYOUT_REQUEST` for ADMIN configurable per organization —
`settings.managersCanViewFinancials`, `settings.adminsCanRequestPayouts` — and calls them opt-in.
They remain in the closures because the settings-based resolution that would grant them belongs to
ET-ORG-003 and does not exist. Removing them would take financial visibility from every manager
and payout requests from every admin, with nothing to restore either.

Both are over-grants relative to §4 and both are recorded here rather than half-corrected.

### Evidence

`OrganizationRoleTest` — 13 cases at layer 1. Mutation-verified twice, and the first attempt
**failed to catch one of them**: making MANAGER inherit MARKETER produces an identical effective
set, so every set-based assertion still passed. What changes is the shape, so the test now asserts
the parents directly — MANAGER and MARKETER each have exactly `CONTRIBUTOR`. A permission set is
not a hierarchy, and a test over sets alone cannot see the difference.

---

## F-014 · A forwarded invitation link was enough to join the organization

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R4 and R5.
**Severity** OWASP A01 — Broken Access Control (CWE-639), any authenticated caller.
**Status** **closed 2026-09-01**. **Owning spec** ET-ORG-002.

### What was verified

`TeamInvitationServiceImpl.accept(invitationToken, userId)` looked the invitation up by token,
checked it was `PENDING` and unexpired, and **created the membership for whoever the caller was**.
R5 exists precisely to prevent that:

> WHEN an invitation is accepted, THE SYSTEM SHALL verify the accepting identity matches the
> invitee, and IF it does not, THEN THE SYSTEM SHALL refuse.

The token says *which* invitation. It cannot say *who is accepting*, because an invitation link
arrives by email or WhatsApp and is then forwarded, screenshotted and pasted into group chats —
the holder and the addressee being different people is the normal case, not the attack.

So any signed-in account holding a link joined the organization **in the invitation's proposed
role**. For an `ADMIN` invitation that is the team, the events and the organization's settings.

**This completes the chain [F-012](#f-012--an-invitation-link-disclosed-the-invitees-contact-details-and-the-owner-index-was-never-built)
started.** That finding was `invitationByToken` returning the whole invitation, so a forwarded link
disclosed the invitee's email and phone. This one is the same link then being enough to take their
seat. Neither is remarkable alone; together they are read-the-invitation-then-become-the-invitee.

### The fix

`TeamInvitation.addressedTo(email, phone)`, compared against the accepting user's **own account** —
never against anything in the request, since an identity the caller supplies is not an identity.
Either identifier matching is enough: a Zambian invitee may hold the number the invitation was sent
to and an email the inviter guessed, or the reverse, and requiring both would refuse the ordinary
case.

The refusal is `INVITATION_NOT_ADDRESSED_TO_CALLER`, classified `PERMISSION_DENIED` rather than
disguised as `*_UNKNOWN`: the caller demonstrably holds a real token, so concealing the
invitation's existence conceals nothing they do not already know. What it must not disclose is
*who* it was addressed to — that is the invitee's contact details, and F-012's narrow preview
exists to withhold exactly that. The attempt is logged `securityIncident=true`.

### R4, found alongside

`isValid()` and `isExpired()` both called `Instant.now()` — ET-PLT-001 R3's clock discipline
violated, and no frozen-clock boundary test possible while they did. Both now take the instant, and
`isExpired` uses `!isAfter` rather than `isBefore`: at the expiry instant exactly, `isBefore`
answers false, so the token stayed valid through the whole of its final moment and the two
predicates disagreed with each other there. A day-6/day-8 pair alone would never have found it.

### R5's other two halves, done in the same pass

**Acceptance was three separate writes** — the membership, its event grants, and the invitation's
status — where R5 requires one transaction. A failure between any two left a member with no
grants, or a membership created against an invitation still `PENDING`, which the next holder of the
link could accept again.

**And it read the status before writing it.** Both racers found `PENDING` and both proceeded, so
one invitation admitted two members and nobody had invited the second. That race is the ordinary
case rather than an attack: a link forwarded into a group chat is opened by several people within
seconds, and a recipient tapping twice on a slow connection produces it alone.

Acceptance now **claims first** — one conditional update matching only while the invitation is
still `PENDING`, so exactly one caller's write reports a modified row and owns the acceptance. The
same compare-and-set booking uses for reservations, for the same reason. The claim sits *inside*
the transaction rather than before it, so a failure further down rolls the status back and the
invitee can retry with the same link; claiming outside would burn the token on a transient failure.

The acceptance notification hangs off `doOnSuccess`, outside the boundary: an inviter told somebody
joined by a transaction that then rolls back is worse than one told a moment late — the second is a
delay, the first is a false statement about who is on the team.

### Evidence

`InvitationAcceptanceTest` — 15 cases, layer 2. Mutation-verified twice: making `addressedTo`
return true fails three cases including the stranger; reverting `isExpired` to `isBefore` fails the
boundary case and nothing else. The null-tolerance case is deliberate — a comparison that treats
"unknown" as "equal" would admit every account with an incomplete profile.

`ConcurrentAcceptanceTest` — 6 cases at layer 5, 24 parallel callers against the real replica set,
`@RepeatedTest` because a race that resolves correctly once may have been lucky. **Exactly one**
winner, both bounds: "at most one" passes against an implementation that claims for nobody, "at
least one" against one that claims for everybody.

`InvitationAcceptanceLintTest` — 5 shape checks, mutation-verified twice. It also caught **four
more wall-clock reads** on the same path, including the one setting `expiresAt` — so the expiry a
boundary test asserts was itself computed from the wall. `InlineNowLintTest`'s identity budget fell
97 → 90 across the two passes.

---

## F-015 · A removed member kept every event grant they held

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R6.
**Severity** OWASP A01 — Broken Access Control. **Status** **closed 2026-09-01**.
**Owning spec** ET-ORG-002.

### What was verified

`removeMember` and `leaveOrganization` set `status = REMOVED`, updated the Keycloak group and
recounted the members. **Neither touched `identity_event_access_grants`.**

Organization membership and event access are two independent grants, and a member can hold an
event grant their organization role never gave them — a freelancer given EDITOR on one festival, a
scanner given one gate. So marking the membership `REMOVED` said nothing about those rows, and a
removed member went on editing the event they were removed over.

R6 states the consequence it exists to produce:

> The member loses access on their **next request**, not on their next token

They did not lose it on any request. And the failure is worse than an ordinary missing check
because **the organization's own team screen showed them gone** — nobody looks for a problem the
interface reports as solved.

`removedAt` was not set either, so a membership that ended could not be told from one that never
happened, and a re-invited member's history was unreadable.

### The fix

One transition, `endMembership`, used by both paths. It revokes every `ACTIVE` grant the member
holds **for that organization's events**, sets `REMOVED` and `removedAt`, and does both in one
transaction — the halves are only meaningful together, since a status without the revocations is
this defect and revocations without the status take somebody's access while leaving them on the
team.

Three scoping decisions the tests pin:

- **By organization, not by user.** A member removed from one organization keeps access in
  another; revoking by `userId` alone would take access nobody removed them from.
- **By member, not by event.** A colleague's grant on the same event is not this removal's
  business.
- **Live grants only.** Re-stamping an already-revoked row would move an organizer's deliberate
  revocation to today and relabel their reason as a removal — quietly rewriting the audit trail.

**Keycloak stays outside the transaction**, and the lint asserts the ordering. Keycloak mirrors
membership rather than owning it, and a removal is the last operation that should be blocked by a
third party being down; R8's drift sweep is what repairs the mirror.

### Why two paths had drifted into one defect

`removeMember` and `leaveOrganization` end the same thing for different reasons and had two bodies
doing the same three steps. Two bodies drift: the next requirement lands in one of them, and a
member who leaves keeps grants a member who is removed loses. Both now route through one
transition, and a lint counts the call sites.

### Evidence

`MemberRemovalTest` — 6 cases on a replica set, seeding a grant in a second organization and a
colleague's grant on the same event so both scoping mistakes are exercised rather than described.
`MemberRemovalLintTest` — 4 shape checks, mutation-verified: skipping the revocation and widening
it to the whole user each fail the assertion that names them.

---

## F-016 · A suspended admin could be handed the organization, and two confirmations could run the handover twice

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R7.
**Severity** OWASP A01 — Broken Access Control, plus a correctness race.
**Status** **closed 2026-09-01**. **Owning spec** ET-ORG-002.

### The eligibility check had one half of its condition

R7 requires the nominee to be an **active `ADMIN`**. `initiate` checked
`newOwnerMember.getRole() != ADMIN` and nothing else.

So a member who is `SUSPENDED` — somebody the organization has deliberately shut out, very often
mid-dispute — could be nominated and handed the business. And a `REMOVED` one could too, because
[R6 retains the record by design](#f-015--a-removed-member-kept-every-event-grant-they-held): a
removed member's document survives reading `role = ADMIN` indefinitely, so a role-only check
treats it as live. The two findings compound — the fix for one is what makes the other reachable.

The refusal is now `TRANSFER_TARGET_INELIGIBLE` naming the requirement, rather than a bare
`IllegalArgumentException`.

### Confirmation read the status before writing it

`accept` validated the transfer was `PENDING`, verified the 2FA code, then ran the handover. Two
confirmations racing is the ordinary case — a nominee tapping twice, or a client retrying after a
timeout — and both passed the read. **The second handover demotes the owner the first just
promoted**, leaving the organization with the previous owner as `ADMIN` and nobody as `OWNER`.

Now a conditional claim `PENDING → COMPLETED` runs first; exactly one caller's update reports a
modified row and performs the handover. The same compare-and-set as
[F-014](#f-014--a-forwarded-invitation-link-was-enough-to-join-the-organization)'s acceptance, and
booking's reservations before that — three places, one pattern.

### The handover was not a transaction, and the ordering is load-bearing

Demote, promote, update `organizations.ownerId` — three writes, no boundary. **A demote that
commits without its promote leaves the organization with no owner at all**, which is worse than the
two owners the new partial unique index prevents: nothing can then be transferred, no payout
requested, and no member removed. Now one transaction.

The demote-before-promote ordering is also load-bearing rather than stylistic, and a lint asserts
it: the partial unique index from
[F-012](#f-012--an-invitation-link-disclosed-the-invitees-contact-details-and-the-owner-index-was-never-built)
holds one `OWNER` per organization, so promoting while the current owner still holds the key is a
duplicate-key failure rather than a handover. The index that fixed one defect constrains the fix
for another.

### Also corrected

Two more wall-clock reads, and the transfer window was a `TRANSFER_EXPIRY_HOURS = 72` constant
rather than §4's `identity.transfer.ttl` of P3D — the same duration, expressed so that changing it
means editing a number in a service rather than configuration.

### Evidence

`OwnershipTransferTest` — 11 cases at layer 5, 24 parallel confirmations under `@RepeatedTest`,
asserting **exactly one** winner and that the organization ends with **exactly one owner**, which
is the property the whole handshake exists to preserve. `OwnershipTransferLintTest` — 4 shape
checks, mutation-verified: dropping the `ACTIVE` half and reversing the demote/promote order each
fail the assertion that names them.

---

## F-017 · Re-inviting refused instead of replacing, and two checks R3 requires were absent

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R3.
**Severity** usability with a correctness edge; two missing preconditions.
**Status** **closed 2026-09-01**. **Owning spec** ET-ORG-002.

### The behaviour was inverted

R3 is explicit: inviting somebody who already has a pending invitation **"revokes the pending one
and creates a new one — never two live"**. `invite` refused instead, with "User already has a
pending invitation to this organization".

That strands the inviter. Correcting a mistyped role, a misspelled address or an email that
bounced is impossible until the first invitation expires **seven days later** — and re-inviting is
overwhelmingly done *because* something was wrong the first time. The invitee waits a week for a
link nobody can resend.

The reason R3 wants replacement rather than accumulation is the other half: two live invitations
to one person are **two sets of terms**, very often two different roles, since correcting the role
is exactly why somebody re-invites. Whichever link the invitee happens to open decides what they
get, and the inviter has no way to know which.

### Two preconditions R3 names were not implemented

- **`MEMBER_ALREADY_EXISTS`** — an existing active member could be invited again. They accept, and
  the `{userId, organizationId}` unique index throws a duplicate-key error, surfacing as an
  internal failure rather than an answer. The check is on *active* membership specifically: a
  member removed under [F-015](#f-015--a-removed-member-kept-every-event-grant-they-held) keeps
  their document, and treating that as membership would make the re-invitation R3 explicitly
  allows impossible.
- **`ORGANIZATION_NOT_ACTIVE`** — invitations could be sent from a suspended or unapproved
  organization, building a team for something that cannot sell a ticket, where the invitee's first
  experience is a dead account.

### The supersession's scope is the whole of its correctness

Three ways to get it wrong, each pinned by a test:

- **Scoped by organization alone** revokes the entire team's outstanding invitations.
- **Scoped by email alone** revokes the person's invitation to a different organization, which they
  may legitimately hold at the same time.
- **Without the `PENDING` condition** it re-stamps settled history — an accepted invitation would
  end up marked revoked, saying the membership came from an invitation that was withdrawn.

Revoke and create are one transaction: a revoke committing without its replacement leaves the
invitee with no way in while the inviter believes one was sent.

### Evidence

`InvitationUniquenessTest` — 7 cases on a replica set, seeding another organization's invitation
and a colleague's so both scoping mistakes are exercised rather than described.
`InvitationAcceptanceLintTest` grew to 9 checks, mutation-verified: widening the supersession to
the organization and treating removed members as members each fail the assertion that names them.

---

## F-018 · A failed Keycloak group write was a log line, and nothing repaired the drift

**Found** 2026-09-01, verifying [`ET-ORG-002`](tasks/ET-ORG-002.md) R8.
**Severity** correctness — silent, unbounded drift. **Status** **half closed 2026-09-01**;
the reverse direction is open and explained below.

### What was verified

All three Keycloak group writes — add, remove, role change — ended in
`onErrorResume(e -> { log.warn(...); return Mono.empty(); })`.

That satisfies R8's first half correctly: **the mutation must not fail.** Keycloak mirrors
membership rather than owning it, and an organizer removing somebody must not be blocked by a
third party being down — which is exactly the operation you least want blocked. Every removal,
role change and ownership transfer this session commits before it mirrors, and that is deliberate.

It loses the second half entirely. R8 continues: the failure "marks the membership
`mirrorPending`", and "a scheduled sweep reconciles Keycloak's groups to MongoDB and clears the
marker". **`mirrorPending` did not exist on the model and no sweep existed.**

So a failed group write left drift that was real, invisible and unrepairable except by
reconciling every member of every organization — expensive enough that nobody would, so the mirror
simply stayed wrong. A demoted admin keeps admin groups; a removed member keeps theirs; and
because a token carries its groups for its whole lifetime, so does anyone who signs in during the
gap.

### What was built

`mirrorPending` on the membership, indexed and set by a `markMirrorPending` that logs the
organization and the user, writes the flag, and lets the mutation succeed. The marking is itself
best-effort: if MongoDB is unreachable too there is nothing further to do, and failing the mutation
at that point would surface a Keycloak outage as a membership error — the coupling the whole design
avoids.

`GroupMirrorSweep` reads only marked rows, in bounded batches, applies MongoDB's view — a `REMOVED`
member leaves the group, an active one joins the group their current role names — and **clears the
marker only on success**. That last point carries the mechanism: a sweep that clears regardless is
worse than no sweep, because it reports the mirror as repaired and destroys the only evidence that
it is not, leaving R8's pending-count metric reading zero while the drift persists.

### R8's own lint, which passes

R8 asks for "a lint check for group claims in decision code", and `GroupMirrorLintTest` confirms
**no authorization decision reads a Keycloak group today**. That is not incidental: it is what
makes one-directional mirroring safe. Groups drift by design between a change committing and the
sweep repairing, so a decision consulting them is right almost always and wrong exactly when it
matters — surfacing as an intermittent authorization anomaly nobody can reproduce.

The allowlist has a self-check, and it caught a filename this pass had guessed at rather than
verified.

### The half that is open, and why it was not built

R8 also requires that "a group membership with no MongoDB row is removed" — Keycloak holding a
user the platform has no record of. That direction needs a group-members listing
`KeycloakService` does not expose. Adding one would put a new, unreviewed Keycloak Admin API call
on a path that runs every minute against every organization, and getting its paging or its error
handling wrong turns a repair sweep into an outage. Recorded rather than half-built.

The pending-count **metric and alert** R8 asks for are likewise not wired: `pendingCount()` exists
and nothing publishes it.

### Evidence

`GroupMirrorSweepTest` — 5 cases on a replica set, the central one being that a *failed* repair
leaves the row marked. `GroupMirrorLintTest` — 2 checks, mutation-verified: adding a `groups()`
read to `PermissionResolutionServiceImpl` fails it by name.

---

## F-019 · `CLAUDE.md` documents a runtime the platform does not have

**Found** 2026-09-02, verifying [`ET-PLT-012`](tasks/ET-PLT-012.md) R1.
**Severity** documentation — but the kind that gets built. **Status** **closed 2026-09-02.**

### What was verified

ET-PLT-012 R1 requires that no module resolve `spring-modulith-*`, `spring-boot-starter-jdbc` or
the PostgreSQL driver — "the platform has no relational dependency". Adding that to the enforcer's
banned list meant first checking nothing currently depends on them.

Nothing does. Across all seven modules:

| Claimed by `CLAUDE.md` | Actually present |
|---|---|
| `@ApplicationModuleListener` for intra-service events | **no occurrence in any `src/main/java`** |
| `spring-modulith-starter-jdbc` | **no occurrence in any `pom.xml`** |
| `jdbc:postgresql://…/shared_db?currentSchema=modulith_events` | **no datasource in any `application.yml`** |
| `ModulithEventConfig.java` defining `jdbcTransactionManager` | **no such file** |

`CLAUDE.md`'s "Critical Architecture Decision #1 — Reactive Stack with Blocking Event Publication
(HYBRID)" describes, in detail and with configuration samples, an architecture that does not exist.
So does its event-handling diagram, its PostgreSQL data-store box, its `@ApplicationModuleListener`
worked example, four of its DO/DON'T rules and two troubleshooting entries.

### Why this is not merely untidy

`CLAUDE.md` is the instruction file. It is loaded into every session and it **overrides default
behaviour by its own first line**. A future session implementing guaranteed intra-service delivery
would read it, add `spring-modulith-starter-jdbc`, stand up a `shared_db`, and be building the
hybrid it describes — reintroducing a blocking JDBC driver into a reactive platform, which is the
one thing ET-PLT-001 R1 exists to prevent.

The decision to drop it was deliberate and is recorded — in the parent POM, where the ban's own
comment reads: *"the reactive stack has to earn its place, and once the outbox lives in MongoDB
there is nothing left for it to do. Keycloak owns the only relational schema and no service
connects to it."* The reasoning survived; the document that contradicts it did not get updated.

This is [VERIFICATION.md §1.2](VERIFICATION.md) exactly — read a claim, grep for the mechanism,
find it absent — applied for the first time to a document rather than to a spec. It was found only
because R1 named the dependency explicitly enough to check.

### What was done

- `org.springframework.modulith:*` added to the enforcer's banned list, completing §4's table. The
  ban is now the mechanism; the prose is no longer the only record.
- `BuildTopologyTest.nothingRelationalIsDeclared` asserts it across all five Spring modules.
- `CLAUDE.md` corrected: §1 rewritten to describe the MongoDB outbox actually in use, the
  event-handling diagram's intra-service half rewritten, the PostgreSQL box reduced to Keycloak's
  own schema, and the four stale DO/DON'T rules and two troubleshooting entries replaced.

### The general point

Every other finding in this file came from reading a spec against the code. This one came from
reading the code against the *instructions*, and the corpus has no routine that does that. Worth
one pass: `CLAUDE.md` also states DGS 10.0.1 where CONVENTIONS.md §0 pins 10.5.0 — already flagged
in ET-PLT-012's R0 note, and now two for two.

---

## F-020 · The one module that inherits nothing was compiling at Java 17

**Found** 2026-09-02, verifying [`ET-PLT-012`](tasks/ET-PLT-012.md) R5.
**Severity** correctness — latent, and invisible to every existing check. **Status** **closed
2026-09-02.**

### What was verified

R5: "every module's `target/classes` reports class file major version 65". Six do.
`keycloak-extensions` reported **61** — Java 17.

Its POM declared `maven.compiler.source`/`target` 17. Not as an override of something: R3 requires
this module to have **no `<parent>`**, because a Spring Boot parent would drag Spring onto a
Keycloak provider classpath. The consequence nobody had drawn is that it therefore inherits no
compiler configuration, no encoding, no plugin management and no enforcer either — the module the
build checks least is the one nothing else can check for it.

### Why it built green for as long as it has existed

A lower `release` is a perfectly valid build. Nothing warns. The module compiled, shaded and
deployed, and the only symptom would arrive later: `source`/`target` (rather than `release`)
compiles 21-era syntax against the *running* JDK's class library, so an API added after 17 links
happily at build time and throws `NoSuchMethodError` on whatever JDK Keycloak actually ships.

The same absence had a second consequence found in the same pass: surefire resolved to Maven's
built-in default **3.2.5** against the 3.5.3 every other module inherits from Boot — deterministic
for one Maven version, drifting across them, which is precisely the "two machines, one commit,
different plugin versions" R6 forbids.

### What was done

- `maven.compiler.release` 21 (not `source`/`target`), `project.build.sourceEncoding` UTF-8.
- `keycloak.version` 26.0.0 → **26.0.7**, matching the parent. Compiling an SPI against a different
  Keycloak than the platform declares is how a provider loads and then fails on a method that moved
  between patch releases.
- Compiler, jar, shade and surefire plugin versions pinned as properties, each equal to the
  parent's.
- `BuildTopologyTest.everyModuleCompilesToJava21` reads `target/classes` rather than the POM,
  because the POM is what lied. **Mutation-verified**: setting the module back to 17 fails exactly
  that assertion and no other.

### The general point

The sibling comparison ([VERIFICATION.md §1.1](VERIFICATION.md)) generalises past collections. Six
modules inherited a compiler configuration and one did not, for a good reason — and the good reason
is what made it the one worth opening. **Where something is deliberately exempt from the mechanism,
check what else that exemption silently covers.**

---

## F-021 · The GraphiQL console shipped to production, beside a comment saying it should not

**Found** 2026-09-02, verifying [`ET-PLT-005`](tasks/ET-PLT-005.md) R4.
**Severity** security misconfiguration (OWASP A05:2021) — **bounded**, see below.
**Status** **closed 2026-09-02.**

### What was verified

R4's last acceptance box asks that debug output be off in every non-local profile. All three
subgraphs had, in their **base** `application.yml`:

```yaml
spring:
  graphql:
    graphiql:
      enabled: true
```

The base file is inherited by every profile, and `application-prod.yml` overrides nothing
GraphQL-related in any of the three. The route is also `permitAll`:

```java
// GraphiQL UI - development only
.pathMatchers("/graphiql/**").permitAll()
```

**The comment is the finding.** Somebody knew this was development-only and wrote it down next to
the line that fails to make it so. The intent was recorded; the configuration did the opposite.

### The reach, stated precisely

Not an unauthenticated schema dump, and it matters to say so:

| | |
|---|---|
| The GraphiQL **page** | served unauthenticated, in production, on all three subgraphs |
| Executing a query from it | goes to `/graphql`, which is `.authenticated()` — refused |
| Introspecting the schema | also `/graphql` — refused |

So what shipped is an interactive console with no data behind it, on services that sit behind the
gateway and router rather than on the public internet. That is a misconfiguration to fix, not an
open door — and F-007's lesson is that overstating a finding costs more than the finding is worth.

It is still wrong. `permitAll` on a debug surface is a control that depends entirely on a flag
nobody set, the flag defaulted the unsafe way, and network topology is not an enforced control.

### What was done

- Base files default to `${GRAPHIQL_ENABLED:false}` — off unless an operator turns it on per
  environment. The development value moved to `application-local.yml`, where a value in a file
  called `local` is visibly a development value.
- **A duplicate top-level `spring:` key was introduced and caught while doing this.** Appending a
  `spring:` block to a file that already had one is a YAML defect that either throws or silently
  drops half the file. Merged into the existing block; all six files now parse with exactly one.
- `DebugSurfaceLintTest` bans a literal `true` on any debug key in a base or prod profile, while
  deliberately permitting `${VAR:false}` — a rule with no compliant path gets deleted rather than
  followed.

### The lint was wrong first, and the mutation is what said so

Written, run, green. Then mutated the config back and **it stayed green**. Two causes, both worth
recording:

1. The `sed` used to mutate never matched — `${...}` is special to the shell and to `sed`. **A
   mutation that silently fails to apply reads exactly like a passing mutation test.** Verify the
   mutation landed before believing the result.
2. The real bug: `enabledLiterally` scanned four **raw** lines ahead for `enabled:`. The
   explanatory comment added above it pushed the value to line six. So the lint's reach depended on
   how much prose sat above the value — and it went green on a file that still read
   `enabled: true`. Now skips comments and blanks and counts four *meaningful* lines.

This is [VERIFICATION.md §2.5](VERIFICATION.md) rule 1 in a form not yet listed there: not "strip
comments before matching" but **"do not let comments consume a positional window"**. Same root
cause, opposite direction.

### The general point

ET-PLT-001 D1 found the identical shape in credentials — working fallback passwords in a base
file, inherited by prod, overridden nowhere. **Two for two.** The base `application.yml` is where
unsafe defaults hide, because it reads like "the defaults" and behaves like "production".

---

## F-022 · The command every gate names could not succeed for any spec

**Found** 2026-09-02, closing [`ET-PLT-005`](tasks/ET-PLT-005.md).
**Severity** process — the corpus could not verify itself. **Status** **closed 2026-09-02.**

### What was verified

Every gate in the corpus ends with the same box:

> `mvn -q -f backend verify -Dgroups=<SPEC> -DfailIfNoTests=true` green

Run for ET-PLT-005 it fails. Not on an assertion — **100 tagged tests pass** — but on
`api-gateway`, with `No tests were executed!`.

`failIfNoTests` is evaluated **per module**, not per reactor. So in a five-module build it means
"zero in *each* module", and it fails on the first module that has no test carrying that tag. No
spec in this corpus has a test in all five modules, and most are concentrated in one or two. The
flag therefore refuses every spec, always, however much passed.

A second, independent instance of the same failure: `identity-service` was the only module binding
`maven-failsafe-plugin`, with `<include>**/*IT.java</include>` — and **there is not one `*IT.java`
file anywhere in the backend**. This corpus names its layer-5 tests `*Test` and selects them by
`@Tag`. Failsafe reads the same `failIfNoTests` flag, so it failed on identity-service for every
spec, before surefire's turn even came.

### Why this matters more than a wrong flag

The flag was there for a real reason, stated in `README.md`: without it a tag matching nothing
exits 0 and the spec "verifies green" having executed no tests, *which looks like proof*. That
concern is correct. The mechanism chosen to address it could not work, and because it could not
work it was presumably never run to completion — which is consistent with 41 specs, none marked
`verified`, and thirty-three carrying no tagged test at all.

**A verification step that cannot pass is indistinguishable from one nobody runs.**

### What was done

- The dead failsafe binding removed from `identity-service`, with a note that if `*IT` naming is
  ever adopted it belongs in the **parent**, so all five modules get it rather than one.
- All 83 corpus files carrying the command rewritten to `-DfailIfNoTests=false`.
- **The guarantee moved into the reactor** as `SpecTagCoverageTest`: any spec at `implemented` or
  `verified` with no test carrying its tag fails the build, and — the direction that rots quietly —
  any `@Tag` naming a spec that does not exist fails too. Mutation-verified in both directions.
- `README.md`'s paragraph rewritten. The blanket substitution had inverted it into "`false` is not
  optional, without it a tag that matches nothing exits 0", which is the opposite of true.

### The general point

A flag on a command line is a control that survives only as long as whoever types it next
remembers. Moving it into a test makes it a property of the corpus. That is the same move as
[F-019](FINDINGS.md)'s enforcer ban replacing a documented intention, and F-021's lint replacing a
comment — **three findings this session where the fix was to convert a written rule into a
mechanical one**, because in each case the written rule was already being disregarded and nothing
said so.

---

## F-023 · The storefront advertises the early-bird price; checkout charges the full one

**Found** 2026-09-02, migrating [`ET-PLT-001`](tasks/ET-PLT-001.md) R3 in catalog's domain models.
**Severity** business correctness — a consumer-facing price discrepancy.
**Status** **closed 2026-09-18 by [F-036](#f-036--the-catalog-and-payment-gaps-f-031f-035-left-open-closed)**
— the mirror was widened and `ReservationServiceImpl.line()` now prices off the injected `Clock`, with a
boundary test either side of the window and at the instant itself.

### What was verified

Early-bird pricing is configurable, persisted, advertised — and never charged.

| Stage | Where | State |
|---|---|---|
| Organiser sets it | `CreateTicketTierInput.earlyBirdPrice` / `earlyBirdEndsAt` | works |
| Stored | `TicketTier`, both fields | works |
| **Advertised** | `Event.minTicketPrice` / `maxTicketPrice` resolve through `TicketTier.getCurrentPrice()`, which **is** early-bird aware | **shows the discount** |
| Mirrored to booking | `InternalEventController` → `EventSummaryDto.TicketCategoryDto` | **carries `price` only — no early-bird fields at all** |
| **Charged** | `ReservationServiceImpl.line()` → `tier.getPrice()` | **full price** |

So a customer browsing sees "from K150" and is charged K200. The two numbers are computed by
different services from different fields, and nothing compares them.

This is not "a feature that was never finished". The discount is *displayed*. Only the charge is
wrong, which is the worse half — an unadvertised discount disappoints nobody, an advertised one
that is not honoured is a complaint, and in a market where organisers lean on early-bird pushes to
seed a launch it is their promotion that fails.

### The mirror is the root cause

`TicketCategoryDto` has six fields: `code`, `name`, `price`, `capacity`, `sold`, `active`. Booking
**cannot** apply an early-bird price because it is never told one exists. Fixing this means
widening a cross-service payload, mapping it in `InternalEventController`, and pricing against the
injected clock in `line()` — with a test that a reservation created a minute before the window
closes is priced at the early-bird rate and a minute after is not.

That is a change to what customers are charged. It belongs to the spec that owns reservation
pricing, deliberately and with its own tests, not to a clock migration passing through.

### What was done here

The clock half only, which is ET-PLT-001's actual job:

- `getCurrentPrice`, `isEarlyBirdActive`, `isOnSale`, `getSavings`, `getDiscountPercentage` and
  `Event`'s three date predicates now take the `Instant`; the resolver and
  `InternalEventController` pass `clock.instant()`.
- `EarlyBirdPricingTest` — five cases, **both bounds**, including the instant itself. Until today
  the method deciding the advertised price of every ticket on the platform had no test at all,
  because there was no way to write one against `Instant.now()`. Mutation-verified: making the
  deadline inclusive fails exactly one test, the one written for it.

### A correction to my own method

I first reported these methods as having **zero callers** and nearly deleted them as dead code.
They are not dead: `getCurrentPrice` is reached through `TicketTier::getCurrentPrice` — a **method
reference**, which `grep "\.getCurrentPrice("` does not match. The compiler found them; my search
did not.

Worth adding to [VERIFICATION.md §3](VERIFICATION.md)'s traps: **before calling something dead,
search for `::name` as well as `.name(`**. Deleting `getCurrentPrice` would have removed the only
correct early-bird implementation in the codebase and silently changed every advertised price on
the storefront to the undiscounted one — turning a charge-side defect into a display-side one and
making the two agree by breaking the half that worked.

---

## F-024 · A sold-out tier threw a NullPointerException, and the circuit breaker counted it

**Found** 2026-09-02, classifying [`ET-PLT-004`](tasks/ET-PLT-004.md) R1 — following the §4
ownership registry into how booking reserves inventory.
**Severity** correctness on the money path, and it gets worse under exactly the load it appears at.
**Status** **closed 2026-09-02.**

### What was verified

Catalog owns ticket inventory. Booking reserves against it over
`POST /api/internal/inventory/tiers/{id}/reserve`, and when a tier cannot satisfy the request
catalog answers **HTTP 409** with a well-formed `InventoryReservationResult` carrying
`success: false`. That is a deliberate, correct refusal.

`CatalogServiceClient.reserveInventory` handled it like this:

```java
.onStatus(HttpStatusCode::is4xxClientError, response ->
        response.bodyToMono(InventoryReservationResult.class)
                .flatMap(result -> { log.warn(...); return Mono.just(result); })
                .then(Mono.empty()))          // ← the refusal is read, logged, and discarded
.bodyToMono(InventoryReservationResult.class)
.doOnSuccess(result -> { if (result.success()) ... })   // ← result is null
```

An empty Mono returned from an `onStatus` handler **suppresses the error**, so WebClient carried on
to a body that had already been consumed. `bodyToMono` completed empty, `doOnSuccess` ran with
`null`, and an ordinary sold-out threw a `NullPointerException`.

### Why the NPE is the smaller half

`reserveInventory` carries `@CircuitBreaker`, `@Retry` and `@TimeLimiter`. The breaker counts
exceptions. So **every sold-out tier fed the circuit breaker a failure** — and tiers sell out during
an on-sale, which is the one moment the platform is under load. Enough of them in the rolling
window opens the breaker, and once open the fallback refuses **every** reservation, including for
tiers with thousands of seats left.

The failure mode is therefore: a popular tier sells out → the breaker opens → the entire event stops
selling. Nothing in the logs says "the breaker opened because tickets sold out"; it reads as a
catalog outage.

The `@Retry` makes it worse in the meantime: each sold-out request is retried against a tier that
will still be sold out.

And had the NPE not occurred — had the empty simply propagated —
`ReservationServiceImpl.takeInventory` acts on the refusal inside `.flatMap(result -> …)`, which
does not run on an empty source. `concatMap` would move to the next selection and `.then()` would
complete successfully: **a reservation created against inventory nobody reserved.** The NPE was
accidentally protecting the platform from overselling.

### What was done

`exchangeToMono` replaces `retrieve().onStatus(...)`, because the distinction it draws is the whole
point of the method:

| Status | Meaning | Behaviour |
|---|---|---|
| **4xx** | a business answer — "sold out" is the platform working | returned as `success: false`; **never** reaches the breaker |
| **5xx** | an infrastructure failure | raised, so the breaker counts it and the fallback refuses — failing closed, because reserving against unconfirmed inventory is how a venue oversells |

A 4xx whose body will not parse now becomes a refusal rather than an empty, so an unreadable
response cannot become a reservation either.

### Evidence

`SoldOutInventoryTest` — three cases against a real WireMock catalog: the 409 refusal reaches the
caller as `success: false`; a 503 still raises so the breaker sees genuine outages; the happy path
still reserves. **Written before the fix and watched failing**, which is how the NPE was found
rather than reasoned about — the behaviour turns on whether an empty Mono from an `onStatus`
handler suppresses the error, and that is exactly the kind of framework detail this corpus has
guessed wrong about before. Mutation-verified: making 4xx raise like 5xx fails the sold-out case
and nothing else.

### The general point

This was found by following [VERIFICATION.md §1.2](VERIFICATION.md) — read a §4 claim, grep for the
mechanism — from a **schema ownership registry** into a REST client, which is not where the trail
looked like it was going. §4 said booking extends `TicketTier` with `availableQuantity`; it does
not, and asking *how booking gets that number instead* led here.

Two rules earn their place from this one. **An empty Mono is not a benign "nothing to report"** —
downstream `flatMap`, `doOnSuccess` and `filter` all silently skip, so an empty is how a refusal
becomes a success. And **a business refusal must never reach a circuit breaker**: the breaker exists
to shed load from a failing dependency, and feeding it ordinary domain outcomes converts a busy
day into an outage.

---

## F-025 · Eighty-one paged queries had no maximum page size

**Found** 2026-09-02, verifying [`ET-PLT-004`](tasks/ET-PLT-004.md) R5.
**Severity** availability — one request could pull an entire collection.
**Status** **closed 2026-09-02.**

### What was verified

R5's last acceptance: *"Every paged query has a maximum page size enforced server-side, and a
request above it is refused rather than silently clamped."*

The mechanism exists. `com.pml.shared.graphql.PageSize` defines `MAX = 100`, refuses above it with
`PAGE_SIZE_EXCEEDED`, and carries a careful javadoc explaining why it refuses instead of clamping.

**Three of seven pagination input types called it.** The other four returned the client's number:

```java
public OffsetPaginationInput { if (size == null) size = 20; }
public int getLimit() { return size; }        // whatever was asked for
```

All three copies of `OffsetPaginationInput` were in that group — and **81 of the 137 paged fields
across the three subgraphs take `OffsetPaginationInput`**. So `pagination: { page: 0, size: 1000000 }`
became `limit(1000000)`, against MongoDB, on any of them.

Which ones matters. The list includes `payoutRequests`, `auditLogs`, `transactionsForReview` and
`stuckTransactions` — operator tables over the largest collections the platform holds. A single
authenticated request could pull the whole finance history into one response, and the cost is paid
by the database and the router before anything notices.

### The shape, again

Seven types doing one job; the rule written once, on the type somebody was thinking about; the
copies added later by people who assumed the guard was elsewhere. That is
[VERIFICATION.md §1.1](VERIFICATION.md)'s sibling comparison, and it is now **five for five**:

| Spec | The siblings that enforced | The one that did not |
|---|---|---|
| ET-CAT-001 | a dozen list queries | `event(id)` — F-007 |
| ET-TKT-001 | `cancelReservation`, `myActiveReservations` | `reservation(id)` — F-010 |
| ET-ORG-002 | `organizationMembers` | `organizationMember(orgId, userId)` — F-011 |
| ET-PLT-012 | five modules inheriting a compiler config | `keycloak-extensions` — F-020 |
| **ET-PLT-004** | **`PageableInput`, catalog's `CursorPaginationInput`** | **four other pagination inputs — this** |

F-020 generalised it past collections to build configuration. This one generalises it again: the
siblings need not even be in the same service. Three copies of one record, in three modules, and
the enforcement lived in only one of them.

### What was done

All four unenforcing inputs — three `OffsetPaginationInput`, two `CursorPaginationInput` (five
files) — now route through `PageSize.require`, which **refuses** above the ceiling. Deliberately not
a clamp: a caller silently handed 100 of the 1000 rows it asked for sees a full page and concludes
it has read everything, which is worse than an error because it is invisible on both sides.

`PagingBoundLintTest` scans every pagination input type for the call, with comments stripped — this
codebase has twice had a lint report a documented-but-unenforced rule as compliant because the
documentation named the mechanism. Mutation-verified by reverting one copy.

### Also corrected here

§4's paging registry did not list `provinces`, `categories`, `citiesWithEvents` or
`currentUserPermissions`, so R5's "every list-returning field is a row of this registry" was false —
about the **spec**, not the code. All four are correctly implemented as bare bounded lists (Zambia
has ten provinces; a cursor over a constant is ceremony). Four rows added with their bounds stated.

---

## F-026 · `@Version` on a document nothing versions: an organiser could un-sell a ticket

**Found** 2026-09-02, verifying [`ET-PLT-002`](tasks/ET-PLT-002.md) R6.
**Severity** correctness on the inventory path — a lost update that oversells.
**Status** **closed 2026-09-02.**

### What was verified

R6's fourth acceptance: *"No code path reads `availableQuantity` into Java, compares it, and writes
it back."* Two did.

**The first was dead.** `TicketTierServiceImpl.decrementAvailability` and `incrementAvailability`
did exactly that, and had **zero callers** — none by name, none by method reference, none in tests.
The live purchase path is `InventoryServiceImpl`, which moves inventory with a single
`findAndModify`: an `$expr` filter asserting `availableQuantity - reservedQuantity >= n`, and
`$inc` for the move. That is precisely the shape R6 asks for, and it is correct.

So the platform carried two implementations of inventory, side by side, differing on the one
property that decides whether a venue oversells — and the unsafe one was unreachable. Being dead is
what made it harmless today and what made it worth removing: the next person adjusting a count
would have found two methods, no indication which was safe, and the shorter one reads better.

**The second was live, and it is the finding.** `updateTier` — an organiser editing a tier —
adjusted capacity in the same read-modify-write as every other field:

```java
int diff = input.quantity() - tier.getQuantity();
tier.setQuantity(input.quantity());
tier.setAvailableQuantity(tier.getAvailableQuantity() + diff);
```

### Why `@Version` did not save it

`TicketTier` carries `@Version`, so this looks protected. It is not, and the reason is worth stating
plainly:

> **`findAndModify` does not bump the version field.** Spring Data's optimistic lock is applied by
> Spring Data. `InventoryServiceImpl` writes through `ReactiveMongoTemplate.findAndModify` with
> `$inc`, which is exactly what it should do — and which leaves `version` untouched.

So the lock covers every field *except* the ones two writers actually contend for. The sequence:

1. Organiser opens the tier: `version 5`, `availableQuantity 50`.
2. A sale commits: `$inc availableQuantity: -1, soldQuantity: +1` → `availableQuantity 49`,
   **`version` still 5**.
3. Organiser adds 10 seats and saves. Version check passes. Written:
   `availableQuantity = 50 + 10 = 60`.

The sold seat is back in the pool. Inventory now overstates by one and the platform will sell that
seat again — to a second person, with a valid ticket, for the same physical chair.

Adding capacity while an event is selling is not an exotic scenario; it is what an organiser does
when a tier is going well, which is when a sale is most likely to be committing at that moment.

### What was done

- Both dead methods deleted, from the impl and the interface, with a note at the interface saying
  why they are gone.
- `updateTier` moves capacity through `applyCapacityChange`: one `findAndModify` with
  `$inc quantity` and `$inc availableQuantity` by the same delta. Raising capacity by ten adds ten
  sellable seats whatever happened to the count in between, because nothing is read into Java.
- `InventoryWriteShapeLintTest` bans the shape outright — a setter fed by arithmetic on its own
  getter, on any of the three inventory counters, in any service. Comments stripped, self-checked
  against the exact line that was removed and the atomic form that replaced it.

### Two lints caught this work, both correctly

`InventoryWriteShapeLintTest` found the `updateTier` instance **after** I had already removed the
dead pair and believed the file clean — I had read the two obvious methods and stopped.
`TenantBoundaryLintTest` then refused the new atomic query for having no tenant in it: authorised
upstream by `tierVisibleToCaller`, but D-20 is explicit that the filter and the check work together
and neither replaces the other. `organizationId` added. It then demanded the budget fall from 26 to
24, the two dead lookups having gone.

### The general point

**Mixing Spring Data's optimistic locking with direct `findAndModify` on the same document silently
disables the lock.** Nothing warns. The document still carries `@Version`, the field still exists,
saves still check it — and every write that matters goes around it. Any document that is both
`@Version`-annotated and updated through the template is worth this question, and the answer is not
visible from either file alone.

---

## F-027 · The outbox had a stager and a publisher and nothing in between

**Found** 2026-09-02, verifying [`ET-PLT-003`](tasks/ET-PLT-003.md) R1.
**Severity** correctness — the guarantee the spec's central mechanism exists to give was not being
given by anything. **Status** **closed 2026-09-02** for the first publisher; the rest is wiring.

### What was verified

R1: *"a write and its publication commit together, and survive a crash."* The platform has a
transactional outbox built for exactly this, and it is good code:

- `Outbox` — stages an envelope inside the business transaction, claims rows with a conditional
  `findAndModify`, marks them `SENT`, releases a failed claim back to `PENDING`, reclaims stale
  ones. Proven on a replica set by `OutboxTest`.
- `EventBridge` — hands one envelope to Service Bus with the session key §4's Ordering column
  implies. Its javadoc reads: *"A service stages an envelope inside its transaction and returns.
  **The drain calls this.**"*

**There was no drain.** No class polled the outbox, nothing called `Outbox.drain`, and
`grep -r "outbox.stage"` across all production source returned nothing. Both halves complete, no
shaft between them.

So every publisher in the platform still did this:

```java
boolean sent = streamBridge.send("organizationOutput-out-0", event);
if (sent) log.info(...); else log.warn("Failed to publish ...");
```

Thirteen sites. The write is durable, the send is best-effort, and a WARN line is the only evidence
a message was lost. Nothing retries, because nothing recorded that it should — which is the exact
sentence `Outbox`'s own javadoc uses to explain why it exists.

### What made this hard to see

Everything was green and every gate row was honest. `OutboxTest` passes; the mechanism really is
atomic. The gate said *"true of `Outbox`, empty of the platform"* and *"the mechanism holds, no
service uses it"* — accurate, and easy to read as a small remaining task rather than as *the
central requirement is not met anywhere*.

`EventPublicationLintTest` had it too, in a `NOT_YET_WIRED` allowlist naming five orphaned
mechanisms. The corpus knew. What it lacked was the missing component.

### What was built

- **`OutboxDrain`** — reclaims abandoned claims first, then publishes a bounded batch oldest-first.
  Reclaim before claim, because a row stuck in `PUBLISHING` from a dead process is invisible to the
  claim filter: not lost, never sent, and its status says somebody is handling it. Bounded batch so
  a backlog does not produce a pass that runs for minutes holding claims. Overlapping passes are
  safe rather than merely tolerated — the claim is a conditional update, so exactly one drain wins
  any row.
- **`OutboxAutoConfiguration`** — one `Outbox`, one `EventBridge`, one drain per service, from two
  properties with **no defaults**. A guessed collection would give a misconfigured service a working
  outbox pointed where no drain looks: the failure the outbox prevents, reached by another road.
- **The first real publisher.** `OrganizationServiceImpl.suspend` read
  `save(org).doOnSuccess(suspended -> streamBridge.send(...))` and now stages
  `IDENTITY_ORGANIZATION_SUSPENDED` inside its transaction. A suspended organisation no other
  service hears about keeps selling tickets.

### Evidence

`OutboxDrainTest` — 7 cases on a replica set: publish and mark, a failing bus leaving the row owed,
an abandoned claim reclaimed, a **fresh** claim not stolen, no double-send, a bounded batch draining
over passes, oldest first. `SuspensionOutboxTest` — 2 cases, the load-bearing one being **rollback**:
no document change and no envelope. Mutation-verified by moving the stage outside the transaction,
which produces an envelope announcing a suspension that never happened.

### Three lints caught this work, all correctly

`EventPublicationLintTest` detected the new wiring and **demanded the allowlist be tightened** —
"these are wired now, remove them so the gain is locked in". It then refused a stale `EventBridge`
entry until it carried the right reason: `EventBridge` is reached from the drain, in shared-library,
which the scan deliberately ignores — a *service* referencing it directly would be the defect.
`ConsumerRetryLintTest` caught a **duplicate top-level `platform:` key** I introduced by appending
rather than merging — the same YAML mistake I made with `spring:` while closing F-021, caught the
second time by a lint rather than by me.

### What remains, and why it is not this spec's

`ConsumerGuard` and `ConsumerDispatch` are still orphaned. §4 names **14 consumed rows and the
services implement 2**, so wiring a consumer belongs to the spec that owns it. Twelve publishers
still send directly; each conversion is small now that the drain exists, and each belongs to its own
capability.

### The general point

**A mechanism with no caller is indistinguishable from a finished one, from inside its own tests.**
`OutboxTest` builds an `Outbox` directly and proves it atomic — and would keep passing forever with
no service on it. The question that finds this is not "is it tested" but *"who calls it"*, and it is
worth asking of every well-built shared component in this corpus.

---

## F-028 · Two error handlers re-throw for a retry that does not exist

**Found** 2026-09-02, closing [`ET-PLT-003`](tasks/ET-PLT-003.md)'s consumer rows.
**Severity** one is covered, one is **not** — a chargeback can leave a valid ticket in circulation.
**Status** **closed 2026-09-02.** Comments corrected, and the missing chargeback sweep built —
`ChargebackRecoveryService` reports any chargeback whose ticket is still usable five minutes after
it was recorded. It reports rather than repairs: invalidating a ticket moves money-adjacent state
and restores inventory to catalog, and doing that unattended is a larger decision than closing the
visibility gap. Detection is what was missing. Five layer-2 cases, both bounds, mutation-verified —
dropping the grace window and widening the status set each fail exactly their own assertion.

### What was verified

[F-019](FINDINGS.md) found `CLAUDE.md` documenting a Spring Modulith runtime that has never existed
here. The same fiction is in **ten production files**, and in two of them it is load-bearing — it is
the stated reason for a `throw`:

```java
// PaymentEventListener
// Rethrow so Modulith leaves the publication incomplete and retries it.

// ChargebackEventListener
// Re-throw to trigger Modulith retry - ticket invalidation is critical
```

Both sit in `@TransactionalEventListener(AFTER_COMMIT)` methods. Neither retry exists.

### The two cases are not the same, and saying so matters

**Payments are covered.** `PurchaseRecoveryService` sweeps every 60 seconds and confirms any
reservation still `HELD` whose payment intent `SUCCEEDED` — its own javadoc names the case as *"the
buyer paid and the crash ate the confirmation"*. So the net under that throw is real; the comment
simply credits the wrong thing. I had begun writing this up as a lost-payment defect and it is not
one — checking before claiming is the F-007 lesson, and it applied here.

**Chargebacks are not.** There is no chargeback recovery sweep anywhere in booking. So when ticket
invalidation fails after a chargeback:

1. The chargeback is recorded — that write already committed.
2. The invalidation throws, from an after-commit listener.
3. `log.error("CRITICAL: ...")` is written.
4. **Nothing retries.**

The cardholder has their money back and a ticket that still scans at the gate. On a ticketing
platform that is the shape of chargeback fraud, and the only signal is a log line labelled CRITICAL
that nobody is paged on.

### Also corrected: a design decision justified by fiction

`PurchaseServiceImpl` explained its use of `TransactionalOperator` over `@Transactional` as avoiding
"Spring Modulith's JDBC transaction manager, which is `@Primary`" binding money-moving code to
Postgres. No such manager exists. **The conclusion is right and the reasoning is invented**, which
is the more dangerous combination: it survives review because the code is correct, and it misleads
whoever next tries to simplify it. Rewritten with the actual reason — `@Transactional` on a method
returning `Mono` demarcates the assembly of the chain, not its subscription.

Its `publishPurchased` javadoc also claimed "Spring Modulith writes the publication row with the
same commit, so the event cannot be lost". No row is written; the event is an in-memory
`ApplicationEventPublisher` publication and a process dying between commit and listener loses it.
What covers that gap is, again, `PurchaseRecoveryService`.

### The general point

F-019 treated the stale Modulith documentation as a hazard because *a future session might build
what it describes*. This is the same defect one step worse: engineers have already **written code
whose correctness argument depends on it**. A comment that explains why a `throw` is safe is not
documentation — it is part of the reasoning, and when it is wrong the code is wrong in a way no test
detects, because the code compiles, runs, and does exactly what it says.

Worth a rule: **when a comment gives the reason a failure is safe, that reason is a claim to
verify** — the same as any acceptance box.

---

## F-029 · Any signed-in account could read any organizer's KYC document

**Found** 2026-09-02, Phase 1 — the cross-cutting security sweep, first pass.
**Severity** OWASP A01:2021 / CWE-639 (IDOR) on identity-verification data.
**Status** **closed 2026-09-02.**

### What was verified

```java
@DgsQuery
@PreAuthorize("isAuthenticated()")
public Mono<VerificationDocument> verificationDocument(@InputArgument String id) {
    return documentService.findById(id);
}
```

`documentService.findById` is `documentRepository.findById`. There was **no scoping at any layer** —
not the resolver, not the service, not the repository, not the query.

`VerificationDocument` is organizer KYC. Its `documentType` values are `ID_DOCUMENT`,
`BUSINESS_LICENSE`, `TAX_CERT`, and the type exposes `fileName`, `mimeType`, `status`,
`rejectionReason`, `verifiedById` — and `documentUrl`, the storage location of the file itself.

**The reach, stated carefully.** Any account that can authenticate could read the full metadata of
any organizer's verification document by id. Whether `documentUrl` also retrieves the document
depends on the S3 bucket policy, which is not in this repository — so this is a confirmed metadata
disclosure and a *possible* document disclosure, and it should be checked against the bucket
configuration before anyone concludes otherwise. An account on this platform costs a phone number.

### The sibling comparison, six for six

Three methods below it in the same file:

```java
@PreAuthorize("hasRole('ORGANIZER')")
public Flux<VerificationDocument> myVerificationDocuments(...)   // resolves the caller's orgs, filters to them
```

The list query scoped. The by-id read beside it did not, and always had not. That is now **six for
six** on [VERIFICATION.md §1.1](VERIFICATION.md), and the mechanism is the same every time: the
filter is written once, on the operation somebody thought about, and the by-id lookup is added later
by someone who assumes the guard is elsewhere.

### What was done

- Scoped through `CurrentTenantScope` — `scope.platformAdmin() || scope.permits(organizationId)` —
  which is the pattern `organizationMember` already used, rather than a new one.
- New registry code `DOCUMENT_UNKNOWN` (§4 row added first, since `ErrorCodeRegistryTest` reads the
  spec as the authority). A document belonging to another organization and an id that was never
  issued now answer **identically**; anything finer is an oracle for enumerating real document ids.
- `VerificationDocumentScopeTest` — five layer-2 cases: the owner reads it, an outsider cannot, the
  two refusals are indistinguishable in code, message and details, an unaffiliated customer reads
  nothing, and **a platform administrator still reads across organizations** — without that last
  bound, a fix that refused everybody would pass every other test and silently break the approvals
  workbench.
- Mutation-verified: restoring the unscoped behaviour fails exactly `Outsider` and `Unaffiliated`
  while `Owner` and `Administrator` still pass.

### How the sweep found it

Not by reading the file. The `my*` surface was swept first — **35 queries across three services,
one of which takes an identity argument (`myActiveReservations(userId:)`), and it is correctly
guarded** by `#userId == authentication.principal.subject`. Then by-id query resolvers were
classified by whether any guard was visible: **booking 48 of 48 guarded**, catalog's eight
unguarded all public reference data, and identity eight — of which this was one.

Booking's record is worth noting rather than passing over: forty-eight by-id reads, every one
guarded. The defect is not distributed evenly, and identity is where the remaining sweep should
concentrate.

### Still open from this pass

Seven other identity by-id resolvers show no visible guard, including `user(id)` and
`eventAccessGrant(id)`. Each needs the same treatment as this one — read it, decide who may see it,
and make the refusal indistinguishable from absence.

## F-030 · A paid deposit never reached ticket issuance — and four broken hops around it

**Found** 2026-09-13, during the process inventory for the Temporal migration (every multi-step path in
booking, catalog and identity traced from its trigger to its last write).
**Severity** Money path: a buyer charged with no ticket issued and nothing connecting the money to the
reservation. Cross-service: no catalog lifecycle fact ever reached booking.
**Status** **fixed 2026-09-13** (Phase 0 of the Temporal refactor). Verification recorded below.

### What was verified

Five defects. Each hop read correctly on its own.

1. **The deposit could not be correlated, and nothing marked it paid.** `initiatePayment` sent
   `UUID.randomUUID()` to PawaPay as the deposit id and stored nothing. The deposit webhook called
   `PaymentAttemptService.processWebhook`, which looks up a `PaymentAttempt` this path never creates.
   `markSucceeded` was reachable only from `handlePaymentCallback` and `checkPaymentStatus` — neither
   had a caller — and the `PaymentCompletedEvent` they would have raised went to an
   `@TransactionalEventListener(AFTER_COMMIT)` that a reactive transaction does not trigger. No intent
   ever reached `SUCCEEDED`, so `PurchaseRecoveryService`'s confirm branch never ran either.
2. **catalog published nothing.** The four lifecycle transitions booking acts on raised in-process
   events with no listener, and catalog had no outbox configured. No escrow was ever opened or held
   from a real event, and no cancellation refund could start.
3. **identity's outbox drained to `identity-events-out-0`;** the declared binding is
   `identityEvents-out-0`.
4. **booking's catalog consumer deduplicated on the aggregate, not the message.** Every envelope was
   built as `catalog.EventPublished` keyed on the catalog event's id, so `EventCompleted` for an event
   whose publication had been handled was a "duplicate" and was skipped.
5. **catalog inventory ignored `reservationId`.** A retried release returned seats twice; a release for
   a reservation that held nothing returned another buyer's seats; and booking's confirmation called the
   catalog over HTTP inside its MongoDB transaction, where a later rollback could not undo the sale.

### What was done

- `PaymentOutcomeService` is the only path to a terminal intent (ET-PAY-002 R7). The intent carries a
  `depositId` from creation (new §4 index row, unique partial); a callback is verified against the
  provider's status API (R3); the transition is a compare-and-set with its `booking.Payment*` envelope
  staged in the same transaction; only the writer that made the transition drives confirmation or
  release; a success after the hold ended escalates `PAID_AFTER_EXPIRY`. Transport failures and unmapped
  provider statuses are `PENDING`, never `FAILED` (ET-PAY-001 R2, R3). The webhook answers 200 for
  verified and orphaned callbacks and 503 when verification is unavailable (R6). `PaymentEventListener`
  and the in-memory `PaymentCompletedEvent`, `PaymentFailedEvent` and `TicketPurchasedEvent` are deleted.
- booking and catalog outboxes are on. Confirmation stages one `booking.TicketPurchased` per ticket in
  its transaction; catalog stages `EventPublished`, `EventCompleted`, `EventCancelled` and
  `EventRescheduled` in the transaction that changes the event.
- The consumer binds `EventEnvelope`, routes by wire name, deduplicates on the envelope's own id, and
  asks booking's state per wire name when Redis cannot answer.
- Each tier carries one movement entry per reservation (`HELD`, then `COMMITTED`), moved together with
  the counters in a single conditional `findAndModify` on the tier document. A replayed hold or commit
  matches nothing and is answered from the entry; a release with no entry changes nothing; a release
  after a commit reverses the sale. booking commits before its confirmation transaction, which is safe
  because the commit is now idempotent.
- A first version kept the movements in their own collection and moved them in a multi-document
  transaction with the counters. `InventoryContentionTest` — 200 concurrent holds on one tier — failed
  it: every hold's transaction wrote the same tier document, most aborted on write conflicts, and the
  retries were exhausted. The movement entry moved into the tier document so each movement is one
  atomic update again.
- identity's outbox drains to `identityEvents-out-0`.

### Tests

Against a replica set or Redis started by Testcontainers:
`PaymentConfirmationPathTest` (11), `ConfirmationTransactionBoundaryTest` (4), `CatalogEventRoutingTest`
(3), `InventoryIdempotencyTest` (8), `CatalogLifecycleOutboxTest` (6), and the existing
`InventoryContentionTest` still exact at 50 of 200. Layer 1: `PaymentVerdictTest` (4),
`CatalogEventRouteTest` (2), and `OutboxBindingAlignmentTest` (2), which fails the build if a service's
outbox binding and its declared §4 binding ever diverge again.

### Known limits

- `booking_webhook_receipts` is still not written (ET-PAY-002 R2). Replay safety comes from the
  compare-and-set; the raw-evidence audit trail does not exist yet.
- Orphaned callbacks are logged and answered 200 but not retained for re-matching (R5).
- A hold taken before tiers carried movement entries has no entry, so releasing it is a no-op.
  In-flight holds must be drained before this is deployed to an environment with live reservations.

### How it was found

Not by reading any one file. By asking, for each hop of the purchase chain, *what id does the next hop
receive* and *who calls this method* — [VERIFICATION.md §1.8](VERIFICATION.md). Two of the five hops
were methods with zero callers.

## F-031 · Booking's processes moved onto Temporal — what the move exposed and what it left open

**Scope.** ET-PLT-015 phases 1–4 in booking, with phase 6's deletions. Catalog and identity follow.

### Decisions taken while building

- **Refusals travel as message-prefixed codes.** An exception thrown from an update validator reaches
  the client as an application failure typed by the SDK's own wrapper; the type is lost and the message
  survives. `Refusals.refusal` therefore leads the message with the `ErrorCode` name and the client
  parses it back. Found only by driving the update through a workflow client ([VERIFICATION §1.9](VERIFICATION.md)).
- **Checkout has no legacy flag.** The blueprint's per-event engine flag would have kept the expiry and
  recovery sweeps alive only to be deleted in phase 6. Instead `PurchaseAdoptionRunner` starts an
  adopt-mode `PurchaseWorkflow` for every HELD reservation at boot, so holds created before the
  deployment expire, poll and escalate exactly as new ones do.
- **One open payout per escrow is enforced by the server.** `payout/{escrowAccountId}` under conflict
  policy FAIL; a second request while one is open never runs any code.
- **The reservation id is a name-based UUID of buyer and idempotency key.** A double tap, a reload and
  a retried request all reach one execution and one hold.
- **A payment never enters history with its phone number.** The intent carrying the MSISDN is written
  before the `pay` update, idempotently per reservation; the workflow receives only the intent id.
- **Workflow code never reads its own workflow id.** The first replay test failed `PurchaseWorkflow`: it
  derived the reservation id from `Workflow.getInfo().getWorkflowId()`, which the replayer does not
  reproduce, so the replayed run closed before its first timer. The id now travels in the start and the
  reserve command. Every booking workflow test now replays its own recorded history.
- **Seats return at expiry plus five minutes even with a payment in flight.** A later success is
  escalated for refund by the existing late-arrival path rather than issuing tickets for sold seats.

### What the move removed

`ReservationExpirationScheduler`, `PurchaseRecoveryScheduler`/`PurchaseRecoveryService`,
`PaymentAttemptScheduler`, `FinancialJobListener`, `ReconciliationScheduler`,
`ChargebackRecoveryScheduler`/`ChargebackRecoveryService`, `ChargebackEventListener` with its two
`StreamBridge` sends to an undeclared binding, `WebhookDeduplicationService`, and the bypass methods
that flipped payout, refund and bank-verification status without their process
(`PayoutRequestService.create/approve/reject/process/cancel/complete`, `BankAccountService.verify`,
`PayoutRecoveryService.resumePayoutRequest/bulkRetryFailedPayouts`). Booking now has no `@Scheduled`
method of its own; `StreamBridge.send` in booking is at zero; unscoped tenant lookups fell from 65 to 52.

### Verified

Layer 3 on the time-skipping environment: `PayoutWorkflowTest` (15), `BankVerificationWorkflowTest` (5),
`PurchaseWorkflowTest` (13), `EventFinanceWorkflowTest` (5), `RefundWorkflowTest` (8),
`CancellationRefundsWorkflowTest` (2), `ChargebackWorkflowTest` (6), `ReconciliationWorkflowTest` (1).
Layer 2: `PayoutSettlementServiceTest` (10) and three recovery cases in `PaymentConfirmationPathTest` on a
replica set; `TemporalRoundTripTest` (4) against the Temporal development server, including
Update-with-Start under `USE_EXISTING`. Mutation-verified: the dual-control validator, the escrow
reversal on a failed payout, the payment-in-flight cancel guard, the deadline auto-accept of a
chargeback, and the escrow re-credit on a failed refund.

### Known limits

- The micro-deposit is not journaled: §4 names account `5040` for verification costs, and the chart
  seeds `5040` as Bad Debt Expense. One of the two needs a new code before the expense can be booked.
- ~~A failed refund re-credits the escrow but does not restore a cancelled pending commission;
  `CommissionService` has no reversal for a cancellation~~ Resolved by F-036, for the `PENDING` → `CANCELLED`
  case; the rarer `EARNED` → `CLAWED_BACK` clawback is still not reversed, see F-036's known limits.
- ~~`RefundServiceImpl.processRefund` calls the provider inside its transaction (ET-PAY-001 R7's shape);
  the workflow treats the refund as sent only on a verified answer, but the call has not moved out~~
  Resolved by F-036.
- The escrow's `openDisputeCount` moves by compare-and-set in a transaction, but no layer 2 test drives
  it yet; the gate row stays open.
- `PaymentAttempt` and its mutations remain; the write path for the checkout collect call exists as of
  F-036, refund and payout still do not — see F-036's known limits.
- Search attributes and a payload codec are deferred (ET-PLT-015 §6).

### Catalog (phase 5)

- `EventLifecycleWorkflow` (`event/{eventId}`) owns publish, unpublish, reschedule (capped at three),
  cancel and the completion timer; `EventApprovalWorkflow` (`event-approval/{eventId}`) owns claims,
  the SLA clock that pauses while changes are requested, and one escalation per level. Boot runners
  adopt PUBLISHED and PENDING_APPROVAL/CHANGES_REQUESTED events. The nine in-memory `*Event` classes
  nobody listened to are deleted. 142 catalog tests pass, both workflows replay their own history.
- ET-CAT-001 R6 and ET-ADM-001's sweep table are amended to the workflow timers.
- **Known limits:** ~~the collection validators `approval-escalations-schema.json` and
  `approval-timelines-schema.json` already disagree with their Java models, so writes to those
  collections are rejected in an environment that applies them~~ Resolved by F-035. ~~Cancel does not yet
  ask booking's finance workflow about an in-flight payout~~ Resolved by F-036 (a `BookingServiceClient`
  read, not the finance workflow itself — see F-036). ~~unpublish reads the event's own sold counter
  rather than booking's inventory (ET-CAT-001 R4)~~ Resolved by F-036. ~~approval preconditions (ET-ADM-001 R4) are not
  implemented~~ Resolved by F-036, decision write only — the queue's own display remains open, see F-036.
  ~~"one escalation per level" rests on an exists-check, not a unique index~~ Resolved by F-036 — the
  exists-check remains as a fast path; the index is what actually enforces it now. `EventSubmittedEvent`
  and `EventDecidedEvent` were removed with the publisher, so ET-NTF-002 needs its own trigger — still open,
  see F-036.

### Identity (phase 5)

- `OrganizerOnboardingWorkflow` runs ET-ORG-001 R6's six-step approval with compensation in reverse and
  the step marker as a projection; `OwnershipTransferWorkflow` owns the three-day expiry and retries the
  Keycloak mirror with MongoDB authoritative; `UserSyncWorkflow` answers Keycloak's listener with 202
  after signal-with-start and continues as new; `ReminderWorkflow` replaces `EventReminderScheduler`;
  `NotificationWorkflow` replaces six sends to the undeclared `notificationOutput-out-0`. The group
  mirror repair is a Temporal Schedule starting the dynamic type `GroupMirrorRepair`. Identity's
  `StreamBridge.send` count fell from 11 to 4. 248 identity tests pass; every workflow replays.
- Mirror writes used singular Keycloak group names while the tree is plural, so they changed nothing;
  one `OrganizationGroups` mapping now serves both. The Keycloak write methods that swallowed failures
  gained strict variants for activities, so retries and compensation see the failure.
- The duplicate ownership-transfer path and its `transferOrganizationOwnership` operation are removed;
  the supergraph was recomposed and `docs/FRONTEND_GRAPHQL_CONTRACT.md` regenerated. Frontend codegen
  must be re-run before a client relies on the schema.
- **Known limits:** boot wiring (dynamic-workflow discovery, the Schedule client) is proven only in the
  test harness, not in a started Spring context; ~~the notifications, event-reminders and
  ownership-transfers collection validators disagree with their models~~ Resolved by F-035. ~~`setEventReminder`
  has no event start time, so reminders fire relative to now~~ Resolved by F-036 — F-035's note above that
  this was "already done" and "not built as part of F-035" was checked against the file at a moment when a
  concurrent fork's edit to it had not yet landed or had since been reverted; verified wrong by reading
  `ReminderProcess.set` fresh (it still computed `clock.millis() + minutesBefore`) immediately before fixing
  it. See F-036 for the actual fix. Keycloak's listener sends no event id, so one is derived
  from user, type and timestamp; ET-ORG-001's kill points are proven under time skipping but not by killing
  a worker against a running server.

## F-032 · The last processes outside Temporal, and a spec corpus that still described sweeps

**Scope.** The pass after ET-PLT-015 phases 0–6, on the decision that Temporal is the platform's core
workflow engine (D-21): code in booking and identity; every spec, `spec.yaml`, task file and template;
`CLAUDE.md` and `docs/`.

### What still ran outside the engine

- **Booking kept a second payment lifecycle as GraphQL mutations.** `initiatePaymentAttempt`,
  `retryPaymentAttempt`, `processPaymentWebhook`, `verifyPaymentWithGateway`, `markPaymentFulfilled`,
  `cancelPaymentAttempt`, `pollPendingPayments` and `expireTimedOutPayments` moved `PaymentAttempt`
  statuses beside `PurchaseWorkflow`, against a simulated provider call, and nothing in the frontend
  called them. Retired from the schema, the resolver and the service; operator notes and review status
  remain.
- **`recoverChargebackFunds` recorded a recovery from any fund source outside `ChargebackWorkflow`**,
  whose waterfall already recovers or writes off the whole amount — a second call recovered twice.
  Retired with `RecoverChargebackInput` and `ChargebackService.recoverFromSource`.
- **`resolvePayoutIssue` could repoint a payout at another, unverified bank account** beside
  `PayoutWorkflow`. The argument is gone; the resolution is an annotation, and `resolvedBy` comes from
  the token instead of `null`.
- **Four in-memory events had no listener anywhere** — `EscrowCreditedEvent`, `RefundCompletedEvent`,
  `CommissionEarnedEvent`, `JournalEntryPostedEvent` — and two more were never published. Deleted, with
  `ApplicationEventPublisher` in four services.
- **Identity sent four undeclared events directly.** `UserRegisteredEvent` twice, carrying email and
  phone number and guarded by a `registrationEventPublished` claim; `UserRoleChangedEvent` to a binding
  nothing declared; and an uncalled `OrganizationCreatedEvent`. None was an ET-PLT-003 registry row and
  none had a consumer, so they were removed rather than moved, with the claim field and the
  `userOutput-out-0` binding. `EventPublicationLintTest` is at zero in every service.
- **The full Keycloak re-sync was fire-and-forget** from GraphQL and REST: it answered `true` or `202`
  before any work, ran four batches concurrently and logged failures. It is now `UserBackfillWorkflow`
  (`user-backfill`, one page of 100 per run, continued as new), which hands each user to their own
  `UserSyncWorkflow` so a backfill and a live change are applied by one writer; the nightly
  `identity-user-reconciliation` Schedule starts it at 03:30 UTC for ET-IDN-002 R4. A scheduled run has
  no id in its fixed arguments, so it takes one from `Workflow.currentTimeMillis()`.
- **Document verification status was set by a detached `.subscribe()`.** It is part of the upload and
  approval chains.

### The corpus

Every capability spec still described its timers as sweeps under `lock:sweep:*` Redis keys, its
in-service steps as module events, and its long processes as sagas — so the next agent building from
them would have rebuilt what phases 0–6 removed. Rewritten, greenfield: ET-TKT-001 wholesale;
ET-PLT-003's two tiers became facts between services plus workflow steps within one, with only the
drain reaching the bus; CONVENTIONS §0–§3, §8–§11; ET-PLT-001, 002, 008, 009, 010, 011, 013, 014;
ET-TKT-002, 003, 004; ET-PAY-001, 002; ET-FIN-001 to 005; ET-CAT-001, 002; ET-ADM-001, 004;
ET-ORG-001, 002; ET-IDN-002, 003; ET-NTF-001, 002. Twenty-six `spec.yaml` files lost their lock keys and
module lists and gained `workflows:` and `schedules:`; both templates carry them; README adds authoring
rule 9; ROADMAP D-03 is amended. `CLAUDE.md` and seventeen documents under `docs/` and `backend/` carry
the change, and `docs/architecture/DURABLE_EXECUTION.md` is the orientation page.

The ET-PLT-015 §4 registry gained twenty-one rows declared ahead of their implementation — erasure,
data export, audit maintenance, queue admission, ticket expiry and transfer, webhook orphans, three
rollups, three migrations, device pruning, mass sends and organizer digests — each on a queue that
already exists, because the lint checks every workflow in the tree against the registry and the queues
exactly.

### Decisions

- **ET-PAY-001 R5 follows the built purchase path.** An escalated payment no longer holds its seats:
  they return at expiry plus the seat grace, polling continues, and a late success is escalated as
  `PAID_AFTER_EXPIRY` for refund ([F-031](#f-031--bookings-processes-moved-onto-temporal--what-the-move-exposed-and-what-it-left-open)).
- **Payment-provider reconciliation and ET-FIN-005's provider run are one Schedule**, `recon-provider`.
- **The group-mirror Schedule's id is `identity-group-mirror-repair` everywhere**; three specs had
  shortened it.

### Verified

All four suites green on 2026-09-13, against Testcontainers MongoDB and Redis: shared-library 371,
booking 209, identity 258, catalog 142. New: `UserBackfillWorkflowTest` (7 cases under time skipping —
paging across Continue-As-New, an exact multiple of the page, an empty realm, a Keycloak outage retried,
a scheduled run naming itself, ids, and a replay of the recorded history) and
`UserReconciliationScheduleTest` (3). `ReservationTtlOrderingTest` now asserts the TTL clears the
workflow's seat grace. The spec-parsing lints — `TemporalRegistryLintTest`, `EventEnvelopeTest`,
`SessionKeyTest`, `ServiceBusTopologyTest`, `EventNameClosureTest`, `SpecStatusLintTest` — pass over the
rewritten corpus, and every `spec.yaml` parses. Ratchets lowered to the measured counts: identity
package-info 15 → 14 (the deleted event package), booking unscoped lookups 52 → 51, booking
unconstrained inputs 22 → 21. The supergraph is recomposed, `docs/FRONTEND_GRAPHQL_CONTRACT.md`
regenerated and frontend codegen re-run; no frontend source referenced a removed operation.

### Known limits

- ~~ET-FIN-005 R2 requires internal reconciliation hourly; the built Schedules were daily.~~ Resolved by
  F-033 (ROADMAP D-24).
- ~~Reconciliation does not mark documents whose Keycloak user is gone.~~ Resolved by F-033: ROADMAP D-29
  decides it never should, and ET-IDN-002 R4 now says so.
- ~~The mutation and endpoint admit `ADMIN` to a full reconciliation.~~ Resolved by F-033 (ROADMAP D-30).
- The Schedule runners are proven by tests of the Schedule definitions, not against a running server —
  except booking's reconciliation runner, which F-033 tests against the development server.
- ~~`PaymentAttempt`, its statuses and the repository queries that served polling and expiry remain as a
  read model with no writer of their lifecycle.~~ Partially resolved by F-036: the checkout collect call
  now writes and updates the row; the polling and refund/payout paths still do not.
- The Temporal Flow Atlas artifact predates `UserBackfillWorkflow` and the reconciliation Schedule.


## F-033 · The business decisions D-22 to D-30, built and specced

> **Superseded 2026-10-03 (annotation; history below is unchanged).** This entry's Temporal Cloud material is superseded by D-28 (self-hosted, 2026-09-18) and F-037; the cited `docs/operations/TEMPORAL_CLOUD_SETUP.md` was removed; see `docs/operations/TEMPORAL_SELF_HOSTED.md`. The staging-first order (D-33) still stands, now for the self-hosted service; the trial has not been run (OI-05).

The business questions raised by the move to Temporal were put to the product owner on 2026-09-14 and
answered. Each answer is a ROADMAP decision, and the code and specs now agree with it.

| Decision | What changed |
|---|---|
| D-22 late payment | Nothing: seats are released after the 5-minute grace and late money is refunded, as built |
| D-23 no payout fee | `PayoutRules` fee functions and `platformFee`/`processingFee` removed from the model, validator and GraphQL; the settled amount is the full balance |
| D-24 hourly reconciliation | Schedules at :05, :20 and :35 UTC plus the weekly summary; `ReconciliationSchedules` moves an existing Schedule to the code's cadence and keeps its pause; ET-FIN-005 §4 names the built Schedules |
| D-25 chargeback escalation | `ChargebackWorkflow` alerts finance 24 h before the deadline (`escalate`, `escalatedAt`), then accepts at it |
| D-26 refund approval | Only a cancellation's refund approves itself; `RefundWorkflow` escalates a waiting request at P2D and P5D (`escalateReview`, history `REVIEW_ESCALATED_{n}`); the K1,000 limit and the unused `auto-approve-threshold` property are gone; ET-FIN-004 R5, ET-ADM-002's key and the tasks rewritten |
| D-27 verification cost | Account `5050 Account Verification Costs` seeded (the seeder now creates missing standard accounts at every boot); `BankVerificationWorkflow` books each sent deposit once |
| D-28 Temporal Cloud | `prod` profile in all three services: `TEMPORAL_ADDRESS`, `enable-https`, `TEMPORAL_API_KEY`, `TEMPORAL_NAMESPACE`; ET-PLT-015 §4, ET-PLT-001 and ET-PLT-002 updated |
| D-29 Keycloak-gone users | Left as they are; ET-IDN-002 R4 rewritten to say so |
| D-30 full re-sync | `SUPER_ADMIN` only on the mutation and the REST endpoint |

### Verified

All four suites green on 2026-09-14, against Testcontainers: shared-library 371, booking 213, identity 258,
catalog 142. New: a chargeback escalation case, a refund escalation case (a K50 refund also now waits for a
person), an hourly-cadence rule test, and `ReconciliationSchedulesTest` (2, layer 2), which runs the boot
runner against the Temporal development server and asserts from the server's next action times that
every Schedule fires on its cadence and that a daily Schedule becomes hourly while keeping a pause. Every
changed workflow's recorded history still replays. `PageSizeTest` caught a `Math.min` bound on the
escalation level; it is now a refusing `RefundRules.reviewEscalation`. The supergraph is recomposed,
`docs/FRONTEND_GRAPHQL_CONTRACT.md` regenerated and frontend codegen re-run; no frontend source read the
removed payout fields.

### Known limits

- ~~A deposit the provider later fails is not reversed.~~ Resolved by F-034 (ROADMAP D-31).
- ~~There is no separately addressed finance lead.~~ Resolved by F-034 (ROADMAP D-32).
- A Temporal Cloud namespace is configured, not provisioned, and the prod profile has not booted against
  one. F-034 records the order: staging first (ROADMAP D-33).
- ~~Identity's runner leaves an existing Schedule unchanged at boot.~~ Resolved by F-034 (ROADMAP D-34).

## F-034 · The four limits F-033 left open, decided and built (D-31 to D-34)

> **Superseded 2026-10-03 (annotation; history below is unchanged).** Superseded by D-28 (self-hosted, 2026-09-18) and F-037; the cited `docs/operations/TEMPORAL_CLOUD_SETUP.md` was removed; see `docs/operations/TEMPORAL_SELF_HOSTED.md`. The staging-first order (D-33) still stands, now for the self-hosted service; the trial has not been run (OI-05).

The product owner answered the four open limits on 2026-09-14.

| Decision | What changed |
|---|---|
| D-31 failed test deposit | `BankVerificationWorkflow` asks PawaPay what became of the deposit (30 s, doubling to hourly) while the owner has not confirmed it. A confirmed failure reverses the `5050` entry once (`AccountingService.reverseVerificationDeposit`) and returns the account to `PENDING`; an unanswered deposit keeps its cost |
| D-32 finance lead | New `FINANCE_LEAD` role (Keycloak realm, `UserType`, identity GraphQL enum and validator), granting nothing and held with `FINANCE`. Booking's `FinanceEscalations` copies the finance channel, emails every lead using identity's `GET /api/internal/finance-leads/contacts`, then asks identity's `POST /api/internal/finance-leads/notifications` to WhatsApp each lead (templates `finance.chargeback-undecided`, `finance.refund-waiting`, deduplicated per lead and escalation). Chargeback and refund escalations use it. ET-PLT-007, ET-ORG-003, ET-NTF-002 rows 33–34 and ET-FIN-004 updated |
| D-33 Temporal Cloud staging first | `docs/operations/TEMPORAL_CLOUD_SETUP.md` — account, region record, staging namespace and keys, trial of every flow, crash and deploy drills, sign-off, then production. ET-PLT-015 §4 names staging |
| D-34 Schedule cadence from code | Identity's `UserReconciliationScheduleRunner` and `GroupMirrorScheduleRunner` now update an existing Schedule at boot and keep its pause, as booking's reconciliation runner does |

### Verified

All four suites green on 2026-09-14: shared-library 371, booking 218, identity 262, catalog 142. New: two
deposit cases (a confirmed failure reversed once; an unanswered deposit kept) and a poll-delay rule test;
`FinanceEscalationsTest` (2: the order of copy, emails and WhatsApp request; an unreachable identity
service fails the activity); `FinanceLeadNotifierTest` (3); template assertions in `NotificationRulesTest`;
and `UserReconciliationScheduleRunnerTest` (layer 2), which moves a paused 01:00 Schedule to 03:30 UTC on
the Temporal development server and keeps the pause. `WorkflowDeterminismLintTest` refused a record
carrying an email address in a workflow package, so `FinanceLeadNotifier` lives in identity's `service`
package. The supergraph, `docs/FRONTEND_GRAPHQL_CONTRACT.md` and frontend types are regenerated.

### Known limits

- With no active `FINANCE_LEAD` holder an escalation reaches only the finance channel; identity logs a
  warning and nothing alerts on it.
- A retried escalation activity can send the channel copy and the emails again; only WhatsApp is deduplicated.
- The group-mirror runner's update path has no test against a server; the user-reconciliation runner's does.
- Temporal Cloud is still not provisioned; the checklist is the plan, not evidence.

## F-035 · The collection validators F-031 flagged, and what a parity lint found beyond them

> **Note 2026-10-03.** This entry and F-036 contain no Temporal Cloud text; the Cloud references sit in F-033 and F-034 and are annotated there.

**Found** 2026-09-18, closing out the model/validator half of F-031's known limits and the F-002/F-005/F-023
items ROADMAP left open. **Severity** data integrity — an `additionalProperties: false` validator with
`validationAction: ERROR` (every service's default) rejects any write of a field it does not list, silently
in the test fixtures (which use an unvalidated template) and totally wherever the validator is actually
enforced.

### What was verified wrong, and fixed

A new reflection-based lint per service (`CatalogModelValidatorParityTest`, `IdentityModelValidatorParityTest`,
`BookingModelValidatorParityTest`, all `@Tag("L1")`, no database) scans every `@Document` class under the
service's package, resolves its collection from `MongoSchemaValidationConfig.getSchemaDefinitions()`, and
asserts every persisted field (by name, honouring `@Field` and mapping `@Id` to `_id`) is a declared
property of that collection's JSON-schema validator wherever `additionalProperties: false` is set. It is
one-directional on purpose: a validator may describe an optional or historical shape a given model version
does not use, but a model field the validator omits is a write the database will refuse.

Running it found and fixed **17 collections**, not the 5 F-031 named — the validators had drifted from an
earlier, differently-shaped design in every service:

| Service | Collection (validator file) | What was wrong |
|---|---|---|
| catalog | `approval-escalations` | Validator described an entirely different shape (`escalatedBy`, `escalatedAt`, a 4-value status, a closed `reason` enum) than `ApprovalEscalation` actually has (`level`, `triggeredAt`, `escalatedTo`, `remindersSent`, …) |
| catalog | `approval-timelines` | Same: validator was a flat per-event-action document; `ApprovalTimeline` is the aggregate root with an embedded `timelineEvents` array (`TimelineEvent`) |
| catalog | `cities` | Missing `provinceName`, `country`, `eventCount`, `createdAt`, `updatedAt` |
| catalog | `provinces` | Missing `createdAt`, `updatedAt` |
| catalog | `event-categories` | Missing `code`, `color`, `displayOrder`; wrongly required a `slug` the model does not have |
| catalog | `locations` | Validator assumed `cityId`/`provinceId` references and embedded coordinates; `Location` is flat (`cityName`, `provinceName`, `latitude`, `longitude`, …) |
| catalog | `ticket-tiers` | Validator described an embedded sub-document (`totalQuantity`, `saleStartDate`); `TicketTier` is its own collection with `quantity`, `salesStartAt`/`salesEndAt`, `earlyBirdPrice`/`earlyBirdEndsAt`, `movements[]`, `version`, `organizationId`, … — none of which the validator allowed |
| identity | `notifications` | Validator's `type` enum, `isRead`/`createdAt` shape belonged to a different design than `Notification` (`body`, `channels[]`, `status`, `sentAt`/`deliveredAt`/`readAt`) |
| identity | `event-reminders` | Missing `eventStartsAt` (ET-NTF-002 R5 — see below), wrong `status` enum, wrong field name (`reminderDate` vs `reminderAt`) |
| identity | `ownership-transfers` | Validator's field names (`entityType`/`fromUserId`/`toUserId`) did not match `OwnershipTransferRequest` (`organizationId`/`currentOwnerId`/`newOwnerId`/`transferToken`) at all |
| identity | `event-access-grants` | Missing `revokedById`, `customPermissions`, `eventRole`, `organizationId`, `grantedById`, `revocationReason`, `status`, timestamps |
| identity | `notification-preferences` | Missing `reminderHoursBefore`, `whatsappEnabled`, `eventReminders`, `marketingEmails` |
| identity | `team-invitations` | Wrong field names throughout (`invitedEmail`→`email`, `invitedBy`→`invitedById`, missing `phoneNumber`, `inviteeName`, `eventAccessGrants`) |
| identity | `user-devices` | Missing `deviceName`, `createdAt` |
| booking | `bank-accounts` | Missing `verificationStatus`, `microDepositAmount`, `verificationAttempts`, `verificationLockedUntil` (the D-27/D-31 verification fields) |
| booking | `chargebacks` | Missing `disputeOpen` (`escalatedAt` was already present — D-25 had added it correctly) |
| booking | `payment-intents` | Missing `reservationId` and `depositId` — `reservationId` is also the answer to ET-PAY-001 §4's `paymentAttempts(intentId)` reconciliation note below |
| booking | `booking-payout-requests` | Missing `idempotencyKey`, `journalEntryId`, `reversalEntryId`, `failureCategory` (`platformFee`/`processingFee` were correctly already absent — D-23) |
| booking | `promo-codes` | Missing `currency`, `version` |
| booking | `escrow-transactions` | Validator used `id` instead of `_id` for the `@Id` field (so `_id` itself was never an allowed property), a 3-value `category` enum where the model has six `CATEGORY_*` constants, and was missing `escrowAccountId`, `currency`, `chargebackId`, `journalEntryId`, `createdAt`, `version` |
| booking | `booking-reservations` | **The most severe**: `status` still listed the retired `ACTIVE/CONVERTED/CANCELLED` values. `ReservationStatus` was renamed to `HELD/CONFIRMED/RELEASED` plus new `FAILED` before this pass (ET-TKT-001 R6) and the validator was never updated — every reservation write would have been rejected in a validated environment. Also missing `paymentIntentId`, `idempotencyKey`, `subtotal`, `promoCodeId`, `confirmedAt`/`releasedAt`/`failedAt`/`failureReason`, `version` |

`booking_payment_attempts` is **deliberately excluded** from `BookingModelValidatorParityTest` (an explicit
skip, not a pass): its validator requires `paymentIntentId`/`paymentMethod`/`paymentReference`, none of which
exist on `PaymentAttempt` (`reservationId`, `depositId`, `attemptNumber`), and F-031/F-032 already record that
nothing writes this collection yet (ET-PAY-001's "record every provider call before making it" is unbuilt).
Reconciling the validator ahead of that write path would describe a third, still-different guess; it is left
for whoever builds the write path to design validator, model and writer together. Four more collections
(`identity_audit_logs`, `identity_payout_config_audit_logs`, `identity_token_revocations`,
`platform_configuration`) have **no validator registered at all** — the lint reports these as skipped, not
passing, since there is nothing to compare against; adding validators for them is a separate, larger piece of
work than reconciling an existing one.

### F-023 (early-bird price advertised, full price charged) — already resolved on the catalog side

`TicketTier.earlyBirdPrice`/`earlyBirdEndsAt`/`getCurrentPrice(Instant)` already exist on the model; the gap
this pass found and fixed was that the collection's own validator did not allow either field, so an early-bird
tier could not even be **saved**. The remaining half of F-023 — widening `EventSummaryDto.TicketCategoryDto`
and pricing `ReservationServiceImpl.line()` off the injected `Clock` — was left to another line of work in this
session (see Known limits) to avoid two forks editing `EventSummaryDto`/`ReservationServiceImpl` at once.

### Migrations added (ET-PLT-010 `MigrationRunner` framework, idempotent `$unset`/no-op on rerun)

- **`payout-fee-field-cleanup`** (`PayoutFeeFieldCleanupMigrationService`, booking, step after `payout-conformance`
  in `DataMigrationRunner`) — `$unset`s `platformFee`/`processingFee` from any `booking_payout_requests` document
  that still has them (ROADMAP D-23 removed both from the model and validator; historic rows written before D-23
  can still carry them). Does **not** rewrite `settledAmount` on those rows — the historic settled amount is a
  fact about what was actually paid, not a bug to restate.
- **`user-field-cleanup`** (`UserFieldCleanupMigrationService`, identity, in `IdentityMigrationRunner`) —
  `$unset`s `registrationEventPublished` from any `identity_users` document that still has it; publication moved
  onto the transactional outbox and `User` no longer declares the field.
- **No migration needed** for: `ChargebackRecord.escalatedAt` (additive, nullable, D-25 already validator-correct),
  `RefundRequest.history` (its embedded shape already matches the validator exactly — checked field by field),
  chart account `5050` (the seed runner already creates missing standard accounts at boot — D-27), `FINANCE_LEAD`
  (already in `UserType`, the users validator's role enum and the GraphQL enum — D-32; purely additive, nothing
  to backfill).

### F-005 — both divergences resolved, one by correcting where the fix belongs

- **Collections**: `identity_role_permission_changes` is already a row of ET-PLT-002 §4 (line 329) and of
  ET-PLT-013 §4 — verified, not re-added. `CollectionRegistryLintTest.everyDocumentNamesARegistryRow` already
  scans every module's `src/main/java`, i.e. reads the whole corpus rather than one document, which is what
  F-005 asked for.
- **Error codes**: `UNSUPPORTED_SCHEMA_VERSION` is **not** added to `ErrorCode`. ET-PLT-010 §4's own "Error
  codes" section (rewritten since F-005 was filed) says explicitly: *"None introduced. `UNSUPPORTED_SCHEMA_VERSION`
  is ET-PLT-003's dead-letter reason."* `ErrorCode` is a closed, spec-generated enum (`ErrorCodeRegistryTest`
  asserts it equals ET-PLT-005 §4 row for row) and adding a code there that the spec does not list would fail
  that lint, not satisfy F-005. The actual closed registry missing the value was `DeadLetter`'s reason-code
  constants (`RETRIES_EXHAUSTED`, `PERMANENT_FAILURE`) — `DeadLetter.UNSUPPORTED_SCHEMA_VERSION` is now the
  third. No consumer yet performs the version check that would use it (ET-PLT-003's "dead-letters anything
  older than N−1" line): that remains a known limit below.

### F-002 — not resolved; scoped down to what this pass could verify safely

The census `IndexAuthorityLintTest` already performs (a ratchet on annotation counts, `unique+sparse` banned
outright) was already built by earlier work and is unaffected by this pass — no `@Indexed`/`@CompoundIndex`
annotation was added or removed. **Turning `auto-index-creation` off and moving the ~247 surviving annotation
indexes into each service's `*IndexInitializer` registry was not attempted**: F-002's own text is explicit that
this "needs care: an index the annotations create today and the registry does not declare would disappear", and
verifying which of ~247 annotations are redundant with a registry declaration, which are genuinely undeclared
and load-bearing, and which are safe to drop is a multi-day audit in its own right, not a same-session
follow-on to the validator work above. Flipping the flag without that audit risks silently dropping an index a
production query depends on, which the task's own constraints rule out. `ANNOTATION_BUDGET` is unchanged
because the measured counts are unchanged.

### A ratchet this pass tightened

`InputValidationLintTest.UNCONSTRAINED_BUDGET` had drifted stale (measured 13/23 against a budget of 14/24 for
identity/catalog) from `*Input.java` files elsewhere in the tree gaining Bean Validation constraints. Lowered
to the measured values so the next unconstrained input is caught rather than absorbed into slack.

### Verified

All four suites green against Testcontainers on 2026-09-18: shared-library 371, booking 241, identity 280,
catalog 152. New tests: `CatalogModelValidatorParityTest` (10 collections), `IdentityModelValidatorParityTest`
(17, 4 skipped — no validator registered), `BookingModelValidatorParityTest` (19, 4 skipped — 1 deliberate,
3 no validator registered). No shared-library main-code caller of `DeadLetter`'s new constant exists yet, so
no test exercises it beyond compiling; it is a registry addition, not new behaviour.

### Known limits

- ~~ET-PAY-001's `booking_payment_attempts` write path (record every provider call before making it), the
  `RefundServiceImpl.processRefund` provider call still running inside its transaction, and the missing
  commission reversal on a failed refund are all still open~~ Resolved by F-036 for checkout collect and
  the refund transaction/commission gap; refund and payout provider calls still write no attempt row — see
  F-036's own known limits.
- ~~F-023's booking/catalog half (widening `EventSummaryDto.TicketCategoryDto` with early-bird fields and pricing
  `ReservationServiceImpl.line()` off the injected `Clock`) is unresolved~~ Resolved by F-036.
- ~~Catalog's other four known limits from F-031 remain exactly as recorded there: event cancellation does not
  ask booking's finance workflow about an in-flight payout; `unpublishEvent` still reads `Event.soldTickets`
  rather than booking's real inventory (ET-CAT-001 R4); ET-ADM-001 R4's approval preconditions (published tier,
  location, capacity) are not implemented~~ Resolved by F-036, all three — `EventSubmittedEvent`/`EventDecidedEvent`'s
  removal still leaves ET-NTF-002's submission/decision notifications with no trigger; that one remains open,
  see F-036.
- ~~"One escalation per level" (`EventReviewService.escalate`) still races on an `exists()` check rather than a
  unique index~~ Resolved by F-036 — the index is now the enforcement; the exists-check stays as the fast
  path for the non-racing case.
- F-002's actual fix (turn `auto-index-creation` off, move ~247 annotations into the registry) is untouched;
  see above.
- The four collections with no validator at all (`identity_audit_logs`, `identity_payout_config_audit_logs`,
  `identity_token_revocations`, `platform_configuration`) still have none; only collections that already had a
  validator were reconciled.
- `catalog_approval_notifications` has a validator (`approval-notifications-schema.json`) and a schema-map entry
  but no `@Document` model class anywhere in catalog-service — either a dead validator or a missing model;
  not investigated further.
- `DeadLetter.UNSUPPORTED_SCHEMA_VERSION` is a registry entry, not enforcement: no consumer yet compares an
  incoming envelope's `schemaVersion` against the N−1 it supports and dead-letters with this reason, per
  ET-PLT-003's requirement.

## F-036 · The catalog and payment gaps F-031/F-035 left open, closed

> **Note 2026-10-03.** This entry and F-036 contain no Temporal Cloud text; the Cloud references sit in F-033 and F-034 and are annotated there.

**Found and closed** 2026-09-18, continuing the line of work F-035 explicitly left unclaimed: F-023's
booking/catalog half, ET-PAY-001's payment-attempt write path, `RefundServiceImpl`'s provider call inside
its transaction, the missing commission reversal on a failed refund, and catalog's four remaining known
limits (event cancellation vs. an in-flight payout, `unpublishEvent` vs. booking's real inventory, ET-ADM-001
R4's approval preconditions, and the escalation unique index). ET-NTF-002's submission/decision trigger is
still open — see Known limits.

A concurrent fork (a30727ecff727f82c, working the catalog audit) had implemented and then hand-reverted an
F-023 fix in these same files before this pass began; the revert was verified complete (no `earlyBird`/
`priceAt` symbols remained) before the fix below was written fresh, so nothing here depends on that fork's
work surviving.

### F-023 · the storefront and the charge now agree

- `EventSummaryDto.TicketCategoryDto` (shared-library) gained `earlyBirdPrice`, `earlyBirdEndsAt` and a
  `priceAt(Instant)` helper — not a bean getter, so it never reaches the wire on its own; a caller evaluates
  it against its own clock.
- `InternalEventController.tierMirror` (catalog) populates both fields from `Event.EventTicketCategory` only
  while `isEarlyBird()` is true, for both `getEventById` and `getTicketCategory`.
- `ReservationServiceImpl.line()` (booking) now prices with `tier.priceAt(clock.instant())` instead of
  `tier.getPrice()`.
- `EarlyBirdChargeTest` (booking, `L1`) restored: a minute before the window closes prices at the early-bird
  rate, a minute after and at the boundary instant itself prices full, and a tier with no window is
  unaffected. Catalog's own `EarlyBirdPricingTest` (5 cases) was untouched and still passes.
- A pre-existing history-narrating comment on `EventSummaryDto` (naming what was removed and when) was
  rewritten to describe only the DTO's current no-bean-getter rule, since editing the file required it.

### ET-PAY-001 · the checkout collect call is recorded before it is made

The gap was worse than "PaymentAttempt has no writer": the model (`PaymentAttempt`, 746 lines) and its
validator both predate the Temporal move and describe a shape with no link to `PaymentIntent` at all —
F-035 left `booking_payment_attempts` on `BookingModelValidatorParityTest`'s skip list for exactly this
reason. This pass built the write path for the **collect** call only (refund and payout provider calls are
still unwritten — see Known limits) and reconciled model, validator and test together:

- `PaymentAttempt.paymentIntentId` — a new field, the missing link ET-PAY-001 §4's `paymentAttempts(intentId)`
  needs. Indexed through `BookingIndexInitializer`'s `idx_paymentIntentId` (ET-PLT-002 §4 gained the row),
  not `@Indexed` — a bare annotation would have pushed booking's `IndexAuthorityLintTest` budget from 149 to
  150 for no reason; the registry route leaves it unchanged.
- `PaymentAttemptRecorder` / `PaymentAttemptRecorderImpl` (new) — `beforeCollect(intent)` finds-or-creates
  the row by `depositId` (idempotent: a retried submission for the same intent finds the row already
  staged, and a `DuplicateKeyException` from two concurrent submissions is resolved the same way); *
  `afterCollect(depositId, result)` applies the provider's answer using the same three-way read
  `PaymentServiceImpl.recordSubmission` already used — accepted → `PENDING_APPROVAL`, a verified
  `PaymentOutcomeService.Verdict.FAILED` → `markRejected`, anything else (a timeout, a 5xx, an open breaker)
  leaves the row `CREATED` — deliberately reusing `PaymentOutcomeService.verdictOf` rather than
  `PaymentResult.isFailed()` directly, because the latter is true for a bare transport failure too and would
  have recorded a decline the platform has no evidence for (ET-PAY-001 R2).
- `PaymentServiceImpl.initiatePayment` calls `attempts.beforeCollect(intent)` before the gateway call and
  `attempts.afterCollect(...)` on the response, both inside the same activity (`CheckoutActivitiesImpl.startPayment`
  → `PaymentService.initiatePayment`), so the row's existence is the crash-recovery evidence ET-PAY-001
  asks for.
- `payment-attempts-schema.json` rewritten field-for-field against the real `PaymentAttempt` model (roughly
  60 properties; the previous validator described an unrelated shape — `paymentMethod`, a ten-value
  `status` enum, a `fees` sub-object — none of which the model has). `BookingModelValidatorParityTest`'s
  skip for `booking_payment_attempts` is removed; the collection now passes parity like every other.
- Reconciling ET-PAY-001 §4's read side: added `paymentAttempts(intentId: ID!): [PaymentAttempt!]!
  @auth(requires: ADMIN) @tag(name: "admin")`, `PaymentAttemptRepository.findByPaymentIntentIdOrderByCreatedAtDesc`,
  `PaymentAttemptService.findByPaymentIntentId` and the resolver method. The six `paymentAttemptsBy*`
  variants are untouched — collapsing them into the spec's single operation is a bigger, riskier change than
  this pass attempted (see Known limits).
- Verified: `PaymentConfirmationPathTest` (booking, `L2`, real replica set) extended — `submissionUsesTheStoredDepositId`
  now asserts the attempt row exists, links to the intent and reads `PENDING_APPROVAL`;
  `aTimedOutSubmissionStaysPending` asserts the row exists and stays `CREATED` on a transport failure, proving
  the row is not marked declined for an answer that never came. `BookingModelValidatorParityTest` covers the
  validator.

### `RefundServiceImpl.processRefund` · the provider is called after commission and escrow commit, not during

```java
// before: one @Transactional method, provider call included
return handleCommissionAdjustment(refundRequest)
        .then(handleEscrowDebit(refundRequest))
        .then(initiatePayaPayRefund(refundRequest));

// after: the write commits first; only then is the provider asked
return handleCommissionAdjustment(refundRequest)
        .then(handleEscrowDebit(refundRequest))
        .as(transactionalOperator::transactional)
        .then(initiatePayaPayRefund(refundRequest));
```

`@Transactional` came off the method; a `TransactionalOperator` (already the pattern `PaymentOutcomeService`
uses) wraps only the two internal adjustments. `RefundWorkflow`'s verified-answer rule is unchanged — the
workflow still only marks `COMPLETED` on a polled or callback-verified answer, never on the initiation call
returning.

### A failed refund now reinstates the commission it cancelled at initiation, not only the escrow

- `CommissionRecord.reinstate(Instant)` — the inverse of `cancel`: `CANCELLED` → `PENDING`, clearing
  `cancelledAt`/`refundRequestId`/`refundReason`/`cancelJournalEntryId`. No money moved on cancellation, so
  none moves here; this is a pure status reversal, unlike the (rare) `EARNED` → `CLAWED_BACK` case, which
  posted a real journal entry via `AccountingService.recordCommissionClawback` and is **not** reversed here
  — see Known limits.
- `CommissionService.reinstatePendingCommission(ticketId, refundRequestId)` — idempotent (a commission
  already `PENDING` is returned unchanged) and scoped to the refund that caused the cancellation
  (`refundRequestId` must match); an `EARNED`/`CLAWED_BACK` commission is left alone.
- `RefundActivities.reinstateCommission(refundRequestId)` (new activity method) is called from
  `RefundWorkflowImpl`'s `FAILED` branch, immediately after `restoreEscrow`.
- `RefundWorkflowTest` (`L3`): `aVerifiedFailureRestoresEscrow` and `anUnacceptedRefundRestoresEscrow` now
  also assert `reinstatements == 1`.

### Catalog: cancellation now asks booking about an in-flight payout

- `BookingServiceClient` (new, catalog) — two read-only calls, `soldTicketCount(eventId)` and
  `hasOpenPayoutRequest(eventId)`, over the `bookingServiceWebClient` bean that `OAuth2ClientConfig` /
  `WebClientFallbackConfig` already declared but nothing used. Both let a failure propagate rather than
  defaulting to an answer: an unreachable booking-service must refuse the write and let the activity retry
  (ET-PLT-015), not have an outage quietly read as "nothing sold" or "no payout in flight".
- `PayoutRequestService.hasOpenPayoutRequest(eventId)` (new, booking) — `findByEventId(eventId).any(r ->
  !r.getStatus().isFinal())`. `PayoutRequestStatus.isFinal()` is `COMPLETED`/`REJECTED`/`FAILED`/`CANCELLED`;
  a `FAILED` payout has resolved (ET-FIN-003 R7 permits a fresh request from it), so it does not block a
  cancellation. Exposed at `GET /api/internal/payouts/by-event/{eventId}/open`
  (`InternalPayoutController`, new).
- `EventLifecycleActivitiesImpl.cancel` asks `hasOpenPayoutRequest` first and refuses with
  `EVENT_STATE_INVALID` ("the event has a payout in flight...") before calling
  `EventService.cancelEventWithReason` at all — no local write happens on the refused path.
- Verified: `EventLifecycleActivitiesImplTest` (new, `L2`) — `cancellationRefusedWhilePayoutIsOpen` (nothing
  written), `cancellationProceedsWithNoOpenPayout`.

### Catalog: `unpublishEvent` now asks booking for the real sold count

- `TicketService.countSoldByEventId(eventId)` (new, booking) — `countByEventIdAndStatusIn` filtered to
  `TicketStatus.isSold()`'s three states (`ISSUED`, `VALIDATED`, `REFUND_PENDING`), derived rather than
  listed so it cannot drift from the enum, mirroring the pattern `OrganizerDashboardServiceImpl.SOLD_STATES`
  already used for the same reason. Exposed at `GET /api/internal/tickets/sold-count/by-event/{eventId}`.
- `EventService.unpublishEvent(String, long)` — the `soldCount` is now a parameter the caller verifies, not
  `event.getSoldTickets()` (catalog's own display counter, per ET-CAT-001 §4).
- `EventLifecycleActivitiesImpl.unpublish` calls `booking.soldTicketCount(eventId)` and passes the answer
  straight through.
- Verified: `EventLifecycleActivitiesImplTest.unpublishTrustsBookingOverTheLocalCounter` seeds
  `Event.soldTickets = 0` but has the mocked `BookingServiceClient` answer `3`, and asserts the refusal fires
  anyway — the test that would have passed against the old, wrong implementation. `unpublishProceedsWhenBookingReportsNothingSold`
  covers the other side. `EventLifecycleWritesTest`'s two existing unpublish cases were updated to pass the
  verified count explicitly instead of relying on the seeded `Event.soldTickets`.

### Catalog: one escalation per level is now a database constraint

- `catalog_approval_escalations` gained a unique `{eventId: 1, level: 1}` index, declared in
  `CatalogIndexInitializer` (not `@Indexed`, for the same annotation-budget reason as above) and in
  ET-PLT-002 §4.
- `EventReviewService.escalate` **keeps** its exists-check as the fast path — a genuine attempt to catch and
  swallow the resulting `DuplicateKeyException` inside the same reactive chain was tried and reverted:
  MongoDB aborts a transaction server-side the instant a write inside it violates a unique index, and a
  reactive `onErrorResume` inside that same `TransactionalOperator`-wrapped chain cannot un-abort it — the
  subsequent commit fails with `NoSuchTransaction` regardless of what the chain does with the exception. The
  index is still the real guarantee for a genuine concurrent race: the losing transaction aborts, its caller
  (an activity, per ET-PLT-015) retries, and the retry's exists-check then finds the level the winner
  recorded. The exists-check's job is now only to avoid the write attempt in the overwhelmingly common
  non-racing case.
- Verified: `EventReviewServiceTest.concurrentEscalationsAtOneLevelWriteOnce` (new) drives ten concurrent
  `escalate(EVENT, 1, null)` calls and asserts exactly one `{eventId: 1, level: 1}` row survives — the
  property an exists-check alone cannot guarantee and the index does. The test's `@BeforeAll` now ensures
  `CatalogIndexInitializer.specifications()` against the replica set, since (like every other service-level
  test here) it built its `ReactiveMongoTemplate` directly rather than through a Spring context that would
  have run the initializer.

### Catalog: ET-ADM-001 R4's approval preconditions

- `ApprovalRules.approvalPreconditionRefusal(hasPublishedTier, locationId, totalCapacity)` — a pure function
  naming every missing precondition in one message (e.g. "missing a published ticket tier, a location"), not
  just the first.
- `EventReviewService.approve` calls it first, querying `Event` for `locationId`/`totalCapacity` and
  `TicketTier` for `exists({eventId, isActive: true})`; a refusal short-circuits before `decide()` runs, so
  no write happens. The check only runs while the event is `PENDING_APPROVAL` — an idempotent retry of an
  already-approved event is left to `decide()`'s own compare-and-set rather than re-evaluated against a
  tier an organizer might have deactivated since.
- `reject` and `requestChanges` are untouched: R4 requires a reviewer never be trapped, and this pass
  verified it by asserting rejection still succeeds on an event that fails every approval precondition.
- Verified: three new `EventReviewServiceTest` cases, one per precondition, each asserting the refusal names
  the missing item and that nothing was written; a fourth confirms rejection still works when capacity is
  missing. The test's shared `@BeforeEach` fixture was widened to seed a `locationId` and an active
  `TicketTier` — every existing approval-success case in the file was silently relying on preconditions that
  did not yet exist to check.
- **Not built**: the queue's own display of which precondition is outstanding (R4: "the queue row shows
  which precondition is outstanding, so a reviewer does not open an item they cannot decide") — a GraphQL
  field and resolver change this pass did not attempt. See Known limits.

### Verified

All four suites, Testcontainers, 2026-09-18: shared-library 371/371, booking-service 246/246 (3 skipped —
`booking_payment_attempts` is no longer one of them), catalog-service 163/163, identity-service 281/283 (2
errors in `FinanceLeadNotifierTest`, pre-existing and unrelated — `User.java` is mid-edit by a concurrent
session in this tree; not investigated or touched here). The supergraph was recomposed
(`compose-supergraph.sh --static`) and `docs/FRONTEND_GRAPHQL_CONTRACT.md` regenerated after both the
identity schema change (reminder input) and the booking schema change (`paymentAttempts`); frontend codegen
could not be run in this environment (`npm install` fails with an unrelated, pre-existing npm internal error,
`Cannot read properties of null (reading 'edgesOut')`, on a fresh `frontend/web` with no `node_modules`) —
verified instead by `grep` that no file under `frontend/` outside generated type output references
`setEventReminder`, `cancelEventReminder`, `CreateEventReminderInput`, `EventReminder.minutesBefore` or the
booking payment-attempt operations added here.

### Known limits

- ET-PAY-001's provider calls for **refund** (`RefundServiceImpl.initiatePayaPayRefund`) and **payout**
  (`PayoutProviderActivitiesImpl.initiate`/`status`) still make no `booking_payment_attempts` row. Only the
  checkout collect call does. `PaymentAttempt` has no `attemptType` field distinguishing COLLECT/POLL/REFUND
  the way ET-PAY-001 §4 describes; only the collect path was reconciled model-to-writer in this pass.
- Reconciling ET-PAY-001 §4's six `paymentAttemptsBy*` operations against its single `paymentAttempts(intentId)`
  is unresolved beyond adding the latter — D-19's own rule (schema names win over spec names) does not
  settle which existing operations, if any, should be retired.
- `RefundServiceImpl.initiatePayaPayRefund` mints a fresh `pawaPayRefundId` (`PawaPayClient.generateTransactionId()`)
  on every call rather than reusing one already stored for the request; a retried `process` activity would
  contact PawaPay a second time under a different reference. Found while reading this method for the
  transaction-boundary fix; not in this pass's scope to fix.
- A failed refund reinstates a `CANCELLED` (from `PENDING`) commission; it does not reverse the rarer
  `EARNED` → `CLAWED_BACK` clawback, which posted a real journal entry. Reversing that needs a compensating
  entry through `AccountingService`, not a status flip, and was left for whoever next touches commission
  clawback.
- ET-ADM-001 R4's queue-side display of which precondition is outstanding is not built; only the decision
  mutation's refusal is.
- `EventSubmittedEvent`/`EventDecidedEvent`'s removal still leaves ET-NTF-002 rows 31/32 (the approvals-queue
  and submitter notifications) with no trigger — unattempted here; it needs either a new catalog outbox
  event and an identity consumer, or a direct cross-service call from catalog's approval activities into
  identity's notification path, and a decision between those two shapes belongs to whoever builds it.
- F-002's index census (turning `auto-index-creation` off, moving ~247 surviving annotations into each
  service's registry) remains exactly as F-035 left it: not attempted, for the same reason — verifying which
  annotations are safe to drop is a multi-day audit this pass did not have room for either. The two new
  indexes added in this pass (`booking_payment_attempts.paymentIntentId`,
  `catalog_approval_escalations.{eventId, level}`) went through the registry specifically so as not to add
  to that debt.

---

## F-037 · The open items after the Temporal move, closed: refunds, provider evidence, tenancy, permissions, approvals, indexes

**Found and closed** 2026-09-18. Covers, in the order they were done: the switch to self-hosted Temporal
(ROADMAP D-28/D-33 revised), the clean-up of the Modulith-era code and documents, and six phases that
close F-001's read paths, F-002, F-013's vocabulary and settings, and the three limits F-036 left open.
Every phase ended with all four suites green on Testcontainers (MongoDB replica set, Redis, Temporal dev
server); each behavioural fix was verified by breaking it and watching its test fail.

### Refunds could be sent and debited twice

A retried refund minted a new PawaPay refund id, so PawaPay could not recognise the retry and the buyer
could be paid twice; the retry also debited the organizer's escrow twice. The refund id is now assigned
once, atomically (`findAndModify` with `$ifNull`), and saved before the provider call; the escrow debit
is skipped when the escrow already holds that refund's debit; a circuit-breaker fallback no longer marks
a refund failed. `RefundProviderRetryTest` (unreachable-then-accepted, lost answer, two concurrent
attempts, a rejection).

### Every PawaPay call leaves a record written before it is made

Refunds, payouts and bank test-deposits now write a `booking_payment_attempts` row before the call and
settle it with the answer (`attemptType` `COLLECT`/`REFUND`/`PAYOUT`/`VERIFICATION`, keyed on the provider
reference). Found on the way: a checkout attempt row was silently lost on every second checkout, because
a null `attemptNumber` collided on its unique index. `ProviderCallRecordTest`,
`PayoutAndVerificationCallRecordTest`.

### F-001 · the remaining read paths

About 24 organizer entry points loaded a record by id without checking the caller's organization, and 13
more loaded then compared (which reveals that a record exists). All now go through `TenantGuard`
(`TenantReads`, `IdentityTenantReads`), answering "not yours" exactly as "does not exist". Beyond reads:
promo codes could be created on another organizer's event and were stored without an organization; any
organizer could add a ticket tier to another's event; an organization's tax, contact and payout details
were readable by any signed-in user; and six endpoints named security beans that did not exist, locking
organizers out of their own bank accounts and payouts. `BookingTenantBoundaryTest`,
`CatalogTierVisibilityTest`, `IdentityTenantBoundaryTest`. **F-001 is closed.**

### F-013 · one catalogue, the owner's switches, and the event-access bug

- One closed catalogue of 30 `module:action` permissions (`com.pml.shared.security.Permission`), with the
  platform-role sets beside it; identity's `OrganizationRole` and `EventRole` hold the role sets; the second,
  disagreeing table in `PermissionResolutionServiceImpl` is gone, and every check resolves through one
  algorithm. **No organizer, not even an Owner, could grant access to a single event**, because the check
  asked for a name no role carried; Owners and Admins now hold `event_access:grant`.
- D-35's two switches exist and apply everywhere a decision is made, including booking's financial views
  and payout requests, which now ask identity instead of comparing role names. Changing either switch needs
  `organization:billing` (owner only). The settings mutation had bound nothing: its input record's field
  names did not match the schema, so every change was silently dropped.
- D-37: the permission CRUD, its two collections and the `createEventOwner` mutation (which let any member
  make themselves the owner of any event) were removed; `permission-model-catalogue` drops the collections,
  switches both settings on for existing organizations and rewrites stored custom permissions to catalogue
  codes. Custom permissions must be catalogue codes the granter holds (`requireDelegable`). A grant counts
  only on its own organization's events. `myPermissions`, which `PermissionGate` has always called, now
  exists. `PermissionNameLintTest` fails the build on any permission spelled as a string.
- Tests: `PermissionCatalogueTest`, `OrganizationRoleTest`, `EventRoleTest`, `MemberPermissionsTest`,
  `PermissionResolutionTest`, `PermissionModelMigrationTest`, `CrossServicePermissionCheckTest`,
  `OrganizationSettingsMutationTest`, `SettingsInputBindingTest`, `TeamFinancialAccessTest`.
  **F-013 is closed.**

### Event approval tells people and says what is missing (F-036's limits)

- Catalog's approval workflow announces each step through `POST /api/internal/notifications/approvals`:
  every active `ADMIN` hears of a submission, the organizer hears of a decision (D-36). Announcements run
  behind `Workflow.getVersion` markers; two histories recorded from the previous implementation are kept
  as fixtures and replay against the new one (`EventApprovalWorkflowTest`), and removing a marker fails
  that replay. A failing announcement never blocks or undoes the decision.
- `Event.approvalBlockers` (admin only) lists what an event lacks, from the same function the approval
  refuses with. The admin queue does not display it yet (its screen awaits the Claude Design pass).
- Catalog's schema declared `PENDING_REVIEW` and no `REJECTED`, while events are stored `PENDING_APPROVAL`
  and `REJECTED`: returning such an event over GraphQL failed. The enum, the admin workbench and the
  storefront card now use the stored values.

### F-002 · one index authority

207 index annotations across 41 model classes, plus booking's analytics indexes and catalog's
reference-data indexes, moved into each service's `*IndexInitializer` under their existing names, with a
§4 row each; `auto-index-creation` is off in all three services and `IndexAuthorityLintTest` now bans the
annotations outright. `*IndexCensusTest` proves, shape by shape, that the registry alone rebuilds every
index the services had, except seven dropped on purpose: two plain `expiresAt` indexes that the registry's
TTL indexes replace, and five on `financial_transactions`. `index-registry-conformance-4` repairs existing
databases. **F-002 is closed.**

### The check-in gates enforce `ticket:scan`

The check-in resolvers checked only a platform role (`ORGANIZER`, `ADMIN` or `SCANNER`) and that
the ticket belonged to the event named in the request, so any organizer could admit tickets at any
organization's event; gate reads and conflict review were scoped to "organizer = caller", which
showed team members and gate staff nothing. Scanning, offline uploads, gate reads and conflict
review now go through `EventGateAccess`: catalog names the event's organization, identity decides
`ticket:scan` (an event grant such as `CHECK_IN` first, then membership), and a platform role that
carries the permission (`ADMIN`, `SUPER_ADMIN`) passes on its own. Admissions and conflicts belong
to the event's organizer and name the steward who scanned. An outsider is refused as an unknown
event; a member without the permission as not permitted; an upload batch with one refused event
records nothing. `GateScanAccessTest` (replica set), `EventGateDecisionTest`, and two cases in
identity's `CrossServicePermissionCheckTest`.

### Found along the way, not fixed

- **The admin platform summary reads a collection nothing writes.** `PlatformSummaryRepositoryImpl`
  aggregates `financial_transactions`, which no code in any service writes (verified by search), so its
  transaction figures are always zero. Which collection it should read is a finance decision; the
  journal (`booking_journal_entries`) is the likely candidate, not verified.
- The shared MongoDB test container now starts with a 64,000 open-file limit. At Colima's default of 1,024
  it aborted mid-suite with `EMFILE`, which read as unrelated test failures.
- Admin-only approval fields on `Event` (`approvedBy`, `rejectedBy` and the like) are tagged for the admin
  contract but not guarded at the resolver; any signed-in caller can read them.

## F-038 · Dead-code and single-responsibility audit: what it removed, and what it found

**Found** 2026-09-19. Full report: `docs/audits/2026-09-SINGLE_RESPONSIBILITY_AUDIT.md`; method and
tools: `backend/tools/reachability/`. About 14,200 lines of backend Java, 78 configuration keys and 23
internal endpoints that no business process reached were removed; cross-cutting code duplicated in three services moved into
shared-library as auto-configurations; every suite green; each service's jar booted against MongoDB and
Temporal.

### Fixed, each with a test

- Catalog tier mutations answered with a wrapper the schema does not have, and swallowed every refusal
  into a 200 (`TicketTierMutationShapeTest`, `TicketTierSchemaParityTest`).
- `refundRequestByRequestId`'s `@PreAuthorize` named a missing method, refusing a customer their own
  refund. `PreAuthorizeTargetLintTest` now checks every SpEL bean call in every service.
- Booking called catalog and identity `/api/internal/**` without a service token — 401 outside tests
  (`InternalServiceWebClientsTest`).
- S3 storage, the payment timeout and the consumer retry budget were configured under keys the code did
  not read.
- Services component-scanned shared-library's auto-configurations, so none of their ordering held.
- Migrations and collection validators ran after seeds and adoption; the validator step was
  fire-and-forget. They are now the first two ordered runners.
- Validator `enum` lists lagged the Java enums in ten fields across booking, catalog and identity —
  cancelling a refund, rejecting an event or seeding account 5050 was refused by MongoDB. The three
  `*ModelValidatorParityTest`s now check enum values as well as field names.
- Identity exposed `/actuator/**` and a debug endpoint without a token.
- Ownership transfer's second factor accepted any code. It is now a one-time code sent to the nominee's
  verified phone (`requestOwnershipTransferCode`), single-use and scoped to the transfer
  (`OwnershipConfirmationCodesTest`, Testcontainers Redis).
- Identity's encryption key fell back to a literal in git; it now has no default outside the local
  profile (`EncryptionKeyConfigTest`). Rotate it wherever a service ever ran without the variable.
- Token revocation was checked nowhere on ordinary requests and not at all in catalog and booking.
  `RevocationRequestGuard` now runs after authentication in all three subgraphs (revoked → 401
  `TOKEN_REVOKED`; store unreachable → the request proceeds); catalog and booking read identity's
  records through `HttpDurableRevocationStore` over the internal API; booking's money-moving, check-in
  and recovery mutations and catalog's platform configuration fail closed (`RemoteRevocationEnforcementTest`,
  `FailClosedOnRevocationClassTest`, `SensitiveMutationsTest`). Catalog now needs Redis.
- All `package-info.java` files deleted: Spring Modulith boundary headers that had drifted from the code.
- A second pass inside the classes (PMD, `fields.py`, `endpoints.py`) removed unread fields on both
  sides of internal APIs, 12 payment meters that were never recorded, and 23 internal endpoints no
  service or frontend called.
- Five admin paging types named `paginationInfo` what the schema calls `pagination: PaginationInfo!`,
  so those list queries failed; `ReconciliationSummary` lacked the schema's two non-null variance
  fields; `updateMemberRole` read `role` while clients send `newRole`, so every role change arrived
  null; the discovery filter's `isFree` never matched the schema's `isFreeEvent`.
  `GraphQlDtoSchemaParityTest` (all three services) now holds every GraphQL DTO to its schema type.

### Open

- **High:** `CreateEventInput` discards 13 fields the organizer "New event" page sends, among them
  `ticketTiers` and `location`, so events are created without tiers or venue; `UpdateEventInput`
  discards 14. `UpdateNotificationPreferencesInput` discards 9 of the settings page's fields. Both are
  on the parity test's known list, which may only shrink.
- `discoverEvents` ignores its filter; ticket, payout, escrow and admin event filters drop 12 fields.
- 26 `@Document` fields nothing writes and 38 nothing reads (`inventory-after/fields.tsv`) — a data
  model review with migrations, not a deletion.

- Catalog export links point at `/api/exports`, which nothing serves.
- ET-FIN-004 R1's refund schedule is not implemented; refunds are a flat percentage.
- 14–20% of reachable service methods execute under test; the `GAP` rows of
  `docs/audits/inventory-after/inventory-methods.csv` list them.

## F-039 · The serious bugs F-038 found: event authoring, notification settings, discovery and list filters

**Found and fixed** 2026-09-19, each against its approved spec, each proven by Testcontainers
integration tests that apply the services' own collection validators and indexes.

### Fixed

- **An event was created without what the organizer entered.** `CreateEventInput` bound 7 of 20 schema
  fields: tiers, venue, format, banner and policies were dropped. It now stores every field; the venue
  resolves to a `CITY` of the reference data (unknown → `LOCATION_UNKNOWN`) and is shared across
  organizations; venue, event and tiers are written in one transaction. `EventAuthoringTest` (L2).
- **No ticket could be bought for an event created in the app.** Booking prices a reservation from the
  event's `ticketCategories`, which nothing wrote, and matched the buyer's tier id against the tier
  *code*. `EventTierMirror` now keeps that list, the capacity (sum of tiers) and the lowest on-sale
  price equal to the tiers after every tier write, and booking matches by tier id.
  `ReservationByCatalogTierTest` (L5, WireMock + replica set).
- **Every event creation would have been refused by MongoDB.** The page sent hard-coded category codes
  the reference data does not have, into a field the validator required to be a 24-hex id. Categories
  are now the reference data's `EVENT_CATEGORY` codes, checked on create and edit, and the page lists
  them from the reference data.
- **An edit could change a published event's date freely, and dropped most fields.** The material-change
  rule (ET-CAT-001 R3) is implemented and classified field by field; edits store every field; `featured`
  is administrator-only. `EventEditingTest` (L2), `EventFieldClassificationTest` (L1).
- **Notification settings did not save, and did not matter.** 9 of the settings page's fields were
  dropped; 9 non-null output fields had nothing behind them; nothing consulted preferences when
  sending. Essential categories are now always on (switching one off is refused), reminders honour the
  recipient's category, channels and quiet hours, and a suppressed message is recorded `SUPPRESSED`
  with the reason. The workflow change is version-guarded and replays a pre-deploy history.
  `NotificationPreferencesTest` (L2), `NotificationWorkflowTest` (L3).
- **Every notification write was refused by its own validator.** `_id` is the deterministic
  `notify:{key}` string and `userId` is absent for an invitation; the validator demanded an ObjectId
  and a user. Fixed and proven by recording through the production activity.
- **Discovery ignored its filter, and its indexes indexed nothing.** The registry declared
  `{status, startsAt}` and `{categoryId, cityId, startsAt}`; events store `eventDateTime` and had no
  `cityId`. `discoverEvents` now applies the five filters of ET-CAT-003 R4 and refuses the others,
  caps width and depth, and all 32 filter combinations plan as `IXSCAN` (`EventDiscoveryTest`). The
  text index weights the title; `IndexSpec` and `ConflictingIndexRepair` now carry text weights.
- **The admin event export applied one filter field of fourteen,** and wrote organizer text into CSV
  cells a spreadsheet would run as formulas. Every field now applies (country through the reference
  data), the export is bounded, and a leading `= + - @` is neutralised. `EventExportFilterTest` (L2).
- **Ticket search read the whole collection into memory;** it is now one bounded query with a literal
  search, and payout and escrow filters apply every field. `ListFiltersTest` (L2).
- **Ticket tier input had no validation** — a negative price or quantity was accepted.

### Open

- `catalog_categories`, `catalog_cities` and `catalog_provinces` still exist in code (models, services,
  GraphQL) beside the reference data that now answers for them; retiring them is the next step of the
  one-engine rule.
- `IXSCAN` is proven on Testcontainers; the live confirmation via MCP needs catalog booted once
  against the dev database so the new indexes and the `index-registry-conformance-5` step run.
- The parity test's known list still names: category presentation fields, device metadata, four
  `SendNotificationInput` options, `ReportExport.errorMessage`, `AuthPayload.tokenType`, and the
  deprecated `tags`.

## F-040 · Product decisions of 2026-09-19, and the settings validator that would have stopped catalog

**Decisions (product owner).**
- **Platform settings are one table, like the reference data.** `platform_configuration` →
  `catalog_platform_configuration`: catalog writes it, every other service reads it directly through
  shared-library's `PlatformConfigurationReader`. Identity's private view is deleted; feature flags
  and organization overrides (ET-ADM-002) live in the same table. ET-PLT-002 §4 now has 68 rows.
- **Categories are plain reference rows.** No colour, icon or order styling on any reference row;
  the reference engine does not determine the frontend. `ReferenceMetadataValidator` refuses
  presentation keys, the seed's fifteen colours are gone, and `strip-reference-presentation` clears
  existing rows. `EventCategory.iconUrl/color/sortOrder` and their inputs are `@deprecated`.
- **Notifications are system-only.** `sendNotification`, `sendBulkNotification` and the four
  compose options of `SendNotificationInput` are `@deprecated`.
- **Reports:** no requirement is written now; the export stays as it is until reporting is taken up.
  Known and left: the export link points at `/api/exports`, which nothing serves.
- **Refund policies are platform configuration.** The platform defines them (starting with
  FLEXIBLE, MODERATE, STRICT, NO_REFUNDS); the organizer picks one per event; the event records the
  policy version it was published under. ET-ADM-002 §4 and ET-FIN-004 R1 amended. Until those
  specs are built, catalog still accepts the old values (`FULL_REFUND`, `PARTIAL_REFUND`,
  `NO_REFUND`, `CUSTOM`) and booking applies one flat 24-hour rule — so an event marked no-refund
  can still be refunded. That gap closes with ET-ADM-002 and ET-FIN-004, in their waves.

**Defect found.** The settings validator required `payment.minimumPayoutAmount` as a binary
`double`, while the platform writes money as Decimal128. On a new database catalog's startup seed
was refused, and catalog would not start. `PlatformSettingsTableTest` found it; the validator now
accepts `decimal` (and `double` for pre-migration documents).

**Registry.** Ten booking rows carried `@Version` while §4 said unversioned. §4 was corrected rather
than the code, because removing optimistic locking can only lose updates. With both registry
disagreements closed, ET-PLT-002, and after it ET-PLT-003 and ET-PLT-015, are `implemented`.

## F-041 · Closing Wave 0: what the last three platform specs found when actually run

ET-PLT-004, -005 and -006 are `implemented` (2026-09-19); with -001, -002, -003, -012 and -015,
Wave 0 is complete and Wave 1 may open.

**Live defects found and fixed**
- **Mutations that always reported failure.** Admin event approve/reject/request-changes and, in
  organization-admin, publish, unpublish, create event, create payout request and every bank-account
  action read a `success` field the mutations never return. Every success rendered as a refusal;
  after creating an event the organizer was told it had not been created.
- **Team role changes never worked** — `updateMemberRole` sent `{role}` against an input of
  `{organizationId, memberId, newRole}`.
- **The public contract would not have composed.** 98 elements tagged both `organizer` and `admin`
  were excluded from the variant organization-admin generates from, and 54 public operations
  returned them. The `admin` tag is dropped wherever `organizer` is present.
- **Codegen generated no operation types**: its glob matched a file pattern nothing used, so every
  hook typed its result by hand — which is how the defects above compiled.
- **CI composed a supergraph nobody runs**: bare `schema.graphqls` without `@auth`, federation
  `=2.9.0`; GraphOS check/publish sent the same bare files.
- **The contract document hid ownership grants**, reading organizer queries as console-only.
- Seven documents selected fields the schema lacks; 56 unreachable hooks deleted.

**Facades found**
- `npm run test:compliance` named an Nx target that does not exist; `e2e:compliance` ran two empty
  directories. Microcks ran for one app, not three; the rendered brand check covered one app.
- `errorContract.test.ts` failed only on a hard-coded registry size (93 vs 98).

**Tooling, worked around**: Nx 23.1 builds no external nodes from the pnpm 11 lockfile, so inferred
`nx e2e` targets cannot run — the e2e scripts call Playwright directly. Chained Testcontainers runs
race the reaper; chained scripts disable Ryuk (each harness stops its own container).

**Left as debt, ratcheted**: 38 literal prop values outside the design system's closed sets (mostly
`Button color="gray"|"teal"`). Replacements are design decisions; the design files need `/design-login`.

## F-042 · Documentation realignment

**Found and done** 2026-10-03. Documentation only; no code, spec requirement or decision text changed.
A read-only audit found the planning documents disagreeing with the spec headers and with later findings.

### Changed

- `specs/FINDINGS.md`: added the *Open items index* (27 rows, from reading F-001 to F-041) and its usage
  note; annotated F-033 and F-034 as superseded on Temporal Cloud, and F-035 and F-036 with a pointer;
  added this entry.
- `specs/IMPLEMENTATION_PLAN.md`: spec count and implemented count corrected in the title and §3; the P3
  blocker annotated against its own Track F0 table; a dated status paragraph added.
- `specs/RECONCILIATION.md`: banner marking it a 2026-08-31 snapshot, current implemented counts, pointers to
  the index and to the per-spec task gates. No conformance numbers were added.
- `specs/ROADMAP.md`: a dated status note above the decisions table covering D-21, D-28, D-33 and D-22.
  Decision text untouched.
- `specs/README.md`: stale counts refreshed.
- `todos/README.md`, `todos/00` to `08`, `todos/components/bento-layout.md`: HISTORICAL banner.
- `docs/BACKEND_GAP_ANALYSIS_REPORT.md`, `docs/STUB_TYPES_ANALYSIS.md`: superseded banner.

### Corrections to the audit brief, found while verifying

- The corpus is **42 specs** (plus the template), not 41: `find specs -name spec.yaml` outside `_templates`
  returns 42. **8** are `implemented` (ET-PLT-001 to 006, 012, 015), 34 `approved`, none `verified`. The
  "41" in RECONCILIATION.md was true when it was written and is now stale; the plan's title "42" was right
  and its "All 42 approved" was not.
- F-038's High items and filter items were closed by F-039 on 2026-09-19, not open.
- The Temporal Cloud text is in F-033 and F-034, not F-034 to F-036.

### Not done

Nothing in the index was re-verified against the code; conformance (the §3 read) is still open on every spec
not marked `implemented`.


## F-043 · 2026-10-03 · Late-payment auto-refund (D-22) implemented

`LatePaymentRefundWorkflow` now exists (`booking-service/.../workflow/latepayment/`, queue `booking-checkout`,
id `late-refund/{reservationId}`). `PurchaseWorkflow` starts it when money confirms after the seat grace, behind
`Workflow.getVersion("late-payment-refund", …)`. It reuses the provider refund calls (not `RefundWorkflow`, which
is keyed by ticket) and keeps the `PAID_AFTER_EXPIRY` escalation as the fallback. `PaymentIntent` gained
`lateRefundId`, `lateRefundStatus` and `lateRefundFailure` (schema updated). Booking-service suite: 338 tests, 0
failures.

Open: no outbox event for the buyer notification (the event registry is closed); late arrivals reaching
`PaymentOutcomeService` by webhook do not start the refund yet (the escalation row still covers them); no
history recorded from the pre-change build; the activity "twice, one transition" test against real Mongo and
PawaPay was not run.


## F-044 · 2026-10-04 · Buyer identity redesign

A code review of the identity path (keycloak-extensions `PhoneOtpAuthenticator`, identity-service `OtpService`,
`InternalOtpController`, `MessagingService`, `UserSyncServiceImpl`, `KeycloakService`) found it could not be built
upon: the plugin creates users and grants roles; the OTP is stored in plain text and compared with `String.equals`;
three tries and no lock; the Twilio body unencoded with no timeouts and a fallback that never fires; a
service-account token returned as the buyer's token; user sync trusting user-editable attributes and hard-deleting;
email adopted on a 409; duplicate email indexes; no admin-realm listener; and no tests for the OTP service, controller or
messaging. The redesign (decisions **D-38..D-50**, wire contract
[`identity/004-accounts-and-contacts/CONTRACT.md`](identity/004-accounts-and-contacts/CONTRACT.md)) replaces it. Statuses are unchanged
(`approved`); nothing is `implemented`.

### What changed (documents only)

- `ROADMAP.md`: decisions D-38 to D-50 added (buy first; WhatsApp and email, SMS dropped; fixed QR; gate fallback code plus ID, first
  scan wins; mobile money only; identity-first accounts with the database index as arbiter; in-checkout code with a one-time login
  handle redeemed by a Keycloak authenticator, amending ET-IDN-001 R5; server-side buyer session; no personal data in ids; `enabled`
  owned by identity-service; two realms; staff created from the admin app; defaults). D-15 and D-09 annotated, the "one realm" ground
  rule corrected, the Wave 1 table renamed ET-IDN-001 and gained ET-IDN-004.
- `identity/001-phone-otp-identity` (folder kept, title now *Contact-OTP passwordless identity*): spec.md rewritten (sections 1 to 6; R1 to R7;
  SMS and "email as third channel" removed from Rejected alternatives with new reasons); spec.yaml rewritten (persistence, redis_keys,
  rest.internal, errors, config, SPI modes). `tasks/ET-IDN-001.md` rewritten (R0 reconcile table against the code review, BE/FE/TS tasks,
  new gate) and `reconciliation/ET-IDN-001.md` re-measured.
- **New** `identity/004-accounts-and-contacts` (spec.md, spec.yaml; CONTRACT.md kept), `tasks/ET-IDN-004.md`,
  `reconciliation/ET-IDN-004.md`.
- Amended: `identity/002` (re-scoped to adoption and repair, tombstone not hard delete, no phone change by profile, `enabled` per D-47;
  a banner lists the supersessions, `changePhoneNumber` removed), `identity/003` (access token PT5M, end-to-end logout R9, triggers for
  contact change, suspension, merge), `_platform/007` (two realms, R8: 5-minute token, audience, refresh rotation with reuse detection,
  password grant banned, server-side buyer session, realm as code, the contact authenticator, `myticketzm-web` confidential), `_platform/011` (R6
  concrete OTP limits and the trusted-proxy IP), `_platform/008` (sign-in consent record, contact PII rows, no personal data in ids),
  `_platform/009` (six identity audit actions, `PHONE_NUMBER_CHANGED` renamed `CONTACT_CHANGED`; registry 50 to 56), `_platform/015` (registry rows
  AccountEnsure, AccountMerge, ContactChange, AccountRepair, TicketDelivery, all `planned`; `identity-account` queue noted but deliberately not
  a table row yet; search attributes without personal data), `ticketing/001` (R9 hold after verification), `ticketing/002` (fixed QR, R5 superseded,
  WhatsApp/email delivery), `ticketing/003` (R6a gate fallback, first scan wins), `notification/001` (SMS removed, WhatsApp templates and
  opt-in R9, ticket delivery fallback R10), `payment/001` (R8 mobile money only, payer number separate from contact).
- `docs/KEYCLOAK_PHONE_OTP_AUTHENTICATOR.md`, `AUTHENTICATION_ARCHITECTURE_RECOMMENDATIONS.md`, `BACKCHANNEL_LOGOUT.md`,
  `KEYCLOAK_IMPLEMENTATION_PLAN.md`: superseded-in-part banners (history kept); `SCANNER` removed from the plan; 3-attempt wording struck; the
  D-37/ET-PLT-013 and resolution-order contradictions resolved by a pointer note (ET-ORG-003's six steps win).

### Contradictions found and how they were resolved

- ET-IDN-002 said `enabled` lives only in Keycloak; D-47 reverses it. ET-IDN-002 said one realm; D-48 says two. Both carry banners.
- ET-TKT-002 R5 (re-issue rotates the QR) contradicts D-40 (fixed QR); R5 is superseded, with first-scan-wins and re-send as the replacement.
- Two consent collections now exist (`identity_consent_records` from ET-PLT-008, `identity_consents` from CONTRACT section 8).
- ET-PLT-011 named the OTP refusal `RATE_LIMIT_EXCEEDED`; the contract names it `OTP_RATE_LIMITED`; the spec now follows the contract.
- The Task queue registry in ET-PLT-015 is linted against `application.yml` and `TaskQueues.java`, so `identity-account` is not a table row until the code lands.

### Open items (decisions not yet taken)

1. **Organizer second factor** - whether organizers (who handle payouts) need a second factor beyond the contact code.
2. **Recycled-number inactivity check** - how long a WhatsApp number may be unseen before a proof is not enough to take over its account.
3. **Contact quarantine length** - how long a released contact cannot be claimed by another account.
4. **Merge policy** - automatic on a signal, or only by support. The spec allows support-initiated merge only until decided.
5. **Support recovery proofs** - what a person with no access to their only contact may present, and who may accept it.
6. **Legacy users without a verified contact** - the migration for accounts created under the phone-attribute design (and users in the admin realm created in the console).
7. Merge the two consent collections; confirm whether the named-ticket event option (gate fallback ID matching) exists.

### Decided for implementation 2026-10-04 (contact linking and change) - revisit

Taken so contact management could be built; each is a default, not a closed question.

0. **User decisions the same day** (2026-10-04): (1) adding a second contact needs ONLY the code to the new contact, no step-up - the stolen-session risk below is accepted, revisit later; (2) a pending code may be sent again once `identity.contact.resend-after` (default PT5M) has passed - the user said "after five" and it is read as five minutes after the last send, to be corrected if five means something else; (3) quarantine stays 30 days, configurable; (4) the full account repair (D1..D9, Schedule `identity-account-repair`) is built, see spec 004 R4.
1. **Authorisation of a change** (answers open item 5 for now): a change, a removal and a primary switch are authorised by a fresh code sent to the account's CURRENT primary verified contact. There is **no support-recovery path**: an account with no verified contact able to receive a code is refused `NO_VERIFIED_CONTACT`. *Revisit:* who may accept a recovery proof, and what a person who lost their only contact presents (open item 5 stays open for that case). *Risk accepted by the user:* `confirmContactAdd` needs only a code to the new contact, so a stolen session can attach an attacker's contact that then signs in as the account; a step-up for add is the first thing to add if that matters.
2. **Quarantine** (answers open item 3 for now): a released contact is unclaimable by any other account for `identity.contact.quarantine`, default **P30D**, `PT0S` in the test profile. It applies to `confirmContactAdd`, to a change, and to `ensure` creating a new account from a proof of the contact. The releasing account is not held back. *Revisit:* the length (30 days is a guess between recycled-number risk and a blocked re-registration) and the recycled-number inactivity check (open item 2).
3. **Last verified contact** cannot be removed (`LAST_VERIFIED_CONTACT`).
4. **One primary**; the primary may be switched among verified contacts after a step-up. Removing the primary hands it to the oldest remaining contact.
5. **Sessions**: a change or removal ends the account's Keycloak sessions (refresh tokens die at once); access tokens already issued live until they expire (PT5M). A per-user revocation record was not used because it blocks the person's next sign-in for its whole lifetime. *Revisit:* per-session revocation by `sid` if PT5M is too long.
6. **Admin client**: `keycloak-admin-client` 26.0.12 is the last standalone release (Maven Central has nothing newer; 26.8.0 ships only split core/internal modules whose pom says internal-only). Stay on 26.0.12, compatible with the 26.5.2 server; revisit when a supported 26.x client ships. See ROADMAP D-51.
7. The contact notices are fixed words with no personal data. Email sends them; WhatsApp needs approved parameterless templates (`identity.delivery.whatsapp.notice-template-name`) and sends nothing until they exist.

### Not done

No code, no realm export, no test was changed. The ET-PLT-002 registry (`identity_contacts`, `identity_consents`, `identity_account_events`), the ET-PLT-005
registry rows for the new error codes, the ET-PLT-003 rows for the proposed `identity.Account*` events and the ET-PLT-015 queue row
are the coordinator's follow-ups, because their lints compare those tables with code.

## F-045 · 2026-10-06 · Every `@deprecated` GraphQL element removed

**Decision (product owner):** "all these to be totally removed" — no deprecated API remains in any
subgraph or in the router's supergraph. This is an exception to ET-PLT-010's 180-day window,
recorded there: no client has been released, and the three web apps were migrated in the same change.

**Removed.** In catalog: the cursor/offset pairs for categories and published events, the city and
province connections, the deprecated event-discovery inputs and `tags`, category presentation
fields, and the DTOs, repository methods and service methods behind them. In identity:
`sendNotification`, `sendBulkNotification`, `SendNotificationInput`. In booking: the
`chartOfAccounts…Pagination` pair (the bare `chartOfAccounts` stays, so the held-back collision
test went with it).

**Frontend.** Buyer discovery uses `discoverEvents` and `categories`; the admin Categories tab uses
reference data (code fixed after creation; counts joined by code, shown as "-" when inactive); the
bulk-notification hook and mutation are gone.

**Tests.** The identity unconstrained-input budget fell from 11 to 10; the generated
`docs/FRONTEND_GRAPHQL_CONTRACT.md` was regenerated; the router supergraph recomposes with 0
`@deprecated`. OI-23 is closed.

### Open

- `catalog_categories`, `catalog_cities` and `catalog_provinces` models still exist beside the
  reference data; their orphan GraphQL types were left untouched.
- Some domain fields are still stored but no longer reachable through GraphQL.
- The testcontainers gate counted tests from console output, which `mvn -q` hides on a clean pass;
  it now sums the surefire/failsafe XML reports instead.

## F-046 · 2026-10-09 · ET-PLT-007 first pass: the idempotency guard, a gate on every root field, the internal surface per path

**Built.** `shared-library` now has `IdempotencyGuard`, `Fingerprint` and `MongoIdempotencyLedger`
(package `com.pml.shared.idempotency`). A Redis `SET NX` on `idem:{scope}:{key}` turns away an
obvious reuse and marks the key in flight; the authority is a ledger whose `_id` is the scoped key,
so a unique constraint, not application code, decides which of two simultaneous callers proceeds.
Tested against a real Mongo replica set and Redis: replay returns the first response, a changed
request is `IDEMPOTENCY_KEY_REUSED` and not retryable, 20 parallel submissions apply once, a
`FLUSHALL` between attempts and in the middle of contention changes nothing, an unreachable Redis
falls open to the ledger, a failed operation frees its key, and one actor's key never returns
another actor's response. Breaking the ledger's duplicate handling fails 6 of 11. **No mutation
calls it yet**: the nine registry operations adopt it in their own specs (ET-TKT-001, ET-PAY-001,
ET-FIN-003/004, ET-TKT-004), each adding its collection row to the ET-PLT-002 registry.

**Found, by the sibling comparison (five for five).** `invitationByToken` was narrowed to five
fields in F-011 so a forwarded link discloses nothing. `declineInvitation(token)`, taking the same
bearer token, still returned the whole `TeamInvitation`: the invitee's email, phone number and name.
§4 specifies `Boolean!` and `AUTHENTICATED`; the schema said `TeamInvitation` and the resolver had no
gate. Fixed, with the client selection and the generated types.

**Found, by enumerating every root field.** Access control is `@PreAuthorize` on resolvers or `@auth`
in the schema, and nothing checked that every field has one. `validatePromoCode` had neither, so any
signed-in caller could probe promo codes; it is now `isAuthenticated()`. The 24 deliberately open
catalog reads and identity's two token-guarded or public reads now say so with
`@auth(requires: PUBLIC)`, and `OperationGateLintTest` in all three services fails on any root field
with no decision recorded. The helper is `OperationGates` in the shared test-jar.

**The internal surface, per path.** `InternalSurfaceTest` discovers every `/api/internal/**`
endpoint from the controllers and runs each through the service's real security chain with signed
tokens: no token 401, an administrator user token without an internal scope 403, the intended scope
200. A `permitAll` added before the rule fails it.

### Open

- **`PromoCodeValidation` answers with the raw exception text** ("Promo code not found", "usage limit
  reached", "has expired") and returns the whole `PromoCode` (organizer id, usage counts). That is a
  guessing oracle for signed-in callers and discloses organizer data. Whether the checkout shows a
  specific reason is a product decision. Separately the service is called with an empty tier list, so
  `anyMatch` over nothing is false and the check looks like it can never pass; not investigated.
- **Internal scope is coarse.** `/api/internal/**` accepts `internal-read` *or* `internal-write` for
  any method, so a read-scoped token can call a write endpoint. Only `/api/internal/auth/**` splits by
  method. Splitting it needs each caller's granted scopes confirmed first, and the permission-resolve
  endpoint is a POST that §4 gives `internal-read`, so the rule has to be per path, not per method.
- **The buyer realm export in `docker-resources` does not match R8** (superseded: the authoritative exports are `docker-resources/keycloak`, corrected and tested in [F-047](#f-047--2026-10-09--the-realm-exports-conformed-and-tested-against-a-real-keycloak)):
  `accessTokenLifespan` is 3600 (spec 300); refresh tokens do not rotate (`revokeRefreshToken` unset);
  the buyer web client and the identity-service client have direct (password) grants on; the mobile
  client allows `com.pml.ticketing://*` and `exp://192.168.*.*:*/*`. The Keycloak tests import a copy
  under `identity-service/src/test/resources`, so none of this is exercised. Changing the token
  lifetime depends on the buyer app server refreshing sessions, so it was not changed.
- Still unbuilt: tenant-filter conversion of the frozen read paths (catalog 25, identity 16, booking
  64), `@auth` on every field as the spec words it (the gate today is "`@auth` or `@PreAuthorize`"),
  the realm-export conformance test, backchannel logout, and the front-end idempotency key.

## F-047 · 2026-10-09 · The realm exports conformed and tested against a real Keycloak

**Which file is the realm.** `docker-resources/keycloak/` is the authoritative pair (contact flow, five-minute
tokens, rotation, no password grants). The copies under `docker-resources/keycloak` are an older generation
that the F-046 note described by mistake; the local-e2e scripts render the `infra` files, not those.

**What the `infra` exports still got wrong, now fixed** (Keycloak 26 documentation: realm token settings,
authentication flows):
- Staff second factor was `CONDITIONAL` on "user has an authenticator", so a staff account with only a
  password signed in with the password alone. `auth-otp-form` is now `REQUIRED` in the staff forms flow, so
  Keycloak makes the account enrol one before issuing a session.
- Staff password policy was `length(8) and notUsername(undefined)`; now `length(12) and notUsername and
  notEmail and passwordHistory(3)`.
- The mobile client allowed `com.pml.ticketing://*`, `exp://localhost:*/*` and `exp://192.168.*.*:*/*`;
  now exactly the two deep links it uses (an Expo development build needs its own override).
- Keycloak adds a public `admin-cli` password-grant client to every realm; both exports now define it disabled.
- Buyer `resetPasswordAllowed` was true in a realm with no passwords; now false.

**Tests.** `RealmConformanceIT` (9 cases) on a real Keycloak 26.5.2: it reads the settings back from the
server, tries the password grant against every client, enrols a staff authenticator through the real
page, shows a wrong and a replayed code refused, rotates a refresh token and shows reuse ends the session
(buyer and staff), and checks a seeded authenticator is honoured. A reverted export fails 7 of 8. The
existing staff sign-in cases in `ContactOtpKeycloakIT` now go through `StaffAuth`, which enrols or answers
the code prompt. Keycloak throttles two wrong codes inside one second with a one-minute lock, so the test
spaces its failures.

**The local stack.** Staff dev accounts are seeded with an authenticator (`STAFF_TOTP_SECRET`) and the admin
Playwright setup answers the code prompt when `ADMIN_E2E_TOTP_SECRET` is set. This was not run end to end.

### Open

- `docker-resources/keycloak/*.json` still hold the old realms and an old plugin jar (phone-OTP era);
  bringing them level needs the current jar, the themes and new variables in `docker-resources/env/ticketing.env`
  (`TICKETING_BUYER_APP_URL`, `TICKETING_ORGANIZER_APP_URL`, `TICKETING_ADMIN_APP_URL`,
  `ADMIN_WEB_CLIENT_SECRET`), and those files carry real secrets under version control.
- The staff realm ships a seeded `admin` / `admin_password` account; the buyer realm no longer seeds users.

## F-048 · 2026-10-09 · Identity by-id audit: five exposed operations closed

**Method.** Every identity root field that takes an identifier was read against its service method
(sibling comparison). Of about 60, six were exposed; five are fixed here.

**Fixed, each with a test on a real MongoDB or a pure rule test:**
- `markNotificationRead`, `deleteNotification`: any signed-in user could flip or delete another user's
  notification by id; the sibling `markAllNotificationsRead` was scoped. Both now take the caller's id
  (`findByIdAndUserId`, `deleteByIdAndUserId`). Someone else's id and an unknown id answer the same.
- `unregisterDevice`: any signed-in user could switch off another user's push device. Scoped the same way.
- `uploadVerificationDocument` / `deleteVerificationDocument`: `documentUrl` was client input turned into a
  storage key, so an organization could register a URL pointing at another organization's KYC file and
  have the delete destroy it. `DocumentKeys.ownedKey` now accepts a URL only if its path lies under
  `organizations/{ownOrgId}/verification-documents/` (traversal, look-alike ids, query-string tricks and
  malformed URLs refused); delete removes a file only when the same rule holds.
- `hasPendingOwnershipTransfer`: answered for any organization; now `false` unless the caller is on the
  organization's team.

Tests: `OwnRecordsOnlyTest` (L2, 3 cases, fails 3 of 3 when the owner filter is removed) and
`DocumentKeysTest` (L1, 13 cases).

### Open

- `grantEventAccess`, `bulkGrantEventAccess` and `inviteTeamMember` store a grant for any `eventId`
  without checking that the event belongs to the granting organization, and the duplicate check is global,
  so a stray row blocks the real owner. Whether catalog or booking honour a cross-organization grant is
  not established. The check needs catalog (an internal read), so it is a cross-service change.
- `ownershipTransferByToken` returns the whole transfer to anyone holding the token. The token is a bearer
  secret meant for one named person and is not a field of the type, so the exposure is limited to a
  leaked link; narrowing it to a preview like `InvitationPreview` is the consistent fix.
- `organization(id)`, `organizationBySlug`, `organizationByOwnerId`: private fields are nulled for
  non-members, but `status`, `kybStatus`, `ownerId` and `submittedAt` reach any signed-in user, including
  drafts. Whether that is meant to be public is a product decision.
- `_entities` for `User` and `Organization` resolve by id without authorization. Whether the router lets a
  client reach `_entities` on identity was not established; `User.email` and `phoneNumber` have no field
  guard.

## F-049 · 2026-10-09 · Account merge withdrawn

**Decision (product owner):** accounts are not merged, and the merge workflow is not built. ET-IDN-004 R6
and BE-7 are marked withdrawn.

**Why this was considered.** Duplicates arise when one person signs in with two different contacts (a phone
one day, an email the next); a contact can belong to only one account, so the same contact never
duplicates. Industry guidance (Auth0, Ory, Microsoft's pre-hijacking research, NIST 800-63B recovery
guidance) is that linking needs proof of both identifiers and that automatic linking on a matching
identifier is a takeover path.

**Left open, not built:** (a) after a sign-in, prompt the customer to add their other contact while
signed in (the add-contact flow exists); (b) hand a contact over from an *empty* account to the kept one
with a code to each contact, a notice to both and the 30-day quarantine, using ticket transfer and
ownership transfer to empty an account first. Neither is approved. The `MERGED` state and `mergedInto`
field stay in the model, and the repair job still alerts on a stale `MERGING` marker.

## F-050 · 2026-10-09 · Full-stack start, and the conformed realms in a real browser

**What was started** (`docker-resources/local-e2e`): the shared dev containers (Postgres, MongoDB, Redis, Temporal,
Service Bus emulator), Keycloak re-imported with the corrected realms (`kc-apply.sh --force`: backup, delete
the two ticketing realms, recreate only `dev_keycloak`), identity, catalog, booking, the gateway, the fake
payment provider and the Apollo Router (`e2e_router`, supergraph composed from the current schemas, so the
`@auth(requires: PUBLIC)` and `declineInvitation` changes compose). All four services answered health; a
public query returned through gateway, router and identity, and a tokenless `me` was refused with 401.

**Seen in Chrome.**
- Staff: password alone leads to a one-time-code prompt and no session; a wrong code shows "Invalid
  authenticator code"; the right code completes the sign-in. Defect: the theme lays the error message out as a
  narrow three-line column beside the field.
- Buyer: the sign-in page offers "WhatsApp number or email" with no SMS; the code page masks the contact
  (`c***@example.test`); the captured email code completes the sign-in and Keycloak redirects to the app
  callback with an authorization code.

**Seen in the three apps (Chrome).**
- Buyer app: home renders in the Showstop look (categories load from the live backend; "no events" is the empty
  database); in-app sign-in (WhatsApp/Email choice, consent wording, no SMS; six-box code with expiry and resend
  countdown) completes and the profile page shows the account as signed in with a verified, masked, primary contact.
- Organizer app: landing page, sign-in from the same Keycloak session, welcome page and the three-step onboarding
  wizard render.
- Admin app: SSO page, then password, then one-time code, then the dashboard (no console errors).

**Defects and gaps found.**
- Signing in as a different person while the browser still holds a Keycloak session for another one ends on
  Keycloak's generic "We are sorry... Unexpected error when handling authentication request". The app sends
  `prompt=login&max_age=0` with a login handle; the plugin's `setUser` collides with the session's existing user
  (`AuthenticationProcessor.setAutheticatedUser` throws). Ending the Keycloak session first fixes it. The app
  should end the SSO session before starting a sign-in, or the plugin should handle the conflict.
- Identity cannot publish to the Service Bus emulator: two `AccountActivated` sends failed with
  `NOT_FOUND ... please retry`, the binder health is DOWN, so `/actuator/health` answers 503 and `status.sh`
  shows identity down. The topic exists in the emulator config and its log shows no error; not diagnosed.
  Outbox rows stay unpublished until it is.
- Admin app: the account menu opens below the visible area at 842 px, so "Sign out" (y 879 to 919) cannot be clicked.
- Admin dashboard "Staff with two-step: 0 of 0, Everyone is protected" lists no staff although four staff users
  exist in Keycloak; the tile is vacuously true.
- Keycloak admin theme: the OTP error ("Invalid authenticator code.") is laid out as a narrow three-line column
  beside the field.
- The organizer console shows its full navigation, including "Create event", to an account with no organization
  or ORGANIZER role yet.
- `frontend/web/node_modules` was empty (an interrupted install), and plain `npm install` fails with an npm
  internal error: the project is pnpm (`packageManager`), and `pnpm install --frozen-lockfile` took 27 seconds.
- `apps.sh stop` did not stop anything when the project path contains a space (unquoted `cat $f`); fixed.
- Services start slowly (about two minutes) and, with the host loaded, health answered in 4 to 12 seconds.

## F-051 · 2026-10-09 · Events were silently lost when the bus was unavailable; a sign-out locks the person out

**Lost events (OWASP A04/A08, ET-PLT-003).** Every producer binding used the Azure binder's default
asynchronous send. `StreamBridge.send` therefore returned true as soon as the message was handed over, the outbox
marked the row `SENT` after one attempt, and a failed delivery (Service Bus not ready, a broken link) was only
logged as `onErrorDropped`. Found because identity's two `AccountActivated` events failed with `NOT_FOUND ...
Retries exhausted 3/3` while their outbox rows read `SENT, attempts: 1`.
- **Fixed:** `sync: true` on `identityEvents-out-0`, `catalogEvents-out-0` and `bookingEvents-out-0`
  (`spring.cloud.stream.servicebus.bindings.<name>.producer.sync`, documented default `false`).
  `ProducerBindingsAreSynchronousLintTest` fails for any producer binding without it; mutation-verified.
- **Proved live (Chrome + Mongo):** with the emulator stopped, a new sign-up leaves its outbox row `PUBLISHING`
  and retried (attempts climbing) instead of `SENT`.
- **Three independent causes of identity's undelivered events, all fixed (earlier "the sender does not recover"
  was wrong; a restart only appeared to help):**
  1. asynchronous sends hid every failure (above);
  2. `EventBridge` set a header named `sessionId`, but the binder's session header is `azure_service_bus_session_id`
     (`ServiceBusMessageHeaders.SESSION_ID`), so every event went out with no session id to subscriptions that require
     one, which Azure refuses and the emulator stalls on. Now fixed; `SessionHeaderMatchesBinderTest` compares the
     constant with the binder's;
  3. `application-local.yml` of all three services set `spring.cloud.azure.servicebus.namespace: ""`. An empty value
     still overrides the connection string, so the producer connected as `.servicebus.windows.net` and every send
     timed out ("Timeout waiting for send event hub response"). The key is removed (managed-identity deployments set
     `SPRING_CLOUD_AZURE_SERVICEBUS_NAMESPACE` instead); `NoBlankServiceBusNamespaceLintTest` fails on an empty one.
  Proof: the owed `AccountActivated` row (attempts 118 and climbing) was delivered the moment identity ran with a
  real namespace. The raw SDK had sent to all three topics all along, which is why the emulator looked innocent.

**Sign-out then sign-in locks the person out (organizer app): fixed.** The app revoked the Keycloak session id at
identity but left the SSO session alive (shared by the buyer and organizer apps), so every later sign-in was issued
the same revoked id and refused, in a loop. `endSso` (`libs/shared/src/auth/bff/sso.ts`) now ends the SSO session from
the server on sign-out and on the revoked-token path; covered by unit tests and real-Keycloak integration tests in the
shared library, admin and organizer apps, and verified in Chrome (stuck state healed in one bounce; sign-out logs
`sso: ended`; Keycloak holds no session). The plan for denial-of-service and rate limiting is in
`docs/operations/DOS_RATE_LIMITING_AND_SESSION_PLAN.md`.


## F-052 · 2026-10-09 · Revocation: the gateway failed open, an evicting Redis was trusted, and the defaults were an hour stale

Found while answering "when I log out of Keycloak, how is the access token stopped?". The enforcement chain from
Keycloak's `LOGOUT` event to a refused token existed; four defects in it did not show until it was run against
real tokens.

- **The gateway admitted revoked tokens after any Redis loss.** `SessionBlacklistFilter` did three `EXISTS` and
  swallowed every error as "not revoked", ignoring the completeness marker the other services use. After a flush,
  restart or outage, a logged-out token passed for up to the 2-minute warm interval. **Fixed:** the gateway runs the
  shared `CachedRevocationCheck` (cache, marker, then identity's durable records, through a client-credentials
  registration `api-gateway` with `internal-read`); with no store answering, reads proceed and state-changing
  requests get 503 `REVOCATION_UNAVAILABLE`. `GatewayRevocationEnforcementTest` (Redis container + WireMock),
  `SessionBlacklistFilterSidTest`.
- **An evicting Redis was trusted.** The eviction probe only changed the health status; `RevocationCacheTrust` still
  treated a miss as "not revoked" while the sentinel was present, so under `allkeys-lru` with a memory limit one
  revocation key could vanish and the token be admitted. The spec also accepted `volatile-*` policies as safe, but
  every revocation key carries a TTL, so those evict them too. **Fixed:** an evicting policy with `maxmemory` set makes
  every miss go to the durable store (`EvictionPolicyTrustTest`); the spec table is corrected.
- **Defaults still assumed a one-hour token.** The realms set `accessTokenLifespan: 300`; identity, catalog and
  booking defaulted `access-token-lifespan` to `1h`, `RevocationProperties` to one hour, and the web
  `RevocationService` to a 3660 s cache TTL, so records and keys lived twelve times longer than needed. **Fixed:** 5
  minutes everywhere; `revocation.it.ts` asserts the web TTL.
- **Repeating a revocation extended it.** `save` replaced the record, resetting `revokedAt`, `expiresAt` and the first
  reason. **Fixed:** insert once, return the active record unchanged, replace only a record past its expiry that the TTL
  monitor has not swept yet.
- **Identity's access-changing mutations were not fail-closed** (members, roles, grants, ownership, invitations,
  payout accounts, verification documents, users, credential-changing contact steps). Marked, and
  `SensitiveMutationsTest` now lists every unmarked mutation so a new one cannot be added without a decision.
- **Spec drift:** the Redis keys in the spec (`revoked:*`) were never the ones in the code (`pml:*`); the unique
  `{type,value}` index is unnecessary because `_id` is derived from them. Both corrected.

**The method:** the logout path was proven with real material, not stubs: two real Keycloak 26.5.2 sessions of one
user, a real Keycloak logout, the event the listener sends, MongoDB, Redis and the security chain
(`LogoutRevokesTokenEndToEndTest`, 8 tests: only the logged-out session is cut, Keycloak refuses to refresh it, a flush
loses nothing, a token and a session can be revoked alone, the user revocation covers tokens minted afterwards). The
listener plugin is not installed in that container, so the test posts the event it would send; the listener is covered
by `UserSyncEventListenerTest`.

**Still open in ET-IDN-003** (spec stays `in-progress`): the GraphQL surface, the `identity.TokenRevoked` event, audit
rows, the sessions list; and the rate-limiting work in `docs/operations/DOS_RATE_LIMITING_AND_SESSION_PLAN.md`
(the gateway brute-force filter trusts the first `X-Forwarded-For` entry and counts non-atomically; booking's limiter
fails closed against ET-PLT-011 R8).

## F-053 · 2026-10-10 · ET-PLT-007: realm composites, the platform-wide audience, and a stale SCANNER dependency

**Realm composites, fixed.** The admin realm's roles (`ADMIN`, `SUPER_ADMIN`, `FINANCE`, `FINANCE_LEAD`,
`SCANNER`) carried no `composite`/`composites` block at all, contradicting §4's hierarchy table
(`SUPER_ADMIN` includes `ADMIN`; `ADMIN` includes `FINANCE`). Added to both realm JSONs; a seeded
`admin` user's direct `realmRoles: [SUPER_ADMIN, ADMIN]` grant is now redundant but harmless.
`RealmConformanceIT.roleHierarchyIsComposite` reads the composites back from a real Keycloak.

**Audience validation, turned on.** Only 4 of 11 clients across both realms carried an
`oidc-audience-mapper`; the rest — every service-to-service client (`catalog-service`,
`booking-service`, `api-gateway`, `identity-service`, `otp-authenticator`) — minted a token with
**no `aud` claim at all** (checked live against `dev_keycloak`; `PlatformResourceServer`'s own comment
saying Keycloak defaults to `"aud": "account"` does not hold for this realm's client-credentials
grant, which carries no audience scope). Added the same mapper (aud = the gateway's client id) to
all five, so the platform has one audience, matching the design the four already-mapped clients
implied. `expected-audiences` in all four services now defaults to that client id instead of blank;
`PlatformResourceServer`'s "off by default" WARN should no longer fire in any environment that
imports the current realm export. Verified live: every client's token, service accounts included,
now carries `aud: myticketzm-api-gateway`.

### Open

- **`TicketQueryResolver.ticketByNumber` still checks `hasAnyRole(..., 'SCANNER', ...)`** — a realm
  role the spec's 2026-10-04 amendment (D-47) retired in favor of event-scoped grants
  (`identity_event_access_grants`, ET-ORG-003). The realm role was **not removed** in this pass
  because of this live reference: deleting it would silently cut off ticket-scanner lookups by
  number with no grant-based equivalent wired in. `ticket(id)` next to it already excludes `SCANNER`
  from its own check, so the two queries have drifted. Fixing this properly means giving
  `ticketByNumber` the same event-grant check `GateScanAccessTest`/`CheckInServiceImpl` already use
  for validation, not just deleting the role — that belongs to ET-ORG-003 or ET-TKT-003, not this
  spec. Until then the realm's `SCANNER` role stays, undocumented by the spec it should have been
  removed under.

## F-054 · 2026-10-10 · IdempotencyGuard couldn't replay a response with a derived getter

Found wiring `reserveTickets` onto the already-built `IdempotencyGuard` (ET-PLT-007 R6, first real call
site): `TicketReservation.getNetAmount()` is computed from other fields, with no backing field.
Jackson serializes it on the way into the ledger and then fails to deserialize it back
(`UnrecognizedPropertyException`) on replay, because `FAIL_ON_UNKNOWN_PROPERTIES` is Jackson's
default and nothing turns it off. This would have broken replay for any of the nine registry
mutations whose response type carries even one derived getter — not specific to reservations.

**Fixed in the guard itself**, not per call site: `deserialise` now reads with
`FAIL_ON_UNKNOWN_PROPERTIES` off, via a per-call `ObjectMapper.readerFor(...).without(...)`
rather than mutating the shared mapper a caller passed in (that mapper is used for other things
too). The real fields a computed getter derives from come back correctly; the computed value
itself is simply skipped and recomputes on its own. `IdempotencyGuardTest.replayToleratesAComputedProperty`
pins it with a record carrying exactly this shape.

Also found and fixed in the same pass: a test `ObjectMapper` built bare (`new ObjectMapper()`)
has no JSR-310 module, so any response type carrying an `Instant` fails to serialize at all —
unrelated to production, where Spring Boot's autoconfigured `ObjectMapper` already registers it,
but worth knowing before writing the next seven call sites' tests: use
`new ObjectMapper().findAndRegisterModules()`, not a bare one.

## F-055 · 2026-10-10 · `@Nested` JUnit5 test classes never execute under this project's Maven Surefire setup

Found while writing a cross-tenant proof for the ET-PLT-007 Phase 6 event-admin-operations fix
(`EventAdminOperationsTenantBoundaryTest`): the test reported `Tests run: 0` with no error, no
"skipped" count — Surefire simply never ran any `@Test` method declared inside a `@Nested` inner
class. Confirmed with a minimal, dependency-free probe class (one top-level `@Test`, one `@Nested`
class with one `@Test`): Surefire ran only the top-level method, every time, including a plain
`mvn test` with no `-Dtest` filter at all.

**Confirmed independent of my own code.** This is not new breakage from this session's work — it
reproduces on a from-scratch class with zero project dependencies, and retroactively on pre-existing
files: `TicketTierTenantBoundaryTest` reports "Tests run: 2" (its two top-level methods) while its
`Outsider`/`Unaffiliated`/`Owner` nested classes — the actual cross-tenant attack scenarios the test
exists to prove — have, as far as this investigation found, never executed. `EventVisibilityTest`
shows the same shape (1 of its many nested cases). At least 20 test files across the backend use
`@Nested`; every one of them is affected to some degree.

**Confirmed it is a Surefire-specific defect, not a JUnit Platform one.** Using
`org.junit.platform.launcher.core.LauncherFactory` directly, with the *exact* classpath Surefire's
own JVM uses for the probe class, correctly discovers and runs both the top-level and the nested
test (3 containers, 2 tests, both passing). Surefire's own invocation, every time, finds only the
top-level one. Explicitly naming the nested class (`-Dtest='OuterTest$NestedClass'`) does run it —
as a *separate* test suite report, not merged into the parent — which places the defect in how
Surefire's directory/class scanner decides which `.class` files are test candidates: an inner
class's compiled name (`Outer$Group.class`) does not match the default `**/*Test.class` family of
patterns, so it is apparently never selected on its own, and is also excluded from whatever the
outer class's own selection produces (unlike calling the JUnit Platform launcher directly).

**Tried and ruled out:** declaring `org.junit.platform:junit-platform-launcher` explicitly (it was
genuinely missing — `spring-boot-starter-test` does not bring it in transitively on Boot 3.5.5 —
and is still worth keeping, since relying on Surefire's own bundled copy is not best practice
regardless, but it did not fix this); bumping `maven-surefire-plugin` from 3.5.3 to 3.6.0 (made it
worse — the probe's top-level method stopped running too); forcing the `surefire-junit-platform`
provider as an explicit plugin dependency (no change).

**Not fixed in this session** (user decision, 2026-10-10): root-causing a Surefire-internal defect
was judged lower value than continuing ET-PLT-007 Phase 6's tenant-boundary conversions. The one
new test this defect would have silently broken (`EventAdminOperationsTenantBoundaryTest`) was
rewritten with flat `@Test` methods instead of `@Nested` groups, and now genuinely runs (4/4).
`junit-platform-launcher` was added as an explicit test-scoped dependency to all five Spring Boot
modules (`shared-library`, `catalog-service`, `booking-service`, `identity-service`, `api-gateway`)
regardless, since it is correct independent of this bug.

**Open, for a dedicated follow-up:** every pre-existing `@Nested` test class's grouped cases need
re-verification (they may be silently untested, not merely passing) — in the near term by
flattening to plain `@Test` methods (the only workaround confirmed to work), or longer term by
actually root-causing Surefire's scanner behavior (a Surefire version newer than 3.6.0, a
`<testSourceDirectory>`/`<includes>` configuration this investigation didn't try, or a filed
upstream issue). `TicketTierTenantBoundaryTest` and `EventVisibilityTest` are the two confirmed
affected files in catalog-service alone; the other ~18 files across the backend using `@Nested`
were not individually re-verified.

## F-056 · 2026-10-10 · ET-PLT-007 Phase 6: all 91 unscoped lookups individually resolved

Converting a genuinely-unscoped `repo.findById(id)` to `TenantGuard.locate(scope, repo.findById(id),
...)` does not reduce `TenantBoundaryLintTest`'s frozen count — the guard's own `unscopedById`
argument, needed for the platform-admin branch, is textually still a `findById` call. The test's own
javadoc already documented this for one case (`EventWriteGuard`, "+1 not -7"); this pass confirmed
it generalizes. The actual deliverable for this phase was never "the count reaches zero" — it was
"every one of the 91 flagged call sites is either genuinely tenant-scoped, or an explicit, auditable,
documented exemption." All 91 were traced individually to one of five outcomes:

1. **Genuinely unscoped, now fixed (33 of 91)** — a resolver already called `reads.*ForCaller(id)`
   (a `TenantGuard`-backed, already-guarded lookup) before calling the service method, which then
   re-fetched the same record by raw id with no filter at all. Defense in depth: the service now
   reads `CurrentTenantScope.get()` itself rather than trusting every present and future caller to
   guard first. Where several sibling write methods on one service shared this shape, they were
   consolidated into one shared `*ForCaller` helper rather than an inline `TenantGuard.locate` per
   method — a real reduction in the census, not just a wrap (booking-service's count fell by 14 more
   than the 21 methods fixed, for exactly this reason).
2. **Reached only from a Temporal workflow activity, no request ever exists (7 of 91)** — e.g.
   catalog's `EventServiceImpl.publishEvent`/`cancelEventWithDetails`/`rescheduleEvent`/
   `unpublishEvent`, escrow's `updateExpectedLockDate`, booking's `RefundServiceImpl
   .createAdminRefundRequest`. `CurrentTenantScope.get()` *errors* (not empty) with no request, so
   these construct an explicit `TenantScope.platformAdministrator("system:<workflow>", Set.of())`
   instead — the system-actor decision becomes auditable through the same `TenantGuard.locate` path
   rather than an ad hoc `IllegalArgumentException`.
3. **Already correctly guarded, or a pre-existing documented exemption (18 of 91)** —
   `EventWriteGuard`, `findVisibleById`, `eventVisibleToCaller`, `eventForCaller`,
   `tierVisibleToCaller`/`eventOwnedByCaller`, identity's `IdentityTenantReads` (4 methods),
   booking's `TenantReads` (4 methods) and `EscrowTransactionQueryResolver.accountVisibleToCaller`
   were already routed through `TenantGuard.locate`; catalog's `scheduleEventPublish`/
   `clearPublishSchedule` (reached from both a guarded resolver and a context-free workflow,
   already documented in the budget's own javadoc) and identity's `markMirrorPending` (the
   group-mirror sweep's own marking write, also pre-documented) were left exactly as they were.
   Nothing to fix; added to `GUARDED_PATHS` for completeness where missing.
4. **Dead or effectively unreachable (10 of 91)** — a public `findById` on the service interface
   with zero real callers (catalog `EventServiceImpl`/`TicketTierServiceImpl`, identity's four
   service impls, booking's `PayoutRequestServiceImpl`/`PromoCodeServiceImpl`/
   `TicketServiceImpl.findById`); booking's `RefundServiceImpl.requestPartialRefund` and
   `.bulkApproveRefunds` are reachable only through a `Submit.Kind.PARTIAL` or a direct service call
   neither of which the live workflow or resolver ever constructs (`RefundProcess` only ever builds
   `Kind.BUYER`/`Kind.ADMIN`; the real `bulkApproveRefunds` *mutation* calls `RefundProcess
   .bulkApprove`, a different method, not this one). Left in place, undocumented further than this
   entry — deleting unreferenced code is a separate decision from closing this spec.
5. **A genuine false positive in the regex census (23 of 91, 20 of them in booking's
   `RefundServiceImpl`/`PurchaseServiceImpl` alone)** — the model has an `organizationId`
   field (so it mechanically matches "tenant-owned"), but the real authorization boundary is
   something else entirely, and converting to an organization filter would be a regression, not a
   fix:
   - **Buyer/holder identity, not organization membership.** Booking's `TicketReservation`
     (`ReservationServiceImpl`, all of `PurchaseServiceImpl`) and the holder-facing paths of
     `Ticket`/`RefundRequest` (`RefundServiceImpl.requestRefund`/`findById`/`calculateRefundAmount`,
     `TicketServiceImpl.findById`) are gated by `@PreAuthorize` SpEL expressions comparing the
     caller's subject against `requestedBy`/`getRequestedBy()` (`@refundSecurityService
     .isRefundRequestOwner`, `@ticketSecurityService.isTicketOwner`) — a customer belongs to no
     organization and an org-based filter would refuse their own ticket.
   - **Two-party identity, not organization membership.** Identity's `OwnershipTransferRequest`:
     the schema resolver already restricts to exactly the current owner, the named recipient, or a
     platform administrator (`isPartyTo`); an organization filter would let every other member of
     either organization read a transfer that moves control of the business.
   - **Attribution, not a security boundary.** Catalog's `Location.organizationId` records who
     first added a shared venue; venues are deliberately public and reusable across organizations
     (`location(id): Location @auth(requires: PUBLIC)`), so the field was never meant to gate
     access.
   - **No tenant-scoped access path exists at all.** Booking's `ChargebackServiceImpl` and
     `PaymentAttemptServiceImpl`: every query and mutation across both schemas is
     `@PreAuthorize("hasRole('ADMIN')")` with no organizer-facing equivalent anywhere — a platform
     fraud/diagnostics subsystem by design. Building a `findByIdAndOrganizationIdIn` finder and a
     new `ErrorCode` purely for a branch no caller can ever reach was judged not worth the new
     surface, unlike the workflow cases in (2) where the finder/error code already existed for a
     sibling guarded path.
   - **An internal cross-reference, not a caller-supplied id.** `BankAccountServiceImpl.findById`
     (a GraphQL field resolver reading the bank account referenced by an already-tenant-scoped
     `PayoutRequest.bankAccountId`) and `RefundServiceImpl`'s `initiatePayaPayRefund`/
     `updateTicketForCompletedRefund` (reading the ticket referenced by an already-loaded
     `refundRequest.getTicketId()`) never take an id from the caller at all.
   - **An inherently platform-wide batch job.** `ReconciliationServiceImpl.reconcileEscrowAccount`
     is called only from `startEscrowReconciliation`'s sweep over *every* escrow account in the
     platform — there is no single tenant whose request this is.

New tests: `EventAdminOperationsTenantBoundaryTest` (catalog), `EventAccessServiceTenantBoundaryTest`
(identity), `AdminFinanceOperationsTenantBoundaryTest` (booking: bank accounts, escrow accounts,
promo codes) — each proves an outsider refused, the record unchanged, and a platform administrator
still succeeding (so the guard is verified non-vacuous). `GUARDED_PATHS` extended for every
newly-converted and newly-discovered-already-guarded method. Full `mvn -f backend verify` run clean
after each service's batch (catalog, identity, booking) before moving to the next.

## F-057 · 2026-10-10 · ET-PLT-007 Phase 8: four frontend mutations minted a fresh idempotency key on every call, and two plan items were already done by a different mechanism

Three findings from the frontend half of R6/R7.

**A real bug: idempotency keys that were never actually stable.** `useFinanceDecisions`
(`finance.hooks.ts`) and `usePayoutOps`/`useRefundOps` (`finance-ops.hooks.ts`) — `approvePayout`,
`approveRefund`, `retryPayout`, `createAdminRefund` — called `idempotencyKey: crypto.randomUUID()`
**inside** the mutation call itself, so every invocation minted a new key, not every click. The
button is disabled while its own mutation is in flight (`Button`'s `loading` prop drops `onClick`),
which stops an immediate double-click, but it does nothing for the scenario R6 actually exists for:
a response drops, the UI settles back to idle, the staff member clicks the same decision again —
and the backend's `IdempotencyGuard` sees a different key each time, so it replays nothing and the
decision is simply reprocessed. Compare the four buyer-facing dialogs
(`CheckoutClient`/`RefundDialog`/`TransferDialog`), which already minted a key once per `useRef`
and reused it — those were correct in spirit, just not persisted across a reload (the actual FE-2
gap) and, in `PayoutFlow.tsx`, built with `Math.random().toString(36)` rather than a real UUID.
Fixed with two small primitives in `libs/shared/src/lib/idempotency.ts`: `useIdempotencyKey
(storageKey)` for a component holding one key for one identity (sessionStorage-backed, so a reload
reuses it, with `regenerate()` for a deliberate new attempt at the same identity), and
`stableActionKey(cache, ...inputs)` for an imperative hook-level call with no component lifetime to
hang a ref off — keyed by the call's own inputs, not by id alone: `createAdminRefund` is keyed by
`(ticketId, reason)` rather than `ticketId`, because a second, later refund request for the same
ticket with a different reason is a genuinely new request, not a retry, and must not be refused as
`IDEMPOTENCY_KEY_REUSED`.

**FE-1 was already satisfied, by a different mechanism than the plan assumed.** The plan called for
wiring `PermissionGate`/`AdminGate`/`FinanceGate` (shared, exported, unused) into both consoles'
navigation. Both already have their own complete, tested mechanism instead: `apps/admin` filters
its drawer from `MODULES[].roles` and refuses direct navigation per module via `ModuleFrame`'s own
"No access" + "Back to my dashboard" state (proven by `e2e/browser/gating.spec.ts`, one case per
staff role); `apps/organization-admin` filters its drawer and blocks direct navigation via
`ConsoleShell`'s own `blocked` check against `ctx.capabilities` (proven by `e2e/browser/roles.spec.ts`,
"role MARKETER sees the no-access state on finance, team and bookings"). Wiring the generic gate
components in on top would have duplicated already-working, already-tested logic. (One attempt was
made and reverted in this session: adding a second `blocked` guard to admin's `ConsoleShell` — it
would have pre-empted `ModuleFrame`'s more specific "No access" render and broken
`gating.spec.ts`'s exact-text assertions. Caught before committing; no harm done.)

**FE-3's note was simply wrong, not the CSS.** The task file said `apps/ticketing` carries
`data-brand="ticketing"` with an "iris" palette. Neither the attribute name nor the app name nor
the color exist: the real attribute is `data-app`, the real values are `"platform"` (admin),
`"organizer"` (organization-admin) and `"buyer"` (ticketing), and `m3.themes.css`'s own header
comment already says the storefront gets "a slightly different teal" from the organizer/platform
palette — not a different color family. Corrected the task file's wording to match the CSS rather
than touching working, tested CSS to match a stale note. Added the missing
`apps/ticketing/e2e/brand.spec.ts` (admin and organization-admin already had one); installed
Playwright's Chromium (`pnpm exec playwright install chromium`, not present in this environment)
and ran it alone — passes. See F-058 for why "alone" is load-bearing: running it together with
`checkout.spec.ts` in one invocation, or `checkout.spec.ts` under more than one worker, fails for
reasons unrelated to this spec's own content.

## F-058 · 2026-10-10 · The ticketing e2e harness cannot run more than one `next dev` instance in `apps/ticketing` at a time, and Next 16's dev lock is per distDir

Found trying to run the new TS-6 double-submit spec (`checkout.spec.ts`) against a real browser.
Two separate, compounding problems, neither caused by anything this session changed:

**1. `harnessTest`'s per-test app spawning and Playwright's own `fullyParallel` default collide.**
`e2e-harness/browser/app-server.ts` starts a fresh `next dev` on a fresh `freePort()` for every
test that uses the `harnessTest` fixture (as opposed to `brand.spec.ts`, which uses plain
`@playwright/test` against the config's single shared `webServer`). With `fullyParallel: true`
and Playwright's default worker count (5 on this machine), several `harnessTest`-based tests start
their own `next dev` in `apps/ticketing` **at the same time** — and Next.js refuses a second `next
dev` process in the same project directory outright, regardless of which port it was asked to
bind. Only the first to win the race survives; every other test in the run fails with "Another
`next dev` server is already running." `--workers=1` does not fix this on its own (see next
finding).

**2. Next 16.2.11's dev lock file (`.next/dev/lock`) records a *different* port than the one the
process actually bound.** Confirmed by direct inspection: a `next dev --port 53671` process
printed "✓ Ready" on `http://localhost:53671` (the port it actually listened on) in the same
breath as writing `.next/dev/lock` with `{"port":3001,...}` — the fixed port this app is assigned
in `docker-resources/local-e2e`'s generated `.env.local`/`.env`, not the ephemeral one it was
invoked with. The *next* `next dev` invocation in the same directory — even run strictly serially,
even with `.env.local` removed entirely — reads that lock, sees port 3001 "claimed" by a PID that
may already be dead, and refuses to start. A stale lock from an abruptly-killed process (e.g. a
plain `kill`/`pkill` rather than letting Next's own shutdown handler run) compounds this: the lock
is never port-checked against reality, only presence-checked. **Open**: which of `publicOrigin`
(`next.config.js`, falls back to `"localhost:3001"` when `APP_URL` is unset) or something else in
Next 16/Turbopack's dev-lock writer is the actual source of the hardcoded port; not root-caused in
this session.

**Corrected cause**: the Next dev server lock is per distDir; the harness now uses its own distDir, so only a live incumbent on the same app's default `.next` was the collision.

**Consequence**: any `harnessTest`-based ticketing spec (`checkout.spec.ts` and presumably the
other `apps/ticketing/e2e/browser/*.spec.ts` files) cannot currently be run against a real browser
in this environment without first stopping the local-e2e dev stack (which also generates the
conflicting `.env.local`) and clearing `apps/ticketing/.next/dev/lock` by hand if a prior attempt
left it stale — and even then, a single clean serial run still failed with a freshly-self-written
stale-looking lock, which was not resolved in this session. `checkout.spec.ts`'s new TS-6 case
(`two rapid taps on Pay carry the same idempotency key`) is therefore **unverified by an actual
browser run** — it compiles, follows the harness's own established patterns exactly, and is
reviewed for correctness, but has not executed. The parallel case for `organization-admin`
(`flows_finance.spec.ts`, "payout request: two rapid submissions carry the same idempotency key")
hit a related-but-different failure (the page's expected content never rendered) under the same
investigation and is equally unverified by a browser run.

**Process note, not a code finding**: while investigating this, a process on port 3001 was killed
under the mistaken belief it was this session's own leftover test server; it was actually part of
the user's long-running local-e2e stack. Caught immediately, disclosed, and the user chose to have
it restarted via `docker-resources/local-e2e/apps.sh`, which is idempotent per app and did not
disturb the other two already-running apps. See memory for the standing rule this produced: check
a process's start time against the session's own before killing anything on a shared dev port.

**Not fixed in this session** — this is a harness/tooling issue orthogonal to ET-PLT-007, and
chasing Next 16/Turbopack's dev-lock internals further was judged lower value than finishing the
spec's actual acceptance criteria. Left as a flagged follow-up: either pin the harness to run
`apps/ticketing` browser specs with `--workers=1` **and** a guaranteed-clean `.next/dev/lock`
(e.g. delete it in `global-setup.ts` before the run), or root-cause why Next 16.2.11's dev server
lock ignores its own `--port` flag.

## F-059 · 2026-10-10 · Service-to-service call audit: every direct call is a legitimate exception; the federation bypass is in the schema, as flat denormalized duplicates of federated entities

**Question asked:** do the internal REST calls between catalog, booking, identity and the gateway
duplicate what federation could do?

**Inventory (all 4 services + shared-library; external providers — PawaPay, WhatsApp, Slack/SMS —
excluded, they are not inter-service):**

| Client | Calls | Kind | Verdict |
|---|---|---|---|
| booking → catalog `CatalogServiceClient` | `getEventById`; inventory `reserve`/`release`/`commit`/`restore` | server-side business reads and stateful commands | keep: callers are `ReservationServiceImpl`, `CheckoutActivitiesImpl`, `TicketTransferProcess`, `OrganizerAccess`, `EventGateAccess`, `TenantReads` — none is a GraphQL field resolver enriching a client response |
| booking → identity `IdentityServiceClient` | `checkAuthorization`, `checkSameOrganization`, `getUserOrganizations` | authorization decisions | keep: access control, not data composition |
| booking → identity | `notifyUser(s)`, `notifyFinanceLeads`, `financeLeadContacts`, `lookupByContact` | commands / search by contact (not by `@key`) | keep: federation resolves entities by key and does not send or search |
| catalog → booking `BookingServiceClient` | `soldTicketCount`, `hasOpenPayoutRequest` | guards inside `EventLifecycleActivitiesImpl` (Temporal activity) | keep: workflow business rules needing an authoritative synchronous answer |
| catalog → identity `IdentityServiceClient` | `checkAuthorization`, `checkEventAccess`, `getUserOrganizations`, `notifyApproval` | authz + command | keep |
| catalog → identity (**new, this pass**) | `getOrganizationName` | write-time denormalization | keep: federation composes query-time reads and cannot populate a stored field at creation |
| shared `RemoteTenantMemberships`, `HttpDurableRevocationStore` | tenancy and revocation | security boundary | keep, fail-closed by design (ET-PLT-007 R4/R7) |

**Why none of the read calls should move to federation.** Federation composes a *client's* query
across subgraphs through the router. These calls are a service's own business logic needing an
authoritative answer inside a transaction or a workflow; routing them through the router would add a
hop, lose the service-principal auth context, couple the service to the public schema, and still
could not express a write. Where federation applies it is already used: `Event.organizer: User` and
`Event.organization: Organization` return `{__typename, id}` stubs the router resolves against
identity (`EventFieldResolver`, `EventContentFieldResolver`).

**The real federation bypass — in the schema, not the call graph.** `Event` carries eight flat
stored copies of data identity owns (`organizerName`, `organizerEmail`, `organizerPhone`,
`organizerBusinessEmail`, `organizerBusinessPhone`, `organizerCompanyName`, `organizerFirstName`,
`organizerLastName`) beside the federated `organization`/`organizer` references; booking carries
`buyerName`/`buyerEmail`/`buyerPhone` the same way. They are what "defeats the purpose of
federation" here, and also an OWASP A02/A04 concern: personal data duplicated across services, stale
on change, impossible to erase from one place. `organizerName` was **never populated** by event
creation at all, so every event list that selected it failed with `NullValueInNonNullableField`
(found live: the admin approvals list returned "Something went wrong" for every event).

**Done in this pass:** `createEvent` now denormalizes `organizerName` once, at creation, from a new
read-only internal lookup (`GET /api/internal/authorization/organization-name`, `internal-read`
scope, covered by the auto-discovering `InternalSurfaceTest` for 401/403/200); an identity outage
falls back to "Unknown" and never blocks creation; `Event.organizerName` is null-safe on read so a
legacy row can no longer fail a whole list. Tests: `EventAuthoringTest` (success + outage fallback,
flat methods per F-055), `EventOrganizerNameFieldTest`, `CrossServicePermissionCheckTest`.

**Migrated (2026-10-10, second pass):**

| Field | Outcome | Why |
|---|---|---|
| `Event.organizerEmail/Phone/BusinessEmail/BusinessPhone/CompanyName/FirstName/LastName` | **Removed** from schema, entity, `EventFields`; stored values unset by migration `event-organizer-contact-strip` | Identity-owned PII; only consumer was the admin event detail, now `organization { id name businessEmail businessPhone }`. Identity returns those only to members and platform admins (`OrganizationPrivateFields`), so access is *narrower* than the old flat `@auth(AUTHENTICATED)`. |
| `EventEscrowAccount.organizerName`, `PayoutRequest.organizerName`, `AccountSummary.organizerName` | **Replaced** by `organization: Organization` (booking reference stub, id only) | Admin money surfaces; name now identity's and always current. The escrow account never had the name populated. |

**Kept on purpose, with the reason:**
- `Event.organizerName` — public attribution on anonymous discover/event pages. The anonymous
  `_entities` path to identity is not established (identity's public-operation rules would have to
  allow it), it costs a cross-subgraph hop per card, and a name is not PII. Stored at write time,
  null-safe on read. Revisit only if a rename-propagation requirement appears.
- `Ticket.buyerName/Email/Phone` — booking's own holder snapshot, not a copy of an identity entity:
  checkout contact details, rewritten by the transfer workflow, searched by `TicketSearch`
  (search-by-contact cannot be federated). The federated `Ticket.buyer: User` already exists for
  identity-owned data.

**Closed (2026-10-10, third pass):**
1. `Ticket.buyerName/Email/Phone` are now decided per read by `TicketHolderContactAccess`: the holder,
   a caller whose tenant scope covers the ticket's organization, or a platform administrator; null for
   everyone else. `Ticket.buyer` previously *provided* the cached name/email/phone to the router for any
   caller, so identity's own gate never ran; it now provides them only to an entitled caller.
   Tests: `TicketHolderContactAccessTest` (holder, member, admin, other buyer, other organization,
   anonymous, missing scope, federation reference). `CheckInEvent.buyerName` is unchanged: its only
   source is the organizer-gated live-dashboard query.
2. The stored `organizerName` on `EventEscrowAccount` and `PayoutRequest`, the
   `CreateEscrowAccountInput.organizerName` input, the service parameter and both collection validators
   are removed; migration `organizer-name-copy-cleanup` unsets existing values (runs before validators
   tighten). Test: `OrganizerNameCopyCleanupTest`.
3. Legacy events with no stored organizer name are repaired on first read (identity lookup, written
   back); an identity outage answers "Unknown" and writes nothing so the next read retries. No startup
   dependency on identity. Test: `EventOrganizerNameFieldTest`.

## F-060 · 2026-10-10 · The first live buyer checkout found six defects that no test could reach: validators and a resolver signature that only a real write or a real DGS call exercises

**Found by:** driving a real buyer (sign-in → reserve → pay) through Chrome against the full local stack,
with a double-tap on Reserve and Pay. Every one of these passed the suite, because the test template has
no `$jsonSchema` validator and no test called the resolver.

| # | Defect | Effect | Fix |
|---|---|---|---|
| 1 | `booking-reservations` validator: `_id` was `objectId` only; checkout names a reservation by a deterministic UUID string | every `reserveTickets` failed at `CheckoutHold` (3 retries, SERVICE_ERROR) | `_id` is `objectId` or `string`, as `tickets-schema` already was |
| 2 | `ReservationFieldResolver.remainingSeconds(dfe, Instant now)`: DGS fills extra parameters from GraphQL arguments, so `now` was always null | the reserve response failed on `remainingSeconds`; the page never advanced | injected `Clock`; flat test `ReservationFieldResolverTest` incl. a signature guard |
| 3 | `booking-escrow-accounts` validator required `organizerId`; the event-published envelope names only the organization | no event could ever open an escrow, so no payment could be credited | `organizerId` optional (see "Open" below) |
| 4 | `tickets` validator: `reservationId` pattern ObjectId-only (it is a UUID now); `createdBy`/`updatedBy` rejected the platform actor `system` | ticket issue failed after the payment had succeeded | patterns accept ObjectId, UUID, and `system` |
| 5 | `tickets` validator required `eventTitle`, `eventDate`, `ticketCategory`; issuance never sets them (the ticket carries tier id/name; the event is federated) | same | dropped from `required`, still allow-listed |
| 6 | `journal-entries` and `chart-of-accounts` validators: per-event sub-account suffix `[A-Z0-9]+`; the code writes lowercase hex (`2010-6aca0c80`) | same | suffix `[A-Za-z0-9]+` |
| 7 | `Ticket.eventTitle: String!` is a stored copy that issuance never sets (fix #5 made it absent); `My tickets` failed on it | the buyer's ticket list errored | `TicketEventFields`: stored value, else one catalog lookup written back, else "Unknown"; `TicketEventFieldsTest` (5) |

**What the live run did prove (idempotency, the point of the exercise):**
- Two `ReserveTickets` mutations fired in the same tick carried the same `idempotencyKey`, and the
  server held exactly one reservation and one completed ledger row (`booking:reserveTickets|<key>`).
- `PayReservation` double-tap: the UI sent one request (the button disables on the first), one
  payment intent, one attempt, one ledger row.

**Why the suite missed all of it:** `MongoSchemaValidationConfig` applies validators at startup, but the
unit/integration templates write through a collection with no validator ("silently in tests, which use a
template with no validator" is already written in the schema's own `_class` description). A write test
that goes through the real validator is the missing layer.

**Closed (2026-10-10, after the business decision "organization permissions, current event details"):**
1. Payouts are decided by the organization. `createPayoutRequest` reads the organization from the escrow
   account (never from the input) and asks identity for `payout:request` there, on the event, so a member
   holding the permission may request regardless of who created the event; event grants still decide alone
   where present; the organization's status still gates. An escrow account of another organization, an
   unknown one, and a denied caller all read the same. Platform staff keep acting under the organizer's own
   authority, and the organization they resolve to must be the escrow's. The settlement service matches the
   escrow and the bank account on `organizationId`, `Submit` carries it, and the payout records who asked.
   `payoutEligibility` and the escrow/event read checks (`EventSecurityService`) use the same decision and
   the caller's tenant scope. New: `PayoutAccess`, `PayoutAccessTest`, `EventSecurityServiceTest`; changed:
   `TeamFinancialAccessTest` (+2), `RequestPayoutIdempotencyTest`, `PayoutDecisionIdempotencyTest`,
   `PayoutSettlementServiceTest`, `PayoutWorkflowTest`.
2. Event name and date are the event's current ones. `CurrentEventDetails` asks catalog (30 s per-event
   window, failures never kept); `Ticket.eventTitle/eventDate`, `TicketTransfer.eventTitle`, ticket resend,
   transfer messages, the organizer activity feed and transactions, and the live dashboard title all use it,
   in memory only. A stored value is the fallback on an outage. Tests: `CurrentEventDetailsTest`,
   `TicketEventFieldsTest`, `TicketResendTest`, `TicketTransferTest`.

3. Payout accounts belong to the organization (decision 2026-10-10). Adding, listing, viewing, changing,
   deleting, setting the default and starting or confirming verification need the organization's
   `payout:request` (`BankAccountAccess`: member of the organization + identity's decision; platform staff
   act on all; every refusal reads as an unknown account). The default account is the organization's. The
   list and default queries return the accounts of the organizations the caller manages whoever added them,
   and the account number is masked on every read (`BankAccountFields`, `AccountNumberMask`); an update that
   re-submits the masked number is not a new destination. Tests: `BankAccountAccessTest`,
   `BankAccountFieldsTest`; `BankAccountOwningOrganizationTest`, `AdminFinanceOperationsTenantBoundaryTest`
   updated.
4. Finance records keep the event name recorded when the money moved (decision 2026-10-10); `event: Event`
   on `EventEscrowAccount` and `PayoutRequest` is the event now, resolved by catalog, and the admin escrow
   and payout lists search and show it beside the recorded name. `Ticket.event` no longer `@provides` a
   cached title or date (the router would have served the old name); booking's unused `@external` Event
   fields were removed so the supergraph composes. Tests: `EventReferenceFieldsTest`.

**Open:**
1. A validator-conformance test: save one real instance of each entity through a validator-enabled
   collection (Testcontainers) so a schema/entity drift fails in the gate, not in front of a buyer.
2. Not driven through the organizer web app: the live pass below used the buyer app's session (a member
   added to the organization) calling the operations directly. The organizer UI for payout accounts and
   payouts still needs a pass with a real organizer sign-in.
3. `bank:manage` already exists as a permission (held by the organization's admin role by default); payout
   accounts are gated by `payout:request` per the decision, so an admin cannot manage accounts until the
   owner switches `adminsCanRequestPayouts` on. If admins should keep managing accounts, gate on
   `bank:manage` instead.
4. A refused payout request surfaces as `COMMAND_NOT_WELL_FORMED` (the existing mapping of the business
   validation refusal); a non-transient validator failure inside `createRequest` is retried by Temporal
   instead of refused at once, and the caller times out while the activity retries (the idempotency row
   stays in flight until it expires).

**Verified live (2026-10-10):** a buyer signed in through the ensure flow, double-tapped Reserve (one
reservation, one ledger row), paid (one intent, one attempt), the reservation went `CONFIRMED`, one ticket
`ISSUED`, one commission and one journal entry written, escrow credited the net K 142.50, and `My tickets`
rendered it. A first purchase whose hold lapsed while these were being fixed settled `FAILED` with a
`PAID_AFTER_EXPIRY` escalation, as designed. Targeted tests: 18/18.

**Live pass on payout accounts and payouts (2026-10-10, after decisions A and B):** a second member (role
ADMIN) was added to the test organization and the buyer app's session used to call the operations. With
`adminsCanRequestPayouts` off: add account, read one account and request payout refused, list empty. With
it on: account created (masked `****4567`, `organizationId` set, `organizerId` the member), listed by
organization whatever organizer id was passed, verified by the member, and a payout of K 142.50 requested
with a deliberately wrong `organizerId` (ignored; the payout records the requester) and the same request sent
twice at once: one `PENDING` payout, one workflow. Two further validator defects found and fixed:

| # | Defect | Fix |
|---|---|---|
| 8 | `booking-payout-requests` validator: `_id` objectId only; the payout workflow generates a UUID | `_id` objectId or string |
| 9 | same validator: `accountNumber` `^[0-9]{10,20}$`; the payout stores the masked number `****4567` on purpose | pattern also allows `****` + 4 digits |

The test fixtures (member, escrow state, account, payout, workflow) were removed afterwards.

**Organizer console routing and the work it exposed (2026-10-10, against the design canvas "Showstop app designs"):**

| # | Finding | Fix |
|---|---|---|
| 10 | The console gated every route on the `ORGANIZER`/`ADMIN` **realm role** (proxy, dashboard layout, step-up). The platform grants `ORGANIZER` to the owner at approval only; an invited admin, manager, marketer or contributor is a member with no realm role, so they looped `/dashboard` ↔ `/welcome` and could never enter | Routes need a session; who belongs is the organization lookup (owner or active member) and its status; the backend decides what they may do. Test: `bff.config.test.ts` |
| 11 | The 52 `@auth(requires: ORGANIZER)` operations (booking 17, catalog 35) also read that realm role, so a team member passed no organizer operation | The membership mirror now grants `ORGANIZER` to a non-owner member when it mirrors an active membership, and releases it when the last membership goes (`GroupMirrorActivitiesImpl`; owner rows untouched, approval still grants the owner). Verified live: the sweep granted the role to the test member. Test: `GroupMirrorOrganizerRoleTest` |
| 12 | The `my*` dashboard queries were gated on the realm role only and aggregated by the **person** (`organizerId` of the ticket, the event's creator), so a member saw zeros and the owner saw only events they created | Gated on the real permission per query (`analytics:view`, `event:view`, `financial:view`) through identity; all ticket-derived figures keyed by the organization. Upcoming events and activity take name/date from the event |
| 13 | `EventEscrowAccount.totalDeposits/totalWithdrawals/totalRefunds/lockUntil` are non-null/served from fields the entity renamed (`totalCredited`, `totalDebited`, `totalRefunded`, `holdUntil`) with no mapping: the escrow list failed whole. `organizerId` non-null though escrows open by organization | Field resolvers for the four; `organizerId` nullable on `EventEscrowAccount` and `AccountSummary`. Guard: `EntityBackedFieldsParityTest` (every non-null field of an entity-backed type has a property or resolver) |
| 14 | `payoutEligibility`, the payout-account and dashboard queries accepted any `ORGANIZER`-role holder | covered by 12 and the earlier payout/bank-account access work |
| 15 | A class compiled by the IDE (Eclipse compiler, "Unresolved compilation problem") in `target/classes` is packaged by `mvn package` without being recompiled, and the error appears only at runtime. Hit twice here | Not a pom setting: IntelliJ's build writes `target/classes`. After editing, a `mvn compile`/`package` that recompiles the touched sources (they were newer) is the check; consider a CI build from clean |

Design alignment: the finance sidebar entries are now "Escrow & payouts" and "Bank accounts", as drawn; the organizer's escrow and payout lists show the event's current name beside the recorded one.

**Still open from this pass:** the design's "Viewing as <role>" chip and "Switch app" entry are not built; an invited member's role is granted by the next mirror sweep (about a minute), not at the instant of acceptance; existing members created before this change are repaired only when their `mirrorPending` is set.

**Decisions on team access (2026-10-10):**
- A person belongs to one organization at a time (service rule `OneOrganizationPerPerson`, partial unique index
  `uniq_one_active_organization_per_person`, error `MEMBER_IN_ANOTHER_ORGANIZATION`; the booking resolver refuses
  rather than choose between two). No "Viewing as" and no "Switch app" in production. No backfill code: the
  platform is in development and the dev data was corrected by hand (it was already clean).
- Access for a newly accepted member is asynchronous (option A): the membership exists at once, the platform
  organizer role follows on the next mirror sweep (about a minute). The console shows "We're setting up your access"
  with a re-check (`AccessSettingUp`, `hasOrganizerAccess`), and the acceptance page says it takes about a minute.
- **Sending an invitation does not create an account.** The invitation records the addressee's email or phone and
  sends a link. Accepting needs an account that already exists (`USER_UNKNOWN` otherwise), and that account must own
  the contact the invitation was sent to (`INVITATION_NOT_ADDRESSED_TO_CALLER` otherwise). A person without an account
  creates one by the ordinary sign-in with that contact (code by email or WhatsApp), which the acceptance page sends
  them through; the account is created at that sign-in, then they accept.

## F-061 · 2026-10-10 · ET-PLT-007 Phase 9: what the closing pass built, what it found, and what stays open

**Built.** Explicit, bounded JWKS cache (`keycloak.jwks-cache-ttl`, default 5 min, at most 15; refresh-ahead off so the
TTL is the bound; an unknown `kid` still refetches, rate limited to once per 10 s). A single named platform-wide path,
`PlatformWideAccess`, with a fixed `Reason` per site and an audit sink interface; `PlatformWideBypassLintTest` fails
the build on a bypass outside it. `transferBetweenPlatformAccounts` now goes through `IdempotencyGuard`. Three identity
endpoints booking had been calling for a long time without them existing: `POST /api/internal/notifications/users`,
`/users/batch` and `/api/internal/users/lookup` (internal-write scope, anti-enumeration lookup, masked contact).

**Defects the new tests found and that are fixed.**
- The authorities converter threw `ClassCastException` on a claim of the wrong shape (a signed token whose
  `realm_access` was not an object failed the request with a 500 instead of contributing nothing).
- `@auth` on a field with no authentication answered `ACTOR_NOT_PERMITTED`; it is now `ACTOR_NOT_AUTHENTICATED`.
- The JWKS held the first key set it fetched for the life of the process: a withdrawn key verified until restart.
- Booking's holder-message batch (200) exceeded what identity accepts (100); it is 100.
- `ContactOtpKeycloakIT.staffRefused` left an `ADMIN` role in the buyers realm of the shared Keycloak; it now removes it.

**Decisions recorded.**
- `@auth` and method-level `@PreAuthorize` are the same decision; the build accepts either (R3 reworded).
- Five lines of platform-staff checks remain in booking (`isPlatformStaff` x2, `SUPPORT_AUTHORITIES`, `DualControlRules`
  x2). All include `FINANCE`, which is not a tenant-wide authority, so routing them through `PlatformWideAccess` would
  have narrowed finance's access. `PlatformWideBypassLintTest` budgets them at 5.
- The platform-wide record is a structured `audit.platform-wide` log line. A queryable row needs a new
  `AuditAction` value and a validator change in identity (`audit-logs-schema.json`); that belongs with ET-PLT-009.
- `processRefundRequest` / `rejectRefundRequest` take no key: both are state transitions that refuse or return the
  stored request on a repeat, so no money moves twice.

**Open.**
1. **Closed:** the staff export's `SCANNER` role was removed (2026-10-10, on the owner's instruction). The running dev
   Keycloak keeps the role until it is deleted by hand or the realm is re-imported, because the compose import skips
   a realm that already exists.
1a. **Closed, and the nightly sweep with it (2026-10-10, owner's decision):** `POST /api/internal/keycloak/sync/all`,
   the `syncAllUsersFromKeycloak` mutation, `UserBackfillWorkflow` and the `identity-user-reconciliation` Schedule are
   deleted (the Schedule was also deleted from the dev Temporal namespace). A profile is kept right by the listener's
   per-user `UserSyncWorkflow` and the lazy repair on next sign-in; drift for someone who never signs in again is
   accepted. The admin mutation `syncUserFromKeycloak(userId)` and the console's "Sync from Keycloak" action were
   removed too: accounts are created platform-first, so there is nothing a manual pull repairs that the listener's
   per-user workflow and the next sign-in do not. `UserSyncService.syncUser` is now reached only from
   `UserSyncActivities` inside the per-user workflow.
1c. **Every non-workflow synchronization was removed (2026-10-10, owner's instruction).**
   - `AccountRepair` and its `AccountRepairWorkflow`, `identity-account-repair` Schedule, runner, activities and
     `identity.account.repair.*` properties (drift classes D1..D9: a scan of every account and every Keycloak user every
     15 minutes). The Keycloak port lost `listUsers` and `readUserByUsername`; `KeycloakService` lost `getAllUsers`.
   - The member service's inline Keycloak group writes (`addToKeycloakGroup`, `removeFromKeycloakGroup`,
     `updateKeycloakGroup`) and the two best-effort group methods they called. A membership change now carries
     `mirrorPending` in the same save and the group-mirror workflow applies it; a role change leaves the member's other
     role groups before joining the new one.
   - The contact-change marker cleanup is retried until it lands (it was capped at six tries and then left to the repair
     pass). A workflow terminated mid-cleanup leaves the marker; that is the one stuck state with no automatic clearing.
   - Not changed: `UserServiceImpl` creates a staff user in Keycloak inline in the admin `createUser` mutation (idempotent,
     a 409 reads the user back); moving it into a workflow changes that mutation's contract.
   - Correction to an earlier statement: the "lazy repair on the person's next authenticated request" the spec describes
     is not implemented as a separate pass; what exists is the per-login `AccountEnsureWorkflow`, which applies roles and
     attributes when the person signs in with their contact.
1b. **Permission resolution is one implementation now.** The organization-status gate applies on the GraphQL path
   too (a member of an organization whose status does not permit an action is refused `event:create/edit/delete/
   publish` and `payout:request`); the internal authorization endpoints still ignore platform roles; expired event
   grants are ignored on both. An event-grant holder is not subject to the status gate on either path.
2. **Closed (2026-10-10, owner's decision):** the workflow request carries ids only. `UserNotifier` renders the message
   when the request arrives and stores it as the pending `identity_notifications` row (the row's id is also the
   deduplication key); the workflow's `record` activity finds that row. A start that fails removes the row again.
3. **Closed:** the six new templates were rewritten to the platform's wording rules (what happened, one next action,
   what becomes of the ticket, no links, another person's name only where the message is about them); the rules are on
   `NotificationRules.Message`.
4. **Closed:** `NotificationWorkflow.Request` is its original five fields again, so existing histories replay unchanged.
5. **Closed (2026-10-11):** the browser harness runs with its own `distDir` (a dev server on the same app no longer
   blocks it) and both double-submit specs pass in real Chrome, desktop and phone: the buyer's two rapid taps on Pay
   and the organizer's two rapid payout submissions each send one request carrying a real idempotency key. Two defects
   in the specs themselves were fixed: the buyer fixture lacked the mobile-money operator list the pay step now needs
   (so Pay stayed disabled), and the specs read the key from the wrong level (`input.idempotencyKey`). The specs also
   assumed the page lets two requests through; it holds the second, which is the stronger result.
