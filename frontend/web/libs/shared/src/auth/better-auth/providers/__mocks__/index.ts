/**
 * Better Auth Test Mocks
 *
 * Mock implementations of providers for testing.
 * No server-only restriction - can be used in any test environment.
 *
 * @example
 * ```typescript
 * import {
 *   TestEnvProvider,
 *   InMemoryDatabaseProvider,
 *   InMemoryRedisProvider,
 * } from '@pml.tickets/shared/auth/better-auth/providers/__mocks__';
 *
 * const env = new TestEnvProvider({ appUrl: 'http://test.local' });
 * const db = new InMemoryDatabaseProvider();
 * const redis = new InMemoryRedisProvider();
 *
 * const container = new AuthContainer(config, env, db, redis);
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/__mocks__
 */

export { TestEnvProvider } from './TestEnvProvider';
export { InMemoryDatabaseProvider } from './InMemoryDatabaseProvider';
export { InMemoryRedisProvider } from './InMemoryRedisProvider';
