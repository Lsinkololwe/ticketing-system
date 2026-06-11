# Admin Portal - Production-Grade Design Document

## Executive Summary

This document defines the complete architecture, design patterns, and implementation guidelines for the Ticketing System Admin Portal. The admin portal is a Next.js 16 application using React 19, Apollo Client 4, and Radix UI Themes, designed for platform administrators to manage organizers, events, tickets, payments, and users.

---

## Table of Contents

1. [Design System](#1-design-system)
2. [Architecture Overview](#2-architecture-overview)
3. [Navigation Structure](#3-navigation-structure)
4. [Domain Modules](#4-domain-modules)
5. [Component Architecture](#5-component-architecture)
6. [Data Flow Patterns](#6-data-flow-patterns)
7. [OWASP Compliance](#7-owasp-compliance)
8. [Implementation Guidelines](#8-implementation-guidelines)

---

## 1. Design System

### 1.1 Visual Identity

| Property | Value | Notes |
|----------|-------|-------|
| **Style** | Dark Mode (OLED) | High contrast, eye-friendly |
| **Primary Color** | `#7C3AED` | Purple - trust, creativity |
| **Secondary Color** | `#A78BFA` | Light purple - accents |
| **CTA Color** | `#F97316` | Orange - action buttons |
| **Background** | `#0F0F0F` | Deep black for OLED |
| **Surface** | `#1A1A1A` | Cards, panels |
| **Text Primary** | `#FAFAFA` | White text |
| **Text Secondary** | `#A1A1AA` | Muted text |
| **Success** | `#22C55E` | Approved states |
| **Warning** | `#F59E0B` | Pending states |
| **Error** | `#EF4444` | Rejected/failed states |

### 1.2 Typography

| Element | Font | Weight | Size |
|---------|------|--------|------|
| **Headings** | Fira Code | 600-700 | 24-32px |
| **Body** | Fira Sans | 400-500 | 14-16px |
| **Data/Tables** | Fira Code | 400 | 13-14px |
| **Labels** | Fira Sans | 500 | 12-13px |

### 1.3 Spacing Scale

```
4px  - xs (tight spacing)
8px  - sm (element padding)
16px - md (section spacing)
24px - lg (card padding)
32px - xl (section gaps)
48px - 2xl (major sections)
```

### 1.4 Component Patterns

**Cards**: Surface background with subtle border
```css
background: #1A1A1A;
border: 1px solid #2A2A2A;
border-radius: 8px;
```

**Buttons (Primary)**:
```css
background: #7C3AED;
color: white;
border-radius: 6px;
transition: background 200ms;
```

**Tables**: Radix UI Table with custom styling
```css
header-background: #1F1F1F;
row-hover: #252525;
border-color: #2A2A2A;
```

---

## 2. Architecture Overview

### 2.1 Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Framework | Next.js (App Router) | 16.x |
| UI Runtime | React | 19.x |
| UI Components | Radix UI Themes | 3.2.x |
| Data Fetching | Apollo Client | 4.x |
| Validation | Zod | 4.x |
| Forms | React Hook Form + Zod | Latest |
| Icons | Iconoir React | 7.x |
| Styling | Tailwind CSS + Radix | 3.4.x |
| Auth | Keycloak JS | 26.x |

### 2.2 Directory Structure

```
apps/admin/src/
├── app/                          # Next.js App Router
│   ├── (dashboard)/              # Protected dashboard layout group
│   │   ├── layout.tsx            # Dashboard shell (sidebar + header)
│   │   ├── page.tsx              # Dashboard home (overview)
│   │   ├── organizers/           # Organizer management
│   │   │   ├── page.tsx          # Organizer list
│   │   │   ├── [id]/             # Organizer detail
│   │   │   │   ├── page.tsx      # Profile view/edit
│   │   │   │   └── review/page.tsx # Review workflow
│   │   │   └── applications/     # Pending applications queue
│   │   │       └── page.tsx
│   │   ├── events/               # Event management
│   │   │   ├── page.tsx          # Event list
│   │   │   ├── [id]/page.tsx     # Event detail
│   │   │   └── pending/page.tsx  # Approval queue
│   │   ├── tickets/              # Ticket management
│   │   │   ├── page.tsx          # Ticket search/list
│   │   │   └── [id]/page.tsx     # Ticket detail
│   │   ├── payments/             # Financial management
│   │   │   ├── page.tsx          # Payment overview
│   │   │   ├── escrow/page.tsx   # Escrow accounts
│   │   │   ├── payouts/page.tsx  # Payout requests
│   │   │   └── refunds/page.tsx  # Refund requests
│   │   ├── users/                # User management
│   │   │   ├── page.tsx          # User list
│   │   │   └── [id]/page.tsx     # User detail
│   │   └── settings/             # Platform settings
│   │       ├── page.tsx          # General settings
│   │       ├── categories/page.tsx
│   │       ├── locations/page.tsx
│   │       └── permissions/page.tsx
│   ├── login/page.tsx            # Login page
│   ├── layout.tsx                # Root layout
│   └── global.css                # Global styles
├── components/                   # Admin-specific components
│   ├── dashboard/                # Dashboard components
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   ├── StatsCard.tsx
│   │   └── ActivityFeed.tsx
│   ├── data-table/               # Reusable data table
│   │   ├── DataTable.tsx
│   │   ├── DataTablePagination.tsx
│   │   ├── DataTableFilters.tsx
│   │   └── DataTableColumns.tsx
│   ├── forms/                    # Form components
│   │   ├── FormField.tsx
│   │   ├── FormSelect.tsx
│   │   └── FormTextarea.tsx
│   └── dialogs/                  # Modal dialogs
│       ├── ConfirmDialog.tsx
│       ├── ReviewDialog.tsx
│       └── DetailDialog.tsx
├── hooks/                        # Admin-specific hooks
│   └── use-token-sync.ts
├── lib/                          # Utilities
│   ├── auth-server.ts
│   └── utils.ts
└── types/                        # Admin types
    └── generated/graphql.ts      # Generated from codegen
```

### 2.3 Shared Library Structure

```
libs/shared/src/
├── api/graphql/                  # GraphQL definitions
│   ├── client.ts                 # Apollo Client setup
│   ├── organizers/               # Organizer domain
│   │   ├── organizers.consumer.queryDefinitions.ts
│   │   ├── organizers.admin.queryDefinitions.ts
│   │   ├── organizers.mutationDefinitions.ts
│   │   ├── organizers.admin-hooks.ts
│   │   └── index.ts
│   ├── events/                   # Event domain
│   ├── tickets/                  # Ticket domain
│   ├── payments/                 # Payment domain
│   ├── users/                    # User domain
│   └── permissions/              # Permission domain
├── components/                   # Shared components
│   ├── auth/                     # Auth components
│   │   └── PermissionGate.tsx
│   └── ui/                       # UI components
│       └── SectionError.tsx
├── auth/                         # Auth utilities
└── types/graphql/                # Generated types
    └── schema-types.ts
```

---

## 3. Navigation Structure

### 3.1 Sidebar Navigation

```
┌─────────────────────────────────────┐
│  [Logo] Ticketing Admin             │
├─────────────────────────────────────┤
│                                     │
│  MAIN                               │
│  ├── Dashboard                      │
│  ├── Organizers          [Badge: 5] │
│  │   ├── All Organizers             │
│  │   └── Applications               │
│  ├── Events              [Badge: 3] │
│  │   ├── All Events                 │
│  │   └── Pending Approval           │
│  └── Tickets                        │
│                                     │
│  FINANCIAL                          │
│  ├── Payments Overview              │
│  ├── Escrow Accounts                │
│  ├── Payout Requests     [Badge: 2] │
│  └── Refund Requests                │
│                                     │
│  USERS                              │
│  └── User Management                │
│                                     │
│  SETTINGS                           │
│  ├── Categories                     │
│  ├── Locations                      │
│  └── Permissions                    │
│                                     │
├─────────────────────────────────────┤
│  [Avatar] Admin User                │
│  admin@ticketing.com                │
└─────────────────────────────────────┘
```

### 3.2 Navigation Component Structure

```tsx
// Sidebar navigation items configuration
const navigationItems = [
  {
    section: 'Main',
    items: [
      { name: 'Dashboard', href: '/', icon: 'HomeSimple' },
      {
        name: 'Organizers',
        icon: 'Building',
        badge: pendingOrganizers,
        children: [
          { name: 'All Organizers', href: '/organizers' },
          { name: 'Applications', href: '/organizers/applications' },
        ],
      },
      {
        name: 'Events',
        icon: 'Calendar',
        badge: pendingEvents,
        children: [
          { name: 'All Events', href: '/events' },
          { name: 'Pending Approval', href: '/events/pending' },
        ],
      },
      { name: 'Tickets', href: '/tickets', icon: 'Ticket' },
    ],
  },
  {
    section: 'Financial',
    items: [
      { name: 'Payments', href: '/payments', icon: 'CreditCard' },
      { name: 'Escrow', href: '/payments/escrow', icon: 'Bank' },
      { name: 'Payouts', href: '/payments/payouts', icon: 'SendDollars', badge: pendingPayouts },
      { name: 'Refunds', href: '/payments/refunds', icon: 'RefreshDouble' },
    ],
  },
  {
    section: 'Users',
    items: [
      { name: 'Users', href: '/users', icon: 'Group' },
    ],
  },
  {
    section: 'Settings',
    items: [
      { name: 'Categories', href: '/settings/categories', icon: 'Folder' },
      { name: 'Locations', href: '/settings/locations', icon: 'MapPin' },
      { name: 'Permissions', href: '/settings/permissions', icon: 'Lock' },
    ],
  },
];
```

---

## 4. Domain Modules

### 4.1 Organizers Module

#### Data Model (from Identity Service)

```graphql
type Organization {
  id: ID!
  userId: String!
  user: User

  # Business Information
  companyName: String
  companyDescription: String
  tagline: String
  website: String
  socialLinks: SocialLinks

  # Business Registration (KYB)
  taxId: String              # TPIN in Zambia
  businessRegistrationNumber: String  # PACRA number
  businessType: String
  yearEstablished: Int

  # Contact Information
  businessPhone: String
  businessEmail: String
  businessAddress: String
  city: String
  province: String
  country: String

  # Status & Workflow
  status: OrganizerStatus!   # DRAFT | PENDING_REVIEW | APPROVED | REJECTED | SUSPENDED
  statusReason: String
  adminNotes: String

  # Verification Flags
  verified: Boolean
  documentsVerified: Boolean
  bankVerified: Boolean

  # Timestamps
  submittedAt: DateTime
  reviewedAt: DateTime
  approvedAt: DateTime
  rejectedAt: DateTime
  createdAt: DateTime!
  updatedAt: DateTime!
}

enum OrganizerStatus {
  DRAFT              # Just applied, filling profile
  PENDING_DOCUMENTS  # Profile complete, awaiting documents
  PENDING_REVIEW     # Documents submitted, awaiting admin review
  CHANGES_REQUESTED  # Admin requested modifications
  APPROVED           # Approved - can create events
  REJECTED           # Application rejected
  SUSPENDED          # Temporarily suspended
}
```

#### UI Pages

**4.1.1 Organizers List Page** (`/organizers`)

```
┌─────────────────────────────────────────────────────────────────────┐
│ Organizers                                            [+ Add] [⬇ Export] │
├─────────────────────────────────────────────────────────────────────┤
│ Filters: [Status ▼] [City ▼] [Search...            ] [Clear Filters]│
├─────────────────────────────────────────────────────────────────────┤
│ Tabs: All (156) | Pending (5) | Approved (140) | Suspended (8) | Rejected (3)│
├─────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────┐ │
│ │ □ │ Company          │ Contact        │ Status    │ Submitted  │ Actions │
│ ├───┼──────────────────┼────────────────┼───────────┼────────────┼─────────┤
│ │ □ │ ABC Events Ltd   │ john@abc.com   │ 🟡 Pending│ 2 days ago │ [View]  │
│ │ □ │ XYZ Productions  │ info@xyz.co.zm │ 🟢 Approved│ 1 week ago│ [View]  │
│ │ □ │ Concert Masters  │ admin@cm.zm    │ 🔴 Rejected│ 3 days ago│ [View]  │
│ └─────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────┤
│ Showing 1-10 of 156                     [◀] 1 2 3 ... 16 [▶]        │
└─────────────────────────────────────────────────────────────────────┘
```

**4.1.2 Organizer Detail Page** (`/organizers/[id]`)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ← Back to Organizers                                                │
├─────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────┐  ABC Events Ltd                                 │
│ │                 │  ──────────────────────────────                 │
│ │    [Company     │  Status: 🟡 PENDING_REVIEW                      │
│ │     Logo]       │  Submitted: May 15, 2024 at 10:30 AM            │
│ │                 │                                                 │
│ └─────────────────┘  [✓ Approve] [✗ Reject] [⟳ Request Changes]     │
├─────────────────────────────────────────────────────────────────────┤
│ Tabs: [Overview] [Documents] [Events (12)] [Payouts] [Activity]     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ BUSINESS INFORMATION                                                │
│ ┌────────────────────────────────┬────────────────────────────────┐ │
│ │ Company Name: ABC Events Ltd   │ Business Type: Limited Company │ │
│ │ TPIN: 1234567890               │ PACRA: 123456789               │ │
│ │ Year Established: 2020         │ Website: www.abcevents.com     │ │
│ └────────────────────────────────┴────────────────────────────────┘ │
│                                                                     │
│ CONTACT INFORMATION                                                 │
│ ┌────────────────────────────────┬────────────────────────────────┐ │
│ │ Email: info@abcevents.com      │ Phone: +260 97 123 4567        │ │
│ │ Address: 123 Cairo Road        │ City: Lusaka, Lusaka Province  │ │
│ └────────────────────────────────┴────────────────────────────────┘ │
│                                                                     │
│ VERIFICATION STATUS                                                 │
│ ┌────────────────────────────────────────────────────────────────┐ │
│ │ ☑ Profile Complete  ☑ Documents Uploaded  ☐ Bank Verified      │ │
│ └────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│ ADMIN NOTES                                                         │
│ ┌────────────────────────────────────────────────────────────────┐ │
│ │ [Textarea for admin notes...]                                   │ │
│ │                                                         [Save]  │ │
│ └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

**4.1.3 Application Review Dialog**

```
┌─────────────────────────────────────────────────────────────────────┐
│ Review Application: ABC Events Ltd                            [×]   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ Decision:                                                           │
│ ○ Approve - Allow organizer to create events                        │
│ ○ Reject - Deny application with reason                             │
│ ○ Request Changes - Ask for modifications                           │
│                                                                     │
│ Commission Rate (if approving):                                     │
│ [10%        ▼]                                                      │
│                                                                     │
│ Payout Schedule (if approving):                                     │
│ [Weekly     ▼]                                                      │
│                                                                     │
│ Comments (required for reject/changes):                             │
│ ┌────────────────────────────────────────────────────────────────┐ │
│ │                                                                 │ │
│ │                                                                 │ │
│ └────────────────────────────────────────────────────────────────┘ │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                               [Cancel]  [Submit Decision]           │
└─────────────────────────────────────────────────────────────────────┘
```

#### GraphQL Operations

```typescript
// Queries
GET_ORGANIZER_APPLICATIONS_ADMIN     // Offset pagination for tables
GET_PENDING_ORGANIZER_APPLICATIONS   // Quick filter for pending
GET_ORGANIZER_PROFILE                // Single profile detail
GET_ORGANIZER_STATISTICS             // Performance metrics
GET_VERIFICATION_DOCUMENTS           // Documents for review

// Mutations
APPROVE_ORGANIZER                    // Approve application
REJECT_ORGANIZER                     // Reject with reason
REQUEST_ORGANIZER_CHANGES            // Request modifications
SUSPEND_ORGANIZER                    // Suspend approved organizer
REACTIVATE_ORGANIZER                 // Reactivate suspended
VERIFY_ORGANIZER_BUSINESS            // Mark business verified
VERIFY_ORGANIZER_DOCUMENTS           // Mark documents verified
VERIFY_ORGANIZER_BANK_ACCOUNT        // Mark bank verified
```

### 4.2 Events Module

#### Data Model (from Catalog Service)

```graphql
type Event {
  id: ID!
  title: String!
  description: String!
  category: EventCategory
  eventDateTime: DateTime!
  endDateTime: DateTime!
  location: Location

  # Organizer (from Identity Service)
  organizerId: String!
  organizerName: String

  # Status & Workflow
  status: EventStatus!   # DRAFT | PENDING_REVIEW | APPROVED | PUBLISHED | CANCELLED | COMPLETED

  # Ticket Information
  ticketTiers: [TicketTier!]!
  totalCapacity: Int!
  ticketsSold: Int
  availableTickets: Int

  # Financial
  grossRevenue: BigDecimal
  netRevenue: BigDecimal

  # Timestamps
  publishedAt: DateTime
  approvedAt: DateTime
  createdAt: DateTime!
  updatedAt: DateTime!
}

enum EventStatus {
  DRAFT              # Initial state, not visible
  PENDING_REVIEW     # Submitted for admin review
  CHANGES_REQUESTED  # Admin requested changes
  APPROVED           # Approved but not published
  PUBLISHED          # Live and visible
  CANCELLED          # Event cancelled
  COMPLETED          # Event ended
}
```

#### UI Pages

**4.2.1 Events List Page** (`/events`)

Similar to organizers list with:
- Filter by status, category, organizer, date range
- Quick stats: Total events, Published, Pending, Revenue
- Bulk actions for approval workflow

**4.2.2 Event Approval Queue** (`/events/pending`)

Focused view for events awaiting review with:
- Preview of event details
- Quick approve/reject actions
- Days since submission indicator

### 4.3 Payments Module

#### UI Pages

**4.3.1 Payments Overview** (`/payments`)

```
┌─────────────────────────────────────────────────────────────────────┐
│ Financial Overview                                                  │
├─────────────────────────────────────────────────────────────────────┤
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ │
│ │ Total Escrow │ │ Pending      │ │ Processed    │ │ Active       │ │
│ │   Balance    │ │   Payouts    │ │   This Month │ │   Events     │ │
│ │  K 1,234,567 │ │   K 45,678   │ │   K 234,567  │ │     156      │ │
│ └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘ │
├─────────────────────────────────────────────────────────────────────┤
│ Quick Actions:                                                      │
│ [View Escrow Accounts] [Process Payouts] [Review Refunds]           │
├─────────────────────────────────────────────────────────────────────┤
│ Recent Activity                                                     │
│ ┌───────────────────────────────────────────────────────────────┐   │
│ │ • Payout K 15,000 to ABC Events - Processing                  │   │
│ │ • Refund K 500 for TKT-12345 - Completed                      │   │
│ │ • Escrow deposit K 2,500 for Event #456 - Received            │   │
│ └───────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

**4.3.2 Payout Requests** (`/payments/payouts`)

- List of payout requests with status filters
- Approve/Reject workflow
- Bulk processing capabilities
- Recovery queue for failed payouts

**4.3.3 Refund Requests** (`/payments/refunds`)

- List of refund requests
- Event cancellation refund batches
- User-initiated refund review

---

## 5. Component Architecture

### 5.1 Core Components

#### 5.1.1 DataTable Component

A reusable, server-side paginated data table built on Radix UI Table.

```tsx
interface DataTableProps<T> {
  // Data source
  data: T[];
  columns: ColumnDef<T>[];
  isLoading?: boolean;

  // Pagination
  pagination: {
    currentPage: number;
    pageSize: number;
    totalCount: number;
    onPageChange: (page: number) => void;
    onPageSizeChange: (size: number) => void;
  };

  // Sorting
  sorting?: {
    sortBy?: string;
    sortOrder?: 'asc' | 'desc';
    onSortChange: (column: string, order: 'asc' | 'desc') => void;
  };

  // Selection
  selection?: {
    selectedIds: Set<string>;
    onSelectionChange: (ids: Set<string>) => void;
  };

  // Row actions
  rowActions?: (row: T) => ReactNode;

  // Empty state
  emptyState?: ReactNode;
}
```

#### 5.1.2 FilterBar Component

```tsx
interface FilterBarProps {
  filters: FilterConfig[];
  values: Record<string, any>;
  onChange: (values: Record<string, any>) => void;
  onClear: () => void;
}

interface FilterConfig {
  key: string;
  label: string;
  type: 'select' | 'search' | 'date' | 'dateRange';
  options?: { value: string; label: string }[];
  placeholder?: string;
}
```

#### 5.1.3 StatusBadge Component

```tsx
interface StatusBadgeProps {
  status: string;
  variant: 'organizer' | 'event' | 'ticket' | 'payment';
}

// Status color mapping
const statusColors = {
  organizer: {
    DRAFT: 'gray',
    PENDING_REVIEW: 'yellow',
    CHANGES_REQUESTED: 'orange',
    APPROVED: 'green',
    REJECTED: 'red',
    SUSPENDED: 'purple',
  },
  // ... other variants
};
```

#### 5.1.4 ReviewDialog Component

Generic dialog for approval workflows:

```tsx
interface ReviewDialogProps {
  title: string;
  entity: any;
  isOpen: boolean;
  onClose: () => void;
  onApprove: (data: ApproveData) => Promise<void>;
  onReject: (data: RejectData) => Promise<void>;
  onRequestChanges?: (data: ChangesData) => Promise<void>;
  approveFields?: ReactNode;  // Additional fields for approve action
}
```

### 5.2 Page Templates

#### 5.2.1 List Page Template

```tsx
// Standard list page structure
export default function ListPage() {
  // 1. State management
  const [filters, setFilters] = useState(defaultFilters);
  const [pagination, setPagination] = useState({ page: 1, size: 10 });

  // 2. Data fetching (Apollo hook)
  const { data, loading, error, refetch } = useQuery(LIST_QUERY, {
    variables: { filter: filters, pagination },
  });

  // 3. Render
  return (
    <div className="space-y-6">
      {/* Header with title and actions */}
      <PageHeader
        title="Organizers"
        actions={<Button>Add Organizer</Button>}
      />

      {/* Filters */}
      <FilterBar filters={filterConfig} values={filters} onChange={setFilters} />

      {/* Status tabs (optional) */}
      <StatusTabs counts={data?.statusCounts} activeStatus={filters.status} />

      {/* Data table */}
      <DataTable
        data={data?.items ?? []}
        columns={columns}
        isLoading={loading}
        pagination={{
          currentPage: pagination.page,
          pageSize: pagination.size,
          totalCount: data?.totalCount ?? 0,
          onPageChange: (page) => setPagination(p => ({ ...p, page })),
          onPageSizeChange: (size) => setPagination({ page: 1, size }),
        }}
      />
    </div>
  );
}
```

#### 5.2.2 Detail Page Template

```tsx
// Standard detail page structure
export default function DetailPage({ params }: { params: { id: string } }) {
  // 1. Data fetching
  const { data, loading, error } = useQuery(GET_DETAIL_QUERY, {
    variables: { id: params.id },
  });

  // 2. Mutations
  const [updateEntity] = useMutation(UPDATE_MUTATION);

  // 3. Render
  return (
    <div className="space-y-6">
      {/* Back navigation */}
      <BackButton href="/organizers" />

      {/* Header with entity info and actions */}
      <EntityHeader
        entity={data?.entity}
        actions={<ActionButtons entity={data?.entity} />}
      />

      {/* Tabbed content */}
      <Tabs.Root defaultValue="overview">
        <Tabs.List>
          <Tabs.Trigger value="overview">Overview</Tabs.Trigger>
          <Tabs.Trigger value="documents">Documents</Tabs.Trigger>
          <Tabs.Trigger value="activity">Activity</Tabs.Trigger>
        </Tabs.List>

        <Tabs.Content value="overview">
          <OverviewTab entity={data?.entity} />
        </Tabs.Content>
        {/* ... other tabs */}
      </Tabs.Root>
    </div>
  );
}
```

---

## 6. Data Flow Patterns

### 6.1 GraphQL Integration

#### Apollo Client Setup

```typescript
// libs/shared/src/api/graphql/client.ts
import { ApolloClient, InMemoryCache, createHttpLink } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';

const httpLink = createHttpLink({
  uri: process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql',
});

const authLink = setContext(async (_, { headers }) => {
  // Get token from Keycloak
  const token = await getAccessToken();
  return {
    headers: {
      ...headers,
      authorization: token ? `Bearer ${token}` : '',
    },
  };
});

export const apolloClient = new ApolloClient({
  link: authLink.concat(httpLink),
  cache: new InMemoryCache({
    typePolicies: {
      Query: {
        fields: {
          // Pagination merge policies
          organizerApplicationsOffsetPagination: {
            keyArgs: ['filter'],
            merge(existing, incoming) {
              return incoming; // Replace on pagination change
            },
          },
        },
      },
    },
  }),
});
```

#### Custom Hooks Pattern

```typescript
// libs/shared/src/api/graphql/organizers/organizers.admin-hooks.ts
import { useQuery, useMutation, QueryHookOptions } from '@apollo/client';
import {
  GET_ORGANIZER_APPLICATIONS_ADMIN,
  APPROVE_ORGANIZER,
  REJECT_ORGANIZER,
} from './organizers.admin.queryDefinitions';
import type {
  Organization,
  OrganizerStatus,
  OffsetPaginationInput,
} from '../../../types/graphql/schema-types';

// Query hook with proper typing
export function useOrganizerApplicationsAdmin(
  options?: {
    status?: OrganizerStatus;
    pagination?: OffsetPaginationInput;
  }
) {
  return useQuery(GET_ORGANIZER_APPLICATIONS_ADMIN, {
    variables: {
      status: options?.status,
      pagination: options?.pagination ?? { page: 1, size: 10 },
    },
    fetchPolicy: 'cache-and-network',
  });
}

// Mutation hook with optimistic updates
export function useApproveOrganizer() {
  return useMutation(APPROVE_ORGANIZER, {
    update(cache, { data }) {
      // Update cache after approval
      if (data?.approveOrganizer) {
        cache.modify({
          fields: {
            organizerApplicationsOffsetPagination(existing = {}) {
              // Move from pending to approved in cache
              return existing;
            },
          },
        });
      }
    },
  });
}
```

### 6.2 State Management

#### Server State (Apollo Client)
- Primary data fetching via Apollo Client
- Automatic caching and cache invalidation
- Optimistic UI updates for mutations

#### Local State (React useState/useReducer)
- UI state (modals, filters, selected items)
- Form state (managed by React Hook Form)
- Navigation state (Next.js router)

#### URL State (Query Parameters)
- Pagination state
- Filter state
- Tab state (for shareable links)

```typescript
// Example: URL-synced filters
function useUrlFilters() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const filters = {
    status: searchParams.get('status') ?? undefined,
    search: searchParams.get('search') ?? undefined,
    page: parseInt(searchParams.get('page') ?? '1'),
  };

  const setFilters = (newFilters: Partial<typeof filters>) => {
    const params = new URLSearchParams(searchParams);
    Object.entries(newFilters).forEach(([key, value]) => {
      if (value !== undefined) {
        params.set(key, String(value));
      } else {
        params.delete(key);
      }
    });
    router.push(`?${params.toString()}`);
  };

  return [filters, setFilters] as const;
}
```

### 6.3 Form Handling

#### React Hook Form + Zod Integration

```typescript
// Schema definition
import { z } from 'zod';

export const approveOrganizerSchema = z.object({
  profileId: z.string().uuid(),
  commissionRate: z.number().min(0).max(100).default(10),
  payoutSchedule: z.enum(['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY']).default('WEEKLY'),
  reviewNotes: z.string().optional(),
});

export type ApproveOrganizerFormData = z.infer<typeof approveOrganizerSchema>;

// Form component
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

function ApproveOrganizerForm({ profileId, onSubmit }: Props) {
  const form = useForm<ApproveOrganizerFormData>({
    resolver: zodResolver(approveOrganizerSchema),
    defaultValues: {
      profileId,
      commissionRate: 10,
      payoutSchedule: 'WEEKLY',
    },
  });

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      {/* Form fields */}
    </form>
  );
}
```

---

## 7. OWASP Compliance

### 7.1 A01:2021 - Broken Access Control

**Implementation:**

1. **JWT-Based Authorization**
   - All API requests include JWT token from Keycloak
   - Backend extracts user ID from token (never from request body)
   - Role-based access control enforced at GraphQL resolver level

2. **Frontend Permission Gate**
   ```tsx
   // Wrap sensitive components
   <PermissionGate requires="ADMIN">
     <ApproveButton onClick={handleApprove} />
   </PermissionGate>
   ```

3. **Route Protection**
   ```typescript
   // middleware.ts
   export function middleware(request: NextRequest) {
     const token = request.cookies.get('access_token');
     if (!token && !request.nextUrl.pathname.startsWith('/login')) {
       return NextResponse.redirect(new URL('/login', request.url));
     }
   }
   ```

### 7.2 A02:2021 - Cryptographic Failures

**Implementation:**

1. **HTTPS Only** - All API calls over TLS
2. **Secure Token Storage** - HttpOnly cookies for refresh tokens
3. **No PII in Logs** - Filter sensitive data from client-side logging

### 7.3 A03:2021 - Injection

**Implementation:**

1. **GraphQL Validation** - Schema-based input validation
2. **Parameterized Queries** - Apollo Client handles parameterization
3. **Zod Validation** - Client-side validation before submission

```typescript
// Input validation with Zod
const searchSchema = z.object({
  query: z.string().max(100).regex(/^[a-zA-Z0-9\s\-]+$/),
  status: z.enum(['DRAFT', 'PENDING_REVIEW', 'APPROVED']).optional(),
});
```

### 7.4 A04:2021 - Insecure Design

**Implementation:**

1. **Principle of Least Privilege**
   - Admin users have specific permissions
   - GraphQL operations tagged with `@tag(name: "admin")`

2. **Defense in Depth**
   - Client-side permission checks
   - Server-side authorization
   - Database-level access control

### 7.5 A05:2021 - Security Misconfiguration

**Implementation:**

1. **Environment Variables**
   ```typescript
   // lib/env-validation.ts
   import { z } from 'zod';

   const envSchema = z.object({
     NEXT_PUBLIC_GRAPHQL_URL: z.string().url(),
     NEXT_PUBLIC_KEYCLOAK_URL: z.string().url(),
     KEYCLOAK_CLIENT_SECRET: z.string().min(32),
   });

   export const env = envSchema.parse(process.env);
   ```

2. **Security Headers** (in Next.js config)
   ```typescript
   const securityHeaders = [
     { key: 'X-Frame-Options', value: 'DENY' },
     { key: 'X-Content-Type-Options', value: 'nosniff' },
     { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
   ];
   ```

### 7.6 A07:2021 - Identification and Authentication Failures

**Implementation:**

1. **Keycloak Integration**
   - Industry-standard OAuth 2.0 / OIDC
   - Session management with token rotation
   - Backchannel logout support

2. **Token Refresh**
   ```typescript
   // Automatic token refresh before expiry
   async function getAccessToken() {
     const keycloak = getKeycloakInstance();
     if (keycloak.isTokenExpired(30)) {
       await keycloak.updateToken(30);
     }
     return keycloak.token;
   }
   ```

### 7.7 A09:2021 - Security Logging and Monitoring

**Implementation:**

1. **Structured Logging**
   ```typescript
   // lib/logger.ts
   export function logAdminAction(action: string, details: object) {
     console.log(JSON.stringify({
       timestamp: new Date().toISOString(),
       type: 'ADMIN_ACTION',
       action,
       ...details,
       // Never log tokens or passwords
     }));
   }
   ```

2. **Audit Trail**
   - All admin actions logged
   - Reviewer ID recorded with decisions
   - Activity feed on entity detail pages

---

## 8. Implementation Guidelines

### 8.1 Code Organization

1. **One component per file** - Max 300 lines
2. **Colocate related files** - Tests, types, styles together
3. **Use barrel exports** - index.ts for public API
4. **Type everything** - No `any` types

### 8.2 Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Components | PascalCase | `OrganizerList.tsx` |
| Hooks | camelCase with `use` prefix | `useOrganizerList.ts` |
| GraphQL Operations | SCREAMING_SNAKE_CASE | `GET_ORGANIZER_PROFILE` |
| Types | PascalCase | `Organization` |
| Variables | camelCase | `organizerList` |

### 8.3 GraphQL Operation Naming

```typescript
// Queries: GET_[ENTITY]_[MODIFIER]
GET_ORGANIZER_PROFILE
GET_ORGANIZER_APPLICATIONS_ADMIN
GET_PENDING_ORGANIZER_APPLICATIONS

// Mutations: [ACTION]_[ENTITY]
APPROVE_ORGANIZER
REJECT_ORGANIZER
UPDATE_ORGANIZER_PROFILE
```

### 8.4 Error Handling

```tsx
// Standard error handling pattern
function OrganizerDetail({ id }: Props) {
  const { data, loading, error } = useOrganization(id);

  if (loading) return <DetailSkeleton />;
  if (error) return <ErrorState error={error} onRetry={() => refetch()} />;
  if (!data?.organizerProfile) return <NotFoundState />;

  return <OrganizerDetailContent profile={data.organizerProfile} />;
}
```

### 8.5 Testing Strategy

| Type | Tool | Coverage Target |
|------|------|-----------------|
| Unit | Jest + React Testing Library | 80% |
| Integration | Playwright | Critical paths |
| E2E | Playwright | Happy paths |

### 8.6 Performance Guidelines

1. **Pagination** - Always use server-side pagination
2. **Lazy Loading** - Dynamic imports for heavy components
3. **Memoization** - useMemo/useCallback for expensive computations
4. **Image Optimization** - Next.js Image component

### 8.7 Accessibility (WCAG 2.1 AA)

1. **Color Contrast** - 4.5:1 minimum for text
2. **Keyboard Navigation** - All actions keyboard accessible
3. **Focus Management** - Visible focus indicators
4. **Screen Reader** - Proper ARIA labels
5. **Reduced Motion** - Respect `prefers-reduced-motion`

---

## Appendix A: Implementation Checklist

### Phase 1: Foundation (Week 1)
- [ ] Set up dashboard layout (Sidebar + Header)
- [ ] Implement DataTable component
- [ ] Create FilterBar component
- [ ] Set up authentication flow
- [ ] Configure Apollo Client

### Phase 2: Organizers Module (Week 2)
- [ ] Organizers list page with pagination
- [ ] Organizer detail page
- [ ] Approval workflow (approve/reject/request changes)
- [ ] Document verification tab
- [ ] Statistics integration

### Phase 3: Events Module (Week 3)
- [ ] Events list page
- [ ] Event detail page
- [ ] Event approval workflow
- [ ] Event statistics

### Phase 4: Payments Module (Week 4)
- [ ] Payments overview dashboard
- [ ] Escrow accounts management
- [ ] Payout request workflow
- [ ] Refund request workflow

### Phase 5: Users & Settings (Week 5)
- [ ] User list and detail pages
- [ ] Categories management
- [ ] Locations management
- [ ] Permissions configuration

### Phase 6: Polish & Testing (Week 6)
- [ ] End-to-end testing
- [ ] Performance optimization
- [ ] Accessibility audit
- [ ] Security review

---

## Appendix B: GraphQL Schema Reference

See:
- `backend/identity-service/src/main/resources/graphql/schema.graphqls`
- `backend/catalog-service/src/main/resources/graphql/schema.graphqls`
- `backend/booking-service/src/main/resources/graphql/schema.graphqls`

---

*Document Version: 1.0*
*Last Updated: May 18, 2026*
*Author: Claude Code*
