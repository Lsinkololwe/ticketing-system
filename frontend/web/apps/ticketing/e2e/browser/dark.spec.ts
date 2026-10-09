import { harnessTest as test, expect, captureConsole, axe } from '../../../../e2e-harness/browser/playwright';
import { iso, signedInBase } from './fixtures';
import { shot, settle } from './_kit';

const reservation = { __typename: 'TicketReservation', id: 'res-0001', eventId: 'e1', totalAmount: '300', currency: 'ZMW', status: 'HELD', expiresAt: iso(0.007), remainingSeconds: 600, discountAmount: '0', promoCodeApplied: null, paymentIntentId: null, confirmedAt: null, releasedAt: null, failedAt: null, failureReason: null, items: [{ __typename: 'ReservationItem', ticketTierId: 't1', tierName: 'General', quantity: 2, unitPrice: '150', subtotal: '300' }] };

test.describe('dark theme (prefers-color-scheme: dark)', () => {
  test.beforeEach(async ({ page, upstream, signInAs }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    upstream.gql({ ...signedInBase(), GetReservation: { reservation }, ReserveTickets: { reserveTickets: reservation } });
    await signInAs({ roles: ['CUSTOMER'] });
  });

  for (const [name, path] of [['home', '/'], ['event', '/events/e1'], ['checkout', '/events/e1/book?r=res-0001'], ['tickets', '/my-tickets'], ['profile', '/profile']] as const) {
    test(`${name}: renders dark, no console errors, no serious contrast failures`, async ({ page, upstream }, info) => {
      const log = captureConsole(page);
      await page.goto(path);
      await page.waitForLoadState('networkidle').catch(() => undefined);
      const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
      const [r, g, b] = bg.match(/\d+/g)!.map(Number);
      expect(r + g + b, `body background ${bg} is dark`).toBeLessThan(200);
      await shot(page, `dark-${name}`, info);
      await settle(page, upstream, log, { a11y: false });
      const bad = (await axe(page)).filter((v) => v.impact === 'serious' || v.impact === 'critical');
      expect(bad.map((v) => `${v.id}: ${v.detail} @ ${v.nodes.slice(0, 3).join(' | ')}`)).toEqual([]);
    });
  }

  test('the theme button flips and remembers the choice', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await page.getByRole('button', { name: 'Switch light or dark theme' }).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  });
});
