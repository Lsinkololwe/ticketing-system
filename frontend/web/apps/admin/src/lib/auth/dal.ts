/**
 * Data Access Layer (DAL) — Server-Side Authentication & Authorization
 *
 * SERVER-ONLY thin facade over the auth service layer (SessionService /
 * AccessService) via the DI container — mirrors the organization-admin DAL.
 *
 * The admin realm split (myticketzm-admin) already prevents non-staff from
 * obtaining an admin token; this DAL is the SECOND layer (defense in depth):
 * it validates the Better Auth session against the DB and enforces platform roles
 * before any admin route renders.
 *
 * @see https://nextjs.org/docs/app/guides/authentication#creating-a-data-access-layer-dal
 */

import 'server-only';

import { getSessionService, getAccessService } from './container';
import { ADMIN_DASHBOARD_ROLES } from './interfaces';
import type { AdminRole } from './interfaces';

export type { Session, User } from './interfaces';
export { ADMIN_DASHBOARD_ROLES };

/**
 * Verify the current session (validated against the DB, not just the cookie).
 * Redirects to /login when there is no valid session.
 */
export const verifySession = () => getSessionService().verifySession();

/** Get the session without redirecting (null if none). */
export const getSession = () => getSessionService().getSession();

/**
 * Require at least one of the allowed roles (defaults to the dashboard roles).
 * Redirects to /login (no session) or /unauthorized (wrong role).
 */
export const requireRoles = (allowed: readonly AdminRole[] = ADMIN_DASHBOARD_ROLES) =>
  getAccessService().requireRoles(allowed);
