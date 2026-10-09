import { z } from 'zod';
import type { DiscoverFilter, DiscoverSort } from '@pml.tickets/shared';
import { DAY, isoDay } from '@/lib/format';

export const filterSchema = z.object({
  q: z.string(),
  categoryId: z.string(),
  city: z.string(),
  when: z.enum(['', 'week', 'month', '3m', 'custom']),
  from: z.string(),
  to: z.string(),
  min: z.string().regex(/^\d*$/, 'Enter a whole amount.'),
  max: z.string().regex(/^\d*$/, 'Enter a whole amount.'),
  sort: z.enum(['', 'hot', 'price_asc', 'price_desc']),
});

export interface FilterState {
  q: string;
  categoryId: string;
  city: string;
  when: '' | 'week' | 'month' | '3m' | 'custom';
  from: string;
  to: string;
  min: string;
  max: string;
  sort: '' | 'hot' | 'price_asc' | 'price_desc';
}

export const EMPTY_FILTERS: FilterState = { q: '', categoryId: '', city: '', when: '', from: '', to: '', min: '', max: '', sort: '' };

/** The catalog does the ordering: the Sort control maps to its EventDiscoverySort. */
export const SORT_TO_API: Record<FilterState['sort'], DiscoverSort> = { '': 'SOONEST', hot: 'POPULAR', price_asc: 'PRICE_ASC', price_desc: 'PRICE_DESC' };

export const WHEN_OPTIONS = [
  { value: '', label: 'Any date' },
  { value: 'week', label: 'Next 7 days' },
  { value: 'month', label: 'This month' },
  { value: '3m', label: 'Next 3 months' },
  { value: 'custom', label: 'Custom dates' },
];

export const SORT_OPTIONS = [
  { value: '', label: 'Soonest first' },
  { value: 'hot', label: 'Most tickets sold' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
];

/** Date window for a "When" choice; custom keeps the typed dates. */
export function whenRange(when: FilterState['when'], from: string, to: string, now = Date.now()): { from: string; to: string } {
  const today = isoDay(now);
  if (when === 'week') return { from: today, to: isoDay(now + 7 * DAY) };
  if (when === '3m') return { from: today, to: isoDay(now + 92 * DAY) };
  if (when === 'month') {
    const d = new Date(now + 2 * 3_600_000);
    const end = Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0);
    return { from: today, to: new Date(end).toISOString().slice(0, 10) };
  }
  if (when === 'custom') return { from, to };
  return { from: '', to: '' };
}

/** Number of active filters shown in "N filters active" (sort does not count). */
export function activeCount(f: FilterState): number {
  return [f.q.trim(), f.categoryId, f.city, f.from, f.to, f.min, f.max].filter((v) => v !== '').length;
}

/** Count shown on the "More filters" badge. */
export function moreCount(f: FilterState): number {
  return [f.categoryId, f.from, f.to, f.min, f.max].filter((v) => v !== '').length;
}

/** Backend filter for the discovery query. Dates are Zambian days, sent as ISO instants. */
export function toDiscoverFilter(f: FilterState): DiscoverFilter {
  const out: DiscoverFilter = {};
  if (f.q.trim()) out.searchQuery = f.q.trim();
  if (f.categoryId) out.categoryId = f.categoryId;
  if (f.city) out.cityId = f.city;
  if (f.from) out.startDate = `${f.from}T00:00:00+02:00`;
  if (f.to) out.endDate = `${f.to}T23:59:59+02:00`;
  if (f.min !== '') out.minPrice = Number(f.min);
  if (f.max !== '') out.maxPrice = Number(f.max);
  return out;
}
