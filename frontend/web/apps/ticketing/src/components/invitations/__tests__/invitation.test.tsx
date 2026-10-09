// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

let authed = true;
let state: { invitation: unknown; loading: boolean; error: unknown } = { invitation: null, loading: false, error: null };
const accept = vi.fn();
const decline = vi.fn();

vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@/lib/auth/session-context', () => ({ useBuyerAuth: () => ({ authenticated: authed, user: null, logout: vi.fn() }) }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useInvitation: () => ({ ...state, refetch: vi.fn(), busy: false, accept, decline }),
}));

import { InvitationClient } from '../InvitationClient';

const INV = { organizationName: 'Fixture Org', organizationLogoUrl: null, proposedRole: 'CONTRIBUTOR', inviterDisplayName: 'Pat Fixture', expiresAt: new Date(Date.now() + 5 * 86_400_000).toISOString() };

beforeEach(() => {
  vi.clearAllMocks();
  authed = true;
  state = { invitation: INV, loading: false, error: null };
});

describe('InvitationClient', () => {
  it('previews the invitation and accepts it', async () => {
    accept.mockResolvedValue({});
    render(<InvitationClient token="tok" />);
    expect(screen.getByText('Fixture Org invited you to join their team')).toBeInTheDocument();
    expect(screen.getByText('Pat Fixture')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Accept invitation' }));
    expect(await screen.findByText("You've joined Fixture Org")).toBeInTheDocument();
  });
  it('declines after confirming', async () => {
    decline.mockResolvedValue({});
    render(<InvitationClient token="tok" />);
    fireEvent.click(screen.getByRole('button', { name: 'Decline' }));
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Decline invitation' }));
    await waitFor(() => expect(decline).toHaveBeenCalled());
    expect(await screen.findByText('Invitation declined')).toBeInTheDocument();
  });
  it('asks a signed-out visitor to sign in and return here', () => {
    authed = false;
    render(<InvitationClient token="tok" />);
    expect(screen.getByRole('link', { name: 'Sign in to accept' })).toHaveAttribute('href', '/auth?next=%2Finvitations%2Faccept%3Ftoken%3Dtok');
  });
  it('shows expired/withdrawn, incomplete and loading states', () => {
    state = { invitation: null, loading: false, error: null };
    const a = render(<InvitationClient token="tok" />);
    expect(screen.getByText('This invitation has expired or was withdrawn')).toBeInTheDocument();
    a.unmount();
    const b = render(<InvitationClient token="" />);
    expect(screen.getByText('This invitation link is incomplete')).toBeInTheDocument();
    b.unmount();
    state = { invitation: null, loading: true, error: null };
    render(<InvitationClient token="tok" />);
    expect(screen.getByLabelText('Loading invitation')).toBeInTheDocument();
  });
  it('shows an error from a failed accept', async () => {
    accept.mockRejectedValue({ message: 'nope', graphQLErrors: [{ message: 'nope' }] });
    render(<InvitationClient token="tok" />);
    fireEvent.click(screen.getByRole('button', { name: 'Accept invitation' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
