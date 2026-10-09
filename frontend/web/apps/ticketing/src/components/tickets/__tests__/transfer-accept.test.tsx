// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

let state: { transfers: unknown[]; loading: boolean; error: unknown } = { transfers: [], loading: false, error: null };
const accept = vi.fn().mockResolvedValue({});
const decline = vi.fn().mockResolvedValue({});

vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useMyTicketTransfers: () => ({ ...state, refetch: vi.fn() }),
  useTicketTransferActions: () => ({ busy: false, accept, decline }),
}));

import { TransferAcceptClient } from '../TransferAcceptClient';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const offer = (o: Record<string, unknown> = {}) => ({ id: 'tr1', ticketNumber: 'TKT-7', eventTitle: 'Gift Fest', fromDisplayName: 'Chola M.', note: 'Enjoy!', expiresAt: '2026-12-01T10:00:00Z', status: 'PENDING', ...o });
const mount = () => render(<SnackbarProvider><TransferAcceptClient transferId="tr1" /></SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  state = { transfers: [offer()], loading: false, error: null };
});

describe('TransferAcceptClient', () => {
  it('shows the offer and accepts or declines it', async () => {
    mount();
    expect(screen.getByRole('heading', { name: 'Ticket transfer' })).toBeInTheDocument();
    expect(screen.getByText(/Chola M\. wants to send you this ticket/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Accept ticket' }));
    await waitFor(() => expect(accept).toHaveBeenCalledWith('tr1'));
    fireEvent.click(screen.getByRole('button', { name: 'Decline' }));
    await waitFor(() => expect(decline).toHaveBeenCalledWith('tr1'));
  });
  it('has loading, not found, already answered and error states', () => {
    state = { transfers: [], loading: true, error: null };
    const a = mount();
    expect(screen.getByLabelText('Loading the transfer')).toBeInTheDocument();
    a.unmount();
    state = { transfers: [], loading: false, error: null };
    const b = mount();
    expect(screen.getByText('We could not find that transfer')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to My tickets' })).toHaveAttribute('href', '/my-tickets');
    b.unmount();
    state = { transfers: [offer({ status: 'CANCELLED' })], loading: false, error: null };
    const c = mount();
    expect(screen.getByText('This transfer was cancelled')).toBeInTheDocument();
    c.unmount();
    state = { transfers: [], loading: false, error: { message: 'down' } };
    mount();
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});
