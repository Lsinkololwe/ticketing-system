# Terminology Migration Guide

## Overview

This document outlines the migration from e-commerce terminology ("Order") to event-specific terminology ("Booking") across the entire ticketing system.

## Terminology Mapping

| Old Term | New Term | Notes |
|----------|----------|-------|
| `Order` | `Booking` | Primary transaction entity |
| `OrderItem` | `BookingItem` | Line items within a booking |
| `OrderStatus` | `BookingStatus` | Status enum |
| `OrderNumber` | `BookingNumber` | Reference number (e.g., BK-2024-XXXXX) |
| `OrderService` | `BookingService` | Backend service |
| `OrderRepository` | `BookingRepository` | Data repository |
| `OrderController` | `BookingController` | REST/GraphQL controller |
| `ordersPage` | `bookingsPage` | GraphQL query |
| `createOrder` | `createBooking` | GraphQL mutation |
| `cancelOrder` | `cancelBooking` | GraphQL mutation |

## Booking Number Format

```
BK-{YEAR}-{SEQUENTIAL_ID}
Example: BK-2024-00001234

Alternative formats:
- EVT-{EVENT_ID}-{SEQ}: EVT-1234-0001
- {DATE}-{RANDOM}: 20240305-X7K9M2
```

## Status Definitions

### BookingStatus

```java
public enum BookingStatus {
    PENDING,              // Awaiting payment
    CONFIRMED,            // Payment successful, tickets issued
    CANCELLED,            // Cancelled by user or admin
    REFUNDED,             // Full refund processed
    PARTIALLY_REFUNDED,   // Partial refund processed
    EXPIRED,              // Event has passed
    FAILED                // Payment failed
}
```

### TicketStatus

```java
public enum TicketStatus {
    VALID,        // Ready for admission
    USED,         // Checked in / scanned
    CANCELLED,    // Cancelled/refunded
    TRANSFERRED,  // Transferred to another holder
    EXPIRED       // Event has passed
}
```

---

## Backend Migration

### 1. Database Schema Migration

Full table and column renaming for clean domain language:

#### Flyway Migration: `V{next}_rename_orders_to_bookings.sql`

```sql
-- =====================================================
-- MIGRATION: Rename 'orders' to 'bookings'
-- =====================================================

-- 1. Rename main table
ALTER TABLE orders RENAME TO bookings;

-- 2. Rename order_items table
ALTER TABLE order_items RENAME TO booking_items;

-- 3. Rename columns in bookings table
ALTER TABLE bookings RENAME COLUMN order_number TO booking_number;
ALTER TABLE bookings RENAME COLUMN order_status TO booking_status;

-- 4. Rename foreign key columns in booking_items
ALTER TABLE booking_items RENAME COLUMN order_id TO booking_id;

-- 5. Rename foreign key in tickets table (if exists)
ALTER TABLE tickets RENAME COLUMN order_id TO booking_id;

-- 6. Update foreign key constraints
-- Drop old constraints
ALTER TABLE booking_items DROP CONSTRAINT IF EXISTS fk_order_items_order;
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS fk_tickets_order;

-- Add new constraints with correct names
ALTER TABLE booking_items
    ADD CONSTRAINT fk_booking_items_booking
    FOREIGN KEY (booking_id) REFERENCES bookings(id);

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_booking
    FOREIGN KEY (booking_id) REFERENCES bookings(id);

-- 7. Rename indexes
ALTER INDEX IF EXISTS idx_orders_user_id RENAME TO idx_bookings_user_id;
ALTER INDEX IF EXISTS idx_orders_event_id RENAME TO idx_bookings_event_id;
ALTER INDEX IF EXISTS idx_orders_status RENAME TO idx_bookings_status;
ALTER INDEX IF EXISTS idx_order_items_order_id RENAME TO idx_booking_items_booking_id;

-- 8. Update enum type if using PostgreSQL enum
-- First check if the enum exists and rename
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status') THEN
        ALTER TYPE order_status RENAME TO booking_status;
    END IF;
END$$;

-- 9. Update booking_number prefix for new bookings (optional trigger)
-- Existing: ORD-2024-00001 → Keep as-is for historical records
-- New: BK-2024-00002 → New format going forward

-- 10. Add comment for documentation
COMMENT ON TABLE bookings IS 'Ticket bookings (formerly orders)';
COMMENT ON TABLE booking_items IS 'Line items within a booking (formerly order_items)';
```

#### For MySQL/MariaDB:

```sql
-- MySQL syntax differs slightly
RENAME TABLE orders TO bookings;
RENAME TABLE order_items TO booking_items;

ALTER TABLE bookings CHANGE order_number booking_number VARCHAR(50);
ALTER TABLE bookings CHANGE order_status booking_status VARCHAR(50);

ALTER TABLE booking_items CHANGE order_id booking_id BINARY(16);
ALTER TABLE tickets CHANGE order_id booking_id BINARY(16);
```

#### Complete Table Structure After Migration:

```sql
-- bookings table (formerly orders)
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_number VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID REFERENCES users(id),
    event_id UUID REFERENCES events(id) NOT NULL,

    -- Pricing
    subtotal DECIMAL(10, 2) NOT NULL,
    fees DECIMAL(10, 2) DEFAULT 0,
    discount DECIMAL(10, 2) DEFAULT 0,
    total DECIMAL(10, 2) NOT NULL,

    -- Status
    booking_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50),
    payment_reference VARCHAR(255),

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,

    -- Cancellation
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES users(id),

    -- Metadata
    source VARCHAR(50) DEFAULT 'WEB', -- WEB, MOBILE, API, BOX_OFFICE
    ip_address INET,
    user_agent TEXT,

    CONSTRAINT chk_booking_status CHECK (booking_status IN (
        'PENDING', 'CONFIRMED', 'CANCELLED', 'REFUNDED',
        'PARTIALLY_REFUNDED', 'EXPIRED', 'FAILED'
    ))
);

-- booking_items table (formerly order_items)
CREATE TABLE booking_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id) ON DELETE CASCADE NOT NULL,
    ticket_type_id UUID REFERENCES ticket_types(id) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- tickets table (individual admission credentials)
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id) ON DELETE CASCADE NOT NULL,
    booking_item_id UUID REFERENCES booking_items(id) NOT NULL,
    ticket_type_id UUID REFERENCES ticket_types(id) NOT NULL,
    event_id UUID REFERENCES events(id) NOT NULL,

    -- Ticket identification
    ticket_code VARCHAR(50) UNIQUE NOT NULL, -- QR/barcode content
    ticket_number VARCHAR(50), -- Human-readable number

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'VALID',

    -- Check-in
    checked_in_at TIMESTAMP WITH TIME ZONE,
    checked_in_by UUID REFERENCES users(id),
    check_in_gate VARCHAR(100),

    -- Transfer
    transferred_at TIMESTAMP WITH TIME ZONE,
    transferred_to_email VARCHAR(255),
    original_holder_id UUID REFERENCES users(id),

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_ticket_status CHECK (status IN (
        'VALID', 'USED', 'CANCELLED', 'TRANSFERRED', 'EXPIRED'
    ))
);

-- Indexes
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_event_id ON bookings(event_id);
CREATE INDEX idx_bookings_status ON bookings(booking_status);
CREATE INDEX idx_bookings_number ON bookings(booking_number);
CREATE INDEX idx_bookings_created_at ON bookings(created_at);

CREATE INDEX idx_booking_items_booking_id ON booking_items(booking_id);
CREATE INDEX idx_tickets_booking_id ON tickets(booking_id);
CREATE INDEX idx_tickets_code ON tickets(ticket_code);
CREATE INDEX idx_tickets_status ON tickets(status);
```

#### Refunds Table:

```sql
CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id) NOT NULL,

    -- Amount
    amount DECIMAL(10, 2) NOT NULL,

    -- Reason and status
    reason TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',

    -- Processing
    processed_by UUID REFERENCES users(id),
    payment_refund_id VARCHAR(255), -- External payment gateway reference

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_refund_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    ))
);

CREATE INDEX idx_refunds_booking_id ON refunds(booking_id);
```

### 2. Entity Classes

#### Booking.java (formerly Order.java)
```java
@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_number", unique = true, nullable = false)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Pricing
    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0")
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0")
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private String paymentMethod;
    private String paymentReference;

    // Relationships
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "booking")
    private List<Refund> refunds = new ArrayList<>();

    // Timestamps
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Cancellation
    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    // Metadata
    @Column(columnDefinition = "VARCHAR(50) DEFAULT 'WEB'")
    private String source = "WEB";

    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    // Helper methods
    public void addItem(BookingItem item) {
        items.add(item);
        item.setBooking(this);
    }

    public void removeItem(BookingItem item) {
        items.remove(item);
        item.setBooking(null);
    }

    public int getTotalTicketCount() {
        return items.stream()
            .mapToInt(BookingItem::getQuantity)
            .sum();
    }

    public boolean isCancellable() {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
    }

    public boolean isRefundable() {
        return status == BookingStatus.CONFIRMED &&
               paymentStatus == PaymentStatus.PAID;
    }
}
```

#### BookingItem.java (formerly OrderItem.java)
```java
@Entity
@Table(name = "booking_items")
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Derived tickets for this line item
    @OneToMany(mappedBy = "bookingItem")
    private List<Ticket> tickets = new ArrayList<>();

    @PrePersist
    public void calculateTotal() {
        this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }
}
```

#### Ticket.java (updated for new FK names)
```java
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_item_id", nullable = false)
    private BookingItem bookingItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Ticket identification
    @Column(name = "ticket_code", unique = true, nullable = false)
    private String ticketCode;

    @Column(name = "ticket_number")
    private String ticketNumber;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;

    // Check-in
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_in_by")
    private User checkedInBy;

    @Column(name = "check_in_gate")
    private String checkInGate;

    // Transfer
    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Column(name = "transferred_to_email")
    private String transferredToEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_holder_id")
    private User originalHolder;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Methods
    public boolean isUsable() {
        return status == TicketStatus.VALID;
    }

    public void checkIn(User scanner, String gate) {
        if (!isUsable()) {
            throw new TicketNotUsableException("Ticket is not valid for check-in");
        }
        this.status = TicketStatus.USED;
        this.checkedInAt = LocalDateTime.now();
        this.checkedInBy = scanner;
        this.checkInGate = gate;
    }
}
```

#### Refund.java
```java
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status = RefundStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Column(name = "payment_refund_id")
    private String paymentRefundId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
```

#### Enums
```java
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    EXPIRED,
    FAILED
}

public enum TicketStatus {
    VALID,
    USED,
    CANCELLED,
    TRANSFERRED,
    EXPIRED
}

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED
}

public enum RefundStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
```

### 3. Repository Layer

```java
@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingNumber(String bookingNumber);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId")
    Page<Booking> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.event.id = :eventId")
    Page<Booking> findByEventId(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.status = :status")
    long countByStatus(@Param("status") BookingStatus status);

    @Query("SELECT SUM(b.totalAmount) FROM Booking b WHERE b.status = 'CONFIRMED'")
    BigDecimal sumConfirmedRevenue();
}
```

### 4. Service Layer

```java
@Service
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TicketService ticketService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    public Booking createBooking(CreateBookingRequest request) {
        // Validate availability
        // Create booking with PENDING status
        // Process payment
        // On success: update to CONFIRMED, generate tickets
        // Send confirmation notification
    }

    public Booking confirmBooking(UUID bookingId) {
        Booking booking = findById(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        // Generate tickets
        ticketService.generateTicketsForBooking(booking);

        // Send confirmation
        notificationService.sendBookingConfirmation(booking);

        return bookingRepository.save(booking);
    }

    public Booking cancelBooking(UUID bookingId, String reason) {
        Booking booking = findById(bookingId);
        validateCancellable(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason);

        // Cancel all tickets
        ticketService.cancelTicketsForBooking(booking);

        // Notify customer
        notificationService.sendBookingCancellation(booking);

        return bookingRepository.save(booking);
    }

    public Refund processRefund(UUID bookingId, RefundRequest request) {
        // Process refund through payment gateway
        // Update booking status
        // Cancel affected tickets
        // Send refund confirmation
    }
}
```

### 5. GraphQL Schema

```graphql
# schema/booking.graphqls

type Booking {
    id: ID!
    bookingNumber: String!
    customer: User!
    event: Event!
    items: [BookingItem!]!
    tickets: [Ticket!]!
    subtotal: Float!
    fees: Float!
    total: Float!
    status: BookingStatus!
    paymentStatus: PaymentStatus!
    paymentMethod: String
    createdAt: DateTime!
    confirmedAt: DateTime
    cancelledAt: DateTime
    refunds: [Refund!]!
}

type BookingItem {
    id: ID!
    ticketType: TicketType!
    quantity: Int!
    unitPrice: Float!
    total: Float!
}

enum BookingStatus {
    PENDING
    CONFIRMED
    CANCELLED
    REFUNDED
    PARTIALLY_REFUNDED
    EXPIRED
    FAILED
}

input BookingFilterInput {
    status: [BookingStatus!]
    eventId: ID
    customerId: ID
    paymentStatus: PaymentStatus
    dateFrom: DateTime
    dateTo: DateTime
    searchQuery: String
}

type BookingPage {
    content: [Booking!]!
    totalElements: Int!
    totalPages: Int!
    pageNumber: Int!
    pageSize: Int!
}

extend type Query {
    booking(id: ID!): Booking @hasPermission(permission: "booking.read")
    bookingByNumber(bookingNumber: String!): Booking @hasPermission(permission: "booking.read")
    bookingsPage(
        filter: BookingFilterInput
        page: Int = 0
        size: Int = 20
    ): BookingPage! @hasPermission(permission: "booking.read")
    myBookings(page: Int = 0, size: Int = 20): BookingPage!
}

extend type Mutation {
    createBooking(input: CreateBookingInput!): Booking!
    cancelBooking(id: ID!, reason: String): Booking! @hasPermission(permission: "booking.cancel")
    resendBookingConfirmation(id: ID!): Boolean! @hasPermission(permission: "booking.read")
}
```

### 6. GraphQL Resolver

```java
@Controller
public class BookingResolver {

    private final BookingService bookingService;

    @QueryMapping
    public Booking booking(@Argument UUID id) {
        return bookingService.findById(id);
    }

    @QueryMapping
    public Booking bookingByNumber(@Argument String bookingNumber) {
        return bookingService.findByBookingNumber(bookingNumber);
    }

    @QueryMapping
    public BookingPage bookingsPage(
            @Argument BookingFilterInput filter,
            @Argument int page,
            @Argument int size
    ) {
        return bookingService.findAll(filter, PageRequest.of(page, size));
    }

    @MutationMapping
    public Booking cancelBooking(@Argument UUID id, @Argument String reason) {
        return bookingService.cancelBooking(id, reason);
    }

    @SchemaMapping(typeName = "Booking", field = "tickets")
    public List<Ticket> tickets(Booking booking) {
        return ticketService.findByBookingId(booking.getId());
    }
}
```

---

## Frontend Migration

### 1. File Structure Changes

```
Before:
src/app/dashboard/orders/
├── page.tsx
├── [id]/
│   ├── page.tsx
│   └── refund/page.tsx

After:
src/app/dashboard/bookings/
├── page.tsx
├── [id]/
│   ├── page.tsx
│   └── refund/page.tsx
```

### 2. GraphQL Queries

```typescript
// lib/graphql/queries/bookings.ts

import { gql } from '@apollo/client';

export const BOOKINGS_PAGE_QUERY = gql`
  query BookingsPage($filter: BookingFilterInput, $page: Int, $size: Int) {
    bookingsPage(filter: $filter, page: $page, size: $size) {
      content {
        id
        bookingNumber
        customer {
          id
          name
          email
        }
        event {
          id
          name
        }
        total
        status
        paymentStatus
        createdAt
      }
      totalElements
      totalPages
    }
  }
`;

export const BOOKING_DETAIL_QUERY = gql`
  query BookingDetail($id: ID!) {
    booking(id: $id) {
      id
      bookingNumber
      customer {
        id
        name
        email
        phone
      }
      event {
        id
        name
        startDate
        venue {
          name
        }
      }
      items {
        id
        ticketType {
          id
          name
        }
        quantity
        unitPrice
        total
      }
      tickets {
        id
        ticketCode
        status
        checkedInAt
      }
      subtotal
      fees
      total
      status
      paymentStatus
      paymentMethod
      createdAt
      confirmedAt
      refunds {
        id
        amount
        reason
        createdAt
      }
    }
  }
`;

export const CANCEL_BOOKING = gql`
  mutation CancelBooking($id: ID!, $reason: String) {
    cancelBooking(id: $id, reason: $reason) {
      id
      status
      cancelledAt
    }
  }
`;
```

### 3. TypeScript Types

```typescript
// lib/types/booking.ts

export interface Booking {
  id: string;
  bookingNumber: string;
  customer: User;
  event: Event;
  items: BookingItem[];
  tickets: Ticket[];
  subtotal: number;
  fees: number;
  total: number;
  status: BookingStatus;
  paymentStatus: PaymentStatus;
  paymentMethod?: string;
  createdAt: string;
  confirmedAt?: string;
  cancelledAt?: string;
  refunds: Refund[];
}

export interface BookingItem {
  id: string;
  ticketType: TicketType;
  quantity: number;
  unitPrice: number;
  total: number;
}

export enum BookingStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED',
  CANCELLED = 'CANCELLED',
  REFUNDED = 'REFUNDED',
  PARTIALLY_REFUNDED = 'PARTIALLY_REFUNDED',
  EXPIRED = 'EXPIRED',
  FAILED = 'FAILED',
}

export const bookingStatusLabels: Record<BookingStatus, string> = {
  [BookingStatus.PENDING]: 'Pending',
  [BookingStatus.CONFIRMED]: 'Confirmed',
  [BookingStatus.CANCELLED]: 'Cancelled',
  [BookingStatus.REFUNDED]: 'Refunded',
  [BookingStatus.PARTIALLY_REFUNDED]: 'Partially Refunded',
  [BookingStatus.EXPIRED]: 'Expired',
  [BookingStatus.FAILED]: 'Failed',
};

export const bookingStatusColors: Record<BookingStatus, 'gray' | 'blue' | 'green' | 'red' | 'orange' | 'purple'> = {
  [BookingStatus.PENDING]: 'orange',
  [BookingStatus.CONFIRMED]: 'green',
  [BookingStatus.CANCELLED]: 'gray',
  [BookingStatus.REFUNDED]: 'red',
  [BookingStatus.PARTIALLY_REFUNDED]: 'purple',
  [BookingStatus.EXPIRED]: 'gray',
  [BookingStatus.FAILED]: 'red',
};
```

### 4. Navigation Updates

```typescript
// components/layout/Sidebar.tsx

const navigation: NavItem[] = [
  // ...
  {
    label: 'Bookings',  // Changed from 'Orders'
    href: '/dashboard/bookings',
    icon: Ticket,  // Changed from ShoppingCart
    permissions: ['booking.read' as PermissionString]
  },
  // ...
];
```

### 5. Page Component Example

```tsx
// app/dashboard/bookings/page.tsx

'use client';

import { useQuery } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Badge, TextField, Select,
  Button, DropdownMenu
} from '@radix-ui/themes';
import { Search, MoreHorizontal, Eye, RefreshCcw, Download, Ticket } from 'lucide-react';
import Link from 'next/link';
import { DataTable } from '@/components/ui/DataTable';
import { BOOKINGS_PAGE_QUERY } from '@/lib/graphql/queries/bookings';
import { Booking, BookingStatus, bookingStatusColors } from '@/lib/types/booking';
import { formatDateTime, formatCurrency } from '@/lib/utils/format';

export default function BookingsPage() {
  const [filter, setFilter] = useState({
    status: null as BookingStatus | null,
    searchQuery: '',
  });

  const { data, loading } = useQuery(BOOKINGS_PAGE_QUERY, {
    variables: {
      filter: {
        status: filter.status ? [filter.status] : null,
        searchQuery: filter.searchQuery || null,
      },
    },
  });

  const bookings = data?.bookingsPage?.content ?? [];

  const columns = [
    {
      key: 'bookingNumber',
      header: 'Booking #',
      render: (booking: Booking) => (
        <Flex align="center" gap="2">
          <Ticket size={14} />
          <Text weight="medium" style={{ fontFamily: 'monospace' }}>
            {booking.bookingNumber}
          </Text>
        </Flex>
      ),
    },
    {
      key: 'customer',
      header: 'Customer',
      render: (booking: Booking) => (
        <Box>
          <Text size="2">{booking.customer.name || 'Guest'}</Text>
          <Text size="1" color="gray">{booking.customer.email}</Text>
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
      key: 'total',
      header: 'Total',
      render: (booking: Booking) => (
        <Text size="2" weight="medium">{formatCurrency(booking.total)}</Text>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (booking: Booking) => (
        <Badge color={bookingStatusColors[booking.status]} variant="soft">
          {booking.status}
        </Badge>
      ),
    },
    {
      key: 'createdAt',
      header: 'Date',
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
            {booking.status === 'CONFIRMED' && (
              <DropdownMenu.Item asChild>
                <Link href={`/dashboard/bookings/${booking.id}/refund`}>
                  <RefreshCcw size={14} /> Process Refund
                </Link>
              </DropdownMenu.Item>
            )}
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
          <Text color="gray" size="2">Manage ticket bookings</Text>
        </Box>
        <Button variant="soft">
          <Download size={16} /> Export
        </Button>
      </Flex>

      {/* Filters */}
      <Flex gap="3" mb="4">
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
          onValueChange={(v) => setFilter(f => ({ ...f, status: v === 'all' ? null : v as BookingStatus }))}
        >
          <Select.Trigger placeholder="Status" />
          <Select.Content>
            <Select.Item value="all">All Status</Select.Item>
            <Select.Item value="PENDING">Pending</Select.Item>
            <Select.Item value="CONFIRMED">Confirmed</Select.Item>
            <Select.Item value="CANCELLED">Cancelled</Select.Item>
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

---

## UI Copy Changes

### Admin Dashboard Text

| Location | Old Text | New Text |
|----------|----------|----------|
| Sidebar | Orders | Bookings |
| Page Title | Orders | Bookings |
| Table Header | Order # | Booking # |
| Stat Card | Total Orders | Total Bookings |
| Button | View Order | View Booking |
| Action | Cancel Order | Cancel Booking |
| Confirmation | Order Cancelled | Booking Cancelled |
| Email Subject | Order Confirmation | Booking Confirmation |

### Customer-Facing Text

| Context | Text |
|---------|------|
| Confirmation Email | "Your booking is confirmed!" |
| Ticket Email | "Your tickets for {event}" |
| Cancellation | "Your booking has been cancelled" |
| Refund | "Your refund has been processed" |
| My Account | "My Bookings" |
| Support | "Need help with your booking?" |

---

## Migration Checklist

### Backend
- [ ] Create new enum `BookingStatus`
- [ ] Rename `Order` entity to `Booking`
- [ ] Rename `OrderItem` to `BookingItem`
- [ ] Update repository interfaces
- [ ] Update service classes
- [ ] Update GraphQL schema
- [ ] Update GraphQL resolvers
- [ ] Update REST controllers (if any)
- [ ] Update tests
- [ ] Update API documentation

### Frontend
- [ ] Rename `orders/` directory to `bookings/`
- [ ] Update GraphQL queries and mutations
- [ ] Update TypeScript types
- [ ] Update navigation/sidebar
- [ ] Update all page components
- [ ] Update all UI text/labels
- [ ] Update routes in links
- [ ] Update tests

### Documentation
- [ ] Update API documentation
- [ ] Update README files
- [ ] Update todos/plan documents
- [ ] Update Postman/Insomnia collections

---

## Permissions Update

```typescript
// Old permissions
'order.read'
'order.create'
'order.cancel'
'order.refund'

// New permissions
'booking.read'
'booking.create'
'booking.cancel'
'booking.refund'
```

Update `Permission` enum in backend and frontend to reflect new naming.
