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

  getStatus = cache(async (): Promise<OrganizationStatus> => {
    try {
      // First verify we have a valid session
      const session = await this.sessionService.getSession();
      if (!session) {
        return this.noOrganization;
      }

      // Get request headers for Better Auth API
      const requestHeaders = await headers();

      // Get access token from Better Auth using server-side API
      // This retrieves the Keycloak access token stored in the accounts collection
      let accessToken: string | null = null;
      try {
        const tokenResponse = await auth.api.getAccessToken({
          body: { providerId: 'keycloak' },
          headers: requestHeaders,
        });
        accessToken = tokenResponse?.accessToken ?? null;
      } catch (tokenError) {
        console.warn('[OrganizationService] Failed to get access token:', tokenError);
        // Continue without token - may still work for public queries
      }

      // Build request headers with Authorization if token available
      const graphqlHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
      };

      if (accessToken) {
        graphqlHeaders['Authorization'] = `Bearer ${accessToken}`;
      }

      // Query organization status via GraphQL
      const response = await fetch(this.config.graphqlEndpoint, {
        method: 'POST',
        headers: graphqlHeaders,
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
        console.error(
          '[OrganizationService] GraphQL request failed:',
          response.status
        );
        return this.noOrganization;
      }

      const data: GraphQLResponse = await response.json();

      // Handle GraphQL errors
      if (data.errors) {
        console.error('[OrganizationService] GraphQL errors:', data.errors);
        return this.noOrganization;
      }

      const org = data.data?.myOwnedOrganization;

      if (!org) {
        return this.noOrganization;
      }

      const status = org.status;

      // Return OrganizationStatus with computed boolean properties
      return {
        hasOrganization: true,
        id: org.id,
        name: org.name,
        status,
        isApproved: status === 'APPROVED' || status === 'ACTIVE',
        isPendingReview: status === 'PENDING_REVIEW',
        needsChanges: status === 'CHANGES_REQUESTED',
        isRejected: status === 'REJECTED',
        isDraft: status === 'DRAFT',
      };
    } catch (error) {
      console.error(
        '[OrganizationService] Failed to get organization status:',
        error
      );
      return this.noOrganization;
    }
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
    if (!status) return '/welcome';

    switch (status) {
      case 'DRAFT':
      case 'CHANGES_REQUESTED':
        return '/apply/business-info';
      case 'PENDING_REVIEW':
      case 'REJECTED':
      case 'SUSPENDED':
        return '/apply/status';
      case 'APPROVED':
      case 'ACTIVE':
        return '/dashboard';
      default:
        return '/welcome';
    }
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

    // Special case: Allow access to dashboard only if approved
    if (requestedPath.startsWith('/dashboard')) {
      if (orgStatus.isApproved) {
        return {
          allowed: true,
          currentStatus,
        };
      }

      return {
        allowed: false,
        redirectTo: expectedRoute,
        currentStatus,
        reason: 'Organization not approved',
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
