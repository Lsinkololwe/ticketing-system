# Phase 4: Booking & Finance Management

## Overview
Implement comprehensive booking management, refund processing, financial reporting, and organizer settlement capabilities.

---

## Task 4.1: Booking Management Page

### Description
Create a searchable, filterable booking listing with detail views.

### Backend Requirements

#### GraphQL Schema

```graphql
input BookingFilterInput {
  status: [BookingStatus!]
  eventId: ID
  userId: ID
  paymentStatus: PaymentStatus
  dateFrom: DateTime
  dateTo: DateTime
  searchQuery: String # Search by booking ID, email, or name
}

enum BookingStatus {
  PENDING
  CONFIRMED
  COMPLETED
  CANCELLED
  REFUNDED
  PARTIALLY_REFUNDED
}

enum PaymentStatus {
  PENDING
  PAID
  FAILED
  REFUNDED
}

type BookingPage {
  content: [Booking!]!
  totalElements: Int!
  totalPages: Int!
}

type Booking {
  id: ID!
  bookingNumber: String!
  user: User!
  event: Event!
  tickets: [BookingTicket!]!
  subtotal: Float!
  fees: Float!
  total: Float!
  status: BookingStatus!
  paymentStatus: PaymentStatus!
  paymentMethod: String
  createdAt: DateTime!
  updatedAt: DateTime!
  refunds: [Refund!]!
}

type BookingTicket {
  id: ID!
  ticketType: TicketType!
  quantity: Int!
  unitPrice: Float!
  total: Float!
  tickets: [Ticket!]!
}

extend type Query {
  bookingsPage(
    filter: BookingFilterInput
    page: Int = 0
    size: Int = 20
  ): BookingPage! @hasPermission(permission: "booking.read")

  booking(id: ID!): Booking @hasPermission(permission: "booking.read")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/bookings/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Badge, TextField, Select,
  Button, DropdownMenu
} from '@radix-ui/themes';
import { Search, Filter, MoreHorizontal, Eye, RefreshCcw, Download } from 'lucide-react';
import Link from 'next/link';
import { DataTable } from '@/components/ui/DataTable';
import { ORDERS_PAGE_QUERY } from '@/lib/graphql/queries/bookings';
import { formatDateTime, formatCurrency } from '@/lib/utils/format';

const statusColors: Record<string, 'gray' | 'blue' | 'green' | 'red' | 'orange' | 'purple'> = {
  PENDING: 'orange',
  CONFIRMED: 'blue',
  COMPLETED: 'green',
  CANCELLED: 'gray',
  REFUNDED: 'red',
  PARTIALLY_REFUNDED: 'purple',
};

const paymentStatusColors: Record<string, 'gray' | 'green' | 'red' | 'orange'> = {
  PENDING: 'orange',
  PAID: 'green',
  FAILED: 'red',
  REFUNDED: 'gray',
};

export default function BookingsPage() {
  const [filter, setFilter] = useState({
    status: null as string | null,
    paymentStatus: null as string | null,
    searchQuery: '',
    dateFrom: '',
    dateTo: '',
  });
  const [page, setPage] = useState(0);

  const { data, loading } = useQuery(ORDERS_PAGE_QUERY, {
    variables: {
      filter: {
        status: filter.status ? [filter.status] : null,
        paymentStatus: filter.paymentStatus || null,
        searchQuery: filter.searchQuery || null,
        dateFrom: filter.dateFrom || null,
        dateTo: filter.dateTo || null,
      },
      page,
      size: 20,
    },
  });

  const bookings = data?.bookingsPage?.content ?? [];

  const columns = [
    {
      key: 'bookingNumber',
      header: 'Booking #',
      sortable: true,
      render: (booking: Booking) => (
        <Text weight="medium" style={{ fontFamily: 'monospace' }}>
          {booking.bookingNumber}
        </Text>
      ),
    },
    {
      key: 'user',
      header: 'Customer',
      render: (booking: Booking) => (
        <Box>
          <Text size="2">{booking.user.name || 'Guest'}</Text>
          <Text size="1" color="gray">{booking.user.email}</Text>
        </Box>
      ),
    },
    {
      key: 'event',
      header: 'Event',
      render: (booking: Booking) => (
        <Text size="2">{booking.event.name}</Text>
      ),
    },
    {
      key: 'tickets',
      header: 'Tickets',
      render: (booking: Booking) => {
        const totalTickets = booking.tickets.reduce((sum, t) => sum + t.quantity, 0);
        return <Text size="2">{totalTickets}</Text>;
      },
    },
    {
      key: 'total',
      header: 'Total',
      sortable: true,
      render: (booking: Booking) => (
        <Text size="2" weight="medium">{formatCurrency(booking.total)}</Text>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (booking: Booking) => (
        <Flex gap="2">
          <Badge color={statusColors[booking.status]} variant="soft" size="1">
            {booking.status}
          </Badge>
          <Badge color={paymentStatusColors[booking.paymentStatus]} variant="outline" size="1">
            {booking.paymentStatus}
          </Badge>
        </Flex>
      ),
    },
    {
      key: 'createdAt',
      header: 'Date',
      sortable: true,
      render: (booking: Booking) => (
        <Text size="2" color="gray">{formatDateTime(booking.createdAt)}</Text>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (booking: Booking) => (
        <DropdownMenu.Root>
          <DropdownMenu.Trigger>
            <Button variant="ghost" size="1">
              <MoreHorizontal size={16} />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content>
            <DropdownMenu.Item asChild>
              <Link href={`/dashboard/bookings/${booking.id}`}>
                <Eye size={14} /> View Details
              </Link>
            </DropdownMenu.Item>
            {booking.status === 'COMPLETED' && booking.paymentStatus === 'PAID' && (
              <DropdownMenu.Item asChild>
                <Link href={`/dashboard/bookings/${booking.id}/refund`}>
                  <RefreshCcw size={14} /> Process Refund
                </Link>
              </DropdownMenu.Item>
            )}
            <DropdownMenu.Item>
              <Download size={14} /> Download Invoice
            </DropdownMenu.Item>
          </DropdownMenu.Content>
        </DropdownMenu.Root>
      ),
    },
  ];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Bookings</Heading>
          <Text color="gray" size="2">Manage all ticket bookings</Text>
        </Box>
        <Button variant="soft">
          <Download size={16} /> Export
        </Button>
      </Flex>

      {/* Filters */}
      <Flex gap="3" mb="4" wrap="wrap">
        <TextField.Root
          placeholder="Search by booking #, email, name..."
          value={filter.searchQuery}
          onChange={(e) => setFilter(f => ({ ...f, searchQuery: e.target.value }))}
          style={{ width: '300px' }}
        >
          <TextField.Slot>
            <Search size={16} />
          </TextField.Slot>
        </TextField.Root>
        <Select.Root
          value={filter.status || 'all'}
          onValueChange={(v) => setFilter(f => ({ ...f, status: v === 'all' ? null : v }))}
        >
          <Select.Trigger placeholder="Booking Status" />
          <Select.Content>
            <Select.Item value="all">All Status</Select.Item>
            <Select.Item value="PENDING">Pending</Select.Item>
            <Select.Item value="CONFIRMED">Confirmed</Select.Item>
            <Select.Item value="COMPLETED">Completed</Select.Item>
            <Select.Item value="CANCELLED">Cancelled</Select.Item>
            <Select.Item value="REFUNDED">Refunded</Select.Item>
          </Select.Content>
        </Select.Root>
        <Select.Root
          value={filter.paymentStatus || 'all'}
          onValueChange={(v) => setFilter(f => ({ ...f, paymentStatus: v === 'all' ? null : v }))}
        >
          <Select.Trigger placeholder="Payment Status" />
          <Select.Content>
            <Select.Item value="all">All Payments</Select.Item>
            <Select.Item value="PENDING">Pending</Select.Item>
            <Select.Item value="PAID">Paid</Select.Item>
            <Select.Item value="FAILED">Failed</Select.Item>
            <Select.Item value="REFUNDED">Refunded</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      <DataTable
        data={bookings}
        columns={columns}
        loading={loading}
        onRowClick={(booking) => window.location.href = `/dashboard/bookings/${booking.id}`}
      />
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Booking listing with pagination
- [ ] Search by booking number, email, customer name
- [ ] Filter by booking status, payment status, date range
- [ ] Booking detail navigation
- [ ] Export bookings to CSV
- [ ] Refund action for paid bookings

---

## Task 4.2: Booking Detail Page

### Description
Comprehensive booking detail view with timeline and actions.

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/bookings/[id]/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, useRouter } from 'next/navigation';
import {
  Box, Flex, Heading, Text, Card, Badge, Button, Separator,
  Table, Dialog, TextArea, Callout
} from '@radix-ui/themes';
import {
  ArrowLeft, User, Calendar, CreditCard, Ticket, RefreshCcw,
  Download, Send, CheckCircle, XCircle, Clock
} from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { ORDER_DETAIL_QUERY, RESEND_CONFIRMATION, CANCEL_ORDER } from '@/lib/graphql/queries/bookings';
import { formatDateTime, formatCurrency } from '@/lib/utils/format';
import { PermissionGuard } from '@/components/auth/PermissionGuard';

export default function BookingDetailPage() {
  const params = useParams();
  const router = useRouter();
  const bookingId = params.id as string;
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');

  const { data, loading, refetch } = useQuery(ORDER_DETAIL_QUERY, {
    variables: { id: bookingId },
  });

  const [resendConfirmation, { loading: resending }] = useMutation(RESEND_CONFIRMATION);
  const [cancelBooking, { loading: cancelling }] = useMutation(CANCEL_ORDER, {
    onCompleted: () => {
      refetch();
      setCancelDialogOpen(false);
    },
  });

  if (loading) return <BookingDetailSkeleton />;
  if (!data?.booking) return <BookingNotFound />;

  const booking = data.booking;

  return (
    <Box>
      {/* Header */}
      <Flex align="center" gap="3" mb="5">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft size={16} />
        </Button>
        <Box style={{ flex: 1 }}>
          <Flex align="center" gap="3">
            <Heading size="6">Booking {booking.bookingNumber}</Heading>
            <Badge color={statusColors[booking.status]} variant="soft">
              {booking.status}
            </Badge>
            <Badge color={paymentStatusColors[booking.paymentStatus]} variant="outline">
              {booking.paymentStatus}
            </Badge>
          </Flex>
          <Text color="gray" size="2">
            Placed on {formatDateTime(booking.createdAt)}
          </Text>
        </Box>
        <Flex gap="2">
          <Button
            variant="soft"
            onClick={() => resendConfirmation({ variables: { bookingId: booking.id } })}
            disabled={resending}
          >
            <Send size={16} /> Resend Confirmation
          </Button>
          <Button variant="soft">
            <Download size={16} /> Download Invoice
          </Button>
          <PermissionGuard permission="booking.refund">
            {booking.paymentStatus === 'PAID' && booking.status !== 'REFUNDED' && (
              <Button variant="soft" color="orange" asChild>
                <Link href={`/dashboard/bookings/${booking.id}/refund`}>
                  <RefreshCcw size={16} /> Refund
                </Link>
              </Button>
            )}
          </PermissionGuard>
          <PermissionGuard permission="booking.cancel">
            {booking.status === 'PENDING' && (
              <Button
                variant="soft"
                color="red"
                onClick={() => setCancelDialogOpen(true)}
              >
                <XCircle size={16} /> Cancel
              </Button>
            )}
          </PermissionGuard>
        </Flex>
      </Flex>

      <Flex gap="5">
        {/* Main Content */}
        <Box style={{ flex: 1 }}>
          {/* Booking Items */}
          <Card mb="4">
            <Box p="4">
              <Text size="3" weight="medium" mb="3">Booking Items</Text>
              <Table.Root>
                <Table.Header>
                  <Table.Row>
                    <Table.ColumnHeaderCell>Ticket Type</Table.ColumnHeaderCell>
                    <Table.ColumnHeaderCell>Qty</Table.ColumnHeaderCell>
                    <Table.ColumnHeaderCell>Unit Price</Table.ColumnHeaderCell>
                    <Table.ColumnHeaderCell>Total</Table.ColumnHeaderCell>
                  </Table.Row>
                </Table.Header>
                <Table.Body>
                  {booking.tickets.map((item) => (
                    <Table.Row key={item.id}>
                      <Table.Cell>
                        <Box>
                          <Text weight="medium">{item.ticketType.name}</Text>
                          <Text size="1" color="gray">{booking.event.name}</Text>
                        </Box>
                      </Table.Cell>
                      <Table.Cell>{item.quantity}</Table.Cell>
                      <Table.Cell>{formatCurrency(item.unitPrice)}</Table.Cell>
                      <Table.Cell>{formatCurrency(item.total)}</Table.Cell>
                    </Table.Row>
                  ))}
                </Table.Body>
              </Table.Root>

              <Separator my="3" />

              {/* Totals */}
              <Flex direction="column" align="end" gap="1">
                <Flex gap="5">
                  <Text color="gray">Subtotal</Text>
                  <Text>{formatCurrency(booking.subtotal)}</Text>
                </Flex>
                <Flex gap="5">
                  <Text color="gray">Fees</Text>
                  <Text>{formatCurrency(booking.fees)}</Text>
                </Flex>
                <Flex gap="5">
                  <Text weight="bold">Total</Text>
                  <Text weight="bold" size="4">{formatCurrency(booking.total)}</Text>
                </Flex>
              </Flex>
            </Box>
          </Card>

          {/* Issued Tickets */}
          <Card mb="4">
            <Box p="4">
              <Text size="3" weight="medium" mb="3">Issued Tickets</Text>
              <Flex direction="column" gap="2">
                {booking.tickets.flatMap(item =>
                  item.tickets.map(ticket => (
                    <Flex
                      key={ticket.id}
                      justify="between"
                      align="center"
                      p="3"
                      style={{
                        backgroundColor: 'var(--gray-a2)',
                        borderRadius: 'var(--radius-2)',
                      }}
                    >
                      <Flex align="center" gap="3">
                        <Ticket size={18} />
                        <Box>
                          <Text size="2" style={{ fontFamily: 'monospace' }}>
                            {ticket.ticketCode}
                          </Text>
                          <Text size="1" color="gray">{item.ticketType.name}</Text>
                        </Box>
                      </Flex>
                      <Flex align="center" gap="2">
                        <Badge
                          color={ticket.status === 'USED' ? 'green' : ticket.status === 'VALID' ? 'blue' : 'gray'}
                          variant="soft"
                        >
                          {ticket.status}
                        </Badge>
                        {ticket.checkedInAt && (
                          <Text size="1" color="gray">
                            Checked in: {formatDateTime(ticket.checkedInAt)}
                          </Text>
                        )}
                      </Flex>
                    </Flex>
                  ))
                )}
              </Flex>
            </Box>
          </Card>

          {/* Refund History */}
          {booking.refunds.length > 0 && (
            <Card>
              <Box p="4">
                <Text size="3" weight="medium" mb="3">Refund History</Text>
                <Flex direction="column" gap="2">
                  {booking.refunds.map(refund => (
                    <Flex
                      key={refund.id}
                      justify="between"
                      align="center"
                      p="3"
                      style={{
                        backgroundColor: 'var(--red-a2)',
                        borderRadius: 'var(--radius-2)',
                      }}
                    >
                      <Box>
                        <Text size="2">Refund #{refund.id.slice(-8)}</Text>
                        <Text size="1" color="gray">{refund.reason}</Text>
                      </Box>
                      <Box style={{ textAlign: 'right' }}>
                        <Text size="2" weight="medium" color="red">
                          -{formatCurrency(refund.amount)}
                        </Text>
                        <Text size="1" color="gray">
                          {formatDateTime(refund.createdAt)}
                        </Text>
                      </Box>
                    </Flex>
                  ))}
                </Flex>
              </Box>
            </Card>
          )}
        </Box>

        {/* Sidebar */}
        <Box style={{ width: '320px' }}>
          {/* Customer Info */}
          <Card mb="4">
            <Box p="4">
              <Text size="2" weight="medium" mb="3">Customer</Text>
              <Flex direction="column" gap="2">
                <Flex align="center" gap="2">
                  <User size={14} />
                  <Text size="2">{booking.user.name || 'Guest User'}</Text>
                </Flex>
                <Text size="2" color="gray">{booking.user.email}</Text>
                {booking.user.phone && (
                  <Text size="2" color="gray">{booking.user.phone}</Text>
                )}
                <Button variant="soft" size="1" asChild mt="2">
                  <Link href={`/dashboard/users/${booking.user.id}`}>
                    View Profile
                  </Link>
                </Button>
              </Flex>
            </Box>
          </Card>

          {/* Payment Info */}
          <Card mb="4">
            <Box p="4">
              <Text size="2" weight="medium" mb="3">Payment</Text>
              <Flex direction="column" gap="2">
                <Flex justify="between">
                  <Text size="2" color="gray">Method</Text>
                  <Text size="2">{booking.paymentMethod}</Text>
                </Flex>
                <Flex justify="between">
                  <Text size="2" color="gray">Status</Text>
                  <Badge color={paymentStatusColors[booking.paymentStatus]} variant="soft">
                    {booking.paymentStatus}
                  </Badge>
                </Flex>
                {booking.paymentId && (
                  <Flex justify="between">
                    <Text size="2" color="gray">Transaction ID</Text>
                    <Text size="1" style={{ fontFamily: 'monospace' }}>
                      {booking.paymentId.slice(0, 16)}...
                    </Text>
                  </Flex>
                )}
              </Flex>
            </Box>
          </Card>

          {/* Event Info */}
          <Card>
            <Box p="4">
              <Text size="2" weight="medium" mb="3">Event</Text>
              <Link href={`/dashboard/events/${booking.event.id}`}>
                <Flex direction="column" gap="2">
                  <Text size="2" weight="medium">{booking.event.name}</Text>
                  <Flex align="center" gap="1">
                    <Calendar size={12} />
                    <Text size="1" color="gray">
                      {formatDateTime(booking.event.startDate)}
                    </Text>
                  </Flex>
                </Flex>
              </Link>
            </Box>
          </Card>
        </Box>
      </Flex>

      {/* Cancel Dialog */}
      <Dialog.Root open={cancelDialogOpen} onOpenChange={setCancelDialogOpen}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Cancel Booking</Dialog.Title>
          <Dialog.Description>
            This will cancel booking {booking.bookingNumber} and invalidate all associated tickets.
          </Dialog.Description>
          <Box mt="3">
            <TextArea
              placeholder="Reason for cancellation (optional)"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
            />
          </Box>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Keep Booking</Button>
            </Dialog.Close>
            <Button
              color="red"
              onClick={() => cancelBooking({
                variables: { id: booking.id, reason: cancelReason }
              })}
              disabled={cancelling}
            >
              Cancel Booking
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Display full booking details
- [ ] Show all booking items with prices
- [ ] List issued tickets with status
- [ ] Display refund history
- [ ] Customer information sidebar
- [ ] Payment information display
- [ ] Resend confirmation email action
- [ ] Download invoice action
- [ ] Cancel booking functionality
- [ ] Link to refund page

---

## Task 4.3: Refund Processing Page

### Description
Process full or partial refunds for bookings.

### Backend Requirements

```graphql
input ProcessRefundInput {
  bookingId: ID!
  amount: Float!
  reason: String!
  ticketIds: [ID!] # For partial refunds, specify which tickets
  notifyCustomer: Boolean = true
}

type Refund {
  id: ID!
  booking: Booking!
  amount: Float!
  reason: String!
  status: RefundStatus!
  processedBy: User!
  createdAt: DateTime!
  completedAt: DateTime
}

enum RefundStatus {
  PENDING
  PROCESSING
  COMPLETED
  FAILED
}

extend type Mutation {
  processRefund(input: ProcessRefundInput!): Refund!
    @hasPermission(permission: "booking.refund")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/bookings/[id]/refund/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Button, TextField,
  TextArea, Checkbox, Callout, Separator, RadioGroup
} from '@radix-ui/themes';
import { ArrowLeft, AlertTriangle, CheckCircle } from 'lucide-react';
import { ORDER_DETAIL_QUERY, PROCESS_REFUND } from '@/lib/graphql/queries/bookings';
import { formatCurrency } from '@/lib/utils/format';

export default function RefundPage() {
  const params = useParams();
  const router = useRouter();
  const bookingId = params.id as string;

  const [refundType, setRefundType] = useState<'full' | 'partial'>('full');
  const [customAmount, setCustomAmount] = useState('');
  const [selectedTickets, setSelectedTickets] = useState<string[]>([]);
  const [reason, setReason] = useState('');
  const [notifyCustomer, setNotifyCustomer] = useState(true);
  const [success, setSuccess] = useState(false);

  const { data, loading } = useQuery(ORDER_DETAIL_QUERY, {
    variables: { id: bookingId },
  });

  const [processRefund, { loading: processing, error }] = useMutation(PROCESS_REFUND, {
    onCompleted: () => setSuccess(true),
  });

  if (loading) return <RefundSkeleton />;
  if (!data?.booking) return <BookingNotFound />;

  const booking = data.booking;
  const maxRefundable = booking.total - booking.refunds.reduce((sum, r) => sum + r.amount, 0);

  const refundAmount = refundType === 'full'
    ? maxRefundable
    : parseFloat(customAmount) || 0;

  const handleSubmit = () => {
    processRefund({
      variables: {
        input: {
          bookingId: booking.id,
          amount: refundAmount,
          reason,
          ticketIds: refundType === 'partial' ? selectedTickets : null,
          notifyCustomer,
        },
      },
    });
  };

  if (success) {
    return (
      <Box style={{ maxWidth: 500, margin: '0 auto' }}>
        <Card>
          <Flex direction="column" align="center" p="6" gap="4">
            <Box
              p="4"
              style={{
                backgroundColor: 'var(--green-a3)',
                borderRadius: '50%',
              }}
            >
              <CheckCircle size={48} style={{ color: 'var(--green-11)' }} />
            </Box>
            <Heading size="5">Refund Processed</Heading>
            <Text color="gray" align="center">
              {formatCurrency(refundAmount)} has been refunded to the customer.
            </Text>
            <Button onClick={() => router.push(`/dashboard/bookings/${bookingId}`)}>
              Back to Booking
            </Button>
          </Flex>
        </Card>
      </Box>
    );
  }

  return (
    <Box style={{ maxWidth: 700 }}>
      <Flex align="center" gap="3" mb="5">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft size={16} />
        </Button>
        <Box>
          <Heading size="6">Process Refund</Heading>
          <Text color="gray" size="2">Booking {booking.bookingNumber}</Text>
        </Box>
      </Flex>

      {error && (
        <Callout.Root color="red" mb="4">
          <Callout.Icon><AlertTriangle size={16} /></Callout.Icon>
          <Callout.Text>{error.message}</Callout.Text>
        </Callout.Root>
      )}

      <Card>
        <Box p="5">
          {/* Booking Summary */}
          <Box mb="5">
            <Text size="2" weight="medium" mb="2">Booking Summary</Text>
            <Flex justify="between" mb="1">
              <Text size="2" color="gray">Booking Total</Text>
              <Text size="2">{formatCurrency(booking.total)}</Text>
            </Flex>
            <Flex justify="between" mb="1">
              <Text size="2" color="gray">Already Refunded</Text>
              <Text size="2" color="red">
                -{formatCurrency(booking.total - maxRefundable)}
              </Text>
            </Flex>
            <Flex justify="between">
              <Text size="2" weight="medium">Maximum Refundable</Text>
              <Text size="2" weight="medium">{formatCurrency(maxRefundable)}</Text>
            </Flex>
          </Box>

          <Separator mb="5" />

          {/* Refund Type */}
          <Box mb="5">
            <Text size="2" weight="medium" mb="3">Refund Type</Text>
            <RadioGroup.Root value={refundType} onValueChange={(v) => setRefundType(v as 'full' | 'partial')}>
              <Flex direction="column" gap="2">
                <Text as="label" size="2">
                  <Flex align="center" gap="2">
                    <RadioGroup.Item value="full" />
                    Full Refund ({formatCurrency(maxRefundable)})
                  </Flex>
                </Text>
                <Text as="label" size="2">
                  <Flex align="center" gap="2">
                    <RadioGroup.Item value="partial" />
                    Partial Refund
                  </Flex>
                </Text>
              </Flex>
            </RadioGroup.Root>
          </Box>

          {refundType === 'partial' && (
            <Box mb="5">
              <Text size="2" weight="medium" mb="2">Refund Amount</Text>
              <TextField.Root
                type="number"
                placeholder="0.00"
                value={customAmount}
                onChange={(e) => setCustomAmount(e.target.value)}
                style={{ maxWidth: 200 }}
              >
                <TextField.Slot>$</TextField.Slot>
              </TextField.Root>
              {parseFloat(customAmount) > maxRefundable && (
                <Text size="1" color="red" mt="1">
                  Amount exceeds maximum refundable
                </Text>
              )}
            </Box>
          )}

          <Separator mb="5" />

          {/* Reason */}
          <Box mb="5">
            <Text size="2" weight="medium" mb="2">Reason for Refund *</Text>
            <TextArea
              placeholder="Enter the reason for this refund..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              required
            />
          </Box>

          {/* Notify Customer */}
          <Box mb="5">
            <Text as="label" size="2">
              <Flex align="center" gap="2">
                <Checkbox
                  checked={notifyCustomer}
                  onCheckedChange={(v) => setNotifyCustomer(v === true)}
                />
                Send refund confirmation email to customer
              </Flex>
            </Text>
          </Box>

          <Separator mb="5" />

          {/* Summary */}
          <Callout.Root color="orange" mb="5">
            <Callout.Icon><AlertTriangle size={16} /></Callout.Icon>
            <Callout.Text>
              You are about to refund <strong>{formatCurrency(refundAmount)}</strong> to {booking.user.email}.
              This action cannot be undone.
            </Callout.Text>
          </Callout.Root>

          {/* Actions */}
          <Flex justify="end" gap="3">
            <Button variant="soft" color="gray" onClick={() => router.back()}>
              Cancel
            </Button>
            <Button
              color="red"
              onClick={handleSubmit}
              disabled={processing || !reason || refundAmount <= 0 || refundAmount > maxRefundable}
            >
              {processing ? 'Processing...' : `Refund ${formatCurrency(refundAmount)}`}
            </Button>
          </Flex>
        </Box>
      </Card>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Show booking summary and max refundable amount
- [ ] Full refund option
- [ ] Partial refund with custom amount
- [ ] Reason field (required)
- [ ] Customer notification toggle
- [ ] Validation (amount <= max refundable)
- [ ] Success confirmation page
- [ ] Error handling

---

## Task 4.4: Financial Dashboard

### Description
Comprehensive financial overview for FINANCE role administrators.

### Backend Requirements

```graphql
type FinancialSummary {
  totalRevenue: Float!
  totalRefunds: Float!
  netRevenue: Float!
  pendingSettlements: Float!
  completedSettlements: Float!
  platformFees: Float!
  organizerPayouts: Float!
}

type RevenueByPeriod {
  period: String!
  revenue: Float!
  refunds: Float!
  net: Float!
  bookingCount: Int!
}

type PaymentMethodBreakdown {
  method: String!
  count: Int!
  total: Float!
  percentage: Float!
}

extend type Query {
  financialSummary(period: String = "30d"): FinancialSummary!
    @hasPermission(permission: "finance.read")

  revenueByPeriod(
    groupBy: String = "day"
    startDate: DateTime
    endDate: DateTime
  ): [RevenueByPeriod!]! @hasPermission(permission: "finance.read")

  paymentMethodBreakdown(period: String = "30d"): [PaymentMethodBreakdown!]!
    @hasPermission(permission: "finance.read")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/finance/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { Box, Flex, Heading, Text, Card, Tabs, Grid } from '@radix-ui/themes';
import {
  DollarSign, TrendingUp, TrendingDown, RefreshCcw,
  Building, Percent
} from 'lucide-react';
import { BentoGrid, StatCard } from '@/components/ui/BentoGrid';
import { RevenueChart } from '@/components/finance/RevenueChart';
import { PaymentMethodsChart } from '@/components/finance/PaymentMethodsChart';
import { RecentTransactions } from '@/components/finance/RecentTransactions';
import { PendingSettlements } from '@/components/finance/PendingSettlements';
import { FINANCIAL_SUMMARY_QUERY, REVENUE_BY_PERIOD_QUERY } from '@/lib/graphql/queries/finance';
import { formatCurrency } from '@/lib/utils/format';

export default function FinancePage() {
  const { data: summaryData, loading: summaryLoading } = useQuery(FINANCIAL_SUMMARY_QUERY, {
    variables: { period: '30d' },
  });

  const { data: revenueData } = useQuery(REVENUE_BY_PERIOD_QUERY, {
    variables: { groupBy: 'day' },
  });

  const summary = summaryData?.financialSummary;

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Financial Overview</Heading>
          <Text color="gray" size="2">Last 30 days performance</Text>
        </Box>
        <Tabs.Root defaultValue="30d">
          <Tabs.List size="1">
            <Tabs.Trigger value="7d">7D</Tabs.Trigger>
            <Tabs.Trigger value="30d">30D</Tabs.Trigger>
            <Tabs.Trigger value="90d">90D</Tabs.Trigger>
            <Tabs.Trigger value="ytd">YTD</Tabs.Trigger>
          </Tabs.List>
        </Tabs.Root>
      </Flex>

      {/* Key Metrics */}
      <Grid columns="4" gap="4" mb="5">
        <StatCard
          title="Total Revenue"
          value={formatCurrency(summary?.totalRevenue ?? 0)}
          change={{ value: 12, trend: 'up' }}
          icon={<DollarSign size={18} />}
          color="green"
        />
        <StatCard
          title="Net Revenue"
          value={formatCurrency(summary?.netRevenue ?? 0)}
          change={{ value: 8, trend: 'up' }}
          icon={<TrendingUp size={18} />}
          color="blue"
        />
        <StatCard
          title="Total Refunds"
          value={formatCurrency(summary?.totalRefunds ?? 0)}
          change={{ value: 3, trend: 'down' }}
          icon={<RefreshCcw size={18} />}
          color="red"
        />
        <StatCard
          title="Platform Fees"
          value={formatCurrency(summary?.platformFees ?? 0)}
          icon={<Percent size={18} />}
          color="purple"
        />
      </Grid>

      {/* Charts Row */}
      <Grid columns="3" gap="4" mb="5">
        {/* Revenue Chart - 2 columns */}
        <Box gridColumn="span 2">
          <Card style={{ height: '400px' }}>
            <Box p="4" style={{ height: '100%' }}>
              <Text size="2" weight="medium" mb="3">Revenue Over Time</Text>
              <RevenueChart data={revenueData?.revenueByPeriod ?? []} />
            </Box>
          </Card>
        </Box>

        {/* Payment Methods */}
        <Card style={{ height: '400px' }}>
          <Box p="4" style={{ height: '100%' }}>
            <Text size="2" weight="medium" mb="3">Payment Methods</Text>
            <PaymentMethodsChart />
          </Box>
        </Card>
      </Grid>

      {/* Bottom Row */}
      <Grid columns="2" gap="4">
        {/* Pending Settlements */}
        <Card>
          <Box p="4">
            <Flex justify="between" align="center" mb="3">
              <Text size="2" weight="medium">Pending Settlements</Text>
              <Text size="2" color="orange" weight="medium">
                {formatCurrency(summary?.pendingSettlements ?? 0)}
              </Text>
            </Flex>
            <PendingSettlements />
          </Box>
        </Card>

        {/* Recent Transactions */}
        <Card>
          <Box p="4">
            <Text size="2" weight="medium" mb="3">Recent Transactions</Text>
            <RecentTransactions />
          </Box>
        </Card>
      </Grid>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Key financial metrics display
- [ ] Revenue over time chart
- [ ] Payment method breakdown
- [ ] Period selector (7D, 30D, 90D, YTD)
- [ ] Pending settlements list
- [ ] Recent transactions list
- [ ] Export financial report

---

## Task 4.5: Settlement Management

### Description
Manage organizer payouts and settlements.

### Backend Requirements

```graphql
type Settlement {
  id: ID!
  organizer: Organizer!
  period: SettlementPeriod!
  grossAmount: Float!
  platformFee: Float!
  refunds: Float!
  netAmount: Float!
  status: SettlementStatus!
  events: [Event!]!
  bookings: [Booking!]!
  createdAt: DateTime!
  processedAt: DateTime
  paidAt: DateTime
  bankAccount: BankAccount
}

type SettlementPeriod {
  startDate: Date!
  endDate: Date!
}

enum SettlementStatus {
  PENDING
  PROCESSING
  COMPLETED
  FAILED
  ON_HOLD
}

type SettlementPage {
  content: [Settlement!]!
  totalElements: Int!
  totalPages: Int!
}

extend type Query {
  settlementsPage(
    status: SettlementStatus
    organizerId: ID
    page: Int = 0
    size: Int = 20
  ): SettlementPage! @hasPermission(permission: "finance.settlements.read")

  settlement(id: ID!): Settlement @hasPermission(permission: "finance.settlements.read")
}

extend type Mutation {
  processSettlement(id: ID!): Settlement!
    @hasPermission(permission: "finance.settlements.process")

  putSettlementOnHold(id: ID!, reason: String!): Settlement!
    @hasPermission(permission: "finance.settlements.manage")

  releaseSettlementHold(id: ID!): Settlement!
    @hasPermission(permission: "finance.settlements.manage")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/finance/settlements/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Badge, Button, Select,
  Dialog, TextArea, Table
} from '@radix-ui/themes';
import { Building, DollarSign, Clock, CheckCircle, AlertTriangle } from 'lucide-react';
import Link from 'next/link';
import { SETTLEMENTS_PAGE_QUERY, PROCESS_SETTLEMENT, PUT_ON_HOLD } from '@/lib/graphql/queries/settlements';
import { formatCurrency, formatDate } from '@/lib/utils/format';

const statusColors: Record<string, 'gray' | 'blue' | 'green' | 'red' | 'orange'> = {
  PENDING: 'orange',
  PROCESSING: 'blue',
  COMPLETED: 'green',
  FAILED: 'red',
  ON_HOLD: 'gray',
};

export default function SettlementsPage() {
  const [statusFilter, setStatusFilter] = useState<string>('PENDING');
  const [holdDialogOpen, setHoldDialogOpen] = useState(false);
  const [selectedSettlement, setSelectedSettlement] = useState<Settlement | null>(null);
  const [holdReason, setHoldReason] = useState('');

  const { data, loading, refetch } = useQuery(SETTLEMENTS_PAGE_QUERY, {
    variables: { status: statusFilter || null },
  });

  const [processSettlement, { loading: processing }] = useMutation(PROCESS_SETTLEMENT, {
    onCompleted: () => refetch(),
  });

  const [putOnHold] = useMutation(PUT_ON_HOLD, {
    onCompleted: () => {
      refetch();
      setHoldDialogOpen(false);
      setHoldReason('');
    },
  });

  const settlements = data?.settlementsPage?.content ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Settlements</Heading>
          <Text color="gray" size="2">Manage organizer payouts</Text>
        </Box>
        <Flex gap="3">
          <Select.Root value={statusFilter} onValueChange={setStatusFilter}>
            <Select.Trigger placeholder="Filter by status" />
            <Select.Content>
              <Select.Item value="all">All Status</Select.Item>
              <Select.Item value="PENDING">Pending</Select.Item>
              <Select.Item value="PROCESSING">Processing</Select.Item>
              <Select.Item value="COMPLETED">Completed</Select.Item>
              <Select.Item value="ON_HOLD">On Hold</Select.Item>
            </Select.Content>
          </Select.Root>
        </Flex>
      </Flex>

      {/* Summary Cards */}
      <Flex gap="4" mb="5">
        <Card style={{ flex: 1 }}>
          <Flex p="4" gap="3" align="center">
            <Box p="2" style={{ backgroundColor: 'var(--orange-a3)', borderRadius: 'var(--radius-2)' }}>
              <Clock size={20} style={{ color: 'var(--orange-11)' }} />
            </Box>
            <Box>
              <Text size="1" color="gray">Pending</Text>
              <Text size="4" weight="bold">{formatCurrency(125000)}</Text>
            </Box>
          </Flex>
        </Card>
        <Card style={{ flex: 1 }}>
          <Flex p="4" gap="3" align="center">
            <Box p="2" style={{ backgroundColor: 'var(--blue-a3)', borderRadius: 'var(--radius-2)' }}>
              <Building size={20} style={{ color: 'var(--blue-11)' }} />
            </Box>
            <Box>
              <Text size="1" color="gray">Processing</Text>
              <Text size="4" weight="bold">{formatCurrency(45000)}</Text>
            </Box>
          </Flex>
        </Card>
        <Card style={{ flex: 1 }}>
          <Flex p="4" gap="3" align="center">
            <Box p="2" style={{ backgroundColor: 'var(--green-a3)', borderRadius: 'var(--radius-2)' }}>
              <CheckCircle size={20} style={{ color: 'var(--green-11)' }} />
            </Box>
            <Box>
              <Text size="1" color="gray">Paid (30d)</Text>
              <Text size="4" weight="bold">{formatCurrency(890000)}</Text>
            </Box>
          </Flex>
        </Card>
      </Flex>

      {/* Settlements Table */}
      <Card>
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Organizer</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Period</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Gross</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Fees</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Net Amount</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {settlements.map((settlement) => (
              <Table.Row key={settlement.id}>
                <Table.Cell>
                  <Box>
                    <Text weight="medium">{settlement.organizer.name}</Text>
                    <Text size="1" color="gray">{settlement.events.length} events</Text>
                  </Box>
                </Table.Cell>
                <Table.Cell>
                  <Text size="2">
                    {formatDate(settlement.period.startDate)} - {formatDate(settlement.period.endDate)}
                  </Text>
                </Table.Cell>
                <Table.Cell>{formatCurrency(settlement.grossAmount)}</Table.Cell>
                <Table.Cell color="red">-{formatCurrency(settlement.platformFee)}</Table.Cell>
                <Table.Cell>
                  <Text weight="medium">{formatCurrency(settlement.netAmount)}</Text>
                </Table.Cell>
                <Table.Cell>
                  <Badge color={statusColors[settlement.status]} variant="soft">
                    {settlement.status}
                  </Badge>
                </Table.Cell>
                <Table.Cell>
                  <Flex gap="2">
                    {settlement.status === 'PENDING' && (
                      <>
                        <Button
                          size="1"
                          onClick={() => processSettlement({ variables: { id: settlement.id } })}
                          disabled={processing}
                        >
                          Process
                        </Button>
                        <Button
                          size="1"
                          variant="soft"
                          color="gray"
                          onClick={() => {
                            setSelectedSettlement(settlement);
                            setHoldDialogOpen(true);
                          }}
                        >
                          Hold
                        </Button>
                      </>
                    )}
                    <Button size="1" variant="ghost" asChild>
                      <Link href={`/dashboard/finance/settlements/${settlement.id}`}>
                        Details
                      </Link>
                    </Button>
                  </Flex>
                </Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Card>

      {/* Hold Dialog */}
      <Dialog.Root open={holdDialogOpen} onOpenChange={setHoldDialogOpen}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Put Settlement On Hold</Dialog.Title>
          <Dialog.Description>
            Settlement for {selectedSettlement?.organizer.name} will be paused.
          </Dialog.Description>
          <Box mt="3">
            <Text size="2" weight="medium" mb="2">Reason</Text>
            <TextArea
              placeholder="Enter reason for holding this settlement..."
              value={holdReason}
              onChange={(e) => setHoldReason(e.target.value)}
            />
          </Box>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              color="orange"
              onClick={() => putOnHold({
                variables: { id: selectedSettlement?.id, reason: holdReason }
              })}
              disabled={!holdReason}
            >
              Put On Hold
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] List all settlements with status filter
- [ ] Summary cards (pending, processing, paid)
- [ ] Process settlement action
- [ ] Put on hold functionality with reason
- [ ] Settlement detail page link
- [ ] Period and amount display
- [ ] Organizer information

---

## Dependencies

- Phase 1: Core Infrastructure (DataTable, Layout)
- Phase 3: Event Management (for linking bookings to events)

## Estimated Time

- Task 4.1 (Booking Listing): 5 hours
- Task 4.2 (Booking Detail): 6 hours
- Task 4.3 (Refund Processing): 5 hours
- Task 4.4 (Financial Dashboard): 6 hours
- Task 4.5 (Settlements): 6 hours

**Total: ~28 hours**
