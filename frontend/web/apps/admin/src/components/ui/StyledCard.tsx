'use client';

/**
 * Professional Styled Card Components
 *
 * Modern card designs with:
 * - Glass-morphism effects
 * - Subtle gradients and shadows
 * - Smooth hover transitions
 * - Dark mode optimized
 *
 * UI/UX Pro Max recommendations applied:
 * - Proper contrast ratios (WCAG AAA)
 * - cursor-pointer on interactive elements
 * - 150-300ms transitions
 * - prefers-reduced-motion respected
 */

import { forwardRef, type ReactNode, type CSSProperties } from 'react';
import { Box, Flex, Heading, Text } from '@radix-ui/themes';

// =============================================================================
// TYPES
// =============================================================================

interface StyledCardProps {
  children: ReactNode;
  /** Padding size */
  padding?: '3' | '4' | '5' | '6';
  /** Additional inline styles */
  style?: CSSProperties;
  /** Additional class names */
  className?: string;
  /** Click handler (makes card interactive) */
  onClick?: () => void;
  /** Hover effect style */
  hover?: 'default' | 'lift' | 'glow' | 'none';
}

interface StatCardProps {
  title: string;
  value: string;
  change?: string;
  changeType?: 'positive' | 'negative' | 'neutral';
  icon: ReactNode;
  onClick?: () => void;
}

interface SectionCardProps {
  title: string;
  children: ReactNode;
  action?: ReactNode;
  minHeight?: string;
}

// =============================================================================
// BASE STYLED CARD
// =============================================================================

/**
 * Base styled card with professional dark-mode styling
 */
export const StyledCard = forwardRef<HTMLDivElement, StyledCardProps>(
  function StyledCard(
    { children, padding = '5', style, className, onClick, hover = 'default' },
    ref
  ) {
    const isInteractive = !!onClick;
    const hoverClass = hover !== 'none' ? `styled-card-hover-${hover}` : '';

    return (
      <>
        <Box
          ref={ref}
          p={padding}
          className={`styled-card ${hoverClass} ${className || ''}`}
          onClick={onClick}
          style={{
            // Glass-like card background
            backgroundColor: 'var(--card-bg, var(--gray-2))',
            backgroundImage: 'linear-gradient(135deg, var(--gray-a2) 0%, transparent 100%)',
            borderRadius: '14px',
            border: '1px solid var(--gray-a4)',
            boxShadow: 'var(--card-shadow, 0 1px 3px rgba(0, 0, 0, 0.08))',
            transition: 'all 200ms ease',
            cursor: isInteractive ? 'pointer' : undefined,
            position: 'relative',
            overflow: 'hidden',
            // Subtle top highlight for depth
            ...style,
          }}
        >
          {/* Inner highlight for glass effect */}
          <Box
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: '1px',
              background: 'linear-gradient(90deg, transparent 0%, var(--gray-a4) 50%, transparent 100%)',
              pointerEvents: 'none',
            }}
          />
          {children}
        </Box>

        <style jsx global>{`
          @media (prefers-reduced-motion: no-preference) {
            .styled-card-hover-default:hover {
              border-color: var(--gray-a6);
              box-shadow: var(--card-shadow-hover, 0 4px 12px rgba(0, 0, 0, 0.1));
            }
            .styled-card-hover-lift:hover {
              transform: translateY(-2px);
              border-color: var(--violet-a5);
              box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12), 0 0 0 1px var(--violet-a3);
            }
            .styled-card-hover-glow:hover {
              border-color: var(--violet-a5);
              box-shadow: 0 0 20px var(--violet-a4), 0 4px 16px rgba(0, 0, 0, 0.1);
            }
          }
          /* Dark mode card enhancements */
          .dark .styled-card {
            background-image: linear-gradient(135deg, rgba(139, 92, 246, 0.03) 0%, transparent 100%);
          }
          .dark .styled-card-hover-lift:hover {
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3), 0 0 0 1px var(--violet-a4), 0 0 30px var(--violet-a3);
          }
        `}</style>
      </>
    );
  }
);

// =============================================================================
// STAT CARD
// =============================================================================

/**
 * Stat card for displaying metrics with icons
 */
export function StatCard({
  title,
  value,
  change,
  changeType = 'neutral',
  icon,
  onClick,
}: StatCardProps) {
  const changeColor = {
    positive: 'var(--green-11)',
    negative: 'var(--red-11)',
    neutral: 'var(--gray-11)',
  }[changeType];

  const changePrefix = {
    positive: '+',
    negative: '',
    neutral: '',
  }[changeType];

  return (
    <StyledCard onClick={onClick} hover={onClick ? 'lift' : 'default'}>
      <Flex justify="between" align="start" gap="3">
        <Flex direction="column" gap="1" style={{ flex: 1, minWidth: 0 }}>
          <Text
            size="2"
            weight="medium"
            style={{
              color: 'var(--gray-11)',
              letterSpacing: '0.01em',
            }}
          >
            {title}
          </Text>
          <Heading
            size="7"
            weight="bold"
            style={{
              letterSpacing: '-0.02em',
              lineHeight: 1.1,
              color: 'var(--gray-12)',
            }}
          >
            {value}
          </Heading>
          {change && (
            <Flex align="center" gap="1" mt="1">
              <Text
                size="1"
                weight="medium"
                style={{
                  color: changeColor,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '2px',
                }}
              >
                {changeType === 'positive' && (
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M6 2.5L10 6.5H7V9.5H5V6.5H2L6 2.5Z" fill="currentColor" />
                  </svg>
                )}
                {changeType === 'negative' && (
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M6 9.5L2 5.5H5V2.5H7V5.5H10L6 9.5Z" fill="currentColor" />
                  </svg>
                )}
                {changePrefix}
                {change}
              </Text>
            </Flex>
          )}
        </Flex>
        <Box
          style={{
            padding: '12px',
            borderRadius: '10px',
            background: 'linear-gradient(135deg, var(--violet-a3) 0%, var(--violet-a4) 100%)',
            border: '1px solid var(--violet-a4)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          {icon}
        </Box>
      </Flex>
    </StyledCard>
  );
}

// =============================================================================
// SECTION CARD
// =============================================================================

/**
 * Section card with title and optional action
 */
export function SectionCard({ title, children, action, minHeight }: SectionCardProps) {
  return (
    <StyledCard hover="none" style={{ minHeight }}>
      <Flex direction="column" gap="4" style={{ height: '100%' }}>
        <Flex justify="between" align="center">
          <Heading
            size="4"
            weight="medium"
            style={{
              color: 'var(--gray-12)',
              letterSpacing: '-0.01em',
            }}
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
// EMPTY STATE CARD
// =============================================================================

/**
 * Card for displaying empty states
 */
export function EmptyCard({
  message,
  icon,
  action,
}: {
  message: string;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Flex
      align="center"
      justify="center"
      direction="column"
      gap="3"
      py="8"
      style={{
        borderRadius: '8px',
        backgroundColor: 'var(--gray-a2)',
        border: '1px dashed var(--gray-a6)',
      }}
    >
      {icon && (
        <Box
          style={{
            padding: '12px',
            borderRadius: '12px',
            backgroundColor: 'var(--gray-a3)',
          }}
        >
          {icon}
        </Box>
      )}
      <Text color="gray" size="2" align="center" style={{ maxWidth: '240px' }}>
        {message}
      </Text>
      {action}
    </Flex>
  );
}

// =============================================================================
// INFO CARD
// =============================================================================

/**
 * Card for displaying information sections
 */
export function InfoCard({
  title,
  children,
  icon,
}: {
  title: string;
  children: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <StyledCard hover="none">
      <Flex direction="column" gap="3">
        <Flex align="center" gap="2">
          {icon && (
            <Box
              style={{
                color: 'var(--violet-11)',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              {icon}
            </Box>
          )}
          <Heading
            size="3"
            weight="medium"
            style={{ color: 'var(--gray-12)' }}
          >
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

/**
 * Row component for displaying key-value metrics
 */
export function MetricRow({
  label,
  value,
  valueColor,
}: {
  label: string;
  value: string | ReactNode;
  valueColor?: string;
}) {
  return (
    <Flex justify="between" align="center" py="2">
      <Text size="2" color="gray">
        {label}
      </Text>
      <Text size="2" weight="medium" style={{ color: valueColor || 'var(--gray-12)' }}>
        {value}
      </Text>
    </Flex>
  );
}

export default StyledCard;
