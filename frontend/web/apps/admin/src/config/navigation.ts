/**
 * Platform admin navigation: modules, their tabs and which staff roles may
 * open them. Mirrors the approved prototype (six groups, ten modules).
 */
import type { IconName } from '@pml.tickets/shared';
import type { UserType } from '@pml.tickets/shared/types/graphql';
import { PLATFORM_ROLE_GRANTS, type PermissionCode } from '@pml.tickets/shared/generated/permission-codes';

/** The platform roles that sign in to this console: a subset of the generated `UserType`. */
export type StaffRole = Extract<UserType, 'SUPER_ADMIN' | 'ADMIN' | 'FINANCE' | 'FINANCE_LEAD'>;

export const ROLE_LABELS: Record<StaffRole, string> = {
  SUPER_ADMIN: 'Super admin',
  ADMIN: 'Admin',
  FINANCE: 'Finance',
  FINANCE_LEAD: 'Finance lead',
};

/** Staff roles in privilege order: the keys of the label map, so a role has a label or does not compile. */
export const STAFF_ROLES: readonly StaffRole[] = Object.keys(ROLE_LABELS) as StaffRole[];

export const ROLE_DESCRIPTIONS: Record<StaffRole, string> = {
  SUPER_ADMIN: 'Everything, including staff accounts, timing rules and force-complete.',
  ADMIN: 'Approvals, events, users, organizations and day-to-day finance.',
  FINANCE: 'Payouts, refunds, escrow, chargebacks, ledger and reports.',
  FINANCE_LEAD: 'Finance plus escalations, second approvals and stuck transactions.',
};

export type ModuleId =
  | 'dashboard'
  | 'approvals'
  | 'events'
  | 'users'
  | 'finance'
  | 'ledger'
  | 'transactions'
  | 'analytics'
  | 'health'
  | 'config';

export interface ModuleTab {
  id: string;
  label: string;
  /** The permission that opens this tab; when absent the module's own permission does. */
  permission?: PermissionCode;
}

export interface ConsoleModule {
  id: ModuleId;
  label: string;
  icon: IconName;
  /** Base path; modules with tabs live at `/<path>/<tab>`. */
  path: string;
  /** The permission that opens the module; absent means every signed-in staff member. */
  permission?: PermissionCode;
  group: string;
  tabs?: ModuleTab[];
}

export const MODULES: ConsoleModule[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', path: '/dashboard',  group: 'Overview' },
  {
    id: 'approvals', label: 'Approvals', icon: 'check-circle', path: '/approvals', permission: 'approval:view', group: 'Review',
    tabs: [{ id: 'orgs', label: 'Organizers' }, { id: 'events', label: 'Events' }, { id: 'docs', label: 'Documents', permission: 'kyb_document:view' }],
  },
  {
    id: 'events', label: 'Events', icon: 'calendar', path: '/events', permission: 'catalog:view', group: 'Platform',
    tabs: [
      { id: 'all', label: 'All events' },
      { id: 'categories', label: 'Categories' },
      { id: 'locations', label: 'Provinces and cities' },
      { id: 'media', label: 'Media moderation', permission: 'media_moderation:view' },
      { id: 'stock', label: 'Stock images', permission: 'stock_image:manage' },
    ],
  },
  {
    id: 'users', label: 'Users & orgs', icon: 'users', path: '/users', permission: 'user:view', group: 'Platform',
    tabs: [{ id: 'users', label: 'Users' }, { id: 'orgs', label: 'Organizations', permission: 'organization_directory:view' }],
  },
  {
    id: 'finance', label: 'Finance', icon: 'card', path: '/finance', permission: 'financial:view', group: 'Money',
    tabs: [
      { id: 'payouts', label: 'Payout requests', permission: 'payout:view' },
      { id: 'refunds', label: 'Refund requests', permission: 'refund:view' },
      { id: 'escrow', label: 'Escrow accounts', permission: 'escrow:view' },
      { id: 'chargebacks', label: 'Chargebacks', permission: 'chargeback:view' },
      { id: 'banks', label: 'Payout accounts', permission: 'bank_account:view' },
    ],
  },
  {
    id: 'ledger', label: 'Ledger', icon: 'receipt', path: '/ledger', permission: 'ledger:view', group: 'Money',
    tabs: [
      { id: 'coa', label: 'Chart of accounts', permission: 'ledger:view' },
      { id: 'journal', label: 'Journal entries' },
      { id: 'tb', label: 'Trial balance' },
      { id: 'platform', label: 'Platform accounts', permission: 'platform_account:view' },
      { id: 'commission', label: 'Commission', permission: 'commission:view' },
      { id: 'recon', label: 'Reconciliation', permission: 'reconciliation:view' },
    ],
  },
  {
    id: 'transactions', label: 'Transactions', icon: 'server', path: '/transactions', group: 'Operations',
    tabs: [
      { id: 'payments', label: 'Payments', permission: 'payment:view' },
      { id: 'tickets', label: 'Tickets', permission: 'ticket:view' },
      { id: 'reservations', label: 'Reservations', permission: 'reservation:view' },
      { id: 'recovery', label: 'Transaction recovery', permission: 'transaction:recover' },
      { id: 'refdata', label: 'Reference data', permission: 'reference_data:manage' },
      { id: 'audit', label: 'Audit log', permission: 'audit:view' },
      { id: 'announce', label: 'Announcements', permission: 'platform:manage' },
    ],
  },
  { id: 'analytics', label: 'Analytics', icon: 'chart', path: '/analytics', permission: 'statistics:view', group: 'Operations' },
  { id: 'health', label: 'Health', icon: 'pulse', path: '/health', permission: 'platform:view', group: 'Operations' },
  {
    id: 'config', label: 'Settings', icon: 'cog', path: '/config', permission: 'platform:view', group: 'Setup',
    tabs: [{ id: 'rules', label: 'Platform rules' }, { id: 'roles', label: 'Roles and access', permission: 'permission:view' }, { id: 'refdata', label: 'Reference data', permission: 'reference_data:manage' }],
  },
];

export const MODULE_BY_ID = Object.fromEntries(MODULES.map((m) => [m.id, m])) as Record<ModuleId, ConsoleModule>;

/** Staff roles carried by a session (upper-cased, filtered to platform staff). */
export function staffRolesOf(roles: readonly string[] | undefined): StaffRole[] {
  const set = new Set((roles ?? []).map((r) => r.toUpperCase()));
  return STAFF_ROLES.filter((r) => set.has(r));
}

/**
 * Whether the permissions held open the module: its own permission, and for a module made of tabs at
 * least one tab. A module with no permission is open to every signed-in staff member.
 */
export function canOpenModule(held: readonly string[], id: ModuleId): boolean {
  const m = MODULE_BY_ID[id];
  if (m.permission && !held.includes(m.permission)) return false;
  return m.tabs ? tabsFor(held, id).length > 0 : true;
}

/** Tabs of a module that the held permissions may open. */
export function tabsFor(held: readonly string[], id: ModuleId): ModuleTab[] {
  const m = MODULE_BY_ID[id];
  if (!m.tabs) return [];
  if (m.permission && !held.includes(m.permission)) return [];
  return m.tabs.filter((t) => !t.permission || held.includes(t.permission));
}

/** The permissions a staff role grants on its own: what the navigation matrix shows per role. */
export function grantsOf(role: StaffRole): readonly PermissionCode[] {
  return PLATFORM_ROLE_GRANTS[role];
}

/** Labels on the phone bottom bar (the prototype's rail names). */
export const RAIL_LABELS: Partial<Record<ModuleId, string>> = {
  dashboard: 'Home',
  approvals: 'Approvals',
  users: 'Users',
  ledger: 'Ledger',
  transactions: 'System',
  analytics: 'Analytics',
  health: 'Health',
  config: 'Config',
};

/** Module a pathname belongs to ('/user/abc' -> users, '/event/x' -> events). */
export function moduleOfPath(pathname: string): ModuleId | null {
  const first = pathname.split('/')[1] ?? '';
  if (first === 'user' || first === 'org') return 'users';
  if (first === 'event') return 'events';
  if (first === 'profile') return null;
  return MODULES.find((m) => m.path === `/${first}`)?.id ?? null;
}

export function modulesFor(held: readonly string[]): ConsoleModule[] {
  return MODULES.filter((m) => canOpenModule(held, m.id));
}
