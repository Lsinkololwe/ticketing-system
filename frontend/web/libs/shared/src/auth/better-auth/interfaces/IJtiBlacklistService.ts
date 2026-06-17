/**
 * JTI Blacklist Service Interface
 *
 * Manages a blacklist of revoked JWT IDs (JTI claims) to prevent
 * session creation with tokens that have been invalidated via
 * Keycloak backchannel logout.
 *
 * @see https://openid.net/specs/openid-connect-backchannel-1_0.html
 *
 * @example
 * ```typescript
 * // Add JTI on backchannel logout
 * await blacklist.add({
 *   jti: 'abc123',
 *   userId: 'user-456',
 *   reason: 'backchannel_logout',
 * });
 *
 * // Check during session creation
 * if (await blacklist.isBlacklisted('abc123')) {
 *   throw new Error('Token has been revoked');
 * }
 * ```
 *
 * @module libs/shared/src/auth/better-auth/interfaces/IJtiBlacklistService
 */

// =============================================================================
// BLACKLIST ENTRY TYPES
// =============================================================================

/**
 * Reason for blacklisting a JTI
 */
export type BlacklistReason =
  | 'backchannel_logout'  // Keycloak backchannel logout
  | 'manual_revoke'       // Admin manually revoked
  | 'session_revoke'      // User revoked own session
  | 'security_incident';  // Security-related revocation

/**
 * Blacklist entry data
 */
export interface BlacklistEntry {
  /** JWT ID that was blacklisted */
  jti: string;

  /** User ID (sub claim) */
  userId: string;

  /** Keycloak session ID (sid claim), if available */
  sessionId?: string;

  /** When the JTI was blacklisted (ISO string) */
  blacklistedAt: string;

  /** Why it was blacklisted */
  reason: BlacklistReason;

  /** Original token expiry time (epoch seconds) */
  tokenExpiry?: number;
}

/**
 * Input for adding a JTI to blacklist
 */
export type BlacklistInput = Omit<BlacklistEntry, 'blacklistedAt'>;

// =============================================================================
// JTI BLACKLIST SERVICE INTERFACE
// =============================================================================

/**
 * JTI Blacklist service contract
 *
 * Implementations store blacklisted JTIs with TTL matching token expiry.
 * Production uses Redis, tests can use in-memory storage.
 */
export interface IJtiBlacklistService {
  /**
   * Add a JTI to the blacklist
   *
   * Called when:
   * - Keycloak sends backchannel logout
   * - Admin revokes a session
   * - User logs out
   *
   * @param entry - Blacklist entry details
   * @returns true if added successfully
   *
   * @example
   * ```typescript
   * await blacklist.add({
   *   jti: claims.jti,
   *   userId: claims.sub,
   *   sessionId: claims.sid,
   *   reason: 'backchannel_logout',
   *   tokenExpiry: claims.exp,
   * });
   * ```
   */
  add(entry: BlacklistInput): Promise<boolean>;

  /**
   * Check if a JTI is blacklisted
   *
   * Called during:
   * - Session creation (OAuth callback)
   * - Token validation
   *
   * @param jti - JWT ID to check
   * @returns true if blacklisted
   *
   * @example
   * ```typescript
   * if (await blacklist.isBlacklisted(tokenJti)) {
   *   throw new AuthError('Token has been revoked');
   * }
   * ```
   */
  isBlacklisted(jti: string): Promise<boolean>;

  /**
   * Get blacklist entry details
   *
   * For debugging and audit logging.
   *
   * @param jti - JWT ID to look up
   * @returns Entry details or null if not found
   */
  get(jti: string): Promise<BlacklistEntry | null>;

  /**
   * Remove a JTI from the blacklist
   *
   * Use with caution - allows previously revoked tokens.
   * Primarily for admin override scenarios.
   *
   * @param jti - JWT ID to remove
   * @returns true if removed, false if not found
   */
  remove(jti: string): Promise<boolean>;

  /**
   * Blacklist all tokens for a user issued before a timestamp
   *
   * Used for:
   * - Account deactivation
   * - Password change (invalidate all sessions)
   * - Security incident response
   *
   * @param userId - User ID to blacklist
   * @param beforeTimestamp - Reject tokens issued before this time (epoch seconds)
   * @param ttlSeconds - How long to enforce this blacklist
   * @returns true if added successfully
   */
  blacklistUserTokensBefore(
    userId: string,
    beforeTimestamp: number,
    ttlSeconds?: number
  ): Promise<boolean>;

  /**
   * Check if a user's token is blacklisted by timestamp
   *
   * @param userId - User ID
   * @param tokenIssuedAt - Token's iat claim (epoch seconds)
   * @returns true if token was issued before blacklist cutoff
   */
  isUserTokenBlacklisted(
    userId: string,
    tokenIssuedAt: number
  ): Promise<boolean>;

  /**
   * Get blacklist statistics
   *
   * For monitoring dashboards and health checks.
   *
   * @returns Count of blacklisted JTIs and users
   */
  getStats(): Promise<BlacklistStats>;
}

/**
 * Blacklist statistics
 */
export interface BlacklistStats {
  /** Number of blacklisted JTIs */
  jtiCount: number;

  /** Number of users with token timestamp blacklists */
  userCount: number;
}
