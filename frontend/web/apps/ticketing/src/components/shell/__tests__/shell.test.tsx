// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';

let authed = false;
let unread = 0;
let hold: { reservationId: string; eventId: string; expiresAt: string } | null = null;
let path = '/';
const toggle = vi.fn();
const logout = vi.fn();

vi.mock('next/navigation', () => ({ usePathname: () => path }));
vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useActiveEventCategories: () => ({ categories: [{ id: 'c1', name: 'Music' }, { id: 'c2', name: 'Comedy' }] }),
  useUnreadCount: () => unread,
}));
vi.mock('@/lib/auth/session-context', () => ({ useBuyerAuth: () => ({ authenticated: authed, user: authed ? { id: 'u', givenName: 'Chanda', familyName: 'Mwansa', email: '' } : null, logout }) }));
vi.mock('@/lib/hold', () => ({ useHeldReservation: () => hold }));
vi.mock('@/lib/theme', () => ({ useTheme: () => ({ theme: null, toggle }) }));

import { SiteShell } from '../SiteShell';
import { buildFooterColumns } from '../footer';

beforeEach(() => {
  authed = false;
  unread = 0;
  hold = null;
  path = '/';
  vi.clearAllMocks();
});

describe('SiteShell navigation', () => {
  it('links the header and footer to their routes', () => {
    render(<SiteShell>content</SiteShell>);
    const main = screen.getByRole('navigation', { name: 'Main' });
    expect(within(main).getByRole('link', { name: 'Events' })).toHaveAttribute('href', '/');
    expect(within(main).getByRole('link', { name: 'My tickets' })).toHaveAttribute('href', '/my-tickets');
    expect(screen.getByRole('link', { name: 'Showstop home' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/auth?next=%2F');
    expect(screen.getByRole('link', { name: 'Notifications' })).toHaveAttribute('href', '/notifications');
    expect(screen.queryByRole('navigation', { name: 'Primary' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Refund policies explained' })).toHaveAttribute('href', '/help#refund-policies');
    expect(screen.getByRole('link', { name: 'Music' })).toHaveAttribute('href', '/?category=Music#events');
    expect(screen.getByRole('link', { name: 'Terms of use' })).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', { name: 'Privacy policy' })).toHaveAttribute('href', '/privacy');
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#m3-main');
  });
  it('marks the current section', () => {
    path = '/my-tickets';
    render(<SiteShell>x</SiteShell>);
    const main = screen.getByRole('navigation', { name: 'Main' });
    expect(within(main).getByRole('link', { name: 'My tickets' })).toHaveAttribute('aria-current', 'page');
    expect(within(main).getByRole('link', { name: 'Events' })).not.toHaveAttribute('aria-current');
  });
  it('shows the unread count and toggles the theme', () => {
    unread = 3;
    render(<SiteShell>x</SiteShell>);
    expect(screen.getByRole('link', { name: 'Notifications, 3 unread' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Switch light or dark theme' }));
    expect(toggle).toHaveBeenCalled();
  });
  it('offers the account menu when signed in and signs out', () => {
    authed = true;
    render(<SiteShell>x</SiteShell>);
    expect(screen.queryByRole('link', { name: 'Sign in' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    const menu = screen.getByRole('menu');
    for (const item of ['My tickets', 'Notifications', 'Profile and settings', 'Sign out']) expect(within(menu).getByRole('menuitem', { name: item })).toBeInTheDocument();
    fireEvent.click(within(menu).getByRole('menuitem', { name: 'Sign out' }));
    expect(logout).toHaveBeenCalled();
  });
  it('offers to resume a held checkout with its countdown', () => {
    hold = { reservationId: 'r1', eventId: 'e9', expiresAt: new Date(Date.now() + 5 * 60_000).toISOString() };
    render(<SiteShell>x</SiteShell>);
    const chip = screen.getByRole('link', { name: 'Resume checkout' });
    expect(chip).toHaveAttribute('href', '/events/e9/book');
    expect(chip).toHaveTextContent(/Checkout\s*[45]:\d\d/);
  });
});

describe('footer columns', () => {
  it('omits Browse without categories and has no organizer column without a URL', () => {
    const cols = buildFooterColumns([]);
    expect(cols.map((c) => c.heading)).toEqual(['Showstop', 'Help']);
  });
});
