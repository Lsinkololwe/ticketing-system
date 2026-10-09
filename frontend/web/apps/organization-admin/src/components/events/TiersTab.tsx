'use client';

import { useState } from 'react';
import { Button, Card, CardHeader, ConfirmDialog, DataTable, EmptyState, IconButton, StatusPill, type DataColumn } from '@pml.tickets/shared/components/m3';
import type { OrgTier, TierInput } from '@/lib/api/events';
import { formatCount, formatEventDate, formatMoney } from '@/lib/format/figure';
import { TierDialog } from './TierDialog';

export interface TiersTabProps {
  tiers: OrgTier[];
  canWrite: boolean;
  /** Platform commission as a percentage of the ticket price; omitted until known. */
  commissionPercent?: number | null;
  actions: {
    create: (i: TierInput) => Promise<unknown>;
    update: (id: string, i: Partial<TierInput>) => Promise<unknown>;
    remove: (id: string) => Promise<unknown>;
    setActive: (id: string, active: boolean) => Promise<unknown>;
    reorder: (ids: string[]) => Promise<unknown>;
  };
}

function tierState(t: OrgTier): { label: string; tone: 'success' | 'warning' | 'neutral' | 'error' } {
  if (!t.isActive) return { label: 'Inactive', tone: 'neutral' };
  if (t.availableQuantity <= 0) return { label: 'Sold out', tone: 'error' };
  if (t.salesStartAt && new Date(t.salesStartAt) > new Date()) return { label: 'Scheduled', tone: 'warning' };
  return { label: t.isHidden ? 'Hidden' : 'On sale', tone: 'success' };
}

export function TiersTab({ tiers, canWrite, actions, commissionPercent }: TiersTabProps) {
  const sorted = [...tiers].sort((a, b) => a.sortOrder - b.sortOrder);
  const [editing, setEditing] = useState<OrgTier | 'new' | null>(null);
  const [removing, setRemoving] = useState<OrgTier | null>(null);

  const move = (i: number, d: number) => {
    const ids = sorted.map((t) => t.id);
    const j = i + d;
    if (j < 0 || j >= ids.length) return;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    void actions.reorder(ids);
  };

  const columns: DataColumn<OrgTier>[] = [
    { id: 'name', header: 'Tier', rowHeader: true, cell: (t) => <><b>{t.name}</b>{t.description ? <><br /><span className="m3-muted">{t.description}</span></> : null}</> },
    {
      id: 'price',
      header: 'Price',
      cell: (t) => (
        <span className="m3-mono">
          {formatMoney(t.price, t.currency)}
          {t.earlyBirdPrice ? <><br /><span className="m3-muted">Early bird {formatMoney(t.earlyBirdPrice, t.currency)}{t.earlyBirdEndsAt ? ` until ${formatEventDate(t.earlyBirdEndsAt)}` : ''}</span></> : null}
        </span>
      ),
    },
    { id: 'sold', header: 'Sold / quantity', cell: (t) => `${formatCount(t.soldQuantity)} / ${formatCount(t.quantity)}` },
    { id: 'window', header: 'Sales window', cell: (t) => (t.salesStartAt || t.salesEndAt ? `${formatEventDate(t.salesStartAt) || 'Now'} to ${formatEventDate(t.salesEndAt) || 'event'}` : 'Until the event') },
    { id: 'state', header: 'Status', cell: (t) => { const s = tierState(t); return <StatusPill tone={s.tone}>{s.label}</StatusPill>; } },
  ];

  return (
    <>
      <Card>
        <CardHeader
          title="Ticket tiers"
          subtitle="Each tier has its own price, quantity and sales window."
          actions={canWrite ? <Button variant="tonal" icon="add" onClick={() => setEditing('new')}>Add tier</Button> : null}
        />
        <DataTable
          caption="Ticket tiers"
          columns={columns}
          rows={sorted}
          getRowId={(t) => t.id}
          empty={<EmptyState icon="ticket" title="No ticket tiers yet" description="Add a tier so buyers can purchase tickets." />}
          rowActions={
            canWrite
              ? (t) => {
                  const i = sorted.findIndex((x) => x.id === t.id);
                  return (
                    <span className="m3-row">
                      <IconButton icon="arrow-up" label={`Move ${t.name} up`} disabled={i === 0} onClick={() => move(i, -1)} />
                      <IconButton icon="arrow-down" label={`Move ${t.name} down`} disabled={i === sorted.length - 1} onClick={() => move(i, 1)} />
                      <Button size="sm" variant="tonal" onClick={() => setEditing(t)}>Edit</Button>
                      <Button size="sm" variant="text" onClick={() => void actions.setActive(t.id, !t.isActive)}>{t.isActive ? 'Deactivate' : 'Activate'}</Button>
                      <IconButton
                        icon="delete"
                        danger
                        label={t.soldQuantity > 0 ? `Delete ${t.name} (not possible, tickets sold)` : `Delete ${t.name}`}
                        disabled={t.soldQuantity > 0}
                        onClick={() => setRemoving(t)}
                      />
                    </span>
                  );
                }
              : undefined
          }
        />
      </Card>
      {commissionPercent != null && sorted.length ? (
        <Card>
          <CardHeader title="Commission preview" subtitle={`MyTicketZM takes ${commissionPercent}% of each ticket price. Buyers pay no service fee on top.`} />
          <DataTable
            caption="Commission preview"
            rows={sorted}
            getRowId={(t) => t.id}
            columns={[
              { id: 'tier', header: 'Tier', rowHeader: true, cell: (t) => t.name },
              { id: 'pays', header: 'Buyer pays', align: 'end', cell: (t) => <span className="m3-mono">{formatMoney(t.price, t.currency)}</span> },
              { id: 'fee', header: `Commission ${commissionPercent}%`, align: 'end', cell: (t) => <span className="m3-mono">{formatMoney((Number(t.price) * commissionPercent) / 100, t.currency)}</span> },
              { id: 'net', header: 'You receive', align: 'end', cell: (t) => <span className="m3-mono">{formatMoney(Number(t.price) - (Number(t.price) * commissionPercent) / 100, t.currency)}</span> },
            ]}
          />
        </Card>
      ) : null}
      {editing ? (
        <TierDialog
          tier={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSave={(input) => (editing === 'new' ? actions.create(input) : actions.update(editing.id, input))}
        />
      ) : null}
      <ConfirmDialog
        open={Boolean(removing)}
        onClose={() => setRemoving(null)}
        danger
        title={`Delete ${removing?.name ?? 'tier'}?`}
        description="The tier is removed. Tiers with sales cannot be deleted."
        confirmLabel="Delete tier"
        onConfirm={() => {
          if (removing) void actions.remove(removing.id);
          setRemoving(null);
        }}
      />
    </>
  );
}
