import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MembersTab, type MembersTabProps } from './MembersTab';
import type { RosterMember } from '@/lib/api/team';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

const m = (o: Partial<RosterMember>): RosterMember => ({
  id: 'm1', userId: 'u1', role: 'OWNER', status: 'ACTIVE', joinedAt: '2025-03-10T00:00:00Z', lastActiveAt: null,
  customPermissions: null, deniedPermissions: null, user: { id: 'u1', fullName: 'Owner Fixture', username: 'owner' }, ...o,
});
const base = (o: Partial<MembersTabProps> = {}): MembersTabProps => ({
  members: [
    m({}),
    m({ id: 'm2', userId: 'u2', role: 'MANAGER', user: { id: 'u2', fullName: 'Manager Fixture', username: 'mgr' }, customPermissions: ['finance:read'] }),
    m({ id: 'm3', userId: 'u3', role: 'MARKETER', status: 'SUSPENDED', user: { id: 'u3', fullName: 'Suspended Fixture', username: null } }),
  ],
  currentUserId: 'u1',
  canManage: true,
  onChangeRole: vi.fn(),
  onSuspend: vi.fn(),
  onReactivate: vi.fn(),
  onRemove: vi.fn(),
  ...o,
});

describe('MembersTab', () => {
  it('lists members, marks you and custom permissions', () => {
    render(<MembersTab {...base()} />);
    expect(screen.getByText('You')).toBeInTheDocument();
    expect(screen.getByText('Custom')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Suspended')).toBeInTheDocument();
    expect(screen.getByText('Owner')).toBeInTheDocument();
  });
  it('changes role from the row menu', async () => {
    const p = base();
    render(<MembersTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Manager Fixture' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Change role' }));
    fireEvent.change(screen.getByLabelText('New role'), { target: { value: 'ADMIN' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save role' }));
    await waitFor(() => expect(p.onChangeRole).toHaveBeenCalledWith(expect.objectContaining({ id: 'm2' }), 'ADMIN', ['finance:read'], []));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Change role' })).toBeNull());
  });
  it('offers reactivate for suspended and suspend for active', () => {
    const p = base();
    render(<MembersTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Suspended Fixture' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Reactivate' }));
    expect(p.onReactivate).toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Manager Fixture' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Suspend' }));
    expect(p.onSuspend).toHaveBeenCalledWith(expect.objectContaining({ id: 'm2' }));
  });
  it('saves custom permissions as allow and deny lists', async () => {
    const p = base();
    render(<MembersTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Manager Fixture' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Custom permissions' }));
    fireEvent.change(screen.getByLabelText('Delete events'), { target: { value: 'deny' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(p.onChangeRole).toHaveBeenCalledWith(expect.objectContaining({ id: 'm2' }), 'MANAGER', ['finance:read'], ['event:delete']));
  });
  it('keeps the role dialog open and shows the server error, and guards a double submit', async () => {
    const p = base();
    let fail: (e: unknown) => void = () => undefined;
    (p.onChangeRole as ReturnType<typeof vi.fn>).mockImplementation(() => new Promise((_, rej) => (fail = rej)));
    render(<MembersTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Manager Fixture' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Change role' }));
    const save = screen.getByRole('button', { name: 'Save role' });
    fireEvent.click(save);
    fireEvent.click(save);
    await waitFor(() => expect(p.onChangeRole).toHaveBeenCalledTimes(1));
    fail({ errors: [{ message: 'x', extensions: { errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION', retryable: false } }] });
    await waitFor(() => expect(document.querySelector('[data-form-banner]')).not.toBeNull());
    expect(screen.getByRole('dialog', { name: 'Change role' })).toBeInTheDocument();
  });
  it('confirms removal', () => {
    const p = base();
    render(<MembersTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Manager Fixture' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Remove from team' }));
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Remove from team' }));
    expect(p.onRemove).toHaveBeenCalled();
  });
  it('has no actions when the viewer cannot manage', () => {
    render(<MembersTab {...base({ canManage: false })} />);
    expect(screen.queryByRole('button', { name: /Actions for/ })).toBeNull();
  });
  it('filters by role and shows the empty state', () => {
    render(<MembersTab {...base()} />);
    fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'CONTRIBUTOR' } });
    expect(screen.getByText('No members match.')).toBeInTheDocument();
  });
});
