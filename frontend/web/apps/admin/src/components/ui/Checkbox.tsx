'use client';

/**
 * Checkbox — MyTicketZM Design System §7 / §8.
 *
 * Contract props (exact, no extras):
 *   checked, defaultChecked, onChange, label, disabled, size, style
 *
 * Focus uses the offset double ring
 * (`0 0 0 2px --color-background, 0 0 0 4px --accent-8`) declared in
 * global.css. The whole row is the hit target so the label is clickable, and
 * it stays at least 44px wide in practice by sitting on a `Text` baseline.
 */

import type { CSSProperties } from 'react';
import { Checkbox as RadixCheckbox, Flex, Text } from '@radix-ui/themes';

export interface CheckboxProps {
  checked?: boolean;
  defaultChecked?: boolean;
  onChange?: (checked: boolean) => void;
  label?: string;
  disabled?: boolean;
  size?: '1' | '2' | '3';
  style?: CSSProperties;
}

export function Checkbox({
  checked,
  defaultChecked,
  onChange,
  label,
  disabled = false,
  size = '2',
  style,
}: CheckboxProps) {
  const control = (
    <RadixCheckbox
      data-testid="ds-checkbox"
      checked={checked}
      defaultChecked={defaultChecked}
      onCheckedChange={(next) => onChange?.(next === true)}
      disabled={disabled}
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

export default Checkbox;
