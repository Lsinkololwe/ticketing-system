import type {
  BookingStatus,
  DiscountType,
  EscrowAccountStatus,
  HolderSegment,
  OrganizerTransactionType,
  PayoutRequestStatus,
  RefundRequestStatus,
  SalesBucket,
  TicketStatus,
} from '@pml.tickets/shared/types/graphql';

/**
 * Labels for closed API enums, typed from the GENERATED enum.
 *
 * Each map is a `Record<Enum, string>`: a member added to the schema and not labelled here fails the
 * type check on the next codegen, and a member removed from the schema fails it too. The filter and select
 * option lists are derived from the keys (`enumValues`), so no value is typed twice. Order is the order a
 * person reads the list in, not the schema's alphabetical order.
 */

/** The enum's values, in the order the label map lists them. */
export function enumValues<T extends string>(labels: Record<T, string>): T[] {
  return Object.keys(labels) as T[];
}

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  ISSUED: 'Issued',
  VALIDATED: 'Validated',
  REFUND_PENDING: 'Refund pending',
  REFUNDED: 'Refunded',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
  TRANSFERRED: 'Transferred',
};

export const REFUND_REQUEST_STATUS_LABELS: Record<RefundRequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  REJECTED: 'Rejected',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
};

export const ESCROW_STATUS_LABELS: Record<EscrowAccountStatus, string> = {
  ACTIVE: 'Active',
  HOLD: 'Hold',
  PAYOUT_ELIGIBLE: 'Payout eligible',
  SUSPENDED: 'Suspended',
  CLOSED: 'Closed',
};

export const PAYOUT_STATUS_LABELS: Record<PayoutRequestStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  ON_HOLD: 'On hold',
};

export const TRANSACTION_TYPE_LABELS: Record<OrganizerTransactionType, string> = {
  TICKET_SALE: 'Ticket sale',
  REFUND: 'Refund',
  PAYOUT: 'Payout',
  PLATFORM_FEE: 'Commission',
  ADJUSTMENT: 'Adjustment',
};

export const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {
  CONFIRMED: 'Confirmed',
  PENDING: 'Pending',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
  PARTIALLY_REFUNDED: 'Partially refunded',
  EXPIRED: 'Expired',
  FAILED: 'Failed',
  PAID_AFTER_EXPIRY_AUTO_REFUNDED: 'Paid after expiry, refunded',
};

export const DISCOUNT_TYPE_LABELS: Record<DiscountType, string> = {
  PERCENTAGE: 'Percentage',
  FIXED_AMOUNT: 'Fixed amount (K)',
};

export const HOLDER_SEGMENT_LABELS: Record<HolderSegment, string> = {
  ALL: 'Everyone with a ticket',
  NOT_ADMITTED: 'Not yet admitted',
  ADMITTED: 'Already admitted',
};

export const SALES_BUCKET_LABELS: Record<SalesBucket, string> = {
  HOUR: 'Hour',
  DAY: 'Day',
  WEEK: 'Week',
};

/** Payout requests that still hold money back from the organizer. */
export const OPEN_PAYOUT_STATUSES: readonly PayoutRequestStatus[] = ['PENDING', 'APPROVED', 'PROCESSING', 'ON_HOLD'];
