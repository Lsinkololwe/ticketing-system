'use client';

import { useEffect } from 'react';
import { useFieldArray, useFormContext } from 'react-hook-form';
import { ChipsRHF, MoneyRHF, SelectRHF, SwitchRHF, TextAreaRHF, TextFieldRHF } from '@pml.tickets/shared';
import {
  Banner,
  Button,
  Card,
  CardHeader,
  DataTable,
  ExpansionItem,
  FormCell,
  FormGrid,
  IconButton,
  Menu,
  StatusPill,
} from '@pml.tickets/shared/components/m3';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { formatMoney } from '@/lib/format/figure';
import {
  commissionFor,
  currentPrice,
  duplicateTier,
  newTier,
  kwacha,
  num,
  type TierDraft,
} from './model';
import { useEditorValues } from './useEditorValues';

/** Starting points only: names, descriptions and perks. Prices are the organizer's to set. */
export const TIER_PRESETS: Array<{ id: string; label: string; hint: string; tier: Partial<TierDraft> }> = [
  { id: 'general', label: 'General', hint: 'Standard entry', tier: { name: 'General', description: 'Standard entry to the event.' } },
  { id: 'early', label: 'Early bird', hint: 'Limited, lower price', tier: { name: 'Early bird', description: 'Limited release at a lower price.', benefits: ['Standard entry'] } },
  { id: 'student', label: 'Student', hint: 'ID checked at the gate', tier: { name: 'Student', description: 'Valid student ID at the gate.', benefits: ['Student ID required'] } },
  { id: 'vip', label: 'VIP', hint: 'Lounge and fast track', tier: { name: 'VIP', description: 'Lounge access, fast-track entry and a welcome drink.', benefits: ['Fast-track entry', 'Lounge access', 'Welcome drink'] } },
  { id: 'vvip', label: 'VVIP', hint: 'Front of stage and hosting', tier: { name: 'VVIP', description: 'The best view with hosted hospitality.', benefits: ['Front-of-stage area', 'Complimentary drinks', 'Meet and greet'] } },
  { id: 'free', label: 'Free', hint: 'K 0 RSVP ticket', tier: { name: 'Free', description: 'Free ticket, limited places.', price: 0 } },
  { id: 'blank', label: 'Blank tier', hint: 'Start from scratch', tier: {} },
];

export function netLine(t: TierDraft, percent: number | undefined): string {
  const price = currentPrice(t);
  if (!price) return 'Free ticket: nothing to collect and no commission.';
  if (percent == null) return 'Not available yet: the commission rate could not be loaded.';
  const { fee, net } = commissionFor(price, percent);
  const m = (v: number) => formatMoney(v, 'ZMW', { decimals: 2 });
  return `Buyer pays ${m(price)} per ticket · commission ${percent}% ${m(fee)} · you receive ${m(net)}. No buyer service fee.`;
}

export function TiersTab({
  commissionPercent,
  maxPerOrder,
}: {
  commissionPercent?: number;
  maxPerOrder?: number;
}) {
  const values = useEditorValues();
  const { control } = useFormContext();
  const rows = useFieldArray({ control, name: 'tiers', keyName: 'rowKey' });
  const tiers = values.tiers ?? [];
  const active = tiers.filter((t) => t.isActive);
  const total = active.reduce((a, t) => a + num(t.quantity), 0);
  const cap = num(values.capacity);
  const m = (v: number) => formatMoney(v, 'ZMW', { decimals: 2 });
  const categories = useReferenceOptions('TICKET_TIER_CATEGORY');
  // A new tier starts in the platform's first category; there is no built-in default.
  const firstCategory = categories.options[0]?.value ?? '';
  const { setValue, getValues } = useFormContext();
  useEffect(() => {
    if (!firstCategory) return;
    ((getValues('tiers') ?? []) as TierDraft[]).forEach((t, i) => {
      if (!t.category) setValue(`tiers.${i}.category`, firstCategory, { shouldDirty: false });
    });
  }, [firstCategory, tiers.length, getValues, setValue]);
  const add = (preset: Partial<TierDraft>) => rows.append(newTier({ category: firstCategory, ...preset }) as never);

  return (
    <div className="m3-stack">
      <Card>
        <CardHeader
          title="Ticket tiers"
          subtitle={`${tiers.length} tier${tiers.length === 1 ? '' : 's'}, shown to buyers in this order. Active tiers admit ${total}${cap ? ` of ${cap} capacity` : ''}.`}
          actions={
            <Menu
              label="Add tier presets"
              align="end"
              trigger={(p) => (
                <Button {...p} variant="tonal" icon="add">
                  Add tier
                </Button>
              )}
              items={TIER_PRESETS.map((p) => ({ id: p.id, label: p.label, hint: p.hint, onSelect: () => add(p.tier) }))}
            />
          }
        />
        {tiers.length === 0 ? (
          <Banner tone="info">
            No tiers yet. Choose Add tier to start from a preset such as General, Early bird or VIP. At least one active tier avoids an approval blocker.
          </Banner>
        ) : null}
        <div className="m3-stack">
          {rows.fields.map((row, i) => {
            const t = tiers[i];
            if (!t) return null;
            const p = (f: string) => `tiers.${i}.${f}`;
            return (
              <ExpansionItem
                key={(row as unknown as { rowKey: string }).rowKey}
                defaultOpen={i === 0}
                title={t.name || 'Untitled tier'}
                subtitle={`${kwacha(t.price) ? m(kwacha(t.price)) : 'Free'} · ${t.sold} of ${num(t.quantity)} sold`}
                trailing={
                  <span className="oc-tier-actions">
                    {t.isHidden ? <StatusPill>Hidden</StatusPill> : null}
                    <StatusPill status={t.isActive ? 'ACTIVE' : 'INACTIVE'}>{t.isActive ? 'Active' : 'Inactive'}</StatusPill>
                  </span>
                }
              >
                <FormGrid>
                  <FormCell span={6}>
                    <TextFieldRHF name={p('name')} label="Tier name" required maxLength={40} />
                  </FormCell>
                  <FormCell span={3}>
                    <SelectRHF name={p('category')} label="Category" disabled={categories.loading} options={categories.options.map((c) => ({ value: c.value, label: c.label }))} helperText={!categories.loading && categories.empty ? 'Not available yet: the tier categories could not be loaded' : undefined} />
                  </FormCell>
                  <FormCell span={3}>
                    <MoneyRHF name={p('price')} label="Price" />
                  </FormCell>
                  <FormCell span={3}>
                    <TextFieldRHF name={p('quantity')} label="Quantity" inputMode="numeric" helperText={t.sold ? `${t.sold} already sold` : undefined} />
                  </FormCell>
                  <FormCell span={12}>
                    <TextAreaRHF name={p('description')} label="Description" rows={2} maxLength={140} />
                  </FormCell>
                  <FormCell span={12}>
                    <ChipsRHF name={p('benefits')} label="Perks and inclusions" />
                  </FormCell>
                  <FormCell span={3}>
                    <MoneyRHF name={p('earlyBirdPrice')} label="Early-bird price" helperText="Optional" />
                  </FormCell>
                  <FormCell span={3}>
                    <TextFieldRHF name={p('earlyBirdEndsAt')} label="Early-bird ends" type="datetime-local" />
                  </FormCell>
                  <FormCell span={3}>
                    <TextFieldRHF name={p('minPerOrder')} label="Minimum per order" inputMode="numeric" />
                  </FormCell>
                  <FormCell span={3}>
                    <TextFieldRHF name={p('maxPerOrder')} label="Maximum per order" inputMode="numeric" helperText={maxPerOrder ? `Platform limit: ${maxPerOrder}` : undefined} />
                  </FormCell>
                  <FormCell span={6}>
                    <TextFieldRHF name={p('salesStartAt')} label="Sales start" type="datetime-local" />
                  </FormCell>
                  <FormCell span={6}>
                    <TextFieldRHF name={p('salesEndAt')} label="Sales end" type="datetime-local" />
                  </FormCell>
                </FormGrid>
                <SwitchRHF name={p('isHidden')} label="Hidden tier" hint="Only people with the access code can see it." />
                {t.isHidden ? (
                  <FormGrid>
                    <FormCell span={6}>
                      <TextFieldRHF name={p('accessCode')} label="Access code" helperText="At least 4 characters." />
                    </FormCell>
                  </FormGrid>
                ) : null}
                <SwitchRHF name={p('isActive')} label="Active" hint="Inactive tiers cannot be bought. Tickets already sold stay valid." />
                <Banner tone="info">{netLine(t, commissionPercent)}</Banner>
                <div className="m3-row">
                  <IconButton icon="arrow-up" label={`Move ${t.name || 'tier'} up`} disabled={i === 0} onClick={() => rows.swap(i, i - 1)} />
                  <IconButton icon="arrow-down" label={`Move ${t.name || 'tier'} down`} disabled={i === tiers.length - 1} onClick={() => rows.swap(i, i + 1)} />
                  <IconButton icon="copy" label={`Duplicate ${t.name || 'tier'}`} onClick={() => rows.append(duplicateTier(t) as never)} />
                  <IconButton icon="delete" danger label={`Delete ${t.name || 'tier'}`} disabled={t.sold > 0} onClick={() => rows.remove(i)} />
                  {t.sold > 0 ? <span className="m3-muted">Tiers with sales cannot be deleted. Deactivate it instead.</span> : null}
                </div>
              </ExpansionItem>
            );
          })}
        </div>
      </Card>
      <Card>
        <CardHeader title="Commission preview" subtitle={commissionPercent == null ? 'Not available yet: the commission rate could not be loaded.' : `MyTicketZM takes ${commissionPercent}% of each ticket price. Buyers pay no service fee on top.`} />
        <DataTable
          caption="Commission preview"
          rows={tiers}
          getRowId={(t) => t.key}
          empty="Add a tier to see what you would earn per ticket."
          columns={[
            { id: 'tier', header: 'Tier', rowHeader: true, cell: (t) => t.name || 'Untitled' },
            { id: 'pays', header: 'Buyer pays', align: 'end', cell: (t) => m(currentPrice(t)) },
            { id: 'fee', header: commissionPercent == null ? 'Commission' : `Commission ${commissionPercent}%`, align: 'end', cell: (t) => (commissionPercent == null ? '—' : m(commissionFor(currentPrice(t), commissionPercent).fee)) },
            { id: 'net', header: 'You receive', align: 'end', cell: (t) => (commissionPercent == null ? '—' : m(commissionFor(currentPrice(t), commissionPercent).net)) },
          ]}
        />
      </Card>
    </div>
  );
}
