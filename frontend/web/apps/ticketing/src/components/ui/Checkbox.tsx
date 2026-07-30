'use client';

import { useId, type CSSProperties, type ChangeEvent } from 'react';

/**
 * Checkbox — MyTicketZM Design System core primitive.
 *
 * Contract (spec §7): checked, defaultChecked, onChange, label, disabled,
 * size, style.
 *
 * Checkboxes take the OFFSET DOUBLE focus ring (2px background + 4px
 * accent-8), same as buttons and switches — not the single text-field ring.
 * `accent-color` keeps the native control on the brand hue so we do not have
 * to re-implement the checked glyph.
 */
export interface CheckboxProps {
  checked?: boolean;
  defaultChecked?: boolean;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
  label?: string;
  disabled?: boolean;
  size?: '1' | '2' | '3';
  style?: CSSProperties;
}

const BOX_SIZE = { '1': 14, '2': 16, '3': 20 } as const;
const TEXT_SIZE = {
  '1': 'var(--text-1-size)',
  '2': 'var(--text-2-size)',
  '3': 'var(--text-3-size)',
} as const;

export function Checkbox({
  checked,
  defaultChecked,
  onChange,
  label,
  disabled = false,
  size = '2',
  style,
}: CheckboxProps) {
  const id = useId();
  const box = BOX_SIZE[size];

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
        data-testid="ds-checkbox"
        type="checkbox"
        checked={checked}
        defaultChecked={defaultChecked}
        onChange={onChange}
        disabled={disabled}
        style={{
          width: box,
          height: box,
          margin: 0,
          flexShrink: 0,
          accentColor: 'var(--accent-9)',
          borderRadius: 'var(--radius-2)',
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

export default Checkbox;
