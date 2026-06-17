/**
 * In-Memory Database Provider
 *
 * Mock implementation of IDatabaseProvider for testing.
 *
 * ## Usage Options
 *
 * 1. **Simple Mock** (this file): Basic mock for unit tests
 * 2. **mongodb-memory-server**: Full MongoDB for integration tests
 *
 * For integration tests requiring real MongoDB behavior, use:
 * ```typescript
 * import { MongoMemoryServer } from 'mongodb-memory-server';
 * import { MongoDatabaseProvider } from '../MongoDatabaseProvider';
 *
 * const mongod = await MongoMemoryServer.create();
 * const db = MongoDatabaseProvider.create(mongod.getUri(), 'test');
 * ```
 *
 * @example
 * ```typescript
 * // Unit test with simple mock
 * const db = new InMemoryDatabaseProvider();
 * const container = new AuthContainer(config, env, db, redis);
 *
 * // After test
 * db.clear();
 * ```
 *
 * @module libs/shared/src/auth/better-auth/providers/__mocks__/InMemoryDatabaseProvider
 */

import type { Db, MongoClient, Collection, Document } from 'mongodb';
import type { IDatabaseProvider } from '../../interfaces/IDatabaseProvider';

// =============================================================================
// MOCK COLLECTION
// =============================================================================

/**
 * Mock MongoDB collection
 *
 * Implements the subset of Collection methods used by Better Auth.
 */
class MockCollection<T extends Document = Document> {
  private readonly documents = new Map<string, T>();
  readonly collectionName: string;

  constructor(name: string) {
    this.collectionName = name;
  }

  // Find operations
  async findOne(filter: Partial<T> | { $or?: Partial<T>[] }): Promise<T | null> {
    for (const doc of this.documents.values()) {
      if (this.matchesFilter(doc, filter)) {
        return doc;
      }
    }
    return null;
  }

  find(filter: Partial<T> = {}): MockCursor<T> {
    const results: T[] = [];
    for (const doc of this.documents.values()) {
      if (this.matchesFilter(doc, filter)) {
        results.push(doc);
      }
    }
    return new MockCursor(results);
  }

  // Insert operations
  async insertOne(doc: T): Promise<{ insertedId: string; acknowledged: boolean }> {
    const id = (doc as Record<string, unknown>).id || (doc as Record<string, unknown>)._id || this.generateId();
    (doc as Record<string, unknown>)._id = id;
    this.documents.set(String(id), doc);
    return { insertedId: String(id), acknowledged: true };
  }

  async insertMany(docs: T[]): Promise<{ insertedCount: number; acknowledged: boolean }> {
    for (const doc of docs) {
      await this.insertOne(doc);
    }
    return { insertedCount: docs.length, acknowledged: true };
  }

  // Update operations
  async updateOne(
    filter: Partial<T>,
    update: { $set?: Partial<T>; $unset?: Record<string, unknown> }
  ): Promise<{ matchedCount: number; modifiedCount: number; acknowledged: boolean }> {
    for (const [id, doc] of this.documents.entries()) {
      if (this.matchesFilter(doc, filter)) {
        if (update.$set) {
          Object.assign(doc, update.$set);
        }
        if (update.$unset) {
          for (const key of Object.keys(update.$unset)) {
            delete (doc as Record<string, unknown>)[key];
          }
        }
        this.documents.set(id, doc);
        return { matchedCount: 1, modifiedCount: 1, acknowledged: true };
      }
    }
    return { matchedCount: 0, modifiedCount: 0, acknowledged: true };
  }

  // Delete operations
  async deleteOne(filter: Partial<T>): Promise<{ deletedCount: number; acknowledged: boolean }> {
    for (const [id, doc] of this.documents.entries()) {
      if (this.matchesFilter(doc, filter)) {
        this.documents.delete(id);
        return { deletedCount: 1, acknowledged: true };
      }
    }
    return { deletedCount: 0, acknowledged: true };
  }

  async deleteMany(filter: Partial<T>): Promise<{ deletedCount: number; acknowledged: boolean }> {
    let deleted = 0;
    const toDelete: string[] = [];

    for (const [id, doc] of this.documents.entries()) {
      if (this.matchesFilter(doc, filter)) {
        toDelete.push(id);
      }
    }

    for (const id of toDelete) {
      this.documents.delete(id);
      deleted++;
    }

    return { deletedCount: deleted, acknowledged: true };
  }

  // Count operations
  async countDocuments(filter: Partial<T> = {}): Promise<number> {
    let count = 0;
    for (const doc of this.documents.values()) {
      if (this.matchesFilter(doc, filter)) {
        count++;
      }
    }
    return count;
  }

  // Test utilities
  clear(): void {
    this.documents.clear();
  }

  size(): number {
    return this.documents.size;
  }

  // Private helpers
  private matchesFilter(
    doc: T,
    filter: Partial<T> | { $or?: Array<Partial<T>> }
  ): boolean {
    // Handle $or operator
    const filterWithOr = filter as { $or?: Array<Partial<T>> };
    if (filterWithOr.$or && Array.isArray(filterWithOr.$or)) {
      return filterWithOr.$or.some((orFilter: Partial<T>) =>
        this.matchesFilter(doc, orFilter)
      );
    }

    // Simple field matching
    for (const [key, value] of Object.entries(filter)) {
      if (key.startsWith('$')) continue; // Skip operators
      if ((doc as Record<string, unknown>)[key] !== value) {
        return false;
      }
    }
    return true;
  }

  private generateId(): string {
    return Math.random().toString(36).substring(2, 15);
  }
}

/**
 * Mock cursor for find operations
 */
class MockCursor<T> {
  constructor(private results: T[]) {}

  async toArray(): Promise<T[]> {
    return this.results;
  }

  limit(n: number): MockCursor<T> {
    this.results = this.results.slice(0, n);
    return this;
  }

  skip(n: number): MockCursor<T> {
    this.results = this.results.slice(n);
    return this;
  }

  sort(): MockCursor<T> {
    // No-op for simplicity
    return this;
  }
}

// =============================================================================
// MOCK DATABASE
// =============================================================================

/**
 * Mock MongoDB database
 */
class MockDb {
  private readonly collections = new Map<string, MockCollection<Document>>();
  readonly databaseName: string;

  constructor(name: string) {
    this.databaseName = name;
  }

  collection<T extends Document = Document>(name: string): MockCollection<T> {
    if (!this.collections.has(name)) {
      this.collections.set(name, new MockCollection(name));
    }
    return this.collections.get(name) as MockCollection<T>;
  }

  listCollections(): { toArray: () => Promise<{ name: string }[]> } {
    return {
      toArray: async () => {
        return Array.from(this.collections.keys()).map((name) => ({ name }));
      },
    };
  }

  clear(): void {
    for (const collection of this.collections.values()) {
      collection.clear();
    }
  }
}

// =============================================================================
// IN-MEMORY DATABASE PROVIDER
// =============================================================================

/**
 * In-memory database provider for testing
 *
 * Provides a MongoDB-compatible interface backed by Map storage.
 */
export class InMemoryDatabaseProvider implements IDatabaseProvider {
  private readonly mockDb: MockDb;
  private connected = true;

  /**
   * Create in-memory database provider
   *
   * @param databaseName - Database name (for identification)
   */
  constructor(databaseName = 'test_db') {
    this.mockDb = new MockDb(databaseName);
  }

  /**
   * Get MongoDB client (mock)
   */
  getClient(): MongoClient {
    // Return a minimal mock that satisfies the type
    return {
      db: () => this.mockDb,
      close: async () => {
        this.connected = false;
      },
    } as unknown as MongoClient;
  }

  /**
   * Get database instance (mock)
   */
  getDb(): Db {
    return this.mockDb as unknown as Db;
  }

  /**
   * Get typed collection
   */
  getCollection<T extends Document = Document>(name: string): Collection<T> {
    return this.mockDb.collection<T>(name) as unknown as Collection<T>;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Disconnect (mock)
   */
  async disconnect(): Promise<void> {
    this.connected = false;
  }

  // ===========================================================================
  // TEST UTILITIES
  // ===========================================================================

  /**
   * Clear all collections
   */
  clear(): void {
    this.mockDb.clear();
  }

  /**
   * Get collection for direct access in tests
   */
  getMockCollection<T extends Document = Document>(name: string): MockCollection<T> {
    return this.mockDb.collection<T>(name);
  }
}
