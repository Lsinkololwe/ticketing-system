'use client';

/**
 * Phone-number helpers for the mobile-money checkout. Which operators exist, and which number
 * ranges are theirs, is platform reference data: see `useMobileOperators`. Nothing here lists them.
 */
export { normalizeDigits } from './useMobileOperators';
import { normalizeDigits } from './useMobileOperators';

/** Format a local number to display E.164, e.g. "+260 96 123 4567". */
export function formatPhoneDisplay(phone: string): string {
  const digits = normalizeDigits(phone);
  if (digits.length !== 10) return phone;
  const national = digits.slice(1); // drop leading 0
  return `+260 ${national.slice(0, 2)} ${national.slice(2, 5)} ${national.slice(5)}`;
}

/** Convert a local number to E.164 for the backend, e.g. "+260961234567". */
export function toE164(phone: string): string {
  const digits = normalizeDigits(phone);
  if (digits.startsWith('0')) return `+260${digits.slice(1)}`;
  if (digits.startsWith('260')) return `+${digits}`;
  return `+260${digits}`;
}
