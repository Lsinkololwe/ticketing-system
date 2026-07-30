'use client';

import { useState, type CSSProperties, type ReactNode } from 'react';

/**
 * StyledCard — MyTicketZM Design System surface primitive.
 *
 * Contract (spec §7): children, padding, hover, interactive, style, onClick.
 * `hover: default | lift | glow | none`.
 *
 * Surface language (§5): flat fill (--card-bg), 1px hairline alpha-gray
 * border (--gray-a5), soft 2-step shadow (--card-shadow). NO colored
 * left-border accents. NO blur — blur is reserved for the top nav.
 *
 * `hover="lift"` is the 2px rise used by interactive event cards; there is no
 * scale or shrink press effect anywhere in this system.
 */
export interface StyledCardProps {
  children?: ReactNode;
  padding?: '0' | '2' | '3' | '4' | '5';
  hover?: 'default' | 'lift' | 'glow' | 'none';
  interactive?: boolean;
  style?: CSSProperties;
  onClick?: () => void;
}

const PADDING = { '0': 0, '2': 12, '3': 16, '4': 20, '5': 24 } as const;

export function StyledCard({
  children,
  padding = '4',
  hover = 'default',
  interactive = false,
  style,
  onClick,
}: StyledCardProps) {
  const [hovered, setHovered] = useState(false);
  const active = hovered && hover !== 'none';

  const hoverShadow =
    hover === 'glow'
      ? '0 0 0 1px var(--accent-a6), 0 4px 16px 0 var(--accent-a5)'
      : 'var(--card-shadow-hover)';

  return (
    <div
      data-testid="ds-styled-card"
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      role={interactive || onClick ? 'button' : undefined}
      tabIndex={interactive || onClick ? 0 : undefined}
      onKeyDown={
        interactive || onClick
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick?.();
              }
            }
          : undefined
      }
      style={{
        background: 'var(--card-bg)',
        border: 'var(--card-border)',
        borderRadius: 'var(--card-radius)',
        boxShadow: active ? hoverShadow : 'var(--card-shadow)',
        padding: PADDING[padding],
        cursor: interactive || onClick ? 'pointer' : 'default',
        transform: active && hover === 'lift' ? 'translateY(-2px)' : 'translateY(0)',
        transition:
          'transform var(--transition-default) var(--ease-standard), box-shadow var(--transition-default) var(--ease-standard)',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

export default StyledCard;
