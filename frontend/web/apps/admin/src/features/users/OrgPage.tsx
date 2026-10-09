'use client';

import { ownerLabel } from '@/lib/ownerLabel';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Banner, Button, Card, CardHeader, ConfirmDialog, DataTable, EmptyState, ErrorState, KeyValue, Skeleton, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import {
  useIdentityOrganization, useOrgAdminActions, useOrgAdminDocuments, useOrgAdminEvents, useOrgAdminMembers,
  type AdminOrgRecord,
} from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { ModuleFrame, Tiles, useStaff } from '@/components/console';
import { formatDate, formatNumber, humanize } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { CardGrid, MaskedValue, errorMessage, orgRateText } from './common';
import { useOrgActionController } from './useOrgActionController';

function PayoutAccount({ org }: { org: AdminOrgRecord }) {
  const staff = useStaff();
  const api = useOrgAdminActions();
  const snack = useSnackbar();
  const [ask, setAsk] = useState<'reject' | 'revoke' | null>(null);
  const [busy, setBusy] = useState(false);
  const cfg = org.payoutConfig;
  const bank = cfg?.bankAccount;
  const momo = cfg?.mobileMoneyAccount;
  const configured = Boolean(cfg?.isConfigured && (bank || momo));
  const decide = staff.can('payoutDecide');

  const set = async (verified: boolean, message: string) => {
    setBusy(true);
    try {
      await api.verifyPayoutAccount(org.id, verified);
      setAsk(null);
      snack.show(message);
    } catch (e) {
      snack.show({ message: errorMessage(e), tone: 'error' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Payout account" subtitle="Verification is required before payouts can be approved" />
      {configured ? (
        <div className="m3-row" style={{ justifyContent: 'space-between' }}>
          <span>
            {bank ? (
              <>
                <strong>{bank.bankName}</strong> <span className="m3-mono">{bank.maskedAccountNumber}</span>
                <br />
                <span className="m3-muted">{[bank.accountHolderName, humanize(bank.accountType)].filter(Boolean).join(' · ')}</span>
              </>
            ) : (
              <>
                <strong>{humanize(momo?.provider)}</strong> <span className="m3-mono">{momo?.maskedPhoneNumber}</span>
                <br />
                <span className="m3-muted">{momo?.accountHolderName}</span>
              </>
            )}
          </span>
          <span className="m3-row">
            <StatusPill status={org.payoutAccountVerified ? 'VERIFIED' : 'PENDING_VERIFICATION'} />
            {decide && !org.payoutAccountVerified ? (
              <>
                <Button variant="tonal" size="sm" loading={busy} onClick={() => set(true, 'Payout account verified')}>
                  Verify
                </Button>
                <Button variant="tonal" size="sm" danger onClick={() => setAsk('reject')}>
                  Reject
                </Button>
              </>
            ) : null}
            {decide && org.payoutAccountVerified ? (
              <Button variant="text" size="sm" danger onClick={() => setAsk('revoke')}>
                Revoke verification
              </Button>
            ) : null}
          </span>
        </div>
      ) : (
        <p className="m3-muted">No payout account on file.</p>
      )}
      {!decide && configured ? <p className="m3-muted">{needText('payoutDecide')}</p> : null}
      <ConfirmDialog
        open={ask !== null}
        onClose={() => setAsk(null)}
        title={ask === 'revoke' ? 'Revoke payout verification?' : 'Reject this payout account?'}
        description="Payouts to this account are blocked until it is verified again."
        confirmLabel={ask === 'revoke' ? 'Revoke verification' : 'Reject account'}
        danger
        loading={busy}
        onConfirm={() => set(false, ask === 'revoke' ? 'Payout verification revoked' : 'Payout account rejected')}
      />
    </Card>
  );
}

/** /org/[id]: organization, actions, payout account, team, events, documents, commission. */
export function OrgPage({ id }: { id: string }) {
  const router = useRouter();
  const staff = useStaff();
  const { organization: org, loading, error, refetch } = useIdentityOrganization(id);
  const members = useOrgAdminMembers(org ? id : null);
  const docs = useOrgAdminDocuments(org ? id : null);
  const events = useOrgAdminEvents(org?.ownerId ?? null);
  const ctl = useOrgActionController();
  const back = () => router.push('/users/orgs');

  if (loading && !org) {
    return (
      <ModuleFrame module="users" title="Organization" onBack={back}>
        <Skeleton width="100%" />
      </ModuleFrame>
    );
  }
  if (!org) {
    return (
      <ModuleFrame module="users" title="Organization" onBack={back}>
        {error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <Card>
            <EmptyState icon="building" title="Organization not found" action={<Button variant="filled" onClick={back}>All organizations</Button>} />
          </Card>
        )}
      </ModuleFrame>
    );
  }

  const manage = staff.can('orgs');
  const place = [org.businessAddress?.city, org.businessAddress?.province].filter(Boolean).join(', ');
  return (
    <ModuleFrame
      module="users"
      title={org.name}
      subtitle={`${humanize(org.type)} · ${org.businessAddress?.city ?? '—'}`}
      onBack={back}
      actions={
        <Button variant="outlined" onClick={back}>
          All organizations
        </Button>
      }
    >
      <div className="m3-stack">
        <Tiles
          label="Organization summary"
          items={[
            { id: 'status', label: 'Status', value: <StatusPill status={org.status} /> },
            { id: 'kyb', label: 'KYB', value: <StatusPill status={org.kybStatus}>{humanize(org.kybStatus)}</StatusPill> },
            { id: 'commission', label: 'Commission', value: orgRateText(org) },
            { id: 'events', label: 'Events', value: events.loading && events.events.length === 0 ? '…' : formatNumber(events.events.length) },
            { id: 'team', label: 'Team members', value: members.loading && !members.members.length ? '…' : formatNumber(members.members.length) },
          ]}
        />
        <CardGrid>
          <Card>
            <CardHeader
              title="Organization"
              subtitle={org.slug}
              actions={
                <span className="m3-row">
                  <StatusPill status={org.status} />
                  <StatusPill status={org.kybStatus}>KYB: {humanize(org.kybStatus)}</StatusPill>
                </span>
              }
            />
            {org.status === 'SUSPENDED' ? (
              <Banner tone="error" title="Suspended">
                {org.suspensionReason || org.rejectionReason || 'Events are hidden and payouts are blocked.'}
              </Banner>
            ) : null}
            <KeyValue columns
              items={[
                { label: 'Owner', value: ownerLabel(org) },
                { label: 'Email', value: <MaskedValue kind="email" value={org.businessEmail} label="organization email" /> },
                { label: 'Phone', value: <MaskedValue kind="phone" value={org.businessPhone} label="organization phone" /> },
                { label: 'Address', value: place || '—' },
                { label: 'TPIN', value: <span className="m3-mono">{org.taxId ?? '—'}</span> },
                { label: 'Registration', value: <span className="m3-mono">{org.businessRegistrationNumber ?? '—'}</span> },
                { label: 'Documents', value: org.documentsVerified ? 'All verified' : 'Not fully verified' },
                { label: 'Joined', value: formatDate(org.createdAt) },
              ]}
            />
          </Card>
          <Card>
            <CardHeader title="Actions" />
            {manage ? (
              <div className="m3-row">
                {org.status === 'SUSPENDED' ? (
                  <Button variant="tonal" size="sm" onClick={() => ctl.run('unsuspend', org)}>
                    Unsuspend
                  </Button>
                ) : null}
                {org.status === 'ACTIVE' ? (
                  <Button variant="tonal" size="sm" danger onClick={() => ctl.run('suspend', org)}>
                    Suspend…
                  </Button>
                ) : null}
                <Button variant="tonal" size="sm" onClick={() => ctl.run('status', org)}>
                  Update status
                </Button>
                <Button variant="tonal" size="sm" onClick={() => ctl.run('commission', org)}>
                  Commission override
                </Button>
                {org.status === 'PENDING_REVIEW' && staff.can('decide') ? (
                  <Button variant="filled" size="sm" onClick={() => router.push('/approvals/orgs')}>
                    Review application
                  </Button>
                ) : null}
              </div>
            ) : (
              <p className="m3-muted">{needText('orgs')}</p>
            )}
            <KeyValue items={[{ label: 'Commission rate', value: orgRateText(org) }]} />
          </Card>
        </CardGrid>

        <PayoutAccount org={org} />

        <Card>
          <CardHeader title="Team" subtitle="Members of this organization (read only)" />
          <DataTable
            caption="Team"
            loading={members.loading && members.members.length === 0}
            error={members.error && members.members.length === 0 ? <ErrorState error={members.error} onRetry={members.refetch} /> : undefined}
            empty={<EmptyState icon="users" title="No team members." />}
            rows={members.members}
            getRowId={(m) => m.id}
            columns={[
              { id: 'member', header: 'Member', rowHeader: true, cell: (m) => <strong>{m.user?.fullName ?? m.userId}</strong> },
              { id: 'role', header: 'Role', cell: (m) => humanize(m.role) },
              { id: 'status', header: 'Status', cell: (m) => <StatusPill status={m.status} /> },
            ]}
          />
        </Card>

        <Card>
          <CardHeader title="Events" subtitle="Events created by this organization" />
          <DataTable
            caption="Events"
            loading={events.loading && events.events.length === 0}
            error={events.error && events.events.length === 0 ? <ErrorState error={events.error} onRetry={events.refetch} /> : undefined}
            empty={<EmptyState icon="calendar" title="No events yet." />}
            rows={events.events}
            getRowId={(e) => e.id}
            columns={[
              { id: 'event', header: 'Event', rowHeader: true, cell: (e) => <strong>{e.title}</strong> },
              { id: 'date', header: 'Date', cell: (e) => formatDate(e.eventDateTime) },
              { id: 'status', header: 'Status', cell: (e) => <StatusPill status={e.status} /> },
              { id: 'sold', header: 'Sold', align: 'end', cell: (e) => formatNumber(e.soldTickets) },
            ]}
            rowActions={(e) => (
              <Button variant="text" size="sm" aria-label={`View ${e.title}`} onClick={() => router.push(`/event/${e.id}`)}>
                View
              </Button>
            )}
          />
        </Card>

        <Card>
          <CardHeader title="Verification documents" />
          {docs.error && docs.documents.length === 0 ? (
            <ErrorState error={docs.error} onRetry={docs.refetch} />
          ) : docs.loading && docs.documents.length === 0 ? (
            <Skeleton width="100%" />
          ) : docs.documents.length ? (
            <ul className="m3-stack" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {docs.documents.map((d) => (
                <li key={d.id} className="m3-row" style={{ justifyContent: 'space-between' }}>
                  <span>
                    <strong>{humanize(d.documentType)}</strong>
                    <br />
                    <span className="m3-muted">{[d.fileName, formatDate(d.uploadedAt)].filter(Boolean).join(' · ')}</span>
                  </span>
                  <StatusPill status={d.status} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="m3-muted">No documents uploaded.</p>
          )}
        </Card>
      </div>
      {ctl.dialogs}
    </ModuleFrame>
  );
}
