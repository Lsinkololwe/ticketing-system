import type { EventRole, OrgRole } from '@/lib/api/team';

// Role names, descriptions and who may be invited are reference data (ORGANIZATION_ROLE, EVENT_ROLE): see
// `useTeamRoles()` in ./useTeamRoles. Only the permission matrix below stays here: it has no organizer-readable
// backend source (identity `permissions` / `rolePermissions` are ADMIN-only).

export interface Capability {
  key: string;
  label: string;
  permission: string;
  always: OrgRole[];
  /** Roles that get it only when an organization setting is on. */
  conditional?: { roles: OrgRole[]; setting: string };
}

/** The fixed role-to-permission catalogue (read only; defined by the platform). */
export const CAPABILITIES: Capability[] = [
  { key: 'org.edit', label: 'Edit organization profile', permission: 'organization:update', always: ['OWNER', 'ADMIN'] },
  { key: 'billing', label: 'Manage billing and payout settings', permission: 'organization:billing', always: ['OWNER'] },
  { key: 'owner', label: 'Transfer or delete the organization', permission: 'organization:delete', always: ['OWNER'] },
  { key: 'fin.view', label: 'View financial reports', permission: 'finance:read', always: ['OWNER', 'ADMIN'], conditional: { roles: ['MANAGER'], setting: 'managersCanViewFinancials' } },
  { key: 'payout', label: 'Request payouts', permission: 'payout:request', always: ['OWNER'], conditional: { roles: ['ADMIN'], setting: 'adminsCanRequestPayouts' } },
  { key: 'invite', label: 'Invite and remove members', permission: 'member:invite', always: ['OWNER', 'ADMIN'] },
  { key: 'roles', label: 'Change member roles', permission: 'member:update_role', always: ['OWNER', 'ADMIN'] },
  { key: 'ev.write', label: 'Create, edit and publish events', permission: 'event:update', always: ['OWNER', 'ADMIN', 'MANAGER'] },
  { key: 'ev.delete', label: 'Delete events', permission: 'event:delete', always: ['OWNER', 'ADMIN'] },
  { key: 'analytics', label: 'View event analytics', permission: 'analytics:read', always: ['OWNER', 'ADMIN', 'MANAGER', 'MARKETER'] },
  { key: 'promo', label: 'Manage promo codes', permission: 'promotion:manage', always: ['OWNER', 'ADMIN', 'MANAGER', 'MARKETER'] },
  { key: 'attend', label: 'View attendees and scan tickets', permission: 'ticket:scan', always: ['OWNER', 'ADMIN', 'MANAGER', 'CONTRIBUTOR'] },
  { key: 'refund', label: 'Issue and cancel ticket refunds', permission: 'refund:create', always: ['OWNER', 'ADMIN', 'MANAGER'] },
  { key: 'notify', label: 'Send attendee notifications', permission: 'notification:send', always: ['OWNER', 'ADMIN', 'MANAGER'] },
  { key: 'grants', label: 'Assign event access', permission: 'event_access:grant', always: ['OWNER', 'ADMIN', 'MANAGER'] },
  { key: 'media', label: 'Manage the media library', permission: 'media:manage', always: ['OWNER', 'ADMIN', 'MANAGER', 'MARKETER'] },
  { key: 'team.view', label: 'View the team', permission: 'member:read', always: ['OWNER', 'ADMIN', 'MANAGER'] },
];

/** [action, roles that may do it] for event-level roles. */
export const EVENT_CAPABILITIES: Array<[string, EventRole[]]> = [
  ['Edit details, tiers, send notifications', ['EVENT_OWNER', 'EVENT_ADMIN', 'EDITOR']],
  ['View sales', ['EVENT_OWNER', 'EVENT_ADMIN', 'EDITOR', 'VIEWER']],
  ['Issue refunds', ['EVENT_OWNER', 'EVENT_ADMIN']],
  ['Scan tickets', ['EVENT_OWNER', 'EVENT_ADMIN', 'EDITOR', 'CHECK_IN']],
  ['Cancel or delete the event', ['EVENT_OWNER']],
  ['Assign event roles', ['EVENT_OWNER', 'EVENT_ADMIN']],
];

/** Last-seen wording used in the members table. */
export function lastSeen(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '—';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '—';
  const days = Math.floor((now - t) / 86_400_000);
  return days <= 0 ? 'Today' : days === 1 ? 'Yesterday' : `${days} days ago`;
}
