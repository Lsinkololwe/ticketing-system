'use client';

import { useMemo, useState } from 'react';
import {
  Avatar,
  Card,
  ConfirmDialog,
  DataTable,
  Dialog,
  RowMenu,
  Select,
  TextField,
} from '@pml.tickets/shared/components/m3';
import { Form, FormActions, SelectRHF, useZodForm } from '@pml.tickets/shared';
import type { OrgRole, RosterMember } from '@/lib/api/team';
import { permissionsSchema, roleSchema, type PermSetting } from './schemas';
import { CAPABILITIES, lastSeen } from '@/lib/team/roles';
import { useTeamRoles } from '@/lib/team/useTeamRoles';
import { formatEventDate } from '@/lib/format/figure';
import { Status, statusLabel } from '@/components/console/Status';
import { usePaged } from '@/components/finance/usePaged';

export interface MembersTabProps {
  members: RosterMember[];
  currentUserId: string | null;
  /** The viewer may change roles and manage members. */
  canManage: boolean;
  onChangeRole: (member: RosterMember, role: OrgRole, custom: string[], denied: string[]) => Promise<unknown> | void;
  onSuspend: (member: RosterMember) => Promise<void> | void;
  onReactivate: (member: RosterMember) => Promise<void> | void;
  onRemove: (member: RosterMember) => Promise<void> | void;
}

const memberName = (m: RosterMember) => m.user?.fullName ?? m.user?.username ?? 'Member';

export function MembersTab(p: MembersTabProps) {
  const roles = useTeamRoles();
  const [q, setQ] = useState('');
  const [role, setRole] = useState('all');
  const [status, setStatus] = useState('all');
  const [roleFor, setRoleFor] = useState<RosterMember | null>(null);
  const [permFor, setPermFor] = useState<RosterMember | null>(null);
  const [removeFor, setRemoveFor] = useState<RosterMember | null>(null);

  const list = useMemo(
    () =>
      p.members.filter(
        (m) =>
          (role === 'all' || m.role === role) &&
          (status === 'all' || m.status === status) &&
          (!q || `${memberName(m)} ${m.user?.username ?? ''}`.toLowerCase().includes(q.toLowerCase()))
      ),
    [p.members, q, role, status]
  );
  const paged = usePaged(list, 5);
  const manageable = (m: RosterMember) => p.canManage && m.role !== 'OWNER' && m.userId !== p.currentUserId;

  return (
    <Card>
      <div className="m3-toolbar">
        <TextField density="compact" label="Search members" placeholder="Name" value={q} onChange={(e) => { setQ(e.target.value); paged.reset(); }} />
        <Select density="compact" label="Role" value={role} onChange={(e) => { setRole(e.target.value); paged.reset(); }}>
          <option value="all">All roles</option>
          {roles.orgRoles.map((r) => <option key={r.value} value={r.value}>{r.value}</option>)}
        </Select>
        <Select density="compact" label="Status" value={status} onChange={(e) => { setStatus(e.target.value); paged.reset(); }}>
          <option value="all">All statuses</option>
          {['ACTIVE', 'INACTIVE', 'SUSPENDED', 'REMOVED'].map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
        </Select>
      </div>
      <DataTable
        caption="Team members"
        rows={paged.rows}
        getRowId={(m) => m.id}
        pagination={paged.pagination}
        empty={<span>No members match.</span>}
        columns={[
          {
            id: 'member',
            header: 'Member',
            rowHeader: true,
            cell: (m) => (
              <div className="m3-row">
                <Avatar name={memberName(m)} size="sm" />
                <div>
                  <b>{memberName(m)}</b>
                  {m.userId === p.currentUserId ? <> <span className="m3-pill">You</span></> : null}
                  {m.user?.username ? <div className="m3-muted">{m.user.username}</div> : null}
                </div>
              </div>
            ),
          },
          {
            id: 'role',
            header: 'Role',
            cell: (m) => (
              <>
                <b>{m.role}</b>
                {m.customPermissions?.length || m.deniedPermissions?.length ? <> <span className="m3-pill">Custom</span></> : null}
              </>
            ),
          },
          { id: 'status', header: 'Status', cell: (m) => <Status status={m.status} /> },
          { id: 'joined', header: 'Joined', cell: (m) => (m.joinedAt ? formatEventDate(m.joinedAt) : '—') },
          { id: 'last', header: 'Last active', cell: (m) => lastSeen(m.lastActiveAt) },
        ]}
        rowActions={(m) =>
          manageable(m) ? (
            <RowMenu
              label={`Actions for ${memberName(m)}`}
              items={[
                { id: 'role', label: 'Change role', onSelect: () => setRoleFor(m) },
                { id: 'perm', label: 'Custom permissions', onSelect: () => setPermFor(m) },
                m.status === 'SUSPENDED'
                  ? { id: 'react', label: 'Reactivate', onSelect: () => void p.onReactivate(m) }
                  : { id: 'susp', label: 'Suspend', onSelect: () => void p.onSuspend(m) },
                { id: 'rm', label: 'Remove from team', danger: true, onSelect: () => setRemoveFor(m) },
              ]}
            />
          ) : (
            <span className="m3-muted">{m.role === 'OWNER' ? 'Owner' : '—'}</span>
          )
        }
      />
      {roleFor ? <RoleDialog member={roleFor} onClose={() => setRoleFor(null)} onSave={(r) => p.onChangeRole(roleFor, r, roleFor.customPermissions ?? [], roleFor.deniedPermissions ?? [])} /> : null}
      {permFor ? <PermissionsDialog member={permFor} onClose={() => setPermFor(null)} onSave={(c, d) => p.onChangeRole(permFor, permFor.role, c, d)} /> : null}
      {removeFor ? (
        <ConfirmDialog
          open
          danger
          title={`Remove ${memberName(removeFor)}?`}
          description="They lose access to the organization immediately. You can invite them again later."
          confirmLabel="Remove from team"
          onClose={() => setRemoveFor(null)}
          onConfirm={async () => { const m = removeFor; setRemoveFor(null); await p.onRemove(m); }}
        />
      ) : null}
    </Card>
  );
}

function RoleDialog({ member, onClose, onSave }: { member: RosterMember; onClose: () => void; onSave: (r: OrgRole) => Promise<unknown> | void }) {
  const roles = useTeamRoles();
  const schema = useMemo(() => roleSchema(roles.invitableRoles.map((r) => r.value)), [roles.invitableRoles]);
  // The owner role moves only through transfer: an owner's dialog starts empty and the person chooses.
  const form = useZodForm(schema, { defaultValues: { role: member.role === 'OWNER' ? '' : member.role } });
  const role = form.watch('role');
  return (
    <Dialog open onClose={onClose} title="Change role">
      <Form form={form} aria-label="Change role" onSubmit={async (v) => { await onSave(v.role as OrgRole); onClose(); }}>
        <p>
          <b>{memberName(member)}</b> · now <b>{member.role}</b>
        </p>
        <SelectRHF name="role" label="New role" placeholder="Choose a role" disabled={roles.loading} options={roles.invitableRoles.map((r) => ({ value: r.value, label: r.value }))} helperText={roles.unavailable ? 'Not available yet: the role list could not be loaded' : undefined} />
        <p className="m3-muted">{roles.describe(role)}</p>
        <p className="m3-muted">The owner role moves only through ownership transfer.</p>
        <FormActions submitLabel="Save role" onCancel={onClose} />
      </Form>
    </Dialog>
  );
}

function PermissionsDialog({ member, onClose, onSave }: { member: RosterMember; onClose: () => void; onSave: (custom: string[], denied: string[]) => Promise<unknown> | void }) {
  const form = useZodForm(permissionsSchema, {
    defaultValues: {
      perms: CAPABILITIES.map<PermSetting>((c) =>
        member.customPermissions?.includes(c.permission) ? 'allow' : member.deniedPermissions?.includes(c.permission) ? 'deny' : ''
      ),
    },
  });
  return (
    <Dialog open wide onClose={onClose} title="Custom permissions">
      <Form
        form={form}
        aria-label="Custom permissions"
        onSubmit={async ({ perms }) => {
          const pick = (v: PermSetting) => CAPABILITIES.filter((_, i) => perms[i] === v).map((c) => c.permission);
          await onSave(pick('allow'), pick('deny'));
          onClose();
        }}
      >
        <p>
          <b>{memberName(member)}</b> keeps the {member.role} role. Add or remove individual permissions below.
        </p>
        <div className="m3-stack">
          {CAPABILITIES.map((c, i) => (
            <div key={c.key} className="oc-spread">
              <div>
                {c.label}
                <div className="m3-muted m3-mono">{c.permission}</div>
              </div>
              <SelectRHF
                name={`perms.${i}`}
                label={c.label}
                options={[
                  { value: '', label: 'Role default' },
                  { value: 'allow', label: 'Allow' },
                  { value: 'deny', label: 'Deny' },
                ]}
              />
            </div>
          ))}
        </div>
        <FormActions submitLabel="Save" onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
