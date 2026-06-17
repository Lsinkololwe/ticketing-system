/**
 * Better Auth Testing Utilities
 *
 * Mock implementations for testing Better Auth without real dependencies.
 * Can be imported in any test environment (no server-only restriction).
 *
 * ## Usage
 *
 * ```typescript
 * import { AuthContainer } from '@pml.tickets/shared/auth/better-auth/server';
 * import {
 *   TestEnvProvider,
 *   InMemoryDatabaseProvider,
 *   InMemoryRedisProvider,
 * } from '@pml.tickets/shared/auth/better-auth/testing';
 *
 * describe('MyAuthTest', () => {
 *   let container: AuthContainer;
 *   let dbProvider: InMemoryDatabaseProvider;
 *   let redisProvider: InMemoryRedisProvider;
 *
 *   beforeEach(() => {
 *     const env = new TestEnvProvider({
 *       appUrl: 'http://test.local:3000',
 *       keycloakIssuer: 'http://keycloak.test/realms/test',
 *     });
 *     dbProvider = new InMemoryDatabaseProvider();
 *     redisProvider = new InMemoryRedisProvider();
 *
 *     container = new AuthContainer(
 *       { appId: 'test', cookiePrefix: 'test_' },
 *       env,
 *       dbProvider,
 *       redisProvider,
 *     );
 *   });
 *
 *   afterEach(() => {
 *     dbProvider.clear();
 *     redisProvider.clear();
 *   });
 *
 *   it('should create auth instance', () => {
 *     const auth = container.getAuth();
 *     expect(auth).toBeDefined();
 *   });
 * });
 * ```
 *
 * @module libs/shared/src/auth/better-auth/testing
 */

// =============================================================================
// MOCK PROVIDERS
// =============================================================================

export {
  TestEnvProvider,
  InMemoryDatabaseProvider,
  InMemoryRedisProvider,
} from './providers/__mocks__';

// =============================================================================
// RE-EXPORT INTERFACES FOR TYPING
// =============================================================================

export type {
  IEnvironmentProvider,
  PartialEnvConfig,
  IDatabaseProvider,
  IRedisProvider,
  IJtiBlacklistService,
  IBackchannelLogoutHandler,
} from './interfaces';
