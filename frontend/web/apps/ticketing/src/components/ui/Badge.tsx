'use client';

import type { CSSProperties, ReactNode } from 'react';

/**
 * Badge — MyTicketZM Design System core primitive.
 *
 * Contract (spec §7): children, color, variant, size, style.
 * `color: gray | accent | green | amber | red | blue`,
 * `variant: soft | solid | outline`.
 *
 * Colour discipline: `green` here is the STATUS green (--status-success-*),
 * NOT jade. Money keeps its own hue via <Money/> so a "paid" amount is never
 * confused with a generic success chip. Promo/featured chips use the copper
 * highlight through `<Badge color="amber">`'s sibling utility
 * `--color-highlight-*`, applied by the caller via `style`.
 */
export interface BadgeProps {
  children?: ReactNode;
  color?: 'gray' | 'accent' | 'green' | 'amber' | 'red' | 'blue';
  variant?: 'soft' | 'solid' | 'outline';
  size?: '1' | '2' | '3';
  style?: CSSProperties;
}

const COLOR_SCALE = {
  gray: { solid: 'var(--gray-9)', text: 'var(--gray-11)', surface: 'var(--gray-a3)', border: 'var(--gray-a7)' },
  accent: { solid: 'var(--accent-9)', text: 'var(--accent-11)', surface: 'var(--accent-a3)', border: 'var(--accent-a7)' },
  green: { solid: 'var(--status-success-9)', text: 'var(--status-success-11)', surface: 'var(--status-success-a3)', border: 'var(--green-a7)' },
  amber: { solid: 'var(--status-warning-9)', text: 'var(--status-warning-11)', surface: 'var(--status-warning-a3)', border: 'var(--amber-a7)' },
  red: { solid: 'var(--status-danger-9)', text: 'var(--status-danger-11)', surface: 'var(--status-danger-a3)', border: 'var(--red-a7)' },
  blue: { solid: 'var(--status-info-9)', text: 'var(--status-info-11)', surface: 'var(--status-info-a3)', border: 'var(--blue-a7)' },
} as const;

const SIZE_SPEC = {
  '1': { height: 18, padding: '0 6px', fontSize: 'var(--text-1-size)' },
  '2': { height: 22, padding: '0 8px', fontSize: 'var(--text-1-size)' },
  '3': { height: 26, padding: '0 10px', fontSize: 'var(--text-2-size)' },
} as const;

export function Badge({
  children,
  color = 'gray',
  variant = 'soft',
  size = '2',
  style,
}: BadgeProps) {
  const c = COLOR_SCALE[color];
  const s = SIZE_SPEC[size];

  const variantStyle: CSSProperties =
    variant === 'solid'
      ? { background: c.solid, color: 'var(--gray-1)', border: '1px solid transparent' }
      : variant === 'outline'
        ? { background: 'transparent', color: c.text, border: `1px solid ${c.border}` }
        : { background: c.surface, color: c.text, border: '1px solid transparent' };

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        height: s.height,
        padding: s.padding,
        borderRadius: 'var(--radius-full, 9999px)',
        fontFamily: 'var(--font-sans)',
        fontSize: s.fontSize,
        fontWeight: 'var(--weight-medium)',
        lineHeight: 1,
        whiteSpace: 'nowrap',
        ...variantStyle,
        ...style,
      }}
    >
      {children}
    </span>
  );
}

export default Badge;
