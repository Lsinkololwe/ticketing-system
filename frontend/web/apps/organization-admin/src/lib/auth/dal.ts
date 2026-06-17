/**
 * Data Access Layer (DAL) - Server-Side Authentication & Authorization
 *
 * SERVER-ONLY: This module provides secure session verification for Server Components.
 *
 * ## Next.js DAL Pattern
 *
 * @see https://nextjs.org/docs/app/guides/authentication#creating-a-data-access-layer-dal
 *
 * This module serves as a thin facade over the auth service layer, providing
 * backward compatibility with existing code while delegating to the DI container.
 *
 * @example
 * ```tsx
 * import { verifySession, getOrganizationStatus } from '@/lib/auth/dal';
 *
 * export default async function DashboardLayout({ children }) {
 *   const session = await verifySession(); // Redirects if no session
 *   return <>{children}</>;
 * }
 * ```
 */

import 'server-only';

import { getSessionService, getOrganizationService } from './container';

// =============================================================================
// TYPE EXPORTS
// =============================================================================

/** Session type inferred from Better Auth instance */
export type { Session, User } from './interfaces';

/**
 * Organization status for routing decisions
 */
export type { OrganizationStatus } from './interfaces';

// =============================================================================
// SESSION OPERATIONS (Delegated to SessionService)
// =============================================================================

/**
 * Verify the current user's session
 *
 * Delegates to SessionService for implementation.
 * Uses React's cache() internally to deduplicate calls within a single request.
 *
 * SECURITY: This validates the session against the database, not just the cookie.
 *
 * @returns Better Auth session with user data
 * @throws Redirects to /login if not authenticated
 */
export const verifySession = () => getSessionService().verifySession();

/**
 * Get session without redirecting
 *
 * Delegates to SessionService for implementation.
 * Returns null if not authenticated. Use this when you need to check
 * auth status without forcing a redirect.
 *
 * @returns Better Auth session or null
 */
export const getSession = () => getSessionService().getSession();

// =============================================================================
// ORGANIZATION OPERATIONS (Delegated to OrganizationService)
// =============================================================================

/**
 * Get the current user's organization status
 *
 * Delegates to OrganizationService for implementation.
 * Makes a server-side GraphQL request to check organization status.
 * Uses the session token from cookies for authentication.
 *
 * @returns Organization status information
 */
export const getOrganizationStatus = () => getOrganizationService().getStatus();

// =============================================================================
// ROUTING HELPERS (Delegated to OrganizationService)
// =============================================================================

/**
 * Get route for organization status
 *
 * Delegates to OrganizationService for implementation.
 * Determines the appropriate route based on organization status.
 *
 * @param status - Organization status
 * @returns Route path
 */
export const getRouteForStatus = (status: string | null): string =>
  getOrganizationService().getRouteForStatus(status);
