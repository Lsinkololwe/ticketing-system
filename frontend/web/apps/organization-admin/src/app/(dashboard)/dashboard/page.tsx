'use client';

/**
 * Organization Dashboard Home Page
 *
 * Overview dashboard for event organizers.
 * Features:
 * - Key metrics cards (sales, revenue, events, attendees)
 * - Quick actions for common tasks
 * - Recent activity feed
 * - Upcoming events list
 *
 * Future enhancements:
 * - Real-time data via GraphQL subscriptions
 * - Interactive charts with Recharts
 * - Customizable widget layout
 */

import { Box, Flex, Text, Heading, Button, Card, Badge, Skeleton } from '@radix-ui/themes';
import {
  Calendar,
  CreditCard,
  Group,
  StatsReport,
  Plus,
  ArrowRight,
  NavArrowUp,
  NavArrowDown,
  Clock,
  CheckCircle,
} from 'iconoir-react';
import Link from 'next/link';
import {
  useMyDashboardStats,
  useMyUpcomingEvents,
  useMyRecentActivity,
} from '@pml.tickets/shared/api/organization-admin/modules/dashboard';

// =============================================================================
// TYPES
// =============================================================================

interface MetricCardProps {
  title: string;
  value: string;
  change?: number;
  changeLabel?: string;
  icon: React.ReactNode;
  trend?: 'up' | 'down' | 'neutral';
}

interface QuickActionProps {
  title: string;
  description: string;
  href: string;
  icon: React.ReactNode;
}

interface ActivityItemProps {
  type: 'sale' | 'checkin' | 'event' | 'payout';
  message: string;
  time: string;
}

interface UpcomingEventProps {
  id: string;
  title: string;
  date: string;
  ticketsSold: number;
  ticketsTotal: number;
  status: 'published' | 'draft' | 'ended';
}

// =============================================================================
// COMPONENTS
// =============================================================================

function MetricCard({ title, value, change, changeLabel, icon, trend }: MetricCardProps) {
  const trendColor = trend === 'up' ? 'var(--success-500)' : trend === 'down' ? 'var(--error-500)' : 'var(--content-muted)';
  const TrendIcon = trend === 'up' ? NavArrowUp : trend === 'down' ? NavArrowDown : null;

  return (
    <Card
      style={{
        padding: '24px',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: 'var(--card-radius-bento)',
      }}
    >
      <Flex justify="between" align="start" mb="4">
        <Box
          style={{
            padding: '12px',
            borderRadius: 'var(--card-radius)',
            background: 'var(--accent-a3)',
            border: '1px solid var(--accent-a5)',
          }}
        >
          {icon}
        </Box>
        {change !== undefined && TrendIcon && (
          <Flex align="center" gap="1" style={{ color: trendColor }}>
            <TrendIcon style={{ width: 14, height: 14 }} />
            <Text size="2" weight="medium">
              {change > 0 ? '+' : ''}{change}%
            </Text>
          </Flex>
        )}
      </Flex>
      <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: 4 }}>
        {title}
      </Text>
      <Heading size="6" style={{ color: 'var(--content-primary)' }}>
        {value}
      </Heading>
      {changeLabel && (
        <Text size="1" style={{ color: 'var(--content-muted)', marginTop: 8, display: 'block' }}>
          {changeLabel}
        </Text>
      )}
    </Card>
  );
}

function QuickActionCard({ title, description, href, icon }: QuickActionProps) {
  return (
    <Link href={href} style={{ textDecoration: 'none' }}>
      <Card
        style={{
          padding: '20px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: 'var(--card-radius)',
          cursor: 'pointer',
          transition: 'all 200ms ease',
        }}
        className="quick-action-card"
      >
        <Flex align="center" gap="3">
          <Box
            style={{
              padding: '10px',
              borderRadius: 'var(--radius-3)',
              background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
              flexShrink: 0,
            }}
          >
            {icon}
          </Box>
          <Box style={{ flex: 1 }}>
            <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
              {title}
            </Text>
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              {description}
            </Text>
          </Box>
          <ArrowRight style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
        </Flex>
      </Card>
    </Link>
  );
}

function ActivityItem({ type, message, time }: ActivityItemProps) {
  const iconMap = {
    sale: <CreditCard style={{ width: 14, height: 14, color: 'var(--success-500)' }} />,
    checkin: <CheckCircle style={{ width: 14, height: 14, color: 'var(--brand-500)' }} />,
    event: <Calendar style={{ width: 14, height: 14, color: 'var(--warning-500)' }} />,
    payout: <NavArrowUp style={{ width: 14, height: 14, color: 'var(--info-500)' }} />,
  };

  return (
    <Flex align="center" gap="3" py="3" style={{ borderBottom: '1px solid var(--surface-border)' }}>
      <Box
        style={{
          width: 32,
          height: 32,
          borderRadius: 'var(--radius-4)',
          background: 'var(--surface-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        {iconMap[type]}
      </Box>
      <Box style={{ flex: 1 }}>
        <Text size="2" style={{ color: 'var(--content-primary)' }}>{message}</Text>
      </Box>
      <Text size="1" style={{ color: 'var(--content-muted)' }}>{time}</Text>
    </Flex>
  );
}

function UpcomingEventItem({ title, date, ticketsSold, ticketsTotal, status }: UpcomingEventProps) {
  const progress = ticketsTotal > 0 ? (ticketsSold / ticketsTotal) * 100 : 0;
  const statusColors: Record<string, { bg: string; color: string }> = {
    published: { bg: 'var(--accent-a3)', color: 'var(--success-500)' },
    draft: { bg: 'var(--status-warning-a3)', color: 'var(--warning-500)' },
    ended: { bg: 'var(--gray-a5)', color: 'var(--content-muted)' },
  };

  return (
    <Box py="3" style={{ borderBottom: '1px solid var(--surface-border)' }}>
      <Flex justify="between" align="start" mb="2">
        <Box>
          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
            {title}
          </Text>
          <Flex align="center" gap="2" mt="1">
            <Clock style={{ width: 12, height: 12, color: 'var(--content-muted)' }} />
            <Text size="1" style={{ color: 'var(--content-muted)' }}>{date}</Text>
          </Flex>
        </Box>
        <Badge
          style={{
            background: statusColors[status].bg,
            color: statusColors[status].color,
            textTransform: 'capitalize',
          }}
        >
          {status}
        </Badge>
      </Flex>
      <Box mt="3">
        <Flex justify="between" mb="1">
          <Text size="1" style={{ color: 'var(--content-muted)' }}>Tickets Sold</Text>
          <Text size="1" weight="medium" style={{ color: 'var(--content-primary)' }}>
            {ticketsSold} / {ticketsTotal}
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
              transition: 'width 300ms ease',
            }}
          />
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// HELPERS
// =============================================================================

const QUICK_ACTIONS: QuickActionProps[] = [
  {
    title: 'Create Event',
    description: 'Start a new event from scratch',
    href: '/events/new',
    icon: <Plus style={{ width: 18, height: 18, color: 'white' }} />,
  },
  {
    title: 'View Analytics',
    description: 'Check your performance metrics',
    href: '/analytics',
    icon: <StatsReport style={{ width: 18, height: 18, color: 'white' }} />,
  },
  {
    title: 'Team Settings',
    description: 'Manage team members and roles',
    href: '/team',
    icon: <Group style={{ width: 18, height: 18, color: 'white' }} />,
  },
];

function trendOf(change?: number | null): 'up' | 'down' | 'neutral' {
  if (change == null || change === 0) return 'neutral';
  return change > 0 ? 'up' : 'down';
}

/** Format a BigDecimal string as money with a leading symbol (K for ZMW). */
function formatMoney(amount?: string | null, currency?: string | null): string {
  const n = Number(amount ?? 0);
  const symbol = !currency || currency === 'ZMW' ? 'K' : currency;
  return `${symbol} ${n.toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
}

/** Map the backend activity enum to the local icon category. */
function activityCategory(type: string): ActivityItemProps['type'] {
  switch (type) {
    case 'TICKET_SALE':
      return 'sale';
    case 'CHECK_IN':
      return 'checkin';
    case 'PAYOUT_COMPLETED':
    case 'PAYOUT_REQUESTED':
    case 'REFUND_PROCESSED':
      return 'payout';
    default:
      return 'event';
  }
}

/** Coerce the backend status string to the local badge status. */
function eventStatus(status: string): UpcomingEventProps['status'] {
  const s = (status || '').toLowerCase();
  if (s === 'published') return 'published';
  if (s === 'ended') return 'ended';
  return 'draft';
}

function formatEventDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  } catch {
    return '';
  }
}

function formatRelativeTime(iso: string): string {
  try {
    const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.round(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.round(hrs / 24)}d ago`;
  } catch {
    return '';
  }
}

/** Simple inline empty state for dashboard cards. */
function CardEmptyState({ message, cta }: { message: string; cta?: { label: string; href: string } }) {
  return (
    <Flex direction="column" align="center" justify="center" gap="3" py="6" style={{ textAlign: 'center' }}>
      <Text size="2" style={{ color: 'var(--content-muted)' }}>{message}</Text>
      {cta && (
        <Button size="1" variant="soft" asChild data-testid="dashboard-empty-cta">
          <Link href={cta.href}>{cta.label}</Link>
        </Button>
      )}
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function DashboardPage() {
  const { stats, loading: statsLoading } = useMyDashboardStats();
  const { events, loading: eventsLoading } = useMyUpcomingEvents(5);
  const { activity, loading: activityLoading } = useMyRecentActivity(5);

  const metrics: MetricCardProps[] = stats
    ? [
        {
          title: 'Total Revenue',
          value: formatMoney(stats.totalRevenue, stats.revenueCurrency),
          change: stats.revenueChange ?? undefined,
          changeLabel: 'vs last month',
          icon: <CreditCard style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
          trend: trendOf(stats.revenueChange),
        },
        {
          title: 'Tickets Sold',
          value: (stats.totalTicketsSold ?? 0).toLocaleString(),
          change: stats.ticketsSoldChange ?? undefined,
          changeLabel: 'vs last month',
          icon: <StatsReport style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
          trend: trendOf(stats.ticketsSoldChange),
        },
        {
          title: 'Active Events',
          value: String(stats.activeEvents ?? 0),
          change: stats.eventsChange ?? undefined,
          changeLabel: `${stats.eventsEndingThisWeek ?? 0} ending this week`,
          icon: <Calendar style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
          trend: trendOf(stats.eventsChange),
        },
        {
          title: 'Total Attendees',
          value: (stats.totalAttendees ?? 0).toLocaleString(),
          change: stats.attendeesChange ?? undefined,
          changeLabel: 'vs last month',
          icon: <Group style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
          trend: trendOf(stats.attendeesChange),
        },
      ]
    : [];

  const metricsLoading = statsLoading && !stats;

  return (
    <Box>
      {/* Page Header */}
      <Box mb="6">
        <Heading size="6" mb="1" style={{ color: 'var(--content-primary)' }}>
          Dashboard
        </Heading>
        <Text size="2" style={{ color: 'var(--content-muted)' }}>
          Welcome back! Here&apos;s an overview of your organization.
        </Text>
      </Box>

      {/* Metrics Grid */}
      <Box
        mb="6"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '16px',
        }}
      >
        {metricsLoading
          ? Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} style={{ height: 148, borderRadius: 16 }} />
            ))
          : metrics.map((metric, index) => <MetricCard key={index} {...metric} />)}
      </Box>

      {/* Quick Actions */}
      <Box mb="6">
        <Heading size="4" mb="4" style={{ color: 'var(--content-primary)' }}>
          Quick Actions
        </Heading>
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '12px',
          }}
        >
          {QUICK_ACTIONS.map((action, index) => (
            <QuickActionCard key={index} {...action} />
          ))}
        </Box>
      </Box>

      {/* Two-Column Layout */}
      <Box
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
          gap: '24px',
        }}
      >
        {/* Recent Activity */}
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
              Recent Activity
            </Heading>
            <Button variant="ghost" size="1" asChild data-testid="dashboard-activity-view-all">
              <Link href="/activity">View All</Link>
            </Button>
          </Flex>
          <Box>
            {activityLoading && activity.length === 0 ? (
              <Skeleton style={{ height: 120, borderRadius: 8 }} />
            ) : activity.length === 0 ? (
              <CardEmptyState message="No recent activity yet. Sales and check-ins will appear here." />
            ) : (
              activity.map((item) => (
                <ActivityItem
                  key={item.id}
                  type={activityCategory(item.type)}
                  message={item.message}
                  time={formatRelativeTime(item.timestamp)}
                />
              ))
            )}
          </Box>
        </Card>

        {/* Upcoming Events */}
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
              Upcoming Events
            </Heading>
            <Button variant="ghost" size="1" asChild data-testid="dashboard-events-view-all">
              <Link href="/events">View All</Link>
            </Button>
          </Flex>
          <Box>
            {eventsLoading && events.length === 0 ? (
              <Skeleton style={{ height: 120, borderRadius: 8 }} />
            ) : events.length === 0 ? (
              <CardEmptyState
                message="No events yet. Create your first draft to get started."
                cta={{ label: 'Create event', href: '/events/new' }}
              />
            ) : (
              events.map((event) => (
                <UpcomingEventItem
                  key={event.id}
                  id={event.id}
                  title={event.title}
                  date={formatEventDate(event.eventDateTime)}
                  ticketsSold={event.ticketsSold}
                  ticketsTotal={event.totalCapacity}
                  status={eventStatus(event.status)}
                />
              ))
            )}
          </Box>
        </Card>
      </Box>

      {/* Styles */}
      <style jsx global>{`
        .quick-action-card:hover {
          border-color: var(--brand-400);
          transform: translateY(-2px);
          box-shadow: 0 4px 12px var(--accent-a3);
        }
      `}</style>
    </Box>
  );
}
