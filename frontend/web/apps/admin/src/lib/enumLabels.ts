/**
 * Display labels for the API's closed enums (class (b) reference data).
 *
 * Each map is a `Record` over the GENERATED GraphQL enum type, so the key set is the schema's: a member
 * added to the schema fails the type check here until it has a label, and a member removed does too.
 * Filter lists and form schemas are derived from the maps (`enumOptions`, `enumSchema`), never typed twice.
 * Platform-owned lists (banks, document types, reasons, roles ...) are NOT here: they come from the
 * backend through `useReferenceOptions`.
 */
import { z } from 'zod';
import type {
  AccountType, AlertSeverity, AnnouncementSegment, ChargebackReason, ChargebackStatus, CommissionStatus, DocumentStatus,
  EscrowAccountStatus, EventStatus, JournalEntryType, OrganizationStatus, PayoutAccountStatus, PayoutIssueType,
  PayoutMethod, PayoutRequestStatus, PayoutResolutionType, ReconciliationStatus, ReconciliationType, RefundRequestStatus,
  RefundRequestType, ReservationStatus, StockImagePurpose, TicketStatus, TransactionResolutionType,
  TransactionReviewStatus,
} from '@pml.tickets/shared/types/graphql';

export interface EnumOption<T extends string> {
  value: T;
  label: string;
}

/** `{ value, label }` options in the map's declared order. */
export function enumOptions<T extends string>(labels: Readonly<Record<T, string>>): Array<EnumOption<T>> {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }));
}

/** The keys of a label map: the enum's members, in the order the map declares them. */
export function enumValues<T extends string>(labels: Readonly<Record<T, string>>): T[] {
  return Object.keys(labels) as T[];
}

/** A zod schema accepting exactly the members of a label map (the form-side twin of the enum). */
export function enumSchema<T extends string>(labels: Readonly<Record<T, string>>, message = 'Choose one of the listed options') {
  return z.custom<T>((v) => typeof v === 'string' && Object.hasOwn(labels, v), { message });
}

export const COMMISSION_STATUS_LABELS: Record<CommissionStatus, string> = {
  PENDING: 'Pending',
  EARNED: 'Earned',
  CLAWED_BACK: 'Clawed back',
  CANCELLED: 'Cancelled',
};

export const PAYOUT_ACCOUNT_STATUS_LABELS: Record<Exclude<PayoutAccountStatus, 'NONE'>, string> = {
  PENDING: 'Pending',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

export const REFUND_STATUS_LABELS: Record<RefundRequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export const REFUND_TYPE_LABELS: Record<RefundRequestType, string> = {
  USER_REQUESTED: 'User requested',
  ADMIN_INITIATED: 'Admin initiated',
  EVENT_CANCELLED: 'Event cancelled',
  SYSTEM_AUTOMATIC: 'System automatic',
  TICKET_EXPIRED: 'Ticket expired',
  FULL: 'Full',
  PARTIAL: 'Partial',
};

export const PAYOUT_STATUS_LABELS: Record<PayoutRequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  PROCESSING: 'Processing',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const ESCROW_STATUS_LABELS: Record<EscrowAccountStatus, string> = {
  ACTIVE: 'Active',
  HOLD: 'Hold',
  PAYOUT_ELIGIBLE: 'Payout eligible',
  SUSPENDED: 'Suspended',
  CLOSED: 'Closed',
};

export const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  EXPIRED: 'Expired',
};

export const RESERVATION_STATUS_LABELS: Record<ReservationStatus, string> = {
  HELD: 'Held',
  CONFIRMED: 'Confirmed',
  EXPIRED: 'Expired',
  RELEASED: 'Released',
  FAILED: 'Failed',
};

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  ISSUED: 'Issued',
  VALIDATED: 'Validated',
  TRANSFERRED: 'Transferred',
  REFUND_PENDING: 'Refund pending',
  REFUNDED: 'Refunded',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
};

export const REVIEW_STATUS_LABELS: Record<TransactionReviewStatus, string> = {
  NONE: 'None',
  PENDING_REVIEW: 'Pending review',
  UNDER_REVIEW: 'Under review',
  REVIEWED: 'Reviewed',
  ESCALATED: 'Escalated',
};

export const TRANSACTION_RESOLUTION_LABELS: Record<TransactionResolutionType, string> = {
  AUTO_RESOLVED: 'Auto resolved',
  MANUAL_APPROVAL: 'Manual approval',
  MANUAL_REJECTION: 'Manual rejection',
  RETRIED_SUCCESS: 'Retried success',
  REFUNDED: 'Refunded',
  WRITTEN_OFF: 'Written off',
  RECONCILED: 'Reconciled',
  ESCALATED: 'Escalated',
};

export const CHARGEBACK_REASON_LABELS: Record<ChargebackReason, string> = {
  FRAUD: 'Fraud',
  NOT_RECEIVED: 'Not received',
  NOT_AS_DESCRIBED: 'Not as described',
  DUPLICATE: 'Duplicate',
  CANCELLED: 'Cancelled',
  OTHER: 'Other',
};

export const CHARGEBACK_STATUS_LABELS: Record<ChargebackStatus, string> = {
  RECEIVED: 'Received',
  UNDER_REVIEW: 'Under review',
  ACCEPTED: 'Accepted',
  DISPUTED: 'Disputed',
  WON: 'Won',
  LOST: 'Lost',
};

export const RECON_TYPE_LABELS: Record<ReconciliationType, string> = {
  GATEWAY: 'Gateway',
  BANK: 'Bank',
  ESCROW: 'Escrow',
  ESCROW_JOURNAL: 'Escrow journal',
};

export const RECON_STATUS_LABELS: Record<ReconciliationStatus, string> = {
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  REQUIRES_REVIEW: 'Requires review',
  FAILED: 'Failed',
};

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  ASSET: 'Asset',
  LIABILITY: 'Liability',
  EQUITY: 'Equity',
  REVENUE: 'Revenue',
  EXPENSE: 'Expense',
};

export const JOURNAL_ENTRY_TYPE_LABELS: Record<JournalEntryType, string> = {
  STANDARD: 'Standard',
  ADJUSTMENT: 'Adjustment',
  REVERSAL: 'Reversal',
};

export const PAYOUT_ISSUE_TYPE_LABELS: Record<PayoutIssueType, string> = {
  BANK_REJECTED: 'Bank rejected',
  INVALID_ACCOUNT_DETAILS: 'Invalid account details',
  INSUFFICIENT_ESCROW: 'Insufficient escrow',
  COMPLIANCE_HOLD: 'Compliance hold',
  SUSPECTED_FRAUD: 'Suspected fraud',
  TECHNICAL_ERROR: 'Technical error',
  PROVIDER_ERROR: 'Provider error',
  TIMEOUT: 'Timeout',
  DUPLICATE_REQUEST: 'Duplicate request',
  OTHER: 'Other',
};

export const PAYOUT_RESOLUTION_TYPE_LABELS: Record<PayoutResolutionType, string> = {
  MANUAL_APPROVAL: 'Manual approval',
  MANUAL_REJECTION: 'Manual rejection',
  RETRIED_SUCCESS: 'Retried success',
  ACCOUNT_UPDATED: 'Account updated',
  REFUNDED_TO_ESCROW: 'Refunded to escrow',
  WRITTEN_OFF: 'Written off',
  ESCALATED: 'Escalated',
  AUTO_RESOLVED: 'Auto resolved',
};

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  CHANGES_REQUESTED: 'Changes requested',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PUBLISHED: 'Live',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
};

export const ANNOUNCEMENT_SEGMENT_LABELS: Record<AnnouncementSegment, string> = {
  ALL: 'All users',
  BUYERS: 'Buyers',
  ORGANIZERS: 'Organizers',
  STAFF: 'Platform staff',
};

export const ALERT_SEVERITY_LABELS: Record<AlertSeverity, string> = {
  INFO: 'Information',
  WARNING: 'Warning',
  CRITICAL: 'Critical',
};

export const STOCK_IMAGE_PURPOSE_LABELS: Record<StockImagePurpose, string> = {
  EVENT_COVER: 'Event cover',
  CATEGORY_TILE: 'Category tile',
};

export const ORGANIZATION_STATUS_LABELS: Record<OrganizationStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending review',
  CHANGES_REQUESTED: 'Changes requested',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  INACTIVE: 'Inactive',
  PENDING_DELETION: 'Pending deletion',
};

export const PAYOUT_METHOD_LABELS: Record<PayoutMethod, string> = {
  BANK_TRANSFER: 'Bank transfer',
  CHEQUE: 'Cheque',
  MOBILE_MONEY: 'Mobile money',
};
