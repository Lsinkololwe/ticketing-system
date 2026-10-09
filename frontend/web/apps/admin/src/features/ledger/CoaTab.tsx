'use client';

import { RowActions } from '@/components/console/RowActions';
import { useMemo, useState } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import {
  Button,
  ConfirmDialog,
  FormCell,
  FormGrid,
  Select,
  TextField,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import {
  toNumber,
  useChartOfAccounts,
  useChartOfAccountsActions,
  useTrialBalance,
  type AccountType,
  type LedgerAccount,
} from '@pml.tickets/shared/api/admin/modules/ledger';
import { humanize, money } from '@/lib/format';
import { FormDialog } from './FormDialog';
import { ListCard } from './ListCard';
import { ACCOUNT_TYPE_LABELS, enumSchema, enumValues } from '@/lib/enumLabels';

import type { AccountSubType } from '@pml.tickets/shared/types/graphql';

export const ACCOUNT_TYPES: AccountType[] = enumValues(ACCOUNT_TYPE_LABELS);
/** The sub types an account may be created under (a policy subset of the schema's `AccountSubType`). */
export const ACCOUNT_SUBTYPES: AccountSubType[] = [
  'BANK_ACCOUNT', 'GATEWAY_RECEIVABLE', 'COMMISSION_RECEIVABLE', 'CHARGEBACK_RECEIVABLE', 'ESCROW_PAYABLE',
  'PAYOUTS_PAYABLE', 'REFUNDS_PAYABLE', 'TAX_PAYABLE', 'FEES_PAYABLE', 'DEFERRED_REVENUE', 'RETAINED_EARNINGS',
  'RESERVE', 'COMMISSION_REVENUE', 'FEE_REVENUE', 'GATEWAY_FEES', 'CHARGEBACK_LOSS', 'CHARGEBACK_FEES', 'BAD_DEBT',
];

const accountSchema = z.object({
  accountCode: z.string().regex(/^\d{4}$/, 'Use a 4-digit code'),
  accountName: z.string().trim().min(1, 'Enter a name'),
  accountType: enumSchema(ACCOUNT_TYPE_LABELS),
  subType: z.string(),
  parentAccountCode: z.string(),
  description: z.string(),
});

function AccountDialogBody({ account, accounts, onClose }: { account: LedgerAccount | 'new'; accounts: LedgerAccount[]; onClose: () => void }) {
  const { createAccount, updateAccount } = useChartOfAccountsActions();
  const snackbar = useSnackbar();
  const editing = account !== 'new' ? account : null;
  const form = useZodForm(accountSchema, {
    defaultValues: {
      accountCode: editing?.accountCode ?? '',
      accountName: editing?.accountName ?? '',
      accountType: editing?.accountType ?? 'ASSET',
      subType: editing?.subType ?? '',
      parentAccountCode: editing?.parentAccountCode ?? '',
      description: editing?.description ?? '',
    },
  });
  const { register, formState: { errors }, watch } = form;
  const code = watch('accountCode');
  return (
    <FormDialog
      title={editing ? `Edit account ${editing.accountCode}` : 'New account'}
      form={form}
      wide
      onClose={onClose}
      submitLabel={editing ? 'Save changes' : 'Create account'}
      onSubmit={async (v) => {
        if (!editing && accounts.some((a) => a.accountCode === v.accountCode)) {
          form.setError('accountCode', { message: 'That code is already in use' });
          return;
        }
        const input = {
          accountCode: v.accountCode,
          accountName: v.accountName,
          accountType: v.accountType,
          subType: v.subType || null,
          parentAccountCode: v.parentAccountCode || null,
          description: v.description.trim() || null,
        };
        if (editing) await updateAccount(editing.id, input);
        else await createAccount(input);
        snackbar.show(editing ? 'Account updated' : 'Account created');
        onClose();
      }}
    >
      <FormGrid>
        <FormCell span={6}>
          <TextField label="Account code" density="form" inputMode="numeric" disabled={!!editing} {...register('accountCode')} errorText={errors.accountCode?.message} />
        </FormCell>
        <FormCell span={6}>
          <TextField label="Name" density="form" {...register('accountName')} errorText={errors.accountName?.message} />
        </FormCell>
        <FormCell span={6}>
          <Select label="Type" density="form" {...register('accountType')}>
            {ACCOUNT_TYPES.map((t) => (<option key={t} value={t}>{humanize(t)}</option>))}
          </Select>
        </FormCell>
        <FormCell span={6}>
          <Select label="Subtype" density="form" {...register('subType')}>
            <option value="">None</option>
            {ACCOUNT_SUBTYPES.map((t) => (<option key={t} value={t}>{humanize(t)}</option>))}
          </Select>
        </FormCell>
        <FormCell span={6}>
          <Select label="Parent account" density="form" {...register('parentAccountCode')}>
            <option value="">None</option>
            {accounts.filter((a) => a.accountCode !== code).map((a) => (<option key={a.id} value={a.accountCode}>{a.accountCode} {a.accountName}</option>))}
          </Select>
        </FormCell>
        <FormCell span={6}>
          <TextField label="Description" density="form" {...register('description')} />
        </FormCell>
      </FormGrid>
    </FormDialog>
  );
}

export function CoaTab() {
  const { items, loading, error, refetch } = useChartOfAccounts();
  const tb = useTrialBalance(null);
  const { deactivateAccount, seedChart, busy } = useChartOfAccountsActions();
  const snackbar = useSnackbar();
  const [dialog, setDialog] = useState<LedgerAccount | 'new' | null>(null);
  const [toDeactivate, setToDeactivate] = useState<LedgerAccount | null>(null);
  const [seeding, setSeeding] = useState(false);

  // Balance = what the posted entries say, signed by the account's normal balance.
  const balances = useMemo(() => {
    const m = new Map<string, number>();
    tb.items.forEach((b) => m.set(b.accountCode, toNumber(b.debitBalance) - toNumber(b.creditBalance)));
    return m;
  }, [tb.items]);
  const rows = useMemo(() => [...items].sort((a, b) => a.accountCode.localeCompare(b.accountCode)), [items]);

  const columns: Array<DataColumn<LedgerAccount>> = [
    { id: 'code', header: 'Code', cell: (a) => <span className="m3-mono">{a.accountCode}</span> },
    { id: 'name', header: 'Account', rowHeader: true, cell: (a) => <b>{a.accountName}</b> },
    { id: 'type', header: 'Type', cell: (a) => humanize(a.accountType) },
    { id: 'sub', header: 'Subtype', cell: (a) => humanize(a.subType) },
    { id: 'parent', header: 'Parent', cell: (a) => (a.parentAccountCode ? <span className="m3-mono">{a.parentAccountCode}</span> : '—') },
    { id: 'normal', header: 'Normal', cell: (a) => humanize(a.normalBalance) },
    {
      id: 'balance',
      header: 'Balance',
      align: 'end',
      cell: (a) => {
        const net = balances.get(a.accountCode);
        return <span className="m3-mono">{net === undefined ? '—' : money(a.normalBalance === 'DEBIT' ? net : -net)}</span>;
      },
    },
    { id: 'active', header: 'Status', cell: (a) => <span className="m3-pill" data-tone={a.isActive ? 'success' : undefined}>{a.isActive ? 'Active' : 'Inactive'}</span> },
  ];

  return (
    <>
      <ListCard
        title="Chart of accounts"
        subtitle="Four-digit codes. Balances come from posted journal entries."
        caption="Chart of accounts"
        toolbar={
          <>
            <Button variant="filled" size="sm" icon="add" onClick={() => setDialog('new')}>New account</Button>
            <Button variant="tonal" size="sm" onClick={() => setSeeding(true)}>Seed standard chart</Button>
          </>
        }
        rows={rows}
        columns={columns}
        getRowId={(a) => a.id}
        searchLabel="Search accounts"
        searchText={(a) => `${a.accountCode} ${a.accountName} ${a.subType ?? ''}`}
        filters={[{ id: 'type', label: 'Type', options: ACCOUNT_TYPES.map((t) => ({ value: t, label: humanize(t) })), match: (a, v) => a.accountType === v }]}
        loading={loading}
        error={error}
        onRetry={refetch}
        empty={{ title: 'No accounts yet.', description: 'Seed the standard chart or add an account.' }}
        rowActions={(a) => (
          <RowActions
            name={`account ${a.accountCode}`}
            primary={{ label: 'Edit', onSelect: () => setDialog(a) }}
            items={a.isActive ? [{ id: 'deactivate', label: 'Deactivate', danger: true, onSelect: () => setToDeactivate(a) }] : []}
          />
        )}
      />
      {dialog ? <AccountDialogBody account={dialog} accounts={items} onClose={() => setDialog(null)} /> : null}
      <ConfirmDialog
        open={toDeactivate !== null}
        onClose={() => setToDeactivate(null)}
        title={`Deactivate ${toDeactivate?.accountCode ?? ''}?`}
        description="The account stays in history but can no longer be used in new journal entries. Accounts used by journal entries cannot be deleted, only deactivated."
        confirmLabel="Deactivate"
        danger
        loading={busy}
        onConfirm={async () => {
          if (!toDeactivate) return;
          try {
            await deactivateAccount(toDeactivate.id);
            snackbar.show(`Account ${toDeactivate.accountCode} deactivated`);
          } catch (e) {
            snackbar.show((e as Error).message || 'Could not deactivate the account');
          }
          setToDeactivate(null);
        }}
      />
      <ConfirmDialog
        open={seeding}
        onClose={() => setSeeding(false)}
        title="Seed the standard chart?"
        description="Adds any standard accounts that are missing. Existing accounts are untouched."
        confirmLabel="Add accounts"
        loading={busy}
        onConfirm={async () => {
          try {
            await seedChart();
            snackbar.show('Standard chart seeded');
          } catch (e) {
            snackbar.show((e as Error).message || 'Could not seed the chart');
          }
          setSeeding(false);
        }}
      />
    </>
  );
}
