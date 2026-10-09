import { z } from 'zod';

/** Trimmed, non-empty string. */
export function nonEmptyTrimmed(label = 'This field', opts: { max?: number } = {}) {
  let s = z.string({ error: 'Required' }).trim().min(1, 'Required');
  if (opts.max) s = s.max(opts.max, `${label} must be at most ${opts.max} characters`);
  return s;
}

export function email() {
  return z
    .string({ error: 'Enter an email address' })
    .trim()
    .toLowerCase()
    .min(1, 'Enter an email address')
    .pipe(z.email({ error: 'Enter a valid email address' }));
}

/** Six digits, as typed into the OTP boxes. */
export const otp6 = () =>
  z
    .string({ error: 'Enter the 6-digit code' })
    .regex(/^\d{6}$/, 'Enter the 6-digit code');

/** Lower-case URL slug: a-z, 0-9, single hyphens, no leading/trailing hyphen. */
export function slug(opts: { min?: number; max?: number } = {}) {
  const { min = 3, max = 60 } = opts;
  return z
    .string({ error: 'Required' })
    .trim()
    .toLowerCase()
    .min(min, `Use at least ${min} characters`)
    .max(max, `Use at most ${max} characters`)
    .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, 'Use lowercase letters, numbers and single hyphens');
}

/** Absolute http(s) URL. */
export function url() {
  return z
    .string({ error: 'Enter a web address' })
    .trim()
    .min(1, 'Enter a web address')
    .refine(
      (v) => {
        try {
          const u = new URL(v);
          return u.protocol === 'http:' || u.protocol === 'https:';
        } catch {
          return false;
        }
      },
      { error: 'Enter a valid web address starting with https://' }
    );
}

/** Whole or fractional percentage 0..100. Accepts number or numeric string. */
export function percent(opts: { min?: number; max?: number; decimals?: number } = {}) {
  const { min = 0, max = 100, decimals = 2 } = opts;
  return z
    .union([z.number(), z.string().trim().min(1)], { error: 'Enter a percentage' })
    .transform((v, ctx) => {
      const n = typeof v === 'number' ? v : Number(v);
      if (!Number.isFinite(n)) {
        ctx.issues.push({ code: 'custom', message: 'Enter a percentage', input: v });
        return z.NEVER;
      }
      if (n < min || n > max) {
        ctx.issues.push({ code: 'custom', message: `Enter a value between ${min} and ${max}`, input: v });
        return z.NEVER;
      }
      if (Math.abs(n * 10 ** decimals - Math.round(n * 10 ** decimals)) > 1e-9) {
        ctx.issues.push({ code: 'custom', message: `Use at most ${decimals} decimal places`, input: v });
        return z.NEVER;
      }
      return n;
    });
}
