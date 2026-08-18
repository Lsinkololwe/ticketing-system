import { test as setup, expect } from '@playwright/test';
import path from 'node:path';

export const ORGANIZER_STORAGE_STATE = path.join(__dirname, '.auth', 'organizer.json');

/**
 * A real login, through Keycloak, once per run.
 *
 * <h2>Why the login is not faked</h2>
 * The onboarding guard runs in a Server Component and calls Better Auth's
 * `auth.api.getSession()`, which validates against MongoDB rather than trusting
 * the cookie — that is the whole point of the "Layer 2" design. A synthesised
 * cookie is rejected there, so the only way to exercise the guard at all is to
 * hold a session it accepts.
 *
 * <p>This also means `auth.api.getAccessToken({ providerId: 'keycloak' })`
 * returns a real token, which the guard requires: the `myOwnedOrganization`
 * query is `hasRole('ORGANIZER')`-guarded, and a tokenless query returns an
 * authorization error rather than "no organization". Reproducing that
 * distinction was the point of the fix under test.</p>
 *
 * <p>Credentials are the seeded development ones from
 * `docker-resources/keycloak/myticketzm-realm.json` — the `organizer` user,
 * which holds the ORGANIZER realm role. That realm exists to be logged into by a
 * developer; it holds no real accounts.</p>
 *
 * <h2>What must be running</h2>
 * MongoDB (Better Auth's session store) and Keycloak on 8084. The stub API
 * server replaces the whole backend, so no Java service is needed.
 */
const USERNAME = process.env.ORGANIZER_E2E_USERNAME ?? 'organizer';
const PASSWORD = process.env.ORGANIZER_E2E_PASSWORD ?? 'admin_password';

const KEYCLOAK_URL = /\/realms\/[^/]*myticketzm[^/]*\//;

setup('authenticate as organizer', async ({ page }) => {
  await page.goto('/login');

  // Two ways to reach Keycloak, and which one happens is not ours to control:
  // when no SSO session exists the app renders its own sign-in page and waits
  // for a click, but when Keycloak already holds a session the app redirects
  // straight through. Waiting for the button in the second case times out on a
  // page that is already navigating away.
  if (!KEYCLOAK_URL.test(page.url())) {
    const ssoButton = page.getByTestId('login-signin-button');
    await ssoButton.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
    if (await ssoButton.isVisible().catch(() => false)) {
      await ssoButton.click();
    }
  }

  await page.waitForURL(KEYCLOAK_URL, { timeout: 30_000 });

  // Keycloak's own form. Field ids are Keycloak's, not ours — they are stable
  // across versions and are what the realm's login theme renders.
  //
  // An existing SSO session can also skip the form entirely and bounce straight
  // back, so the fields are only filled when they are actually presented.
  const username = page.locator('#username');
  if (await username.isVisible().catch(() => false)) {
    await username.fill(USERNAME);
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#kc-login').click();
  }

  // Back on our side, authenticated. Asserting we left /login specifically,
  // rather than "some page rendered" — a redirect loop satisfies the weaker
  // check. Which onboarding screen we land on depends on the stub's state and
  // is not this file's business.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
  await expect(page).not.toHaveURL(/\/login/);

  await page.context().storageState({ path: ORGANIZER_STORAGE_STATE });
});
