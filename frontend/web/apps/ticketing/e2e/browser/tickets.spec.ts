import { harnessTest as test, expect, captureConsole, httpFailure, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { BOOKINGS, booking, bookings, iso, signedInBase, ticket } from './fixtures';
import { settle, shot } from './_kit';

const retryable = { extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', retryable: true } };

test.describe('my tickets', () => {
  test('populated: bookings, tabs, QR dialog', async ({ page, upstream, signInAs }, info) => {
    upstream.gql(signedInBase());
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto('/my-tickets');
    await expect(page.getByRole('heading', { level: 1, name: 'My tickets' })).toBeVisible();
    await expect(page.getByRole('article', { name: 'Booking for Fixture Sunset Sessions' })).toBeVisible();
    await expect(page.getByText('Remind me 24 hours before')).toBeVisible();
    await shot(page, 'tickets', info);
    await page.getByRole('button', { name: 'Show QR' }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await shot(page, 'tickets-qr', info);
    await page.keyboard.press('Escape');
    await page.getByRole('tab', { name: /Past/ }).click();
    await expect(page.getByRole('article', { name: 'Booking for Fixture Comedy Night' })).toBeVisible();
    await shot(page, 'tickets-past', info);
    await page.getByRole('tab', { name: /Refunds/ }).click();
    await expect(page.getByText('No refund requests')).toBeVisible();
    await shot(page, 'tickets-refunds-empty', info);
    await settle(page, upstream, log, { axeExclude: ['[role=dialog]'] });
  });

  test('empty: no bookings', async ({ page, upstream, signInAs }, info) => {
    upstream.gql(signedInBase({ bookings: [] }));
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto('/my-tickets');
    await expect(page.getByText('No upcoming tickets')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Find an event' })).toHaveAttribute('href', '/');
    await shot(page, 'tickets-empty', info);
    await settle(page, upstream, log);
  });

  test('error: bookings query fails with retry', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), BuyerMyBookings: gqlErrors({ message: 'boom', ...retryable }) });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/my-tickets');
    await expect(page.getByTestId('error-state')).toBeVisible();
    await shot(page, 'tickets-error', info);
    upstream.gql({ BuyerMyBookings: bookings(BOOKINGS) });
    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByRole('article', { name: 'Booking for Fixture Sunset Sessions' })).toBeVisible();
  });

  test('offline: gateway unreachable', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), BuyerMyBookings: httpFailure(503) });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/my-tickets');
    await expect(page.getByTestId('error-state')).toBeVisible();
    await shot(page, 'tickets-offline', info);
  });

  test('transfer: look up the recipient, confirm, offer is created', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({
      ...signedInBase(),
      BuyerTransferRecipient: { transferRecipient: { __typename: 'TransferRecipient', displayName: 'Chanda M.', maskedContact: '+260 96 *** 1234' } },
      BuyerInitiateTicketTransfer: { initiateTicketTransfer: { __typename: 'TicketTransfer', id: 'tr1', ticketId: 'b1-1', status: 'PENDING', recipientMasked: '+260 96 *** 1234', toDisplayName: 'Chanda M.', expiresAt: iso(3) } },
    });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/my-tickets');
    await page.getByRole('button', { name: 'Transfer' }).first().click();
    const dlg = page.getByRole('dialog');
    await expect(dlg).toBeVisible();
    await shot(page, 'tickets-transfer-1', info);
    await dlg.getByRole('textbox').first().fill('961231234');
    await dlg.getByRole('button', { name: /continue|find|next/i }).first().click();
    await expect(dlg.getByText('Chanda M.')).toBeVisible();
    await shot(page, 'tickets-transfer-2', info);
    await dlg.getByRole('button', { name: /^transfer/i }).last().click();
    await expect.poll(() => upstream.calls('BuyerInitiateTicketTransfer').length).toBe(1);
  });

  test('transfer: pending outgoing and incoming offers', async ({ page, upstream, signInAs }, info) => {
    const t = (o: object) => ({ __typename: 'TicketTransfer', id: 'tr1', ticketId: 'b1-2', ticketNumber: 'TKT-1002', bookingNumber: 'BK-2026-0001', eventId: 'e1', eventTitle: 'Fixture Sunset Sessions', status: 'PENDING', direction: 'OUTGOING', fromDisplayName: 'Mwila B.', toDisplayName: 'Chanda M.', recipientMasked: '+260 96 *** 1234', note: null, createdAt: iso(-1), expiresAt: iso(3), resolvedAt: null, ...o });
    const page_ = (rows: unknown[]) => ({ myTicketTransfers: { __typename: 'TicketTransferPage', data: rows, pagination: { __typename: 'Pagination', totalElements: rows.length, hasNext: false } } });
    upstream.gql({
      ...signedInBase({ bookings: [booking('b1', { tickets: [ticket('b1-1', 1), ticket('b1-2', 2, { transferPending: true })] })] }),
      BuyerMyTicketTransfers: (v) => (v.direction === 'INCOMING' ? page_([t({ id: 'tr9', direction: 'INCOMING', fromDisplayName: 'Thandi K.', ticketId: 'x9' })]) : page_([t({})])),
    });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/my-tickets');
    await expect(page.getByRole('region', { name: 'Tickets offered to you' })).toBeVisible();
    await expect(page.getByText(/is pending until they accept it/)).toBeVisible();
    await shot(page, 'tickets-transfers', info);
  });

  test('refund: quote dialog shows policy and amount', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({
      ...signedInBase(),
      CalculateRefundAmount: { calculateRefundAmount: { __typename: 'RefundCalculation', ticketId: 'b1-1', ticketNumber: 'TKT-1001', eventId: 'e1', eventDate: iso(20), originalAmount: '150', daysBeforeEvent: 20, refundPercentage: 100, refundAmount: '150', platformRetains: '0', policyApplied: 'MODERATE', isEligible: true, ineligibleReason: null } },
    });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/my-tickets');
    await page.getByRole('button', { name: 'Request refund' }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('dialog').getByText(/100%/).first()).toBeVisible();
    await shot(page, 'tickets-refund', info);
  });
});
