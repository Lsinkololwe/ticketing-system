/**
 * Fine-grained staff capabilities (prototype ACT table). The backend resolvers
 * stay the source of truth; this only decides what the UI offers.
 */
import type { StaffRole } from '@/config/navigation';

const SA: StaffRole[] = ['SUPER_ADMIN'];
const OPS: StaffRole[] = ['SUPER_ADMIN', 'ADMIN'];
const FIN: StaffRole[] = ['SUPER_ADMIN', 'ADMIN', 'FINANCE', 'FINANCE_LEAD'];

export const ACTIONS = {
  createAdmin: SA,
  syncAll: SA,
  deleteUser: SA,
  staffRoles: SA,
  forceComplete: SA,
  cfgHolds: SA,
  cfgEdit: OPS,
  announce: OPS,
  closeEscrow: OPS,
  orgs: OPS,
  users: OPS,
  decide: OPS,
  payoutDecide: FIN,
  secondApprove: ['SUPER_ADMIN', 'FINANCE_LEAD'] as StaffRole[],
  postJournal: FIN,
  reconcile: FIN,
  mediaMod: OPS,
  stock: OPS,
  featureEvent: OPS,
  escalations: ['SUPER_ADMIN', 'FINANCE_LEAD'] as StaffRole[],
} as const satisfies Record<string, StaffRole[]>;

export type ActionKey = keyof typeof ACTIONS;

const LABELS: Record<StaffRole, string> = {
  SUPER_ADMIN: 'super admins',
  ADMIN: 'admins',
  FINANCE: 'finance staff',
  FINANCE_LEAD: 'finance leads',
};

const WHAT: Record<ActionKey, string> = {
  createAdmin: 'create staff accounts',
  syncAll: 'sync every account from Keycloak',
  deleteUser: 'delete users',
  staffRoles: 'change staff roles',
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

export function can(roles: readonly StaffRole[], key: ActionKey): boolean {
  return ACTIONS[key].some((r) => roles.includes(r));
}

/** "Only super admins can delete users." for locked controls. */
export function needText(key: ActionKey): string {
  const who = (ACTIONS[key] as readonly StaffRole[]).map((r) => LABELS[r]);
  const list = who.length === 1 ? who[0] : `${who.slice(0, -1).join(', ')} and ${who[who.length - 1]}`;
  return `Only ${list} can ${WHAT[key]}.`;
}
