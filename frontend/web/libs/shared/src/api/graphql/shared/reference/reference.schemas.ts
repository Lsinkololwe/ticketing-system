import { z } from 'zod';

/**
 * A code that must be one the platform lists.
 *
 * Once the list has loaded the code is checked against it, so a stale or tampered value cannot be
 * submitted. While the list is unavailable (loading, or the read failed) the client cannot judge, so
 * the value passes and the server, which validates against the same rows, has the last word.
 */
export function referenceCode(
  codes: readonly string[],
  message = 'Choose one of the listed options',
  required = 'Choose an option',
) {
  const allowed = new Set(codes);
  return z
    .string({ error: required })
    .min(1, required)
    .refine((value) => allowed.size === 0 || allowed.has(value), { message });
}

/** Same, but an empty value is allowed (an optional field). */
export function optionalReferenceCode(codes: readonly string[], message = 'Choose one of the listed options') {
  const allowed = new Set(codes);
  return z
    .string()
    .refine((value) => value === '' || allowed.size === 0 || allowed.has(value), { message });
}
