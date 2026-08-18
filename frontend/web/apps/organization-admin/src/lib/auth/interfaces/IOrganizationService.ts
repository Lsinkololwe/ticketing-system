import 'server-only';
import type { OrganizationStatus } from './types';
import type { OnboardingState } from '../../onboarding/state';

/**
 * Organization service interface for managing organization application state.
 *
 * This service handles fetching organization status from the GraphQL backend
 * and determining appropriate routing based on application state.
 *
 * @example
 * ```typescript
 * const orgService: IOrganizationService = new OrganizationService();
 * const status = await orgService.getStatus();
 * const route = orgService.getRouteForStatus(status.status);
 * ```
 */
export interface IOrganizationService {
  /**
   * Fetches the current organization application status from the backend.
   *
   * This method queries the GraphQL API to retrieve the authenticated user's
   * organization application status, including approval state, application ID,
   * and any rejection reasons.
   *
   * @returns Promise resolving to organization status data
   * @throws {Error} When GraphQL query fails or user is not authenticated
   *
   * @example
   * ```typescript
   * const status = await orgService.getStatus();
   * console.log('Application status:', status.status);
   * console.log('Is approved:', status.isApproved);
   * ```
   */
  getStatus(): Promise<OrganizationStatus>;

  /**
   * Resolves the caller's onboarding state for routing.
   *
   * Unlike {@link getStatus}, this NEVER collapses a failed lookup into "no
   * organization". A backend outage, a token-refresh failure or an unrecognised
   * status value all produce `{ kind: 'unknown' }`, which routes to a retry
   * screen rather than to the empty setup form.
   *
   * This distinction is the fix for the defect where a user whose application
   * was submitted and awaiting review was returned to the application form on
   * every login.
   *
   * @returns Promise resolving to a three-valued onboarding state
   *
   * @example
   * ```typescript
   * const state = await orgService.getOnboardingState();
   * const target = redirectFor(state, '/welcome');
   * if (target) redirect(target);
   * ```
   */
  getOnboardingState(): Promise<OnboardingState>;

  /**
   * Determines the appropriate route path based on organization status.
   *
   * This method maps application statuses to their corresponding routes
   * in the application flow (e.g., DRAFT -> /apply/business-info).
   *
   * @param status - Current organization status string or null
   * @returns Route path to redirect to
   *
   * @example
   * ```typescript
   * const route = orgService.getRouteForStatus('DRAFT');
   * // Returns: '/apply/business-info'
   *
   * const approvedRoute = orgService.getRouteForStatus('APPROVED');
   * // Returns: '/dashboard'
   * ```
   */
  getRouteForStatus(status: string | null): string;

  /**
   * Checks if the organization application is complete and approved.
   *
   * @returns Promise resolving to true if organization is approved
   *
   * @example
   * ```typescript
   * const isApproved = await orgService.isApproved();
   * if (isApproved) {
   *   // Show dashboard
   * } else {
   *   // Show application flow
   * }
   * ```
   */
  isApproved(): Promise<boolean>;

  /**
   * Validates if the user can access a specific route based on their status.
   *
   * @param requestedPath - Path the user is trying to access
   * @returns Promise resolving to validation result with redirect path if needed
   *
   * @example
   * ```typescript
   * const validation = await orgService.validateRouteAccess('/dashboard');
   * if (!validation.allowed) {
   *   redirect(validation.redirectTo);
   * }
   * ```
   */
  validateRouteAccess(requestedPath: string): Promise<RouteValidationResult>;
}

/**
 * Result of route access validation.
 */
export interface RouteValidationResult {
  /** Whether the user is allowed to access the requested route */
  readonly allowed: boolean;
  /** Route to redirect to if access is denied */
  readonly redirectTo?: string;
  /** Current organization status string */
  readonly currentStatus: string | null;
  /** Reason for denial if applicable */
  readonly reason?: string;
}
