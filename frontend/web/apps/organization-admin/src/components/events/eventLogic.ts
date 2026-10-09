import type { EventStatus, OrgEventRow } from '@/lib/api/events';

export const EVENT_STATUSES: EventStatus[] = [
  'PUBLISHED',
  'DRAFT',
  'PENDING_APPROVAL',
  'CHANGES_REQUESTED',
  'APPROVED',
  'REJECTED',
  'COMPLETED',
  'CANCELLED',
];

export type ApprovalBlocker = 'NO_PUBLISHED_TIER' | 'NO_LOCATION' | 'NO_CAPACITY';

export const BLOCKER_TEXT: Record<ApprovalBlocker, string> = {
  NO_PUBLISHED_TIER: 'At least one active ticket tier',
  NO_LOCATION: 'A venue',
  NO_CAPACITY: 'A capacity above zero',
};

/** Outstanding approval blockers, computed from what the event holds. */
export interface BlockerInput {
  locationName: string | null;
  totalCapacity: number;
  ticketTiers: Array<{ isActive: boolean }> | null;
}
export function blockersOf(e: BlockerInput): ApprovalBlocker[] {
  const out: ApprovalBlocker[] = [];
  if (!(e.ticketTiers ?? []).some((t) => t.isActive)) out.push('NO_PUBLISHED_TIER');
  if (!e.locationName) out.push('NO_LOCATION');
  if (!(e.totalCapacity > 0)) out.push('NO_CAPACITY');
  return out;
}

export interface RowAction {
  id: 'edit' | 'submit' | 'publish' | 'unpublish' | 'reschedule' | 'cancel' | 'duplicate' | 'delete';
  label: string;
  danger?: boolean;
}

/** Lifecycle actions offered for a status (organization requires approval before going live). */
export function actionsFor(status: EventStatus): RowAction[] {
  const a: RowAction[] = [];
  if (['DRAFT', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED'].includes(status)) a.push({ id: 'edit', label: 'Edit details' });
  if (status === 'DRAFT') a.push({ id: 'submit', label: 'Submit for approval' });
  if (status === 'CHANGES_REQUESTED') a.push({ id: 'submit', label: 'Resubmit for approval' });
  if (status === 'APPROVED') a.push({ id: 'publish', label: 'Publish' });
  if (status === 'PUBLISHED') a.push({ id: 'unpublish', label: 'Unpublish' });
  if (['APPROVED', 'PUBLISHED'].includes(status)) {
    a.push({ id: 'reschedule', label: 'Reschedule' });
    a.push({ id: 'cancel', label: 'Cancel event', danger: true });
  }
  a.push({ id: 'duplicate', label: 'Duplicate' });
  if (status === 'DRAFT') a.push({ id: 'delete', label: 'Delete draft', danger: true });
  return a;
}

export function whyCannotPublish(e: BlockerInput & { status: string }): string[] {
  const r: string[] = [];
  if (e.status !== 'APPROVED') r.push('Events must be approved by the platform before they can go live.');
  blockersOf(e).forEach((b) => r.push(`Missing: ${BLOCKER_TEXT[b].toLowerCase()}.`));
  return r;
}

export type SortKey = 'date_asc' | 'date_desc' | 'name' | 'revenue' | 'sold';

export interface EventFilters {
  q: string;
  status: 'all' | EventStatus;
  category: string;
  when: 'all' | 'upcoming' | 'past' | 'soon';
  sort: SortKey;
}
export const DEFAULT_FILTERS: EventFilters = { q: '', status: 'all', category: 'all', when: 'all', sort: 'date_asc' };

export function filterEvents(list: OrgEventRow[], f: EventFilters, now: Date): OrgEventRow[] {
  const q = f.q.trim().toLowerCase();
  const t0 = now.getTime();
  const in30 = t0 + 30 * 864e5;
  const out = list.filter((e) => {
    const t = new Date(e.eventDateTime).getTime();
    return (
      (f.status === 'all' || e.status === f.status) &&
      (f.category === 'all' || e.category?.name === f.category) &&
      (!q || `${e.title} ${e.locationName ?? ''} ${e.cityName ?? ''}`.toLowerCase().includes(q)) &&
      (f.when === 'all' || (f.when === 'upcoming' ? t >= t0 : f.when === 'past' ? t < t0 : t >= t0 && t <= in30))
    );
  });
  const rev = (e: OrgEventRow) => Number(e.revenue ?? 0);
  const cmp: Record<SortKey, (a: OrgEventRow, b: OrgEventRow) => number> = {
    date_asc: (a, b) => a.eventDateTime.localeCompare(b.eventDateTime),
    date_desc: (a, b) => b.eventDateTime.localeCompare(a.eventDateTime),
    name: (a, b) => a.title.localeCompare(b.title),
    revenue: (a, b) => rev(b) - rev(a),
    sold: (a, b) => b.soldTickets - a.soldTickets,
  };
  return out.sort(cmp[f.sort]);
}
