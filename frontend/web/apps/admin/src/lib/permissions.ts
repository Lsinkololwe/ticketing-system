/**
 * What the console offers for each staff action, keyed to the catalogue permission that action
 * needs. The services decide every request; this only decides what the UI shows, and it asks the
 * same question they do: does the caller hold the permission.
 */
import { PLATFORM_ROLE_GRANTS, type PermissionCode } from '@pml.tickets/shared/generated/permission-codes';
import { ROLE_LABELS, STAFF_ROLES } from '@/config/navigation';

export const ACTIONS = {
  createAdmin: 'staff:manage',
  deleteUser: 'user:manage',
  staffRoles: 'staff:manage',
  resetStaffPassword: 'staff:manage',
  forceComplete: 'payment:approve',
  cfgHolds: 'platform:configure',
  cfgEdit: 'platform:configure',
  announce: 'platform:manage',
  closeEscrow: 'escrow:manage',
  orgs: 'organization:manage',
  users: 'user:manage',
  decide: 'approval:manage',
  payoutDecide: 'payout:approve',
  secondApprove: 'refund:approve',
  postJournal: 'ledger:manage',
  reconcile: 'reconciliation:manage',
  mediaMod: 'media_moderation:manage',
  stock: 'stock_image:manage',
  featureEvent: 'event:manage',
  escalations: 'chargeback:manage',
} as const satisfies Record<string, PermissionCode>;

export type ActionKey = keyof typeof ACTIONS;

const WHAT: Record<ActionKey, string> = {
  createAdmin: 'create staff accounts',
  deleteUser: 'delete users',
  staffRoles: 'change staff roles',
  resetStaffPassword: 'reset staff passwords',
  forceComplete: 'force-complete transactions',
  cfgHolds: 'change timing rules',
  cfgEdit: 'edit platform rules',
  announce: 'send announcements',
  closeEscrow: 'close escrow accounts',
  orgs: 'manage organizations',
  users: 'manage users',
  decide: 'approve or reject submissions',
  payoutDecide: 'decide payouts',
  secondApprove: 'give the second approval',
  postJournal: 'post journal entries',
  reconcile: 'run reconciliation',
  mediaMod: 'moderate media',
  stock: 'manage stock images',
  featureEvent: 'feature events',
  escalations: 'handle escalations',
};

/** Whether the held permissions allow the action. */
export function can(held: readonly string[], key: ActionKey): boolean {
  return held.includes(ACTIONS[key]);
}

const PLURAL: Record<string, string> = {
  SUPER_ADMIN: 'super admins',
  ADMIN: 'admins',
  FINANCE: 'finance staff',
  FINANCE_LEAD: 'finance leads',
};

/** "Only super admins can delete users." for locked controls: the roles that grant the permission. */
export function needText(key: ActionKey): string {
  const permission = ACTIONS[key];
  const who = STAFF_ROLES.filter((r) => PLATFORM_ROLE_GRANTS[r].includes(permission)).map((r) => PLURAL[r] ?? ROLE_LABELS[r]);
  if (who.length === 0) return `Nobody holds the permission to ${WHAT[key]}.`;
  const list = who.length === 1 ? who[0] : `${who.slice(0, -1).join(', ')} and ${who[who.length - 1]}`;
  return `Only ${list} can ${WHAT[key]}.`;
}
