import { z } from 'zod';

/** Parse "1,250.50" / "1250.5" / 1250.5 to ngwee (minor units), or undefined when not a money amount (more than 2 dp, negative, junk). */
export function parseKwachaToMinor(input: string | number | null | undefined): number | undefined {
  if (input === null || input === undefined) return undefined;
  const text = String(input).replace(/[\s,]/g, '').replace(/^K/i, '');
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return undefined;
  const [whole, frac = ''] = text.split('.');
  const minor = Number(whole) * 100 + Number(frac.padEnd(2, '0'));
  return Number.isSafeInteger(minor) ? minor : undefined;
}

/** 125050 -> "1250.50" (no grouping, suitable for an input value). */
export function minorToKwachaString(minor: number | null | undefined): string {
  if (minor === null || minor === undefined || !Number.isFinite(minor)) return '';
  const sign = minor < 0 ? '-' : '';
  const abs = Math.abs(Math.trunc(minor));
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`;
}

export interface MoneyOptions {
  /** Smallest allowed amount in ngwee (e.g. the payout minimum). Default 1. */
  minMinor?: number;
  /** Largest allowed amount in ngwee. */
  maxMinor?: number;
  /** Allow zero (free tiers). Default false. */
  allowZero?: boolean;
  label?: string;
  /** Message when below minMinor; receives the minimum formatted as "K 50.00". */
  belowMinMessage?: (min: string) => string;
}

const fmt = (minor: number) => `K ${minorToKwachaString(minor)}`;

/**
 * Major-unit money text/number (what a person types, "125.50") -> integer
 * ngwee. Positive, at most 2 decimals. Use for plain inputs; `MoneyRHF` already
 * holds minor units, validate it with `moneyMinor`.
 */
export function money(options: MoneyOptions = {}) {
  const { minMinor = options.allowZero ? 0 : 1, maxMinor, allowZero, belowMinMessage } = options;
  return z
    .union([z.string(), z.number()], { error: 'Enter an amount' })
    .transform((value, ctx) => {
      if (typeof value === 'string' && value.trim() === '') {
        ctx.issues.push({ code: 'custom', message: 'Enter an amount', input: value });
        return z.NEVER;
      }
      const minor = parseKwachaToMinor(value);
      if (minor === undefined) {
        ctx.issues.push({ code: 'custom', message: 'Enter an amount with at most 2 decimal places', input: value });
        return z.NEVER;
      }
      if (minor === 0 && !allowZero) {
        ctx.issues.push({ code: 'custom', message: 'Amount must be more than zero', input: value });
        return z.NEVER;
      }
      if (minor < minMinor) {
        ctx.issues.push({
          code: 'custom',
          message: belowMinMessage ? belowMinMessage(fmt(minMinor)) : `Minimum amount is ${fmt(minMinor)}`,
          input: value,
        });
        return z.NEVER;
      }
      if (maxMinor !== undefined && minor > maxMinor) {
        ctx.issues.push({ code: 'custom', message: `Maximum amount is ${fmt(maxMinor)}`, input: value });
        return z.NEVER;
      }
      return minor;
    });
}

/** Integer ngwee, the value `MoneyRHF` stores. Same bounds as `money`. */
export function moneyMinor(options: MoneyOptions = {}) {
  const { minMinor = options.allowZero ? 0 : 1, maxMinor, allowZero, belowMinMessage } = options;
  let schema = z
    .number({ error: 'Enter an amount' })
    .int('Enter an amount with at most 2 decimal places')
    .refine((n) => allowZero || n > 0, { error: 'Amount must be more than zero' })
    .refine((n) => n >= minMinor, {
      error: belowMinMessage ? belowMinMessage(fmt(minMinor)) : `Minimum amount is ${fmt(minMinor)}`,
    });
  if (maxMinor !== undefined) {
    schema = schema.refine((n) => n <= maxMinor, { error: `Maximum amount is ${fmt(maxMinor)}` });
  }
  return schema;
}
