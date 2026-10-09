# Org Admin — spec conformance audit

Compares the operations the `specs/` directory defines against what the backend
schemas actually expose, for the three areas the organizer portal depends on.

Method: for each operation named in a `spec.yaml` `graphql:` block, grep the
owning service's `schema.graphqls` for its definition.

**Headline: the specs are largely unimplemented. The organizer portal is wired to
what exists, which is a much smaller and differently-shaped surface.**

---

## finance/003-payouts-and-settlement (ET-FIN-003)

| Spec'd operation | In schema |
|---|---|
| `payoutEligibility(eventId)` | **no** |
| `myPayoutRequests(organizationId, status, page)` | **no** |
| `bankAccounts(organizationId)` | **no** |
| `requestPayout(input)` — with `idempotencyKey` | **no** |
| `cancelPayout(id)` | **no** |
| `startBankVerification(id)` | **no** |
| `confirmBankVerification(id, amt)` | **no** |

**0 of 7 present.** What exists instead:
`payoutRequestsByOrganizer`, `bankAccountsByOrganizer`,
`createPayoutRequest`, `cancelPayoutRequest`, plus the CRUD bank-account
mutations. Same intent, different names and shapes.

Two substantive gaps, not just naming:

1. ~~**No idempotency key on payout creation.**~~ **CLOSED.**
   `CreatePayoutRequestInput.idempotencyKey` now exists, backed by a
   `unique + sparse` index on `PayoutRequest.idempotencyKey`. A repeat returns
   the original payout; a concurrent duplicate loses the index race and is
   answered with the winner rather than a raw database error. Sparse so the
   historical rows that predate the field — all null — do not collide.
   `auto-index-creation: true` is set in `application.yml`, so the annotation
   really does create the index at runtime; without that the guarantee would be
   inert. The portal mints the key once when the payout dialog opens and reuses
   it across retries — minting per attempt would defeat the whole mechanism.
   Proven by `PayoutIdempotencyIntegrationTest` (5 tests, real MongoDB),
   including the concurrent case a check-then-write in application code cannot
   survive.
2. **No bank-account verification flow.** `startBankVerification` /
   `confirmBankVerification` do not exist, and `BankAccountVM.isVerified` is
   therefore a field nothing can ever set to true. Payouts can be sent to an
   unverified destination.

**`myPayoutSources` (added in this work) overlaps `payoutEligibility(eventId)`.**
It was added because organizers had no way to obtain their own `escrowAccountId`
— every escrow query is admin-tagged — so the payout button was inert. It should
be reconciled with the spec'd operation rather than kept alongside it.

## ticketing/003-validation-and-checkin (ET-TKT-003)

| Spec'd operation | In schema |
|---|---|
| `checkInSummary(eventId)` | **yes** |
| `recentCheckIns(eventId, limit)` | **yes** — bounded at 100 server-side |
| `checkInConflicts(eventId, page)` | **yes** |
| `validateTicket(input)` | **yes** |
| `uploadScans(input)` | **yes** |
| `reviewConflict(id, note)` | **yes** |
| `validateByReference(input)` | folded into `validateTicket` |

**6 of 7 present, one folded.** `validateByReference` was not built as a
separate operation: it differs from `validateTicket` only in that the code is
typed rather than scanned, which is already carried by `method: MANUAL` plus a
required reason. Two operations performing the same write would be two places
for the guarantee to drift.

### What was built

- **`booking_checkins`** with a **UNIQUE `ticketId`** index. This is the
  "a ticket admits once" guarantee, and it now lives where it can be enforced.
  What stood before was a read-modify-write on the ticket — load it, see
  PURCHASED, set VALIDATED, save — so two stewards scanning the same ticket a
  moment apart both read PURCHASED before either wrote, both were told the
  ticket was good, and **the ticket admitted twice**. Nothing recorded that it
  had.
- **UNIQUE SPARSE `scanId`** for offline upload idempotency: a device that
  loses its connection mid-upload retries the whole batch, and without this the
  retry admits everyone in it again — and reports them as duplicate *people*
  rather than a duplicate transmission, which is a much more alarming report.
  Sparse because online scans carry no scan id, and a non-sparse unique index
  would treat every one of those nulls as a duplicate of the others.
- **`booking_checkin_conflicts`**, so a refused scan is kept rather than lost.
  For an offline duplicate this row is the *only* evidence that a second person
  walked in: the losing device's admission gets no check-in row, because the
  unique index forbids it. Attendance and physical admissions therefore differ
  by exactly the duplicate-scan count, and the gate screen shows both rather
  than folding them together.
- **`eventId` on the validate input.** The old signature had none, so a
  perfectly valid ticket for tomorrow's show **passed at tonight's gate**.
- **Organizer scoping** on every read and on `reviewConflict`. Scoping by event
  id alone made the gate log readable by anyone who knew an event id, and
  attendance is commercially sensitive. Another organizer's conflict is now
  indistinguishable from one that does not exist, so the mutation cannot be
  used to probe for other organizers' events.

Proven by `CheckInIntegrationTest` — 15 tests against real MongoDB, including
**8 concurrent scans of one ticket producing exactly one admission and seven
conflicts**. That is the case a check-then-write in application code cannot
survive and a mocked repository cannot detect.

The portal's gate screen now reads its counters from `checkInSummary` — counted
by the server over `booking_checkins` — instead of deriving them from whichever
roster page the browser happened to hold, which reported the check-ins visible
on screen and called it attendance.

### Still missing

- **No offline mode at the client.** `uploadScans` exists and resolves
  deterministically (earliest `scannedAt` wins, ties broken by `scanId`
  lexicographically), but nothing scans offline yet: there is no local
  signature verification and no cached event key. For outdoor venues in Zambia
  this is not a nice-to-have.
- **No `CheckInConflictDetectedEvent`.** Conflicts are recorded but not
  published, so nothing reacts to them while the event is running.
- **No per-event `ticket:scan` grant.** ET-TKT-003 requires one from
  ET-ORG-003, which does not exist. Until it does, any of an organization's
  organizers can scan any of its gates — the coarse role gate is all there is.
- **No camera scanner.** The gate screen's scanner panel is still a placeholder;
  every admission today goes through the manual path, which is why that path
  requires a reason and is counted separately.

## organization/002-teams-and-invitations (ET-ORG-002)

Best of the three.

| Spec'd operation | In schema |
|---|---|
| `organizationMembers(organizationId, role, status, page)` | **no** |
| `pendingInvitations(organizationId)` | **no** |
| `myPendingInvitations` | **no** |
| `invitationByToken(token)` | yes |
| `revokeInvitation(id)` | yes |
| `declineInvitation(token)` | yes |
| `inviteTeamMember` | yes — but `(organizationId, input)`, spec says `(input)` |
| `removeMember` | yes — returns `Boolean!`, spec says `OrganizationMember!` |

**4 of 8, two with divergent signatures.** No paginated member query exists, so
the portal reads `myOwnedOrganization { members }` — the full roster in one
response, which will not hold at scale. No pending-invitations query exists, so
the team screen cannot show invitations that have been sent but not accepted.

---

## What this means for the redesign

The design project contains screens for features the backend does not have:
`Org Admin - Events & Finance` shows escrow and settlement detail;
`Org Admin - Team & Permissions` shows event-level access grants and pending
invitations; the check-in flow assumes conflict review. Building those screens
against today's schema means either leaving large parts inert or inventing the
data — and inventing it is exactly what this work has spent its time removing.

**Recommended order:**

1. ~~Close the payout idempotency gap.~~ **DONE.**
2. ~~Implement the check-in persistence model (`booking_checkins` + unique
   index) before building any check-in UI.~~ **DONE.**
3. Implement `payoutEligibility` and reconcile `myPayoutSources` into it.
4. Add `organizationMembers` pagination and `pendingInvitations`, then build the
   Team & Permissions screen.
5. Then rebuild the remaining screens to the design.

Each step wants a Testcontainers integration test in the same shape as
`OrganizerDashboardAnalyticsIntegrationTest` and `EventCreationIntegrationTest`
— particularly the idempotency and unique-index guarantees, which are only
observable against a real database.
