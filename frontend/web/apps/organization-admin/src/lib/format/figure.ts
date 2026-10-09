import type { EventStatus } from '@pml.tickets/shared/types/graphql';
/**
 * Figure formatting for the organizer portal.
 *
 * One module so a currency symbol, a month label or a delta is never formatted
 * two different ways on two different screens.
 *
 * House rules, from the design system's content fundamentals:
 *   - Local currency is ALWAYS "K 125,430". Never "ZMW", never "$", never a
 *     symbol jammed against the digits.
 *   - Figures use tabular numerals so columns of digits line up and stat tiles
 *     do not jitter between renders. That is applied by `.ds-amount` /
 *     `.viz-figure-core`; this module only produces the string.
 *   - Enum values are humanised, never shown raw: PENDING_REVIEW → "Pending
 *     review", PUBLISHED → "Live".
 */

/** ISO-4217 codes that render as a bare symbol rather than the code. */
const CURRENCY_SYMBOLS: Record<string, string> = {
  ZMW: 'K',
};

/**
 * Format an amount as money.
 *
 * Amounts arrive from GraphQL as BigDecimal strings, so they are parsed here
 * rather than upstream — parsing early would lose precision on the wire for no
 * benefit.
 *
 * @param amount   BigDecimal string, number, or null
 * @param currency ISO-4217 code; defaults to ZMW
 * @param options  `decimals` forces a fixed fraction length (payout figures
 *                 want 2; dashboard headline figures want 0)
 */
export function formatMoney(
  amount?: string | number | null,
  currency?: string | null,
  options?: { decimals?: number }
): string {
  const value = Number(amount ?? 0);
  const safe = Number.isFinite(value) ? value : 0;
  const code = currency ?? 'ZMW';
  const symbol = CURRENCY_SYMBOLS[code] ?? code;
  const decimals = options?.decimals;

  const digits = safe.toLocaleString('en-GB', {
    minimumFractionDigits: decimals ?? 0,
    maximumFractionDigits: decimals ?? 2,
  });

  return `${symbol} ${digits}`;
}

/** Whole-number count with thousands separators. */
export function formatCount(value?: number | string | null): string {
  const n = Number(value ?? 0);
  return (Number.isFinite(n) ? n : 0).toLocaleString('en-GB');
}

/**
 * Split "K 125,430" / "8,234" / "87%" into a lighter unit and the bold numeral
 * core, so a figure reads as one number rather than a wall of heavy glyphs.
 *
 * This is the design system's StatCard treatment, lifted out so the stat tiles
 * and the chart tiles render figures identically.
 */
export function splitFigure(value: string | number): {
  prefix: string;
  core: string;
  suffix: string;
} {
  const text = String(value);
  const match = text.match(/^([A-Za-z]{1,3}\s?)?([\d.,]+)(\s?[A-Za-z%]{1,3})?$/);
  if (!match) return { prefix: '', core: text, suffix: '' };
  return {
    prefix: (match[1] ?? '').trim(),
    core: match[2],
    suffix: (match[3] ?? '').trim(),
  };
}

/**
 * Short month label for a chart axis, from an ISO-8601 date ("2026-03-01").
 *
 * Parsed as a plain date rather than through `new Date(iso)`, which would treat
 * the string as UTC midnight and can render the previous month for users west
 * of Greenwich — exactly the bug the server avoids by sending a date string.
 */
export function monthLabel(isoDate: string): string {
  const [year, month] = isoDate.split('-').map(Number);
  if (!year || !month) return isoDate;
  return new Date(year, month - 1, 1).toLocaleDateString('en-GB', { month: 'short' });
}

/** "Mar 2026" — used where a bare month would be ambiguous across a year edge. */
export function monthYearLabel(isoDate: string): string {
  const [year, month] = isoDate.split('-').map(Number);
  if (!year || !month) return isoDate;
  return new Date(year, month - 1, 1).toLocaleDateString('en-GB', {
    month: 'short',
    year: 'numeric',
  });
}

/** Absolute event date, e.g. "12 Aug 2026". */
export function formatEventDate(iso?: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

/**
 * Relative time for the activity feed: "2m ago", "3h ago", "5d ago".
 *
 * `now` is injectable so tests do not depend on the wall clock.
 */
export function formatRelativeTime(iso?: string | null, now: number = Date.now()): string {
  if (!iso) return '';
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';

  const minutes = Math.round((now - then) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;

  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;

  return `${Math.round(hours / 24)}d ago`;
}

/**
 * Percentage change between two values, rounded to one decimal.
 *
 * Returns null when there is no meaningful baseline. A delta against zero is
 * not "+100%", it is undefined — and printing a number there would assert a
 * finding the data does not support.
 */
export function percentChange(previous: number, current: number): number | null {
  if (!Number.isFinite(previous) || !Number.isFinite(current) || previous === 0) return null;
  return Math.round(((current - previous) / previous) * 1000) / 10;
}

/** Direction of a delta, for the trend tokens. */
export function trendOf(change?: number | null): 'up' | 'down' | 'neutral' {
  if (change == null || !Number.isFinite(change) || change === 0) return 'neutral';
  return change > 0 ? 'up' : 'down';
}

/**
 * Humanise a backend enum: PENDING_REVIEW → "Pending review".
 *
 * `overrides` carries the cases where the product word differs from the enum,
 * e.g. PUBLISHED renders as "Live".
 */
export function humanizeStatus(
  status?: string | null,
  overrides: Record<string, string> = {}
): string {
  if (!status) return '';
  const key = status.toUpperCase();
  if (overrides[key]) return overrides[key];
  const words = key.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Event status wording used across the organizer portal. */
export const EVENT_STATUS_LABELS: Partial<Record<EventStatus | 'PENDING_REVIEW', string>> = {
  PUBLISHED: 'Live',
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending review',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Ended',
};
