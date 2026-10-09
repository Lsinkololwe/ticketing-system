'use client';

import type { UserType } from '@pml.tickets/shared/types/graphql';
import { enumValues } from '@/lib/enumLabels';
import { ErrorState, Heatmap, Skeleton, StackedBarChart } from '@pml.tickets/shared/components/m3';
import { useUserGrowthSeries } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { usePurchasesByDayAndHour } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { formatNumber } from '@/lib/format';

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const HOURS = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'));

/** Purchases by weekday and hour in Lusaka time (purchasesByDayAndHour), for the selected period. */
export function PurchaseHeat({ from, to }: { from: string; to: string }) {
  const { cells, loading, error, refetch } = usePurchasesByDayAndHour({ from, to });
  if (error && cells.length === 0) return <ErrorState error={error} onRetry={refetch} />;
  if (loading && cells.length === 0) return <Skeleton width="100%" />;
  const values = DAYS.map(() => HOURS.map(() => 0));
  for (const c of cells) {
    const d = c.dayOfWeek - 1;
    if (values[d] && c.hour >= 0 && c.hour < 24) values[d][c.hour] = c.purchases;
  }
  if (cells.every((c) => c.purchases === 0)) return <p>No purchases in this period.</p>;
  return <Heatmap title="Purchases by day and hour" description="Number of purchases for each weekday and hour, Lusaka time" rows={DAYS} cols={HOURS} values={values} format={(n) => formatNumber(n)} />;
}

/** The account types the growth chart splits by, labelled. Typed from the generated user type. */
const GROWTH_ROLES: Record<Extract<UserType, 'CUSTOMER' | 'ORGANIZER' | 'ADMIN'>, string> = {
  CUSTOMER: 'Buyers',
  ORGANIZER: 'Organizers',
  ADMIN: 'Staff',
};
const [ROLE_A, ROLE_B, ROLE_C] = enumValues(GROWTH_ROLES);

/** New accounts per month by type over the last 12 months (userGrowthSeries). */
export function NewAccounts({ from, to }: { from: string; to: string }) {
  const a = useUserGrowthSeries({ from, to, bucket: 'MONTH', role: ROLE_A });
  const b = useUserGrowthSeries({ from, to, bucket: 'MONTH', role: ROLE_B });
  const c = useUserGrowthSeries({ from, to, bucket: 'MONTH', role: ROLE_C });
  const all = [a, b, c];
  const error = all.find((q) => q.error)?.error;
  const loading = all.some((q) => q.loading);
  if (error && all.every((q) => q.points.length === 0)) return <ErrorState error={error} onRetry={() => all.forEach((q) => q.refetch())} />;
  if (loading && all.every((q) => q.points.length === 0)) return <Skeleton width="100%" />;
  const labels = a.points.map((p) => new Date(p.bucketStart).toLocaleDateString('en-GB', { month: 'short', year: '2-digit' }));
  if (labels.length === 0) return <p>No new accounts in this period.</p>;
  return (
    <StackedBarChart
      title="Stacked columns of new accounts by type per month"
      labels={labels}
      series={all.map((q, i) => ({ label: GROWTH_ROLES[[ROLE_A, ROLE_B, ROLE_C][i]], values: q.points.map((p) => p.newUsers) }))}
      format={(n) => formatNumber(n)}
    />
  );
}
