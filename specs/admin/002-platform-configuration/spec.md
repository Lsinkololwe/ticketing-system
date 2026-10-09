# ET-ADM-002 · Platform configuration, feature flags and versioned settings

> **Conformance** · V3 §2.2 commission structure · V3 §14.1 refund fee policy

> **Amendment, 2026-09-19 — one settings table, owned by catalog; refund policies are platform
> configuration.** Decided by the product owner:
>
> - **The platform settings are one table, like the reference data.** The database is shared;
>   the services differ, the settings do not. Everything this spec stores — settings,
>   organization overrides and feature flags — lives in **`catalog_platform_configuration`**,
>   written only by catalog-service and read directly, read-only, by every other service through
>   shared-library's `PlatformConfigurationReader` (the arrangement `StatusSemanticResolver`
>   already uses for `catalog_reference_data`). The two identity collections this spec had
>   reserved are withdrawn; ET-PLT-002 §4 carries the one row. The spec moves to
>   catalog-service and the `catalog` subgraph, which already serves `platformConfiguration`.
> - **Refund policies are platform configuration.** The platform defines the refund policies and
>   their schedules (§4 *Refund policies*); an organizer picks one of the active policies for
>   each event. [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) R1 reads them from here.

## 1. Capability

Scattered through this corpus are roughly sixty numbers: commission rates, hold periods,
reservation TTLs, refund minimums, payout thresholds, SLA windows, rate caps. Every one is
declared as configuration, and configuration in a Spring application means a YAML file and
a deploy. For most of them that is correct — a reservation TTL should not be changeable by
somebody in a hurry at nine on a Friday.

But a handful genuinely need to move without a deploy: the commission rate for a specific
organization negotiated in a meeting, a feature turned off during an incident, the payout
minimum adjusted for a market. This spec declares which those are, holds them in a
versioned store with an audit trail, and — importantly — declares that **everything else
stays in YAML**. A platform where any number can be changed at runtime by anyone is a
platform whose behaviour cannot be reproduced.

It declares three kinds of runtime configuration: **platform settings** (one value,
platform-wide), **organization overrides** (a value for one tenant, such as a negotiated
commission rate), and **feature flags** (a boolean with an audience). Each is versioned,
each change is audited, and each has a declared blast radius so that the person changing it
knows what they are changing.

## 2. Design decisions

**A closed registry of runtime-configurable keys; everything else is YAML.** The registry
in §4 is the complete list. A key not in it cannot be set at runtime, and adding one is a
change to this spec — which is the review step that stops the registry growing into "all
configuration, mutable, by anyone".

**Every value is typed, validated and bounded.** A commission rate is a decimal between 0
and 0.5. A hold period is a duration between 0 and 90 days. The bounds are in the registry
and are enforced on write, because the failure mode of runtime configuration is somebody
typing 50 into a field that means a percentage.

**Configuration is versioned and never overwritten.** Setting a value appends a new version;
the current value is the latest. That makes *what was the commission rate on 3 March*
answerable, which matters because a ticket sold that day was priced by it.

**Reads are cached with a short TTL and invalidated on write.** A configuration read on the
purchase path cannot be a database round trip. Thirty seconds of staleness is acceptable for
every key in the registry, and a write evicts immediately so the normal case is instant.

**An organization override replaces the platform value; it does not merge.** For the two
keys that support it — commission rate and payout schedule — the override is the value.
Merging semantics for a single scalar is an invitation to a bug nobody can reproduce.

**Feature flags carry an audience and default to off.** A flag is off, on, or on for a
named set of organizations. Percentage rollouts are not built: with a few thousand
organizations, a named set is more useful and entirely reproducible, and a percentage
rollout whose bucketing nobody can explain is a support case.

**A kill switch is a flag, and the flags that are kill switches are marked.** Turning off
resale, ticket transfer or new registrations during an incident is a flag flip. Those are
marked in the registry so an operator can find them under pressure rather than reading
sixty rows.

**Changing a value never changes what has already happened.** A commission rate change
applies to tickets sold afterwards; existing tickets carry their snapshot
([ET-FIN-002](../../finance/002-commission/) R2). A hold-period change applies to escrow
accounts opened afterwards. This is asserted rather than assumed, because the natural
implementation of a "current value" lookup gets it wrong.

**Rejected alternatives**

- *All configuration mutable at runtime.* The platform's behaviour becomes unreproducible.
- *Overwriting a value in place.* *What was the rate in March* becomes unanswerable, and a ticket sold then was priced by it.
- *Percentage-based feature rollouts.* Bucketing nobody can explain, for a population where a named set is more useful.
- *Merging an organization override with the platform value.* Merge semantics for a scalar.
- *Untyped string values.* Somebody types `50` into a rate that means a fraction.
- *Reading configuration from the database on every call.* A round trip on the purchase path.
- *Applying a rate change retroactively.* Re-prices tickets people have already bought.

## 3. Requirements

### ET-ADM-002-R1 · The runtime-configurable registry is closed and typed

THE SYSTEM SHALL permit runtime configuration only of the §4 keys, each with its declared
type and bounds.

**Acceptance**
- [ ] `setPlatformConfiguration` refuses a key absent from the registry with `CONFIGURATION_KEY_UNKNOWN`
- [ ] Every value is validated against its declared type and bounds; a violation refuses with `CONFIGURATION_VALUE_INVALID` carrying `constraint`
- [ ] A rate outside `[0, 0.5]`, a duration outside `[PT0S, P90D]` and a negative money amount are each refused
- [ ] Keys not in the registry remain in YAML and are unreachable at runtime
- [ ] Adding a key changes this spec's §4 in the same commit
- [ ] The registry is seeded from a migration with the platform defaults, idempotently

### ET-ADM-002-R2 · Every change appends a version and is audited

WHEN a configuration value changes, THE SYSTEM SHALL append a new version and SHALL record
who changed it and why.

**Acceptance**
- [ ] Setting a value inserts a new `catalog_platform_configuration` row; no row is updated or deleted
- [ ] The current value is the highest `version` for that key and scope
- [ ] Every change requires a reason of at least 20 characters
- [ ] Every change writes an audit row with the old value, the new value, the actor and the reason ([ET-PLT-009](../../_platform/009-audit-trail/))
- [ ] `configurationHistory(key, scope)` returns every version with its actor, reason and effective time
- [ ] A test asserts the value as at a past instant is recoverable

### ET-ADM-002-R3 · Reads are cached, invalidated on write, and never on the hot path uncached

THE SYSTEM SHALL serve configuration from a cache with a short TTL and SHALL evict on
change.

**Acceptance**
- [ ] Values are cached at `cache:config:{scope}:{key}` for `admin.config.cache-ttl` (PT30S)
- [ ] A write evicts the key immediately across every instance
- [ ] A cache miss falls through to MongoDB and repopulates; cold and warm answers are identical
- [ ] No configuration read on the reservation, payment or validation path issues a database query in the warm case
- [ ] A load test asserts configuration reads add no measurable latency during a simulated on-sale
- [ ] Nothing polls for configuration changes

### ET-ADM-002-R4 · An organization override replaces the platform value

WHERE an organization override exists for a supported key, THE SYSTEM SHALL use it in place
of the platform value.

**Acceptance**
- [ ] Exactly two keys support an override: `finance.commission.rate` and `finance.payout.schedule`
- [ ] An override replaces the platform value entirely; there is no merge
- [ ] Resolution is: organization override → platform value → YAML default, in that order
- [ ] An override outside the key's bounds is refused
- [ ] Setting an override requires `SUPER_ADMIN` and a reason
- [ ] An override is versioned and audited exactly as a platform value
- [ ] [ET-FIN-002](../../finance/002-commission/)'s resolver consults the override, and this spec's §4 states the interaction with the tier card explicitly

### ET-ADM-002-R5 · Feature flags default off and carry an audience

THE SYSTEM SHALL evaluate each flag against its audience and SHALL default to off.

**Acceptance**
- [ ] `FeatureFlagState` is `OFF`, `ON`, `ORGANIZATIONS` — with `ORGANIZATIONS` carrying an explicit id list
- [ ] A flag with no row evaluates `OFF`
- [ ] No percentage or hash-based rollout exists
- [ ] Flag evaluation is a pure method over the flag and the requesting context, testable at layer 1
- [ ] Flags marked `killSwitch` in §4 are listed separately in the admin surface
- [ ] Every flag change is versioned and audited like any other value
- [ ] A test asserts an unknown flag evaluates off rather than throwing

### ET-ADM-002-R6 · A change never re-prices or re-times what already happened

THE SYSTEM SHALL apply every configuration change prospectively only.

**Acceptance**
- [ ] A commission-rate change does not alter any existing ticket's `commissionRate` or `commissionAmount`
- [ ] A hold-period change does not alter any existing escrow's `holdUntil`
- [ ] A reservation-TTL change does not alter any existing reservation's `expiresAt`
- [ ] A refund-policy configuration change does not alter an event's chosen policy
- [ ] Each of these is asserted by its own test that changes the value and re-reads the prior entity
- [ ] Where a change *should* apply to in-flight entities, the registry marks the key `retroactive` and names the migration that applies it — no key is marked so today

### ET-ADM-002-R7 · The configuration surface is `SUPER_ADMIN` and fully visible

THE SYSTEM SHALL restrict configuration mutation to `SUPER_ADMIN` and SHALL expose the
current state.

**Acceptance**
- [ ] Every mutation requires `SUPER_ADMIN` and `platform:configure` ([ET-ORG-003](../../organization/003-permission-resolution/) §4)
- [ ] Reads require `ADMIN` and are `@tag(name: "admin")`
- [ ] `platformConfiguration` returns every registry key with its current value, source (override, platform, YAML default) and bounds
- [ ] A value's source is always visible, so an operator can see whether they are looking at an override
- [ ] The kill switches are queryable as their own list
- [ ] A test asserts an `ADMIN` can read and cannot write

## 4. Model

### Runtime-configurable registry — closed

| Key | Type | Bounds | Default | Override | Kill switch |
|---|---|---|---|---|---|
| `finance.commission.rate.standard` | decimal | `[0, 0.5]` | `0.05` | — | — |
| `finance.commission.rate.charity` | decimal | `[0, 0.5]` | `0.02` | — | — |
| `finance.commission.rate.high-volume` | decimal | `[0, 0.5]` | `0.03` | — | — |
| `finance.commission.rate.premium-organizer` | decimal | `[0, 0.5]` | `0.04` | — | — |
| `finance.commission.rate` | decimal | `[0, 0.5]` | — | **organization** | — |
| `finance.commission.high-volume-threshold` | int | `[1, 100000]` | `1000` | — | — |
| `finance.commission.premium-threshold` | int | `[1, 1000]` | `10` | — | — |
| `finance.escrow.hold-period` | duration | `[PT0S, P90D]` | `P7D` | — | — |
| `finance.payout.minimum` | money | `[0, 100000]` | `K100` | — | — |
| `finance.payout.schedule` | enum | `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `ON_REQUEST` | `ON_REQUEST` | **organization** | — |
| `finance.refund.minimum` | money | `[0, 10000]` | `K10` | — | — |
| `finance.refund.review-escalations` | durations | each `[PT1H, P30D]` | `P2D, P5D` | — | — |
| `finance.refund.fee-bearer` | enum | `CUSTOMER`, `PLATFORM`, `ORGANIZER` | `CUSTOMER` | — | — |
| `booking.reservation.ttl` | duration | `[PT2M, PT60M]` | `PT10M` | — | — |
| `catalog.transfer.cutoff` | duration | `[PT0S, P7D]` | `PT2H` | — | — |
| `admin.sla.organizer` | duration | `[PT1H, P14D]` | `PT48H` | — | — |
| `admin.sla.event` | duration | `[PT1H, P14D]` | `PT24H` | — | — |
| `notification.per-user-hourly-cap` | int | `[1, 100]` | `10` | — | — |

Eighteen keys. Every other number in this corpus stays in YAML.

### Refund policies — defined by the platform, picked by the organizer

A refund policy is a platform setting: a code, a name the buyer reads, and a schedule of
refundable percentage against hours before the event starts. Administrators define and
change them here; an organizer picks one **active** policy for each event, and an event
cannot be published without one. The platform starts with the four policies of
[ET-FIN-004 §4](../../finance/004-refunds-and-chargebacks/):

| Code | > 168 h | 168–48 h | 48–24 h | < 24 h |
|---|---|---|---|---|
| `FLEXIBLE` | 100% | 100% | 50% | 0% |
| `MODERATE` | 100% | 50% | 0% | 0% |
| `STRICT` | 50% | 0% | 0% | 0% |
| `NO_REFUNDS` | 0% | 0% | 0% | 0% |

- Each policy is a row of `catalog_platform_configuration` under key `refund.policy.{CODE}`,
  value type `REFUND_SCHEDULE`, versioned and audited like every other key (R2).
- A schedule's steps are bounded: hours in `[0, 8760]`, percentages in `[0, 100]`, and the
  percentage never rises as the event approaches.
- A policy is retired, never deleted, so an event that chose it keeps resolving.
- **An event records the policy code and the version it was published under** (R6). A later
  change to the policy applies to events published afterwards; the buyer is always refunded
  under the schedule they were shown before paying.
- Cancellation and the reschedule window still override every policy to 100%
  (ET-FIN-004 R6, R7).

### Feature flags — closed

| Flag | Default | Kill switch | Effect when off |
|---|---|---|---|
| `feature.resale` | `OFF` | ✅ | `listForResale` refuses platform-wide |
| `feature.ticket-transfer` | `ON` | ✅ | `initiateTransfer` refuses platform-wide |
| `feature.new-registrations` | `ON` | ✅ | new account creation refuses |
| `feature.organizer-applications` | `ON` | ✅ | `applyToBeOrganizer` refuses |
| `feature.payouts` | `ON` | ✅ | `requestPayout` refuses |
| `feature.promo-codes` | `ON` | — | promo codes are not applied |
| `feature.event-reminders` | `ON` | — | reminder rows are not created |
| `feature.organizer-digest` | `ON` | — | the daily digest is not sent |
| `feature.bulk-event-approval` | `OFF` | — | `bulkApproveEvents` refuses |

Nine flags. Five are kill switches, listed separately in the admin surface so an operator
finds them under pressure.

### Documents

`catalog_platform_configuration` — append-only, versioned. Settings, organization overrides,
refund policies and feature flags are all rows of this one table, told apart by `kind`
(`SETTING`, `REFUND_POLICY`, `FLAG`).

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `kind` | `ConfigKind` | `SETTING`, `REFUND_POLICY` or `FLAG` |
| `configKey` | `String` | a registry key |
| `scope` | `ConfigScope` | `PLATFORM` or `ORGANIZATION` |
| `scopeId` | `String` | the organization id when scoped |
| `valueType` | `ConfigValueType` | `DECIMAL`, `INT`, `MONEY`, `DURATION`, `ENUM`, `BOOLEAN`, `ID_LIST` |
| `value` | `String` | canonical string form, parsed by type |
| `version` | `int` | monotonic per `{configKey, scope, scopeId}` |
| `previousValue` | `String` | for the audit and the diff |
| `changedById`, `reason` | `String` | reason ≥ 20 characters |
| `effectiveFrom` | `Instant` | |
| `createdAt` | `Instant` | |

`{configKey, scope, scopeId, version}` is unique. Nothing is ever updated or deleted.

A flag row carries `state` and `organizationIds` in place of `value`.

*Until this spec is built, the table holds the single pre-registry settings document
(`_id: platform-config`) that catalog seeds at startup. T2 migrates it to versioned rows.*

### Resolution order

```
organization override (the two supported keys only)
    ↓ absent
platform configuration (current version)
    ↓ absent
YAML default
```

The resolved source is returned alongside the value, so an operator always knows which
layer answered.

### The commission interaction, stated explicitly

[ET-FIN-002](../../finance/002-commission/) R1 resolves the **lowest applicable tier rate**.
An organization override for `finance.commission.rate` **replaces that result entirely** —
it is not a floor, not a ceiling and not another tier. An organization on a 3% override
pays 3% even where the charity tier would have given 2%, and even where standard would have
given 5%.

This resolves the contradiction [ET-FIN-002](../../finance/002-commission/)'s risk register
raises. An override is a negotiated commercial term and it wins.

### Caching

| Key | TTL | Evicted by |
|---|---|---|
| `cache:config:platform:{key}` | 30 s | any write to that key |
| `cache:config:org:{orgId}:{key}` | 30 s | any write to that override |
| `cache:flags` | 30 s | any flag write |

Nothing polls. A write evicts across instances via Redis.

### GraphQL

Subgraph `catalog` — catalog owns the settings table, as it owns the reference data.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `platformConfiguration` | query | `ADMIN` | `[ConfigurationEntry!]!` `@tag(name: "admin")` |
| `configurationHistory(key, scope, scopeId)` | query | `ADMIN` | `[ConfigurationVersion!]!` `@tag(name: "admin")` |
| `organizationConfiguration(organizationId)` | query | `ADMIN` | `[ConfigurationEntry!]!` `@tag(name: "admin")` |
| `featureFlags` | query | `ADMIN` | `[FeatureFlag!]!` `@tag(name: "admin")` |
| `killSwitches` | query | `ADMIN` | `[FeatureFlag!]!` `@tag(name: "admin")` |
| `setPlatformConfiguration(input)` | mutation | `SUPER_ADMIN` | `ConfigurationEntry!` `@tag(name: "admin")` |
| `setOrganizationConfiguration(input)` | mutation | `SUPER_ADMIN` | `ConfigurationEntry!` `@tag(name: "admin")` |
| `clearOrganizationConfiguration(input)` | mutation | `SUPER_ADMIN` | `Boolean!` `@tag(name: "admin")` |
| `setFeatureFlag(input)` | mutation | `SUPER_ADMIN` | `FeatureFlag!` `@tag(name: "admin")` |

`ConfigurationEntry` carries `key`, `value`, `valueType`, `source`, `bounds`, `version`,
`changedAt` and `changedBy` — everything an operator needs to understand what they are
looking at before changing it.

### Configuration

| Property | Value |
|---|---|
| `admin.config.cache-ttl` | `PT30S` |
| `admin.config.reason-min-length` | 20 |

These two are themselves YAML, not runtime-configurable — the configuration system's own
settings must not be changeable through the configuration system.

### Error codes

`CONFIGURATION_KEY_UNKNOWN`, `CONFIGURATION_VALUE_INVALID` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The registry, its types and bounds, and the seeder**
  - requirements: R1
  - files: `backend/catalog-service/.../domain/ConfigurationRegistry.java`, the seeder
  - verify: an unknown key refuses; each bound refuses its violation; seeding is idempotent
  - parallel-safe: no
  - depends: —

- [ ] **T2 · Append-only versioned storage, with the reason and the audit row**
  - requirements: R2
  - files: `backend/catalog-service/.../domain/model/PlatformConfiguration.java`
  - verify: no row is ever updated; the value as at a past instant is recoverable
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · Caching with cross-instance eviction**
  - requirements: R3
  - files: `backend/catalog-service/.../infrastructure/cache/ConfigurationCache.java`
  - verify: cold and warm answers are identical; nothing polls; no hot-path query in the warm case
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The resolution order and the organization override**
  - requirements: R4
  - files: `backend/catalog-service/.../service/impl/ConfigurationServiceImpl.java`
  - verify: an override replaces rather than merges; the source is always returned
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · The commission-override interaction in ET-FIN-002's resolver**
  - requirements: R4
  - files: `backend/booking-service/.../domain/CommissionResolver.java`
  - verify: a 3% override beats a 2% charity tier and a 5% standard, asserted both ways
  - parallel-safe: no — it amends ET-FIN-002
  - depends: T4

- [ ] **T6 · Feature flags, their audiences and the kill-switch list**
  - requirements: R5
  - files: `backend/catalog-service/.../domain/FeatureFlag.java`
  - verify: an unknown flag evaluates off; no percentage rollout exists
  - parallel-safe: yes
  - depends: T2

- [ ] **T7 · The four prospective-only tests**
  - requirements: R6
  - files: `backend/*/src/test/.../ConfigurationProspectivityTest.java`
  - verify: a rate, hold, TTL and policy change each leave prior entities untouched
  - parallel-safe: yes
  - depends: T5

- [ ] **T8 · The subgraph half; `SUPER_ADMIN` writes, `ADMIN` reads**
  - requirements: R7
  - files: `backend/catalog-service/src/main/resources/graphql/schema.graphqls`
  - verify: an `ADMIN` reads and cannot write; the public contract exposes nothing
  - parallel-safe: no — shared SDL across catalog's specs
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| The commission tier card these keys parameterise | [ET-FIN-002](../../finance/002-commission/) |
| Escrow, payout and refund behaviour the values drive | [ET-FIN-001](../../finance/001-escrow-and-ledger/), [ET-FIN-003](../../finance/003-payouts-and-settlement/), [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| The `platform:configure` permission | [ET-ORG-003](../../organization/003-permission-resolution/) |
| Audit row shape and retention | [ET-PLT-009](../../_platform/009-audit-trail/) |
| Rate-limit configuration, which lives with its enforcement | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| Deployment-time configuration and secrets | [ET-PLT-001](../../_platform/001-runtime-baseline/) |

Deliberately never in scope: **all configuration mutable at runtime** (the platform's
behaviour becomes unreproducible), **percentage-based rollouts** (bucketing nobody can
explain), and **retroactive application of a value change** (it re-prices tickets people
have already bought).

---

## 7 · Amendment, 2026-10-04 — the runtime rules organizers and buyers obey

Decided with the three frontends' rewrite (`PLATFORM_CONFIG.md`): the values an organizer or buyer must
obey are administrator-owned configuration, and both apps read them. They live in the **same
settings document** (`catalog_platform_configuration`, written only by catalog) as a `rules` section
beside `payment` and the approval fields; no second table exists. The approval SLA default is
**48 hours** (the frontends are specified against it; the earlier 72 is withdrawn), with the warning at 36
and the escalation delay at 12.

### ET-ADM-002-R8 · The rules are bounded, versioned and edited as a whole

THE SYSTEM SHALL let an administrator edit `commissionDefault` (percent, 0 to 50), `minimumPayout`,
`reservationHoldMinutes` (1 to 120), `reservationGraceMinutes` (0 to 60), `escrowHoldDays` (0 to 90),
`refundCutoffHours` (0 to 720), `maxTicketsPerBooking` (1 to 50), `rescheduleLimit` (0 to 20),
`currency` (ISO-4217) and the four refund policies (`FLEXIBLE`, `MODERATE`, `STRICT`, `NO_REFUNDS`),
and SHALL bump a `version` on every save.

**Acceptance**
- [ ] `updatePlatformConfiguration` validates every supplied value before writing any; one violation refuses the whole update with `CONFIGURATION_VALUE_INVALID` carrying `constraint`, and the document is unchanged
- [ ] A refund policy's tiers have `percent` in 0..100, `daysBefore` in 0..365 and distinct, and never refund more closer to the event; `NO_REFUNDS` has no tiers; a code outside the four is refused
- [ ] A document written before the section existed is given the documented defaults once, on first read
- [ ] `PlatformConfiguration` returns the new fields, `version`, `updatedAt` and `updatedBy`

**Tests** `PlatformRulesUpdaterTest` (L1)

### ET-ADM-002-R9 · Organizers and buyers read the rules without admin rights

THE SYSTEM SHALL serve `platformRules` to any signed-in caller from identity-service, read through
shared-library's `PlatformConfigurationReader`, with the commission that applies to the caller's own
organization.

**Acceptance**
- [ ] `platformRules` needs no role beyond being signed in and returns no personal data; `updatedBy` is a display name, never an id or email
- [ ] `commissionRate` is the caller's organization's own rate when it has one, else the default; both are percentages
- [ ] Reference lists (banks, categories, cities, cancellation reasons, KYB document types) stay catalog reference data, read with `referenceData(type:)`

**Tests** `PlatformRulesAssemblerTest` (L1)

### ET-ADM-002-R10 · A signed-out buyer reads the buyer-facing rules

WHEN a caller without a token queries `publicPlatformRules`, THE SYSTEM SHALL return only the
buyer-facing subset of the rules, rate limited per client address, and SHALL refuse every other
operation without a token.

The 'buy first' flow starts signed out and must show the hold time, the per-booking ticket limit
and the refund policies before anyone has an account. The subset is a separate query, not nullable
fields on `platformRules`, so nothing organizer-only can leak by a resolver mistake: the type does
not contain it.

**Acceptance**
- [ ] `publicPlatformRules` returns `version`, `updatedAt`, `currency`, `reservationHoldMinutes`, `reservationGraceMinutes`, `maxTicketsPerBooking`, `refundCutoffHours`, `rescheduleLimit` and `refundPolicies` (with tiers), and nothing else
- [ ] The type has no commission, minimum payout, escrow days, approval settings, `updatedBy` or any account data; `platformRules` still needs a signed-in caller and keeps them
- [ ] Cancellation reasons and banks stay catalog reference data (`referenceData(type:)`), not part of either query
- [ ] Without an `Authorization` header only a single, non-batched query whose top-level fields are all on the allowlist (`publicPlatformRules`) is admitted; `_service`, introspection, mutations, subscriptions, other queries and mixed selections get 401; `_entities` is refused except the router's `Organization { verified }` lookup for the signed-out event page ([ET-PLT-007](../../_platform/007-security-and-authorization/) R9)
- [ ] A request that carries a token is handled exactly as before
- [ ] An admitted anonymous caller is limited to 120 requests per minute per client address (the address the gateway appended to `X-Forwarded-For`); the 121st is refused with 429 and `Retry-After`; if Redis is unreachable the request is served, because the data is public
- [ ] The gateway needs no change: it already forwards `/graphql` without authentication and each subgraph decides

**Tests** `PublicOperationFilterTest` (L1), `PlatformRulesAssemblerTest` (L1), `PublicPlatformRulesSchemaTest` (L4)
