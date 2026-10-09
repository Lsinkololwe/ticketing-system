import { describe, expect, it } from 'vitest';
import { organizerBffConfig } from './bff.config';

describe('organizerBffConfig account id', () => {
  const env = { KEYCLOAK_ISSUER: 'http://kc/realms/r', KEYCLOAK_CLIENT_ID: 'c', KEYCLOAK_CLIENT_SECRET: 's' };

  it('reads the platform account id from the accountId claim, not the Keycloak subject', () => {
    const cfg = organizerBffConfig(env);
    expect(cfg.access?.accountClaim).toBe('accountId');
    expect(cfg.postLogin).toBeUndefined();
  });

  it('falls back to the subject only when the claim is switched off', async () => {
    const cfg = organizerBffConfig({ ...env, ACCOUNT_CLAIM: '' });
    expect(cfg.access?.accountClaim).toBeUndefined();
    await expect(cfg.postLogin!({ sub: 'kc-sub' } as never)).resolves.toMatchObject({ ok: true, accountId: 'kc-sub' });
  });
});
