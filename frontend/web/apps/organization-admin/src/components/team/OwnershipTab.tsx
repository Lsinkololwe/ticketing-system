'use client';

import { useState, type ReactNode } from 'react';
import { Avatar, Banner, Button, Card, CardHeader, ConfirmDialog } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, SelectRHF, useZodForm } from '@pml.tickets/shared';
import type { OwnershipTransferRow, RosterMember } from '@/lib/api/team';
import { formatEventDate } from '@/lib/format/figure';
import { Status } from '@/components/console/Status';
import { transferSchema } from './schemas';

export interface OwnershipTabProps {
  ownerName: string;
  orgName: string;
  /** Viewer is the owner. */
  isOwner: boolean;
  /** Active admins who can be nominated. */
  admins: RosterMember[];
  transfer: OwnershipTransferRow | null;
  /** Reject to map a server error onto the form. */
  onStart: (newOwnerId: string) => Promise<unknown> | void;
  onCancel: () => Promise<void> | void;
  onLeave: () => Promise<void> | void;
  /** Offers made to the viewer (they are the nominee). */
  incoming?: ReactNode;
}

const nameOf = (m: RosterMember) => m.user?.fullName ?? m.user?.username ?? 'Admin';

export function OwnershipTab(p: OwnershipTabProps) {
  const [leave, setLeave] = useState(false);
  const pending = p.transfer?.status === 'PENDING';
  const form = useZodForm(transferSchema, { defaultValues: { newOwnerId: p.admins[0]?.userId ?? '' } });

  return (
    <div className="m3-stack">
      <Card>
        <CardHeader title="Organization owner" />
        <div className="m3-row">
          <Avatar name={p.ownerName} />
          <div>
            <b>{p.ownerName}</b>
            <div className="m3-muted">Exactly one owner at a time</div>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader title={pending ? 'Ownership transfer pending' : 'Transfer ownership'} />
        {!p.isOwner ? (
          <p className="m3-muted">Only the owner can transfer ownership.</p>
        ) : pending && p.transfer ? (
          <div className="m3-stack">
            <p>
              To <b>{p.transfer.newOwner?.fullName ?? 'the nominated admin'}</b>. Expires {formatEventDate(p.transfer.expiresAt)}. They receive a link and a one-time code on their verified phone, and the transfer completes when they enter it.
            </p>
            <div className="m3-row">
              <Button variant="outlined" danger onClick={() => void p.onCancel()}>Cancel transfer</Button>
            </div>
          </div>
        ) : (
          <div className="m3-stack">
            <p className="m3-muted">
              You become an admin. The new owner takes over billing, payouts and deleting the organization. Only an active admin can be nominated.
            </p>
            {p.admins.length ? (
              <>
                <Form form={form} aria-label="Transfer ownership" guardLeave={false} fieldMap={{ newOwnerId: 'newOwnerId' }} onSubmit={async (v) => { await p.onStart(v.newOwnerId); }}>
                  <SelectRHF name="newOwnerId" label="New owner" placeholder="Choose an admin" options={p.admins.map((a) => ({ value: a.userId, label: nameOf(a) }))} />
                  <FormActions submitLabel="Start transfer" align="start" />
                </Form>
              </>
            ) : (
              <Banner tone="info">Promote someone to ADMIN first, then come back here.</Banner>
            )}
            {p.transfer && p.transfer.status !== 'PENDING' ? (
              <p className="m3-muted">
                Last transfer: <Status status={p.transfer.status} />
              </p>
            ) : null}
          </div>
        )}
      </Card>

      {p.incoming}
      <Card>
        <CardHeader title="Leave organization" />
        <p className="m3-muted">
          {p.isOwner ? 'The owner cannot leave. Transfer ownership first.' : `You lose access to ${p.orgName} immediately. The owner can invite you again.`}
        </p>
        {p.isOwner ? null : <Button variant="outlined" danger onClick={() => setLeave(true)}>Leave organization</Button>}
      </Card>
      {leave ? (
        <ConfirmDialog
          open
          danger
          title={`Leave ${p.orgName}?`}
          description="You lose access to events, bookings and finance right away."
          confirmLabel="Leave organization"
          onClose={() => setLeave(false)}
          onConfirm={async () => { setLeave(false); await p.onLeave(); }}
        />
      ) : null}
    </div>
  );
}
