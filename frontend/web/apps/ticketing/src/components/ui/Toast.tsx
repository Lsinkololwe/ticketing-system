'use client';

import type { ReactNode } from 'react';
import { CheckCircle, InfoCircle, WarningCircle, WarningTriangle, Xmark } from 'iconoir-react';

/**
 * Toast — MyTicketZM Design System feedback primitive.
 *
 * Contract (spec §7): variant, title, description, icon, onClose.
 * `variant: success | error | warning | info`.
 *
 * Uses the shared status roles only — a toast is never money-jade or
 * brand-iris, so "paid" feedback still reads as a status, and the commerce
 * colour stays reserved for amounts.
 *
 * Copy rule (§10): errors explain what happened and how to fix it. They do not
 * apologise and are never vague.
 */
export interface ToastProps {
  variant?: 'success' | 'error' | 'warning' | 'info';
  title?: string;
  description?: string;
  icon?: ReactNode;
  onClose?: () => void;
}

const VARIANT_SPEC = {
  success: { text: 'var(--status-success-11)', surface: 'var(--status-success-a3)', border: 'var(--green-a6)', Icon: CheckCircle },
  error: { text: 'var(--status-danger-11)', surface: 'var(--status-danger-a3)', border: 'var(--red-a6)', Icon: WarningCircle },
  warning: { text: 'var(--status-warning-11)', surface: 'var(--status-warning-a3)', border: 'var(--amber-a6)', Icon: WarningTriangle },
  info: { text: 'var(--status-info-11)', surface: 'var(--status-info-a3)', border: 'var(--blue-a6)', Icon: InfoCircle },
} as const;

export function Toast({ variant = 'info', title, description, icon, onClose }: ToastProps) {
  const v = VARIANT_SPEC[variant];
  const FallbackIcon = v.Icon;

  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 'var(--space-3)',
        maxWidth: 420,
        padding: 'var(--space-3) var(--space-4)',
        background: 'var(--card-bg)',
        border: `1px solid ${v.border}`,
        borderRadius: 'var(--card-radius)',
        boxShadow: 'var(--shadow-4)',
      }}
    >
      <span
        aria-hidden="true"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 28,
          height: 28,
          flexShrink: 0,
          borderRadius: 'var(--radius-3)',
          background: v.surface,
          color: v.text,
        }}
      >
        {icon ?? <FallbackIcon width={16} height={16} />}
      </span>

      <div style={{ flex: 1, minWidth: 0 }}>
        {title && (
          <div
            style={{
              fontSize: 'var(--text-2-size)',
              fontWeight: 'var(--weight-semibold)',
              color: 'var(--gray-12)',
            }}
          >
            {title}
          </div>
        )}
        {description && (
          <div
            style={{
              marginTop: 2,
              fontSize: 'var(--text-2-size)',
              lineHeight: 'var(--text-2-line)',
              color: 'var(--gray-11)',
            }}
          >
            {description}
          </div>
        )}
      </div>

      {onClose && (
        <button
          type="button"
          data-testid="ds-toast-close"
          onClick={onClose}
          aria-label="Dismiss"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 24,
            height: 24,
            flexShrink: 0,
            padding: 0,
            border: 'none',
            borderRadius: 'var(--radius-3)',
            background: 'transparent',
            color: 'var(--gray-9)',
            cursor: 'pointer',
          }}
        >
          <Xmark width={14} height={14} />
        </button>
      )}
    </div>
  );
}

export default Toast;
