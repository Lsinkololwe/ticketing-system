'use client';

import type { ReactNode } from 'react';

/**
 * EmptyState — MyTicketZM Design System primitive.
 *
 * Contract (spec §7): icon, title, description, action, size.
 * `size: sm | md | lg`.
 *
 * Copy rule (§10): an empty screen is an invitation to act, not an apology.
 * Title states the situation in sentence case; description tells the customer
 * what to do next; `action` is the way out.
 */
export interface EmptyStateProps {
  icon?: ReactNode;
  title?: string;
  description?: string;
  action?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

const SIZE_SPEC = {
  sm: { pad: 'var(--space-6)', chip: 40, iconGap: 'var(--space-3)', title: 'var(--heading-3-size)' },
  md: { pad: 'var(--space-8)', chip: 56, iconGap: 'var(--space-4)', title: 'var(--heading-4-size)' },
  lg: { pad: 'var(--space-9)', chip: 72, iconGap: 'var(--space-5)', title: 'var(--heading-5-size)' },
} as const;

export function EmptyState({ icon, title, description, action, size = 'md' }: EmptyStateProps) {
  const s = SIZE_SPEC[size];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        padding: s.pad,
        gap: 'var(--space-2)',
      }}
    >
      {icon && (
        <span
          aria-hidden="true"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: s.chip,
            height: s.chip,
            marginBottom: s.iconGap,
            borderRadius: 'var(--card-radius-bento)',
            background: 'var(--gray-a3)',
            color: 'var(--gray-9)',
          }}
        >
          {icon}
        </span>
      )}
      {title && (
        <h3
          style={{
            margin: 0,
            fontFamily: 'var(--font-display)',
            fontSize: s.title,
            fontWeight: 'var(--weight-semibold)',
            color: 'var(--gray-12)',
            letterSpacing: '-0.01em',
          }}
        >
          {title}
        </h3>
      )}
      {description && (
        <p
          style={{
            margin: 0,
            maxWidth: '38ch',
            fontSize: 'var(--text-2-size)',
            lineHeight: 'var(--text-2-line)',
            color: 'var(--gray-11)',
          }}
        >
          {description}
        </p>
      )}
      {action && <div style={{ marginTop: 'var(--space-4)' }}>{action}</div>}
    </div>
  );
}

export default EmptyState;
