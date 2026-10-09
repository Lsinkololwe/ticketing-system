import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { BankAccountsView, type BankAccountsViewProps } from './BankAccountsView';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});
vi.mock('@/lib/api/platform', () => ({ usePlatformRules: () => ({ rules: { currency: 'ZMW' } }) }));

const acc = (o: Record<string, unknown>) => ({
  id: 'b1', organizerId: 'u', accountHolderName: 'Fixture Ltd', bankName: 'Zanaco', bankCode: null, branchName: null, branchCode: null,
  accountNumber: '0123456784821', accountType: 'BANK_ACCOUNT', currency: 'ZMW', swiftCode: null, isDefault: true, isVerified: true,
  status: 'VERIFIED', createdAt: null, ...o,
});
const base = (o: Partial<BankAccountsViewProps> = {}): BankAccountsViewProps => ({
  accounts: [acc({}) as never, acc({ id: 'b2', bankName: 'Stanbic Bank', isDefault: false, isVerified: false, status: 'PENDING_VERIFICATION' }) as never],
  loading: false,
  canManage: true,
  onStartVerification: vi.fn(),
  onMakeDefault: vi.fn(),
  onDelete: vi.fn(),
  renderForm: (a) => <div role="dialog" aria-label="form">{a ? 'edit' : 'add'}</div>,
  renderVerify: (a) => <div role="dialog" aria-label="verify">{a.id}</div>,
  ...o,
});

describe('BankAccountsView', () => {
  it('lists masked accounts with statuses', () => {
    render(<BankAccountsView {...base()} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Bank accounts' })).toBeInTheDocument();
    expect(screen.getAllByText(/\*\*\*\*4821/)).toHaveLength(2);
    expect(screen.getByText('Pending verification')).toBeInTheDocument();
    expect(screen.queryByText('0123456784821')).toBeNull();
  });
  it('runs row actions', () => {
    const p = base();
    render(<BankAccountsView {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Add account' }));
    expect(screen.getByRole('dialog', { name: 'form' })).toHaveTextContent('add');
    const row = screen.getByRole('row', { name: /Stanbic/ });
    fireEvent.click(within(row).getByRole('button', { name: 'Start test deposit' }));
    expect(p.onStartVerification).toHaveBeenCalledWith('b2');
    fireEvent.click(within(row).getByRole('button', { name: 'Confirm deposit' }));
    expect(screen.getByRole('dialog', { name: 'verify' })).toHaveTextContent('b2');
    fireEvent.click(within(row).getByRole('button', { name: 'Delete account' }));
    const dlg = screen.getByRole('alertdialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Delete account' }));
    expect(p.onDelete).toHaveBeenCalledWith('b2');
  });
  it('is read-only for non owners', () => {
    render(<BankAccountsView {...base({ canManage: false })} />);
    expect(screen.getByText(/Only the owner can add/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add account' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();
  });
  it('shows empty and loading states', () => {
    const { rerender } = render(<BankAccountsView {...base({ accounts: [] })} />);
    expect(screen.getByText('No accounts yet. Add one to receive payouts.')).toBeInTheDocument();
    rerender(<BankAccountsView {...base({ accounts: [], loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
  });
  it('shows the mobile wallet with its test-deposit state and lets the owner replace it', () => {
    const wallet = {
      provider: 'MTN', maskedPhoneNumber: '+260****0112', accountHolderName: 'Fixture Holder', verified: false, status: 'PENDING', rejectionReason: null,
      suspended: false, suspendedReason: null, testDepositSentAt: '2026-10-01T10:00:00Z', verificationAttemptsLeft: 2,
    };
    const renderWalletForm = vi.fn((_w, close) => <div role="dialog" aria-label="wallet"><button onClick={close}>close</button></div>);
    render(<BankAccountsView {...base({ wallet: wallet as never, renderWalletForm })} />);
    const row = screen.getByRole('row', { name: /MTN Mobile Money wallet/ });
    expect(within(row).getByText('Mobile wallet')).toBeInTheDocument();
    expect(within(row).getByText(/2 tries left/)).toBeInTheDocument();
    fireEvent.click(within(row).getByRole('button', { name: 'Replace wallet' }));
    expect(screen.getByRole('dialog', { name: 'wallet' })).toBeInTheDocument();
  });
  it('hides wallet management from non owners', () => {
    const wallet = { provider: 'AIRTEL', maskedPhoneNumber: '+260****0099', accountHolderName: 'F', verified: true, status: 'VERIFIED', rejectionReason: null, suspended: false, suspendedReason: null, testDepositSentAt: null, verificationAttemptsLeft: 3 };
    render(<BankAccountsView {...base({ canManage: false, wallet: wallet as never })} />);
    expect(screen.queryByRole('button', { name: 'Replace wallet' })).toBeNull();
  });
});
