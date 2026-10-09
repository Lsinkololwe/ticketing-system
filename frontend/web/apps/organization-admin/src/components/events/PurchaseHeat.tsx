'use client';

import { EmptyState } from '@pml.tickets/shared/components/m3';
import type { HeatCell } from '@/lib/api/event-insights';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

/** Day-of-week by hour grid. Darker cells mean more purchases; every cell states its count for assistive tech. */
export function PurchaseHeat({ cells }: { cells: HeatCell[] }) {
  if (cells.length === 0) return <EmptyState icon="chart" title="No purchases yet" description="Buying patterns appear after the first sale." />;
  const max = Math.max(1, ...cells.map((c) => c.purchases));
  // dayOfWeek is 1 (Monday) to 7 (Sunday).
  const at = (d: number, h: number) => cells.find((c) => c.dayOfWeek === d && c.hour === h)?.purchases ?? 0;
  return (
    <div className="oc-heat" role="table" aria-label="Purchases by day and hour">
      {DAYS.map((name, i) => (
        <div key={name} role="row" className="m3-row">
          <span role="rowheader" className="m3-muted">{name.slice(0, 3)}</span>
          {Array.from({ length: 24 }, (_, h) => {
            const n = at(i + 1, h);
            return (
              <span
                key={h}
                role="cell"
                className="oc-heat__cell"
                data-level={n === 0 ? 0 : Math.max(1, Math.ceil((n / max) * 4))}
                aria-label={`${name} ${String(h).padStart(2, '0')}:00, ${n} ${n === 1 ? 'purchase' : 'purchases'}`}
                title={`${n}`}
              />
            );
          })}
        </div>
      ))}
    </div>
  );
}
