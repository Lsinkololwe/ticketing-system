'use client';

import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, CardHeader, KpiCard, KpiGrid, List, ListItem } from '@pml.tickets/shared/components/m3';
import type { IconName } from '@pml.tickets/shared';
import { ROLE_DESCRIPTIONS, ROLE_LABELS, type ModuleId, type StaffRole } from '@/config/navigation';
import { hrefOf } from '@/lib/pending';
import { RoleChip } from '@/components/console/RoleChip';

/** Seconds between background refreshes of the pending-queue counts (usePendingCounts polls every 60 s). */
export const POLL_SECONDS = 60;

export interface KpiSpec {
  icon: IconName;
  label: string;
  /** undefined = backend has no value for this tile yet. */
  value: ReactNode | undefined;
  caption?: ReactNode;
  trend?: { text: string; direction: 'up' | 'down' };
  go?: { module: ModuleId; tab?: string };
  /** Exact backend operation missing when value is undefined. */
  missing?: string;
}

const KPI_COLUMNS: Record<number, { wide: number; medium: number; narrow: number }> = {
  3: { wide: 3, medium: 3, narrow: 1 },
  4: { wide: 4, medium: 2, narrow: 1 },
  6: { wide: 3, medium: 2, narrow: 1 },
  8: { wide: 4, medium: 2, narrow: 1 },
  9: { wide: 3, medium: 3, narrow: 1 },
  12: { wide: 4, medium: 2, narrow: 1 },
};

/** KPI tiles in the prototype's column layout; "View" sits inside the tile. */
export function KpiRow({ items }: { items: KpiSpec[] }) {
  const router = useRouter();
  return (
    <KpiGrid columns={KPI_COLUMNS[items.length] ?? { wide: items.length, medium: 2, narrow: 1 }}>
      {items.map((k) => (
        <KpiCard
          key={k.label}
          icon={k.icon}
          label={k.label}
          value={k.value === undefined ? 'Not available' : k.value}
          trend={k.trend}
          caption={k.value === undefined ? `Needs ${k.missing ?? 'a backend operation'}` : k.caption}
          action={
            k.go ? (
              <Button variant="text" size="sm" aria-label={`Open ${k.label}`} onClick={() => router.push(hrefOf(k.go!.module, k.go!.tab))}>
                View
              </Button>
            ) : undefined
          }
        />
      ))}
    </KpiGrid>
  );
}

export function RoleBanner({ role }: { role: StaffRole }) {
  return (
    <div className="adm-notice" role="note">
      <RoleChip role={role} />
      <span className="adm-notice__text">
        {ROLE_DESCRIPTIONS[role]} This dashboard shows what {ROLE_LABELS[role].toLowerCase()}s act on first.
      </span>
    </div>
  );
}

export function PollNote({ updatedAt }: { updatedAt: Date | null }) {
  return (
    <p className="adm-note">
      Updates every {POLL_SECONDS} seconds · last updated {updatedAt ? updatedAt.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'waiting for first update'}
    </p>
  );
}

export interface ListRow {
  id: string;
  title: ReactNode;
  support?: ReactNode;
  trailing?: ReactNode;
  go?: { label: string; href: string };
}

/** Card with a list of rows, the designed "Nothing needs attention here." empty copy and an optional footer link. */
export function ListCard({
  title,
  subtitle,
  rows,
  empty = 'Nothing needs attention here.',
  loading,
  foot,
  actions,
}: {
  title: string;
  subtitle?: string;
  rows: ListRow[];
  empty?: string;
  loading?: boolean;
  foot?: { label: string; href: string } | null;
  actions?: ReactNode;
}) {
  const router = useRouter();
  return (
    <Card>
      <CardHeader title={title} subtitle={subtitle} actions={actions} />
      {loading && rows.length === 0 ? (
        <p className="adm-note" role="status">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="adm-note">{empty}</p>
      ) : (
        <List aria-label={title}>
          {rows.map((r) => (
            <ListItem
              key={r.id}
              headline={
                r.go ? (
                  <button type="button" className="adm-rowlink" onClick={() => router.push(r.go!.href)}>
                    <b>{r.title}</b>
                    {r.support ? <small>{r.support}</small> : null}
                  </button>
                ) : (
                  <b>{r.title}</b>
                )
              }
              support={r.go ? undefined : r.support}
              trailing={r.trailing}
            />
          ))}
        </List>
      )}
      {foot ? (
        <Button variant="text" size="sm" onClick={() => router.push(foot.href)}>
          {foot.label}
        </Button>
      ) : null}
    </Card>
  );
}
