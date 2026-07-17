import 'server-only';
import type { Session, AccessCheckResult, AdminRole } from './types';

/**
 * Access (authorization) service interface.
 *
 * The admin equivalent of organization-admin's OrganizationService: instead of
 * routing on organization status, the admin app authorizes on PLATFORM ROLES.
 * Roles come from the Better Auth session (`session.user.roles`), seeded from the
 * Keycloak `realm_access.roles` claim.
 */
export interface IAccessService {
  /** The user's normalised (upper-case) roles for a given session. */
  getRoles(session: Session): readonly string[];

  /** Whether a session holds at least one of the allowed roles. */
  hasAnyRole(session: Session, allowed: readonly AdminRole[]): boolean;

  /** Evaluate access without redirecting (for UI / conditional logic). */
  checkAccess(session: Session, allowed: readonly AdminRole[]): AccessCheckResult;

  /**
   * Require at least one of the allowed roles. Verifies the session first
   * (redirect to /login if absent), then redirects to /unauthorized if the user
   * lacks every allowed role. Returns the verified session on success.
   */
  requireRoles(allowed?: readonly AdminRole[]): Promise<Session>;
}
