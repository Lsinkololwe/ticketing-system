'use client';

/**
 * Radio — MyTicketZM design system core primitive.
 *
 * Contract (spec §7): `checked, onChange, label, disabled, name, value, size, style`
 *
 * Same interaction language as `Checkbox`: brand accent-color for the selected
 * state, offset double focus ring, 44px minimum target height via the label.
 */

import type { CSSProperties, ChangeEventHandler } from 'react';

export type RadioSize = 'sm' | 'md' | 'lg';

export interface RadioProps {
  checked?: boolean;
  onChange?: ChangeEventHandler<HTMLInputElement>;
  label?: string;
  disabled?: boolean;
  name?: string;
  value?: string;
  size?: RadioSize;
  style?: CSSProperties;
}

const BOX: Record<RadioSize, number> = { sm: 14, md: 16, lg: 20 };

export function Radio({
  checked,
  onChange,
  label,
  disabled = false,
  name,
  value,
  size = 'md',
  style,
}: RadioProps) {
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
        data-testid="ds-radio"
        className="ds-radio"
        type="radio"
        name={name}
        value={value}
        checked={checked}
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
        .ds-radio:focus-visible {
          outline: none;
          box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--accent-8);
          border-radius: 50%;
        }
      `}</style>
    </label>
  );
}

export default Radio;
