'use client';

import { useMutation } from '@apollo/client/react';
import { useSnackbar } from '@pml.tickets/shared/components/m3';
import {
  CREATE_BANK_ACCOUNT,
  UPDATE_BANK_ACCOUNT,
  useDeleteBankAccount,
  useMyBankAccounts,
  useSetDefaultBankAccount,
} from '@pml.tickets/shared/api/organization-admin/modules/finance';
import { minorToKwachaString } from '@pml.tickets/shared';
import type { MobileMoneyProvider } from '@pml.tickets/shared/types/graphql';
import { useOrgContext } from '@/lib/api/org-context';
import { useReferenceList } from '@/lib/api/platform';
import { useStepUp } from '@/lib/useStepUp';
import { useBankVerification, usePayoutWallet, useSetMobileMoneyAccount } from '@/lib/api/finance';
import { BankAccountsView } from '@/components/finance/BankAccountsView';
import { BankAccountDialog, BankVerifyDialog, WalletDialog, type BankFormValues } from '@/components/finance/BankAccountDialogs';

export default function BankAccountsPage() {
  const { organization, capabilities } = useOrgContext();
  // Bank accounts belong to the organization's owner; every member reads them, only the owner changes them.
  const organizerId = organization?.ownerId ?? null;
  const banks = useReferenceList('BANK');
  const walletState = usePayoutWallet();
  const { saveWallet } = useSetMobileMoneyAccount();
  const { bankAccounts, loading, error, refetch } = useMyBankAccounts(organizerId);
  const snackbar = useSnackbar();
  const [createBankAccount] = useMutation(CREATE_BANK_ACCOUNT);
  const [updateBankAccount] = useMutation(UPDATE_BANK_ACCOUNT);
  const { deleteBankAccount } = useDeleteBankAccount();
  const { setDefaultBankAccount } = useSetDefaultBankAccount();
  const verification = useBankVerification();
  const ensureFresh = useStepUp();

  const run = async (fn: () => Promise<{ success: boolean; message: string | null }>, ok: string) => {
    // Bank-account changes redirect payouts: require a recent interactive login first.
    if (!(await ensureFresh())) return { success: false, message: 'Confirm your identity to continue' };
    const res = await fn();
    snackbar.show(res.success ? ok : { message: res.message ?? 'Something went wrong', tone: 'error' });
    if (res.success) await refetch();
    return res;
  };

  return (
    <BankAccountsView
      accounts={bankAccounts}
      loading={loading}
      error={error}
      onRetry={() => void refetch()}
      canManage={capabilities.isOwner}
      wallet={walletState.wallet}
      renderWalletForm={(wallet, close, switchToBank) => (
        <WalletDialog
          initial={wallet ? { holder: wallet.accountHolderName ?? '', network: wallet.provider ?? undefined } : undefined}
          onClose={close}
          onSwitchToBank={switchToBank}
          onSave={async (v) => {
            if (!(await ensureFresh())) return;
            if (!walletState.organizationId) throw new Error('Your organization could not be loaded');
            await saveWallet(walletState.organizationId, { provider: v.network as MobileMoneyProvider, phoneNumber: v.phone, accountHolderName: v.holder });
            snackbar.show('Wallet saved. We will send a small test deposit to verify it.');
          }}
        />
      )}
      onStartVerification={async (id) => {
        try {
          if (!(await ensureFresh())) return;
          await verification.start(id);
          snackbar.show('Test deposit started. Check the account for a small amount.');
          await refetch();
        } catch (e) {
          snackbar.show({ message: (e as Error).message, tone: 'error' });
        }
      }}
      onMakeDefault={async (id) => void (await run(() => setDefaultBankAccount(id), 'Default account updated'))}
      onDelete={async (id) => void (await run(() => deleteBankAccount(id), 'Account deleted'))}
      renderForm={(account, close, switchToWallet) => (
        <BankAccountDialog
          banks={banks.items.map((b) => b.name)}
          onSwitchToWallet={switchToWallet}
          initial={
            account
              ? { id: account.id, holder: account.accountHolderName, bankName: account.bankName, branchCode: account.branchCode ?? '', number: account.accountNumber, swift: account.swiftCode ?? '', currency: account.currency }
              : undefined
          }
          onClose={close}
          onSave={async (v: BankFormValues) => {
            if (!(await ensureFresh())) return;
            const input = { accountHolderName: v.holder, bankName: v.bankName, branchCode: v.branchCode || null, swiftCode: v.swift || null, currency: v.currency };
            // Rejections carry the server error contract; the dialog's <Form> maps them onto fields.
            if (account) await updateBankAccount({ variables: { id: account.id, input: { accountHolderName: input.accountHolderName, bankName: input.bankName, branchCode: input.branchCode, swiftCode: input.swiftCode } } });
            else await createBankAccount({ variables: { input: { ...input, organizerId: organizerId ?? '', accountNumber: v.number } } });
            snackbar.show(account ? 'Account saved' : 'Account added. Start a test deposit to verify it.');
            await refetch();
          }}
        />
      )}
      renderVerify={(account, close) => (
        <BankVerifyDialog
          accountNumber={account.accountNumber}
          onClose={close}
          onConfirm={async (amountMinor) => {
            if (!(await ensureFresh())) return undefined;
            const res = await verification.confirm(account.id, minorToKwachaString(amountMinor));
            const st = (res.data as { confirmBankVerification?: { status?: string } } | null | undefined)?.confirmBankVerification?.status;
            if (st === 'REJECTED') snackbar.show({ message: 'Too many wrong tries. The account was rejected.', tone: 'error' });
            else if (st !== 'VERIFIED') return { amountError: 'That is not the amount we sent.' };
            else snackbar.show('Account verified');
            await refetch();
            return undefined;
          }}
        />
      )}
    />
  );
}
