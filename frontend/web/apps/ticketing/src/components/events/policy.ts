import { describeRule, policyFor, sortedRules, type BuyerPlatformRules } from '@pml.tickets/shared';

export interface PolicyView {
  /** The policy's name as the platform words it, or the raw code when the platform does not list it. */
  name: string;
  /** One-line summary, or null when the platform rules could not be read. */
  summary: string | null;
  /** One line per refund rule, longest lead time first. Empty for a no-refund policy. */
  lines: string[];
}

/** The event's refund policy in the platform's words (publicPlatformRules.refundPolicies), never a built-in default. */
export function policyView(rules: Pick<BuyerPlatformRules, 'refundPolicies'> | null, code: string | null): PolicyView {
  const p = policyFor(rules, code);
  if (p) return { name: p.label, summary: p.summary, lines: sortedRules(p).map(describeRule) };
  return { name: code ?? 'Not stated', summary: null, lines: [] };
}
