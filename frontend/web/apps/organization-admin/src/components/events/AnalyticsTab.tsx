'use client';

import { enumValues, SALES_BUCKET_LABELS } from '@/lib/format/enumLabels';
import { useState } from 'react';
import { Card, CardHeader, DonutChart, KpiCard, KpiGrid, EmptyState, ErrorState, LineChart, SegmentedButton, Skeleton } from '@pml.tickets/shared/components/m3';
import type { SalesBucket } from '@pml.tickets/shared/types/graphql';
import { usePurchaseHeatmap, useSalesSeries } from '@/lib/api/event-insights';
import { PurchaseHeat } from './PurchaseHeat';
import type { EventStatistics, OrgTier } from '@/lib/api/events';
import { formatCount, formatMoney } from '@/lib/format/figure';

function SalesOverTime({ eventId }: { eventId: string }) {
  const [bucket, setBucket] = useState<SalesBucket>('DAY');
  const { points, loading, error } = useSalesSeries(eventId, bucket, { from: null, to: null });
  const label = (iso: string) => new Date(iso).toLocaleDateString('en-GB', bucket === 'HOUR' ? { day: 'numeric', month: 'short', hour: '2-digit' } : { day: 'numeric', month: 'short' });
  return (
    <Card>
      <CardHeader
        title="Sales over time"
        actions={
          <SegmentedButton
            label="Group by"
            value={bucket}
            onChange={setBucket}
            options={enumValues(SALES_BUCKET_LABELS).map((value) => ({ value, label: SALES_BUCKET_LABELS[value] }))}
          />
        }
      />
      {error && points.length === 0 ? (
        <ErrorState error={error} variant="inline" />
      ) : loading && points.length === 0 ? (
        <Skeleton />
      ) : points.length === 0 ? (
        <EmptyState icon="chart" title="No sales in this period" />
      ) : (
        <LineChart
          title="Tickets sold"
          labels={points.map((p) => label(p.bucketStart))}
          series={[{ label: 'Tickets', values: points.map((p) => p.tickets) }]}
          format={(n) => formatCount(n)}
        />
      )}
    </Card>
  );
}

function BuyingHours({ eventId }: { eventId: string }) {
  const { cells, loading, error } = usePurchaseHeatmap(eventId);
  return (
    <Card>
      <CardHeader title="When people buy" subtitle="Purchases by day of week and hour." />
      {error && cells.length === 0 ? <ErrorState error={error} variant="inline" /> : loading && cells.length === 0 ? <Skeleton /> : <PurchaseHeat cells={cells} />}
    </Card>
  );
}

export function AnalyticsTab({ eventId, stats, tiers, loading }: { eventId: string; stats: EventStatistics | null; tiers: OrgTier[]; loading: boolean }) {
  const mix = tiers.filter((t) => t.soldQuantity > 0).map((t) => ({ label: t.name, value: t.soldQuantity }));
  if (!stats && !loading) return <Card><EmptyState icon="chart" title="No analytics yet" description="Sales figures appear after the first ticket sells." /></Card>;
  return (
    <div className="m3-stack">
      <KpiGrid>
        <KpiCard label="Tickets sold" icon="ticket" value={formatCount(stats?.totalTicketsSold ?? 0)} caption={stats ? `${Math.round(stats.overallSalesPercentage)}% of ${formatCount(stats.totalTicketsAvailable + stats.totalTicketsSold)}` : undefined} />
        <KpiCard label="Gross revenue" icon="money" value={formatMoney(stats?.totalGrossRevenue ?? 0)} />
        <KpiCard label="Commission" icon="receipt" value={formatMoney(stats?.totalCommissionEarned ?? 0)} />
        <KpiCard label="Refunded tickets" icon="swap" value={formatCount(stats?.totalTicketsRefunded ?? 0)} />
      </KpiGrid>
      <div className="oc-cols oc-section">
        <Card>
          <CardHeader title="Tier mix" subtitle="Share of tickets sold" />
          {mix.length ? <DonutChart title="Tier mix" data={mix} format={(n) => formatCount(n)} /> : <EmptyState icon="ticket" title="No sales yet" />}
        </Card>
        <SalesOverTime eventId={eventId} />
      </div>
      <BuyingHours eventId={eventId} />
    </div>
  );
}
