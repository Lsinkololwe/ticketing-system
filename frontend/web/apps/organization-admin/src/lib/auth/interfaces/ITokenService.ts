import 'server-only';
import type { KeycloakTokenResponse } from './types';

/**
 * Token service interface for managing Keycloak authentication tokens.
 *
 * This service handles token exchange between Better Auth and Keycloak,
 * enabling access to GraphQL APIs that require Keycloak JWT tokens.
 *
 * @example
 * ```typescript
 * const tokenService: ITokenService = new TokenService();
 * const accessToken = await tokenService.getKeycloakAccessToken();
 * ```
 */
export interface ITokenService {
  /**
   * Retrieves a Keycloak access token using Better Auth session.
   *
   * This method exchanges the Better Auth session for a Keycloak access token
   * using OAuth2 token exchange. The token is cached and refreshed automatically
   * when expired.
   *
   * @returns Promise resolving to Keycloak access token or null if unavailable
   * @throws {Error} When token exchange fails or session is invalid
   *
   * @example
   * ```typescript
   * const token = await tokenService.getKeycloakAccessToken();
   * if (token) {
   *   // Use token for GraphQL requests
   *   const response = await fetch(graphqlEndpoint, {
   *     headers: { Authorization: `Bearer ${token}` }
   *   });
   * }
   * ```
   */
  getKeycloakAccessToken(): Promise<string | null>;

  /**
   * Generates the Keycloak logout URL for complete session termination.
   *
   * This method constructs the Keycloak end_session_endpoint URL with
   * the appropriate redirect URI to ensure complete logout across both
   * Better Auth and Keycloak.
   *
   * @param idTokenHint - Optional ID token hint for logout
   * @returns Keycloak logout URL
   *
   * @example
   * ```typescript
   * const logoutUrl = tokenService.getLogoutUrl(session.idToken);
   * // Redirect to logoutUrl to complete logout
   * ```
   */
  getLogoutUrl(idTokenHint?: string): string;

  /**
   * Exchanges Better Auth session token for Keycloak access token.
   *
   * This is the core token exchange mechanism using OAuth2 token exchange grant.
   *
   * @param sessionToken - Better Auth session token
   * @returns Promise resolving to Keycloak token response
   * @throws {Error} When token exchange request fails
   *
   * @example
   * ```typescript
   * const tokenResponse = await tokenService.exchangeToken(session.token);
   * console.log('Access token expires in:', tokenResponse.expires_in, 'seconds');
   * ```
   */
  exchangeToken(sessionToken: string): Promise<KeycloakTokenResponse>;

  /**
   * Validates if a Keycloak access token is still valid.
   *
   * @param token - Keycloak access token to validate
   * @returns Promise resolving to true if token is valid
   *
   * @example
   * ```typescript
   * const isValid = await tokenService.validateToken(accessToken);
   * if (!isValid) {
   *   // Refresh token
   *   const newToken = await tokenService.getKeycloakAccessToken();
   * }
   * ```
   */
  validateToken(token: string): Promise<boolean>;

  /**
   * Decodes a JWT token without verification (for inspection only).
   *
   * @param token - JWT token to decode
   * @returns Decoded token payload
   *
   * @example
   * ```typescript
   * const payload = tokenService.decodeToken(accessToken);
   * console.log('Token subject:', payload.sub);
   * console.log('Token expiry:', new Date(payload.exp * 1000));
   * ```
   */
  decodeToken(token: string): Record<string, unknown>;

  /**
   * Revokes a Keycloak access token.
   *
   * @param token - Token to revoke
   * @returns Promise that resolves when revocation is complete
   *
   * @example
   * ```typescript
   * await tokenService.revokeToken(oldAccessToken);
   * // Token is now invalid
   * ```
   */
  revokeToken(token: string): Promise<void>;
}
