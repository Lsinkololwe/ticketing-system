# Phase 3: Event Management

## Overview
Implement comprehensive event management capabilities including listing, creation, editing, ticket management, and event analytics.

---

## Task 3.1: Event Listing Page

### Description
Create a filterable, searchable event listing with bulk actions.

### Backend Requirements

#### GraphQL Schema Enhancement

```graphql
input EventFilterInput {
  status: [EventStatus!]
  categoryId: ID
  venueId: ID
  startDateFrom: DateTime
  startDateTo: DateTime
  searchQuery: String
  organizerId: ID
}

input EventSortInput {
  field: EventSortField!
  direction: SortDirection!
}

enum EventSortField {
  NAME
  START_DATE
  CREATED_AT
  TICKETS_SOLD
  STATUS
}

type EventPage {
  content: [Event!]!
  totalElements: Int!
  totalPages: Int!
  pageNumber: Int!
  pageSize: Int!
}

extend type Query {
  eventsPage(
    filter: EventFilterInput
    sort: EventSortInput
    page: Int = 0
    size: Int = 20
  ): EventPage! @hasPermission(permission: "event.read")
}

extend type Mutation {
  publishEvent(id: ID!): Event! @hasPermission(permission: "event.publish")
  unpublishEvent(id: ID!): Event! @hasPermission(permission: "event.publish")
  cancelEvent(id: ID!, reason: String): Event! @hasPermission(permission: "event.cancel")
  deleteEvent(id: ID!): Boolean! @hasPermission(permission: "event.delete")
  bulkPublishEvents(ids: [ID!]!): [Event!]! @hasPermission(permission: "event.publish")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/events/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Button, Badge, DropdownMenu,
  Select, TextField, Dialog, Callout
} from '@radix-ui/themes';
import { Plus, Filter, MoreHorizontal, Calendar, Eye, Edit, Trash2, Ban } from 'lucide-react';
import Link from 'next/link';
import { DataTable } from '@/components/ui/DataTable';
import { PermissionGuard } from '@/components/auth/PermissionGuard';
import { EVENTS_PAGE_QUERY, PUBLISH_EVENT, CANCEL_EVENT, DELETE_EVENT } from '@/lib/graphql/queries/events';
import { formatDate } from '@/lib/utils/date';

const statusColors: Record<string, 'gray' | 'blue' | 'green' | 'red' | 'orange'> = {
  DRAFT: 'gray',
  PENDING: 'orange',
  PUBLISHED: 'green',
  CANCELLED: 'red',
  COMPLETED: 'blue',
};

export default function EventsPage() {
  const [filter, setFilter] = useState({
    status: null as string | null,
    searchQuery: '',
  });
  const [page, setPage] = useState(0);
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { data, loading, refetch } = useQuery(EVENTS_PAGE_QUERY, {
    variables: {
      filter: {
        status: filter.status ? [filter.status] : null,
        searchQuery: filter.searchQuery || null,
      },
      page,
      size: 20,
    },
  });

  const [publishEvent] = useMutation(PUBLISH_EVENT, {
    onCompleted: () => refetch(),
  });

  const [cancelEvent] = useMutation(CANCEL_EVENT, {
    onCompleted: () => refetch(),
  });

  const [deleteEvent] = useMutation(DELETE_EVENT, {
    onCompleted: () => {
      refetch();
      setDeleteDialogOpen(false);
    },
  });

  const events = data?.eventsPage?.content ?? [];
  const totalPages = data?.eventsPage?.totalPages ?? 0;

  const columns = [
    {
      key: 'name',
      header: 'Event Name',
      sortable: true,
      render: (event: Event) => (
        <Flex align="center" gap="3">
          <Box
            style={{
              width: 40,
              height: 40,
              borderRadius: 'var(--radius-2)',
              backgroundColor: 'var(--gray-a3)',
              backgroundImage: event.coverImage ? `url(${event.coverImage})` : undefined,
              backgroundSize: 'cover',
            }}
          />
          <Box>
            <Text weight="medium">{event.name}</Text>
            <Text size="1" color="gray">{event.venue?.name}</Text>
          </Box>
        </Flex>
      ),
    },
    {
      key: 'startDate',
      header: 'Date',
      sortable: true,
      render: (event: Event) => formatDate(event.startDate),
    },
    {
      key: 'status',
      header: 'Status',
      render: (event: Event) => (
        <Badge color={statusColors[event.status]} variant="soft">
          {event.status}
        </Badge>
      ),
    },
    {
      key: 'ticketsSold',
      header: 'Sales',
      render: (event: Event) => (
        <Text size="2">
          {event.ticketsSold} / {event.capacity}
        </Text>
      ),
    },
    {
      key: 'revenue',
      header: 'Revenue',
      render: (event: Event) => `$${event.totalRevenue?.toLocaleString() ?? 0}`,
    },
    {
      key: 'actions',
      header: '',
      render: (event: Event) => (
        <DropdownMenu.Root>
          <DropdownMenu.Trigger>
            <Button variant="ghost" size="1">
              <MoreHorizontal size={16} />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content>
            <DropdownMenu.Item asChild>
              <Link href={`/dashboard/events/${event.id}`}>
                <Eye size={14} /> View Details
              </Link>
            </DropdownMenu.Item>
            <PermissionGuard permission="event.update">
              <DropdownMenu.Item asChild>
                <Link href={`/dashboard/events/${event.id}/edit`}>
                  <Edit size={14} /> Edit
                </Link>
              </DropdownMenu.Item>
            </PermissionGuard>
            <PermissionGuard permission="event.publish">
              {event.status === 'DRAFT' && (
                <DropdownMenu.Item onClick={() => publishEvent({ variables: { id: event.id } })}>
                  <Calendar size={14} /> Publish
                </DropdownMenu.Item>
              )}
            </PermissionGuard>
            <DropdownMenu.Separator />
            <PermissionGuard permission="event.cancel">
              {event.status === 'PUBLISHED' && (
                <DropdownMenu.Item color="orange" onClick={() => cancelEvent({ variables: { id: event.id } })}>
                  <Ban size={14} /> Cancel Event
                </DropdownMenu.Item>
              )}
            </PermissionGuard>
            <PermissionGuard permission="event.delete">
              <DropdownMenu.Item
                color="red"
                onClick={() => {
                  setSelectedEvent(event);
                  setDeleteDialogOpen(true);
                }}
              >
                <Trash2 size={14} /> Delete
              </DropdownMenu.Item>
            </PermissionGuard>
          </DropdownMenu.Content>
        </DropdownMenu.Root>
      ),
    },
  ];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Events</Heading>
          <Text color="gray" size="2">Manage all events in the system</Text>
        </Box>
        <PermissionGuard permission="event.create">
          <Button asChild>
            <Link href="/dashboard/events/create">
              <Plus size={16} /> Create Event
            </Link>
          </Button>
        </PermissionGuard>
      </Flex>

      {/* Filters */}
      <Flex gap="3" mb="4">
        <TextField.Root
          placeholder="Search events..."
          value={filter.searchQuery}
          onChange={(e) => setFilter(f => ({ ...f, searchQuery: e.target.value }))}
          style={{ width: '300px' }}
        />
        <Select.Root
          value={filter.status || 'all'}
          onValueChange={(v) => setFilter(f => ({ ...f, status: v === 'all' ? null : v }))}
        >
          <Select.Trigger placeholder="Status" />
          <Select.Content>
            <Select.Item value="all">All Status</Select.Item>
            <Select.Item value="DRAFT">Draft</Select.Item>
            <Select.Item value="PUBLISHED">Published</Select.Item>
            <Select.Item value="CANCELLED">Cancelled</Select.Item>
            <Select.Item value="COMPLETED">Completed</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      {/* Data Table */}
      <DataTable
        data={events}
        columns={columns}
        loading={loading}
        onRowClick={(event) => window.location.href = `/dashboard/events/${event.id}`}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog.Root open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <Dialog.Content style={{ maxWidth: 400 }}>
          <Dialog.Title>Delete Event</Dialog.Title>
          <Dialog.Description>
            Are you sure you want to delete "{selectedEvent?.name}"? This action cannot be undone.
          </Dialog.Description>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              color="red"
              onClick={() => deleteEvent({ variables: { id: selectedEvent?.id } })}
            >
              Delete Event
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Events list with pagination
- [ ] Search by event name
- [ ] Filter by status, category, date range
- [ ] Row actions (view, edit, publish, cancel, delete)
- [ ] Bulk actions for selected events
- [ ] Delete confirmation dialog
- [ ] Permission-based action visibility

---

## Task 3.2: Event Detail Page

### Description
Comprehensive event detail page with tabs for different sections.

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/events/[id]/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useParams } from 'next/navigation';
import {
  Box, Flex, Heading, Text, Badge, Tabs, Card, Avatar,
  Button, Separator
} from '@radix-ui/themes';
import {
  Calendar, MapPin, Users, DollarSign, Ticket, Edit,
  Eye, BarChart3, Settings
} from 'lucide-react';
import Link from 'next/link';
import { EVENT_DETAIL_QUERY } from '@/lib/graphql/queries/events';
import { formatDate, formatDateTime } from '@/lib/utils/date';
import { PermissionGuard } from '@/components/auth/PermissionGuard';
import { EventOverviewTab } from '@/components/events/EventOverviewTab';
import { EventTicketsTab } from '@/components/events/EventTicketsTab';
import { EventBookingsTab } from '@/components/events/EventBookingsTab';
import { EventAnalyticsTab } from '@/components/events/EventAnalyticsTab';
import { EventSettingsTab } from '@/components/events/EventSettingsTab';

export default function EventDetailPage() {
  const params = useParams();
  const eventId = params.id as string;

  const { data, loading, error } = useQuery(EVENT_DETAIL_QUERY, {
    variables: { id: eventId },
  });

  if (loading) return <EventDetailSkeleton />;
  if (error || !data?.event) return <EventNotFound />;

  const event = data.event;

  return (
    <Box>
      {/* Header */}
      <Flex gap="5" mb="5">
        {/* Cover Image */}
        <Box
          style={{
            width: 200,
            height: 120,
            borderRadius: 'var(--radius-3)',
            backgroundColor: 'var(--gray-a3)',
            backgroundImage: event.coverImage ? `url(${event.coverImage})` : undefined,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
          }}
        />
        <Box style={{ flex: 1 }}>
          <Flex justify="between" align="start">
            <Box>
              <Flex align="center" gap="2" mb="2">
                <Heading size="6">{event.name}</Heading>
                <Badge color={statusColors[event.status]} variant="soft">
                  {event.status}
                </Badge>
              </Flex>
              <Flex gap="4" mb="2">
                <Flex align="center" gap="1">
                  <Calendar size={14} />
                  <Text size="2">{formatDateTime(event.startDate)}</Text>
                </Flex>
                <Flex align="center" gap="1">
                  <MapPin size={14} />
                  <Text size="2">{event.venue?.name}</Text>
                </Flex>
              </Flex>
              <Text size="2" color="gray">
                Organized by {event.organizer?.name}
              </Text>
            </Box>
            <Flex gap="2">
              <PermissionGuard permission="event.update">
                <Button variant="soft" asChild>
                  <Link href={`/dashboard/events/${event.id}/edit`}>
                    <Edit size={16} /> Edit
                  </Link>
                </Button>
              </PermissionGuard>
              <Button variant="soft" asChild>
                <a href={event.publicUrl} target="_blank" rel="noopener">
                  <Eye size={16} /> View Public Page
                </a>
              </Button>
            </Flex>
          </Flex>

          {/* Quick Stats */}
          <Flex gap="5" mt="4">
            <Box>
              <Text size="1" color="gray">Tickets Sold</Text>
              <Flex align="baseline" gap="1">
                <Text size="5" weight="bold">{event.ticketsSold}</Text>
                <Text size="2" color="gray">/ {event.capacity}</Text>
              </Flex>
            </Box>
            <Separator orientation="vertical" />
            <Box>
              <Text size="1" color="gray">Revenue</Text>
              <Text size="5" weight="bold">${event.totalRevenue?.toLocaleString()}</Text>
            </Box>
            <Separator orientation="vertical" />
            <Box>
              <Text size="1" color="gray">Page Views</Text>
              <Text size="5" weight="bold">{event.pageViews?.toLocaleString()}</Text>
            </Box>
          </Flex>
        </Box>
      </Flex>

      {/* Tabs */}
      <Tabs.Root defaultValue="overview">
        <Tabs.List>
          <Tabs.Trigger value="overview">Overview</Tabs.Trigger>
          <Tabs.Trigger value="tickets">
            <Ticket size={14} /> Tickets
          </Tabs.Trigger>
          <Tabs.Trigger value="bookings">
            <DollarSign size={14} /> Bookings
          </Tabs.Trigger>
          <PermissionGuard permission="event.analytics">
            <Tabs.Trigger value="analytics">
              <BarChart3 size={14} /> Analytics
            </Tabs.Trigger>
          </PermissionGuard>
          <PermissionGuard permission="event.update">
            <Tabs.Trigger value="settings">
              <Settings size={14} /> Settings
            </Tabs.Trigger>
          </PermissionGuard>
        </Tabs.List>

        <Box pt="4">
          <Tabs.Content value="overview">
            <EventOverviewTab event={event} />
          </Tabs.Content>
          <Tabs.Content value="tickets">
            <EventTicketsTab eventId={event.id} />
          </Tabs.Content>
          <Tabs.Content value="bookings">
            <EventBookingsTab eventId={event.id} />
          </Tabs.Content>
          <Tabs.Content value="analytics">
            <EventAnalyticsTab eventId={event.id} />
          </Tabs.Content>
          <Tabs.Content value="settings">
            <EventSettingsTab event={event} />
          </Tabs.Content>
        </Box>
      </Tabs.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Event header with cover image and basic info
- [ ] Quick stats (sales, revenue, views)
- [ ] Tabbed interface for different sections
- [ ] Permission-based tab visibility
- [ ] Edit button for authorized users
- [ ] Link to public event page

---

## Task 3.3: Event Analytics Dashboard

### Description
Real-time analytics dashboard for individual events.

### Backend Requirements

```graphql
type EventAnalytics {
  totalRevenue: Float!
  ticketsSold: Int!
  pageViews: Int!
  conversionRate: Float!
  salesByDay: [SalesDataPoint!]!
  salesByTicketType: [TicketTypeSales!]!
  topReferrers: [ReferrerData!]!
  checkInRate: Float!
}

type SalesDataPoint {
  date: Date!
  tickets: Int!
  revenue: Float!
}

type TicketTypeSales {
  ticketType: TicketType!
  sold: Int!
  revenue: Float!
  percentOfTotal: Float!
}

extend type Query {
  eventAnalytics(eventId: ID!, period: String = "30d"): EventAnalytics!
    @hasPermission(permission: "event.analytics")
}
```

### Frontend Implementation

#### File: `apps/admin/src/components/events/EventAnalyticsTab.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { Box, Flex, Text, Card, Tabs, Grid, Progress } from '@radix-ui/themes';
import { TrendingUp, TrendingDown, Users, Eye, ShoppingCart, Percent } from 'lucide-react';
import { EVENT_ANALYTICS_QUERY } from '@/lib/graphql/queries/events';
import { StatCard } from '@/components/ui/BentoGrid';

interface EventAnalyticsTabProps {
  eventId: string;
}

export function EventAnalyticsTab({ eventId }: EventAnalyticsTabProps) {
  const { data, loading } = useQuery(EVENT_ANALYTICS_QUERY, {
    variables: { eventId, period: '30d' },
  });

  const analytics = data?.eventAnalytics;

  if (loading || !analytics) return <AnalyticsSkeleton />;

  return (
    <Box>
      {/* Key Metrics */}
      <Grid columns="4" gap="4" mb="5">
        <StatCard
          title="Total Revenue"
          value={`$${analytics.totalRevenue.toLocaleString()}`}
          icon={<TrendingUp size={18} />}
          color="green"
        />
        <StatCard
          title="Tickets Sold"
          value={analytics.ticketsSold}
          icon={<ShoppingCart size={18} />}
          color="blue"
        />
        <StatCard
          title="Page Views"
          value={analytics.pageViews.toLocaleString()}
          icon={<Eye size={18} />}
          color="purple"
        />
        <StatCard
          title="Conversion Rate"
          value={`${analytics.conversionRate.toFixed(1)}%`}
          icon={<Percent size={18} />}
          color="orange"
        />
      </Grid>

      <Grid columns="2" gap="4">
        {/* Sales Chart */}
        <Card>
          <Box p="4">
            <Text size="2" weight="medium" mb="3">Sales Over Time</Text>
            <SalesChart data={analytics.salesByDay} />
          </Box>
        </Card>

        {/* Sales by Ticket Type */}
        <Card>
          <Box p="4">
            <Text size="2" weight="medium" mb="3">Sales by Ticket Type</Text>
            <Flex direction="column" gap="3">
              {analytics.salesByTicketType.map((item) => (
                <Box key={item.ticketType.id}>
                  <Flex justify="between" mb="1">
                    <Text size="2">{item.ticketType.name}</Text>
                    <Text size="2" weight="medium">
                      {item.sold} sold (${item.revenue.toLocaleString()})
                    </Text>
                  </Flex>
                  <Progress value={item.percentOfTotal} />
                </Box>
              ))}
            </Flex>
          </Box>
        </Card>

        {/* Check-in Progress */}
        <Card>
          <Box p="4">
            <Text size="2" weight="medium" mb="3">Check-in Progress</Text>
            <Flex direction="column" align="center" py="4">
              <Box
                style={{
                  width: 120,
                  height: 120,
                  borderRadius: '50%',
                  background: `conic-gradient(var(--accent-9) ${analytics.checkInRate}%, var(--gray-a3) 0)`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Box
                  style={{
                    width: 100,
                    height: 100,
                    borderRadius: '50%',
                    backgroundColor: 'var(--color-background)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Text size="5" weight="bold">{analytics.checkInRate.toFixed(0)}%</Text>
                </Box>
              </Box>
              <Text size="2" color="gray" mt="2">Attendees Checked In</Text>
            </Flex>
          </Box>
        </Card>

        {/* Top Referrers */}
        <Card>
          <Box p="4">
            <Text size="2" weight="medium" mb="3">Top Traffic Sources</Text>
            <Flex direction="column" gap="2">
              {analytics.topReferrers.map((ref, i) => (
                <Flex key={i} justify="between" align="center">
                  <Text size="2">{ref.source}</Text>
                  <Flex align="center" gap="2">
                    <Text size="2" weight="medium">{ref.visits}</Text>
                    <Text size="1" color="gray">({ref.conversionRate}% conv)</Text>
                  </Flex>
                </Flex>
              ))}
            </Flex>
          </Box>
        </Card>
      </Grid>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Key metrics display (revenue, sales, views, conversion)
- [ ] Sales over time chart
- [ ] Sales breakdown by ticket type
- [ ] Check-in progress indicator
- [ ] Top traffic sources list
- [ ] Period selector (7d, 30d, all time)

---

## Task 3.4: Ticket Type Management

### Description
Manage ticket types for an event (create, edit, delete, enable/disable).

### Backend Requirements

```graphql
input UpdateTicketTypeInput {
  name: String
  description: String
  price: Float
  quantity: Int
  maxPerBooking: Int
  salesStartDate: DateTime
  salesEndDate: DateTime
  isActive: Boolean
}

extend type Mutation {
  createTicketType(eventId: ID!, input: CreateTicketTypeInput!): TicketType!
    @hasPermission(permission: "event.update")

  updateTicketType(id: ID!, input: UpdateTicketTypeInput!): TicketType!
    @hasPermission(permission: "event.update")

  deleteTicketType(id: ID!): Boolean!
    @hasPermission(permission: "event.update")

  toggleTicketTypeStatus(id: ID!): TicketType!
    @hasPermission(permission: "event.update")
}
```

### Frontend Implementation

#### File: `apps/admin/src/components/events/EventTicketsTab.tsx`

```tsx
'use client';

import { useState } from 'react';
import { useQuery, useMutation } from '@apollo/client/react';
import {
  Box, Flex, Text, Card, Button, Badge, Dialog, TextField,
  TextArea, Switch, Table
} from '@radix-ui/themes';
import { Plus, Edit, Trash2, ToggleLeft } from 'lucide-react';
import { EVENT_TICKET_TYPES_QUERY, CREATE_TICKET_TYPE, UPDATE_TICKET_TYPE, DELETE_TICKET_TYPE } from '@/lib/graphql/queries/tickets';

interface EventTicketsTabProps {
  eventId: string;
}

export function EventTicketsTab({ eventId }: EventTicketsTabProps) {
  const [editingTicket, setEditingTicket] = useState<TicketType | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  const { data, loading, refetch } = useQuery(EVENT_TICKET_TYPES_QUERY, {
    variables: { eventId },
  });

  const [createTicketType] = useMutation(CREATE_TICKET_TYPE, {
    onCompleted: () => {
      refetch();
      setIsDialogOpen(false);
    },
  });

  const [updateTicketType] = useMutation(UPDATE_TICKET_TYPE, {
    onCompleted: () => {
      refetch();
      setIsDialogOpen(false);
      setEditingTicket(null);
    },
  });

  const [deleteTicketType] = useMutation(DELETE_TICKET_TYPE, {
    onCompleted: () => refetch(),
  });

  const ticketTypes = data?.event?.ticketTypes ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="4">
        <Text size="3" weight="medium">Ticket Types</Text>
        <Button onClick={() => setIsDialogOpen(true)}>
          <Plus size={16} /> Add Ticket Type
        </Button>
      </Flex>

      <Table.Root variant="surface">
        <Table.Header>
          <Table.Row>
            <Table.ColumnHeaderCell>Name</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Price</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Available</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Sold</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {ticketTypes.map((ticket) => (
            <Table.Row key={ticket.id}>
              <Table.Cell>
                <Box>
                  <Text weight="medium">{ticket.name}</Text>
                  {ticket.description && (
                    <Text size="1" color="gray">{ticket.description}</Text>
                  )}
                </Box>
              </Table.Cell>
              <Table.Cell>${ticket.price.toFixed(2)}</Table.Cell>
              <Table.Cell>{ticket.available} / {ticket.quantity}</Table.Cell>
              <Table.Cell>{ticket.sold}</Table.Cell>
              <Table.Cell>
                <Badge color={ticket.isActive ? 'green' : 'gray'} variant="soft">
                  {ticket.isActive ? 'Active' : 'Inactive'}
                </Badge>
              </Table.Cell>
              <Table.Cell>
                <Flex gap="2">
                  <Button
                    variant="ghost"
                    size="1"
                    onClick={() => {
                      setEditingTicket(ticket);
                      setIsDialogOpen(true);
                    }}
                  >
                    <Edit size={14} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="1"
                    color="red"
                    onClick={() => {
                      if (confirm('Delete this ticket type?')) {
                        deleteTicketType({ variables: { id: ticket.id } });
                      }
                    }}
                    disabled={ticket.sold > 0}
                  >
                    <Trash2 size={14} />
                  </Button>
                </Flex>
              </Table.Cell>
            </Table.Row>
          ))}
        </Table.Body>
      </Table.Root>

      {/* Create/Edit Dialog */}
      <TicketTypeDialog
        open={isDialogOpen}
        onOpenChange={setIsDialogOpen}
        ticketType={editingTicket}
        eventId={eventId}
        onSave={(data) => {
          if (editingTicket) {
            updateTicketType({ variables: { id: editingTicket.id, input: data } });
          } else {
            createTicketType({ variables: { eventId, input: data } });
          }
        }}
      />
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] List all ticket types for event
- [ ] Create new ticket type dialog
- [ ] Edit existing ticket type
- [ ] Delete ticket type (only if no sales)
- [ ] Toggle active/inactive status
- [ ] Show sold vs available counts
- [ ] Validate price and quantity

---

## Dependencies

- Phase 1: Core Infrastructure (DataTable, Layout)
- Phase 2: Dashboard components (for event dashboard widgets)

## Estimated Time

- Task 3.1 (Event Listing): 6 hours
- Task 3.2 (Event Detail Page): 6 hours
- Task 3.3 (Event Analytics): 5 hours
- Task 3.4 (Ticket Management): 4 hours

**Total: ~21 hours**
