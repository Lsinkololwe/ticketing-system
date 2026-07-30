'use client';

/**
 * StyledCard — MyTicketZM Design System §5 / §7.
 *
 * Contract props (exact, no extras):
 *   children, padding, hover, interactive, style, onClick
 *
 * hover: default | lift | glow | none
 *
 * The DS card language is deliberately flat: a solid `--card-bg` fill, a 1px
 * `--gray-a5` hairline border and a soft two-step shadow. Bento tiles are
 * rounder (14px) than form controls (6–8px) so a tile never reads as a giant
 * input.
 *
 * Explicitly NOT here, by design:
 *   - no gradient or glass fill (blur is reserved for header bars)
 *   - no colored left-border accent stripe (that pattern is banned)
 *   - no scale/shrink press effect
 */

import { forwardRef, type CSSProperties, type ReactNode } from 'react';
import { Box } from '@radix-ui/themes';

export interface StyledCardProps {
  children: ReactNode;
  /** Radix padding step. */
  padding?: '3' | '4' | '5' | '6';
  hover?: 'default' | 'lift' | 'glow' | 'none';
  /** Marks the card as an affordance: pointer cursor + keyboard reachable. */
  interactive?: boolean;
  style?: CSSProperties;
  onClick?: () => void;
}

export const StyledCard = forwardRef<HTMLDivElement, StyledCardProps>(
  function StyledCard(
    { children, padding = '5', hover = 'default', interactive, style, onClick },
    ref
  ) {
    // A click handler implies interactivity even if the flag was omitted.
    const isInteractive = interactive ?? Boolean(onClick);

    return (
      <Box
        ref={ref}
        p={padding}
        className={`ds-styled-card ds-styled-card--${hover}`}
        onClick={onClick}
        role={isInteractive ? 'button' : undefined}
        tabIndex={isInteractive ? 0 : undefined}
        onKeyDown={
          isInteractive && onClick
            ? (event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onClick();
                }
              }
            : undefined
        }
        style={{
          background: 'var(--card-bg)',
          border: 'var(--card-border)',
          borderRadius: 'var(--card-radius-bento)',
          boxShadow: 'var(--card-shadow)',
          cursor: isInteractive ? 'pointer' : undefined,
          transition:
            'box-shadow var(--transition-default) var(--ease-standard),' +
            'border-color var(--transition-default) var(--ease-standard),' +
            'transform var(--transition-default) var(--ease-standard)',
          ...style,
        }}
      >
        {children}
      </Box>
    );
  }
);

export default StyledCard;
