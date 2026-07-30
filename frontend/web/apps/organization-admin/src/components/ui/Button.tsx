'use client';

/**
 * Button — MyTicketZM design system core primitive.
 *
 * Contract (spec §7): `children, variant, color, size, disabled, icon, style, onClick`
 *   variant: solid | soft | outline | ghost
 *   color:   accent | red | green
 *
 * Hover shifts one step down the accent scale (--accent-9 → --accent-10) rather
 * than darkening a fill. Focus uses the offset double ring reserved for
 * buttons/checkboxes/switches. There is no press scale/shrink anywhere in this
 * system.
 *
 * `color` is deliberately limited to the three semantic actions. Money (jade)
 * is never a button color — jade means "this figure is currency", not "this
 * button is safe to press".
 */

import type { CSSProperties, MouseEventHandler, ReactNode } from 'react';

export type ButtonVariant = 'solid' | 'soft' | 'outline' | 'ghost';
export type ButtonColor = 'accent' | 'red' | 'green';
/**
 * Numeric 1-4, matching the design system's reference Button and Radix Themes'
 * own <Button size>. Deliberately NOT sm/md/lg — a second scale for the same
 * concept is how the three apps drift apart.
 */
export type ButtonSize = '1' | '2' | '3' | '4';

export interface ButtonProps {
  children?: ReactNode;
  variant?: ButtonVariant;
  color?: ButtonColor;
  size?: ButtonSize;
  disabled?: boolean;
  icon?: ReactNode;
  style?: CSSProperties;
  onClick?: MouseEventHandler<HTMLButtonElement>;
}

const SIZES: Record<ButtonSize, CSSProperties> = {
  '1': { height: 24, padding: '0 12px', fontSize: 'var(--text-1-size)', gap: 4, borderRadius: 'var(--radius-2)' },
  '2': { height: 32, padding: '0 16px', fontSize: 'var(--text-2-size)', gap: 6, borderRadius: 'var(--radius-2)' },
  '3': { height: 40, padding: '0 20px', fontSize: 'var(--text-3-size)', gap: 8, borderRadius: 'var(--radius-3)' },
  '4': { height: 48, padding: '0 28px', fontSize: 'var(--text-4-size)', gap: 8, borderRadius: 'var(--radius-3)' },
};

/** Each color maps to a scale; nothing is inlined. */
const RAMP: Record<
  ButtonColor,
  { solid: string; text: string; surface: string; border: string; contrast: string }
> = {
  accent: {
    solid: 'var(--accent-9)',
    text: 'var(--accent-11)',
    surface: 'var(--accent-a3)',
    border: 'var(--accent-7)',
    contrast: 'var(--accent-contrast)',
  },
  red: {
    solid: 'var(--status-danger-9)',
    text: 'var(--status-danger-11)',
    surface: 'var(--status-danger-a3)',
    border: 'var(--red-7)',
    contrast: 'var(--gray-1)',
  },
  green: {
    solid: 'var(--status-success-9)',
    text: 'var(--status-success-11)',
    surface: 'var(--status-success-a3)',
    border: 'var(--green-7)',
    contrast: 'var(--gray-1)',
  },
};

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
  const ramp = RAMP[color];

  const variantStyle: CSSProperties =
    variant === 'solid'
      ? { background: ramp.solid, color: ramp.contrast, border: '1px solid transparent' }
      : variant === 'soft'
        ? { background: ramp.surface, color: ramp.text, border: '1px solid transparent' }
        : variant === 'outline'
          ? { background: 'transparent', color: ramp.text, border: `1px solid ${ramp.border}` }
          : { background: 'transparent', color: ramp.text, border: '1px solid transparent' };

  return (
    <button
      type="button"
      data-testid="ds-button"
      className="ds-button"
      data-variant={variant}
      data-color={color}
      disabled={disabled}
      onClick={onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontFamily: 'var(--font-sans)',
        fontWeight: 'var(--weight-medium)',
        borderRadius: 'var(--radius-3)',
        whiteSpace: 'nowrap',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition:
          'background-color var(--transition-fast) var(--ease-standard), border-color var(--transition-fast) var(--ease-standard), color var(--transition-fast) var(--ease-standard)',
        ...SIZES[size],
        ...variantStyle,
        ...style,
      }}
    >
      {icon}
      {children}

      <style jsx global>{`
        .ds-button:focus-visible {
          outline: none;
          box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--accent-8);
        }
        .ds-button[data-color='red']:focus-visible {
          box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--red-8);
        }
        .ds-button[data-color='green']:focus-visible {
          box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--green-8);
        }

        /* Solid: one step down the scale. */
        .ds-button[data-variant='solid'][data-color='accent']:hover:not(:disabled) {
          background: var(--accent-10);
        }
        .ds-button[data-variant='solid'][data-color='red']:hover:not(:disabled) {
          background: var(--red-10);
        }
        .ds-button[data-variant='solid'][data-color='green']:hover:not(:disabled) {
          background: var(--green-10);
        }

        /* Soft / outline / ghost: shift to a tinted step, never darken a fill. */
        .ds-button[data-variant='soft'][data-color='accent']:hover:not(:disabled),
        .ds-button[data-variant='outline'][data-color='accent']:hover:not(:disabled),
        .ds-button[data-variant='ghost'][data-color='accent']:hover:not(:disabled) {
          background: var(--accent-a4);
        }
        .ds-button[data-variant='soft'][data-color='red']:hover:not(:disabled),
        .ds-button[data-variant='outline'][data-color='red']:hover:not(:disabled),
        .ds-button[data-variant='ghost'][data-color='red']:hover:not(:disabled) {
          background: var(--red-a4);
        }
        .ds-button[data-variant='soft'][data-color='green']:hover:not(:disabled),
        .ds-button[data-variant='outline'][data-color='green']:hover:not(:disabled),
        .ds-button[data-variant='ghost'][data-color='green']:hover:not(:disabled) {
          background: var(--green-a4);
        }
      `}</style>
    </button>
  );
}

export default Button;
