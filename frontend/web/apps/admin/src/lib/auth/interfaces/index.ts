import 'server-only';

/**
 * Auth module interfaces barrel (admin app).
 */

export type {
  Session,
  User,
  AdminRole,
  AccessCheckResult,
  KeycloakTokenResponse,
  SessionOptions,
} from './types';

export { ADMIN_DASHBOARD_ROLES } from './types';

export type { ISessionService } from './ISessionService';
export type { ITokenService } from './ITokenService';
export type { IAccessService } from './IAccessService';
export type { IAuthConfig } from './IAuthConfig';
