'use client';

/**
 * Radio — MyTicketZM Design System §7 / §8.
 *
 * Contract props (exact, no extras):
 *   checked, onChange, label, disabled, name, value, size, style
 *
 * A single radio input, not a group: `name` ties siblings together so this can
 * be composed into any layout (a stacked list, a payment-method row) without a
 * wrapper dictating the arrangement. Focus uses the offset double ring from
 * global.css.
 */

import type { CSSProperties } from 'react';
import { Flex, Radio as RadixRadio, Text } from '@radix-ui/themes';

export interface RadioProps {
  checked?: boolean;
  onChange?: (value: string) => void;
  label?: string;
  disabled?: boolean;
  name?: string;
  value?: string;
  size?: '1' | '2' | '3';
  style?: CSSProperties;
}

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
  const control = (
    <RadixRadio
      data-testid="ds-radio"
      checked={checked}
      onValueChange={() => onChange?.(value ?? '')}
      disabled={disabled}
      name={name}
      value={value ?? ''}
      size={size}
      style={label ? undefined : style}
    />
  );

  if (!label) return control;

  return (
    <Text as="label" size="2" style={{ ...style }}>
      <Flex
        align="center"
        gap="2"
        style={{ cursor: disabled ? 'not-allowed' : 'pointer' }}
      >
        {control}
        <span style={{ color: 'var(--gray-12)' }}>{label}</span>
      </Flex>
    </Text>
  );
}

export default Radio;
