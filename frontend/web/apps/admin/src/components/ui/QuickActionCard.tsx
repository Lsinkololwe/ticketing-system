'use client';

/**
 * QuickActionCard — MyTicketZM Design System §7.
 *
 * Contract props (exact, no extras):
 *   title, description, icon, href, onClick
 *
 * An interactive bento tile: accent icon chip, title, one line of description,
 * a chevron affordance. Lifts 2px on hover (`.ds-lift`), never scales on press.
 * Renders as a link when `href` is given so middle-click and "open in new tab"
 * behave, otherwise as a button.
 */

import type { ReactNode } from 'react';
import Link from 'next/link';
import { Box, Flex, Text } from '@radix-ui/themes';
import { NavArrowRight } from 'iconoir-react';
import { StyledCard } from './StyledCard';

export interface QuickActionCardProps {
  title: string;
  description?: string;
  /** Iconoir icon, 14–24px. Rendered inside the accent chip. */
  icon?: ReactNode;
  href?: string;
  onClick?: () => void;
}

export function QuickActionCard({
  title,
  description,
  icon,
  href,
  onClick,
}: QuickActionCardProps) {
  const card = (
    <StyledCard padding="4" hover="lift" interactive onClick={onClick}>
      <Flex align="center" gap="3">
        {icon ? (
          <Flex
            align="center"
            justify="center"
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

        <Flex direction="column" gap="1" style={{ flex: 1, minWidth: 0 }}>
          <Text size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
            {title}
          </Text>
          {description ? (
            <Text size="1" style={{ color: 'var(--gray-11)' }}>
              {description}
            </Text>
          ) : null}
        </Flex>

        <NavArrowRight
          style={{ width: 16, height: 16, color: 'var(--gray-9)', flexShrink: 0 }}
        />
      </Flex>
    </StyledCard>
  );

  if (!href) return card;

  return (
    <Link href={href} style={{ textDecoration: 'none', display: 'block' }}>
      {card}
    </Link>
  );
}

export default QuickActionCard;
