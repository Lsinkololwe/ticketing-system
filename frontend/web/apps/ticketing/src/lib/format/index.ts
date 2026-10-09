/** Display helpers. Zambia is UTC+2 all year, so dates render in that zone regardless of the browser. */
const DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];
const HR = 3_600_000;
export const DAY = 24 * HR;

const pad = (n: number) => String(n).padStart(2, "0");
const ms = (v: string | number | Date) =>
  typeof v === "number" ? v : new Date(v).getTime();
const lz = (v: string | number | Date) => new Date(ms(v) + 2 * HR);

/** "K 1,500" (the buyer app always shows the Kwacha symbol first). */
export function money(n: number | string | null | undefined): string {
  if (n === null || n === undefined || n === "") return "—";
  const v = Number(n);
  if (!Number.isFinite(v)) return "—";
  return `K ${v.toLocaleString("en-US", { maximumFractionDigits: 2 })}`;
}

/** "Sat 14 Nov 2026" */
export const fullDate = (v: string | number | Date) => {
  const d = lz(v);
  return `${DOW[d.getUTCDay()]} ${d.getUTCDate()} ${MONS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
};
/** "14 Nov" */
export const shortDate = (v: string | number | Date) => {
  const d = lz(v);
  return `${d.getUTCDate()} ${MONS[d.getUTCMonth()]}`;
};
/** "17:00" */
export const clock = (v: string | number | Date) => {
  const d = lz(v);
  return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
};
export const monthShort = (v: string | number | Date) =>
  MONS[lz(v).getUTCMonth()];
export const dayOfMonth = (v: string | number | Date) =>
  String(lz(v).getUTCDate());
/** "2026-11-14" in Zambian time, for date inputs. */
export const isoDay = (v: string | number | Date) =>
  lz(v).toISOString().slice(0, 10);

/** Whole days until `v` (0 when already started). */
export function daysTo(v: string | number | Date, now = Date.now()): number {
  return Math.max(0, Math.ceil((ms(v) - now) / DAY));
}

/** Long countdown: "2d 5h 12m", "5h 12m 04s" or "12:04". */
export function countdown(msLeft: number): string {
  const t = Math.max(0, msLeft);
  const s = Math.floor(t / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  return d
    ? `${d}d ${h}h ${m}m`
    : h
      ? `${h}h ${m}m ${pad(s % 60)}s`
      : `${pad(m)}:${pad(s % 60)}`;
}

/** Hold timer: "9:41". */
export function mmss(msLeft: number): string {
  const s = Math.max(0, Math.ceil(msLeft / 1000));
  return `${Math.floor(s / 60)}:${pad(s % 60)}`;
}

/** "5 min ago" style relative time. */
export function ago(v: string | number | Date, now = Date.now()): string {
  const m = Math.round((now - ms(v)) / 60_000);
  return m < 1
    ? "Just now"
    : m < 60
      ? `${m} min ago`
      : m < 1440
        ? `${Math.round(m / 60)} h ago`
        : `${Math.round(m / 1440)} d ago`;
}

/** "24 hours" / "7 days" */
export const hoursText = (h: number) =>
  h % 24 === 0 && h >= 48 ? `${h / 24} days` : `${h} hour${h === 1 ? "" : "s"}`;

export const initials = (name: string, n = 2) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .map((x) => x[0])
    .join("")
    .slice(0, n)
    .toUpperCase();

/** "+260 97 7123456" from any Zambian number input. Returns null when it is not a mobile number. */
export function parseZmMobile(v: string): string | null {
  let d = String(v).replace(/\D/g, "");
  if (d.startsWith("260")) d = d.slice(3);
  if (d.startsWith("0")) d = d.slice(1);
  return /^(95|96|97|76|77)\d{7}$/.test(d) ? d : null;
}
export const fmtZmPhone = (d: string) => `+260 ${d.slice(0, 2)} ${d.slice(2)}`;
