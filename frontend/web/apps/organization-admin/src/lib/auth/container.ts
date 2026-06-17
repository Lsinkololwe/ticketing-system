/**
 * Dependency Injection Container - Service Factory
 *
 * Provides lazy singleton instances of auth services following industry-standard
 * dependency injection patterns. Inspired by Next.js and TypeScript DI best practices.
 *
 * ## Features
 *
 * - Lazy singleton pattern: Services created only when first requested
 * - Global singleton cache: Survives hot reload in development
 * - Test-friendly: Supports mock injection for testing
 * - Strong typing: Full TypeScript support
 *
 * ## Usage
 *
 * ```typescript
 * // In Server Components or DAL
 * import { getSessionService, getOrganizationService } from './container';
 *
 * const sessionService = getSessionService();
 * const session = await sessionService.verifySession();
 * ```
 *
 * @see https://github.com/vercel/next.js/blob/canary/examples/with-mysql/lib/prisma.ts
 * @module container
 */

import 'server-only';

import { auth, env } from './index';
import { SessionService } from './services/SessionService';
import { OrganizationService } from './services/OrganizationService';
import { TokenService } from './services/TokenService';
import type {
  ISessionService,
  IOrganizationService,
  ITokenService,
  IAuthConfig,
} from './interfaces';

// =============================================================================
// GLOBAL SINGLETON STORAGE (Survives Hot Reload)
// =============================================================================

declare global {
  // eslint-disable-next-line no-var
  var _sessionService: ISessionService | undefined;
  // eslint-disable-next-line no-var
  var _organizationService: IOrganizationService | undefined;
  // eslint-disable-next-line no-var
  var _tokenService: ITokenService | undefined;
}

// =============================================================================
// CONFIGURATION
// =============================================================================

/**
 * Build auth configuration from environment
 *
 * Extracts required configuration values from environment variables
 * and Better Auth services.
 */
function buildAuthConfig(): IAuthConfig {
  const graphqlEndpoint =
    process.env.GRAPHQL_ENDPOINT || 'http://localhost:8080/graphql';

  return {
    graphqlEndpoint,
    keycloakIssuer: env.KEYCLOAK_ISSUER,
    keycloakClientId: env.KEYCLOAK_CLIENT_ID,
    appUrl: env.APP_URL,
  };
}

// Lazy configuration - built once on first access
let config: IAuthConfig | null = null;

function getConfig(): IAuthConfig {
  if (!config) {
    config = buildAuthConfig();
  }
  return config;
}

// =============================================================================
// SERVICE FACTORIES (Lazy Singletons)
// =============================================================================

/**
 * Get SessionService singleton instance
 *
 * Creates the service on first call and caches it globally.
 * In development mode, the instance is stored in globalThis to survive
 * hot module reloads.
 *
 * @returns Singleton SessionService instance
 *
 * @example
 * ```typescript
 * const sessionService = getSessionService();
 * const session = await sessionService.verifySession();
 * ```
 */
export function getSessionService(): ISessionService {
  if (process.env.NODE_ENV === 'development') {
    if (!global._sessionService) {
      global._sessionService = new SessionService(auth);
    }
    return global._sessionService;
  }

  // Production: no global cache (each instance isolated)
  return new SessionService(auth);
}

/**
 * Get OrganizationService singleton instance
 *
 * Creates the service on first call and caches it globally.
 * Depends on SessionService for authentication.
 *
 * @returns Singleton OrganizationService instance
 *
 * @example
 * ```typescript
 * const orgService = getOrganizationService();
 * const status = await orgService.getStatus();
 * const route = orgService.getRouteForStatus(status.status);
 * ```
 */
export function getOrganizationService(): IOrganizationService {
  if (process.env.NODE_ENV === 'development') {
    if (!global._organizationService) {
      global._organizationService = new OrganizationService(
        getSessionService(),
        getConfig()
      );
    }
    return global._organizationService;
  }

  return new OrganizationService(getSessionService(), getConfig());
}

/**
 * Get TokenService singleton instance
 *
 * Creates the service on first call and caches it globally.
 * Depends on SessionService for authentication.
 *
 * @returns Singleton TokenService instance
 *
 * @example
 * ```typescript
 * const tokenService = getTokenService();
 * const token = await tokenService.getKeycloakAccessToken();
 * ```
 */
export function getTokenService(): ITokenService {
  if (process.env.NODE_ENV === 'development') {
    if (!global._tokenService) {
      global._tokenService = new TokenService(getSessionService(), getConfig());
    }
    return global._tokenService;
  }

  return new TokenService(getSessionService(), getConfig());
}

// =============================================================================
// TESTING UTILITIES
// =============================================================================

/**
 * Reset all service singletons
 *
 * Clears the global service cache. Useful for testing to ensure
 * a clean state between test runs.
 *
 * WARNING: Only use this in test environments!
 *
 * @example
 * ```typescript
 * // In test setup
 * beforeEach(() => {
 *   resetServices();
 * });
 * ```
 */
export function resetServices(): void {
  global._sessionService = undefined;
  global._organizationService = undefined;
  global._tokenService = undefined;
  config = null;
}

/**
 * Inject mock services for testing
 *
 * Replaces singleton instances with test mocks. This enables
 * isolated unit testing without hitting real auth services.
 *
 * WARNING: Only use this in test environments!
 *
 * @param mocks - Object containing mock services to inject
 *
 * @example
 * ```typescript
 * // In test
 * const mockSession = {
 *   verifySession: jest.fn().mockResolvedValue({ user: { id: '123' } }),
 *   getSession: jest.fn().mockResolvedValue({ user: { id: '123' } }),
 *   signOut: jest.fn().mockResolvedValue(undefined),
 * };
 *
 * setMockServices({ session: mockSession });
 *
 * // Now getSessionService() returns the mock
 * const service = getSessionService();
 * await service.verifySession(); // Calls mock
 * ```
 */
export function setMockServices(mocks: {
  session?: ISessionService;
  organization?: IOrganizationService;
  token?: ITokenService;
}): void {
  if (mocks.session) global._sessionService = mocks.session;
  if (mocks.organization) global._organizationService = mocks.organization;
  if (mocks.token) global._tokenService = mocks.token;
}
