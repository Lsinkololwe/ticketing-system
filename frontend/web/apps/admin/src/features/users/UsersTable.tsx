'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, CardHeader, DataTable, EmptyState, ErrorState, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import { useIdentityUsers, useUserAdminActions, ADMIN_ACCOUNT_STATUSES, ADMIN_STAFF_ROLES, ADMIN_USER_ROLES, type AdminAccountStatus, type AdminUserRecord, type AdminUserRole } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { RowActions } from '@/components/console/RowActions';
import { FilterBar, useStaff, useTemporaryPassword } from '@/components/console';
import { ago, csvText, humanize, maskEmail, maskPhone } from '@/lib/format';
import { BuyerLookupDialog } from './BuyerLookupDialog';
import { PersonCell, RolePills, copyText, optionsOf, staffErrorMessage, useDebounced } from './common';
import { useStepUp } from '@/lib/useStepUp';
import { UserFormDialog, type UserFormValues } from './UserDialogs';
import { UserQuickView } from './UserQuickView';
import { toMenu, useUserActionController } from './useUserActionController';

const PAGE_SIZE = 20;

export function UsersTable() {
  const router = useRouter();
  const staff = useStaff();
  const snack = useSnackbar();
  const api = useUserAdminActions();
  const { guard } = useStepUp();
  const ctl = useUserActionController();
  const [query, setQuery] = useState('');
  const [role, setRole] = useState('all');
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(1);
  const [quick, setQuick] = useState<AdminUserRecord | null>(null);
  const [form, setForm] = useState<'admin' | null>(null);
  const [busy, setBusy] = useState(false);
  const [lookup, setLookup] = useState(false);
  const issued = useTemporaryPassword();
  const search = useDebounced(query);

  const list = useIdentityUsers({
    search,
    role: role === 'all' ? null : (role as AdminUserRole),
    accountStatus: status === 'all' ? null : (status as AdminAccountStatus),
    page: page - 1,
    size: PAGE_SIZE,
  });

  const submitForm = async (v: UserFormValues) => {
    setBusy(true);
    try {
      const created = await guard(() => api.createUser({ email: v.email, firstName: v.firstName, lastName: v.lastName, phoneNumber: v.phone || undefined, role: v.role as AdminUserRole }));
      if (created && v.role === 'FINANCE_LEAD') await guard(() => api.setUserRoles(created.user.id, ['CUSTOMER', 'FINANCE', 'FINANCE_LEAD']));
      setForm(null);
      setPage(1);
      const name = `${v.firstName} ${v.lastName}`;
      if (created?.temporaryPassword) {
        issued.show({ password: created.temporaryPassword, accountName: name, kind: 'created' });
      } else {
        // A repeat of a request that already finished: the account exists and its password was shown once, when it was created.
        snack.show(`${name} already has an account. Their password was shown when it was created. Use Reset password if they need a new one.`);
      }
    } catch (e) {
      snack.show({ message: staffErrorMessage(e), tone: 'error' });
    } finally {
      setBusy(false);
    }
  };

  const filtered = query !== '' || role !== 'all' || status !== 'all';
  const canCreateAdmin = staff.can('createAdmin');
  const canManage = staff.can('users');

  return (
    <>
      <Card>
        <CardHeader title="Users" subtitle="Customers, organizers and platform staff." />
        <FilterBar
          searchLabel="Search name, email or phone"
          query={query}
          onQuery={(q) => {
            setQuery(q);
            setPage(1);
          }}
          filters={[
            { id: 'role', label: 'Role', options: optionsOf(ADMIN_USER_ROLES.filter((r) => !ADMIN_STAFF_ROLES.includes(r))) },
            { id: 'st', label: 'Status', options: optionsOf(ADMIN_ACCOUNT_STATUSES) },
          ]}
          values={{ role, st: status }}
          onFilter={(id, v) => {
            if (id === 'role') setRole(v);
            else setStatus(v);
            setPage(1);
          }}
          onClear={() => {
            setQuery('');
            setRole('all');
            setStatus('all');
            setPage(1);
          }}
          actions={
            <>
              <Button
                variant="text"
                size="sm"
                icon="copy"
                onClick={() => {
                  copyText(
                    csvText([
                      ['Name', 'Email', 'Phone', 'Roles', 'Status'],
                      ...list.rows.map((u) => [u.fullName, maskEmail(u.email), maskPhone(u.phoneNumber), u.roles.join(' '), humanize(u.accountStatus)]),
                    ])
                  );
                  snack.show('CSV copied (contacts stay masked)');
                }}
              >
                Copy CSV
              </Button>
              <Button variant="outlined" size="sm" icon="search" onClick={() => setLookup(true)}>
                Buyer lookup
              </Button>
              {canCreateAdmin ? (
                <Button variant="tonal" size="sm" onClick={() => setForm('admin')}>
                  Create admin
                </Button>
              ) : null}
            </>
          }
        />
        <DataTable<AdminUserRecord>
          caption="Users"
          loading={list.loading && list.rows.length === 0}
          error={list.error && list.rows.length === 0 ? <ErrorState error={list.error} onRetry={list.refetch} /> : undefined}
          empty={
            <EmptyState
              icon="users"
              title={filtered ? 'No users match your search.' : 'No users yet'}
              description={filtered ? 'Try a different search or clear the filters.' : 'Accounts appear here once people sign up or are synced from Keycloak.'}
            />
          }
          rows={list.rows}
          getRowId={(u) => u.id}
          columns={[
            { id: 'user', header: 'User', rowHeader: true, cell: (u) => <PersonCell name={u.fullName} sub={maskEmail(u.email)} /> },
            { id: 'phone', header: 'Phone', cell: (u) => <span className="m3-mono">{maskPhone(u.phoneNumber)}</span> },
            { id: 'roles', header: 'Roles', cell: (u) => <RolePills roles={u.roles} /> },
            { id: 'status', header: 'Status', cell: (u) => <StatusPill status={u.accountStatus} /> },
            { id: '2fa', header: '2-step', cell: (u) => (u.twoFactorEnabled ? 'On' : <span className="m3-muted">Off</span>) },
            { id: 'last', header: 'Last sign-in', cell: (u) => ago(u.lastLoginAt) },
          ]}
          rowActions={(u) => (
            <RowActions
              name={u.fullName}
              primary={{ label: 'Quick view', onSelect: () => setQuick(u) }}
              items={[{ id: 'profile', label: 'Open full profile', onSelect: () => router.push(`/user/${u.id}`) }, ...(canManage ? toMenu(ctl.actionsFor(u), (id) => ctl.run(id, u)) : [])]}
            />
          )}
          pagination={{ page, pageSize: list.pageSize || PAGE_SIZE, total: list.total, onPageChange: setPage }}
        />
      </Card>

      <UserFormDialog open={form !== null} mode="admin" loading={busy} onClose={() => setForm(null)} onSubmit={submitForm} />
      {issued.panel}
      <BuyerLookupDialog open={lookup} onClose={() => setLookup(false)} />
      <UserQuickView
        user={quick}
        onClose={() => setQuick(null)}
        onOpen={(u) => router.push(`/user/${u.id}`)}
        controller={ctl}
      />
      {ctl.dialogs}
    </>
  );
}
