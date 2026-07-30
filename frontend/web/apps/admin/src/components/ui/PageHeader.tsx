'use client';

/**
 * PageHeader — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   title, description, breadcrumbs, actions
 *
 * Sentence case throughout (§10). Breadcrumbs encode real hierarchy — do not
 * pass a decorative trail; if a page has no parent, omit them.
 */

import type { ReactNode } from 'react';
import Link from 'next/link';
import { Box, Flex, Heading, Text } from '@radix-ui/themes';
import { NavArrowRight } from 'iconoir-react';

export interface Breadcrumb {
  label: string;
  href?: string;
}

export interface PageHeaderProps {
  title: string;
  description?: string;
  breadcrumbs?: Breadcrumb[];
  actions?: ReactNode;
}

export function PageHeader({
  title,
  description,
  breadcrumbs,
  actions,
}: PageHeaderProps) {
  return (
    <Flex direction="column" gap="3" mb="5">
      {breadcrumbs && breadcrumbs.length > 0 ? (
        <Flex asChild align="center" gap="1" wrap="wrap">
          <nav aria-label="Breadcrumb">
            {breadcrumbs.map((crumb, index) => {
              const isLast = index === breadcrumbs.length - 1;

              return (
                <Flex key={`${crumb.label}-${index}`} align="center" gap="1">
                  {index > 0 ? (
                    <NavArrowRight
                      aria-hidden="true"
                      style={{ width: 12, height: 12, color: 'var(--gray-9)' }}
                    />
                  ) : null}

                  {crumb.href && !isLast ? (
                    <Text size="1" asChild>
                      <Link
                        href={crumb.href}
                        style={{
                          color: 'var(--gray-11)',
                          textDecoration: 'none',
                        }}
                      >
                        {crumb.label}
                      </Link>
                    </Text>
                  ) : (
                    <Text
                      size="1"
                      style={{ color: isLast ? 'var(--gray-12)' : 'var(--gray-11)' }}
                      aria-current={isLast ? 'page' : undefined}
                    >
                      {crumb.label}
                    </Text>
                  )}
                </Flex>
              );
            })}
          </nav>
        </Flex>
      ) : null}

      <Flex
        direction={{ initial: 'column', sm: 'row' }}
        justify="between"
        align={{ initial: 'start', sm: 'center' }}
        gap="3"
      >
        <Box>
          <Heading size="6" weight="bold" style={{ color: 'var(--gray-12)' }}>
            {title}
          </Heading>
          {description ? (
            <Text
              size="2"
              mt="1"
              style={{ display: 'block', color: 'var(--gray-11)' }}
            >
              {description}
            </Text>
          ) : null}
        </Box>

        {actions ? (
          <Flex align="center" gap="2" style={{ flexShrink: 0 }}>
            {actions}
          </Flex>
        ) : null}
      </Flex>
    </Flex>
  );
}

export default PageHeader;
