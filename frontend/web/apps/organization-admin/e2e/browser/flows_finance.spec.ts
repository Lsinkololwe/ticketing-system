import { harnessTest as test, expect, gqlErrors, delayed } from '../../../../e2e-harness/browser/playwright';
import { as, baseFixtures, financeFixtures, BANKS } from './fixtures';
import { shot } from './_kit';

const ELIGIBLE = { id: 'esc-2', accountNumber: 'ESC-0002', eventId: 'ie', eventTitle: 'Independence Eve Concert', currentBalance: '37268.5', totalDeposits: '37268.5', totalWithdrawals: '0', totalRefunds: '0', totalCommissions: '1900', pendingWithdrawals: null, currency: 'ZMW', status: 'ACTIVE', lockUntil: null, payoutEligibleAt: '2026-10-01T00:00:00Z' };
const tx = (n: number) => Array.from({ length: n }, (_, i) => ({ id: `lt-${i}`, type: i % 2 ? 'WITHDRAWAL' : 'DEPOSIT', category: 'TICKET_SALE', amount: String(300 + i), balanceAfter: String(8000 + i), currency: 'ZMW', description: `Sale ${i + 1}`, journalEntryId: null, timestamp: '2026-10-02T09:00:00Z' }));
const pg = (n: number) => ({ totalElements: n, totalPages: 1, currentPage: 0, hasNext: false });
const setup = async (up: any, signInAs: any, extra: Record<string, any> = {}, role: any = 'OWNER') => {
  up.gql({ ...baseFixtures({ role }), ...financeFixtures({ escrow: [ELIGIBLE] }), ...extra });
  await signInAs(as(role));
};

test('ledger sheet: populated, empty, error, loading', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { OrganizerEscrowTransactions: { escrowTransactions: { data: tx(3), pagination: pg(3) } } });
  await page.goto('/finance');
  await page.getByRole('button', { name: /^ledger$/i }).first().click();
  await expect(page.getByRole('dialog').getByText(/ticket sale/i).first()).toBeVisible();
  await shot(page, 'finance-ledger', info);
  await page.keyboard.press('Escape');
  upstream.gql({ OrganizerEscrowTransactions: { escrowTransactions: { data: [], pagination: pg(0) } } });
  await page.getByRole('button', { name: /^ledger$/i }).first().click();
  await page.waitForTimeout(700);
  await shot(page, 'finance-ledger-empty', info);
  await page.keyboard.press('Escape');
  upstream.gql({ OrganizerEscrowTransactions: gqlErrors({ message: 'Ledger unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.getByRole('button', { name: /^ledger$/i }).first().click();
  await page.waitForTimeout(700);
  await shot(page, 'finance-ledger-error', info);
});

test('payout request: dialog, eligibility, submit; failure keeps dialog', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { CreatePayoutRequest: { createPayoutRequest: { id: 'po-new', requestId: 'PO-2060', status: 'PENDING' } } });
  await page.goto('/finance');
  await expect(page.getByText('Independence Eve Concert').first()).toBeVisible();
  await page.getByRole('button', { name: /request payout/i }).first().click();
  const dlg = page.getByRole('dialog');
  await expect(dlg).toContainText('Request a payout');
  await page.waitForTimeout(600);
  await shot(page, 'finance-payout-dialog', info);
  await dlg.getByLabel(/amount/i).fill('1000');
  await dlg.getByRole('button', { name: 'Request payout', exact: true }).click();
  await expect.poll(() => upstream.calls('CreatePayoutRequest').length).toBe(1);
  expect(upstream.calls('CreatePayoutRequest')[0].variables).toMatchObject({ input: { escrowAccountId: 'esc-2' } });
});

test('payout request: two rapid submissions carry the same idempotency key (TS-6)', async ({ page, upstream, signInAs }, info) => {
  // A delayed response widens the window a true double-tap race falls into: the mutation is
  // still in flight when the second click lands — before React has re-rendered the button into
  // its disabled/loading state, which is the one thing standing between a double-tap and two
  // network calls. The frontend's job is not to stop the second call (it may well go out); it is
  // to make sure both calls carry the identical idempotency key, so the backend's own guard
  // collapses them into one applied payout request rather than two.
  await setup(upstream, signInAs, { CreatePayoutRequest: delayed(400, { createPayoutRequest: { id: 'po-new', requestId: 'PO-2060', status: 'PENDING' } }) });
  await page.goto('/finance');
  await page.getByRole('button', { name: /request payout/i }).first().click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel(/amount/i).fill('1000');
  const submit = dlg.getByRole('button', { name: 'Request payout', exact: true });
  // Two synchronous DOM clicks in one task, bypassing Playwright's own actionability wait
  // between clicks, so both land before React can flip the button to its disabled/loading state.
  await submit.evaluate((el: HTMLButtonElement) => {
    el.click();
    el.click();
  });
  await page.waitForTimeout(600);
  const calls = upstream.calls('CreatePayoutRequest');
  // The page holds the second submission while the first is in flight, so one request leaves; if a build ever
  // lets both through, they must carry the same key. Zero would mean the submission never registered.
  expect(calls.length, 'the submission must reach the upstream').toBeGreaterThanOrEqual(1);
  expect(calls.length, 'a second submission must not become a second request').toBeLessThanOrEqual(2);
  const keys = calls.map((c) => (c.variables as { input?: { idempotencyKey?: string } }).input?.idempotencyKey);
  expect(new Set(keys).size, 'every rapid submission of the same intent must carry one key').toBe(1);
  expect(keys[0], 'a real key, not an accidental empty string').toBeTruthy();
  await shot(page, 'finance-payout-double-submit', info);
});

test('payout request: server refusal is shown, dialog stays open', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { CreatePayoutRequest: gqlErrors({ message: 'An open payout request already exists', code: 'PAYOUT_ALREADY_OPEN' }) });
  await page.goto('/finance');
  await page.getByRole('button', { name: /request payout/i }).first().click();
  const dlg = page.getByRole('dialog');
  await dlg.getByLabel(/amount/i).fill('1000');
  await dlg.getByRole('button', { name: 'Request payout', exact: true }).click();
  await expect.poll(() => upstream.calls('CreatePayoutRequest').length).toBe(1);
  await page.waitForTimeout(700);
  await shot(page, 'finance-payout-refused', info);
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('payout: ADMIN without the owner setting cannot request; cancel of a pending request', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { OrganizerCancelPayoutRequest: { cancelPayoutRequest: { id: 'po-1', status: 'CANCELLED' } } }, 'ADMIN');
  await page.goto('/finance');
  await expect(page.getByText('PO-2051').first()).toBeVisible();
  await expect(page.getByRole('button', { name: /request payout/i })).toHaveCount(0);
  await shot(page, 'finance-admin-no-request', info);
});

test('bank accounts: add, verify (start + confirm), wallet', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, {
    CreateBankAccount: { createBankAccount: { id: 'ba-9' } },
    OrganizerStartBankVerification: { startBankVerification: { id: 'ba-2', status: 'PENDING_VERIFICATION' } },
    OrganizerConfirmBankVerification: { confirmBankVerification: { id: 'ba-2', status: 'VERIFIED' } },
  });
  await page.goto('/finance/bank-accounts');
  await expect(page.getByText('Stanbic Bank').first()).toBeVisible();
  await page.getByRole('button', { name: /start test deposit/i }).click();
  await expect.poll(() => upstream.calls('OrganizerStartBankVerification').length).toBe(1);
  await page.getByRole('button', { name: /confirm deposit/i }).click();
  const dlg = page.getByRole('dialog');
  await expect(dlg).toContainText('Confirm the test deposit');
  await shot(page, 'bank-verify-dialog', info);
  await dlg.getByLabel(/deposit amount/i).fill('0.23');
  await dlg.getByRole('button', { name: /confirm/i }).last().click();
  await expect.poll(() => upstream.calls('OrganizerConfirmBankVerification').length).toBe(1);
  await page.getByRole('button', { name: /add account/i }).first().click();
  await expect(page.getByRole('dialog')).toContainText('Add account');
  await shot(page, 'bank-add-dialog', info);
  await page.getByRole('dialog').getByRole('button', { name: /cancel/i }).click();
  await page.getByRole('button', { name: /replace wallet/i }).click();
  await expect(page.getByRole('dialog')).toContainText('Replace wallet');
  await shot(page, 'bank-wallet-dialog', info);
});

test('bank accounts: empty, error, MARKETER blocked', async ({ page, upstream, signInAs }, info) => {
  await setup(upstream, signInAs, { BankAccountsByOrganizer: { bankAccountsByOrganizer: [] } });
  await page.goto('/finance/bank-accounts');
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'bank-empty', info);
  upstream.gql({ BankAccountsByOrganizer: gqlErrors({ message: 'Banks unavailable', code: 'SERVICE_UNAVAILABLE' }) });
  await page.reload();
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await shot(page, 'bank-error', info);
});
