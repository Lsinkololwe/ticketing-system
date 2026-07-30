'use client';

import { Card, Flex, Box, Text, Heading } from '@radix-ui/themes';
import type { ReactNode } from 'react';

/**
 * StatTile — a single bento stat tile.
 *
 * DS: dashboard stats are flat bento tiles (hairline border, soft 2-step
 * shadow, rounded corners) with a small tinted icon chip — NOT full-bleed
 * gradient cards. The value uses tabular figures so a row of tiles stays
 * aligned. Built on the Radix `Card` surface primitive.
 */
export type StatAccent = 'brand' | 'money' | 'info' | 'warning' | 'neutral';

/* Styled through ROLE tokens, never raw scale steps. */
const ACCENT_VARS: Record<StatAccent, { surface: string; text: string }> = {
  brand: { surface: 'var(--color-secondary-surface)', text: 'var(--color-secondary-text)' },
  money: { surface: 'var(--color-money-surface)', text: 'var(--color-money-text)' },
  info: { surface: 'var(--status-info-a3)', text: 'var(--status-info-11)' },
  warning: { surface: 'var(--status-warning-a3)', text: 'var(--status-warning-11)' },
  neutral: { surface: 'var(--gray-a3)', text: 'var(--gray-11)' },
};

export interface StatTileProps {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  accent?: StatAccent;
}

export function StatTile({ label, value, hint, icon, accent = 'brand' }: StatTileProps) {
  const c = ACCENT_VARS[accent];
  return (
    <Card size="2" className="ds-lift" style={{ borderRadius: 'var(--card-radius-bento)' }}>
      <Flex justify="between" align="start" gap="3">
        <Box>
          <Text as="div" size="1" className="ds-label" mb="2">
            {label}
          </Text>
          <Heading size="7" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {value}
          </Heading>
          {hint && (
            <Text as="div" size="1" color="gray" mt="1">
              {hint}
            </Text>
          )}
        </Box>
        {icon && (
          <Flex
            align="center"
            justify="center"
            style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              flexShrink: 0,
              background: c.surface,
              color: c.text,
            }}
          >
            {icon}
          </Flex>
        )}
      </Flex>
    </Card>
  );
}

export default StatTile;
