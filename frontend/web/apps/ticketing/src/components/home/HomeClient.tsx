'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useActiveEventCategories, useCitiesWithEvents } from '@pml.tickets/shared';
import { CtaBanner } from '@pml.tickets/shared/components/m3';
import { useDiscoverEvents, useMyBookings, useRecommendedEvents, useTrendingEvents } from '@pml.tickets/shared';
import { useBuyerAuth } from '@/lib/auth/session-context';
import { useDebounced } from '@/lib/useDebounced';
import { useRecentlyViewed } from '@/lib/recent';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';
import { DiscoverSearch } from './DiscoverSearch';
import { EventsSection } from './EventsSection';
import { FeaturedCarousel } from './FeaturedCarousel';
import { BecauseRail, CardRail, TrendingRail } from './Rails';
import { CategoryTiles } from './CategoryTiles';
import { EMPTY_FILTERS, SORT_TO_API, filterSchema, toDiscoverFilter, whenRange, type FilterState } from './filters';
import { useZodForm } from '@pml.tickets/shared/forms';

const PAGE = 6;

/** Public home: featured carousel, search and filters, trending, upcoming events grid, categories and banners. */
export function HomeClient() {
  const params = useSearchParams();
  const { categories } = useActiveEventCategories();
  const { cities } = useCitiesWithEvents();
  const form = useZodForm(filterSchema, { defaultValues: EMPTY_FILTERS });
  const filters = form.watch() as FilterState;
  const [moreOpen, setMoreOpen] = useState(false);
  const recent = useRecentlyViewed();

  // ?category=Music from the footer and category tiles: resolve the name to its id once categories load.
  const wanted = params?.get('category') ?? '';
  useEffect(() => {
    if (!wanted || !categories.length) return;
    const hit = categories.find((c) => c.name.toLowerCase() === wanted.toLowerCase());
    if (hit && form.getValues('categoryId') !== hit.id) form.reset({ ...EMPTY_FILTERS, categoryId: hit.id });
  }, [wanted, categories, form]);

  const debounced = useDebounced(filters, 300);
  const filter = useMemo(() => toDiscoverFilter(debounced), [debounced]);
  const grid = useDiscoverEvents(filter, PAGE, false, SORT_TO_API[debounced.sort]);
  // The feed has no `featured` filter (ET-CAT-003 R4 admits five, each indexed): take the most
  // popular page and keep the events an administrator marked featured.
  const popular = useDiscoverEvents({}, 24, false, 'POPULAR');
  const featured = { events: popular.events.filter((e) => e.featured).slice(0, 5) };
  const trending = useTrendingEvents(9);
  const auth = useBuyerAuth();
  const mine = useMyBookings(50, !auth.authenticated);
  const heldEvents = useMemo(
    () => [...new Map(mine.bookings.filter((b) => b.tickets.length > 0).map((b) => [b.eventId, b.eventTitle ?? ''])).entries()],
    [mine.bookings]
  );
  const because = useRecommendedEvents(heldEvents.map(([id]) => id), 4, !auth.authenticated || heldEvents.length === 0);

  const patch = useCallback(
    (p: Partial<FilterState>) => {
      (Object.keys(p) as Array<keyof FilterState>).forEach((k) => form.setValue(k, p[k] as never, { shouldDirty: true }));
      if (p.from !== undefined || p.to !== undefined) form.setValue('when', p.from || p.to ? 'custom' : '');
    },
    [form]
  );
  const onWhen = useCallback(
    (when: FilterState['when']) => {
      const r = whenRange(when, form.getValues('from'), form.getValues('to'));
      form.setValue('from', r.from);
      form.setValue('to', r.to);
      if (when === 'custom') setMoreOpen(true);
    },
    [form]
  );
  const clear = useCallback(() => form.reset(EMPTY_FILTERS), [form]);
  const toEvents = useCallback(() => document.getElementById('events')?.scrollIntoView({ behavior: 'smooth' }), []);

  const cats = useMemo(() => categories.map((c) => ({ id: c.id, name: c.name, imageUrl: c.imageUrl })), [categories]);

  return (
    <SiteShell>
      <FeaturedCarousel events={featured.events} />
      <div className="m3-site-wrap" data-hero={featured.events.length > 0 ? 'true' : 'false'}>
        <DiscoverSearch
          form={form as never}
          onWhen={onWhen}
          onSearch={toEvents}
          onClear={clear}
          cities={cities}
          categories={cats}
          moreOpen={moreOpen}
          onToggleMore={() => setMoreOpen((v) => !v)}
        />
        <TrendingRail events={trending.events} />
        <BecauseRail items={because.items} titles={new Map(heldEvents)} />
        <CardRail id="recent" title="Recently viewed" events={recent.slice(0, 4)} />
        <EventsSection
          filters={filters}
          onChange={patch}
          onClear={clear}
          categories={cats}
          events={grid.events}
          total={grid.total}
          hasNext={grid.hasNext}
          loading={grid.loading}
          error={grid.error ?? null}
          onRetry={() => void grid.refetch()}
          onLoadMore={() => void grid.loadMore()}
        />
        <CategoryTiles categories={cats} onPick={(id) => { form.reset({ ...EMPTY_FILTERS, categoryId: id }); toEvents(); }} />
        <section className="m3-site-section">
          <CtaBanner
            title="Buy online, pay on your phone"
            action={
              <button type="button" className="m3-btn m3-state" data-variant="filled" onClick={toEvents}>
                Find an event
              </button>
            }
          >
            No cards and no queues at the gate. Reserve your tickets, approve the prompt with MTN, Airtel or Zamtel mobile money, and your QR tickets arrive by SMS straight away.
          </CtaBanner>
        </section>
        {process.env.NEXT_PUBLIC_ORGANIZER_URL ? (
          <section className="m3-site-section">
            <CtaBanner
              title="Selling tickets for your own event?"
              action={<LinkBtn href={process.env.NEXT_PUBLIC_ORGANIZER_URL} variant="filled">Become an organizer</LinkBtn>}
            >
              Apply as an organizer to create events, set ticket tiers and receive payouts.
            </CtaBanner>
          </section>
        ) : null}
      </div>
    </SiteShell>
  );
}
