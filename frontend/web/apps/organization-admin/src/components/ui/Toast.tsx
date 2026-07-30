'use client';

/**
 * Toast — MyTicketZM design system feedback primitive.
 *
 * Contract (spec §7): `variant, title, description, icon, onClose`
 *   variant: success | error | warning | info
 *
 * This file previously styled five variants with Tailwind default-palette
 * utility classes — 44 of them, which was the entire palette-class debt in this
 * app. Everything now resolves through the --status-* role tokens, so light/dark
 * and brand context are handled by the token layer instead of by a
 * hand-maintained dark-mode variant per class.
 *
 * Copy rule: errors explain what happened and how to fix it, in the interface's
 * voice. They do not apologise and they are never vague.
 */

import * as React from 'react';
import * as ToastPrimitive from '@radix-ui/react-toast';
import {
  CheckCircle,
  WarningCircle,
  WarningTriangle,
  InfoCircle,
  Xmark,
} from 'iconoir-react';

export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastProps {
  variant?: ToastVariant;
  title: string;
  description?: string;
  icon?: React.ReactNode;
  onClose?: () => void;
}

const VARIANTS: Record<
  ToastVariant,
  { surface: string; border: string; text: string; icon: React.ReactNode }
> = {
  success: {
    surface: 'var(--status-success-a3)',
    border: 'var(--green-a6)',
    text: 'var(--status-success-11)',
    icon: <CheckCircle width={20} height={20} />,
  },
  error: {
    surface: 'var(--status-danger-a3)',
    border: 'var(--red-a6)',
    text: 'var(--status-danger-11)',
    icon: <WarningCircle width={20} height={20} />,
  },
  warning: {
    surface: 'var(--status-warning-a3)',
    border: 'var(--amber-a6)',
    text: 'var(--status-warning-11)',
    icon: <WarningTriangle width={20} height={20} />,
  },
  info: {
    surface: 'var(--status-info-a3)',
    border: 'var(--blue-a6)',
    text: 'var(--status-info-11)',
    icon: <InfoCircle width={20} height={20} />,
  },
};

export function Toast({ variant = 'info', title, description, icon, onClose }: ToastProps) {
  const config = VARIANTS[variant];

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 'var(--space-3)',
        width: '100%',
        padding: 'var(--space-4)',
        background: 'var(--color-panel-solid)',
        borderRadius: 'var(--card-radius)',
        border: `1px solid ${config.border}`,
        boxShadow: 'var(--shadow-4)',
      }}
    >
      <span
        aria-hidden="true"
        style={{
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 28,
          height: 28,
          borderRadius: 'var(--radius-2)',
          background: config.surface,
          color: config.text,
        }}
      >
        {icon || config.icon}
      </span>

      <div style={{ flex: 1, minWidth: 0 }}>
        <ToastPrimitive.Title
          style={{
            display: 'block',
            fontSize: 'var(--text-2-size)',
            fontWeight: 'var(--weight-semibold)',
            color: 'var(--gray-12)',
          }}
        >
          {title}
        </ToastPrimitive.Title>
        {description && (
          <ToastPrimitive.Description
            style={{
              display: 'block',
              marginTop: 2,
              fontSize: 'var(--text-2-size)',
              lineHeight: 'var(--text-2-line)',
              color: 'var(--gray-11)',
            }}
          >
            {description}
          </ToastPrimitive.Description>
        )}
      </div>

      {onClose && (
        <ToastPrimitive.Close asChild>
          <button
            type="button"
            data-testid="toast-close"
            className="ds-toast-close"
            aria-label="Dismiss notification"
            onClick={onClose}
            style={{
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 24,
              height: 24,
              borderRadius: 'var(--radius-2)',
              border: 0,
              background: 'transparent',
              color: 'var(--gray-9)',
              cursor: 'pointer',
              transition:
                'background-color var(--transition-fast) var(--ease-standard), color var(--transition-fast) var(--ease-standard)',
            }}
          >
            <Xmark width={16} height={16} />
          </button>
        </ToastPrimitive.Close>
      )}

      <style jsx global>{`
        .ds-toast-close:hover {
          background: var(--gray-a3);
          color: var(--gray-12);
        }
        .ds-toast-close:focus-visible {
          outline: none;
          box-shadow: 0 0 0 2px var(--color-background), 0 0 0 4px var(--accent-8);
        }
      `}</style>
    </div>
  );
}

/**
 * Viewport — bottom-right on desktop, top on mobile so a toast never covers a
 * thumb-reachable primary action.
 */
export const ToastViewport = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Viewport>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitive.Viewport>
>((props, ref) => (
  <ToastPrimitive.Viewport
    ref={ref}
    className="ds-toast-viewport"
    style={{
      position: 'fixed',
      zIndex: 100,
      display: 'flex',
      flexDirection: 'column-reverse',
      gap: 'var(--space-3)',
      width: '100%',
      maxWidth: 420,
      maxHeight: '100vh',
      padding: 'var(--space-4)',
      listStyle: 'none',
      margin: 0,
      outline: 'none',
    }}
    {...props}
  />
));
ToastViewport.displayName = 'ToastViewport';

export const ToastRoot = ToastPrimitive.Root;
export const ToastAction = ToastPrimitive.Action;
export const ToastProvider = ToastPrimitive.Provider;

export interface ToastData {
  id: string;
  title: string;
  description?: string;
  variant?: ToastVariant;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}
