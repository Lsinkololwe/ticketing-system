'use client';

/**
 * Toast context and hook.
 *
 * ```tsx
 * const { toast } = useToast();
 * toast.success('Event published');
 * toast.error('Payout request failed', 'Add a payout destination first.');
 * ```
 *
 * The Radix Toast primitives (Provider / Root / Viewport / Action) are plumbing
 * and live here. `<Toast>` itself is the design-system component and keeps to
 * its declared contract: variant, title, description, icon, onClose.
 */

import * as React from 'react';
import {
  Toast,
  ToastRoot,
  ToastAction,
  ToastProvider as RadixToastProvider,
  ToastViewport,
  type ToastData,
  type ToastVariant,
} from './Toast';

const DEFAULT_DURATION = 5000;
/** Errors linger — the reader usually has to act on them. */
const ERROR_DURATION = 8000;

interface ToastContextType {
  toasts: ToastData[];
  addToast: (toast: Omit<ToastData, 'id'>) => string;
  removeToast: (id: string) => void;
  toast: {
    success: (title: string, description?: string, action?: ToastData['action']) => string;
    error: (title: string, description?: string, action?: ToastData['action']) => string;
    warning: (title: string, description?: string, action?: ToastData['action']) => string;
    info: (title: string, description?: string, action?: ToastData['action']) => string;
    custom: (data: Omit<ToastData, 'id'>) => string;
  };
}

const ToastContext = React.createContext<ToastContextType | null>(null);

let toastIdCounter = 0;
const generateId = () => `toast-${++toastIdCounter}-${Date.now()}`;

export function ToastContextProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = React.useState<ToastData[]>([]);

  const addToast = React.useCallback((next: Omit<ToastData, 'id'>): string => {
    const id = generateId();
    setToasts((prev) => [...prev, { ...next, id, duration: next.duration ?? DEFAULT_DURATION }]);
    return id;
  }, []);

  const removeToast = React.useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = React.useMemo(
    () => ({
      success: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'success', action }),

      error: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'error', action, duration: ERROR_DURATION }),

      warning: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'warning', action }),

      info: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'info', action }),

      custom: (data: Omit<ToastData, 'id'>) => addToast(data),
    }),
    [addToast]
  );

  const contextValue = React.useMemo(
    () => ({ toasts, addToast, removeToast, toast }),
    [toasts, addToast, removeToast, toast]
  );

  return (
    <ToastContext.Provider value={contextValue}>
      <RadixToastProvider swipeDirection="right">
        {children}

        {toasts.map((t) => (
          <ToastRoot
            key={t.id}
            open
            duration={t.duration}
            onOpenChange={(open) => {
              if (!open) removeToast(t.id);
            }}
            asChild
          >
            <div className="ds-toast-item">
              <Toast
                variant={t.variant}
                title={t.title}
                description={t.description}
                onClose={() => removeToast(t.id)}
              />
              {t.action && (
                <ToastAction asChild altText={t.action.label}>
                  <button
                    type="button"
                    data-testid="toast-action"
                    className="ds-toast-action"
                    onClick={t.action.onClick}
                  >
                    {t.action.label}
                  </button>
                </ToastAction>
              )}
            </div>
          </ToastRoot>
        ))}

        <ToastViewport />
      </RadixToastProvider>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextType {
  const context = React.useContext(ToastContext);

  if (!context) {
    throw new Error('useToast must be used within a ToastContextProvider');
  }

  return context;
}

export type { ToastData, ToastVariant };
