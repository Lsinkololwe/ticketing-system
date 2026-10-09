'use client';

import { useParams, usePathname, useRouter, useSearchParams } from 'next/navigation';
import { EventDetailView, EVENT_TABS } from '@/components/events/EventDetailView';
import { useLifecycle } from '@/components/events/LifecycleDialogs';
import { useEventStatistics, useOrgEventDetail, useTierActions } from '@/lib/api/events';
import { useOrgContext } from '@/lib/api/org-context';
import { usePlatformRules } from '@/lib/api/platform';
import { useEventPromos, usePromoActions } from '@/lib/api/promos';

export default function EventDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const { capabilities, organization } = useOrgContext();
  const { rules } = usePlatformRules();
  const commissionPercent = organization?.commissionRate ?? rules?.commissionRate ?? rules?.commissionDefault ?? null;
  const tabParam = search.get('tab') ?? 'overview';
  const tab = EVENT_TABS.some((t) => t.id === tabParam) ? tabParam : 'overview';

  const { event, loading, error, refetch } = useOrgEventDetail(id);
  const { promos } = useEventPromos(id);
  const { stats, loading: statsLoading } = useEventStatistics(id);
  const tierActions = useTierActions(id);
  const promoActions = usePromoActions(id);
  const { run, dialogs } = useLifecycle((what) => {
    if (what === 'delete') router.push('/events');
    else void refetch();
  });

  return (
    <>
      <EventDetailView
        event={event}
        loading={loading}
        error={error}
        onRetry={() => void refetch()}
        tab={tab}
        onTab={(t) => router.replace(`${pathname}?tab=${t}`)}
        canWrite={capabilities.canWriteEvents}
        commissionPercent={commissionPercent}
        canNotify={capabilities.canNotify}
        onAction={(a) => event && void run(a, { ...event, soldTickets: event.soldTickets })}
        tierActions={tierActions}
        promos={promos}
        promoActions={promoActions}
        stats={stats}
        statsLoading={statsLoading}
      />
      {dialogs}
    </>
  );
}
