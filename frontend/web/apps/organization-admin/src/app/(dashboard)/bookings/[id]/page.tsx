'use client';

import { use, useMemo } from 'react';
import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { BookingDetailView } from '@/components/bookings/BookingDetailView';
import { useOrganizerBooking, useResendTicket } from '@/lib/api/bookings';
import { useOrgContext } from '@/lib/api/org-context';
import { fromBooking } from '@/lib/bookings/group';

export default function BookingPage({ params }: { params: Promise<{ id: string }> }) {
  const id = decodeURIComponent(use(params).id);
  const { capabilities } = useOrgContext();
  const { booking: raw, loading, error, refetch } = useOrganizerBooking(id);
  const booking = useMemo(() => (raw ? fromBooking(raw) : null), [raw]);
  const { resend } = useResendTicket();
  const snack = useSnackbar();
  return (
    <BookingDetailView
      id={id}
      booking={booking}
      loading={loading}
      error={error}
      onRetry={() => void refetch()}
      canRefund={capabilities.canRefund}
      onResend={async (ticketId) => {
        try {
          const r = await resend(ticketId);
          const d = r.data?.resendTicket;
          snack.show(d?.destination ? `Ticket sent to ${d.destination}` : 'Ticket sent again');
        } catch (e) {
          snack.show({ message: (e as Error).message, tone: 'error' });
        }
      }}
    />
  );
}
