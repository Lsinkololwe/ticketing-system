'use client';

/**
 * Button — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   children, variant, color, size, disabled, icon, style, onClick
 *
 * variant: solid | soft | outline | ghost
 * color:   accent | red | green
 *
 * `accent` is the admin teal backbone. `red` is destructive. `green` is the
 * generic success status — it is NOT the money colour (money is jade, and money
 * is never a button).
 *
 * Hover walks one step down the scale (--accent-9 → --accent-10), handled by
 * Radix. Focus uses the offset double ring declared in global.css. There is no
 * scale/shrink press effect anywhere in this system.
 */

import type { CSSProperties, ReactNode } from 'react';
import { Button as RadixButton } from '@radix-ui/themes';

/**
 * The design-system contract surface — exactly the props the spec declares.
 * Nothing may be added here.
 */
interface ButtonContractProps {
  children: ReactNode;
  variant?: 'solid' | 'soft' | 'outline' | 'ghost';
  color?: 'accent' | 'red' | 'green';
  size?: '1' | '2' | '3' | '4';
  disabled?: boolean;
  /** Leading icon. Iconoir only, sized 14–24px, coloured via currentColor. */
  icon?: ReactNode;
  style?: CSSProperties;
  onClick?: () => void;
}

/**
 * A test hook, deliberately kept OUTSIDE the contract surface above. It carries
 * no design meaning and renders straight through to the DOM; the repo's
 * test-selector policy requires every button to be addressable by
 * `getByTestId`.
 */
interface TestHookProps {
  'data-testid'?: string;
}

export type ButtonProps = ButtonContractProps & TestHookProps;

export function Button({
  children,
  variant = 'solid',
  color = 'accent',
  size = '2',
  disabled = false,
  icon,
  style,
  onClick,
  'data-testid': testId,
}: ButtonProps) {
  return (
    <RadixButton
      data-testid={testId}
      // Radix reads the theme accent when `color` is omitted, which is exactly
      // what the DS `accent` role means.
      color={color === 'accent' ? undefined : color}
      variant={variant}
      size={size}
      disabled={disabled}
      onClick={onClick}
      style={style}
    >
      {icon}
      {children}
    </RadixButton>
  );
}

export default Button;
