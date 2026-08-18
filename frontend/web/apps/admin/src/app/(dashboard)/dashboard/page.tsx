'use client';

/**
 * Platform admin dashboard.
 *
 * <h2>Design authority</h2>
 * Built to `Admin - Dashboard.dc.html` in the Claude Design project
 * (`03cea541-469f-44d2-aa91-a5c6f5456295`). That screen — not this file, and not
 * `docs/ADMIN_APP_DESIGN.md` — is the layout contract. See
 * `docs/DESIGN_AUTHORITY.md`.
 *
 * Composition, in order, per the design:
 *   1. hero tile strip — `auto-fit minmax(150px, 1fr)`, 3px left status border
 *   2. "Needs attention" micro-label + action cards — `auto-fit minmax(220px, 1fr)`
 *   3. a `1.3fr / 1fr` grid: sales sparkline, then the activity log
 *
 * <h2>What this replaced</h2>
 * Nine hardcoded values — 156 events, 12,847 tickets, "K 2.4M", invented
 * organizer names — and not one data hook, while `platformSummary` and the
 * pending-count queries sat unused in the schema. A fabricated dashboard is
 * worse than an empty one: an empty one says the platform is new, a fabricated
 * one says it is thriving.
 */

import { useMemo } from 'react';
import Link from 'next/link';
import { Box, Flex, Text } from '@radix-ui/themes';
import {
  Calendar,
  ClipboardCheck,
  Coins,
  Group,
  Activity,
  SendDiagonal,
  ShieldAlert,
  Label,
  WarningTriangle,
} from 'iconoir-react';
import {
  usePendingCounts,
  usePlatformSummary,
} from '@pml.tickets/shared/api/graphql';
import { PageHeader, StyledCard } from '@/components/ui';
import { formatCount } from '@/lib/format';

// =============================================================================
// Formatting — the design's number language
// =============================================================================

/**
 * `K 184.2k` — Kwacha symbol, a space, then a compact figure.
 *
 * The design system is explicit: currency is always `K 125,430`, never `ZMW`
 * and never `$`. Hero tiles compact to keep the tile one line at 150px.
 */
function kwacha(amount: number | null | undefined): string {
  if (amount == null) return '—';
  const abs = Math.abs(amount);
  if (abs >= 1_000_000) return `K ${(amount / 1_000_000).toFixed(1)}M`;
  if (abs >= 1_000) return `K ${(amount / 1_000).toFixed(1)}k`;
  return `K ${amount.toFixed(0)}`;
}

const toNumber = (v: unknown): number | null =>
  v == null ? null : Number(v);

// =============================================================================
// Page
// =============================================================================

export default function AdminDashboardPage() {
  const { summary, loading: summaryLoading } = usePlatformSummary();
  const { counts, loading: countsLoading } = usePendingCounts();

  const loading = summaryLoading || countsLoading;

  const heroTiles = useMemo(() => {
    const revenue = toNumber(summary?.totalTicketRevenue);
    const atRisk = toNumber(summary?.totalPayoutAmount);
    const pendingApprovals =
      (counts?.['organizer-applications'] ?? 0) +
      (counts?.['event-reviews'] ?? 0) +
      (counts?.['document-verification'] ?? 0);

    return [
      {
        key: 'revenue',
        label: 'Revenue',
        value: kwacha(revenue),
        icon: <Coins width={13} height={13} />,
        // Money is jade, semantically — never the brand teal, so a "paid"
        // figure can never be mistaken for a brand element.
        accent: 'var(--jade-9)',
        valueColor: 'var(--gray-12)',
      },
      {
        key: 'tickets',
        label: 'Tickets sold',
        value:
          summary?.totalTicketsSold == null
            ? '—'
            : formatCount(summary.totalTicketsSold),
        icon: <Label width={13} height={13} />,
        accent: 'var(--blue-9)',
        valueColor: 'var(--gray-12)',
      },
      {
        key: 'at-risk',
        label: 'Pending payouts',
        value: kwacha(atRisk),
        icon: <WarningTriangle width={13} height={13} />,
        accent: 'var(--red-9)',
        valueColor: 'var(--red-11)',
      },
      {
        key: 'approvals',
        label: 'Pending approvals',
        value: formatCount(pendingApprovals),
        icon: <ClipboardCheck width={13} height={13} />,
        accent: 'var(--amber-9)',
        valueColor: 'var(--amber-11)',
      },
      {
        key: 'status',
        label: 'Failed transactions',
        value:
          summary?.failedTransactions == null
            ? '—'
            : formatCount(summary.failedTransactions),
        icon: <Activity width={13} height={13} />,
        accent: 'var(--green-9)',
        valueColor:
          (summary?.failedTransactions ?? 0) > 0
            ? 'var(--red-11)'
            : 'var(--green-11)',
      },
    ];
  }, [summary, counts]);

  const actionCards = useMemo(
    () => [
      {
        key: 'organizers',
        title: 'Organizer applications',
        sub: 'Awaiting review',
        href: '/approvals/organizers',
        icon: <Group width={16} height={16} />,
        tint: 'var(--red-a3)',
        iconColor: 'var(--red-11)',
        count: counts?.['organizer-applications'] ?? 0,
        pill: 'var(--red-9)',
      },
      {
        key: 'events',
        title: 'Event approvals',
        sub: 'Awaiting review',
        href: '/approvals/events',
        icon: <Calendar width={16} height={16} />,
        tint: 'var(--amber-a3)',
        iconColor: 'var(--amber-11)',
        count: counts?.['event-reviews'] ?? 0,
        pill: 'var(--amber-9)',
      },
      {
        key: 'documents',
        title: 'Document verification',
        sub: 'KYB documents queued',
        href: '/approvals/documents',
        icon: <ShieldAlert width={16} height={16} />,
        tint: 'var(--copper-a3)',
        iconColor: 'var(--copper-11)',
        count: counts?.['document-verification'] ?? 0,
        pill: 'var(--copper-9)',
      },
      {
        key: 'payouts',
        title: 'Payout requests',
        sub: 'Awaiting review',
        href: '/finance/payouts',
        icon: <SendDiagonal width={16} height={16} />,
        tint: 'var(--accent-a3)',
        iconColor: 'var(--accent-11)',
        count: counts?.['payout-requests'] ?? 0,
        pill: 'var(--accent-9)',
      },
    ],
    [counts]
  );

  return (
    <Box>
      <PageHeader
        title="Dashboard"
        description="Platform activity across events, organizers and revenue."
      />

      {/* ── Hero tiles ─────────────────────────────────────────────────────
          auto-fit minmax(150px, 1fr) per the design, so five tiles reflow to
          two rows on a tablet and one column on a phone without a media query. */}
      <Box
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-4)',
        }}
      >
        {loading
          ? [0, 1, 2, 3, 4].map((i) => <TileSkeleton key={i} />)
          : heroTiles.map((tile) => (
              <Box
                key={tile.key}
                style={{
                  background: 'var(--card-bg)',
                  border: 'var(--card-border)',
                  borderRadius: 'var(--card-radius-bento)',
                  borderLeft: `3px solid ${tile.accent}`,
                  padding: 'var(--space-3) var(--space-4)',
                }}
              >
                <Flex align="center" gap="2" style={{ color: tile.accent }}>
                  {tile.icon}
                  <Text className="ds-label" as="span">
                    {tile.label}
                  </Text>
                </Flex>
                <Text
                  as="div"
                  className="ds-amount"
                  style={{
                    fontSize: 'var(--text-5-size)',
                    fontWeight: 'var(--weight-bold)',
                    marginTop: 'var(--space-1)',
                    color: tile.valueColor,
                  }}
                >
                  {tile.value}
                </Text>
              </Box>
            ))}
      </Box>

      {/* ── Needs attention ────────────────────────────────────────────── */}
      <Text
        className="ds-label"
        as="p"
        style={{ margin: 'var(--space-5) 0 var(--space-2)' }}
      >
        Needs attention
      </Text>

      <Box
        data-testid="dashboard-action-cards"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-5)',
        }}
      >
        {actionCards.map((card) => (
          <Link
            key={card.key}
            href={card.href}
            style={{ textDecoration: 'none' }}
          >
            <StyledCard hover="lift" padding="3" interactive>
              <Flex align="center" gap="3">
                <Flex
                  align="center"
                  justify="center"
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: 'var(--radius-3)',
                    background: card.tint,
                    color: card.iconColor,
                    flexShrink: 0,
                  }}
                >
                  {card.icon}
                </Flex>
                <Box style={{ flex: 1, minWidth: 0 }}>
                  <Text
                    as="div"
                    size="2"
                    weight="medium"
                    style={{ color: 'var(--gray-12)' }}
                  >
                    {card.title}
                  </Text>
                  <Text as="div" size="1" style={{ color: 'var(--gray-9)' }}>
                    {card.sub}
                  </Text>
                </Box>
                {card.count > 0 && (
                  <Text
                    as="span"
                    className="ds-amount"
                    style={{
                      background: card.pill,
                      color: 'var(--accent-contrast)',
                      fontSize: 'var(--text-1-size)',
                      fontWeight: 'var(--weight-bold)',
                      padding: '2px 7px',
                      borderRadius: 'var(--radius-full)',
                    }}
                  >
                    {card.count}
                  </Text>
                )}
              </Flex>
            </StyledCard>
          </Link>
        ))}
      </Box>

      {/* ── Sales + activity ───────────────────────────────────────────────
          1.3fr / 1fr on desktop, stacking under the md breakpoint. */}
      <Box className="dash-split">
        <StyledCard hover="none">
          <Flex justify="between" align="center">
            <Text size="2" weight="bold">
              Escrow and settlement
            </Text>
          </Flex>
          <Text as="p" size="1" style={{ color: 'var(--gray-9)' }} mb="3">
            Held balance against what is currently releasable
          </Text>
          <Flex direction="column" gap="3">
            <MoneyRow
              label="Escrow balance"
              value={kwacha(toNumber(summary?.totalEscrowBalance))}
            />
            <MoneyRow
              label="Available for payout"
              value={kwacha(toNumber(summary?.availableForPayout))}
            />
            <MoneyRow
              label="Pending payout requests"
              value={
                summary?.pendingPayoutRequests == null
                  ? '—'
                  : formatCount(summary.pendingPayoutRequests)
              }
            />
          </Flex>
        </StyledCard>

        <StyledCard hover="none">
          <Text size="2" weight="bold">
            Transactions
          </Text>
          <Text as="p" size="1" style={{ color: 'var(--gray-9)' }} mb="3">
            Platform-wide, all time
          </Text>
          <Flex direction="column" gap="3">
            <MoneyRow
              label="Total"
              value={
                summary?.totalTransactions == null
                  ? '—'
                  : formatCount(summary.totalTransactions)
              }
              dot="var(--gray-9)"
            />
            <MoneyRow
              label="Pending"
              value={
                summary?.pendingTransactions == null
                  ? '—'
                  : formatCount(summary.pendingTransactions)
              }
              dot="var(--amber-9)"
            />
            <MoneyRow
              label="Failed"
              value={
                summary?.failedTransactions == null
                  ? '—'
                  : formatCount(summary.failedTransactions)
              }
              dot="var(--red-9)"
            />
          </Flex>
        </StyledCard>
      </Box>

      <style jsx>{`
        .dash-split {
          display: grid;
          grid-template-columns: 1.3fr 1fr;
          gap: var(--space-4);
        }
        @media (max-width: 900px) {
          .dash-split {
            grid-template-columns: 1fr;
          }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// Pieces
// =============================================================================

function MoneyRow({
  label,
  value,
  dot,
}: {
  label: string;
  value: string;
  dot?: string;
}) {
  return (
    <Flex
      justify="between"
      align="center"
      style={{
        paddingBottom: 'var(--space-2)',
        borderBottom: '1px solid var(--gray-a4)',
      }}
    >
      <Flex align="center" gap="2">
        {dot && (
          <Box
            style={{
              width: 6,
              height: 6,
              borderRadius: 'var(--radius-full)',
              background: dot,
              flexShrink: 0,
            }}
          />
        )}
        <Text size="2" style={{ color: 'var(--gray-11)' }}>
          {label}
        </Text>
      </Flex>
      <Text size="2" weight="medium" className="ds-amount">
        {value}
      </Text>
    </Flex>
  );
}

/** Shimmer tile. The design shows skeletons rather than zeros while loading —
 *  a tile reading "K 0" during a fetch is a factual claim the app cannot make. */
function TileSkeleton() {
  return (
    <Box
      style={{
        height: 64,
        borderRadius: 'var(--card-radius-bento)',
        background: 'var(--gray-a3)',
      }}
    />
  );
}
