import { test as setup, expect } from '@playwright/test';
import path from 'node:path';

export const ORGANIZER_STORAGE_STATE = path.join(__dirname, '.auth', 'organizer.json');

/**
 * A real sign-in, through Keycloak, once per run.
 *
 * <h2>Why the sign-in is not faked</h2>
 * The onboarding guard runs in a Server Component and resolves the session through
 * the BFF, which validates the opaque session cookie against its Redis store rather
 * than trusting it. A synthesised cookie is rejected there, so the only way to
 * exercise the guard at all is to hold a session it accepts.
 *
 * <p>This also means the BFF attaches a real access token server-side, which the
 * guard requires: the `myOwnedOrganization`
 * query is `hasRole('ORGANIZER')`-guarded, and a tokenless query returns an
 * authorization error rather than "no organization". Reproducing that
 * distinction was the point of the fix under test.</p>
 *
 * <h2>How the person signs in</h2>
 * The realm's browser flow is the contact flow: Keycloak shows a page asking for a WhatsApp number or
 * an email, sends a six digit code there, and shows a second page asking for it. There is no username
 * or password. This setup types the organizer's contact (`ORGANIZER_E2E_CONTACT`), then reads the code
 * the way a developer does on their own machine: identity-service keeps the messages it would have sent
 * in memory when `identity.delivery.capture.enabled=true` (local and test profiles only) and serves them
 * from `POST /api/internal/auth/dev/captured/search`. That route needs a service token with the
 * internal write scope and a direct loopback caller, so this setup asks Keycloak for one with the
 * identity client credentials. If the environment has a fixed development code, set
 * `ORGANIZER_E2E_CODE` and the lookup is skipped.
 *
 * <h2>What must be running</h2>
 * Redis (the BFF session store), Keycloak on 8084 with the contact flow, and identity-service with
 * message capture enabled. The seeded organizer account must hold the contact given in
 * `ORGANIZER_E2E_CONTACT`; nothing here is a real account.
 */
const CONTACT = process.env.ORGANIZER_E2E_CONTACT ?? '';
const FIXED_CODE = process.env.ORGANIZER_E2E_CODE;
const ISSUER = process.env.KEYCLOAK_ISSUER ?? 'http://localhost:8084/realms/myticketzm';
const IDENTITY_BASE_URL = (process.env.IDENTITY_BASE_URL ?? 'http://localhost:8083').replace(/\/$/, '');
const IDENTITY_CLIENT_ID = process.env.IDENTITY_CLIENT_ID ?? '';
const IDENTITY_CLIENT_SECRET = process.env.IDENTITY_CLIENT_SECRET ?? '';

const KEYCLOAK_URL = /\/realms\/[^/]*myticketzm[^/]*\//;

/** The address form the identity service stores: emails lowercased, phone numbers without spaces. */
function recipientOf(contact: string): string {
  return contact.includes('@') ? contact.trim().toLowerCase() : contact.replace(/\s+/g, '');
}

/** Newest captured code for the contact that arrived after `since`, polled for up to 15 seconds. */
async function capturedCode(contact: string, since: number): Promise<string> {
  if (!IDENTITY_CLIENT_ID || !IDENTITY_CLIENT_SECRET) {
    throw new Error(
      'Reading the sign-in code needs IDENTITY_CLIENT_ID and IDENTITY_CLIENT_SECRET (a service account with the ' +
        'internal write scope), or set ORGANIZER_E2E_CODE when the environment uses a fixed development code.'
    );
  }
  const tokenRes = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'client_credentials',
      client_id: IDENTITY_CLIENT_ID,
      client_secret: IDENTITY_CLIENT_SECRET,
      scope: 'internal-read internal-write',
    }),
  });
  if (!tokenRes.ok) throw new Error(`service token request failed (${tokenRes.status})`);
  const { access_token: token } = (await tokenRes.json()) as { access_token: string };

  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const res = await fetch(`${IDENTITY_BASE_URL}/api/internal/auth/dev/captured/search`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({ recipient: recipientOf(contact), limit: 5 }),
    });
    if (!res.ok) throw new Error(`captured messages lookup failed (${res.status}); is message capture enabled?`);
    const messages = (await res.json()) as Array<{ code: string; at: string }>;
    const fresh = messages.find((m) => Date.parse(m.at) >= since - 1000);
    if (fresh) return fresh.code;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error('no sign-in code was captured for the organizer contact within 15 seconds');
}

setup('authenticate as organizer', async ({ page }) => {
  if (!CONTACT) {
    throw new Error('Set ORGANIZER_E2E_CONTACT to the WhatsApp number or email of the seeded organizer account.');
  }
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

  // Keycloak's contact page. The ids are the ones the contact templates render (`contact-input.ftl`,
  // `contact-code.ftl`). An existing SSO session can skip both pages and bounce straight back, so the
  // fields are only filled when they are actually presented.
  const contact = page.locator('#contact');
  if (await contact.isVisible().catch(() => false)) {
    const askedAt = Date.now();
    await contact.fill(CONTACT);
    await page.locator('#kc-contact-submit').click();
    const code = FIXED_CODE ?? (await capturedCode(CONTACT, askedAt));
    await page.locator('#code').fill(code);
    await page.locator('#kc-code-submit').click();
  }

  // Back on our side, authenticated. Asserting we left /login specifically,
  // rather than "some page rendered" — a redirect loop satisfies the weaker
  // check. Which onboarding screen we land on depends on the stub's state and
  // is not this file's business.
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
  await expect(page).not.toHaveURL(/\/login/);

  await page.context().storageState({ path: ORGANIZER_STORAGE_STATE });
});
