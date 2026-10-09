/** Small pure helpers shared by the Events module screens. */
import type { EventStatusValue } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { EVENT_STATUS_LABELS, enumValues } from '@/lib/enumLabels';

export const EVENT_STATUSES: EventStatusValue[] = enumValues(EVENT_STATUS_LABELS);

/** Statuses an admin may cancel from. */
export const CANCELLABLE: string[] = ['PUBLISHED', 'APPROVED'];

export const COUNTRY = 'Zambia';

export const DATE_FILTERS = [
  { value: 'next30', label: 'Next 30 days' },
  { value: 'later', label: 'Later' },
  { value: 'past', label: 'Past' },
];

/** ISO bounds for the Date filter, relative to `now`. */
export function dateBounds(kind: string, now: Date = new Date()): { after: string | null; before: string | null } {
  const in30 = new Date(now.getTime() + 30 * 24 * 3600 * 1000);
  if (kind === 'past') return { after: null, before: now.toISOString() };
  if (kind === 'next30') return { after: now.toISOString(), before: in30.toISOString() };
  if (kind === 'later') return { after: in30.toISOString(), before: null };
  return { after: null, before: null };
}

export const BLOCKER_LABEL: Record<string, string> = {
  NO_PUBLISHED_TIER: 'At least one published ticket tier',
  NO_LOCATION: 'Venue and location set',
  NO_CAPACITY: 'Capacity set',
};

export const BLOCKER_HINT: Record<string, string> = {
  NO_PUBLISHED_TIER: 'the organizer must publish a tier',
  NO_LOCATION: 'venue is missing',
  NO_CAPACITY: 'capacity is zero',
};

/** Number of approval steps passed (0 to 4) for the progress strip. */
export function approvalStep(status: string): number {
  switch (status) {
    case 'DRAFT':
      return 1;
    case 'PENDING_APPROVAL':
    case 'CHANGES_REQUESTED':
    case 'REJECTED':
      return 2;
    case 'APPROVED':
      return 3;
    default:
      return 4;
  }
}
export const APPROVAL_STEPS = ['Draft', 'In review', 'Approved', 'Live'];

export function percent(part: number, whole: number): number {
  return whole > 0 ? Math.min(100, Math.round((part / whole) * 100)) : 0;
}
