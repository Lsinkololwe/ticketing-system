/**
 * HTTP client for identity-service's internal revocation API.
 *
 * Revocation writes are routed through the service that owns the data, so there is one writer,
 * one key layout and one durable system of record across the backend and every frontend app.
 *
 * ## Error handling
 *
 * Every method either succeeds or throws, so callers can distinguish "revoked" from "could not
 * tell" and "written" from "not written".
 *
 * @module libs/shared/src/auth/revocation/IdentityRevocationClient
 */

import { RevocationWriteError } from './errors';
import type {
  RevocationDecision,
  RevocationIdentifier,
  RevokeSignOutInput,
} from './types';

// =============================================================================
// OPTIONS
// =============================================================================

export interface IdentityRevocationClientOptions {
  /** Base URL of identity-service, e.g. `http://localhost:8083`. */
  baseUrl: string;

  /**
   * Supplies a bearer token with `internal-read`/`internal-write` scope.
   *
   * A function rather than a string so the caller owns caching and refresh.
   */
  getAccessToken: () => Promise<string>;

  /** Per-request budget. Defaults to 2000ms. */
  timeoutMs?: number;

  /** Injectable for tests. Defaults to global `fetch`. */
  fetchImpl?: typeof fetch;
}

interface LogoutResponseBody {
  revoked: boolean;
  records: { id: string }[];
}

interface CheckResponseBody {
  decision: RevocationDecision;
}

// =============================================================================
// CLIENT
// =============================================================================

export class IdentityRevocationClient {
  private readonly baseUrl: string;
  private readonly getAccessToken: () => Promise<string>;
  private readonly timeoutMs: number;
  private readonly fetchImpl: typeof fetch;

  constructor(options: IdentityRevocationClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.getAccessToken = options.getAccessToken;
    this.timeoutMs = options.timeoutMs ?? 2000;
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  /**
   * Revokes a sign-out's token, session and user in one durable call.
   *
   * @returns the number of identifiers persisted
   * @throws {RevocationWriteError} if the revocation did not reach the system of record
   */
  async revokeSignOut(input: RevokeSignOutInput): Promise<number> {
    const response = await this.request('/api/internal/revocations/logout', {
      method: 'POST',
      body: JSON.stringify({
        jti: input.jti,
        sid: input.sid,
        sub: input.sub,
        reason: input.reason,
        revokedBy: input.revokedBy ?? 'organizer-web',
      }),
    });

    if (!response.ok) {
      throw new RevocationWriteError(
        `identity-service refused the revocation (HTTP ${response.status}). ` +
          'The token has NOT been revoked.'
      );
    }

    const body = (await response.json()) as LogoutResponseBody;
    return body.records?.length ?? 0;
  }

  /**
   * The durable read, used when the local Redis cache cannot answer.
   *
   * @throws if identity-service cannot be reached — the caller reports `UNKNOWN`, not `ACTIVE`
   */
  async check(identifiers: RevocationIdentifier[]): Promise<RevocationDecision> {
    const response = await this.request('/api/internal/revocations/check', {
      method: 'POST',
      body: JSON.stringify({ identifiers }),
    });

    if (!response.ok) {
      throw new Error(
        `identity-service could not resolve the revocation check (HTTP ${response.status})`
      );
    }

    const body = (await response.json()) as CheckResponseBody;
    if (!body?.decision) {
      // No decision means the check did not resolve, which is not the same as ACTIVE.
      throw new Error('identity-service returned no revocation decision');
    }
    return body.decision;
  }

  /** Liveness of the durable store. Throws when unreachable. */
  async ping(): Promise<void> {
    const response = await this.request('/api/internal/revocations/health', {
      method: 'GET',
    });
    if (!response.ok) {
      throw new Error(`identity-service revocation health returned ${response.status}`);
    }
  }

  // ===========================================================================
  // PRIVATE
  // ===========================================================================

  private async request(path: string, init: RequestInit): Promise<Response> {
    const token = await this.getAccessToken();
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      return await this.fetchImpl(`${this.baseUrl}${path}`, {
        ...init,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          ...(init.headers ?? {}),
        },
        cache: 'no-store',
      });
    } finally {
      clearTimeout(timer);
    }
  }
}
