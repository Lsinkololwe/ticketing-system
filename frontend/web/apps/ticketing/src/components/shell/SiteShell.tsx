'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useMemo, type ReactNode } from 'react';
import { Icon, Menu, type MenuEntry } from '@pml.tickets/shared/components/m3';
import { PublicLayout, SiteFooter, SiteHeader, SiteTool } from '@pml.tickets/shared/layouts';
import { useBuyerAuth } from '@/lib/auth/session-context';
import { useUnreadCount } from '@pml.tickets/shared';
import { useHeldReservation } from '@/lib/hold';
import { useTheme } from '@/lib/theme';
import { initials, mmss } from '@/lib/format';
import { buildFooterColumns, SWITCH_APPS } from './footer';
import { useActiveEventCategories } from '@pml.tickets/shared';
import { useNow } from '@/lib/useNow';

function currentOf(path: string) {
  if (path === '/' || path.startsWith('/events')) return 'events';
  if (path.startsWith('/my-tickets')) return 'tickets';
  if (path.startsWith('/notifications')) return 'notifications';
  if (path.startsWith('/profile')) return 'profile';
  return '';
}

/** Buyer site frame: sticky header and footer around every public and signed-in page (the prototype has no phone bottom bar). */
export function SiteShell({ children, banner }: { children: ReactNode; banner?: ReactNode }) {
  const path = usePathname() ?? '/';
  const auth = useBuyerAuth();
  const { toggle } = useTheme();
  const unread = useUnreadCount(auth.authenticated);
  const hold = useHeldReservation();
  const now = useNow(1000, Boolean(hold));
  const { categories } = useActiveEventCategories();
  const footerColumns = useMemo(() => buildFooterColumns(categories.map((c) => c.name)), [categories]);
  const current = currentOf(path);
  const name = auth.user ? `${auth.user.givenName} ${auth.user.familyName}`.trim() : '';

  const account: MenuEntry[] = [
    { type: 'label', id: 'who', label: name || 'Your account' },
    { id: 'tickets', label: 'My tickets', icon: 'ticket', onSelect: () => window.location.assign('/my-tickets') },
    { id: 'notes', label: 'Notifications', icon: 'bell', onSelect: () => window.location.assign('/notifications') },
    { id: 'profile', label: 'Profile and settings', icon: 'settings', onSelect: () => window.location.assign('/profile') },
    { type: 'divider', id: 'd' },
    { id: 'out', label: 'Sign out', icon: 'logout', onSelect: () => void auth.logout() },
  ];
  const apps: MenuEntry[] = [
    { type: 'label', id: 'sw', label: 'Switch app' },
    { id: 'buyer', label: 'Buyer app', hint: 'Discover and buy tickets', icon: 'ticket', onSelect: () => window.location.assign('/') },
    ...SWITCH_APPS.map((a) => ({
      id: a.id,
      label: a.label,
      hint: a.hint,
      icon: a.icon,
      onSelect: () => window.location.assign(a.href),
    })),
  ];

  const tools = (
    <>
      {hold && !/\/book(\/|$)/.test(path) ? (
        <SiteTool label="Resume checkout" href={`/events/${hold.eventId}/book`} tone="accent" linkAs={Link}>
          <Icon name="clock" /> Checkout <b>{mmss(Date.parse(hold.expiresAt) - now)}</b>
        </SiteTool>
      ) : null}
      <SiteTool label={unread ? `Notifications, ${unread} unread` : 'Notifications'} href="/notifications" linkAs={Link}>
        <Icon name="bell" />
        {unread ? <span className="buyer-badge">{unread}</span> : null}
      </SiteTool>
      <SiteTool label="Switch light or dark theme" onClick={toggle}>
        <Icon name="sun" />
      </SiteTool>
      {SWITCH_APPS.length ? (
        <Menu
          label="Switch app"
          align="end"
          items={apps}
          trigger={(p) => (
            <button {...p} type="button" className="m3-site-tool m3-state" aria-label="Switch app">
              <Icon name="dashboard" />
              <span className="buyer-hide-s">Switch app</span>
            </button>
          )}
        />
      ) : null}
      {auth.authenticated ? (
        <Menu
          label="Account menu"
          align="end"
          items={account}
          trigger={(p) => (
            <button {...p} type="button" className="m3-site-tool m3-state" data-tone="accent" data-shape="round" aria-label="Account menu">
              <b>{initials(name || 'U')}</b>
            </button>
          )}
        />
      ) : (
        <SiteTool label="Sign in" href={`/auth?next=${encodeURIComponent(path)}`} tone="accent" linkAs={Link}>
          Sign in
        </SiteTool>
      )}
    </>
  );

  return (
    <>
      <PublicLayout
        banner={banner}
        header={
          <SiteHeader
            name="Showstop"
            linkAs={Link}
            links={[
              { id: 'events', label: 'Events', href: '/', current: current === 'events' },
              { id: 'tickets', label: 'My tickets', href: '/my-tickets', current: current === 'tickets' },
            ]}
            tools={tools}
          />
        }
        footer={<SiteFooter columns={footerColumns} linkAs={Link} legal="Tickets for concerts, theatre, comedy, sport and festivals across Zambia." />}
      >
        {children}
      </PublicLayout>
    </>
  );
}
