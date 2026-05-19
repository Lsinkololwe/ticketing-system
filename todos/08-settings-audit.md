# Phase 8: Settings & Audit Logs

## Overview
Implement system settings, admin profile management, security settings, and comprehensive audit logging.

---

## Task 8.1: Settings Overview Page

### Description
Central settings hub with organized sections.

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/settings/page.tsx`

```tsx
'use client';

import {
  Box, Flex, Heading, Text, Card, Grid
} from '@radix-ui/themes';
import {
  User, Shield, Bell, Palette, Globe, CreditCard,
  ArrowRight
} from 'lucide-react';
import Link from 'next/link';
import { PermissionGuard } from '@/components/auth/PermissionGuard';

interface SettingsSection {
  title: string;
  description: string;
  icon: React.ElementType;
  href: string;
  permission?: string;
}

const settingsSections: SettingsSection[] = [
  {
    title: 'Profile',
    description: 'Manage your account information and preferences',
    icon: User,
    href: '/dashboard/settings/profile',
  },
  {
    title: 'Security',
    description: 'Password, two-factor authentication, and sessions',
    icon: Shield,
    href: '/dashboard/settings/security',
  },
  {
    title: 'Notifications',
    description: 'Email and push notification preferences',
    icon: Bell,
    href: '/dashboard/settings/notifications',
  },
  {
    title: 'Appearance',
    description: 'Theme, language, and display settings',
    icon: Palette,
    href: '/dashboard/settings/appearance',
  },
  {
    title: 'System Settings',
    description: 'Platform-wide configuration options',
    icon: Globe,
    href: '/dashboard/settings/system',
    permission: 'settings.system',
  },
  {
    title: 'Payment Settings',
    description: 'Payment gateways and fee configuration',
    icon: CreditCard,
    href: '/dashboard/settings/payments',
    permission: 'settings.payments',
  },
];

export default function SettingsPage() {
  return (
    <Box>
      <Box mb="5">
        <Heading size="6">Settings</Heading>
        <Text color="gray" size="2">Manage your account and system preferences</Text>
      </Box>

      <Grid columns="2" gap="4">
        {settingsSections.map((section) => {
          const content = (
            <Link key={section.href} href={section.href}>
              <Card style={{ height: '100%', cursor: 'pointer' }}>
                <Flex p="4" justify="between" align="center">
                  <Flex gap="4" align="center">
                    <Box
                      p="3"
                      style={{
                        backgroundColor: 'var(--gray-a3)',
                        borderRadius: 'var(--radius-3)',
                      }}
                    >
                      <section.icon size={20} />
                    </Box>
                    <Box>
                      <Text size="3" weight="medium">{section.title}</Text>
                      <Text size="2" color="gray">{section.description}</Text>
                    </Box>
                  </Flex>
                  <ArrowRight size={16} style={{ color: 'var(--gray-9)' }} />
                </Flex>
              </Card>
            </Link>
          );

          if (section.permission) {
            return (
              <PermissionGuard key={section.href} permission={section.permission as any}>
                {content}
              </PermissionGuard>
            );
          }

          return content;
        })}
      </Grid>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Settings sections grid
- [ ] Permission-based section visibility
- [ ] Navigation to sub-pages
- [ ] Clear section descriptions

---

## Task 8.2: Profile Settings Page

### Description
Admin profile management including avatar, contact info, and preferences.

### Backend Requirements

```graphql
extend type Mutation {
  updateMyProfile(input: UpdateProfileInput!): User!
  updateAvatar(file: Upload!): User!
}

input UpdateProfileInput {
  name: String
  phone: String
  timezone: String
  language: String
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/settings/profile/page.tsx`

```tsx
'use client';

import { useState, useCallback } from 'react';
import { useMutation } from '@apollo/client/react';
import {
  Box, Flex, Heading, Text, Card, Button, TextField,
  Avatar, Select, Callout
} from '@radix-ui/themes';
import { ArrowLeft, Upload, Check, AlertCircle } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useKeycloak } from '@pml.tickets/shared';
import { UPDATE_MY_PROFILE } from '@/lib/graphql/queries/users';

const timezones = [
  { value: 'UTC', label: 'UTC' },
  { value: 'America/New_York', label: 'Eastern Time (US & Canada)' },
  { value: 'America/Chicago', label: 'Central Time (US & Canada)' },
  { value: 'America/Denver', label: 'Mountain Time (US & Canada)' },
  { value: 'America/Los_Angeles', label: 'Pacific Time (US & Canada)' },
  { value: 'Europe/London', label: 'London' },
  { value: 'Europe/Paris', label: 'Paris' },
  { value: 'Asia/Tokyo', label: 'Tokyo' },
  { value: 'Africa/Lusaka', label: 'Lusaka' },
];

const languages = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Spanish' },
  { value: 'fr', label: 'French' },
  { value: 'de', label: 'German' },
];

export default function ProfileSettingsPage() {
  const router = useRouter();
  const { user } = useKeycloak();

  const [profile, setProfile] = useState({
    name: user?.name || '',
    phone: user?.phone || '',
    timezone: user?.timezone || 'UTC',
    language: user?.language || 'en',
  });
  const [success, setSuccess] = useState(false);

  const [updateProfile, { loading, error }] = useMutation(UPDATE_MY_PROFILE, {
    onCompleted: () => {
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    },
  });

  const handleSave = () => {
    updateProfile({
      variables: { input: profile },
    });
  };

  return (
    <Box>
      <Flex align="center" gap="3" mb="5">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft size={16} />
        </Button>
        <Box>
          <Heading size="6">Profile Settings</Heading>
          <Text color="gray" size="2">Manage your account information</Text>
        </Box>
      </Flex>

      {error && (
        <Callout.Root color="red" mb="4">
          <Callout.Icon><AlertCircle size={16} /></Callout.Icon>
          <Callout.Text>{error.message}</Callout.Text>
        </Callout.Root>
      )}

      {success && (
        <Callout.Root color="green" mb="4">
          <Callout.Icon><Check size={16} /></Callout.Icon>
          <Callout.Text>Profile updated successfully!</Callout.Text>
        </Callout.Root>
      )}

      <Card>
        <Box p="5">
          {/* Avatar Section */}
          <Flex gap="4" mb="6" align="center">
            <Avatar
              size="6"
              src={user?.avatar}
              fallback={profile.name?.charAt(0) || user?.email?.charAt(0) || 'A'}
              radius="full"
            />
            <Box>
              <Text size="2" weight="medium">Profile Photo</Text>
              <Text size="2" color="gray" mb="2">
                JPG, GIF or PNG. 1MB max.
              </Text>
              <Button variant="soft" size="1">
                <Upload size={14} /> Upload New
              </Button>
            </Box>
          </Flex>

          {/* Form Fields */}
          <Flex direction="column" gap="4" style={{ maxWidth: '400px' }}>
            <Box>
              <Text size="2" weight="medium" mb="1">Email</Text>
              <TextField.Root
                value={user?.email || ''}
                disabled
                style={{ backgroundColor: 'var(--gray-a2)' }}
              />
              <Text size="1" color="gray" mt="1">
                Email cannot be changed
              </Text>
            </Box>

            <Box>
              <Text size="2" weight="medium" mb="1">Display Name</Text>
              <TextField.Root
                placeholder="Your name"
                value={profile.name}
                onChange={(e) => setProfile(p => ({ ...p, name: e.target.value }))}
              />
            </Box>

            <Box>
              <Text size="2" weight="medium" mb="1">Phone Number</Text>
              <TextField.Root
                type="tel"
                placeholder="+1 (555) 000-0000"
                value={profile.phone}
                onChange={(e) => setProfile(p => ({ ...p, phone: e.target.value }))}
              />
            </Box>

            <Box>
              <Text size="2" weight="medium" mb="1">Timezone</Text>
              <Select.Root
                value={profile.timezone}
                onValueChange={(v) => setProfile(p => ({ ...p, timezone: v }))}
              >
                <Select.Trigger style={{ width: '100%' }} />
                <Select.Content>
                  {timezones.map((tz) => (
                    <Select.Item key={tz.value} value={tz.value}>
                      {tz.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Box>

            <Box>
              <Text size="2" weight="medium" mb="1">Language</Text>
              <Select.Root
                value={profile.language}
                onValueChange={(v) => setProfile(p => ({ ...p, language: v }))}
              >
                <Select.Trigger style={{ width: '100%' }} />
                <Select.Content>
                  {languages.map((lang) => (
                    <Select.Item key={lang.value} value={lang.value}>
                      {lang.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Box>
          </Flex>

          <Flex justify="end" mt="6">
            <Button onClick={handleSave} disabled={loading}>
              {loading ? 'Saving...' : 'Save Changes'}
            </Button>
          </Flex>
        </Box>
      </Card>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Display current profile info
- [ ] Avatar upload functionality
- [ ] Update display name
- [ ] Update phone number
- [ ] Timezone selection
- [ ] Language preference
- [ ] Success/error feedback

---

## Task 8.3: Security Settings Page

### Description
Password management, 2FA, and session management.

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/settings/security/page.tsx`

```tsx
'use client';

import { useState } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  Box, Flex, Heading, Text, Card, Button, TextField,
  Switch, Table, Badge, Dialog, Callout, Separator
} from '@radix-ui/themes';
import {
  ArrowLeft, Key, Shield, Monitor, Smartphone, Trash2,
  AlertTriangle, Check
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useKeycloak } from '@pml.tickets/shared';
import {
  CHANGE_PASSWORD, ENABLE_2FA, DISABLE_2FA, GET_SESSIONS, REVOKE_SESSION
} from '@/lib/graphql/queries/security';
import { formatDateTime } from '@/lib/utils/format';

export default function SecuritySettingsPage() {
  const router = useRouter();
  const { user } = useKeycloak();

  const [passwordDialog, setPasswordDialog] = useState(false);
  const [passwords, setPasswords] = useState({
    current: '',
    new: '',
    confirm: '',
  });
  const [twoFAEnabled, setTwoFAEnabled] = useState(user?.twoFactorEnabled ?? false);

  const { data: sessionsData, refetch: refetchSessions } = useQuery(GET_SESSIONS);

  const [changePassword, { loading: changingPassword, error: passwordError }] = useMutation(CHANGE_PASSWORD, {
    onCompleted: () => {
      setPasswordDialog(false);
      setPasswords({ current: '', new: '', confirm: '' });
    },
  });

  const [revokeSession] = useMutation(REVOKE_SESSION, {
    onCompleted: () => refetchSessions(),
  });

  const sessions = sessionsData?.mySessions ?? [];

  return (
    <Box>
      <Flex align="center" gap="3" mb="5">
        <Button variant="ghost" onClick={() => router.back()}>
          <ArrowLeft size={16} />
        </Button>
        <Box>
          <Heading size="6">Security Settings</Heading>
          <Text color="gray" size="2">Manage your account security</Text>
        </Box>
      </Flex>

      {/* Password Section */}
      <Card mb="4">
        <Box p="5">
          <Flex justify="between" align="center">
            <Flex gap="3" align="center">
              <Box p="2" style={{ backgroundColor: 'var(--gray-a3)', borderRadius: 'var(--radius-2)' }}>
                <Key size={20} />
              </Box>
              <Box>
                <Text size="3" weight="medium">Password</Text>
                <Text size="2" color="gray">Last changed 30 days ago</Text>
              </Box>
            </Flex>
            <Button variant="soft" onClick={() => setPasswordDialog(true)}>
              Change Password
            </Button>
          </Flex>
        </Box>
      </Card>

      {/* Two-Factor Authentication */}
      <Card mb="4">
        <Box p="5">
          <Flex justify="between" align="center">
            <Flex gap="3" align="center">
              <Box p="2" style={{ backgroundColor: 'var(--gray-a3)', borderRadius: 'var(--radius-2)' }}>
                <Shield size={20} />
              </Box>
              <Box>
                <Text size="3" weight="medium">Two-Factor Authentication</Text>
                <Text size="2" color="gray">
                  {twoFAEnabled
                    ? 'Your account is protected with 2FA'
                    : 'Add an extra layer of security to your account'}
                </Text>
              </Box>
            </Flex>
            <Flex align="center" gap="3">
              <Badge color={twoFAEnabled ? 'green' : 'gray'} variant="soft">
                {twoFAEnabled ? 'Enabled' : 'Disabled'}
              </Badge>
              <Switch
                checked={twoFAEnabled}
                onCheckedChange={setTwoFAEnabled}
              />
            </Flex>
          </Flex>
        </Box>
      </Card>

      {/* Active Sessions */}
      <Card>
        <Box p="5">
          <Text size="3" weight="medium" mb="4">Active Sessions</Text>
          <Text size="2" color="gray" mb="4">
            These are the devices currently logged into your account.
          </Text>

          <Flex direction="column" gap="3">
            {sessions.map((session: any) => (
              <Flex
                key={session.id}
                justify="between"
                align="center"
                p="3"
                style={{
                  backgroundColor: session.current ? 'var(--green-a2)' : 'var(--gray-a2)',
                  borderRadius: 'var(--radius-2)',
                }}
              >
                <Flex gap="3" align="center">
                  {session.deviceType === 'mobile' ? (
                    <Smartphone size={18} />
                  ) : (
                    <Monitor size={18} />
                  )}
                  <Box>
                    <Flex align="center" gap="2">
                      <Text size="2" weight="medium">{session.deviceName}</Text>
                      {session.current && (
                        <Badge color="green" variant="soft" size="1">Current</Badge>
                      )}
                    </Flex>
                    <Text size="1" color="gray">
                      {session.location} • {session.browser}
                    </Text>
                    <Text size="1" color="gray">
                      Last active: {formatDateTime(session.lastActive)}
                    </Text>
                  </Box>
                </Flex>
                {!session.current && (
                  <Button
                    variant="ghost"
                    size="1"
                    color="red"
                    onClick={() => revokeSession({ variables: { sessionId: session.id } })}
                  >
                    <Trash2 size={14} /> Revoke
                  </Button>
                )}
              </Flex>
            ))}
          </Flex>

          <Separator my="4" />

          <Button variant="soft" color="red">
            <Trash2 size={14} /> Revoke All Other Sessions
          </Button>
        </Box>
      </Card>

      {/* Change Password Dialog */}
      <Dialog.Root open={passwordDialog} onOpenChange={setPasswordDialog}>
        <Dialog.Content style={{ maxWidth: 400 }}>
          <Dialog.Title>Change Password</Dialog.Title>
          {passwordError && (
            <Callout.Root color="red" mb="3">
              <Callout.Icon><AlertTriangle size={16} /></Callout.Icon>
              <Callout.Text>{passwordError.message}</Callout.Text>
            </Callout.Root>
          )}
          <Flex direction="column" gap="4" mt="4">
            <Box>
              <Text size="2" weight="medium" mb="1">Current Password</Text>
              <TextField.Root
                type="password"
                value={passwords.current}
                onChange={(e) => setPasswords(p => ({ ...p, current: e.target.value }))}
              />
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="1">New Password</Text>
              <TextField.Root
                type="password"
                value={passwords.new}
                onChange={(e) => setPasswords(p => ({ ...p, new: e.target.value }))}
              />
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="1">Confirm New Password</Text>
              <TextField.Root
                type="password"
                value={passwords.confirm}
                onChange={(e) => setPasswords(p => ({ ...p, confirm: e.target.value }))}
              />
              {passwords.new && passwords.confirm && passwords.new !== passwords.confirm && (
                <Text size="1" color="red" mt="1">Passwords do not match</Text>
              )}
            </Box>
          </Flex>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              onClick={() => changePassword({
                variables: {
                  currentPassword: passwords.current,
                  newPassword: passwords.new,
                },
              })}
              disabled={changingPassword || !passwords.current || !passwords.new || passwords.new !== passwords.confirm}
            >
              Change Password
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Change password dialog
- [ ] Password validation (match, strength)
- [ ] Two-factor authentication toggle
- [ ] 2FA setup wizard
- [ ] Active sessions list
- [ ] Revoke individual sessions
- [ ] Revoke all other sessions

---

## Task 8.4: Audit Logs Page

### Description
Comprehensive audit log viewer for system administrators.

### Backend Requirements

```graphql
type AuditLog {
  id: ID!
  action: String!
  resource: String!
  resourceId: String
  user: User!
  ipAddress: String
  userAgent: String
  changes: JSON
  timestamp: DateTime!
  status: AuditStatus!
}

enum AuditStatus {
  SUCCESS
  FAILURE
  PARTIAL
}

type AuditLogPage {
  content: [AuditLog!]!
  totalElements: Int!
  totalPages: Int!
}

extend type Query {
  auditLogs(
    action: String
    resource: String
    userId: ID
    status: AuditStatus
    startDate: DateTime
    endDate: DateTime
    page: Int = 0
    size: Int = 50
  ): AuditLogPage! @hasPermission(permission: "audit.read")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/audit/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Card, Badge, TextField,
  Select, Table, Button, Dialog, ScrollArea
} from '@radix-ui/themes';
import {
  Search, Filter, Eye, Download, CheckCircle, XCircle,
  AlertTriangle, User, Calendar
} from 'lucide-react';
import { AUDIT_LOGS_QUERY } from '@/lib/graphql/queries/audit';
import { formatDateTime } from '@/lib/utils/format';
import { DateRangePicker } from '@/components/ui/DateRangePicker';

const actionColors: Record<string, 'blue' | 'green' | 'orange' | 'red'> = {
  CREATE: 'green',
  UPDATE: 'blue',
  DELETE: 'red',
  LOGIN: 'blue',
  LOGOUT: 'gray',
  VIEW: 'gray',
  EXPORT: 'orange',
};

const statusIcons: Record<string, React.ElementType> = {
  SUCCESS: CheckCircle,
  FAILURE: XCircle,
  PARTIAL: AlertTriangle,
};

const statusColors: Record<string, 'green' | 'red' | 'orange'> = {
  SUCCESS: 'green',
  FAILURE: 'red',
  PARTIAL: 'orange',
};

export default function AuditLogsPage() {
  const [filter, setFilter] = useState({
    action: '',
    resource: '',
    status: '',
    searchQuery: '',
    dateRange: {
      startDate: '',
      endDate: '',
    },
  });
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const { data, loading } = useQuery(AUDIT_LOGS_QUERY, {
    variables: {
      action: filter.action || null,
      resource: filter.resource || null,
      status: filter.status || null,
      startDate: filter.dateRange.startDate || null,
      endDate: filter.dateRange.endDate || null,
    },
  });

  const logs = data?.auditLogs?.content ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Audit Logs</Heading>
          <Text color="gray" size="2">System activity and security events</Text>
        </Box>
        <Button variant="soft">
          <Download size={16} /> Export Logs
        </Button>
      </Flex>

      {/* Filters */}
      <Card mb="4">
        <Flex p="4" gap="3" wrap="wrap" align="end">
          <Box style={{ width: '250px' }}>
            <Text size="1" color="gray" mb="1">Search</Text>
            <TextField.Root
              placeholder="Search by user, resource ID..."
              value={filter.searchQuery}
              onChange={(e) => setFilter(f => ({ ...f, searchQuery: e.target.value }))}
            >
              <TextField.Slot>
                <Search size={14} />
              </TextField.Slot>
            </TextField.Root>
          </Box>
          <Box>
            <Text size="1" color="gray" mb="1">Action</Text>
            <Select.Root
              value={filter.action}
              onValueChange={(v) => setFilter(f => ({ ...f, action: v }))}
            >
              <Select.Trigger placeholder="All Actions" />
              <Select.Content>
                <Select.Item value="">All Actions</Select.Item>
                <Select.Item value="CREATE">Create</Select.Item>
                <Select.Item value="UPDATE">Update</Select.Item>
                <Select.Item value="DELETE">Delete</Select.Item>
                <Select.Item value="LOGIN">Login</Select.Item>
                <Select.Item value="LOGOUT">Logout</Select.Item>
                <Select.Item value="EXPORT">Export</Select.Item>
              </Select.Content>
            </Select.Root>
          </Box>
          <Box>
            <Text size="1" color="gray" mb="1">Resource</Text>
            <Select.Root
              value={filter.resource}
              onValueChange={(v) => setFilter(f => ({ ...f, resource: v }))}
            >
              <Select.Trigger placeholder="All Resources" />
              <Select.Content>
                <Select.Item value="">All Resources</Select.Item>
                <Select.Item value="EVENT">Event</Select.Item>
                <Select.Item value="BOOKING">Booking</Select.Item>
                <Select.Item value="USER">User</Select.Item>
                <Select.Item value="TICKET">Ticket</Select.Item>
                <Select.Item value="REFUND">Refund</Select.Item>
                <Select.Item value="SETTLEMENT">Settlement</Select.Item>
              </Select.Content>
            </Select.Root>
          </Box>
          <Box>
            <Text size="1" color="gray" mb="1">Status</Text>
            <Select.Root
              value={filter.status}
              onValueChange={(v) => setFilter(f => ({ ...f, status: v }))}
            >
              <Select.Trigger placeholder="All Status" />
              <Select.Content>
                <Select.Item value="">All Status</Select.Item>
                <Select.Item value="SUCCESS">Success</Select.Item>
                <Select.Item value="FAILURE">Failure</Select.Item>
                <Select.Item value="PARTIAL">Partial</Select.Item>
              </Select.Content>
            </Select.Root>
          </Box>
          <DateRangePicker
            value={filter.dateRange}
            onChange={(range) => setFilter(f => ({ ...f, dateRange: range }))}
          />
        </Flex>
      </Card>

      {/* Logs Table */}
      <Card>
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Timestamp</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>User</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Action</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Resource</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>IP Address</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Details</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {logs.map((log: any) => {
              const StatusIcon = statusIcons[log.status] || CheckCircle;
              return (
                <Table.Row key={log.id}>
                  <Table.Cell>
                    <Text size="2">{formatDateTime(log.timestamp)}</Text>
                  </Table.Cell>
                  <Table.Cell>
                    <Flex align="center" gap="2">
                      <User size={14} />
                      <Box>
                        <Text size="2">{log.user.name || log.user.email}</Text>
                      </Box>
                    </Flex>
                  </Table.Cell>
                  <Table.Cell>
                    <Badge
                      color={actionColors[log.action] || 'gray'}
                      variant="soft"
                    >
                      {log.action}
                    </Badge>
                  </Table.Cell>
                  <Table.Cell>
                    <Box>
                      <Text size="2">{log.resource}</Text>
                      {log.resourceId && (
                        <Text size="1" color="gray" style={{ fontFamily: 'monospace' }}>
                          {log.resourceId.slice(0, 8)}...
                        </Text>
                      )}
                    </Box>
                  </Table.Cell>
                  <Table.Cell>
                    <Badge color={statusColors[log.status]} variant="soft">
                      <StatusIcon size={12} /> {log.status}
                    </Badge>
                  </Table.Cell>
                  <Table.Cell>
                    <Text size="2" style={{ fontFamily: 'monospace' }}>
                      {log.ipAddress || '-'}
                    </Text>
                  </Table.Cell>
                  <Table.Cell>
                    <Button
                      variant="ghost"
                      size="1"
                      onClick={() => setSelectedLog(log)}
                    >
                      <Eye size={14} />
                    </Button>
                  </Table.Cell>
                </Table.Row>
              );
            })}
          </Table.Body>
        </Table.Root>
      </Card>

      {/* Log Details Dialog */}
      <Dialog.Root open={!!selectedLog} onOpenChange={() => setSelectedLog(null)}>
        <Dialog.Content style={{ maxWidth: 600 }}>
          <Dialog.Title>Audit Log Details</Dialog.Title>
          {selectedLog && (
            <Box mt="4">
              <Flex direction="column" gap="3">
                <Flex justify="between">
                  <Text color="gray">Timestamp</Text>
                  <Text>{formatDateTime(selectedLog.timestamp)}</Text>
                </Flex>
                <Flex justify="between">
                  <Text color="gray">User</Text>
                  <Text>{selectedLog.user.name || selectedLog.user.email}</Text>
                </Flex>
                <Flex justify="between">
                  <Text color="gray">Action</Text>
                  <Badge color={actionColors[selectedLog.action]}>{selectedLog.action}</Badge>
                </Flex>
                <Flex justify="between">
                  <Text color="gray">Resource</Text>
                  <Text>{selectedLog.resource}</Text>
                </Flex>
                <Flex justify="between">
                  <Text color="gray">Resource ID</Text>
                  <Text style={{ fontFamily: 'monospace' }}>{selectedLog.resourceId}</Text>
                </Flex>
                <Flex justify="between">
                  <Text color="gray">IP Address</Text>
                  <Text style={{ fontFamily: 'monospace' }}>{selectedLog.ipAddress}</Text>
                </Flex>
                <Flex justify="between">
                  <Text color="gray">User Agent</Text>
                  <Text size="1">{selectedLog.userAgent}</Text>
                </Flex>
                {selectedLog.changes && (
                  <Box>
                    <Text color="gray" mb="2">Changes</Text>
                    <ScrollArea style={{ maxHeight: '200px' }}>
                      <Box
                        p="3"
                        style={{
                          backgroundColor: 'var(--gray-a2)',
                          borderRadius: 'var(--radius-2)',
                          fontFamily: 'monospace',
                          fontSize: '12px',
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        {JSON.stringify(selectedLog.changes, null, 2)}
                      </Box>
                    </ScrollArea>
                  </Box>
                )}
              </Flex>
            </Box>
          )}
          <Flex justify="end" mt="4">
            <Dialog.Close>
              <Button variant="soft" color="gray">Close</Button>
            </Dialog.Close>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Audit log listing with pagination
- [ ] Filter by action type
- [ ] Filter by resource type
- [ ] Filter by status (success/failure)
- [ ] Filter by date range
- [ ] Search by user or resource ID
- [ ] Log detail modal with JSON changes
- [ ] Export logs to CSV
- [ ] Color-coded action badges
- [ ] Status icons

---

## Dependencies

- Phase 1: Core Infrastructure (PermissionGuard)
- Phase 5: User Management (for user references)

## Estimated Time

- Task 8.1 (Settings Overview): 2 hours
- Task 8.2 (Profile Settings): 4 hours
- Task 8.3 (Security Settings): 6 hours
- Task 8.4 (Audit Logs): 5 hours

**Total: ~17 hours**

---

# Project Summary

## Total Estimated Hours

| Phase | Description | Hours |
|-------|-------------|-------|
| Phase 1 | Core Infrastructure | 20 |
| Phase 2 | Dashboard & Overview | 20 |
| Phase 3 | Event Management | 29 |
| Phase 4 | Booking & Finance | 28 |
| Phase 5 | User Management | 21 |
| Phase 6 | Scanner & Validation | 19 |
| Phase 7 | Reports & Analytics | 23 |
| Phase 8 | Settings & Audit | 17 |
| **Total** | | **177 hours** |

## Recommended Team

- 1 Senior Frontend Developer (lead)
- 1 Mid-level Frontend Developer
- 1 Backend Developer (for GraphQL schemas and services)
- 1 QA Engineer

## Recommended Timeline

With a 2-person frontend team working full-time:
- **Weeks 1-2**: Phase 1 (Core Infrastructure)
- **Weeks 2-3**: Phase 2 (Dashboard)
- **Weeks 3-5**: Phase 3 (Events)
- **Weeks 5-7**: Phase 4 (Bookings & Finance)
- **Weeks 7-8**: Phase 5 (Users)
- **Weeks 8-9**: Phase 6 (Scanner)
- **Weeks 9-10**: Phase 7 (Reports)
- **Week 10-11**: Phase 8 (Settings & Audit)

**Total: ~11 weeks**
