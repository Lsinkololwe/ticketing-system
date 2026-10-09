'use client';

import { useEffect, useState } from 'react';
import { LinkBtn } from '@/components/LinkBtn';
import type { EventCardRow } from '@pml.tickets/shared';
import { fullDate, money } from '@/lib/format';
import { isSellingFast } from '@/components/events/EventCardView';

const ROTATE_MS = 6500;

/** Featured-events carousel: one slide visible, auto-advancing unless reduced motion or hovered/focused. */
export function FeaturedCarousel({ events }: { events: EventCardRow[] }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const n = events.length;

  useEffect(() => {
    if (n < 2 || paused) return;
    if (typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const t = window.setInterval(() => setIndex((i) => (i + 1) % n), ROTATE_MS);
    return () => window.clearInterval(t);
  }, [n, paused]);

  if (!n) return null;
  const go = (i: number) => setIndex((i + n) % n);

  return (
    <div
      className="buyer-car"
      role="region"
      aria-roledescription="carousel"
      aria-label="Featured events"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      {events.map((e, i) => {
        const on = i === index;
        const tag = i === 0 ? 'Featured' : e.soldOut ? 'Sold out' : isSellingFast(e) ? 'Selling fast' : 'Top pick';
        return (
          <div key={e.id} className="buyer-slide" data-on={on ? 'true' : undefined} aria-hidden={!on} {...(on ? {} : { inert: true })}>
            {e.bannerImageUrl ? <img src={e.bannerImageUrl} alt="" /> : null}
            <div className="m3-site-wrap buyer-slide__content">
              <span className="m3-hero__tag">
                {tag} · {e.category?.name ?? 'Event'}
              </span>
              <h1 className="m3-hero__title">{e.title}</h1>
              <p className="m3-hero__meta">
                {fullDate(e.eventDateTime)} · {[e.locationName, e.cityName].filter(Boolean).join(', ')}
              </p>
              <div className="m3-row">
                <LinkBtn href={`/events/${e.id}`} variant="accent">
                  {e.soldOut ? 'See event' : `Get tickets from ${money(e.minTicketPrice)}`}
                </LinkBtn>
                <LinkBtn href={`/events/${e.id}`} variant="ghost-inverse">
                  Event details
                </LinkBtn>
              </div>
            </div>
          </div>
        );
      })}
      {n > 1 ? (
        <>
          <button type="button" className="buyer-car__arrow" data-side="l" aria-label="Previous slide" onClick={() => go(index - 1)}>
            ‹
          </button>
          <button type="button" className="buyer-car__arrow" data-side="r" aria-label="Next slide" onClick={() => go(index + 1)}>
            ›
          </button>
          <div className="buyer-car__dots">
            {events.map((e, i) => (
              <button key={e.id} type="button" className="buyer-car__dot" aria-label={`Go to slide ${i + 1}`} aria-current={i === index} onClick={() => go(i)} />
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}
