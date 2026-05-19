'use client';

/**
 * Dashboard Home Page
 *
 * Displays:
 * - Key platform metrics
 * - Recent activity
 * - Quick actions
 *
 * Responsive layout:
 * - 1 column on mobile
 * - 2 columns on tablet
 * - 4 columns on desktop (stats)
 */

import { Box, Flex, Grid, Heading, Text, Badge, Button } from '@radix-ui/themes';
import {
  Calendar,
  Group,
  Label,
  CreditCard,
  ArrowRight,
  Clock,
  CheckCircle,
  WarningTriangle,
} from 'iconoir-react';
import Link from 'next/link';
import { StatCard, SectionCard, EmptyCard } from '@/components/ui/StyledCard';

// =============================================================================
// MOCK DATA (Replace with real data from GraphQL)
// =============================================================================

const recentApplications = [
  {
    id: '1',
    name: 'John Banda Events',
    email: 'john@bandaevents.com',
    submittedAt: '2 hours ago',
    status: 'pending',
  },
  {
    id: '2',
    name: 'Lusaka Concerts Ltd',
    email: 'info@lusakaconcerts.zm',
    submittedAt: '5 hours ago',
    status: 'pending',
  },
  {
    id: '3',
    name: 'Copperbelt Entertainment',
    email: 'events@copperbelt.zm',
    submittedAt: '1 day ago',
    status: 'under_review',
  },
];

const recentEvents = [
  {
    id: '1',
    title: 'Zambia Music Awards 2024',
    organizer: 'ZMA Productions',
    date: 'Dec 15, 2024',
    ticketsSold: 2450,
    status: 'active',
  },
  {
    id: '2',
    title: 'Lusaka Food Festival',
    organizer: 'Foodies Zambia',
    date: 'Dec 20, 2024',
    ticketsSold: 890,
    status: 'active',
  },
  {
    id: '3',
    title: 'Tech Summit Zambia',
    organizer: 'ZamTech Hub',
    date: 'Jan 10, 2025',
    ticketsSold: 320,
    status: 'draft',
  },
];

// =============================================================================
// HELPER COMPONENTS
// =============================================================================

function StatusBadge({ status }: { status: string }) {
  const config: Record<string, { color: 'orange' | 'blue' | 'green' | 'gray'; label: string }> = {
    pending: { color: 'orange', label: 'Pending' },
    under_review: { color: 'blue', label: 'Under Review' },
    approved: { color: 'green', label: 'Approved' },
    active: { color: 'green', label: 'Active' },
    draft: { color: 'gray', label: 'Draft' },
  };

  const { color, label } = config[status] || { color: 'gray', label: status };

  return (
    <Badge color={color} variant="soft" size="1">
      {label}
    </Badge>
  );
}

function ApplicationRow({
  application,
}: {
  application: (typeof recentApplications)[0];
}) {
  return (
    <Flex
      align="center"
      justify="between"
      py="3"
      style={{
        borderBottom: '1px solid var(--gray-a4)',
      }}
      className="application-row"
    >
      <Flex direction="column" gap="1" style={{ minWidth: 0, flex: 1 }}>
        <Text size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
          {application.name}
        </Text>
        <Text size="1" color="gray" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {application.email}
        </Text>
      </Flex>
      <Flex align="center" gap="3">
        <Flex align="center" gap="1" className="time-info">
          <Clock style={{ width: 12, height: 12, color: 'var(--gray-9)' }} />
          <Text size="1" color="gray">
            {application.submittedAt}
          </Text>
        </Flex>
        <StatusBadge status={application.status} />
      </Flex>
    </Flex>
  );
}

function EventRow({ event }: { event: (typeof recentEvents)[0] }) {
  return (
    <Flex
      align="center"
      justify="between"
      py="3"
      style={{
        borderBottom: '1px solid var(--gray-a4)',
      }}
      className="event-row"
    >
      <Flex direction="column" gap="1" style={{ minWidth: 0, flex: 1 }}>
        <Text size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
          {event.title}
        </Text>
        <Flex align="center" gap="2">
          <Text size="1" color="gray">
            {event.organizer}
          </Text>
          <Text size="1" color="gray">
            •
          </Text>
          <Text size="1" color="gray">
            {event.date}
          </Text>
        </Flex>
      </Flex>
      <Flex align="center" gap="3">
        <Flex align="center" gap="1" className="tickets-info">
          <Label style={{ width: 12, height: 12, color: 'var(--gray-9)' }} />
          <Text size="1" color="gray">
            {event.ticketsSold.toLocaleString()}
          </Text>
        </Flex>
        <StatusBadge status={event.status} />
      </Flex>
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function DashboardPage() {
  return (
    <Box>
      <Flex direction="column" gap="6">
        {/* Page Header */}
        <Flex
          direction={{ initial: 'column', sm: 'row' }}
          justify="between"
          align={{ initial: 'start', sm: 'center' }}
          gap="3"
        >
          <Box>
            <Heading size="6" weight="bold" style={{ color: 'var(--gray-12)' }}>
              Dashboard
            </Heading>
            <Text color="gray" size="2" mt="1" style={{ display: 'block' }}>
              Welcome back! Here&apos;s an overview of your platform.
            </Text>
          </Box>
          <Flex gap="2">
            <Button variant="soft" size="2">
              <Calendar style={{ width: 16, height: 16 }} />
              Last 30 days
            </Button>
          </Flex>
        </Flex>

        {/* Stats Grid */}
        <Grid columns={{ initial: '1', xs: '2', lg: '4' }} gap="4">
          <StatCard
            title="Total Events"
            value="156"
            change="12% from last month"
            changeType="positive"
            icon={<Calendar style={{ width: 22, height: 22, color: 'var(--violet-11)' }} />}
          />
          <StatCard
            title="Active Organizers"
            value="48"
            change="5 new this week"
            changeType="positive"
            icon={<Group style={{ width: 22, height: 22, color: 'var(--violet-11)' }} />}
          />
          <StatCard
            title="Tickets Sold"
            value="12,847"
            change="23% from last month"
            changeType="positive"
            icon={<Label style={{ width: 22, height: 22, color: 'var(--violet-11)' }} />}
          />
          <StatCard
            title="Revenue"
            value="K 2.4M"
            change="18% from last month"
            changeType="positive"
            icon={<CreditCard style={{ width: 22, height: 22, color: 'var(--violet-11)' }} />}
          />
        </Grid>

        {/* Activity Grid */}
        <Grid columns={{ initial: '1', lg: '2' }} gap="4">
          {/* Recent Organizer Applications */}
          <SectionCard
            title="Recent Organizer Applications"
            action={
              <Link href="/organizers?status=pending" style={{ textDecoration: 'none' }}>
                <Button variant="ghost" size="1">
                  View all
                  <ArrowRight style={{ width: 14, height: 14 }} />
                </Button>
              </Link>
            }
            minHeight="320px"
          >
            {recentApplications.length > 0 ? (
              <Flex direction="column">
                {recentApplications.map((app) => (
                  <ApplicationRow key={app.id} application={app} />
                ))}
              </Flex>
            ) : (
              <EmptyCard
                message="No pending applications at the moment."
                icon={<CheckCircle style={{ width: 24, height: 24, color: 'var(--green-11)' }} />}
              />
            )}
          </SectionCard>

          {/* Recent Events */}
          <SectionCard
            title="Recent Events"
            action={
              <Link href="/dashboard/events" style={{ textDecoration: 'none' }}>
                <Button variant="ghost" size="1">
                  View all
                  <ArrowRight style={{ width: 14, height: 14 }} />
                </Button>
              </Link>
            }
            minHeight="320px"
          >
            {recentEvents.length > 0 ? (
              <Flex direction="column">
                {recentEvents.map((event) => (
                  <EventRow key={event.id} event={event} />
                ))}
              </Flex>
            ) : (
              <EmptyCard
                message="No events created yet."
                icon={<Calendar style={{ width: 24, height: 24, color: 'var(--gray-9)' }} />}
              />
            )}
          </SectionCard>
        </Grid>

        {/* Alerts Section */}
        <SectionCard
          title="System Alerts"
          action={
            <Badge color="orange" variant="soft">
              2 Active
            </Badge>
          }
        >
          <Flex direction="column" gap="2">
            <Flex
              align="center"
              gap="3"
              p="3"
              style={{
                backgroundColor: 'var(--orange-a2)',
                borderRadius: '8px',
                border: '1px solid var(--orange-a4)',
              }}
            >
              <WarningTriangle style={{ width: 18, height: 18, color: 'var(--orange-11)', flexShrink: 0 }} />
              <Box style={{ flex: 1 }}>
                <Text size="2" weight="medium" style={{ color: 'var(--orange-11)' }}>
                  3 payout requests pending review
                </Text>
                <Text size="1" style={{ color: 'var(--orange-10)' }}>
                  Organizers are waiting for their funds to be released.
                </Text>
              </Box>
              <Button variant="soft" color="orange" size="1">
                Review
              </Button>
            </Flex>
            <Flex
              align="center"
              gap="3"
              p="3"
              style={{
                backgroundColor: 'var(--blue-a2)',
                borderRadius: '8px',
                border: '1px solid var(--blue-a4)',
              }}
            >
              <Clock style={{ width: 18, height: 18, color: 'var(--blue-11)', flexShrink: 0 }} />
              <Box style={{ flex: 1 }}>
                <Text size="2" weight="medium" style={{ color: 'var(--blue-11)' }}>
                  5 events starting within 24 hours
                </Text>
                <Text size="1" style={{ color: 'var(--blue-10)' }}>
                  Ensure all ticket validations systems are operational.
                </Text>
              </Box>
              <Button variant="soft" color="blue" size="1">
                View
              </Button>
            </Flex>
          </Flex>
        </SectionCard>
      </Flex>

      {/* Responsive styles */}
      <style jsx global>{`
        @media (max-width: 640px) {
          .time-info,
          .tickets-info {
            display: none;
          }
        }
        .application-row:last-child,
        .event-row:last-child {
          border-bottom: none;
        }
        .application-row:hover,
        .event-row:hover {
          background-color: var(--gray-a2);
          margin: 0 -16px;
          padding-left: 16px;
          padding-right: 16px;
        }
      `}</style>
    </Box>
  );
}
