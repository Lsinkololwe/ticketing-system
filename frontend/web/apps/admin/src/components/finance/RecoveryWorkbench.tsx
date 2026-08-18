'use client';

/**
 * Transaction recovery.
 *
 * <h2>Design authority</h2>
 * `Admin - Transaction Recovery.dc.html` — a rail with four views (Recovery
 * queue, Dual-approval proposals, Dead letters, Risk metrics), risk tiles, a
 * source-filtered queue table, and a per-source bar chart under metrics.
 *
 * <h2>What is real, stated plainly</h2>
 * The design's queue claims eight sources. booking-service backs one family of
 * them — payouts — through `stuckPayoutRequests…`, `retryablePayoutRequests…`,
 * `payoutRequestsForReview…` and `payoutRecoverySummary`. There is no query for
 * orphaned or disputed webhooks, reconciliation items, failed refunds or dead
 * letters, and no dual-approval model anywhere in the schema.
 *
 * <p>So the queue is built over the payout buckets and named for what it
 * covers, the metrics view uses the real recovery summary including its
 * per-issue-type breakdown, and the two unbacked views say what they need
 * rather than showing invented rows. An operator who believes a recovery queue
 * is complete stops looking for money elsewhere — which is the specific harm a
 * plausible-looking queue would cause here.
 *
 * <h2>Dual approval is absent, not disabled</h2>
 * The design requires a proposer and a confirmer with different identities for
 * money-moving recoveries. Nothing in the backend records a proposal, so there
 * is no control to disable — the view names the gap instead. Shipping a
 * "Propose" button that immediately applied would defeat the exact control the
 * design exists to enforce.
 */

import { useState } from 'react';
import { Box, Flex, Table, Text } from '@radix-ui/themes';
import { MailOut, StatsReport, Timer, Group, WarningTriangle } from 'iconoir-react';
import {
  usePayoutRecoverySummary,
  useRecoveryQueue,
  type RecoveryBucket,
} from '@pml.tickets/shared/api/admin/modules/finance';
import { Amount, Badge, Button, EmptyState, PageHeader, StyledCard } from '@/components/ui';
import { formatCount, humanizeEnum, statusTone } from '@/lib/format';

type RecoveryView = 'queue' | 'proposals' | 'deadletters' | 'metrics';

const VIEWS: { id: RecoveryView; label: string; icon: React.ReactNode }[] = [
  { id: 'queue', label: 'Recovery queue', icon: <WarningTriangle width={18} height={18} /> },
  { id: 'proposals', label: 'Dual-approval proposals', icon: <Group width={18} height={18} /> },
  { id: 'deadletters', label: 'Dead letters', icon: <MailOut width={18} height={18} /> },
  { id: 'metrics', label: 'Risk metrics', icon: <StatsReport width={18} height={18} /> },
];

/** The design's source filter chips, restricted to the buckets that exist. */
const BUCKETS: { id: RecoveryBucket; label: string }[] = [
  { id: 'stuck', label: 'Stuck' },
  { id: 'retryable', label: 'Retryable' },
  { id: 'review', label: 'Under review' },
];

function ageLabel(from: string | null | undefined): string {
  if (!from) return '—';
  const ms = Date.now() - new Date(from).getTime();
  if (Number.isNaN(ms) || ms < 0) return '—';
  const hours = ms / 3_600_000;
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  if (hours < 48) return `${Math.round(hours)}h`;
  return `${Math.round(hours / 24)}d`;
}

export function RecoveryWorkbench() {
  const [view, setView] = useState<RecoveryView>('queue');
  const { summary } = usePayoutRecoverySummary();

  return (
    <Box>
      <Flex justify="between" align="start" wrap="wrap" gap="3">
        <PageHeader
          title="Transaction recovery"
          description="Payouts in an ambiguous state, ordered by what is at risk."
          breadcrumbs={[{ label: 'Action center' }, { label: 'Recovery queue' }]}
        />
        <Flex
          data-testid="recovery-scope-chip"
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
        <Box width={{ initial: '100%', md: '232px' }} flexShrink="0" style={{ minWidth: 0 }}>
          <StyledCard hover="none" padding="3">
            <Flex direction="column" gap="1">
              {VIEWS.map((v) => {
                const active = v.id === view;
                const badge =
                  v.id === 'queue' ? summary?.totalPayoutsForReview ?? null : null;
                return (
                  <Flex
                    key={v.id}
                    data-testid={`recovery-view-${v.id}`}
                    align="center"
                    gap="3"
                    onClick={() => setView(v.id)}
                    style={{
                      padding: 'var(--space-2) var(--space-3)',
                      borderRadius: 'var(--radius-3)',
                      cursor: 'pointer',
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
                );
              })}
            </Flex>
          </StyledCard>
        </Box>

        <Box style={{ flex: 1, minWidth: 0, width: '100%' }}>
          {view === 'queue' && <QueueView />}
          {view === 'metrics' && <MetricsView />}
          {view === 'proposals' && (
            <NotBacked
              title="Dual-approval proposals"
              icon={<Group width={22} height={22} />}
              detail="Money-moving recoveries need a proposer and a confirmer with different identities, and a proposal that expires after two hours. Nothing in the schema records a proposal — no model, no query, no mutation — so this view has nothing to read. Building it means adding the proposal model to booking-service first."
            />
          )}
          {view === 'deadletters' && (
            <NotBacked
              title="Dead letters"
              icon={<MailOut width={22} height={22} />}
              detail="Failed messages live in the Azure Service Bus dead-letter queues. No subgraph exposes them, so there is no field to select. Building this means a query over the DLQ — replay through the existing idempotent consumer, discard gated on SUPER_ADMIN with a reason."
            />
          )}
        </Box>
      </Flex>
    </Box>
  );
}

// =============================================================================
// Queue
// =============================================================================

function QueueView() {
  const [bucket, setBucket] = useState<RecoveryBucket>('stuck');
  const { items, pageInfo, loading } = useRecoveryQueue(bucket);
  const { summary } = usePayoutRecoverySummary();

  return (
    <>
      <Box
        data-testid="recovery-risk-tiles"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-4)',
        }}
      >
        <RiskTile
          label="Total at risk"
          value={summary ? <Amount value={summary.totalAmountAtRisk} tone="money" /> : '—'}
          sub="Across payout recovery"
        />
        <RiskTile
          label="In the queue"
          value={summary ? formatCount(summary.totalPayoutsForReview) : '—'}
          sub="Payout sources only"
        />
        <RiskTile
          label="Stuck"
          value={summary ? formatCount(summary.stuckPayoutsCount) : '—'}
          sub="No longer progressing"
        />
        <RiskTile
          label="Retryable"
          value={summary ? formatCount(summary.retryablePayoutsCount) : '—'}
          sub="Safe to send again"
        />
      </Box>

      {/* The design's honest note about scope, on the screen rather than only in
          a comment — the person reading the queue is the one who needs it. */}
      <Text as="p" size="1" mb="3" style={{ color: 'var(--gray-9)' }}>
        This queue covers payout recovery. Webhooks, reconciliation items, refunds and dead letters
        are not yet queryable and are not counted here.
      </Text>

      <Flex gap="2" mb="3" wrap="wrap" data-testid="recovery-bucket-filters">
        {BUCKETS.map((b) => (
          <Button
            key={b.id}
            variant={b.id === bucket ? 'solid' : 'outline'}
            size="1"
            onClick={() => setBucket(b.id)}
            data-testid={`recovery-bucket-${b.id}`}
          >
            {b.label}
          </Button>
        ))}
      </Flex>

      <StyledCard hover="none">
        <Box style={{ overflowX: 'auto', maxWidth: '100%', minWidth: 0 }}>
          <Table.Root variant="surface">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>Subject</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Amount at risk</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Age</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Failure reason</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>Review</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {loading && items.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <Text size="2" style={{ color: 'var(--gray-9)' }}>
                      Loading the recovery queue…
                    </Text>
                  </Table.Cell>
                </Table.Row>
              ) : items.length === 0 ? (
                <Table.Row>
                  <Table.Cell colSpan={5}>
                    <EmptyState
                      size="sm"
                      icon={<Timer width={20} height={20} />}
                      title="Nothing in this bucket"
                      description="No payout is currently in this recovery state."
                    />
                  </Table.Cell>
                </Table.Row>
              ) : (
                items.map((p) => (
                  <Table.Row key={p.id} align="center">
                    <Table.RowHeaderCell>
                      <Text as="div" weight="medium" size="2">
                        {p.organizerName ?? p.organizerId}
                      </Text>
                      <Text as="div" size="1" className="ds-amount" style={{ color: 'var(--gray-9)' }}>
                        {p.requestId}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Amount value={p.requestedAmount} tone="money" />
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" className="ds-amount">
                        {ageLabel(p.stuckAt ?? p.requestedAt)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2">
                        {p.stuckReason ?? p.lastError ?? humanizeEnum(p.issueType)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <span data-testid={`recovery-review-${p.id}`}>
                        <Badge color={statusTone(p.reviewStatus)} variant="soft">
                          {humanizeEnum(p.reviewStatus)}
                        </Badge>
                      </span>
                    </Table.Cell>
                  </Table.Row>
                ))
              )}
            </Table.Body>
          </Table.Root>
        </Box>
        <Flex justify="end" pt="3">
          <Text size="1" style={{ color: 'var(--gray-9)' }} data-testid="recovery-queue-count">
            {pageInfo.totalCount === 0
              ? 'Nothing to show'
              : `${formatCount(pageInfo.totalCount)} in this bucket`}
          </Text>
        </Flex>
      </StyledCard>
    </>
  );
}

function RiskTile({
  label,
  value,
  sub,
}: {
  label: string;
  value: React.ReactNode;
  sub: string;
}) {
  return (
    <StyledCard hover="none" padding="4">
      <Text className="ds-label" as="div">
        {label}
      </Text>
      <Text
        as="div"
        className="ds-amount"
        style={{
          fontSize: 'var(--text-5-size)',
          fontWeight: 'var(--weight-bold)',
          marginTop: 'var(--space-1)',
        }}
      >
        {value}
      </Text>
      <Text as="div" size="1" mt="1" style={{ color: 'var(--gray-9)' }}>
        {sub}
      </Text>
    </StyledCard>
  );
}

// =============================================================================
// Metrics
// =============================================================================

function MetricsView() {
  const { summary, loading } = usePayoutRecoverySummary();

  const issues = summary?.issuesByType ?? [];
  const max = issues.reduce((m, i) => Math.max(m, Number(i.totalAmount ?? 0)), 0);

  return (
    <>
      <Box
        data-testid="recovery-metric-tiles"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-4)',
        }}
      >
        <RiskTile
          label="Total at risk"
          value={summary ? <Amount value={summary.totalAmountAtRisk} tone="money" /> : '—'}
          sub="Payout recovery"
        />
        <RiskTile
          label="Median resolution"
          value={
            summary?.averageResolutionTimeMinutes != null
              ? `${Math.round(summary.averageResolutionTimeMinutes)}m`
              : '—'
          }
          sub="Mean, per resolved item"
        />
        <RiskTile
          label="Recently resolved"
          value={summary ? formatCount(summary.recentlyResolvedCount) : '—'}
          sub="Closed out"
        />
        <RiskTile
          label="Under review"
          value={summary ? formatCount(summary.underReviewCount) : '—'}
          sub="With an operator now"
        />
      </Box>

      <StyledCard hover="none">
        <Text as="div" size="3" weight="bold">
          Amount at risk by issue type
        </Text>
        <Text as="p" size="1" mt="1" mb="4" style={{ color: 'var(--gray-9)' }}>
          {/* The design charts by source; the backend groups by issue type. Same
              question — where should an operator look first — answered with the
              dimension that actually exists. */}
          One sorted bar per issue type. The longest bar is where to look first.
        </Text>

        {loading && issues.length === 0 ? (
          <Text size="2" style={{ color: 'var(--gray-9)' }}>
            Loading…
          </Text>
        ) : issues.length === 0 ? (
          <EmptyState
            size="sm"
            icon={<StatsReport width={20} height={20} />}
            title="No payout issues recorded"
            description="Nothing has entered payout recovery, so there is nothing to break down."
          />
        ) : (
          <Flex direction="column" gap="3" data-testid="recovery-issue-bars">
            {[...issues]
              .sort((a, b) => Number(b.totalAmount ?? 0) - Number(a.totalAmount ?? 0))
              .map((i) => {
                const value = Number(i.totalAmount ?? 0);
                const pct = max > 0 ? Math.max(4, Math.round((value / max) * 100)) : 4;
                return (
                  <Box key={i.issueType}>
                    <Flex justify="between" mb="1">
                      <Text size="2" weight="medium">
                        {humanizeEnum(i.issueType)}
                      </Text>
                      <Amount value={value} tone="money" />
                    </Flex>
                    <Box
                      style={{
                        height: 'var(--space-2)',
                        background: 'var(--gray-4)',
                        borderRadius: 'var(--radius-1)',
                        overflow: 'hidden',
                      }}
                    >
                      <Box
                        style={{
                          width: `${pct}%`,
                          height: '100%',
                          background: 'var(--accent-9)',
                          borderRadius: 'var(--radius-1)',
                        }}
                      />
                    </Box>
                  </Box>
                );
              })}
          </Flex>
        )}
      </StyledCard>
    </>
  );
}

// =============================================================================
// Views with no backing
// =============================================================================

function NotBacked({
  title,
  icon,
  detail,
}: {
  title: string;
  icon: React.ReactNode;
  detail: string;
}) {
  return (
    <StyledCard hover="none">
      <EmptyState size="md" icon={icon} title={title} description={detail} />
    </StyledCard>
  );
}

export default RecoveryWorkbench;
