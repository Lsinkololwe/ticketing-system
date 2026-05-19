# Phase 6: Scanner & Ticket Validation

## Overview
Implement ticket scanning and validation features for event check-in staff (SCANNER role).

---

## Task 6.1: Scanner Dashboard

### Description
Dashboard for scanner staff showing today's events and quick scan access.

### Backend Requirements

#### GraphQL Schema

```graphql
type ScannerDashboard {
  todaysEvents: [AssignedEvent!]!
  totalScansToday: Int!
  validScansToday: Int!
  invalidScansToday: Int!
  recentScans: [ScanRecord!]!
}

type AssignedEvent {
  event: Event!
  checkInCount: Int!
  totalTickets: Int!
  checkInPercentage: Float!
  scannerAssignment: ScannerAssignment
}

type ScannerAssignment {
  id: ID!
  scanner: User!
  event: Event!
  gate: String
  startTime: DateTime
  endTime: DateTime
}

extend type Query {
  scannerDashboard: ScannerDashboard! @hasPermission(permission: "ticket.validate")
  myScannerAssignments(date: Date): [ScannerAssignment!]! @hasPermission(permission: "ticket.validate")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/scanner/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import {
  Box, Flex, Heading, Text, Card, Button, Badge, Grid, Progress
} from '@radix-ui/themes';
import {
  ScanLine, CheckCircle, XCircle, Clock, Calendar,
  MapPin, Users, ArrowRight
} from 'lucide-react';
import Link from 'next/link';
import { SCANNER_DASHBOARD_QUERY } from '@/lib/graphql/queries/scanner';
import { formatTime, formatDate } from '@/lib/utils/format';
import { StatCard } from '@/components/ui/BentoGrid';

export default function ScannerDashboardPage() {
  const { data, loading } = useQuery(SCANNER_DASHBOARD_QUERY, {
    pollInterval: 30000, // Refresh every 30 seconds
  });

  const dashboard = data?.scannerDashboard;

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Scanner Dashboard</Heading>
          <Text color="gray" size="2">
            {formatDate(new Date())} - Today's check-in overview
          </Text>
        </Box>
        <Button size="3" asChild>
          <Link href="/dashboard/scanner/validate">
            <ScanLine size={18} /> Start Scanning
          </Link>
        </Button>
      </Flex>

      {/* Stats */}
      <Grid columns="4" gap="4" mb="5">
        <StatCard
          title="Total Scans Today"
          value={dashboard?.totalScansToday ?? 0}
          icon={<ScanLine size={18} />}
          color="blue"
        />
        <StatCard
          title="Valid Scans"
          value={dashboard?.validScansToday ?? 0}
          icon={<CheckCircle size={18} />}
          color="green"
        />
        <StatCard
          title="Invalid Scans"
          value={dashboard?.invalidScansToday ?? 0}
          icon={<XCircle size={18} />}
          color="red"
        />
        <StatCard
          title="Scan Rate"
          value={`${((dashboard?.validScansToday ?? 0) / Math.max(dashboard?.totalScansToday ?? 1, 1) * 100).toFixed(0)}%`}
          icon={<Clock size={18} />}
          color="purple"
        />
      </Grid>

      <Grid columns="2" gap="4">
        {/* Today's Events */}
        <Card>
          <Box p="4">
            <Flex justify="between" align="center" mb="4">
              <Text size="3" weight="medium">Today's Events</Text>
              <Badge variant="soft">{dashboard?.todaysEvents?.length ?? 0} events</Badge>
            </Flex>

            <Flex direction="column" gap="3">
              {dashboard?.todaysEvents?.map((item) => (
                <Card key={item.event.id} variant="surface">
                  <Box p="3">
                    <Flex justify="between" align="start" mb="2">
                      <Box>
                        <Text weight="medium">{item.event.name}</Text>
                        <Flex gap="3" mt="1">
                          <Flex align="center" gap="1">
                            <Clock size={12} />
                            <Text size="1" color="gray">
                              {formatTime(item.event.startDate)}
                            </Text>
                          </Flex>
                          <Flex align="center" gap="1">
                            <MapPin size={12} />
                            <Text size="1" color="gray">
                              {item.event.venue?.name}
                            </Text>
                          </Flex>
                        </Flex>
                      </Box>
                      <Button size="1" asChild>
                        <Link href={`/dashboard/scanner/validate?eventId=${item.event.id}`}>
                          Scan
                        </Link>
                      </Button>
                    </Flex>

                    {/* Check-in Progress */}
                    <Box mt="3">
                      <Flex justify="between" mb="1">
                        <Text size="1" color="gray">Check-in Progress</Text>
                        <Text size="1">
                          {item.checkInCount} / {item.totalTickets}
                        </Text>
                      </Flex>
                      <Progress value={item.checkInPercentage} />
                    </Box>

                    {/* Assignment Info */}
                    {item.scannerAssignment && (
                      <Flex
                        mt="2"
                        p="2"
                        style={{
                          backgroundColor: 'var(--blue-a2)',
                          borderRadius: 'var(--radius-2)',
                        }}
                        gap="2"
                      >
                        <Text size="1" color="blue">
                          Assigned to Gate: {item.scannerAssignment.gate || 'Main'}
                        </Text>
                        <Text size="1" color="gray">
                          {formatTime(item.scannerAssignment.startTime)} - {formatTime(item.scannerAssignment.endTime)}
                        </Text>
                      </Flex>
                    )}
                  </Box>
                </Card>
              ))}

              {(!dashboard?.todaysEvents || dashboard.todaysEvents.length === 0) && (
                <Flex
                  direction="column"
                  align="center"
                  justify="center"
                  py="6"
                  gap="2"
                >
                  <Calendar size={32} style={{ color: 'var(--gray-9)' }} />
                  <Text color="gray">No events assigned for today</Text>
                </Flex>
              )}
            </Flex>
          </Box>
        </Card>

        {/* Recent Scans */}
        <Card>
          <Box p="4">
            <Flex justify="between" align="center" mb="4">
              <Text size="3" weight="medium">Recent Scans</Text>
              <Button variant="ghost" size="1" asChild>
                <Link href="/dashboard/scanner/history">
                  View All <ArrowRight size={14} />
                </Link>
              </Button>
            </Flex>

            <Flex direction="column" gap="2">
              {dashboard?.recentScans?.map((scan) => (
                <Flex
                  key={scan.id}
                  justify="between"
                  align="center"
                  p="2"
                  style={{
                    backgroundColor: scan.isValid ? 'var(--green-a2)' : 'var(--red-a2)',
                    borderRadius: 'var(--radius-2)',
                  }}
                >
                  <Flex align="center" gap="2">
                    {scan.isValid ? (
                      <CheckCircle size={16} style={{ color: 'var(--green-11)' }} />
                    ) : (
                      <XCircle size={16} style={{ color: 'var(--red-11)' }} />
                    )}
                    <Box>
                      <Text size="2" style={{ fontFamily: 'monospace' }}>
                        {scan.ticketCode}
                      </Text>
                      <Text size="1" color="gray">{scan.eventName}</Text>
                    </Box>
                  </Flex>
                  <Text size="1" color="gray">
                    {formatTime(scan.scannedAt)}
                  </Text>
                </Flex>
              ))}

              {(!dashboard?.recentScans || dashboard.recentScans.length === 0) && (
                <Text size="2" color="gray" align="center" py="4">
                  No scans recorded yet
                </Text>
              )}
            </Flex>
          </Box>
        </Card>
      </Grid>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Stats cards (total, valid, invalid scans)
- [ ] Today's assigned events list
- [ ] Event check-in progress bars
- [ ] Scanner assignment info (gate, time)
- [ ] Quick scan button per event
- [ ] Recent scans feed with status
- [ ] Auto-refresh every 30 seconds

---

## Task 6.2: Ticket Validation Interface

### Description
Real-time ticket scanning and validation interface.

### Backend Requirements

```graphql
type ScanResult {
  isValid: Boolean!
  ticket: Ticket
  message: String!
  attendeeName: String
  ticketType: String
  eventName: String
  alreadyScanned: Boolean
  scannedAt: DateTime
}

type Ticket {
  id: ID!
  ticketCode: String!
  status: TicketStatus!
  ticketType: TicketType!
  event: Event!
  booking: Booking!
  checkedInAt: DateTime
  checkedInBy: User
}

enum TicketStatus {
  VALID
  USED
  CANCELLED
  EXPIRED
}

extend type Mutation {
  validateTicket(ticketCode: String!, eventId: ID): ScanResult!
    @hasPermission(permission: "ticket.validate")

  manualCheckIn(ticketCode: String!, eventId: ID!): ScanResult!
    @hasPermission(permission: "ticket.validate")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/scanner/validate/page.tsx`

```tsx
'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { useSearchParams } from 'next/navigation';
import {
  Box, Flex, Heading, Text, Card, Button, TextField,
  Dialog, Select
} from '@radix-ui/themes';
import {
  ScanLine, CheckCircle, XCircle, Camera, Keyboard,
  RefreshCw, Volume2, VolumeX
} from 'lucide-react';
import { VALIDATE_TICKET, TODAYS_EVENTS_QUERY } from '@/lib/graphql/queries/scanner';

type ScanMode = 'camera' | 'manual';

export default function TicketValidationPage() {
  const searchParams = useSearchParams();
  const initialEventId = searchParams.get('eventId');

  const [mode, setMode] = useState<ScanMode>('manual');
  const [eventId, setEventId] = useState(initialEventId || '');
  const [manualCode, setManualCode] = useState('');
  const [lastResult, setLastResult] = useState<ScanResult | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [scanCount, setScanCount] = useState({ valid: 0, invalid: 0 });

  const inputRef = useRef<HTMLInputElement>(null);
  const successSound = useRef<HTMLAudioElement | null>(null);
  const errorSound = useRef<HTMLAudioElement | null>(null);

  const { data: eventsData } = useQuery(TODAYS_EVENTS_QUERY);

  const [validateTicket, { loading: validating }] = useMutation(VALIDATE_TICKET, {
    onCompleted: (data) => {
      const result = data.validateTicket;
      setLastResult(result);

      // Update counts
      setScanCount(prev => ({
        valid: prev.valid + (result.isValid ? 1 : 0),
        invalid: prev.invalid + (result.isValid ? 0 : 1),
      }));

      // Play sound
      if (soundEnabled) {
        if (result.isValid) {
          successSound.current?.play();
        } else {
          errorSound.current?.play();
        }
      }

      // Clear input after short delay
      setTimeout(() => {
        setManualCode('');
        inputRef.current?.focus();
      }, 500);
    },
  });

  // Initialize audio
  useEffect(() => {
    successSound.current = new Audio('/sounds/success.mp3');
    errorSound.current = new Audio('/sounds/error.mp3');
  }, []);

  // Focus input on mount
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const handleScan = useCallback((code: string) => {
    if (!code.trim() || validating) return;

    validateTicket({
      variables: {
        ticketCode: code.trim(),
        eventId: eventId || null,
      },
    });
  }, [eventId, validating, validateTicket]);

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && manualCode) {
      handleScan(manualCode);
    }
  };

  const todaysEvents = eventsData?.todaysEvents ?? [];

  return (
    <Box>
      {/* Hidden audio elements */}
      <audio ref={successSound} src="/sounds/success.mp3" preload="auto" />
      <audio ref={errorSound} src="/sounds/error.mp3" preload="auto" />

      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Ticket Validation</Heading>
          <Text color="gray" size="2">Scan or enter ticket codes to check in attendees</Text>
        </Box>
        <Flex gap="2">
          <Button
            variant={soundEnabled ? 'soft' : 'ghost'}
            onClick={() => setSoundEnabled(!soundEnabled)}
          >
            {soundEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
          </Button>
          <Button
            variant="soft"
            onClick={() => {
              setScanCount({ valid: 0, invalid: 0 });
              setLastResult(null);
            }}
          >
            <RefreshCw size={16} /> Reset
          </Button>
        </Flex>
      </Flex>

      <Flex gap="5">
        {/* Main Scanner Area */}
        <Box style={{ flex: 1 }}>
          <Card style={{ minHeight: '500px' }}>
            <Box p="5">
              {/* Event Selection */}
              <Box mb="4">
                <Text size="2" weight="medium" mb="2">Select Event (Optional)</Text>
                <Select.Root value={eventId} onValueChange={setEventId}>
                  <Select.Trigger placeholder="All Events" style={{ width: '100%' }} />
                  <Select.Content>
                    <Select.Item value="">All Events</Select.Item>
                    {todaysEvents.map((event) => (
                      <Select.Item key={event.id} value={event.id}>
                        {event.name}
                      </Select.Item>
                    ))}
                  </Select.Content>
                </Select.Root>
              </Box>

              {/* Mode Toggle */}
              <Flex gap="2" mb="5">
                <Button
                  variant={mode === 'manual' ? 'solid' : 'soft'}
                  onClick={() => setMode('manual')}
                  style={{ flex: 1 }}
                >
                  <Keyboard size={16} /> Manual Entry
                </Button>
                <Button
                  variant={mode === 'camera' ? 'solid' : 'soft'}
                  onClick={() => setMode('camera')}
                  style={{ flex: 1 }}
                >
                  <Camera size={16} /> Camera Scan
                </Button>
              </Flex>

              {/* Input Area */}
              {mode === 'manual' ? (
                <Box mb="5">
                  <TextField.Root
                    ref={inputRef}
                    size="3"
                    placeholder="Enter or scan ticket code..."
                    value={manualCode}
                    onChange={(e) => setManualCode(e.target.value.toUpperCase())}
                    onKeyPress={handleKeyPress}
                    style={{ fontFamily: 'monospace', fontSize: '18px' }}
                  >
                    <TextField.Slot>
                      <ScanLine size={20} />
                    </TextField.Slot>
                  </TextField.Root>
                  <Button
                    size="3"
                    onClick={() => handleScan(manualCode)}
                    disabled={!manualCode || validating}
                    style={{ width: '100%', marginTop: '12px' }}
                  >
                    {validating ? 'Validating...' : 'Validate Ticket'}
                  </Button>
                </Box>
              ) : (
                <Box
                  mb="5"
                  style={{
                    aspectRatio: '4/3',
                    backgroundColor: 'var(--gray-a3)',
                    borderRadius: 'var(--radius-3)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {/* Camera QR Scanner would go here */}
                  <Text color="gray">Camera scanner - Requires camera access</Text>
                </Box>
              )}

              {/* Result Display */}
              {lastResult && (
                <Card
                  style={{
                    backgroundColor: lastResult.isValid ? 'var(--green-a3)' : 'var(--red-a3)',
                  }}
                >
                  <Flex direction="column" align="center" p="5" gap="3">
                    {lastResult.isValid ? (
                      <CheckCircle size={64} style={{ color: 'var(--green-11)' }} />
                    ) : (
                      <XCircle size={64} style={{ color: 'var(--red-11)' }} />
                    )}
                    <Heading size="5" align="center">
                      {lastResult.isValid ? 'VALID' : 'INVALID'}
                    </Heading>
                    <Text size="3" align="center">
                      {lastResult.message}
                    </Text>
                    {lastResult.isValid && lastResult.attendeeName && (
                      <Box style={{ textAlign: 'center' }}>
                        <Text size="4" weight="bold">{lastResult.attendeeName}</Text>
                        <Text size="2" color="gray">{lastResult.ticketType}</Text>
                      </Box>
                    )}
                    {lastResult.alreadyScanned && (
                      <Text size="2" color="red">
                        Already checked in at {formatTime(lastResult.scannedAt)}
                      </Text>
                    )}
                  </Flex>
                </Card>
              )}
            </Box>
          </Card>
        </Box>

        {/* Stats Sidebar */}
        <Box style={{ width: '280px' }}>
          <Card mb="4">
            <Flex direction="column" p="4" gap="3">
              <Text size="2" weight="medium">Session Stats</Text>
              <Flex justify="between">
                <Text color="gray">Valid Scans</Text>
                <Text weight="bold" color="green">{scanCount.valid}</Text>
              </Flex>
              <Flex justify="between">
                <Text color="gray">Invalid Scans</Text>
                <Text weight="bold" color="red">{scanCount.invalid}</Text>
              </Flex>
              <Flex justify="between">
                <Text color="gray">Total Scans</Text>
                <Text weight="bold">{scanCount.valid + scanCount.invalid}</Text>
              </Flex>
              <Flex justify="between">
                <Text color="gray">Success Rate</Text>
                <Text weight="bold">
                  {scanCount.valid + scanCount.invalid > 0
                    ? ((scanCount.valid / (scanCount.valid + scanCount.invalid)) * 100).toFixed(0)
                    : 0}%
                </Text>
              </Flex>
            </Flex>
          </Card>

          {/* Keyboard Shortcuts */}
          <Card>
            <Flex direction="column" p="4" gap="2">
              <Text size="2" weight="medium" mb="2">Keyboard Shortcuts</Text>
              <Flex justify="between">
                <Text size="1" color="gray">Submit code</Text>
                <Badge variant="soft">Enter</Badge>
              </Flex>
              <Flex justify="between">
                <Text size="1" color="gray">Clear input</Text>
                <Badge variant="soft">Esc</Badge>
              </Flex>
              <Flex justify="between">
                <Text size="1" color="gray">Toggle sound</Text>
                <Badge variant="soft">S</Badge>
              </Flex>
            </Flex>
          </Card>
        </Box>
      </Flex>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] Manual code entry with large input
- [ ] Event selection filter (optional)
- [ ] Real-time validation feedback
- [ ] Visual success/error indicators
- [ ] Sound feedback (toggle on/off)
- [ ] Session stats display
- [ ] Already scanned warning
- [ ] Attendee name display on valid scan
- [ ] Keyboard shortcuts
- [ ] Auto-focus input after scan

---

## Task 6.3: Scan History

### Description
View and search scan history with filters.

### Backend Requirements

```graphql
type ScanRecord {
  id: ID!
  ticketCode: String!
  isValid: Boolean!
  message: String!
  ticket: Ticket
  event: Event!
  scanner: User!
  scannedAt: DateTime!
  gate: String
}

type ScanHistoryPage {
  content: [ScanRecord!]!
  totalElements: Int!
  totalPages: Int!
}

extend type Query {
  scanHistory(
    eventId: ID
    isValid: Boolean
    dateFrom: DateTime
    dateTo: DateTime
    page: Int = 0
    size: Int = 50
  ): ScanHistoryPage! @hasPermission(permission: "ticket.validate")
}
```

### Frontend Implementation

#### File: `apps/admin/src/app/dashboard/scanner/history/page.tsx`

```tsx
'use client';

import { useQuery } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Heading, Text, Badge, Select, Table, Card, Button
} from '@radix-ui/themes';
import { CheckCircle, XCircle, Download, Filter } from 'lucide-react';
import { SCAN_HISTORY_QUERY } from '@/lib/graphql/queries/scanner';
import { formatDateTime } from '@/lib/utils/format';

export default function ScanHistoryPage() {
  const [filter, setFilter] = useState({
    eventId: '',
    isValid: null as boolean | null,
    dateFrom: '',
    dateTo: '',
  });

  const { data, loading } = useQuery(SCAN_HISTORY_QUERY, {
    variables: {
      eventId: filter.eventId || null,
      isValid: filter.isValid,
      dateFrom: filter.dateFrom || null,
      dateTo: filter.dateTo || null,
    },
  });

  const scans = data?.scanHistory?.content ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6">Scan History</Heading>
          <Text color="gray" size="2">View all ticket scans</Text>
        </Box>
        <Button variant="soft">
          <Download size={16} /> Export
        </Button>
      </Flex>

      {/* Filters */}
      <Flex gap="3" mb="4">
        <Select.Root
          value={filter.isValid === null ? 'all' : filter.isValid.toString()}
          onValueChange={(v) => setFilter(f => ({
            ...f,
            isValid: v === 'all' ? null : v === 'true'
          }))}
        >
          <Select.Trigger placeholder="All Results" />
          <Select.Content>
            <Select.Item value="all">All Results</Select.Item>
            <Select.Item value="true">Valid Only</Select.Item>
            <Select.Item value="false">Invalid Only</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      {/* Scans Table */}
      <Card>
        <Table.Root>
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Result</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Ticket Code</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Event</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Gate</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Scanner</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Time</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Message</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {scans.map((scan) => (
              <Table.Row key={scan.id}>
                <Table.Cell>
                  {scan.isValid ? (
                    <Badge color="green" variant="soft">
                      <CheckCircle size={12} /> Valid
                    </Badge>
                  ) : (
                    <Badge color="red" variant="soft">
                      <XCircle size={12} /> Invalid
                    </Badge>
                  )}
                </Table.Cell>
                <Table.Cell>
                  <Text style={{ fontFamily: 'monospace' }}>{scan.ticketCode}</Text>
                </Table.Cell>
                <Table.Cell>{scan.event.name}</Table.Cell>
                <Table.Cell>{scan.gate || '-'}</Table.Cell>
                <Table.Cell>{scan.scanner.name}</Table.Cell>
                <Table.Cell>{formatDateTime(scan.scannedAt)}</Table.Cell>
                <Table.Cell>
                  <Text size="2" color="gray">{scan.message}</Text>
                </Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Card>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] List all scans with pagination
- [ ] Filter by valid/invalid
- [ ] Filter by event
- [ ] Filter by date range
- [ ] Show ticket code, event, gate, scanner
- [ ] Export to CSV
- [ ] Visual indicators for valid/invalid

---

## Task 6.4: Scanner Assignment Management (Admin)

### Description
Admin interface to assign scanners to events and gates.

### Backend Requirements

```graphql
extend type Mutation {
  assignScannerToEvent(input: ScannerAssignmentInput!): ScannerAssignment!
    @hasPermission(permission: "scanner.assign")

  removesScannerAssignment(id: ID!): Boolean!
    @hasPermission(permission: "scanner.assign")
}

input ScannerAssignmentInput {
  scannerId: ID!
  eventId: ID!
  gate: String
  startTime: DateTime
  endTime: DateTime
}

extend type Query {
  eventScannerAssignments(eventId: ID!): [ScannerAssignment!]!
    @hasPermission(permission: "scanner.assign")

  availableScanners(eventId: ID!): [User!]!
    @hasPermission(permission: "scanner.assign")
}
```

### Frontend Implementation

#### File: `apps/admin/src/components/events/ScannerAssignmentsTab.tsx`

```tsx
'use client';

import { useQuery, useMutation } from '@apollo/client/react';
import { useState } from 'react';
import {
  Box, Flex, Text, Card, Button, Dialog, Select,
  TextField, Table, Avatar
} from '@radix-ui/themes';
import { Plus, Trash2, User } from 'lucide-react';
import {
  EVENT_SCANNER_ASSIGNMENTS,
  AVAILABLE_SCANNERS,
  ASSIGN_SCANNER,
  REMOVE_ASSIGNMENT
} from '@/lib/graphql/queries/scanner';
import { formatTime } from '@/lib/utils/format';

interface ScannerAssignmentsTabProps {
  eventId: string;
}

export function ScannerAssignmentsTab({ eventId }: ScannerAssignmentsTabProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [newAssignment, setNewAssignment] = useState({
    scannerId: '',
    gate: '',
    startTime: '',
    endTime: '',
  });

  const { data: assignmentsData, refetch } = useQuery(EVENT_SCANNER_ASSIGNMENTS, {
    variables: { eventId },
  });

  const { data: scannersData } = useQuery(AVAILABLE_SCANNERS, {
    variables: { eventId },
    skip: !dialogOpen,
  });

  const [assignScanner] = useMutation(ASSIGN_SCANNER, {
    onCompleted: () => {
      refetch();
      setDialogOpen(false);
      setNewAssignment({ scannerId: '', gate: '', startTime: '', endTime: '' });
    },
  });

  const [removeAssignment] = useMutation(REMOVE_ASSIGNMENT, {
    onCompleted: () => refetch(),
  });

  const assignments = assignmentsData?.eventScannerAssignments ?? [];
  const availableScanners = scannersData?.availableScanners ?? [];

  return (
    <Box>
      <Flex justify="between" align="center" mb="4">
        <Text size="3" weight="medium">Scanner Assignments</Text>
        <Button onClick={() => setDialogOpen(true)}>
          <Plus size={16} /> Assign Scanner
        </Button>
      </Flex>

      <Table.Root variant="surface">
        <Table.Header>
          <Table.Row>
            <Table.ColumnHeaderCell>Scanner</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Gate</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Shift Time</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Scans</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell>Actions</Table.ColumnHeaderCell>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {assignments.map((assignment) => (
            <Table.Row key={assignment.id}>
              <Table.Cell>
                <Flex align="center" gap="2">
                  <Avatar
                    size="1"
                    fallback={assignment.scanner.name?.charAt(0)}
                    radius="full"
                  />
                  <Text>{assignment.scanner.name}</Text>
                </Flex>
              </Table.Cell>
              <Table.Cell>{assignment.gate || 'Main Entrance'}</Table.Cell>
              <Table.Cell>
                {assignment.startTime && assignment.endTime ? (
                  `${formatTime(assignment.startTime)} - ${formatTime(assignment.endTime)}`
                ) : (
                  'Full Event'
                )}
              </Table.Cell>
              <Table.Cell>{assignment.scanCount || 0}</Table.Cell>
              <Table.Cell>
                <Button
                  size="1"
                  variant="ghost"
                  color="red"
                  onClick={() => removeAssignment({ variables: { id: assignment.id } })}
                >
                  <Trash2 size={14} />
                </Button>
              </Table.Cell>
            </Table.Row>
          ))}
        </Table.Body>
      </Table.Root>

      {/* Assign Dialog */}
      <Dialog.Root open={dialogOpen} onOpenChange={setDialogOpen}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Assign Scanner</Dialog.Title>
          <Flex direction="column" gap="4" mt="4">
            <Box>
              <Text size="2" weight="medium" mb="1">Scanner</Text>
              <Select.Root
                value={newAssignment.scannerId}
                onValueChange={(v) => setNewAssignment(a => ({ ...a, scannerId: v }))}
              >
                <Select.Trigger placeholder="Select scanner" style={{ width: '100%' }} />
                <Select.Content>
                  {availableScanners.map((scanner) => (
                    <Select.Item key={scanner.id} value={scanner.id}>
                      {scanner.name} ({scanner.email})
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Box>
            <Box>
              <Text size="2" weight="medium" mb="1">Gate (Optional)</Text>
              <TextField.Root
                placeholder="e.g., Gate A, Main Entrance"
                value={newAssignment.gate}
                onChange={(e) => setNewAssignment(a => ({ ...a, gate: e.target.value }))}
              />
            </Box>
            <Flex gap="3">
              <Box style={{ flex: 1 }}>
                <Text size="2" weight="medium" mb="1">Start Time</Text>
                <TextField.Root
                  type="time"
                  value={newAssignment.startTime}
                  onChange={(e) => setNewAssignment(a => ({ ...a, startTime: e.target.value }))}
                />
              </Box>
              <Box style={{ flex: 1 }}>
                <Text size="2" weight="medium" mb="1">End Time</Text>
                <TextField.Root
                  type="time"
                  value={newAssignment.endTime}
                  onChange={(e) => setNewAssignment(a => ({ ...a, endTime: e.target.value }))}
                />
              </Box>
            </Flex>
          </Flex>
          <Flex gap="3" mt="4" justify="end">
            <Dialog.Close>
              <Button variant="soft" color="gray">Cancel</Button>
            </Dialog.Close>
            <Button
              onClick={() => assignScanner({
                variables: {
                  input: {
                    ...newAssignment,
                    eventId,
                  },
                },
              })}
              disabled={!newAssignment.scannerId}
            >
              Assign
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
```

### Acceptance Criteria
- [ ] List current scanner assignments
- [ ] Add new scanner assignment
- [ ] Select scanner from available list
- [ ] Assign to specific gate
- [ ] Set shift start/end times
- [ ] Remove assignment
- [ ] Show scan count per assignment

---

## Dependencies

- Phase 1: Core Infrastructure (StatCard, PermissionGuard)
- Phase 3: Event Management (event context)

## Estimated Time

- Task 6.1 (Scanner Dashboard): 4 hours
- Task 6.2 (Validation Interface): 8 hours
- Task 6.3 (Scan History): 3 hours
- Task 6.4 (Scanner Assignments): 4 hours

**Total: ~19 hours**
