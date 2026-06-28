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

export type AccountStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'LOCKED'
  | 'PENDING_DELETION'
  | 'PENDING_VERIFICATION'
  | 'SUSPENDED';

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

export type ApprovalAction =
  | 'APPROVED'
  | 'ASSIGNED'
  | 'CHANGES_REQUESTED'
  | 'COMMENT_ADDED'
  | 'ESCALATED'
  | 'ESCALATION_RESOLVED'
  | 'REJECTED'
  | 'RESUBMITTED'
  | 'SUBMITTED'
  | 'VIEWED';

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

export type ApprovalTimelineMutationResponse = {
  __typename: 'ApprovalTimelineMutationResponse';
  data: Maybe<ApprovalTimeline>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
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
  errors: Array<Scalars['String']['output']>;
  failedCount: Scalars['Int']['output'];
  message: Maybe<Scalars['String']['output']>;
  processedCount: Scalars['Int']['output'];
  success: Scalars['Boolean']['output'];
};

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

export type ChargebackFundSource =
  | 'ORGANIZER_ESCROW'
  | 'ORGANIZER_FUTURE'
  | 'PLATFORM_RESERVE'
  | 'WRITE_OFF';

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
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

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

export type ChartOfAccountsMutationResponse = {
  __typename: 'ChartOfAccountsMutationResponse';
  data: Maybe<ChartOfAccountsEntry>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ChartOfAccountsOffsetPage = {
  __typename: 'ChartOfAccountsOffsetPage';
  data: Array<ChartOfAccountsEntry>;
  pagination: PaginationInfo;
};

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

export type CreateBankAccountMutationResponse = {
  __typename: 'CreateBankAccountMutationResponse';
  data: Maybe<BankAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

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

export type CreateUserInput = {
  email: Scalars['String']['input'];
  firstName: Scalars['String']['input'];
  lastName: Scalars['String']['input'];
  password: InputMaybe<Scalars['String']['input']>;
  phoneNumber: InputMaybe<Scalars['PhoneNumber']['input']>;
};

export type CursorPaginationInput = {
  after: InputMaybe<Scalars['String']['input']>;
  before: InputMaybe<Scalars['String']['input']>;
  first: InputMaybe<Scalars['Int']['input']>;
  last: InputMaybe<Scalars['Int']['input']>;
};

export type DaysWaitingBreakdown = {
  __typename: 'DaysWaitingBreakdown';
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

export type DevicePlatform =
  | 'ANDROID'
  | 'IOS'
  | 'WEB';

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

export type DocumentMutationResponse = {
  __typename: 'DocumentMutationResponse';
  document: Maybe<VerificationDocument>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
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

export type EscrowAccountStatus =
  | 'ACTIVE'
  | 'CANCELLED'
  | 'CLOSED'
  | 'CREATED'
  | 'LOCKED'
  | 'PAYOUT_ELIGIBLE'
  | 'PROCESSING_PAYOUT';

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

export type EscrowJournalVerificationStatus =
  | 'BALANCE_MISMATCH'
  | 'CONSISTENT'
  | 'MISSING_JOURNAL_ACCOUNT'
  | 'NOT_FOUND'
  | 'ORPHANED_JOURNAL_ACCOUNT';

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

export type Event = {
  __typename: 'Event';
  accessibility: Maybe<EventAccessibility>;
  additionalInfo: Maybe<Scalars['JSON']['output']>;
  approvalDeadline: Maybe<Scalars['DateTime']['output']>;
  approvedAt: Maybe<Scalars['DateTime']['output']>;
  approvedBy: Maybe<Scalars['String']['output']>;
  availableTickets: Scalars['Int']['output'];
  bannerImageUrl: Maybe<Scalars['String']['output']>;
  cancellationPolicy: Maybe<Scalars['String']['output']>;
  category: Maybe<EventCategory>;
  categoryId: Maybe<Scalars['String']['output']>;
  cityName: Maybe<Scalars['String']['output']>;
  createdAt: Maybe<Scalars['DateTime']['output']>;
  createdBy: Maybe<Scalars['String']['output']>;
  currency: Maybe<Scalars['String']['output']>;
  description: Scalars['String']['output'];
  endDateTime: Scalars['DateTime']['output'];
  eventDateTime: Scalars['DateTime']['output'];
  featured: Scalars['Boolean']['output'];
  galleryImages: Maybe<Array<Scalars['String']['output']>>;
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
  organization: Maybe<Organization>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizer: Maybe<User>;
  organizerBusinessEmail: Maybe<Scalars['String']['output']>;
  organizerBusinessPhone: Maybe<Scalars['String']['output']>;
  organizerCompanyName: Maybe<Scalars['String']['output']>;
  organizerEmail: Maybe<Scalars['String']['output']>;
  organizerFirstName: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
  organizerLastName: Maybe<Scalars['String']['output']>;
  organizerName: Scalars['String']['output'];
  organizerPhone: Maybe<Scalars['String']['output']>;
  parentEventId: Maybe<Scalars['String']['output']>;
  published: Scalars['Boolean']['output'];
  publishedAt: Maybe<Scalars['DateTime']['output']>;
  recurrencePattern: Maybe<Scalars['String']['output']>;
  refundPolicy: Maybe<Scalars['String']['output']>;
  rejectedAt: Maybe<Scalars['DateTime']['output']>;
  rejectedBy: Maybe<Scalars['String']['output']>;
  rejectionReason: Maybe<Scalars['String']['output']>;
  revenue: Scalars['BigDecimal']['output'];
  salesPercentage: Scalars['Float']['output'];
  soldOut: Scalars['Boolean']['output'];
  soldTickets: Scalars['Int']['output'];
  status: EventStatus;
  submittedForApprovalAt: Maybe<Scalars['DateTime']['output']>;
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

export type EventAccessMutationResponse = {
  __typename: 'EventAccessMutationResponse';
  accessGrant: Maybe<EventAccessGrant>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
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
  coordinates: InputMaybe<CreateCoordinatesInput>;
  country: Scalars['String']['input'];
  description: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  postalCode: InputMaybe<Scalars['String']['input']>;
  province: InputMaybe<Scalars['String']['input']>;
};

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
  channels: Maybe<Array<NotificationChannel>>;
  createdAt: Scalars['DateTime']['output'];
  errorMessage: Maybe<Scalars['String']['output']>;
  eventDateTime: Maybe<Scalars['DateTime']['output']>;
  eventId: Scalars['ID']['output'];
  eventTitle: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  minutesBefore: Scalars['Int']['output'];
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
  | 'PENDING_REVIEW'
  | 'PUBLISHED';

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
  payerPhone: Scalars['PhoneNumber']['input'];
  /** Mobile money provider (e.g., MTN_MOMO_ZMB, AIRTEL_OAPI_ZMB) */
  provider: Scalars['String']['input'];
  /** Session ID for tracing */
  sessionId: InputMaybe<Scalars['String']['input']>;
  /** Ticket being purchased */
  ticketId: Scalars['String']['input'];
};

export type InvitationMutationResponse = {
  __typename: 'InvitationMutationResponse';
  invitation: Maybe<TeamInvitation>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type InvitationStatus =
  | 'ACCEPTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'PENDING'
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
  credit: InputMaybe<Scalars['BigDecimal']['input']>;
  debit: InputMaybe<Scalars['BigDecimal']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  referenceId: InputMaybe<Scalars['String']['input']>;
  referenceType: InputMaybe<Scalars['String']['input']>;
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

export type MemberMutationResponse = {
  __typename: 'MemberMutationResponse';
  member: Maybe<OrganizationMember>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type MemberStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'REMOVED'
  | 'SUSPENDED';

export type MobileMoneyAccount = {
  __typename: 'MobileMoneyAccount';
  accountHolderName: Maybe<Scalars['String']['output']>;
  maskedPhoneNumber: Maybe<Scalars['String']['output']>;
  phoneNumber: Maybe<Scalars['String']['output']>;
  provider: Maybe<MobileMoneyProvider>;
  verified: Scalars['Boolean']['output'];
};

export type MobileMoneyProvider =
  | 'AIRTEL'
  | 'MTN'
  | 'ZAMTEL';

export type Mutation = {
  __typename: 'Mutation';
  acceptChargeback: ChargebackMutationResponse;
  acceptInvitation: Maybe<OrganizationMember>;
  acceptOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  acknowledgeEscalation: ApprovalEscalationMutationResponse;
  activateEventCategory: CategoryMutationResponse;
  activatePromoCode: PromoCode;
  activateTicketTier: TierMutationResponse;
  activateUser: Scalars['Boolean']['output'];
  addApprovalComment: ApprovalTimelineMutationResponse;
  addPaymentAttemptNote: PaymentAttemptMutationResponse;
  /**
   * Add a role to a user.
   * A user can have multiple roles (e.g., CUSTOMER + ORGANIZER).
   * The CUSTOMER role is the base role that all users have.
   */
  addUserRole: UserMutationResponse;
  adminUpdateTicket: TicketMutationResponse;
  /**
   * Apply to become an organizer.
   * Creates a new organization in DRAFT status.
   * User must fill in business details and submit for approval.
   */
  applyToBeOrganizer: Organization;
  approveEvent: EventMutationResponse;
  /**
   * Approve an organization application.
   * Changes status from PENDING_REVIEW to APPROVED.
   * Organization can now publish events.
   */
  approveOrganization: Maybe<Organization>;
  approvePayoutRequest: ApprovePayoutRequestMutationResponse;
  approveRefundRequest: ApproveRefundRequestMutationResponse;
  approveVerificationDocument: Maybe<VerificationDocument>;
  assignEventReviewer: ApprovalTimelineMutationResponse;
  assignPermissionToRole: Scalars['Boolean']['output'];
  bulkApproveRefunds: BulkOperationResponse;
  bulkCancelTickets: BulkOperationResponse;
  bulkGrantEventAccess: Maybe<Array<EventAccessGrant>>;
  bulkInviteTeamMembers: Maybe<Array<TeamInvitation>>;
  bulkMarkPayoutsForReview: BulkPayoutOperationResponse;
  bulkRetryFailedPayouts: BulkPayoutOperationResponse;
  cancelAccountDeletion: MutationResponse;
  cancelEvent: EventCancellationResponse;
  cancelEventReminder: Scalars['Boolean']['output'];
  cancelOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  cancelPaymentAttempt: PaymentAttemptMutationResponse;
  cancelPayoutRequest: RejectPayoutRequestMutationResponse;
  cancelRefundRequest: RefundRequestMutationResponse;
  cancelReservation: Scalars['Boolean']['output'];
  cancelTicket: CancelTicketMutationResponse;
  changePassword: Scalars['Boolean']['output'];
  closeEscrowAccount: EscrowAccountMutationResponse;
  completeEvent: EventMutationResponse;
  completePayoutRequest: PayoutRequestMutationResponse;
  completeReconciliation: ReconciliationMutationResponse;
  completeReservation: Array<Ticket>;
  createAdminRefundRequest: RefundRequestMutationResponse;
  createBankAccount: CreateBankAccountMutationResponse;
  createChartOfAccountsEntry: ChartOfAccountsMutationResponse;
  createCity: CityMutationResponse;
  createEscrowAccount: EscrowAccountMutationResponse;
  createEvent: EventMutationResponse;
  createEventCategory: CategoryMutationResponse;
  createEventOwner: Maybe<EventAccessGrant>;
  createJournalEntry: JournalEntryMutationResponse;
  createPayoutRequest: CreatePayoutRequestMutationResponse;
  createPermission: Maybe<Permission>;
  createPlatformAccount: PlatformAccountMutationResponse;
  createPromoCode: PromoCode;
  createProvince: ProvinceMutationResponse;
  createTicketTier: TierMutationResponse;
  createUser: UserMutationResponse;
  createUserRefundRequest: CreateRefundRequestMutationResponse;
  creditPlatformAccount: PlatformAccountMutationResponse;
  deactivateChartOfAccountsEntry: ChartOfAccountsMutationResponse;
  deactivateEventCategory: CategoryMutationResponse;
  deactivatePromoCode: PromoCode;
  deactivateTicketTier: TierMutationResponse;
  deactivateUser: Scalars['Boolean']['output'];
  debitPlatformAccount: PlatformAccountMutationResponse;
  declineInvitation: Maybe<TeamInvitation>;
  declineOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  deleteBankAccount: DeleteBankAccountMutationResponse;
  deleteCity: DeleteMutationResponse;
  deleteEvent: DeleteMutationResponse;
  deleteEventCategory: DeleteMutationResponse;
  deleteNotification: Scalars['Boolean']['output'];
  deletePermission: Scalars['Boolean']['output'];
  deletePromoCode: DeleteMutationResponse;
  deleteProvince: DeleteMutationResponse;
  deleteTicketTier: DeleteMutationResponse;
  deleteVerificationDocument: Scalars['Boolean']['output'];
  disableTwoFactor: MutationResponse;
  disputeChargeback: ChargebackMutationResponse;
  duplicateEvent: EventMutationResponse;
  escalatePayoutRequest: PayoutRequestMutationResponse;
  expireTimedOutPayments: Scalars['Int']['output'];
  extendReservation: TicketReservation;
  failReconciliation: ReconciliationMutationResponse;
  featureEvent: EventMutationResponse;
  forceExpireReservation: Scalars['Boolean']['output'];
  /**
   * Get or create organization for the current user.
   * If user has no organization, creates one in DRAFT status.
   */
  getOrCreateMyOrganization: Organization;
  grantEventAccess: Maybe<EventAccessGrant>;
  initiateOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  initiatePaymentAttempt: PaymentAttemptMutationResponse;
  inviteTeamMember: Maybe<TeamInvitation>;
  leaveOrganization: Scalars['Boolean']['output'];
  linkSocialAccount: MutationResponse;
  lockEscrowAccount: EscrowAccountMutationResponse;
  lockUser: MutationResponse;
  login: AuthPayload;
  logout: Scalars['Boolean']['output'];
  markAllNotificationsRead: Scalars['Int']['output'];
  markNotificationRead: Maybe<Notification>;
  markPaymentFulfilled: PaymentAttemptMutationResponse;
  markPayoutEligible: EscrowAccountMutationResponse;
  markPayoutForReview: PayoutRequestMutationResponse;
  pollPendingPayments: Scalars['Int']['output'];
  postJournalEntry: JournalEntryMutationResponse;
  processPaymentWebhook: PaymentAttemptMutationResponse;
  processPayoutRequest: ProcessPayoutRequestMutationResponse;
  processRefundRequest: ProcessRefundRequestMutationResponse;
  publishEvent: EventMutationResponse;
  purchaseTicket: PurchaseTicketMutationResponse;
  reactivateMember: Maybe<OrganizationMember>;
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
  refreshToken: AuthPayload;
  refundTicket: RefundTicketMutationResponse;
  regenerateTicketQrCode: TicketMutationResponse;
  register: User;
  registerDevice: Maybe<UserDevice>;
  rejectEvent: EventMutationResponse;
  /**
   * Reject an organization application.
   * Changes status to REJECTED.
   */
  rejectOrganization: Maybe<Organization>;
  rejectPayoutRequest: RejectPayoutRequestMutationResponse;
  rejectRefundRequest: RejectRefundRequestMutationResponse;
  rejectVerificationDocument: Maybe<VerificationDocument>;
  removeMember: Scalars['Boolean']['output'];
  removePermissionFromRole: Scalars['Boolean']['output'];
  /**
   * Remove a role from a user.
   * Note: The CUSTOMER role cannot be removed as it is the base role.
   */
  removeUserRole: UserMutationResponse;
  reorderTicketTiers: Array<TicketTier>;
  requestAccountDeletion: MutationResponse;
  requestDocumentUploadUrl: DocumentUploadUrlResponse;
  requestEventChanges: EventMutationResponse;
  /**
   * Request changes to an organization application.
   * Changes status from PENDING_REVIEW to CHANGES_REQUESTED.
   * User can update details and resubmit.
   */
  requestOrganizationChanges: Maybe<Organization>;
  requestPhoneOtp: OtpRequestResponse;
  resendInvitation: Maybe<TeamInvitation>;
  reserveTickets: TicketReservation;
  resetPassword: Scalars['Boolean']['output'];
  resolveEscalation: ApprovalEscalationMutationResponse;
  resolvePayoutIssue: PayoutRequestMutationResponse;
  resolveReconciliationItem: ReconciliationMutationResponse;
  resumePayoutRequest: PayoutRequestMutationResponse;
  retryPaymentAttempt: PaymentAttemptMutationResponse;
  retryPayoutRequest: PayoutRequestMutationResponse;
  reverseJournalEntry: JournalEntryMutationResponse;
  revokeEventAccess: Maybe<EventAccessGrant>;
  revokeInvitation: Maybe<TeamInvitation>;
  seedChartOfAccounts: Scalars['Boolean']['output'];
  sendBulkEventPublishReminders: BulkReminderResponse;
  sendBulkNotification: Scalars['Int']['output'];
  sendEmailVerification: Scalars['Boolean']['output'];
  sendEventPublishReminder: EventMutationResponse;
  sendNotification: Maybe<Notification>;
  sendPhoneVerification: Scalars['Boolean']['output'];
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
  setDefaultBankAccount: UpdateBankAccountMutationResponse;
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
  setPaymentAttemptReviewStatus: PaymentAttemptMutationResponse;
  /**
   * Set all roles for a user (replaces existing roles).
   * The roles set must include CUSTOMER.
   */
  setUserRoles: UserMutationResponse;
  setupTwoFactor: TwoFactorSetupResponse;
  socialAuth: AuthPayload;
  startChargebackReview: ChargebackMutationResponse;
  startReconciliation: ReconciliationMutationResponse;
  submitEventForApproval: EventMutationResponse;
  /**
   * Submit organization application for admin review.
   * Changes status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW.
   */
  submitOrganizationForReview: Maybe<Organization>;
  suspendMember: Maybe<OrganizationMember>;
  suspendOrganization: Maybe<Organization>;
  suspendUser: MutationResponse;
  syncAllUsersFromKeycloak: Scalars['Boolean']['output'];
  syncEmailVerificationStatus: Maybe<User>;
  syncUserFromKeycloak: Maybe<User>;
  transferOrganizationOwnership: Maybe<OrganizationMember>;
  triggerManualEscalation: ApprovalEscalationMutationResponse;
  unassignEventReviewer: ApprovalTimelineMutationResponse;
  unlinkSocialAccount: MutationResponse;
  unlockEscrowAccount: EscrowAccountMutationResponse;
  unlockUser: MutationResponse;
  unpublishEvent: EventMutationResponse;
  unregisterDevice: Scalars['Boolean']['output'];
  unsuspendOrganization: Maybe<Organization>;
  unsuspendUser: MutationResponse;
  updateBankAccount: UpdateBankAccountMutationResponse;
  updateChartOfAccountsEntry: ChartOfAccountsMutationResponse;
  updateCity: CityMutationResponse;
  updateEscrowAccountStatus: EscrowAccountMutationResponse;
  updateEvent: EventMutationResponse;
  updateEventAccess: Maybe<EventAccessGrant>;
  updateEventAccessibility: EventMutationResponse;
  updateEventCapacity: EventMutationResponse;
  updateEventCategory: CategoryMutationResponse;
  updateMemberRole: Maybe<OrganizationMember>;
  updateMyProfile: UserMutationResponse;
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
  updatePermission: Maybe<Permission>;
  updatePlatformConfiguration: PlatformConfigurationMutationResponse;
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
  uploadVerificationDocument: VerificationDocumentUploadResponse;
  useTicket: UseTicketMutationResponse;
  validateTicket: ValidateTicketMutationResponse;
  validateToken: TokenValidation;
  verifyBankAccount: VerifyBankAccountMutationResponse;
  verifyEmail: Maybe<User>;
  verifyEscrowJournalConsistency: EscrowJournalVerificationResponse;
  verifyPaymentWithGateway: PaymentAttemptMutationResponse;
  /**
   * Verify payout account (admin only).
   * AUTHORIZATION: Only ADMIN or FINANCE role can verify accounts.
   * SECURITY:
   * - Verification status change is audit logged
   * - Only verified accounts can receive payouts
   * - Verification can be revoked if fraud detected
   */
  verifyPayoutAccount: Maybe<Organization>;
  verifyPhone: Maybe<User>;
  verifyPhoneOtp: PhoneAuthPayload;
  verifyTwoFactor: MutationResponse;
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
  id: Scalars['ID']['input'];
};


export type MutationApprovePayoutRequestArgs = {
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationApproveRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
  reviewComments: InputMaybe<Scalars['String']['input']>;
};


export type MutationApproveVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
};


export type MutationAssignEventReviewerArgs = {
  input: AssignReviewerInput;
};


export type MutationAssignPermissionToRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
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
  grants: Array<EventAccessGrantInput>;
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


export type MutationCancelEventArgs = {
  id: Scalars['ID']['input'];
  input: EventCancellationInput;
};


export type MutationCancelEventReminderArgs = {
  reminderId: Scalars['ID']['input'];
};


export type MutationCancelOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


export type MutationCancelPaymentAttemptArgs = {
  depositId: Scalars['String']['input'];
  reason: Scalars['String']['input'];
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


export type MutationCancelTicketArgs = {
  reason: Scalars['String']['input'];
  ticketNumber: Scalars['String']['input'];
};


export type MutationChangePasswordArgs = {
  newPassword: Scalars['String']['input'];
  oldPassword: Scalars['String']['input'];
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


export type MutationCompleteReservationArgs = {
  input: CompleteReservationInput;
};


export type MutationCreateAdminRefundRequestArgs = {
  bypassApproval: InputMaybe<Scalars['Boolean']['input']>;
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


export type MutationCreateEventOwnerArgs = {
  eventId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
};


export type MutationCreateJournalEntryArgs = {
  input: CreateJournalEntryInput;
};


export type MutationCreatePayoutRequestArgs = {
  input: CreatePayoutRequestInput;
};


export type MutationCreatePermissionArgs = {
  category: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
};


export type MutationCreatePlatformAccountArgs = {
  accountType: PlatformAccountType;
  currency: Scalars['String']['input'];
  name: Scalars['String']['input'];
};


export type MutationCreatePromoCodeArgs = {
  input: CreatePromoCodeInput;
};


export type MutationCreateProvinceArgs = {
  input: CreateProvinceInput;
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


export type MutationCreditPlatformAccountArgs = {
  amount: Scalars['BigDecimal']['input'];
  description: Scalars['String']['input'];
  id: Scalars['ID']['input'];
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


export type MutationDebitPlatformAccountArgs = {
  amount: Scalars['BigDecimal']['input'];
  description: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


export type MutationDeclineInvitationArgs = {
  token: Scalars['String']['input'];
};


export type MutationDeclineOwnershipTransferArgs = {
  token: Scalars['String']['input'];
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


export type MutationDeleteNotificationArgs = {
  notificationId: Scalars['ID']['input'];
};


export type MutationDeletePermissionArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeletePromoCodeArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteProvinceArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteTicketTierArgs = {
  tierId: Scalars['ID']['input'];
};


export type MutationDeleteVerificationDocumentArgs = {
  documentId: Scalars['ID']['input'];
};


export type MutationDisableTwoFactorArgs = {
  confirmationCode: Scalars['String']['input'];
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


export type MutationExtendReservationArgs = {
  minutes: Scalars['Int']['input'];
  reservationId: Scalars['ID']['input'];
};


export type MutationFailReconciliationArgs = {
  reason: Scalars['String']['input'];
  runId: Scalars['ID']['input'];
};


export type MutationFeatureEventArgs = {
  eventId: Scalars['ID']['input'];
  featured: Scalars['Boolean']['input'];
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


export type MutationInitiateOwnershipTransferArgs = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationInitiatePaymentAttemptArgs = {
  input: InitiatePaymentAttemptInput;
};


export type MutationInviteTeamMemberArgs = {
  input: InviteMemberInput;
  organizationId: Scalars['ID']['input'];
};


export type MutationLeaveOrganizationArgs = {
  organizationId: Scalars['ID']['input'];
};


export type MutationLinkSocialAccountArgs = {
  input: SocialAuthInput;
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


export type MutationLoginArgs = {
  email: Scalars['String']['input'];
  password: Scalars['String']['input'];
};


export type MutationMarkNotificationReadArgs = {
  notificationId: Scalars['ID']['input'];
};


export type MutationMarkPaymentFulfilledArgs = {
  input: MarkPaymentFulfilledInput;
};


export type MutationMarkPayoutEligibleArgs = {
  accountId: Scalars['ID']['input'];
};


export type MutationMarkPayoutForReviewArgs = {
  issueType: PayoutIssueType;
  notes: InputMaybe<Scalars['String']['input']>;
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationPostJournalEntryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationProcessPaymentWebhookArgs = {
  input: ProcessPaymentWebhookInput;
};


export type MutationProcessPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationProcessRefundRequestArgs = {
  refundRequestId: Scalars['ID']['input'];
};


export type MutationPublishEventArgs = {
  id: Scalars['ID']['input'];
};


export type MutationPurchaseTicketArgs = {
  input: TicketPurchaseInput;
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


export type MutationRecoverChargebackFundsArgs = {
  id: Scalars['ID']['input'];
  input: RecoverChargebackInput;
};


export type MutationRefreshTokenArgs = {
  refreshToken: Scalars['String']['input'];
};


export type MutationRefundTicketArgs = {
  reason: Scalars['String']['input'];
  ticketNumber: Scalars['String']['input'];
};


export type MutationRegenerateTicketQrCodeArgs = {
  ticketId: Scalars['ID']['input'];
};


export type MutationRegisterArgs = {
  input: RegisterInput;
};


export type MutationRegisterDeviceArgs = {
  input: RegisterDeviceInput;
};


export type MutationRejectEventArgs = {
  comments: Scalars['String']['input'];
  eventId: Scalars['ID']['input'];
};


export type MutationRejectOrganizationArgs = {
  id: Scalars['ID']['input'];
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


export type MutationRemoveMemberArgs = {
  memberId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationRemovePermissionFromRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
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
  input: RequestAccountDeletionInput;
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


export type MutationRequestPhoneOtpArgs = {
  channel: InputMaybe<Scalars['String']['input']>;
  phoneNumber: Scalars['String']['input'];
};


export type MutationResendInvitationArgs = {
  invitationId: Scalars['ID']['input'];
};


export type MutationReserveTicketsArgs = {
  input: ReserveTicketsInput;
};


export type MutationResetPasswordArgs = {
  email: Scalars['String']['input'];
};


export type MutationResolveEscalationArgs = {
  input: ResolveEscalationInput;
};


export type MutationResolvePayoutIssueArgs = {
  newBankAccountId: InputMaybe<Scalars['ID']['input']>;
  notes: Scalars['String']['input'];
  payoutRequestId: Scalars['ID']['input'];
  resolutionType: PayoutResolutionType;
};


export type MutationResolveReconciliationItemArgs = {
  input: ResolveReconciliationItemInput;
  runId: Scalars['ID']['input'];
};


export type MutationResumePayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationRetryPaymentAttemptArgs = {
  depositId: Scalars['String']['input'];
};


export type MutationRetryPayoutRequestArgs = {
  payoutRequestId: Scalars['ID']['input'];
};


export type MutationReverseJournalEntryArgs = {
  id: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};


export type MutationRevokeEventAccessArgs = {
  accessId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};


export type MutationRevokeInvitationArgs = {
  invitationId: Scalars['ID']['input'];
};


export type MutationSendBulkEventPublishRemindersArgs = {
  eventIds: Array<Scalars['ID']['input']>;
  triggeredBy: Scalars['String']['input'];
};


export type MutationSendBulkNotificationArgs = {
  input: SendNotificationInput;
  userIds: Array<Scalars['ID']['input']>;
};


export type MutationSendEventPublishReminderArgs = {
  eventId: Scalars['ID']['input'];
  triggeredBy: Scalars['String']['input'];
};


export type MutationSendNotificationArgs = {
  input: SendNotificationInput;
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


export type MutationSetPaymentAttemptReviewStatusArgs = {
  depositId: Scalars['String']['input'];
  notes: InputMaybe<Scalars['String']['input']>;
  reviewStatus: Scalars['String']['input'];
};


export type MutationSetUserRolesArgs = {
  roles: Array<UserType>;
  userId: Scalars['ID']['input'];
};


export type MutationSetupTwoFactorArgs = {
  input: SetupTwoFactorInput;
};


export type MutationSocialAuthArgs = {
  input: SocialAuthInput;
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


export type MutationTransferOrganizationOwnershipArgs = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
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


export type MutationUnlinkSocialAccountArgs = {
  provider: SocialProvider;
};


export type MutationUnlockEscrowAccountArgs = {
  accountId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
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


export type MutationUpdatePermissionArgs = {
  category: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  id: Scalars['ID']['input'];
  name: InputMaybe<Scalars['String']['input']>;
};


export type MutationUpdatePlatformConfigurationArgs = {
  input: UpdatePlatformConfigurationInput;
};


export type MutationUpdateProfileArgs = {
  input: Scalars['JSON']['input'];
};


export type MutationUpdatePromoCodeArgs = {
  id: Scalars['ID']['input'];
  input: UpdatePromoCodeInput;
};


export type MutationUpdateProvinceArgs = {
  id: Scalars['ID']['input'];
  input: UpdateProvinceInput;
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


export type MutationUploadVerificationDocumentArgs = {
  input: UploadVerificationDocumentInput;
};


export type MutationUseTicketArgs = {
  ticketNumber: Scalars['String']['input'];
};


export type MutationValidateTicketArgs = {
  ticketNumber: Scalars['String']['input'];
};


export type MutationValidateTokenArgs = {
  token: Scalars['String']['input'];
};


export type MutationVerifyBankAccountArgs = {
  id: Scalars['ID']['input'];
};


export type MutationVerifyEmailArgs = {
  token: Scalars['String']['input'];
};


export type MutationVerifyEscrowJournalConsistencyArgs = {
  eventId: Scalars['ID']['input'];
};


export type MutationVerifyPaymentWithGatewayArgs = {
  depositId: Scalars['String']['input'];
};


export type MutationVerifyPayoutAccountArgs = {
  organizationId: Scalars['ID']['input'];
  verified: Scalars['Boolean']['input'];
};


export type MutationVerifyPhoneArgs = {
  code: Scalars['String']['input'];
};


export type MutationVerifyPhoneOtpArgs = {
  otp: Scalars['String']['input'];
  phoneNumber: Scalars['String']['input'];
};


export type MutationVerifyTwoFactorArgs = {
  input: VerifyTwoFactorInput;
};

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
  | 'SENT';

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
  page: InputMaybe<Scalars['Int']['input']>;
  size: InputMaybe<Scalars['Int']['input']>;
  sortBy: InputMaybe<Scalars['String']['input']>;
  sortDirection: InputMaybe<SortDirection>;
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
  createdAt: Scalars['DateTime']['output'];
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
  rejectionReason: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<User>;
  settings: Maybe<OrganizationSettings>;
  slug: Scalars['String']['output'];
  socialLinks: Maybe<SocialLinks>;
  status: OrganizationStatus;
  submittedAt: Maybe<Scalars['DateTime']['output']>;
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

export type OrganizationApplicationInput = {
  bannerUrl: InputMaybe<Scalars['String']['input']>;
  businessEmail: InputMaybe<Scalars['String']['input']>;
  businessPhone: InputMaybe<Scalars['String']['input']>;
  city: InputMaybe<Scalars['String']['input']>;
  country: InputMaybe<Scalars['String']['input']>;
  description: InputMaybe<Scalars['String']['input']>;
  logoUrl: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  province: InputMaybe<Scalars['String']['input']>;
  socialLinks: InputMaybe<SocialLinksInput>;
  tagline: InputMaybe<Scalars['String']['input']>;
  type: InputMaybe<OrganizationType>;
  website: InputMaybe<Scalars['String']['input']>;
};

export type OrganizationApplicationOffsetPage = {
  __typename: 'OrganizationApplicationOffsetPage';
  content: Array<Organization>;
  pageInfo: PageInfo;
};

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

export type OrganizationMember = {
  __typename: 'OrganizationMember';
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

export type OrganizationRole =
  | 'ADMIN'
  | 'CONTRIBUTOR'
  | 'MANAGER'
  | 'MARKETER'
  | 'OWNER';

export type OrganizationSettings = {
  __typename: 'OrganizationSettings';
  allowMembersToInvite: Scalars['Boolean']['output'];
  defaultEventVisibility: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  inviteRequiresApproval: Scalars['Boolean']['output'];
  managersCanRequestPayouts: Scalars['Boolean']['output'];
  marketersCanViewFinancials: Scalars['Boolean']['output'];
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
  eventDateAfter: InputMaybe<Scalars['DateTime']['input']>;
  eventDateBefore: InputMaybe<Scalars['DateTime']['input']>;
  searchQuery: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<EventStatus>;
  statuses: InputMaybe<Array<EventStatus>>;
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
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  eventId: InputMaybe<Scalars['ID']['input']>;
  maxAmount: InputMaybe<Scalars['BigDecimal']['input']>;
  minAmount: InputMaybe<Scalars['BigDecimal']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  type: InputMaybe<OrganizerTransactionType>;
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
  transferToken: Scalars['String']['output'];
};

export type OwnershipTransferResponse = {
  __typename: 'OwnershipTransferResponse';
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  transfer: Maybe<OwnershipTransferRequest>;
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

export type PaymentAttemptMutationResponse = {
  __typename: 'PaymentAttemptMutationResponse';
  data: Maybe<PaymentAttempt>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
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

export type PaymentMethod =
  | 'BANK_TRANSFER'
  | 'CARD'
  | 'MOBILE_MONEY';

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
  verified: Scalars['Boolean']['output'];
};

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
  eventId: Maybe<Scalars['String']['output']>;
  eventTitle: Maybe<Scalars['String']['output']>;
  expectedPayoutDate: Maybe<Scalars['DateTime']['output']>;
  externalTransactionId: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isStuck: Maybe<Scalars['Boolean']['output']>;
  issueType: Maybe<PayoutIssueType>;
  lastError: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  netPayoutAmount: Scalars['BigDecimal']['output'];
  notes: Maybe<Scalars['String']['output']>;
  organizationId: Maybe<Scalars['String']['output']>;
  organizerId: Scalars['String']['output'];
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

export type Permission = {
  __typename: 'Permission';
  active: Scalars['Boolean']['output'];
  category: Maybe<Scalars['String']['output']>;
  code: Scalars['String']['output'];
  createdAt: Scalars['DateTime']['output'];
  description: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  scope: PermissionScope;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type PermissionScope =
  | 'EVENT'
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

export type PlatformAccountMutationResponse = {
  __typename: 'PlatformAccountMutationResponse';
  data: Maybe<PlatformAccount>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
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
  escalationDelayHours: Scalars['Int']['output'];
  escalationRecipientRole: Scalars['String']['output'];
  escalationReminderIntervalHours: Scalars['Int']['output'];
  id: Scalars['ID']['output'];
  maxEscalationReminders: Scalars['Int']['output'];
  organizerNotificationChannel: ApprovalNotificationChannel;
  requireCommentsOnChangesRequested: Scalars['Boolean']['output'];
  requireCommentsOnRejection: Scalars['Boolean']['output'];
  sendEscalationNotifications: Scalars['Boolean']['output'];
  sendSlaWarningNotifications: Scalars['Boolean']['output'];
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

export type Query = {
  __typename: 'Query';
  accountBalance: Maybe<AccountBalance>;
  accountSummary: Maybe<AccountSummary>;
  activeEscalationsCursorPagination: ApprovalEscalationConnection;
  activeEscalationsOffsetPagination: ApprovalEscalationOffsetPage;
  activeEventCategoriesCursorPagination: EventCategoryConnection;
  activeEventCategoriesOffsetPagination: EventCategoryOffsetPage;
  allPermissions: Array<Permission>;
  allowedStatusTransitions: Array<EventStatus>;
  approvalEscalation: Maybe<ApprovalEscalation>;
  approvalStats: ApprovalStats;
  approvalTimeline: Maybe<ApprovalTimeline>;
  approvalTimelinesByOrganizerCursorPagination: ApprovalTimelineConnection;
  approvalTimelinesByOrganizerOffsetPagination: ApprovalTimelineOffsetPage;
  approvalTimelinesCursorPagination: ApprovalTimelineConnection;
  approvalTimelinesOffsetPagination: ApprovalTimelineOffsetPage;
  approvedNotPublishedEventsCursorPagination: EventConnection;
  approvedNotPublishedEventsOffsetPagination: EventOffsetPage;
  availableTicketTiers: Array<TicketTier>;
  bankAccount: Maybe<BankAccount>;
  bankAccountsByOrganizer: Array<BankAccount>;
  calculateRefundAmount: RefundCalculation;
  cancelledEventsCursorPagination: EventConnection;
  cancelledEventsOffsetPagination: EventOffsetPage;
  chargeback: Maybe<ChargebackRecord>;
  chargebackByChargebackId: Maybe<ChargebackRecord>;
  chargebackStats: ChargebackStats;
  chargebacksByEvent: ChargebackOffsetPage;
  chargebacksByOrganizer: ChargebackOffsetPage;
  chargebacksOffsetPagination: ChargebackOffsetPage;
  chargebacksPendingRecovery: Array<ChargebackRecord>;
  chartOfAccounts: Array<ChartOfAccountsEntry>;
  chartOfAccountsByCode: Maybe<ChartOfAccountsEntry>;
  chartOfAccountsByType: Array<ChartOfAccountsEntry>;
  chartOfAccountsEntry: Maybe<ChartOfAccountsEntry>;
  chartOfAccountsOffsetPagination: ChartOfAccountsOffsetPage;
  citiesByCountryCursorPagination: CityConnection;
  citiesByCountryOffsetPagination: CityOffsetPage;
  citiesByProvinceCursorPagination: CityConnection;
  citiesByProvinceOffsetPagination: CityOffsetPage;
  citiesCursorPagination: CityConnection;
  citiesOffsetPagination: CityOffsetPage;
  citiesWithEvents: Array<City>;
  city: Maybe<City>;
  completedEventsCursorPagination: EventConnection;
  completedEventsOffsetPagination: EventOffsetPage;
  confirmedUnfulfilledPaymentAttempts: Array<PaymentAttempt>;
  currentUserPermissions: Array<Scalars['String']['output']>;
  defaultBankAccount: Maybe<BankAccount>;
  discoverEvents: EventConnection;
  draftEventsCursorPagination: EventConnection;
  draftEventsOffsetPagination: EventOffsetPage;
  escrowAccount: Maybe<EventEscrowAccount>;
  escrowAccountBalance: Maybe<Scalars['BigDecimal']['output']>;
  escrowAccountByEvent: Maybe<EventEscrowAccount>;
  escrowAccountByNumber: Maybe<EventEscrowAccount>;
  escrowAccountsByOrganizerCursorPagination: EscrowAccountConnection;
  escrowAccountsByOrganizerOffsetPagination: EscrowAccountOffsetPage;
  escrowAccountsCursorPagination: EscrowAccountConnection;
  escrowAccountsOffsetPagination: EscrowAccountOffsetPage;
  escrowBalance: Scalars['BigDecimal']['output'];
  escrowBalanceAsOf: Scalars['BigDecimal']['output'];
  escrowJournalInconsistencies: Array<EscrowJournalVerificationResponse>;
  escrowJournalVerification: EscrowJournalVerificationResponse;
  escrowJournalVerificationAll: Array<EscrowJournalVerificationResponse>;
  escrowTransaction: Maybe<StandaloneEscrowTransaction>;
  escrowTransactionsByAccount: EscrowTransactionOffsetPage;
  escrowTransactionsByTicket: Array<StandaloneEscrowTransaction>;
  escrowTransactionsUnlinked: Array<StandaloneEscrowTransaction>;
  event: Maybe<Event>;
  eventAccessGrant: Maybe<EventAccessGrant>;
  eventAccessGrantsCursorPagination: EventAccessGrantConnection;
  eventAccessGrantsOffsetPagination: EventAccessGrantOffsetPage;
  eventCategoriesCursorPagination: EventCategoryConnection;
  eventCategoriesOffsetPagination: EventCategoryOffsetPage;
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
  eventsByCategoryCursorPagination: EventConnection;
  eventsByCategoryOffsetPagination: EventOffsetPage;
  eventsByCityCursorPagination: EventConnection;
  eventsByCityOffsetPagination: EventOffsetPage;
  eventsByDateRangeCursorPagination: EventConnection;
  eventsByDateRangeOffsetPagination: EventOffsetPage;
  eventsByOrganizerCursorPagination: EventConnection;
  eventsByOrganizerOffsetPagination: EventOffsetPage;
  eventsByPriceRangeCursorPagination: EventConnection;
  eventsByPriceRangeOffsetPagination: EventOffsetPage;
  eventsByStatusCursorPagination: EventConnection;
  eventsByStatusOffsetPagination: EventOffsetPage;
  eventsCursorPagination: EventConnection;
  eventsOffsetPagination: EventOffsetPage;
  expiredReservationsCursorPagination: ReservationConnection;
  expiredReservationsOffsetPagination: ReservationOffsetPage;
  exportEventData: ReportExport;
  exportEventsReport: ReportExport;
  exportFinancialReport: ReportExport;
  exportSalesReport: ReportExport;
  failedPayoutRequestsCursorPagination: PayoutRequestConnection;
  failedPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  featuredEventsCursorPagination: EventConnection;
  featuredEventsOffsetPagination: EventOffsetPage;
  financialReport: FinancialReport;
  freeEventsCursorPagination: EventConnection;
  freeEventsOffsetPagination: EventOffsetPage;
  hasEventPermission: Scalars['Boolean']['output'];
  hasOrganizationPermission: Scalars['Boolean']['output'];
  hasPendingOwnershipTransfer: Scalars['Boolean']['output'];
  hasSuccessfulPayment: Scalars['Boolean']['output'];
  invitationByToken: Maybe<TeamInvitation>;
  isSlugAvailable: Scalars['Boolean']['output'];
  isTicketEligibleForRefund: Scalars['Boolean']['output'];
  journalEntriesByAccountCode: JournalEntryOffsetPage;
  journalEntriesByCorrelationId: Array<JournalEntry>;
  journalEntriesOffsetPagination: JournalEntryOffsetPage;
  journalEntry: Maybe<JournalEntry>;
  journalEntryByNumber: Maybe<JournalEntry>;
  latestPaymentAttemptByTicket: Maybe<PaymentAttempt>;
  location: Maybe<Location>;
  locationsByCityCursorPagination: LocationConnection;
  locationsByCountryCursorPagination: LocationConnection;
  locationsCursorPagination: LocationConnection;
  locationsNearbyCursorPagination: LocationConnection;
  me: Maybe<User>;
  myActiveReservations: Array<TicketReservation>;
  myApprovedDocumentCount: Scalars['Long']['output'];
  myDashboardStats: OrganizerDashboardStats;
  myDevices: Array<UserDevice>;
  myDraftEventsOffsetPagination: EventOffsetPage;
  myEffectivePermissions: EffectivePermissions;
  myEscalationsCursorPagination: ApprovalEscalationConnection;
  myEscalationsOffsetPagination: ApprovalEscalationOffsetPage;
  myEventAccess: Maybe<EventAccessGrant>;
  myEventAccessGrants: Array<EventAccessGrant>;
  myEventCount: Scalars['Int']['output'];
  myEventCountByStatus: Scalars['Int']['output'];
  myEventReminders: Array<EventReminder>;
  myEventRole: Maybe<EventRole>;
  myEventsOffsetPagination: EventOffsetPage;
  myFinanceOverview: OrganizerFinanceOverview;
  myNotificationPreferences: Maybe<NotificationPreferences>;
  myNotificationsCursorPagination: NotificationConnection;
  myNotificationsOffsetPagination: NotificationOffsetPage;
  myOrganizationMembership: Maybe<OrganizationMember>;
  myOrganizationRole: Maybe<OrganizationRole>;
  myOrganizations: Array<Organization>;
  myOwnedOrganization: Maybe<Organization>;
  myPendingInvitations: Array<TeamInvitation>;
  myPendingOwnershipTransfers: Array<OwnershipTransferRequest>;
  myRecentActivity: Array<OrganizerActivityItem>;
  myTransactionsOffsetPagination: OrganizerTransactionOffsetPage;
  myUpcomingEvents: Array<OrganizerUpcomingEvent>;
  myVerificationDocumentByType: Maybe<VerificationDocument>;
  myVerificationDocumentCount: Scalars['Long']['output'];
  myVerificationDocuments: Array<VerificationDocument>;
  organization: Maybe<Organization>;
  organizationApplicationsCursorPagination: OrganizationApplicationConnection;
  organizationApplicationsOffsetPagination: OrganizationApplicationOffsetPage;
  organizationByOwnerId: Maybe<Organization>;
  organizationBySlug: Maybe<Organization>;
  organizationCount: Scalars['Long']['output'];
  organizationMember: Maybe<OrganizationMember>;
  organizationMembersCursorPagination: OrganizationMemberConnection;
  organizationMembersOffsetPagination: OrganizationMemberOffsetPage;
  organizationsCursorPagination: OrganizationConnection;
  organizationsOffsetPagination: OrganizationOffsetPage;
  organizerPromoCodes: Array<PromoCode>;
  organizerStatistics: Maybe<OrganizerStatistics>;
  overdueApprovalEventsCursorPagination: EventConnection;
  overdueApprovalEventsOffsetPagination: EventOffsetPage;
  overdueApprovalTimelinesCursorPagination: ApprovalTimelineConnection;
  overdueApprovalTimelinesOffsetPagination: ApprovalTimelineOffsetPage;
  ownershipTransfer: Maybe<OwnershipTransferRequest>;
  ownershipTransferByToken: Maybe<OwnershipTransferRequest>;
  ownershipTransfers: Array<OwnershipTransferRequest>;
  paymentAttempt: Maybe<PaymentAttempt>;
  paymentAttemptByAttemptNumber: Maybe<PaymentAttempt>;
  paymentAttemptByDepositId: Maybe<PaymentAttempt>;
  paymentAttemptCountByStatus: Scalars['Int']['output'];
  paymentAttemptsByBuyer: Array<PaymentAttempt>;
  paymentAttemptsByEvent: Array<PaymentAttempt>;
  paymentAttemptsByStatus: Array<PaymentAttempt>;
  paymentAttemptsByTicket: Array<PaymentAttempt>;
  payoutRecoverySummary: PayoutRecoverySummary;
  payoutRequest: Maybe<PayoutRequest>;
  payoutRequestByRequestId: Maybe<PayoutRequest>;
  payoutRequestStats: PayoutRequestStats;
  payoutRequestsByEventCursorPagination: PayoutRequestConnection;
  payoutRequestsByEventOffsetPagination: PayoutRequestOffsetPage;
  payoutRequestsByIssueTypeOffsetPagination: PayoutRequestOffsetPage;
  payoutRequestsByOrganizerCursorPagination: PayoutRequestConnection;
  payoutRequestsByOrganizerOffsetPagination: PayoutRequestOffsetPage;
  payoutRequestsCursorPagination: PayoutRequestConnection;
  payoutRequestsForReviewCursorPagination: PayoutRequestConnection;
  payoutRequestsForReviewOffsetPagination: PayoutRequestOffsetPage;
  payoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  pendingApprovalEventsCursorPagination: EventConnection;
  pendingApprovalEventsOffsetPagination: EventOffsetPage;
  pendingApprovalTimelinesCursorPagination: ApprovalTimelineConnection;
  pendingApprovalTimelinesOffsetPagination: ApprovalTimelineOffsetPage;
  pendingChargebacks: Array<ChargebackRecord>;
  pendingInvitationsCursorPagination: TeamInvitationConnection;
  pendingInvitationsOffsetPagination: TeamInvitationOffsetPage;
  pendingJournalEntriesOffsetPagination: JournalEntryOffsetPage;
  pendingOwnershipTransfer: Maybe<OwnershipTransferRequest>;
  pendingPaymentAttempts: Array<PaymentAttempt>;
  pendingPayoutRequestsCursorPagination: PayoutRequestConnection;
  pendingPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  pendingRefundRequestsCursorPagination: RefundRequestConnection;
  pendingRefundRequestsOffsetPagination: RefundRequestOffsetPage;
  pendingVerificationDocuments: Array<VerificationDocument>;
  permission: Maybe<Permission>;
  permissionByName: Maybe<Permission>;
  permissions: Array<Permission>;
  permissionsByCategory: Array<Permission>;
  platformAccount: Maybe<PlatformAccount>;
  platformAccountByType: Maybe<PlatformAccount>;
  platformAccounts: Array<PlatformAccount>;
  platformConfiguration: PlatformConfiguration;
  platformStatistics: Maybe<PlatformStatistics>;
  platformSummary: PlatformSummary;
  popularCategories: Array<EventCategory>;
  promoCode: Maybe<PromoCode>;
  promoCodeByCode: Maybe<PromoCode>;
  province: Maybe<Province>;
  provincesByCountryCursorPagination: ProvinceConnection;
  provincesByCountryOffsetPagination: ProvinceOffsetPage;
  provincesCursorPagination: ProvinceConnection;
  provincesOffsetPagination: ProvinceOffsetPage;
  publishedEventsCursorPagination: EventConnection;
  publishedEventsOffsetPagination: EventOffsetPage;
  recentlyResolvedPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  reconciliationRun: Maybe<ReconciliationRun>;
  reconciliationRunsByType: ReconciliationRunOffsetPage;
  reconciliationRunsOffsetPagination: ReconciliationRunOffsetPage;
  reconciliationRunsRequiringReview: Array<ReconciliationRun>;
  reconciliationSummary: ReconciliationSummary;
  refundRequest: Maybe<RefundRequest>;
  refundRequestByRequestId: Maybe<RefundRequest>;
  refundRequestsByBuyerCursorPagination: RefundRequestConnection;
  refundRequestsByBuyerOffsetPagination: RefundRequestOffsetPage;
  refundRequestsByEventCursorPagination: RefundRequestConnection;
  refundRequestsByEventOffsetPagination: RefundRequestOffsetPage;
  refundRequestsByTicket: Array<RefundRequest>;
  refundRequestsCursorPagination: RefundRequestConnection;
  refundRequestsOffsetPagination: RefundRequestOffsetPage;
  reservation: Maybe<TicketReservation>;
  reservationsByEventCursorPagination: ReservationConnection;
  reservationsByEventOffsetPagination: ReservationOffsetPage;
  retryablePayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  rolePermissions: Array<Permission>;
  searchCitiesCursorPagination: CityConnection;
  searchCitiesOffsetPagination: CityOffsetPage;
  searchEventCategoriesCursorPagination: EventCategoryConnection;
  searchEventCategoriesOffsetPagination: EventCategoryOffsetPage;
  searchEventsCursorPagination: EventConnection;
  searchEventsOffsetPagination: EventOffsetPage;
  searchLocationsCursorPagination: LocationConnection;
  searchProvincesCursorPagination: ProvinceConnection;
  searchProvincesOffsetPagination: ProvinceOffsetPage;
  searchTicketsCursorPagination: TicketConnection;
  searchTicketsOffsetPagination: TicketOffsetPage;
  stuckPayoutRequestsCursorPagination: PayoutRequestConnection;
  stuckPayoutRequestsOffsetPagination: PayoutRequestOffsetPage;
  successfulPaymentAttemptByTicket: Maybe<PaymentAttempt>;
  teamStatistics: Maybe<TeamStatistics>;
  ticket: Maybe<Ticket>;
  ticketByNumber: Maybe<Ticket>;
  ticketCountByBuyer: Scalars['Int']['output'];
  ticketCountByEvent: Scalars['Int']['output'];
  ticketStats: TicketStats;
  ticketTier: Maybe<TicketTier>;
  ticketTierStatistics: Maybe<TicketTierStats>;
  ticketsByBuyerCursorPagination: TicketConnection;
  ticketsByBuyerOffsetPagination: TicketOffsetPage;
  ticketsByEventCursorPagination: TicketConnection;
  ticketsByEventOffsetPagination: TicketOffsetPage;
  ticketsByOrganizerCursorPagination: TicketConnection;
  ticketsByOrganizerOffsetPagination: TicketOffsetPage;
  transactionStats: TransactionStats;
  trialBalance: Array<AccountBalance>;
  unreadNotificationCount: Scalars['Int']['output'];
  upcomingEventsCursorPagination: EventConnection;
  upcomingEventsOffsetPagination: EventOffsetPage;
  user: Maybe<User>;
  userByEmail: Maybe<User>;
  userByPhone: Maybe<User>;
  userEventAccess: Maybe<EventAccessGrant>;
  userStats: Maybe<UserStats>;
  /**
   * Find all users who have a specific role.
   * Example: usersByRole(role: ORGANIZER) returns all users with ORGANIZER role.
   */
  usersByRole: UserOffsetPage;
  /** Count users who have a specific role. */
  usersCountByRole: Scalars['Long']['output'];
  usersCursorPagination: UserConnection;
  usersOffsetPagination: UserOffsetPage;
  validatePromoCode: PromoCodeValidation;
  verificationDocument: Maybe<VerificationDocument>;
  verificationDocuments: Array<VerificationDocument>;
};


export type QueryAccountBalanceArgs = {
  accountCode: Scalars['String']['input'];
  asOf: InputMaybe<Scalars['DateTime']['input']>;
};


export type QueryAccountSummaryArgs = {
  accountId: Scalars['String']['input'];
};


export type QueryActiveEscalationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryActiveEscalationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryActiveEventCategoriesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryActiveEventCategoriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryAllPermissionsArgs = {
  scope: InputMaybe<PermissionScope>;
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


export type QueryApprovalTimelinesByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryApprovalTimelinesByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryApprovalTimelinesCursorPaginationArgs = {
  filter: InputMaybe<ApprovalTimelineFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryApprovalTimelinesOffsetPaginationArgs = {
  filter: InputMaybe<ApprovalTimelineFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryApprovedNotPublishedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryApprovedNotPublishedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryAvailableTicketTiersArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryBankAccountArgs = {
  id: Scalars['ID']['input'];
};


export type QueryBankAccountsByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
};


export type QueryCalculateRefundAmountArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryCancelledEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryCancelledEventsOffsetPaginationArgs = {
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


export type QueryChargebacksByEventArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryChargebacksByOrganizerArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryChargebacksOffsetPaginationArgs = {
  filter: InputMaybe<ChargebackFilterInput>;
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


export type QueryChartOfAccountsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryCitiesByCountryCursorPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryCitiesByCountryOffsetPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryCitiesByProvinceCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  provinceId: Scalars['String']['input'];
};


export type QueryCitiesByProvinceOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  provinceId: Scalars['String']['input'];
};


export type QueryCitiesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryCitiesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryCityArgs = {
  id: Scalars['ID']['input'];
};


export type QueryCompletedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryCompletedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryDefaultBankAccountArgs = {
  organizerId: Scalars['String']['input'];
};


export type QueryDiscoverEventsArgs = {
  filter: EventDiscoveryFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryDraftEventsCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryDraftEventsOffsetPaginationArgs = {
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


export type QueryEscrowAccountsByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEscrowAccountsByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEscrowAccountsCursorPaginationArgs = {
  filter: InputMaybe<EscrowAccountFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEscrowAccountsOffsetPaginationArgs = {
  filter: InputMaybe<EscrowAccountFilterInput>;
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


export type QueryEscrowTransactionsByAccountArgs = {
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


export type QueryEventAccessGrantsCursorPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<AccessGrantStatus>;
};


export type QueryEventAccessGrantsOffsetPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<AccessGrantStatus>;
};


export type QueryEventCategoriesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventCategoriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
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


export type QueryEventsByCategoryCursorPaginationArgs = {
  categoryId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsByCategoryOffsetPaginationArgs = {
  categoryId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEventsByCityCursorPaginationArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsByCityOffsetPaginationArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEventsByDateRangeCursorPaginationArgs = {
  endDate: Scalars['DateTime']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  startDate: Scalars['DateTime']['input'];
};


export type QueryEventsByDateRangeOffsetPaginationArgs = {
  endDate: Scalars['DateTime']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  startDate: Scalars['DateTime']['input'];
};


export type QueryEventsByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEventsByPriceRangeCursorPaginationArgs = {
  maxPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  minPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsByPriceRangeOffsetPaginationArgs = {
  maxPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  minPrice: InputMaybe<Scalars['BigDecimal']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryEventsByStatusCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: EventStatus;
};


export type QueryEventsByStatusOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: EventStatus;
};


export type QueryEventsCursorPaginationArgs = {
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryEventsOffsetPaginationArgs = {
  filter: InputMaybe<EventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryExpiredReservationsCursorPaginationArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  pagination: InputMaybe<CursorPaginationInput>;
  since: Scalars['DateTime']['input'];
};


export type QueryExpiredReservationsOffsetPaginationArgs = {
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


export type QueryFailedPayoutRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryFailedPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryFeaturedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryFeaturedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryFinancialReportArgs = {
  filter: FinancialReportFilterInput;
};


export type QueryFreeEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryFreeEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryHasEventPermissionArgs = {
  eventId: Scalars['ID']['input'];
  permission: Scalars['String']['input'];
};


export type QueryHasOrganizationPermissionArgs = {
  organizationId: Scalars['ID']['input'];
  permission: Scalars['String']['input'];
};


export type QueryHasPendingOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryHasSuccessfulPaymentArgs = {
  ticketId: Scalars['String']['input'];
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


export type QueryJournalEntriesByAccountCodeArgs = {
  accountCode: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryJournalEntriesByCorrelationIdArgs = {
  correlationId: Scalars['String']['input'];
};


export type QueryJournalEntriesOffsetPaginationArgs = {
  filter: InputMaybe<JournalEntryFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryJournalEntryArgs = {
  id: Scalars['ID']['input'];
};


export type QueryJournalEntryByNumberArgs = {
  entryNumber: Scalars['String']['input'];
};


export type QueryLatestPaymentAttemptByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryLocationArgs = {
  id: Scalars['ID']['input'];
};


export type QueryLocationsByCityCursorPaginationArgs = {
  city: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryLocationsByCountryCursorPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryLocationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryLocationsNearbyCursorPaginationArgs = {
  input: NearbyLocationInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryMyActiveReservationsArgs = {
  userId: Scalars['ID']['input'];
};


export type QueryMyDraftEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyEffectivePermissionsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizationId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryMyEscalationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryMyEscalationsOffsetPaginationArgs = {
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


export type QueryMyEventRoleArgs = {
  eventId: Scalars['ID']['input'];
};


export type QueryMyEventsOffsetPaginationArgs = {
  filter: InputMaybe<OrganizerEventFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryMyNotificationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<NotificationStatus>;
  type: InputMaybe<NotificationType>;
};


export type QueryMyNotificationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  status: InputMaybe<NotificationStatus>;
  type: InputMaybe<NotificationType>;
};


export type QueryMyOrganizationMembershipArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryMyOrganizationRoleArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryMyRecentActivityArgs = {
  limit: InputMaybe<Scalars['Int']['input']>;
};


export type QueryMyTransactionsOffsetPaginationArgs = {
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


export type QueryOrganizationApplicationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  status: InputMaybe<OrganizationStatus>;
};


export type QueryOrganizationApplicationsOffsetPaginationArgs = {
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


export type QueryOrganizationMemberArgs = {
  organizationId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type QueryOrganizationMembersCursorPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
  role: InputMaybe<OrganizationRole>;
  status: InputMaybe<MemberStatus>;
};


export type QueryOrganizationMembersOffsetPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<OrganizationRole>;
  status: InputMaybe<MemberStatus>;
};


export type QueryOrganizationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  search: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<OrganizationStatus>;
  verified: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryOrganizationsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  search: InputMaybe<Scalars['String']['input']>;
  status: InputMaybe<OrganizationStatus>;
  verified: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryOrganizerPromoCodesArgs = {
  organizerId: Scalars['ID']['input'];
};


export type QueryOrganizerStatisticsArgs = {
  organizerId: Scalars['ID']['input'];
};


export type QueryOverdueApprovalEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryOverdueApprovalEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryOverdueApprovalTimelinesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryOverdueApprovalTimelinesOffsetPaginationArgs = {
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


export type QueryPaymentAttemptsByBuyerArgs = {
  buyerId: Scalars['String']['input'];
};


export type QueryPaymentAttemptsByEventArgs = {
  eventId: Scalars['String']['input'];
};


export type QueryPaymentAttemptsByStatusArgs = {
  status: PaymentAttemptStatus;
};


export type QueryPaymentAttemptsByTicketArgs = {
  ticketId: Scalars['String']['input'];
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


export type QueryPayoutRequestsByEventCursorPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPayoutRequestsByEventOffsetPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsByIssueTypeOffsetPaginationArgs = {
  issueType: PayoutIssueType;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsByOrganizerCursorPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPayoutRequestsByOrganizerOffsetPaginationArgs = {
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPayoutRequestsCursorPaginationArgs = {
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPayoutRequestsForReviewCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  reviewStatus: InputMaybe<PayoutReviewStatus>;
};


export type QueryPayoutRequestsForReviewOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  reviewStatus: InputMaybe<PayoutReviewStatus>;
};


export type QueryPayoutRequestsOffsetPaginationArgs = {
  filter: PayoutRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingApprovalEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPendingApprovalEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingApprovalTimelinesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPendingApprovalTimelinesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingInvitationsCursorPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPendingInvitationsOffsetPaginationArgs = {
  organizationId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingJournalEntriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingOwnershipTransferArgs = {
  organizationId: Scalars['ID']['input'];
};


export type QueryPendingPayoutRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPendingPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPendingRefundRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPendingRefundRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPermissionArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPermissionByNameArgs = {
  name: Scalars['String']['input'];
};


export type QueryPermissionsByCategoryArgs = {
  category: Scalars['String']['input'];
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


export type QueryProvincesByCountryCursorPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryProvincesByCountryOffsetPaginationArgs = {
  country: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryProvincesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryProvincesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryPublishedEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryPublishedEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRecentlyResolvedPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryReconciliationRunArgs = {
  id: Scalars['ID']['input'];
};


export type QueryReconciliationRunsByTypeArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  type: ReconciliationType;
};


export type QueryReconciliationRunsOffsetPaginationArgs = {
  filter: InputMaybe<ReconciliationFilterInput>;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryReconciliationSummaryArgs = {
  endDate: InputMaybe<Scalars['DateTime']['input']>;
  startDate: InputMaybe<Scalars['DateTime']['input']>;
  type: InputMaybe<ReconciliationType>;
};


export type QueryRefundRequestArgs = {
  id: Scalars['ID']['input'];
};


export type QueryRefundRequestByRequestIdArgs = {
  requestId: Scalars['String']['input'];
};


export type QueryRefundRequestsByBuyerCursorPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryRefundRequestsByBuyerOffsetPaginationArgs = {
  buyerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRefundRequestsByEventCursorPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryRefundRequestsByEventOffsetPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRefundRequestsByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryRefundRequestsCursorPaginationArgs = {
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryRefundRequestsOffsetPaginationArgs = {
  filter: RefundRequestFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryReservationArgs = {
  id: Scalars['ID']['input'];
};


export type QueryReservationsByEventCursorPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryReservationsByEventOffsetPaginationArgs = {
  eventId: Scalars['ID']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRetryablePayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryRolePermissionsArgs = {
  roleId: Scalars['String']['input'];
};


export type QuerySearchCitiesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchCitiesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchEventCategoriesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchEventCategoriesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchLocationsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchProvincesCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchProvincesOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
  query: Scalars['String']['input'];
};


export type QuerySearchTicketsCursorPaginationArgs = {
  filter: TicketFilterInput;
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QuerySearchTicketsOffsetPaginationArgs = {
  filter: TicketFilterInput;
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryStuckPayoutRequestsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryStuckPayoutRequestsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QuerySuccessfulPaymentAttemptByTicketArgs = {
  ticketId: Scalars['String']['input'];
};


export type QueryTeamStatisticsArgs = {
  organizationId: Scalars['ID']['input'];
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


export type QueryTicketsByEventCursorPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryTicketsByEventOffsetPaginationArgs = {
  eventId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryTicketsByOrganizerCursorPaginationArgs = {
  filter: InputMaybe<TicketFilterInput>;
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryTicketsByOrganizerOffsetPaginationArgs = {
  filter: InputMaybe<TicketFilterInput>;
  organizerId: Scalars['String']['input'];
  pagination: InputMaybe<OffsetPaginationInput>;
};


export type QueryTransactionStatsArgs = {
  eventId: InputMaybe<Scalars['ID']['input']>;
  organizerId: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTrialBalanceArgs = {
  asOf: InputMaybe<Scalars['DateTime']['input']>;
};


export type QueryUpcomingEventsCursorPaginationArgs = {
  pagination: InputMaybe<CursorPaginationInput>;
};


export type QueryUpcomingEventsOffsetPaginationArgs = {
  pagination: InputMaybe<OffsetPaginationInput>;
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


export type QueryUsersByRoleArgs = {
  activeOnly?: InputMaybe<Scalars['Boolean']['input']>;
  pagination: InputMaybe<OffsetPaginationInput>;
  role: UserType;
};


export type QueryUsersCountByRoleArgs = {
  role: UserType;
};


export type QueryUsersCursorPaginationArgs = {
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<CursorPaginationInput>;
  role: InputMaybe<UserType>;
  search: InputMaybe<Scalars['String']['input']>;
};


export type QueryUsersOffsetPaginationArgs = {
  accountStatus: InputMaybe<AccountStatus>;
  pagination: InputMaybe<OffsetPaginationInput>;
  role: InputMaybe<UserType>;
  search: InputMaybe<Scalars['String']['input']>;
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

export type ReconciliationMutationResponse = {
  __typename: 'ReconciliationMutationResponse';
  data: Maybe<ReconciliationRun>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

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

export type RecoverChargebackInput = {
  amount: InputMaybe<Scalars['BigDecimal']['input']>;
  fundSource: ChargebackFundSource;
  notes: InputMaybe<Scalars['String']['input']>;
};

export type RecoveryStatus =
  | 'IN_PROGRESS'
  | 'NOT_STARTED'
  | 'RECOVERED'
  | 'WRITTEN_OFF';

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
  requestedBy: Maybe<Scalars['String']['output']>;
  reviewComments: Maybe<Scalars['String']['output']>;
  reviewedAt: Maybe<Scalars['DateTime']['output']>;
  reviewedBy: Maybe<Scalars['String']['output']>;
  status: RefundRequestStatus;
  ticketId: Scalars['String']['output'];
  ticketNumber: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

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

export type RefundTicketMutationResponse = {
  __typename: 'RefundTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type RefundTypeSummary = {
  __typename: 'RefundTypeSummary';
  count: Scalars['Int']['output'];
  percentage: Scalars['Float']['output'];
  requestType: RefundRequestType;
  totalAmount: Scalars['BigDecimal']['output'];
};

export type RegisterDeviceInput = {
  appVersion: InputMaybe<Scalars['String']['input']>;
  deviceModel: InputMaybe<Scalars['String']['input']>;
  deviceName: InputMaybe<Scalars['String']['input']>;
  deviceToken: Scalars['String']['input'];
  osVersion: InputMaybe<Scalars['String']['input']>;
  platform: DevicePlatform;
};

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

export type ReminderStatus =
  | 'CANCELLED'
  | 'FAILED'
  | 'SCHEDULED'
  | 'SENT';

export type RemoveMemberInput = {
  memberId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
};

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

export type RequestUploadUrlInput = {
  documentType: Scalars['String']['input'];
  fileName: Scalars['String']['input'];
  fileSize: Scalars['Long']['input'];
  mimeType: Scalars['String']['input'];
};

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

export type RolePermissions = {
  __typename: 'RolePermissions';
  inheritedFrom: Maybe<Array<Scalars['String']['output']>>;
  permissions: Array<Permission>;
  role: Scalars['String']['output'];
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
  accountType: InputMaybe<Scalars['String']['input']>;
  /** SWIFT/BIC code (8 or 11 characters, alphanumeric) */
  bankCode: Scalars['String']['input'];
  /** Bank name (e.g., Zanaco, FNB, Standard Chartered) */
  bankName: Scalars['String']['input'];
  /** Branch code */
  branchCode: InputMaybe<Scalars['String']['input']>;
  /** Branch name */
  branchName: InputMaybe<Scalars['String']['input']>;
};

export type SetEventReminderInput = {
  minutesBefore: Scalars['Int']['input'];
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

export type SetupTwoFactorInput = {
  email: InputMaybe<Scalars['String']['input']>;
  method: TwoFactorMethod;
  phoneNumber: InputMaybe<Scalars['String']['input']>;
};

export type SocialAuthInput = {
  accessToken: Scalars['String']['input'];
  idToken: InputMaybe<Scalars['String']['input']>;
  provider: SocialProvider;
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
  facebook: InputMaybe<Scalars['String']['input']>;
  instagram: InputMaybe<Scalars['String']['input']>;
  linkedin: InputMaybe<Scalars['String']['input']>;
  tiktok: InputMaybe<Scalars['String']['input']>;
  twitter: InputMaybe<Scalars['String']['input']>;
  youtube: InputMaybe<Scalars['String']['input']>;
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
  reason: Maybe<Scalars['String']['output']>;
  toStatus: EventStatus;
  transitionedAt: Scalars['DateTime']['output'];
  transitionedBy: Maybe<Scalars['String']['output']>;
};

export type SuspendOrganizationInput = {
  organizationId: Scalars['ID']['input'];
  reason: Scalars['String']['input'];
};

export type TeamInvitation = {
  __typename: 'TeamInvitation';
  acceptedAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Scalars['DateTime']['output'];
  declinedAt: Maybe<Scalars['DateTime']['output']>;
  email: Scalars['String']['output'];
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

export type Ticket = {
  __typename: 'Ticket';
  barcode: Maybe<Scalars['String']['output']>;
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
  refundedAt: Maybe<Scalars['DateTime']['output']>;
  status: TicketStatus;
  ticketCategory: Maybe<TicketCategory>;
  ticketCategoryCode: Maybe<Scalars['String']['output']>;
  ticketCategoryName: Maybe<Scalars['String']['output']>;
  ticketNumber: Scalars['String']['output'];
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

export type TicketStatus =
  | 'CANCELLED'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'PAYMENT_FAILED'
  | 'PENDING_PAYMENT'
  | 'PURCHASED'
  | 'REFUNDED'
  | 'REFUND_PENDING'
  | 'USED'
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

export type TokenValidation = {
  __typename: 'TokenValidation';
  email: Maybe<Scalars['String']['output']>;
  roles: Maybe<Array<Scalars['String']['output']>>;
  userId: Maybe<Scalars['String']['output']>;
  valid: Scalars['Boolean']['output'];
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

export type TransferOwnershipInput = {
  newOwnerId: Scalars['ID']['input'];
  organizationId: Scalars['ID']['input'];
  reason: InputMaybe<Scalars['String']['input']>;
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

export type TwoFactorSetupResponse = {
  __typename: 'TwoFactorSetupResponse';
  backupCodes: Maybe<Array<Scalars['String']['output']>>;
  message: Maybe<Scalars['String']['output']>;
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

/**
 * Update payout configuration input.
 * SECURITY: Fields are validated with OWASP-compliant sanitization.
 */
export type UpdatePayoutConfigInput = {
  /** Minimum payout amount in ZMW (must be >= 100.0) */
  minimumPayoutAmount: InputMaybe<Scalars['Float']['input']>;
  /** Preferred payout method */
  preferredMethod: InputMaybe<PayoutMethod>;
  /** Payout schedule */
  schedule: InputMaybe<PayoutSchedule>;
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
  firstName: InputMaybe<Scalars['String']['input']>;
  gender: InputMaybe<Scalars['String']['input']>;
  lastName: InputMaybe<Scalars['String']['input']>;
  phoneCountry: InputMaybe<Scalars['String']['input']>;
  phoneNumber: InputMaybe<Scalars['PhoneNumber']['input']>;
};

export type UploadVerificationDocumentInput = {
  documentType: Scalars['String']['input'];
  documentUrl: InputMaybe<Scalars['String']['input']>;
  fileName: Scalars['String']['input'];
  fileSize: Scalars['Long']['input'];
  mimeType: Scalars['String']['input'];
};

export type UseTicketMutationResponse = {
  __typename: 'UseTicketMutationResponse';
  data: Maybe<Ticket>;
  errors: Array<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  metadata: Maybe<Scalars['JSON']['output']>;
  success: Scalars['Boolean']['output'];
};

export type User = {
  __typename: 'User';
  accountStatus: AccountStatus;
  active: Scalars['Boolean']['output'];
  activeTicketCount: Scalars['Int']['output'];
  createdAt: Scalars['DateTime']['output'];
  email: Scalars['String']['output'];
  emailVerified: Scalars['Boolean']['output'];
  firstName: Scalars['String']['output'];
  fullName: Scalars['String']['output'];
  gender: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  lastActiveAt: Maybe<Scalars['DateTime']['output']>;
  lastLoginAt: Maybe<Scalars['DateTime']['output']>;
  lastName: Scalars['String']['output'];
  locked: Scalars['Boolean']['output'];
  memberSince: Maybe<Scalars['DateTime']['output']>;
  notificationPreferences: Maybe<NotificationPreferences>;
  organizationMemberships: Maybe<Array<OrganizationMember>>;
  phoneCountry: Maybe<Scalars['String']['output']>;
  phoneNumber: Maybe<Scalars['PhoneNumber']['output']>;
  phoneVerified: Scalars['Boolean']['output'];
  purchasedTickets: Array<Ticket>;
  /**
   * All roles assigned to the user.
   * A user can have multiple roles. CUSTOMER is the base role that all users have.
   * Example: [CUSTOMER, ORGANIZER] for an event organizer
   */
  roles: Array<UserType>;
  socialConnections: Maybe<Array<SocialConnection>>;
  totalSpent: Scalars['BigDecimal']['output'];
  twoFactorEnabled: Scalars['Boolean']['output'];
  twoFactorMethod: Maybe<TwoFactorMethod>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  username: Scalars['String']['output'];
};

export type UserConnection = {
  __typename: 'UserConnection';
  edges: Array<UserEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
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

export type UserEdge = {
  __typename: 'UserEdge';
  cursor: Scalars['String']['output'];
  node: User;
};

export type UserMutationResponse = {
  __typename: 'UserMutationResponse';
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  user: Maybe<User>;
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
  | 'ORGANIZER'
  | 'SUPER_ADMIN';

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

export type VerificationDocumentUploadResponse = {
  __typename: 'VerificationDocumentUploadResponse';
  document: Maybe<VerificationDocument>;
  errors: Maybe<Array<FileUploadError>>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
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
