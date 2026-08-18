'use client';

/**
 * Financial operations.
 *
 * <h2>Design authority</h2>
 * `Admin - Finance.dc.html` — a section-scoped surface with its own three-item
 * rail (Payout requests · Refund requests · Escrow accounts), and per view: a
 * four-tile stat strip, a table, a pagination footer, and a right-hand detail
 * drawer carrying the approve/reject decision.
 *
 * <h2>One component behind three routes</h2>
 * Same arrangement as {@code PeopleWorkbench}: the design is one screen whose
 * rail switches views, while the navigation the same design declares has three
 * separate entries. Rendering this at each route with a different `view` makes
 * the rail real navigation, so a reviewer can be linked straight to Escrow.
 *
 * <h2>What the decisions actually do</h2>
 * Approve and Reject call `approve/rejectPayoutRequest` and
 * `approve/rejectRefundRequest`. Approving a payout AUTHORISES it — money moves
 * later, at `processPayoutRequest` and `completePayoutRequest`. The copy says
 * "Approve", never "Pay", because a screen that implies funds have left when
 * they have not is how a reconciliation goes wrong.
 *
 * <p>Rejection requires a reason and the button stays disabled until one is
 * typed. The server declares `rejectionReason: String!`; sending a placeholder
 * to satisfy that non-null would put "n/a" in front of an organizer who has
 * just been refused their money.
 *
 * <h2>Two deliberate departures from the design</h2>
 * <ul>
 *   <li>The escrow drawer's "open disputes are blocking payout" warning is not
 *       rendered. `EventEscrowAccount` exposes no dispute count, so the banner
 *       could only ever be decoration — and a payout-blocking warning that is
 *       decoration is worse than none.</li>
 *   <li>The refund stat strip shows counts, not the design's "Auto-approve
 *       threshold" and "Fee borne by". Those are policy constants no query
 *       returns; hardcoding them would put two invented numbers on a finance
 *       screen.</li>
 * </ul>
 */

import { useCallback, useMemo, useState } from 'react';
import Link from 'next/link';
import { Box, Flex, Table, Text, TextArea } from '@radix-ui/themes';
import { Safe, SendDiagonal, Undo, WarningTriangle, Xmark } from 'iconoir-react';
import {
  useAdminEscrowAccounts,
  useAdminPayoutRequests,
  useAdminRefundRequests,
  useFinanceDecisions,
  usePayoutRequestStats,
  useRefundStatusCount,
  type FinancePageInfo,
} from '@pml.tickets/shared/api/admin/modules/finance';
import {
  Amount,
  Badge,
  Button,
  EmptyState,
  PageHeader,
  StyledCard,
  Toast,
} from '@/components/ui';
import { formatCount, humanizeEnum, statusTone } from '@/lib/format';

export type FinanceView = 'payouts' | 'refunds' | 'escrow';

const VIEWS: {
  id: FinanceView;
  label: string;
  href: string;
  icon: React.ReactNode;
}[] = [
  {
    id: 'payouts',
    label: 'Payout requests',
    href: '/finance/payouts',
    icon: <SendDiagonal width={18} height={18} />,
  },
  {
    id: 'refunds',
    label: 'Refund requests',
    href: '/finance/refunds',
    icon: <Undo width={18} height={18} />,
  },
  {
    id: 'escrow',
    label: 'Escrow accounts',
    href: '/finance/escrow',
    icon: <Safe width={18} height={18} />,
  },
];

const TITLES: Record<FinanceView, { title: string; description: string }> = {
  payouts: {
    title: 'Payout requests',
    description: 'Organizer withdrawals awaiting a platform decision, newest first.',
  },
  refunds: {
    title: 'Refund requests',
    description: 'Customer refunds across every event, newest first.',
  },
  escrow: {
    title: 'Escrow accounts',
    description: 'One account per event. Balance is a projection of the journal.',
  },
};

/**
 * A status chip with a test hook.
 *
 * The DS `Badge` is a §7 contract component with a closed prop set — it takes
 * no `data-testid`, and adding one would break the adherence lint. The hook
 * goes on a wrapping span instead, so the contract stays intact.
 */
function StatusBadge({
  status,
  testId,
}: {
  status: string | null | undefined;
  testId: string;
}) {
  return (
    <span data-testid={testId}>
      <Badge color={statusTone(status)} variant="soft">
        {humanizeEnum(status)}
      </Badge>
    </span>
  );
}

function shortDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? '—'
    : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

// =============================================================================
// Shell
// =============================================================================

export function FinanceWorkbench({ view }: { view: FinanceView }) {
  const [toast, setToast] = useState<{ variant: 'success' | 'error'; title: string } | null>(null);
  const heading = TITLES[view];

  const announce = useCallback((variant: 'success' | 'error', title: string) => {
    setToast({ variant, title });
  }, []);

  const payoutStats = usePayoutRequestStats();
  const pendingRefunds = useRefundStatusCount('PENDING');

  return (
    <Box>
      <Flex justify="between" align="start" wrap="wrap" gap="3">
        <PageHeader
          title={heading.title}
          description={heading.description}
          breadcrumbs={[{ label: 'Financial ops' }, { label: heading.title }]}
        />
        {/* The design puts a copper FINANCE chip in this bar. Copper is the
            highlight role — it marks the section as money-handling; jade stays
            reserved for amounts. */}
        <Flex
          data-testid="finance-scope-chip"
          align="center"
          gap="2"
          style={{
            color: 'var(--copper-11)',
            background: 'var(--copper-3)',
            border: '1px solid var(--copper-6)',
            padding: 'var(--space-1) var(--space-3)',
            borderRadius: 'var(--radius-2)',
            fontSize: 'var(--text-1-size)',
            fontWeight: 'var(--weight-bold)',
            letterSpacing: '0.04em',
          }}
        >
          FINANCE
        </Flex>
      </Flex>

      <Flex gap="5" align="start" direction={{ initial: 'column', md: 'row' }}>
        {/* ── Section rail ───────────────────────────────────────────────── */}
        <Box width={{ initial: '100%', md: '232px' }} flexShrink="0" style={{ minWidth: 0 }}>
          <StyledCard hover="none" padding="3">
            <Flex direction="column" gap="1">
              {VIEWS.map((v) => {
                const active = v.id === view;
                // The rail badges count what is waiting on a person, which is
                // the only number worth interrupting someone for.
                const badge =
                  v.id === 'payouts'
                    ? payoutStats.stats?.pendingPayoutRequests ?? null
                    : v.id === 'refunds'
                      ? pendingRefunds.count
                      : null;

                return (
                  <Link key={v.id} href={v.href} style={{ textDecoration: 'none' }}>
                    <Flex
                      data-testid={`finance-view-${v.id}`}
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
          {view === 'payouts' && <PayoutsView announce={announce} />}
          {view === 'refunds' && <RefundsView announce={announce} />}
          {view === 'escrow' && <EscrowView announce={announce} />}
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

type Announce = (variant: 'success' | 'error', title: string) => void;

// =============================================================================
// Stat strip — the design's `auto-fit minmax(150px, 1fr)`
// =============================================================================

interface Tile {
  label: string;
  value: string;
  color?: string;
}

function StatStrip({ tiles, testId }: { tiles: Tile[]; testId: string }) {
  return (
    <Box
      data-testid={testId}
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

/** Em dash until the query lands — never a zero the data has not earned. */
function tileNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : formatCount(value);
}

// =============================================================================
// Pagination footer
// =============================================================================

function Pager({
  pageInfo,
  onPage,
  testId,
}: {
  pageInfo: FinancePageInfo;
  onPage: (page: number) => void;
  testId: string;
}) {
  // A pager over a single page is decoration. The count line still shows.
  if (pageInfo.totalPages <= 1) {
    return (
      <Flex justify="end" pt="3">
        <Text size="1" style={{ color: 'var(--gray-9)' }} data-testid={`${testId}-count`}>
          {pageInfo.totalCount === 0
            ? 'Nothing to show'
            : `Showing ${pageInfo.totalCount} of ${pageInfo.totalCount}`}
        </Text>
      </Flex>
    );
  }

  const current = pageInfo.currentPage;
  const numbers = Array.from({ length: Math.min(4, pageInfo.totalPages) }, (_, i) => i);

  return (
    <Flex justify="between" align="center" wrap="wrap" gap="3" pt="3" data-testid={testId}>
      <Text size="1" style={{ color: 'var(--gray-9)' }} data-testid={`${testId}-count`}>
        Page {current + 1} of {pageInfo.totalPages} · {formatCount(pageInfo.totalCount)} total
      </Text>
      <Flex gap="2">
        <Button
          variant="outline"
          size="1"
          disabled={!pageInfo.hasPreviousPage}
          onClick={() => onPage(Math.max(0, current - 1))}
          data-testid={`${testId}-prev`}
        >
          Prev
        </Button>
        {numbers.map((n) => (
          <Button
            key={n}
            variant={n === current ? 'solid' : 'outline'}
            size="1"
            onClick={() => onPage(n)}
            data-testid={`${testId}-page-${n}`}
          >
            {String(n + 1)}
          </Button>
        ))}
        <Button
          variant="outline"
          size="1"
          disabled={!pageInfo.hasNextPage}
          onClick={() => onPage(current + 1)}
          data-testid={`${testId}-next`}
        >
          Next
        </Button>
      </Flex>
    </Flex>
  );
}

// =============================================================================
// Payouts
// =============================================================================

function PayoutsView({ announce }: { announce: Announce }) {
  const [page, setPage] = useState(0);
  const { payouts, pageInfo, loading, refetch } = useAdminPayoutRequests({ page });
  const { stats } = usePayoutRequestStats();
  const [active, setActive] = useState<(typeof payouts)[number] | null>(null);

  const tiles: Tile[] = [
    { label: 'Pending', value: tileNumber(stats?.pendingPayoutRequests), color: 'var(--amber-11)' },
    {
      label: 'Pending value',
      value: stats ? `K ${formatCount(Number(stats.pendingPayoutAmount ?? 0))}` : '—',
      color: 'var(--color-money-text)',
    },
    { label: 'Approved', value: tileNumber(stats?.approvedPayoutRequests) },
    {
      label: 'Paid out',
      value: stats ? `K ${formatCount(Number(stats.totalPayoutAmount ?? 0))}` : '—',
      color: 'var(--color-money-text)',
    },
  ];

  return (
    <>
      <StatStrip tiles={tiles} testId="finance-payout-tiles" />

      <StyledCard hover="none">
        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Organizer</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Amount</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Requested</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Method</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell />
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && payouts.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={6}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading payout requests…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : payouts.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={6}>
                    <EmptyState
                      size="sm"
                      icon={<SendDiagonal width={20} height={20} />}
                      title="No payout requests"
                      description="No organizer has requested a payout yet."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                payouts.map((p) => (
                  <Table.Row key={p.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium" size="2">
                        {p.organizerName ?? p.organizerId}
                      </Text>
                      {p.eventTitle && (
                        <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                          {p.eventTitle}
                        </Text>
                      )}
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Amount value={p.requestedAmount} tone="money" />
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" className="ds-amount">
                        {shortDate(p.requestedAt)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2">{humanizeEnum(p.payoutMethod)}</Text>
                    </Table.Cell>
                    <Table.Cell>
                      <StatusBadge
                        status={p.status}
                        testId={`finance-payout-status-${p.id}`}
                      />
                    </Table.Cell>
                    <Table.Cell>
                      <Flex justify="end">
                        <Button
                          variant="outline"
                          size="1"
                          onClick={() => setActive(p)}
                          data-testid={`finance-payout-view-${p.id}`}
                        >
                          View details
                        </Button>
                      </Flex>
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>
        <Pager pageInfo={pageInfo} onPage={setPage} testId="finance-payout-pager" />
      </StyledCard>

      {active && (
        <DecisionDrawer
          kind="Payout request"
          title={active.organizerName ?? active.organizerId}
          reference={active.requestId}
          status={active.status}
          pending={active.status === 'PENDING'}
          approveLabel="Approve payout"
          fields={[
            { label: 'Requested', value: <Amount value={active.requestedAmount} tone="money" /> },
            { label: 'Settled amount', value: <Amount value={active.settledAmount} /> },
            { label: 'Method', value: humanizeEnum(active.payoutMethod) },
            { label: 'Bank', value: active.bankName ?? '—' },
            { label: 'Event', value: active.eventTitle ?? 'Whole account' },
            { label: 'Currency', value: active.currency ?? '—' },
          ]}
          timeline={[
            { action: 'Payout requested', time: shortDate(active.requestedAt) },
            active.approvedAt ? { action: 'Approved', time: shortDate(active.approvedAt) } : null,
            active.rejectedAt
              ? {
                  action: `Rejected — ${active.rejectionReason ?? 'no reason recorded'}`,
                  time: shortDate(active.rejectedAt),
                }
              : null,
            active.processedAt ? { action: 'Processed', time: shortDate(active.processedAt) } : null,
          ]}
          note="Approving authorises the payout. Funds move when it is processed and completed against a bank reference."
          testId="finance-payout-drawer"
          onClose={() => setActive(null)}
          onDecide={async (decision, reason, decisions) => {
            const result =
              decision === 'approve'
                ? await decisions.approvePayout(active.id, reason || undefined)
                : await decisions.rejectPayout(active.id, reason);
            if (result.success) {
              announce('success', `Payout ${decision === 'approve' ? 'approved' : 'rejected'}.`);
              setActive(null);
              refetch();
            } else {
              announce('error', result.errors[0] ?? result.message ?? 'The decision was refused.');
            }
          }}
        />
      )}
    </>
  );
}

// =============================================================================
// Refunds
// =============================================================================

function RefundsView({ announce }: { announce: Announce }) {
  const [page, setPage] = useState(0);
  const { refunds, pageInfo, loading, refetch } = useAdminRefundRequests({ page });
  const pending = useRefundStatusCount('PENDING');
  const approved = useRefundStatusCount('APPROVED');
  const completed = useRefundStatusCount('COMPLETED');
  const all = useRefundStatusCount(null);
  const [active, setActive] = useState<(typeof refunds)[number] | null>(null);

  const tiles: Tile[] = [
    { label: 'Pending', value: tileNumber(pending.count), color: 'var(--amber-11)' },
    { label: 'Approved', value: tileNumber(approved.count) },
    { label: 'Completed', value: tileNumber(completed.count), color: 'var(--color-money-text)' },
    { label: 'All requests', value: tileNumber(all.count) },
  ];

  return (
    <>
      <StatStrip tiles={tiles} testId="finance-refund-tiles" />

      <StyledCard hover="none">
        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Ticket</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Amount</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Reason</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Requested</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell />
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && refunds.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={6}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading refund requests…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : refunds.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={6}>
                    <EmptyState
                      size="sm"
                      icon={<Undo width={20} height={20} />}
                      title="No refund requests"
                      description="No customer has requested a refund yet."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                refunds.map((r) => (
                  <Table.Row key={r.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium" size="2" className="ds-amount">
                        {r.ticketNumber}
                      </Text>
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {humanizeEnum(r.requestType)}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Amount value={r.refundAmount} tone="money" />
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2">{r.reason ?? '—'}</Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" className="ds-amount">
                        {shortDate(r.requestedAt)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <StatusBadge
                        status={r.status}
                        testId={`finance-refund-status-${r.id}`}
                      />
                    </Table.Cell>
                    <Table.Cell>
                      <Flex justify="end">
                        <Button
                          variant="outline"
                          size="1"
                          onClick={() => setActive(r)}
                          data-testid={`finance-refund-view-${r.id}`}
                        >
                          View details
                        </Button>
                      </Flex>
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>
        <Pager pageInfo={pageInfo} onPage={setPage} testId="finance-refund-pager" />
      </StyledCard>

      {active && (
        <DecisionDrawer
          kind="Refund request"
          title={active.ticketNumber}
          reference={active.requestId}
          status={active.status}
          pending={active.status === 'PENDING'}
          approveLabel="Approve refund"
          fields={[
            { label: 'Refund amount', value: <Amount value={active.refundAmount} tone="money" /> },
            { label: 'Net to customer', value: <Amount value={active.netRefundAmount} /> },
            { label: 'Processing fee', value: <Amount value={active.processingFee} /> },
            { label: 'Type', value: humanizeEnum(active.requestType) },
            { label: 'Policy applied', value: active.policyApplied ?? '—' },
            { label: 'Reason', value: active.reason ?? '—' },
          ]}
          timeline={[
            { action: 'Refund requested', time: shortDate(active.requestedAt) },
            active.reviewedAt ? { action: 'Reviewed', time: shortDate(active.reviewedAt) } : null,
            active.processedAt ? { action: 'Processed', time: shortDate(active.processedAt) } : null,
          ]}
          note="Approving marks the refund payable. It is sent to the payment provider when processed."
          testId="finance-refund-drawer"
          onClose={() => setActive(null)}
          onDecide={async (decision, reason, decisions) => {
            const result =
              decision === 'approve'
                ? await decisions.approveRefund(active.id, reason || undefined)
                : await decisions.rejectRefund(active.id, reason);
            if (result.success) {
              announce('success', `Refund ${decision === 'approve' ? 'approved' : 'rejected'}.`);
              setActive(null);
              refetch();
            } else {
              announce('error', result.errors[0] ?? result.message ?? 'The decision was refused.');
            }
          }}
        />
      )}
    </>
  );
}

// =============================================================================
// Escrow
// =============================================================================

function EscrowView({ announce }: { announce: Announce }) {
  const [page, setPage] = useState(0);
  const { accounts, pageInfo, loading, refetch } = useAdminEscrowAccounts({ page });
  const [active, setActive] = useState<(typeof accounts)[number] | null>(null);

  const tiles: Tile[] = useMemo(() => {
    // Counted from the loaded page and labelled as such. Escrow has no
    // per-status count query, and a page total presented as a platform total is
    // the kind of number that gets quoted in a meeting.
    const on = (status: string) => accounts.filter((a) => a.status === status).length;
    return [
      { label: 'Accounts', value: tileNumber(pageInfo.totalCount) },
      { label: 'Active (this page)', value: tileNumber(on('ACTIVE')) },
      { label: 'On hold (this page)', value: tileNumber(on('HOLD')), color: 'var(--amber-11)' },
      {
        label: 'Payout eligible (this page)',
        value: tileNumber(on('PAYOUT_ELIGIBLE')),
        color: 'var(--color-money-text)',
      },
    ];
  }, [accounts, pageInfo.totalCount]);

  return (
    <>
      <StatStrip tiles={tiles} testId="finance-escrow-tiles" />

      <StyledCard hover="none">
        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Event</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Balance</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Hold until</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell />
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && accounts.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading escrow accounts…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : accounts.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <EmptyState
                      size="sm"
                      icon={<Safe width={20} height={20} />}
                      title="No escrow accounts"
                      description="An escrow account opens when an event is published."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                accounts.map((a) => (
                  <Table.Row key={a.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium" size="2">
                        {a.eventTitle ?? a.eventId}
                      </Text>
                      <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                        {a.organizerName ?? a.organizerId}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Amount value={a.currentBalance} tone="money" />
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" className="ds-amount">
                        {shortDate(a.lockUntil)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <StatusBadge
                        status={a.status}
                        testId={`finance-escrow-status-${a.id}`}
                      />
                    </Table.Cell>
                    <Table.Cell>
                      <Flex justify="end">
                        <Button
                          variant="outline"
                          size="1"
                          onClick={() => setActive(a)}
                          data-testid={`finance-escrow-view-${a.id}`}
                        >
                          View details
                        </Button>
                      </Flex>
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>
        <Pager pageInfo={pageInfo} onPage={setPage} testId="finance-escrow-pager" />
      </StyledCard>

      {active && (
        <EscrowDrawer
          account={active}
          onClose={() => setActive(null)}
          onDone={(ok, message) => {
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
// Drawers
// =============================================================================

function DrawerShell({
  kind,
  title,
  reference,
  children,
  footer,
  testId,
  onClose,
}: {
  kind: string;
  title: string;
  reference?: string | null;
  children: React.ReactNode;
  footer?: React.ReactNode;
  testId: string;
  onClose: () => void;
}) {
  return (
    <>
      <Box
        onClick={onClose}
        aria-hidden="true"
        style={{ position: 'fixed', inset: 0, background: 'var(--color-overlay)', zIndex: 60 }}
      />
      <Flex
        data-testid={testId}
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
              {kind}
            </Text>
            <Text as="div" size="5" weight="bold">
              {title}
            </Text>
            {reference && (
              <Text as="div" size="1" className="ds-amount" style={{ color: 'var(--gray-9)' }}>
                {reference}
              </Text>
            )}
          </Box>
          <Button
            variant="ghost"
            size="1"
            onClick={onClose}
            icon={<Xmark width={16} height={16} />}
            data-testid={`${testId}-close`}
          >
            Close
          </Button>
        </Flex>

        <Box p="5" style={{ flex: 1, overflowY: 'auto' }}>
          {children}
        </Box>

        {footer && (
          <Box p="5" style={{ borderTop: '1px solid var(--gray-a5)' }}>
            {footer}
          </Box>
        )}
      </Flex>
    </>
  );
}

function FieldList({
  fields,
}: {
  fields: { label: string; value: React.ReactNode }[];
}) {
  return (
    <Flex direction="column">
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
  );
}

function Timeline({ entries }: { entries: ({ action: string; time: string } | null)[] }) {
  const real = entries.filter(Boolean) as { action: string; time: string }[];
  if (real.length === 0) return null;

  return (
    <>
      <Text className="ds-label" as="div" mt="5" mb="2">
        Timeline
      </Text>
      <Flex direction="column">
        {real.map((t) => (
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
  );
}

function DecisionDrawer({
  kind,
  title,
  reference,
  status,
  pending,
  approveLabel,
  fields,
  timeline,
  note,
  testId,
  onClose,
  onDecide,
}: {
  kind: string;
  title: string;
  reference?: string | null;
  status?: string | null;
  pending: boolean;
  approveLabel: string;
  fields: { label: string; value: React.ReactNode }[];
  timeline: ({ action: string; time: string } | null)[];
  note: string;
  testId: string;
  onClose: () => void;
  onDecide: (
    decision: 'approve' | 'reject',
    reason: string,
    decisions: ReturnType<typeof useFinanceDecisions>
  ) => Promise<void>;
}) {
  const decisions = useFinanceDecisions();
  const [reason, setReason] = useState('');
  const [rejecting, setRejecting] = useState(false);

  const canReject = reason.trim().length > 0;

  return (
    <DrawerShell
      kind={kind}
      title={title}
      reference={reference}
      testId={testId}
      onClose={onClose}
      footer={
        pending ? (
          <Flex direction="column" gap="3">
            {rejecting && (
              <TextArea
                data-testid={`${testId}-reason`}
                placeholder="Why is this being rejected? The requester is shown this."
                value={reason}
                onChange={(e) => setReason(e.currentTarget.value)}
                rows={3}
              />
            )}
            <Flex gap="2" justify="end">
              {rejecting ? (
                <>
                  <Button
                    variant="ghost"
                    size="2"
                    onClick={() => {
                      setRejecting(false);
                      setReason('');
                    }}
                    data-testid={`${testId}-reject-cancel`}
                  >
                    Cancel
                  </Button>
                  <Button
                    variant="solid"
                    color="red"
                    size="2"
                    // The server requires a reason. Disabling here means the UI
                    // cannot invent one to get past the non-null.
                    disabled={!canReject || decisions.submitting}
                    onClick={() => onDecide('reject', reason.trim(), decisions)}
                    data-testid={`${testId}-reject-confirm`}
                  >
                    Confirm rejection
                  </Button>
                </>
              ) : (
                <>
                  <Button
                    variant="outline"
                    color="red"
                    size="2"
                    disabled={decisions.submitting}
                    onClick={() => setRejecting(true)}
                    data-testid={`${testId}-reject`}
                  >
                    Reject
                  </Button>
                  <Button
                    variant="solid"
                    size="2"
                    disabled={decisions.submitting}
                    onClick={() => onDecide('approve', reason.trim(), decisions)}
                    data-testid={`${testId}-approve`}
                  >
                    {approveLabel}
                  </Button>
                </>
              )}
            </Flex>
          </Flex>
        ) : null
      }
    >
      <StatusBadge status={status} testId={`${testId}-status`} />

      <Box mt="4">
        <FieldList fields={fields} />
      </Box>

      <Timeline entries={timeline} />

      <Text
        as="p"
        size="1"
        mt="5"
        style={{ color: 'var(--gray-9)', lineHeight: 'var(--line-height-relaxed, 1.6)' }}
      >
        {note}
      </Text>
    </DrawerShell>
  );
}

function EscrowDrawer({
  account,
  onClose,
  onDone,
}: {
  account: {
    id: string;
    accountNumber?: string | null;
    eventId: string;
    eventTitle?: string | null;
    organizerName?: string | null;
    organizerId: string;
    currentBalance?: string | null;
    totalDeposits?: string | null;
    totalWithdrawals?: string | null;
    totalRefunds?: string | null;
    totalCommissions?: string | null;
    lockUntil?: string | null;
    payoutEligibleAt?: string | null;
    status?: string | null;
  };
  onClose: () => void;
  onDone: (ok: boolean, message: string) => void;
}) {
  const decisions = useFinanceDecisions();
  const [reason, setReason] = useState('');
  const [confirming, setConfirming] = useState<'SUSPENDED' | 'ACTIVE' | null>(null);

  const suspended = account.status === 'SUSPENDED';
  const closed = account.status === 'CLOSED';
  const canReason = reason.trim().length > 0;

  const apply = async (status: 'SUSPENDED' | 'ACTIVE') => {
    const result = await decisions.setEscrowStatus(account.id, status, reason.trim());
    onDone(
      result.success,
      result.success
        ? status === 'SUSPENDED'
          ? 'Escrow suspended — reason recorded.'
          : 'Escrow reactivated.'
        : result.errors[0] ?? result.message ?? 'The status change was refused.'
    );
  };

  return (
    <DrawerShell
      kind="Escrow account"
      title={account.eventTitle ?? account.eventId}
      reference={account.accountNumber}
      testId="finance-escrow-drawer"
      onClose={onClose}
      footer={
        closed ? null : (
          <Flex direction="column" gap="3">
            {confirming && (
              <TextArea
                data-testid="finance-escrow-drawer-reason"
                placeholder="Why? This is recorded against the account."
                value={reason}
                onChange={(e) => setReason(e.currentTarget.value)}
                rows={3}
              />
            )}
            <Flex gap="2" justify="end">
              {confirming ? (
                <>
                  <Button
                    variant="ghost"
                    size="2"
                    onClick={() => {
                      setConfirming(null);
                      setReason('');
                    }}
                    data-testid="finance-escrow-drawer-cancel"
                  >
                    Cancel
                  </Button>
                  <Button
                    variant="solid"
                    color={confirming === 'SUSPENDED' ? 'red' : 'accent'}
                    size="2"
                    disabled={!canReason || decisions.submitting}
                    onClick={() => apply(confirming)}
                    data-testid="finance-escrow-drawer-confirm"
                  >
                    {confirming === 'SUSPENDED' ? 'Confirm suspension' : 'Confirm reactivation'}
                  </Button>
                </>
              ) : suspended ? (
                <Button
                  variant="solid"
                  size="2"
                  onClick={() => setConfirming('ACTIVE')}
                  data-testid="finance-escrow-drawer-reactivate"
                >
                  Reactivate
                </Button>
              ) : (
                <Button
                  variant="outline"
                  color="red"
                  size="2"
                  onClick={() => setConfirming('SUSPENDED')}
                  data-testid="finance-escrow-drawer-suspend"
                >
                  Suspend
                </Button>
              )}
            </Flex>
          </Flex>
        )
      }
    >
      <StatusBadge status={account.status} testId="finance-escrow-drawer-status" />

      {suspended && (
        <Flex
          gap="2"
          align="center"
          mt="3"
          p="3"
          style={{
            background: 'var(--red-2)',
            border: '1px solid var(--red-6)',
            borderRadius: 'var(--radius-3)',
            color: 'var(--red-11)',
          }}
        >
          <WarningTriangle width={16} height={16} />
          <Text size="1">Suspended accounts cannot fund a payout until a person lifts the hold.</Text>
        </Flex>
      )}

      <Box mt="4">
        <FieldList
          fields={[
            { label: 'Current balance', value: <Amount value={account.currentBalance} tone="money" /> },
            { label: 'Total deposits', value: <Amount value={account.totalDeposits} /> },
            { label: 'Total withdrawals', value: <Amount value={account.totalWithdrawals} /> },
            { label: 'Total refunds', value: <Amount value={account.totalRefunds} /> },
            { label: 'Total commissions', value: <Amount value={account.totalCommissions} /> },
            { label: 'Hold until', value: shortDate(account.lockUntil) },
            { label: 'Payout eligible', value: shortDate(account.payoutEligibleAt) },
            { label: 'Organizer', value: account.organizerName ?? account.organizerId },
          ]}
        />
      </Box>

      <Text
        as="p"
        size="1"
        mt="5"
        style={{ color: 'var(--gray-9)', lineHeight: 'var(--line-height-relaxed, 1.6)' }}
      >
        One escrow account per event. The balance is a projection of the journal, never assigned
        directly. It becomes payout eligible once the hold elapses.
      </Text>
    </DrawerShell>
  );
}

export default FinanceWorkbench;
