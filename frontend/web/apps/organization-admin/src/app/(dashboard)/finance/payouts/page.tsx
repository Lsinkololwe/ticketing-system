'use client';

/**
 * Payouts Page
 *
 * Manage payout requests:
 * - Request new payouts
 * - View payout history
 * - Track payout status
 */

import { useState, useMemo, useCallback } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
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
  TextArea,
} from '@radix-ui/themes';
import {
  Plus,
  Search,
  Wallet,
  Check,
  Clock,
  WarningCircle,
  Bank,
  NavArrowRight,
} from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  canRequestPayouts,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// TYPES
// =============================================================================

interface PayoutRequest {
  id: string;
  amount: number;
  status: 'pending' | 'processing' | 'completed' | 'rejected';
  requestedAt: string;
  completedAt?: string;
  bankAccount: {
    bankName: string;
    accountNumber: string;
  };
  reference?: string;
  notes?: string;
}

interface BankAccount {
  id: string;
  bankName: string;
  accountNumber: string;
  accountHolder: string;
  isDefault: boolean;
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockPayouts: PayoutRequest[] = [
  {
    id: '1',
    amount: 5000,
    status: 'processing',
    requestedAt: '2025-05-18T10:00:00',
    bankAccount: { bankName: 'Stanbic Bank', accountNumber: '****4521' },
    notes: 'Monthly payout for April sales',
  },
  {
    id: '2',
    amount: 3500,
    status: 'pending',
    requestedAt: '2025-05-17T15:30:00',
    bankAccount: { bankName: 'Zanaco', accountNumber: '****7832' },
  },
  {
    id: '3',
    amount: 8200,
    status: 'completed',
    requestedAt: '2025-05-10T09:00:00',
    completedAt: '2025-05-12T14:30:00',
    bankAccount: { bankName: 'Stanbic Bank', accountNumber: '****4521' },
    reference: 'PAY-2025-0510-8200',
  },
  {
    id: '4',
    amount: 2100,
    status: 'completed',
    requestedAt: '2025-05-01T11:20:00',
    completedAt: '2025-05-03T16:45:00',
    bankAccount: { bankName: 'Stanbic Bank', accountNumber: '****4521' },
    reference: 'PAY-2025-0501-2100',
  },
  {
    id: '5',
    amount: 1500,
    status: 'rejected',
    requestedAt: '2025-04-28T08:15:00',
    bankAccount: { bankName: 'Invalid Bank', accountNumber: '****0000' },
    notes: 'Rejected due to invalid bank details',
  },
];

const mockBankAccounts: BankAccount[] = [
  {
    id: '1',
    bankName: 'Stanbic Bank',
    accountNumber: '1234567890004521',
    accountHolder: 'Event Organizers Ltd',
    isDefault: true,
  },
  {
    id: '2',
    bankName: 'Zanaco',
    accountNumber: '9876543210007832',
    accountHolder: 'Event Organizers Ltd',
    isDefault: false,
  },
];

const availableBalance = 12450.00;

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

function formatCurrency(amount: number): string {
  // Currency is ALWAYS "K 125,430" (spec §10) — never "ZMW", never "$".
  return `K ${amount.toLocaleString('en-ZM', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function formatDate(dateString: string): string {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatDateTime(dateString: string): string {
  return new Date(dateString).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// =============================================================================
// STATUS CONFIG
// =============================================================================

const statusConfig = {
  pending: {
    label: 'Pending',
    color: 'orange',
    icon: <Clock style={{ width: 14, height: 14 }} />,
    description: 'Awaiting approval',
  },
  processing: {
    label: 'Processing',
    color: 'blue',
    icon: <Clock style={{ width: 14, height: 14 }} />,
    description: 'Being processed by the bank',
  },
  completed: {
    label: 'Completed',
    color: 'green',
    icon: <Check style={{ width: 14, height: 14 }} />,
    description: 'Funds transferred successfully',
  },
  rejected: {
    label: 'Rejected',
    color: 'red',
    icon: <WarningCircle style={{ width: 14, height: 14 }} />,
    description: 'Request was rejected',
  },
};

// =============================================================================
// PAYOUT CARD COMPONENT
// =============================================================================

interface PayoutCardProps {
  payout: PayoutRequest;
  onClick: (payout: PayoutRequest) => void;
}

function PayoutCard({ payout, onClick }: PayoutCardProps) {
  const config = statusConfig[payout.status];

  return (
    <Card
      style={{
        padding: '20px',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: 'var(--card-radius)',
        cursor: 'pointer',
        transition: 'border-color 0.15s ease, box-shadow 0.15s ease',
      }}
      onClick={() => onClick(payout)}
      className="payout-card"
    >
      <Flex justify="between" align="start">
        <Flex gap="3">
          <Box
            style={{
              width: 44,
              height: 44,
              borderRadius: 'var(--card-radius)',
              background: 'var(--accent-a3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--brand-500)',
            }}
          >
            <Wallet style={{ width: 22, height: 22 }} />
          </Box>
          <Box>
            <Text size="3" weight="bold" style={{ color: 'var(--content-primary)', display: 'block' }}>
              {formatCurrency(payout.amount)}
            </Text>
            <Text size="1" style={{ color: 'var(--content-muted)', display: 'block', marginTop: '2px' }}>
              {payout.bankAccount.bankName} • {payout.bankAccount.accountNumber}
            </Text>
            <Flex align="center" gap="2" mt="2">
              <Badge color={config.color as any} variant="soft" size="1">
                <Flex align="center" gap="1">
                  {config.icon}
                  {config.label}
                </Flex>
              </Badge>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {formatDate(payout.requestedAt)}
              </Text>
            </Flex>
          </Box>
        </Flex>
        <NavArrowRight style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
      </Flex>
    </Card>
  );
}

// =============================================================================
// PAYOUT DETAIL DIALOG
// =============================================================================

interface PayoutDetailDialogProps {
  payout: PayoutRequest | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function PayoutDetailDialog({ payout, open, onOpenChange }: PayoutDetailDialogProps) {
  if (!payout) return null;

  const config = statusConfig[payout.status];

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content style={{ maxWidth: 480 }}>
        <Dialog.Title>Payout Details</Dialog.Title>

        <Flex direction="column" gap="4" mt="4">
          {/* Amount */}
          <Box
            p="4"
            style={{
              background: 'var(--surface-subtle)',
              borderRadius: 'var(--card-radius)',
              textAlign: 'center',
            }}
          >
            <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
              Amount
            </Text>
            <Text size="7" weight="bold" style={{ color: 'var(--content-primary)' }}>
              {formatCurrency(payout.amount)}
            </Text>
          </Box>

          {/* Status */}
          <Flex justify="between" align="center">
            <Text size="2" style={{ color: 'var(--content-muted)' }}>Status</Text>
            <Badge color={config.color as any} variant="soft">
              <Flex align="center" gap="1">
                {config.icon}
                {config.label}
              </Flex>
            </Badge>
          </Flex>

          {/* Bank Account */}
          <Flex justify="between" align="center">
            <Text size="2" style={{ color: 'var(--content-muted)' }}>Bank Account</Text>
            <Text size="2" style={{ color: 'var(--content-primary)' }}>
              {payout.bankAccount.bankName} ({payout.bankAccount.accountNumber})
            </Text>
          </Flex>

          {/* Requested Date */}
          <Flex justify="between" align="center">
            <Text size="2" style={{ color: 'var(--content-muted)' }}>Requested</Text>
            <Text size="2" style={{ color: 'var(--content-primary)' }}>
              {formatDateTime(payout.requestedAt)}
            </Text>
          </Flex>

          {/* Completed Date */}
          {payout.completedAt && (
            <Flex justify="between" align="center">
              <Text size="2" style={{ color: 'var(--content-muted)' }}>Completed</Text>
              <Text size="2" style={{ color: 'var(--content-primary)' }}>
                {formatDateTime(payout.completedAt)}
              </Text>
            </Flex>
          )}

          {/* Reference */}
          {payout.reference && (
            <Flex justify="between" align="center">
              <Text size="2" style={{ color: 'var(--content-muted)' }}>Reference</Text>
              <Text size="2" weight="medium" style={{ color: 'var(--brand-500)', fontFamily: 'monospace' }}>
                {payout.reference}
              </Text>
            </Flex>
          )}

          {/* Notes */}
          {payout.notes && (
            <Box>
              <Text size="2" mb="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
                Notes
              </Text>
              <Box
                p="3"
                style={{
                  background: 'var(--surface-subtle)',
                  borderRadius: 'var(--radius-4)',
                }}
              >
                <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                  {payout.notes}
                </Text>
              </Box>
            </Box>
          )}
        </Flex>

        <Flex gap="3" justify="end" mt="5">
          <Dialog.Close>
            <Button variant="outline" style={{ borderColor: 'var(--surface-border)' }}>
              Close
            </Button>
          </Dialog.Close>
        </Flex>
      </Dialog.Content>
    </Dialog.Root>
  );
}

// =============================================================================
// NEW PAYOUT DIALOG
// =============================================================================

interface NewPayoutDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  bankAccounts: BankAccount[];
  availableBalance: number;
  onSubmit: (amount: number, bankAccountId: string, notes: string) => void;
}

/**
 * Payout destinations.
 *
 * Mobile money (MTN / Airtel / Zamtel) is a CORE destination family and is
 * ALWAYS visible on this form — never behind a "more options" disclosure
 * (spec §8). Providers whose wallet is not yet saved still render, with an
 * inline route to add one, rather than being hidden.
 */
const PAYOUT_DESTINATIONS = [
  { id: 'bank', label: 'Bank transfer', dot: null },
  { id: 'mtn', label: 'MTN MoMo', dot: 'var(--momo-mtn)' },
  { id: 'airtel', label: 'Airtel Money', dot: 'var(--momo-airtel)' },
  { id: 'zamtel', label: 'Zamtel Kwacha', dot: 'var(--momo-zamtel)' },
] as const;

type PayoutDestination = (typeof PAYOUT_DESTINATIONS)[number]['id'];

function NewPayoutDialog({ open, onOpenChange, bankAccounts, availableBalance, onSubmit }: NewPayoutDialogProps) {
  const [amount, setAmount] = useState('');
  const [destination, setDestination] = useState<PayoutDestination>('bank');
  const [selectedBank, setSelectedBank] = useState(bankAccounts.find((b) => b.isDefault)?.id || '');
  const [notes, setNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const amountNum = parseFloat(amount) || 0;
  const isValidAmount = amountNum > 0 && amountNum <= availableBalance;

  const handleSubmit = async () => {
    if (!isValidAmount || !selectedBank) return;

    setIsSubmitting(true);
    try {
      await onSubmit(amountNum, selectedBank, notes);
      onOpenChange(false);
      setAmount('');
      setNotes('');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content style={{ maxWidth: 480 }}>
        <Dialog.Title>Request payout</Dialog.Title>
        <Dialog.Description size="2" style={{ color: 'var(--gray-10)' }}>
          Move money out of your escrow balance to a bank account or mobile money wallet.
        </Dialog.Description>

        <Flex direction="column" gap="4" mt="4">
          {/* Available balance — jade, because this is money. */}
          <Box
            p="4"
            style={{
              background: 'var(--color-money-surface)',
              border: '1px solid var(--gray-a5)',
              borderRadius: 'var(--card-radius)',
            }}
          >
            <Flex justify="between" align="center">
              <Flex align="center" gap="2">
                <Wallet width={20} height={20} style={{ color: 'var(--color-money-text)' }} />
                <Text className="ds-label">Available balance</Text>
              </Flex>
              <Text
                size="4"
                weight="bold"
                className="ds-amount"
                style={{ color: 'var(--color-money-text)' }}
              >
                {formatCurrency(availableBalance)}
              </Text>
            </Flex>
          </Box>

          {/* Amount */}
          <Box>
            <Text as="label" className="ds-label" mb="2" style={{ display: 'block' }}>
              Amount (K)
            </Text>
            <TextField.Root
              data-testid="payout-amount"
              size="3"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              min={0}
              max={availableBalance}
              aria-invalid={Boolean(amount) && !isValidAmount}
              className="ds-amount"
              style={{
                border: amount && !isValidAmount ? '1px solid var(--status-danger-9)' : undefined,
              }}
            />
            {amount && !isValidAmount && (
              <Text
                role="alert"
                size="1"
                style={{ color: 'var(--status-danger-11)', display: 'block', marginTop: 4 }}
              >
                {amountNum > availableBalance
                  ? `That is more than your available balance of ${formatCurrency(availableBalance)}.`
                  : 'Enter an amount greater than zero.'}
              </Text>
            )}
          </Box>

          {/* Destination family — mobile money is always on screen. */}
          <Box>
            <Text as="p" className="ds-label" mb="2" style={{ display: 'block' }}>
              Pay out to
            </Text>
            <Flex gap="2" wrap="wrap">
              {PAYOUT_DESTINATIONS.map((option) => {
                const active = destination === option.id;
                return (
                  <button
                    key={option.id}
                    type="button"
                    data-testid={`payout-destination-${option.id}`}
                    onClick={() => setDestination(option.id)}
                    aria-pressed={active}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      minHeight: 40,
                      padding: '0 12px',
                      borderRadius: 'var(--radius-3)',
                      border: `1px solid ${active ? 'var(--accent-8)' : 'var(--gray-a5)'}`,
                      background: active ? 'var(--accent-a3)' : 'var(--color-surface)',
                      color: active ? 'var(--accent-11)' : 'var(--gray-11)',
                      fontFamily: 'var(--font-sans)',
                      fontSize: 'var(--text-2-size)',
                      fontWeight: 'var(--weight-medium)',
                      cursor: 'pointer',
                    }}
                  >
                    {option.dot && (
                      <span
                        aria-hidden="true"
                        className="ds-momo-dot"
                        style={{ background: option.dot }}
                      />
                    )}
                    {!option.dot && <Bank width={16} height={16} aria-hidden="true" />}
                    {option.label}
                  </button>
                );
              })}
            </Flex>
          </Box>

          {/* Destination detail */}
          {destination === 'bank' ? (
            <Box>
              <Text as="label" className="ds-label" mb="2" style={{ display: 'block' }}>
                Bank account
              </Text>
              <Select.Root value={selectedBank} onValueChange={setSelectedBank}>
                <Select.Trigger data-testid="payout-bank-account" style={{ width: '100%' }} />
                <Select.Content>
                  {bankAccounts.map((account) => (
                    <Select.Item key={account.id} value={account.id}>
                      <Flex align="center" gap="2">
                        <Bank width={16} height={16} />
                        <span className="ds-amount">
                          {account.bankName} ····{account.accountNumber.slice(-4)}
                        </span>
                        {account.isDefault && (
                          <Badge size="1" variant="soft" color="green">
                            Default
                          </Badge>
                        )}
                      </Flex>
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Box>
          ) : (
            <Box
              p="3"
              style={{
                background: 'var(--status-info-a3)',
                borderRadius: 'var(--radius-4)',
                border: '1px solid var(--gray-a5)',
              }}
            >
              <Text as="p" size="2" style={{ color: 'var(--gray-12)' }}>
                No {PAYOUT_DESTINATIONS.find((d) => d.id === destination)?.label} wallet is saved
                yet.
              </Text>
              <Text as="p" size="2" style={{ color: 'var(--gray-11)', marginTop: 2 }}>
                Add one under{' '}
                <Link href="/finance/bank-accounts" style={{ color: 'var(--accent-11)' }}>
                  payout destinations
                </Link>
                , then come back to request the payout.
              </Text>
            </Box>
          )}

          {/* Notes */}
          <Box>
            <Text as="label" className="ds-label" mb="2" style={{ display: 'block' }}>
              Note (optional)
            </Text>
            <TextArea
              data-testid="payout-notes"
              size="2"
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="A reference for your own records"
            />
          </Box>

          <Box p="3" style={{ background: 'var(--gray-a3)', borderRadius: 'var(--radius-4)' }}>
            <Flex align="center" gap="2">
              <Clock width={16} height={16} style={{ color: 'var(--gray-9)' }} aria-hidden="true" />
              <Text size="1" style={{ color: 'var(--gray-10)' }}>
                Payouts usually land within one to three business days.
              </Text>
            </Flex>
          </Box>
        </Flex>

        <Flex gap="3" justify="end" mt="5">
          <Dialog.Close>
            <Button data-testid="payout-cancel" variant="outline" color="gray">
              Cancel
            </Button>
          </Dialog.Close>
          <Button
            data-testid="payout-submit"
            color="teal"
            onClick={handleSubmit}
            disabled={!isValidAmount || !selectedBank || destination !== 'bank' || isSubmitting}
            style={{
              cursor:
                isValidAmount && selectedBank && destination === 'bank' && !isSubmitting
                  ? 'pointer'
                  : 'not-allowed',
            }}
          >
            {isSubmitting ? 'Submitting...' : `Request ${amount ? formatCurrency(amountNum) : 'Payout'}`}
          </Button>
        </Flex>
      </Dialog.Content>
    </Dialog.Root>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function PayoutsPage() {
  const searchParams = useSearchParams();
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const { status } = useMyOrganization({ skip: !isAuthenticated });
  const canPayout = canRequestPayouts(status);

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [selectedPayout, setSelectedPayout] = useState<PayoutRequest | null>(null);
  const [showNewPayoutDialog, setShowNewPayoutDialog] = useState(searchParams.get('action') === 'new');
  const [payouts, setPayouts] = useState(mockPayouts);

  // Filter payouts
  const filteredPayouts = useMemo(() => {
    let result = payouts;

    if (statusFilter !== 'all') {
      result = result.filter((p) => p.status === statusFilter);
    }

    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (p) =>
          p.bankAccount.bankName.toLowerCase().includes(query) ||
          p.reference?.toLowerCase().includes(query) ||
          p.amount.toString().includes(query)
      );
    }

    return result;
  }, [payouts, statusFilter, searchQuery]);

  const handleNewPayout = useCallback((amount: number, bankAccountId: string, notes: string) => {
    const bank = mockBankAccounts.find((b) => b.id === bankAccountId);
    if (!bank) return;

    const newPayout: PayoutRequest = {
      id: String(Date.now()),
      amount,
      status: 'pending',
      requestedAt: new Date().toISOString(),
      bankAccount: {
        bankName: bank.bankName,
        accountNumber: `****${bank.accountNumber.slice(-4)}`,
      },
      notes: notes || undefined,
    };

    setPayouts((prev) => [newPayout, ...prev]);
  }, []);

  // Stats
  const stats = useMemo(() => ({
    pending: payouts.filter((p) => p.status === 'pending').length,
    processing: payouts.filter((p) => p.status === 'processing').length,
    completed: payouts.filter((p) => p.status === 'completed').reduce((sum, p) => sum + p.amount, 0),
    total: payouts.length,
  }), [payouts]);

  return (
    <Box>
      <PageHeader
        title="Payouts"
        description="Request and track your payout requests"
        breadcrumbs={[
          { label: 'Finance', href: '/finance' },
          { label: 'Payouts' },
        ]}
        actions={canPayout ? [
          {
            label: 'Request payout',
            icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: () => setShowNewPayoutDialog(true),
          },
        ] : undefined}
      />

      {/* Stats */}
      <Flex gap="4" mb="6" wrap="wrap">
        <Card
          style={{
            padding: '16px 20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius)',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Available Balance
          </Text>
          <Text size="4" weight="bold" style={{ color: 'var(--brand-500)' }}>
            {formatCurrency(availableBalance)}
          </Text>
        </Card>
        <Card
          style={{
            padding: '16px 20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius)',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Pending
          </Text>
          <Text size="4" weight="bold" className="ds-amount" style={{ color: 'var(--status-warning-11)' }}>
            {stats.pending}
          </Text>
        </Card>
        <Card
          style={{
            padding: '16px 20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius)',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Processing
          </Text>
          <Text size="4" weight="bold" className="ds-amount" style={{ color: 'var(--status-info-11)' }}>
            {stats.processing}
          </Text>
        </Card>
        <Card
          style={{
            padding: '16px 20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius)',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Total Paid Out
          </Text>
          <Text size="4" weight="bold" style={{ color: 'var(--content-primary)' }}>
            {formatCurrency(stats.completed)}
          </Text>
        </Card>
      </Flex>

      {/* Filters */}
      <Card
        mb="6"
        style={{
          padding: '16px 20px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: 'var(--card-radius)',
        }}
      >
        <Flex gap="4" align="end" wrap="wrap">
          <Box style={{ flex: '1 1 300px' }}>
            <TextField.Root
              size="2"
              placeholder="Search payouts..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            >
              <TextField.Slot>
                <Search style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
              </TextField.Slot>
            </TextField.Root>
          </Box>
          <Select.Root value={statusFilter} onValueChange={setStatusFilter}>
            <Select.Trigger style={{ width: 160 }} />
            <Select.Content>
              <Select.Item value="all">All Status</Select.Item>
              <Select.Item value="pending">Pending</Select.Item>
              <Select.Item value="processing">Processing</Select.Item>
              <Select.Item value="completed">Completed</Select.Item>
              <Select.Item value="rejected">Rejected</Select.Item>
            </Select.Content>
          </Select.Root>
        </Flex>
      </Card>

      {/* Payouts List */}
      {filteredPayouts.length === 0 ? (
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: 'var(--card-radius-bento)',
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
            <Wallet style={{ width: 28, height: 28, color: 'var(--content-muted)' }} />
          </Box>
          <Text size="4" weight="medium" style={{ color: 'var(--content-primary)', display: 'block', marginBottom: '8px' }}>
            {searchQuery || statusFilter !== 'all' ? 'No matching payouts' : 'No payouts yet'}
          </Text>
          <Text size="2" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: '24px' }}>
            {searchQuery || statusFilter !== 'all'
              ? 'Try adjusting your filters'
              : 'Request a payout to transfer funds to your bank account'}
          </Text>
          {canPayout && !searchQuery && statusFilter === 'all' && (
            <Button
              size="3"
              onClick={() => setShowNewPayoutDialog(true)}
              style={{
                background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
              }}
            >
              <Plus style={{ width: 18, height: 18, marginRight: 8 }} />
              Request First Payout
            </Button>
          )}
        </Card>
      ) : (
        <Flex direction="column" gap="3">
          {filteredPayouts.map((payout) => (
            <PayoutCard
              key={payout.id}
              payout={payout}
              onClick={setSelectedPayout}
            />
          ))}
        </Flex>
      )}

      {/* Payout Detail Dialog */}
      <PayoutDetailDialog
        payout={selectedPayout}
        open={!!selectedPayout}
        onOpenChange={(open) => !open && setSelectedPayout(null)}
      />

      {/* New Payout Dialog */}
      <NewPayoutDialog
        open={showNewPayoutDialog}
        onOpenChange={setShowNewPayoutDialog}
        bankAccounts={mockBankAccounts}
        availableBalance={availableBalance}
        onSubmit={handleNewPayout}
      />

      <style jsx global>{`
        .payout-card:hover {
          border-color: var(--brand-500) !important;
          box-shadow: 0 0 0 1px var(--brand-500);
        }
      `}</style>
    </Box>
  );
}
