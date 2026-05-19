'use client';

import React, { useState } from 'react';
import { useQuery } from '@apollo/client/react';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  TextField,
  Spinner,
  Callout,
  Card,
  Grid,
  Tabs,
  Container,
  Section,
} from '@radix-ui/themes';
import { Heart, Search, WarningTriangle } from 'iconoir-react';
import { GET_EVENTS, GET_EVENT_STATS } from '@pml.tickets/shared';
import { Event, EventStatus, EventFilter } from '@/types/event';
import NavbarComponent from '@components/Navbar';
import EventCard from '@components/EventCard';
import EventStats from '@components/EventStats';
import UpcomingEventsDashboard from '@components/UpcomingEventsDashboard';

const HomePage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [activeTab, setActiveTab] = useState('all');

  // Event filter
  const eventFilter: EventFilter = {
    published: true,
    status: EventStatus.PUBLISHED,
    limit: 12,
    offset: 0,
  };

  // Fetch events
  const {
    data: eventsData,
    loading: eventsLoading,
    error: eventsError,
  } = useQuery(GET_EVENTS, {
    variables: { filter: eventFilter },
    fetchPolicy: 'cache-and-network',
  });

  // Fetch event stats
  const { data: statsData } = useQuery(GET_EVENT_STATS);

  const events = (eventsData as any)?.events || [];
  const stats = (statsData as any)?.eventStats;

  // Filter events based on search
  const filteredEvents = events.filter((event: Event) => {
    const matchesSearch =
      !searchQuery ||
      event.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      event.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      event.location.city.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesSearch;
  });

  // Handle favorite toggle
  const toggleFavorite = (eventId: string) => {
    setFavorites((prev) => {
      const newFavorites = new Set(prev);
      if (newFavorites.has(eventId)) {
        newFavorites.delete(eventId);
      } else {
        newFavorites.add(eventId);
      }
      return newFavorites;
    });
  };

  // Handle event view
  const handleViewEvent = (event: Event) => {
    window.location.href = `/events/${event.id}`;
  };

  // Handle ticket booking
  const handleBookTicket = (event: Event) => {
    window.location.href = `/events/${event.id}/book`;
  };

  return (
    <Box style={{ minHeight: '100vh', backgroundColor: 'var(--gray-2)' }}>
      <NavbarComponent />

      {/* Hero Section */}
      <Section
        size="3"
        style={{
          background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
          color: 'white',
          paddingTop: '5rem',
          paddingBottom: '5rem',
        }}
      >
        <Container size="3">
          <Flex direction="column" align="center" style={{ textAlign: 'center' }}>
            <Heading
              size="9"
              mb="4"
              style={{ color: 'white', maxWidth: '800px' }}
            >
              Discover Amazing Events
            </Heading>
            <Text
              size="5"
              mb="6"
              style={{ opacity: 0.9, maxWidth: '600px' }}
            >
              From concerts to conferences, find and book tickets for the best events in Zambia
            </Text>

            {/* Search Bar */}
            <Box style={{ maxWidth: '640px', width: '100%' }}>
              <Flex gap="3" direction={{ initial: 'column', sm: 'row' }}>
                <Box style={{ flex: 1 }}>
                  <TextField.Root
                    placeholder="Search events, venues, or organizers..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    size="3"
                  >
                    <TextField.Slot>
                      <Search style={{ width: '1rem', height: '1rem' }} />
                    </TextField.Slot>
                  </TextField.Root>
                </Box>
                <Button size="3">Search</Button>
              </Flex>
            </Box>
          </Flex>
        </Container>
      </Section>

      {/* Stats Section */}
      {stats && (
        <Section size="2" style={{ backgroundColor: 'white' }}>
          <Container size="4">
            <EventStats stats={stats} />
          </Container>
        </Section>
      )}

      {/* Main Content */}
      <Section size="3">
        <Container size="4">
          <Flex gap="8" direction={{ initial: 'column', lg: 'row' }}>
            {/* Sidebar Filters */}
            <Box style={{ width: '100%', maxWidth: '280px' }} className="hidden lg:block">
              <Card size="2">
                <Box p="4">
                  <Text size="4" weight="bold" mb="4" style={{ display: 'block' }}>
                    Filters
                  </Text>
                  <Text size="2" color="gray">
                    Filter options will be available here
                  </Text>
                </Box>
              </Card>
            </Box>

            {/* Events Grid */}
            <Box style={{ flex: 1 }}>
              <Tabs.Root value={activeTab} onValueChange={setActiveTab}>
                <Tabs.List mb="6">
                  <Tabs.Trigger value="all">All Events</Tabs.Trigger>
                  <Tabs.Trigger value="upcoming">Upcoming</Tabs.Trigger>
                  <Tabs.Trigger value="popular">Popular</Tabs.Trigger>
                  <Tabs.Trigger value="favorites">My Favorites</Tabs.Trigger>
                </Tabs.List>

                <Tabs.Content value="all">
                  <Box mb="6">
                    <Heading size="5" mb="2">
                      All Events
                    </Heading>
                    <Text size="2" color="gray">
                      {filteredEvents.length} events found
                    </Text>
                  </Box>

                  {eventsLoading ? (
                    <Flex justify="center" py="9">
                      <Spinner size="3" />
                    </Flex>
                  ) : eventsError ? (
                    <Callout.Root color="red" mb="4">
                      <Callout.Icon>
                        <WarningTriangle />
                      </Callout.Icon>
                      <Callout.Text>
                        Error loading events: {eventsError.message}
                      </Callout.Text>
                    </Callout.Root>
                  ) : filteredEvents.length === 0 ? (
                    <Flex
                      direction="column"
                      align="center"
                      justify="center"
                      py="9"
                    >
                      <Heading size="4" mb="2">
                        No events found
                      </Heading>
                      <Text size="2" color="gray">
                        Try adjusting your search criteria
                      </Text>
                    </Flex>
                  ) : (
                    <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
                      {filteredEvents.map((event: Event) => (
                        <EventCard
                          key={event.id}
                          event={event}
                          onViewDetails={handleViewEvent}
                          onBookTicket={handleBookTicket}
                          onToggleFavorite={(event) => toggleFavorite(event.id)}
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
                    <Heading size="5" mb="2">
                      Popular Events
                    </Heading>
                    <Text size="2" color="gray">
                      Most booked events this month
                    </Text>
                  </Box>

                  <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
                    {filteredEvents
                      .sort((a: Event, b: Event) => b.soldTickets - a.soldTickets)
                      .slice(0, 6)
                      .map((event: Event) => (
                        <EventCard
                          key={event.id}
                          event={event}
                          onViewDetails={handleViewEvent}
                          onBookTicket={handleBookTicket}
                          onToggleFavorite={(event) => toggleFavorite(event.id)}
                          isFavorite={favorites.has(event.id)}
                        />
                      ))}
                  </Grid>
                </Tabs.Content>

                <Tabs.Content value="favorites">
                  <Box mb="6">
                    <Heading size="5" mb="2">
                      My Favorites
                    </Heading>
                    <Text size="2" color="gray">
                      {favorites.size} favorite events
                    </Text>
                  </Box>

                  {favorites.size === 0 ? (
                    <Flex
                      direction="column"
                      align="center"
                      justify="center"
                      py="9"
                    >
                      <Heart
                        style={{
                          width: '4rem',
                          height: '4rem',
                          color: 'var(--gray-6)',
                          marginBottom: '1rem',
                        }}
                      />
                      <Heading size="4" mb="2">
                        No favorite events yet
                      </Heading>
                      <Text size="2" color="gray">
                        Start exploring events and add them to your favorites
                      </Text>
                    </Flex>
                  ) : (
                    <Grid columns={{ initial: '1', md: '2', xl: '3' }} gap="6">
                      {filteredEvents
                        .filter((event: Event) => favorites.has(event.id))
                        .map((event: Event) => (
                          <EventCard
                            key={event.id}
                            event={event}
                            onViewDetails={handleViewEvent}
                            onBookTicket={handleBookTicket}
                            onToggleFavorite={(event) => toggleFavorite(event.id)}
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
      <Box
        style={{ backgroundColor: 'var(--gray-12)', color: 'white' }}
        py="9"
      >
        <Container size="4">
          <Grid columns={{ initial: '1', md: '4' }} gap="8">
            <Box>
              <Text size="4" weight="bold" mb="4" style={{ display: 'block', color: 'white' }}>
                Event Ticketing
              </Text>
              <Text size="2" style={{ color: 'var(--gray-8)' }}>
                Your gateway to amazing events in Zambia. Discover, book, and enjoy the best
                events.
              </Text>
            </Box>

            <Box>
              <Text size="4" weight="bold" mb="4" style={{ display: 'block', color: 'white' }}>
                Quick Links
              </Text>
              <Flex direction="column" gap="2">
                <a href="/events" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">All Events</Text>
                </a>
                <a href="/categories" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Categories</Text>
                </a>
                <a href="/venues" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Venues</Text>
                </a>
              </Flex>
            </Box>

            <Box>
              <Text size="4" weight="bold" mb="4" style={{ display: 'block', color: 'white' }}>
                Support
              </Text>
              <Flex direction="column" gap="2">
                <a href="/help" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Help Center</Text>
                </a>
                <a href="/contact" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Contact Us</Text>
                </a>
                <a href="/faq" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">FAQ</Text>
                </a>
              </Flex>
            </Box>

            <Box>
              <Text size="4" weight="bold" mb="4" style={{ display: 'block', color: 'white' }}>
                Connect
              </Text>
              <Flex direction="column" gap="2">
                <a href="/facebook" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Facebook</Text>
                </a>
                <a href="/twitter" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Twitter</Text>
                </a>
                <a href="/instagram" style={{ color: 'var(--gray-8)', textDecoration: 'none' }}>
                  <Text size="2">Instagram</Text>
                </a>
              </Flex>
            </Box>
          </Grid>

          <Box
            mt="8"
            pt="8"
            style={{
              borderTop: '1px solid var(--gray-10)',
              textAlign: 'center',
            }}
          >
            <Text size="2" style={{ color: 'var(--gray-8)' }}>
              © 2024 Event Ticketing. All rights reserved.
            </Text>
          </Box>
        </Container>
      </Box>
    </Box>
  );
};

export default HomePage;
