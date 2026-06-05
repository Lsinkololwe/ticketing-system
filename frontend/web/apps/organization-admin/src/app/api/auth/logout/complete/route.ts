/**
 * Complete Logout Endpoint - Double Invalidation
 *
 * Implements OWASP-compliant logout with dual storage invalidation:
 * 1. Delete session from Redis (immediate)
 * 2. Delete session from MongoDB (immediate)
 * 3. Clear cookie cache (via Better Auth signOut)
 * 4. Blacklist access token (defense-in-depth)
 * 5. Audit log the event
 *
 * The client then redirects to Keycloak's end_session endpoint for SSO logout.
 *
 * @see https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
 */

import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import {
  authPromise,
  getRedisClient,
  getMongoClientPromise,
} from '@/lib/auth';
import {
  blacklistTokenFromJwt,
  removeSessionFromUserIndex,
  revokeUserSessions,
} from '@/lib/auth/token-blacklist';

// =============================================================================
// CONSTANTS
// =============================================================================

const REDIS_KEY_PREFIX = 'pml-organizer:';
const MONGODB_DATABASE = process.env.MONGODB_DATABASE || 'dev_ticketing';

// =============================================================================
// TYPES
// =============================================================================

interface BetterAuthSession {
  session: {
    id: string;
    userId: string;
    token: string;
    expiresAt: Date;
  };
  user: {
    id: string;
    email: string;
    name?: string;
  };
}

interface LogoutResults {
  redisSessionDeleted: boolean;
  redisTrackingCleaned: boolean;
  mongoSessionDeleted: boolean;
  tokenBlacklisted: boolean;
  userRevoked: boolean;
  betterAuthSignedOut: boolean;
  auditLogged: boolean;
}

// =============================================================================
// ROUTE HANDLER
// =============================================================================

/**
 * POST /api/auth/logout/complete
 *
 * Performs complete logout with double invalidation.
 */
export async function POST(request: NextRequest) {
  const startTime = Date.now();
  const results: LogoutResults = {
    redisSessionDeleted: false,
    redisTrackingCleaned: false,
    mongoSessionDeleted: false,
    tokenBlacklisted: false,
    userRevoked: false,
    betterAuthSignedOut: false,
    auditLogged: false,
  };

  let sessionToken: string | undefined;
  let userId: string | undefined;

  try {
    const auth = await authPromise;

    // 1. Get current session cookie
    const cookieStore = await cookies();
    const sessionCookie = cookieStore.get('pml_org.session_token');

    if (!sessionCookie?.value) {
      console.log('[Logout] No session cookie found');
      return NextResponse.json({
        success: true,
        message: 'No active session',
        results,
      });
    }

    sessionToken = sessionCookie.value;

    // 2. Get session data from Better Auth
    let sessionData: BetterAuthSession | null = null;
    try {
      const result = await auth.api.getSession({
        headers: request.headers,
      });
      sessionData = result as BetterAuthSession | null;
      userId = sessionData?.user?.id;

      console.log('[Logout] Session retrieved:', {
        hasSession: !!sessionData?.session,
        userId: userId?.slice(0, 8) + '...',
      });
    } catch (sessionError) {
      console.warn('[Logout] Failed to get session:', sessionError);
    }

    // 3. DOUBLE INVALIDATION - Redis
    try {
      const redis = getRedisClient();

      // Delete the session from Redis
      const redisKey = `${REDIS_KEY_PREFIX}${sessionToken}`;
      const deleted = await redis.del(redisKey);
      results.redisSessionDeleted = deleted > 0;

      // Clean up active sessions tracking
      if (userId) {
        const activeKey = `${REDIS_KEY_PREFIX}active_sessions:${userId}`;
        await redis.srem(activeKey, sessionToken);
        results.redisTrackingCleaned = true;
      }

      console.log('[Logout] Redis invalidation:', {
        sessionDeleted: results.redisSessionDeleted,
        trackingCleaned: results.redisTrackingCleaned,
      });
    } catch (redisError) {
      console.error('[Logout] Redis invalidation failed:', redisError);
    }

    // 4. DOUBLE INVALIDATION - MongoDB
    try {
      const mongoClient = await getMongoClientPromise();
      const db = mongoClient.db(MONGODB_DATABASE);

      const deleteResult = await db.collection('session').deleteOne({
        token: sessionToken,
      });

      results.mongoSessionDeleted = deleteResult.deletedCount > 0;
      console.log('[Logout] MongoDB invalidation:', {
        deleted: results.mongoSessionDeleted,
      });
    } catch (mongoError) {
      console.error('[Logout] MongoDB invalidation failed:', mongoError);
    }

    // 5. Blacklist access token (defense-in-depth)
    if (sessionData?.session) {
      try {
        const redis = getRedisClient();
        const storedSession = await redis.get(
          `${REDIS_KEY_PREFIX}${sessionData.session.token}`
        );

        if (storedSession) {
          const sessionObj = JSON.parse(storedSession);
          const accessToken = sessionObj.accessToken || sessionObj.token;

          if (accessToken?.includes('.')) {
            results.tokenBlacklisted = await blacklistTokenFromJwt(accessToken);
          }
        }
      } catch (tokenError) {
        console.warn('[Logout] Token blacklisting failed:', tokenError);
      }
    }

    // 6. Create user revocation entry (defense-in-depth)
    if (userId) {
      try {
        await revokeUserSessions(userId);
        results.userRevoked = true;
      } catch (revokeError) {
        console.warn('[Logout] User revocation failed:', revokeError);
      }

      // Clean up session index
      if (sessionData?.session) {
        try {
          await removeSessionFromUserIndex(userId, sessionData.session.id);
        } catch {
          // Ignore
        }
      }
    }

    // 7. Sign out from Better Auth (clears cookie)
    try {
      await auth.api.signOut({
        headers: request.headers,
      });
      results.betterAuthSignedOut = true;
      console.log('[Logout] Better Auth signOut completed');
    } catch (signOutError) {
      console.warn('[Logout] Better Auth signOut failed:', signOutError);
    }

    // 8. Audit log
    try {
      const redis = getRedisClient();
      const auditEvent = {
        type: 'SESSION_LOGOUT',
        sessionToken: sessionToken?.slice(0, 8) + '...',
        userId,
        timestamp: new Date().toISOString(),
        ipAddress: request.headers.get('x-forwarded-for') || 'unknown',
        results,
        duration: Date.now() - startTime,
      };

      await redis.lpush(`${REDIS_KEY_PREFIX}audit:logout`, JSON.stringify(auditEvent));
      await redis.ltrim(`${REDIS_KEY_PREFIX}audit:logout`, 0, 999);
      results.auditLogged = true;
    } catch (auditError) {
      console.warn('[Logout] Audit logging failed:', auditError);
    }

    const duration = Date.now() - startTime;
    console.log(`[Logout] Complete in ${duration}ms:`, results);

    return NextResponse.json({
      success: true,
      results,
      duration,
    });
  } catch (error) {
    const err = error as Error;
    console.error('[Logout] Failed:', err.message);

    // Attempt cleanup even on error
    try {
      const auth = await authPromise;
      await auth.api.signOut({ headers: request.headers });
    } catch {
      // Ignore
    }

    return NextResponse.json(
      {
        success: false,
        error: err.message,
        results,
      },
      { status: 500 }
    );
  }
}

/**
 * GET /api/auth/logout/complete
 *
 * Health check / info endpoint
 */
export async function GET() {
  return NextResponse.json({
    endpoint: 'complete-logout',
    description: 'OWASP-compliant logout with double invalidation',
    method: 'POST',
    securityFeatures: [
      'Redis session deletion (immediate)',
      'MongoDB session deletion (immediate)',
      'Active sessions tracking cleanup',
      'Access token blacklisting',
      'User revocation entry',
      'Better Auth cookie clearing',
      'Audit logging',
    ],
  });
}
