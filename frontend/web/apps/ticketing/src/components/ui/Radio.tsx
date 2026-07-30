'use client';

import { useId, type CSSProperties, type ChangeEvent } from 'react';

/**
 * Radio — MyTicketZM Design System core primitive.
 *
 * Contract (spec §7): checked, onChange, label, disabled, name, value, size,
 * style.
 *
 * Same focus treatment as Checkbox: the offset double ring
 * (2px background + 4px accent-8).
 */
export interface RadioProps {
  checked?: boolean;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
  label?: string;
  disabled?: boolean;
  name?: string;
  value?: string;
  size?: '1' | '2' | '3';
  style?: CSSProperties;
}

const DOT_SIZE = { '1': 14, '2': 16, '3': 20 } as const;
const TEXT_SIZE = {
  '1': 'var(--text-1-size)',
  '2': 'var(--text-2-size)',
  '3': 'var(--text-3-size)',
} as const;

export function Radio({
  checked,
  onChange,
  label,
  disabled = false,
  name,
  value,
  size = '2',
  style,
}: RadioProps) {
  const id = useId();
  const dot = DOT_SIZE[size];

  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--space-2)',
        opacity: disabled ? 0.5 : 1,
        ...style,
      }}
    >
      <input
        id={id}
        data-testid="ds-radio"
        type="radio"
        name={name}
        value={value}
        checked={checked}
        onChange={onChange}
        disabled={disabled}
        style={{
          width: dot,
          height: dot,
          margin: 0,
          flexShrink: 0,
          accentColor: 'var(--accent-9)',
          cursor: disabled ? 'not-allowed' : 'pointer',
        }}
        onFocus={(e) => {
          e.currentTarget.style.outline = 'none';
          e.currentTarget.style.boxShadow =
            '0 0 0 2px var(--color-background), 0 0 0 4px var(--accent-8)';
        }}
        onBlur={(e) => {
          e.currentTarget.style.boxShadow = 'none';
        }}
      />
      {label && (
        <label
          htmlFor={id}
          style={{
            fontSize: TEXT_SIZE[size],
            color: 'var(--gray-12)',
            cursor: disabled ? 'not-allowed' : 'pointer',
          }}
        >
          {label}
        </label>
      )}
    </div>
  );
}

export default Radio;
