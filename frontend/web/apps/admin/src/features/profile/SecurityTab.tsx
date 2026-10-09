'use client';

import { Button, Card, CardHeader, ErrorState, Skeleton, StatusPill, useSnackbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import { DataTable, EmptyState } from '@pml.tickets/shared/components/m3';
import { useMySecurity, useMySessions, useRevokeSession, type AccountSessionRow } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { formatDateTime } from '@/lib/format';
import { useStepUp } from '@/lib/useStepUp';

/** Two-step verification status (me.twoFactorEnabled) and signed-in devices (mySessions / revokeSession). */
export function SecurityTab() {
  const me = useMySecurity();
  const sessions = useMySessions();
  const { revoke, loading: revoking } = useRevokeSession();
  const { guard } = useStepUp();
  const snackbar = useSnackbar();

  const signOut = async (s: AccountSessionRow) => {
    try {
      await guard(() => revoke(s.id));
      snackbar.show('Signed out of that device');
    } catch (e) {
      snackbar.show((e as Error).message || 'Could not sign out that device');
    }
  };
  const columns: Array<DataColumn<AccountSessionRow>> = [
    { id: 'device', header: 'Device', rowHeader: true, cell: (s) => <>{s.clients.length ? s.clients.join(', ') : 'Browser'}{s.current ? <> <StatusPill tone="success">This device</StatusPill></> : null}</> },
    { id: 'ip', header: 'IP address', cell: (s) => <span className="m3-mono">{s.ipAddress ?? '—'}</span> },
    { id: 'started', header: 'Signed in', cell: (s) => formatDateTime(s.startedAt) },
    { id: 'last', header: 'Last active', cell: (s) => formatDateTime(s.lastAccessAt) },
  ];
  return (
    <div className="adm-stack">
      <Card>
        <CardHeader title="Two-step verification" subtitle="Required for every staff account." />
        {me.error && !me.me ? (
          <ErrorState error={me.error} onRetry={me.refetch} />
        ) : !me.me ? (
          <Skeleton width="100%" />
        ) : (
          <div className="m3-row">
            <StatusPill tone={me.me.twoFactorEnabled ? 'success' : 'warning'}>{me.me.twoFactorEnabled ? 'On' : 'Off'}</StatusPill>
            <span className="m3-muted">{me.me.twoFactorEnabled ? 'Your authenticator app is asked for a code at sign-in.' : 'Set up an authenticator app at your next sign-in.'}</span>
          </div>
        )}
      </Card>
      <Card>
        <CardHeader title="Where you are signed in" subtitle="Sign out of any device you do not recognise." />
        <DataTable<AccountSessionRow>
          caption="Signed-in devices"
          columns={columns}
          rows={sessions.sessions}
          getRowId={(s) => s.id}
          loading={sessions.loading && sessions.sessions.length === 0}
          error={sessions.error && sessions.sessions.length === 0 ? <ErrorState error={sessions.error} onRetry={sessions.refetch} /> : undefined}
          empty={<EmptyState title="No other devices are signed in." />}
          rowActions={(s) => (s.current ? null : <Button variant="text" size="sm" danger loading={revoking} onClick={() => void signOut(s)}>Sign out</Button>)}
        />
      </Card>
    </div>
  );
}
