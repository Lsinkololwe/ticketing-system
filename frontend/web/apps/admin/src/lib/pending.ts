/**
 * "Pending work" for a staff role, built from the real pending-queue counts.
 * Shared by the notification bell, the command palette and the dashboards.
 * Queues without a backend count (chargebacks, stuck transactions, alerts ...)
 * are not listed: nothing is shown rather than an invented number.
 */
import { MODULE_BY_ID, STAFF_ROLES, canOpenModule, tabsFor, type ModuleId, type StaffRole } from '@/config/navigation';

export interface PendingCountsLike {
  'pending-approvals': number;
  'organizer-applications': number;
  'event-reviews': number;
  'document-verification': number;
  'payout-requests': number;
  'refund-requests': number;
}

export interface PendingItem {
  label: string;
  count: number;
  module: ModuleId;
  tab?: string;
  /** Needs attention soon (styled as hot). */
  hot?: boolean;
}

export function hrefOf(module: ModuleId, tab?: string): string {
  const m = MODULE_BY_ID[module];
  return tab ? `${m.path}/${tab}` : m.path;
}

export function pendingFor(roles: readonly StaffRole[], counts: PendingCountsLike): PendingItem[] {
  const out: PendingItem[] = [];
  const add = (label: string, count: number, module: ModuleId, tab: string) => {
    if (count <= 0 || !canOpenModule(roles, module)) return;
    if (!tabsFor(roles, module).some((t) => t.id === tab)) return;
    out.push({ label, count, module, tab });
  };
  add('Organizer applications', counts['organizer-applications'], 'approvals', 'orgs');
  add('Events awaiting approval', counts['event-reviews'], 'approvals', 'events');
  add('Documents to verify', counts['document-verification'], 'approvals', 'docs');
  add('Payout requests pending', counts['payout-requests'], 'finance', 'payouts');
  add('Refunds pending', counts['refund-requests'], 'finance', 'refunds');
  return out;
}

/** Highest-priority staff role: SUPER_ADMIN > ADMIN > FINANCE_LEAD > FINANCE. */
export function primaryRole(roles: readonly StaffRole[]): StaffRole | null {
  const order: StaffRole[] = ['SUPER_ADMIN', 'ADMIN', 'FINANCE_LEAD', 'FINANCE'];
  return order.find((r) => roles.includes(r)) ?? STAFF_ROLES.find((r) => roles.includes(r)) ?? null;
}
