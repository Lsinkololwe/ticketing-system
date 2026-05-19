# API Design Specification - Event Ticketing Platform

**Version:** 2.0
**Date:** March 2026
**Author:** Architecture Team
**Status:** Design Review

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [API Architecture Overview](#2-api-architecture-overview)
3. [Catalog Service API](#3-catalog-service-api)
4. [Booking Service API](#4-booking-service-api)
5. [Identity Service API](#5-identity-service-api)
6. [Webhook Endpoints](#6-webhook-endpoints)
7. [Subscriptions (Real-time)](#7-subscriptions-real-time)
8. [Error Handling](#8-error-handling)
9. [Security](#9-security)
10. [Rate Limiting](#10-rate-limiting)

---

## 1. Executive Summary

### 1.1 API Style

| Layer | Technology | Use Case |
|-------|------------|----------|
| **Client API** | GraphQL | Mobile apps, Web apps, Admin dashboard |
| **Webhooks** | REST | Payment provider callbacks (pawaPay) |
| **Internal** | REST | Service-to-service communication |
| **Events** | Azure Service Bus | Async cross-service events |

### 1.2 Services & Ports

| Service | Port | GraphQL Endpoint | REST Endpoints |
|---------|------|------------------|----------------|
| Catalog Service | 8081 | `/graphql` | `/api/internal/*`, `/actuator/*` |
| Booking Service | 8082 | `/graphql` | `/api/webhooks/*`, `/api/internal/*` |
| Identity Service | 8083 | `/graphql` | `/api/internal/*` |

### 1.3 Key Improvements in This Version

- **Two-Stage Commission Model**: Commission states (PENDING → EARNED)
- **pawaPay Integration**: Unified payment provider APIs
- **Event Escrow Accounts**: Per-event escrow tracking
- **Double-Entry Bookkeeping**: Journal entry APIs
- **Settlement Tracking**: Settlement status and reconciliation
- **Real-time Subscriptions**: GraphQL subscriptions for live updates

---

## 2. API Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT APPLICATIONS                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Mobile App  │  │  Web App    │  │   Admin     │  │  Scanner    │        │
│  │  (Expo)     │  │  (Next.js)  │  │  Dashboard  │  │   App       │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
│         │                │                │                │                │
│         └────────────────┴────────┬───────┴────────────────┘                │
│                                   │                                          │
│                          ┌────────▼────────┐                                │
│                          │   API GATEWAY   │                                │
│                          │  (Rate Limit,   │                                │
│                          │   Auth, Route)  │                                │
│                          └────────┬────────┘                                │
└───────────────────────────────────┼─────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
           ┌────────▼────┐  ┌───────▼─────┐  ┌─────▼───────┐
           │   CATALOG   │  │   BOOKING   │  │  IDENTITY   │
           │   SERVICE   │  │   SERVICE   │  │   SERVICE   │
           │   :8081     │  │    :8082    │  │    :8083    │
           │             │  │             │  │             │
           │ • Events    │  │ • Tickets   │  │ • Users     │
           │ • Locations │  │ • Payments  │  │ • Bank Accts│
           │ • Categories│  │ • Escrow    │  │ • Payouts   │
           │ • Approval  │  │ • Refunds   │  │ • Notifs    │
           └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
                  │                │                │
                  └────────────────┼────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │      AZURE SERVICE BUS      │
                    │  (Topics & Subscriptions)   │
                    └─────────────────────────────┘
```

### 2.1 Standard Response Envelope

All GraphQL mutations use a consistent response envelope:

```graphql
type MutationResponse {
    success: Boolean!          # Operation succeeded
    message: String            # Human-readable message
    data: <EntityType>         # The entity (if successful)
    errors: [ApiError!]!       # Structured errors
    metadata: JSON             # Additional context
}

type ApiError {
    code: String!              # Machine-readable error code
    message: String!           # Human-readable message
    field: String              # Field that caused error (validation)
    details: JSON              # Additional error context
}
```

### 2.2 Standard Pagination

```graphql
# Cursor-based (Mobile - Infinite Scroll)
input CursorPaginationInput {
    first: Int = 20           # Forward pagination
    after: String             # Cursor after
    last: Int                 # Backward pagination
    before: String            # Cursor before
}

type PageInfo {
    hasNextPage: Boolean!
    hasPreviousPage: Boolean!
    startCursor: String
    endCursor: String
    totalCount: Int           # Total items (optional, expensive)
}

# Offset-based (Admin - Tables)
input PageableInput {
    page: Int = 0
    size: Int = 20
    sortBy: String = "createdAt"
    sortDirection: SortDirection = DESC
}

type PagedResult {
    content: [<EntityType>!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}
```

---

## 3. Catalog Service API

### 3.1 GraphQL Schema - Types

```graphql
# ============================================================================
# CATALOG SERVICE - TYPES
# ============================================================================

# Custom Scalars
scalar DateTime          # ISO 8601 format: "2026-03-15T18:00:00Z"
scalar BigDecimal        # Precise decimal: "299.99"
scalar JSON              # Arbitrary JSON object
scalar UUID              # UUID v4 format

# ============================================================================
# ENUMS
# ============================================================================

enum EventStatus {
    DRAFT                 # Initial state, editable
    PENDING_APPROVAL      # Submitted for admin review
    APPROVED              # Admin approved, ready to publish
    REJECTED              # Admin rejected, needs revision
    PUBLISHED             # Live, tickets on sale
    RESCHEDULED          # Date changed, still active
    CANCELLED             # Organizer cancelled
    COMPLETED             # Event date passed
}

enum ApprovalStatus {
    DRAFT
    SUBMITTED
    UNDER_REVIEW
    PENDING_DOCUMENTS
    APPROVED
    REJECTED
    REQUIRES_CHANGES
}

enum TicketCategoryType {
    GENERAL               # Standard admission
    VIP                   # Premium seating
    VVIP                  # All access
    EARLY_BIRD            # Discounted early purchase
    STUDENT               # Student discount
    GROUP                 # Group pricing
    FREE                  # Free admission
}

enum EventType {
    CONCERT
    FESTIVAL
    CONFERENCE
    WORKSHOP
    SPORTS_EVENT
    COMEDY_SHOW
    THEATER
    EXHIBITION
    NETWORKING
    CHARITY_EVENT
    CORPORATE_EVENT
    PRIVATE_PARTY
}

# ============================================================================
# EVENT TYPES
# ============================================================================

"""
Event - Core entity representing a scheduled event
"""
type Event {
    # Identifiers
    id: ID!
    slug: String!                        # URL-friendly identifier

    # Basic Info
    title: String!
    description: String!
    shortDescription: String             # Max 200 chars for cards
    eventType: EventType!

    # Timing
    eventDateTime: DateTime!             # Start time
    endDateTime: DateTime!               # End time
    doorsOpenAt: DateTime                # When doors open
    timezone: String!                    # IANA timezone

    # Location
    location: Location
    locationId: String
    isVirtual: Boolean!
    virtualEventUrl: String
    virtualEventPlatform: String

    # Organizer (denormalized for performance)
    organizerId: String!
    organizerName: String!
    organizerEmail: String
    organizerPhone: String
    organizerCompanyName: String
    organizerLogo: String

    # Category
    category: EventCategory
    categoryId: String

    # Status & Workflow
    status: EventStatus!
    published: Boolean!
    publishedAt: DateTime
    submittedForApprovalAt: DateTime
    approvedAt: DateTime
    approvedBy: String
    rejectedAt: DateTime
    rejectedBy: String
    rejectionReason: String

    # Capacity & Inventory
    totalCapacity: Int!
    availableTickets: Int!
    soldTickets: Int!
    reservedTickets: Int!                # Currently in checkout
    ticketCategories: [TicketCategory!]!

    # Pricing Summary
    minPrice: BigDecimal                 # Cheapest ticket
    maxPrice: BigDecimal                 # Most expensive ticket
    currency: String!

    # Media
    bannerImageUrl: String
    thumbnailUrl: String
    galleryImages: [String!]
    videoUrl: String

    # Discovery
    tags: [String!]
    featured: Boolean!
    featuredUntil: DateTime
    soldOut: Boolean!

    # Policies
    refundPolicy: RefundPolicy
    ageRestriction: String               # e.g., "18+", "All ages"
    dresscode: String

    # SEO
    metaTitle: String
    metaDescription: String

    # Audit
    createdAt: DateTime!
    updatedAt: DateTime!
    createdBy: String
    updatedBy: String
    version: Int!                        # Optimistic locking

    # Escrow (populated from Booking Service)
    escrowAccountId: String
    escrowStatus: String
}

"""
TicketCategory - Pricing tier for an event
"""
type TicketCategory {
    id: ID!
    code: String!                        # e.g., "VIP", "GENERAL"
    name: String!                        # e.g., "VIP Access"
    description: String
    categoryType: TicketCategoryType!

    # Pricing
    price: BigDecimal!
    originalPrice: BigDecimal            # For showing discounts
    currency: String!

    # Inventory
    totalQuantity: Int!
    availableQuantity: Int!
    soldQuantity: Int!
    reservedQuantity: Int!               # In active checkouts

    # Limits
    minPerOrder: Int!                    # Min tickets per purchase
    maxPerOrder: Int!                    # Max tickets per purchase

    # Availability
    salesStartAt: DateTime               # When sales begin
    salesEndAt: DateTime                 # When sales end
    isActive: Boolean!
    isSoldOut: Boolean!

    # Benefits
    benefits: [String!]                  # ["Front row seating", "Free drinks"]
    isPremium: Boolean!

    # Display
    displayOrder: Int!                   # Sort order on page
    colorCode: String                    # UI color coding
}

"""
RefundPolicy - Event refund rules
"""
type RefundPolicy {
    allowRefunds: Boolean!
    refundDeadlineHours: Int             # Hours before event
    refundPercentage: Int                # 100 = full refund
    refundFeePolicy: RefundFeePolicy!
    customPolicy: String                 # Free-text policy
}

enum RefundFeePolicy {
    CUSTOMER_PAYS                        # Customer absorbs processing fee
    PLATFORM_ABSORBS                     # Platform absorbs fee
    ORGANIZER_PAYS                       # Deducted from organizer payout
}

"""
Location - Venue where event takes place
"""
type Location {
    id: ID!
    name: String!
    address: String!
    city: String!
    province: String
    country: String!
    postalCode: String

    # Coordinates
    latitude: Float
    longitude: Float

    # Details
    capacity: Int
    facilities: [String!]                # ["Parking", "WiFi", "Accessible"]
    accessibilityInfo: String
    images: [String!]
    website: String
    phoneNumber: String

    # Audit
    isActive: Boolean!
    createdAt: DateTime!
    updatedAt: DateTime!
}

"""
EventCategory - Event classification
"""
type EventCategory {
    id: ID!
    name: String!
    code: String!
    description: String
    iconUrl: String
    colorCode: String
    displayOrder: Int!
    isActive: Boolean!
    eventCount: Int                      # Number of active events
    createdAt: DateTime!
    updatedAt: DateTime!
}

# ============================================================================
# APPROVAL WORKFLOW TYPES
# ============================================================================

"""
ApprovalTimeline - Track event approval progress
"""
type ApprovalTimeline {
    eventId: String!
    eventTitle: String!
    organizerId: String!
    organizerName: String!
    currentStatus: ApprovalStatus!

    # Timeline
    submittedAt: DateTime
    lastActivityAt: DateTime
    expectedApprovalAt: DateTime
    actualApprovalAt: DateTime

    # Events
    timelineEvents: [TimelineEvent!]!

    # Metrics
    totalProcessingTimeHours: Int
    isOverdue: Boolean!
    overdueByHours: Int
    priority: ApprovalPriority!
}

type TimelineEvent {
    id: ID!
    timestamp: DateTime!
    status: ApprovalStatus!
    action: String!                      # "SUBMITTED", "APPROVED", etc.
    description: String!
    actorId: String
    actorName: String
    actorRole: String
    comments: String
    attachments: [String!]
}

enum ApprovalPriority {
    LOW
    NORMAL
    HIGH
    URGENT
}
```

### 3.2 GraphQL Schema - Queries

```graphql
# ============================================================================
# CATALOG SERVICE - QUERIES
# ============================================================================

type Query {
    # ========================================================================
    # EVENT QUERIES
    # ========================================================================

    """
    Get single event by ID
    """
    event(id: ID!): Event

    """
    Get event by URL slug
    """
    eventBySlug(slug: String!): Event

    """
    Browse published events (mobile infinite scroll)
    """
    publishedEvents(
        filter: EventBrowseFilter
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Search events by keyword
    """
    searchEvents(
        query: String!
        filter: EventBrowseFilter
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Get upcoming events (next 30 days)
    """
    upcomingEvents(
        filter: EventBrowseFilter
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Get featured events for homepage
    """
    featuredEvents(
        limit: Int = 10
    ): [Event!]!

    """
    Get events by category
    """
    eventsByCategory(
        categoryId: String!
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Get events by city
    """
    eventsByCity(
        city: String!
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Get events by date range
    """
    eventsByDateRange(
        startDate: DateTime!
        endDate: DateTime!
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Get events by organizer (organizer dashboard)
    """
    eventsByOrganizer(
        organizerId: String!
        status: EventStatus
        pagination: CursorPaginationInput
    ): EventConnection!

    """
    Admin: Get all events with filters (admin tables)
    """
    eventsAdmin(
        filter: EventAdminFilter
        pageable: PageableInput
    ): PagedEventResult!

    """
    Admin: Get events pending approval
    """
    pendingApprovalEvents(
        pageable: PageableInput
    ): PagedEventResult!

    """
    Admin: Get overdue approval events
    """
    overdueApprovalEvents(
        pageable: PageableInput
    ): PagedEventResult!

    """
    Get event ticket statistics
    """
    eventStatistics(eventId: ID!): EventStatistics

    # ========================================================================
    # CATEGORY QUERIES
    # ========================================================================

    """
    Get all active categories
    """
    categories: [EventCategory!]!

    """
    Get category by ID
    """
    category(id: ID!): EventCategory

    """
    Admin: Get all categories with pagination
    """
    categoriesAdmin(
        pageable: PageableInput
    ): PagedCategoryResult!

    # ========================================================================
    # LOCATION QUERIES
    # ========================================================================

    """
    Get location by ID
    """
    location(id: ID!): Location

    """
    Search locations
    """
    searchLocations(
        query: String!
        city: String
        pagination: CursorPaginationInput
    ): LocationConnection!

    """
    Get nearby locations
    """
    nearbyLocations(
        latitude: Float!
        longitude: Float!
        radiusKm: Float = 10.0
        limit: Int = 10
    ): [Location!]!

    """
    Get locations by city
    """
    locationsByCity(
        city: String!
        pagination: CursorPaginationInput
    ): LocationConnection!

    # ========================================================================
    # CITY & PROVINCE QUERIES
    # ========================================================================

    """
    Get all cities
    """
    cities(
        provinceId: String
        pagination: CursorPaginationInput
    ): CityConnection!

    """
    Get all provinces
    """
    provinces: [Province!]!

    """
    Search cities
    """
    searchCities(
        query: String!
        limit: Int = 10
    ): [City!]!

    # ========================================================================
    # APPROVAL TIMELINE QUERIES
    # ========================================================================

    """
    Get approval timeline for event
    """
    approvalTimeline(eventId: String!): ApprovalTimeline

    """
    Admin: Get all pending approvals
    """
    pendingApprovals(
        pageable: PageableInput
    ): PagedApprovalTimelineResult!

    # ========================================================================
    # STATISTICS QUERIES
    # ========================================================================

    """
    Get platform-wide event statistics
    """
    eventStats: EventPlatformStats!

    """
    Get organizer's event statistics
    """
    organizerStats(organizerId: String!): OrganizerStats!
}

# ============================================================================
# FILTER INPUTS
# ============================================================================

"""
Filter for browsing events (customer-facing)
"""
input EventBrowseFilter {
    categoryId: String
    city: String
    country: String
    eventType: EventType
    minPrice: BigDecimal
    maxPrice: BigDecimal
    dateFrom: DateTime
    dateTo: DateTime
    isVirtual: Boolean
    isFree: Boolean
    hasAvailability: Boolean             # Exclude sold out
}

"""
Filter for admin event management
"""
input EventAdminFilter {
    status: EventStatus
    organizerId: String
    categoryId: String
    city: String
    published: Boolean
    featured: Boolean
    dateFrom: DateTime
    dateTo: DateTime
    createdFrom: DateTime
    createdTo: DateTime
    searchQuery: String                  # Title, organizer name
}

# ============================================================================
# CONNECTION TYPES (Relay Specification)
# ============================================================================

type EventConnection {
    edges: [EventEdge!]!
    pageInfo: PageInfo!
    totalCount: Int
}

type EventEdge {
    node: Event!
    cursor: String!
}

type LocationConnection {
    edges: [LocationEdge!]!
    pageInfo: PageInfo!
}

type LocationEdge {
    node: Location!
    cursor: String!
}

type CityConnection {
    edges: [CityEdge!]!
    pageInfo: PageInfo!
}

type CityEdge {
    node: City!
    cursor: String!
}

# ============================================================================
# PAGED RESULTS (Admin Tables)
# ============================================================================

type PagedEventResult {
    content: [Event!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

type PagedCategoryResult {
    content: [EventCategory!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

type PagedApprovalTimelineResult {
    content: [ApprovalTimeline!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

# ============================================================================
# STATISTICS TYPES
# ============================================================================

type EventStatistics {
    eventId: ID!
    eventTitle: String!
    eventDate: DateTime!

    # Ticket Stats
    totalTicketsAvailable: Int!
    totalTicketsSold: Int!
    totalTicketsRefunded: Int!
    ticketsSoldPercentage: Float!

    # Revenue Stats
    grossRevenue: BigDecimal!
    netRevenue: BigDecimal!              # After commission
    pendingCommission: BigDecimal!
    earnedCommission: BigDecimal!
    refundedAmount: BigDecimal!

    # Category Breakdown
    categoryStats: [TicketCategoryStats!]!

    # Timeline
    salesByDay: [DailySales!]!
    peakSalesHour: Int                   # 0-23

    # Escrow (from Booking Service)
    escrowBalance: BigDecimal
    escrowStatus: String
}

type TicketCategoryStats {
    categoryCode: String!
    categoryName: String!
    price: BigDecimal!
    totalQuantity: Int!
    soldQuantity: Int!
    soldPercentage: Float!
    revenue: BigDecimal!
}

type DailySales {
    date: String!                        # "2026-03-01"
    ticketsSold: Int!
    revenue: BigDecimal!
}

type EventPlatformStats {
    totalEvents: Int!
    publishedEvents: Int!
    draftEvents: Int!
    pendingApprovalEvents: Int!
    completedEvents: Int!
    cancelledEvents: Int!

    totalTicketsSold: Int!
    totalGrossRevenue: BigDecimal!
    totalCommissionEarned: BigDecimal!
    totalCommissionPending: BigDecimal!

    eventsByCategory: [CategoryCount!]!
    eventsByCity: [CityCount!]!
    topOrganizers: [OrganizerRanking!]!
}

type OrganizerStats {
    organizerId: String!
    totalEvents: Int!
    publishedEvents: Int!
    completedEvents: Int!
    totalTicketsSold: Int!
    totalGrossRevenue: BigDecimal!
    totalPayoutsReceived: BigDecimal!
    pendingPayoutAmount: BigDecimal!
    averageTicketPrice: BigDecimal!
    averageAttendance: Float!
}

type CategoryCount {
    categoryId: String!
    categoryName: String!
    count: Int!
}

type CityCount {
    city: String!
    count: Int!
}

type OrganizerRanking {
    organizerId: String!
    organizerName: String!
    eventCount: Int!
    totalRevenue: BigDecimal!
}
```

### 3.3 GraphQL Schema - Mutations

```graphql
# ============================================================================
# CATALOG SERVICE - MUTATIONS
# ============================================================================

type Mutation {
    # ========================================================================
    # EVENT MUTATIONS (Organizer)
    # ========================================================================

    """
    Create a new draft event
    Required: ORGANIZER role
    """
    createEvent(
        input: CreateEventInput!
    ): CreateEventResponse!

    """
    Update event details
    Required: ORGANIZER role, must own event
    Allowed: Only DRAFT or REJECTED status
    """
    updateEvent(
        id: ID!
        input: UpdateEventInput!
    ): UpdateEventResponse!

    """
    Delete draft event
    Required: ORGANIZER role, must own event
    Allowed: Only DRAFT status
    """
    deleteEvent(
        id: ID!
    ): DeleteEventResponse!

    """
    Submit event for admin approval
    Required: ORGANIZER role, must own event
    Transitions: DRAFT → PENDING_APPROVAL
    Validation: All required fields must be complete
    """
    submitEventForApproval(
        eventId: ID!
    ): SubmitForApprovalResponse!

    """
    Publish approved event (make tickets available)
    Required: ORGANIZER role, must own event
    Transitions: APPROVED → PUBLISHED
    Side Effects:
      - Creates escrow account (Booking Service)
      - Enables ticket sales
      - Notifies followers
    """
    publishEvent(
        eventId: ID!
    ): PublishEventResponse!

    """
    Unpublish event (temporarily hide)
    Required: ORGANIZER role, must own event
    Allowed: Only PUBLISHED status, no tickets sold
    """
    unpublishEvent(
        eventId: ID!
    ): UnpublishEventResponse!

    """
    Reschedule event to new date
    Required: ORGANIZER role, must own event
    Allowed: PUBLISHED status
    Side Effects:
      - Updates escrow lock date
      - Notifies all ticket holders
      - Opens refund window
    """
    rescheduleEvent(
        eventId: ID!
        newEventDateTime: DateTime!
        newEndDateTime: DateTime!
        reason: String!
    ): RescheduleEventResponse!

    """
    Cancel event
    Required: ORGANIZER role, must own event
    Allowed: PUBLISHED status
    Side Effects:
      - Initiates automatic refunds for all tickets
      - Cancels pending commission (NOT earned)
      - Notifies all ticket holders
    """
    cancelEvent(
        eventId: ID!
        reason: String!
    ): CancelEventResponse!

    """
    Update ticket category pricing/quantity
    Required: ORGANIZER role, must own event
    Allowed: DRAFT, APPROVED, or PUBLISHED
    Validation: Cannot reduce below sold quantity
    """
    updateTicketCategory(
        eventId: ID!
        categoryCode: String!
        input: UpdateTicketCategoryInput!
    ): UpdateTicketCategoryResponse!

    """
    Add new ticket category
    Required: ORGANIZER role, must own event
    """
    addTicketCategory(
        eventId: ID!
        input: CreateTicketCategoryInput!
    ): AddTicketCategoryResponse!

    """
    Remove ticket category
    Required: ORGANIZER role, must own event
    Validation: No tickets sold in this category
    """
    removeTicketCategory(
        eventId: ID!
        categoryCode: String!
    ): RemoveTicketCategoryResponse!

    """
    Duplicate event (create copy as draft)
    Required: ORGANIZER role, must own event
    """
    duplicateEvent(
        eventId: ID!
        newTitle: String!
        newEventDateTime: DateTime!
    ): DuplicateEventResponse!

    # ========================================================================
    # EVENT MUTATIONS (Admin)
    # ========================================================================

    """
    Approve event for publishing
    Required: ADMIN role
    Transitions: PENDING_APPROVAL → APPROVED
    Side Effects:
      - Notifies organizer
      - Records approval in timeline
    """
    approveEvent(
        eventId: ID!
        comments: String
    ): ApproveEventResponse!

    """
    Reject event submission
    Required: ADMIN role
    Transitions: PENDING_APPROVAL → REJECTED
    Side Effects:
      - Notifies organizer with reason
      - Records rejection in timeline
    """
    rejectEvent(
        eventId: ID!
        reason: String!
        comments: String
    ): RejectEventResponse!

    """
    Request changes to event
    Required: ADMIN role
    Transitions: PENDING_APPROVAL → REQUIRES_CHANGES
    """
    requestEventChanges(
        eventId: ID!
        requiredChanges: [String!]!
        comments: String
    ): RequestChangesResponse!

    """
    Feature event on homepage
    Required: ADMIN role
    """
    featureEvent(
        eventId: ID!
        until: DateTime!
    ): FeatureEventResponse!

    """
    Remove event from featured
    Required: ADMIN role
    """
    unfeatureEvent(
        eventId: ID!
    ): UnfeatureEventResponse!

    # ========================================================================
    # CATEGORY MUTATIONS (Admin)
    # ========================================================================

    """
    Create event category
    Required: ADMIN role
    """
    createCategory(
        input: CreateCategoryInput!
    ): CreateCategoryResponse!

    """
    Update category
    Required: ADMIN role
    """
    updateCategory(
        id: ID!
        input: UpdateCategoryInput!
    ): UpdateCategoryResponse!

    """
    Delete category
    Required: ADMIN role
    Validation: No events using this category
    """
    deleteCategory(
        id: ID!
    ): DeleteCategoryResponse!

    # ========================================================================
    # LOCATION MUTATIONS (Admin)
    # ========================================================================

    """
    Create location
    Required: ADMIN role
    """
    createLocation(
        input: CreateLocationInput!
    ): CreateLocationResponse!

    """
    Update location
    Required: ADMIN role
    """
    updateLocation(
        id: ID!
        input: UpdateLocationInput!
    ): UpdateLocationResponse!

    """
    Delete location
    Required: ADMIN role
    Validation: No events using this location
    """
    deleteLocation(
        id: ID!
    ): DeleteLocationResponse!

    # ========================================================================
    # CITY/PROVINCE MUTATIONS (Admin)
    # ========================================================================

    createCity(input: CreateCityInput!): CreateCityResponse!
    updateCity(id: ID!, input: UpdateCityInput!): UpdateCityResponse!
    deleteCity(id: ID!): DeleteCityResponse!

    createProvince(input: CreateProvinceInput!): CreateProvinceResponse!
    updateProvince(id: ID!, input: UpdateProvinceInput!): UpdateProvinceResponse!
    deleteProvince(id: ID!): DeleteProvinceResponse!
}

# ============================================================================
# MUTATION INPUT TYPES
# ============================================================================

input CreateEventInput {
    # Basic Info
    title: String!                       # Min: 5, Max: 200
    description: String!                 # Min: 50, Max: 5000
    shortDescription: String             # Max: 200
    eventType: EventType!

    # Timing
    eventDateTime: DateTime!             # Must be future
    endDateTime: DateTime!               # Must be after start
    doorsOpenAt: DateTime
    timezone: String = "Africa/Lusaka"

    # Location
    locationId: String                   # Existing location
    newLocation: CreateLocationInput     # Or create new
    isVirtual: Boolean = false
    virtualEventUrl: String
    virtualEventPlatform: String

    # Category
    categoryId: String!

    # Capacity
    totalCapacity: Int!                  # Min: 1
    ticketCategories: [CreateTicketCategoryInput!]!  # Min: 1

    # Media
    bannerImageUrl: String
    thumbnailUrl: String
    galleryImages: [String!]

    # Discovery
    tags: [String!]                      # Max: 10

    # Policies
    refundPolicy: RefundPolicyInput
    ageRestriction: String
    dresscode: String
}

input UpdateEventInput {
    title: String
    description: String
    shortDescription: String
    eventType: EventType
    eventDateTime: DateTime
    endDateTime: DateTime
    doorsOpenAt: DateTime
    timezone: String
    locationId: String
    isVirtual: Boolean
    virtualEventUrl: String
    virtualEventPlatform: String
    categoryId: String
    totalCapacity: Int
    bannerImageUrl: String
    thumbnailUrl: String
    galleryImages: [String!]
    tags: [String!]
    refundPolicy: RefundPolicyInput
    ageRestriction: String
    dresscode: String
}

input CreateTicketCategoryInput {
    code: String!                        # Unique within event, e.g., "VIP"
    name: String!
    description: String
    categoryType: TicketCategoryType!
    price: BigDecimal!                   # Min: 0 (free)
    currency: String = "ZMW"
    totalQuantity: Int!                  # Min: 1
    minPerOrder: Int = 1
    maxPerOrder: Int = 10
    salesStartAt: DateTime
    salesEndAt: DateTime
    benefits: [String!]
    isPremium: Boolean = false
    displayOrder: Int = 0
}

input UpdateTicketCategoryInput {
    name: String
    description: String
    price: BigDecimal
    totalQuantity: Int                   # Cannot reduce below sold
    minPerOrder: Int
    maxPerOrder: Int
    salesStartAt: DateTime
    salesEndAt: DateTime
    benefits: [String!]
    isPremium: Boolean
    isActive: Boolean
    displayOrder: Int
}

input RefundPolicyInput {
    allowRefunds: Boolean!
    refundDeadlineHours: Int             # Hours before event
    refundPercentage: Int = 100          # 0-100
    refundFeePolicy: RefundFeePolicy = CUSTOMER_PAYS
    customPolicy: String
}

input CreateLocationInput {
    name: String!
    address: String!
    city: String!
    province: String
    country: String = "Zambia"
    postalCode: String
    latitude: Float
    longitude: Float
    capacity: Int
    facilities: [String!]
    accessibilityInfo: String
    website: String
    phoneNumber: String
}

input UpdateLocationInput {
    name: String
    address: String
    city: String
    province: String
    country: String
    postalCode: String
    latitude: Float
    longitude: Float
    capacity: Int
    facilities: [String!]
    accessibilityInfo: String
    images: [String!]
    website: String
    phoneNumber: String
    isActive: Boolean
}

input CreateCategoryInput {
    name: String!
    code: String!                        # Unique, uppercase
    description: String
    iconUrl: String
    colorCode: String                    # Hex color
    displayOrder: Int = 0
}

input UpdateCategoryInput {
    name: String
    description: String
    iconUrl: String
    colorCode: String
    displayOrder: Int
    isActive: Boolean
}

input CreateCityInput {
    name: String!
    code: String!
    provinceId: String!
    country: String = "Zambia"
}

input UpdateCityInput {
    name: String
    code: String
    provinceId: String
    isActive: Boolean
}

input CreateProvinceInput {
    name: String!
    code: String!
    country: String = "Zambia"
}

input UpdateProvinceInput {
    name: String
    code: String
    isActive: Boolean
}

# ============================================================================
# MUTATION RESPONSE TYPES
# ============================================================================

type CreateEventResponse {
    success: Boolean!
    message: String
    event: Event
    errors: [ApiError!]!
}

type UpdateEventResponse {
    success: Boolean!
    message: String
    event: Event
    errors: [ApiError!]!
}

type DeleteEventResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type SubmitForApprovalResponse {
    success: Boolean!
    message: String
    event: Event
    timeline: ApprovalTimeline
    errors: [ApiError!]!
}

type PublishEventResponse {
    success: Boolean!
    message: String
    event: Event
    escrowAccountId: String              # Created escrow account
    errors: [ApiError!]!
}

type UnpublishEventResponse {
    success: Boolean!
    message: String
    event: Event
    errors: [ApiError!]!
}

type RescheduleEventResponse {
    success: Boolean!
    message: String
    event: Event
    notifiedTicketHolders: Int           # Number notified
    refundWindowEnds: DateTime           # When refund window closes
    errors: [ApiError!]!
}

type CancelEventResponse {
    success: Boolean!
    message: String
    event: Event
    refundsInitiated: Int                # Number of refunds
    totalRefundAmount: BigDecimal
    errors: [ApiError!]!
}

type ApproveEventResponse {
    success: Boolean!
    message: String
    event: Event
    timeline: ApprovalTimeline
    errors: [ApiError!]!
}

type RejectEventResponse {
    success: Boolean!
    message: String
    event: Event
    timeline: ApprovalTimeline
    errors: [ApiError!]!
}

type RequestChangesResponse {
    success: Boolean!
    message: String
    event: Event
    requiredChanges: [String!]!
    timeline: ApprovalTimeline
    errors: [ApiError!]!
}

type UpdateTicketCategoryResponse {
    success: Boolean!
    message: String
    event: Event
    category: TicketCategory
    errors: [ApiError!]!
}

type AddTicketCategoryResponse {
    success: Boolean!
    message: String
    event: Event
    category: TicketCategory
    errors: [ApiError!]!
}

type RemoveTicketCategoryResponse {
    success: Boolean!
    message: String
    event: Event
    errors: [ApiError!]!
}

type DuplicateEventResponse {
    success: Boolean!
    message: String
    originalEvent: Event
    newEvent: Event
    errors: [ApiError!]!
}

type FeatureEventResponse {
    success: Boolean!
    message: String
    event: Event
    errors: [ApiError!]!
}

type UnfeatureEventResponse {
    success: Boolean!
    message: String
    event: Event
    errors: [ApiError!]!
}

type CreateCategoryResponse {
    success: Boolean!
    message: String
    category: EventCategory
    errors: [ApiError!]!
}

type UpdateCategoryResponse {
    success: Boolean!
    message: String
    category: EventCategory
    errors: [ApiError!]!
}

type DeleteCategoryResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type CreateLocationResponse {
    success: Boolean!
    message: String
    location: Location
    errors: [ApiError!]!
}

type UpdateLocationResponse {
    success: Boolean!
    message: String
    location: Location
    errors: [ApiError!]!
}

type DeleteLocationResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type CreateCityResponse {
    success: Boolean!
    message: String
    city: City
    errors: [ApiError!]!
}

type UpdateCityResponse {
    success: Boolean!
    message: String
    city: City
    errors: [ApiError!]!
}

type DeleteCityResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type CreateProvinceResponse {
    success: Boolean!
    message: String
    province: Province
    errors: [ApiError!]!
}

type UpdateProvinceResponse {
    success: Boolean!
    message: String
    province: Province
    errors: [ApiError!]!
}

type DeleteProvinceResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}
```

---

## 4. Booking Service API

### 4.1 GraphQL Schema - Types

```graphql
# ============================================================================
# BOOKING SERVICE - TYPES
# ============================================================================

# Custom Scalars
scalar DateTime
scalar BigDecimal
scalar JSON
scalar UUID

# ============================================================================
# ENUMS
# ============================================================================

enum TicketStatus {
    RESERVED              # In checkout, payment pending
    PENDING_PAYMENT       # Payment initiated, awaiting confirmation
    PURCHASED             # Payment confirmed, ticket issued
    VALIDATED             # Checked in at event
    USED                  # Fully used
    EXPIRED               # Event passed, not used
    CANCELLED             # Cancelled by user/system
    REFUNDED              # Refund completed
    TRANSFERRED           # Transferred to another user
}

enum PaymentStatus {
    PENDING               # Payment not started
    PROCESSING            # Payment in progress (user completing)
    SUCCEEDED             # Payment confirmed
    FAILED                # Payment failed
    EXPIRED               # Payment timeout
    CANCELLED             # User cancelled
    REFUNDED              # Full refund processed
    PARTIALLY_REFUNDED    # Partial refund
}

enum PaymentProvider {
    PAWAPAY               # Unified mobile money
    MTN_MOMO_ZMB          # MTN Mobile Money (via pawaPay)
    AIRTEL_ZMB            # Airtel Money (via pawaPay)
    ZAMTEL_ZMB            # Zamtel Kwacha (via pawaPay)
}

enum CommissionStatus {
    PENDING               # Commission recorded, not yet earned
    EARNED                # Event complete + 7 days, now platform revenue
    CANCELLED             # Refund before event, commission cancelled
    CLAWED_BACK           # Refund after event (rare), deducted from earned
}

enum EscrowStatus {
    CREATED               # Account created, no funds
    ACTIVE                # Receiving funds
    LOCKED                # Event happened, in hold period
    PAYOUT_ELIGIBLE       # Hold period passed, can payout
    PROCESSING_PAYOUT     # Payout in progress
    CLOSED                # All funds paid out
    CANCELLED             # Event cancelled, refunds processed
}

enum RefundStatus {
    PENDING               # Refund requested
    APPROVED              # Approved, awaiting processing
    PROCESSING            # Refund in progress
    COMPLETED             # Refund sent to customer
    FAILED                # Refund failed
    REJECTED              # Refund request denied
    CANCELLED             # Request cancelled
}

enum RefundType {
    USER_REQUESTED        # Customer requested
    EVENT_CANCELLED       # Automatic due to cancellation
    EVENT_RESCHEDULED     # Customer opted out after reschedule
    ADMIN_INITIATED       # Admin processed refund
    QUALITY_ISSUE         # Dispute resolution
}

enum TransactionType {
    TICKET_SALE           # Customer payment received
    ESCROW_CREDIT         # Organizer portion to escrow
    COMMISSION_PENDING    # Platform commission (pending)
    COMMISSION_EARNED     # Commission becomes revenue
    COMMISSION_CANCELLED  # Commission cancelled on refund
    REFUND_DEBIT          # Money leaving escrow for refund
    PAYOUT                # Money to organizer bank
    PROCESSING_FEE        # Payment provider fee
}

enum JournalEntryType {
    DEBIT
    CREDIT
}

# ============================================================================
# TICKET TYPES
# ============================================================================

"""
Ticket - Purchased ticket for an event
"""
type Ticket {
    id: ID!
    ticketNumber: String!                # Human-readable: "TKT-ABC12345"

    # Event Info (denormalized)
    eventId: String!
    eventTitle: String!
    eventDate: DateTime!
    eventEndDate: DateTime
    eventLocationName: String
    eventLocationAddress: String
    eventCity: String
    eventBannerUrl: String

    # Buyer Info
    buyerId: String!
    buyerName: String!
    buyerEmail: String!
    buyerPhone: String

    # Organizer Info
    organizerId: String!
    organizerName: String

    # Ticket Details
    ticketCategoryCode: String!
    ticketCategoryName: String!
    ticketCategoryType: String

    # Pricing
    price: BigDecimal!
    currency: String!
    quantity: Int!
    totalAmount: BigDecimal!

    # Commission (Two-Stage Model)
    commissionRate: BigDecimal!          # e.g., 0.05 (5%)
    commissionAmount: BigDecimal!        # price * rate
    netAmount: BigDecimal!               # price - commission
    commissionStatus: CommissionStatus!
    commissionEarnedAt: DateTime

    # Status
    status: TicketStatus!
    statusHistory: [TicketStatusChange!]!

    # Reservation
    reservedAt: DateTime
    reservationExpiresAt: DateTime

    # Payment
    paymentIntentId: String
    paymentStatus: PaymentStatus
    paymentProvider: PaymentProvider
    paymentReference: String             # Provider's transaction ID
    paidAt: DateTime

    # Validation
    qrCode: String                       # Base64 encoded QR
    qrCodeUrl: String                    # URL to QR image
    validatedAt: DateTime
    validatedBy: String
    usedAt: DateTime

    # Refund
    refundRequestId: String
    refundStatus: RefundStatus
    refundedAt: DateTime
    refundAmount: BigDecimal
    refundReason: String

    # Transfer
    transferredTo: String                # New owner user ID
    transferredAt: DateTime
    originalBuyerId: String

    # Audit
    correlationId: String!               # For tracing
    idempotencyKey: String               # For duplicate prevention
    createdAt: DateTime!
    updatedAt: DateTime!
    version: Int!
}

type TicketStatusChange {
    fromStatus: TicketStatus
    toStatus: TicketStatus!
    changedAt: DateTime!
    changedBy: String
    reason: String
}

# ============================================================================
# PAYMENT TYPES
# ============================================================================

"""
PaymentIntent - Represents a payment attempt
"""
type PaymentIntent {
    id: ID!
    idempotencyKey: String!
    transactionRef: String!              # Our reference: "TXN-20260301-ABC123"
    providerTransactionId: String        # Provider's reference

    # References
    ticketId: String!
    eventId: String!
    userId: String!

    # Amount
    amount: BigDecimal!
    currency: String!

    # Provider
    provider: PaymentProvider!
    phoneNumber: String!                 # Customer phone for mobile money
    correspondent: String                # MTN_MOMO_ZMB, AIRTEL_ZMB, etc.

    # Status
    status: PaymentStatus!
    failureReason: String
    failureCode: String

    # Tracking
    webhookAttempts: Int!
    lastWebhookAt: DateTime
    pollAttempts: Int!
    lastPolledAt: DateTime

    # Timing
    createdAt: DateTime!
    expiresAt: DateTime!
    processedAt: DateTime
    version: Int!
}

"""
PaymentResult - Result of initiating payment
"""
type PaymentResult {
    paymentIntentId: String!
    status: PaymentStatus!
    transactionRef: String!

    # For USSD prompt
    ussdCode: String                     # e.g., "*165*PIN#"

    # For mobile prompt
    promptSent: Boolean!
    promptMessage: String                # "Check your phone for payment prompt"

    # Expiry
    expiresAt: DateTime!
    timeoutSeconds: Int!
}

# ============================================================================
# ESCROW TYPES
# ============================================================================

"""
EscrowAccount - Per-event escrow for organizer funds
"""
type EscrowAccount {
    id: ID!
    accountNumber: String!               # "ESC-EVT001-2026"

    # References
    eventId: String!
    eventTitle: String
    organizerId: String!
    organizerName: String

    # Balances
    currentBalance: BigDecimal!
    totalDeposits: BigDecimal!
    totalWithdrawals: BigDecimal!
    totalRefunds: BigDecimal!
    pendingWithdrawals: BigDecimal!

    # Status
    status: EscrowStatus!
    lockUntil: DateTime                  # Event date + 7 days
    payoutEligibleAt: DateTime

    # Limits
    minimumBalance: BigDecimal!
    maximumPayoutAmount: BigDecimal

    # Currency
    currency: String!

    # Transaction History (recent)
    recentTransactions: [EscrowTransaction!]!

    # Audit
    createdAt: DateTime!
    updatedAt: DateTime!
    version: Int!
}

type EscrowTransaction {
    id: ID!
    type: JournalEntryType!              # DEBIT or CREDIT
    category: String!                    # TICKET_SALE, REFUND, PAYOUT
    amount: BigDecimal!
    balanceAfter: BigDecimal!
    ticketId: String
    paymentIntentId: String
    refundRequestId: String
    payoutRequestId: String
    description: String!
    timestamp: DateTime!
}

# ============================================================================
# COMMISSION & LEDGER TYPES
# ============================================================================

"""
CommissionRecord - Platform commission tracking
"""
type CommissionRecord {
    id: ID!
    ticketId: String!
    eventId: String!
    organizerId: String!

    # Amount
    amount: BigDecimal!
    rate: BigDecimal!
    ticketPrice: BigDecimal!
    currency: String!

    # Status (Two-Stage Model)
    status: CommissionStatus!
    pendingAt: DateTime!                 # When recorded
    earnedAt: DateTime                   # When became revenue
    cancelledAt: DateTime                # If refunded before event
    clawedBackAt: DateTime               # If refunded after event (rare)

    # References
    refundRequestId: String              # If cancelled/clawed back
}

"""
PlatformAccount - Platform-level virtual account
"""
type PlatformAccount {
    id: ID!
    accountType: PlatformAccountType!
    accountName: String!

    # Balances
    currentBalance: BigDecimal!
    totalCredits: BigDecimal!
    totalDebits: BigDecimal!

    # Currency
    currency: String!

    # Recent transactions
    recentTransactions: [LedgerEntry!]!

    updatedAt: DateTime!
}

enum PlatformAccountType {
    PENDING_COMMISSION                   # Commission not yet earned
    EARNED_REVENUE                       # Confirmed platform revenue
    OPERATIONS                           # Operating expenses
    REFUND_RESERVE                       # Reserve for processing fees
}

"""
LedgerEntry - Double-entry bookkeeping entry
"""
type LedgerEntry {
    id: ID!
    journalEntryId: String!              # Groups related entries
    accountId: String!
    accountType: String!

    entryType: JournalEntryType!         # DEBIT or CREDIT
    amount: BigDecimal!
    balanceAfter: BigDecimal!

    # Transaction details
    transactionType: TransactionType!
    description: String!

    # References
    ticketId: String
    eventId: String
    refundRequestId: String
    payoutRequestId: String

    timestamp: DateTime!
}

"""
JournalEntry - Complete journal entry with all legs
"""
type JournalEntry {
    id: ID!
    transactionType: TransactionType!
    description: String!
    totalAmount: BigDecimal!

    # Entries (debits and credits)
    entries: [LedgerEntry!]!

    # Validation
    isBalanced: Boolean!                 # Total debits = Total credits

    # References
    ticketId: String
    eventId: String
    refundRequestId: String
    payoutRequestId: String

    # Audit
    createdAt: DateTime!
    createdBy: String
}

# ============================================================================
# REFUND TYPES
# ============================================================================

"""
RefundRequest - Customer refund request
"""
type RefundRequest {
    id: ID!
    requestNumber: String!               # "REF-ABC12345"

    # References
    ticketId: String!
    ticketNumber: String!
    eventId: String!
    eventTitle: String
    organizerId: String!
    buyerId: String!

    # Type
    refundType: RefundType!

    # Amounts
    originalTicketPrice: BigDecimal!
    refundAmount: BigDecimal!
    processingFee: BigDecimal!
    netRefundAmount: BigDecimal!         # What customer receives
    currency: String!

    # Commission handling
    commissionToCancel: BigDecimal!      # If before event
    commissionToClawback: BigDecimal!    # If after event (rare)

    # Status
    status: RefundStatus!
    statusHistory: [RefundStatusChange!]!

    # Request details
    reason: String!
    additionalNotes: String
    requestedBy: String!
    requestedAt: DateTime!

    # Review
    reviewedBy: String
    reviewedAt: DateTime
    reviewComments: String
    rejectionReason: String

    # Processing
    processedAt: DateTime
    processedBy: String
    paymentReference: String             # Refund transaction ID

    # Audit
    createdAt: DateTime!
    updatedAt: DateTime!
}

type RefundStatusChange {
    fromStatus: RefundStatus
    toStatus: RefundStatus!
    changedAt: DateTime!
    changedBy: String
    reason: String
}

# ============================================================================
# STATISTICS TYPES
# ============================================================================

type TicketSalesStats {
    eventId: String!
    totalTicketsSold: Int!
    totalRevenue: BigDecimal!
    ticketsByCategory: [CategorySalesStats!]!
    salesByDay: [DailySalesStats!]!
    averageTicketPrice: BigDecimal!
}

type CategorySalesStats {
    categoryCode: String!
    categoryName: String!
    sold: Int!
    available: Int!
    revenue: BigDecimal!
}

type DailySalesStats {
    date: String!
    ticketsSold: Int!
    revenue: BigDecimal!
}

type RefundStats {
    eventId: String!
    totalRefunds: Int!
    totalRefundAmount: BigDecimal!
    pendingRefunds: Int!
    pendingAmount: BigDecimal!
    refundsByType: [RefundTypeStats!]!
}

type RefundTypeStats {
    refundType: RefundType!
    count: Int!
    amount: BigDecimal!
}

type FinancialSummary {
    # Revenue
    grossRevenue: BigDecimal!
    netRevenue: BigDecimal!

    # Commission
    pendingCommission: BigDecimal!
    earnedCommission: BigDecimal!
    cancelledCommission: BigDecimal!

    # Escrow
    totalInEscrow: BigDecimal!
    lockedInEscrow: BigDecimal!
    payoutEligible: BigDecimal!

    # Refunds
    totalRefunded: BigDecimal!
    pendingRefunds: BigDecimal!

    # Payouts
    totalPaidOut: BigDecimal!
    pendingPayouts: BigDecimal!
}
```

### 4.2 GraphQL Schema - Queries

```graphql
# ============================================================================
# BOOKING SERVICE - QUERIES
# ============================================================================

type Query {
    # ========================================================================
    # TICKET QUERIES
    # ========================================================================

    """
    Get ticket by ID
    """
    ticket(id: ID!): Ticket

    """
    Get ticket by ticket number
    """
    ticketByNumber(ticketNumber: String!): Ticket

    """
    Get my tickets (current user)
    """
    myTickets(
        status: TicketStatus
        pagination: CursorPaginationInput
    ): TicketConnection!

    """
    Get my active tickets (upcoming events)
    """
    myActiveTickets(
        pagination: CursorPaginationInput
    ): TicketConnection!

    """
    Get my past tickets
    """
    myPastTickets(
        pagination: CursorPaginationInput
    ): TicketConnection!

    """
    Get tickets for event (organizer)
    """
    ticketsByEvent(
        eventId: String!
        status: TicketStatus
        pagination: CursorPaginationInput
    ): TicketConnection!

    """
    Search tickets (admin)
    """
    searchTickets(
        query: String!
        filter: TicketSearchFilter
        pageable: PageableInput
    ): PagedTicketResult!

    """
    Validate ticket (scanner)
    Returns ticket if valid, null if not found
    """
    validateTicket(
        ticketNumber: String!
        eventId: String!
    ): TicketValidationResult!

    # ========================================================================
    # PAYMENT QUERIES
    # ========================================================================

    """
    Get payment intent by ID
    """
    paymentIntent(id: ID!): PaymentIntent

    """
    Get payment status by transaction reference
    """
    paymentStatus(transactionRef: String!): PaymentIntent

    """
    Get payment history for user
    """
    myPayments(
        status: PaymentStatus
        pagination: CursorPaginationInput
    ): PaymentIntentConnection!

    """
    Get predicted provider for phone number
    """
    predictProvider(phoneNumber: String!): PredictedProvider!

    # ========================================================================
    # ESCROW QUERIES
    # ========================================================================

    """
    Get escrow account by ID
    """
    escrowAccount(id: ID!): EscrowAccount

    """
    Get escrow account for event
    """
    escrowAccountByEvent(eventId: String!): EscrowAccount

    """
    Get all escrow accounts for organizer
    """
    organizerEscrowAccounts(
        organizerId: String!
        status: EscrowStatus
    ): [EscrowAccount!]!

    """
    Get escrow transactions
    """
    escrowTransactions(
        escrowAccountId: String!
        dateFrom: DateTime
        dateTo: DateTime
        pagination: CursorPaginationInput
    ): EscrowTransactionConnection!

    """
    Admin: Get all escrow accounts
    """
    escrowAccountsAdmin(
        status: EscrowStatus
        pageable: PageableInput
    ): PagedEscrowAccountResult!

    # ========================================================================
    # COMMISSION QUERIES
    # ========================================================================

    """
    Get commission records for event
    """
    eventCommissions(
        eventId: String!
    ): [CommissionRecord!]!

    """
    Get platform commission summary
    """
    commissionSummary(
        dateFrom: DateTime
        dateTo: DateTime
    ): CommissionSummary!

    """
    Get platform accounts
    """
    platformAccounts: [PlatformAccount!]!

    """
    Get ledger entries
    """
    ledgerEntries(
        accountId: String!
        dateFrom: DateTime
        dateTo: DateTime
        pagination: CursorPaginationInput
    ): LedgerEntryConnection!

    """
    Get journal entry by ID
    """
    journalEntry(id: ID!): JournalEntry

    # ========================================================================
    # REFUND QUERIES
    # ========================================================================

    """
    Get refund request by ID
    """
    refundRequest(id: ID!): RefundRequest

    """
    Get my refund requests
    """
    myRefundRequests(
        status: RefundStatus
    ): [RefundRequest!]!

    """
    Get refund requests for event (organizer)
    """
    eventRefundRequests(
        eventId: String!
        status: RefundStatus
    ): [RefundRequest!]!

    """
    Check if ticket is eligible for refund
    """
    isRefundEligible(ticketId: String!): RefundEligibility!

    """
    Admin: Get pending refund requests
    """
    pendingRefundRequests(
        pageable: PageableInput
    ): PagedRefundRequestResult!

    # ========================================================================
    # STATISTICS QUERIES
    # ========================================================================

    """
    Get ticket sales stats for event
    """
    ticketSalesStats(eventId: String!): TicketSalesStats!

    """
    Get refund stats for event
    """
    refundStats(eventId: String!): RefundStats!

    """
    Get financial summary (admin)
    """
    financialSummary(
        dateFrom: DateTime
        dateTo: DateTime
    ): FinancialSummary!

    """
    Get organizer financial summary
    """
    organizerFinancialSummary(
        organizerId: String!
        dateFrom: DateTime
        dateTo: DateTime
    ): OrganizerFinancialSummary!
}

# ============================================================================
# FILTER & RESULT TYPES
# ============================================================================

input TicketSearchFilter {
    eventId: String
    buyerId: String
    organizerId: String
    status: TicketStatus
    commissionStatus: CommissionStatus
    dateFrom: DateTime
    dateTo: DateTime
}

type TicketConnection {
    edges: [TicketEdge!]!
    pageInfo: PageInfo!
    totalCount: Int
}

type TicketEdge {
    node: Ticket!
    cursor: String!
}

type PagedTicketResult {
    content: [Ticket!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

type PaymentIntentConnection {
    edges: [PaymentIntentEdge!]!
    pageInfo: PageInfo!
}

type PaymentIntentEdge {
    node: PaymentIntent!
    cursor: String!
}

type EscrowTransactionConnection {
    edges: [EscrowTransactionEdge!]!
    pageInfo: PageInfo!
}

type EscrowTransactionEdge {
    node: EscrowTransaction!
    cursor: String!
}

type LedgerEntryConnection {
    edges: [LedgerEntryEdge!]!
    pageInfo: PageInfo!
}

type LedgerEntryEdge {
    node: LedgerEntry!
    cursor: String!
}

type PagedEscrowAccountResult {
    content: [EscrowAccount!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

type PagedRefundRequestResult {
    content: [RefundRequest!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

"""
Result of ticket validation (scanner)
"""
type TicketValidationResult {
    valid: Boolean!
    ticket: Ticket
    message: String!
    errorCode: String                    # If invalid
}

"""
Predicted mobile money provider
"""
type PredictedProvider {
    phoneNumber: String!
    provider: PaymentProvider!
    correspondent: String!               # MTN_MOMO_ZMB, etc.
    country: String!
}

"""
Refund eligibility check result
"""
type RefundEligibility {
    eligible: Boolean!
    ticketId: String!
    reason: String!
    maxRefundAmount: BigDecimal
    processingFee: BigDecimal
    netRefundAmount: BigDecimal
    refundDeadline: DateTime
}

type CommissionSummary {
    totalPending: BigDecimal!
    totalEarned: BigDecimal!
    totalCancelled: BigDecimal!
    totalClawedBack: BigDecimal!
    byEventType: [EventTypeCommission!]!
}

type EventTypeCommission {
    eventType: String!
    pending: BigDecimal!
    earned: BigDecimal!
}

type OrganizerFinancialSummary {
    organizerId: String!
    totalGrossRevenue: BigDecimal!
    totalCommissionPaid: BigDecimal!
    totalNetRevenue: BigDecimal!
    totalInEscrow: BigDecimal!
    totalPaidOut: BigDecimal!
    pendingPayout: BigDecimal!
    totalRefunded: BigDecimal!
}
```

### 4.3 GraphQL Schema - Mutations

```graphql
# ============================================================================
# BOOKING SERVICE - MUTATIONS
# ============================================================================

type Mutation {
    # ========================================================================
    # TICKET PURCHASE FLOW
    # ========================================================================

    """
    Reserve tickets (start checkout)
    Creates ticket in RESERVED status with 10-minute hold
    """
    reserveTickets(
        input: ReserveTicketsInput!
    ): ReserveTicketsResponse!

    """
    Initiate payment for reserved tickets
    Calls pawaPay to send payment prompt to customer phone
    """
    initiatePayment(
        input: InitiatePaymentInput!
    ): InitiatePaymentResponse!

    """
    Confirm payment (webhook fallback)
    Only needed if webhook doesn't fire
    """
    confirmPayment(
        transactionRef: String!
    ): ConfirmPaymentResponse!

    """
    Cancel reservation
    Releases ticket hold if payment not completed
    """
    cancelReservation(
        ticketId: String!
        reason: String
    ): CancelReservationResponse!

    # ========================================================================
    # TICKET VALIDATION (Scanner)
    # ========================================================================

    """
    Mark ticket as validated (check-in)
    """
    validateTicket(
        ticketNumber: String!
        eventId: String!
        scannerId: String!
    ): ValidateTicketResponse!

    """
    Mark ticket as used
    """
    useTicket(
        ticketNumber: String!
        scannerId: String!
    ): UseTicketResponse!

    # ========================================================================
    # REFUND REQUESTS
    # ========================================================================

    """
    Request refund for ticket
    """
    requestRefund(
        input: RequestRefundInput!
    ): RequestRefundResponse!

    """
    Cancel refund request
    """
    cancelRefundRequest(
        refundRequestId: String!
    ): CancelRefundRequestResponse!

    """
    Admin: Approve refund request
    """
    approveRefund(
        refundRequestId: String!
        comments: String
    ): ApproveRefundResponse!

    """
    Admin: Reject refund request
    """
    rejectRefund(
        refundRequestId: String!
        reason: String!
    ): RejectRefundResponse!

    """
    Admin: Process approved refund
    Sends money back to customer via pawaPay payout
    """
    processRefund(
        refundRequestId: String!
    ): ProcessRefundResponse!

    # ========================================================================
    # ESCROW MANAGEMENT (Internal/Admin)
    # ========================================================================

    """
    Create escrow account for event (internal, called on event publish)
    """
    createEscrowAccount(
        eventId: String!
        organizerId: String!
        currency: String = "ZMW"
    ): CreateEscrowAccountResponse!

    """
    Lock escrow (internal, called when event happens)
    """
    lockEscrowAccount(
        eventId: String!
    ): LockEscrowAccountResponse!

    """
    Mark escrow as payout eligible (internal, after 7-day hold)
    """
    markPayoutEligible(
        eventId: String!
    ): MarkPayoutEligibleResponse!

    # ========================================================================
    # COMMISSION MANAGEMENT (Internal)
    # ========================================================================

    """
    Convert pending commission to earned (batch job)
    Called 7 days after event completion
    """
    earnCommissions(
        eventId: String!
    ): EarnCommissionsResponse!

    # ========================================================================
    # JOURNAL ENTRIES (Finance/Admin)
    # ========================================================================

    """
    Create manual journal entry
    For corrections and adjustments
    """
    createJournalEntry(
        input: CreateJournalEntryInput!
    ): CreateJournalEntryResponse!
}

# ============================================================================
# MUTATION INPUT TYPES
# ============================================================================

input ReserveTicketsInput {
    eventId: String!
    ticketCategoryCode: String!
    quantity: Int!                       # 1-10
    buyerName: String!
    buyerEmail: String!
    buyerPhone: String!
    idempotencyKey: String!              # Client-generated UUID
}

input InitiatePaymentInput {
    ticketId: String!
    phoneNumber: String!                 # Mobile money number
    idempotencyKey: String!
}

input RequestRefundInput {
    ticketId: String!
    reason: String!
    additionalNotes: String
}

input CreateJournalEntryInput {
    description: String!
    entries: [JournalEntryLineInput!]!   # Min 2 entries
    references: JournalReferenceInput
}

input JournalEntryLineInput {
    accountId: String!
    entryType: JournalEntryType!         # DEBIT or CREDIT
    amount: BigDecimal!
    description: String
}

input JournalReferenceInput {
    ticketId: String
    eventId: String
    refundRequestId: String
    payoutRequestId: String
}

# ============================================================================
# MUTATION RESPONSE TYPES
# ============================================================================

type ReserveTicketsResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    reservationExpiresAt: DateTime
    errors: [ApiError!]!
}

type InitiatePaymentResponse {
    success: Boolean!
    message: String
    paymentResult: PaymentResult
    ticket: Ticket
    errors: [ApiError!]!
}

type ConfirmPaymentResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    paymentIntent: PaymentIntent
    errors: [ApiError!]!
}

type CancelReservationResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type ValidateTicketResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    validationCode: String               # Display code for confirmation
    errors: [ApiError!]!
}

type UseTicketResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    errors: [ApiError!]!
}

type TransferTicketResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    transferToken: String                # Token for recipient
    expiresAt: DateTime
    errors: [ApiError!]!
}

type AcceptTransferResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    errors: [ApiError!]!
}

type CancelTransferResponse {
    success: Boolean!
    message: String
    ticket: Ticket
    errors: [ApiError!]!
}

type RequestRefundResponse {
    success: Boolean!
    message: String
    refundRequest: RefundRequest
    errors: [ApiError!]!
}

type CancelRefundRequestResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type ApproveRefundResponse {
    success: Boolean!
    message: String
    refundRequest: RefundRequest
    errors: [ApiError!]!
}

type RejectRefundResponse {
    success: Boolean!
    message: String
    refundRequest: RefundRequest
    errors: [ApiError!]!
}

type ProcessRefundResponse {
    success: Boolean!
    message: String
    refundRequest: RefundRequest
    payoutReference: String              # pawaPay payout ID
    errors: [ApiError!]!
}

type CreateEscrowAccountResponse {
    success: Boolean!
    message: String
    escrowAccount: EscrowAccount
    errors: [ApiError!]!
}

type LockEscrowAccountResponse {
    success: Boolean!
    message: String
    escrowAccount: EscrowAccount
    errors: [ApiError!]!
}

type MarkPayoutEligibleResponse {
    success: Boolean!
    message: String
    escrowAccount: EscrowAccount
    errors: [ApiError!]!
}

type EarnCommissionsResponse {
    success: Boolean!
    message: String
    commissionsEarned: Int
    totalAmount: BigDecimal
    errors: [ApiError!]!
}

type CreateJournalEntryResponse {
    success: Boolean!
    message: String
    journalEntry: JournalEntry
    errors: [ApiError!]!
}
```

---

## 5. Identity Service API

### 5.1 GraphQL Schema

```graphql
# ============================================================================
# IDENTITY SERVICE - TYPES
# ============================================================================

scalar DateTime
scalar BigDecimal
scalar JSON

# ============================================================================
# ENUMS
# ============================================================================

enum UserType {
    CUSTOMER              # Regular ticket buyer
    ORGANIZER             # Event organizer
    SCANNER               # Ticket validator
    ADMIN                 # Platform admin
    SUPER_ADMIN           # Full access
    FINANCE               # Financial operations
}

enum OrganizerStatus {
    PENDING               # Application submitted
    UNDER_REVIEW          # Being reviewed
    APPROVED              # Can create events
    REJECTED              # Application rejected
    SUSPENDED             # Temporarily disabled
    BANNED                # Permanently disabled
}

enum PayoutStatus {
    PENDING               # Requested by organizer
    PENDING_APPROVAL      # Awaiting finance approval
    APPROVED              # Approved, ready to process
    PROCESSING            # Sending to bank
    COMPLETED             # Money sent
    FAILED                # Transfer failed
    REJECTED              # Request rejected
    CANCELLED             # Cancelled by organizer
}

enum NotificationType {
    TICKET_PURCHASED
    TICKET_VALIDATED
    EVENT_REMINDER
    EVENT_CANCELLED
    EVENT_RESCHEDULED
    REFUND_APPROVED
    REFUND_COMPLETED
    PAYOUT_COMPLETED
    ORGANIZER_APPROVED
    CUSTOM
}

enum NotificationChannel {
    EMAIL
    SMS
    PUSH
    IN_APP
}

# ============================================================================
# USER TYPES
# ============================================================================

type User {
    id: ID!
    username: String!
    email: String!
    firstName: String!
    lastName: String!
    fullName: String!
    phoneNumber: String

    # Type & Status
    userType: UserType!
    isActive: Boolean!
    isEmailVerified: Boolean!
    isPhoneVerified: Boolean!

    # Profile
    avatarUrl: String
    bio: String
    dateOfBirth: DateTime
    gender: String

    # Organizer fields (if ORGANIZER type)
    organizerProfile: OrganizerProfile

    # Preferences
    notificationPreferences: NotificationPreferences

    # Audit
    lastLoginAt: DateTime
    createdAt: DateTime!
    updatedAt: DateTime!
}

type OrganizerProfile {
    id: ID!
    userId: String!

    # Business Info
    companyName: String!
    companyRegistrationNumber: String
    taxId: String
    businessEmail: String!
    businessPhone: String!

    # Address
    businessAddress: String
    city: String
    province: String
    country: String

    # Verification
    status: OrganizerStatus!
    submittedAt: DateTime
    reviewedAt: DateTime
    reviewedBy: String
    reviewNotes: String
    rejectionReason: String

    # Documents
    documents: [OrganizerDocument!]!

    # Statistics
    totalEvents: Int!
    totalTicketsSold: Int!
    totalRevenue: BigDecimal!
    rating: Float

    # Commission
    commissionRate: BigDecimal!          # Custom rate if negotiated

    # Bank Accounts
    bankAccounts: [BankAccount!]!
    primaryBankAccount: BankAccount

    # Audit
    createdAt: DateTime!
    updatedAt: DateTime!
}

type OrganizerDocument {
    id: ID!
    documentType: String!                # "BUSINESS_LICENSE", "ID_CARD", etc.
    fileName: String!
    fileUrl: String!
    uploadedAt: DateTime!
    verifiedAt: DateTime
    verifiedBy: String
    status: String!                      # "PENDING", "VERIFIED", "REJECTED"
}

type BankAccount {
    id: ID!
    userId: String!

    # Bank Details
    bankName: String!
    bankCode: String!
    accountNumber: String!               # Masked: "****1234"
    accountHolderName: String!
    branchCode: String
    branchName: String
    swiftCode: String

    # Mobile Money (alternative)
    isMobileMoney: Boolean!
    mobileMoneyProvider: String          # MTN, Airtel, Zamtel
    mobileMoneyNumber: String            # Masked

    # Status
    isPrimary: Boolean!
    isVerified: Boolean!
    verifiedAt: DateTime
    verificationMethod: String           # "MICRO_DEPOSIT", "DOCUMENT", "MANUAL"

    # Audit
    createdAt: DateTime!
    updatedAt: DateTime!
}

type NotificationPreferences {
    email: Boolean!
    sms: Boolean!
    push: Boolean!
    marketingEmails: Boolean!

    # Specific notifications
    ticketPurchaseConfirmation: Boolean!
    eventReminders: Boolean!
    eventUpdates: Boolean!
    refundUpdates: Boolean!
    payoutNotifications: Boolean!
}

# ============================================================================
# PAYOUT TYPES
# ============================================================================

type PayoutRequest {
    id: ID!
    requestNumber: String!               # "PAY-ABC12345"

    # References
    organizerId: String!
    organizerName: String
    escrowAccountId: String!
    eventId: String
    eventTitle: String
    bankAccountId: String!

    # Amount
    amount: BigDecimal!
    currency: String!
    processingFee: BigDecimal!
    netAmount: BigDecimal!               # Amount to receive

    # Status
    status: PayoutStatus!
    statusHistory: [PayoutStatusChange!]!

    # Request
    notes: String
    requestedAt: DateTime!

    # Review
    approvedBy: String
    approvedAt: DateTime
    rejectedBy: String
    rejectionReason: String

    # Processing
    processedBy: String
    processedAt: DateTime
    paymentReference: String             # Bank/mobile money reference
    transactionId: String                # pawaPay transaction ID

    # Audit
    createdAt: DateTime!
    updatedAt: DateTime!
}

type PayoutStatusChange {
    fromStatus: PayoutStatus
    toStatus: PayoutStatus!
    changedAt: DateTime!
    changedBy: String
    reason: String
}

# ============================================================================
# NOTIFICATION TYPES
# ============================================================================

type Notification {
    id: ID!
    userId: String!

    # Content
    type: NotificationType!
    title: String!
    message: String!
    data: JSON                           # Additional data

    # Delivery
    channels: [NotificationChannel!]!
    emailSent: Boolean!
    smsSent: Boolean!
    pushSent: Boolean!

    # Status
    read: Boolean!
    readAt: DateTime

    # References
    eventId: String
    ticketId: String
    refundRequestId: String
    payoutRequestId: String

    # Audit
    createdAt: DateTime!
}

# ============================================================================
# QUERIES
# ============================================================================

type Query {
    # ========================================================================
    # USER QUERIES
    # ========================================================================

    """
    Get current user
    """
    me: User

    """
    Get user by ID
    """
    user(id: ID!): User

    """
    Search users (admin)
    """
    searchUsers(
        query: String!
        userType: UserType
        pageable: PageableInput
    ): PagedUserResult!

    """
    Get users by type
    """
    usersByType(
        userType: UserType!
        pageable: PageableInput
    ): PagedUserResult!

    # ========================================================================
    # ORGANIZER QUERIES
    # ========================================================================

    """
    Get organizer profile
    """
    organizerProfile(userId: String!): OrganizerProfile

    """
    Get pending organizer applications
    """
    pendingOrganizerApplications(
        pageable: PageableInput
    ): PagedOrganizerProfileResult!

    """
    Search organizers
    """
    searchOrganizers(
        query: String!
        status: OrganizerStatus
        pageable: PageableInput
    ): PagedOrganizerProfileResult!

    # ========================================================================
    # BANK ACCOUNT QUERIES
    # ========================================================================

    """
    Get my bank accounts
    """
    myBankAccounts: [BankAccount!]!

    """
    Get bank account by ID
    """
    bankAccount(id: ID!): BankAccount

    """
    Get primary bank account
    """
    primaryBankAccount: BankAccount

    # ========================================================================
    # PAYOUT QUERIES
    # ========================================================================

    """
    Get payout request by ID
    """
    payoutRequest(id: ID!): PayoutRequest

    """
    Get my payout requests (organizer)
    """
    myPayoutRequests(
        status: PayoutStatus
        pagination: CursorPaginationInput
    ): PayoutRequestConnection!

    """
    Get pending payout requests (finance)
    """
    pendingPayoutRequests(
        pageable: PageableInput
    ): PagedPayoutRequestResult!

    """
    Get payout requests for organizer (admin)
    """
    organizerPayoutRequests(
        organizerId: String!
        status: PayoutStatus
        pageable: PageableInput
    ): PagedPayoutRequestResult!

    # ========================================================================
    # NOTIFICATION QUERIES
    # ========================================================================

    """
    Get my notifications
    """
    myNotifications(
        unreadOnly: Boolean = false
        pagination: CursorPaginationInput
    ): NotificationConnection!

    """
    Get unread notification count
    """
    unreadNotificationCount: Int!

    # ========================================================================
    # STATISTICS QUERIES
    # ========================================================================

    """
    Get platform user statistics
    """
    userStats: UserStats!
}

# ============================================================================
# MUTATIONS
# ============================================================================

type Mutation {
    # ========================================================================
    # AUTHENTICATION
    # ========================================================================

    """
    Register new user
    """
    register(input: RegisterInput!): RegisterResponse!

    """
    Login
    """
    login(email: String!, password: String!): LoginResponse!

    """
    Refresh access token
    """
    refreshToken(refreshToken: String!): RefreshTokenResponse!

    """
    Logout
    """
    logout: LogoutResponse!

    """
    Request password reset
    """
    requestPasswordReset(email: String!): RequestPasswordResetResponse!

    """
    Reset password with token
    """
    resetPassword(
        token: String!
        newPassword: String!
    ): ResetPasswordResponse!

    """
    Change password (authenticated)
    """
    changePassword(
        currentPassword: String!
        newPassword: String!
    ): ChangePasswordResponse!

    # ========================================================================
    # EMAIL/PHONE VERIFICATION
    # ========================================================================

    """
    Send email verification
    """
    sendEmailVerification: SendVerificationResponse!

    """
    Verify email with token
    """
    verifyEmail(token: String!): VerifyEmailResponse!

    """
    Send phone verification OTP
    """
    sendPhoneVerification: SendVerificationResponse!

    """
    Verify phone with OTP
    """
    verifyPhone(otp: String!): VerifyPhoneResponse!

    # ========================================================================
    # PROFILE MANAGEMENT
    # ========================================================================

    """
    Update profile
    """
    updateProfile(input: UpdateProfileInput!): UpdateProfileResponse!

    """
    Update avatar
    """
    updateAvatar(avatarUrl: String!): UpdateAvatarResponse!

    """
    Update notification preferences
    """
    updateNotificationPreferences(
        input: NotificationPreferencesInput!
    ): UpdatePreferencesResponse!

    # ========================================================================
    # ORGANIZER APPLICATION
    # ========================================================================

    """
    Apply to become organizer
    """
    applyAsOrganizer(
        input: OrganizerApplicationInput!
    ): ApplyAsOrganizerResponse!

    """
    Upload organizer document
    """
    uploadOrganizerDocument(
        documentType: String!
        fileUrl: String!
    ): UploadDocumentResponse!

    """
    Admin: Approve organizer application
    """
    approveOrganizer(
        userId: String!
        comments: String
    ): ApproveOrganizerResponse!

    """
    Admin: Reject organizer application
    """
    rejectOrganizer(
        userId: String!
        reason: String!
    ): RejectOrganizerResponse!

    """
    Admin: Suspend organizer
    """
    suspendOrganizer(
        userId: String!
        reason: String!
    ): SuspendOrganizerResponse!

    """
    Admin: Reinstate organizer
    """
    reinstateOrganizer(
        userId: String!
    ): ReinstateOrganizerResponse!

    # ========================================================================
    # BANK ACCOUNT MANAGEMENT
    # ========================================================================

    """
    Add bank account
    """
    addBankAccount(
        input: AddBankAccountInput!
    ): AddBankAccountResponse!

    """
    Add mobile money account
    """
    addMobileMoneyAccount(
        input: AddMobileMoneyAccountInput!
    ): AddBankAccountResponse!

    """
    Update bank account
    """
    updateBankAccount(
        id: ID!
        input: UpdateBankAccountInput!
    ): UpdateBankAccountResponse!

    """
    Delete bank account
    """
    deleteBankAccount(id: ID!): DeleteBankAccountResponse!

    """
    Set primary bank account
    """
    setPrimaryBankAccount(id: ID!): SetPrimaryBankAccountResponse!

    """
    Verify bank account with micro-deposit
    """
    verifyBankAccountMicroDeposit(
        id: ID!
        amount: BigDecimal!
    ): VerifyBankAccountResponse!

    # ========================================================================
    # PAYOUT REQUESTS
    # ========================================================================

    """
    Request payout (organizer)
    """
    requestPayout(
        input: RequestPayoutInput!
    ): RequestPayoutResponse!

    """
    Cancel payout request
    """
    cancelPayoutRequest(id: ID!): CancelPayoutRequestResponse!

    """
    Finance: Approve payout request
    """
    approvePayoutRequest(
        id: ID!
        comments: String
    ): ApprovePayoutRequestResponse!

    """
    Finance: Reject payout request
    """
    rejectPayoutRequest(
        id: ID!
        reason: String!
    ): RejectPayoutRequestResponse!

    """
    Finance: Process payout (send money)
    """
    processPayoutRequest(
        id: ID!
    ): ProcessPayoutRequestResponse!

    # ========================================================================
    # NOTIFICATIONS
    # ========================================================================

    """
    Mark notification as read
    """
    markNotificationRead(id: ID!): MarkReadResponse!

    """
    Mark all notifications as read
    """
    markAllNotificationsRead: MarkAllReadResponse!

    """
    Delete notification
    """
    deleteNotification(id: ID!): DeleteNotificationResponse!

    # ========================================================================
    # ADMIN USER MANAGEMENT
    # ========================================================================

    """
    Admin: Create user
    """
    createUser(input: CreateUserInput!): CreateUserResponse!

    """
    Admin: Update user
    """
    updateUser(
        id: ID!
        input: AdminUpdateUserInput!
    ): UpdateUserResponse!

    """
    Admin: Deactivate user
    """
    deactivateUser(id: ID!): DeactivateUserResponse!

    """
    Admin: Reactivate user
    """
    reactivateUser(id: ID!): ReactivateUserResponse!
}

# ============================================================================
# INPUT TYPES
# ============================================================================

input RegisterInput {
    email: String!                       # Valid email
    password: String!                    # Min 8 chars, 1 upper, 1 number
    firstName: String!                   # Min 2 chars
    lastName: String!                    # Min 2 chars
    phoneNumber: String                  # E.164 format
    username: String                     # Auto-generated if not provided
}

input UpdateProfileInput {
    firstName: String
    lastName: String
    phoneNumber: String
    bio: String
    dateOfBirth: DateTime
    gender: String
}

input NotificationPreferencesInput {
    email: Boolean
    sms: Boolean
    push: Boolean
    marketingEmails: Boolean
    ticketPurchaseConfirmation: Boolean
    eventReminders: Boolean
    eventUpdates: Boolean
    refundUpdates: Boolean
    payoutNotifications: Boolean
}

input OrganizerApplicationInput {
    companyName: String!
    companyRegistrationNumber: String
    taxId: String
    businessEmail: String!
    businessPhone: String!
    businessAddress: String!
    city: String!
    province: String
    country: String = "Zambia"
}

input AddBankAccountInput {
    bankName: String!
    bankCode: String!
    accountNumber: String!               # Full number, will be masked
    accountHolderName: String!
    branchCode: String
    branchName: String
    swiftCode: String
    isPrimary: Boolean = false
}

input AddMobileMoneyAccountInput {
    provider: String!                    # MTN, Airtel, Zamtel
    phoneNumber: String!                 # E.164 format
    accountHolderName: String!
    isPrimary: Boolean = false
}

input UpdateBankAccountInput {
    branchCode: String
    branchName: String
}

input RequestPayoutInput {
    escrowAccountId: String!
    bankAccountId: String!
    amount: BigDecimal!
    currency: String = "ZMW"
    notes: String
}

input CreateUserInput {
    email: String!
    password: String!
    firstName: String!
    lastName: String!
    phoneNumber: String
    userType: UserType!
}

input AdminUpdateUserInput {
    firstName: String
    lastName: String
    phoneNumber: String
    userType: UserType
    isActive: Boolean
}

# ============================================================================
# RESPONSE TYPES
# ============================================================================

type RegisterResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type LoginResponse {
    success: Boolean!
    message: String
    accessToken: String
    refreshToken: String
    expiresIn: Int
    user: User
    errors: [ApiError!]!
}

type RefreshTokenResponse {
    success: Boolean!
    accessToken: String
    refreshToken: String
    expiresIn: Int
    errors: [ApiError!]!
}

type LogoutResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type RequestPasswordResetResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type ResetPasswordResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type ChangePasswordResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type SendVerificationResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type VerifyEmailResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type VerifyPhoneResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type UpdateProfileResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type UpdateAvatarResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type UpdatePreferencesResponse {
    success: Boolean!
    message: String
    preferences: NotificationPreferences
    errors: [ApiError!]!
}

type ApplyAsOrganizerResponse {
    success: Boolean!
    message: String
    organizerProfile: OrganizerProfile
    errors: [ApiError!]!
}

type UploadDocumentResponse {
    success: Boolean!
    message: String
    document: OrganizerDocument
    errors: [ApiError!]!
}

type ApproveOrganizerResponse {
    success: Boolean!
    message: String
    organizerProfile: OrganizerProfile
    errors: [ApiError!]!
}

type RejectOrganizerResponse {
    success: Boolean!
    message: String
    organizerProfile: OrganizerProfile
    errors: [ApiError!]!
}

type SuspendOrganizerResponse {
    success: Boolean!
    message: String
    organizerProfile: OrganizerProfile
    errors: [ApiError!]!
}

type ReinstateOrganizerResponse {
    success: Boolean!
    message: String
    organizerProfile: OrganizerProfile
    errors: [ApiError!]!
}

type AddBankAccountResponse {
    success: Boolean!
    message: String
    bankAccount: BankAccount
    errors: [ApiError!]!
}

type UpdateBankAccountResponse {
    success: Boolean!
    message: String
    bankAccount: BankAccount
    errors: [ApiError!]!
}

type DeleteBankAccountResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type SetPrimaryBankAccountResponse {
    success: Boolean!
    message: String
    bankAccount: BankAccount
    errors: [ApiError!]!
}

type VerifyBankAccountResponse {
    success: Boolean!
    message: String
    bankAccount: BankAccount
    errors: [ApiError!]!
}

type RequestPayoutResponse {
    success: Boolean!
    message: String
    payoutRequest: PayoutRequest
    errors: [ApiError!]!
}

type CancelPayoutRequestResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type ApprovePayoutRequestResponse {
    success: Boolean!
    message: String
    payoutRequest: PayoutRequest
    errors: [ApiError!]!
}

type RejectPayoutRequestResponse {
    success: Boolean!
    message: String
    payoutRequest: PayoutRequest
    errors: [ApiError!]!
}

type ProcessPayoutRequestResponse {
    success: Boolean!
    message: String
    payoutRequest: PayoutRequest
    transactionId: String
    errors: [ApiError!]!
}

type MarkReadResponse {
    success: Boolean!
    errors: [ApiError!]!
}

type MarkAllReadResponse {
    success: Boolean!
    markedCount: Int!
    errors: [ApiError!]!
}

type DeleteNotificationResponse {
    success: Boolean!
    errors: [ApiError!]!
}

type CreateUserResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type UpdateUserResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

type DeactivateUserResponse {
    success: Boolean!
    message: String
    errors: [ApiError!]!
}

type ReactivateUserResponse {
    success: Boolean!
    message: String
    user: User
    errors: [ApiError!]!
}

# ============================================================================
# CONNECTION TYPES
# ============================================================================

type PayoutRequestConnection {
    edges: [PayoutRequestEdge!]!
    pageInfo: PageInfo!
}

type PayoutRequestEdge {
    node: PayoutRequest!
    cursor: String!
}

type NotificationConnection {
    edges: [NotificationEdge!]!
    pageInfo: PageInfo!
    unreadCount: Int!
}

type NotificationEdge {
    node: Notification!
    cursor: String!
}

type PagedUserResult {
    content: [User!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

type PagedOrganizerProfileResult {
    content: [OrganizerProfile!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

type PagedPayoutRequestResult {
    content: [PayoutRequest!]!
    pageNumber: Int!
    pageSize: Int!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
    hasPrevious: Boolean!
}

# ============================================================================
# STATISTICS TYPES
# ============================================================================

type UserStats {
    totalUsers: Int!
    activeUsers: Int!
    customerCount: Int!
    organizerCount: Int!
    adminCount: Int!
    pendingOrganizerApplications: Int!
    newUsersThisMonth: Int!
}
```

---

## 6. Webhook Endpoints

### 6.1 pawaPay Webhooks (Booking Service)

```
POST /api/webhooks/pawapay/deposits
POST /api/webhooks/pawapay/payouts
```

#### Deposit Callback (Payment Confirmation)

```json
// Request Headers
{
    "Content-Type": "application/json",
    "pawapay-signature": "sha256=<hmac_signature>"
}

// Request Body
{
    "depositId": "dep_abc123",
    "status": "COMPLETED",
    "amount": "500.00",
    "currency": "ZMW",
    "correspondent": "MTN_MOMO_ZMB",
    "payer": {
        "type": "MSISDN",
        "address": {
            "value": "+260971234567"
        }
    },
    "customerTimestamp": "2026-03-01T10:30:00Z",
    "created": "2026-03-01T10:30:05Z",
    "receivedByRecipient": "2026-03-01T10:30:10Z",
    "correspondentIds": {
        "externalId": "TXN-20260301-ABC123"
    },
    "metadata": {
        "ticketId": "tkt_xyz789",
        "eventId": "evt_123"
    }
}

// Response: 200 OK (empty body)
```

#### Deposit Status Values

| Status | Description | Action |
|--------|-------------|--------|
| `ACCEPTED` | Payment initiated | Wait for completion |
| `COMPLETED` | Payment successful | Confirm ticket, credit escrow |
| `FAILED` | Payment failed | Release reservation |
| `REJECTED` | Payment rejected by provider | Release reservation |
| `EXPIRED` | Payment timed out | Release reservation |

#### Payout Callback (Refund/Organizer Payout)

```json
// Request Body
{
    "payoutId": "pay_xyz789",
    "status": "COMPLETED",
    "amount": "475.00",
    "currency": "ZMW",
    "correspondent": "MTN_MOMO_ZMB",
    "recipient": {
        "type": "MSISDN",
        "address": {
            "value": "+260971234567"
        }
    },
    "created": "2026-03-01T14:00:00Z",
    "correspondentIds": {
        "externalId": "REF-ABC123"
    },
    "metadata": {
        "type": "REFUND",
        "refundRequestId": "ref_123"
    }
}
```

### 6.2 Internal Webhooks (Service-to-Service)

```
# Catalog Service Internal
POST /api/internal/events/{eventId}/inventory/decrement
POST /api/internal/events/{eventId}/inventory/increment

# Booking Service Internal
POST /api/internal/escrow/create
POST /api/internal/escrow/{eventId}/credit
POST /api/internal/escrow/{eventId}/debit

# Identity Service Internal
POST /api/internal/notifications/send
POST /api/internal/users/{userId}/verify-exists
```

---

## 7. Subscriptions (Real-time)

### 7.1 Catalog Service Subscriptions

```graphql
type Subscription {
    """
    Real-time event updates for organizer dashboard
    """
    eventUpdates(organizerId: String!): EventUpdate!

    """
    Real-time ticket sales for event (organizer)
    """
    ticketSalesLive(eventId: String!): TicketSaleUpdate!

    """
    Event approval updates (admin)
    """
    approvalUpdates: ApprovalUpdate!
}

type EventUpdate {
    eventId: String!
    updateType: EventUpdateType!
    event: Event
    timestamp: DateTime!
}

enum EventUpdateType {
    TICKET_SOLD
    CAPACITY_CHANGED
    STATUS_CHANGED
    DETAILS_UPDATED
}

type TicketSaleUpdate {
    eventId: String!
    ticketsSold: Int!
    totalSold: Int!
    availableTickets: Int!
    revenue: BigDecimal!
    lastSaleAt: DateTime!
}

type ApprovalUpdate {
    eventId: String!
    status: ApprovalStatus!
    updatedAt: DateTime!
}
```

### 7.2 Booking Service Subscriptions

```graphql
type Subscription {
    """
    Payment status updates
    """
    paymentStatus(paymentIntentId: String!): PaymentStatusUpdate!

    """
    Ticket validation updates for scanner
    """
    ticketValidations(eventId: String!): TicketValidationUpdate!

    """
    Escrow balance updates (organizer)
    """
    escrowUpdates(escrowAccountId: String!): EscrowUpdate!
}

type PaymentStatusUpdate {
    paymentIntentId: String!
    status: PaymentStatus!
    message: String
    ticket: Ticket
    timestamp: DateTime!
}

type TicketValidationUpdate {
    eventId: String!
    ticketNumber: String!
    validationType: String!              # "VALIDATED", "USED"
    validatedAt: DateTime!
    totalValidated: Int!
    totalUsed: Int!
}

type EscrowUpdate {
    escrowAccountId: String!
    currentBalance: BigDecimal!
    lastTransaction: EscrowTransaction
    timestamp: DateTime!
}
```

### 7.3 Identity Service Subscriptions

```graphql
type Subscription {
    """
    New notifications for user
    """
    notificationReceived: Notification!

    """
    Payout status updates (organizer)
    """
    payoutUpdates(organizerId: String!): PayoutStatusUpdate!
}

type PayoutStatusUpdate {
    payoutRequestId: String!
    status: PayoutStatus!
    message: String
    timestamp: DateTime!
}
```

---

## 8. Error Handling

### 8.1 Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Input validation failed |
| `UNAUTHORIZED` | 401 | Authentication required |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource conflict (e.g., duplicate) |
| `BUSINESS_RULE_VIOLATION` | 422 | Business logic error |
| `RATE_LIMITED` | 429 | Too many requests |
| `INTERNAL_ERROR` | 500 | Server error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

### 8.2 Error Response Structure

```graphql
type ApiError {
    code: String!              # Machine-readable code
    message: String!           # Human-readable message
    field: String              # Field that caused error
    details: JSON              # Additional context
}

# Example error response
{
    "success": false,
    "message": "Validation failed",
    "data": null,
    "errors": [
        {
            "code": "VALIDATION_ERROR",
            "message": "Email format is invalid",
            "field": "email",
            "details": {
                "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
            }
        },
        {
            "code": "VALIDATION_ERROR",
            "message": "Password must be at least 8 characters",
            "field": "password",
            "details": {
                "minLength": 8,
                "actualLength": 5
            }
        }
    ]
}
```

### 8.3 Business Rule Errors

| Error Code | Description |
|------------|-------------|
| `EVENT_NOT_PUBLISHED` | Cannot purchase tickets for unpublished event |
| `TICKETS_SOLD_OUT` | No tickets available in category |
| `RESERVATION_EXPIRED` | Ticket reservation has expired |
| `PAYMENT_TIMEOUT` | Payment not completed in time |
| `REFUND_NOT_ELIGIBLE` | Ticket not eligible for refund |
| `REFUND_DEADLINE_PASSED` | Refund deadline has passed |
| `PAYOUT_NOT_ELIGIBLE` | Escrow not eligible for payout |
| `INSUFFICIENT_BALANCE` | Escrow balance too low |
| `EVENT_ALREADY_STARTED` | Event has already started |
| `DUPLICATE_PURCHASE` | Duplicate purchase attempt |

---

## 9. Security

### 9.1 Authentication

- **Type**: JWT Bearer tokens (OAuth 2.0)
- **Provider**: Keycloak
- **Token Lifetime**: 15 minutes (access), 7 days (refresh)

```
Authorization: Bearer <access_token>
```

### 9.2 Role-Based Access Control

| Role | Capabilities |
|------|--------------|
| `CUSTOMER` | Browse events, purchase tickets, request refunds |
| `ORGANIZER` | All customer + create/manage events, view sales |
| `SCANNER` | Validate tickets at events |
| `ADMIN` | Approve events/organizers, manage platform |
| `FINANCE` | Process payouts, view financial reports |
| `SUPER_ADMIN` | Full system access |

### 9.3 Field-Level Authorization

```graphql
# Some fields require specific roles
type User {
    id: ID!
    email: String!                       # CUSTOMER+
    phoneNumber: String @auth(roles: ["ADMIN", "SELF"])
    # ...
}

type BankAccount {
    accountNumber: String!               # Always masked for non-owners
    # Full number only visible to owner and FINANCE
}
```

---

## 10. Rate Limiting

### 10.1 Default Limits

| Endpoint Type | Rate Limit | Window |
|---------------|------------|--------|
| GraphQL Queries | 100 requests | 1 minute |
| GraphQL Mutations | 30 requests | 1 minute |
| Authentication | 5 requests | 1 minute |
| Webhooks | 1000 requests | 1 minute |

### 10.2 Rate Limit Headers

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1709283600
```

---

## Appendix A: Data Type Reference

### Scalar Types

| Scalar | Format | Example |
|--------|--------|---------|
| `ID` | UUID v4 | `"550e8400-e29b-41d4-a716-446655440000"` |
| `DateTime` | ISO 8601 | `"2026-03-15T18:00:00Z"` |
| `BigDecimal` | String | `"299.99"` |
| `JSON` | Object | `{"key": "value"}` |

### Phone Number Format

- Format: E.164 international format
- Example: `"+260971234567"` (Zambia)
- Validation: Must start with country code

### Currency

- Default: `"ZMW"` (Zambian Kwacha)
- Format: ISO 4217 currency code

---

## Appendix B: Validation Rules

### Common Validations

| Field | Rules |
|-------|-------|
| `email` | Valid email format, max 255 chars |
| `password` | Min 8 chars, 1 uppercase, 1 number, 1 special |
| `phoneNumber` | E.164 format |
| `title` | Min 5, max 200 chars |
| `description` | Min 50, max 5000 chars |
| `price` | >= 0, max 2 decimal places |
| `quantity` | >= 1, <= 10 (per order) |
| `totalCapacity` | >= 1 |

---

**Document Version History**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-01 | Team | Initial design |
| 2.0 | 2026-03-01 | Team | Added pawaPay, two-stage commission, double-entry |
