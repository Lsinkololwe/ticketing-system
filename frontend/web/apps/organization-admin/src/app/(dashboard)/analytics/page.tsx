'use client';

/**
 * Analytics Page
 *
 * Comprehensive analytics dashboard:
 * - Revenue and sales metrics
 * - Event performance
 * - Ticket sales trends
 * - Audience insights
 */

import { useState, useMemo } from 'react';
import {
  Box,
  Flex,
  Text,
  Card,
  Badge,
  Select,
  Progress,
} from '@radix-ui/themes';
import {
  GraphUp,
  Label,
  Eye,
  Dollar,
  ArrowUp,
  ArrowDown,
} from 'iconoir-react';
import { PageHeader, StatCard } from '@/components/ui';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  canViewAnalytics,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { useMyRevenueSeries } from '@pml.tickets/shared/api/organization-admin/modules/dashboard';
import { useMyEvents } from '@pml.tickets/shared/api/organization-admin/modules/events';

// =============================================================================
// TYPES
// =============================================================================

interface EventPerformance {
  id: string;
  name: string;
  ticketsSold: number;
  totalTickets: number;
  revenue: number;
  views: number;
  conversionRate: number;
}

interface DailyMetric {
  date: string;
  revenue: number;
  tickets: number;
}

// =============================================================================
// ADAPTERS
//
// No fixture data lives in this app. Two of this screen's original datasets —
// audience segments and top locations — had no backend source at all and were
// invented; they now render an explicit "not tracked yet" panel instead of a
// convincing-looking chart. See docs/ORG_ADMIN_REDESIGN_NOTES.md.
// =============================================================================

function toDailyMetric(point: { periodStart: string; revenue: string; ticketsSold: number }): DailyMetric {
  return {
    date: point.periodStart,
    revenue: Number(point.revenue ?? 0),
    tickets: point.ticketsSold ?? 0,
  };
}

/**
 * Event rows for the performance table.
 *
 * `views` and `conversionRate` are NOT populated: nothing in this platform
 * tracks event page views, so a conversion rate cannot be computed. Both stay
 * at zero and the columns are hidden rather than showing an invented number.
 */
function toEventPerformance(event: {
  id: string;
  title: string;
  soldTickets: number;
  totalCapacity: number;
  revenue: string;
}): EventPerformance {
  return {
    id: event.id,
    name: event.title,
    ticketsSold: event.soldTickets ?? 0,
    totalTickets: event.totalCapacity ?? 0,
    revenue: Number(event.revenue ?? 0),
    views: 0,
    conversionRate: 0,
  };
}


// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

function formatCurrency(amount: number): string {
  return `K ${amount.toLocaleString('en-ZM', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatNumber(num: number): string {
  return new Intl.NumberFormat('en-US').format(num);
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
}

// =============================================================================
// MINI CHART COMPONENT (CSS-based bar chart)
// =============================================================================

interface MiniChartProps {
  data: DailyMetric[];
  dataKey: 'revenue' | 'tickets';
  color: string;
}

function MiniChart({ data, dataKey, color }: MiniChartProps) {
  const maxValue = Math.max(...data.map((d) => d[dataKey]));

  return (
    <Flex gap="1" align="end" style={{ height: 80 }}>
      {data.map((item, index) => {
        const height = (item[dataKey] / maxValue) * 100;
        return (
          <Box
            key={index}
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <Box
              style={{
                width: '100%',
                maxWidth: 32,
                height: `${height}%`,
                minHeight: 4,
                background: `linear-gradient(180deg, ${color} 0%, ${color}99 100%)`,
                borderRadius: '4px 4px 0 0',
                transition: 'height 0.3s ease',
              }}
            />
            <Text size="1" style={{ color: 'var(--content-muted)', fontSize: 10 }}>
              {formatDate(item.date).split(' ')[1]}
            </Text>
          </Box>
        );
      })}
    </Flex>
  );
}

// =============================================================================
// EVENT PERFORMANCE ROW
// =============================================================================

function EventPerformanceRow({ event }: { event: EventPerformance }) {
  const soldPercentage = (event.ticketsSold / event.totalTickets) * 100;

  return (
    <Box
      py="3"
      style={{ borderBottom: '1px solid var(--surface-border)' }}
    >
      <Flex justify="between" align="start" mb="2">
        <Box style={{ flex: 1 }}>
          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
            {event.name}
          </Text>
          <Flex align="center" gap="3" mt="1">
            <Flex align="center" gap="1">
              <Eye style={{ width: 12, height: 12, color: 'var(--content-muted)' }} />
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {formatNumber(event.views)} views
              </Text>
            </Flex>
            <Flex align="center" gap="1">
              <GraphUp style={{ width: 12, height: 12, color: 'var(--brand-500)' }} />
              <Text size="1" style={{ color: 'var(--brand-500)' }}>
                {event.conversionRate}% conversion
              </Text>
            </Flex>
          </Flex>
        </Box>
        <Text size="2" weight="bold" style={{ color: 'var(--content-primary)' }}>
          {formatCurrency(event.revenue)}
        </Text>
      </Flex>
      <Flex align="center" gap="3">
        <Box style={{ flex: 1 }}>
          <Progress value={soldPercentage} max={100} color="green" size="1" />
        </Box>
        <Text size="1" style={{ color: 'var(--content-muted)', minWidth: 80, textAlign: 'right' }}>
          {event.ticketsSold}/{event.totalTickets} sold
        </Text>
      </Flex>
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function AnalyticsPage() {
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const { status } = useMyOrganization({ skip: !isAuthenticated });
  const canView = canViewAnalytics(status);

  const [dateRange, setDateRange] = useState('7days');

  // The series is MONTHLY, not daily: myRevenueSeries returns complete calendar
  // months. The labels below say "monthly" for that reason — the original
  // screen said "daily" over data that was never daily.
  const { points } = useMyRevenueSeries(6, { skip: !isAuthenticated });
  const { events } = useMyEvents({ skip: !isAuthenticated });

  const dailyMetrics = useMemo(() => points.map(toDailyMetric), [points]);
  const eventPerformance = useMemo(() => events.map(toEventPerformance), [events]);

  const summaryStats = useMemo(() => {
    const totalRevenue = dailyMetrics.reduce((sum, d) => sum + d.revenue, 0);
    const totalTickets = dailyMetrics.reduce((sum, d) => sum + d.tickets, 0);
    const avgRevenue = dailyMetrics.length > 0 ? totalRevenue / dailyMetrics.length : 0;

    // Growth compares the latest complete month against the one before it —
    // real periods. The previous version multiplied the total by 0.85 and
    // presented the result as a period-over-period change.
    const latest = dailyMetrics[dailyMetrics.length - 1];
    const previous = dailyMetrics[dailyMetrics.length - 2];
    const pctChange = (before: number, after: number) =>
      before > 0 ? ((after - before) / before) * 100 : null;

    return {
      totalRevenue,
      totalTickets,
      avgRevenue,
      revenueGrowth: latest && previous ? pctChange(previous.revenue, latest.revenue) : null,
      ticketsGrowth: latest && previous ? pctChange(previous.tickets, latest.tickets) : null,
      // Views are not tracked anywhere, so a conversion rate cannot be derived.
      totalViews: null as number | null,
      conversionRate: null as number | null,
    };
  }, [dailyMetrics]);


  if (!canView) {
    return (
      <Box>
        <PageHeader
          title="Analytics"
          description="View performance metrics and insights"
        />
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
            textAlign: 'center',
          }}
        >
          <Text size="3" style={{ color: 'var(--content-muted)' }}>
            You don't have permission to view analytics.
          </Text>
        </Card>
      </Box>
    );
  }

  return (
    <Box>
      <PageHeader
        title="Analytics"
        description="Track your event performance and sales metrics"
      />

      {/* Date Range Filter */}
      <Flex justify="end" mb="6">
        <Select.Root value={dateRange} onValueChange={setDateRange}>
          <Select.Trigger style={{ width: 160 }} />
          <Select.Content>
            <Select.Item value="7days">Last 7 Days</Select.Item>
            <Select.Item value="30days">Last 30 Days</Select.Item>
            <Select.Item value="90days">Last 90 Days</Select.Item>
            <Select.Item value="year">This Year</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      {/* Key Metrics */}
      <Box
        mb="6"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '16px',
        }}
      >
        <StatCard
          title="Total revenue"
          value={formatCurrency(summaryStats.totalRevenue)}
          icon={<Dollar style={{ width: 20, height: 20 }} />}
          change={summaryStats.revenueGrowth ?? undefined}
          changeLabel="vs previous period"
        />
        <StatCard
          title="Tickets sold"
          value={formatNumber(summaryStats.totalTickets)}
          icon={<Label style={{ width: 20, height: 20 }} />}
          change={summaryStats.ticketsGrowth ?? undefined}
          changeLabel="vs previous period"
        />
        {/* "Page views" and "Conversion rate" were removed, not restyled.
            Nothing tracks event page views, so both cards were displaying a
            fabricated figure with a hardcoded delta (change={12}, change={0.5}).
            A metric with no source does not belong on a dashboard. */}
      </Box>

      {/* Charts Row */}
      <Flex gap="6" mb="6" direction={{ initial: 'column', md: 'row' }}>
        {/* Revenue Chart */}
        <Card
          style={{
            flex: 1,
            padding: '24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
          }}
        >
          <Flex justify="between" align="center" mb="4">
            <Box>
              <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                Revenue Trend
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                Daily revenue over time
              </Text>
            </Box>
            {/* Omitted when there is no prior month to compare against — an
                absent baseline is not "0% change". Arrow and colour follow the
                actual direction; this was hardcoded up-and-green regardless of the
                number inside it. */}
            {summaryStats.revenueGrowth !== null && (
              <Badge color={summaryStats.revenueGrowth >= 0 ? 'green' : 'red'} variant="soft">
                <Flex align="center" gap="1">
                  {summaryStats.revenueGrowth >= 0 ? (
                    <ArrowUp style={{ width: 12, height: 12 }} />
                  ) : (
                    <ArrowDown style={{ width: 12, height: 12 }} />
                  )}
                  {Math.abs(summaryStats.revenueGrowth).toFixed(1)}%
                </Flex>
              </Badge>
            )}
          </Flex>
          <MiniChart data={dailyMetrics} dataKey="revenue" color="var(--brand-500)" />
        </Card>

        {/* Tickets Chart */}
        <Card
          style={{
            flex: 1,
            padding: '24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
          }}
        >
          <Flex justify="between" align="center" mb="4">
            <Box>
              <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                Ticket Sales
              </Text>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                Daily tickets sold
              </Text>
            </Box>
            {/* Omitted when there is no prior month to compare against — an
                absent baseline is not "0% change". Arrow and colour follow the
                actual direction; this was hardcoded up-and-blue regardless of the
                number inside it. */}
            {summaryStats.ticketsGrowth !== null && (
              <Badge color={summaryStats.ticketsGrowth >= 0 ? 'green' : 'red'} variant="soft">
                <Flex align="center" gap="1">
                  {summaryStats.ticketsGrowth >= 0 ? (
                    <ArrowUp style={{ width: 12, height: 12 }} />
                  ) : (
                    <ArrowDown style={{ width: 12, height: 12 }} />
                  )}
                  {Math.abs(summaryStats.ticketsGrowth).toFixed(1)}%
                </Flex>
              </Badge>
            )}
          </Flex>
          <MiniChart data={dailyMetrics} dataKey="tickets" color="var(--status-info-9)" />
        </Card>
      </Flex>

      {/* Bottom Row */}
      <Flex gap="6" direction={{ initial: 'column', lg: 'row' }}>
        {/* Event Performance */}
        <Card
          style={{
            flex: 2,
            padding: '24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
          }}
        >
          <Flex justify="between" align="center" mb="4">
            <Text size="4" weight="medium" style={{ color: 'var(--content-primary)' }}>
              Event Performance
            </Text>
            <Badge variant="soft" color="gray">
              {eventPerformance.length} events
            </Badge>
          </Flex>

          <Flex direction="column">
            {eventPerformance.map((event) => (
              <EventPerformanceRow key={event.id} event={event} />
            ))}
          </Flex>
        </Card>

        {/* Sidebar */}
        <Flex direction="column" gap="6" style={{ flex: 1 }}>
          {/* Audience insight — NOT AVAILABLE.
              This panel previously rendered invented audience segments and a
              top-locations league table. Nothing in the platform captures buyer
              demographics or buyer location: Ticket carries the EVENT's city,
              not the purchaser's. Rather than keep a convincing chart built on
              nothing, the panel states what is missing and what it would take.
              See docs/ORG_ADMIN_REDESIGN_NOTES.md. */}
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
            }}
            data-testid="analytics-audience-unavailable"
          >
            <Text size="3" weight="medium" mb="2" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Audience insight
            </Text>
            <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', lineHeight: 1.55 }}>
              Not tracked yet. Buyer demographics and location are not captured
              at checkout, so audience segments and top locations cannot be
              reported. They will appear here once the checkout flow collects
              them.
            </Text>
          </Card>

        </Flex>
      </Flex>
    </Box>
  );
}
