/** Display formatting for the admin console. Pure functions, no data. */

const SPECIAL: Record<string, string> = {
  PUBLISHED: 'Live',
  SUPER_ADMIN: 'Super admin',
  FINANCE_LEAD: 'Finance lead',
  KYB: 'KYB',
  TAX_CERT: 'Tax certificate',
  ID_DOCUMENT: 'ID document',
  BUSINESS_LICENSE: 'Business licence',
  NO_PUBLISHED_TIER: 'No published ticket tier',
  NO_LOCATION: 'No location set',
  NO_CAPACITY: 'No capacity set',
};

/** ACTIVE_NOW -> "Active now"; platform vocabulary wins (PUBLISHED -> "Live"). */
export function humanize(value: string | null | undefined): string {
  if (value == null || value === '') return '—';
  if (SPECIAL[value]) return SPECIAL[value];
  const s = value.replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Kwacha amount, "K 1,250" (two decimals only when needed). */
export function money(value: number | string | null | undefined): string {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) return '—';
  const abs = Math.abs(n);
  const body = abs.toLocaleString('en-GB', { minimumFractionDigits: Number.isInteger(abs) ? 0 : 2, maximumFractionDigits: 2 });
  return `${n < 0 ? '−' : ''}K ${body}`;
}

export function formatNumber(value: number | null | undefined): string {
  return Math.round(value ?? 0).toLocaleString('en-GB');
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const time = d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  return `${formatDate(iso)}, ${time}`;
}

/** "3 h ago", "2 d ago" relative to `now` (injectable for tests). */
export function ago(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '—';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '—';
  const h = Math.abs(now.getTime() - t) / 36e5;
  const text = h < 1 ? `${Math.max(1, Math.round(h * 60))} min` : h < 48 ? `${Math.round(h)} h` : `${Math.round(h / 24)} d`;
  return `${text} ago`;
}

/** Hours elapsed since an ISO timestamp. */
export function ageHours(iso: string | null | undefined, now: Date = new Date()): number {
  if (!iso) return 0;
  return (now.getTime() - new Date(iso).getTime()) / 36e5;
}

export type SlaState = 'ok' | 'warn' | 'overdue';

/** SLA clock against the configured target/warning hours. */
export function slaOf(submittedAt: string | null | undefined, slaHours: number, warnHours: number, now: Date = new Date()): { state: SlaState; label: string } {
  const h = ageHours(submittedAt, now);
  const state: SlaState = h > slaHours ? 'overdue' : h > warnHours ? 'warn' : 'ok';
  const left = slaHours - h;
  const label = state === 'overdue' ? `Overdue by ${Math.round(-left)} h` : `${Math.max(1, Math.round(left))} h left`;
  return { state, label };
}

/** Masks a phone/email/account for the contacts view (keep last 3 / first char). */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '—';
  const d = phone.replace(/\s/g, '');
  return d.length <= 4 ? d : `${d.slice(0, 4)} ••• ••${d.slice(-3)}`;
}
export function maskEmail(email: string | null | undefined): string {
  if (!email) return '—';
  const [u, host] = email.split('@');
  return host ? `${u.slice(0, 1)}•••@${host}` : email;
}

/** CSV text for "Copy CSV" on list toolbars. */
export function csvText(rows: Array<Array<string | number | null | undefined>>): string {
  const cell = (v: string | number | null | undefined) => {
    const s = String(v ?? '');
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return rows.map((r) => r.map(cell).join(',')).join('\n');
}
