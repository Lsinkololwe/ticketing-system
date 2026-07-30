# ET-NTF-002 · Lifecycle triggers — which fact produces which message

> **Conformance** · V3 §4 user journeys · V3 §9 rescheduling and cancellation notifications

## 1. Capability

[ET-NTF-001](../001-notification-transport/) built a pipe. This spec decides what goes into
it, when, and to whom — and it exists as a separate spec for one reason: that decision
otherwise gets made twelve times, once per feature, by twelve people, and nobody can answer
*why did this buyer receive four messages about one purchase*.

It declares the **trigger registry**: one row per (fact, recipient) pair, naming the event
that fires it, the template, the category and the audience. Twenty-eight rows covering the
whole platform, in one table, reviewable in one sitting. A capability that wants a new
message adds a row here rather than calling the notification service directly.

It declares the two rules that keep the volume sane. **Coalescing**: one purchase produces
one message, not one per ticket and one per payment. **Bounds**: no actor receives more than
a stated number of platform messages per hour, and a mass send to an event's ticket holders
is a job with a rate, not a loop.

And it declares the scheduled triggers — event reminders — which are the only messages the
platform sends that no fact caused, and which therefore need their own state so that a
rescheduled event does not remind everybody about the old date.

## 2. Design decisions

**One registry, one row per (fact, recipient).** A cancellation produces a message to the
buyer and a different message to the organizer; those are two rows, not one row with a
branch. The registry is the artefact somebody reviews before launch to answer *what does
this platform actually send*.

**No capability calls the notification service directly.** Every message originates from a
row in this registry, driven by an event that capability already publishes. That keeps the
volume answerable and means adding a message is a reviewable diff in one file rather than a
line buried in a service.

**One purchase, one message.** A confirmed purchase publishes `booking.TicketPurchased` and
`booking.PaymentCompleted`, and issues N tickets. The buyer gets **one** message covering
the whole order, with the ticket count and a link. Coalescing is on the reservation, in a
short window, because the events arrive within milliseconds of each other but not
simultaneously.

**Mass sends are jobs with a rate, not loops.** Cancelling an event with ten thousand
holders is a batched job at a configured rate, resumable, with progress. A loop publishing
ten thousand notification events saturates the queue, the providers rate-limit, and the
retry sweep then re-attempts thousands of throttled sends.

**Every actor has an hourly bound, and transactional messages are exempt from suppression
but not from ordering.** A user cannot receive more than `notification.per-user-hourly-cap`
optional messages an hour. Transactional messages are never suppressed by the cap — but
when the cap is exceeded, optional messages are dropped rather than queued, because a
reminder delivered three hours late is noise.

**Reminders are scheduled state, not a cron scanning every event.** Each ticket gets
reminder rows at 24 hours and 1 hour before `startsAt`. A reschedule moves them; a
cancellation deletes them; a refund deletes them. Scanning every event on every tick
re-derives the same answer thousands of times and gets it wrong the moment a date changes.

**The organizer is a recipient too, and their messages are digested.** An organizer with a
selling event does not want a message per sale. Their sales notifications are a daily
digest; only exceptional facts — a payout, a chargeback, a cancellation — are immediate.

**Rejected alternatives**

- *Each capability calling the notification service where it likes.* Nobody can answer what the platform sends.
- *One message per ticket in a multi-ticket order.* A four-ticket purchase sends four confirmations and a payment receipt.
- *A loop over ticket holders for a mass send.* Saturates the queue and triggers provider rate limits, which the retry sweep then amplifies.
- *A cron scanning every event for reminders.* Re-derives the same answer thousands of times and is wrong the moment a date moves.
- *Per-sale notifications to organizers.* A selling event becomes a denial of service against its own organizer.
- *Queueing optional messages past the cap.* A reminder delivered three hours late is noise.

## 3. Requirements

### ET-NTF-002-R1 · Every message originates from a registry row

THE SYSTEM SHALL send only the messages in the §4 trigger registry, and no capability SHALL
call the notification service directly.

**Acceptance**
- [ ] Every row names the triggering event, the template key, the category and the recipient
- [ ] No service outside the notification module calls `NotificationService.send`
- [ ] Every template key in the registry exists in [ET-NTF-001](../001-notification-transport/) §4
- [ ] Every triggering event exists in [ET-PLT-003](../../_platform/003-event-contract/) §4 or is a declared module event
- [ ] Adding a message changes this spec's §4 in the same commit as the trigger
- [ ] A test asserts the set of templates actually sent equals the registry

### ET-NTF-002-R2 · One purchase produces one message

WHEN a purchase confirms, THE SYSTEM SHALL send the buyer exactly one message covering the
whole order.

**Acceptance**
- [ ] A four-ticket purchase produces one buyer notification, not four
- [ ] `booking.TicketPurchased` and `booking.PaymentCompleted` for one reservation coalesce into one message
- [ ] Coalescing keys on `reservationId` within `notification.coalesce-window` (PT30S)
- [ ] The message carries the ticket count, the event, the total paid and a link to all tickets
- [ ] A second delivery of either event produces no second message ([ET-NTF-001](../001-notification-transport/) R7)
- [ ] A test confirms a 10-ticket purchase and asserts exactly one notification

### ET-NTF-002-R3 · Mass sends are rated, batched and resumable

WHEN an event change affects every ticket holder, THE SYSTEM SHALL send as a rated job
rather than a loop.

**Acceptance**
- [ ] `catalog.EventCancelled` and `catalog.EventRescheduled` trigger a batched job over holders
- [ ] The job sends at `notification.mass-send-rate` (100/second) and is resumable from its last position
- [ ] It is idempotent per recipient — a restart re-sends to nobody
- [ ] Progress is queryable by the organizer and by an operator
- [ ] A test cancels an event with 10,000 holders, kills the job midway, resumes, and asserts exactly 10,000 messages
- [ ] The job's queue depth and rate are metrics
- [ ] Mass sends never bypass [ET-NTF-001](../001-notification-transport/)'s per-message dedup

### ET-NTF-002-R4 · Optional messages are capped per actor per hour

WHILE an actor has received the hourly cap of optional messages, THE SYSTEM SHALL drop
further optional messages and SHALL continue sending transactional ones.

**Acceptance**
- [ ] The cap is `notification.per-user-hourly-cap` (10) optional messages
- [ ] A dropped message is recorded as `SUPPRESSED` with reason `RATE_CAPPED`, not silently discarded
- [ ] Transactional messages are never dropped by the cap
- [ ] The counter is a Redis key with a rolling hour and is not a business record
- [ ] A test sends 15 optional and 5 transactional messages in an hour and asserts 10 and 5 delivered
- [ ] The suppression rate is a metric

### ET-NTF-002-R5 · Reminders are scheduled rows that follow the event

THE SYSTEM SHALL schedule reminders per ticket and SHALL update them when the event changes.

**Acceptance**
- [ ] Issuing a ticket creates reminder rows at `startsAt − 24h` and `startsAt − 1h`
- [ ] `catalog.EventRescheduled` moves every reminder for that event to the new times
- [ ] `catalog.EventCancelled` deletes every reminder for that event
- [ ] A refunded or transferred-away ticket deletes the sender's reminders; a claimed transfer creates the recipient's
- [ ] A reminder whose scheduled time is already past when created is dropped, not sent late
- [ ] The dispatch sweep claims due reminders under a lock and is idempotent
- [ ] A test reschedules an event forward and backward and asserts the reminders track it

### ET-NTF-002-R6 · Organizers receive digests, not a message per sale

THE SYSTEM SHALL digest routine organizer notifications and SHALL send exceptional ones
immediately.

**Acceptance**
- [ ] Sales activity is a daily digest at `notification.digest-hour` in the organization's timezone
- [ ] The digest carries tickets sold, revenue, remaining capacity and the next event
- [ ] A payout, a chargeback, a cancellation, an approval decision and a team change are immediate
- [ ] An organizer with no activity receives no digest — an empty digest is noise
- [ ] The digest is one message per organization, addressed to the owner, with admins opting in
- [ ] A test simulates 500 sales in a day and asserts one digest

### ET-NTF-002-R7 · Every recipient is resolved from an authoritative source

WHEN a message is triggered, THE SYSTEM SHALL resolve its recipient at send time, not from
the event payload.

**Acceptance**
- [ ] No trigger reads a phone number, email address or name from an event payload ([ET-PLT-003](../../_platform/003-event-contract/) R3)
- [ ] The recipient is resolved from `identity_users` by the id the event carries
- [ ] A ticket message goes to the ticket's **current** `ownerId`, resolved at send time — so a transfer mid-flight reaches the right person
- [ ] An organizer message goes to the organization's current owner, resolved at send time
- [ ] A recipient who no longer exists causes the message to be recorded `FAILED`, not to throw
- [ ] A test transfers a ticket between trigger and send and asserts the new owner receives it

## 4. Model

### Trigger registry

**Buyer**

| # | Triggering fact | Template | Category | Recipient |
|---|---|---|---|---|
| 1 | `booking.TicketPurchased` **+** `booking.PaymentCompleted`, coalesced | `ticket.issued` | `TICKET` | buyer |
| 2 | `booking.PaymentFailed` | `payment.failed` | `PAYMENT` | buyer |
| 3 | `booking.TicketTransferred` | `ticket.transferred.sender` | `TICKET` | sender |
| 4 | `booking.TicketTransferred` | `ticket.transferred.recipient` | `TICKET` | recipient |
| 5 | `TransferInitiatedEvent` | `ticket.transfer.claim-link` | `TICKET` | recipient |
| 6 | `TransferExpiredEvent` | `ticket.transfer.expired` | `TICKET` | sender |
| 7 | `catalog.EventRescheduled` | `event.rescheduled` | `EVENT_CHANGE` | **every holder** — mass |
| 8 | `catalog.EventCancelled` | `event.cancelled` | `EVENT_CHANGE` | **every holder** — mass |
| 9 | refund approved | `refund.approved` | `REFUND` | buyer |
| 10 | `booking.RefundCompleted` | `refund.completed` | `REFUND` | buyer |
| 11 | refund rejected | `refund.rejected` | `REFUND` | buyer |
| 12 | reminder, 24 h | `event.reminder.24h` | `EVENT_REMINDER` | holder |
| 13 | reminder, 1 h | `event.reminder.1h` | `EVENT_REMINDER` | holder |

**Organizer and team**

| # | Triggering fact | Template | Category | Recipient |
|---|---|---|---|---|
| 14 | `identity.OrganizationApproved` | `organization.approved` | `ORGANIZATION` | owner |
| 15 | organization rejected | `organization.rejected` | `ORGANIZATION` | applicant |
| 16 | changes requested | `organization.changes-requested` | `ORGANIZATION` | applicant |
| 17 | `identity.OrganizationSuspended` | `organization.suspended` | `ORGANIZATION` | owner |
| 18 | event approved | `event.approved` | `ORGANIZATION` | submitter |
| 19 | event rejected | `event.rejected` | `ORGANIZATION` | submitter |
| 20 | `InvitationCreatedEvent` | `team.invitation` | `TEAM_INVITE` | invitee |
| 21 | `TeamMemberJoinedEvent` | `team.accepted` | `TEAM_INVITE` | inviter |
| 22 | `identity.MemberRoleChanged` | `team.role-changed` | `TEAM_INVITE` | the member |
| 23 | `identity.MemberRemoved` | `team.removed` | `TEAM_INVITE` | the member |
| 24 | `OwnershipTransferredEvent` | `ownership.transfer-requested` | `ORGANIZATION` | nominee |
| 25 | ownership confirmed | `ownership.confirmed` | `ORGANIZATION` | both parties |
| 26 | `PayoutApprovedEvent` | `payout.approved` | `PAYOUT` | owner |
| 27 | `booking.PayoutCompleted` | `payout.completed` | `PAYOUT` | owner |
| 28 | `PayoutFailedEvent` | `payout.failed` | `PAYOUT` | owner |
| 29 | `ChargebackReceivedEvent` | `chargeback.received` | `PAYOUT` | owner |
| 30 | daily digest | `organizer.daily-digest` | `MARKETING` | owner + opted-in admins |

**Operator**

| # | Triggering fact | Template | Category | Recipient |
|---|---|---|---|---|
| 31 | `OrganizationSubmittedEvent` | `admin.application-pending` | `ORGANIZATION` | the approvals queue |
| 32 | `EventSubmittedEvent` | `admin.event-pending` | `ORGANIZATION` | the approvals queue |

**Thirty-two rows.** No other message exists. Rows 7 and 8 are mass sends (R3); rows 12, 13
and 30 are scheduled (R5, R6).

`otp.login` is deliberately absent — it is requested directly by
[ET-IDN-001](../../identity/001-phone-otp-identity/)'s internal endpoint rather than
triggered by a domain fact, and it is the one exception to R1.

### Coalescing

| Key | Window | Rows |
|---|---|---|
| `reservationId` | `PT30S` | 1 |

The purchase message waits 30 seconds for both events, then renders once from whatever it
has — never longer, because a buyer waiting on a confirmation is a buyer about to pay
again.

### Reminders

`identity_event_reminders`

| Field | Notes |
|---|---|
| `_id`, `ticketId`, `userId`, `eventId` | |
| `reminderType` | `T_MINUS_24H`, `T_MINUS_1H` |
| `scheduledFor` | `Instant` |
| `status` | `SCHEDULED`, `SENT`, `CANCELLED`, `SKIPPED` |
| `notificationId` | on send |
| `createdAt`, `sentAt` | |

| Fact | Effect on reminders |
|---|---|
| ticket issued | create both rows |
| `catalog.EventRescheduled` | move both to the new times |
| `catalog.EventCancelled` | `CANCELLED` |
| refund completed | `CANCELLED` |
| transfer claimed | `CANCELLED` for the sender, created for the recipient |
| scheduled time already past at creation | `SKIPPED` |

Dispatch is a sweep under `lock:sweep:reminder-dispatch` every minute, claiming
`SCHEDULED` rows whose `scheduledFor` has passed.

### Mass send

`identity_mass_sends`

| Field | Notes |
|---|---|
| `_id`, `eventId`, `templateKey`, `category` | |
| `totalRecipients`, `sentCount`, `failedCount` | |
| `lastProcessedId` | the resume cursor |
| `status` | `RUNNING`, `COMPLETED`, `FAILED`, `PAUSED` |
| `rate` | messages per second |
| `startedAt`, `completedAt` | |

Resumption reads `lastProcessedId` and continues; per-recipient idempotency is
[ET-NTF-001](../001-notification-transport/) R7's `deduplicationKey`, which for a mass send
is `{massSendId}:{userId}`.

### Deduplication keys

| Row | `deduplicationKey` |
|---|---|
| 1 | `purchase:{reservationId}` |
| 7, 8 | `{massSendId}:{userId}` |
| 12, 13 | `reminder:{ticketId}:{reminderType}` |
| 26–29 | `{templateKey}:{payoutRequestId}` |
| 30 | `digest:{organizationId}:{date}` |
| others | `{templateKey}:{primaryEntityId}` |

### Rate cap

| Key | TTL | Purpose |
|---|---|---|
| `notif:cap:{userId}` | rolling `PT1H` | optional-message counter (R4) |

Transactional messages neither increment nor consult it.

### GraphQL

Subgraph `identity`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `massSend(id)` | query | `ORGANIZER` | `MassSend` |
| `massSendsForEvent(eventId)` | query | `ORGANIZER` | `[MassSend!]!` |
| `myUpcomingReminders` | query | `AUTHENTICATED` | `[EventReminder!]!` |
| `triggerRegistry` | query | `SUPER_ADMIN` | `[NotificationTrigger!]!` `@tag(name: "admin")` |
| `pauseMassSend(id)` | mutation | `ADMIN` | `MassSend!` `@tag(name: "admin")` |
| `resumeMassSend(id)` | mutation | `ADMIN` | `MassSend!` `@tag(name: "admin")` |

`triggerRegistry` returns the §4 table as data, so an operator can answer *what does this
platform send* without reading a spec.

### Sweeps

| Sweep | Lock | Interval | Purpose |
|---|---|---|---|
| reminder dispatch | `lock:sweep:reminder-dispatch` | `PT1M` | R5 |
| mass send | `lock:sweep:mass-send` | `PT10S` | R3, rated |
| digest | `lock:sweep:organizer-digest` | hourly, fires at the local digest hour | R6 |

### Configuration

| Property | Value |
|---|---|
| `notification.coalesce-window` | `PT30S` |
| `notification.mass-send-rate` | 100/second |
| `notification.mass-send-batch` | 500 |
| `notification.per-user-hourly-cap` | 10 optional |
| `notification.digest-hour` | 08:00, organization timezone |
| `notification.reminder.offsets` | `PT24H`, `PT1H` |

### Error codes

None introduced. Failures are recorded on the notification
([ET-NTF-001](../001-notification-transport/) R6), not returned to a caller — there is no
caller.

## 5. Tasks

- [ ] **T1 · The trigger registry as data, and the listeners that drive it**
  - requirements: R1
  - files: `backend/identity-service/.../notification/triggers/`
  - verify: the set of templates sent equals the registry; no service calls `send` directly
  - parallel-safe: no
  - depends: —

- [ ] **T2 · Purchase coalescing on `reservationId`**
  - requirements: R2
  - files: `backend/identity-service/.../notification/PurchaseNotificationCoalescer.java`
  - verify: a 10-ticket purchase yields one notification
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · The mass-send job: rated, batched, resumable**
  - requirements: R3
  - files: `backend/identity-service/.../service/impl/MassSendService.java`
  - verify: 10,000 holders, killed midway, resumed, exactly 10,000 messages
  - parallel-safe: no
  - depends: T1

- [ ] **T4 · The hourly cap and its suppression record**
  - requirements: R4
  - files: `backend/identity-service/.../service/impl/NotificationRateCap.java`
  - verify: 15 optional and 5 transactional yield 10 and 5
  - parallel-safe: yes
  - depends: T1

- [ ] **T5 · Reminder rows, their lifecycle consumers and the dispatch sweep**
  - requirements: R5
  - files: `backend/identity-service/.../service/impl/EventReminderService.java`
  - verify: reminders track a reschedule in both directions; a past-due creation skips
  - parallel-safe: yes
  - depends: T1

- [ ] **T6 · The organizer digest and its empty-digest suppression**
  - requirements: R6
  - files: `backend/identity-service/.../scheduler/OrganizerDigestSweeper.java`
  - verify: 500 sales yield one digest; no activity yields none
  - parallel-safe: yes
  - depends: T1

- [ ] **T7 · Recipient resolution at send time**
  - requirements: R7
  - files: `backend/identity-service/.../notification/RecipientResolver.java`
  - verify: a transfer between trigger and send reaches the new owner; no payload PII is read
  - parallel-safe: yes
  - depends: T1

- [ ] **T8 · The subgraph half and the `triggerRegistry` query**
  - requirements: R1
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL across identity's specs
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| Channels, templates, devices, delivery and retry | [ET-NTF-001](../001-notification-transport/) |
| The OTP, which bypasses this registry deliberately | [ET-IDN-001](../../identity/001-phone-otp-identity/) |
| The facts that fire these triggers | the spec that publishes each event |
| Ticket delivery content | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) |
| The approvals queue these messages feed | [ET-ADM-001](../../admin/001-approvals-workbench/) |
| Marketing consent capture | [ET-PLT-008](../../_platform/008-data-protection/) |
| Delivery dashboards and alerting | [ET-ADM-005](../../admin/005-observability-and-health/) |

Deliberately never in scope: **capabilities calling the notification service directly**
(nobody can then answer what the platform sends), **a message per ticket in a multi-ticket
order**, and **a cron scanning every event for reminders** (wrong the moment a date moves).
