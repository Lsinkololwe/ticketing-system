'use client';

import { useMemo, useState } from 'react';
import { Banner, Button, Card, CardHeader, DataTable, Dialog, Select, TextField } from '@pml.tickets/shared/components/m3';
import { CheckboxRHF, Form, FormActions, SelectRHF, TextAreaRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import { inviteBulkSchema, inviteOneSchema, type InviteRequest } from './schemas';
import type { InvitationRow } from '@/lib/api/team';
import { useTeamRoles } from '@/lib/team/useTeamRoles';
import { useDefaultChoice } from '@/lib/team/useDefaultChoice';
import { formatEventDate } from '@/lib/format/figure';
import { Status, statusLabel } from '@/components/console/Status';
import { usePaged } from '@/components/finance/usePaged';

export type { InvitePerson, InviteRequest } from './schemas';
export { parseBulkInvites } from './schemas';

export interface InvitationsTabProps {
  invitations: InvitationRow[];
  events: Array<{ id: string; title: string }>;
  /** Organization is approved. */
  orgActive: boolean;
  /** Viewer may invite (owner and admins). */
  canInvite: boolean;
  /** Resolve when sent; reject (server error) to show it on the dialog's form. */
  onInvite: (req: InviteRequest) => Promise<unknown> | void;
  onResend: (id: string) => Promise<void> | void;
  onRevoke: (id: string) => Promise<void> | void;
  /** Open the invite dialog on mount (from /team/invite). */
  startOpen?: boolean;
}

export function InvitationsTab(p: InvitationsTabProps) {
  const [q, setQ] = useState('');
  const [st, setSt] = useState('all');
  const [dialog, setDialog] = useState<'one' | 'bulk' | null>(p.startOpen && p.canInvite && p.orgActive ? 'one' : null);
  const list = useMemo(
    () =>
      p.invitations.filter(
        (i) => (st === 'all' || i.status === st) && (!q || `${i.inviteeName ?? ''} ${i.email ?? ''} ${i.phoneNumber ?? ''}`.toLowerCase().includes(q.toLowerCase()))
      ),
    [p.invitations, q, st]
  );
  const paged = usePaged(list, 5);
  const ok = p.canInvite && p.orgActive;
  const eventTitle = (id: string) => p.events.find((e) => e.id === id)?.title ?? 'Event';

  return (
    <>
      {!p.orgActive ? <Banner tone="warning">Team invitations unlock once your organization is approved.</Banner> : !p.canInvite ? <Banner tone="info">Only the owner and admins can invite people.</Banner> : null}
      <Card>
        <CardHeader
          title="Invitations"
          subtitle="Links expire after 7 days. People accept with the email or WhatsApp number you invited."
          actions={
            ok ? (
              <>
                <Button variant="tonal" onClick={() => setDialog('one')}>Invite one person</Button>
                <Button variant="outlined" onClick={() => setDialog('bulk')}>Invite several</Button>
              </>
            ) : null
          }
        />
        <div className="m3-toolbar">
          <TextField density="compact" label="Search invitations" placeholder="Name, email or phone" value={q} onChange={(e) => { setQ(e.target.value); paged.reset(); }} />
          <Select density="compact" label="Status" value={st} onChange={(e) => { setSt(e.target.value); paged.reset(); }}>
            <option value="all">All statuses</option>
            {['PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'REVOKED'].map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </Select>
        </div>
        <DataTable
          caption="Invitations"
          rows={paged.rows}
          getRowId={(i) => i.id}
          pagination={paged.pagination}
          empty={<span>No invitations. Invite someone to get started.</span>}
          columns={[
            {
              id: 'who',
              header: 'Invitee',
              rowHeader: true,
              cell: (i) => (
                <>
                  <b>{i.inviteeName ?? i.email ?? i.phoneNumber}</b>
                  {i.inviteeName ? <div className="m3-muted">{i.email ?? `${i.phoneNumber} (WhatsApp)`}</div> : null}
                </>
              ),
            },
            { id: 'role', header: 'Role', cell: (i) => i.proposedRole },
            {
              id: 'grants',
              header: 'Event access',
              cell: (i) => (i.eventAccessGrants?.length ? i.eventAccessGrants.map((g) => <div key={g.eventId}>{eventTitle(g.eventId)} · {statusLabel(g.role)}</div>) : '—'),
            },
            { id: 'status', header: 'Status', cell: (i) => <Status status={i.status} /> },
            { id: 'sent', header: 'Sent', cell: (i) => formatEventDate(i.createdAt) },
            { id: 'exp', header: 'Expires', cell: (i) => (i.status === 'PENDING' ? formatEventDate(i.expiresAt) : '—') },
          ]}
          rowActions={(i) =>
            ok && ['PENDING', 'EXPIRED'].includes(i.status) ? (
              <div className="m3-row">
                <Button size="sm" variant="outlined" onClick={() => void p.onResend(i.id)}>Resend</Button>
                {i.status === 'PENDING' ? <Button size="sm" variant="text" onClick={() => void p.onRevoke(i.id)}>Revoke</Button> : null}
              </div>
            ) : null
          }
        />
      </Card>
      {dialog ? (
        <InviteDialog bulk={dialog === 'bulk'} events={p.events} onClose={() => setDialog(null)} onSend={p.onInvite} />
      ) : null}
    </>
  );
}

function FirstRole({ name, first }: { name: string; first: string | undefined }) {
  useDefaultChoice(name, first);
  return null;
}

function InviteDialog({ bulk, events, onClose, onSend }: {
  bulk: boolean;
  events: Array<{ id: string; title: string }>;
  onClose: () => void;
  /** Reject to map a server error onto the form. */
  onSend: (r: InviteRequest) => Promise<unknown> | void;
}) {
  // The two variants are fixed per mount, so each uses its own schema and defaults.
  const roles = useTeamRoles();
  const codes = useMemo(
    () => ({ org: roles.invitableRoles.map((r) => r.value), event: roles.grantableEventRoles.map((r) => r.value) }),
    [roles.invitableRoles, roles.grantableEventRoles],
  );
  // The platform's least-privileged listed roles are chosen once its lists arrive; there is no built-in default.
  const shared = {
    role: '',
    message: '',
    picked: Object.fromEntries(events.map((e) => [e.id, false])),
    grantRole: '',
  };
  const form = useZodForm(bulk ? inviteBulkSchema(codes) : inviteOneSchema(codes), {
    defaultValues: bulk ? { list: '', ...shared } : { name: '', email: '', phone: '', ...shared },
  } as never) as unknown as ReturnType<typeof useZodForm<ReturnType<typeof inviteOneSchema>>>;
  const role = (form.watch('role') ?? shared.role) as string;

  return (
    <Dialog open wide onClose={onClose} title={bulk ? 'Invite several people' : 'Invite a team member'}>
      <Form
        form={form}
        aria-label={bulk ? 'Invite several people' : 'Invite a team member'}
        fieldMap={{ inviteeName: 'name', proposedRole: 'role' }}
        onSubmit={async (v) => {
          await onSend(v);
          onClose();
        }}
      >
        {bulk ? (
          <TextAreaRHF
            name={'list' as never}
            label="People, one per line"
            rows={5}
            helperText="Format: name, email or WhatsApp number. For example: Chanda Mwansa, chanda@example.com or Chanda Mwansa, 0971234567"
          />
        ) : (
          <>
            <TextFieldRHF name="name" label="Name" />
            <TextFieldRHF name="email" label="Email" type="email" helperText="Email or WhatsApp number, or both." />
            <TextFieldRHF name="phone" label="WhatsApp number" inputMode="tel" placeholder="+260 97 123 4567" helperText="Phone-only invitations are sent by WhatsApp." />
          </>
        )}
        {/* Least privilege first: the platform lists roles highest first, so the last is the safest default. */}
        <FirstRole name="role" first={codes.org[codes.org.length - 1]} />
        <FirstRole name="grantRole" first={codes.event[codes.event.length - 1]} />
        <SelectRHF name="role" label="Role" disabled={roles.loading} options={roles.invitableRoles.map((r) => ({ value: r.value, label: r.value }))} helperText={roles.unavailable ? 'Not available yet: the role list could not be loaded' : undefined} />
        <p className="m3-muted">{roles.describe(role)}</p>
        <TextAreaRHF name="message" label="Message (optional)" rows={2} />
        {events.length ? (
          <fieldset className="m3-stack">
            <legend>Event access (optional)</legend>
            {events.map((e) => (
              <CheckboxRHF key={e.id} name={`picked.${e.id}` as never} label={e.title} />
            ))}
            <SelectRHF name="grantRole" label="Event role for ticked events" options={roles.grantableEventRoles.map((r) => ({ value: r.value, label: r.label }))} />
          </fieldset>
        ) : null}
        <FormActions submitLabel={bulk ? 'Send invitations' : 'Send invitation'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
