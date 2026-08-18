'use client';

/**
 * Events, categories and locations.
 *
 * <h2>Design authority</h2>
 * `Admin - Events.dc.html` — a section-scoped surface with its own three-item
 * rail (All events · Categories · Locations), a four-tile strip and status
 * filter chips over the events table, a card grid for categories, a table for
 * locations, and an event detail drawer with fields and a timeline.
 *
 * <h2>Approval decisions are real</h2>
 * The pending rows carry Approve, Request changes and Reject, wired to
 * `approveEvent`, `requestEventChanges` and `rejectEvent`. The latter two
 * declare `comments: String!` — the organizer reads that text, so the button
 * stays disabled until something is typed rather than sending a placeholder to
 * satisfy the non-null.
 *
 * <h2>Three departures from the design, and why</h2>
 * <ul>
 *   <li>The locations table drops the design's Capacity and Events-hosted
 *       columns. `Location` has neither field — no capacity, no hosted count —
 *       so both could only be invented.</li>
 *   <li>Category and venue create/edit/delete are not wired. The design's modal
 *       is a text form over `CategoryMutationResponse`, but a Delete that
 *       removes a category still referenced by events is a data-integrity
 *       decision nobody has specified. Read-only until it is.</li>
 *   <li>Category cards show the real `eventCount` from catalog rather than a
 *       seeded number.</li>
 * </ul>
 */

import { useState } from 'react';
import Link from 'next/link';
import { Box, Flex, Table, Text, TextArea } from '@radix-ui/themes';
import { Calendar, Folder, MapPin, Xmark } from 'iconoir-react';
import {
  useAdminEventCategories,
  useAdminEvents,
  useAdminLocations,
  useEventDecisions,
  useEventStats,
} from '@pml.tickets/shared/api/admin/modules/event';
import type { EventStatus } from '@pml.tickets/shared/types/graphql';
import { Badge, Button, EmptyState, PageHeader, StyledCard, Toast } from '@/components/ui';
import { formatCount, humanizeEnum, statusTone } from '@/lib/format';

export type EventsView = 'all' | 'categories' | 'locations';

const VIEWS: { id: EventsView; label: string; href: string; icon: React.ReactNode }[] = [
  { id: 'all', label: 'All events', href: '/events', icon: <Calendar width={18} height={18} /> },
  {
    id: 'categories',
    label: 'Categories',
    href: '/events/categories',
    icon: <Folder width={18} height={18} />,
  },
  {
    id: 'locations',
    label: 'Locations',
    href: '/events/locations',
    icon: <MapPin width={18} height={18} />,
  },
];

const TITLES: Record<EventsView, { title: string; description: string }> = {
  all: { title: 'All events', description: 'Every event on the platform, newest first.' },
  categories: { title: 'Categories', description: 'How events are classified for discovery.' },
  locations: { title: 'Locations', description: 'Venues events can be held at.' },
};

/**
 * The design's filter chips. `null` is "All".
 *
 * The values are EventStatus as the SCHEMA declares it — DRAFT, PENDING_REVIEW,
 * CHANGES_REQUESTED, APPROVED, PUBLISHED, CANCELLED, COMPLETED. The design's
 * fixture invents `PENDING_APPROVAL`, which is not a member; sending it made
 * the router reject the whole query with
 * "invalid type for variable: 'filter'", so the table rendered blank on every
 * load. The label stays the design's wording, the value is the real one.
 *
 * <p>NOTE THE ABSENCE OF `as EventStatus`. These were written with that cast,
 * which is precisely what let the invented value through — a cast tells the
 * compiler to stop checking the one thing worth checking. Unannotated, the
 * generated union rejects any non-member at build time, so this class of
 * mistake cannot reach the router again.
 */
const STATUS_FILTERS: { label: string; value: EventStatus | null }[] = [
  { label: 'All', value: null },
  { label: 'Published', value: 'PUBLISHED' },
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Pending approval', value: 'PENDING_REVIEW' },
  { label: 'Completed', value: 'COMPLETED' },
];

function shortDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function sellThrough(sold: number | null | undefined, capacity: number | null | undefined): string {
  if (!capacity || capacity <= 0) return '—';
  return `${Math.round(((sold ?? 0) / capacity) * 100)}%`;
}

export function EventsWorkbench({ view }: { view: EventsView }) {
  const [toast, setToast] = useState<{ variant: 'success' | 'error'; title: string } | null>(null);
  const heading = TITLES[view];
  const { stats } = useEventStats();

  return (
    <Box>
      <PageHeader
        title={heading.title}
        description={heading.description}
        breadcrumbs={[{ label: 'Events' }, { label: heading.title }]}
      />

      <Flex gap="5" align="start" direction={{ initial: 'column', md: 'row' }}>
        <Box width={{ initial: '100%', md: '224px' }} flexShrink="0" style={{ minWidth: 0 }}>
          <StyledCard hover="none" padding="3">
            <Flex direction="column" gap="1">
              {VIEWS.map((v) => {
                const active = v.id === view;
                const badge =
                  v.id === 'all' ? stats?.pendingApprovalEvents ?? null : null;
                return (
                  <Link key={v.id} href={v.href} style={{ textDecoration: 'none' }}>
                    <Flex
                      data-testid={`events-view-${v.id}`}
                      align="center"
                      gap="3"
                      style={{
                        padding: 'var(--space-2) var(--space-3)',
                        borderRadius: 'var(--radius-3)',
                        borderLeft: `2px solid ${active ? 'var(--accent-9)' : 'transparent'}`,
                        background: active ? 'var(--accent-a3)' : undefined,
                        color: active ? 'var(--accent-11)' : 'var(--gray-11)',
                        fontWeight: active ? 'var(--weight-medium)' : 'var(--weight-regular)',
                      }}
                    >
                      {v.icon}
                      <Text size="2" style={{ flex: 1 }}>
                        {v.label}
                      </Text>
                      {badge !== null && badge > 0 && (
                        <Text as="span" size="1" className="ds-amount">
                          {badge}
                        </Text>
                      )}
                    </Flex>
                  </Link>
                );
              })}
            </Flex>
          </StyledCard>
        </Box>

        <Box style={{ flex: 1, minWidth: 0, width: '100%' }}>
          {view === 'all' && (
            <AllEventsView announce={(v, t) => setToast({ variant: v, title: t })} />
          )}
          {view === 'categories' && <CategoriesView />}
          {view === 'locations' && <LocationsView />}
        </Box>
      </Flex>

      {toast && (
        <Box
          style={{
            position: 'fixed',
            bottom: 'var(--space-6)',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 80,
          }}
        >
          <Toast variant={toast.variant} title={toast.title} onClose={() => setToast(null)} />
        </Box>
      )}
    </Box>
  );
}

// =============================================================================
// All events
// =============================================================================

function AllEventsView({
  announce,
}: {
  announce: (variant: 'success' | 'error', title: string) => void;
}) {
  const [status, setStatus] = useState<EventStatus | null>(null);
  const [page, setPage] = useState(0);
  const { events, pageInfo, loading, refetch } = useAdminEvents({ status, page });
  const { stats } = useEventStats();
  const [active, setActive] = useState<(typeof events)[number] | null>(null);

  const tiles = [
    { label: 'Total events', value: stats ? formatCount(stats.totalEvents) : '—' },
    {
      label: 'Published',
      value: stats ? formatCount(stats.publishedEvents) : '—',
      color: 'var(--status-success-11)',
    },
    {
      label: 'Pending approval',
      value: stats ? formatCount(stats.pendingApprovalEvents) : '—',
      color: 'var(--amber-11)',
    },
    {
      label: 'Sell-through',
      value:
        stats && stats.totalCapacity > 0
          ? `${Math.round((stats.totalSoldTickets / stats.totalCapacity) * 100)}%`
          : '—',
      color: 'var(--accent-11)',
    },
  ];

  return (
    <>
      <Box
        data-testid="events-stat-tiles"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-4)',
        }}
      >
        {tiles.map((t) => (
          <StyledCard key={t.label} hover="none" padding="4">
            <Text className="ds-label" as="div">
              {t.label}
            </Text>
            <Text
              as="div"
              className="ds-amount"
              style={{
                fontSize: 'var(--text-5-size)',
                fontWeight: 'var(--weight-bold)',
                marginTop: 'var(--space-1)',
                color: t.color ?? 'var(--gray-12)',
              }}
            >
              {t.value}
            </Text>
          </StyledCard>
        ))}
      </Box>

      <Flex gap="2" mb="3" wrap="wrap" data-testid="events-status-filters">
        {STATUS_FILTERS.map((f) => (
          <Button
            key={f.label}
            variant={f.value === status ? 'solid' : 'outline'}
            size="1"
            onClick={() => {
              setStatus(f.value);
              setPage(0);
            }}
            data-testid={`events-filter-${f.value ?? 'all'}`}
          >
            {f.label}
          </Button>
        ))}
      </Flex>

      <StyledCard hover="none">
        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Event</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Organizer</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Date</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Capacity</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Sold</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell />
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && events.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={7}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading events…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : events.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={7}>
                    <EmptyState
                      size="sm"
                      icon={<Calendar width={20} height={20} />}
                      title="No events"
                      description="No event matches this filter yet."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                events.map((e) => (
                  <Table.Row key={e.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium" size="2">
                        {e.title}
                      </Text>
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {[e.category?.name, e.cityName].filter(Boolean).join(' · ') || '—'}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Text size="2">{e.organizerName}</Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" className="ds-amount">
                        {shortDate(e.eventDateTime)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" className="ds-amount">
                        {formatCount(e.totalCapacity)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text
                        size="2"
                        className="ds-amount"
                        style={{ color: 'var(--accent-11)', fontWeight: 'var(--weight-bold)' }}
                      >
                        {sellThrough(e.soldTickets, e.totalCapacity)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <span data-testid={`events-status-${e.id}`}>
                        <Badge color={statusTone(e.status)} variant="soft">
                          {humanizeEnum(e.status)}
                        </Badge>
                      </span>
                    </Table.Cell>
                    <Table.Cell>
                      <Flex justify="end">
                        <Button
                          variant="outline"
                          size="1"
                          onClick={() => setActive(e)}
                          data-testid={`events-view-detail-${e.id}`}
                        >
                          View
                        </Button>
                      </Flex>
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>

        <Flex justify="between" align="center" wrap="wrap" gap="3" pt="3">
          <Text size="1" style={{ color: 'var(--gray-9)' }} data-testid="events-count">
            {pageInfo.totalCount === 0
              ? 'Nothing to show'
              : `${formatCount(pageInfo.totalCount)} events`}
          </Text>
          {pageInfo.totalPages > 1 && (
            <Flex gap="2">
              <Button
                variant="outline"
                size="1"
                disabled={!pageInfo.hasPreviousPage}
                onClick={() => setPage(Math.max(0, pageInfo.currentPage - 1))}
                data-testid="events-pager-prev"
              >
                Prev
              </Button>
              <Button
                variant="outline"
                size="1"
                disabled={!pageInfo.hasNextPage}
                onClick={() => setPage(pageInfo.currentPage + 1)}
                data-testid="events-pager-next"
              >
                Next
              </Button>
            </Flex>
          )}
        </Flex>
      </StyledCard>

      {active && (
        <EventDrawer
          event={active}
          onClose={() => setActive(null)}
          onDecided={(ok, message) => {
            announce(ok ? 'success' : 'error', message);
            if (ok) {
              setActive(null);
              refetch();
            }
          }}
        />
      )}
    </>
  );
}

// =============================================================================
// Event drawer
// =============================================================================

type DecisionKind = 'reject' | 'changes';

function EventDrawer({
  event,
  onClose,
  onDecided,
}: {
  event: {
    id: string;
    title: string;
    status?: string | null;
    organizerName?: string | null;
    cityName?: string | null;
    locationName?: string | null;
    eventDateTime?: string | null;
    totalCapacity?: number | null;
    soldTickets?: number | null;
    submittedForApprovalAt?: string | null;
    approvalDeadline?: string | null;
    approvedAt?: string | null;
    approvedBy?: string | null;
    rejectedAt?: string | null;
    rejectionReason?: string | null;
    isOverdue?: boolean | null;
    category?: { name: string } | null;
  };
  onClose: () => void;
  onDecided: (ok: boolean, message: string) => void;
}) {
  const decisions = useEventDecisions();
  const [comments, setComments] = useState('');
  const [asking, setAsking] = useState<DecisionKind | null>(null);

  // PENDING_REVIEW is the schema's value. Gating on the design fixture's
  // invented PENDING_APPROVAL would have hidden Approve/Reject/Request-changes
  // on every event that actually needed a decision.
  const pending = event.status === 'PENDING_REVIEW';
  const canSubmit = comments.trim().length > 0;

  const fields: { label: string; value: string }[] = [
    { label: 'Organizer', value: event.organizerName ?? '—' },
    { label: 'Category', value: event.category?.name ?? '—' },
    { label: 'Venue', value: event.locationName ?? '—' },
    { label: 'City', value: event.cityName ?? '—' },
    { label: 'Starts', value: shortDate(event.eventDateTime) },
    { label: 'Capacity', value: formatCount(event.totalCapacity) },
    {
      label: 'Sold',
      value: `${formatCount(event.soldTickets)} (${sellThrough(event.soldTickets, event.totalCapacity)})`,
    },
    { label: 'Submitted', value: shortDate(event.submittedForApprovalAt) },
    { label: 'Reviewed by', value: event.approvedBy ?? '— not yet reviewed' },
  ];

  const timeline = [
    event.submittedForApprovalAt
      ? { action: 'Submitted for approval', time: shortDate(event.submittedForApprovalAt) }
      : null,
    event.approvedAt
      ? {
          action: `Approved${event.approvedBy ? ` by ${event.approvedBy}` : ''}`,
          time: shortDate(event.approvedAt),
        }
      : null,
    event.rejectedAt
      ? {
          action: `Rejected — ${event.rejectionReason ?? 'no reason recorded'}`,
          time: shortDate(event.rejectedAt),
        }
      : null,
  ].filter(Boolean) as { action: string; time: string }[];

  const run = async (kind: 'approve' | DecisionKind) => {
    const result =
      kind === 'approve'
        ? await decisions.approve(event.id)
        : kind === 'reject'
          ? await decisions.reject(event.id, comments.trim())
          : await decisions.requestChanges(event.id, comments.trim());

    onDecided(
      result.success,
      result.success
        ? kind === 'approve'
          ? 'Event approved.'
          : kind === 'reject'
            ? 'Event rejected — the organizer has been told why.'
            : 'Changes requested.'
        : result.errors[0] ?? result.message ?? 'The decision was refused.'
    );
  };

  return (
    <>
      <Box
        onClick={onClose}
        aria-hidden="true"
        style={{ position: 'fixed', inset: 0, background: 'var(--color-overlay)', zIndex: 60 }}
      />
      <Flex
        data-testid="events-drawer"
        direction="column"
        style={{
          position: 'fixed',
          top: 0,
          right: 0,
          bottom: 0,
          width: 'min(440px, 94vw)',
          background: 'var(--color-panel-solid)',
          borderLeft: '1px solid var(--gray-a5)',
          boxShadow: 'var(--shadow-5)',
          zIndex: 70,
        }}
      >
        <Flex
          justify="between"
          align="start"
          gap="3"
          p="5"
          style={{ borderBottom: '1px solid var(--gray-a5)' }}
        >
          <Box style={{ minWidth: 0 }}>
            <Text className="ds-label" as="div">
              Event
            </Text>
            <Text as="div" size="5" weight="bold">
              {event.title}
            </Text>
            <Text as="div" size="1" className="ds-amount" style={{ color: 'var(--gray-9)' }}>
              {event.id}
            </Text>
          </Box>
          <Button
            variant="ghost"
            size="1"
            onClick={onClose}
            icon={<Xmark width={16} height={16} />}
            data-testid="events-drawer-close"
          >
            Close
          </Button>
        </Flex>

        <Box p="5" style={{ flex: 1, overflowY: 'auto' }}>
          <Flex gap="2" align="center" wrap="wrap">
            <span data-testid="events-drawer-status">
              <Badge color={statusTone(event.status)} variant="soft">
                {humanizeEnum(event.status)}
              </Badge>
            </span>
            {event.isOverdue && (
              <Badge color="red" variant="soft">
                Past its approval deadline
              </Badge>
            )}
          </Flex>

          <Flex direction="column" mt="4">
            {fields.map((f) => (
              <Flex
                key={f.label}
                justify="between"
                align="center"
                gap="3"
                py="2"
                style={{ borderBottom: '1px solid var(--gray-a4)' }}
              >
                <Text size="2" style={{ color: 'var(--gray-9)' }}>
                  {f.label}
                </Text>
                <Text size="2" weight="medium">
                  {f.value}
                </Text>
              </Flex>
            ))}
          </Flex>

          {timeline.length > 0 && (
            <>
              <Text className="ds-label" as="div" mt="5" mb="2">
                Timeline
              </Text>
              <Flex direction="column">
                {timeline.map((t) => (
                  <Flex key={`${t.action}-${t.time}`} gap="3" py="2" align="start">
                    <Box
                      style={{
                        width: 'var(--space-2)',
                        height: 'var(--space-2)',
                        borderRadius: 'var(--radius-round)',
                        background: 'var(--accent-9)',
                        marginTop: 'var(--space-1)',
                        flexShrink: 0,
                      }}
                    />
                    <Box>
                      <Text as="div" size="2" weight="medium">
                        {t.action}
                      </Text>
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {t.time}
                      </Text>
                    </Box>
                  </Flex>
                ))}
              </Flex>
            </>
          )}
        </Box>

        {pending && (
          <Box p="5" style={{ borderTop: '1px solid var(--gray-a5)' }}>
            <Flex direction="column" gap="3">
              {asking && (
                <TextArea
                  data-testid="events-drawer-comments"
                  placeholder={
                    asking === 'reject'
                      ? 'Why is this event being rejected? The organizer is shown this.'
                      : 'What needs to change? The organizer is shown this.'
                  }
                  value={comments}
                  onChange={(e) => setComments(e.currentTarget.value)}
                  rows={3}
                />
              )}
              <Flex gap="2" justify="end" wrap="wrap">
                {asking ? (
                  <>
                    <Button
                      variant="ghost"
                      size="2"
                      onClick={() => {
                        setAsking(null);
                        setComments('');
                      }}
                      data-testid="events-drawer-cancel"
                    >
                      Cancel
                    </Button>
                    <Button
                      variant="solid"
                      color={asking === 'reject' ? 'red' : 'accent'}
                      size="2"
                      disabled={!canSubmit || decisions.submitting}
                      onClick={() => run(asking)}
                      data-testid="events-drawer-confirm"
                    >
                      {asking === 'reject' ? 'Confirm rejection' : 'Send request'}
                    </Button>
                  </>
                ) : (
                  <>
                    <Button
                      variant="outline"
                      color="red"
                      size="2"
                      disabled={decisions.submitting}
                      onClick={() => setAsking('reject')}
                      data-testid="events-drawer-reject"
                    >
                      Reject
                    </Button>
                    <Button
                      variant="outline"
                      size="2"
                      disabled={decisions.submitting}
                      onClick={() => setAsking('changes')}
                      data-testid="events-drawer-changes"
                    >
                      Request changes
                    </Button>
                    <Button
                      variant="solid"
                      size="2"
                      disabled={decisions.submitting}
                      onClick={() => run('approve')}
                      data-testid="events-drawer-approve"
                    >
                      Approve
                    </Button>
                  </>
                )}
              </Flex>
            </Flex>
          </Box>
        )}
      </Flex>
    </>
  );
}

// =============================================================================
// Categories
// =============================================================================

function CategoriesView() {
  const { categories, loading } = useAdminEventCategories();

  if (loading && categories.length === 0) {
    return (
      <StyledCard hover="none">
        <Text size="2" style={{ color: 'var(--gray-9)' }}>
          Loading categories…
        </Text>
      </StyledCard>
    );
  }

  if (categories.length === 0) {
    return (
      <StyledCard hover="none">
        <EmptyState
          size="md"
          icon={<Folder width={22} height={22} />}
          title="No categories"
          description="No event category has been created yet."
        />
      </StyledCard>
    );
  }

  return (
    <Box
      data-testid="events-category-grid"
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 'var(--space-3)',
      }}
    >
      {categories.map((c) => (
        <StyledCard key={c.id} hover="none" padding="4">
          <Flex align="center" gap="3">
            <Box style={{ flex: 1, minWidth: 0 }}>
              <Text as="div" size="3" weight="medium">
                {c.name}
              </Text>
              <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                {formatCount(c.eventCount ?? 0)} events
              </Text>
            </Box>
            {!c.isActive && (
              <Badge color="gray" variant="soft">
                Inactive
              </Badge>
            )}
          </Flex>
        </StyledCard>
      ))}
    </Box>
  );
}

// =============================================================================
// Locations
// =============================================================================

function LocationsView() {
  const { locations, totalCount, loading } = useAdminLocations();

  return (
    <StyledCard hover="none">
      <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
        <Table.Root variant="surface">
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Venue</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>City</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Address</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Country</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {loading && locations.length === 0 ? (
              <Table.Row>
                <Table.Cell colSpan={4}>
                  <Text size="2" style={{ color: 'var(--gray-9)' }}>
                    Loading locations…
                  </Text>
                </Table.Cell>
              </Table.Row>
            ) : locations.length === 0 ? (
              <Table.Row>
                <Table.Cell colSpan={4}>
                  <EmptyState
                    size="sm"
                    icon={<MapPin width={20} height={20} />}
                    title="No locations"
                    description="No venue has been registered yet."
                  />
                </Table.Cell>
              </Table.Row>
            ) : (
              locations.map((l) => (
                <Table.Row key={l.id} align="center">
                  <Table.RowHeaderCell>
                    <Text weight="medium" size="2">
                      {l.name}
                    </Text>
                  </Table.RowHeaderCell>
                  <Table.Cell>
                    <Text size="2">{l.city}</Text>
                  </Table.Cell>
                  <Table.Cell>
                    <Text size="2">{l.address}</Text>
                  </Table.Cell>
                  <Table.Cell>
                    <Text size="2">{l.country}</Text>
                  </Table.Cell>
                </Table.Row>
              ))
            )}
          </Table.Body>
        </Table.Root>
      </Box>
      <Flex justify="end" pt="3">
        <Text size="1" style={{ color: 'var(--gray-9)' }} data-testid="events-locations-count">
          {/* The design's Capacity and Events-hosted columns are absent: Location
              carries neither field, so both could only be invented. */}
          {totalCount === 0 ? 'Nothing to show' : `${formatCount(totalCount)} venues`}
        </Text>
      </Flex>
    </StyledCard>
  );
}

export default EventsWorkbench;
