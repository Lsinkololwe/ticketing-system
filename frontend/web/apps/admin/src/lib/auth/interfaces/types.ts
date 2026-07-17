import 'server-only';
import type { Session as BetterAuthSession, User as BetterAuthUser } from 'better-auth/types';

/**
 * Core auth types for the admin app.
 *
 * Mirrors the organization-admin modular auth layer, but the admin app is gated
 * by PLATFORM ROLES (ADMIN / SUPER_ADMIN / FINANCE / SCANNER) rather than
 * organization status.
 */

/** Better Auth session response from auth.api.getSession() (session + user). */
export interface Session {
  readonly session: BetterAuthSession;
  readonly user: BetterAuthUser;
}

/** User type alias for convenience. */
export type User = BetterAuthUser;

/** Platform roles recognised by the admin app (mirrors the backend users enum). */
export type AdminRole =
  | 'ADMIN'
  | 'SUPER_ADMIN'
  | 'FINANCE'
  | 'SCANNER'
  | 'ORGANIZER'
  | 'CUSTOMER';

/** Roles permitted to access the admin dashboard. */
export const ADMIN_DASHBOARD_ROLES: readonly AdminRole[] = [
  'ADMIN',
  'SUPER_ADMIN',
  'FINANCE',
];

/** Result of an authorization check. */
export interface AccessCheckResult {
  /** Whether the user holds at least one of the required roles. */
  readonly allowed: boolean;
  /** The user's normalised (upper-case) roles. */
  readonly roles: readonly string[];
  /** The roles that were required for the check. */
  readonly required: readonly string[];
}

/** Keycloak token response structure. */
export interface KeycloakTokenResponse {
  readonly access_token: string;
  readonly expires_in: number;
  readonly refresh_expires_in: number;
  readonly refresh_token?: string;
  readonly token_type: string;
  readonly id_token?: string;
  readonly session_state?: string;
  readonly scope: string;
}

/** Options for session operations. */
export interface SessionOptions {
  readonly redirectOnError?: boolean;
  readonly errorPath?: string;
}
