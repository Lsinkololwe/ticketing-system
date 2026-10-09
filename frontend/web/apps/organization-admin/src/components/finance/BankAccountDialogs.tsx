'use client';

import { Dialog, FormCell, FormGrid, SegmentedButton } from '@pml.tickets/shared/components/m3';
import { useEffect, useMemo } from 'react';
import { useFormContext } from 'react-hook-form';
import { Form, FormActions, MoneyRHF, SelectRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { maskAccount } from '@/lib/finance/payouts';
import { bankAccountSchema, bankVerifySchema, walletSchemaFor, type BankFormValues, type WalletFormValues } from './schemas';

export type { BankFormValues } from './schemas';

/** Selects the platform's first listed value for a field once its list has loaded and the field is still empty. */
function FirstChoice({ name, values }: { name: 'currency' | 'network'; values: readonly string[] }) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { getValues, setValue } = useFormContext<any>();
  const first = values[0];
  useEffect(() => {
    if (first && !getValues(name)) setValue(name, first, { shouldDirty: false });
  }, [first, name, getValues, setValue]);
  return null;
}

export interface BankFormInitial extends Partial<Omit<BankFormValues, 'currency'>> {
  id?: string;
  currency?: string;
}

interface DialogProps {
  initial?: BankFormInitial;
  /** The platform's bank list (catalog reference data). Empty while it cannot be read. */
  banks: string[];
  /** Resolve on success, reject (GraphQL error) to map it onto the form. */
  onSave: (values: BankFormValues) => Promise<unknown> | void;
  /** Adding only: switch the dialog to the mobile wallet form. */
  onSwitchToWallet?: () => void;
  onClose: () => void;
}

/** Add or edit a bank account. The account number is fixed once created. */
export function BankAccountDialog({ initial, banks, onSave, onSwitchToWallet, onClose }: DialogProps) {
  const isEdit = Boolean(initial?.id);
  const currencies = useReferenceOptions('CURRENCY');
  const schema = useMemo(() => bankAccountSchema(isEdit, currencies.options.map((o) => o.value)), [isEdit, currencies.options]);
  const form = useZodForm(schema, {
    defaultValues: {
      holder: initial?.holder ?? '',
      bankName: initial?.bankName ?? banks[0] ?? '',
      branchCode: initial?.branchCode ?? '',
      number: initial?.number ?? '',
      swift: initial?.swift ?? '',
      // The platform's first currency is chosen once its list arrives (see FirstChoice).
      currency: initial?.currency ?? '',
    },
  });

  return (
    <Dialog open wide onClose={onClose} title={isEdit ? 'Edit account' : 'Add account'}>
      <Form
        form={form}
        aria-label={isEdit ? 'Edit bank account' : 'Add bank account'}
        fieldMap={{ accountHolderName: 'holder', accountNumber: 'number', swiftCode: 'swift', branchCode: 'branchCode' }}
        onSubmit={async (v) => {
          await onSave(v);
          onClose();
        }}
      >
        <SegmentedButton
          label="Account type"
          value="BANK_ACCOUNT"
          onChange={(v) => v === 'MOBILE_WALLET' && onSwitchToWallet?.()}
          options={[
            { value: 'BANK_ACCOUNT', label: 'Bank account' },
            { value: 'MOBILE_WALLET', label: 'Mobile wallet', disabled: isEdit || !onSwitchToWallet },
          ]}
        />
        <FirstChoice name="currency" values={currencies.options.map((o) => o.value)} />
        <FormGrid>
          <FormCell span={12}>
            <TextFieldRHF name="holder" label="Account holder" />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="bankName"
              label="Bank"
              options={(initial?.bankName && !banks.includes(initial.bankName) ? [initial.bankName, ...banks] : banks).map((b) => ({ value: b, label: b }))}
              helperText={banks.length === 0 ? 'Not available yet: the bank list could not be loaded' : undefined}
            />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="branchCode" label="Branch code" />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF
              name="number"
              label="Account number"
              readOnly={isEdit}
              inputMode="numeric"
              helperText={isEdit ? 'Account numbers cannot be edited. Add a new account instead.' : '10 to 16 digits'}
            />
          </FormCell>
          <FormCell span={3}>
            <TextFieldRHF name="swift" label="SWIFT code" helperText="Optional, 8 or 11 characters" />
          </FormCell>
          <FormCell span={3}>
            <SelectRHF
              name="currency"
              label="Currency"
              disabled={currencies.loading}
              options={currencies.options.map((o) => ({ value: o.value, label: o.label }))}
              helperText={!currencies.loading && currencies.empty ? 'Not available yet: the currency list could not be loaded' : undefined}
            />
          </FormCell>
        </FormGrid>
        <FormActions submitLabel={isEdit ? 'Save' : 'Add account'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}

interface ConfirmProps {
  accountNumber: string;
  /**
   * Receives the test-deposit amount in ngwee. Reject to map a server error; resolve with
   * `{ amountError }` when the amount is simply wrong (the dialog stays open).
   */
  onConfirm: (amountMinor: number) => Promise<{ amountError?: string } | void> | void;
  onClose: () => void;
}

/** Enter the exact test-deposit amount to verify the account. */
export function BankVerifyDialog({ accountNumber, onConfirm, onClose }: ConfirmProps) {
  const form = useZodForm(bankVerifySchema, { defaultValues: { amount: undefined } });
  return (
    <Dialog open onClose={onClose} title="Confirm the test deposit">
      <Form
        form={form}
        aria-label="Confirm the test deposit"
        onSubmit={async (v, f) => {
          const res = await onConfirm(v.amount);
          if (res && res.amountError) {
            f.setError('amount', { type: 'server', message: res.amountError });
            return;
          }
          onClose();
        }}
      >
        <p>
          Enter the exact amount we sent to <b>{maskAccount(accountNumber)}</b>.
        </p>
        <MoneyRHF name="amount" label="Deposit amount" helperText="Three wrong tries rejects the account." />
        <FormActions submitLabel="Confirm" onCancel={onClose} />
      </Form>
    </Dialog>
  );
}

interface WalletDialogProps {
  initial?: { holder?: string; network?: string };
  /** Resolve on success, reject (GraphQL error) to map it onto the form. */
  onSave: (values: WalletFormValues) => Promise<unknown> | void;
  /** Adding only: switch back to the bank account form. */
  onSwitchToBank?: () => void;
  onClose: () => void;
}

/** Add or replace the mobile wallet payouts go to. The number is validated server-side as E.164. */
export function WalletDialog({ initial, onSave, onSwitchToBank, onClose }: WalletDialogProps) {
  const networks = useReferenceOptions<{ msisdnPrefixes?: string[] }>('MOBILE_MONEY_OPERATOR');
  const schema = useMemo(() => walletSchemaFor(networks.options.map((o) => o.value)), [networks.options]);
  const form = useZodForm(schema, { defaultValues: { holder: initial?.holder ?? '', network: initial?.network ?? '', phone: '' } });
  // "+260 76, 77, 95, 96, 97": the number ranges the listed networks own, read from their rows.
  const ranges = useMemo(() => {
    const prefixes = networks.options.flatMap((o) => o.metadata.msisdnPrefixes ?? []).map((p) => p.replace(/^260/, ''));
    return prefixes.length ? `+260 ${[...new Set(prefixes)].sort().join(', ')}` : undefined;
  }, [networks.options]);
  return (
    <Dialog open wide onClose={onClose} title={initial ? 'Replace wallet' : 'Add account'}>
      <Form
        form={form}
        aria-label="Add mobile wallet"
        fieldMap={{ accountHolderName: 'holder', provider: 'network', phoneNumber: 'phone' }}
        onSubmit={async (v) => {
          await onSave(v);
          onClose();
        }}
      >
        <FirstChoice name="network" values={networks.options.map((o) => o.value)} />
        <SegmentedButton
          label="Account type"
          value="MOBILE_WALLET"
          onChange={(v) => v === 'BANK_ACCOUNT' && onSwitchToBank?.()}
          options={[
            { value: 'BANK_ACCOUNT', label: 'Bank account', disabled: !onSwitchToBank },
            { value: 'MOBILE_WALLET', label: 'Mobile wallet' },
          ]}
        />
        <FormGrid>
          <FormCell span={12}>
            <TextFieldRHF name="holder" label="Wallet holder" />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="network"
              label="Network"
              disabled={networks.loading}
              options={networks.options.map((o) => ({ value: o.value, label: o.label }))}
              helperText={!networks.loading && networks.empty ? 'Not available yet: the network list could not be loaded' : undefined}
            />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="phone" label="Mobile number" placeholder="+260 97 123 4567" inputMode="tel" helperText={ranges} />
          </FormCell>
        </FormGrid>
        <FormActions submitLabel={initial ? 'Save' : 'Add account'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
