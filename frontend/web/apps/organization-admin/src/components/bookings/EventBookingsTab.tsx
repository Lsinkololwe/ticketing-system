'use client';

import { useDeferredValue, useState } from 'react';
import { useOrgContext } from '@/lib/api/org-context';
import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { useCancelTicket, useOrganizerTickets, useRefundTicket, type TicketRow } from '@/lib/api/bookings';
import { EventTicketsView, type TicketFilters } from './EventTicketsView';
import { ReasonDialog } from './ReasonDialog';

/** Event detail tab: tickets sold for the event with refund and cancel. Props: { eventId }. */
export function EventBookingsTab({ eventId }: { eventId: string }) {
  const { organization, capabilities } = useOrgContext();
  const snackbar = useSnackbar();
  const [filters, setFilters] = useState<TicketFilters>({ q: '', status: 'all', checkIn: 'all' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);
  const q = useDeferredValue(filters.q);
  const { tickets, total, loading, error, refetch } = useOrganizerTickets(
    organization?.ownerId,
    { eventId, status: filters.status === 'all' ? undefined : filters.status, searchQuery: q || undefined },
    page,
    size
  );
  const { refund } = useRefundTicket();
  const { cancel } = useCancelTicket();
  const [action, setAction] = useState<{ kind: 'refund' | 'cancel'; ticket: TicketRow } | null>(null);

  const run = async (reason: string) => {
    if (!action) return;
    if (action.kind === 'refund') await refund(action.ticket.ticketNumber, reason);
    else await cancel(action.ticket.ticketNumber, reason);
    snackbar.show(action.kind === 'refund' ? 'Ticket refunded' : 'Ticket cancelled');
    setAction(null);
  };

  return (
    <>
      <EventTicketsView
        tickets={tickets}
        filters={filters}
        onFiltersChange={(f) => {
          setFilters(f);
          setPage(0);
        }}
        loading={loading}
        error={error}
        onRetry={() => void refetch()}
        page={page}
        pageSize={size}
        total={total}
        onPageChange={setPage}
        onPageSizeChange={(n) => {
          setSize(n);
          setPage(0);
        }}
        canRefund={capabilities.canRefund}
        onRefund={(ticket) => setAction({ kind: 'refund', ticket })}
        onCancel={(ticket) => setAction({ kind: 'cancel', ticket })}
      />
      <ReasonDialog
        key={action ? `${action.kind}-${action.ticket.ticketNumber}` : 'none'}
        open={Boolean(action)}
        title={action?.kind === 'refund' ? `Refund ticket ${action.ticket.ticketNumber}` : `Cancel ticket ${action?.ticket.ticketNumber ?? ''}`}
        description={
          action?.kind === 'refund'
            ? 'The refund follows the event refund policy and returns to the buyer. Commission on the ticket is taken back.'
            : 'The QR code stops working immediately. Cancelling does not refund the buyer: use Refund for that.'
        }
        confirmLabel={action?.kind === 'refund' ? 'Refund' : 'Cancel ticket'}
        cancelLabel={action?.kind === 'refund' ? 'Cancel' : 'Keep ticket'}
        danger={action?.kind === 'cancel'}
        onClose={() => setAction(null)}
        onConfirm={run}
      />
    </>
  );
}
