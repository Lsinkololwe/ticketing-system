/**
 * Database Provider Interface
 *
 * Abstracts MongoDB connection management for testability.
 * Production implementations use real MongoDB with connection pooling,
 * test implementations can use mongodb-memory-server or mocks.
 *
 * @example
 * ```typescript
 * // Production
 * const db = MongoDatabaseProvider.getInstance(env);
 *
 * // Testing with mongodb-memory-server
 * const db = await InMemoryDatabaseProvider.create();
 *
 * // Usage
 * const collection = db.getDb().collection('users');
 * ```
 *
 * @module libs/shared/src/auth/better-auth/interfaces/IDatabaseProvider
 */

import type { Db, MongoClient, Collection, Document } from 'mongodb';

// =============================================================================
// DATABASE PROVIDER INTERFACE
// =============================================================================

/**
 * MongoDB database provider contract
 *
 * Provides access to MongoDB client and database instances.
 * Implementations handle connection lifecycle and pooling.
 */
export interface IDatabaseProvider {
  /**
   * Get the MongoDB client instance
   *
   * Used for direct client operations like transactions.
   *
   * @returns MongoClient instance (lazy connected)
   */
  getClient(): MongoClient;

  /**
   * Get the database instance
   *
   * Primary method for collection access.
   *
   * @returns Db instance for the configured database
   *
   * @example
   * ```typescript
   * const db = provider.getDb();
   * const users = db.collection('users');
   * const sessions = db.collection('sessions');
   * ```
   */
  getDb(): Db;

  /**
   * Get a typed collection
   *
   * Convenience method for type-safe collection access.
   *
   * @param name - Collection name
   * @returns Typed Collection instance
   *
   * @example
   * ```typescript
   * interface User { id: string; email: string; }
   * const users = provider.getCollection<User>('users');
   * ```
   */
  getCollection<T extends Document = Document>(name: string): Collection<T>;

  /**
   * Disconnect from the database
   *
   * Closes all connections in the pool.
   * Call during graceful shutdown or test cleanup.
   */
  disconnect(): Promise<void>;

  /**
   * Check if the database is connected
   *
   * Useful for health checks.
   */
  isConnected(): boolean;
}

// =============================================================================
// CONNECTION OPTIONS
// =============================================================================

/**
 * MongoDB connection pool options
 */
export interface MongoPoolOptions {
  /** Maximum connections in the pool */
  maxPoolSize: number;

  /** Minimum connections to maintain */
  minPoolSize: number;

  /** Max idle time before closing a connection (ms) */
  maxIdleTimeMS?: number;

  /** Connection timeout (ms) */
  connectTimeoutMS?: number;

  /** Socket timeout (ms) */
  socketTimeoutMS?: number;
}

/**
 * Default pool options by environment
 */
export const DEFAULT_POOL_OPTIONS: Record<'development' | 'production', MongoPoolOptions> = {
  development: {
    maxPoolSize: 10,
    minPoolSize: 2,
    maxIdleTimeMS: 60000,
  },
  production: {
    maxPoolSize: 50,
    minPoolSize: 10,
    connectTimeoutMS: 10000,
    socketTimeoutMS: 45000,
  },
};
