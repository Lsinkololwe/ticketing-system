'use client';

import Link from 'next/link';
import {
  BarChart,
  Button,
  Card,
  CardHeader,
  CircularProgress,
  EmptyState,
  HorizontalBars,
  KpiCard,
  KpiGrid,
  PageHeader,
  Skeleton,
  StatusPill,
} from '@pml.tickets/shared/components/m3';
import type {
  MyCheckInRateQuery,
  MyDashboardStatsQuery,
  MyPayoutWindowQuery,
  MyRevenueSeriesQuery,
  MyTicketMixQuery,
  MyUpcomingEventsQuery,
  MyRecentActivityQuery,
} from '@pml.tickets/shared/types/graphql';
import { formatCount, formatEventDate, formatMoney, formatRelativeTime, monthLabel, percentChange } from '@/lib/format/figure';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status, statusLabel } from '@/components/console/Status';
import { NotAvailable } from '@/components/console/NotAvailable';
import type { NotificationRow } from '@/lib/api/notifications';

type Stats = MyDashboardStatsQuery['myDashboardStats'];
type Series = MyRevenueSeriesQuery['myRevenueSeries'];
type Mix = NonNullable<MyTicketMixQuery['myTicketMix']>;
type CheckIn = NonNullable<MyCheckInRateQuery['myCheckInRate']>;
type PayoutWindow = NonNullable<MyPayoutWindowQuery['myPayoutWindow']>;
type Upcoming = MyUpcomingEventsQuery['myUpcomingEvents'];
type Activity = MyRecentActivityQuery['myRecentActivity'];

export interface DashboardViewProps {
  orgName: string;
  /** Finance KPIs and charts are shown only to roles that may view financials. */
  canViewFinance: boolean;
  canCreateEvents: boolean;
  loading: boolean;
  stats: Stats | null;
  series: Series;
  mix: Mix | null;
  checkIn: CheckIn | null;
  payoutWindow: PayoutWindow | null;
  upcoming: Upcoming;
  activity: Activity;
  notifications: NotificationRow[];
  unreadNotifications: number;
  onMarkAllRead?: () => void;
  today?: Date;
}

function trendFor(change: number | null | undefined) {
  if (change == null || change === 0) return undefined;
  return { text: `${Math.abs(change)}%`, direction: change > 0 ? ('up' as const) : ('down' as const) };
}

export function DashboardView(p: DashboardViewProps) {
  const money = (v: string | number | null | undefined, cur?: string | null) => formatMoney(v, cur, { decimals: 0 });
  const s = p.stats;
  const cur = s?.revenueCurrency ?? 'ZMW';
  const sub = `${p.orgName} · ${formatEventDate((p.today ?? new Date()).toISOString())}`;
  const rev = s ? Number(s.totalRevenue) : 0;
  const revTrend = s ? trendFor(s.revenueChange) : undefined;
  void percentChange;

  const mixRows = (p.mix?.rows ?? []).map((r) => ({ label: r.name, value: r.count }));
  const mixTotal = p.mix?.totalSold || mixRows.reduce((n, r) => n + r.value, 0);
  const share = (n: number) => (mixTotal ? `${((n / mixTotal) * 100).toFixed((n / mixTotal) * 100 >= 10 ? 0 : 1)}%` : '0%');
  const series = p.series.map((pt) => ({ label: monthLabel(pt.periodStart), value: Number(pt.revenue) }));

  return (
    <div data-testid="dashboard-page">
      <PageHeader
        title="Overview"
        subtitle={sub}
        actions={
          p.canCreateEvents ? (
            <LinkBtn href="/events/new" variant="filled" icon="add">
              Create event
            </LinkBtn>
          ) : null
        }
      />

      {p.loading && !s ? (
        <div className="m3-stack" role="status" aria-label="Loading overview" data-testid="loading">
          <Skeleton />
          <Skeleton />
          <Skeleton />
        </div>
      ) : (
        <>
          <KpiGrid>
            {p.canViewFinance ? (
              <KpiCard
                label={p.series.length ? `Revenue · ${new Date(p.series[p.series.length - 1].periodStart).toLocaleDateString('en-GB', { month: 'short', year: 'numeric', timeZone: 'UTC' })}` : 'Revenue'}
                icon="money"
                value={money(rev, cur)}
                trend={revTrend}
                caption={revTrend ? 'vs previous month · Net of refunds, before commission' : 'Net of refunds, before commission'}
              />
            ) : null}
            <KpiCard label="Tickets sold" icon="ticket" value={formatCount(s?.totalTicketsSold ?? 0)} trend={trendFor(s?.ticketsSoldChange)} />
            <KpiCard label="Active events" icon="calendar" value={formatCount(s?.activeEvents ?? 0)} caption="Live and on sale" />
            <KpiCard label="Ending this week" icon="clock" value={formatCount(s?.eventsEndingThisWeek ?? 0)} />
            <KpiCard label="Attendees expected" icon="users" value={formatCount(s?.totalAttendees ?? 0)} trend={trendFor(s?.attendeesChange)} caption="Tickets for upcoming events" />
            {p.canViewFinance ? <KpiCard label="Available balance" icon="wallet" value={money(s?.availableBalance, cur)} caption="Ready to withdraw" /> : null}
            {p.canViewFinance ? <KpiCard label="Pending payouts" icon="bank" value={money(s?.pendingPayouts, cur)} /> : null}
          </KpiGrid>

          {p.canViewFinance ? (
            <div className="oc-cols--2-1 oc-section">
              <Card>
                <CardHeader title="Revenue by month" subtitle={`Last ${series.length || 12} months. Empty months show as zero.`} />
                {series.length ? (
                  <BarChart title="Revenue by month" data={series} format={(n) => money(n, cur)} seriesLabel="Revenue" />
                ) : (
                  <EmptyState icon="chart" title="No revenue yet" description="Revenue appears here after your first sale." />
                )}
              </Card>
              <Card>
                <CardHeader title="Ticket mix by tier" subtitle="Share of tickets sold" />
                {mixRows.length ? (
                  <HorizontalBars title="Ticket mix by tier" data={mixRows} format={share} />
                ) : (
                  <EmptyState icon="ticket" title="No sales yet" />
                )}
              </Card>
            </div>
          ) : null}

          <div className="oc-cols oc-section">
            <Card>
              <CardHeader title="Check-in rate" subtitle={p.checkIn ? p.checkIn.eventTitle : 'Most recent event that has run'} />
              {p.checkIn && p.checkIn.ratePercent != null ? (
                <div className="m3-row">
                  <CircularProgress value={p.checkIn.ratePercent} label="Check-in rate" showValue />
                  <div>
                    <b>{p.checkIn.ratePercent}%</b>
                    <div className="m3-muted">
                      {formatCount(p.checkIn.scanned)} of {formatCount(p.checkIn.issued)} tickets admitted
                    </div>
                  </div>
                </div>
              ) : (
                <EmptyState icon="qr" title="—" description="No event has run yet." />
              )}
            </Card>
            {p.canViewFinance ? (
              <Card>
                <CardHeader title="Payout window" subtitle="Withdrawable now versus held" />
                {p.payoutWindow ? (
                  <div className="m3-stack">
                    <div className="oc-split" role="img" aria-label="Withdrawable now versus held">
                      <i style={{ flexGrow: Number(p.payoutWindow.availableNow) || 0 }} />
                      <b style={{ flexGrow: Number(p.payoutWindow.pendingRelease) || 0 }} />
                    </div>
                    <div className="m3-kv">
                      <span>Withdrawable</span>
                      <b className="m3-mono">{money(p.payoutWindow.availableNow, p.payoutWindow.currency)}</b>
                    </div>
                    <div className="m3-kv">
                      <span>Held</span>
                      <b className="m3-mono">{money(p.payoutWindow.pendingRelease, p.payoutWindow.currency)}</b>
                    </div>
                    <LinkBtn href="/finance" variant="text" size="sm">
                      Open payouts
                    </LinkBtn>
                  </div>
                ) : (
                  <NotAvailable what="The payout window" />
                )}
              </Card>
            ) : null}
            <Card>
              <CardHeader
                title="Upcoming events"
                subtitle="Sales against ticket quantity"
                actions={
                  <LinkBtn href="/events" variant="text" size="sm">
                    All events
                  </LinkBtn>
                }
              />
              {p.upcoming.length ? (
                <ul className="m3-list">
                  {p.upcoming.map((e) => {
                    const pct = e.totalCapacity ? Math.min(100, (e.ticketsSold / e.totalCapacity) * 100) : 0;
                    return (
                      <li key={e.id} className="m3-list__item">
                        <div className="m3-list__main">
                          <Link className="m3-link" href={`/events/${e.id}`}>
                            {e.title}
                          </Link>
                          <span className="m3-list__support">
                            {formatEventDate(e.eventDateTime)} · {statusLabel(e.status)}
                          </span>
                          <div className="oc-bar" aria-hidden="true">
                            <i style={{ width: `${pct}%` }} />
                          </div>
                          <span className="m3-list__support">
                            {formatCount(e.ticketsSold)} of {formatCount(e.totalCapacity)} sold
                          </span>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              ) : (
                <EmptyState icon="calendar" title="No upcoming events" />
              )}
            </Card>
          </div>

          <div className="oc-cols oc-section">
            <Card>
              <CardHeader
                title={`Notifications${p.unreadNotifications ? ` (${p.unreadNotifications})` : ''}`}
                subtitle={`${p.unreadNotifications} unread`}
                actions={
                  p.unreadNotifications && p.onMarkAllRead ? (
                    <Button size="sm" variant="text" onClick={p.onMarkAllRead}>
                      Mark all read
                    </Button>
                  ) : null
                }
              />
              {p.notifications.length ? (
                <ul className="m3-list">
                  {p.notifications.slice(0, 5).map((n) => (
                    <li key={n.id} className="m3-list__item">
                      <div className="m3-list__main">
                        <b>
                          {n.title} {!n.readAt ? <StatusPill tone="warning">New</StatusPill> : null}
                        </b>
                        <span className="m3-list__support">{formatRelativeTime(n.createdAt)}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState icon="bell" title="You are all caught up." />
              )}
            </Card>
            <Card>
              <CardHeader title="Recent activity" subtitle="Latest across your organization" />
              {p.activity.length ? (
                <ul className="m3-list">
                  {p.activity.map((a) => (
                    <li key={a.id} className="m3-list__item">
                      <div className="m3-list__main">
                        <span>
                          <Status status={a.type} /> {a.message}
                        </span>
                        <span className="m3-list__support">{formatRelativeTime(a.timestamp)}</span>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState icon="history" title="No activity yet" />
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
