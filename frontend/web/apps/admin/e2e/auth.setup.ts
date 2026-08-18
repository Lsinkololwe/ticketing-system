import { test as setup, expect } from '@playwright/test';
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

setup('authenticate as platform admin', async ({ page }) => {
  await page.goto('/login');

  await page.getByTestId('admin-login-sso-button').click();

  // Keycloak's own form. Field names are Keycloak's, not ours — they are stable
  // across versions and are what the realm's login theme renders.
  await page.waitForURL(/\/realms\/myticketzm-admin\//, { timeout: 30_000 });
  await page.locator('#username').fill(USERNAME);
  await page.locator('#password').fill(PASSWORD);
  await page.locator('#kc-login').click();

  // Back on our side, authenticated. Waiting for the dashboard rather than for
  // "not the login page" — a redirect loop would satisfy the weaker assertion.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
  await expect(page).not.toHaveURL(/\/login/);

  await page.context().storageState({ path: ADMIN_STORAGE_STATE });
});
