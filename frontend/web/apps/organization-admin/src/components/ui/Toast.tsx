'use client';

/**
 * Professional Toast Notification System using Radix UI
 *
 * Features:
 * - Four variants: success, error, warning, info
 * - Accessible (ARIA compliant)
 * - Auto-dismiss with configurable duration
 * - Manual dismiss with close button
 * - Smooth animations
 * - Dark mode support
 */

import * as React from 'react';
import * as ToastPrimitive from '@radix-ui/react-toast';
import { cva, type VariantProps } from 'class-variance-authority';
import {
  CheckCircledIcon,
  CrossCircledIcon,
  ExclamationTriangleIcon,
  InfoCircledIcon,
  Cross2Icon,
} from '@radix-ui/react-icons';

// Toast variant styles using CVA for type-safe variants
const toastVariants = cva(
  [
    'group pointer-events-auto relative flex w-full items-center justify-between space-x-4 overflow-hidden rounded-lg border p-4 pr-8 shadow-lg transition-all',
    'data-[swipe=cancel]:translate-x-0 data-[swipe=end]:translate-x-[var(--radix-toast-swipe-end-x)] data-[swipe=move]:translate-x-[var(--radix-toast-swipe-move-x)] data-[swipe=move]:transition-none',
    'data-[state=open]:animate-in data-[state=closed]:animate-out data-[swipe=end]:animate-out data-[state=closed]:fade-out-80 data-[state=closed]:slide-out-to-right-full data-[state=open]:slide-in-from-top-full data-[state=open]:sm:slide-in-from-bottom-full',
  ],
  {
    variants: {
      variant: {
        success: 'border-green-200 bg-green-50 text-green-900 dark:border-green-800 dark:bg-green-950 dark:text-green-100',
        error: 'border-red-200 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950 dark:text-red-100',
        warning: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100',
        info: 'border-blue-200 bg-blue-50 text-blue-900 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-100',
        default: 'border-gray-200 bg-white text-gray-900 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-100',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
);

// Icon component based on variant
const ToastIcon = ({ variant }: { variant?: 'success' | 'error' | 'warning' | 'info' | 'default' }) => {
  const iconClass = 'h-5 w-5 flex-shrink-0';

  switch (variant) {
    case 'success':
      return <CheckCircledIcon className={`${iconClass} text-green-600 dark:text-green-400`} />;
    case 'error':
      return <CrossCircledIcon className={`${iconClass} text-red-600 dark:text-red-400`} />;
    case 'warning':
      return <ExclamationTriangleIcon className={`${iconClass} text-amber-600 dark:text-amber-400`} />;
    case 'info':
      return <InfoCircledIcon className={`${iconClass} text-blue-600 dark:text-blue-400`} />;
    default:
      return null;
  }
};

// Toast viewport (where toasts are rendered)
export const ToastViewport = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Viewport>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitive.Viewport>
>(({ className, ...props }, ref) => (
  <ToastPrimitive.Viewport
    ref={ref}
    className={`fixed top-0 z-[100] flex max-h-screen w-full flex-col-reverse p-4 sm:bottom-0 sm:right-0 sm:top-auto sm:flex-col md:max-w-[420px] ${className || ''}`}
    {...props}
  />
));
ToastViewport.displayName = ToastPrimitive.Viewport.displayName;

// Main Toast component
export interface ToastProps
  extends React.ComponentPropsWithoutRef<typeof ToastPrimitive.Root>,
    VariantProps<typeof toastVariants> {}

export const Toast = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Root>,
  ToastProps
>(({ className, variant, ...props }, ref) => (
  <ToastPrimitive.Root
    ref={ref}
    className={toastVariants({ variant, className })}
    {...props}
  />
));
Toast.displayName = ToastPrimitive.Root.displayName;

// Toast action button
export const ToastAction = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Action>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitive.Action>
>(({ className, ...props }, ref) => (
  <ToastPrimitive.Action
    ref={ref}
    className={`inline-flex h-8 shrink-0 items-center justify-center rounded-md border bg-transparent px-3 text-sm font-medium transition-colors hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 dark:hover:bg-gray-800 ${className || ''}`}
    {...props}
  />
));
ToastAction.displayName = ToastPrimitive.Action.displayName;

// Toast close button
export const ToastClose = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Close>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitive.Close>
>(({ className, ...props }, ref) => (
  <ToastPrimitive.Close
    ref={ref}
    className={`absolute right-2 top-2 rounded-md p-1 text-gray-500 opacity-0 transition-opacity hover:text-gray-900 focus:opacity-100 focus:outline-none focus:ring-2 group-hover:opacity-100 dark:text-gray-400 dark:hover:text-gray-100 ${className || ''}`}
    toast-close=""
    {...props}
  >
    <Cross2Icon className="h-4 w-4" />
  </ToastPrimitive.Close>
));
ToastClose.displayName = ToastPrimitive.Close.displayName;

// Toast title
export const ToastTitle = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitive.Title>
>(({ className, ...props }, ref) => (
  <ToastPrimitive.Title
    ref={ref}
    className={`text-sm font-semibold ${className || ''}`}
    {...props}
  />
));
ToastTitle.displayName = ToastPrimitive.Title.displayName;

// Toast description
export const ToastDescription = React.forwardRef<
  React.ElementRef<typeof ToastPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof ToastPrimitive.Description>
>(({ className, ...props }, ref) => (
  <ToastPrimitive.Description
    ref={ref}
    className={`text-sm opacity-90 ${className || ''}`}
    {...props}
  />
));
ToastDescription.displayName = ToastPrimitive.Description.displayName;

// Toast Provider (re-export)
export const ToastProvider = ToastPrimitive.Provider;

// Export the icon component for use in the toast context
export { ToastIcon };

// Type for toast data
export type ToastVariant = 'success' | 'error' | 'warning' | 'info' | 'default';

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
