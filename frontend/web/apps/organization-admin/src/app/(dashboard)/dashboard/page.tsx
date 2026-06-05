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

import { Box, Flex, Text, Heading, Button, Card, Badge } from '@radix-ui/themes';
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
        borderRadius: '16px',
      }}
    >
      <Flex justify="between" align="start" mb="4">
        <Box
          style={{
            padding: '12px',
            borderRadius: '12px',
            background: 'rgba(16, 185, 129, 0.1)',
            border: '1px solid rgba(16, 185, 129, 0.2)',
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
          borderRadius: '12px',
          cursor: 'pointer',
          transition: 'all 200ms ease',
        }}
        className="quick-action-card"
      >
        <Flex align="center" gap="3">
          <Box
            style={{
              padding: '10px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
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
          borderRadius: '8px',
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
  const progress = (ticketsSold / ticketsTotal) * 100;
  const statusColors: Record<string, { bg: string; color: string }> = {
    published: { bg: 'rgba(16, 185, 129, 0.1)', color: 'var(--success-500)' },
    draft: { bg: 'rgba(245, 158, 11, 0.1)', color: 'var(--warning-500)' },
    ended: { bg: 'rgba(148, 163, 184, 0.1)', color: 'var(--content-muted)' },
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
              background: 'linear-gradient(90deg, var(--brand-500) 0%, var(--brand-400) 100%)',
              transition: 'width 300ms ease',
            }}
          />
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockMetrics = [
  {
    title: 'Total Revenue',
    value: 'K 125,430',
    change: 12.5,
    changeLabel: 'vs last month',
    icon: <CreditCard style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
    trend: 'up' as const,
  },
  {
    title: 'Tickets Sold',
    value: '2,847',
    change: 8.2,
    changeLabel: 'vs last month',
    icon: <StatsReport style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
    trend: 'up' as const,
  },
  {
    title: 'Active Events',
    value: '12',
    change: -2,
    changeLabel: '3 ending this week',
    icon: <Calendar style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
    trend: 'down' as const,
  },
  {
    title: 'Total Attendees',
    value: '8,234',
    change: 15.3,
    changeLabel: 'vs last month',
    icon: <Group style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />,
    trend: 'up' as const,
  },
];

const mockQuickActions = [
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

const mockActivity: ActivityItemProps[] = [
  { type: 'sale', message: '5 tickets sold for Tech Summit 2025', time: '2m ago' },
  { type: 'checkin', message: 'Guest checked in at Music Festival', time: '15m ago' },
  { type: 'event', message: 'Networking Night was published', time: '1h ago' },
  { type: 'payout', message: 'Payout of K 15,000 processed', time: '3h ago' },
  { type: 'sale', message: '12 tickets sold for Startup Pitch Day', time: '5h ago' },
];

const mockUpcomingEvents: UpcomingEventProps[] = [
  { id: '1', title: 'Tech Summit 2025', date: 'Dec 15, 2025', ticketsSold: 450, ticketsTotal: 500, status: 'published' },
  { id: '2', title: 'Music Festival', date: 'Dec 20, 2025', ticketsSold: 1200, ticketsTotal: 2000, status: 'published' },
  { id: '3', title: 'Networking Night', date: 'Dec 28, 2025', ticketsSold: 45, ticketsTotal: 100, status: 'draft' },
];

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function DashboardPage() {
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
        {mockMetrics.map((metric, index) => (
          <MetricCard key={index} {...metric} />
        ))}
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
          {mockQuickActions.map((action, index) => (
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
            borderRadius: '16px',
          }}
        >
          <Flex justify="between" align="center" mb="4">
            <Heading size="4" style={{ color: 'var(--content-primary)' }}>
              Recent Activity
            </Heading>
            <Button variant="ghost" size="1" asChild>
              <Link href="/activity">View All</Link>
            </Button>
          </Flex>
          <Box>
            {mockActivity.map((item, index) => (
              <ActivityItem key={index} {...item} />
            ))}
          </Box>
        </Card>

        {/* Upcoming Events */}
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
              Upcoming Events
            </Heading>
            <Button variant="ghost" size="1" asChild>
              <Link href="/events">View All</Link>
            </Button>
          </Flex>
          <Box>
            {mockUpcomingEvents.map((event) => (
              <UpcomingEventItem key={event.id} {...event} />
            ))}
          </Box>
        </Card>
      </Box>

      {/* Styles */}
      <style jsx global>{`
        .quick-action-card:hover {
          border-color: var(--brand-400);
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(16, 185, 129, 0.1);
        }
      `}</style>
    </Box>
  );
}
