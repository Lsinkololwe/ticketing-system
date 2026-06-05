'use client';

/**
 * StatCard Component
 *
 * Displays a metric/statistic with:
 * - Large value display
 * - Trend indicator (up/down/neutral)
 * - Optional comparison label
 * - Icon and title
 */

import { ReactNode } from 'react';
import { Box, Flex, Text, Heading } from '@radix-ui/themes';
import { NavArrowUp, NavArrowDown, Minus } from 'iconoir-react';

// =============================================================================
// TYPES
// =============================================================================

type TrendDirection = 'up' | 'down' | 'neutral';

interface StatCardProps {
  /** Card title */
  title: string;
  /** Main value to display */
  value: string | number;
  /** Optional icon */
  icon?: ReactNode;
  /** Percentage change */
  change?: number;
  /** Comparison label (e.g., "vs last month") */
  changeLabel?: string;
  /** Trend direction */
  trend?: TrendDirection;
  /** Format as currency */
  isCurrency?: boolean;
  /** Currency symbol */
  currencySymbol?: string;
  /** Additional subtitle */
  subtitle?: string;
  /** Loading state */
  loading?: boolean;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
}

// =============================================================================
// COMPONENT
// =============================================================================

export function StatCard({
  title,
  value,
  icon,
  change,
  changeLabel,
  trend,
  isCurrency = false,
  currencySymbol = 'K',
  subtitle,
  loading = false,
  size = 'md',
}: StatCardProps) {
  // Size configurations
  const sizeConfig = {
    sm: {
      padding: '16px',
      iconSize: 32,
      iconInner: 16,
      valueSize: '5' as const,
    },
    md: {
      padding: '24px',
      iconSize: 40,
      iconInner: 20,
      valueSize: '6' as const,
    },
    lg: {
      padding: '32px',
      iconSize: 48,
      iconInner: 24,
      valueSize: '7' as const,
    },
  };

  const config = sizeConfig[size];

  // Trend configuration
  const trendConfig: Record<TrendDirection, { color: string; icon: ReactNode }> = {
    up: {
      color: 'var(--success-500)',
      icon: <NavArrowUp style={{ width: 14, height: 14 }} />,
    },
    down: {
      color: 'var(--error-500)',
      icon: <NavArrowDown style={{ width: 14, height: 14 }} />,
    },
    neutral: {
      color: 'var(--content-muted)',
      icon: <Minus style={{ width: 14, height: 14 }} />,
    },
  };

  // Format value
  const formattedValue = isCurrency
    ? `${currencySymbol} ${typeof value === 'number' ? value.toLocaleString() : value}`
    : typeof value === 'number'
      ? value.toLocaleString()
      : value;

  // Determine trend direction from change if not provided
  const effectiveTrend = trend ?? (change !== undefined ? (change > 0 ? 'up' : change < 0 ? 'down' : 'neutral') : undefined);

  if (loading) {
    return (
      <Box
        style={{
          padding: config.padding,
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Flex direction="column" gap="3">
          <Box
            style={{
              width: config.iconSize,
              height: config.iconSize,
              borderRadius: '12px',
              background: 'var(--surface-subtle)',
              animation: 'pulse 2s infinite',
            }}
          />
          <Box
            style={{
              width: '60%',
              height: '16px',
              borderRadius: '4px',
              background: 'var(--surface-subtle)',
              animation: 'pulse 2s infinite',
            }}
          />
          <Box
            style={{
              width: '80%',
              height: '32px',
              borderRadius: '6px',
              background: 'var(--surface-subtle)',
              animation: 'pulse 2s infinite',
            }}
          />
        </Flex>
        <style jsx global>{`
          @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
          }
        `}</style>
      </Box>
    );
  }

  return (
    <Box
      style={{
        padding: config.padding,
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: '16px',
        transition: 'all 200ms ease',
      }}
    >
      {/* Header with Icon and Trend */}
      <Flex justify="between" align="start" mb="4">
        {icon && (
          <Box
            style={{
              width: config.iconSize,
              height: config.iconSize,
              borderRadius: '12px',
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid rgba(16, 185, 129, 0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--brand-500)',
            }}
          >
            {icon}
          </Box>
        )}

        {/* Trend Badge */}
        {effectiveTrend && change !== undefined && (
          <Flex
            align="center"
            gap="1"
            style={{
              color: trendConfig[effectiveTrend].color,
              padding: '4px 8px',
              borderRadius: '6px',
              background: `${trendConfig[effectiveTrend].color}15`,
            }}
          >
            {trendConfig[effectiveTrend].icon}
            <Text size="1" weight="medium">
              {change > 0 ? '+' : ''}{change}%
            </Text>
          </Flex>
        )}
      </Flex>

      {/* Title */}
      <Text
        size="2"
        style={{
          color: 'var(--content-muted)',
          display: 'block',
          marginBottom: '4px',
        }}
      >
        {title}
      </Text>

      {/* Value */}
      <Heading
        size={config.valueSize}
        style={{
          color: 'var(--content-primary)',
          letterSpacing: '-0.02em',
        }}
      >
        {formattedValue}
      </Heading>

      {/* Subtitle or Change Label */}
      {(subtitle || changeLabel) && (
        <Text
          size="1"
          style={{
            color: 'var(--content-muted)',
            display: 'block',
            marginTop: '8px',
          }}
        >
          {subtitle || changeLabel}
        </Text>
      )}
    </Box>
  );
}

// =============================================================================
// STAT GRID
// =============================================================================

interface StatGridProps {
  children: ReactNode;
  columns?: number;
}

export function StatGrid({ children, columns = 4 }: StatGridProps) {
  return (
    <Box
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(auto-fit, minmax(${240}px, 1fr))`,
        gap: '16px',
      }}
    >
      {children}
    </Box>
  );
}

export default StatCard;
