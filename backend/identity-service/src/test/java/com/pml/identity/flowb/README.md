# Flow B — Organization User Registration: Integration Test Plan

Flow B is the **form-registration + role-minting** path described in the security
architecture report: a human fills the Keycloak registration form, selects an account
type, and the platform must end up with a *fully provisioned* organization user across
four independent stores.

This directory contains the executable specification for that flow.

---

## 1. What Flow B actually is (traced from code, not docs)

```
 HUMAN                    KEYCLOAK (container)                 IDENTITY-SERVICE            STORES
 ─────                    ────────────────────                 ────────────────            ──────
   │  GET /auth
   │────────────────────▶ myticketzm realm
   │                      login page (theme: myticketzm)
   │  GET registration
   │────────────────────▶ register.ftl
   │                      ├ firstName / lastName / username / email
   │                      ├ password + password-confirm
   │                      └ user.attributes.accountType[]  ◀── the role decision
   │  POST form
   │────────────────────▶ MyTicket Registration Form flow
   │                       ├ 20 registration-user-creation   → UserModel created
   │                       ├ 50 registration-password-action → credential set
   │                       └ 60 account-type-role-mapper     → AccountTypeRoleMapper
   │                            ├ validate(): allowlist {CUSTOMER, ORGANIZER}
   │                            └ success(): grant realm roles,
   │                                         always add CUSTOMER,
   │                                         write accountType + roles attributes
   │                      EventType.REGISTER
   │                       └ UserSyncEventListener (SPI, "user-sync")
   │                            └ buildUserData(UserModel) ──────▶ POST /api/internal/
   │                               (Bearer: client_credentials     keycloak/sync/user-data
   │                                myticketzm-otp-authenticator,          │
   │                                scope internal-write)                  ▼
   │                                                        KeycloakSyncController
   │                                                          @PreAuthorize SCOPE_internal-write
   │                                                                       │
   │                                                        UserSyncServiceImpl.syncUserFromData
   │                                                          ├ createUserFromData → users doc
   │                                                          │    (_id == Keycloak sub)
   │                                                          ├ extractRolesFromData
   │                                                          │    (always CUSTOMER + accountTypes)
   │                                                          └ publishRegistrationIfNeeded
   │                                                               findAndModify CAS on
   │                                                               registrationEventPublished
   │                                                                       │
   │  302 → redirect_uri?code=…                                            ├──▶ MongoDB users
   │◀──────────────────────                                                └──▶ identity-events
   │                                                                             UserRegisteredEvent
```

Four components must all agree. **Nothing on this path is mocked in these tests.**

---

## 2. Test topology

Every box below is a real process. There is exactly one substitution, called out explicitly.

| Component | How it runs | Why real |
|---|---|---|
| Keycloak 26.5.2 | Testcontainer, **production realm JSON**, **production themes**, **production `keycloak-extensions.jar`** | The FormAction, the SPI listener and the `register.ftl` field names are the thing under test |
| MongoDB 8 | Testcontainer (replica set) | JSON-schema validators are part of the contract (`users-schema.json`) |
| PostgreSQL 16 | Testcontainer | Spring Modulith event publication registry — the app will not boot without it |
| Redis 7 | Testcontainer | Health/readiness group includes `redis`; OTP store |
| identity-service | real `@SpringBootTest(RANDOM_PORT)`, real `SecurityConfig`, real JWT validation against the container's JWKS | Authorization on `/api/internal/**` is a Flow B assertion, not scenery |
| Keycloak → identity-service hop | routed through an in-test `FaultInjectingProxy` | Lets us cut exactly one wire and observe what the system does |
| Azure Service Bus | **substituted** with `spring-cloud-stream` test binder | The ASB emulator needs `azure-sql-edge`, which has no arm64 image. `StreamBridge`, the binding name `userOutput-out-0`, the destination `identity-events` and the serialized payload are all still exercised — only the wire protocol is swapped |

### "If one component fails, all fails"

`FlowBIntegrationSupport.assertAllComponentsLive()` runs before **every** test and
pings all six hops (Mongo, Postgres, Redis, Keycloak, the app, the proxy target).
A dead component aborts the test rather than letting an assertion pass for the wrong
reason. There is no `@MockBean` anywhere in this package.

---

## 3. Test matrix

### `FlowBRegistrationHappyPathIT` — the flow works end to end

| # | Assertion |
|---|---|
| H1 | Registering with `accountType=ORGANIZER` returns a 302 to the redirect URI with an auth `code` |
| H2 | Keycloak grants realm roles **{ORGANIZER, CUSTOMER}** — CUSTOMER auto-added as base role |
| H3 | Keycloak stores `accountType` **and** the mirrored `roles` attribute, both containing CUSTOMER |
| H4 | MongoDB `users` document exists with `_id == Keycloak sub` |
| H5 | Mongo roles are exactly `{CUSTOMER, ORGANIZER}` — no drift from Keycloak |
| H6 | Defaults are minted: `active=true`, `accountStatus=ACTIVE`, `locked=false`, `twoFactorEnabled=false`, `phoneVerified=false`, `createdAt`/`updatedAt` set |
| H7 | `registrationEventPublished=true` (idempotency guard armed) |
| H8 | Exactly one `UserRegisteredEvent` on `identity-events` carrying both roles |
| H9 | Progressive onboarding respected: **no** `Organization` and **no** `OrganizationMember` created at registration |
| H10 | The document survives the real `users-schema.json` validator (proves the write was not silently relaxed) |
| H11 | The issued access token carries `realm_access.roles` ⊇ {CUSTOMER, ORGANIZER} — the roles are usable, not just stored |

### `FlowBRoleMintingEdgeCaseIT` — every branch of `AccountTypeRoleMapper`

| # | Input | Expected |
|---|---|---|
| E1 | `accountType=CUSTOMER` only | Mongo+KC roles = {CUSTOMER}; **no** ORGANIZER anywhere |
| E2 | `accountType=[CUSTOMER, ORGANIZER]` | both roles, `accountType` attribute not duplicated |
| E3 | `accountType=ORGANIZER` only | CUSTOMER added by the base-role rule (both stores) |
| E4 | no `accountType` field | registration rejected; **no** Keycloak user, **no** Mongo doc, **no** event |
| E5 | `accountType=ADMIN` | rejected; no ADMIN role anywhere (privilege escalation) |
| E6 | `accountType=[ORGANIZER, SUPER_ADMIN]` | whole submission rejected — partial acceptance is a failure |
| E7 | `accountType=organizer` (lower case) | rejected — the allowlist is case-sensitive by construction |
| E8 | duplicate email | rejected; the first user's Mongo doc is untouched |
| E9 | duplicate username | rejected; no second doc |
| E10 | replay of the same sync payload 5× | still exactly **one** `UserRegisteredEvent`, roles unchanged |
| E11 | `UPDATE_PROFILE` sync after registration | roles preserved, no second event |
| E12 | organizer registers with a phone number | stored E.164-normalised with `phoneCountry`, or `null` — never malformed |

### `FlowBOwaspComplianceIT` — mapped to OWASP Top 10 2021

| OWASP | Test |
|---|---|
| A01 Broken Access Control | unauthenticated `POST /api/internal/keycloak/sync/user-data` → 401 |
| A01 | token **without** `internal-write` scope → 403 |
| A01 | token **with** `internal-write` (real client_credentials from the container) → 200 |
| A01 | `POST /sync/all` with a non-admin token → 403 |
| A01 | role elevation via the registration form (`ADMIN`, `SUPER_ADMIN`, `FINANCE`, `SCANNER`) → all rejected |
| A03 Injection | XSS payload in `firstName` → rejected by `person-name-prohibited-characters`, nothing persisted |
| A03 | NoSQL operator payload (`{"$ne":null}`) in username → rejected by username validation / users-schema pattern |
| A03 | CRLF in email → rejected |
| A04 Insecure Design | 6-role `enum` in `users-schema.json` is enforced by the database, not only by code |
| A05 Misconfiguration | realm keeps `duplicateEmailsAllowed=false`, `bruteForceProtected=true`, `registrationAllowed=true` |
| A05 | the shipped realm registers the `user-sync` event listener — without it Flow B stops at Keycloak |
| A07 Auth Failures | 6 failed logins lock the account, and an empty password is refused |
| A08 Data Integrity | the `users` JSON-schema validator rejects a hand-crafted doc with role `ROOT`, and one with no roles |
| A09 Logging | a `REGISTER` event is recorded in Keycloak with the user id and source IP |

> **A07, recorded decision.** The realm ships with an empty `passwordPolicy`. That is a
> deliberate product choice and the suite does not assert a strength policy into existence.
> It asserts the controls actually relied on instead: empty passwords are refused, and
> brute-force lockout is in force. The exposure — a one-character password is accepted at
> registration — is stated in the test so it stays visible rather than forgotten.

### `FlowBFailureRecoveryIT` — "if a step fails, the system resumes from where it stopped"

| # | Scenario |
|---|---|
| R1 | Sync hop down (`FAIL_503`) during registration → Keycloak user **is** created (registration must not break), Mongo has **no** doc → drift is real and detectable via `GET /sync/user/{id}` → 404 |
| R2 | Restore the hop, run the reconciliation path (`UserSyncService.syncUserFromKeycloak`) → final state is **byte-for-byte the happy-path state**: same roles, same defaults, `registrationEventPublished=true` |
| R3 | The deferred `UserRegisteredEvent` is published exactly once by the recovery pass — not zero, not twice |
| R4 | Sync hop hangs (`HANG`) → the Keycloak client times out, registration still succeeds, recovery still converges |
| R5 | Mongo write fails mid-sync (validator rejection) → endpoint returns 500, no half-written document, recovery after the cause is removed converges |
| R6 | Bulk recovery `syncAllUsersFromKeycloak()` repairs N drifted users and emits N events, one each |
| R7 | Recovery is idempotent: running it twice changes nothing and emits nothing the second time |

---

## 4. Running

```bash
# 1. the extensions JAR is a real input to the test
(cd backend/keycloak-extensions && mvn -q clean package)

# 2. run Flow B  (45 tests, ~4 min, 4 containers)
(cd backend/identity-service && mvn verify -Dit.test='FlowB*IT' \
    -Dtest=NoSuchUnitTest -Dsurefire.failIfNoSpecifiedTests=false)
```

The suite needs `docker-resources/` checked out as a sibling of `ticketing-system/`
(it owns the realm JSON and the login theme). Override with
`-Dflowb.docker-resources=/path/to/docker-resources`.

`*IT` classes run under failsafe (`verify`), so `mvn test` stays fast and Docker-free.

---

## 5. What the first full run found

Everything below was surfaced by running this suite, not by reading the code.

| # | Finding | Status |
|---|---|---|
| 1 | **`UserRegisteredEvent` was never published.** The binding declares `producer.partition-key-expression: headers['partitionKey']` but `StreamBridge.send` was called with a bare payload, so Spring Cloud Stream threw *"Partition key cannot be null"* before any binder saw the message. Every registration lost its cross-service event. | fixed — `UserSyncServiceImpl` now sends a `Message` keyed by user id |
| 2 | **The loss was unrecoverable.** `registrationEventPublished` was set *before* publishing, so the exactly-once guard turned a failed send into "never send": no later sync or backfill could ever emit it. | fixed — the claim is released when the send fails |
| 3 | **Reconciliation skipped users whose document already existed.** `syncKeycloakUserToMongo`'s update branch never called `publishRegistrationIfNeeded`, so a user created by Better Auth, or one whose event failed, could not be repaired by a re-sync. | fixed — the update branch now reconciles too (CAS keeps it exactly-once) |
| 4 | **One bad user aborted the whole backfill.** `syncAllUsersFromKeycloak` used `doOnError`, which logs but does not resume, so the first user rejected by the `users` validator killed the run for everyone behind it. | fixed — per-user `onErrorResume` |
| 5 | **The shipped realm did not register the `user-sync` listener** (`eventsListeners: ["jboss-logging"]`), so a fresh deployment would register users in Keycloak that never reached MongoDB. | fixed in `docker-resources/keycloak/myticketzm-realm.json` |
| 6 | `AccountTypeRoleMapper`'s "always add CUSTOMER" branch never grants the role directly, because `user.hasRole` already sees CUSTOMER through the `default-roles-*` composite. Behaviour is correct — authorization uses effective roles — but assertions must read effective roles, not direct mappings. | no change; documented in the tests |
| 7 | A Keycloak user without `firstName`/`lastName` cannot be synced at all: `createUserFromKeycloak` writes `""`, which the `users` schema rejects (`minLength: 1`). Admin-created users can be in this state permanently. | not changed — R8 asserts it fails loudly rather than corrupting the store |
| 8 | The identity-service config uses client id `identity-service`, but the realm provisions `myticketzm-identity-service`. Any `client_credentials` call from the service would fail. | not changed — outside Flow B; the suite overrides it to the realm's value |
