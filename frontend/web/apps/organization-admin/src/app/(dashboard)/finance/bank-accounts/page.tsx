'use client';

/**
 * Bank Accounts Page
 *
 * Manage bank accounts for payouts:
 * - Add bank accounts
 * - Set default account
 * - Remove accounts
 */

import { useState, useCallback } from 'react';
import {
  Box,
  Flex,
  Text,
  Card,
  Button,
  Badge,
  TextField,
  Select,
  Dialog,
  AlertDialog,
} from '@radix-ui/themes';
import {
  Plus,
  Bank,
  Trash,
  Check,
  Edit,
  Star,
  StarSolid,
  WarningTriangle,
} from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import { useOrganization } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// TYPES
// =============================================================================

interface BankAccount {
  id: string;
  bankName: string;
  bankCode: string;
  branchCode: string;
  accountNumber: string;
  accountHolder: string;
  accountType: 'savings' | 'current';
  isDefault: boolean;
  isVerified: boolean;
  createdAt: string;
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockBankAccounts: BankAccount[] = [
  {
    id: '1',
    bankName: 'Stanbic Bank Zambia',
    bankCode: 'SBICZMLX',
    branchCode: 'LSK001',
    accountNumber: '1234567890004521',
    accountHolder: 'Event Organizers Ltd',
    accountType: 'current',
    isDefault: true,
    isVerified: true,
    createdAt: '2024-06-15T10:00:00',
  },
  {
    id: '2',
    bankName: 'Zanaco',
    bankCode: 'ZNCOZMLU',
    branchCode: 'NDL002',
    accountNumber: '9876543210007832',
    accountHolder: 'Event Organizers Ltd',
    accountType: 'current',
    isDefault: false,
    isVerified: true,
    createdAt: '2024-09-20T14:30:00',
  },
];

const zambianBanks = [
  { code: 'SBICZMLX', name: 'Stanbic Bank Zambia' },
  { code: 'ZNCOZMLU', name: 'Zanaco' },
  { code: 'BABORZMLU', name: 'Atlas Mara Bank' },
  { code: 'FILOZMLU', name: 'First National Bank' },
  { code: 'INDBZMLU', name: 'Indo-Zambia Bank' },
  { code: 'AABORZMLU', name: 'Absa Bank Zambia' },
  { code: 'ACBKZMLU', name: 'Access Bank Zambia' },
  { code: 'UBPRZMLX', name: 'United Bank for Africa' },
];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

function formatAccountNumber(number: string): string {
  return `****${number.slice(-4)}`;
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

// =============================================================================
// BANK ACCOUNT CARD COMPONENT
// =============================================================================

interface BankAccountCardProps {
  account: BankAccount;
  onSetDefault: (id: string) => void;
  onEdit: (account: BankAccount) => void;
  onDelete: (account: BankAccount) => void;
  canManage: boolean;
}

function BankAccountCard({ account, onSetDefault, onEdit, onDelete, canManage }: BankAccountCardProps) {
  return (
    <Card
      style={{
        padding: '24px',
        background: 'var(--surface-elevated)',
        border: account.isDefault
          ? '2px solid var(--brand-500)'
          : '1px solid var(--surface-border)',
        borderRadius: '16px',
        position: 'relative',
      }}
    >
      {account.isDefault && (
        <Badge
          color="green"
          variant="soft"
          style={{
            position: 'absolute',
            top: '16px',
            right: '16px',
          }}
        >
          <Flex align="center" gap="1">
            <StarSolid style={{ width: 12, height: 12 }} />
            Default
          </Flex>
        </Badge>
      )}

      <Flex gap="4">
        <Box
          style={{
            width: 56,
            height: 56,
            borderRadius: '14px',
            background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.2) 0%, rgba(5, 150, 105, 0.2) 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Bank style={{ width: 28, height: 28, color: 'var(--brand-500)' }} />
        </Box>

        <Box style={{ flex: 1 }}>
          <Flex align="center" gap="2" mb="1">
            <Text size="3" weight="bold" style={{ color: 'var(--content-primary)' }}>
              {account.bankName}
            </Text>
            {account.isVerified && (
              <Badge color="green" variant="soft" size="1">
                <Flex align="center" gap="1">
                  <Check style={{ width: 12, height: 12 }} />
                  Verified
                </Flex>
              </Badge>
            )}
          </Flex>

          <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: '12px' }}>
            {account.accountHolder}
          </Text>

          <Flex gap="4" wrap="wrap">
            <Box>
              <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Account Number
              </Text>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', fontFamily: 'monospace' }}>
                {formatAccountNumber(account.accountNumber)}
              </Text>
            </Box>
            <Box>
              <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Account Type
              </Text>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', textTransform: 'capitalize' }}>
                {account.accountType}
              </Text>
            </Box>
            <Box>
              <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Added
              </Text>
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                {formatDate(account.createdAt)}
              </Text>
            </Box>
          </Flex>
        </Box>
      </Flex>

      {canManage && (
        <Flex gap="2" mt="4" pt="4" style={{ borderTop: '1px solid var(--surface-border)' }}>
          {!account.isDefault && (
            <Button
              variant="outline"
              size="1"
              onClick={() => onSetDefault(account.id)}
              style={{ borderColor: 'var(--surface-border)' }}
            >
              <Star style={{ width: 14, height: 14, marginRight: 6 }} />
              Set as Default
            </Button>
          )}
          <Button
            variant="outline"
            size="1"
            onClick={() => onEdit(account)}
            style={{ borderColor: 'var(--surface-border)' }}
          >
            <Edit style={{ width: 14, height: 14, marginRight: 6 }} />
            Edit
          </Button>
          {!account.isDefault && (
            <Button
              variant="outline"
              size="1"
              color="red"
              onClick={() => onDelete(account)}
            >
              <Trash style={{ width: 14, height: 14, marginRight: 6 }} />
              Remove
            </Button>
          )}
        </Flex>
      )}
    </Card>
  );
}

// =============================================================================
// ADD/EDIT BANK ACCOUNT DIALOG
// =============================================================================

interface BankAccountDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  account?: BankAccount | null;
  onSubmit: (data: Omit<BankAccount, 'id' | 'isDefault' | 'isVerified' | 'createdAt'>) => void;
}

function BankAccountDialog({ open, onOpenChange, account, onSubmit }: BankAccountDialogProps) {
  const [bankCode, setBankCode] = useState(account?.bankCode || '');
  const [branchCode, setBranchCode] = useState(account?.branchCode || '');
  const [accountNumber, setAccountNumber] = useState(account?.accountNumber || '');
  const [accountHolder, setAccountHolder] = useState(account?.accountHolder || '');
  const [accountType, setAccountType] = useState<'savings' | 'current'>(account?.accountType || 'current');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const selectedBank = zambianBanks.find((b) => b.code === bankCode);

  const handleSubmit = async () => {
    if (!bankCode || !branchCode || !accountNumber || !accountHolder) return;

    setIsSubmitting(true);
    try {
      await onSubmit({
        bankName: selectedBank?.name || '',
        bankCode,
        branchCode,
        accountNumber,
        accountHolder,
        accountType,
      });
      onOpenChange(false);
      // Reset form
      setBankCode('');
      setBranchCode('');
      setAccountNumber('');
      setAccountHolder('');
      setAccountType('current');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content style={{ maxWidth: 500 }}>
        <Dialog.Title>{account ? 'Edit Bank Account' : 'Add Bank Account'}</Dialog.Title>
        <Dialog.Description size="2" style={{ color: 'var(--content-muted)' }}>
          {account
            ? 'Update your bank account details'
            : 'Add a bank account to receive payouts'}
        </Dialog.Description>

        <Flex direction="column" gap="4" mt="4">
          {/* Bank Selection */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Bank
            </Text>
            <Select.Root value={bankCode} onValueChange={setBankCode}>
              <Select.Trigger placeholder="Select a bank" style={{ width: '100%' }} />
              <Select.Content>
                {zambianBanks.map((bank) => (
                  <Select.Item key={bank.code} value={bank.code}>
                    {bank.name}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Box>

          {/* Branch Code */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Branch Code
            </Text>
            <TextField.Root
              size="3"
              value={branchCode}
              onChange={(e) => setBranchCode(e.target.value)}
              placeholder="e.g., LSK001"
            />
          </Box>

          {/* Account Number */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Account Number
            </Text>
            <TextField.Root
              size="3"
              value={accountNumber}
              onChange={(e) => setAccountNumber(e.target.value)}
              placeholder="Your account number"
            />
          </Box>

          {/* Account Holder */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Account Holder Name
            </Text>
            <TextField.Root
              size="3"
              value={accountHolder}
              onChange={(e) => setAccountHolder(e.target.value)}
              placeholder="Name as it appears on account"
            />
          </Box>

          {/* Account Type */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Account Type
            </Text>
            <Select.Root value={accountType} onValueChange={(v) => setAccountType(v as any)}>
              <Select.Trigger style={{ width: '100%' }} />
              <Select.Content>
                <Select.Item value="current">Current Account</Select.Item>
                <Select.Item value="savings">Savings Account</Select.Item>
              </Select.Content>
            </Select.Root>
          </Box>

          {/* Warning */}
          <Box
            p="3"
            style={{
              background: 'rgba(245, 158, 11, 0.1)',
              border: '1px solid rgba(245, 158, 11, 0.2)',
              borderRadius: '8px',
            }}
          >
            <Flex align="start" gap="2">
              <WarningTriangle style={{ width: 18, height: 18, color: '#F59E0B', flexShrink: 0, marginTop: 2 }} />
              <Text size="1" style={{ color: 'var(--content-secondary)' }}>
                Please ensure all bank details are correct. Incorrect details may result in failed or delayed payouts.
              </Text>
            </Flex>
          </Box>
        </Flex>

        <Flex gap="3" justify="end" mt="5">
          <Dialog.Close>
            <Button variant="outline" style={{ borderColor: 'var(--surface-border)' }}>
              Cancel
            </Button>
          </Dialog.Close>
          <Button
            onClick={handleSubmit}
            disabled={!bankCode || !branchCode || !accountNumber || !accountHolder || isSubmitting}
            style={{
              background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
              cursor: !bankCode || !branchCode || !accountNumber || !accountHolder || isSubmitting
                ? 'not-allowed'
                : 'pointer',
            }}
          >
            {isSubmitting ? 'Saving...' : account ? 'Save Changes' : 'Add Account'}
          </Button>
        </Flex>
      </Dialog.Content>
    </Dialog.Root>
  );
}

// =============================================================================
// DELETE CONFIRMATION DIALOG
// =============================================================================

interface DeleteConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  account: BankAccount | null;
  onConfirm: () => void;
}

function DeleteConfirmDialog({ open, onOpenChange, account, onConfirm }: DeleteConfirmDialogProps) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Content style={{ maxWidth: 450 }}>
        <AlertDialog.Title>Remove Bank Account</AlertDialog.Title>
        <AlertDialog.Description size="2">
          Are you sure you want to remove <strong>{account?.bankName}</strong> ({formatAccountNumber(account?.accountNumber || '')})?
          This action cannot be undone.
        </AlertDialog.Description>
        <Flex gap="3" justify="end" mt="4">
          <AlertDialog.Cancel>
            <Button variant="outline" style={{ borderColor: 'var(--surface-border)' }}>
              Cancel
            </Button>
          </AlertDialog.Cancel>
          <AlertDialog.Action>
            <Button
              color="red"
              onClick={onConfirm}
              style={{ background: 'linear-gradient(135deg, #EF4444 0%, #DC2626 100%)' }}
            >
              Remove Account
            </Button>
          </AlertDialog.Action>
        </Flex>
      </AlertDialog.Content>
    </AlertDialog.Root>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function BankAccountsPage() {
  const { can } = useOrganization();
  const canRequestPayouts = can('requestPayouts');

  const [accounts, setAccounts] = useState<BankAccount[]>(mockBankAccounts);
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editAccount, setEditAccount] = useState<BankAccount | null>(null);
  const [deleteAccount, setDeleteAccount] = useState<BankAccount | null>(null);

  const handleSetDefault = useCallback((id: string) => {
    setAccounts((prev) =>
      prev.map((acc) => ({
        ...acc,
        isDefault: acc.id === id,
      }))
    );
  }, []);

  const handleAddAccount = useCallback((data: Omit<BankAccount, 'id' | 'isDefault' | 'isVerified' | 'createdAt'>) => {
    const newAccount: BankAccount = {
      ...data,
      id: String(Date.now()),
      isDefault: accounts.length === 0,
      isVerified: false,
      createdAt: new Date().toISOString(),
    };
    setAccounts((prev) => [...prev, newAccount]);
  }, [accounts.length]);

  const handleEditAccount = useCallback((data: Omit<BankAccount, 'id' | 'isDefault' | 'isVerified' | 'createdAt'>) => {
    if (!editAccount) return;
    setAccounts((prev) =>
      prev.map((acc) =>
        acc.id === editAccount.id
          ? { ...acc, ...data }
          : acc
      )
    );
    setEditAccount(null);
  }, [editAccount]);

  const handleDeleteAccount = useCallback(() => {
    if (!deleteAccount) return;
    setAccounts((prev) => prev.filter((acc) => acc.id !== deleteAccount.id));
    setDeleteAccount(null);
  }, [deleteAccount]);

  return (
    <Box>
      <PageHeader
        title="Bank Accounts"
        description="Manage your bank accounts for receiving payouts"
        breadcrumbs={[
          { label: 'Finance', href: '/finance' },
          { label: 'Bank Accounts' },
        ]}
        actions={canRequestPayouts ? [
          {
            label: 'Add Account',
            icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: () => setShowAddDialog(true),
          },
        ] : undefined}
      />

      {/* Info Card */}
      <Card
        mb="6"
        style={{
          padding: '16px 20px',
          background: 'rgba(59, 130, 246, 0.1)',
          border: '1px solid rgba(59, 130, 246, 0.2)',
          borderRadius: '12px',
        }}
      >
        <Flex align="center" gap="3">
          <Bank style={{ width: 20, height: 20, color: '#3B82F6' }} />
          <Text size="2" style={{ color: 'var(--content-secondary)' }}>
            <strong>Payout Information:</strong>{' '}
            <Text style={{ color: 'var(--content-muted)' }}>
              Payouts are processed within 1-3 business days to your default bank account. A 5% platform fee applies to all payouts.
            </Text>
          </Text>
        </Flex>
      </Card>

      {/* Bank Accounts List */}
      {accounts.length === 0 ? (
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '16px',
            textAlign: 'center',
          }}
        >
          <Box
            style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'var(--surface-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 20px',
            }}
          >
            <Bank style={{ width: 28, height: 28, color: 'var(--content-muted)' }} />
          </Box>
          <Text size="4" weight="medium" style={{ color: 'var(--content-primary)', display: 'block', marginBottom: '8px' }}>
            No bank accounts added
          </Text>
          <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: '24px' }}>
            Add a bank account to start receiving payouts from your ticket sales
          </Text>
          {canRequestPayouts && (
            <Button
              size="3"
              onClick={() => setShowAddDialog(true)}
              style={{
                background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
              }}
            >
              <Plus style={{ width: 18, height: 18, marginRight: 8 }} />
              Add First Bank Account
            </Button>
          )}
        </Card>
      ) : (
        <Flex direction="column" gap="4">
          {accounts.map((account) => (
            <BankAccountCard
              key={account.id}
              account={account}
              onSetDefault={handleSetDefault}
              onEdit={setEditAccount}
              onDelete={setDeleteAccount}
              canManage={canRequestPayouts}
            />
          ))}
        </Flex>
      )}

      {/* Add Account Dialog */}
      <BankAccountDialog
        open={showAddDialog}
        onOpenChange={setShowAddDialog}
        onSubmit={handleAddAccount}
      />

      {/* Edit Account Dialog */}
      <BankAccountDialog
        open={!!editAccount}
        onOpenChange={(open) => !open && setEditAccount(null)}
        account={editAccount}
        onSubmit={handleEditAccount}
      />

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteAccount}
        onOpenChange={(open) => !open && setDeleteAccount(null)}
        account={deleteAccount}
        onConfirm={handleDeleteAccount}
      />
    </Box>
  );
}
