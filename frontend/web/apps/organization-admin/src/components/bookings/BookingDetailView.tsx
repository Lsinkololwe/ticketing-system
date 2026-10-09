'use client';

import { Button, Card, CardHeader, DataTable, EmptyState, ErrorState, KeyValue, PageHeader, Skeleton, SummaryLine } from '@pml.tickets/shared/components/m3';
import type { BookingGroup } from '@/lib/bookings/group';
import { isRefundable } from '@/lib/bookings/group';
import { formatDateTime, kwacha, showPhone } from '@/lib/bookings/format';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status } from '@/components/console/Status';

export interface BookingDetailViewProps {
  id: string;
  booking: BookingGroup | null;
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  canRefund: boolean;
  /** Sends the ticket to the buyer again (SMS, WhatsApp or email as the buyer chose). */
  onResend?: (ticketId: string) => void | Promise<void>;
}

export function BookingDetailView({ id, booking: b, loading, error, onRetry, canRefund, onResend }: BookingDetailViewProps) {
  const enc = encodeURIComponent(id);
  return (
    <>
      <PageHeader
        title={b?.bookingNumber ?? id}
        subtitle={b ? <>{b.eventTitle}</> : undefined}
        breadcrumbs={[{ label: 'Bookings', href: '/bookings' }, { label: b?.bookingNumber ?? id }]}
        actions={
          b && canRefund && b.tickets.some(isRefundable) ? (
            <LinkBtn href={`/bookings/${enc}/refund`} variant="filled">
              Process refund
            </LinkBtn>
          ) : null
        }
      />
      {error ? (
        <ErrorState error={error} onRetry={onRetry} />
      ) : loading && !b ? (
        <div className="m3-stack" role="status" aria-label="Loading booking" data-testid="loading">
          <Skeleton />
          <Skeleton />
        </div>
      ) : !b ? (
        <EmptyState icon="receipt" title="Booking not found" description="It may belong to another organization." action={<LinkBtn href="/bookings">Back to bookings</LinkBtn>} />
      ) : (
        <div className="oc-cols--2-1">
          <div className="m3-stack">
            <Card>
              <CardHeader title="Booking" actions={<Status status={b.status} />} />
              <KeyValue
                columns
                items={[
                  { label: 'Buyer', value: b.buyerName },
                  { label: 'Phone', value: showPhone(b.buyerPhone) },
                  { label: 'Email', value: b.buyerEmail ?? '—' },
                  { label: 'Event', value: b.eventTitle },
                  { label: 'Booked', value: formatDateTime(b.purchasedAt) },
                  { label: 'Total', value: kwacha(b.total, b.currency) },
                ]}
              />
            </Card>
            <Card>
              <CardHeader title="Issued tickets" />
              <DataTable
                caption="Issued tickets"
                rows={b.tickets}
                getRowId={(t) => t.ticketNumber}
                empty="No tickets were issued for this booking."
                columns={[
                  { id: 'no', header: 'Ticket', rowHeader: true, cell: (t) => <span className="m3-mono">{t.ticketNumber}</span> },
                  { id: 'tier', header: 'Tier', cell: (t) => t.ticketCategoryName ?? '—' },
                  { id: 'price', header: 'Price', align: 'end', cell: (t) => <span className="m3-mono">{kwacha(t.price, t.currency)}</span> },
                  { id: 'status', header: 'Status', cell: (t) => <Status status={t.status} /> },
                  { id: 'ci', header: 'Checked in', cell: (t) => formatDateTime(t.validatedAt) },
                ]}
                rowActions={
                  onResend
                    ? (t) =>
                        t.status === 'ISSUED' || t.status === 'VALIDATED' ? (
                          <Button size="sm" variant="tonal" onClick={() => void onResend(t.id)} aria-label={`Resend ticket ${t.ticketNumber}`}>Resend ticket</Button>
                        ) : null
                    : undefined
                }
              />
              <SummaryLine label="Total" value={kwacha(b.total, b.currency)} strong />
            </Card>
          </div>
          <div className="m3-stack">
            <Card>
              <CardHeader title="Payment" />
              <KeyValue
                items={[
                  { label: 'Provider', value: b.paymentMethod ? <Status status={b.paymentMethod} /> : '—' },
                  { label: 'Status', value: b.paymentStatus ? <Status status={b.paymentStatus} /> : '—' },
                  { label: 'Reference', value: <span className="m3-mono">{b.paymentReference ?? b.bookingNumber}</span> },
                ]}
              />
            </Card>
            <Card>
              <CardHeader title="Refund history" />
              <DataTable
                caption="Refund history"
                rows={b.tickets.filter((t) => t.refundInfo || t.status === 'REFUNDED')}
                getRowId={(t) => t.ticketNumber}
                empty="No refunds on this booking."
                columns={[
                  { id: 'no', header: 'Ticket', rowHeader: true, cell: (t) => <span className="m3-mono">{t.ticketNumber}</span> },
                  { id: 'date', header: 'Date', cell: (t) => formatDateTime(t.refundInfo?.refundDate ?? t.refundedAt) },
                  { id: 'amt', header: 'Amount', align: 'end', cell: (t) => kwacha(t.refundInfo?.refundAmount ?? t.price, t.currency) },
                  { id: 'st', header: 'Status', cell: (t) => <Status status={t.refundInfo?.status ?? t.status} /> },
                  { id: 'why', header: 'Reason', cell: (t) => t.refundInfo?.reason ?? t.refundReason ?? '—' },
                ]}
              />
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
