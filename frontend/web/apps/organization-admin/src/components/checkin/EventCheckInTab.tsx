'use client';

import { useState } from 'react';
import { useCheckInSummary, useGateDeviceId, useValidateTicket } from '@pml.tickets/shared/api/organization-admin/modules/checkin';
import { useMyEventDetail } from '@pml.tickets/shared/api/organization-admin/modules/events';
import { useCheckInConflicts, useRecentCheckIns, useReviewConflict } from '@/lib/api/bookings';
import { GateView, type GateResult } from './GateView';

/** Event detail tab and gate page body: summary, code entry, recent check-ins and conflicts. Props: { eventId }. */
export function EventCheckInTab({ eventId }: { eventId: string }) {
  const { event } = useMyEventDetail(eventId);
  const { summary, loading: summaryLoading, refetch: refetchSummary } = useCheckInSummary(eventId);
  const { validateTicket, loading } = useValidateTicket(eventId);
  const deviceId = useGateDeviceId();
  const recent = useRecentCheckIns(eventId, 25);
  const { conflicts, refetch: refetchConflicts } = useCheckInConflicts(eventId);
  const { review } = useReviewConflict();
  const [result, setResult] = useState<GateResult | null>(null);

  const run = async ({ code, manual, reason }: { code: string; manual: boolean; reason?: string }) => {
    const r = await validateTicket(code, { method: manual ? 'MANUAL' : 'QR_ONLINE', reason, deviceId });
    setResult({ outcome: r.outcome, message: r.message, code, manual });
    void refetchSummary();
    void recent.refetch();
    void refetchConflicts();
  };

  return (
    <GateView
      open={event?.status === 'PUBLISHED'}
      summary={summary}
      summaryLoading={summaryLoading}
      result={result}
      busy={loading}
      onCheckIn={(i) => void run(i)}
      recent={recent.checkIns}
      recentLoading={recent.loading}
      conflicts={conflicts}
      onReviewConflict={(id, note) => review(id, note)}
    />
  );
}
