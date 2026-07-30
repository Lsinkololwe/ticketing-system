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

import { useState, useMemo, useCallback } from 'react';
import { Box, Flex, Text, TextField, Button, Badge, Card, Tabs, DropdownMenu, Callout } from '@radix-ui/themes';
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
  Rocket,
} from 'iconoir-react';
import { useRouter } from 'next/navigation';
import { PageHeader, NoEventsEmptyState } from '@/components/ui';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  canCreateDraftEvents,
  isApproved,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import {
  useMyEvents,
  usePublishEvent,
  useUnpublishEvent,
  type MyEventRow,
} from '@pml.tickets/shared/api/organization-admin/modules/events';

// =============================================================================
// TYPES
// =============================================================================

type EventStatus = 'DRAFT' | 'PUBLISHED' | 'ENDED' | 'CANCELLED';
type ViewMode = 'grid' | 'list';

interface Event {
  id: string;
  title: string;
  coverImageUrl?: string;
  startDate: string;
  endDate: string;
  location: string;
  status: EventStatus;
  /** Real backend status, used to decide publish/unpublish availability. */
  rawStatus: string;
  ticketsSold: number;
  ticketsTotal: number;
  revenue: number;
}

// =============================================================================
// DATA MAPPING
// =============================================================================

/** Collapse the backend EventStatus into the four display buckets. */
function toDisplayStatus(raw: string): EventStatus {
  switch (raw) {
    case 'PUBLISHED':
      return 'PUBLISHED';
    case 'CANCELLED':
      return 'CANCELLED';
    case 'COMPLETED':
      return 'ENDED';
    default:
      // DRAFT, PENDING_REVIEW, CHANGES_REQUESTED, APPROVED
      return 'DRAFT';
  }
}

/** Map a backend event row to the card's view model. */
function mapEvent(row: MyEventRow): Event {
  return {
    id: row.id,
    title: row.title,
    coverImageUrl: row.bannerImageUrl ?? undefined,
    startDate: row.eventDateTime,
    endDate: row.endDateTime,
    location: row.locationName || row.cityName || '—',
    status: toDisplayStatus(row.status),
    rawStatus: row.status,
    ticketsSold: row.soldTickets ?? 0,
    ticketsTotal: row.totalCapacity ?? 0,
    revenue: Number(row.revenue ?? 0),
  };
}

// =============================================================================
// EVENT CARD COMPONENT
// =============================================================================

interface EventCardProps {
  event: Event;
  viewMode: ViewMode;
  canEdit: boolean;
  canPublish: boolean;
  actionLoading: boolean;
  onPublish: (id: string) => void;
  onUnpublish: (id: string) => void;
}

function EventCard({ event, viewMode, canEdit, canPublish, actionLoading, onPublish, onUnpublish }: EventCardProps) {
  const router = useRouter();

  // Humanized enums (spec §10): PUBLISHED reads as "Live". Status colors are
  // the generic status ramps — never jade, which is reserved for amounts.
  const statusConfig: Record<EventStatus, { color: string; bg: string; label: string }> = {
    DRAFT: { color: 'var(--status-warning-11)', bg: 'var(--status-warning-a3)', label: 'Draft' },
    PUBLISHED: { color: 'var(--status-success-11)', bg: 'var(--status-success-a3)', label: 'Live' },
    ENDED: { color: 'var(--gray-11)', bg: 'var(--gray-a3)', label: 'Ended' },
    CANCELLED: { color: 'var(--status-danger-11)', bg: 'var(--status-danger-a3)', label: 'Cancelled' },
  };

  const status = statusConfig[event.status];
  const progress = event.ticketsTotal > 0 ? (event.ticketsSold / event.ticketsTotal) * 100 : 0;

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
          borderRadius: 'var(--card-radius)',
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
              borderRadius: 'var(--radius-4)',
              background: event.coverImageUrl
                ? `url(${event.coverImageUrl}) center/cover`
                : 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
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
                {canPublish && event.rawStatus === 'APPROVED' && (
                  <DropdownMenu.Item
                    onClick={() => onPublish(event.id)}
                    disabled={actionLoading}
                    data-testid="event-publish"
                  >
                    <Rocket style={{ width: 16, height: 16, marginRight: 8 }} />
                    Publish
                  </DropdownMenu.Item>
                )}
                {canPublish && event.rawStatus === 'PUBLISHED' && (
                  <DropdownMenu.Item
                    onClick={() => onUnpublish(event.id)}
                    disabled={actionLoading}
                    data-testid="event-unpublish"
                  >
                    <Rocket style={{ width: 16, height: 16, marginRight: 8 }} />
                    Unpublish
                  </DropdownMenu.Item>
                )}
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
        borderRadius: 'var(--card-radius-bento)',
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
            : 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
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
                background: 'var(--accent-9)',
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
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const { status } = useMyOrganization({ skip: !isAuthenticated });

  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState('all');
  const [viewMode, setViewMode] = useState<ViewMode>('grid');

  // Draft authoring is allowed throughout the approval workflow (pending orgs too);
  // publishing is approved-only. Both are ultimately enforced server-side.
  const canCreate = canCreateDraftEvents(status);
  const canEdit = canCreateDraftEvents(status);
  const canPublish = isApproved(status);

  const { events: rawEvents, loading, error, refetch } = useMyEvents({ skip: !isAuthenticated });
  const { publish, loading: publishing } = usePublishEvent();
  const { unpublish, loading: unpublishing } = useUnpublishEvent();
  const [actionError, setActionError] = useState<string | null>(null);
  const actionLoading = publishing || unpublishing;

  const allEvents = useMemo(() => rawEvents.map(mapEvent), [rawEvents]);

  const handlePublish = useCallback(
    async (id: string) => {
      setActionError(null);
      const res = await publish(id);
      if (res.success) {
        await refetch();
      } else {
        setActionError(res.message || res.errors[0] || 'Failed to publish event');
      }
    },
    [publish, refetch]
  );

  const handleUnpublish = useCallback(
    async (id: string) => {
      setActionError(null);
      const res = await unpublish(id);
      if (res.success) {
        await refetch();
      } else {
        setActionError(res.message || res.errors[0] || 'Failed to unpublish event');
      }
    },
    [unpublish, refetch]
  );

  // Filter events based on tab and search
  const filteredEvents = useMemo(() => {
    let events = allEvents;

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
  }, [allEvents, activeTab, searchQuery]);

  // Count events by status
  const counts = useMemo(() => ({
    all: allEvents.length,
    published: allEvents.filter((e) => e.status === 'PUBLISHED').length,
    draft: allEvents.filter((e) => e.status === 'DRAFT').length,
    ended: allEvents.filter((e) => ['ENDED', 'CANCELLED'].includes(e.status)).length,
  }), [allEvents]);

  return (
    <Box>
      <PageHeader
        title="Events"
        description="Your events and how their ticket sales are going."
        actions={canCreate ? [
          {
            label: 'Create event',
            icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
            href: '/events/new',
          },
        ] : undefined}
      />

      {(actionError || error) && (
        <Callout.Root color="red" size="1" mb="4" role="alert">
          <Callout.Text>
            {actionError || 'Failed to load events. Please try again.'}
          </Callout.Text>
        </Callout.Root>
      )}

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
              borderRadius: 'var(--radius-4)',
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
                borderRadius: 'var(--radius-3)',
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
                borderRadius: 'var(--radius-3)',
              }}
            >
              <List style={{ width: 18, height: 18 }} />
            </Button>
          </Flex>
        </Flex>
      </Flex>

      {/* Events Grid/List */}
      {loading && allEvents.length === 0 ? (
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
            textAlign: 'center',
          }}
        >
          <Text size="2" style={{ color: 'var(--content-muted)' }}>Loading events…</Text>
        </Card>
      ) : filteredEvents.length === 0 ? (
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
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
              canEdit={canEdit}
              canPublish={canPublish}
              actionLoading={actionLoading}
              onPublish={handlePublish}
              onUnpublish={handleUnpublish}
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
              canEdit={canEdit}
              canPublish={canPublish}
              actionLoading={actionLoading}
              onPublish={handlePublish}
              onUnpublish={handleUnpublish}
            />
          ))}
        </Flex>
      )}

      <style jsx global>{`
        .event-card-hover:hover {
          border-color: var(--brand-400);
          transform: translateY(-2px);
          box-shadow: var(--shadow-3);
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
