/**
 * Organizer console navigation. One source for the drawer, the phone bottom
 * bar and tests. Sections and labels match the design: Overview, Events,
 * Bookings, Media, (Onboarding while not active), Finance, Organization.
 */
import type { IconName } from '@pml.tickets/shared/components/m3';

export interface ConsoleNavEntry {
  id: string;
  label: string;
  href: string;
  icon: IconName;
  /** Path prefixes (besides href) that mark this entry current. */
  match?: string[];
  /** Only shown while the organization is not ACTIVE/APPROVED. */
  onboardingOnly?: boolean;
}
export interface ConsoleNavSection {
  id: string;
  label?: string;
  items: ConsoleNavEntry[];
}

export const NAV_SECTIONS: ConsoleNavSection[] = [
  {
    id: 'main',
    items: [
      { id: 'dashboard', label: 'Overview', href: '/dashboard', icon: 'dashboard' },
      { id: 'events', label: 'Events', href: '/events', icon: 'calendar' },
      { id: 'bookings', label: 'Bookings', href: '/bookings', icon: 'receipt' },
      { id: 'media', label: 'Media', href: '/media', icon: 'image' },
      { id: 'onboarding', label: 'Onboarding', href: '/apply/status', icon: 'file', onboardingOnly: true, match: ['/apply'] },
    ],
  },
  {
    id: 'finance',
    label: 'Finance',
    items: [
      { id: 'payouts', label: 'Escrow & payouts', href: '/finance', icon: 'wallet' },
      { id: 'banks', label: 'Bank accounts', href: '/finance/bank-accounts', icon: 'bank' },
      { id: 'transactions', label: 'Transactions', href: '/finance/transactions', icon: 'list' },
    ],
  },
  {
    id: 'organization',
    label: 'Organization',
    items: [
      { id: 'team', label: 'Team', href: '/team', icon: 'users' },
      { id: 'settings', label: 'Settings', href: '/settings', icon: 'settings' },
    ],
  },
];

/** True when `pathname` belongs to the entry (exact for /finance, prefix otherwise). */
export function isCurrent(entry: ConsoleNavEntry, pathname: string): boolean {
  if (entry.id === 'payouts') return pathname === '/finance' || pathname.startsWith('/finance/payouts');
  const bases = [entry.href, ...(entry.match ?? [])];
  return bases.some((b) => pathname === b || pathname.startsWith(`${b}/`));
}

/** The capabilities that gate navigation entries (a subset of OrgCapabilities). */
export interface NavAccess {
  canViewBookings: boolean;
  canManageMedia: boolean;
  canViewFinance: boolean;
  canViewTeam: boolean;
}
const NAV_NEEDS: Record<string, keyof NavAccess> = {
  bookings: 'canViewBookings',
  media: 'canManageMedia',
  payouts: 'canViewFinance',
  banks: 'canViewFinance',
  transactions: 'canViewFinance',
  team: 'canViewTeam',
};

/** Entries for the current organization state and, once the membership role is known, for that role. */
export function visibleSections(isActive: boolean, access?: NavAccess | null): ConsoleNavSection[] {
  return NAV_SECTIONS.map((s) => ({
    ...s,
    items: s.items.filter((i) => (!i.onboardingOnly || !isActive) && (!access || !NAV_NEEDS[i.id] || access[NAV_NEEDS[i.id]])),
  })).filter((s) => s.items.length > 0);
}
