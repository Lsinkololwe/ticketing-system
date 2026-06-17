/**
 * UI Components Index
 *
 * Re-export all UI components for convenient imports:
 * import { PageHeader, StyledCard, StatCard, EmptyState, useToast } from '@/components/ui';
 */

export { PageHeader } from './PageHeader';
export type { Breadcrumb, PageAction } from './PageHeader';

export { StyledCard } from './StyledCard';

export { StatCard, StatGrid } from './StatCard';

export {
  EmptyState,
  NoEventsEmptyState,
  NoTeamMembersEmptyState,
  NoTransactionsEmptyState,
  NoSearchResultsEmptyState,
  NoPayoutsEmptyState,
  NoBankAccountsEmptyState,
  NoAttendeesEmptyState,
  NoAnalyticsEmptyState,
} from './EmptyState';

// Toast Notifications
export {
  Toast,
  ToastProvider,
  ToastViewport,
  ToastTitle,
  ToastDescription,
  ToastClose,
  ToastAction,
} from './Toast';
export type { ToastData, ToastVariant, ToastProps } from './Toast';

export { ToastContextProvider, useToast } from './useToast';
