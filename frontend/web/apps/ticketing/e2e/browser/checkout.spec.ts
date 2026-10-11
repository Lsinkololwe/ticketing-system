import { harnessTest as test, expect, captureConsole, gqlErrors, delayed } from '../../../../e2e-harness/browser/playwright';
import { BOOKINGS, bookings, iso, signedInBase, ticket, booking } from './fixtures';
import { settle, shot } from './_kit';

const reservation = (o: Record<string, unknown> = {}) => ({
  __typename: 'TicketReservation', id: 'res-0001', eventId: 'e1', totalAmount: '300', currency: 'ZMW', status: 'HELD', expiresAt: iso(0.007), remainingSeconds: 600, discountAmount: '0', promoCodeApplied: null,
  paymentIntentId: null, confirmedAt: null, releasedAt: null, failedAt: null, failureReason: null,
  items: [{ __typename: 'ReservationItem', ticketTierId: 't1', tierName: 'General', quantity: 2, unitPrice: '150', subtotal: '300' }], ...o,
});

test.describe('checkout', () => {
  test('signed out, nothing parked: asks to choose tickets', async ({ page, upstream }, info) => {
    upstream.gql({ ...signedInBase() });
    const log = captureConsole(page);
    await page.goto('/events/e1/book');
    await expect(page.getByText('Choose your tickets first')).toBeVisible();
    await shot(page, 'checkout-empty', info);
    await settle(page, upstream, log, { allowMissing: [/.*/] });
  });

  test('signed out with tickets chosen: identify step', async ({ page, upstream }, info) => {
    upstream.gql({ ...signedInBase() });
    await page.goto('/events/e1');
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('complementary', { name: 'Your tickets' }).getByRole('button', { name: 'Reserve tickets' }).click();
    await expect(page).toHaveURL(/\/events\/e1\/book/);
    await expect(page.getByRole('heading', { level: 1, name: 'Checkout' })).toBeVisible();
    await shot(page, 'checkout-identify', info);
  });

  test('signed in: reserve, contact, pay, approval, confirmation', async ({ page, upstream, signInAs }, info) => {
    let res = reservation();
    let confirmed = false;
    upstream.gql({
      ...signedInBase(),
      ReserveTickets: () => ({ reserveTickets: res }),
      GetReservation: () => ({ reservation: res }),
      PayReservation: () => {
        res = reservation({ paymentIntentId: 'pi-1' });
        return { payReservation: { __typename: 'PaymentInitiation', paymentIntentId: 'pi-1', transactionRef: 'TX-1', paymentStatus: 'PENDING', reservationId: 'res-0001' } };
      },
      BuyerMyBookings: () => bookings(confirmed ? [booking('b9', { reservationId: 'res-0001', bookingNumber: 'BK-2026-0099', tickets: [ticket('b9-1', 9), ticket('b9-2', 10)] }), ...BOOKINGS] : BOOKINGS),
    });
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto('/events/e1');
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('complementary', { name: 'Your tickets' }).getByRole('button', { name: 'Reserve tickets' }).click();
    await expect(page).toHaveURL(/\/events\/e1\/book\?r=res-0001/);
    await expect(page.getByText(/General/).first()).toBeVisible();
    await shot(page, 'checkout-1-reserve', info);
    await page.getByRole('button', { name: /continue/i }).first().click();
    await expect(page.getByText('Contact details')).toBeVisible();
    await shot(page, 'checkout-2-contact', info);
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    await shot(page, 'checkout-3-pay', info);
    await page.getByRole('textbox').first().fill('0961234567');
    await page.getByRole('button', { name: /pay|send/i }).last().click();
    await expect.poll(() => upstream.calls('PayReservation').length).toBe(1);
    await shot(page, 'checkout-4-approval', info);
    res = reservation({ status: 'CONFIRMED', paymentIntentId: 'pi-1', confirmedAt: iso(0) });
    confirmed = true;
    await expect(page.getByText(/BK-2026-0099/)).toBeVisible({ timeout: 20_000 });
    await shot(page, 'checkout-5-confirmed', info);
    await settle(page, upstream, log, { a11y: false });
  });

  test('two rapid taps on Pay carry the same idempotency key (TS-6)', async ({ page, upstream, signInAs }, info) => {
    const res = reservation();
    // A delayed response widens the window a true double-tap race falls into: exactly the
    // mobile-money scenario R6 exists for — a buyer unsure whether their first tap registered,
    // tapping again before the screen has visibly changed.
    upstream.gql({
      ...signedInBase(),
      ReserveTickets: () => ({ reserveTickets: res }),
      GetReservation: () => ({ reservation: res }),
      PayReservation: delayed(500, { payReservation: { __typename: 'PaymentInitiation', paymentIntentId: 'pi-1', transactionRef: 'TX-1', paymentStatus: 'PENDING', reservationId: 'res-0001' } }),
    });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/events/e1');
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('complementary', { name: 'Your tickets' }).getByRole('button', { name: 'Reserve tickets' }).click();
    await expect(page).toHaveURL(/\/events\/e1\/book\?r=res-0001/);
    await page.getByRole('button', { name: /continue/i }).first().click();
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    await page.getByRole('textbox').first().fill('0961234567');
    const pay = page.getByRole('button', { name: /pay|send/i }).last();
    // Two synchronous DOM clicks in one task, bypassing Playwright's own actionability wait
    // between clicks, so both land before React can flip the button to its disabled/loading state.
    await pay.evaluate((el: HTMLButtonElement) => {
      el.click();
      el.click();
    });
    await page.waitForTimeout(700);
    const calls = upstream.calls('PayReservation');
    // The page holds the second tap while the first is in flight, so one request leaves; if a build ever lets
    // both through, they must still carry the same key. Zero would mean the tap never registered.
    expect(calls.length, 'the tap must reach the upstream').toBeGreaterThanOrEqual(1);
    expect(calls.length, 'a second tap must not become a second charge request').toBeLessThanOrEqual(2);
    const keys = calls.map((c) => (c.variables as { input?: { idempotencyKey?: string } }).input?.idempotencyKey);
    expect(new Set(keys).size, 'every rapid tap on the same reservation must carry one key').toBe(1);
    expect(keys[0], 'a real key, not an accidental empty string').toBeTruthy();
    await shot(page, 'checkout-double-submit', info);
  });

  test('declined payment shows the reason and stays on pay', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({
      ...signedInBase(),
      ReserveTickets: { reserveTickets: reservation() },
      GetReservation: { reservation: reservation() },
      PayReservation: gqlErrors({ message: 'declined', extensions: { errorCode: 'PAYMENT_DECLINED', classification: 'CONFLICT' } }),
    });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/events/e1');
    await page.getByRole('button', { name: 'Add one General ticket' }).click();
    await page.getByRole('complementary', { name: 'Your tickets' }).getByRole('button', { name: 'Reserve tickets' }).click();
    await page.getByRole('button', { name: /continue/i }).first().click();
    await page.getByRole('button', { name: 'Continue to payment' }).click();
    await page.getByRole('textbox').first().fill('0961234567');
    await page.getByRole('button', { name: /pay|send/i }).last().click();
    await expect(page.getByRole('alert').first()).toBeVisible();
    await shot(page, 'checkout-declined', info);
  });

  test('expired hold and released reservation end states', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), GetReservation: { reservation: reservation({ status: 'EXPIRED', expiresAt: iso(-1) }) } });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/events/e1/book?r=res-0001');
    await expect(page.getByText('Your hold has expired')).toBeVisible();
    await shot(page, 'checkout-expired', info);
  });

  test('reservation cannot be loaded: error state', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), GetReservation: gqlErrors({ message: 'nope', extensions: { errorCode: 'NOT_FOUND', classification: 'NOT_FOUND' } }) });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/events/e1/book?r=res-0001');
    await shot(page, 'checkout-load-error', info);
  });
});
