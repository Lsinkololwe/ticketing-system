import { harnessTest as test, expect, captureConsole, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { catalog, EVENT_DETAIL, RULES, signedInBase } from './fixtures';
import { settle, shot } from './_kit';

test.describe('event page', () => {
  test('signed out: tiers, quantity, totals, public rules wording', async ({ page, upstream }, info) => {
    upstream.gql(catalog());
    const log = captureConsole(page);
    await page.goto('/events/e1');
    await expect(page.getByRole('heading', { level: 1, name: 'Fixture Sunset Sessions' })).toBeVisible();
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await expect(page.getByRole('complementary', { name: 'Your tickets' })).toContainText('K 300');
    // Hold wording comes from publicPlatformRules, with no sign-in.
    await expect(page.getByText(`held for ${RULES.reservationHoldMinutes} minutes`)).toBeVisible();
    await shot(page, 'event', info);
    await page.getByRole('tab', { name: 'Venue and access' }).click();
    await shot(page, 'event-venue', info);
    await page.getByRole('tab', { name: 'Good to know' }).click();
    // Refund policy text for MODERATE is the platform's, readable signed out; no sign-in workaround.
    await expect(page.getByText('Full refund until 7 days before, half until 2 days.')).toBeVisible();
    await expect(page.getByText(/Sign in to see the refund rules/)).toHaveCount(0);
    await expect(page.getByText(/Refund requests close 2 days before the event/)).toBeVisible();
    await shot(page, 'event-know', info);
    await page.getByRole('tab', { name: 'Schedule' }).click();
    await shot(page, 'event-schedule', info);
    expect(upstream.calls('BuyerPlatformRules').length).toBeGreaterThan(0);
    await settle(page, upstream, log);
  });

  test('signed in sees the same rules from the same operation', async ({ page, upstream, signInAs }) => {
    upstream.gql(signedInBase());
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/events/e1');
    await page.getByRole('tab', { name: 'Good to know' }).click();
    await expect(page.getByText('Full refund until 7 days before, half until 2 days.')).toBeVisible();
    expect(upstream.calls('BuyerPlatformRules').length).toBeGreaterThan(0);
    expect(upstream.requests.some((r) => r.query.includes('platformRules {') && !r.query.includes('publicPlatformRules'))).toBe(false);
  });

  test('rules unavailable: honest fallback text, page still works', async ({ page, upstream }, info) => {
    upstream.gql({ ...catalog(), BuyerPlatformRules: gqlErrors({ message: 'down' }) });
    await page.goto('/events/e1');
    await page.getByRole('tab', { name: 'Good to know' }).click();
    await expect(page.getByText('The refund rules for this event are not available right now.')).toBeVisible();
    await shot(page, 'event-rules-down', info);
  });

  test('empty: unknown event', async ({ page, upstream }, info) => {
    upstream.gql({ ...catalog(), EventPage: { event: null } });
    await page.goto('/events/nope');
    await expect(page.getByText('We could not find that event')).toBeVisible();
    await shot(page, 'event-missing', info);
  });

  test('error: event query fails', async ({ page, upstream }, info) => {
    upstream.gql({ ...catalog(), EventPage: gqlErrors({ message: 'boom' }) });
    await page.goto('/events/e1');
    await expect(page.getByTestId('error-state')).toBeVisible();
    await shot(page, 'event-error', info);
  });

  test('sold out tier cannot be added; detail has the sold-out state', async ({ page, upstream }) => {
    upstream.gql(catalog());
    await page.goto('/events/e1');
    await expect(page.getByRole('group', { name: 'VVIP' })).toContainText(/sold out/i);
    await expect(page.getByRole('button', { name: 'Add one VVIP ticket' })).toHaveCount(0);
    void EVENT_DETAIL;
  });
});
