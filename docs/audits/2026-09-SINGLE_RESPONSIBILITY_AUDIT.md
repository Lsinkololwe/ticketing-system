# Backend dead-code and single-responsibility audit — 2026-09-19

Scope: the six backend modules (`shared-library`, `identity-service`, `catalog-service`,
`booking-service`, `api-gateway`, `keycloak-extensions`) and their `application*.yml`. The frontend was
out of scope. Baseline: the tree after the Temporal migration (D-21…D-37), staged in the git index as
the baseline commit.

## 1. Result in numbers

| | Before | After | Change |
|---|---:|---:|---:|
| Main Java files | 1,138 | 1,010 | −128 (56 of them `package-info.java`) |
| Main Java lines | 131,537 | 117,343 | **−14,194 (−10.8%)** |
| Declared methods (inventory) | 5,992 | 5,104 | −888 |
| Declared types (inventory) | 1,383 | 1,297 | −86 |
| Custom config keys | 256 | 178 | −78, and 0 left unbound |
| `application*.yml` lines | 1,717 | 1,657 | −60 (−143 removed, +83 for token revocation in catalog and booking) |
| Internal REST endpoints | 49 | 26 | −23 that no service or frontend called |
| Tests | 1,208 | 1,328 | +120 (new tests), all green |
| Services that boot against real MongoDB + Temporal | not checked | 3 of 3 | — |

Per module (lines): shared 13,819→12,778 · identity 31,047→27,638 · catalog 21,453→18,317 ·
booking 60,813→54,661 · gateway 2,006→1,867 · keycloak-extensions 2,399→2,082. Shared absorbed 19
per-service copies and still shrank.

Snapshots: `docs/audits/inventory-before/` and `docs/audits/inventory-after/` (CSV per method, type
and config key, with the verdict and its evidence; after-state also `fields.tsv` and
`internal-endpoints.tsv`).

## 2. Method

**The deletion rule.** A symbol stayed if any of these held; otherwise it was deleted, together with
tests that existed only to test it.

1. Reachable from an entry point: a DGS field in `schema.graphqls`, a REST mapping, a Service Bus
   consumer, a Temporal workflow or activity (including handler implementations), a Schedule, a
   runner, a SpEL `@bean.method(...)`, a Resilience4j `fallbackMethod`, an SPI in `META-INF/services`,
   an auto-configuration import, a bean-property accessor.
2. Named by a `spec.yaml` of any status except `withdrawn` (GraphQL operations, workflow activities,
   collections): planned work is not dead.
3. Kept by a reviewed entry in `backend/tools/reachability/keep.txt`, with its reason.

**Evidence, joined per symbol** by `backend/tools/reachability/inventory.py`: a name-based reference
graph over `src/main` and `src/test` (comments stripped, `implements` clauses not counted as use,
iterated to a fixpoint so code used only by dead code is dead), entry-point annotations, spec names,
and JaCoCo method coverage (new opt-in `-Pcoverage` profile; shared-library measured under each
service's tests through `report-aggregate`). Coverage is evidence, never a deletion trigger:
reachable-but-unexecuted code is a **test gap**, not dead code.

**Deletion** by `prune.py` (declaration + Javadoc + annotations + dangling imports, comment-masked brace
matching), repeated until the inventory reported zero DEAD rows (six rounds the first time — a deleted
method leaves its private helpers uncalled). Every round compiled every module including tests.

**Second pass — inside the classes.** The inventory sees methods and types; it cannot see a field
nothing reads, a parameter nothing uses, or an internal endpoint nothing calls. Four more checks,
each kept as a tool:

- **PMD** (`tools/reachability/unused-code.xml`): unused locals, parameters, private members, imports.
- **`fields.py`:** fields nothing reads, including the Lombok, record and builder fields PMD skips
  by design, split by whether they cross a service, provider or database boundary.
- **`endpoints.py`:** every `/api/internal/**` mapping against the literals of every other module and
  the frontend. An internal endpoint exists only for a caller, so one without a caller is dead.
- **`GraphQlDtoSchemaParityTest`** (all three services): each GraphQL DTO against the schema type of
  the same name. DGS binds by property name, so a rename on either side fails silently. This found
  more than dead code — see defects 14–17.

**Verification** after each phase: all suites; lint ratchets lowered in the same change when they
reported a gain; finally a real boot of each service against MongoDB and Temporal. The boot found what
no test could: defects 7, 8 and 9 in §5 (7 and 8 pre-existing, 9 pre-existing and then generalised into
a test), and 11 plus a missing-auditing regression that this audit's own consolidation had introduced.

## 3. What was removed

### Whole classes (143 main files deleted; 76 added, most of them moves)

- **Unreachable services and support:** `RoleSyncService`/`RoleSyncServiceImpl` + `AuditLogRepository`,
  `OAuth2TokenProxyService`, `MongockConfig` and the Mongock dependency (no change units existed),
  `TenantValidationService` (superseded by ET-PLT-007, recorded as an orphan in ET-PLT-001),
  identity `domain/base` interfaces nothing called through (`Auditable`, `Identifiable`, `Timestamped`),
  `MongoValidationErrorHandler`, `PayoutIssueType`, `PayoutResolutionType`.
- **A dormant duplicate security stack:** `TokenBlacklist{AutoConfiguration,Filter,Service,Properties}`
  (switched on by a property nothing set; revocation ET-IDN-003 and the gateway's session blacklist do
  this job) and `KeycloakSecurity{AutoConfiguration,Properties}` (beans nothing injected). The key
  format they shared with the gateway survives as `RevocationKeys`, now used by the gateway too.
- **13 exception types nothing threw**, and their translator mappings: booking `DoubleBooking`,
  `PaymentFailed`, `RefundNotAllowed`, `TicketAlreadyUsed`, `TicketNotFound`,
  `InsufficientRecoveryFunds`; catalog `TicketTierNotFound`, `ApprovalWorkflow`; identity
  `DuplicateResource`, `InvalidInvitation`, `OrganizationNotFound`; shared `TenantIsolation`,
  `MongoSchemaValidation`.
- **15 GraphQL response wrappers no resolver returned** (`*MutationResponse` in booking and catalog).
- **A public debug endpoint:** identity `AuthDebugController` at `/api/debug/auth`, `permitAll`, marked
  "remove in production".

### Members (≈830 method declarations net)

The largest groups, all with no caller by any spelling:

- **Sweep-era queries and operations** the Temporal migration replaced: `findExpired{Grants,Invitations,
  Transfers,Reservations,Escalations}`, `findDueForReminder`, `findByStatusAndHoldUntilBefore`,
  `expireOld{Invitations,Grants}`, `processScheduledReconciliations` (a `TODO` stub), the pre-Temporal
  `PaymentAttempt`/`PaymentIntent` state methods (`markConfirmed`, `recordPoll`, `scheduleRetry`, …),
  `PaymentService.handlePaymentCallback`, `findExpiredPayments`.
- **258 repository methods** nothing called — 20 on chargebacks, 19 on reconciliation runs, 18 on
  events, 15 on journal entries, 14 on approval timelines, 13 on escrow transactions, and the rest.
- **Service-layer paging duplicates** in catalog: every `*Cursor`/`*Admin` finder — the resolvers build
  their Connections themselves.
- **Unused metrics and logging helpers:** 12 `PaymentMetrics.record*`, 4 `PciDssLogger` methods.
- **28 injected fields** nothing read (Lombok constructors made them look used), and the
  `Clock` parameter they dragged through `CachedRevocationCheck`.
- **Beans nothing injected:** `oauth2WebClient` ×2, gateway `userKeyResolver`, `pathKeyResolver`.

### Second pass: fields, parameters and internal endpoints

- **23 internal endpoints no service or frontend called** — identity's whole `InternalUserController`
  (6, including user lookup by e-mail), 6 authorization checks, `keycloak/sync/user/{id}`; catalog's
  inventory snapshot, sold-ticket write, availability and category lookups; booking's ticket lookups
  and `/{ticketNumber}/validate`. Each took its service methods with it (`checkOwnership`,
  `checkMembership`, `getDefaultOrganizationForUser`, `updateSoldTickets`, …) and two shared DTOs
  (`UserSummaryDto`, `TicketSummaryDto`). Kept without a caller because a spec or operators use them:
  `keycloak/sync/all`, `otp/status/{phone}`, `keycloak/sync/health`.
- **Dead classes the name-based inventory missed** because a live type shares the simple name: web
  `ChargebackStats` (behind `ChargebackService.ChargebackStats`), identity `organizer/SocialLinksInput`,
  the Keycloak extension's duplicate OTP client methods and result classes, `PayerDetails`,
  `RecipientDetails`, `MobileMoneyAccountDetails`, `NotificationOffsetPage`.
- **Fields with no reader, on both sides of the wire where both sides are ours:** inventory results
  (booking never read the counts catalog computed), `SharedOrganizationResponse` roles and
  `OrganizationMembershipInfo` name/owner (identity computed, nobody read), `AuthorizationResult`
  slug and permission set, `AuthorizationRequest.resourceId`, `EventSummaryDto` price range,
  `InventoryRestoreRequest.referenceId` (always sent null).
- **Provider payload fields nothing reads:** Keycloak token and introspection extras, PawaPay callback
  payer/recipient/completion time, deposit pre-authorisation code (always null). Safe because the
  platform mapper ignores unknown properties.
- **12 payment meters registered and never recorded** (refund, payout, circuit-open, four timers) —
  exported as permanent zeros. This also ends the `pawapay.webhook.processing_time` registration
  conflict booking logged at boot.
- **GraphQL output fields the schema never exposed:** `success`/`message`/`errors` on five response
  types, `RefundCalculation.processingFee` (already exposed as `platformRetains`).
- **Unsettable inputs:** `TicketFilterInput.limit/offset`, `CreateRefundRequestInput.requestedBy`,
  tier `originalPrice` on both tier inputs (the service copied an always-null value).
- **Injected fields, locals and parameters:** 4 injected fields, 7 locals (`oldRoles` ×2, `nowLocal`,
  `startTime` ×2, `runningBalance`, `oneWeekFromNow`), 11 private-method parameters, 90 unused imports,
  `PaymentAttemptStatus.canTransition`, `messaging.provider`.

**Kept on purpose, with the reason recorded:** fields Temporal serialises in workflow payloads and
query results (the SDK's default mapper fails on unknown properties, so deleting one breaks recorded
histories); `success` flags the Keycloak extension reads through Gson; Resilience4j fallback
parameters (the signature must mirror the guarded method); `@Document` fields (see §6).

### Configuration (78 keys)

Removed because nothing read them: identity `identity.{service,cdc,features,notifications}.*`,
`messaging.storage.*`, most of `file-upload.*`, `file-storage.local.{serve-files,cleanup-on-shutdown}`,
`aws.{credentials,s3.encryption,s3.lifecycle}.*`, `services.{booking,catalog}.url`, `mongock.*`;
catalog `catalog.{service,event-approval,features}.*` and the unused `eventOutput-out-0` binding;
booking `booking.{service,inventory}.*`, `booking.reservation.{sweep-interval,ttl-grace}`,
`booking.payment.max-pending`, `payment.{timeout,commission}.*`, `payment.escrow.{hold-period,
allow-partial-payout}`, `pawapay.retry.*`, `payment.gateway.fallback.{max-retries,
health-check-timeout-ms}`, `platform.{qrcode,refund,features}.*`, `integrations.*`, `spring.cache`
(nothing cached); gateway `keycloak.{url,realm}`; identity `messaging.provider`.

**Rebound rather than removed** (the key was right, the code read another): `booking.payment.timeout`,
`file-storage.type` for S3, `catalog.export.*` (was `app.export.*`, undeclared), and
`platform.events.consumer.*`, which every service declared and nothing bound.

Also: `security.debug.enabled` now defaults to `false` everywhere; `allow-bean-definition-overriding`
is off in all three services (it existed only so each service's Jackson config could override
shared-library's).

## 4. Shared-library consolidation and package layout

**Moved into shared-library as auto-configurations** — each existed as two to four near-identical
copies, and each is now covered by a test once instead of three times:

| Now in shared-library | Replaced |
|---|---|
| `infrastructure.temporal.TemporalGateway` + auto-config | 3 × `TemporalGateway` |
| `workflow.Refusals`, `workflow.Refusal` | 3 × `Refusals`, 2 × `Refusal` |
| `config.PlatformJacksonAutoConfiguration` (a `Jackson2ObjectMapperBuilderCustomizer`) | 3 × `JacksonConfig` + `SharedJacksonConfig`, which replaced Boot's mapper with a `@Primary` one |
| `persistence.PlatformMongoTransactionAutoConfiguration` | 3 × `MongoConfig` |
| `config.PlatformAuditingAutoConfiguration` (JWT-subject auditor) | 3 × `MongoAuditingConfig` |
| `security.tenancy.TenantScopeAutoConfiguration` + `TenancyProperties` | 3 × `TenantScopeConfiguration` |
| `security.tenancy.RemoteTenantMemberships` + auto-config | booking and catalog `IdentityTenantMemberships` |
| `security.InternalServiceWebClients` + auto-config | 2 × `OAuth2ClientConfig`, `WebClientFallbackConfig` |
| `security.ServiceSecurity` + auto-config | booking and catalog `SecurityConfig` |
| `event.RetryBudget` bound as `@ConfigurationProperties` | the hard-coded `RetryBudget.DEFAULT` |
| `constants.PayoutMethod` | identity's copy |

**Deliberately not moved:** `@Document` entities, repositories, services and resolvers. Each service
owns its data; a shared entity couples every service's schema and deploy to the others, and
`SharedLibraryBoundaryTest` (ET-PLT-001-R5) forbids it. Identity keeps its own `SecurityConfig` (it
needs CORS and a different public surface); the shared chain steps aside for any service that
declares one.

**Package moves** (within the existing layer layout; the per-domain module layout of ET-PLT-001 T4 was
out of scope by decision):

- booking `dto/*` (PawaPay callbacks) → `web/rest/dto`; `event/consumer` → `infrastructure/messaging`;
  `event/listener/CatalogEventListener` → `service/CatalogLifecycleService` (no longer a listener);
  `infrastructure/gateway/domain` → `infrastructure/gateway/model`, gateway `PayoutRequest` →
  `GatewayPayoutRequest` (three classes had shared that name).
- catalog `dto/*` → `web/graphql/dto`; `config/security/OrganizationSecurityService` → `security`;
  empty `event/` removed.
- identity `dto/sync/*` → `web/rest/dto`; `service/validation/FileUploadValidator` → `validation`;
  `infrastructure/cache/OtpService` → `service`; `domain/ApprovalSagaStep` →
  `domain/enums/OnboardingStep`; `OrganizationGroups`, `RequiredDocuments` → `domain/valueobject`.
- All 56 `package-info.java` files deleted: they were Spring Modulith boundary headers that outlived
  Modulith and had drifted from the code. `ModuleBoundaryLintTest` still enforces the boundaries
  themselves; its package-info count ratchet and ET-PLT-001 R4's documentation clause are withdrawn.
- Stale sweep/saga/listener wording in ~30 comments rewritten to describe the Temporal flow.

## 5. Defects found and fixed — each with a test

| # | Defect | Effect | Fix and test |
|---|---|---|---|
| 1 | Catalog tier mutations returned a `success/message/data` wrapper; the schema declares `TicketTier!` | every `TicketTier` field resolved null; every refusal swallowed into a 200 | return the tier, let refusals propagate — `TicketTierMutationShapeTest` (L1), `TicketTierSchemaParityTest` (L4) |
| 2 | `@PreAuthorize` on `refundRequestByRequestId` called a method `RefundSecurityService` did not have | a customer was refused their own refund | method added — `RefundOwnershipTest`; **new lint `PreAuthorizeTargetLintTest`** checks every SpEL bean call in every service |
| 3 | Booking's calls to catalog/identity `/api/internal/**` carried no service token (clients used a plain `WebClient.Builder`; the OAuth2 clients were never injected) | 401 on every internal call outside tests — tenancy lookups included | `InternalServiceWebClients` — `InternalServiceWebClientsTest` (WireMock token exchange) |
| 4 | S3 document storage switched on `aws.s3.enabled`, which the config never set; with S3 on, the local store stayed on too | S3 unreachable by configuration; two stores | one key, `file-storage.type` — `FileStorageSelectionTest` |
| 5 | Payment intent timeout read `payment.timeout.minutes`, which nothing set | operator's timeout ignored | `booking.payment.timeout` — `PaymentTimeoutConfigTest` |
| 6 | Consumer retry budget declared in yml, never bound; `backoff-multiplier` named differently from the field | stated retry policy ignored | bound — `RetryBudgetBindingTest` |
| 7 | Services component-scanned `com.pml.shared`, pulling auto-configurations in as ordinary configuration | every auto-configuration ordering in shared-library ignored; found at boot as a missing `TemporalGateway` bean | `@SpringBootApplication(scanBasePackages = …)`, which keeps Boot's exclude filters |
| 8 | Collection validators applied on `ApplicationReadyEvent`, fire-and-forget; migrations also after runners | seeds and adoption ran before migrations and validators; a new enum value in a seed stopped the service from starting | `MigrationRunner` (first) and `MongoSchemaValidationConfig` (second) are ordered, blocking runners |
| 9 | Validator `enum` lists behind the Java enums — chart of accounts `subType` (5 vs 27, 2 not in code), refund `status` (no `CANCELLED`) and `requestType`, chargeback `reason`/`primaryFundSource`, payout `CHEQUE`, reconciliation `ESCROW_JOURNAL`, event `CHANGES_REQUESTED`/`REJECTED`, reference-data `parentType`, verification-document `EXPIRED` | "Document failed validation" on writes the code makes: cancelling a refund, requesting changes to or rejecting an event, seeding account 5050 | validators accept every model constant (union with existing values); **enum parity check added to all three `*ModelValidatorParityTest`** |
| 10 | Identity permitted `/actuator/**` and `/api/debug/**`, `/api/auth/**` (no controller) | environment and auth details public | probes only; debug controller deleted — verified at boot (`/actuator/env` → 401) |
| 12 | `KeycloakService.verify2FACode` returned `true` for any code | an ownership transfer's second factor checked nothing | `OwnershipConfirmationCodes`: a code sent to the nominee's verified phone via `requestOwnershipTransferCode`, single-use, scoped to the transfer, 3 attempts, 60 s cooldown — `OwnershipConfirmationCodesTest` (Redis on Testcontainers) |
| 13 | identity's field-encryption key fell back to a literal committed to git | any environment missing the variable encrypted payout details with a public key | no fallback; only the local profile carries a key — `EncryptionKeyConfigTest`. **Rotate the key in any environment that ever ran without `APP_SECURITY_ENCRYPTION_KEY`, and re-encrypt what it wrote.** |
| 11 | `platform.security.public-paths` read with `@Value`, which cannot read a YAML list (introduced and caught in this audit) | PawaPay webhooks would have been refused | bound with `Binder` — a YAML-shaped test case added |
| 14 | Five admin paging types (`ChargebackOffsetPage`, `ChartOfAccountsOffsetPage`, `JournalEntryOffsetPage`, `EscrowTransactionOffsetPage`, `ReconciliationRunOffsetPage`) named their component `paginationInfo`; the schema field is `pagination: PaginationInfo!` | every admin list query selecting `pagination` failed with a non-null error | components renamed — `GraphQlDtoSchemaParityTest` |
| 15 | `ReconciliationSummary` had none of the schema's `resolvedVariance!`, `unresolvedVariance!`, `lastCompletedDate`, `oldestPendingDate`, and seven fields the schema never exposed, five of them hard-coded zeros | the admin reconciliation summary failed whenever a variance was selected | the DTO is the schema type field for field, filled from the service summary — `GraphQlDtoSchemaParityTest` |
| 16 | `updateMemberRole` read `input.role`; the schema and the organizer app send `newRole` | every role change arrived with a null role | component renamed — `GraphQlDtoSchemaParityTest` |
| 17 | `EventDiscoveryFilterInput.isFree`; the schema field is `isFreeEvent` | the free-events filter could never be set (the filter is not applied yet either — §6) | renamed — `GraphQlDtoSchemaParityTest` |
| 18 | Catalog and booking accepted revoked tokens until they expired | a signed-out or disabled user kept access for the token's lifetime | per-request check after authentication (shared cache, then identity); money-moving and platform-configuration mutations fail closed when the check cannot answer — `RemoteRevocationEnforcementTest` (WireMock + Redis), `SensitiveMutationsTest` in both services |

## 6. Open — reported, not fixed

**Found by the new parity test — fixed the same day (F-039):** event creation and editing now store
every field (venue resolved through the reference data, tiers written with the event, the
material-change rule enforced); booking can price tickets for app-created events; categories come
from the reference data; notification settings save, essential messages stay on and reminders honour
the recipient; discovery applies its five filters on real indexes; the admin export applies every
filter field and neutralises spreadsheet formulas; ticket search queries rather than scans. Two more
production defects surfaced while testing against the real validators and were fixed: every event
creation and every notification write would have been refused by MongoDB.

**Still on the parity test's known list:** category presentation fields (`color`, `iconUrl`,
`sortOrder`) and the `catalog_categories` / `catalog_cities` / `catalog_provinces` collections that
duplicate the reference data; device metadata; four `SendNotificationInput` options;
`ReportExport.errorMessage`; `AuthPayload.tokenType`; the deprecated `tags`.

**Data-model candidates, not deleted** (`inventory-after/fields.tsv`): 26 `@Document` fields nothing
writes (e.g. `Ticket.transferredToId`/`transferredAt`/`paymentUrl`, `PaymentAttempt.deviceFingerprint`,
`RefundRequest.supportingDocuments`) and 38 written but never read (history entries and audit fields,
mostly by design). Dropping a persisted field needs a migration and a look at the data first.

**Workflow payload fields no Java code reads** (e.g. onboarding `View.sagaStep`, refund
`Evidence.providerRefundId`): query results are read by operators through Temporal; the rest can go
only with a versioned change, because recorded histories still carry them.

- **Catalog export links point at `/api/exports`,** which no controller serves.
- **ET-FIN-004 R1 is not implemented:** refunds are a flat 100% minus a fee; the per-event
  `RefundPolicy` schedule does not exist.
- **`BoundedRetry`** (ET-PLT-002 BE-7) has no production caller.
- Identity's own member, role and grant mutations are not yet marked `@FailClosedOnRevocation`.
- The spec names `booking.payment.max-pending` as configuration; the implementation is a workflow
  constant (`PurchaseRules.MAX_PENDING`), which is the right design — the spec should say so.
- The dev database holds duplicate usernames, so identity's unique `idx_username` cannot build there.
- **Test gaps are the largest remaining risk:** only 14% (booking), 18% (identity), 20% (catalog) and
  42% (shared) of *reachable* methods execute under the suites. `GAP` rows in
  `inventory-after/inventory-methods.csv` list them per method.
- Kept but test-only: catalog early-bird pricing (`isEarlyBirdActive`, `getDiscountPercentage`) has
  tests and no production caller.

## 7. Single-responsibility review — recommended splits, not performed

| Class | Size | Responsibilities it mixes | Suggested split |
|---|---:|---|---|
| `booking … AccountingServiceImpl` | 1,434 lines | posting 12 kinds of journal entry *and* balance queries | `LedgerPostings` (the `record*` methods, one per business event) · `LedgerBalances` (`get*Balance*`) |
| `booking … ReconciliationServiceImpl` | 1,085 | four reconciliation kinds, run lifecycle, reporting, alerting | one reconciler per kind behind the recon workflow's activities · `ReconciliationRuns` (lifecycle + queries) · `ReconciliationReports` |
| `booking … OrganizerDashboardServiceImpl` | 959 | ten independent read models | a query object per panel (finance overview, activity, revenue series, payout window…) |
| `identity … KeycloakService` | 735 | user admin, realm roles, org group tree, sessions, e-mail actions, and the 2FA stub | `KeycloakUsers` · `KeycloakRoles` · `KeycloakOrganizationGroups` · `KeycloakSessions`; 2FA out entirely (F-038) |
| `booking … RefundServiceImpl` | 775 | refund calculation, request lifecycle, provider calls | `RefundCalculator` (pure, L1-testable) · lifecycle stays in the refund workflow's activities |
| `identity … UserMutationResolver` | 748 | ten mutations with inline business rules | move the rules into `UserService`; the resolver maps and authorizes |
| `booking … PaymentAttempt` / `ChargebackRecord` (documents) | 700 / 637 | persistence shape plus behaviour | keep the documents as data; rules to `*Rules` classes like the workflows already use |

## 8. OWASP Top 10 check of the changes

| | Effect of this change set |
|---|---|
| A01 Broken access control | fixed: the refund owner check (and a lint for every SpEL target); identity's public actuator and debug endpoints closed; public paths are now an explicit per-service list; the internal clients authenticate; 23 internal endpoints without a caller removed, among them user lookup by e-mail and ticket validation |
| A02 Cryptographic failures | fixed: the encryption key has no default outside the local profile |
| A04 Insecure design | the startup order now guarantees validators before writes; refusals reach callers as coded errors instead of 200s |
| A05 Security misconfiguration | `security.debug.enabled` defaults off; bean overriding disabled; auto-configurations load only as auto-configurations |
| A07 Identification and authentication failures | fixed: ownership transfer requires a one-time code to the nominee's verified phone; revoked tokens are refused in every service, not only identity |
| A08 Software and data integrity | validators and models agree on every enum, checked in all three services |
| A10 SSRF | internal clients take base URLs from configuration only; no request-derived URLs were added |

A03, A06 and A09 are unaffected.

## 9. Verification

- **Suites:** shared 458, catalog 197, booking 310 (6 skipped — parity assumptions), identity 360
  (8 skipped — parity assumptions), gateway 3. All green. `ChartOfAccountsValidatorTest`
  (Testcontainers) applies booking's real validators and writes every account sub-type. Lint ratchets lowered where the
  cleanup gained ground: tenant-unscoped lookups (identity 21→20, booking 55→50, catalog 21→19), 
  shared fire-and-forget budget 1→0.
- **Boot:** each service's jar started against MongoDB and Temporal (`docker-resources`), `local`
  profile: booking 6.0 s, catalog 3.9 s, identity 6.6 s. Smoke checks: health 200; `/graphql` and
  `/api/internal/**` without a token 401; booking's webhook path reaches its controller; identity
  `/actuator/env` 401. The Service Bus emulator could not start (port 5672 is held by `dev_rabbitmq`),
  so broker delivery was not exercised.
- **Repeatable:** `backend/tools/reachability/README.md`.
