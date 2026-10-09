import type { z } from 'zod';

type Issue = z.core.$ZodIssue;

/**
 * Flatten zod issues to `{ 'tiers.0.price': 'message' }`. First message per
 * path wins. Accepts a ZodError or its `issues` array.
 */
export function zodIssuesToFieldErrors(source: { issues: readonly Issue[] } | readonly Issue[]): Record<string, string> {
  const issues = Array.isArray(source) ? (source as readonly Issue[]) : (source as { issues: readonly Issue[] }).issues;
  const out: Record<string, string> = {};
  for (const issue of issues) {
    const key = issue.path.map(String).join('.');
    if (out[key] === undefined) out[key] = issue.message;
  }
  return out;
}
