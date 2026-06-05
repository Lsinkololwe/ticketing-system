'use client';

/**
 * Events List Page
 *
 * Displays all events for the organization with:
 * - Status tabs (All, Published, Draft, Ended)
 * - Search and filters
 * - Grid/List view toggle
 * - Quick actions
 */

import { useState, useMemo } from 'react';
import { Box, Flex, Text, TextField, Button, Badge, Card, Tabs, DropdownMenu } from '@radix-ui/themes';
import {
  Plus,
  Search,
  Calendar,
  GridPlus,
  List,
  MoreHoriz,
  Edit,
  Copy,
  Trash,
  Eye,
  Group,
  CreditCard,
} from 'iconoir-react';
import { useRouter } from 'next/navigation';
import { PageHeader, NoEventsEmptyState } from '@/components/ui';
import { useOrganization } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// TYPES
// =============================================================================

type EventStatus = 'DRAFT' | 'PUBLISHED' | 'ENDED' | 'CANCELLED';
type ViewMode = 'grid' | 'list';

interface Event {
  id: string;
  title: string;
  slug: string;
  coverImageUrl?: string;
  startDate: string;
  endDate: string;
  location: string;
  status: EventStatus;
  ticketsSold: number;
  ticketsTotal: number;
  revenue: number;
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockEvents: Event[] = [
  {
    id: '1',
    title: 'Tech Summit Zambia 2025',
    slug: 'tech-summit-zambia-2025',
    coverImageUrl: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800',
    startDate: '2025-12-15T09:00:00Z',
    endDate: '2025-12-15T18:00:00Z',
    location: 'Mulungushi Conference Center, Lusaka',
    status: 'PUBLISHED',
    ticketsSold: 450,
    ticketsTotal: 500,
    revenue: 45000,
  },
  {
    id: '2',
    title: 'Lusaka Music Festival',
    slug: 'lusaka-music-festival',
    coverImageUrl: 'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800',
    startDate: '2025-12-20T14:00:00Z',
    endDate: '2025-12-21T02:00:00Z',
    location: 'National Heroes Stadium',
    status: 'PUBLISHED',
    ticketsSold: 1200,
    ticketsTotal: 5000,
    revenue: 180000,
  },
  {
    id: '3',
    title: 'Startup Pitch Night',
    slug: 'startup-pitch-night',
    startDate: '2025-12-28T18:00:00Z',
    endDate: '2025-12-28T21:00:00Z',
    location: 'BongoHive, Lusaka',
    status: 'DRAFT',
    ticketsSold: 0,
    ticketsTotal: 100,
    revenue: 0,
  },
  {
    id: '4',
    title: 'Corporate Workshop Series',
    slug: 'corporate-workshop-series',
    startDate: '2025-11-10T09:00:00Z',
    endDate: '2025-11-10T17:00:00Z',
    location: 'Radisson Blu Hotel',
    status: 'ENDED',
    ticketsSold: 75,
    ticketsTotal: 80,
    revenue: 22500,
  },
];

// =============================================================================
// EVENT CARD COMPONENT
// =============================================================================

interface EventCardProps {
  event: Event;
  viewMode: ViewMode;
  canEdit: boolean;
}

function EventCard({ event, viewMode, canEdit }: EventCardProps) {
  const router = useRouter();

  const statusConfig: Record<EventStatus, { color: string; bg: string; label: string }> = {
    DRAFT: { color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)', label: 'Draft' },
    PUBLISHED: { color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)', label: 'Published' },
    ENDED: { color: '#94A3B8', bg: 'rgba(100, 116, 139, 0.1)', label: 'Ended' },
    CANCELLED: { color: '#EF4444', bg: 'rgba(239, 68, 68, 0.1)', label: 'Cancelled' },
  };

  const status = statusConfig[event.status];
  const progress = (event.ticketsSold / event.ticketsTotal) * 100;

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  const formatCurrency = (amount: number) => {
    return `K ${amount.toLocaleString()}`;
  };

  if (viewMode === 'list') {
    return (
      <Card
        style={{
          padding: '16px 20px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '12px',
          cursor: 'pointer',
          transition: 'all 200ms ease',
        }}
        onClick={() => router.push(`/events/${event.id}`)}
        className="event-card-hover"
      >
        <Flex align="center" gap="4">
          {/* Thumbnail */}
          <Box
            style={{
              width: 64,
              height: 64,
              borderRadius: '8px',
              background: event.coverImageUrl
                ? `url(${event.coverImageUrl}) center/cover`
                : 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
              flexShrink: 0,
            }}
          />

          {/* Info */}
          <Box style={{ flex: 1, minWidth: 0 }}>
            <Flex align="center" gap="2" mb="1">
              <Text
                size="2"
                weight="medium"
                style={{
                  color: 'var(--content-primary)',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {event.title}
              </Text>
              <Badge style={{ background: status.bg, color: status.color }}>
                {status.label}
              </Badge>
            </Flex>
            <Flex align="center" gap="3">
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {formatDate(event.startDate)}
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                •
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {event.location}
              </Text>
            </Flex>
          </Box>

          {/* Stats */}
          <Flex gap="6" align="center" className="hidden-mobile">
            <Box style={{ textAlign: 'right' }}>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                {event.ticketsSold} / {event.ticketsTotal}
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                Tickets
              </Text>
            </Box>
            <Box style={{ textAlign: 'right' }}>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                {formatCurrency(event.revenue)}
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                Revenue
              </Text>
            </Box>
          </Flex>

          {/* Actions */}
          {canEdit && (
            <DropdownMenu.Root>
              <DropdownMenu.Trigger>
                <Button
                  variant="ghost"
                  size="1"
                  onClick={(e) => e.stopPropagation()}
                  style={{ color: 'var(--content-muted)' }}
                >
                  <MoreHoriz style={{ width: 18, height: 18 }} />
                </Button>
              </DropdownMenu.Trigger>
              <DropdownMenu.Content>
                <DropdownMenu.Item onClick={() => router.push(`/events/${event.id}`)}>
                  <Eye style={{ width: 16, height: 16, marginRight: 8 }} />
                  View Details
                </DropdownMenu.Item>
                <DropdownMenu.Item onClick={() => router.push(`/events/${event.id}/edit`)}>
                  <Edit style={{ width: 16, height: 16, marginRight: 8 }} />
                  Edit Event
                </DropdownMenu.Item>
                <DropdownMenu.Item>
                  <Copy style={{ width: 16, height: 16, marginRight: 8 }} />
                  Duplicate
                </DropdownMenu.Item>
                <DropdownMenu.Separator />
                <DropdownMenu.Item color="red">
                  <Trash style={{ width: 16, height: 16, marginRight: 8 }} />
                  Delete
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Root>
          )}
        </Flex>
      </Card>
    );
  }

  // Grid view
  return (
    <Card
      style={{
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: '16px',
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 200ms ease',
      }}
      onClick={() => router.push(`/events/${event.id}`)}
      className="event-card-hover"
    >
      {/* Cover Image */}
      <Box
        style={{
          height: 160,
          background: event.coverImageUrl
            ? `url(${event.coverImageUrl}) center/cover`
            : 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
          position: 'relative',
        }}
      >
        <Badge
          style={{
            position: 'absolute',
            top: 12,
            right: 12,
            background: status.bg,
            color: status.color,
          }}
        >
          {status.label}
        </Badge>
      </Box>

      {/* Content */}
      <Box p="4">
        <Text
          size="3"
          weight="medium"
          style={{
            color: 'var(--content-primary)',
            display: 'block',
            marginBottom: '8px',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {event.title}
        </Text>

        <Flex align="center" gap="2" mb="2">
          <Calendar style={{ width: 14, height: 14, color: 'var(--content-muted)' }} />
          <Text size="1" style={{ color: 'var(--content-muted)' }}>
            {formatDate(event.startDate)}
          </Text>
        </Flex>

        <Text
          size="1"
          style={{
            color: 'var(--content-muted)',
            display: 'block',
            marginBottom: '16px',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {event.location}
        </Text>

        {/* Progress Bar */}
        <Box mb="3">
          <Flex justify="between" mb="1">
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              Tickets Sold
            </Text>
            <Text size="1" weight="medium" style={{ color: 'var(--content-primary)' }}>
              {event.ticketsSold} / {event.ticketsTotal}
            </Text>
          </Flex>
          <Box
            style={{
              height: 6,
              borderRadius: 3,
              background: 'var(--surface-subtle)',
              overflow: 'hidden',
            }}
          >
            <Box
              style={{
                width: `${progress}%`,
                height: '100%',
                borderRadius: 3,
                background: 'linear-gradient(90deg, var(--brand-500) 0%, var(--brand-400) 100%)',
              }}
            />
          </Box>
        </Box>

        {/* Stats Row */}
        <Flex justify="between" pt="3" style={{ borderTop: '1px solid var(--surface-border)' }}>
          <Flex align="center" gap="1">
            <Group style={{ width: 14, height: 14, color: 'var(--content-muted)' }} />
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              {event.ticketsSold}
            </Text>
          </Flex>
          <Flex align="center" gap="1">
            <CreditCard style={{ width: 14, height: 14, color: 'var(--content-muted)' }} />
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              {formatCurrency(event.revenue)}
            </Text>
          </Flex>
        </Flex>
      </Box>
    </Card>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function EventsPage() {
  const { can } = useOrganization();
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState('all');
  const [viewMode, setViewMode] = useState<ViewMode>('grid');

  const canCreateEvents = can('createEvents');
  const canEditEvents = can('createEvents');

  // Filter events based on tab and search
  const filteredEvents = useMemo(() => {
    let events = mockEvents;

    // Filter by status
    if (activeTab !== 'all') {
      const statusMap: Record<string, EventStatus[]> = {
        published: ['PUBLISHED'],
        draft: ['DRAFT'],
        ended: ['ENDED', 'CANCELLED'],
      };
      events = events.filter((e) => statusMap[activeTab]?.includes(e.status));
    }

    // Filter by search
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      events = events.filter(
        (e) =>
          e.title.toLowerCase().includes(query) ||
          e.location.toLowerCase().includes(query)
      );
    }

    return events;
  }, [activeTab, searchQuery]);

  // Count events by status
  const counts = useMemo(() => ({
    all: mockEvents.length,
    published: mockEvents.filter((e) => e.status === 'PUBLISHED').length,
    draft: mockEvents.filter((e) => e.status === 'DRAFT').length,
    ended: mockEvents.filter((e) => ['ENDED', 'CANCELLED'].includes(e.status)).length,
  }), []);

  return (
    <Box>
      <PageHeader
        title="Events"
        description="Manage your events and track ticket sales"
        actions={canCreateEvents ? [
          {
            label: 'Create Event',
            icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
            href: '/events/new',
          },
        ] : undefined}
      />

      {/* Filters Bar */}
      <Flex
        justify="between"
        align="center"
        mb="4"
        gap="4"
        direction={{ initial: 'column', sm: 'row' }}
      >
        {/* Tabs */}
        <Tabs.Root value={activeTab} onValueChange={setActiveTab}>
          <Tabs.List>
            <Tabs.Trigger value="all">
              All ({counts.all})
            </Tabs.Trigger>
            <Tabs.Trigger value="published">
              Published ({counts.published})
            </Tabs.Trigger>
            <Tabs.Trigger value="draft">
              Drafts ({counts.draft})
            </Tabs.Trigger>
            <Tabs.Trigger value="ended">
              Ended ({counts.ended})
            </Tabs.Trigger>
          </Tabs.List>
        </Tabs.Root>

        {/* Search and View Toggle */}
        <Flex gap="2" align="center">
          <TextField.Root
            size="2"
            placeholder="Search events..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ width: 240 }}
          >
            <TextField.Slot>
              <Search style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
            </TextField.Slot>
          </TextField.Root>

          <Flex
            style={{
              background: 'var(--surface-subtle)',
              borderRadius: '8px',
              padding: '2px',
            }}
          >
            <Button
              variant="ghost"
              size="1"
              onClick={() => setViewMode('grid')}
              style={{
                background: viewMode === 'grid' ? 'var(--surface-elevated)' : 'transparent',
                color: viewMode === 'grid' ? 'var(--content-primary)' : 'var(--content-muted)',
                borderRadius: '6px',
              }}
            >
              <GridPlus style={{ width: 18, height: 18 }} />
            </Button>
            <Button
              variant="ghost"
              size="1"
              onClick={() => setViewMode('list')}
              style={{
                background: viewMode === 'list' ? 'var(--surface-elevated)' : 'transparent',
                color: viewMode === 'list' ? 'var(--content-primary)' : 'var(--content-muted)',
                borderRadius: '6px',
              }}
            >
              <List style={{ width: 18, height: 18 }} />
            </Button>
          </Flex>
        </Flex>
      </Flex>

      {/* Events Grid/List */}
      {filteredEvents.length === 0 ? (
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '16px',
          }}
        >
          {searchQuery ? (
            <NoEventsEmptyState
              action={{
                label: 'Clear Search',
                onClick: () => setSearchQuery(''),
              }}
            />
          ) : (
            <NoEventsEmptyState />
          )}
        </Card>
      ) : viewMode === 'grid' ? (
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
            gap: '20px',
          }}
        >
          {filteredEvents.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              viewMode={viewMode}
              canEdit={canEditEvents}
            />
          ))}
        </Box>
      ) : (
        <Flex direction="column" gap="3">
          {filteredEvents.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              viewMode={viewMode}
              canEdit={canEditEvents}
            />
          ))}
        </Flex>
      )}

      <style jsx global>{`
        .event-card-hover:hover {
          border-color: var(--brand-400);
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }

        @media (max-width: 640px) {
          .hidden-mobile {
            display: none !important;
          }
        }
      `}</style>
    </Box>
  );
}
