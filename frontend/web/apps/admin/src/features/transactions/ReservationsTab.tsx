'use client';

import { RowActions } from '@/components/console/RowActions';
import { useState } from 'react';
import { Banner, ConfirmDialog, Select, StatusPill, useSnackbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import {
  useForceExpireReservation,
  useReservationsByEvent,
  type TxReservation,
} from '@pml.tickets/shared/api/admin/modules/transactions';
import { useAdminEvents } from '@pml.tickets/shared/api/admin/modules/event';
import { ListCard } from '@/features/ledger/ListCard';
import { formatDateTime, money } from '@/lib/format';
import { RESERVATION_STATUS_LABELS, enumOptions } from '@/lib/enumLabels';


function holdText(r: TxReservation, now = Date.now()) {
  if (r.status !== 'HELD') return formatDateTime(r.expiresAt);
  const secs = Math.max(0, Math.round((new Date(r.expiresAt).getTime() - now) / 1000));
  return `Expires in ${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}`;
}

export function ReservationsTab() {
  const events = useAdminEvents({ size: 100 });
  const [eventId, setEventId] = useState('');
  const [page, setPage] = useState(1);
  const { items, pageInfo, loading, error, refetch } = useReservationsByEvent(eventId || null, page - 1, 20);
  const { forceExpire, busy } = useForceExpireReservation();
  const snackbar = useSnackbar();
  const [target, setTarget] = useState<TxReservation | null>(null);

  const columns: Array<DataColumn<TxReservation>> = [
    { id: 'id', header: 'Reservation', cell: (r) => <span className="m3-mono">{r.id.slice(-8)}</span> },
    { id: 'buyer', header: 'Buyer', cell: (r) => <span className="m3-mono">{r.userId}</span> },
    { id: 'items', header: 'Items', cell: (r) => r.items.map((i) => `${i.quantity} × ${i.tierName}`).join(', ') },
    { id: 'total', header: 'Total', align: 'end', cell: (r) => <span className="m3-mono">{money(Number(r.totalAmount))}</span> },
    { id: 'status', header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
    { id: 'hold', header: 'Hold', cell: (r) => holdText(r) },
  ];

  return (
    <>
      <div className="m3-toolbar adm-filterbar">
        <Select label="Event" density="compact" value={eventId} onChange={(e) => { setEventId(e.target.value); setPage(1); }}>
          <option value="">Choose an event</option>
          {events.events.map((e) => <option key={e.id} value={e.id}>{e.title}</option>)}
        </Select>
      </div>
      {!eventId ? (
        <Banner tone="info" title="Choose an event">Reservations are listed per event. Pick one to see its holds.</Banner>
      ) : (
        <ListCard
          title="Reservations"
          subtitle="Tickets are held for 10 minutes plus a 5 minute grace period. Late payments are refunded in full."
          caption="Reservations"
          rows={items}
          columns={columns}
          getRowId={(r) => r.id}
          searchLabel="Search reservations"
          searchText={(r) => `${r.id} ${r.userId}`}
          filters={[{ id: 'st', label: 'Status', options: enumOptions(RESERVATION_STATUS_LABELS), match: (r, v) => r.status === v }]}
          serverPage={{ page, total: pageInfo.totalCount, onPage: setPage }}
          pageSize={20}
          loading={loading}
          error={error}
          onRetry={refetch}
          empty={{ title: 'No reservations match.' }}
          rowActions={(r) => (r.status === 'HELD' ? <RowActions name={`reservation ${r.id}`} items={[{ id: 'expire', label: 'Force expire', danger: true, onSelect: () => setTarget(r) }]} /> : null)}
        />
      )}
      <ConfirmDialog
        open={target !== null}
        onClose={() => setTarget(null)}
        title={`Force-expire ${target?.id.slice(-8) ?? ''}?`}
        description="The held tickets go back on sale immediately and the buyer sees the hold as expired."
        confirmLabel="Expire hold"
        danger
        loading={busy}
        onConfirm={async () => {
          const r = target;
          setTarget(null);
          if (!r) return;
          try {
            await forceExpire(r.id);
            snackbar.show(`Reservation ${r.id.slice(-8)} expired`);
          } catch (e) {
            snackbar.show((e as Error).message || 'Could not expire the hold');
          }
        }}
      />
    </>
  );
}
