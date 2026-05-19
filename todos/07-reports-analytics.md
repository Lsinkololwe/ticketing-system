# Phase 7: Reports & Analytics

## Overview
Implement comprehensive reporting and analytics features for sales, attendance, and custom report generation.

---

## Task 7.1: Reports Overview Page

### Description
Central hub for accessing all report types with quick insights.

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/reports/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import {
  Box, Flex, Heading, Text, Card, Grid, Button
} from '@radix-ui/themes';
import {
  BarChart3, DollarSign, Users, Ticket, TrendingUp,
  Download, Calendar, ArrowRight
} from 'lucide-react';
import Link from 'next/link';
import { REPORTS_OVERVIEW_QUERY } from '@/lib/graphql/queries/reports';
import { formatCurrency } from '@/lib/utils/format';

interface ReportCard {
  title: string;
  description: string;
  icon: React.ElementType;
  href: string;
  color: 'blue' | 'green' | 'purple' | 'orange';
  quickStat?: string;
}

const reportCards: ReportCard[] = [
  {
    title: 'Sales Reports',
    description: 'Revenue, transactions, and payment analytics',
    icon: DollarSign,
    href: '/dashboard/reports/sales',
    color: 'green',
  },
  {
    title: 'Attendance Reports',
    description: 'Check-in rates, attendance patterns, and no-shows',
    icon: Users,
    href: '/dashboard/reports/attendance',
    color: 'blue',
  },
  {
    title: 'Event Performance',
    description: 'Event-by-event metrics and comparisons',
    icon: BarChart3,
    href: '/dashboard/reports/events',
    color: 'purple',
  },
  {
    title: 'Ticket Analytics',
    description: 'Ticket type performance and pricing analysis',
    icon: Ticket,
    href: '/dashboard/reports/tickets',
    color: 'orange',
  },
];

export default function ReportsPage() {
  const { data } = useQuery(REPORTS_OVERVIEW_QUERY);

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Reports & Analytics</Heading>
          <Text color="gray" size="2">Insights and data exports</Text>
        </Box>
        <Flex gap="2">
          <Button variant="soft" asChild>
            <Link href="/dashboard/reports/custom">
              <TrendingUp size={16} /> Custom Report
            </Link>
          </Button>
          <Button variant="soft">
            <Download size={16} /> Export All
          </Button>
        </Flex>
      </Flex>

      {/* Quick Stats */}
      <Grid columns="4" gap="4" mb="5">
        <Card>
          <Flex direction="column" p="4">
            <Text size="1" color="gray">This Month Revenue</Text>
            <Text size="5" weight="bold">
              {formatCurrency(data?.monthlyStats?.revenue ?? 0)}
            </Text>
            <Text size="1" color="green">+12% vs last month</Text>
          </Flex>
        </Card>
        <Card>
          <Flex direction="column" p="4">
            <Text size="1" color="gray">Tickets Sold</Text>
            <Text size="5" weight="bold">
              {data?.monthlyStats?.ticketsSold?.toLocaleString() ?? 0}
            </Text>
            <Text size="1" color="green">+8% vs last month</Text>
          </Flex>
        </Card>
        <Card>
          <Flex direction="column" p="4">
            <Text size="1" color="gray">Events Held</Text>
            <Text size="5" weight="bold">
              {data?.monthlyStats?.eventsHeld ?? 0}
            </Text>
          </Flex>
        </Card>
        <Card>
          <Flex direction="column" p="4">
            <Text size="1" color="gray">Avg Check-in Rate</Text>
            <Text size="5" weight="bold">
              {data?.monthlyStats?.avgCheckInRate?.toFixed(0) ?? 0}%
            </Text>
          </Flex>
        </Card>
      </Grid>

      {/* Report Types */}
      <Grid columns="2" gap="4" mb="5">
        {reportCards.map((report) => (
          <Link key={report.href} href={report.href}>
            <Card style={{ height: '100%', cursor: 'pointer' }}>
              <Flex p="4" gap="4" align="start">
                <Box
                  p="3"
                  style={{
                    backgroundColor: `var(--${report.color}-a3)`,
                    borderRadius: 'var(--radius-3)',
                  }}
                >
                  <report.icon size={24} style={{ color: `var(--${report.color}-11)` }} />
                </Box>
                <Box style={{ flex: 1 }}>
                  <Flex justify="between" align="start">
                    <Box>
                      <Text size="3" weight="medium">{report.title}</Text>
                      <Text size="2" color="gray" mt="1">{report.description}</Text>
                    </Box>
                    <ArrowRight size={16} style={{ color: 'var(--gray-9)' }} />
                  </Flex>
                </Box>
              </Flex>
            </Card>
          </Link>
        ))}
      </Grid>

      {/* Recent Reports */}
      <Card>
        <Box p="4">
          <Flex justify="between" align="center" mb="4">
            <Text size="3" weight="medium">Recently Generated Reports</Text>
            <Button variant="ghost" size="1">View All</Button>
          </Flex>
          <Flex direction="column" gap="2">
            {data?.recentReports?.map((report: any) => (
              <Flex
                key={report.id}
                justify="between"
                align="center"
                p="2"
                style={{
                  backgroundColor: 'var(--gray-a2)',
                  borderRadius: 'var(--radius-2)',
                }}
              >
                <Flex align="center" gap="2">
                  <BarChart3 size={14} />
                  <Text size="2">{report.name}</Text>
                </Flex>
                <Flex align="center" gap="3">
                  <Text size="1" color="gray">{report.createdAt}</Text>
                  <Button variant="ghost" size="1">
                    <Download size={14} />
                  </Button>
                </Flex>
              </Flex>
            ))}
          </Flex>
        </Box>
      </Card>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Quick stats for current month
- [ ] Report type cards with navigation
- [ ] Recently generated reports list
- [ ] Export all functionality
- [ ] Custom report builder link

---

## Task 7.2: Sales Reports Page

### Description
Detailed sales analytics with charts and data tables.

### Backend Requirements

```graphql
type SalesReport {
  period: String!
  totalRevenue: Float!
  totalBookings: Int!
  totalTickets: Int!
  avgBookingValue: Float!
  refundRate: Float!
  topEvents: [EventSales!]!
  salesByDay: [DailySales!]!
  salesByPaymentMethod: [PaymentMethodSales!]!
  salesByTicketType: [TicketTypeSales!]!
}

type DailySales {
  date: Date!
  revenue: Float!
  bookings: Int!
  tickets: Int!
}

type EventSales {
  event: Event!
  revenue: Float!
  tickets: Int!
  percentOfTotal: Float!
}

extend type Query {
  salesReport(
    startDate: DateTime!
    endDate: DateTime!
    eventId: ID
    organizerId: ID
  ): SalesReport! @hasPermission(permission: "report.sales")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/reports/sales/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Grid, Tabs, Button,
  Select, Table
} from '@radix-ui/themes';
import {
  DollarSign, TrendingUp, ShoppingCart, Ticket,
  Download, Calendar
} from 'lucide-react';
import { SALES_REPORT_QUERY } from '@/lib/graphql/queries/reports';
import { formatCurrency, formatDate } from '@/lib/utils/format';
import { StatCard } from '@/components/ui/BentoGrid';
import { DateRangePicker } from '@/components/ui/DateRangePicker';

export default function SalesReportsPage() {
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
    endDate: new Date().toISOString(),
  });
  const [groupBy, setGroupBy] = useState<'day' | 'week' | 'month'>('day');

  const { data, loading } = useQuery(SALES_REPORT_QUERY, {
    variables: {
      startDate: dateRange.startDate,
      endDate: dateRange.endDate,
    },
  });

  const report = data?.salesReport;

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Sales Reports</Heading>
          <Text color="gray" size="2">Revenue and transaction analytics</Text>
        </Box>
        <Flex gap="3">
          <DateRangePicker
            value={dateRange}
            onChange={setDateRange}
          />
          <Button variant="soft">
            <Download size={16} /> Export
          </Button>
        </Flex>
      </Flex>

      {/* Key Metrics */}
      <Grid columns="4" gap="4" mb="5">
        <StatCard
          title="Total Revenue"
          value={formatCurrency(report?.totalRevenue ?? 0)}
          icon={<DollarSign size={18} />}
          color="green"
        />
        <StatCard
          title="Total Bookings"
          value={report?.totalBookings ?? 0}
          icon={<ShoppingCart size={18} />}
          color="blue"
        />
        <StatCard
          title="Tickets Sold"
          value={report?.totalTickets ?? 0}
          icon={<Ticket size={18} />}
          color="purple"
        />
        <StatCard
          title="Avg Booking Value"
          value={formatCurrency(report?.avgBookingValue ?? 0)}
          icon={<TrendingUp size={18} />}
          color="orange"
        />
      </Grid>

      <Grid columns="3" gap="4" mb="5">
        {/* Revenue Chart */}
        <Card style={{ gridColumn: 'span 2' }}>
          <Box p="4">
            <Flex justify="between" align="center" mb="4">
              <Text size="3" weight="medium">Revenue Over Time</Text>
              <Select.Root value={groupBy} onValueChange={(v) => setGroupBy(v as typeof groupBy)}>
                <Select.Trigger />
                <Select.Content>
                  <Select.Item value="day">Daily</Select.Item>
                  <Select.Item value="week">Weekly</Select.Item>
                  <Select.Item value="month">Monthly</Select.Item>
                </Select.Content>
              </Select.Root>
            </Flex>
            <Box style={{ height: '300px' }}>
              <RevenueLineChart data={report?.salesByDay ?? []} />
            </Box>
          </Box>
        </Card>

        {/* Payment Methods */}
        <Card>
          <Box p="4">
            <Text size="3" weight="medium" mb="4">Payment Methods</Text>
            <Flex direction="column" gap="3">
              {report?.salesByPaymentMethod?.map((method) => (
                <Box key={method.method}>
                  <Flex justify="between" mb="1">
                    <Text size="2">{method.method}</Text>
                    <Text size="2" weight="medium">
                      {formatCurrency(method.total)}
                    </Text>
                  </Flex>
                  <Box
                    style={{
                      height: '8px',
                      backgroundColor: 'var(--gray-a3)',
                      borderRadius: 'var(--radius-1)',
                      overflow: 'hidden',
                    }}
                  >
                    <Box
                      style={{
                        height: '100%',
                        width: `${method.percentage}%`,
                        backgroundColor: 'var(--accent-9)',
                      }}
                    />
                  </Box>
                </Box>
              ))}
            </Flex>
          </Box>
        </Card>
      </Grid>

      {/* Top Events */}
      <Card>
        <Box p="4">
          <Text size="3" weight="medium" mb="4">Top Selling Events</Text>
          <Table.Root>
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Event</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Tickets Sold</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Revenue</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>% of Total</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {report?.topEvents?.map((item, i) => (
                <Table.Row key={item.event.id}>
                  <Table.Cell>
                    <Flex align="center" gap="2">
                      <Text>{i + 1}.</Text>
                      <Text weight="medium">{item.event.name}</Text>
                    </Flex>
                  </Table.Cell>
                  <Table.Cell>{item.tickets}</Table.Cell>
                  <Table.Cell>{formatCurrency(item.revenue)}</Table.Cell>
                  <Table.Cell>{item.percentOfTotal.toFixed(1)}%</Table.Cell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table.Root>
        </Box>
      </Card>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Date range picker
- [ ] Key metrics cards
- [ ] Revenue over time chart
- [ ] Daily/Weekly/Monthly grouping
- [ ] Payment method breakdown
- [ ] Top selling events table
- [ ] Export to CSV/PDF

---

## Task 7.3: Attendance Reports Page

### Description
Attendance analytics including check-in rates and patterns.

### Backend Requirements

```graphql
type AttendanceReport {
  totalTicketsSold: Int!
  totalCheckIns: Int!
  checkInRate: Float!
  noShowRate: Float!
  avgCheckInTime: String
  checkInsByHour: [HourlyCheckIns!]!
  checkInsByEvent: [EventAttendance!]!
  gateBreakdown: [GateAttendance!]!
}

type HourlyCheckIns {
  hour: Int!
  count: Int!
}

type EventAttendance {
  event: Event!
  sold: Int!
  checkedIn: Int!
  checkInRate: Float!
}

type GateAttendance {
  gate: String!
  checkIns: Int!
  avgWaitTime: Float
}

extend type Query {
  attendanceReport(
    startDate: DateTime!
    endDate: DateTime!
    eventId: ID
  ): AttendanceReport! @hasPermission(permission: "report.attendance")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/reports/attendance/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Grid, Progress, Table, Button
} from '@radix-ui/themes';
import {
  Users, CheckCircle, XCircle, Clock, Download
} from 'lucide-react';
import { ATTENDANCE_REPORT_QUERY } from '@/lib/graphql/queries/reports';
import { formatPercent } from '@/lib/utils/format';
import { StatCard } from '@/components/ui/BentoGrid';
import { DateRangePicker } from '@/components/ui/DateRangePicker';

export default function AttendanceReportsPage() {
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
    endDate: new Date().toISOString(),
  });

  const { data, loading } = useQuery(ATTENDANCE_REPORT_QUERY, {
    variables: {
      startDate: dateRange.startDate,
      endDate: dateRange.endDate,
    },
  });

  const report = data?.attendanceReport;

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Attendance Reports</Heading>
          <Text color="gray" size="2">Check-in analytics and patterns</Text>
        </Box>
        <Flex gap="3">
          <DateRangePicker value={dateRange} onChange={setDateRange} />
          <Button variant="soft">
            <Download size={16} /> Export
          </Button>
        </Flex>
      </Flex>

      {/* Key Metrics */}
      <Grid columns="4" gap="4" mb="5">
        <StatCard
          title="Total Check-ins"
          value={report?.totalCheckIns?.toLocaleString() ?? 0}
          icon={<CheckCircle size={18} />}
          color="green"
        />
        <StatCard
          title="Check-in Rate"
          value={formatPercent(report?.checkInRate ?? 0)}
          icon={<Users size={18} />}
          color="blue"
        />
        <StatCard
          title="No-show Rate"
          value={formatPercent(report?.noShowRate ?? 0)}
          icon={<XCircle size={18} />}
          color="red"
        />
        <StatCard
          title="Avg Check-in Time"
          value={report?.avgCheckInTime ?? '-'}
          icon={<Clock size={18} />}
          color="purple"
        />
      </Grid>

      <Grid columns="2" gap="4" mb="5">
        {/* Check-ins by Hour */}
        <Card>
          <Box p="4">
            <Text size="3" weight="medium" mb="4">Check-ins by Hour</Text>
            <Box style={{ height: '250px' }}>
              <HourlyCheckInsChart data={report?.checkInsByHour ?? []} />
            </Box>
          </Box>
        </Card>

        {/* Gate Breakdown */}
        <Card>
          <Box p="4">
            <Text size="3" weight="medium" mb="4">Check-ins by Gate</Text>
            <Flex direction="column" gap="3">
              {report?.gateBreakdown?.map((gate) => (
                <Box key={gate.gate}>
                  <Flex justify="between" mb="1">
                    <Text size="2">{gate.gate}</Text>
                    <Flex gap="3">
                      <Text size="2">{gate.checkIns} check-ins</Text>
                      {gate.avgWaitTime && (
                        <Text size="2" color="gray">
                          ~{gate.avgWaitTime.toFixed(0)}s avg wait
                        </Text>
                      )}
                    </Flex>
                  </Flex>
                  <Progress
                    value={(gate.checkIns / report.totalCheckIns) * 100}
                  />
                </Box>
              ))}
            </Flex>
          </Box>
        </Card>
      </Grid>

      {/* Event Attendance */}
      <Card>
        <Box p="4">
          <Text size="3" weight="medium" mb="4">Attendance by Event</Text>
          <Table.Root>
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Event</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Date</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Tickets Sold</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Checked In</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Check-in Rate</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Progress</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {report?.checkInsByEvent?.map((item) => (
                <Table.Row key={item.event.id}>
                  <Table.Cell>
                    <Text weight="medium">{item.event.name}</Text>
                  </Table.Cell>
                  <Table.Cell>{formatDate(item.event.startDate)}</Table.Cell>
                  <Table.Cell>{item.sold}</Table.Cell>
                  <Table.Cell>{item.checkedIn}</Table.Cell>
                  <Table.Cell>{formatPercent(item.checkInRate)}</Table.Cell>
                  <Table.Cell style={{ width: '150px' }}>
                    <Progress value={item.checkInRate} />
                  </Table.Cell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table.Root>
        </Box>
      </Card>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Key attendance metrics
- [ ] Check-ins by hour chart
- [ ] Gate breakdown with progress bars
- [ ] Event attendance table
- [ ] No-show rate tracking
- [ ] Export functionality

---

## Task 7.4: Custom Report Builder

### Description
Interface for building and saving custom reports.

### Backend Requirements

```graphql
type CustomReport {
  id: ID!
  name: String!
  description: String
  config: ReportConfig!
  createdBy: User!
  createdAt: DateTime!
  lastRunAt: DateTime
  schedule: ReportSchedule
}

type ReportConfig {
  metrics: [String!]!
  dimensions: [String!]!
  filters: [ReportFilter!]!
  dateRange: DateRangeConfig!
  groupBy: String
  orderBy: String
}

input ReportFilter {
  field: String!
  operator: FilterOperator!
  value: String!
}

enum FilterOperator {
  EQUALS
  NOT_EQUALS
  CONTAINS
  GREATER_THAN
  LESS_THAN
  BETWEEN
  IN
}

type ReportSchedule {
  frequency: ScheduleFrequency!
  recipients: [String!]!
  format: ExportFormat!
}

enum ScheduleFrequency {
  DAILY
  WEEKLY
  MONTHLY
}

enum ExportFormat {
  CSV
  PDF
  EXCEL
}

extend type Query {
  customReports: [CustomReport!]! @hasPermission(permission: "report.custom")
  runCustomReport(id: ID!, dateRange: DateRangeInput): ReportResult!
    @hasPermission(permission: "report.custom")
}

extend type Mutation {
  createCustomReport(input: CreateCustomReportInput!): CustomReport!
    @hasPermission(permission: "report.custom.create")

  deleteCustomReport(id: ID!): Boolean!
    @hasPermission(permission: "report.custom.delete")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/reports/custom/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Button, Dialog, TextField,
  Select, Checkbox, TextArea, Table, Badge
} from '@radix-ui/themes';
import {
  Plus, Play, Download, Trash2, Calendar, Settings
} from 'lucide-react';
import {
  CUSTOM_REPORTS_QUERY, CREATE_CUSTOM_REPORT, RUN_CUSTOM_REPORT
} from '@/lib/graphql/queries/reports';
import { formatDateTime } from '@/lib/utils/format';

const availableMetrics = [
  { value: 'revenue', label: 'Revenue' },
  { value: 'bookings', label: 'Booking Count' },
  { value: 'tickets', label: 'Tickets Sold' },
  { value: 'checkIns', label: 'Check-ins' },
  { value: 'refunds', label: 'Refunds' },
  { value: 'avgBookingValue', label: 'Avg Booking Value' },
];

const availableDimensions = [
  { value: 'date', label: 'Date' },
  { value: 'event', label: 'Event' },
  { value: 'category', label: 'Category' },
  { value: 'venue', label: 'Venue' },
  { value: 'ticketType', label: 'Ticket Type' },
  { value: 'paymentMethod', label: 'Payment Method' },
  { value: 'organizer', label: 'Organizer' },
];

export default function CustomReportsPage() {
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [newReport, setNewReport] = useState({
    name: '',
    description: '',
    metrics: [] as string[],
    dimensions: [] as string[],
  });

  const { data, loading, refetch } = useQuery(CUSTOM_REPORTS_QUERY);

  const [createReport, { loading: creating }] = useMutation(CREATE_CUSTOM_REPORT, {
    onCompleted: () => {
      refetch();
      setCreateDialogOpen(false);
      setNewReport({ name: '', description: '', metrics: [], dimensions: [] });
    },
  });

  const [runReport, { loading: running }] = useMutation(RUN_CUSTOM_REPORT);

  const reports = data?.customReports ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Custom Reports</Heading>
          <Text color="gray" size="2">Build and save custom report configurations</Text>
        </Box>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus size={16} /> New Report
        </Button>
      </Flex>

      {/* Saved Reports */}
      <Card>
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Report Name</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Metrics</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Last Run</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Schedule</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {reports.map((report) => (
              <Table.Row key={report.id}>
                <Table.Cell>
                  <Box>
                    <Text weight="medium">{report.name}</Text>
                    {report.description && (
                      <Text size="1" color="gray">{report.description}</Text>
                    )}
                  </Box>
                </Table.Cell>
                <Table.Cell>
                  <Flex gap="1" wrap="wrap">
                    {report.config.metrics.slice(0, 3).map((m) => (
                      <Badge key={m} variant="soft" size="1">{m}</Badge>
                    ))}
                    {report.config.metrics.length > 3 && (
                      <Badge variant="soft" size="1">
                        +{report.config.metrics.length - 3}
                      </Badge>
                    )}
                  </Flex>
                </Table.Cell>
                <Table.Cell>
                  {report.lastRunAt ? formatDateTime(report.lastRunAt) : 'Never'}
                </Table.Cell>
                <Table.Cell>
                  {report.schedule ? (
                    <Badge color="blue" variant="soft">
                      {report.schedule.frequency}
                    </Badge>
                  ) : (
                    <Text size="2" color="gray">Manual</Text>
                  )}
                </Table.Cell>
                <Table.Cell>
                  <Flex gap="2">
                    <Button
                      size="1"
                      variant="soft"
                      onClick={() => runReport({ variables: { id: report.id } })}
                      disabled={running}
                    >
                      <Play size={12} /> Run
                    </Button>
                    <Button size="1" variant="ghost">
                      <Settings size={12} />
                    </Button>
                    <Button size="1" variant="ghost" color="red">
                      <Trash2 size={12} />
                    </Button>
                  </Flex>
                </Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Card>

      {/* Create Report Dialog */}
      <Dialog.Root open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <Dialog.Content style={{ maxWidth: 600 }}>
          <Dialog.Title>Create Custom Report</Dialog.Title>
          <Flex direction="column" gap="4" mt="4">
            <Box>
              <Text size="2" weight="medium" mb="1">Report Name</Text>
              <TextField.Root
                placeholder="e.g., Weekly Sales Summary"
                value={newReport.name}
                onChange={(e) => setNewReport(r => ({ ...r, name: e.target.value }))}
              />
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="1">Description</Text>
              <TextArea
                placeholder="Describe what this report shows..."
                value={newReport.description}
                onChange={(e) => setNewReport(r => ({ ...r, description: e.target.value }))}
              />
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="2">Metrics</Text>
              <Flex direction="column" gap="2">
                {availableMetrics.map((metric) => (
                  <Text as="label" key={metric.value} size="2">
                    <Flex align="center" gap="2">
                      <Checkbox
                        checked={newReport.metrics.includes(metric.value)}
                        onCheckedChange={(checked) => {
                          setNewReport(r => ({
                            ...r,
                            metrics: checked
                              ? [...r.metrics, metric.value]
                              : r.metrics.filter(m => m !== metric.value)
                          }));
                        }}
                      />
                      {metric.label}
                    </Flex>
                  </Text>
                ))}
              </Flex>
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="2">Group By</Text>
              <Flex direction="column" gap="2">
                {availableDimensions.map((dim) => (
                  <Text as="label" key={dim.value} size="2">
                    <Flex align="center" gap="2">
                      <Checkbox
                        checked={newReport.dimensions.includes(dim.value)}
                        onCheckedChange={(checked) => {
                          setNewReport(r => ({
                            ...r,
                            dimensions: checked
                              ? [...r.dimensions, dim.value]
                              : r.dimensions.filter(d => d !== dim.value)
                          }));
                        }}
                      />
                      {dim.label}
                    </Flex>
                  </Text>
                ))}
              </Flex>
            </Box>
          </Flex>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              onClick={() => createReport({
                variables: {
                  input: {
                    name: newReport.name,
                    description: newReport.description,
                    config: {
                      metrics: newReport.metrics,
                      dimensions: newReport.dimensions,
                      filters: [],
                      dateRange: { preset: 'LAST_30_DAYS' },
                    },
                  },
                },
              })}
              disabled={creating || !newReport.name || newReport.metrics.length === 0}
            >
              Create Report
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] List saved custom reports
- [ ] Create new report dialog
- [ ] Metric selection
- [ ] Dimension/grouping selection
- [ ] Run report on demand
- [ ] Schedule report (daily/weekly/monthly)
- [ ] Delete custom reports
- [ ] Export results

---

## Dependencies

- Phase 1: Core Infrastructure (StatCard, DataTable)
- Phase 4: Booking & Finance (for sales data)
- Phase 6: Scanner (for attendance data)

## Estimated Time

- Task 7.1 (Reports Overview): 4 hours
- Task 7.2 (Sales Reports): 6 hours
- Task 7.3 (Attendance Reports): 5 hours
- Task 7.4 (Custom Report Builder): 8 hours

**Total: ~23 hours**
