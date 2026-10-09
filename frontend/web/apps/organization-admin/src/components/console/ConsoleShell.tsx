'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTheme } from 'next-themes';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { AppShell } from '@pml.tickets/shared/layouts';
import { Avatar, Banner, Button, DrawerCard, EmptyState, type NavItem, type NavSection } from '@pml.tickets/shared/components/m3';
import { signOut, useSession } from '@/lib/session';
import { useOrgContext } from '@/lib/api/org-context';
import { useOrganizationDeletion } from '@/lib/api/settings';
import { LinkBtn } from './LinkBtn';
import { isCurrent, visibleSections } from '@/config/navigation';

const ACTIVE = new Set(['ACTIVE', 'APPROVED']);

const STAGE_BANNER: Record<string, { tone: 'info' | 'warning' | 'error'; title: string; body: string; cta: string }> = {
  DRAFT: {
    tone: 'info',
    title: 'Finish your organizer application.',
    body: 'You can create draft events now. Publishing, inviting team members and requesting payouts unlock once you are approved.',
    cta: 'Continue application',
  },
  PENDING_REVIEW: {
    tone: 'info',
    title: 'Application under review.',
    body: 'Our team usually responds within 2 working days. You can keep creating draft events. Publishing, team invitations and payouts stay locked until approval.',
    cta: 'View status',
  },
  CHANGES_REQUESTED: {
    tone: 'warning',
    title: 'Changes requested.',
    body: 'The reviewer asked you to update your application. Fix the items and resubmit to continue.',
    cta: 'Update application',
  },
  REJECTED: {
    tone: 'error',
    title: 'Application rejected.',
    body: 'You can re-apply with corrected details.',
    cta: 'Re-apply',
  },
};

const ROLE_LABEL: Record<string, string> = { OWNER: 'Owner', ADMIN: 'Admin', MANAGER: 'Manager', MARKETER: 'Marketer', CONTRIBUTOR: 'Contributor' };

/** Shown while a deletion request is open; only the owner can cancel it. */
export function DeletionBanner({ scheduledFor, canCancel, onCancel }: { scheduledFor: string | null | undefined; canCancel: boolean; onCancel: () => void }) {
  if (!scheduledFor) return null;
  const on = new Date(scheduledFor).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  return (
    <Banner
      tone="warning"
      title="Organization scheduled for deletion."
      actions={canCancel ? <Button size="sm" variant="tonal" onClick={onCancel}>Cancel deletion</Button> : null}
    >
      It will be permanently removed on {on}. Events and payouts are frozen.
    </Banner>
  );
}

/** The banner that sits above every page while the organization is not active. */
export function StageBanner({ status }: { status: string | null | undefined }) {
  if (!status || ACTIVE.has(status)) return null;
  const b = STAGE_BANNER[status];
  if (!b) return null;
  return (
    <Banner
      tone={b.tone}
      title={b.title}
      actions={
        <LinkBtn href="/apply/status" size="sm" variant="tonal">
          {b.cta}
        </LinkBtn>
      }
    >
      {b.body}
    </Banner>
  );
}

/** Which capability a section of the console needs; a role without it sees a clear no-access state instead of the page. */
const SECTION_NEEDS: Array<[prefix: string, cap: 'canViewFinance' | 'canViewBookings' | 'canViewTeam']> = [
  ['/finance', 'canViewFinance'],
  ['/bookings', 'canViewBookings'],
  ['/team', 'canViewTeam'],
];

export function ConsoleShell({ children }: { children: ReactNode }) {
  const pathname = usePathname() ?? '';
  const { data: session } = useSession();
  const ctx = useOrgContext();
  const organization = ctx.organization;
  const deletion = useOrganizationDeletion();
  const { resolvedTheme, setTheme } = useTheme();
  // The server cannot know the system scheme: pick the icon only after mount so hydration matches.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const dark = mounted && resolvedTheme === 'dark';
  const status = organization?.status ?? null;
  const active = status ? ACTIVE.has(status) : false;
  const userName = session?.user?.name ?? 'Your account';

  const sections: NavSection[] = useMemo(
    () =>
      visibleSections(active, ctx.role ? ctx.capabilities : null).map((s) => ({
        id: s.id,
        label: s.label,
        items: s.items.map<NavItem>((i) => ({
          id: i.id,
          label: i.label,
          icon: i.icon,
          href: i.href,
          current: isCurrent(i, pathname),
        })),
      })),
    [active, pathname, ctx.role, ctx.capabilities]
  );
  const need = SECTION_NEEDS.find(([prefix]) => pathname === prefix || pathname.startsWith(`${prefix}/`));
  const blocked = Boolean(need && ctx.role && !ctx.capabilities[need[1]]);
  const flat = sections.flatMap((s) => s.items);
  const bottom = flat.filter((i) => ['dashboard', 'events', 'bookings', 'payouts', 'settings'].includes(i.id));

  return (
    <AppShell
      linkAs={Link}
      defaultCollapsed={false}
      drawer={{
        brand: 'MyTicketZM',
        label: 'Main navigation',
        linkAs: Link,
        header: (
          <DrawerCard
            kind="org"
            name={organization?.name ?? 'Your organization'}
            detail={`Organizer${status ? ` · ${status.charAt(0)}${status.slice(1).toLowerCase().replace(/_/g, ' ')}` : ''}`}
            avatar={<Avatar name={organization?.name ?? 'Organization'} size="sm" />}
          />
        ),
        primaryAction: { label: 'Create event', icon: 'add', href: '/events/new', linkAs: Link },
        sections,
        utility: [
          {
            id: 'theme',
            label: 'Light or dark',
            icon: dark ? 'sun' : 'moon',
            onSelect: () => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark'),
          },
          { id: 'signout', label: 'Sign out', icon: 'logout', onSelect: signOut },
        ],
        footer: (
          <DrawerCard kind="user" name={userName} detail={ctx.role ? ROLE_LABEL[ctx.role] ?? ctx.role : 'Member'} avatar={<Avatar name={userName} size="sm" tone="accent" />} />
        ),
      }}
      bottomNav={bottom}
    >
      <StageBanner status={status} />
      <DeletionBanner
        scheduledFor={ctx.organization?.deletionScheduledFor}
        canCancel={ctx.capabilities.isOwner}
        onCancel={() => ctx.organizationId && void deletion.cancelDeletion(ctx.organizationId)}
      />
      {blocked ? (
        <EmptyState icon="lock" title="You do not have access to this page" description="Your role in this organization does not include it. Ask an owner or admin if you need access." />
      ) : (
        children
      )}
    </AppShell>
  );
}

