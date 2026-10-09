'use client';

import { useState } from 'react';
import { Button, Dialog, useSnackbar } from '@pml.tickets/shared/components/m3';
import type { EventPageRow } from '@pml.tickets/shared';
import { clock, daysTo, fullDate, money } from '@/lib/format';

export function calendarHref(e: Pick<EventPageRow, 'title' | 'eventDateTime' | 'endDateTime' | 'locationName' | 'locationAddress'>): string {
  const z = (v: string) => new Date(v).toISOString().replace(/[-:]|\.\d{3}/g, '');
  return (
    'https://calendar.google.com/calendar/render?action=TEMPLATE&text=' +
    encodeURIComponent(e.title) +
    '&dates=' + z(e.eventDateTime) + '/' + z(e.endDateTime) +
    '&location=' + encodeURIComponent([e.locationName, e.locationAddress].filter(Boolean).join(', ')) +
    '&details=' + encodeURIComponent('Booked on Showstop')
  );
}

export function dayText(start: string, now: number): string {
  const d = daysTo(start, now);
  return d === 0 ? 'Starts today' : d === 1 ? 'Starts tomorrow' : `${d} days to go`;
}

export function priceRange(prices: number[]): string {
  if (!prices.length) return '—';
  const lo = Math.min(...prices);
  const hi = Math.max(...prices);
  return lo === hi ? money(lo) : `${money(lo)} – ${money(hi)}`;
}

/** Cover image with the category/status tags, title and date line, then the facts bar. */
export function EventHero({ event, prices, soldOut, fast, now, policyName }: { event: EventPageRow; prices: number[]; soldOut: boolean; fast: boolean; now: number; policyName: string }) {
  const [shareOpen, setShareOpen] = useState(false);
  const snack = useSnackbar();
  const url = typeof window === 'undefined' ? '' : `${window.location.origin}/events/${event.id}`;
  const msg = encodeURIComponent(`${event.title}, ${fullDate(event.eventDateTime)} at ${event.locationName ?? ''}: ${url}`);
  return (
    <>
      <div className="buyer-dhero">
        {event.bannerImageUrl ? <img src={event.bannerImageUrl} alt="" /> : null}
        <div className="buyer-dhero__body">
          <div className="buyer-tags">
            {event.category ? <span className="m3-hero__tag">{event.category.name}</span> : null}
            {soldOut ? <span className="m3-hero__tag" data-tone="low">Sold out</span> : fast ? <span className="m3-hero__tag" data-tone="low">Selling fast</span> : null}
            <span className="m3-hero__tag" data-tone="cd">{dayText(event.eventDateTime, now)}</span>
          </div>
          <h1 className="buyer-dhero__title">{event.title}</h1>
          <p>
            {fullDate(event.eventDateTime)} · {clock(event.eventDateTime)} · {[event.locationName, event.cityName].filter(Boolean).join(', ')}
          </p>
        </div>
      </div>
      <div className="buyer-facts">
      <dl className="buyer-facts__list">
        <div>
          <dt>Date</dt>
          <dd>{fullDate(event.eventDateTime)}</dd>
        </div>
        <div>
          <dt>{event.doorsOpenAt ? 'Doors / starts' : 'Starts'}</dt>
          <dd>{event.doorsOpenAt ? `${clock(event.doorsOpenAt)} / ${clock(event.eventDateTime)}` : clock(event.eventDateTime)}</dd>
        </div>
        <div>
          <dt>Venue</dt>
          <dd>
            {event.locationName}
            <small className="m3-muted"> {event.locationAddress}</small>
          </dd>
        </div>
        <div>
          <dt>Prices</dt>
          <dd className="m3-num">{priceRange(prices)}</dd>
        </div>
        <div>
          <dt>Refund policy</dt>
          <dd>{policyName}</dd>
        </div>
      </dl>
      <div className="buyer-facts__acts">
          <Button size="sm" onClick={() => setShareOpen(true)}>
            Share
          </Button>
          <a className="m3-btn m3-state" data-variant="outlined" data-size="sm" href={calendarHref(event)} target="_blank" rel="noopener noreferrer">
            Add to calendar
          </a>
        </div>
      </div>
      <Dialog open={shareOpen} onClose={() => setShareOpen(false)} title="Share this event" actions={<Button variant="text" onClick={() => setShareOpen(false)}>Close</Button>}>
        <p className="m3-muted">
          {event.title} · {fullDate(event.eventDateTime)}
        </p>
        <div className="m3-row">
          <Button
            onClick={() => {
              void navigator.clipboard?.writeText(url).catch(() => undefined);
              snack.show('Link copied');
              setShareOpen(false);
            }}
          >
            Copy link
          </Button>
          <a className="m3-btn m3-state" data-variant="outlined" href={`https://wa.me/?text=${msg}`} target="_blank" rel="noopener noreferrer">
            WhatsApp
          </a>
          <a className="m3-btn m3-state" data-variant="outlined" href={`sms:?&body=${msg}`}>
            SMS
          </a>
        </div>
      </Dialog>
    </>
  );
}
