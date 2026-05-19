# Phase 2: Dashboard & Overview

## Overview
Implement role-based dashboard home pages with real-time statistics, quick actions, and activity feeds.

---

## Task 2.1: Dashboard Overview Page

### Description
Create the main dashboard page that adapts content based on user role.

### Backend Requirements

#### GraphQL Schema: `backend/gateway/src/main/resources/graphql/dashboard.graphqls`

```graphql
type DashboardStats {
  totalEvents: Int!
  activeEvents: Int!
  totalBookings: Int!
  pendingBookings: Int!
  totalRevenue: Float!
  todayRevenue: Float!
  totalUsers: Int!
  newUsersToday: Int!
  ticketsSoldToday: Int!
  upcomingEvents: Int!
}

type RecentActivity {
  id: ID!
  type: ActivityType!
  message: String!
  timestamp: DateTime!
  entityId: String
  entityType: String
}

enum ActivityType {
  ORDER_PLACED
  ORDER_REFUNDED
  EVENT_CREATED
  EVENT_PUBLISHED
  USER_REGISTERED
  TICKET_SCANNED
}

type Query {
  dashboardStats: DashboardStats! @hasPermission(permission: "dashboard.read")
  recentActivity(limit: Int = 10): [RecentActivity!]! @hasPermission(permission: "dashboard.read")
}
```

#### Service: `backend/gateway/src/main/java/pml/tickets/gateway/service/DashboardService.java`

```java
@Service
public class DashboardService {

    private final EventClient eventClient;
    private final BookingClient bookingClient;
    private final UserClient userClient;

    @Cacheable(value = "dashboardStats", key = "#root.method.name")
    public DashboardStats getDashboardStats() {
        return DashboardStats.builder()
            .totalEvents(eventClient.countAll())
            .activeEvents(eventClient.countActive())
            .totalBookings(bookingClient.countAll())
            .pendingBookings(bookingClient.countByStatus(BookingStatus.PENDING))
            .totalRevenue(bookingClient.sumRevenue())
            .todayRevenue(bookingClient.sumTodayRevenue())
            .totalUsers(userClient.countAll())
            .newUsersToday(userClient.countTodayRegistrations())
            .ticketsSoldToday(bookingClient.countTodayTickets())
            .upcomingEvents(eventClient.countUpcoming())
            .build();
    }
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { Box, Heading, Text, Flex, Spinner } from '@radix-ui/themes';
import { Calendar, ShoppingCart, Users, DollarSign, Ticket, TrendingUp } from 'lucide-react';
import { BentoGrid, StatCard } from '@/components/ui/BentoGrid';
import { RecentActivityFeed } from '@/components/dashboard/RecentActivityFeed';
import { QuickActions } from '@/components/dashboard/QuickActions';
import { UpcomingEventsCard } from '@/components/dashboard/UpcomingEventsCard';
import { RevenueChart } from '@/components/dashboard/RevenueChart';
import { PermissionGuard } from '@/components/auth/PermissionGuard';
import { DASHBOARD_STATS_QUERY } from '@/lib/graphql/queries/dashboard';

export default function DashboardPage() {
  const { data, loading, error } = useQuery(DASHBOARD_STATS_QUERY, {
    fetchPolicy: 'cache-and-network',
    pollInterval: 60000, // Refresh every minute
  });

  if (loading && !data) {
    return (
      <Flex justify="center" align="center" style={{ minHeight: '400px' }}>
        <Spinner size="3" />
      </Flex>
    );
  }

  const stats = data?.dashboardStats;

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Dashboard</Heading>
          <Text color="gray" size="2">Welcome back! Here's what's happening today.</Text>
        </Box>
      </Flex>

      <BentoGrid>
        {/* Large Revenue Card - spans 2 columns */}
        <PermissionGuard permission="finance.read">
          <Box gridColumn={{ lg: "span 2" }} gridRow={{ lg: "span 2" }}>
            <RevenueChart data={stats?.revenueHistory} />
          </Box>
        </PermissionGuard>

        {/* Stat Cards */}
        <StatCard
          title="Total Revenue"
          value={`$${stats?.totalRevenue?.toLocaleString() ?? 0}`}
          change={{ value: 12, trend: 'up' }}
          icon={<DollarSign size={18} />}
          color="green"
        />

        <StatCard
          title="Bookings Today"
          value={stats?.totalBookings ?? 0}
          change={{ value: 8, trend: 'up' }}
          icon={<ShoppingCart size={18} />}
          color="blue"
        />

        <StatCard
          title="Active Events"
          value={stats?.activeEvents ?? 0}
          icon={<Calendar size={18} />}
          color="purple"
        />

        <StatCard
          title="Tickets Sold Today"
          value={stats?.ticketsSoldToday ?? 0}
          change={{ value: 15, trend: 'up' }}
          icon={<Ticket size={18} />}
          color="orange"
        />

        <PermissionGuard permission="user.read">
          <StatCard
            title="Total Users"
            value={stats?.totalUsers?.toLocaleString() ?? 0}
            change={{ value: 5, trend: 'up' }}
            icon={<Users size={18} />}
            color="blue"
          />
        </PermissionGuard>

        {/* Upcoming Events Card - spans 2 columns */}
        <Box gridColumn={{ lg: "span 2" }}>
          <UpcomingEventsCard />
        </Box>

        {/* Quick Actions */}
        <QuickActions />

        {/* Recent Activity - spans 2 columns */}
        <Box gridColumn={{ lg: "span 2" }}>
          <RecentActivityFeed />
        </Box>
      </BentoGrid>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Dashboard loads stats from GraphQL
- [ ] Stats refresh periodically (every 60s)
- [ ] Bento grid layout adapts to screen size
- [ ] Permission-protected sections are hidden for unauthorized users
- [ ] Loading state shows spinner
- [ ] Error state displays error message

---

## Task 2.2: Revenue Chart Component

### Description
Create an interactive revenue chart for the dashboard.

### Backend Requirements

#### Add to GraphQL Schema

```graphql
type RevenueDataPoint {
  date: Date!
  amount: Float!
}

extend type Query {
  revenueHistory(period: String = "7d"): [RevenueDataPoint!]! @hasPermission(permission: "finance.read")
}
```

### Frontend Implementation

#### File: `apps/admin/src/components/dashboard/RevenueChart.tsx`

```tsx
'use client';

import { Card, Flex, Heading, Text, Tabs, Box } from '@radix-ui/themes';
import { useState } from 'react';

interface RevenueDataPoint {
  date: string;
  amount: number;
}

interface RevenueChartProps {
  data?: RevenueDataPoint[];
}

export function RevenueChart({ data = [] }: RevenueChartProps) {
  const [period, setPeriod] = useState<'7d' | '30d' | '90d'>('7d');

  const maxAmount = Math.max(...data.map(d => d.amount), 1);

  return (
    <Card style={{ height: '100%' }}>
      <Flex direction="column" p="4" style={{ height: '100%' }}>
        <Flex justify="between" align="center" mb="4">
          <Box>
            <Text size="2" color="gray">Total Revenue</Text>
            <Heading size="5">
              ${data.reduce((sum, d) => sum + d.amount, 0).toLocaleString()}
            </Heading>
          </Box>
          <Tabs.Root value={period} onValueChange={(v) => setPeriod(v as typeof period)}>
            <Tabs.List size="1">
              <Tabs.Trigger value="7d">7D</Tabs.Trigger>
              <Tabs.Trigger value="30d">30D</Tabs.Trigger>
              <Tabs.Trigger value="90d">90D</Tabs.Trigger>
            </Tabs.List>
          </Tabs.Root>
        </Flex>

        {/* Simple bar chart */}
        <Flex gap="1" align="end" style={{ flex: 1, minHeight: '150px' }}>
          {data.map((point, i) => (
            <Box
              key={i}
              style={{
                flex: 1,
                height: `${(point.amount / maxAmount) * 100}%`,
                backgroundColor: 'var(--accent-9)',
                borderRadius: 'var(--radius-1)',
                minHeight: '4px',
              }}
              title={`${point.date}: $${point.amount.toLocaleString()}`}
            />
          ))}
        </Flex>

        {/* X-axis labels */}
        <Flex justify="between" mt="2">
          <Text size="1" color="gray">{data[0]?.date}</Text>
          <Text size="1" color="gray">{data[data.length - 1]?.date}</Text>
        </Flex>
      </Flex>
    </Card>
  );
}
```

### Acceptance Criteria
- [ ] Chart displays revenue data as bar graph
- [ ] Period tabs switch between 7D, 30D, 90D views
- [ ] Total revenue is calculated and displayed
- [ ] Hover shows individual bar values
- [ ] Chart scales properly to container

---

## Task 2.3: Quick Actions Component

### Description
Create a quick actions card with common admin tasks.

### File: `apps/admin/src/components/dashboard/QuickActions.tsx`

```tsx
'use client';

import { Card, Flex, Text, Button, Box } from '@radix-ui/themes';
import { Plus, ScanLine, FileText, Download } from 'lucide-react';
import Link from 'next/link';
import { PermissionGuard } from '@/components/auth/PermissionGuard';
import { PermissionString } from '@/lib/hooks/usePermissions';

interface QuickAction {
  label: string;
  href: string;
  icon: React.ElementType;
  color: 'blue' | 'green' | 'purple' | 'orange';
  permission?: PermissionString;
}

const quickActions: QuickAction[] = [
  {
    label: 'Create Event',
    href: '/dashboard/events/create',
    icon: Plus,
    color: 'blue',
    permission: 'event.create' as PermissionString,
  },
  {
    label: 'Scan Tickets',
    href: '/dashboard/scanner',
    icon: ScanLine,
    color: 'green',
    permission: 'ticket.validate' as PermissionString,
  },
  {
    label: 'View Reports',
    href: '/dashboard/reports',
    icon: FileText,
    color: 'purple',
    permission: 'report.read' as PermissionString,
  },
  {
    label: 'Export Data',
    href: '/dashboard/reports/export',
    icon: Download,
    color: 'orange',
    permission: 'report.export' as PermissionString,
  },
];

export function QuickActions() {
  return (
    <Card>
      <Flex direction="column" p="4" gap="3">
        <Text size="2" weight="medium">Quick Actions</Text>
        <Flex direction="column" gap="2">
          {quickActions.map((action) => (
            <PermissionGuard key={action.label} permission={action.permission!}>
              <Button
                asChild
                variant="soft"
                color={action.color}
                style={{ justifyContent: 'flex-start' }}
              >
                <Link href={action.href}>
                  <action.icon size={16} />
                  {action.label}
                </Link>
              </Button>
            </PermissionGuard>
          ))}
        </Flex>
      </Flex>
    </Card>
  );
}
```

### Acceptance Criteria
- [ ] Quick actions are displayed based on permissions
- [ ] Clicking action navigates to correct page
- [ ] Icons and colors match action type
- [ ] Buttons have hover states

---

## Task 2.4: Recent Activity Feed

### Description
Create a real-time activity feed showing recent system events.

### Backend Requirements

#### Add WebSocket subscription for real-time updates

```graphql
type Subscription {
  activityAdded: RecentActivity!
}
```

### Frontend Implementation

#### File: `apps/admin/src/components/dashboard/RecentActivityFeed.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { Card, Flex, Text, Box, Avatar, ScrollArea, Badge } from '@radix-ui/themes';
import { formatDistanceToNow } from 'date-fns';
import { ShoppingCart, Calendar, User, Ticket, RefreshCcw, AlertCircle } from 'lucide-react';
import { RECENT_ACTIVITY_QUERY } from '@/lib/graphql/queries/dashboard';

interface Activity {
  id: string;
  type: string;
  message: string;
  timestamp: string;
  entityId?: string;
  entityType?: string;
}

const activityIcons: Record<string, React.ElementType> = {
  ORDER_PLACED: ShoppingCart,
  ORDER_REFUNDED: RefreshCcw,
  EVENT_CREATED: Calendar,
  EVENT_PUBLISHED: Calendar,
  USER_REGISTERED: User,
  TICKET_SCANNED: Ticket,
};

const activityColors: Record<string, 'blue' | 'green' | 'red' | 'purple' | 'orange'> = {
  ORDER_PLACED: 'green',
  ORDER_REFUNDED: 'red',
  EVENT_CREATED: 'blue',
  EVENT_PUBLISHED: 'purple',
  USER_REGISTERED: 'blue',
  TICKET_SCANNED: 'orange',
};

export function RecentActivityFeed() {
  const { data, loading } = useQuery(RECENT_ACTIVITY_QUERY, {
    variables: { limit: 10 },
    pollInterval: 30000, // Refresh every 30 seconds
  });

  const activities: Activity[] = data?.recentActivity ?? [];

  return (
    <Card style={{ height: '100%' }}>
      <Flex direction="column" p="4" style={{ height: '100%' }}>
        <Flex justify="between" align="center" mb="3">
          <Text size="2" weight="medium">Recent Activity</Text>
          <Badge color="gray" variant="soft">{activities.length} items</Badge>
        </Flex>

        <ScrollArea style={{ flex: 1, maxHeight: '300px' }}>
          <Flex direction="column" gap="3">
            {activities.map((activity) => {
              const Icon = activityIcons[activity.type] ?? AlertCircle;
              const color = activityColors[activity.type] ?? 'gray';

              return (
                <Flex key={activity.id} gap="3" align="start">
                  <Box
                    p="2"
                    style={{
                      backgroundColor: `var(--${color}-a3)`,
                      borderRadius: 'var(--radius-2)',
                      flexShrink: 0,
                    }}
                  >
                    <Icon size={14} style={{ color: `var(--${color}-11)` }} />
                  </Box>
                  <Box style={{ flex: 1 }}>
                    <Text size="2">{activity.message}</Text>
                    <Text size="1" color="gray">
                      {formatDistanceToNow(new Date(activity.timestamp), { addSuffix: true })}
                    </Text>
                  </Box>
                </Flex>
              );
            })}

            {activities.length === 0 && !loading && (
              <Text size="2" color="gray" align="center">
                No recent activity
              </Text>
            )}
          </Flex>
        </ScrollArea>
      </Flex>
    </Card>
  );
}
```

### Acceptance Criteria
- [ ] Activity feed loads from GraphQL
- [ ] Feed refreshes periodically
- [ ] Each activity has appropriate icon and color
- [ ] Timestamps show relative time ("2 minutes ago")
- [ ] Empty state displays message
- [ ] Scrollable for many items

---

## Task 2.5: Upcoming Events Card

### Description
Show a list of upcoming events with key details.

### Backend Requirements

```graphql
extend type Query {
  upcomingEvents(limit: Int = 5): [Event!]! @hasPermission(permission: "event.read")
}
```

### Frontend Implementation

#### File: `apps/admin/src/components/dashboard/UpcomingEventsCard.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { Card, Flex, Text, Box, Badge, Avatar, Button } from '@radix-ui/themes';
import { Calendar, MapPin, Users, ArrowRight } from 'lucide-react';
import Link from 'next/link';
import { format } from 'date-fns';
import { UPCOMING_EVENTS_QUERY } from '@/lib/graphql/queries/events';

interface Event {
  id: string;
  name: string;
  startDate: string;
  venue?: { name: string };
  status: string;
  ticketsSold: number;
  capacity: number;
}

export function UpcomingEventsCard() {
  const { data, loading } = useQuery(UPCOMING_EVENTS_QUERY, {
    variables: { limit: 5 },
  });

  const events: Event[] = data?.upcomingEvents ?? [];

  return (
    <Card style={{ height: '100%' }}>
      <Flex direction="column" p="4" style={{ height: '100%' }}>
        <Flex justify="between" align="center" mb="3">
          <Text size="2" weight="medium">Upcoming Events</Text>
          <Button variant="ghost" size="1" asChild>
            <Link href="/dashboard/events">
              View All <ArrowRight size={14} />
            </Link>
          </Button>
        </Flex>

        <Flex direction="column" gap="3">
          {events.map((event) => (
            <Link key={event.id} href={`/dashboard/events/${event.id}`}>
              <Flex
                gap="3"
                p="2"
                style={{
                  borderRadius: 'var(--radius-2)',
                  transition: 'background 0.15s',
                }}
                className="hover:bg-gray-a2"
              >
                <Box
                  p="3"
                  style={{
                    backgroundColor: 'var(--accent-a3)',
                    borderRadius: 'var(--radius-2)',
                  }}
                >
                  <Calendar size={20} style={{ color: 'var(--accent-11)' }} />
                </Box>
                <Box style={{ flex: 1 }}>
                  <Text size="2" weight="medium">{event.name}</Text>
                  <Flex gap="3" mt="1">
                    <Flex align="center" gap="1">
                      <Calendar size={12} />
                      <Text size="1" color="gray">
                        {format(new Date(event.startDate), 'MMM d, yyyy')}
                      </Text>
                    </Flex>
                    {event.venue && (
                      <Flex align="center" gap="1">
                        <MapPin size={12} />
                        <Text size="1" color="gray">{event.venue.name}</Text>
                      </Flex>
                    )}
                  </Flex>
                </Box>
                <Flex direction="column" align="end" gap="1">
                  <Badge
                    color={event.status === 'PUBLISHED' ? 'green' : 'orange'}
                    variant="soft"
                    size="1"
                  >
                    {event.status}
                  </Badge>
                  <Text size="1" color="gray">
                    {event.ticketsSold}/{event.capacity} sold
                  </Text>
                </Flex>
              </Flex>
            </Link>
          ))}

          {events.length === 0 && !loading && (
            <Text size="2" color="gray" align="center" py="4">
              No upcoming events
            </Text>
          )}
        </Flex>
      </Flex>
    </Card>
  );
}
```

### Acceptance Criteria
- [ ] Shows up to 5 upcoming events
- [ ] Event cards link to event detail page
- [ ] Shows date, venue, status, and ticket sales
- [ ] "View All" link goes to events page
- [ ] Hover state on event items
- [ ] Empty state message when no events

---

## Task 2.6: Role-Specific Dashboard Variants

### Description
Create dashboard variants for different admin roles.

### Frontend Implementation

#### FINANCE Role Dashboard: `apps/admin/src/components/dashboard/FinanceDashboard.tsx`

```tsx
'use client';

import { BentoGrid, StatCard } from '@/components/ui/BentoGrid';
import { Box } from '@radix-ui/themes';
import { DollarSign, TrendingUp, RefreshCcw, Building } from 'lucide-react';
import { RevenueChart } from './RevenueChart';
import { PendingRefundsCard } from './PendingRefundsCard';
import { SettlementsCard } from './SettlementsCard';
import { TransactionHistoryCard } from './TransactionHistoryCard';

export function FinanceDashboard() {
  return (
    <BentoGrid>
      {/* Revenue Chart - Large */}
      <Box gridColumn={{ lg: "span 2" }} gridRow={{ lg: "span 2" }}>
        <RevenueChart />
      </Box>

      <StatCard
        title="Today's Revenue"
        value="$12,450"
        change={{ value: 23, trend: 'up' }}
        icon={<DollarSign size={18} />}
        color="green"
      />

      <StatCard
        title="Pending Refunds"
        value="7"
        icon={<RefreshCcw size={18} />}
        color="red"
      />

      <StatCard
        title="Net Profit"
        value="$8,320"
        change={{ value: 12, trend: 'up' }}
        icon={<TrendingUp size={18} />}
        color="blue"
      />

      <StatCard
        title="Pending Settlements"
        value="3"
        icon={<Building size={18} />}
        color="orange"
      />

      {/* Pending Refunds */}
      <Box gridColumn={{ lg: "span 2" }}>
        <PendingRefundsCard />
      </Box>

      {/* Settlements */}
      <Box gridColumn={{ lg: "span 2" }}>
        <SettlementsCard />
      </Box>

      {/* Transaction History */}
      <Box gridColumn={{ lg: "span 4" }}>
        <TransactionHistoryCard />
      </Box>
    </BentoGrid>
  );
}
```

#### SCANNER Role Dashboard: `apps/admin/src/components/dashboard/ScannerDashboard.tsx`

```tsx
'use client';

import { BentoGrid, StatCard } from '@/components/ui/BentoGrid';
import { Box, Card, Flex, Heading, Text, Button } from '@radix-ui/themes';
import { ScanLine, CheckCircle, XCircle, Clock } from 'lucide-react';
import Link from 'next/link';

export function ScannerDashboard() {
  return (
    <BentoGrid>
      {/* Quick Scan Card - Large */}
      <Box gridColumn={{ lg: "span 2" }} gridRow={{ lg: "span 2" }}>
        <Card style={{ height: '100%' }}>
          <Flex direction="column" align="center" justify="center" p="6" gap="4" style={{ height: '100%' }}>
            <Box
              p="5"
              style={{
                backgroundColor: 'var(--blue-a3)',
                borderRadius: 'var(--radius-4)',
              }}
            >
              <ScanLine size={48} style={{ color: 'var(--blue-11)' }} />
            </Box>
            <Heading size="4">Ready to Scan</Heading>
            <Text color="gray" align="center">
              Click below to start scanning tickets for today's events
            </Text>
            <Button size="3" asChild>
              <Link href="/dashboard/scanner/validate">
                <ScanLine size={18} /> Start Scanning
              </Link>
            </Button>
          </Flex>
        </Card>
      </Box>

      <StatCard
        title="Scanned Today"
        value="342"
        icon={<CheckCircle size={18} />}
        color="green"
      />

      <StatCard
        title="Invalid Attempts"
        value="12"
        icon={<XCircle size={18} />}
        color="red"
      />

      <StatCard
        title="Avg Scan Time"
        value="1.2s"
        icon={<Clock size={18} />}
        color="blue"
      />

      {/* Today's Events to Scan */}
      <Box gridColumn={{ lg: "span 2" }}>
        <TodaysEventsCard />
      </Box>

      {/* Recent Scans */}
      <Box gridColumn={{ lg: "span 2" }}>
        <RecentScansCard />
      </Box>
    </BentoGrid>
  );
}
```

### Acceptance Criteria
- [ ] Finance dashboard shows financial-focused widgets
- [ ] Scanner dashboard shows scan-focused widgets
- [ ] Each role sees relevant quick actions
- [ ] Stats are role-appropriate
- [ ] Dashboard selection based on user's primary role

---

## Dependencies

- Phase 1: Core Infrastructure (Layout, BentoGrid components)

## Estimated Time

- Task 2.1 (Dashboard Page): 4 hours
- Task 2.2 (Revenue Chart): 3 hours
- Task 2.3 (Quick Actions): 2 hours
- Task 2.4 (Activity Feed): 3 hours
- Task 2.5 (Upcoming Events): 3 hours
- Task 2.6 (Role Dashboards): 5 hours

**Total: ~20 hours**
