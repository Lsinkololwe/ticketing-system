import { describe, expect, it } from 'vitest';
import { adminBffConfig } from '../bffConfig';

// The console signs staff into one realm, but the service account it uses to tell the identity
// service about a sign-out lives in the buyer realm. These tests pin where that token is requested from.
const required = (name: string) => `value-of-${name}`;
const base = {
  APP_URL: 'http://localhost:3030',
  KEYCLOAK_ISSUER: 'http://localhost:8084/realms/myticketzm-admin',
  KEYCLOAK_CLIENT_ID: 'myticketzm-admin',
  IDENTITY_BASE_URL: 'http://localhost:8083',
};

describe('admin identity service account', () => {
  it('asks the realm named by IDENTITY_TOKEN_URL, not the staff realm it signs into', () => {
    const cfg = adminBffConfig(
      { ...base, IDENTITY_TOKEN_URL: 'http://localhost:8084/realms/myticketzm/protocol/openid-connect/token' },
      required,
    );
    expect(cfg.identity?.tokenUrl).toBe('http://localhost:8084/realms/myticketzm/protocol/openid-connect/token');
  });

  it('falls back to the issuer-derived endpoint only when nothing is configured', () => {
    const cfg = adminBffConfig(base, required);
    expect(cfg.identity?.tokenUrl).toBe(
      'http://localhost:8084/realms/myticketzm-admin/protocol/openid-connect/token',
    );
  });

  it('has no identity client at all when the identity service is not configured', () => {
    const { IDENTITY_BASE_URL: _unused, ...withoutIdentity } = base;
    expect(adminBffConfig(withoutIdentity, required).identity).toBeUndefined();
  });
});
