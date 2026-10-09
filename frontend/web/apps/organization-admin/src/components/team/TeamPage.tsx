'use client';

import { Suspense, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { useMyEvents } from '@pml.tickets/shared/api/organization-admin/modules/events';
import { useOrgContext } from '@/lib/api/org-context';
import { useSession } from '@/lib/session';
import type { EventRole, OrgRole } from '@/lib/api/team';
import { useIncomingTransfers, useInvitations, useOrgAccessGrants, useOwnershipTransfer, useRoster, useTeamActions } from '@/lib/api/team';
import { DataState } from '@/components/console/DataState';
import { TeamView, parseTeamTab, type TeamTab } from './TeamView';
import { MembersTab } from './MembersTab';
import { InvitationsTab } from './InvitationsTab';
import { AccessGrants } from './AccessGrants';
import { OwnershipTab } from './OwnershipTab';
import { IncomingTransferCard } from './IncomingTransferCard';
import { RolesTab } from './RolesTab';

const msg = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

function TeamPageInner() {
  const router = useRouter();
  const pathname = usePathname() ?? '/team';
  const params = useSearchParams();
  const tab = parseTeamTab(params?.get('tab'));
  const snackbar = useSnackbar();
  const { data: session } = useSession();
  const userId = session?.user?.id ?? null;
  const { organization, capabilities } = useOrgContext();
  const incoming = useIncomingTransfers();
  const roster = useRoster();
  const invites = useInvitations(roster.organizationId);
  const transfer = useOwnershipTransfer(roster.organizationId);
  const { events } = useMyEvents({ size: 100 });
  const actions = useTeamActions();
  const [openInvite, setOpenInvite] = useState(params?.get('invite') === '1');
  const orgActive = organization?.status === 'ACTIVE' || organization?.status === 'APPROVED';
  const orgId = roster.organizationId;
  const isOwner = capabilities.isOwner;
  const orgGrants = useOrgAccessGrants(orgId);

  const goTab = (t: TeamTab) => router.replace(`${pathname}?tab=${t}`);
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      snackbar.show(ok);
      await Promise.all([roster.refetch(), invites.refetch(), transfer.refetch()]);
    } catch (e) {
      snackbar.show({ message: msg(e), tone: 'error' });
    }
  };

  /** Like `run`, but lets a failure reject so the calling dialog's <Form> maps it. */
  const submit = async (fn: () => Promise<unknown>, ok: string) => {
    await fn();
    snackbar.show(ok);
    await Promise.all([roster.refetch(), invites.refetch(), transfer.refetch()]);
  };

  const renderTab = (t: TeamTab) => {
    switch (t) {
      case 'members':
        return (
          <DataState loading={roster.loading && roster.members.length === 0} error={roster.error} onRetry={() => void roster.refetch()}>
            <MembersTab
              members={roster.members}
              currentUserId={userId}
              canManage={capabilities.canManageTeam}
              onChangeRole={(m, role, c, d) => submit(() => actions.changeRole(orgId ?? '', m.id, role, c, d), 'Role saved')}
              onSuspend={(m) => run(() => actions.suspendMember(m.id), 'Member suspended')}
              onReactivate={(m) => run(() => actions.reactivateMember(m.id), 'Member reactivated')}
              onRemove={(m) => run(() => actions.removeMember(m.id), 'Member removed')}
            />
          </DataState>
        );
      case 'invites':
        return (
          <DataState loading={invites.loading && invites.invitations.length === 0} error={invites.error} onRetry={() => void invites.refetch()}>
            <InvitationsTab
              invitations={invites.invitations}
              events={events.map((e) => ({ id: e.id, title: e.title }))}
              orgActive={orgActive}
              canInvite={capabilities.canManageTeam}
              startOpen={openInvite}
              onInvite={async (req) => {
                if (!orgId) throw new Error('Organization not loaded');
                const failed: string[] = [];
                let firstError: unknown = null;
                for (const person of req.people) {
                  try {
                    await actions.invitePerson(orgId, { ...person, role: req.role as OrgRole, message: req.message, eventAccessGrants: req.eventAccessGrants as Array<{ eventId: string; role: EventRole }> });
                  } catch (e) {
                    firstError ??= e;
                    failed.push(`${person.email ?? person.phoneNumber}: ${msg(e)}`);
                  }
                }
                await invites.refetch();
                // Every invite failed: reject with the original error so the form maps its code. Partial failure: say who.
                if (failed.length === req.people.length) throw firstError;
                setOpenInvite(false);
                if (failed.length) snackbar.show({ message: `Not sent: ${failed.join(' | ')}`, tone: 'error' });
                snackbar.show(req.people.length === 1 ? `Invitation sent to ${req.people[0].email ?? req.people[0].phoneNumber}` : `${req.people.length - failed.length} of ${req.people.length} invitations sent`);
              }}
              onResend={(id) => run(() => actions.resendInvitation(id), 'Invitation resent')}
              onRevoke={(id) => run(() => actions.revokeInvitation(id), 'Invitation revoked')}
            />
          </DataState>
        );
      case 'access':
        return (
          <DataState loading={orgGrants.loading && orgGrants.grants.length === 0} error={orgGrants.error} onRetry={() => void orgGrants.refetch()}>
            <AccessGrants
              title="Event access grants"
              subtitle="Event-specific roles with optional expiry, on top of organization roles."
              grants={orgGrants.grants}
              members={roster.members}
              events={events.map((e) => ({ id: e.id, title: e.title }))}
              canGrant={capabilities.canWriteEvents}
              // Rejections propagate so the dialog's <Form> maps the server error onto its fields.
              onGrant={async (v) => {
                if (!orgId || !v.eventId) throw new Error('Choose an event');
                await actions.grantAccess({ eventId: v.eventId, organizationId: orgId, userId: v.userId, role: v.role, reason: v.reason, expiresAt: v.expiresAt });
                snackbar.show('Access saved');
                await orgGrants.refetch();
              }}
              onUpdate={async (grant, role, expiresAt) => {
                await actions.updateGrant(grant.id, role, expiresAt);
                snackbar.show('Access saved');
                await orgGrants.refetch();
              }}
              onRevoke={async (grant) => {
                try {
                  await actions.revokeGrant(grant.id);
                  snackbar.show('Access revoked');
                  await orgGrants.refetch();
                } catch (e) {
                  snackbar.show({ message: msg(e), tone: 'error' });
                }
              }}
            />
          </DataState>
        );
      case 'owner': {
        const owner = roster.members.find((m) => m.role === 'OWNER');
        return (
          <OwnershipTab
            ownerName={owner?.user?.fullName ?? 'Owner'}
            orgName={organization?.name ?? 'the organization'}
            isOwner={isOwner}
            admins={roster.members.filter((m) => m.role === 'ADMIN' && m.status === 'ACTIVE')}
            transfer={transfer.transfer}
            onStart={(newOwnerId) => submit(() => actions.initiateTransfer(orgId ?? '', newOwnerId), 'Transfer started. The nominee has been sent a link and code.')}
            onCancel={() => run(() => actions.cancelTransfer(orgId ?? ''), 'Transfer cancelled')}
            incoming={
              <IncomingTransferCard
                transfers={incoming.transfers}
                onRequestCode={(token) => actions.requestTransferCode(token).then(() => snackbar.show('A one-time code was sent to your verified phone.'))}
                onAccept={(token, code) => submit(() => actions.acceptTransfer(token, code), 'You are now the owner.')}
                onDecline={(token) => run(() => actions.declineTransfer(token), 'Transfer declined')}
              />
            }
            onLeave={async () => {
              try {
                await actions.leaveOrganization(orgId ?? '');
                window.location.href = '/welcome';
              } catch (e) {
                snackbar.show({ message: msg(e), tone: 'error' });
              }
            }}
          />
        );
      }
      case 'roles':
        return <RolesTab />;
    }
  };

  return (
    <TeamView
      tab={tab}
      onTabChange={goTab}
      canInvite={orgActive && capabilities.canManageTeam}
      onInvitePeople={() => { setOpenInvite(true); goTab('invites'); }}
      renderTab={renderTab}
    />
  );
}

export function TeamPage() {
  return (
    <Suspense fallback={null}>
      <TeamPageInner />
    </Suspense>
  );
}
