'use client';

/**
 * Dashboard Home
 *
 * Layout is bento-first (DS §4): stat tiles and panels are auto-fit grids that
 * reflow on their own content width, not a fixed 12-column grid.
 *
 * Copy is operational and third-person (DS §10) — this is a back-office tool,
 * not a greeting. Currency renders through <Amount> so it is always "K 2.4M"
 * in Fira Code, and status enums go through humanizeEnum()/statusTone() so a
 * raw PENDING_REVIEW can never reach the screen.
 */

import { Box, Flex, Text } from '@radix-ui/themes';
import {
  ArrowRight,
  Calendar,
  CheckCircle,
  Clock,
  CreditCard,
  Group,
  Label,
  WarningTriangle,
} from 'iconoir-react';
import Link from 'next/link';
import {
  Amount,
  Badge,
  Button,
  EmptyState,
  PageHeader,
  SectionCard,
  StatCard,
  Toast,
} from '@/components/ui';
import { formatCount, humanizeEnum, statusTone } from '@/lib/format';

// =============================================================================
// MOCK DATA (Replace with real data from GraphQL)
// =============================================================================

const recentApplications = [
  {
    id: '1',
    name: 'John Banda Events',
    email: 'john@bandaevents.com',
    submittedAt: '2 hours ago',
    status: 'PENDING',
  },
  {
    id: '2',
    name: 'Lusaka Concerts Ltd',
    email: 'info@lusakaconcerts.zm',
    submittedAt: '5 hours ago',
    status: 'PENDING',
  },
  {
    id: '3',
    name: 'Copperbelt Entertainment',
    email: 'events@copperbelt.zm',
    submittedAt: '1 day ago',
    status: 'UNDER_REVIEW',
  },
];

const recentEvents = [
  {
    id: '1',
    title: 'Zambia Music Awards 2024',
    organizer: 'ZMA Productions',
    date: 'Dec 15, 2024',
    ticketsSold: 2450,
    status: 'PUBLISHED',
  },
  {
    id: '2',
    title: 'Lusaka Food Festival',
    organizer: 'Foodies Zambia',
    date: 'Dec 20, 2024',
    ticketsSold: 890,
    status: 'PUBLISHED',
  },
  {
    id: '3',
    title: 'Tech Summit Zambia',
    organizer: 'ZamTech Hub',
    date: 'Jan 10, 2025',
    ticketsSold: 320,
    status: 'DRAFT',
  },
];

// =============================================================================
// HELPER COMPONENTS
// =============================================================================

/** Renders any backend status enum as a humanised, correctly-toned chip. */
function StatusBadge({ status }: { status: string }) {
  return <Badge color={statusTone(status)}>{humanizeEnum(status)}</Badge>;
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
      gap="3"
      py="3"
      className="dashboard-row"
      style={{ borderBottom: 'var(--hairline)' }}
    >
      <Flex direction="column" gap="1" style={{ minWidth: 0, flex: 1 }}>
        <Text size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
          {application.name}
        </Text>
        <Text
          size="1"
          style={{
            color: 'var(--gray-11)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {application.email}
        </Text>
      </Flex>

      <Flex align="center" gap="3" style={{ flexShrink: 0 }}>
        <Flex align="center" gap="1" className="row-meta">
          <Clock style={{ width: 12, height: 12, color: 'var(--gray-9)' }} />
          <Text size="1" style={{ color: 'var(--gray-11)' }}>
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
      gap="3"
      py="3"
      className="dashboard-row"
      style={{ borderBottom: 'var(--hairline)' }}
    >
      <Flex direction="column" gap="1" style={{ minWidth: 0, flex: 1 }}>
        <Text size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
          {event.title}
        </Text>
        <Flex align="center" gap="2">
          <Text size="1" style={{ color: 'var(--gray-11)' }}>
            {event.organizer}
          </Text>
          <Text size="1" style={{ color: 'var(--gray-9)' }}>
            &middot;
          </Text>
          <Text size="1" style={{ color: 'var(--gray-11)' }}>
            {event.date}
          </Text>
        </Flex>
      </Flex>

      <Flex align="center" gap="3" style={{ flexShrink: 0 }}>
        <Flex align="center" gap="1" className="row-meta">
          <Label style={{ width: 12, height: 12, color: 'var(--gray-9)' }} />
          <Text size="1" className="ds-amount" style={{ color: 'var(--gray-11)' }}>
            {formatCount(event.ticketsSold)}
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
      <PageHeader
        title="Dashboard"
        description="Platform activity across events, organizers and revenue."
        actions={
          <Button
            variant="soft"
            size="2"
            data-testid="dashboard-date-range-button"
            icon={<Calendar style={{ width: 16, height: 16 }} />}
          >
            Last 30 days
          </Button>
        }
      />

      <Flex direction="column" gap="5">
        {/* Stat tiles — bento auto-fit, minmax(240px, 1fr). */}
        <Box className="ds-bento-grid">
          <StatCard
            title="Total events"
            value={formatCount(156)}
            trend="up"
            change="12%"
            changeLabel="from last month"
            icon={<Calendar style={{ width: 20, height: 20 }} />}
          />
          <StatCard
            title="Active organizers"
            value={formatCount(48)}
            trend="up"
            change="5"
            changeLabel="new this week"
            icon={<Group style={{ width: 20, height: 20 }} />}
          />
          <StatCard
            title="Tickets sold"
            value={<span className="ds-amount">{formatCount(12847)}</span>}
            trend="up"
            change="23%"
            changeLabel="from last month"
            icon={<Label style={{ width: 20, height: 20 }} />}
          />
          <StatCard
            title="Revenue"
            // Money: jade tone, Fira Code, always "K 2.4M".
            value={<Amount value={2_400_000} compact tone="money" />}
            trend="up"
            change="18%"
            changeLabel="from last month"
            icon={<CreditCard style={{ width: 20, height: 20 }} />}
          />
        </Box>

        {/* Activity panels — wider bento, minmax(340px, 1fr). */}
        <Box className="ds-bento-grid-wide">
          <SectionCard
            title="Recent organizer applications"
            minHeight="320px"
            action={
              <Link
                href="/organizers?status=pending"
                style={{ textDecoration: 'none' }}
              >
                <Button
                  variant="ghost"
                  size="1"
                  data-testid="dashboard-view-all-applications"
                  icon={<ArrowRight style={{ width: 14, height: 14 }} />}
                >
                  View all
                </Button>
              </Link>
            }
          >
            {recentApplications.length > 0 ? (
              <Flex direction="column">
                {recentApplications.map((app) => (
                  <ApplicationRow key={app.id} application={app} />
                ))}
              </Flex>
            ) : (
              <EmptyState
                size="sm"
                icon={<CheckCircle style={{ width: 20, height: 20 }} />}
                title="No applications waiting"
                description="New organizer applications will appear here for review."
              />
            )}
          </SectionCard>

          <SectionCard
            title="Recent events"
            minHeight="320px"
            action={
              <Link href="/events" style={{ textDecoration: 'none' }}>
                <Button
                  variant="ghost"
                  size="1"
                  data-testid="dashboard-view-all-events"
                  icon={<ArrowRight style={{ width: 14, height: 14 }} />}
                >
                  View all
                </Button>
              </Link>
            }
          >
            {recentEvents.length > 0 ? (
              <Flex direction="column">
                {recentEvents.map((event) => (
                  <EventRow key={event.id} event={event} />
                ))}
              </Flex>
            ) : (
              <EmptyState
                size="sm"
                icon={<Calendar style={{ width: 20, height: 20 }} />}
                title="No events yet"
                description="Events created by organizers will be listed here."
              />
            )}
          </SectionCard>
        </Box>

        {/* System alerts — status-toned toasts, no left-border accent cards. */}
        <SectionCard
          title="System alerts"
          action={<Badge color="amber">2 active</Badge>}
        >
          <Flex direction="column" gap="3">
            <Toast
              variant="warning"
              icon={<WarningTriangle style={{ width: 18, height: 18 }} />}
              title="3 payout requests are waiting for review"
              description="Organizers cannot receive funds until these are approved."
            />
            <Toast
              variant="info"
              icon={<Clock style={{ width: 18, height: 18 }} />}
              title="5 events start within 24 hours"
              description="Confirm ticket validation is online at each venue."
            />
          </Flex>
        </SectionCard>
      </Flex>

      <style jsx global>{`
        .dashboard-row:last-child {
          border-bottom: none;
        }
        .dashboard-row:hover {
          background-color: var(--gray-a3);
        }
        @media (max-width: 640px) {
          .row-meta {
            display: none;
          }
        }
      `}</style>
    </Box>
  );
}
