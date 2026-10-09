# Event Ticketing · specification corpus and build order

A greenfield specification of the platform on **Java 21 / Spring Boot 3.5.4 WebFlux,
Netflix DGS 10.5 over Apollo Federation 2.9, reactive MongoDB,
Azure Service Bus and Keycloak 26**, using the constructs in [CONVENTIONS.md](CONVENTIONS.md).

Written 2026-07-30 against `docs/USER_STORIES.md` v3.0 (roles and hierarchy),
`docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` (the financial model),
`docs/PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md` (transactional integrity), and the
architecture documents in `docs/architecture/`.

---

## Ground rules for this corpus

**Greenfield.** Every spec describes the target system. None references what exists
today. Reconciliation against the working tree is a separate pass — see
[README §Reconciliation](README.md#reconciliation).

**The stack is settled and is not a spec's decision to make.** Three services, three
subgraphs, one MongoDB, one router, one Keycloak (two realms, D-48). A spec that wants a fourth
service, a second broker or a per-service database is proposing a platform change and
belongs in `_platform/`, with the argument written down.

**Every construct is from CONVENTIONS.md.** Fully reactive signatures, `Instant` from an
injected `Clock`, `BigDecimal` money in a double-entry ledger, two-tier eventing with the
bus never inside a transaction, federation ownership with `@tag` contracts, one
permission resolver, typed errors with registry codes. Anything else is a defect, not a
variation.

---

## Decisions taken while writing this corpus

> **Status 2026-10-03 (Temporal decisions D-21, D-28, D-33).** Code is implemented: ET-PLT-015 is
> `implemented` and the booking, catalog and identity workflows exist and replay in tests. The self-hosted
> staging trial D-33 requires (a purchase, a payout and a refund through the staged service) has **not been
> run**. `LatePaymentRefundWorkflow` (D-22) is being implemented. Decision text below is unchanged.

| # | Question | Decision |
|---|---|---|
| **D-01** | MongoDB topology | **Replica set in every environment**, single-node in development. Reservations, escrow movements and journal pairs are multi-document writes; against a standalone `mongod` `@Transactional` is silently inert and the platform oversells under load |
| **D-02** | One datastore per service | **MongoDB only.** No service connects to PostgreSQL: Keycloak runs its own schema and nothing else relational exists. A blocking JDBC pool inside a strictly reactive stack has to earn its place, and once the outbox lives in MongoDB there is nothing left for it to do |
| **D-03** | Eventing tiers | **Azure Service Bus between services, with a MongoDB outbox in front of it; within a service, a step is a workflow activity or part of one transaction, never an in-memory event** (amended with D-21). The outbox row is staged in the same reactive transaction as the document, so the write and the intent to publish cannot disagree; the drain reaches the bus afterwards. `StreamBridge` is called only by the drain |
| **D-04** | Commission model | **Two-stage: pending at purchase, recognised at event completion.** Money owed on a ticket for an event that is later cancelled was never revenue, and a platform that books it at purchase reports a profit it must then reverse |
| **D-05** | Escrow granularity | **One escrow account per event**, not per organizer. Cancelling one event must not reach into another event's settled funds; and a per-event balance is what makes a refund obligation computable |
| **D-06** | Payment provider | **PawaPay only at launch, behind a `PaymentProviderPort`.** MTN, Airtel and Zamtel reach the platform through one aggregator; the port exists so the second aggregator is an adapter, not a rewrite |
| **D-07** | Idempotency | **Every money-moving mutation takes a client-supplied `idempotencyKey`**, guarded in Redis with a 24-hour TTL and by a unique index on the persisted attempt. Mobile networks retry; a double charge is not recoverable by apologising |
| **D-08** | Inventory | **One conditional atomic update per reservation**, `findAndModify` filtering on `available >= quantity`, plus optimistic `@Version`. Read-modify-write oversells at on-sale and only at on-sale, which is when it matters |
| **D-09** | Reservation model | **Tickets are reserved before they are paid for** *(and, per **D-38**, after the buyer's contact is verified)*, with a 10-minute TTL swept by a scheduled job. Mobile-money confirmation takes tens of seconds and sometimes minutes; holding inventory during it is the difference between a sale and a race |
| **D-10** | Permission resolution | **One resolver, one order**: platform role → event grant → organization role → custom → denied → deny. Explicit deny beats inherited allow. Written once, in identity-service, consumed by the other two over the internal API |
| **D-11** | File uploads | **REST with presigned URLs, never GraphQL multipart.** Streams to storage, no CSRF surface, native progress, and the server never buffers the file |
| **D-12** | Dashboard freshness | **Smart polling, not GraphQL subscriptions.** Apollo Router's managed federation does not carry subscriptions on the path this platform uses; polling with a visibility-aware interval is honest about that rather than half-building a transport |
| **D-13** | Statistics | **Server-side MongoDB aggregation with `$match` first**, one pipeline per stat type, never client-side counting over a fetched page |
| **D-14** | Currency | **ZMW only at launch**, but stored on every monetary field. Single-currency assumptions are cheap to make and expensive to remove |
| **D-15** | Notification channels | **WhatsApp, SMS, push and email.** *(SMS and the SMS-fallback wording superseded by **D-39**, 2026-10-04: WhatsApp and email only for codes and tickets.)* WhatsApp is primary in-market and carries the OTP that is the login mechanism; SMS is its fallback; email is for receipts and the invitation flow, which needs a durable addressable identity |
| **D-17** | Permission vocabulary | **`module:action` — flat, exactly one colon, no dots — a closed catalogue declared in code, with `manage` implying CRUD and any write implying read, and no third rule.** A finer subject is its own module (`ticket_tiers`, `ticket_qr`, `bank_accounts`), never a dotted path, so `split(':')` is the whole parser. Keycloak owns roles; MongoDB owns the role→permission mapping as **one document per role** — no junction collection, no view, no `$lookup` on the authorization path. A platform administrator edits the mapping; nobody invents a permission, because a key nothing enforces is protection that does not exist |
| **D-18** | Reference data | **One polymorphic collection, compiled types and runtime rows.** Workflow statuses are reflected out of the code's own enums rather than seeded, so the value list cannot drift; each carries a coarse `WorkflowSemantic`, and code branches on the semantic, never on the code string, so a new status needs no deployment |
| **D-19** | Operation naming, where the corpus and the schema disagree | **Split, because the two halves are different questions.** *Pagination:* the twins collapse — 60 `*OffsetPagination`/`*CursorPagination` pairs become one field each, and **§4 already names the survivor per operation** (`*Connection!` with `(first, after)` for the seven public and personal feeds; `*Page!` with `(page)` for the thirty-three admin and organizer tables). Offering both shapes for the same data was never a decision anyone took, and a client choosing between them is choosing between two ways of being right. *Naming:* the schema wins — `createPayoutRequest`, `deleteTicketTier`, `submitOrganizationForReview` stand, and §4 is amended to them. `<verb><Noun>Request` is a defensible convention consistently applied; renaming 36 working operations to satisfy a document would be precedence exercised for its own sake |
| **D-16** | Target scale | **200,000 tickets/month, with an on-sale peak of 5,000 reservations/minute against a single event.** Every capacity requirement in this corpus is sized against that second figure, because the first never breaks anything |
| **D-20** | Whether the tenant filter replaces or joins the remote permission check | **They work together; neither may replace the other.** The filter (`findByIdAndOrganizationIdIn`) asks whether the event belongs to an organization the caller is in — a database predicate that knows nothing else. The check (`checkEventAccess`) asks whether the caller may perform *this action*, resolved through D-10's order. A MARKETER cannot edit events, a CONTRIBUTOR is view-only, and event-level roles override the organization role for one event, so a filter that only knows membership would hand all of them owner-level power over everything the organization runs — ET-ORG-002's product promise undone by an optimisation. The reverse is the F-001 shape, where forgetting the check is invisible. **The availability cost is accepted knowingly**: an event mutation depends on identity-service answering, so an identity outage means organizers cannot edit, publish or cancel. Caching the decision was declined — it trades a security property for an unmeasured performance gain, and a cached grant outlives a demotion. Degrading to membership-only during an outage was rejected outright: it turns an availability incident into a privilege-escalation window at the moment nobody is watching |
| **D-21** | How multi-step, long-running and time-based processes run | **Temporal, one namespace per environment, task queues per concern ([ET-PLT-015](_platform/015-durable-execution/)).** Hand-rolled sagas, `@Scheduled` sweeps under Redis locks and in-process listener chains become workflows whose history survives a crash and whose timers fire with no pod running the code that set them. MongoDB stays the system of record, with statuses projected by idempotent compare-and-set activities that stage their outbox rows in the same transaction. Service Bus stays for the ET-PLT-003 facts, because topic fan-out to independent subscribers is the one thing Temporal signals cannot do without every publisher holding a recipient list. **The cost is accepted knowingly**: Temporal's database becomes a checkout dependency, sized before the first production write. A payload codec is deferred behind an ids-only payload rule (R7) |
| **D-22** | A payment that confirms after its reservation lapsed | **The seats are released after the 5-minute grace and the late money is refunded in full, automatically** ([ET-PAY-001](payment/001-payment-intent/) R5, `LatePaymentRefundWorkflow`). Holding seats open for a payment that may never come starves an on-sale; the buyer is made whole and can buy again. Decided 2026-09-14 |
| **D-23** | Fees on an organizer payout | **None.** The platform's income is the per-ticket commission ([ET-FIN-002](finance/002-commission/)); a payout settles the full escrow balance, and `platformFee`/`processingFee` no longer exist on a payout request ([ET-FIN-003](finance/003-payouts-and-settlement/)). Decided 2026-09-14 |
| **D-24** | How often internal reconciliation runs | **Hourly, as [ET-FIN-005](finance/005-reconciliation/) R2 requires** — the escrow and escrow-journal checks and the alerts on what they find, staggered at :05, :20 and :35 UTC; the summary stays weekly. An existing Schedule takes the code's cadence at boot and keeps an operator's pause. Decided 2026-09-14 |
| **D-25** | A chargeback nobody decides | **Escalate to finance 24 hours before the provider's response deadline, then accept at the deadline** ([ET-FIN-004](finance/004-refunds-and-chargebacks/) R8). Accepting silently loses disputes that evidence could win; one alert with a day left is the cheapest point to ask a person. Decided 2026-09-14 |
| **D-26** | Which refunds approve themselves | **Only an event cancellation's refund.** Every other refund, whatever its size, is decided by a person; one still waiting is escalated to finance after two days and again after five, and is never approved by waiting ([ET-FIN-004](finance/004-refunds-and-chargebacks/) R5). The earlier K1,000 automatic limit is withdrawn: a compromised buyer account could otherwise refund every ticket it holds instantly. Decided 2026-09-14 |
| **D-27** | Where micro-deposit verification costs are booked | **A new expense account, `5050 Account Verification Costs`**, one journal entry per deposit sent ([ET-FIN-003](finance/003-payouts-and-settlement/) R4). `5040` is Bad Debt in the seeded chart, and mixing the two would make bad debt look worse than it is. Decided 2026-09-14 |
| **D-28** | Where Temporal runs, and where its data lives | **Self-hosted Temporal, not Temporal Cloud** (revised 2026-09-18). Development uses the Temporal server already set up in `docker-resources` (`dev_temporal`, namespace `ticketing`). Staging and production run a Temporal service the platform operates, persisting to its own PostgreSQL databases `temporal` and `temporal_visibility`, with 30-day retention ([ET-PLT-015](_platform/015-durable-execution/) §4). Workflow history never leaves the platform's infrastructure, and payloads carry ids only (R7). Decided 2026-09-14, revised 2026-09-18 |
| **D-29** | A MongoDB user whose Keycloak user is gone | **Reconciliation leaves the document as it is.** Only Keycloak's own admin `DELETE` event marks a user `DELETED` ([ET-IDN-002](identity/002-keycloak-user-sync/) R4); absence from a listing is not proof of deletion, and a partial read must never tombstone real accounts. Decided 2026-09-14 |
| **D-30** | Who may start a full Keycloak re-sync | **`SUPER_ADMIN` only**, on the GraphQL mutation and the REST endpoint alike ([ET-IDN-002](identity/002-keycloak-user-sync/) R4). It pages through every user in the realm. Decided 2026-09-14 |
| **D-31** | A test deposit PawaPay accepted but never delivered | **Its cost is reversed automatically once PawaPay confirms the failure**, and the verification ends so the organizer can start again ([ET-FIN-003](finance/003-payouts-and-settlement/) R4). The books show only money that actually left. Decided 2026-09-14 |
| **D-32** | Who is told about chargeback and refund escalations | **A named finance lead**: whoever holds the `FINANCE_LEAD` role, by email and WhatsApp, with a copy to the shared finance channel ([ET-FIN-004](finance/004-refunds-and-chargebacks/) R5, R8; [ET-PLT-007](_platform/007-security-and-authorization/)). One accountable person, and nobody loses sight of it. Decided 2026-09-14 |
| **D-33** | When the production Temporal service is set up | **Before staging.** The self-hosted service is stood up for staging first and a purchase, a payout and a refund run through it; production is set up the same way before launch (`docs/operations/TEMPORAL_SELF_HOSTED.md`). The development server stays on developers' machines: it keeps everything in one SQLite file and has no redundancy. Decided 2026-09-14, revised 2026-09-18 |
| **D-34** | Whether a Schedule's timing changed in code applies on release | **Yes — the code decides, for every Schedule.** At boot an existing Schedule takes the code's cadence, action and overlap policy; an operator's pause is kept. Decided 2026-09-14 |
| **D-35** | Whether managers see money and admins request payouts | **Each organization's owner decides**, with two switches: `managersCanViewFinancials` and `adminsCanRequestPayouts` ([ET-ORG-003](organization/003-permission-resolution/) §4). Organizations that existed before the switches keep the access their teams had (both on); a new organization starts with both off. Only the owner may change either. Decided 2026-09-18 |
| **D-36** | Who is told an event is waiting for approval, and who hears the outcome | **Every active platform `ADMIN`** is told when an event is submitted or resubmitted; **the event's organizer** is told when it is approved, rejected or sent back for changes ([ET-NTF-002](notification/002-lifecycle-triggers/) rows 18, 19, 19a, 32). Decided 2026-09-18 |
| **D-37** | Where the role → permission table lives *(a pointer, not a rewrite: ET-PLT-013 and the resolution-order text in `docs/` that describe an editable mapping are superseded by this row)* | **In the software, changed only by a release** (`com.pml.shared.security.Permission`, identity's `OrganizationRole` and `EventRole`). No administrator edits it at runtime; the stored copies were dropped. Supersedes ET-PLT-013's editable mapping (R3, R7, R8). Decided 2026-09-18 |
| **D-38** | Buy first, sign in second | **A visitor browses and chooses tickets signed out; identity is asked for only at the moment of holding the tickets.** The reservation is created after the contact is verified, never before ([ET-TKT-001](ticketing/001-reservation-and-hold/)). Seats are not held for an anonymous session, so a bot cannot drain inventory without owning a verifiable contact. Decided 2026-10-04 |
| **D-39** | Buyer channels | **WhatsApp and email at launch; SMS is dropped, and no message carries a link by SMS.** The code and the tickets travel by WhatsApp (international numbers accepted, country allowlist) or by email; a buyer who has neither cannot buy. Supersedes the SMS halves of **D-15** and of ET-IDN-001 R2 and ET-NTF-001. Decided 2026-10-04 |
| **D-40** | Ticket QR | **One fixed QR per ticket, issued with the ticket and never rotated.** It is a signed, opaque reference, not a payload of personal data ([ET-TKT-002](ticketing/002-ticket-issuance-and-qr/)). A screenshot of it works at the gate once; the second scan is refused. Decided 2026-10-04 |
| **D-41** | A QR that will not scan at the gate | **Fallback: the staff member enters the ticket code plus the holder's ID number; the first scan or entry wins** and every later one is refused as already used ([ET-TKT-003](ticketing/003-validation-and-checkin/)). Decided 2026-10-04 |
| **D-42** | Payment instruments | **Mobile money only; no cards.** The payer's mobile-money number is entered at payment and is separate from the contact that identifies the account ([ET-PAY-001](payment/001-payment-intents-and-providers/)). Any card assumption elsewhere in the corpus is superseded. Decided 2026-10-04 |
| **D-43** | Who creates an account | **The account is created identity-first by `AccountEnsureWorkflow` ([ET-IDN-004](identity/004-accounts-and-contacts/)); the database's unique index on the verified contact is the arbiter of a race.** The Keycloak plugin never creates users; a lost race returns the winner's account. Decided 2026-10-04 |
| **D-44** | How the buyer signs in during checkout | **The code is entered inside the checkout page and verified by identity-service; the result is a one-time login handle that a Keycloak authenticator redeems** to complete an authorization-code flow with no further screen. Amends ET-IDN-001 R5: the code is no longer typed into a Keycloak page for the buyer flow, but **Keycloak remains the only token issuer**. Decided 2026-10-04 |
| **D-45** | Where buyer tokens live | **In a server-side session of the buyer app (Next.js), never in the browser.** The browser holds an opaque, HttpOnly session cookie; GraphQL goes through a server route that attaches the token. Decided 2026-10-04 |
| **D-46** | Personal data in process plumbing | **None.** Workflow ids, search attributes, workflow payloads, activity inputs, queue names and log lines carry opaque ids and HMAC keys, never an email, a phone number or a name; a lint fails on `@` or `+<digits>` in an id or search attribute ([ET-PLT-015](_platform/015-durable-execution/), [ET-PLT-008](_platform/008-data-protection/)). Decided 2026-10-04 |
| **D-47** | Who owns `enabled` (suspension) | **identity-service decides suspension and applies it to Keycloak; a change made in the Keycloak console is adopted back** into the account and audited. Supersedes ET-IDN-002 §2 "`enabled` lives only in Keycloak". Decided 2026-10-04 |
| **D-48** | Realms | **Two: `myticketzm` (buyers and organizers) and `myticketzm-admin` (platform staff).** The corpus previously said one realm, `event-ticketing`. The `user-sync` listener is enabled in both. Decided 2026-10-04 |
| **D-49** | How platform staff are created | **From the admin app, identity-first** (same workflow shape as buyers, with password and second factor set up at first login). Creating a staff user in the Keycloak console is break-glass and is adopted by UserSync, flagged for review. Decided 2026-10-04 |
| **D-50** | Identity defaults | **Proof 2 min, login handle 60 s, challenge 5 min, resend cooldown 60 s, 5 tries, 15 min lock, account repair schedule every 15 min, PROVISIONING alert after 10 min.** All are configuration keys in [CONTRACT.md](identity/004-accounts-and-contacts/CONTRACT.md) §7. Decided 2026-10-04 |
| **D-51** | Keycloak admin client | **Stay on `keycloak-admin-client` 26.0.12, the last standalone release, against the 26.5.2 server.** Maven Central carries no standalone 26.1+ admin client; 26.8.0 ships only split core/internal modules whose pom says internal-only. The Admin REST API is stable across 26.x and `KeycloakServiceContainerTest` runs the client against the 26.5.2 server. Its RESTEasy 6.2.15 wants slightly newer jaxb, mail, activation and logging than Spring Boot 3.5.5 manages; those are raised in the root pom and its Jackson 2.21 artifacts are excluded so the platform keeps Boot's Jackson. Revisit when a supported 26.x client ships. Decided 2026-10-04 |
| **D-53** | Contact codes and repair | **Adding a contact needs only the new contact's code; a pending code may be sent again after `identity.contact.resend-after` (PT5M, "after five" read as five minutes); quarantine stays P30D; the account repair D1..D9 runs on the Schedule `identity-account-repair` (PT15M, overlap SKIP), created at boot in prod.** Revisit: step-up for add, the meaning of "after five". Decided 2026-10-04 |
| **D-52** | Contact linking and change | **A change, removal or primary switch is authorised by a fresh code to the current primary verified contact; no support-recovery path yet (`NO_VERIFIED_CONTACT`); a released contact is quarantined `P30D` (`identity.contact.quarantine`); the last verified contact cannot be removed; one primary, switchable among verified contacts after step-up.** Revisit: recovery proofs, quarantine length, step-up for add. Decided for implementation 2026-10-04, [F-044](FINDINGS.md) |


## Rulings — all ten answered 2026-09-01

Questions the corpus cannot answer from evidence. Each is blocking real work, and each is small —
what makes them rulings is that the tree contains two defensible answers, not that they are hard.
**Not a backlog**: nothing here is a task waiting for a spare afternoon.

| # | Question | What it blocks | What is needed |
|---|---|---|---|
| **O-1** | ~~The 20 catalog pairs where the offset half is `ADMIN`/`ORGANIZER` and the cursor half is `PUBLIC`~~ **Reframed 2026-09-01 — mostly not a ruling** | 20 pairs; 7 §4 names | **Ruled 2026-09-01, three ways.** *(a)* The ten reference-data pairs were never a ruling: ET-CAT-003 §4 declares `provinces`, `cities` and `categories` as bounded PUBLIC lists and catalog shipped **no bare list at all** — twenty fields paging ten provinces. The three lists are now built; the twenty are deprecated toward them. *(b)* The three event-discovery pairs were already ruled by D-19 — §4 names the cursor form, so it takes the bare name and the offset half is deprecated. *(c)* The seven pairs no spec names are deprecated toward `discoverEvents(filter, pagination)`, the schema's own PRIMARY PUBLIC QUERY, which already answers all fourteen as filters. **Deprecated, not deleted**: ET-PLT-010 gates removal on observed usage and four have live clients |
| **O-2** | Which operation is `myPermissions` (ET-PLT-013 and ET-ORG-003 both declare it) | The whole permission surface; ET-PLT-013 cannot be verified | **Ruled 2026-09-01.** ET-PLT-013 → `currentUserPermissions`: the only caller-scoped permission query with a resolver, no argument, read from the JWT's realm roles. ET-ORG-003 is a *different operation* → `myEffectivePermissions(organizationId, eventId)`, declared in the SDL with no resolver, so that row stays contradicted until built. `currentUserPermissions` is not a substitute there and `ImplicitSubjectOperationTest` now refuses the mapping: it ignores `organizationId`, answering step one of D-10's five-step order in place of all five |
| **O-3** | Which operation is `ticketTiers` (ET-CAT-002) | ET-CAT-002's §4 | **Ruled 2026-09-01.** Both stand. §4's `ticketTiers(eventId) PUBLIC # bounded <= 50` is `availableTicketTiers`; `eventTicketTiers(eventId, includeHidden) ORGANIZER` is added to §4 as its own row — hidden tiers are the organizer's working set, not the public list |
| **O-4** | `escrowTransactions` has three filtered variants and no unfiltered list (ET-FIN-001) | ET-FIN-001's §4 | **Ruled 2026-09-01 — and it was not a rename.** §4 asks for `escrowTransactions(escrowAccountId, page)` at **ORGANIZER**; it shipped as `escrowTransactionsByAccount` under `hasRole('ADMIN')`. Taking the §4 name meant taking the §4 audience, and widening a client-supplied-id query from ADMIN to ORGANIZER without a filter would have published every organization's complete money history — sales, refunds, payouts, with amounts — to any organizer holding an account id. The account is now located through `TenantGuard` first. `EscrowTransactionScopeTest`, 4 cases on a replica set |
| **O-5** | `referenceData` and `chartOfAccounts` each collide with a bare unsuffixed field | The last 2 of the 17 lone pagination variants | **Ruled 2026-09-01, both from §4.** `referenceDataOffsetPagination` → **`referenceDataAll`**, which is ET-PLT-014 §4's own name for it — resolving the collision and the naming in one edit, since the public `referenceData` dropdown is a different operation and stays. `chartOfAccountsOffsetPagination` is deprecated: ET-FIN-001 §4 declares the chart a bounded FINANCE list and names no paged form, because a chart of accounts is a fixed set of codes |
| **O-6** | `eventApprovalQueue` (ET-ADM-001 §4) vs `pendingApprovalEvents` (ET-CAT-001 §4, and shipped) | One duplicated §4 row across two specs | **Ruled 2026-09-01.** One operation, two names. ET-ADM-001's row is now `pendingApprovalEvents(page)`, matching ET-CAT-001 §4 and what catalog ships |
| **O-7** | `PaymentAttempt` carries no link to `PaymentIntent` (ET-PAY-001) | `paymentAttempts(intentId)`, classified `absent` rather than `contradicted` for this reason | **Ruled 2026-09-01 — §4 named the wrong end, and I had classified it wrong twice.** `PaymentAttempt` really has no `intentId` and could not easily acquire one (attempts are created from a `TicketReservation`, with no intent in hand). But **`PaymentIntent.reservationId` is `@Indexed(unique = true)`** — intent and reservation are 1:1 and the database enforces it, so `reservationId` identifies the intent exactly as an `intentId` would. §4 now names `paymentAttemptsByReservation(reservationId)`, which ships and matches on audience and return type. Adding the field would have denormalised a key the index already guarantees equivalent |
| **O-8** | Whether the tenant filter replaces or joins the remote permission check | The 105 ratcheted read paths, the largest remaining block of [F-001](FINDINGS.md#f-001--organization-scoped-data-has-no-tenant-boundary) work | **Ruled 2026-09-01 — see [D-20](#decisions-taken-while-writing-this-corpus).** Both, always. Implemented as `EventWriteGuard`, through which all seven catalog event mutations now pass; `TenantGuard.locateAndPermit` takes both locks as required arguments so neither can be omitted, and two tests keep them honest — `EventWriteGuardLintTest` (the code is there, comments stripped so a javadoc cannot satisfy it) and `EventWriteBothLocksTest` (each lock refuses with the other wide open) |
| **O-9** | Whether `FRONTEND_GRAPHQL_CONTRACT.md` keeps its "generated" header until a generator exists | Nothing; it is a truthfulness question | **Closed 2026-09-01 — the generator was written instead.** `FrontendContractLintTest` derives the document from the three SDLs and fails the build on drift. It also found the document was wrong before it drifted: 51 of its 557 "operations" were object fields, not root fields |
| **O-10** | Whether identity's seven GraphQL authentication operations are vestigial or broken | `login`, `register`, `refreshToken`, `validateToken`, `requestPhoneOtp`, `verifyPhoneOtp`, `resetPassword` — every one with a working resolver, none reachable signed-out | **Ruled 2026-09-01 — vestigial, and deprecated rather than deleted.** No frontend document calls any of the seven; `login` and `validateToken` matches turn out to be Keycloak client and local `TokenService` methods. ET-PLT-010 is explicit that GraphQL *deprecates before it removes* and that removal is gated by observed usage rather than elapsed time, so all eight (`socialAuth` included) now carry `@deprecated` naming F-008. Removal when GraphOS confirms no usage |

Six further §4 names have **exactly one** shipped candidate and are spec edits rather than rulings —
`createReferenceRow → createReferenceData` and its two siblings in ET-PLT-014, and
`initiateTransfer` / `cancelTransfer` / `transferByToken` → the `OwnershipTransfer` forms in
ET-TKT-004.

---

## Build order

Eight waves. Each wave is buildable once the previous is `implemented`; within a wave,
specs are largely independent and can fan out.

### Wave 0 · Platform foundation

The runtime, the contracts every later spec depends on, and the test harness. Nothing
below is optional and nothing after it is safe to sequence first.

| ID | Title | Conformance |
|---|---|---|
| [ET-PLT-012](_platform/012-build-topology/) | Build topology — the parent POM, the BOM set, the reactor, the enforcer | PDI §1 |
| [ET-PLT-001](_platform/001-runtime-baseline/) | Runtime baseline — reactive contract, `Clock`, module boundaries, service topology | PDI §1 |
| [ET-PLT-002](_platform/002-persistence-baseline/) | Persistence baseline — replica set, collection and index registry, money and time types | PDI §1, §4 |
| [ET-PLT-003](_platform/003-event-contract/) | Event contract — two tiers, envelope, outbox, topics, idempotent consumers, DLQ | PDI §3, §8 |
| [ET-PLT-004](_platform/004-federation-contract/) | Federation contract — ownership, keys, stubs, `@tag` contracts, composition gate | — |
| [ET-PLT-005](_platform/005-error-contract/) | Error contract — the closed code registry, typed handlers, retryability | — |
| [ET-PLT-006](_platform/006-test-harness/) | Five-layer test harness — Testcontainers, WireMock, frozen `Clock` | PDI §9 |
| [ET-PLT-015](_platform/015-durable-execution/) | Durable execution — Temporal workflows, task queues, determinism and the reactive boundary |

### Wave 1 · Identity and access

| ID | Title |
|---|---|
| [ET-PLT-007](_platform/007-security-and-authorization/) | Keycloak realm, roles, `@auth`, internal scopes, idempotency keys, tenant scoping |
| [ET-PLT-013](_platform/013-permission-engine/) | The permission engine — flat `module:action` catalogue, role→permission mapping, evaluation |
| [ET-IDN-001](identity/001-phone-otp-identity/) | Contact-OTP passwordless identity — challenges, proofs, login handles and the Keycloak contact authenticator |
| [ET-IDN-002](identity/002-keycloak-user-sync/) | Keycloak ↔ MongoDB user synchronisation, drift detection and recovery |
| [ET-IDN-003](identity/003-token-revocation/) | Token revocation — `jti`/`sid`/`sub`, the fail-closed check, platform-wide propagation |
| [ET-IDN-004](identity/004-accounts-and-contacts/) | Accounts and contacts — identity-first creation, adoption, merge, contact change, repair |
| [ET-ORG-001](organization/001-organizer-onboarding/) | Organizer application — **nine states**, documents, staged access, approval |
| [ET-ORG-002](organization/002-teams-and-invitations/) | Organization members, invitations, ownership transfer |
| [ET-ORG-003](organization/003-permission-resolution/) | The three-tier permission resolver and event access grants |

### Wave 2 · The catalogue

| ID | Title |
|---|---|
| [ET-CAT-001](catalog/001-event-lifecycle/) | Event lifecycle — the state machine, approval, publish, reschedule, cancel |
| [ET-CAT-002](catalog/002-ticket-tiers-and-inventory/) | Ticket tiers, capacity, sales windows, the authoritative inventory count |
| [ET-PLT-014](_platform/014-reference-data-engine/) | The reference data engine — enum-derived, administrator-owned lookups and statuses |
| [ET-CAT-003](catalog/003-locations-and-reference-data/) | Provinces, cities, venues, categories, discovery and search |

### Wave 3 · The purchase loop

| ID | Title |
|---|---|
| [ET-TKT-001](ticketing/001-reservation-and-hold/) | Reservation, atomic hold, ten-minute expiry, the purchase workflow |
| [ET-PAY-001](payment/001-payment-intents-and-providers/) | Payment intents, the provider port, the PawaPay adapter, idempotency |
| [ET-PAY-002](payment/002-webhooks-and-settlement/) | Webhook signature verification, replay defence, provider reconciliation |
| [ET-TKT-002](ticketing/002-ticket-issuance-and-qr/) | Ticket issuance, QR signing, delivery and re-issue |
| [ET-FIN-001](finance/001-escrow-and-ledger/) | Per-event escrow accounts, chart of accounts, double-entry journal |

### Wave 4 · Money out

| ID | Title |
|---|---|
| [ET-FIN-002](finance/002-commission/) | Two-stage commission, rate resolution, recognition at event completion |
| [ET-FIN-003](finance/003-payouts-and-settlement/) | Payout eligibility, request lifecycle, bank accounts, the payout workflow |
| [ET-FIN-004](finance/004-refunds-and-chargebacks/) | Refund policy and fees, event-cancellation refunds, chargeback handling |
| [ET-FIN-005](finance/005-reconciliation/) | Provider reconciliation, ledger-to-balance proof, financial close |

### Wave 5 · At the venue, and after

| ID | Title |
|---|---|
| [ET-TKT-003](ticketing/003-validation-and-checkin/) | QR validation, offline scanning, duplicate-scan defence, check-in reporting |
| [ET-TKT-004](ticketing/004-transfer-and-resale/) | Ticket transfer between users, and controlled resale |
| [ET-NTF-001](notification/001-notification-transport/) | Channels, templates, devices, preferences, delivery outcomes |
| [ET-NTF-002](notification/002-lifecycle-triggers/) | Which fact produces which message, to whom, on which channel |

### Wave 6 · Operations

| ID | Title |
|---|---|
| [ET-ADM-001](admin/001-approvals-workbench/) | Organizer, event and document approval queues, SLA and escalation |
| [ET-ADM-002](admin/002-platform-configuration/) | Market configuration, commission defaults, feature flags, versioned config |
| [ET-ADM-003](admin/003-transaction-recovery/) | Stuck transactions, payout and escrow lifecycle operations, bulk retry |
| [ET-ADM-004](admin/004-analytics-and-statistics/) | Dashboard aggregations across all three services, and the polling contract |
| [ET-ADM-005](admin/005-observability-and-health/) | Metrics, tracing, correlation IDs, SLOs, system health and alerting |

### Wave 7 · Scale and compliance

| ID | Title |
|---|---|
| [ET-PLT-008](_platform/008-data-protection/) | PII inventory, GDPR erasure, the 30-day grace period, anonymised retention |
| [ET-PLT-009](_platform/009-audit-trail/) | The immutable audit log, what must be recorded, and who may read it |
| [ET-PLT-010](_platform/010-schema-evolution/) | Event and GraphQL schema versioning, upcasting, deprecation windows |
| [ET-PLT-011](_platform/011-rate-limiting-and-abuse/) | Rate limits, on-sale queueing, bot defence, OTP abuse control |

**42 specs, all authored and `approved`.** Every one carries the six sections, 7–8 EARS requirements with
acceptance boxes, a §4 model that names every collection, index, event, operation and
code, and a §5 task list with `depends` and `parallel-safe` on each task.

The dependency graph is a DAG — 180 edges, no cycles, and no spec is blocked by one in a
later wave, so the wave order below is executable as written.

---

## Conformance coverage

Two documents in `docs/` are treated as external authorities this corpus must satisfy in
full. Each stop is mapped to the spec that covers it.

### `ARCHITECTURE_REDESIGN_V3_COMPLETE.md` — the financial model

| § | Topic | Spec |
|---|---|---|
| 2 | Commission structure and profit model | ET-FIN-002 |
| 3 | Account types and fund flow | ET-FIN-001 |
| 4 | Complete user journeys | ET-TKT-001, ET-ORG-001 |
| 5 | Event lifecycle state machine | ET-CAT-001 |
| 7 | MongoDB collections per service | ET-PLT-002 §4 registry |
| 8 | Payment integration and consistency | ET-PAY-001, ET-PAY-002 |
| 9 | Rescheduling and cancellation | ET-CAT-001, ET-FIN-004 |
| 10 | Payout and settlement rules | ET-FIN-003 |
| 11 | Transaction tracking and terminology | ET-FIN-001, ET-FIN-005 |
| 12 | Settlement process | ET-FIN-003, ET-FIN-005 |
| 13 | Escrow vs non-escrow account classification | ET-FIN-001 |
| 14 | Refund processing fees | ET-FIN-004 |

### `PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md` — transactional integrity

| Phase | Topic | Spec |
|---|---|---|
| 1 | MongoDB replica set and transactions | ET-PLT-002 |
| 2 | Transaction architecture, reservation model | ET-TKT-001 |
| 3 | Atomic outbox pattern | ET-PLT-003 |
| 4 | Optimistic locking and concurrency | ET-PLT-002, ET-CAT-002 |
| 5 | Payment idempotency | ET-PAY-001, ET-PLT-007 |
| 6 | Webhook hardening | ET-PAY-002 |
| 7 | Saga state machine | ET-TKT-001, ET-FIN-003 |
| 8 | Dead-letter queue and recovery | ET-PLT-003, ET-ADM-003 |
| 9 | Monitoring and alerting | ET-ADM-005 |

### `USER_STORIES.md` v3.0 — roles and hierarchy

| Part | Topic | Spec |
|---|---|---|
| I §1–3 | Platform, organization and event role hierarchies | ET-ORG-003 |
| I §4 | Organizer onboarding stages | ET-ORG-001 |
| I §5 | Team management and invitations | ET-ORG-002 |
| I §6 | Keycloak integration architecture | ET-IDN-002 |
| II §7–12 | CRUD operations and business logic | ET-ORG-001, ET-ORG-002 |
| III §13–19 | End-user stories by role | mapped per spec in each §1 |
| IV §20 | Saga orchestration | ET-ORG-001, ET-TKT-001, ET-FIN-003 |
| IV §21 | State machines | ET-ORG-001, ET-ORG-002, ET-CAT-001 |
| IV §22 | Permission resolution algorithm | ET-ORG-003 |
| V §23–25 | Collections, documents, indexes | ET-PLT-002 §4 |

---

## Cross-cutting properties

Properties no single spec owns, asserted across several. Each is listed here so it cannot
fall between specs.

| Property | Asserted by |
|---|---|
| No blocking call ever runs on an event-loop thread | ET-PLT-001, and lint |
| Every timestamp comes from the injected `Clock` | ET-PLT-001, and lint |
| No business document lives in PostgreSQL or Redis | ET-PLT-002, and lint |
| An event can never be sold beyond its capacity | ET-CAT-002, ET-TKT-001 |
| No message is published to the bus inside a transaction | ET-PLT-003, and lint |
| Every cross-service consumer is idempotent on `eventId` | ET-PLT-003 |
| No balance is written except as a double-entry pair | ET-FIN-001, and lint |
| The ledger and every cached balance reconcile | ET-FIN-005 |
| A refused operation persists nothing | ET-PLT-006 — required on every refusal test |
| Every money-moving mutation is idempotent under retry | ET-PLT-007, ET-PAY-001 |
| Permission is resolved in exactly one implementation | ET-ORG-003, and lint |
| No tenant's data is reachable through another tenant's query | ET-PLT-007, ET-ORG-003 |
| Every admin-only schema field is `@tag`ged | ET-PLT-004, and composition |
| Every domain refusal has a registry code and a typed handler | ET-PLT-005, and lint |
| No personal data leaves the platform in an event payload | ET-PLT-008 |
