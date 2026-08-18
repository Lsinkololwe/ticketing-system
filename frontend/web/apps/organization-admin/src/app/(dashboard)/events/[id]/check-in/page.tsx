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
  useEventTicketHolders,
  useValidateTicket,
  useCheckInSummary,
  useGateDeviceId,
  type TicketHolderVM,
  type ValidateTicketResult,
} from '@pml.tickets/shared/api/organization-admin/modules/checkin';
import { useMyEventDetail } from '@pml.tickets/shared/api/organization-admin/modules/events';
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

/**
 * How the result dialog should read.
 *
 * Three states, not two. A dropped connection is NOT a refusal — the ticket may
 * be perfectly good, and turning someone away on the strength of a lost signal
 * is a worse outcome than letting a steward retry.
 */
type ResultTone = 'admitted' | 'refused' | 'unreachable';

function toneOf(result: ValidateTicketResult): ResultTone {
  if (result.outcome === 'UNREACHABLE') return 'unreachable';
  return result.admitted ? 'admitted' : 'refused';
}

/** The headline for each outcome. The steward reads this and nothing else. */
const OUTCOME_HEADLINE: Record<string, string> = {
  ADMITTED: 'Admitted',
  ALREADY_RECORDED: 'Already recorded',
  ALREADY_ADMITTED: 'Already admitted',
  WRONG_EVENT: 'Wrong event',
  INVALID_STATE: 'Not admissible',
  NOT_FOUND: 'Not found',
  UNREACHABLE: 'Could not verify',
};

// =============================================================================
// ADAPTER — Ticket → the gate roster's row shape
//
// No fixture data lives in this app.
// =============================================================================

/** Ticket states that mean the holder has already passed the gate. */
const SCANNED_STATES = new Set(['VALIDATED']);

function toAttendee(ticket: TicketHolderVM): Attendee {
  return {
    id: ticket.id,
    ticketId: ticket.ticketNumber,
    name: ticket.buyerName ?? 'Guest',
    email: ticket.buyerEmail ?? '',
    ticketType: ticket.ticketCategoryName ?? 'General admission',
    // Derived from validatedAt AND status: a ticket refunded after being
    // scanned still carries a validatedAt, and must not read as admitted.
    checkedIn: !!ticket.validatedAt && SCANNED_STATES.has(String(ticket.status)),
    checkedInAt: ticket.validatedAt ?? undefined,
    purchasedAt: ticket.purchaseDate ?? '',
  };
}


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
  onScan: (code: string, options?: { manual?: boolean; reason?: string }) => void;
  isScanning: boolean;
  /**
   * Controlled, so a roster row can load a ticket number into it.
   *
   * The row's button used to admit directly, which meant a one-click manual
   * admission with no reason attached — the one thing a manual admission must
   * always carry. It now fills this field instead and the steward completes it.
   */
  code: string;
  onCodeChange: (code: string) => void;
}

/** The spec requires a substantive reason for a manual admission. */
const MIN_REASON_LENGTH = 10;

function Scanner({ onScan, isScanning, code, onCodeChange }: ScannerProps) {
  const manualInput = code;
  const setManualInput = onCodeChange;
  const [reason, setReason] = useState('');

  const reasonIsSufficient = reason.trim().length >= MIN_REASON_LENGTH;
  const canSubmit = manualInput.trim().length > 0 && reasonIsSufficient;

  const handleManualSubmit = () => {
    if (!canSubmit) return;
    onScan(manualInput.trim(), { manual: true, reason: reason.trim() });
    setManualInput('');
    // The reason is kept. A steward working a broken scanner types the same
    // reason for a queue of people, and clearing it every time is friction that
    // encourages a single character instead.
  };

  return (
    <Card
      style={{
        padding: '24px',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: 'var(--card-radius-bento)',
      }}
    >
      {/* Camera Scanner Placeholder */}
      <Box
        style={{
          aspectRatio: '1',
          maxWidth: 300,
          margin: '0 auto 24px',
          background: 'linear-gradient(135deg, var(--surface-subtle) 0%, var(--surface-default) 100%)',
          borderRadius: 'var(--card-radius-bento)',
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
            borderRadius: 'var(--card-radius)',
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
          style={{ background: 'var(--accent-a4)', color: 'var(--brand-500)' }}
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
          Or admit manually
        </Text>

        <Flex direction="column" gap="2">
          <TextField.Root
            size="3"
            data-testid="checkin-manual-code"
            placeholder="Ticket number, e.g. TKT-001-2025"
            value={manualInput}
            onChange={(e) => setManualInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleManualSubmit()}
          >
            <TextField.Slot>
              <Label style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
            </TextField.Slot>
          </TextField.Root>

          {/* A manual admission bypasses the QR entirely, so the record of WHY
              is the only thing that makes it reviewable afterwards. */}
          <TextField.Root
            size="2"
            data-testid="checkin-manual-reason"
            placeholder="Reason — e.g. QR damaged, ID checked"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleManualSubmit()}
          />

          {manualInput.trim() && !reasonIsSufficient && (
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              A reason of at least {MIN_REASON_LENGTH} characters is required.
            </Text>
          )}

          <Button
            size="3"
            data-testid="checkin-manual-submit"
            onClick={handleManualSubmit}
            disabled={!canSubmit}
            style={{
              background: canSubmit
                ? 'linear-gradient(135deg, var(--accent-9), var(--accent-11))'
                : undefined,
            }}
          >
            Admit
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
  result: ValidateTicketResult | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * The gate's answer, in three tones.
 *
 * Colour never carries the meaning alone — the headline word does. A steward
 * squinting at a phone in a dark venue reads "Already admitted" faster than
 * they read a shade of red, and the screen still works if the display is washed
 * out by stage lighting.
 */
function CheckInResultDialog({ result, open, onOpenChange }: CheckInResultDialogProps) {
  if (!result) return null;

  const tone = toneOf(result);
  const attendee = result.ticket ? toAttendee(result.ticket) : null;

  const ring =
    tone === 'admitted'
      ? { bg: 'var(--accent-a3)', border: 'var(--status-success-9)' }
      : tone === 'unreachable'
      ? { bg: 'var(--gray-a3)', border: 'var(--status-warning-9)' }
      : { bg: 'var(--status-danger-a3)', border: 'var(--status-danger-9)' };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content style={{ maxWidth: 400, textAlign: 'center' }} data-testid="checkin-result">
        <Box
          style={{
            width: 80,
            height: 80,
            borderRadius: '50%',
            background: ring.bg,
            border: `2px solid ${ring.border}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 20px',
          }}
        >
          {tone === 'admitted' ? (
            <Check style={{ width: 40, height: 40, color: 'var(--brand-500)' }} />
          ) : (
            <Xmark
              style={{
                width: 40,
                height: 40,
                color:
                  tone === 'unreachable'
                    ? 'var(--status-warning-11)'
                    : 'var(--status-danger-9)',
              }}
            />
          )}
        </Box>

        <Dialog.Title style={{ marginBottom: 8 }} data-testid="checkin-outcome">
          {OUTCOME_HEADLINE[result.outcome] ?? 'Check-in'}
        </Dialog.Title>

        <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: 16 }}>
          {result.message}
        </Text>

        {/* When this ticket was first admitted. Ten seconds ago is a steward
            double-tapping; two hours ago is two people. */}
        {result.conflict?.originalCheckInAt && (
          <Text size="2" style={{ color: 'var(--content-secondary)', display: 'block', marginBottom: 16 }}>
            First admitted at {formatTime(result.conflict.originalCheckInAt)}
          </Text>
        )}

        {attendee && (
          <Card
            style={{
              padding: '16px',
              background: 'var(--surface-subtle)',
              borderRadius: 'var(--card-radius)',
              marginBottom: 20,
            }}
          >
            <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
              {attendee.name}
            </Text>
            <Text size="2" style={{ color: 'var(--content-muted)', display: 'block' }}>
              {attendee.email}
            </Text>
            <Badge color="gray" variant="soft" mt="2">
              {attendee.ticketType}
            </Badge>
          </Card>
        )}

        <Button
          size="3"
          data-testid="checkin-result-dismiss"
          onClick={() => onOpenChange(false)}
          style={{
            width: '100%',
            background:
              tone === 'admitted'
                ? 'linear-gradient(135deg, var(--accent-9), var(--accent-11))'
                : 'var(--surface-subtle)',
            color: tone === 'admitted' ? 'white' : 'var(--content-primary)',
          }}
        >
          Next
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
  /** Loads this attendee's ticket number into the manual field. */
  onSelect: (ticketNumber: string) => void;
}

function AttendeeRow({ attendee, onSelect }: AttendeeRowProps) {
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
              ? 'linear-gradient(135deg, var(--accent-9), var(--accent-11))'
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
          data-testid={`checkin-select-${attendee.ticketId}`}
          // The TICKET NUMBER, not the internal id. The id matches no ticket
          // server-side, so this button used to report every attendee as not
          // found.
          onClick={() => onSelect(attendee.ticketId)}
          style={{ background: 'var(--accent-a4)', color: 'var(--brand-500)' }}
        >
          Admit manually
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

  const { holders, loading, refetch } = useEventTicketHolders(eventId);
  const { event } = useMyEventDetail(eventId);
  const deviceId = useGateDeviceId();
  const { validateTicket } = useValidateTicket(eventId);
  const { summary, refetch: refetchSummary } = useCheckInSummary(eventId);

  const attendees = useMemo(() => holders.map(toAttendee), [holders]);

  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<'all' | 'checked-in' | 'not-checked-in'>('all');
  const [isScanning, setIsScanning] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [checkInResult, setCheckInResult] = useState<ValidateTicketResult | null>(null);
  const [showResultDialog, setShowResultDialog] = useState(false);

  // Stats.
  //
  // Counted by the SERVER over booking_checkins, not derived from the roster
  // page the browser happens to hold. The roster is one page of a possibly
  // thousands-long list, so counting it locally reported the check-ins visible
  // on screen and called it attendance.
  const stats = useMemo(() => {
    const total = summary?.issued ?? attendees.length;
    const checkedIn = summary?.admitted ?? 0;
    const remaining = Math.max(total - checkedIn, 0);
    // Null, not 0, when nothing is issued: a rate with an empty denominator is
    // undefined, and 0% asserts a finding the data does not support.
    const percentage = total > 0 ? (checkedIn / total) * 100 : null;

    return { total, checkedIn, remaining, percentage };
  }, [summary, attendees.length]);

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

  // Handle check-in.
  //
  // The SERVER decides admissibility. This used to search the local roster and
  // decide itself, which admits a refunded or transferred ticket whenever the
  // list the browser loaded is a few minutes stale — and at a gate it always is.
  //
  // `code` is the ticket NUMBER, which is what a QR carries and what a steward
  // can read off a phone. Passing the internal id — as the roster button used
  // to — matches no ticket at all and reports every attendee as not found.
  const handleCheckIn = useCallback(
    async (code: string, options?: { manual?: boolean; reason?: string }) => {
      const result = await validateTicket(code.trim(), {
        // Typing a number or pressing the roster button IS a manual admission:
        // no QR was read. Recording it as a scan would hide it from the
        // manual-ratio alert, which exists precisely to catch a gate where the
        // scanning has stopped working.
        method: options?.manual ? 'MANUAL' : 'QR_ONLINE',
        reason: options?.reason,
        deviceId,
      });

      setCheckInResult(result);
      setShowResultDialog(true);

      // Refresh both: the roster so the row flips, the summary so the counters
      // move. Refetch rather than patch the cache — a hand-written optimistic
      // update at a gate is a way to show an admission that did not happen.
      if (result.admitted) {
        await Promise.all([refetch(), refetchSummary()]);
      }
    },
    [validateTicket, refetch, refetchSummary, deviceId]
  );

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
        description={event?.title ?? 'Loading event…'}
        breadcrumbs={[
          { label: 'Events', href: '/events' },
          { label: event?.title ?? 'Event', href: `/events/${eventId}` },
          { label: 'Check-In' },
        ]}
      />

      {/* Stats Bar */}
      <Card
        mb="6"
        style={{
          padding: '20px 24px',
          background: 'linear-gradient(135deg, var(--accent-a3) 0%, var(--accent-a3) 100%)',
          border: '1px solid var(--accent-a5)',
          borderRadius: 'var(--card-radius-bento)',
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
                Issued
              </Text>
              <Text size="6" weight="bold" style={{ color: 'var(--content-secondary)' }}>
                {stats.total}
              </Text>
            </Box>

            {/* Refused scans. Shown next to the attendance figures rather than
                buried, because for an offline duplicate this count is the only
                record that a second person walked in — the two numbers are
                meant to be read together. */}
            {summary && summary.conflicts > 0 && (
              <Box>
                <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                  Conflicts
                </Text>
                <Text
                  size="6"
                  weight="bold"
                  data-testid="checkin-conflicts"
                  style={{ color: 'var(--status-warning-11)' }}
                >
                  {summary.conflicts}
                </Text>
              </Box>
            )}

            {/* A gate running mostly on manual admissions is a broken scanner,
                and it is worth saying so while the event is still running. */}
            {summary && summary.manualAdmissions > 0 && (
              <Box>
                <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                  Manual
                </Text>
                <Text size="6" weight="bold" style={{ color: 'var(--content-secondary)' }}>
                  {summary.manualAdmissions}
                </Text>
              </Box>
            )}
          </Flex>

          <Box style={{ minWidth: 200 }}>
            <Flex justify="between" mb="1">
              <Text size="1" style={{ color: 'var(--content-muted)' }}>Progress</Text>
              <Text size="1" weight="medium" style={{ color: 'var(--brand-500)' }}>
                {/* An em dash, not "0%". No tickets issued means the rate is
                    undefined, and 0% would read as "nobody has arrived". */}
                {stats.percentage === null ? '—' : `${stats.percentage.toFixed(0)}%`}
              </Text>
            </Flex>
            <Box
              style={{
                height: 8,
                background: 'var(--gray-a6)',
                borderRadius: 4,
                overflow: 'hidden',
              }}
            >
              <Box
                style={{
                  height: '100%',
                  width: `${stats.percentage ?? 0}%`,
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
          <Scanner
            onScan={handleCheckIn}
            isScanning={isScanning}
            code={manualCode}
            onCodeChange={setManualCode}
          />
        </Box>

        {/* Attendee List */}
        <Box style={{ flex: 2 }}>
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: 'var(--card-radius-bento)',
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

            {/* Attendee List.
                Loading is distinguished from empty: at a gate, "no attendees"
                while the roster is still arriving would send a steward to turn
                people away. */}
            {loading && attendees.length === 0 ? (
              <Box py="8" style={{ textAlign: 'center' }} data-testid="checkin-roster-loading">
                <Text size="2" style={{ color: 'var(--content-muted)' }}>
                  Loading the gate list…
                </Text>
              </Box>
            ) : filteredAttendees.length === 0 ? (
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
                      onSelect={setManualCode}
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
