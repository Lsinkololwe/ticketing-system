'use client';

import { Button, Chip, ChipGroup, EmptyState, ErrorState, Select, SectionHeader, Skeleton } from '@pml.tickets/shared/components/m3';
import { EventGrid } from '@pml.tickets/shared/components/m3';
import type { GraphQLLikeError } from '@pml.tickets/shared';
import type { EventCardRow } from '@pml.tickets/shared';
import { EventCardView } from '@/components/events/EventCardView';
import { SORT_OPTIONS, activeCount, type FilterState } from './filters';

export interface EventsSectionProps {
  filters: FilterState;
  onChange: (patch: Partial<FilterState>) => void;
  onClear: () => void;
  categories: Array<{ id: string; name: string }>;
  events: EventCardRow[];
  total: number;
  hasNext: boolean;
  loading: boolean;
  error: GraphQLLikeError | null;
  onRetry: () => void;
  onLoadMore: () => void;
}

function Loading() {
  return (
    <div className="m3-site-grid" aria-busy="true" aria-label="Loading events">
      {Array.from({ length: 6 }, (_, i) => (
        <Skeleton key={i} shape="block" width="100%" />
      ))}
    </div>
  );
}

/** "Upcoming events": category chips, sort, the card grid and Load more, with designed loading/empty/error states. */
export function EventsSection(p: EventsSectionProps) {
  const n = activeCount(p.filters);
  const first = p.loading && p.events.length === 0;
  return (
    <section className="m3-site-section" id="events" aria-labelledby="events-title">
      <SectionHeader
        eyebrow="Discover"
        title="Upcoming events"
        actions={
          <>
            <span className="m3-muted" aria-live="polite">
              {n ? `${n} filter${n > 1 ? 's' : ''} active` : ''}
            </span>
            <Button variant="text" onClick={p.onClear}>
              Clear filters
            </Button>
            <Select
              label="Sort"
              wrapperClassName="buyer-sort"
              value={p.filters.sort}
              onChange={(e) => p.onChange({ sort: e.target.value as FilterState['sort'] })}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </Select>
          </>
        }
      />
      <ChipGroup label="Category" className="buyer-chiprow">
        <Chip kind="filter" selected={p.filters.categoryId === ''} onClick={() => p.onChange({ categoryId: '' })}>
          All
        </Chip>
        {p.categories.map((c) => (
          <Chip key={c.id} kind="filter" selected={p.filters.categoryId === c.id} onClick={() => p.onChange({ categoryId: c.id })}>
            {c.name}
          </Chip>
        ))}
      </ChipGroup>
      {first ? (
        <Loading />
      ) : p.error && p.events.length === 0 ? (
        <ErrorState error={p.error} onRetry={p.onRetry} />
      ) : p.events.length === 0 ? (
        <EmptyState
          icon="ticket"
          title="No events match your filters"
          description="Try a different city or date range, or clear your filters to see all upcoming events."
          action={<Button variant="filled" onClick={p.onClear}>Clear filters</Button>}
        />
      ) : (
        <>
          <EventGrid label="Upcoming events">
            {p.events.map((e) => (
              <EventCardView key={e.id} event={e} />
            ))}
          </EventGrid>
          <div className="buyer-more">
            <span className="m3-muted" aria-live="polite">
              Showing {p.events.length} of {Math.max(p.total, p.events.length)} event{p.total === 1 ? '' : 's'}
            </span>
            {p.error ? <span role="alert">We could not load more events. Check your connection and try again.</span> : null}
            {p.hasNext ? (
              <Button loading={p.loading} onClick={p.onLoadMore}>
                {p.loading ? 'Loading…' : p.error ? 'Try again' : 'Load more'}
              </Button>
            ) : null}
          </div>
        </>
      )}
    </section>
  );
}
