'use client';

/**
 * Approvals workbench.
 *
 * <h2>Design authority</h2>
 * `Admin - Approvals Workbench.dc.html`. That screen is a section-scoped
 * surface: its own left rail with four views — Organizer applications, Event
 * approvals, Escalations, Metrics — rather than a single table.
 *
 * <p>The organizer-applications table's columns are the design's:
 * Organization · Contact · Submitted · Age · Documents · SLA.
 *
 * <h2>What is real and what is not, stated plainly</h2>
 * Organizer applications are wired to `organizationsOffsetPagination` filtered
 * to `PENDING_REVIEW`. The other three views are empty states naming the design
 * file they will be built from — they invent nothing. An admin screen that
 * shows plausible rows it made up is worse than one that admits it is
 * unfinished, because only the second is obvious.
 *
 * <h2>Claim is deliberately absent</h2>
 * The design has a Claim column — a reviewer takes an item so two people do not
 * work it at once. There is no claim model in identity-service, so the column
 * would have nothing behind it. Rendering an inert control is how a team
 * discovers at the worst moment that nobody actually held the lock.
 */

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { Badge, Box, Flex, Table, Text } from '@radix-ui/themes';
import {
  Calendar,
  ClipboardCheck,
  Group,
  StatsReport,
  WarningTriangle,
} from 'iconoir-react';
import { usePendingOrganizations } from '@pml.tickets/shared/api/admin/modules/organization';
import { EmptyState, PageHeader, StyledCard } from '@/components/ui';

// =============================================================================
// SLA — ET-ADM-001 / the design's `admin.sla.organizer` = PT48H
// =============================================================================

const ORGANIZER_SLA_HOURS = 48;

type SlaState = 'ok' | 'warn' | 'breach';

/**
 * How close an application is to breaching its review SLA.
 *
 * The design's three states, and the thresholds it implies: comfortable, inside
 * the last quarter of the window, and past it.
 */
function slaState(hoursWaiting: number): SlaState {
  if (hoursWaiting >= ORGANIZER_SLA_HOURS) return 'breach';
  if (hoursWaiting >= ORGANIZER_SLA_HOURS * 0.75) return 'warn';
  return 'ok';
}

const SLA_COLOR: Record<SlaState, 'green' | 'amber' | 'red'> = {
  ok: 'green',
  warn: 'amber',
  breach: 'red',
};

const SLA_LABEL: Record<SlaState, string> = {
  ok: 'On track',
  warn: 'Due soon',
  breach: 'Breached',
};

/** "3d 4h" / "5h" / "22m" — the age column reads at a glance, not to the second. */
function humanAge(from: string | null | undefined): { label: string; hours: number } {
  if (!from) return { label: '—', hours: 0 };
  const ms = Date.now() - new Date(from).getTime();
  if (Number.isNaN(ms) || ms < 0) return { label: '—', hours: 0 };
  const hours = ms / 3_600_000;
  if (hours < 1) return { label: `${Math.round(hours * 60)}m`, hours };
  if (hours < 24) return { label: `${Math.floor(hours)}h`, hours };
  const days = Math.floor(hours / 24);
  return { label: `${days}d ${Math.floor(hours - days * 24)}h`, hours };
}

function submittedOn(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      });
}

// =============================================================================
// Views — the design's four
// =============================================================================

type View = 'organizers' | 'events' | 'escalations' | 'metrics';

const VIEWS: {
  id: View;
  label: string;
  icon: React.ReactNode;
}[] = [
  { id: 'organizers', label: 'Organizer applications', icon: <Group width={18} height={18} /> },
  { id: 'events', label: 'Event approvals', icon: <Calendar width={18} height={18} /> },
  { id: 'escalations', label: 'Escalations', icon: <WarningTriangle width={18} height={18} /> },
  { id: 'metrics', label: 'Metrics', icon: <StatsReport width={18} height={18} /> },
];

export default function ApprovalsWorkbenchPage() {
  const [view, setView] = useState<View>('organizers');
  const { organizations, totalElements, loading } = usePendingOrganizations();

  return (
    <Box>
      <PageHeader
        title="Approvals workbench"
        description="Everything waiting on a platform decision, oldest first."
        breadcrumbs={[{ label: 'Action center' }, { label: 'All approvals' }]}
      />

      <Flex gap="5" align="start" direction={{ initial: 'column', md: 'row' }}>
        {/* ── Section rail ─────────────────────────────────────────────────
            The design gives this surface its own rail rather than folding the
            four queues into tabs — they are separate bodies of work. */}
        <Box width={{ initial: '100%', md: '232px' }} flexShrink="0" style={{ minWidth: 0 }}>
          <StyledCard hover="none" padding="3">
            <Flex direction="column" gap="1">
              {VIEWS.map((v) => {
                const active = v.id === view;
                return (
                  <Flex
                    key={v.id}
                    data-testid={`approvals-view-${v.id}`}
                    align="center"
                    gap="3"
                    onClick={() => setView(v.id)}
                    style={{
                      padding: 'var(--space-2) var(--space-3)',
                      borderRadius: 'var(--radius-3)',
                      cursor: 'pointer',
                      background: active ? 'var(--accent-a3)' : undefined,
                      color: active ? 'var(--accent-11)' : 'var(--gray-11)',
                      fontWeight: active
                        ? 'var(--weight-semibold)'
                        : 'var(--weight-regular)',
                    }}
                  >
                    {v.icon}
                    <Text size="2" style={{ flex: 1 }}>
                      {v.label}
                    </Text>
                    {v.id === 'organizers' && totalElements > 0 && (
                      <Text as="span" size="1" className="ds-amount">
                        {totalElements}
                      </Text>
                    )}
                  </Flex>
                );
              })}
            </Flex>
          </StyledCard>
        </Box>

        {/* ── The queue ───────────────────────────────────────────────────── */}
        <Box style={{ flex: 1, minWidth: 0, width: '100%' }}>
          {view === 'organizers' && (
            <OrganizerApplications
              organizations={organizations}
              loading={loading}
              total={totalElements}
            />
          )}
          {view === 'events' && (
            <NotYetBuilt
              title="Event approvals"
              design="Admin - Approvals Workbench.dc.html"
              detail="Event · Organizer · Capacity · Age · SLA · Bulk-eligible"
            />
          )}
          {view === 'escalations' && (
            <NotYetBuilt
              title="Escalations"
              design="Admin - Approvals Workbench.dc.html"
              detail="Items past their SLA that have been raised to a senior reviewer."
            />
          )}
          {view === 'metrics' && (
            <NotYetBuilt
              title="Queue health"
              design="Admin - Approvals Workbench.dc.html"
              detail="Throughput, time-to-decision and the share of reviews breaching SLA."
            />
          )}
        </Box>
      </Flex>
    </Box>
  );
}

// =============================================================================
// Organizer applications
// =============================================================================

interface OrgRow {
  id: string;
  /** Nullable in the generated type — an application can be saved before it is named. */
  name: string | null;
  businessEmail?: string | null;
  businessPhone?: string | null;
  documentsVerified?: boolean | null;
  submittedAt?: string | null;
  createdAt?: string | null;
}

function OrganizerApplications({
  organizations,
  loading,
  total,
}: {
  organizations: OrgRow[];
  loading: boolean;
  total: number;
}) {
  const rows = useMemo(
    () =>
      organizations.map((org) => {
        // submittedAt is when the clock starts; createdAt is the fallback for
        // rows written before the field existed.
        const age = humanAge(org.submittedAt ?? org.createdAt);
        return { org, age, sla: slaState(age.hours) };
      }),
    [organizations]
  );

  const breached = rows.filter((r) => r.sla === 'breach').length;

  return (
    <StyledCard hover="none">
      <Flex justify="between" align="center" wrap="wrap" gap="3" mb="1">
        <Text size="3" weight="bold">
          Organizer applications
        </Text>
        <Flex gap="2" align="center">
          {breached > 0 && (
            <Badge color="red" variant="soft">
              {breached} breached SLA
            </Badge>
          )}
          <Text size="1" style={{ color: 'var(--gray-9)' }}>
            {total} awaiting review
          </Text>
        </Flex>
      </Flex>
      <Text as="p" size="1" style={{ color: 'var(--gray-9)' }} mb="3">
        Oldest first. The review SLA is {ORGANIZER_SLA_HOURS} hours from submission.
      </Text>

      <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
        <Table.Root variant="surface">
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Organization</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Contact</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Submitted</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Age</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Documents</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>SLA</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {loading && rows.length === 0 ? (
              <Table.Row>
                <Table.Cell colSpan={6}>
                  <Text size="2" style={{ color: 'var(--gray-9)' }}>
                    Loading the queue…
                  </Text>
                </Table.Cell>
              </Table.Row>
            ) : rows.length === 0 ? (
              <Table.Row>
                <Table.Cell colSpan={6}>
                  <EmptyState
                    size="sm"
                    icon={<ClipboardCheck width={20} height={20} />}
                    title="Nothing waiting"
                    description="No organizer application is currently pending review."
                  />
                </Table.Cell>
              </Table.Row>
            ) : (
              rows.map(({ org, age, sla }) => (
                <Table.Row key={org.id} align="center">
                  <Table.RowHeaderCell>
                    <Link
                      href={`/organizations/${org.id}`}
                      style={{ textDecoration: 'none' }}
                    >
                      <Text weight="medium" style={{ color: 'var(--accent-11)' }}>
                        {org.name ?? 'Unnamed application'}
                      </Text>
                    </Link>
                  </Table.RowHeaderCell>
                  <Table.Cell>
                    <Text as="div" size="2">
                      {org.businessEmail ?? '—'}
                    </Text>
                    {org.businessPhone && (
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {org.businessPhone}
                      </Text>
                    )}
                  </Table.Cell>
                  <Table.Cell>
                    <Text size="2">{submittedOn(org.submittedAt ?? org.createdAt)}</Text>
                  </Table.Cell>
                  <Table.Cell>
                    <Text size="2" className="ds-amount">
                      {age.label}
                    </Text>
                  </Table.Cell>
                  <Table.Cell>
                    {org.documentsVerified ? (
                      <Badge color="green" variant="soft">
                        Verified
                      </Badge>
                    ) : (
                      <Badge color="amber" variant="soft">
                        Awaiting
                      </Badge>
                    )}
                  </Table.Cell>
                  <Table.Cell>
                    <Badge
                      data-testid={`approvals-sla-${org.id}`}
                      color={SLA_COLOR[sla]}
                      variant="soft"
                    >
                      {SLA_LABEL[sla]}
                    </Badge>
                  </Table.Cell>
                </Table.Row>
              ))
            )}
          </Table.Body>
        </Table.Root>
      </Box>
    </StyledCard>
  );
}

// =============================================================================
// Honest placeholder
// =============================================================================

function NotYetBuilt({
  title,
  design,
  detail,
}: {
  title: string;
  design: string;
  detail: string;
}) {
  return (
    <StyledCard hover="none">
      <EmptyState
        size="md"
        icon={<ClipboardCheck width={22} height={22} />}
        title={title}
        description={`${detail} Built from ${design} — not yet implemented.`}
      />
    </StyledCard>
  );
}
