'use client';

/**
 * PageHeader — MyTicketZM design system navigation primitive.
 *
 * Contract (spec §7): `title, description, breadcrumbs, actions`
 *
 * Copy rules (spec §10): sentence case titles, plain operational descriptions.
 * Actions carry no gradient — a primary action is a flat --accent-9 fill that
 * shifts to --accent-10 on hover. Destructive actions use --status-danger-*.
 */

import type { ReactNode } from 'react';
import { Box, Flex, Text, Heading, Button } from '@radix-ui/themes';
import { NavArrowRight } from 'iconoir-react';
import Link from 'next/link';

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

export interface PageHeaderProps {
  title: string;
  description?: string;
  breadcrumbs?: Breadcrumb[];
  actions?: PageAction[];
}

export function PageHeader({ title, description, breadcrumbs, actions }: PageHeaderProps) {
  return (
    <Box mb="6">
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Flex asChild align="center" gap="1" mb="3" wrap="wrap">
          <nav aria-label="Breadcrumb">
            {breadcrumbs.map((crumb, index) => (
              <Flex key={`${crumb.label}-${index}`} align="center" gap="1">
                {index > 0 && (
                  <NavArrowRight
                    aria-hidden="true"
                    width={14}
                    height={14}
                    style={{ color: 'var(--gray-9)' }}
                  />
                )}
                {crumb.href ? (
                  <Link
                    href={crumb.href}
                    style={{
                      color: 'var(--gray-10)',
                      textDecoration: 'none',
                      fontSize: 'var(--text-1-size)',
                    }}
                  >
                    {crumb.label}
                  </Link>
                ) : (
                  <Text size="1" style={{ color: 'var(--gray-11)' }}>
                    {crumb.label}
                  </Text>
                )}
              </Flex>
            ))}
          </nav>
        </Flex>
      )}

      <Flex
        justify="between"
        align={{ initial: 'start', sm: 'center' }}
        direction={{ initial: 'column', sm: 'row' }}
        gap="4"
      >
        <Box>
          <Heading size="6" style={{ color: 'var(--gray-12)', letterSpacing: '-0.02em' }}>
            {title}
          </Heading>
          {description && (
            <Text
              size="2"
              style={{ color: 'var(--gray-10)', display: 'block', marginTop: 4 }}
            >
              {description}
            </Text>
          )}
        </Box>

        {actions && actions.length > 0 && (
          <Flex gap="2" wrap="wrap">
            {actions.map((action, index) => {
              const isDanger = action.color === 'danger';
              const variant =
                action.variant === 'outline'
                  ? ('outline' as const)
                  : action.variant === 'ghost'
                    ? ('ghost' as const)
                    : ('solid' as const);
              const color = isDanger ? ('red' as const) : ('teal' as const);
              const cursorStyle = { cursor: action.disabled ? 'not-allowed' : 'pointer' };

              if (action.href) {
                return (
                  <Button
                    key={`${action.label}-${index}`}
                    data-testid={`page-header-action-${index}`}
                    size="2"
                    variant={variant}
                    color={color}
                    disabled={action.disabled}
                    style={cursorStyle}
                    asChild
                  >
                    <Link href={action.href}>
                      {action.icon}
                      {action.label}
                    </Link>
                  </Button>
                );
              }

              return (
                <Button
                  key={`${action.label}-${index}`}
                  data-testid={`page-header-action-${index}`}
                  size="2"
                  variant={variant}
                  color={color}
                  disabled={action.disabled}
                  style={cursorStyle}
                  onClick={action.onClick}
                >
                  {action.icon}
                  {action.label}
                </Button>
              );
            })}
          </Flex>
        )}
      </Flex>
    </Box>
  );
}

export default PageHeader;
