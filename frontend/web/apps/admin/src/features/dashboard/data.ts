'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { usePendingCounts } from '@pml.tickets/shared';
import { money } from '@/lib/format';

const DAY = 864e5;

export interface DateRange {
  startDate: string;
  endDate: string;
}

/** Stable report ranges (computed once per mount so queries do not refetch every render). */
export function useReportRanges() {
  return useMemo(() => {
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const iso = (d: Date) => d.toISOString();
    const range = (from: Date, to: Date): DateRange => ({ startDate: iso(from), endDate: iso(to) });
    return {
      today: range(startOfToday, now),
      last30: range(new Date(now.getTime() - 30 * DAY), now),
      prior30: range(new Date(now.getTime() - 60 * DAY), new Date(now.getTime() - 30 * DAY)),
      last12m: range(new Date(now.getFullYear(), now.getMonth() - 11, 1), now),
    };
  }, []);
}

/** Percentage change as a KPI trend, or undefined when there is no prior value to compare with. */
export function trendOf(current: number | string | null | undefined, prior: number | string | null | undefined) {
  const c = Number(current ?? NaN);
  const p = Number(prior ?? NaN);
  if (!Number.isFinite(c) || !Number.isFinite(p) || p <= 0) return undefined;
  const pct = Math.round(((c - p) / p) * 100);
  return { text: `${Math.abs(pct)}%`, direction: (pct >= 0 ? 'up' : 'down') as 'up' | 'down' };
}

/** Pending-queue counts plus the time they last finished loading (for the "last updated" note). */
export function useTimedPendingCounts() {
  const res = usePendingCounts();
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const seen = useRef(false);
  useEffect(() => {
    if (!res.loading && (!seen.current || res.counts)) {
      seen.current = true;
      setUpdatedAt(new Date());
    }
  }, [res.loading, res.counts]);
  return { ...res, updatedAt };
}

export function chartPoints(
  rows: Array<{ period: string; revenue: number | string }> | undefined
): Array<{ label: string; value: number }> {
  return (rows ?? []).map((r) => ({ label: r.period, value: Number(r.revenue) || 0 }));
}

export const fmtMoney = (n: number) => money(n);
