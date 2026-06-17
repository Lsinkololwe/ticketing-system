/**
 * In-Memory Redis Provider
 *
 * Mock implementation of IRedisProvider for testing.
 * Uses Map-based storage instead of real Redis.
 *
 * ## Features
 *
 * - No Redis dependency required
 * - TTL support via setTimeout cleanup
 * - Casts to ioredis Redis type for compatibility
 * - Synchronous in-memory operations
 * - Easy state inspection for assertions
 *
 * @example
 * ```typescript
 * const redis = new InMemoryRedisProvider();
 * const container = new AuthContainer(config, env, mockDb, redis);
 *
 * // After test
 * redis.clear();
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/__mocks__/InMemoryRedisProvider
 */

import type { Redis } from 'ioredis';
import type {
  IRedisProvider,
  RedisConnectionStatus,
} from '../../interfaces/IRedisProvider';

// =============================================================================
// STORE ENTRY TYPE
// =============================================================================

interface StoreEntry {
  value: string;
  expiresAt: number | null; // null = no expiry
  timeoutId?: ReturnType<typeof setTimeout>;
}

// =============================================================================
// IN-MEMORY REDIS PROVIDER
// =============================================================================

/**
 * In-memory Redis provider for testing
 *
 * Provides a Redis-compatible interface backed by Map storage.
 * Returns a mock that is cast to ioredis Redis type.
 */
export class InMemoryRedisProvider implements IRedisProvider {
  private readonly store = new Map<string, StoreEntry>();
  private readonly enabled: boolean;
  private status: RedisConnectionStatus;

  /**
   * Create in-memory Redis provider
   *
   * @param enabled - Whether Redis features are enabled
   */
  constructor(enabled = true) {
    this.enabled = enabled;
    this.status = enabled ? 'connected' : 'disabled';
  }

  /**
   * Create disabled provider
   */
  static disabled(): InMemoryRedisProvider {
    return new InMemoryRedisProvider(false);
  }

  /**
   * Get mock Redis client (cast to ioredis Redis type)
   *
   * The mock implements the subset of Redis commands used by Better Auth.
   */
  getClient(): Redis | null {
    if (!this.enabled) return null;
    // Cast mock to Redis type for compatibility with Better Auth
    return this.createMockClient() as unknown as Redis;
  }

  /**
   * Check if enabled
   */
  isEnabled(): boolean {
    return this.enabled;
  }

  /**
   * Check if connected
   */
  async isConnected(): Promise<boolean> {
    return this.enabled;
  }

  /**
   * Get status
   */
  getStatus(): RedisConnectionStatus {
    return this.status;
  }

  /**
   * Disconnect (no-op for in-memory)
   */
  async disconnect(): Promise<void> {
    // Clear all timeouts
    for (const entry of this.store.values()) {
      if (entry.timeoutId) {
        clearTimeout(entry.timeoutId);
      }
    }
    this.store.clear();
    this.status = 'disconnected';
  }

  // ===========================================================================
  // TEST UTILITIES
  // ===========================================================================

  /**
   * Clear all stored data
   */
  clear(): void {
    for (const entry of this.store.values()) {
      if (entry.timeoutId) {
        clearTimeout(entry.timeoutId);
      }
    }
    this.store.clear();
  }

  /**
   * Get number of stored keys
   */
  size(): number {
    return this.store.size;
  }

  /**
   * Check if key exists (synchronous)
   */
  has(key: string): boolean {
    const entry = this.store.get(key);
    if (!entry) return false;
    if (entry.expiresAt && Date.now() > entry.expiresAt) {
      this.store.delete(key);
      return false;
    }
    return true;
  }

  /**
   * Get all keys (synchronous)
   */
  getAllKeys(): string[] {
    return Array.from(this.store.keys());
  }

  /**
   * Get raw value (synchronous, for testing)
   */
  getRaw(key: string): string | null {
    const entry = this.store.get(key);
    if (!entry) return null;
    if (entry.expiresAt && Date.now() > entry.expiresAt) {
      this.store.delete(key);
      return null;
    }
    return entry.value;
  }

  // ===========================================================================
  // PRIVATE: Create mock client with Redis-like interface
  // ===========================================================================

  private createMockClient() {
    const self = this;

    return {
      get: async (key: string): Promise<string | null> => {
        const entry = self.store.get(key);
        if (!entry) return null;

        // Check expiry
        if (entry.expiresAt && Date.now() > entry.expiresAt) {
          self.store.delete(key);
          return null;
        }

        return entry.value;
      },

      set: async (key: string, value: string): Promise<'OK'> => {
        // Clear existing timeout if any
        const existing = self.store.get(key);
        if (existing?.timeoutId) {
          clearTimeout(existing.timeoutId);
        }

        self.store.set(key, { value, expiresAt: null });
        return 'OK';
      },

      setex: async (key: string, seconds: number, value: string): Promise<'OK'> => {
        // Clear existing timeout
        const existing = self.store.get(key);
        if (existing?.timeoutId) {
          clearTimeout(existing.timeoutId);
        }

        const expiresAt = Date.now() + seconds * 1000;
        const timeoutId = setTimeout(() => {
          self.store.delete(key);
        }, seconds * 1000);

        self.store.set(key, { value, expiresAt, timeoutId });
        return 'OK';
      },

      del: async (...keys: string[]): Promise<number> => {
        let deleted = 0;
        for (const key of keys) {
          const entry = self.store.get(key);
          if (entry) {
            if (entry.timeoutId) {
              clearTimeout(entry.timeoutId);
            }
            self.store.delete(key);
            deleted++;
          }
        }
        return deleted;
      },

      exists: async (...keys: string[]): Promise<number> => {
        let count = 0;
        for (const key of keys) {
          if (self.has(key)) count++;
        }
        return count;
      },

      keys: async (pattern: string): Promise<string[]> => {
        // Convert Redis pattern to regex
        const regexPattern = pattern
          .replace(/\*/g, '.*')
          .replace(/\?/g, '.');
        const regex = new RegExp(`^${regexPattern}$`);

        const result: string[] = [];
        for (const key of self.store.keys()) {
          if (regex.test(key) && self.has(key)) {
            result.push(key);
          }
        }
        return result;
      },

      expire: async (key: string, seconds: number): Promise<number> => {
        const entry = self.store.get(key);
        if (!entry) return 0;

        // Clear existing timeout
        if (entry.timeoutId) {
          clearTimeout(entry.timeoutId);
        }

        const expiresAt = Date.now() + seconds * 1000;
        const timeoutId = setTimeout(() => {
          self.store.delete(key);
        }, seconds * 1000);

        entry.expiresAt = expiresAt;
        entry.timeoutId = timeoutId;
        return 1;
      },

      ttl: async (key: string): Promise<number> => {
        const entry = self.store.get(key);
        if (!entry) return -2; // Key doesn't exist
        if (!entry.expiresAt) return -1; // No expiry

        const ttl = Math.ceil((entry.expiresAt - Date.now()) / 1000);
        return ttl > 0 ? ttl : -2;
      },

      ping: async (): Promise<'PONG'> => {
        return 'PONG';
      },

      quit: async (): Promise<'OK'> => {
        self.clear();
        return 'OK';
      },
    };
  }
}
