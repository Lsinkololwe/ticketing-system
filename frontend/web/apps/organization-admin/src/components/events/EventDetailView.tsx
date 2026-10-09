'use client';

import { useRouter } from 'next/navigation';
import { Banner, Button, EmptyState, PageHeader, RowMenu, Skeleton, Tabs, type TabItem, KpiGrid, StatCard } from '@pml.tickets/shared/components/m3';
import type { EventStatistics, OrgEventDetail, TierInput } from '@/lib/api/events';
import type { PromoInput, PromoRow } from '@/lib/api/promos';
import { formatEventDate } from '@/lib/format/figure';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status } from '@/components/console/Status';
import { EventBookingsTab } from '@/components/bookings/EventBookingsTab';
import { EventCheckInTab } from '@/components/checkin/EventCheckInTab';
import { EventAccessTab } from '@/components/team/EventAccessTab';
import { formatCount, formatMoney } from '@/lib/format/figure';
import { actionsFor, blockersOf, type RowAction } from './eventLogic';
import { OverviewTab } from './OverviewTab';
import { TiersTab } from './TiersTab';
import { PromosTab } from './PromosTab';
import { NotifyTab } from './NotifyTab';
import { AnalyticsTab } from './AnalyticsTab';

export const EVENT_TABS: TabItem[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'tiers', label: 'Ticket tiers' },
  { id: 'promos', label: 'Promo codes' },
  { id: 'bookings', label: 'Bookings' },
  { id: 'checkin', label: 'Check-in' },
  { id: 'access', label: 'Team access' },
  { id: 'notify', label: 'Notify attendees' },
  { id: 'analytics', label: 'Analytics' },
];

export interface EventDetailViewProps {
  event: OrgEventDetail | null;
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  tab: string;
  onTab: (id: string) => void;
  canWrite: boolean;
  /** Whether the person's role may message attendees. */
  canNotify?: boolean;
  /** Percentage the platform keeps from each ticket price (5 = 5%); drives the tier commission preview. */
  commissionPercent?: number | null;
  onAction: (a: RowAction['id']) => void;
  tierActions: Parameters<typeof TiersTab>[0]['actions'];
  promos: PromoRow[];
  promoActions: Parameters<typeof PromosTab>[0]['actions'];
  stats: EventStatistics | null;
  statsLoading: boolean;
}
export type { TierInput, PromoInput };

export function EventDetailView(p: EventDetailViewProps) {
  const router = useRouter();
  const e = p.event;
  if (!e) {
    return (
      <div data-testid="event-detail">
        <PageHeader title="Event" onBack={() => router.push('/events')} backLabel="Back to events" />
        {p.error ? (
          <Banner tone="error" urgent actions={p.onRetry ? <Button size="sm" variant="tonal" onClick={p.onRetry}>Try again</Button> : undefined}>
            {p.error.message ?? 'The event could not be loaded.'}
          </Banner>
        ) : p.loading ? (
          <div className="m3-stack" role="status" aria-label="Loading event" data-testid="loading"><Skeleton /><Skeleton /><Skeleton /></div>
        ) : (
          <EmptyState icon="calendar" title="Event not found" description="It may have been deleted." action={<LinkBtn href="/events" variant="filled">Back to events</LinkBtn>} />
        )}
      </div>
    );
  }
  const tiers = e.ticketTiers ?? [];
  const menu = actionsFor(e.status).filter((a) => !['edit', 'submit', 'publish'].includes(a.id));
  const primary = actionsFor(e.status).find((a) => ['submit', 'publish'].includes(a.id));
  const tabs = EVENT_TABS.map((t) => (t.id === 'tiers' && blockersOf(e).includes('NO_PUBLISHED_TIER') ? { ...t, warning: true } : t));

  return (
    <div data-testid="event-detail">
      <PageHeader
        title={e.title || 'Untitled draft'}
        subtitle={
          <>
            <Status status={e.status} /> {e.category?.name ?? ''} · {formatEventDate(e.eventDateTime)}
            {e.cityName ? ` · ${e.cityName}` : ''}
          </>
        }
        onBack={() => router.push('/events')}
        backLabel="Back to events"
        breadcrumbs={[{ label: 'Events', href: '/events' }, { label: e.title || 'Untitled draft' }]}
        actions={
          p.canWrite ? (
            <>
              <LinkBtn href={`/events/${e.id}/edit`} variant="outlined" icon="edit">Edit details</LinkBtn>
              {primary ? <Button variant="filled" onClick={() => p.onAction(primary.id)}>{primary.label}</Button> : null}
              {menu.length ? (
                <RowMenu label="More event actions" items={menu.map((a) => ({ id: a.id, label: a.label, danger: a.danger, onSelect: () => p.onAction(a.id) }))} />
              ) : null}
            </>
          ) : null
        }
      />
      <KpiGrid>
        <StatCard label="Status" value={<Status status={e.status} />} />
        <StatCard
          label="Tickets sold"
          value={`${formatCount(e.soldTickets)} / ${formatCount(e.totalCapacity)}`}
          progress={e.totalCapacity ? Math.min(100, (e.soldTickets / e.totalCapacity) * 100) : 0}
        />
        <StatCard label="Revenue" value={formatMoney(e.revenue, e.currency)} />
        {p.stats ? <StatCard label="Refunded tickets" value={formatCount(p.stats.totalTicketsRefunded)} /> : null}
      </KpiGrid>
      <Tabs label="Event sections" tabs={tabs} value={p.tab} onChange={p.onTab} sticky>
        {(active) => {
          switch (active) {
            case 'tiers':
              return <TiersTab tiers={tiers} canWrite={p.canWrite} actions={p.tierActions} commissionPercent={p.commissionPercent} />;
            case 'promos':
              return <PromosTab promos={p.promos} tiers={tiers} canWrite={p.canWrite} actions={p.promoActions} />;
            case 'bookings':
              return <EventBookingsTab eventId={e.id} />;
            case 'checkin':
              return <EventCheckInTab eventId={e.id} />;
            case 'access':
              return <EventAccessTab eventId={e.id} />;
            case 'notify':
              return <NotifyTab eventId={e.id} tiers={tiers.map((t) => ({ id: t.id, name: t.name }))} canNotify={p.canNotify ?? p.canWrite} />;
            case 'analytics':
              return <AnalyticsTab eventId={e.id} stats={p.stats} tiers={tiers} loading={p.statsLoading} />;
            default:
              return <OverviewTab event={e} onAction={p.onAction} onGoTab={p.onTab} />;
          }
        }}
      </Tabs>
    </div>
  );
}
