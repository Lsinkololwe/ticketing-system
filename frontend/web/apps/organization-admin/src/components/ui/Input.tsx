'use client';

/**
 * Input — MyTicketZM design system core primitive.
 *
 * Contract (spec §7): `placeholder, value, onChange, type, size, variant, icon, error, style`
 *   variant: outline | filled
 *
 * Spec §8 form rules honoured here:
 *   - --radius-3 corners
 *   - 1px --gray-a5 hairline border
 *   - --gray-a3 hover tint
 *   - a SINGLE 2px accent-alpha focus ring (the offset double ring belongs to
 *     buttons/checkboxes/switches, not text fields)
 *   - errors via --status-danger-*, never a raw red
 *
 * Labels are NOT part of this component — pair it with `.ds-label` so the micro
 * uppercase label treatment stays consistent across Radix and DS controls.
 */

import type { CSSProperties, ChangeEventHandler, ReactNode } from 'react';

export type InputVariant = 'outline' | 'filled';
export type InputSize = 'sm' | 'md' | 'lg';

export interface InputProps {
  placeholder?: string;
  value?: string;
  onChange?: ChangeEventHandler<HTMLInputElement>;
  type?: string;
  size?: InputSize;
  variant?: InputVariant;
  icon?: ReactNode;
  error?: string;
  style?: CSSProperties;
}

const SIZES: Record<InputSize, { height: number; padding: number; font: string }> = {
  sm: { height: 32, padding: 10, font: 'var(--text-1-size)' },
  md: { height: 40, padding: 12, font: 'var(--text-2-size)' },
  lg: { height: 48, padding: 14, font: 'var(--text-3-size)' },
};

export function Input({
  placeholder,
  value,
  onChange,
  type = 'text',
  size = 'md',
  variant = 'outline',
  icon,
  error,
  style,
}: InputProps) {
  const dims = SIZES[size];
  const hasError = Boolean(error);

  return (
    <div style={{ width: '100%' }}>
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        {icon && (
          <span
            aria-hidden="true"
            style={{
              position: 'absolute',
              left: dims.padding,
              display: 'flex',
              alignItems: 'center',
              color: 'var(--gray-9)',
              pointerEvents: 'none',
            }}
          >
            {icon}
          </span>
        )}
        <input
          data-testid="ds-input"
          className="ds-input"
          type={type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          aria-invalid={hasError || undefined}
          data-variant={variant}
          style={{
            width: '100%',
            height: dims.height,
            paddingLeft: icon ? dims.padding + 26 : dims.padding,
            paddingRight: dims.padding,
            fontFamily: 'var(--font-sans)',
            fontSize: dims.font,
            color: 'var(--gray-12)',
            background: variant === 'filled' ? 'var(--gray-a3)' : 'var(--color-surface)',
            border: `1px solid ${hasError ? 'var(--status-danger-9)' : 'var(--gray-a5)'}`,
            borderRadius: 'var(--radius-3)',
            transition:
              'border-color var(--transition-fast) var(--ease-standard), box-shadow var(--transition-fast) var(--ease-standard), background-color var(--transition-fast) var(--ease-standard)',
            ...style,
          }}
        />
      </div>

      {hasError && (
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

      <style jsx global>{`
        .ds-input::placeholder {
          color: var(--gray-9);
        }
        .ds-input:hover:not(:focus):not(:disabled) {
          background: var(--gray-a3);
        }
        .ds-input:focus {
          outline: none;
          border-color: var(--accent-8);
          box-shadow: 0 0 0 2px var(--accent-a5);
        }
        .ds-input[aria-invalid='true']:focus {
          border-color: var(--status-danger-9);
          box-shadow: 0 0 0 2px var(--status-danger-a3);
        }
      `}</style>
    </div>
  );
}

export default Input;
