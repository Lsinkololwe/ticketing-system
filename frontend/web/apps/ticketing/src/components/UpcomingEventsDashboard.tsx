'use client';

import React, { useState, useEffect } from 'react';
import {
  Card,
  Box,
  Flex,
  Text,
  Heading,
  Button,
  Select,
  Spinner,
  Callout,
  Grid,
} from '@radix-ui/themes';
import { Event, EventStatus } from '@/types/event';
import { useMockEvents, useMockEventStats } from '@/hooks/useMocks';
import EventCard from '@components/EventCard';
import EventFilters from '@components/EventFilters';
import EventStats from '@components/EventStats';
import { Calendar, ViewColumns2, Refresh, WarningTriangle } from 'iconoir-react';

// Simple grid icon as SVG since iconoir doesn't have a suitable one
const GridIcon = ({ style }: { style?: React.CSSProperties }) => (
  <svg style={style} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
    />
  </svg>
);

const UpcomingEventsDashboard: React.FC = () => {
  // Use Apollo Client for GraphQL calls
  const {
    data: eventsData,
    loading: eventsLoading,
    error: eventsError,
    refetch: refetchEvents,
  } = useMockEvents();
  const { data: statsData, loading: statsLoading, error: statsError } = useMockEventStats();

  // Extract data from Apollo Client response
  const events = (eventsData as any)?.events || [];
  const stats = (statsData as any)?.eventStats || null;

  const [filteredEvents, setFilteredEvents] = useState<Event[]>([]);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [sortBy, setSortBy] = useState<string>('date');
  const [favorites, setFavorites] = useState<Set<string>>(new Set());

  const [filters, setFilters] = useState({
    search: '',
    category: '',
    status: '' as EventStatus | '',
    city: '',
    dateRange: '',
    priceRange: '',
  });

  // Filter and sort events
  useEffect(() => {
    if (!events || events.length === 0) {
      setFilteredEvents([]);
      return;
    }

    let filtered = [...events];

    // Apply filters
    if (filters.search) {
      filtered = filtered.filter(
        (event) =>
          event.title.toLowerCase().includes(filters.search.toLowerCase()) ||
          event.description.toLowerCase().includes(filters.search.toLowerCase()) ||
          event.organizerName.toLowerCase().includes(filters.search.toLowerCase())
      );
    }

    if (filters.category) {
      filtered = filtered.filter((event) => event.category === filters.category);
    }

    if (filters.status) {
      filtered = filtered.filter((event) => event.status === filters.status);
    }

    if (filters.city) {
      filtered = filtered.filter((event) => event.location.city === filters.city);
    }

    if (filters.dateRange) {
      const now = new Date();
      filtered = filtered.filter((event) => {
        const eventDate = new Date(event.eventDateTime);
        switch (filters.dateRange) {
          case 'today':
            return eventDate.toDateString() === now.toDateString();
          case 'tomorrow': {
            const tomorrow = new Date(now);
            tomorrow.setDate(tomorrow.getDate() + 1);
            return eventDate.toDateString() === tomorrow.toDateString();
          }
          case 'this-week': {
            const weekStart = new Date(now);
            weekStart.setDate(now.getDate() - now.getDay());
            const weekEnd = new Date(weekStart);
            weekEnd.setDate(weekStart.getDate() + 6);
            return eventDate >= weekStart && eventDate <= weekEnd;
          }
          default:
            return true;
        }
      });
    }

    // Apply sorting
    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'date':
          return new Date(a.eventDateTime).getTime() - new Date(b.eventDateTime).getTime();
        case 'title':
          return a.title.localeCompare(b.title);
        case 'price': {
          const aPrice = Math.min(...a.ticketCategories.map((cat: { price: number }) => cat.price));
          const bPrice = Math.min(...b.ticketCategories.map((cat: { price: number }) => cat.price));
          return aPrice - bPrice;
        }
        case 'popularity':
          return b.soldTickets - a.soldTickets;
        default:
          return 0;
      }
    });

    setFilteredEvents(filtered);
  }, [
    events,
    filters.search,
    filters.category,
    filters.status,
    filters.city,
    filters.dateRange,
    sortBy,
  ]);

  const handleFiltersChange = (newFilters: typeof filters) => {
    setFilters(newFilters);
  };

  const handleClearFilters = () => {
    setFilters({
      search: '',
      category: '',
      status: '' as EventStatus | '',
      city: '',
      dateRange: '',
      priceRange: '',
    });
  };

  const handleViewDetails = (event: Event) => {
    console.log('View details for event:', event.id);
  };

  const handleBookTicket = (event: Event) => {
    console.log('Book ticket for event:', event.id);
  };

  const handleToggleFavorite = (event: Event) => {
    const newFavorites = new Set(favorites);
    if (newFavorites.has(event.id)) {
      newFavorites.delete(event.id);
    } else {
      newFavorites.add(event.id);
    }
    setFavorites(newFavorites);
  };

  const handleRefresh = () => {
    refetchEvents();
  };

  const isLoading = eventsLoading || statsLoading;
  const error = eventsError || statsError;

  if (error) {
    return (
      <Box p="6">
        <Callout.Root color="red" mb="4">
          <Callout.Icon>
            <WarningTriangle style={{ width: '1.5rem', height: '1.5rem' }} />
          </Callout.Icon>
          <Callout.Text>
            {error instanceof Error ? error.message : 'An error occurred while loading events'}
          </Callout.Text>
        </Callout.Root>
      </Box>
    );
  }

  return (
    <Box
      style={{ minHeight: '100vh', backgroundColor: 'var(--gray-1)' }}
      p={{ initial: '4', md: '6', lg: '8' }}
    >
      <Box style={{ maxWidth: '1280px', margin: '0 auto' }}>
        {/* Header */}
        <Box mb="8">
          <Flex
            direction={{ initial: 'column', md: 'row' }}
            justify="between"
            align={{ initial: 'start', md: 'center' }}
            gap="4"
          >
            <Box>
              <Heading size="8" mb="2">
                Upcoming Events
              </Heading>
              <Text size="3" color="gray" style={{ maxWidth: '42rem' }}>
                Discover and book tickets for amazing events happening near you
              </Text>
            </Box>

            <Flex align="center" gap="3">
              <Button
                variant="outline"
                size="2"
                onClick={handleRefresh}
                disabled={isLoading}
              >
                <Refresh
                  style={{
                    width: '1rem',
                    height: '1rem',
                    animation: isLoading ? 'spin 1s linear infinite' : 'none',
                  }}
                />
                Refresh
              </Button>
            </Flex>
          </Flex>
        </Box>

        {/* Stats Section */}
        <Box mb="8">
          <EventStats stats={stats} isLoading={statsLoading} />
        </Box>

        {/* Filters */}
        <Box mb="6">
          <EventFilters
            filters={filters}
            onFiltersChange={handleFiltersChange}
            onClearFilters={handleClearFilters}
          />
        </Box>

        {/* Controls */}
        <Flex
          direction={{ initial: 'column', md: 'row' }}
          justify="between"
          align={{ initial: 'start', md: 'center' }}
          gap="4"
          mb="6"
        >
          <Flex align="center" gap="4">
            <Text size="2" color="gray">
              {filteredEvents.length} events found
            </Text>
          </Flex>

          <Flex align="center" gap="4">
            {/* Sort By */}
            <Flex align="center" gap="2">
              <Text size="2" color="gray">
                Sort by:
              </Text>
              <Select.Root value={sortBy} onValueChange={(value) => setSortBy(value)}>
                <Select.Trigger style={{ width: '140px' }} />
                <Select.Content>
                  <Select.Item value="date">Date</Select.Item>
                  <Select.Item value="title">Title</Select.Item>
                  <Select.Item value="price">Price</Select.Item>
                  <Select.Item value="popularity">Popularity</Select.Item>
                </Select.Content>
              </Select.Root>
            </Flex>

            {/* View Mode */}
            <Flex align="center" gap="1">
              <Button
                variant={viewMode === 'grid' ? 'solid' : 'outline'}
                size="1"
                onClick={() => setViewMode('grid')}
              >
                <GridIcon style={{ width: '1rem', height: '1rem' }} />
              </Button>
              <Button
                variant={viewMode === 'list' ? 'solid' : 'outline'}
                size="1"
                onClick={() => setViewMode('list')}
              >
                <ViewColumns2 style={{ width: '1rem', height: '1rem' }} />
              </Button>
            </Flex>
          </Flex>
        </Flex>

        {/* Events Grid/List */}
        {isLoading ? (
          <Flex justify="center" align="center" py="9">
            <Spinner size="3" />
          </Flex>
        ) : filteredEvents.length === 0 ? (
          <Card size="3">
            <Flex direction="column" align="center" justify="center" py="9">
              <Calendar
                style={{
                  width: '4rem',
                  height: '4rem',
                  color: 'var(--gray-8)',
                  marginBottom: '1rem',
                }}
              />
              <Heading size="4" mb="2">
                No events found
              </Heading>
              <Text size="2" color="gray">
                Try adjusting your filters or search terms
              </Text>
            </Flex>
          </Card>
        ) : (
          <Grid
            columns={viewMode === 'grid' ? { initial: '1', md: '2', lg: '3' } : '1'}
            gap="6"
          >
            {filteredEvents.map((event) => (
              <EventCard
                key={event.id}
                event={event}
                onViewDetails={handleViewDetails}
                onBookTicket={handleBookTicket}
                onToggleFavorite={handleToggleFavorite}
                isFavorite={favorites.has(event.id)}
              />
            ))}
          </Grid>
        )}
      </Box>
    </Box>
  );
};

export default UpcomingEventsDashboard;
