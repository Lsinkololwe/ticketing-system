'use client';

import { useState } from 'react';
import { Card, CardHeader, KeyValue, QrPlaceholder, SegmentedButton, StatusPill } from '@pml.tickets/shared/components/m3';
import type { AdminEventDetail } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { formatDateTime, money } from '@/lib/format';

type View = 'card' | 'event' | 'cart' | 'ticket';

/** Compact buyer-side preview built only from the event's own data. */
export function BuyerPreview({ event }: { event: AdminEventDetail }) {
  const [view, setView] = useState<View>('card');
  const tiers = (event.ticketTiers ?? []).filter((t) => t.isActive && !t.isHidden);
  const tier = tiers.find((t) => t.quantity > t.soldQuantity) ?? tiers[0];
  const place = [event.locationName, event.cityName].filter(Boolean).join(', ');

  return (
    <aside aria-label="Buyer preview" className="m3-stack">
      <Card>
        <CardHeader title="Buyer preview" subtitle={`What buyers see${event.status === 'PUBLISHED' ? ' now' : ' once it is live'}.`} />
        <SegmentedButton<View>
          label="Preview screen"
          value={view}
          onChange={setView}
          options={[
            { value: 'card', label: 'Listing card' },
            { value: 'event', label: 'Event page' },
            { value: 'cart', label: 'Basket' },
            { value: 'ticket', label: 'E-ticket' },
          ]}
        />
        <div data-testid={`preview-${view}`}>
          {view === 'card' ? (
            <KeyValue
              items={[
                { label: 'Title', value: event.title },
                { label: 'Category', value: event.category?.name ?? '-' },
                { label: 'When', value: formatDateTime(event.eventDateTime) },
                { label: 'Where', value: place || '-' },
                { label: 'From', value: event.minTicketPrice != null ? money(event.minTicketPrice) : '-' },
              ]}
            />
          ) : null}
          {view === 'event' ? (
            <div className="m3-stack">
              <h3>{event.title}</h3>
              <p>{event.description}</p>
              <KeyValue items={[{ label: 'When', value: formatDateTime(event.eventDateTime) }, { label: 'Where', value: place || '-' }]} />
              {tiers.length === 0 ? <p className="m3-muted">No ticket tiers on sale yet.</p> : tiers.map((t) => <div key={t.id}>{t.name}: {money(t.price)}</div>)}
            </div>
          ) : null}
          {view === 'cart' ? (
            tier ? (
              <KeyValue items={[{ label: `2 x ${tier.name}`, value: money(Number(tier.price) * 2) }, { label: 'Booking fees', value: 'Calculated at checkout' }]} />
            ) : (
              <p className="m3-muted">Add a ticket tier to see a basket.</p>
            )
          ) : null}
          {view === 'ticket' ? (
            <div className="m3-stack">
              <div className="m3-row">
                <StatusPill>{event.category?.name ?? 'Event'}</StatusPill>
                <span>Admit one</span>
              </div>
              <h3>{event.title}</h3>
              <KeyValue items={[{ label: 'Tier', value: tier?.name ?? '-' }, { label: 'Price', value: tier ? money(tier.price) : '-' }, { label: 'Venue', value: place || '-' }]} />
              <QrPlaceholder value={event.id} label="Sample QR code" />
            </div>
          ) : null}
        </div>
      </Card>
    </aside>
  );
}
