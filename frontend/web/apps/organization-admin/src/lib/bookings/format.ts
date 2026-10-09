import { BOOKING_STATUS_LABELS, enumValues } from '@/lib/format/enumLabels';
import { formatMoney } from '@/lib/format/figure';

/** "4 Oct 2026, 14:05" or an em dash. */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const date = d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  const time = d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  return `${date}, ${time}`;
}

export const kwacha = (amount: number | string | null | undefined, currency?: string | null) =>
  formatMoney(amount, currency, { decimals: 2 });

/** +260 97 7 000 112 style display for a stored E.164 number. */
export function showPhone(value?: string | null): string {
  if (!value) return '—';
  const p = value.replace(/[\s-]/g, '');
  return /^\+260\d{9}$/.test(p) ? `+260 ${p.slice(4, 6)}${p.slice(6, 7)} ${p.slice(7, 10)} ${p.slice(10)}` : value;
}

export const BOOKING_STATUSES = enumValues(BOOKING_STATUS_LABELS);
