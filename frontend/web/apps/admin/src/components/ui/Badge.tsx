'use client';

/**
 * Badge — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   children, color, variant, size, style
 *
 * color:   gray | accent | green | amber | red | blue
 * variant: soft | solid | outline
 *
 * `green` is the GENERIC success status. Money ("paid", settled payouts) is
 * jade and must never be rendered through this palette — a paid chip must never
 * be mistakable for a brand-teal chip. Use <MoneyBadge> semantics via the
 * `--color-money-*` tokens if you need one.
 *
 * Pair with `humanizeEnum()` from `@/lib/format` — raw SCREAMING_SNAKE never
 * reaches the screen.
 */

import type { CSSProperties, ReactNode } from 'react';
import { Badge as RadixBadge } from '@radix-ui/themes';

export interface BadgeProps {
  children: ReactNode;
  color?: 'gray' | 'accent' | 'green' | 'amber' | 'red' | 'blue';
  variant?: 'soft' | 'solid' | 'outline';
  size?: '1' | '2' | '3';
  style?: CSSProperties;
}

export function Badge({
  children,
  color = 'gray',
  variant = 'soft',
  size = '1',
  style,
}: BadgeProps) {
  return (
    <RadixBadge
      // Omitting `color` makes Radix fall back to the theme accent — the DS
      // `accent` role.
      color={color === 'accent' ? undefined : color}
      variant={variant}
      size={size}
      style={style}
    >
      {children}
    </RadixBadge>
  );
}

export default Badge;
