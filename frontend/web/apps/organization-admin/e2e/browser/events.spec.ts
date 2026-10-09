import { harnessTest as test, expect, captureConsole, gqlErrors, delayed } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, dashboardFixtures, eventsListFixtures, eventDetailFixtures, EVENTS } from './fixtures';
import { settle, shot } from './_kit';

const allow = { allowMissing: [/imageUrl$/, /logoUrl$/, /bannerUrl$/, /^\$\.myOwnedOrganization\./, /bannerImageUrl$/] };
const setup = async (up: any, signInAs: any, extra: Record<string, any> = {}, role: any = 'OWNER') => {
  up.gql({ ...baseFixtures({ role }), ...extra });
  await signInAs(as(role));
};

test('events list populated + status filter', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, eventsListFixtures());
  const log = captureConsole(page);
  await page.goto('/events');
  await expect(page.getByText('Lusaka Sunset Sessions').first()).toBeVisible();
  await shot(page, 'events', info);
  const draft = page.getByRole('tab', { name: /draft/i }).or(page.getByRole('button', { name: /^draft/i })).first();
  await draft.click();
  await expect(page.getByText('Kabwe Gospel Night').first()).toBeVisible();
  await expect(page.getByText('Lusaka Sunset Sessions')).toHaveCount(0);
  await shot(page, 'events-draft', info);
  await settle(page, upstream, log, allow);
});

test('events list empty / error / loading', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, eventsListFixtures([]));
  await page.goto('/events');
  await page.waitForLoadState('networkidle');
  await shot(page, 'events-empty', info);
  upstream.gql({ OrgEventsConnection: gqlErrors({ message: 'Events are unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForLoadState('networkidle');
  await shot(page, 'events-error', info);
});

for (const tab of ['overview', 'tiers', 'promos', 'bookings', 'check-in', 'access', 'notify', 'analytics']) {
  test(`event detail tab ${tab}`, async ({ page, upstream, signInAs }, info) => {
    await setup(upstream, signInAs, { ...eventsListFixtures(), ...eventDetailFixtures('ev1') });
    await page.goto('/events/ev1');
    await expect(page.getByRole('heading', { name: 'Lusaka Sunset Sessions' }).first()).toBeVisible();
    if (tab !== 'overview') {
      const label = { promos: /promo/i, 'check-in': /check-in/i, access: /team access/i, notify: /notify/i, tiers: /ticket tiers/i, bookings: /^bookings/i, analytics: /analytics/i }[tab] as RegExp;
      await page.getByRole('tab', { name: label }).first().click({ timeout: 8000 });
    }
    await page.waitForLoadState('networkidle').catch(() => undefined);
    await shot(page, `event-${tab}`, info);
    console.log(`TAB ${tab} unhandled`, JSON.stringify([...new Set(upstream.unhandled)]), 'missing', JSON.stringify([...new Set(upstream.missing)].slice(0, 8)));
  });
}
