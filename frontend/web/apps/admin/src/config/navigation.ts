/**
 * Platform admin navigation: modules, their tabs and which staff roles may
 * open them. Mirrors the approved prototype (six groups, ten modules).
 */
import type { IconName } from '@pml.tickets/shared';
import type { UserType } from '@pml.tickets/shared/types/graphql';

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
}

export interface ConsoleModule {
  id: ModuleId;
  label: string;
  icon: IconName;
  /** Base path; modules with tabs live at `/<path>/<tab>`. */
  path: string;
  roles: readonly StaffRole[];
  group: string;
  tabs?: ModuleTab[];
  /** Per-role tab allow-list (module default: every tab). */
  roleTabs?: Partial<Record<StaffRole, string[]>>;
}

const ALL: readonly StaffRole[] = STAFF_ROLES;
const OPS: readonly StaffRole[] = ['SUPER_ADMIN', 'ADMIN'];

export const MODULES: ConsoleModule[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', path: '/dashboard', roles: ALL, group: 'Overview' },
  {
    id: 'approvals', label: 'Approvals', icon: 'check-circle', path: '/approvals', roles: OPS, group: 'Review',
    tabs: [{ id: 'orgs', label: 'Organizers' }, { id: 'events', label: 'Events' }, { id: 'docs', label: 'Documents' }],
  },
  {
    id: 'events', label: 'Events', icon: 'calendar', path: '/events', roles: OPS, group: 'Platform',
    tabs: [
      { id: 'all', label: 'All events' },
      { id: 'categories', label: 'Categories' },
      { id: 'locations', label: 'Provinces and cities' },
      { id: 'media', label: 'Media moderation' },
      { id: 'stock', label: 'Stock images' },
    ],
  },
  {
    id: 'users', label: 'Users & orgs', icon: 'users', path: '/users', roles: OPS, group: 'Platform',
    tabs: [{ id: 'users', label: 'Users' }, { id: 'orgs', label: 'Organizations' }],
  },
  {
    id: 'finance', label: 'Finance', icon: 'card', path: '/finance', roles: ALL, group: 'Money',
    tabs: [
      { id: 'payouts', label: 'Payout requests' },
      { id: 'refunds', label: 'Refund requests' },
      { id: 'escrow', label: 'Escrow accounts' },
      { id: 'chargebacks', label: 'Chargebacks' },
      { id: 'banks', label: 'Payout accounts' },
    ],
  },
  {
    id: 'ledger', label: 'Ledger', icon: 'receipt', path: '/ledger', roles: ALL, group: 'Money',
    tabs: [
      { id: 'coa', label: 'Chart of accounts' },
      { id: 'journal', label: 'Journal entries' },
      { id: 'tb', label: 'Trial balance' },
      { id: 'platform', label: 'Platform accounts' },
      { id: 'commission', label: 'Commission' },
      { id: 'recon', label: 'Reconciliation' },
    ],
  },
  {
    id: 'transactions', label: 'Transactions', icon: 'server', path: '/transactions', roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE_LEAD'], group: 'Operations',
    tabs: [
      { id: 'payments', label: 'Payments' },
      { id: 'tickets', label: 'Tickets' },
      { id: 'reservations', label: 'Reservations' },
      { id: 'recovery', label: 'Transaction recovery' },
      { id: 'refdata', label: 'Reference data' },
      { id: 'audit', label: 'Audit log' },
      { id: 'announce', label: 'Announcements' },
    ],
    roleTabs: { FINANCE_LEAD: ['payments', 'reservations', 'recovery', 'audit'] },
  },
  { id: 'analytics', label: 'Analytics', icon: 'chart', path: '/analytics', roles: ALL, group: 'Operations' },
  { id: 'health', label: 'Health', icon: 'pulse', path: '/health', roles: ['SUPER_ADMIN', 'ADMIN', 'FINANCE_LEAD'], group: 'Operations' },
  {
    id: 'config', label: 'Settings', icon: 'cog', path: '/config', roles: OPS, group: 'Setup',
    tabs: [{ id: 'rules', label: 'Platform rules' }, { id: 'roles', label: 'Roles and access' }, { id: 'refdata', label: 'Reference data' }],
  },
];

export const MODULE_BY_ID = Object.fromEntries(MODULES.map((m) => [m.id, m])) as Record<ModuleId, ConsoleModule>;

/** Staff roles carried by a session (upper-cased, filtered to platform staff). */
export function staffRolesOf(roles: readonly string[] | undefined): StaffRole[] {
  const set = new Set((roles ?? []).map((r) => r.toUpperCase()));
  return STAFF_ROLES.filter((r) => set.has(r));
}

export function canOpenModule(roles: readonly StaffRole[], id: ModuleId): boolean {
  return MODULE_BY_ID[id].roles.some((r) => roles.includes(r));
}

/** Tabs of a module that the given roles may open (union across roles). */
export function tabsFor(roles: readonly StaffRole[], id: ModuleId): ModuleTab[] {
  const m = MODULE_BY_ID[id];
  if (!m.tabs) return [];
  const allowed = new Set<string>();
  for (const r of roles) {
    if (!m.roles.includes(r)) continue;
    (m.roleTabs?.[r] ?? m.tabs.map((t) => t.id)).forEach((t) => allowed.add(t));
  }
  return m.tabs.filter((t) => allowed.has(t.id));
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

export function modulesFor(roles: readonly StaffRole[]): ConsoleModule[] {
  return MODULES.filter((m) => canOpenModule(roles, m.id));
}
