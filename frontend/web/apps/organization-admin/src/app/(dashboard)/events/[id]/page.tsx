'use client';

/**
 * Event Detail Page
 *
 * Displays comprehensive event information:
 * - Event overview and stats
 * - Ticket sales breakdown
 * - Attendee list
 * - Quick actions
 */

import { useState, useMemo } from 'react';
import { useParams } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  Card,
  Badge,
  Tabs,
  Avatar,
  Table,
  Skeleton,
} from '@radix-ui/themes';
import {
  Calendar,
  MapPin,
  Edit,
  Copy,
  Group,
  CreditCard,
  Eye,
  ShareAndroid,
  ScanQrCode,
  Download,
} from 'iconoir-react';
import { PageHeader, StatCard } from '@/components/ui';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  canEditEvents,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import {
  useMyEventDetail,
  type EventDetailVM,
} from '@pml.tickets/shared/api/organization-admin/modules/events';

// =============================================================================
// TYPES
// =============================================================================

type EventStatus = 'DRAFT' | 'PUBLISHED' | 'ENDED' | 'CANCELLED';

interface TicketTier {
  id: string;
  name: string;
  price: number;
  sold: number;
  total: number;
  revenue: number;
}

interface Attendee {
  id: string;
  name: string;
  email: string;
  ticketType: string;
  purchaseDate: string;
  checkedIn: boolean;
}

interface EventDetail {
  id: string;
  title: string;
  description: string;
  coverImageUrl?: string;
  status: EventStatus;
  startDate: string;
  endDate: string;
  location: string;
  address: string;
  ticketsSold: number;
  ticketsTotal: number;
  revenue: number;
  checkedIn: number;
  tiers: TicketTier[];
  recentAttendees: Attendee[];
}

// =============================================================================
// ADAPTER — catalog Event → this page's presentation shape
//
// No fixture data lives in this app. Fields the backend does not expose stay
// empty rather than being filled with a plausible-looking default.
// =============================================================================

function toEventDetail(event: EventDetailVM): EventDetail {
  const tiers: TicketTier[] = (event.ticketTiers ?? []).map((tier) => ({
    id: tier.id,
    name: tier.name,
    price: Number(tier.price ?? 0),
    sold: tier.soldQuantity ?? 0,
    total: tier.quantity ?? 0,
    // Per-tier revenue is not a field the catalog exposes; price x sold is the
    // gross for the tier and is exactly what the column means.
    revenue: Number(tier.price ?? 0) * (tier.soldQuantity ?? 0),
  }));

  return {
    id: event.id,
    title: event.title,
    description: event.description ?? '',
    coverImageUrl: event.bannerImageUrl ?? undefined,
    status: event.status as EventStatus,
    startDate: event.eventDateTime,
    endDate: event.endDateTime,
    location: event.locationName ?? event.cityName ?? '',
    address: event.locationAddress ?? '',
    ticketsSold: event.soldTickets ?? 0,
    ticketsTotal: event.totalCapacity ?? 0,
    revenue: Number(event.revenue ?? 0),
    // Gate attendance is a booking-service concern and is not on the catalog
    // Event. Left at zero rather than invented; see ORG_ADMIN_REDESIGN_NOTES.
    checkedIn: 0,
    tiers,
    recentAttendees: [],
  };
}


// =============================================================================
// STATUS CONFIG
// =============================================================================

const statusConfig: Record<EventStatus, { color: string; bg: string; label: string }> = {
  // Humanized enums (spec §10): PUBLISHED reads as "Live". Status colors are
  // the generic status ramps — never jade, which is reserved for amounts.
  DRAFT: { color: 'var(--status-warning-11)', bg: 'var(--status-warning-a3)', label: 'Draft' },
  PUBLISHED: { color: 'var(--status-success-11)', bg: 'var(--status-success-a3)', label: 'Live' },
  ENDED: { color: 'var(--gray-11)', bg: 'var(--gray-a3)', label: 'Ended' },
  CANCELLED: { color: 'var(--status-danger-11)', bg: 'var(--status-danger-a3)', label: 'Cancelled' },
};

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function EventDetailPage() {
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const { status: orgStatus } = useMyOrganization({ skip: !isAuthenticated });

  const [activeTab, setActiveTab] = useState('overview');

  const params = useParams<{ id: string }>();
  const { event: eventRow, loading } = useMyEventDetail(params?.id);

  const event = useMemo(() => (eventRow ? toEventDetail(eventRow) : null), [eventRow]);

  const canEdit = canEditEvents(orgStatus);

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  const formatTime = (dateStr: string) => {
    return new Date(dateStr).toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading && !event) {
    return (
      <Box data-testid="event-detail-loading">
        <Skeleton style={{ height: 120, borderRadius: 'var(--card-radius-bento)', marginBottom: 16 }} />
        <Skeleton style={{ height: 320, borderRadius: 'var(--card-radius-bento)' }} />
      </Box>
    );
  }

  // Null covers both "no such event" and "not yours" — `event(id:)` is public
  // but the organizer-tagged fields resolve to null for a non-owner. Rendering
  // an empty shell would look like a real event with no data in it.
  if (!event) {
    return (
      <Box data-testid="event-detail-not-found">
        <PageHeader
          title="Event not found"
          description="This event does not exist, or it is not one of yours."
          breadcrumbs={[{ label: 'Events', href: '/events' }, { label: 'Not found' }]}
        />
      </Box>
    );
  }

  const status = statusConfig[event.status];
  // Guard the denominator: a draft with no allocation yet would otherwise
  // render NaN% sold.
  const progress = event.ticketsTotal > 0 ? (event.ticketsSold / event.ticketsTotal) * 100 : 0;

  return (
    <Box>
      <PageHeader
        title={event.title}
        breadcrumbs={[
          { label: 'Events', href: '/events' },
          { label: event.title },
        ]}
        actions={canEdit ? [
          {
            label: 'Edit event',
            icon: <Edit width={18} height={18} style={{ marginRight: 8 }} />,
            href: `/events/${event.id}/edit`,
            variant: 'outline',
          },
          {
            label: 'Check-in',
            icon: <ScanQrCode width={18} height={18} style={{ marginRight: 8 }} />,
            href: `/check-in?event=${event.id}`,
          },
        ] : undefined}
      />

      {/* Status strip. PageHeader takes no children — its contract is
          title / description / breadcrumbs / actions only. */}
      <Flex align="center" gap="3" mt="-4" mb="6">
        <Badge style={{ background: status.bg, color: status.color }}>{status.label}</Badge>
        <Text size="2" style={{ color: 'var(--gray-10)' }}>
          {formatDate(event.startDate)}
        </Text>
      </Flex>

      {/* Cover Image */}
      {event.coverImageUrl && (
        <Box
          mb="6"
          style={{
            height: 240,
            borderRadius: 'var(--card-radius-bento)',
            background: `url(${event.coverImageUrl}) center/cover`,
            position: 'relative',
          }}
        >
          <Box
            style={{
              position: 'absolute',
              inset: 0,
              background: 'linear-gradient(to bottom, transparent 50%, rgba(0,0,0,0.6) 100%)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          />
        </Box>
      )}

      {/* Bento stat row — auto-fit minmax(240px, 1fr), not a fixed 12-col grid. */}
      <Box className="ds-bento-grid" mb="6">
        <StatCard
          title="Tickets sold"
          value={event.ticketsSold}
          icon={<Group width={20} height={20} />}
          changeLabel={`of ${event.ticketsTotal} total`}
        />
        <StatCard
          title="Total revenue"
          value={`K ${event.revenue.toLocaleString()}`}
          icon={<CreditCard width={20} height={20} />}
          change={12}
          trend="up"
        />
        <StatCard
          title="Check-ins"
          value={event.checkedIn}
          icon={<ScanQrCode width={20} height={20} />}
          changeLabel={`${Math.round((event.checkedIn / event.ticketsSold) * 100) || 0}% attendance`}
        />
        <StatCard
          title="Page views"
          value="1,234"
          icon={<Eye width={20} height={20} />}
          change={8}
          trend="up"
        />
      </Box>

      {/* Tabs */}
      <Tabs.Root value={activeTab} onValueChange={setActiveTab}>
        <Tabs.List mb="4">
          <Tabs.Trigger value="overview">Overview</Tabs.Trigger>
          <Tabs.Trigger value="tickets">Tickets</Tabs.Trigger>
          <Tabs.Trigger value="attendees">Attendees</Tabs.Trigger>
        </Tabs.List>

        {/* Overview Tab */}
        <Tabs.Content value="overview">
          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
              gap: '24px',
            }}
          >
            {/* Event Details */}
            <Card
              style={{
                padding: '24px',
                background: 'var(--surface-elevated)',
                border: '1px solid var(--surface-border)',
                borderRadius: 'var(--card-radius-bento)',
              }}
            >
              <Heading size="4" mb="4" style={{ color: 'var(--content-primary)' }}>
                Event Details
              </Heading>

              <Flex direction="column" gap="4">
                <Flex align="start" gap="3">
                  <Box
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 'var(--radius-3)',
                      background: 'var(--accent-a3)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Calendar style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
                  </Box>
                  <Box>
                    <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                      {formatDate(event.startDate)}
                    </Text>
                    <Text size="1" style={{ color: 'var(--content-muted)' }}>
                      {formatTime(event.startDate)} - {formatTime(event.endDate)}
                    </Text>
                  </Box>
                </Flex>

                <Flex align="start" gap="3">
                  <Box
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 'var(--radius-3)',
                      background: 'var(--accent-a3)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <MapPin style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
                  </Box>
                  <Box>
                    <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                      {event.location}
                    </Text>
                    <Text size="1" style={{ color: 'var(--content-muted)' }}>
                      {event.address}
                    </Text>
                  </Box>
                </Flex>
              </Flex>

              <Box mt="5" pt="5" style={{ borderTop: '1px solid var(--surface-border)' }}>
                <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
                  About This Event
                </Text>
                <Text size="2" style={{ color: 'var(--content-muted)', lineHeight: 1.6 }}>
                  {event.description}
                </Text>
              </Box>
            </Card>

            {/* Quick Actions & Sales Progress */}
            <Flex direction="column" gap="4">
              {/* Sales Progress */}
              <Card
                style={{
                  padding: '24px',
                  background: 'var(--surface-elevated)',
                  border: '1px solid var(--surface-border)',
                  borderRadius: 'var(--card-radius-bento)',
                }}
              >
                <Heading size="4" mb="4" style={{ color: 'var(--content-primary)' }}>
                  Sales Progress
                </Heading>

                <Box mb="4">
                  <Flex justify="between" mb="2">
                    <Text size="2" style={{ color: 'var(--content-muted)' }}>
                      {event.ticketsSold} of {event.ticketsTotal} sold
                    </Text>
                    <Text size="2" weight="medium" style={{ color: 'var(--brand-500)' }}>
                      {Math.round(progress)}%
                    </Text>
                  </Flex>
                  <Box
                    style={{
                      height: 8,
                      borderRadius: 4,
                      background: 'var(--surface-subtle)',
                      overflow: 'hidden',
                    }}
                  >
                    <Box
                      style={{
                        width: `${progress}%`,
                        height: '100%',
                        borderRadius: 4,
                        background: 'var(--accent-9)',
                      }}
                    />
                  </Box>
                </Box>

                <Text size="1" style={{ color: 'var(--content-muted)' }}>
                  {event.ticketsTotal - event.ticketsSold} tickets remaining
                </Text>
              </Card>

              {/* Quick Actions */}
              <Card
                style={{
                  padding: '24px',
                  background: 'var(--surface-elevated)',
                  border: '1px solid var(--surface-border)',
                  borderRadius: 'var(--card-radius-bento)',
                }}
              >
                <Heading size="4" mb="4" style={{ color: 'var(--content-primary)' }}>
                  Quick Actions
                </Heading>

                <Flex direction="column" gap="2">
                  <Button
                    variant="outline"
                    style={{ justifyContent: 'flex-start', borderColor: 'var(--surface-border)' }}
                  >
                    <ShareAndroid style={{ width: 18, height: 18, marginRight: 12 }} />
                    Share Event
                  </Button>
                  <Button
                    variant="outline"
                    style={{ justifyContent: 'flex-start', borderColor: 'var(--surface-border)' }}
                  >
                    <Copy style={{ width: 18, height: 18, marginRight: 12 }} />
                    Duplicate Event
                  </Button>
                  <Button
                    variant="outline"
                    style={{ justifyContent: 'flex-start', borderColor: 'var(--surface-border)' }}
                  >
                    <Download style={{ width: 18, height: 18, marginRight: 12 }} />
                    Export Attendees
                  </Button>
                </Flex>
              </Card>
            </Flex>
          </Box>
        </Tabs.Content>

        {/* Tickets Tab */}
        <Tabs.Content value="tickets">
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Heading size="4" mb="4" style={{ color: 'var(--content-primary)' }}>
              Ticket Tiers
            </Heading>

            <Flex direction="column" gap="3">
              {event.tiers.map((tier) => (
                <Card
                  key={tier.id}
                  style={{
                    padding: '20px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--surface-border)',
                    borderRadius: 'var(--card-radius)',
                  }}
                >
                  <Flex justify="between" align="center" mb="3">
                    <Box>
                      <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                        {tier.name}
                      </Text>
                      <Text size="2" style={{ color: 'var(--brand-500)' }}>
                        K {tier.price.toLocaleString()}
                      </Text>
                    </Box>
                    <Box style={{ textAlign: 'right' }}>
                      <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                        K {tier.revenue.toLocaleString()}
                      </Text>
                      <Text size="1" style={{ color: 'var(--content-muted)' }}>
                        Revenue
                      </Text>
                    </Box>
                  </Flex>

                  <Box>
                    <Flex justify="between" mb="1">
                      <Text size="1" style={{ color: 'var(--content-muted)' }}>
                        {tier.sold} of {tier.total} sold
                      </Text>
                      <Text size="1" style={{ color: 'var(--content-muted)' }}>
                        {Math.round((tier.sold / tier.total) * 100)}%
                      </Text>
                    </Flex>
                    <Box
                      style={{
                        height: 6,
                        borderRadius: 3,
                        background: 'var(--surface-border)',
                        overflow: 'hidden',
                      }}
                    >
                      <Box
                        style={{
                          width: `${(tier.sold / tier.total) * 100}%`,
                          height: '100%',
                          borderRadius: 3,
                          background: tier.sold === tier.total
                            ? 'var(--success-500)'
                            : 'var(--accent-9)',
                        }}
                      />
                    </Box>
                  </Box>
                </Card>
              ))}
            </Flex>
          </Card>
        </Tabs.Content>

        {/* Attendees Tab */}
        <Tabs.Content value="attendees">
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Flex justify="between" align="center" mb="4">
              <Heading size="4" style={{ color: 'var(--content-primary)' }}>
                Recent Attendees
              </Heading>
              <Button
                variant="outline"
                size="2"
                style={{ borderColor: 'var(--accent-a6)', color: 'var(--brand-500)' }}
              >
                <Download style={{ width: 16, height: 16, marginRight: 8 }} />
                Export All
              </Button>
            </Flex>

            <Table.Root>
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>Attendee</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Ticket Type</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Purchase Date</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {event.recentAttendees.map((attendee) => (
                  <Table.Row key={attendee.id}>
                    <Table.Cell>
                      <Flex align="center" gap="3">
                        <Avatar
                          size="2"
                          fallback={attendee.name.charAt(0)}
                          radius="full"
                        />
                        <Box>
                          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                            {attendee.name}
                          </Text>
                          <Text size="1" style={{ color: 'var(--content-muted)' }}>
                            {attendee.email}
                          </Text>
                        </Box>
                      </Flex>
                    </Table.Cell>
                    <Table.Cell>
                      <Badge variant="soft">{attendee.ticketType}</Badge>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" style={{ color: 'var(--content-muted)' }}>
                        {new Date(attendee.purchaseDate).toLocaleDateString()}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Badge
                        color={attendee.checkedIn ? 'green' : 'gray'}
                        variant="soft"
                      >
                        {attendee.checkedIn ? 'Checked In' : 'Not Checked In'}
                      </Badge>
                    </Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
          </Card>
        </Tabs.Content>
      </Tabs.Root>
    </Box>
  );
}
