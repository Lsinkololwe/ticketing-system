'use client';

import React, { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Select, Spinner, Grid } from '@radix-ui/themes';
import { usePublishedEvents, type Event } from '@pml.tickets/shared';
import EventCard from '@components/EventCard';
import { Calendar, Refresh } from 'iconoir-react';
import { EmptyState, Toast } from '@/components/ui';

type SortKey = 'date' | 'title' | 'price' | 'popularity';

const SORT_LABELS: Record<SortKey, string> = {
  date: 'Soonest first',
  title: 'Name (A–Z)',
  price: 'Cheapest first',
  popularity: 'Most booked',
};

/**
 * Upcoming events panel.
 *
 * This renders INSIDE the home page's "Upcoming" tab, so it is a content
 * panel — not a page. It owns no page chrome, no heading hierarchy of its own
 * beyond a section title, and no second filter sidebar: the home page's
 * filters already scope what the customer is looking at.
 */
const UpcomingEventsDashboard: React.FC = () => {
  const router = useRouter();
  const {
    events,
    loading,
    error,
    refetch: refetchEvents,
  } = usePublishedEvents({ size: 20 });

  const [sortBy, setSortBy] = useState<SortKey>('date');
  const [favorites, setFavorites] = useState<Set<string>>(new Set());

  const upcoming = useMemo(() => {
    const now = Date.now();
    const future = (events ?? []).filter(
      (event: Event) => new Date(event.eventDateTime).getTime() >= now
    );

    return future.sort((a: Event, b: Event) => {
      switch (sortBy) {
        case 'title':
          return a.title.localeCompare(b.title);
        case 'price':
          return Number(a.minTicketPrice ?? 0) - Number(b.minTicketPrice ?? 0);
        case 'popularity':
          return b.soldTickets - a.soldTickets;
        case 'date':
        default:
          return (
            new Date(a.eventDateTime).getTime() - new Date(b.eventDateTime).getTime()
          );
      }
    });
  }, [events, sortBy]);

  const toggleFavorite = (event: Event) =>
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(event.id)) next.delete(event.id);
      else next.add(event.id);
      return next;
    });

  if (error) {
    return (
      <Toast
        variant="error"
        title="Upcoming events didn't load"
        description="Check your connection and refresh the page to try again."
      />
    );
  }

  return (
    <Box>
      <Flex
        direction={{ initial: 'column', md: 'row' }}
        justify="between"
        align={{ initial: 'start', md: 'end' }}
        gap="4"
        mb="6"
      >
        <Box>
          <Heading size="5" mb="2" className="font-display">
            Upcoming
          </Heading>
          <Text size="2" color="gray">
            {upcoming.length} {upcoming.length === 1 ? 'event' : 'events'} still to come
          </Text>
        </Box>

        <Flex align="center" gap="3">
          <Text as="label" size="1" className="ds-label">
            Sort by
          </Text>
          <Select.Root value={sortBy} onValueChange={(v) => setSortBy(v as SortKey)}>
            <Select.Trigger style={{ minWidth: 160 }} />
            <Select.Content>
              {Object.entries(SORT_LABELS).map(([value, label]) => (
                <Select.Item key={value} value={value}>
                  {label}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
        </Flex>
      </Flex>

      {loading ? (
        <Flex justify="center" align="center" py="9">
          <Spinner size="3" />
        </Flex>
      ) : upcoming.length === 0 ? (
        <EmptyState
          icon={<Calendar width={24} height={24} />}
          title="Nothing coming up"
          description="New events go live all the time — check back shortly."
          action={
            <Flex
              align="center"
              gap="2"
              onClick={() => refetchEvents()}
              style={{ cursor: 'pointer', color: 'var(--accent-11)' }}
            >
              <Refresh width={16} height={16} />
              <Text size="2" weight="medium">
                Refresh
              </Text>
            </Flex>
          }
        />
      ) : (
        <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
          {upcoming.map((event: Event) => (
            <EventCard
              key={event.id}
              event={event}
              onViewDetails={(e) => router.push(`/events/${e.id}`)}
              onBookTicket={(e) => router.push(`/events/${e.id}/book`)}
              onToggleFavorite={toggleFavorite}
              isFavorite={favorites.has(event.id)}
            />
          ))}
        </Grid>
      )}
    </Box>
  );
};

export default UpcomingEventsDashboard;
