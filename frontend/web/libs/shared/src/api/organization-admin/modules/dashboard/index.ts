/**
 * Dashboard Module (Organization Admin App)
 *
 * Read-only organizer dashboard data: headline stats, upcoming events, recent activity.
 *
 * @example
 * ```tsx
 * import {
 *   useMyDashboardStats,
 *   useMyUpcomingEvents,
 *   useMyRecentActivity,
 * } from '@pml.tickets/shared/api/organization-admin/modules/dashboard';
 * ```
 */

export * from './dashboard.queries';
export * from './dashboard.hooks';
