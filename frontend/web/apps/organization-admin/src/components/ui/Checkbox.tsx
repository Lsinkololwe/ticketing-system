'use client';

/**
 * Checkbox — MyTicketZM design system core primitive.
 *
 * Contract (spec §7): `checked, defaultChecked, onChange, label, disabled, size, style`
 *
 * Uses the native input with `accent-color` so the checked state tracks the
 * brand accent in both appearances, and the offset double focus ring that
 * buttons/checkboxes/switches share. The 44x44px target rule is met by the
 * label's padding, not by inflating the box itself.
 */

import type { CSSProperties, ChangeEventHandler } from 'react';

export type CheckboxSize = 'sm' | 'md' | 'lg';

export interface CheckboxProps {
  checked?: boolean;
  defaultChecked?: boolean;
  onChange?: ChangeEventHandler<HTMLInputElement>;
  label?: string;
  disabled?: boolean;
  size?: CheckboxSize;
  style?: CSSProperties;
}

const BOX: Record<CheckboxSize, number> = { sm: 14, md: 16, lg: 20 };

export function Checkbox({
  checked,
  defaultChecked,
  onChange,
  label,
  disabled = false,
  size = 'md',
  style,
}: CheckboxProps) {
  const box = BOX[size];

  return (
    <label
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--space-2)',
        minHeight: 44,
        paddingRight: 4,
        fontFamily: 'var(--font-sans)',
        fontSize: 'var(--text-2-size)',
        color: disabled ? 'var(--gray-9)' : 'var(--gray-12)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        ...style,
      }}
    >
      <input
        data-testid="ds-checkbox"
        className="ds-checkbox"
        type="checkbox"
        checked={checked}
        defaultChecked={defaultChecked}
        onChange={onChange}
        disabled={disabled}
        style={{
          width: box,
          height: box,
          flexShrink: 0,
          accentColor: 'var(--accent-9)',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
      />
      {label}

      <style jsx global>{`
        .ds-checkbox:focus-visible {
          outline: none;
          box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--accent-8);
          border-radius: var(--radius-1);
        }
      `}</style>
    </label>
  );
}

export default Checkbox;
