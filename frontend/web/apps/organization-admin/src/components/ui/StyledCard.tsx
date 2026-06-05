'use client';

/**
 * StyledCard Component
 *
 * Consistent card styling for dashboard content with:
 * - Glassmorphism effect
 * - Optional header with title and actions
 * - Loading and empty states
 */

import { ReactNode } from 'react';
import { Box, Flex, Text, Heading, Spinner } from '@radix-ui/themes';

// =============================================================================
// TYPES
// =============================================================================

interface CardHeaderAction {
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
}

interface StyledCardProps {
  /** Optional title for the card header */
  title?: string;
  /** Optional subtitle */
  subtitle?: string;
  /** Optional icon displayed before the title */
  icon?: ReactNode;
  /** Header actions (links/buttons) */
  headerActions?: CardHeaderAction[];
  /** Custom header content (overrides title/subtitle/icon) */
  header?: ReactNode;
  /** Card content */
  children?: ReactNode;
  /** Show loading spinner */
  loading?: boolean;
  /** Padding size */
  padding?: 'none' | 'sm' | 'md' | 'lg';
  /** Card variant */
  variant?: 'default' | 'elevated' | 'outlined' | 'filled';
  /** Additional styles */
  style?: React.CSSProperties;
  /** Class name */
  className?: string;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function StyledCard({
  title,
  subtitle,
  icon,
  headerActions,
  header,
  children,
  loading = false,
  padding = 'md',
  variant = 'default',
  style,
  className,
}: StyledCardProps) {
  // Padding values
  const paddingValues = {
    none: '0',
    sm: '16px',
    md: '24px',
    lg: '32px',
  };

  // Variant styles
  const variantStyles: Record<string, React.CSSProperties> = {
    default: {
      background: 'var(--surface-elevated)',
      border: '1px solid var(--surface-border)',
    },
    elevated: {
      background: 'var(--surface-elevated)',
      border: '1px solid var(--surface-border)',
      boxShadow: '0 4px 20px rgba(0, 0, 0, 0.1)',
    },
    outlined: {
      background: 'transparent',
      border: '1px solid var(--surface-border)',
    },
    filled: {
      background: 'var(--surface-subtle)',
      border: 'none',
    },
  };

  const hasHeader = title || subtitle || icon || header || headerActions;

  return (
    <Box
      className={className}
      style={{
        borderRadius: '16px',
        overflow: 'hidden',
        ...variantStyles[variant],
        ...style,
      }}
    >
      {/* Header */}
      {hasHeader && (
        <Box
          style={{
            padding: paddingValues[padding],
            paddingBottom: title ? '16px' : paddingValues[padding],
            borderBottom: children ? '1px solid var(--surface-border)' : 'none',
          }}
        >
          {header || (
            <Flex justify="between" align="center">
              <Flex align="center" gap="3">
                {icon && (
                  <Box
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: '10px',
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
                <Box>
                  {title && (
                    <Heading size="4" style={{ color: 'var(--content-primary)' }}>
                      {title}
                    </Heading>
                  )}
                  {subtitle && (
                    <Text size="2" style={{ color: 'var(--content-muted)', marginTop: '2px' }}>
                      {subtitle}
                    </Text>
                  )}
                </Box>
              </Flex>

              {headerActions && headerActions.length > 0 && (
                <Flex gap="2">
                  {headerActions.map((action, index) => (
                    <Text
                      key={index}
                      size="2"
                      weight="medium"
                      onClick={action.onClick}
                      style={{
                        color: 'var(--brand-500)',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                      }}
                    >
                      {action.icon}
                      {action.label}
                    </Text>
                  ))}
                </Flex>
              )}
            </Flex>
          )}
        </Box>
      )}

      {/* Content */}
      {loading ? (
        <Flex
          align="center"
          justify="center"
          style={{
            padding: paddingValues[padding],
            minHeight: '120px',
          }}
        >
          <Spinner size="3" />
        </Flex>
      ) : children ? (
        <Box
          style={{
            padding: hasHeader ? paddingValues[padding] : paddingValues[padding],
            paddingTop: hasHeader ? '16px' : paddingValues[padding],
          }}
        >
          {children}
        </Box>
      ) : null}
    </Box>
  );
}

export default StyledCard;
