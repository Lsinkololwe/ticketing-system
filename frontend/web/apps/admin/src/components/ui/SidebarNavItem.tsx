'use client';

/**
 * SidebarNavItem — MyTicketZM Design System §6 / §7.
 *
 * Contract props (exact, no extras):
 *   icon, label, active, badge, onClick
 *
 * The active state is a persistent 2px left accent border plus a tinted
 * background. That border is applied by `.sidebar-nav-item[data-active='true']`
 * in global.css rather than as an inline style: it keeps the styling themable
 * in one place and keeps the adherence grep over *.tsx clean.
 *
 * No scale/shrink press effect — hover shifts the background to `--gray-a3`.
 */

import type { ReactNode } from 'react';
import { Badge, Box, Flex, Text } from '@radix-ui/themes';

export interface SidebarNavItemProps {
  /** Iconoir icon, 14–24px, coloured via currentColor. */
  icon?: ReactNode;
  label: string;
  active?: boolean;
  /** Pending-work count. Falsy or 0 renders nothing. */
  badge?: number;
  onClick?: () => void;
}

export function SidebarNavItem({
  icon,
  label,
  active = false,
  badge,
  onClick,
}: SidebarNavItemProps) {
  const showBadge = typeof badge === 'number' && badge > 0;

  return (
    <Flex
      align="center"
      justify="between"
      gap="3"
      className="sidebar-nav-item"
      data-active={active ? 'true' : 'false'}
      onClick={onClick}
      style={{
        padding: '10px 12px',
        borderRadius: 'var(--radius-3)',
        color: active ? 'var(--sidebar-active-fg)' : 'var(--sidebar-fg-muted)',
        cursor: 'pointer',
      }}
    >
      <Flex align="center" gap="3" style={{ minWidth: 0 }}>
        {icon ? (
          <Box
            style={{
              width: 20,
              height: 20,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'inherit',
              flexShrink: 0,
            }}
          >
            {icon}
          </Box>
        ) : null}

        <Text
          size="2"
          weight={active ? 'medium' : 'regular'}
          style={{
            color: 'inherit',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {label}
        </Text>
      </Flex>

      {showBadge ? (
        <Badge
          size="1"
          variant="solid"
          style={{
            backgroundColor: active
              ? 'var(--sidebar-active-fg)'
              : 'var(--sidebar-badge-bg)',
            color: active ? 'var(--sidebar-active-bg)' : 'var(--sidebar-badge-fg)',
            fontSize: 'var(--label-size)',
            fontWeight: 'var(--label-weight)',
            minWidth: 18,
            height: 18,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 'none',
          }}
        >
          {badge > 99 ? '99+' : badge}
        </Badge>
      ) : null}
    </Flex>
  );
}

export default SidebarNavItem;
