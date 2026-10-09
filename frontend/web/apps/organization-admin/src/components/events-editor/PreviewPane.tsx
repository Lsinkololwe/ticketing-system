'use client';

import { richView } from '@pml.tickets/shared';
import { useState } from 'react';
import { EventCard, HeroBanner, SegmentedButton, TierRow, Tabs, StatusPill, EmptyState } from '@pml.tickets/shared/components/m3';
import { formatMoney } from '@/lib/format/figure';
import { currentPrice, num, type EditorForm, type RefundPolicyOption } from './model';

type Screen = 'event' | 'card' | 'basket';
type Device = 'd' | 'm';

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

function visibleTiers(form: EditorForm) {
  return form.tiers.filter((t) => t.isActive && !t.isHidden);
}

/** What buyers will see. Read only: interactions inside are disabled via `inert`. */
export function PreviewPane({ form, categoryName, refundPolicies = [] }: { form: EditorForm; categoryName?: string; refundPolicies?: RefundPolicyOption[] }) {
  const [screen, setScreen] = useState<Screen>('event');
  const [device, setDevice] = useState<Device>('d');
  const start = form.start ? new Date(form.start) : null;
  const tiers = visibleTiers(form);
  const cheapest = tiers.length ? Math.min(...tiers.map((t) => currentPrice(t))) : null;
  const money = (v: number) => (v ? formatMoney(v, 'ZMW') : 'Free');
  const venueLine = [form.venue, form.city].filter(Boolean).join(', ');
  const hidden = form.tiers.filter((t) => t.isActive && t.isHidden).length;
  const policy = refundPolicies.find((p) => p.value === form.refundPolicy);

  return (
    <section aria-label="Buyer preview" className="m3-stack">
      <div className="oc-spread">
        <b>Buyer preview</b>
        <SegmentedButton
          label="Device"
          value={device}
          onChange={setDevice}
          options={[
            { value: 'd', label: 'Desktop' },
            { value: 'm', label: 'Phone' },
          ]}
        />
      </div>
      <Tabs
        label="Preview screen"
        value={screen}
        onChange={(v) => setScreen(v as Screen)}
        tabs={[
          { id: 'event', label: 'Event page' },
          { id: 'card', label: 'Listing card' },
          { id: 'basket', label: 'Basket' },
        ]}
      />
      <div className="oc-pv" data-device={device} inert>
        {screen === 'event' ? (
          <div className="m3-stack">
            <HeroBanner
              image={form.bannerImageUrl}
              tag={categoryName}
              title={form.title || 'Event title'}
              meta={[venueLine, start ? start.toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : 'Date to be set'].filter(Boolean).join(' · ')}
            />
            <div className="oc-rich-view" dangerouslySetInnerHTML={{ __html: richView(form.description) || '<p>The description appears here.</p>' }} />
            {tiers.length ? (
              tiers.map((t) => (
                <TierRow key={t.key} name={t.name || 'Untitled'} description={t.description} price={money(currentPrice(t))} available={Math.max(0, num(t.quantity) - t.sold)} quantity={0} onQuantityChange={() => undefined} />
              ))
            ) : (
              <p className="m3-muted">No visible ticket tiers yet.</p>
            )}
            {hidden ? <p className="m3-muted">{hidden} hidden tier{hidden > 1 ? 's' : ''} unlock with an access code.</p> : null}
            {policy ? (
              <p>
                <b>{policy.label} refunds.</b> {policy.summary}
              </p>
            ) : null}
          </div>
        ) : null}
        {screen === 'card' ? (
          <EventCard
            title={form.title || 'Event title'}
            href="#"
            image={form.bannerImageUrl}
            date={{ month: start ? (MONTHS[start.getMonth()] as string) : '---', day: start ? String(start.getDate()) : '--' }}
            category={[categoryName, form.city].filter(Boolean).join(' · ') || 'Category'}
            venue={`${form.venue || 'Venue'}${start ? ` · ${start.toTimeString().slice(0, 5)}` : ''}`}
            priceFrom={cheapest == null ? 'Not on sale' : `From ${money(cheapest)}`}
          />
        ) : null}
        {screen === 'basket' ? (
          tiers.length ? (
            <div className="m3-stack">
              <StatusPill tone="info">Tickets are held while the buyer pays</StatusPill>
              {tiers.map((t) => (
                <TierRow key={t.key} name={t.name || 'Untitled'} price={money(currentPrice(t))} available={Math.max(0, num(t.quantity) - t.sold)} quantity={1} onQuantityChange={() => undefined} maxPerOrder={num(t.maxPerOrder) || undefined} />
              ))}
            </div>
          ) : (
            <EmptyState icon="cart" title="No tickets to buy yet" />
          )
        ) : null}
      </div>
    </section>
  );
}
