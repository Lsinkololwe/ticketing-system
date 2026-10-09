import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { OwnershipTab, type OwnershipTabProps } from './OwnershipTab';
import { RolesTab } from './RolesTab';
import type { RosterMember } from '@/lib/api/team';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

const admin: RosterMember = {
  id: 'm2', userId: 'u2', role: 'ADMIN', status: 'ACTIVE', joinedAt: null, lastActiveAt: null,
  customPermissions: null, deniedPermissions: null, user: { id: 'u2', fullName: 'Bwalya Fixture', username: null },
};
const base = (o: Partial<OwnershipTabProps> = {}): OwnershipTabProps => ({
  ownerName: 'Owner Fixture', orgName: 'Fixture Org', isOwner: true, admins: [admin], transfer: null,
  onStart: vi.fn(), onCancel: vi.fn(), onLeave: vi.fn(), ...o,
});

describe('OwnershipTab', () => {
  it('starts a transfer to the chosen admin', async () => {
    const p = base();
    render(<OwnershipTab {...p} />);
    expect(screen.getByText('The owner cannot leave. Transfer ownership first.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Start transfer' }));
    await waitFor(() => expect(p.onStart).toHaveBeenCalledWith('u2'));
  });
  it('shows a server refusal on the transfer form and ignores a double submit', async () => {
    let fail: (e: unknown) => void = () => undefined;
    const p = base({ onStart: vi.fn().mockImplementation(() => new Promise((_, rej) => (fail = rej))) });
    render(<OwnershipTab {...p} />);
    const go = screen.getByRole('button', { name: 'Start transfer' });
    fireEvent.click(go);
    fireEvent.click(go);
    await waitFor(() => expect(p.onStart).toHaveBeenCalledTimes(1));
    fail({ errors: [{ message: 'x', extensions: { errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION', retryable: false } }] });
    await waitFor(() => expect(document.querySelector('[data-form-banner]')).not.toBeNull());
  });
  it('asks to promote an admin first when there is none', () => {
    render(<OwnershipTab {...base({ admins: [] })} />);
    expect(screen.getByText(/Promote someone to ADMIN first/)).toBeInTheDocument();
  });
  it('shows and cancels a pending transfer', () => {
    const p = base({ transfer: { id: 't', newOwnerId: 'u2', newOwner: { id: 'u2', fullName: 'Bwalya Fixture' }, status: 'PENDING', expiresAt: '2026-10-07T00:00:00Z', initiatedAt: '2026-10-04T00:00:00Z' } });
    render(<OwnershipTab {...p} />);
    expect(screen.getByText('Ownership transfer pending')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel transfer' }));
    expect(p.onCancel).toHaveBeenCalled();
  });
  it('lets a non owner leave after confirmation', () => {
    const p = base({ isOwner: false });
    render(<OwnershipTab {...p} />);
    expect(screen.getByText('Only the owner can transfer ownership.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Leave organization' }));
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Leave organization' }));
    expect(p.onLeave).toHaveBeenCalled();
  });
});

describe('RolesTab', () => {
  it('renders the read-only matrices', () => {
    render(<RolesTab />);
    const t = screen.getByRole('table', { name: 'Organization roles' });
    expect(within(t).getByRole('columnheader', { name: 'CONTRIBUTOR' })).toBeInTheDocument();
    expect(within(t).getByText('organization:billing')).toBeInTheDocument();
    expect(within(t).getAllByLabelText('Allowed when managersCanViewFinancials is on')).toHaveLength(1);
    expect(screen.getByRole('table', { name: 'Event roles' })).toBeInTheDocument();
    expect(screen.queryByRole('button')).toBeNull();
  });
});
