'use client';

/**
 * Event Check-In Page
 *
 * Check-in attendees at events:
 * - QR code scanning
 * - Manual ticket lookup
 * - Attendee list with check-in status
 * - Real-time stats
 */

import { useState, useCallback, useMemo, useEffect } from 'react';
import { useParams } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Card,
  Button,
  Badge,
  TextField,
  Dialog,
  Avatar,
  ScrollArea,
} from '@radix-ui/themes';
import {
  Search,
  Check,
  Xmark,
  User,
  Label,
  Refresh,
  Camera,
  ScanBarcode,
} from 'iconoir-react';
import { PageHeader } from '@/components/ui';

// =============================================================================
// TYPES
// =============================================================================

interface Attendee {
  id: string;
  ticketId: string;
  name: string;
  email: string;
  ticketType: string;
  checkedIn: boolean;
  checkedInAt?: string;
  purchasedAt: string;
}

interface CheckInResult {
  success: boolean;
  message: string;
  attendee?: Attendee;
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockEvent = {
  id: '1',
  title: 'Summer Music Festival',
  date: '2025-06-15',
  venue: 'Lusaka National Park',
};

const mockAttendees: Attendee[] = [
  {
    id: '1',
    ticketId: 'TKT-001-2025',
    name: 'John Mwanza',
    email: 'john@example.com',
    ticketType: 'VIP',
    checkedIn: true,
    checkedInAt: '2025-05-19T14:30:00',
    purchasedAt: '2025-05-10T09:00:00',
  },
  {
    id: '2',
    ticketId: 'TKT-002-2025',
    name: 'Mary Banda',
    email: 'mary@example.com',
    ticketType: 'Early Bird',
    checkedIn: true,
    checkedInAt: '2025-05-19T14:45:00',
    purchasedAt: '2025-05-11T11:30:00',
  },
  {
    id: '3',
    ticketId: 'TKT-003-2025',
    name: 'Peter Tembo',
    email: 'peter@example.com',
    ticketType: 'Regular',
    checkedIn: false,
    purchasedAt: '2025-05-12T16:00:00',
  },
  {
    id: '4',
    ticketId: 'TKT-004-2025',
    name: 'Grace Phiri',
    email: 'grace@example.com',
    ticketType: 'VIP',
    checkedIn: false,
    purchasedAt: '2025-05-13T08:45:00',
  },
  {
    id: '5',
    ticketId: 'TKT-005-2025',
    name: 'David Lungu',
    email: 'david@example.com',
    ticketType: 'Regular',
    checkedIn: true,
    checkedInAt: '2025-05-19T15:00:00',
    purchasedAt: '2025-05-14T10:20:00',
  },
  {
    id: '6',
    ticketId: 'TKT-006-2025',
    name: 'Sarah Mulenga',
    email: 'sarah@example.com',
    ticketType: 'Early Bird',
    checkedIn: false,
    purchasedAt: '2025-05-15T13:15:00',
  },
  {
    id: '7',
    ticketId: 'TKT-007-2025',
    name: 'Michael Chanda',
    email: 'michael@example.com',
    ticketType: 'Regular',
    checkedIn: false,
    purchasedAt: '2025-05-16T09:30:00',
  },
  {
    id: '8',
    ticketId: 'TKT-008-2025',
    name: 'Linda Zulu',
    email: 'linda@example.com',
    ticketType: 'VIP',
    checkedIn: true,
    checkedInAt: '2025-05-19T15:15:00',
    purchasedAt: '2025-05-17T14:00:00',
  },
];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

function formatTime(dateString: string): string {
  return new Date(dateString).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
}


// =============================================================================
// SCANNER COMPONENT
// =============================================================================

interface ScannerProps {
  onScan: (ticketId: string) => void;
  isScanning: boolean;
}

function Scanner({ onScan, isScanning }: ScannerProps) {
  const [manualInput, setManualInput] = useState('');

  const handleManualSubmit = () => {
    if (manualInput.trim()) {
      onScan(manualInput.trim());
      setManualInput('');
    }
  };

  return (
    <Card
      style={{
        padding: '24px',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: '16px',
      }}
    >
      {/* Camera Scanner Placeholder */}
      <Box
        style={{
          aspectRatio: '1',
          maxWidth: 300,
          margin: '0 auto 24px',
          background: 'linear-gradient(135deg, var(--surface-subtle) 0%, var(--surface-default) 100%)',
          borderRadius: '16px',
          border: '2px dashed var(--surface-border)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Scanner Frame */}
        <Box
          style={{
            position: 'absolute',
            inset: 20,
            border: '3px solid var(--brand-500)',
            borderRadius: '12px',
            opacity: 0.3,
          }}
        />

        {/* Scanner Line Animation */}
        {isScanning && (
          <Box
            style={{
              position: 'absolute',
              left: 24,
              right: 24,
              height: 3,
              background: 'linear-gradient(90deg, transparent 0%, var(--brand-500) 50%, transparent 100%)',
              animation: 'scanLine 2s linear infinite',
            }}
          />
        )}

        <Camera style={{ width: 48, height: 48, color: 'var(--content-muted)' }} />
        <Text size="2" style={{ color: 'var(--content-muted)', textAlign: 'center' }}>
          {isScanning ? 'Scanning...' : 'Camera scanner ready'}
        </Text>
        <Button
          size="2"
          variant="soft"
          style={{ background: 'rgba(16, 185, 129, 0.15)', color: 'var(--brand-500)' }}
        >
          <ScanBarcode style={{ width: 18, height: 18, marginRight: 8 }} />
          {isScanning ? 'Scanning Active' : 'Start Scanner'}
        </Button>
      </Box>

      {/* Manual Input */}
      <Box>
        <Text
          size="2"
          weight="medium"
          mb="2"
          style={{ color: 'var(--content-secondary)', display: 'block', textAlign: 'center' }}
        >
          Or enter ticket ID manually
        </Text>
        <Flex gap="2">
          <TextField.Root
            size="3"
            placeholder="e.g., TKT-001-2025"
            value={manualInput}
            onChange={(e) => setManualInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleManualSubmit()}
            style={{ flex: 1 }}
          >
            <TextField.Slot>
              <Label style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
            </TextField.Slot>
          </TextField.Root>
          <Button
            size="3"
            onClick={handleManualSubmit}
            disabled={!manualInput.trim()}
            style={{
              background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
            }}
          >
            Check In
          </Button>
        </Flex>
      </Box>

      <style jsx global>{`
        @keyframes scanLine {
          0% { top: 24px; }
          50% { top: calc(100% - 27px); }
          100% { top: 24px; }
        }
      `}</style>
    </Card>
  );
}

// =============================================================================
// CHECK-IN RESULT DIALOG
// =============================================================================

interface CheckInResultDialogProps {
  result: CheckInResult | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function CheckInResultDialog({ result, open, onOpenChange }: CheckInResultDialogProps) {
  if (!result) return null;

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content style={{ maxWidth: 400, textAlign: 'center' }}>
        <Box
          style={{
            width: 80,
            height: 80,
            borderRadius: '50%',
            background: result.success
              ? 'rgba(16, 185, 129, 0.1)'
              : 'rgba(239, 68, 68, 0.1)',
            border: `2px solid ${result.success ? 'var(--brand-500)' : '#EF4444'}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 20px',
          }}
        >
          {result.success ? (
            <Check style={{ width: 40, height: 40, color: 'var(--brand-500)' }} />
          ) : (
            <Xmark style={{ width: 40, height: 40, color: '#EF4444' }} />
          )}
        </Box>

        <Dialog.Title style={{ marginBottom: 8 }}>
          {result.success ? 'Check-In Successful!' : 'Check-In Failed'}
        </Dialog.Title>

        <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: 16 }}>
          {result.message}
        </Text>

        {result.attendee && (
          <Card
            style={{
              padding: '16px',
              background: 'var(--surface-subtle)',
              borderRadius: '12px',
              marginBottom: 20,
            }}
          >
            <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
              {result.attendee.name}
            </Text>
            <Text size="2" style={{ color: 'var(--content-muted)', display: 'block' }}>
              {result.attendee.email}
            </Text>
            <Badge color="green" variant="soft" mt="2">
              {result.attendee.ticketType}
            </Badge>
          </Card>
        )}

        <Button
          size="3"
          onClick={() => onOpenChange(false)}
          style={{
            width: '100%',
            background: result.success
              ? 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)'
              : 'var(--surface-subtle)',
            color: result.success ? 'white' : 'var(--content-primary)',
          }}
        >
          Continue Scanning
        </Button>
      </Dialog.Content>
    </Dialog.Root>
  );
}

// =============================================================================
// ATTENDEE ROW COMPONENT
// =============================================================================

interface AttendeeRowProps {
  attendee: Attendee;
  onCheckIn: (id: string) => void;
}

function AttendeeRow({ attendee, onCheckIn }: AttendeeRowProps) {
  return (
    <Flex
      justify="between"
      align="center"
      py="3"
      style={{ borderBottom: '1px solid var(--surface-border)' }}
    >
      <Flex gap="3" align="center">
        <Avatar
          size="2"
          fallback={attendee.name.charAt(0)}
          radius="full"
          style={{
            background: attendee.checkedIn
              ? 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)'
              : 'var(--surface-subtle)',
          }}
        />
        <Box>
          <Flex align="center" gap="2">
            <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
              {attendee.name}
            </Text>
            <Badge
              size="1"
              variant="soft"
              color={
                attendee.ticketType === 'VIP'
                  ? 'amber'
                  : attendee.ticketType === 'Early Bird'
                  ? 'blue'
                  : 'gray'
              }
            >
              {attendee.ticketType}
            </Badge>
          </Flex>
          <Text size="1" style={{ color: 'var(--content-muted)', fontFamily: 'monospace' }}>
            {attendee.ticketId}
          </Text>
        </Box>
      </Flex>

      {attendee.checkedIn ? (
        <Flex align="center" gap="2">
          <Check style={{ width: 16, height: 16, color: 'var(--brand-500)' }} />
          <Text size="1" style={{ color: 'var(--content-muted)' }}>
            {attendee.checkedInAt && formatTime(attendee.checkedInAt)}
          </Text>
        </Flex>
      ) : (
        <Button
          size="1"
          variant="soft"
          onClick={() => onCheckIn(attendee.id)}
          style={{ background: 'rgba(16, 185, 129, 0.15)', color: 'var(--brand-500)' }}
        >
          Check In
        </Button>
      )}
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function CheckInPage() {
  const params = useParams();
  const eventId = params.id as string;

  const [attendees, setAttendees] = useState<Attendee[]>(mockAttendees);
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<'all' | 'checked-in' | 'not-checked-in'>('all');
  const [isScanning, setIsScanning] = useState(false);
  const [checkInResult, setCheckInResult] = useState<CheckInResult | null>(null);
  const [showResultDialog, setShowResultDialog] = useState(false);

  // Stats
  const stats = useMemo(() => {
    const total = attendees.length;
    const checkedIn = attendees.filter((a) => a.checkedIn).length;
    const remaining = total - checkedIn;
    const percentage = total > 0 ? (checkedIn / total) * 100 : 0;

    return { total, checkedIn, remaining, percentage };
  }, [attendees]);

  // Filtered attendees
  const filteredAttendees = useMemo(() => {
    let result = attendees;

    if (filter === 'checked-in') {
      result = result.filter((a) => a.checkedIn);
    } else if (filter === 'not-checked-in') {
      result = result.filter((a) => !a.checkedIn);
    }

    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (a) =>
          a.name.toLowerCase().includes(query) ||
          a.email.toLowerCase().includes(query) ||
          a.ticketId.toLowerCase().includes(query)
      );
    }

    return result;
  }, [attendees, filter, searchQuery]);

  // Handle check-in
  const handleCheckIn = useCallback((identifier: string) => {
    // Find attendee by ID or ticket ID
    const attendee = attendees.find(
      (a) => a.id === identifier || a.ticketId.toLowerCase() === identifier.toLowerCase()
    );

    if (!attendee) {
      setCheckInResult({
        success: false,
        message: 'Ticket not found. Please verify the ticket ID.',
      });
    } else if (attendee.checkedIn) {
      setCheckInResult({
        success: false,
        message: 'This ticket has already been checked in.',
        attendee,
      });
    } else {
      // Perform check-in
      setAttendees((prev) =>
        prev.map((a) =>
          a.id === attendee.id
            ? { ...a, checkedIn: true, checkedInAt: new Date().toISOString() }
            : a
        )
      );
      setCheckInResult({
        success: true,
        message: 'Welcome to the event!',
        attendee: { ...attendee, checkedIn: true },
      });
    }

    setShowResultDialog(true);
  }, [attendees]);

  // Simulate scanner
  useEffect(() => {
    if (isScanning) {
      const timer = setTimeout(() => {
        setIsScanning(false);
      }, 3000);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [isScanning]);

  return (
    <Box>
      <PageHeader
        title="Check-In"
        description={mockEvent.title}
        breadcrumbs={[
          { label: 'Events', href: '/events' },
          { label: mockEvent.title, href: `/events/${eventId}` },
          { label: 'Check-In' },
        ]}
      />

      {/* Stats Bar */}
      <Card
        mb="6"
        style={{
          padding: '20px 24px',
          background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(5, 150, 105, 0.1) 100%)',
          border: '1px solid rgba(16, 185, 129, 0.2)',
          borderRadius: '16px',
        }}
      >
        <Flex justify="between" align="center" wrap="wrap" gap="4">
          <Flex gap="6" wrap="wrap">
            <Box>
              <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Checked In
              </Text>
              <Text size="6" weight="bold" style={{ color: 'var(--brand-500)' }}>
                {stats.checkedIn}
              </Text>
            </Box>
            <Box>
              <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Remaining
              </Text>
              <Text size="6" weight="bold" style={{ color: 'var(--content-primary)' }}>
                {stats.remaining}
              </Text>
            </Box>
            <Box>
              <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Total
              </Text>
              <Text size="6" weight="bold" style={{ color: 'var(--content-secondary)' }}>
                {stats.total}
              </Text>
            </Box>
          </Flex>
          <Box style={{ minWidth: 200 }}>
            <Flex justify="between" mb="1">
              <Text size="1" style={{ color: 'var(--content-muted)' }}>Progress</Text>
              <Text size="1" weight="medium" style={{ color: 'var(--brand-500)' }}>
                {stats.percentage.toFixed(0)}%
              </Text>
            </Flex>
            <Box
              style={{
                height: 8,
                background: 'rgba(255, 255, 255, 0.3)',
                borderRadius: 4,
                overflow: 'hidden',
              }}
            >
              <Box
                style={{
                  height: '100%',
                  width: `${stats.percentage}%`,
                  background: 'var(--brand-500)',
                  borderRadius: 4,
                  transition: 'width 0.3s ease',
                }}
              />
            </Box>
          </Box>
        </Flex>
      </Card>

      {/* Main Content */}
      <Flex gap="6" direction={{ initial: 'column', lg: 'row' }}>
        {/* Scanner Section */}
        <Box style={{ flex: 1, maxWidth: 400 }}>
          <Scanner onScan={handleCheckIn} isScanning={isScanning} />
        </Box>

        {/* Attendee List */}
        <Box style={{ flex: 2 }}>
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Flex justify="between" align="center" mb="4">
              <Text size="4" weight="medium" style={{ color: 'var(--content-primary)' }}>
                Attendees
              </Text>
              <Button variant="ghost" size="1" style={{ color: 'var(--content-muted)' }}>
                <Refresh style={{ width: 16, height: 16, marginRight: 6 }} />
                Refresh
              </Button>
            </Flex>

            {/* Search and Filter */}
            <Flex gap="3" mb="4">
              <Box style={{ flex: 1 }}>
                <TextField.Root
                  size="2"
                  placeholder="Search by name, email, or ticket ID..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                >
                  <TextField.Slot>
                    <Search style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
                  </TextField.Slot>
                </TextField.Root>
              </Box>
              <Flex gap="2">
                <Button
                  size="2"
                  variant={filter === 'all' ? 'solid' : 'outline'}
                  onClick={() => setFilter('all')}
                  style={{
                    background: filter === 'all' ? 'var(--brand-500)' : undefined,
                    borderColor: 'var(--surface-border)',
                  }}
                >
                  All
                </Button>
                <Button
                  size="2"
                  variant={filter === 'not-checked-in' ? 'solid' : 'outline'}
                  onClick={() => setFilter('not-checked-in')}
                  style={{
                    background: filter === 'not-checked-in' ? 'var(--brand-500)' : undefined,
                    borderColor: 'var(--surface-border)',
                  }}
                >
                  Pending ({stats.remaining})
                </Button>
                <Button
                  size="2"
                  variant={filter === 'checked-in' ? 'solid' : 'outline'}
                  onClick={() => setFilter('checked-in')}
                  style={{
                    background: filter === 'checked-in' ? 'var(--brand-500)' : undefined,
                    borderColor: 'var(--surface-border)',
                  }}
                >
                  Checked In ({stats.checkedIn})
                </Button>
              </Flex>
            </Flex>

            {/* Attendee List */}
            {filteredAttendees.length === 0 ? (
              <Box py="8" style={{ textAlign: 'center' }}>
                <User style={{ width: 32, height: 32, color: 'var(--content-muted)', margin: '0 auto 12px' }} />
                <Text size="2" style={{ color: 'var(--content-muted)' }}>
                  {searchQuery ? 'No matching attendees found' : 'No attendees in this category'}
                </Text>
              </Box>
            ) : (
              <ScrollArea style={{ maxHeight: 500 }}>
                <Flex direction="column">
                  {filteredAttendees.map((attendee) => (
                    <AttendeeRow
                      key={attendee.id}
                      attendee={attendee}
                      onCheckIn={handleCheckIn}
                    />
                  ))}
                </Flex>
              </ScrollArea>
            )}
          </Card>
        </Box>
      </Flex>

      {/* Check-In Result Dialog */}
      <CheckInResultDialog
        result={checkInResult}
        open={showResultDialog}
        onOpenChange={setShowResultDialog}
      />
    </Box>
  );
}
