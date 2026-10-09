'use client';

import { Button, DataTable, KeyValue, SideSheet } from '@pml.tickets/shared/components/m3';
import type { BookingGroup } from '@/lib/bookings/group';
import { isRefundable } from '@/lib/bookings/group';
import { formatDateTime, kwacha, showPhone } from '@/lib/bookings/format';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status } from '@/components/console/Status';

export interface BookingQuickSheetProps {
  booking: BookingGroup | null;
  canRefund: boolean;
  onResend?: (ticketId: string) => void | Promise<void>;
  onClose: () => void;
}

/** Right-hand quick view of one booking (the "bkQuick" panel). */
export function BookingQuickSheet({ booking, canRefund, onResend, onClose }: BookingQuickSheetProps) {
  const b = booking;
  const enc = b ? encodeURIComponent(b.id) : '';
  const refundable = b ? b.tickets.some(isRefundable) : false;
  return (
    <SideSheet
      open={Boolean(b)}
      onClose={onClose}
      title={b?.bookingNumber ?? 'Booking'}
      actions={
        b ? (
          <>
            <LinkBtn href={`/bookings/${enc}`} variant="tonal">
              Open full booking
            </LinkBtn>
            {canRefund && refundable ? (
              <LinkBtn href={`/bookings/${enc}/refund`} variant="text">
                Process refund
              </LinkBtn>
            ) : null}
          </>
        ) : null
      }
    >
      {b ? (
        <div className="m3-stack">
          <div className="m3-row">
            <Status status={b.status} />
            {b.paymentStatus ? <Status status={b.paymentStatus} /> : null}
          </div>
          <KeyValue
            columns
            items={[
              { label: 'Buyer', value: b.buyerName },
              { label: 'Mobile number', value: showPhone(b.buyerPhone) },
              { label: 'Event', value: b.eventTitle },
              { label: 'Booked', value: formatDateTime(b.purchasedAt) },
              { label: 'Tickets', value: String(b.tickets.length) },
              { label: 'Total', value: kwacha(b.total, b.currency) },
              { label: 'Provider', value: b.paymentMethod ? <Status status={b.paymentMethod} /> : '—' },
              { label: 'Reference', value: <span className="m3-mono">{b.paymentReference ?? b.bookingNumber}</span> },
            ]}
          />
          <h3 className="m3-card__title">Tickets</h3>
          <DataTable
            caption="Tickets in this booking"
            rows={b.tickets}
            getRowId={(t) => t.ticketNumber}
            empty="No tickets."
            columns={[
              { id: 'no', header: 'Ticket', rowHeader: true, cell: (t) => <span className="m3-mono">{t.ticketNumber}</span> },
              { id: 'price', header: 'Price', align: 'end', cell: (t) => kwacha(t.price, t.currency) },
              { id: 'status', header: 'Status', cell: (t) => <Status status={t.status} /> },
            ]}
            rowActions={
              onResend
                ? (t) =>
                    t.status === 'ISSUED' || t.status === 'VALIDATED' ? (
                      <Button size="sm" variant="text" onClick={() => void onResend(t.id)} aria-label={`Resend ticket ${t.ticketNumber}`}>Resend</Button>
                    ) : null
                : undefined
            }
          />
        </div>
      ) : null}
    </SideSheet>
  );
}

