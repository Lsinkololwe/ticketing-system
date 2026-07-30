'use client';

/**
 * Textarea — MyTicketZM design system core primitive.
 *
 * Contract (spec §7): `placeholder, value, onChange, rows, variant, error, disabled, style`
 *   variant: outline | filled
 *
 * Same field language as `Input`: --radius-3 corners, --gray-a5 hairline,
 * --gray-a3 hover tint, single 2px accent-alpha focus ring, --status-danger-*
 * errors.
 */

import type { CSSProperties, ChangeEventHandler } from 'react';

export type TextareaVariant = 'outline' | 'filled';

export interface TextareaProps {
  placeholder?: string;
  value?: string;
  onChange?: ChangeEventHandler<HTMLTextAreaElement>;
  rows?: number;
  variant?: TextareaVariant;
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
  const hasError = Boolean(error);

  return (
    <div style={{ width: '100%' }}>
      <textarea
        data-testid="ds-textarea"
        className="ds-textarea"
        rows={rows}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        disabled={disabled}
        aria-invalid={hasError || undefined}
        style={{
          width: '100%',
          padding: '10px 12px',
          fontFamily: 'var(--font-sans)',
          fontSize: 'var(--text-2-size)',
          lineHeight: 'var(--text-2-line)',
          color: 'var(--gray-12)',
          background: variant === 'filled' ? 'var(--gray-a3)' : 'var(--color-surface)',
          border: `1px solid ${hasError ? 'var(--status-danger-9)' : 'var(--gray-a5)'}`,
          borderRadius: 'var(--radius-3)',
          resize: 'vertical',
          cursor: disabled ? 'not-allowed' : 'auto',
          opacity: disabled ? 0.6 : 1,
          transition:
            'border-color var(--transition-fast) var(--ease-standard), box-shadow var(--transition-fast) var(--ease-standard), background-color var(--transition-fast) var(--ease-standard)',
          ...style,
        }}
      />

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
        .ds-textarea::placeholder {
          color: var(--gray-9);
        }
        .ds-textarea:hover:not(:focus):not(:disabled) {
          background: var(--gray-a3);
        }
        .ds-textarea:focus {
          outline: none;
          border-color: var(--accent-8);
          box-shadow: 0 0 0 2px var(--accent-a5);
        }
        .ds-textarea[aria-invalid='true']:focus {
          border-color: var(--status-danger-9);
          box-shadow: 0 0 0 2px var(--status-danger-a3);
        }
      `}</style>
    </div>
  );
}

export default Textarea;
