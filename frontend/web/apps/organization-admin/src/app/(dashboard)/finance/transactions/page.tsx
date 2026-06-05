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
    ticketType: 'Early Bird',
    customerName: 'John Doe',
    status: 'completed',
    reference: 'TXN-001-2025',
  },
  {
    id: '2',
    type: 'sale',
    description: 'Ticket Sale - VIP',
    amount: 500,
    date: '2025-05-19T09:15:00',
    eventName: 'Summer Music Festival',
    ticketType: 'VIP',
    customerName: 'Jane Smith',
    status: 'completed',
    reference: 'TXN-002-2025',
  },
  {
    id: '3',
    type: 'sale',
    description: 'Ticket Sale - Regular',
    amount: 100,
    date: '2025-05-19T08:45:00',
    eventName: 'Tech Conference 2025',
    ticketType: 'Regular',
    customerName: 'Mike Johnson',
    status: 'completed',
    reference: 'TXN-003-2025',
  },
  {
    id: '4',
    type: 'refund',
    description: 'Refund Processed',
    amount: -75,
    date: '2025-05-18T16:45:00',
    eventName: 'Tech Conference 2025',
    customerName: 'Peter Brown',
    status: 'completed',
    reference: 'REF-001-2025',
  },
  {
    id: '5',
    type: 'fee',
    description: 'Platform Commission (5%)',
    amount: -32.50,
    date: '2025-05-18T14:00:00',
    status: 'completed',
    reference: 'FEE-001-2025',
  },
  {
    id: '6',
    type: 'payout',
    description: 'Payout to Stanbic Bank',
    amount: -5000,
    date: '2025-05-17T12:00:00',
    status: 'completed',
    reference: 'PAY-001-2025',
  },
  {
    id: '7',
    type: 'sale',
    description: 'Ticket Sale - Regular',
    amount: 200,
    date: '2025-05-17T11:30:00',
    eventName: 'Summer Music Festival',
    ticketType: 'Regular (x2)',
    customerName: 'Sarah Wilson',
    status: 'completed',
    reference: 'TXN-004-2025',
  },
  {
    id: '8',
    type: 'sale',
    description: 'Ticket Sale - VIP',
    amount: 500,
    date: '2025-05-17T10:00:00',
    eventName: 'Summer Music Festival',
    ticketType: 'VIP',
    customerName: 'Tom Anderson',
    status: 'completed',
    reference: 'TXN-005-2025',
  },
  {
    id: '9',
    type: 'fee',
    description: 'Platform Commission (5%)',
    amount: -35,
    date: '2025-05-16T18:00:00',
    status: 'completed',
    reference: 'FEE-002-2025',
  },
  {
    id: '10',
    type: 'sale',
    description: 'Ticket Sale - Early Bird',
    amount: 150,
    date: '2025-05-16T15:30:00',
    eventName: 'Tech Conference 2025',
    ticketType: 'Early Bird',
    customerName: 'Lisa Davis',
    status: 'completed',
    reference: 'TXN-006-2025',
  },
];

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
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [dateFilter, setDateFilter] = useState<string>('all');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;

  // Filter transactions
  const filteredTransactions = useMemo(() => {
    let result = mockTransactions;

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
  }, [typeFilter, dateFilter, searchQuery]);

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
    // TODO: Implement CSV export
    console.log('Exporting transactions...');
  };

  return (
    <Box>
      <PageHeader
        title="Transactions"
        description="View your complete transaction history"
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
            borderRadius: '12px',
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
            borderRadius: '12px',
            flex: '1 1 150px',
          }}
        >
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block' }}>
            Total Expenses
          </Text>
          <Text size="4" weight="bold" style={{ color: '#EF4444' }}>
            {formatCurrency(summary.expenses)}
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
            borderRadius: '12px',
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
          borderRadius: '12px',
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
          borderRadius: '16px',
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
                          ? 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)'
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
