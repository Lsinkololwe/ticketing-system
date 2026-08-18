/**
 * Organization Service - Organization Status and Routing
 *
 * Handles organization status queries via GraphQL and routing logic.
 * Uses React's cache() for request deduplication within a single render pass.
 *
 * @module OrganizationService
 */

import 'server-only';

import { cache } from 'react';
import { headers } from 'next/headers';
import { auth } from '../index';
import type {
  IOrganizationService,
  ISessionService,
  IAuthConfig,
  OrganizationStatus,
  RouteValidationResult,
} from '../interfaces';
import {
  NO_ORGANIZATION,
  parseStatus,
  routeForStatus,
  unknownState,
  type OnboardingState,
} from '../../onboarding/state';

// =============================================================================
// GRAPHQL TYPES
// =============================================================================

/**
 * GraphQL query response structure
 */
interface GraphQLResponse {
  data?: {
    myOwnedOrganization?: {
      id: string;
      name: string;
      status: string;
    } | null;
  };
  errors?: Array<{ message: string }>;
}

/** Render an unknown thrown value as a short log-safe string. */
function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

// =============================================================================
// ORGANIZATION SERVICE IMPLEMENTATION
// =============================================================================

/**
 * Organization management service
 *
 * Provides cached methods for organization status queries and routing logic.
 * Integrates with GraphQL backend for organization data.
 *
 * @example
 * ```typescript
 * const orgService = new OrganizationService(sessionService, config);
 * const status = await orgService.getStatus();
 * const route = orgService.getRouteForStatus(status.status);
 * ```
 */
export class OrganizationService implements IOrganizationService {
  /**
   * Creates a new OrganizationService instance
   *
   * @param sessionService - Session service for authentication
   * @param config - Auth configuration for GraphQL endpoint
   */
  constructor(
    private readonly sessionService: ISessionService,
    private readonly config: IAuthConfig
  ) {}

  /**
   * Get the current user's organization status
   *
   * Makes a server-side GraphQL request to check organization status.
   * Uses the session token from cookies for authentication.
   *
   * Uses React's cache() to deduplicate calls within a single request.
   *
   * @returns Organization status information
   *
   * @example
   * ```typescript
   * // In Server Component
   * export default async function ApplyLayout({ children }) {
   *   const orgStatus = await orgService.getStatus();
   *
   *   if (orgStatus.isApproved) {
   *     redirect('/dashboard');
   *   }
   *
   *   return <>{children}</>;
   * }
   * ```
   */
  /**
   * Default "no organization" status
   */
  private readonly noOrganization: OrganizationStatus = {
    hasOrganization: false,
    id: null,
    name: null,
    status: null,
    isApproved: false,
    isPendingReview: false,
    needsChanges: false,
    isRejected: false,
    isDraft: false,
  };

  /**
   * Resolve the caller's onboarding state.
   *
   * Every failure path returns `unknown`, never `none`. That asymmetry is the
   * whole point: "we could not reach the backend" and "this person has not
   * applied yet" are different facts, and conflating them sent applicants whose
   * submission was already under review back to an empty form.
   *
   * Cached per request via React `cache()` so the layout, the page and any
   * nested guard share one round-trip.
   */
  getOnboardingState = cache(async (): Promise<OnboardingState> => {
    // A missing session is genuinely "not authenticated" rather than a failure
    // to determine status — the caller's session guard redirects to /login
    // before this matters.
    let session: Awaited<ReturnType<ISessionService['getSession']>>;
    try {
      session = await this.sessionService.getSession();
    } catch (error) {
      return unknownState(`session lookup failed: ${describe(error)}`);
    }
    if (!session) {
      return unknownState('no session');
    }

    // The Keycloak access token is REQUIRED. `myOwnedOrganization` is guarded by
    // hasRole('ORGANIZER'), so an unauthenticated query does not return "no
    // organization" — it returns an authorization error. Previously this call
    // logged a warning and carried on tokenless, turning a token-refresh blip
    // into a bogus "you have no application".
    let accessToken: string | null = null;
    try {
      const requestHeaders = await headers();
      const tokenResponse = await auth.api.getAccessToken({
        body: { providerId: 'keycloak' },
        headers: requestHeaders,
      });
      accessToken = tokenResponse?.accessToken ?? null;
    } catch (error) {
      return unknownState(`access token unavailable: ${describe(error)}`);
    }
    if (!accessToken) {
      return unknownState('access token unavailable');
    }

    let payload: GraphQLResponse;
    try {
      const response = await fetch(this.config.graphqlEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          query: `
            query MyOrganizationStatus {
              myOwnedOrganization {
                id
                name
                status
              }
            }
          `,
        }),
        cache: 'no-store', // Always fetch fresh data for security
      });

      if (!response.ok) {
        return unknownState(`graphql http ${response.status}`);
      }

      payload = (await response.json()) as GraphQLResponse;
    } catch (error) {
      return unknownState(`graphql request failed: ${describe(error)}`);
    }

    if (payload.errors?.length) {
      return unknownState(
        `graphql errors: ${payload.errors.map((e) => e.message).join('; ')}`
      );
    }

    const org = payload.data?.myOwnedOrganization;

    // `data.myOwnedOrganization === null` with no errors is the one genuine
    // "this user has not applied yet". Note we require the `data` key to be
    // present — a malformed body without it is a failure, not an absence.
    if (!('data' in payload) || payload.data === undefined) {
      return unknownState('graphql response had no data key');
    }
    if (!org) {
      return NO_ORGANIZATION;
    }

    const status = parseStatus(org.status);
    if (!status) {
      // A status we do not know how to route. Refusing to guess is safer than
      // defaulting to the form.
      return unknownState(`unrecognised status: ${org.status}`);
    }

    return { kind: 'org', status, id: org.id, name: org.name ?? null };
  });

  /**
   * Legacy status shape, derived from {@link getOnboardingState}.
   *
   * Retained for callers that only need the lifecycle booleans. Note that an
   * `unknown` state still reports `hasOrganization: false` here — which is why
   * routing decisions must use `getOnboardingState()` and not this method.
   */
  getStatus = cache(async (): Promise<OrganizationStatus> => {
    const state = await this.getOnboardingState();

    if (state.kind !== 'org') {
      if (state.kind === 'unknown') {
        console.error('[OrganizationService] status unresolved:', state.reason);
      }
      return this.noOrganization;
    }

    const { status } = state;
    return {
      hasOrganization: true,
      id: state.id,
      name: state.name,
      status,
      isApproved: status === 'APPROVED' || status === 'ACTIVE',
      isPendingReview: status === 'PENDING_REVIEW',
      needsChanges: status === 'CHANGES_REQUESTED',
      isRejected: status === 'REJECTED',
      isDraft: status === 'DRAFT',
    };
  });

  /**
   * Get route for organization status
   *
   * Determines the appropriate route based on organization status.
   * This ensures users are redirected to the correct page based on their
   * application state.
   *
   * @param status - Organization status string
   * @returns Route path
   *
   * @example
   * ```typescript
   * const orgStatus = await orgService.getStatus();
   * const route = orgService.getRouteForStatus(orgStatus.status);
   * redirect(route);
   * ```
   */
  getRouteForStatus(status: string | null): string {
    // Delegates to the canonical map so this app has exactly one status → route
    // table. The previous inline `switch` had no case for PENDING_DOCUMENTS,
    // INACTIVE or PENDING_DELETION and fell through to '/welcome' — the setup
    // screen — for all three.
    const parsed = parseStatus(status);
    if (!parsed) return '/welcome';
    return routeForStatus(parsed);
  }

  /**
   * Check if organization is approved
   *
   * @returns True if organization status is APPROVED or ACTIVE
   *
   * @example
   * ```typescript
   * const isApproved = await orgService.isApproved();
   * if (isApproved) {
   *   // Allow dashboard access
   * }
   * ```
   */
  async isApproved(): Promise<boolean> {
    const orgStatus = await this.getStatus();
    return orgStatus.isApproved;
  }

  /**
   * Validate route access based on organization status
   *
   * Determines if a user can access a specific route based on their
   * organization application status.
   *
   * @param requestedPath - Path user is trying to access
   * @returns Validation result with redirect path if needed
   *
   * @example
   * ```typescript
   * const validation = await orgService.validateRouteAccess('/dashboard');
   * if (!validation.allowed) {
   *   redirect(validation.redirectTo);
   * }
   * ```
   */
  async validateRouteAccess(
    requestedPath: string
  ): Promise<RouteValidationResult> {
    const orgStatus = await this.getStatus();
    const currentStatus = orgStatus.status;
    const expectedRoute = this.getRouteForStatus(currentStatus);

    // If user is trying to access expected route, allow it
    if (requestedPath === expectedRoute) {
      return {
        allowed: true,
        currentStatus,
      };
    }

    // Special case: dashboard is available to operational orgs (approved/active)
    // and to those under review (read-only preview) — both are business lifecycle states.
    if (requestedPath.startsWith('/dashboard')) {
      if (orgStatus.isApproved || orgStatus.isPendingReview) {
        return {
          allowed: true,
          currentStatus,
        };
      }

      return {
        allowed: false,
        redirectTo: expectedRoute,
        currentStatus,
        reason: 'Organization cannot access dashboard in current status',
      };
    }

    // Special case: Allow access to application flow if not approved
    if (requestedPath.startsWith('/apply')) {
      if (orgStatus.isApproved) {
        return {
          allowed: false,
          redirectTo: '/dashboard',
          currentStatus,
          reason: 'Organization already approved',
        };
      }

      return {
        allowed: true,
        currentStatus,
      };
    }

    // For other routes, redirect to expected route
    return {
      allowed: false,
      redirectTo: expectedRoute,
      currentStatus,
      reason: 'Invalid route for current status',
    };
  }
}
