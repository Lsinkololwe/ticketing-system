/**
 * Redis Provider
 *
 * SERVER-ONLY: Uses ioredis native driver.
 *
 * Production implementation of IRedisProvider.
 * Uses singleton pattern with global storage to survive Next.js HMR.
 *
 * ## Native ioredis Features Used
 *
 * - Automatic reconnection with exponential backoff
 * - Lazy connection (connects on first command)
 * - Offline command queue
 * - Connection pooling
 * - Cluster support ready
 *
 * @example
 * ```typescript
 * const env = new ProcessEnvProvider();
 * const redis = RedisProvider.getInstance(env);
 *
 * if (redis.isEnabled()) {
 *   const client = redis.getClient()!;
 *   await client.setex('session:123', 3600, JSON.stringify(data));
 * }
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/RedisProvider
 */

import 'server-only';

import { Redis } from 'ioredis';
import type {
  IRedisProvider,
  RedisConnectionStatus,
  RedisConnectionOptions,
} from '../interfaces/IRedisProvider';
import { DEFAULT_REDIS_OPTIONS } from '../interfaces/IRedisProvider';
import type { IEnvironmentProvider } from '../interfaces/IEnvironmentProvider';

// =============================================================================
// GLOBAL SINGLETON (Survives HMR)
// =============================================================================

declare global {
  // eslint-disable-next-line no-var
  var _redisProvider: RedisProvider | undefined;
}

// =============================================================================
// REDIS PROVIDER
// =============================================================================

/**
 * Production Redis provider
 *
 * Manages Redis connection with automatic reconnection.
 * Uses global singleton in development to survive hot reload.
 */
export class RedisProvider implements IRedisProvider {
  private readonly client: Redis | null;
  private readonly enabled: boolean;
  private status: RedisConnectionStatus;

  /**
   * Private constructor - use getInstance() for singleton
   *
   * @param options - Redis connection options (null to disable)
   */
  private constructor(options: RedisConnectionOptions | null) {
    if (!options) {
      this.client = null;
      this.enabled = false;
      this.status = 'disabled';
      return;
    }

    this.enabled = true;
    this.status = 'connecting';

    // Create ioredis client with native features
    this.client = new Redis({
      host: options.host,
      port: options.port,
      password: options.password,
      keyPrefix: options.keyPrefix,
      maxRetriesPerRequest: options.maxRetriesPerRequest ?? DEFAULT_REDIS_OPTIONS.maxRetriesPerRequest,
      connectTimeout: options.connectTimeout ?? DEFAULT_REDIS_OPTIONS.connectTimeout,
      lazyConnect: options.lazyConnect ?? DEFAULT_REDIS_OPTIONS.lazyConnect,
      enableOfflineQueue: options.enableOfflineQueue ?? DEFAULT_REDIS_OPTIONS.enableOfflineQueue,
      // Native ioredis retry strategy
      retryStrategy: (times: number) => {
        if (times > 5) {
          console.error('[RedisProvider] Max retries reached, giving up');
          return null; // Stop retrying
        }
        // Exponential backoff: 200ms, 400ms, 800ms, 1600ms, 3200ms
        const delay = Math.min(times * 200, 5000);
        console.log(`[RedisProvider] Retrying connection in ${delay}ms (attempt ${times})`);
        return delay;
      },
      // Reconnect on error (native ioredis feature)
      reconnectOnError: (err: Error) => {
        const targetErrors = ['READONLY', 'ECONNRESET', 'ETIMEDOUT'];
        return targetErrors.some((e) => err.message.includes(e));
      },
    });

    // Set up event listeners
    this.setupEventListeners();
  }

  /**
   * Get singleton instance
   *
   * In development: Returns cached global instance (survives HMR)
   * In production: Returns new instance (each cold start isolated)
   *
   * @param env - Environment provider with Redis config
   * @returns RedisProvider singleton
   */
  static getInstance(env: IEnvironmentProvider): RedisProvider {
    // Redis not configured
    if (!env.redisEnabled || !env.redisHost) {
      return new RedisProvider(null);
    }

    const options: RedisConnectionOptions = {
      host: env.redisHost,
      port: env.redisPort ?? 6379,
      password: env.redisPassword,
    };

    // Production: fresh instance per cold start
    if (env.isProduction) {
      return new RedisProvider(options);
    }

    // Development: global singleton
    if (!global._redisProvider) {
      global._redisProvider = new RedisProvider(options);
    }

    return global._redisProvider;
  }

  /**
   * Create a new instance (for testing)
   *
   * Bypasses singleton for test isolation.
   *
   * @param options - Redis connection options (null to create disabled provider)
   */
  static create(options: RedisConnectionOptions | null): RedisProvider {
    return new RedisProvider(options);
  }

  /**
   * Create a disabled provider
   *
   * Use when Redis is not needed.
   */
  static disabled(): RedisProvider {
    return new RedisProvider(null);
  }

  /**
   * Get the Redis client instance
   *
   * Returns null if Redis is not enabled.
   */
  getClient(): Redis | null {
    return this.client;
  }

  /**
   * Check if Redis is enabled
   */
  isEnabled(): boolean {
    return this.enabled;
  }

  /**
   * Get connection status
   */
  getStatus(): RedisConnectionStatus {
    return this.status;
  }

  /**
   * Check if connected and responding
   */
  async isConnected(): Promise<boolean> {
    if (!this.client) return false;

    try {
      const pong = await this.client.ping();
      return pong === 'PONG';
    } catch {
      return false;
    }
  }

  /**
   * Disconnect from Redis
   */
  async disconnect(): Promise<void> {
    if (!this.client) return;

    try {
      await this.client.quit();
      this.status = 'disconnected';
      console.log('[RedisProvider] Disconnected from Redis');
    } catch (error) {
      console.error('[RedisProvider] Error disconnecting:', error);
      throw error;
    }
  }

  /**
   * Set up connection event listeners
   */
  private setupEventListeners(): void {
    if (!this.client) return;

    // Native ioredis events
    this.client.on('connect', () => {
      console.log('[RedisProvider] Connecting to Redis...');
    });

    this.client.on('ready', () => {
      this.status = 'connected';
      console.log('[RedisProvider] Redis connection ready');
    });

    this.client.on('error', (error: Error) => {
      this.status = 'error';
      console.error('[RedisProvider] Redis error:', error.message);
    });

    this.client.on('close', () => {
      this.status = 'disconnected';
      console.log('[RedisProvider] Redis connection closed');
    });

    this.client.on('reconnecting', () => {
      this.status = 'connecting';
      console.log('[RedisProvider] Reconnecting to Redis...');
    });

    this.client.on('end', () => {
      this.status = 'disconnected';
      console.log('[RedisProvider] Redis connection ended');
    });
  }
}
