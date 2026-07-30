'use client';

/**
 * Badge — MyTicketZM design system core primitive.
 *
 * Contract (spec §7): `children, color, variant, size, style`
 *   color:   gray | accent | green | amber | red | blue
 *   variant: soft | solid | outline
 *
 * Note on color discipline: this badge intentionally has NO jade option.
 * Jade is the money role — it belongs on an amount, not on a status chip.
 * A "Paid" chip is `green` (generic success); the amount next to it is what
 * carries `--color-money`. Keeping them apart is why a paid chip can never be
 * confused for a brand-teal chip.
 */

import type { CSSProperties, ReactNode } from 'react';

export type BadgeColor = 'gray' | 'accent' | 'green' | 'amber' | 'red' | 'blue';
export type BadgeVariant = 'soft' | 'solid' | 'outline';
export type BadgeSize = 'sm' | 'md';

export interface BadgeProps {
  children?: ReactNode;
  color?: BadgeColor;
  variant?: BadgeVariant;
  size?: BadgeSize;
  style?: CSSProperties;
}

const RAMP: Record<BadgeColor, { solid: string; text: string; surface: string; border: string; contrast: string }> = {
  gray: {
    solid: 'var(--gray-9)',
    text: 'var(--gray-11)',
    surface: 'var(--gray-a3)',
    border: 'var(--gray-a6)',
    contrast: 'var(--gray-1)',
  },
  accent: {
    solid: 'var(--accent-9)',
    text: 'var(--accent-11)',
    surface: 'var(--accent-a3)',
    border: 'var(--accent-a6)',
    contrast: 'var(--accent-contrast)',
  },
  green: {
    solid: 'var(--status-success-9)',
    text: 'var(--status-success-11)',
    surface: 'var(--status-success-a3)',
    border: 'var(--green-a6)',
    contrast: 'var(--gray-1)',
  },
  amber: {
    solid: 'var(--status-warning-9)',
    text: 'var(--status-warning-11)',
    surface: 'var(--status-warning-a3)',
    border: 'var(--amber-a6)',
    contrast: 'var(--gray-12)',
  },
  red: {
    solid: 'var(--status-danger-9)',
    text: 'var(--status-danger-11)',
    surface: 'var(--status-danger-a3)',
    border: 'var(--red-a6)',
    contrast: 'var(--gray-1)',
  },
  blue: {
    solid: 'var(--status-info-9)',
    text: 'var(--status-info-11)',
    surface: 'var(--status-info-a3)',
    border: 'var(--blue-a6)',
    contrast: 'var(--gray-1)',
  },
};

const SIZES: Record<BadgeSize, CSSProperties> = {
  sm: { padding: '1px 6px', fontSize: 'var(--text-1-size)', gap: 4 },
  md: { padding: '3px 9px', fontSize: 'var(--text-2-size)', gap: 6 },
};

export function Badge({
  children,
  color = 'gray',
  variant = 'soft',
  size = 'sm',
  style,
}: BadgeProps) {
  const ramp = RAMP[color];

  const variantStyle: CSSProperties =
    variant === 'solid'
      ? { background: ramp.solid, color: ramp.contrast, border: '1px solid transparent' }
      : variant === 'outline'
        ? { background: 'transparent', color: ramp.text, border: `1px solid ${ramp.border}` }
        : { background: ramp.surface, color: ramp.text, border: '1px solid transparent' };

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        borderRadius: 'var(--radius-2)',
        fontFamily: 'var(--font-sans)',
        fontWeight: 'var(--weight-medium)',
        lineHeight: 1.4,
        whiteSpace: 'nowrap',
        ...SIZES[size],
        ...variantStyle,
        ...style,
      }}
    >
      {children}
    </span>
  );
}

export default Badge;
