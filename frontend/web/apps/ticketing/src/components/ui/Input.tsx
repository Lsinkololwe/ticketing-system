'use client';

import { useState, type CSSProperties, type ChangeEvent, type ReactNode } from 'react';

/**
 * Input — MyTicketZM Design System core primitive.
 *
 * Contract (spec §7): placeholder, value, onChange, type, size, variant, icon,
 * error, style. `variant: outline | filled`.
 *
 * Form spec (§8): hairline --gray-a5 border, --gray-a3 hover tint,
 * --radius-3 / --radius-4 corners, a SINGLE 2px accent-alpha focus ring on
 * text fields (buttons get the offset double ring, text fields do not), and
 * errors via --status-danger-*.
 */
export interface InputProps {
  placeholder?: string;
  value?: string;
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;
  type?: 'text' | 'email' | 'password' | 'tel' | 'number' | 'search';
  size?: '1' | '2' | '3';
  variant?: 'outline' | 'filled';
  icon?: ReactNode;
  error?: string;
  style?: CSSProperties;
}

const SIZE_SPEC = {
  '1': { height: 28, fontSize: 'var(--text-1-size)', padding: 8, radius: 'var(--radius-3)' },
  '2': { height: 36, fontSize: 'var(--text-2-size)', padding: 10, radius: 'var(--radius-3)' },
  '3': { height: 44, fontSize: 'var(--text-3-size)', padding: 12, radius: 'var(--radius-4)' },
} as const;

export function Input({
  placeholder,
  value,
  onChange,
  type = 'text',
  size = '2',
  variant = 'outline',
  icon,
  error,
  style,
}: InputProps) {
  const [focused, setFocused] = useState(false);
  const [hovered, setHovered] = useState(false);
  const s = SIZE_SPEC[size];
  const invalid = Boolean(error);

  const borderColor = invalid
    ? 'var(--status-danger-9)'
    : focused
      ? 'var(--accent-8)'
      : 'var(--gray-a5)';

  const background = invalid
    ? 'var(--status-danger-a3)'
    : variant === 'filled'
      ? hovered && !focused
        ? 'var(--gray-a4)'
        : 'var(--gray-a3)'
      : hovered && !focused
        ? 'var(--gray-a3)'
        : 'var(--color-surface)';

  return (
    <div style={{ width: '100%', ...style }}>
      <div
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          height: s.height,
          borderRadius: s.radius,
          background,
          border: `1px solid ${borderColor}`,
          /* Single 2px accent-alpha ring — text fields never take the offset
             double ring reserved for buttons/checkboxes/switches. */
          boxShadow: focused
            ? `0 0 0 2px ${invalid ? 'var(--status-danger-a3)' : 'var(--accent-a7)'}`
            : 'none',
          transition:
            'border-color var(--transition-fast) var(--ease-standard), box-shadow var(--transition-fast) var(--ease-standard), background var(--transition-fast) var(--ease-standard)',
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        {icon && (
          <span
            aria-hidden="true"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              paddingLeft: s.padding,
              color: 'var(--gray-9)',
              flexShrink: 0,
            }}
          >
            {icon}
          </span>
        )}
        <input
          data-testid="ds-input"
          type={type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          aria-invalid={invalid || undefined}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          style={{
            flex: 1,
            width: '100%',
            height: '100%',
            minWidth: 0,
            padding: `0 ${s.padding}px`,
            paddingLeft: icon ? 6 : s.padding,
            border: 'none',
            outline: 'none',
            background: 'transparent',
            fontFamily: type === 'tel' || type === 'number' ? 'var(--font-mono)' : 'var(--font-sans)',
            fontSize: s.fontSize,
            color: 'var(--gray-12)',
          }}
        />
      </div>
      {error && (
        <span
          role="alert"
          style={{
            display: 'block',
            marginTop: 6,
            fontSize: 'var(--text-1-size)',
            color: 'var(--status-danger-11)',
          }}
        >
          {error}
        </span>
      )}
    </div>
  );
}

export default Input;
