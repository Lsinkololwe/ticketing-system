'use client';

import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { useEventAccessGrants, useRoster, useTeamActions } from '@/lib/api/team';
import { DataState } from '@/components/console/DataState';
import { AccessGrants } from './AccessGrants';

/** "Team access" tab of the event detail page. Props contract: { eventId }. */
export function EventAccessTab({ eventId }: { eventId: string }) {
  const snackbar = useSnackbar();
  const roster = useRoster();
  const g = useEventAccessGrants(eventId);
  const actions = useTeamActions();
  const fail = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

  return (
    <DataState loading={g.loading && g.grants.length === 0} error={g.error} onRetry={() => void g.refetch()}>
      <AccessGrants
        grants={g.grants}
        members={roster.members}
        canGrant
        // Rejections propagate: the dialog's <Form> maps the server error onto its fields.
        onGrant={async (v) => {
          if (!roster.organizationId) throw new Error('Organization not loaded');
          await actions.grantAccess({ eventId, organizationId: roster.organizationId, userId: v.userId, role: v.role, reason: v.reason, expiresAt: v.expiresAt });
          snackbar.show('Access saved');
          await g.refetch();
        }}
        onUpdate={async (grant, role, expiresAt) => {
          await actions.updateGrant(grant.id, role, expiresAt);
          snackbar.show('Access saved');
          await g.refetch();
        }}
        onRevoke={async (grant) => {
          try {
            await actions.revokeGrant(grant.id);
            snackbar.show('Access revoked');
            await g.refetch();
          } catch (e) {
            snackbar.show({ message: fail(e), tone: 'error' });
          }
        }}
      />
    </DataState>
  );
}
