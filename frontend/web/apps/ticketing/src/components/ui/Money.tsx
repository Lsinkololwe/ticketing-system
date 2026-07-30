'use client';

import { Text } from '@radix-ui/themes';
import type { ComponentProps } from 'react';
import { formatKwacha } from '@/lib/currency';

type RadixTextSize = ComponentProps<typeof Text>['size'];
type RadixTextWeight = ComponentProps<typeof Text>['weight'];

/**
 * Money — Kwacha amount rendered the MyTicketZM way.
 *
 * - Always `K 1,500` (never `$` / `ZMW`) via {@link formatKwacha}.
 * - Fira Code + tabular figures (`.amount`) so amounts never jitter.
 * - `tone="money"` uses the jade commerce color (prices, totals, "paid").
 *
 * Built on the Radix `Text` primitive, so `size`/`weight` are Radix scale
 * values and it composes anywhere Text does.
 */
export interface MoneyProps {
  amount: number;
  /** Show two decimal places (fees, per-unit). Default: whole Kwacha. */
  decimals?: boolean;
  tone?: 'money' | 'default' | 'muted' | 'inverse';
  size?: RadixTextSize;
  weight?: RadixTextWeight;
  className?: string;
}

const TONE_COLOR = {
  money: 'jade',
  default: undefined,
  muted: 'gray',
  inverse: undefined,
} as const;

export function Money({
  amount,
  decimals = false,
  tone = 'default',
  size = '3',
  weight = 'bold',
  className,
}: MoneyProps) {
  return (
    <Text
      size={size}
      weight={weight}
      color={TONE_COLOR[tone]}
      className={['ds-amount', className].filter(Boolean).join(' ')}
      style={tone === 'inverse' ? { color: 'inherit' } : undefined}
    >
      {formatKwacha(amount, { decimals })}
    </Text>
  );
}

export default Money;
