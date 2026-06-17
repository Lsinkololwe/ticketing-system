'use client';

/**
 * Toast Context and Hook
 *
 * Provides a simple API for showing toast notifications:
 *
 * ```tsx
 * const { toast } = useToast();
 *
 * // Success notification
 * toast.success('Changes saved successfully');
 *
 * // Error notification
 * toast.error('Failed to save changes', 'Please try again');
 *
 * // With action button
 * toast.info('New update available', undefined, {
 *   label: 'Refresh',
 *   onClick: () => window.location.reload()
 * });
 * ```
 */

import * as React from 'react';
import {
  Toast,
  ToastClose,
  ToastDescription,
  ToastIcon,
  ToastProvider as RadixToastProvider,
  ToastTitle,
  ToastViewport,
  ToastAction,
  type ToastData,
  type ToastVariant,
} from './Toast';

// Default duration for auto-dismiss (5 seconds)
const DEFAULT_DURATION = 5000;

// Toast context type
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

// Generate unique ID for each toast
let toastIdCounter = 0;
const generateId = () => `toast-${++toastIdCounter}-${Date.now()}`;

/**
 * Toast Provider Component
 *
 * Wrap your app with this provider to enable toast notifications.
 * Place at the root of your app, inside other providers.
 */
export function ToastContextProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = React.useState<ToastData[]>([]);

  // Add a new toast
  const addToast = React.useCallback((toast: Omit<ToastData, 'id'>): string => {
    const id = generateId();
    const newToast: ToastData = {
      ...toast,
      id,
      duration: toast.duration ?? DEFAULT_DURATION,
    };

    setToasts((prev) => [...prev, newToast]);
    return id;
  }, []);

  // Remove a toast by ID
  const removeToast = React.useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Convenience methods for different toast types
  const toast = React.useMemo(
    () => ({
      success: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'success', action }),

      error: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'error', action, duration: 8000 }), // Errors stay longer

      warning: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'warning', action }),

      info: (title: string, description?: string, action?: ToastData['action']) =>
        addToast({ title, description, variant: 'info', action }),

      custom: (data: Omit<ToastData, 'id'>) => addToast(data),
    }),
    [addToast]
  );

  const contextValue = React.useMemo(
    () => ({
      toasts,
      addToast,
      removeToast,
      toast,
    }),
    [toasts, addToast, removeToast, toast]
  );

  return (
    <ToastContext.Provider value={contextValue}>
      <RadixToastProvider swipeDirection="right">
        {children}

        {/* Render all active toasts */}
        {toasts.map((t) => (
          <Toast
            key={t.id}
            open={true}
            variant={t.variant}
            duration={t.duration}
            onOpenChange={(open) => {
              if (!open) removeToast(t.id);
            }}
          >
            <div className="flex items-start gap-3">
              <ToastIcon variant={t.variant} />
              <div className="flex-1 space-y-1">
                <ToastTitle>{t.title}</ToastTitle>
                {t.description && (
                  <ToastDescription>{t.description}</ToastDescription>
                )}
              </div>
            </div>
            {t.action && (
              <ToastAction altText={t.action.label} onClick={t.action.onClick}>
                {t.action.label}
              </ToastAction>
            )}
            <ToastClose />
          </Toast>
        ))}

        <ToastViewport />
      </RadixToastProvider>
    </ToastContext.Provider>
  );
}

/**
 * useToast Hook
 *
 * Access the toast context to show notifications.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { toast } = useToast();
 *
 *   const handleSave = async () => {
 *     try {
 *       await saveData();
 *       toast.success('Saved!', 'Your changes have been saved.');
 *     } catch (error) {
 *       toast.error('Save failed', error.message);
 *     }
 *   };
 * }
 * ```
 */
export function useToast(): ToastContextType {
  const context = React.useContext(ToastContext);

  if (!context) {
    throw new Error('useToast must be used within a ToastContextProvider');
  }

  return context;
}

/**
 * Standalone toast function for use outside React components
 *
 * Note: This requires the ToastContextProvider to be mounted.
 * For most cases, prefer using the useToast hook.
 */
export type { ToastData, ToastVariant };
