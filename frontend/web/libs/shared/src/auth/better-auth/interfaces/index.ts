/**
 * Better Auth Interfaces
 *
 * Contract definitions for dependency injection and testability.
 * All providers and services implement these interfaces.
 *
 * @module libs/shared/src/auth/better-auth/interfaces
 */

// =============================================================================
// PROVIDER INTERFACES
// =============================================================================

export type {
  IEnvironmentProvider,
  PartialEnvConfig,
} from './IEnvironmentProvider';

export type {
  IDatabaseProvider,
  MongoPoolOptions,
} from './IDatabaseProvider';

export { DEFAULT_POOL_OPTIONS } from './IDatabaseProvider';

export type {
  IRedisProvider,
  RedisConnectionStatus,
  RedisConnectionOptions,
} from './IRedisProvider';

export { DEFAULT_REDIS_OPTIONS } from './IRedisProvider';

// =============================================================================
// SERVICE INTERFACES
// =============================================================================

export type {
  IJtiBlacklistService,
  BlacklistEntry,
  BlacklistInput,
  BlacklistReason,
  BlacklistStats,
} from './IJtiBlacklistService';

export type {
  IBackchannelLogoutHandler,
  LogoutTokenClaims,
  BackchannelLogoutResult,
  BackchannelLogoutDetails,
  BackchannelLogoutErrorCode,
  BackchannelLogoutDependencies,
} from './IBackchannelLogoutHandler';
