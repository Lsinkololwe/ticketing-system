'use client';

import { useState } from 'react';
import { Button, Card, CardHeader, ConfirmDialog, DataTable, EmptyState, IconButton, Select, TextField, Toolbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import type { OrgTier } from '@/lib/api/events';
import type { PromoInput, PromoRow } from '@/lib/api/promos';
import { formatCount, formatEventDate, formatMoney } from '@/lib/format/figure';
import { Status } from '@/components/console/Status';
import { PromoDialog } from './PromoDialog';

export interface PromosTabProps {
  promos: PromoRow[];
  tiers: OrgTier[];
  canWrite: boolean;
  actions: {
    create: (i: PromoInput) => Promise<unknown>;
    update: (id: string, i: Omit<PromoInput, 'code'>) => Promise<unknown>;
    setActive: (id: string, active: boolean) => Promise<unknown>;
    remove: (id: string) => Promise<unknown>;
  };
}

export function PromosTab({ promos, tiers, canWrite, actions }: PromosTabProps) {
  const [q, setQ] = useState('');
  const [st, setSt] = useState<'all' | 'active' | 'inactive'>('all');
  const [editing, setEditing] = useState<PromoRow | 'new' | null>(null);
  const [removing, setRemoving] = useState<PromoRow | null>(null);
  const list = promos.filter((p) => (st === 'all' || (st === 'active') === p.isActive) && (!q || p.code.toLowerCase().includes(q.toLowerCase())));
  const tierName = (id: string) => tiers.find((t) => t.id === id)?.name ?? '?';

  const columns: DataColumn<PromoRow>[] = [
    { id: 'code', header: 'Code', rowHeader: true, cell: (p) => <b className="m3-mono">{p.code}</b> },
    {
      id: 'discount',
      header: 'Discount',
      cell: (p) => (p.discountType === 'PERCENTAGE' ? `${Number(p.discountValue)}%${p.maxDiscountAmount ? ` (up to ${formatMoney(p.maxDiscountAmount)})` : ''}` : formatMoney(p.discountValue)),
    },
    { id: 'uses', header: 'Uses', cell: (p) => `${formatCount(p.currentUses)} / ${p.maxUses ? formatCount(p.maxUses) : '∞'}` },
    { id: 'valid', header: 'Valid', cell: (p) => `${formatEventDate(p.validFrom) || 'Now'} to ${formatEventDate(p.validUntil) || 'no end'}` },
    { id: 'tiers', header: 'Tiers', cell: (p) => (p.applicableTiers?.length ? p.applicableTiers.map(tierName).join(', ') : 'All tiers') },
    { id: 'status', header: 'Status', cell: (p) => <Status status={p.isActive ? 'ACTIVE' : 'INACTIVE'} /> },
  ];

  return (
    <>
      <Card>
        <CardHeader
          title="Promo codes"
          subtitle="Buyers enter a code at checkout. Codes belong to this event."
          actions={canWrite ? <Button variant="tonal" icon="add" onClick={() => setEditing('new')}>New code</Button> : null}
        />
        <Toolbar label="Promo filters">
          <TextField density="compact" label="Search codes" value={q} onChange={(e) => setQ(e.target.value)} />
          <Select density="compact" label="Status" value={st} onChange={(e) => setSt(e.target.value as typeof st)}>
            <option value="all">All</option>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </Select>
        </Toolbar>
        <DataTable
          caption="Promo codes"
          columns={columns}
          rows={list}
          getRowId={(p) => p.id}
          empty={<EmptyState icon="tag" title="No promo codes match" description="Create one to offer discounts." />}
          rowActions={
            canWrite
              ? (p) => (
                  <span className="m3-row">
                    <Button size="sm" variant="tonal" onClick={() => setEditing(p)}>Edit</Button>
                    <Button size="sm" variant="text" onClick={() => void actions.setActive(p.id, !p.isActive)}>{p.isActive ? 'Disable' : 'Enable'}</Button>
                    <IconButton icon="delete" danger label={`Delete ${p.code}`} onClick={() => setRemoving(p)} />
                  </span>
                )
              : undefined
          }
        />
      </Card>
      {editing ? (
        <PromoDialog
          promo={editing === 'new' ? null : editing}
          tiers={tiers}
          existingCodes={promos.map((p) => p.code)}
          onClose={() => setEditing(null)}
          onSave={async (input, active) => {
            if (editing === 'new') {
              await actions.create(input);
              return;
            }
            const { code: _c, ...rest } = input;
            void _c;
            await actions.update(editing.id, rest);
            if (active !== editing.isActive) await actions.setActive(editing.id, active);
          }}
        />
      ) : null}
      <ConfirmDialog
        open={Boolean(removing)}
        onClose={() => setRemoving(null)}
        danger
        title={`Delete ${removing?.code ?? 'code'}?`}
        description="Buyers can no longer use this code."
        confirmLabel="Delete code"
        onConfirm={() => {
          if (removing) void actions.remove(removing.id);
          setRemoving(null);
        }}
      />
    </>
  );
}
