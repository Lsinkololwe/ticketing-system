'use client';

import type { CSSProperties, ReactNode } from 'react';

/**
 * Button — MyTicketZM Design System core primitive.
 *
 * Contract (spec §7): children, variant, color, size, disabled, icon, style,
 * onClick. No other props — the adherence lint enforces this signature.
 * (The `data-testid` on the rendered element is a fixed internal attribute,
 * not a prop, so the public signature stays exactly as specified.)
 *
 * - Hover moves one step down the scale (--accent-9 → --accent-10). Never a
 *   scale/shrink press effect.
 * - Focus is the offset double ring: 2px background + 4px accent-8.
 * - `color="green"` is the COMMERCE colour in this app (jade — prices,
 *   "Book now", "Pay"). It is money-semantic, not a generic success colour.
 */
export interface ButtonProps {
  children?: ReactNode;
  variant?: 'solid' | 'soft' | 'outline' | 'ghost';
  color?: 'accent' | 'red' | 'green';
  size?: '1' | '2' | '3' | '4';
  disabled?: boolean;
  icon?: ReactNode;
  style?: CSSProperties;
  onClick?: () => void;
}

/** Role scales. `green` maps to jade so money never borrows the brand hue. */
const COLOR_SCALE = {
  accent: {
    solid: 'var(--accent-9)',
    solidHover: 'var(--accent-10)',
    contrast: 'var(--accent-contrast)',
    text: 'var(--accent-11)',
    surface: 'var(--accent-a3)',
    surfaceHover: 'var(--accent-a4)',
    border: 'var(--accent-a7)',
    ring: 'var(--accent-8)',
  },
  red: {
    solid: 'var(--status-danger-9)',
    solidHover: 'var(--red-10)',
    contrast: 'var(--gray-1)',
    text: 'var(--status-danger-11)',
    surface: 'var(--status-danger-a3)',
    surfaceHover: 'var(--red-a4)',
    border: 'var(--red-a7)',
    ring: 'var(--red-8)',
  },
  green: {
    solid: 'var(--color-money)',
    solidHover: 'var(--color-money-hover)',
    contrast: 'var(--gray-1)',
    text: 'var(--color-money-text)',
    surface: 'var(--color-money-surface)',
    surfaceHover: 'var(--jade-a4)',
    border: 'var(--jade-a7)',
    ring: 'var(--jade-8)',
  },
} as const;

const SIZE_SPEC = {
  '1': { height: 24, padding: '0 8px', fontSize: 'var(--text-1-size)', radius: 'var(--radius-3)', gap: 4 },
  '2': { height: 32, padding: '0 12px', fontSize: 'var(--text-2-size)', radius: 'var(--radius-3)', gap: 6 },
  '3': { height: 40, padding: '0 16px', fontSize: 'var(--text-3-size)', radius: 'var(--radius-4)', gap: 8 },
  '4': { height: 48, padding: '0 24px', fontSize: 'var(--text-4-size)', radius: 'var(--radius-4)', gap: 8 },
} as const;

export function Button({
  children,
  variant = 'solid',
  color = 'accent',
  size = '2',
  disabled = false,
  icon,
  style,
  onClick,
}: ButtonProps) {
  const c = COLOR_SCALE[color];
  const s = SIZE_SPEC[size];

  const variantStyle: CSSProperties =
    variant === 'solid'
      ? { background: c.solid, color: c.contrast, border: '1px solid transparent' }
      : variant === 'soft'
        ? { background: c.surface, color: c.text, border: '1px solid transparent' }
        : variant === 'outline'
          ? { background: 'transparent', color: c.text, border: `1px solid ${c.border}` }
          : { background: 'transparent', color: c.text, border: '1px solid transparent' };

  return (
    <button
      type="button"
      data-testid="ds-button"
      disabled={disabled}
      onClick={onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: s.gap,
        height: s.height,
        padding: s.padding,
        fontFamily: 'var(--font-sans)',
        fontSize: s.fontSize,
        fontWeight: 'var(--weight-medium)',
        lineHeight: 1,
        borderRadius: s.radius,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        whiteSpace: 'nowrap',
        transition:
          'background var(--transition-fast) var(--ease-standard), border-color var(--transition-fast) var(--ease-standard), color var(--transition-fast) var(--ease-standard)',
        ...variantStyle,
        ...style,
      }}
      onMouseEnter={(e) => {
        if (disabled) return;
        e.currentTarget.style.background = variant === 'solid' ? c.solidHover : c.surfaceHover;
      }}
      onMouseLeave={(e) => {
        if (disabled) return;
        e.currentTarget.style.background = (variantStyle.background as string) ?? 'transparent';
      }}
      onFocus={(e) => {
        e.currentTarget.style.boxShadow = `0 0 0 2px var(--color-background), 0 0 0 4px ${c.ring}`;
        e.currentTarget.style.outline = 'none';
      }}
      onBlur={(e) => {
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      {icon}
      {children}
    </button>
  );
}

export default Button;
