'use client';

/**
 * Transactions Page
 *
 * View all financial transactions:
 * - Ticket sales
 * - Payouts
 * - Refunds
 * - Platform fees
 */

import { useState, useMemo } from 'react';
import {
  Box,
  Flex,
  Text,
  Card,
  Button,
  Badge,
  TextField,
  Select,
  Table,
} from '@radix-ui/themes';
import {
  Search,
  Download,
  ArrowDownLeft,
  ArrowUpRight,
  NavArrowLeft,
  NavArrowRight,
} from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import { useSession } from '@/lib/auth/client';
import { useMyTransactions } from '@pml.tickets/shared/api/organization-admin/modules/finance';

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
  ticketType?: string;
  customerName?: string;
  status: 'completed' | 'pending' | 'failed';
  reference?: string;
}

// =============================================================================
// DATA MAPPING
// =============================================================================

function mapTransactionType(type: string): Transaction['type'] {
  switch (type) {
    case 'TICKET_SALE': return 'sale';
    case 'REFUND': return 'refund';
    case 'PAYOUT': return 'payout';
    default: return 'fee'; // PLATFORM_FEE, ADJUSTMENT
  }
}

function mapTransactionStatus(status: string): Transaction['status'] {
  const t = (status || '').toLowerCase();
  if (t.includes('pending')) return 'pending';
  if (t.includes('fail')) return 'failed';
  return 'completed';
}

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

function formatTime(dateString: string): string {
  return new Date(dateString).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

// =============================================================================
// TYPE CONFIG
// =============================================================================

const typeConfig = {
  sale: {
    label: 'Sale',
    color: 'green',
    icon: <ArrowDownLeft style={{ width: 14, height: 14 }} />,
  },
  payout: {
    label: 'Payout',
    color: 'gray',
    icon: <ArrowUpRight style={{ width: 14, height: 14 }} />,
  },
  refund: {
    label: 'Refund',
    color: 'red',
    icon: <ArrowUpRight style={{ width: 14, height: 14 }} />,
  },
  fee: {
    label: 'Fee',
    color: 'orange',
    icon: <ArrowUpRight style={{ width: 14, height: 14 }} />,
  },
};

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function TransactionsPage() {
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [dateFilter, setDateFilter] = useState<string>('all');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;

  const { transactions: txnRows } = useMyTransactions({ size: 200, skip: !isAuthenticated });

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
          reference: t.reference ?? undefined,
          status: mapTransactionStatus(t.status),
        };
      }),
    [txnRows]
  );

  // Filter transactions
  const filteredTransactions = useMemo(() => {
    let result = transactions;

    if (typeFilter !== 'all') {
      result = result.filter((t) => t.type === typeFilter);
    }

    if (dateFilter !== 'all') {
      const now = new Date();
      const startDate = new Date();

      if (dateFilter === '7days') {
        startDate.setDate(now.getDate() - 7);
      } else if (dateFilter === '30days') {
        startDate.setDate(now.getDate() - 30);
      } else if (dateFilter === '90days') {
        startDate.setDate(now.getDate() - 90);
      }

      result = result.filter((t) => new Date(t.date) >= startDate);
    }

    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (t) =>
          t.description.toLowerCase().includes(query) ||
          t.eventName?.toLowerCase().includes(query) ||
          t.customerName?.toLowerCase().includes(query) ||
          t.reference?.toLowerCase().includes(query)
      );
    }

    return result;
  }, [transactions, typeFilter, dateFilter, searchQuery]);

  // Pagination
  const totalPages = Math.ceil(filteredTransactions.length / itemsPerPage);
  const paginatedTransactions = useMemo(() => {
    const start = (currentPage - 1) * itemsPerPage;
    return filteredTransactions.slice(start, start + itemsPerPage);
  }, [filteredTransactions, currentPage]);

  // Summary stats
  const summary = useMemo(() => {
    const income = filteredTransactions
      .filter((t) => t.amount > 0)
      .reduce((sum, t) => sum + t.amount, 0);
    const expenses = filteredTransactions
      .filter((t) => t.amount < 0)
      .reduce((sum, t) => sum + Math.abs(t.amount), 0);
    return {
      income,
      expenses,
      net: income - expenses,
      count: filteredTransactions.length,
    };
  }, [filteredTransactions]);

  const handleExport = () => {
    const header = ['Date', 'Type', 'Description', 'Event', 'Reference', 'Status', 'Amount'];
    const rows = filteredTransactions.map((t) => [
      t.date,
      t.type,
      t.description,
      t.eventName ?? '',
      t.reference ?? '',
      t.status,
      String(t.amount),
    ]);
    const csv = [header, ...rows]
      .map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(','))
      .join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'transactions.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <Box>
      <PageHeader
        title="Transactions"
        description="Every sale, refund, fee and payout, newest first."
        breadcrumbs={[
          { label: 'Finance', href: '/finance' },
          { label: 'Transactions' },
        ]}
        actions={[
          {
            label: 'Export CSV',
            icon: <Download style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: handleExport,
            variant: 'outline' as const,
          },
        ]}
      />

      {/* Summary Stats */}
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
            Total Income
          </Text>
          <Text size="4" weight="bold" style={{ color: 'var(--brand-500)' }}>
            {formatCurrency(summary.income)}
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
            Total Expenses
          </Text>
          <Text size="4" weight="bold" className="ds-amount" style={{ color: 'var(--status-danger-11)' }}>
            {formatCurrency(summary.expenses)}
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
            Net Amount
          </Text>
          <Text size="4" weight="bold" style={{ color: 'var(--content-primary)' }}>
            {formatCurrency(summary.net)}
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
            Transactions
          </Text>
          <Text size="4" weight="bold" style={{ color: 'var(--content-primary)' }}>
            {summary.count}
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
              placeholder="Search transactions..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            >
              <TextField.Slot>
                <Search style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
              </TextField.Slot>
            </TextField.Root>
          </Box>
          <Select.Root value={typeFilter} onValueChange={setTypeFilter}>
            <Select.Trigger style={{ width: 140 }} />
            <Select.Content>
              <Select.Item value="all">All Types</Select.Item>
              <Select.Item value="sale">Sales</Select.Item>
              <Select.Item value="payout">Payouts</Select.Item>
              <Select.Item value="refund">Refunds</Select.Item>
              <Select.Item value="fee">Fees</Select.Item>
            </Select.Content>
          </Select.Root>
          <Select.Root value={dateFilter} onValueChange={setDateFilter}>
            <Select.Trigger style={{ width: 160 }} />
            <Select.Content>
              <Select.Item value="all">All Time</Select.Item>
              <Select.Item value="7days">Last 7 Days</Select.Item>
              <Select.Item value="30days">Last 30 Days</Select.Item>
              <Select.Item value="90days">Last 90 Days</Select.Item>
            </Select.Content>
          </Select.Root>
        </Flex>
      </Card>

      {/* Transactions Table */}
      <Card
        style={{
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: 'var(--card-radius-bento)',
          overflow: 'hidden',
        }}
      >
        {paginatedTransactions.length === 0 ? (
          <Box py="8" style={{ textAlign: 'center' }}>
            <Text size="3" style={{ color: 'var(--content-muted)' }}>
              No transactions found
            </Text>
          </Box>
        ) : (
          <>
            <Table.Root>
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell style={{ color: 'var(--content-muted)', fontWeight: 500 }}>
                    Date
                  </Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell style={{ color: 'var(--content-muted)', fontWeight: 500 }}>
                    Description
                  </Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell style={{ color: 'var(--content-muted)', fontWeight: 500 }}>
                    Type
                  </Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell style={{ color: 'var(--content-muted)', fontWeight: 500 }}>
                    Reference
                  </Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell style={{ color: 'var(--content-muted)', fontWeight: 500, textAlign: 'right' }}>
                    Amount
                  </Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>

              <Table.Body>
                {paginatedTransactions.map((transaction) => {
                  const config = typeConfig[transaction.type];
                  const isPositive = transaction.amount > 0;

                  return (
                    <Table.Row key={transaction.id}>
                      <Table.Cell>
                        <Box>
                          <Text size="2" style={{ color: 'var(--content-primary)', display: 'block' }}>
                            {formatDate(transaction.date)}
                          </Text>
                          <Text size="1" style={{ color: 'var(--content-muted)' }}>
                            {formatTime(transaction.date)}
                          </Text>
                        </Box>
                      </Table.Cell>
                      <Table.Cell>
                        <Box>
                          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                            {transaction.description}
                          </Text>
                          {transaction.eventName && (
                            <Text size="1" style={{ color: 'var(--content-muted)' }}>
                              {transaction.eventName}
                              {transaction.customerName && ` • ${transaction.customerName}`}
                            </Text>
                          )}
                        </Box>
                      </Table.Cell>
                      <Table.Cell>
                        <Badge color={config.color as any} variant="soft">
                          <Flex align="center" gap="1">
                            {config.icon}
                            {config.label}
                          </Flex>
                        </Badge>
                      </Table.Cell>
                      <Table.Cell>
                        <Text size="2" style={{ color: 'var(--content-muted)', fontFamily: 'monospace' }}>
                          {transaction.reference || '-'}
                        </Text>
                      </Table.Cell>
                      <Table.Cell style={{ textAlign: 'right' }}>
                        <Text
                          size="2"
                          weight="medium"
                          style={{ color: isPositive ? 'var(--brand-500)' : 'var(--content-secondary)' }}
                        >
                          {isPositive ? '+' : ''}{formatCurrency(transaction.amount)}
                        </Text>
                      </Table.Cell>
                    </Table.Row>
                  );
                })}
              </Table.Body>
            </Table.Root>

            {/* Pagination */}
            {totalPages > 1 && (
              <Flex
                justify="between"
                align="center"
                p="4"
                style={{ borderTop: '1px solid var(--surface-border)' }}
              >
                <Text size="2" style={{ color: 'var(--content-muted)' }}>
                  Showing {(currentPage - 1) * itemsPerPage + 1} to{' '}
                  {Math.min(currentPage * itemsPerPage, filteredTransactions.length)} of{' '}
                  {filteredTransactions.length} transactions
                </Text>
                <Flex gap="2">
                  <Button
                    variant="outline"
                    size="1"
                    disabled={currentPage === 1}
                    onClick={() => setCurrentPage((p) => p - 1)}
                    style={{ borderColor: 'var(--surface-border)' }}
                  >
                    <NavArrowLeft style={{ width: 16, height: 16 }} />
                  </Button>
                  {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                    <Button
                      key={page}
                      variant={page === currentPage ? 'solid' : 'outline'}
                      size="1"
                      onClick={() => setCurrentPage(page)}
                      style={{
                        borderColor: 'var(--surface-border)',
                        background: page === currentPage
                          ? 'linear-gradient(135deg, var(--accent-9), var(--accent-11))'
                          : undefined,
                      }}
                    >
                      {page}
                    </Button>
                  ))}
                  <Button
                    variant="outline"
                    size="1"
                    disabled={currentPage === totalPages}
                    onClick={() => setCurrentPage((p) => p + 1)}
                    style={{ borderColor: 'var(--surface-border)' }}
                  >
                    <NavArrowRight style={{ width: 16, height: 16 }} />
                  </Button>
                </Flex>
              </Flex>
            )}
          </>
        )}
      </Card>
    </Box>
  );
}
