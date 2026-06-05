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
} from 'iconoir-react';
import { PageHeader, StatCard } from '@/components/ui';
import { useOrganization } from '@/lib/contexts/OrganizationContext';

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

interface AudienceSegment {
  label: string;
  count: number;
  percentage: number;
  color: string;
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockDailyMetrics: DailyMetric[] = [
  { date: '2025-05-13', revenue: 1200, tickets: 12 },
  { date: '2025-05-14', revenue: 1850, tickets: 18 },
  { date: '2025-05-15', revenue: 2100, tickets: 21 },
  { date: '2025-05-16', revenue: 1650, tickets: 16 },
  { date: '2025-05-17', revenue: 2800, tickets: 28 },
  { date: '2025-05-18', revenue: 3200, tickets: 32 },
  { date: '2025-05-19', revenue: 2450, tickets: 24 },
];

const mockEventPerformance: EventPerformance[] = [
  {
    id: '1',
    name: 'Summer Music Festival',
    ticketsSold: 342,
    totalTickets: 500,
    revenue: 45600,
    views: 2840,
    conversionRate: 12.0,
  },
  {
    id: '2',
    name: 'Tech Conference 2025',
    ticketsSold: 156,
    totalTickets: 200,
    revenue: 23400,
    views: 1520,
    conversionRate: 10.3,
  },
  {
    id: '3',
    name: 'Food & Wine Expo',
    ticketsSold: 89,
    totalTickets: 300,
    revenue: 8900,
    views: 980,
    conversionRate: 9.1,
  },
  {
    id: '4',
    name: 'Art Gallery Opening',
    ticketsSold: 45,
    totalTickets: 100,
    revenue: 2250,
    views: 560,
    conversionRate: 8.0,
  },
];

const mockAudienceSegments: AudienceSegment[] = [
  { label: 'Returning Customers', count: 234, percentage: 45, color: 'var(--brand-500)' },
  { label: 'New Customers', count: 187, percentage: 36, color: '#3B82F6' },
  { label: 'VIP Members', count: 56, percentage: 11, color: '#8B5CF6' },
  { label: 'Early Bird Buyers', count: 42, percentage: 8, color: '#F59E0B' },
];

const mockTopCities = [
  { city: 'Lusaka', tickets: 289, percentage: 55 },
  { city: 'Kitwe', tickets: 98, percentage: 19 },
  { city: 'Ndola', tickets: 67, percentage: 13 },
  { city: 'Livingstone', tickets: 45, percentage: 9 },
  { city: 'Other', tickets: 21, percentage: 4 },
];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-ZM', {
    style: 'currency',
    currency: 'ZMW',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
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
  const { can } = useOrganization();
  const canViewAnalytics = can('viewAnalytics');

  const [dateRange, setDateRange] = useState('7days');

  // Calculate summary stats
  const summaryStats = useMemo(() => {
    const totalRevenue = mockDailyMetrics.reduce((sum, d) => sum + d.revenue, 0);
    const totalTickets = mockDailyMetrics.reduce((sum, d) => sum + d.tickets, 0);
    const avgRevenue = totalRevenue / mockDailyMetrics.length;
    const totalViews = mockEventPerformance.reduce((sum, e) => sum + e.views, 0);

    // Mock previous period for comparison
    const prevRevenue = totalRevenue * 0.85;
    const prevTickets = totalTickets * 0.9;

    return {
      totalRevenue,
      totalTickets,
      avgRevenue,
      totalViews,
      revenueGrowth: ((totalRevenue - prevRevenue) / prevRevenue) * 100,
      ticketsGrowth: ((totalTickets - prevTickets) / prevTickets) * 100,
      conversionRate: (totalTickets / totalViews) * 100,
    };
  }, []);

  if (!canViewAnalytics) {
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
            borderRadius: '16px',
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
          title="Total Revenue"
          value={formatCurrency(summaryStats.totalRevenue)}
          icon={<Dollar style={{ width: 20, height: 20 }} />}
          change={summaryStats.revenueGrowth}
          changeLabel="vs previous period"
        />
        <StatCard
          title="Tickets Sold"
          value={formatNumber(summaryStats.totalTickets)}
          icon={<Label style={{ width: 20, height: 20 }} />}
          change={summaryStats.ticketsGrowth}
          changeLabel="vs previous period"
        />
        <StatCard
          title="Page Views"
          value={formatNumber(summaryStats.totalViews)}
          icon={<Eye style={{ width: 20, height: 20 }} />}
          change={12}
          changeLabel="Unique visitors"
        />
        <StatCard
          title="Conversion Rate"
          value={`${summaryStats.conversionRate.toFixed(1)}%`}
          icon={<GraphUp style={{ width: 20, height: 20 }} />}
          change={0.5}
          changeLabel="Views to purchases"
        />
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
            borderRadius: '16px',
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
            <Badge color="green" variant="soft">
              <Flex align="center" gap="1">
                <ArrowUp style={{ width: 12, height: 12 }} />
                {summaryStats.revenueGrowth.toFixed(1)}%
              </Flex>
            </Badge>
          </Flex>
          <MiniChart data={mockDailyMetrics} dataKey="revenue" color="var(--brand-500)" />
        </Card>

        {/* Tickets Chart */}
        <Card
          style={{
            flex: 1,
            padding: '24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '16px',
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
            <Badge color="blue" variant="soft">
              <Flex align="center" gap="1">
                <ArrowUp style={{ width: 12, height: 12 }} />
                {summaryStats.ticketsGrowth.toFixed(1)}%
              </Flex>
            </Badge>
          </Flex>
          <MiniChart data={mockDailyMetrics} dataKey="tickets" color="#3B82F6" />
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
            borderRadius: '16px',
          }}
        >
          <Flex justify="between" align="center" mb="4">
            <Text size="4" weight="medium" style={{ color: 'var(--content-primary)' }}>
              Event Performance
            </Text>
            <Badge variant="soft" color="gray">
              {mockEventPerformance.length} events
            </Badge>
          </Flex>

          <Flex direction="column">
            {mockEventPerformance.map((event) => (
              <EventPerformanceRow key={event.id} event={event} />
            ))}
          </Flex>
        </Card>

        {/* Sidebar */}
        <Flex direction="column" gap="6" style={{ flex: 1 }}>
          {/* Audience Segments */}
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Text size="3" weight="medium" mb="4" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Audience Segments
            </Text>

            <Flex direction="column" gap="3">
              {mockAudienceSegments.map((segment) => (
                <Box key={segment.label}>
                  <Flex justify="between" mb="1">
                    <Flex align="center" gap="2">
                      <Box
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          background: segment.color,
                        }}
                      />
                      <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                        {segment.label}
                      </Text>
                    </Flex>
                    <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                      {segment.count}
                    </Text>
                  </Flex>
                  <Progress
                    value={segment.percentage}
                    max={100}
                    size="1"
                    style={{
                      '--progress-indicator-color': segment.color,
                    } as any}
                  />
                </Box>
              ))}
            </Flex>
          </Card>

          {/* Top Cities */}
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Text size="3" weight="medium" mb="4" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Top Locations
            </Text>

            <Flex direction="column" gap="3">
              {mockTopCities.map((city, index) => (
                <Flex key={city.city} justify="between" align="center">
                  <Flex align="center" gap="2">
                    <Box
                      style={{
                        width: 20,
                        height: 20,
                        borderRadius: '6px',
                        background: index === 0 ? 'var(--brand-500)' : 'var(--surface-subtle)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <Text
                        size="1"
                        weight="medium"
                        style={{ color: index === 0 ? 'white' : 'var(--content-muted)' }}
                      >
                        {index + 1}
                      </Text>
                    </Box>
                    <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                      {city.city}
                    </Text>
                  </Flex>
                  <Flex align="center" gap="2">
                    <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                      {city.tickets}
                    </Text>
                    <Text size="1" style={{ color: 'var(--content-muted)' }}>
                      ({city.percentage}%)
                    </Text>
                  </Flex>
                </Flex>
              ))}
            </Flex>
          </Card>

          {/* Quick Stats */}
          <Card
            style={{
              padding: '20px',
              background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(5, 150, 105, 0.1) 100%)',
              border: '1px solid rgba(16, 185, 129, 0.2)',
              borderRadius: '16px',
            }}
          >
            <Flex align="center" gap="3" mb="3">
              <GraphUp style={{ width: 24, height: 24, color: 'var(--brand-500)' }} />
              <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
                Performance Insight
              </Text>
            </Flex>
            <Text size="2" style={{ color: 'var(--content-secondary)', lineHeight: 1.5 }}>
              Your events have a <strong style={{ color: 'var(--brand-500)' }}>12% higher</strong> conversion rate
              than the platform average. Keep up the great work!
            </Text>
          </Card>
        </Flex>
      </Flex>
    </Box>
  );
}
