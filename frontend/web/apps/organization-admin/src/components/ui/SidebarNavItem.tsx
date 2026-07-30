'use client';

/**
 * SidebarNavItem — MyTicketZM design system navigation primitive.
 *
 * Contract (spec §7): `icon, label, active, badge, onClick`
 *
 * Active items get a persistent 2px left accent edge plus a tinted background
 * (spec §6). Both live in `.ds-nav-item[data-active='true']` in global.css
 * rather than as an inline style — inline colored left edges are the banned
 * accent-card pattern, and keeping the rule in CSS lets the audit grep stay
 * clean while the sanctioned nav treatment still renders.
 */

import type { ReactNode } from 'react';

export interface SidebarNavItemProps {
  icon?: ReactNode;
  label: string;
  active?: boolean;
  badge?: number;
  onClick?: () => void;
}

export function SidebarNavItem({ icon, label, active = false, badge, onClick }: SidebarNavItemProps) {
  return (
    <button
      type="button"
      data-testid="ds-sidebar-nav-item"
      className="ds-nav-item"
      data-active={active}
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
      style={{
        width: '100%',
        background: 'transparent',
        fontFamily: 'var(--font-sans)',
        fontSize: 'var(--text-2-size)',
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', minWidth: 0 }}>
        {icon && (
          <span
            aria-hidden="true"
            style={{
              width: 18,
              height: 18,
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'currentColor',
            }}
          >
            {icon}
          </span>
        )}
        <span
          style={{
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {label}
        </span>
      </span>

      {badge !== undefined && badge > 0 && (
        <span
          className="ds-amount"
          style={{
            minWidth: 18,
            height: 18,
            padding: '0 5px',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 'var(--radius-full, 999px)',
            background: active ? 'var(--accent-a5)' : 'var(--gray-a4)',
            color: active ? 'var(--accent-11)' : 'var(--gray-11)',
            fontSize: 'var(--label-size)',
            fontWeight: 'var(--weight-semibold)',
          }}
        >
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </button>
  );
}

export default SidebarNavItem;
