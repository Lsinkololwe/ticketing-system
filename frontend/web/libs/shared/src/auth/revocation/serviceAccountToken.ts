/**
 * Client-credentials token provider for calls to identity-service's internal API.
 *
 * Shared by every app that reaches `/api/internal/**`; app-specific config (client id, secret,
 * token URL) is passed in.
 *
 * Tokens are cached and refreshed ahead of expiry so a long-running request cannot present an
 * expired one.
 *
 * @module libs/shared/src/auth/revocation/serviceAccountToken
 */

export interface ServiceAccountTokenOptions {
  tokenUrl: string;
  clientId: string;
  clientSecret: string;
  /** Space-separated. Needs at least `internal-write` to record revocations. */
  scope?: string;
  /** Refresh this many seconds before actual expiry. Defaults to 30. */
  refreshSkewSeconds?: number;
  fetchImpl?: typeof fetch;
}

interface CachedToken {
  accessToken: string;
  expiresAtMs: number;
}

/**
 * Builds a `getAccessToken` function suitable for {@link IdentityRevocationClient}.
 *
 * Concurrent callers share one in-flight request rather than stampeding the token endpoint.
 */
export function createServiceAccountTokenProvider(
  options: ServiceAccountTokenOptions
): () => Promise<string> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const skewMs = (options.refreshSkewSeconds ?? 30) * 1000;
  const scope = options.scope ?? 'openid internal-read internal-write';

  let cached: CachedToken | null = null;
  let inFlight: Promise<string> | null = null;

  async function fetchToken(): Promise<string> {
    const body = new URLSearchParams({
      grant_type: 'client_credentials',
      client_id: options.clientId,
      client_secret: options.clientSecret,
      scope,
    });

    const response = await fetchImpl(options.tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
      cache: 'no-store',
    });

    if (!response.ok) {
      throw new Error(
        `Could not obtain a service-account token (HTTP ${response.status}). ` +
          'Revocations cannot be recorded without one.'
      );
    }

    const payload = (await response.json()) as {
      access_token: string;
      expires_in: number;
    };

    cached = {
      accessToken: payload.access_token,
      expiresAtMs: Date.now() + payload.expires_in * 1000 - skewMs,
    };
    return cached.accessToken;
  }

  return async function getAccessToken(): Promise<string> {
    if (cached && Date.now() < cached.expiresAtMs) {
      return cached.accessToken;
    }
    if (!inFlight) {
      inFlight = fetchToken().finally(() => {
        inFlight = null;
      });
    }
    return inFlight;
  };
}
