import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AccessGrants, type AccessGrantsProps } from './AccessGrants';
import type { AccessGrantRow, RosterMember } from '@/lib/api/team';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

const member = (id: string, name: string): RosterMember => ({
  id: `m-${id}`, userId: id, role: 'MANAGER', status: 'ACTIVE', joinedAt: null, lastActiveAt: null,
  customPermissions: null, deniedPermissions: null, user: { id, fullName: name, username: null },
});
const grant = (o: Partial<AccessGrantRow>): AccessGrantRow => ({
  id: 'g1', userId: 'u1', user: { id: 'u1', fullName: 'Mwila Fixture' }, eventId: 'e1', eventRole: 'CHECK_IN',
  reason: 'Gate steward', status: 'ACTIVE', expiresAt: null, ...o,
});
const base = (o: Partial<AccessGrantsProps> = {}): AccessGrantsProps => ({
  grants: [grant({})],
  members: [member('u1', 'Mwila Fixture'), member('u2', 'Kunda Fixture')],
  canGrant: true,
  onGrant: vi.fn(),
  onUpdate: vi.fn(),
  onRevoke: vi.fn(),
  ...o,
});

describe('AccessGrants', () => {
  it('lists grants and revokes through the action button', () => {
    const p = base();
    render(<AccessGrants {...p} />);
    expect(screen.getByText('Gate steward')).toBeInTheDocument();
    expect(screen.getByText('No expiry')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Revoke' }));
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Revoke' }));
    expect(p.onRevoke).toHaveBeenCalledWith(expect.objectContaining({ id: 'g1' }));
  });
  it('validates, focuses the first error, then grants access', async () => {
    const user = userEvent.setup();
    const p = base({ onGrant: vi.fn().mockResolvedValue(undefined) });
    render(<AccessGrants {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Grant access' }));
    const dlg = screen.getByRole('dialog', { name: 'Grant event access' });
    await user.click(within(dlg).getByRole('button', { name: 'Grant access' }));
    expect((await within(dlg).findAllByText('Choose a team member')).length).toBeGreaterThan(0);
    await waitFor(() => expect(within(dlg).getByLabelText('Team member')).toHaveFocus());
    expect(within(dlg).getAllByText('Required').length).toBeGreaterThan(0);
    await user.selectOptions(within(dlg).getByLabelText('Team member'), 'u1');
    await user.type(within(dlg).getByLabelText('Reason'), 'Promo copy');
    await user.click(within(dlg).getByRole('button', { name: 'Grant access' }));
    expect((await within(dlg).findAllByText('This member already has access to the event')).length).toBeGreaterThan(0);
    await user.selectOptions(within(dlg).getByLabelText('Team member'), 'u2');
    await user.click(within(dlg).getByRole('button', { name: 'Grant access' }));
    await waitFor(() => expect(p.onGrant).toHaveBeenCalledWith({ userId: 'u2', role: 'VIEWER', expiresAt: null, reason: 'Promo copy' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Grant event access' })).toBeNull());
  });
  it('rejects a past expiry date', async () => {
    const user = userEvent.setup();
    const p = base();
    render(<AccessGrants {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Grant access' }));
    const dlg = screen.getByRole('dialog', { name: 'Grant event access' });
    await user.selectOptions(within(dlg).getByLabelText('Team member'), 'u2');
    await user.type(within(dlg).getByLabelText('Reason'), 'Promo copy');
    fireEvent.change(within(dlg).getByLabelText('Expires (optional)'), { target: { value: '2020-01-01' } });
    await user.click(within(dlg).getByRole('button', { name: 'Grant access' }));
    expect((await within(dlg).findAllByText('Pick a future expiry date')).length).toBeGreaterThan(0);
    expect(p.onGrant).not.toHaveBeenCalled();
  });
  it('maps a server field error onto the form and ignores a double submit', async () => {
    const user = userEvent.setup();
    let reject: (e: unknown) => void = () => undefined;
    const onGrant = vi.fn().mockImplementation(() => new Promise((_, rej) => (reject = rej)));
    render(<AccessGrants {...base({ onGrant })} />);
    fireEvent.click(screen.getByRole('button', { name: 'Grant access' }));
    const dlg = screen.getByRole('dialog', { name: 'Grant event access' });
    await user.selectOptions(within(dlg).getByLabelText('Team member'), 'u2');
    await user.type(within(dlg).getByLabelText('Reason'), 'Promo copy');
    const submit = within(dlg).getByRole('button', { name: 'Grant access' });
    await user.click(submit);
    await user.click(submit);
    expect(onGrant).toHaveBeenCalledTimes(1);
    reject({ errors: [{ message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.reason', constraint: 'NotBlank' }] } }] });
    await waitFor(() => expect(within(dlg).getByLabelText('Reason')).toHaveAttribute('aria-invalid', 'true'));
  });
  it('hides actions for viewers and shows the empty state', () => {
    const { rerender } = render(<AccessGrants {...base({ canGrant: false })} />);
    expect(screen.queryByRole('button', { name: 'Grant access' })).toBeNull();
    rerender(<AccessGrants {...base({ grants: [] })} />);
    expect(screen.getByText('No grants for this event yet.')).toBeInTheDocument();
  });
});
