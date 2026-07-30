'use client';

/**
 * Textarea — MyTicketZM Design System §7 / §8.
 *
 * Contract props (exact, no extras):
 *   placeholder, value, onChange, rows, variant, error, disabled, style
 *
 * variant: outline | filled
 *
 * Shares the form language of the Input primitive: --radius-3 corners, a
 * --gray-a5 hairline border, a single 2px accent-alpha focus ring, and errors
 * expressed through --status-danger-*.
 */

import type { CSSProperties } from 'react';
import { Text, TextArea } from '@radix-ui/themes';

export interface TextareaProps {
  placeholder?: string;
  value?: string;
  onChange?: (value: string) => void;
  rows?: number;
  variant?: 'outline' | 'filled';
  /** Error message. Presence also switches the control to the danger tone. */
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
    <>
      <TextArea
        data-testid="ds-textarea"
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange?.(event.currentTarget.value)}
        rows={rows}
        variant={variant === 'filled' ? 'soft' : 'surface'}
        disabled={disabled}
        color={hasError ? 'red' : undefined}
        aria-invalid={hasError || undefined}
        style={{
          borderRadius: 'var(--radius-3)',
          ...(hasError
            ? { boxShadow: 'inset 0 0 0 1px var(--status-danger-9)' }
            : null),
          ...style,
        }}
      />

      {hasError ? (
        <Text
          size="1"
          role="alert"
          style={{
            display: 'block',
            marginTop: 'var(--space-1)',
            color: 'var(--status-danger-11)',
          }}
        >
          {error}
        </Text>
      ) : null}
    </>
  );
}

export default Textarea;
