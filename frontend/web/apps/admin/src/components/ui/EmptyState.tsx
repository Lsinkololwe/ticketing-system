'use client';

/**
 * EmptyState — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   icon, title, description, action, size
 *
 * size: sm | md | lg
 *
 * An empty screen is an invitation to act, not an apology. The title states
 * what is not there; the description says what to do about it; `action` is the
 * way to do it.
 */

import type { ReactNode } from 'react';
import { Box, Flex, Heading, Text } from '@radix-ui/themes';

export interface EmptyStateProps {
  /** Iconoir icon, 14–24px. */
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

const SIZES = {
  sm: { py: '5' as const, chip: 40, heading: '3' as const, maxWidth: 260 },
  md: { py: '7' as const, chip: 52, heading: '4' as const, maxWidth: 340 },
  lg: { py: '9' as const, chip: 64, heading: '5' as const, maxWidth: 420 },
};

export function EmptyState({
  icon,
  title,
  description,
  action,
  size = 'md',
}: EmptyStateProps) {
  const s = SIZES[size];

  return (
    <Flex
      direction="column"
      align="center"
      justify="center"
      gap="3"
      py={s.py}
      px="4"
      style={{
        borderRadius: 'var(--card-radius)',
        background: 'var(--gray-a2)',
        border: '1px dashed var(--gray-a6)',
      }}
    >
      {icon ? (
        <Flex
          align="center"
          justify="center"
          style={{
            width: s.chip,
            height: s.chip,
            borderRadius: 'var(--radius-4)',
            background: 'var(--gray-a3)',
            color: 'var(--gray-9)',
          }}
        >
          <Box style={{ display: 'flex', color: 'inherit' }}>{icon}</Box>
        </Flex>
      ) : null}

      <Heading
        size={s.heading}
        weight="medium"
        align="center"
        style={{ color: 'var(--gray-12)' }}
      >
        {title}
      </Heading>

      {description ? (
        <Text
          size="2"
          align="center"
          style={{
            color: 'var(--gray-11)',
            maxWidth: s.maxWidth,
            lineHeight: 1.6,
          }}
        >
          {description}
        </Text>
      ) : null}

      {action ? <Box mt="2">{action}</Box> : null}
    </Flex>
  );
}

export default EmptyState;
