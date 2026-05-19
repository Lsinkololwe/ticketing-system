'use client';

import React from 'react';
import { Card, Box, Flex, Text, Progress, Badge, Grid, Heading } from '@radix-ui/themes';
import { EventStats as EventStatsType } from '@/types/event';
import { Calendar, User, Dollar } from 'iconoir-react';

// Custom icons since iconoir doesn't have these
const BarChartIcon = ({ style }: { style?: React.CSSProperties }) => (
  <svg style={style} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
    />
  </svg>
);

const TrendingUpIcon = ({ style }: { style?: React.CSSProperties }) => (
  <svg style={style} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
    />
  </svg>
);

interface EventStatsProps {
  stats: EventStatsType;
  isLoading?: boolean;
}

const EventStats: React.FC<EventStatsProps> = ({ stats, isLoading = false }) => {
  // Early return if stats is undefined or loading
  if (isLoading || !stats) {
    return (
      <Grid columns={{ initial: '1', md: '2', lg: '4' }} gap="6">
        {[...Array(4)].map((_, index) => (
          <Card key={index}>
            <Box p="4">
              <Box
                style={{
                  height: '1rem',
                  backgroundColor: 'var(--gray-4)',
                  borderRadius: 'var(--radius-2)',
                  marginBottom: '0.5rem',
                }}
              />
              <Box
                style={{
                  height: '2rem',
                  backgroundColor: 'var(--gray-4)',
                  borderRadius: 'var(--radius-2)',
                  marginBottom: '0.5rem',
                }}
              />
              <Box
                style={{
                  height: '0.75rem',
                  backgroundColor: 'var(--gray-4)',
                  borderRadius: 'var(--radius-2)',
                  width: '66%',
                }}
              />
            </Box>
          </Card>
        ))}
      </Grid>
    );
  }

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const formatNumber = (num: number) => {
    return new Intl.NumberFormat('en-US').format(num);
  };

  const getCompletionRate = () => {
    if (stats.totalEvents === 0) return 0;
    return Math.round((stats.completedEvents / stats.totalEvents) * 100);
  };

  const getSalesRate = () => {
    if (stats.totalCapacity === 0) return 0;
    return Math.round((stats.totalSoldTickets / stats.totalCapacity) * 100);
  };

  const getTopCategory = () => {
    if (!stats.eventsByCategory || stats.eventsByCategory.length === 0) return null;
    return stats.eventsByCategory.reduce((prev, current) =>
      prev.count > current.count ? prev : current
    );
  };

  const getTopOrganizer = () => {
    if (!stats.eventsByOrganizer || stats.eventsByOrganizer.length === 0) return null;
    return stats.eventsByOrganizer.reduce((prev, current) =>
      prev.eventCount > current.eventCount ? prev : current
    );
  };

  const topCategory = getTopCategory();
  const topOrganizer = getTopOrganizer();

  return (
    <Flex direction="column" gap="6">
      {/* Main Stats Cards */}
      <Grid columns={{ initial: '1', md: '2', lg: '4' }} gap="6">
        {/* Total Events */}
        <Card
          style={{
            background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
            color: 'white',
          }}
        >
          <Box p="4">
            <Flex justify="between" align="start">
              <Box>
                <Text size="2" style={{ opacity: 0.8 }} mb="1">
                  Total Events
                </Text>
                <Heading size="7" style={{ color: 'white' }}>
                  {formatNumber(stats.totalEvents)}
                </Heading>
                <Text size="2" style={{ opacity: 0.8 }} mt="1">
                  {stats.publishedEvents} published
                </Text>
              </Box>
              <Calendar style={{ width: '2rem', height: '2rem', opacity: 0.8 }} />
            </Flex>
          </Box>
        </Card>

        {/* Total Capacity */}
        <Card
          style={{
            background: 'linear-gradient(135deg, var(--blue-9), var(--blue-11))',
            color: 'white',
          }}
        >
          <Box p="4">
            <Flex justify="between" align="start">
              <Box>
                <Text size="2" style={{ opacity: 0.8 }} mb="1">
                  Total Capacity
                </Text>
                <Heading size="7" style={{ color: 'white' }}>
                  {formatNumber(stats.totalCapacity)}
                </Heading>
                <Text size="2" style={{ opacity: 0.8 }} mt="1">
                  {formatNumber(stats.totalSoldTickets)} sold
                </Text>
              </Box>
              <User style={{ width: '2rem', height: '2rem', opacity: 0.8 }} />
            </Flex>
          </Box>
        </Card>

        {/* Total Revenue */}
        <Card
          style={{
            background: 'linear-gradient(135deg, var(--green-9), var(--green-11))',
            color: 'white',
          }}
        >
          <Box p="4">
            <Flex justify="between" align="start">
              <Box>
                <Text size="2" style={{ opacity: 0.8 }} mb="1">
                  Total Revenue
                </Text>
                <Heading size="7" style={{ color: 'white' }}>
                  {formatCurrency(stats.totalRevenue)}
                </Heading>
                <Text size="2" style={{ opacity: 0.8 }} mt="1">
                  {getSalesRate()}% sold
                </Text>
              </Box>
              <Dollar style={{ width: '2rem', height: '2rem', opacity: 0.8 }} />
            </Flex>
          </Box>
        </Card>

        {/* Completion Rate */}
        <Card
          style={{
            background: 'linear-gradient(135deg, var(--orange-9), var(--orange-11))',
            color: 'white',
          }}
        >
          <Box p="4">
            <Flex justify="between" align="start">
              <Box>
                <Text size="2" style={{ opacity: 0.8 }} mb="1">
                  Completion Rate
                </Text>
                <Heading size="7" style={{ color: 'white' }}>
                  {getCompletionRate()}%
                </Heading>
                <Text size="2" style={{ opacity: 0.8 }} mt="1">
                  {stats.completedEvents} completed
                </Text>
              </Box>
              <BarChartIcon style={{ width: '2rem', height: '2rem', opacity: 0.8 }} />
            </Flex>
          </Box>
        </Card>
      </Grid>

      {/* Detailed Stats */}
      <Grid columns={{ initial: '1', lg: '2' }} gap="6">
        {/* Events by Category */}
        <Card size="2">
          <Box p="4">
            <Text size="4" weight="bold" mb="4" style={{ display: 'block' }}>
              Events by Category
            </Text>
            <Flex direction="column" gap="3">
              {stats.eventsByCategory.slice(0, 5).map((category) => (
                <Flex key={category.category} justify="between" align="center">
                  <Flex align="center" gap="3">
                    <Box
                      style={{
                        width: '0.75rem',
                        height: '0.75rem',
                        borderRadius: '50%',
                        backgroundColor: 'var(--accent-9)',
                      }}
                    />
                    <Text size="2">{category.category}</Text>
                  </Flex>
                  <Flex align="center" gap="2">
                    <Text size="2" weight="medium">
                      {category.count}
                    </Text>
                    <Badge variant="outline">{category.percentage.toFixed(1)}%</Badge>
                  </Flex>
                </Flex>
              ))}
            </Flex>
          </Box>
        </Card>

        {/* Events by Status */}
        <Card size="2">
          <Box p="4">
            <Text size="4" weight="bold" mb="4" style={{ display: 'block' }}>
              Events by Status
            </Text>
            <Flex direction="column" gap="3">
              {stats.eventsByStatus.map((status) => (
                <Box key={status.status}>
                  <Flex justify="between" mb="1">
                    <Text size="2">{status.status}</Text>
                    <Text size="2" weight="medium">
                      {status.count} ({status.percentage.toFixed(1)}%)
                    </Text>
                  </Flex>
                  <Progress value={status.percentage} size="1" />
                </Box>
              ))}
            </Flex>
          </Box>
        </Card>
      </Grid>

      {/* Top Performers */}
      <Grid columns={{ initial: '1', lg: '2' }} gap="6">
        {/* Top Category */}
        {topCategory && (
          <Card
            size="2"
            style={{ backgroundColor: 'var(--accent-2)', borderColor: 'var(--accent-6)' }}
          >
            <Box p="4">
              <Flex align="center" gap="3" mb="4">
                <TrendingUpIcon
                  style={{ width: '1.5rem', height: '1.5rem', color: 'var(--accent-9)' }}
                />
                <Text size="4" weight="bold">
                  Top Category
                </Text>
              </Flex>
              <Flex direction="column" align="center">
                <Heading size="5" color="iris" mb="2">
                  {topCategory.category}
                </Heading>
                <Text size="2" color="gray" mb="3">
                  {topCategory.count} events • {formatCurrency(topCategory.totalRevenue)} revenue
                </Text>
                <Badge color="iris">{topCategory.percentage.toFixed(1)}% of total</Badge>
              </Flex>
            </Box>
          </Card>
        )}

        {/* Top Organizer */}
        {topOrganizer && (
          <Card
            size="2"
            style={{ backgroundColor: 'var(--green-2)', borderColor: 'var(--green-6)' }}
          >
            <Box p="4">
              <Flex align="center" gap="3" mb="4">
                <TrendingUpIcon
                  style={{ width: '1.5rem', height: '1.5rem', color: 'var(--green-9)' }}
                />
                <Text size="4" weight="bold">
                  Top Organizer
                </Text>
              </Flex>
              <Flex direction="column" align="center">
                <Heading size="5" color="green" mb="2">
                  {topOrganizer.organizerName}
                </Heading>
                <Text size="2" color="gray" mb="3">
                  {topOrganizer.eventCount} events • {formatCurrency(topOrganizer.totalRevenue)}{' '}
                  revenue
                </Text>
                <Badge color="green">{formatNumber(topOrganizer.totalSoldTickets)} tickets sold</Badge>
              </Flex>
            </Box>
          </Card>
        )}
      </Grid>
    </Flex>
  );
};

export default EventStats;
