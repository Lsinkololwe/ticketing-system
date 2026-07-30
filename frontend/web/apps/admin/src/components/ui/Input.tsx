'use client';

/**
 * Input — MyTicketZM Design System §7 / §8.
 *
 * Contract props (exact, no extras):
 *   placeholder, value, onChange, type, size, variant, icon, error, style
 *
 * variant: outline | filled
 *
 * Form spec: 6–8px corners (--radius-3), 1px --gray-a5 hairline border,
 * --gray-a3 hover tint, a SINGLE 2px accent-alpha focus ring (text fields do
 * not get the offset double ring), errors through --status-danger-*.
 *
 * Labels are supplied by the caller using the micro uppercase treatment
 * (`.ds-label`) — the DS keeps labelling out of the control itself so a field
 * can be labelled, described or grouped without fighting the component.
 */

import type { CSSProperties, ReactNode } from 'react';
import { Text, TextField } from '@radix-ui/themes';

export interface InputProps {
  placeholder?: string;
  value?: string;
  onChange?: (value: string) => void;
  type?: 'text' | 'email' | 'password' | 'search' | 'tel' | 'url' | 'number';
  size?: '1' | '2' | '3';
  variant?: 'outline' | 'filled';
  /** Leading icon. Iconoir only, 14–24px, coloured via currentColor. */
  icon?: ReactNode;
  /** Error message. Presence also switches the control to the danger tone. */
  error?: string;
  style?: CSSProperties;
}

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
  const hasError = Boolean(error);

  return (
    <>
      <TextField.Root
        data-testid="ds-input"
        type={type}
        size={size}
        // Radix "surface" is the hairline-bordered field; "soft" is the tinted
        // fill. They map onto the DS outline/filled variants.
        variant={variant === 'filled' ? 'soft' : 'surface'}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange?.(event.currentTarget.value)}
        color={hasError ? 'red' : undefined}
        aria-invalid={hasError || undefined}
        style={{
          borderRadius: 'var(--radius-3)',
          ...(hasError
            ? { boxShadow: 'inset 0 0 0 1px var(--status-danger-9)' }
            : null),
          ...style,
        }}
      >
        {icon ? <TextField.Slot>{icon}</TextField.Slot> : null}
      </TextField.Root>

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

export default Input;
