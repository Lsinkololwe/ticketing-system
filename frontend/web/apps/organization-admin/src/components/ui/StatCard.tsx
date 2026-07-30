'use client';

/**
 * StatCard — MyTicketZM design system bento tile.
 *
 * Contract (spec §7): `title, value, icon, change, changeLabel, trend`
 *   trend: up | down | neutral
 *
 * Layout notes:
 *   - Bento radius (14px) so tiles read as a distinct language from the 6-8px
 *     form controls sitting next to them.
 *   - The value renders in `.ds-amount` (Fira Code, tabular figures) so a row
 *     of stat cards never jitters between renders and the digits line up
 *     column-to-column. Currency should already be formatted as "K 125,430"
 *     by the caller — this component does not invent a currency symbol.
 *   - Trend uses generic status colors (green/red), NOT jade. Jade is the money
 *     role and belongs to the amount, not to the direction it moved.
 */

import type { ReactNode } from 'react';
import { NavArrowUp, NavArrowDown, Minus } from 'iconoir-react';

export type StatTrend = 'up' | 'down' | 'neutral';

export interface StatCardProps {
  title: string;
  value: string | number;
  icon?: ReactNode;
  change?: number;
  changeLabel?: string;
  trend?: StatTrend;
}

const TREND: Record<StatTrend, { color: string; surface: string; icon: ReactNode }> = {
  up: {
    color: 'var(--status-success-11)',
    surface: 'var(--status-success-a3)',
    icon: <NavArrowUp width={14} height={14} />,
  },
  down: {
    color: 'var(--status-danger-11)',
    surface: 'var(--status-danger-a3)',
    icon: <NavArrowDown width={14} height={14} />,
  },
  neutral: {
    color: 'var(--gray-11)',
    surface: 'var(--gray-a3)',
    icon: <Minus width={14} height={14} />,
  },
};

export function StatCard({ title, value, icon, change, changeLabel, trend }: StatCardProps) {
  // Derive direction from the delta when the caller does not state one.
  const direction: StatTrend | undefined =
    trend ?? (change === undefined ? undefined : change > 0 ? 'up' : change < 0 ? 'down' : 'neutral');

  const display = typeof value === 'number' ? value.toLocaleString() : value;

  return (
    <div className="ds-card-bento" style={{ padding: 'var(--space-5)' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-4)',
        }}
      >
        {icon && (
          <span
            aria-hidden="true"
            style={{
              width: 40,
              height: 40,
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 'var(--radius-3)',
              background: 'var(--accent-a3)',
              color: 'var(--accent-11)',
            }}
          >
            {icon}
          </span>
        )}

        {direction && change !== undefined && (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              padding: '4px 8px',
              borderRadius: 'var(--radius-2)',
              background: TREND[direction].surface,
              color: TREND[direction].color,
              fontSize: 'var(--text-1-size)',
              fontWeight: 'var(--weight-medium)',
            }}
          >
            {TREND[direction].icon}
            <span className="ds-amount">
              {change > 0 ? '+' : ''}
              {change}%
            </span>
          </span>
        )}
      </div>

      <span className="ds-label" style={{ display: 'block', marginBottom: 6 }}>
        {title}
      </span>

      <div
        className="ds-amount"
        style={{
          fontSize: 'var(--heading-6-size)',
          lineHeight: 'var(--heading-6-line)',
          fontWeight: 'var(--weight-semibold)',
          color: 'var(--gray-12)',
        }}
      >
        {display}
      </div>

      {changeLabel && (
        <span
          style={{
            display: 'block',
            marginTop: 'var(--space-2)',
            fontSize: 'var(--text-1-size)',
            color: 'var(--gray-10)',
          }}
        >
          {changeLabel}
        </span>
      )}
    </div>
  );
}

export default StatCard;
