import { z } from 'zod';

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Whether `value` is a real calendar date written YYYY-MM-DD. */
export function isRealIsoDate(value: string): boolean {
  const m = ISO_DATE.exec(value);
  if (!m) return false;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const dt = new Date(Date.UTC(y, mo - 1, d));
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === mo - 1 && dt.getUTCDate() === d;
}

/** Calendar date, YYYY-MM-DD (what `DateRHF` stores). */
export function isoDate(opts: { min?: string; max?: string; message?: string } = {}) {
  const message = opts.message ?? 'Enter a valid date';
  return z
    .string({ error: 'Choose a date' })
    .min(1, 'Choose a date')
    .refine(isRealIsoDate, { error: message })
    .refine((v) => !opts.min || v >= opts.min, { error: `Date must be on or after ${opts.min}` })
    .refine((v) => !opts.max || v <= opts.max, { error: `Date must be on or before ${opts.max}` });
}

/** HH:mm, 24 hour (what `TimeRHF` stores). */
export function isoTime() {
  return z
    .string({ error: 'Choose a time' })
    .min(1, 'Choose a time')
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Enter a valid time');
}

/**
 * Start/end pair where the end must come after the start. Errors land on `end`
 * so the message sits beside the field to fix.
 */
export function dateRange<K extends string = 'start', E extends string = 'end'>(
  opts: { startKey?: K; endKey?: E; allowSame?: boolean; message?: string } = {}
) {
  const startKey = (opts.startKey ?? 'start') as K;
  const endKey = (opts.endKey ?? 'end') as E;
  return z
    .object({ [startKey]: isoDate(), [endKey]: isoDate() } as Record<K | E, ReturnType<typeof isoDate>>)
    .superRefine((value, ctx) => {
      const rec = value as Record<string, string>;
      const s = rec[startKey];
      const e = rec[endKey];
      if (!s || !e) return;
      const bad = opts.allowSame ? e < s : e <= s;
      if (bad) {
        ctx.addIssue({ code: 'custom', path: [endKey], message: opts.message ?? 'End must be after start' });
      }
    });
}

/**
 * Refinement helper for flat form schemas that hold start and end as
 * separate keys: `schema.superRefine(endAfterStart('startsAt', 'endsAt'))`.
 */
export function endAfterStart<T extends Record<string, unknown>>(startKey: keyof T & string, endKey: keyof T & string, message = 'End must be after start') {
  return (value: T, ctx: z.RefinementCtx) => {
    const s = value[startKey];
    const e = value[endKey];
    if (typeof s === 'string' && typeof e === 'string' && s && e && e <= s) {
      ctx.addIssue({ code: 'custom', path: [endKey], message });
    }
  };
}
