import { describe, expect, it } from 'vitest';
import { organizerBffConfig } from './bff.config';

describe('organizerBffConfig console access', () => {
  const env = { KEYCLOAK_ISSUER: 'http://kc/realms/r', KEYCLOAK_CLIENT_ID: 'c', KEYCLOAK_CLIENT_SECRET: 's' };

  it('lets any signed-in organization member in: a team member has no ORGANIZER realm role', () => {
    const cfg = organizerBffConfig(env);
    const prefixes = (cfg.guarded ?? []).map((g) => g.prefix);

    expect(prefixes).toEqual(expect.arrayContaining(['/dashboard', '/finance', '/team', '/welcome', '/apply']));
    expect((cfg.guarded ?? []).filter((g) => 'roles' in g && g.roles && g.roles.length > 0)).toEqual([]);
  });
});

describe('organizerBffConfig account id', () => {
  const env = { KEYCLOAK_ISSUER: 'http://kc/realms/r', KEYCLOAK_CLIENT_ID: 'c', KEYCLOAK_CLIENT_SECRET: 's' };

  it('reads the platform account id from the accountId claim, not the Keycloak subject', () => {
    const cfg = organizerBffConfig(env);
    expect(cfg.access?.accountClaim).toBe('accountId');
    expect(cfg.postLogin).toBeUndefined();
  });

  it('has no switch that lets the subject stand in for the account id', () => {
    const cfg = organizerBffConfig({ ...env, ACCOUNT_CLAIM: '' });
    expect(cfg.access?.accountClaim).toBe('accountId');
    expect(cfg.postLogin).toBeUndefined();
  });
});
