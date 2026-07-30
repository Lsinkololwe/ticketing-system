'use client';

/**
 * Amount — currency rendering. MyTicketZM Design System §8 / §10.
 *
 * NOT one of the §7 contract components; a local primitive whose only job is to
 * guarantee two rules are never broken by hand:
 *
 *   1. Currency always reads `K 125,430` — Kwacha symbol, single space, grouped
 *      figures. Never "ZMW", never "$".
 *   2. It always lands in `.ds-amount`, i.e. Fira Code with tabular figures, so
 *      columns of money line up and a re-render cannot make a figure jitter.
 *
 * `tone="money"` opts into the jade money role. Jade is SEMANTIC-ONLY: use it
 * for revenue, payouts, escrow balances and "paid" — never as decoration, and
 * never for a count. The default tone inherits the surrounding text colour,
 * which is what most table cells want.
 */

import type { CSSProperties } from 'react';
import { formatKwacha, formatKwachaCompact } from '@/lib/format';

export interface AmountProps {
  /** Value in Kwacha. Null/undefined renders an em dash. */
  value: number | string | null | undefined;
  /** Abbreviate large figures: `K 2.4M`. Use in stat tiles, not in tables. */
  compact?: boolean;
  /** Fraction digits for the non-compact form. */
  decimals?: number;
  /** `money` applies the jade money role. Only for actual money semantics. */
  tone?: 'inherit' | 'money';
  style?: CSSProperties;
}

export function Amount({
  value,
  compact = false,
  decimals = 0,
  tone = 'inherit',
  style,
}: AmountProps) {
  const text = compact ? formatKwachaCompact(value) : formatKwacha(value, decimals);

  return (
    <span
      className="ds-amount"
      style={{
        color: tone === 'money' ? 'var(--color-money-text)' : undefined,
        ...style,
      }}
    >
      {text}
    </span>
  );
}

export default Amount;
