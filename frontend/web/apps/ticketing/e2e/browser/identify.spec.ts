import { harnessTest as test, expect, captureConsole } from '../../../../e2e-harness/browser/playwright';
import type { FakeUpstream } from '../../../../e2e-harness/browser/playwright';
import type { Page } from '@playwright/test';
import { catalog } from './fixtures';
import { settle, shot } from './_kit';

/**
 * The sign-in flow through the real buyer app: real `next` server, real BFF routes, real Redis behind the
 * session, flow record and rate limiter. Only identity-service is a fixture (the fake upstream answers its
 * REST routes), so what the browser sends, what the BFF forwards, what the browser is allowed to see back
 * and how the limiter and CSRF guard behave are all the production code paths.
 *
 * Keycloak is not part of this harness: the hand-over to /api/auth/start is intercepted.
 */

const CHALLENGE_ID = '123e4567-e89b-12d3-a456-426614174000';
const PROOF = 'PROOF-SECRET-VALUE';
const HANDLE = 'HANDLE-SECRET-VALUE';

let counter = 0;
/** A client address and contact of its own so no spec shares a rate-limit bucket with another. */
const unique = () => {
  counter += 1;
  const n = (Date.now() % 200) + counter;
  return { ip: `198.51.100.${(n % 250) + 1}`, email: `buyer${Date.now()}${counter}@example.test` };
};

function identityFixtures(upstream: FakeUpstream, over: { challenge?: unknown; verify?: unknown; ensure?: unknown } = {}) {
  upstream.gql(catalog());
  upstream.rest('POST', '/api/internal/auth/challenges', {
    status: 200,
    body: { challengeId: CHALLENGE_ID, contactType: 'EMAIL', maskedContact: 'b***@example.test', channel: 'EMAIL', expiresInSeconds: 300, resendAfterSeconds: 30 },
    ...(over.challenge as object),
  });
  upstream.rest('POST', '/api/internal/auth/challenges/verify', {
    status: 200,
    body: { proof: PROOF, contactType: 'EMAIL', maskedContact: 'b***@example.test', expiresInSeconds: 120 },
    ...(over.verify as object),
  });
  upstream.rest('POST', '/api/internal/auth/accounts/ensure', {
    status: 200,
    body: { accountId: 'acc-1', status: 'ACTIVE', isNew: true, loginHandle: HANDLE },
    ...(over.ensure as object),
  });
}

async function prepare(page: Page, ip: string) {
  await page.setExtraHTTPHeaders({ 'x-forwarded-for': ip });
  await page.route('**/api/auth/start**', (route) => route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>handed over</h1>' }));
}

/** Picks the channel with the toggle and types the contact into the one field of that channel. */
async function enter(page: Page, channel: 'WhatsApp' | 'Email', value: string) {
  await page.getByRole('radio', { name: channel }).click();
  await page.getByTestId('contact-input').getByRole('textbox').last().fill(value);
  await page.getByTestId('request-code-submit').click();
}

const typeCode = async (page: Page, digits: string) => {
  await page.getByTestId('otp-input').getByRole('textbox').first().click();
  await page.keyboard.type(digits);
};
const requests = (upstream: FakeUpstream, path: string) => upstream.requests.filter((r) => r.path === path);

test.describe('sign in with a code', () => {
  test('email: one field to the welcome step, forwarding only what identity-service needs', async ({ page, upstream }, info) => {
    const { ip, email } = unique();
    identityFixtures(upstream);
    await prepare(page, ip);
    const browserSaw: string[] = [];
    page.on('response', async (res) => {
      if (res.url().includes('/api/identity/')) browserSaw.push(await res.text().catch(() => ''));
    });
    const log = captureConsole(page);
    await page.goto('/auth?next=/events/e1/book');
    await expect(page.getByRole('heading', { level: 1, name: 'Sign in to Showstop' })).toBeVisible();
    await shot(page, 'identify-contact', info);

    await enter(page, 'Email', email);
    await expect(page.getByTestId('identify-readback')).toContainText('We sent a 6 digit code by email to b***@example.test');
    await expect(page.getByTestId('otp-countdown')).toContainText('Expires in');
    await shot(page, 'identify-code', info);

    await typeCode(page, '123456');
    await expect(page.getByTestId('identify-welcome')).toContainText('We created your free account');
    await shot(page, 'identify-welcome', info);

    // What the BFF sent to identity-service.
    const sent = requests(upstream, '/api/internal/auth/challenges')[0].body as Record<string, unknown>;
    expect(sent).toMatchObject({ contact: { value: email, type: 'EMAIL' } });
    expect(requests(upstream, '/api/internal/auth/challenges/verify')[0].body).toEqual({ challengeId: CHALLENGE_ID, code: '123456' });
    // The proof was parked server-side by verify and is what ensure carries; the browser never sent it.
    expect(requests(upstream, '/api/internal/auth/accounts/ensure')[0].body).toMatchObject({ proof: PROOF, issueHandle: true });

    // What the browser was allowed to see: no proof, no handle, no token.
    const everything = browserSaw.join('\n');
    expect(everything).not.toContain(PROOF);
    expect(everything).not.toContain(HANDLE);
    expect(everything).not.toMatch(/access_token|refresh_token|eyJ/);

    // Where it is kept: nothing in storage, and the flow cookie is HttpOnly.
    const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }) + document.cookie);
    for (const secret of [PROOF, HANDLE, '123456', CHALLENGE_ID]) expect(storage).not.toContain(secret);
    const cookies = await page.context().cookies();
    const flow = cookies.find((c) => c.name.endsWith('pml_buyer_f'));
    expect(flow?.httpOnly).toBe(true);

    await settle(page, upstream, log, { allowErrors: [/Failed to load resource/] });
  });

  test('a phone number is sent to identity-service in international form with a region hint', async ({ page, upstream }) => {
    const { ip } = unique();
    identityFixtures(upstream, {
      challenge: { body: { challengeId: CHALLENGE_ID, contactType: 'WHATSAPP', maskedContact: '+260 97* ***567', channel: 'WHATSAPP', expiresInSeconds: 300, resendAfterSeconds: 30 } },
    });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'WhatsApp', `097${String(Date.now()).slice(-7)}`);
    await expect(page.getByTestId('identify-readback')).toContainText('by WhatsApp');
    const sent = requests(upstream, '/api/internal/auth/challenges')[0].body as { contact: { value: string; type: string }; regionHint?: string };
    expect(sent.contact.value).toMatch(/^\+260\d{9}$/);
    expect(sent.contact.type).toBe('WHATSAPP');
    expect(sent.regionHint).toBe('ZM');
  });

  test('the sign-in hand-over carries only the return path', async ({ page, upstream }) => {
    const { ip, email } = unique();
    identityFixtures(upstream);
    await prepare(page, ip);
    const started = page.waitForRequest((r) => r.url().includes('/api/auth/start'));
    await page.goto('/auth?next=/events/e1/book');
    await enter(page, 'Email', email);
    await typeCode(page, '123456');
    const url = (await started).url();
    expect(new URL(url).searchParams.get('next')).toBe('/events/e1/book');
    expect(url).not.toContain(HANDLE);
    expect(url).not.toContain(PROOF);
  });
});

test.describe('refusals from identity-service reach the field unchanged', () => {
  const lockedUntil = () => new Date(Date.now() + 10 * 60_000).toISOString();

  test('OTP_INVALID with the tries left', async ({ page, upstream }, info) => {
    const { ip, email } = unique();
    identityFixtures(upstream, { verify: { status: 400, body: { errorCode: 'OTP_INVALID', attemptsRemaining: 2 } } });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'Email', email);
    await typeCode(page, '000000');
    await expect(page.getByTestId('otp-error-OTP_INVALID')).toBeVisible();
    await expect(page.getByTestId('identify-attempts')).toContainText('2 tries left');
    await shot(page, 'identify-invalid', info);
  });

  test('OTP_LOCKED with when it lifts', async ({ page, upstream }, info) => {
    const { ip, email } = unique();
    identityFixtures(upstream, { verify: { status: 423, body: { errorCode: 'OTP_LOCKED', lockedUntil: lockedUntil() } } });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'Email', email);
    await typeCode(page, '111111');
    await expect(page.getByTestId('otp-error-OTP_LOCKED')).toContainText(/Try again at/);
    await expect(page.getByTestId('otp-submit')).toBeDisabled();
    await shot(page, 'identify-locked', info);
  });

  test('OTP_RATE_LIMITED with the wait', async ({ page, upstream }) => {
    const { ip, email } = unique();
    identityFixtures(upstream, { challenge: { status: 429, body: { errorCode: 'OTP_RATE_LIMITED', retryAfterSeconds: 120 } } });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'Email', email);
    await expect(page.getByTestId('otp-error-OTP_RATE_LIMITED')).toContainText(/0[12]:\d\d/);
  });

  test('OTP_DELIVERY_FAILED is shown beside the field and nothing is sent to another contact', async ({ page, upstream }) => {
    const { ip } = unique();
    identityFixtures(upstream, { challenge: { status: 502, body: { errorCode: 'OTP_DELIVERY_FAILED' } } });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'WhatsApp', `097${String(Date.now()).slice(-7)}`);
    await expect(page.getByTestId('otp-error-OTP_DELIVERY_FAILED')).toBeVisible();
    expect(requests(upstream, '/api/internal/auth/challenges')).toHaveLength(1);
  });

  test('Send by email instead returns to entry with Email chosen and sends nothing silently', async ({ page, upstream }) => {
    const { ip } = unique();
    identityFixtures(upstream, {
      challenge: { body: { challengeId: CHALLENGE_ID, contactType: 'WHATSAPP', maskedContact: '+260 97* ***567', channel: 'WHATSAPP', expiresInSeconds: 300, resendAfterSeconds: 30 } },
    });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'WhatsApp', `097${String(Date.now()).slice(-7)}`);
    await expect(page.getByTestId('otp-switch-channel')).toHaveText('Send by email instead');
    await page.getByTestId('otp-switch-channel').click();
    await expect(page.getByTestId('identify-contact-step')).toBeVisible();
    await expect(page.getByRole('radio', { name: 'Email' })).toBeChecked();
    expect(requests(upstream, '/api/internal/auth/challenges')).toHaveLength(1);
  });

  test('ACCOUNT_SUSPENDED after the code is accepted is a dead end with a pointer to support', async ({ page, upstream }) => {
    const { ip, email } = unique();
    identityFixtures(upstream, { ensure: { status: 403, body: { errorCode: 'ACCOUNT_SUSPENDED' } } });
    await prepare(page, ip);
    await page.goto('/auth');
    await enter(page, 'Email', email);
    await typeCode(page, '123456');
    await expect(page.getByTestId('otp-error-ACCOUNT_SUSPENDED')).toBeVisible();
  });
});

test.describe('browser mutation guard', () => {
  test('a request without X-Requested-With, or from another origin, never reaches identity-service', async ({ page, upstream, baseURL }) => {
    const { ip, email } = unique();
    identityFixtures(upstream);
    await page.goto('/auth');
    const data = { contact: { value: email, type: 'EMAIL' } };
    const noHeader = await page.request.post('/api/identity/challenge', { data, headers: { origin: baseURL!, 'x-forwarded-for': ip } });
    expect(noHeader.status()).toBe(403);
    expect((await noHeader.json()).errorCode).toBe('ORIGIN_REJECTED');
    const foreign = await page.request.post('/api/identity/challenge', { data, headers: { origin: 'https://evil.example', 'x-requested-with': 'pml-web', 'x-forwarded-for': ip } });
    expect(foreign.status()).toBe(403);
    // The convention is one header: the old name is not accepted.
    const legacy = await page.request.post('/api/identity/challenge', { data, headers: { origin: baseURL!, 'x-pml-csrf': '1', 'x-forwarded-for': ip } });
    expect(legacy.status()).toBe(403);
    expect(requests(upstream, '/api/internal/auth/challenges')).toHaveLength(0);
    const ok = await page.request.post('/api/identity/challenge', { data, headers: { origin: baseURL!, 'x-requested-with': 'pml-web', 'x-forwarded-for': ip } });
    expect(ok.status()).toBe(202);
  });

  test('parking the cart is rate limited per client address', async ({ page, baseURL }) => {
    const { ip } = unique();
    const post = (forIp: string) =>
      page.request.post('/api/checkout/intent', {
        data: { eventId: 'e1', quantities: { t1: 1 } },
        headers: { origin: baseURL!, 'x-requested-with': 'pml-web', 'x-forwarded-for': forIp },
      });
    let last = 0;
    for (let i = 0; i < 31; i++) last = (await post(ip)).status();
    expect(last).toBe(429);
    const other = unique();
    expect((await post(other.ip)).status()).toBe(200);
  });
});
