import { parsePhoneNumberFromString, type CountryCode } from 'libphonenumber-js';
import { z } from 'zod';

export interface PhoneE164Options {
  /** Region used to read national numbers ("097...", "97 123 4567"). Default Zambia. */
  region?: CountryCode;
  requiredMessage?: string;
  invalidMessage?: string;
}

/**
 * Normalise anything a person types into E.164, or `undefined` when it is not a
 * valid number for the region. Accepts spaces, dashes, brackets, a leading
 * zero ("0971234567" -> "+260971234567") and international numbers written
 * with "+" or "00" (WhatsApp numbers outside Zambia).
 */
export function normalisePhone(raw: string, region: CountryCode = 'ZM'): string | undefined {
  const text = raw.trim().replace(/^00(?=\d)/, '+');
  if (!text) return undefined;
  try {
    const parsed = parsePhoneNumberFromString(text, region);
    return parsed?.isValid() ? parsed.number : undefined;
  } catch {
    return undefined;
  }
}

/** Required phone number. Output is canonical E.164 (`+260971234567`). */
export function phoneE164(options: PhoneE164Options = {}) {
  const { region = 'ZM', requiredMessage = 'Enter a phone number', invalidMessage = 'Enter a valid phone number' } = options;
  return z
    .string({ error: requiredMessage })
    .trim()
    .min(1, requiredMessage)
    .transform((value, ctx) => {
      const e164 = normalisePhone(value, region);
      if (!e164) {
        ctx.issues.push({ code: 'custom', message: invalidMessage, input: value });
        return z.NEVER;
      }
      return e164;
    });
}

/** Optional phone number: empty string / undefined become `undefined`. */
export function phoneE164Optional(options: PhoneE164Options = {}) {
  const inner = phoneE164(options);
  return z.preprocess((v) => (typeof v === 'string' && v.trim() === '' ? undefined : v), inner.optional());
}
