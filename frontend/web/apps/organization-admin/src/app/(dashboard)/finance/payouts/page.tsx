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
  return new Intl.NumberFormat('en-ZM', {
    style: 'currency',
    currency: 'ZMW',
    minimumFractionDigits: 2,
  }).format(amount);
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
        borderRadius: '12px',
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
              borderRadius: '12px',
              background: 'rgba(16, 185, 129, 0.1)',
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
              borderRadius: '12px',
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
                  borderRadius: '8px',
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

function NewPayoutDialog({ open, onOpenChange, bankAccounts, availableBalance, onSubmit }: NewPayoutDialogProps) {
  const [amount, setAmount] = useState('');
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
        <Dialog.Title>Request Payout</Dialog.Title>
        <Dialog.Description size="2" style={{ color: 'var(--content-muted)' }}>
          Transfer funds from your escrow balance to your bank account.
        </Dialog.Description>

        <Flex direction="column" gap="4" mt="4">
          {/* Available Balance */}
          <Box
            p="4"
            style={{
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid rgba(16, 185, 129, 0.2)',
              borderRadius: '12px',
            }}
          >
            <Flex justify="between" align="center">
              <Flex align="center" gap="2">
                <Wallet style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
                <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                  Available Balance
                </Text>
              </Flex>
              <Text size="4" weight="bold" style={{ color: 'var(--brand-500)' }}>
                {formatCurrency(availableBalance)}
              </Text>
            </Flex>
          </Box>

          {/* Amount */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Amount (ZMW)
            </Text>
            <TextField.Root
              size="3"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              min={0}
              max={availableBalance}
              style={{
                border: amount && !isValidAmount ? '1px solid var(--error-500)' : undefined,
              }}
            />
            {amount && !isValidAmount && (
              <Text size="1" style={{ color: 'var(--error-500)', display: 'block', marginTop: '4px' }}>
                {amountNum > availableBalance
                  ? 'Amount exceeds available balance'
                  : 'Please enter a valid amount'}
              </Text>
            )}
          </Box>

          {/* Bank Account */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Bank Account
            </Text>
            <Select.Root value={selectedBank} onValueChange={setSelectedBank}>
              <Select.Trigger style={{ width: '100%' }} />
              <Select.Content>
                {bankAccounts.map((account) => (
                  <Select.Item key={account.id} value={account.id}>
                    <Flex align="center" gap="2">
                      <Bank style={{ width: 16, height: 16 }} />
                      {account.bankName} - ****{account.accountNumber.slice(-4)}
                      {account.isDefault && (
                        <Badge size="1" variant="soft" color="green">Default</Badge>
                      )}
                    </Flex>
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Box>

          {/* Notes */}
          <Box>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-secondary)', display: 'block' }}>
              Notes (Optional)
            </Text>
            <TextArea
              size="2"
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add a note for your reference..."
            />
          </Box>

          {/* Processing Info */}
          <Box
            p="3"
            style={{
              background: 'var(--surface-subtle)',
              borderRadius: '8px',
            }}
          >
            <Flex align="center" gap="2">
              <Clock style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                Payouts are typically processed within 1-3 business days
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
            disabled={!isValidAmount || !selectedBank || isSubmitting}
            style={{
              background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
              cursor: isValidAmount && selectedBank && !isSubmitting ? 'pointer' : 'not-allowed',
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
            label: 'Request Payout',
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
            borderRadius: '12px',
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
            borderRadius: '12px',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Pending
          </Text>
          <Text size="4" weight="bold" style={{ color: '#F59E0B' }}>
            {stats.pending}
          </Text>
        </Card>
        <Card
          style={{
            padding: '16px 20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Processing
          </Text>
          <Text size="4" weight="bold" style={{ color: '#3B82F6' }}>
            {stats.processing}
          </Text>
        </Card>
        <Card
          style={{
            padding: '16px 20px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
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
          borderRadius: '12px',
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
                background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
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
