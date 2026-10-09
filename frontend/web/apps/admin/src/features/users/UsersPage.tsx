'use client';

import { ModuleFrame } from '@/components/console';
import { OrgsTable } from './OrgsTable';
import { UsersTable } from './UsersTable';

/** /users/[tab]: Users and Organizations tables. */
export function UsersPage({ tab }: { tab: string }) {
  const current = tab === 'orgs' ? 'orgs' : 'users';
  return (
    <ModuleFrame module="users" tab={current} title="Users & orgs" subtitle="Manage accounts, roles and organizations">
      {current === 'users' ? <UsersTable /> : <OrgsTable />}
    </ModuleFrame>
  );
}
