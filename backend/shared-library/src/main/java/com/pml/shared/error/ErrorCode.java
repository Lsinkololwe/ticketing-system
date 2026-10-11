package com.pml.shared.error;

/**
 * The closed registry of every refusal the platform can return.
 *
 * <h2>Asserted row for row against the published registry</h2>
 * {@code ErrorCodeRegistryTest} parses the published error-code table and asserts
 * this enum equals it <b>row for row</b> — not "contains". An enum with an extra
 * code is a code nobody documented; an enum missing one is a refusal no client
 * can branch on.
 *
 * <h2>Why a closed set at all</h2>
 * Without a registry the platform gets forty spellings of
 * <em>not found</em> and a frontend {@code switch} nobody can complete. One
 * condition gets one code, so a client that handles a code handles the
 * condition.
 *
 * <h2>{@code retryable} is a judgement about this operation, not this class</h2>
 * It is the field a client reads to decide whether to offer a retry button, and
 * getting it wrong is expensive in both directions: retrying a declined payment
 * declines again while the user watches, and not retrying a transient provider
 * outage turns a blip into a lost sale.
 */
public enum ErrorCode {
    /** — · CommandNotWellFormed */
    COMMAND_NOT_WELL_FORMED(ErrorClassification.BAD_REQUEST, false),
    /** — · InternalError: an unexpected failure whose detail stays on the server */
    INTERNAL_ERROR(ErrorClassification.INTERNAL, true),
    /** — · ResourceConflict */
    RESOURCE_CONFLICT(ErrorClassification.FAILED_PRECONDITION, true),
    /** — · PageSizeExceeded */
    PAGE_SIZE_EXCEEDED(ErrorClassification.BAD_REQUEST, false),
    /** — · ActorNotAuthenticated */
    ACTOR_NOT_AUTHENTICATED(ErrorClassification.UNAUTHENTICATED, false),
    /** — · ActorNotPermitted */
    ACTOR_NOT_PERMITTED(ErrorClassification.PERMISSION_DENIED, false),
    /** — · IdempotencyKeyReused */
    IDEMPOTENCY_KEY_REUSED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · TokenRevoked */
    TOKEN_REVOKED(ErrorClassification.UNAUTHENTICATED, false),
    /** — · RevocationUnavailable */
    REVOCATION_UNAVAILABLE(ErrorClassification.UNAVAILABLE, true),
    /** — · RateLimitExceeded */
    RATE_LIMIT_EXCEEDED(ErrorClassification.UNAVAILABLE, true),
    /** — · PermissionKeyInvalid */
    PERMISSION_KEY_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** — · PermissionUnknown */
    PERMISSION_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · RoleMappingUnknown */
    ROLE_MAPPING_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · ReferenceTypeUnknown */
    REFERENCE_TYPE_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · ReferenceCodeDuplicate */
    REFERENCE_CODE_DUPLICATE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · ReferenceSemanticRequired */
    REFERENCE_SEMANTIC_REQUIRED(ErrorClassification.BAD_REQUEST, false),
    /** — · ReferenceMachineCodeOwned */
    REFERENCE_MACHINE_CODE_OWNED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · PhoneNumberInvalid */
    PHONE_NUMBER_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** `attemptsRemaining` · OtpInvalid */
    OTP_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · OtpExpired */
    OTP_EXPIRED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `retryAfterSeconds` · OtpCooldownActive */
    OTP_COOLDOWN_ACTIVE(ErrorClassification.FAILED_PRECONDITION, true),
    /** `lockedUntil` · OtpAttemptsExhausted */
    OTP_ATTEMPTS_EXHAUSTED(ErrorClassification.PERMISSION_DENIED, false),
    /** — · UserUnknown */
    USER_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · UserSyncConflict */
    USER_SYNC_CONFLICT(ErrorClassification.FAILED_PRECONDITION, true),
    /** — · ContactInvalid */
    CONTACT_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** `lockedUntil` · OtpLocked */
    OTP_LOCKED(ErrorClassification.PERMISSION_DENIED, false),
    /** `retryAfterSeconds` · OtpRateLimited */
    OTP_RATE_LIMITED(ErrorClassification.UNAVAILABLE, true),
    /** — · OtpDeliveryFailed */
    OTP_DELIVERY_FAILED(ErrorClassification.UNAVAILABLE, true),
    /** — · ContactAlreadyClaimed */
    CONTACT_ALREADY_CLAIMED(ErrorClassification.FAILED_PRECONDITION, true),
    /** — · AccountSuspended */
    ACCOUNT_SUSPENDED(ErrorClassification.PERMISSION_DENIED, false),
    /** — · AccountMerging */
    ACCOUNT_MERGING(ErrorClassification.FAILED_PRECONDITION, true),
    /** `currentStatus` · AccountNotActive */
    ACCOUNT_NOT_ACTIVE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · LoginHandleInvalid */
    LOGIN_HANDLE_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** — · ProofInvalid */
    PROOF_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** — · ContactUnknown: no such contact on the caller's account (another account's is indistinguishable) */
    CONTACT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · ContactChangeInProgress */
    CONTACT_CHANGE_IN_PROGRESS(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · LastVerifiedContact */
    LAST_VERIFIED_CONTACT(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · NoVerifiedContact */
    NO_VERIFIED_CONTACT(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · OrganizationUnknown */
    ORGANIZATION_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `currentStatus` · OrganizationNotInExpectedState */
    ORGANIZATION_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · OrganizationAlreadyExists */
    ORGANIZATION_ALREADY_EXISTS(ErrorClassification.FAILED_PRECONDITION, false),
    /** `suggestedSlug` · SlugTaken */
    SLUG_TAKEN(ErrorClassification.FAILED_PRECONDITION, false),
    /** `missingFields` · ApplicationIncomplete */
    APPLICATION_INCOMPLETE(ErrorClassification.FAILED_PRECONDITION, false),
    /** `missingDocumentTypes` · DocumentRequired */
    DOCUMENT_REQUIRED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `currentStatus` · DocumentNotInExpectedState */
    DOCUMENT_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · MemberUnknown */
    MEMBER_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · MemberAlreadyExists */
    MEMBER_ALREADY_EXISTS(ErrorClassification.FAILED_PRECONDITION, false),
    /** A person belongs to one organization at a time · MemberBelongsToAnotherOrganization */
    MEMBER_IN_ANOTHER_ORGANIZATION(ErrorClassification.FAILED_PRECONDITION, false),

    /**
     * The organization is not in a state that can take new members.
     *
     * <p>Suspended, or still awaiting approval. Inviting into it builds a team for something that
     * cannot sell a ticket, and the invitee's first experience of the platform is a dead account.
     * Retryable is false: the caller has to wait for the organization's own state to change, and
     * an immediate retry finds the same answer.
     */
    ORGANIZATION_NOT_ACTIVE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · OwnerCannotBeRemoved */
    OWNER_CANNOT_BE_REMOVED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · OwnerRoleImmutable */
    OWNER_ROLE_IMMUTABLE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · InvitationUnknown */
    INVITATION_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /**
     * A verification document that does not exist, or belongs to another organization.
     *
     * <p>The two must be indistinguishable. A caller asking for another organizer's KYC document
     * gets the same answer as one asking for an id that does not exist; a distinguishable refusal
     * would be an oracle for enumerating real document ids.</p>
     */
    DOCUMENT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `expiredAt` · InvitationExpired */
    INVITATION_EXPIRED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `currentStatus` · InvitationNotPending */
    INVITATION_NOT_PENDING(ErrorClassification.FAILED_PRECONDITION, false),

    /**
     * The caller holds the token but is not who the invitation was sent to.
     *
     * <p>PERMISSION_DENIED rather than a {@code *_UNKNOWN} disguise: the caller demonstrably holds
     * a real token, so concealing that the invitation exists conceals nothing they do not already
     * know. What the refusal must not disclose is <em>who</em> it was addressed to — that is the
     * invitee's contact details, and a forwarded link is exactly how somebody comes to be holding
     * the token without being the addressee.
     */
    INVITATION_NOT_ADDRESSED_TO_CALLER(ErrorClassification.PERMISSION_DENIED, false),
    /** `requiredRole` · TransferTargetIneligible */
    TRANSFER_TARGET_INELIGIBLE(ErrorClassification.FAILED_PRECONDITION, false),

    /**
     * The transfer was resolved by somebody else while this caller was confirming.
     *
     * <p>Not retryable: the transfer is gone, and a retry finds the same terminal state. The
     * caller who lost is usually the same person tapping twice, so the honest answer is that it is
     * already done rather than that something failed.
     */
    TRANSFER_NOT_PENDING(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · AccessGrantUnknown */
    ACCESS_GRANT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `eventRole` · EventRoleNotGrantable */
    EVENT_ROLE_NOT_GRANTABLE(ErrorClassification.PERMISSION_DENIED, false),
    /** — · EventUnknown */
    EVENT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `currentStatus` · EventNotInExpectedState */
    EVENT_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** `organizationStatus` · OrganizerNotApproved */
    ORGANIZER_NOT_APPROVED(ErrorClassification.PERMISSION_DENIED, false),
    /** — · TierUnknown */
    TIER_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `salesStartAt`, `salesEndAt` · TierNotOnSale */
    TIER_NOT_ON_SALE(ErrorClassification.FAILED_PRECONDITION, false),
    /** `committedQuantity` · CapacityBelowCommitted */
    CAPACITY_BELOW_COMMITTED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · LocationUnknown */
    LOCATION_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · MediaUnknown */
    MEDIA_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `currentStatus` · MediaNotInExpectedState */
    MEDIA_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · PromoCodeUnknown */
    PROMO_CODE_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · PromoCodeExhausted */
    PROMO_CODE_EXHAUSTED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `reason` · PromoCodeNotApplicable */
    PROMO_CODE_NOT_APPLICABLE(ErrorClassification.FAILED_PRECONDITION, false),
    /** `tierId`, `availableQuantity` · TierSoldOut */
    TIER_SOLD_OUT(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · ReservationUnknown */
    RESERVATION_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `expiredAt` · ReservationExpired */
    RESERVATION_EXPIRED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `currentStatus` · ReservationNotInExpectedState */
    RESERVATION_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** `limit`, `alreadyHeld` · PurchaseLimitExceeded */
    PURCHASE_LIMIT_EXCEEDED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · TicketUnknown */
    TICKET_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `currentStatus` · TicketNotInExpectedState */
    TICKET_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** `validatedAt`, `validatedBy` · TicketAlreadyValidated */
    TICKET_ALREADY_VALIDATED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `expectedEventId` · TicketNotValidForEvent */
    TICKET_NOT_VALID_FOR_EVENT(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · TicketSignatureInvalid */
    TICKET_SIGNATURE_INVALID(ErrorClassification.PERMISSION_DENIED, false),
    /** `reason` · TicketNotTransferable */
    TICKET_NOT_TRANSFERABLE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · TransferToSelf */
    TRANSFER_TO_SELF(ErrorClassification.BAD_REQUEST, false),
    /** — · BookingUnknown */
    BOOKING_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · TicketTransferUnknown */
    TICKET_TRANSFER_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · PaymentIntentUnknown */
    PAYMENT_INTENT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `completedAt` · PaymentAlreadyCompleted */
    PAYMENT_ALREADY_COMPLETED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `declineCategory` · PaymentDeclined */
    PAYMENT_DECLINED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `expectedAmount` · PaymentAmountMismatch */
    PAYMENT_AMOUNT_MISMATCH(ErrorClassification.FAILED_PRECONDITION, false),
    /** `retryAfterSeconds` · PaymentProviderUnavailable */
    PAYMENT_PROVIDER_UNAVAILABLE(ErrorClassification.UNAVAILABLE, true),
    /** `supportedProviders` · MsisdnProviderUnsupported */
    MSISDN_PROVIDER_UNSUPPORTED(ErrorClassification.BAD_REQUEST, false),
    /** — · WebhookSignatureInvalid */
    WEBHOOK_SIGNATURE_INVALID(ErrorClassification.PERMISSION_DENIED, false),
    /** — · WebhookReplayed */
    WEBHOOK_REPLAYED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · EscrowAccountUnknown */
    ESCROW_ACCOUNT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `currentStatus` · EscrowNotActive */
    ESCROW_NOT_ACTIVE(ErrorClassification.FAILED_PRECONDITION, false),
    /** `availableBalance` · EscrowInsufficientBalance */
    ESCROW_INSUFFICIENT_BALANCE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · JournalUnbalanced */
    JOURNAL_UNBALANCED(ErrorClassification.INTERNAL, false),
    /** — · AccountCodeUnknown */
    ACCOUNT_CODE_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `recognisedAt` · CommissionAlreadyRecognised */
    COMMISSION_ALREADY_RECOGNISED(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · CommissionRateUnknown */
    COMMISSION_RATE_UNKNOWN(ErrorClassification.FAILED_PRECONDITION, false),
    /** `minimumAmount` · PayoutBelowMinimum */
    PAYOUT_BELOW_MINIMUM(ErrorClassification.FAILED_PRECONDITION, false),
    /** `opensAt` · PayoutWindowNotOpen */
    PAYOUT_WINDOW_NOT_OPEN(ErrorClassification.FAILED_PRECONDITION, false),
    /** `currentStatus` · PayoutNotInExpectedState */
    PAYOUT_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · BankAccountUnknown */
    BANK_ACCOUNT_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · PayoutRequestUnknown */
    PAYOUT_REQUEST_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** — · BankAccountNotVerified */
    BANK_ACCOUNT_NOT_VERIFIED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `reason` · RefundNotPermitted */
    REFUND_NOT_PERMITTED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `closedAt` · RefundWindowClosed */
    REFUND_WINDOW_CLOSED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `refundRequestId` · RefundAlreadyIssued */
    REFUND_ALREADY_ISSUED(ErrorClassification.FAILED_PRECONDITION, false),
    /** `currentStatus` · ChargebackNotInExpectedState */
    CHARGEBACK_STATE_INVALID(ErrorClassification.FAILED_PRECONDITION, false),
    /** `runId` · ReconciliationInProgress */
    RECONCILIATION_IN_PROGRESS(ErrorClassification.FAILED_PRECONDITION, true),
    /** `channel` · NotificationChannelUnavailable */
    NOTIFICATION_CHANNEL_UNAVAILABLE(ErrorClassification.UNAVAILABLE, true),
    /** — · DeviceTokenInvalid */
    DEVICE_TOKEN_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** — · ConfigurationKeyUnknown */
    CONFIGURATION_KEY_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    /** `constraint` · ConfigurationValueInvalid */
    CONFIGURATION_VALUE_INVALID(ErrorClassification.BAD_REQUEST, false),
    /** `currentStatus` · TransactionNotRecoverable */
    TRANSACTION_NOT_RECOVERABLE(ErrorClassification.FAILED_PRECONDITION, false),
    /** — · RecoveryProposalUnknown */
    RECOVERY_PROPOSAL_UNKNOWN(ErrorClassification.NOT_FOUND, false),
    ;

    private final ErrorClassification classification;
    private final boolean retryable;

    ErrorCode(ErrorClassification classification, boolean retryable) {
        this.classification = classification;
        this.retryable = retryable;
    }

    /** The family this refusal belongs to, which drives the default client behaviour. */
    public ErrorClassification classification() {
        return classification;
    }

    /** Whether retrying this same operation could plausibly succeed. */
    public boolean retryable() {
        return retryable;
    }
}
