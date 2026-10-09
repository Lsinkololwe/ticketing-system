import { test as setup, expect } from '@playwright/test';
import { createHmac } from 'node:crypto';
import path from 'node:path';

export const ADMIN_STORAGE_STATE = path.join(__dirname, '.auth', 'admin.json');

/**
 * A real login, through Keycloak, once per run.
 *
 * <h2>Why the login is not mocked</h2>
 * Every mutation on the reference-data screen is `@PreAuthorize("hasRole('ADMIN')")`.
 * A test that injects a fake session proves the form works and proves nothing
 * about whether the person filling it in is allowed to — which is the half that
 * fails in production. So this drives the actual SSO redirect into the
 * `myticketzm-admin` realm and stores the resulting session for the specs.
 *
 * <p>The credentials are the seeded development ones from
 * `docker-resources/keycloak/myticketzm-admin-realm.json`. That realm exists to
 * be logged into by a developer; it holds no real accounts.
 */
const USERNAME = process.env.ADMIN_E2E_USERNAME ?? 'admin';
const PASSWORD = process.env.ADMIN_E2E_PASSWORD ?? 'admin_password';
// Staff sign-in requires a second factor. When the realm holds an authenticator for this account its secret is
// supplied here; Keycloak keys the HMAC with the secret string's own bytes.
const TOTP_SECRET = process.env.ADMIN_E2E_TOTP_SECRET;

/** RFC 6238 code (HMAC-SHA1, 6 digits, 30 s) for the given step. */
export function totp(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac('sha1', Buffer.from(secret, 'utf8')).update(counter).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  const binary = ((hash[offset] & 0x7f) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
  return String(binary % 1_000_000).padStart(6, '0');
}

setup('authenticate as platform admin', async ({ page }) => {
  await page.goto('/login');

  await page.getByTestId('admin-login-sso-button').click();

  // Keycloak's own form. Field names are Keycloak's, not ours — they are stable
  // across versions and are what the realm's login theme renders.
  await page.waitForURL(/\/realms\/myticketzm-admin\//, { timeout: 30_000 });
  await page.locator('#username').fill(USERNAME);
  await page.locator('#password').fill(PASSWORD);
  await page.locator('#kc-login').click();

  if (TOTP_SECRET) {
    await page.locator('#otp').fill(totp(TOTP_SECRET, Math.floor(Date.now() / 30_000)));
    await page.locator('#kc-login').click();
  }

  // Back on our side, authenticated. Waiting for the dashboard rather than for
  // "not the login page" — a redirect loop would satisfy the weaker assertion.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
  await expect(page).not.toHaveURL(/\/login/);

  await page.context().storageState({ path: ADMIN_STORAGE_STATE });
});
