'use client';

/**
 * Finance Overview Page
 *
 * Displays organization financial summary:
 * - Escrow balance
 * - Revenue stats
 * - Recent transactions
 * - Pending payouts
 * - Quick actions
 */

import { useMemo } from 'react';
import Link from 'next/link';
import {
  Box,
  Flex,
  Text,
  Card,
  Button,
  Badge,
  Progress,
} from '@radix-ui/themes';
import {
  Wallet,
  CreditCard,
  ArrowDownLeft,
  ArrowUpRight,
  Plus,
  Calendar,
  GraphUp,
  WarningCircle,
  Check,
  Clock,
  NavArrowRight,
} from 'iconoir-react';
import { PageHeader, StatCard } from '@/components/ui';
import { useOrganization } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// TYPES
// =============================================================================

interface Transaction {
  id: string;
  type: 'sale' | 'payout' | 'refund' | 'fee';
  description: string;
  amount: number;
  date: string;
  eventName?: string;
  status: 'completed' | 'pending' | 'failed';
}

interface PayoutRequest {
  id: string;
  amount: number;
  status: 'pending' | 'processing' | 'completed' | 'rejected';
  requestedAt: string;
  completedAt?: string;
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockTransactions: Transaction[] = [
  {
    id: '1',
    type: 'sale',
    description: 'Ticket Sale - Early Bird',
    amount: 150,
    date: '2025-05-19T10:30:00',
    eventName: 'Summer Music Festival',
    status: 'completed',
  },
  {
    id: '2',
    type: 'sale',
    description: 'Ticket Sale - VIP',
    amount: 500,
    date: '2025-05-19T09:15:00',
    eventName: 'Summer Music Festival',
    status: 'completed',
  },
  {
    id: '3',
    type: 'refund',
    description: 'Refund Processed',
    amount: -75,
    date: '2025-05-18T16:45:00',
    eventName: 'Tech Conference 2025',
    status: 'completed',
  },
  {
    id: '4',
    type: 'fee',
    description: 'Platform Commission (5%)',
    amount: -32.50,
    date: '2025-05-18T14:00:00',
    status: 'completed',
  },
  {
    id: '5',
    type: 'payout',
    description: 'Payout to Bank Account',
    amount: -5000,
    date: '2025-05-17T12:00:00',
    status: 'completed',
  },
];

const mockPayouts: PayoutRequest[] = [
  {
    id: '1',
    amount: 5000,
    status: 'processing',
    requestedAt: '2025-05-18T10:00:00',
  },
  {
    id: '2',
    amount: 3500,
    status: 'pending',
    requestedAt: '2025-05-17T15:30:00',
  },
];

// =============================================================================
// STATS CONFIG
// =============================================================================

const financialStats = {
  escrowBalance: 12450.00,
  totalRevenue: 45680.50,
  pendingPayouts: 8500.00,
  thisMonthRevenue: 8250.00,
  lastMonthRevenue: 7100.00,
  platformFees: 2284.03,
  refunds: 450.00,
};

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
    hour: '2-digit',
    minute: '2-digit',
  });
}

// =============================================================================
// TRANSACTION ROW COMPONENT
// =============================================================================

function TransactionRow({ transaction }: { transaction: Transaction }) {
  const isPositive = transaction.amount > 0;

  const typeConfig = {
    sale: { color: 'var(--brand-500)', icon: <ArrowDownLeft style={{ width: 16, height: 16 }} /> },
    payout: { color: 'var(--content-muted)', icon: <ArrowUpRight style={{ width: 16, height: 16 }} /> },
    refund: { color: '#EF4444', icon: <ArrowUpRight style={{ width: 16, height: 16 }} /> },
    fee: { color: '#F59E0B', icon: <ArrowUpRight style={{ width: 16, height: 16 }} /> },
  };

  const config = typeConfig[transaction.type];

  return (
    <Flex
      justify="between"
      align="center"
      py="3"
      style={{ borderBottom: '1px solid var(--surface-border)' }}
    >
      <Flex gap="3" align="center">
        <Box
          style={{
            width: 36,
            height: 36,
            borderRadius: '10px',
            background: `${config.color}15`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: config.color,
          }}
        >
          {config.icon}
        </Box>
        <Box>
          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
            {transaction.description}
          </Text>
          <Flex align="center" gap="2">
            {transaction.eventName && (
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                {transaction.eventName}
              </Text>
            )}
            <Text size="1" style={{ color: 'var(--content-muted)' }}>
              {formatDate(transaction.date)}
            </Text>
          </Flex>
        </Box>
      </Flex>
      <Text
        size="2"
        weight="medium"
        style={{ color: isPositive ? 'var(--brand-500)' : 'var(--content-secondary)' }}
      >
        {isPositive ? '+' : ''}{formatCurrency(transaction.amount)}
      </Text>
    </Flex>
  );
}

// =============================================================================
// PAYOUT ROW COMPONENT
// =============================================================================

function PayoutRow({ payout }: { payout: PayoutRequest }) {
  const statusConfig = {
    pending: { color: 'orange', icon: <Clock style={{ width: 14, height: 14 }} /> },
    processing: { color: 'blue', icon: <Clock style={{ width: 14, height: 14 }} /> },
    completed: { color: 'green', icon: <Check style={{ width: 14, height: 14 }} /> },
    rejected: { color: 'red', icon: <WarningCircle style={{ width: 14, height: 14 }} /> },
  };

  const config = statusConfig[payout.status];

  return (
    <Flex
      justify="between"
      align="center"
      py="3"
      style={{ borderBottom: '1px solid var(--surface-border)' }}
    >
      <Flex gap="3" align="center">
        <Box
          style={{
            width: 36,
            height: 36,
            borderRadius: '10px',
            background: 'rgba(16, 185, 129, 0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--brand-500)',
          }}
        >
          <Wallet style={{ width: 16, height: 16 }} />
        </Box>
        <Box>
          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
            {formatCurrency(payout.amount)}
          </Text>
          <Text size="1" style={{ color: 'var(--content-muted)' }}>
            Requested {formatDate(payout.requestedAt)}
          </Text>
        </Box>
      </Flex>
      <Badge color={config.color as any} variant="soft">
        <Flex align="center" gap="1">
          {config.icon}
          {payout.status.charAt(0).toUpperCase() + payout.status.slice(1)}
        </Flex>
      </Badge>
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function FinancePage() {
  const { can } = useOrganization();
  const canRequestPayouts = can('requestPayouts');

  const revenueGrowth = useMemo(() => {
    if (financialStats.lastMonthRevenue === 0) return 100;
    return ((financialStats.thisMonthRevenue - financialStats.lastMonthRevenue) / financialStats.lastMonthRevenue) * 100;
  }, []);

  return (
    <Box>
      <PageHeader
        title="Finance"
        description="Manage your earnings, payouts, and financial reports"
        actions={canRequestPayouts ? [
          {
            label: 'Request Payout',
            icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
            href: '/finance/payouts?action=new',
          },
        ] : undefined}
      />

      {/* Stats Grid */}
      <Box
        mb="6"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '16px',
        }}
      >
        <StatCard
          title="Available Balance"
          value={formatCurrency(financialStats.escrowBalance)}
          icon={<Wallet style={{ width: 20, height: 20 }} />}
          change={12}
          changeLabel="Ready for payout"
        />
        <StatCard
          title="Total Revenue"
          value={formatCurrency(financialStats.totalRevenue)}
          icon={<GraphUp style={{ width: 20, height: 20 }} />}
          change={18}
          changeLabel="All time"
        />
        <StatCard
          title="This Month"
          value={formatCurrency(financialStats.thisMonthRevenue)}
          icon={<Calendar style={{ width: 20, height: 20 }} />}
          change={revenueGrowth}
          changeLabel="vs last month"
        />
        <StatCard
          title="Pending Payouts"
          value={formatCurrency(financialStats.pendingPayouts)}
          icon={<Clock style={{ width: 20, height: 20 }} />}
          subtitle={`${mockPayouts.length} requests`}
        />
      </Box>

      {/* Two Column Layout */}
      <Flex gap="6" direction={{ initial: 'column', md: 'row' }}>
        {/* Recent Transactions */}
        <Box style={{ flex: 2 }}>
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Flex justify="between" align="center" mb="4">
              <Text size="4" weight="medium" style={{ color: 'var(--content-primary)' }}>
                Recent Transactions
              </Text>
              <Link href="/finance/transactions" style={{ textDecoration: 'none' }}>
                <Button variant="ghost" size="2" style={{ color: 'var(--brand-500)' }}>
                  View All
                  <NavArrowRight style={{ width: 16, height: 16, marginLeft: 4 }} />
                </Button>
              </Link>
            </Flex>

            <Flex direction="column">
              {mockTransactions.slice(0, 5).map((transaction) => (
                <TransactionRow key={transaction.id} transaction={transaction} />
              ))}
            </Flex>
          </Card>
        </Box>

        {/* Sidebar */}
        <Flex direction="column" gap="6" style={{ flex: 1 }}>
          {/* Pending Payouts */}
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Flex justify="between" align="center" mb="4">
              <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
                Pending Payouts
              </Text>
              <Link href="/finance/payouts" style={{ textDecoration: 'none' }}>
                <Button variant="ghost" size="1" style={{ color: 'var(--brand-500)' }}>
                  View All
                </Button>
              </Link>
            </Flex>

            {mockPayouts.length > 0 ? (
              <Flex direction="column">
                {mockPayouts.map((payout) => (
                  <PayoutRow key={payout.id} payout={payout} />
                ))}
              </Flex>
            ) : (
              <Box py="6" style={{ textAlign: 'center' }}>
                <Text size="2" style={{ color: 'var(--content-muted)' }}>
                  No pending payouts
                </Text>
              </Box>
            )}

            {canRequestPayouts && (
              <Box mt="4">
                <Link href="/finance/payouts?action=new" style={{ textDecoration: 'none', width: '100%' }}>
                  <Button
                    size="2"
                    style={{
                      width: '100%',
                      background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                    }}
                  >
                    <Plus style={{ width: 16, height: 16, marginRight: 8 }} />
                    Request Payout
                  </Button>
                </Link>
              </Box>
            )}
          </Card>

          {/* Breakdown Card */}
          <Card
            style={{
              padding: '24px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Text size="3" weight="medium" mb="4" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Revenue Breakdown
            </Text>

            <Flex direction="column" gap="4">
              {/* Gross Revenue */}
              <Box>
                <Flex justify="between" mb="2">
                  <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                    Gross Revenue
                  </Text>
                  <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                    {formatCurrency(financialStats.totalRevenue + financialStats.platformFees + financialStats.refunds)}
                  </Text>
                </Flex>
              </Box>

              {/* Platform Fees */}
              <Box>
                <Flex justify="between" mb="2">
                  <Flex align="center" gap="2">
                    <Box
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: '#F59E0B',
                      }}
                    />
                    <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                      Platform Fees (5%)
                    </Text>
                  </Flex>
                  <Text size="2" style={{ color: '#F59E0B' }}>
                    -{formatCurrency(financialStats.platformFees)}
                  </Text>
                </Flex>
                <Progress value={5} max={100} color="orange" size="1" />
              </Box>

              {/* Refunds */}
              <Box>
                <Flex justify="between" mb="2">
                  <Flex align="center" gap="2">
                    <Box
                      style={{
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: '#EF4444',
                      }}
                    />
                    <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                      Refunds
                    </Text>
                  </Flex>
                  <Text size="2" style={{ color: '#EF4444' }}>
                    -{formatCurrency(financialStats.refunds)}
                  </Text>
                </Flex>
                <Progress value={1} max={100} color="red" size="1" />
              </Box>

              {/* Divider */}
              <Box style={{ borderTop: '1px solid var(--surface-border)', marginTop: '8px', paddingTop: '12px' }}>
                <Flex justify="between">
                  <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                    Net Revenue
                  </Text>
                  <Text size="3" weight="bold" style={{ color: 'var(--brand-500)' }}>
                    {formatCurrency(financialStats.totalRevenue)}
                  </Text>
                </Flex>
              </Box>
            </Flex>
          </Card>

          {/* Quick Links */}
          <Card
            style={{
              padding: '20px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
            }}
          >
            <Flex direction="column" gap="2">
              <Link href="/finance/transactions" style={{ textDecoration: 'none' }}>
                <Flex
                  align="center"
                  justify="between"
                  p="3"
                  style={{
                    borderRadius: '10px',
                    cursor: 'pointer',
                    transition: 'background 0.15s ease',
                  }}
                  className="hover-subtle"
                >
                  <Flex align="center" gap="3">
                    <CreditCard style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
                    <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                      Transaction History
                    </Text>
                  </Flex>
                  <NavArrowRight style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
                </Flex>
              </Link>
              <Link href="/finance/bank-accounts" style={{ textDecoration: 'none' }}>
                <Flex
                  align="center"
                  justify="between"
                  p="3"
                  style={{
                    borderRadius: '10px',
                    cursor: 'pointer',
                    transition: 'background 0.15s ease',
                  }}
                  className="hover-subtle"
                >
                  <Flex align="center" gap="3">
                    <Wallet style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
                    <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                      Bank Accounts
                    </Text>
                  </Flex>
                  <NavArrowRight style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
                </Flex>
              </Link>
            </Flex>
          </Card>
        </Flex>
      </Flex>

      <style jsx global>{`
        .hover-subtle:hover {
          background: var(--surface-subtle);
        }
      `}</style>
    </Box>
  );
}
