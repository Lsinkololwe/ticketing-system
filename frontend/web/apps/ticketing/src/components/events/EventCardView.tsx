'use client';

import Link from 'next/link';
import { EventCard } from '@pml.tickets/shared/components/m3';
import type { EventCardRow } from '@pml.tickets/shared';
import { clock, dayOfMonth, daysTo, money, monthShort, shortDate } from '@/lib/format';

/** Transparent pixel for events without artwork, so the card keeps its 3:2 media area. */
export const BLANK_IMAGE = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';

/** True when 10% or fewer of the tickets are left. */
export function isSellingFast(e: Pick<EventCardRow, 'availableTickets' | 'totalCapacity' | 'soldOut'>): boolean {
  return !e.soldOut && e.totalCapacity > 0 && e.availableTickets > 0 && e.availableTickets <= e.totalCapacity * 0.1;
}

export function soonText(start: string, now = Date.now()): { text: string; soon: boolean } {
  const d = daysTo(start, now);
  if (d <= 30) return { text: d === 0 ? 'Today' : d === 1 ? 'Tomorrow' : `In ${d} days`, soon: true };
  return { text: shortDate(start), soon: false };
}

/** Maps a catalog event to the shared storefront card. The whole card is one link to the event page. */
export function EventCardView({ event, now }: { event: EventCardRow; now?: number }) {
  const sold = event.soldOut || event.availableTickets <= 0;
  const price = event.minTicketPrice;
  return (
    <div role="listitem">
      <EventCard
        linkAs={Link}
        href={`/events/${event.id}`}
        title={event.title}
        image={event.bannerImageUrl ?? BLANK_IMAGE}
        date={{ month: monthShort(event.eventDateTime), day: dayOfMonth(event.eventDateTime) }}
        category={[event.category?.name, event.cityName].filter(Boolean).join(' · ')}
        venue={[event.locationName, clock(event.eventDateTime)].filter(Boolean).join(' · ')}
        priceFrom={sold || price === null || price === undefined ? undefined : money(price)}
        note={sold ? 'No tickets left' : soonText(event.eventDateTime, now).text}
        ribbon={sold ? 'Sold out' : isSellingFast(event) ? 'Selling fast' : undefined}
      />
    </div>
  );
}
