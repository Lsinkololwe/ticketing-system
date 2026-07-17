/**
 * Dependency Injection Container — Service Factory (admin app).
 *
 * Lazy singleton instances of the auth services, mirroring the organization-admin
 * pattern. Global cache survives hot reload in development; test-friendly via
 * setMockServices().
 *
 * @module auth/container
 */

import 'server-only';

import { auth, env } from './index';
import { SessionService } from './services/SessionService';
import { TokenService } from './services/TokenService';
import { AccessService } from './services/AccessService';
import type {
  ISessionService,
  ITokenService,
  IAccessService,
  IAuthConfig,
} from './interfaces';

// =============================================================================
// GLOBAL SINGLETON STORAGE (survives hot reload)
// =============================================================================

declare global {
  // eslint-disable-next-line no-var
  var _adminSessionService: ISessionService | undefined;
  // eslint-disable-next-line no-var
  var _adminTokenService: ITokenService | undefined;
  // eslint-disable-next-line no-var
  var _adminAccessService: IAccessService | undefined;
}

// =============================================================================
// CONFIGURATION
// =============================================================================

let config: IAuthConfig | null = null;

function getConfig(): IAuthConfig {
  if (!config) {
    config = {
      graphqlEndpoint:
        process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT ||
        process.env.GRAPHQL_ENDPOINT ||
        'http://localhost:8080/graphql',
      keycloakIssuer: env.KEYCLOAK_ISSUER,
      keycloakClientId: env.KEYCLOAK_CLIENT_ID,
      // Server-only — used for back-channel logout; undefined in client bundles.
      keycloakClientSecret: process.env.AUTH_KEYCLOAK_SECRET,
      appUrl: env.APP_URL,
    };
  }
  return config;
}

// =============================================================================
// SERVICE FACTORIES (lazy singletons)
// =============================================================================

export function getSessionService(): ISessionService {
  if (process.env.NODE_ENV === 'development') {
    if (!global._adminSessionService) {
      global._adminSessionService = new SessionService(auth);
    }
    return global._adminSessionService;
  }
  return new SessionService(auth);
}

export function getTokenService(): ITokenService {
  if (process.env.NODE_ENV === 'development') {
    if (!global._adminTokenService) {
      global._adminTokenService = new TokenService(getConfig());
    }
    return global._adminTokenService;
  }
  return new TokenService(getConfig());
}

export function getAccessService(): IAccessService {
  if (process.env.NODE_ENV === 'development') {
    if (!global._adminAccessService) {
      global._adminAccessService = new AccessService(getSessionService());
    }
    return global._adminAccessService;
  }
  return new AccessService(getSessionService());
}

// =============================================================================
// TESTING UTILITIES
// =============================================================================

export function resetServices(): void {
  global._adminSessionService = undefined;
  global._adminTokenService = undefined;
  global._adminAccessService = undefined;
  config = null;
}

export function setMockServices(mocks: {
  session?: ISessionService;
  token?: ITokenService;
  access?: IAccessService;
}): void {
  if (mocks.session) global._adminSessionService = mocks.session;
  if (mocks.token) global._adminTokenService = mocks.token;
  if (mocks.access) global._adminAccessService = mocks.access;
}
