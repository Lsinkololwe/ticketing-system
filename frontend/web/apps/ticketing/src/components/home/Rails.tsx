'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { IconButton, SectionHeader } from '@pml.tickets/shared/components/m3';
import type { EventCardRow, RecommendedEvent } from '@pml.tickets/shared';
import { money, shortDate } from '@/lib/format';
import { BLANK_IMAGE, EventCardView, isSellingFast } from '@/components/events/EventCardView';

/** A horizontally scrolling rail with previous/next buttons that disable at the ends. */
function Rail({ label, children }: { label: string; children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [state, setState] = useState({ prev: false, next: false });
  const update = useCallback(() => {
    const r = ref.current;
    if (!r) return;
    const over = r.scrollWidth > r.clientWidth + 4;
    setState({ prev: over && r.scrollLeft > 2, next: over && r.scrollLeft + r.clientWidth < r.scrollWidth - 2 });
  }, []);
  useEffect(() => {
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, [update, children]);
  const scroll = (d: number) => {
    const r = ref.current;
    if (!r) return;
    r.scrollBy({ left: d * Math.max(r.clientWidth * 0.8, 1), behavior: 'smooth' });
  };
  return (
    <div className="buyer-railwrap">
      <div className="buyer-railbtns">
        <IconButton icon="chevron-left" label={`Scroll ${label} left`} variant="outlined" disabled={!state.prev} onClick={() => scroll(-1)} />
        <IconButton icon="chevron-right" label={`Scroll ${label} right`} variant="outlined" disabled={!state.next} onClick={() => scroll(1)} />
      </div>
      <div ref={ref} className="buyer-rail" role="list" aria-label={label} onScroll={update}>
        {children}
      </div>
    </div>
  );
}

/** Top events ranked by tickets sold. */
export function TrendingRail({ events }: { events: EventCardRow[] }) {
  if (!events.length) return null;
  return (
    <section className="m3-site-section" id="trending" aria-labelledby="trending-title">
      <SectionHeader eyebrow="Right now" title="Trending now" description="Ranked by tickets sold so far." />
      <Rail label="Trending events, ranked">
        {events.map((e, i) => (
          <div role="listitem" className="buyer-rail__item" key={e.id}>
            <Link className="buyer-tcard" href={`/events/${e.id}`} aria-label={`Number ${i + 1} trending: ${e.title}, ${shortDate(e.eventDateTime)}`}>
              <span className="buyer-tcard__img">
                <img src={e.bannerImageUrl ?? BLANK_IMAGE} alt="" loading="lazy" />
                <span className="buyer-tcard__rank" aria-hidden="true">
                  {i + 1}
                </span>
              </span>
              <span className="buyer-tcard__body">
                <b>{e.title}</b>
                <span className="m3-muted">
                  {shortDate(e.eventDateTime)} · {e.cityName}
                </span>
                <span className="m3-muted">
                  From <b className="m3-num">{money(e.minTicketPrice)}</b>
                </span>
                <span className="m3-muted">{e.soldTickets.toLocaleString('en-US')} tickets sold</span>
                {isSellingFast(e) ? <span className="buyer-low">Selling fast</span> : null}
              </span>
            </Link>
          </div>
        ))}
      </Rail>
    </section>
  );
}

/** "Recently viewed" or any plain row of event cards. */
export function CardRail({ id, title, description, events }: { id: string; title: string; description?: string; events: EventCardRow[] }) {
  if (!events.length) return null;
  return (
    <section className="m3-site-section" id={id} aria-labelledby={`${id}-title`}>
      <SectionHeader title={title} description={description} />
      <Rail label={title}>
        {events.map((e) => (
          <div className="buyer-rail__item" key={e.id}>
            <EventCardView event={e} />
          </div>
        ))}
      </Rail>
    </section>
  );
}

/** "Because you booked X": events in the categories of events the buyer holds tickets for. The trending fallback is not shown here. */
export function BecauseRail({ items, titles }: { items: RecommendedEvent[]; titles: ReadonlyMap<string, string> }) {
  const picked = items.filter((i) => i.reason === 'BECAUSE_YOU_BOOKED');
  if (!picked.length) return null;
  const basis = titles.get(picked[0].basedOnEventId ?? '') ?? '';
  return (
    <CardRail
      id="because"
      title={basis ? `Because you booked ${basis}` : 'Because you booked'}
      description="More events you might enjoy."
      events={picked.map((i) => i.event)}
    />
  );
}
