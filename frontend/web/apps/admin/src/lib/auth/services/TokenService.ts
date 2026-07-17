/**
 * Token Service (admin) — Keycloak access-token management via Better Auth's
 * native auth.api.getAccessToken() (handles retrieval from the accounts
 * collection + automatic refresh).
 *
 * @module auth/services/TokenService
 */

import 'server-only';

import { headers } from 'next/headers';
import { auth } from '../index';
import type { ITokenService, IAuthConfig, KeycloakTokenResponse } from '../interfaces';

export class TokenService implements ITokenService {
  constructor(private readonly config: IAuthConfig) {}

  async getKeycloakAccessToken(): Promise<string | null> {
    try {
      const requestHeaders = await headers();
      const session = await auth.api.getSession({ headers: requestHeaders });
      if (!session?.user) return null;

      const tokenResponse = await auth.api.getAccessToken({
        body: { providerId: 'keycloak' },
        headers: requestHeaders,
      });
      return tokenResponse?.accessToken ?? null;
    } catch (error) {
      console.error('[TokenService] Failed to get Keycloak access token:', error);
      return null;
    }
  }

  async endKeycloakSession(refreshToken: string): Promise<boolean> {
    if (!this.config.keycloakClientSecret) {
      console.warn('[TokenService] No client secret — cannot back-channel logout');
      return false;
    }
    const logoutEndpoint =
      this.config.logoutEndpoint ||
      `${this.config.keycloakIssuer}/protocol/openid-connect/logout`;
    try {
      // Keycloak terminates the user's SSO session for this refresh token.
      // Confidential client → authenticate with client_id + client_secret.
      const res = await fetch(logoutEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          client_id: this.config.keycloakClientId,
          client_secret: this.config.keycloakClientSecret,
          refresh_token: refreshToken,
        }),
      });
      // 204 No Content on success; 400 if the token/session is already gone.
      return res.ok;
    } catch (error) {
      console.error('[TokenService] Back-channel logout failed:', error);
      return false;
    }
  }

  getLogoutUrl(idTokenHint?: string): string {
    const logoutEndpoint =
      this.config.logoutEndpoint ||
      `${this.config.keycloakIssuer}/protocol/openid-connect/logout`;
    // Always include client_id so Keycloak can validate the redirect even when
    // no id_token_hint is available; id_token_hint (when present) lets Keycloak
    // terminate the SSO session without a confirmation page.
    const params = new URLSearchParams({
      client_id: this.config.keycloakClientId,
      post_logout_redirect_uri: `${this.config.appUrl}/login`,
    });
    if (idTokenHint) params.set('id_token_hint', idTokenHint);
    return `${logoutEndpoint}?${params.toString()}`;
  }

  async exchangeToken(sessionToken: string): Promise<KeycloakTokenResponse> {
    const tokenUrl =
      this.config.tokenEndpoint ||
      `${this.config.keycloakIssuer}/protocol/openid-connect/token`;
    const response = await fetch(tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
        client_id: this.config.keycloakClientId,
        subject_token: sessionToken,
        subject_token_type: 'urn:ietf:params:oauth:token-type:access_token',
      }),
    });
    if (!response.ok) throw new Error(`Token exchange failed: ${response.statusText}`);
    return response.json();
  }

  async validateToken(token: string): Promise<boolean> {
    try {
      const exp = this.decodeToken(token).exp as number;
      if (!exp) return false;
      return exp > Math.floor(Date.now() / 1000);
    } catch {
      return false;
    }
  }

  decodeToken(token: string): Record<string, unknown> {
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error('Invalid JWT format');
    return JSON.parse(Buffer.from(parts[1], 'base64').toString('utf-8'));
  }

  async revokeToken(token: string): Promise<void> {
    const revokeUrl = `${this.config.keycloakIssuer}/protocol/openid-connect/revoke`;
    const response = await fetch(revokeUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: this.config.keycloakClientId,
        token,
        token_type_hint: 'access_token',
      }),
    });
    if (!response.ok) throw new Error(`Token revocation failed: ${response.statusText}`);
  }
}
