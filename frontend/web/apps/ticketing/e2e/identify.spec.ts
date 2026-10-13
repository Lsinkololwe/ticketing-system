import { expect, test, type Page, type Route } from '@playwright/test';

/**
 * The sign-in screen at /auth in a browser, against mocked network only (no Keycloak, no identity-service).
 * The browser-side routes are fulfilled by Playwright and the hand-over to /api/auth/start is intercepted,
 * so these specs prove what the screen does with each answer the server can give. The server routes (CSRF,
 * rate limits, what is forwarded to identity-service) are proven against the real BFF in
 * e2e/browser/identify.spec.ts.
 */

const CHALLENGE_ID = '123e4567-e89b-12d3-a456-426614174000';
const challengeBody = (over: Record<string, unknown> = {}) => ({
  challengeId: CHALLENGE_ID,
  contactType: 'EMAIL',
  maskedContact: 'j***@gmail.com',
  channel: 'EMAIL',
  expiresInSeconds: 300,
  resendAfterSeconds: 60,
  ...over,
});
const json = (route: Route, status: number, body: unknown) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

interface Answer {
  status: number;
  body: unknown;
}
interface Calls {
  challenge: Array<Record<string, unknown>>;
  verify: Array<Record<string, unknown>>;
  headers: Array<Record<string, string>>;
}

async function mockIdentity(
  page: Page,
  a: { challenge?: (n: number) => Answer | 'abort'; verify?: (n: number) => Answer; delayMs?: number } = {}
): Promise<Calls> {
  const calls: Calls = { challenge: [], verify: [], headers: [] };
  await page.route('**/api/identity/challenge', async (route) => {
    calls.challenge.push(route.request().postDataJSON());
    calls.headers.push(route.request().headers());
    if (a.delayMs) await new Promise((r) => setTimeout(r, a.delayMs));
    const out = a.challenge?.(calls.challenge.length) ?? { status: 202, body: challengeBody() };
    if (out === 'abort') return route.abort('failed');
    return json(route, out.status, out.body);
  });
  await page.route('**/api/identity/verify', (route) => {
    calls.verify.push(route.request().postDataJSON());
    const out = a.verify?.(calls.verify.length) ?? {
      status: 200,
      body: { verified: true, maskedContact: 'j***@gmail.com', expiresInSeconds: 120 },
    };
    return json(route, out.status, out.body);
  });
  await page.route('**/api/identity/ensure', (route) =>
    json(route, 200, { status: 'ACTIVE', isNew: true, next: '/api/auth/start' })
  );
  await page.route('**/api/auth/start**', (route) =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>redirected</h1>' })
  );
  return calls;
}

/** Picks the channel with the toggle, types the contact and asks for the code. */
async function requestCode(page: Page, channel: 'WhatsApp' | 'Email', value: string) {
  await page.goto('/auth?next=/events/e1/book');
  await page.getByRole('radio', { name: channel }).click();
  await page.getByTestId('contact-input').getByRole('textbox').last().fill(value);
  await page.getByTestId('request-code-submit').click();
}

async function typeCode(page: Page, digits: string) {
  await page.getByTestId('otp-input').getByRole('textbox').first().click();
  await page.keyboard.type(digits);
}

test.describe('contact entry', () => {
  test('populated: channel toggle, one field, consent links to terms and privacy', async ({ page }) => {
    await page.goto('/auth');
    await expect(page.getByRole('radio', { name: 'WhatsApp' })).toBeVisible();
    await expect(page.getByRole('radio', { name: 'Email' })).toBeVisible();
    await expect(page.getByTestId('contact-input')).toBeVisible();
    const terms = page.getByTestId('identify-terms');
    await expect(terms.getByRole('link', { name: 'Terms' })).toHaveAttribute('href', '/terms');
    await expect(terms.getByRole('link', { name: 'Privacy Policy' })).toHaveAttribute('href', '/privacy');
  });

  test('loading: the send button is held while the request is in flight', async ({ page }) => {
    await mockIdentity(page, { delayMs: 1200 });
    await requestCode(page, 'Email', 'j@gmail.com');
    await expect(page.getByTestId('request-code-submit')).toBeDisabled();
    await expect(page.getByTestId('identify-code-step')).toBeVisible();
  });

  test('a Zambian number typed without a prefix goes out in international form', async ({ page }) => {
    const calls = await mockIdentity(page, {
      challenge: () => ({ status: 202, body: challengeBody({ contactType: 'WHATSAPP', channel: 'WHATSAPP', maskedContact: '+260 97* ***567' }) }),
    });
    await requestCode(page, 'WhatsApp', '0971234567');
    await expect(page.getByTestId('identify-readback')).toContainText('by WhatsApp');
    expect(calls.challenge[0]).toMatchObject({ contact: { value: '+260971234567', type: 'WHATSAPP' }, regionHint: 'ZM' });
    expect(calls.headers[0]['x-requested-with']).toBe('pml-web');
  });

  test('an email goes out on the email channel', async ({ page }) => {
    const calls = await mockIdentity(page);
    await requestCode(page, 'Email', 'j@gmail.com');
    await expect(page.getByTestId('identify-readback')).toContainText('by email');
    expect(calls.challenge[0]).toMatchObject({ contact: { value: 'j@gmail.com', type: 'EMAIL' } });
  });
});

test.describe('code entry', () => {
  test('populated: countdown, resend held back, submit disabled until six digits', async ({ page }) => {
    await mockIdentity(page);
    await requestCode(page, 'Email', 'j@gmail.com');
    await expect(page.getByTestId('otp-countdown')).toContainText(/Expires in 0[45]:\d\d/);
    await expect(page.getByTestId('otp-resend')).toBeDisabled();
    await expect(page.getByTestId('otp-submit')).toBeDisabled();
  });

  test('submits on the sixth digit, then hands over with only the return path in the URL', async ({ page }) => {
    const calls = await mockIdentity(page);
    const started = page.waitForRequest((r) => r.url().includes('/api/auth/start'));
    await requestCode(page, 'Email', 'j@gmail.com');
    await typeCode(page, '12345');
    expect(calls.verify).toHaveLength(0);
    await page.keyboard.type('6');
    await expect(page.getByTestId('identify-welcome')).toContainText('We created your free account');
    expect(calls.verify[0]).toEqual({ challengeId: CHALLENGE_ID, code: '123456' });
    await page.getByTestId('identify-continue').click();
    const url = (await started).url();
    expect(url).toContain('/api/auth/start?next=%2Fevents%2Fe1%2Fbook');
    expect(url).not.toMatch(/handle|proof|code=|login_hint/i);
  });

  test('the explicit submit verifies the digits in the boxes after a transient failure', async ({ page }) => {
    const calls = await mockIdentity(page, {
      verify: (n) =>
        n === 1
          ? { status: 503, body: { errorCode: 'SERVICE_UNAVAILABLE' } }
          : { status: 200, body: { verified: true, maskedContact: 'x', expiresInSeconds: 120 } },
    });
    await requestCode(page, 'Email', 'j@gmail.com');
    await typeCode(page, '123456');
    await expect(page.getByTestId('otp-error-SERVICE_UNAVAILABLE')).toBeVisible();
    await page.getByTestId('otp-submit').click();
    await expect(page.getByTestId('identify-welcome')).toBeVisible();
    expect(calls.verify).toHaveLength(2);
  });

  test('resend stays disabled until resendAfterSeconds, then asks again for the same contact', async ({ page }) => {
    const calls = await mockIdentity(page, { challenge: () => ({ status: 202, body: challengeBody({ resendAfterSeconds: 3 }) }) });
    await requestCode(page, 'Email', 'j@gmail.com');
    const resend = page.getByTestId('otp-resend');
    await expect(resend).toBeDisabled();
    await expect(resend).toBeEnabled({ timeout: 8_000 });
    await resend.click();
    await expect.poll(() => calls.challenge.length).toBe(2);
    expect(calls.challenge[1]).toEqual(calls.challenge[0]);
  });

  test('Send by WhatsApp instead returns to entry with WhatsApp chosen and sends nothing', async ({ page }) => {
    const calls = await mockIdentity(page);
    await requestCode(page, 'Email', 'j@gmail.com');
    await page.getByTestId('otp-switch-channel').click();
    await expect(page.getByTestId('identify-contact-step')).toBeVisible();
    await expect(page.getByRole('radio', { name: 'WhatsApp' })).toBeChecked();
    expect(calls.challenge).toHaveLength(1);
  });

  test('the countdown follows the wall clock after the tab was in the background', async ({ page }) => {
    await mockIdentity(page, { challenge: () => ({ status: 202, body: challengeBody({ expiresInSeconds: 120, resendAfterSeconds: 120 }) }) });
    await page.clock.install();
    await requestCode(page, 'Email', 'j@gmail.com');
    await expect(page.getByTestId('otp-countdown')).toContainText('Expires in 02:00');
    await page.clock.fastForward(90_000);
    await expect(page.getByTestId('otp-countdown')).toContainText('Expires in 00:30');
  });
});

test.describe('refusals are shown in the alert line beside the field', () => {
  const atContact: Array<[string, number, Record<string, unknown>]> = [
    ['CONTACT_INVALID', 400, {}],
    ['OTP_RATE_LIMITED', 429, { retryAfterSeconds: 90 }],
    ['OTP_COOLDOWN_ACTIVE', 429, { retryAfterSeconds: 40 }],
    ['OTP_DELIVERY_FAILED', 502, {}],
    ['ACCOUNT_SUSPENDED', 403, {}],
  ];
  for (const [code, status, extra] of atContact) {
    test(`${code} when asking for a code`, async ({ page }) => {
      await mockIdentity(page, { challenge: () => ({ status, body: { errorCode: code, ...extra } }) });
      await requestCode(page, 'Email', 'j@gmail.com');
      const box = page.getByTestId(`otp-error-${code}`);
      await expect(box).toBeVisible();
      await expect(box).toHaveAttribute('role', 'alert');
      await expect(box).not.toContainText(/no account|not registered|already have/i);
    });
  }

  test('the wait comes from retryAfterSeconds and holds the send button', async ({ page }) => {
    await mockIdentity(page, { challenge: () => ({ status: 429, body: { errorCode: 'OTP_RATE_LIMITED', retryAfterSeconds: 90 } }) });
    await requestCode(page, 'Email', 'j@gmail.com');
    await expect(page.getByTestId('otp-error-OTP_RATE_LIMITED')).toContainText(/01:(30|29)/);
    await expect(page.getByTestId('request-code-submit')).toBeDisabled();
  });

  test('OTP_INVALID shows the tries left and clears the boxes', async ({ page }) => {
    await mockIdentity(page, { verify: () => ({ status: 400, body: { errorCode: 'OTP_INVALID', attemptsRemaining: 3 } }) });
    await requestCode(page, 'Email', 'j@gmail.com');
    await typeCode(page, '000000');
    await expect(page.getByTestId('otp-error-OTP_INVALID')).toBeVisible();
    await expect(page.getByTestId('identify-attempts')).toContainText('3 tries left');
    for (const box of await page.getByTestId('otp-input').getByRole('textbox').all()) await expect(box).toHaveValue('');
  });

  test('OTP_EXPIRED is shown and resend is still there', async ({ page }) => {
    await mockIdentity(page, { verify: () => ({ status: 410, body: { errorCode: 'OTP_EXPIRED' } }) });
    await requestCode(page, 'Email', 'j@gmail.com');
    await typeCode(page, '222222');
    await expect(page.getByTestId('otp-error-OTP_EXPIRED')).toBeVisible();
    await expect(page.getByTestId('otp-resend')).toBeVisible();
  });

  test('OTP_LOCKED shows when it lifts and closes the boxes', async ({ page }) => {
    const lockedUntil = new Date(Date.now() + 5 * 60_000).toISOString();
    await mockIdentity(page, { verify: () => ({ status: 423, body: { errorCode: 'OTP_LOCKED', lockedUntil } }) });
    await requestCode(page, 'Email', 'j@gmail.com');
    await typeCode(page, '111111');
    const box = page.getByTestId('otp-error-OTP_LOCKED');
    await expect(box).toBeVisible();
    const clock = await page.evaluate(
      (iso) => new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }),
      lockedUntil
    );
    await expect(box).toContainText(clock);
    for (const b of await page.getByTestId('otp-input').getByRole('textbox').all()) await expect(b).toBeDisabled();
  });

  test('error: a network failure keeps the person on the entry step', async ({ page }) => {
    await mockIdentity(page, { challenge: () => 'abort' });
    await requestCode(page, 'Email', 'j@gmail.com');
    await expect(page.getByTestId('otp-error-SERVICE_UNAVAILABLE')).toBeVisible();
    await expect(page.getByTestId('request-code-submit')).toBeEnabled();
  });
});

test.describe('nothing secret reaches the browser', () => {
  test('no token, handle, proof or code in storage, cookies or the URL after a full sign-in', async ({ page }) => {
    await mockIdentity(page);
    await requestCode(page, 'Email', 'j@gmail.com');
    await typeCode(page, '482915');
    await expect(page.getByTestId('identify-welcome')).toBeVisible();
    const seen = await page.evaluate(() => ({
      cookie: document.cookie,
      local: JSON.stringify({ ...localStorage }),
      session: JSON.stringify({ ...sessionStorage }),
      href: location.href,
    }));
    for (const secret of ['482915', CHALLENGE_ID, 'j@gmail.com']) {
      expect(seen.local).not.toContain(secret);
      expect(seen.session).not.toContain(secret);
      expect(seen.cookie).not.toContain(secret);
      expect(seen.href).not.toContain(secret);
    }
    expect(seen.cookie + seen.local + seen.session).not.toMatch(/token|handle|proof|kc-callback/i);
  });
});
