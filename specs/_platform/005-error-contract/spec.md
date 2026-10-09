# ET-PLT-005 · Error contract — the closed code registry, typed handlers, retryability

## 1. Capability

A mobile client in Lusaka taps *pay* on a K350 ticket and something goes wrong. What it
does next depends entirely on what the platform tells it: retry silently, show *this tier
just sold out*, send the user to re-authenticate, or say *your money is fine, we are
checking*. A response that says `INTERNAL_ERROR: something went wrong` supports none of
those, so the client shows a generic failure, the user retries manually, and the platform
takes a second payment for a ticket it already issued.

This spec makes every refusal in the platform legible. It declares the closed registry of
error codes — one code per business refusal, allocated per capability and never
duplicated — the base type every refusal extends, the exact `extensions` payload a client
receives, and the mapping from refusal to GraphQL `ErrorType`. It settles the two
questions that decide whether a client can act: **is this retryable**, and **which of my
inputs was wrong**. And it draws the line between a refusal (a business outcome, named as a
fact) and a failure (a bug or an outage, which a client can only be told to try again about
later).

It delivers no domain behaviour. Its success criterion is that every domain refusal in the
platform reaches a client with a code from this registry, that no unhandled exception ever
carries an internal detail across the boundary, and that a client written against
`extensions.errorCode` alone never needs to read a message string.

## 2. Design decisions

**Refusals are named as facts, not as plumbing.** `TierSoldOut`, `PayoutBelowMinimum`,
`InvitationExpired`, `OrganizerNotApproved` — never `TicketNotFoundException` or
`ValidationException`. The name of the type is the business outcome, which means the
handler reads as a statement about the domain and the code that raises it does not need a
comment.

**One base type, and it is abstract.** Every refusal extends
`com.pml.shared.error.DomainRefusal`, which carries the registry code, the retryability
flag and a typed details map. One base type means one `@DgsExceptionHandler` can catch
the whole family as a fallback, and a lint rule can prove every subtype has a specific
handler.

**The registry in §4 is closed, and one condition gets one code.** Two codes for "the
transition is not legal from the current state" means every client branches on both, then
on one, then on neither. A capability that needs a new code adds a row here in the same
change; a capability that finds an existing row describing its condition uses it.
Codes are `UPPER_SNAKE_CASE`, allocated per area, and never reused after withdrawal.

**`extensions` is the contract; the message is for humans.** Clients branch on
`extensions.errorCode` and read `extensions.retryable` to decide whether to retry. They
never parse `message`, which exists for logs and for a developer reading a response —
and which may change without notice, because it is not the contract.

**Every error carries `retryable`, and it is a judgement about this operation.**
`PAYMENT_PROVIDER_UNAVAILABLE` is retryable; `PAYMENT_DECLINED` is not, and a client that
retries it will decline again while the user watches. `TIER_SOLD_OUT` is not retryable in
the sense the client means — the answer will not change by trying — even though the tier
might later be restocked.

**A refusal never carries a third party's raw message.** A provider's decline text can
name an account state, a balance, or a subscriber's status. It is logged, correlated and
never returned. What the client gets is the registry code and the typed details this spec
allows.

**Cross-tenant access is `NOT_FOUND`, not `PERMISSION_DENIED`.** Telling a caller that a
resource exists but is not theirs is an enumeration oracle: iterate ids, collect the ones
that say *denied*, and you have mapped another organization's events. Within a tenant, an
insufficient role is honestly `PERMISSION_DENIED`, because there the caller already knows
the resource exists.

**Unhandled exceptions cross the boundary as `INTERNAL`, with a correlation id and
nothing else.** No stack trace, no exception class name, no message. The response carries
`extensions.correlationId`, the log carries the same id and the full detail, and a support
conversation is one grep. A stack trace in a GraphQL response is a map of the codebase.

**Input validation is one code with a field list.** Bean Validation runs at the input
boundary and produces `COMMAND_NOT_WELL_FORMED` carrying
`extensions.fields: [{ path, constraint }]`. One code rather than one per constraint,
because a form needs the list, not a taxonomy.

**Optimistic-lock contention is a retryable refusal, not an internal failure.**
`OptimisticLockingFailureException` from [ET-PLT-002](../002-persistence-baseline/)'s
version-locked documents maps to `RESOURCE_CONFLICT` with `retryable: true` — the caller
lost a race that a retry will usually win, which is a materially different message from
*something broke*.

**Errors that only a listener can see are not client errors.** A consumer that cannot
process a message records it and lets the dead-letter machinery of
[ET-PLT-003](../003-event-contract/) handle it. `UNSUPPORTED_SCHEMA_VERSION` is a
dead-letter reason and appears in no GraphQL response.

**Rejected alternatives**

- *`extensions.code` as a free string per call site.* What the platform gets without a registry: forty spellings of *not found*, and a frontend `switch` nobody can complete.
- *HTTP status codes as the contract.* GraphQL answers 200 with an errors array; a status code carries nothing a federated response needs.
- *A `success: false` field on a mutation payload.* Two error channels — see [ET-PLT-004](../004-federation-contract/) R4.
- *Returning the provider's decline reason so support can see it.* Support can see it in the log, correlated. The client is not the audit trail.
- *Per-constraint validation codes.* A form needs the list of bad fields, not a hundred codes describing kinds of badness.
- *`PERMISSION_DENIED` for cross-tenant reads.* An enumeration oracle, delivered politely.
- *An `errors` array inside a successful payload for partial application.* A mutation applies or refuses (ET-PLT-004 R4); partial application has no representation here on purpose.

## 3. Requirements

### ET-PLT-005-R1 · Every refusal is a `DomainRefusal` carrying a registry code

THE SYSTEM SHALL raise every business refusal as a subtype of `DomainRefusal` whose code
is a row of the §4 registry.

**Acceptance**
- [ ] `DomainRefusal` is abstract, in `shared-library`, and carries `errorCode`, `retryable` and `Map<String,Object> details`
- [ ] Every refusal type in the platform extends it, is named as a fact, and ends in no `Exception` suffix
- [ ] Every `errorCode` raised anywhere is a row of the §4 registry
- [ ] No two registry rows describe the same condition
- [ ] Every `errorCode` names a row of the ET-PLT-005 §4 registry, every `DomainRefusal` subtype has a `@DgsExceptionHandler`, and every handler sets `extensions.retryable`

### ET-PLT-005-R2 · Every refusal reaches the client with the same `extensions` shape

WHEN a refusal is raised during a GraphQL operation, THE SYSTEM SHALL return a GraphQL
error carrying the §4 `extensions` payload.

**Acceptance**
- [ ] Every `DomainRefusal` subtype has a `@DgsExceptionHandler`, or is caught by the family fallback handler, and neither path returns `INTERNAL`
- [ ] Every returned error carries `extensions.errorCode`, `extensions.retryable` and `extensions.correlationId`
- [ ] `errorType` is the §4 mapping for that code
- [ ] `extensions` carries only the typed detail keys §4 permits for that code — no free-text field, no nested exception
- [ ] A client branching on `extensions.errorCode` alone can distinguish every refusal in the registry
- [ ] An integration test asserts the exact `extensions` payload for one refusal per area

### ET-PLT-005-R3 · Retryability is stated and is correct

THE SYSTEM SHALL declare on every error whether retrying the same operation could succeed.

**Acceptance**
- [ ] `extensions.retryable` is present on every error, including `INTERNAL`
- [ ] Every registry row's retryability matches §4, and a test asserts row-by-row equality between the registry and the code
- [ ] Provider-unavailable, lock-contention and rate-limit conditions are `retryable: true`
- [ ] Declined payments, exhausted OTP attempts, sold-out tiers and permission denials are `retryable: false`
- [ ] A retryable money-moving operation is safe to retry because it carries an idempotency key (ET-PLT-007)

### ET-PLT-005-R4 · Unhandled failures leak nothing

IF an exception is not a `DomainRefusal`, THEN THE SYSTEM SHALL return `INTERNAL` carrying
a correlation id and no internal detail.

**Acceptance**
- [ ] A catch-all `@DgsExceptionHandler` maps every non-`DomainRefusal` throwable to `errorCode: INTERNAL_ERROR`, `errorType: INTERNAL`, `retryable: true`
- [ ] The returned `message` is a fixed string; no exception message, class name, stack frame, SQL, Mongo query or provider response appears in any response
- [ ] The same `correlationId` appears in the response and in the log entry carrying the full detail
- [ ] An integration test throws a `NullPointerException` from a resolver and asserts the response contains none of: the class name, the message, or a stack frame
- [ ] `graphql.servlet.exception-handlers-enabled`-style debug output is off in every non-local profile

### ET-PLT-005-R5 · Validation is one code carrying the field list

WHEN input fails validation, THE SYSTEM SHALL refuse with `COMMAND_NOT_WELL_FORMED` and
enumerate the offending fields.

**Acceptance**
- [ ] Bean Validation annotations are declared on every GraphQL input type and every REST request body
- [ ] A validation failure produces exactly one GraphQL error with `errorCode: COMMAND_NOT_WELL_FORMED` and `errorType: BAD_REQUEST`
- [ ] `extensions.fields` is a list of `{ path, constraint }`, where `path` is the input path and `constraint` is the constraint name — never the message template
- [ ] Validation runs before any repository call, so a malformed request writes nothing
- [ ] A refused operation persists nothing — asserted per refusal in the owning spec's tests (ET-PLT-006)

### ET-PLT-005-R6 · Existence is not disclosed across a tenant boundary

IF a caller requests a resource belonging to an organization they are not a member of,
THEN THE SYSTEM SHALL respond as though it does not exist.

**Acceptance**
- [ ] A cross-tenant read of a known-good id returns the `*_UNKNOWN` code for that resource type with `errorType: NOT_FOUND`
- [ ] A within-tenant request lacking the required role returns `ACTOR_NOT_PERMITTED` with `errorType: PERMISSION_DENIED`
- [ ] The two are distinguishable in logs but not in responses
- [ ] A test iterates ids across two organizations and asserts the response is identical for a non-existent id and for another tenant's id

### ET-PLT-005-R7 · Lock contention is a retryable refusal, not a failure

WHEN an optimistic-lock conflict occurs, THE SYSTEM SHALL refuse with `RESOURCE_CONFLICT`
and mark it retryable.

**Acceptance**
- [ ] `OptimisticLockingFailureException` and `DuplicateKeyException` on a versioned document map to `RESOURCE_CONFLICT`, `errorType: FAILED_PRECONDITION`, `retryable: true`
- [ ] A `DuplicateKeyException` on the `booking_payment_intents.idempotencyKey` index maps instead to `IDEMPOTENCY_KEY_REUSED` — a duplicate request is not contention
- [ ] Server-side retry of a lock conflict is bounded and explicit; when the budget is exhausted the refusal reaches the client
- [ ] No lock conflict surfaces as `INTERNAL`

## 4. Model

### The base type

```java
public abstract class DomainRefusal extends RuntimeException {
    private final ErrorCode code;                 // the registry row
    private final boolean retryable;
    private final Map<String, Object> details;    // typed keys only, per §4
}
```

`ErrorCode` is an enum in `shared-library` with one constant per registry row, each
carrying its `ErrorType` and its default retryability. The enum **is** the registry in
code; the table below is the registry in prose, and a test asserts they agree.

### The `extensions` payload

| Key | Always present | Contents |
|---|---|---|
| `errorCode` | yes | the registry row |
| `retryable` | yes | boolean |
| `correlationId` | yes | matches the log entry |
| `fields` | validation only | `[{ path, constraint }]` |
| *typed detail keys* | per row | only the keys the row permits |

```json
{ "message": "This ticket tier is sold out",
  "path": ["reserveTickets"],
  "extensions": { "errorCode": "TIER_SOLD_OUT", "retryable": false,
                  "correlationId": "5f3c…", "tierId": "665a…",
                  "availableQuantity": 0 } }
```

### `ErrorType` vocabulary

DGS's `com.netflix.graphql.types.errors.ErrorType`. These eight and no others.

| `ErrorType` | Used for |
|---|---|
| `BAD_REQUEST` | the request is malformed |
| `UNAUTHENTICATED` | no valid identity |
| `PERMISSION_DENIED` | identity known, role insufficient, resource within the caller's tenant |
| `NOT_FOUND` | absent — or another tenant's (R6) |
| `FAILED_PRECONDITION` | the request is well formed but the world is not in the required state |
| `UNAVAILABLE` | a dependency is down or the caller is throttled |
| `INTERNAL` | a defect |
| `UNKNOWN` | never used deliberately |

### Error code registry — closed

**Platform**

| Code | Refusal type | `ErrorType` | Retryable | Introduced by |
|---|---|---|---|---|
| `COMMAND_NOT_WELL_FORMED` | `CommandNotWellFormed` | `BAD_REQUEST` | no | ET-PLT-005 |
| `INTERNAL_ERROR` | *(catch-all)* | `INTERNAL` | yes | ET-PLT-005 |
| `RESOURCE_CONFLICT` | `ResourceConflict` | `FAILED_PRECONDITION` | **yes** | ET-PLT-005 |
| `PAGE_SIZE_EXCEEDED` | `PageSizeExceeded` | `BAD_REQUEST` | no | ET-PLT-004 |
| `ACTOR_NOT_AUTHENTICATED` | `ActorNotAuthenticated` | `UNAUTHENTICATED` | no | ET-PLT-007 |
| `ACTOR_NOT_PERMITTED` | `ActorNotPermitted` | `PERMISSION_DENIED` | no | ET-PLT-007 |
| `IDEMPOTENCY_KEY_REUSED` | `IdempotencyKeyReused` | `FAILED_PRECONDITION` | no | ET-PLT-007 |
| `TOKEN_REVOKED` | `TokenRevoked` | `UNAUTHENTICATED` | no | ET-IDN-003 |
| `REVOCATION_UNAVAILABLE` | `RevocationUnavailable` | `UNAVAILABLE` | **yes** | ET-IDN-003 |
| `RATE_LIMIT_EXCEEDED` | `RateLimitExceeded` | `UNAVAILABLE` | **yes** | ET-PLT-011 |
| `PERMISSION_KEY_INVALID` | `PermissionKeyInvalid` | `BAD_REQUEST` | no | ET-PLT-013 |
| `PERMISSION_UNKNOWN` | `PermissionUnknown` | `NOT_FOUND` | no | ET-PLT-013 |
| `ROLE_MAPPING_UNKNOWN` | `RoleMappingUnknown` | `NOT_FOUND` | no | ET-PLT-013 |
| `REFERENCE_TYPE_UNKNOWN` | `ReferenceTypeUnknown` | `NOT_FOUND` | no | ET-PLT-014 |
| `REFERENCE_CODE_DUPLICATE` | `ReferenceCodeDuplicate` | `FAILED_PRECONDITION` | no | ET-PLT-014 |
| `REFERENCE_SEMANTIC_REQUIRED` | `ReferenceSemanticRequired` | `BAD_REQUEST` | no | ET-PLT-014 |
| `REFERENCE_MACHINE_CODE_OWNED` | `ReferenceMachineCodeOwned` | `FAILED_PRECONDITION` | no | ET-PLT-014 |

**Identity** — ET-IDN-001, ET-IDN-002, ET-IDN-004

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `PHONE_NUMBER_INVALID` | `PhoneNumberInvalid` | `BAD_REQUEST` | no | — |
| `OTP_INVALID` | `OtpInvalid` | `FAILED_PRECONDITION` | no | `attemptsRemaining` |
| `OTP_EXPIRED` | `OtpExpired` | `FAILED_PRECONDITION` | no | — |
| `OTP_COOLDOWN_ACTIVE` | `OtpCooldownActive` | `FAILED_PRECONDITION` | **yes** | `retryAfterSeconds` |
| `OTP_ATTEMPTS_EXHAUSTED` | `OtpAttemptsExhausted` | `PERMISSION_DENIED` | no | `lockedUntil` |
| `USER_UNKNOWN` | `UserUnknown` | `NOT_FOUND` | no | — |
| `USER_SYNC_CONFLICT` | `UserSyncConflict` | `FAILED_PRECONDITION` | **yes** | — |
| `CONTACT_INVALID` | `ContactInvalid` | `BAD_REQUEST` | no | — |
| `OTP_LOCKED` | `OtpLocked` | `PERMISSION_DENIED` | no | `lockedUntil` |
| `OTP_RATE_LIMITED` | `OtpRateLimited` | `UNAVAILABLE` | **yes** | `retryAfterSeconds` |
| `OTP_DELIVERY_FAILED` | `OtpDeliveryFailed` | `UNAVAILABLE` | **yes** | — |
| `CONTACT_ALREADY_CLAIMED` | `ContactAlreadyClaimed` | `FAILED_PRECONDITION` | **yes** | — |
| `ACCOUNT_SUSPENDED` | `AccountSuspended` | `PERMISSION_DENIED` | no | — |
| `ACCOUNT_MERGING` | `AccountMerging` | `FAILED_PRECONDITION` | **yes** | — |
| `ACCOUNT_NOT_ACTIVE` | `AccountNotActive` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `LOGIN_HANDLE_INVALID` | `LoginHandleInvalid` | `BAD_REQUEST` | no | — |
| `PROOF_INVALID` | `ProofInvalid` | `BAD_REQUEST` | no | — |
| `CONTACT_UNKNOWN` | `ContactUnknown` | `NOT_FOUND` | no | — |
| `CONTACT_CHANGE_IN_PROGRESS` | `ContactChangeInProgress` | `FAILED_PRECONDITION` | no | — |
| `LAST_VERIFIED_CONTACT` | `LastVerifiedContact` | `FAILED_PRECONDITION` | no | — |
| `NO_VERIFIED_CONTACT` | `NoVerifiedContact` | `FAILED_PRECONDITION` | no | — |

**Organization** — ET-ORG-001, ET-ORG-002, ET-ORG-003

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `ORGANIZATION_UNKNOWN` | `OrganizationUnknown` | `NOT_FOUND` | no | — |
| `ORGANIZATION_STATE_INVALID` | `OrganizationNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `ORGANIZATION_ALREADY_EXISTS` | `OrganizationAlreadyExists` | `FAILED_PRECONDITION` | no | — |
| `SLUG_TAKEN` | `SlugTaken` | `FAILED_PRECONDITION` | no | `suggestedSlug` |
| `APPLICATION_INCOMPLETE` | `ApplicationIncomplete` | `FAILED_PRECONDITION` | no | `missingFields` |
| `DOCUMENT_REQUIRED` | `DocumentRequired` | `FAILED_PRECONDITION` | no | `missingDocumentTypes` |
| `DOCUMENT_STATE_INVALID` | `DocumentNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `MEMBER_UNKNOWN` | `MemberUnknown` | `NOT_FOUND` | no | — |
| `MEMBER_ALREADY_EXISTS` | `MemberAlreadyExists` | `FAILED_PRECONDITION` | no | — |
| `ORGANIZATION_NOT_ACTIVE` | `OrganizationNotActive` | `FAILED_PRECONDITION` | no | — |
| `OWNER_CANNOT_BE_REMOVED` | `OwnerCannotBeRemoved` | `FAILED_PRECONDITION` | no | — |
| `OWNER_ROLE_IMMUTABLE` | `OwnerRoleImmutable` | `FAILED_PRECONDITION` | no | — |
| `INVITATION_UNKNOWN` | `InvitationUnknown` | `NOT_FOUND` | no | — |
| `DOCUMENT_UNKNOWN` | `DocumentUnknown` | `NOT_FOUND` | no | — |
| `INVITATION_EXPIRED` | `InvitationExpired` | `FAILED_PRECONDITION` | no | `expiredAt` |
| `INVITATION_NOT_PENDING` | `InvitationNotPending` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `INVITATION_NOT_ADDRESSED_TO_CALLER` | `InvitationNotAddressedToCaller` | `PERMISSION_DENIED` | no | — |
| `TRANSFER_TARGET_INELIGIBLE` | `TransferTargetIneligible` | `FAILED_PRECONDITION` | no | `requiredRole` |
| `TRANSFER_NOT_PENDING` | `TransferNotPending` | `FAILED_PRECONDITION` | no | — |
| `ACCESS_GRANT_UNKNOWN` | `AccessGrantUnknown` | `NOT_FOUND` | no | — |
| `EVENT_ROLE_NOT_GRANTABLE` | `EventRoleNotGrantable` | `PERMISSION_DENIED` | no | `eventRole` |

**Catalogue** — ET-CAT-001, ET-CAT-002, ET-CAT-003

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `EVENT_UNKNOWN` | `EventUnknown` | `NOT_FOUND` | no | — |
| `EVENT_STATE_INVALID` | `EventNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `ORGANIZER_NOT_APPROVED` | `OrganizerNotApproved` | `PERMISSION_DENIED` | no | `organizationStatus` |
| `TIER_UNKNOWN` | `TierUnknown` | `NOT_FOUND` | no | — |
| `TIER_NOT_ON_SALE` | `TierNotOnSale` | `FAILED_PRECONDITION` | no | `salesStartAt`, `salesEndAt` |
| `CAPACITY_BELOW_COMMITTED` | `CapacityBelowCommitted` | `FAILED_PRECONDITION` | no | `committedQuantity` |
| `LOCATION_UNKNOWN` | `LocationUnknown` | `NOT_FOUND` | no | — |
| `MEDIA_UNKNOWN` | `MediaUnknown` | `NOT_FOUND` | no | — |
| `MEDIA_STATE_INVALID` | `MediaNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `PROMO_CODE_UNKNOWN` | `PromoCodeUnknown` | `NOT_FOUND` | no | — |
| `PROMO_CODE_EXHAUSTED` | `PromoCodeExhausted` | `FAILED_PRECONDITION` | no | — |
| `PROMO_CODE_NOT_APPLICABLE` | `PromoCodeNotApplicable` | `FAILED_PRECONDITION` | no | `reason` |

**Ticketing** — ET-TKT-001 … ET-TKT-004

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `TIER_SOLD_OUT` | `TierSoldOut` | `FAILED_PRECONDITION` | no | `tierId`, `availableQuantity` |
| `RESERVATION_UNKNOWN` | `ReservationUnknown` | `NOT_FOUND` | no | — |
| `RESERVATION_EXPIRED` | `ReservationExpired` | `FAILED_PRECONDITION` | no | `expiredAt` |
| `RESERVATION_STATE_INVALID` | `ReservationNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `PURCHASE_LIMIT_EXCEEDED` | `PurchaseLimitExceeded` | `FAILED_PRECONDITION` | no | `limit`, `alreadyHeld` |
| `TICKET_UNKNOWN` | `TicketUnknown` | `NOT_FOUND` | no | — |
| `TICKET_STATE_INVALID` | `TicketNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `TICKET_ALREADY_VALIDATED` | `TicketAlreadyValidated` | `FAILED_PRECONDITION` | no | `validatedAt`, `validatedBy` |
| `TICKET_NOT_VALID_FOR_EVENT` | `TicketNotValidForEvent` | `FAILED_PRECONDITION` | no | `expectedEventId` |
| `TICKET_SIGNATURE_INVALID` | `TicketSignatureInvalid` | `PERMISSION_DENIED` | no | — |
| `TICKET_NOT_TRANSFERABLE` | `TicketNotTransferable` | `FAILED_PRECONDITION` | no | `reason` |
| `TRANSFER_TO_SELF` | `TransferToSelf` | `BAD_REQUEST` | no | — |
| `BOOKING_UNKNOWN` | `BookingUnknown` | `NOT_FOUND` | no | — |
| `TICKET_TRANSFER_UNKNOWN` | `TicketTransferUnknown` | `NOT_FOUND` | no | — |

**Payment** — ET-PAY-001, ET-PAY-002

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `PAYMENT_INTENT_UNKNOWN` | `PaymentIntentUnknown` | `NOT_FOUND` | no | — |
| `PAYMENT_ALREADY_COMPLETED` | `PaymentAlreadyCompleted` | `FAILED_PRECONDITION` | no | `completedAt` |
| `PAYMENT_DECLINED` | `PaymentDeclined` | `FAILED_PRECONDITION` | no | `declineCategory` |
| `PAYMENT_AMOUNT_MISMATCH` | `PaymentAmountMismatch` | `FAILED_PRECONDITION` | no | `expectedAmount` |
| `PAYMENT_PROVIDER_UNAVAILABLE` | `PaymentProviderUnavailable` | `UNAVAILABLE` | **yes** | `retryAfterSeconds` |
| `MSISDN_PROVIDER_UNSUPPORTED` | `MsisdnProviderUnsupported` | `BAD_REQUEST` | no | `supportedProviders` |
| `WEBHOOK_SIGNATURE_INVALID` | `WebhookSignatureInvalid` | `PERMISSION_DENIED` | no | — |
| `WEBHOOK_REPLAYED` | `WebhookReplayed` | `FAILED_PRECONDITION` | no | — |

`declineCategory` is a platform enum — `INSUFFICIENT_FUNDS`, `SUBSCRIBER_UNREACHABLE`,
`LIMIT_EXCEEDED`, `REJECTED_BY_USER`, `OTHER` — mapped from the provider. **The provider's
own message is never a detail key.**

**Finance** — ET-FIN-001 … ET-FIN-005

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `ESCROW_ACCOUNT_UNKNOWN` | `EscrowAccountUnknown` | `NOT_FOUND` | no | — |
| `ESCROW_NOT_ACTIVE` | `EscrowNotActive` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `ESCROW_INSUFFICIENT_BALANCE` | `EscrowInsufficientBalance` | `FAILED_PRECONDITION` | no | `availableBalance` |
| `JOURNAL_UNBALANCED` | `JournalUnbalanced` | `INTERNAL` | no | — |
| `ACCOUNT_CODE_UNKNOWN` | `AccountCodeUnknown` | `NOT_FOUND` | no | — |
| `COMMISSION_ALREADY_RECOGNISED` | `CommissionAlreadyRecognised` | `FAILED_PRECONDITION` | no | `recognisedAt` |
| `COMMISSION_RATE_UNKNOWN` | `CommissionRateUnknown` | `FAILED_PRECONDITION` | no | — |
| `PAYOUT_BELOW_MINIMUM` | `PayoutBelowMinimum` | `FAILED_PRECONDITION` | no | `minimumAmount` |
| `PAYOUT_WINDOW_NOT_OPEN` | `PayoutWindowNotOpen` | `FAILED_PRECONDITION` | no | `opensAt` |
| `PAYOUT_STATE_INVALID` | `PayoutNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `BANK_ACCOUNT_UNKNOWN` | `BankAccountUnknown` | `NOT_FOUND` | no | — |
| `PAYOUT_REQUEST_UNKNOWN` | `PayoutRequestUnknown` | `NOT_FOUND` | no | — |
| `BANK_ACCOUNT_NOT_VERIFIED` | `BankAccountNotVerified` | `FAILED_PRECONDITION` | no | — |
| `REFUND_NOT_PERMITTED` | `RefundNotPermitted` | `FAILED_PRECONDITION` | no | `reason` |
| `REFUND_WINDOW_CLOSED` | `RefundWindowClosed` | `FAILED_PRECONDITION` | no | `closedAt` |
| `REFUND_ALREADY_ISSUED` | `RefundAlreadyIssued` | `FAILED_PRECONDITION` | no | `refundRequestId` |
| `CHARGEBACK_STATE_INVALID` | `ChargebackNotInExpectedState` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `RECONCILIATION_IN_PROGRESS` | `ReconciliationInProgress` | `FAILED_PRECONDITION` | **yes** | `runId` |

`JOURNAL_UNBALANCED` is `INTERNAL` deliberately. Debits not equalling credits is not a
user's mistake; it is a defect, and it must page somebody.

**Notification and administration** — ET-NTF-001, ET-ADM-002, ET-ADM-003

| Code | Refusal type | `ErrorType` | Retryable | Detail keys |
|---|---|---|---|---|
| `NOTIFICATION_CHANNEL_UNAVAILABLE` | `NotificationChannelUnavailable` | `UNAVAILABLE` | **yes** | `channel` |
| `DEVICE_TOKEN_INVALID` | `DeviceTokenInvalid` | `BAD_REQUEST` | no | — |
| `CONFIGURATION_KEY_UNKNOWN` | `ConfigurationKeyUnknown` | `NOT_FOUND` | no | — |
| `CONFIGURATION_VALUE_INVALID` | `ConfigurationValueInvalid` | `BAD_REQUEST` | no | `constraint` |
| `TRANSACTION_NOT_RECOVERABLE` | `TransactionNotRecoverable` | `FAILED_PRECONDITION` | no | `currentStatus` |
| `RECOVERY_PROPOSAL_UNKNOWN` | `RecoveryProposalUnknown` | `NOT_FOUND` | no | — |

**86 codes.** No other code exists. `UNSUPPORTED_SCHEMA_VERSION` is a dead-letter reason
([ET-PLT-003](../003-event-contract/)), not a client-facing code, and is deliberately
absent from this registry.

### The handler shape

One `@DgsComponent` per service, plus a family fallback in `shared-library`.

```java
@DgsComponent
public class BookingErrorHandler {

    @DgsExceptionHandler(TierSoldOut.class)
    public GraphQLError soldOut(TierSoldOut ex, DgsExceptionHandlerParameters p) {
        return GraphQlErrors.from(ex, p);   // shared-library: builds the §4 extensions
    }

    // contention, not a defect — the caller lost a race a retry usually wins
    @DgsExceptionHandler(OptimisticLockingFailureException.class)
    public GraphQLError contended(OptimisticLockingFailureException ex,
                                  DgsExceptionHandlerParameters p) {
        return GraphQlErrors.from(new ResourceConflict(), p);
    }
}
```

`GraphQlErrors.from` is the single place `extensions` is constructed, so the payload shape
cannot drift between services. The catch-all handler lives in `shared-library` and is the
only code path that produces `INTERNAL_ERROR`.

### REST parity

`/api/internal/**` and the upload endpoints of [ET-ORG-001](../../organization/001-organizer-onboarding/)
return the same registry code in an RFC 9457 problem document, with `type` derived from
the code and the HTTP status mapped from `ErrorType`.

| `ErrorType` | HTTP |
|---|---|
| `BAD_REQUEST` | 400 |
| `UNAUTHENTICATED` | 401 |
| `PERMISSION_DENIED` | 403 |
| `NOT_FOUND` | 404 |
| `FAILED_PRECONDITION` | 409 |
| `UNAVAILABLE` | 503 |
| `INTERNAL` | 500 |

## 5. Tasks

- [ ] **T1 · `DomainRefusal`, `ErrorCode` enum and `GraphQlErrors` in `shared-library`**
  - requirements: R1, R2
  - files: `backend/shared-library/src/main/java/com/pml/shared/error/`
  - verify: a test asserts the enum equals the §4 registry row for row
  - parallel-safe: no — every service imports it
  - depends: —

- [ ] **T2 · The family fallback and catch-all handlers**
  - requirements: R2, R4
  - files: `backend/shared-library/.../error/GlobalErrorHandler.java`
  - verify: an NPE thrown from a resolver leaks no class name, message or frame
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · A refusal type and a `@DgsExceptionHandler` per registry row, per service**
  - requirements: R1, R2, R3
  - files: `backend/*/src/main/java/com/pml/*/exception/`, `.../web/graphql/exception/`
  - verify: every `errorCode` names a row of the ET-PLT-005 §4 registry, every `DomainRefusal` subtype has a `@DgsExceptionHandler`, and every handler sets `extensions.retryable`
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T4 · Bean Validation on every input; the `fields` extension**
  - requirements: R5
  - files: every GraphQL input DTO, every REST request body
  - verify: a malformed input yields one error with the offending field list and writes nothing
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T5 · Tenant-boundary responses return `*_UNKNOWN`**
  - requirements: R6
  - files: the repository-level tenant filters of ET-PLT-007
  - verify: a cross-tenant id and a non-existent id produce identical responses
  - parallel-safe: yes
  - depends: T3

- [ ] **T6 · Map lock contention and duplicate keys; bound the server-side retry**
  - requirements: R7
  - files: `backend/shared-library/.../error/`, booking's handlers
  - verify: a lock conflict returns `RESOURCE_CONFLICT` retryable; an idempotency-key duplicate returns `IDEMPOTENCY_KEY_REUSED`
  - parallel-safe: yes
  - depends: T3

- [ ] **T7 · RFC 9457 problem documents on the REST surface**
  - requirements: R2
  - files: `backend/*/src/main/java/com/pml/*/web/rest/`
  - verify: a REST refusal carries the same `errorCode` as its GraphQL equivalent
  - parallel-safe: yes
  - depends: T2

## 6. Out of scope

| Capability | Spec |
|---|---|
| Which conditions each capability actually raises | the spec that introduces the refusal |
| Authentication and authorization mechanics behind `ACTOR_NOT_*` | [ET-PLT-007](../007-security-and-authorization/) |
| Idempotency-key lifecycle behind `IDEMPOTENCY_KEY_REUSED` | [ET-PLT-007](../007-security-and-authorization/), [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| Dead-letter reasons and consumer failure handling | [ET-PLT-003](../003-event-contract/) |
| Rate-limit thresholds behind `RATE_LIMIT_EXCEEDED` | [ET-PLT-011](../011-rate-limiting-and-abuse/) |
| Alerting on `INTERNAL_ERROR` rate and on `JOURNAL_UNBALANCED` | [ET-ADM-005](../../admin/005-observability-and-health/) |
| Operator recovery of failed transactions | [ET-ADM-003](../../admin/003-transaction-recovery/) |

Deliberately never in scope: **per-constraint validation codes** (a form needs a field
list), and **provider decline text in a response** (it belongs in the correlated log).
