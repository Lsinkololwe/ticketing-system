'use client';

/**
 * Organizer dashboard — bento overview.
 *
 * Layout follows the imported design system's `Org Admin - Dashboard` screen:
 * an asymmetric top band (revenue trend gets twice the width of cash), then a
 * counter row, a composition band, quick actions, and two operational panels.
 *
 * Every data-bearing tile is governed by
 * `docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md`, which passed its conformance
 * gate before this file was written. The two rules that constrain this file
 * most:
 *
 *   - ONE KERNEL PER TILE. Each tile makes exactly one point, and its largest
 *     type is the figure carrying that point — never the tile's own title.
 *   - NO FABRICATED DATA. A tile with no data renders an empty state, never a
 *     zero. "0% checked in" asserts that nobody showed up; "no event has run
 *     yet" is the truth. The design's team-seats tile is deliberately absent
 *     because the domain model has no seat limit to divide by (spec §B5).
 *
 * All presentation lives in `.viz-*` / `.ds-*` classes in global.css so no raw
 * colour or spacing value appears here.
 */

import { useMemo } from 'react';
import Link from 'next/link';
import { Box, Flex, Text, Skeleton } from '@radix-ui/themes';
import {
  Calendar,
  CreditCard,
  Group,
  StatsReport,
  Plus,
  SendDiagonal,
} from 'iconoir-react';
import {
  useMyDashboardStats,
  useMyUpcomingEvents,
  useMyRecentActivity,
  useMyRevenueSeries,
  useMyTicketMix,
  useMyCheckInRate,
  useMyPayoutWindow,
} from '@pml.tickets/shared/api/organization-admin/modules/dashboard';
import { StatCard, QuickActionCard, Button } from '@/components/ui';
import {
  ColumnSeries,
  ShareBars,
  BulletMeter,
  VizFigure,
  VizTitle,
  VizInsight,
  VizNote,
  type ColumnPoint,
  type ShareRow,
} from '@/components/charts/primitives';
import {
  formatMoney,
  formatCount,
  monthLabel,
  monthYearLabel,
  formatEventDate,
  formatRelativeTime,
  percentChange,
  trendOf,
  humanizeStatus,
  EVENT_STATUS_LABELS,
} from '@/lib/format/figure';

// =============================================================================
// SMALL SHARED PIECES
// =============================================================================

/** A bento tile. Flat fill, hairline border, 14px radius — no gradient. */
function Tile({
  children,
  testId,
  compact,
}: {
  children: React.ReactNode;
  testId: string;
  compact?: boolean;
}) {
  return (
    <div
      className="ds-card-bento"
      data-testid={testId}
      style={{ padding: compact ? 'var(--space-4)' : 'var(--space-5)' }}
    >
      {children}
    </div>
  );
}

/**
 * Empty state for a single tile.
 *
 * Deliberately says what is missing rather than showing a zero. A zeroed chart
 * is a claim; "nothing here yet" is a fact.
 */
function TileEmpty({ message, testId }: { message: string; testId: string }) {
  return (
    <Text as="p" size="2" style={{ color: 'var(--gray-11)' }} data-testid={testId}>
      {message}
    </Text>
  );
}

function PanelHeading({
  label,
  href,
  linkLabel,
  testId,
}: {
  label: string;
  href: string;
  linkLabel: string;
  testId: string;
}) {
  return (
    <Flex justify="between" align="center" mb="2">
      <span className="viz-title">{label}</span>
      <Link href={href} data-testid={testId} style={{ fontSize: 'var(--text-1-size)' }}>
        {linkLabel}
      </Link>
    </Flex>
  );
}

// =============================================================================
// B1 — REVENUE TREND
// =============================================================================

function RevenueTrendTile() {
  const { points, loading } = useMyRevenueSeries(6);

  const model = useMemo(() => {
    if (points.length === 0) return null;

    const columns: ColumnPoint[] = points.map((p) => ({
      label: monthLabel(p.periodStart),
      value: Number(p.revenue ?? 0),
    }));

    const first = columns[0];
    const latest = columns[columns.length - 1];
    const growth = percentChange(first.value, latest.value);

    // "Six straight monthly rises" must be TRUE before we print it. Count the
    // actual consecutive rises ending at the latest month.
    let consecutiveRises = 0;
    for (let i = columns.length - 1; i > 0; i -= 1) {
      if (columns[i].value > columns[i - 1].value) consecutiveRises += 1;
      else break;
    }

    const previous = columns.length > 1 ? columns[columns.length - 2].value : 0;

    return {
      columns,
      latest,
      growth,
      consecutiveRises,
      monthOverMonth: percentChange(previous, latest.value),
      currency: points[0]?.currency ?? 'ZMW',
      rangeStart: points[0].periodStart,
      rangeEnd: points[points.length - 1].periodStart,
    };
  }, [points]);

  if (loading && points.length === 0) {
    return (
      <Tile testId="dashboard-revenue-tile">
        <Skeleton style={{ height: 220, borderRadius: 'var(--radius-4)' }} />
      </Tile>
    );
  }

  if (!model) {
    return (
      <Tile testId="dashboard-revenue-tile">
        <VizTitle testId="dashboard-revenue-title">Ticket revenue</VizTitle>
        <Box mt="3">
          <TileEmpty
            testId="dashboard-revenue-empty"
            message="No completed month of sales yet. Your revenue trend appears here once your first month closes."
          />
        </Box>
      </Tile>
    );
  }

  const { columns, latest, growth, consecutiveRises, monthOverMonth, currency } = model;

  return (
    <Tile testId="dashboard-revenue-tile">
      {/* Provenance sits in the title: what, over what range, in what units. */}
      <VizTitle testId="dashboard-revenue-title">
        Ticket revenue · {monthYearLabel(model.rangeStart)}–{monthYearLabel(model.rangeEnd)} ·{' '}
        {currency}
      </VizTitle>

      <Box mt="2">
        <VizFigure
          testId="dashboard-revenue-figure"
          value={formatMoney(latest.value, currency)}
          delta={monthOverMonth}
          trend={trendOf(monthOverMonth)}
        />
      </Box>

      <ColumnSeries points={columns} testId="dashboard-revenue-columns" />

      {/* The finding, in words, at the evidence — and only when it is true. */}
      {consecutiveRises >= 2 ? (
        <VizInsight
          testId="dashboard-revenue-insight"
          lead={`${consecutiveRises} straight monthly rises`}
        >
          {growth !== null
            ? `revenue is up ${growth}% since ${monthLabel(model.rangeStart)}.`
            : `${monthLabel(model.rangeStart)} had no sales to compare against.`}
        </VizInsight>
      ) : (
        <VizInsight
          testId="dashboard-revenue-insight"
          lead={`${latest.label} closed at ${formatMoney(latest.value, currency)}`}
        >
          {monthOverMonth !== null
            ? `${monthOverMonth >= 0 ? 'up' : 'down'} ${Math.abs(monthOverMonth)}% on the month before.`
            : ''}
        </VizInsight>
      )}
    </Tile>
  );
}

// =============================================================================
// B2 — CASH AVAILABLE
// =============================================================================

function CashAvailableTile() {
  const { window: payout, loading } = useMyPayoutWindow();

  if (loading && !payout) {
    return (
      <Tile testId="dashboard-cash-tile">
        <Skeleton style={{ height: 220, borderRadius: 'var(--radius-4)' }} />
      </Tile>
    );
  }

  const available = Number(payout?.availableNow ?? 0);
  const pending = Number(payout?.pendingRelease ?? 0);
  const currency = payout?.currency ?? 'ZMW';
  const hasWindow = (payout?.windowDaysTotal ?? 0) > 0;

  return (
    <Tile testId="dashboard-cash-tile">
      <VizTitle testId="dashboard-cash-title">Ready to withdraw · {currency}</VizTitle>

      <Box mt="2" mb="3">
        <VizFigure testId="dashboard-cash-figure" value={formatMoney(available, currency)} />
      </Box>

      {/* The caveat, not buried: "available" is not "everything I earned". */}
      {pending > 0 && (
        <VizNote testId="dashboard-cash-pending">
          A further {formatMoney(pending, currency)} is still held in escrow until those
          events clear.
        </VizNote>
      )}

      {/* No window means nothing is locked. Rendering a meter at 100% would
          claim a countdown that is not running. */}
      {hasWindow && (
        <Box mt="3" mb="3">
          <BulletMeter
            testId="dashboard-cash-meter"
            elapsed={payout?.daysElapsed ?? 0}
            total={payout?.windowDaysTotal ?? 0}
            elapsedLabel={`${payout?.daysElapsed ?? 0}d`}
            remainingLabel={`${payout?.daysRemaining ?? 0}d left`}
          />
        </Box>
      )}

      <Box mt="3">
        <Button
          variant="soft"
          size="2"
          disabled={available <= 0}
          data-testid="dashboard-request-payout"
        >
          Request payout
        </Button>
      </Box>

      <VizNote testId="dashboard-cash-note">
        Payouts land on MTN MoMo, Airtel Money or Zamtel Kwacha within minutes of approval.
      </VizNote>
    </Tile>
  );
}

// =============================================================================
// COUNTER ROW
// =============================================================================

function CounterRow() {
  const { stats, loading } = useMyDashboardStats();

  if (loading && !stats) {
    return (
      <>
        {Array.from({ length: 4 }).map((_, index) => (
          <Skeleton key={index} style={{ height: 150, borderRadius: 'var(--card-radius-bento)' }} />
        ))}
      </>
    );
  }

  if (!stats) return null;

  return (
    <>
      <StatCard
        title="Total revenue"
        value={formatMoney(stats.totalRevenue, stats.revenueCurrency)}
        change={stats.revenueChange ?? undefined}
        trend={trendOf(stats.revenueChange)}
        changeLabel="vs last month"
        icon={<CreditCard width={20} height={20} />}
      />
      <StatCard
        title="Tickets sold"
        value={formatCount(stats.totalTicketsSold)}
        change={stats.ticketsSoldChange ?? undefined}
        trend={trendOf(stats.ticketsSoldChange)}
        changeLabel="vs last month"
        icon={<StatsReport width={20} height={20} />}
      />
      <StatCard
        title="Active events"
        value={formatCount(stats.activeEvents)}
        changeLabel={`${formatCount(stats.eventsEndingThisWeek)} ending this week`}
        icon={<Calendar width={20} height={20} />}
      />
      <StatCard
        title="Attendees"
        value={formatCount(stats.totalAttendees)}
        change={stats.attendeesChange ?? undefined}
        trend={trendOf(stats.attendeesChange)}
        changeLabel="vs last month"
        icon={<Group width={20} height={20} />}
      />
    </>
  );
}

// =============================================================================
// B3 — TICKET MIX
// =============================================================================

function TicketMixTile() {
  const { mix, loading } = useMyTicketMix();

  const insight = useMemo(() => {
    if (!mix || mix.rows.length === 0) return null;
    const totalRevenue = Number(mix.totalRevenue ?? 0);
    if (totalRevenue <= 0 || !mix.totalSold) return null;

    // Look for a tier whose revenue share materially outruns its volume share
    // — the one genuinely interesting thing a ticket mix can tell you.
    const candidates = mix.rows
      .map((row) => ({
        name: row.name,
        volumeShare: (row.count / mix.totalSold) * 100,
        revenueShare: (Number(row.revenue ?? 0) / totalRevenue) * 100,
      }))
      .filter((row) => row.revenueShare - row.volumeShare >= 5)
      .sort((a, b) => b.revenueShare - b.volumeShare - (a.revenueShare - a.volumeShare));

    return candidates[0] ?? null;
  }, [mix]);

  if (loading && !mix) {
    return (
      <Tile testId="dashboard-mix-tile" compact>
        <Skeleton style={{ height: 140, borderRadius: 'var(--radius-4)' }} />
      </Tile>
    );
  }

  if (!mix || mix.totalSold === 0 || mix.rows.length === 0) {
    return (
      <Tile testId="dashboard-mix-tile" compact>
        <VizTitle testId="dashboard-mix-title">Ticket mix</VizTitle>
        <Box mt="3">
          <TileEmpty
            testId="dashboard-mix-empty"
            message="No tickets sold yet. Your tier breakdown appears here after the first sale."
          />
        </Box>
      </Tile>
    );
  }

  const rows: ShareRow[] = mix.rows.map((row) => ({
    name: row.name,
    value: row.count,
  }));

  return (
    <Tile testId="dashboard-mix-tile" compact>
      {/* The denominator is in the title, so every percentage below is checkable. */}
      <VizTitle testId="dashboard-mix-title">
        Ticket mix · share of {formatCount(mix.totalSold)} sold
      </VizTitle>
      <Box mt="3">
        <ShareBars rows={rows} total={mix.totalSold} testId="dashboard-mix-bars" />
      </Box>
      {insight && (
        <VizNote testId="dashboard-mix-note">
          {insight.name} is {Math.round(insight.volumeShare)}% of volume but{' '}
          {Math.round(insight.revenueShare)}% of revenue.
        </VizNote>
      )}
    </Tile>
  );
}

// =============================================================================
// B4 — CHECK-IN RATE
// =============================================================================

function CheckInTile() {
  const { rate, loading } = useMyCheckInRate();

  if (loading && !rate) {
    return (
      <Tile testId="dashboard-checkin-tile" compact>
        <Skeleton style={{ height: 140, borderRadius: 'var(--radius-4)' }} />
      </Tile>
    );
  }

  // No event has run yet. A 0% rate here would assert that nobody arrived.
  if (!rate || rate.issued === 0) {
    return (
      <Tile testId="dashboard-checkin-tile" compact>
        <VizTitle testId="dashboard-checkin-title">Check-in rate</VizTitle>
        <Box mt="3">
          <TileEmpty
            testId="dashboard-checkin-empty"
            message="No event has run yet. Gate attendance appears here after your first event."
          />
        </Box>
      </Tile>
    );
  }

  const noShow = Math.max(0, rate.issued - rate.scanned);

  return (
    <Tile testId="dashboard-checkin-tile" compact>
      <VizTitle testId="dashboard-checkin-title">
        Check-in rate · {rate.eventTitle}
      </VizTitle>

      <Box mt="2" mb="3">
        <VizFigure testId="dashboard-checkin-figure" value={`${rate.ratePercent}%`} />
      </Box>

      <ShareBars
        testId="dashboard-checkin-bars"
        total={rate.issued}
        rows={[
          { name: 'Scanned', value: rate.scanned },
          // Not a reference series and not red: a no-show is not an error
          // state, and colouring it as one asserts a judgement the data does
          // not support.
          { name: 'No-show', value: noShow },
        ]}
      />

      {/* The rate always ships with its denominator. */}
      <VizNote testId="dashboard-checkin-note">
        {formatCount(rate.scanned)} of {formatCount(rate.issued)} ticket holders arrived at
        the gate{rate.eventDateTime ? ` on ${formatEventDate(rate.eventDateTime)}` : ''}.
      </VizNote>
    </Tile>
  );
}

// =============================================================================
// OPERATIONAL PANELS
// =============================================================================

function ActivityPanel() {
  const { activity, loading } = useMyRecentActivity(6);

  return (
    <Tile testId="dashboard-activity-panel" compact>
      <PanelHeading
        label="Recent activity"
        href="/events"
        linkLabel="View all"
        testId="dashboard-activity-view-all"
      />

      {loading && activity.length === 0 ? (
        <Skeleton style={{ height: 140, borderRadius: 'var(--radius-4)' }} />
      ) : activity.length === 0 ? (
        <TileEmpty
          testId="dashboard-activity-empty"
          message="Nothing yet. Sales, check-ins and payouts show up here as they happen."
        />
      ) : (
        <Box role="list">
          {activity.map((item) => (
            <Flex
              key={item.id}
              role="listitem"
              gap="3"
              justify="between"
              align="center"
              py="2"
              style={{ borderBottom: '1px solid var(--gray-a4)' }}
            >
              <Text size="2" style={{ color: 'var(--gray-12)' }}>
                {item.message}
              </Text>
              <Text
                size="1"
                className="ds-amount"
                style={{ color: 'var(--gray-11)', whiteSpace: 'nowrap' }}
              >
                {formatRelativeTime(item.timestamp)}
              </Text>
            </Flex>
          ))}
        </Box>
      )}
    </Tile>
  );
}

function UpcomingPanel() {
  const { events, loading } = useMyUpcomingEvents(5);

  return (
    <Tile testId="dashboard-upcoming-panel" compact>
      <PanelHeading
        label="Upcoming events"
        href="/events"
        linkLabel="View all"
        testId="dashboard-events-view-all"
      />

      {loading && events.length === 0 ? (
        <Skeleton style={{ height: 140, borderRadius: 'var(--radius-4)' }} />
      ) : events.length === 0 ? (
        <Flex direction="column" gap="3" align="start">
          <TileEmpty
            testId="dashboard-upcoming-empty"
            message="No events yet. Create your first draft to get started."
          />
          <Button variant="soft" size="1" data-testid="dashboard-empty-cta">
            <Link href="/events/new" style={{ color: 'inherit', textDecoration: 'none' }}>
              Create event
            </Link>
          </Button>
        </Flex>
      ) : (
        <Box role="list">
          {events.map((event) => {
            const sold = event.ticketsSold ?? 0;
            const capacity = event.totalCapacity ?? 0;
            return (
              <Box
                key={event.id}
                role="listitem"
                py="2"
                style={{ borderBottom: '1px solid var(--gray-a4)' }}
              >
                <Flex justify="between" align="center" gap="3">
                  <Text size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
                    {event.title}
                  </Text>
                  <span
                    className={
                      (event.status ?? '').toUpperCase() === 'PUBLISHED'
                        ? 'badge badge-success'
                        : 'badge badge-neutral'
                    }
                  >
                    {humanizeStatus(event.status, EVENT_STATUS_LABELS)}
                  </span>
                </Flex>

                <Box mt="2">
                  {/* Sold against capacity — the denominator is printed, so the
                      percentage is checkable rather than asserted. */}
                  <ShareBars
                    testId={`dashboard-upcoming-progress-${event.id}`}
                    total={capacity}
                    rows={[
                      {
                        name: formatEventDate(event.eventDateTime),
                        value: sold,
                        display: `${formatCount(sold)}/${formatCount(capacity)}`,
                      },
                    ]}
                  />
                </Box>
              </Box>
            );
          })}
        </Box>
      )}
    </Tile>
  );
}

// =============================================================================
// PAGE
// =============================================================================

export default function DashboardPage() {
  return (
    <Box data-testid="dashboard-page">
      {/* Band 1 — asymmetric on purpose. The trend is the page's lead, so it
          gets twice the width of the cash tile beside it. */}
      <div className="viz-band viz-band-split">
        <RevenueTrendTile />
        <CashAvailableTile />
      </div>

      {/* Band 2 — counters. Supporting, not focal: each is one number with one
          comparison anchor. */}
      <div className="viz-band viz-band-metrics">
        <CounterRow />
      </div>

      {/* Band 3 — composition. Two tiles, not the design's three: the
          team-seats tile is deliberately absent because there is no seat limit
          in the domain model to divide by (spec §B5). */}
      <div className="viz-band viz-band-actions">
        <TicketMixTile />
        <CheckInTile />
      </div>

      <span className="viz-title" style={{ marginBottom: 'var(--space-2)' }}>
        Quick actions
      </span>
      <div className="viz-band viz-band-actions">
        <QuickActionCard
          title="Create event"
          description="Start a new event from scratch"
          href="/events/new"
          icon={<Plus width={18} height={18} />}
        />
        <QuickActionCard
          title="View analytics"
          description="Check your performance metrics"
          href="/analytics"
          icon={<StatsReport width={18} height={18} />}
        />
        <QuickActionCard
          title="Request payout"
          description="Move your balance to mobile money"
          href="/finance/payouts"
          icon={<SendDiagonal width={18} height={18} />}
        />
      </div>

      <div className="viz-band viz-band-panels">
        <ActivityPanel />
        <UpcomingPanel />
      </div>
    </Box>
  );
}
