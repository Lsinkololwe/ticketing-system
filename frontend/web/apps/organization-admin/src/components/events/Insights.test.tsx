import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const send = vi.fn();
let audience: { count: number | null; error: unknown } = { count: 12, error: null };
let history: { messages: unknown[]; loading: boolean; error: unknown } = { messages: [], loading: false, error: null };
vi.mock('@/lib/api/event-insights', () => ({
  useHolderAudience: () => ({ ...audience, loading: false }),
  useHolderMessages: () => ({ ...history, refetch: vi.fn() }),
  useMessageHolders: () => ({ send }),
}));

import { NotifyTab } from './NotifyTab';
import { PurchaseHeat } from './PurchaseHeat';

const wrap = (ui: React.ReactElement) => render(<SnackbarProvider>{ui}</SnackbarProvider>);

describe('NotifyTab', () => {
  it('shows the audience size, validates and sends to the chosen segment and tier', async () => {
    const user = userEvent.setup();
    send.mockResolvedValue({ data: { messageTicketHolders: { recipientCount: 12 } } });
    wrap(<NotifyTab eventId="e1" tiers={[{ id: 't1', name: 'VIP' }]} canNotify />);
    expect(screen.getByText('12 people will receive this.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Send message' }));
    expect((await screen.findAllByText(/Enter a subject/)).length).toBeGreaterThan(0);
    expect(send).not.toHaveBeenCalled();
    await user.selectOptions(screen.getByLabelText('Send to'), 'NOT_ADMITTED');
    await user.selectOptions(screen.getByLabelText('Ticket tier'), 't1');
    await user.type(screen.getByLabelText('Subject'), 'Gates open early');
    await user.type(screen.getByLabelText('Message'), 'Doors now open at 17:00, bring your ticket.');
    await user.click(screen.getByRole('button', { name: 'Send message' }));
    await waitFor(() => expect(send).toHaveBeenCalledWith({ subject: 'Gates open early', body: 'Doors now open at 17:00, bring your ticket.', segment: 'NOT_ADMITTED', ticketTierId: 't1' }));
    expect(await screen.findByText('Message sent to 12 people.')).toBeInTheDocument();
  });
  it('is read-only for roles that cannot message attendees', () => {
    wrap(<NotifyTab eventId="e1" tiers={[]} canNotify={false} />);
    expect(screen.getByText('Your role cannot message attendees.')).toBeInTheDocument();
  });
  it('shows an unavailable audience and the sent history', () => {
    audience = { count: null, error: new Error('x') };
    history = { messages: [{ id: 'm1', subject: 'Parking update', deliveredCount: 9, recipientCount: 10, status: 'SENT', createdAt: '2026-10-01T10:00:00Z' }], loading: false, error: null };
    wrap(<NotifyTab eventId="e1" tiers={[]} canNotify />);
    expect(screen.getByText(/Not available yet: the audience size/)).toBeInTheDocument();
    expect(screen.getByText('Parking update')).toBeInTheDocument();
    expect(screen.getByText('9 of 10')).toBeInTheDocument();
  });
});

describe('PurchaseHeat', () => {
  it('shows the empty state without purchases', () => {
    render(<PurchaseHeat cells={[]} />);
    expect(screen.getByText('No purchases yet')).toBeInTheDocument();
  });
  it('announces each cell and shades the busiest hour', () => {
    render(<PurchaseHeat cells={[{ dayOfWeek: 5, hour: 18, purchases: 4, tickets: 6, revenue: '600' } as never, { dayOfWeek: 1, hour: 9, purchases: 1, tickets: 1, revenue: '100' } as never]} />);
    expect(screen.getByRole('table', { name: 'Purchases by day and hour' })).toBeInTheDocument();
    const busiest = screen.getByLabelText('Friday 18:00, 4 purchases');
    expect(busiest).toHaveAttribute('data-level', '4');
    expect(screen.getByLabelText('Monday 09:00, 1 purchase')).toHaveAttribute('data-level', '1');
    expect(screen.getByLabelText('Tuesday 00:00, 0 purchases')).toHaveAttribute('data-level', '0');
    fireEvent.focus(busiest);
  });
});
