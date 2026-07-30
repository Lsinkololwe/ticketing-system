'use client';

/**
 * Local composition helpers — NOT §7 contract components.
 *
 * These are thin arrangements built on top of <StyledCard>, kept separate from
 * the contract files so those stay exactly as the design system declares them.
 * They exist because admin screens are dense and repeat three shapes:
 *
 *   SectionCard — a titled panel with an optional action in the top-right
 *   InfoCard    — a titled panel with an optional accent icon
 *   MetricRow   — a label/value row inside one of the above
 *
 * All three inherit the DS card language from StyledCard: flat fill, hairline
 * border, soft shadow, no gradient, no left-border accent.
 */

import type { ReactNode } from 'react';
import { Box, Flex, Heading, Text } from '@radix-ui/themes';
import { StyledCard } from './StyledCard';

// =============================================================================
// SECTION CARD
// =============================================================================

export interface SectionCardProps {
  title: string;
  children: ReactNode;
  action?: ReactNode;
  minHeight?: string;
}

export function SectionCard({
  title,
  children,
  action,
  minHeight,
}: SectionCardProps) {
  return (
    <StyledCard hover="none" style={{ minHeight }}>
      <Flex direction="column" gap="4" style={{ height: '100%' }}>
        <Flex justify="between" align="center" gap="3">
          <Heading
            size="4"
            weight="medium"
            style={{ color: 'var(--gray-12)', letterSpacing: '-0.01em' }}
          >
            {title}
          </Heading>
          {action}
        </Flex>
        <Box style={{ flex: 1 }}>{children}</Box>
      </Flex>
    </StyledCard>
  );
}

// =============================================================================
// INFO CARD
// =============================================================================

export interface InfoCardProps {
  title: string;
  children: ReactNode;
  /** Iconoir icon, 14–24px. */
  icon?: ReactNode;
}

export function InfoCard({ title, children, icon }: InfoCardProps) {
  return (
    <StyledCard hover="none">
      <Flex direction="column" gap="3">
        <Flex align="center" gap="2">
          {icon ? (
            <Box style={{ display: 'flex', color: 'var(--accent-11)' }}>{icon}</Box>
          ) : null}
          <Heading size="3" weight="medium" style={{ color: 'var(--gray-12)' }}>
            {title}
          </Heading>
        </Flex>
        {children}
      </Flex>
    </StyledCard>
  );
}

// =============================================================================
// METRIC ROW
// =============================================================================

export interface MetricRowProps {
  label: string;
  value: string | ReactNode;
  /** Pass a token, e.g. `var(--color-money-text)` for money figures. */
  valueColor?: string;
}

export function MetricRow({ label, value, valueColor }: MetricRowProps) {
  return (
    <Flex justify="between" align="center" gap="3" py="2">
      <Text size="2" style={{ color: 'var(--gray-11)' }}>
        {label}
      </Text>
      <Text
        size="2"
        weight="medium"
        style={{ color: valueColor ?? 'var(--gray-12)' }}
      >
        {value}
      </Text>
    </Flex>
  );
}
