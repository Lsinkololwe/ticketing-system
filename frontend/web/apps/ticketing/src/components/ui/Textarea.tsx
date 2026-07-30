'use client';

import { useState, type CSSProperties, type ChangeEvent } from 'react';

/**
 * Textarea — MyTicketZM Design System core primitive.
 *
 * Contract (spec §7): placeholder, value, onChange, rows, variant, error,
 * disabled, style. `variant: outline | filled`.
 *
 * Same field language as Input: hairline --gray-a5 border, --gray-a3 hover
 * tint, --radius-4 corners, single 2px accent-alpha focus ring, errors via
 * --status-danger-*.
 */
export interface TextareaProps {
  placeholder?: string;
  value?: string;
  onChange?: (event: ChangeEvent<HTMLTextAreaElement>) => void;
  rows?: number;
  variant?: 'outline' | 'filled';
  error?: string;
  disabled?: boolean;
  style?: CSSProperties;
}

export function Textarea({
  placeholder,
  value,
  onChange,
  rows = 4,
  variant = 'outline',
  error,
  disabled = false,
  style,
}: TextareaProps) {
  const [focused, setFocused] = useState(false);
  const [hovered, setHovered] = useState(false);
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
      <textarea
        data-testid="ds-textarea"
        value={value}
        onChange={onChange}
        rows={rows}
        placeholder={placeholder}
        disabled={disabled}
        aria-invalid={invalid || undefined}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        style={{
          width: '100%',
          padding: '10px 12px',
          resize: 'vertical',
          background,
          border: `1px solid ${borderColor}`,
          borderRadius: 'var(--radius-4)',
          boxShadow: focused
            ? `0 0 0 2px ${invalid ? 'var(--status-danger-a3)' : 'var(--accent-a7)'}`
            : 'none',
          outline: 'none',
          fontFamily: 'var(--font-sans)',
          fontSize: 'var(--text-2-size)',
          lineHeight: 'var(--text-2-line)',
          color: 'var(--gray-12)',
          opacity: disabled ? 0.5 : 1,
          cursor: disabled ? 'not-allowed' : 'auto',
          transition:
            'border-color var(--transition-fast) var(--ease-standard), box-shadow var(--transition-fast) var(--ease-standard), background var(--transition-fast) var(--ease-standard)',
        }}
      />
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

export default Textarea;
