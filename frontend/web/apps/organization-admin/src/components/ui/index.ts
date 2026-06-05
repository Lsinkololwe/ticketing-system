/**
 * UI Components Index
 *
 * Re-export all UI components for convenient imports:
 * import { PageHeader, StyledCard, StatCard, EmptyState } from '@/components/ui';
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
