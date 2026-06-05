/**
 * Custom MongoDB Adapter for Better Auth + Keycloak Integration
 *
 * This adapter implements a "shared user" pattern where:
 * - Better Auth manages sessions and OAuth accounts
 * - The existing `users` collection (from Identity Service) is the single source of truth
 * - No duplicate user data is stored
 *
 * Collection Strategy:
 * - `users` - Shared with Identity Service (read-only from Better Auth)
 * - `auth_sessions` - Better Auth session management
 * - `auth_accounts` - OAuth account linking (Keycloak tokens)
 * - `auth_verifications` - Verification tokens (password reset, email verification)
 *
 * @see https://www.better-auth.com/docs/concepts/database#custom-adapters
 */

import type { Adapter, AdapterAccount, AdapterSession, AdapterUser, Where } from 'better-auth';
import type { Db, Collection, ObjectId, Filter, UpdateFilter, Document } from 'mongodb';

// =============================================================================
// COLLECTION NAMES
// =============================================================================

const COLLECTIONS = {
  // Shared with Identity Service - Better Auth only READS from this
  USERS: 'users',
  // Better Auth managed collections
  SESSIONS: 'auth_sessions',
  ACCOUNTS: 'auth_accounts',
  VERIFICATIONS: 'auth_verifications',
} as const;

// =============================================================================
// TYPE DEFINITIONS
// =============================================================================

/**
 * User document structure (matches Identity Service User entity)
 * Better Auth will only read from this, never write user profile data
 */
interface UserDocument {
  _id: string; // Keycloak UUID (not ObjectId)
  email: string;
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  emailVerified: boolean;
  phoneVerified?: boolean;
  profileImage?: string;
  roles: string[];
  active: boolean;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Session document structure
 */
interface SessionDocument {
  _id: string;
  userId: string;
  token: string;
  expiresAt: Date;
  ipAddress?: string;
  userAgent?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Account document structure (OAuth accounts from Keycloak)
 */
interface AccountDocument {
  _id: string;
  userId: string;
  accountId: string; // Keycloak user ID
  providerId: string; // 'keycloak'
  accessToken?: string;
  refreshToken?: string;
  accessTokenExpiresAt?: Date;
  refreshTokenExpiresAt?: Date;
  scope?: string;
  idToken?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Verification document structure
 */
interface VerificationDocument {
  _id: string;
  identifier: string;
  value: string;
  expiresAt: Date;
  createdAt: Date;
  updatedAt: Date;
}

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Convert Better Auth where clause to MongoDB filter
 */
function whereToFilter<T extends Document>(where: Where[]): Filter<T> {
  const filter: Record<string, unknown> = {};

  for (const condition of where) {
    const { field, value, operator = 'eq' } = condition;
    const fieldName = field === 'id' ? '_id' : field;

    switch (operator) {
      case 'eq':
        filter[fieldName] = value;
        break;
      case 'ne':
        filter[fieldName] = { $ne: value };
        break;
      case 'gt':
        filter[fieldName] = { $gt: value };
        break;
      case 'gte':
        filter[fieldName] = { $gte: value };
        break;
      case 'lt':
        filter[fieldName] = { $lt: value };
        break;
      case 'lte':
        filter[fieldName] = { $lte: value };
        break;
      case 'in':
        filter[fieldName] = { $in: value };
        break;
      case 'contains':
        filter[fieldName] = { $regex: value, $options: 'i' };
        break;
      case 'starts_with':
        filter[fieldName] = { $regex: `^${value}`, $options: 'i' };
        break;
      case 'ends_with':
        filter[fieldName] = { $regex: `${value}$`, $options: 'i' };
        break;
      default:
        filter[fieldName] = value;
    }
  }

  return filter as Filter<T>;
}

/**
 * Map MongoDB user document to Better Auth user format
 */
function mapUserToAdapter(doc: UserDocument | null): AdapterUser | null {
  if (!doc) return null;

  return {
    id: doc._id,
    email: doc.email,
    emailVerified: doc.emailVerified,
    name: [doc.firstName, doc.lastName].filter(Boolean).join(' ') || doc.email,
    image: doc.profileImage || null,
    createdAt: doc.createdAt,
    updatedAt: doc.updatedAt,
  };
}

/**
 * Map MongoDB session document to Better Auth session format
 */
function mapSessionToAdapter(doc: SessionDocument | null): AdapterSession | null {
  if (!doc) return null;

  return {
    id: doc._id,
    userId: doc.userId,
    token: doc.token,
    expiresAt: doc.expiresAt,
    ipAddress: doc.ipAddress,
    userAgent: doc.userAgent,
    createdAt: doc.createdAt,
    updatedAt: doc.updatedAt,
  };
}

/**
 * Map MongoDB account document to Better Auth account format
 */
function mapAccountToAdapter(doc: AccountDocument | null): AdapterAccount | null {
  if (!doc) return null;

  return {
    id: doc._id,
    userId: doc.userId,
    accountId: doc.accountId,
    providerId: doc.providerId,
    accessToken: doc.accessToken,
    refreshToken: doc.refreshToken,
    accessTokenExpiresAt: doc.accessTokenExpiresAt,
    refreshTokenExpiresAt: doc.refreshTokenExpiresAt,
    scope: doc.scope,
    idToken: doc.idToken,
    createdAt: doc.createdAt,
    updatedAt: doc.updatedAt,
  };
}

// =============================================================================
// CUSTOM ADAPTER
// =============================================================================

export interface KeycloakMongoAdapterOptions {
  /**
   * Whether to allow Better Auth to create users.
   * Set to false to require users to exist in Identity Service first.
   * @default false
   */
  allowUserCreation?: boolean;

  /**
   * Whether to sync user profile updates back to the users collection.
   * Set to false since Identity Service is the source of truth.
   * @default false
   */
  syncUserUpdates?: boolean;
}

/**
 * Creates a custom MongoDB adapter for Better Auth that:
 * 1. Shares the `users` collection with Identity Service (read-only)
 * 2. Manages sessions in `auth_sessions` collection
 * 3. Manages OAuth accounts in `auth_accounts` collection
 * 4. Manages verifications in `auth_verifications` collection
 */
export function keycloakMongoAdapter(
  db: Db,
  options: KeycloakMongoAdapterOptions = {}
): Adapter {
  const { allowUserCreation = false, syncUserUpdates = false } = options;

  // Get collection references
  const usersCollection = db.collection<UserDocument>(COLLECTIONS.USERS);
  const sessionsCollection = db.collection<SessionDocument>(COLLECTIONS.SESSIONS);
  const accountsCollection = db.collection<AccountDocument>(COLLECTIONS.ACCOUNTS);
  const verificationsCollection = db.collection<VerificationDocument>(COLLECTIONS.VERIFICATIONS);

  // Ensure indexes exist
  const ensureIndexes = async () => {
    // Sessions indexes
    await sessionsCollection.createIndex({ token: 1 }, { unique: true });
    await sessionsCollection.createIndex({ userId: 1 });
    await sessionsCollection.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });

    // Accounts indexes
    await accountsCollection.createIndex({ providerId: 1, accountId: 1 }, { unique: true });
    await accountsCollection.createIndex({ userId: 1 });

    // Verifications indexes
    await verificationsCollection.createIndex({ identifier: 1 });
    await verificationsCollection.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
  };

  // Initialize indexes (fire and forget)
  ensureIndexes().catch((err) => {
    console.error('[KeycloakMongoAdapter] Failed to create indexes:', err);
  });

  return {
    id: 'keycloak-mongo-adapter',

    // =========================================================================
    // USER OPERATIONS (Read-only from shared collection)
    // =========================================================================

    async createUser(user) {
      if (!allowUserCreation) {
        // User must be created by Identity Service via Keycloak sync
        // Check if user already exists (created by Identity Service)
        const existingUser = await usersCollection.findOne({ email: user.email });
        if (existingUser) {
          return mapUserToAdapter(existingUser)!;
        }

        throw new Error(
          'User creation is disabled. Users must be created through Keycloak registration.'
        );
      }

      // If user creation is allowed (not recommended), create minimal user
      const now = new Date();
      const newUser: UserDocument = {
        _id: user.id || crypto.randomUUID(),
        email: user.email,
        firstName: user.name?.split(' ')[0] || '',
        lastName: user.name?.split(' ').slice(1).join(' ') || '',
        emailVerified: user.emailVerified || false,
        profileImage: user.image || undefined,
        roles: ['CUSTOMER'], // Default role
        active: true,
        createdAt: now,
        updatedAt: now,
      };

      await usersCollection.insertOne(newUser);
      return mapUserToAdapter(newUser)!;
    },

    async findUserById(userId) {
      const user = await usersCollection.findOne({ _id: userId });
      return mapUserToAdapter(user);
    },

    async findUserByEmail(email) {
      const user = await usersCollection.findOne({ email });
      return mapUserToAdapter(user);
    },

    async updateUser(userId, data) {
      if (!syncUserUpdates) {
        // Don't update user data - Identity Service is source of truth
        // Just return the current user
        const user = await usersCollection.findOne({ _id: userId });
        return mapUserToAdapter(user)!;
      }

      const updateData: Partial<UserDocument> = {
        updatedAt: new Date(),
      };

      if (data.email) updateData.email = data.email;
      if (data.emailVerified !== undefined) updateData.emailVerified = data.emailVerified;
      if (data.name) {
        const [firstName, ...lastNameParts] = data.name.split(' ');
        updateData.firstName = firstName;
        updateData.lastName = lastNameParts.join(' ');
      }
      if (data.image) updateData.profileImage = data.image;

      await usersCollection.updateOne(
        { _id: userId },
        { $set: updateData }
      );

      const user = await usersCollection.findOne({ _id: userId });
      return mapUserToAdapter(user)!;
    },

    async deleteUser(userId) {
      // Delete associated sessions and accounts
      await sessionsCollection.deleteMany({ userId });
      await accountsCollection.deleteMany({ userId });

      // Don't delete the user from shared collection
      // Identity Service handles user lifecycle
      console.warn(
        '[KeycloakMongoAdapter] deleteUser called but user deletion is handled by Identity Service'
      );
    },

    async linkAccount(account) {
      const now = new Date();
      const newAccount: AccountDocument = {
        _id: account.id || crypto.randomUUID(),
        userId: account.userId,
        accountId: account.accountId,
        providerId: account.providerId,
        accessToken: account.accessToken,
        refreshToken: account.refreshToken,
        accessTokenExpiresAt: account.accessTokenExpiresAt,
        refreshTokenExpiresAt: account.refreshTokenExpiresAt,
        scope: account.scope,
        idToken: account.idToken,
        createdAt: now,
        updatedAt: now,
      };

      // Upsert to handle re-linking
      await accountsCollection.updateOne(
        { providerId: account.providerId, accountId: account.accountId },
        { $set: newAccount },
        { upsert: true }
      );

      return mapAccountToAdapter(newAccount)!;
    },

    async findAccounts(userId) {
      const accounts = await accountsCollection.find({ userId }).toArray();
      return accounts.map((acc) => mapAccountToAdapter(acc)!);
    },

    async findAccountByProvider(providerId, accountId) {
      const account = await accountsCollection.findOne({ providerId, accountId });
      return mapAccountToAdapter(account);
    },

    async updateAccount(accountId, data) {
      const updateData: Partial<AccountDocument> = {
        updatedAt: new Date(),
      };

      if (data.accessToken) updateData.accessToken = data.accessToken;
      if (data.refreshToken) updateData.refreshToken = data.refreshToken;
      if (data.accessTokenExpiresAt) updateData.accessTokenExpiresAt = data.accessTokenExpiresAt;
      if (data.refreshTokenExpiresAt) updateData.refreshTokenExpiresAt = data.refreshTokenExpiresAt;
      if (data.scope) updateData.scope = data.scope;
      if (data.idToken) updateData.idToken = data.idToken;

      await accountsCollection.updateOne(
        { _id: accountId },
        { $set: updateData }
      );

      const account = await accountsCollection.findOne({ _id: accountId });
      return mapAccountToAdapter(account)!;
    },

    async deleteAccount(accountId) {
      await accountsCollection.deleteOne({ _id: accountId });
    },

    // =========================================================================
    // SESSION OPERATIONS
    // =========================================================================

    async createSession(session) {
      const now = new Date();
      const newSession: SessionDocument = {
        _id: session.id || crypto.randomUUID(),
        userId: session.userId,
        token: session.token,
        expiresAt: session.expiresAt,
        ipAddress: session.ipAddress,
        userAgent: session.userAgent,
        createdAt: now,
        updatedAt: now,
      };

      await sessionsCollection.insertOne(newSession);
      return mapSessionToAdapter(newSession)!;
    },

    async findSession(token) {
      const session = await sessionsCollection.findOne({ token });
      return mapSessionToAdapter(session);
    },

    async findSessions(userId) {
      const sessions = await sessionsCollection.find({ userId }).toArray();
      return sessions.map((sess) => mapSessionToAdapter(sess)!);
    },

    async updateSession(sessionId, data) {
      const updateData: Partial<SessionDocument> = {
        updatedAt: new Date(),
      };

      if (data.expiresAt) updateData.expiresAt = data.expiresAt;

      await sessionsCollection.updateOne(
        { _id: sessionId },
        { $set: updateData }
      );

      const session = await sessionsCollection.findOne({ _id: sessionId });
      return mapSessionToAdapter(session)!;
    },

    async deleteSession(sessionId) {
      await sessionsCollection.deleteOne({ _id: sessionId });
    },

    async deleteSessions(userId) {
      await sessionsCollection.deleteMany({ userId });
    },

    // =========================================================================
    // VERIFICATION OPERATIONS
    // =========================================================================

    async createVerification(verification) {
      const now = new Date();
      const newVerification: VerificationDocument = {
        _id: verification.id || crypto.randomUUID(),
        identifier: verification.identifier,
        value: verification.value,
        expiresAt: verification.expiresAt,
        createdAt: now,
        updatedAt: now,
      };

      await verificationsCollection.insertOne(newVerification);

      return {
        id: newVerification._id,
        identifier: newVerification.identifier,
        value: newVerification.value,
        expiresAt: newVerification.expiresAt,
        createdAt: newVerification.createdAt,
        updatedAt: newVerification.updatedAt,
      };
    },

    async findVerification(identifier, value) {
      const verification = await verificationsCollection.findOne({
        identifier,
        value,
        expiresAt: { $gt: new Date() },
      });

      if (!verification) return null;

      return {
        id: verification._id,
        identifier: verification.identifier,
        value: verification.value,
        expiresAt: verification.expiresAt,
        createdAt: verification.createdAt,
        updatedAt: verification.updatedAt,
      };
    },

    async deleteVerification(id) {
      await verificationsCollection.deleteOne({ _id: id });
    },

    // =========================================================================
    // GENERIC OPERATIONS
    // =========================================================================

    async create({ model, data }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const now = new Date();
      const doc = {
        _id: (data as { id?: string }).id || crypto.randomUUID(),
        ...data,
        createdAt: now,
        updatedAt: now,
      };
      delete (doc as { id?: string }).id;

      await collection.insertOne(doc);

      return { id: doc._id, ...data } as Record<string, unknown>;
    },

    async findOne({ model, where }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const filter = whereToFilter<Document>(where);
      const doc = await collection.findOne(filter);

      if (!doc) return null;

      const { _id, ...rest } = doc;
      return { id: _id, ...rest } as Record<string, unknown>;
    },

    async findMany({ model, where, limit, offset, sortBy }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const filter = where ? whereToFilter<Document>(where) : {};
      let cursor = collection.find(filter);

      if (sortBy) {
        const sort: Record<string, 1 | -1> = {};
        sort[sortBy.field === 'id' ? '_id' : sortBy.field] = sortBy.direction === 'asc' ? 1 : -1;
        cursor = cursor.sort(sort);
      }

      if (offset) cursor = cursor.skip(offset);
      if (limit) cursor = cursor.limit(limit);

      const docs = await cursor.toArray();
      return docs.map((doc) => {
        const { _id, ...rest } = doc;
        return { id: _id, ...rest } as Record<string, unknown>;
      });
    },

    async update({ model, where, data }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const filter = whereToFilter<Document>(where);
      const updateDoc = {
        ...data,
        updatedAt: new Date(),
      };
      delete (updateDoc as { id?: string }).id;

      await collection.updateOne(filter, { $set: updateDoc });

      const updated = await collection.findOne(filter);
      if (!updated) return null;

      const { _id, ...rest } = updated;
      return { id: _id, ...rest } as Record<string, unknown>;
    },

    async delete({ model, where }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const filter = whereToFilter<Document>(where);
      await collection.deleteOne(filter);
    },

    async deleteMany({ model, where }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const filter = whereToFilter<Document>(where);
      const result = await collection.deleteMany(filter);
      return result.deletedCount;
    },

    async count({ model, where }) {
      const collectionMap: Record<string, Collection<Document>> = {
        user: usersCollection as unknown as Collection<Document>,
        session: sessionsCollection as unknown as Collection<Document>,
        account: accountsCollection as unknown as Collection<Document>,
        verification: verificationsCollection as unknown as Collection<Document>,
      };

      const collection = collectionMap[model];
      if (!collection) {
        throw new Error(`Unknown model: ${model}`);
      }

      const filter = where ? whereToFilter<Document>(where) : {};
      return collection.countDocuments(filter);
    },
  };
}

export default keycloakMongoAdapter;
