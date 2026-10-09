import { expect, test } from '@playwright/test';

const APP_URL = process.env.BASE_URL || `http://localhost:${process.env.APP_PORT ?? 3001}`;

/**
 * The SSO probe was moved into a hidden iframe so discovery is browsable signed
 * out. This is the test that says the change did not cost anything.
 *
 * <h2>Why a fix like this needs its own guard</h2>
 * "Stop redirecting to the identity server" and "stop requiring a session" look
 * identical from the outside on a public page, and only differ on a private
 * one. The comfortable way to make discovery reachable is to weaken the auth
 * path — and the resulting app browses beautifully, passes every screen test,
 * and serves someone else's tickets to whoever asks.
 *
 * <p>So public routes are asserted reachable <em>and</em> a guarded route is
 * asserted still guarded, in the same file. Splitting them lets one be deleted
 * without the other noticing.</p>
 */
test.describe('public surface vs guarded surface, signed out', () => {
  /**
   * Discovery is a public surface per the design. A first-time visitor with no
   * session must be able to browse before being asked for anything.
   */
  const PUBLIC_ROUTES = ['/', '/events'];

  /** Anything tied to a person. */
  const GUARDED_ROUTES = ['/my-tickets'];

  for (const route of PUBLIC_ROUTES) {
    test(`${route} is reachable without a session`, async ({ page }) => {
      await page.goto(route);

      // Deliberately generous: the app may client-route, so what matters is that
      // the browser is still on the app's own origin rather than the identity
      // server's.
      expect(
        new URL(page.url()).host,
        `${route} left the app for ${page.url()} — discovery is meant to be public`
      ).toBe(new URL(APP_URL).host);

      await expect(page.locator('body')).not.toBeEmpty();
    });
  }

  for (const route of GUARDED_ROUTES) {
    test(`${route} still demands a session`, async ({ page }) => {
      await page.goto(route);
      // The guard may redirect to Keycloak or render its own sign-in prompt.
      // Both are correct; what would not be is rendering the page.
      await page.waitForTimeout(2_000);

      const url = new URL(page.url());
      const leftForIdentityServer = url.host !== new URL(APP_URL).host;
      const showsSignIn = await page
        .getByText(/sign in|log in|continue with/i)
        .first()
        .isVisible()
        .catch(() => false);
      const stayedOnRoute = url.pathname.startsWith(route);

      expect(
        leftForIdentityServer || showsSignIn || !stayedOnRoute,
        `${route} rendered for an anonymous visitor. Making discovery public must ` +
          'not have made anything else public.'
      ).toBe(true);
    });
  }
});
