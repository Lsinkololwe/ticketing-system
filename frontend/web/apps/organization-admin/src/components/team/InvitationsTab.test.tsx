import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { InvitationsTab, parseBulkInvites, type InvitationsTabProps } from './InvitationsTab';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

const inv = (o: Record<string, unknown>) => ({
  id: 'i1', email: 'kelvin@example.com', phoneNumber: null, inviteeName: 'Kelvin', proposedRole: 'CONTRIBUTOR',
  eventAccessGrants: [{ eventId: 'e1', role: 'CHECK_IN', expiresAt: null }], message: null, expiresAt: '2026-10-06T00:00:00Z',
  status: 'PENDING', createdAt: '2026-09-29T00:00:00Z', ...o,
});
const base = (o: Partial<InvitationsTabProps> = {}): InvitationsTabProps => ({
  invitations: [inv({}) as never, inv({ id: 'i2', status: 'ACCEPTED', inviteeName: null, email: 'acc@example.com', eventAccessGrants: null }) as never],
  events: [{ id: 'e1', title: 'Fixture Fest' }],
  orgActive: true,
  canInvite: true,
  onInvite: vi.fn(async () => true),
  onResend: vi.fn(),
  onRevoke: vi.fn(),
  ...o,
});

describe('parseBulkInvites', () => {
  it('reads name and email and flags bad lines', () => {
    const r = parseBulkInvites('Chanda Mwansa, Chanda@Example.com\n\nnot valid\nsolo@example.com');
    expect(r.people).toEqual([
      { email: 'chanda@example.com', inviteeName: 'Chanda Mwansa', phoneNumber: null },
      { email: 'solo@example.com', inviteeName: null, phoneNumber: null },
    ]);
    expect(r.bad).toEqual(['not valid']);
  });
});

describe('InvitationsTab', () => {
  it('shows invitations with event access and row actions', () => {
    const p = base();
    render(<InvitationsTab {...p} />);
    expect(screen.getByText('Fixture Fest · Check in')).toBeInTheDocument();
    const row = screen.getByRole('row', { name: /Kelvin/ });
    fireEvent.click(within(row).getByRole('button', { name: 'Resend' }));
    expect(p.onResend).toHaveBeenCalledWith('i1');
    fireEvent.click(within(row).getByRole('button', { name: 'Revoke' }));
    expect(p.onRevoke).toHaveBeenCalledWith('i1');
  });
  it('validates, focuses the first error and sends a single invitation with event grants', async () => {
    const user = userEvent.setup();
    const p = base();
    render(<InvitationsTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Invite one person' }));
    await user.click(screen.getByRole('button', { name: 'Send invitation' }));
    expect((await screen.findAllByText('Required')).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.getByLabelText('Name')).toHaveFocus());
    expect(screen.getByText('Enter an email address or a WhatsApp number')).toBeInTheDocument();
    expect(p.onInvite).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Name'), 'Chanda');
    await user.type(screen.getByLabelText('Email'), 'nope');
    await user.click(screen.getByRole('button', { name: 'Send invitation' }));
    expect((await screen.findAllByText('Enter a valid email address')).length).toBeGreaterThan(0);
    await user.clear(screen.getByLabelText('Email'));
    await user.type(screen.getByLabelText('Email'), 'Chanda@Example.com');
    await user.click(screen.getByLabelText('Fixture Fest'));
    await user.click(screen.getByRole('button', { name: 'Send invitation' }));
    await waitFor(() =>
      expect(p.onInvite).toHaveBeenCalledWith({
        people: [{ email: 'chanda@example.com', inviteeName: 'Chanda', phoneNumber: null }],
        // nothing chosen: the platform's least-privileged listed roles
        role: 'CONTRIBUTOR',
        message: null,
        eventAccessGrants: [{ eventId: 'e1', role: 'VIEWER' }],
      })
    );
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Invite a team member' })).toBeNull());
  });
  it('invites by WhatsApp number alone', async () => {
    const user = userEvent.setup();
    const p = base();
    render(<InvitationsTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Invite one person' }));
    await user.type(screen.getByLabelText('Name'), 'Mulenga');
    await user.type(screen.getByLabelText('WhatsApp number'), '0971234567');
    await user.click(screen.getByRole('button', { name: 'Send invitation' }));
    await waitFor(() =>
      expect(p.onInvite).toHaveBeenCalledWith(expect.objectContaining({ people: [{ email: null, inviteeName: 'Mulenga', phoneNumber: '+260971234567' }] }))
    );
  });
  it('rejects bad bulk lines and sends good ones', async () => {
    const user = userEvent.setup();
    const p = base();
    render(<InvitationsTab {...p} />);
    fireEvent.click(screen.getByRole('button', { name: 'Invite several' }));
    await user.click(screen.getByRole('button', { name: 'Send invitations' }));
    expect((await screen.findAllByText('Add at least one person')).length).toBeGreaterThan(0);
    await user.type(screen.getByLabelText('People, one per line'), 'nobody');
    await user.click(screen.getByRole('button', { name: 'Send invitations' }));
    expect((await screen.findAllByText(/These lines need a valid email: nobody/)).length).toBeGreaterThan(0);
    await user.clear(screen.getByLabelText('People, one per line'));
    await user.type(screen.getByLabelText('People, one per line'), 'A B, a@example.com');
    await user.click(screen.getByRole('button', { name: 'Send invitations' }));
    await waitFor(() =>
      expect(p.onInvite).toHaveBeenCalledWith(expect.objectContaining({ role: 'CONTRIBUTOR', people: [{ email: 'a@example.com', inviteeName: 'A B', phoneNumber: null }] }))
    );
  });
  it('maps a server error onto the email field and ignores a double submit', async () => {
    const user = userEvent.setup();
    let reject: (e: unknown) => void = () => undefined;
    const onInvite = vi.fn().mockImplementation(() => new Promise((_, rej) => (reject = rej)));
    render(<InvitationsTab {...base({ onInvite })} />);
    fireEvent.click(screen.getByRole('button', { name: 'Invite one person' }));
    await user.type(screen.getByLabelText('Name'), 'Chanda');
    await user.type(screen.getByLabelText('Email'), 'chanda@example.com');
    const send = screen.getByRole('button', { name: 'Send invitation' });
    await user.click(send);
    await user.click(send);
    expect(onInvite).toHaveBeenCalledTimes(1);
    reject({ errors: [{ message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.email', constraint: 'Email' }] } }] });
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByRole('dialog', { name: 'Invite a team member' })).toBeInTheDocument();
  });
  it('is locked until the organization is approved', () => {
    render(<InvitationsTab {...base({ orgActive: false })} />);
    expect(screen.getByText(/unlock once your organization is approved/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Invite one person' })).toBeNull();
  });
  it('shows the empty state', () => {
    render(<InvitationsTab {...base({ invitations: [] })} />);
    expect(screen.getByText('No invitations. Invite someone to get started.')).toBeInTheDocument();
  });
});
