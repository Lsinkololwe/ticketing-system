'use client';

import { use, useMemo, useState } from 'react';
import { RefundBookingView } from '@/components/bookings/RefundBookingView';
import { useOrganizerBooking, useRefundTicket } from '@/lib/api/bookings';
import { useOrgContext } from '@/lib/api/org-context';
import { fromBooking } from '@/lib/bookings/group';

export default function RefundBookingPage({ params }: { params: Promise<{ id: string }> }) {
  const id = decodeURIComponent(use(params).id);
  const { capabilities } = useOrgContext();
  const { booking: raw, loading } = useOrganizerBooking(id);
  const booking = useMemo(() => (raw ? fromBooking(raw) : null), [raw]);
  const { refund } = useRefundTicket();
  const [result, setResult] = useState<string | null>(null);

  const submit = async (numbers: string[], reason: string, amount: string | null) => {
    setResult(null);
    let done = 0;
    try {
      for (const n of numbers) {
        await refund(n, reason, amount);
        done += 1;
      }
    } catch (e) {
      // Rethrown so the form maps it; say how far we got.
      const err = e as Error;
      err.message = `${done} of ${numbers.length} refunded. ${err.message}`;
      throw err;
    }
    setResult(`Refunded ${done} ticket${done === 1 ? '' : 's'}. The buyer is refunded to their original payment method.`);
  };

  if (!capabilities.canRefund && !loading) return <p role="alert">Your role cannot issue refunds.</p>;
  return <RefundBookingView id={id} booking={booking} loading={loading} onSubmit={submit} resultMessage={result} />;
}
