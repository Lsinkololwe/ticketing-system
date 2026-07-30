'use client';

/**
 * StyledCard — MyTicketZM design system surface primitive.
 *
 * Contract (spec §7): `children, padding, hover, interactive, style, onClick`
 *   hover: default | lift | glow | none
 *
 * Card language (spec §5): flat fill (--card-bg), 1px hairline alpha-gray
 * border (--gray-a5), soft two-step shadow. NO blur — transparency is reserved
 * for header bars and the marketing site's glass cards. NO colored left-border
 * accent: that pattern is banned, so emphasis is carried by the full hairline
 * and the shadow step instead.
 */

import type { CSSProperties, ReactNode } from 'react';

export type CardPadding = 'none' | 'sm' | 'md' | 'lg';
export type CardHover = 'default' | 'lift' | 'glow' | 'none';

export interface StyledCardProps {
  children?: ReactNode;
  padding?: CardPadding;
  hover?: CardHover;
  interactive?: boolean;
  style?: CSSProperties;
  onClick?: () => void;
}

const PADDING: Record<CardPadding, string> = {
  none: '0',
  sm: 'var(--space-4)',
  md: 'var(--space-5)',
  lg: 'var(--space-6)',
};

export function StyledCard({
  children,
  padding = 'md',
  hover = 'default',
  interactive = false,
  style,
  onClick,
}: StyledCardProps) {
  return (
    <div
      className="ds-styled-card"
      data-hover={hover}
      data-interactive={interactive || Boolean(onClick) || undefined}
      onClick={onClick}
      onKeyDown={
        onClick
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onClick();
              }
            }
          : undefined
      }
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      style={{
        padding: PADDING[padding],
        background: 'var(--card-bg)',
        border: 'var(--card-border)',
        borderRadius: 'var(--card-radius)',
        boxShadow: 'var(--card-shadow)',
        transition:
          'box-shadow var(--transition-default) var(--ease-standard), border-color var(--transition-default) var(--ease-standard), transform var(--transition-default) var(--ease-standard)',
        ...style,
      }}
    >
      {children}

      <style jsx global>{`
        .ds-styled-card[data-hover='default']:hover {
          box-shadow: var(--card-shadow-hover);
        }
        /* 2px rise — the system's only "press feel". No scale, no shrink. */
        .ds-styled-card[data-hover='lift']:hover {
          transform: translateY(-2px);
          box-shadow: var(--card-shadow-hover);
        }
        .ds-styled-card[data-hover='glow']:hover {
          border-color: var(--accent-a6);
          box-shadow: var(--card-shadow-hover);
        }
        .ds-styled-card[data-interactive] {
          cursor: pointer;
        }
        .ds-styled-card:focus-visible {
          outline: 2px solid var(--accent-8);
          outline-offset: 2px;
        }
      `}</style>
    </div>
  );
}

export default StyledCard;
