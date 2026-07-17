import 'server-only';
import type { KeycloakTokenResponse } from './types';

/**
 * Token service interface — Keycloak access-token retrieval/validation for the
 * admin app, backed by Better Auth's native token management.
 */
export interface ITokenService {
  /** Get the current Keycloak access token (auto-refreshed), or null. */
  getKeycloakAccessToken(): Promise<string | null>;

  /** Build the Keycloak end-session (logout) URL. */
  getLogoutUrl(idTokenHint?: string): string;

  /**
   * Terminate the Keycloak SSO session server-side via back-channel logout
   * (confidential client + refresh_token). Returns true on success. This avoids
   * the browser-facing "Do you want to log out?" confirmation page entirely.
   */
  endKeycloakSession(refreshToken: string): Promise<boolean>;

  /** Exchange a Better Auth session token for a Keycloak token (token-exchange grant). */
  exchangeToken(sessionToken: string): Promise<KeycloakTokenResponse>;

  /** Whether a JWT is still valid (not expired). */
  validateToken(token: string): Promise<boolean>;

  /** Decode a JWT payload WITHOUT signature verification (inspection only). */
  decodeToken(token: string): Record<string, unknown>;

  /** Revoke a Keycloak access token. */
  revokeToken(token: string): Promise<void>;
}
