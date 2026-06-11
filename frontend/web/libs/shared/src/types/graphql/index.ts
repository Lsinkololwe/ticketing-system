export type Maybe<T> = T | null;
export type InputMaybe<T> = T | null;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  BigDecimal: { input: string; output: string; }
  DateTime: { input: string; output: string; }
  JSON: { input: Record<string, unknown>; output: Record<string, unknown>; }
  Long: { input: number; output: number; }
};

export type AcceptInvitationInput = {
  invitationToken: Scalars['String']['input'];
};

/**  Event access grant status */
export type AccessGrantStatus =
  | 'ACTIVE'
  /**  Access revoked */
  | 'EXPIRED'
  /**  Access suspended */
  | 'REVOKED'
  /**  Access is active */
  | 'SUSPENDED';

/**  Account Balance for Trial Balance */
export type AccountBalance = {
  __typename: 'AccountBalance';
  accountCode: Scalars['String']['output'];
  accountName: Scalars['String']['output'];
  accountType: Scalars['String']['output'];
  creditBalance: Scalars['BigDecimal']['output'];
  debitBalance: Scalars['BigDecimal']['output'];
  netBalance: Scalars['BigDecimal']['output'];
};

/**  User account status */
export type AccountStatus =
  | 'ACTIVE'
  /**  Account is active */
  | 'INACTIVE'
  /**  Account is inactive (user-initiated) */
  | 'LOCKED'
  /**  Awaiting email/phone verification */
  | 'PENDING_DELETION'
  /**  Suspended by admin */
  | 'PENDING_VERIFICATION'
  /**  Temporarily locked (security) */
  | 'SUSPENDED';

/**  Detailed account sub-classification */
export type AccountSubType =
  | 'BAD_DEBT'
  | 'BANK_ACCOUNT'
  | 'CHARGEBACK_FEES'
  | 'CHARGEBACK_LOSS'
  | 'CHARGEBACK_RECEIVABLE'
  | 'COMMISSION_RECEIVABLE'
  | 'COMMISSION_REVENUE'
  | 'DEFERRED_REVENUE'
  | 'ESCROW_PAYABLE'
  | 'FEES_PAYABLE'
  | 'FEE_REVENUE'
  | 'GATEWAY_FEES'
  | 'GATEWAY_RECEIVABLE'
  | 'PAYOUTS_PAYABLE'
  | 'REFUNDS_PAYABLE'
  | 'RESERVE'
  | 'RETAINED_EARNINGS'
  | 'TAX_PAYABLE';

/**  ADMIN - Individual escrow account summary for admin dashboard */
export type AccountSummary = {
  __typename: 'AccountSummary';
  accountId: Scalars['ID']['output'];
  accountNumber: Scalars['String']['output'];
  availableForPayout: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  currentBalance: Scalars['BigDecimal']['output'];
  eventId: Scalars['String']['output'];
  eventTitle: Scalars['String']['output'];
  organizerId: Scalars['String']['output'];
  organizerName: Scalars['String']['output'];
  status: EscrowAccountStatus;
  totalCommissions: Scalars['BigDecimal']['output'];
  totalDeposits: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalWithdrawals: Scalars['BigDecimal']['output'];
  transactionCount: Scalars['Int']['output'];
};

/**
 * ============================================================================
 * FINANCIAL ENGINE ENUMS (Double-Entry Bookkeeping)
 * ============================================================================
 * Primary account classification in Chart of Accounts
 */
export type AccountType =
  | 'ASSET'
  | 'EQUITY'
  | 'EXPENSE'
  | 'LIABILITY'
  | 'REVENUE';

export type AdminTicketUpdateInput = {
  buyerEmail: InputMaybe<Scalars['String']['input']>;
  buyerName: InputMaybe<Scalars['String']['input']>;
  buyerPhone: InputMaybe<Scalars['String']['input']>;
  notes: InputMaybe<Scalars['String']['input']>;
  ticketCategoryCode: InputMaybe<Scalars['String']['input']>;
};

/**  Approval action types for timeline tracking */
export type ApprovalAction =
  /**  Reviewer viewed event details */
  | 'APPROVED'
  /**  Organizer submitted event for review */
  | 'ASSIGNED'
  /**  Event rejected (final) */
  | 'CHANGES_REQUESTED'
  /**  Escalation was handled */
  | 'COMMENT_ADDED'
  /**  Organizer resubmitted after changes */
  | 'ESCALATED'
  /**  Auto-escalated due to SLA breach */
  | 'ESCALATION_RESOLVED'
  /**  Event approved */
  | 'REJECTED'
  /**  Reviewer requested changes */
  | 'RESUBMITTED'
  | 'SUBMITTED'
  /**  Event assigned to reviewer */
  | 'VIEWED';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL ESCALATION (Admin Only)
 * ─────────────────────────────────────────────────────────────────────────────
 */
export type ApprovalEscalation = {
  __typename: 'ApprovalEscalation';
  acknowledgedAt: Maybe<Scalars['DateTime']['output']>;
  acknowledgedBy: Maybe<Scalars['String']['output']>;
  acknowledgedByName: Maybe<Scalars['String']['output']>;
  /**  Actors */
  escalatedTo: Scalars['String']['output'];
  /**  User ID of senior admin */
  escalatedToName: Scalars['String']['output'];
  eventId: Scalars['String']['output'];
  eventTitle: Scalars['String']['output'];
  /**  SLA context */
  hoursOverdue: Scalars['Int']['output'];
  id: Scalars['ID']['output'];
  lastReminderAt: Maybe<Scalars['DateTime']['output']>;
  nextReminderAt: Maybe<Scalars['DateTime']['output']>;
  /**  Original assignment */
  originalReviewerId: Maybe<Scalars['String']['output']>;
  originalReviewerName: Maybe<Scalars['String']['output']>;
  reason: Scalars['String']['output'];
  /**  Reminder tracking */
  remindersSent: Scalars['Int']['output'];
  resolutionNotes: Maybe<Scalars['String']['output']>;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  resolvedBy: Maybe<Scalars['String']['output']>;
  resolvedByName: Maybe<Scalars['String']['output']>;
  slaDeadline: Scalars['DateTime']['output'];
  /**  Escalation details */
  status: EscalationStatus;
  /**
   * e.g., "SLA breach - 24 hours overdue"
   * Timing
   */
  triggeredAt: Scalars['DateTime']['output'];
};

/**  Cursor pagination for escalations */
export type ApprovalEscalationConnection = {
  __typename: 'ApprovalEscalationConnection';
  edges: Array<ApprovalEscalationEdge>;
  pageInfo: PageInfo;
  totalCount: Scalars['Int']['output'];
};

export type ApprovalEscalationEdge = {
  __typename: 'ApprovalEscalationEdge';
  cursor: Scalars['String']['output'];
  node: ApprovalEscalation;
};

export type ApprovalEscalationMutationResponse = {
  __typename: 'ApprovalEscalationMutationResponse';
  data: Maybe<ApprovalEscalation>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Offset pagination for escalations */
export type ApprovalEscalationOffsetPage = {
  __typename: 'ApprovalEscalationOffsetPage';
  content: Array<ApprovalEscalation>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL NOTIFICATION (Admin Only)
 * ─────────────────────────────────────────────────────────────────────────────
 */
export type ApprovalNotification = {
  __typename: 'ApprovalNotification';
  actionUrl: Maybe<Scalars['String']['output']>;
  channel: ApprovalNotificationChannel;
  deliveredAt: Maybe<Scalars['DateTime']['output']>;
  eventId: Scalars['String']['output'];
  eventTitle: Scalars['String']['output'];
  failedAt: Maybe<Scalars['DateTime']['output']>;
  failureReason: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  message: Scalars['String']['output'];
  nextRetryAt: Maybe<Scalars['DateTime']['output']>;
  readAt: Maybe<Scalars['DateTime']['output']>;
  recipientEmail: Maybe<Scalars['String']['output']>;
  recipientId: Scalars['String']['output'];
  recipientName: Scalars['String']['output'];
  /**  Retry tracking */
  retryCount: Scalars['Int']['output'];
  /**
   * Deep link to event/approval page
   * Status
   */
  sentAt: Maybe<Scalars['DateTime']['output']>;
  /**  Content */
  subject: Scalars['String']['output'];
  /**  Notification details */
  type: ApprovalNotificationType;
};

/**  Notification channel for approval workflow */
export type ApprovalNotificationChannel =
  | 'BOTH'
  | 'EMAIL'
  | 'IN_APP';

/**  Notification type for approval workflow */
export type ApprovalNotificationType =
  /**  Admin: event assigned to you */
  | 'APPROVAL_GRANTED'
  /**  Organizer: your event was rejected */
  | 'CHANGES_REQUESTED'
  /**  Organizer: changes requested */
  | 'ESCALATION_TRIGGERED'
  /**  Organizer: your event was approved */
  | 'REJECTION_ISSUED'
  /**  Senior Admin: SLA breach escalation */
  | 'REMINDER_PENDING'
  /**  Organizer: your event was submitted */
  | 'REVIEW_ASSIGNED'
  /**  Admin: reminder for pending review */
  | 'SLA_WARNING'
  | 'SUBMISSION_RECEIVED';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL STATISTICS (Admin Only)
 * ─────────────────────────────────────────────────────────────────────────────
 */
export type ApprovalStats = {
  __typename: 'ApprovalStats';
  /**  Escalation metrics */
  activeEscalations: Scalars['Int']['output'];
  approvedToday: Scalars['Int']['output'];
  averageEscalationResolutionHours: Maybe<Scalars['Float']['output']>;
  /**  SLA metrics */
  averageProcessingTimeHours: Scalars['Float']['output'];
  changesRequestedToday: Scalars['Int']['output'];
  escalationsThisWeek: Scalars['Int']['output'];
  /**
   * Percentage approved within SLA
   * Breakdown by status
   */
  pendingByDaysWaiting: Array<DaysWaitingBreakdown>;
  rejectedToday: Scalars['Int']['output'];
  slaComplianceRate: Scalars['Float']['output'];
  /**  Today's metrics */
  submittedToday: Scalars['Int']['output'];
  totalEscalated: Scalars['Int']['output'];
  totalOverdue: Scalars['Int']['output'];
  /**  Volume metrics */
  totalPendingReviews: Scalars['Int']['output'];
};

/**
 * ------------------------------
 * APPROVAL TIMELINE
 * ------------------------------
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL WORKFLOW TYPES (Admin Only)
 * ─────────────────────────────────────────────────────────────────────────────
 * These types track the event approval process. Only platform administrators
 * reviewing and approving events should access this data.
 */
export type ApprovalTimeline = {
  __typename: 'ApprovalTimeline';
  actualApprovalAt: Maybe<Scalars['DateTime']['output']>;
  assignedReviewerId: Maybe<Scalars['String']['output']>;
  assignedReviewerName: Maybe<Scalars['String']['output']>;
  currentIteration: Scalars['Int']['output'];
  /**  Current state */
  currentStatus: EventStatus;
  /**  Escalation tracking */
  escalation: Maybe<ApprovalEscalation>;
  eventId: Scalars['String']['output'];
  eventTitle: Scalars['String']['output'];
  expectedApprovalAt: Maybe<Scalars['DateTime']['output']>;
  hasActiveEscalation: Scalars['Boolean']['output'];
  hoursUntilDeadline: Maybe<Scalars['Int']['output']>;
  isOverdue: Scalars['Boolean']['output'];
  lastActivityAt: Maybe<Scalars['DateTime']['output']>;
  organizerId: Scalars['String']['output'];
  organizerName: Scalars['String']['output'];
  slaCompliancePercentage: Maybe<Scalars['Float']['output']>;
  slaDeadline: Maybe<Scalars['DateTime']['output']>;
  /**  Iteration tracking (for changes requested flow) */
  submissionCount: Scalars['Int']['output'];
  /**  SLA tracking */
  submittedAt: Maybe<Scalars['DateTime']['output']>;
  /**  Timeline events (audit trail) */
  timelineEvents: Array<TimelineEvent>;
  /**  Quick stats */
  totalComments: Scalars['Int']['output'];
  totalProcessingTimeHours: Maybe<Scalars['Int']['output']>;
};

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL WORKFLOW PAGINATION TYPES (Admin Only)
 * ─────────────────────────────────────────────────────────────────────────────
 * Cursor pagination for approval timelines
 */
export type ApprovalTimelineConnection = {
  __typename: 'ApprovalTimelineConnection';
  edges: Array<ApprovalTimelineEdge>;
  pageInfo: PageInfo;
  totalCount: Scalars['Int']['output'];
};

export type ApprovalTimelineEdge = {
  __typename: 'ApprovalTimelineEdge';
  cursor: Scalars['String']['output'];
  node: ApprovalTimeline;
};

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL WORKFLOW INPUT TYPES
 * ─────────────────────────────────────────────────────────────────────────────
 */
export type ApprovalTimelineFilterInput = {
  assignedReviewerId: InputMaybe<Scalars['String']['input']>;
  hasActiveEscalation: InputMaybe<Scalars['Boolean']['input']>;
  isOverdue: InputMaybe<Scalars['Boolean']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  searchQuery: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<EventStatus>;
  submittedAfter: InputMaybe<Scalars['DateTime']['input']>;
  submittedBefore: InputMaybe<Scalars['DateTime']['input']>;
};

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * APPROVAL WORKFLOW MUTATION RESPONSES
 * ─────────────────────────────────────────────────────────────────────────────
 */
export type ApprovalTimelineMutationResponse = {
  __typename: 'ApprovalTimelineMutationResponse';
  data: Maybe<ApprovalTimeline>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Offset pagination for approval timelines */
export type ApprovalTimelineOffsetPage = {
  __typename: 'ApprovalTimelineOffsetPage';
  content: Array<ApprovalTimeline>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

/**  Admin inputs for organization review */
export type ApproveOrganizationInput = {
  commissionRate: InputMaybe<Scalars['Float']['input']>;
  organizationId: Scalars['ID']['input'];
  payoutSchedule: InputMaybe<Scalars['String']['input']>;
  reviewNotes: InputMaybe<Scalars['String']['input']>;
};

export type ApprovePayoutRequestMutationResponse = {
  __typename: 'ApprovePayoutRequestMutationResponse';
  data: Maybe<PayoutRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ApproveRefundRequestMutationResponse = {
  __typename: 'ApproveRefundRequestMutationResponse';
  data: Maybe<RefundRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type AssignReviewerInput = {
  eventId: Scalars['ID']['input'];
  internalNotes: InputMaybe<Scalars['String']['input']>;
  reviewerId: Scalars['String']['input'];
  reviewerName: Scalars['String']['input'];
};

/**
 * ============================================================================
 * SECTION 12: AUTHENTICATION TYPES
 * ============================================================================
 */
export type AuthPayload = {
  __typename: 'AuthPayload';
  accessToken: Maybe<Scalars['String']['output']>;
  expiresIn: Maybe<Scalars['Int']['output']>;
  message: Maybe<Scalars['String']['output']>;
  refreshToken: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  tokenType: Maybe<Scalars['String']['output']>;
  user: Maybe<User>;
};

/**  Normal balance direction for accounts */
export type BalanceDirection =
  | 'CREDIT'
  | 'DEBIT';

/**
 * ------------------------------
 * BANK ACCOUNT
 * ORGANIZER/ADMIN - Bank account details for payout processing
 * Organizers manage their own bank accounts, admins can view/verify all
 * ------------------------------
 */
export type BankAccount = {
  __typename: 'BankAccount';
  /**  Multi-tenant tracking */
  accountHolderName: Scalars['String']['output'];
  accountNumber: Scalars['String']['output'];
  accountType: Maybe<Scalars['String']['output']>;
  bankCode: Maybe<Scalars['String']['output']>;
  bankName: Scalars['String']['output'];
  branchCode: Maybe<Scalars['String']['output']>;
  branchName: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isDefault: Scalars['Boolean']['output'];
  isVerified: Scalars['Boolean']['output'];
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  status: Scalars['String']['output'];
  swiftCode: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedBy: Maybe<Scalars['String']['output']>;
};

export type BankAccountMutationResponse = {
  __typename: 'BankAccountMutationResponse';
  data: Maybe<BankAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type BulkAccessGrantResponse = {
  __typename: 'BulkAccessGrantResponse';
  errors: Maybe<Array<BulkOperationError>>;
  failedCount: Scalars['Int']['output'];
  grantedCount: Scalars['Int']['output'];
  grants: Maybe<Array<EventAccessGrant>>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type BulkApprovalResponse = {
  __typename: 'BulkApprovalResponse';
  errors: Array<Scalars['String']['output']>;
  failedCount: Scalars['Int']['output'];
  message: Maybe<Scalars['String']['output']>;
  processedCount: Scalars['Int']['output'];
  results: Array<EventApprovalResult>;
  success: Scalars['Boolean']['output'];
};

export type BulkGrantEventAccessInput = {
  eventId: Scalars['ID']['input'];
  grants: Array<GrantEventAccessInput>;
  organizationId: Scalars['ID']['input'];
};

export type BulkInvitationResponse = {
  __typename: 'BulkInvitationResponse';
  errors: Maybe<Array<BulkOperationError>>;
  failedCount: Scalars['Int']['output'];
  invitations: Maybe<Array<TeamInvitation>>;
  message: Maybe<Scalars['String']['output']>;
  sentCount: Scalars['Int']['output'];
  success: Scalars['Boolean']['output'];
};

/**  Bulk operations */
export type BulkInviteInput = {
  invites: Array<InviteTeamMemberInput>;
  organizationId: Scalars['ID']['input'];
};

/**  Error type for bulk operations */
export type BulkOperationError = {
  __typename: 'BulkOperationError';
  code: Maybe<Scalars['String']['output']>;
  identifier: Maybe<Scalars['String']['output']>;
  index: Scalars['Int']['output'];
  message: Scalars['String']['output'];
};

export type BulkOperationResponse = {
  __typename: 'BulkOperationResponse';
  errors: Array<Scalars['String']['output']>;
  failedCount: Scalars['Int']['output'];
  message: Maybe<Scalars['String']['output']>;
  processedCount: Scalars['Int']['output'];
  success: Scalars['Boolean']['output'];
};

/**
 * BulkTransactionOperationResponse - REMOVED (use BulkPaymentAttemptOperationResponse)
 * ADMIN - Bulk payout operation response with payout request details
 */
export type BulkPayoutOperationResponse = {
  __typename: 'BulkPayoutOperationResponse';
  errors: Array<Scalars['String']['output']>;
  failedCount: Scalars['Int']['output'];
  failedPayoutIds: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  processedCount: Scalars['Int']['output'];
  processedPayouts: Array<PayoutRequest>;
  success: Scalars['Boolean']['output'];
};

export type BulkReminderResponse = {
  __typename: 'BulkReminderResponse';
  errors: Array<Scalars['String']['output']>;
  failedCount: Scalars['Int']['output'];
  message: Maybe<Scalars['String']['output']>;
  sentCount: Scalars['Int']['output'];
  success: Scalars['Boolean']['output'];
};

/**  Business address for an organization */
export type BusinessAddress = {
  __typename: 'BusinessAddress';
  addressLine1: Maybe<Scalars['String']['output']>;
  addressLine2: Maybe<Scalars['String']['output']>;
  city: Maybe<Scalars['String']['output']>;
  country: Maybe<Scalars['String']['output']>;
  countryCode: Maybe<Scalars['String']['output']>;
  /**  ISO 3166-1 alpha-2 (e.g., ZM for Zambia) */
  formattedAddress: Maybe<Scalars['String']['output']>;
  /**  Province/State */
  postalCode: Maybe<Scalars['String']['output']>;
  province: Maybe<Scalars['String']['output']>;
};

/**  Legal business type for KYB verification */
export type BusinessType =
  | 'GOVERNMENT'
  | 'INDIVIDUAL'
  | 'LIMITED_COMPANY'
  | 'NGO'
  | 'PARTNERSHIP'
  | 'SOLE_PROPRIETORSHIP';

export type CancelTicketMutationResponse = {
  __typename: 'CancelTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type CategoryMutationResponse = {
  __typename: 'CategoryMutationResponse';
  data: Maybe<EventCategory>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ChannelStatus = {
  __typename: 'ChannelStatus';
  channel: NotificationChannel;
  deliveredAt: Maybe<Scalars['DateTime']['output']>;
  errorMessage: Maybe<Scalars['String']['output']>;
  sentAt: Maybe<Scalars['DateTime']['output']>;
  status: NotificationStatus;
};

export type ChargebackFilterInput = {
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  eventId: InputMaybe<Scalars['String']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  recoveryStatus: InputMaybe<RecoveryStatus>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  status: InputMaybe<ChargebackStatus>;
};

/**  Source of chargeback recovery funds */
export type ChargebackFundSource =
  | 'ORGANIZER_ESCROW'
  | 'ORGANIZER_FUTURE'
  | 'PLATFORM_RESERVE'
  | 'WRITE_OFF';

/**  Chargeback mutation response */
export type ChargebackMutationResponse = {
  __typename: 'ChargebackMutationResponse';
  data: Maybe<ChargebackRecord>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ChargebackOffsetPage = {
  __typename: 'ChargebackOffsetPage';
  data: Array<ChargebackRecord>;
  pagination: PaginationInfo;
};

/**  Chargeback reason categories */
export type ChargebackReason =
  | 'CANCELLED'
  | 'DUPLICATE'
  | 'FRAUD'
  | 'NOT_AS_DESCRIBED'
  | 'NOT_RECEIVED'
  | 'OTHER';

/**  Chargeback Record */
export type ChargebackRecord = {
  __typename: 'ChargebackRecord';
  chargebackAmount: Scalars['BigDecimal']['output'];
  chargebackFee: Scalars['BigDecimal']['output'];
  chargebackId: Scalars['String']['output'];
  commissionClawbackId: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
  /**  Multi-tenant tracking */
  customerId: Scalars['String']['output'];
  eventId: Scalars['String']['output'];
  evidenceSubmitted: Maybe<Scalars['String']['output']>;
  fundSource: Maybe<ChargebackFundSource>;
  id: Scalars['ID']['output'];
  journalEntryId: Maybe<Scalars['String']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  originalAmount: Scalars['BigDecimal']['output'];
  originalTransactionId: Scalars['String']['output'];
  reason: ChargebackReason;
  receivedAt: Scalars['DateTime']['output'];
  recoveredAmount: Maybe<Scalars['BigDecimal']['output']>;
  recoveryStatus: RecoveryStatus;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  responseDeadline: Scalars['DateTime']['output'];
  status: ChargebackStatus;
  ticketId: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

/**  Chargeback Statistics */
export type ChargebackStats = {
  __typename: 'ChargebackStats';
  chargebackRate: Scalars['Float']['output'];
  disputedCount: Scalars['Int']['output'];
  lostCount: Scalars['Int']['output'];
  pendingCount: Scalars['Int']['output'];
  recoveredAmount: Scalars['BigDecimal']['output'];
  totalAmount: Scalars['BigDecimal']['output'];
  totalCount: Scalars['Int']['output'];
  winRate: Scalars['Float']['output'];
  wonCount: Scalars['Int']['output'];
  writtenOffAmount: Scalars['BigDecimal']['output'];
};

/**  Chargeback lifecycle status */
export type ChargebackStatus =
  | 'ACCEPTED'
  | 'DISPUTED'
  | 'LOST'
  | 'RECEIVED'
  | 'UNDER_REVIEW'
  | 'WON';

/**
 * ============================================================================
 * SECTION 10b: FINANCIAL ENGINE TYPES (Double-Entry Bookkeeping)
 * ============================================================================
 * Chart of Accounts Entry
 */
export type ChartOfAccountsEntry = {
  __typename: 'ChartOfAccountsEntry';
  accountCode: Scalars['String']['output'];
  accountName: Scalars['String']['output'];
  accountType: AccountType;
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
  description: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  normalBalance: BalanceDirection;
  parentAccountCode: Maybe<Scalars['String']['output']>;
  subType: Maybe<AccountSubType>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

/**
 * ============================================================================
 * FINANCIAL ENGINE MUTATION RESPONSE TYPES
 * ============================================================================
 * Chart of Accounts mutation response
 */
export type ChartOfAccountsMutationResponse = {
  __typename: 'ChartOfAccountsMutationResponse';
  data: Maybe<ChartOfAccountsEntry>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Financial Engine Pagination Types */
export type ChartOfAccountsOffsetPage = {
  __typename: 'ChartOfAccountsOffsetPage';
  data: Array<ChartOfAccountsEntry>;
  pagination: PaginationInfo;
};

/**  ORGANIZER - Individual check-in event for live dashboard feed */
export type CheckInEvent = {
  __typename: 'CheckInEvent';
  buyerName: Maybe<Scalars['String']['output']>;
  checkedInAt: Scalars['DateTime']['output'];
  scannerId: Maybe<Scalars['String']['output']>;
  scannerName: Maybe<Scalars['String']['output']>;
  ticketId: Scalars['ID']['output'];
  ticketNumber: Scalars['String']['output'];
  tierName: Scalars['String']['output'];
  totalCheckedIn: Scalars['Int']['output'];
};

/**
 * ------------------------------
 * CITY
 * ------------------------------
 */
export type City = {
  __typename: 'City';
  code: Maybe<Scalars['String']['output']>;
  country: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  eventCount: Maybe<Scalars['Int']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  province: Maybe<Scalars['String']['output']>;
  provinceId: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedBy: Maybe<Scalars['String']['output']>;
};

export type CityConnection = {
  __typename: 'CityConnection';
  edges: Array<CityEdge>;
  pageInfo: PageInfo;
};

export type CityEdge = {
  __typename: 'CityEdge';
  cursor: Scalars['String']['output'];
  node: City;
};

export type CityMutationResponse = {
  __typename: 'CityMutationResponse';
  data: Maybe<City>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type CityOffsetPage = {
  __typename: 'CityOffsetPage';
  content: Array<City>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

/**  City statistics for event analytics (admin only) */
export type CityStats = {
  __typename: 'CityStats';
  cityId: Maybe<Scalars['String']['output']>;
  cityName: Scalars['String']['output'];
  country: Scalars['String']['output'];
  eventCount: Scalars['Int']['output'];
};

export type CompleteReservationInput = {
  paymentMethod: PaymentMethod;
  phoneNumber: Scalars['String']['input'];
  promoCode: InputMaybe<Scalars['String']['input']>;
  reservationId: Scalars['ID']['input'];
};

export type ConfirmOwnershipTransferInput = {
  confirmationCode: Scalars['String']['input'];
  transferToken: Scalars['String']['input'];
};

export type Coordinates = {
  __typename: 'Coordinates';
  latitude: Maybe<Scalars['Float']['output']>;
  longitude: Maybe<Scalars['Float']['output']>;
};

export type CreateBankAccountInput = {
  accountHolderName: Scalars['String']['input'];
  accountNumber: Scalars['String']['input'];
  accountType: InputMaybe<Scalars['String']['input']>;
  bankCode: InputMaybe<Scalars['String']['input']>;
  bankName: Scalars['String']['input'];
  branchCode: InputMaybe<Scalars['String']['input']>;
  branchName: InputMaybe<Scalars['String']['input']>;
  currency: Scalars['String']['input'];
  isDefault: InputMaybe<Scalars['Boolean']['input']>;
  organizerId: Scalars['String']['input'];
  swiftCode: InputMaybe<Scalars['String']['input']>;
};

/**  Specific bank account mutation response types matching resolver return types */
export type CreateBankAccountMutationResponse = {
  __typename: 'CreateBankAccountMutationResponse';
  data: Maybe<BankAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**
 * ============================================================================
 * FINANCIAL ENGINE INPUT TYPES
 * ============================================================================
 */
export type CreateChartOfAccountsInput = {
  accountCode: Scalars['String']['input'];
  accountName: Scalars['String']['input'];
  accountType: AccountType;
  currency: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  parentAccountCode: InputMaybe<Scalars['String']['input']>;
  subType: InputMaybe<AccountSubType>;
};

export type CreateCityInput = {
  code: Scalars['String']['input'];
  country: Scalars['String']['input'];
  name: Scalars['String']['input'];
  provinceId: Scalars['String']['input'];
};

/**
 * NOTE: ApprovalTimelineOffsetPage is defined above with @tag(name: "admin")
 * ============================================================================
 * SECTION 10: INPUT TYPES
 * ============================================================================
 */
export type CreateCoordinatesInput = {
  latitude: Scalars['Float']['input'];
  longitude: Scalars['Float']['input'];
};

export type CreateEscrowAccountInput = {
  currency: Scalars['String']['input'];
  eventId: Scalars['String']['input'];
  eventTitle: InputMaybe<Scalars['String']['input']>;
  organizerId: Scalars['String']['input'];
  organizerName: InputMaybe<Scalars['String']['input']>;
};

export type CreateEventCategoryInput = {
  code: Scalars['String']['input'];
  color: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  iconUrl: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  sortOrder: InputMaybe<Scalars['Int']['input']>;
};

export type CreateEventInput = {
  accessibility: InputMaybe<EventAccessibilityInput>;
  additionalInfo: InputMaybe<Scalars['JSON']['input']>;
  bannerImageUrl: InputMaybe<Scalars['String']['input']>;
  cancellationPolicy: InputMaybe<Scalars['String']['input']>;
  categoryId: Scalars['String']['input'];
  description: Scalars['String']['input'];
  enableWaitlist: InputMaybe<Scalars['Boolean']['input']>;
  endDateTime: Scalars['DateTime']['input'];
  eventDateTime: Scalars['DateTime']['input'];
  isFreeEvent: InputMaybe<Scalars['Boolean']['input']>;
  isVirtual: InputMaybe<Scalars['Boolean']['input']>;
  location: InputMaybe<EventLocationInput>;
  refundPolicy: InputMaybe<Scalars['String']['input']>;
  tags: InputMaybe<Array<Scalars['String']['input']>>;
  termsAndConditions: InputMaybe<Scalars['String']['input']>;
  ticketTiers: Array<CreateTicketTierInput>;
  title: Scalars['String']['input'];
  totalCapacity: Scalars['Int']['input'];
  virtualEventUrl: InputMaybe<Scalars['String']['input']>;
  waitlistCapacity: InputMaybe<Scalars['Int']['input']>;
};

export type CreateEventReminderInput = {
  channels: InputMaybe<Array<NotificationChannel>>;
  eventId: Scalars['ID']['input'];
  minutesBefore: Scalars['Int']['input'];
  ticketId: Scalars['ID']['input'];
};

export type CreateJournalEntryInput = {
  correlationId: Scalars['String']['input'];
  description: Scalars['String']['input'];
  effectiveDate: InputMaybe<Scalars['DateTime']['input']>;
  entryDate: Scalars['DateTime']['input'];
  lines: Array<JournalLineInput>;
  metadata: InputMaybe<Scalars['JSON']['input']>;
  type: JournalEntryType;
};

export type CreatePayoutRequestInput = {
  bankAccountId: Scalars['String']['input'];
  currency: Scalars['String']['input'];
  escrowAccountId: Scalars['String']['input'];
  eventId: InputMaybe<Scalars['String']['input']>;
  metadata: InputMaybe<Scalars['JSON']['input']>;
  notes: InputMaybe<Scalars['String']['input']>;
  organizerId: Scalars['String']['input'];
  payoutMethod: PayoutMethod;
  requestedAmount: Scalars['BigDecimal']['input'];
};

/**  Specific payout request mutation response types matching resolver return types */
export type CreatePayoutRequestMutationResponse = {
  __typename: 'CreatePayoutRequestMutationResponse';
  data: Maybe<PayoutRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type CreatePromoCodeInput = {
  applicableTiers: InputMaybe<Array<Scalars['String']['input']>>;
  code: Scalars['String']['input'];
  discountType: DiscountType;
  discountValue: Scalars['BigDecimal']['input'];
  eventId: InputMaybe<Scalars['ID']['input']>;
  maxDiscountAmount: InputMaybe<Scalars['BigDecimal']['input']>;
  maxUses: InputMaybe<Scalars['Int']['input']>;
  minPurchaseAmount: InputMaybe<Scalars['BigDecimal']['input']>;
  organizerId: InputMaybe<Scalars['ID']['input']>;
  validFrom: InputMaybe<Scalars['DateTime']['input']>;
  validUntil: InputMaybe<Scalars['DateTime']['input']>;
};

export type CreateProvinceInput = {
  code: Scalars['String']['input'];
  country: Scalars['String']['input'];
  name: Scalars['String']['input'];
};

export type CreateRefundRequestInput = {
  additionalNotes: InputMaybe<Scalars['String']['input']>;
  metadata: InputMaybe<Scalars['JSON']['input']>;
  reason: Scalars['String']['input'];
  requestedBy: Scalars['String']['input'];
  ticketId: Scalars['String']['input'];
};

/**  Specific refund request mutation response types matching resolver return types */
export type CreateRefundRequestMutationResponse = {
  __typename: 'CreateRefundRequestMutationResponse';
  data: Maybe<RefundRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type CreateTicketTierInput = {
  accessCode: InputMaybe<Scalars['String']['input']>;
  benefits: InputMaybe<Array<Scalars['String']['input']>>;
  code: Scalars['String']['input'];
  currency: Scalars['String']['input'];
  description: InputMaybe<Scalars['String']['input']>;
  earlyBirdEndsAt: InputMaybe<Scalars['DateTime']['input']>;
  earlyBirdPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  isHidden: InputMaybe<Scalars['Boolean']['input']>;
  maxPerOrder: InputMaybe<Scalars['Int']['input']>;
  minPerOrder: InputMaybe<Scalars['Int']['input']>;
  name: Scalars['String']['input'];
  price: Scalars['BigDecimal']['input'];
  quantity: Scalars['Int']['input'];
  salesEndAt: InputMaybe<Scalars['DateTime']['input']>;
  salesStartAt: InputMaybe<Scalars['DateTime']['input']>;
  sortOrder: InputMaybe<Scalars['Int']['input']>;
};

/**
 * ============================================================================
 * SECTION 15: INPUT TYPES - USER & PROFILE
 * ============================================================================
 */
export type CreateUserInput = {
  email: Scalars['String']['input'];
  firstName: Scalars['String']['input'];
  lastName: Scalars['String']['input'];
  password: InputMaybe<Scalars['String']['input']>;
  phoneNumber: InputMaybe<Scalars['String']['input']>;
};

/**  Cursor-based pagination input (Relay style) for mobile/infinite scroll */
export type CursorPaginationInput = {
  after: InputMaybe<Scalars['String']['input']>;
  before: InputMaybe<Scalars['String']['input']>;
  first: InputMaybe<Scalars['Int']['input']>;
  last: InputMaybe<Scalars['Int']['input']>;
};

export type DaysWaitingBreakdown = {
  __typename: 'DaysWaitingBreakdown';
  /**  0, 1, 2, 3+ */
  count: Scalars['Int']['output'];
  daysWaiting: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
};

export type DeleteBankAccountMutationResponse = {
  __typename: 'DeleteBankAccountMutationResponse';
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Shared type - must have @shareable on all fields for federation */
export type DeleteMutationResponse = {
  __typename: 'DeleteMutationResponse';
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type DeviceMutationResponse = {
  __typename: 'DeviceMutationResponse';
  device: Maybe<UserDevice>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Device platform */
export type DevicePlatform =
  /**  iOS device */
  | 'ANDROID'
  | 'IOS'
  /**  Android device */
  | 'WEB';

/**  Promo code discount type */
export type DiscountType =
  | 'FIXED_AMOUNT'
  | 'PERCENTAGE';

export type DisputeChargebackInput = {
  additionalDocuments: InputMaybe<Scalars['String']['input']>;
  customerCommunicationLog: InputMaybe<Scalars['String']['input']>;
  deliveryConfirmation: InputMaybe<Scalars['String']['input']>;
  notes: Scalars['String']['input'];
  termsAcceptanceProof: InputMaybe<Scalars['String']['input']>;
  ticketValidationProof: InputMaybe<Scalars['String']['input']>;
};

/**  Document responses */
export type DocumentMutationResponse = {
  __typename: 'DocumentMutationResponse';
  document: Maybe<VerificationDocument>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type DocumentStatus =
  /**  Uploaded, awaiting review */
  | 'APPROVED'
  /**  Document rejected */
  | 'EXPIRED'
  | 'PENDING'
  /**  Document verified */
  | 'REJECTED';

/**  Effective permissions for a user in a context */
export type EffectivePermissions = {
  __typename: 'EffectivePermissions';
  eventId: Maybe<Scalars['ID']['output']>;
  organizationId: Maybe<Scalars['ID']['output']>;
  permissions: Array<Scalars['String']['output']>;
  /**  List of permission codes */
  role: Maybe<Scalars['String']['output']>;
  /**  Applicable role */
  source: Maybe<Scalars['String']['output']>;
  userId: Scalars['ID']['output'];
};

export type ErrorDetail =
  /**
   * The deadline expired before the operation could complete.
   *
   * For operations that change the state of the system, this error
   * may be returned even if the operation has completed successfully.
   * For example, a successful response from a server could have been
   * delayed long enough for the deadline to expire.
   *
   * HTTP Mapping: 504 Gateway Timeout
   * Error Type: UNAVAILABLE
   */
  | 'DEADLINE_EXCEEDED'
  /**
   * The server detected that the client is exhibiting a behavior that
   * might be generating excessive load.
   *
   * HTTP Mapping: 420 Enhance Your Calm
   * Error Type: UNAVAILABLE
   */
  | 'ENHANCE_YOUR_CALM'
  /**
   * The requested field is not found in the schema.
   *
   * This differs from `NOT_FOUND` in that `NOT_FOUND` should be used when a
   * query is valid, but is unable to return a result (if, for example, a
   * specific video id doesn't exist). `FIELD_NOT_FOUND` is intended to be
   * returned by the server to signify that the requested field is not known to exist.
   * This may be returned in lieu of failing the entire query.
   * See also `PERMISSION_DENIED` for cases where the
   * requested field is invalid only for the given user or class of users.
   *
   * HTTP Mapping: 404 Not Found
   * Error Type: BAD_REQUEST
   */
  | 'FIELD_NOT_FOUND'
  /**
   * The client specified an invalid argument.
   *
   * Note that this differs from `FAILED_PRECONDITION`.
   * `INVALID_ARGUMENT` indicates arguments that are problematic
   * regardless of the state of the system (e.g., a malformed file name).
   *
   * HTTP Mapping: 400 Bad Request
   * Error Type: BAD_REQUEST
   */
  | 'INVALID_ARGUMENT'
  /**
   * The provided cursor is not valid.
   *
   * The most common usage for this error is when a client is paginating
   * through a list that uses stateful cursors. In that case, the provided
   * cursor may be expired.
   *
   * HTTP Mapping: 404 Not Found
   * Error Type: NOT_FOUND
   */
  | 'INVALID_CURSOR'
  /**
   * Unable to perform operation because a required resource is missing.
   *
   * Example: Client is attempting to refresh a list, but the specified
   * list is expired. This requires an action by the client to get a new list.
   *
   * If the user is simply trying GET a resource that is not found,
   * use the NOT_FOUND error type. FAILED_PRECONDITION.MISSING_RESOURCE
   * is to be used particularly when the user is performing an operation
   * that requires a particular resource to exist.
   *
   * HTTP Mapping: 400 Bad Request or 500 Internal Server Error
   * Error Type: FAILED_PRECONDITION
   */
  | 'MISSING_RESOURCE'
  /**
   * Service Error.
   *
   * There is a problem with an upstream service.
   *
   * This may be returned if a gateway receives an unknown error from a service
   * or if a service is unreachable.
   * If a request times out which waiting on a response from a service,
   * `DEADLINE_EXCEEDED` may be returned instead.
   * If a service returns a more specific error Type, the specific error Type may
   * be returned instead.
   *
   * HTTP Mapping: 502 Bad Gateway
   * Error Type: UNAVAILABLE
   */
  | 'SERVICE_ERROR'
  /**
   * Request failed due to network errors.
   *
   * HTTP Mapping: 503 Unavailable
   * Error Type: UNAVAILABLE
   */
  | 'TCP_FAILURE'
  /**
   * Request throttled based on server concurrency limits.
   *
   * HTTP Mapping: 503 Unavailable
   * Error Type: UNAVAILABLE
   */
  | 'THROTTLED_CONCURRENCY'
  /**
   * Request throttled based on server CPU limits
   *
   * HTTP Mapping: 503 Unavailable.
   * Error Type: UNAVAILABLE
   */
  | 'THROTTLED_CPU'
  /**
   * The server detected that the client is exhibiting a behavior that
   * might be generating excessive load.
   *
   * HTTP Mapping: 429 Too Many Requests
   * Error Type: UNAVAILABLE
   */
  | 'TOO_MANY_REQUESTS'
  /**
   * The operation is not implemented or is not currently supported/enabled.
   *
   * HTTP Mapping: 501 Not Implemented
   * Error Type: BAD_REQUEST
   */
  | 'UNIMPLEMENTED'
  /**
   * Unknown error.
   *
   * This error should only be returned when no other error detail applies.
   * If a client sees an unknown errorDetail, it will be interpreted as UNKNOWN.
   *
   * HTTP Mapping: 500 Internal Server Error
   */
  | 'UNKNOWN';

export type ErrorType =
  /**
   * Bad Request.
   *
   * There is a problem with the request.
   * Retrying the same request is not likely to succeed.
   * An example would be a query or argument that cannot be deserialized.
   *
   * HTTP Mapping: 400 Bad Request
   */
  | 'BAD_REQUEST'
  /**
   * The operation was rejected because the system is not in a state
   * required for the operation's execution.  For example, the directory
   * to be deleted is non-empty, an rmdir operation is applied to
   * a non-directory, etc.
   *
   * Service implementers can use the following guidelines to decide
   * between `FAILED_PRECONDITION` and `UNAVAILABLE`:
   *
   * - Use `UNAVAILABLE` if the client can retry just the failing call.
   * - Use `FAILED_PRECONDITION` if the client should not retry until
   * the system state has been explicitly fixed.  E.g., if an "rmdir"
   *      fails because the directory is non-empty, `FAILED_PRECONDITION`
   * should be returned since the client should not retry unless
   * the files are deleted from the directory.
   *
   * HTTP Mapping: 400 Bad Request or 500 Internal Server Error
   */
  | 'FAILED_PRECONDITION'
  /**
   * Internal error.
   *
   * An unexpected internal error was encountered. This means that some
   * invariants expected by the underlying system have been broken.
   * This error code is reserved for serious errors.
   *
   * HTTP Mapping: 500 Internal Server Error
   */
  | 'INTERNAL'
  /**
   * The requested entity was not found.
   *
   * This could apply to a resource that has never existed (e.g. bad resource id),
   * or a resource that no longer exists (e.g. cache expired.)
   *
   * Note to server developers: if a request is denied for an entire class
   * of users, such as gradual feature rollout or undocumented allowlist,
   * `NOT_FOUND` may be used. If a request is denied for some users within
   * a class of users, such as user-based access control, `PERMISSION_DENIED`
   * must be used.
   *
   * HTTP Mapping: 404 Not Found
   */
  | 'NOT_FOUND'
  /**
   * The caller does not have permission to execute the specified
   * operation.
   *
   * `PERMISSION_DENIED` must not be used for rejections
   * caused by exhausting some resource or quota.
   * `PERMISSION_DENIED` must not be used if the caller
   * cannot be identified (use `UNAUTHENTICATED`
   * instead for those errors).
   *
   * This error Type does not imply the
   * request is valid or the requested entity exists or satisfies
   * other pre-conditions.
   *
   * HTTP Mapping: 403 Forbidden
   */
  | 'PERMISSION_DENIED'
  /**
   * The request does not have valid authentication credentials.
   *
   * This is intended to be returned only for routes that require
   * authentication.
   *
   * HTTP Mapping: 401 Unauthorized
   */
  | 'UNAUTHENTICATED'
  /**
   * Currently Unavailable.
   *
   * The service is currently unavailable.  This is most likely a
   * transient condition, which can be corrected by retrying with
   * a backoff.
   *
   * HTTP Mapping: 503 Unavailable
   */
  | 'UNAVAILABLE'
  /**
   * Unknown error.
   *
   * For example, this error may be returned when
   * an error code received from another address space belongs to
   * an error space that is not known in this address space.  Also
   * errors raised by APIs that do not return enough error information
   * may be converted to this error.
   *
   * If a client sees an unknown errorType, it will be interpreted as UNKNOWN.
   * Unknown errors MUST NOT trigger any special behavior. These MAY be treated
   * by an implementation as being equivalent to INTERNAL.
   *
   * When possible, a more specific error should be provided.
   *
   * HTTP Mapping: 520 Unknown Error
   */
  | 'UNKNOWN';

/**  Escalation status */
export type EscalationStatus =
  /**  Escalation triggered, not yet handled */
  | 'ACKNOWLEDGED'
  /**  Escalation resolved */
  | 'EXPIRED'
  | 'PENDING'
  /**  Senior admin acknowledged */
  | 'RESOLVED';

/**  Escrow Account cursor pagination */
export type EscrowAccountConnection = {
  __typename: 'EscrowAccountConnection';
  edges: Array<EscrowAccountEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type EscrowAccountEdge = {
  __typename: 'EscrowAccountEdge';
  cursor: Scalars['String']['output'];
  node: EventEscrowAccount;
};

/**  TransactionFilterInput - REMOVED (use PaymentAttempt queries instead) */
export type EscrowAccountFilterInput = {
  currency: InputMaybe<Scalars['String']['input']>;
  eventId: InputMaybe<Scalars['String']['input']>;
  hasBalance: InputMaybe<Scalars['Boolean']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<EscrowAccountStatus>;
};

export type EscrowAccountMutationResponse = {
  __typename: 'EscrowAccountMutationResponse';
  data: Maybe<EventEscrowAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type EscrowAccountOffsetPage = {
  __typename: 'EscrowAccountOffsetPage';
  data: Array<EventEscrowAccount>;
  pagination: PaginationInfo;
};

/**  Escrow account status */
export type EscrowAccountStatus =
  | 'ACTIVE'
  | 'CANCELLED'
  | 'CLOSED'
  | 'CREATED'
  | 'LOCKED'
  | 'PAYOUT_ELIGIBLE'
  | 'PROCESSING_PAYOUT';

/**
 * Escrow-Journal cross-verification response
 * Compares EventEscrowAccount balance with Journal Entry calculated balance
 */
export type EscrowJournalVerificationResponse = {
  __typename: 'EscrowJournalVerificationResponse';
  details: Array<Scalars['String']['output']>;
  errors: Array<Scalars['String']['output']>;
  escrowAccountId: Maybe<Scalars['String']['output']>;
  escrowBalance: Maybe<Scalars['BigDecimal']['output']>;
  eventId: Scalars['String']['output'];
  isConsistent: Scalars['Boolean']['output'];
  journalAccountCode: Maybe<Scalars['String']['output']>;
  journalBalance: Maybe<Scalars['BigDecimal']['output']>;
  message: Maybe<Scalars['String']['output']>;
  status: Maybe<EscrowJournalVerificationStatus>;
  success: Scalars['Boolean']['output'];
  variance: Maybe<Scalars['BigDecimal']['output']>;
};

/**  Escrow-Journal verification status */
export type EscrowJournalVerificationStatus =
  /**  Balances match */
  | 'BALANCE_MISMATCH'
  | 'CONSISTENT'
  /**  Both exist but balances don't match */
  | 'MISSING_JOURNAL_ACCOUNT'
  /**  Journal account exists but no escrow */
  | 'NOT_FOUND'
  /**  Escrow exists but no journal account */
  | 'ORPHANED_JOURNAL_ACCOUNT';

/**  Escrow Transaction mutation response */
export type EscrowTransactionMutationResponse = {
  __typename: 'EscrowTransactionMutationResponse';
  data: Maybe<StandaloneEscrowTransaction>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type EscrowTransactionOffsetPage = {
  __typename: 'EscrowTransactionOffsetPage';
  data: Array<StandaloneEscrowTransaction>;
  pagination: PaginationInfo;
};

/**  Event stub - owned by Catalog Service */
export type Event = {
  __typename: 'Event';
  /**  Accessibility metadata */
  accessibility: Maybe<EventAccessibility>;
  additionalInfo: Maybe<Scalars['JSON']['output']>;
  approvalDeadline: Maybe<Scalars['DateTime']['output']>;
  approvedAt: Maybe<Scalars['DateTime']['output']>;
  approvedBy: Maybe<Scalars['String']['output']>;
  availableTickets: Scalars['Int']['output'];
  /**  Media and branding */
  bannerImageUrl: Maybe<Scalars['String']['output']>;
  cancellationPolicy: Maybe<Scalars['String']['output']>;
  category: Maybe<EventCategory>;
  categoryId: Maybe<Scalars['String']['output']>;
  cityName: Maybe<Scalars['String']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────────
   * AUDIT FIELDS - For tracking changes (internal/admin use)
   * ─────────────────────────────────────────────────────────────────────────
   * Timestamps are useful for organizers to see when event was created/updated
   */
  createdAt: Maybe<Scalars['DateTime']['output']>;
  /**  Actor tracking is internal - used for audit logs */
  createdBy: Maybe<Scalars['String']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  description: Scalars['String']['output'];
  endDateTime: Scalars['DateTime']['output'];
  eventDateTime: Scalars['DateTime']['output'];
  /**  Flags */
  featured: Scalars['Boolean']['output'];
  galleryImages: Maybe<Array<Scalars['String']['output']>>;
  hasWaitlist: Scalars['Boolean']['output'];
  id: Scalars['ID']['output'];
  /**  Active flag is needed by organizers to see soft-deleted events */
  isActive: Scalars['Boolean']['output'];
  isFreeEvent: Scalars['Boolean']['output'];
  /**  Organizer needs to see why rejected */
  isOverdue: Maybe<Scalars['Boolean']['output']>;
  /**  Recurring event support */
  isRecurring: Scalars['Boolean']['output'];
  /**  Virtual event support */
  isVirtual: Scalars['Boolean']['output'];
  /**  Location information - PUBLIC (where is the event?) */
  location: Maybe<Location>;
  locationAddress: Maybe<Scalars['String']['output']>;
  locationId: Maybe<Scalars['String']['output']>;
  locationName: Maybe<Scalars['String']['output']>;
  maxTicketPrice: Maybe<Scalars['BigDecimal']['output']>;
  /**  Pricing info (denormalized for filtering) */
  minTicketPrice: Maybe<Scalars['BigDecimal']['output']>;
  /**
   * Organization that owns this event (for team-based access control)
   * Team members of this organization can manage this event based on their roles
   */
  organization: Maybe<Organization>;
  organizationId: Maybe<Scalars['String']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────────
   * ORGANIZER FIELDS - For organizer dashboards and admin portals
   * These fields identify who created the event and their contact info
   * ─────────────────────────────────────────────────────────────────────────
   * Organizer reference - needed by organizer dashboard and admin
   */
  organizer: Maybe<User>;
  organizerBusinessEmail: Maybe<Scalars['String']['output']>;
  organizerBusinessPhone: Maybe<Scalars['String']['output']>;
  organizerCompanyName: Maybe<Scalars['String']['output']>;
  /**  Contact info - private, only for organizer dashboard and admin */
  organizerEmail: Maybe<Scalars['String']['output']>;
  organizerFirstName: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  organizerLastName: Maybe<Scalars['String']['output']>;
  /**  Public organizer name for attribution on event pages */
  organizerName: Scalars['String']['output'];
  organizerPhone: Maybe<Scalars['String']['output']>;
  parentEventId: Maybe<Scalars['String']['output']>;
  published: Scalars['Boolean']['output'];
  publishedAt: Maybe<Scalars['DateTime']['output']>;
  recurrencePattern: Maybe<Scalars['String']['output']>;
  /**  Refund/cancellation policies */
  refundPolicy: Maybe<Scalars['String']['output']>;
  rejectedAt: Maybe<Scalars['DateTime']['output']>;
  rejectedBy: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  revenue: Scalars['BigDecimal']['output'];
  /**  ORGANIZER/ADMIN - Sales percentage for performance tracking */
  salesPercentage: Scalars['Float']['output'];
  soldOut: Scalars['Boolean']['output'];
  soldTickets: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────────
   * STATUS FIELDS - Event lifecycle management
   * Status is public (users see "PUBLISHED"), management fields are restricted
   * ─────────────────────────────────────────────────────────────────────────
   * Event status - public so consumers know if event is live
   */
  status: EventStatus;
  /**
   * ─────────────────────────────────────────────────────────────────────────
   * ADMIN-ONLY FIELDS - Approval workflow (platform administrators only)
   * These fields track the approval process and are not shown to organizers
   * until after approval/rejection
   * ─────────────────────────────────────────────────────────────────────────
   */
  submittedForApprovalAt: Maybe<Scalars['DateTime']['output']>;
  tags: Maybe<Array<Scalars['String']['output']>>;
  termsAndConditions: Maybe<Scalars['String']['output']>;
  thumbnailImageUrl: Maybe<Scalars['String']['output']>;
  ticketTiers: Maybe<Array<TicketTier>>;
  /**  ORGANIZER/ADMIN - Full ticket list for event management */
  tickets: Array<Ticket>;
  /**  PUBLIC - Availability info displayed on event listings */
  ticketsAvailable: Scalars['Int']['output'];
  /**  ORGANIZER/ADMIN - Sales metrics for dashboard */
  ticketsSold: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────────
   * PUBLIC FIELDS - Available to all clients (mobile, web, admin)
   * These fields are needed for event discovery and display
   * ─────────────────────────────────────────────────────────────────────────
   * Basic event information - PUBLIC
   */
  title: Scalars['String']['output'];
  /**  Capacity and tickets */
  totalCapacity: Scalars['Int']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedBy: Maybe<Scalars['String']['output']>;
  /**  Version field is for internal optimistic locking only */
  version: Maybe<Scalars['Int']['output']>;
  virtualEventPlatform: Maybe<Scalars['String']['output']>;
  virtualEventUrl: Maybe<Scalars['String']['output']>;
  waitlistCapacity: Maybe<Scalars['Int']['output']>;
  waitlistEnabled: Scalars['Boolean']['output'];
};

/**
 * Event-level access assignment
 * Allows granting specific users access to specific events
 * Overrides organization-level role for that event
 * ORGANIZER - Event access management for team
 */
export type EventAccessGrant = {
  __typename: 'EventAccessGrant';
  createdAt: Scalars['DateTime']['output'];
  /**  Custom permissions for this event */
  customPermissions: Maybe<Array<Scalars['String']['output']>>;
  eventId: Scalars['ID']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Event Role
   * ─────────────────────────────────────────────────────────────────────
   */
  eventRole: EventRole;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Timestamps
   * ─────────────────────────────────────────────────────────────────────
   */
  grantedAt: Scalars['DateTime']['output'];
  grantedBy: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Assignment Details
   * ─────────────────────────────────────────────────────────────────────
   */
  grantedById: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  organization: Maybe<Organization>;
  /**  References Event in Catalog service */
  organizationId: Scalars['ID']['output'];
  reason: Maybe<Scalars['String']['output']>;
  revocationReason: Maybe<Scalars['String']['output']>;
  revokedAt: Maybe<Scalars['DateTime']['output']>;
  revokedBy: Maybe<User>;
  /**
   * Optional time-limited access
   * ─────────────────────────────────────────────────────────────────────
   * Revocation (if revoked)
   * ─────────────────────────────────────────────────────────────────────
   */
  revokedById: Maybe<Scalars['ID']['output']>;
  /**
   * Why access was granted
   * ─────────────────────────────────────────────────────────────────────
   * Status & Expiry
   * ─────────────────────────────────────────────────────────────────────
   */
  status: AccessGrantStatus;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  user: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Links
   * ─────────────────────────────────────────────────────────────────────
   */
  userId: Scalars['ID']['output'];
};

/**  EventAccessGrant cursor pagination */
export type EventAccessGrantConnection = {
  __typename: 'EventAccessGrantConnection';
  edges: Array<EventAccessGrantEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type EventAccessGrantEdge = {
  __typename: 'EventAccessGrantEdge';
  cursor: Scalars['String']['output'];
  node: EventAccessGrant;
};

export type EventAccessGrantInput = {
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  eventId: Scalars['ID']['input'];
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  reason: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
};

export type EventAccessGrantOffsetPage = {
  __typename: 'EventAccessGrantOffsetPage';
  content: Array<EventAccessGrant>;
  pageInfo: PageInfo;
};

export type EventAccessInput = {
  eventId: Scalars['ID']['input'];
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  role: EventRole;
};

/**  Event access responses */
export type EventAccessMutationResponse = {
  __typename: 'EventAccessMutationResponse';
  accessGrant: Maybe<EventAccessGrant>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**
 * Proposed event access in an invitation (output type)
 * Mirrors EventAccessInput but for reading, not writing
 * ORGANIZER - Part of team invitation workflow
 */
export type EventAccessProposal = {
  __typename: 'EventAccessProposal';
  eventId: Scalars['ID']['output'];
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  role: EventRole;
};

/**
 * ------------------------------
 * EVENT ACCESSIBILITY
 * ------------------------------
 */
export type EventAccessibility = {
  __typename: 'EventAccessibility';
  accessibleParking: Scalars['Boolean']['output'];
  accessibleRestrooms: Scalars['Boolean']['output'];
  additionalNotes: Maybe<Scalars['String']['output']>;
  assistanceDogsAllowed: Scalars['Boolean']['output'];
  hearingLoopAvailable: Scalars['Boolean']['output'];
  signLanguageInterpreter: Scalars['Boolean']['output'];
  wheelchairAccessible: Scalars['Boolean']['output'];
  wheelchairSeatsAvailable: Maybe<Scalars['Int']['output']>;
};

export type EventAccessibilityInput = {
  accessibleParking: InputMaybe<Scalars['Boolean']['input']>;
  accessibleRestrooms: InputMaybe<Scalars['Boolean']['input']>;
  additionalNotes: InputMaybe<Scalars['String']['input']>;
  assistanceDogsAllowed: InputMaybe<Scalars['Boolean']['input']>;
  hearingLoopAvailable: InputMaybe<Scalars['Boolean']['input']>;
  signLanguageInterpreter: InputMaybe<Scalars['Boolean']['input']>;
  wheelchairAccessible: InputMaybe<Scalars['Boolean']['input']>;
  wheelchairSeatsAvailable: InputMaybe<Scalars['Int']['input']>;
};

export type EventApprovalResult = {
  __typename: 'EventApprovalResult';
  eventId: Scalars['ID']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  newStatus: Maybe<EventStatus>;
  success: Scalars['Boolean']['output'];
};

/**
 * Catalog-specific event cancellation (used by catalog mutations)
 * Note: Booking service has its own EventCancellationInput with eventId
 */
export type EventCancellationInput = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  notifyAttendees: InputMaybe<Scalars['Boolean']['input']>;
  notifyBuyers: InputMaybe<Scalars['Boolean']['input']>;
  processRefundsImmediately: InputMaybe<Scalars['Boolean']['input']>;
  reason: Scalars['String']['input'];
  triggerRefunds: InputMaybe<Scalars['Boolean']['input']>;
};

export type EventCancellationResponse = {
  __typename: 'EventCancellationResponse';
  errors: Array<Scalars['String']['output']>;
  event: Maybe<Event>;
  message: Maybe<Scalars['String']['output']>;
  refundSagaInitiated: Scalars['Boolean']['output'];
  sagaId: Maybe<Scalars['ID']['output']>;
  success: Scalars['Boolean']['output'];
  ticketsAffected: Scalars['Int']['output'];
};

/**
 * ------------------------------
 * EVENT CATEGORY
 * ------------------------------
 */
export type EventCategory = {
  __typename: 'EventCategory';
  code: Scalars['String']['output'];
  color: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  description: Maybe<Scalars['String']['output']>;
  eventCount: Maybe<Scalars['Int']['output']>;
  iconUrl: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  sortOrder: Maybe<Scalars['Int']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type EventCategoryConnection = {
  __typename: 'EventCategoryConnection';
  edges: Array<EventCategoryEdge>;
  pageInfo: PageInfo;
};

export type EventCategoryEdge = {
  __typename: 'EventCategoryEdge';
  cursor: Scalars['String']['output'];
  node: EventCategory;
};

export type EventCategoryOffsetPage = {
  __typename: 'EventCategoryOffsetPage';
  content: Array<EventCategory>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

export type EventCategoryStats = {
  __typename: 'EventCategoryStats';
  category: Scalars['String']['output'];
  categoryId: Scalars['String']['output'];
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  totalCapacity: Scalars['Int']['output'];
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
  totalSoldTickets: Scalars['Int']['output'];
};

export type EventConnection = {
  __typename: 'EventConnection';
  edges: Array<EventEdge>;
  pageInfo: PageInfo;
};

/**  Event discovery filter (combined filter for buyers) */
export type EventDiscoveryFilterInput = {
  categoryId: InputMaybe<Scalars['String']['input']>;
  categoryIds: InputMaybe<Array<Scalars['String']['input']>>;
  cityId: InputMaybe<Scalars['String']['input']>;
  cityName: InputMaybe<Scalars['String']['input']>;
  country: InputMaybe<Scalars['String']['input']>;
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  hasAvailableTickets: InputMaybe<Scalars['Boolean']['input']>;
  isAccessible: InputMaybe<Scalars['Boolean']['input']>;
  isFeatured: InputMaybe<Scalars['Boolean']['input']>;
  isFreeEvent: InputMaybe<Scalars['Boolean']['input']>;
  isVirtual: InputMaybe<Scalars['Boolean']['input']>;
  maxPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  minPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  provinceId: InputMaybe<Scalars['String']['input']>;
  searchQuery: InputMaybe<Scalars['String']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  tags: InputMaybe<Array<Scalars['String']['input']>>;
};

export type EventEdge = {
  __typename: 'EventEdge';
  cursor: Scalars['String']['output'];
  node: Event;
};

/**
 * ------------------------------
 * EVENT ESCROW ACCOUNT
 * ADMIN-ONLY - Platform escrow management for event revenue holding
 * Escrow accounts hold ticket revenue until event completion
 * ------------------------------
 */
export type EventEscrowAccount = {
  __typename: 'EventEscrowAccount';
  accountNumber: Scalars['String']['output'];
  closedAt: Maybe<Scalars['DateTime']['output']>;
  closedReason: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  currentBalance: Scalars['BigDecimal']['output'];
  eventId: Scalars['String']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  lockReason: Maybe<Scalars['String']['output']>;
  lockUntil: Maybe<Scalars['DateTime']['output']>;
  organizerId: Scalars['String']['output'];
  organizerName: Maybe<Scalars['String']['output']>;
  payoutEligibleAt: Maybe<Scalars['DateTime']['output']>;
  pendingWithdrawals: Maybe<Scalars['BigDecimal']['output']>;
  status: EscrowAccountStatus;
  totalCommissions: Scalars['BigDecimal']['output'];
  totalDeposits: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalWithdrawals: Scalars['BigDecimal']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

/**  Admin event filter */
export type EventFilterInput = {
  approvedNotPublished: InputMaybe<Scalars['Boolean']['input']>;
  categoryId: InputMaybe<Scalars['String']['input']>;
  cityId: InputMaybe<Scalars['String']['input']>;
  country: InputMaybe<Scalars['String']['input']>;
  createdAfter: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore: InputMaybe<Scalars['DateTime']['input']>;
  daysSinceApprovalMax: InputMaybe<Scalars['Int']['input']>;
  daysSinceApprovalMin: InputMaybe<Scalars['Int']['input']>;
  eventDateAfter: InputMaybe<Scalars['DateTime']['input']>;
  eventDateBefore: InputMaybe<Scalars['DateTime']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  overdue: InputMaybe<Scalars['Boolean']['input']>;
  published: InputMaybe<Scalars['Boolean']['input']>;
  searchQuery: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<EventStatus>;
  statuses: InputMaybe<Array<EventStatus>>;
};

/**  ORGANIZER/ADMIN - Event-level financial summary */
export type EventFinancialSummary = {
  __typename: 'EventFinancialSummary';
  escrowBalance: Scalars['BigDecimal']['output'];
  eventId: Scalars['ID']['output'];
  eventTitle: Scalars['String']['output'];
  payoutStatus: Scalars['String']['output'];
  totalCommissions: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalRevenue: Scalars['BigDecimal']['output'];
};

/**
 * ------------------------------
 * EVENT LIFECYCLE
 * ------------------------------
 * Event lifecycle tracking is useful for organizers managing their events
 * and admins monitoring the platform. Not needed by mobile consumers.
 */
export type EventLifecycle = {
  __typename: 'EventLifecycle';
  allowedTransitions: Maybe<Array<EventStatus>>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  currentStatus: EventStatus;
  eventId: Scalars['String']['output'];
  lastStatusChange: Maybe<Scalars['DateTime']['output']>;
  /**  Internal audit */
  statusTransitions: Maybe<Array<StatusTransition>>;
};

/**  Event location input - simplified address information for events */
export type EventLocationInput = {
  address: Scalars['String']['input'];
  city: Scalars['String']['input'];
  coordinates: InputMaybe<CreateCoordinatesInput>;
  country: Scalars['String']['input'];
  description: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  postalCode: InputMaybe<Scalars['String']['input']>;
  province: InputMaybe<Scalars['String']['input']>;
};

/**
 * ============================================================================
 * SECTION 11: MUTATION RESPONSE TYPES (Consolidated)
 * ============================================================================
 */
export type EventMutationResponse = {
  __typename: 'EventMutationResponse';
  data: Maybe<Event>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type EventOffsetPage = {
  __typename: 'EventOffsetPage';
  content: Array<Event>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

export type EventOrganizerStats = {
  __typename: 'EventOrganizerStats';
  eventCount: Scalars['Int']['output'];
  organizerId: Scalars['String']['output'];
  organizerName: Scalars['String']['output'];
  totalCapacity: Scalars['Int']['output'];
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
  totalSoldTickets: Scalars['Int']['output'];
};

export type EventReminder = {
  __typename: 'EventReminder';
  /**  Delivery */
  channels: Maybe<Array<NotificationChannel>>;
  /**  Audit */
  createdAt: Scalars['DateTime']['output'];
  errorMessage: Maybe<Scalars['String']['output']>;
  eventDateTime: Maybe<Scalars['DateTime']['output']>;
  eventId: Scalars['ID']['output'];
  /**  Reminder details */
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  minutesBefore: Scalars['Int']['output'];
  reminderAt: Scalars['DateTime']['output'];
  sentAt: Maybe<Scalars['DateTime']['output']>;
  /**  Status */
  status: ReminderStatus;
  ticketId: Scalars['ID']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
};

/**  Event-level roles (override organization roles for specific events) */
export type EventRole =
  /**
   * CHECK_IN: Venue staff for scanning tickets
   * - Can scan/validate tickets
   * - Can view attendee list
   * - Cannot edit anything
   */
  | 'CHECK_IN'
  /**
   * EDITOR: Can edit event content
   * - Can edit event details (title, description, etc.)
   * - Can manage ticket types
   * - Cannot issue refunds
   * - Cannot assign roles
   */
  | 'EDITOR'
  /**
   * EVENT_ADMIN: Full access to this event
   * - Can edit all event details
   * - Can manage tickets and pricing
   * - Can issue refunds
   * - Can assign event roles (except EVENT_OWNER)
   */
  | 'EVENT_ADMIN'
  /**
   * EVENT_OWNER: Person who created the event
   * - Full control over this specific event
   * - Can delete the event
   * - Can assign event roles to others
   */
  | 'EVENT_OWNER'
  /**
   * VIEWER: Read-only access
   * - Can view event details
   * - Can view sales data
   * - Cannot edit or scan
   */
  | 'VIEWER';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PLATFORM-WIDE STATISTICS (Admin Dashboard Only)
 * ─────────────────────────────────────────────────────────────────────────────
 * These aggregate stats across all events on the platform.
 * Only platform administrators should see this data.
 */
export type EventStats = {
  __typename: 'EventStats';
  approvedNotPublishedEvents: Scalars['Int']['output'];
  cancelledEvents: Scalars['Int']['output'];
  completedEvents: Scalars['Int']['output'];
  draftEvents: Scalars['Int']['output'];
  eventsByCategory: Maybe<Array<EventCategoryStats>>;
  eventsByOrganizer: Maybe<Array<EventOrganizerStats>>;
  eventsByStatus: Maybe<Array<EventStatusStats>>;
  pendingApprovalEvents: Scalars['Int']['output'];
  publishedEvents: Scalars['Int']['output'];
  recentEvents: Maybe<Array<Event>>;
  rejectedEvents: Scalars['Int']['output'];
  totalCapacity: Scalars['Int']['output'];
  totalEvents: Scalars['Int']['output'];
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
  totalSoldTickets: Scalars['Int']['output'];
};

/**
 * ============================================================================
 * SECTION 3: ENUMS
 * ============================================================================
 * Event lifecycle status - controls what actions are available
 */
export type EventStatus =
  /**  Admin requested changes */
  | 'APPROVED'
  /**  Live and visible to public */
  | 'CANCELLED'
  /**  Submitted for admin review */
  | 'CHANGES_REQUESTED'
  /**  Event cancelled */
  | 'COMPLETED'
  | 'DRAFT'
  /**  Initial state, not visible to public */
  | 'PENDING_REVIEW'
  /**  Approved but not yet published */
  | 'PUBLISHED';

export type EventStatusStats = {
  __typename: 'EventStatusStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: EventStatus;
};

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PER-EVENT STATISTICS (Organizer Dashboard + Admin)
 * ─────────────────────────────────────────────────────────────────────────────
 * These stats are for a specific event. Organizers can see stats for their
 * own events, admins can see stats for any event.
 */
export type EventTicketStatistics = {
  __typename: 'EventTicketStatistics';
  bestSellingTier: Maybe<Scalars['String']['output']>;
  eventDate: Scalars['DateTime']['output'];
  eventId: Scalars['ID']['output'];
  eventTitle: Scalars['String']['output'];
  overallSalesPercentage: Scalars['Float']['output'];
  tierStatistics: Maybe<Array<TicketTierStats>>;
  totalCommissionEarned: Maybe<Scalars['BigDecimal']['output']>;
  totalGrossRevenue: Maybe<Scalars['BigDecimal']['output']>;
  totalTicketsAvailable: Scalars['Int']['output'];
  totalTicketsRefunded: Scalars['Int']['output'];
  totalTicketsSold: Scalars['Int']['output'];
};

/**  Export format */
export type ExportFormat =
  | 'CSV'
  | 'EXCEL'
  | 'JSON'
  | 'PDF';

/**  ADMIN - Time-series financial data points */
export type FinancialDataPoint = {
  __typename: 'FinancialDataPoint';
  commissions: Scalars['BigDecimal']['output'];
  payouts: Scalars['BigDecimal']['output'];
  period: Scalars['String']['output'];
  refunds: Scalars['BigDecimal']['output'];
  revenue: Scalars['BigDecimal']['output'];
  ticketsSold: Scalars['Int']['output'];
};

/**  ADMIN - Financial reporting for platform management */
export type FinancialReport = {
  __typename: 'FinancialReport';
  dataPoints: Array<FinancialDataPoint>;
  endDate: Scalars['DateTime']['output'];
  escrowBalance: Scalars['BigDecimal']['output'];
  eventBreakdown: Maybe<Array<EventFinancialSummary>>;
  netPlatformRevenue: Scalars['BigDecimal']['output'];
  pendingPayouts: Scalars['BigDecimal']['output'];
  startDate: Scalars['DateTime']['output'];
  totalCommissions: Scalars['BigDecimal']['output'];
  totalPayouts: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalRevenue: Scalars['BigDecimal']['output'];
};

export type FinancialReportFilterInput = {
  endDate: Scalars['DateTime']['input'];
  eventId: InputMaybe<Scalars['ID']['input']>;
  groupBy: InputMaybe<TimeUnit>;
  organizerId: InputMaybe<Scalars['ID']['input']>;
  startDate: Scalars['DateTime']['input'];
};

export type GrantEventAccessInput = {
  eventId: Scalars['ID']['input'];
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
  userId: Scalars['ID']['input'];
};

/**
 * Input for initiating a payment attempt with PawaPay.
 * Called during ticket checkout to start mobile money payment.
 */
export type InitiatePaymentAttemptInput = {
  /** Payment amount */
  amount: Scalars['BigDecimal']['input'];
  /** User making the payment */
  buyerId: Scalars['String']['input'];
  /** Client IP address for security */
  clientIp: InputMaybe<Scalars['String']['input']>;
  /** Optional correlation ID for tracing */
  correlationId: InputMaybe<Scalars['String']['input']>;
  /** Currency code (default: ZMW) */
  currency: InputMaybe<Scalars['String']['input']>;
  /** Event the ticket is for */
  eventId: Scalars['String']['input'];
  /** Payer phone number in E.164 format (e.g., +260763456789) */
  payerPhone: Scalars['String']['input'];
  /** Mobile money provider (e.g., MTN_MOMO_ZMB, AIRTEL_OAPI_ZMB) */
  provider: Scalars['String']['input'];
  /** Session ID for tracing */
  sessionId: InputMaybe<Scalars['String']['input']>;
  /** Ticket being purchased */
  ticketId: Scalars['String']['input'];
};

/**  Invitation responses */
export type InvitationMutationResponse = {
  __typename: 'InvitationMutationResponse';
  invitation: Maybe<TeamInvitation>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Team invitation status */
export type InvitationStatus =
  /**  Awaiting response */
  | 'ACCEPTED'
  /**  Invitation accepted */
  | 'DECLINED'
  /**  Invitation declined by invitee */
  | 'EXPIRED'
  | 'PENDING'
  /**  Invitation expired */
  | 'REVOKED';

export type InviteMemberInput = {
  email: Scalars['String']['input'];
  eventAccessGrants: InputMaybe<Array<EventAccessGrantInput>>;
  inviteeName: InputMaybe<Scalars['String']['input']>;
  message: InputMaybe<Scalars['String']['input']>;
  phoneNumber: InputMaybe<Scalars['String']['input']>;
  role: OrganizationRole;
};

export type InviteTeamMemberInput = {
  email: Scalars['String']['input'];
  eventAccessGrants: InputMaybe<Array<EventAccessInput>>;
  inviteeName: InputMaybe<Scalars['String']['input']>;
  message: InputMaybe<Scalars['String']['input']>;
  organizationId: Scalars['ID']['input'];
  phoneNumber: InputMaybe<Scalars['String']['input']>;
  role: OrganizationRole;
};

/**  Journal Entry (core double-entry record) */
export type JournalEntry = {
  __typename: 'JournalEntry';
  correlationId: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  createdBy: Maybe<Scalars['String']['output']>;
  description: Scalars['String']['output'];
  effectiveDate: Maybe<Scalars['DateTime']['output']>;
  entryDate: Scalars['DateTime']['output'];
  entryNumber: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isBalanced: Scalars['Boolean']['output'];
  lines: Array<JournalLine>;
  metadata: Maybe<Scalars['JSON']['output']>;
  postedAt: Maybe<Scalars['DateTime']['output']>;
  postedBy: Maybe<Scalars['String']['output']>;
  reversalEntryId: Maybe<Scalars['String']['output']>;
  reversedAt: Maybe<Scalars['DateTime']['output']>;
  reversedBy: Maybe<Scalars['String']['output']>;
  reversedByEntryId: Maybe<Scalars['String']['output']>;
  status: JournalEntryStatus;
  totalCredits: Scalars['BigDecimal']['output'];
  totalDebits: Scalars['BigDecimal']['output'];
  type: JournalEntryType;
};

export type JournalEntryFilterInput = {
  accountCode: InputMaybe<Scalars['String']['input']>;
  correlationId: InputMaybe<Scalars['String']['input']>;
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  status: InputMaybe<JournalEntryStatus>;
  type: InputMaybe<JournalEntryType>;
};

/**  Journal Entry mutation response */
export type JournalEntryMutationResponse = {
  __typename: 'JournalEntryMutationResponse';
  data: Maybe<JournalEntry>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type JournalEntryOffsetPage = {
  __typename: 'JournalEntryOffsetPage';
  data: Array<JournalEntry>;
  pagination: PaginationInfo;
};

/**  Journal entry lifecycle status */
export type JournalEntryStatus =
  | 'DRAFT'
  | 'POSTED'
  | 'REVERSED';

/**  Journal entry type */
export type JournalEntryType =
  | 'ADJUSTMENT'
  | 'REVERSAL'
  | 'STANDARD';

/**  Journal Entry Line (embedded in JournalEntry) */
export type JournalLine = {
  __typename: 'JournalLine';
  accountCode: Scalars['String']['output'];
  accountName: Scalars['String']['output'];
  credit: Maybe<Scalars['BigDecimal']['output']>;
  debit: Maybe<Scalars['BigDecimal']['output']>;
  description: Maybe<Scalars['String']['output']>;
  referenceId: Maybe<Scalars['String']['output']>;
  referenceType: Maybe<Scalars['String']['output']>;
};

export type JournalLineInput = {
  accountCode: Scalars['String']['input'];
  accountName: Scalars['String']['input'];
  credit: InputMaybe<Scalars['BigDecimal']['input']>;
  debit: InputMaybe<Scalars['BigDecimal']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  referenceId: InputMaybe<Scalars['String']['input']>;
  referenceType: InputMaybe<Scalars['String']['input']>;
};

/**
 * Know Your Business (KYB) verification status
 * Determines what actions the organization can take
 */
export type KybStatus =
  /**  KYB submitted, awaiting admin review */
  | 'CHANGES_REQUESTED'
  /**  KYB not started - can create draft events */
  | 'IN_PROGRESS'
  | 'NOT_STARTED'
  /**  KYB form partially filled */
  | 'PENDING_REVIEW'
  /**  KYB verified - can publish events and receive payouts */
  | 'REJECTED'
  /**  Admin requested changes to KYB */
  | 'VERIFIED';

/**  ORGANIZER - Real-time event day dashboard for entry management */
export type LiveDashboard = {
  __typename: 'LiveDashboard';
  checkInRate: Scalars['Float']['output'];
  /**  per minute */
  checkInsByTier: Array<TierCheckInStats>;
  checkInsLastHour: Scalars['Int']['output'];
  checkedIn: Scalars['Int']['output'];
  currentCheckInRate: Maybe<Scalars['Float']['output']>;
  eventId: Scalars['ID']['output'];
  eventTitle: Scalars['String']['output'];
  peakCheckInTime: Maybe<Scalars['DateTime']['output']>;
  recentCheckIns: Maybe<Array<CheckInEvent>>;
  totalCapacity: Scalars['Int']['output'];
  totalSold: Scalars['Int']['output'];
};

export type Location = {
  __typename: 'Location';
  address: Scalars['String']['output'];
  city: Scalars['String']['output'];
  coordinates: Maybe<Coordinates>;
  country: Scalars['String']['output'];
  description: Maybe<Scalars['String']['output']>;
  /**  PUBLIC - for event discovery and maps */
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  postalCode: Maybe<Scalars['String']['output']>;
  province: Maybe<Scalars['String']['output']>;
};

export type LocationConnection = {
  __typename: 'LocationConnection';
  edges: Array<LocationEdge>;
  pageInfo: PageInfo;
};

export type LocationEdge = {
  __typename: 'LocationEdge';
  cursor: Scalars['String']['output'];
  node: Location;
};

/** Input for marking a payment as fulfilled after accounting operations complete. */
export type MarkPaymentFulfilledInput = {
  /** ID of the commission record created */
  commissionId: InputMaybe<Scalars['String']['input']>;
  /** UUID of the payment attempt (depositId) */
  depositId: Scalars['String']['input'];
  /** ID of the escrow transaction created */
  escrowTransactionId: Scalars['String']['input'];
  /** ID of the journal entry created */
  journalEntryId: Scalars['String']['input'];
};

/**  Member responses */
export type MemberMutationResponse = {
  __typename: 'MemberMutationResponse';
  member: Maybe<OrganizationMember>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Team member status within organization */
export type MemberStatus =
  | 'ACTIVE'
  /**  Active member */
  | 'INACTIVE'
  /**  Temporarily suspended */
  | 'REMOVED'
  /**  Deactivated (by admin) */
  | 'SUSPENDED';

/**  Mobile money account for payouts (Zambia: MTN, Airtel, Zamtel) */
export type MobileMoneyAccount = {
  __typename: 'MobileMoneyAccount';
  /**  +260****1234 */
  accountHolderName: Maybe<Scalars['String']['output']>;
  /**  Masked for display */
  maskedPhoneNumber: Maybe<Scalars['String']['output']>;
  phoneNumber: Maybe<Scalars['String']['output']>;
  provider: Maybe<MobileMoneyProvider>;
  verified: Scalars['Boolean']['output'];
};

/**  Mobile money providers in Zambia */
export type MobileMoneyProvider =
  | 'AIRTEL'
  | 'MTN'
  | 'ZAMTEL';

/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type Mutation = {
  __typename: 'Mutation';
  acceptChargeback: ChargebackMutationResponse;
  /**  ORGANIZER - Accept invitation (by invitee) */
  acceptInvitation: Maybe<OrganizationMember>;
  /**  ORGANIZER - Accept ownership transfer (new owner) */
  acceptOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  /**
   * ========================================================================
   * ESCALATION MUTATIONS (Admin Only)
   * NOTE: All adminId parameters extracted from JWT authentication (OWASP A01:2021 compliance)
   * ========================================================================
   * ADMIN - Acknowledge an escalation
   */
  acknowledgeEscalation: ApprovalEscalationMutationResponse;
  activateEventCategory: CategoryMutationResponse;
  activatePromoCode: PromoCode;
  activateTicketTier: TierMutationResponse;
  /**  ADMIN - Activate user */
  activateUser: Scalars['Boolean']['output'];
  /**
   * ADMIN - Add internal comment to approval timeline (without status change)
   * NOTE: adminId extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  addApprovalComment: ApprovalTimelineMutationResponse;
  /**
   * ADMIN - Add note for audit trail
   * NOTE: author extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  addPaymentAttemptNote: PaymentAttemptMutationResponse;
  /**
   * Add a role to a user.
   * A user can have multiple roles (e.g., CUSTOMER + ORGANIZER).
   * The CUSTOMER role is the base role that all users have.
   */
  addUserRole: UserMutationResponse;
  /**  ADMIN - Administrative ticket operations */
  adminUpdateTicket: TicketMutationResponse;
  /**
   * Apply to become an organizer.
   * Creates a new organization in DRAFT status.
   * User must fill in business details and submit for approval.
   */
  applyToBeOrganizer: Organization;
  /**
   * ADMIN - Approval decisions are admin-only platform operations
   * NOTE: reviewerId/adminId extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  approveEvent: EventMutationResponse;
  /**
   * Approve an organization application.
   * Changes status from PENDING_REVIEW to APPROVED.
   * Organization can now publish events.
   */
  approveOrganization: Maybe<Organization>;
  /**  ADMIN - Payout approval workflow */
  approvePayoutRequest: ApprovePayoutRequestMutationResponse;
  /**
   * ADMIN - Refund approval workflow
   * NOTE: Actor IDs (reviewerId, processedBy, etc.) extracted from JWT - OWASP A01:2021
   */
  approveRefundRequest: ApproveRefundRequestMutationResponse;
  /**  ADMIN - Approve verification document */
  approveVerificationDocument: Maybe<VerificationDocument>;
  /**  ADMIN - Reviewer assignment for approval workflow */
  assignEventReviewer: ApprovalTimelineMutationResponse;
  /**  ADMIN - Assign permission to role */
  assignPermissionToRole: Scalars['Boolean']['output'];
  bulkApproveRefunds: BulkOperationResponse;
  bulkCancelTickets: BulkOperationResponse;
  /**  ORGANIZER - Bulk grant event access */
  bulkGrantEventAccess: Maybe<Array<EventAccessGrant>>;
  /**  ORGANIZER - Bulk invite team members */
  bulkInviteTeamMembers: Maybe<Array<TeamInvitation>>;
  bulkMarkPayoutsForReview: BulkPayoutOperationResponse;
  bulkRetryFailedPayouts: BulkPayoutOperationResponse;
  cancelAccountDeletion: MutationResponse;
  /**  ORGANIZER - Event cancellation (triggers refund workflows) */
  cancelEvent: EventCancellationResponse;
  /**  MOBILE - Cancel event reminder */
  cancelEventReminder: Scalars['Boolean']['output'];
  /**  ORGANIZER - Cancel ownership transfer (current owner) */
  cancelOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  /**  ADMIN - Cancel payment */
  cancelPaymentAttempt: PaymentAttemptMutationResponse;
  /**  ORGANIZER - Cancel own payout request */
  cancelPayoutRequest: RejectPayoutRequestMutationResponse;
  cancelRefundRequest: RefundRequestMutationResponse;
  cancelReservation: Scalars['Boolean']['output'];
  /**
   * ORGANIZER/ADMIN - Cancel and refund tickets
   * NOTE: processedBy extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  cancelTicket: CancelTicketMutationResponse;
  /**  MOBILE - Change password */
  changePassword: Scalars['Boolean']['output'];
  closeEscrowAccount: EscrowAccountMutationResponse;
  /**  INTERNAL - Auto-completion triggered by system when event date passes */
  completeEvent: EventMutationResponse;
  /**  ADMIN - Complete payout after bank transfer (records accounting entries) */
  completePayoutRequest: PayoutRequestMutationResponse;
  completeReconciliation: ReconciliationMutationResponse;
  completeReservation: Array<Ticket>;
  /**  ADMIN - Admin-initiated refund */
  createAdminRefundRequest: RefundRequestMutationResponse;
  /**
   * ========================================================================
   * BANK ACCOUNT MUTATIONS
   * ORGANIZER - Manage own bank accounts, ADMIN - Verify
   * ========================================================================
   * ORGANIZER - Bank account CRUD
   * NOTE: organizerId/verifiedBy extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  createBankAccount: CreateBankAccountMutationResponse;
  /**
   * ========================================================================
   * FINANCIAL ENGINE MUTATIONS - Double-Entry Bookkeeping
   * ADMIN-ONLY - Core accounting infrastructure
   * ========================================================================
   * ADMIN - Chart of Accounts management
   */
  createChartOfAccountsEntry: ChartOfAccountsMutationResponse;
  /**
   * ========================================================================
   * CITY MUTATIONS
   * Platform manages city reference data - admin only
   * ========================================================================
   * ADMIN - City CRUD for geographic reference data
   */
  createCity: CityMutationResponse;
  /**
   * ========================================================================
   * ESCROW ACCOUNT MUTATIONS
   * ADMIN - Escrow account management
   * ========================================================================
   * INTERNAL - Auto-create escrow on event publish
   */
  createEscrowAccount: EscrowAccountMutationResponse;
  /**
   * ========================================================================
   * EVENT MUTATIONS
   * Organizers create and manage their own events
   * Admin can also perform these operations for platform management
   * ========================================================================
   * ORGANIZER - Core event CRUD operations
   * NOTE: organizerId is extracted from JWT authentication, not passed as parameter
   * This prevents OWASP A01:2021 Broken Access Control vulnerabilities
   */
  createEvent: EventMutationResponse;
  /**
   * ========================================================================
   * EVENT CATEGORY MUTATIONS
   * Platform manages category reference data - admin only
   * ========================================================================
   * ADMIN - Category CRUD for platform taxonomy
   */
  createEventCategory: CategoryMutationResponse;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Event Access Management
   * ORGANIZER - Event-level access control for team members
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Create event owner (internal)
   */
  createEventOwner: Maybe<EventAccessGrant>;
  /**
   * ADMIN - Journal Entry management
   * NOTE: Actor IDs (postedBy, reversedBy) extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  createJournalEntry: JournalEntryMutationResponse;
  /**
   * ========================================================================
   * PAYOUT REQUEST MUTATIONS
   * ORGANIZER - Request payouts, ADMIN - Approve/process
   * ========================================================================
   * ORGANIZER - Request payout of event revenue
   * NOTE: Actor IDs extracted from JWT authentication - OWASP A01:2021
   */
  createPayoutRequest: CreatePayoutRequestMutationResponse;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * NOTE: Organizer Profile mutations have been merged into Organization
   * See Organization Onboarding mutations:
   * - applyToBeOrganizer
   * - updateOrganizationApplication
   * - submitOrganizationForReview
   * - approveOrganization
   * - requestOrganizationChanges
   * - rejectOrganization
   * ─────────────────────────────────────────────────────────────────────
   * ─────────────────────────────────────────────────────────────────────
   * Permission Management
   * ─────────────────────────────────────────────────────────────────────
   * ADMIN - Create permission
   */
  createPermission: Maybe<Permission>;
  /**  ADMIN - Platform Account management */
  createPlatformAccount: PlatformAccountMutationResponse;
  /**
   * ========================================================================
   * PROMO CODE MUTATIONS
   * ORGANIZER - Promo code management for marketing
   * ========================================================================
   * ORGANIZER - Promo code CRUD
   * NOTE: createdBy extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  createPromoCode: PromoCode;
  /**
   * ========================================================================
   * PROVINCE MUTATIONS
   * Platform manages province reference data - admin only
   * ========================================================================
   * ADMIN - Province CRUD for regional data management
   */
  createProvince: ProvinceMutationResponse;
  /**
   * ========================================================================
   * TICKET TIER MUTATIONS
   * Organizers manage ticket tiers for their events
   * ========================================================================
   * ORGANIZER - Ticket tier CRUD for event pricing structure
   */
  createTicketTier: TierMutationResponse;
  /**  ADMIN - User management */
  createUser: UserMutationResponse;
  /**
   * ========================================================================
   * REFUND REQUEST MUTATIONS
   * MOBILE - Customer requests, ADMIN - Approval workflow
   * ========================================================================
   * MOBILE - Customer initiates refund request
   */
  createUserRefundRequest: CreateRefundRequestMutationResponse;
  creditPlatformAccount: PlatformAccountMutationResponse;
  deactivateChartOfAccountsEntry: ChartOfAccountsMutationResponse;
  deactivateEventCategory: CategoryMutationResponse;
  deactivatePromoCode: PromoCode;
  deactivateTicketTier: TierMutationResponse;
  /**  ADMIN - Deactivate user */
  deactivateUser: Scalars['Boolean']['output'];
  debitPlatformAccount: PlatformAccountMutationResponse;
  /**  ORGANIZER - Decline invitation (by invitee) */
  declineInvitation: Maybe<TeamInvitation>;
  /**  ORGANIZER - Decline ownership transfer (new owner) */
  declineOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  deleteBankAccount: DeleteBankAccountMutationResponse;
  deleteCity: DeleteMutationResponse;
  deleteEvent: DeleteMutationResponse;
  deleteEventCategory: DeleteMutationResponse;
  /**  MOBILE - Delete notification */
  deleteNotification: Scalars['Boolean']['output'];
  /**  ADMIN - Delete permission */
  deletePermission: Scalars['Boolean']['output'];
  deletePromoCode: DeleteMutationResponse;
  deleteProvince: DeleteMutationResponse;
  deleteTicketTier: DeleteMutationResponse;
  /**  ORGANIZER - Delete verification document */
  deleteVerificationDocument: Scalars['Boolean']['output'];
  disableTwoFactor: MutationResponse;
  disputeChargeback: ChargebackMutationResponse;
  /**  ORGANIZER - Duplicate event for recurring events workflow */
  duplicateEvent: EventMutationResponse;
  escalatePayoutRequest: PayoutRequestMutationResponse;
  expireTimedOutPayments: Scalars['Int']['output'];
  extendReservation: TicketReservation;
  failReconciliation: ReconciliationMutationResponse;
  /**  ADMIN - Featured events are platform decisions (homepage promotion) */
  featureEvent: EventMutationResponse;
  /**  ADMIN - Force expire stuck reservations */
  forceExpireReservation: Scalars['Boolean']['output'];
  /**
   * Get or create organization for the current user.
   * If user has no organization, creates one in DRAFT status.
   */
  getOrCreateMyOrganization: Organization;
  /**  ORGANIZER - Grant event access */
  grantEventAccess: Maybe<EventAccessGrant>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Ownership Transfer
   * ORGANIZER - Organization ownership transfer workflow
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Initiate ownership transfer (current owner)
   */
  initiateOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  /**
   * ========================================================================
   * FINANCIAL TRANSACTION MUTATIONS - REMOVED
   * Replaced by:
   * - PaymentAttempt mutations for payment lifecycle
   * - JournalEntry mutations for accounting
   * ========================================================================
   * ========================================================================
   * PAYMENT ATTEMPT MUTATIONS
   * ADMIN/INTERNAL - Payment attempt lifecycle management
   * ========================================================================
   * INTERNAL - Initiate payment (called by checkout flow)
   */
  initiatePaymentAttempt: PaymentAttemptMutationResponse;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Team Management
   * ORGANIZER - Team invitation and management
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Invite team member (matches resolver signature)
   */
  inviteTeamMember: Maybe<TeamInvitation>;
  /**  ORGANIZER - Leave organization (self-removal) */
  leaveOrganization: Scalars['Boolean']['output'];
  linkSocialAccount: MutationResponse;
  lockEscrowAccount: EscrowAccountMutationResponse;
  lockUser: MutationResponse;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Authentication - Core auth operations
   * ─────────────────────────────────────────────────────────────────────
   * Login with email and password
   */
  login: AuthPayload;
  /**  Logout and invalidate session */
  logout: Scalars['Boolean']['output'];
  /**  MOBILE - Mark all notifications as read */
  markAllNotificationsRead: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Notification Management
   * MOBILE - User notification management
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Mark notification as read
   */
  markNotificationRead: Maybe<Notification>;
  /**  ADMIN - Mark fulfillment complete */
  markPaymentFulfilled: PaymentAttemptMutationResponse;
  markPayoutEligible: EscrowAccountMutationResponse;
  markPayoutForReview: PayoutRequestMutationResponse;
  /**  INTERNAL - Scheduled job operations */
  pollPendingPayments: Scalars['Int']['output'];
  postJournalEntry: JournalEntryMutationResponse;
  /**  INTERNAL - Webhook processing (called by PawaPay webhook handler) */
  processPaymentWebhook: PaymentAttemptMutationResponse;
  processPayoutRequest: ProcessPayoutRequestMutationResponse;
  processRefundRequest: ProcessRefundRequestMutationResponse;
  /**  ORGANIZER - Event publishing workflow */
  publishEvent: EventMutationResponse;
  /**
   * ========================================================================
   * TICKET MUTATIONS
   * MOBILE - Purchase flow, ORGANIZER - Validation
   * ========================================================================
   * MOBILE - Customer ticket purchase
   * NOTE: buyerId extracted from JWT authentication - not passed as parameter (OWASP A01:2021)
   */
  purchaseTicket: PurchaseTicketMutationResponse;
  /**  ORGANIZER - Reactivate member */
  reactivateMember: Maybe<OrganizationMember>;
  /**
   * ========================================================================
   * CHARGEBACK MUTATIONS
   * ADMIN-ONLY - Chargeback lifecycle management
   * ========================================================================
   * ADMIN - Chargeback lifecycle management
   */
  receiveChargeback: ChargebackMutationResponse;
  recordChargebackOutcome: ChargebackMutationResponse;
  /**
   * Records a gateway settlement in the accounting system.
   *
   * Called when:
   * 1. Gateway webhook confirms settlement completed
   * 2. Admin manually records a settlement from bank statement
   * 3. Reconciliation process confirms matched transactions
   *
   * Accounting Entry (IN/OUT):
   * - DR Bank Account (1011)        - IN: Money received in bank
   * - DR Gateway Fees Expense (5010) - IN: Fee cost to platform
   * - CR Gateway Receivable (1021)  - OUT: Receivable cleared
   */
  recordGatewaySettlement: JournalEntryMutationResponse;
  recoverChargebackFunds: ChargebackMutationResponse;
  /**  Refresh access token using refresh token */
  refreshToken: AuthPayload;
  refundTicket: RefundTicketMutationResponse;
  regenerateTicketQrCode: TicketMutationResponse;
  /**  Register a new user */
  register: User;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Device Management
   * MOBILE - Push notification device management
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Register device for push notifications
   */
  registerDevice: Maybe<UserDevice>;
  rejectEvent: EventMutationResponse;
  /**
   * Reject an organization application.
   * Changes status to REJECTED.
   */
  rejectOrganization: Maybe<Organization>;
  rejectPayoutRequest: RejectPayoutRequestMutationResponse;
  rejectRefundRequest: RejectRefundRequestMutationResponse;
  /**  ADMIN - Reject verification document */
  rejectVerificationDocument: Maybe<VerificationDocument>;
  /**  ORGANIZER - Remove member */
  removeMember: Scalars['Boolean']['output'];
  /**  ADMIN - Remove permission from role */
  removePermissionFromRole: Scalars['Boolean']['output'];
  /**
   * Remove a role from a user.
   * Note: The CUSTOMER role cannot be removed as it is the base role.
   */
  removeUserRole: UserMutationResponse;
  reorderTicketTiers: Array<TicketTier>;
  /**  MOBILE - Account deletion (GDPR) */
  requestAccountDeletion: MutationResponse;
  requestEventChanges: EventMutationResponse;
  /**
   * Request changes to an organization application.
   * Changes status from PENDING_REVIEW to CHANGES_REQUESTED.
   * User can update details and resubmit.
   */
  requestOrganizationChanges: Maybe<Organization>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Phone OTP Authentication
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Request phone OTP
   */
  requestPhoneOtp: OtpRequestResponse;
  /**  ORGANIZER - Resend invitation */
  resendInvitation: Maybe<TeamInvitation>;
  /**
   * ========================================================================
   * RESERVATION MUTATIONS
   * MOBILE - Ticket checkout flow
   * ========================================================================
   * MOBILE - Customer checkout flow (userId extracted from JWT for security)
   */
  reserveTickets: TicketReservation;
  /**  MOBILE - Reset password */
  resetPassword: Scalars['Boolean']['output'];
  /**  ADMIN - Resolve an escalation (must also approve/reject/request changes) */
  resolveEscalation: ApprovalEscalationMutationResponse;
  resolvePayoutIssue: PayoutRequestMutationResponse;
  resolveReconciliationItem: ReconciliationMutationResponse;
  /**  ADMIN - Payout Recovery Operations */
  resumePayoutRequest: PayoutRequestMutationResponse;
  /**  INTERNAL - Retry failed payment */
  retryPaymentAttempt: PaymentAttemptMutationResponse;
  retryPayoutRequest: PayoutRequestMutationResponse;
  reverseJournalEntry: JournalEntryMutationResponse;
  /**  ORGANIZER - Revoke event access */
  revokeEventAccess: Maybe<EventAccessGrant>;
  /**  ORGANIZER - Revoke invitation */
  revokeInvitation: Maybe<TeamInvitation>;
  seedChartOfAccounts: Scalars['Boolean']['output'];
  sendBulkEventPublishReminders: BulkReminderResponse;
  /**  ADMIN - Send bulk notification */
  sendBulkNotification: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * User Profile & Verification
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Send email verification
   */
  sendEmailVerification: Scalars['Boolean']['output'];
  /**
   * ========================================================================
   * EVENT REMINDER MUTATIONS
   * Platform sends reminders to organizers - admin operation
   * ========================================================================
   * ADMIN - Platform nudges organizers to publish their events
   */
  sendEventPublishReminder: EventMutationResponse;
  /**  ADMIN - Send notification (admin/system) */
  sendNotification: Maybe<Notification>;
  /**  MOBILE - Send phone verification */
  sendPhoneVerification: Scalars['Boolean']['output'];
  setDefaultBankAccount: UpdateBankAccountMutationResponse;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Event Reminder Management
   * MOBILE - Event reminder management
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Set event reminder
   */
  setEventReminder: Maybe<EventReminder>;
  /**
   * ADMIN - Set review status for investigation
   * NOTE: reviewedBy extracted from JWT authentication (OWASP A01:2021 compliance)
   */
  setPaymentAttemptReviewStatus: PaymentAttemptMutationResponse;
  /**
   * Set all roles for a user (replaces existing roles).
   * The roles set must include CUSTOMER.
   */
  setUserRoles: UserMutationResponse;
  /**  MOBILE - Two-factor authentication setup */
  setupTwoFactor: TwoFactorSetupResponse;
  /**  MOBILE - Social login and account linking */
  socialAuth: AuthPayload;
  startChargebackReview: ChargebackMutationResponse;
  /**
   * ========================================================================
   * RECONCILIATION MUTATIONS
   * ADMIN-ONLY - Reconciliation engine
   * ========================================================================
   * ADMIN - Reconciliation management
   */
  startReconciliation: ReconciliationMutationResponse;
  /**
   * ========================================================================
   * EVENT APPROVAL MUTATIONS
   * Approval workflow: Organizers submit, Admins approve/reject
   * ========================================================================
   * ORGANIZER - Submit event for platform review
   * NOTE: organizerId is extracted from JWT authentication
   */
  submitEventForApproval: EventMutationResponse;
  /**
   * Submit organization application for admin review.
   * Changes status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW.
   */
  submitOrganizationForReview: Maybe<Organization>;
  /**  ORGANIZER - Suspend member */
  suspendMember: Maybe<OrganizationMember>;
  /**  ADMIN - Suspend organization */
  suspendOrganization: Maybe<Organization>;
  suspendUser: MutationResponse;
  /**  ADMIN - Full sync all users */
  syncAllUsersFromKeycloak: Scalars['Boolean']['output'];
  /**  MOBILE - Sync email verification status */
  syncEmailVerificationStatus: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Keycloak Sync (Internal)
   * ─────────────────────────────────────────────────────────────────────
   * ADMIN - Sync user from Keycloak
   */
  syncUserFromKeycloak: Maybe<User>;
  /**  ORGANIZER - Transfer ownership (alternative method) */
  transferOrganizationOwnership: Maybe<OrganizationMember>;
  /**  ADMIN - Manually trigger escalation (for testing or manual override) */
  triggerManualEscalation: ApprovalEscalationMutationResponse;
  unassignEventReviewer: ApprovalTimelineMutationResponse;
  unlinkSocialAccount: MutationResponse;
  unlockEscrowAccount: EscrowAccountMutationResponse;
  unlockUser: MutationResponse;
  unpublishEvent: EventMutationResponse;
  /**  MOBILE - Unregister device */
  unregisterDevice: Scalars['Boolean']['output'];
  /**  ADMIN - Unsuspend organization */
  unsuspendOrganization: Maybe<Organization>;
  unsuspendUser: MutationResponse;
  updateBankAccount: UpdateBankAccountMutationResponse;
  updateChartOfAccountsEntry: ChartOfAccountsMutationResponse;
  updateCity: CityMutationResponse;
  /**  ADMIN - Escrow account management */
  updateEscrowAccountStatus: EscrowAccountMutationResponse;
  updateEvent: EventMutationResponse;
  /**  ORGANIZER - Update event access */
  updateEventAccess: Maybe<EventAccessGrant>;
  updateEventAccessibility: EventMutationResponse;
  /**  ORGANIZER - Capacity and accessibility management */
  updateEventCapacity: EventMutationResponse;
  updateEventCategory: CategoryMutationResponse;
  /**  ORGANIZER - Update member role */
  updateMemberRole: Maybe<OrganizationMember>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * User Management
   * MOBILE - Self-service profile management
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Update my profile
   */
  updateMyProfile: UserMutationResponse;
  /**  MOBILE - Update notification preferences */
  updateNotificationPreferences: Maybe<NotificationPreferences>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization Management
   * ORGANIZER - Organization settings management
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Update organization (owner/admin only)
   */
  updateOrganization: Maybe<Organization>;
  /**
   * Update organization application details.
   * Only allowed when status is DRAFT or CHANGES_REQUESTED.
   */
  updateOrganizationApplication: Maybe<Organization>;
  /**  ORGANIZER - Update organization settings */
  updateOrganizationSettings: Maybe<Organization>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization Management (additional)
   * ─────────────────────────────────────────────────────────────────────
   * ADMIN - Update organization status
   */
  updateOrganizationStatus: Maybe<Organization>;
  /**  ADMIN - Update permission */
  updatePermission: Maybe<Permission>;
  /**
   * ========================================================================
   * PLATFORM CONFIGURATION MUTATIONS (Admin Only)
   * ========================================================================
   * ADMIN - Update platform configuration (SLA settings, escalation, notifications)
   */
  updatePlatformConfiguration: PlatformConfigurationMutationResponse;
  /**  MOBILE - Update profile */
  updateProfile: Maybe<User>;
  updatePromoCode: PromoCode;
  updateProvince: ProvinceMutationResponse;
  updateTicketTier: TierMutationResponse;
  updateUser: UserMutationResponse;
  /**
   * Upgrade an individual organization to a business organization.
   * Requires the organization to be owned by the current user.
   */
  upgradeToBusinessOrganization: Maybe<Organization>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Verification Document Management
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Upload verification document
   */
  uploadVerificationDocument: Maybe<VerificationDocument>;
  useTicket: UseTicketMutationResponse;
  /**  ORGANIZER - Ticket validation at venue (scanner app) */
  validateTicket: ValidateTicketMutationResponse;
  /**  Validate a token and get its claims */
  validateToken: TokenValidation;
  /**  ADMIN - Bank account verification */
  verifyBankAccount: VerifyBankAccountMutationResponse;
  /**  MOBILE - Verify email */
  verifyEmail: Maybe<User>;
  /**
   * ADMIN - Escrow-Journal Cross-Verification
   * Verifies that EventEscrowAccount balances match journal entry calculated balances
   */
  verifyEscrowJournalConsistency: EscrowJournalVerificationResponse;
  /**  ADMIN - Manual status verification with gateway */
  verifyPaymentWithGateway: PaymentAttemptMutationResponse;
  /**  MOBILE - Verify phone */
  verifyPhone: Maybe<User>;
  /**  MOBILE - Verify phone OTP */
  verifyPhoneOtp: PhoneAuthPayload;
  verifyTwoFactor: MutationResponse;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAcceptChargebackArgs = {
  id: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAcceptInvitationArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAcceptOwnershipTransferArgs = {
  confirmationCode: Scalars['String']['input'];
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAcknowledgeEscalationArgs = {
  escalationId: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationActivateEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationActivatePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationActivateTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationActivateUserArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAddApprovalCommentArgs = {
  comment: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
  isInternal?: InputMaybe<Scalars['Boolean']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAddPaymentAttemptNoteArgs = {
  depositId: Scalars['String']['input'];
  note: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAddUserRoleArgs = {
  role: UserType;
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAdminUpdateTicketArgs = {
  input: AdminTicketUpdateInput;
  ticketId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationApplyToBeOrganizerArgs = {
  input: OrganizationApplicationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationApproveEventArgs = {
  comments: InputMaybe<Scalars['String']['input']>;
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationApproveOrganizationArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationApprovePayoutRequestArgs = {
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationApproveRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
  reviewComments: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationApproveVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAssignEventReviewerArgs = {
  input: AssignReviewerInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationAssignPermissionToRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationBulkApproveRefundsArgs = {
  refundRequestIds: Array<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationBulkCancelTicketsArgs = {
  reason: Scalars['String']['input'];
  ticketIds: Array<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationBulkGrantEventAccessArgs = {
  eventId: Scalars['ID']['input'];
  grants: Array<EventAccessGrantInput>;
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationBulkInviteTeamMembersArgs = {
  invitations: Array<InviteMemberInput>;
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationBulkMarkPayoutsForReviewArgs = {
  issueType: PayoutIssueType;
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestIds: Array<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationBulkRetryFailedPayoutsArgs = {
  payoutRequestIds: Array<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelEventArgs = {
  id: Scalars['ID']['input'];
  input: EventCancellationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelEventReminderArgs = {
  reminderId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelPaymentAttemptArgs = {
  depositId: Scalars['String']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelRefundRequestArgs = {
  reason: Scalars['String']['input'];
  refundRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelReservationArgs = {
  reservationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCancelTicketArgs = {
  reason: Scalars['String']['input'];
  ticketNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationChangePasswordArgs = {
  newPassword: Scalars['String']['input'];
  oldPassword: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCloseEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCompleteEventArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCompletePayoutRequestArgs = {
  bankReference: Scalars['String']['input'];
  payoutRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCompleteReconciliationArgs = {
  notes: InputMaybe<Scalars['String']['input']>;
  runId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCompleteReservationArgs = {
  input: CompleteReservationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateAdminRefundRequestArgs = {
  bypassApproval: InputMaybe<Scalars['Boolean']['input']>;
  reason: Scalars['String']['input'];
  ticketId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateBankAccountArgs = {
  input: CreateBankAccountInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateChartOfAccountsEntryArgs = {
  input: CreateChartOfAccountsInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateCityArgs = {
  input: CreateCityInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateEscrowAccountArgs = {
  input: CreateEscrowAccountInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateEventArgs = {
  input: CreateEventInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateEventCategoryArgs = {
  input: CreateEventCategoryInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateEventOwnerArgs = {
  eventId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateJournalEntryArgs = {
  input: CreateJournalEntryInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreatePayoutRequestArgs = {
  input: CreatePayoutRequestInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreatePermissionArgs = {
  category: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreatePlatformAccountArgs = {
  accountType: PlatformAccountType;
  currency: Scalars['String']['input'];
  name: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreatePromoCodeArgs = {
  input: CreatePromoCodeInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateProvinceArgs = {
  input: CreateProvinceInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateTicketTierArgs = {
  eventId: Scalars['ID']['input'];
  input: CreateTicketTierInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateUserArgs = {
  input: CreateUserInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreateUserRefundRequestArgs = {
  input: CreateRefundRequestInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationCreditPlatformAccountArgs = {
  amount: Scalars['BigDecimal']['input'];
  description: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeactivateChartOfAccountsEntryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeactivateEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeactivatePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeactivateTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeactivateUserArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDebitPlatformAccountArgs = {
  amount: Scalars['BigDecimal']['input'];
  description: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeclineInvitationArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeclineOwnershipTransferArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteBankAccountArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteCityArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteEventArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteNotificationArgs = {
  notificationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeletePermissionArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeletePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteProvinceArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDeleteVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDisableTwoFactorArgs = {
  confirmationCode: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDisputeChargebackArgs = {
  id: Scalars['ID']['input'];
  input: DisputeChargebackInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationDuplicateEventArgs = {
  eventId: Scalars['ID']['input'];
  newTitle: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationEscalatePayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationExtendReservationArgs = {
  minutes: Scalars['Int']['input'];
  reservationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationFailReconciliationArgs = {
  reason: Scalars['String']['input'];
  runId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationFeatureEventArgs = {
  eventId: Scalars['ID']['input'];
  featured: Scalars['Boolean']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationForceExpireReservationArgs = {
  reservationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationGrantEventAccessArgs = {
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  eventId: Scalars['ID']['input'];
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationInitiateOwnershipTransferArgs = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationInitiatePaymentAttemptArgs = {
  input: InitiatePaymentAttemptInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationInviteTeamMemberArgs = {
  input: InviteMemberInput;
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationLeaveOrganizationArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationLinkSocialAccountArgs = {
  input: SocialAuthInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationLockEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  lockUntil: Scalars['DateTime']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationLockUserArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationLoginArgs = {
  email: Scalars['String']['input'];
  password: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationMarkNotificationReadArgs = {
  notificationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationMarkPaymentFulfilledArgs = {
  input: MarkPaymentFulfilledInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationMarkPayoutEligibleArgs = {
  accountId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationMarkPayoutForReviewArgs = {
  issueType: PayoutIssueType;
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationPostJournalEntryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationProcessPaymentWebhookArgs = {
  input: ProcessPaymentWebhookInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationProcessPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationProcessRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationPublishEventArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationPurchaseTicketArgs = {
  input: TicketPurchaseInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationReactivateMemberArgs = {
  memberId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationReceiveChargebackArgs = {
  input: ReceiveChargebackInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRecordChargebackOutcomeArgs = {
  id: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  won: Scalars['Boolean']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRecordGatewaySettlementArgs = {
  input: RecordGatewaySettlementInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRecoverChargebackFundsArgs = {
  id: Scalars['ID']['input'];
  input: RecoverChargebackInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRefreshTokenArgs = {
  refreshToken: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRefundTicketArgs = {
  reason: Scalars['String']['input'];
  ticketNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRegenerateTicketQrCodeArgs = {
  ticketId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRegisterArgs = {
  input: RegisterInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRegisterDeviceArgs = {
  input: RegisterDeviceInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRejectEventArgs = {
  comments: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRejectOrganizationArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRejectPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  rejectionReason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRejectRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
  rejectionReason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRejectVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRemoveMemberArgs = {
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRemovePermissionFromRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRemoveUserRoleArgs = {
  role: UserType;
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationReorderTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
  tierIds: Array<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRequestAccountDeletionArgs = {
  input: RequestAccountDeletionInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRequestEventChangesArgs = {
  comments: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRequestOrganizationChangesArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRequestPhoneOtpArgs = {
  channel: InputMaybe<Scalars['String']['input']>;
  phoneNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationResendInvitationArgs = {
  invitationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationReserveTicketsArgs = {
  input: ReserveTicketsInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationResetPasswordArgs = {
  email: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationResolveEscalationArgs = {
  input: ResolveEscalationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationResolvePayoutIssueArgs = {
  newBankAccountId: InputMaybe<Scalars['ID']['input']>;
  notes: Scalars['String']['input'];
  payoutRequestId: Scalars['ID']['input'];
  resolutionType: PayoutResolutionType;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationResolveReconciliationItemArgs = {
  input: ResolveReconciliationItemInput;
  runId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationResumePayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRetryPaymentAttemptArgs = {
  depositId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRetryPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationReverseJournalEntryArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRevokeEventAccessArgs = {
  accessId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationRevokeInvitationArgs = {
  invitationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSendBulkEventPublishRemindersArgs = {
  eventIds: Array<Scalars['ID']['input']>;
  triggeredBy: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSendBulkNotificationArgs = {
  input: SendNotificationInput;
  userIds: Array<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSendEventPublishReminderArgs = {
  eventId: Scalars['ID']['input'];
  triggeredBy: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSendNotificationArgs = {
  input: SendNotificationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSetDefaultBankAccountArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSetEventReminderArgs = {
  input: SetEventReminderInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSetPaymentAttemptReviewStatusArgs = {
  depositId: Scalars['String']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  reviewStatus: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSetUserRolesArgs = {
  roles: Array<UserType>;
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSetupTwoFactorArgs = {
  input: SetupTwoFactorInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSocialAuthArgs = {
  input: SocialAuthInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationStartChargebackReviewArgs = {
  id: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationStartReconciliationArgs = {
  input: StartReconciliationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSubmitEventForApprovalArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSubmitOrganizationForReviewArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSuspendMemberArgs = {
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSuspendOrganizationArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSuspendUserArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationSyncUserFromKeycloakArgs = {
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationTransferOrganizationOwnershipArgs = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationTriggerManualEscalationArgs = {
  escalateTo: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnassignEventReviewerArgs = {
  eventId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnlinkSocialAccountArgs = {
  provider: SocialProvider;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnlockEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnlockUserArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnpublishEventArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnregisterDeviceArgs = {
  deviceId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnsuspendOrganizationArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUnsuspendUserArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateBankAccountArgs = {
  id: Scalars['ID']['input'];
  input: UpdateBankAccountInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateChartOfAccountsEntryArgs = {
  id: Scalars['ID']['input'];
  input: CreateChartOfAccountsInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateCityArgs = {
  id: Scalars['ID']['input'];
  input: UpdateCityInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateEscrowAccountStatusArgs = {
  accountId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
  status: EscrowAccountStatus;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateEventArgs = {
  id: Scalars['ID']['input'];
  input: UpdateEventInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateEventAccessArgs = {
  accessId: Scalars['ID']['input'];
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  newRole: InputMaybe<EventRole>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateEventAccessibilityArgs = {
  eventId: Scalars['ID']['input'];
  input: EventAccessibilityInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateEventCapacityArgs = {
  eventId: Scalars['ID']['input'];
  newCapacity: Scalars['Int']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateEventCategoryArgs = {
  id: Scalars['ID']['input'];
  input: UpdateEventCategoryInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateMemberRoleArgs = {
  input: UpdateMemberRoleInput;
  memberId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateMyProfileArgs = {
  input: UpdateUserInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateNotificationPreferencesArgs = {
  input: UpdateNotificationPreferencesInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateOrganizationArgs = {
  id: Scalars['ID']['input'];
  input: UpdateOrganizationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateOrganizationApplicationArgs = {
  id: Scalars['ID']['input'];
  input: OrganizationApplicationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateOrganizationSettingsArgs = {
  id: Scalars['ID']['input'];
  input: UpdateOrganizationSettingsInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateOrganizationStatusArgs = {
  id: Scalars['ID']['input'];
  status: OrganizationStatus;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdatePermissionArgs = {
  category: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  id: Scalars['ID']['input'];
  name: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdatePlatformConfigurationArgs = {
  input: UpdatePlatformConfigurationInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateProfileArgs = {
  input: Scalars['JSON']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdatePromoCodeArgs = {
  id: Scalars['ID']['input'];
  input: UpdatePromoCodeInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateProvinceArgs = {
  id: Scalars['ID']['input'];
  input: UpdateProvinceInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateTicketTierArgs = {
  input: UpdateTicketTierInput;
  tierId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpdateUserArgs = {
  id: Scalars['ID']['input'];
  input: UpdateUserInput;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUpgradeToBusinessOrganizationArgs = {
  businessName: Scalars['String']['input'];
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUploadVerificationDocumentArgs = {
  documentType: Scalars['String']['input'];
  documentUrl: Scalars['String']['input'];
  fileName: InputMaybe<Scalars['String']['input']>;
  fileSize: InputMaybe<Scalars['Long']['input']>;
  mimeType: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationUseTicketArgs = {
  ticketNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationValidateTicketArgs = {
  ticketNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationValidateTokenArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyBankAccountArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyEmailArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyEscrowJournalConsistencyArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyPaymentWithGatewayArgs = {
  depositId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyPhoneArgs = {
  code: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyPhoneOtpArgs = {
  otp: Scalars['String']['input'];
  phoneNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 16: ROOT MUTATION TYPE
 * Tags indicate which clients should include these mutations in their operations
 * ============================================================================
 */
export type MutationVerifyTwoFactorArgs = {
  input: VerifyTwoFactorInput;
};

/**
 * ============================================================================
 * SECTION 19: MUTATION RESPONSE TYPES
 * ============================================================================
 * Generic mutation response
 */
export type MutationResponse = {
  __typename: 'MutationResponse';
  code: Maybe<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type NearbyLocationInput = {
  latitude: Scalars['Float']['input'];
  longitude: Scalars['Float']['input'];
  maxResults: InputMaybe<Scalars['Int']['input']>;
  radiusKm: InputMaybe<Scalars['Float']['input']>;
};

/**
 * ============================================================================
 * SECTION 9: NOTIFICATION TYPES
 * ============================================================================
 */
export type Notification = {
  __typename: 'Notification';
  actionUrl: Maybe<Scalars['String']['output']>;
  body: Scalars['String']['output'];
  /**  Status tracking per channel */
  channelStatuses: Maybe<Array<ChannelStatus>>;
  /**  Delivery */
  channels: Array<NotificationChannel>;
  /**  Audit */
  createdAt: Scalars['DateTime']['output'];
  data: Maybe<Scalars['JSON']['output']>;
  deliveredAt: Maybe<Scalars['DateTime']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  imageUrl: Maybe<Scalars['String']['output']>;
  priority: Maybe<Scalars['String']['output']>;
  readAt: Maybe<Scalars['DateTime']['output']>;
  /**  Timestamps */
  scheduledAt: Maybe<Scalars['DateTime']['output']>;
  sentAt: Maybe<Scalars['DateTime']['output']>;
  status: NotificationStatus;
  title: Scalars['String']['output'];
  /**  Content */
  type: NotificationType;
  userId: Scalars['ID']['output'];
};

/**  Notification delivery channels */
export type NotificationChannel =
  /**  WhatsApp message */
  | 'EMAIL'
  /**  Email */
  | 'IN_APP'
  | 'PUSH'
  /**  Mobile push notification */
  | 'SMS'
  /**  SMS message */
  | 'WHATSAPP';

/**  Notification cursor pagination */
export type NotificationConnection = {
  __typename: 'NotificationConnection';
  edges: Array<NotificationEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type NotificationEdge = {
  __typename: 'NotificationEdge';
  cursor: Scalars['String']['output'];
  node: Notification;
};

/**  Notification responses */
export type NotificationMutationResponse = {
  __typename: 'NotificationMutationResponse';
  message: Maybe<Scalars['String']['output']>;
  notification: Maybe<Notification>;
  success: Scalars['Boolean']['output'];
};

export type NotificationOffsetPage = {
  __typename: 'NotificationOffsetPage';
  content: Array<Notification>;
  pageInfo: PageInfo;
};

export type NotificationPreferences = {
  __typename: 'NotificationPreferences';
  /**  Channel preferences */
  emailEnabled: Scalars['Boolean']['output'];
  eventReminders: Scalars['Boolean']['output'];
  eventUpdates: Scalars['Boolean']['output'];
  id: Scalars['ID']['output'];
  inAppEnabled: Scalars['Boolean']['output'];
  marketingEmails: Scalars['Boolean']['output'];
  paymentNotifications: Scalars['Boolean']['output'];
  pushEnabled: Scalars['Boolean']['output'];
  quietHoursEnd: Maybe<Scalars['String']['output']>;
  quietHoursStart: Maybe<Scalars['String']['output']>;
  /**  Timing preferences */
  reminderHoursBefore: Scalars['Int']['output'];
  smsEnabled: Scalars['Boolean']['output'];
  systemAnnouncements: Scalars['Boolean']['output'];
  teamNotifications: Scalars['Boolean']['output'];
  /**  Category preferences */
  ticketNotifications: Scalars['Boolean']['output'];
  timezone: Maybe<Scalars['String']['output']>;
  /**  Audit */
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
  whatsappEnabled: Scalars['Boolean']['output'];
};

/**  Notification status */
export type NotificationStatus =
  /**  Sent to delivery provider */
  | 'DELIVERED'
  /**  Delivery failed */
  | 'EXPIRED'
  /**  Read by user */
  | 'FAILED'
  | 'PENDING'
  /**  Not yet sent */
  | 'QUEUED'
  /**  Confirmed delivered */
  | 'READ'
  /**  Queued for delivery */
  | 'SENT';

/**
 * ============================================================================
 * SECTION 5: ENUMS - NOTIFICATION & DEVICE
 * ============================================================================
 * Notification types
 */
export type NotificationType =
  | 'ACCOUNT_SECURITY'
  | 'EVENT_APPROVED'
  | 'EVENT_CANCELLED'
  | 'EVENT_CHANGES_REQUESTED'
  | 'EVENT_REJECTED'
  /**  Event-related */
  | 'EVENT_REMINDER'
  | 'EVENT_STARTING_SOON'
  | 'EVENT_UPDATED'
  /**  Organizer-related */
  | 'ORGANIZER_APPROVED'
  | 'ORGANIZER_CHANGES_REQUESTED'
  | 'ORGANIZER_REJECTED'
  | 'ORGANIZER_SUSPENDED'
  | 'OWNERSHIP_TRANSFER_COMPLETED'
  | 'OWNERSHIP_TRANSFER_REQUESTED'
  | 'PAYMENT_FAILED'
  | 'PAYMENT_PENDING'
  /**  Payment-related */
  | 'PAYMENT_SUCCESSFUL'
  | 'PAYOUT_APPROVED'
  | 'PAYOUT_COMPLETED'
  | 'PAYOUT_FAILED'
  /**  Payout-related */
  | 'PAYOUT_REQUESTED'
  | 'QUEUE_TURN'
  | 'REFUND_APPROVED'
  | 'REFUND_PROCESSED'
  | 'REFUND_REJECTED'
  /**  Refund-related */
  | 'REFUND_REQUESTED'
  /**  System */
  | 'SYSTEM_ANNOUNCEMENT'
  /**  Organization/Team-related */
  | 'TEAM_INVITATION_RECEIVED'
  | 'TEAM_MEMBER_JOINED'
  | 'TEAM_MEMBER_LEFT'
  | 'TEAM_ROLE_CHANGED'
  | 'TICKET_EXPIRING'
  /**  Ticket-related */
  | 'TICKET_PURCHASED'
  | 'TICKET_REFUND_REQUESTED'
  | 'TICKET_TRANSFERRED'
  | 'TICKET_TRANSFER_RECEIVED'
  /**  Waitlist/Queue */
  | 'WAITLIST_AVAILABLE'
  | 'WELCOME';

/**
 * ============================================================================
 * SECTION 12: INPUT TYPES
 * ============================================================================
 * Offset-based pagination input for admin/dashboard tables
 */
export type OffsetPaginationInput = {
  page: InputMaybe<Scalars['Int']['input']>;
  size: InputMaybe<Scalars['Int']['input']>;
  sortBy: InputMaybe<Scalars['String']['input']>;
  sortDirection: InputMaybe<SortDirection>;
};

/**
 * Organization stub - owned by Identity Service
 * Events belong to organizations, enabling team-based access control
 */
export type Organization = {
  __typename: 'Organization';
  activeMembers: Maybe<Array<OrganizationMember>>;
  /**  When application was submitted for review */
  approvedAt: Maybe<Scalars['DateTime']['output']>;
  averageRating: Maybe<Scalars['Float']['output']>;
  bannerUrl: Maybe<Scalars['String']['output']>;
  businessAddress: Maybe<BusinessAddress>;
  businessEmail: Maybe<Scalars['String']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * KYB - Contact Information
   * ─────────────────────────────────────────────────────────────────────
   */
  businessPhone: Maybe<Scalars['String']['output']>;
  /**  TPIN in Zambia */
  businessRegistrationNumber: Maybe<Scalars['String']['output']>;
  /**
   * Only ONE owner per organization
   * ─────────────────────────────────────────────────────────────────────
   * KYB - Business Information
   * ─────────────────────────────────────────────────────────────────────
   */
  businessType: Maybe<BusinessType>;
  /**  True only for APPROVED with verified payout account */
  canBeEdited: Scalars['Boolean']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Capabilities (derived from status)
   * ─────────────────────────────────────────────────────────────────────
   */
  canCreateDraftEvents: Scalars['Boolean']['output'];
  /**  True for DRAFT, PENDING_REVIEW, CHANGES_REQUESTED, APPROVED */
  canPublishEvents: Scalars['Boolean']['output'];
  /**  True only for APPROVED/ACTIVE */
  canReceivePayouts: Scalars['Boolean']['output'];
  /**  True for DRAFT, CHANGES_REQUESTED, APPROVED */
  canSubmitForReview: Scalars['Boolean']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Audit Fields
   * ─────────────────────────────────────────────────────────────────────
   */
  createdAt: Scalars['DateTime']['output'];
  /**  URL-friendly identifier (unique) */
  description: Maybe<Scalars['String']['output']>;
  /**  Business verification status */
  documentsVerified: Scalars['Boolean']['output'];
  id: Scalars['ID']['output'];
  /**  True for DRAFT, CHANGES_REQUESTED */
  isApproved: Scalars['Boolean']['output'];
  /**  True for APPROVED/ACTIVE */
  isInApprovalWorkflow: Scalars['Boolean']['output'];
  /**
   * KYB verification status
   * ─────────────────────────────────────────────────────────────────────
   * Keycloak Integration
   * ─────────────────────────────────────────────────────────────────────
   */
  keycloakGroupId: Maybe<Scalars['String']['output']>;
  /**  Operational status */
  kybStatus: KybStatus;
  kybSubmittedAt: Maybe<Scalars['DateTime']['output']>;
  logoUrl: Maybe<Scalars['String']['output']>;
  memberCount: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Team Members
   * ─────────────────────────────────────────────────────────────────────
   */
  members: Maybe<Array<OrganizationMember>>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Basic Information
   * ─────────────────────────────────────────────────────────────────────
   */
  name: Scalars['String']['output'];
  /**  User ID of the owner */
  owner: Maybe<User>;
  /**
   * Keycloak group ID for this org
   * ─────────────────────────────────────────────────────────────────────
   * Ownership
   * ─────────────────────────────────────────────────────────────────────
   */
  ownerId: Scalars['ID']['output'];
  /**  KYB documents verified */
  payoutAccountVerified: Scalars['Boolean']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Payout Configuration
   * ─────────────────────────────────────────────────────────────────────
   */
  payoutConfig: Maybe<PayoutConfig>;
  pendingInvitationCount: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Pending Invitations
   * ─────────────────────────────────────────────────────────────────────
   */
  pendingInvitations: Maybe<Array<TeamInvitation>>;
  /**  When application was approved */
  rejectionReason: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  /**  Reason for rejection or changes requested */
  reviewedBy: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization Settings
   * ─────────────────────────────────────────────────────────────────────
   */
  settings: Maybe<OrganizationSettings>;
  /**  Display name */
  slug: Scalars['String']['output'];
  socialLinks: Maybe<SocialLinks>;
  /**  INDIVIDUAL or BUSINESS */
  status: OrganizationStatus;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Application & Review Information
   * ─────────────────────────────────────────────────────────────────────
   */
  submittedAt: Maybe<Scalars['DateTime']['output']>;
  tagline: Maybe<Scalars['String']['output']>;
  taxId: Maybe<Scalars['String']['output']>;
  /**
   * True for DRAFT, PENDING_REVIEW, CHANGES_REQUESTED
   * ─────────────────────────────────────────────────────────────────────
   * Statistics (computed from Catalog/Booking services)
   * ─────────────────────────────────────────────────────────────────────
   */
  totalEvents: Maybe<Scalars['Int']['output']>;
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
  totalTicketsSold: Maybe<Scalars['Int']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization Type & Status
   * ─────────────────────────────────────────────────────────────────────
   */
  type: OrganizationType;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Verification Documents
   * ─────────────────────────────────────────────────────────────────────
   */
  verificationDocuments: Maybe<Array<VerificationDocument>>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Verification Flags
   * ─────────────────────────────────────────────────────────────────────
   */
  verified: Scalars['Boolean']['output'];
  /**  Payout account verified */
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedBy: Maybe<User>;
  website: Maybe<Scalars['String']['output']>;
  /**  PACRA number in Zambia */
  yearEstablished: Maybe<Scalars['Int']['output']>;
};

/**  Organization Application Cursor Pagination (Approval Queue) */
export type OrganizationApplicationConnection = {
  __typename: 'OrganizationApplicationConnection';
  edges: Array<OrganizationApplicationEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type OrganizationApplicationEdge = {
  __typename: 'OrganizationApplicationEdge';
  cursor: Scalars['String']['output'];
  node: Organization;
};

/**
 * Input for organization application (becoming an organizer)
 * Business details required - NO registration/tax details needed
 */
export type OrganizationApplicationInput = {
  /**  URL to organization banner image */
  bannerUrl: InputMaybe<Scalars['String']['input']>;
  /**  Business contact email */
  businessEmail: InputMaybe<Scalars['String']['input']>;
  /**  Business contact phone number */
  businessPhone: InputMaybe<Scalars['String']['input']>;
  /**  City */
  city: InputMaybe<Scalars['String']['input']>;
  /**  Country (defaults to Zambia) */
  country: InputMaybe<Scalars['String']['input']>;
  /**  Description of the organization */
  description: InputMaybe<Scalars['String']['input']>;
  /**  URL to organization logo */
  logoUrl: InputMaybe<Scalars['String']['input']>;
  /**  Organization/business name (required) */
  name: Scalars['String']['input'];
  /**  Province/State */
  province: InputMaybe<Scalars['String']['input']>;
  /**  Social media links */
  socialLinks: InputMaybe<SocialLinksInput>;
  /**  Short tagline */
  tagline: InputMaybe<Scalars['String']['input']>;
  /**  Organization type: INDIVIDUAL or BUSINESS */
  type: InputMaybe<OrganizationType>;
  /**  Company website URL */
  website: InputMaybe<Scalars['String']['input']>;
};

/**  Organization Application Pagination (Approval Queue) */
export type OrganizationApplicationOffsetPage = {
  __typename: 'OrganizationApplicationOffsetPage';
  content: Array<Organization>;
  pageInfo: PageInfo;
};

/**  Organization cursor pagination */
export type OrganizationConnection = {
  __typename: 'OrganizationConnection';
  edges: Array<OrganizationEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type OrganizationEdge = {
  __typename: 'OrganizationEdge';
  cursor: Scalars['String']['output'];
  node: Organization;
};

/**
 * Organization member - links User to Organization with a role
 * ORGANIZER - Team management for organization owners and admins
 */
export type OrganizationMember = {
  __typename: 'OrganizationMember';
  createdAt: Scalars['DateTime']['output'];
  /**
   * Custom permissions that override role defaults
   * Example: ["PAYOUT_REQUEST"] grants a Manager payout access
   */
  customPermissions: Maybe<Array<Scalars['String']['output']>>;
  /**
   * Permissions explicitly denied
   * Example: ["EVENT_DELETE"] prevents deletion even for Admins
   */
  deniedPermissions: Maybe<Array<Scalars['String']['output']>>;
  id: Scalars['ID']['output'];
  invitedBy: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Invitation Tracking
   * ─────────────────────────────────────────────────────────────────────
   */
  invitedById: Maybe<Scalars['ID']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Timestamps
   * ─────────────────────────────────────────────────────────────────────
   */
  joinedAt: Scalars['DateTime']['output'];
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  organization: Maybe<Organization>;
  organizationId: Scalars['ID']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Role & Permissions
   * ─────────────────────────────────────────────────────────────────────
   */
  role: OrganizationRole;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Status
   * ─────────────────────────────────────────────────────────────────────
   */
  status: MemberStatus;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  user: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Links
   * ─────────────────────────────────────────────────────────────────────
   */
  userId: Scalars['ID']['output'];
};

/**  OrganizationMember cursor pagination */
export type OrganizationMemberConnection = {
  __typename: 'OrganizationMemberConnection';
  edges: Array<OrganizationMemberEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type OrganizationMemberEdge = {
  __typename: 'OrganizationMemberEdge';
  cursor: Scalars['String']['output'];
  node: OrganizationMember;
};

export type OrganizationMemberOffsetPage = {
  __typename: 'OrganizationMemberOffsetPage';
  content: Array<OrganizationMember>;
  pageInfo: PageInfo;
};

/**  Organization responses */
export type OrganizationMutationResponse = {
  __typename: 'OrganizationMutationResponse';
  message: Maybe<Scalars['String']['output']>;
  organization: Maybe<Organization>;
  success: Scalars['Boolean']['output'];
};

export type OrganizationOffsetPage = {
  __typename: 'OrganizationOffsetPage';
  content: Array<Organization>;
  pageInfo: PageInfo;
};

/**
 * Organization-level roles (within a tenant)
 * These roles determine what a user can do within their organization
 */
export type OrganizationRole =
  /**
   * ADMIN: Full administrative access
   * - Full access except ownership transfer
   * - Can invite/remove team members
   * - Can manage all events
   * - Can view financial reports
   * - Can request payouts (if enabled by owner)
   */
  | 'ADMIN'
  /**
   * CONTRIBUTOR: Limited access
   * - View-only access to events
   * - Can view attendee lists
   * - Can assist with check-in
   * - No event editing capabilities
   */
  | 'CONTRIBUTOR'
  /**
   * MANAGER: Event management focus
   * - Can create and manage events
   * - Can publish events
   * - Can view event analytics
   * - Limited financial access (view only)
   * - Cannot invite members
   */
  | 'MANAGER'
  /**
   * MARKETER: Marketing and analytics focus
   * - Can view all events
   * - Can manage promotions and discounts
   * - Can view analytics and reports
   * - Cannot create or edit events
   * - No financial access
   */
  | 'MARKETER'
  /**
   * OWNER: Single person who owns the organization
   * - Full access to all organization features
   * - Can transfer ownership to another member
   * - Can delete the organization
   * - Can manage billing and payouts
   * - Only ONE owner per organization
   */
  | 'OWNER';

/**  Organization settings */
export type OrganizationSettings = {
  __typename: 'OrganizationSettings';
  /**
   * Events need owner/admin approval
   * ─────────────────────────────────────────────────────────────────────
   * Team Settings
   * ─────────────────────────────────────────────────────────────────────
   */
  allowMembersToInvite: Scalars['Boolean']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Event Defaults
   * ─────────────────────────────────────────────────────────────────────
   */
  defaultEventVisibility: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  /**  Can non-owners invite members */
  inviteRequiresApproval: Scalars['Boolean']['output'];
  /**
   * Max team size (null = unlimited)
   * ─────────────────────────────────────────────────────────────────────
   * Permission Settings
   * ─────────────────────────────────────────────────────────────────────
   */
  managersCanRequestPayouts: Scalars['Boolean']['output'];
  marketersCanViewFinancials: Scalars['Boolean']['output'];
  /**  Invites need owner approval */
  maxTeamMembers: Maybe<Scalars['Int']['output']>;
  notifyOwnerOnEventCreated: Scalars['Boolean']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Notification Settings
   * ─────────────────────────────────────────────────────────────────────
   */
  notifyOwnerOnMemberJoin: Scalars['Boolean']['output'];
  notifyOwnerOnPayoutRequest: Scalars['Boolean']['output'];
  organizationId: Scalars['ID']['output'];
  /**  PUBLIC, PRIVATE, UNLISTED */
  requireEventApproval: Scalars['Boolean']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Audit
   * ─────────────────────────────────────────────────────────────────────
   */
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedById: Maybe<Scalars['ID']['output']>;
};

/**
 * ============================================================================
 * SECTION 4: ENUMS - ORGANIZATION & TEAM
 * ============================================================================
 * Organization status in approval workflow
 * APPROVAL-BASED ONBOARDING:
 * 1. User applies → DRAFT
 * 2. User fills details and submits → PENDING_REVIEW
 * 3. Admin: APPROVED / CHANGES_REQUESTED / REJECTED
 * 4. User can create draft events during approval process
 */
export type OrganizationStatus =
  /**
   * Application rejected
   * Operational statuses (post-approval)
   */
  | 'ACTIVE'
  /**  Admin requested changes */
  | 'APPROVED'
  /**  Application submitted for admin review */
  | 'CHANGES_REQUESTED'
  /**  Approval workflow statuses */
  | 'DRAFT'
  /**  Suspended by admin */
  | 'INACTIVE'
  /**  Deactivated by owner */
  | 'PENDING_DELETION'
  /**  Initial status - application in progress */
  | 'PENDING_REVIEW'
  /**  Application approved - can publish events */
  | 'REJECTED'
  /**  Organization is active (same as APPROVED) */
  | 'SUSPENDED';

/**  Organization type */
export type OrganizationType =
  /**  Sole proprietor / personal account */
  | 'BUSINESS'
  | 'INDIVIDUAL';

/**
 * ============================================================================
 * SECTION 13: STATISTICS TYPES
 * ============================================================================
 */
export type OrganizerStatistics = {
  __typename: 'OrganizerStatistics';
  activeEvents: Scalars['Int']['output'];
  averageRating: Maybe<Scalars['Float']['output']>;
  cancelledEvents: Scalars['Int']['output'];
  completedEvents: Scalars['Int']['output'];
  completedPayouts: Scalars['BigDecimal']['output'];
  organizationId: Maybe<Scalars['ID']['output']>;
  organizerId: Scalars['ID']['output'];
  pendingPayouts: Scalars['BigDecimal']['output'];
  period: Maybe<Scalars['String']['output']>;
  totalEvents: Scalars['Int']['output'];
  totalRevenue: Scalars['BigDecimal']['output'];
  totalReviews: Scalars['Int']['output'];
  totalTicketsSold: Scalars['Int']['output'];
};

/**
 * ============================================================================
 * SECTION 19c: PHONE OTP RESPONSE TYPES
 * ============================================================================
 */
export type OtpRequestResponse = {
  __typename: 'OtpRequestResponse';
  expiresIn: Scalars['Int']['output'];
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type OtpResponse = {
  __typename: 'OtpResponse';
  attemptsRemaining: Maybe<Scalars['Int']['output']>;
  cooldownSeconds: Maybe<Scalars['Int']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Ownership transfer request */
export type OwnershipTransferRequest = {
  __typename: 'OwnershipTransferRequest';
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  completedAt: Maybe<Scalars['DateTime']['output']>;
  currentOwner: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Transfer Parties
   * ─────────────────────────────────────────────────────────────────────
   */
  currentOwnerId: Scalars['ID']['output'];
  expiresAt: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Timestamps
   * ─────────────────────────────────────────────────────────────────────
   */
  initiatedAt: Scalars['DateTime']['output'];
  newOwner: Maybe<User>;
  newOwnerId: Scalars['ID']['output'];
  organization: Maybe<Organization>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization
   * ─────────────────────────────────────────────────────────────────────
   */
  organizationId: Scalars['ID']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Transfer Details
   * ─────────────────────────────────────────────────────────────────────
   */
  reason: Maybe<Scalars['String']['output']>;
  /**
   * Token for new owner to accept
   * ─────────────────────────────────────────────────────────────────────
   * Status
   * ─────────────────────────────────────────────────────────────────────
   */
  status: TransferStatus;
  transferToken: Scalars['String']['output'];
};

/**  Ownership transfer response */
export type OwnershipTransferResponse = {
  __typename: 'OwnershipTransferResponse';
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  transfer: Maybe<OwnershipTransferRequest>;
};

/**  Shared type for cursor-based pagination - must have @shareable for federation */
export type PageInfo = {
  __typename: 'PageInfo';
  currentPage: Maybe<Scalars['Int']['output']>;
  endCursor: Maybe<Scalars['String']['output']>;
  hasNext: Maybe<Scalars['Boolean']['output']>;
  hasNextPage: Maybe<Scalars['Boolean']['output']>;
  hasPrevious: Maybe<Scalars['Boolean']['output']>;
  hasPreviousPage: Maybe<Scalars['Boolean']['output']>;
  pageSize: Maybe<Scalars['Int']['output']>;
  startCursor: Maybe<Scalars['String']['output']>;
  totalCount: Maybe<Scalars['Int']['output']>;
  totalElements: Maybe<Scalars['Int']['output']>;
  totalPages: Maybe<Scalars['Int']['output']>;
};

/**
 * ============================================================================
 * SECTION 11: PAGINATION TYPES
 * ============================================================================
 * Shared type - must have @shareable on all fields for federation
 */
export type PaginationInfo = {
  __typename: 'PaginationInfo';
  currentPage: Maybe<Scalars['Int']['output']>;
  hasNext: Maybe<Scalars['Boolean']['output']>;
  hasNextPage: Maybe<Scalars['Boolean']['output']>;
  hasPrevious: Maybe<Scalars['Boolean']['output']>;
  hasPreviousPage: Maybe<Scalars['Boolean']['output']>;
  pageNumber: Maybe<Scalars['Int']['output']>;
  pageSize: Scalars['Int']['output'];
  totalCount: Maybe<Scalars['Int']['output']>;
  totalElements: Maybe<Scalars['Int']['output']>;
  totalPages: Scalars['Int']['output'];
};

/**
 * For payment tracking, use PaymentAttempt queries
 * For accounting/financial records, use JournalEntry queries
 * ------------------------------
 * ------------------------------
 * PAYMENT ATTEMPT
 * ADMIN-ONLY - Tracks the complete lifecycle of a payment operation
 * This is the OPERATIONAL layer tracking HOW a payment progresses through PawaPay,
 * distinct from JournalEntry which records THAT money moved in the accounting ledger.
 * ------------------------------
 */
export type PaymentAttempt = {
  __typename: 'PaymentAttempt';
  /**
   * Ticket number sent to PawaPay
   * Payment Details
   */
  amount: Scalars['BigDecimal']['output'];
  amountVerified: Scalars['Boolean']['output'];
  /**  API Interaction Tracking */
  apiCalledAt: Maybe<Scalars['DateTime']['output']>;
  apiDurationMs: Maybe<Scalars['Int']['output']>;
  apiHttpStatus: Maybe<Scalars['Int']['output']>;
  apiRespondedAt: Maybe<Scalars['DateTime']['output']>;
  /**  UUID sent to PawaPay (idempotency key) */
  attemptNumber: Scalars['String']['output'];
  /**  Multi-tenant tracking */
  buyerId: Scalars['String']['output'];
  /**  Security & Audit */
  clientIpAddress: Maybe<Scalars['String']['output']>;
  clientReferenceId: Maybe<Scalars['String']['output']>;
  commissionId: Maybe<Scalars['String']['output']>;
  /**  Human-readable: PAY-{YYYYMMDD}-{XXXXX} */
  correlationId: Maybe<Scalars['String']['output']>;
  /**  PawaPay's transaction ID (set after COMPLETED) */
  country: Maybe<Scalars['String']['output']>;
  /**  Timestamps */
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  customerErrorMessage: Maybe<Scalars['String']['output']>;
  customerMessage: Maybe<Scalars['String']['output']>;
  /**  Identity & Correlation */
  depositId: Scalars['String']['output'];
  escrowTransactionId: Maybe<Scalars['String']['output']>;
  eventId: Maybe<Scalars['String']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  /**  Failure Tracking */
  failureCode: Maybe<Scalars['String']['output']>;
  failureMessage: Maybe<Scalars['String']['output']>;
  /**  Fulfillment Tracking */
  fulfilled: Scalars['Boolean']['output'];
  fulfilledAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  journalEntryId: Maybe<Scalars['String']['output']>;
  lastError: Maybe<Scalars['String']['output']>;
  lastPollResult: Maybe<Scalars['String']['output']>;
  lastPollStatus: Maybe<Scalars['String']['output']>;
  lastPolledAt: Maybe<Scalars['DateTime']['output']>;
  nextRetryAt: Maybe<Scalars['DateTime']['output']>;
  notes: Maybe<Scalars['String']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Maybe<Scalars['String']['output']>;
  /**  MTN_MOMO_ZMB, AIRTEL_OAPI_ZMB, ZAMTEL_ZMB */
  payerPhone: Scalars['String']['output'];
  /**  Polling & Recovery */
  pollCount: Scalars['Int']['output'];
  provider: Scalars['String']['output'];
  providerStatus: Maybe<Scalars['String']['output']>;
  /**  PawaPay's status (ACCEPTED, PROCESSING, COMPLETED, FAILED) */
  providerTransactionId: Maybe<Scalars['String']['output']>;
  requestId: Maybe<Scalars['String']['output']>;
  retryCount: Scalars['Int']['output'];
  reviewNotes: Maybe<Scalars['String']['output']>;
  /**  Review */
  reviewStatus: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  sessionId: Maybe<Scalars['String']['output']>;
  /**  Status Tracking */
  status: PaymentAttemptStatus;
  /**
   * Links related operations
   * Business Entity References
   */
  ticketId: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
  /**  Verification Flags (OWASP compliance) */
  verifiedBeforeFulfillment: Scalars['Boolean']['output'];
  webhookProcessed: Scalars['Boolean']['output'];
  /**  Webhook Tracking */
  webhookReceivedAt: Maybe<Scalars['DateTime']['output']>;
  webhookSignatureValid: Maybe<Scalars['Boolean']['output']>;
  webhookSourceIp: Maybe<Scalars['String']['output']>;
};

/**
 * TransactionMutationResponse - REMOVED (replaced by PaymentAttemptMutationResponse)
 * ProcessTransactionMutationResponse - REMOVED (use PaymentAttemptMutationResponse)
 * Payment Attempt mutation response type
 */
export type PaymentAttemptMutationResponse = {
  __typename: 'PaymentAttemptMutationResponse';
  data: Maybe<PaymentAttempt>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**
 * Payment Attempt Status - Tracks the lifecycle of a payment operation
 * Used by PaymentAttempt to track PawaPay mobile money payment lifecycle
 */
export type PaymentAttemptStatus =
  /**  15-minute timeout reached without confirmation */
  | 'CANCELLED'
  /**  Payment confirmed by PawaPay, ready for fulfillment */
  | 'COMPLETED'
  /**  PawaPay is processing the payment */
  | 'CONFIRMED'
  | 'CREATED'
  /**  PawaPay rejected our request (invalid phone, unsupported provider) */
  | 'EXPIRED'
  /**  Fulfillment done (escrow credited, commission created, journal posted) */
  | 'FAILED'
  /**  Record created, PawaPay API not yet called */
  | 'PENDING_APPROVAL'
  /**  PawaPay accepted, waiting for customer to approve on phone */
  | 'PROCESSING'
  /**  Payment failed (customer declined, insufficient funds, etc.) */
  | 'REJECTED';

/**
 * ============================================================================
 * SECTION 9: SUPPORTING TYPES
 * Embedded types used by primary entities
 * ============================================================================
 * ORGANIZER/ADMIN - Payment details embedded in ticket
 */
export type PaymentInfo = {
  __typename: 'PaymentInfo';
  amount: Maybe<Scalars['BigDecimal']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  paymentDate: Maybe<Scalars['DateTime']['output']>;
  paymentId: Maybe<Scalars['String']['output']>;
  paymentMethod: Maybe<Scalars['String']['output']>;
  providerReference: Maybe<Scalars['String']['output']>;
  status: Maybe<Scalars['String']['output']>;
  transactionId: Maybe<Scalars['String']['output']>;
};

/**  Payment methods supported */
export type PaymentMethod =
  /**  Visa, Mastercard */
  | 'BANK_TRANSFER'
  /**  MTN, Airtel, Zamtel */
  | 'CARD'
  | 'MOBILE_MONEY';

/**
 * Bank account details for payout configuration (embedded type)
 * NOTE: Full BankAccount entity is owned by Booking Service
 */
export type PayoutBankDetails = {
  __typename: 'PayoutBankDetails';
  /**  Last 4 digits only */
  accountHolderName: Maybe<Scalars['String']['output']>;
  accountNumber: Maybe<Scalars['String']['output']>;
  accountType: Maybe<Scalars['String']['output']>;
  bankCode: Maybe<Scalars['String']['output']>;
  bankName: Maybe<Scalars['String']['output']>;
  branchCode: Maybe<Scalars['String']['output']>;
  /**  SWIFT code */
  branchName: Maybe<Scalars['String']['output']>;
  /**  Masked for display */
  maskedAccountNumber: Maybe<Scalars['String']['output']>;
  /**  CHECKING, SAVINGS, BUSINESS */
  verified: Scalars['Boolean']['output'];
};

/**  Payout configuration for an organization */
export type PayoutConfig = {
  __typename: 'PayoutConfig';
  /**  Minimum payout in ZMW */
  bankAccount: Maybe<PayoutBankDetails>;
  /**  Has a configured payout method */
  canProcessPayouts: Scalars['Boolean']['output'];
  commissionRate: Maybe<Scalars['Float']['output']>;
  /**  Payout config verified */
  isConfigured: Scalars['Boolean']['output'];
  /**  Platform commission (e.g., 0.05 = 5%) */
  minimumPayoutAmount: Maybe<Scalars['Float']['output']>;
  mobileMoneyAccount: Maybe<MobileMoneyAccount>;
  preferredMethod: Maybe<PayoutMethod>;
  schedule: Maybe<PayoutSchedule>;
  verified: Scalars['Boolean']['output'];
};

/**  Payout issue types for recovery operations */
export type PayoutIssueType =
  | 'BANK_REJECTED'
  /**  Not enough funds in escrow */
  | 'COMPLIANCE_HOLD'
  /**  Payout processing timed out */
  | 'DUPLICATE_REQUEST'
  /**  Account details are invalid */
  | 'INSUFFICIENT_ESCROW'
  /**  Bank rejected the payout */
  | 'INVALID_ACCOUNT_DETAILS'
  /**  Potential duplicate payout */
  | 'OTHER'
  /**  Technical failure during processing */
  | 'PROVIDER_ERROR'
  /**  Held for compliance review */
  | 'SUSPECTED_FRAUD'
  /**  Flagged for potential fraud */
  | 'TECHNICAL_ERROR'
  /**  Payment provider error */
  | 'TIMEOUT';

/**  ADMIN - Payout issue type breakdown */
export type PayoutIssueTypeStats = {
  __typename: 'PayoutIssueTypeStats';
  count: Scalars['Int']['output'];
  issueType: PayoutIssueType;
  percentage: Scalars['Float']['output'];
  totalAmount: Scalars['BigDecimal']['output'];
  unresolvedCount: Scalars['Int']['output'];
};

/**  Payout method types */
export type PayoutMethod =
  | 'BANK_TRANSFER'
  /**  Mobile money (MTN, Airtel, Zamtel) */
  | 'CHEQUE'
  /**  Bank transfer to verified account */
  | 'MOBILE_MONEY';

/**  ADMIN - Payout recovery summary for admin dashboard */
export type PayoutRecoverySummary = {
  __typename: 'PayoutRecoverySummary';
  averageResolutionTimeMinutes: Maybe<Scalars['Float']['output']>;
  issuesByType: Array<PayoutIssueTypeStats>;
  pendingReviewCount: Scalars['Int']['output'];
  recentlyResolvedCount: Scalars['Int']['output'];
  retryablePayoutsCount: Scalars['Int']['output'];
  stuckPayoutsCount: Scalars['Int']['output'];
  totalAmountAtRisk: Scalars['BigDecimal']['output'];
  totalPayoutsForReview: Scalars['Int']['output'];
  underReviewCount: Scalars['Int']['output'];
};

/**
 * ------------------------------
 * PAYOUT REQUEST
 * ORGANIZER/ADMIN - Payout request workflow
 * Organizers create and track their payout requests, admins approve/process
 * ------------------------------
 */
export type PayoutRequest = {
  __typename: 'PayoutRequest';
  accountNumber: Maybe<Scalars['String']['output']>;
  actualPayoutDate: Maybe<Scalars['DateTime']['output']>;
  approvedAt: Maybe<Scalars['DateTime']['output']>;
  approvedBy: Maybe<Scalars['String']['output']>;
  bankAccount: Maybe<BankAccount>;
  bankAccountId: Scalars['String']['output'];
  bankAccountName: Maybe<Scalars['String']['output']>;
  bankName: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  escrowAccountId: Scalars['String']['output'];
  eventId: Maybe<Scalars['String']['output']>;
  eventTitle: Maybe<Scalars['String']['output']>;
  expectedPayoutDate: Maybe<Scalars['DateTime']['output']>;
  externalTransactionId: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isStuck: Maybe<Scalars['Boolean']['output']>;
  /**  Recovery and Review fields (admin only) */
  issueType: Maybe<PayoutIssueType>;
  lastError: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  netPayoutAmount: Scalars['BigDecimal']['output'];
  notes: Maybe<Scalars['String']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  /**  Multi-tenant tracking */
  organizerName: Maybe<Scalars['String']['output']>;
  paymentReference: Maybe<Scalars['String']['output']>;
  payoutMethod: Maybe<PayoutMethod>;
  platformFee: Maybe<Scalars['BigDecimal']['output']>;
  processedAt: Maybe<Scalars['DateTime']['output']>;
  processedBy: Maybe<Scalars['String']['output']>;
  processingFee: Maybe<Scalars['BigDecimal']['output']>;
  rejectedAt: Maybe<Scalars['DateTime']['output']>;
  rejectedBy: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  requestId: Scalars['String']['output'];
  requestedAmount: Scalars['BigDecimal']['output'];
  requestedAt: Scalars['DateTime']['output'];
  requestedBy: Scalars['String']['output'];
  resolutionNotes: Maybe<Scalars['String']['output']>;
  resolutionType: Maybe<PayoutResolutionType>;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  resolvedBy: Maybe<Scalars['String']['output']>;
  retryCount: Maybe<Scalars['Int']['output']>;
  reviewNotes: Maybe<Scalars['String']['output']>;
  reviewStatus: Maybe<PayoutReviewStatus>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  status: PayoutRequestStatus;
  stuckAt: Maybe<Scalars['DateTime']['output']>;
  stuckReason: Maybe<Scalars['String']['output']>;
  taxAmount: Maybe<Scalars['BigDecimal']['output']>;
  transactionId: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

/**  Payout Request cursor pagination */
export type PayoutRequestConnection = {
  __typename: 'PayoutRequestConnection';
  edges: Array<PayoutRequestEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type PayoutRequestEdge = {
  __typename: 'PayoutRequestEdge';
  cursor: Scalars['String']['output'];
  node: PayoutRequest;
};

export type PayoutRequestFilterInput = {
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  escrowAccountId: InputMaybe<Scalars['String']['input']>;
  eventId: InputMaybe<Scalars['String']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  payoutMethod: InputMaybe<PayoutMethod>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  status: InputMaybe<PayoutRequestStatus>;
};

export type PayoutRequestMutationResponse = {
  __typename: 'PayoutRequestMutationResponse';
  data: Maybe<PayoutRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type PayoutRequestOffsetPage = {
  __typename: 'PayoutRequestOffsetPage';
  data: Array<PayoutRequest>;
  pagination: PaginationInfo;
};

/**
 * TransactionRecoverySummary - REMOVED (replaced by PaymentAttempt tracking)
 * TransactionIssueTypeStats - REMOVED
 * ADMIN - Payout request statistics for financial dashboard
 */
export type PayoutRequestStats = {
  __typename: 'PayoutRequestStats';
  approvedPayoutRequests: Scalars['Int']['output'];
  completedPayoutRequests: Scalars['Int']['output'];
  failedPayoutRequests: Scalars['Int']['output'];
  pendingPayoutAmount: Scalars['BigDecimal']['output'];
  pendingPayoutRequests: Scalars['Int']['output'];
  processingPayoutRequests: Scalars['Int']['output'];
  totalPayoutAmount: Scalars['BigDecimal']['output'];
  totalPayoutRequests: Scalars['Int']['output'];
};

/**  Payout request status */
export type PayoutRequestStatus =
  | 'APPROVED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'FAILED'
  | 'PENDING'
  | 'PROCESSING'
  | 'REJECTED';

/**  How payout issues are resolved */
export type PayoutResolutionType =
  /**  Successfully retried */
  | 'ACCOUNT_UPDATED'
  | 'AUTO_RESOLVED'
  /**  Written off as loss */
  | 'ESCALATED'
  /**  System automatically resolved */
  | 'MANUAL_APPROVAL'
  /**  Admin manually approved */
  | 'MANUAL_REJECTION'
  /**  Bank account details corrected */
  | 'REFUNDED_TO_ESCROW'
  /**  Admin manually rejected */
  | 'RETRIED_SUCCESS'
  /**  Amount returned to escrow */
  | 'WRITTEN_OFF';

/**  Review status for payout requests requiring attention */
export type PayoutReviewStatus =
  /**  Review completed */
  | 'ESCALATED'
  | 'NONE'
  /**  No review needed */
  | 'PENDING_REVIEW'
  /**  Currently being reviewed */
  | 'REVIEWED'
  /**  Waiting for review */
  | 'UNDER_REVIEW';

/**  Payout schedule */
export type PayoutSchedule =
  | 'BIWEEKLY'
  | 'DAILY'
  | 'MANUAL'
  | 'MONTHLY'
  | 'WEEKLY';

/**
 * ============================================================================
 * SECTION 11: PERMISSION TYPES
 * ============================================================================
 * Permission definition
 */
export type Permission = {
  __typename: 'Permission';
  /**  Where this permission applies */
  active: Scalars['Boolean']['output'];
  category: Maybe<Scalars['String']['output']>;
  /**  Human-readable name */
  code: Scalars['String']['output'];
  createdAt: Scalars['DateTime']['output'];
  /**  System code (e.g., EVENT_CREATE) */
  description: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  /**  ORGANIZATION, EVENT, FINANCIAL, etc. */
  scope: PermissionScope;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type PermissionScope =
  /**  Organization-scoped permission */
  | 'EVENT'
  /**  Platform-wide permission */
  | 'ORGANIZATION'
  | 'PLATFORM';

export type PhoneAuthPayload = {
  __typename: 'PhoneAuthPayload';
  accessToken: Maybe<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  refreshToken: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  user: Maybe<User>;
};

/**  Platform Account (OPERATING, RESERVE, TAX_HOLDING) */
export type PlatformAccount = {
  __typename: 'PlatformAccount';
  accountType: PlatformAccountType;
  balance: Scalars['BigDecimal']['output'];
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  lastUpdatedAt: Maybe<Scalars['DateTime']['output']>;
  name: Scalars['String']['output'];
};

/**  Platform Account mutation response */
export type PlatformAccountMutationResponse = {
  __typename: 'PlatformAccountMutationResponse';
  data: Maybe<PlatformAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Platform account types */
export type PlatformAccountType =
  | 'OPERATING'
  | 'RESERVE'
  | 'TAX_HOLDING';

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * PLATFORM CONFIGURATION (Admin Only)
 * ─────────────────────────────────────────────────────────────────────────────
 * Configuration is stored in database for runtime flexibility.
 * Admin can update these settings without deployment.
 */
export type PlatformConfiguration = {
  __typename: 'PlatformConfiguration';
  adminNotificationChannel: ApprovalNotificationChannel;
  allowSelfApproval: Scalars['Boolean']['output'];
  /**  APPROVAL SLA SETTINGS */
  approvalSlaHours: Scalars['Int']['output'];
  /**  Default: 72 hours (3 days) */
  approvalWarningThresholdHours: Scalars['Int']['output'];
  /**
   * Warning at X hours before deadline
   * AUTO-ESCALATION SETTINGS
   */
  autoEscalationEnabled: Scalars['Boolean']['output'];
  escalationDelayHours: Scalars['Int']['output'];
  /**  Hours after SLA breach to escalate */
  escalationRecipientRole: Scalars['String']['output'];
  /**  Role to escalate to (e.g., "SENIOR_ADMIN") */
  escalationReminderIntervalHours: Scalars['Int']['output'];
  id: Scalars['ID']['output'];
  /**  Reminder interval after escalation */
  maxEscalationReminders: Scalars['Int']['output'];
  /**
   * Max reminders before marking critical
   * NOTIFICATION SETTINGS
   */
  organizerNotificationChannel: ApprovalNotificationChannel;
  requireCommentsOnChangesRequested: Scalars['Boolean']['output'];
  /**  WORKFLOW SETTINGS */
  requireCommentsOnRejection: Scalars['Boolean']['output'];
  sendEscalationNotifications: Scalars['Boolean']['output'];
  sendSlaWarningNotifications: Scalars['Boolean']['output'];
  /**
   * Can organizer approve own events?
   * Audit fields
   */
  updatedAt: Scalars['DateTime']['output'];
  updatedBy: Scalars['String']['output'];
};

export type PlatformConfigurationMutationResponse = {
  __typename: 'PlatformConfigurationMutationResponse';
  data: Maybe<PlatformConfiguration>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type PlatformStatistics = {
  __typename: 'PlatformStatistics';
  activeUsers30Days: Scalars['Int']['output'];
  pendingOrganizerApplications: Scalars['Int']['output'];
  period: Maybe<Scalars['String']['output']>;
  totalEvents: Scalars['Int']['output'];
  totalOrganizations: Scalars['Int']['output'];
  totalOrganizers: Scalars['Int']['output'];
  totalRevenue: Scalars['BigDecimal']['output'];
  totalTicketsSold: Scalars['Int']['output'];
  totalUsers: Scalars['Int']['output'];
};

/**  ADMIN - Platform-wide financial summary for admin dashboard */
export type PlatformSummary = {
  __typename: 'PlatformSummary';
  activeEscrowAccounts: Scalars['Int']['output'];
  availableForPayout: Scalars['BigDecimal']['output'];
  closedEscrowAccounts: Scalars['Int']['output'];
  completedPayoutRequests: Scalars['Int']['output'];
  completedTransactions: Scalars['Int']['output'];
  failedTransactions: Scalars['Int']['output'];
  lockedEscrowAccounts: Scalars['Int']['output'];
  payoutEligibleAccounts: Scalars['Int']['output'];
  pendingPayoutRequests: Scalars['Int']['output'];
  pendingTransactions: Scalars['Int']['output'];
  /**  Currency */
  primaryCurrency: Scalars['String']['output'];
  totalCommissions: Scalars['BigDecimal']['output'];
  totalDeposits: Scalars['BigDecimal']['output'];
  /**  Escrow Account Metrics */
  totalEscrowAccounts: Scalars['Int']['output'];
  /**  Balance Metrics */
  totalEscrowBalance: Scalars['BigDecimal']['output'];
  totalPayoutAmount: Scalars['BigDecimal']['output'];
  /**  Payout Metrics */
  totalPayoutRequests: Scalars['Int']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalTicketRevenue: Scalars['BigDecimal']['output'];
  /**  Ticket Metrics */
  totalTicketsSold: Scalars['Int']['output'];
  totalTransactionVolume: Scalars['BigDecimal']['output'];
  /**  Transaction Metrics */
  totalTransactions: Scalars['Int']['output'];
  totalWithdrawals: Scalars['BigDecimal']['output'];
};

/** Input for processing a PawaPay webhook callback. */
export type ProcessPaymentWebhookInput = {
  /** UUID of the payment attempt (depositId) */
  depositId: Scalars['String']['input'];
  /** Failure code (if FAILED) */
  failureCode: InputMaybe<Scalars['String']['input']>;
  /** Failure message (if FAILED) */
  failureMessage: InputMaybe<Scalars['String']['input']>;
  /** Status from PawaPay (COMPLETED, FAILED, PROCESSING) */
  providerStatus: Scalars['String']['input'];
  /** PawaPay transaction ID (if COMPLETED) */
  providerTransactionId: InputMaybe<Scalars['String']['input']>;
  /** Whether webhook signature was valid */
  signatureValid: Scalars['Boolean']['input'];
  /** Source IP of webhook request */
  sourceIp: Scalars['String']['input'];
  /** Raw webhook payload for audit */
  webhookPayload: Scalars['String']['input'];
};

export type ProcessPayoutRequestMutationResponse = {
  __typename: 'ProcessPayoutRequestMutationResponse';
  data: Maybe<PayoutRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ProcessRefundRequestMutationResponse = {
  __typename: 'ProcessRefundRequestMutationResponse';
  data: Maybe<RefundRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**
 * ------------------------------
 * PROMO CODE
 * ORGANIZER - Promo code management for marketing campaigns
 * Organizers create and track promo codes for their events
 * ------------------------------
 */
export type PromoCode = {
  __typename: 'PromoCode';
  applicableTiers: Maybe<Array<Scalars['String']['output']>>;
  code: Scalars['String']['output'];
  createdAt: Scalars['DateTime']['output'];
  currentUses: Scalars['Int']['output'];
  discountType: DiscountType;
  discountValue: Scalars['BigDecimal']['output'];
  eventId: Maybe<Scalars['ID']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  maxDiscountAmount: Maybe<Scalars['BigDecimal']['output']>;
  maxUses: Maybe<Scalars['Int']['output']>;
  minPurchaseAmount: Maybe<Scalars['BigDecimal']['output']>;
  organizerId: Maybe<Scalars['ID']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  validFrom: Maybe<Scalars['DateTime']['output']>;
  validUntil: Maybe<Scalars['DateTime']['output']>;
};

/**  MOBILE - Promo code validation result for checkout */
export type PromoCodeValidation = {
  __typename: 'PromoCodeValidation';
  discountAmount: Maybe<Scalars['BigDecimal']['output']>;
  errorMessage: Maybe<Scalars['String']['output']>;
  promoCode: Maybe<PromoCode>;
  valid: Scalars['Boolean']['output'];
};

/**
 * ------------------------------
 * PROVINCE
 * ------------------------------
 */
export type Province = {
  __typename: 'Province';
  cityCount: Maybe<Scalars['Int']['output']>;
  code: Scalars['String']['output'];
  country: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  formattedName: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedBy: Maybe<Scalars['String']['output']>;
};

export type ProvinceConnection = {
  __typename: 'ProvinceConnection';
  edges: Array<ProvinceEdge>;
  pageInfo: PageInfo;
};

export type ProvinceEdge = {
  __typename: 'ProvinceEdge';
  cursor: Scalars['String']['output'];
  node: Province;
};

export type ProvinceMutationResponse = {
  __typename: 'ProvinceMutationResponse';
  data: Maybe<Province>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ProvinceOffsetPage = {
  __typename: 'ProvinceOffsetPage';
  content: Array<Province>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

export type PurchaseTicketMutationResponse = {
  __typename: 'PurchaseTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type Query = {
  __typename: 'Query';
  accountBalance: Maybe<AccountBalance>;
  accountSummary: Maybe<AccountSummary>;
  /**  ADMIN - Cursor pagination for escalations (mobile admin) */
  activeEscalationsCursorPagination: ApprovalEscalationConnection;
  /**  ADMIN - Get active escalations (offset pagination for dashboard) */
  activeEscalationsOffsetPagination: ApprovalEscalationOffsetPage;
  activeEventCategoriesCursorPagination: EventCategoryConnection;
  activeEventCategoriesOffsetPagination: EventCategoryOffsetPage;
  /**  ADMIN - Get all permissions with optional scope filter */
  allPermissions: Array<Permission>;
  /**  ORGANIZER/ADMIN - Determine valid status transitions for workflow UI */
  allowedStatusTransitions: Array<EventStatus>;
  /**
   * ========================================================================
   * APPROVAL ESCALATION QUERIES (Admin Only)
   * ========================================================================
   * ADMIN - Get specific escalation by ID
   */
  approvalEscalation: Maybe<ApprovalEscalation>;
  /**
   * ========================================================================
   * APPROVAL STATISTICS QUERIES (Admin Only)
   * ========================================================================
   * ADMIN - Get approval workflow statistics for dashboard
   */
  approvalStats: ApprovalStats;
  /**
   * ========================================================================
   * APPROVAL TIMELINE QUERIES
   * Approval workflows are admin-only - platform administrators manage event approvals
   * Organizers can see their own approval status via event.status field
   * ========================================================================
   * ADMIN - View approval timeline for any event (approval workflow visibility)
   */
  approvalTimeline: Maybe<ApprovalTimeline>;
  approvalTimelinesByOrganizerCursorPagination: ApprovalTimelineConnection;
  approvalTimelinesByOrganizerOffsetPagination: ApprovalTimelineOffsetPage;
  /**  ADMIN - Cursor pagination for approval timeline lists (if admin uses mobile) */
  approvalTimelinesCursorPagination: ApprovalTimelineConnection;
  /**
   * ---------------------------------------------------------------------------
   * ADMIN APPROVAL QUERIES - Offset pagination for admin dashboard tables
   * Approval workflow management is admin-only functionality
   * ---------------------------------------------------------------------------
   */
  approvalTimelinesOffsetPagination: ApprovalTimelineOffsetPage;
  approvedNotPublishedEventsCursorPagination: EventConnection;
  approvedNotPublishedEventsOffsetPagination: EventOffsetPage;
  /**  PUBLIC - Available tiers for customer ticket purchase (mobile app) */
  availableTicketTiers: Array<TicketTier>;
  /**
   * ========================================================================
   * BANK ACCOUNT QUERIES
   * ORGANIZER - Manage own bank accounts
   * ADMIN - View and verify all bank accounts
   * ========================================================================
   * ORGANIZER/ADMIN - Bank account lookups
   */
  bankAccount: Maybe<BankAccount>;
  bankAccountsByOrganizer: Array<BankAccount>;
  calculateRefundAmount: RefundCalculation;
  cancelledEventsCursorPagination: EventConnection;
  cancelledEventsOffsetPagination: EventOffsetPage;
  /**
   * ========================================================================
   * CHARGEBACK QUERIES
   * ADMIN-ONLY - Chargeback management
   * ========================================================================
   * ADMIN - Chargeback queries
   */
  chargeback: Maybe<ChargebackRecord>;
  chargebackByChargebackId: Maybe<ChargebackRecord>;
  chargebackStats: ChargebackStats;
  chargebacksByEvent: ChargebackOffsetPage;
  chargebacksByOrganizer: ChargebackOffsetPage;
  chargebacksOffsetPagination: ChargebackOffsetPage;
  chargebacksPendingRecovery: Array<ChargebackRecord>;
  /**
   * ========================================================================
   * FINANCIAL ENGINE QUERIES - Double-Entry Bookkeeping
   * ADMIN-ONLY - Core accounting infrastructure
   * ========================================================================
   * ADMIN - Chart of Accounts queries
   */
  chartOfAccounts: Array<ChartOfAccountsEntry>;
  chartOfAccountsByCode: Maybe<ChartOfAccountsEntry>;
  chartOfAccountsByType: Array<ChartOfAccountsEntry>;
  chartOfAccountsEntry: Maybe<ChartOfAccountsEntry>;
  chartOfAccountsOffsetPagination: ChartOfAccountsOffsetPage;
  citiesByCountryCursorPagination: CityConnection;
  citiesByCountryOffsetPagination: CityOffsetPage;
  citiesByProvinceCursorPagination: CityConnection;
  citiesByProvinceOffsetPagination: CityOffsetPage;
  /**
   * ---------------------------------------------------------------------------
   * PUBLIC CITY QUERIES - Cursor pagination for mobile infinite scroll
   * Cities are public reference data needed for location-based filtering
   * ---------------------------------------------------------------------------
   */
  citiesCursorPagination: CityConnection;
  /**
   * ---------------------------------------------------------------------------
   * ADMIN CITY QUERIES - Offset pagination for admin dashboard tables
   * Only platform admins manage city reference data
   * ---------------------------------------------------------------------------
   */
  citiesOffsetPagination: CityOffsetPage;
  /**  PUBLIC - Used for filtering events to cities that have events */
  citiesWithEvents: Array<City>;
  /**
   * ========================================================================
   * CITY QUERIES
   * Cities are reference data used for filtering - public for event discovery
   * ========================================================================
   * PUBLIC - Single city lookup for event location display
   */
  city: Maybe<City>;
  completedEventsCursorPagination: EventConnection;
  completedEventsOffsetPagination: EventOffsetPage;
  confirmedUnfulfilledPaymentAttempts: Array<PaymentAttempt>;
  /**  Get current user's permissions based on their JWT roles */
  currentUserPermissions: Array<Scalars['String']['output']>;
  defaultBankAccount: Maybe<BankAccount>;
  /**
   * Combined discovery with all filters - PRIMARY PUBLIC QUERY
   * This is the main query for event discovery on mobile and web
   */
  discoverEvents: EventConnection;
  draftEventsCursorPagination: EventConnection;
  /**  Draft events - Organizer sees their own, admin sees all */
  draftEventsOffsetPagination: EventOffsetPage;
  /**
   * ========================================================================
   * ESCROW ACCOUNT QUERIES
   * ADMIN-ONLY - Escrow account management is platform-level
   * ========================================================================
   * ADMIN - Single escrow account lookup
   */
  escrowAccount: Maybe<EventEscrowAccount>;
  /**  ADMIN - Escrow balance and summary */
  escrowAccountBalance: Maybe<Scalars['BigDecimal']['output']>;
  escrowAccountByEvent: Maybe<EventEscrowAccount>;
  escrowAccountByNumber: Maybe<EventEscrowAccount>;
  escrowAccountsByOrganizerCursorPagination: EscrowAccountConnection;
  escrowAccountsByOrganizerOffsetPagination: EscrowAccountOffsetPage;
  /**  ADMIN - Cursor pagination for escrow lists */
  escrowAccountsCursorPagination: EscrowAccountConnection;
  /**  ADMIN - Offset pagination for escrow dashboard tables */
  escrowAccountsOffsetPagination: EscrowAccountOffsetPage;
  escrowBalance: Scalars['BigDecimal']['output'];
  escrowBalanceAsOf: Scalars['BigDecimal']['output'];
  /**  ADMIN - Get only inconsistencies (filtered view) */
  escrowJournalInconsistencies: Array<EscrowJournalVerificationResponse>;
  /**
   * ========================================================================
   * ESCROW-JOURNAL CROSS-VERIFICATION QUERIES
   * ADMIN-ONLY - Verify escrow balances match journal entries
   * ========================================================================
   * ADMIN - Verify single event's escrow-journal consistency
   */
  escrowJournalVerification: EscrowJournalVerificationResponse;
  /**  ADMIN - Verify all escrow accounts against journal entries */
  escrowJournalVerificationAll: Array<EscrowJournalVerificationResponse>;
  /**  ADMIN - Standalone Escrow Transaction queries */
  escrowTransaction: Maybe<StandaloneEscrowTransaction>;
  escrowTransactionsByAccount: EscrowTransactionOffsetPage;
  escrowTransactionsByTicket: Array<StandaloneEscrowTransaction>;
  escrowTransactionsUnlinked: Array<StandaloneEscrowTransaction>;
  /**
   * ========================================================================
   * EVENT DISCOVERY QUERIES (PUBLIC - For all consumers)
   * ========================================================================
   * These queries are used by mobile apps and web to browse/search events.
   * No tags needed - available to all clients.
   * Get single event by ID - PUBLIC
   */
  event: Maybe<Event>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Event Access Queries
   * ORGANIZER - Event-level access control for team members
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Get event access grant by ID
   */
  eventAccessGrant: Maybe<EventAccessGrant>;
  /**  ORGANIZER - Get event access grants with cursor pagination */
  eventAccessGrantsCursorPagination: EventAccessGrantConnection;
  /**  ORGANIZER - Get event access grants for an event with offset pagination */
  eventAccessGrantsOffsetPagination: EventAccessGrantOffsetPage;
  /**
   * ---------------------------------------------------------------------------
   * PUBLIC CATEGORY QUERIES - Cursor pagination for mobile infinite scroll
   * Categories are public reference data needed for event filtering/browsing
   * ---------------------------------------------------------------------------
   */
  eventCategoriesCursorPagination: EventCategoryConnection;
  /**
   * ---------------------------------------------------------------------------
   * ADMIN CATEGORY QUERIES - Offset pagination for admin dashboard tables
   * Only platform admins manage category reference data
   * ---------------------------------------------------------------------------
   */
  eventCategoriesOffsetPagination: EventCategoryOffsetPage;
  /**
   * ========================================================================
   * EVENT CATEGORY QUERIES
   * Categories are essential for event discovery - public for filtering
   * ========================================================================
   * PUBLIC - Single category lookup for event categorization display
   */
  eventCategory: Maybe<EventCategory>;
  /**
   * ========================================================================
   * EVENT COUNT QUERIES (ADMIN - Dashboard statistics)
   * ========================================================================
   * Count queries for admin dashboard statistics cards
   */
  eventCount: Scalars['Int']['output'];
  eventCountByCategory: Scalars['Int']['output'];
  eventCountByCity: Scalars['Int']['output'];
  eventCountByOrganizer: Scalars['Int']['output'];
  eventCountByStatus: Scalars['Int']['output'];
  /**
   * ========================================================================
   * EVENT LIFECYCLE QUERIES
   * Lifecycle management is for event management (organizers and admins)
   * Customers don't need to see internal lifecycle/status transition logic
   * ========================================================================
   * ORGANIZER/ADMIN - View event lifecycle state and history
   */
  eventLifecycle: Maybe<EventLifecycle>;
  /**
   * ========================================================================
   * LIVE DASHBOARD QUERIES
   * ORGANIZER - Real-time event day dashboard
   * ========================================================================
   * ORGANIZER - Live check-in dashboard for event day
   */
  eventLiveDashboard: LiveDashboard;
  /**  ORGANIZER - Promo code management */
  eventPromoCodes: Array<PromoCode>;
  /**  ORGANIZER/ADMIN - Refund summary for event */
  eventRefundSummary: Maybe<RefundSummary>;
  /**
   * ========================================================================
   * EVENT STATISTICS QUERIES (ORGANIZER/ADMIN)
   * ========================================================================
   * Per-event statistics - organizers can see their own event stats
   * Per-event ticket statistics - for organizer dashboard
   */
  eventStatistics: Maybe<EventTicketStatistics>;
  /**  Platform-wide event statistics - ADMIN ONLY */
  eventStats: EventStats;
  /**
   * ORGANIZER/ADMIN - Include hidden tiers (draft tiers not visible to customers)
   * Organizers need to see all tiers including hidden ones when managing events
   */
  eventTicketTiers: Array<TicketTier>;
  eventsByCategoryCursorPagination: EventConnection;
  eventsByCategoryOffsetPagination: EventOffsetPage;
  eventsByCityCursorPagination: EventConnection;
  eventsByCityOffsetPagination: EventOffsetPage;
  eventsByDateRangeCursorPagination: EventConnection;
  eventsByDateRangeOffsetPagination: EventOffsetPage;
  /**  Organizer's public events - available to see organizer's profile page */
  eventsByOrganizerCursorPagination: EventConnection;
  eventsByOrganizerOffsetPagination: EventOffsetPage;
  eventsByPriceRangeCursorPagination: EventConnection;
  eventsByPriceRangeOffsetPagination: EventOffsetPage;
  eventsByStatusCursorPagination: EventConnection;
  eventsByStatusOffsetPagination: EventOffsetPage;
  /**  Cursor versions for mobile admin app (if needed) */
  eventsCursorPagination: EventConnection;
  /**
   * ========================================================================
   * ADMIN EVENT MANAGEMENT QUERIES
   * ========================================================================
   * These queries are for managing events - approval workflow, drafts, etc.
   * General event list with filters - ADMIN dashboard
   */
  eventsOffsetPagination: EventOffsetPage;
  expiredReservationsCursorPagination: ReservationConnection;
  expiredReservationsOffsetPagination: ReservationOffsetPage;
  /**
   * ========================================================================
   * EXPORT QUERIES
   * Export functionality is admin-only for platform reporting
   * Organizers get their own reporting via separate organizer dashboard queries
   * ========================================================================
   * ADMIN - Export single event data for compliance/reporting
   */
  exportEventData: ReportExport;
  /**  ADMIN - Export filtered events report for platform analytics */
  exportEventsReport: ReportExport;
  exportFinancialReport: ReportExport;
  /**  ORGANIZER/ADMIN - Event sales report */
  exportSalesReport: ReportExport;
  failedPayoutRequestsCursorPagination: PayoutRequestConnection;
  failedPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  featuredEventsCursorPagination: EventConnection;
  featuredEventsOffsetPagination: EventOffsetPage;
  /**
   * ========================================================================
   * FINANCIAL REPORTING QUERIES
   * ADMIN - Platform financial reporting
   * ========================================================================
   * ADMIN - Financial reports and exports
   */
  financialReport: FinancialReport;
  freeEventsCursorPagination: EventConnection;
  freeEventsOffsetPagination: EventOffsetPage;
  hasEventPermission: Scalars['Boolean']['output'];
  /**  ORGANIZER - Check if user has permission */
  hasOrganizationPermission: Scalars['Boolean']['output'];
  /**  ORGANIZER - Check if organization has pending transfer */
  hasPendingOwnershipTransfer: Scalars['Boolean']['output'];
  hasSuccessfulPayment: Scalars['Boolean']['output'];
  /**  PUBLIC - Get invitation by token (for acceptance page - anyone with link) */
  invitationByToken: Maybe<TeamInvitation>;
  /**  ORGANIZER - Check if slug is available */
  isSlugAvailable: Scalars['Boolean']['output'];
  /**  MOBILE - Refund eligibility and calculation for customer */
  isTicketEligibleForRefund: Scalars['Boolean']['output'];
  journalEntriesByAccountCode: JournalEntryOffsetPage;
  journalEntriesByCorrelationId: Array<JournalEntry>;
  journalEntriesOffsetPagination: JournalEntryOffsetPage;
  /**  ADMIN - Journal Entry queries */
  journalEntry: Maybe<JournalEntry>;
  journalEntryByNumber: Maybe<JournalEntry>;
  latestPaymentAttemptByTicket: Maybe<PaymentAttempt>;
  /**
   * ========================================================================
   * LOCATION QUERIES
   * ========================================================================
   */
  location: Maybe<Location>;
  locationsByCityCursorPagination: LocationConnection;
  locationsByCountryCursorPagination: LocationConnection;
  /**  Cursor pagination (mobile/infinite scroll) */
  locationsCursorPagination: LocationConnection;
  /**  PUBLIC - Mobile nearby locations with cursor pagination */
  locationsNearbyCursorPagination: LocationConnection;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * User Queries
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Get current authenticated user's profile
   */
  me: Maybe<User>;
  myActiveReservations: Array<TicketReservation>;
  /**  ORGANIZER - Count my approved documents */
  myApprovedDocumentCount: Scalars['Long']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Device Queries
   * MOBILE - Device management for push notifications
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Get my registered devices
   */
  myDevices: Array<UserDevice>;
  /**  ORGANIZER - Get my effective permissions */
  myEffectivePermissions: EffectivePermissions;
  /**  adminId extracted from JWT - OWASP A01:2021 compliance */
  myEscalationsCursorPagination: ApprovalEscalationConnection;
  /**  ADMIN - Get escalations assigned to current admin (adminId from JWT - OWASP A01:2021) */
  myEscalationsOffsetPagination: ApprovalEscalationOffsetPage;
  /**  ORGANIZER - Get my access to a specific event */
  myEventAccess: Maybe<EventAccessGrant>;
  /**  ORGANIZER - Get all my event access grants */
  myEventAccessGrants: Array<EventAccessGrant>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Event Reminder Queries
   * MOBILE - Event reminder management
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Get my event reminders
   */
  myEventReminders: Array<EventReminder>;
  /**  ORGANIZER - Get my role for event */
  myEventRole: Maybe<EventRole>;
  /**  MOBILE - Get my notification preferences */
  myNotificationPreferences: Maybe<NotificationPreferences>;
  /**  MOBILE - Get my notifications with cursor pagination (mobile/infinite scroll) */
  myNotificationsCursorPagination: NotificationConnection;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Notification Queries
   * MOBILE - User notification management
   * ─────────────────────────────────────────────────────────────────────
   * MOBILE - Get my notifications with offset pagination
   */
  myNotificationsOffsetPagination: NotificationOffsetPage;
  /**  ORGANIZER - Get my membership in an organization */
  myOrganizationMembership: Maybe<OrganizationMember>;
  /**  ORGANIZER - Get my role in organization */
  myOrganizationRole: Maybe<OrganizationRole>;
  /**  ORGANIZER - Get organizations I belong to (for dashboard switcher) */
  myOrganizations: Array<Organization>;
  /**  ORGANIZER - Get organization I own */
  myOwnedOrganization: Maybe<Organization>;
  /**  ORGANIZER - Get my pending invitations (invites sent to me) */
  myPendingInvitations: Array<TeamInvitation>;
  /**  ORGANIZER - Get my pending ownership transfer requests (where I'm the new owner) */
  myPendingOwnershipTransfers: Array<OwnershipTransferRequest>;
  /**  ORGANIZER - Get document by type for organizer */
  myVerificationDocumentByType: Maybe<VerificationDocument>;
  /**  ORGANIZER - Count my documents */
  myVerificationDocumentCount: Scalars['Long']['output'];
  /**  ORGANIZER - Get my verification documents */
  myVerificationDocuments: Array<VerificationDocument>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization Queries
   * ORGANIZER - Manage own organization, ADMIN - Manage all organizations
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER/ADMIN - Get organization by ID
   */
  organization: Maybe<Organization>;
  /**  ADMIN - Get organization applications with cursor pagination */
  organizationApplicationsCursorPagination: OrganizationApplicationConnection;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * ─────────────────────────────────────────────────────────────────────
   * Organization Queries (Approval Queue)
   * ADMIN - Organization approval queue and management
   * ─────────────────────────────────────────────────────────────────────
   * ADMIN - Get organization applications with offset pagination for admin queue
   */
  organizationApplicationsOffsetPagination: OrganizationApplicationOffsetPage;
  /**  ORGANIZER - Get organization by owner ID */
  organizationByOwnerId: Maybe<Organization>;
  /**  ORGANIZER/ADMIN - Get organization by slug */
  organizationBySlug: Maybe<Organization>;
  /**  ADMIN - Count organizations by status */
  organizationCount: Scalars['Long']['output'];
  /**  ORGANIZER - Get specific member */
  organizationMember: Maybe<OrganizationMember>;
  /**  ORGANIZER - Get organization members with cursor pagination */
  organizationMembersCursorPagination: OrganizationMemberConnection;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Team Member Queries
   * ORGANIZER - Team management for organization owners/admins
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Get organization members with offset pagination
   */
  organizationMembersOffsetPagination: OrganizationMemberOffsetPage;
  /**  ADMIN - Search organizations with cursor pagination */
  organizationsCursorPagination: OrganizationConnection;
  /**  ADMIN - Search organizations with offset pagination for admin tables */
  organizationsOffsetPagination: OrganizationOffsetPage;
  organizerPromoCodes: Array<PromoCode>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Statistics Queries
   * ORGANIZER - Own stats, ADMIN - Platform stats
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER/ADMIN - Get organizer statistics
   */
  organizerStatistics: Maybe<OrganizerStatistics>;
  overdueApprovalEventsCursorPagination: EventConnection;
  overdueApprovalEventsOffsetPagination: EventOffsetPage;
  overdueApprovalTimelinesCursorPagination: ApprovalTimelineConnection;
  overdueApprovalTimelinesOffsetPagination: ApprovalTimelineOffsetPage;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Ownership Transfer Queries
   * ORGANIZER - Organization ownership transfer
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Get ownership transfer by ID
   */
  ownershipTransfer: Maybe<OwnershipTransferRequest>;
  /**  ORGANIZER - Get ownership transfer by token */
  ownershipTransferByToken: Maybe<OwnershipTransferRequest>;
  /**  ORGANIZER - Get all transfers for an organization */
  ownershipTransfers: Array<OwnershipTransferRequest>;
  /**
   * ========================================================================
   * FINANCIAL TRANSACTION QUERIES - REMOVED
   * Replaced by:
   * - PaymentAttempt queries for payment lifecycle tracking
   * - JournalEntry queries for accounting records
   * ========================================================================
   * ========================================================================
   * PAYMENT ATTEMPT QUERIES
   * ADMIN-ONLY - Payment attempt lifecycle tracking
   * Tracks the operational journey of payments through PawaPay
   * ========================================================================
   * ADMIN - Single payment attempt lookup
   */
  paymentAttempt: Maybe<PaymentAttempt>;
  paymentAttemptByAttemptNumber: Maybe<PaymentAttempt>;
  paymentAttemptByDepositId: Maybe<PaymentAttempt>;
  /**  ADMIN - Statistics */
  paymentAttemptCountByStatus: Scalars['Int']['output'];
  paymentAttemptsByBuyer: Array<PaymentAttempt>;
  /**  ADMIN - Payment attempts by event/buyer */
  paymentAttemptsByEvent: Array<PaymentAttempt>;
  /**  ADMIN - Payment attempts by status (for monitoring/recovery) */
  paymentAttemptsByStatus: Array<PaymentAttempt>;
  /**  ADMIN - Payment attempts by ticket (all attempts for a purchase) */
  paymentAttemptsByTicket: Array<PaymentAttempt>;
  payoutRecoverySummary: PayoutRecoverySummary;
  /**
   * ========================================================================
   * PAYOUT REQUEST QUERIES
   * ORGANIZER - Track own payout requests
   * ADMIN - Manage all payout requests
   * ========================================================================
   * ORGANIZER/ADMIN - Single payout lookup
   */
  payoutRequest: Maybe<PayoutRequest>;
  payoutRequestByRequestId: Maybe<PayoutRequest>;
  /**  ADMIN - Payout request statistics */
  payoutRequestStats: PayoutRequestStats;
  payoutRequestsByEventCursorPagination: PayoutRequestConnection;
  payoutRequestsByEventOffsetPagination: PayoutRequestOffsetPage;
  payoutRequestsByIssueTypeOffsetPagination: PayoutRequestOffsetPage;
  /**  ORGANIZER - Own payout request history */
  payoutRequestsByOrganizerCursorPagination: PayoutRequestConnection;
  /**  ORGANIZER/ADMIN - Payouts by organizer */
  payoutRequestsByOrganizerOffsetPagination: PayoutRequestOffsetPage;
  /**  ADMIN - Cursor pagination for payout lists */
  payoutRequestsCursorPagination: PayoutRequestConnection;
  payoutRequestsForReviewCursorPagination: PayoutRequestConnection;
  /**  ADMIN - Payout Recovery Queries */
  payoutRequestsForReviewOffsetPagination: PayoutRequestOffsetPage;
  /**  ADMIN - Offset pagination for all payouts */
  payoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  pendingApprovalEventsCursorPagination: EventConnection;
  /**  Approval workflow queries - ADMIN ONLY */
  pendingApprovalEventsOffsetPagination: EventOffsetPage;
  pendingApprovalTimelinesCursorPagination: ApprovalTimelineConnection;
  pendingApprovalTimelinesOffsetPagination: ApprovalTimelineOffsetPage;
  pendingChargebacks: Array<ChargebackRecord>;
  /**  ORGANIZER - Get pending invitations with cursor pagination */
  pendingInvitationsCursorPagination: TeamInvitationConnection;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Team Invitation Queries
   * ORGANIZER - Team invitation management
   * ─────────────────────────────────────────────────────────────────────
   * ORGANIZER - Get pending invitations for organization with offset pagination
   */
  pendingInvitationsOffsetPagination: TeamInvitationOffsetPage;
  pendingJournalEntriesOffsetPagination: JournalEntryOffsetPage;
  /**  ORGANIZER - Get pending ownership transfer for organization */
  pendingOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  pendingPaymentAttempts: Array<PaymentAttempt>;
  pendingPayoutRequestsCursorPagination: PayoutRequestConnection;
  /**  ADMIN - Pending and failed payouts queue */
  pendingPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  pendingRefundRequestsCursorPagination: RefundRequestConnection;
  pendingRefundRequestsOffsetPagination: RefundRequestOffsetPage;
  /**  ADMIN - Get pending documents queue */
  pendingVerificationDocuments: Array<VerificationDocument>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Permission Queries
   * ORGANIZER - Permission checks for UI rendering
   * ─────────────────────────────────────────────────────────────────────
   * ADMIN - Get permission by ID
   */
  permission: Maybe<Permission>;
  /**  ADMIN - Get permission by name */
  permissionByName: Maybe<Permission>;
  /**  ADMIN - Get all active permissions */
  permissions: Array<Permission>;
  /**  ADMIN - Get permissions by category */
  permissionsByCategory: Array<Permission>;
  platformAccount: Maybe<PlatformAccount>;
  platformAccountByType: Maybe<PlatformAccount>;
  /**  ADMIN - Platform Account queries */
  platformAccounts: Array<PlatformAccount>;
  /**
   * ========================================================================
   * PLATFORM CONFIGURATION QUERIES (Admin Only)
   * Platform settings for SLA, escalation, and notification configuration
   * ========================================================================
   * ADMIN - Get current platform configuration
   */
  platformConfiguration: PlatformConfiguration;
  /**  ADMIN - Get platform statistics (admin dashboard) */
  platformStatistics: Maybe<PlatformStatistics>;
  platformSummary: PlatformSummary;
  /**  PUBLIC - Popular categories for mobile home screen and event discovery */
  popularCategories: Array<EventCategory>;
  /**
   * ========================================================================
   * PROMO CODE QUERIES
   * ORGANIZER - Promo code management
   * MOBILE - Promo code validation during checkout
   * ========================================================================
   * ORGANIZER/ADMIN - Promo code lookups
   */
  promoCode: Maybe<PromoCode>;
  promoCodeByCode: Maybe<PromoCode>;
  /**
   * ========================================================================
   * PROVINCE QUERIES
   * Provinces are reference data used for regional filtering - public for discovery
   * ========================================================================
   * PUBLIC - Single province lookup for regional display
   */
  province: Maybe<Province>;
  provincesByCountryCursorPagination: ProvinceConnection;
  provincesByCountryOffsetPagination: ProvinceOffsetPage;
  /**
   * ---------------------------------------------------------------------------
   * PUBLIC PROVINCE QUERIES - Cursor pagination for mobile infinite scroll
   * Provinces are public reference data needed for regional filtering
   * ---------------------------------------------------------------------------
   */
  provincesCursorPagination: ProvinceConnection;
  /**
   * ---------------------------------------------------------------------------
   * ADMIN PROVINCE QUERIES - Offset pagination for admin dashboard tables
   * Only platform admins manage province reference data
   * ---------------------------------------------------------------------------
   */
  provincesOffsetPagination: ProvinceOffsetPage;
  /**
   * Cursor pagination queries (mobile/infinite scroll) - PUBLIC
   * Used by mobile apps for infinite scroll event lists
   */
  publishedEventsCursorPagination: EventConnection;
  /**
   * ========================================================================
   * OFFSET PAGINATION QUERIES (ADMIN/ORGANIZER - Dashboard tables)
   * ========================================================================
   * Offset pagination is used for admin dashboard tables with page numbers.
   * These are not needed by mobile consumers.
   */
  publishedEventsOffsetPagination: EventOffsetPage;
  recentlyResolvedPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  /**
   * ========================================================================
   * RECONCILIATION QUERIES
   * ADMIN-ONLY - Reconciliation engine
   * ========================================================================
   * ADMIN - Reconciliation queries
   */
  reconciliationRun: Maybe<ReconciliationRun>;
  reconciliationRunsByType: ReconciliationRunOffsetPage;
  reconciliationRunsOffsetPagination: ReconciliationRunOffsetPage;
  reconciliationRunsRequiringReview: Array<ReconciliationRun>;
  reconciliationSummary: ReconciliationSummary;
  /**
   * ========================================================================
   * REFUND REQUEST QUERIES
   * ORGANIZER - See refunds for own events
   * ADMIN - See all refunds platform-wide
   * ========================================================================
   * ORGANIZER/ADMIN - Single refund lookup
   */
  refundRequest: Maybe<RefundRequest>;
  refundRequestByRequestId: Maybe<RefundRequest>;
  refundRequestsByBuyerCursorPagination: RefundRequestConnection;
  refundRequestsByBuyerOffsetPagination: RefundRequestOffsetPage;
  refundRequestsByEventCursorPagination: RefundRequestConnection;
  /**  ORGANIZER/ADMIN - Refunds by event */
  refundRequestsByEventOffsetPagination: RefundRequestOffsetPage;
  refundRequestsByTicket: Array<RefundRequest>;
  /**  ADMIN - Cursor pagination for refund lists */
  refundRequestsCursorPagination: RefundRequestConnection;
  /**  ADMIN - Offset pagination for refund dashboard tables */
  refundRequestsOffsetPagination: RefundRequestOffsetPage;
  /**
   * ========================================================================
   * RESERVATION QUERIES
   * MOBILE - Customer checkout flow
   * ========================================================================
   * MOBILE - Reservation lookup during checkout
   */
  reservation: Maybe<TicketReservation>;
  /**  ADMIN - Cursor pagination for reservation lists */
  reservationsByEventCursorPagination: ReservationConnection;
  /**  ADMIN - Reservation monitoring for event management */
  reservationsByEventOffsetPagination: ReservationOffsetPage;
  retryablePayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  /**  ADMIN - Get permissions assigned to a role */
  rolePermissions: Array<Permission>;
  searchCitiesCursorPagination: CityConnection;
  searchCitiesOffsetPagination: CityOffsetPage;
  searchEventCategoriesCursorPagination: EventCategoryConnection;
  searchEventCategoriesOffsetPagination: EventCategoryOffsetPage;
  searchEventsCursorPagination: EventConnection;
  searchEventsOffsetPagination: EventOffsetPage;
  /**  PUBLIC - Mobile search with cursor pagination for infinite scroll */
  searchLocationsCursorPagination: LocationConnection;
  searchProvincesCursorPagination: ProvinceConnection;
  searchProvincesOffsetPagination: ProvinceOffsetPage;
  searchTicketsCursorPagination: TicketConnection;
  searchTicketsOffsetPagination: TicketOffsetPage;
  stuckPayoutRequestsCursorPagination: PayoutRequestConnection;
  stuckPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  successfulPaymentAttemptByTicket: Maybe<PaymentAttempt>;
  /**  ORGANIZER - Get team statistics for organization */
  teamStatistics: Maybe<TeamStatistics>;
  /**
   * ========================================================================
   * TICKET QUERIES
   * PUBLIC - Single ticket lookup (owner can view their own ticket)
   * ORGANIZER/ADMIN - Paginated lists for management
   * ========================================================================
   * PUBLIC - Ticket lookup for ticket holders
   */
  ticket: Maybe<Ticket>;
  ticketByNumber: Maybe<Ticket>;
  ticketCountByBuyer: Scalars['Int']['output'];
  /**  ORGANIZER/ADMIN - Ticket counts for dashboard stats */
  ticketCountByEvent: Scalars['Int']['output'];
  /**
   * ========================================================================
   * STATISTICS QUERIES
   * ADMIN - Platform-wide statistics
   * ========================================================================
   * ADMIN - Ticket statistics (platform-wide)
   */
  ticketStats: TicketStats;
  /**
   * ========================================================================
   * TICKET TIER QUERIES
   * Ticket tiers are essential for the ticket purchase flow
   * PUBLIC - Needed by mobile app for displaying ticket options
   * ========================================================================
   * PUBLIC - Single ticket tier lookup for purchase flow
   */
  ticketTier: Maybe<TicketTier>;
  ticketTierStatistics: Maybe<TicketTierStats>;
  /**  MOBILE - Customer views their own tickets */
  ticketsByBuyerCursorPagination: TicketConnection;
  ticketsByBuyerOffsetPagination: TicketOffsetPage;
  /**  ORGANIZER/ADMIN - Cursor pagination for dashboard lists */
  ticketsByEventCursorPagination: TicketConnection;
  /**  ORGANIZER/ADMIN - Offset pagination for dashboard tables */
  ticketsByEventOffsetPagination: TicketOffsetPage;
  ticketsByOrganizerCursorPagination: TicketConnection;
  ticketsByOrganizerOffsetPagination: TicketOffsetPage;
  /**  ADMIN - Transaction statistics */
  transactionStats: TransactionStats;
  /**  ADMIN - Trial Balance and Account Balance queries */
  trialBalance: Array<AccountBalance>;
  /**  MOBILE - Get unread notification count (for badge) */
  unreadNotificationCount: Scalars['Int']['output'];
  upcomingEventsCursorPagination: EventConnection;
  upcomingEventsOffsetPagination: EventOffsetPage;
  /**  ADMIN - Get user by ID for user management */
  user: Maybe<User>;
  /**  ADMIN - User lookup by email */
  userByEmail: Maybe<User>;
  /**  ADMIN - User lookup by phone number */
  userByPhone: Maybe<User>;
  /**  ORGANIZER - Get user's access to a specific event */
  userEventAccess: Maybe<EventAccessGrant>;
  /**  ADMIN - User statistics for admin dashboard */
  userStats: Maybe<UserStats>;
  /**
   * Find all users who have a specific role.
   * Example: usersByRole(role: ORGANIZER) returns all users with ORGANIZER role.
   */
  usersByRole: UserOffsetPage;
  /** Count users who have a specific role. */
  usersCountByRole: Scalars['Long']['output'];
  /**  ADMIN - Search users with cursor pagination */
  usersCursorPagination: UserConnection;
  /**  ADMIN - Search users with offset pagination for admin tables */
  usersOffsetPagination: UserOffsetPage;
  /**  MOBILE - Validate promo code during checkout */
  validatePromoCode: PromoCodeValidation;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Verification Document Queries
   * ORGANIZER - Own documents, ADMIN - All documents
   * ─────────────────────────────────────────────────────────────────────
   * Get verification document by ID
   */
  verificationDocument: Maybe<VerificationDocument>;
  /**  ADMIN - Get documents for organization */
  verificationDocuments: Array<VerificationDocument>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryAccountBalanceArgs = {
  accountCode: Scalars['String']['input'];
  asOf: InputMaybe<Scalars['DateTime']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryAccountSummaryArgs = {
  accountId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryActiveEscalationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryActiveEscalationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryActiveEventCategoriesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryActiveEventCategoriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryAllPermissionsArgs = {
  scope: InputMaybe<PermissionScope>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryAllowedStatusTransitionsArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovalEscalationArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovalTimelineArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovalTimelinesByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovalTimelinesByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovalTimelinesCursorPaginationArgs = {
  filter: InputMaybe<ApprovalTimelineFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovalTimelinesOffsetPaginationArgs = {
  filter: InputMaybe<ApprovalTimelineFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovedNotPublishedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryApprovedNotPublishedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryAvailableTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryBankAccountArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryBankAccountsByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCalculateRefundAmountArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCancelledEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCancelledEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChargebackArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChargebackByChargebackIdArgs = {
  chargebackId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChargebackStatsArgs = {
  eventId: InputMaybe<Scalars['String']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChargebacksByEventArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChargebacksByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChargebacksOffsetPaginationArgs = {
  filter: InputMaybe<ChargebackFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChartOfAccountsByCodeArgs = {
  accountCode: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChartOfAccountsByTypeArgs = {
  accountType: AccountType;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChartOfAccountsEntryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryChartOfAccountsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCitiesByCountryCursorPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCitiesByCountryOffsetPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCitiesByProvinceCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  provinceId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCitiesByProvinceOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  provinceId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCitiesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCitiesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCityArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCompletedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryCompletedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryDefaultBankAccountArgs = {
  organizerId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryDiscoverEventsArgs = {
  filter: EventDiscoveryFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryDraftEventsCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryDraftEventsOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountBalanceArgs = {
  accountId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountByEventArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountByNumberArgs = {
  accountNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountsByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountsByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountsCursorPaginationArgs = {
  filter: InputMaybe<EscrowAccountFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowAccountsOffsetPaginationArgs = {
  filter: InputMaybe<EscrowAccountFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowBalanceArgs = {
  escrowAccountId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowBalanceAsOfArgs = {
  asOf: Scalars['DateTime']['input'];
  escrowAccountId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowJournalVerificationArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowTransactionArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowTransactionsByAccountArgs = {
  escrowAccountId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEscrowTransactionsByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventAccessGrantArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventAccessGrantsCursorPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<AccessGrantStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventAccessGrantsOffsetPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<AccessGrantStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCategoriesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCategoriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCountByCategoryArgs = {
  categoryId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCountByCityArgs = {
  city: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCountByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventCountByStatusArgs = {
  status: EventStatus;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventLifecycleArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventLiveDashboardArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventPromoCodesArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventRefundSummaryArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventStatisticsArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
  includeHidden?: InputMaybe<Scalars['Boolean']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByCategoryCursorPaginationArgs = {
  categoryId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByCategoryOffsetPaginationArgs = {
  categoryId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByCityCursorPaginationArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByCityOffsetPaginationArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByDateRangeCursorPaginationArgs = {
  endDate: Scalars['DateTime']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  startDate: Scalars['DateTime']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByDateRangeOffsetPaginationArgs = {
  endDate: Scalars['DateTime']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  startDate: Scalars['DateTime']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByPriceRangeCursorPaginationArgs = {
  maxPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  minPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByPriceRangeOffsetPaginationArgs = {
  maxPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  minPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByStatusCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: EventStatus;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsByStatusOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: EventStatus;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsCursorPaginationArgs = {
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryEventsOffsetPaginationArgs = {
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryExpiredReservationsCursorPaginationArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<CursorPaginationInput>;
  since: Scalars['DateTime']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryExpiredReservationsOffsetPaginationArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
  since: Scalars['DateTime']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryExportEventDataArgs = {
  eventId: Scalars['ID']['input'];
  format: ExportFormat;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryExportEventsReportArgs = {
  filter: EventFilterInput;
  format: ExportFormat;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryExportFinancialReportArgs = {
  filter: FinancialReportFilterInput;
  format: ExportFormat;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryExportSalesReportArgs = {
  eventId: Scalars['ID']['input'];
  format: ExportFormat;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFailedPayoutRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFailedPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFeaturedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFeaturedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFinancialReportArgs = {
  filter: FinancialReportFilterInput;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFreeEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryFreeEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryHasEventPermissionArgs = {
  eventId: Scalars['ID']['input'];
  permission: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryHasOrganizationPermissionArgs = {
  organizationId: Scalars['ID']['input'];
  permission: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryHasPendingOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryHasSuccessfulPaymentArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryInvitationByTokenArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryIsSlugAvailableArgs = {
  slug: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryIsTicketEligibleForRefundArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryJournalEntriesByAccountCodeArgs = {
  accountCode: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryJournalEntriesByCorrelationIdArgs = {
  correlationId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryJournalEntriesOffsetPaginationArgs = {
  filter: InputMaybe<JournalEntryFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryJournalEntryArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryJournalEntryByNumberArgs = {
  entryNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryLatestPaymentAttemptByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryLocationArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryLocationsByCityCursorPaginationArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryLocationsByCountryCursorPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryLocationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryLocationsNearbyCursorPaginationArgs = {
  input: NearbyLocationInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyActiveReservationsArgs = {
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyEffectivePermissionsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyEscalationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyEscalationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyEventAccessArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyEventRemindersArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyEventRoleArgs = {
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyNotificationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<NotificationStatus>;
  type: InputMaybe<NotificationType>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyNotificationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<NotificationStatus>;
  type: InputMaybe<NotificationType>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyOrganizationMembershipArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyOrganizationRoleArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyVerificationDocumentByTypeArgs = {
  documentType: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryMyVerificationDocumentsArgs = {
  status: InputMaybe<DocumentStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationApplicationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<OrganizationStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationApplicationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<OrganizationStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationByOwnerIdArgs = {
  ownerId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationBySlugArgs = {
  slug: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationCountArgs = {
  status: InputMaybe<OrganizationStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationMemberArgs = {
  organizationId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationMembersCursorPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  role: InputMaybe<OrganizationRole>;
  status: InputMaybe<MemberStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationMembersOffsetPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<OrganizationRole>;
  status: InputMaybe<MemberStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  search: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<OrganizationStatus>;
  verified: InputMaybe<Scalars['Boolean']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  search: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<OrganizationStatus>;
  verified: InputMaybe<Scalars['Boolean']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizerPromoCodesArgs = {
  organizerId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOrganizerStatisticsArgs = {
  organizerId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOverdueApprovalEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOverdueApprovalEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOverdueApprovalTimelinesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOverdueApprovalTimelinesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOwnershipTransferArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOwnershipTransferByTokenArgs = {
  token: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryOwnershipTransfersArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptByAttemptNumberArgs = {
  attemptNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptByDepositIdArgs = {
  depositId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptCountByStatusArgs = {
  status: PaymentAttemptStatus;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptsByBuyerArgs = {
  buyerId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptsByEventArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptsByStatusArgs = {
  status: PaymentAttemptStatus;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPaymentAttemptsByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestByRequestIdArgs = {
  requestId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestStatsArgs = {
  organizerId: InputMaybe<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsByEventCursorPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsByEventOffsetPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsByIssueTypeOffsetPaginationArgs = {
  issueType: PayoutIssueType;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsCursorPaginationArgs = {
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsForReviewCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  reviewStatus: InputMaybe<PayoutReviewStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsForReviewOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  reviewStatus: InputMaybe<PayoutReviewStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPayoutRequestsOffsetPaginationArgs = {
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingApprovalEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingApprovalEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingApprovalTimelinesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingApprovalTimelinesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingInvitationsCursorPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingInvitationsOffsetPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingJournalEntriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingPayoutRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingRefundRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPendingRefundRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPermissionArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPermissionByNameArgs = {
  name: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPermissionsByCategoryArgs = {
  category: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPlatformAccountArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPlatformAccountByTypeArgs = {
  accountType: PlatformAccountType;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPopularCategoriesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPromoCodeArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPromoCodeByCodeArgs = {
  code: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryProvinceArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryProvincesByCountryCursorPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryProvincesByCountryOffsetPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryProvincesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryProvincesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPublishedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryPublishedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRecentlyResolvedPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReconciliationRunArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReconciliationRunsByTypeArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  type: ReconciliationType;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReconciliationRunsOffsetPaginationArgs = {
  filter: InputMaybe<ReconciliationFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReconciliationSummaryArgs = {
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  type: InputMaybe<ReconciliationType>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestByRequestIdArgs = {
  requestId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsByBuyerCursorPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsByBuyerOffsetPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsByEventCursorPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsByEventOffsetPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsCursorPaginationArgs = {
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRefundRequestsOffsetPaginationArgs = {
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReservationArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReservationsByEventCursorPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryReservationsByEventOffsetPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRetryablePayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryRolePermissionsArgs = {
  roleId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchCitiesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchCitiesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchEventCategoriesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchEventCategoriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchLocationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchProvincesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchProvincesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchTicketsCursorPaginationArgs = {
  filter: TicketFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySearchTicketsOffsetPaginationArgs = {
  filter: TicketFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryStuckPayoutRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryStuckPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QuerySuccessfulPaymentAttemptByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTeamStatisticsArgs = {
  organizationId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketByNumberArgs = {
  ticketNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketCountByBuyerArgs = {
  buyerId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketCountByEventArgs = {
  eventId: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketStatsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketTierArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketTierStatisticsArgs = {
  eventId: Scalars['ID']['input'];
  tierId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketsByBuyerCursorPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<TicketStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketsByBuyerOffsetPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<TicketStatus>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketsByEventCursorPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketsByEventOffsetPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketsByOrganizerCursorPaginationArgs = {
  filter: InputMaybe<TicketFilterInput>;
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTicketsByOrganizerOffsetPaginationArgs = {
  filter: InputMaybe<TicketFilterInput>;
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTransactionStatsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizerId: InputMaybe<Scalars['ID']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryTrialBalanceArgs = {
  asOf: InputMaybe<Scalars['DateTime']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUpcomingEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUpcomingEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUserArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUserByEmailArgs = {
  email: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUserByPhoneArgs = {
  phoneNumber: Scalars['String']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUserEventAccessArgs = {
  eventId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUsersByRoleArgs = {
  activeOnly?: InputMaybe<Scalars['Boolean']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
  role: UserType;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUsersCountByRoleArgs = {
  role: UserType;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUsersCursorPaginationArgs = {
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<CursorPaginationInput>;
  role: InputMaybe<UserType>;
  search: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryUsersOffsetPaginationArgs = {
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<UserType>;
  search: InputMaybe<Scalars['String']['input']>;
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryValidatePromoCodeArgs = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  code: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryVerificationDocumentArgs = {
  id: Scalars['ID']['input'];
};


/**
 * ============================================================================
 * SECTION 15: ROOT QUERY TYPE
 * Tags indicate which clients should include these queries in their operations
 * ============================================================================
 */
export type QueryVerificationDocumentsArgs = {
  organizationId: Scalars['ID']['input'];
  status: InputMaybe<DocumentStatus>;
};

export type ReceiveChargebackInput = {
  chargebackAmount: Scalars['BigDecimal']['input'];
  chargebackFee: Scalars['BigDecimal']['input'];
  chargebackId: Scalars['String']['input'];
  currency: Scalars['String']['input'];
  /**  Multi-tenant tracking */
  customerId: Scalars['String']['input'];
  eventId: Scalars['String']['input'];
  organizationId: InputMaybe<Scalars['String']['input']>;
  organizerId: Scalars['String']['input'];
  originalAmount: Scalars['BigDecimal']['input'];
  originalTransactionId: Scalars['String']['input'];
  reason: ChargebackReason;
  responseDeadline: Scalars['DateTime']['input'];
  ticketId: Scalars['String']['input'];
};

export type ReconciliationFilterInput = {
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  status: InputMaybe<ReconciliationStatus>;
  type: InputMaybe<ReconciliationType>;
};

/**  Reconciliation Item (embedded in ReconciliationRun) */
export type ReconciliationItem = {
  __typename: 'ReconciliationItem';
  externalAmount: Maybe<Scalars['BigDecimal']['output']>;
  externalId: Maybe<Scalars['String']['output']>;
  internalAmount: Maybe<Scalars['BigDecimal']['output']>;
  internalId: Maybe<Scalars['String']['output']>;
  resolution: Maybe<Scalars['String']['output']>;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  resolvedBy: Maybe<Scalars['String']['output']>;
  status: ReconciliationItemStatus;
};

/**  Reconciliation item matching status */
export type ReconciliationItemStatus =
  | 'AMOUNT_MISMATCH'
  | 'MATCHED'
  | 'UNMATCHED_EXTERNAL'
  | 'UNMATCHED_INTERNAL';

/**  Reconciliation mutation response */
export type ReconciliationMutationResponse = {
  __typename: 'ReconciliationMutationResponse';
  data: Maybe<ReconciliationRun>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Reconciliation Run */
export type ReconciliationRun = {
  __typename: 'ReconciliationRun';
  actualTotal: Maybe<Scalars['BigDecimal']['output']>;
  completedAt: Maybe<Scalars['DateTime']['output']>;
  dataSource: Maybe<Scalars['String']['output']>;
  expectedTotal: Maybe<Scalars['BigDecimal']['output']>;
  id: Scalars['ID']['output'];
  items: Array<ReconciliationItem>;
  matchedCount: Scalars['Int']['output'];
  notes: Maybe<Scalars['String']['output']>;
  reconciliationDate: Scalars['DateTime']['output'];
  runBy: Maybe<Scalars['String']['output']>;
  startedAt: Scalars['DateTime']['output'];
  status: ReconciliationStatus;
  type: ReconciliationType;
  unmatchedCount: Scalars['Int']['output'];
  variance: Maybe<Scalars['BigDecimal']['output']>;
};

export type ReconciliationRunOffsetPage = {
  __typename: 'ReconciliationRunOffsetPage';
  data: Array<ReconciliationRun>;
  pagination: PaginationInfo;
};

/**  Reconciliation run status */
export type ReconciliationStatus =
  | 'COMPLETED'
  | 'FAILED'
  | 'REQUIRES_REVIEW'
  | 'RUNNING';

/**  Reconciliation Summary */
export type ReconciliationSummary = {
  __typename: 'ReconciliationSummary';
  completedRuns: Scalars['Int']['output'];
  failedRuns: Scalars['Int']['output'];
  lastCompletedDate: Maybe<Scalars['DateTime']['output']>;
  oldestPendingDate: Maybe<Scalars['DateTime']['output']>;
  pendingReviewRuns: Scalars['Int']['output'];
  resolvedVariance: Scalars['BigDecimal']['output'];
  totalRuns: Scalars['Int']['output'];
  totalVariance: Scalars['BigDecimal']['output'];
  unresolvedVariance: Scalars['BigDecimal']['output'];
};

/**  Reconciliation type */
export type ReconciliationType =
  | 'BANK'
  | 'ESCROW'
  | 'ESCROW_JOURNAL'
  | 'GATEWAY';

/**
 * Input for recording a gateway settlement in the accounting system.
 *
 * Called when payment gateway settles funds to our bank account.
 * Creates journal entry:
 *   DR Bank Account (1011)        - Net amount received
 *   DR Gateway Fees Expense (5010) - Fees deducted by gateway
 *   CR Gateway Receivable (1021)  - Gross amount cleared
 */
export type RecordGatewaySettlementInput = {
  /** Bank transaction reference */
  bankReference: Scalars['String']['input'];
  /** Currency code (e.g., ZMW) */
  currency: Scalars['String']['input'];
  /** Fees deducted by gateway */
  feeAmount: Scalars['BigDecimal']['input'];
  /** Gross amount before fees */
  grossAmount: Scalars['BigDecimal']['input'];
  /** Net amount received in bank */
  netAmount: Scalars['BigDecimal']['input'];
  /** Date of settlement */
  settlementDate: Scalars['DateTime']['input'];
  /** Unique settlement ID from gateway (e.g., PAW-SETTLE-20260420) */
  settlementId: Scalars['String']['input'];
};

export type RecoverChargebackInput = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  fundSource: ChargebackFundSource;
  notes: InputMaybe<Scalars['String']['input']>;
};

/**  Chargeback recovery status */
export type RecoveryStatus =
  | 'IN_PROGRESS'
  | 'NOT_STARTED'
  | 'RECOVERED'
  | 'WRITTEN_OFF';

/**  MOBILE - Refund calculation preview for customer */
export type RefundCalculation = {
  __typename: 'RefundCalculation';
  commissionRefund: Scalars['BigDecimal']['output'];
  daysBeforeEvent: Scalars['Int']['output'];
  eventDate: Scalars['DateTime']['output'];
  eventId: Scalars['ID']['output'];
  ineligibleReason: Maybe<Scalars['String']['output']>;
  isEligible: Scalars['Boolean']['output'];
  originalAmount: Scalars['BigDecimal']['output'];
  platformRetains: Scalars['BigDecimal']['output'];
  policyApplied: Scalars['String']['output'];
  refundAmount: Scalars['BigDecimal']['output'];
  refundPercentage: Scalars['Float']['output'];
  ticketId: Scalars['ID']['output'];
  ticketNumber: Scalars['String']['output'];
};

/**  ORGANIZER/ADMIN - Refund details embedded in ticket */
export type RefundInfo = {
  __typename: 'RefundInfo';
  processedBy: Maybe<Scalars['String']['output']>;
  reason: Maybe<Scalars['String']['output']>;
  refundAmount: Maybe<Scalars['BigDecimal']['output']>;
  refundDate: Maybe<Scalars['DateTime']['output']>;
  refundId: Maybe<Scalars['String']['output']>;
  status: Maybe<Scalars['String']['output']>;
  transactionId: Maybe<Scalars['String']['output']>;
};

/**
 * ------------------------------
 * REFUND REQUEST
 * ORGANIZER/ADMIN - Refund request workflow
 * Organizers see refunds for their events, admins process all refunds
 * Mobile users can see their own refund status via ticket.refundInfo
 * ------------------------------
 */
export type RefundRequest = {
  __typename: 'RefundRequest';
  additionalNotes: Maybe<Scalars['String']['output']>;
  /**  Multi-tenant tracking */
  buyerId: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  daysBeforeEvent: Maybe<Scalars['Int']['output']>;
  eventId: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  netRefundAmount: Maybe<Scalars['BigDecimal']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  originalPaymentMethod: Maybe<Scalars['String']['output']>;
  originalTicketPrice: Maybe<Scalars['BigDecimal']['output']>;
  paymentReference: Maybe<Scalars['String']['output']>;
  platformRetains: Maybe<Scalars['BigDecimal']['output']>;
  policyApplied: Maybe<Scalars['String']['output']>;
  processedAt: Maybe<Scalars['DateTime']['output']>;
  processedBy: Maybe<Scalars['String']['output']>;
  processingFee: Maybe<Scalars['BigDecimal']['output']>;
  reason: Scalars['String']['output'];
  refundAmount: Scalars['BigDecimal']['output'];
  refundPercentage: Maybe<Scalars['Float']['output']>;
  refundTransactionId: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  requestId: Scalars['String']['output'];
  requestType: RefundRequestType;
  requestedAt: Maybe<Scalars['DateTime']['output']>;
  requestedBy: Maybe<Scalars['String']['output']>;
  reviewComments: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  status: RefundRequestStatus;
  ticketId: Scalars['String']['output'];
  ticketNumber: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

/**  Refund Request cursor pagination */
export type RefundRequestConnection = {
  __typename: 'RefundRequestConnection';
  edges: Array<RefundRequestEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type RefundRequestEdge = {
  __typename: 'RefundRequestEdge';
  cursor: Scalars['String']['output'];
  node: RefundRequest;
};

export type RefundRequestFilterInput = {
  buyerId: InputMaybe<Scalars['String']['input']>;
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  eventId: InputMaybe<Scalars['String']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  requestType: InputMaybe<RefundRequestType>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  status: InputMaybe<RefundRequestStatus>;
  ticketId: InputMaybe<Scalars['String']['input']>;
};

export type RefundRequestMutationResponse = {
  __typename: 'RefundRequestMutationResponse';
  data: Maybe<RefundRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type RefundRequestOffsetPage = {
  __typename: 'RefundRequestOffsetPage';
  data: Array<RefundRequest>;
  pagination: PaginationInfo;
};

/**  Refund request status */
export type RefundRequestStatus =
  | 'APPROVED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'FAILED'
  | 'PENDING'
  | 'PROCESSING'
  | 'REJECTED';

/**  How the refund was initiated */
export type RefundRequestType =
  | 'ADMIN_INITIATED'
  | 'EVENT_CANCELLED'
  | 'SYSTEM_AUTOMATIC'
  | 'TICKET_EXPIRED'
  | 'USER_REQUESTED';

/**  ORGANIZER/ADMIN - Refund status breakdown */
export type RefundStatusSummary = {
  __typename: 'RefundStatusSummary';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: RefundRequestStatus;
  totalAmount: Scalars['BigDecimal']['output'];
};

/**  ORGANIZER/ADMIN - Refund summary (organizers see for own events) */
export type RefundSummary = {
  __typename: 'RefundSummary';
  averageRefundAmount: Maybe<Scalars['BigDecimal']['output']>;
  currency: Scalars['String']['output'];
  refundsByStatus: Maybe<Array<RefundStatusSummary>>;
  refundsByType: Maybe<Array<RefundTypeSummary>>;
  totalAmount: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['Int']['output'];
};

export type RefundTicketMutationResponse = {
  __typename: 'RefundTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  ORGANIZER/ADMIN - Refund type breakdown */
export type RefundTypeSummary = {
  __typename: 'RefundTypeSummary';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  requestType: RefundRequestType;
  totalAmount: Scalars['BigDecimal']['output'];
};

/**
 * ============================================================================
 * SECTION 18: INPUT TYPES - NOTIFICATION & DEVICE
 * ============================================================================
 */
export type RegisterDeviceInput = {
  appVersion: InputMaybe<Scalars['String']['input']>;
  deviceModel: InputMaybe<Scalars['String']['input']>;
  deviceName: InputMaybe<Scalars['String']['input']>;
  deviceToken: Scalars['String']['input'];
  osVersion: InputMaybe<Scalars['String']['input']>;
  platform: DevicePlatform;
};

/**
 * User registration input
 * All new users are automatically assigned the CUSTOMER role by default
 */
export type RegisterInput = {
  email: Scalars['String']['input'];
  firstName: InputMaybe<Scalars['String']['input']>;
  lastName: InputMaybe<Scalars['String']['input']>;
  password: Scalars['String']['input'];
  phoneNumber: InputMaybe<Scalars['String']['input']>;
  username: InputMaybe<Scalars['String']['input']>;
};

export type RejectOrganizationInput = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
  reviewNotes: InputMaybe<Scalars['String']['input']>;
};

export type RejectPayoutRequestMutationResponse = {
  __typename: 'RejectPayoutRequestMutationResponse';
  data: Maybe<PayoutRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type RejectRefundRequestMutationResponse = {
  __typename: 'RejectRefundRequestMutationResponse';
  data: Maybe<RefundRequest>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ReminderMutationResponse = {
  __typename: 'ReminderMutationResponse';
  message: Maybe<Scalars['String']['output']>;
  reminder: Maybe<EventReminder>;
  success: Scalars['Boolean']['output'];
};

/**  Reminder status */
export type ReminderStatus =
  /**  Reminder sent */
  | 'CANCELLED'
  /**  Cancelled */
  | 'FAILED'
  | 'SCHEDULED'
  /**  Scheduled for future */
  | 'SENT';

export type RemoveMemberInput = {
  memberId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};

/**  Shared type - must have @shareable on all fields for federation */
export type ReportExport = {
  __typename: 'ReportExport';
  downloadUrl: Maybe<Scalars['String']['output']>;
  errorMessage: Maybe<Scalars['String']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  fileName: Maybe<Scalars['String']['output']>;
  format: ExportFormat;
  generatedAt: Scalars['DateTime']['output'];
  success: Scalars['Boolean']['output'];
};

export type RequestAccountDeletionInput = {
  confirmPassword: InputMaybe<Scalars['String']['input']>;
  feedback: InputMaybe<Scalars['String']['input']>;
  reason: InputMaybe<Scalars['String']['input']>;
};

export type RequestOrganizationChangesInput = {
  changesRequired: Scalars['String']['input'];
  organizationId: Scalars['ID']['input'];
  reviewNotes: InputMaybe<Scalars['String']['input']>;
};

/**
 * Financial Transaction cursor pagination - REMOVED (use PaymentAttempt for payment tracking)
 * Reservation cursor pagination
 */
export type ReservationConnection = {
  __typename: 'ReservationConnection';
  edges: Array<ReservationEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type ReservationEdge = {
  __typename: 'ReservationEdge';
  cursor: Scalars['String']['output'];
  node: TicketReservation;
};

/**  MOBILE - Items in a reservation */
export type ReservationItem = {
  __typename: 'ReservationItem';
  quantity: Scalars['Int']['output'];
  subtotal: Scalars['BigDecimal']['output'];
  ticketTierId: Scalars['String']['output'];
  tierName: Scalars['String']['output'];
  unitPrice: Scalars['BigDecimal']['output'];
};

/**  TransactionOffsetPage - REMOVED (use PaymentAttemptOffsetPage for payment tracking) */
export type ReservationOffsetPage = {
  __typename: 'ReservationOffsetPage';
  data: Array<TicketReservation>;
  pagination: PaginationInfo;
};

/**  Reservation status */
export type ReservationStatus =
  | 'ACTIVE'
  | 'CANCELLED'
  | 'CONVERTED'
  | 'EXPIRED';

export type ReserveTicketsInput = {
  eventId: Scalars['ID']['input'];
  promoCode: InputMaybe<Scalars['String']['input']>;
  selections: Array<TicketSelectionInput>;
};

export type ResolveEscalationInput = {
  action: ApprovalAction;
  escalationId: Scalars['ID']['input'];
  resolutionNotes: Scalars['String']['input'];
};

export type ResolveReconciliationItemInput = {
  externalId: Scalars['String']['input'];
  resolution: Scalars['String']['input'];
};

export type RevokeEventAccessInput = {
  accessId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};

/**
 * Authorization roles for GraphQL operations.
 *
 * Role Hierarchy (higher roles include lower):
 * - SUPER_ADMIN: Full system access
 * - ADMIN: Platform administration (includes ORGANIZER, CUSTOMER)
 * - FINANCE: Financial operations
 * - ORGANIZER: Event management (includes CUSTOMER)
 * - CUSTOMER: Ticket purchasing
 * - AUTHENTICATED: Any logged-in user
 * - PUBLIC: No authentication required
 * - INTERNAL: Service-to-service calls only
 */
export type Role =
  /** Admin role - platform administrators. */
  | 'ADMIN'
  /** Any authenticated user can access. Requires valid JWT token. */
  | 'AUTHENTICATED'
  /** Customer role - regular ticket buyers. */
  | 'CUSTOMER'
  /** Finance role - financial operations access. */
  | 'FINANCE'
  /** Internal service role - service-to-service communication only. */
  | 'INTERNAL'
  /** Organizer role - event creators and managers. */
  | 'ORGANIZER'
  /** No authentication required. Field/operation is publicly accessible. */
  | 'PUBLIC'
  /** Super Admin role - highest privilege level. */
  | 'SUPER_ADMIN';

/**  Role with its default permissions */
export type RolePermissions = {
  __typename: 'RolePermissions';
  /**  Default permissions for this role */
  inheritedFrom: Maybe<Array<Scalars['String']['output']>>;
  /**  Role scope */
  permissions: Array<Permission>;
  role: Scalars['String']['output'];
  /**  Role name */
  scope: PermissionScope;
};

export type SendNotificationInput = {
  actionUrl: InputMaybe<Scalars['String']['input']>;
  body: Scalars['String']['input'];
  channels: Array<NotificationChannel>;
  data: InputMaybe<Scalars['JSON']['input']>;
  imageUrl: InputMaybe<Scalars['String']['input']>;
  priority: InputMaybe<Scalars['String']['input']>;
  scheduledAt: InputMaybe<Scalars['DateTime']['input']>;
  title: Scalars['String']['input'];
  type: NotificationType;
  userId: Scalars['ID']['input'];
};

/**
 * ============================================================================
 * SECTION 19b: ADDITIONAL INPUT TYPES
 * These input types are used by resolver-style mutations
 * ============================================================================
 */
export type SetEventReminderInput = {
  minutesBefore: Scalars['Int']['input'];
  ticketId: Scalars['ID']['input'];
};

export type SetupTwoFactorInput = {
  /**  Required for SMS */
  email: InputMaybe<Scalars['String']['input']>;
  method: TwoFactorMethod;
  phoneNumber: InputMaybe<Scalars['String']['input']>;
};

export type SocialAuthInput = {
  accessToken: Scalars['String']['input'];
  idToken: InputMaybe<Scalars['String']['input']>;
  provider: SocialProvider;
};

/**  Social login connection */
export type SocialConnection = {
  __typename: 'SocialConnection';
  connectedAt: Scalars['DateTime']['output'];
  email: Maybe<Scalars['String']['output']>;
  name: Maybe<Scalars['String']['output']>;
  provider: SocialProvider;
  providerId: Scalars['String']['output'];
};

/**
 * ============================================================================
 * SECTION 8: SUPPORTING TYPES - SOCIAL LINKS & ADDRESS
 * ============================================================================
 */
export type SocialLinks = {
  __typename: 'SocialLinks';
  facebook: Maybe<Scalars['String']['output']>;
  instagram: Maybe<Scalars['String']['output']>;
  linkedin: Maybe<Scalars['String']['output']>;
  tiktok: Maybe<Scalars['String']['output']>;
  twitter: Maybe<Scalars['String']['output']>;
  youtube: Maybe<Scalars['String']['output']>;
};

/**
 * ============================================================================
 * SECTION 16: INPUT TYPES - SUPPORTING
 * ============================================================================
 */
export type SocialLinksInput = {
  facebook: InputMaybe<Scalars['String']['input']>;
  instagram: InputMaybe<Scalars['String']['input']>;
  linkedin: InputMaybe<Scalars['String']['input']>;
  tiktok: InputMaybe<Scalars['String']['input']>;
  twitter: InputMaybe<Scalars['String']['input']>;
  youtube: InputMaybe<Scalars['String']['input']>;
};

/**  Social login provider */
export type SocialProvider =
  | 'APPLE'
  | 'FACEBOOK'
  | 'GOOGLE'
  | 'TWITTER';

/**  Pagination sort direction */
export type SortDirection =
  | 'ASC'
  | 'DESC';

/**  Standalone Escrow Transaction (for audit trail) */
export type StandaloneEscrowTransaction = {
  __typename: 'StandaloneEscrowTransaction';
  amount: Scalars['BigDecimal']['output'];
  balanceAfter: Scalars['BigDecimal']['output'];
  category: Scalars['String']['output'];
  chargebackId: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
  description: Maybe<Scalars['String']['output']>;
  escrowAccountId: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  journalEntryId: Maybe<Scalars['String']['output']>;
  paymentIntentId: Maybe<Scalars['String']['output']>;
  payoutRequestId: Maybe<Scalars['String']['output']>;
  refundRequestId: Maybe<Scalars['String']['output']>;
  ticketId: Maybe<Scalars['String']['output']>;
  timestamp: Scalars['DateTime']['output'];
  type: Scalars['String']['output'];
};

export type StartReconciliationInput = {
  dataSource: InputMaybe<Scalars['String']['input']>;
  /**
   * For ESCROW_JOURNAL reconciliation only:
   * If true, includes CLOSED and CANCELLED accounts in verification.
   * Default is false (only verifies OPEN accounts for better performance).
   * Use true for full audit purposes.
   */
  includeClosed: InputMaybe<Scalars['Boolean']['input']>;
  reconciliationDate: Scalars['DateTime']['input'];
  type: ReconciliationType;
};

export type StatusTransition = {
  __typename: 'StatusTransition';
  fromStatus: Maybe<EventStatus>;
  metadata: Maybe<Scalars['JSON']['output']>;
  /**  Internal audit */
  reason: Maybe<Scalars['String']['output']>;
  toStatus: EventStatus;
  transitionedAt: Scalars['DateTime']['output'];
  transitionedBy: Maybe<Scalars['String']['output']>;
};

export type SuspendOrganizationInput = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};

/**
 * Team invitation for pending members
 * ORGANIZER - Team invitation management
 */
export type TeamInvitation = {
  __typename: 'TeamInvitation';
  acceptedAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Timestamps
   * ─────────────────────────────────────────────────────────────────────
   */
  createdAt: Scalars['DateTime']['output'];
  declinedAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Invitee Information
   * ─────────────────────────────────────────────────────────────────────
   */
  email: Scalars['String']['output'];
  /**  Event-specific access to grant upon acceptance */
  eventAccessGrants: Maybe<Array<EventAccessProposal>>;
  /**  Unique token for acceptance link */
  expiresAt: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  /**
   * Personal message from inviter
   * ─────────────────────────────────────────────────────────────────────
   * Token & Expiry
   * ─────────────────────────────────────────────────────────────────────
   */
  invitationToken: Scalars['String']['output'];
  invitedBy: Maybe<User>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Invitation Details
   * ─────────────────────────────────────────────────────────────────────
   */
  invitedById: Scalars['ID']['output'];
  /**  Optional phone number */
  inviteeName: Maybe<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  organization: Maybe<Organization>;
  /**
   * Optional name of invitee
   * ─────────────────────────────────────────────────────────────────────
   * Organization
   * ─────────────────────────────────────────────────────────────────────
   */
  organizationId: Scalars['ID']['output'];
  /**  Email to send invitation */
  phoneNumber: Maybe<Scalars['String']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Proposed Role
   * ─────────────────────────────────────────────────────────────────────
   */
  proposedRole: OrganizationRole;
  /**
   * Invitation expiry (default 7 days)
   * ─────────────────────────────────────────────────────────────────────
   * Status
   * ─────────────────────────────────────────────────────────────────────
   */
  status: InvitationStatus;
};

/**  TeamInvitation cursor pagination */
export type TeamInvitationConnection = {
  __typename: 'TeamInvitationConnection';
  edges: Array<TeamInvitationEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type TeamInvitationEdge = {
  __typename: 'TeamInvitationEdge';
  cursor: Scalars['String']['output'];
  node: TeamInvitation;
};

export type TeamInvitationOffsetPage = {
  __typename: 'TeamInvitationOffsetPage';
  content: Array<TeamInvitation>;
  pageInfo: PageInfo;
};

export type TeamStatistics = {
  __typename: 'TeamStatistics';
  activeMembers: Scalars['Int']['output'];
  membersByRole: Maybe<Scalars['JSON']['output']>;
  organizationId: Scalars['ID']['output'];
  pendingInvitations: Scalars['Int']['output'];
  totalMembers: Scalars['Int']['output'];
};

/**
 * ============================================================================
 * SECTION 6: PRIMARY TYPES
 * ============================================================================
 * ------------------------------
 * TICKET (Primary Entity)
 * Core booking entity - different fields exposed to different clients
 * ------------------------------
 */
export type Ticket = {
  __typename: 'Ticket';
  barcode: Maybe<Scalars['String']['output']>;
  /**
   * Buyer reference - only own buyer sees full details
   * @provides optimization: We cache buyer name/email/phone on Ticket for display
   */
  buyer: User;
  buyerEmail: Maybe<Scalars['String']['output']>;
  /**  ORGANIZER/ADMIN - Buyer ID for attendee management (privacy protection) */
  buyerId: Scalars['String']['output'];
  /**
   * ---------------------------------------------------------------------------
   * ORGANIZER/ADMIN FIELDS - Buyer PII for attendee management
   * Protected for privacy - only event organizers and admins can see buyer details
   * ---------------------------------------------------------------------------
   */
  buyerName: Maybe<Scalars['String']['output']>;
  buyerPhone: Maybe<Scalars['String']['output']>;
  cancellationReason: Maybe<Scalars['String']['output']>;
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  commissionAmount: Maybe<Scalars['BigDecimal']['output']>;
  /**  ORGANIZER/ADMIN - Financial breakdown for revenue tracking */
  commissionRate: Maybe<Scalars['BigDecimal']['output']>;
  /**
   * ---------------------------------------------------------------------------
   * INTERNAL/ADMIN - Transaction and financial tracking
   * ---------------------------------------------------------------------------
   * INTERNAL - Correlation ID for distributed tracing
   */
  correlationId: Maybe<Scalars['String']['output']>;
  /**
   * ---------------------------------------------------------------------------
   * INTERNAL - Audit fields for system tracking
   * ---------------------------------------------------------------------------
   */
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  /**  Event reference - public for ticket display */
  event: Event;
  eventDate: Maybe<Scalars['String']['output']>;
  eventId: Scalars['String']['output'];
  eventLocationAddress: Maybe<Scalars['String']['output']>;
  eventLocationName: Maybe<Scalars['String']['output']>;
  /**  Cached event data - public for ticket display */
  eventTitle: Scalars['String']['output'];
  /**
   * ---------------------------------------------------------------------------
   * PUBLIC FIELDS - Available to all clients including mobile
   * Core ticket identification and display information
   * ---------------------------------------------------------------------------
   */
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  netAmount: Maybe<Scalars['BigDecimal']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  /**
   * INTERNAL - Organizer and Organization IDs for multi-tenant tracking
   * Used for authorization, reporting, and financial compliance
   */
  organizerId: Maybe<Scalars['String']['output']>;
  paymentInfo: Maybe<PaymentInfo>;
  paymentReference: Maybe<Scalars['String']['output']>;
  price: Scalars['BigDecimal']['output'];
  /**
   * ---------------------------------------------------------------------------
   * TICKET LIFECYCLE - Mixed visibility
   * ---------------------------------------------------------------------------
   */
  purchaseDate: Maybe<Scalars['DateTime']['output']>;
  /**  PUBLIC - QR code for ticket holder's mobile app */
  qrCode: Maybe<Scalars['String']['output']>;
  quantity: Maybe<Scalars['Int']['output']>;
  refundInfo: Maybe<RefundInfo>;
  refundReason: Maybe<Scalars['String']['output']>;
  refundedAt: Maybe<Scalars['DateTime']['output']>;
  status: TicketStatus;
  /**
   * ---------------------------------------------------------------------------
   * PUBLIC FIELDS - Ticket details shown to ticket holder
   * ---------------------------------------------------------------------------
   */
  ticketCategory: Maybe<TicketCategory>;
  ticketCategoryCode: Maybe<Scalars['String']['output']>;
  ticketCategoryName: Maybe<Scalars['String']['output']>;
  ticketNumber: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  /**  Scanner ID who validated */
  usedAt: Maybe<Scalars['DateTime']['output']>;
  validFrom: Maybe<Scalars['DateTime']['output']>;
  validUntil: Maybe<Scalars['DateTime']['output']>;
  /**  ORGANIZER/ADMIN - Entry validation tracking for venue management */
  validatedAt: Maybe<Scalars['DateTime']['output']>;
  validatedBy: Maybe<Scalars['String']['output']>;
};

/**  Ticket category (shared value type with Catalog Service) */
export type TicketCategory =
  | 'CORPORATE'
  | 'EARLY_BIRD'
  | 'FREE'
  | 'GENERAL'
  | 'GROUP'
  | 'PREMIUM'
  | 'PRE_SALE'
  | 'SENIOR'
  | 'SPONSOR'
  | 'STUDENT'
  | 'VIP'
  | 'VVIP';

/**  ORGANIZER/ADMIN - Ticket category analytics (organizers see for own events) */
export type TicketCategoryStats = {
  __typename: 'TicketCategoryStats';
  category: Scalars['String']['output'];
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
};

/**  Ticket cursor pagination */
export type TicketConnection = {
  __typename: 'TicketConnection';
  edges: Array<TicketEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type TicketEdge = {
  __typename: 'TicketEdge';
  cursor: Scalars['String']['output'];
  node: Ticket;
};

export type TicketFilterInput = {
  buyerId: InputMaybe<Scalars['String']['input']>;
  category: InputMaybe<Scalars['String']['input']>;
  eventId: InputMaybe<Scalars['String']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
  purchaseDateAfter: InputMaybe<Scalars['DateTime']['input']>;
  purchaseDateBefore: InputMaybe<Scalars['DateTime']['input']>;
  searchQuery: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<TicketStatus>;
  statuses: InputMaybe<Array<TicketStatus>>;
};

/**
 * ============================================================================
 * SECTION 13: MUTATION RESPONSE TYPES (Consolidated)
 * ============================================================================
 */
export type TicketMutationResponse = {
  __typename: 'TicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type TicketOffsetPage = {
  __typename: 'TicketOffsetPage';
  data: Array<Ticket>;
  pagination: PaginationInfo;
};

export type TicketPurchaseInput = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  buyerEmail: Scalars['String']['input'];
  buyerName: Scalars['String']['input'];
  buyerPhone: InputMaybe<Scalars['String']['input']>;
  correlationId: Scalars['String']['input'];
  currency: InputMaybe<Scalars['String']['input']>;
  eventId: Scalars['String']['input'];
  metadata: InputMaybe<Scalars['JSON']['input']>;
  paymentMethod: PaymentMethod;
  paymentReference: InputMaybe<Scalars['String']['input']>;
  promoCode: InputMaybe<Scalars['String']['input']>;
  quantity: Scalars['Int']['input'];
  ticketCategoryCode: Scalars['String']['input'];
};

/**
 * ============================================================================
 * SECTION 7: FEATURE TYPES
 * Consumer-facing features for ticket purchasing workflow
 * ============================================================================
 * ------------------------------
 * TICKET RESERVATION
 * MOBILE - Temporary ticket holds during checkout flow
 * Customers reserve tickets before payment completion
 * ------------------------------
 */
export type TicketReservation = {
  __typename: 'TicketReservation';
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
  discountAmount: Maybe<Scalars['BigDecimal']['output']>;
  eventId: Scalars['ID']['output'];
  expiresAt: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  items: Array<ReservationItem>;
  promoCodeApplied: Maybe<Scalars['String']['output']>;
  remainingSeconds: Maybe<Scalars['Int']['output']>;
  status: ReservationStatus;
  totalAmount: Scalars['BigDecimal']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
};

export type TicketSelectionInput = {
  quantity: Scalars['Int']['input'];
  ticketTierId: Scalars['String']['input'];
};

/**
 * ============================================================================
 * SECTION 10: STATISTICS TYPES
 * Dashboard statistics for organizers (own events) and admins (platform-wide)
 * ============================================================================
 * ADMIN - Platform-wide ticket statistics
 */
export type TicketStats = {
  __typename: 'TicketStats';
  cancelledTickets: Scalars['Int']['output'];
  expiredTickets: Scalars['Int']['output'];
  pendingPaymentTickets: Scalars['Int']['output'];
  purchasedTickets: Scalars['Int']['output'];
  recentTickets: Maybe<Array<Ticket>>;
  refundedTickets: Scalars['Int']['output'];
  ticketsByCategory: Maybe<Array<TicketCategoryStats>>;
  ticketsByStatus: Maybe<Array<TicketStatusStats>>;
  totalTickets: Scalars['Int']['output'];
  usedTickets: Scalars['Int']['output'];
  validatedTickets: Scalars['Int']['output'];
};

/**
 * ============================================================================
 * SECTION 3: ENUMS
 * ============================================================================
 * Ticket lifecycle status
 */
export type TicketStatus =
  /**  Past event date, never used */
  | 'CANCELLED'
  /**  Payment received */
  | 'CONFIRMED'
  /**  Fully used (for multi-entry tickets) */
  | 'EXPIRED'
  /**  Refund processed */
  | 'PAYMENT_FAILED'
  | 'PENDING_PAYMENT'
  /**  Ticket reserved, awaiting payment */
  | 'PURCHASED'
  /**  Cancelled before event */
  | 'REFUNDED'
  /**  Payment attempt failed */
  | 'REFUND_PENDING'
  /**  Scanned/checked at venue */
  | 'USED'
  /**  Ticket confirmed and issued */
  | 'VALIDATED';

/**  ADMIN - Ticket status breakdown */
export type TicketStatusStats = {
  __typename: 'TicketStatusStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: TicketStatus;
};

export type TicketTier = {
  __typename: 'TicketTier';
  /**  ORGANIZER/ADMIN - access code for hidden/restricted tiers */
  accessCode: Maybe<Scalars['String']['output']>;
  /**  PUBLIC - availability info for consumers */
  availableQuantity: Scalars['Int']['output'];
  benefits: Maybe<Array<Scalars['String']['output']>>;
  /**  Multi-tenant tracking */
  code: Scalars['String']['output'];
  /**  AUDIT - internal tracking */
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  description: Maybe<Scalars['String']['output']>;
  earlyBirdEndsAt: Maybe<Scalars['DateTime']['output']>;
  earlyBirdPrice: Maybe<Scalars['BigDecimal']['output']>;
  eventId: Scalars['ID']['output'];
  /**  PUBLIC - for ticket selection UI */
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  isHidden: Scalars['Boolean']['output'];
  maxPerOrder: Maybe<Scalars['Int']['output']>;
  minPerOrder: Maybe<Scalars['Int']['output']>;
  name: Scalars['String']['output'];
  organizationId: Maybe<Scalars['String']['output']>;
  originalPrice: Maybe<Scalars['BigDecimal']['output']>;
  price: Scalars['BigDecimal']['output'];
  /**  ORGANIZER/ADMIN - inventory management (full quantity/sold info) */
  quantity: Scalars['Int']['output'];
  salesEndAt: Maybe<Scalars['DateTime']['output']>;
  salesStartAt: Maybe<Scalars['DateTime']['output']>;
  soldQuantity: Scalars['Int']['output'];
  sortOrder: Scalars['Int']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type TicketTierStats = {
  __typename: 'TicketTierStats';
  eventId: Scalars['ID']['output'];
  grossRevenue: Maybe<Scalars['BigDecimal']['output']>;
  isActive: Scalars['Boolean']['output'];
  price: Scalars['BigDecimal']['output'];
  salesPercentage: Scalars['Float']['output'];
  ticketsRefunded: Scalars['Int']['output'];
  ticketsSold: Scalars['Int']['output'];
  tierCode: Scalars['String']['output'];
  tierId: Scalars['ID']['output'];
  tierName: Scalars['String']['output'];
  totalQuantity: Scalars['Int']['output'];
};

/**  ORGANIZER - Tier-level check-in statistics */
export type TierCheckInStats = {
  __typename: 'TierCheckInStats';
  checkInRate: Scalars['Float']['output'];
  checkedIn: Scalars['Int']['output'];
  sold: Scalars['Int']['output'];
  tierId: Scalars['String']['output'];
  tierName: Scalars['String']['output'];
};

export type TierMutationResponse = {
  __typename: 'TierMutationResponse';
  data: Maybe<TicketTier>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**  Time unit for reporting */
export type TimeUnit =
  | 'DAY'
  | 'HOUR'
  | 'MONTH'
  | 'WEEK';

export type TimelineEvent = {
  __typename: 'TimelineEvent';
  action: ApprovalAction;
  /**  Actor information */
  actorId: Scalars['String']['output'];
  actorName: Scalars['String']['output'];
  actorRole: Maybe<Scalars['String']['output']>;
  comments: Maybe<Scalars['String']['output']>;
  /**  Details */
  description: Scalars['String']['output'];
  eventId: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  internalNotes: Maybe<Scalars['String']['output']>;
  /**  For escalations */
  isEscalationRelated: Scalars['Boolean']['output'];
  /**  Metadata for extensibility */
  metadata: Maybe<Scalars['JSON']['output']>;
  newStatus: Maybe<EventStatus>;
  /**  Status at this point */
  previousStatus: Maybe<EventStatus>;
  timestamp: Scalars['DateTime']['output'];
};

/**  Token validation response */
export type TokenValidation = {
  __typename: 'TokenValidation';
  email: Maybe<Scalars['String']['output']>;
  roles: Maybe<Array<Scalars['String']['output']>>;
  userId: Maybe<Scalars['String']['output']>;
  valid: Scalars['Boolean']['output'];
};

/**  Transaction issue types for recovery operations */
export type TransactionIssueType =
  /**  Callback not received */
  | 'AMOUNT_MISMATCH'
  /**  Payment amount doesn't match expected */
  | 'DUPLICATE_TRANSACTION'
  /**  Transaction validation failed */
  | 'MANUAL_REVIEW_REQUIRED'
  /**  Payment provider returned error */
  | 'NETWORK_FAILURE'
  /**  Needs reconciliation with external system */
  | 'OTHER'
  | 'PAYMENT_TIMEOUT'
  /**  Potential duplicate detected */
  | 'PROVIDER_ERROR'
  /**  Flagged for manual review */
  | 'RECONCILIATION_NEEDED'
  /**  Network communication failure */
  | 'VALIDATION_FAILURE'
  /**  Payment gateway timeout */
  | 'WEBHOOK_MISSED';

/**  How transaction issues are resolved */
export type TransactionResolutionType =
  | 'AUTO_RESOLVED'
  /**  Reconciled with external system */
  | 'ESCALATED'
  /**  System automatically resolved */
  | 'MANUAL_APPROVAL'
  /**  Admin manually approved */
  | 'MANUAL_REJECTION'
  /**  Written off as loss */
  | 'RECONCILED'
  /**  Successfully retried */
  | 'REFUNDED'
  /**  Admin manually rejected */
  | 'RETRIED_SUCCESS'
  /**  Refunded to customer */
  | 'WRITTEN_OFF';

/**  Review status for transactions requiring attention */
export type TransactionReviewStatus =
  /**  Review completed */
  | 'ESCALATED'
  | 'NONE'
  /**  No review needed */
  | 'PENDING_REVIEW'
  /**  Currently being reviewed */
  | 'REVIEWED'
  /**  Waiting for review */
  | 'UNDER_REVIEW';

/**  ADMIN - Platform-wide transaction statistics */
export type TransactionStats = {
  __typename: 'TransactionStats';
  averageTransactionValue: Maybe<Scalars['BigDecimal']['output']>;
  completedTransactions: Scalars['Int']['output'];
  failedTransactions: Scalars['Int']['output'];
  pendingTransactions: Scalars['Int']['output'];
  timedOutTransactions: Scalars['Int']['output'];
  totalCommissions: Scalars['BigDecimal']['output'];
  totalTransactions: Scalars['Int']['output'];
  totalVolume: Scalars['BigDecimal']['output'];
};

/**  Financial transaction status */
export type TransactionStatus =
  | 'CANCELLED'
  | 'COMPLETED'
  | 'FAILED'
  | 'FULFILLED'
  | 'PENDING'
  | 'PENDING_VERIFICATION'
  | 'PROCESSING'
  | 'RETRYING'
  | 'REVERSED'
  | 'ROLLED_BACK'
  | 'TIMED_OUT';

export type TransferOwnershipInput = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};

/**  Ownership transfer status - synchronized with booking service */
export type TransferStatus =
  /**  Transfer initiated, waiting for recipient acceptance */
  | 'ACCEPTED'
  /**  Transfer expired after timeout */
  | 'CANCELLED'
  /**  Recipient rejected the transfer */
  | 'COMPLETED'
  /**  Transfer completed */
  | 'EXPIRED'
  | 'PENDING'
  /**  Recipient accepted */
  | 'REJECTED';

/**  Two-factor authentication method */
export type TwoFactorMethod =
  | 'AUTHENTICATOR_APP'
  | 'BACKUP_CODE'
  | 'EMAIL'
  | 'SMS';

export type TwoFactorSetupResponse = {
  __typename: 'TwoFactorSetupResponse';
  /**  QR code for authenticator */
  backupCodes: Maybe<Array<Scalars['String']['output']>>;
  message: Maybe<Scalars['String']['output']>;
  /**  For AUTHENTICATOR_APP */
  qrCodeUrl: Maybe<Scalars['String']['output']>;
  secret: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type UpdateBankAccountInput = {
  accountHolderName: InputMaybe<Scalars['String']['input']>;
  accountNumber: InputMaybe<Scalars['String']['input']>;
  accountType: InputMaybe<Scalars['String']['input']>;
  bankCode: InputMaybe<Scalars['String']['input']>;
  bankName: InputMaybe<Scalars['String']['input']>;
  branchCode: InputMaybe<Scalars['String']['input']>;
  branchName: InputMaybe<Scalars['String']['input']>;
  isDefault: InputMaybe<Scalars['Boolean']['input']>;
  swiftCode: InputMaybe<Scalars['String']['input']>;
};

export type UpdateBankAccountMutationResponse = {
  __typename: 'UpdateBankAccountMutationResponse';
  data: Maybe<BankAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type UpdateCityInput = {
  code: InputMaybe<Scalars['String']['input']>;
  country: InputMaybe<Scalars['String']['input']>;
  isActive: InputMaybe<Scalars['Boolean']['input']>;
  name: InputMaybe<Scalars['String']['input']>;
  provinceId: InputMaybe<Scalars['String']['input']>;
};

export type UpdateCoordinatesInput = {
  latitude: InputMaybe<Scalars['Float']['input']>;
  longitude: InputMaybe<Scalars['Float']['input']>;
};

export type UpdateEscrowAccountInput = {
  lockReason: InputMaybe<Scalars['String']['input']>;
  lockUntil: InputMaybe<Scalars['DateTime']['input']>;
  status: InputMaybe<EscrowAccountStatus>;
};

export type UpdateEventAccessInput = {
  accessId: Scalars['ID']['input'];
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  newRole: InputMaybe<EventRole>;
};

export type UpdateEventCategoryInput = {
  code: InputMaybe<Scalars['String']['input']>;
  color: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  iconUrl: InputMaybe<Scalars['String']['input']>;
  isActive: InputMaybe<Scalars['Boolean']['input']>;
  name: InputMaybe<Scalars['String']['input']>;
  sortOrder: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateEventInput = {
  additionalInfo: InputMaybe<Scalars['JSON']['input']>;
  bannerImageUrl: InputMaybe<Scalars['String']['input']>;
  cancellationPolicy: InputMaybe<Scalars['String']['input']>;
  categoryId: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  enableWaitlist: InputMaybe<Scalars['Boolean']['input']>;
  endDateTime: InputMaybe<Scalars['DateTime']['input']>;
  eventDateTime: InputMaybe<Scalars['DateTime']['input']>;
  featured: InputMaybe<Scalars['Boolean']['input']>;
  galleryImages: InputMaybe<Array<Scalars['String']['input']>>;
  isFreeEvent: InputMaybe<Scalars['Boolean']['input']>;
  isVirtual: InputMaybe<Scalars['Boolean']['input']>;
  location: InputMaybe<EventLocationInput>;
  refundPolicy: InputMaybe<Scalars['String']['input']>;
  tags: InputMaybe<Array<Scalars['String']['input']>>;
  termsAndConditions: InputMaybe<Scalars['String']['input']>;
  thumbnailImageUrl: InputMaybe<Scalars['String']['input']>;
  title: InputMaybe<Scalars['String']['input']>;
  totalCapacity: InputMaybe<Scalars['Int']['input']>;
  virtualEventPlatform: InputMaybe<Scalars['String']['input']>;
  virtualEventUrl: InputMaybe<Scalars['String']['input']>;
  waitlistCapacity: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateMemberRoleInput = {
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  deniedPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  memberId: Scalars['ID']['input'];
  newRole: OrganizationRole;
  organizationId: Scalars['ID']['input'];
};

export type UpdateNotificationPreferencesInput = {
  emailEnabled: InputMaybe<Scalars['Boolean']['input']>;
  eventReminders: InputMaybe<Scalars['Boolean']['input']>;
  eventUpdates: InputMaybe<Scalars['Boolean']['input']>;
  inAppEnabled: InputMaybe<Scalars['Boolean']['input']>;
  marketingEmails: InputMaybe<Scalars['Boolean']['input']>;
  paymentNotifications: InputMaybe<Scalars['Boolean']['input']>;
  pushEnabled: InputMaybe<Scalars['Boolean']['input']>;
  quietHoursEnd: InputMaybe<Scalars['String']['input']>;
  quietHoursStart: InputMaybe<Scalars['String']['input']>;
  reminderHoursBefore: InputMaybe<Scalars['Int']['input']>;
  smsEnabled: InputMaybe<Scalars['Boolean']['input']>;
  systemAnnouncements: InputMaybe<Scalars['Boolean']['input']>;
  teamNotifications: InputMaybe<Scalars['Boolean']['input']>;
  ticketNotifications: InputMaybe<Scalars['Boolean']['input']>;
  timezone: InputMaybe<Scalars['String']['input']>;
  whatsappEnabled: InputMaybe<Scalars['Boolean']['input']>;
};

/**
 * ============================================================================
 * SECTION 17: INPUT TYPES - ORGANIZATION & TEAM
 * ============================================================================
 */
export type UpdateOrganizationInput = {
  bannerUrl: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  logoUrl: InputMaybe<Scalars['String']['input']>;
  name: InputMaybe<Scalars['String']['input']>;
};

export type UpdateOrganizationSettingsInput = {
  allowMembersToInvite: InputMaybe<Scalars['Boolean']['input']>;
  defaultEventVisibility: InputMaybe<Scalars['String']['input']>;
  inviteRequiresApproval: InputMaybe<Scalars['Boolean']['input']>;
  managersCanRequestPayouts: InputMaybe<Scalars['Boolean']['input']>;
  marketersCanViewFinancials: InputMaybe<Scalars['Boolean']['input']>;
  maxTeamMembers: InputMaybe<Scalars['Int']['input']>;
  notifyOwnerOnEventCreated: InputMaybe<Scalars['Boolean']['input']>;
  notifyOwnerOnMemberJoin: InputMaybe<Scalars['Boolean']['input']>;
  notifyOwnerOnPayoutRequest: InputMaybe<Scalars['Boolean']['input']>;
  organizationId: Scalars['ID']['input'];
  requireEventApproval: InputMaybe<Scalars['Boolean']['input']>;
};

export type UpdatePlatformConfigurationInput = {
  adminNotificationChannel: InputMaybe<ApprovalNotificationChannel>;
  allowSelfApproval: InputMaybe<Scalars['Boolean']['input']>;
  approvalSlaHours: InputMaybe<Scalars['Int']['input']>;
  approvalWarningThresholdHours: InputMaybe<Scalars['Int']['input']>;
  autoEscalationEnabled: InputMaybe<Scalars['Boolean']['input']>;
  escalationDelayHours: InputMaybe<Scalars['Int']['input']>;
  escalationRecipientRole: InputMaybe<Scalars['String']['input']>;
  escalationReminderIntervalHours: InputMaybe<Scalars['Int']['input']>;
  maxEscalationReminders: InputMaybe<Scalars['Int']['input']>;
  organizerNotificationChannel: InputMaybe<ApprovalNotificationChannel>;
  requireCommentsOnChangesRequested: InputMaybe<Scalars['Boolean']['input']>;
  requireCommentsOnRejection: InputMaybe<Scalars['Boolean']['input']>;
  sendEscalationNotifications: InputMaybe<Scalars['Boolean']['input']>;
  sendSlaWarningNotifications: InputMaybe<Scalars['Boolean']['input']>;
};

export type UpdatePromoCodeInput = {
  applicableTiers: InputMaybe<Array<Scalars['String']['input']>>;
  discountType: InputMaybe<DiscountType>;
  discountValue: InputMaybe<Scalars['BigDecimal']['input']>;
  isActive: InputMaybe<Scalars['Boolean']['input']>;
  maxDiscountAmount: InputMaybe<Scalars['BigDecimal']['input']>;
  maxUses: InputMaybe<Scalars['Int']['input']>;
  minPurchaseAmount: InputMaybe<Scalars['BigDecimal']['input']>;
  validFrom: InputMaybe<Scalars['DateTime']['input']>;
  validUntil: InputMaybe<Scalars['DateTime']['input']>;
};

export type UpdateProvinceInput = {
  code: InputMaybe<Scalars['String']['input']>;
  country: InputMaybe<Scalars['String']['input']>;
  isActive: InputMaybe<Scalars['Boolean']['input']>;
  name: InputMaybe<Scalars['String']['input']>;
};

export type UpdateTicketTierInput = {
  accessCode: InputMaybe<Scalars['String']['input']>;
  benefits: InputMaybe<Array<Scalars['String']['input']>>;
  description: InputMaybe<Scalars['String']['input']>;
  earlyBirdEndsAt: InputMaybe<Scalars['DateTime']['input']>;
  earlyBirdPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  isActive: InputMaybe<Scalars['Boolean']['input']>;
  isHidden: InputMaybe<Scalars['Boolean']['input']>;
  maxPerOrder: InputMaybe<Scalars['Int']['input']>;
  minPerOrder: InputMaybe<Scalars['Int']['input']>;
  name: InputMaybe<Scalars['String']['input']>;
  price: InputMaybe<Scalars['BigDecimal']['input']>;
  quantity: InputMaybe<Scalars['Int']['input']>;
  salesEndAt: InputMaybe<Scalars['DateTime']['input']>;
  salesStartAt: InputMaybe<Scalars['DateTime']['input']>;
  sortOrder: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateUserInput = {
  avatarUrl: InputMaybe<Scalars['String']['input']>;
  bio: InputMaybe<Scalars['String']['input']>;
  dateOfBirth: InputMaybe<Scalars['DateTime']['input']>;
  firstName: InputMaybe<Scalars['String']['input']>;
  gender: InputMaybe<Scalars['String']['input']>;
  lastName: InputMaybe<Scalars['String']['input']>;
  locale: InputMaybe<Scalars['String']['input']>;
  phoneNumber: InputMaybe<Scalars['String']['input']>;
  timezone: InputMaybe<Scalars['String']['input']>;
};

export type UploadVerificationDocumentInput = {
  documentType: Scalars['String']['input'];
  documentUrl: Scalars['String']['input'];
  fileName: InputMaybe<Scalars['String']['input']>;
  fileSize: InputMaybe<Scalars['Int']['input']>;
  mimeType: InputMaybe<Scalars['String']['input']>;
  organizationId: Scalars['ID']['input'];
};

export type UseTicketMutationResponse = {
  __typename: 'UseTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

/**
 * ============================================================================
 * SECTION 4: ENTITY STUBS (Owned by other services)
 * ============================================================================
 * User stub - owned by Identity Service
 * Includes fields we can @provides from cached Ticket buyer data
 * NOTE: @external field types MUST match the owning service (identity)
 */
export type User = {
  __typename: 'User';
  accountStatus: AccountStatus;
  /**
   * KYC verification for organizers
   * ─────────────────────────────────────────────────────────────────────
   * Account Flags
   * ADMIN - Account state management
   * ─────────────────────────────────────────────────────────────────────
   */
  active: Scalars['Boolean']['output'];
  /**  MOBILE - Quick count for my tickets badge */
  activeTicketCount: Scalars['Int']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Profile Information
   * PUBLIC - Optional profile details
   * ─────────────────────────────────────────────────────────────────────
   */
  avatarUrl: Maybe<Scalars['String']['output']>;
  /**  Profile picture URL */
  bio: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  /**  User bio/description */
  dateOfBirth: Maybe<Scalars['DateTime']['output']>;
  /**
   * Unique username
   * ADMIN - Email is PII, only visible to admins or the user themselves
   * @shareable allows booking-service to @provides this from cached Ticket data
   */
  email: Scalars['String']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Verification Status
   * ADMIN - Verification status for user management
   * ─────────────────────────────────────────────────────────────────────
   */
  emailVerified: Scalars['Boolean']['output'];
  firstName: Scalars['String']['output'];
  /**  @shareable allows booking-service to @provides this from cached Ticket data */
  fullName: Scalars['String']['output'];
  gender: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  identityVerified: Scalars['Boolean']['output'];
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Audit Fields
   * ADMIN - Audit trail for user management
   * ─────────────────────────────────────────────────────────────────────
   */
  lastLoginAt: Maybe<Scalars['DateTime']['output']>;
  lastName: Scalars['String']['output'];
  locale: Maybe<Scalars['String']['output']>;
  locked: Scalars['Boolean']['output'];
  memberSince: Maybe<Scalars['DateTime']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Notification Preferences
   * MOBILE - User manages their own notification preferences
   * ─────────────────────────────────────────────────────────────────────
   */
  notificationPreferences: Maybe<NotificationPreferences>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Organization Memberships
   * ORGANIZER - Organizer dashboard needs organization access
   * ─────────────────────────────────────────────────────────────────────
   */
  organizationMemberships: Maybe<Array<OrganizationMember>>;
  /**
   * Computed: firstName + lastName
   * ADMIN - Phone number is PII
   * @shareable allows booking-service to @provides this from cached Ticket data
   */
  phoneNumber: Maybe<Scalars['String']['output']>;
  phoneVerified: Scalars['Boolean']['output'];
  primaryOrganization: Maybe<Organization>;
  /**  Primary organization (for quick access) */
  primaryOrganizationId: Maybe<Scalars['ID']['output']>;
  /**  MOBILE - Customer's purchased tickets (my tickets list) */
  purchasedTickets: Array<Ticket>;
  /**
   * All roles assigned to the user.
   * A user can have multiple roles. CUSTOMER is the base role that all users have.
   * Example: [CUSTOMER, ORGANIZER] for an event organizer
   */
  roles: Array<UserType>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Social Connections
   * MOBILE - User manages their own social connections
   * ─────────────────────────────────────────────────────────────────────
   */
  socialConnections: Maybe<Array<SocialConnection>>;
  /**  Preferred locale (e.g., "en-ZM") */
  timezone: Maybe<Scalars['String']['output']>;
  totalEventsAttended: Maybe<Scalars['Int']['output']>;
  /**  ORGANIZER/ADMIN - Financial tracking for customer spending */
  totalSpent: Scalars['BigDecimal']['output'];
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Statistics (as customer)
   * PUBLIC - Gamification/profile stats
   * ─────────────────────────────────────────────────────────────────────
   */
  totalTicketsPurchased: Maybe<Scalars['Int']['output']>;
  /**
   * ─────────────────────────────────────────────────────────────────────
   * Two-Factor Authentication
   * MOBILE - User can manage their own 2FA settings
   * ─────────────────────────────────────────────────────────────────────
   */
  twoFactorEnabled: Scalars['Boolean']['output'];
  twoFactorMethod: Maybe<TwoFactorMethod>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * Keycloak user ID (sub claim from JWT)
   * ─────────────────────────────────────────────────────────────────────
   * Core Identity (synced with Keycloak)
   * PUBLIC - Basic profile information
   * ─────────────────────────────────────────────────────────────────────
   */
  username: Scalars['String']['output'];
};

/**
 * ============================================================================
 * SECTION 14b: CURSOR PAGINATION TYPES (Relay-style Connections)
 * ============================================================================
 * Cursor-based pagination following the Relay Connection Specification.
 * Used for infinite scroll, mobile apps, and real-time feeds.
 * https://relay.dev/graphql/connections.htm
 * User cursor pagination
 */
export type UserConnection = {
  __typename: 'UserConnection';
  edges: Array<UserEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

/**
 * ============================================================================
 * SECTION 10: DEVICE & REMINDER TYPES
 * ============================================================================
 */
export type UserDevice = {
  __typename: 'UserDevice';
  appVersion: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  deviceModel: Maybe<Scalars['String']['output']>;
  deviceName: Maybe<Scalars['String']['output']>;
  /**  Device info */
  deviceToken: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  /**  Status */
  isActive: Scalars['Boolean']['output'];
  isPrimary: Scalars['Boolean']['output'];
  /**  Timestamps */
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  osVersion: Maybe<Scalars['String']['output']>;
  platform: DevicePlatform;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
};

export type UserEdge = {
  __typename: 'UserEdge';
  cursor: Scalars['String']['output'];
  node: User;
};

/**  User-related responses */
export type UserMutationResponse = {
  __typename: 'UserMutationResponse';
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  user: Maybe<User>;
};

/**  Paged results */
export type UserOffsetPage = {
  __typename: 'UserOffsetPage';
  content: Array<User>;
  pageInfo: PageInfo;
};

/**
 * ADMIN - Statistics for users grouped by role
 * Note: Since users can have multiple roles, the total count across all
 * roles may exceed the total number of users (one user counted per role)
 */
export type UserRoleStats = {
  __typename: 'UserRoleStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  role: UserType;
};

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * USER STATISTICS (Admin Dashboard)
 * ─────────────────────────────────────────────────────────────────────────────
 * Comprehensive user statistics for admin dashboard.
 * Uses MongoDB aggregation pipelines for efficient computation.
 * ADMIN - Main container for user statistics
 */
export type UserStats = {
  __typename: 'UserStats';
  activeUsers: Scalars['Int']['output'];
  adminUsers: Scalars['Int']['output'];
  attendees: Scalars['Int']['output'];
  /**  Growth metric (month-over-month percentage) */
  growthRate: Maybe<Scalars['Float']['output']>;
  lockedUsers: Scalars['Int']['output'];
  /**  Time-based counts */
  newUsersThisMonth: Scalars['Int']['output'];
  newUsersThisWeek: Scalars['Int']['output'];
  organizers: Scalars['Int']['output'];
  pendingVerificationUsers: Scalars['Int']['output'];
  /**  Account status counts */
  suspendedUsers: Scalars['Int']['output'];
  /**  Core counts */
  totalUsers: Scalars['Int']['output'];
  /**  Breakdowns */
  usersByRole: Array<UserRoleStats>;
  usersByStatus: Array<UserStatusStats>;
  verifiedUsers: Scalars['Int']['output'];
};

/**  ADMIN - Statistics for users grouped by account status */
export type UserStatusStats = {
  __typename: 'UserStatusStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: AccountStatus;
};

/**
 * ============================================================================
 * SECTION 3: ENUMS - PLATFORM & ACCOUNT
 * ============================================================================
 * Platform-level user type (Keycloak realm roles)
 * Determines system-wide capabilities
 */
export type UserType =
  /**  Event organizer who creates and manages events */
  | 'ADMIN'
  | 'CUSTOMER'
  /**  Super administrator with full system access */
  | 'FINANCE'
  /**  Regular customer who buys tickets */
  | 'ORGANIZER'
  /**  Platform administrator */
  | 'SUPER_ADMIN';

/**  Specific ticket mutation response types matching resolver return types */
export type ValidateTicketMutationResponse = {
  __typename: 'ValidateTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type VerificationDocument = {
  __typename: 'VerificationDocument';
  documentType: Scalars['String']['output'];
  /**  ID_DOCUMENT, BUSINESS_LICENSE, TAX_CERT, etc. */
  documentUrl: Scalars['String']['output'];
  fileName: Maybe<Scalars['String']['output']>;
  fileSize: Maybe<Scalars['Int']['output']>;
  id: Scalars['ID']['output'];
  mimeType: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  status: DocumentStatus;
  uploadedAt: Scalars['DateTime']['output'];
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedBy: Maybe<User>;
  verifiedById: Maybe<Scalars['String']['output']>;
};

export type VerifyBankAccountMutationResponse = {
  __typename: 'VerifyBankAccountMutationResponse';
  data: Maybe<BankAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type VerifyTwoFactorInput = {
  code: Scalars['String']['input'];
  method: TwoFactorMethod;
};
