'use client';

import { useState } from 'react';
import { useMyTransactions } from '@pml.tickets/shared/api/organization-admin/modules/finance';
import { useMyEvents } from '@pml.tickets/shared/api/organization-admin/modules/events';
import { TransactionsView } from '@/components/finance/TransactionsView';

export default function TransactionsPage() {
  const [type, setType] = useState('all');
  const [eventId, setEventId] = useState('all');
  const { transactions, loading, error, refetch } = useMyTransactions({
    filter: type === 'all' && eventId === 'all' ? undefined : { type: type === 'all' ? undefined : type, eventId: eventId === 'all' ? undefined : eventId },
    size: 100,
  });
  const { events } = useMyEvents({ size: 100 });
  return (
    <TransactionsView
      transactions={transactions}
      events={events.map((e) => ({ id: e.id, title: e.title }))}
      type={type}
      eventId={eventId}
      onTypeChange={setType}
      onEventChange={setEventId}
      loading={loading}
      error={error}
      onRetry={() => void refetch()}
    />
  );
}
