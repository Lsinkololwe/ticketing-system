# ET-NTF-001 · Notification transport — channels, templates, devices, delivery

> **Conformance** · V3 §4 user journeys · US Part I §6 Keycloak integration

## 1. Capability

The platform speaks to people constantly: a login code, a ticket, a receipt, a cancellation,
a payout. Each of those messages has to reach somebody on a channel they actually check, in
a language they read, without arriving four times, and without the sending of it ever being
able to break the thing that caused it.

This spec builds the pipe. It declares the four channels — WhatsApp, SMS, push and email —
and the provider port behind each, so a provider change is an adapter. It declares
templates, which is how a message stops being a string concatenated in a service and starts
being a reviewable, translatable artefact. It declares device registration for push, user
preferences, and the fallback chain that tries the next channel when one fails.

The rule that governs it is that **notification failure is never business failure**. A
ticket exists whether or not its WhatsApp message was delivered. A payout settles whether or
not the organizer's email bounced. Every send is asynchronous, retried, and recorded — and
nothing upstream ever waits on it or fails because of it.

The OTP is the one message with a harder requirement: it carries a login credential, it is
time-critical, and if it does not arrive the user cannot get in at all. It is declared here
as a distinct class with its own urgency and its own no-logging rule
([ET-IDN-001](../../identity/001-phone-otp-identity/) R4).

## 2. Design decisions

**Four channels, each behind a port, each with one adapter.** `WHATSAPP`, `SMS`, `PUSH`,
`EMAIL`. `NotificationChannelPort` declares `send` and `status`; each adapter translates its
provider's vocabulary at the boundary, exactly as
[ET-PAY-001](../../payment/001-payment-intents-and-providers/) does. No service names a
provider.

**Every message is a template, rendered with parameters — never a concatenated string.**
Templates live in a registry with a key, a channel, a locale and a body. That is what makes
them reviewable by somebody who is not a developer, translatable without a deploy, and
consistent across the four places a ticket confirmation is sent from.

**Preferences are per user per category, and transactional messages ignore them.** A user
may turn off marketing and event reminders. They may not turn off *your payment failed* or
*your event was cancelled* — those are transactional and are sent regardless. Conflating
the two produces either a spammable platform or a user who never learns their event was
cancelled.

**Sending is asynchronous and never blocks the caller.** A notification is requested by
publishing an event; the sender consumes it. Nothing in a purchase, a payout or a
cancellation path waits for a provider. This is the rule that keeps a WhatsApp outage from
stopping ticket sales.

**Delivery is attempted down a fallback chain, and the chain is per category.** A ticket
tries WhatsApp, then SMS, then email. An OTP tries WhatsApp, then SMS, and stops — email is
too slow for a five-minute code. A marketing message tries push and stops. The chain is
declared per category rather than per message, so a new message type inherits a considered
default.

**A failed send is retried with backoff and then recorded as failed — it never throws.**
[ET-PLT-003](../../_platform/003-event-contract/) §2's rule applies: a provider being down
is a business outcome. The listener records it, the retry sweep re-attempts, and the
dead-letter queue is reserved for messages that are actually malformed.

**Deduplication is on a caller-supplied key.** The same fact can reach the notification
layer twice — a redelivered bus message, a retried job. Every request carries a
`deduplicationKey`, and a second request with that key inside the window is dropped. This
is what stops a buyer receiving two identical ticket messages.

**The OTP is a distinct class with its own rules.** Highest priority, no email fallback, no
retry after expiry, and its content is never logged at any level in any environment
([ET-IDN-001](../../identity/001-phone-otp-identity/) R4).

**Rejected alternatives**

- *Strings built in services.* Unreviewable, untranslatable, and inconsistent between the four places one message is sent from.
- *Synchronous sending on the business path.* A WhatsApp outage stops ticket sales.
- *One global fallback chain.* Sends an OTP by email twenty minutes after it expired.
- *Honouring preferences for transactional messages.* A user opts out and never learns their event was cancelled.
- *Throwing on a provider failure.* Dead-letters a message whose only problem is that somebody else's server is down.
- *Deduplicating on message content.* Two genuinely different reminders for one event look identical.
- *A single provider per channel with no port.* The provider change is a rewrite.

## 3. Requirements

### ET-NTF-001-R1 · Four channels, each behind a port, no provider named outside its adapter

THE SYSTEM SHALL define a channel port and SHALL confine every provider's vocabulary to its
adapter.

**Acceptance**
- [ ] `NotificationChannel` declares exactly `WHATSAPP`, `SMS`, `PUSH`, `EMAIL`
- [ ] `NotificationChannelPort` declares `send(RenderedMessage)` and `status(providerReference)`, both returning platform types
- [ ] One adapter per channel; no service, resolver or listener names a provider, imports its types or knows its URLs
- [ ] Each adapter maps its provider's status and failure vocabulary in one table
- [ ] An unmapped provider value becomes `UNKNOWN` and increments a metric
- [ ] Every adapter call is bounded by a configured timeout and a circuit breaker
- [ ] A test double implementing the port requires no service change

### ET-NTF-001-R2 · Every message is a registered template

THE SYSTEM SHALL render every notification from a template in the §4 registry.

**Acceptance**
- [ ] No notification body is constructed by string concatenation in a service
- [ ] `identity_notification_templates` carries `templateKey`, `channel`, `locale`, `subject`, `body`, `category`, `version`
- [ ] `{templateKey, channel, locale}` is unique
- [ ] Rendering fails fast on a missing parameter rather than emitting an empty placeholder
- [ ] A missing locale falls back to `en`, and the fallback is counted as a metric
- [ ] Every template in §4 exists for every channel its category's chain uses
- [ ] Templates are seeded from a migration and are editable by `SUPER_ADMIN` without a deploy

### ET-NTF-001-R3 · Preferences apply to non-transactional categories only

WHILE a user has disabled a category, THE SYSTEM SHALL suppress its messages, and
transactional categories SHALL be sent regardless.

**Acceptance**
- [ ] `identity_notification_preferences` holds per user, per category, per channel booleans
- [ ] The §4 category table marks each category `TRANSACTIONAL` or `OPTIONAL`
- [ ] A suppressed `OPTIONAL` message is recorded as `SUPPRESSED`, not silently dropped
- [ ] A `TRANSACTIONAL` message ignores preferences entirely — asserted per category
- [ ] A user with no preferences receives everything; the default is opt-in for transactional and opt-in for reminders, opt-out for marketing
- [ ] Every `OPTIONAL` message carries an unsubscribe path appropriate to its channel

### ET-NTF-001-R4 · Sending never blocks or breaks the business path

THE SYSTEM SHALL send asynchronously and SHALL NOT allow a delivery failure to affect the
operation that requested it.

**Acceptance**
- [ ] Notification is requested by publishing a module event; no business method calls a channel adapter
- [ ] No `@Transactional` method calls the notification service
- [ ] A listener never rethrows a provider failure ([ET-PLT-003](../../_platform/003-event-contract/) R6)
- [ ] Every messaging provider stopped, a purchase, a payout and a cancellation all still complete — asserted by three tests
- [ ] No `StreamBridge.send` appears inside a `@Transactional` method, no module boundary uses a bare `@EventListener`, and no `@TransactionalEventListener(AFTER_COMMIT)` rethrows a delivery failure
- [ ] The notification queue depth is a metric and alerts

### ET-NTF-001-R5 · Delivery follows a per-category fallback chain

WHEN a channel fails, THE SYSTEM SHALL attempt the next channel in that category's chain,
and SHALL stop at its end.

**Acceptance**
- [ ] Each category declares its chain in §4
- [ ] `OTP` is `WHATSAPP → SMS` and stops — it never attempts email
- [ ] A channel that reports a terminal failure advances immediately; a transient one is retried before advancing
- [ ] Each attempt is recorded with its channel, provider reference, outcome and timestamp
- [ ] The notification is `DELIVERED` on the first success and `FAILED` when the chain is exhausted
- [ ] A user with no phone number skips the phone channels rather than failing them
- [ ] A test fails every channel in turn and asserts the chain order and the terminal state

### ET-NTF-001-R6 · A failed send retries, then rests as failed

IF a send fails transiently, THEN THE SYSTEM SHALL retry with backoff up to the limit, and
past it SHALL record a failure without throwing.

**Acceptance**
- [ ] Retries use exponential backoff up to `notification.max-attempts` (3) per channel
- [ ] A retry sweep under `lock:sweep:notification-retry` re-attempts eligible notifications
- [ ] Past the limit on every channel, the notification is `FAILED` with a reason
- [ ] A `FAILED` transactional notification is surfaced to an operator; a failed optional one is only counted
- [ ] No provider failure reaches the dead-letter queue
- [ ] The failure rate per channel is a metric and alerts

### ET-NTF-001-R7 · Duplicate requests are dropped on a supplied key

WHEN a notification is requested with a key already seen, THE SYSTEM SHALL drop it.

**Acceptance**
- [ ] Every request carries a `deduplicationKey` derived from the fact that caused it
- [ ] A second request with that key inside `notification.dedup-window` (PT24H) is dropped and counted
- [ ] The guard is a unique index on `deduplicationKey`, plus a Redis fast path
- [ ] A redelivered bus message produces exactly one notification, asserted per lifecycle trigger
- [ ] Deduplication never suppresses a genuinely distinct message — two reminders for one event carry different keys
- [ ] A test delivers the same trigger event three times and asserts one message

### ET-NTF-001-R8 · Push devices are registered, refreshed and pruned

THE SYSTEM SHALL maintain push device registrations and SHALL remove tokens the provider
reports as invalid.

**Acceptance**
- [ ] `identity_user_devices` carries `userId`, `deviceToken`, `platform`, `appVersion`, `lastSeenAt`, `active`
- [ ] `deviceToken` is unique — a token registered to a new user moves rather than duplicating
- [ ] A provider response indicating an invalid or unregistered token deactivates it immediately
- [ ] A device not seen for `notification.device.stale-after` (P90D) is deactivated
- [ ] A push to a user with no active device skips the channel rather than failing it
- [ ] `registerDevice` is idempotent on the token
- [ ] A test registers, invalidates and re-registers a token and asserts one active row

## 4. Model

### Documents

`identity_notifications`

| Field | Type | Notes |
|---|---|---|
| `_id`, `userId` | `String` | |
| `category` | `NotificationCategory` | see the table |
| `templateKey` | `String` | |
| `deduplicationKey` | `String` | **unique** |
| `parameters` | `Map<String,String>` | rendering inputs — **no PII beyond what the message needs** |
| `channelsAttempted` | `List<DeliveryAttempt>` | channel, provider reference, outcome, timestamp |
| `deliveredVia` | `NotificationChannel` | on success |
| `status` | `NotificationStatus` | `PENDING`, `SENDING`, `DELIVERED`, `FAILED`, `SUPPRESSED` |
| `attemptCount`, `nextAttemptAt` | `int`, `Instant` | |
| `readAt` | `Instant` | in-app |
| `createdAt`, `deliveredAt` | `Instant` | 180-day TTL on `createdAt` |

`identity_notification_templates`, `identity_notification_preferences`,
`identity_user_devices` as declared in
[ET-PLT-002](../../_platform/002-persistence-baseline/) §4.

### Categories, chains and preference behaviour

| Category | Kind | Fallback chain | Preference |
|---|---|---|---|
| `OTP` | transactional | `WHATSAPP → SMS` | ignored |
| `TICKET` | transactional | `WHATSAPP → SMS → EMAIL` | ignored |
| `PAYMENT` | transactional | `WHATSAPP → SMS → EMAIL` | ignored |
| `REFUND` | transactional | `WHATSAPP → SMS → EMAIL` | ignored |
| `EVENT_CHANGE` | transactional | `PUSH → WHATSAPP → SMS → EMAIL` | ignored |
| `PAYOUT` | transactional | `EMAIL → WHATSAPP` | ignored |
| `ORGANIZATION` | transactional | `EMAIL → WHATSAPP` | ignored |
| `TEAM_INVITE` | transactional | `EMAIL → WHATSAPP → SMS` | ignored |
| `EVENT_REMINDER` | optional | `PUSH → WHATSAPP` | honoured |
| `MARKETING` | optional | `PUSH → EMAIL` | honoured, **opt-in** |

`OTP` never reaches email: a five-minute code delivered by email twenty minutes later is
worse than not sending it.

`PAYOUT` and `ORGANIZATION` lead with email because they carry detail an organizer needs to
keep and refer back to.

### Template registry

| `templateKey` | Category | Introduced by |
|---|---|---|
| `otp.login` | `OTP` | [ET-IDN-001](../../identity/001-phone-otp-identity/) |
| `ticket.issued` | `TICKET` | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) |
| `ticket.transferred.sender` / `.recipient` | `TICKET` | [ET-TKT-004](../../ticketing/004-transfer-and-resale/) |
| `payment.completed` | `PAYMENT` | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| `payment.failed` | `PAYMENT` | [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| `refund.approved` / `.completed` / `.rejected` | `REFUND` | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| `event.rescheduled` / `.cancelled` | `EVENT_CHANGE` | [ET-CAT-001](../../catalog/001-event-lifecycle/) |
| `payout.approved` / `.completed` / `.failed` | `PAYOUT` | [ET-FIN-003](../../finance/003-payouts-and-settlement/) |
| `organization.approved` / `.rejected` / `.changes-requested` / `.suspended` | `ORGANIZATION` | [ET-ORG-001](../../organization/001-organizer-onboarding/) |
| `team.invitation` / `.accepted` / `.role-changed` / `.removed` | `TEAM_INVITE` | [ET-ORG-002](../../organization/002-teams-and-invitations/) |
| `ownership.transfer-requested` / `.confirmed` | `ORGANIZATION` | [ET-ORG-002](../../organization/002-teams-and-invitations/) |
| `event.reminder.24h` / `.1h` | `EVENT_REMINDER` | [ET-NTF-002](../002-lifecycle-triggers/) |

Each exists for every channel in its category's chain, in `en` at minimum.
[ET-NTF-002](../002-lifecycle-triggers/) owns which fact fires which key.

### The port

```java
public interface NotificationChannelPort {
    NotificationChannel channel();
    Mono<SendResult> send(RenderedMessage message);
    Mono<DeliveryStatus> status(String providerReference);
}
```

| Channel | Adapter | Provider |
|---|---|---|
| `WHATSAPP` | `WhatsAppAdapter` | WhatsApp Business API |
| `SMS` | `SmsAdapter` | Africa's Talking, with Twilio as an alternate adapter |
| `PUSH` | `PushAdapter` | FCM / APNs via Expo |
| `EMAIL` | `EmailAdapter` | SMTP |

### The send path

```
1  a business fact commits
2  an @TransactionalEventListener(AFTER_COMMIT) publishes NotificationRequestedEvent
       { userId, category, templateKey, parameters, deduplicationKey }
3  the notification service:
       dedup on deduplicationKey            → drop
       category OPTIONAL and disabled       → SUPPRESSED
       resolve the chain for the category
       resolve the user's locale
4  per channel in the chain:
       render the template
       adapter.send(...) with a timeout and a breaker
       success   → DELIVERED, stop
       transient → retry up to max-attempts, then advance
       terminal  → advance immediately
5  chain exhausted                          → FAILED, reason recorded
```

Step 2 runs after commit. Nothing in step 1 waits for anything after it.

### GraphQL

Subgraph `identity`.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `myNotifications(status, first, after)` | query | `AUTHENTICATED` | `NotificationConnection!` |
| `unreadNotificationCount` | query | `AUTHENTICATED` | `Int!` |
| `myNotificationPreferences` | query | `AUTHENTICATED` | `[NotificationPreference!]!` |
| `myDevices` | query | `AUTHENTICATED` | `[UserDevice!]!` |
| `notificationTemplates(channel, locale)` | query | `SUPER_ADMIN` | `[NotificationTemplate!]!` `@tag(name: "admin")` |
| `markNotificationRead(id)` | mutation | `AUTHENTICATED` | `Notification!` |
| `markAllNotificationsRead` | mutation | `AUTHENTICATED` | `Int!` |
| `updateNotificationPreferences(input)` | mutation | `AUTHENTICATED` | `[NotificationPreference!]!` |
| `registerDevice(input)` | mutation | `AUTHENTICATED` | `UserDevice!` |
| `deregisterDevice(token)` | mutation | `AUTHENTICATED` | `Boolean!` |
| `updateNotificationTemplate(input)` | mutation | `SUPER_ADMIN` | `NotificationTemplate!` `@tag(name: "admin")` |
| `resendNotification(id)` | mutation | `ADMIN` | `Notification!` `@tag(name: "admin")` |

`updateNotificationPreferences` accepts only `OPTIONAL` categories; an attempt to disable a
transactional one is refused rather than silently ignored.

### Sweeps

| Sweep | Lock | Interval | Purpose |
|---|---|---|---|
| retry | `lock:sweep:notification-retry` | `PT1M` | re-attempt eligible notifications |
| device pruning | `lock:sweep:device-pruning` | `P1D` | deactivate stale tokens |

### Configuration

| Property | Value |
|---|---|
| `notification.max-attempts` | 3 per channel |
| `notification.retry-backoff` | `PT30S` initial, exponential |
| `notification.dedup-window` | `PT24H` |
| `notification.send-timeout` | `PT10S` |
| `notification.device.stale-after` | `P90D` |
| `notification.default-locale` | `en` |
| `WHATSAPP_API_URL`, `WHATSAPP_API_TOKEN` | environment only |
| `SMS_API_KEY`, `SMS_SENDER_ID` | environment only |
| `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` | environment only |

### Error codes

`NOTIFICATION_CHANNEL_UNAVAILABLE`, `DEVICE_TOKEN_INVALID` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`NOTIFICATION_CHANNEL_UNAVAILABLE` is the refusal
[ET-IDN-001](../../identity/001-phone-otp-identity/) R2 raises when every OTP channel fails.

## 5. Tasks

- [ ] **T1 · `NotificationChannelPort` and the four adapters**
  - requirements: R1
  - files: `backend/identity-service/.../infrastructure/messaging/`
  - verify: no service names a provider; a test double needs no service change
  - parallel-safe: yes — one adapter per agent
  - depends: —

- [ ] **T2 · The template registry, its seeding and the render-fail-fast rule**
  - requirements: R2
  - files: `backend/identity-service/.../domain/model/NotificationTemplate.java`, the seeder
  - verify: every §4 key exists for every channel in its chain; a missing parameter fails
  - parallel-safe: no
  - depends: —

- [ ] **T3 · Categories, chains and the transactional-versus-optional rule**
  - requirements: R3, R5
  - files: `backend/identity-service/.../domain/NotificationCategory.java`
  - verify: `OTP` never attempts email; a disabled transactional category still sends
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · The asynchronous send path and the three provider-outage tests**
  - requirements: R4
  - files: `backend/identity-service/.../service/impl/NotificationServiceImpl.java`
  - verify: purchase, payout and cancellation all complete with every provider stopped
  - parallel-safe: no
  - depends: T1, T3

- [ ] **T5 · Deduplication: the unique index and the Redis fast path**
  - requirements: R7
  - files: `backend/identity-service/.../service/impl/NotificationServiceImpl.java`
  - verify: three deliveries of one trigger produce one message
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · Retry with backoff, the sweep and the terminal failure**
  - requirements: R6
  - files: `backend/identity-service/.../scheduler/NotificationRetrySweeper.java`
  - verify: nothing reaches the dead-letter queue; a failed transactional message surfaces
  - parallel-safe: yes
  - depends: T4

- [ ] **T7 · Device registration, invalidation and pruning**
  - requirements: R8
  - files: `backend/identity-service/.../service/impl/UserDeviceServiceImpl.java`
  - verify: register, invalidate and re-register yields one active row
  - parallel-safe: yes
  - depends: T1

- [ ] **T8 · Preferences, and the refusal to disable a transactional category**
  - requirements: R3
  - files: `backend/identity-service/.../web/graphql/mutation/`
  - verify: disabling `PAYMENT` is refused, not ignored
  - parallel-safe: yes
  - depends: T3

- [ ] **T9 · The subgraph half; `@auth` on every field**
  - requirements: R1–R8
  - files: `backend/identity-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL across identity's specs
  - depends: T8

## 6. Out of scope

| Capability | Spec |
|---|---|
| Which fact fires which template | [ET-NTF-002](../002-lifecycle-triggers/) |
| The OTP's generation, verification and lifecycle | [ET-IDN-001](../../identity/001-phone-otp-identity/) |
| Ticket delivery content and re-send | [ET-TKT-002](../../ticketing/002-ticket-issuance-and-qr/) |
| The two-tier event system this consumes from | [ET-PLT-003](../../_platform/003-event-contract/) |
| Rate limiting outbound messages per user | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| Erasing a phone number or email on account deletion | [ET-PLT-008](../../_platform/008-data-protection/) |
| Delivery dashboards and alert routing | [ET-ADM-005](../../admin/005-observability-and-health/) |

Deliberately never in scope: **synchronous sending on the business path** (a WhatsApp
outage would stop ticket sales), **honouring preferences for transactional messages** (a
user opts out and never learns their event was cancelled), and **one global fallback
chain** (it sends an OTP by email twenty minutes after it expired).
