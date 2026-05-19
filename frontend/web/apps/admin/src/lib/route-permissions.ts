// Route to Permission Mapping
// Maps routes to required permissions for access control
// Uses backend permission names directly (dot.case format from Permissions.java)

// Permission string type - matches backend permission format
export type PermissionString = string;

export interface RoutePermissionConfig {
  route: string;
  requiredPermissions: PermissionString[];
  requireAll?: boolean; // If true, requires all permissions; if false (default), requires any
  description?: string;
}

/**
 * Route-to-permission mappings
 * Defines which permissions are required to access each route
 * 
 * NOTE: This file is for the ADMIN app only.
 * The TICKETING app (apps/ticketing) allows public browsing of events, categories, and locations
 * without authentication. Authentication is only required for actions like:
 * - Purchasing tickets (requires TICKET_PURCHASE permission)
 * - Viewing own tickets (requires TICKET_VIEW permission)
 * - Requesting refunds (requires TICKET_VIEW permission)
 */
export const ROUTE_PERMISSIONS: RoutePermissionConfig[] = [
  // Dashboard
  {
    route: '/dashboard',
    requiredPermissions: [],
    description: 'Dashboard overview - accessible to all authenticated users',
  },

  // User Management
  {
    route: '/users',
    requiredPermissions: ['users.view' as PermissionString],
    description: 'User management - view all users',
  },

  // Event Management
  // NOTE: Admin app requires EVENT_VIEW to manage events
  // Public ticketing app allows unauthenticated browsing (permitAll on backend)
  {
    route: '/events',
    requiredPermissions: ['events.view' as PermissionString],
    description: 'Event management - view and manage events (admin only)',
  },
  {
    route: '/events/pending-approval',
    requiredPermissions: ['events.manage' as PermissionString],
    description: 'Pending event approvals - review and approve/reject events',
  },
  {
    route: '/events/approved-not-published',
    requiredPermissions: ['events.view' as PermissionString],
    description: 'Approved events not yet published - guide organizers to publish',
  },
  {
    route: '/organizers/support',
    requiredPermissions: ['events.view' as PermissionString],
    description: 'Organizer support dashboard - help organizers through event lifecycle',
  },

  // Ticket Management
  {
    route: '/tickets',
    requiredPermissions: ['tickets.view' as PermissionString],
    description: 'Ticket management - view tickets',
  },

  // Financial - Transactions
  {
    route: '/transactions',
    requiredPermissions: ['transactions.view' as PermissionString],
    description: 'Transaction management - view financial transactions',
  },

  // Financial - Payouts
  {
    route: '/payouts',
    requiredPermissions: ['payouts.view' as PermissionString],
    description: 'Payout management - view and manage payouts',
  },

  // Analytics
  {
    route: '/analytics',
    requiredPermissions: ['analytics.view' as PermissionString],
    description: 'Analytics dashboard - view analytics and reports',
  },

  // Accounts - Escrow
  {
    route: '/accounts/escrow',
    requiredPermissions: ['escrow.view' as PermissionString],
    description: 'Escrow account management - view and manage escrow accounts',
  },
  {
    route: '/accounts/platform',
    requiredPermissions: ['escrow.view' as PermissionString],
    description: 'Platform revenue account management - view and manage platform revenue accounts',
  },

  // System
  {
    route: '/system',
    requiredPermissions: ['system.manage' as PermissionString],
    description: 'System settings - configure system settings',
  },
];

/**
 * Menu item configuration with permission requirements
 */
export interface MenuItemConfig {
  title: string;
  description: string;
  href: string;
  icon?: React.ElementType | null;
  requiredPermissions: PermissionString[];
  requireAll?: boolean;
  category?: 'management' | 'financial' | 'system';
}

export const MENU_ITEMS: MenuItemConfig[] = [
  // Management Menu Items
  {
    title: 'Users',
    description: 'Manage user accounts and permissions',
    href: '/users',
    icon: null, // Will be set by component
    requiredPermissions: ['users.view' as PermissionString],
    category: 'management',
  },
  {
    title: 'Events',
    description: 'Create and manage events',
    href: '/events',
    icon: null,
    requiredPermissions: ['events.view' as PermissionString],
    category: 'management',
  },
  {
    title: 'Pending Approvals',
    description: 'Review and approve pending events',
    href: '/events/pending-approval',
    icon: null,
    requiredPermissions: ['events.manage' as PermissionString],
    category: 'management',
  },
  {
    title: 'Approved Not Published',
    description: 'Guide organizers to publish approved events',
    href: '/events/approved-not-published',
    icon: null,
    requiredPermissions: ['events.view' as PermissionString],
    category: 'management',
  },
  {
    title: 'Organizer Support',
    description: 'Support organizers through event lifecycle',
    href: '/organizers/support',
    icon: null,
    requiredPermissions: ['events.view' as PermissionString],
    category: 'management',
  },
  {
    title: 'Tickets',
    description: 'Manage ticket sales and inventory',
    href: '/tickets',
    icon: null,
    requiredPermissions: ['tickets.view' as PermissionString],
    category: 'management',
  },
  {
    title: 'Analytics',
    description: 'View reports and insights',
    href: '/analytics',
    icon: null,
    requiredPermissions: ['analytics.view' as PermissionString],
    category: 'management',
  },

  // Financial Menu Items
  {
    title: 'Transactions',
    description: 'View and manage transactions',
    href: '/transactions',
    icon: null,
    requiredPermissions: ['transactions.view' as PermissionString],
    category: 'financial',
  },
  {
    title: 'Payouts',
    description: 'Manage payouts and settlements',
    href: '/payouts',
    icon: null,
    requiredPermissions: ['payouts.view' as PermissionString],
    category: 'financial',
  },

  // System Menu Items
  {
    title: 'System Settings',
    description: 'Configure system settings',
    href: '/system',
    icon: null,
    requiredPermissions: ['system.manage' as PermissionString],
    category: 'system',
  },
];

/**
 * Get permission requirements for a route
 */
export function getRoutePermissions(route: string): RoutePermissionConfig | undefined {
  return ROUTE_PERMISSIONS.find(config => config.route === route);
}

/**
 * Check if user has access to a route based on permissions
 */
export function canAccessRoute(
  route: string,
  userPermissions: string[],
  requireAll: boolean = false
): boolean {
  const config = getRoutePermissions(route);
  
  if (!config || config.requiredPermissions.length === 0) {
    return true; // Public route or no restrictions
  }

  const hasAllPermissions = config.requiredPermissions.every(permission =>
    userPermissions.includes(permission as string)
  );

  const hasAnyPermission = config.requiredPermissions.some(permission =>
    userPermissions.includes(permission as string)
  );

  const checkAll = config.requireAll ?? requireAll;
  return checkAll ? hasAllPermissions : hasAnyPermission;
}

/**
 * Filter menu items based on user permissions
 */
export function filterMenuItemsByPermissions(
  items: MenuItemConfig[],
  userPermissions: string[]
): MenuItemConfig[] {
  return items.filter(item => {
    if (item.requiredPermissions.length === 0) {
      return true; // No restrictions
    }

    const hasAllPermissions = item.requiredPermissions.every(permission =>
      userPermissions.includes(permission as string)
    );

    const hasAnyPermission = item.requiredPermissions.some(permission =>
      userPermissions.includes(permission as string)
    );

    return item.requireAll ? hasAllPermissions : hasAnyPermission;
  });
}

