# Phase 1: Core Infrastructure

## Overview
Establish the foundational components, navigation system, and layout infrastructure for the admin dashboard.

---

## Task 1.1: Sidebar Navigation Component

### Description
Create a responsive sidebar navigation with role-based menu visibility.

### Backend Requirements
**None** - Uses existing permission system

### Frontend Implementation

#### File: `apps/admin/src/components/layout/Sidebar.tsx`

```tsx
'use client';

import { Box, Flex, Text, IconButton, ScrollArea } from '@radix-ui/themes';
import {
  LayoutDashboard, Calendar, ShoppingCart, Users,
  CreditCard, MapPin, Tags, ScanLine, BarChart3,
  Settings, Shield, ChevronLeft, ChevronRight
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { usePermissions, PermissionString } from '@/lib/hooks/usePermissions';

interface NavItem {
  label: string;
  href: string;
  icon: React.ElementType;
  permissions?: PermissionString[];
  children?: NavItem[];
}

const navigation: NavItem[] = [
  {
    label: 'Dashboard',
    href: '/dashboard',
    icon: LayoutDashboard
  },
  {
    label: 'Events',
    href: '/dashboard/events',
    icon: Calendar,
    permissions: ['event.read' as PermissionString],
    children: [
      { label: 'All Events', href: '/dashboard/events', icon: Calendar },
      { label: 'Create Event', href: '/dashboard/events/create', icon: Calendar, permissions: ['event.create' as PermissionString] },
    ]
  },
  {
    label: 'Bookings',
    href: '/dashboard/bookings',
    icon: ShoppingCart,
    permissions: ['booking.read' as PermissionString]
  },
  {
    label: 'Users',
    href: '/dashboard/users',
    icon: Users,
    permissions: ['user.read' as PermissionString]
  },
  {
    label: 'Finance',
    href: '/dashboard/finance',
    icon: CreditCard,
    permissions: ['finance.read' as PermissionString]
  },
  {
    label: 'Venues',
    href: '/dashboard/venues',
    icon: MapPin,
    permissions: ['venue.read' as PermissionString]
  },
  {
    label: 'Categories',
    href: '/dashboard/categories',
    icon: Tags,
    permissions: ['category.read' as PermissionString]
  },
  {
    label: 'Scanner',
    href: '/dashboard/scanner',
    icon: ScanLine,
    permissions: ['ticket.validate' as PermissionString]
  },
  {
    label: 'Reports',
    href: '/dashboard/reports',
    icon: BarChart3,
    permissions: ['report.read' as PermissionString]
  },
  {
    label: 'Settings',
    href: '/dashboard/settings',
    icon: Settings
  },
  {
    label: 'Audit Logs',
    href: '/dashboard/audit',
    icon: Shield,
    permissions: ['audit.read' as PermissionString]
  },
];
```

### UI/UX Specifications
- **Width**: 240px expanded, 64px collapsed
- **Behavior**: Collapsible with smooth animation
- **Mobile**: Full overlay with backdrop
- **Active State**: Highlighted background with accent color
- **Hover**: Subtle background change

### Acceptance Criteria
- [ ] Sidebar renders all navigation items
- [ ] Items are filtered based on user permissions
- [ ] Active route is visually highlighted
- [ ] Sidebar collapses on mobile devices
- [ ] Collapse button toggles sidebar state
- [ ] Smooth animation transitions

---

## Task 1.2: Dashboard Layout Component

### Description
Create the main dashboard layout with header, sidebar, and content area.

### File: `apps/admin/src/components/layout/DashboardLayout.tsx`

```tsx
'use client';

import { Box, Flex } from '@radix-ui/themes';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { useState } from 'react';

interface DashboardLayoutProps {
  children: React.ReactNode;
}

export function DashboardLayout({ children }: DashboardLayoutProps) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <Flex style={{ minHeight: '100vh' }}>
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
      />
      <Box
        style={{
          flex: 1,
          marginLeft: sidebarCollapsed ? '64px' : '240px',
          transition: 'margin-left 0.2s ease'
        }}
      >
        <Header />
        <Box p="5" style={{ minHeight: 'calc(100vh - 64px)' }}>
          {children}
        </Box>
      </Box>
    </Flex>
  );
}
```

### Acceptance Criteria
- [ ] Layout renders with sidebar and header
- [ ] Content area adjusts when sidebar collapses
- [ ] Responsive breakpoints work correctly
- [ ] Scroll behavior is correct for long content

---

## Task 1.3: Header Component

### Description
Create the dashboard header with user menu, notifications, and search.

### File: `apps/admin/src/components/layout/Header.tsx`

```tsx
'use client';

import { Box, Flex, Text, Avatar, DropdownMenu, Badge, IconButton, TextField } from '@radix-ui/themes';
import { Bell, Search, LogOut, User, Settings } from 'lucide-react';
import { useKeycloak } from '@pml.tickets/shared';

export function Header() {
  const { user, logout } = useKeycloak();

  return (
    <Box
      px="5"
      py="3"
      style={{
        borderBottom: '1px solid var(--gray-a5)',
        position: 'sticky',
        top: 0,
        backgroundColor: 'var(--color-background)',
        zIndex: 10
      }}
    >
      <Flex justify="between" align="center">
        {/* Search */}
        <Box style={{ width: '300px' }}>
          <TextField.Root placeholder="Search...">
            <TextField.Slot>
              <Search size={16} />
            </TextField.Slot>
          </TextField.Root>
        </Box>

        {/* Right side */}
        <Flex gap="4" align="center">
          {/* Notifications */}
          <Box style={{ position: 'relative' }}>
            <IconButton variant="ghost" size="2">
              <Bell size={18} />
            </IconButton>
            <Badge
              color="red"
              style={{
                position: 'absolute',
                top: -4,
                right: -4,
                minWidth: '18px',
                height: '18px'
              }}
            >
              3
            </Badge>
          </Box>

          {/* User menu */}
          <DropdownMenu.Root>
            <DropdownMenu.Trigger>
              <Flex align="center" gap="2" style={{ cursor: 'pointer' }}>
                <Avatar
                  size="2"
                  fallback={user?.email?.charAt(0).toUpperCase() || 'A'}
                  radius="full"
                />
                <Text size="2">{user?.email}</Text>
              </Flex>
            </DropdownMenu.Trigger>
            <DropdownMenu.Content>
              <DropdownMenu.Item>
                <User size={14} /> Profile
              </DropdownMenu.Item>
              <DropdownMenu.Item>
                <Settings size={14} /> Settings
              </DropdownMenu.Item>
              <DropdownMenu.Separator />
              <DropdownMenu.Item color="red" onClick={() => logout()}>
                <LogOut size={14} /> Logout
              </DropdownMenu.Item>
            </DropdownMenu.Content>
          </DropdownMenu.Root>
        </Flex>
      </Flex>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Header displays search input
- [ ] Notification bell shows unread count
- [ ] User dropdown menu works correctly
- [ ] Logout functionality works

---

## Task 1.4: Bento Grid System Component

### Description
Create reusable bento-style grid components for dashboard layouts.

### File: `apps/admin/src/components/ui/BentoGrid.tsx`

```tsx
'use client';

import { Grid, Box, Card, Flex, Text, Heading } from '@radix-ui/themes';
import { ReactNode } from 'react';

interface BentoGridProps {
  children: ReactNode;
}

export function BentoGrid({ children }: BentoGridProps) {
  return (
    <Grid
      columns={{ initial: "1", sm: "2", md: "3", lg: "4" }}
      gap="4"
      style={{ gridAutoRows: 'minmax(120px, auto)' }}
    >
      {children}
    </Grid>
  );
}

interface BentoCardProps {
  children: ReactNode;
  span?: { col?: number; row?: number };
  variant?: 'surface' | 'classic' | 'ghost';
}

export function BentoCard({ children, span, variant = 'surface' }: BentoCardProps) {
  return (
    <Card
      variant={variant}
      style={{
        gridColumn: span?.col ? `span ${span.col}` : undefined,
        gridRow: span?.row ? `span ${span.row}` : undefined,
      }}
    >
      {children}
    </Card>
  );
}

interface StatCardProps {
  title: string;
  value: string | number;
  change?: { value: number; trend: 'up' | 'down' };
  icon?: ReactNode;
  color?: 'blue' | 'green' | 'red' | 'orange' | 'purple';
}

export function StatCard({ title, value, change, icon, color = 'blue' }: StatCardProps) {
  return (
    <Card>
      <Flex direction="column" gap="2" p="4">
        <Flex justify="between" align="start">
          <Text size="2" color="gray">{title}</Text>
          {icon && (
            <Box
              p="2"
              style={{
                backgroundColor: `var(--${color}-a3)`,
                borderRadius: 'var(--radius-2)'
              }}
            >
              {icon}
            </Box>
          )}
        </Flex>
        <Heading size="6">{value}</Heading>
        {change && (
          <Flex align="center" gap="1">
            <Text
              size="1"
              color={change.trend === 'up' ? 'green' : 'red'}
            >
              {change.trend === 'up' ? '↑' : '↓'} {Math.abs(change.value)}%
            </Text>
            <Text size="1" color="gray">vs last period</Text>
          </Flex>
        )}
      </Flex>
    </Card>
  );
}
```

### Acceptance Criteria
- [ ] BentoGrid creates responsive grid layout
- [ ] BentoCard supports column/row spanning
- [ ] StatCard displays title, value, and trend
- [ ] Components are responsive across breakpoints

---

## Task 1.5: Data Table Component

### Description
Create a reusable data table with sorting, filtering, and pagination.

### File: `apps/admin/src/components/ui/DataTable.tsx`

```tsx
'use client';

import { Table, Flex, Text, Button, Select, TextField, Box } from '@radix-ui/themes';
import { ChevronLeft, ChevronRight, Search, ArrowUpDown } from 'lucide-react';
import { useState, useMemo } from 'react';

interface Column<T> {
  key: keyof T | string;
  header: string;
  sortable?: boolean;
  render?: (item: T) => ReactNode;
}

interface DataTableProps<T> {
  data: T[];
  columns: Column<T>[];
  pageSize?: number;
  searchable?: boolean;
  searchPlaceholder?: string;
  onRowClick?: (item: T) => void;
  loading?: boolean;
}

export function DataTable<T extends { id: string | number }>({
  data,
  columns,
  pageSize = 10,
  searchable = true,
  searchPlaceholder = 'Search...',
  onRowClick,
  loading,
}: DataTableProps<T>) {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');

  // Filter and sort logic
  const processedData = useMemo(() => {
    let result = [...data];

    // Search filter
    if (search) {
      result = result.filter(item =>
        columns.some(col => {
          const value = item[col.key as keyof T];
          return String(value).toLowerCase().includes(search.toLowerCase());
        })
      );
    }

    // Sort
    if (sortKey) {
      result.sort((a, b) => {
        const aVal = a[sortKey as keyof T];
        const bVal = b[sortKey as keyof T];
        const comparison = String(aVal).localeCompare(String(bVal));
        return sortDir === 'asc' ? comparison : -comparison;
      });
    }

    return result;
  }, [data, search, sortKey, sortDir, columns]);

  // Pagination
  const totalPages = Math.ceil(processedData.length / pageSize);
  const paginatedData = processedData.slice((page - 1) * pageSize, page * pageSize);

  return (
    <Box>
      {/* Search and filters */}
      {searchable && (
        <Flex mb="4" gap="3">
          <Box style={{ width: '300px' }}>
            <TextField.Root
              placeholder={searchPlaceholder}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            >
              <TextField.Slot>
                <Search size={16} />
              </TextField.Slot>
            </TextField.Root>
          </Box>
        </Flex>
      )}

      {/* Table */}
      <Table.Root variant="surface">
        <Table.Header>
          <Table.Row>
            {columns.map(col => (
              <Table.ColumnHeaderCell
                key={String(col.key)}
                style={{ cursor: col.sortable ? 'pointer' : 'default' }}
                onClick={() => {
                  if (col.sortable) {
                    if (sortKey === col.key) {
                      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
                    } else {
                      setSortKey(String(col.key));
                      setSortDir('asc');
                    }
                  }
                }}
              >
                <Flex align="center" gap="1">
                  {col.header}
                  {col.sortable && <ArrowUpDown size={14} />}
                </Flex>
              </Table.ColumnHeaderCell>
            ))}
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {paginatedData.map(item => (
            <Table.Row
              key={item.id}
              style={{ cursor: onRowClick ? 'pointer' : 'default' }}
              onClick={() => onRowClick?.(item)}
            >
              {columns.map(col => (
                <Table.Cell key={String(col.key)}>
                  {col.render
                    ? col.render(item)
                    : String(item[col.key as keyof T] ?? '')}
                </Table.Cell>
              ))}
            </Table.Row>
          ))}
        </Table.Body>
      </Table.Root>

      {/* Pagination */}
      <Flex mt="4" justify="between" align="center">
        <Text size="2" color="gray">
          Showing {(page - 1) * pageSize + 1} to {Math.min(page * pageSize, processedData.length)} of {processedData.length}
        </Text>
        <Flex gap="2" align="center">
          <Button
            variant="soft"
            disabled={page === 1}
            onClick={() => setPage(p => p - 1)}
          >
            <ChevronLeft size={16} />
          </Button>
          <Text size="2">Page {page} of {totalPages}</Text>
          <Button
            variant="soft"
            disabled={page === totalPages}
            onClick={() => setPage(p => p + 1)}
          >
            <ChevronRight size={16} />
          </Button>
        </Flex>
      </Flex>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Table renders data with columns
- [ ] Search filters data across columns
- [ ] Sorting works on sortable columns
- [ ] Pagination controls navigate pages
- [ ] Row click handler works when provided
- [ ] Loading state displays skeleton

---

## Task 1.6: Permission Guard Component

### Description
Create components to protect UI elements based on permissions.

### File: `apps/admin/src/components/auth/PermissionGuard.tsx`

```tsx
'use client';

import { ReactNode } from 'react';
import { usePermissions, PermissionString } from '@/lib/hooks/usePermissions';
import { Box, Flex, Text, Callout } from '@radix-ui/themes';
import { ShieldX } from 'lucide-react';

interface PermissionGuardProps {
  permission: PermissionString | PermissionString[];
  requireAll?: boolean;
  fallback?: ReactNode;
  children: ReactNode;
}

export function PermissionGuard({
  permission,
  requireAll = false,
  fallback,
  children
}: PermissionGuardProps) {
  const { hasPermission, hasAllPermissions, hasAnyPermission, isLoading, permissionsLoaded } = usePermissions();

  if (isLoading || !permissionsLoaded) {
    return null; // Or loading skeleton
  }

  const permissions = Array.isArray(permission) ? permission : [permission];
  const hasAccess = requireAll
    ? hasAllPermissions(permissions)
    : hasAnyPermission(permissions);

  if (!hasAccess) {
    return fallback ?? null;
  }

  return <>{children}</>;
}

interface AccessDeniedProps {
  message?: string;
}

export function AccessDenied({ message = "You don't have permission to access this resource." }: AccessDeniedProps) {
  return (
    <Flex
      direction="column"
      align="center"
      justify="center"
      style={{ minHeight: '400px' }}
    >
      <Callout.Root color="red" size="3">
        <Callout.Icon>
          <ShieldX size={24} />
        </Callout.Icon>
        <Callout.Text>
          <Text weight="bold">Access Denied</Text>
          <br />
          {message}
        </Callout.Text>
      </Callout.Root>
    </Flex>
  );
}
```

### Acceptance Criteria
- [ ] PermissionGuard hides content when permission is missing
- [ ] Supports single permission or array of permissions
- [ ] `requireAll` option works correctly
- [ ] Loading state is handled gracefully
- [ ] AccessDenied component displays friendly message

---

## Task 1.7: Update App Layout

### Description
Integrate the new layout components into the app.

### File: `apps/admin/src/app/dashboard/layout.tsx`

```tsx
import { DashboardLayout } from '@/components/layout/DashboardLayout';

export default function DashboardRootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <DashboardLayout>{children}</DashboardLayout>;
}
```

### Acceptance Criteria
- [ ] Dashboard routes use the new layout
- [ ] Sidebar and header render correctly
- [ ] Navigation between routes works
- [ ] Layout persists across route changes

---

## Dependencies

This phase has no dependencies and can start immediately.

## Estimated Time

- Task 1.1 (Sidebar): 4 hours
- Task 1.2 (Layout): 2 hours
- Task 1.3 (Header): 3 hours
- Task 1.4 (BentoGrid): 3 hours
- Task 1.5 (DataTable): 5 hours
- Task 1.6 (PermissionGuard): 2 hours
- Task 1.7 (App Layout): 1 hour

**Total: ~20 hours**
