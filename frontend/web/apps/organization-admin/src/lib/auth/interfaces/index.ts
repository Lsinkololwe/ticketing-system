import 'server-only';

/**
 * Auth module interfaces barrel export.
 *
 * This file provides a centralized export point for all authentication-related
 * interfaces and types, following the interface segregation principle.
 *
 * @module auth/interfaces
 */

// Core type definitions
export type {
  OrganizationStatus,
  OrganizationStatusResponse,
  RouteMapping,
  Session,
  User,
  SessionWithOrganization,
  KeycloakTokenResponse,
  SessionVerificationResult,
  SessionOptions,
} from './types';

export { OrganizationStatusEnum } from './types';

// Service interfaces
export type { ISessionService } from './ISessionService';
export type {
  IOrganizationService,
  RouteValidationResult,
} from './IOrganizationService';
export type { ITokenService } from './ITokenService';

// Configuration interface
export type {
  IAuthConfig,
  AuthConfigFactory,
  PartialAuthConfig,
  RequiredAuthConfigKeys,
} from './IAuthConfig';

export { validateAuthConfig } from './IAuthConfig';
