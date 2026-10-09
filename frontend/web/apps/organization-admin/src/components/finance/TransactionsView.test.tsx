import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TransactionsView, type TransactionsViewProps } from './TransactionsView';

const tx = (o: Record<string, unknown>) => ({
  id: 't1', type: 'TICKET_SALE', description: null, amount: '150', currency: 'ZMW', status: 'COMPLETED',
  timestamp: '2026-09-01T10:00:00Z', eventId: 'e1', eventTitle: 'Fixture Fest', reference: 'BK-1', ...o,
});
const base = (o: Partial<TransactionsViewProps> = {}): TransactionsViewProps => ({
  transactions: [tx({}) as never, tx({ id: 't2', type: 'REFUND', amount: '-50', reference: 'RF-1' }) as never],
  events: [{ id: 'e1', title: 'Fixture Fest' }],
  type: 'all', eventId: 'all', onTypeChange: vi.fn(), onEventChange: vi.fn(), loading: false, ...o,
});

describe('TransactionsView', () => {
  it('renders signed amounts', () => {
    render(<TransactionsView {...base()} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Transactions' })).toBeInTheDocument();
    expect(screen.getByText('+K 150.00')).toBeInTheDocument();
    expect(screen.getByText('-K 50.00')).toBeInTheDocument();
  });
  it('reports filter changes and searches locally', () => {
    const p = base();
    render(<TransactionsView {...p} />);
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'REFUND' } });
    expect(p.onTypeChange).toHaveBeenCalledWith('REFUND');
    fireEvent.change(screen.getByLabelText('Search transactions'), { target: { value: 'RF-1' } });
    expect(screen.queryByText('BK-1')).toBeNull();
  });
  it('handles empty, loading and error', () => {
    const { rerender } = render(<TransactionsView {...base({ transactions: [] })} />);
    expect(screen.getByText('No transactions match.')).toBeInTheDocument();
    rerender(<TransactionsView {...base({ transactions: [], loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    rerender(<TransactionsView {...base({ error: new Error('x') })} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});
