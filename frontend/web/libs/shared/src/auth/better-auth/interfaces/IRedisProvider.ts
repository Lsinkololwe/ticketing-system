/**
 * Redis Provider Interface
 *
 * Abstracts Redis connection management for testability.
 * Production implementations use ioredis with connection pooling,
 * test implementations can use ioredis-mock or in-memory stores.
 *
 * @example
 * ```typescript
 * // Production
 * const redis = RedisProvider.getInstance(env);
 *
 * // Testing with mock
 * const redis = new InMemoryRedisProvider();
 *
 * // Usage
 * if (redis.isEnabled()) {
 *   const client = redis.getClient();
 *   await client.setex('key', 3600, 'value');
 * }
 * ```
 *
 * @module libs/shared/src/auth/better-auth/interfaces/IRedisProvider
 */

import type { Redis } from 'ioredis';

// =============================================================================
// REDIS PROVIDER INTERFACE
// =============================================================================

/**
 * Redis provider contract
 *
 * Provides access to Redis client for session storage and caching.
 * Handles connection lifecycle, retries, and graceful degradation.
 */
export interface IRedisProvider {
  /**
   * Get the Redis client instance
   *
   * Returns null if Redis is not configured/enabled.
   * Callers should check isEnabled() first or handle null.
   *
   * @returns Native ioredis Redis client or null if disabled
   *
   * @example
   * ```typescript
   * const client = provider.getClient();
   * if (client) {
   *   await client.setex('session:123', 3600, JSON.stringify(session));
   * }
   * ```
   */
  getClient(): Redis | null;

  /**
   * Check if Redis is enabled and configured
   *
   * Use this to conditionally enable Redis-dependent features.
   *
   * @returns true if Redis is available
   *
   * @example
   * ```typescript
   * if (provider.isEnabled()) {
   *   // Enable JTI blacklist, secondary storage, etc.
   * }
   * ```
   */
  isEnabled(): boolean;

  /**
   * Disconnect from Redis
   *
   * Gracefully closes the connection.
   * Call during shutdown or test cleanup.
   */
  disconnect(): Promise<void>;

  /**
   * Check connection health
   *
   * Pings Redis to verify connectivity.
   *
   * @returns true if connected and responding
   */
  isConnected(): Promise<boolean>;

  /**
   * Get connection status
   *
   * Returns the current connection state.
   */
  getStatus(): RedisConnectionStatus;
}

// =============================================================================
// CONNECTION STATUS
// =============================================================================

/**
 * Redis connection status
 */
export type RedisConnectionStatus =
  | 'disabled'      // Redis not configured
  | 'connecting'    // Connection in progress
  | 'connected'     // Connected and ready
  | 'disconnected'  // Disconnected (will reconnect)
  | 'error';        // Connection error

// =============================================================================
// CONNECTION OPTIONS
// =============================================================================

/**
 * Redis connection options
 */
export interface RedisConnectionOptions {
  /** Redis host */
  host: string;

  /** Redis port */
  port: number;

  /** Redis password (optional) */
  password?: string;

  /** Key prefix for all operations */
  keyPrefix?: string;

  /** Max retries per request */
  maxRetriesPerRequest?: number;

  /** Connection timeout (ms) */
  connectTimeout?: number;

  /** Enable lazy connect (connect on first command) */
  lazyConnect?: boolean;

  /** Enable offline queue (queue commands while disconnected) */
  enableOfflineQueue?: boolean;
}

/**
 * Default Redis connection options
 */
export const DEFAULT_REDIS_OPTIONS: Partial<RedisConnectionOptions> = {
  port: 6379,
  maxRetriesPerRequest: 3,
  connectTimeout: 10000,
  lazyConnect: true,
  enableOfflineQueue: true,
};
