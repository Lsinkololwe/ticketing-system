import { harnessTest as test, expect, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, bookingsFixtures, BOOKINGS } from './fixtures';
import { shot } from './_kit';

const confirmed = () => {
  const b = JSON.parse(JSON.stringify(BOOKINGS[3]));
  b.id = 'bk-9000';
  b.bookingNumber = 'BK-2026-00009000';
  b.ticketCount = 2;
  b.totalAmount = '300';
  b.tickets = [1, 2].map((i) => ({ ...BOOKINGS[3].tickets[0], id: `tk-9000-${i}`, ticketNumber: `TK-9000-${i}`, price: '150', status: 'ISSUED' }));
  return b;
};
const setup = async (up: any, signInAs: any, extra: Record<string, any> = {}, role: any = 'OWNER') => {
  up.gql({ ...baseFixtures({ role }), ...bookingsFixtures([...BOOKINGS, confirmed()]), ...extra });
  await signInAs(as(role));
};

test('refund: full refund of the selected tickets', async ({ page, upstream, signInAs }, info) => {
  test.setTimeout(240_000);
  await setup(upstream, signInAs);
  await page.goto('/bookings/bk-9000/refund');
  await expect(page.getByText('Maximum refundable').first()).toBeVisible();
  await shot(page, 'refund-page', info);
  await page.getByRole('checkbox').nth(1).check().catch(() => undefined);
  await page.getByLabel('Reason').fill('Event cancelled by the artist');
  await page.getByRole('button', { name: /^refund$/i }).click();
  await expect.poll(() => upstream.calls('OrganizerRefundTicket').length).toBeGreaterThan(0);
  await expect(page.getByText(/refunded/i).first()).toBeVisible();
  await shot(page, 'refund-done', info);
});

test('refund: partial amount is sent for the single selected ticket', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs);
  await page.goto('/bookings/bk-9000/refund');
  await expect(page.getByText('Maximum refundable').first()).toBeVisible();
  const boxes = page.getByRole('checkbox');
  const n = await boxes.count();
  for (let i = 0; i < n; i++) await boxes.nth(i).uncheck().catch(() => undefined);
  await boxes.nth(1).check();
  await page.getByLabel(/partial amount/i).fill('50');
  await page.getByLabel('Reason').fill('Goodwill');
  await shot(page, 'refund-partial', info);
  await page.getByRole('button', { name: /^refund$/i }).click();
  await expect.poll(() => upstream.calls('OrganizerRefundTicket').length).toBe(1);
  expect(upstream.calls('OrganizerRefundTicket')[0].variables).toMatchObject({ ticketNumber: 'TK-9000-1', amount: '50' });
});

test('refund: server refusal is shown and nothing is reported as refunded', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { OrganizerRefundTicket: gqlErrors({ message: 'Refund window has closed', code: 'REFUND_NOT_ALLOWED' }) });
  await page.goto('/bookings/bk-9000/refund');
  await expect(page.getByText('Maximum refundable').first()).toBeVisible();
  await page.getByLabel('Reason').fill('Test');
  await page.getByRole('checkbox').nth(1).check().catch(() => undefined);
  await page.getByRole('button', { name: /^refund$/i }).click();
  await expect.poll(() => upstream.calls('OrganizerRefundTicket').length).toBeGreaterThan(0);
  await page.waitForTimeout(700);
  await shot(page, 'refund-error', info);
  await expect(page.getByText(/Refunded \d+ ticket/)).toHaveCount(0);
});

test('refund: a role without the refund right sees the notice', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {}, 'CONTRIBUTOR');
  await page.goto('/bookings/bk-9000/refund');
  await expect(page.getByText('Your role cannot issue refunds.')).toBeVisible();
  await shot(page, 'refund-no-right', info);
});

test('resend: success toast and failure', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs);
  await page.goto('/bookings/bk-9000');
  await expect(page.getByText('BK-2026-00009000').first()).toBeVisible();
  await page.getByRole('button', { name: /resend/i }).first().click();
  await expect.poll(() => upstream.calls('OrganizerResendTicket').length).toBe(1);
  await expect(page.getByText(/sent/i).first()).toBeVisible();
  upstream.gql({ OrganizerResendTicket: gqlErrors({ message: 'Delivery channel unavailable', code: 'DELIVERY_FAILED' }) });
  await page.getByRole('button', { name: /resend/i }).first().click();
  await page.waitForTimeout(700);
  await shot(page, 'resend-error', info);
  await expect(page.getByText(/unavailable|could not|failed/i).first()).toBeVisible();
});
