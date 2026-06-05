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

import { useState } from 'react';
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
import { PageHeader, StatCard, StatGrid } from '@/components/ui';
import { useOrganization } from '@/lib/contexts/OrganizationContext';

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
// MOCK DATA
// =============================================================================

const mockEvent: EventDetail = {
  id: '1',
  title: 'Tech Summit Zambia 2025',
  description: 'Join us for the biggest tech conference in Zambia. Network with industry leaders, attend workshops, and learn about the latest trends in technology.',
  coverImageUrl: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=1200',
  status: 'PUBLISHED',
  startDate: '2025-12-15T09:00:00Z',
  endDate: '2025-12-15T18:00:00Z',
  location: 'Mulungushi Conference Center',
  address: 'Great East Road, Lusaka, Zambia',
  ticketsSold: 450,
  ticketsTotal: 500,
  revenue: 67500,
  checkedIn: 0,
  tiers: [
    { id: '1', name: 'Early Bird', price: 100, sold: 200, total: 200, revenue: 20000 },
    { id: '2', name: 'Regular', price: 150, sold: 200, total: 250, revenue: 30000 },
    { id: '3', name: 'VIP', price: 350, sold: 50, total: 50, revenue: 17500 },
  ],
  recentAttendees: [
    { id: '1', name: 'John Mwanza', email: 'john@email.com', ticketType: 'VIP', purchaseDate: '2025-11-20', checkedIn: false },
    { id: '2', name: 'Mary Banda', email: 'mary@email.com', ticketType: 'Regular', purchaseDate: '2025-11-19', checkedIn: false },
    { id: '3', name: 'Peter Tembo', email: 'peter@email.com', ticketType: 'Early Bird', purchaseDate: '2025-11-18', checkedIn: false },
    { id: '4', name: 'Grace Phiri', email: 'grace@email.com', ticketType: 'Regular', purchaseDate: '2025-11-17', checkedIn: false },
    { id: '5', name: 'David Lungu', email: 'david@email.com', ticketType: 'VIP', purchaseDate: '2025-11-16', checkedIn: false },
  ],
};

// =============================================================================
// STATUS CONFIG
// =============================================================================

const statusConfig: Record<EventStatus, { color: string; bg: string; label: string }> = {
  DRAFT: { color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)', label: 'Draft' },
  PUBLISHED: { color: '#10B981', bg: 'rgba(16, 185, 129, 0.1)', label: 'Published' },
  ENDED: { color: '#94A3B8', bg: 'rgba(100, 116, 139, 0.1)', label: 'Ended' },
  CANCELLED: { color: '#EF4444', bg: 'rgba(239, 68, 68, 0.1)', label: 'Cancelled' },
};

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function EventDetailPage() {
  const { can } = useOrganization();
  const [activeTab, setActiveTab] = useState('overview');

  // In real app, fetch event by ID
  const event = mockEvent;
  const status = statusConfig[event.status];

  const canEdit = can('createEvents');

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

  const progress = (event.ticketsSold / event.ticketsTotal) * 100;

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
            label: 'Edit Event',
            icon: <Edit style={{ width: 18, height: 18, marginRight: 8 }} />,
            href: `/events/${event.id}/edit`,
            variant: 'outline',
          },
          {
            label: 'Check-in',
            icon: <ScanQrCode style={{ width: 18, height: 18, marginRight: 8 }} />,
            href: `/check-in?event=${event.id}`,
          },
        ] : undefined}
      >
        <Flex align="center" gap="3" mt="2">
          <Badge style={{ background: status.bg, color: status.color }}>
            {status.label}
          </Badge>
          <Text size="2" style={{ color: 'var(--content-muted)' }}>
            {formatDate(event.startDate)}
          </Text>
        </Flex>
      </PageHeader>

      {/* Cover Image */}
      {event.coverImageUrl && (
        <Box
          mb="6"
          style={{
            height: 240,
            borderRadius: '16px',
            background: `url(${event.coverImageUrl}) center/cover`,
            position: 'relative',
          }}
        >
          <Box
            style={{
              position: 'absolute',
              inset: 0,
              background: 'linear-gradient(to bottom, transparent 50%, rgba(0,0,0,0.6) 100%)',
              borderRadius: '16px',
            }}
          />
        </Box>
      )}

      {/* Stats Grid */}
      <StatGrid>
        <StatCard
          title="Tickets Sold"
          value={event.ticketsSold}
          icon={<Group style={{ width: 20, height: 20 }} />}
          subtitle={`of ${event.ticketsTotal} total`}
        />
        <StatCard
          title="Total Revenue"
          value={event.revenue}
          icon={<CreditCard style={{ width: 20, height: 20 }} />}
          isCurrency
          change={12}
          trend="up"
        />
        <StatCard
          title="Check-ins"
          value={event.checkedIn}
          icon={<ScanQrCode style={{ width: 20, height: 20 }} />}
          subtitle={`${Math.round((event.checkedIn / event.ticketsSold) * 100) || 0}% attendance`}
        />
        <StatCard
          title="Views"
          value="1,234"
          icon={<Eye style={{ width: 20, height: 20 }} />}
          change={8}
          trend="up"
        />
      </StatGrid>

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
                borderRadius: '16px',
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
                      borderRadius: '10px',
                      background: 'rgba(16, 185, 129, 0.1)',
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
                      borderRadius: '10px',
                      background: 'rgba(16, 185, 129, 0.1)',
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
                  borderRadius: '16px',
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
                        background: 'linear-gradient(90deg, var(--brand-500) 0%, var(--brand-400) 100%)',
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
                  borderRadius: '16px',
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
              borderRadius: '16px',
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
                    borderRadius: '12px',
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
                            : 'linear-gradient(90deg, var(--brand-500) 0%, var(--brand-400) 100%)',
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
              borderRadius: '16px',
            }}
          >
            <Flex justify="between" align="center" mb="4">
              <Heading size="4" style={{ color: 'var(--content-primary)' }}>
                Recent Attendees
              </Heading>
              <Button
                variant="outline"
                size="2"
                style={{ borderColor: 'rgba(16, 185, 129, 0.3)', color: 'var(--brand-500)' }}
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
