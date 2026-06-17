/**
 * MongoDB Database Provider
 *
 * SERVER-ONLY: Uses MongoDB native driver.
 *
 * Production implementation of IDatabaseProvider.
 * Uses singleton pattern with global storage to survive Next.js HMR.
 *
 * ## Native MongoDB Features Used
 *
 * - Connection pooling (MongoClient)
 * - Lazy connection (connects on first operation)
 * - Automatic reconnection
 * - Server monitoring
 *
 * @example
 * ```typescript
 * const env = new ProcessEnvProvider();
 * const db = MongoDatabaseProvider.getInstance(env);
 *
 * const users = db.getCollection<User>('users');
 * const sessions = db.getCollection<Session>('sessions');
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/MongoDatabaseProvider
 */

import 'server-only';

import { MongoClient, type Db, type Collection, type Document } from 'mongodb';
import type { IDatabaseProvider, MongoPoolOptions } from '../interfaces/IDatabaseProvider';
import { DEFAULT_POOL_OPTIONS } from '../interfaces/IDatabaseProvider';
import type { IEnvironmentProvider } from '../interfaces/IEnvironmentProvider';

// =============================================================================
// GLOBAL SINGLETON (Survives HMR)
// =============================================================================

declare global {
  // eslint-disable-next-line no-var
  var _mongoDatabaseProvider: MongoDatabaseProvider | undefined;
}

// =============================================================================
// MONGODB DATABASE PROVIDER
// =============================================================================

/**
 * Production MongoDB database provider
 *
 * Manages MongoDB connection with pooling and automatic reconnection.
 * Uses global singleton in development to survive hot reload.
 */
export class MongoDatabaseProvider implements IDatabaseProvider {
  private readonly client: MongoClient;
  private readonly db: Db;
  private connected = false;

  /**
   * Private constructor - use getInstance() for singleton
   *
   * @param uri - MongoDB connection URI
   * @param database - Database name
   * @param options - Connection pool options
   */
  private constructor(
    uri: string,
    database: string,
    options: MongoPoolOptions
  ) {
    this.client = new MongoClient(uri, {
      maxPoolSize: options.maxPoolSize,
      minPoolSize: options.minPoolSize,
      maxIdleTimeMS: options.maxIdleTimeMS,
      connectTimeoutMS: options.connectTimeoutMS,
      socketTimeoutMS: options.socketTimeoutMS,
      // Native MongoDB driver features
      serverSelectionTimeoutMS: 5000,
      retryWrites: true,
      retryReads: true,
    });

    this.db = this.client.db(database);

    // Set up connection event listeners
    this.setupEventListeners();
  }

  /**
   * Get singleton instance
   *
   * In development: Returns cached global instance (survives HMR)
   * In production: Returns new instance (each cold start isolated)
   *
   * @param env - Environment provider with connection config
   * @returns MongoDatabaseProvider singleton
   */
  static getInstance(env: IEnvironmentProvider): MongoDatabaseProvider {
    const poolOptions = env.isProduction
      ? DEFAULT_POOL_OPTIONS.production
      : DEFAULT_POOL_OPTIONS.development;

    // Production: fresh instance per cold start
    if (env.isProduction) {
      return new MongoDatabaseProvider(
        env.mongoUri,
        env.mongoDatabase,
        poolOptions
      );
    }

    // Development: global singleton
    if (!global._mongoDatabaseProvider) {
      global._mongoDatabaseProvider = new MongoDatabaseProvider(
        env.mongoUri,
        env.mongoDatabase,
        poolOptions
      );
    }

    return global._mongoDatabaseProvider;
  }

  /**
   * Create a new instance (for testing)
   *
   * Bypasses singleton for test isolation.
   *
   * @param uri - MongoDB connection URI
   * @param database - Database name
   * @param options - Connection pool options (optional)
   */
  static create(
    uri: string,
    database: string,
    options?: Partial<MongoPoolOptions>
  ): MongoDatabaseProvider {
    const poolOptions: MongoPoolOptions = {
      ...DEFAULT_POOL_OPTIONS.development,
      ...options,
    };
    return new MongoDatabaseProvider(uri, database, poolOptions);
  }

  /**
   * Get the MongoDB client instance
   *
   * Used for transactions or direct client operations.
   */
  getClient(): MongoClient {
    return this.client;
  }

  /**
   * Get the database instance
   *
   * Primary method for collection access.
   */
  getDb(): Db {
    return this.db;
  }

  /**
   * Get a typed collection
   *
   * @param name - Collection name
   * @returns Typed MongoDB Collection
   */
  getCollection<T extends Document = Document>(name: string): Collection<T> {
    return this.db.collection<T>(name);
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Disconnect from MongoDB
   *
   * Closes all connections in the pool.
   */
  async disconnect(): Promise<void> {
    try {
      await this.client.close();
      this.connected = false;
      console.log('[MongoDatabaseProvider] Disconnected from MongoDB');
    } catch (error) {
      console.error('[MongoDatabaseProvider] Error disconnecting:', error);
      throw error;
    }
  }

  /**
   * Set up connection event listeners
   */
  private setupEventListeners(): void {
    // Using native MongoDB driver events
    this.client.on('open', () => {
      this.connected = true;
      console.log('[MongoDatabaseProvider] Connected to MongoDB');
    });

    this.client.on('close', () => {
      this.connected = false;
      console.log('[MongoDatabaseProvider] MongoDB connection closed');
    });

    this.client.on('error', (error) => {
      console.error('[MongoDatabaseProvider] MongoDB error:', error);
    });

    this.client.on('serverHeartbeatFailed', (event) => {
      console.warn('[MongoDatabaseProvider] Server heartbeat failed:', event.failure);
    });
  }
}
