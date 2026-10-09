'use client';

/**
 * Console frame: dark navigation drawer (collapses to a rail), pending-work
 * badges, the "Switch app" and account menus. Rendered by the (console) layout
 * only after the server-side role guard passed.
 */
import { useEffect, useState, type ReactNode } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { signOut } from '@/components/auth/SignOutForm';
import { useTheme } from 'next-themes';
import { Avatar, DrawerCard, Icon, Menu, type MenuEntry, type NavItem, type NavSection } from '@pml.tickets/shared/components/m3';
import { AppShell } from '@pml.tickets/shared/layouts';
import { usePendingCounts } from '@pml.tickets/shared';
import { useStuckTransactions } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { useSystemAlerts } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { MODULES, RAIL_LABELS, canOpenModule, ROLE_LABELS, modulesFor, moduleOfPath, type ModuleId } from '@/config/navigation';
import { hrefOf, pendingFor, primaryRole } from '@/lib/pending';
import { CommandPalette } from './CommandPalette';
import { ConsoleUiProvider } from './ConsoleUi';
import { useStaff } from './StaffContext';

const BUYER_URL = process.env.NEXT_PUBLIC_BUYER_APP_URL ?? 'http://localhost:3000';
const ORGANIZER_URL = process.env.NEXT_PUBLIC_ORGANIZER_APP_URL ?? 'http://localhost:3003';

export function buildSections(
  roles: Parameters<typeof modulesFor>[0],
  current: ModuleId | null,
  badges: Partial<Record<ModuleId, number>>
): NavSection[] {
  const groups = new Map<string, NavItem[]>();
  for (const m of modulesFor(roles)) {
    const list = groups.get(m.group) ?? [];
    const first = m.tabs ? `${m.path}/${m.tabs[0].id}` : m.path;
    list.push({ id: m.id, label: m.label, icon: m.icon, href: first, current: m.id === current, badge: badges[m.id] });
    groups.set(m.group, list);
  }
  return [...groups.entries()].map(([label, items]) => ({ id: label.toLowerCase(), label, items }));
}

/** Renders nothing; reports a pending count for a nav badge (only mounted for roles that may open the module). */
function StuckProbe({ onCount }: { onCount: (n: number) => void }) {
  const { pageInfo, error } = useStuckTransactions({ size: 1 });
  const n = error ? 0 : pageInfo.totalCount;
  useEffect(() => onCount(n), [n, onCount]);
  return null;
}
function AlertProbe({ onCount }: { onCount: (n: number) => void }) {
  const { alerts, error } = useSystemAlerts({ status: 'OPEN' });
  const n = error ? 0 : alerts.length;
  useEffect(() => onCount(n), [n, onCount]);
  return null;
}

export function ConsoleShell({ children }: { children: ReactNode }) {
  const staff = useStaff();
  const pathname = usePathname() ?? '/';
  const router = useRouter();
  const { counts } = usePendingCounts();
  const current = moduleOfPath(pathname);
  const [stuck, setStuck] = useState(0);
  const [openAlerts, setOpenAlerts] = useState(0);

  const badges: Partial<Record<ModuleId, number>> = {
    approvals: counts['pending-approvals'],
    finance: counts['payout-requests'] + counts['refund-requests'],
    transactions: stuck || undefined,
    health: openAlerts || undefined,
  };
  const sections = buildSections(staff.roles, current, badges);
  const top = staff.roles[0];
  const [paletteOpen, setPaletteOpen] = useState(false);
  const { resolvedTheme, setTheme } = useTheme();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setPaletteOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const pending = pendingFor(staff.roles, counts);
  const pendingTotal = pending.reduce((a, x) => a + x.count, 0);
  const role = primaryRole(staff.roles);
  const bellItems: MenuEntry[] = [
    { type: 'label', id: 'head', label: `Pending work for ${role ? ROLE_LABELS[role].toLowerCase() : 'staff'}` },
    ...(pending.length
      ? pending.map((x): MenuEntry => ({
          id: `${x.module}-${x.tab}`,
          label: x.label,
          hint: String(x.count),
          onSelect: () => router.push(hrefOf(x.module, x.tab)),
        }))
      : [{ type: 'label', id: 'clear', label: 'You are all caught up.' } as MenuEntry]),
  ];

  const bottom: NavItem[] = [
    ...sections.flatMap((x) => x.items).map((i) => ({ ...i, label: RAIL_LABELS[i.id as ModuleId] ?? i.label, badge: i.badge })),
    { id: 'buyer', label: 'Buyer', icon: 'swap', onSelect: () => window.location.assign(BUYER_URL) },
    { id: 'organizer', label: 'Organizer', icon: 'dashboard', onSelect: () => window.location.assign(ORGANIZER_URL) },
    { id: 'theme', label: 'Theme', icon: resolvedTheme === 'dark' ? 'sun' : 'moon', onSelect: () => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark') },
  ];

  return (
    <ConsoleUiProvider value={{ openPalette: () => setPaletteOpen(true), bellItems, pendingTotal, signOut, openProfile: () => router.push('/profile') }}>
      <AppShell
        linkAs={Link}
        bottomNav={bottom}
        drawer={{
          brand: 'MyTicketZM',
          sections,
          header: (
            <DrawerCard kind="org" name="Platform admin" detail={staff.roles.map((r) => ROLE_LABELS[r]).join(', ')} avatar={<Avatar name="Platform admin" size="sm" />} />
          ),
          utility: [],
          footer: (
            <>
              <div className="m3-drawer__utility">
                <Menu
                  align="start"
                  label="Switch app"
                  items={[
                    { id: 'buyer', label: 'Buyer app', onSelect: () => window.location.assign(BUYER_URL) },
                    { id: 'organizer', label: 'Organizer portal', onSelect: () => window.location.assign(ORGANIZER_URL) },
                  ]}
                  trigger={({ ref, ...p }) => (
                    <button ref={ref} type="button" className="m3-navitem m3-state" {...p}>
                      <Icon name="swap" />
                      <span>Switch app</span>
                    </button>
                  )}
                />
                <button type="button" className="m3-navitem m3-state" onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}>
                  <Icon name={resolvedTheme === 'dark' ? 'sun' : 'moon'} />
                  <span>Light or dark</span>
                </button>
              </div>
              <Menu
                align="start"
                label="Account menu"
                items={[
                  { id: 'profile', label: 'My profile', icon: 'user', onSelect: () => router.push('/profile') },
                  { id: 'signout', label: 'Sign out', icon: 'logout', danger: true, onSelect: signOut },
                ]}
                trigger={({ ref, ...p }) => (
                  <button ref={ref} type="button" className="m3-drawer__card" data-kind="user" aria-label="Account menu" {...p}>
                    <Avatar name={staff.name} size="sm" tone="accent" />
                    <span className="m3-drawer__card-text">
                      <b>{staff.name}</b>
                      <small>{top ? ROLE_LABELS[top] : 'Staff'}</small>
                    </span>
                    <Icon name="more-vert" />
                  </button>
                )}
              />
            </>
          ),
        }}
      >
        {children}
        {canOpenModule(staff.roles, 'transactions') ? <StuckProbe onCount={setStuck} /> : null}
        {canOpenModule(staff.roles, 'health') ? <AlertProbe onCount={setOpenAlerts} /> : null}
        <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} roles={staff.roles} />
      </AppShell>
    </ConsoleUiProvider>
  );
}

export const ALL_MODULE_IDS = MODULES.map((m) => m.id);
