# ET-XXX-000 · <Title>

> **Conformance** · <V3 §n · PDI Phase n · US Part n §n — the external stops this spec satisfies>

## 1. Capability

<What this delivers, in domain terms, and why the platform needs it. Written as though
nothing exists yet. No references to current code, no file:line citations, no
"currently". Two to four paragraphs.>

## 2. Design decisions

<The calls this spec makes and why. One bolded paragraph or table row per decision. Every
construct must come from specs/CONVENTIONS.md.>

**Rejected alternatives**

- <one line each — this is what stops the next agent re-litigating a settled decision>

## 3. Requirements

### ET-XXX-000-R1 · <short name>

WHEN <trigger>, THE SYSTEM SHALL <observable behaviour>.

**Acceptance**
- [ ] <mechanically checkable statement>
- [ ] <mechanically checkable statement>

### ET-XXX-000-R2 · <short name>

IF <condition>, THEN THE SYSTEM SHALL <observable behaviour>.

**Acceptance**
- [ ] ...

## 4. Model

### Documents

| Collection | Owning service | Key fields | Notes |
|---|---|---|---|
| `booking_tickets` | booking-service | `_id`, `eventId`, `ownerId`, `status` | |

### Indexes

| Collection | Index | Kind | Why |
|---|---|---|---|
| `booking_tickets` | `{ eventId: 1, status: 1 }` | compound | the check-in query |

### Events

| Tier | Java type | Wire name | Topic | Consumers |
|---|---|---|---|---|
| bus | `TicketPurchasedEvent` | `booking.TicketPurchased` v`1` | `booking-events` | catalog, identity |

Only cross-service facts appear here, each staged in the outbox by the transaction that makes it
true. A step within a service is a workflow activity, declared below, never an in-memory event.

### Workflows and Schedules

| Workflow | Id | Task queue | Start and conflict | Updates · signals | Timers |
|---|---|---|---|---|---|
| `PurchaseWorkflow` | `purchase/{reservationId}` | `booking-checkout` | Update-with-Start, `USE_EXISTING` | `pay`, `cancel` · `paymentCallback` | expiry at `expiresAt` |

Every row is a row of the [ET-PLT-015](../_platform/015-durable-execution/) §4 registry. Timers are
workflow timers; a recurring job is a Temporal Schedule with overlap policy `SKIP` — never a
`@Scheduled` sweep or a Redis lock.

### GraphQL

| Operation | Kind | Subgraph | `@auth` | Returns |
|---|---|---|---|---|
| `reserveTickets` | mutation | booking | `CUSTOMER` | `ReservationResult!` |

### Redis keys

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `…` | | | |

### Error registry rows

| Code | Refusal type | GraphQL `ErrorType` | Retryable |
|---|---|---|---|
| `TIER_SOLD_OUT` | `TierSoldOut` | `FAILED_PRECONDITION` | no |

### Flow

```
<mutation> → <service> → <documents written> → <events> → <consumers> → <read model>
```

## 5. Tasks

- [ ] **T1 · <name>**
  - requirements: R1
  - files: `<path>`
  - verify: `<command>`
  - parallel-safe: yes
  - depends: —

## 6. Out of scope

- <named, with the spec that covers it if there is one>
