/**
 * Analytics GraphQL Domain (Admin App)
 *
 * Dashboard / action-center stats hooks backed by MongoDB aggregation in the
 * backend stats services. All counts come from federated GraphQL queries.
 */

export {
  usePendingCounts,
  type PendingCounts,
  type PendingCountKey,
  type UsePendingCountsResult,
} from './pending-counts.hooks';
export { PENDING_COUNTS } from './pending-counts.queries';
export {
  usePlatformSummary,
  type UsePlatformSummaryResult,
} from './platform-summary.hooks';
export { PLATFORM_SUMMARY } from './platform-summary.queries';
