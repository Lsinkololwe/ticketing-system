# How to verify a spec

This is the method that produced [F-007 through F-018](FINDINGS.md) — eighteen findings, eight of
them OWASP A01, from opening **three** of the forty-one capability specs. It is written down
because the finding rate is the point: three for three is not a run of bad luck in three specs, it
is what unverified code looks like, and there are thirty-three specs left.

Nothing here is general advice. Every rule below is here because it caught something, and each one
names what it caught.

---

## 0 · What "verified" means, and what it does not

**Presence is not conformance.** The reconciliation pass measured which §4 operations exist. That
number says nothing about behaviour, and every spec opened since has proved it: `event(id)`
existed and returned other tenants' drafts; `removeMember` existed and never revoked event access;
`invitationByToken` existed and disclosed the invitee's phone number.

So: an operation being **bound** means a resolver answers it. It does not mean the resolver does
what §3 says. Verifying a spec is reading §3 one requirement at a time and asking *what would make
this false*.

**Do not tick a box the work does not satisfy.** `[~]` exists. A gate that overstates is worse than
one that is empty, because the next person trusts it. Several boxes in this corpus are `[~]` with a
sentence saying exactly which half is missing — that is the correct outcome when half is missing.

---

## 1 · Seven places defects were actually found

Run these against a spec before writing a single test. Between them they account for most of the
eighteen.

### 1.1 · The sibling comparison — **five for five**

> Where several operations read one collection, open the one that does **not** filter.

| Spec | The siblings that filtered | The one that did not |
|---|---|---|
| ET-CAT-001 | a dozen list queries all ending `PublishedTrueAndIsActiveTrue` | `event(id)` — [F-007](FINDINGS.md) |
| ET-TKT-001 | `cancelReservation`, `myActiveReservations` | `reservation(id)` — F-010 |
| ET-ORG-002 | `organizationMembers` (the paged list) | `organizationMember(orgId, userId)` — F-011 |
| ET-ORG-002 | — | `ownershipTransfer(id)` — F-011 |
| ET-ORG-002 | `invitationByToken`, narrowed to five fields | `declineInvitation(token)`, same bearer token, still returned the whole invitation — [F-046](FINDINGS.md) |

It works because the filter is usually written once, on the operation somebody thought about, and
the by-id lookup is added later by someone who assumes the guard is elsewhere. **This is the
highest-yield five minutes in the whole method.**

### 1.2 · Read an acceptance box as a claim, then grep for the mechanism

The boxes are testable sentences. Take them literally and look for the thing they name.

- *"Every event access grant is revoked in the same transaction"* → grep the removal path for the
  grant repository → **absent entirely** (F-015).
- *"A partial unique index on `organizationId` where `role = OWNER`"* → read the index initializer
  → **absent** (F-012).
- *"marks the membership `mirrorPending`"* → grep the model → **the field did not exist** (F-018).

Three of the largest findings were absences, not bugs. Nothing fails when a requirement was never
built; it simply is not there, and only the spec knows to look.

### 1.3 · Both halves of a compound condition

*"an **active** `ADMIN`"* — the role was checked, the status was not, so a suspended or removed
admin could be handed the organization (F-016). A compound condition with one half implemented
passes every ordinary test, because in ordinary use both halves are true.

Grep for the enum you expect and check the neighbouring one is there too.

### 1.4 · Read-then-write on anything with a status

If the code reads a status, decides, then writes — two callers both pass. Ask *who else clicks this
at the same time*, and the answer is usually mundane:

- A link forwarded to a group chat, opened by four people (F-014 — two members from one invitation).
- A nominee tapping "confirm" twice on a slow connection (F-016 — two handovers, ending with **no**
  owner).

The fix is always the same shape: **one conditional update that matches only while the state is
still what you read**, so exactly one caller's write reports a modified row. Booking's reservations
had it; identity's invitations and transfers did not. Three places, one pattern.

### 1.5 · Credentials as schema fields

`transferToken` and `invitationToken` were both selectable fields on output types (F-011, F-012).
A bearer credential that only its recipient needs has no reason to be readable through the graph —
and scoping the *read* does not fix it, because the field is still there for whoever the read
admits.

Grep the SDL for `token`, `secret`, `code`, `key` on any `type` (not `input`).

### 1.6 · `Instant.now()` anywhere near a deadline

Two consequences, both real here. It violates ET-PLT-001 R3, and it makes boundary tests
impossible — which is why ET-ORG-002 had no day-6/day-8 test to fail. Worse, the wall clock hid a
defect: the expiry those tests would assert was itself computed from `Instant.now()` (F-014).

`InlineNowLintTest` ratchets this. Identity went 97 → 88 across two passes.

### 1.7 · Ordering claims are load-bearing

When a spec says one thing happens *after* another, that ordering is usually a correctness
property, not a preference:

- The TTL index must fire **after** the sweep. It was `Duration.ZERO`, so MongoDB deleted the hold
  at the instant the sweep tried to claim it, and the seats were never returned — silently,
  permanently (F-009).
- The demote must precede the promote, because the one-owner index holds the key (F-016).

### 1.8 · Walk a money path hop by hop, all the way to a caller

A multi-hop path can be correct at every hop and dead as a whole. For each hop ask two questions:
**what identifier does the next hop receive**, and **who calls this method**. Then grep for the callers
of every method that writes a terminal status — zero callers is a finding, however well the method is
written and tested.

F-030 had five hops that each read correctly: an id sent to the provider but never stored, a webhook
looking up a record the path never creates, the only two methods that marked a payment succeeded with
no caller, and an after-commit listener that a reactive transaction never triggers. Two corollaries
from the same finding:

- **Two strings that must agree are one contract.** `platform.outbox.binding` and the declared stream
  binding live a hundred lines apart in one file; when they differ every component starts and no
  message arrives. Pair the fix with a lint (`OutboxBindingAlignmentTest`).
- **A deduplication key is unique per message, never per aggregate.** Keyed on the catalog event's id,
  the second message about one event looked like a redelivery of the first.
- **An idempotency record must not turn a hot write into a transaction.** Adding a separate movement
  document to every inventory hold made each hold a multi-document transaction on one shared tier
  document; the existing 200-against-50 contention test aborted it on write conflicts. Put the record
  inside the document the atomic update already targets, and run the contention test after any change
  to an idempotency mechanism on a hot path.

---

### 1.9 · A refusal is proven where the caller receives it, and a compensation by removing it

Two rules from moving the money processes onto durable workflows (ET-PLT-015, [F-031](FINDINGS.md)).

**Assert the refusal code at the far side of every boundary it crosses.** A refusal raised in an
update validator reached the client with its type replaced by the SDK's wrapper class; only the
message survived. Every unit test of the validator passed. The defect showed only in a test that sent
the update through a real workflow client and read the code back. So a refusal test runs the command
the way a caller runs it and asserts the `ErrorCode` the caller sees — not the exception the method
threw.

**Replay the history, not just the run.** A workflow test that only runs the code proves the first
execution. Fetch the recorded history and replay it against the current implementation in the same
test: a branch on anything the replay does not reproduce — the wall clock, a random value, the
execution's own id — fails there instead of after a deploy.

**A compensation is verified by deleting it.** For each reversing step — the escrow re-credit after a
failed transfer or refund, the automatic acceptance at a chargeback's deadline, the dual-control
check — remove the line and run the test that claims to prove it. A test that still passes proves the
happy path, not the compensation. Record the mutation next to the gate row it ticks.

### 1.10 · A process hiding outside the workflow engine

Once processes run on Temporal, the defects that remain are the ones that never made it there. Three
greps find them, and on 2026-09-13 they found eleven ([F-032](FINDINGS.md)):

- **`.subscribe(` in main code.** A fire-and-forget subscription in a resolver, controller or service is
  a job with no owner: the caller hears "done" before the work starts, and a failure goes to a log
  line. Anything that must outlive the request is a workflow; anything that must finish before the
  answer is part of the chain.
- **Mutations whose names are lifecycle verbs** — `initiate…`, `verify…`, `mark…Fulfilled`, `expire…`,
  `poll…`, `recover…`, `resolve…` — on an aggregate that has a workflow. Read what they write. A status
  or a money movement written beside the workflow is one the workflow does not know about, and its next
  timer or retry undoes it or doubles it.
- **`ApplicationEventPublisher`, `@EventListener` and `StreamBridge` outside `EventBridge`.** Search for
  the listener before believing the publisher does anything: an in-memory event with no listener is
  dead code that reads like a guarantee, and a direct send is a fact the outbox never recorded.

Then check the spec corpus for the same constructs — `sweep`, `lock:sweep:`, `| module |`, `saga` — so
the next agent does not rebuild from a spec what the code has just retired.

## 2 · How to write the test so it is worth its green

### 2.1 · Prove the row exists before proving it is hidden

Every negative case must first assert the row **is** reachable unscoped:

```java
assertThat(events.findById(DRAFT).block())
        .as("the draft must exist, or this test passes against an empty collection")
        .isNotNull();
```

Without it, a refusal test passes when the fixture never wrote the row, when the id is misspelled,
or when some unrelated error fires first. A green security test that would also pass against the
vulnerable code is worse than no test, because it gets cited as proof.

### 2.2 · Mutation-verify: break it and watch the right test fail

Non-negotiable, and it **caught two of this session's own tests being wrong**:

- The role-hierarchy test asserted permission *sets*. Making MANAGER inherit MARKETER produces an
  identical set, so every assertion still passed — the property was **structure**, and the test
  could not see it. Now it asserts parents directly.
- `EventWriteGuardLintTest` passed a mutation that removed the filter, because the javadoc
  explaining the filter still contained the word.

Break each guarantee separately and confirm *the assertion that names it* fails. If two mutations
fail the same test, the test is not distinguishing them.

### 2.3 · Assert both bounds

`isEqualTo(1)`, never `isLessThanOrEqualTo(1)`. "At most one" passes against an implementation that
admits nobody; "at least one" against one that admits everybody. Same for inventory: **exactly**
50, not "about 50".

### 2.4 · Pair a runtime test with a shape lint

The runtime test proves the rule is right. It cannot prove the code still applies it — the rule is
often one line whose removal leaves a method that compiles and reads naturally. So:

- **Runtime** (layer 2/5, Testcontainers): the boundary refuses, the race resolves, the books
  balance.
- **Lint** (layer 1, reads source): the filter is still called, the transaction still wraps it, the
  credential is still off the type.

`ReservationScopeLintTest` and `ReservationVisibilityTest` are the pattern.

### 2.5 · Rules for writing a lint

Learned the hard way, three times each:

1. **Strip comments before matching.** A lint that counts prose charges a file for documenting
   itself, and fails on a pure comment edit. Hit in `TenantBoundaryLintTest`,
   `EventWriteGuardLintTest` and `IndexAuthorityLintTest` — the last one had over-counted booking
   by one for who knows how long.
2. **Keep string literals** when the thing you are checking *is* a literal (role names, error
   codes). Stripping them leaves the assertion comparing against nothing.
3. **A lint that scans nothing passes forever.** Assert the census found something —
   `assertThat(examined).isGreaterThan(50)` — separately from asserting it found no problems.
4. **Exclude the lint from itself.** A file that names every marker it searches for will match all
   of them.
5. **Watch for catastrophic backtracking.** `(?:[^*]|\*(?!/))*` overflowed the stack on the first
   file over a few hundred lines. Use reluctant quantifiers.
6. **An allowlist needs a self-check** that its entries still exist, or it silently grants
   exemptions nobody is using.

### 2.6 · Layer it honestly

A rule over values is layer 1: no Spring, no database, a millisecond. The state machine, the role
closure, the fee arithmetic. If you are asserting arithmetic inside a Testcontainers test, move it
down. `TestLayerLintTest` derives each class's layer from what it references and fails when the
tag disagrees — it found three disagreements on its first run, one of them in its own logic.

---

## 3 · Traps to avoid, all of them fallen into

**Do not overstate a finding's reach.** F-007 was first written as "unauthenticated". It was not —
all three subgraphs require a token on `/graphql/**`, and the schema's `PUBLIC` comments are
documentation, not enforcement. Corrected the same day. **Check the enforcement, not the
annotation.**

**Do not classify from one file.** `paymentAttempts` was classified wrong twice: once from a list
of names, once from the `PaymentAttempt` model. The answer was one annotation on `PaymentIntent` —
`reservationId` is `@Indexed(unique = true)`, so §4 had simply named the wrong end of a 1:1
relationship. **Read both sides of a relationship before declaring a gap.**

**Do not call something dead from one grep.** `getCurrentPrice()` looked unused —
`grep "\.getCurrentPrice("` found nothing — and deleting it would have removed the only early-bird
implementation in the codebase. It is reached as `TicketTier::getCurrentPrice`, a **method
reference**. Search for `::name` as well as `.name(`, and let the compiler confirm before deleting
(F-023).

**Do not half-migrate.** The permission vocabulary is `SCREAMING_CASE` where §4 says
`module:action`. Changing the enum alone would leave 38 call sites across three services comparing
against the old strings — every permission check broken while looking correct. Recorded in F-013,
not attempted.

**Do not silently correct an over-grant you cannot replace.** Two permissions §4 marks opt-in are
unconditional. Removing them takes financial visibility from every manager, with nothing to restore
it until ET-ORG-003 exists. Record the deviation; do not create an outage to satisfy a document.

---

## 4 · The order to work in

1. **§3, one requirement at a time.** Not the gate — the gate is a summary and it is easy to tick.
2. **Run §1's seven checks first**, before writing anything. They are minutes each and they find
   the absences.
3. **Fix, then test, then mutate.** In that order. A test written before the fix tends to assert
   the behaviour you just implemented rather than the property you need.
4. **Run the full suite.** This corpus has ~30 lints and they will catch you: blocking calls,
   fire-and-forget subscribes, unregistered indexes, wall-clock reads, unregistered error codes,
   drifted contracts. Five caught the R8 work alone. **Treat every one as correct until proven
   otherwise** — each was right this session.
5. **Update the spec and `FINDINGS.md` as part of the task**, not afterwards. Tick only what is
   true; leave `[~]` with a sentence naming the missing half.
6. **Lower the ratchet you improved.** A budget left high re-opens exactly the room you closed.

---

## 5 · What this is worth

Eighteen findings from three specs. The eight access-control ones were each reachable by an
ordinary signed-in customer — an account that costs a phone number.

Thirty-three capability specs have never been opened, and **not one test is tagged to any of them**.
The code exists for many; the evidence does not. On the record so far, assuming they are clean is
optimism rather than a finding.

The method above is what makes that tractable: it is repeatable, it needs no context from this
session, and the corpus itself tells you when a spec is done.
