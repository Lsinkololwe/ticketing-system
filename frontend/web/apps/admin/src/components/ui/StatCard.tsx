'use client';

/**
 * StatCard — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   title, value, icon, change, changeLabel, trend
 *
 * trend: up | down | neutral
 *
 * The value sits on `--heading-7`, tabular-figured so a live-updating figure
 * never jitters. Currency values should be passed already wrapped in <Amount>
 * so they pick up Fira Code.
 *
 * `trend` colours the delta only — an up/down arrow plus a signed figure, so
 * the direction survives colour-blindness and greyscale. It never colours the
 * headline number.
 */

import type { ReactNode } from 'react';
import { Box, Flex, Heading, Text } from '@radix-ui/themes';
import { ArrowDown, ArrowUp, Minus } from 'iconoir-react';
import { StyledCard } from './StyledCard';

export interface StatCardProps {
  title: string;
  value: ReactNode;
  /** Iconoir icon, 14–24px. Rendered inside the accent chip. */
  icon?: ReactNode;
  /** The delta itself, e.g. "12%" or "5". */
  change?: string;
  /** What the delta is measured against, e.g. "from last month". */
  changeLabel?: string;
  trend?: 'up' | 'down' | 'neutral';
}

const TREND_COLOR: Record<'up' | 'down' | 'neutral', string> = {
  // Generic movement, not money: status-success / status-danger, never jade.
  up: 'var(--status-success-11)',
  down: 'var(--status-danger-11)',
  neutral: 'var(--gray-11)',
};

const TREND_ICON: Record<'up' | 'down' | 'neutral', typeof ArrowUp> = {
  up: ArrowUp,
  down: ArrowDown,
  neutral: Minus,
};

export function StatCard({
  title,
  value,
  icon,
  change,
  changeLabel,
  trend = 'neutral',
}: StatCardProps) {
  const TrendIcon = TREND_ICON[trend];

  return (
    <StyledCard hover="default">
      <Flex justify="between" align="start" gap="3">
        <Flex direction="column" gap="1" style={{ flex: 1, minWidth: 0 }}>
          <Text className="ds-label" as="p">
            {title}
          </Text>

          <Heading
            size="7"
            weight="bold"
            style={{
              color: 'var(--gray-12)',
              letterSpacing: '-0.02em',
              lineHeight: 1.1,
            }}
          >
            {value}
          </Heading>

          {change ? (
            <Flex align="center" gap="1" mt="1">
              <TrendIcon
                style={{ width: 14, height: 14, color: TREND_COLOR[trend] }}
              />
              <Text size="1" weight="medium" style={{ color: TREND_COLOR[trend] }}>
                {change}
              </Text>
              {changeLabel ? (
                <Text size="1" style={{ color: 'var(--gray-11)' }}>
                  {changeLabel}
                </Text>
              ) : null}
            </Flex>
          ) : null}
        </Flex>

        {icon ? (
          <Flex
            align="center"
            justify="center"
            // .ds-accent-chip is the ONE sanctioned gradient in dashboard UI.
            className="ds-accent-chip"
            style={{
              width: 40,
              height: 40,
              borderRadius: 'var(--radius-3)',
              flexShrink: 0,
            }}
          >
            <Box style={{ display: 'flex', color: 'inherit' }}>{icon}</Box>
          </Flex>
        ) : null}
      </Flex>
    </StyledCard>
  );
}

export default StatCard;
