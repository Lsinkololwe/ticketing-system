import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { IncomingTransferCard } from './IncomingTransferCard';
import { AccessGrants } from './AccessGrants';

const transfer = { id: 'tr1', status: 'PENDING', expiresAt: '2026-11-01T00:00:00Z', reason: null, organization: { id: 'o1', name: 'Fixture Org' }, currentOwner: { id: 'u1', fullName: 'Ann Owner' } };

describe('IncomingTransferCard', () => {
  it('renders nothing without an offer', () => {
    const { container } = render(<IncomingTransferCard transfers={[]} onRequestCode={vi.fn()} onAccept={vi.fn()} onDecline={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });
  it('asks for a code, validates it and accepts', async () => {
    const user = userEvent.setup();
    const onAccept = vi.fn().mockResolvedValue(undefined);
    const onRequestCode = vi.fn().mockResolvedValue(undefined);
    render(<IncomingTransferCard transfers={[transfer as never]} onRequestCode={onRequestCode} onAccept={onAccept} onDecline={vi.fn()} />);
    expect(screen.getByText(/Ann Owner wants you to own Fixture Org/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Transfer token'), 'tok-12345678');
    fireEvent.click(screen.getByRole('button', { name: 'Send me a code' }));
    expect(onRequestCode).toHaveBeenCalledWith('tok-12345678');
    await user.type(screen.getByLabelText('One-time code'), '12');
    await user.click(screen.getByRole('button', { name: 'Accept ownership' }));
    expect(await screen.findByText('Enter the 6-digit code')).toBeInTheDocument();
    await user.clear(screen.getByLabelText('One-time code'));
    await user.type(screen.getByLabelText('One-time code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Accept ownership' }));
    await waitFor(() => expect(onAccept).toHaveBeenCalledWith('tok-12345678', '123456'));
  });
  it('declines with the token', async () => {
    const user = userEvent.setup();
    const onDecline = vi.fn();
    render(<IncomingTransferCard transfers={[transfer as never]} onRequestCode={vi.fn()} onAccept={vi.fn()} onDecline={onDecline} />);
    await user.type(screen.getByLabelText('Transfer token'), 'tok-12345678');
    await user.click(screen.getByRole('button', { name: 'Decline' }));
    expect(onDecline).toHaveBeenCalledWith('tok-12345678');
  });
});

describe('AccessGrants (organization-wide)', () => {
  const grants = [
    { id: 'g1', userId: 'u2', user: { id: 'u2', fullName: 'Bo Member' }, eventId: 'e1', eventRole: 'CHECK_IN', reason: 'Gate steward', status: 'ACTIVE', expiresAt: null },
    { id: 'g2', userId: 'u3', user: { id: 'u3', fullName: 'Cy Member' }, eventId: 'e2', eventRole: 'EDITOR', reason: null, status: 'ACTIVE', expiresAt: null },
  ];
  const events = [{ id: 'e1', title: 'Fixture Fest' }, { id: 'e2', title: 'Other Fest' }];
  const base = { grants: grants as never, members: [] as never, events, canGrant: true, onGrant: vi.fn(), onUpdate: vi.fn(), onRevoke: vi.fn() };
  it('lists grants across events with the Event column, reason and filters', () => {
    render(<AccessGrants {...base} />);
    expect(screen.getByText('Bo Member')).toBeInTheDocument();
    expect(screen.getByText('Gate steward')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Event'), { target: { value: 'e2' } });
    expect(screen.queryByText('Bo Member')).toBeNull();
    expect(screen.getByText('Cy Member')).toBeInTheDocument();
  });
  it('revokes only after confirming', async () => {
    const onRevoke = vi.fn();
    render(<AccessGrants {...base} onRevoke={onRevoke} />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Revoke' })[0]);
    expect(onRevoke).not.toHaveBeenCalled();
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Revoke' }));
    expect(onRevoke).toHaveBeenCalledWith(expect.objectContaining({ id: 'g1' }));
  });
  it('shows the empty state and hides actions without the right', () => {
    render(<AccessGrants {...base} grants={[]} canGrant={false} />);
    expect(screen.getByText('No grants for this event yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Grant access' })).toBeNull();
  });
});
