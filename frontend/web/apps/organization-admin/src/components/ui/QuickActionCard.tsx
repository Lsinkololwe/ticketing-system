'use client';

/**
 * QuickActionCard — MyTicketZM design system bento tile.
 *
 * Contract (spec §7): `title, description, icon, href, onClick`
 *
 * The icon tile is one of the two sanctioned gradients in the whole dashboard
 * (`.ds-accent-chip`, a small 135deg accent chip). Everything else stays flat.
 * The tile lifts 2px on hover — the system has no scale or shrink press effect.
 */

import type { ReactNode } from 'react';
import Link from 'next/link';
import { NavArrowRight } from 'iconoir-react';

export interface QuickActionCardProps {
  title: string;
  description?: string;
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
  const body = (
    <>
      {icon && (
        <span
          aria-hidden="true"
          className="ds-accent-chip"
          style={{
            width: 40,
            height: 40,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 'var(--radius-3)',
          }}
        >
          {icon}
        </span>
      )}

      <span style={{ flex: 1, minWidth: 0 }}>
        <span
          style={{
            display: 'block',
            fontSize: 'var(--text-2-size)',
            fontWeight: 'var(--weight-semibold)',
            color: 'var(--gray-12)',
          }}
        >
          {title}
        </span>
        {description && (
          <span
            style={{
              display: 'block',
              marginTop: 2,
              fontSize: 'var(--text-1-size)',
              lineHeight: 'var(--text-2-line)',
              color: 'var(--gray-10)',
            }}
          >
            {description}
          </span>
        )}
      </span>

      <NavArrowRight
        aria-hidden="true"
        width={16}
        height={16}
        style={{ flexShrink: 0, color: 'var(--gray-9)' }}
      />
    </>
  );

  const shared = {
    className: 'ds-card-bento ds-lift ds-quick-action',
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)',
      padding: 'var(--space-4)',
      textAlign: 'left' as const,
      textDecoration: 'none',
      width: '100%',
      cursor: 'pointer',
    },
  };

  const focusStyles = (
    <style jsx global>{`
      .ds-quick-action:hover {
        border-color: var(--accent-a6);
      }
      .ds-quick-action:focus-visible {
        outline: 2px solid var(--accent-8);
        outline-offset: 2px;
      }
    `}</style>
  );

  if (href) {
    return (
      <Link href={href} {...shared}>
        {body}
        {focusStyles}
      </Link>
    );
  }

  return (
    <button type="button" data-testid="ds-quick-action" onClick={onClick} {...shared}>
      {body}
      {focusStyles}
    </button>
  );
}

export default QuickActionCard;
