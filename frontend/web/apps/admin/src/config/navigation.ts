/**
 * Admin Navigation Configuration
 *
 * Role-based navigation structure for the MyTicket Zambia Admin.
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
 *
 * Note: SCANNER, ORGANIZER, and CUSTOMER are valid AdminRole values but hold
 * no dashboard-access sections — they will receive an empty navigation set.
 */

// `import type` erases to nothing at runtime, so `server-only` in the barrel
// is never triggered when this config is consumed by Client Components.
import type { AdminRole } from '@/lib/auth/interfaces';

// =============================================================================
// TYPES
// =============================================================================

// Re-export AdminRole so existing callers that import it from this module
// continue to work without changes.
export type { AdminRole };

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

/**
 * The admin navigation, exactly as `Admin - Dashboard.dc.html` declares it.
 *
 * <h2>This list is not editorial</h2>
 * It is a transcription of the `NAV` constant in the design project's admin
 * dashboard screen — seven sections, fifteen items, in this order. The app
 * previously carried eight sections with a four-item Action center and a
 * Transactions group the design does not have, which is the drift that made the
 * shell "totally different from the design".
 *
 * <p>Adding an item here without a corresponding entry in the design is how that
 * drift starts again. If a screen needs navigation the design does not show,
 * change the design first.
 *
 * @see frontend/web/docs/DESIGN_AUTHORITY.md
 */
export const navigationConfig: NavSection[] = [
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

  {
    id: 'action-center',
    title: 'Action center',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        // The design folds organizer applications, event reviews and document
        // verification into one queue. They remain reachable as routes and as
        // the dashboard's action cards; they are not separate nav items.
        id: 'pending-approvals',
        label: 'All approvals',
        href: '/approvals',
        icon: 'ClipboardCheck',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'recovery-queue',
        label: 'Recovery queue',
        href: '/transactions/recovery',
        icon: 'WarningTriangle',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },

  {
    id: 'events',
    title: 'Events',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        id: 'all-events',
        label: 'All events',
        href: '/events',
        icon: 'Calendar',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
      {
        id: 'event-categories',
        label: 'Categories',
        href: '/events/categories',
        icon: 'Folder',
        roles: ['SUPER_ADMIN', 'ADMIN'],
      },
    ],
  },

  {
    id: 'users',
    title: 'Users',
    roles: ['SUPER_ADMIN', 'ADMIN'],
    items: [
      {
        id: 'all-users',
        label: 'All users',
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

  {
    id: 'financial-ops',
    title: 'Financial ops',
    roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
    items: [
      {
        id: 'payout-requests',
        label: 'Payout requests',
        href: '/finance/payouts',
        icon: 'SendDiagonal',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
      },
      {
        id: 'refund-requests',
        label: 'Refund requests',
        href: '/finance/refunds',
        icon: 'Undo',
        badge: 'dynamic',
        roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
      },
      {
        id: 'escrow-accounts',
        label: 'Escrow accounts',
        href: '/finance/escrow',
        icon: 'Safe',
        roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
      },
    ],
  },

  {
    id: 'analytics',
    title: 'Analytics',
    roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
    items: [
      {
        id: 'platform-analytics',
        label: 'Platform overview',
        href: '/analytics',
        icon: 'StatsReport',
        roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
      },
      {
        id: 'ledger-reconciliation',
        label: 'Ledger & reconciliation',
        href: '/analytics/ledger',
        icon: 'GraphUp',
        roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE'],
      },
    ],
  },

  {
    id: 'system',
    title: 'System',
    roles: ['SUPER_ADMIN'],
    items: [
      {
        id: 'platform-configuration',
        label: 'Platform configuration',
        href: '/system/configuration',
        icon: 'Settings',
        roles: ['SUPER_ADMIN'],
      },
      {
        id: 'observability',
        label: 'Observability',
        href: '/system/observability',
        icon: 'Activity',
        roles: ['SUPER_ADMIN'],
      },
      {
        id: 'audit-logs',
        label: 'Audit logs',
        href: '/system/audit',
        icon: 'HistoricShield',
        roles: ['SUPER_ADMIN'],
      },
    ],
  },
];

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Return the UNION of all navigation sections/items visible to ANY of the
 * supplied roles.
 *
 * - Preserves the section and item order declared in `navigationConfig`.
 * - Emits no duplicate sections or items (each appears at most once because
 *   we iterate `navigationConfig` once and filter per-item by role set).
 * - Roles not present in any section's `roles` array (e.g. SCANNER, CUSTOMER)
 *   will yield an empty array — this is correct; never coerce unknown roles.
 */
export function getNavigationForRoles(roles: AdminRole[]): NavSection[] {
  // Use a Set for O(1) membership tests across potentially many items.
  const roleSet = new Set<string>(roles);

  return navigationConfig
    .filter((section) => section.roles.some((r) => roleSet.has(r)))
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles.some((r) => roleSet.has(r))),
    }))
    .filter((section) => section.items.length > 0);
}

/**
 * Filter navigation based on a single user role.
 *
 * Kept for back-compat; implemented as `getNavigationForRoles([role])` so
 * the Sidebar can migrate to `getNavigationForRoles` at its own pace.
 */
export function getNavigationForRole(role: AdminRole): NavSection[] {
  return getNavigationForRoles([role]);
}

/**
 * Get a flat list of all nav items (including children) visible to a role.
 */
export function getAllNavItemsForRole(role: AdminRole): NavItem[] {
  const sections = getNavigationForRoles([role]);
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
 * Check if a nav item's href matches the current pathname.
 *
 * Uses PREFIX-AWARE matching: a nav item is active when the pathname equals
 * the href exactly OR begins with `href + '/'`.  This keeps parent items
 * highlighted on nested routes (e.g. `/organizers/123` lights up `/organizers`)
 * while preventing `/events` from matching `/events-archive`.
 */
export function isNavItemActive(href: string, pathname: string): boolean {
  return pathname === href || pathname.startsWith(href + '/');
}

/**
 * Return the href of the BEST (most specific) visible nav item for the given
 * pathname and role set, using longest-match precedence.
 *
 * This ensures that `/events/calendar` resolves to the Calendar item
 * (`/events/calendar`, 16 chars) rather than "All events" (`/events`, 7 chars),
 * and `/dashboard/settings` resolves to Settings rather than "Dashboard".
 *
 * Returns `null` when no visible item matches the pathname.
 */
export function getActiveNavHref(pathname: string, roles: AdminRole[]): string | null {
  // Collect every visible item (top-level + children) in config order.
  const candidates: NavItem[] = [];
  for (const section of getNavigationForRoles(roles)) {
    for (const item of section.items) {
      candidates.push(item);
      if (item.children) {
        candidates.push(...item.children);
      }
    }
  }

  // Among all matching hrefs, keep the longest (most specific) one.
  let bestHref: string | null = null;
  let bestLength = -1;

  for (const item of candidates) {
    if (isNavItemActive(item.href, pathname) && item.href.length > bestLength) {
      bestHref = item.href;
      bestLength = item.href.length;
    }
  }

  return bestHref;
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
  Database: 'Database',
  HistoricShield: 'HistoricShield',
  Key: 'Key',
};
