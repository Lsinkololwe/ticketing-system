/**
 * Organization Portal Navigation Configuration
 *
 * Role-based navigation for the organization admin portal.
 * Implements permission matrix from system architecture.
 *
 * Role Permissions:
 * | Capability     | OWNER | ADMIN | MANAGER | MARKETER | CONTRIBUTOR |
 * |----------------|-------|-------|---------|----------|-------------|
 * | Team Management| yes   | yes   | -       | -        | -           |
 * | Create Events  | yes   | yes   | yes     | -        | -           |
 * | Publish Events | yes   | yes   | yes     | -        | -           |
 * | View Financials| yes   | yes   | yes     | -        | -           |
 * | Request Payouts| yes   | yes   | -       | -        | -           |
 * | Analytics      | yes   | yes   | yes     | yes      | -           |
 * | Check-in       | yes   | yes   | yes     | yes      | yes         |
 */

// =============================================================================
// TYPES
// =============================================================================

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MARKETER' | 'CONTRIBUTOR';

export interface NavItem {
  id: string;
  label: string;
  href: string;
  icon: string;
  badge?: number | 'dynamic';
  roles?: OrganizationRole[]; // If undefined, visible to all roles
}

export interface NavSection {
  id: string;
  title: string;
  items: NavItem[];
  roles?: OrganizationRole[]; // If undefined, visible to all roles
}

// =============================================================================
// NAVIGATION CONFIGURATION
// =============================================================================

export const navigation: NavSection[] = [
  // Overview Section - All roles
  {
    id: 'overview',
    title: 'Overview',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        href: '/dashboard',
        icon: 'HomeSimple',
      },
    ],
  },

  // Events Section - OWNER, ADMIN, MANAGER can manage; MARKETER, CONTRIBUTOR can view
  {
    id: 'events',
    title: 'Events',
    items: [
      {
        id: 'events-list',
        label: 'All Events',
        href: '/events',
        icon: 'Calendar',
      },
      {
        id: 'events-create',
        label: 'Create Event',
        href: '/events/new',
        icon: 'CalendarPlus',
        roles: ['OWNER', 'ADMIN', 'MANAGER'],
      },
      {
        id: 'events-drafts',
        label: 'Drafts',
        href: '/events?status=draft',
        icon: 'PageEdit',
        badge: 'dynamic',
        roles: ['OWNER', 'ADMIN', 'MANAGER'],
      },
    ],
  },

  // Check-in Section - All roles
  {
    id: 'checkin',
    title: 'Check-in',
    items: [
      {
        id: 'scanner',
        label: 'Ticket Scanner',
        href: '/check-in',
        icon: 'ScanQrCode',
      },
      {
        id: 'attendees',
        label: 'Attendee List',
        href: '/check-in/attendees',
        icon: 'Group',
      },
    ],
  },

  // Analytics Section - OWNER, ADMIN, MANAGER, MARKETER
  {
    id: 'analytics',
    title: 'Analytics',
    roles: ['OWNER', 'ADMIN', 'MANAGER', 'MARKETER'],
    items: [
      {
        id: 'analytics-overview',
        label: 'Overview',
        href: '/analytics',
        icon: 'StatsReport',
      },
      {
        id: 'analytics-sales',
        label: 'Sales Reports',
        href: '/analytics/sales',
        icon: 'GraphUp',
      },
      {
        id: 'analytics-attendance',
        label: 'Attendance',
        href: '/analytics/attendance',
        icon: 'StatsUpSquare',
      },
    ],
  },

  // Finance Section - OWNER, ADMIN, MANAGER
  {
    id: 'finance',
    title: 'Finance',
    roles: ['OWNER', 'ADMIN', 'MANAGER'],
    items: [
      {
        id: 'finance-overview',
        label: 'Overview',
        href: '/finance',
        icon: 'Safe',
      },
      {
        id: 'finance-payouts',
        label: 'Payouts',
        href: '/finance/payouts',
        icon: 'SendDiagonal',
        badge: 'dynamic',
        roles: ['OWNER', 'ADMIN'],
      },
      {
        id: 'finance-bank-accounts',
        label: 'Bank Accounts',
        href: '/finance/bank-accounts',
        icon: 'CreditCard',
        roles: ['OWNER', 'ADMIN'],
      },
      {
        id: 'finance-transactions',
        label: 'Transactions',
        href: '/finance/transactions',
        icon: 'List',
      },
    ],
  },

  // Team Section - OWNER, ADMIN only
  {
    id: 'team',
    title: 'Team',
    roles: ['OWNER', 'ADMIN'],
    items: [
      {
        id: 'team-members',
        label: 'Members',
        href: '/team',
        icon: 'Community',
      },
      {
        id: 'team-invite',
        label: 'Invite Member',
        href: '/team/invite',
        icon: 'UserPlus',
      },
      {
        id: 'team-roles',
        label: 'Roles & Permissions',
        href: '/team/roles',
        icon: 'Key',
      },
    ],
  },

  // Settings Section - Access varies by role
  {
    id: 'settings',
    title: 'Settings',
    items: [
      {
        id: 'settings-organization',
        label: 'Organization',
        href: '/settings',
        icon: 'Building',
        roles: ['OWNER', 'ADMIN'],
      },
      {
        id: 'settings-profile',
        label: 'My Profile',
        href: '/settings/profile',
        icon: 'User',
      },
      {
        id: 'settings-notifications',
        label: 'Notifications',
        href: '/settings/notifications',
        icon: 'Bell',
      },
    ],
  },
];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Filter navigation based on user role
 *
 * @param role - User's organization role
 * @returns Filtered navigation sections
 */
export function getNavigationForRole(role: OrganizationRole): NavSection[] {
  return navigation
    .filter((section) => {
      // Section-level role check
      if (section.roles && !section.roles.includes(role)) {
        return false;
      }
      return true;
    })
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => {
        // Item-level role check
        if (item.roles && !item.roles.includes(role)) {
          return false;
        }
        return true;
      }),
    }))
    // Remove sections with no visible items
    .filter((section) => section.items.length > 0);
}

/**
 * Check if a nav item is active based on pathname
 *
 * @param itemHref - The nav item's href
 * @param pathname - Current pathname
 * @returns true if the item should be marked as active
 */
export function isNavItemActive(itemHref: string, pathname: string): boolean {
  // Exact match for root paths
  if (itemHref === '/dashboard' && pathname === '/dashboard') {
    return true;
  }

  // Check if pathname starts with itemHref (for nested routes)
  if (itemHref !== '/dashboard' && pathname.startsWith(itemHref)) {
    // Make sure it's not a partial match (e.g., /events shouldn't match /events-other)
    const nextChar = pathname[itemHref.length];
    return !nextChar || nextChar === '/' || nextChar === '?';
  }

  return false;
}

/**
 * Get breadcrumb trail for a pathname
 *
 * @param pathname - Current pathname
 * @returns Array of breadcrumb items
 */
export function getBreadcrumbs(pathname: string): { label: string; href: string }[] {
  const breadcrumbs: { label: string; href: string }[] = [];

  // Find matching nav items
  for (const section of navigation) {
    for (const item of section.items) {
      if (isNavItemActive(item.href, pathname)) {
        // Add section as parent if not Overview
        if (section.id !== 'overview') {
          breadcrumbs.push({
            label: section.title,
            href: section.items[0]?.href || '#',
          });
        }
        breadcrumbs.push({
          label: item.label,
          href: item.href,
        });
        break;
      }
    }
  }

  return breadcrumbs;
}

// =============================================================================
// ICON MAP
// =============================================================================

/**
 * Map of icon names to their display names for reference
 * Actual icons are imported from iconoir-react in the Sidebar component
 */
export const iconNames = [
  'HomeSimple',
  'Calendar',
  'CalendarPlus',
  'PageEdit',
  'ScanQrCode',
  'Group',
  'StatsReport',
  'GraphUp',
  'StatsUpSquare',
  'Safe',
  'SendDiagonal',
  'CreditCard',
  'List',
  'Community',
  'UserPlus',
  'Key',
  'Building',
  'User',
  'Bell',
] as const;

export type IconName = typeof iconNames[number];
