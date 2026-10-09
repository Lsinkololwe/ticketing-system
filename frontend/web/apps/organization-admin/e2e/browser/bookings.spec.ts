import { harnessTest as test, expect, captureConsole, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, bookingsFixtures } from './fixtures';
import { shot } from './_kit';

const setup = async (up: any, signInAs: any, extra: Record<string, any> = {}, role: any = 'OWNER') => {
  up.gql({ ...baseFixtures({ role }), ...extra });
  await signInAs(as(role));
};
const diag = (up: any, n: string) => console.log(`DIAG ${n} unhandled`, JSON.stringify([...new Set(up.unhandled)]), 'missing', JSON.stringify([...new Set(up.missing)].slice(0, 12)));

test('bookings list + quick view', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, bookingsFixtures());
  await page.goto('/bookings');
  await expect(page.getByText('BK-2026-00003462').first()).toBeVisible();
  await shot(page, 'bookings', info);
  await page.getByRole('button', { name: /^(view|quick view)/i }).first().click();
  await page.waitForTimeout(500);
  await shot(page, 'bookings-quick', info);
  diag(upstream, 'bookings');
});

test('bookings refund requests tab', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, bookingsFixtures());
  await page.goto('/bookings');
  await page.getByRole('tab', { name: /refund requests/i }).click();
  await page.waitForTimeout(600);
  await shot(page, 'bookings-refunds', info);
  diag(upstream, 'refunds');
});

test('bookings empty and error', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, bookingsFixtures([], []));
  await page.goto('/bookings');
  await page.waitForLoadState('networkidle');
  await shot(page, 'bookings-empty', info);
  upstream.gql({ OrganizerBookings: gqlErrors({ message: 'Bookings unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForLoadState('networkidle');
  await shot(page, 'bookings-error', info);
});

test('booking detail + resend + refund page', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, bookingsFixtures());
  await page.goto('/bookings/bk-3458');
  await expect(page.getByText('BK-2026-00003458').first()).toBeVisible();
  await shot(page, 'booking', info);
  await page.getByRole('button', { name: /resend/i }).first().click().catch(() => undefined);
  await page.waitForTimeout(500);
  await shot(page, 'booking-resend', info);
  expect(upstream.calls('OrganizerResendTicket').length).toBeGreaterThan(0);
  await page.goto('/bookings/bk-3458/refund');
  await page.waitForLoadState('networkidle');
  await shot(page, 'booking-refund', info);
  diag(upstream, 'booking');
});

test('bookings: Open and Quick view land where expected; role without refund right gets no refund action', async ({ page, upstream, signInAs }) => {
  await setup(upstream, signInAs, bookingsFixtures(), 'CONTRIBUTOR');
  await page.goto('/bookings');
  await expect(page.getByText('BK-2026-00003458').first()).toBeVisible();
  await page.getByRole('button', { name: /^quick view/i }).first().click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('button', { name: /process refund/i }).or(page.getByRole('link', { name: /process refund/i }))).toHaveCount(0);
  await page.keyboard.press('Escape');
  await page.getByRole('link', { name: /open booking BK-2026-00003462/i }).click();
  await expect(page).toHaveURL(/\/bookings\/bk-3462$/);
});
