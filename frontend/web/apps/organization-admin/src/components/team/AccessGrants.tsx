'use client';

import { useMemo, useState } from 'react';
import { Button, Card, CardHeader, ConfirmDialog, DataTable, Dialog, Select, TextField } from '@pml.tickets/shared/components/m3';
import { DateRHF, Form, FormActions, SelectRHF, TextAreaRHF, useZodForm } from '@pml.tickets/shared';
import { grantSchema } from './schemas';
import type { AccessGrantRow, EventRole, RosterMember } from '@/lib/api/team';
import { EVENT_CAPABILITIES } from '@/lib/team/roles';
import { useTeamRoles } from '@/lib/team/useTeamRoles';
import { useDefaultChoice } from '@/lib/team/useDefaultChoice';
import { formatEventDate } from '@/lib/format/figure';
import { Status, statusLabel } from '@/components/console/Status';
import { usePaged } from '@/components/finance/usePaged';

export interface GrantInput {
  /** Set when the grant is created from the organization-wide table. */
  eventId?: string;
  userId: string;
  role: EventRole;
  expiresAt: string | null;
  reason: string;
}

export interface AccessGrantsProps {
  grants: AccessGrantRow[];
  members: RosterMember[];
  loading?: boolean;
  /** Viewer may assign event access. */
  canGrant: boolean;
  title?: string;
  subtitle?: string;
  /** Organization-wide mode: shows the Event column, filters and an event choice in the dialog. */
  events?: Array<{ id: string; title: string }>;
  /** Reject to surface a server error on the dialog's form. */
  onGrant: (input: GrantInput) => Promise<unknown> | void;
  onUpdate: (grant: AccessGrantRow, role: EventRole, expiresAt: string | null) => Promise<unknown> | void;
  onRevoke: (grant: AccessGrantRow) => Promise<void> | void;
}

const memberLabel = (m: RosterMember) => `${m.user?.fullName ?? m.user?.username ?? 'Member'} · ${m.role}`;

/** Grants table plus grant/edit dialog for one event. */
export function AccessGrants(p: AccessGrantsProps) {
  const [editing, setEditing] = useState<AccessGrantRow | 'new' | null>(null);
  const [revoking, setRevoking] = useState<AccessGrantRow | null>(null);
  const [q, setQ] = useState('');
  const [ev, setEv] = useState('all');
  const [st, setSt] = useState('all');
  const orgWide = Boolean(p.events);
  const eventTitle = (id: string) => p.events?.find((e) => e.id === id)?.title ?? 'Event';
  const shown = p.grants.filter(
    (g) =>
      (ev === 'all' || g.eventId === ev) &&
      (st === 'all' || g.status === st) &&
      (!q || `${g.user?.fullName ?? ''} ${p.members.find((m) => m.userId === g.userId)?.user?.fullName ?? ''} ${g.reason ?? ''}`.toLowerCase().includes(q.toLowerCase()))
  );
  const paged = usePaged(shown, 5);
  const nameOf = (g: AccessGrantRow) => g.user?.fullName ?? p.members.find((m) => m.userId === g.userId)?.user?.fullName ?? 'Member';

  return (
    <Card>
      <CardHeader
        title={p.title ?? 'Team access'}
        subtitle={p.subtitle ?? 'Give team members an event-specific role, with an optional expiry.'}
        actions={p.canGrant ? <Button variant="tonal" icon="add" onClick={() => setEditing('new')}>Grant access</Button> : null}
      />
      {orgWide ? (
        <div className="m3-toolbar" role="toolbar" aria-label="Grant filters">
          <TextField density="compact" label="Search grants" placeholder="Member or reason" value={q} onChange={(e) => setQ(e.target.value)} />
          <Select density="compact" label="Event" value={ev} onChange={(e) => setEv(e.target.value)}>
            <option value="all">All events</option>
            {p.events!.map((e) => <option key={e.id} value={e.id}>{e.title}</option>)}
          </Select>
          <Select density="compact" label="Status" value={st} onChange={(e) => setSt(e.target.value)}>
            <option value="all">All statuses</option>
            {['ACTIVE', 'EXPIRED', 'REVOKED'].map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </Select>
        </div>
      ) : null}
      <DataTable
        caption="Event access grants"
        loading={p.loading}
        rows={paged.rows}
        getRowId={(g) => g.id}
        pagination={paged.pagination}
        empty={<span>No grants for this event yet.</span>}
        columns={[
          ...(orgWide ? [{ id: 'event', header: 'Event', cell: (g: AccessGrantRow) => eventTitle(g.eventId) }] : []),
          { id: 'member', header: 'Member', rowHeader: !orgWide, cell: (g) => nameOf(g) },
          { id: 'role', header: 'Event role', cell: (g) => statusLabel(g.eventRole) },
          { id: 'reason', header: 'Reason', cell: (g) => g.reason ?? '—' },
          { id: 'status', header: 'Status', cell: (g) => <Status status={g.status} /> },
          { id: 'exp', header: 'Expires', cell: (g) => (g.expiresAt ? formatEventDate(g.expiresAt) : 'No expiry') },
        ]}
        rowActions={(g) =>
          p.canGrant && g.status !== 'REVOKED' ? (
            <div className="m3-row">
              <Button size="sm" variant="outlined" onClick={() => setEditing(g)}>Edit</Button>
              <Button size="sm" variant="outlined" danger onClick={() => setRevoking(g)}>Revoke</Button>
            </div>
          ) : null
        }
      />
      <ConfirmDialog
        open={Boolean(revoking)}
        onClose={() => setRevoking(null)}
        danger
        title="Revoke access?"
        description={revoking ? `${nameOf(revoking)} loses their ${statusLabel(revoking.eventRole)} role on ${orgWide ? eventTitle(revoking.eventId) : 'this event'}.` : ''}
        confirmLabel="Revoke"
        onConfirm={() => {
          const g = revoking;
          setRevoking(null);
          if (g) void p.onRevoke(g);
        }}
      />
      {editing ? (
        <GrantDialog
          events={p.events}
          takenPairs={p.grants.filter((g) => ['ACTIVE', 'SUSPENDED'].includes(g.status)).map((g) => `${g.eventId}:${g.userId}`)}
          grant={editing === 'new' ? null : editing}
          members={p.members.filter((m) => m.status === 'ACTIVE')}
          takenUserIds={p.grants.filter((g) => ['ACTIVE', 'SUSPENDED'].includes(g.status)).map((g) => g.userId)}
          onClose={() => setEditing(null)}
          onSave={(v) => (editing === 'new' ? p.onGrant(v) : p.onUpdate(editing, v.role, v.expiresAt))}
        />
      ) : null}
    </Card>
  );
}

function DefaultRole({ first }: { first: string | undefined }) {
  useDefaultChoice('role', first);
  return null;
}

function GrantDialog({ grant, members, takenUserIds, events, takenPairs, onClose, onSave }: {
  grant: AccessGrantRow | null;
  events?: Array<{ id: string; title: string }>;
  takenPairs?: string[];
  members: RosterMember[];
  takenUserIds: string[];
  onClose: () => void;
  /** Reject to map a server error onto the form. */
  onSave: (v: GrantInput) => Promise<unknown> | void;
}) {
  const roles = useTeamRoles();
  const eventRoles = useMemo(() => roles.grantableEventRoles.map((r) => r.value), [roles.grantableEventRoles]);
  const schema = useMemo(
    () => grantSchema({ eventRoles, editing: Boolean(grant), takenUserIds, withEvent: Boolean(events), takenPairs }),
    [eventRoles, grant, takenUserIds, events, takenPairs],
  );
  const form = useZodForm(schema, {
    defaultValues: {
      eventId: grant?.eventId ?? '',
      userId: grant?.userId ?? '',
      role: grant?.eventRole ?? '',
      expires: grant?.expiresAt?.slice(0, 10) ?? '',
      reason: grant?.reason ?? '',
    },
  });

  return (
    <Dialog open wide onClose={onClose} title={grant ? 'Edit access' : 'Grant event access'}>
      <Form
        form={form}
        aria-label={grant ? 'Edit access' : 'Grant event access'}
        fieldMap={{ eventRole: 'role', expiresAt: 'expires' }}
        onSubmit={async (v) => {
          await onSave({ eventId: v.eventId || undefined, userId: v.userId, role: v.role as EventRole, expiresAt: v.expires ? `${v.expires}T23:59:59Z` : null, reason: v.reason });
          onClose();
        }}
      >
        {events ? (
          <SelectRHF name="eventId" label="Event" disabled={Boolean(grant)} placeholder="Choose an event" options={events.map((e) => ({ value: e.id, label: e.title }))} />
        ) : null}
        <SelectRHF
          name="userId"
          label="Team member"
          disabled={Boolean(grant)}
          placeholder="Choose a member"
          options={members.map((m) => ({ value: m.userId, label: memberLabel(m) }))}
        />
        {/* Least privilege first: the platform lists roles highest first, so the last is the safest default. */}
        <DefaultRole first={eventRoles[eventRoles.length - 1]} />
        <SelectRHF name="role" label="Event role" disabled={roles.loading} options={roles.grantableEventRoles.map((r) => ({ value: r.value, label: r.label }))} helperText={roles.unavailable ? 'Not available yet: the role list could not be loaded' : undefined} />
        <DateRHF name="expires" label="Expires (optional)" />
        <TextAreaRHF name="reason" label="Reason" rows={2} disabled={Boolean(grant)} />
        <p className="m3-muted">
          {EVENT_CAPABILITIES.map(([a, roles]) => (
            <span key={a}>
              <b>{a}:</b> {roles.map(statusLabel).join(', ')}
              <br />
            </span>
          ))}
        </p>
        <FormActions submitLabel={grant ? 'Save' : 'Grant access'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
