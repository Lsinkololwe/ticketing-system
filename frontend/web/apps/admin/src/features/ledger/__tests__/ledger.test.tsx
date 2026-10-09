import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction, menuItem } from '@/test/menu';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  settle: { settlements: [] as any[], pageInfo: { totalCount: 0 }, loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  comm: { records: [] as any[], totals: null as any, pageInfo: { totalCount: 0 }, loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  transfer: vi.fn().mockResolvedValue({ executed: true, requiresSecondApprover: false }),
  coa: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  tb: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  journal: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn(), pageInfo: { totalCount: 0 } },
  plat: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  runs: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn(), pageInfo: { totalCount: 0 } },
  actions: {
    createAccount: vi.fn(), updateAccount: vi.fn(), deactivateAccount: vi.fn().mockResolvedValue(undefined), seedChart: vi.fn().mockResolvedValue(true),
    createEntry: vi.fn().mockResolvedValue({ entryNumber: 'JE-1' }), postEntry: vi.fn().mockResolvedValue(undefined), reverseEntry: vi.fn().mockResolvedValue(undefined),
    startRun: vi.fn(), resolveItem: vi.fn(), completeRun: vi.fn(), failRun: vi.fn(), recordSettlement: vi.fn(), busy: false,
  },
}));

vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({
  useGatewaySettlements: () => h.settle,
  useCommissionRecords: () => h.comm,
  useTransferBetweenPlatformAccounts: () => ({ transfer: h.transfer, loading: false }),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/ledger', async (orig) => ({
  ...(await orig<object>()),
  useChartOfAccounts: () => h.coa,
  useTrialBalance: () => h.tb,
  useJournalEntries: () => h.journal,
  usePlatformAccounts: () => h.plat,
  useReconciliationRuns: () => h.runs,
  useChartOfAccountsActions: () => h.actions,
  useJournalActions: () => h.actions,
  useReconciliationActions: () => h.actions,
}));

import { CoaTab } from '../CoaTab';
import { JournalTab, journalSchema } from '../JournalTab';
import { TrialBalanceTab } from '../TrialBalanceTab';
import { PlatformTab } from '../PlatformTab';
import { CommissionTab } from '../CommissionTab';
import { ReconTab, isOpenItem } from '../ReconTab';

const account = (o: object = {}) => ({ id: 'a1', accountCode: '1000', accountName: 'Bank', accountType: 'ASSET', subType: 'BANK_ACCOUNT', parentAccountCode: null, currency: 'ZMW', isActive: true, description: null, normalBalance: 'DEBIT', ...o });
const entry = (o: object = {}) => ({ id: 'j1', entryNumber: 'JE-2026-000001', correlationId: 'c', entryDate: '2026-10-01T00:00:00Z', description: 'Settlement', type: 'STANDARD', status: 'DRAFT', lines: [{ accountCode: '1000', accountName: 'Bank', debit: 100, credit: 0, description: null }], totalDebits: 100, totalCredits: 100, isBalanced: true, reversalEntryId: null, reversedByEntryId: null, ...o });

beforeEach(() => {
  Object.assign(h.coa, { items: [], loading: false, error: undefined });
  Object.assign(h.tb, { items: [], loading: false, error: undefined });
  Object.assign(h.journal, { items: [], loading: false, error: undefined });
  Object.assign(h.plat, { items: [], loading: false, error: undefined });
  Object.assign(h.runs, { items: [], loading: false, error: undefined });
  Object.values(h.actions).forEach((f) => typeof f === 'function' && 'mockClear' in f && (f as any).mockClear());
});

describe('Chart of accounts', () => {
  it('lists accounts with action buttons, not clickable rows', () => {
    h.coa.items = [account(), account({ id: 'a2', accountCode: '2000', accountName: 'Escrow', accountType: 'LIABILITY', isActive: false, normalBalance: 'CREDIT' })];
    h.tb.items = [{ accountCode: '1000', accountName: 'Bank', accountType: 'ASSET', debitBalance: 500, creditBalance: 100, netBalance: 400 }];
    renderConsole(<CoaTab />);
    expect(screen.getByRole('table', { name: 'Chart of accounts' })).toBeInTheDocument();
    expect(screen.getByText('K 400')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Edit account/ })).toHaveLength(2);
    expect(menuItem('More actions for account 1000', 'Deactivate')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Seed standard chart/ })).toBeInTheDocument();
  });
  it('shows loading, empty and error states', () => {
    h.coa.loading = true;
    const { unmount } = renderConsole(<CoaTab />);
    expect(document.querySelectorAll('[aria-hidden="true"] .m3-skeleton, tr[aria-hidden="true"]').length).toBeGreaterThan(0);
    unmount();
    h.coa.loading = false;
    const r2 = renderConsole(<CoaTab />);
    expect(screen.getByText('No accounts yet.')).toBeInTheDocument();
    r2.unmount();
    h.coa.error = new Error('boom');
    renderConsole(<CoaTab />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
  it('validates the 4-digit code and creates an account', async () => {
    renderConsole(<CoaTab />);
    fireEvent.click(screen.getByRole('button', { name: /New account/ }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Account code'), { target: { value: '12' } });
    fireEvent.change(within(dlg).getByLabelText('Name'), { target: { value: 'Cash' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create account' }));
    expect((await within(dlg).findAllByText('Use a 4-digit code')).length).toBeGreaterThan(0);
    fireEvent.change(within(dlg).getByLabelText('Account code'), { target: { value: '1200' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(h.actions.createAccount).toHaveBeenCalledWith(expect.objectContaining({ accountCode: '1200', accountName: 'Cash' })));
  });
  it('deactivates after confirmation', async () => {
    h.coa.items = [account()];
    renderConsole(<CoaTab />);
    menuAction('More actions for account 1000', 'Deactivate');
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Deactivate' }));
    await waitFor(() => expect(h.actions.deactivateAccount).toHaveBeenCalledWith('a1'));
  });
});

describe('Journal entries', () => {
  it('shows status-dependent actions', () => {
    h.journal.items = [entry(), entry({ id: 'j2', entryNumber: 'JE-2', status: 'POSTED' }), entry({ id: 'j3', entryNumber: 'JE-3', status: 'POSTED', type: 'REVERSAL' })];
    renderConsole(<JournalTab />);
    expect(screen.getAllByRole('button', { name: /^View entry/ })).toHaveLength(3);
    expect(menuItem('More actions for entry JE-2026-000001', 'Post')).toBeInTheDocument();
    expect(menuItem('More actions for entry JE-2', 'Reverse…')).toBeInTheDocument();
  });
  it('posts a draft after confirmation', async () => {
    h.journal.items = [entry()];
    renderConsole(<JournalTab />);
    menuAction('More actions for entry JE-2026-000001', 'Post');
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Post entry' }));
    await waitFor(() => expect(h.actions.postEntry).toHaveBeenCalledWith('j1'));
  });
  it('requires a reason to reverse', async () => {
    h.journal.items = [entry({ status: 'POSTED' })];
    renderConsole(<JournalTab />);
    menuAction('More actions for entry JE-2026-000001', 'Reverse…');
    const dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reverse entry' }));
    expect(await within(dlg).findByText(/at least 5 characters/)).toBeInTheDocument();
    expect(h.actions.reverseEntry).not.toHaveBeenCalled();
  });
  it('shows the empty state', () => {
    renderConsole(<JournalTab />);
    expect(screen.getByText('No journal entries yet.')).toBeInTheDocument();
  });
  it('journal schema enforces balance as a refinement', () => {
    const base = { description: 'x', date: '2026-10-01', type: 'STANDARD' as const };
    const bad = journalSchema.safeParse({ ...base, lines: [{ accountCode: '1000', debit: '100', credit: '' }, { accountCode: '2000', debit: '', credit: '90' }] });
    expect(bad.success).toBe(false);
    expect(JSON.stringify((bad as any).error.issues)).toContain('out of balance by K 10');
    const ok = journalSchema.safeParse({ ...base, lines: [{ accountCode: '1000', debit: '100', credit: '' }, { accountCode: '2000', debit: '', credit: '100' }] });
    expect(ok.success).toBe(true);
  });
  it('new entry dialog reports live totals', async () => {
    h.coa.items = [account(), account({ id: 'a2', accountCode: '2000', accountName: 'Escrow' })];
    renderConsole(<JournalTab />);
    fireEvent.click(screen.getAllByRole('button', { name: /New entry/ })[0]);
    const dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Debit (K), line 1'), { target: { value: '50' } });
    expect(within(dlg).getByTestId('journal-totals')).toHaveTextContent('Out of balance by K 50');
    fireEvent.change(within(dlg).getByLabelText('Credit (K), line 2'), { target: { value: '50' } });
    expect(within(dlg).getByTestId('journal-totals')).toHaveTextContent('Balanced');
  });
});

describe('Trial balance', () => {
  it('computes totals and balance pill', () => {
    h.tb.items = [
      { accountCode: '1000', accountName: 'Bank', accountType: 'ASSET', debitBalance: 300, creditBalance: 0, netBalance: 300 },
      { accountCode: '2000', accountName: 'Escrow', accountType: 'LIABILITY', debitBalance: 0, creditBalance: 300, netBalance: -300 },
    ];
    renderConsole(<TrialBalanceTab />);
    expect(screen.getByText('Debits equal credits')).toBeInTheDocument();
    expect(screen.getByTestId('tb-totals').closest('tr')).toHaveTextContent('K 300');
  });
  it('shows the empty state', () => {
    renderConsole(<TrialBalanceTab />);
    expect(screen.getByText('Nothing posted yet.')).toBeInTheDocument();
  });
});

describe('Platform accounts and commission', () => {
  it('renders balances and records a transfer with an idempotency key', async () => {
    h.plat.items = [{ id: 'p1', accountType: 'OPERATING', name: 'Main', balance: 1250, currency: 'ZMW', lastUpdatedAt: null }];
    renderConsole(<PlatformTab />);
    expect(screen.getByText('K 1,250')).toBeInTheDocument();
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Record transfer' }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Record transfer' }));
    expect((await within(dlg).findAllByText(/Enter an amount/)).length).toBeGreaterThan(0);
    expect(h.transfer).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Amount (K)'), { target: { value: '250.50' } });
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Top up the reserve account' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Record transfer' }));
    await waitFor(() => expect(h.transfer).toHaveBeenCalledWith(expect.objectContaining({ fromAccount: 'OPERATING', toAccount: 'RESERVE', amount: '250.50', idempotencyKey: expect.any(String) })));
  });
  it('transfer needs a finance role', () => {
    renderConsole(<PlatformTab />, { roles: [] });
    expect(screen.getByRole('button', { name: 'Record transfer' })).toBeDisabled();
  });
  it('commission shows totals and records from the backend', () => {
    h.comm.totals = { earned: '120.00', pending: '30.00', cancelled: '0', clawedBack: '5.00' };
    h.comm.records = [{ id: 'c1', ticketId: 'T-1', eventId: 'e1', ticketPrice: '100', rate: '5', amount: '5', status: 'EARNED', earnedAt: '2026-10-01T00:00:00Z', pendingAt: null, createdAt: null, refundReason: null }];
    renderConsole(<CommissionTab />);
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByText('K 120')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Commission records' })).getByText('T-1')).toBeInTheDocument();
    h.comm.records = []; h.comm.totals = null;
  });
  it('commission empty state', () => {
    renderConsole(<CommissionTab />);
    expect(screen.getByText('No commission records yet.')).toBeInTheDocument();
  });
});

describe('Reconciliation', () => {
  const run = (o: object = {}) => ({ id: 'run-00000001', reconciliationDate: '2026-10-01T00:00:00Z', type: 'GATEWAY', status: 'REQUIRES_REVIEW', dataSource: null, expectedTotal: 0, actualTotal: 0, variance: 0, matchedCount: 1, unmatchedCount: 1, runBy: 'Ann', startedAt: '2026-10-02T08:00:00Z', completedAt: null, notes: null, items: [{ externalId: 'PAY-1', internalId: null, externalAmount: 80, internalAmount: 100, status: 'AMOUNT_MISMATCH', resolution: null, resolvedBy: null, resolvedAt: null }], ...o });
  it('opens a run, blocks completion while differences are open', async () => {
    h.runs.items = [run()];
    renderConsole(<ReconTab />, { roles: ['FINANCE'] });
    fireEvent.click(screen.getByRole('button', { name: 'Open' }));
    const sheet = await screen.findByRole('dialog');
    expect(within(sheet).getByRole('button', { name: 'Resolve' })).toBeInTheDocument();
    fireEvent.click(within(sheet).getByRole('button', { name: 'Complete run' }));
    expect(await screen.findByText('Differences still open')).toBeInTheDocument();
    expect(h.actions.completeRun).not.toHaveBeenCalled();
  });
  it('locks Start run for roles without the permission', () => {
    renderConsole(<ReconTab />, { roles: [] });
    expect(screen.queryByRole('button', { name: /Start run/ })).toBeNull();
    expect(screen.getByText(/run reconciliation/)).toBeInTheDocument();
  });
  it('settlement form validates the reference format', async () => {
    renderConsole(<ReconTab />, { roles: ['FINANCE'] });
    fireEvent.click(screen.getByRole('button', { name: /Record settlement/ }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Record settlement' }));
    expect((await within(dlg).findAllByText('Use the format PWP-SET-20261002')).length).toBeGreaterThan(0);
    expect(h.actions.recordSettlement).not.toHaveBeenCalled();
    expect(screen.queryByText(/Not available yet/)).toBeNull();
  });
  it('lists gateway settlements', () => {
    h.settle.settlements = [{ settlementId: 'PWP-SET-20261001', settlementDate: '2026-10-01T00:00:00Z', grossAmount: '1000', feeAmount: '30', netAmount: '970', bankReference: 'BK1', entryNumber: 'JE-1', journalEntryId: 'j1' }];
    renderConsole(<ReconTab />, { roles: ['FINANCE'] });
    expect(within(screen.getByRole('table', { name: 'Gateway settlements' })).getByText('PWP-SET-20261001')).toBeInTheDocument();
    h.settle.settlements = [];
  });
  it('isOpenItem ignores matched and resolved items', () => {
    expect(isOpenItem({ status: 'MATCHED' } as any)).toBe(false);
    expect(isOpenItem({ status: 'AMOUNT_MISMATCH', resolution: 'ok' } as any)).toBe(false);
    expect(isOpenItem({ status: 'AMOUNT_MISMATCH', resolution: null, resolvedAt: null } as any)).toBe(true);
  });
});
