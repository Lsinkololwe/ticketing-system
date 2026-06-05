/**
 * Admin Navigation Configuration
 *
 * Role-based navigation structure for the PML Admin Portal.
 *
 * Navigation Philosophy:
 * - Action-oriented: Show pending tasks, not just data views
 * - Role-appropriate: Each role sees relevant features only
 * - Workflow-focused: Group by workflow, not entity type
 *
 * Admin Roles:
 * - SUPER_ADMIN: Full access to all features
 * - ADMIN: Operations focus (approvals, user management, events)
 * - FINANCE: Financial focus (payouts, refunds, escrow, reports)
 */


// =============================================================================
// TYPES
// =============================================================================

export type AdminRole = 'SUPER_ADMIN' | 'ADMIN' | 'FINANCE';

export interface NavItem {
  id: string;
  label: string;
  href: string;
  icon: string; // Icon name from iconoir-react
  /** Badge count - for pending items */
  badge?: number | 'dynamic';
  /** Roles that can see this item */
  roles: AdminRole[];
  /** Sub-items (for nested navigation) */
  children?: NavItem[];
}

export interface NavSection {
  id: string;
  title: string;
  roles: AdminRole[];
  items: NavItem[];
}

// =============================================================================
// NAVIGATION CONFIGURATION
// =============================================================================

export const navigationConfig: NavSection[] = [
  // ===========================================================================
  // DASHBOARD - All roles
  // ===========================================================================
  {
    id: 'overview',
    title: 'Overview',
    roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        href: '/dashboard',
        icon: 'HomeSimple',
        roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
      },
    ],
  },

  // ===========================================================================
  // ACTION CENTER - ADMIN/SUPER_ADMIN (Operations focus)
  // ===========================================================================
  {
    id: 'action-center',
    title: 'Action Center',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        id: 'pending-approvals',
        label: 'All Approvals',
        href: '/approvals',
        icon: 'ClipboardCheck',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'organizer-applications',
        label: 'Organizer Applications',
        href: '/approvals/organizers',
        icon: 'Group',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'event-reviews',
        label: 'Event Reviews',
        href: '/approvals/events',
        icon: 'Calendar',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'document-verification',
        label: 'Document Verification',
        href: '/approvals/documents',
        icon: 'PageSearch',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },

  // ===========================================================================
  // EVENTS - ADMIN/SUPER_ADMIN
  // ===========================================================================
  {
    id: 'events',
    title: 'Events',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        id: 'all-events',
        label: 'All Events',
        href: '/events',
        icon: 'Calendar',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'event-calendar',
        label: 'Calendar',
        href: '/events/calendar',
        icon: 'CalendarPlus',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'event-categories',
        label: 'Categories',
        href: '/events/categories',
        icon: 'Folder',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'event-locations',
        label: 'Locations',
        href: '/events/locations',
        icon: 'MapPin',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },

  // ===========================================================================
  // USERS - ADMIN/SUPER_ADMIN
  // ===========================================================================
  {
    id: 'users',
    title: 'Users',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        id: 'all-users',
        label: 'All Users',
        href: '/users',
        icon: 'Group',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'organizers',
        label: 'Organizers',
        href: '/organizers',
        icon: 'Building',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'organizations',
        label: 'Organizations',
        href: '/organizations',
        icon: 'Community',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },

  // ===========================================================================
  // FINANCIAL OPERATIONS - FINANCE/SUPER_ADMIN
  // ===========================================================================
  {
    id: 'financial-ops',
    title: 'Financial Operations',
    roles: ['SUPER_ADMIN', 'FINANCE', "ADMIN"],
    items: [
      {
        id: 'payout-requests',
        label: 'Payout Requests',
        href: '/finance/payouts',
        icon: 'SendDiagonal',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
      {
        id: 'refund-requests',
        label: 'Refund Requests',
        href: '/finance/refunds',
        icon: 'Undo',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
      {
        id: 'escrow-accounts',
        label: 'Escrow Accounts',
        href: '/finance/escrow',
        icon: 'Safe',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
    ],
  },

  // ===========================================================================
  // TRANSACTIONS - FINANCE/SUPER_ADMIN
  // ===========================================================================
  {
    id: 'transactions',
    title: 'Transactions',
    roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
    items: [
      {
        id: 'payment-history',
        label: 'Payment History',
        href: '/transactions/payments',
        icon: 'CreditCard',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
      {
        id: 'ticket-sales',
        label: 'Ticket Sales',
        href: '/transactions/tickets',
        icon: 'Label',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
      {
        id: 'commissions',
        label: 'Commissions',
        href: '/transactions/commissions',
        icon: 'Percentage',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
    ],
  },

  // ===========================================================================
  // ANALYTICS - All roles (filtered views)
  // ===========================================================================
  {
    id: 'analytics',
    title: 'Analytics',
    roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
    items: [
      {
        id: 'platform-analytics',
        label: 'Platform Overview',
        href: '/analytics',
        icon: 'StatsReport',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'revenue-reports',
        label: 'Revenue Reports',
        href: '/analytics/revenue',
        icon: 'GraphUp',
        roles: ['SUPER_ADMIN', 'FINANCE', 'ADMIN'],
      },
      {
        id: 'user-growth',
        label: 'User Growth',
        href: '/analytics/users',
        icon: 'StatsUpSquare',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },

  // ===========================================================================
  // SYSTEM - SUPER_ADMIN only
  // ===========================================================================
  {
    id: 'system',
    title: 'System',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        id: 'settings',
        label: 'Settings',
        href: '/dashboard/settings',
        icon: 'Settings',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'audit-logs',
        label: 'Audit Logs',
        href: '/system/audit',
        icon: 'HistoricShield',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'api-keys',
        label: 'API Keys',
        href: '/system/api-keys',
        icon: 'Key',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },
];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Filter navigation based on user role
 */
export function getNavigationForRole(role: AdminRole): NavSection[] {
  return navigationConfig
    .filter((section) => section.roles.includes(role))
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles.includes(role)),
    }))
    .filter((section) => section.items.length > 0);
}

/**
 * Get flat list of all nav items for a role
 */
export function getAllNavItemsForRole(role: AdminRole): NavItem[] {
  const sections = getNavigationForRole(role);
  const items: NavItem[] = [];

  for (const section of sections) {
    for (const item of section.items) {
      items.push(item);
      if (item.children) {
        items.push(...item.children.filter((child) => child.roles.includes(role)));
      }
    }
  }

  return items;
}

/**
 * Check if a path is active (exact match only)
 */
export function isNavItemActive(href: string, pathname: string): boolean {
  return pathname === href;
}

// =============================================================================
// ICON MAPPING (for dynamic icon rendering)
// =============================================================================

export const iconMap: Record<string, string> = {
  HomeSimple: 'HomeSimple',
  ClipboardCheck: 'ClipboardCheck',
  Group: 'Group',
  Calendar: 'Calendar',
  CalendarPlus: 'CalendarPlus',
  PageSearch: 'PageSearch',
  Folder: 'Folder',
  MapPin: 'MapPin',
  Building: 'Building',
  Community: 'Community',
  SendDiagonal: 'SendDiagonal',
  Undo: 'Undo',
  Safe: 'Safe',
  CreditCard: 'CreditCard',
  Label: 'Label',
  Percentage: 'Percentage',
  StatsReport: 'StatsReport',
  GraphUp: 'GraphUp',
  TrendingUp: 'TrendingUp',
  Settings: 'Settings',
  HistoricShield: 'HistoricShield',
  Key: 'Key',
};
