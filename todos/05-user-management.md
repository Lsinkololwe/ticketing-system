# Phase 5: User & Access Management

## Overview
Implement user management, organizer verification, and admin user administration capabilities.

---

## Task 5.1: User Listing Page

### Description
Searchable user directory with filtering and user management actions.

### Backend Requirements

#### GraphQL Schema

```graphql
input UserFilterInput {
  role: [UserRole!]
  status: UserStatus
  searchQuery: String # Search by name, email, phone
  registeredFrom: DateTime
  registeredTo: DateTime
  isVerified: Boolean
}

type UserPage {
  content: [User!]!
  totalElements: Int!
  totalPages: Int!
}

type User {
  id: ID!
  email: String!
  name: String
  phone: String
  avatar: String
  roles: [UserRole!]!
  status: UserStatus!
  isEmailVerified: Boolean!
  isPhoneVerified: Boolean!
  createdAt: DateTime!
  lastLoginAt: DateTime
  bookingsCount: Int!
  totalSpent: Float!
}

enum UserStatus {
  ACTIVE
  INACTIVE
  SUSPENDED
  PENDING_VERIFICATION
}

extend type Query {
  usersPage(
    filter: UserFilterInput
    page: Int = 0
    size: Int = 20
  ): UserPage! @hasPermission(permission: "user.read")

  user(id: ID!): User @hasPermission(permission: "user.read")
}

extend type Mutation {
  suspendUser(id: ID!, reason: String!): User! @hasPermission(permission: "user.suspend")
  activateUser(id: ID!): User! @hasPermission(permission: "user.activate")
  updateUserRoles(id: ID!, roles: [UserRole!]!): User! @hasPermission(permission: "user.roles.manage")
  resetUserPassword(id: ID!): Boolean! @hasPermission(permission: "user.password.reset")
  deleteUser(id: ID!): Boolean! @hasPermission(permission: "user.delete")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/users/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Badge, Avatar, TextField,
  Select, Button, DropdownMenu, Dialog, TextArea
} from '@radix-ui/themes';
import {
  Search, MoreHorizontal, Eye, Shield, Ban, Key,
  Trash2, CheckCircle, Mail, Phone
} from 'lucide-react';
import Link from 'next/link';
import { DataTable } from '@/components/ui/DataTable';
import { PermissionGuard } from '@/components/auth/PermissionGuard';
import { USERS_PAGE_QUERY, SUSPEND_USER, ACTIVATE_USER } from '@/lib/graphql/queries/users';
import { formatDateTime, formatCurrency } from '@/lib/utils/format';

const statusColors: Record<string, 'gray' | 'green' | 'red' | 'orange'> = {
  ACTIVE: 'green',
  INACTIVE: 'gray',
  SUSPENDED: 'red',
  PENDING_VERIFICATION: 'orange',
};

const roleColors: Record<string, 'blue' | 'purple' | 'green' | 'orange'> = {
  CUSTOMER: 'blue',
  ORGANIZER: 'purple',
  ADMIN: 'orange',
  SCANNER: 'green',
};

export default function UsersPage() {
  const [filter, setFilter] = useState({
    searchQuery: '',
    role: null as string | null,
    status: null as string | null,
  });
  const [suspendDialog, setSuspendDialog] = useState<{ open: boolean; user: User | null }>({
    open: false,
    user: null,
  });
  const [suspendReason, setSuspendReason] = useState('');

  const { data, loading, refetch } = useQuery(USERS_PAGE_QUERY, {
    variables: {
      filter: {
        searchQuery: filter.searchQuery || null,
        role: filter.role ? [filter.role] : null,
        status: filter.status || null,
      },
    },
  });

  const [suspendUser] = useMutation(SUSPEND_USER, {
    onCompleted: () => {
      refetch();
      setSuspendDialog({ open: false, user: null });
      setSuspendReason('');
    },
  });

  const [activateUser] = useMutation(ACTIVATE_USER, {
    onCompleted: () => refetch(),
  });

  const users = data?.usersPage?.content ?? [];

  const columns = [
    {
      key: 'name',
      header: 'User',
      render: (user: User) => (
        <Flex align="center" gap="3">
          <Avatar
            size="2"
            src={user.avatar}
            fallback={user.name?.charAt(0) || user.email.charAt(0)}
            radius="full"
          />
          <Box>
            <Text weight="medium">{user.name || 'Unnamed User'}</Text>
            <Flex align="center" gap="1">
              <Text size="1" color="gray">{user.email}</Text>
              {user.isEmailVerified && (
                <CheckCircle size={10} style={{ color: 'var(--green-9)' }} />
              )}
            </Flex>
          </Box>
        </Flex>
      ),
    },
    {
      key: 'roles',
      header: 'Roles',
      render: (user: User) => (
        <Flex gap="1" wrap="wrap">
          {user.roles.map((role) => (
            <Badge key={role} color={roleColors[role]} variant="soft" size="1">
              {role}
            </Badge>
          ))}
        </Flex>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (user: User) => (
        <Badge color={statusColors[user.status]} variant="soft">
          {user.status}
        </Badge>
      ),
    },
    {
      key: 'activity',
      header: 'Activity',
      render: (user: User) => (
        <Box>
          <Text size="2">{user.bookingsCount} bookings</Text>
          <Text size="1" color="gray">{formatCurrency(user.totalSpent)} spent</Text>
        </Box>
      ),
    },
    {
      key: 'lastLogin',
      header: 'Last Login',
      render: (user: User) => (
        <Text size="2" color="gray">
          {user.lastLoginAt ? formatDateTime(user.lastLoginAt) : 'Never'}
        </Text>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (user: User) => (
        <DropdownMenu.Root>
          <DropdownMenu.Trigger>
            <Button variant="ghost" size="1">
              <MoreHorizontal size={16} />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content>
            <DropdownMenu.Item asChild>
              <Link href={`/dashboard/users/${user.id}`}>
                <Eye size={14} /> View Profile
              </Link>
            </DropdownMenu.Item>
            <PermissionGuard permission="user.roles.manage">
              <DropdownMenu.Item asChild>
                <Link href={`/dashboard/users/${user.id}/roles`}>
                  <Shield size={14} /> Manage Roles
                </Link>
              </DropdownMenu.Item>
            </PermissionGuard>
            <PermissionGuard permission="user.password.reset">
              <DropdownMenu.Item>
                <Key size={14} /> Reset Password
              </DropdownMenu.Item>
            </PermissionGuard>
            <DropdownMenu.Separator />
            <PermissionGuard permission="user.suspend">
              {user.status === 'ACTIVE' ? (
                <DropdownMenu.Item
                  color="orange"
                  onClick={() => setSuspendDialog({ open: true, user })}
                >
                  <Ban size={14} /> Suspend User
                </DropdownMenu.Item>
              ) : user.status === 'SUSPENDED' && (
                <DropdownMenu.Item
                  color="green"
                  onClick={() => activateUser({ variables: { id: user.id } })}
                >
                  <CheckCircle size={14} /> Activate User
                </DropdownMenu.Item>
              )}
            </PermissionGuard>
            <PermissionGuard permission="user.delete">
              <DropdownMenu.Item color="red">
                <Trash2 size={14} /> Delete User
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
          <Heading size="6">Users</Heading>
          <Text color="gray" size="2">Manage system users</Text>
        </Box>
      </Flex>

      {/* Filters */}
      <Flex gap="3" mb="4">
        <TextField.Root
          placeholder="Search by name, email, phone..."
          value={filter.searchQuery}
          onChange={(e) => setFilter(f => ({ ...f, searchQuery: e.target.value }))}
          style={{ width: '300px' }}
        >
          <TextField.Slot>
            <Search size={16} />
          </TextField.Slot>
        </TextField.Root>
        <Select.Root
          value={filter.role || 'all'}
          onValueChange={(v) => setFilter(f => ({ ...f, role: v === 'all' ? null : v }))}
        >
          <Select.Trigger placeholder="Role" />
          <Select.Content>
            <Select.Item value="all">All Roles</Select.Item>
            <Select.Item value="CUSTOMER">Customer</Select.Item>
            <Select.Item value="ORGANIZER">Organizer</Select.Item>
            <Select.Item value="ADMIN">Admin</Select.Item>
            <Select.Item value="SCANNER">Scanner</Select.Item>
          </Select.Content>
        </Select.Root>
        <Select.Root
          value={filter.status || 'all'}
          onValueChange={(v) => setFilter(f => ({ ...f, status: v === 'all' ? null : v }))}
        >
          <Select.Trigger placeholder="Status" />
          <Select.Content>
            <Select.Item value="all">All Status</Select.Item>
            <Select.Item value="ACTIVE">Active</Select.Item>
            <Select.Item value="SUSPENDED">Suspended</Select.Item>
            <Select.Item value="INACTIVE">Inactive</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      <DataTable
        data={users}
        columns={columns}
        loading={loading}
        onRowClick={(user) => window.location.href = `/dashboard/users/${user.id}`}
      />

      {/* Suspend Dialog */}
      <Dialog.Root open={suspendDialog.open} onOpenChange={(open) => setSuspendDialog({ open, user: open ? suspendDialog.user : null })}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Suspend User</Dialog.Title>
          <Dialog.Description>
            Suspending {suspendDialog.user?.name || suspendDialog.user?.email} will prevent them from accessing the platform.
          </Dialog.Description>
          <Box mt="3">
            <Text size="2" weight="medium" mb="2">Reason for suspension</Text>
            <TextArea
              placeholder="Enter the reason for suspending this user..."
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
            />
          </Box>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              color="red"
              onClick={() => suspendUser({
                variables: { id: suspendDialog.user?.id, reason: suspendReason }
              })}
              disabled={!suspendReason}
            >
              Suspend User
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] User listing with pagination
- [ ] Search by name, email, phone
- [ ] Filter by role and status
- [ ] User avatar and verification badges
- [ ] Role badges display
- [ ] Activity stats (bookings, spent)
- [ ] Suspend/Activate user actions
- [ ] Permission-based action visibility

---

## Task 5.2: User Profile Page

### Description
Detailed user profile view with activity history and management options.

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/users/[id]/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useParams, useRouter } from 'next/navigation';
import {
  Box, Flex, Heading, Text, Card, Avatar, Badge, Tabs,
  Button, Separator
} from '@radix-ui/themes';
import {
  ArrowLeft, Mail, Phone, Calendar, MapPin, Shield,
  ShoppingCart, Ticket, Clock, Edit
} from 'lucide-react';
import Link from 'next/link';
import { USER_DETAIL_QUERY } from '@/lib/graphql/queries/users';
import { formatDateTime, formatCurrency } from '@/lib/utils/format';
import { UserBookingsTab } from '@/components/users/UserBookingsTab';
import { UserTicketsTab } from '@/components/users/UserTicketsTab';
import { UserActivityTab } from '@/components/users/UserActivityTab';
import { PermissionGuard } from '@/components/auth/PermissionGuard';

export default function UserProfilePage() {
  const params = useParams();
  const router = useRouter();
  const userId = params.id as string;

  const { data, loading } = useQuery(USER_DETAIL_QUERY, {
    variables: { id: userId },
  });

  if (loading) return <UserProfileSkeleton />;
  if (!data?.user) return <UserNotFound />;

  const user = data.user;

  return (
    <Box>
      {/* Header */}
      <Flex align="center" gap="3" mb="5">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft size={16} />
        </Button>
        <Heading size="6">User Profile</Heading>
      </Flex>

      <Flex gap="5">
        {/* Profile Card */}
        <Box style={{ width: '320px' }}>
          <Card>
            <Box p="5">
              <Flex direction="column" align="center" gap="3">
                <Avatar
                  size="6"
                  src={user.avatar}
                  fallback={user.name?.charAt(0) || user.email.charAt(0)}
                  radius="full"
                />
                <Box style={{ textAlign: 'center' }}>
                  <Heading size="4">{user.name || 'Unnamed User'}</Heading>
                  <Badge
                    color={statusColors[user.status]}
                    variant="soft"
                    mt="1"
                  >
                    {user.status}
                  </Badge>
                </Box>
              </Flex>

              <Separator my="4" />

              {/* Contact Info */}
              <Flex direction="column" gap="3">
                <Flex align="center" gap="2">
                  <Mail size={14} />
                  <Text size="2">{user.email}</Text>
                  {user.isEmailVerified && (
                    <Badge color="green" variant="soft" size="1">Verified</Badge>
                  )}
                </Flex>
                {user.phone && (
                  <Flex align="center" gap="2">
                    <Phone size={14} />
                    <Text size="2">{user.phone}</Text>
                    {user.isPhoneVerified && (
                      <Badge color="green" variant="soft" size="1">Verified</Badge>
                    )}
                  </Flex>
                )}
                <Flex align="center" gap="2">
                  <Calendar size={14} />
                  <Text size="2" color="gray">
                    Joined {formatDateTime(user.createdAt)}
                  </Text>
                </Flex>
                {user.lastLoginAt && (
                  <Flex align="center" gap="2">
                    <Clock size={14} />
                    <Text size="2" color="gray">
                      Last login {formatDateTime(user.lastLoginAt)}
                    </Text>
                  </Flex>
                )}
              </Flex>

              <Separator my="4" />

              {/* Roles */}
              <Box>
                <Text size="2" weight="medium" mb="2">Roles</Text>
                <Flex gap="1" wrap="wrap">
                  {user.roles.map((role) => (
                    <Badge key={role} color={roleColors[role]} variant="soft">
                      {role}
                    </Badge>
                  ))}
                </Flex>
              </Box>

              <Separator my="4" />

              {/* Quick Stats */}
              <Flex direction="column" gap="2">
                <Flex justify="between">
                  <Text size="2" color="gray">Total Bookings</Text>
                  <Text size="2" weight="medium">{user.bookingsCount}</Text>
                </Flex>
                <Flex justify="between">
                  <Text size="2" color="gray">Total Spent</Text>
                  <Text size="2" weight="medium">{formatCurrency(user.totalSpent)}</Text>
                </Flex>
                <Flex justify="between">
                  <Text size="2" color="gray">Active Tickets</Text>
                  <Text size="2" weight="medium">{user.activeTicketsCount}</Text>
                </Flex>
              </Flex>

              <Separator my="4" />

              {/* Actions */}
              <Flex direction="column" gap="2">
                <PermissionGuard permission="user.roles.manage">
                  <Button variant="soft" asChild>
                    <Link href={`/dashboard/users/${user.id}/roles`}>
                      <Shield size={14} /> Manage Roles
                    </Link>
                  </Button>
                </PermissionGuard>
                <PermissionGuard permission="user.update">
                  <Button variant="soft" asChild>
                    <Link href={`/dashboard/users/${user.id}/edit`}>
                      <Edit size={14} /> Edit Profile
                    </Link>
                  </Button>
                </PermissionGuard>
              </Flex>
            </Box>
          </Card>
        </Box>

        {/* Main Content */}
        <Box style={{ flex: 1 }}>
          <Tabs.Root defaultValue="bookings">
            <Tabs.List>
              <Tabs.Trigger value="bookings">
                <ShoppingCart size={14} /> Bookings
              </Tabs.Trigger>
              <Tabs.Trigger value="tickets">
                <Ticket size={14} /> Tickets
              </Tabs.Trigger>
              <Tabs.Trigger value="activity">
                <Clock size={14} /> Activity
              </Tabs.Trigger>
            </Tabs.List>

            <Box pt="4">
              <Tabs.Content value="bookings">
                <UserBookingsTab userId={user.id} />
              </Tabs.Content>
              <Tabs.Content value="tickets">
                <UserTicketsTab userId={user.id} />
              </Tabs.Content>
              <Tabs.Content value="activity">
                <UserActivityTab userId={user.id} />
              </Tabs.Content>
            </Box>
          </Tabs.Root>
        </Box>
      </Flex>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] User profile sidebar with avatar and info
- [ ] Contact information with verification badges
- [ ] Roles display
- [ ] Quick stats (bookings, spent, tickets)
- [ ] Bookings tab with user's booking history
- [ ] Tickets tab with user's tickets
- [ ] Activity tab with login/action history
- [ ] Edit and role management buttons

---

## Task 5.3: Organizer Management

### Description
Manage event organizers including verification and payout settings.

### Backend Requirements

```graphql
type Organizer {
  id: ID!
  user: User!
  businessName: String!
  businessType: BusinessType!
  taxId: String
  website: String
  description: String
  logo: String
  isVerified: Boolean!
  verificationStatus: VerificationStatus!
  verificationDocuments: [Document!]!
  bankAccount: BankAccount
  events: [Event!]!
  totalRevenue: Float!
  pendingPayout: Float!
  createdAt: DateTime!
}

enum VerificationStatus {
  PENDING
  UNDER_REVIEW
  APPROVED
  REJECTED
  REQUIRES_INFO
}

enum BusinessType {
  INDIVIDUAL
  COMPANY
  NON_PROFIT
  GOVERNMENT
}

extend type Query {
  organizersPage(
    verificationStatus: VerificationStatus
    searchQuery: String
    page: Int = 0
    size: Int = 20
  ): OrganizerPage! @hasPermission(permission: "organizer.read")
}

extend type Mutation {
  approveOrganizer(id: ID!): Organizer! @hasPermission(permission: "organizer.verify")
  rejectOrganizer(id: ID!, reason: String!): Organizer! @hasPermission(permission: "organizer.verify")
  requestOrganizerInfo(id: ID!, message: String!): Organizer! @hasPermission(permission: "organizer.verify")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/users/organizers/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Badge, Avatar, Button,
  Select, TextField, Dialog, TextArea, Table
} from '@radix-ui/themes';
import {
  Search, Building, CheckCircle, XCircle, AlertCircle,
  FileText, Eye, ExternalLink
} from 'lucide-react';
import Link from 'next/link';
import { ORGANIZERS_PAGE_QUERY, APPROVE_ORGANIZER, REJECT_ORGANIZER } from '@/lib/graphql/queries/organizers';
import { formatCurrency, formatDate } from '@/lib/utils/format';

const verificationStatusColors: Record<string, 'gray' | 'blue' | 'green' | 'red' | 'orange'> = {
  PENDING: 'orange',
  UNDER_REVIEW: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  REQUIRES_INFO: 'gray',
};

export default function OrganizersPage() {
  const [statusFilter, setStatusFilter] = useState<string>('PENDING');
  const [searchQuery, setSearchQuery] = useState('');
  const [rejectDialog, setRejectDialog] = useState<{ open: boolean; organizer: Organizer | null }>({
    open: false,
    organizer: null,
  });
  const [rejectReason, setRejectReason] = useState('');

  const { data, loading, refetch } = useQuery(ORGANIZERS_PAGE_QUERY, {
    variables: {
      verificationStatus: statusFilter || null,
      searchQuery: searchQuery || null,
    },
  });

  const [approveOrganizer] = useMutation(APPROVE_ORGANIZER, {
    onCompleted: () => refetch(),
  });

  const [rejectOrganizer] = useMutation(REJECT_ORGANIZER, {
    onCompleted: () => {
      refetch();
      setRejectDialog({ open: false, organizer: null });
      setRejectReason('');
    },
  });

  const organizers = data?.organizersPage?.content ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Organizers</Heading>
          <Text color="gray" size="2">Manage event organizers and verifications</Text>
        </Box>
      </Flex>

      {/* Stats Cards */}
      <Flex gap="4" mb="5">
        <Card style={{ flex: 1 }}>
          <Flex p="3" align="center" gap="3">
            <Box p="2" style={{ backgroundColor: 'var(--orange-a3)', borderRadius: 'var(--radius-2)' }}>
              <AlertCircle size={18} style={{ color: 'var(--orange-11)' }} />
            </Box>
            <Box>
              <Text size="1" color="gray">Pending Review</Text>
              <Text size="4" weight="bold">12</Text>
            </Box>
          </Flex>
        </Card>
        <Card style={{ flex: 1 }}>
          <Flex p="3" align="center" gap="3">
            <Box p="2" style={{ backgroundColor: 'var(--green-a3)', borderRadius: 'var(--radius-2)' }}>
              <CheckCircle size={18} style={{ color: 'var(--green-11)' }} />
            </Box>
            <Box>
              <Text size="1" color="gray">Verified</Text>
              <Text size="4" weight="bold">156</Text>
            </Box>
          </Flex>
        </Card>
        <Card style={{ flex: 1 }}>
          <Flex p="3" align="center" gap="3">
            <Box p="2" style={{ backgroundColor: 'var(--blue-a3)', borderRadius: 'var(--radius-2)' }}>
              <Building size={18} style={{ color: 'var(--blue-11)' }} />
            </Box>
            <Box>
              <Text size="1" color="gray">Total Organizers</Text>
              <Text size="4" weight="bold">178</Text>
            </Box>
          </Flex>
        </Card>
      </Flex>

      {/* Filters */}
      <Flex gap="3" mb="4">
        <TextField.Root
          placeholder="Search organizers..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{ width: '300px' }}
        >
          <TextField.Slot>
            <Search size={16} />
          </TextField.Slot>
        </TextField.Root>
        <Select.Root value={statusFilter} onValueChange={setStatusFilter}>
          <Select.Trigger placeholder="Verification Status" />
          <Select.Content>
            <Select.Item value="all">All Status</Select.Item>
            <Select.Item value="PENDING">Pending</Select.Item>
            <Select.Item value="UNDER_REVIEW">Under Review</Select.Item>
            <Select.Item value="APPROVED">Approved</Select.Item>
            <Select.Item value="REJECTED">Rejected</Select.Item>
            <Select.Item value="REQUIRES_INFO">Requires Info</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      {/* Organizers Table */}
      <Card>
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Organizer</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Business Type</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Events</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Revenue</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {organizers.map((organizer) => (
              <Table.Row key={organizer.id}>
                <Table.Cell>
                  <Flex align="center" gap="3">
                    <Avatar
                      size="2"
                      src={organizer.logo}
                      fallback={organizer.businessName.charAt(0)}
                    />
                    <Box>
                      <Text weight="medium">{organizer.businessName}</Text>
                      <Text size="1" color="gray">{organizer.user.email}</Text>
                    </Box>
                  </Flex>
                </Table.Cell>
                <Table.Cell>
                  <Badge variant="soft">{organizer.businessType}</Badge>
                </Table.Cell>
                <Table.Cell>{organizer.events.length}</Table.Cell>
                <Table.Cell>{formatCurrency(organizer.totalRevenue)}</Table.Cell>
                <Table.Cell>
                  <Badge
                    color={verificationStatusColors[organizer.verificationStatus]}
                    variant="soft"
                  >
                    {organizer.verificationStatus}
                  </Badge>
                </Table.Cell>
                <Table.Cell>
                  <Flex gap="2">
                    <Button size="1" variant="ghost" asChild>
                      <Link href={`/dashboard/users/organizers/${organizer.id}`}>
                        <Eye size={14} />
                      </Link>
                    </Button>
                    {organizer.verificationStatus === 'PENDING' && (
                      <>
                        <Button
                          size="1"
                          color="green"
                          onClick={() => approveOrganizer({ variables: { id: organizer.id } })}
                        >
                          Approve
                        </Button>
                        <Button
                          size="1"
                          variant="soft"
                          color="red"
                          onClick={() => setRejectDialog({ open: true, organizer })}
                        >
                          Reject
                        </Button>
                      </>
                    )}
                  </Flex>
                </Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Card>

      {/* Reject Dialog */}
      <Dialog.Root open={rejectDialog.open} onOpenChange={(open) => setRejectDialog({ open, organizer: open ? rejectDialog.organizer : null })}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Reject Organizer</Dialog.Title>
          <Dialog.Description>
            Rejecting {rejectDialog.organizer?.businessName} will prevent them from creating events.
          </Dialog.Description>
          <Box mt="3">
            <Text size="2" weight="medium" mb="2">Reason for rejection</Text>
            <TextArea
              placeholder="Enter the reason for rejection..."
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
          </Box>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              color="red"
              onClick={() => rejectOrganizer({
                variables: { id: rejectDialog.organizer?.id, reason: rejectReason }
              })}
              disabled={!rejectReason}
            >
              Reject
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Organizer listing with search and filter
- [ ] Verification status stats cards
- [ ] Business type and events count
- [ ] Revenue display
- [ ] Approve/Reject actions for pending
- [ ] Rejection reason dialog
- [ ] Link to organizer detail page
- [ ] Verification document review

---

## Task 5.4: Admin User Management

### Description
Manage admin users and their permissions (SUPER_ADMIN only).

### Backend Requirements

```graphql
extend type Query {
  adminUsers: [User!]! @hasPermission(permission: "admin.users.read")
}

extend type Mutation {
  createAdminUser(input: CreateAdminUserInput!): User!
    @hasPermission(permission: "admin.users.create")

  updateAdminPermissions(userId: ID!, permissions: [String!]!): User!
    @hasPermission(permission: "admin.users.permissions")

  revokeAdminAccess(userId: ID!): User!
    @hasPermission(permission: "admin.users.revoke")
}

input CreateAdminUserInput {
  email: String!
  name: String!
  role: AdminRole!
  permissions: [String!]
}

enum AdminRole {
  ADMIN
  SUPER_ADMIN
  FINANCE
  SCANNER
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/users/admins/page.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Badge, Avatar, Button,
  Dialog, TextField, Select, Table, Checkbox
} from '@radix-ui/themes';
import { Plus, Shield, Trash2, Edit } from 'lucide-react';
import { ADMIN_USERS_QUERY, CREATE_ADMIN_USER, REVOKE_ADMIN } from '@/lib/graphql/queries/admins';
import { formatDateTime } from '@/lib/utils/format';
import { PERMISSIONS_LIST } from '@/lib/constants/permissions';

export default function AdminUsersPage() {
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [newAdmin, setNewAdmin] = useState({
    email: '',
    name: '',
    role: 'ADMIN',
    permissions: [] as string[],
  });

  const { data, loading, refetch } = useQuery(ADMIN_USERS_QUERY);

  const [createAdminUser, { loading: creating }] = useMutation(CREATE_ADMIN_USER, {
    onCompleted: () => {
      refetch();
      setCreateDialogOpen(false);
      setNewAdmin({ email: '', name: '', role: 'ADMIN', permissions: [] });
    },
  });

  const [revokeAdminAccess] = useMutation(REVOKE_ADMIN, {
    onCompleted: () => refetch(),
  });

  const admins = data?.adminUsers ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Admin Users</Heading>
          <Text color="gray" size="2">Manage administrator accounts</Text>
        </Box>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus size={16} /> Add Admin
        </Button>
      </Flex>

      <Card>
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Admin</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Role</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Permissions</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Last Login</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {admins.map((admin) => (
              <Table.Row key={admin.id}>
                <Table.Cell>
                  <Flex align="center" gap="3">
                    <Avatar
                      size="2"
                      src={admin.avatar}
                      fallback={admin.name?.charAt(0) || admin.email.charAt(0)}
                      radius="full"
                    />
                    <Box>
                      <Text weight="medium">{admin.name}</Text>
                      <Text size="1" color="gray">{admin.email}</Text>
                    </Box>
                  </Flex>
                </Table.Cell>
                <Table.Cell>
                  <Badge
                    color={admin.roles.includes('SUPER_ADMIN') ? 'red' : 'orange'}
                    variant="soft"
                  >
                    {admin.roles.includes('SUPER_ADMIN') ? 'Super Admin' : 'Admin'}
                  </Badge>
                </Table.Cell>
                <Table.Cell>
                  <Text size="2">{admin.permissions?.length || 0} permissions</Text>
                </Table.Cell>
                <Table.Cell>
                  <Text size="2" color="gray">
                    {admin.lastLoginAt ? formatDateTime(admin.lastLoginAt) : 'Never'}
                  </Text>
                </Table.Cell>
                <Table.Cell>
                  <Flex gap="2">
                    <Button size="1" variant="ghost">
                      <Edit size={14} />
                    </Button>
                    <Button
                      size="1"
                      variant="ghost"
                      color="red"
                      onClick={() => {
                        if (confirm('Revoke admin access?')) {
                          revokeAdminAccess({ variables: { userId: admin.id } });
                        }
                      }}
                    >
                      <Trash2 size={14} />
                    </Button>
                  </Flex>
                </Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Card>

      {/* Create Admin Dialog */}
      <Dialog.Root open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <Dialog.Content style={{ maxWidth: 500 }}>
          <Dialog.Title>Add Admin User</Dialog.Title>
          <Flex direction="column" gap="4" mt="4">
            <Box>
              <Text size="2" weight="medium" mb="1">Email</Text>
              <TextField.Root
                type="email"
                placeholder="admin@example.com"
                value={newAdmin.email}
                onChange={(e) => setNewAdmin(a => ({ ...a, email: e.target.value }))}
              />
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="1">Name</Text>
              <TextField.Root
                placeholder="Full Name"
                value={newAdmin.name}
                onChange={(e) => setNewAdmin(a => ({ ...a, name: e.target.value }))}
              />
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="1">Role</Text>
              <Select.Root
                value={newAdmin.role}
                onValueChange={(v) => setNewAdmin(a => ({ ...a, role: v }))}
              >
                <Select.Trigger />
                <Select.Content>
                  <Select.Item value="ADMIN">Admin</Select.Item>
                  <Select.Item value="FINANCE">Finance</Select.Item>
                  <Select.Item value="SCANNER">Scanner</Select.Item>
                  <Select.Item value="SUPER_ADMIN">Super Admin</Select.Item>
                </Select.Content>
              </Select.Root>
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="2">Permissions</Text>
              <Flex direction="column" gap="2" style={{ maxHeight: '200px', overflow: 'auto' }}>
                {PERMISSIONS_LIST.map((perm) => (
                  <Text as="label" key={perm.value} size="2">
                    <Flex align="center" gap="2">
                      <Checkbox
                        checked={newAdmin.permissions.includes(perm.value)}
                        onCheckedChange={(checked) => {
                          setNewAdmin(a => ({
                            ...a,
                            permissions: checked
                              ? [...a.permissions, perm.value]
                              : a.permissions.filter(p => p !== perm.value)
                          }));
                        }}
                      />
                      {perm.label}
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
              onClick={() => createAdminUser({ variables: { input: newAdmin } })}
              disabled={creating || !newAdmin.email || !newAdmin.name}
            >
              Create Admin
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] List all admin users
- [ ] Create new admin user dialog
- [ ] Role selection (Admin, Finance, Scanner, Super Admin)
- [ ] Permission checkboxes
- [ ] Edit admin permissions
- [ ] Revoke admin access
- [ ] Last login display

---

## Dependencies

- Phase 1: Core Infrastructure (DataTable, PermissionGuard)

## Estimated Time

- Task 5.1 (User Listing): 5 hours
- Task 5.2 (User Profile): 5 hours
- Task 5.3 (Organizer Management): 6 hours
- Task 5.4 (Admin Management): 5 hours

**Total: ~21 hours**
