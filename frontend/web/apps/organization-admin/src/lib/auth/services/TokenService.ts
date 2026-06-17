/**
 * Token Service - Keycloak Token Management
 *
 * Handles Keycloak access token retrieval using Better Auth's native API.
 * Uses auth.api.getAccessToken() which automatically handles token refresh.
 *
 * @module TokenService
 */

import 'server-only';

import { headers } from 'next/headers';
import { auth } from '../index';
import type {
  ITokenService,
  IAuthConfig,
  KeycloakTokenResponse,
} from '../interfaces';

// =============================================================================
// TOKEN SERVICE IMPLEMENTATION
// =============================================================================

/**
 * Token management service
 *
 * Provides Keycloak token operations using Better Auth's native API.
 * The native auth.api.getAccessToken() handles:
 * - Token retrieval from accounts collection
 * - Automatic token refresh when expired
 * - Provider-specific token management
 *
 * @example
 * ```typescript
 * const tokenService = new TokenService(config);
 * const token = await tokenService.getKeycloakAccessToken();
 * ```
 */
export class TokenService implements ITokenService {
  /**
   * Creates a new TokenService instance
   *
   * @param config - Auth configuration for Keycloak integration
   */
  constructor(private readonly config: IAuthConfig) {}

  /**
   * Get Keycloak access token using Better Auth native API
   *
   * Uses auth.api.getAccessToken() which:
   * - Retrieves token from the accounts collection (not session)
   * - Automatically refreshes expired tokens
   * - Returns null if no linked Keycloak account
   *
   * @returns Access token or null if not available
   *
   * @example
   * ```typescript
   * const token = await tokenService.getKeycloakAccessToken();
   * if (token) {
   *   const response = await fetch(GRAPHQL_ENDPOINT, {
   *     headers: { Authorization: `Bearer ${token}` },
   *   });
   * }
   * ```
   */
  async getKeycloakAccessToken(): Promise<string | null> {
    try {
      const requestHeaders = await headers();

      // First verify we have a valid session
      const session = await auth.api.getSession({
        headers: requestHeaders,
      });

      if (!session?.user) {
        return null;
      }

      // Use Better Auth's native API to get the access token
      // This retrieves from accounts collection and handles refresh automatically
      const tokenResponse = await auth.api.getAccessToken({
        body: { providerId: 'keycloak' },
        headers: requestHeaders,
      });

      return tokenResponse?.accessToken ?? null;
    } catch (error) {
      console.error(
        '[TokenService] Failed to get Keycloak access token:',
        error
      );

      // Handle "no linked account" gracefully
      if (error instanceof Error && error.message.includes('No linked account')) {
        return null;
      }

      return null;
    }
  }

  /**
   * Build Keycloak logout URL
   *
   * Constructs the Keycloak logout endpoint URL with proper redirect.
   * Uses the ID token hint for clean logout flow.
   *
   * @param idTokenHint - Optional ID token for logout hint
   * @returns Keycloak logout URL
   *
   * @example
   * ```typescript
   * const session = await sessionService.getSession();
   * const idToken = session?.user?.idToken;
   *
   * const logoutUrl = tokenService.getLogoutUrl(idToken);
   * redirect(logoutUrl);
   * ```
   */
  getLogoutUrl(idTokenHint?: string): string {
    const postLogoutRedirectUri = `${this.config.appUrl}/login`;
    const logoutEndpoint = this.config.logoutEndpoint ||
      `${this.config.keycloakIssuer}/protocol/openid-connect/logout`;

    const params = new URLSearchParams({
      post_logout_redirect_uri: postLogoutRedirectUri,
    });

    if (idTokenHint) {
      params.set('id_token_hint', idTokenHint);
    }

    return `${logoutEndpoint}?${params.toString()}`;
  }

  /**
   * Exchange Better Auth session token for Keycloak access token
   *
   * Uses OAuth2 token exchange grant to obtain a Keycloak access token.
   *
   * @param sessionToken - Better Auth session token
   * @returns Keycloak token response
   *
   * @example
   * ```typescript
   * const tokenResponse = await tokenService.exchangeToken(session.token);
   * console.log('Access token expires in:', tokenResponse.expires_in);
   * ```
   */
  async exchangeToken(sessionToken: string): Promise<KeycloakTokenResponse> {
    const tokenUrl = this.config.tokenEndpoint ||
      `${this.config.keycloakIssuer}/protocol/openid-connect/token`;

    const response = await fetch(tokenUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
        client_id: this.config.keycloakClientId,
        subject_token: sessionToken,
        subject_token_type: 'urn:ietf:params:oauth:token-type:access_token',
      }),
    });

    if (!response.ok) {
      throw new Error(`Token exchange failed: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * Validate if a Keycloak access token is still valid
   *
   * Checks token expiration by decoding the JWT payload.
   *
   * @param token - Keycloak access token
   * @returns True if token is valid and not expired
   *
   * @example
   * ```typescript
   * const isValid = await tokenService.validateToken(accessToken);
   * if (!isValid) {
   *   const newToken = await tokenService.getKeycloakAccessToken();
   * }
   * ```
   */
  async validateToken(token: string): Promise<boolean> {
    try {
      const payload = this.decodeToken(token);
      const exp = payload.exp as number;

      if (!exp) {
        return false;
      }

      const now = Math.floor(Date.now() / 1000);
      return exp > now;
    } catch {
      return false;
    }
  }

  /**
   * Decode a JWT token without verification
   *
   * Extracts the payload from a JWT token for inspection purposes.
   * Does NOT verify the signature.
   *
   * @param token - JWT token to decode
   * @returns Decoded token payload
   *
   * @example
   * ```typescript
   * const payload = tokenService.decodeToken(accessToken);
   * console.log('Token subject:', payload.sub);
   * console.log('Token expiry:', new Date((payload.exp as number) * 1000));
   * ```
   */
  decodeToken(token: string): Record<string, unknown> {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        throw new Error('Invalid JWT format');
      }

      const payload = parts[1];
      const decoded = Buffer.from(payload, 'base64').toString('utf-8');
      return JSON.parse(decoded);
    } catch (error) {
      console.error('[TokenService] Failed to decode token:', error);
      throw new Error('Failed to decode JWT token');
    }
  }

  /**
   * Revoke a Keycloak access token
   *
   * Calls the Keycloak revocation endpoint to invalidate the token.
   *
   * @param token - Token to revoke
   *
   * @example
   * ```typescript
   * await tokenService.revokeToken(oldAccessToken);
   * // Token is now invalid
   * ```
   */
  async revokeToken(token: string): Promise<void> {
    const revokeUrl = `${this.config.keycloakIssuer}/protocol/openid-connect/revoke`;

    const response = await fetch(revokeUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        client_id: this.config.keycloakClientId,
        token,
        token_type_hint: 'access_token',
      }),
    });

    if (!response.ok) {
      throw new Error(`Token revocation failed: ${response.statusText}`);
    }
  }
}
