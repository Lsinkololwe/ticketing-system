'use client';

import { RowActions } from '@/components/console/RowActions';
import { useState } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import {
  Button,
  ConfirmDialog,
  FormCell,
  FormGrid,
  StatusPill,
  TextArea,
  TextField,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import {
  useTicketActions,
  useTicketSearch,
  type TicketStatus,
  type TxTicket,
} from '@pml.tickets/shared/api/admin/modules/transactions';
import { useAdminEvents } from '@pml.tickets/shared/api/admin/modules/event';
import { ReasonDialog } from '@/components/console';
import { FormDialog } from '@/features/ledger/FormDialog';
import { ListCard } from '@/features/ledger/ListCard';
import { money } from '@/lib/format';
import { TICKET_STATUS_LABELS, enumOptions, enumValues } from '@/lib/enumLabels';

export const TICKET_STATUSES: TicketStatus[] = enumValues(TICKET_STATUS_LABELS);
const PHONE = /^\+260\s?(95|96|97|76|77)[\s\d]{7,10}$/;

const editSchema = z.object({
  name: z.string().trim().min(1, 'Enter the holder name'),
  phone: z.string().regex(PHONE, 'Use +260 then 95, 96, 97, 76 or 77 and seven digits'),
  email: z.string().trim().refine((v) => v === '' || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v), 'Enter a valid email address'),
  reason: z.string().trim().min(5, 'Give a short reason (at least 5 characters)'),
});

function EditBody({ ticket, onClose }: { ticket: TxTicket; onClose: () => void }) {
  const { updateTicket } = useTicketActions();
  const snackbar = useSnackbar();
  const form = useZodForm(editSchema, {
    defaultValues: { name: ticket.buyerName ?? '', phone: ticket.buyerPhone ?? '', email: ticket.buyerEmail ?? '', reason: '' },
  });
  const { register, formState: { errors } } = form;
  return (
    <FormDialog title={`Update ticket ${ticket.ticketNumber}`} form={form} wide onClose={onClose} submitLabel="Save changes"
      onSubmit={async (v) => {
        await updateTicket(ticket.id, { buyerName: v.name, buyerPhone: v.phone, buyerEmail: v.email || undefined, notes: v.reason });
        snackbar.show('Ticket updated');
        onClose();
      }}>
      <FormGrid>
        <FormCell span={6}><TextField label="Holder name" density="form" {...register('name')} errorText={errors.name?.message} /></FormCell>
        <FormCell span={6}><TextField label="Holder phone" density="form" {...register('phone')} errorText={errors.phone?.message} /></FormCell>
        <FormCell span={12}><TextField label="Holder email" type="email" density="form" {...register('email')} errorText={errors.email?.message} /></FormCell>
        <FormCell span={12}><TextArea label="Reason for the change" rows={2} {...register('reason')} errorText={errors.reason?.message} /></FormCell>
      </FormGrid>
    </FormDialog>
  );
}

export function TicketsTab() {
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);
  const status = filters.st && filters.st !== 'all' ? (filters.st as TicketStatus) : null;
  const eventId = filters.ev && filters.ev !== 'all' ? filters.ev : null;
  const { items, pageInfo, loading, error, refetch } = useTicketSearch({ searchQuery: search, status, eventId, page: page - 1, size: 20 });
  const events = useAdminEvents({ size: 100 });
  const { regenerateQr, bulkCancel, busy } = useTicketActions();
  const snackbar = useSnackbar();
  const [editing, setEditing] = useState<TxTicket | null>(null);
  const [qr, setQr] = useState<TxTicket | null>(null);
  const [cancelling, setCancelling] = useState<TxTicket | null>(null);
  const [bulkIds, setBulkIds] = useState<string[] | null>(null);
  const [clearSel, setClearSel] = useState<(() => void) | null>(null);

  const columns: Array<DataColumn<TxTicket>> = [
    { id: 'ticket', header: 'Ticket', cell: (t) => <><span className="m3-mono">{t.ticketNumber}</span><br /><span className="m3-mono m3-muted">QR {t.qrCode ? t.qrCode.slice(-8) : '—'}</span></> },
    { id: 'event', header: 'Event', cell: (t) => <>{t.eventTitle}<br /><span className="m3-muted">{t.ticketCategoryName ?? '—'}</span></> },
    { id: 'holder', header: 'Holder', cell: (t) => <>{t.buyerName ?? '—'}<br /><span className="m3-muted">{t.buyerPhone ?? ''}</span></> },
    { id: 'price', header: 'Price', align: 'end', cell: (t) => <span className="m3-mono">{money(Number(t.price))}</span> },
    { id: 'status', header: 'Status', cell: (t) => <StatusPill status={t.status} /> },
  ];
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      snackbar.show(ok);
    } catch (e) {
      snackbar.show((e as Error).message || 'That did not go through');
    }
  };
  const cancellable = (ids: string[]) => ids.filter((id) => {
    const t = items.find((x) => x.id === id);
    return t && !['CANCELLED', 'REFUNDED'].includes(t.status);
  });

  return (
    <>
      <ListCard
        title="Tickets"
        subtitle="Search any ticket by number, holder or phone. Admin changes are written to the audit log."
        caption="Tickets"
        rows={items}
        columns={columns}
        getRowId={(t) => t.id}
        searchLabel="Search ticket number, holder or phone"
        searchText={() => ''}
        onSearchQuery={(q) => { setSearch(q); setPage(1); }}
        filters={[
          { id: 'st', label: 'Status', options: enumOptions(TICKET_STATUS_LABELS) },
          { id: 'ev', label: 'Event', options: events.events.map((e) => ({ value: e.id, label: e.title })) },
        ]}
        onFilterChange={(v) => { setFilters(v); setPage(1); }}
        serverPage={{ page, total: pageInfo.totalCount, onPage: setPage }}
        pageSize={20}
        selectable
        bulk={(ids, clear) => (
          <Button variant="filled" size="sm" danger onClick={() => { setBulkIds(cancellable(ids)); setClearSel(() => clear); }}>Cancel selected</Button>
        )}
        loading={loading}
        error={error}
        onRetry={refetch}
        empty={{ title: 'No tickets match.' }}
        rowActions={(t) => (
          <RowActions
            name={`ticket ${t.ticketNumber}`}
            primary={{ label: 'Update', onSelect: () => setEditing(t) }}
            items={[
              { id: 'qr', label: 'Regenerate QR', onSelect: () => setQr(t) },
              { id: 'cancel', label: 'Cancel ticket…', danger: true, disabled: ['CANCELLED', 'REFUNDED'].includes(t.status), onSelect: () => setCancelling(t) },
            ]}
          />
        )}
      />
      {editing ? <EditBody ticket={editing} onClose={() => setEditing(null)} /> : null}
      <ConfirmDialog open={qr !== null} onClose={() => setQr(null)} title="Regenerate the QR code?"
        description={`The old code for ${qr?.ticketNumber ?? ''} stops working at once. The holder receives the new code by SMS.`}
        confirmLabel="Regenerate QR" loading={busy}
        onConfirm={() => { const t = qr; setQr(null); if (t) void run(() => regenerateQr(t.id), 'New QR code issued'); }} />
      <ReasonDialog open={cancelling !== null} onClose={() => setCancelling(null)} title={`Cancel ticket ${cancelling?.ticketNumber ?? ''}?`}
        body="The ticket can no longer be scanned." confirmLabel="Cancel ticket" danger loading={busy}
        onConfirm={(reason) => { const t = cancelling; setCancelling(null); if (t) void run(() => bulkCancel([t.id], reason), 'Ticket cancelled'); }} />
      <ReasonDialog open={bulkIds !== null} onClose={() => setBulkIds(null)}
        title={`Cancel ${bulkIds?.length ?? 0} ticket${bulkIds?.length === 1 ? '' : 's'}?`}
        body="Refunded and already cancelled tickets are skipped." confirmLabel="Cancel tickets" danger loading={busy}
        onConfirm={(reason) => {
          const ids = bulkIds ?? [];
          setBulkIds(null);
          if (ids.length) void run(async () => { await bulkCancel(ids, reason); clearSel?.(); }, `${ids.length} ticket${ids.length === 1 ? '' : 's'} cancelled`);
        }} />
    </>
  );
}
