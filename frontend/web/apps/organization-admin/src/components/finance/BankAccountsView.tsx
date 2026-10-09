'use client';

import { useMemo, useState, type ReactNode } from 'react';
import {
  Banner,
  Button,
  Card,
  CardHeader,
  ConfirmDialog,
  DataTable,
  IconButton,
  PageHeader,
  TextField,
} from '@pml.tickets/shared/components/m3';
import type { BankAccountVM } from '@pml.tickets/shared/api/organization-admin/modules/finance';
import type { WalletRow } from '@/lib/api/finance';
import { formatDateTime } from '@/lib/bookings/format';
import { maskAccount } from '@/lib/finance/payouts';
import { Status } from '@/components/console/Status';
import { DataState } from '@/components/console/DataState';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { usePlatformRules } from '@/lib/api/platform';
import { usePaged } from './usePaged';

export interface BankAccountsViewProps {
  accounts: BankAccountVM[];
  loading: boolean;
  error?: Error | null;
  onRetry?: () => void;
  /** Owner-only: add, edit, delete, verify. */
  canManage: boolean;
  onStartVerification: (id: string) => Promise<void> | void;
  onMakeDefault: (id: string) => Promise<void> | void;
  onDelete: (id: string) => Promise<void> | void;
  /** Renders the add/edit dialog. `account` is null when adding. */
  renderForm: (account: BankAccountVM | null, close: () => void, switchToWallet?: () => void) => ReactNode;
  /** Renders the confirm-deposit dialog. */
  renderVerify: (account: BankAccountVM, close: () => void) => ReactNode;
  /** The mobile wallet payouts can go to (null = none set). */
  wallet?: WalletRow | null;
  /** Renders the add/replace wallet dialog. */
  renderWalletForm?: (wallet: WalletRow | null, close: () => void, switchToBank?: () => void) => ReactNode;
}

/** One line in the accounts table: a bank account or the mobile wallet. */
interface AccountRow {
  id: string;
  kind: 'bank' | 'wallet';
  holder: string;
  line: string;
  currency: string;
  isDefault: boolean;
  isVerified: boolean;
  status: string;
  note: string | null;
  bank: BankAccountVM | null;
}

const WALLET_STATUS: Record<string, string> = { PENDING: 'PENDING_VERIFICATION', VERIFIED: 'VERIFIED', REJECTED: 'REJECTED', SUSPENDED: 'SUSPENDED' };

function walletNote(w: WalletRow): string | null {
  if (w.suspended) return w.suspendedReason ?? 'Suspended';
  if (w.status === 'REJECTED') return w.rejectionReason ?? 'Rejected';
  if (w.status === 'PENDING') {
    return w.testDepositSentAt
      ? `Test deposit sent ${formatDateTime(w.testDepositSentAt)}. ${w.verificationAttemptsLeft} ${w.verificationAttemptsLeft === 1 ? 'try' : 'tries'} left.`
      : 'Waiting for the test deposit.';
  }
  return null;
}

export function BankAccountsView(p: BankAccountsViewProps) {
  const [q, setQ] = useState('');
  const [form, setForm] = useState<{ account: BankAccountVM | null } | null>(null);
  const [verify, setVerify] = useState<BankAccountVM | null>(null);
  const [remove, setRemove] = useState<BankAccountVM | null>(null);
  const [walletForm, setWalletForm] = useState<'add' | 'replace' | null>(null);
  const networks = useReferenceOptions('MOBILE_MONEY_OPERATOR');
  const currencies = useReferenceOptions('CURRENCY');
  // Wallet payouts settle in the platform's currency (platformRules.currency).
  const platformCurrency = usePlatformRules().rules?.currency ?? '';
  const rows = useMemo<AccountRow[]>(() => {
    const banks = p.accounts.map<AccountRow>((a) => ({
      id: a.id,
      kind: 'bank',
      holder: a.accountHolderName,
      line: `${a.bankName} · ${maskAccount(a.accountNumber)}`,
      currency: a.currency,
      isDefault: a.isDefault,
      isVerified: a.isVerified,
      status: a.status,
      note: null,
      bank: a,
    }));
    const w = p.wallet;
    const wallet: AccountRow[] = w
      ? [
          {
            id: 'wallet',
            kind: 'wallet',
            holder: w.accountHolderName ?? '—',
            line: `${networks.labelOf(w.provider) || 'Mobile'} wallet · ${w.maskedPhoneNumber ?? '—'}`,
            currency: platformCurrency,
            isDefault: false,
            isVerified: w.verified,
            status: w.suspended ? 'SUSPENDED' : WALLET_STATUS[w.status] ?? w.status,
            note: walletNote(w),
            bank: null,
          },
        ]
      : [];
    return [...banks, ...wallet];
  }, [p.accounts, p.wallet, networks, platformCurrency]);
  const list = useMemo(() => rows.filter((a) => !q || `${a.holder} ${a.line}`.toLowerCase().includes(q.toLowerCase())), [rows, q]);
  const paged = usePaged(list, 5);

  return (
    <div data-testid="bank-accounts-page">
      <PageHeader title="Bank accounts" subtitle="Where your payouts are sent." />
      {!p.canManage ? <Banner tone="info">Only the owner can add or change bank accounts.</Banner> : null}
      <DataState loading={p.loading && rows.length === 0} error={p.error} onRetry={p.onRetry}>
        <Card>
          <CardHeader
            title="Bank accounts and wallets"
            subtitle="Payouts go to a verified account. Account numbers are stored encrypted and shown masked."
            actions={p.canManage ? <Button variant="tonal" icon="add" onClick={() => setForm({ account: null })}>Add account</Button> : null}
          />
          <div className="m3-toolbar">
            <TextField density="compact" label="Search accounts" placeholder="Holder or bank" value={q} onChange={(e) => { setQ(e.target.value); paged.reset(); }} />
          </div>
          <DataTable
            caption="Bank accounts"
            rows={paged.rows}
            getRowId={(a) => a.id}
            pagination={paged.pagination}
            empty={<span>No accounts yet. Add one to receive payouts.</span>}
            columns={[
              {
                id: 'account',
                header: 'Account',
                rowHeader: true,
                cell: (a) => (
                  <>
                    <b>{a.holder}</b>
                    <div className="m3-muted">
                      <span className="m3-mono">{a.line}</span>
                    </div>
                    {a.note ? <div className="m3-muted">{a.note}</div> : null}
                  </>
                ),
              },
              { id: 'type', header: 'Type', cell: (a) => (a.kind === 'wallet' ? 'Mobile wallet' : 'Bank account') },
              { id: 'cur', header: 'Currency', cell: (a) => currencies.labelOf(a.currency) },
              { id: 'def', header: 'Default', cell: (a) => (a.isDefault ? <span className="m3-pos">Default</span> : '—') },
              { id: 'status', header: 'Status', cell: (a) => <Status status={a.status} /> },
            ]}
            rowActions={(a) => {
              if (!p.canManage) return null;
              if (a.kind === 'wallet') {
                return (
                  <div className="m3-row">
                    <Button size="sm" variant="outlined" onClick={() => setWalletForm('replace')}>Replace wallet</Button>
                  </div>
                );
              }
              const b = a.bank!;
              return (
                <div className="m3-row">
                  {b.status === 'PENDING_VERIFICATION' ? (
                    <>
                      <Button size="sm" variant="tonal" onClick={() => void p.onStartVerification(b.id)}>Start test deposit</Button>
                      <Button size="sm" variant="filled" onClick={() => setVerify(b)}>Confirm deposit</Button>
                    </>
                  ) : null}
                  {b.status === 'REJECTED' ? <Button size="sm" variant="tonal" onClick={() => void p.onStartVerification(b.id)}>Restart verification</Button> : null}
                  <Button size="sm" variant="outlined" onClick={() => setForm({ account: b })}>Edit</Button>
                  {b.isVerified && !b.isDefault ? <Button size="sm" variant="tonal" onClick={() => void p.onMakeDefault(b.id)}>Make default</Button> : null}
                  <IconButton icon="delete" label="Delete account" danger onClick={() => setRemove(b)} />
                </div>
              );
            }}
          />
        </Card>
        <div className="oc-section">
          <Card>
            <CardHeader title="How verification works" />
            <ol>
              <li>Start a test deposit. We send a few ngwee to the account.</li>
              <li>Read the exact amount from your statement or wallet.</li>
              <li>Enter it to confirm. Three wrong tries rejects the account.</li>
            </ol>
          </Card>
        </div>
      </DataState>
      {form
        ? p.renderForm(
            form.account,
            () => setForm(null),
            !form.account && p.renderWalletForm && !p.wallet
              ? () => {
                  setForm(null);
                  setWalletForm('add');
                }
              : undefined
          )
        : null}
      {verify ? p.renderVerify(verify, () => setVerify(null)) : null}
      {walletForm && p.renderWalletForm
        ? p.renderWalletForm(
            walletForm === 'replace' ? p.wallet ?? null : null,
            () => setWalletForm(null),
            walletForm === 'add'
              ? () => {
                  setWalletForm(null);
                  setForm({ account: null });
                }
              : undefined
          )
        : null}
      {remove ? (
        <ConfirmDialog
          open
          danger
          title={`Delete ${remove.bankName} ${maskAccount(remove.accountNumber)}?`}
          description="The account is removed from your payout destinations."
          confirmLabel="Delete account"
          onClose={() => setRemove(null)}
          onConfirm={async () => {
            const id = remove.id;
            setRemove(null);
            await p.onDelete(id);
          }}
        />
      ) : null}
    </div>
  );
}
