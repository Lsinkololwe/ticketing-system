'use client';

/**
 * Users & organizations.
 *
 * <h2>Design authority</h2>
 * `Admin - Users & Organizations.dc.html` — one section-scoped surface with a
 * three-item rail (All users · Organizers · Organizations), and per view: a
 * stat-tile strip, a table, and a count footer.
 *
 * <h2>Why one component behind three routes</h2>
 * The design is a single screen whose rail switches views. The navigation the
 * same design declares has three separate entries pointing at three hrefs. Both
 * are satisfied by rendering this component at each route with a different
 * `view` — the rail is real navigation rather than local state, so a reviewer
 * can link somebody straight to Organizers.
 *
 * <h2>Suspend is not wired</h2>
 * The design's rows carry Edit and Suspend/Activate. Suspending a user is a
 * real, auditable write against identity-service, and a button that toggles a
 * local flag while the account stays live is worse than no button — it is the
 * exact failure this project keeps finding, a UI reporting success for
 * something that did not happen. The control is present and disabled, with the
 * reason on hover.
 */

import { useMemo } from 'react';
import Link from 'next/link';
import { Badge, Box, Button, Flex, Table, Text, Tooltip } from '@radix-ui/themes';
import { Building, Community, Group } from 'iconoir-react';
import { useAdminUsers } from '@pml.tickets/shared/api/admin/modules/user';
import { usePendingOrganizations } from '@pml.tickets/shared/api/admin/modules/organization';
import { EmptyState, PageHeader, StyledCard } from '@/components/ui';

export type PeopleView = 'users' | 'organizers' | 'organizations';

const VIEWS: {
  id: PeopleView;
  label: string;
  href: string;
  icon: React.ReactNode;
}[] = [
  { id: 'users', label: 'All users', href: '/users', icon: <Group width={18} height={18} /> },
  { id: 'organizers', label: 'Organizers', href: '/organizers', icon: <Building width={18} height={18} /> },
  { id: 'organizations', label: 'Organizations', href: '/organizations', icon: <Community width={18} height={18} /> },
];

const TITLES: Record<PeopleView, { title: string; description: string }> = {
  users: { title: 'All users', description: 'Everyone with an account, newest first.' },
  organizers: { title: 'Organizers', description: 'Accounts holding the organizer role.' },
  organizations: {
    title: 'Organizations',
    description: 'Registered organizations and their review state.',
  },
};

/** `PENDING_REVIEW` → "Pending review". Statuses are humanised, never raw. */
function humanise(value: string | null | undefined): string {
  if (!value) return '—';
  const lower = value.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

function statusColor(status: string | null | undefined): 'green' | 'amber' | 'red' | 'gray' {
  switch (status) {
    case 'ACTIVE':
    case 'APPROVED':
      return 'green';
    case 'PENDING_REVIEW':
    case 'CHANGES_REQUESTED':
      return 'amber';
    case 'SUSPENDED':
    case 'REJECTED':
    case 'LOCKED':
      return 'red';
    default:
      return 'gray';
  }
}

function shortDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function PeopleWorkbench({ view }: { view: PeopleView }) {
  // Users view queries users; the organizations view queries organizations.
  // `role: ORGANIZER` is what separates the organizers view from all users.
  const users = useAdminUsers(view === 'organizers' ? { role: 'ORGANIZER' } : {});
  const orgs = usePendingOrganizations({ size: 20 });

  const heading = TITLES[view];

  return (
    <Box>
      <PageHeader
        title={heading.title}
        description={heading.description}
        breadcrumbs={[{ label: 'Users' }, { label: heading.title }]}
      />

      <Flex gap="5" align="start" direction={{ initial: 'column', md: 'row' }}>
        {/* ── Section rail ───────────────────────────────────────────────── */}
        <Box width={{ initial: '100%', md: '224px' }} flexShrink="0" style={{ minWidth: 0 }}>
          <StyledCard hover="none" padding="3">
            <Flex direction="column" gap="1">
              {VIEWS.map((v) => {
                const active = v.id === view;
                return (
                  <Link key={v.id} href={v.href} style={{ textDecoration: 'none' }}>
                    <Flex
                      data-testid={`people-view-${v.id}`}
                      align="center"
                      gap="3"
                      style={{
                        padding: 'var(--space-2) var(--space-3)',
                        borderRadius: 'var(--radius-3)',
                        // The design marks the active row with a 2px left accent
                        // border plus a tinted background.
                        borderLeft: `2px solid ${active ? 'var(--accent-9)' : 'transparent'}`,
                        background: active ? 'var(--accent-a3)' : undefined,
                        color: active ? 'var(--accent-11)' : 'var(--gray-11)',
                        fontWeight: active ? 'var(--weight-medium)' : 'var(--weight-regular)',
                      }}
                    >
                      {v.icon}
                      <Text size="2">{v.label}</Text>
                    </Flex>
                  </Link>
                );
              })}
            </Flex>
          </StyledCard>
        </Box>

        <Box style={{ flex: 1, minWidth: 0, width: '100%' }}>
          {view === 'organizations' ? (
            <OrganizationsTable
              rows={orgs.organizations}
              total={orgs.totalElements}
              loading={orgs.loading}
            />
          ) : (
            <UsersTable
              rows={users.users}
              total={users.totalCount}
              loading={users.loading}
              organizersOnly={view === 'organizers'}
            />
          )}
        </Box>
      </Flex>
    </Box>
  );
}

// =============================================================================
// Stat tiles — the design's `auto-fit minmax(150px, 1fr)` strip
// =============================================================================

function StatStrip({ tiles }: { tiles: { label: string; value: string; color?: string }[] }) {
  return (
    <Box
      data-testid="people-stat-strip"
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
  );
}

// =============================================================================
// Users
// =============================================================================

interface UserRow {
  id: string;
  fullName?: string | null;
  email?: string | null;
  phoneNumber?: string | null;
  roles?: readonly (string | null)[] | null;
  accountStatus?: string | null;
  memberSince?: string | null;
  createdAt?: string | null;
}

function UsersTable({
  rows,
  total,
  loading,
  organizersOnly,
}: {
  rows: UserRow[];
  total: number;
  loading: boolean;
  organizersOnly: boolean;
}) {
  const tiles = useMemo(() => {
    const suspended = rows.filter((u) => u.accountStatus === 'SUSPENDED').length;
    return [
      { label: organizersOnly ? 'Organizers' : 'Total users', value: String(total) },
      { label: 'On this page', value: String(rows.length), color: 'var(--blue-11)' },
      {
        label: 'Suspended here',
        value: String(suspended),
        color: suspended > 0 ? 'var(--red-11)' : 'var(--gray-12)',
      },
    ];
  }, [rows, total, organizersOnly]);

  return (
    <>
      <StatStrip tiles={tiles} />
      <StyledCard hover="none">
        <Flex justify="between" align="center" mb="3" wrap="wrap" gap="2">
          <Text size="3" weight="bold">
            {organizersOnly ? 'Organizers' : 'All users'}
          </Text>
          <Text size="1" style={{ color: 'var(--gray-9)' }}>
            Showing {rows.length} of {total}
          </Text>
        </Flex>

        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>User</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Role</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Joined</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell align="right">Actions</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && rows.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : rows.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <EmptyState
                      size="sm"
                      icon={<Group width={20} height={20} />}
                      title={organizersOnly ? 'No organizers yet' : 'No users yet'}
                      description="Accounts appear here as soon as somebody registers."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                rows.map((u) => (
                  <Table.Row key={u.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium">
                        {u.fullName ?? '—'}
                      </Text>
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {u.email ?? u.phoneNumber ?? '—'}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Flex gap="1" wrap="wrap">
                        {(u.roles ?? []).filter(Boolean).map((r) => (
                          <Badge key={r} color="gray" variant="soft">
                            {humanise(r)}
                          </Badge>
                        ))}
                      </Flex>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2">{shortDate(u.memberSince ?? u.createdAt)}</Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Badge
                        data-testid={`people-status-${u.id}`}
                        color={statusColor(u.accountStatus)}
                        variant="soft"
                      >
                        {humanise(u.accountStatus)}
                      </Badge>
                    </Table.Cell>
                    <Table.Cell align="right">
                      <SuspendControl id={u.id} />
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>
      </StyledCard>
    </>
  );
}

// =============================================================================
// Organizations
// =============================================================================

interface OrgRow {
  id: string;
  name?: string | null;
  businessEmail?: string | null;
  status?: string | null;
  verified?: boolean | null;
  documentsVerified?: boolean | null;
  submittedAt?: string | null;
  createdAt?: string | null;
}

function OrganizationsTable({
  rows,
  total,
  loading,
}: {
  rows: OrgRow[];
  total: number;
  loading: boolean;
}) {
  const tiles = useMemo(
    () => [
      { label: 'Awaiting review', value: String(total), color: 'var(--amber-11)' },
      {
        label: 'Documents verified',
        value: String(rows.filter((o) => o.documentsVerified).length),
        color: 'var(--green-11)',
      },
      { label: 'On this page', value: String(rows.length) },
    ],
    [rows, total]
  );

  return (
    <>
      <StatStrip tiles={tiles} />
      <StyledCard hover="none">
        <Flex justify="between" align="center" mb="1" wrap="wrap" gap="2">
          <Text size="3" weight="bold">
            Organizations
          </Text>
          <Text size="1" style={{ color: 'var(--gray-9)' }}>
            Showing {rows.length} of {total}
          </Text>
        </Flex>
        <Text as="p" size="1" style={{ color: 'var(--gray-9)' }} mb="3">
          Organizations awaiting platform review. Decisions are taken in the
          approvals workbench.
        </Text>

        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Organization</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Documents</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Since</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell align="right">Actions</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && rows.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : rows.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <EmptyState
                      size="sm"
                      icon={<Community width={20} height={20} />}
                      title="No organizations awaiting review"
                      description="Applications appear here the moment they are submitted."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                rows.map((o) => (
                  <Table.Row key={o.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium">
                        {o.name ?? 'Unnamed organization'}
                      </Text>
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {o.businessEmail ?? '—'}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Badge color={o.documentsVerified ? 'green' : 'amber'} variant="soft">
                        {o.documentsVerified ? 'Verified' : 'Awaiting'}
                      </Badge>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2">{shortDate(o.submittedAt ?? o.createdAt)}</Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Badge
                        data-testid={`people-status-${o.id}`}
                        color={statusColor(o.status)}
                        variant="soft"
                      >
                        {humanise(o.status)}
                      </Badge>
                    </Table.Cell>
                    <Table.Cell align="right">
                      <Link href="/approvals" style={{ textDecoration: 'none' }}>
                        <Button
                          data-testid={`people-review-${o.id}`}
                          variant="soft"
                          color="gray"
                          size="1"
                        >
                          Review
                        </Button>
                      </Link>
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>
      </StyledCard>
    </>
  );
}

/**
 * The design's Suspend control, disabled and saying why.
 *
 * Suspending an account is an auditable write against identity-service. A
 * control that flips a local flag while the account stays live is the failure
 * mode this project keeps finding — a UI reporting success for a thing that did
 * not happen.
 */
function SuspendControl({ id }: { id: string }) {
  return (
    <Tooltip content="Suspension is not wired to identity-service yet — the control is inert rather than misleading.">
      <Button
        data-testid={`people-suspend-${id}`}
        variant="soft"
        color="gray"
        size="1"
        disabled
      >
        Suspend
      </Button>
    </Tooltip>
  );
}
