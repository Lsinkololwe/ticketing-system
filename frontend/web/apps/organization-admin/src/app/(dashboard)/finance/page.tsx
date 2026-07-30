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
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  canRequestPayouts,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import {
  useMyFinanceOverview,
  useMyTransactions,
  useMyPayouts,
} from '@pml.tickets/shared/api/organization-admin/modules/finance';

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
// DATA MAPPING
// =============================================================================

/** Map a backend transaction type to the row's display category + sign. */
function mapTransactionType(type: string): Transaction['type'] {
  switch (type) {
    case 'TICKET_SALE':
      return 'sale';
    case 'REFUND':
      return 'refund';
    case 'PAYOUT':
      return 'payout';
    default:
      return 'fee'; // PLATFORM_FEE, ADJUSTMENT
  }
}

function mapTransactionStatus(status: string): Transaction['status'] {
  const s = (status || '').toLowerCase();
  if (s.includes('pending')) return 'pending';
  if (s.includes('fail')) return 'failed';
  return 'completed';
}

function mapPayoutStatus(status: string): PayoutRequest['status'] {
  switch (status) {
    case 'PENDING':
      return 'pending';
    case 'APPROVED':
    case 'PROCESSING':
      return 'processing';
    case 'COMPLETED':
      return 'completed';
    default:
      return 'rejected'; // REJECTED, FAILED, CANCELLED
  }
}

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Currency is ALWAYS rendered as "K 125,430" — Kwacha symbol, space, tabular
 * figures (spec §10). Never "ZMW", never "$". Pair with `.ds-amount` so the
 * figures set in Fira Code and columns of digits line up.
 */
function formatCurrency(amount: number): string {
  return `K ${amount.toLocaleString('en-ZM', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
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

  // A sale is money coming in — jade, the money role. Everything that reduces
  // the balance uses a generic status ramp so the two never blur together.
  const typeConfig = {
    sale: { color: 'var(--color-money-text)', icon: <ArrowDownLeft width={16} height={16} /> },
    payout: { color: 'var(--gray-11)', icon: <ArrowUpRight width={16} height={16} /> },
    refund: { color: 'var(--status-danger-11)', icon: <ArrowUpRight width={16} height={16} /> },
    fee: { color: 'var(--status-warning-11)', icon: <ArrowUpRight width={16} height={16} /> },
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
            borderRadius: 'var(--radius-3)',
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
            borderRadius: 'var(--radius-3)',
            background: 'var(--accent-a3)',
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
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const { organization, status } = useMyOrganization({ skip: !isAuthenticated });
  const canPayout = canRequestPayouts(status);
  const organizerId = organization?.ownerId ?? null;

  const { overview } = useMyFinanceOverview({ skip: !isAuthenticated });
  const { transactions: txnRows } = useMyTransactions({ size: 5, skip: !isAuthenticated });
  const { payouts: payoutRows } = useMyPayouts(organizerId);

  const stats = useMemo(
    () => ({
      escrowBalance: Number(overview?.availableBalance ?? 0),
      totalRevenue: Number(overview?.netEarnings ?? 0),
      pendingPayouts: Number(overview?.pendingBalance ?? 0),
      thisMonthRevenue: Number(overview?.earningsThisMonth ?? 0),
      lastMonthRevenue: Number(overview?.earningsLastMonth ?? 0),
      platformFees: Number(overview?.platformFees ?? 0),
      refunds: Number(overview?.totalRefunds ?? 0),
    }),
    [overview]
  );

  const pendingPayoutCount = overview?.pendingPayoutRequests ?? 0;

  const transactions: Transaction[] = useMemo(
    () =>
      txnRows.map((t) => {
        const magnitude = Math.abs(Number(t.amount ?? 0));
        const type = mapTransactionType(t.type);
        return {
          id: t.id,
          type,
          description: t.description,
          amount: type === 'sale' ? magnitude : -magnitude,
          date: t.timestamp,
          eventName: t.eventTitle ?? undefined,
          status: mapTransactionStatus(t.status),
        };
      }),
    [txnRows]
  );

  const payouts: PayoutRequest[] = useMemo(
    () =>
      payoutRows
        .map((p) => ({
          id: p.id,
          amount: Number(p.requestedAmount ?? 0),
          status: mapPayoutStatus(p.status),
          requestedAt: p.requestedAt,
          completedAt: p.processedAt ?? undefined,
        }))
        .filter((p) => p.status === 'pending' || p.status === 'processing'),
    [payoutRows]
  );

  const revenueGrowth =
    overview?.monthlyGrowth ??
    (stats.lastMonthRevenue === 0
      ? 100
      : ((stats.thisMonthRevenue - stats.lastMonthRevenue) / stats.lastMonthRevenue) * 100);

  return (
    <Box>
      <PageHeader
        title="Finance"
        description="Your balance, payouts and transaction history."
        actions={canPayout ? [
          {
            label: 'Request payout',
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
          title="Available balance"
          value={formatCurrency(stats.escrowBalance)}
          icon={<Wallet style={{ width: 20, height: 20 }} />}
          change={12}
          changeLabel="Ready to pay out"
        />
        <StatCard
          title="Total revenue"
          value={formatCurrency(stats.totalRevenue)}
          icon={<GraphUp style={{ width: 20, height: 20 }} />}
          change={18}
          changeLabel="All time"
        />
        <StatCard
          title="This month"
          value={formatCurrency(stats.thisMonthRevenue)}
          icon={<Calendar style={{ width: 20, height: 20 }} />}
          change={revenueGrowth}
          changeLabel="vs last month"
        />
        <StatCard
          title="Pending payouts"
          value={formatCurrency(stats.pendingPayouts)}
          icon={<Clock width={20} height={20} />}
          changeLabel={`${pendingPayoutCount} request${pendingPayoutCount === 1 ? '' : 's'} waiting`}
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
              borderRadius: 'var(--card-radius-bento)',
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

            {transactions.length === 0 ? (
              <Box py="6" style={{ textAlign: 'center' }}>
                <Text size="2" style={{ color: 'var(--content-muted)' }}>No transactions yet</Text>
              </Box>
            ) : (
              <Flex direction="column">
                {transactions.map((transaction) => (
                  <TransactionRow key={transaction.id} transaction={transaction} />
                ))}
              </Flex>
            )}
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
              borderRadius: 'var(--card-radius-bento)',
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

            {payouts.length > 0 ? (
              <Flex direction="column">
                {payouts.map((payout) => (
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

            {canPayout && (
              <Box mt="4">
                <Link href="/finance/payouts?action=new" style={{ textDecoration: 'none', width: '100%' }}>
                  <Button
                    size="2"
                    style={{
                      width: '100%',
                      background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
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
              borderRadius: 'var(--card-radius-bento)',
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
                    {formatCurrency(stats.totalRevenue + stats.platformFees + stats.refunds)}
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
                        background: 'var(--status-warning-9)',
                      }}
                    />
                    <Text size="2" style={{ color: 'var(--gray-11)' }}>
                      Platform fees (5%)
                    </Text>
                  </Flex>
                  <Text size="2" className="ds-amount" style={{ color: 'var(--status-warning-11)' }}>
                    -{formatCurrency(stats.platformFees)}
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
                        background: 'var(--status-danger-9)',
                      }}
                    />
                    <Text size="2" style={{ color: 'var(--gray-11)' }}>
                      Refunds
                    </Text>
                  </Flex>
                  <Text size="2" className="ds-amount" style={{ color: 'var(--status-danger-11)' }}>
                    -{formatCurrency(stats.refunds)}
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
                    {formatCurrency(stats.totalRevenue)}
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
              borderRadius: 'var(--card-radius-bento)',
            }}
          >
            <Flex direction="column" gap="2">
              <Link href="/finance/transactions" style={{ textDecoration: 'none' }}>
                <Flex
                  align="center"
                  justify="between"
                  p="3"
                  style={{
                    borderRadius: 'var(--radius-3)',
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
                    borderRadius: 'var(--radius-3)',
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
