'use client';

import { useRouter } from 'next/navigation';
import { Banner, Button, Card, CardHeader, DataTable, EmptyState, ErrorState, KeyValue, Skeleton, StatusPill } from '@pml.tickets/shared/components/m3';
import { useBuyerRefunds, useBuyerTickets, useIdentityUser } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { ModuleFrame, useStaff } from '@/components/console';
import { formatDate, formatDateTime, humanize, maskEmail, money } from '@/lib/format';
import { CardGrid, MaskedValue, RolePills, VerifiedPill } from './common';
import { UserActivity } from './UserActivity';
import { UserBookings } from './UserBookings';
import { useUserActionController } from './useUserActionController';

/** /user/[id]: account, actions, buyer history (tickets, refunds) and activity. */
export function UserProfilePage({ id }: { id: string }) {
  const router = useRouter();
  const staff = useStaff();
  const { user, loading, error, refetch } = useIdentityUser(id);
  const tickets = useBuyerTickets(user ? id : null);
  const refunds = useBuyerRefunds(user ? id : null);
  const ctl = useUserActionController();
  const back = () => router.push('/users/users');

  if (loading && !user) {
    return (
      <ModuleFrame module="users" title="User" onBack={back}>
        <Skeleton width="100%" />
      </ModuleFrame>
    );
  }
  if (!user) {
    return (
      <ModuleFrame module="users" title="User" onBack={back}>
        {error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <Card>
            <EmptyState icon="user" title="User not found" description="This account does not exist or was removed." action={<Button variant="filled" onClick={back}>All users</Button>} />
          </Card>
        )}
      </ModuleFrame>
    );
  }

  const actions = staff.can('users') ? ctl.actionsFor(user) : [];
  const orgs = (user.organizationMemberships ?? []).map((m) => m.organization?.name).filter(Boolean);
  return (
    <ModuleFrame
      module="users"
      title={user.fullName}
      subtitle={maskEmail(user.email)}
      onBack={back}
      actions={
        <Button variant="outlined" onClick={back}>
          All users
        </Button>
      }
    >
      <div className="m3-stack">
        <CardGrid>
          <Card>
            <CardHeader title="Account" subtitle={<RolePills roles={user.roles} />} actions={<StatusPill status={user.accountStatus} />} />
            <KeyValue columns
              items={[
                { label: 'Username', value: <span className="m3-mono">{user.username ?? '—'}</span> },
                { label: 'Email', value: <span className="m3-row"><MaskedValue kind="email" value={user.email} label="email" /><VerifiedPill verified={user.emailVerified} /></span> },
                { label: 'Phone', value: <span className="m3-row"><MaskedValue kind="phone" value={user.phoneNumber} label="phone" /><VerifiedPill verified={user.phoneVerified} /></span> },
                { label: 'Two-step verification', value: user.twoFactorEnabled ? 'On' : 'Off' },
                { label: 'Joined', value: formatDate(user.createdAt) },
                { label: 'Last sign-in', value: formatDateTime(user.lastLoginAt) },
                { label: 'Organizations', value: orgs.length ? orgs.join(', ') : '—' },
              ]}
            />
            {user.accountStatus === 'SUSPENDED' ? (
              <Banner tone="error" title="Suspended">
                {user.suspendReason || 'This account is suspended and cannot sign in or buy.'}
              </Banner>
            ) : null}
            {user.accountStatus === 'LOCKED' ? (
              <Banner tone="error" title="Locked">
                {user.lockReason || 'This account is locked and cannot sign in.'}
              </Banner>
            ) : null}
          </Card>
          <Card>
            <CardHeader title="Account actions" subtitle="Every action is written to the audit log" />
            {actions.length ? (
              <div className="m3-row">
                {actions.map((a) => (
                  <Button key={a.id} variant="tonal" size="sm" danger={a.danger} disabled={a.disabled} title={a.hint} onClick={() => ctl.run(a.id, user)}>
                    {a.label}
                  </Button>
                ))}
              </div>
            ) : (
              <p className="m3-muted">No actions available for your role.</p>
            )}
          </Card>
        </CardGrid>

        <Card>
          <CardHeader title="Contacts" subtitle="Masked by the server; reveal a value from the account card" />
          {user.contacts && user.contacts.length ? (
            <DataTable
              caption="Contacts"
              rows={user.contacts}
              getRowId={(c) => c.id}
              columns={[
                { id: 'type', header: 'Channel', rowHeader: true, cell: (c) => humanize(c.type) },
                { id: 'value', header: 'Contact', cell: (c) => <span className="m3-mono">{c.valueMasked}</span> },
                { id: 'primary', header: 'Primary', cell: (c) => (c.primary ? 'Yes' : 'No') },
                { id: 'verified', header: 'Verified', cell: (c) => <VerifiedPill verified={Boolean(c.verifiedAt)} /> },
              ]}
            />
          ) : (
            <p className="m3-muted">No contacts on file.</p>
          )}
        </Card>

        <Card>
          <CardHeader title="Bookings" subtitle="Recent purchases" />
          <UserBookings buyerId={id} />
        </Card>

        <Card>
          <CardHeader title="Tickets" />
          <DataTable
            caption="Tickets"
            loading={tickets.loading && tickets.tickets.length === 0}
            error={tickets.error && tickets.tickets.length === 0 ? <ErrorState error={tickets.error} onRetry={tickets.refetch} /> : undefined}
            empty={<EmptyState icon="ticket" title="No tickets" description="This account has not bought any tickets." />}
            rows={tickets.tickets}
            getRowId={(t) => t.id}
            columns={[
              { id: 'no', header: 'Ticket number', rowHeader: true, cell: (t) => <span className="m3-mono">{t.ticketNumber}</span> },
              { id: 'event', header: 'Event', cell: (t) => t.eventTitle },
              { id: 'tier', header: 'Tier', cell: (t) => t.ticketCategoryName ?? '—' },
              { id: 'price', header: 'Price', align: 'end', cell: (t) => <span className="m3-mono">{money(t.price)}</span> },
              { id: 'status', header: 'Status', cell: (t) => <StatusPill status={t.status} /> },
              { id: 'date', header: 'Purchased', cell: (t) => formatDate(t.purchaseDate) },
            ]}
          />
        </Card>

        <Card>
          <CardHeader title="Refund requests" />
          <DataTable
            caption="Refund requests"
            loading={refunds.loading && refunds.refunds.length === 0}
            error={refunds.error && refunds.refunds.length === 0 ? <ErrorState error={refunds.error} onRetry={refunds.refetch} /> : undefined}
            empty={<EmptyState icon="receipt" title="No refund requests" />}
            rows={refunds.refunds}
            getRowId={(r) => r.id}
            columns={[
              { id: 'req', header: 'Request', rowHeader: true, cell: (r) => <span className="m3-mono">{r.requestId}</span> },
              { id: 'ticket', header: 'Ticket', cell: (r) => <span className="m3-mono">{r.ticketNumber}</span> },
              { id: 'amount', header: 'Amount', align: 'end', cell: (r) => <span className="m3-mono">{money(r.refundAmount)}</span> },
              { id: 'status', header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
              { id: 'date', header: 'Requested', cell: (r) => formatDate(r.requestedAt) },
            ]}
          />
        </Card>

        <Card>
          <CardHeader title="Activity" />
          <UserActivity resourceId={id} />
        </Card>
      </div>
      {ctl.dialogs}
    </ModuleFrame>
  );
}
