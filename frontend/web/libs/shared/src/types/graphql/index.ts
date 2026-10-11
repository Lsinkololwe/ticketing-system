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
  PhoneNumber: { input: string; output: string; }
  join__DirectiveArguments: { input: any; output: any; }
  join__FieldSet: { input: any; output: any; }
  join__FieldValue: { input: any; output: any; }
  link__Import: { input: any; output: any; }
};

export type AcceptInvitationInput = {
  invitationToken: Scalars['String']['input'];
};

export type AccessGrantStatus =
  | 'ACTIVE'
  | 'EXPIRED'
  | 'REVOKED'
  | 'SUSPENDED';

export type AccountBalance = {
  __typename: 'AccountBalance';
  accountCode: Scalars['String']['output'];
  accountName: Scalars['String']['output'];
  accountType: Scalars['String']['output'];
  creditBalance: Scalars['BigDecimal']['output'];
  debitBalance: Scalars['BigDecimal']['output'];
  netBalance: Scalars['BigDecimal']['output'];
};

export type AccountSession = {
  __typename: 'AccountSession';
  clients: Array<Scalars['String']['output']>;
  current: Scalars['Boolean']['output'];
  id: Scalars['ID']['output'];
  ipAddress: Maybe<Scalars['String']['output']>;
  lastAccessAt: Maybe<Scalars['DateTime']['output']>;
  startedAt: Maybe<Scalars['DateTime']['output']>;
};

export type AccountState =
  | 'ACTIVE'
  | 'DELETED'
  | 'MERGED'
  | 'PROVISIONING'
  | 'SUSPENDED';

export type AccountStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'LOCKED'
  | 'PENDING_DELETION'
  | 'PENDING_VERIFICATION'
  | 'SUSPENDED';

export type AccountSubType =
  | 'BAD_DEBT'
  | 'BAD_DEBT_EXPENSE'
  | 'BANK_ACCOUNT'
  | 'CHARGEBACK_EXPENSE'
  | 'CHARGEBACK_FEES'
  | 'CHARGEBACK_LOSS'
  | 'CHARGEBACK_RECEIVABLE'
  | 'CHARGEBACK_RECOVERY_RECEIVABLE'
  | 'COMMISSION_RECEIVABLE'
  | 'COMMISSION_REVENUE'
  | 'DEFERRED_REVENUE'
  | 'ESCROW_PAYABLE'
  | 'FEES_PAYABLE'
  | 'FEE_REVENUE'
  | 'GATEWAY_FEES'
  | 'GATEWAY_FEE_EXPENSE'
  | 'GATEWAY_RECEIVABLE'
  | 'OTHER_EXPENSE'
  | 'OTHER_INCOME'
  | 'PAYOUTS_PAYABLE'
  | 'PAYOUT_PAYABLE'
  | 'REFUNDS_PAYABLE'
  | 'REFUND_PAYABLE'
  | 'RESERVE'
  | 'RETAINED_EARNINGS'
  | 'TAX_PAYABLE'
  | 'VERIFICATION_EXPENSE';

export type AccountSummary = {
  __typename: 'AccountSummary';
  accountId: Scalars['ID']['output'];
  accountNumber: Scalars['String']['output'];
  availableForPayout: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  currentBalance: Scalars['BigDecimal']['output'];
  eventId: Scalars['String']['output'];
  eventTitle: Scalars['String']['output'];
  organization: Maybe<Organization>;
  organizerId: Scalars['String']['output'];
  status: EscrowAccountStatus;
  totalCommissions: Scalars['BigDecimal']['output'];
  totalDeposits: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalWithdrawals: Scalars['BigDecimal']['output'];
  transactionCount: Scalars['Int']['output'];
};

export type AccountType =
  | 'ASSET'
  | 'EQUITY'
  | 'EXPENSE'
  | 'LIABILITY'
  | 'REVENUE';

export type AdminTicketUpdateInput = {
  buyerEmail?: InputMaybe<Scalars['String']['input']>;
  buyerName?: InputMaybe<Scalars['String']['input']>;
  buyerPhone?: InputMaybe<Scalars['String']['input']>;
  notes?: InputMaybe<Scalars['String']['input']>;
  ticketCategoryCode?: InputMaybe<Scalars['String']['input']>;
};

export type AlertSeverity =
  | 'CRITICAL'
  | 'INFO'
  | 'WARNING';

export type AlertStatus =
  | 'ACKNOWLEDGED'
  | 'OPEN'
  | 'RESOLVED';

export type AnnouncementSegment =
  | 'ALL'
  | 'BUYERS'
  | 'ORGANIZERS'
  | 'STAFF';

export type ApprovalAction =
  | 'APPROVED'
  | 'ASSIGNED'
  | 'CHANGES_REQUESTED'
  | 'CLAIM_EXPIRED'
  | 'CLAIM_RELEASED'
  | 'COMMENT_ADDED'
  | 'ESCALATED'
  | 'ESCALATION_RESOLVED'
  | 'REJECTED'
  | 'RESUBMITTED'
  | 'SUBMITTED'
  | 'VIEWED';

export type ApprovalBlocker =
  | 'NO_CAPACITY'
  | 'NO_LOCATION'
  | 'NO_PUBLISHED_TIER';

export type ApprovalEscalation = {
  __typename: 'ApprovalEscalation';
  acknowledgedAt: Maybe<Scalars['DateTime']['output']>;
  acknowledgedBy: Maybe<Scalars['String']['output']>;
  acknowledgedByName: Maybe<Scalars['String']['output']>;
  escalatedTo: Scalars['String']['output'];
  escalatedToName: Scalars['String']['output'];
  eventId: Scalars['String']['output'];
  eventTitle: Scalars['String']['output'];
  hoursOverdue: Scalars['Int']['output'];
  id: Scalars['ID']['output'];
  lastReminderAt: Maybe<Scalars['DateTime']['output']>;
  nextReminderAt: Maybe<Scalars['DateTime']['output']>;
  originalReviewerId: Maybe<Scalars['String']['output']>;
  originalReviewerName: Maybe<Scalars['String']['output']>;
  reason: Scalars['String']['output'];
  remindersSent: Scalars['Int']['output'];
  resolutionNotes: Maybe<Scalars['String']['output']>;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  resolvedBy: Maybe<Scalars['String']['output']>;
  resolvedByName: Maybe<Scalars['String']['output']>;
  slaDeadline: Scalars['DateTime']['output'];
  status: EscalationStatus;
  triggeredAt: Scalars['DateTime']['output'];
};

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
  retryCount: Scalars['Int']['output'];
  sentAt: Maybe<Scalars['DateTime']['output']>;
  subject: Scalars['String']['output'];
  type: ApprovalNotificationType;
};

export type ApprovalNotificationChannel =
  | 'BOTH'
  | 'EMAIL'
  | 'IN_APP';

export type ApprovalNotificationType =
  | 'APPROVAL_GRANTED'
  | 'CHANGES_REQUESTED'
  | 'ESCALATION_TRIGGERED'
  | 'REJECTION_ISSUED'
  | 'REMINDER_PENDING'
  | 'REVIEW_ASSIGNED'
  | 'SLA_WARNING'
  | 'SUBMISSION_RECEIVED';

export type ApprovalStats = {
  __typename: 'ApprovalStats';
  activeEscalations: Scalars['Int']['output'];
  approvedToday: Scalars['Int']['output'];
  averageEscalationResolutionHours: Maybe<Scalars['Float']['output']>;
  averageProcessingTimeHours: Scalars['Float']['output'];
  changesRequestedToday: Scalars['Int']['output'];
  escalationsThisWeek: Scalars['Int']['output'];
  pendingByDaysWaiting: Array<DaysWaitingBreakdown>;
  rejectedToday: Scalars['Int']['output'];
  slaComplianceRate: Scalars['Float']['output'];
  submittedToday: Scalars['Int']['output'];
  totalEscalated: Scalars['Int']['output'];
  totalOverdue: Scalars['Int']['output'];
  totalPendingReviews: Scalars['Int']['output'];
};

export type ApprovalTimeline = {
  __typename: 'ApprovalTimeline';
  actualApprovalAt: Maybe<Scalars['DateTime']['output']>;
  assignedReviewerId: Maybe<Scalars['String']['output']>;
  assignedReviewerName: Maybe<Scalars['String']['output']>;
  currentIteration: Scalars['Int']['output'];
  currentStatus: EventStatus;
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
  submissionCount: Scalars['Int']['output'];
  submittedAt: Maybe<Scalars['DateTime']['output']>;
  timelineEvents: Array<TimelineEvent>;
  totalComments: Scalars['Int']['output'];
  totalProcessingTimeHours: Maybe<Scalars['Int']['output']>;
};

export type ApprovalTimelineFilterInput = {
  assignedReviewerId?: InputMaybe<Scalars['String']['input']>;
  hasActiveEscalation?: InputMaybe<Scalars['Boolean']['input']>;
  isOverdue?: InputMaybe<Scalars['Boolean']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  searchQuery?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<EventStatus>;
  submittedAfter?: InputMaybe<Scalars['DateTime']['input']>;
  submittedBefore?: InputMaybe<Scalars['DateTime']['input']>;
};

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

export type ApproveOrganizationInput = {
  commissionRate?: InputMaybe<Scalars['Float']['input']>;
  organizationId: Scalars['ID']['input'];
  payoutSchedule?: InputMaybe<Scalars['String']['input']>;
  reviewNotes?: InputMaybe<Scalars['String']['input']>;
};

export type AssignReviewerInput = {
  eventId: Scalars['ID']['input'];
  internalNotes?: InputMaybe<Scalars['String']['input']>;
  reviewerId: Scalars['String']['input'];
  reviewerName: Scalars['String']['input'];
};

export type AuditLogEntry = {
  __typename: 'AuditLogEntry';
  action: Scalars['String']['output'];
  actorId: Maybe<Scalars['ID']['output']>;
  at: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  metadata: Maybe<Scalars['JSON']['output']>;
  resourceId: Maybe<Scalars['ID']['output']>;
  resourceType: Maybe<Scalars['String']['output']>;
  source: Scalars['String']['output'];
  status: Maybe<Scalars['String']['output']>;
  subjectId: Maybe<Scalars['ID']['output']>;
};

export type AuditLogEntryOffsetPage = {
  __typename: 'AuditLogEntryOffsetPage';
  content: Array<AuditLogEntry>;
  pageInfo: PageInfo;
};

export type AuditLogFilterInput = {
  action?: InputMaybe<Scalars['String']['input']>;
  actorId?: InputMaybe<Scalars['ID']['input']>;
  from?: InputMaybe<Scalars['DateTime']['input']>;
  includeAccountEvents?: InputMaybe<Scalars['Boolean']['input']>;
  resourceId?: InputMaybe<Scalars['ID']['input']>;
  resourceType?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<Scalars['String']['input']>;
  to?: InputMaybe<Scalars['DateTime']['input']>;
};

export type BalanceDirection =
  | 'CREDIT'
  | 'DEBIT';

export type BankAccount = {
  __typename: 'BankAccount';
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

export type Booking = {
  __typename: 'Booking';
  bookingNumber: Scalars['String']['output'];
  buyerId: Scalars['String']['output'];
  confirmedAt: Maybe<Scalars['DateTime']['output']>;
  contactEmail: Maybe<Scalars['String']['output']>;
  contactName: Maybe<Scalars['String']['output']>;
  contactPhone: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  discountAmount: Maybe<Scalars['BigDecimal']['output']>;
  eventDate: Maybe<Scalars['String']['output']>;
  eventId: Scalars['String']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  items: Array<BookingItem>;
  lateRefundStatus: Maybe<Scalars['String']['output']>;
  payment: Maybe<BookingPayment>;
  promoCode: Maybe<Scalars['String']['output']>;
  refundRequests: Array<RefundRequest>;
  refundableAmount: Scalars['BigDecimal']['output'];
  refundedAmount: Scalars['BigDecimal']['output'];
  reservationId: Scalars['String']['output'];
  status: BookingStatus;
  subtotal: Maybe<Scalars['BigDecimal']['output']>;
  ticketCount: Scalars['Int']['output'];
  tickets: Array<Ticket>;
  totalAmount: Scalars['BigDecimal']['output'];
};

export type BookingFilterInput = {
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore?: InputMaybe<Scalars['DateTime']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  search?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<BookingStatus>;
  statuses?: InputMaybe<Array<BookingStatus>>;
};

export type BookingItem = {
  __typename: 'BookingItem';
  quantity: Scalars['Int']['output'];
  subtotal: Scalars['BigDecimal']['output'];
  ticketTierId: Scalars['String']['output'];
  tierName: Scalars['String']['output'];
  unitPrice: Scalars['BigDecimal']['output'];
};

export type BookingOffsetPage = {
  __typename: 'BookingOffsetPage';
  data: Array<Booking>;
  pagination: PaginationInfo;
};

export type BookingPayment = {
  __typename: 'BookingPayment';
  amount: Maybe<Scalars['BigDecimal']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  paidAt: Maybe<Scalars['DateTime']['output']>;
  payerPhone: Maybe<Scalars['String']['output']>;
  provider: Maybe<Scalars['String']['output']>;
  reference: Maybe<Scalars['String']['output']>;
  status: Maybe<Scalars['String']['output']>;
};

export type BookingPendingCounts = {
  __typename: 'BookingPendingCounts';
  payoutRequests: Scalars['Int']['output'];
  refundRequests: Scalars['Int']['output'];
};

export type BookingStatus =
  | 'CANCELLED'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'FAILED'
  | 'PAID_AFTER_EXPIRY_AUTO_REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | 'PENDING'
  | 'REFUNDED';

export type BroadcastInput = {
  endsAt?: InputMaybe<Scalars['DateTime']['input']>;
  message: Scalars['String']['input'];
  segment?: InputMaybe<AnnouncementSegment>;
  severity?: InputMaybe<AlertSeverity>;
  startsAt?: InputMaybe<Scalars['DateTime']['input']>;
  title: Scalars['String']['input'];
};

export type BulkApprovalResponse = {
  __typename: 'BulkApprovalResponse';
  failedCount: Scalars['Int']['output'];
  processedCount: Scalars['Int']['output'];
  results: Array<EventApprovalResult>;
};

export type BulkEventAccessGrantInput = {
  customPermissions?: InputMaybe<Array<Scalars['String']['input']>>;
  expiresAt?: InputMaybe<Scalars['DateTime']['input']>;
  reason?: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
  userId: Scalars['ID']['input'];
};

export type BulkGrantEventAccessInput = {
  eventId: Scalars['ID']['input'];
  grants: Array<GrantEventAccessInput>;
  organizationId: Scalars['ID']['input'];
};

export type BulkInviteInput = {
  invites: Array<InviteTeamMemberInput>;
  organizationId: Scalars['ID']['input'];
};

export type BulkOperationError = {
  __typename: 'BulkOperationError';
  code: Maybe<Scalars['String']['output']>;
  identifier: Maybe<Scalars['String']['output']>;
  index: Scalars['Int']['output'];
  message: Scalars['String']['output'];
};

export type BulkOperationResponse = {
  __typename: 'BulkOperationResponse';
  failedCount: Scalars['Int']['output'];
  processedCount: Scalars['Int']['output'];
};

export type BulkPayoutOperationResponse = {
  __typename: 'BulkPayoutOperationResponse';
  failedCount: Scalars['Int']['output'];
  failedPayoutIds: Array<Scalars['String']['output']>;
  processedCount: Scalars['Int']['output'];
  processedPayouts: Array<PayoutRequest>;
};

export type BulkReminderResponse = {
  __typename: 'BulkReminderResponse';
  failedCount: Scalars['Int']['output'];
  sentCount: Scalars['Int']['output'];
};

export type BusinessAddress = {
  __typename: 'BusinessAddress';
  addressLine1: Maybe<Scalars['String']['output']>;
  addressLine2: Maybe<Scalars['String']['output']>;
  city: Maybe<Scalars['String']['output']>;
  country: Maybe<Scalars['String']['output']>;
  countryCode: Maybe<Scalars['String']['output']>;
  formattedAddress: Maybe<Scalars['String']['output']>;
  postalCode: Maybe<Scalars['String']['output']>;
  province: Maybe<Scalars['String']['output']>;
};

export type BusinessAddressInput = {
  addressLine1?: InputMaybe<Scalars['String']['input']>;
  addressLine2?: InputMaybe<Scalars['String']['input']>;
  city?: InputMaybe<Scalars['String']['input']>;
  country?: InputMaybe<Scalars['String']['input']>;
  countryCode?: InputMaybe<Scalars['String']['input']>;
  postalCode?: InputMaybe<Scalars['String']['input']>;
  province?: InputMaybe<Scalars['String']['input']>;
};

export type BusinessType =
  | 'GOVERNMENT'
  | 'INDIVIDUAL'
  | 'LIMITED_COMPANY'
  | 'NGO'
  | 'PARTNERSHIP'
  | 'SOLE_PROPRIETORSHIP';

export type CatalogPendingCounts = {
  __typename: 'CatalogPendingCounts';
  eventReviews: Scalars['Int']['output'];
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
  awaitingResponse?: InputMaybe<Scalars['Boolean']['input']>;
  deadlineAfter?: InputMaybe<Scalars['DateTime']['input']>;
  deadlineBefore?: InputMaybe<Scalars['DateTime']['input']>;
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  recoveryStatus?: InputMaybe<RecoveryStatus>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  status?: InputMaybe<ChargebackStatus>;
};

export type ChargebackFundSource =
  | 'ORGANIZER_ESCROW'
  | 'ORGANIZER_FUTURE'
  | 'PLATFORM_RESERVE'
  | 'WRITE_OFF';

export type ChargebackOffsetPage = {
  __typename: 'ChargebackOffsetPage';
  data: Array<ChargebackRecord>;
  pagination: PaginationInfo;
};

export type ChargebackReason =
  | 'CANCELLED'
  | 'DUPLICATE'
  | 'FRAUD'
  | 'NOT_AS_DESCRIBED'
  | 'NOT_RECEIVED'
  | 'OTHER';

export type ChargebackRecord = {
  __typename: 'ChargebackRecord';
  chargebackAmount: Scalars['BigDecimal']['output'];
  chargebackFee: Scalars['BigDecimal']['output'];
  chargebackId: Scalars['String']['output'];
  commissionClawbackId: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
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
  unrecoveredAmount: Maybe<Scalars['BigDecimal']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type ChargebackRecoveryAction =
  | 'RECORD_RECOVERY'
  | 'START_RECOVERY';

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

export type ChargebackStatus =
  | 'ACCEPTED'
  | 'DISPUTED'
  | 'LOST'
  | 'RECEIVED'
  | 'UNDER_REVIEW'
  | 'WON';

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

export type CheckIn = {
  __typename: 'CheckIn';
  deviceId: Maybe<Scalars['String']['output']>;
  eventId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  method: ValidationMethod;
  reason: Maybe<Scalars['String']['output']>;
  recordedAt: Scalars['DateTime']['output'];
  scannedAt: Maybe<Scalars['DateTime']['output']>;
  scannedBy: Maybe<Scalars['String']['output']>;
  ticketId: Scalars['ID']['output'];
  ticketNumber: Maybe<Scalars['String']['output']>;
};

export type CheckInConflict = {
  __typename: 'CheckInConflict';
  detectedAt: Scalars['DateTime']['output'];
  deviceId: Maybe<Scalars['String']['output']>;
  eventId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  method: ValidationMethod;
  originalCheckInAt: Maybe<Scalars['DateTime']['output']>;
  presentedCode: Maybe<Scalars['String']['output']>;
  reviewNote: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  scannedAt: Maybe<Scalars['DateTime']['output']>;
  scannedBy: Maybe<Scalars['String']['output']>;
  status: CheckInConflictStatus;
  ticketId: Maybe<Scalars['ID']['output']>;
  type: CheckInConflictType;
};

export type CheckInConflictPage = {
  __typename: 'CheckInConflictPage';
  content: Array<CheckInConflict>;
  page: Scalars['Int']['output'];
  size: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
};

export type CheckInConflictStatus =
  | 'OPEN'
  | 'REVIEWED';

export type CheckInConflictType =
  | 'DUPLICATE_SCAN'
  | 'INVALID_STATE'
  | 'TICKET_NOT_FOUND'
  | 'TICKET_NOT_VALID_FOR_EVENT';

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

export type CheckInOutcome =
  | 'ADMITTED'
  | 'ALREADY_ADMITTED'
  | 'ALREADY_RECORDED'
  | 'INVALID_STATE'
  | 'NOT_FOUND'
  | 'WRONG_EVENT';

export type CheckInSummary = {
  __typename: 'CheckInSummary';
  admitted: Scalars['Int']['output'];
  conflicts: Scalars['Int']['output'];
  eventId: Scalars['ID']['output'];
  issued: Scalars['Int']['output'];
  lastCheckInAt: Maybe<Scalars['DateTime']['output']>;
  manualAdmissions: Scalars['Int']['output'];
  openConflicts: Scalars['Int']['output'];
};

export type CheckoutSettingsInput = {
  collectHolderNames?: InputMaybe<Scalars['Boolean']['input']>;
  extraQuestion?: InputMaybe<Scalars['String']['input']>;
  maxTicketsPerOrder?: InputMaybe<Scalars['Int']['input']>;
};

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

export type CityStats = {
  __typename: 'CityStats';
  cityId: Maybe<Scalars['String']['output']>;
  cityName: Scalars['String']['output'];
  country: Scalars['String']['output'];
  eventCount: Scalars['Int']['output'];
};

export type CommissionFilterInput = {
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore?: InputMaybe<Scalars['DateTime']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  organizationId?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<CommissionStatus>;
  ticketId?: InputMaybe<Scalars['String']['input']>;
};

export type CommissionRecord = {
  __typename: 'CommissionRecord';
  amount: Scalars['BigDecimal']['output'];
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  clawedBackAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  earnedAt: Maybe<Scalars['DateTime']['output']>;
  eventId: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Maybe<Scalars['String']['output']>;
  pendingAt: Maybe<Scalars['DateTime']['output']>;
  rate: Scalars['BigDecimal']['output'];
  refundReason: Maybe<Scalars['String']['output']>;
  refundRequestId: Maybe<Scalars['String']['output']>;
  status: CommissionStatus;
  ticketId: Scalars['String']['output'];
  ticketPrice: Scalars['BigDecimal']['output'];
};

export type CommissionRecordPage = {
  __typename: 'CommissionRecordPage';
  data: Array<CommissionRecord>;
  pagination: PaginationInfo;
  totals: CommissionTotals;
};

export type CommissionStatus =
  | 'CANCELLED'
  | 'CLAWED_BACK'
  | 'EARNED'
  | 'PENDING';

export type CommissionTotals = {
  __typename: 'CommissionTotals';
  cancelled: Scalars['BigDecimal']['output'];
  clawedBack: Scalars['BigDecimal']['output'];
  earned: Scalars['BigDecimal']['output'];
  pending: Scalars['BigDecimal']['output'];
};

export type ConfirmContactAddInput = {
  challengeId: Scalars['ID']['input'];
  code: Scalars['String']['input'];
};

export type ConfirmContactChangeInput = {
  changeId: Scalars['ID']['input'];
  currentContactCode?: InputMaybe<Scalars['String']['input']>;
  newContactCode: Scalars['String']['input'];
};

export type ConfirmContactRemovalInput = {
  challengeId: Scalars['ID']['input'];
  code: Scalars['String']['input'];
};

export type ConfirmOwnershipTransferInput = {
  confirmationCode: Scalars['String']['input'];
  transferToken: Scalars['String']['input'];
};

export type Contact = {
  __typename: 'Contact';
  createdAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  primary: Scalars['Boolean']['output'];
  type: ContactType;
  valueMasked: Scalars['String']['output'];
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
};

export type ContactChangeKind =
  | 'ADD'
  | 'CHANGE'
  | 'PRIMARY'
  | 'REMOVE';

export type ContactChangeRequested = {
  __typename: 'ContactChangeRequested';
  changeId: Scalars['ID']['output'];
  currentContact: ContactCodeSent;
  expiresAt: Scalars['DateTime']['output'];
  newContact: ContactCodeSent;
};

export type ContactChangeResult = {
  __typename: 'ContactChangeResult';
  changeId: Scalars['ID']['output'];
  contacts: Array<Contact>;
  kind: ContactChangeKind;
  status: ContactChangeStatus;
};

export type ContactChangeStatus =
  | 'APPLYING'
  | 'COMPLETED';

export type ContactCodeSent = {
  __typename: 'ContactCodeSent';
  challengeId: Scalars['ID']['output'];
  contactType: ContactType;
  expiresInSeconds: Scalars['Int']['output'];
  maskedContact: Scalars['String']['output'];
  resendAfterSeconds: Scalars['Int']['output'];
};

export type ContactCodeTarget =
  | 'CURRENT'
  | 'NEW';

export type ContactType =
  | 'EMAIL'
  | 'WHATSAPP';

export type Coordinates = {
  __typename: 'Coordinates';
  latitude: Maybe<Scalars['Float']['output']>;
  longitude: Maybe<Scalars['Float']['output']>;
};

export type CreateBankAccountInput = {
  accountHolderName: Scalars['String']['input'];
  accountNumber: Scalars['String']['input'];
  accountType?: InputMaybe<Scalars['String']['input']>;
  bankCode?: InputMaybe<Scalars['String']['input']>;
  bankName: Scalars['String']['input'];
  branchCode?: InputMaybe<Scalars['String']['input']>;
  branchName?: InputMaybe<Scalars['String']['input']>;
  currency: Scalars['String']['input'];
  isDefault?: InputMaybe<Scalars['Boolean']['input']>;
  organizerId: Scalars['String']['input'];
  swiftCode?: InputMaybe<Scalars['String']['input']>;
};

export type CreateChartOfAccountsInput = {
  accountCode: Scalars['String']['input'];
  accountName: Scalars['String']['input'];
  accountType: AccountType;
  currency?: InputMaybe<Scalars['String']['input']>;
  description?: InputMaybe<Scalars['String']['input']>;
  parentAccountCode?: InputMaybe<Scalars['String']['input']>;
  subType?: InputMaybe<AccountSubType>;
};

export type CreateCityInput = {
  code: Scalars['String']['input'];
  country: Scalars['String']['input'];
  name: Scalars['String']['input'];
  provinceId: Scalars['String']['input'];
};

export type CreateCoordinatesInput = {
  latitude: Scalars['Float']['input'];
  longitude: Scalars['Float']['input'];
};

export type CreateEscrowAccountInput = {
  currency: Scalars['String']['input'];
  eventId: Scalars['String']['input'];
  eventTitle?: InputMaybe<Scalars['String']['input']>;
  organizerId: Scalars['String']['input'];
};

export type CreateEventCategoryInput = {
  code: Scalars['String']['input'];
  description?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
};

export type CreateEventInput = {
  accessibility?: InputMaybe<EventAccessibilityInput>;
  additionalInfo?: InputMaybe<Scalars['JSON']['input']>;
  ageRestriction?: InputMaybe<Scalars['String']['input']>;
  bagPolicy?: InputMaybe<Scalars['String']['input']>;
  bannerAltText?: InputMaybe<Scalars['String']['input']>;
  bannerImageUrl?: InputMaybe<Scalars['String']['input']>;
  cancellationPolicy?: InputMaybe<Scalars['String']['input']>;
  categoryId: Scalars['String']['input'];
  checkoutSettings?: InputMaybe<CheckoutSettingsInput>;
  description: Scalars['String']['input'];
  doorsOpenAt?: InputMaybe<Scalars['DateTime']['input']>;
  enableWaitlist?: InputMaybe<Scalars['Boolean']['input']>;
  endDateTime: Scalars['DateTime']['input'];
  eventDateTime: Scalars['DateTime']['input'];
  faqs?: InputMaybe<Array<EventFaqInput>>;
  galleryImages?: InputMaybe<Array<Scalars['String']['input']>>;
  gettingThere?: InputMaybe<Scalars['String']['input']>;
  isFreeEvent?: InputMaybe<Scalars['Boolean']['input']>;
  isVirtual?: InputMaybe<Scalars['Boolean']['input']>;
  location?: InputMaybe<EventLocationInput>;
  parkingInfo?: InputMaybe<Scalars['String']['input']>;
  publishAt?: InputMaybe<Scalars['DateTime']['input']>;
  refundPolicy?: InputMaybe<Scalars['String']['input']>;
  runningOrder?: InputMaybe<Array<RunningOrderItemInput>>;
  tagline?: InputMaybe<Scalars['String']['input']>;
  termsAndConditions?: InputMaybe<Scalars['String']['input']>;
  ticketTiers: Array<CreateTicketTierInput>;
  title: Scalars['String']['input'];
  totalCapacity: Scalars['Int']['input'];
  virtualEventUrl?: InputMaybe<Scalars['String']['input']>;
  waitlistCapacity?: InputMaybe<Scalars['Int']['input']>;
};

export type CreateJournalEntryInput = {
  correlationId: Scalars['String']['input'];
  description: Scalars['String']['input'];
  effectiveDate?: InputMaybe<Scalars['DateTime']['input']>;
  entryDate: Scalars['DateTime']['input'];
  lines: Array<JournalLineInput>;
  metadata?: InputMaybe<Scalars['JSON']['input']>;
  type: JournalEntryType;
};

export type CreatePayoutRequestInput = {
  bankAccountId: Scalars['String']['input'];
  currency: Scalars['String']['input'];
  escrowAccountId: Scalars['String']['input'];
  eventId?: InputMaybe<Scalars['String']['input']>;
  idempotencyKey: Scalars['String']['input'];
  metadata?: InputMaybe<Scalars['JSON']['input']>;
  notes?: InputMaybe<Scalars['String']['input']>;
  organizerId: Scalars['String']['input'];
  payoutMethod: PayoutMethod;
  requestedAmount: Scalars['BigDecimal']['input'];
};

export type CreatePromoCodeInput = {
  applicableTiers?: InputMaybe<Array<Scalars['String']['input']>>;
  code: Scalars['String']['input'];
  discountType: DiscountType;
  discountValue: Scalars['BigDecimal']['input'];
  eventId?: InputMaybe<Scalars['ID']['input']>;
  maxDiscountAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  maxUses?: InputMaybe<Scalars['Int']['input']>;
  minPurchaseAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  organizerId?: InputMaybe<Scalars['ID']['input']>;
  validFrom?: InputMaybe<Scalars['DateTime']['input']>;
  validUntil?: InputMaybe<Scalars['DateTime']['input']>;
};

export type CreateProvinceInput = {
  code: Scalars['String']['input'];
  country: Scalars['String']['input'];
  name: Scalars['String']['input'];
};

export type CreateReferenceDataInput = {
  allowedTransitions?: InputMaybe<Array<Scalars['String']['input']>>;
  code: Scalars['String']['input'];
  description?: InputMaybe<Scalars['String']['input']>;
  displayOrder?: InputMaybe<Scalars['Int']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  metadata?: InputMaybe<Scalars['JSON']['input']>;
  name: Scalars['String']['input'];
  parentCode?: InputMaybe<Scalars['String']['input']>;
  parentType?: InputMaybe<ReferenceType>;
  semantic?: InputMaybe<WorkflowSemantic>;
  type: ReferenceType;
};

export type CreateRefundRequestInput = {
  additionalNotes?: InputMaybe<Scalars['String']['input']>;
  idempotencyKey: Scalars['String']['input'];
  metadata?: InputMaybe<Scalars['JSON']['input']>;
  reason: Scalars['String']['input'];
  requestedById: Scalars['String']['input'];
  ticketId: Scalars['String']['input'];
};

export type CreateTicketTierInput = {
  accessCode?: InputMaybe<Scalars['String']['input']>;
  benefits?: InputMaybe<Array<Scalars['String']['input']>>;
  category?: InputMaybe<TicketCategory>;
  code: Scalars['String']['input'];
  currency: Scalars['String']['input'];
  description?: InputMaybe<Scalars['String']['input']>;
  earlyBirdEndsAt?: InputMaybe<Scalars['DateTime']['input']>;
  earlyBirdPrice?: InputMaybe<Scalars['BigDecimal']['input']>;
  isHidden?: InputMaybe<Scalars['Boolean']['input']>;
  maxPerOrder?: InputMaybe<Scalars['Int']['input']>;
  minPerOrder?: InputMaybe<Scalars['Int']['input']>;
  name: Scalars['String']['input'];
  price: Scalars['BigDecimal']['input'];
  quantity: Scalars['Int']['input'];
  salesEndAt?: InputMaybe<Scalars['DateTime']['input']>;
  salesStartAt?: InputMaybe<Scalars['DateTime']['input']>;
  sortOrder?: InputMaybe<Scalars['Int']['input']>;
};

export type CreateUserInput = {
  email: Scalars['String']['input'];
  firstName: Scalars['String']['input'];
  lastName: Scalars['String']['input'];
  password?: InputMaybe<Scalars['String']['input']>;
  phoneNumber?: InputMaybe<Scalars['String']['input']>;
  role?: InputMaybe<UserType>;
};

export type CursorPaginationInput = {
  after?: InputMaybe<Scalars['String']['input']>;
  before?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
};

export type DaysWaitingBreakdown = {
  __typename: 'DaysWaitingBreakdown';
  count: Scalars['Int']['output'];
  daysWaiting: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
};

export type DevicePlatform =
  | 'ANDROID'
  | 'IOS'
  | 'WEB';

export type DiscountType =
  | 'FIXED_AMOUNT'
  | 'PERCENTAGE';

export type DisputeChargebackInput = {
  additionalDocuments?: InputMaybe<Scalars['String']['input']>;
  customerCommunicationLog?: InputMaybe<Scalars['String']['input']>;
  deliveryConfirmation?: InputMaybe<Scalars['String']['input']>;
  notes: Scalars['String']['input'];
  termsAcceptanceProof?: InputMaybe<Scalars['String']['input']>;
  ticketValidationProof?: InputMaybe<Scalars['String']['input']>;
};

export type DocumentStatus =
  | 'APPROVED'
  | 'EXPIRED'
  | 'PENDING'
  | 'REJECTED';

export type DocumentUploadUrlResponse = {
  __typename: 'DocumentUploadUrlResponse';
  allowedMimeTypes: Array<Scalars['String']['output']>;
  expiresAt: Scalars['DateTime']['output'];
  fileKey: Scalars['String']['output'];
  maxFileSize: Scalars['Long']['output'];
  uploadUrl: Scalars['String']['output'];
};

export type EffectivePermissions = {
  __typename: 'EffectivePermissions';
  eventId: Maybe<Scalars['ID']['output']>;
  organizationId: Maybe<Scalars['ID']['output']>;
  permissions: Array<Scalars['String']['output']>;
  role: Maybe<Scalars['String']['output']>;
  source: Maybe<Scalars['String']['output']>;
  userId: Scalars['ID']['output'];
};

export type EscalationStatus =
  | 'ACKNOWLEDGED'
  | 'EXPIRED'
  | 'PENDING'
  | 'RESOLVED';

export type EscrowAccountFilterInput = {
  currency?: InputMaybe<Scalars['String']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  hasBalance?: InputMaybe<Scalars['Boolean']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<EscrowAccountStatus>;
};

export type EscrowAccountOffsetPage = {
  __typename: 'EscrowAccountOffsetPage';
  data: Array<EventEscrowAccount>;
  pagination: PaginationInfo;
};

export type EscrowAccountStatus =
  | 'ACTIVE'
  | 'CLOSED'
  | 'HOLD'
  | 'PAYOUT_ELIGIBLE'
  | 'SUSPENDED';

export type EscrowJournalVerificationResponse = {
  __typename: 'EscrowJournalVerificationResponse';
  details: Array<Scalars['String']['output']>;
  escrowAccountId: Maybe<Scalars['String']['output']>;
  escrowBalance: Maybe<Scalars['BigDecimal']['output']>;
  eventId: Scalars['String']['output'];
  isConsistent: Scalars['Boolean']['output'];
  journalAccountCode: Maybe<Scalars['String']['output']>;
  journalBalance: Maybe<Scalars['BigDecimal']['output']>;
  status: Maybe<EscrowJournalVerificationStatus>;
  variance: Maybe<Scalars['BigDecimal']['output']>;
};

export type EscrowJournalVerificationStatus =
  | 'BALANCE_MISMATCH'
  | 'CONSISTENT'
  | 'MISSING_JOURNAL_ACCOUNT'
  | 'NOT_FOUND'
  | 'ORPHANED_JOURNAL_ACCOUNT';

export type EscrowTransactionOffsetPage = {
  __typename: 'EscrowTransactionOffsetPage';
  data: Array<StandaloneEscrowTransaction>;
  pagination: PaginationInfo;
};

export type Event = {
  __typename: 'Event';
  accessibility: Maybe<EventAccessibility>;
  additionalInfo: Maybe<Scalars['JSON']['output']>;
  ageRestriction: Maybe<Scalars['String']['output']>;
  approvalBlockers: Array<ApprovalBlocker>;
  approvalDeadline: Maybe<Scalars['DateTime']['output']>;
  approvedAt: Maybe<Scalars['DateTime']['output']>;
  approvedBy: Maybe<Scalars['String']['output']>;
  availableTickets: Scalars['Int']['output'];
  bagPolicy: Maybe<Scalars['String']['output']>;
  bannerAltText: Maybe<Scalars['String']['output']>;
  bannerImageUrl: Maybe<Scalars['String']['output']>;
  cancellationPolicy: Maybe<Scalars['String']['output']>;
  cancellationReason: Maybe<Scalars['String']['output']>;
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  category: Maybe<EventCategory>;
  categoryId: Maybe<Scalars['String']['output']>;
  checkoutSettings: Maybe<EventCheckoutSettings>;
  cityName: Maybe<Scalars['String']['output']>;
  commissionAmount: Maybe<Scalars['BigDecimal']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  description: Scalars['String']['output'];
  doorsOpenAt: Maybe<Scalars['DateTime']['output']>;
  endDateTime: Scalars['DateTime']['output'];
  eventDateTime: Scalars['DateTime']['output'];
  faqs: Maybe<Array<EventFaq>>;
  featured: Scalars['Boolean']['output'];
  galleryImages: Maybe<Array<Scalars['String']['output']>>;
  gettingThere: Maybe<Scalars['String']['output']>;
  grossSales: Maybe<Scalars['BigDecimal']['output']>;
  hasWaitlist: Scalars['Boolean']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  isFreeEvent: Scalars['Boolean']['output'];
  isOverdue: Maybe<Scalars['Boolean']['output']>;
  isRecurring: Scalars['Boolean']['output'];
  isVirtual: Scalars['Boolean']['output'];
  location: Maybe<Location>;
  locationAddress: Maybe<Scalars['String']['output']>;
  locationId: Maybe<Scalars['String']['output']>;
  locationName: Maybe<Scalars['String']['output']>;
  maxTicketPrice: Maybe<Scalars['BigDecimal']['output']>;
  minTicketPrice: Maybe<Scalars['BigDecimal']['output']>;
  netSales: Maybe<Scalars['BigDecimal']['output']>;
  organization: Maybe<Organization>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizer: Maybe<User>;
  organizerId: Scalars['String']['output'];
  organizerName: Scalars['String']['output'];
  parentEventId: Maybe<Scalars['String']['output']>;
  parkingInfo: Maybe<Scalars['String']['output']>;
  publishAt: Maybe<Scalars['DateTime']['output']>;
  publishScheduled: Scalars['Boolean']['output'];
  published: Scalars['Boolean']['output'];
  publishedAt: Maybe<Scalars['DateTime']['output']>;
  recurrencePattern: Maybe<Scalars['String']['output']>;
  refundPolicy: Maybe<Scalars['String']['output']>;
  rejectedAt: Maybe<Scalars['DateTime']['output']>;
  rejectedBy: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  revenue: Scalars['BigDecimal']['output'];
  runningOrder: Maybe<Array<RunningOrderItem>>;
  salesPercentage: Scalars['Float']['output'];
  soldOut: Scalars['Boolean']['output'];
  soldTickets: Scalars['Int']['output'];
  status: EventStatus;
  submittedForApprovalAt: Maybe<Scalars['DateTime']['output']>;
  tagline: Maybe<Scalars['String']['output']>;
  tags: Maybe<Array<Scalars['String']['output']>>;
  termsAndConditions: Maybe<Scalars['String']['output']>;
  thumbnailImageUrl: Maybe<Scalars['String']['output']>;
  ticketTiers: Maybe<Array<TicketTier>>;
  tickets: Array<Ticket>;
  ticketsAvailable: Scalars['Int']['output'];
  ticketsSold: Scalars['Int']['output'];
  title: Scalars['String']['output'];
  totalCapacity: Scalars['Int']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedBy: Maybe<Scalars['String']['output']>;
  version: Maybe<Scalars['Int']['output']>;
  virtualEventPlatform: Maybe<Scalars['String']['output']>;
  virtualEventUrl: Maybe<Scalars['String']['output']>;
  waitlistCapacity: Maybe<Scalars['Int']['output']>;
  waitlistEnabled: Scalars['Boolean']['output'];
};

export type EventAccessGrant = {
  __typename: 'EventAccessGrant';
  createdAt: Scalars['DateTime']['output'];
  customPermissions: Maybe<Array<Scalars['String']['output']>>;
  eventId: Scalars['ID']['output'];
  eventRole: EventRole;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  grantedAt: Scalars['DateTime']['output'];
  grantedBy: Maybe<User>;
  grantedById: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  organization: Maybe<Organization>;
  organizationId: Scalars['ID']['output'];
  reason: Maybe<Scalars['String']['output']>;
  revocationReason: Maybe<Scalars['String']['output']>;
  revokedAt: Maybe<Scalars['DateTime']['output']>;
  revokedBy: Maybe<User>;
  revokedById: Maybe<Scalars['ID']['output']>;
  status: AccessGrantStatus;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  user: Maybe<User>;
  userId: Scalars['ID']['output'];
};

export type EventAccessGrantInput = {
  customPermissions?: InputMaybe<Array<Scalars['String']['input']>>;
  eventId: Scalars['ID']['input'];
  expiresAt?: InputMaybe<Scalars['DateTime']['input']>;
  reason?: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
};

export type EventAccessGrantOffsetPage = {
  __typename: 'EventAccessGrantOffsetPage';
  content: Array<EventAccessGrant>;
  pageInfo: PageInfo;
};

export type EventAccessInput = {
  eventId: Scalars['ID']['input'];
  expiresAt?: InputMaybe<Scalars['DateTime']['input']>;
  role: EventRole;
};

export type EventAccessProposal = {
  __typename: 'EventAccessProposal';
  eventId: Scalars['ID']['output'];
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  role: EventRole;
};

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
  accessibleParking?: InputMaybe<Scalars['Boolean']['input']>;
  accessibleRestrooms?: InputMaybe<Scalars['Boolean']['input']>;
  additionalNotes?: InputMaybe<Scalars['String']['input']>;
  assistanceDogsAllowed?: InputMaybe<Scalars['Boolean']['input']>;
  hearingLoopAvailable?: InputMaybe<Scalars['Boolean']['input']>;
  signLanguageInterpreter?: InputMaybe<Scalars['Boolean']['input']>;
  wheelchairAccessible?: InputMaybe<Scalars['Boolean']['input']>;
  wheelchairSeatsAvailable?: InputMaybe<Scalars['Int']['input']>;
};

export type EventApprovalResult = {
  __typename: 'EventApprovalResult';
  /** Null when this event was approved. Otherwise the ET-PLT-005 registry code explaining why it was not. */
  errorCode: Maybe<Scalars['String']['output']>;
  eventId: Scalars['ID']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  newStatus: Maybe<EventStatus>;
};

export type EventCancellationInput = {
  eventId?: InputMaybe<Scalars['ID']['input']>;
  notifyAttendees?: InputMaybe<Scalars['Boolean']['input']>;
  notifyBuyers?: InputMaybe<Scalars['Boolean']['input']>;
  processRefundsImmediately?: InputMaybe<Scalars['Boolean']['input']>;
  reason: Scalars['String']['input'];
  triggerRefunds?: InputMaybe<Scalars['Boolean']['input']>;
};

export type EventCancellationResponse = {
  __typename: 'EventCancellationResponse';
  event: Maybe<Event>;
  refundSagaInitiated: Scalars['Boolean']['output'];
  sagaId: Maybe<Scalars['ID']['output']>;
  ticketsAffected: Scalars['Int']['output'];
};

export type EventCategory = {
  __typename: 'EventCategory';
  code: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  description: Maybe<Scalars['String']['output']>;
  eventCount: Maybe<Scalars['Int']['output']>;
  id: Scalars['ID']['output'];
  imageUrl: Maybe<Scalars['String']['output']>;
  isActive: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
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

export type EventCheckoutSettings = {
  __typename: 'EventCheckoutSettings';
  collectHolderNames: Scalars['Boolean']['output'];
  extraQuestion: Maybe<Scalars['String']['output']>;
  maxTicketsPerOrder: Maybe<Scalars['Int']['output']>;
};

export type EventConnection = {
  __typename: 'EventConnection';
  edges: Array<EventEdge>;
  pageInfo: PageInfo;
};

export type EventDiscoveryFilterInput = {
  categoryId?: InputMaybe<Scalars['String']['input']>;
  categoryIds?: InputMaybe<Array<Scalars['String']['input']>>;
  cityId?: InputMaybe<Scalars['String']['input']>;
  cityName?: InputMaybe<Scalars['String']['input']>;
  country?: InputMaybe<Scalars['String']['input']>;
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  hasAvailableTickets?: InputMaybe<Scalars['Boolean']['input']>;
  isAccessible?: InputMaybe<Scalars['Boolean']['input']>;
  isFeatured?: InputMaybe<Scalars['Boolean']['input']>;
  isFreeEvent?: InputMaybe<Scalars['Boolean']['input']>;
  isVirtual?: InputMaybe<Scalars['Boolean']['input']>;
  maxPrice?: InputMaybe<Scalars['BigDecimal']['input']>;
  minPrice?: InputMaybe<Scalars['BigDecimal']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  provinceId?: InputMaybe<Scalars['String']['input']>;
  searchQuery?: InputMaybe<Scalars['String']['input']>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
};

export type EventDiscoverySort =
  | 'NEWEST'
  | 'POPULAR'
  | 'PRICE_ASC'
  | 'PRICE_DESC'
  | 'SOONEST';

export type EventEdge = {
  __typename: 'EventEdge';
  cursor: Scalars['String']['output'];
  node: Event;
};

export type EventEscrowAccount = {
  __typename: 'EventEscrowAccount';
  accountNumber: Scalars['String']['output'];
  closedAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  currentBalance: Scalars['BigDecimal']['output'];
  event: Maybe<Event>;
  eventId: Scalars['String']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  lockUntil: Maybe<Scalars['DateTime']['output']>;
  organization: Maybe<Organization>;
  organizerId: Scalars['String']['output'];
  payoutEligibleAt: Maybe<Scalars['DateTime']['output']>;
  pendingWithdrawals: Maybe<Scalars['BigDecimal']['output']>;
  status: EscrowAccountStatus;
  totalCommissions: Scalars['BigDecimal']['output'];
  totalDeposits: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalWithdrawals: Scalars['BigDecimal']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type EventFaq = {
  __typename: 'EventFaq';
  answer: Scalars['String']['output'];
  question: Scalars['String']['output'];
};

export type EventFaqInput = {
  answer: Scalars['String']['input'];
  question: Scalars['String']['input'];
};

export type EventFilterInput = {
  approvedNotPublished?: InputMaybe<Scalars['Boolean']['input']>;
  categoryId?: InputMaybe<Scalars['String']['input']>;
  cityId?: InputMaybe<Scalars['String']['input']>;
  country?: InputMaybe<Scalars['String']['input']>;
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore?: InputMaybe<Scalars['DateTime']['input']>;
  daysSinceApprovalMax?: InputMaybe<Scalars['Int']['input']>;
  daysSinceApprovalMin?: InputMaybe<Scalars['Int']['input']>;
  eventDateAfter?: InputMaybe<Scalars['DateTime']['input']>;
  eventDateBefore?: InputMaybe<Scalars['DateTime']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  overdue?: InputMaybe<Scalars['Boolean']['input']>;
  published?: InputMaybe<Scalars['Boolean']['input']>;
  searchQuery?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<EventStatus>;
  statuses?: InputMaybe<Array<EventStatus>>;
};

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

export type EventLifecycle = {
  __typename: 'EventLifecycle';
  allowedTransitions: Maybe<Array<EventStatus>>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  currentStatus: EventStatus;
  eventId: Scalars['String']['output'];
  lastStatusChange: Maybe<Scalars['DateTime']['output']>;
  statusTransitions: Maybe<Array<StatusTransition>>;
};

export type EventLocationInput = {
  address: Scalars['String']['input'];
  city: Scalars['String']['input'];
  coordinates?: InputMaybe<CreateCoordinatesInput>;
  country: Scalars['String']['input'];
  description?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  postalCode?: InputMaybe<Scalars['String']['input']>;
  province?: InputMaybe<Scalars['String']['input']>;
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

export type EventRecommendation = {
  __typename: 'EventRecommendation';
  basedOnEventId: Maybe<Scalars['ID']['output']>;
  event: Event;
  reason: RecommendationReason;
};

export type EventReminder = {
  __typename: 'EventReminder';
  channels: Maybe<Array<NotificationChannel>>;
  createdAt: Scalars['DateTime']['output'];
  errorMessage: Maybe<Scalars['String']['output']>;
  eventDateTime: Maybe<Scalars['DateTime']['output']>;
  eventId: Scalars['ID']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  reminderAt: Scalars['DateTime']['output'];
  sentAt: Maybe<Scalars['DateTime']['output']>;
  status: ReminderStatus;
  ticketId: Scalars['ID']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
};

export type EventRole =
  | 'CHECK_IN'
  | 'EDITOR'
  | 'EVENT_ADMIN'
  | 'EVENT_OWNER'
  | 'VIEWER';

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

export type EventStatus =
  | 'APPROVED'
  | 'CANCELLED'
  | 'CHANGES_REQUESTED'
  | 'COMPLETED'
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'PUBLISHED'
  | 'REJECTED';

export type EventStatusStats = {
  __typename: 'EventStatusStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: EventStatus;
};

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

export type ExportFormat =
  | 'CSV'
  | 'EXCEL'
  | 'JSON'
  | 'PDF';

export type FileUploadError = {
  __typename: 'FileUploadError';
  code: FileUploadErrorCode;
  field: Scalars['String']['output'];
  message: Scalars['String']['output'];
};

export type FileUploadErrorCode =
  | 'CORRUPTED_FILE'
  | 'FILE_TOO_LARGE'
  | 'INVALID_FILENAME'
  | 'INVALID_FILE_TYPE'
  | 'INVALID_MIME_TYPE'
  | 'MALWARE_DETECTED'
  | 'UPLOAD_FAILED'
  | 'VALIDATION_FAILED';

export type FinancialDataPoint = {
  __typename: 'FinancialDataPoint';
  commissions: Scalars['BigDecimal']['output'];
  payouts: Scalars['BigDecimal']['output'];
  period: Scalars['String']['output'];
  refunds: Scalars['BigDecimal']['output'];
  revenue: Scalars['BigDecimal']['output'];
  ticketsSold: Scalars['Int']['output'];
};

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
  eventId?: InputMaybe<Scalars['ID']['input']>;
  groupBy?: InputMaybe<TimeUnit>;
  organizerId?: InputMaybe<Scalars['ID']['input']>;
  startDate: Scalars['DateTime']['input'];
};

export type GatewaySettlement = {
  __typename: 'GatewaySettlement';
  bankReference: Maybe<Scalars['String']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  entryNumber: Maybe<Scalars['String']['output']>;
  feeAmount: Scalars['BigDecimal']['output'];
  grossAmount: Scalars['BigDecimal']['output'];
  journalEntryId: Scalars['String']['output'];
  netAmount: Scalars['BigDecimal']['output'];
  postedAt: Maybe<Scalars['DateTime']['output']>;
  settlementDate: Maybe<Scalars['DateTime']['output']>;
  settlementId: Scalars['String']['output'];
};

export type GatewaySettlementFilterInput = {
  from?: InputMaybe<Scalars['DateTime']['input']>;
  settlementId?: InputMaybe<Scalars['String']['input']>;
  to?: InputMaybe<Scalars['DateTime']['input']>;
};

export type GatewaySettlementPage = {
  __typename: 'GatewaySettlementPage';
  data: Array<GatewaySettlement>;
  pagination: PaginationInfo;
};

export type GrantEventAccessInput = {
  eventId: Scalars['ID']['input'];
  expiresAt?: InputMaybe<Scalars['DateTime']['input']>;
  organizationId: Scalars['ID']['input'];
  reason?: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
  userId: Scalars['ID']['input'];
};

export type GrowthBucket =
  | 'DAY'
  | 'MONTH'
  | 'WEEK';

export type GrowthPoint = {
  __typename: 'GrowthPoint';
  bucketStart: Scalars['DateTime']['output'];
  cumulative: Scalars['Int']['output'];
  newUsers: Scalars['Int']['output'];
};

export type HolderMessage = {
  __typename: 'HolderMessage';
  body: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  deliveredCount: Scalars['Int']['output'];
  eventId: Scalars['String']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  recipientCount: Scalars['Int']['output'];
  segment: HolderSegment;
  sentBy: Scalars['String']['output'];
  status: HolderMessageStatus;
  subject: Scalars['String']['output'];
  ticketTierId: Maybe<Scalars['String']['output']>;
};

export type HolderMessagePage = {
  __typename: 'HolderMessagePage';
  data: Array<HolderMessage>;
  pagination: PaginationInfo;
};

export type HolderMessageStatus =
  | 'FAILED'
  | 'PARTIAL'
  | 'SENDING'
  | 'SENT';

export type HolderSegment =
  | 'ADMITTED'
  | 'ALL'
  | 'NOT_ADMITTED';

export type IdentityPendingCounts = {
  __typename: 'IdentityPendingCounts';
  documentVerifications: Scalars['Int']['output'];
  organizerApplications: Scalars['Int']['output'];
};

export type InitiateTicketTransferInput = {
  channel: TransferChannel;
  idempotencyKey: Scalars['String']['input'];
  note?: InputMaybe<Scalars['String']['input']>;
  recipient: Scalars['String']['input'];
  ticketId: Scalars['ID']['input'];
};

/**
 * What the holder of an invitation link is shown before they accept.
 *
 * ET-ORG-002 §4 · exactly five fields, and the narrowness is the requirement rather than a
 * convenience. The invitation token is a bearer credential: it arrives by email or WhatsApp and
 * is forwarded, screenshotted and pasted into group chats. Everything reachable by it is
 * reachable by whoever the link reached, so this type answers only what a stranger needs in
 * order to decide whether to accept — which organization, in what role, from whom, until when.
 *
 * Deliberately absent: `invitationToken` (the credential itself), `email` and `phoneNumber`
 * (the invitee's contact details, which a forwarded link would otherwise disclose), `invitedById`,
 * `message`, and the invitation's own id.
 */
export type InvitationPreview = {
  __typename: 'InvitationPreview';
  expiresAt: Scalars['DateTime']['output'];
  inviterDisplayName: Scalars['String']['output'];
  organizationLogoUrl: Maybe<Scalars['String']['output']>;
  organizationName: Scalars['String']['output'];
  proposedRole: OrganizationRole;
};

export type InvitationStatus =
  | 'ACCEPTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'PENDING'
  | 'REVOKED';

export type InviteMemberInput = {
  email?: InputMaybe<Scalars['String']['input']>;
  eventAccessGrants?: InputMaybe<Array<EventAccessGrantInput>>;
  inviteeName?: InputMaybe<Scalars['String']['input']>;
  message?: InputMaybe<Scalars['String']['input']>;
  phoneNumber?: InputMaybe<Scalars['String']['input']>;
  role: OrganizationRole;
};

export type InviteTeamMemberInput = {
  email: Scalars['String']['input'];
  eventAccessGrants?: InputMaybe<Array<EventAccessInput>>;
  inviteeName?: InputMaybe<Scalars['String']['input']>;
  message?: InputMaybe<Scalars['String']['input']>;
  organizationId: Scalars['ID']['input'];
  phoneNumber?: InputMaybe<Scalars['String']['input']>;
  role: OrganizationRole;
};

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
  accountCode?: InputMaybe<Scalars['String']['input']>;
  correlationId?: InputMaybe<Scalars['String']['input']>;
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  status?: InputMaybe<JournalEntryStatus>;
  type?: InputMaybe<JournalEntryType>;
};

export type JournalEntryOffsetPage = {
  __typename: 'JournalEntryOffsetPage';
  data: Array<JournalEntry>;
  pagination: PaginationInfo;
};

export type JournalEntryStatus =
  | 'DRAFT'
  | 'POSTED'
  | 'REVERSED';

export type JournalEntryType =
  | 'ADJUSTMENT'
  | 'REVERSAL'
  | 'STANDARD';

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
  credit?: InputMaybe<Scalars['BigDecimal']['input']>;
  debit?: InputMaybe<Scalars['BigDecimal']['input']>;
  description?: InputMaybe<Scalars['String']['input']>;
  referenceId?: InputMaybe<Scalars['String']['input']>;
  referenceType?: InputMaybe<Scalars['String']['input']>;
};

export type KybStatus =
  | 'CHANGES_REQUESTED'
  | 'IN_PROGRESS'
  | 'NOT_STARTED'
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'VERIFIED';

export type LiveDashboard = {
  __typename: 'LiveDashboard';
  checkInRate: Scalars['Float']['output'];
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

export type MediaAsset = {
  __typename: 'MediaAsset';
  altText: Maybe<Scalars['String']['output']>;
  contentType: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  eventId: Maybe<Scalars['ID']['output']>;
  fileName: Scalars['String']['output'];
  flaggedAt: Maybe<Scalars['DateTime']['output']>;
  flaggedReason: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  moderationLog: Maybe<Array<MediaModerationEntry>>;
  organizationId: Maybe<Scalars['ID']['output']>;
  removedAt: Maybe<Scalars['DateTime']['output']>;
  removedReason: Maybe<Scalars['String']['output']>;
  sizeBytes: Scalars['Int']['output'];
  status: MediaStatus;
  title: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  uploadedBy: Maybe<Scalars['String']['output']>;
  url: Scalars['String']['output'];
};

export type MediaAssetConnection = {
  __typename: 'MediaAssetConnection';
  edges: Array<MediaAssetEdge>;
  pageInfo: PageInfo;
};

export type MediaAssetEdge = {
  __typename: 'MediaAssetEdge';
  cursor: Scalars['String']['output'];
  node: MediaAsset;
};

export type MediaAssetOffsetPage = {
  __typename: 'MediaAssetOffsetPage';
  content: Array<MediaAsset>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

export type MediaFilterInput = {
  eventId?: InputMaybe<Scalars['ID']['input']>;
  search?: InputMaybe<Scalars['String']['input']>;
};

export type MediaModerationEntry = {
  __typename: 'MediaModerationEntry';
  action: Scalars['String']['output'];
  actorId: Maybe<Scalars['String']['output']>;
  at: Maybe<Scalars['DateTime']['output']>;
  reason: Maybe<Scalars['String']['output']>;
};

export type MediaModerationFilterInput = {
  eventId?: InputMaybe<Scalars['ID']['input']>;
  organizationId?: InputMaybe<Scalars['ID']['input']>;
  search?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<MediaStatus>;
};

export type MediaStatus =
  | 'ACTIVE'
  | 'FLAGGED'
  | 'REMOVED';

export type MemberStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'REMOVED'
  | 'SUSPENDED';

export type MessageTicketHoldersInput = {
  body: Scalars['String']['input'];
  segment?: InputMaybe<HolderSegment>;
  subject: Scalars['String']['input'];
  ticketTierId?: InputMaybe<Scalars['String']['input']>;
};

export type MobileMoneyAccount = {
  __typename: 'MobileMoneyAccount';
  accountHolderName: Maybe<Scalars['String']['output']>;
  maskedPhoneNumber: Maybe<Scalars['String']['output']>;
  phoneNumber: Maybe<Scalars['String']['output']>;
  provider: Maybe<MobileMoneyProvider>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  status: PayoutAccountStatus;
  suspended: Scalars['Boolean']['output'];
  suspendedReason: Maybe<Scalars['String']['output']>;
  testDepositSentAt: Maybe<Scalars['DateTime']['output']>;
  verificationAttemptsLeft: Scalars['Int']['output'];
  verified: Scalars['Boolean']['output'];
};

export type MobileMoneyProvider =
  | 'AIRTEL'
  | 'MTN'
  | 'ZAMTEL';

export type Mutation = {
  __typename: 'Mutation';
  acceptChargeback: ChargebackRecord;
  acceptInvitation: Maybe<OrganizationMember>;
  acceptOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  acceptTicketTransfer: Ticket;
  acknowledgeAlert: SystemAlert;
  acknowledgeEscalation: ApprovalEscalation;
  activateEventCategory: EventCategory;
  activatePromoCode: PromoCode;
  activateTicketTier: TicketTier;
  activateUser: Scalars['Boolean']['output'];
  addApprovalComment: ApprovalTimeline;
  addPaymentAttemptNote: PaymentAttempt;
  /**
   * Add a role to a user.
   * A user can have multiple roles (e.g., CUSTOMER + ORGANIZER).
   * The CUSTOMER role is the base role that all users have.
   */
  addUserRole: User;
  adminUpdateTicket: Ticket;
  /**
   * Apply to become an organizer.
   * Creates a new organization in DRAFT status.
   * User must fill in business details and submit for approval.
   */
  applyToBeOrganizer: Organization;
  approveEvent: Event;
  /**
   * Approve an organization application.
   * Changes status from PENDING_REVIEW to APPROVED.
   * Organization can now publish events.
   */
  approveOrganization: Maybe<Organization>;
  approvePayoutRequest: PayoutRequest;
  approveRefundRequest: RefundRequest;
  approveVerificationDocument: Maybe<VerificationDocument>;
  assignEventReviewer: ApprovalTimeline;
  broadcastNotification: SystemAnnouncement;
  bulkApproveRefunds: BulkOperationResponse;
  bulkCancelTickets: BulkOperationResponse;
  bulkGrantEventAccess: Maybe<Array<EventAccessGrant>>;
  bulkInviteTeamMembers: Maybe<Array<TeamInvitation>>;
  bulkMarkPayoutsForReview: BulkPayoutOperationResponse;
  bulkRetryFailedPayouts: BulkPayoutOperationResponse;
  cancelAccountDeletion: User;
  cancelAnnouncement: SystemAnnouncement;
  cancelContactChange: Scalars['Boolean']['output'];
  cancelEvent: EventCancellationResponse;
  cancelEventReminder: Scalars['Boolean']['output'];
  cancelOrganizationDeletion: Maybe<Organization>;
  cancelOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  cancelPayoutRequest: PayoutRequest;
  cancelRefundRequest: RefundRequest;
  cancelReservation: Scalars['Boolean']['output'];
  cancelScheduledPublish: Event;
  cancelTicket: Ticket;
  cancelTicketTransfer: TicketTransfer;
  closeEscrowAccount: EventEscrowAccount;
  completeEvent: Event;
  completePayoutRequest: PayoutRequest;
  completeReconciliation: ReconciliationRun;
  confirmBankVerification: BankAccount;
  confirmContactAdd: ContactChangeResult;
  confirmContactChange: ContactChangeResult;
  confirmContactRemoval: ContactChangeResult;
  confirmRecoveryAction: RecoveryProposal;
  createAdminRefundRequest: RefundRequest;
  createBankAccount: BankAccount;
  createChartOfAccountsEntry: ChartOfAccountsEntry;
  createCity: City;
  createEscrowAccount: EventEscrowAccount;
  createEvent: Event;
  createEventCategory: EventCategory;
  createJournalEntry: JournalEntry;
  createPayoutRequest: PayoutRequest;
  createPromoCode: PromoCode;
  createProvince: Province;
  createReferenceData: ReferenceData;
  createTicketTier: TicketTier;
  createUser: User;
  createUserRefundRequest: RefundRequest;
  deactivateChartOfAccountsEntry: ChartOfAccountsEntry;
  deactivateEventCategory: EventCategory;
  deactivatePromoCode: PromoCode;
  deactivateTicketTier: TicketTier;
  deactivateUser: Scalars['Boolean']['output'];
  declineInvitation: Scalars['Boolean']['output'];
  declineOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  declineTicketTransfer: TicketTransfer;
  deleteBankAccount: Scalars['ID']['output'];
  deleteCity: Scalars['ID']['output'];
  deleteEvent: Scalars['ID']['output'];
  deleteEventCategory: Scalars['ID']['output'];
  deleteMedia: Scalars['ID']['output'];
  deleteNotification: Scalars['Boolean']['output'];
  deletePromoCode: Scalars['ID']['output'];
  deleteProvince: Scalars['ID']['output'];
  deleteReferenceData: Scalars['ID']['output'];
  deleteStockImage: Scalars['ID']['output'];
  deleteTicketTier: Scalars['ID']['output'];
  deleteUser: User;
  deleteVerificationDocument: Scalars['Boolean']['output'];
  disputeChargeback: ChargebackRecord;
  duplicateEvent: Event;
  escalatePayoutRequest: PayoutRequest;
  failReconciliation: ReconciliationRun;
  featureEvent: Event;
  flagMedia: MediaAsset;
  forceCompletePaymentAttempts: RecoveryProposal;
  forceExpireReservation: Scalars['Boolean']['output'];
  /**
   * Get or create organization for the current user.
   * If user has no organization, creates one in DRAFT status.
   */
  getOrCreateMyOrganization: Organization;
  grantEventAccess: Maybe<EventAccessGrant>;
  holdPayoutRequest: PayoutRequest;
  initiateOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  initiateTicketTransfer: TicketTransfer;
  inviteTeamMember: Maybe<TeamInvitation>;
  leaveOrganization: Scalars['Boolean']['output'];
  lockEscrowAccount: EventEscrowAccount;
  lockUser: Scalars['Boolean']['output'];
  logout: Scalars['Boolean']['output'];
  markAllNotificationsRead: Scalars['Int']['output'];
  markNotificationRead: Maybe<Notification>;
  markPayoutEligible: EventEscrowAccount;
  markPayoutForReview: PayoutRequest;
  messageTicketHolders: HolderMessage;
  overrideEventBanner: Event;
  payReservation: PaymentInitiationResponse;
  postJournalEntry: JournalEntry;
  processPayoutRequest: PayoutRequest;
  processRefundRequest: RefundRequest;
  proposeRecoveryAction: RecoveryProposal;
  publishEvent: Event;
  reactivateMember: Maybe<OrganizationMember>;
  receiveChargeback: ChargebackRecord;
  recordChargebackOutcome: ChargebackRecord;
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
  recordGatewaySettlement: JournalEntry;
  refundTicket: Ticket;
  regenerateTicketQrCode: Ticket;
  registerDevice: Maybe<UserDevice>;
  reinstateBankAccount: Maybe<Organization>;
  rejectBankAccount: Maybe<Organization>;
  rejectEvent: Event;
  /**
   * Reject an organization application.
   * Changes status to REJECTED.
   */
  rejectOrganization: Maybe<Organization>;
  rejectPayoutAccount: Maybe<Organization>;
  rejectPayoutRequest: PayoutRequest;
  rejectRefundRequest: RefundRequest;
  rejectVerificationDocument: Maybe<VerificationDocument>;
  releasePayoutHold: PayoutRequest;
  removeMedia: MediaAsset;
  removeMember: Scalars['Boolean']['output'];
  /**
   * Remove a role from a user.
   * Note: The CUSTOMER role cannot be removed as it is the base role.
   */
  removeUserRole: User;
  reorderTicketTiers: Array<TicketTier>;
  requestAccountDeletion: User;
  requestContactAdd: ContactCodeSent;
  requestContactChange: ContactChangeRequested;
  requestContactRemoval: ContactCodeSent;
  requestDocumentUploadUrl: DocumentUploadUrlResponse;
  requestEventChanges: Event;
  /**
   * Request changes to an organization application.
   * Changes status from PENDING_REVIEW to CHANGES_REQUESTED.
   * User can update details and resubmit.
   */
  requestOrganizationChanges: Maybe<Organization>;
  requestOrganizationDeletion: Maybe<Organization>;
  requestOwnershipTransferCode: Scalars['Boolean']['output'];
  requestPrimaryContact: ContactCodeSent;
  rescheduleEvent: Event;
  resendContactCode: ContactCodeSent;
  resendInvitation: Maybe<TeamInvitation>;
  resendTicket: ResendTicketResult;
  reserveTickets: TicketReservation;
  resolveEscalation: ApprovalEscalation;
  resolvePayoutIssue: PayoutRequest;
  resolveReconciliationItem: ReconciliationRun;
  restoreMedia: MediaAsset;
  resumePaymentAttempt: PaymentAttempt;
  resumePayoutRequest: PayoutRequest;
  retryPaymentAttempts: Array<PaymentRecoveryOutcome>;
  retryPayoutRequest: PayoutRequest;
  reverseJournalEntry: JournalEntry;
  reviewConflict: CheckInConflict;
  revokeEventAccess: Maybe<EventAccessGrant>;
  revokeInvitation: Maybe<TeamInvitation>;
  revokeSession: Scalars['Boolean']['output'];
  seedChartOfAccounts: Scalars['Boolean']['output'];
  sendBulkEventPublishReminders: BulkReminderResponse;
  sendEventPublishReminder: Event;
  /**
   * Set bank account for payouts.
   * AUTHORIZATION: Only organization OWNER can set bank account.
   * SECURITY:
   * - Account numbers are encrypted at rest (AES-256-GCM)
   * - Input is validated (10-16 digits for Zambian banks)
   * - SWIFT codes validated (8 or 11 alphanumeric characters)
   * - Account holder name validated (letters, spaces, hyphens, apostrophes only)
   * - All changes audit logged (with masked account numbers)
   * COMPLIANCE: PCI-DSS compliant encryption and audit trail.
   */
  setBankAccount: Maybe<Organization>;
  setDefaultBankAccount: BankAccount;
  setEventReminder: Maybe<EventReminder>;
  /**
   * Set mobile money account for payouts.
   * AUTHORIZATION: Only organization OWNER can set mobile money account.
   * SECURITY:
   * - Phone numbers validated in E.164 format (+260XXXXXXXXX)
   * - Validated against Zambian mobile prefixes (MTN, Airtel, Zamtel)
   * - Phone numbers masked for display (show prefix + last 4 digits)
   * - All changes audit logged
   */
  setMobileMoneyAccount: Maybe<Organization>;
  setOrganizationCommissionRate: Maybe<Organization>;
  setPaymentAttemptReviewStatus: PaymentAttempt;
  setPrimaryContact: ContactChangeResult;
  setReferenceDataActive: ReferenceData;
  /**
   * Set all roles for a user (replaces existing roles).
   * The roles set must include CUSTOMER.
   */
  setUserRoles: User;
  startBankVerification: BankAccount;
  startChargebackReview: ChargebackRecord;
  startReconciliation: ReconciliationRun;
  submitEventForApproval: Event;
  /**
   * Submit organization application for admin review.
   * Changes status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW.
   */
  submitOrganizationForReview: Maybe<Organization>;
  suspendBankAccount: Maybe<Organization>;
  suspendMember: Maybe<OrganizationMember>;
  suspendOrganization: Maybe<Organization>;
  suspendUser: User;
  transferBetweenPlatformAccounts: PlatformTransferResult;
  triggerManualEscalation: ApprovalEscalation;
  unassignEventReviewer: ApprovalTimeline;
  unlockEscrowAccount: EventEscrowAccount;
  unlockTierWithAccessCode: TicketTier;
  unlockUser: Scalars['Boolean']['output'];
  unpublishEvent: Event;
  unregisterDevice: Scalars['Boolean']['output'];
  unsuspendOrganization: Maybe<Organization>;
  unsuspendUser: User;
  updateBankAccount: BankAccount;
  updateChargebackRecovery: ChargebackRecord;
  updateChartOfAccountsEntry: ChartOfAccountsEntry;
  updateCity: City;
  updateEscrowAccountStatus: EventEscrowAccount;
  updateEvent: Event;
  updateEventAccess: Maybe<EventAccessGrant>;
  updateEventAccessibility: Event;
  updateEventCapacity: Event;
  updateEventCategory: EventCategory;
  updateMedia: MediaAsset;
  updateMemberRole: Maybe<OrganizationMember>;
  updateMyProfile: User;
  updateNotificationPreferences: Maybe<NotificationPreferences>;
  updateOrganization: Maybe<Organization>;
  /**
   * Update organization application details.
   * Only allowed when status is DRAFT or CHANGES_REQUESTED.
   */
  updateOrganizationApplication: Maybe<Organization>;
  updateOrganizationSettings: Maybe<Organization>;
  updateOrganizationStatus: Maybe<Organization>;
  /**
   * Update payout configuration (method, schedule, minimum amount).
   * AUTHORIZATION: Only organization OWNER can modify payout config.
   * SECURITY: All changes are audit logged with IP address and user agent.
   */
  updatePayoutConfig: Maybe<Organization>;
  updatePlatformConfiguration: PlatformConfiguration;
  updatePromoCode: PromoCode;
  updateProvince: Province;
  updateReferenceData: ReferenceData;
  updateStockImage: StockImage;
  updateTicketTier: TicketTier;
  updateUser: User;
  /**
   * Upgrade an individual organization to a business organization.
   * Requires the organization to be owned by the current user.
   */
  upgradeToBusinessOrganization: Maybe<Organization>;
  uploadMedia: MediaAsset;
  uploadScans: Array<ValidationResult>;
  uploadStockImage: StockImage;
  uploadVerificationDocument: VerificationDocument;
  validateTicket: ValidationResult;
  verifyBankAccount: BankAccount;
  verifyEscrowJournalConsistency: EscrowJournalVerificationResponse;
  /**
   * Verify payout account (admin only).
   * AUTHORIZATION: Only ADMIN or FINANCE role can verify accounts.
   * SECURITY:
   * - Verification status change is audit logged
   * - Only verified accounts can receive payouts
   * - Verification can be revoked if fraud detected
   */
  verifyPayoutAccount: Maybe<Organization>;
  withdrawRecoveryProposal: RecoveryProposal;
};


export type MutationAcceptChargebackArgs = {
  id: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationAcceptInvitationArgs = {
  token: Scalars['String']['input'];
};


export type MutationAcceptOwnershipTransferArgs = {
  confirmationCode: Scalars['String']['input'];
  token: Scalars['String']['input'];
};


export type MutationAcceptTicketTransferArgs = {
  transferId: Scalars['ID']['input'];
};


export type MutationAcknowledgeAlertArgs = {
  id: Scalars['ID']['input'];
};


export type MutationAcknowledgeEscalationArgs = {
  escalationId: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
};


export type MutationActivateEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationActivatePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


export type MutationActivateTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


export type MutationActivateUserArgs = {
  id: Scalars['ID']['input'];
};


export type MutationAddApprovalCommentArgs = {
  comment: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
  isInternal?: InputMaybe<Scalars['Boolean']['input']>;
};


export type MutationAddPaymentAttemptNoteArgs = {
  depositId: Scalars['String']['input'];
  note: Scalars['String']['input'];
};


export type MutationAddUserRoleArgs = {
  role: UserType;
  userId: Scalars['ID']['input'];
};


export type MutationAdminUpdateTicketArgs = {
  input: AdminTicketUpdateInput;
  ticketId: Scalars['ID']['input'];
};


export type MutationApplyToBeOrganizerArgs = {
  input: OrganizationApplicationInput;
};


export type MutationApproveEventArgs = {
  comments: InputMaybe<Scalars['String']['input']>;
  eventId: Scalars['ID']['input'];
};


export type MutationApproveOrganizationArgs = {
  commissionRate: InputMaybe<Scalars['Float']['input']>;
  id: Scalars['ID']['input'];
};


export type MutationApprovePayoutRequestArgs = {
  idempotencyKey: Scalars['String']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationApproveRefundRequestArgs = {
  idempotencyKey: Scalars['String']['input'];
  refundRequestId: Scalars['ID']['input'];
  reviewComments: InputMaybe<Scalars['String']['input']>;
};


export type MutationApproveVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
};


export type MutationAssignEventReviewerArgs = {
  input: AssignReviewerInput;
};


export type MutationBroadcastNotificationArgs = {
  input: BroadcastInput;
};


export type MutationBulkApproveRefundsArgs = {
  refundRequestIds: Array<Scalars['ID']['input']>;
};


export type MutationBulkCancelTicketsArgs = {
  reason: Scalars['String']['input'];
  ticketIds: Array<Scalars['ID']['input']>;
};


export type MutationBulkGrantEventAccessArgs = {
  eventId: Scalars['ID']['input'];
  grants: Array<BulkEventAccessGrantInput>;
  organizationId: Scalars['ID']['input'];
};


export type MutationBulkInviteTeamMembersArgs = {
  invitations: Array<InviteMemberInput>;
  organizationId: Scalars['ID']['input'];
};


export type MutationBulkMarkPayoutsForReviewArgs = {
  issueType: PayoutIssueType;
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestIds: Array<Scalars['ID']['input']>;
};


export type MutationBulkRetryFailedPayoutsArgs = {
  payoutRequestIds: Array<Scalars['ID']['input']>;
};


export type MutationCancelAnnouncementArgs = {
  id: Scalars['ID']['input'];
};


export type MutationCancelContactChangeArgs = {
  changeId: Scalars['ID']['input'];
};


export type MutationCancelEventArgs = {
  id: Scalars['ID']['input'];
  input: EventCancellationInput;
};


export type MutationCancelEventReminderArgs = {
  reminderId: Scalars['ID']['input'];
};


export type MutationCancelOrganizationDeletionArgs = {
  organizationId: Scalars['ID']['input'];
};


export type MutationCancelOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


export type MutationCancelPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationCancelRefundRequestArgs = {
  reason: Scalars['String']['input'];
  refundRequestId: Scalars['ID']['input'];
};


export type MutationCancelReservationArgs = {
  reservationId: Scalars['ID']['input'];
};


export type MutationCancelScheduledPublishArgs = {
  eventId: Scalars['ID']['input'];
};


export type MutationCancelTicketArgs = {
  reason: Scalars['String']['input'];
  ticketNumber: Scalars['String']['input'];
};


export type MutationCancelTicketTransferArgs = {
  transferId: Scalars['ID']['input'];
};


export type MutationCloseEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationCompleteEventArgs = {
  id: Scalars['ID']['input'];
};


export type MutationCompletePayoutRequestArgs = {
  bankReference: Scalars['String']['input'];
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationCompleteReconciliationArgs = {
  notes: InputMaybe<Scalars['String']['input']>;
  runId: Scalars['ID']['input'];
};


export type MutationConfirmBankVerificationArgs = {
  amount: Scalars['BigDecimal']['input'];
  id: Scalars['ID']['input'];
};


export type MutationConfirmContactAddArgs = {
  input: ConfirmContactAddInput;
};


export type MutationConfirmContactChangeArgs = {
  input: ConfirmContactChangeInput;
};


export type MutationConfirmContactRemovalArgs = {
  input: ConfirmContactRemovalInput;
};


export type MutationConfirmRecoveryActionArgs = {
  proposalId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationCreateAdminRefundRequestArgs = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  bypassApproval: InputMaybe<Scalars['Boolean']['input']>;
  idempotencyKey: Scalars['String']['input'];
  reason: Scalars['String']['input'];
  ticketId: Scalars['ID']['input'];
};


export type MutationCreateBankAccountArgs = {
  input: CreateBankAccountInput;
};


export type MutationCreateChartOfAccountsEntryArgs = {
  input: CreateChartOfAccountsInput;
};


export type MutationCreateCityArgs = {
  input: CreateCityInput;
};


export type MutationCreateEscrowAccountArgs = {
  input: CreateEscrowAccountInput;
};


export type MutationCreateEventArgs = {
  input: CreateEventInput;
};


export type MutationCreateEventCategoryArgs = {
  input: CreateEventCategoryInput;
};


export type MutationCreateJournalEntryArgs = {
  input: CreateJournalEntryInput;
};


export type MutationCreatePayoutRequestArgs = {
  input: CreatePayoutRequestInput;
};


export type MutationCreatePromoCodeArgs = {
  input: CreatePromoCodeInput;
};


export type MutationCreateProvinceArgs = {
  input: CreateProvinceInput;
};


export type MutationCreateReferenceDataArgs = {
  input: CreateReferenceDataInput;
};


export type MutationCreateTicketTierArgs = {
  eventId: Scalars['ID']['input'];
  input: CreateTicketTierInput;
};


export type MutationCreateUserArgs = {
  input: CreateUserInput;
};


export type MutationCreateUserRefundRequestArgs = {
  input: CreateRefundRequestInput;
};


export type MutationDeactivateChartOfAccountsEntryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeactivateEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeactivatePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeactivateTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


export type MutationDeactivateUserArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeclineInvitationArgs = {
  token: Scalars['String']['input'];
};


export type MutationDeclineOwnershipTransferArgs = {
  token: Scalars['String']['input'];
};


export type MutationDeclineTicketTransferArgs = {
  transferId: Scalars['ID']['input'];
};


export type MutationDeleteBankAccountArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteCityArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteEventArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteMediaArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteNotificationArgs = {
  notificationId: Scalars['ID']['input'];
};


export type MutationDeletePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteProvinceArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteReferenceDataArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteStockImageArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


export type MutationDeleteUserArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
};


export type MutationDisputeChargebackArgs = {
  id: Scalars['ID']['input'];
  input: DisputeChargebackInput;
};


export type MutationDuplicateEventArgs = {
  eventId: Scalars['ID']['input'];
  newTitle: Scalars['String']['input'];
};


export type MutationEscalatePayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationFailReconciliationArgs = {
  reason: Scalars['String']['input'];
  runId: Scalars['ID']['input'];
};


export type MutationFeatureEventArgs = {
  eventId: Scalars['ID']['input'];
  featured: Scalars['Boolean']['input'];
};


export type MutationFlagMediaArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationForceCompletePaymentAttemptsArgs = {
  depositIds: Array<Scalars['String']['input']>;
  reason: Scalars['String']['input'];
};


export type MutationForceExpireReservationArgs = {
  reservationId: Scalars['ID']['input'];
};


export type MutationGrantEventAccessArgs = {
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  eventId: Scalars['ID']['input'];
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
  role: EventRole;
  userId: Scalars['ID']['input'];
};


export type MutationHoldPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationInitiateOwnershipTransferArgs = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationInitiateTicketTransferArgs = {
  input: InitiateTicketTransferInput;
};


export type MutationInviteTeamMemberArgs = {
  input: InviteMemberInput;
  organizationId: Scalars['ID']['input'];
};


export type MutationLeaveOrganizationArgs = {
  organizationId: Scalars['ID']['input'];
};


export type MutationLockEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  lockUntil: Scalars['DateTime']['input'];
  reason: Scalars['String']['input'];
};


export type MutationLockUserArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationMarkNotificationReadArgs = {
  notificationId: Scalars['ID']['input'];
};


export type MutationMarkPayoutEligibleArgs = {
  accountId: Scalars['ID']['input'];
};


export type MutationMarkPayoutForReviewArgs = {
  issueType: PayoutIssueType;
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationMessageTicketHoldersArgs = {
  eventId: Scalars['ID']['input'];
  input: MessageTicketHoldersInput;
};


export type MutationOverrideEventBannerArgs = {
  eventId: Scalars['ID']['input'];
  mediaId: InputMaybe<Scalars['ID']['input']>;
  reason: Scalars['String']['input'];
};


export type MutationPayReservationArgs = {
  input: PayReservationInput;
};


export type MutationPostJournalEntryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationProcessPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationProcessRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
};


export type MutationProposeRecoveryActionArgs = {
  input: ProposeRecoveryActionInput;
};


export type MutationPublishEventArgs = {
  id: Scalars['ID']['input'];
};


export type MutationReactivateMemberArgs = {
  memberId: Scalars['ID']['input'];
};


export type MutationReceiveChargebackArgs = {
  input: ReceiveChargebackInput;
};


export type MutationRecordChargebackOutcomeArgs = {
  id: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  won: Scalars['Boolean']['input'];
};


export type MutationRecordGatewaySettlementArgs = {
  input: RecordGatewaySettlementInput;
};


export type MutationRefundTicketArgs = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  reason: Scalars['String']['input'];
  ticketNumber: Scalars['String']['input'];
};


export type MutationRegenerateTicketQrCodeArgs = {
  ticketId: Scalars['ID']['input'];
};


export type MutationRegisterDeviceArgs = {
  input: RegisterDeviceInput;
};


export type MutationReinstateBankAccountArgs = {
  organizationId: Scalars['ID']['input'];
};


export type MutationRejectBankAccountArgs = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationRejectEventArgs = {
  comments: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


export type MutationRejectOrganizationArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationRejectPayoutAccountArgs = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationRejectPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
  rejectionReason: Scalars['String']['input'];
};


export type MutationRejectRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
  rejectionReason: Scalars['String']['input'];
};


export type MutationRejectVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationReleasePayoutHoldArgs = {
  note: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationRemoveMediaArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationRemoveMemberArgs = {
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationRemoveUserRoleArgs = {
  role: UserType;
  userId: Scalars['ID']['input'];
};


export type MutationReorderTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
  tierIds: Array<Scalars['ID']['input']>;
};


export type MutationRequestAccountDeletionArgs = {
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationRequestContactAddArgs = {
  input: RequestContactAddInput;
};


export type MutationRequestContactChangeArgs = {
  input: RequestContactChangeInput;
};


export type MutationRequestContactRemovalArgs = {
  contactId: Scalars['ID']['input'];
};


export type MutationRequestDocumentUploadUrlArgs = {
  input: RequestUploadUrlInput;
};


export type MutationRequestEventChangesArgs = {
  comments: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


export type MutationRequestOrganizationChangesArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationRequestOrganizationDeletionArgs = {
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationRequestOwnershipTransferCodeArgs = {
  token: Scalars['String']['input'];
};


export type MutationRequestPrimaryContactArgs = {
  contactId: Scalars['ID']['input'];
};


export type MutationRescheduleEventArgs = {
  input: RescheduleEventInput;
};


export type MutationResendContactCodeArgs = {
  input: ResendContactCodeInput;
};


export type MutationResendInvitationArgs = {
  invitationId: Scalars['ID']['input'];
};


export type MutationResendTicketArgs = {
  ticketId: Scalars['ID']['input'];
};


export type MutationReserveTicketsArgs = {
  input: ReserveTicketsInput;
};


export type MutationResolveEscalationArgs = {
  input: ResolveEscalationInput;
};


export type MutationResolvePayoutIssueArgs = {
  notes: Scalars['String']['input'];
  payoutRequestId: Scalars['ID']['input'];
  resolutionType: PayoutResolutionType;
};


export type MutationResolveReconciliationItemArgs = {
  input: ResolveReconciliationItemInput;
  runId: Scalars['ID']['input'];
};


export type MutationRestoreMediaArgs = {
  id: Scalars['ID']['input'];
};


export type MutationResumePaymentAttemptArgs = {
  depositId: Scalars['String']['input'];
};


export type MutationResumePayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationRetryPaymentAttemptsArgs = {
  depositIds: Array<Scalars['String']['input']>;
};


export type MutationRetryPayoutRequestArgs = {
  idempotencyKey: Scalars['String']['input'];
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationReverseJournalEntryArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationReviewConflictArgs = {
  id: Scalars['ID']['input'];
  note: Scalars['String']['input'];
};


export type MutationRevokeEventAccessArgs = {
  accessId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationRevokeInvitationArgs = {
  invitationId: Scalars['ID']['input'];
};


export type MutationRevokeSessionArgs = {
  sessionId: Scalars['ID']['input'];
};


export type MutationSendBulkEventPublishRemindersArgs = {
  eventIds: Array<Scalars['ID']['input']>;
  triggeredBy: Scalars['String']['input'];
};


export type MutationSendEventPublishReminderArgs = {
  eventId: Scalars['ID']['input'];
  triggeredBy: Scalars['String']['input'];
};


export type MutationSetBankAccountArgs = {
  input: SetBankAccountInput;
  organizationId: Scalars['ID']['input'];
};


export type MutationSetDefaultBankAccountArgs = {
  id: Scalars['ID']['input'];
};


export type MutationSetEventReminderArgs = {
  input: SetEventReminderInput;
};


export type MutationSetMobileMoneyAccountArgs = {
  input: SetMobileMoneyAccountInput;
  organizationId: Scalars['ID']['input'];
};


export type MutationSetOrganizationCommissionRateArgs = {
  organizationId: Scalars['ID']['input'];
  rate: Scalars['Float']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationSetPaymentAttemptReviewStatusArgs = {
  depositId: Scalars['String']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  reviewStatus: Scalars['String']['input'];
};


export type MutationSetPrimaryContactArgs = {
  input: SetPrimaryContactInput;
};


export type MutationSetReferenceDataActiveArgs = {
  active: Scalars['Boolean']['input'];
  id: Scalars['ID']['input'];
};


export type MutationSetUserRolesArgs = {
  roles: Array<UserType>;
  userId: Scalars['ID']['input'];
};


export type MutationStartBankVerificationArgs = {
  id: Scalars['ID']['input'];
};


export type MutationStartChargebackReviewArgs = {
  id: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
};


export type MutationStartReconciliationArgs = {
  input: StartReconciliationInput;
};


export type MutationSubmitEventForApprovalArgs = {
  eventId: Scalars['ID']['input'];
};


export type MutationSubmitOrganizationForReviewArgs = {
  id: Scalars['ID']['input'];
};


export type MutationSuspendBankAccountArgs = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationSuspendMemberArgs = {
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationSuspendOrganizationArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationSuspendUserArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationSyncUserFromKeycloakArgs = {
  userId: Scalars['ID']['input'];
};


export type MutationTransferBetweenPlatformAccountsArgs = {
  input: PlatformTransferInput;
};


export type MutationTriggerManualEscalationArgs = {
  escalateTo: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationUnassignEventReviewerArgs = {
  eventId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationUnlockEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationUnlockTierWithAccessCodeArgs = {
  accessCode: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


export type MutationUnlockUserArgs = {
  id: Scalars['ID']['input'];
};


export type MutationUnpublishEventArgs = {
  id: Scalars['ID']['input'];
};


export type MutationUnregisterDeviceArgs = {
  deviceId: Scalars['ID']['input'];
};


export type MutationUnsuspendOrganizationArgs = {
  id: Scalars['ID']['input'];
};


export type MutationUnsuspendUserArgs = {
  id: Scalars['ID']['input'];
};


export type MutationUpdateBankAccountArgs = {
  id: Scalars['ID']['input'];
  input: UpdateBankAccountInput;
};


export type MutationUpdateChargebackRecoveryArgs = {
  id: Scalars['ID']['input'];
  input: UpdateChargebackRecoveryInput;
};


export type MutationUpdateChartOfAccountsEntryArgs = {
  id: Scalars['ID']['input'];
  input: CreateChartOfAccountsInput;
};


export type MutationUpdateCityArgs = {
  id: Scalars['ID']['input'];
  input: UpdateCityInput;
};


export type MutationUpdateEscrowAccountStatusArgs = {
  accountId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
  status: EscrowAccountStatus;
};


export type MutationUpdateEventArgs = {
  id: Scalars['ID']['input'];
  input: UpdateEventInput;
};


export type MutationUpdateEventAccessArgs = {
  accessId: Scalars['ID']['input'];
  customPermissions: InputMaybe<Array<Scalars['String']['input']>>;
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
  newRole: InputMaybe<EventRole>;
};


export type MutationUpdateEventAccessibilityArgs = {
  eventId: Scalars['ID']['input'];
  input: EventAccessibilityInput;
};


export type MutationUpdateEventCapacityArgs = {
  eventId: Scalars['ID']['input'];
  newCapacity: Scalars['Int']['input'];
};


export type MutationUpdateEventCategoryArgs = {
  id: Scalars['ID']['input'];
  input: UpdateEventCategoryInput;
};


export type MutationUpdateMediaArgs = {
  id: Scalars['ID']['input'];
  input: UpdateMediaInput;
};


export type MutationUpdateMemberRoleArgs = {
  input: UpdateMemberRoleInput;
  memberId: Scalars['ID']['input'];
};


export type MutationUpdateMyProfileArgs = {
  input: UpdateUserInput;
};


export type MutationUpdateNotificationPreferencesArgs = {
  input: UpdateNotificationPreferencesInput;
};


export type MutationUpdateOrganizationArgs = {
  id: Scalars['ID']['input'];
  input: UpdateOrganizationInput;
};


export type MutationUpdateOrganizationApplicationArgs = {
  id: Scalars['ID']['input'];
  input: OrganizationApplicationInput;
};


export type MutationUpdateOrganizationSettingsArgs = {
  id: Scalars['ID']['input'];
  input: UpdateOrganizationSettingsInput;
};


export type MutationUpdateOrganizationStatusArgs = {
  id: Scalars['ID']['input'];
  status: OrganizationStatus;
};


export type MutationUpdatePayoutConfigArgs = {
  input: UpdatePayoutConfigInput;
  organizationId: Scalars['ID']['input'];
};


export type MutationUpdatePlatformConfigurationArgs = {
  input: UpdatePlatformConfigurationInput;
};


export type MutationUpdatePromoCodeArgs = {
  id: Scalars['ID']['input'];
  input: UpdatePromoCodeInput;
};


export type MutationUpdateProvinceArgs = {
  id: Scalars['ID']['input'];
  input: UpdateProvinceInput;
};


export type MutationUpdateReferenceDataArgs = {
  id: Scalars['ID']['input'];
  input: UpdateReferenceDataInput;
};


export type MutationUpdateStockImageArgs = {
  id: Scalars['ID']['input'];
  input: UpdateStockImageInput;
};


export type MutationUpdateTicketTierArgs = {
  input: UpdateTicketTierInput;
  tierId: Scalars['ID']['input'];
};


export type MutationUpdateUserArgs = {
  id: Scalars['ID']['input'];
  input: UpdateUserInput;
};


export type MutationUpgradeToBusinessOrganizationArgs = {
  businessName: Scalars['String']['input'];
  organizationId: Scalars['ID']['input'];
};


export type MutationUploadMediaArgs = {
  input: UploadMediaInput;
};


export type MutationUploadScansArgs = {
  inputs: Array<ValidateTicketInput>;
};


export type MutationUploadStockImageArgs = {
  input: UploadStockImageInput;
};


export type MutationUploadVerificationDocumentArgs = {
  input: UploadVerificationDocumentInput;
};


export type MutationValidateTicketArgs = {
  input: ValidateTicketInput;
};


export type MutationVerifyBankAccountArgs = {
  id: Scalars['ID']['input'];
};


export type MutationVerifyEscrowJournalConsistencyArgs = {
  eventId: Scalars['ID']['input'];
};


export type MutationVerifyPayoutAccountArgs = {
  organizationId: Scalars['ID']['input'];
  verified: Scalars['Boolean']['input'];
};


export type MutationWithdrawRecoveryProposalArgs = {
  proposalId: Scalars['ID']['input'];
};

export type MyContacts = {
  __typename: 'MyContacts';
  contacts: Array<Contact>;
  pendingChange: Maybe<PendingContactChange>;
};

export type MyPermissions = {
  __typename: 'MyPermissions';
  permissions: Array<Scalars['String']['output']>;
  roles: Array<Scalars['String']['output']>;
};

export type NearbyLocationInput = {
  latitude: Scalars['Float']['input'];
  longitude: Scalars['Float']['input'];
  maxResults?: InputMaybe<Scalars['Int']['input']>;
  radiusKm?: InputMaybe<Scalars['Float']['input']>;
};

export type Notification = {
  __typename: 'Notification';
  actionUrl: Maybe<Scalars['String']['output']>;
  body: Scalars['String']['output'];
  channelStatuses: Maybe<Array<ChannelStatus>>;
  channels: Array<NotificationChannel>;
  createdAt: Scalars['DateTime']['output'];
  data: Maybe<Scalars['JSON']['output']>;
  deliveredAt: Maybe<Scalars['DateTime']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  imageUrl: Maybe<Scalars['String']['output']>;
  priority: Maybe<Scalars['String']['output']>;
  readAt: Maybe<Scalars['DateTime']['output']>;
  scheduledAt: Maybe<Scalars['DateTime']['output']>;
  sentAt: Maybe<Scalars['DateTime']['output']>;
  status: NotificationStatus;
  title: Scalars['String']['output'];
  type: NotificationType;
  userId: Scalars['ID']['output'];
};

export type NotificationChannel =
  | 'EMAIL'
  | 'IN_APP'
  | 'PUSH'
  | 'SMS'
  | 'WHATSAPP';

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

export type NotificationOffsetPage = {
  __typename: 'NotificationOffsetPage';
  content: Array<Notification>;
  pageInfo: PageInfo;
};

export type NotificationPreferences = {
  __typename: 'NotificationPreferences';
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
  reminderHoursBefore: Scalars['Int']['output'];
  smsEnabled: Scalars['Boolean']['output'];
  systemAnnouncements: Scalars['Boolean']['output'];
  teamNotifications: Scalars['Boolean']['output'];
  ticketNotifications: Scalars['Boolean']['output'];
  timezone: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
  whatsappEnabled: Scalars['Boolean']['output'];
};

export type NotificationStatus =
  | 'DELIVERED'
  | 'EXPIRED'
  | 'FAILED'
  | 'PENDING'
  | 'QUEUED'
  | 'READ'
  | 'SENT'
  | 'SUPPRESSED';

export type NotificationType =
  | 'ACCOUNT_SECURITY'
  | 'EVENT_APPROVED'
  | 'EVENT_CANCELLED'
  | 'EVENT_CHANGES_REQUESTED'
  | 'EVENT_REJECTED'
  | 'EVENT_REMINDER'
  | 'EVENT_STARTING_SOON'
  | 'EVENT_UPDATED'
  | 'ORGANIZER_APPROVED'
  | 'ORGANIZER_CHANGES_REQUESTED'
  | 'ORGANIZER_REJECTED'
  | 'ORGANIZER_SUSPENDED'
  | 'OWNERSHIP_TRANSFER_COMPLETED'
  | 'OWNERSHIP_TRANSFER_REQUESTED'
  | 'PAYMENT_FAILED'
  | 'PAYMENT_PENDING'
  | 'PAYMENT_SUCCESSFUL'
  | 'PAYOUT_APPROVED'
  | 'PAYOUT_COMPLETED'
  | 'PAYOUT_FAILED'
  | 'PAYOUT_REQUESTED'
  | 'QUEUE_TURN'
  | 'REFUND_APPROVED'
  | 'REFUND_PROCESSED'
  | 'REFUND_REJECTED'
  | 'REFUND_REQUESTED'
  | 'SYSTEM_ANNOUNCEMENT'
  | 'TEAM_INVITATION_RECEIVED'
  | 'TEAM_MEMBER_JOINED'
  | 'TEAM_MEMBER_LEFT'
  | 'TEAM_ROLE_CHANGED'
  | 'TICKET_EXPIRING'
  | 'TICKET_PURCHASED'
  | 'TICKET_REFUND_REQUESTED'
  | 'TICKET_TRANSFERRED'
  | 'TICKET_TRANSFER_RECEIVED'
  | 'WAITLIST_AVAILABLE'
  | 'WELCOME';

export type OffsetPaginationInput = {
  page?: InputMaybe<Scalars['Int']['input']>;
  size?: InputMaybe<Scalars['Int']['input']>;
  sortBy?: InputMaybe<Scalars['String']['input']>;
  sortDirection?: InputMaybe<SortDirection>;
};

export type Organization = {
  __typename: 'Organization';
  activeMembers: Maybe<Array<OrganizationMember>>;
  approvedAt: Maybe<Scalars['DateTime']['output']>;
  averageRating: Maybe<Scalars['Float']['output']>;
  bannerUrl: Maybe<Scalars['String']['output']>;
  businessAddress: Maybe<BusinessAddress>;
  businessEmail: Maybe<Scalars['String']['output']>;
  businessPhone: Maybe<Scalars['String']['output']>;
  businessRegistrationNumber: Maybe<Scalars['String']['output']>;
  businessType: Maybe<BusinessType>;
  canBeEdited: Scalars['Boolean']['output'];
  canCreateDraftEvents: Scalars['Boolean']['output'];
  canPublishEvents: Scalars['Boolean']['output'];
  canReceivePayouts: Scalars['Boolean']['output'];
  canSubmitForReview: Scalars['Boolean']['output'];
  commissionRate: Maybe<Scalars['Float']['output']>;
  completedEventCount: Scalars['Int']['output'];
  createdAt: Scalars['DateTime']['output'];
  deletionRequestedAt: Maybe<Scalars['DateTime']['output']>;
  deletionScheduledFor: Maybe<Scalars['DateTime']['output']>;
  description: Maybe<Scalars['String']['output']>;
  documentsVerified: Scalars['Boolean']['output'];
  id: Scalars['ID']['output'];
  isApproved: Scalars['Boolean']['output'];
  isInApprovalWorkflow: Scalars['Boolean']['output'];
  keycloakGroupId: Maybe<Scalars['String']['output']>;
  kybStatus: KybStatus;
  kybSubmittedAt: Maybe<Scalars['DateTime']['output']>;
  logoUrl: Maybe<Scalars['String']['output']>;
  memberCount: Scalars['Int']['output'];
  members: Maybe<Array<OrganizationMember>>;
  name: Scalars['String']['output'];
  owner: Maybe<User>;
  ownerId: Scalars['ID']['output'];
  payoutAccountVerified: Scalars['Boolean']['output'];
  payoutConfig: Maybe<PayoutConfig>;
  pendingInvitationCount: Scalars['Int']['output'];
  pendingInvitations: Maybe<Array<TeamInvitation>>;
  publishedEventCount: Scalars['Int']['output'];
  rejectionReason: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<User>;
  settings: Maybe<OrganizationSettings>;
  slug: Scalars['String']['output'];
  socialLinks: Maybe<SocialLinks>;
  status: OrganizationStatus;
  submittedAt: Maybe<Scalars['DateTime']['output']>;
  suspendedAt: Maybe<Scalars['DateTime']['output']>;
  suspensionReason: Maybe<Scalars['String']['output']>;
  tagline: Maybe<Scalars['String']['output']>;
  taxId: Maybe<Scalars['String']['output']>;
  totalEvents: Maybe<Scalars['Int']['output']>;
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
  totalTicketsSold: Maybe<Scalars['Int']['output']>;
  type: OrganizationType;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  verificationDocuments: Maybe<Array<VerificationDocument>>;
  verified: Scalars['Boolean']['output'];
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedBy: Maybe<User>;
  website: Maybe<Scalars['String']['output']>;
  yearEstablished: Maybe<Scalars['Int']['output']>;
};

export type OrganizationApplicationInput = {
  bannerUrl?: InputMaybe<Scalars['String']['input']>;
  businessEmail?: InputMaybe<Scalars['String']['input']>;
  businessPhone?: InputMaybe<Scalars['String']['input']>;
  businessRegistrationNumber?: InputMaybe<Scalars['String']['input']>;
  businessType?: InputMaybe<BusinessType>;
  city?: InputMaybe<Scalars['String']['input']>;
  country?: InputMaybe<Scalars['String']['input']>;
  description?: InputMaybe<Scalars['String']['input']>;
  logoUrl?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  province?: InputMaybe<Scalars['String']['input']>;
  socialLinks?: InputMaybe<SocialLinksInput>;
  tagline?: InputMaybe<Scalars['String']['input']>;
  taxId?: InputMaybe<Scalars['String']['input']>;
  type?: InputMaybe<OrganizationType>;
  website?: InputMaybe<Scalars['String']['input']>;
};

export type OrganizationApplicationOffsetPage = {
  __typename: 'OrganizationApplicationOffsetPage';
  content: Array<Organization>;
  pageInfo: PageInfo;
};

export type OrganizationMember = {
  __typename: 'OrganizationMember';
  contactEmailMasked: Maybe<Scalars['String']['output']>;
  contactPhoneMasked: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  customPermissions: Maybe<Array<Scalars['String']['output']>>;
  deniedPermissions: Maybe<Array<Scalars['String']['output']>>;
  id: Scalars['ID']['output'];
  invitedBy: Maybe<User>;
  invitedById: Maybe<Scalars['ID']['output']>;
  joinedAt: Scalars['DateTime']['output'];
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  organization: Maybe<Organization>;
  organizationId: Scalars['ID']['output'];
  role: OrganizationRole;
  status: MemberStatus;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  user: Maybe<User>;
  userId: Scalars['ID']['output'];
};

export type OrganizationMemberOffsetPage = {
  __typename: 'OrganizationMemberOffsetPage';
  content: Array<OrganizationMember>;
  pageInfo: PageInfo;
};

export type OrganizationOffsetPage = {
  __typename: 'OrganizationOffsetPage';
  content: Array<Organization>;
  pageInfo: PageInfo;
};

export type OrganizationRole =
  | 'ADMIN'
  | 'CONTRIBUTOR'
  | 'MANAGER'
  | 'MARKETER'
  | 'OWNER';

export type OrganizationSettings = {
  __typename: 'OrganizationSettings';
  adminsCanRequestPayouts: Scalars['Boolean']['output'];
  allowMembersToInvite: Scalars['Boolean']['output'];
  defaultEventVisibility: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  inviteRequiresApproval: Scalars['Boolean']['output'];
  managersCanViewFinancials: Scalars['Boolean']['output'];
  maxTeamMembers: Maybe<Scalars['Int']['output']>;
  notifyOwnerOnEventCreated: Scalars['Boolean']['output'];
  notifyOwnerOnMemberJoin: Scalars['Boolean']['output'];
  notifyOwnerOnPayoutRequest: Scalars['Boolean']['output'];
  organizationId: Scalars['ID']['output'];
  requireEventApproval: Scalars['Boolean']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedById: Maybe<Scalars['ID']['output']>;
};

export type OrganizationStatus =
  | 'ACTIVE'
  | 'APPROVED'
  | 'CHANGES_REQUESTED'
  | 'DRAFT'
  | 'INACTIVE'
  | 'PENDING_DELETION'
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'SUSPENDED';

export type OrganizationType =
  | 'BUSINESS'
  | 'COMMUNITY'
  | 'EDUCATIONAL'
  | 'GOVERNMENT'
  | 'INDIVIDUAL'
  | 'NON_PROFIT'
  | 'RELIGIOUS';

export type OrganizerActivityItem = {
  __typename: 'OrganizerActivityItem';
  amount: Maybe<Scalars['BigDecimal']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  eventId: Maybe<Scalars['String']['output']>;
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  message: Scalars['String']['output'];
  timestamp: Scalars['DateTime']['output'];
  type: OrganizerActivityType;
};

export type OrganizerActivityType =
  | 'CHECK_IN'
  | 'EVENT_CREATED'
  | 'EVENT_PUBLISHED'
  | 'PAYOUT_COMPLETED'
  | 'PAYOUT_REQUESTED'
  | 'REFUND_PROCESSED'
  | 'TICKET_SALE';

export type OrganizerCheckInRate = {
  __typename: 'OrganizerCheckInRate';
  eventDateTime: Maybe<Scalars['DateTime']['output']>;
  eventId: Scalars['ID']['output'];
  eventTitle: Scalars['String']['output'];
  issued: Scalars['Int']['output'];
  ratePercent: Scalars['Float']['output'];
  scanned: Scalars['Int']['output'];
};

export type OrganizerDashboardStats = {
  __typename: 'OrganizerDashboardStats';
  activeEvents: Scalars['Int']['output'];
  attendeesChange: Maybe<Scalars['Float']['output']>;
  availableBalance: Scalars['BigDecimal']['output'];
  eventsChange: Maybe<Scalars['Float']['output']>;
  eventsEndingThisWeek: Scalars['Int']['output'];
  pendingPayouts: Scalars['BigDecimal']['output'];
  periodEnd: Maybe<Scalars['DateTime']['output']>;
  periodStart: Maybe<Scalars['DateTime']['output']>;
  revenueChange: Maybe<Scalars['Float']['output']>;
  revenueCurrency: Scalars['String']['output'];
  ticketsSoldChange: Maybe<Scalars['Float']['output']>;
  totalAttendees: Scalars['Int']['output'];
  totalRevenue: Scalars['BigDecimal']['output'];
  totalTicketsSold: Scalars['Int']['output'];
};

export type OrganizerEventFilterInput = {
  eventDateAfter?: InputMaybe<Scalars['DateTime']['input']>;
  eventDateBefore?: InputMaybe<Scalars['DateTime']['input']>;
  searchQuery?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<EventStatus>;
  statuses?: InputMaybe<Array<EventStatus>>;
};

export type OrganizerFinanceOverview = {
  __typename: 'OrganizerFinanceOverview';
  availableBalance: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  earningsLastMonth: Scalars['BigDecimal']['output'];
  earningsThisMonth: Scalars['BigDecimal']['output'];
  lastPayoutAmount: Maybe<Scalars['BigDecimal']['output']>;
  lastPayoutDate: Maybe<Scalars['DateTime']['output']>;
  monthlyGrowth: Maybe<Scalars['Float']['output']>;
  netEarnings: Scalars['BigDecimal']['output'];
  pendingBalance: Scalars['BigDecimal']['output'];
  pendingPayoutRequests: Scalars['Int']['output'];
  platformFees: Scalars['BigDecimal']['output'];
  totalEarned: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalTicketRevenue: Scalars['BigDecimal']['output'];
};

export type OrganizerPayoutSource = {
  __typename: 'OrganizerPayoutSource';
  availableAmount: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  eligibleSince: Maybe<Scalars['DateTime']['output']>;
  escrowAccountId: Scalars['ID']['output'];
  eventId: Maybe<Scalars['String']['output']>;
  eventTitle: Maybe<Scalars['String']['output']>;
};

export type OrganizerPayoutWindow = {
  __typename: 'OrganizerPayoutWindow';
  availableNow: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  daysElapsed: Scalars['Int']['output'];
  daysRemaining: Scalars['Int']['output'];
  nextReleaseAt: Maybe<Scalars['DateTime']['output']>;
  pendingRelease: Scalars['BigDecimal']['output'];
  windowDaysTotal: Scalars['Int']['output'];
  windowOpenedAt: Maybe<Scalars['DateTime']['output']>;
};

export type OrganizerRevenuePoint = {
  __typename: 'OrganizerRevenuePoint';
  currency: Scalars['String']['output'];
  periodStart: Scalars['String']['output'];
  revenue: Scalars['BigDecimal']['output'];
  ticketsSold: Scalars['Int']['output'];
};

export type OrganizerShareRow = {
  __typename: 'OrganizerShareRow';
  count: Scalars['Int']['output'];
  name: Scalars['String']['output'];
  revenue: Maybe<Scalars['BigDecimal']['output']>;
};

export type OrganizerTicketMix = {
  __typename: 'OrganizerTicketMix';
  currency: Scalars['String']['output'];
  rows: Array<OrganizerShareRow>;
  totalRevenue: Scalars['BigDecimal']['output'];
  totalSold: Scalars['Int']['output'];
};

export type OrganizerTransaction = {
  __typename: 'OrganizerTransaction';
  amount: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  description: Scalars['String']['output'];
  eventId: Maybe<Scalars['String']['output']>;
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  payoutRequestId: Maybe<Scalars['String']['output']>;
  reference: Maybe<Scalars['String']['output']>;
  status: Scalars['String']['output'];
  ticketId: Maybe<Scalars['String']['output']>;
  timestamp: Scalars['DateTime']['output'];
  type: OrganizerTransactionType;
};

export type OrganizerTransactionFilterInput = {
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  eventId?: InputMaybe<Scalars['ID']['input']>;
  maxAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  minAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  type?: InputMaybe<OrganizerTransactionType>;
};

export type OrganizerTransactionOffsetPage = {
  __typename: 'OrganizerTransactionOffsetPage';
  content: Array<OrganizerTransaction>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  page: Scalars['Int']['output'];
  size: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

export type OrganizerTransactionType =
  | 'ADJUSTMENT'
  | 'PAYOUT'
  | 'PLATFORM_FEE'
  | 'REFUND'
  | 'TICKET_SALE';

export type OrganizerUpcomingEvent = {
  __typename: 'OrganizerUpcomingEvent';
  currency: Scalars['String']['output'];
  eventDateTime: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  revenue: Scalars['BigDecimal']['output'];
  status: Scalars['String']['output'];
  ticketsSold: Scalars['Int']['output'];
  title: Scalars['String']['output'];
  totalCapacity: Scalars['Int']['output'];
};

export type OwnershipTransferRequest = {
  __typename: 'OwnershipTransferRequest';
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  completedAt: Maybe<Scalars['DateTime']['output']>;
  currentOwner: Maybe<User>;
  currentOwnerId: Scalars['ID']['output'];
  expiresAt: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  initiatedAt: Scalars['DateTime']['output'];
  newOwner: Maybe<User>;
  newOwnerId: Scalars['ID']['output'];
  organization: Maybe<Organization>;
  organizationId: Scalars['ID']['output'];
  reason: Maybe<Scalars['String']['output']>;
  status: TransferStatus;
};

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

export type PayReservationInput = {
  idempotencyKey: Scalars['String']['input'];
  phoneNumber: Scalars['String']['input'];
  reservationId: Scalars['ID']['input'];
};

export type PaymentAttempt = {
  __typename: 'PaymentAttempt';
  amount: Scalars['BigDecimal']['output'];
  amountVerified: Scalars['Boolean']['output'];
  apiCalledAt: Maybe<Scalars['DateTime']['output']>;
  apiDurationMs: Maybe<Scalars['Int']['output']>;
  apiHttpStatus: Maybe<Scalars['Int']['output']>;
  apiRespondedAt: Maybe<Scalars['DateTime']['output']>;
  attemptNumber: Scalars['String']['output'];
  buyerId: Scalars['String']['output'];
  clientIpAddress: Maybe<Scalars['String']['output']>;
  clientReferenceId: Maybe<Scalars['String']['output']>;
  commissionId: Maybe<Scalars['String']['output']>;
  correlationId: Maybe<Scalars['String']['output']>;
  country: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  customerErrorMessage: Maybe<Scalars['String']['output']>;
  customerMessage: Maybe<Scalars['String']['output']>;
  depositId: Scalars['String']['output'];
  escrowTransactionId: Maybe<Scalars['String']['output']>;
  eventId: Maybe<Scalars['String']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  failureCode: Maybe<Scalars['String']['output']>;
  failureMessage: Maybe<Scalars['String']['output']>;
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
  payerPhone: Scalars['String']['output'];
  pollCount: Scalars['Int']['output'];
  provider: Scalars['String']['output'];
  providerStatus: Maybe<Scalars['String']['output']>;
  providerTransactionId: Maybe<Scalars['String']['output']>;
  requestId: Maybe<Scalars['String']['output']>;
  retryCount: Scalars['Int']['output'];
  reviewNotes: Maybe<Scalars['String']['output']>;
  reviewStatus: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  riskFlags: Array<Scalars['String']['output']>;
  riskLevel: Maybe<Scalars['String']['output']>;
  riskScore: Maybe<Scalars['Int']['output']>;
  sessionId: Maybe<Scalars['String']['output']>;
  status: PaymentAttemptStatus;
  ticketId: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedAt: Maybe<Scalars['DateTime']['output']>;
  verifiedBeforeFulfillment: Scalars['Boolean']['output'];
  webhookProcessed: Scalars['Boolean']['output'];
  webhookReceivedAt: Maybe<Scalars['DateTime']['output']>;
  webhookSignatureValid: Maybe<Scalars['Boolean']['output']>;
  webhookSourceIp: Maybe<Scalars['String']['output']>;
};

export type PaymentAttemptFilterInput = {
  attemptType?: InputMaybe<PaymentAttemptType>;
  buyerId?: InputMaybe<Scalars['String']['input']>;
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore?: InputMaybe<Scalars['DateTime']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  maxAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  minAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  organizationId?: InputMaybe<Scalars['String']['input']>;
  provider?: InputMaybe<Scalars['String']['input']>;
  reference?: InputMaybe<Scalars['String']['input']>;
  reviewStatus?: InputMaybe<Scalars['String']['input']>;
  riskLevel?: InputMaybe<Scalars['String']['input']>;
  statuses?: InputMaybe<Array<PaymentAttemptStatus>>;
  stuckForMinutes?: InputMaybe<Scalars['Int']['input']>;
};

export type PaymentAttemptOffsetPage = {
  __typename: 'PaymentAttemptOffsetPage';
  data: Array<PaymentAttempt>;
  pagination: PaginationInfo;
};

export type PaymentAttemptStatus =
  | 'CANCELLED'
  | 'COMPLETED'
  | 'CONFIRMED'
  | 'CREATED'
  | 'EXPIRED'
  | 'FAILED'
  | 'PENDING_APPROVAL'
  | 'PROCESSING'
  | 'REJECTED';

export type PaymentAttemptType =
  | 'COLLECT'
  | 'PAYOUT'
  | 'REFUND'
  | 'VERIFICATION';

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

export type PaymentInitiationResponse = {
  __typename: 'PaymentInitiationResponse';
  paymentIntentId: Maybe<Scalars['ID']['output']>;
  paymentStatus: Maybe<Scalars['String']['output']>;
  reservationId: Maybe<Scalars['ID']['output']>;
  transactionRef: Maybe<Scalars['String']['output']>;
};

export type PaymentMethod =
  | 'BANK_TRANSFER'
  | 'CARD'
  | 'MOBILE_MONEY';

export type PaymentRecoveryOutcome = {
  __typename: 'PaymentRecoveryOutcome';
  depositId: Scalars['String']['output'];
  detail: Maybe<Scalars['String']['output']>;
  result: Scalars['String']['output'];
};

export type PaymentRiskSummary = {
  __typename: 'PaymentRiskSummary';
  amountAtRisk: Scalars['BigDecimal']['output'];
  evaluated: Scalars['Int']['output'];
  flagged: Scalars['Int']['output'];
  high: Scalars['Int']['output'];
  low: Scalars['Int']['output'];
  medium: Scalars['Int']['output'];
  topFlags: Array<RiskFlagCount>;
  windowHours: Scalars['Int']['output'];
};

export type PayoutAccountFilterInput = {
  method?: InputMaybe<PayoutMethod>;
  search?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<PayoutAccountStatus>;
};

export type PayoutAccountRecord = {
  __typename: 'PayoutAccountRecord';
  accountHolderName: Maybe<Scalars['String']['output']>;
  accountNumberMasked: Maybe<Scalars['String']['output']>;
  bankName: Maybe<Scalars['String']['output']>;
  method: PayoutMethod;
  network: Maybe<MobileMoneyProvider>;
  organizationId: Scalars['ID']['output'];
  organizationName: Scalars['String']['output'];
  organizationSlug: Scalars['String']['output'];
  phoneMasked: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  status: PayoutAccountStatus;
  suspendedReason: Maybe<Scalars['String']['output']>;
  testDepositSentAt: Maybe<Scalars['DateTime']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  verificationAttemptsLeft: Scalars['Int']['output'];
};

export type PayoutAccountRecordOffsetPage = {
  __typename: 'PayoutAccountRecordOffsetPage';
  content: Array<PayoutAccountRecord>;
  pageInfo: PageInfo;
};

export type PayoutAccountStatus =
  | 'NONE'
  | 'PENDING'
  | 'REJECTED'
  | 'SUSPENDED'
  | 'VERIFIED';

export type PayoutBankDetails = {
  __typename: 'PayoutBankDetails';
  accountHolderName: Maybe<Scalars['String']['output']>;
  accountNumber: Maybe<Scalars['String']['output']>;
  accountType: Maybe<Scalars['String']['output']>;
  bankCode: Maybe<Scalars['String']['output']>;
  bankName: Maybe<Scalars['String']['output']>;
  branchCode: Maybe<Scalars['String']['output']>;
  branchName: Maybe<Scalars['String']['output']>;
  maskedAccountNumber: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  status: PayoutAccountStatus;
  suspended: Scalars['Boolean']['output'];
  suspendedReason: Maybe<Scalars['String']['output']>;
  testDepositSentAt: Maybe<Scalars['DateTime']['output']>;
  verificationAttemptsLeft: Scalars['Int']['output'];
  verified: Scalars['Boolean']['output'];
};

export type PayoutBlockedReason =
  | 'BELOW_MINIMUM'
  | 'EVENT_NOT_COMPLETED'
  | 'HOLD_NOT_ELAPSED'
  | 'NO_ESCROW_ACCOUNT'
  | 'OPEN_DISPUTES'
  | 'PAYOUT_ALREADY_REQUESTED';

export type PayoutConfig = {
  __typename: 'PayoutConfig';
  bankAccount: Maybe<PayoutBankDetails>;
  canProcessPayouts: Scalars['Boolean']['output'];
  commissionRate: Maybe<Scalars['Float']['output']>;
  isConfigured: Scalars['Boolean']['output'];
  minimumPayoutAmount: Maybe<Scalars['Float']['output']>;
  mobileMoneyAccount: Maybe<MobileMoneyAccount>;
  preferredMethod: Maybe<PayoutMethod>;
  schedule: Maybe<PayoutSchedule>;
  verified: Scalars['Boolean']['output'];
};

export type PayoutEligibility = {
  __typename: 'PayoutEligibility';
  availableAmount: Scalars['BigDecimal']['output'];
  currency: Scalars['String']['output'];
  eligible: Scalars['Boolean']['output'];
  minimumAmount: Scalars['BigDecimal']['output'];
  opensAt: Maybe<Scalars['DateTime']['output']>;
  reasons: Array<PayoutBlockedReason>;
};

export type PayoutIssueType =
  | 'BANK_REJECTED'
  | 'COMPLIANCE_HOLD'
  | 'DUPLICATE_REQUEST'
  | 'INSUFFICIENT_ESCROW'
  | 'INVALID_ACCOUNT_DETAILS'
  | 'OTHER'
  | 'PROVIDER_ERROR'
  | 'SUSPECTED_FRAUD'
  | 'TECHNICAL_ERROR'
  | 'TIMEOUT';

export type PayoutIssueTypeStats = {
  __typename: 'PayoutIssueTypeStats';
  count: Scalars['Int']['output'];
  issueType: PayoutIssueType;
  percentage: Scalars['Float']['output'];
  totalAmount: Scalars['BigDecimal']['output'];
  unresolvedCount: Scalars['Int']['output'];
};

export type PayoutMethod =
  | 'BANK_TRANSFER'
  | 'CHEQUE'
  | 'MOBILE_MONEY';

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
  event: Maybe<Event>;
  eventId: Maybe<Scalars['String']['output']>;
  eventTitle: Maybe<Scalars['String']['output']>;
  expectedPayoutDate: Maybe<Scalars['DateTime']['output']>;
  externalTransactionId: Maybe<Scalars['String']['output']>;
  heldAt: Maybe<Scalars['DateTime']['output']>;
  heldBy: Maybe<Scalars['String']['output']>;
  holdReason: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isStuck: Maybe<Scalars['Boolean']['output']>;
  issueType: Maybe<PayoutIssueType>;
  lastError: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  notes: Maybe<Scalars['String']['output']>;
  onHold: Scalars['Boolean']['output'];
  organization: Maybe<Organization>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  paymentReference: Maybe<Scalars['String']['output']>;
  payoutMethod: Maybe<PayoutMethod>;
  processedAt: Maybe<Scalars['DateTime']['output']>;
  processedBy: Maybe<Scalars['String']['output']>;
  rejectedAt: Maybe<Scalars['DateTime']['output']>;
  rejectedBy: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  releasedAt: Maybe<Scalars['DateTime']['output']>;
  releasedBy: Maybe<Scalars['String']['output']>;
  requestId: Scalars['String']['output'];
  requestedAmount: Scalars['BigDecimal']['output'];
  requestedAt: Scalars['DateTime']['output'];
  requestedById: Scalars['String']['output'];
  resolutionNotes: Maybe<Scalars['String']['output']>;
  resolutionType: Maybe<PayoutResolutionType>;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  resolvedBy: Maybe<Scalars['String']['output']>;
  retryCount: Maybe<Scalars['Int']['output']>;
  reviewNotes: Maybe<Scalars['String']['output']>;
  reviewStatus: Maybe<PayoutReviewStatus>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  settledAmount: Scalars['BigDecimal']['output'];
  status: PayoutRequestStatus;
  stuckAt: Maybe<Scalars['DateTime']['output']>;
  stuckReason: Maybe<Scalars['String']['output']>;
  taxAmount: Maybe<Scalars['BigDecimal']['output']>;
  transactionId: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type PayoutRequestFilterInput = {
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  escrowAccountId?: InputMaybe<Scalars['String']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  payoutMethod?: InputMaybe<PayoutMethod>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  status?: InputMaybe<PayoutRequestStatus>;
};

export type PayoutRequestOffsetPage = {
  __typename: 'PayoutRequestOffsetPage';
  data: Array<PayoutRequest>;
  pagination: PaginationInfo;
};

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

export type PayoutRequestStatus =
  | 'APPROVED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'FAILED'
  | 'ON_HOLD'
  | 'PENDING'
  | 'PROCESSING'
  | 'REJECTED';

export type PayoutResolutionType =
  | 'ACCOUNT_UPDATED'
  | 'AUTO_RESOLVED'
  | 'ESCALATED'
  | 'MANUAL_APPROVAL'
  | 'MANUAL_REJECTION'
  | 'REFUNDED_TO_ESCROW'
  | 'RETRIED_SUCCESS'
  | 'WRITTEN_OFF';

export type PayoutReviewStatus =
  | 'ESCALATED'
  | 'NONE'
  | 'PENDING_REVIEW'
  | 'REVIEWED'
  | 'UNDER_REVIEW';

export type PayoutSchedule =
  | 'BIWEEKLY'
  | 'DAILY'
  | 'MANUAL'
  | 'MONTHLY'
  | 'WEEKLY';

export type PendingContactChange = {
  __typename: 'PendingContactChange';
  attemptsRemaining: Scalars['Int']['output'];
  changeId: Scalars['ID']['output'];
  currentContactVerified: Scalars['Boolean']['output'];
  expiresAt: Scalars['DateTime']['output'];
  kind: ContactChangeKind;
  newContactMasked: Maybe<Scalars['String']['output']>;
};

export type Permission = {
  __typename: 'Permission';
  code: Scalars['String']['output'];
  description: Scalars['String']['output'];
  module: Scalars['String']['output'];
  scope: PermissionScope;
};

export type PermissionScope =
  | 'EVENT'
  | 'ORGANIZATION'
  | 'PLATFORM';

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

export type PlatformAccountType =
  | 'OPERATING'
  | 'RESERVE'
  | 'TAX_HOLDING';

export type PlatformConfiguration = {
  __typename: 'PlatformConfiguration';
  adminNotificationChannel: ApprovalNotificationChannel;
  allowSelfApproval: Scalars['Boolean']['output'];
  approvalSlaHours: Scalars['Int']['output'];
  approvalWarningThresholdHours: Scalars['Int']['output'];
  autoEscalationEnabled: Scalars['Boolean']['output'];
  commissionDefault: Maybe<Scalars['Float']['output']>;
  currency: Scalars['String']['output'];
  escalationDelayHours: Scalars['Int']['output'];
  escalationRecipientRole: Scalars['String']['output'];
  escalationReminderIntervalHours: Scalars['Int']['output'];
  escrowHoldDays: Scalars['Int']['output'];
  id: Scalars['ID']['output'];
  maxEscalationReminders: Scalars['Int']['output'];
  maxTicketsPerBooking: Scalars['Int']['output'];
  minimumPayout: Maybe<Scalars['BigDecimal']['output']>;
  organizerNotificationChannel: ApprovalNotificationChannel;
  refundCutoffHours: Scalars['Int']['output'];
  refundPolicies: Array<PlatformRefundPolicy>;
  requireCommentsOnChangesRequested: Scalars['Boolean']['output'];
  requireCommentsOnRejection: Scalars['Boolean']['output'];
  rescheduleLimit: Scalars['Int']['output'];
  reservationGraceMinutes: Scalars['Int']['output'];
  reservationHoldMinutes: Scalars['Int']['output'];
  sendEscalationNotifications: Scalars['Boolean']['output'];
  sendSlaWarningNotifications: Scalars['Boolean']['output'];
  updatedAt: Scalars['DateTime']['output'];
  updatedBy: Scalars['String']['output'];
  version: Scalars['Int']['output'];
};

export type PlatformRefundPolicy = {
  __typename: 'PlatformRefundPolicy';
  code: Scalars['String']['output'];
  label: Scalars['String']['output'];
  rules: Array<PlatformRefundRule>;
  summary: Scalars['String']['output'];
};

export type PlatformRefundPolicyInput = {
  code: Scalars['String']['input'];
  label?: InputMaybe<Scalars['String']['input']>;
  rules?: InputMaybe<Array<PlatformRefundRuleInput>>;
  summary?: InputMaybe<Scalars['String']['input']>;
};

export type PlatformRefundRule = {
  __typename: 'PlatformRefundRule';
  daysBefore: Scalars['Int']['output'];
  percent: Scalars['Int']['output'];
};

export type PlatformRefundRuleInput = {
  daysBefore: Scalars['Int']['input'];
  percent: Scalars['Int']['input'];
};

export type PlatformRules = {
  __typename: 'PlatformRules';
  approval: PlatformRulesApproval;
  commissionDefault: Scalars['Float']['output'];
  commissionRate: Maybe<Scalars['Float']['output']>;
  currency: Scalars['String']['output'];
  escrowHoldDays: Scalars['Int']['output'];
  maxTicketsPerBooking: Scalars['Int']['output'];
  minimumPayout: Maybe<Scalars['BigDecimal']['output']>;
  refundCutoffHours: Scalars['Int']['output'];
  refundPolicies: Array<RulesRefundPolicy>;
  rescheduleLimit: Scalars['Int']['output'];
  reservationGraceMinutes: Scalars['Int']['output'];
  reservationHoldMinutes: Scalars['Int']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedBy: Maybe<Scalars['String']['output']>;
  version: Scalars['Int']['output'];
};

export type PlatformRulesApproval = {
  __typename: 'PlatformRulesApproval';
  allowSelfApproval: Scalars['Boolean']['output'];
  autoEscalation: Scalars['Boolean']['output'];
  escalationDelayHours: Scalars['Int']['output'];
  requireCommentsOnChangesRequested: Scalars['Boolean']['output'];
  requireCommentsOnRejection: Scalars['Boolean']['output'];
  slaHours: Scalars['Int']['output'];
  warnHours: Scalars['Int']['output'];
};

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
  primaryCurrency: Scalars['String']['output'];
  totalCommissions: Scalars['BigDecimal']['output'];
  totalDeposits: Scalars['BigDecimal']['output'];
  totalEscrowAccounts: Scalars['Int']['output'];
  totalEscrowBalance: Scalars['BigDecimal']['output'];
  totalPayoutAmount: Scalars['BigDecimal']['output'];
  totalPayoutRequests: Scalars['Int']['output'];
  totalRefunds: Scalars['BigDecimal']['output'];
  totalTicketRevenue: Scalars['BigDecimal']['output'];
  totalTicketsSold: Scalars['Int']['output'];
  totalTransactionVolume: Scalars['BigDecimal']['output'];
  totalTransactions: Scalars['Int']['output'];
  totalWithdrawals: Scalars['BigDecimal']['output'];
};

export type PlatformTransfer = {
  __typename: 'PlatformTransfer';
  amount: Scalars['BigDecimal']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  executedBy: Scalars['String']['output'];
  fromAccount: PlatformAccountType;
  id: Scalars['ID']['output'];
  journalEntryId: Maybe<Scalars['String']['output']>;
  proposalId: Maybe<Scalars['String']['output']>;
  reason: Scalars['String']['output'];
  toAccount: PlatformAccountType;
};

export type PlatformTransferInput = {
  amount: Scalars['BigDecimal']['input'];
  fromAccount: PlatformAccountType;
  idempotencyKey: Scalars['String']['input'];
  reason: Scalars['String']['input'];
  toAccount: PlatformAccountType;
};

export type PlatformTransferResult = {
  __typename: 'PlatformTransferResult';
  executed: Scalars['Boolean']['output'];
  proposal: Maybe<RecoveryProposal>;
  requiresSecondApprover: Scalars['Boolean']['output'];
  transfer: Maybe<PlatformTransfer>;
};

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

export type PromoCodeValidation = {
  __typename: 'PromoCodeValidation';
  discountAmount: Maybe<Scalars['BigDecimal']['output']>;
  errorMessage: Maybe<Scalars['String']['output']>;
  promoCode: Maybe<PromoCode>;
  valid: Scalars['Boolean']['output'];
};

export type ProposeRecoveryActionInput = {
  action: RecoveryAction;
  amount?: InputMaybe<Scalars['BigDecimal']['input']>;
  parameters?: InputMaybe<Scalars['JSON']['input']>;
  reason: Scalars['String']['input'];
  subjectIds: Array<Scalars['String']['input']>;
};

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

export type PublicPlatformRules = {
  __typename: 'PublicPlatformRules';
  currency: Scalars['String']['output'];
  maxTicketsPerBooking: Scalars['Int']['output'];
  refundCutoffHours: Scalars['Int']['output'];
  refundPolicies: Array<RulesRefundPolicy>;
  rescheduleLimit: Scalars['Int']['output'];
  reservationGraceMinutes: Scalars['Int']['output'];
  reservationHoldMinutes: Scalars['Int']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  version: Scalars['Int']['output'];
};

export type PurchaseHeatCell = {
  __typename: 'PurchaseHeatCell';
  dayOfWeek: Scalars['Int']['output'];
  hour: Scalars['Int']['output'];
  purchases: Scalars['Int']['output'];
  revenue: Scalars['BigDecimal']['output'];
  tickets: Scalars['Int']['output'];
};

export type Query = {
  __typename: 'Query';
  accountSummary: Maybe<AccountSummary>;
  activeEscalations: ApprovalEscalationOffsetPage;
  allowedStatusTransitions: Array<EventStatus>;
  approvalEscalation: Maybe<ApprovalEscalation>;
  approvalStats: ApprovalStats;
  approvalTimeline: Maybe<ApprovalTimeline>;
  approvalTimelines: ApprovalTimelineOffsetPage;
  approvalTimelinesByOrganizer: ApprovalTimelineOffsetPage;
  approvedNotPublishedEvents: EventOffsetPage;
  auditLogs: AuditLogEntryOffsetPage;
  availableTicketTiers: Array<TicketTier>;
  bankAccount: Maybe<BankAccount>;
  bankAccounts: PayoutAccountRecordOffsetPage;
  bankAccountsByOrganizer: Array<BankAccount>;
  booking: Maybe<Booking>;
  bookingByNumber: Maybe<Booking>;
  bookingPendingCounts: BookingPendingCounts;
  bookingsByBuyer: BookingOffsetPage;
  bookingsByOrganizer: BookingOffsetPage;
  calculateRefundAmount: RefundCalculation;
  cancelledEvents: EventOffsetPage;
  catalogPendingCounts: CatalogPendingCounts;
  categories: Array<EventCategory>;
  chargeback: Maybe<ChargebackRecord>;
  chargebackByChargebackId: Maybe<ChargebackRecord>;
  chargebackStats: ChargebackStats;
  chargebacks: ChargebackOffsetPage;
  chargebacksByEvent: ChargebackOffsetPage;
  chargebacksByOrganizer: ChargebackOffsetPage;
  chargebacksPendingRecovery: Array<ChargebackRecord>;
  chartOfAccounts: Array<ChartOfAccountsEntry>;
  chartOfAccountsByCode: Maybe<ChartOfAccountsEntry>;
  chartOfAccountsByType: Array<ChartOfAccountsEntry>;
  chartOfAccountsEntry: Maybe<ChartOfAccountsEntry>;
  checkInConflicts: CheckInConflictPage;
  checkInSummary: CheckInSummary;
  cities: Array<City>;
  citiesWithEvents: Array<City>;
  city: Maybe<City>;
  commissionRecords: CommissionRecordPage;
  completedEvents: EventOffsetPage;
  confirmedUnfulfilledPaymentAttempts: Array<PaymentAttempt>;
  currentUserPermissions: Array<Scalars['String']['output']>;
  defaultBankAccount: Maybe<BankAccount>;
  discoverEvents: EventConnection;
  draftEvents: EventOffsetPage;
  dualControlQueue: Array<RecoveryProposal>;
  escrowAccount: Maybe<EventEscrowAccount>;
  escrowAccountBalance: Maybe<Scalars['BigDecimal']['output']>;
  escrowAccountByEvent: Maybe<EventEscrowAccount>;
  escrowAccountByNumber: Maybe<EventEscrowAccount>;
  escrowAccounts: EscrowAccountOffsetPage;
  escrowAccountsByOrganizer: EscrowAccountOffsetPage;
  escrowBalance: Scalars['BigDecimal']['output'];
  escrowBalanceAsOf: Scalars['BigDecimal']['output'];
  escrowJournalInconsistencies: Array<EscrowJournalVerificationResponse>;
  escrowJournalVerification: EscrowJournalVerificationResponse;
  escrowJournalVerificationAll: Array<EscrowJournalVerificationResponse>;
  escrowTransaction: Maybe<StandaloneEscrowTransaction>;
  escrowTransactions: EscrowTransactionOffsetPage;
  escrowTransactionsByTicket: Array<StandaloneEscrowTransaction>;
  escrowTransactionsUnlinked: Array<StandaloneEscrowTransaction>;
  event: Maybe<Event>;
  eventAccessGrant: Maybe<EventAccessGrant>;
  eventAccessGrants: EventAccessGrantOffsetPage;
  eventCategory: Maybe<EventCategory>;
  eventCount: Scalars['Int']['output'];
  eventCountByCategory: Scalars['Int']['output'];
  eventCountByCity: Scalars['Int']['output'];
  eventCountByOrganizer: Scalars['Int']['output'];
  eventCountByStatus: Scalars['Int']['output'];
  eventLifecycle: Maybe<EventLifecycle>;
  eventLiveDashboard: LiveDashboard;
  eventPromoCodes: Array<PromoCode>;
  eventRefundSummary: Maybe<RefundSummary>;
  eventStatistics: Maybe<EventTicketStatistics>;
  eventStats: EventStats;
  eventTicketTiers: Array<TicketTier>;
  events: EventOffsetPage;
  eventsByCategory: EventConnection;
  eventsByCity: EventConnection;
  eventsByStatus: EventOffsetPage;
  expiredReservations: ReservationOffsetPage;
  exportEventData: ReportExport;
  exportEventsReport: ReportExport;
  exportFinancialReport: ReportExport;
  exportSalesReport: ReportExport;
  failedPayoutRequests: PayoutRequestOffsetPage;
  financialReport: FinancialReport;
  gatewaySettlements: GatewaySettlementPage;
  hasPendingOwnershipTransfer: Scalars['Boolean']['output'];
  hasSuccessfulPayment: Scalars['Boolean']['output'];
  identityPendingCounts: Maybe<IdentityPendingCounts>;
  invitationByToken: Maybe<InvitationPreview>;
  isSlugAvailable: Scalars['Boolean']['output'];
  isTicketEligibleForRefund: Scalars['Boolean']['output'];
  journalEntries: JournalEntryOffsetPage;
  journalEntriesByAccountCode: JournalEntryOffsetPage;
  journalEntriesByCorrelationId: Array<JournalEntry>;
  journalEntry: Maybe<JournalEntry>;
  journalEntryByNumber: Maybe<JournalEntry>;
  latestPaymentAttemptByReservation: Maybe<PaymentAttempt>;
  location: Maybe<Location>;
  locations: LocationConnection;
  locationsByCity: LocationConnection;
  locationsByCountry: LocationConnection;
  locationsNearby: LocationConnection;
  me: Maybe<User>;
  mediaAssets: MediaAssetOffsetPage;
  myActiveReservations: Array<TicketReservation>;
  myAnnouncements: Array<SystemAnnouncement>;
  myApprovedDocumentCount: Scalars['Long']['output'];
  myBookings: BookingOffsetPage;
  myCheckInRate: Maybe<OrganizerCheckInRate>;
  myContacts: MyContacts;
  myDashboardStats: OrganizerDashboardStats;
  myDevices: Array<UserDevice>;
  myDraftEvents: EventOffsetPage;
  myEffectivePermissions: EffectivePermissions;
  myEscalations: ApprovalEscalationOffsetPage;
  myEscrowAccounts: EscrowAccountOffsetPage;
  myEventAccess: Maybe<EventAccessGrant>;
  myEventAccessGrants: Array<EventAccessGrant>;
  myEventCount: Scalars['Int']['output'];
  myEventCountByStatus: Scalars['Int']['output'];
  myEventReminders: Array<EventReminder>;
  myEvents: EventOffsetPage;
  myEventsConnection: EventConnection;
  myFinanceOverview: OrganizerFinanceOverview;
  myMedia: MediaAssetConnection;
  myNotificationPreferences: Maybe<NotificationPreferences>;
  myNotifications: NotificationConnection;
  myOrganization: Maybe<Organization>;
  myOrganizationMembership: Maybe<OrganizationMember>;
  myOrganizations: Array<Organization>;
  myOwnedOrganization: Maybe<Organization>;
  myPayoutRequests: PayoutRequestOffsetPage;
  myPayoutSources: Array<OrganizerPayoutSource>;
  myPayoutWindow: OrganizerPayoutWindow;
  myPendingInvitations: Array<TeamInvitation>;
  myPendingOwnershipTransfers: Array<OwnershipTransferRequest>;
  myPermissions: MyPermissions;
  myRecentActivity: Array<OrganizerActivityItem>;
  myRecoveryProposals: Array<RecoveryProposal>;
  myRefundRequests: RefundRequestOffsetPage;
  myRevenueSeries: Array<OrganizerRevenuePoint>;
  mySessions: Array<AccountSession>;
  myTicketMix: OrganizerTicketMix;
  myTicketTransfers: TicketTransferPage;
  myTransactions: OrganizerTransactionOffsetPage;
  myUpcomingEvents: Array<OrganizerUpcomingEvent>;
  myVerificationDocumentByType: Maybe<VerificationDocument>;
  myVerificationDocumentCount: Scalars['Long']['output'];
  myVerificationDocuments: Array<VerificationDocument>;
  /** Count users who have a specific role. */
  organization: Maybe<Organization>;
  organizationApplications: OrganizationApplicationOffsetPage;
  organizationByOwnerId: Maybe<Organization>;
  organizationBySlug: Maybe<Organization>;
  organizationCount: Scalars['Long']['output'];
  organizationEventAccessGrants: EventAccessGrantOffsetPage;
  organizationMember: Maybe<OrganizationMember>;
  organizationMembers: OrganizationMemberOffsetPage;
  organizations: OrganizationOffsetPage;
  organizerPromoCodes: Array<PromoCode>;
  overdueApprovalEvents: EventOffsetPage;
  overdueApprovalTimelines: ApprovalTimelineOffsetPage;
  ownershipTransfer: Maybe<OwnershipTransferRequest>;
  ownershipTransferByToken: Maybe<OwnershipTransferRequest>;
  ownershipTransfers: Array<OwnershipTransferRequest>;
  paymentAttempt: Maybe<PaymentAttempt>;
  paymentAttemptByAttemptNumber: Maybe<PaymentAttempt>;
  paymentAttemptByDepositId: Maybe<PaymentAttempt>;
  paymentAttemptCountByStatus: Scalars['Int']['output'];
  paymentAttemptSearch: PaymentAttemptOffsetPage;
  paymentAttempts: Array<PaymentAttempt>;
  paymentAttemptsByBuyer: Array<PaymentAttempt>;
  paymentAttemptsByEvent: Array<PaymentAttempt>;
  paymentAttemptsByReservation: Array<PaymentAttempt>;
  paymentAttemptsByStatus: Array<PaymentAttempt>;
  paymentRiskSummary: PaymentRiskSummary;
  payoutEligibility: PayoutEligibility;
  payoutRecoverySummary: PayoutRecoverySummary;
  payoutRequest: Maybe<PayoutRequest>;
  payoutRequestByRequestId: Maybe<PayoutRequest>;
  payoutRequestStats: PayoutRequestStats;
  payoutRequests: PayoutRequestOffsetPage;
  payoutRequestsByEvent: PayoutRequestOffsetPage;
  payoutRequestsByIssueType: PayoutRequestOffsetPage;
  payoutRequestsByOrganizer: PayoutRequestOffsetPage;
  payoutRequestsForReview: PayoutRequestOffsetPage;
  pendingApprovalEvents: EventOffsetPage;
  pendingApprovalTimelines: ApprovalTimelineOffsetPage;
  pendingChargebacks: Array<ChargebackRecord>;
  pendingInvitations: TeamInvitationOffsetPage;
  pendingJournalEntries: JournalEntryOffsetPage;
  pendingOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  pendingPaymentAttempts: Array<PaymentAttempt>;
  pendingPayoutRequests: PayoutRequestOffsetPage;
  pendingRecoveryProposals: Array<RecoveryProposal>;
  pendingRefundRequests: RefundRequestOffsetPage;
  pendingVerificationDocuments: Array<VerificationDocument>;
  permission: Maybe<Permission>;
  permissions: Array<Permission>;
  platformAccount: Maybe<PlatformAccount>;
  platformAccountByType: Maybe<PlatformAccount>;
  platformAccounts: Array<PlatformAccount>;
  platformConfiguration: PlatformConfiguration;
  platformRules: PlatformRules;
  platformSummary: PlatformSummary;
  popularCategories: Array<EventCategory>;
  promoCode: Maybe<PromoCode>;
  promoCodeByCode: Maybe<PromoCode>;
  province: Maybe<Province>;
  provinces: Array<Province>;
  publicPlatformRules: PublicPlatformRules;
  purchasesByDayAndHour: Array<PurchaseHeatCell>;
  recentCheckIns: Array<CheckIn>;
  recentlyResolvedPayoutRequests: PayoutRequestOffsetPage;
  recommendedEvents: Array<EventRecommendation>;
  reconciliationRun: Maybe<ReconciliationRun>;
  reconciliationRuns: ReconciliationRunOffsetPage;
  reconciliationRunsByType: ReconciliationRunOffsetPage;
  reconciliationRunsRequiringReview: Array<ReconciliationRun>;
  reconciliationSummary: ReconciliationSummary;
  /** All rows of a type. activeOnly=true (default) is the dropdown query. */
  referenceData: Array<ReferenceData>;
  /**
   * All rows of a type including inactive ones, paged — the admin management table.
   *
   * O-5 · this is ET-PLT-014 §4's `referenceDataAll`, and adopting that name resolves the
   * collision that held it back from D-19's de-suffixing: `referenceData` above already
   * exists as the bounded dropdown query, so the suffix could not simply be dropped. The
   * two are different operations, not two spellings of one — `referenceData` is public and
   * active-only, this is admin and shows everything.
   */
  referenceDataAll: ReferenceDataOffsetPage;
  /** Child rows within a hierarchy (active only), e.g. genres of a category. */
  referenceDataByParent: Array<ReferenceData>;
  /** A single reference item by type + code. */
  referenceItem: Maybe<ReferenceData>;
  /** The type registry that powers the generic admin management screen. */
  referenceTypes: Array<ReferenceTypeInfo>;
  refundRequest: Maybe<RefundRequest>;
  refundRequestByRequestId: Maybe<RefundRequest>;
  refundRequests: RefundRequestOffsetPage;
  refundRequestsByBuyer: RefundRequestOffsetPage;
  refundRequestsByEvent: RefundRequestOffsetPage;
  refundRequestsByOrganizer: RefundRequestOffsetPage;
  refundRequestsByTicket: Array<RefundRequest>;
  reservation: Maybe<TicketReservation>;
  reservationsByEvent: ReservationOffsetPage;
  retryablePayoutRequests: PayoutRequestOffsetPage;
  rolePermissions: Maybe<RolePermissions>;
  salesOverTime: Array<SalesPoint>;
  searchEvents: EventConnection;
  searchLocations: LocationConnection;
  searchTickets: TicketOffsetPage;
  serviceHealth: Array<ServiceHealth>;
  staffAccounts: UserOffsetPage;
  stockImages: StockImageConnection;
  stuckPayoutRequests: PayoutRequestOffsetPage;
  stuckTransactions: PaymentAttemptOffsetPage;
  successfulPaymentAttemptByReservation: Maybe<PaymentAttempt>;
  systemAlerts: Array<SystemAlert>;
  systemAnnouncements: Array<SystemAnnouncement>;
  ticket: Maybe<Ticket>;
  ticketByNumber: Maybe<Ticket>;
  ticketCountByBuyer: Scalars['Int']['output'];
  ticketCountByEvent: Scalars['Int']['output'];
  ticketHolderAudience: Scalars['Int']['output'];
  ticketHolderMessages: HolderMessagePage;
  ticketStats: TicketStats;
  ticketTier: Maybe<TicketTier>;
  ticketTierStatistics: Maybe<TicketTierStats>;
  ticketTransferChain: Array<TicketTransfer>;
  ticketsByBuyerCursorPagination: TicketConnection;
  ticketsByBuyerOffsetPagination: TicketOffsetPage;
  ticketsByEvent: TicketOffsetPage;
  ticketsByOrganizer: TicketOffsetPage;
  transactionStats: TransactionStats;
  transferRecipient: Maybe<TransferRecipient>;
  trendingEvents: Array<Event>;
  trialBalance: Array<AccountBalance>;
  unreadNotificationCount: Scalars['Int']['output'];
  user: Maybe<User>;
  userByEmail: Maybe<User>;
  userByPhone: Maybe<User>;
  userEventAccess: Maybe<EventAccessGrant>;
  userGrowthSeries: Array<GrowthPoint>;
  userStats: Maybe<UserStats>;
  users: UserOffsetPage;
  /**
   * Find all users who have a specific role.
   * Example: usersByRole(role: ORGANIZER) returns all users with ORGANIZER role.
   */
  usersByRole: UserOffsetPage;
  validatePromoCode: PromoCodeValidation;
  verificationDocument: Maybe<VerificationDocument>;
  verificationDocuments: Array<VerificationDocument>;
};


export type QueryAccountSummaryArgs = {
  accountId: Scalars['String']['input'];
};


export type QueryActiveEscalationsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryAllowedStatusTransitionsArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryApprovalEscalationArgs = {
  id: Scalars['ID']['input'];
};


export type QueryApprovalTimelineArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryApprovalTimelinesArgs = {
  filter: InputMaybe<ApprovalTimelineFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryApprovalTimelinesByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryApprovedNotPublishedEventsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryAuditLogsArgs = {
  filter: InputMaybe<AuditLogFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryAvailableTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryBankAccountArgs = {
  id: Scalars['ID']['input'];
};


export type QueryBankAccountsArgs = {
  filter: InputMaybe<PayoutAccountFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryBankAccountsByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
};


export type QueryBookingArgs = {
  id: Scalars['ID']['input'];
};


export type QueryBookingByNumberArgs = {
  bookingNumber: Scalars['String']['input'];
};


export type QueryBookingsByBuyerArgs = {
  buyerId: Scalars['String']['input'];
  filter: InputMaybe<BookingFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryBookingsByOrganizerArgs = {
  filter: InputMaybe<BookingFilterInput>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryCalculateRefundAmountArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryCancelledEventsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryChargebackArgs = {
  id: Scalars['ID']['input'];
};


export type QueryChargebackByChargebackIdArgs = {
  chargebackId: Scalars['String']['input'];
};


export type QueryChargebackStatsArgs = {
  eventId: InputMaybe<Scalars['String']['input']>;
  organizerId: InputMaybe<Scalars['String']['input']>;
};


export type QueryChargebacksArgs = {
  filter: InputMaybe<ChargebackFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryChargebacksByEventArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryChargebacksByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryChartOfAccountsByCodeArgs = {
  accountCode: Scalars['String']['input'];
};


export type QueryChartOfAccountsByTypeArgs = {
  accountType: AccountType;
};


export type QueryChartOfAccountsEntryArgs = {
  id: Scalars['ID']['input'];
};


export type QueryCheckInConflictsArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryCheckInSummaryArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryCitiesArgs = {
  provinceId: InputMaybe<Scalars['String']['input']>;
};


export type QueryCityArgs = {
  id: Scalars['ID']['input'];
};


export type QueryCommissionRecordsArgs = {
  filter: InputMaybe<CommissionFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryCompletedEventsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryDefaultBankAccountArgs = {
  organizerId: Scalars['String']['input'];
};


export type QueryDiscoverEventsArgs = {
  filter: EventDiscoveryFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
  sort?: InputMaybe<EventDiscoverySort>;
};


export type QueryDraftEventsArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEscrowAccountArgs = {
  id: Scalars['ID']['input'];
};


export type QueryEscrowAccountBalanceArgs = {
  accountId: Scalars['String']['input'];
};


export type QueryEscrowAccountByEventArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryEscrowAccountByNumberArgs = {
  accountNumber: Scalars['String']['input'];
};


export type QueryEscrowAccountsArgs = {
  filter: InputMaybe<EscrowAccountFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEscrowAccountsByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEscrowBalanceArgs = {
  escrowAccountId: Scalars['String']['input'];
};


export type QueryEscrowBalanceAsOfArgs = {
  asOf: Scalars['DateTime']['input'];
  escrowAccountId: Scalars['String']['input'];
};


export type QueryEscrowJournalVerificationArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryEscrowTransactionArgs = {
  id: Scalars['ID']['input'];
};


export type QueryEscrowTransactionsArgs = {
  escrowAccountId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEscrowTransactionsByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryEventArgs = {
  id: Scalars['ID']['input'];
};


export type QueryEventAccessGrantArgs = {
  id: Scalars['ID']['input'];
};


export type QueryEventAccessGrantsArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<AccessGrantStatus>;
};


export type QueryEventCategoryArgs = {
  id: Scalars['ID']['input'];
};


export type QueryEventCountByCategoryArgs = {
  categoryId: Scalars['String']['input'];
};


export type QueryEventCountByCityArgs = {
  city: Scalars['String']['input'];
};


export type QueryEventCountByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
};


export type QueryEventCountByStatusArgs = {
  status: EventStatus;
};


export type QueryEventLifecycleArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryEventLiveDashboardArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryEventPromoCodesArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryEventRefundSummaryArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryEventStatisticsArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryEventTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
  includeHidden?: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryEventsArgs = {
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEventsByCategoryArgs = {
  categoryId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsByCityArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsByStatusArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: EventStatus;
};


export type QueryExpiredReservationsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
  since: Scalars['DateTime']['input'];
};


export type QueryExportEventDataArgs = {
  eventId: Scalars['ID']['input'];
  format: ExportFormat;
};


export type QueryExportEventsReportArgs = {
  filter: EventFilterInput;
  format: ExportFormat;
};


export type QueryExportFinancialReportArgs = {
  filter: FinancialReportFilterInput;
  format: ExportFormat;
};


export type QueryExportSalesReportArgs = {
  eventId: Scalars['ID']['input'];
  format: ExportFormat;
};


export type QueryFailedPayoutRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryFinancialReportArgs = {
  filter: FinancialReportFilterInput;
};


export type QueryGatewaySettlementsArgs = {
  filter: InputMaybe<GatewaySettlementFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryHasPendingOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryHasSuccessfulPaymentArgs = {
  reservationId: Scalars['String']['input'];
};


export type QueryInvitationByTokenArgs = {
  token: Scalars['String']['input'];
};


export type QueryIsSlugAvailableArgs = {
  slug: Scalars['String']['input'];
};


export type QueryIsTicketEligibleForRefundArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryJournalEntriesArgs = {
  filter: InputMaybe<JournalEntryFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryJournalEntriesByAccountCodeArgs = {
  accountCode: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryJournalEntriesByCorrelationIdArgs = {
  correlationId: Scalars['String']['input'];
};


export type QueryJournalEntryArgs = {
  id: Scalars['ID']['input'];
};


export type QueryJournalEntryByNumberArgs = {
  entryNumber: Scalars['String']['input'];
};


export type QueryLatestPaymentAttemptByReservationArgs = {
  reservationId: Scalars['String']['input'];
};


export type QueryLocationArgs = {
  id: Scalars['ID']['input'];
};


export type QueryLocationsArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryLocationsByCityArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryLocationsByCountryArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryLocationsNearbyArgs = {
  input: NearbyLocationInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryMediaAssetsArgs = {
  filter: InputMaybe<MediaModerationFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyActiveReservationsArgs = {
  userId: Scalars['ID']['input'];
};


export type QueryMyBookingsArgs = {
  filter: InputMaybe<BookingFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyDraftEventsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyEffectivePermissionsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryMyEscalationsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyEscrowAccountsArgs = {
  organizationId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyEventAccessArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryMyEventCountByStatusArgs = {
  status: EventStatus;
};


export type QueryMyEventRemindersArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryMyEventsArgs = {
  filter: InputMaybe<OrganizerEventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyEventsConnectionArgs = {
  filter: InputMaybe<OrganizerEventFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryMyMediaArgs = {
  filter: InputMaybe<MediaFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryMyNotificationsArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<NotificationStatus>;
  type: InputMaybe<NotificationType>;
};


export type QueryMyOrganizationMembershipArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryMyPayoutRequestsArgs = {
  organizationId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<PayoutRequestStatus>;
};


export type QueryMyRecentActivityArgs = {
  limit: InputMaybe<Scalars['Int']['input']>;
};


export type QueryMyRefundRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyRevenueSeriesArgs = {
  months: InputMaybe<Scalars['Int']['input']>;
};


export type QueryMyTicketTransfersArgs = {
  direction: InputMaybe<TransferDirection>;
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<TicketTransferStatus>;
};


export type QueryMyTransactionsArgs = {
  filter: InputMaybe<OrganizerTransactionFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyUpcomingEventsArgs = {
  limit: InputMaybe<Scalars['Int']['input']>;
};


export type QueryMyVerificationDocumentByTypeArgs = {
  documentType: Scalars['String']['input'];
};


export type QueryMyVerificationDocumentsArgs = {
  status: InputMaybe<DocumentStatus>;
};


export type QueryOrganizationArgs = {
  id: Scalars['ID']['input'];
};


export type QueryOrganizationApplicationsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<OrganizationStatus>;
};


export type QueryOrganizationByOwnerIdArgs = {
  ownerId: Scalars['ID']['input'];
};


export type QueryOrganizationBySlugArgs = {
  slug: Scalars['String']['input'];
};


export type QueryOrganizationCountArgs = {
  status: InputMaybe<OrganizationStatus>;
};


export type QueryOrganizationEventAccessGrantsArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<AccessGrantStatus>;
};


export type QueryOrganizationMemberArgs = {
  organizationId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type QueryOrganizationMembersArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<OrganizationRole>;
  status: InputMaybe<MemberStatus>;
};


export type QueryOrganizationsArgs = {
  kybStatus: InputMaybe<KybStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
  search: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<OrganizationStatus>;
  verified: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryOrganizerPromoCodesArgs = {
  organizerId: Scalars['ID']['input'];
};


export type QueryOverdueApprovalEventsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryOverdueApprovalTimelinesArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryOwnershipTransferArgs = {
  id: Scalars['ID']['input'];
};


export type QueryOwnershipTransferByTokenArgs = {
  token: Scalars['String']['input'];
};


export type QueryOwnershipTransfersArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryPaymentAttemptArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPaymentAttemptByAttemptNumberArgs = {
  attemptNumber: Scalars['String']['input'];
};


export type QueryPaymentAttemptByDepositIdArgs = {
  depositId: Scalars['String']['input'];
};


export type QueryPaymentAttemptCountByStatusArgs = {
  status: PaymentAttemptStatus;
};


export type QueryPaymentAttemptSearchArgs = {
  filter: InputMaybe<PaymentAttemptFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPaymentAttemptsArgs = {
  intentId: Scalars['ID']['input'];
};


export type QueryPaymentAttemptsByBuyerArgs = {
  buyerId: Scalars['String']['input'];
};


export type QueryPaymentAttemptsByEventArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryPaymentAttemptsByReservationArgs = {
  reservationId: Scalars['String']['input'];
};


export type QueryPaymentAttemptsByStatusArgs = {
  status: PaymentAttemptStatus;
};


export type QueryPaymentRiskSummaryArgs = {
  windowHours?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryPayoutEligibilityArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryPayoutRequestArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPayoutRequestByRequestIdArgs = {
  requestId: Scalars['String']['input'];
};


export type QueryPayoutRequestStatsArgs = {
  organizerId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryPayoutRequestsArgs = {
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsByEventArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsByIssueTypeArgs = {
  issueType: PayoutIssueType;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsForReviewArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  reviewStatus: InputMaybe<PayoutReviewStatus>;
};


export type QueryPendingApprovalEventsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingApprovalTimelinesArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingInvitationsArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingJournalEntriesArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryPendingPayoutRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingRefundRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPermissionArgs = {
  code: Scalars['String']['input'];
};


export type QueryPlatformAccountArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPlatformAccountByTypeArgs = {
  accountType: PlatformAccountType;
};


export type QueryPopularCategoriesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryPromoCodeArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPromoCodeByCodeArgs = {
  code: Scalars['String']['input'];
};


export type QueryProvinceArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPurchasesByDayAndHourArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  from: InputMaybe<Scalars['DateTime']['input']>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
  to: InputMaybe<Scalars['DateTime']['input']>;
};


export type QueryRecentCheckInsArgs = {
  eventId: Scalars['ID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryRecentlyResolvedPayoutRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRecommendedEventsArgs = {
  basedOnEventIds: InputMaybe<Array<Scalars['ID']['input']>>;
  first?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryReconciliationRunArgs = {
  id: Scalars['ID']['input'];
};


export type QueryReconciliationRunsArgs = {
  filter: InputMaybe<ReconciliationFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryReconciliationRunsByTypeArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  type: ReconciliationType;
};


export type QueryReconciliationSummaryArgs = {
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  type: InputMaybe<ReconciliationType>;
};


export type QueryReferenceDataArgs = {
  activeOnly?: InputMaybe<Scalars['Boolean']['input']>;
  type: ReferenceType;
};


export type QueryReferenceDataAllArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  type: ReferenceType;
};


export type QueryReferenceDataByParentArgs = {
  parentCode: Scalars['String']['input'];
  type: ReferenceType;
};


export type QueryReferenceItemArgs = {
  code: Scalars['String']['input'];
  type: ReferenceType;
};


export type QueryRefundRequestArgs = {
  id: Scalars['ID']['input'];
};


export type QueryRefundRequestByRequestIdArgs = {
  requestId: Scalars['String']['input'];
};


export type QueryRefundRequestsArgs = {
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRefundRequestsByBuyerArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRefundRequestsByEventArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRefundRequestsByOrganizerArgs = {
  filter: InputMaybe<RefundRequestFilterInput>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRefundRequestsByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryReservationArgs = {
  id: Scalars['ID']['input'];
};


export type QueryReservationsByEventArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRetryablePayoutRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRolePermissionsArgs = {
  role: Scalars['String']['input'];
};


export type QuerySalesOverTimeArgs = {
  bucket?: InputMaybe<SalesBucket>;
  eventId: Scalars['ID']['input'];
  from: InputMaybe<Scalars['DateTime']['input']>;
  to: InputMaybe<Scalars['DateTime']['input']>;
};


export type QuerySearchEventsArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchLocationsArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchTicketsArgs = {
  filter: TicketFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryStaffAccountsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<UserType>;
  search: InputMaybe<Scalars['String']['input']>;
};


export type QueryStockImagesArgs = {
  filter: InputMaybe<StockImageFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryStuckPayoutRequestsArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryStuckTransactionsArgs = {
  minutes?: InputMaybe<Scalars['Int']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QuerySuccessfulPaymentAttemptByReservationArgs = {
  reservationId: Scalars['String']['input'];
};


export type QuerySystemAlertsArgs = {
  severity: InputMaybe<AlertSeverity>;
  status: InputMaybe<AlertStatus>;
};


export type QueryTicketArgs = {
  id: Scalars['ID']['input'];
};


export type QueryTicketByNumberArgs = {
  ticketNumber: Scalars['String']['input'];
};


export type QueryTicketCountByBuyerArgs = {
  buyerId: Scalars['String']['input'];
};


export type QueryTicketCountByEventArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryTicketHolderAudienceArgs = {
  eventId: Scalars['ID']['input'];
  segment?: InputMaybe<HolderSegment>;
  ticketTierId: InputMaybe<Scalars['String']['input']>;
};


export type QueryTicketHolderMessagesArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryTicketStatsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTicketTierArgs = {
  id: Scalars['ID']['input'];
};


export type QueryTicketTierStatisticsArgs = {
  eventId: Scalars['ID']['input'];
  tierId: Scalars['ID']['input'];
};


export type QueryTicketTransferChainArgs = {
  ticketId: Scalars['ID']['input'];
};


export type QueryTicketsByBuyerCursorPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<TicketStatus>;
};


export type QueryTicketsByBuyerOffsetPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<TicketStatus>;
};


export type QueryTicketsByEventArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryTicketsByOrganizerArgs = {
  filter: InputMaybe<TicketFilterInput>;
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryTransactionStatsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizerId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTransferRecipientArgs = {
  channel: TransferChannel;
  value: Scalars['String']['input'];
};


export type QueryTrendingEventsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryTrialBalanceArgs = {
  asOf: InputMaybe<Scalars['DateTime']['input']>;
};


export type QueryUserArgs = {
  id: Scalars['ID']['input'];
};


export type QueryUserByEmailArgs = {
  email: Scalars['String']['input'];
};


export type QueryUserByPhoneArgs = {
  phoneNumber: Scalars['String']['input'];
};


export type QueryUserEventAccessArgs = {
  eventId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type QueryUserGrowthSeriesArgs = {
  bucket?: InputMaybe<GrowthBucket>;
  from: Scalars['DateTime']['input'];
  role: InputMaybe<UserType>;
  to: Scalars['DateTime']['input'];
};


export type QueryUsersArgs = {
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<UserType>;
  search: InputMaybe<Scalars['String']['input']>;
};


export type QueryUsersByRoleArgs = {
  activeOnly?: InputMaybe<Scalars['Boolean']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
  role: UserType;
};


export type QueryValidatePromoCodeArgs = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  code: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


export type QueryVerificationDocumentArgs = {
  id: Scalars['ID']['input'];
};


export type QueryVerificationDocumentsArgs = {
  organizationId: Scalars['ID']['input'];
  status: InputMaybe<DocumentStatus>;
};

export type ReceiveChargebackInput = {
  chargebackAmount: Scalars['BigDecimal']['input'];
  chargebackFee: Scalars['BigDecimal']['input'];
  chargebackId: Scalars['String']['input'];
  currency: Scalars['String']['input'];
  customerId: Scalars['String']['input'];
  eventId: Scalars['String']['input'];
  organizationId?: InputMaybe<Scalars['String']['input']>;
  organizerId: Scalars['String']['input'];
  originalAmount: Scalars['BigDecimal']['input'];
  originalTransactionId: Scalars['String']['input'];
  reason: ChargebackReason;
  responseDeadline: Scalars['DateTime']['input'];
  ticketId: Scalars['String']['input'];
};

export type RecommendationReason =
  | 'BECAUSE_YOU_BOOKED'
  | 'TRENDING';

export type ReconciliationFilterInput = {
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  status?: InputMaybe<ReconciliationStatus>;
  type?: InputMaybe<ReconciliationType>;
};

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

export type ReconciliationItemStatus =
  | 'AMOUNT_MISMATCH'
  | 'MATCHED'
  | 'UNMATCHED_EXTERNAL'
  | 'UNMATCHED_INTERNAL';

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

export type ReconciliationStatus =
  | 'COMPLETED'
  | 'FAILED'
  | 'REQUIRES_REVIEW'
  | 'RUNNING';

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

export type RecoveryAction =
  | 'FORCE_COMPLETE_PAYMENT_ATTEMPTS'
  | 'TRANSFER_PLATFORM_FUNDS'
  | 'WRITE_OFF_CHARGEBACK';

export type RecoveryProposal = {
  __typename: 'RecoveryProposal';
  action: RecoveryAction;
  amount: Maybe<Scalars['BigDecimal']['output']>;
  canConfirm: Scalars['Boolean']['output'];
  confirmationReason: Maybe<Scalars['String']['output']>;
  confirmedAt: Maybe<Scalars['DateTime']['output']>;
  confirmedById: Maybe<Scalars['String']['output']>;
  expiresAt: Scalars['DateTime']['output'];
  failureReason: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  outcome: Maybe<Scalars['String']['output']>;
  parameters: Maybe<Scalars['JSON']['output']>;
  proposalReason: Scalars['String']['output'];
  proposedAt: Scalars['DateTime']['output'];
  proposedById: Scalars['String']['output'];
  status: RecoveryProposalStatus;
  subjectIds: Array<Scalars['String']['output']>;
  subjectType: Scalars['String']['output'];
};

export type RecoveryProposalStatus =
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'FAILED'
  | 'PENDING'
  | 'WITHDRAWN';

export type RecoveryStatus =
  | 'IN_PROGRESS'
  | 'NOT_STARTED'
  | 'RECOVERED'
  | 'WRITTEN_OFF';

/** A single reference/catalog row. */
export type ReferenceData = {
  __typename: 'ReferenceData';
  allowedTransitions: Array<Scalars['String']['output']>;
  code: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  description: Maybe<Scalars['String']['output']>;
  displayOrder: Scalars['Int']['output'];
  effectiveFrom: Maybe<Scalars['DateTime']['output']>;
  effectiveTo: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  isSystem: Scalars['Boolean']['output'];
  metadata: Maybe<Scalars['JSON']['output']>;
  name: Scalars['String']['output'];
  parentCode: Maybe<Scalars['String']['output']>;
  parentType: Maybe<ReferenceType>;
  semantic: Maybe<WorkflowSemantic>;
  type: ReferenceType;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  updatedBy: Maybe<Scalars['String']['output']>;
};

export type ReferenceDataOffsetPage = {
  __typename: 'ReferenceDataOffsetPage';
  content: Array<ReferenceData>;
  hasNext: Scalars['Boolean']['output'];
  hasPrevious: Scalars['Boolean']['output'];
  pageNumber: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalElements: Scalars['Int']['output'];
  totalPages: Scalars['Int']['output'];
};

/** Discriminator for the polymorphic reference_data collection. Adding a value is a backend change. */
export type ReferenceType =
  | 'AGE_RESTRICTION'
  | 'BANK'
  | 'BUSINESS_TYPE'
  | 'CANCELLATION_REASON'
  | 'CARD_SCHEME'
  | 'CHARGEBACK_STATUS'
  | 'CITY'
  | 'COUNTRY'
  | 'CURRENCY'
  | 'ESCROW_STATUS'
  | 'EVENT_CATEGORY'
  | 'EVENT_ROLE'
  | 'EVENT_STATUS'
  | 'EVENT_TYPE'
  | 'KYB_DOCUMENT_TYPE'
  | 'LANGUAGE'
  | 'MOBILE_MONEY_OPERATOR'
  | 'MUSIC_GENRE'
  | 'NOTIFICATION_CATEGORY'
  | 'NOTIFICATION_CHANNEL'
  | 'ORGANIZATION_ROLE'
  | 'ORGANIZATION_STATUS'
  | 'ORGANIZER_TYPE'
  | 'PAYMENT_STATUS'
  | 'PAYOUT_STATUS'
  | 'PROVINCE'
  | 'REFUND_REASON'
  | 'REFUND_STATUS'
  | 'REJECTION_REASON'
  | 'REPORT_PERIOD'
  | 'RESERVATION_STATUS'
  | 'TAX_RATE'
  | 'TEAM_INVITATION_STATUS'
  | 'TICKET_STATUS'
  | 'TICKET_TIER_CATEGORY'
  | 'TIMEZONE'
  | 'VERIFICATION_DOCUMENT_STATUS';

/** Describes a ReferenceType for the admin type picker (label, group, metadata form fields). */
export type ReferenceTypeInfo = {
  __typename: 'ReferenceTypeInfo';
  group: Scalars['String']['output'];
  groupLabel: Scalars['String']['output'];
  label: Scalars['String']['output'];
  requiredMetadataKeys: Array<Scalars['String']['output']>;
  type: ReferenceType;
};

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

export type RefundRequest = {
  __typename: 'RefundRequest';
  additionalNotes: Maybe<Scalars['String']['output']>;
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
  requestedById: Maybe<Scalars['String']['output']>;
  reviewComments: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  status: RefundRequestStatus;
  ticketId: Scalars['String']['output'];
  ticketNumber: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type RefundRequestFilterInput = {
  buyerId?: InputMaybe<Scalars['String']['input']>;
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  requestType?: InputMaybe<RefundRequestType>;
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  status?: InputMaybe<RefundRequestStatus>;
  ticketId?: InputMaybe<Scalars['String']['input']>;
};

export type RefundRequestOffsetPage = {
  __typename: 'RefundRequestOffsetPage';
  data: Array<RefundRequest>;
  pagination: PaginationInfo;
};

export type RefundRequestStatus =
  | 'APPROVED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'FAILED'
  | 'PENDING'
  | 'PROCESSING'
  | 'REJECTED';

export type RefundRequestType =
  | 'ADMIN_INITIATED'
  | 'EVENT_CANCELLED'
  | 'FULL'
  | 'PARTIAL'
  | 'SYSTEM_AUTOMATIC'
  | 'TICKET_EXPIRED'
  | 'USER_REQUESTED';

export type RefundStatusSummary = {
  __typename: 'RefundStatusSummary';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: RefundRequestStatus;
  totalAmount: Scalars['BigDecimal']['output'];
};

export type RefundSummary = {
  __typename: 'RefundSummary';
  averageRefundAmount: Maybe<Scalars['BigDecimal']['output']>;
  currency: Scalars['String']['output'];
  refundsByStatus: Maybe<Array<RefundStatusSummary>>;
  refundsByType: Maybe<Array<RefundTypeSummary>>;
  totalAmount: Scalars['BigDecimal']['output'];
  totalRefunds: Scalars['Int']['output'];
};

export type RefundTypeSummary = {
  __typename: 'RefundTypeSummary';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  requestType: RefundRequestType;
  totalAmount: Scalars['BigDecimal']['output'];
};

export type RegisterDeviceInput = {
  appVersion?: InputMaybe<Scalars['String']['input']>;
  deviceModel?: InputMaybe<Scalars['String']['input']>;
  deviceName?: InputMaybe<Scalars['String']['input']>;
  deviceToken: Scalars['String']['input'];
  osVersion?: InputMaybe<Scalars['String']['input']>;
  platform: DevicePlatform;
};

export type RejectOrganizationInput = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
  reviewNotes?: InputMaybe<Scalars['String']['input']>;
};

export type ReminderStatus =
  | 'CANCELLED'
  | 'FAILED'
  | 'SCHEDULED'
  | 'SENT';

export type RemoveMemberInput = {
  memberId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason?: InputMaybe<Scalars['String']['input']>;
};

export type ReportExport = {
  __typename: 'ReportExport';
  downloadUrl: Maybe<Scalars['String']['output']>;
  errorMessage: Maybe<Scalars['String']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  fileName: Maybe<Scalars['String']['output']>;
  format: ExportFormat;
  generatedAt: Scalars['DateTime']['output'];
};

export type RequestContactAddInput = {
  regionHint?: InputMaybe<Scalars['String']['input']>;
  type: ContactType;
  value: Scalars['String']['input'];
};

export type RequestContactChangeInput = {
  contactId: Scalars['ID']['input'];
  regionHint?: InputMaybe<Scalars['String']['input']>;
  type: ContactType;
  value: Scalars['String']['input'];
};

export type RequestOrganizationChangesInput = {
  changesRequired: Scalars['String']['input'];
  organizationId: Scalars['ID']['input'];
  reviewNotes?: InputMaybe<Scalars['String']['input']>;
};

export type RequestUploadUrlInput = {
  documentType: Scalars['String']['input'];
  fileName: Scalars['String']['input'];
  fileSize: Scalars['Long']['input'];
  mimeType: Scalars['String']['input'];
};

export type RescheduleEventInput = {
  eventId: Scalars['ID']['input'];
  newStartsAt: Scalars['DateTime']['input'];
  reason: Scalars['String']['input'];
};

export type ResendContactCodeInput = {
  challengeId?: InputMaybe<Scalars['ID']['input']>;
  changeId?: InputMaybe<Scalars['ID']['input']>;
  target?: InputMaybe<ContactCodeTarget>;
};

export type ResendTicketResult = {
  __typename: 'ResendTicketResult';
  channel: Maybe<Scalars['String']['output']>;
  destination: Maybe<Scalars['String']['output']>;
  status: Scalars['String']['output'];
  ticketId: Scalars['ID']['output'];
  ticketNumber: Scalars['String']['output'];
};

export type ReservationItem = {
  __typename: 'ReservationItem';
  quantity: Scalars['Int']['output'];
  subtotal: Scalars['BigDecimal']['output'];
  ticketTierId: Scalars['String']['output'];
  tierName: Scalars['String']['output'];
  unitPrice: Scalars['BigDecimal']['output'];
};

export type ReservationOffsetPage = {
  __typename: 'ReservationOffsetPage';
  data: Array<TicketReservation>;
  pagination: PaginationInfo;
};

export type ReservationStatus =
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'FAILED'
  | 'HELD'
  | 'RELEASED';

export type ReserveTicketsInput = {
  contactEmail?: InputMaybe<Scalars['String']['input']>;
  contactName?: InputMaybe<Scalars['String']['input']>;
  contactPhone?: InputMaybe<Scalars['PhoneNumber']['input']>;
  eventId: Scalars['ID']['input'];
  idempotencyKey: Scalars['String']['input'];
  promoCode?: InputMaybe<Scalars['String']['input']>;
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
  reason?: InputMaybe<Scalars['String']['input']>;
};

export type RiskFlagCount = {
  __typename: 'RiskFlagCount';
  count: Scalars['Int']['output'];
  flag: Scalars['String']['output'];
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

export type RolePermissions = {
  __typename: 'RolePermissions';
  permissions: Array<Permission>;
  role: Scalars['String']['output'];
  scope: PermissionScope;
  switchable: Array<Permission>;
};

export type RulesRefundPolicy = {
  __typename: 'RulesRefundPolicy';
  code: Scalars['String']['output'];
  label: Scalars['String']['output'];
  rules: Array<RulesRefundTier>;
  summary: Scalars['String']['output'];
};

export type RulesRefundTier = {
  __typename: 'RulesRefundTier';
  daysBefore: Scalars['Int']['output'];
  percent: Scalars['Int']['output'];
};

export type RunningOrderItem = {
  __typename: 'RunningOrderItem';
  time: Scalars['String']['output'];
  title: Scalars['String']['output'];
};

export type RunningOrderItemInput = {
  time: Scalars['String']['input'];
  title: Scalars['String']['input'];
};

export type SalesBucket =
  | 'DAY'
  | 'HOUR'
  | 'WEEK';

export type SalesPoint = {
  __typename: 'SalesPoint';
  bucketEnd: Scalars['DateTime']['output'];
  bucketStart: Scalars['DateTime']['output'];
  grossRevenue: Scalars['BigDecimal']['output'];
  netRevenue: Scalars['BigDecimal']['output'];
  orders: Scalars['Int']['output'];
  refundedAmount: Scalars['BigDecimal']['output'];
  tickets: Scalars['Int']['output'];
};

export type ServiceHealth = {
  __typename: 'ServiceHealth';
  checkedAt: Scalars['DateTime']['output'];
  detail: Maybe<Scalars['String']['output']>;
  latencyMillis: Scalars['Long']['output'];
  name: Scalars['String']['output'];
  status: ServiceStatus;
};

export type ServiceStatus =
  | 'DOWN'
  | 'UP';

/**
 * Set bank account for payouts.
 * SECURITY:
 * - Account numbers are encrypted at rest (AES-256-GCM)
 * - Only last 4 digits shown in responses
 * - All changes are audit logged
 * - Only OWNER can modify
 */
export type SetBankAccountInput = {
  /**
   * Account holder name (must match business/individual name).
   * SECURITY: Validated to prevent injection attacks (letters, spaces, hyphens, apostrophes only).
   */
  accountHolderName: Scalars['String']['input'];
  /**
   * Bank account number (10-16 digits for Zambian banks).
   * SECURITY: Validated, sanitized, and encrypted before storage.
   */
  accountNumber: Scalars['String']['input'];
  /** Account type (CHECKING, SAVINGS, BUSINESS) */
  accountType?: InputMaybe<Scalars['String']['input']>;
  /** SWIFT/BIC code (8 or 11 characters, alphanumeric) */
  bankCode: Scalars['String']['input'];
  /** Bank name (e.g., Zanaco, FNB, Standard Chartered) */
  bankName: Scalars['String']['input'];
  /** Branch code */
  branchCode?: InputMaybe<Scalars['String']['input']>;
  /** Branch name */
  branchName?: InputMaybe<Scalars['String']['input']>;
};

export type SetEventReminderInput = {
  eventStartsAt: Scalars['DateTime']['input'];
  ticketId: Scalars['ID']['input'];
};

/**
 * Set mobile money account for payouts.
 * SECURITY:
 * - Phone numbers validated in E.164 format
 * - Masked for display (show prefix + last 4 digits)
 * - All changes are audit logged
 * - Only OWNER can modify
 */
export type SetMobileMoneyAccountInput = {
  /**
   * Account holder name (must match business/individual name).
   * SECURITY: Validated to prevent injection attacks.
   */
  accountHolderName: Scalars['String']['input'];
  /**
   * Phone number in E.164 format (e.g., +260971234567).
   * Normalized + validated by the PhoneNumber scalar (libphonenumber).
   */
  phoneNumber: Scalars['PhoneNumber']['input'];
  /** Mobile money provider (MTN, AIRTEL, ZAMTEL) */
  provider: MobileMoneyProvider;
};

export type SetPrimaryContactInput = {
  challengeId: Scalars['ID']['input'];
  code: Scalars['String']['input'];
  contactId: Scalars['ID']['input'];
};

export type SocialConnection = {
  __typename: 'SocialConnection';
  connectedAt: Scalars['DateTime']['output'];
  email: Maybe<Scalars['String']['output']>;
  name: Maybe<Scalars['String']['output']>;
  provider: SocialProvider;
  providerId: Scalars['String']['output'];
};

export type SocialLinks = {
  __typename: 'SocialLinks';
  facebook: Maybe<Scalars['String']['output']>;
  instagram: Maybe<Scalars['String']['output']>;
  linkedin: Maybe<Scalars['String']['output']>;
  tiktok: Maybe<Scalars['String']['output']>;
  twitter: Maybe<Scalars['String']['output']>;
  youtube: Maybe<Scalars['String']['output']>;
};

export type SocialLinksInput = {
  facebook?: InputMaybe<Scalars['String']['input']>;
  instagram?: InputMaybe<Scalars['String']['input']>;
  linkedin?: InputMaybe<Scalars['String']['input']>;
  tiktok?: InputMaybe<Scalars['String']['input']>;
  twitter?: InputMaybe<Scalars['String']['input']>;
  youtube?: InputMaybe<Scalars['String']['input']>;
};

export type SocialProvider =
  | 'APPLE'
  | 'FACEBOOK'
  | 'GOOGLE'
  | 'TWITTER';

export type SortDirection =
  | 'ASC'
  | 'DESC';

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
  dataSource?: InputMaybe<Scalars['String']['input']>;
  /**
   * For ESCROW_JOURNAL reconciliation only:
   * If true, includes CLOSED and CANCELLED accounts in verification.
   * Default is false (only verifies OPEN accounts for better performance).
   * Use true for full audit purposes.
   */
  includeClosed?: InputMaybe<Scalars['Boolean']['input']>;
  reconciliationDate: Scalars['DateTime']['input'];
  type: ReconciliationType;
};

export type StatusTransition = {
  __typename: 'StatusTransition';
  fromStatus: Maybe<EventStatus>;
  metadata: Maybe<Scalars['JSON']['output']>;
  reason: Maybe<Scalars['String']['output']>;
  toStatus: EventStatus;
  transitionedAt: Scalars['DateTime']['output'];
  transitionedBy: Maybe<Scalars['String']['output']>;
};

export type StockImage = {
  __typename: 'StockImage';
  active: Scalars['Boolean']['output'];
  altText: Maybe<Scalars['String']['output']>;
  categoryCode: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  purpose: StockImagePurpose;
  title: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  url: Scalars['String']['output'];
};

export type StockImageConnection = {
  __typename: 'StockImageConnection';
  edges: Array<StockImageEdge>;
  pageInfo: PageInfo;
};

export type StockImageEdge = {
  __typename: 'StockImageEdge';
  cursor: Scalars['String']['output'];
  node: StockImage;
};

export type StockImageFilterInput = {
  categoryCode?: InputMaybe<Scalars['String']['input']>;
  includeInactive?: InputMaybe<Scalars['Boolean']['input']>;
  purpose?: InputMaybe<StockImagePurpose>;
};

export type StockImagePurpose =
  | 'CATEGORY_TILE'
  | 'EVENT_COVER';

export type SuspendOrganizationInput = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};

export type SystemAlert = {
  __typename: 'SystemAlert';
  acknowledgedAt: Maybe<Scalars['DateTime']['output']>;
  acknowledgedBy: Maybe<Scalars['ID']['output']>;
  id: Scalars['ID']['output'];
  key: Scalars['String']['output'];
  lastSeenAt: Maybe<Scalars['DateTime']['output']>;
  message: Maybe<Scalars['String']['output']>;
  occurrences: Scalars['Int']['output'];
  raisedAt: Scalars['DateTime']['output'];
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  severity: AlertSeverity;
  source: Scalars['String']['output'];
  status: AlertStatus;
  title: Scalars['String']['output'];
};

export type SystemAnnouncement = {
  __typename: 'SystemAnnouncement';
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Scalars['DateTime']['output'];
  endsAt: Maybe<Scalars['DateTime']['output']>;
  id: Scalars['ID']['output'];
  message: Scalars['String']['output'];
  segment: AnnouncementSegment;
  severity: AlertSeverity;
  startsAt: Scalars['DateTime']['output'];
  title: Scalars['String']['output'];
};

export type TeamInvitation = {
  __typename: 'TeamInvitation';
  acceptedAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Scalars['DateTime']['output'];
  declinedAt: Maybe<Scalars['DateTime']['output']>;
  email: Maybe<Scalars['String']['output']>;
  eventAccessGrants: Maybe<Array<EventAccessProposal>>;
  expiresAt: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  invitationToken: Scalars['String']['output'];
  invitedBy: Maybe<User>;
  invitedById: Scalars['ID']['output'];
  inviteeName: Maybe<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  organization: Maybe<Organization>;
  organizationId: Scalars['ID']['output'];
  phoneNumber: Maybe<Scalars['String']['output']>;
  proposedRole: OrganizationRole;
  status: InvitationStatus;
};

export type TeamInvitationOffsetPage = {
  __typename: 'TeamInvitationOffsetPage';
  content: Array<TeamInvitation>;
  pageInfo: PageInfo;
};

export type Ticket = {
  __typename: 'Ticket';
  barcode: Maybe<Scalars['String']['output']>;
  bookingId: Maybe<Scalars['String']['output']>;
  bookingNumber: Maybe<Scalars['String']['output']>;
  buyer: User;
  buyerEmail: Maybe<Scalars['String']['output']>;
  buyerId: Scalars['String']['output'];
  buyerName: Maybe<Scalars['String']['output']>;
  buyerPhone: Maybe<Scalars['String']['output']>;
  cancellationReason: Maybe<Scalars['String']['output']>;
  cancelledAt: Maybe<Scalars['DateTime']['output']>;
  commissionAmount: Maybe<Scalars['BigDecimal']['output']>;
  commissionRate: Maybe<Scalars['BigDecimal']['output']>;
  correlationId: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  event: Event;
  eventDate: Maybe<Scalars['String']['output']>;
  eventId: Scalars['String']['output'];
  eventLocationAddress: Maybe<Scalars['String']['output']>;
  eventLocationName: Maybe<Scalars['String']['output']>;
  eventTitle: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  netAmount: Maybe<Scalars['BigDecimal']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Maybe<Scalars['String']['output']>;
  paymentInfo: Maybe<PaymentInfo>;
  paymentReference: Maybe<Scalars['String']['output']>;
  price: Scalars['BigDecimal']['output'];
  purchaseDate: Maybe<Scalars['DateTime']['output']>;
  qrCode: Maybe<Scalars['String']['output']>;
  quantity: Maybe<Scalars['Int']['output']>;
  refundInfo: Maybe<RefundInfo>;
  refundReason: Maybe<Scalars['String']['output']>;
  refundableAmount: Maybe<Scalars['BigDecimal']['output']>;
  refundedAmount: Maybe<Scalars['BigDecimal']['output']>;
  refundedAt: Maybe<Scalars['DateTime']['output']>;
  status: TicketStatus;
  ticketCategory: Maybe<TicketCategory>;
  ticketCategoryCode: Maybe<Scalars['String']['output']>;
  ticketCategoryName: Maybe<Scalars['String']['output']>;
  ticketNumber: Scalars['String']['output'];
  transferCount: Scalars['Int']['output'];
  transferPending: Scalars['Boolean']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  usedAt: Maybe<Scalars['DateTime']['output']>;
  validFrom: Maybe<Scalars['DateTime']['output']>;
  validUntil: Maybe<Scalars['DateTime']['output']>;
  validatedAt: Maybe<Scalars['DateTime']['output']>;
  validatedBy: Maybe<Scalars['String']['output']>;
};

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

export type TicketCategoryStats = {
  __typename: 'TicketCategoryStats';
  category: Scalars['String']['output'];
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  totalRevenue: Maybe<Scalars['BigDecimal']['output']>;
};

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
  buyerId?: InputMaybe<Scalars['String']['input']>;
  category?: InputMaybe<Scalars['String']['input']>;
  eventId?: InputMaybe<Scalars['String']['input']>;
  organizerId?: InputMaybe<Scalars['String']['input']>;
  purchaseDateAfter?: InputMaybe<Scalars['DateTime']['input']>;
  purchaseDateBefore?: InputMaybe<Scalars['DateTime']['input']>;
  searchQuery?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<TicketStatus>;
  statuses?: InputMaybe<Array<TicketStatus>>;
};

export type TicketOffsetPage = {
  __typename: 'TicketOffsetPage';
  data: Array<Ticket>;
  pagination: PaginationInfo;
};

export type TicketReservation = {
  __typename: 'TicketReservation';
  confirmedAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Scalars['DateTime']['output'];
  currency: Scalars['String']['output'];
  discountAmount: Maybe<Scalars['BigDecimal']['output']>;
  eventId: Scalars['ID']['output'];
  expiresAt: Scalars['DateTime']['output'];
  failedAt: Maybe<Scalars['DateTime']['output']>;
  failureReason: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  items: Array<ReservationItem>;
  paymentIntentId: Maybe<Scalars['ID']['output']>;
  promoCodeApplied: Maybe<Scalars['String']['output']>;
  releasedAt: Maybe<Scalars['DateTime']['output']>;
  remainingSeconds: Maybe<Scalars['Int']['output']>;
  status: ReservationStatus;
  subtotal: Maybe<Scalars['BigDecimal']['output']>;
  totalAmount: Scalars['BigDecimal']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
};

export type TicketSelectionInput = {
  quantity: Scalars['Int']['input'];
  ticketTierId: Scalars['String']['input'];
};

export type TicketStats = {
  __typename: 'TicketStats';
  cancelledTickets: Scalars['Int']['output'];
  expiredTickets: Scalars['Int']['output'];
  issuedTickets: Scalars['Int']['output'];
  recentTickets: Maybe<Array<Ticket>>;
  refundPendingTickets: Scalars['Int']['output'];
  refundedTickets: Scalars['Int']['output'];
  ticketsByCategory: Maybe<Array<TicketCategoryStats>>;
  ticketsByStatus: Maybe<Array<TicketStatusStats>>;
  totalTickets: Scalars['Int']['output'];
  validatedTickets: Scalars['Int']['output'];
};

export type TicketStatus =
  | 'CANCELLED'
  | 'EXPIRED'
  | 'ISSUED'
  | 'REFUNDED'
  | 'REFUND_PENDING'
  | 'TRANSFERRED'
  | 'VALIDATED';

export type TicketStatusStats = {
  __typename: 'TicketStatusStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: TicketStatus;
};

export type TicketTier = {
  __typename: 'TicketTier';
  accessCode: Maybe<Scalars['String']['output']>;
  availableQuantity: Scalars['Int']['output'];
  benefits: Maybe<Array<Scalars['String']['output']>>;
  category: TicketCategory;
  code: Scalars['String']['output'];
  createdAt: Maybe<Scalars['DateTime']['output']>;
  currency: Scalars['String']['output'];
  description: Maybe<Scalars['String']['output']>;
  earlyBirdEndsAt: Maybe<Scalars['DateTime']['output']>;
  earlyBirdPrice: Maybe<Scalars['BigDecimal']['output']>;
  eventId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  isHidden: Scalars['Boolean']['output'];
  maxPerOrder: Maybe<Scalars['Int']['output']>;
  minPerOrder: Maybe<Scalars['Int']['output']>;
  name: Scalars['String']['output'];
  organizationId: Maybe<Scalars['String']['output']>;
  originalPrice: Maybe<Scalars['BigDecimal']['output']>;
  price: Scalars['BigDecimal']['output'];
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

export type TicketTransfer = {
  __typename: 'TicketTransfer';
  bookingNumber: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  direction: Maybe<TransferDirection>;
  eventId: Scalars['String']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  expiresAt: Maybe<Scalars['DateTime']['output']>;
  fromDisplayName: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  note: Maybe<Scalars['String']['output']>;
  recipientMasked: Maybe<Scalars['String']['output']>;
  resolvedAt: Maybe<Scalars['DateTime']['output']>;
  status: TicketTransferStatus;
  ticketId: Scalars['ID']['output'];
  ticketNumber: Scalars['String']['output'];
  toDisplayName: Maybe<Scalars['String']['output']>;
};

export type TicketTransferPage = {
  __typename: 'TicketTransferPage';
  data: Array<TicketTransfer>;
  pagination: PaginationInfo;
};

export type TicketTransferStatus =
  | 'ACCEPTED'
  | 'CANCELLED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'PENDING';

export type TierCheckInStats = {
  __typename: 'TierCheckInStats';
  checkInRate: Scalars['Float']['output'];
  checkedIn: Scalars['Int']['output'];
  sold: Scalars['Int']['output'];
  tierId: Scalars['String']['output'];
  tierName: Scalars['String']['output'];
};

export type TimeUnit =
  | 'DAY'
  | 'HOUR'
  | 'MONTH'
  | 'WEEK';

export type TimelineEvent = {
  __typename: 'TimelineEvent';
  action: ApprovalAction;
  actorId: Scalars['String']['output'];
  actorName: Scalars['String']['output'];
  actorRole: Maybe<Scalars['String']['output']>;
  comments: Maybe<Scalars['String']['output']>;
  description: Scalars['String']['output'];
  eventId: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  internalNotes: Maybe<Scalars['String']['output']>;
  isEscalationRelated: Scalars['Boolean']['output'];
  metadata: Maybe<Scalars['JSON']['output']>;
  newStatus: Maybe<EventStatus>;
  previousStatus: Maybe<EventStatus>;
  timestamp: Scalars['DateTime']['output'];
};

export type TransactionIssueType =
  | 'AMOUNT_MISMATCH'
  | 'DUPLICATE_TRANSACTION'
  | 'MANUAL_REVIEW_REQUIRED'
  | 'NETWORK_FAILURE'
  | 'OTHER'
  | 'PAYMENT_TIMEOUT'
  | 'PROVIDER_ERROR'
  | 'RECONCILIATION_NEEDED'
  | 'VALIDATION_FAILURE'
  | 'WEBHOOK_MISSED';

export type TransactionResolutionType =
  | 'AUTO_RESOLVED'
  | 'ESCALATED'
  | 'MANUAL_APPROVAL'
  | 'MANUAL_REJECTION'
  | 'RECONCILED'
  | 'REFUNDED'
  | 'RETRIED_SUCCESS'
  | 'WRITTEN_OFF';

export type TransactionReviewStatus =
  | 'ESCALATED'
  | 'NONE'
  | 'PENDING_REVIEW'
  | 'REVIEWED'
  | 'UNDER_REVIEW';

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

export type TransferChannel =
  | 'EMAIL'
  | 'WHATSAPP';

export type TransferDirection =
  | 'INCOMING'
  | 'OUTGOING';

export type TransferOwnershipInput = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason?: InputMaybe<Scalars['String']['input']>;
};

export type TransferRecipient = {
  __typename: 'TransferRecipient';
  displayName: Maybe<Scalars['String']['output']>;
  maskedContact: Scalars['String']['output'];
};

export type TransferStatus =
  | 'ACCEPTED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'EXPIRED'
  | 'PENDING'
  | 'REJECTED';

export type TwoFactorMethod =
  | 'AUTHENTICATOR_APP'
  | 'BACKUP_CODE'
  | 'EMAIL'
  | 'SMS';

export type UpdateBankAccountInput = {
  accountHolderName?: InputMaybe<Scalars['String']['input']>;
  accountNumber?: InputMaybe<Scalars['String']['input']>;
  accountType?: InputMaybe<Scalars['String']['input']>;
  bankCode?: InputMaybe<Scalars['String']['input']>;
  bankName?: InputMaybe<Scalars['String']['input']>;
  branchCode?: InputMaybe<Scalars['String']['input']>;
  branchName?: InputMaybe<Scalars['String']['input']>;
  isDefault?: InputMaybe<Scalars['Boolean']['input']>;
  swiftCode?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateChargebackRecoveryInput = {
  action: ChargebackRecoveryAction;
  amount?: InputMaybe<Scalars['BigDecimal']['input']>;
  fundSource?: InputMaybe<ChargebackFundSource>;
  reference?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateCityInput = {
  code?: InputMaybe<Scalars['String']['input']>;
  country?: InputMaybe<Scalars['String']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  provinceId?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateCoordinatesInput = {
  latitude?: InputMaybe<Scalars['Float']['input']>;
  longitude?: InputMaybe<Scalars['Float']['input']>;
};

export type UpdateEscrowAccountInput = {
  lockReason?: InputMaybe<Scalars['String']['input']>;
  lockUntil?: InputMaybe<Scalars['DateTime']['input']>;
  status?: InputMaybe<EscrowAccountStatus>;
};

export type UpdateEventAccessInput = {
  accessId: Scalars['ID']['input'];
  customPermissions?: InputMaybe<Array<Scalars['String']['input']>>;
  expiresAt?: InputMaybe<Scalars['DateTime']['input']>;
  newRole?: InputMaybe<EventRole>;
};

export type UpdateEventCategoryInput = {
  code?: InputMaybe<Scalars['String']['input']>;
  description?: InputMaybe<Scalars['String']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateEventInput = {
  additionalInfo?: InputMaybe<Scalars['JSON']['input']>;
  ageRestriction?: InputMaybe<Scalars['String']['input']>;
  bagPolicy?: InputMaybe<Scalars['String']['input']>;
  bannerAltText?: InputMaybe<Scalars['String']['input']>;
  bannerImageUrl?: InputMaybe<Scalars['String']['input']>;
  cancellationPolicy?: InputMaybe<Scalars['String']['input']>;
  categoryId?: InputMaybe<Scalars['String']['input']>;
  checkoutSettings?: InputMaybe<CheckoutSettingsInput>;
  description?: InputMaybe<Scalars['String']['input']>;
  doorsOpenAt?: InputMaybe<Scalars['DateTime']['input']>;
  enableWaitlist?: InputMaybe<Scalars['Boolean']['input']>;
  endDateTime?: InputMaybe<Scalars['DateTime']['input']>;
  eventDateTime?: InputMaybe<Scalars['DateTime']['input']>;
  faqs?: InputMaybe<Array<EventFaqInput>>;
  featured?: InputMaybe<Scalars['Boolean']['input']>;
  galleryImages?: InputMaybe<Array<Scalars['String']['input']>>;
  gettingThere?: InputMaybe<Scalars['String']['input']>;
  isFreeEvent?: InputMaybe<Scalars['Boolean']['input']>;
  isVirtual?: InputMaybe<Scalars['Boolean']['input']>;
  location?: InputMaybe<EventLocationInput>;
  parkingInfo?: InputMaybe<Scalars['String']['input']>;
  publishAt?: InputMaybe<Scalars['DateTime']['input']>;
  refundPolicy?: InputMaybe<Scalars['String']['input']>;
  runningOrder?: InputMaybe<Array<RunningOrderItemInput>>;
  tagline?: InputMaybe<Scalars['String']['input']>;
  termsAndConditions?: InputMaybe<Scalars['String']['input']>;
  thumbnailImageUrl?: InputMaybe<Scalars['String']['input']>;
  title?: InputMaybe<Scalars['String']['input']>;
  totalCapacity?: InputMaybe<Scalars['Int']['input']>;
  virtualEventPlatform?: InputMaybe<Scalars['String']['input']>;
  virtualEventUrl?: InputMaybe<Scalars['String']['input']>;
  waitlistCapacity?: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateMediaInput = {
  altText?: InputMaybe<Scalars['String']['input']>;
  eventId?: InputMaybe<Scalars['ID']['input']>;
  title?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateMemberRoleInput = {
  customPermissions?: InputMaybe<Array<Scalars['String']['input']>>;
  deniedPermissions?: InputMaybe<Array<Scalars['String']['input']>>;
  memberId: Scalars['ID']['input'];
  newRole: OrganizationRole;
  organizationId: Scalars['ID']['input'];
};

export type UpdateNotificationPreferencesInput = {
  emailEnabled?: InputMaybe<Scalars['Boolean']['input']>;
  eventReminders?: InputMaybe<Scalars['Boolean']['input']>;
  eventUpdates?: InputMaybe<Scalars['Boolean']['input']>;
  inAppEnabled?: InputMaybe<Scalars['Boolean']['input']>;
  marketingEmails?: InputMaybe<Scalars['Boolean']['input']>;
  paymentNotifications?: InputMaybe<Scalars['Boolean']['input']>;
  pushEnabled?: InputMaybe<Scalars['Boolean']['input']>;
  quietHoursEnd?: InputMaybe<Scalars['String']['input']>;
  quietHoursStart?: InputMaybe<Scalars['String']['input']>;
  reminderHoursBefore?: InputMaybe<Scalars['Int']['input']>;
  smsEnabled?: InputMaybe<Scalars['Boolean']['input']>;
  systemAnnouncements?: InputMaybe<Scalars['Boolean']['input']>;
  teamNotifications?: InputMaybe<Scalars['Boolean']['input']>;
  ticketNotifications?: InputMaybe<Scalars['Boolean']['input']>;
  timezone?: InputMaybe<Scalars['String']['input']>;
  whatsappEnabled?: InputMaybe<Scalars['Boolean']['input']>;
};

export type UpdateOrganizationInput = {
  bannerUrl?: InputMaybe<Scalars['String']['input']>;
  businessAddress?: InputMaybe<BusinessAddressInput>;
  businessEmail?: InputMaybe<Scalars['String']['input']>;
  businessPhone?: InputMaybe<Scalars['String']['input']>;
  businessRegistrationNumber?: InputMaybe<Scalars['String']['input']>;
  businessType?: InputMaybe<BusinessType>;
  description?: InputMaybe<Scalars['String']['input']>;
  logoUrl?: InputMaybe<Scalars['String']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  socialLinks?: InputMaybe<SocialLinksInput>;
  tagline?: InputMaybe<Scalars['String']['input']>;
  taxId?: InputMaybe<Scalars['String']['input']>;
  website?: InputMaybe<Scalars['String']['input']>;
  yearEstablished?: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateOrganizationSettingsInput = {
  adminsCanRequestPayouts?: InputMaybe<Scalars['Boolean']['input']>;
  allowMembersToInvite?: InputMaybe<Scalars['Boolean']['input']>;
  defaultEventVisibility?: InputMaybe<Scalars['String']['input']>;
  inviteRequiresApproval?: InputMaybe<Scalars['Boolean']['input']>;
  managersCanViewFinancials?: InputMaybe<Scalars['Boolean']['input']>;
  maxTeamMembers?: InputMaybe<Scalars['Int']['input']>;
  notifyOwnerOnEventCreated?: InputMaybe<Scalars['Boolean']['input']>;
  notifyOwnerOnMemberJoin?: InputMaybe<Scalars['Boolean']['input']>;
  notifyOwnerOnPayoutRequest?: InputMaybe<Scalars['Boolean']['input']>;
  requireEventApproval?: InputMaybe<Scalars['Boolean']['input']>;
};

/**
 * Update payout configuration input.
 * SECURITY: Fields are validated with OWASP-compliant sanitization.
 */
export type UpdatePayoutConfigInput = {
  /** Minimum payout amount in ZMW (must be >= 100.0) */
  minimumPayoutAmount?: InputMaybe<Scalars['Float']['input']>;
  /** Preferred payout method */
  preferredMethod?: InputMaybe<PayoutMethod>;
  /** Payout schedule */
  schedule?: InputMaybe<PayoutSchedule>;
};

export type UpdatePlatformConfigurationInput = {
  adminNotificationChannel?: InputMaybe<ApprovalNotificationChannel>;
  allowSelfApproval?: InputMaybe<Scalars['Boolean']['input']>;
  approvalSlaHours?: InputMaybe<Scalars['Int']['input']>;
  approvalWarningThresholdHours?: InputMaybe<Scalars['Int']['input']>;
  autoEscalationEnabled?: InputMaybe<Scalars['Boolean']['input']>;
  commissionDefault?: InputMaybe<Scalars['BigDecimal']['input']>;
  currency?: InputMaybe<Scalars['String']['input']>;
  escalationDelayHours?: InputMaybe<Scalars['Int']['input']>;
  escalationRecipientRole?: InputMaybe<Scalars['String']['input']>;
  escalationReminderIntervalHours?: InputMaybe<Scalars['Int']['input']>;
  escrowHoldDays?: InputMaybe<Scalars['Int']['input']>;
  maxEscalationReminders?: InputMaybe<Scalars['Int']['input']>;
  maxTicketsPerBooking?: InputMaybe<Scalars['Int']['input']>;
  minimumPayout?: InputMaybe<Scalars['BigDecimal']['input']>;
  organizerNotificationChannel?: InputMaybe<ApprovalNotificationChannel>;
  refundCutoffHours?: InputMaybe<Scalars['Int']['input']>;
  refundPolicies?: InputMaybe<Array<PlatformRefundPolicyInput>>;
  requireCommentsOnChangesRequested?: InputMaybe<Scalars['Boolean']['input']>;
  requireCommentsOnRejection?: InputMaybe<Scalars['Boolean']['input']>;
  rescheduleLimit?: InputMaybe<Scalars['Int']['input']>;
  reservationGraceMinutes?: InputMaybe<Scalars['Int']['input']>;
  reservationHoldMinutes?: InputMaybe<Scalars['Int']['input']>;
  sendEscalationNotifications?: InputMaybe<Scalars['Boolean']['input']>;
  sendSlaWarningNotifications?: InputMaybe<Scalars['Boolean']['input']>;
};

export type UpdatePromoCodeInput = {
  applicableTiers?: InputMaybe<Array<Scalars['String']['input']>>;
  discountType?: InputMaybe<DiscountType>;
  discountValue?: InputMaybe<Scalars['BigDecimal']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  maxDiscountAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  maxUses?: InputMaybe<Scalars['Int']['input']>;
  minPurchaseAmount?: InputMaybe<Scalars['BigDecimal']['input']>;
  validFrom?: InputMaybe<Scalars['DateTime']['input']>;
  validUntil?: InputMaybe<Scalars['DateTime']['input']>;
};

export type UpdateProvinceInput = {
  code?: InputMaybe<Scalars['String']['input']>;
  country?: InputMaybe<Scalars['String']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateReferenceDataInput = {
  allowedTransitions?: InputMaybe<Array<Scalars['String']['input']>>;
  description?: InputMaybe<Scalars['String']['input']>;
  displayOrder?: InputMaybe<Scalars['Int']['input']>;
  effectiveFrom?: InputMaybe<Scalars['DateTime']['input']>;
  effectiveTo?: InputMaybe<Scalars['DateTime']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  metadata?: InputMaybe<Scalars['JSON']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  parentCode?: InputMaybe<Scalars['String']['input']>;
  parentType?: InputMaybe<ReferenceType>;
  semantic?: InputMaybe<WorkflowSemantic>;
};

export type UpdateStockImageInput = {
  active?: InputMaybe<Scalars['Boolean']['input']>;
  altText?: InputMaybe<Scalars['String']['input']>;
  categoryCode?: InputMaybe<Scalars['String']['input']>;
  purpose?: InputMaybe<StockImagePurpose>;
  title?: InputMaybe<Scalars['String']['input']>;
};

export type UpdateTicketTierInput = {
  accessCode?: InputMaybe<Scalars['String']['input']>;
  benefits?: InputMaybe<Array<Scalars['String']['input']>>;
  category?: InputMaybe<TicketCategory>;
  description?: InputMaybe<Scalars['String']['input']>;
  earlyBirdEndsAt?: InputMaybe<Scalars['DateTime']['input']>;
  earlyBirdPrice?: InputMaybe<Scalars['BigDecimal']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  isHidden?: InputMaybe<Scalars['Boolean']['input']>;
  maxPerOrder?: InputMaybe<Scalars['Int']['input']>;
  minPerOrder?: InputMaybe<Scalars['Int']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  price?: InputMaybe<Scalars['BigDecimal']['input']>;
  quantity?: InputMaybe<Scalars['Int']['input']>;
  salesEndAt?: InputMaybe<Scalars['DateTime']['input']>;
  salesStartAt?: InputMaybe<Scalars['DateTime']['input']>;
  sortOrder?: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateUserInput = {
  displayName?: InputMaybe<Scalars['String']['input']>;
  firstName?: InputMaybe<Scalars['String']['input']>;
  gender?: InputMaybe<Scalars['String']['input']>;
  lastName?: InputMaybe<Scalars['String']['input']>;
};

export type UploadMediaInput = {
  altText?: InputMaybe<Scalars['String']['input']>;
  contentBase64: Scalars['String']['input'];
  contentType: Scalars['String']['input'];
  eventId?: InputMaybe<Scalars['ID']['input']>;
  fileName: Scalars['String']['input'];
  title?: InputMaybe<Scalars['String']['input']>;
};

export type UploadStockImageInput = {
  altText?: InputMaybe<Scalars['String']['input']>;
  categoryCode?: InputMaybe<Scalars['String']['input']>;
  contentBase64: Scalars['String']['input'];
  contentType: Scalars['String']['input'];
  fileName: Scalars['String']['input'];
  purpose: StockImagePurpose;
  title?: InputMaybe<Scalars['String']['input']>;
};

export type UploadVerificationDocumentInput = {
  documentType: Scalars['String']['input'];
  documentUrl?: InputMaybe<Scalars['String']['input']>;
  fileName: Scalars['String']['input'];
  fileSize: Scalars['Long']['input'];
  mimeType: Scalars['String']['input'];
};

export type User = {
  __typename: 'User';
  accountStatus: AccountStatus;
  active: Scalars['Boolean']['output'];
  activeTicketCount: Scalars['Int']['output'];
  contacts: Array<Contact>;
  createdAt: Scalars['DateTime']['output'];
  deletionRequestedAt: Maybe<Scalars['DateTime']['output']>;
  deletionScheduledFor: Maybe<Scalars['DateTime']['output']>;
  displayName: Maybe<Scalars['String']['output']>;
  email: Maybe<Scalars['String']['output']>;
  emailVerified: Scalars['Boolean']['output'];
  firstName: Maybe<Scalars['String']['output']>;
  fullName: Scalars['String']['output'];
  gender: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  lastLoginAt: Maybe<Scalars['DateTime']['output']>;
  lastName: Maybe<Scalars['String']['output']>;
  lockReason: Maybe<Scalars['String']['output']>;
  locked: Scalars['Boolean']['output'];
  memberSince: Maybe<Scalars['DateTime']['output']>;
  notificationPreferences: Maybe<NotificationPreferences>;
  organizationMemberships: Maybe<Array<OrganizationMember>>;
  phoneCountry: Maybe<Scalars['String']['output']>;
  phoneNumber: Maybe<Scalars['PhoneNumber']['output']>;
  phoneVerified: Scalars['Boolean']['output'];
  preferredChannel: Maybe<ContactType>;
  purchasedTickets: Array<Ticket>;
  /**
   * All roles assigned to the user.
   * A user can have multiple roles. CUSTOMER is the base role that all users have.
   * Example: [CUSTOMER, ORGANIZER] for an event organizer
   */
  roles: Array<UserType>;
  socialConnections: Maybe<Array<SocialConnection>>;
  status: AccountState;
  suspendReason: Maybe<Scalars['String']['output']>;
  totalSpent: Scalars['BigDecimal']['output'];
  twoFactorEnabled: Scalars['Boolean']['output'];
  twoFactorMethod: Maybe<TwoFactorMethod>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  username: Maybe<Scalars['String']['output']>;
};

export type UserDevice = {
  __typename: 'UserDevice';
  appVersion: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  deviceModel: Maybe<Scalars['String']['output']>;
  deviceName: Maybe<Scalars['String']['output']>;
  deviceToken: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  isPrimary: Scalars['Boolean']['output'];
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  osVersion: Maybe<Scalars['String']['output']>;
  platform: DevicePlatform;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  userId: Scalars['ID']['output'];
};

export type UserOffsetPage = {
  __typename: 'UserOffsetPage';
  content: Array<User>;
  pageInfo: PageInfo;
};

export type UserRoleStats = {
  __typename: 'UserRoleStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  role: UserType;
};

export type UserStats = {
  __typename: 'UserStats';
  activeUsers: Scalars['Int']['output'];
  adminUsers: Scalars['Int']['output'];
  attendees: Scalars['Int']['output'];
  growthRate: Maybe<Scalars['Float']['output']>;
  lockedUsers: Scalars['Int']['output'];
  newUsersThisMonth: Scalars['Int']['output'];
  newUsersThisWeek: Scalars['Int']['output'];
  organizers: Scalars['Int']['output'];
  pendingVerificationUsers: Scalars['Int']['output'];
  suspendedUsers: Scalars['Int']['output'];
  totalUsers: Scalars['Int']['output'];
  usersByRole: Array<UserRoleStats>;
  usersByStatus: Array<UserStatusStats>;
  verifiedUsers: Scalars['Int']['output'];
};

export type UserStatusStats = {
  __typename: 'UserStatusStats';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  status: AccountStatus;
};

export type UserType =
  | 'ADMIN'
  | 'CUSTOMER'
  | 'FINANCE'
  | 'FINANCE_LEAD'
  | 'ORGANIZER'
  | 'SUPER_ADMIN';

export type ValidateTicketInput = {
  code: Scalars['String']['input'];
  deviceId?: InputMaybe<Scalars['String']['input']>;
  eventId: Scalars['ID']['input'];
  method?: InputMaybe<ValidationMethod>;
  reason?: InputMaybe<Scalars['String']['input']>;
  scanId?: InputMaybe<Scalars['String']['input']>;
  scannedAt?: InputMaybe<Scalars['DateTime']['input']>;
};

export type ValidationMethod =
  | 'MANUAL'
  | 'QR_OFFLINE'
  | 'QR_ONLINE';

export type ValidationResult = {
  __typename: 'ValidationResult';
  admitted: Scalars['Boolean']['output'];
  checkIn: Maybe<CheckIn>;
  conflict: Maybe<CheckInConflict>;
  message: Scalars['String']['output'];
  outcome: CheckInOutcome;
  ticket: Maybe<Ticket>;
};

export type VerificationDocument = {
  __typename: 'VerificationDocument';
  documentType: Scalars['String']['output'];
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

/**
 * What an administrator-defined status MEANS to the code.
 *
 * Statuses are fully configurable, but code cannot branch on a string somebody
 * invented this morning — a payout in an unrecognised status never moves, and
 * nobody finds out until an organizer asks where the money went. So the value SET
 * is configurable and this small vocabulary of meanings is fixed. Same shape as
 * Jira's status categories, for the same reason.
 */
export type WorkflowSemantic =
  | 'CANCELLED'
  | 'FAILED'
  | 'INITIAL'
  | 'IN_PROGRESS'
  | 'PENDING'
  | 'SUCCEEDED';

export type Join__ContextArgument = {
  context: Scalars['String']['input'];
  name: Scalars['String']['input'];
  selection: Scalars['join__FieldValue']['input'];
  type: Scalars['String']['input'];
};

export type Join__Graph =
  | 'BOOKING'
  | 'CATALOG'
  | 'IDENTITY';

export type Link__Purpose =
  /** `EXECUTION` features provide metadata necessary for operation execution. */
  | 'EXECUTION'
  /** `SECURITY` features provide metadata necessary to securely resolve fields. */
  | 'SECURITY';

export type OrganizerTicketsQueryVariables = Exact<{
  organizerId: Scalars['String']['input'];
  filter: InputMaybe<TicketFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerTicketsQuery = { __typename: 'Query', ticketsByOrganizer: { __typename: 'TicketOffsetPage', data: Array<{ __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, purchaseDate: string | null, validatedAt: string | null, cancelledAt: string | null, cancellationReason: string | null, refundedAt: string | null, refundReason: string | null, paymentReference: string | null, netAmount: string | null, commissionAmount: string | null, paymentInfo: { __typename: 'PaymentInfo', paymentMethod: string | null, status: string | null, providerReference: string | null, paymentDate: string | null } | null, refundInfo: { __typename: 'RefundInfo', refundAmount: string | null, reason: string | null, status: string | null, refundDate: string | null } | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, hasNext: boolean | null } } };

export type EventRefundRequestsQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type EventRefundRequestsQuery = { __typename: 'Query', refundRequestsByEvent: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketNumber: string, eventId: string, refundAmount: string, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, requestedAt: string | null, policyApplied: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, hasNext: boolean | null } } };

export type OrganizerCalculateRefundQueryVariables = Exact<{
  ticketId: Scalars['String']['input'];
}>;


export type OrganizerCalculateRefundQuery = { __typename: 'Query', calculateRefundAmount: { __typename: 'RefundCalculation', ticketId: string, ticketNumber: string, eventDate: string, originalAmount: string, daysBeforeEvent: number, refundPercentage: number, refundAmount: string, commissionRefund: string, platformRetains: string, policyApplied: string, isEligible: boolean, ineligibleReason: string | null } };

export type OrganizerRefundTicketMutationVariables = Exact<{
  ticketNumber: Scalars['String']['input'];
  reason: Scalars['String']['input'];
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
}>;


export type OrganizerRefundTicketMutation = { __typename: 'Mutation', refundTicket: { __typename: 'Ticket', id: string, ticketNumber: string, status: TicketStatus, refundedAt: string | null } };

export type OrganizerResendTicketMutationVariables = Exact<{
  ticketId: Scalars['ID']['input'];
}>;


export type OrganizerResendTicketMutation = { __typename: 'Mutation', resendTicket: { __typename: 'ResendTicketResult', ticketId: string, ticketNumber: string, status: string, channel: string | null, destination: string | null } };

export type OrganizerBookingsQueryVariables = Exact<{
  filter: InputMaybe<BookingFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerBookingsQuery = { __typename: 'Query', bookingsByOrganizer: { __typename: 'BookingOffsetPage', data: Array<{ __typename: 'Booking', id: string, bookingNumber: string, eventId: string, eventTitle: string | null, contactName: string | null, contactEmail: string | null, contactPhone: string | null, status: BookingStatus, ticketCount: number, subtotal: string | null, discountAmount: string | null, totalAmount: string, currency: string, promoCode: string | null, refundedAmount: string, refundableAmount: string, createdAt: string | null, confirmedAt: string | null, payment: { __typename: 'BookingPayment', provider: string | null, status: string | null, reference: string | null, amount: string | null, currency: string | null, payerPhone: string | null, paidAt: string | null } | null, tickets: Array<{ __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, purchaseDate: string | null, validatedAt: string | null, cancelledAt: string | null, cancellationReason: string | null, refundedAt: string | null, refundReason: string | null, paymentReference: string | null, netAmount: string | null, commissionAmount: string | null, paymentInfo: { __typename: 'PaymentInfo', paymentMethod: string | null, status: string | null, providerReference: string | null, paymentDate: string | null } | null, refundInfo: { __typename: 'RefundInfo', refundAmount: string | null, reason: string | null, status: string | null, refundDate: string | null } | null }> }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, hasNext: boolean | null } } };

export type OrganizerBookingQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrganizerBookingQuery = { __typename: 'Query', booking: { __typename: 'Booking', id: string, bookingNumber: string, eventId: string, eventTitle: string | null, contactName: string | null, contactEmail: string | null, contactPhone: string | null, status: BookingStatus, ticketCount: number, subtotal: string | null, discountAmount: string | null, totalAmount: string, currency: string, promoCode: string | null, refundedAmount: string, refundableAmount: string, createdAt: string | null, confirmedAt: string | null, items: Array<{ __typename: 'BookingItem', ticketTierId: string, tierName: string, quantity: number, unitPrice: string, subtotal: string }>, payment: { __typename: 'BookingPayment', provider: string | null, status: string | null, reference: string | null, amount: string | null, currency: string | null, payerPhone: string | null, paidAt: string | null } | null, refundRequests: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketNumber: string, refundAmount: string, currency: string, status: RefundRequestStatus, reason: string, requestedAt: string | null }>, tickets: Array<{ __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, purchaseDate: string | null, validatedAt: string | null, cancelledAt: string | null, cancellationReason: string | null, refundedAt: string | null, refundReason: string | null, paymentReference: string | null, netAmount: string | null, commissionAmount: string | null, paymentInfo: { __typename: 'PaymentInfo', paymentMethod: string | null, status: string | null, providerReference: string | null, paymentDate: string | null } | null, refundInfo: { __typename: 'RefundInfo', refundAmount: string | null, reason: string | null, status: string | null, refundDate: string | null } | null }> } | null };

export type OrganizerRefundInboxQueryVariables = Exact<{
  filter: InputMaybe<RefundRequestFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerRefundInboxQuery = { __typename: 'Query', refundRequestsByOrganizer: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketNumber: string, eventId: string, refundAmount: string, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, requestedAt: string | null, policyApplied: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, hasNext: boolean | null } } };

export type OrganizerCancelTicketMutationVariables = Exact<{
  ticketNumber: Scalars['String']['input'];
  reason: Scalars['String']['input'];
}>;


export type OrganizerCancelTicketMutation = { __typename: 'Mutation', cancelTicket: { __typename: 'Ticket', id: string, ticketNumber: string, status: TicketStatus, cancelledAt: string | null } };

export type OrganizerRecentCheckInsQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
  limit: InputMaybe<Scalars['Int']['input']>;
}>;


export type OrganizerRecentCheckInsQuery = { __typename: 'Query', recentCheckIns: Array<{ __typename: 'CheckIn', id: string, ticketNumber: string | null, method: ValidationMethod, reason: string | null, scannedAt: string | null, recordedAt: string }> };

export type OrganizerCheckInConflictsQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerCheckInConflictsQuery = { __typename: 'Query', checkInConflicts: { __typename: 'CheckInConflictPage', totalElements: number, content: Array<{ __typename: 'CheckInConflict', id: string, presentedCode: string | null, type: CheckInConflictType, status: CheckInConflictStatus, detectedAt: string, reviewNote: string | null }> } };

export type OrganizerReviewConflictMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  note: Scalars['String']['input'];
}>;


export type OrganizerReviewConflictMutation = { __typename: 'Mutation', reviewConflict: { __typename: 'CheckInConflict', id: string, status: CheckInConflictStatus, reviewNote: string | null } };

export type EditorEventQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type EditorEventQuery = { __typename: 'Query', event: { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, categoryId: string | null, eventDateTime: string, endDateTime: string, bannerImageUrl: string | null, bannerAltText: string | null, isVirtual: boolean, isFreeEvent: boolean, virtualEventUrl: string | null, totalCapacity: number, soldTickets: number, refundPolicy: string | null, cancellationPolicy: string | null, termsAndConditions: string | null, rejectionReason: string | null, tagline: string | null, ageRestriction: string | null, doorsOpenAt: string | null, galleryImages: Array<string> | null, gettingThere: string | null, parkingInfo: string | null, bagPolicy: string | null, publishAt: string | null, publishScheduled: boolean, publishedAt: string | null, submittedForApprovalAt: string | null, approvalDeadline: string | null, approvedAt: string | null, rejectedAt: string | null, faqs: Array<{ __typename: 'EventFaq', question: string, answer: string }> | null, runningOrder: Array<{ __typename: 'RunningOrderItem', time: string, title: string }> | null, checkoutSettings: { __typename: 'EventCheckoutSettings', maxTicketsPerOrder: number | null, collectHolderNames: boolean, extraQuestion: string | null } | null, location: { __typename: 'Location', name: string, address: string, city: string, province: string | null, country: string } | null, accessibility: { __typename: 'EventAccessibility', wheelchairAccessible: boolean, wheelchairSeatsAvailable: number | null, signLanguageInterpreter: boolean, hearingLoopAvailable: boolean, accessibleParking: boolean, accessibleRestrooms: boolean, assistanceDogsAllowed: boolean, additionalNotes: string | null } | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, code: string, name: string, description: string | null, price: string, currency: string, quantity: number, soldQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, salesStartAt: string | null, salesEndAt: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, sortOrder: number, isActive: boolean, isHidden: boolean, accessCode: string | null, category: TicketCategory }> | null } | null };

export type EditorReferenceDataQueryVariables = Exact<{ [key: string]: never; }>;


export type EditorReferenceDataQuery = { __typename: 'Query', categories: Array<{ __typename: 'EventCategory', id: string, name: string, code: string, isActive: boolean }>, provinces: Array<{ __typename: 'Province', id: string, name: string, code: string }>, cities: Array<{ __typename: 'City', id: string, name: string, province: string | null, provinceId: string | null }> };

export type EditorCreateEventMutationVariables = Exact<{
  input: CreateEventInput;
}>;


export type EditorCreateEventMutation = { __typename: 'Mutation', createEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type EditorUpdateEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateEventInput;
}>;


export type EditorUpdateEventMutation = { __typename: 'Mutation', updateEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type EditorUpdateAccessibilityMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  input: EventAccessibilityInput;
}>;


export type EditorUpdateAccessibilityMutation = { __typename: 'Mutation', updateEventAccessibility: { __typename: 'Event', id: string } };

export type EditorSubmitForApprovalMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type EditorSubmitForApprovalMutation = { __typename: 'Mutation', submitEventForApproval: { __typename: 'Event', id: string, status: EventStatus } };

export type EditorCancelScheduledPublishMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type EditorCancelScheduledPublishMutation = { __typename: 'Mutation', cancelScheduledPublish: { __typename: 'Event', id: string, status: EventStatus, publishAt: string | null, publishScheduled: boolean } };

export type EditorCreateTierMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  input: CreateTicketTierInput;
}>;


export type EditorCreateTierMutation = { __typename: 'Mutation', createTicketTier: { __typename: 'TicketTier', id: string, code: string, name: string, description: string | null, price: string, currency: string, quantity: number, soldQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, salesStartAt: string | null, salesEndAt: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, sortOrder: number, isActive: boolean, isHidden: boolean, accessCode: string | null, category: TicketCategory } };

export type EditorUpdateTierMutationVariables = Exact<{
  tierId: Scalars['ID']['input'];
  input: UpdateTicketTierInput;
}>;


export type EditorUpdateTierMutation = { __typename: 'Mutation', updateTicketTier: { __typename: 'TicketTier', id: string, code: string, name: string, description: string | null, price: string, currency: string, quantity: number, soldQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, salesStartAt: string | null, salesEndAt: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, sortOrder: number, isActive: boolean, isHidden: boolean, accessCode: string | null, category: TicketCategory } };

export type EditorDeleteTierMutationVariables = Exact<{
  tierId: Scalars['ID']['input'];
}>;


export type EditorDeleteTierMutation = { __typename: 'Mutation', deleteTicketTier: string };

export type EditorReorderTiersMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  tierIds: Array<Scalars['ID']['input']> | Scalars['ID']['input'];
}>;


export type EditorReorderTiersMutation = { __typename: 'Mutation', reorderTicketTiers: Array<{ __typename: 'TicketTier', id: string, sortOrder: number }> };

export type OrganizerSalesSeriesQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
  from: InputMaybe<Scalars['DateTime']['input']>;
  to: InputMaybe<Scalars['DateTime']['input']>;
  bucket: InputMaybe<SalesBucket>;
}>;


export type OrganizerSalesSeriesQuery = { __typename: 'Query', salesOverTime: Array<{ __typename: 'SalesPoint', bucketStart: string, bucketEnd: string, tickets: number, orders: number, grossRevenue: string, netRevenue: string, refundedAmount: string }> };

export type OrganizerHeatmapQueryVariables = Exact<{
  eventId: InputMaybe<Scalars['ID']['input']>;
  from: InputMaybe<Scalars['DateTime']['input']>;
  to: InputMaybe<Scalars['DateTime']['input']>;
}>;


export type OrganizerHeatmapQuery = { __typename: 'Query', purchasesByDayAndHour: Array<{ __typename: 'PurchaseHeatCell', dayOfWeek: number, hour: number, purchases: number, tickets: number, revenue: string }> };

export type OrganizerHolderAudienceQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
  segment: InputMaybe<HolderSegment>;
  ticketTierId: InputMaybe<Scalars['String']['input']>;
}>;


export type OrganizerHolderAudienceQuery = { __typename: 'Query', ticketHolderAudience: number };

export type OrganizerHolderMessagesQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerHolderMessagesQuery = { __typename: 'Query', ticketHolderMessages: { __typename: 'HolderMessagePage', data: Array<{ __typename: 'HolderMessage', id: string, subject: string, body: string, segment: HolderSegment, ticketTierId: string | null, recipientCount: number, deliveredCount: number, status: HolderMessageStatus, sentBy: string, createdAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null } } };

export type OrganizerMessageHoldersMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  input: MessageTicketHoldersInput;
}>;


export type OrganizerMessageHoldersMutation = { __typename: 'Mutation', messageTicketHolders: { __typename: 'HolderMessage', id: string, status: HolderMessageStatus, recipientCount: number, deliveredCount: number } };

export type OrgEventsConnectionQueryVariables = Exact<{
  filter: InputMaybe<OrganizerEventFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type OrgEventsConnectionQuery = { __typename: 'Query', myEventsConnection: { __typename: 'EventConnection', edges: Array<{ __typename: 'EventEdge', cursor: string, node: { __typename: 'Event', id: string, title: string, status: EventStatus, eventDateTime: string, endDateTime: string, locationName: string | null, cityName: string | null, bannerImageUrl: string | null, totalCapacity: number, soldTickets: number, revenue: string, currency: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, isActive: boolean }> | null } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean | null, endCursor: string | null } } };

export type OrgEventCountsQueryVariables = Exact<{ [key: string]: never; }>;


export type OrgEventCountsQuery = { __typename: 'Query', total: number, draft: number, pending: number, changes: number, approved: number, rejected: number, published: number, cancelled: number, completed: number };

export type OrgEventDetailQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgEventDetailQuery = { __typename: 'Query', event: { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, eventDateTime: string, endDateTime: string, locationName: string | null, locationAddress: string | null, cityName: string | null, bannerImageUrl: string | null, totalCapacity: number, soldTickets: number, availableTickets: number, revenue: string, currency: string | null, refundPolicy: string | null, rejectionReason: string | null, publishedAt: string | null, submittedForApprovalAt: string | null, approvedAt: string | null, rejectedAt: string | null, createdAt: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, eventId: string, code: string, name: string, description: string | null, price: string, currency: string, quantity: number, soldQuantity: number, availableQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, salesStartAt: string | null, salesEndAt: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, sortOrder: number, isActive: boolean, isHidden: boolean, accessCode: string | null }> | null } | null };

export type OrgEventStatisticsQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgEventStatisticsQuery = { __typename: 'Query', eventStatistics: { __typename: 'EventTicketStatistics', totalTicketsAvailable: number, totalTicketsSold: number, totalTicketsRefunded: number, totalGrossRevenue: string | null, totalCommissionEarned: string | null, overallSalesPercentage: number, bestSellingTier: string | null } | null };

export type OrgSubmitEventMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type OrgSubmitEventMutation = { __typename: 'Mutation', submitEventForApproval: { __typename: 'Event', id: string, status: EventStatus } };

export type OrgPublishEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgPublishEventMutation = { __typename: 'Mutation', publishEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type OrgUnpublishEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgUnpublishEventMutation = { __typename: 'Mutation', unpublishEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type OrgRescheduleEventMutationVariables = Exact<{
  input: RescheduleEventInput;
}>;


export type OrgRescheduleEventMutation = { __typename: 'Mutation', rescheduleEvent: { __typename: 'Event', id: string, status: EventStatus, eventDateTime: string, endDateTime: string } };

export type OrgCancelEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: EventCancellationInput;
}>;


export type OrgCancelEventMutation = { __typename: 'Mutation', cancelEvent: { __typename: 'EventCancellationResponse', ticketsAffected: number, refundSagaInitiated: boolean, event: { __typename: 'Event', id: string, status: EventStatus } | null } };

export type OrgDuplicateEventMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  newTitle: Scalars['String']['input'];
}>;


export type OrgDuplicateEventMutation = { __typename: 'Mutation', duplicateEvent: { __typename: 'Event', id: string, title: string, status: EventStatus } };

export type OrgDeleteEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgDeleteEventMutation = { __typename: 'Mutation', deleteEvent: string };

export type OrgCreateTierMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  input: CreateTicketTierInput;
}>;


export type OrgCreateTierMutation = { __typename: 'Mutation', createTicketTier: { __typename: 'TicketTier', id: string } };

export type OrgUpdateTierMutationVariables = Exact<{
  tierId: Scalars['ID']['input'];
  input: UpdateTicketTierInput;
}>;


export type OrgUpdateTierMutation = { __typename: 'Mutation', updateTicketTier: { __typename: 'TicketTier', id: string } };

export type OrgDeleteTierMutationVariables = Exact<{
  tierId: Scalars['ID']['input'];
}>;


export type OrgDeleteTierMutation = { __typename: 'Mutation', deleteTicketTier: string };

export type OrgActivateTierMutationVariables = Exact<{
  tierId: Scalars['ID']['input'];
}>;


export type OrgActivateTierMutation = { __typename: 'Mutation', activateTicketTier: { __typename: 'TicketTier', id: string, isActive: boolean } };

export type OrgDeactivateTierMutationVariables = Exact<{
  tierId: Scalars['ID']['input'];
}>;


export type OrgDeactivateTierMutation = { __typename: 'Mutation', deactivateTicketTier: { __typename: 'TicketTier', id: string, isActive: boolean } };

export type OrgReorderTiersMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  tierIds: Array<Scalars['ID']['input']> | Scalars['ID']['input'];
}>;


export type OrgReorderTiersMutation = { __typename: 'Mutation', reorderTicketTiers: Array<{ __typename: 'TicketTier', id: string, sortOrder: number }> };

export type OrgEventPayoutsQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
}>;


export type OrgEventPayoutsQuery = { __typename: 'Query', payoutRequestsByEvent: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', requestId: string, status: PayoutRequestStatus, requestedAmount: string, currency: string }> } };

export type OrganizerEscrowAccountsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerEscrowAccountsQuery = { __typename: 'Query', myEscrowAccounts: { __typename: 'EscrowAccountOffsetPage', data: Array<{ __typename: 'EventEscrowAccount', id: string, accountNumber: string, eventId: string, eventTitle: string | null, currentBalance: string, totalDeposits: string, totalWithdrawals: string, totalRefunds: string, totalCommissions: string, pendingWithdrawals: string | null, currency: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, event: { __typename: 'Event', id: string, title: string } | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, currentPage: number | null, hasNext: boolean | null } } };

export type OrganizerEscrowTransactionsQueryVariables = Exact<{
  escrowAccountId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerEscrowTransactionsQuery = { __typename: 'Query', escrowTransactions: { __typename: 'EscrowTransactionOffsetPage', data: Array<{ __typename: 'StandaloneEscrowTransaction', id: string, type: string, category: string, amount: string, balanceAfter: string, currency: string, description: string | null, journalEntryId: string | null, timestamp: string }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, currentPage: number | null, hasNext: boolean | null } } };

export type OrganizerPayoutEligibilityQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type OrganizerPayoutEligibilityQuery = { __typename: 'Query', payoutEligibility: { __typename: 'PayoutEligibility', eligible: boolean, reasons: Array<PayoutBlockedReason>, availableAmount: string, currency: string, opensAt: string | null, minimumAmount: string } };

export type OrganizerCancelPayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OrganizerCancelPayoutRequestMutation = { __typename: 'Mutation', cancelPayoutRequest: { __typename: 'PayoutRequest', id: string, status: PayoutRequestStatus } };

export type OrganizerStartBankVerificationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrganizerStartBankVerificationMutation = { __typename: 'Mutation', startBankVerification: { __typename: 'BankAccount', id: string, status: string } };

export type OrganizerConfirmBankVerificationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  amount: Scalars['BigDecimal']['input'];
}>;


export type OrganizerConfirmBankVerificationMutation = { __typename: 'Mutation', confirmBankVerification: { __typename: 'BankAccount', id: string, status: string, isVerified: boolean } };

export type OrganizerPayoutWalletQueryVariables = Exact<{ [key: string]: never; }>;


export type OrganizerPayoutWalletQuery = { __typename: 'Query', myOrganization: { __typename: 'Organization', id: string, payoutConfig: { __typename: 'PayoutConfig', mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean, status: PayoutAccountStatus, rejectionReason: string | null, suspended: boolean, suspendedReason: string | null, testDepositSentAt: string | null, verificationAttemptsLeft: number } | null } | null } | null };

export type OrganizerSetMobileMoneyAccountMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  input: SetMobileMoneyAccountInput;
}>;


export type OrganizerSetMobileMoneyAccountMutation = { __typename: 'Mutation', setMobileMoneyAccount: { __typename: 'Organization', id: string, payoutConfig: { __typename: 'PayoutConfig', mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean, status: PayoutAccountStatus, testDepositSentAt: string | null, verificationAttemptsLeft: number } | null } | null } | null };

export type OrganizerMediaQueryVariables = Exact<{
  filter: InputMaybe<MediaFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type OrganizerMediaQuery = { __typename: 'Query', myMedia: { __typename: 'MediaAssetConnection', edges: Array<{ __typename: 'MediaAssetEdge', cursor: string, node: { __typename: 'MediaAsset', id: string, url: string, fileName: string, contentType: string, sizeBytes: number, title: string | null, altText: string | null, eventId: string | null, status: MediaStatus, flaggedReason: string | null, removedReason: string | null, createdAt: string | null } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean | null, endCursor: string | null } } };

export type OrganizerUploadMediaMutationVariables = Exact<{
  input: UploadMediaInput;
}>;


export type OrganizerUploadMediaMutation = { __typename: 'Mutation', uploadMedia: { __typename: 'MediaAsset', id: string, url: string, fileName: string, contentType: string, sizeBytes: number, title: string | null, altText: string | null, eventId: string | null, status: MediaStatus, createdAt: string | null } };

export type OrganizerUpdateMediaMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateMediaInput;
}>;


export type OrganizerUpdateMediaMutation = { __typename: 'Mutation', updateMedia: { __typename: 'MediaAsset', id: string, title: string | null, altText: string | null, eventId: string | null } };

export type OrganizerDeleteMediaMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrganizerDeleteMediaMutation = { __typename: 'Mutation', deleteMedia: string };

export type OrganizerNotificationsQueryVariables = Exact<{
  first: InputMaybe<Scalars['Int']['input']>;
}>;


export type OrganizerNotificationsQuery = { __typename: 'Query', unreadNotificationCount: number, myNotifications: { __typename: 'NotificationConnection', totalCount: number | null, edges: Array<{ __typename: 'NotificationEdge', cursor: string, node: { __typename: 'Notification', id: string, type: NotificationType, title: string, body: string, actionUrl: string | null, status: NotificationStatus, readAt: string | null, createdAt: string } }> } };

export type OrganizerMarkAllNotificationsReadMutationVariables = Exact<{ [key: string]: never; }>;


export type OrganizerMarkAllNotificationsReadMutation = { __typename: 'Mutation', markAllNotificationsRead: number };

export type OrganizerMarkNotificationReadMutationVariables = Exact<{
  notificationId: Scalars['ID']['input'];
}>;


export type OrganizerMarkNotificationReadMutation = { __typename: 'Mutation', markNotificationRead: { __typename: 'Notification', id: string, readAt: string | null, status: NotificationStatus } | null };

export type OrganizerContextQueryVariables = Exact<{ [key: string]: never; }>;


export type OrganizerContextQuery = { __typename: 'Query', myOrganization: { __typename: 'Organization', id: string, name: string, slug: string, status: OrganizationStatus, ownerId: string, commissionRate: number | null, deletionRequestedAt: string | null, deletionScheduledFor: string | null, settings: { __typename: 'OrganizationSettings', id: string, managersCanViewFinancials: boolean, adminsCanRequestPayouts: boolean } | null } | null };

export type OrganizerMembershipQueryVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OrganizerMembershipQuery = { __typename: 'Query', myOrganizationMembership: { __typename: 'OrganizationMember', id: string, userId: string, role: OrganizationRole, status: MemberStatus, customPermissions: Array<string> | null, deniedPermissions: Array<string> | null } | null };

export type OrganizerPlatformRulesQueryVariables = Exact<{ [key: string]: never; }>;


export type OrganizerPlatformRulesQuery = { __typename: 'Query', platformRules: { __typename: 'PlatformRules', version: number, updatedAt: string | null, updatedBy: string | null, currency: string, commissionDefault: number, commissionRate: number | null, minimumPayout: string | null, reservationHoldMinutes: number, reservationGraceMinutes: number, escrowHoldDays: number, refundCutoffHours: number, maxTicketsPerBooking: number, rescheduleLimit: number, refundPolicies: Array<{ __typename: 'RulesRefundPolicy', code: string, label: string, summary: string, rules: Array<{ __typename: 'RulesRefundTier', daysBefore: number, percent: number }> }>, approval: { __typename: 'PlatformRulesApproval', slaHours: number, warnHours: number, autoEscalation: boolean, escalationDelayHours: number, requireCommentsOnRejection: boolean, requireCommentsOnChangesRequested: boolean, allowSelfApproval: boolean } } };

export type OrgEventPromosQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type OrgEventPromosQuery = { __typename: 'Query', eventPromoCodes: Array<{ __typename: 'PromoCode', id: string, code: string, eventId: string | null, discountType: DiscountType, discountValue: string, maxUses: number | null, currentUses: number, validFrom: string | null, validUntil: string | null, minPurchaseAmount: string | null, maxDiscountAmount: string | null, applicableTiers: Array<string> | null, isActive: boolean }> };

export type OrgCreatePromoMutationVariables = Exact<{
  input: CreatePromoCodeInput;
}>;


export type OrgCreatePromoMutation = { __typename: 'Mutation', createPromoCode: { __typename: 'PromoCode', id: string } };

export type OrgUpdatePromoMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdatePromoCodeInput;
}>;


export type OrgUpdatePromoMutation = { __typename: 'Mutation', updatePromoCode: { __typename: 'PromoCode', id: string } };

export type OrgActivatePromoMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgActivatePromoMutation = { __typename: 'Mutation', activatePromoCode: { __typename: 'PromoCode', id: string, isActive: boolean } };

export type OrgDeactivatePromoMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgDeactivatePromoMutation = { __typename: 'Mutation', deactivatePromoCode: { __typename: 'PromoCode', id: string, isActive: boolean } };

export type OrgDeletePromoMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OrgDeletePromoMutation = { __typename: 'Mutation', deletePromoCode: string };

export type SettingsOrganizationQueryVariables = Exact<{ [key: string]: never; }>;


export type SettingsOrganizationQuery = { __typename: 'Query', myOrganization: { __typename: 'Organization', id: string, name: string, slug: string, tagline: string | null, description: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, businessType: BusinessType | null, taxId: string | null, businessRegistrationNumber: string | null, yearEstablished: number | null, businessPhone: string | null, businessEmail: string | null, status: OrganizationStatus, commissionRate: number | null, deletionRequestedAt: string | null, deletionScheduledFor: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, addressLine2: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null, settings: { __typename: 'OrganizationSettings', id: string, requireEventApproval: boolean, allowMembersToInvite: boolean, inviteRequiresApproval: boolean, managersCanViewFinancials: boolean, adminsCanRequestPayouts: boolean, notifyOwnerOnMemberJoin: boolean, notifyOwnerOnEventCreated: boolean, notifyOwnerOnPayoutRequest: boolean } | null } | null };

export type SettingsUpdateOrganizationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateOrganizationInput;
}>;


export type SettingsUpdateOrganizationMutation = { __typename: 'Mutation', updateOrganization: { __typename: 'Organization', id: string, name: string, description: string | null, logoUrl: string | null, bannerUrl: string | null, tagline: string | null, website: string | null } | null };

export type SettingsRequestOrganizationDeletionMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type SettingsRequestOrganizationDeletionMutation = { __typename: 'Mutation', requestOrganizationDeletion: { __typename: 'Organization', id: string, status: OrganizationStatus, deletionRequestedAt: string | null, deletionScheduledFor: string | null } | null };

export type SettingsCancelOrganizationDeletionMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type SettingsCancelOrganizationDeletionMutation = { __typename: 'Mutation', cancelOrganizationDeletion: { __typename: 'Organization', id: string, status: OrganizationStatus, deletionRequestedAt: string | null, deletionScheduledFor: string | null } | null };

export type SettingsUpdateOrganizationSettingsMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateOrganizationSettingsInput;
}>;


export type SettingsUpdateOrganizationSettingsMutation = { __typename: 'Mutation', updateOrganizationSettings: { __typename: 'Organization', id: string, settings: { __typename: 'OrganizationSettings', id: string, requireEventApproval: boolean, allowMembersToInvite: boolean, inviteRequiresApproval: boolean, managersCanViewFinancials: boolean, adminsCanRequestPayouts: boolean, notifyOwnerOnMemberJoin: boolean, notifyOwnerOnEventCreated: boolean, notifyOwnerOnPayoutRequest: boolean } | null } | null };

export type SettingsIsSlugAvailableQueryVariables = Exact<{
  slug: Scalars['String']['input'];
}>;


export type SettingsIsSlugAvailableQuery = { __typename: 'Query', isSlugAvailable: boolean };

export type SettingsMeQueryVariables = Exact<{ [key: string]: never; }>;


export type SettingsMeQuery = { __typename: 'Query', me: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string, email: string | null, phoneNumber: string | null } | null };

export type SettingsNotificationPrefsQueryVariables = Exact<{ [key: string]: never; }>;


export type SettingsNotificationPrefsQuery = { __typename: 'Query', myNotificationPreferences: { __typename: 'NotificationPreferences', id: string, emailEnabled: boolean, smsEnabled: boolean, whatsappEnabled: boolean, pushEnabled: boolean, inAppEnabled: boolean, ticketNotifications: boolean, eventReminders: boolean, eventUpdates: boolean, paymentNotifications: boolean, teamNotifications: boolean, marketingEmails: boolean, systemAnnouncements: boolean, reminderHoursBefore: number, quietHoursStart: string | null, quietHoursEnd: string | null, timezone: string | null } | null };

export type SettingsUpdateNotificationPrefsMutationVariables = Exact<{
  input: UpdateNotificationPreferencesInput;
}>;


export type SettingsUpdateNotificationPrefsMutation = { __typename: 'Mutation', updateNotificationPreferences: { __typename: 'NotificationPreferences', id: string } | null };

export type SettingsUpdateProfileMutationVariables = Exact<{
  input: UpdateUserInput;
}>;


export type SettingsUpdateProfileMutation = { __typename: 'Mutation', updateMyProfile: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string } };

export type OrganizerRosterQueryVariables = Exact<{ [key: string]: never; }>;


export type OrganizerRosterQuery = { __typename: 'Query', myOrganization: { __typename: 'Organization', id: string, ownerId: string, members: Array<{ __typename: 'OrganizationMember', id: string, userId: string, role: OrganizationRole, status: MemberStatus, joinedAt: string, lastActiveAt: string | null, customPermissions: Array<string> | null, deniedPermissions: Array<string> | null, user: { __typename: 'User', id: string, fullName: string, username: string | null } | null }> | null } | null };

export type OrganizerInvitationsQueryVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OrganizerInvitationsQuery = { __typename: 'Query', pendingInvitations: { __typename: 'TeamInvitationOffsetPage', content: Array<{ __typename: 'TeamInvitation', id: string, email: string | null, phoneNumber: string | null, inviteeName: string | null, proposedRole: OrganizationRole, message: string | null, expiresAt: string, status: InvitationStatus, createdAt: string, eventAccessGrants: Array<{ __typename: 'EventAccessProposal', eventId: string, role: EventRole, expiresAt: string | null }> | null }> } };

export type OrganizerEventAccessGrantsQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type OrganizerEventAccessGrantsQuery = { __typename: 'Query', eventAccessGrants: { __typename: 'EventAccessGrantOffsetPage', content: Array<{ __typename: 'EventAccessGrant', id: string, userId: string, eventId: string, eventRole: EventRole, reason: string | null, status: AccessGrantStatus, expiresAt: string | null, user: { __typename: 'User', id: string, fullName: string } | null }> } };

export type OrganizerOrgAccessGrantsQueryVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OrganizerOrgAccessGrantsQuery = { __typename: 'Query', organizationEventAccessGrants: { __typename: 'EventAccessGrantOffsetPage', content: Array<{ __typename: 'EventAccessGrant', id: string, userId: string, eventId: string, eventRole: EventRole, reason: string | null, status: AccessGrantStatus, expiresAt: string | null, user: { __typename: 'User', id: string, fullName: string } | null }> } };

export type OrganizerIncomingTransfersQueryVariables = Exact<{ [key: string]: never; }>;


export type OrganizerIncomingTransfersQuery = { __typename: 'Query', myPendingOwnershipTransfers: Array<{ __typename: 'OwnershipTransferRequest', id: string, status: TransferStatus, expiresAt: string, reason: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, currentOwner: { __typename: 'User', id: string, fullName: string } | null }> };

export type OrganizerPendingTransferQueryVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OrganizerPendingTransferQuery = { __typename: 'Query', pendingOwnershipTransfer: { __typename: 'OwnershipTransferRequest', id: string, newOwnerId: string, status: TransferStatus, expiresAt: string, initiatedAt: string, newOwner: { __typename: 'User', id: string, fullName: string } | null } | null };

export type OrganizerUpdateMemberRoleMutationVariables = Exact<{
  memberId: Scalars['ID']['input'];
  input: UpdateMemberRoleInput;
}>;


export type OrganizerUpdateMemberRoleMutation = { __typename: 'Mutation', updateMemberRole: { __typename: 'OrganizationMember', id: string, role: OrganizationRole } | null };

export type OrganizerSuspendMemberMutationVariables = Exact<{
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type OrganizerSuspendMemberMutation = { __typename: 'Mutation', suspendMember: { __typename: 'OrganizationMember', id: string, status: MemberStatus } | null };

export type OrganizerReactivateMemberMutationVariables = Exact<{
  memberId: Scalars['ID']['input'];
}>;


export type OrganizerReactivateMemberMutation = { __typename: 'Mutation', reactivateMember: { __typename: 'OrganizationMember', id: string, status: MemberStatus } | null };

export type OrganizerRemoveMemberMutationVariables = Exact<{
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type OrganizerRemoveMemberMutation = { __typename: 'Mutation', removeMember: boolean };

export type OrganizerLeaveMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OrganizerLeaveMutation = { __typename: 'Mutation', leaveOrganization: boolean };

export type OrganizerInviteMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  input: InviteMemberInput;
}>;


export type OrganizerInviteMutation = { __typename: 'Mutation', inviteTeamMember: { __typename: 'TeamInvitation', id: string, email: string | null } | null };

export type OrganizerResendInviteMutationVariables = Exact<{
  invitationId: Scalars['ID']['input'];
}>;


export type OrganizerResendInviteMutation = { __typename: 'Mutation', resendInvitation: { __typename: 'TeamInvitation', id: string, status: InvitationStatus } | null };

export type OrganizerRevokeInviteMutationVariables = Exact<{
  invitationId: Scalars['ID']['input'];
}>;


export type OrganizerRevokeInviteMutation = { __typename: 'Mutation', revokeInvitation: { __typename: 'TeamInvitation', id: string, status: InvitationStatus } | null };

export type OrganizerInitiateTransferMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  newOwnerId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type OrganizerInitiateTransferMutation = { __typename: 'Mutation', initiateOwnershipTransfer: { __typename: 'OwnershipTransferRequest', id: string, status: TransferStatus } | null };

export type OrganizerCancelTransferMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OrganizerCancelTransferMutation = { __typename: 'Mutation', cancelOwnershipTransfer: { __typename: 'OwnershipTransferRequest', id: string, status: TransferStatus } | null };

export type OrganizerRequestTransferCodeMutationVariables = Exact<{
  token: Scalars['String']['input'];
}>;


export type OrganizerRequestTransferCodeMutation = { __typename: 'Mutation', requestOwnershipTransferCode: boolean };

export type OrganizerAcceptTransferMutationVariables = Exact<{
  token: Scalars['String']['input'];
  confirmationCode: Scalars['String']['input'];
}>;


export type OrganizerAcceptTransferMutation = { __typename: 'Mutation', acceptOwnershipTransfer: { __typename: 'OwnershipTransferRequest', id: string, status: TransferStatus } | null };

export type OrganizerDeclineTransferMutationVariables = Exact<{
  token: Scalars['String']['input'];
}>;


export type OrganizerDeclineTransferMutation = { __typename: 'Mutation', declineOwnershipTransfer: { __typename: 'OwnershipTransferRequest', id: string, status: TransferStatus } | null };

export type OrganizerGrantAccessMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
  role: EventRole;
  reason: InputMaybe<Scalars['String']['input']>;
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
}>;


export type OrganizerGrantAccessMutation = { __typename: 'Mutation', grantEventAccess: { __typename: 'EventAccessGrant', id: string, status: AccessGrantStatus } | null };

export type OrganizerUpdateGrantMutationVariables = Exact<{
  accessId: Scalars['ID']['input'];
  newRole: InputMaybe<EventRole>;
  expiresAt: InputMaybe<Scalars['DateTime']['input']>;
}>;


export type OrganizerUpdateGrantMutation = { __typename: 'Mutation', updateEventAccess: { __typename: 'EventAccessGrant', id: string, eventRole: EventRole } | null };

export type OrganizerRevokeGrantMutationVariables = Exact<{
  accessId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type OrganizerRevokeGrantMutation = { __typename: 'Mutation', revokeEventAccess: { __typename: 'EventAccessGrant', id: string, status: AccessGrantStatus } | null };

export type OrganizerApplicationsQueryVariables = Exact<{
  status: InputMaybe<OrganizationStatus>;
  search: InputMaybe<Scalars['String']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizerApplicationsQuery = { __typename: 'Query', organizations: { __typename: 'OrganizationOffsetPage', content: Array<{ __typename: 'Organization', id: string, name: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, description: string | null, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, rejectionReason: string | null, submittedAt: string | null, payoutAccountVerified: boolean, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, country: string | null } | null, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, verificationDocuments: Array<{ __typename: 'VerificationDocument', id: string, documentType: string, fileName: string | null, fileSize: number | null, status: DocumentStatus, uploadedAt: string, rejectionReason: string | null }> | null, payoutConfig: { __typename: 'PayoutConfig', verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, verified: boolean } | null } | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null, hasNext: boolean | null, hasPrevious: boolean | null } } };

export type OrganizationDocumentsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OrganizationDocumentsQuery = { __typename: 'Query', organizations: { __typename: 'OrganizationOffsetPage', content: Array<{ __typename: 'Organization', id: string, name: string, verificationDocuments: Array<{ __typename: 'VerificationDocument', id: string, documentType: string, fileName: string | null, fileSize: number | null, status: DocumentStatus, uploadedAt: string, rejectionReason: string | null }> | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null, hasNext: boolean | null, hasPrevious: boolean | null } } };

export type PendingApprovalEventsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type PendingApprovalEventsQuery = { __typename: 'Query', events: { __typename: 'EventOffsetPage', pageNumber: number, pageSize: number, totalElements: number, totalPages: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'Event', id: string, title: string, status: EventStatus, organizerId: string, organizerName: string, eventDateTime: string, cityName: string | null, locationName: string | null, totalCapacity: number, minTicketPrice: string | null, currency: string | null, submittedForApprovalAt: string | null, approvalBlockers: Array<ApprovalBlocker>, category: { __typename: 'EventCategory', id: string, name: string } | null }> }, pendingApprovalTimelines: { __typename: 'ApprovalTimelineOffsetPage', content: Array<{ __typename: 'ApprovalTimeline', eventId: string, assignedReviewerId: string | null, assignedReviewerName: string | null, submittedAt: string | null, slaDeadline: string | null, isOverdue: boolean, hoursUntilDeadline: number | null, submissionCount: number, hasActiveEscalation: boolean, escalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus, triggeredAt: string } | null }> } };

export type ApprovalTimelineQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
}>;


export type ApprovalTimelineQuery = { __typename: 'Query', approvalTimeline: { __typename: 'ApprovalTimeline', eventId: string, eventTitle: string, organizerName: string, currentStatus: EventStatus, assignedReviewerId: string | null, assignedReviewerName: string | null, submittedAt: string | null, slaDeadline: string | null, isOverdue: boolean, hoursUntilDeadline: number | null, submissionCount: number, totalComments: number, hasActiveEscalation: boolean, escalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus, reason: string, triggeredAt: string, acknowledgedAt: string | null, escalatedToName: string, hoursOverdue: number } | null, timelineEvents: Array<{ __typename: 'TimelineEvent', id: string, timestamp: string, action: ApprovalAction, actorName: string, description: string, comments: string | null, isEscalationRelated: boolean }> } | null };

export type ReviewerCandidatesQueryVariables = Exact<{
  role: InputMaybe<UserType>;
}>;


export type ReviewerCandidatesQuery = { __typename: 'Query', users: { __typename: 'UserOffsetPage', content: Array<{ __typename: 'User', id: string, fullName: string }> } };

export type AssignEventReviewerMutationVariables = Exact<{
  input: AssignReviewerInput;
}>;


export type AssignEventReviewerMutation = { __typename: 'Mutation', assignEventReviewer: { __typename: 'ApprovalTimeline', eventId: string, eventTitle: string, organizerName: string, currentStatus: EventStatus, assignedReviewerId: string | null, assignedReviewerName: string | null, submittedAt: string | null, slaDeadline: string | null, isOverdue: boolean, hoursUntilDeadline: number | null, submissionCount: number, totalComments: number, hasActiveEscalation: boolean, escalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus, reason: string, triggeredAt: string, acknowledgedAt: string | null, escalatedToName: string, hoursOverdue: number } | null, timelineEvents: Array<{ __typename: 'TimelineEvent', id: string, timestamp: string, action: ApprovalAction, actorName: string, description: string, comments: string | null, isEscalationRelated: boolean }> } };

export type UnassignEventReviewerMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type UnassignEventReviewerMutation = { __typename: 'Mutation', unassignEventReviewer: { __typename: 'ApprovalTimeline', eventId: string, eventTitle: string, organizerName: string, currentStatus: EventStatus, assignedReviewerId: string | null, assignedReviewerName: string | null, submittedAt: string | null, slaDeadline: string | null, isOverdue: boolean, hoursUntilDeadline: number | null, submissionCount: number, totalComments: number, hasActiveEscalation: boolean, escalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus, reason: string, triggeredAt: string, acknowledgedAt: string | null, escalatedToName: string, hoursOverdue: number } | null, timelineEvents: Array<{ __typename: 'TimelineEvent', id: string, timestamp: string, action: ApprovalAction, actorName: string, description: string, comments: string | null, isEscalationRelated: boolean }> } };

export type AddApprovalCommentMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  comment: Scalars['String']['input'];
  isInternal: InputMaybe<Scalars['Boolean']['input']>;
}>;


export type AddApprovalCommentMutation = { __typename: 'Mutation', addApprovalComment: { __typename: 'ApprovalTimeline', eventId: string, eventTitle: string, organizerName: string, currentStatus: EventStatus, assignedReviewerId: string | null, assignedReviewerName: string | null, submittedAt: string | null, slaDeadline: string | null, isOverdue: boolean, hoursUntilDeadline: number | null, submissionCount: number, totalComments: number, hasActiveEscalation: boolean, escalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus, reason: string, triggeredAt: string, acknowledgedAt: string | null, escalatedToName: string, hoursOverdue: number } | null, timelineEvents: Array<{ __typename: 'TimelineEvent', id: string, timestamp: string, action: ApprovalAction, actorName: string, description: string, comments: string | null, isEscalationRelated: boolean }> } };

export type AcknowledgeEscalationMutationVariables = Exact<{
  escalationId: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
}>;


export type AcknowledgeEscalationMutation = { __typename: 'Mutation', acknowledgeEscalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus } };

export type TriggerManualEscalationMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
  escalateTo: Scalars['String']['input'];
}>;


export type TriggerManualEscalationMutation = { __typename: 'Mutation', triggerManualEscalation: { __typename: 'ApprovalEscalation', id: string, status: EscalationStatus } };

export type FeatureEventMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  featured: Scalars['Boolean']['input'];
}>;


export type FeatureEventMutation = { __typename: 'Mutation', featureEvent: { __typename: 'Event', id: string, featured: boolean } };

export type CancelEventAsAdminMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: EventCancellationInput;
}>;


export type CancelEventAsAdminMutation = { __typename: 'Mutation', cancelEvent: { __typename: 'EventCancellationResponse', ticketsAffected: number, refundSagaInitiated: boolean, event: { __typename: 'Event', id: string, status: EventStatus } | null } };

export type CreateEventCategoryMutationVariables = Exact<{
  input: CreateReferenceDataInput;
}>;


export type CreateEventCategoryMutation = { __typename: 'Mutation', createReferenceData: { __typename: 'ReferenceData', id: string, code: string, name: string, description: string | null, isActive: boolean } };

export type UpdateEventCategoryMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateReferenceDataInput;
}>;


export type UpdateEventCategoryMutation = { __typename: 'Mutation', updateReferenceData: { __typename: 'ReferenceData', id: string, code: string, name: string, description: string | null, isActive: boolean } };

export type DeleteEventCategoryMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteEventCategoryMutation = { __typename: 'Mutation', deleteReferenceData: string };

export type SetEventCategoryActiveMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  active: Scalars['Boolean']['input'];
}>;


export type SetEventCategoryActiveMutation = { __typename: 'Mutation', setReferenceDataActive: { __typename: 'ReferenceData', id: string, code: string, name: string, description: string | null, isActive: boolean } };

export type CreateProvinceMutationVariables = Exact<{
  input: CreateProvinceInput;
}>;


export type CreateProvinceMutation = { __typename: 'Mutation', createProvince: { __typename: 'Province', id: string, name: string, code: string, country: string, cityCount: number | null, isActive: boolean } };

export type UpdateProvinceMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateProvinceInput;
}>;


export type UpdateProvinceMutation = { __typename: 'Mutation', updateProvince: { __typename: 'Province', id: string, name: string, code: string, country: string, cityCount: number | null, isActive: boolean } };

export type DeleteProvinceMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteProvinceMutation = { __typename: 'Mutation', deleteProvince: string };

export type CreateCityMutationVariables = Exact<{
  input: CreateCityInput;
}>;


export type CreateCityMutation = { __typename: 'Mutation', createCity: { __typename: 'City', id: string, name: string, code: string | null, provinceId: string | null, province: string | null, country: string | null, eventCount: number | null, isActive: boolean } };

export type UpdateCityMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateCityInput;
}>;


export type UpdateCityMutation = { __typename: 'Mutation', updateCity: { __typename: 'City', id: string, name: string, code: string | null, provinceId: string | null, province: string | null, country: string | null, eventCount: number | null, isActive: boolean } };

export type DeleteCityMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteCityMutation = { __typename: 'Mutation', deleteCity: string };

export type AddEventApprovalCommentMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  comment: Scalars['String']['input'];
  isInternal: InputMaybe<Scalars['Boolean']['input']>;
}>;


export type AddEventApprovalCommentMutation = { __typename: 'Mutation', addApprovalComment: { __typename: 'ApprovalTimeline', eventId: string } };

export type AdminEventsTableQueryVariables = Exact<{
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminEventsTableQuery = { __typename: 'Query', events: { __typename: 'EventOffsetPage', pageNumber: number, pageSize: number, totalElements: number, totalPages: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'Event', id: string, title: string, status: EventStatus, published: boolean, featured: boolean, eventDateTime: string, endDateTime: string, organizerId: string, organizerName: string, organizationId: string | null, locationName: string | null, cityName: string | null, categoryId: string | null, totalCapacity: number, soldTickets: number, currency: string | null, minTicketPrice: string | null, submittedForApprovalAt: string | null, approvalDeadline: string | null, isOverdue: boolean | null, category: { __typename: 'EventCategory', id: string, name: string } | null }> } };

export type AdminEventDetailQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type AdminEventDetailQuery = { __typename: 'Query', event: { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, published: boolean, publishedAt: string | null, featured: boolean, eventDateTime: string, endDateTime: string, organizerId: string, organizerName: string, organizationId: string | null, locationName: string | null, locationAddress: string | null, cityName: string | null, categoryId: string | null, totalCapacity: number, soldTickets: number, availableTickets: number, currency: string | null, minTicketPrice: string | null, maxTicketPrice: string | null, refundPolicy: string | null, cancellationPolicy: string | null, bannerImageUrl: string | null, thumbnailImageUrl: string | null, galleryImages: Array<string> | null, submittedForApprovalAt: string | null, approvalDeadline: string | null, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectedBy: string | null, rejectionReason: string | null, isOverdue: boolean | null, approvalBlockers: Array<ApprovalBlocker>, createdAt: string | null, updatedAt: string | null, organization: { __typename: 'Organization', id: string, name: string, businessEmail: string | null, businessPhone: string | null } | null, category: { __typename: 'EventCategory', id: string, name: string } | null, location: { __typename: 'Location', id: string, city: string, province: string | null, country: string } | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, name: string, code: string, price: string, currency: string, quantity: number, soldQuantity: number, isActive: boolean, isHidden: boolean, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null }> | null } | null };

export type EventApprovalTimelineQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
}>;


export type EventApprovalTimelineQuery = { __typename: 'Query', approvalTimeline: { __typename: 'ApprovalTimeline', eventId: string, currentStatus: EventStatus, assignedReviewerName: string | null, submittedAt: string | null, slaDeadline: string | null, isOverdue: boolean, hoursUntilDeadline: number | null, submissionCount: number, hasActiveEscalation: boolean, escalation: { __typename: 'ApprovalEscalation', status: EscalationStatus } | null, timelineEvents: Array<{ __typename: 'TimelineEvent', id: string, timestamp: string, action: ApprovalAction, actorName: string, actorRole: string | null, description: string, comments: string | null, isEscalationRelated: boolean }> } | null };

export type ProvincesAdminQueryVariables = Exact<{ [key: string]: never; }>;


export type ProvincesAdminQuery = { __typename: 'Query', provinces: Array<{ __typename: 'Province', id: string, name: string, code: string, country: string, cityCount: number | null, isActive: boolean }> };

export type CitiesAdminQueryVariables = Exact<{
  provinceId: InputMaybe<Scalars['String']['input']>;
}>;


export type CitiesAdminQuery = { __typename: 'Query', cities: Array<{ __typename: 'City', id: string, name: string, code: string | null, provinceId: string | null, province: string | null, country: string | null, eventCount: number | null, isActive: boolean }> };

export type EscrowByEventQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
}>;


export type EscrowByEventQuery = { __typename: 'Query', escrowAccountByEvent: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, eventId: string, currentBalance: string, totalDeposits: string, totalWithdrawals: string, totalRefunds: string, totalCommissions: string, pendingWithdrawals: string | null, currency: string, status: EscrowAccountStatus } | null };

export type PayoutsByEventQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
}>;


export type PayoutsByEventQuery = { __typename: 'Query', payoutRequests: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, requestedAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string }> } };

export type RefundsByEventQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
}>;


export type RefundsByEventQuery = { __typename: 'Query', refundRequests: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, refundAmount: string, currency: string, status: RefundRequestStatus, requestedAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null } } };

export type ApproveVerificationDocumentMutationVariables = Exact<{
  documentId: Scalars['ID']['input'];
}>;


export type ApproveVerificationDocumentMutation = { __typename: 'Mutation', approveVerificationDocument: { __typename: 'VerificationDocument', id: string, documentType: string, documentUrl: string, fileName: string | null, fileSize: number | null, mimeType: string | null, status: DocumentStatus, uploadedAt: string, verifiedAt: string | null, verifiedById: string | null, rejectionReason: string | null, verifiedBy: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string } | null } | null };

export type RejectVerificationDocumentMutationVariables = Exact<{
  documentId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type RejectVerificationDocumentMutation = { __typename: 'Mutation', rejectVerificationDocument: { __typename: 'VerificationDocument', id: string, documentType: string, documentUrl: string, fileName: string | null, fileSize: number | null, mimeType: string | null, status: DocumentStatus, uploadedAt: string, verifiedAt: string | null, verifiedById: string | null, rejectionReason: string | null, verifiedBy: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string } | null } | null };

export type VerificationDocumentFieldsFragment = { __typename: 'VerificationDocument', id: string, documentType: string, documentUrl: string, fileName: string | null, fileSize: number | null, mimeType: string | null, status: DocumentStatus, uploadedAt: string, verifiedAt: string | null, verifiedById: string | null, rejectionReason: string | null, verifiedBy: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string } | null };

export type PendingVerificationDocumentsQueryVariables = Exact<{ [key: string]: never; }>;


export type PendingVerificationDocumentsQuery = { __typename: 'Query', pendingVerificationDocuments: Array<{ __typename: 'VerificationDocument', id: string, documentType: string, documentUrl: string, fileName: string | null, fileSize: number | null, mimeType: string | null, status: DocumentStatus, uploadedAt: string, verifiedAt: string | null, verifiedById: string | null, rejectionReason: string | null, verifiedBy: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string } | null }> };

export type EventListFieldsFragment = { __typename: 'Event', id: string, title: string, status: EventStatus, published: boolean, eventDateTime: string, endDateTime: string, organizerName: string, organizerId: string, locationName: string | null, cityName: string | null, totalCapacity: number, soldTickets: number, availableTickets: number, minTicketPrice: string | null, currency: string | null, submittedForApprovalAt: string | null, approvalDeadline: string | null, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, isOverdue: boolean | null, category: { __typename: 'EventCategory', id: string, name: string } | null };

export type AdminEventsQueryVariables = Exact<{
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminEventsQuery = { __typename: 'Query', events: { __typename: 'EventOffsetPage', pageNumber: number, pageSize: number, totalElements: number, totalPages: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'Event', id: string, title: string, status: EventStatus, published: boolean, eventDateTime: string, endDateTime: string, organizerName: string, organizerId: string, locationName: string | null, cityName: string | null, totalCapacity: number, soldTickets: number, availableTickets: number, minTicketPrice: string | null, currency: string | null, submittedForApprovalAt: string | null, approvalDeadline: string | null, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, isOverdue: boolean | null, category: { __typename: 'EventCategory', id: string, name: string } | null }> } };

export type EventStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type EventStatsQuery = { __typename: 'Query', eventStats: { __typename: 'EventStats', totalEvents: number, publishedEvents: number, draftEvents: number, pendingApprovalEvents: number, approvedNotPublishedEvents: number, cancelledEvents: number, completedEvents: number, rejectedEvents: number, totalCapacity: number, totalSoldTickets: number } };

export type AdminEventCategoriesQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminEventCategoriesQuery = { __typename: 'Query', referenceDataAll: { __typename: 'ReferenceDataOffsetPage', pageNumber: number, pageSize: number, totalElements: number, totalPages: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'ReferenceData', id: string, code: string, name: string, description: string | null, isActive: boolean }> }, categories: Array<{ __typename: 'EventCategory', code: string, eventCount: number | null }> };

export type AdminLocationsQueryVariables = Exact<{
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type AdminLocationsQuery = { __typename: 'Query', locations: { __typename: 'LocationConnection', edges: Array<{ __typename: 'LocationEdge', node: { __typename: 'Location', id: string, name: string, address: string, city: string, province: string | null, country: string, postalCode: string | null } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean | null, endCursor: string | null, totalCount: number | null } } };

export type ApproveEventMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  comments: InputMaybe<Scalars['String']['input']>;
}>;


export type ApproveEventMutation = { __typename: 'Mutation', approveEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type RejectEventMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  comments: Scalars['String']['input'];
}>;


export type RejectEventMutation = { __typename: 'Mutation', rejectEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type RequestEventChangesMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  comments: Scalars['String']['input'];
}>;


export type RequestEventChangesMutation = { __typename: 'Mutation', requestEventChanges: { __typename: 'Event', id: string, status: EventStatus } };

export type FinanceOpsPageFragment = { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null };

export type PayoutOpsFieldsFragment = { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null };

export type PayoutOpsListQueryVariables = Exact<{
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type PayoutOpsListQuery = { __typename: 'Query', payoutRequests: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type PayoutOpsDetailQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type PayoutOpsDetailQuery = { __typename: 'Query', payoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } | null };

export type ProcessPayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
}>;


export type ProcessPayoutRequestMutation = { __typename: 'Mutation', processPayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type CompletePayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  bankReference: Scalars['String']['input'];
}>;


export type CompletePayoutRequestMutation = { __typename: 'Mutation', completePayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type RetryPayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  idempotencyKey: Scalars['String']['input'];
}>;


export type RetryPayoutRequestMutation = { __typename: 'Mutation', retryPayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type ResumePayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
}>;


export type ResumePayoutRequestMutation = { __typename: 'Mutation', resumePayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type MarkPayoutForReviewMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  issueType: PayoutIssueType;
  notes: InputMaybe<Scalars['String']['input']>;
}>;


export type MarkPayoutForReviewMutation = { __typename: 'Mutation', markPayoutForReview: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type ResolvePayoutIssueMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  resolutionType: PayoutResolutionType;
  notes: Scalars['String']['input'];
}>;


export type ResolvePayoutIssueMutation = { __typename: 'Mutation', resolvePayoutIssue: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type EscalatePayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type EscalatePayoutRequestMutation = { __typename: 'Mutation', escalatePayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, escrowAccountId: string, bankAccountId: string, requestedAmount: string, taxAmount: string | null, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, requestedById: string, approvedAt: string | null, approvedBy: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, expectedPayoutDate: string | null, paymentReference: string | null, externalTransactionId: string | null, bankName: string | null, accountNumber: string | null, retryCount: number | null, lastError: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, reviewNotes: string | null, resolutionNotes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, bankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, accountHolderName: string } | null } };

export type BulkRetryFailedPayoutsMutationVariables = Exact<{
  payoutRequestIds: Array<Scalars['ID']['input']> | Scalars['ID']['input'];
}>;


export type BulkRetryFailedPayoutsMutation = { __typename: 'Mutation', bulkRetryFailedPayouts: { __typename: 'BulkPayoutOperationResponse', processedCount: number, failedCount: number, failedPayoutIds: Array<string> } };

export type RefundOpsFieldsFragment = { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, originalTicketPrice: string | null, refundAmount: string, refundPercentage: number | null, platformRetains: string | null, processingFee: string | null, netRefundAmount: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, additionalNotes: string | null, requestedById: string | null, requestedAt: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewComments: string | null, rejectionReason: string | null, processedAt: string | null, paymentReference: string | null, originalPaymentMethod: string | null, daysBeforeEvent: number | null, policyApplied: string | null };

export type RefundOpsListQueryVariables = Exact<{
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type RefundOpsListQuery = { __typename: 'Query', refundRequests: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, originalTicketPrice: string | null, refundAmount: string, refundPercentage: number | null, platformRetains: string | null, processingFee: string | null, netRefundAmount: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, additionalNotes: string | null, requestedById: string | null, requestedAt: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewComments: string | null, rejectionReason: string | null, processedAt: string | null, paymentReference: string | null, originalPaymentMethod: string | null, daysBeforeEvent: number | null, policyApplied: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type RefundOpsDetailQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type RefundOpsDetailQuery = { __typename: 'Query', refundRequest: { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, originalTicketPrice: string | null, refundAmount: string, refundPercentage: number | null, platformRetains: string | null, processingFee: string | null, netRefundAmount: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, additionalNotes: string | null, requestedById: string | null, requestedAt: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewComments: string | null, rejectionReason: string | null, processedAt: string | null, paymentReference: string | null, originalPaymentMethod: string | null, daysBeforeEvent: number | null, policyApplied: string | null } | null };

export type ProcessRefundRequestMutationVariables = Exact<{
  refundRequestId: Scalars['ID']['input'];
}>;


export type ProcessRefundRequestMutation = { __typename: 'Mutation', processRefundRequest: { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, originalTicketPrice: string | null, refundAmount: string, refundPercentage: number | null, platformRetains: string | null, processingFee: string | null, netRefundAmount: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, additionalNotes: string | null, requestedById: string | null, requestedAt: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewComments: string | null, rejectionReason: string | null, processedAt: string | null, paymentReference: string | null, originalPaymentMethod: string | null, daysBeforeEvent: number | null, policyApplied: string | null } };

export type BulkApproveRefundsMutationVariables = Exact<{
  refundRequestIds: Array<Scalars['ID']['input']> | Scalars['ID']['input'];
}>;


export type BulkApproveRefundsMutation = { __typename: 'Mutation', bulkApproveRefunds: { __typename: 'BulkOperationResponse', processedCount: number, failedCount: number } };

export type CreateAdminRefundRequestMutationVariables = Exact<{
  ticketId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
  bypassApproval: InputMaybe<Scalars['Boolean']['input']>;
  idempotencyKey: Scalars['String']['input'];
}>;


export type CreateAdminRefundRequestMutation = { __typename: 'Mutation', createAdminRefundRequest: { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, originalTicketPrice: string | null, refundAmount: string, refundPercentage: number | null, platformRetains: string | null, processingFee: string | null, netRefundAmount: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, additionalNotes: string | null, requestedById: string | null, requestedAt: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewComments: string | null, rejectionReason: string | null, processedAt: string | null, paymentReference: string | null, originalPaymentMethod: string | null, daysBeforeEvent: number | null, policyApplied: string | null } };

export type EscrowOpsDetailQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type EscrowOpsDetailQuery = { __typename: 'Query', escrowAccount: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, eventId: string, eventTitle: string | null, organizerId: string, currentBalance: string, totalDeposits: string, totalWithdrawals: string, totalRefunds: string, totalCommissions: string, pendingWithdrawals: string | null, currency: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, createdAt: string | null, organization: { __typename: 'Organization', id: string, name: string } | null } | null };

export type EscrowOpsTransactionsQueryVariables = Exact<{
  escrowAccountId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type EscrowOpsTransactionsQuery = { __typename: 'Query', escrowTransactions: { __typename: 'EscrowTransactionOffsetPage', data: Array<{ __typename: 'StandaloneEscrowTransaction', id: string, type: string, category: string, amount: string, balanceAfter: string, currency: string, description: string | null, payoutRequestId: string | null, refundRequestId: string | null, chargebackId: string | null, timestamp: string }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type EscrowJournalVerificationAllQueryVariables = Exact<{ [key: string]: never; }>;


export type EscrowJournalVerificationAllQuery = { __typename: 'Query', escrowJournalVerificationAll: Array<{ __typename: 'EscrowJournalVerificationResponse', eventId: string, escrowAccountId: string | null, escrowBalance: string | null, journalBalance: string | null, variance: string | null, isConsistent: boolean, status: EscrowJournalVerificationStatus | null }> };

export type LockEscrowAccountMutationVariables = Exact<{
  accountId: Scalars['ID']['input'];
  lockUntil: Scalars['DateTime']['input'];
  reason: Scalars['String']['input'];
}>;


export type LockEscrowAccountMutation = { __typename: 'Mutation', lockEscrowAccount: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, currentBalance: string } };

export type UnlockEscrowAccountMutationVariables = Exact<{
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type UnlockEscrowAccountMutation = { __typename: 'Mutation', unlockEscrowAccount: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, currentBalance: string } };

export type MarkPayoutEligibleMutationVariables = Exact<{
  accountId: Scalars['ID']['input'];
}>;


export type MarkPayoutEligibleMutation = { __typename: 'Mutation', markPayoutEligible: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, currentBalance: string } };

export type CloseEscrowAccountMutationVariables = Exact<{
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type CloseEscrowAccountMutation = { __typename: 'Mutation', closeEscrowAccount: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, currentBalance: string } };

export type ChargebackOpsFieldsFragment = { __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string };

export type ChargebackOpsListQueryVariables = Exact<{
  filter: InputMaybe<ChargebackFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type ChargebackOpsListQuery = { __typename: 'Query', chargebacks: { __typename: 'ChargebackOffsetPage', data: Array<{ __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type ChargebackOpsPendingQueryVariables = Exact<{ [key: string]: never; }>;


export type ChargebackOpsPendingQuery = { __typename: 'Query', pendingChargebacks: Array<{ __typename: 'ChargebackRecord', id: string, chargebackAmount: string, responseDeadline: string, status: ChargebackStatus }>, chargebacksPendingRecovery: Array<{ __typename: 'ChargebackRecord', id: string }> };

export type ChargebackOpsStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type ChargebackOpsStatsQuery = { __typename: 'Query', chargebackStats: { __typename: 'ChargebackStats', totalCount: number, pendingCount: number, disputedCount: number, wonCount: number, lostCount: number, totalAmount: string, recoveredAmount: string, writtenOffAmount: string, winRate: number } };

export type ReceiveChargebackMutationVariables = Exact<{
  input: ReceiveChargebackInput;
}>;


export type ReceiveChargebackMutation = { __typename: 'Mutation', receiveChargeback: { __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string } };

export type StartChargebackReviewMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
}>;


export type StartChargebackReviewMutation = { __typename: 'Mutation', startChargebackReview: { __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string } };

export type AcceptChargebackMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type AcceptChargebackMutation = { __typename: 'Mutation', acceptChargeback: { __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string } };

export type DisputeChargebackMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: DisputeChargebackInput;
}>;


export type DisputeChargebackMutation = { __typename: 'Mutation', disputeChargeback: { __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string } };

export type RecordChargebackOutcomeMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  won: Scalars['Boolean']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
}>;


export type RecordChargebackOutcomeMutation = { __typename: 'Mutation', recordChargebackOutcome: { __typename: 'ChargebackRecord', id: string, chargebackId: string, originalTransactionId: string, ticketId: string, eventId: string, organizerId: string, customerId: string, originalAmount: string, chargebackAmount: string, chargebackFee: string, currency: string, reason: ChargebackReason, status: ChargebackStatus, receivedAt: string, responseDeadline: string, evidenceSubmitted: string | null, resolvedAt: string | null, recoveryStatus: RecoveryStatus, recoveredAmount: string | null, fundSource: ChargebackFundSource | null, createdAt: string } };

export type VerifyBankAccountMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type VerifyBankAccountMutation = { __typename: 'Mutation', verifyBankAccount: { __typename: 'BankAccount', id: string, isVerified: boolean, status: string, verifiedAt: string | null } };

export type ApprovePayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  idempotencyKey: Scalars['String']['input'];
}>;


export type ApprovePayoutRequestMutation = { __typename: 'Mutation', approvePayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null } };

export type RejectPayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  rejectionReason: Scalars['String']['input'];
}>;


export type RejectPayoutRequestMutation = { __typename: 'Mutation', rejectPayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null } };

export type ApproveRefundRequestMutationVariables = Exact<{
  refundRequestId: Scalars['ID']['input'];
  reviewComments: InputMaybe<Scalars['String']['input']>;
  idempotencyKey: Scalars['String']['input'];
}>;


export type ApproveRefundRequestMutation = { __typename: 'Mutation', approveRefundRequest: { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, refundAmount: string, netRefundAmount: string | null, processingFee: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, requestedAt: string | null, reviewedAt: string | null, rejectionReason: string | null, processedAt: string | null, policyApplied: string | null } };

export type RejectRefundRequestMutationVariables = Exact<{
  refundRequestId: Scalars['ID']['input'];
  rejectionReason: Scalars['String']['input'];
}>;


export type RejectRefundRequestMutation = { __typename: 'Mutation', rejectRefundRequest: { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, refundAmount: string, netRefundAmount: string | null, processingFee: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, requestedAt: string | null, reviewedAt: string | null, rejectionReason: string | null, processedAt: string | null, policyApplied: string | null } };

export type UpdateEscrowAccountStatusMutationVariables = Exact<{
  accountId: Scalars['ID']['input'];
  status: EscrowAccountStatus;
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type UpdateEscrowAccountStatusMutation = { __typename: 'Mutation', updateEscrowAccountStatus: { __typename: 'EventEscrowAccount', id: string, accountNumber: string, eventId: string, eventTitle: string | null, organizerId: string, currentBalance: string, totalDeposits: string, totalWithdrawals: string, totalRefunds: string, totalCommissions: string, currency: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, createdAt: string | null, event: { __typename: 'Event', id: string, title: string } | null, organization: { __typename: 'Organization', id: string, name: string } | null } };

export type FinancePaginationFieldsFragment = { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null };

export type PayoutListFieldsFragment = { __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null };

export type AdminPayoutRequestsQueryVariables = Exact<{
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminPayoutRequestsQuery = { __typename: 'Query', payoutRequests: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type PayoutRequestStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type PayoutRequestStatsQuery = { __typename: 'Query', payoutRequestStats: { __typename: 'PayoutRequestStats', totalPayoutRequests: number, pendingPayoutRequests: number, approvedPayoutRequests: number, processingPayoutRequests: number, completedPayoutRequests: number, failedPayoutRequests: number, totalPayoutAmount: string, pendingPayoutAmount: string } };

export type RefundListFieldsFragment = { __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, refundAmount: string, netRefundAmount: string | null, processingFee: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, requestedAt: string | null, reviewedAt: string | null, rejectionReason: string | null, processedAt: string | null, policyApplied: string | null };

export type AdminRefundRequestsQueryVariables = Exact<{
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminRefundRequestsQuery = { __typename: 'Query', refundRequests: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, buyerId: string, refundAmount: string, netRefundAmount: string | null, processingFee: string | null, currency: string, status: RefundRequestStatus, requestType: RefundRequestType, reason: string, requestedAt: string | null, reviewedAt: string | null, rejectionReason: string | null, processedAt: string | null, policyApplied: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type RefundStatusCountQueryVariables = Exact<{
  filter: RefundRequestFilterInput;
}>;


export type RefundStatusCountQuery = { __typename: 'Query', refundRequests: { __typename: 'RefundRequestOffsetPage', pagination: { __typename: 'PaginationInfo', totalCount: number | null } } };

export type EscrowListFieldsFragment = { __typename: 'EventEscrowAccount', id: string, accountNumber: string, eventId: string, eventTitle: string | null, organizerId: string, currentBalance: string, totalDeposits: string, totalWithdrawals: string, totalRefunds: string, totalCommissions: string, currency: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, createdAt: string | null, event: { __typename: 'Event', id: string, title: string } | null, organization: { __typename: 'Organization', id: string, name: string } | null };

export type AdminEscrowAccountsQueryVariables = Exact<{
  filter: InputMaybe<EscrowAccountFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminEscrowAccountsQuery = { __typename: 'Query', escrowAccounts: { __typename: 'EscrowAccountOffsetPage', data: Array<{ __typename: 'EventEscrowAccount', id: string, accountNumber: string, eventId: string, eventTitle: string | null, organizerId: string, currentBalance: string, totalDeposits: string, totalWithdrawals: string, totalRefunds: string, totalCommissions: string, currency: string, status: EscrowAccountStatus, lockUntil: string | null, payoutEligibleAt: string | null, closedAt: string | null, createdAt: string | null, event: { __typename: 'Event', id: string, title: string } | null, organization: { __typename: 'Organization', id: string, name: string } | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type PayoutRecoveryFieldsFragment = { __typename: 'PayoutRequest', issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, stuckAt: string | null, reviewedAt: string | null, reviewNotes: string | null, retryCount: number | null, lastError: string | null };

export type PayoutRecoverySummaryQueryVariables = Exact<{ [key: string]: never; }>;


export type PayoutRecoverySummaryQuery = { __typename: 'Query', payoutRecoverySummary: { __typename: 'PayoutRecoverySummary', totalPayoutsForReview: number, pendingReviewCount: number, underReviewCount: number, stuckPayoutsCount: number, retryablePayoutsCount: number, recentlyResolvedCount: number, averageResolutionTimeMinutes: number | null, totalAmountAtRisk: string, issuesByType: Array<{ __typename: 'PayoutIssueTypeStats', issueType: PayoutIssueType, count: number, percentage: number, unresolvedCount: number, totalAmount: string }> } };

export type StuckPayoutRequestsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type StuckPayoutRequestsQuery = { __typename: 'Query', stuckPayoutRequests: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, stuckAt: string | null, reviewedAt: string | null, reviewNotes: string | null, retryCount: number | null, lastError: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type RetryablePayoutRequestsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type RetryablePayoutRequestsQuery = { __typename: 'Query', retryablePayoutRequests: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, stuckAt: string | null, reviewedAt: string | null, reviewNotes: string | null, retryCount: number | null, lastError: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type PayoutsForReviewQueryVariables = Exact<{
  reviewStatus: InputMaybe<PayoutReviewStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type PayoutsForReviewQuery = { __typename: 'Query', payoutRequestsForReview: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, rejectedAt: string | null, rejectionReason: string | null, processedAt: string | null, bankName: string | null, accountNumber: string | null, notes: string | null, issueType: PayoutIssueType | null, reviewStatus: PayoutReviewStatus | null, isStuck: boolean | null, stuckReason: string | null, stuckAt: string | null, reviewedAt: string | null, reviewNotes: string | null, retryCount: number | null, lastError: string | null, organization: { __typename: 'Organization', id: string, name: string } | null, event: { __typename: 'Event', id: string, title: string } | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type IdentityAdminCreateUserMutationVariables = Exact<{
  input: CreateUserInput;
}>;


export type IdentityAdminCreateUserMutation = { __typename: 'Mutation', createUser: { __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null } };

export type IdentityAdminUpdateUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateUserInput;
}>;


export type IdentityAdminUpdateUserMutation = { __typename: 'Mutation', updateUser: { __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null } };

export type IdentityAdminSuspendUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type IdentityAdminSuspendUserMutation = { __typename: 'Mutation', suspendUser: { __typename: 'User', id: string, accountStatus: AccountStatus } };

export type IdentityAdminUnsuspendUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminUnsuspendUserMutation = { __typename: 'Mutation', unsuspendUser: { __typename: 'User', id: string, accountStatus: AccountStatus } };

export type IdentityAdminLockUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type IdentityAdminLockUserMutation = { __typename: 'Mutation', lockUser: boolean };

export type IdentityAdminUnlockUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminUnlockUserMutation = { __typename: 'Mutation', unlockUser: boolean };

export type IdentityAdminActivateUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminActivateUserMutation = { __typename: 'Mutation', activateUser: boolean };

export type IdentityAdminDeactivateUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminDeactivateUserMutation = { __typename: 'Mutation', deactivateUser: boolean };

export type IdentityAdminSetUserRolesMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
  roles: Array<UserType> | UserType;
}>;


export type IdentityAdminSetUserRolesMutation = { __typename: 'Mutation', setUserRoles: { __typename: 'User', id: string, roles: Array<UserType> } };

export type IdentityAdminSuspendOrgMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type IdentityAdminSuspendOrgMutation = { __typename: 'Mutation', suspendOrganization: { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null } | null };

export type IdentityAdminUnsuspendOrgMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminUnsuspendOrgMutation = { __typename: 'Mutation', unsuspendOrganization: { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null } | null };

export type IdentityAdminUpdateOrgStatusMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  status: OrganizationStatus;
}>;


export type IdentityAdminUpdateOrgStatusMutation = { __typename: 'Mutation', updateOrganizationStatus: { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null } | null };

export type IdentityAdminVerifyPayoutMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  verified: Scalars['Boolean']['input'];
}>;


export type IdentityAdminVerifyPayoutMutation = { __typename: 'Mutation', verifyPayoutAccount: { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null } | null };

export type AdminPermissionCatalogueQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminPermissionCatalogueQuery = { __typename: 'Query', permissions: Array<{ __typename: 'Permission', code: string, module: string, description: string, scope: PermissionScope }> };

export type AdminRolePermissionsQueryVariables = Exact<{
  role: Scalars['String']['input'];
}>;


export type AdminRolePermissionsQuery = { __typename: 'Query', rolePermissions: { __typename: 'RolePermissions', role: string, scope: PermissionScope, permissions: Array<{ __typename: 'Permission', code: string }>, switchable: Array<{ __typename: 'Permission', code: string }> } | null };

export type AdminUserRowFieldsFragment = { __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null };

export type IdentityAdminUsersQueryVariables = Exact<{
  search: InputMaybe<Scalars['String']['input']>;
  role: InputMaybe<UserType>;
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type IdentityAdminUsersQuery = { __typename: 'Query', users: { __typename: 'UserOffsetPage', content: Array<{ __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null } } };

export type IdentityAdminUserQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminUserQuery = { __typename: 'Query', user: { __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null, contacts: Array<{ __typename: 'Contact', id: string, type: ContactType, valueMasked: string, verifiedAt: string | null, primary: boolean }>, organizationMemberships: Array<{ __typename: 'OrganizationMember', role: OrganizationRole, status: MemberStatus, organization: { __typename: 'Organization', id: string, name: string } | null }> | null } | null };

export type IdentityAdminUserByEmailQueryVariables = Exact<{
  email: Scalars['String']['input'];
}>;


export type IdentityAdminUserByEmailQuery = { __typename: 'Query', userByEmail: { __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null } | null };

export type IdentityAdminUserByPhoneQueryVariables = Exact<{
  phoneNumber: Scalars['String']['input'];
}>;


export type IdentityAdminUserByPhoneQuery = { __typename: 'Query', userByPhone: { __typename: 'User', id: string, username: string | null, email: string | null, firstName: string | null, lastName: string | null, fullName: string, phoneNumber: string | null, gender: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, phoneVerified: boolean, active: boolean, locked: boolean, lockReason: string | null, suspendReason: string | null, twoFactorEnabled: boolean, memberSince: string | null, lastLoginAt: string | null, lastActiveAt: string | null, createdAt: string, updatedAt: string | null } | null };

export type AdminOrgRowFieldsFragment = { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null };

export type IdentityAdminOrganizationsQueryVariables = Exact<{
  search: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<OrganizationStatus>;
  verified: InputMaybe<Scalars['Boolean']['input']>;
  kybStatus: InputMaybe<KybStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type IdentityAdminOrganizationsQuery = { __typename: 'Query', organizations: { __typename: 'OrganizationOffsetPage', content: Array<{ __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null } } };

export type IdentityAdminOrganizationQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type IdentityAdminOrganizationQuery = { __typename: 'Query', organization: { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, ownerId: string, businessEmail: string | null, businessPhone: string | null, taxId: string | null, businessRegistrationNumber: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, rejectionReason: string | null, suspensionReason: string | null, commissionRate: number | null, memberCount: number, totalEvents: number | null, createdAt: string, owner: { __typename: 'User', id: string, fullName: string, email: string | null, contacts: Array<{ __typename: 'Contact', valueMasked: string, primary: boolean }> } | null, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null, addressLine1: string | null } | null, payoutConfig: { __typename: 'PayoutConfig', commissionRate: number | null, preferredMethod: PayoutMethod | null, verified: boolean, isConfigured: boolean, bankAccount: { __typename: 'PayoutBankDetails', bankName: string | null, maskedAccountNumber: string | null, accountHolderName: string | null, accountType: string | null, verified: boolean } | null, mobileMoneyAccount: { __typename: 'MobileMoneyAccount', provider: MobileMoneyProvider | null, maskedPhoneNumber: string | null, accountHolderName: string | null, verified: boolean } | null } | null } | null };

export type IdentityAdminOrgMembersQueryVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type IdentityAdminOrgMembersQuery = { __typename: 'Query', organizationMembers: { __typename: 'OrganizationMemberOffsetPage', content: Array<{ __typename: 'OrganizationMember', id: string, userId: string, role: OrganizationRole, status: MemberStatus, user: { __typename: 'User', id: string, fullName: string } | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null } } };

export type IdentityAdminOrgDocumentsQueryVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type IdentityAdminOrgDocumentsQuery = { __typename: 'Query', verificationDocuments: Array<{ __typename: 'VerificationDocument', id: string, documentType: string, fileName: string | null, status: DocumentStatus, uploadedAt: string, rejectionReason: string | null }> };

export type IdentityAdminOrgEventsQueryVariables = Exact<{
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type IdentityAdminOrgEventsQuery = { __typename: 'Query', events: { __typename: 'EventOffsetPage', totalElements: number, content: Array<{ __typename: 'Event', id: string, title: string, status: EventStatus, eventDateTime: string, soldTickets: number }> } };

export type IdentityAdminUserTicketsQueryVariables = Exact<{
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type IdentityAdminUserTicketsQuery = { __typename: 'Query', ticketsByBuyerOffsetPagination: { __typename: 'TicketOffsetPage', data: Array<{ __typename: 'Ticket', id: string, ticketNumber: string, eventTitle: string, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, purchaseDate: string | null, paymentReference: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalCount: number | null } } };

export type IdentityAdminUserRefundsQueryVariables = Exact<{
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type IdentityAdminUserRefundsQuery = { __typename: 'Query', refundRequestsByBuyer: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketNumber: string, refundAmount: string, currency: string, status: RefundRequestStatus, requestedAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalCount: number | null } } };

export type LedgerChartOfAccountsQueryVariables = Exact<{ [key: string]: never; }>;


export type LedgerChartOfAccountsQuery = { __typename: 'Query', chartOfAccounts: Array<{ __typename: 'ChartOfAccountsEntry', id: string, accountCode: string, accountName: string, accountType: AccountType, subType: AccountSubType | null, parentAccountCode: string | null, currency: string, isActive: boolean, description: string | null, normalBalance: BalanceDirection }> };

export type LedgerCreateAccountMutationVariables = Exact<{
  input: CreateChartOfAccountsInput;
}>;


export type LedgerCreateAccountMutation = { __typename: 'Mutation', createChartOfAccountsEntry: { __typename: 'ChartOfAccountsEntry', id: string, accountCode: string } };

export type LedgerUpdateAccountMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: CreateChartOfAccountsInput;
}>;


export type LedgerUpdateAccountMutation = { __typename: 'Mutation', updateChartOfAccountsEntry: { __typename: 'ChartOfAccountsEntry', id: string, accountCode: string } };

export type LedgerDeactivateAccountMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type LedgerDeactivateAccountMutation = { __typename: 'Mutation', deactivateChartOfAccountsEntry: { __typename: 'ChartOfAccountsEntry', id: string, isActive: boolean } };

export type LedgerSeedChartMutationVariables = Exact<{ [key: string]: never; }>;


export type LedgerSeedChartMutation = { __typename: 'Mutation', seedChartOfAccounts: boolean };

export type LedgerJournalEntryFieldsFragment = { __typename: 'JournalEntry', id: string, entryNumber: string, correlationId: string | null, entryDate: string, description: string, type: JournalEntryType, status: JournalEntryStatus, createdBy: string | null, postedBy: string | null, postedAt: string | null, reversedBy: string | null, reversedAt: string | null, reversalEntryId: string | null, reversedByEntryId: string | null, totalDebits: string, totalCredits: string, isBalanced: boolean, lines: Array<{ __typename: 'JournalLine', accountCode: string, accountName: string, debit: string | null, credit: string | null, description: string | null }> };

export type LedgerJournalEntriesQueryVariables = Exact<{
  filter: InputMaybe<JournalEntryFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type LedgerJournalEntriesQuery = { __typename: 'Query', journalEntries: { __typename: 'JournalEntryOffsetPage', data: Array<{ __typename: 'JournalEntry', id: string, entryNumber: string, correlationId: string | null, entryDate: string, description: string, type: JournalEntryType, status: JournalEntryStatus, createdBy: string | null, postedBy: string | null, postedAt: string | null, reversedBy: string | null, reversedAt: string | null, reversalEntryId: string | null, reversedByEntryId: string | null, totalDebits: string, totalCredits: string, isBalanced: boolean, lines: Array<{ __typename: 'JournalLine', accountCode: string, accountName: string, debit: string | null, credit: string | null, description: string | null }> }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type LedgerCreateJournalEntryMutationVariables = Exact<{
  input: CreateJournalEntryInput;
}>;


export type LedgerCreateJournalEntryMutation = { __typename: 'Mutation', createJournalEntry: { __typename: 'JournalEntry', id: string, entryNumber: string, correlationId: string | null, entryDate: string, description: string, type: JournalEntryType, status: JournalEntryStatus, createdBy: string | null, postedBy: string | null, postedAt: string | null, reversedBy: string | null, reversedAt: string | null, reversalEntryId: string | null, reversedByEntryId: string | null, totalDebits: string, totalCredits: string, isBalanced: boolean, lines: Array<{ __typename: 'JournalLine', accountCode: string, accountName: string, debit: string | null, credit: string | null, description: string | null }> } };

export type LedgerPostJournalEntryMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type LedgerPostJournalEntryMutation = { __typename: 'Mutation', postJournalEntry: { __typename: 'JournalEntry', id: string, entryNumber: string, correlationId: string | null, entryDate: string, description: string, type: JournalEntryType, status: JournalEntryStatus, createdBy: string | null, postedBy: string | null, postedAt: string | null, reversedBy: string | null, reversedAt: string | null, reversalEntryId: string | null, reversedByEntryId: string | null, totalDebits: string, totalCredits: string, isBalanced: boolean, lines: Array<{ __typename: 'JournalLine', accountCode: string, accountName: string, debit: string | null, credit: string | null, description: string | null }> } };

export type LedgerReverseJournalEntryMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type LedgerReverseJournalEntryMutation = { __typename: 'Mutation', reverseJournalEntry: { __typename: 'JournalEntry', id: string, entryNumber: string, correlationId: string | null, entryDate: string, description: string, type: JournalEntryType, status: JournalEntryStatus, createdBy: string | null, postedBy: string | null, postedAt: string | null, reversedBy: string | null, reversedAt: string | null, reversalEntryId: string | null, reversedByEntryId: string | null, totalDebits: string, totalCredits: string, isBalanced: boolean, lines: Array<{ __typename: 'JournalLine', accountCode: string, accountName: string, debit: string | null, credit: string | null, description: string | null }> } };

export type LedgerTrialBalanceQueryVariables = Exact<{
  asOf: InputMaybe<Scalars['DateTime']['input']>;
}>;


export type LedgerTrialBalanceQuery = { __typename: 'Query', trialBalance: Array<{ __typename: 'AccountBalance', accountCode: string, accountName: string, accountType: string, debitBalance: string, creditBalance: string, netBalance: string }> };

export type LedgerPlatformAccountsQueryVariables = Exact<{ [key: string]: never; }>;


export type LedgerPlatformAccountsQuery = { __typename: 'Query', platformAccounts: Array<{ __typename: 'PlatformAccount', id: string, accountType: PlatformAccountType, name: string, balance: string, currency: string, lastUpdatedAt: string | null }> };

export type LedgerReconciliationRunsQueryVariables = Exact<{
  filter: InputMaybe<ReconciliationFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type LedgerReconciliationRunsQuery = { __typename: 'Query', reconciliationRuns: { __typename: 'ReconciliationRunOffsetPage', data: Array<{ __typename: 'ReconciliationRun', id: string, reconciliationDate: string, type: ReconciliationType, status: ReconciliationStatus, dataSource: string | null, expectedTotal: string | null, actualTotal: string | null, variance: string | null, matchedCount: number, unmatchedCount: number, runBy: string | null, startedAt: string, completedAt: string | null, notes: string | null, items: Array<{ __typename: 'ReconciliationItem', externalId: string | null, internalId: string | null, externalAmount: string | null, internalAmount: string | null, status: ReconciliationItemStatus, resolution: string | null, resolvedBy: string | null, resolvedAt: string | null }> }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type LedgerStartReconciliationMutationVariables = Exact<{
  input: StartReconciliationInput;
}>;


export type LedgerStartReconciliationMutation = { __typename: 'Mutation', startReconciliation: { __typename: 'ReconciliationRun', id: string, status: ReconciliationStatus, notes: string | null, unmatchedCount: number } };

export type LedgerResolveReconciliationItemMutationVariables = Exact<{
  runId: Scalars['ID']['input'];
  input: ResolveReconciliationItemInput;
}>;


export type LedgerResolveReconciliationItemMutation = { __typename: 'Mutation', resolveReconciliationItem: { __typename: 'ReconciliationRun', id: string, status: ReconciliationStatus, notes: string | null, unmatchedCount: number } };

export type LedgerCompleteReconciliationMutationVariables = Exact<{
  runId: Scalars['ID']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
}>;


export type LedgerCompleteReconciliationMutation = { __typename: 'Mutation', completeReconciliation: { __typename: 'ReconciliationRun', id: string, status: ReconciliationStatus, notes: string | null, unmatchedCount: number } };

export type LedgerFailReconciliationMutationVariables = Exact<{
  runId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type LedgerFailReconciliationMutation = { __typename: 'Mutation', failReconciliation: { __typename: 'ReconciliationRun', id: string, status: ReconciliationStatus, notes: string | null, unmatchedCount: number } };

export type LedgerRecordGatewaySettlementMutationVariables = Exact<{
  input: RecordGatewaySettlementInput;
}>;


export type LedgerRecordGatewaySettlementMutation = { __typename: 'Mutation', recordGatewaySettlement: { __typename: 'JournalEntry', id: string, entryNumber: string, status: JournalEntryStatus } };

export type OpsMediaAssetFieldsFragment = { __typename: 'MediaAsset', id: string, eventId: string | null, organizationId: string | null, fileName: string, title: string | null, altText: string | null, contentType: string, sizeBytes: number, url: string, status: MediaStatus, flaggedAt: string | null, flaggedReason: string | null, removedAt: string | null, removedReason: string | null, uploadedBy: string | null, createdAt: string | null, updatedAt: string | null, moderationLog: Array<{ __typename: 'MediaModerationEntry', action: string, reason: string | null, actorId: string | null, at: string | null }> | null };

export type OpsMediaAssetsQueryVariables = Exact<{
  filter: InputMaybe<MediaModerationFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsMediaAssetsQuery = { __typename: 'Query', mediaAssets: { __typename: 'MediaAssetOffsetPage', totalElements: number, totalPages: number, pageNumber: number, pageSize: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'MediaAsset', id: string, eventId: string | null, organizationId: string | null, fileName: string, title: string | null, altText: string | null, contentType: string, sizeBytes: number, url: string, status: MediaStatus, flaggedAt: string | null, flaggedReason: string | null, removedAt: string | null, removedReason: string | null, uploadedBy: string | null, createdAt: string | null, updatedAt: string | null, moderationLog: Array<{ __typename: 'MediaModerationEntry', action: string, reason: string | null, actorId: string | null, at: string | null }> | null }> } };

export type OpsFlagMediaMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsFlagMediaMutation = { __typename: 'Mutation', flagMedia: { __typename: 'MediaAsset', id: string, eventId: string | null, organizationId: string | null, fileName: string, title: string | null, altText: string | null, contentType: string, sizeBytes: number, url: string, status: MediaStatus, flaggedAt: string | null, flaggedReason: string | null, removedAt: string | null, removedReason: string | null, uploadedBy: string | null, createdAt: string | null, updatedAt: string | null, moderationLog: Array<{ __typename: 'MediaModerationEntry', action: string, reason: string | null, actorId: string | null, at: string | null }> | null } };

export type OpsRemoveMediaMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsRemoveMediaMutation = { __typename: 'Mutation', removeMedia: { __typename: 'MediaAsset', id: string, eventId: string | null, organizationId: string | null, fileName: string, title: string | null, altText: string | null, contentType: string, sizeBytes: number, url: string, status: MediaStatus, flaggedAt: string | null, flaggedReason: string | null, removedAt: string | null, removedReason: string | null, uploadedBy: string | null, createdAt: string | null, updatedAt: string | null, moderationLog: Array<{ __typename: 'MediaModerationEntry', action: string, reason: string | null, actorId: string | null, at: string | null }> | null } };

export type OpsRestoreMediaMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OpsRestoreMediaMutation = { __typename: 'Mutation', restoreMedia: { __typename: 'MediaAsset', id: string, eventId: string | null, organizationId: string | null, fileName: string, title: string | null, altText: string | null, contentType: string, sizeBytes: number, url: string, status: MediaStatus, flaggedAt: string | null, flaggedReason: string | null, removedAt: string | null, removedReason: string | null, uploadedBy: string | null, createdAt: string | null, updatedAt: string | null, moderationLog: Array<{ __typename: 'MediaModerationEntry', action: string, reason: string | null, actorId: string | null, at: string | null }> | null } };

export type OpsStockImageFieldsFragment = { __typename: 'StockImage', id: string, title: string | null, altText: string | null, url: string, purpose: StockImagePurpose, categoryCode: string | null, active: boolean, createdAt: string | null, updatedAt: string | null };

export type OpsStockImagesQueryVariables = Exact<{
  filter: InputMaybe<StockImageFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type OpsStockImagesQuery = { __typename: 'Query', stockImages: { __typename: 'StockImageConnection', edges: Array<{ __typename: 'StockImageEdge', node: { __typename: 'StockImage', id: string, title: string | null, altText: string | null, url: string, purpose: StockImagePurpose, categoryCode: string | null, active: boolean, createdAt: string | null, updatedAt: string | null } }> } };

export type OpsUploadStockImageMutationVariables = Exact<{
  input: UploadStockImageInput;
}>;


export type OpsUploadStockImageMutation = { __typename: 'Mutation', uploadStockImage: { __typename: 'StockImage', id: string, title: string | null, altText: string | null, url: string, purpose: StockImagePurpose, categoryCode: string | null, active: boolean, createdAt: string | null, updatedAt: string | null } };

export type OpsUpdateStockImageMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateStockImageInput;
}>;


export type OpsUpdateStockImageMutation = { __typename: 'Mutation', updateStockImage: { __typename: 'StockImage', id: string, title: string | null, altText: string | null, url: string, purpose: StockImagePurpose, categoryCode: string | null, active: boolean, createdAt: string | null, updatedAt: string | null } };

export type OpsDeleteStockImageMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OpsDeleteStockImageMutation = { __typename: 'Mutation', deleteStockImage: string };

export type OpsOverrideEventBannerMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  mediaId: InputMaybe<Scalars['ID']['input']>;
  reason: Scalars['String']['input'];
}>;


export type OpsOverrideEventBannerMutation = { __typename: 'Mutation', overrideEventBanner: { __typename: 'Event', id: string, bannerImageUrl: string | null } };

export type ApproveOrganizationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  commissionRate: InputMaybe<Scalars['Float']['input']>;
}>;


export type ApproveOrganizationMutation = { __typename: 'Mutation', approveOrganization: { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null } | null };

export type RejectOrganizationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type RejectOrganizationMutation = { __typename: 'Mutation', rejectOrganization: { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null } | null };

export type RequestOrganizationChangesMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type RequestOrganizationChangesMutation = { __typename: 'Mutation', requestOrganizationChanges: { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null } | null };

export type SuspendOrganizationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type SuspendOrganizationMutation = { __typename: 'Mutation', suspendOrganization: { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null } | null };

export type UnsuspendOrganizationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type UnsuspendOrganizationMutation = { __typename: 'Mutation', unsuspendOrganization: { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null } | null };

export type AdminOrganizationFieldsFragment = { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null };

export type OrganizationListFieldsFragment = { __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, logoUrl: string | null, businessEmail: string | null, businessPhone: string | null, verified: boolean, documentsVerified: boolean, submittedAt: string | null, approvedAt: string | null, createdAt: string, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null } | null };

export type PendingOrganizationsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type PendingOrganizationsQuery = { __typename: 'Query', organizations: { __typename: 'OrganizationOffsetPage', content: Array<{ __typename: 'Organization', id: string, name: string, slug: string, type: OrganizationType, status: OrganizationStatus, logoUrl: string | null, businessEmail: string | null, businessPhone: string | null, verified: boolean, documentsVerified: boolean, submittedAt: string | null, approvedAt: string | null, createdAt: string, businessAddress: { __typename: 'BusinessAddress', city: string | null, province: string | null } | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null, hasNext: boolean | null, hasPrevious: boolean | null } } };

export type GetOrganizationQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetOrganizationQuery = { __typename: 'Query', organization: { __typename: 'Organization', id: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, city: string | null, province: string | null, country: string | null, postalCode: string | null } | null } | null };

export type OpsPaginationFieldsFragment = { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null };

export type OpsAttemptFieldsFragment = { __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null };

export type OpsPaymentAttemptSearchQueryVariables = Exact<{
  filter: InputMaybe<PaymentAttemptFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsPaymentAttemptSearchQuery = { __typename: 'Query', paymentAttemptSearch: { __typename: 'PaymentAttemptOffsetPage', data: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type OpsStuckTransactionsQueryVariables = Exact<{
  minutes: InputMaybe<Scalars['Int']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsStuckTransactionsQuery = { __typename: 'Query', stuckTransactions: { __typename: 'PaymentAttemptOffsetPage', data: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type OpsResumePaymentAttemptMutationVariables = Exact<{
  depositId: Scalars['String']['input'];
}>;


export type OpsResumePaymentAttemptMutation = { __typename: 'Mutation', resumePaymentAttempt: { __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null } };

export type OpsRetryPaymentAttemptsMutationVariables = Exact<{
  depositIds: Array<Scalars['String']['input']> | Scalars['String']['input'];
}>;


export type OpsRetryPaymentAttemptsMutation = { __typename: 'Mutation', retryPaymentAttempts: Array<{ __typename: 'PaymentRecoveryOutcome', depositId: string, result: string, detail: string | null }> };

export type OpsRecoveryProposalFieldsFragment = { __typename: 'RecoveryProposal', id: string, action: RecoveryAction, amount: string | null, canConfirm: boolean, confirmationReason: string | null, confirmedAt: string | null, confirmedById: string | null, expiresAt: string, failureReason: string | null, outcome: string | null, proposalReason: string, proposedAt: string, proposedById: string, status: RecoveryProposalStatus, subjectIds: Array<string>, subjectType: string };

export type OpsForceCompletePaymentAttemptsMutationVariables = Exact<{
  depositIds: Array<Scalars['String']['input']> | Scalars['String']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsForceCompletePaymentAttemptsMutation = { __typename: 'Mutation', forceCompletePaymentAttempts: { __typename: 'RecoveryProposal', id: string, action: RecoveryAction, amount: string | null, canConfirm: boolean, confirmationReason: string | null, confirmedAt: string | null, confirmedById: string | null, expiresAt: string, failureReason: string | null, outcome: string | null, proposalReason: string, proposedAt: string, proposedById: string, status: RecoveryProposalStatus, subjectIds: Array<string>, subjectType: string } };

export type OpsDualControlQueueQueryVariables = Exact<{ [key: string]: never; }>;


export type OpsDualControlQueueQuery = { __typename: 'Query', dualControlQueue: Array<{ __typename: 'RecoveryProposal', id: string, action: RecoveryAction, amount: string | null, canConfirm: boolean, confirmationReason: string | null, confirmedAt: string | null, confirmedById: string | null, expiresAt: string, failureReason: string | null, outcome: string | null, proposalReason: string, proposedAt: string, proposedById: string, status: RecoveryProposalStatus, subjectIds: Array<string>, subjectType: string }> };

export type OpsConfirmRecoveryActionMutationVariables = Exact<{
  proposalId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsConfirmRecoveryActionMutation = { __typename: 'Mutation', confirmRecoveryAction: { __typename: 'RecoveryProposal', id: string, action: RecoveryAction, amount: string | null, canConfirm: boolean, confirmationReason: string | null, confirmedAt: string | null, confirmedById: string | null, expiresAt: string, failureReason: string | null, outcome: string | null, proposalReason: string, proposedAt: string, proposedById: string, status: RecoveryProposalStatus, subjectIds: Array<string>, subjectType: string } };

export type OpsWithdrawRecoveryProposalMutationVariables = Exact<{
  proposalId: Scalars['ID']['input'];
}>;


export type OpsWithdrawRecoveryProposalMutation = { __typename: 'Mutation', withdrawRecoveryProposal: { __typename: 'RecoveryProposal', id: string, action: RecoveryAction, amount: string | null, canConfirm: boolean, confirmationReason: string | null, confirmedAt: string | null, confirmedById: string | null, expiresAt: string, failureReason: string | null, outcome: string | null, proposalReason: string, proposedAt: string, proposedById: string, status: RecoveryProposalStatus, subjectIds: Array<string>, subjectType: string } };

export type OpsPaymentRiskSummaryQueryVariables = Exact<{
  windowHours: InputMaybe<Scalars['Int']['input']>;
}>;


export type OpsPaymentRiskSummaryQuery = { __typename: 'Query', paymentRiskSummary: { __typename: 'PaymentRiskSummary', windowHours: number, evaluated: number, flagged: number, high: number, medium: number, low: number, amountAtRisk: string, topFlags: Array<{ __typename: 'RiskFlagCount', flag: string, count: number }> } };

export type OpsCommissionRecordsQueryVariables = Exact<{
  filter: InputMaybe<CommissionFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsCommissionRecordsQuery = { __typename: 'Query', commissionRecords: { __typename: 'CommissionRecordPage', data: Array<{ __typename: 'CommissionRecord', id: string, eventId: string, organizationId: string | null, ticketId: string, ticketPrice: string, rate: string, amount: string, currency: string, status: CommissionStatus, earnedAt: string | null, pendingAt: string | null, cancelledAt: string | null, clawedBackAt: string | null, refundReason: string | null, createdAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null }, totals: { __typename: 'CommissionTotals', earned: string, pending: string, cancelled: string, clawedBack: string } } };

export type OpsGatewaySettlementsQueryVariables = Exact<{
  filter: InputMaybe<GatewaySettlementFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsGatewaySettlementsQuery = { __typename: 'Query', gatewaySettlements: { __typename: 'GatewaySettlementPage', data: Array<{ __typename: 'GatewaySettlement', settlementId: string, settlementDate: string | null, grossAmount: string, feeAmount: string, netAmount: string, currency: string | null, bankReference: string | null, entryNumber: string | null, journalEntryId: string, postedAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type OpsTransferBetweenPlatformAccountsMutationVariables = Exact<{
  input: PlatformTransferInput;
}>;


export type OpsTransferBetweenPlatformAccountsMutation = { __typename: 'Mutation', transferBetweenPlatformAccounts: { __typename: 'PlatformTransferResult', executed: boolean, requiresSecondApprover: boolean, proposal: { __typename: 'RecoveryProposal', id: string, action: RecoveryAction, amount: string | null, canConfirm: boolean, confirmationReason: string | null, confirmedAt: string | null, confirmedById: string | null, expiresAt: string, failureReason: string | null, outcome: string | null, proposalReason: string, proposedAt: string, proposedById: string, status: RecoveryProposalStatus, subjectIds: Array<string>, subjectType: string } | null, transfer: { __typename: 'PlatformTransfer', id: string, fromAccount: PlatformAccountType, toAccount: PlatformAccountType, amount: string, currency: string, reason: string, executedBy: string, createdAt: string | null } | null } };

export type OpsHoldPayoutRequestMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsHoldPayoutRequestMutation = { __typename: 'Mutation', holdPayoutRequest: { __typename: 'PayoutRequest', id: string, status: PayoutRequestStatus } };

export type OpsReleasePayoutHoldMutationVariables = Exact<{
  payoutRequestId: Scalars['ID']['input'];
  note: InputMaybe<Scalars['String']['input']>;
}>;


export type OpsReleasePayoutHoldMutation = { __typename: 'Mutation', releasePayoutHold: { __typename: 'PayoutRequest', id: string, status: PayoutRequestStatus } };

export type OpsPurchasesByDayAndHourQueryVariables = Exact<{
  from: InputMaybe<Scalars['DateTime']['input']>;
  to: InputMaybe<Scalars['DateTime']['input']>;
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
}>;


export type OpsPurchasesByDayAndHourQuery = { __typename: 'Query', purchasesByDayAndHour: Array<{ __typename: 'PurchaseHeatCell', dayOfWeek: number, hour: number, purchases: number, tickets: number, revenue: string }> };

export type OpsBookingsByBuyerQueryVariables = Exact<{
  buyerId: Scalars['String']['input'];
  filter: InputMaybe<BookingFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsBookingsByBuyerQuery = { __typename: 'Query', bookingsByBuyer: { __typename: 'BookingOffsetPage', data: Array<{ __typename: 'Booking', id: string, bookingNumber: string, eventId: string, eventTitle: string | null, eventDate: string | null, status: BookingStatus, ticketCount: number, totalAmount: string, currency: string, refundedAmount: string, createdAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type PlatformConfigFieldsFragment = { __typename: 'PlatformConfiguration', id: string, approvalSlaHours: number, approvalWarningThresholdHours: number, autoEscalationEnabled: boolean, escalationDelayHours: number, escalationRecipientRole: string, escalationReminderIntervalHours: number, maxEscalationReminders: number, organizerNotificationChannel: ApprovalNotificationChannel, adminNotificationChannel: ApprovalNotificationChannel, sendSlaWarningNotifications: boolean, sendEscalationNotifications: boolean, requireCommentsOnRejection: boolean, requireCommentsOnChangesRequested: boolean, allowSelfApproval: boolean, commissionDefault: number | null, minimumPayout: string | null, currency: string, reservationHoldMinutes: number, reservationGraceMinutes: number, escrowHoldDays: number, refundCutoffHours: number, maxTicketsPerBooking: number, rescheduleLimit: number, version: number, updatedAt: string, updatedBy: string, refundPolicies: Array<{ __typename: 'PlatformRefundPolicy', code: string, label: string, summary: string, rules: Array<{ __typename: 'PlatformRefundRule', daysBefore: number, percent: number }> }> };

export type PlatformConfigurationQueryVariables = Exact<{ [key: string]: never; }>;


export type PlatformConfigurationQuery = { __typename: 'Query', platformConfiguration: { __typename: 'PlatformConfiguration', id: string, approvalSlaHours: number, approvalWarningThresholdHours: number, autoEscalationEnabled: boolean, escalationDelayHours: number, escalationRecipientRole: string, escalationReminderIntervalHours: number, maxEscalationReminders: number, organizerNotificationChannel: ApprovalNotificationChannel, adminNotificationChannel: ApprovalNotificationChannel, sendSlaWarningNotifications: boolean, sendEscalationNotifications: boolean, requireCommentsOnRejection: boolean, requireCommentsOnChangesRequested: boolean, allowSelfApproval: boolean, commissionDefault: number | null, minimumPayout: string | null, currency: string, reservationHoldMinutes: number, reservationGraceMinutes: number, escrowHoldDays: number, refundCutoffHours: number, maxTicketsPerBooking: number, rescheduleLimit: number, version: number, updatedAt: string, updatedBy: string, refundPolicies: Array<{ __typename: 'PlatformRefundPolicy', code: string, label: string, summary: string, rules: Array<{ __typename: 'PlatformRefundRule', daysBefore: number, percent: number }> }> } };

export type UpdatePlatformConfigurationMutationVariables = Exact<{
  input: UpdatePlatformConfigurationInput;
}>;


export type UpdatePlatformConfigurationMutation = { __typename: 'Mutation', updatePlatformConfiguration: { __typename: 'PlatformConfiguration', id: string, approvalSlaHours: number, approvalWarningThresholdHours: number, autoEscalationEnabled: boolean, escalationDelayHours: number, escalationRecipientRole: string, escalationReminderIntervalHours: number, maxEscalationReminders: number, organizerNotificationChannel: ApprovalNotificationChannel, adminNotificationChannel: ApprovalNotificationChannel, sendSlaWarningNotifications: boolean, sendEscalationNotifications: boolean, requireCommentsOnRejection: boolean, requireCommentsOnChangesRequested: boolean, allowSelfApproval: boolean, commissionDefault: number | null, minimumPayout: string | null, currency: string, reservationHoldMinutes: number, reservationGraceMinutes: number, escrowHoldDays: number, refundCutoffHours: number, maxTicketsPerBooking: number, rescheduleLimit: number, version: number, updatedAt: string, updatedBy: string, refundPolicies: Array<{ __typename: 'PlatformRefundPolicy', code: string, label: string, summary: string, rules: Array<{ __typename: 'PlatformRefundRule', daysBefore: number, percent: number }> }> } };

export type OpsPageInfoFieldsFragment = { __typename: 'PageInfo', totalCount: number | null, pageSize: number | null, currentPage: number | null, totalPages: number | null, hasNextPage: boolean | null, hasPreviousPage: boolean | null };

export type OpsServiceHealthQueryVariables = Exact<{ [key: string]: never; }>;


export type OpsServiceHealthQuery = { __typename: 'Query', serviceHealth: Array<{ __typename: 'ServiceHealth', name: string, status: ServiceStatus, latencyMillis: number, checkedAt: string, detail: string | null }> };

export type OpsSystemAlertFieldsFragment = { __typename: 'SystemAlert', id: string, source: string, key: string, severity: AlertSeverity, title: string, message: string | null, status: AlertStatus, occurrences: number, raisedAt: string, lastSeenAt: string | null, acknowledgedAt: string | null, acknowledgedBy: string | null, resolvedAt: string | null };

export type OpsSystemAlertsQueryVariables = Exact<{
  status: InputMaybe<AlertStatus>;
  severity: InputMaybe<AlertSeverity>;
}>;


export type OpsSystemAlertsQuery = { __typename: 'Query', systemAlerts: Array<{ __typename: 'SystemAlert', id: string, source: string, key: string, severity: AlertSeverity, title: string, message: string | null, status: AlertStatus, occurrences: number, raisedAt: string, lastSeenAt: string | null, acknowledgedAt: string | null, acknowledgedBy: string | null, resolvedAt: string | null }> };

export type OpsAcknowledgeAlertMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OpsAcknowledgeAlertMutation = { __typename: 'Mutation', acknowledgeAlert: { __typename: 'SystemAlert', id: string, source: string, key: string, severity: AlertSeverity, title: string, message: string | null, status: AlertStatus, occurrences: number, raisedAt: string, lastSeenAt: string | null, acknowledgedAt: string | null, acknowledgedBy: string | null, resolvedAt: string | null } };

export type OpsAnnouncementFieldsFragment = { __typename: 'SystemAnnouncement', id: string, title: string, message: string, segment: AnnouncementSegment, severity: AlertSeverity, startsAt: string, endsAt: string | null, cancelledAt: string | null, createdAt: string };

export type OpsSystemAnnouncementsQueryVariables = Exact<{ [key: string]: never; }>;


export type OpsSystemAnnouncementsQuery = { __typename: 'Query', systemAnnouncements: Array<{ __typename: 'SystemAnnouncement', id: string, title: string, message: string, segment: AnnouncementSegment, severity: AlertSeverity, startsAt: string, endsAt: string | null, cancelledAt: string | null, createdAt: string }> };

export type OpsBroadcastNotificationMutationVariables = Exact<{
  input: BroadcastInput;
}>;


export type OpsBroadcastNotificationMutation = { __typename: 'Mutation', broadcastNotification: { __typename: 'SystemAnnouncement', id: string, title: string, message: string, segment: AnnouncementSegment, severity: AlertSeverity, startsAt: string, endsAt: string | null, cancelledAt: string | null, createdAt: string } };

export type OpsCancelAnnouncementMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OpsCancelAnnouncementMutation = { __typename: 'Mutation', cancelAnnouncement: { __typename: 'SystemAnnouncement', id: string, title: string, message: string, segment: AnnouncementSegment, severity: AlertSeverity, startsAt: string, endsAt: string | null, cancelledAt: string | null, createdAt: string } };

export type OpsAuditLogsQueryVariables = Exact<{
  filter: InputMaybe<AuditLogFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsAuditLogsQuery = { __typename: 'Query', auditLogs: { __typename: 'AuditLogEntryOffsetPage', content: Array<{ __typename: 'AuditLogEntry', id: string, action: string, actorId: string | null, at: string | null, metadata: Record<string, unknown> | null, resourceId: string | null, resourceType: string | null, source: string, status: string | null, subjectId: string | null }>, pageInfo: { __typename: 'PageInfo', totalCount: number | null, pageSize: number | null, currentPage: number | null, totalPages: number | null, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type OpsStaffAccountsQueryVariables = Exact<{
  search: InputMaybe<Scalars['String']['input']>;
  role: InputMaybe<UserType>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsStaffAccountsQuery = { __typename: 'Query', staffAccounts: { __typename: 'UserOffsetPage', content: Array<{ __typename: 'User', id: string, email: string | null, fullName: string, phoneNumber: string | null, roles: Array<UserType>, accountStatus: AccountStatus, locked: boolean, twoFactorEnabled: boolean, lastLoginAt: string | null, createdAt: string }>, pageInfo: { __typename: 'PageInfo', totalCount: number | null, pageSize: number | null, currentPage: number | null, totalPages: number | null, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type OpsCreateStaffMutationVariables = Exact<{
  input: CreateUserInput;
}>;


export type OpsCreateStaffMutation = { __typename: 'Mutation', createUser: { __typename: 'User', id: string, email: string | null, fullName: string, roles: Array<UserType> } };

export type OpsDeleteUserMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type OpsDeleteUserMutation = { __typename: 'Mutation', deleteUser: { __typename: 'User', id: string, accountStatus: AccountStatus } };

export type OpsMySessionsQueryVariables = Exact<{ [key: string]: never; }>;


export type OpsMySessionsQuery = { __typename: 'Query', mySessions: Array<{ __typename: 'AccountSession', id: string, current: boolean, clients: Array<string>, ipAddress: string | null, startedAt: string | null, lastAccessAt: string | null }> };

export type OpsRevokeSessionMutationVariables = Exact<{
  sessionId: Scalars['ID']['input'];
}>;


export type OpsRevokeSessionMutation = { __typename: 'Mutation', revokeSession: boolean };

export type OpsUpdateMyProfileMutationVariables = Exact<{
  input: UpdateUserInput;
}>;


export type OpsUpdateMyProfileMutation = { __typename: 'Mutation', updateMyProfile: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string, displayName: string | null } };

export type OpsMeSecurityQueryVariables = Exact<{ [key: string]: never; }>;


export type OpsMeSecurityQuery = { __typename: 'Query', me: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, displayName: string | null, fullName: string, email: string | null, phoneNumber: string | null, twoFactorEnabled: boolean, lastLoginAt: string | null } | null };

export type OpsUserGrowthSeriesQueryVariables = Exact<{
  from: Scalars['DateTime']['input'];
  to: Scalars['DateTime']['input'];
  bucket: InputMaybe<GrowthBucket>;
  role: InputMaybe<UserType>;
}>;


export type OpsUserGrowthSeriesQuery = { __typename: 'Query', userGrowthSeries: Array<{ __typename: 'GrowthPoint', bucketStart: string, newUsers: number, cumulative: number }> };

export type OpsPayoutAccountsQueryVariables = Exact<{
  filter: InputMaybe<PayoutAccountFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type OpsPayoutAccountsQuery = { __typename: 'Query', bankAccounts: { __typename: 'PayoutAccountRecordOffsetPage', content: Array<{ __typename: 'PayoutAccountRecord', organizationId: string, organizationName: string, organizationSlug: string, method: PayoutMethod, status: PayoutAccountStatus, bankName: string | null, accountHolderName: string | null, accountNumberMasked: string | null, network: MobileMoneyProvider | null, phoneMasked: string | null, rejectionReason: string | null, suspendedReason: string | null, testDepositSentAt: string | null, verificationAttemptsLeft: number, updatedAt: string | null }>, pageInfo: { __typename: 'PageInfo', totalCount: number | null, pageSize: number | null, currentPage: number | null, totalPages: number | null, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type OpsRejectBankAccountMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsRejectBankAccountMutation = { __typename: 'Mutation', rejectBankAccount: { __typename: 'Organization', id: string } | null };

export type OpsSuspendBankAccountMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsSuspendBankAccountMutation = { __typename: 'Mutation', suspendBankAccount: { __typename: 'Organization', id: string } | null };

export type OpsReinstateBankAccountMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
}>;


export type OpsReinstateBankAccountMutation = { __typename: 'Mutation', reinstateBankAccount: { __typename: 'Organization', id: string } | null };

export type OpsRejectPayoutAccountMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type OpsRejectPayoutAccountMutation = { __typename: 'Mutation', rejectPayoutAccount: { __typename: 'Organization', id: string } | null };

export type OpsSetOrganizationCommissionRateMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  rate: Scalars['Float']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type OpsSetOrganizationCommissionRateMutation = { __typename: 'Mutation', setOrganizationCommissionRate: { __typename: 'Organization', id: string, commissionRate: number | null } | null };

export type CreateReferenceDataMutationVariables = Exact<{
  input: CreateReferenceDataInput;
}>;


export type CreateReferenceDataMutation = { __typename: 'Mutation', createReferenceData: { __typename: 'ReferenceData', id: string, type: ReferenceType, code: string, name: string, description: string | null, semantic: WorkflowSemantic | null, allowedTransitions: Array<string>, parentType: ReferenceType | null, parentCode: string | null, displayOrder: number, isActive: boolean, isSystem: boolean, effectiveFrom: string | null, effectiveTo: string | null, metadata: Record<string, unknown> | null, createdAt: string | null, updatedAt: string | null, createdBy: string | null, updatedBy: string | null } };

export type UpdateReferenceDataMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateReferenceDataInput;
}>;


export type UpdateReferenceDataMutation = { __typename: 'Mutation', updateReferenceData: { __typename: 'ReferenceData', id: string, type: ReferenceType, code: string, name: string, description: string | null, semantic: WorkflowSemantic | null, allowedTransitions: Array<string>, parentType: ReferenceType | null, parentCode: string | null, displayOrder: number, isActive: boolean, isSystem: boolean, effectiveFrom: string | null, effectiveTo: string | null, metadata: Record<string, unknown> | null, createdAt: string | null, updatedAt: string | null, createdBy: string | null, updatedBy: string | null } };

export type DeleteReferenceDataMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteReferenceDataMutation = { __typename: 'Mutation', deleteReferenceData: string };

export type SetReferenceDataActiveMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  active: Scalars['Boolean']['input'];
}>;


export type SetReferenceDataActiveMutation = { __typename: 'Mutation', setReferenceDataActive: { __typename: 'ReferenceData', id: string, type: ReferenceType, code: string, name: string, description: string | null, semantic: WorkflowSemantic | null, allowedTransitions: Array<string>, parentType: ReferenceType | null, parentCode: string | null, displayOrder: number, isActive: boolean, isSystem: boolean, effectiveFrom: string | null, effectiveTo: string | null, metadata: Record<string, unknown> | null, createdAt: string | null, updatedAt: string | null, createdBy: string | null, updatedBy: string | null } };

export type ReferenceDataFieldsFragment = { __typename: 'ReferenceData', id: string, type: ReferenceType, code: string, name: string, description: string | null, semantic: WorkflowSemantic | null, allowedTransitions: Array<string>, parentType: ReferenceType | null, parentCode: string | null, displayOrder: number, isActive: boolean, isSystem: boolean, effectiveFrom: string | null, effectiveTo: string | null, metadata: Record<string, unknown> | null, createdAt: string | null, updatedAt: string | null, createdBy: string | null, updatedBy: string | null };

export type ReferenceDataQueryVariables = Exact<{
  type: ReferenceType;
  activeOnly: InputMaybe<Scalars['Boolean']['input']>;
}>;


export type ReferenceDataQuery = { __typename: 'Query', referenceData: Array<{ __typename: 'ReferenceData', id: string, type: ReferenceType, code: string, name: string, description: string | null, semantic: WorkflowSemantic | null, allowedTransitions: Array<string>, parentType: ReferenceType | null, parentCode: string | null, displayOrder: number, isActive: boolean, isSystem: boolean, effectiveFrom: string | null, effectiveTo: string | null, metadata: Record<string, unknown> | null, createdAt: string | null, updatedAt: string | null, createdBy: string | null, updatedBy: string | null }> };

export type ReferenceTypesQueryVariables = Exact<{ [key: string]: never; }>;


export type ReferenceTypesQuery = { __typename: 'Query', referenceTypes: Array<{ __typename: 'ReferenceTypeInfo', type: ReferenceType, label: string, group: string, groupLabel: string, requiredMetadataKeys: Array<string> }> };

export type ReferenceDataAllQueryVariables = Exact<{
  type: ReferenceType;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type ReferenceDataAllQuery = { __typename: 'Query', referenceDataAll: { __typename: 'ReferenceDataOffsetPage', pageNumber: number, pageSize: number, totalElements: number, totalPages: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'ReferenceData', id: string, type: ReferenceType, code: string, name: string, description: string | null, semantic: WorkflowSemantic | null, allowedTransitions: Array<string>, parentType: ReferenceType | null, parentCode: string | null, displayOrder: number, isActive: boolean, isSystem: boolean, effectiveFrom: string | null, effectiveTo: string | null, metadata: Record<string, unknown> | null, createdAt: string | null, updatedAt: string | null, createdBy: string | null, updatedBy: string | null }> } };

export type AdminUserStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminUserStatsQuery = { __typename: 'Query', userStats: { __typename: 'UserStats', totalUsers: number, organizers: number, attendees: number, adminUsers: number, verifiedUsers: number, activeUsers: number, suspendedUsers: number, lockedUsers: number, pendingVerificationUsers: number, newUsersThisMonth: number, newUsersThisWeek: number, growthRate: number | null } | null };

export type AdminFinancialReportQueryVariables = Exact<{
  filter: FinancialReportFilterInput;
}>;


export type AdminFinancialReportQuery = { __typename: 'Query', financialReport: { __typename: 'FinancialReport', startDate: string, endDate: string, totalRevenue: string, totalCommissions: string, totalRefunds: string, totalPayouts: string, pendingPayouts: string, escrowBalance: string, netPlatformRevenue: string, dataPoints: Array<{ __typename: 'FinancialDataPoint', period: string, revenue: string, commissions: string, refunds: string, payouts: string, ticketsSold: number }> } };

export type AdminChargebackStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminChargebackStatsQuery = { __typename: 'Query', chargebackStats: { __typename: 'ChargebackStats', totalCount: number, pendingCount: number, disputedCount: number, wonCount: number, lostCount: number, totalAmount: string, recoveredAmount: string, writtenOffAmount: string, chargebackRate: number, winRate: number } };

export type AdminTransactionStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminTransactionStatsQuery = { __typename: 'Query', transactionStats: { __typename: 'TransactionStats', totalTransactions: number, completedTransactions: number, failedTransactions: number, pendingTransactions: number, timedOutTransactions: number, totalVolume: string, totalCommissions: string, averageTransactionValue: string | null } };

export type AdminTicketStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type AdminTicketStatsQuery = { __typename: 'Query', ticketStats: { __typename: 'TicketStats', totalTickets: number, issuedTickets: number, validatedTickets: number, refundPendingTickets: number, refundedTickets: number, cancelledTickets: number, expiredTickets: number, ticketsByStatus: Array<{ __typename: 'TicketStatusStats', status: TicketStatus, count: number, percentage: number }> | null } };

export type AdminExportFinancialReportQueryVariables = Exact<{
  filter: FinancialReportFilterInput;
  format: ExportFormat;
}>;


export type AdminExportFinancialReportQuery = { __typename: 'Query', exportFinancialReport: { __typename: 'ReportExport', downloadUrl: string | null, expiresAt: string | null, format: ExportFormat, generatedAt: string, fileName: string | null, errorMessage: string | null } };

export type TxPaymentAttemptFieldsFragment = { __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null };

export type TxPaymentAttemptsQueryVariables = Exact<{ [key: string]: never; }>;


export type TxPaymentAttemptsQuery = { __typename: 'Query', s0: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s1: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s2: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s3: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s4: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s5: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s6: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s7: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }>, s8: Array<{ __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null }> };

export type TxAddPaymentAttemptNoteMutationVariables = Exact<{
  depositId: Scalars['String']['input'];
  note: Scalars['String']['input'];
}>;


export type TxAddPaymentAttemptNoteMutation = { __typename: 'Mutation', addPaymentAttemptNote: { __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null } };

export type TxSetPaymentAttemptReviewStatusMutationVariables = Exact<{
  depositId: Scalars['String']['input'];
  reviewStatus: Scalars['String']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
}>;


export type TxSetPaymentAttemptReviewStatusMutation = { __typename: 'Mutation', setPaymentAttemptReviewStatus: { __typename: 'PaymentAttempt', id: string, depositId: string, attemptNumber: string, ticketId: string, eventId: string | null, buyerId: string, amount: string, currency: string, provider: string, payerPhone: string, status: PaymentAttemptStatus, providerStatus: string | null, providerTransactionId: string | null, failureCode: string | null, failureMessage: string | null, webhookProcessed: boolean, retryCount: number, lastError: string | null, fulfilled: boolean, reviewStatus: string | null, reviewedBy: string | null, reviewedAt: string | null, reviewNotes: string | null, notes: string | null, riskScore: number | null, riskLevel: string | null, riskFlags: Array<string>, createdAt: string | null, updatedAt: string | null, expiresAt: string | null } };

export type TxTicketFieldsFragment = { __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryCode: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, purchaseDate: string | null, cancelledAt: string | null, cancellationReason: string | null };

export type TxSearchTicketsQueryVariables = Exact<{
  filter: TicketFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type TxSearchTicketsQuery = { __typename: 'Query', searchTickets: { __typename: 'TicketOffsetPage', data: Array<{ __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryCode: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, purchaseDate: string | null, cancelledAt: string | null, cancellationReason: string | null }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type TxAdminUpdateTicketMutationVariables = Exact<{
  ticketId: Scalars['ID']['input'];
  input: AdminTicketUpdateInput;
}>;


export type TxAdminUpdateTicketMutation = { __typename: 'Mutation', adminUpdateTicket: { __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryCode: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, purchaseDate: string | null, cancelledAt: string | null, cancellationReason: string | null } };

export type TxRegenerateTicketQrMutationVariables = Exact<{
  ticketId: Scalars['ID']['input'];
}>;


export type TxRegenerateTicketQrMutation = { __typename: 'Mutation', regenerateTicketQrCode: { __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, buyerName: string | null, buyerEmail: string | null, buyerPhone: string | null, ticketCategoryCode: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, purchaseDate: string | null, cancelledAt: string | null, cancellationReason: string | null } };

export type TxBulkCancelTicketsMutationVariables = Exact<{
  ticketIds: Array<Scalars['ID']['input']> | Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type TxBulkCancelTicketsMutation = { __typename: 'Mutation', bulkCancelTickets: { __typename: 'BulkOperationResponse', processedCount: number, failedCount: number } };

export type TxReservationsByEventQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type TxReservationsByEventQuery = { __typename: 'Query', reservationsByEvent: { __typename: 'ReservationOffsetPage', data: Array<{ __typename: 'TicketReservation', id: string, eventId: string, userId: string, status: ReservationStatus, totalAmount: string, currency: string, expiresAt: string, createdAt: string, confirmedAt: string | null, releasedAt: string | null, failedAt: string | null, failureReason: string | null, items: Array<{ __typename: 'ReservationItem', tierName: string, quantity: number }> }>, pagination: { __typename: 'PaginationInfo', totalCount: number | null, pageSize: number, currentPage: number | null, totalPages: number, hasNextPage: boolean | null, hasPreviousPage: boolean | null } } };

export type TxForceExpireReservationMutationVariables = Exact<{
  reservationId: Scalars['ID']['input'];
}>;


export type TxForceExpireReservationMutation = { __typename: 'Mutation', forceExpireReservation: boolean };

export type UserListFieldsFragment = { __typename: 'User', id: string, fullName: string, email: string | null, phoneNumber: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, memberSince: string | null, createdAt: string, lastLoginAt: string | null };

export type AdminUsersQueryVariables = Exact<{
  search: InputMaybe<Scalars['String']['input']>;
  role: InputMaybe<UserType>;
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type AdminUsersQuery = { __typename: 'Query', users: { __typename: 'UserOffsetPage', content: Array<{ __typename: 'User', id: string, fullName: string, email: string | null, phoneNumber: string | null, roles: Array<UserType>, accountStatus: AccountStatus, emailVerified: boolean, memberSince: string | null, createdAt: string, lastLoginAt: string | null }>, pageInfo: { __typename: 'PageInfo', currentPage: number | null, pageSize: number | null, totalCount: number | null, hasNext: boolean | null, hasPrevious: boolean | null } } };

export type PendingCountsQueryVariables = Exact<{ [key: string]: never; }>;


export type PendingCountsQuery = { __typename: 'Query', identityPendingCounts: { __typename: 'IdentityPendingCounts', organizerApplications: number, documentVerifications: number } | null, catalogPendingCounts: { __typename: 'CatalogPendingCounts', eventReviews: number }, bookingPendingCounts: { __typename: 'BookingPendingCounts', payoutRequests: number, refundRequests: number } };

export type PlatformSummaryQueryVariables = Exact<{ [key: string]: never; }>;


export type PlatformSummaryQuery = { __typename: 'Query', platformSummary: { __typename: 'PlatformSummary', totalTicketRevenue: string, totalEscrowBalance: string, availableForPayout: string, primaryCurrency: string, totalTicketsSold: number, totalTransactions: number, pendingTransactions: number, failedTransactions: number, totalPayoutRequests: number, pendingPayoutRequests: number, totalPayoutAmount: string } };

export type TicketFieldsFragment = { __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, eventDate: string | null, eventLocationName: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, barcode: string | null, purchaseDate: string | null, validUntil: string | null };

export type ReservationFieldsFragment = { __typename: 'TicketReservation', id: string, eventId: string, totalAmount: string, currency: string, status: ReservationStatus, expiresAt: string, remainingSeconds: number | null, discountAmount: string | null, promoCodeApplied: string | null, paymentIntentId: string | null, confirmedAt: string | null, releasedAt: string | null, failedAt: string | null, failureReason: string | null, items: Array<{ __typename: 'ReservationItem', ticketTierId: string, tierName: string, quantity: number, unitPrice: string, subtotal: string }> };

export type ReserveTicketsMutationVariables = Exact<{
  input: ReserveTicketsInput;
}>;


export type ReserveTicketsMutation = { __typename: 'Mutation', reserveTickets: { __typename: 'TicketReservation', id: string, eventId: string, totalAmount: string, currency: string, status: ReservationStatus, expiresAt: string, remainingSeconds: number | null, discountAmount: string | null, promoCodeApplied: string | null, paymentIntentId: string | null, confirmedAt: string | null, releasedAt: string | null, failedAt: string | null, failureReason: string | null, items: Array<{ __typename: 'ReservationItem', ticketTierId: string, tierName: string, quantity: number, unitPrice: string, subtotal: string }> } };

export type PayReservationMutationVariables = Exact<{
  input: PayReservationInput;
}>;


export type PayReservationMutation = { __typename: 'Mutation', payReservation: { __typename: 'PaymentInitiationResponse', paymentIntentId: string | null, transactionRef: string | null, paymentStatus: string | null, reservationId: string | null } };

export type GetReservationQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetReservationQuery = { __typename: 'Query', reservation: { __typename: 'TicketReservation', id: string, eventId: string, totalAmount: string, currency: string, status: ReservationStatus, expiresAt: string, remainingSeconds: number | null, discountAmount: string | null, promoCodeApplied: string | null, paymentIntentId: string | null, confirmedAt: string | null, releasedAt: string | null, failedAt: string | null, failureReason: string | null, items: Array<{ __typename: 'ReservationItem', ticketTierId: string, tierName: string, quantity: number, unitPrice: string, subtotal: string }> } | null };

export type GetMyTicketsQueryVariables = Exact<{
  buyerId: Scalars['String']['input'];
  status: InputMaybe<TicketStatus>;
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type GetMyTicketsQuery = { __typename: 'Query', ticketsByBuyerCursorPagination: { __typename: 'TicketConnection', totalCount: number | null, edges: Array<{ __typename: 'TicketEdge', node: { __typename: 'Ticket', id: string, ticketNumber: string, eventId: string, eventTitle: string, eventDate: string | null, eventLocationName: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, barcode: string | null, purchaseDate: string | null, validUntil: string | null } }>, pageInfo: { __typename: 'PageInfo', totalElements: number | null, hasNext: boolean | null, endCursor: string | null } } };

export type BuyerMyBookingsQueryVariables = Exact<{
  filter: InputMaybe<BookingFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type BuyerMyBookingsQuery = { __typename: 'Query', myBookings: { __typename: 'BookingOffsetPage', data: Array<{ __typename: 'Booking', id: string, bookingNumber: string, reservationId: string, eventId: string, eventTitle: string | null, eventDate: string | null, status: BookingStatus, ticketCount: number, totalAmount: string, currency: string, refundedAmount: string, lateRefundStatus: string | null, contactName: string | null, contactEmail: string | null, contactPhone: string | null, createdAt: string | null, confirmedAt: string | null, items: Array<{ __typename: 'BookingItem', ticketTierId: string, tierName: string, quantity: number }>, tickets: Array<{ __typename: 'Ticket', bookingNumber: string | null, transferPending: boolean, transferCount: number, id: string, ticketNumber: string, eventId: string, eventTitle: string, eventDate: string | null, eventLocationName: string | null, ticketCategoryName: string | null, price: string, currency: string, status: TicketStatus, qrCode: string | null, barcode: string | null, purchaseDate: string | null, validUntil: string | null }> }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, hasNext: boolean | null } } };

export type BuyerResendTicketMutationVariables = Exact<{
  ticketId: Scalars['ID']['input'];
}>;


export type BuyerResendTicketMutation = { __typename: 'Mutation', resendTicket: { __typename: 'ResendTicketResult', ticketId: string, ticketNumber: string, status: string, channel: string | null, destination: string | null } };

export type BuyerCancelRefundRequestMutationVariables = Exact<{
  refundRequestId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
}>;


export type BuyerCancelRefundRequestMutation = { __typename: 'Mutation', cancelRefundRequest: { __typename: 'RefundRequest', id: string, status: RefundRequestStatus } };

export type DiscoverEventsQueryVariables = Exact<{
  filter: EventDiscoveryFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
  sort: InputMaybe<EventDiscoverySort>;
}>;


export type DiscoverEventsQuery = { __typename: 'Query', discoverEvents: { __typename: 'EventConnection', edges: Array<{ __typename: 'EventEdge', node: { __typename: 'Event', soldOut: boolean, id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null } }>, pageInfo: { __typename: 'PageInfo', totalElements: number | null, hasNext: boolean | null, endCursor: string | null } } };

export type BuyerTrendingEventsQueryVariables = Exact<{
  first: InputMaybe<Scalars['Int']['input']>;
}>;


export type BuyerTrendingEventsQuery = { __typename: 'Query', trendingEvents: Array<{ __typename: 'Event', soldOut: boolean, id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null }> };

export type BuyerRecommendedEventsQueryVariables = Exact<{
  basedOnEventIds: InputMaybe<Array<Scalars['ID']['input']> | Scalars['ID']['input']>;
  first: InputMaybe<Scalars['Int']['input']>;
}>;


export type BuyerRecommendedEventsQuery = { __typename: 'Query', recommendedEvents: Array<{ __typename: 'EventRecommendation', reason: RecommendationReason, basedOnEventId: string | null, event: { __typename: 'Event', soldOut: boolean, id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null } }> };

export type EventPageQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type EventPageQuery = { __typename: 'Query', event: { __typename: 'Event', soldOut: boolean, locationAddress: string | null, refundPolicy: string | null, cancellationPolicy: string | null, termsAndConditions: string | null, isVirtual: boolean, isFreeEvent: boolean, ageRestriction: string | null, doorsOpenAt: string | null, gettingThere: string | null, parkingInfo: string | null, bagPolicy: string | null, id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, faqs: Array<{ __typename: 'EventFaq', question: string, answer: string }> | null, runningOrder: Array<{ __typename: 'RunningOrderItem', time: string, title: string }> | null, organization: { __typename: 'Organization', id: string, verified: boolean, publishedEventCount: number } | null, accessibility: { __typename: 'EventAccessibility', wheelchairAccessible: boolean, wheelchairSeatsAvailable: number | null, signLanguageInterpreter: boolean, hearingLoopAvailable: boolean, accessibleParking: boolean, accessibleRestrooms: boolean, assistanceDogsAllowed: boolean, additionalNotes: string | null } | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, name: string, code: string, description: string | null, price: string, originalPrice: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, salesStartAt: string | null, salesEndAt: string | null, currency: string, quantity: number, soldQuantity: number, availableQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, isActive: boolean, isHidden: boolean, sortOrder: number }> | null, category: { __typename: 'EventCategory', id: string, name: string } | null } | null };

export type BuyerUnlockTierMutationVariables = Exact<{
  eventId: Scalars['ID']['input'];
  accessCode: Scalars['String']['input'];
}>;


export type BuyerUnlockTierMutation = { __typename: 'Mutation', unlockTierWithAccessCode: { __typename: 'TicketTier', id: string, name: string, code: string, description: string | null, price: string, originalPrice: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, salesStartAt: string | null, salesEndAt: string | null, currency: string, quantity: number, soldQuantity: number, availableQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, isActive: boolean, isHidden: boolean, sortOrder: number } };

export type ValidatePromoCodeQueryVariables = Exact<{
  code: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
}>;


export type ValidatePromoCodeQuery = { __typename: 'Query', validatePromoCode: { __typename: 'PromoCodeValidation', valid: boolean, discountAmount: string | null, errorMessage: string | null } };

export type InvitationByTokenQueryVariables = Exact<{
  token: Scalars['String']['input'];
}>;


export type InvitationByTokenQuery = { __typename: 'Query', invitationByToken: { __typename: 'InvitationPreview', organizationName: string, organizationLogoUrl: string | null, proposedRole: OrganizationRole, inviterDisplayName: string, expiresAt: string } | null };

export type AcceptInvitationMutationVariables = Exact<{
  token: Scalars['String']['input'];
}>;


export type AcceptInvitationMutation = { __typename: 'Mutation', acceptInvitation: { __typename: 'OrganizationMember', id: string } | null };

export type DeclineInvitationMutationVariables = Exact<{
  token: Scalars['String']['input'];
}>;


export type DeclineInvitationMutation = { __typename: 'Mutation', declineInvitation: boolean };

export type MeQueryVariables = Exact<{ [key: string]: never; }>;


export type MeQuery = { __typename: 'Query', me: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, displayName: string | null, fullName: string, deletionRequestedAt: string | null, deletionScheduledFor: string | null } | null };

export type BuyerUpdateMyProfileMutationVariables = Exact<{
  input: UpdateUserInput;
}>;


export type BuyerUpdateMyProfileMutation = { __typename: 'Mutation', updateMyProfile: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, displayName: string | null, fullName: string } };

export type BuyerRequestAccountDeletionMutationVariables = Exact<{
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type BuyerRequestAccountDeletionMutation = { __typename: 'Mutation', requestAccountDeletion: { __typename: 'User', id: string, deletionRequestedAt: string | null, deletionScheduledFor: string | null } };

export type BuyerCancelAccountDeletionMutationVariables = Exact<{ [key: string]: never; }>;


export type BuyerCancelAccountDeletionMutation = { __typename: 'Mutation', cancelAccountDeletion: { __typename: 'User', id: string, deletionRequestedAt: string | null, deletionScheduledFor: string | null } };

export type UnreadNotificationCountQueryVariables = Exact<{ [key: string]: never; }>;


export type UnreadNotificationCountQuery = { __typename: 'Query', unreadNotificationCount: number };

export type MyNotificationsQueryVariables = Exact<{
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type MyNotificationsQuery = { __typename: 'Query', myNotifications: { __typename: 'NotificationConnection', totalCount: number | null, edges: Array<{ __typename: 'NotificationEdge', node: { __typename: 'Notification', id: string, type: NotificationType, title: string, body: string, actionUrl: string | null, status: NotificationStatus, readAt: string | null, createdAt: string } }>, pageInfo: { __typename: 'PageInfo', hasNext: boolean | null, endCursor: string | null } } };

export type MarkNotificationReadMutationVariables = Exact<{
  notificationId: Scalars['ID']['input'];
}>;


export type MarkNotificationReadMutation = { __typename: 'Mutation', markNotificationRead: { __typename: 'Notification', id: string, readAt: string | null, status: NotificationStatus } | null };

export type BuyerMarkAllNotificationsReadMutationVariables = Exact<{ [key: string]: never; }>;


export type BuyerMarkAllNotificationsReadMutation = { __typename: 'Mutation', markAllNotificationsRead: number };

export type DeleteNotificationMutationVariables = Exact<{
  notificationId: Scalars['ID']['input'];
}>;


export type DeleteNotificationMutation = { __typename: 'Mutation', deleteNotification: boolean };

export type BuyerMyNotificationPreferencesQueryVariables = Exact<{ [key: string]: never; }>;


export type BuyerMyNotificationPreferencesQuery = { __typename: 'Query', myNotificationPreferences: { __typename: 'NotificationPreferences', emailEnabled: boolean, smsEnabled: boolean, whatsappEnabled: boolean, pushEnabled: boolean, inAppEnabled: boolean, ticketNotifications: boolean, eventReminders: boolean, eventUpdates: boolean, paymentNotifications: boolean, teamNotifications: boolean, marketingEmails: boolean, systemAnnouncements: boolean, reminderHoursBefore: number, quietHoursStart: string | null, quietHoursEnd: string | null, timezone: string | null } | null };

export type BuyerUpdateNotificationPreferencesMutationVariables = Exact<{
  input: UpdateNotificationPreferencesInput;
}>;


export type BuyerUpdateNotificationPreferencesMutation = { __typename: 'Mutation', updateNotificationPreferences: { __typename: 'NotificationPreferences', reminderHoursBefore: number } | null };

export type CalculateRefundAmountQueryVariables = Exact<{
  ticketId: Scalars['String']['input'];
}>;


export type CalculateRefundAmountQuery = { __typename: 'Query', calculateRefundAmount: { __typename: 'RefundCalculation', ticketId: string, ticketNumber: string, eventId: string, eventDate: string, originalAmount: string, daysBeforeEvent: number, refundPercentage: number, refundAmount: string, platformRetains: string, policyApplied: string, isEligible: boolean, ineligibleReason: string | null } };

export type CreateUserRefundRequestMutationVariables = Exact<{
  input: CreateRefundRequestInput;
}>;


export type CreateUserRefundRequestMutation = { __typename: 'Mutation', createUserRefundRequest: { __typename: 'RefundRequest', id: string, status: RefundRequestStatus } };

export type MyRefundRequestsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type MyRefundRequestsQuery = { __typename: 'Query', myRefundRequests: { __typename: 'RefundRequestOffsetPage', data: Array<{ __typename: 'RefundRequest', id: string, requestId: string, ticketId: string, ticketNumber: string, eventId: string, refundAmount: string, refundPercentage: number | null, currency: string, status: RefundRequestStatus, reason: string, rejectionReason: string | null, requestedAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, hasNext: boolean | null } } };

export type MyEventRemindersQueryVariables = Exact<{ [key: string]: never; }>;


export type MyEventRemindersQuery = { __typename: 'Query', myEventReminders: Array<{ __typename: 'EventReminder', id: string, eventId: string, ticketId: string, status: ReminderStatus }> };

export type SetEventReminderMutationVariables = Exact<{
  input: SetEventReminderInput;
}>;


export type SetEventReminderMutation = { __typename: 'Mutation', setEventReminder: { __typename: 'EventReminder', id: string } | null };

export type CancelEventReminderMutationVariables = Exact<{
  reminderId: Scalars['ID']['input'];
}>;


export type CancelEventReminderMutation = { __typename: 'Mutation', cancelEventReminder: boolean };

export type CancelReservationMutationVariables = Exact<{
  reservationId: Scalars['ID']['input'];
}>;


export type CancelReservationMutation = { __typename: 'Mutation', cancelReservation: boolean };

export type BuyerPlatformRulesQueryVariables = Exact<{ [key: string]: never; }>;


export type BuyerPlatformRulesQuery = { __typename: 'Query', publicPlatformRules: { __typename: 'PublicPlatformRules', version: number, updatedAt: string | null, currency: string, reservationHoldMinutes: number, reservationGraceMinutes: number, refundCutoffHours: number, maxTicketsPerBooking: number, rescheduleLimit: number, refundPolicies: Array<{ __typename: 'RulesRefundPolicy', code: string, label: string, summary: string, rules: Array<{ __typename: 'RulesRefundTier', daysBefore: number, percent: number }> }> } };

export type BuyerTransferRecipientQueryVariables = Exact<{
  channel: TransferChannel;
  value: Scalars['String']['input'];
}>;


export type BuyerTransferRecipientQuery = { __typename: 'Query', transferRecipient: { __typename: 'TransferRecipient', displayName: string | null, maskedContact: string } | null };

export type BuyerMyTicketTransfersQueryVariables = Exact<{
  direction: InputMaybe<TransferDirection>;
  status: InputMaybe<TicketTransferStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type BuyerMyTicketTransfersQuery = { __typename: 'Query', myTicketTransfers: { __typename: 'TicketTransferPage', data: Array<{ __typename: 'TicketTransfer', id: string, ticketId: string, ticketNumber: string, bookingNumber: string | null, eventId: string, eventTitle: string | null, status: TicketTransferStatus, direction: TransferDirection | null, fromDisplayName: string | null, toDisplayName: string | null, recipientMasked: string | null, note: string | null, createdAt: string | null, expiresAt: string | null, resolvedAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, hasNext: boolean | null } } };

export type BuyerInitiateTicketTransferMutationVariables = Exact<{
  input: InitiateTicketTransferInput;
}>;


export type BuyerInitiateTicketTransferMutation = { __typename: 'Mutation', initiateTicketTransfer: { __typename: 'TicketTransfer', id: string, ticketId: string, status: TicketTransferStatus, recipientMasked: string | null, toDisplayName: string | null, expiresAt: string | null } };

export type BuyerCancelTicketTransferMutationVariables = Exact<{
  transferId: Scalars['ID']['input'];
}>;


export type BuyerCancelTicketTransferMutation = { __typename: 'Mutation', cancelTicketTransfer: { __typename: 'TicketTransfer', id: string, status: TicketTransferStatus } };

export type BuyerAcceptTicketTransferMutationVariables = Exact<{
  transferId: Scalars['ID']['input'];
}>;


export type BuyerAcceptTicketTransferMutation = { __typename: 'Mutation', acceptTicketTransfer: { __typename: 'Ticket', id: string, ticketNumber: string, status: TicketStatus } };

export type BuyerDeclineTicketTransferMutationVariables = Exact<{
  transferId: Scalars['ID']['input'];
}>;


export type BuyerDeclineTicketTransferMutation = { __typename: 'Mutation', declineTicketTransfer: { __typename: 'TicketTransfer', id: string, status: TicketTransferStatus } };

export type EventCardFieldsFragment = { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null };

export type EventDetailFieldsFragment = { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, name: string, code: string, description: string | null, price: string, originalPrice: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, salesStartAt: string | null, salesEndAt: string | null, currency: string, quantity: number, soldQuantity: number, availableQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, isActive: boolean, isHidden: boolean, sortOrder: number }> | null, category: { __typename: 'EventCategory', id: string, name: string } | null };

export type GetPublishedEventsQueryVariables = Exact<{
  pagination: InputMaybe<CursorPaginationInput>;
}>;


export type GetPublishedEventsQuery = { __typename: 'Query', discoverEvents: { __typename: 'EventConnection', edges: Array<{ __typename: 'EventEdge', node: { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, category: { __typename: 'EventCategory', id: string, name: string } | null } }>, pageInfo: { __typename: 'PageInfo', totalElements: number | null, totalPages: number | null, currentPage: number | null, pageSize: number | null, hasNext: boolean | null, hasPrevious: boolean | null, endCursor: string | null } } };

export type GetEventByIdQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetEventByIdQuery = { __typename: 'Query', event: { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, featured: boolean, eventDateTime: string, endDateTime: string, cityName: string | null, locationName: string | null, bannerImageUrl: string | null, galleryImages: Array<string> | null, organizerName: string, soldTickets: number, totalCapacity: number, availableTickets: number, minTicketPrice: string | null, maxTicketPrice: string | null, currency: string | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, name: string, code: string, description: string | null, price: string, originalPrice: string | null, earlyBirdPrice: string | null, earlyBirdEndsAt: string | null, salesStartAt: string | null, salesEndAt: string | null, currency: string, quantity: number, soldQuantity: number, availableQuantity: number, minPerOrder: number | null, maxPerOrder: number | null, benefits: Array<string> | null, isActive: boolean, isHidden: boolean, sortOrder: number }> | null, category: { __typename: 'EventCategory', id: string, name: string } | null } | null };

export type GetActiveEventCategoriesQueryVariables = Exact<{ [key: string]: never; }>;


export type GetActiveEventCategoriesQuery = { __typename: 'Query', categories: Array<{ __typename: 'EventCategory', id: string, name: string, code: string, eventCount: number | null, imageUrl: string | null }> };

export type GetCitiesWithEventsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetCitiesWithEventsQuery = { __typename: 'Query', citiesWithEvents: Array<{ __typename: 'City', id: string, name: string, province: string | null }> };

export type GetMyPermissionsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetMyPermissionsQuery = { __typename: 'Query', myPermissions: { __typename: 'MyPermissions', permissions: Array<string>, roles: Array<string> } };

export type ReferenceOptionsQueryVariables = Exact<{
  type: ReferenceType;
}>;


export type ReferenceOptionsQuery = { __typename: 'Query', referenceData: Array<{ __typename: 'ReferenceData', id: string, code: string, name: string, description: string | null, parentCode: string | null, displayOrder: number, metadata: Record<string, unknown> | null }> };

export type EventTicketHoldersQueryVariables = Exact<{
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type EventTicketHoldersQuery = { __typename: 'Query', ticketsByEvent: { __typename: 'TicketOffsetPage', data: Array<{ __typename: 'Ticket', id: string, ticketNumber: string, buyerName: string | null, buyerEmail: string | null, ticketCategoryName: string | null, status: TicketStatus, purchaseDate: string | null, validatedAt: string | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, hasNext: boolean | null } } };

export type ValidateTicketMutationVariables = Exact<{
  input: ValidateTicketInput;
}>;


export type ValidateTicketMutation = { __typename: 'Mutation', validateTicket: { __typename: 'ValidationResult', outcome: CheckInOutcome, admitted: boolean, message: string, checkIn: { __typename: 'CheckIn', id: string, ticketNumber: string | null, method: ValidationMethod, recordedAt: string } | null, conflict: { __typename: 'CheckInConflict', id: string, type: CheckInConflictType, originalCheckInAt: string | null } | null, ticket: { __typename: 'Ticket', id: string, ticketNumber: string, buyerName: string | null, buyerEmail: string | null, ticketCategoryName: string | null, status: TicketStatus, purchaseDate: string | null, validatedAt: string | null } | null } };

export type CheckInSummaryQueryVariables = Exact<{
  eventId: Scalars['ID']['input'];
}>;


export type CheckInSummaryQuery = { __typename: 'Query', checkInSummary: { __typename: 'CheckInSummary', eventId: string, issued: number, admitted: number, conflicts: number, openConflicts: number, manualAdmissions: number, lastCheckInAt: string | null } };

export type MyDashboardStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type MyDashboardStatsQuery = { __typename: 'Query', myDashboardStats: { __typename: 'OrganizerDashboardStats', totalRevenue: string, revenueChange: number | null, revenueCurrency: string, totalTicketsSold: number, ticketsSoldChange: number | null, activeEvents: number, eventsChange: number | null, eventsEndingThisWeek: number, totalAttendees: number, attendeesChange: number | null, pendingPayouts: string, availableBalance: string } };

export type MyUpcomingEventsQueryVariables = Exact<{
  limit: InputMaybe<Scalars['Int']['input']>;
}>;


export type MyUpcomingEventsQuery = { __typename: 'Query', myUpcomingEvents: Array<{ __typename: 'OrganizerUpcomingEvent', id: string, title: string, eventDateTime: string, ticketsSold: number, totalCapacity: number, status: string, revenue: string, currency: string }> };

export type MyRevenueSeriesQueryVariables = Exact<{
  months: InputMaybe<Scalars['Int']['input']>;
}>;


export type MyRevenueSeriesQuery = { __typename: 'Query', myRevenueSeries: Array<{ __typename: 'OrganizerRevenuePoint', periodStart: string, revenue: string, ticketsSold: number, currency: string }> };

export type MyTicketMixQueryVariables = Exact<{ [key: string]: never; }>;


export type MyTicketMixQuery = { __typename: 'Query', myTicketMix: { __typename: 'OrganizerTicketMix', totalSold: number, totalRevenue: string, currency: string, rows: Array<{ __typename: 'OrganizerShareRow', name: string, count: number, revenue: string | null }> } };

export type MyCheckInRateQueryVariables = Exact<{ [key: string]: never; }>;


export type MyCheckInRateQuery = { __typename: 'Query', myCheckInRate: { __typename: 'OrganizerCheckInRate', eventId: string, eventTitle: string, eventDateTime: string | null, issued: number, scanned: number, ratePercent: number } | null };

export type MyPayoutWindowQueryVariables = Exact<{ [key: string]: never; }>;


export type MyPayoutWindowQuery = { __typename: 'Query', myPayoutWindow: { __typename: 'OrganizerPayoutWindow', availableNow: string, pendingRelease: string, currency: string, windowOpenedAt: string | null, nextReleaseAt: string | null, windowDaysTotal: number, daysElapsed: number, daysRemaining: number } };

export type MyPayoutSourcesQueryVariables = Exact<{ [key: string]: never; }>;


export type MyPayoutSourcesQuery = { __typename: 'Query', myPayoutSources: Array<{ __typename: 'OrganizerPayoutSource', escrowAccountId: string, eventId: string | null, eventTitle: string | null, availableAmount: string, currency: string, eligibleSince: string | null }> };

export type MyRecentActivityQueryVariables = Exact<{
  limit: InputMaybe<Scalars['Int']['input']>;
}>;


export type MyRecentActivityQuery = { __typename: 'Query', myRecentActivity: Array<{ __typename: 'OrganizerActivityItem', id: string, type: OrganizerActivityType, message: string, timestamp: string, eventId: string | null, eventTitle: string | null, amount: string | null, currency: string | null }> };

export type MyEventsQueryVariables = Exact<{
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type MyEventsQuery = { __typename: 'Query', myEvents: { __typename: 'EventOffsetPage', totalElements: number, totalPages: number, hasNext: boolean, content: Array<{ __typename: 'Event', id: string, title: string, status: EventStatus, eventDateTime: string, endDateTime: string, locationName: string | null, cityName: string | null, bannerImageUrl: string | null, totalCapacity: number, soldTickets: number, revenue: string, currency: string | null }> } };

export type PublishEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type PublishEventMutation = { __typename: 'Mutation', publishEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type UnpublishEventMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type UnpublishEventMutation = { __typename: 'Mutation', unpublishEvent: { __typename: 'Event', id: string, status: EventStatus } };

export type MyEventDetailQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type MyEventDetailQuery = { __typename: 'Query', event: { __typename: 'Event', id: string, title: string, description: string, status: EventStatus, eventDateTime: string, endDateTime: string, locationName: string | null, locationAddress: string | null, cityName: string | null, bannerImageUrl: string | null, totalCapacity: number, soldTickets: number, availableTickets: number, revenue: string, currency: string | null, rejectionReason: string | null, ticketTiers: Array<{ __typename: 'TicketTier', id: string, name: string, price: string, currency: string, quantity: number, soldQuantity: number, isActive: boolean }> | null } | null };

export type CreateEventMutationVariables = Exact<{
  input: CreateEventInput;
}>;


export type CreateEventMutation = { __typename: 'Mutation', createEvent: { __typename: 'Event', id: string, title: string, status: EventStatus } };

export type MyFinanceOverviewQueryVariables = Exact<{ [key: string]: never; }>;


export type MyFinanceOverviewQuery = { __typename: 'Query', myFinanceOverview: { __typename: 'OrganizerFinanceOverview', availableBalance: string, pendingBalance: string, totalEarned: string, currency: string, pendingPayoutRequests: number, lastPayoutDate: string | null, lastPayoutAmount: string | null, totalTicketRevenue: string, totalRefunds: string, platformFees: string, netEarnings: string, earningsThisMonth: string, earningsLastMonth: string, monthlyGrowth: number | null } };

export type MyTransactionsQueryVariables = Exact<{
  filter: InputMaybe<OrganizerTransactionFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type MyTransactionsQuery = { __typename: 'Query', myTransactions: { __typename: 'OrganizerTransactionOffsetPage', totalElements: number, totalPages: number, page: number, size: number, hasNext: boolean, hasPrevious: boolean, content: Array<{ __typename: 'OrganizerTransaction', id: string, type: OrganizerTransactionType, description: string, amount: string, currency: string, status: string, timestamp: string, eventId: string | null, eventTitle: string | null, reference: string | null }> } };

export type PayoutsByOrganizerQueryVariables = Exact<{
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
}>;


export type PayoutsByOrganizerQuery = { __typename: 'Query', payoutRequestsByOrganizer: { __typename: 'PayoutRequestOffsetPage', data: Array<{ __typename: 'PayoutRequest', id: string, requestId: string, organizerId: string, eventId: string | null, eventTitle: string | null, requestedAmount: string, settledAmount: string, currency: string, status: PayoutRequestStatus, payoutMethod: PayoutMethod | null, requestedAt: string, approvedAt: string | null, processedAt: string | null, rejectionReason: string | null, bankName: string | null, accountNumber: string | null, bankAccountName: string | null, notes: string | null, event: { __typename: 'Event', id: string, title: string } | null }>, pagination: { __typename: 'PaginationInfo', totalElements: number | null, totalPages: number, currentPage: number | null, hasNext: boolean | null, hasPrevious: boolean | null } } };

export type BankAccountsByOrganizerQueryVariables = Exact<{
  organizerId: Scalars['String']['input'];
}>;


export type BankAccountsByOrganizerQuery = { __typename: 'Query', bankAccountsByOrganizer: Array<{ __typename: 'BankAccount', id: string, organizerId: string, accountHolderName: string, bankName: string, bankCode: string | null, branchName: string | null, branchCode: string | null, accountNumber: string, accountType: string | null, currency: string, swiftCode: string | null, isDefault: boolean, isVerified: boolean, status: string, createdAt: string | null }> };

export type CreatePayoutRequestMutationVariables = Exact<{
  input: CreatePayoutRequestInput;
}>;


export type CreatePayoutRequestMutation = { __typename: 'Mutation', createPayoutRequest: { __typename: 'PayoutRequest', id: string, requestId: string, status: PayoutRequestStatus, requestedAmount: string } };

export type CreateBankAccountMutationVariables = Exact<{
  input: CreateBankAccountInput;
}>;


export type CreateBankAccountMutation = { __typename: 'Mutation', createBankAccount: { __typename: 'BankAccount', id: string } };

export type UpdateBankAccountMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: UpdateBankAccountInput;
}>;


export type UpdateBankAccountMutation = { __typename: 'Mutation', updateBankAccount: { __typename: 'BankAccount', id: string } };

export type DeleteBankAccountMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteBankAccountMutation = { __typename: 'Mutation', deleteBankAccount: string };

export type SetDefaultBankAccountMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type SetDefaultBankAccountMutation = { __typename: 'Mutation', setDefaultBankAccount: { __typename: 'BankAccount', id: string, isDefault: boolean } };

export type ApplyToBeOrganizerMutationVariables = Exact<{
  input: OrganizationApplicationInput;
}>;


export type ApplyToBeOrganizerMutation = { __typename: 'Mutation', applyToBeOrganizer: { __typename: 'Organization', id: string, ownerId: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, addressLine2: string | null, city: string | null, province: string | null, country: string | null, countryCode: string | null, postalCode: string | null, formattedAddress: string | null } | null } };

export type UpdateOrganizationApplicationMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: OrganizationApplicationInput;
}>;


export type UpdateOrganizationApplicationMutation = { __typename: 'Mutation', updateOrganizationApplication: { __typename: 'Organization', id: string, ownerId: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, addressLine2: string | null, city: string | null, province: string | null, country: string | null, countryCode: string | null, postalCode: string | null, formattedAddress: string | null } | null } | null };

export type SubmitOrganizationForReviewMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type SubmitOrganizationForReviewMutation = { __typename: 'Mutation', submitOrganizationForReview: { __typename: 'Organization', id: string, ownerId: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, addressLine2: string | null, city: string | null, province: string | null, country: string | null, countryCode: string | null, postalCode: string | null, formattedAddress: string | null } | null } | null };

export type OrganizationFieldsFragment = { __typename: 'Organization', id: string, ownerId: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, addressLine2: string | null, city: string | null, province: string | null, country: string | null, countryCode: string | null, postalCode: string | null, formattedAddress: string | null } | null };

export type MyOrganizationQueryVariables = Exact<{ [key: string]: never; }>;


export type MyOrganizationQuery = { __typename: 'Query', myOwnedOrganization: { __typename: 'Organization', id: string, ownerId: string, name: string, slug: string, description: string | null, tagline: string | null, logoUrl: string | null, bannerUrl: string | null, website: string | null, type: OrganizationType, status: OrganizationStatus, kybStatus: KybStatus, businessEmail: string | null, businessPhone: string | null, businessType: BusinessType | null, businessRegistrationNumber: string | null, taxId: string | null, verified: boolean, documentsVerified: boolean, payoutAccountVerified: boolean, verifiedAt: string | null, submittedAt: string | null, approvedAt: string | null, rejectionReason: string | null, reviewedAt: string | null, canCreateDraftEvents: boolean, canPublishEvents: boolean, canReceivePayouts: boolean, canBeEdited: boolean, canSubmitForReview: boolean, isApproved: boolean, isInApprovalWorkflow: boolean, createdAt: string, updatedAt: string | null, socialLinks: { __typename: 'SocialLinks', facebook: string | null, instagram: string | null, twitter: string | null, linkedin: string | null, youtube: string | null, tiktok: string | null } | null, businessAddress: { __typename: 'BusinessAddress', addressLine1: string | null, addressLine2: string | null, city: string | null, province: string | null, country: string | null, countryCode: string | null, postalCode: string | null, formattedAddress: string | null } | null } | null };

export type MyNotificationPreferencesQueryVariables = Exact<{ [key: string]: never; }>;


export type MyNotificationPreferencesQuery = { __typename: 'Query', myNotificationPreferences: { __typename: 'NotificationPreferences', id: string, emailEnabled: boolean, smsEnabled: boolean, whatsappEnabled: boolean, pushEnabled: boolean, inAppEnabled: boolean, ticketNotifications: boolean, eventReminders: boolean, eventUpdates: boolean, paymentNotifications: boolean, teamNotifications: boolean, marketingEmails: boolean, systemAnnouncements: boolean } | null };

export type UpdateNotificationPreferencesMutationVariables = Exact<{
  input: UpdateNotificationPreferencesInput;
}>;


export type UpdateNotificationPreferencesMutation = { __typename: 'Mutation', updateNotificationPreferences: { __typename: 'NotificationPreferences', id: string, emailEnabled: boolean, smsEnabled: boolean, whatsappEnabled: boolean, pushEnabled: boolean, inAppEnabled: boolean, ticketNotifications: boolean, eventReminders: boolean, eventUpdates: boolean, paymentNotifications: boolean, teamNotifications: boolean, marketingEmails: boolean, systemAnnouncements: boolean } | null };

export type UpdateMyProfileMutationVariables = Exact<{
  input: UpdateUserInput;
}>;


export type UpdateMyProfileMutation = { __typename: 'Mutation', updateMyProfile: { __typename: 'User', id: string, firstName: string | null, lastName: string | null, fullName: string } };

export type MyTeamMembersQueryVariables = Exact<{ [key: string]: never; }>;


export type MyTeamMembersQuery = { __typename: 'Query', myOwnedOrganization: { __typename: 'Organization', id: string, members: Array<{ __typename: 'OrganizationMember', id: string, userId: string, role: OrganizationRole, status: MemberStatus, joinedAt: string, lastActiveAt: string | null, user: { __typename: 'User', id: string, fullName: string, username: string | null } | null }> | null } | null };

export type UpdateMemberRoleMutationVariables = Exact<{
  memberId: Scalars['ID']['input'];
  input: UpdateMemberRoleInput;
}>;


export type UpdateMemberRoleMutation = { __typename: 'Mutation', updateMemberRole: { __typename: 'OrganizationMember', id: string, role: OrganizationRole, status: MemberStatus } | null };

export type RemoveMemberMutationVariables = Exact<{
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
}>;


export type RemoveMemberMutation = { __typename: 'Mutation', removeMember: boolean };

export type InviteTeamMemberMutationVariables = Exact<{
  organizationId: Scalars['ID']['input'];
  input: InviteMemberInput;
}>;


export type InviteTeamMemberMutation = { __typename: 'Mutation', inviteTeamMember: { __typename: 'TeamInvitation', id: string, email: string | null, proposedRole: OrganizationRole } | null };
