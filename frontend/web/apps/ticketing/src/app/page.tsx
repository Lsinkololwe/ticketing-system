'use client';

import React, { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  TextField,
  Spinner,
  Grid,
  Tabs,
  Container,
  Section,
} from '@radix-ui/themes';
import { Heart, Search } from 'iconoir-react';
import {
  usePublishedEvents,
  useCitiesWithEvents,
  type Event,
  type EventStatus,
} from '@pml.tickets/shared';
import NavbarComponent from '@components/Navbar';
import EventCard from '@components/EventCard';
import EventFilters from '@components/EventFilters';
import UpcomingEventsDashboard from '@components/UpcomingEventsDashboard';
import { BrandMark, EmptyState, MobileMoneyStrip, StatTile, Toast } from '@/components/ui';
import { Calendar, City as CityIcon, SmartphoneDevice } from 'iconoir-react';

interface EventFilterState {
  search: string;
  category: string;
  status: EventStatus | '';
  city: string;
  dateRange: string;
  priceRange: string;
}

const EMPTY_FILTERS: EventFilterState = {
  search: '',
  category: '',
  status: '',
  city: '',
  dateRange: '',
  priceRange: '',
};

const PRICE_BUCKETS: Record<string, (price: number) => boolean> = {
  free: (p) => p === 0,
  '0-100': (p) => p >= 0 && p <= 100,
  '100-250': (p) => p > 100 && p <= 250,
  '250-500': (p) => p > 250 && p <= 500,
  '500-1000': (p) => p > 500 && p <= 1000,
  '1000+': (p) => p > 1000,
};

function matchesDateRange(eventDate: Date, range: string): boolean {
  const now = new Date();
  const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  switch (range) {
    case 'today':
      return sameDay(eventDate, now);
    case 'tomorrow': {
      const t = new Date(now);
      t.setDate(t.getDate() + 1);
      return sameDay(eventDate, t);
    }
    case 'this-week': {
      const start = new Date(now);
      start.setDate(now.getDate() - now.getDay());
      const end = new Date(start);
      end.setDate(start.getDate() + 6);
      return eventDate >= start && eventDate <= end;
    }
    case 'this-month':
      return (
        eventDate.getMonth() === now.getMonth() &&
        eventDate.getFullYear() === now.getFullYear()
      );
    default:
      return true;
  }
}

const HomePage: React.FC = () => {
  const router = useRouter();
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [activeTab, setActiveTab] = useState('all');
  const [filters, setFilters] = useState<EventFilterState>(EMPTY_FILTERS);

  // Public browsing via shared hooks (cursor queries — no auth required).
  const { events, pageInfo, loading: eventsLoading, error: eventsError } = usePublishedEvents({
    size: 12,
  });
  const { cities } = useCitiesWithEvents();

  const filteredEvents = useMemo(() => {
    const q = filters.search.toLowerCase();
    return events.filter((event: Event) => {
      if (
        q &&
        !event.title.toLowerCase().includes(q) &&
        !event.description.toLowerCase().includes(q) &&
        !(event.cityName?.toLowerCase().includes(q) ?? false)
      ) {
        return false;
      }
      if (filters.category && event.category?.name !== filters.category) return false;
      if (filters.status && event.status !== filters.status) return false;
      if (filters.city && event.cityName !== filters.city) return false;
      if (filters.dateRange && !matchesDateRange(new Date(event.eventDateTime), filters.dateRange)) {
        return false;
      }
      if (filters.priceRange) {
        const bucket = PRICE_BUCKETS[filters.priceRange];
        if (bucket && !bucket(Number(event.minTicketPrice ?? 0))) return false;
      }
      return true;
    });
  }, [events, filters]);

  const toggleFavorite = (eventId: string) => {
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(eventId)) next.delete(eventId);
      else next.add(eventId);
      return next;
    });
  };

  const handleViewEvent = (event: Event) => router.push(`/events/${event.id}`);
  const handleBookTicket = (event: Event) => router.push(`/events/${event.id}/book`);
  const setSearch = (search: string) => setFilters((f) => ({ ...f, search }));
  const iconStyle = { width: '1.25rem', height: '1.25rem' } as const;

  return (
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
      <NavbarComponent />

      {/* Hero — a solid accent field, not a gradient wash. The design system
          sanctions gradients only for small accent chips and the org-admin
          marketing hero, so identity here is carried by flat iris + the
          display face. */}
      <Section
        size="3"
        style={{
          background: 'var(--accent-9)',
          color: 'var(--accent-contrast)',
          paddingTop: '5rem',
          paddingBottom: '5rem',
        }}
      >
        <Container size="3">
          <Flex direction="column" align="center" gap="5" style={{ textAlign: 'center' }}>
            <Heading
              size="9"
              className="font-display"
              style={{ color: 'inherit', maxWidth: '800px', textWrap: 'balance' }}
            >
              Every event in Zambia, one tap away
            </Heading>
            <Text size="5" style={{ opacity: 0.92, maxWidth: '600px' }}>
              Concerts, sport, comedy, culture — find your next night out and pay
              the way you already do, with mobile money.
            </Text>

            {/* Search Bar */}
            <Box style={{ maxWidth: '640px', width: '100%' }}>
              <Flex gap="3" direction={{ initial: 'column', sm: 'row' }}>
                <Box style={{ flex: 1 }}>
                  <TextField.Root
                    placeholder="Search events, venues, or organizers..."
                    value={filters.search}
                    onChange={(e) => setSearch(e.target.value)}
                    size="3"
                    data-testid="hero-search"
                  >
                    <TextField.Slot>
                      <Search style={{ width: '1rem', height: '1rem' }} />
                    </TextField.Slot>
                  </TextField.Root>
                </Box>
                <Button
                  size="3"
                  variant="solid"
                  color="gray"
                  highContrast
                  data-testid="hero-search-submit"
                >
                  Search
                </Button>
              </Flex>
            </Box>

            {/* Always-visible mobile money */}
            <MobileMoneyStrip label="Pay with" inverse />
          </Flex>
        </Container>
      </Section>

      {/* Public highlights strip (no admin analytics — derived public figures) */}
      <Section size="2" style={{ backgroundColor: 'var(--color-surface)' }}>
        <Container size="4">
          <Grid columns={{ initial: '1', sm: '3' }} gap="4">
            <StatTile
              label="Events live"
              value={<span className="ds-amount">{pageInfo.totalElements || filteredEvents.length}</span>}
              hint="Published across Zambia"
              icon={<Calendar style={iconStyle} />}
              accent="brand"
            />
            <StatTile
              label="Cities covered"
              value={<span className="ds-amount">{cities.length}</span>}
              hint="And growing"
              icon={<CityIcon style={iconStyle} />}
              accent="info"
            />
            <StatTile
              label="Ways to pay"
              value="MTN · Airtel · Zamtel"
              hint="Mobile money, no card needed"
              icon={<SmartphoneDevice style={iconStyle} />}
              accent="money"
            />
          </Grid>
        </Container>
      </Section>

      {/* Main Content */}
      <Section size="3">
        <Container size="4">
          <Flex gap="8" direction={{ initial: 'column', lg: 'row' }}>
            {/* Sidebar Filters */}
            <Box style={{ width: '100%', maxWidth: '320px' }} className="hidden lg:block">
              <Box style={{ position: 'sticky', top: 'var(--space-5)' }}>
                <EventFilters
                  filters={filters}
                  onFiltersChange={(next) => setFilters(next as EventFilterState)}
                  onClearFilters={() => setFilters(EMPTY_FILTERS)}
                />
              </Box>
            </Box>

            {/* Events Grid */}
            <Box style={{ flex: 1 }}>
              <Tabs.Root value={activeTab} onValueChange={setActiveTab}>
                <Tabs.List mb="6">
                  <Tabs.Trigger value="all">All events</Tabs.Trigger>
                  <Tabs.Trigger value="upcoming">Upcoming</Tabs.Trigger>
                  <Tabs.Trigger value="popular">Popular</Tabs.Trigger>
                  <Tabs.Trigger value="favorites">Favorites</Tabs.Trigger>
                </Tabs.List>

                <Tabs.Content value="all">
                  <Box mb="6">
                    <Heading size="5" mb="2" className="font-display">
                      All events
                    </Heading>
                    <Text size="2" color="gray">
                      {filteredEvents.length}{' '}
                      {filteredEvents.length === 1 ? 'event' : 'events'}
                    </Text>
                  </Box>

                  {eventsLoading ? (
                    <Flex justify="center" py="9">
                      <Spinner size="3" />
                    </Flex>
                  ) : eventsError ? (
                    <Box mb="4">
                      <Toast
                        variant="error"
                        title="Events didn't load"
                        description="Check your connection and refresh the page to try again."
                      />
                    </Box>
                  ) : filteredEvents.length === 0 ? (
                    <EmptyState
                      icon={<Search width={24} height={24} />}
                      title="No events match that"
                      description="Try a broader search, or clear a filter or two to see more of what's on."
                    />
                  ) : (
                    <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
                      {filteredEvents.map((event: Event) => (
                        <EventCard
                          key={event.id}
                          event={event}
                          onViewDetails={handleViewEvent}
                          onBookTicket={handleBookTicket}
                          onToggleFavorite={(e) => toggleFavorite(e.id)}
                          isFavorite={favorites.has(event.id)}
                        />
                      ))}
                    </Grid>
                  )}
                </Tabs.Content>

                <Tabs.Content value="upcoming">
                  <UpcomingEventsDashboard />
                </Tabs.Content>

                <Tabs.Content value="popular">
                  <Box mb="6">
                    <Heading size="5" mb="2" className="font-display">
                      Popular events
                    </Heading>
                    <Text size="2" color="gray">
                      Most booked this month
                    </Text>
                  </Box>

                  <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
                    {[...filteredEvents]
                      .sort((a: Event, b: Event) => b.soldTickets - a.soldTickets)
                      .slice(0, 6)
                      .map((event: Event) => (
                        <EventCard
                          key={event.id}
                          event={event}
                          onViewDetails={handleViewEvent}
                          onBookTicket={handleBookTicket}
                          onToggleFavorite={(e) => toggleFavorite(e.id)}
                          isFavorite={favorites.has(event.id)}
                        />
                      ))}
                  </Grid>
                </Tabs.Content>

                <Tabs.Content value="favorites">
                  <Box mb="6">
                    <Heading size="5" mb="2" className="font-display">
                      Favorites
                    </Heading>
                    <Text size="2" color="gray">
                      {favorites.size} saved {favorites.size === 1 ? 'event' : 'events'}
                    </Text>
                  </Box>

                  {favorites.size === 0 ? (
                    <EmptyState
                      icon={<Heart width={24} height={24} />}
                      title="Nothing saved yet"
                      description="Tap the heart on any event to keep it here while you decide."
                    />
                  ) : (
                    <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
                      {events
                        .filter((event: Event) => favorites.has(event.id))
                        .map((event: Event) => (
                          <EventCard
                            key={event.id}
                            event={event}
                            onViewDetails={handleViewEvent}
                            onBookTicket={handleBookTicket}
                            onToggleFavorite={(e) => toggleFavorite(e.id)}
                            isFavorite={true}
                          />
                        ))}
                    </Grid>
                  )}
                </Tabs.Content>
              </Tabs.Root>
            </Box>
          </Flex>
        </Container>
      </Section>

      {/* Footer */}
      {/* Footer — a normal surface with a hairline top edge. An inverted dark
          block would need hand-picked colors that break in dark mode. */}
      <Box
        style={{
          backgroundColor: 'var(--color-panel-solid)',
          borderTop: 'var(--hairline)',
        }}
        py="9"
      >
        <Container size="4">
          <Grid columns={{ initial: '1', md: '4' }} gap="8">
            <Box>
              <Box mb="4">
                <BrandMark size="5" />
              </Box>
              <Text as="p" size="2" color="gray">
                Built for how Zambians actually buy — browse what&apos;s on, book in
                seconds, pay with mobile money.
              </Text>
            </Box>

            <Box>
              <Text as="div" size="1" className="ds-label" mb="4">
                Browse
              </Text>
              <Flex direction="column" gap="2">
                <a href="/" style={{ color: 'var(--gray-11)', textDecoration: 'none' }}>
                  <Text size="2">All events</Text>
                </a>
                <a href="/my-tickets" style={{ color: 'var(--gray-11)', textDecoration: 'none' }}>
                  <Text size="2">My tickets</Text>
                </a>
              </Flex>
            </Box>

            <Box>
              <Text as="div" size="1" className="ds-label" mb="4">
                Support
              </Text>
              <Flex direction="column" gap="2">
                <a href="/help" style={{ color: 'var(--gray-11)', textDecoration: 'none' }}>
                  <Text size="2">Help centre</Text>
                </a>
                <a href="/contact" style={{ color: 'var(--gray-11)', textDecoration: 'none' }}>
                  <Text size="2">Contact us</Text>
                </a>
              </Flex>
            </Box>

            <Box>
              <Text as="div" size="1" className="ds-label" mb="4">
                Pay with
              </Text>
              <MobileMoneyStrip label="" />
            </Box>
          </Grid>

          <Box mt="8" pt="8" style={{ borderTop: 'var(--hairline)', textAlign: 'center' }}>
            <Text size="2" color="gray">
              © 2026 MyTicketZM. All rights reserved.
            </Text>
          </Box>
        </Container>
      </Box>
    </Box>
  );
};

export default HomePage;
