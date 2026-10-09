import { expect, test } from '@playwright/test';

import { setOnboardingState } from './microcks/client';

const MICROCKS_URL = `http://localhost:${process.env.MICROCKS_PORT ?? 18080}`;

/**
 * The organizer auth harness reaches a guarded screen.
 *
 * <h2>Why this test exists separately from `auth.setup.ts`</h2>
 * The setup already drives a real Keycloak login and writes a storage state.
 * What it cannot tell you is whether that state is <em>accepted</em> by the part
 * of the app that matters. Its final assertion is "we are no longer on /login",
 * which a redirect to the public marketing page also satisfies — and a harness
 * that produces a session no guarded route honours looks exactly like a working
 * one until the first real spec is written on top of it.
 *
 * <p>That gap is not hypothetical here. The onboarding decision runs in a
 * <b>Server Component</b>: it resolves the BFF session, which
 * is validated against the Redis session store rather than trusting the cookie,
 * and then runs a `hasRole('ORGANIZER')` query with the server-held token. `page.route()` cannot intercept any of that — the fetch is issued by
 * the Next.js Node process — so browser-level mocking silently does nothing and
 * a suite built on it proves nothing. Only a genuine session gets past it.</p>
 *
 * <p>So this asserts the one thing the setup leaves open: the stored session
 * carries an organizer through the guard and onto `(dashboard)/dashboard`.</p>
 */
test.describe('organizer auth harness', () => {
  /**
   * `(dashboard)/dashboard` is only reachable once the caller owns an ACTIVE
   * organization — the onboarding guard routes everyone else into the
   * application flow, which is the guard working rather than failing. Signing in
   * is therefore necessary and not sufficient, and a spec that skipped this step
   * would land on the application screen and report the auth harness as broken.
   */
  test.beforeEach(async () => {
    await setOnboardingState(MICROCKS_URL, {
      organization: { status: 'ACTIVE', name: 'Lusaka Live Events' },
    });
  });

  test('a stored organizer session reaches the dashboard', async ({ page }) => {
    await page.goto('/dashboard');

    // Not "we are not on /login" — that is what the setup already checks and it
    // is satisfied by any redirect at all. This waits for the dashboard's own
    // root element, which only the guarded route renders.
    await expect(
      page.getByTestId('dashboard-page'),
      'the organizer session did not survive the Server Component guard'
    ).toBeVisible({ timeout: 30_000 });

    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('the session is a real one, not an anonymous fallback', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard-page')).toBeVisible({ timeout: 30_000 });

    // A guard that failed open would render the same shell with no identity
    // behind it. The BFF keeps an opaque httpOnly session cookie on the app origin
    // (never a token), so its presence is the difference between "authenticated" and
    // "the page happened to render".
    const cookies = await page.context().cookies();
    const sessionCookie = cookies.find((cookie) =>
      /^(__Host-)?pml_org$/.test(cookie.name)
    );

    expect(sessionCookie?.httpOnly).toBe(true);
    expect(
      sessionCookie,
      'no session cookie on the app origin — the dashboard rendered without an authenticated identity'
    ).toBeDefined();
  });
});
