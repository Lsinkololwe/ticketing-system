'use client';

/**
 * PageHeader Component
 *
 * Consistent header for dashboard pages with:
 * - Title and optional description
 * - Breadcrumb navigation
 * - Action buttons
 */

import { ReactNode } from 'react';
import { Box, Flex, Text, Heading, Button } from '@radix-ui/themes';
import { NavArrowRight } from 'iconoir-react';
import Link from 'next/link';

// =============================================================================
// TYPES
// =============================================================================

export interface Breadcrumb {
  label: string;
  href?: string;
}

export interface PageAction {
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
  href?: string;
  variant?: 'solid' | 'outline' | 'ghost';
  color?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
}

interface PageHeaderProps {
  title: string;
  description?: string;
  breadcrumbs?: Breadcrumb[];
  actions?: PageAction[];
  children?: ReactNode;
}

// =============================================================================
// COMPONENT
// =============================================================================

export function PageHeader({
  title,
  description,
  breadcrumbs,
  actions,
  children,
}: PageHeaderProps) {
  return (
    <Box mb="6">
      {/* Breadcrumbs */}
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Flex align="center" gap="1" mb="3">
          {breadcrumbs.map((crumb, index) => (
            <Flex key={index} align="center" gap="1">
              {index > 0 && (
                <NavArrowRight style={{ width: 14, height: 14, color: 'var(--content-muted)' }} />
              )}
              {crumb.href ? (
                <Link
                  href={crumb.href}
                  style={{
                    color: 'var(--content-muted)',
                    textDecoration: 'none',
                    fontSize: '13px',
                  }}
                >
                  {crumb.label}
                </Link>
              ) : (
                <Text size="1" style={{ color: 'var(--content-secondary)' }}>
                  {crumb.label}
                </Text>
              )}
            </Flex>
          ))}
        </Flex>
      )}

      {/* Title and Actions Row */}
      <Flex
        justify="between"
        align={{ initial: 'start', sm: 'center' }}
        direction={{ initial: 'column', sm: 'row' }}
        gap="4"
      >
        {/* Title and Description */}
        <Box>
          <Heading size="6" style={{ color: 'var(--content-primary)' }}>
            {title}
          </Heading>
          {description && (
            <Text
              size="2"
              style={{ color: 'var(--content-muted)', display: 'block', marginTop: '4px' }}
            >
              {description}
            </Text>
          )}
        </Box>

        {/* Actions */}
        {actions && actions.length > 0 && (
          <Flex gap="2" wrap="wrap">
            {actions.map((action, index) => {
              const buttonProps = {
                size: '2' as const,
                variant: action.variant === 'outline' ? 'outline' as const : action.variant === 'ghost' ? 'ghost' as const : 'solid' as const,
                disabled: action.disabled,
                style: {
                  cursor: action.disabled ? 'not-allowed' : 'pointer',
                  ...(action.variant !== 'outline' && action.variant !== 'ghost'
                    ? {
                        background:
                          action.color === 'danger'
                            ? 'linear-gradient(135deg, #EF4444 0%, #DC2626 100%)'
                            : 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                      }
                    : {}),
                  ...(action.variant === 'outline'
                    ? {
                        borderColor:
                          action.color === 'danger'
                            ? 'rgba(239, 68, 68, 0.3)'
                            : 'rgba(16, 185, 129, 0.3)',
                        color: action.color === 'danger' ? '#EF4444' : 'var(--brand-500)',
                      }
                    : {}),
                },
              };

              if (action.href) {
                return (
                  <Button key={index} {...buttonProps} asChild>
                    <Link href={action.href}>
                      {action.icon}
                      {action.label}
                    </Link>
                  </Button>
                );
              }

              return (
                <Button {...buttonProps} onClick={action.onClick}>
                  {action.icon}
                  {action.label}
                </Button>
              );
            })}
          </Flex>
        )}
      </Flex>

      {/* Custom content */}
      {children && <Box mt="4">{children}</Box>}
    </Box>
  );
}

export default PageHeader;
