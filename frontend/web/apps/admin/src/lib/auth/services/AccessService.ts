/**
 * Access (Authorization) Service — admin role gating.
 *
 * The admin equivalent of organization-admin's OrganizationService: it authorizes
 * on PLATFORM ROLES (ADMIN / SUPER_ADMIN / FINANCE / SCANNER) rather than on
 * organization status. Roles come from the Better Auth session
 * (`session.user.roles`), seeded from the Keycloak `realm_access.roles` claim by
 * the shared `mapProfileToUser`.
 *
 * @module auth/services/AccessService
 */

import 'server-only';

import { redirect } from 'next/navigation';
import type {
  IAccessService,
  ISessionService,
  Session,
  AccessCheckResult,
  AdminRole,
} from '../interfaces';
import { ADMIN_DASHBOARD_ROLES } from '../interfaces';

export class AccessService implements IAccessService {
  constructor(private readonly sessionService: ISessionService) {}

  /** Normalised (upper-case) roles from the session. */
  getRoles(session: Session): readonly string[] {
    const roles = (session.user as { roles?: string[] }).roles ?? [];
    return roles.map((r) => String(r).toUpperCase());
  }

  hasAnyRole(session: Session, allowed: readonly AdminRole[]): boolean {
    const roles = this.getRoles(session);
    return allowed.some((r) => roles.includes(r.toUpperCase()));
  }

  checkAccess(session: Session, allowed: readonly AdminRole[]): AccessCheckResult {
    const roles = this.getRoles(session);
    return {
      allowed: allowed.some((r) => roles.includes(r.toUpperCase())),
      roles,
      required: allowed,
    };
  }

  /**
   * Verify the session, then require at least one allowed role.
   * Redirects to /login (no session) or /unauthorized (wrong role).
   */
  async requireRoles(
    allowed: readonly AdminRole[] = ADMIN_DASHBOARD_ROLES
  ): Promise<Session> {
    const session = await this.sessionService.verifySession();
    if (!this.hasAnyRole(session, allowed)) {
      redirect('/unauthorized');
    }
    return session;
  }
}
